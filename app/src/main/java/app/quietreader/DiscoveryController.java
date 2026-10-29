package app.quietreader;

import android.app.Activity;
import android.os.*;
import app.quietreader.discovery.*;
import app.quietreader.ai.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static app.quietreader.Models.*;

/** Owns foreground collection and frozen feeds. No model runs on the UI thread. */
final class DiscoveryController implements AutoCloseable {
    interface Listener {
        void feed(List<FeedStore.Row> rows,int position,int offset,boolean newest);
        void sources(Map<Source,JSONObject> states);
        void state(boolean loading,boolean updateAvailable);
        void error(String message);
        default void refreshWork(boolean running){}
        default void interestSetup(boolean needed){}
        default void recommendationState(RecommendationState state){}
        default void newPresentation(){}
        /** Completes in the same frame as the accepted home list is drawn. */
        default void beforeFeedDraw(Runnable complete){complete.run();}
        default int feedCapacity(List<FeedStore.Row> candidates){return FeedBatch.SIZE;}
    }
    private final Activity activity;
    private final Listener listener;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FeedStore store;
    private final HotSourceCollector collector;
    private ArticlePreloader preloader;
    private final ImagePreloader imagePreloader;
    private int viewport;
    private int visibleCount=1;
    private final PrefetchWindow cacheWindow=new PrefetchWindow();
    private boolean feedPage=true;
    private boolean clearingBodies,bodySupplyQueued;
    private final Runnable preparedSupply=this::flushBodySupply;
    private long prefetchEpoch,spaceCheckedAt;
    private String cacheBatchKey="";
    private DataStorageBudget.FreeSpace storageSpace;
    private final ActionPrefetchBudget prefetchBudget=new ActionPrefetchBudget();
    private final Runnable expirePrefetch=()->{prefetchBudget.clear();pausePreload();scheduleRecommendation(false);};
    private final Runnable prefetch=this::prefetchWindow;
    private final boolean autoRefresh;
    private boolean closed,foreground,ready,autoStarted,loading,newest;
    private long snapshot,pending;
    private long visibleSourceVersion,visibleProfileVersion;
    private volatile int feedGeneration;
    private volatile long progressEpoch;
    private long explicitRefreshEpoch;
    private boolean explicitRefreshRunning;
    private volatile long recommendationEpoch;
    private long completedSource=-1,completedProfile=-1;
    private LocalRecommendation recommendation;
    private Consumer<String> trialFailure;
    private final ExecutorService maintenance=Executors.newSingleThreadExecutor();
    private Future<?> modelRepair;
    private long repairEpoch;
    private RecommendationState recommendationState=RecommendationState.INITIAL;
    private boolean interacting;
    private long lastRecommendation;
    private final Runnable recommend=this::beginRecommendation;
    // Coalesce fast sources without waiting for the slowest source. Existing
    // visible cards still change only through the explicit pending-update path.
    private final Runnable partialSupply=()->{if(!closed&&foreground&&loading)prepare(false);};
    private String anchor="";
    private int position,offset;
    private List<FeedStore.Row> rows=Collections.emptyList();
    private List<FeedStore.Row> allRows=Collections.emptyList();
    private volatile String sourceFilter="all";
    private String visibleMode="cold";
    private Map<Source,JSONObject> states=new EnumMap<>(Source.class);
    private static final class Snapshot {
        long id;JSONObject info;List<FeedStore.Row> rows;
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh){
        this(activity,listener,autoRefresh,null);
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh,HotSourceCollector.Transport transport){
        this(activity,listener,autoRefresh,transport,null);
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh,HotSourceCollector.Transport transport,String restoredSource){
        this.activity=activity;this.listener=listener;this.autoRefresh=autoRefresh;
        if(restoredSource!=null)sourceFilter=SourceFilter.source(restoredSource);
        imagePreloader=new ImagePreloader(activity,()->canPrefetch()&&storageSpace!=null&&storageSpace.allowsPrefetch()&&!preloader.busy());
        imagePreloader.observeIdle(()->scheduleRecommendation(false));
        preloader=new ArticlePreloader(activity,()->{},null,40,this::bodiesIdle,true);
        preloader.observePrepared(this::bodyPrepared);
        String database=activity.getIntent().getStringExtra("test_database");
        boolean diagnostic=(activity.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&database!=null&&database.matches("test-ui-[a-z0-9-]+\\.db");
        store=diagnostic?new FeedStore(activity,database):new FeedStore(activity);
        HotSourceCollector.Listener collectionListener=new HotSourceCollector.Listener(){
            public void onSourceChanged(Source source){readSources();viewport(viewport);}
            public void onBrowserStarting(Source source){pausePreload();}
            public void onSnapshotCommitted(Source source){main.removeCallbacks(partialSupply);main.postDelayed(partialSupply,250);}
            public void onFinished(){main.removeCallbacks(partialSupply);loading=false;state();finishCollectedSupply();viewport(viewport);}
            public void onStorageFailure(){listener.error("本机存储暂不可用，已显示的内容保留");}
        };
        collector=transport==null?new HotSourceCollector(activity,store,storage,collectionListener):new HotSourceCollector(store,storage,collectionListener,transport);
        work(()->{
            store.ensureCandidatePolicy();
            store.recoverInterruptedRefreshes();
            long id=store.activeSnapshot();JSONObject previous=id==0?null:store.snapshotInfo(id);
            // A configuration change may precede the asynchronous filter commit.
            // Restore the user's selected source before rebuilding the snapshot.
            if(previous!=null&&restoredSource==null)sourceFilter=SourceFilter.source(previous.optString("filter"));
            if(previous==null||!sourceFilter.equals(previous.optString("filter"))||previous.optLong("sourceVersion")!=store.version("sources")||previous.optLong("profileVersion")!=store.profile(System.currentTimeMillis()).getLong("version")){
                id=build(previous!=null&&"newest".equals(previous.optString("mode")));
                if(id>0&&previous!=null)store.anchor(id,previous.optString("anchor"),previous.optInt("offset"));
            }
            return read(id);
        },loaded->{ready=true;accept(loaded,false);readSources();readInterestSetup();maybeStart();if(!loading)scheduleRecommendation(false);});
    }
    private <T>void work(Callable<T> operation,Consumer<T> then){
        work(operation,then,()->true);
    }
    private <T>void work(Callable<T> operation,Consumer<T> then,java.util.function.BooleanSupplier failureCurrent){
        if(closed)return;
        int generation=feedGeneration;long refreshRequest=explicitRefreshEpoch;
        storage.execute(()->{try{T result=operation.call();main.post(()->{if(!closed)then.accept(result);});}
            // Success callbacks retain their existing operation-specific guards.
            // A late failure must likewise belong to the current action before
            // it can cancel its spinner, collection or model state.
            catch(Exception ex){main.post(()->{if(!closed&&generation==feedGeneration&&refreshRequest==explicitRefreshEpoch&&failureCurrent.getAsBoolean()){loading=false;cancelExplicitRefresh();if(recommendationState.phase==RecommendationState.Phase.RUNNING){cancelRecommendation();recommendationStatus(RecommendationState.Phase.PAUSED,"本机内容暂时无法更新，请稍后重试");}state();listener.error("本机内容暂时无法更新，请稍后重试");}});}});
    }
    private Snapshot read(long id)throws Exception{
        Snapshot result=new Snapshot();result.id=id;result.info=store.snapshotInfo(id);result.rows=new ArrayList<>();
        if(result.info!=null)for(int start=0;start<result.info.getInt("count");start+=200)result.rows.addAll(store.readSnapshot(id,200,start));
        return result;
    }
    private long build(boolean latest)throws Exception{
        return build(latest,Collections.emptyList());
    }
    private long build(boolean latest,List<FeedStore.Row> preserve)throws Exception{
        long now=System.currentTimeMillis(),source=store.version("sources");JSONObject profile=store.profile(now);
        List<FeedStore.Row> ordered=CandidateOrder.arrange(store.candidates(5000,0),profile.getJSONArray("feedback"),latest);
        if(!preserve.isEmpty())ordered=CandidateOrder.preserveVisibleGroups(ordered,preserve,profile.getJSONArray("feedback"));
        return store.commitRanking(source,profile.getLong("version"),now,latest?"newest":new PlatformPreference(profile.getJSONArray("feedback")).empty()?"cold":"platform",sourceFilter,ordered);
    }
    private void accept(Snapshot next,boolean keepAnchor){
        if(next.info==null)return;snapshot=next.id;allRows=next.rows;sourceFilter=SourceFilter.source(next.info.optString("filter"));List<FeedStore.Row> eligible=SourceFilter.apply(allRows,sourceFilter);rows=FeedBatch.first(eligible,listener.feedCapacity(eligible));visibleMode=next.info.optString("mode");newest="newest".equals(visibleMode);visibleSourceVersion=next.info.optLong("sourceVersion");visibleProfileVersion=next.info.optLong("profileVersion");
        if(keepAnchor){int found=-1;for(int n=0;n<rows.size();n++)if(EventGroups.matches(rows.get(n),anchor)){found=n;break;}position=found<0?Math.min(position,Math.max(0,rows.size()-1)):found;}
        else{position=0;String savedAnchor=next.info.optString("anchor");for(int n=0;n<rows.size();n++)if(EventGroups.matches(rows.get(n),savedAnchor)){position=n;break;}offset=next.info.optInt("offset");}
        pending=0;long accepted=snapshot;work(()->{store.activateSnapshot(accepted);return null;},ignored->{});
        listener.feed(Collections.unmodifiableList(rows),position,offset,newest);state();
        viewport(position);
        cacheAcceptedBatch(explicitRefreshRunning);
    }
    private void prepare(boolean apply){
        prepare(apply,false);
    }
    private void finishCollectedSupply(){
        int generation=feedGeneration;
        work(()->{
            Snapshot latest=new Snapshot();latest.id=store.latestSnapshot();latest.info=store.snapshotInfo(latest.id);
            long profile=store.profile(System.currentTimeMillis()).getLong("version"),source=store.version("sources");
            if(latest.info!=null&&(latest.info.optLong("sourceVersion")!=source||latest.info.optLong("profileVersion")!=profile))latest.info=null;
            return latest;
        },latest->{
            if(generation!=feedGeneration||!foreground||loading)return;
            if(latest.info==null||!sourceFilter.equals(SourceFilter.normalize(latest.info.optString("filter")))||newest!="newest".equals(latest.info.optString("mode"))){prepare(false);return;}
            // An earlier partial batch may already represent every committed
            // source. Reuse it; a final failure is not a new content update.
            if(latest.id!=snapshot){pending=latest.id;if(rows.isEmpty())applyPending(false);else{state();viewport(viewport);}}
            scheduleRecommendation(false);
        });
    }
    private void prepare(boolean apply,boolean retainOrder){
        cancelBodySupply();readInterestSetup();
        // A rebuilt neutral snapshot needs semantic ordering again, even when
        // switching back from chronological mode with unchanged data versions.
        completedSource=-1;completedProfile=-1;
        cancelRecommendation();boolean latest=newest;int generation=++feedGeneration;pending=0;
        List<FeedStore.Row> preserve=retainOrder?new ArrayList<>(allRows):Collections.emptyList();
        work(()->build(latest,preserve),id->{if(id<0||generation!=feedGeneration)return;pending=id;if(apply||rows.isEmpty())applyPending(apply);else{state();viewport(viewport);}scheduleRecommendation(false);});
    }
    // Only the explicit update action renews speculation. Automatic snapshot
    // publication also uses applyPending and must never extend the lease.
    void acceptUpdates(){
        if(closed||!foreground||pending<=0)return;
        refreshRecommendations();
    }
    /** Swap the available pool immediately; automatic collection/model callbacks
     * keep staging updates and never acquire permission to replace reading. */
    void refreshRecommendations(){
        if(closed||!foreground||!ready||!feedPage||explicitRefreshRunning)return;
        cacheAction();explicitRefreshing(true);long request=++explicitRefreshEpoch;
        int generation=feedGeneration;long capturedPending=pending,id=pending>0?pending:snapshot;
        boolean collect=capturedPending<=0&&!loading;String filter=sourceFilter;boolean latest=newest;
        refreshPresentation(request,generation,capturedPending,id,collect,filter,latest,0);
    }
    private void refreshPresentation(long request,int generation,long capturedPending,long id,boolean collect,String filter,boolean latest,int attempt){
        ArticleProgressBinding.CacheContext context=new ArticleProgressBinding.CacheContext(activity);
        long progressGeneration=progressEpoch;
        // Most refreshes have no verified body revision. Scan bounded scalar metadata
        // from the requested snapshot, not the old visible one, before CookieManager.
        work(()->{if(progressGeneration!=progressEpoch||!context.current())return new ArticleProgressPreparation();try{return ArticleProgressPreparation.read(store,id,System.currentTimeMillis());}catch(Exception unavailable){return new ArticleProgressPreparation();}},prepared->{
            if(request!=explicitRefreshEpoch)return;
            if(generation!=feedGeneration||!foreground||!feedPage){explicitRefreshing(false);return;}
            if(progressGeneration!=progressEpoch||!context.current()){explicitRefreshing(false);listener.error("本次更新暂未完成，已保留当前内容");return;}
            Map<String,ArticleProgressBinding.Scope> scopes=new HashMap<>();
            for(HotCandidate member:prepared.candidates.values()){ArticleProgressBinding.Scope scope=ArticleProgressBinding.scope(activity,member);if(scope!=null)scopes.put(member.id,scope);}
            refreshWithProgress(request,generation,capturedPending,id,collect,filter,latest,attempt,context,progressGeneration,scopes,prepared.followups);
        });
    }
    private void refreshWithProgress(long request,int generation,long capturedPending,long id,boolean collect,String filter,boolean latest,int attempt,ArticleProgressBinding.CacheContext context,long progressGeneration,Map<String,ArticleProgressBinding.Scope> progressScopes,List<ArticleFollowupEvidence.Proof> followups){
        work(()->{
            try{
                if(progressGeneration!=progressEpoch||!context.current())return new Snapshot();
                ArticleProgressHistory history=new ArticleProgressHistory(store.getWritableDatabase());
                try{history.maintain(System.currentTimeMillis());}catch(RuntimeException ignored){/* Optional cleanup is bounded to this action. */}
                FeedStore.ProgressLookup lookup=(row,recent,now)->{if(progressGeneration!=progressEpoch)return 0;long proof=0,cutoff=Math.max(recent.shownAt,recent.readAt);for(HotCandidate member:row.entries){ArticleProgressBinding.Scope scope=progressScopes.get(member.id);if(scope!=null&&scope.current()){long value=history.progress(member.id,scope.key,cutoff,now);if(scope.current()&&progressGeneration==progressEpoch)proof=Math.max(proof,value);}}
                    for(ArticleFollowupEvidence.Proof followup:followups)proof=Math.max(proof,followup.current(store.getWritableDatabase(),row,progressScopes,cutoff));
                    return progressGeneration==progressEpoch?proof:0;};
                long refreshed=store.refreshSnapshot(id,filter,System.currentTimeMillis(),lookup);
                if(refreshed<0){long rebuilt=build(latest);if(rebuilt>0)refreshed=store.refreshSnapshot(rebuilt,filter,System.currentTimeMillis(),lookup);}
                return refreshed>0?read(refreshed):new Snapshot();
            }catch(Exception failure){return new Snapshot();}
        },next->{
            if(request!=explicitRefreshEpoch)return;
            if(generation!=feedGeneration||!foreground||!feedPage){explicitRefreshing(false);return;}
            // A result superseding the clicked identity is read under the same
            // action, with a bounded retry rather than an unbounded callback loop.
            if(pending>0&&pending!=capturedPending&&attempt<2){refreshPresentation(request,generation,pending,pending,collect,filter,latest,attempt+1);return;}
            if(next.info==null||progressGeneration!=progressEpoch||!context.current()||progressScopes.values().stream().anyMatch(scope->!scope.current())){explicitRefreshing(false);listener.error("本次更新暂未完成，已保留当前内容");return;}
            // A newer pending computation belongs to a later explicit action.
            long newer=pending!=capturedPending?pending:0;
            try{next.info.put("anchor","").put("offset",0);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
            // Only a validated publication starts another exposure cycle.
            // Queued, coalesced or cancelled requests leave the visible cycle intact.
            anchor="";position=0;offset=0;listener.newPresentation();accept(next,false);
            if(newer>0&&newer!=next.id){pending=newer;state();}
            listener.beforeFeedDraw(()->{
                if(request!=explicitRefreshEpoch)return;
                explicitRefreshing(false);
                if(collect&&foreground&&feedPage)refresh();
            });
        });
    }
    private void explicitRefreshing(boolean running){if(explicitRefreshRunning==running)return;explicitRefreshRunning=running;listener.refreshWork(running);}
    private void cancelExplicitRefresh(){explicitRefreshEpoch++;explicitRefreshing(false);}
    void applyPending(boolean keepAnchor){
        long id=pending;int generation=feedGeneration;if(id<=0)return;
        work(()->{
            Snapshot next=read(id);
            long profile=store.profile(System.currentTimeMillis()).getLong("version"),source=store.version("sources");
            // Commit-time validation alone is insufficient: feedback or source
            // collection may advance while the user keeps browsing the old list.
            if(next.info!=null&&(next.info.optLong("profileVersion")!=profile||next.info.optLong("sourceVersion")!=source))next.info=null;
            return next;
        },value->{
            if(generation!=feedGeneration)return;
            // A newer result can arrive while this clicked result is being
            // read. Consume the current pending identity instead of letting
            // the old accept (or stale-version rebuild) discard that result.
            if(pending!=id){if(pending>0)applyPending(keepAnchor);return;}
            if(value.info==null){pending=0;if(modelRepair==null)recommendationStatus(RecommendationState.Phase.IDLE,"来源或偏好已更新，旧结果未应用；正在准备最新结果");prepare(false);state();return;}
            accept(value,keepAnchor);
        });
    }
    void sort(boolean newest){cacheWindow.reset();this.newest=newest;prepare(true);}
    String sourceFilter(){return SourceFilter.source(sourceFilter);}
    JSONObject trialSnapshot(){try{return RecommendationTrialStore.freeze(new ArrayList<>(rows),snapshot,visibleSourceVersion,visibleProfileVersion,sourceFilter,visibleMode,System.currentTimeMillis());}catch(org.json.JSONException failure){throw new IllegalStateException(failure);}}
    void cancelTrialComparison(){if(trialFailure!=null)cancelRecommendation();}
    private long trialAppEpoch()throws Exception{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).lastUpdateTime;}
    void prepareProspectiveTrial(Consumer<JSONObject> success,Consumer<String> failure){
        cancelBodySupply();
        if(closed||!ready||!foreground||loading||modelRepair!=null){failure.accept("请等来源更新完成后再试。");return;}
        work(()->{try{return store.evaluationBaseline(System.currentTimeMillis(),new ModelFiles(activity).revision(),trialAppEpoch());}
            catch(IllegalArgumentException error){return new JSONObject().put("error",error.getMessage());}},value->{if(value.has("error"))failure.accept(value.optString("error"));else success.accept(value);});
    }
    void trialComparison(Consumer<RecommendationComparison> success,Consumer<String> failure){trialComparison(null,success,failure);}
    void trialComparison(JSONObject baseline,Consumer<RecommendationComparison> success,Consumer<String> failure){
        if(closed||!ready||!foreground||loading||modelRepair!=null){failure.accept("请等来源更新完成后再试。");return;}
        String restriction=RecommendationPolicy.restriction(activity);if(!restriction.isEmpty()){failure.accept(restriction);return;}
        pausePreload();cancelRecommendation();trialFailure=failure;long epoch=recommendationEpoch;
        recommendationStatus(RecommendationState.Phase.RUNNING,"正在准备本轮对比");
        work(()->{RankingInput input=new RankingInput();JSONObject profile=store.profile(System.currentTimeMillis());input.profile=profile.getLong("version");input.source=store.version("sources");input.interests=new SemanticOrder.Profile(profile.getJSONArray("feedback"));List<FeedStore.Row> available=CandidateOrder.forModels(store.candidates(5000,0),profile.getJSONArray("feedback"));try{input.rows=baseline==null?new ArrayList<>(available.subList(0,Math.min(100,available.size()))):store.evaluationCandidates(baseline,System.currentTimeMillis(),new ModelFiles(activity).revision(),trialAppEpoch());if(!input.rows.isEmpty())input.learning=store.comparisonLearning(input.rows,System.currentTimeMillis(),input.profile,input.source);}catch(IllegalArgumentException error){input.error=error.getMessage();input.rows=Collections.emptyList();}return input;},input->{
            if(epoch!=recommendationEpoch||trialFailure==null||!foreground)return;
            if(input.rows.isEmpty()||input.interests.empty()){trialFailure=null;recommendationStatus(RecommendationState.Phase.IDLE,"本轮对比尚未准备");failure.accept(input.error!=null?input.error:input.rows.isEmpty()?(baseline==null?"请先刷新来源获取热门内容。":"准备之后还没有足够的新话题，请刷新来源后再试。旧话题和本组已评价候选不会重复加入。"):"请先选择一些想多看或少看的内容，再开始对比。");return;}
            RecommendationComparison[] captured={null};
            recommendation=new LocalRecommendation(activity,store,storage,input.rows,input.interests,feedGeneration,input.profile,input.source,new LocalRecommendation.Listener(){
                public void status(String message){if(epoch==recommendationEpoch)recommendationStatus(RecommendationState.Phase.RUNNING,message);}
                public void comparison(RecommendationComparison value){captured[0]=value;}
                public void complete(List<FeedStore.Row> ordered,boolean semantic,int encoded,int total,int refined,String status){
                    if(epoch!=recommendationEpoch||trialFailure==null)return;recommendation=null;
                    if(captured[0]==null){trialFailure=null;recommendationStatus(RecommendationState.Phase.PAUSED,status);failure.accept("本轮计算尚未完成，已保留计算缓存，可以稍后重试。");return;}
                    work(()->store.version("sources")==input.source&&store.profile(System.currentTimeMillis()).getLong("version")==input.profile,current->{
                        if(epoch!=recommendationEpoch||trialFailure==null||!foreground)return;trialFailure=null;recommendationStatus(RecommendationState.Phase.IDLE,"本轮对比已结束，当前阅读列表保留");
                        if(current){try{RecommendationComparison value=captured[0].withLearning(input.learning,System.currentTimeMillis());success.accept(baseline==null?value:value.withProtocol(baseline));}catch(IllegalArgumentException error){failure.accept(error.getMessage());}}else failure.accept("来源或偏好刚刚变化，请重新开始对比。");
                    },()->epoch==recommendationEpoch&&trialFailure!=null&&foreground);
                }
            });recommendation.requestComparison().start();
        },()->epoch==recommendationEpoch&&trialFailure!=null&&foreground);
    }
    ContentCategory categoryFilter(){return SourceFilter.category(sourceFilter);}
    void filterSource(String selected){
        cacheWindow.reset();
        filterSelection(SourceFilter.combine(selected,ContentCategory.ALL));
    }
    void filterCategory(ContentCategory selected){filterSelection(SourceFilter.combine(sourceFilter(),selected));}
    private void filterSelection(String selected){
        String filter=SourceFilter.normalize(selected);if(filter.equals(sourceFilter))return;
        pausePreload();cancelRecommendation();sourceFilter=filter;int generation=++feedGeneration;pending=0;
        List<FeedStore.Row> frozen=new ArrayList<>(allRows);String mode=visibleMode;long source=visibleSourceVersion,profile=visibleProfileVersion;
        anchor="";position=0;offset=0;
        work(()->store.commitRanking(source,profile,System.currentTimeMillis(),mode,filter,frozen),id->{
            if(generation!=feedGeneration)return;if(id<0){prepare(true);return;}pending=id;applyPending(false);
        });
    }
    void start(){
        if(foreground)return;foreground=true;interacting=false;cacheAction();maybeStart();viewport(viewport);cacheAcceptedBatch(false);
        String restriction=RecommendationPolicy.restriction(activity);if(!restriction.isEmpty())recommendationStatus(RecommendationState.Phase.PAUSED,restriction);
        if(ready){scheduleRecommendation(false);readSources();work(()->new long[]{store.version("sources"),store.profile(System.currentTimeMillis()).getLong("version")},versions->{if(versions[0]!=visibleSourceVersion||versions[1]!=visibleProfileVersion)prepare(false);});}
    }
    private void maybeStart(){
        if(ReaderStartup.pending()||!ready||!foreground||autoStarted||!autoRefresh||states.isEmpty())return;autoStarted=true;
        long now=System.currentTimeMillis();List<Source> stale=new ArrayList<>();
        for(Source source:HotSourceCatalog.HOT){JSONObject s=states.get(source);if(s!=null&&s.optBoolean("enabled")&&s.optLong("lastSuccessAt")<now-15*60_000&&s.optLong("retryAt")<=now)stale.add(source);}
        if(!stale.isEmpty())refresh(stale);
    }
    void readerReady(){if(!closed){maybeStart();viewport(viewport);cacheAcceptedBatch(false);scheduleRecommendation(false);}}
    void refresh(){refresh(HotSourceCatalog.HOT);}
    void refresh(Collection<Source> targets){if(!ready||!foreground)return;cacheAction();main.removeCallbacks(partialSupply);feedGeneration++;pending=0;pausePreload();cancelRecommendation();loading=true;state();collector.refresh(targets);}
    void retryBrowser(Source source){if(!ready||!foreground)return;main.removeCallbacks(partialSupply);feedGeneration++;pending=0;pausePreload();cancelRecommendation();loading=true;state();collector.retryInBrowser(source);}
    void stop(){if(!foreground)return;progressEpoch++;cancelExplicitRefresh();cacheWindow.stop();foreground=false;cacheBatchKey="";cancelBodySupply();prefetchBudget.clear();main.removeCallbacks(expirePrefetch);main.removeCallbacks(partialSupply);feedGeneration++;pending=0;pausePreload();cancelRepair();cancelRecommendation();collector.cancel();loading=false;state();persistAnchor();}
    void remember(int position,int offset){this.position=position;this.offset=offset;anchor=position>=0&&position<rows.size()?rows.get(position).eventId:"";}
    private void persistAnchor(){long id=snapshot;String value=anchor;int y=offset;if(id>0)work(()->{store.anchor(id,value,y);return null;},ignored->{});}
    private void state(){listener.state(loading,pending>0);}
    void readSources(){work(()->{Map<Source,JSONObject> result=new EnumMap<>(Source.class);for(Source source:HotSourceCatalog.HOT)result.put(source,store.sourceState(source));return result;},value->{states=value;listener.sources(value);maybeStart();});}
    void enable(Source source,boolean enabled){pausePreload();cancelRecommendation();collector.cancel();loading=false;feedGeneration++;pending=0;work(()->{store.enable(source,enabled);return null;},ignored->{readSources();prepare(true);if(enabled)refresh(Collections.singletonList(source));});}
    void feedback(FeedStore.Row row,String action){
        pausePreload();
        String id=UUID.randomUUID().toString();boolean enough="ENOUGH".equals(action),block="BLOCK".equals(action);feedGeneration++;pending=0;state();
        cancelRecommendation();work(()->store.feedback(id,action,enough||block?"event":"sample",enough||block?EventTargets.capture(row,enough):CandidateOrder.versionTarget(row.candidate),System.currentTimeMillis(),null,row.candidate.modelText(),row.candidate.source),ignored->{prepare(enough||block,enough||block);listener.error(block?"已屏蔽这条内容，可在反馈记录中撤销":"已记下，可在偏好中撤销");});
    }
    void platformFeedback(Source source,boolean more){
        pausePreload();cancelRecommendation();feedGeneration++;pending=0;state();
        work(()->store.feedback(UUID.randomUUID().toString(),more?"MORE":"LESS","source",source.name(),System.currentTimeMillis(),null,source.label,source),ignored->{prepare(false);listener.error("已记下"+source.label+"的平台偏好，可在反馈记录中撤销");});
    }
    void separate(FeedStore.Row row){cancelRecommendation();feedGeneration++;pending=0;String id=UUID.randomUUID().toString();String target=EventTargets.capture(row,false);work(()->store.feedback(id,"SEPARATE","event",target,System.currentTimeMillis(),null,row.candidate.title),ignored->{prepare(true,true);listener.error("已分开显示，可在反馈记录中撤销");});}
    void feedbackHistory(Consumer<JSONArray> callback){work(()->store.activeFeedback(System.currentTimeMillis()),callback);}
    void follow(String description,int days){
        String sample=HotCandidate.clean(description,240);
        if(sample.codePointCount(0,sample.length())<2){listener.error("请写下至少两个字的关注内容");return;}
        if(days!=0&&days!=1&&days!=7&&days!=30)throw new IllegalArgumentException("Unsupported follow duration");
        cancelRecommendation();feedGeneration++;pending=0;state();
        long now=System.currentTimeMillis();Long expires=days==0?null:now+days*86_400_000L;
        String id=UUID.randomUUID().toString();
        work(()->store.feedback(id,"FOLLOW","sample",SemanticOrder.key(sample),now,expires,sample),ignored->{prepare(false);listener.error(days==0?"已添加长期关注，可在反馈记录中结束":"已关注 "+days+" 天，可在反馈记录中提前结束");});
    }
    void undo(String id){cancelRecommendation();feedGeneration++;pending=0;work(()->store.undo(id,System.currentTimeMillis()),ignored->{prepare(true);listener.error("已撤销这条反馈");});}
    void resetProfile(){cancelRecommendation();feedGeneration++;pending=0;work(()->{store.resetProfile();return null;},ignored->{prepare(true);listener.error("已重置偏好");});}
    void clearRecommendationCache(){
        storageSpace=null;
        pausePreload();
        cancelRecommendation();collector.cancel();loading=false;feedGeneration++;pending=0;state();
        work(()->{store.clearDerivedCache();return store.reclaimCacheSpace();},reclaimed->{
            anchor="";position=0;offset=0;readSources();prepare(true);
            recommendationStatus(RecommendationState.Phase.IDLE,"推荐缓存已清理，刷新来源后可重新整理");
            listener.error(reclaimed?"推荐缓存已清理并释放存储空间":"推荐缓存已清理，部分存储空间暂未释放，可稍后重试");
        });
    }
    void clearArticleCache(){
        if(closed||clearingBodies)return;
        storageSpace=null;clearingBodies=true;pausePreload();
        Repository cache=new Repository(activity);cache.invalidateArticles();cache.close();ReaderImages.shared.evictAll();
        work(()->{
            try{boolean files=new ArticleDiskCache(activity).clearFiles();boolean images=new ImageDiskCache(new java.io.File(activity.getCacheDir(),"reader-images-v1")).clearFiles();boolean history=new ReadingHistory(activity).clearBodies();return files&&images&&history;}
            catch(Exception error){return false;}
        },cleared->{clearingBodies=false;listener.error(cleared?"阅读缓存已清理，阅读进度保留":"部分阅读缓存未能清理，可稍后重试");});
    }
    String recommendationStatus(){return recommendationState.detail;}
    private void recommendationStatus(RecommendationState.Phase phase,String detail){
        if(closed||recommendationState.phase==phase&&recommendationState.detail.equals(detail))return;
        recommendationState=new RecommendationState(phase,detail);listener.recommendationState(recommendationState);
    }
    void readInterestSetup(){work(()->store.needsInterestSetup(System.currentTimeMillis()),listener::interestSetup);}
    List<HotCandidate> interestCandidates(){return InterestSetup.candidates(allRows);}
    void finishInterestSetup(List<HotCandidate> candidates,Map<String,Boolean> choices){
        pausePreload();cancelRecommendation();feedGeneration++;pending=0;
        List<HotCandidate> frozen=new ArrayList<>(candidates);Map<String,Boolean> selected=new LinkedHashMap<>(choices);
        work(()->{store.finishInterestSetup(frozen,selected,System.currentTimeMillis());return null;},ignored->{readInterestSetup();prepare(false);listener.error(selected.isEmpty()?"可以先浏览，随时在设置里选择兴趣":"已记录 "+selected.size()+" 条选择，可在反馈记录中撤销");});
    }
    void readLearningState(Consumer<Boolean> callback){work(store::readLearningEnabled,callback);}
    void readLearning(boolean enabled){cancelRecommendation();feedGeneration++;pending=0;work(()->{store.readLearningEnabled(enabled,System.currentTimeMillis());return null;},ignored->{prepare(false);listener.error(enabled?"已开启阅读弱参考":"已关闭，并撤回阅读产生的弱参考");});}
    void storageStatus(Consumer<String> callback){work(()->DataStorageBudget.measure(activity.getDataDir()),value->callback.accept(value.description()));}
    void repairModels(){
        if(closed||!foreground||modelRepair!=null)return;
        pausePreload();
        cancelRecommendation();completedSource=-1;completedProfile=-1;
        recommendationStatus(RecommendationState.Phase.REPAIRING,"正在校验离线模型，损坏的文件将从安装包恢复");listener.error(recommendationStatus());
        long epoch=++repairEpoch;
        modelRepair=maintenance.submit(()->{
            String result;boolean success;try{new ModelFiles(activity).repair();result="离线模型校验完成，可继续整理";success=true;}
            catch(Exception ex){result=Thread.currentThread().isInterrupted()?"模型校验已暂停，可稍后重试":"模型修复未完成，请检查存储空间后重试";success=false;}
            String message=result;boolean repaired=success;main.post(()->{if(closed||epoch!=repairEpoch)return;modelRepair=null;recommendationStatus(repaired?RecommendationState.Phase.IDLE:RecommendationState.Phase.PAUSED,message);listener.error(message);});
        });
    }
    private void cancelRepair(){repairEpoch++;if(modelRepair!=null){modelRepair.cancel(true);modelRepair=null;recommendationStatus(RecommendationState.Phase.PAUSED,"模型校验已暂停，可稍后重试");}}
    void requestRecommendation(){cancelBodySupply();cancelRecommendation();completedSource=-1;completedProfile=-1;newest=false;feedGeneration++;scheduleRecommendation(true);}
    void interacting(boolean value){if(value!=interacting)cacheAction();interacting=value;if(value){pausePreload();cancelRecommendation();}else{viewport(viewport);scheduleRecommendation(false);}}
    void scrollState(int state){if(state==0)cacheWindow.idle();else{if(explicitRefreshRunning)cancelExplicitRefresh();cacheWindow.gesture(state==2,SystemClock.elapsedRealtime());}interacting(state!=0);}
    void scrollViewport(int first,int count,int top){cacheWindow.viewport(first,top,SystemClock.elapsedRealtime());visibleCount=Math.max(1,count);viewport(first);}
    void exposed(Collection<String> events){
        if(closed||!foreground||!feedPage||snapshot<=0||events.isEmpty()||events.size()>64)return;
        long shownSnapshot=snapshot,at=System.currentTimeMillis();List<String> captured=new ArrayList<>(events);
        Set<String> wanted=new HashSet<>(captured);Map<String,ArticleProgressBinding.Scope> scopes=new HashMap<>();
        for(FeedStore.Row row:allRows)if(wanted.contains(row.eventId))for(HotCandidate member:row.entries)if(scopes.size()<64&&!scopes.containsKey(member.id)){ArticleProgressBinding.Scope scope=ArticleProgressBinding.scope(activity,member);if(scope!=null)scopes.put(member.id,scope);}
        // Optional presentation facts must not cancel collection/ranking on storage failure.
        storage.execute(()->{try{
            if(scopes.isEmpty()){store.recordExposed(shownSnapshot,captured,at);return;}
            List<HotCandidate> accepted=store.recordExposedMembers(shownSnapshot,captured,at);ArticleProgressHistory history=new ArticleProgressHistory(store.getWritableDatabase());
            for(HotCandidate member:accepted){ArticleProgressBinding.Scope scope=scopes.get(member.id);if(scope!=null&&scope.current())try{history.presented(member.id,scope.key,at,System.currentTimeMillis());}catch(RuntimeException ignored){/* Exposure facts remain committed if derivative body storage fails. */}}
        }catch(RuntimeException ignored){/* Fail open as unseen; never invent feedback or retry in the background. */}});
    }
    void feedPage(boolean value){if(value&&!feedPage)cacheAction();feedPage=value;if(!value){progressEpoch++;cancelExplicitRefresh();pausePreload();cancelRecommendation();}else viewport(viewport);}
    private void cacheAction(){
        if(closed||!foreground)return;
        prefetchBudget.action(SystemClock.elapsedRealtime());
        armPrefetchExpiry();
    }
    private void armPrefetchExpiry(){main.removeCallbacks(expirePrefetch);main.postDelayed(expirePrefetch,prefetchBudget.remaining(SystemClock.elapsedRealtime()));}
    private void cacheAcceptedBatch(boolean force){
        if(!autoRefresh||closed||!foreground||!feedPage||rows.isEmpty()||ReaderStartup.pending()||ReaderStartup.failed())return;
        StringBuilder identity=new StringBuilder();Set<String> keep=new HashSet<>();
        for(FeedStore.Row row:rows){Item item=row.candidate.readerItem();identity.append(item.source.name()).append('|').append(item.url).append('@').append(ArticleVersion.hash(item)).append('\n');keep.add(item.url);}
        String key=HotCandidate.hash(identity.toString());if(!force&&key.equals(cacheBatchKey))return;boolean grant=force||cacheBatchKey.isEmpty();cacheBatchKey=key;
        pausePreload();cancelRecommendation();
        if(grant){prefetchBudget.batch(SystemClock.elapsedRealtime(),rows.size());armPrefetchExpiry();}
        Repository cache=new Repository(activity);ArticleDiskCache.Retention retained=cache.retainBatch(keep);cache.close();
        Runnable pruneImages=ReaderImages.shared.retainBatch(keep);
        storage.execute(()->{try{new ArticleDiskCache(activity).pruneBatch(retained);pruneImages.run();}catch(RuntimeException ignored){/* Derivative cleanup must not undo the accepted screen. */}});
        main.post(prefetch);
    }
    void viewport(int first){prefetchEpoch++;imagePreloader.pause();viewport=Math.max(0,first);main.removeCallbacks(prefetch);if(autoRefresh&&feedPage&&foreground&&!interacting&&!closed)main.postDelayed(prefetch,650);}
    String handoff(Item item){
        imagePreloader.pause();
        String token=ArticleHandoff.offer(item,preloader);
        if(token!=null){preloader=new ArticlePreloader(activity,()->{},null,40,this::bodiesIdle,true);preloader.observePrepared(this::bodyPrepared);}
        return token;
    }
    String readerPrefetchPlan(HotCandidate candidate){long now=SystemClock.elapsedRealtime();return autoRefresh&&!closed?ReaderPrefetchPlan.from(rows,candidate,cacheWindow.direction(now),cacheWindow.rapid(now)).encode():null;}
    private void bodyPrepared(Item item,Document document){
        if(closed||document.cachedSession==null||!document.cachedSession.equals(ArticleDiskCache.session(item.url))||!new ArticleDiskCache(activity).namespace().equals(document.cachedNamespace)||!item.url.equals(document.url))return;
        imagePreloader.prepared(item,document);
        ContentLanguage.Body language=BodyLanguageEvidence.classify(document);
        boolean video=BodyVideoEvidence.classify(item.source,document);
        boolean textTopic=BodyVideoEvidence.textTopic(item.source,document);
        if(language==ContentLanguage.Body.UNKNOWN&&!video&&!textTopic)return;
        HotCandidate candidate=preparedCandidate(item);
        if(candidate!=null){
            if(!ArticleVersion.matches(candidate.readerItem(),document))return;
            ArticleProgressBinding progress=foreground&&feedPage?ArticleProgressBinding.capture(activity,candidate,document):null;long progressGeneration=progressEpoch;
            long bodyAt=document.cachedAt;
            work(()->{if(progress!=null)try{progress.observe(store,()->progressGeneration==progressEpoch);}catch(RuntimeException ignored){/* Optional evidence does not interrupt existing body admission. */}
                if(video)return store.recordVideo(candidate.id,candidate.textHash,bodyAt);
                boolean changed=textTopic&&store.recordTextTopic(candidate.id,candidate.textHash,bodyAt);
                return store.recordBodyLanguage(candidate.id,candidate.textHash,language,bodyAt)||changed;
            },changed->{if(changed)queueBodySupply();});
        }
    }
    private HotCandidate preparedCandidate(Item item){
        for(FeedStore.Row row:allRows)for(HotCandidate candidate:row.entries)if(candidate.source==item.source&&candidate.originalUrl.equals(item.url)&&candidate.title.equals(item.title)&&candidate.snippet.equals(item.snippet))return candidate;
        return null;
    }
    private void queueBodySupply(){
        if(closed||!foreground||trialFailure!=null||bodySupplyQueued)return;
        bodySupplyQueued=true;main.postDelayed(preparedSupply,500);
    }
    private void flushBodySupply(){
        bodySupplyQueued=false;
        if(!closed&&foreground)prepare(false);
    }
    private void cancelBodySupply(){bodySupplyQueued=false;main.removeCallbacks(preparedSupply);}
    private void pausePreload(){prefetchEpoch++;main.removeCallbacks(prefetch);imagePreloader.pause();preloader.pause();}
    private void bodiesIdle(){imagePreloader.start();scheduleRecommendation(false);}
    private void prefetchWindow(){
        if(!canPrefetch())return;
        long epoch=prefetchEpoch;
        if(storageSpace!=null&&SystemClock.elapsedRealtime()-spaceCheckedAt<15_000){startPrefetch();return;}
        work(()->DataStorageBudget.freeSpace(activity.getDataDir()),value->{
            if(epoch!=prefetchEpoch||!canPrefetch())return;
            storageSpace=value;spaceCheckedAt=SystemClock.elapsedRealtime();startPrefetch();
        });
    }
    private boolean canPrefetch(){return prefetchBudget.active(SystemClock.elapsedRealtime())&&!ReaderStartup.pending()&&!ReaderStartup.failed()&&trialFailure==null&&autoRefresh&&feedPage&&!closed&&foreground&&!interacting&&collector.allowsPrefetch()&&!clearingBodies&&modelRepair==null&&recommendation==null&&RecommendationPolicy.restriction(activity).isEmpty();}
    private void startPrefetch(){
        if(!canPrefetch()||storageSpace==null)return;
        if(!storageSpace.allowsPrefetch()){imagePreloader.pause();preloader.pause();return;}
        offerPrefetch();
    }
    private void offerPrefetch(){
        if(!canPrefetch())return;
        List<Item> window=new ArrayList<>();for(int n:cacheWindow.positions(rows.size(),viewport,visibleCount,SystemClock.elapsedRealtime()))window.add(rows.get(n).candidate.readerItem());
        imagePreloader.offer(window);preloader.offer(window,FeedBatch.SIZE,true);
    }
    void memoryPressure(){cancelBodySupply();prefetchBudget.clear();main.removeCallbacks(expirePrefetch);cancelRepair();suspendRecommendation("内存紧张，已暂停整理");}
    void suspendRecommendation(String reason){pausePreload();cancelRecommendation();lastRecommendation=SystemClock.elapsedRealtime();recommendationStatus(RecommendationState.Phase.PAUSED,reason);}
    private void cancelRecommendation(){recommendationEpoch++;main.removeCallbacks(recommend);if(recommendation!=null){recommendation.close();recommendation=null;}Consumer<String> interrupted=trialFailure;trialFailure=null;if(recommendationState.phase==RecommendationState.Phase.RUNNING)recommendationStatus(RecommendationState.Phase.PAUSED,"本次整理已暂停，当前列表仍可阅读");if(interrupted!=null)interrupted.accept("本轮对比已停止，已保存的计算缓存会保留。");}
    private void scheduleRecommendation(boolean explicit){
        main.removeCallbacks(recommend);if(closed||!ready||!foreground||interacting||newest||loading||modelRepair!=null||trialFailure!=null)return;
        long delay=explicit?0:Math.max(800,lastRecommendation+15_000-SystemClock.elapsedRealtime());main.postDelayed(recommend,delay);
    }
    private static final class RankingInput {String error;JSONObject learning;long source,profile;List<FeedStore.Row> rows;SemanticOrder.Profile interests;}
    private void beginRecommendation(){
        if(ReaderStartup.pending()||closed||!feedPage||!foreground||interacting||newest||loading||bodySupplyQueued||modelRepair!=null||preloader.busy()||imagePreloader.busy()||trialFailure!=null)return;
        String restriction=RecommendationPolicy.restriction(activity);if(!restriction.isEmpty()){recommendationStatus(RecommendationState.Phase.PAUSED,restriction);return;}
        cancelRecommendation();int generation=feedGeneration;long epoch=recommendationEpoch;
        work(()->{
            // A cancelled request may still be waiting behind real storage work.
            // Reject it before profile expiry/IO and before building model rows;
            // the UI callback check alone only prevented stale publication.
            if(epoch!=recommendationEpoch||generation!=feedGeneration)return null;
            RankingInput input=new RankingInput();long now=System.currentTimeMillis();JSONObject profile=store.profile(now);
            input.profile=profile.getLong("version");input.source=store.version("sources");
            List<HotCandidate> candidates=store.candidates(5000,0);
            if(epoch!=recommendationEpoch||generation!=feedGeneration)return null;
            input.interests=new SemanticOrder.Profile(profile.getJSONArray("feedback"));input.rows=CandidateOrder.forModels(candidates,profile.getJSONArray("feedback"));return input;
        },input->{
            if(input==null||epoch!=recommendationEpoch||generation!=feedGeneration||!foreground||!feedPage||interacting||newest||closed||loading||preloader.busy()||imagePreloader.busy())return;
            if(input.source==completedSource&&input.profile==completedProfile){recommendationStatus(RecommendationState.Phase.READY,"本轮结果已准备，可继续浏览或查看更新");return;}
            if(input.rows.isEmpty()){recommendationStatus(RecommendationState.Phase.IDLE,"暂无可整理话题，请先刷新来源");return;}
            lastRecommendation=SystemClock.elapsedRealtime();
            imagePreloader.pause();
            recommendation=new LocalRecommendation(activity,store,storage,input.rows,input.interests,generation,input.profile,input.source,new LocalRecommendation.Listener(){
                public void status(String status){if(epoch==recommendationEpoch)recommendationStatus(RecommendationState.Phase.RUNNING,status);}
                public void complete(List<FeedStore.Row> ordered,boolean semantic,int encoded,int total,int refined,String status){
                    if(epoch!=recommendationEpoch||generation!=feedGeneration||!foreground||newest)return;
                    boolean complete=recommendation!=null&&recommendation.fullyComputed();
                    boolean continueAutomatically=recommendation!=null&&recommendation.canContinueAutomatically();
                    String resources=recommendation==null?"":recommendation.resourceDescription();
                    recommendation=null;String detail=status+" · 已缓存向量 "+encoded+"/"+total+"，本轮精排 "+refined+" 条\n"+resources;
                    String filter=sourceFilter;
                    work(()->store.commitRanking(input.source,input.profile,System.currentTimeMillis(),semantic?"personal":input.interests.platforms.empty()?"cold":"platform",filter,ordered),id->{
                        if(epoch!=recommendationEpoch||generation!=feedGeneration||!foreground)return;
                        if(id<0){recommendationStatus(RecommendationState.Phase.PAUSED,"来源或偏好已更新，当前列表保留，可继续整理");scheduleRecommendation(false);return;}
                        if(complete){completedSource=input.source;completedProfile=input.profile;}pending=id;recommendationStatus(complete?RecommendationState.Phase.READY:semantic?RecommendationState.Phase.PARTIAL:RecommendationState.Phase.PAUSED,detail);state();viewport(viewport);
                        // Completion must not depend on a cache/layout callback renewing work.
                        if(continueAutomatically)scheduleRecommendation(false);
                    },()->epoch==recommendationEpoch&&foreground);
                }
            });recommendation.start();
        },()->epoch==recommendationEpoch&&foreground);
    }
    @Override public void close(){
        if(closed)return;progressEpoch++;cancelExplicitRefresh();cancelBodySupply();prefetchBudget.clear();main.removeCallbacks(expirePrefetch);main.removeCallbacks(partialSupply);pausePreload();preloader.close();imagePreloader.close();cancelRepair();maintenance.shutdownNow();cancelRecommendation();persistAnchor();collector.close();closed=true;storage.execute(store::close);storage.shutdown();
    }
}

