package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Declared fixture pairs, including reused development and synthetic controls. */
final class EventFreshQualityProbe {
    private final Instrumentation test;
    private final boolean polarity;
    private final String fixturePath,outputStem;
    private final int expectedSame,expectedDifferent,expectedUncertain;
    private final boolean pairwise;
    EventFreshQualityProbe(Instrumentation test){this(test,false);}
    EventFreshQualityProbe(Instrumentation test,boolean polarity){this(test,polarity,polarity?"fixtures/controlled-event-polarity-20261027.json":"fixtures/observed-event-fresh-pairs-20261026.json",polarity?"event-polarity-quality":"event-fresh-quality",polarity?2:7,polarity?12:6,polarity?0:3);}
    EventFreshQualityProbe(Instrumentation test,boolean polarity,String fixturePath,String outputStem,int same,int different,int uncertain){this(test,polarity,fixturePath,outputStem,same,different,uncertain,false);}
    EventFreshQualityProbe(Instrumentation test,boolean polarity,String fixturePath,String outputStem,int same,int different,int uncertain,boolean pairwise){this.test=test;this.polarity=polarity;this.fixturePath=fixturePath;this.outputStem=outputStem;expectedSame=same;expectedDifferent=different;expectedUncertain=uncertain;this.pairwise=pairwise;}
    private static String key(HotCandidate a,HotCandidate b){return a.id.compareTo(b.id)<0?a.id+":"+b.id:b.id+":"+a.id;}
    void run(){StringBuilder report=new StringBuilder();int checks=0,failures=0;
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated package required");
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open(fixturePath)){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            report.append("FIXTURE ").append(fixturePath).append(" scope=").append(fixture.optString("scope",fixture.optString("provenance","retained developer fixture; not independent evaluation"))).append('\n');
            JSONArray pairs=fixture.getJSONArray("pairs"),output=new JSONArray();Map<String,HotCandidate> unique=new LinkedHashMap<>();
            JSONArray pool=fixture.optJSONArray("candidatePool");
            if(pool!=null){
                if(pairwise)throw new IllegalStateException("Full pool must use a single bounded global plan");
                for(int n=0;n<pool.length();n++){HotCandidate c=HotCandidate.fromJson(pool.getJSONObject(n));if(unique.put(c.id,c)!=null)throw new IllegalStateException("Duplicate pool identity");}
            }
            for(int n=0;n<pairs.length();n++)for(String side:List.of("left","right")){HotCandidate c=HotCandidate.fromJson(pairs.getJSONObject(n).getJSONObject(side));if(pool!=null){HotCandidate original=unique.get(c.id);if(original==null||!original.json().toString().equals(c.json().toString()))throw new IllegalStateException("Labeled pair differs from frozen full pool");}else unique.put(c.id,c);}
            List<FeedStore.Row> fullBase=pool==null?Collections.emptyList():CandidateOrder.forModels(new ArrayList<>(unique.values()),new JSONArray());
            List<HotCandidate> modeling=new ArrayList<>();Set<String> eligible=new HashSet<>();
            if(pool==null)modeling.addAll(unique.values());else for(FeedStore.Row row:fullBase)for(HotCandidate member:row.entries)if(eligible.add(member.id))modeling.add(member);
            ModelFiles files=ModelFiles.prepare(test.getTargetContext());Map<String,float[]> vectors=new HashMap<>();
            Map<String,EventVerificationPlan.Task> planned=new HashMap<>();List<EventGroupAssembly.ScoredPair> scores=new ArrayList<>();
            int same=0,different=0,uncertain=0,sameMerged=0,falseMerged=0,uncertainMerged=0;
            try(LocalModelEngine engine=new LocalModelEngine(files.directory(),files.manifest())){
                for(HotCandidate c:modeling){String k=SemanticOrder.key(c.modelText());if(!vectors.containsKey(k))vectors.put(k,engine.embed(SemanticOrder.input(c.modelText())));}
                List<EventVerificationPlan.Task> tasks;
                if(pairwise){
                    if(pairs.length()>EventVerificationPlan.MAX_PAIRS)throw new IllegalStateException("Pairwise control window exceeds bounded verification size");
                    Map<String,EventVerificationPlan.Task> individual=new LinkedHashMap<>();
                    for(int n=0;n<pairs.length();n++){JSONObject p=pairs.getJSONObject(n);HotCandidate a=HotCandidate.fromJson(p.getJSONObject("left")),b=HotCandidate.fromJson(p.getJSONObject("right"));for(EventVerificationPlan.Task task:EventVerificationPlan.create(List.of(a,b),vectors,files.revision()))individual.put(key(task.left,task.right),task);}
                    tasks=new ArrayList<>(individual.values());
                }else tasks=EventVerificationPlan.create(modeling,vectors,files.revision());
                for(EventVerificationPlan.Task task:tasks){planned.put(key(task.left,task.right),task);LocalModelEngine.RerankResult f=engine.rerankResult(task.leftInput,task.rightInput),b=engine.rerankResult(task.rightInput,task.leftInput);scores.add(new EventGroupAssembly.ScoredPair(task,f.logit,b.logit,f.complete&&b.complete));}
                List<FeedStore.Row> globalBase=fullBase;
                List<FeedStore.Row> global=pool==null?Collections.emptyList():EventGroupAssembly.apply(globalBase,new JSONArray(),scores,files.revision());
                Map<String,Integer> owner=new HashMap<>();
                if(pool!=null){
                    for(int n=0;n<global.size();n++)for(HotCandidate member:global.get(n).entries)if(owner.put(member.id,n)!=null)throw new IllegalStateException("Global assembly duplicated a source identity");
                    checks++;if(!owner.keySet().equals(eligible)){failures++;report.append("FAIL global assembly lost eligible source identities\n");}else report.append("PASS global assembly conserves every eligible frozen source identity\n");
                }
                for(int n=0;n<pairs.length();n++){
                    JSONObject p=pairs.getJSONObject(n);HotCandidate a=HotCandidate.fromJson(p.getJSONObject("left")),b=HotCandidate.fromJson(p.getJSONObject("right"));
                    LocalModelEngine.RerankResult f=engine.rerankResult(a.title,b.title),r=engine.rerankResult(b.title,a.title);
                    if(!Float.isFinite(f.logit)||!Float.isFinite(r.logit))throw new IllegalStateException("Nonfinite actual score");
                    List<FeedStore.Row> base=CandidateOrder.arrange(List.of(a,b),new JSONArray(),false);
                    List<FeedStore.Row> grouped=EventGroupAssembly.apply(base,new JSONArray(),scores,files.revision());boolean pairOnlyMerged=grouped.size()==1,merged=pool==null?pairOnlyMerged:owner.containsKey(a.id)&&owner.get(a.id).equals(owner.get(b.id));
                    String label=p.getString("label");if(label.equals("same")){same++;if(merged)sameMerged++;}else if(label.equals("different")){different++;if(merged)falseMerged++;}else if(label.equals("uncertain")){uncertain++;if(merged)uncertainMerged++;}else throw new IllegalStateException("Unknown label");
                    JSONObject result=new JSONObject().put("label",label).put("left",a.json()).put("right",b.json()).put("sourceEvidenceGrouped",base.size()==1).put("admission",EventClaimEvidence.evaluate(a,b).name()).put("planned",planned.containsKey(key(a,b))).put("forward",f.logit).put("backward",r.logit).put("minimum",Math.min(f.logit,r.logit)).put("completeInputs",f.complete&&r.complete).put("merged",merged);output.put(result);
                    result.put("diagnosticInput","forward/backward/minimum describe raw original titles, not canonical verification inputs");
                    if(pool!=null)result.put("pairOnlyMerged",pairOnlyMerged);
                    result.put("provenance",p.optString("provenance","retained developer public pair"));
                    for(EventGroupAssembly.ScoredPair verified:scores)if(key(verified.task.left,verified.task.right).equals(key(a,b))){
                        result.put("verificationLeftInput",verified.task.leftInput).put("verificationRightInput",verified.task.rightInput)
                            .put("verificationForward",verified.forward).put("verificationBackward",verified.backward)
                            .put("verificationMinimum",Math.min(verified.forward,verified.backward)).put("verificationComplete",verified.completeInputs);
                    }
                    report.append(label).append(" merged=").append(merged).append(" planned=").append(result.getBoolean("planned")).append(" admission=").append(result.getString("admission")).append(" rawMin=").append(result.getDouble("minimum")).append(" :: ").append(a.title).append(" / ").append(b.title).append('\n');
                    if(polarity){checks++;if(!p.getString("admission").equals(result.getString("admission"))||label.equals("different")&&result.getBoolean("planned")){failures++;report.append("FAIL controlled predicate admission or planning\n");}}
                }
                checks++;if(same!=expectedSame||different!=expectedDifferent||uncertain!=expectedUncertain){failures++;report.append("FAIL fixed label counts changed\n");}else report.append("PASS fixed pre-score labels retained\n");
                checks++;if(falseMerged>0||uncertainMerged>0){failures++;report.append("FAIL different or uncertain pair merged\n");}else report.append("PASS different and uncertain pairs remain separate\n");
                JSONObject summary=new JSONObject().put("schema",1).put("corpus_sha256",fixture.getString("corpus_sha256")).put("fullCandidatePool",pool!=null).put("pairwiseControlPlanning",pairwise).put("claimVersion",EventClaimEvidence.VERSION).put("assemblyVersion",EventGroupAssembly.VERSION).put("modelRevision",files.revision()).put("candidates",unique.size()).put("plannedTasks",tasks.size()).put("same",same).put("sameMerged",sameMerged).put("different",different).put("falseMerged",falseMerged).put("uncertain",uncertain).put("uncertainMerged",uncertainMerged).put("pairs",output);
                if(pool!=null){JSONArray selected=new JSONArray();Set<String> modelInputs=new HashSet<>();for(EventGroupAssembly.ScoredPair score:scores){EventVerificationPlan.Task task=score.task;modelInputs.add(task.cacheKey);selected.put(new JSONObject().put("left",task.left.title).put("right",task.right.title).put("similarity",task.similarity).put("cacheKey",task.cacheKey).put("forward",score.forward).put("backward",score.backward).put("minimum",Math.min(score.forward,score.backward)).put("completeInputs",score.completeInputs));}summary.put("eligibleCandidates",modeling.size()).put("globalBaseRows",globalBase.size()).put("globalRows",global.size()).put("uniqueModelInputPairs",modelInputs.size()).put("selectedTasks",selected);}
                File target=new File(test.getTargetContext().getExternalFilesDir(null),outputStem+".json");try(FileOutputStream out=new FileOutputStream(target)){out.write(summary.toString().getBytes(StandardCharsets.UTF_8));}
                report.append("QUALITY same_merged=").append(sameMerged).append('/').append(same).append(" different_merged=").append(falseMerged).append('/').append(different).append(" uncertain_merged=").append(uncertainMerged).append('/').append(uncertain).append(" planned=").append(tasks.size()).append('\n');
            }
        }catch(Throwable error){checks++;failures++;report.append("FAIL ").append(android.util.Log.getStackTraceString(error));}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append(polarity?"\nscope=controlled synthetic predicate pairs with actual INT8 models, not observed news or population accuracy\n":"\nscope=declared fixture pairs with actual INT8 models; see fixture and per-pair provenance; not population accuracy, personal relevance or ARM resources\n").toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
