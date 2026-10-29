package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Whole recorded public window through the ordinary service/cache pipeline.
 * Uses the default memory/thermal policy. No user preferences or scores injected. */
final class EventWindowReplayProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    EventWindowReplayProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private String asset(String path)throws Exception{try(InputStream in=test.getContext().getAssets().open(path)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private static final class Result {List<FeedStore.Row> rows;boolean complete;int refined,encoded,total,planned,directions;long elapsed;String resources,status;}
    private static int size(LocalRecommendation running,String name){try{java.lang.reflect.Field field=LocalRecommendation.class.getDeclaredField(name);field.setAccessible(true);Object value=field.get(running);return value instanceof Map?((Map<?,?>)value).size():((List<?>)value).size();}catch(Exception error){return -1;}}
    private Result run(FeedStore store,List<FeedStore.Row> input)throws Exception{
        SemanticOrder.Profile profile=new SemanticOrder.Profile(store.activeFeedback(System.currentTimeMillis()));long source=store.version("sources"),version=store.version("profile");
        Result result=new Result();CountDownLatch done=new CountDownLatch(1);LocalRecommendation[] running={null};long started=SystemClock.elapsedRealtime();
        test.runOnMainSync(()->{
            running[0]=new LocalRecommendation(test.getTargetContext(),store,storage,input,profile,1,version,source,new LocalRecommendation.Listener(){
                public void status(String value){}
                public void complete(List<FeedStore.Row> rows,boolean semantic,int encoded,int total,int refined,String status){result.rows=rows;result.encoded=encoded;result.total=total;result.refined=refined;result.complete=running[0].fullyComputed();result.planned=size(running[0],"eventTasks");result.directions=size(running[0],"eventLogits");result.resources=running[0].resourceDescription();result.status=status;result.elapsed=SystemClock.elapsedRealtime()-started;done.countDown();}
            });running[0].start();
        });
        if(!done.await(35,TimeUnit.SECONDS)){test.runOnMainSync(()->running[0].close());throw new IllegalStateException("Whole-window ordinary pipeline timed out");}
        report.append("RUN rows=").append(result.rows.size()).append(" encoded=").append(result.encoded).append('/').append(result.total).append(" refined=").append(result.refined).append(" planned_pairs=").append(result.planned).append(" cached_directions=").append(result.directions).append(" complete=").append(result.complete).append(" elapsed_ms=").append(result.elapsed).append(" status=").append(result.status).append('\n').append(result.resources).append('\n');return result;
    }
    private static Set<String> members(List<FeedStore.Row> rows){Set<String> found=new HashSet<>();for(FeedStore.Row row:rows)for(HotCandidate c:row.entries)if(!found.add(c.id+"@"+c.textHash))throw new IllegalStateException("Repeated member across groups");return found;}
    private static List<FeedStore.Row> readAll(FeedStore store,long id){List<FeedStore.Row> rows=new ArrayList<>();for(int offset=0;offset<5000;offset+=200){List<FeedStore.Row> page=store.readSnapshot(id,200,offset);rows.addAll(page);if(page.size()<200)break;}return rows;}
    private static boolean together(List<FeedStore.Row> rows,HotCandidate a,HotCandidate b){for(FeedStore.Row row:rows){boolean left=false,right=false;for(HotCandidate c:row.entries){left|=c.id.equals(a.id);right|=c.id.equals(b.id);}if(left&&right)return true;}return false;}
    void run(){String database="test-event-window-"+UUID.randomUUID()+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated package required");
            String raw=asset("fixtures/observed-event-fresh-window-20261026.json");JSONObject corpus=new JSONObject(raw),labels=new JSONObject(asset("fixtures/observed-event-fresh-pairs-20261026.json"));
            check(HotCandidate.hash(raw).equalsIgnoreCase(labels.getString("corpus_sha256")),"whole window bytes match the pre-labelled corpus SHA256");
            JSONArray pool=corpus.getJSONArray("candidates");Map<Models.Source,List<HotCandidate>> sources=new EnumMap<>(Models.Source.class);List<HotCandidate> candidates=new ArrayList<>();
            for(int n=0;n<pool.length();n++){HotCandidate c=HotCandidate.fromJson(pool.getJSONObject(n));candidates.add(c);sources.computeIfAbsent(c.source,k->new ArrayList<>()).add(c);}
            check(candidates.size()==270&&sources.size()==9,"replay retains all 270 recorded candidates from nine actual sources");
            for(Map.Entry<Models.Source,List<HotCandidate>> source:sources.entrySet())store.upsertSnapshot(source.getKey(),store.beginRefresh(source.getKey()),source.getValue(),System.currentTimeMillis());
            List<FeedStore.Row> input=CandidateOrder.forModels(store.candidates(5000,0),new JSONArray());Set<String> allowed=members(input);
            long old=store.commitRanking(store.version("sources"),store.version("profile"),System.currentTimeMillis(),"cold","all",input);store.activateSnapshot(old);
            Result result=null;JSONArray runs=new JSONArray();
            for(int round=0;round<3;round++){
                result=run(store,input);check(members(result.rows).equals(allowed),"ordinary whole-window round "+(round+1)+" preserves each eligible member and metadata hash exactly once");
                check(result.planned>=0&&result.planned<=EventVerificationPlan.MAX_PAIRS&&result.directions>=0&&result.directions<=2*result.planned,"whole-window verification uses the bounded actual task/direction set");
                runs.put(new JSONObject().put("round",round+1).put("rows",result.rows.size()).put("complete",result.complete).put("encoded",result.encoded).put("total",result.total).put("refined",result.refined).put("planned_pairs",result.planned).put("cached_directions",result.directions).put("elapsed_ms",result.elapsed).put("status",result.status).put("resources",result.resources));
                if(result.complete)break;
            }
            check(result!=null&&result.complete,"default-policy whole-window work completes within at most three resumable foreground slices");
            check(store.activeSnapshot()==old&&readAll(store,old).size()==input.size(),"ordinary computation keeps the original whole-window active snapshot frozen");
            if(result==null)throw new IllegalStateException("No result");
            List<FeedStore.Row> finalRows=result.rows;Result cached=run(store,input);
            check(cached.complete&&cached.refined==0&&cached.resources.contains("初筛 0 条，精排 0 条"),"complete whole-window repeat performs no new embedding or reranker inference");
            check(members(cached.rows).equals(allowed)&&cached.rows.size()==finalRows.size(),"cache replay preserves full-window membership and event count");
            int same=0,sameMerged=0,different=0,falseMerged=0,uncertain=0,uncertainMerged=0;JSONArray outcomes=new JSONArray();
            for(int n=0;n<labels.getJSONArray("pairs").length();n++){
                JSONObject pair=labels.getJSONArray("pairs").getJSONObject(n);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));boolean merged=together(finalRows,a,b);String label=pair.getString("label");
                if(label.equals("same")){same++;if(merged)sameMerged++;}else if(label.equals("different")){different++;if(merged)falseMerged++;}else{uncertain++;if(merged)uncertainMerged++;}
                outcomes.put(new JSONObject().put("label",label).put("left",a.title).put("right",b.title).put("merged",merged));
            }
            check(falseMerged==0&&uncertainMerged==0,"third-source context does not merge labelled different or uncertain pairs");
            report.append("QUALITY whole_window_same=").append(sameMerged).append('/').append(same).append(" different=").append(falseMerged).append('/').append(different).append(" uncertain=").append(uncertainMerged).append('/').append(uncertain).append(" initial_events=").append(input.size()).append(" final_events=").append(finalRows.size()).append(" eligible_members=").append(allowed.size()).append('\n');
            JSONArray grouped=new JSONArray();for(FeedStore.Row row:finalRows)if(row.entries.size()>1){JSONArray entries=new JSONArray();for(HotCandidate c:row.entries)entries.put(c.json());grouped.put(new JSONObject().put("event_id",row.eventId).put("representative",row.candidate.json()).put("entries",entries));}
            long at=System.currentTimeMillis();long pending=store.commitRanking(store.version("sources"),store.version("profile"),at,"cold","all",finalRows);
            check(pending>old&&store.activeSnapshot()==old&&members(readAll(store,pending)).equals(allowed),"whole-window grouped pending snapshot roundtrips all members without activating itself");
            JSONObject output=new JSONObject().put("schema",1).put("corpus_sha256",labels.getString("corpus_sha256")).put("policy",ProspectiveEvaluation.policies()).put("initial_events",input.size()).put("final_events",finalRows.size()).put("eligible_members",allowed.size()).put("same_merged",sameMerged).put("same",same).put("different_merged",falseMerged).put("different",different).put("uncertain_merged",uncertainMerged).put("uncertain",uncertain).put("runs",runs).put("cache_elapsed_ms",cached.elapsed).put("cache_resources",cached.resources).put("labelled_outcomes",outcomes).put("grouped_events",grouped);
            try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"event-window-replay.json"))){out.write(output.toString().getBytes(StandardCharsets.UTF_8));}
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{storage.shutdown();try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=whole recorded public x86_64 window through ordinary default policy; not independent semantic precision, personal relevance or ARM/energy acceptance\n").toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
