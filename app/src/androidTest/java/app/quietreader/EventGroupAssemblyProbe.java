package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.ai.ModelFiles;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Actual cached model logits -> assembly -> SQLite snapshots and feedback.
 * Does not enable the policy in ordinary recommendation scheduling. */
final class EventGroupAssemblyProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    EventGroupAssemblyProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private String read(InputStream in)throws Exception{try(InputStream input=in){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
    void run(){String database="test-event-assembly-"+UUID.randomUUID()+".db";
        try{
            JSONObject raw=new JSONObject(read(new FileInputStream(new File(test.getTargetContext().getExternalFilesDir(null),"event-verification-holdout-model.json"))));
            String revision=raw.getString("modelRevision");check(revision.equals(new ModelFiles(test.getTargetContext()).revision()),"actual cached raw logits match installed model revision");
            JSONObject fixture=new JSONObject(read(test.getContext().getAssets().open("fixtures/observed-event-holdout-pairs-20261026.json")));LinkedHashMap<String,HotCandidate> candidates=new LinkedHashMap<>();
            for(int n=0;n<fixture.getJSONArray("pairs").length();n++){JSONObject pair=fixture.getJSONArray("pairs").getJSONObject(n);for(String side:Arrays.asList("left","right")){HotCandidate c=HotCandidate.fromJson(pair.getJSONObject(side));HotCandidate old=candidates.get(c.id);if(old==null||c.observedAt>old.observedAt)candidates.put(c.id,c);}}
            List<EventGroupAssembly.ScoredPair> scores=new ArrayList<>();JSONArray tasks=raw.getJSONArray("tasks");
            for(int n=0;n<tasks.length();n++){
                JSONObject item=tasks.getJSONObject(n);HotCandidate a=HotCandidate.fromJson(item.getJSONObject("left")),b=HotCandidate.fromJson(item.getJSONObject("right"));
                // Retrieval already happened in the actual-model run. Rebuild
                // only the current task/evidence binding from its two endpoints.
                float[] unit=new float[512];unit[0]=1;Map<String,float[]> placeholders=new HashMap<>();placeholders.put(SemanticOrder.key(a.modelText()),unit);placeholders.put(SemanticOrder.key(b.modelText()),unit);
                List<EventVerificationPlan.Task> current=EventVerificationPlan.create(Arrays.asList(a,b),placeholders,revision);check(current.size()==1,"cached pair still passes current fact admission");
                if(current.size()==1){check(current.get(0).cacheKey.equals(item.getString("cacheKey")),"raw title-logit cache identity is exact");scores.add(new EventGroupAssembly.ScoredPair(current.get(0),(float)item.getDouble("forward"),(float)item.getDouble("backward"),item.optBoolean("completeInputs",false)));}
            }
            List<FeedStore.Row> base=CandidateOrder.forModels(new ArrayList<>(candidates.values()),new JSONArray());List<FeedStore.Row> merged=EventGroupAssembly.apply(base,new JSONArray(),scores,revision);
            check(base.size()==24&&merged.size()==23,"joint provisional policy merges only the high-score explicit-model release in this replay");
            FeedStore.Row group=null;for(FeedStore.Row row:merged)if(row.entries.size()>1)group=row;if(group==null)throw new IllegalStateException("Expected one assembled group");
            FeedStore.Row selected=group;check(selected.entries.size()==2,"both original release source entries survive");String anchor=selected.entries.get(1).id;
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                long now=System.currentTimeMillis(),source=store.version("sources"),profile=store.version("profile");long old=store.commitRanking(source,profile,now,"cold","all",base);store.activateSnapshot(old);store.anchor(old,anchor,-17);
                long pending=store.commitRanking(source,profile,now+1,"personal","all",merged);store.anchor(pending,anchor,-17);
                check(store.activeSnapshot()==old&&store.readSnapshot(old,200,0).size()==24,"new assembly does not replace or mutate active frozen snapshot");
                List<FeedStore.Row> restored=store.readSnapshot(pending,200,0);FeedStore.Row persisted=restored.stream().filter(row->row.entries.size()>1).findFirst().orElseThrow();
                check(restored.size()==23&&persisted.entries.size()==2,"SQLite round trip preserves assembled original member payloads");
                check(EventGroups.matches(persisted,anchor)&&store.snapshotInfo(pending).getInt("offset")==-17&&store.snapshotInfo(pending).getInt("position")==restored.indexOf(persisted),"member anchor and scroll offset survive grouping and persistence");
                for(HotCandidate entry:persisted.entries)check(SourceFilter.apply(restored,entry.source.name()).stream().anyMatch(row->row.eventId.equals(persisted.eventId)&&row.candidate.id.equals(entry.id)),"source filter opens actual member after SQLite restore: "+entry.source);
                store.feedback("assembly-split","SEPARATE","event",EventTargets.capture(persisted,false),now+2,null,persisted.candidate.modelText());
                JSONArray feedback=store.activeFeedback(now+2);check(EventGroupAssembly.apply(base,feedback,scores,revision).size()==24,"persisted explicit split blocks model reunion");
                check(store.commitRanking(source,profile,now+3,"personal","all",merged)==-1,"feedback revision rejects a previously computed assembly snapshot");
                check(store.undo("assembly-split",now+4)&&EventGroupAssembly.apply(base,store.activeFeedback(now+4),scores,revision).size()==23,"undo restores eligibility without changing stored raw scores");
                store.feedback("assembly-block","BLOCK","event",EventTargets.capture(persisted,false),now+5,null,persisted.candidate.modelText());
                check(EventGroupAssembly.apply(base,store.activeFeedback(now+5),scores,revision).size()==22,"block hides both captured members");store.undo("assembly-block",now+6);
                store.feedback("assembly-enough","ENOUGH","event",EventTargets.capture(persisted,true),now+7,null,persisted.candidate.modelText());
                check(EventGroupAssembly.apply(base,store.activeFeedback(now+7),scores,revision).size()==22,"enough hides both captured metadata versions");
                check(store.readSnapshot(old,200,0).size()==24,"feedback processing leaves old frozen payload unchanged");
                boolean rejected=false;try{new RecommendationComparison(base,base,merged,revision,"profile",1,profile,source,2);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"comparison rejects different pre/post assembly event pools");
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=isolated cached-logit assembly/SQLite; no ordinary scheduling, ARM or energy acceptance\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
