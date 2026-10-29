package app.quietreader;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.*;
import static app.quietreader.Models.*;

/** One speculative request, bounded look-ahead, and promotion instead of duplicate work. */
final class ArticlePreloader implements AutoCloseable {
    interface Loader {void load(Item item,Repository.Result<Document> cb);void cancel();}
    private final Loader loader;
    private final boolean revalidateHidden;
    private Activity activity;
    private final Repository repo;
    private final Handler main=new Handler(Looper.getMainLooper());
    private Runnable onVideo;
    private int windowSize;
    private Runnable onIdle;
    private java.util.function.BiConsumer<Item,Document> onPrepared=(item,doc)->{};
    void observePrepared(java.util.function.BiConsumer<Item,Document> observer){onPrepared=observer;}
    private boolean reserved;
    private static final class Attempt {
        final long at;final String scope;
        Attempt(long at,String scope){this.at=at;this.scope=scope;}
    }
    private final Map<String,Attempt> attempted=new LinkedHashMap<String,Attempt>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Attempt> entry){return size()>64;}
    };
    private final Deque<Item> queue=new ArrayDeque<>();
    private Item active;
    private DynamicReader dynamic;
    private Repository.Result<Document> foreground;
    private boolean retainedForeground,deferredNetwork,deferredDynamic,bufferedAcquired=true;
    private ArticleSession activeSession;
    private Document bufferedResult;
    private String bufferedFailure;
    private int revision;
    private boolean closed;
    private app.quietreader.ai.RecommendationPolicy.Watch policyWatch;

    ArticlePreloader(Activity activity,Runnable onVideo){this(activity,onVideo,null);}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader){this(activity,onVideo,loader,3,()->{});}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader,int windowSize,Runnable onIdle){this(activity,onVideo,loader,windowSize,onIdle,false);}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader,int windowSize,Runnable onIdle,boolean revalidateHidden){this.activity=activity;this.onVideo=onVideo;this.loader=loader;this.revalidateHidden=revalidateHidden;this.windowSize=Math.max(1,Math.min(40,windowSize));this.onIdle=onIdle;repo=new Repository(activity);}
    boolean busy(){return active!=null||!queue.isEmpty();}
    private static String attemptKey(Item item){return item.source.name()+"|"+item.url+"@"+ArticleVersion.hash(item)+"|"+item.selectedTopicPost;}

    void offer(List<Item> candidates){
        offer(candidates,windowSize);
    }
    void offer(List<Item> candidates,int limit){
        offer(candidates,limit,false);
    }
    void offer(List<Item> candidates,int limit,boolean immediate){
        if(closed||reserved||foreground!=null||retainedForeground)return;
        queue.clear();
        Set<String> seen=new HashSet<>();
        int bounded=Math.max(1,Math.min(40,limit));
        for(Item item:candidates){if(seen.add(item.url))queue.add(item);if(queue.size()==bounded)break;}
        if(active!=null&&queue.stream().noneMatch(i->ArticleVersion.sameRequest(i,active)))cancelActive();
        if(policyWatch==null){policyWatch=new app.quietreader.ai.RecommendationPolicy.Watch(activity,reason->pause());policyWatch.start();}
        main.removeCallbacks(pump);main.postDelayed(pump,immediate?0:350);
    }
    private final Runnable pump=this::next;
    private void next(){
        if(closed||reserved||active!=null||foreground!=null||retainedForeground)return;
        if(repo.offline()||!app.quietreader.ai.RecommendationPolicy.restriction(activity).isEmpty()){queue.clear();idle();return;}
        while(!queue.isEmpty()){
            Item item=queue.removeFirst();Attempt previous=attempted.get(attemptKey(item));
            if(!item.source.readable()||!item.source.unifiedReading()||item.video||VideoPolicy.url(item.source,item.url)||!UrlPolicy.belongs(item.source,item.url)||item.url.equals(item.source.login)||item.url.equals(item.source.endpoint)||(!revalidateHidden&&repo.hiddenVideo(item)))continue;
            ArticleSession session=new ArticleSession(activity,item);
            boolean refreshHidden=revalidateHidden&&repo.hiddenVideo(item);
            // A URL-only marker has no metadata version. Recheck the source instead of
            // assigning any previously cached body to the current candidate version.
            Document cached=refreshHidden?null:repo.cachedArticle(item,session);
            if(cached!=null&&(!revalidateHidden||ArticleVersion.matches(item,cached))){onPrepared.accept(item,cached);continue;}
            long now=SystemClock.elapsedRealtime();
            if(previous!=null&&previous.scope.equals(session.scopeIdentity())&&now>=previous.at&&now-previous.at<5*60*1000)continue;
            active=item;activeSession=session;attempted.put(attemptKey(item),new Attempt(now,session.scopeIdentity()));int request=++revision;
            if(loader!=null)loader.load(item,new Repository.Result<Document>(){
                public void success(Document doc){if(valid(request))complete(item,doc);}
                public void failure(String why){failed(request,why);}
            });
            else if(refreshHidden)loadNetwork(item,request);
            else repo.restoreArticle(item,restored->{if(!valid(request))return;if(restored!=null&&(!revalidateHidden||ArticleVersion.matches(item,restored)))complete(item,restored,false);else loadNetwork(item,request);});
            return;
        }
        idle();
    }
    /** Must be called before replacing the board. A true result now owns the callback. */
    boolean reserve(Item item){
        if(closed||reserved||foreground!=null||retainedForeground||active==null||active.source!=item.source||!active.url.equals(item.url))return false;
        if(!ArticleVersion.sameRequest(active,item))return false;
        // Explicit reading cannot promote a hidden-video acquisition; stop() cancels it before reopening.
        if(revalidateHidden&&repo.hiddenVideo(item))return false;
        reserved=true;stopWatch();queue.clear();main.removeCallbacks(pump);
        onIdle=()->{};onVideo=()->{};onPrepared=(ignoredItem,ignoredDoc)->{};
        return true;
    }
    void adopt(Activity owner,Runnable video){activity=owner;onVideo=video;windowSize=3;reserved=false;}
    boolean promote(Item item,Repository.Result<Document> callback){
        if(closed||retainedForeground)return false;
        stopWatch();
        queue.clear();main.removeCallbacks(pump);
        if(active!=null&&ArticleVersion.sameRequest(active,item)){foreground=callback;if(dynamic!=null)dynamic.promote();return true;}
        cancelActive();return false;
    }
    private boolean valid(int request){return !closed&&active!=null&&request==revision;}
    boolean canRetainForeground(Item item){
        return !closed&&(foreground!=null||retainedForeground)&&active!=null&&item!=null&&ArticleVersion.sameRequest(active,item)
            &&(dynamic!=null?dynamic.matches(item):activeSession!=null&&activeSession.current())&&(bufferedResult==null||ArticleSession.contentCurrent(bufferedResult));
    }
    void detachForeground(){
        if(closed||active==null||(foreground==null&&!retainedForeground))return;
        retainedForeground=true;foreground=null;activity=null;stopWatch();queue.clear();main.removeCallbacks(pump);
        onVideo=()->{};onIdle=()->{};onPrepared=(item,doc)->{};
        if(dynamic!=null)dynamic.detach();
    }
    void resumeForeground(Activity owner,Item item,Repository.Result<Document> callback,Runnable video){
        if(!retainedForeground)return;
        if(!canRetainForeground(item)){close();callback.failure("登录或缓存状态已变化，请重新读取");return;}
        adopt(owner,video);retainedForeground=false;foreground=callback;
        if(dynamic!=null){dynamic.attach(owner,dynamicResult(item,revision));return;}
        Document result=bufferedResult;String failure=bufferedFailure;boolean acquired=bufferedAcquired;bufferedResult=null;bufferedFailure=null;bufferedAcquired=true;
        if(result!=null)complete(item,result,acquired);
        else if(failure!=null)failed(revision,failure);
        else if(deferredDynamic){deferredDynamic=false;loadDynamic(item,revision);}
        else if(deferredNetwork){deferredNetwork=false;loadNetwork(item,revision);}
    }
    private void loadNetwork(Item item,int request){
        if(!valid(request))return;
        if(retainedForeground){deferredNetwork=true;return;}
        if(SourceSession.usesWeb(activity,item.source)){loadDynamic(item,request);return;}
        repo.article(item,new Repository.Result<Document>(){
            public void success(Document doc){
                if(!valid(request))return;
                // Repository.article has already saved this session-checked acquisition.
                if(SourceSession.nativeDocumentReady(item.source,doc))complete(item,doc,false);else loadDynamic(item,request);
            }
            public void failure(String why){if(valid(request))loadDynamic(item,request);}
        });
    }
    private void loadDynamic(Item item,int request){
        if(!valid(request))return;
        if(retainedForeground){deferredDynamic=true;return;}
        activeSession=new ArticleSession(activity,item);
        dynamic=new DynamicReader(activity,item,dynamicResult(item,request),foreground!=null||retainedForeground);
    }
    private Repository.Result<Document> dynamicResult(Item item,int request){
        return new Repository.Result<Document>(){
            public void success(Document doc){if(valid(request))complete(item,doc);}
            public void failure(String why){failed(request,why);}
        };
    }
    private void failed(int request,String why){
        if(!valid(request))return;
        if(retainedForeground){bufferedFailure=why;return;}
        Repository.Result<Document> cb=foreground;foreground=null;active=null;activeSession=null;dynamic=null;
        if(cb!=null)cb.failure(why);else main.post(pump);
    }
    private void complete(Item item,Document doc){complete(item,doc,true);}
    private void complete(Item item,Document doc,boolean acquired){
        if(!ArticleSession.contentCurrent(doc)){failed(revision,"登录或缓存状态已变化，请重新读取");return;}
        if(retainedForeground){bufferedResult=doc;bufferedAcquired=acquired;return;}
        if(doc.title.trim().isEmpty())doc.title=item.title;
        // restoreArticle already validated session/namespace and populated memory.
        // A disk hit is not a new acquisition: keep its file and original freshness.
        if(doc.filteredVideo)repo.rememberVideo(item);else if(acquired)repo.cacheArticle(doc);
        if(revalidateHidden)repo.reconcileVideo(item,doc);
        // A prepared text body is reusable work, not a failed network attempt.
        // Let a later window restore it from disk after its RAM entry is evicted.
        // Keep cooldowns for failures, permission shells and video-only results.
        if(doc.hasContent()&&!doc.filteredVideo&&!doc.loginRequired&&!doc.sourceUnavailable)attempted.remove(attemptKey(item));
        onPrepared.accept(item,doc);
        Repository.Result<Document> cb=foreground;foreground=null;active=null;activeSession=null;
        // Keep the delivering DynamicReader reachable while the foreground
        // callback transfers its loaded question to AnswerStream.
        try {
            if(cb!=null)cb.success(doc);
            else {if(doc.filteredVideo)onVideo.run();main.post(pump);}
        } finally {dynamic=null;}
    }
    private void cancelActive(){
        revision++;repo.cancelPending();if(loader!=null)loader.cancel();if(dynamic!=null){dynamic.close();dynamic=null;}
        if(active!=null)attempted.remove(attemptKey(active));active=null;foreground=null;activeSession=null;
        retainedForeground=false;bufferedResult=null;bufferedFailure=null;bufferedAcquired=true;deferredNetwork=false;deferredDynamic=false;
    }
    private void stopWatch(){if(policyWatch!=null){policyWatch.close();policyWatch=null;}}
    private void idle(){stopWatch();onIdle.run();}
    void pause(){stopWatch();queue.clear();main.removeCallbacks(pump);cancelActive();}
    /** Stop launching new speculative work without throwing away a nearly ready page. */
    void finishWindow(){queue.clear();main.removeCallbacks(pump);if(active==null)idle();}
    void trim(){if(foreground==null&&!retainedForeground)pause();}
    void reattach(){if(dynamic!=null&&activity!=null)dynamic.reattach(activity);}
    android.webkit.WebView takeSource(){return dynamic==null?null:dynamic.takeSource();}
    @Override public void close(){pause();closed=true;repo.close();}
}
