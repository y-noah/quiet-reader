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
        default void exhausted(boolean value){}
        default void prepareTopics(List<FeedStore.Row> candidates){}
        default boolean preparesTopics(){return false;}
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
    private volatile long searchRequest;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final FeedStore store;
    private final HotSourceCollector collector;
    private ArticlePreloader preloader;
    private final ImagePreloader imagePreloader;
    private final Runnable preparedImages=this::startPreparedImages;
    private void startPreparedImages(){imagePreloader.start();}
    private int viewport;
    private int visibleCount=1;
    private final PrefetchWindow cacheWindow=new PrefetchWindow();
    private final FeedReserve reserve=new FeedReserve();
    private final FeedReadiness readiness=new FeedReadiness();
    private final Set<String> verifying=new HashSet<>();
    private List<Item> deferredPublication;
    private Snapshot admission;
    private boolean admissionAnchor,admissionCommitting;
    private long admissionEpoch;
    private Runnable afterAdmission=()->{};
    private boolean cacheOffline(){return ReadingNetwork.offline(activity);}
    private long cacheAge(){return cacheOffline()?ArticleDiskCache.MAX_AGE:FeedReadiness.FRESH_MS;}
    private String bodyScope(HotCandidate candidate){return new ArticleSession(activity,candidate.readerItem()).scopeIdentity();}
    private List<FeedStore.Row> availableBodies(List<FeedStore.Row> ranked){
        List<FeedStore.Row> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(FeedStore.Row row:ranked)if(!readiness.hasFailure(row.candidate,now)||!readiness.failed(row.candidate,bodyScope(row.candidate),now))result.add(row);
        return result;
    }
    private List<FeedStore.Row> admissionPool(){
        if(admission==null)return Collections.emptyList();
        List<FeedStore.Row> result=new ArrayList<>();long now=System.currentTimeMillis();
        for(FeedStore.Row row:availableBodies(SourceFilter.apply(admission.rows,SourceFilter.source(admission.info.optString("filter"))))){
            result.add(row);
            if(result.size()==FeedReserve.CAPACITY)break;
        }
        return result;
    }
    private void bodyFailed(Item item){
        HotCandidate candidate=preparedCandidate(item);if(candidate==null)return;
        readiness.failed(FeedReadiness.key(candidate),bodyScope(candidate),System.currentTimeMillis());
        main.post(()->ArticleSessionSnapshot.run(()->{if(!closed){if(admission==null)reserve.stage(availableBodies(allRows),sourceFilter);cacheAcceptedBatch(false);tryAdmission();}}));
    }
    private static final class CachedProof {final HotCandidate candidate;final String scope;final long at;CachedProof(HotCandidate candidate,String scope,long at){this.candidate=candidate;this.scope=scope;this.at=at;}}
    /** Local cache restore is allowed before speculative/network resource policy. */
    private void restoreAdmission(){
        if(admission==null||ReaderStartup.pending())return;
        ArticleSessionSnapshot.run(this::restoreAdmissionSnapshot);
    }
    private void restoreAdmissionSnapshot(){
        long request=admissionEpoch;boolean offline=cacheOffline();
        ArticleDiskCache disk=new ArticleDiskCache(activity);String namespace=disk.namespace();
        List<FeedStore.Row> target=readiness.targets(admissionPool(),this::bodyScope,System.currentTimeMillis());Map<String,String> sessions=new HashMap<>();
        for(FeedStore.Row row:target)sessions.put(row.candidate.id,ArticleDiskCache.session(row.candidate.originalUrl));
        work(()->{List<CachedProof> found=new ArrayList<>();for(FeedStore.Row row:target){HotCandidate candidate=row.candidate;String session=sessions.get(candidate.id);ArticleDiskCache.Hit hit=disk.read(candidate.originalUrl,namespace,session,offline,System.currentTimeMillis());if(hit!=null&&TopicBundle.initialReady(hit.document)&&ArticleVersion.matches(candidate.readerItem(),hit.document))found.add(new CachedProof(candidate,namespace+"\n"+session,hit.at));}return found;},found->{
            if(request!=admissionEpoch||admission==null)return;
            for(CachedProof proof:found)if(proof.scope.equals(bodyScope(proof.candidate)))readiness.persisted(FeedReadiness.key(proof.candidate),proof.scope,proof.at);
            tryAdmission();if(admission!=null&&!admissionCommitting)cacheAcceptedBatch(true);
        });
    }
    private void tryAdmission(){
        if(admission==null||admissionCommitting||closed||!foreground||!feedPage||ReaderStartup.pending())return;
        ArticleSessionSnapshot.run(this::tryAdmissionSnapshot);
    }
    private void tryAdmissionSnapshot(){
        List<FeedStore.Row> target=readiness.targets(admissionPool(),this::bodyScope,System.currentTimeMillis());
        if(!readiness.complete(target,this::bodyScope,System.currentTimeMillis(),cacheAge()))return;
        Snapshot pendingAdmission=admission;long request=admissionEpoch;boolean offline=cacheOffline();admissionCommitting=true;
        ArticleDiskCache disk=new ArticleDiskCache(activity);String namespace=disk.namespace();Map<String,String> sessions=new HashMap<>();
        for(FeedStore.Row row:target)sessions.put(row.candidate.id,ArticleDiskCache.session(row.candidate.originalUrl));
        List<HotCandidate> missing=new ArrayList<>();
        work(()->{
            for(FeedStore.Row row:target){HotCandidate candidate=row.candidate;if(HotSourceCatalog.optionalVideo(candidate))continue;ArticleDiskCache.Hit hit=disk.read(candidate.originalUrl,namespace,sessions.get(candidate.id),offline,System.currentTimeMillis());
                if(hit==null||!TopicBundle.initialReady(hit.document)||!ArticleVersion.matches(candidate.readerItem(),hit.document))missing.add(candidate);}
            if(!missing.isEmpty())return new Snapshot();
            long id=store.admitPrepared(pendingAdmission.info.optLong("profileVersion"),pendingAdmission.info.optString("mode"),pendingAdmission.info.optString("filter"),pendingAdmission.rows,target,System.currentTimeMillis());return id>0?read(id):new Snapshot();
        },accepted->{
            if(request!=admissionEpoch)return;admissionCommitting=false;if(!foreground||!feedPage)return;
            if(!missing.isEmpty()){for(HotCandidate candidate:missing)readiness.forget(candidate,namespace+"\n"+sessions.get(candidate.id));viewport(viewport);return;}
            if(accepted.info==null){admission=null;afterAdmission=()->{};cancelExplicitRefresh();prepare(rows.isEmpty());return;}
            // Cookies and freshness can change while storage commits. Recheck at publication.
            if(!readiness.complete(target,this::bodyScope,System.currentTimeMillis(),cacheAge())){tryAdmission();return;}
            boolean anchor=admissionAnchor;Runnable drawn=afterAdmission;admission=null;afterAdmission=()->{};
            if(explicitRefreshRunning){this.anchor="";position=0;offset=0;listener.newPresentation();}

            accepted.readyCount=target.size();publish(accepted,anchor);listener.beforeFeedDraw(drawn);
            if(bodySupplyQueued){cancelBodySupply();queueBodySupply();}
        });
    }
    private long reserveProfile=-1,reserveRequest,lastPoolRefresh=SystemClock.elapsedRealtime();
    private final Runnable poolUpdate=this::updatePoolIfDue;
    private void armPoolUpdate(){
        main.removeCallbacks(poolUpdate);
        if(autoRefresh&&!closed&&foreground&&feedPage)main.postDelayed(poolUpdate,Math.max(1000,FeedReserve.UPDATE_MS-(SystemClock.elapsedRealtime()-lastPoolRefresh)));
    }
    private void updatePoolIfDue(){
        if(closed||!foreground||!feedPage||!autoRefresh)return;
        if(SystemClock.elapsedRealtime()-lastPoolRefresh<FeedReserve.UPDATE_MS){armPoolUpdate();return;}
        if(!ready||loading||interacting||explicitRefreshRunning||ReaderStartup.pending()||!RecommendationPolicy.restriction(activity).isEmpty()){main.postDelayed(poolUpdate,5000);return;}
        prefetchBudget.reserve(SystemClock.elapsedRealtime(),FeedReserve.CAPACITY);watchCachePolicy();armPrefetchExpiry();refresh();
    }
    private void stageReserve(long id){
        long request=++reserveRequest;int generation=feedGeneration;
        work(()->read(id),next->{
            if(request!=reserveRequest||generation!=feedGeneration||closed||!foreground||next.info==null)return;
            if(!SourceFilter.normalize(sourceFilter).equals(SourceFilter.normalize(next.info.optString("filter"))))return;
            if(reserveProfile!=next.info.optLong("profileVersion"))reserve.clear();
            reserveProfile=next.info.optLong("profileVersion");reserve.preference(next.preference);reserve.nextTurn(next.turn);
            reserve.stage(availableBodies(next.future),sourceFilter);if(next.remaining>0)exhausted(false);else if(pending==id){pending=0;state();}cacheAcceptedBatch(false);
        });
    }
    private boolean feedPage=true;
    private boolean clearingBodies,bodySupplyQueued;
    private final Runnable preparedSupply=this::flushBodySupply;
    private long prefetchEpoch,spaceCheckedAt;
    private String cacheBatchKey="";
    private boolean cachePublicationChanged;
    private DataStorageBudget.FreeSpace storageSpace;
    private final ActionPrefetchBudget prefetchBudget=new ActionPrefetchBudget();
    private RecommendationPolicy.Watch cachePolicy;
    private void watchCachePolicy(){
        if(cachePolicy!=null)return;
        cachePolicy=new RecommendationPolicy.Watch(activity,reason->{main.removeCallbacks(expirePrefetch);expireCache();});cachePolicy.start();
    }
    private void stopCachePolicy(){if(cachePolicy!=null){cachePolicy.close();cachePolicy=null;}}
    private final Runnable expirePrefetch=this::expireCache;
    private void expireCache(){prefetchBudget.clear();stopCachePolicy();pausePreload();
        if(admission!=null){admission=null;admissionEpoch++;admissionCommitting=false;afterAdmission=()->{};cancelExplicitRefresh();state();listener.error(this.rows.isEmpty()?"正文尚未准备好，请稍后刷新":"下一轮尚未准备好，当前内容保留");}
        scheduleRecommendation(false);}
    private final Runnable prefetch=this::prefetchWindow;
    private final boolean autoRefresh;
    private boolean closed,foreground,ready,autoStarted,loading,newest;
    private long snapshot,pending;
    private long visibleSourceVersion,visibleProfileVersion;
    private volatile int feedGeneration;
    private volatile long progressEpoch;
    private long explicitRefreshEpoch;
    private boolean explicitRefreshRunning,recommendationExhausted;
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
    private final Runnable partialSupply=()->{if(!closed&&foreground)prepare(false);};
    private String anchor="";
    private int position,offset;
    private List<FeedStore.Row> rows=Collections.emptyList();
    private List<FeedStore.Row> allRows=Collections.emptyList();
    private volatile String sourceFilter="all";
    private String visibleMode="cold";
    private Map<Source,JSONObject> states=new EnumMap<>(Source.class);
    private static final class Snapshot {
        long id,turn;JSONObject info;List<FeedStore.Row> rows,future;int readyCount=-1,remaining;PlatformPreference preference;
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh){
        this(activity,listener,autoRefresh,null);
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh,HotSourceCollector.Transport transport){
        this(activity,listener,autoRefresh,transport,null);
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh,HotSourceCollector.Transport transport,String restoredSource){
        this(activity,listener,autoRefresh,transport,restoredSource,false);
    }
    DiscoveryController(Activity activity,Listener listener,boolean autoRefresh,HotSourceCollector.Transport transport,String restoredSource,boolean freshOpen){
        this.activity=activity;this.listener=listener;this.autoRefresh=autoRefresh;
        if(restoredSource!=null)sourceFilter=SourceFilter.source(restoredSource);
        imagePreloader=new ImagePreloader(activity,()->canPrefetch()&&storageSpace!=null&&storageSpace.allowsPrefetch());
        imagePreloader.observeIdle(()->scheduleRecommendation(false));
        preloader=new ArticlePreloader(activity,()->{},null,FeedReserve.CAPACITY,this::bodiesIdle,true).wholeTopics();
        preloader.observePrepared(this::bodyPrepared);preloader.observeFailure(this::bodyFailed);
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
            if(autoRefresh&&freshOpen&&previous!=null){
                Snapshot old=read(previous.optLong("id"));store.recordPresentation(FeedBatch.first(SourceFilter.apply(old.rows,sourceFilter)),System.currentTimeMillis());
                long next=store.presentationSnapshot(id,sourceFilter,Collections.emptyList(),System.currentTimeMillis());
                // A fresh launch may advance to new recommendations, but an
                // exhausted window must retain its last actual screen.
                if(next>0&&!SourceFilter.apply(read(next).rows,sourceFilter).isEmpty())id=next;
            }
            return read(id);
        },loaded->{ready=true;accept(loaded,false);readSources();readInterestSetup();maybeStart();if(!loading)scheduleRecommendation(false);});
    }
    /** Search the entire enabled active pool, including read and not-yet-presented rows.
     * No unread(), home batch, current source filter, collection, or model work here. */
    void searchPool(String query,Consumer<HotPoolSearch.Result> then,Runnable failure){
        if(closed)return;long request=++searchRequest;
        storage.execute(()->{
            if(request!=searchRequest)return;
            try{
                List<FeedStore.Row> pool=HotPoolSearch.pool(store.candidates(5000,0),store.profile(System.currentTimeMillis()).getJSONArray("feedback"));
                if(request!=searchRequest)return;
                HotPoolSearch.Result result=HotPoolSearch.search(pool,query);
                main.post(()->{if(!closed&&request==searchRequest)then.accept(result);});
            }catch(Exception unavailable){main.post(()->{if(!closed&&request==searchRequest)failure.run();});}
        });
    }
    void cancelSearch(){searchRequest++;}
    private <T>void work(Callable<T> operation,Consumer<T> then){
        work(operation,then,()->true);
    }
    private <T>void work(Callable<T> operation,Consumer<T> then,java.util.function.BooleanSupplier failureCurrent){
        if(closed)return;
        int generation=feedGeneration;long refreshRequest=explicitRefreshEpoch;
        storage.execute(()->{try{T result=operation.call();main.post(()->{if(!closed)ArticleSessionSnapshot.run(()->then.accept(result));});}
            // Success callbacks retain their existing operation-specific guards.
            // A late failure must likewise belong to the current action before
            // it can cancel its spinner, collection or model state.
            catch(Exception ex){main.post(()->{if(!closed&&generation==feedGeneration&&refreshRequest==explicitRefreshEpoch&&failureCurrent.getAsBoolean()){loading=false;cancelExplicitRefresh();if(recommendationState.phase==RecommendationState.Phase.RUNNING){cancelRecommendation();recommendationStatus(RecommendationState.Phase.PAUSED,"本机内容暂时无法更新，请稍后重试");}state();listener.error("本机内容暂时无法更新，请稍后重试");}});}});
    }
    private Snapshot read(long id)throws Exception{
        Snapshot result=new Snapshot();result.turn=store.balanceTurn();result.preference=new PlatformPreference(store.profile(System.currentTimeMillis()).getJSONArray("feedback"));result.id=id;result.info=store.snapshotInfo(id);result.rows=new ArrayList<>();
        if(result.info!=null)for(int start=0;start<result.info.getInt("count");start+=200)result.rows.addAll(store.readSnapshot(id,200,start));
        result.rows=store.unread(result.rows);result.future=SourceFilter.apply(store.unpresented(result.rows,System.currentTimeMillis()),result.info==null?"all":result.info.optString("filter"));result.remaining=result.future.size();
        return result;
    }
    private String selectionModelRevision;
    private long build(boolean latest)throws Exception{
        return build(latest,Collections.emptyList());
    }
    private long build(boolean latest,List<FeedStore.Row> preserve)throws Exception{
        long now=System.currentTimeMillis(),source=store.version("sources");JSONObject profile=store.profile(now);
        List<FeedStore.Row> ordered=store.unread(CandidateOrder.arrange(store.candidates(RecommendationWindow.POOL_SIZE,0),profile.getJSONArray("feedback"),latest));
        {
            List<FeedStore.Row> chronological=latest?new ArrayList<>(ordered):Collections.emptyList();
            ordered.sort(Comparator.comparing(row->row.eventId));
            SemanticOrder.Profile interests=new SemanticOrder.Profile(profile.getJSONArray("feedback"));
            Map<String,float[]> vectors=new HashMap<>();
            if(selectionModelRevision==null)try{selectionModelRevision=new ModelFiles(activity).revision();}catch(Exception unavailable){selectionModelRevision="";}
            if(!selectionModelRevision.isEmpty()){
                Map<String,String> inputs=new HashMap<>();for(SemanticOrder.Sample sample:interests.samples)inputs.put(sample.key,sample.text);
                for(FeedStore.Row row:ordered)inputs.put(SemanticOrder.key(row.candidate.modelText()),row.candidate.modelText());
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                    for(String key:inputs.keySet()){float[] vector=store.vector(FeedStore.vectorKey(key,selectionModelRevision,"wordpiece-v1"),now);if(vector!=null)vectors.put(key,vector);}db.setTransactionSuccessful();
                }finally{db.endTransaction();}
            }
            ordered=store.selectRecommendations(SemanticOrder.fuse(SemanticOrder.rank(ordered,interests,vectors),Collections.emptyMap(),interests.platforms),now);
            if(latest)ordered=CandidateOrder.preserveVisibleOrder(ordered,chronological);
        }
        if(!latest&&preserve.isEmpty())ordered=FeedSourceBalance.arrange(ordered,new PlatformPreference(profile.getJSONArray("feedback")),store.balanceTurn());
        if(!preserve.isEmpty())ordered=CandidateOrder.preserveVisibleGroups(ordered,preserve,profile.getJSONArray("feedback"));
        return store.commitRanking(source,profile.getLong("version"),now,latest?"newest":new PlatformPreference(profile.getJSONArray("feedback")).empty()?"cold":"platform",sourceFilter,ordered);
    }
    private void accept(Snapshot next,boolean keepAnchor){accept(next,keepAnchor,()->{});}
    private void accept(Snapshot next,boolean keepAnchor,Runnable drawn){
        if(explicitRefreshRunning){
            admission=null;admissionEpoch++;admissionCommitting=false;afterAdmission=()->{};
            anchor="";position=0;offset=0;listener.newPresentation();publish(next,false);listener.beforeFeedDraw(drawn);return;
        }
        if(!autoRefresh||next.info==null||next.rows.isEmpty()||SourceFilter.apply(next.rows,SourceFilter.source(next.info.optString("filter"))).isEmpty()){publish(next,keepAnchor);listener.beforeFeedDraw(drawn);return;}
        // A cold/restored native feed must remain usable even when optional reader
        // warmup, resource policy or network acquisition cannot prepare every body.
        // Existing-screen refreshes still wait for verified bodies before swapping.
        boolean initial=rows.isEmpty();
        if(initial)publish(next,keepAnchor);
        admission=next;admissionAnchor=keepAnchor;afterAdmission=initial?()->{}:drawn;admissionEpoch++;admissionCommitting=false;
        if(initial)listener.beforeFeedDraw(drawn);
        state();tryAdmission();if(!admissionCommitting)restoreAdmission();
    }
    private void publish(Snapshot next,boolean keepAnchor){
        if(next.info==null)return;snapshot=next.id;allRows=new ArticleReadHistory(activity,ArticleReadHistory.database(activity,activity.getIntent().getStringExtra("test_database"))).unread(next.rows);sourceFilter=SourceFilter.source(next.info.optString("filter"));List<FeedStore.Row> eligible=SourceFilter.apply(allRows,sourceFilter);rows=FeedBatch.first(eligible,next.readyCount>=0?Math.min(next.readyCount,listener.feedCapacity(eligible)):listener.feedCapacity(eligible));visibleMode=next.info.optString("mode");newest="newest".equals(visibleMode);visibleSourceVersion=next.info.optLong("sourceVersion");visibleProfileVersion=next.info.optLong("profileVersion");
        List<Item> publishedItems=new ArrayList<>();for(FeedStore.Row row:rows)publishedItems.add(row.candidate.readerItem());
        if(ReaderStartup.pending())deferredPublication=publishedItems;
        else ArticleSessionSnapshot.run(()->new ArticleDiskCache(activity).publishItems(publishedItems));
        cachePublicationChanged=true;
        if(keepAnchor){int found=-1;for(int n=0;n<rows.size();n++)if(EventGroups.matches(rows.get(n),anchor)){found=n;break;}position=found<0?Math.min(position,Math.max(0,rows.size()-1)):found;}
        else{position=0;String savedAnchor=next.info.optString("anchor");for(int n=0;n<rows.size();n++)if(EventGroups.matches(rows.get(n),savedAnchor)){position=n;break;}offset=next.info.optInt("offset");}
        pending=0;long accepted=snapshot;boolean balanced="all".equals(sourceFilter)&&!newest;List<FeedStore.Row> delivered=new ArrayList<>(rows);work(()->{store.activateSnapshot(accepted);store.recordPresentation(delivered,System.currentTimeMillis(),balanced);return null;},ignored->{});
        listener.feed(Collections.unmodifiableList(rows),position,offset,newest);exhausted(next.remaining<=rows.size());state();
        viewport(position);
        if(reserveProfile!=visibleProfileVersion)reserve.clear();reserve.accepted(rows);reserveProfile=visibleProfileVersion;reserve.preference(next.preference);reserve.nextTurn(next.turn+1);reserve.stage(availableBodies(next.future),sourceFilter);
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
            if(latest.id!=snapshot){pending=latest.id;stageReserve(pending);if(rows.isEmpty())applyPending(false);else{state();viewport(viewport);}}
            scheduleRecommendation(false);
        });
    }
    private void prepare(boolean apply,boolean retainOrder){
        if(apply&&admission!=null){admission=null;admissionEpoch++;admissionCommitting=false;afterAdmission=()->{};}
        if(!apply&&admission!=null){bodySupplyQueued=true;return;}
        if(!apply&&explicitRefreshRunning){main.removeCallbacks(partialSupply);main.postDelayed(partialSupply,250);return;}
        cancelBodySupply();readInterestSetup();
        // A rebuilt neutral snapshot needs semantic ordering again, even when
        // switching back from chronological mode with unchanged data versions.
        completedSource=-1;completedProfile=-1;
        cancelRecommendation();boolean latest=newest;int generation=++feedGeneration;pending=0;
        List<FeedStore.Row> preserve=retainOrder?new ArrayList<>(allRows):Collections.emptyList();
        work(()->build(latest,preserve),id->{if(id<0||generation!=feedGeneration)return;pending=id;stageReserve(pending);if(apply||rows.isEmpty())applyPending(apply);else{state();viewport(viewport);}scheduleRecommendation(false);});
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
        if(recommendationExhausted&&pending<=0){
            recycleRecommendations();
            return;
        }
        if(admission!=null){admission=null;admissionEpoch++;admissionCommitting=false;afterAdmission=()->{};}
        cacheAction();explicitRefreshing(true);long request=++explicitRefreshEpoch;
        int generation=feedGeneration;long capturedPending=pending,id=pending>0?pending:snapshot;
        boolean collect=!loading&&SystemClock.elapsedRealtime()-lastPoolRefresh>=FeedReserve.UPDATE_MS;String filter=sourceFilter;boolean latest=newest;
        if(autoRefresh&&!reserve.plan(filter).isEmpty()){
            List<FeedStore.Row> planned=reserve.plan(filter);
            work(()->{
                long next=store.presentationSnapshot(id,filter,planned,System.currentTimeMillis());
                if(next<0){long rebuilt=build(latest);if(rebuilt>0)next=store.presentationSnapshot(rebuilt,filter,planned,System.currentTimeMillis());}
                return next>0?read(next):new Snapshot();
            },next->{
                if(request!=explicitRefreshEpoch)return;
                if(generation!=feedGeneration||!foreground||!feedPage){explicitRefreshing(false);return;}
                if(next.info==null||SourceFilter.apply(next.rows,filter).isEmpty()){finishExhaustedRefresh(collect);return;}
                long newer=pending!=capturedPending?pending:0;
                accept(next,false,()->{if(request!=explicitRefreshEpoch)return;if(newer>0&&newer!=next.id){pending=newer;state();}explicitRefreshing(false);if(collect&&foreground&&feedPage)refresh();});
            });
        }else refreshPresentation(request,generation,capturedPending,id,collect,filter,latest,0);
    }
    private void refreshPresentation(long request,int generation,long capturedPending,long id,boolean collect,String filter,boolean latest,int attempt){
        ArticleProgressBinding.CacheContext context=new ArticleProgressBinding.CacheContext(activity);
        long progressGeneration=progressEpoch;
        // Most refreshes have no verified body revision. Scan bounded scalar metadata
        // from the requested snapshot, not the old visible one, before CookieManager.
        work(()->{
            ArticleProgressPreparation prepared=new ArticleProgressPreparation();prepared.snapshot=id;
            if(progressGeneration!=progressEpoch||!context.current())return prepared;
            // A return-from-reader rebuild may already be queued ahead of this
            // click. Adopt its committed snapshot before scanning/refreshing,
            // instead of computing the old pool then repeating the whole action.
            long latestId=store.latestSnapshot();JSONObject info=latestId>id?store.snapshotInfo(latestId):null;
            if(info!=null&&filter.equals(info.optString("filter"))&&latest=="newest".equals(info.optString("mode"))
                    &&info.optLong("sourceVersion")==store.version("sources")&&info.optLong("profileVersion")==store.profile(System.currentTimeMillis()).getLong("version"))prepared.snapshot=latestId;
            try{return ArticleProgressPreparation.read(store,prepared.snapshot,System.currentTimeMillis());}catch(Exception unavailable){return prepared;}
        },prepared->{
            if(request!=explicitRefreshEpoch)return;
            if(generation!=feedGeneration||!foreground||!feedPage){explicitRefreshing(false);return;}
            if(progressGeneration!=progressEpoch||!context.current()){explicitRefreshing(false);listener.error("本次更新暂未完成，已保留当前内容");return;}
            Map<String,ArticleProgressBinding.Scope> scopes=new HashMap<>();
            for(HotCandidate member:prepared.candidates.values()){ArticleProgressBinding.Scope scope=ArticleProgressBinding.scope(activity,member);if(scope!=null)scopes.put(member.id,scope);}
            refreshWithProgress(request,generation,Math.max(capturedPending,prepared.snapshot),prepared.snapshot,collect,filter,latest,attempt,context,progressGeneration,scopes,prepared.followups);
        });
    }
    private void refreshWithProgress(long request,int generation,long capturedPending,long id,boolean collect,String filter,boolean latest,int attempt,ArticleProgressBinding.CacheContext context,long progressGeneration,Map<String,ArticleProgressBinding.Scope> progressScopes,List<ArticleFollowupEvidence.Proof> followups){
        List<FeedStore.Row> preferred=reserve.plan(filter);long plannedProfile=reserveProfile;
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
                if(refreshed>0&&!preferred.isEmpty()){
                    JSONObject info=store.snapshotInfo(refreshed);
                    if(info!=null&&info.optLong("profileVersion")==plannedProfile){long promoted=store.promoteReserve(refreshed,filter,preferred,System.currentTimeMillis());if(promoted>0)refreshed=promoted;}
                }
                return refreshed>0?read(refreshed):new Snapshot();
            }catch(Exception failure){return new Snapshot();}
        },next->{
            if(request!=explicitRefreshEpoch)return;
            if(generation!=feedGeneration||!foreground||!feedPage){explicitRefreshing(false);return;}
            // A result superseding the clicked identity is read under the same
            // action, with a bounded retry rather than an unbounded callback loop.
            if(pending>capturedPending&&attempt<2){refreshPresentation(request,generation,pending,pending,collect,filter,latest,attempt+1);return;}
            if(next.info==null||progressGeneration!=progressEpoch||!context.current()||progressScopes.values().stream().anyMatch(scope->!scope.current())){explicitRefreshing(false);listener.error("本次更新暂未完成，已保留当前内容");return;}
            if(SourceFilter.apply(next.rows,filter).isEmpty()){finishExhaustedRefresh(collect);return;}
            // A newer pending computation belongs to a later explicit action.
            long newer=pending>capturedPending?pending:0;
            try{next.info.put("anchor","").put("offset",0);}catch(JSONException impossible){throw new IllegalStateException(impossible);}
            // Only a validated publication starts another exposure cycle.
            // Queued, coalesced or cancelled requests leave the visible cycle intact.
            accept(next,false,()->{
                if(request!=explicitRefreshEpoch)return;
                if(newer>0&&newer!=next.id){pending=newer;state();}
                explicitRefreshing(false);
                if(collect&&foreground&&feedPage)refresh();
            });
        });
    }
    private void finishExhaustedRefresh(boolean collect){
        explicitRefreshing(false);exhausted(true);recycleRecommendations();
    }
    private void recycleRecommendations(){
        cacheAction();explicitRefreshing(true);long request=++explicitRefreshEpoch;int generation=feedGeneration;String filter=sourceFilter;boolean latest=newest;
        boolean collect=!loading&&SystemClock.elapsedRealtime()-lastPoolRefresh>=FeedReserve.UPDATE_MS;
        work(()->{long rebuilt=build(latest);long next=rebuilt>0?store.recycleSnapshot(rebuilt,filter,System.currentTimeMillis()):-1;return next>0?read(next):new Snapshot();},next->{
            if(request!=explicitRefreshEpoch)return;
            if(generation!=feedGeneration||!foreground||!feedPage){explicitRefreshing(false);return;}
            if(next.info==null){explicitRefreshing(false);listener.error("本次更新暂未完成，已保留当前内容");return;}
            accept(next,false,()->{if(request!=explicitRefreshEpoch)return;explicitRefreshing(false);listener.error("暂无新内容");if(collect&&foreground&&feedPage)refresh();});
        });
    }
    private void exhausted(boolean value){recommendationExhausted=value;listener.exhausted(value);}
    private void explicitRefreshing(boolean running){if(explicitRefreshRunning==running)return;explicitRefreshRunning=running;listener.refreshWork(running);}
    private void cancelExplicitRefresh(){explicitRefreshEpoch++;if(explicitRefreshRunning&&admission!=null){admission=null;admissionEpoch++;admissionCommitting=false;afterAdmission=()->{};}explicitRefreshing(false);}
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
        work(()->{RankingInput input=new RankingInput();JSONObject profile=store.profile(System.currentTimeMillis());input.profile=profile.getLong("version");input.source=store.version("sources");input.interests=new SemanticOrder.Profile(profile.getJSONArray("feedback"));List<FeedStore.Row> available=CandidateOrder.forModels(store.candidates(RecommendationWindow.POOL_SIZE,0),profile.getJSONArray("feedback"));try{input.rows=baseline==null?new ArrayList<>(available.subList(0,Math.min(100,available.size()))):store.evaluationCandidates(baseline,System.currentTimeMillis(),new ModelFiles(activity).revision(),trialAppEpoch());if(!input.rows.isEmpty())input.learning=store.comparisonLearning(input.rows,System.currentTimeMillis(),input.profile,input.source);}catch(IllegalArgumentException error){input.error=error.getMessage();input.rows=Collections.emptyList();}return input;},input->{
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
        if(foreground)return;
        ArticleReadHistory readHistory=new ArticleReadHistory(activity,ArticleReadHistory.database(activity,activity.getIntent().getStringExtra("test_database")));
        List<FeedStore.Row> unread=readHistory.unread(rows);
        if(!rows.equals(unread)){rows=unread;allRows=readHistory.unread(allRows);position=Math.min(position,Math.max(0,rows.size()-1));listener.feed(Collections.unmodifiableList(rows),position,offset,newest);}
        foreground=true;interacting=false;cacheAction();tryAdmission();armPoolUpdate();maybeStart();viewport(viewport);cacheAcceptedBatch(false);
        String restriction=RecommendationPolicy.restriction(activity);if(!restriction.isEmpty())recommendationStatus(RecommendationState.Phase.PAUSED,restriction);
        if(ready){scheduleRecommendation(false);readSources();work(()->new long[]{store.version("sources"),store.profile(System.currentTimeMillis()).getLong("version")},versions->{if(versions[0]!=visibleSourceVersion||versions[1]!=visibleProfileVersion)prepare(false);});}
    }
    private void maybeStart(){
        if(ReaderStartup.pending()||!ready||!foreground||autoStarted||!autoRefresh||states.isEmpty())return;autoStarted=true;
        long now=System.currentTimeMillis();List<Source> stale=new ArrayList<>();
        for(Source source:HotSourceCatalog.HOT){JSONObject s=states.get(source);if(s!=null&&s.optBoolean("enabled")&&s.optLong("lastSuccessAt")<now-FeedReserve.UPDATE_MS&&s.optLong("retryAt")<=now)stale.add(source);}
        if(!stale.isEmpty())refresh(stale);
    }
    void readerReady(){if(!closed){
        ArticleSessionSnapshot.run(()->{if(deferredPublication!=null){new ArticleDiskCache(activity).publishItems(deferredPublication);deferredPublication=null;}});
        tryAdmission();if(admission!=null&&!admissionCommitting)restoreAdmission();
        maybeStart();viewport(viewport);cacheAcceptedBatch(false);scheduleRecommendation(false);
    }}
    void refresh(){refresh(HotSourceCatalog.HOT);}
    void refresh(Collection<Source> targets){if(!ready||!foreground)return;cacheAction();lastPoolRefresh=SystemClock.elapsedRealtime();armPoolUpdate();main.removeCallbacks(partialSupply);feedGeneration++;pending=0;pausePreload();cancelRecommendation();loading=true;state();collector.refresh(targets);}
    void retryBrowser(Source source){if(!ready||!foreground)return;main.removeCallbacks(partialSupply);feedGeneration++;pending=0;pausePreload();cancelRecommendation();loading=true;state();collector.retryInBrowser(source);}
    void stop(){if(!foreground)return;main.removeCallbacks(poolUpdate);reserveRequest++;progressEpoch++;cancelExplicitRefresh();cacheWindow.stop();foreground=false;cacheBatchKey="";cancelBodySupply();prefetchBudget.clear();stopCachePolicy();main.removeCallbacks(expirePrefetch);main.removeCallbacks(partialSupply);feedGeneration++;pending=0;pausePreload();cancelRepair();cancelRecommendation();collector.cancel();loading=false;state();persistAnchor();}
    void remember(int position,int offset){this.position=position;this.offset=offset;anchor=position>=0&&position<rows.size()?rows.get(position).eventId:"";}
    private void persistAnchor(){long id=snapshot;String value=anchor;int y=offset;if(id>0)work(()->{store.anchor(id,value,y);return null;},ignored->{});}
    private void state(){listener.state(loading||admission!=null,pending>0);}
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
    void interacting(boolean value){if(value!=interacting)cacheAction();interacting=value;if(value){
        // A short scroll must not repeatedly restart a slow source page. Finish
        // the single in-flight body, but launch no new body/image/model work.
        prefetchEpoch++;main.removeCallbacks(preparedImages);main.removeCallbacks(prefetch);imagePreloader.pause();preloader.finishWindow();cancelRecommendation();
    }else{viewport(viewport);scheduleRecommendation(false);}}
    void scrollState(int state){if(state==0)cacheWindow.idle();else{if(explicitRefreshRunning)cancelExplicitRefresh();cacheWindow.gesture(state==2,SystemClock.elapsedRealtime());}interacting(state!=0);}
    void scrollViewport(int first,int count,int top){cacheWindow.viewport(first,top,SystemClock.elapsedRealtime());visibleCount=Math.max(1,count);viewport(first);}
    void exposed(Collection<String> events){
        if(closed||!foreground||!feedPage||snapshot<=0||events.isEmpty()||events.size()>64||ReaderStartup.pending())return;
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
    void feedPage(boolean value){if(value&&!feedPage)cacheAction();feedPage=value;armPoolUpdate();if(!value){progressEpoch++;cancelExplicitRefresh();pausePreload();cancelRecommendation();}else viewport(viewport);}
    private void cacheAction(){
        if(closed||!foreground)return;
        prefetchBudget.action(SystemClock.elapsedRealtime());watchCachePolicy();
        armPrefetchExpiry();
    }
    private void armPrefetchExpiry(){main.removeCallbacks(expirePrefetch);main.postDelayed(expirePrefetch,prefetchBudget.remaining(SystemClock.elapsedRealtime()));}
    private List<FeedStore.Row> cacheRows(){
        List<FeedStore.Row> selected=new ArrayList<>(rows);Set<String> ids=new HashSet<>();for(FeedStore.Row row:rows)ids.add(row.eventId);
        for(FeedStore.Row row:admissionPool())if(ids.add(row.eventId))selected.add(row);
        for(FeedStore.Row row:reserve.future())if(ids.add(row.eventId))selected.add(row);
        return selected;
    }
    private void cacheAcceptedBatch(boolean force){
        ArticleSessionSnapshot.run(()->cacheBatchSnapshot(force));
    }
    private void cacheBatchSnapshot(boolean force){
        if(!autoRefresh||closed||!foreground||!feedPage||(rows.isEmpty()&&admission==null)||ReaderStartup.pending())return;
        StringBuilder identity=new StringBuilder();Set<String> keep=new HashSet<>();List<Item> retainedItems=new ArrayList<>();
        for(FeedStore.Row row:cacheRows()){Item item=row.candidate.readerItem();identity.append(item.source.name()).append('|').append(item.url).append('@').append(ArticleVersion.hash(item)).append('\n');keep.add(item.url);retainedItems.add(item);}
        listener.prepareTopics(cacheRows());
        String key=HotCandidate.hash(identity.toString());if(!force&&key.equals(cacheBatchKey)){
            // Publishing releases the previous screen's disk pins even when
            // the prefetch window is unchanged. Prune without renewing its lease.
            if(cachePublicationChanged){cachePublicationChanged=false;retainCachedBodies(retainedItems,keep,false);}return;
        }
        boolean grant=force||cacheBatchKey.isEmpty();cacheBatchKey=key;cachePublicationChanged=false;
        // Replanning must not restart a still-eligible slow acquisition on each
        // language/source callback. offer() cancels only bodies outside the new window.
        prefetchEpoch++;main.removeCallbacks(preparedImages);main.removeCallbacks(prefetch);imagePreloader.pause();
        if(grant){prefetchBudget.reserve(SystemClock.elapsedRealtime(),FeedReserve.CAPACITY);watchCachePolicy();armPrefetchExpiry();}
        retainCachedBodies(retainedItems,keep,true);
        main.post(prefetch);
    }
    private void retainCachedBodies(List<Item> retainedItems,Set<String> keep,boolean retainImages){
        Repository cache=new Repository(activity);ArticleDiskCache.Retention retained=cache.retainItems(retainedItems);cache.close();
        Runnable pruneImages=retainImages?ReaderImages.shared.retainBatch(keep):()->{};
        storage.execute(()->{try{new ArticleDiskCache(activity).pruneBatch(retained);pruneImages.run();}catch(RuntimeException ignored){/* Derivative cleanup must not undo the accepted screen. */}});
    }
    void viewport(int first){prefetchEpoch++;imagePreloader.pause();viewport=Math.max(0,first);main.removeCallbacks(prefetch);if(autoRefresh&&feedPage&&foreground&&!interacting&&!closed)main.postDelayed(prefetch,650);}
    String handoff(Item item){
        imagePreloader.pause();
        String token=ArticleHandoff.offer(item,preloader);
        if(token!=null){preloader=new ArticlePreloader(activity,()->{},null,FeedReserve.CAPACITY,this::bodiesIdle,true).wholeTopics();preloader.observePrepared(this::bodyPrepared);preloader.observeFailure(this::bodyFailed);}
        return token;
    }
    String readerPrefetchPlan(HotCandidate candidate){long now=SystemClock.elapsedRealtime();return autoRefresh&&!closed?ReaderPrefetchPlan.from(cacheRows(),candidate,cacheWindow.direction(now),cacheWindow.rapid(now)).encode():null;}
    void topicPrepared(Item item,Document document){bodyPrepared(item,document);}
    private void bodyPrepared(Item item,Document document){
        ArticleSessionSnapshot.run(()->bodyPreparedSnapshot(item,document));
    }
    private void bodyPreparedSnapshot(Item item,Document document){
        if(closed||document.cachedSession==null||!document.cachedSession.equals(ArticleDiskCache.session(item.url))||!new ArticleDiskCache(activity).namespace().equals(document.cachedNamespace)||!item.url.equals(document.url))return;
        imagePreloader.prepared(item,document);main.removeCallbacks(preparedImages);main.post(preparedImages);
        HotCandidate prepared=preparedCandidate(item);
        if(prepared!=null&&ArticleVersion.matches(item,document)&&TopicBundle.initialReady(document)&&!document.filteredVideo&&!document.loginRequired&&!document.sourceUnavailable){
            String key=FeedReadiness.key(prepared),scope=bodyScope(prepared),verifyKey=key+"|"+scope;
            if(!readiness.ready(prepared,scope,System.currentTimeMillis(),cacheAge())&&verifying.add(verifyKey)){
                new ArticleDiskCache(activity).verify(document,cacheOffline(),hit->main.post(()->{
                    verifying.remove(verifyKey);if(closed||!scope.equals(bodyScope(prepared)))return;
                    if(hit!=null)readiness.persisted(key,scope,hit.at);
                    // Failed persistence remains unready; never reclassify disk pressure as a source failure.
                    tryAdmission();
                }));
            }
        }
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
        List<FeedStore.Row> known=new ArrayList<>(allRows);if(admission!=null)known.addAll(admission.rows);known.addAll(reserve.future());
        for(FeedStore.Row row:known)for(HotCandidate candidate:row.entries)if(candidate.source==item.source&&candidate.originalUrl.equals(item.url)&&candidate.title.equals(item.title)&&candidate.snippet.equals(item.snippet))return candidate;
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
    private void pausePreload(){prefetchEpoch++;main.removeCallbacks(preparedImages);main.removeCallbacks(prefetch);imagePreloader.pause();preloader.pause();}
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
    private boolean canPrefetch(){return prefetchBudget.active(SystemClock.elapsedRealtime())&&!ReaderStartup.pending()&&trialFailure==null&&autoRefresh&&feedPage&&!closed&&foreground&&!interacting&&collector.allowsPrefetch()&&!clearingBodies&&modelRepair==null&&recommendation==null&&RecommendationPolicy.restriction(activity).isEmpty();}
    private void startPrefetch(){
        if(!canPrefetch()||storageSpace==null)return;
        if(!storageSpace.allowsPrefetch()){imagePreloader.pause();preloader.pause();return;}
        offerPrefetch();
    }
    private void offerPrefetch(){
        if(!canPrefetch())return;
        List<Item> window=new ArrayList<>();for(int n:cacheWindow.positions(rows.size(),viewport,visibleCount,SystemClock.elapsedRealtime()))window.add(rows.get(n).candidate.readerItem());
        for(FeedStore.Row row:admissionPool())window.add(row.candidate.readerItem());
        for(FeedStore.Row row:reserve.future())window.add(row.candidate.readerItem());
        if(listener.preparesTopics())window.removeIf(TopicPreparation::supports);
        imagePreloader.offer(window,rows.size());preloader.offer(window,FeedReserve.CAPACITY,true);
    }
    void memoryPressure(){cancelBodySupply();prefetchBudget.clear();stopCachePolicy();main.removeCallbacks(expirePrefetch);cancelRepair();suspendRecommendation("内存紧张，已暂停整理");}
    void suspendRecommendation(String reason){pausePreload();cancelRecommendation();lastRecommendation=SystemClock.elapsedRealtime();recommendationStatus(RecommendationState.Phase.PAUSED,reason);}
    private void cancelRecommendation(){recommendationEpoch++;main.removeCallbacks(recommend);if(recommendation!=null){recommendation.close();recommendation=null;}Consumer<String> interrupted=trialFailure;trialFailure=null;if(recommendationState.phase==RecommendationState.Phase.RUNNING)recommendationStatus(RecommendationState.Phase.PAUSED,"本次整理已暂停，当前列表仍可阅读");if(interrupted!=null)interrupted.accept("本轮对比已停止，已保存的计算缓存会保留。");}
    private void scheduleRecommendation(boolean explicit){
        main.removeCallbacks(recommend);if(closed||!ready||!foreground||interacting||newest||loading||admission!=null||modelRepair!=null||trialFailure!=null)return;
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
            List<HotCandidate> candidates=store.candidates(RecommendationWindow.POOL_SIZE,0);
            if(epoch!=recommendationEpoch||generation!=feedGeneration)return null;
            input.interests=new SemanticOrder.Profile(profile.getJSONArray("feedback"));input.rows=CandidateOrder.forModels(candidates,profile.getJSONArray("feedback"));input.rows.sort(Comparator.comparing(row->row.eventId));return input;
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
                    work(()->store.commitRanking(input.source,input.profile,System.currentTimeMillis(),semantic?"personal":input.interests.platforms.empty()?"cold":"platform",filter,FeedSourceBalance.arrange(store.selectRecommendations(ordered,System.currentTimeMillis(),semantic&&complete),input.interests.platforms,store.balanceTurn())),id->{
                        if(epoch!=recommendationEpoch||generation!=feedGeneration||!foreground)return;
                        if(id<0){recommendationStatus(RecommendationState.Phase.PAUSED,"来源或偏好已更新，当前列表保留，可继续整理");scheduleRecommendation(false);return;}
                        if(complete){completedSource=input.source;completedProfile=input.profile;}pending=id;stageReserve(pending);recommendationStatus(complete?RecommendationState.Phase.READY:semantic?RecommendationState.Phase.PARTIAL:RecommendationState.Phase.PAUSED,detail);state();viewport(viewport);
                        // Completion must not depend on a cache/layout callback renewing work.
                        if(continueAutomatically)scheduleRecommendation(false);
                    },()->epoch==recommendationEpoch&&foreground);
                }
            });recommendation.start();
        },()->epoch==recommendationEpoch&&foreground);
    }
    @Override public void close(){
        if(closed)return;main.removeCallbacks(poolUpdate);reserveRequest++;progressEpoch++;cancelExplicitRefresh();cancelBodySupply();prefetchBudget.clear();stopCachePolicy();main.removeCallbacks(expirePrefetch);main.removeCallbacks(partialSupply);pausePreload();preloader.close();imagePreloader.close();cancelRepair();maintenance.shutdownNow();cancelRecommendation();persistAnchor();collector.close();closed=true;storage.execute(store::close);storage.shutdown();
    }
}
