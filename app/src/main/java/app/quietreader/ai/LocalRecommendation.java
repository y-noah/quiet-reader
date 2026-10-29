package app.quietreader.ai;

import android.content.Context;
import android.os.*;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;

/** One foreground, cancellable inference window. SQLite and scoring stay on the
 * supplied serial worker; installation has its own interruptible IO thread.
 * Per-item results survive cancellation in versioned caches, never as stale feeds.
 */
public final class LocalRecommendation implements AutoCloseable {
    // Package-private measurement seam. Every ordinary constructor remains DUAL.
    enum Stages { NONE, EMBEDDING, DUAL }
    private static Stages ordinaryStages(Context context){
        if(!app.quietreader.BuildConfig.RANKING_MEASUREMENT_ENABLED||!("app.quietreader.flowprobe".equals(context.getPackageName())||"app.quietreader.discoveryprobe".equals(context.getPackageName())))return Stages.DUAL;
        return Stages.valueOf(context.getSharedPreferences("isolated-measurement",0).getString("stages","DUAL"));
    }
    private final Stages stages;
    private final ModelWorkUsage usage=new ModelWorkUsage();
    public String resourceDescription(){return usage.description();}
    public interface Listener {
        void status(String message);
        void complete(List<FeedStore.Row> rows,boolean semantic,int encoded,int total,int refined,String status);
        default void comparison(RecommendationComparison comparison){}
    }
    private final Context context;private final FeedStore store;private final ExecutorService storage;
    private final ExecutorService io=Executors.newSingleThreadExecutor();private Future<?> installation;
    private final Handler main=new Handler(Looper.getMainLooper());private final Listener listener;
    private final List<FeedStore.Row> inputNeutral;private List<FeedStore.Row> neutral;private final SemanticOrder.Profile profile;
    private List<EventVerificationPlan.Task> eventTasks=Collections.emptyList();
    private final Map<String,Float> eventLogits=new HashMap<>();
    private final Map<String,Boolean> eventCoverage=new HashMap<>();
    private boolean eventMode,eventComplete,eventAssembled;private long eventDeadline;
    private final long generation,profileVersion,sourceVersion;
    private final Map<String,String> inputs=new LinkedHashMap<>();private final Map<String,float[]> vectors=new HashMap<>();
    private final Map<String,Map<String,Float>> logits=new HashMap<>();private List<SemanticOrder.Scored> ranked;
    private List<Map.Entry<String,List<SemanticOrder.Scored>>> groups=Collections.emptyList();private int groupIndex;
    private RankingClient client;private String revision="",kind="",query="";private Map<String,String> batchKeys=Collections.emptyMap();
    private long batch,deadline;private volatile boolean closed,finishing;private int refined;
    private RecommendationPolicy.Watch policyWatch;
    private boolean fullyComputed,continueAutomatically;
    private int persistedItems;
    private boolean comparisonRequested,started;
    /** Opt in before start; ordinary browsing performs no additional comparison scoring. */
    public LocalRecommendation requestComparison(){if(stages!=Stages.DUAL)throw new IllegalStateException("Three-order comparison requires dual execution");if(started)throw new IllegalStateException("Comparison must be requested before start");comparisonRequested=true;return this;}
    private boolean recovering,fallbackAttempted;
    interface ModelAccess {
        ModelFiles prepare()throws Exception;
        ModelFiles fallback(String failedRevision)throws Exception;
    }
    private final ModelAccess models;
    private final java.util.function.BooleanSupplier memoryPressure;
    public boolean fullyComputed(){return fullyComputed;}
    /** Only a normal budget yield with newly persisted results may continue. */
    public boolean canContinueAutomatically(){return continueAutomatically;}
    private final Runnable limit=()->finish("本轮整理已暂停，已算结果保留",true);
    public LocalRecommendation(Context context,FeedStore store,ExecutorService storage,List<FeedStore.Row> neutral,SemanticOrder.Profile profile,long generation,long profileVersion,long sourceVersion,Listener listener){
        this(context,store,storage,neutral,profile,generation,profileVersion,sourceVersion,listener,()->ProcessMemoryBudget.sample(context).shouldPause());
    }
    public LocalRecommendation(Context context,FeedStore store,ExecutorService storage,List<FeedStore.Row> neutral,SemanticOrder.Profile profile,long generation,long profileVersion,long sourceVersion,Listener listener,java.util.function.BooleanSupplier memoryPressure){
        this(context,store,storage,neutral,profile,generation,profileVersion,sourceVersion,listener,memoryPressure,new ModelAccess(){
            public ModelFiles prepare()throws Exception{return ModelFiles.prepare(context);}
            public ModelFiles fallback(String revision)throws Exception{return ModelFiles.afterExecutionFailure(context,revision);}
        });
    }
    LocalRecommendation(Context context,FeedStore store,ExecutorService storage,List<FeedStore.Row> neutral,SemanticOrder.Profile profile,long generation,long profileVersion,long sourceVersion,Listener listener,java.util.function.BooleanSupplier memoryPressure,ModelAccess models){
        this(context,store,storage,neutral,profile,generation,profileVersion,sourceVersion,listener,memoryPressure,models,ordinaryStages(context));
    }
    LocalRecommendation(Context context,FeedStore store,ExecutorService storage,List<FeedStore.Row> neutral,SemanticOrder.Profile profile,long generation,long profileVersion,long sourceVersion,Listener listener,java.util.function.BooleanSupplier memoryPressure,ModelAccess models,Stages stages){
        this.stages=java.util.Objects.requireNonNull(stages);
        this.context=context.getApplicationContext();this.store=store;this.storage=storage;this.inputNeutral=Collections.unmodifiableList(new ArrayList<>(neutral));this.neutral=inputNeutral;this.profile=profile;this.generation=generation;this.profileVersion=profileVersion;this.sourceVersion=sourceVersion;this.listener=listener;
        this.memoryPressure=java.util.Objects.requireNonNull(memoryPressure);
        this.models=java.util.Objects.requireNonNull(models);
    }
    public void start(){
        if(closed||started)return;started=true;
        if(stages==Stages.NONE){fullyComputed=true;finish("已使用基础顺序");return;}
        String restriction=RecommendationPolicy.restriction(context);if(!restriction.isEmpty()){finish(restriction);return;}
        policyWatch=new RecommendationPolicy.Watch(context,this::finish);policyWatch.start();if(finishing||closed)return;
        listener.status("正在准备本机推荐");
        installation=io.submit(()->{
            try{ModelFiles files=models.prepare();revision=files.revision();
                main.post(()->{if(!closed)loadCache();});
            }catch(Exception ex){main.post(()->{if(!closed)finish("本地模型尚未就绪，现有内容仍可阅读");});}
        });
    }
    private void execute(Runnable operation){
        if(closed)return;
        try{storage.execute(()->{if(closed)return;try{operation.run();}catch(Exception ex){main.post(()->{if(!closed)finish("本机缓存暂不可用，已保留当前顺序");});}});}catch(RejectedExecutionException ignored){close();}
    }
    private void loadCache(){
        execute(()->{
            for(SemanticOrder.Sample sample:profile.samples)inputs.put(sample.key,sample.text);
            for(FeedStore.Row row:neutral){String text=SemanticOrder.input(row.candidate.modelText());inputs.putIfAbsent(SemanticOrder.key(text),text);}
            if(stages==Stages.DUAL)for(HotCandidate member:eventCandidates()){String text=SemanticOrder.input(member.modelText());inputs.putIfAbsent(SemanticOrder.key(text),text);}
            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                for(String key:inputs.keySet()){if(closed)return;float[] vector=store.vector(FeedStore.vectorKey(key,revision,"wordpiece-v1"),System.currentTimeMillis());if(vector!=null)vectors.put(key,vector);}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            main.post(()->{if(closed||finishing)return;if(deadline==0){deadline=SystemClock.elapsedRealtime()+15_000;main.postDelayed(limit,15_000);}listener.status("正在按你的反馈整理");embeddingBatch();});
        });
    }
    private void embeddingBatch(){
        if(!allowed())return;
        execute(()->{
            LinkedHashMap<String,String> missing=new LinkedHashMap<>();int chars=0;
            for(Map.Entry<String,String> input:inputs.entrySet())if(!vectors.containsKey(input.getKey())){if(missing.size()==48||chars+input.getValue().length()>45_000)break;missing.put(input.getKey(),input.getValue());chars+=input.getValue().length();}
            main.post(()->{if(closed||finishing)return;if(missing.isEmpty()){if(stages==Stages.DUAL)planEvents();else planRerank();}else submit("embedding","",missing,Collections.emptyMap());});
        });
    }
    private List<HotCandidate> eventCandidates(){
        List<HotCandidate> candidates=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(FeedStore.Row row:inputNeutral)for(HotCandidate member:row.entries)if(seen.add(member.id)){candidates.add(member);if(candidates.size()==EventVerificationPlan.MAX_CANDIDATES)return candidates;}
        return candidates;
    }
    private void planEvents(){
        if(!allowed())return;
        execute(()->{
            eventTasks=EventVerificationPlan.create(eventCandidates(),vectors,revision,()->closed||finishing);
            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                for(EventVerificationPlan.Task task:eventTasks)for(boolean reverse:new boolean[]{false,true}){String key=EventRerankCacheKey.of(task,reverse);FeedStore.EventRerank value=store.eventRerank(key,System.currentTimeMillis());if(value!=null){eventLogits.put(key,value.logit);eventCoverage.put(key,value.complete);}}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            main.post(()->{if(!allowed())return;eventMode=true;eventDeadline=EventVerificationBudget.deadline(SystemClock.elapsedRealtime(),deadline,!profile.samples.isEmpty());eventBatch();});
        });
    }
    private void assembleEvents(){
        if(eventAssembled||eventTasks.isEmpty())return;
        List<EventGroupAssembly.ScoredPair> scored=new ArrayList<>();
        for(EventVerificationPlan.Task task:eventTasks){String ka=EventRerankCacheKey.of(task,false),kb=EventRerankCacheKey.of(task,true);Float a=eventLogits.get(ka),b=eventLogits.get(kb);if(a!=null&&b!=null)scored.add(new EventGroupAssembly.ScoredPair(task,a,b,Boolean.TRUE.equals(eventCoverage.get(ka))&&Boolean.TRUE.equals(eventCoverage.get(kb))));}
        neutral=EventGroupAssembly.apply(inputNeutral,profile.feedback(),scored,revision);eventAssembled=true;
    }
    private void eventBatch(){
        if(!allowed())return;
        execute(()->{
            EventVerificationPlan.Task missing=null;boolean reverse=false;
            outer:for(EventVerificationPlan.Task task:eventTasks)for(boolean direction:new boolean[]{false,true})if(!eventLogits.containsKey(EventRerankCacheKey.of(task,direction))){missing=task;reverse=direction;break outer;}
            if(missing==null||SystemClock.elapsedRealtime()>=eventDeadline){
                eventComplete=missing==null;assembleEvents();main.post(()->{if(closed||finishing)return;eventMode=false;planRerank();});return;
            }
            EventVerificationPlan.Task selected=missing;boolean direction=reverse;String key=EventRerankCacheKey.of(selected,direction);
            String question=direction?selected.rightInput:selected.leftInput,text=direction?selected.leftInput:selected.rightInput;
            main.post(()->{if(!allowed())return;if(SystemClock.elapsedRealtime()>=eventDeadline){eventBatch();return;}LinkedHashMap<String,String> texts=new LinkedHashMap<>();texts.put(key,text);submit("reranker",question,texts,Collections.singletonMap(key,key));});
        });
    }
    private void planRerank(){
        if(closed||finishing)return;
        if(stages==Stages.EMBEDDING){fullyComputed=true;finish("已完成初筛");return;}
        execute(()->{
            ranked=SemanticOrder.rank(neutral,profile,vectors);groups=new ArrayList<>(SemanticOrder.rerankGroups(ranked).entrySet());groupIndex=0;
            android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                for(Map.Entry<String,List<SemanticOrder.Scored>> group:groups){Map<String,Float> values=logits.computeIfAbsent(group.getKey(),ignored->new HashMap<>());
                    for(SemanticOrder.Scored candidate:group.getValue()){String key=rerankKey(group.getKey(),candidate.row.candidate);Float value=store.rerank(key,System.currentTimeMillis());if(value!=null)values.put(candidate.row.eventId,value);}}
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
            main.post(()->{if(!closed&&!finishing)rerankBatch();});
        });
    }
    private String rerankKey(String query,HotCandidate candidate){return RerankCacheKey.of(revision,query,candidate.modelText());}
    private void rerankBatch(){
        if(!allowed())return;
        execute(()->{
            LinkedHashMap<String,String> texts=new LinkedHashMap<>(),keys=new LinkedHashMap<>();String selected="";
            while(groupIndex<groups.size()){
                Map.Entry<String,List<SemanticOrder.Scored>> group=groups.get(groupIndex);selected=group.getKey();
                if(group.getValue().size()<2){groupIndex++;continue;}
                Map<String,Float> values=logits.computeIfAbsent(selected,ignored->new HashMap<>());int chars=selected.length();
                for(SemanticOrder.Scored candidate:group.getValue())if(!values.containsKey(candidate.row.eventId)){
                    String text=SemanticOrder.input(candidate.row.candidate.modelText());if(chars+text.length()>45_000)break;texts.put(candidate.row.eventId,text);keys.put(candidate.row.eventId,rerankKey(selected,candidate.row.candidate));chars+=text.length();}
                if(!texts.isEmpty())break;groupIndex++;
            }
            String query=selected;main.post(()->{if(closed||finishing)return;if(texts.isEmpty()){fullyComputed=eventComplete;finish(eventComplete?"已按你的反馈整理":"推荐已整理，重复话题复核将在下轮继续",!eventComplete);}else submit("reranker",query,texts,keys);});
        });
    }
    private boolean allowed(){
        if(closed||finishing||recovering)return false;
        String restriction=RecommendationPolicy.restriction(context);if(!restriction.isEmpty()){finish(restriction);return false;}
        if(deadline>0&&SystemClock.elapsedRealtime()>=deadline){finish("本轮整理已暂停，已算结果保留",true);return false;}return true;
    }
    private void submit(String kind,String query,LinkedHashMap<String,String> texts,Map<String,String> cacheKeys){
        if(!allowed())return;
        execute(()->{
            boolean overBudget=memoryPressure.getAsBoolean();
            main.post(()->{
                if(!allowed())return;
                if(overBudget){finish("内存占用较高或暂时无法确认，已暂停整理，当前内容仍可阅读");return;}
                submitMeasured(kind,query,texts,cacheKeys);
            });
        });
    }
    private void submitMeasured(String kind,String query,LinkedHashMap<String,String> texts,Map<String,String> cacheKeys){
        if(!allowed())return;this.kind=kind;this.query=query;this.batchKeys=cacheKeys;batch++;
        usage.begin(kind,texts.keySet());
        if(client==null)client=new RankingClient(context,new RankingClient.Listener(){
            public void onResult(int type,Bundle data){received(type,data);}
            public void onClosed(){if(!closed&&!finishing)finish("整理已暂停，已算结果保留");}
        });
        Bundle request=new Bundle();request.putString("modelRevision",revision);request.putString("kind",kind);request.putString("query",query);request.putStringArray("ids",texts.keySet().toArray(new String[0]));request.putStringArray("texts",texts.values().toArray(new String[0]));request.putLong("budgetMs",2000);request.putLong("generation",generation);request.putLong("profileVersion",profileVersion);request.putLong("sourceRevision",sourceVersion);request.putLong("batch",batch);client.submit(request);
    }
    private void received(int type,Bundle data){
        if(closed||finishing||recovering||type==RankingService.HELLO)return;
        if(!revision.equals(data.getString("modelRevision","")))return;
        if(data.getLong("generation")!=generation||data.getLong("profileVersion")!=profileVersion||data.getLong("sourceRevision")!=sourceVersion||data.getLong("batch")!=batch)return;
        if(type==RankingService.ITEM){
            String id=data.getString("id","");
            if(kind.equals("embedding")){float[] vector=data.getFloatArray("vector");String acquiredRevision=revision;if(!inputs.containsKey(id)||vector==null)return;usage.item(id,data.getLong("elapsedMs",-1),data.getLong("processCpuMs",-1));execute(()->{store.putVector(id,acquiredRevision,"wordpiece-v1",vector,System.currentTimeMillis());vectors.put(id,vector);persistedItems++;});}
            else{String key=batchKeys.get(id),query=this.query;boolean event=eventMode,complete=data.getBoolean("inputComplete",false);float logit=data.getFloat("logit",Float.NaN);if(key==null||!Float.isFinite(logit))return;usage.item(id,data.getLong("elapsedMs",-1),data.getLong("processCpuMs",-1));execute(()->{if(event){store.putEventRerank(key,logit,complete,System.currentTimeMillis());eventLogits.put(key,logit);eventCoverage.put(key,complete);}else{store.putRerank(key,logit,System.currentTimeMillis());logits.computeIfAbsent(query,ignored->new HashMap<>()).put(id,logit);}refined++;persistedItems++;});}
        }else if(type==RankingService.DONE){
            usage.done(data.getLong("loadMs",-1),data.getLong("loadCpuMs",-1));
            if(data.getInt("completed")==0){finish("本轮整理已暂停，已算结果保留");return;}
            // Yield between slices; no alarm survives close/background/reading.
            main.postDelayed(kind.equals("embedding")?this::embeddingBatch:eventMode?this::eventBatch:this::rerankBatch,250);
        }else if(type==RankingService.ERROR){
            String code=data.getString("code","");
            if(code.equals("MODEL_EXECUTION_FAILED")||code.equals("MODEL_NOT_INSTALLED"))recover();
            else finish("本轮整理已暂停，已算结果保留");
        }
    }
    private void recover(){
        if(!allowed())return;
        if(fallbackAttempted){finish(modelUnavailable());return;}
        fallbackAttempted=true;recovering=true;String failedRevision=revision;batch++;
        // The worker closes its old engine before switching revision. Keep this
        // connection: releasing/rebinding could race the process's delayed exit.
        // Hashing shares the original foreground deadline, never a new window.
        listener.status("正在检查可用的本机模型");
        installation=io.submit(()->{
            try{
                ModelFiles fallback=models.fallback(failedRevision);
                main.post(()->{
                    if(closed||finishing)return;
                    if(fallback==null||fallback.revision().equals(failedRevision)){recovering=false;finish(modelUnavailable());return;}
                    recovering=false;if(!allowed())return;recovering=true;
                    execute(()->{
                        // Storage is serial: all prior ITEM writes complete first.
                        // No failed-version vector/logit participates in the retry.
                        vectors.clear();logits.clear();eventLogits.clear();eventCoverage.clear();eventTasks=Collections.emptyList();eventMode=false;eventComplete=false;eventAssembled=false;neutral=inputNeutral;inputs.clear();ranked=null;groups=Collections.emptyList();groupIndex=0;refined=0;persistedItems=0;fullyComputed=false;
                        revision=fallback.revision();kind="";query="";batchKeys=Collections.emptyMap();
                        main.post(()->{if(closed||finishing)return;recovering=false;if(allowed())loadCache();});
                    });
                });
            }catch(Exception failure){main.post(()->{if(!closed&&!finishing){recovering=false;finish(modelUnavailable());}});}
        });
    }
    private static String modelUnavailable(){return "本地模型暂不可用，可在本机推荐中校验并修复；已保留可用匹配结果";}
    private void finish(String message){finish(message,false);}
    private void finish(String message,boolean yielded){
        if(closed||finishing)return;finishing=true;main.removeCallbacksAndMessages(null);if(client!=null){client.close();client=null;}io.shutdownNow();
        if(policyWatch!=null){policyWatch.close();policyWatch=null;}
        execute(()->{
            continueAutomatically=yielded&&!fullyComputed&&persistedItems>0;
            assembleEvents();
            List<SemanticOrder.Scored> scored=SemanticOrder.rank(neutral,profile,vectors);List<FeedStore.Row> result=SemanticOrder.fuse(scored,logits,profile.platforms);
            boolean semantic=profile.samples.stream().anyMatch(s->vectors.containsKey(s.key))
                    &&neutral.stream().anyMatch(r->vectors.containsKey(SemanticOrder.key(r.candidate.modelText())));
            int count=vectors.size(),total=inputs.size(),fine=refined;
            RecommendationComparison comparison=null;
            if(comparisonRequested&&fullyComputed&&semantic&&count==total&&!neutral.isEmpty()){
                int scores=0;for(Map<String,Float> values:logits.values())for(Float value:values.values())if(value!=null&&Float.isFinite(value))scores++;
                comparison=new RecommendationComparison(
                    SemanticOrder.fuse(SemanticOrder.rank(neutral,profile,Collections.emptyMap()),Collections.emptyMap(),profile.platforms),
                    SemanticOrder.fuse(scored,Collections.emptyMap(),profile.platforms),result,revision,profile.signature,generation,profileVersion,sourceVersion,scores);
            }
            RecommendationComparison frozen=comparison;
            main.post(()->{if(closed)return;if(frozen!=null)listener.comparison(frozen);listener.complete(result,semantic,count,total,fine,message);close();});
        });
    }
    @Override public void close(){if(closed)return;closed=true;main.removeCallbacksAndMessages(null);if(policyWatch!=null){policyWatch.close();policyWatch=null;}if(installation!=null)installation.cancel(true);io.shutdownNow();if(client!=null){client.close();client=null;}}
}

