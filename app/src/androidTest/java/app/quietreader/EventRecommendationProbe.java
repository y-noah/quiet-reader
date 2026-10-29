package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.Models;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Ordinary DUAL LocalRecommendation and actual RankingClient/service/cache.
 * Public replay candidates; no server, source requests, user DB or phone. */
public final class EventRecommendationProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    public EventRecommendationProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private static final class Result {List<FeedStore.Row> rows;RecommendationComparison comparison;boolean complete,semantic;int refined;String resources,status;}
    private Result run(FeedStore store,List<FeedStore.Row> rows,boolean comparison)throws Exception{
        long now=System.currentTimeMillis();SemanticOrder.Profile profile=new SemanticOrder.Profile(store.activeFeedback(now));long source=store.version("sources"),version=store.version("profile");Result result=new Result();CountDownLatch done=new CountDownLatch(1);LocalRecommendation[] current={null};
        test.runOnMainSync(()->{
            current[0]=new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,1,version,source,new LocalRecommendation.Listener(){
                public void status(String message){}
                public void comparison(RecommendationComparison value){result.comparison=value;}
                public void complete(List<FeedStore.Row> value,boolean semantic,int encoded,int total,int refined,String status){result.rows=value;result.semantic=semantic;result.refined=refined;result.complete=current[0].fullyComputed();result.resources=current[0].resourceDescription();result.status=status;done.countDown();}
            },()->false);
            if(comparison)current[0].requestComparison();current[0].start();
        });
        if(!done.await(35,TimeUnit.SECONDS)){test.runOnMainSync(()->current[0].close());throw new IllegalStateException("Ordinary event recommendation timeout");}
        report.append("RUN comparison=").append(comparison).append(" rows=").append(result.rows.size()).append(" refined=").append(result.refined).append(" complete=").append(result.complete).append(" status=").append(result.status).append('\n').append(result.resources).append('\n');return result;
    }
    private static Object field(Object owner,String name){try{java.lang.reflect.Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private boolean workerAlive(){List<ActivityManager.RunningAppProcessInfo> processes=((ActivityManager)test.getTargetContext().getSystemService(Activity.ACTIVITY_SERVICE)).getRunningAppProcesses();return processes!=null&&processes.stream().anyMatch(p->p.processName.equals(test.getTargetContext().getPackageName()+":ranking"));}
    private void cancellation(JSONObject fixture)throws Exception{
        String database="test-event-cancel-"+UUID.randomUUID()+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            JSONObject pair=fixture.getJSONArray("pairs").getJSONObject(0);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));List<FeedStore.Row> base=CandidateOrder.forModels(Arrays.asList(a,b),new JSONArray());
            long sourceVersion=store.version("sources"),profileVersion=store.version("profile");
            LocalRecommendation[] current={null};CountDownLatch canceled=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger completions=new java.util.concurrent.atomic.AtomicInteger(),late=new java.util.concurrent.atomic.AtomicInteger();java.util.concurrent.atomic.AtomicBoolean received=new java.util.concurrent.atomic.AtomicBoolean();
            test.runOnMainSync(()->{current[0]=new LocalRecommendation(test.getTargetContext(),store,storage,base,new SemanticOrder.Profile(new JSONArray()),2,profileVersion,sourceVersion,new LocalRecommendation.Listener(){public void status(String text){}public void complete(List<FeedStore.Row> rows,boolean semantic,int encoded,int total,int refined,String status){completions.incrementAndGet();}},()->false);current[0].start();});
            long until=android.os.SystemClock.elapsedRealtime()+10000;RankingClient[] client={null};
            while(client[0]==null&&android.os.SystemClock.elapsedRealtime()<until){test.runOnMainSync(()->client[0]=(RankingClient)field(current[0],"client"));if(client[0]==null)Thread.sleep(20);}
            if(client[0]==null)throw new IllegalStateException("Actual event client did not bind");
            test.runOnMainSync(()->{
                RankingClient.Listener delegate=(RankingClient.Listener)field(client[0],"listener");RankingClient.Listener observer=new RankingClient.Listener(){
                    public void onResult(int type,Bundle data){if(canceled.getCount()==0)late.incrementAndGet();delegate.onResult(type,data);
                        if(type==RankingService.ITEM&&data.containsKey("logit")&&received.compareAndSet(false,true)){
                            // Close after the ordinary ITEM cache write has committed,
                            // before the 250ms next-direction scheduling yield.
                            storage.execute(()->new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{current[0].close();canceled.countDown();}));
                        }
                    }
                    public void onClosed(){delegate.onClosed();}
                };try{java.lang.reflect.Field listener=RankingClient.class.getDeclaredField("listener");listener.setAccessible(true);listener.set(client[0],observer);}catch(Exception error){throw new IllegalStateException(error);}
            });
            if(!canceled.await(20,TimeUnit.SECONDS)){test.runOnMainSync(()->current[0].close());throw new IllegalStateException("Event direction cancellation timeout");}
            storage.submit(()->{}).get(5,TimeUnit.SECONDS);
            until=android.os.SystemClock.elapsedRealtime()+5000;while(workerAlive()&&android.os.SystemClock.elapsedRealtime()<until)Thread.sleep(20);
            check(!workerAlive(),"closing during actual event verification retires the shared model process");check(completions.get()==0&&late.get()==0,"canceled event run publishes no feed or late model results");
            String revision=new ModelFiles(test.getTargetContext()).revision();Map<String,float[]> cached=new HashMap<>();for(HotCandidate c:Arrays.asList(a,b)){String key=SemanticOrder.key(c.modelText());cached.put(key,store.vector(FeedStore.vectorKey(key,revision,"wordpiece-v1"),System.currentTimeMillis()));}
            EventVerificationPlan.Task task=EventVerificationPlan.create(Arrays.asList(a,b),cached,revision).get(0);int directions=0;for(boolean reverse:new boolean[]{false,true})if(store.rerank(EventRerankCacheKey.of(task,reverse),System.currentTimeMillis())!=null)directions++;
            check(directions==1,"exactly one completed direction survives cancellation in ordinary cache");
            Result resumed=run(store,base,false);check(resumed.complete&&resumed.rows.size()==1&&resumed.refined==1,"resume computes only the missing direction before merging");
        }finally{test.getTargetContext().deleteDatabase(database);}
    }
    private void longInput()throws Exception{
        String database="test-event-long-"+UUID.randomUUID()+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            List<HotCandidate> pair=EventInputCoverageProbe.longPair();List<FeedStore.Row> base=CandidateOrder.forModels(pair,new JSONArray());String revision=new ModelFiles(test.getTargetContext()).revision();EventVerificationPlan.Task task=EventInputCoverageProbe.binding(pair,revision);
            for(boolean reverse:new boolean[]{false,true})store.putRerank(EventRerankCacheKey.of(task,reverse),99,System.currentTimeMillis());
            Result rescored=run(store,base,false);check(rescored.complete&&rescored.rows.size()==2&&rescored.refined==2,"ordinary pipeline recomputes legacy high scores without input coverage and rejects truncated merge");
            for(boolean reverse:new boolean[]{false,true}){FeedStore.EventRerank value=store.eventRerank(EventRerankCacheKey.of(task,reverse),System.currentTimeMillis());check(value!=null&&!value.complete,"actual worker persists truncated direction with complete=false");}
            Result cached=run(store,base,false);check(cached.complete&&cached.rows.size()==2&&cached.refined==0&&cached.resources.contains("初筛 0 条，精排 0 条"),"known truncated directions remain separate without repeated inference");
        }finally{test.getTargetContext().deleteDatabase(database);}
    }
    private void freshShortHeadlines()throws Exception{
        String database="test-event-fresh-"+UUID.randomUUID()+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-fresh-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            List<HotCandidate> candidates=new ArrayList<>();
            for(int index:new int[]{1,2})for(String side:List.of("left","right"))candidates.add(HotCandidate.fromJson(fixture.getJSONArray("pairs").getJSONObject(index).getJSONObject(side)));
            List<FeedStore.Row> base=CandidateOrder.forModels(candidates,new JSONArray());
            Result first=run(store,base,false);
            check(first.complete&&first.rows.size()==2&&first.refined==4,"ordinary cold pipeline merges actual short election and apology pairs via four real directions");
            check(first.rows.stream().mapToInt(row->row.entries.size()).sum()==4,"fresh ordinary merge preserves all four actual reading entries");
            Result cached=run(store,base,false);
            check(cached.complete&&cached.rows.size()==2&&cached.refined==0&&cached.resources.contains("初筛 0 条，精排 0 条"),"fresh repeat uses actual cached vectors and direction coverage without inference");
            FeedStore.Row merged=first.rows.stream().filter(row->row.candidate.title.contains("Kimi")).findFirst().orElseThrow();
            store.feedback("fresh-split","SEPARATE","event",EventTargets.capture(merged,false),System.currentTimeMillis(),null,merged.candidate.title);
            Result split=run(store,base,false);
            check(split.complete&&split.rows.size()==3&&split.refined==0,"fresh explicit split keeps apology entries separate while preserving election group and cache");
        }finally{test.getTargetContext().deleteDatabase(database);}
    }
    public void run(){String database="test-event-recommendation-"+UUID.randomUUID()+".db";
        if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated probe package required");
        for(String old:test.getTargetContext().databaseList())if(old.matches("test-event-(?:recommendation|cancel|long)-[a-f0-9-]+\\.db"))test.getTargetContext().deleteDatabase(old);
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-holdout-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            Map<Models.Source,List<HotCandidate>> sources=new EnumMap<>(Models.Source.class);List<HotCandidate> all=new ArrayList<>();
            for(int n=0;n<fixture.getJSONArray("pairs").length();n++)for(String side:Arrays.asList("left","right")){HotCandidate c=HotCandidate.fromJson(fixture.getJSONArray("pairs").getJSONObject(n).getJSONObject(side));all.add(c);sources.computeIfAbsent(c.source,ignored->new ArrayList<>()).add(c);}
            long now=System.currentTimeMillis();for(Map.Entry<Models.Source,List<HotCandidate>> source:sources.entrySet())store.upsertSnapshot(source.getKey(),store.beginRefresh(source.getKey()),source.getValue(),now);
            List<FeedStore.Row> base=CandidateOrder.forModels(all,new JSONArray());check(base.size()==24,"real replay starts with 24 exact-rule events");ModelFiles.prepare(test.getTargetContext());
            long old=store.commitRanking(store.version("sources"),store.version("profile"),now,"cold","all",base);store.activateSnapshot(old);
            Result first=run(store,base,false);check(first.complete&&first.rows.size()==23&&!first.semantic,"cold profile still executes actual event verification and merges the release pair");
            check(first.refined==4,"first cold run reports both directions for two actual candidate pairs");
            check(store.activeSnapshot()==old&&store.readSnapshot(old,200,0).size()==24,"ordinary computation leaves active frozen snapshot untouched");
            Result cached=run(store,base,false);check(cached.complete&&cached.rows.size()==23&&cached.refined==0,"second run uses persisted raw directions with no new reranker results");
            check(cached.resources.contains("初筛 0 条，精排 0 条"),"second run reports zero new model inference for cached candidates and pairs");
            store.feedback("event-profile","MORE","sample",all.get(0).id,System.currentTimeMillis(),null,"人工智能模型发布、软件开发与技术产品",Models.Source.ITHOME);
            long cutoff=System.currentTimeMillis();JSONObject learning=store.comparisonLearning(base,cutoff,store.version("profile"),store.version("sources"));
            Result comparison=run(store,base,true);check(comparison.complete&&comparison.comparison!=null,"ordinary dual comparison completes after shared event verification");
            if(comparison.comparison!=null){
                RecommendationComparison value=comparison.comparison.withLearning(learning,System.currentTimeMillis());
                check(value.neutral.size()==23&&value.embedding.size()==23&&value.dual.size()==23,"all three technical orders use the same assembled event pool");
                check(value.learning().getLong("learning_cutoff_ms")==learning.getLong("learning_cutoff_ms")&&value.learning().getString("ledger_sha256").equals(learning.getString("ledger_sha256")),"regrouped comparison preserves pre-ranking learning cutoff and ledger");
                check(value.learning().getJSONArray("candidate_events").length()==23,"comparison captures grouped events without dropping original members");
                JSONObject trial=RecommendationTrialStore.freezeComparison(value,System.currentTimeMillis(),new Random(1));RecommendationTrialStore.validate(trial);check(!trial.getBoolean("holdout_qualified"),"assembled three-order trial validates without claiming independent relevance holdout");
            }
            cancellation(fixture);
            longInput();
            freshShortHeadlines();
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{storage.shutdown();try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=ordinary LocalRecommendation/RankingClient on x86_64 emulator, not controller acceptance, ARM resources or personal relevance\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
