package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real installed INT8 models on recorded public metadata, no network or accounts.
 * Scores are diagnostic only: no equivalence threshold, publication or feed edits. */
final class EventVerificationModelProbe {
    private final Instrumentation test;
    private final boolean holdout;
    private final StringBuilder report=new StringBuilder();private int checks,failures;
    EventVerificationModelProbe(Instrumentation test){this(test,false);}
    EventVerificationModelProbe(Instrumentation test,boolean holdout){this.test=test;this.holdout=holdout;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void status(String value){Bundle b=new Bundle();b.putString("stream",value+"\n");test.sendStatus(1,b);}
    private String asset(String path)throws Exception{try(InputStream in=test.getContext().getAssets().open(path)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    void run(){
        try{
            JSONArray recorded=new JSONArray();LinkedHashMap<String,HotCandidate> unique=new LinkedHashMap<>();
            List<String> fixtures=holdout?Collections.singletonList("fixtures/observed-event-holdout-pairs-20261026.json"):Arrays.asList("fixtures/observed-event-pairs-20261026.json","fixtures/observed-event-stress-pairs-20261026.json");
            for(String path:fixtures){
                JSONObject fixture=new JSONObject(asset(path));JSONArray pairs=fixture.getJSONArray("pairs");
                for(int n=0;n<pairs.length();n++){
                    JSONObject pair=pairs.getJSONObject(n);recorded.put(pair);
                    for(String side:Arrays.asList("left","right")){HotCandidate c=HotCandidate.fromJson(pair.getJSONObject(side));HotCandidate old=unique.get(c.id);if(old==null||c.observedAt>old.observedAt)unique.put(c.id,c);}
                }
            }
            ModelFiles files=ModelFiles.prepare(test.getTargetContext());List<HotCandidate> candidates=new ArrayList<>(unique.values());Map<String,float[]> vectors=new HashMap<>();JSONArray tasksJson=new JSONArray();
            long embeddingMs,planningMs,rerankMs;List<EventVerificationPlan.Task> tasks;
            try(LocalModelEngine engine=new LocalModelEngine(files.directory(),files.manifest())){
                long start=SystemClock.elapsedRealtime();int encoded=0;
                for(HotCandidate c:candidates){String key=SemanticOrder.key(c.modelText());if(!vectors.containsKey(key)){vectors.put(key,engine.embed(SemanticOrder.input(c.modelText())));encoded++;if(encoded%10==0)status("embedded="+encoded);}}
                embeddingMs=SystemClock.elapsedRealtime()-start;check(vectors.size()>20,"actual cached 512-dimensional vectors cover observed candidates");
                start=SystemClock.elapsedRealtime();tasks=EventVerificationPlan.create(candidates,vectors,files.revision());planningMs=SystemClock.elapsedRealtime()-start;
                check(!tasks.isEmpty()&&tasks.size()<=EventVerificationPlan.MAX_PAIRS,"actual vectors produce a bounded verification plan");
                List<HotCandidate> reverse=new ArrayList<>(candidates);Collections.reverse(reverse);List<EventVerificationPlan.Task> reversed=EventVerificationPlan.create(reverse,vectors,files.revision());
                boolean stable=tasks.size()==reversed.size();for(int n=0;stable&&n<tasks.size();n++)stable=tasks.get(n).evidenceKey.equals(reversed.get(n).evidenceKey);check(stable,"same observed window remains deterministic when input is reversed");
                start=SystemClock.elapsedRealtime();
                for(EventVerificationPlan.Task task:tasks){
                    LocalModelEngine.RerankResult f=engine.rerankResult(task.leftInput,task.rightInput),b=engine.rerankResult(task.rightInput,task.leftInput);float forward=f.logit,backward=b.logit;
                    check(Float.isFinite(forward)&&Float.isFinite(backward),"actual bidirectional logits are finite");
                    tasksJson.put(new JSONObject().put("left",task.left.json()).put("right",task.right.json()).put("cosine",task.similarity).put("forward",forward).put("backward",backward).put("minimum",Math.min(forward,backward)).put("completeInputs",f.complete&&b.complete).put("forwardInputTokens",f.originalTokens).put("backwardInputTokens",b.originalTokens).put("cacheKey",task.cacheKey).put("evidenceKey",task.evidenceKey));
                    status("verified="+tasksJson.length()+"/"+tasks.size());
                }
                rerankMs=SystemClock.elapsedRealtime()-start;
                JSONArray labelledScores=new JSONArray();
                if(holdout){
                    int same=0,different=0,uncertain=0,admittedSame=0,admittedDifferent=0,admittedUncertain=0;
                    for(int n=0;n<recorded.length();n++){
                        JSONObject pair=recorded.getJSONObject(n);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));
                        boolean planned=false;for(EventVerificationPlan.Task task:tasks)if(task.left.id.equals(a.id)&&task.right.id.equals(b.id)||task.left.id.equals(b.id)&&task.right.id.equals(a.id))planned=true;
                        float forward=engine.rerank(a.title,b.title),backward=engine.rerank(b.title,a.title);String label=pair.getString("label");
                        if(label.equals("same")){same++;if(planned)admittedSame++;}else if(label.equals("different")){different++;if(planned)admittedDifferent++;}else{uncertain++;if(planned)admittedUncertain++;}
                        labelledScores.put(new JSONObject().put("label",label).put("left",a.title).put("right",b.title).put("admission",EventClaimEvidence.evaluate(a,b).name()).put("planned",planned).put("forward",forward).put("backward",backward).put("minimum",Math.min(forward,backward)));
                    }
                    check(same==3&&different==6&&uncertain==3,"pre-labelled holdout preserves all three categories");
                    report.append("HOLDOUT same_planned=").append(admittedSame).append('/').append(same).append(" different_planned=").append(admittedDifferent).append('/').append(different).append(" uncertain_planned=").append(admittedUncertain).append('/').append(uncertain).append("; retrieval only, no grouping change\n");
                }
                // Deliberate counterexamples are separately labelled synthetic,
                // never presented as recorded source data or model-quality holdout.
                JSONArray synthetic=new JSONArray();String[][] cases={{"王沪宁陪同金正恩访问丹东","王沪宁陪同金正恩访问北京"},{"某公司发布新款蓝牙耳机","某公司发布新款智能空调"},{"秦兵马俑上发现女性指纹","秦兵马俑上发现男性指纹"}};
                for(int n=0;n<cases.length;n++){
                    long now=System.currentTimeMillis();HotCandidate a=new HotCandidate(Models.Source.ZHIHU,"https://example.org/a/"+n,cases[n][0],"","",false,1,null,now),b=new HotCandidate(Models.Source.WEIBO,"https://example.org/b/"+n,cases[n][1],"","",false,1,null,now);
                    float forward=engine.rerank(a.title,b.title),backward=engine.rerank(b.title,a.title);
                    synthetic.put(new JSONObject().put("synthetic",true).put("label","different").put("left",a.title).put("right",b.title).put("admission",EventClaimEvidence.evaluate(a,b).name()).put("forward",forward).put("backward",backward).put("minimum",Math.min(forward,backward)));
                }
                JSONObject output=new JSONObject().put("schema",1).put("holdout",holdout).put("scope","x86_64 emulator; recorded public metadata subset; no feed merging, ARM, personal relevance or energy acceptance").put("modelRevision",files.revision()).put("candidateCount",candidates.size()).put("vectorCount",vectors.size()).put("recordedLabelledPairs",recorded.length()).put("embeddingMs",embeddingMs).put("planningMs",planningMs).put("rerankMs",rerankMs).put("tasks",tasksJson).put("labelledScores",labelledScores).put("syntheticCounterexamples",synthetic);
                File file=new File(test.getTargetContext().getExternalFilesDir(null),holdout?"event-verification-holdout-model.json":"event-verification-plan-model.json");try(FileOutputStream out=new FileOutputStream(file)){out.write(output.toString(2).getBytes(StandardCharsets.UTF_8));}
                report.append("candidates=").append(candidates.size()).append(" vectors=").append(vectors.size()).append(" tasks=").append(tasks.size()).append(" embedding_ms=").append(embeddingMs).append(" planning_ms=").append(planningMs).append(" bidirectional_rerank_ms=").append(rerankMs).append("\noutput=").append(file.getAbsolutePath()).append('\n');
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
