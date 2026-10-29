package app.quietreader;

import android.app.Activity;
import android.os.*;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Foreground collector; one request queue per source, at most three native requests.
 * The owner supplies a serial storage executor. Close this collector on main before
 * queuing store.close() and shutting down that executor. No delayed retry/wakeup.
 */
public final class HotSourceCollector implements AutoCloseable {
    public interface Listener {
        void onSourceChanged(Source source);void onFinished();void onStorageFailure();
        default void onSnapshotCommitted(Source source){}
        default void onBrowserStarting(Source source){}
    }
    interface Request {void close();}
    interface Transport {Request start(Source source,boolean browser,Repository.Result<List<Item>> result);boolean offline();default boolean browserBoard(Source source){return false;}default boolean browserFallback(Source source){return false;}}
    private static final class Job {
        final Source source;final boolean browser;
        volatile boolean cancelled;volatile long revision=-1;
        boolean settled;Request request;
        Job(Source source,boolean browser){this.source=source;this.browser=browser;}
    }
    private final FeedStore store;
    private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService storage;
    private final Transport transport;
    private final Map<Source,Job> jobs=new EnumMap<>(Source.class);
    private final Deque<Job> pending=new ArrayDeque<>();
    private boolean closed,running;
    public HotSourceCollector(Activity activity,FeedStore store,ExecutorService storage,Listener listener){
        this(store,storage,listener,new Transport(){
            public boolean browserBoard(Source source){return source==Source.BILIBILI||source==Source.DOUYIN;}
            public boolean browserFallback(Source source){return source==Source.V2EX;}
            public Request start(Source source,boolean browser,Repository.Result<List<Item>> result){
                if(browser){
                    String restriction=app.quietreader.ai.RecommendationPolicy.restriction(activity);
                    if(!restriction.isEmpty()){result.failure(restriction);return ()->{};}
                    DynamicBoard board=new DynamicBoard(activity,source,result);
                    app.quietreader.ai.RecommendationPolicy.Watch watch=new app.quietreader.ai.RecommendationPolicy.Watch(activity,reason->{board.close();result.failure(reason);});watch.start();
                    return ()->{watch.close();board.close();};
                }
                Repository repository=new Repository(activity);
                try{repository.board(source,result);return repository::close;}catch(RuntimeException ex){repository.close();throw ex;}
            }
            public boolean offline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)activity.getSystemService(Activity.CONNECTIVITY_SERVICE);return cm!=null&&cm.getActiveNetwork()==null;}
        });
    }
    HotSourceCollector(FeedStore store,ExecutorService storage,Listener listener,Transport transport){ui();this.store=store;this.storage=storage;this.listener=listener;this.transport=transport;}
    private static void ui(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Source collector needs main thread");}
    public void refresh(Collection<Source> sources){
        ui();if(closed)return;cancel();running=true;
        for(Source source:new LinkedHashSet<>(sources))if(HotSourceCatalog.eligible(source))pending.add(new Job(source,transport.browserBoard(source)));
        drain();
    }
    private boolean current(Job job){return !closed&&!job.cancelled&&jobs.get(job.source)==job;}
    /** A free source slot permits one serial speculative pipeline. Queued
     * browsers also reserve it, before their actual transport is started. */
    boolean allowsPrefetch(){ui();return jobs.size()<3&&!jobs.values().stream().anyMatch(job->job.browser)&&!pending.stream().anyMatch(job->job.browser);}
    private void drain(){
        if(closed)return;
        while(jobs.size()<3&&!pending.isEmpty()){
            boolean browserActive=jobs.values().stream().anyMatch(job->job.browser);
            Job selected=null;for(Iterator<Job> iterator=pending.iterator();iterator.hasNext();){Job candidate=iterator.next();if(!candidate.browser||!browserActive){selected=candidate;iterator.remove();break;}}
            if(selected==null)break;
            Job job=selected;jobs.put(job.source,job);
            execute(job,()->{
                if(job.cancelled)return;
                if(job.revision<0)job.revision=store.beginRefresh(job.source);
                else if(!store.refreshCurrent(job.source,job.revision))job.revision=-1;
                if(job.cancelled){if(job.revision>=0)store.cancelRefresh(job.source,job.revision);return;}
                main.post(()->{
                    if(!current(job))return;
                    listener.onSourceChanged(job.source);
                    if(!current(job))return;
                    if(job.revision<0){finished(job);return;}
                    if(job.browser){listener.onBrowserStarting(job.source);if(!current(job))return;}
                    try{Request request=transport.start(job.source,job.browser,result(job));if(current(job))job.request=request;else if(request!=null)request.close();}
                    catch(RuntimeException ex){result(job).failure("来源请求未能启动");}
                });
            });
        }
        if(running&&jobs.isEmpty()&&pending.isEmpty()){running=false;listener.onFinished();}
    }
    private void execute(Job job,Runnable operation){
        try{storage.execute(()->{
            try{operation.run();}
            catch(Exception ex){
                // An unavailable database must not wedge a UI slot.
                try{if(job.revision>=0)store.cancelRefresh(job.source,job.revision);}catch(Exception ignored){}
                main.post(()->{if(current(job)){listener.onStorageFailure();finished(job);}});
            }
        });}catch(RejectedExecutionException ex){if(current(job)){listener.onStorageFailure();finished(job);}}
    }
    private boolean settle(Job job){ui();if(!current(job)||job.settled)return false;job.settled=true;return true;}
    private Repository.Result<List<Item>> result(Job job){return new Repository.Result<List<Item>>(){
        @Override public void success(List<Item> rows){
            if(!settle(job))return;
            long observed=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();int rank=0;
            for(Item row:rows){rank++;
                if(row==null||row.source!=job.source||!UrlPolicy.belongs(job.source,row.url)||!ContentLanguage.eligible(row.title,row.snippet,ContentLanguage.Body.UNKNOWN))continue;
                try{
                    // Item.detail is often heat text, not a summary/publication time.
                    candidates.add(new HotCandidate(job.source,row.url,row.title,row.snippet,row.detail,row.video,rank,row.publishedAt,observed,ContentLanguage.Body.UNKNOWN,row.sourceTag));
                }catch(IllegalArgumentException ignored){}
                if(candidates.size()==500)break;
            }
            // A parsed list may contain only foreign headings or unrelated
            // source records. Treat an unusable native board like an empty one.
            if(candidates.isEmpty()&&browserFallback(job,0,0))return;
            commit(job,candidates,observed,"UNAVAILABLE","未取得可用中文热门标题",0);
        }
        @Override public void failure(String reason){failure(reason,0,0);}
        @Override public void failure(String reason,int status){failure(reason,status,0);}
        @Override public void failure(String reason,int status,long retryAt){
            if(!settle(job))return;
            if(browserFallback(job,status,retryAt))return;
            String state=status==429?"RATE_LIMITED":status==401||status==403?"LOGIN_REQUIRED":transport.offline()?"OFFLINE":"UNAVAILABLE";
            commit(job,Collections.emptyList(),System.currentTimeMillis(),state,reason,retryAt);
        }
    };}
    /** Transfer one refresh revision to the serial browser queue; never loop or
     * count native failure as a completed attempt before browser acquisition ends. */
    private boolean browserFallback(Job job,int status,long retryAt){
        if(job.browser||!transport.browserFallback(job.source)||status==429||retryAt>System.currentTimeMillis()||transport.offline())return false;
        jobs.remove(job.source);
        Job fallback=new Job(job.source,true);fallback.revision=job.revision;pending.addLast(fallback);
        if(job.request!=null)job.request.close();
        drain();return true;
    }
    private void commit(Job job,List<HotCandidate> candidates,long at,String status,String error,long retryAt){
        execute(job,()->{
            if(job.cancelled){store.cancelRefresh(job.source,job.revision);return;}
            boolean updated;
            if(candidates.isEmpty()){store.failRefresh(job.source,job.revision,status,error,retryAt,at);updated=false;}
            else updated=store.upsertSnapshot(job.source,job.revision,candidates,at);
            main.post(()->{if(current(job)){listener.onSourceChanged(job.source);if(current(job)&&updated)listener.onSnapshotCommitted(job.source);finished(job);}});
        });
    }
    private void finished(Job job){
        if(!current(job))return;jobs.remove(job.source);if(job.request!=null)job.request.close();drain();
    }
    /** One explicit browser retry, subject to the same disabled/limit checks. */
    public void retryInBrowser(Source source){
        ui();if(closed||!HotSourceCatalog.eligible(source))return;cancel();running=true;pending.add(new Job(source,true));drain();
    }
    /** Cancels queued work before it reaches the database and invalidates open revisions. */
    public void cancel(){
        ui();List<Job> cancelled=new ArrayList<>(jobs.values());cancelled.addAll(pending);pending.clear();running=false;jobs.clear();
        for(Job job:cancelled){job.cancelled=true;if(job.request!=null)job.request.close();}
        if(cancelled.isEmpty())return;
        // Queue now, never from a late main callback: the owner can safely shut down
        // its executor after close() while draining final database operations.
        try{storage.execute(()->{
            for(Job job:cancelled)try{if(job.revision>=0)store.cancelRefresh(job.source,job.revision);}catch(Exception ex){main.post(()->{if(!closed)listener.onStorageFailure();});}
        });}catch(RejectedExecutionException ex){if(!closed)listener.onStorageFailure();}
    }
    @Override public void close(){ui();if(closed)return;closed=true;cancel();}
}
