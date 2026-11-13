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
    private java.util.function.Consumer<Item> onFailure=item->{};
    void observeFailure(java.util.function.Consumer<Item> observer){onFailure=observer;}
    private boolean reserved;
    private boolean speculationAllowed;
    private static final class Attempt {
        final long until;final String scope;final int count;final boolean retryPending;
        Attempt(long until,String scope,int count,boolean retryPending){this.until=until;this.scope=scope;this.count=count;this.retryPending=retryPending;}
    }
    private final Map<String,Attempt> attempted=new LinkedHashMap<String,Attempt>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Attempt> entry){return size()>128;}
    };
    private final Deque<Item> queue=new ArrayDeque<>();
    private Item active;
    private DynamicReader dynamic;
    private Repository.Result<Document> foreground;
    private boolean retainedForeground,deferredNetwork,deferredDynamic,bufferedAcquired=true;
    private ArticleSession activeSession;
    private Document bufferedResult;
    private ArticleFailure bufferedFailure;
    private int revision;
    private long requestStarted;
    private void trace(String state){if(active!=null)android.util.Log.d("NewPrefetch",active.source.name()+" "+state+" ms="+(SystemClock.elapsedRealtime()-requestStarted));}
    private boolean closed;
    private boolean wholeTopics;
    private int topicLimit=TiebaTopic.MAX_POSTS;
    ArticlePreloader topicWindow(int limit){wholeTopics=true;topicLimit=Math.max(1,limit);return this;}
    ArticlePreloader wholeTopics(){wholeTopics=true;return this;}
    void retryTopic(Item item){attempted.keySet().removeIf(key->key.startsWith(item.source.name()+"|"+item.url+"@"));}
    private void prepared(Item item,Document doc){
        if(wholeTopics&&speculationAllowed&&doc.topic!=null){
            topic(doc);
            queue.removeIf(queued->queued.url.equals(item.url));
            List<Item> missing=new ArrayList<>();
            int wanted=0;for(Item post:doc.topic.choices)if(!post.video){if(wanted++>=topicLimit)break;if(TopicBundle.find(doc,post.url)==null)missing.add(TiebaTopic.select(item,doc.topic,post));}
            for(int n=missing.size()-1;n>=0;n--)queue.addFirst(missing.get(n));
        }
        onPrepared.accept(item,doc);
    }
    private TiebaTopic.Resolved topic;
    private String topicScope="";
    void topic(Document doc){
        if(doc.topic==null||!ArticleSession.contentCurrent(doc)||doc.cachedSession==null||!doc.cachedSession.equals(ArticleDiskCache.session(doc.url)))return;
        ArticleSession captured=new ArticleSession(activity,doc.url);
        if(captured.owns(doc)){topic=doc.topic;topicScope=captured.scopeIdentity();}
    }
    private app.quietreader.ai.RecommendationPolicy.Watch policyWatch;

    ArticlePreloader(Activity activity,Runnable onVideo){this(activity,onVideo,null);}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader){this(activity,onVideo,loader,3,()->{});}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader,int windowSize,Runnable onIdle){this(activity,onVideo,loader,windowSize,onIdle,false);}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader,int windowSize,Runnable onIdle,boolean revalidateHidden){this.activity=activity;this.onVideo=onVideo;this.loader=loader;this.revalidateHidden=revalidateHidden;this.windowSize=Math.max(1,Math.min(app.quietreader.discovery.FeedReserve.CAPACITY,windowSize));this.onIdle=onIdle;repo=new Repository(activity);}
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
        speculationAllowed=true;
        queue.clear();
        Set<String> seen=new HashSet<>();
        int bounded=Math.max(1,Math.min(app.quietreader.discovery.FeedReserve.CAPACITY,limit));
        for(Item item:candidates){if(seen.add(attemptKey(item)))queue.add(item);if(queue.size()==bounded)break;}
        if(active!=null&&queue.stream().noneMatch(i->ArticleVersion.sameRequest(i,active)||wholeTopics&&i.url.equals(active.url)))cancelActive();
        if(policyWatch==null){policyWatch=new app.quietreader.ai.RecommendationPolicy.Watch(activity,reason->pause());policyWatch.start();}
        main.removeCallbacks(pump);main.postDelayed(pump,immediate?0:350);
    }
    private final Runnable pump=this::next;
    private boolean cachedVersion(Item item,Document document){return ArticleVersion.readableCached(item,document,System.currentTimeMillis(),repo.offline());}
    private void next(){
        ArticleSessionSnapshot.run(this::nextSnapshot);
    }
    private void nextSnapshot(){
        if(closed||reserved||active!=null||foreground!=null||retainedForeground)return;
        if(!app.quietreader.ai.RecommendationPolicy.restriction(activity).isEmpty()){queue.clear();idle();return;}
        if(!queue.isEmpty()&&!ArticleDiskCache.acceptsPrefetch()){main.postDelayed(pump,100);return;}
        // A warm cache can otherwise drain the entire window synchronously on
        // the UI thread, including cookie checks and every prepared callback.
        int remaining=Math.min(2,queue.size()+TiebaTopic.MAX_POSTS);long earliest=Long.MAX_VALUE;
        while(remaining-->0&&!queue.isEmpty()){
            Item item=queue.removeFirst();Attempt previous=attempted.get(attemptKey(item));
            if(!item.source.readable()||!item.source.unifiedReading()||item.video||VideoPolicy.url(item.source,item.url)||!UrlPolicy.belongs(item.source,item.url)||item.url.equals(item.source.login)||item.url.equals(item.source.endpoint)||(!revalidateHidden&&repo.hiddenVideo(item)))continue;
            ArticleSession session=new ArticleSession(activity,item);
            boolean refreshHidden=revalidateHidden&&repo.hiddenVideo(item);
            // A URL-only marker has no metadata version. Recheck the source instead of
            // assigning any previously cached body to the current candidate version.
            Document cached=refreshHidden?null:repo.cachedArticle(item,session);
            if(cached!=null&&cachedVersion(item,cached)){prepared(item,cached);continue;}
            long now=SystemClock.elapsedRealtime();
            if(previous!=null&&!previous.scope.equals(session.scopeIdentity()))previous=null;
            if(previous!=null&&now<previous.until){if(previous.retryPending){queue.addLast(item);earliest=Math.min(earliest,previous.until);}continue;}
            int count=previous!=null&&previous.retryPending?previous.count+1:1;
            active=item;requestStarted=now;trace("start");activeSession=session;attempted.put(attemptKey(item),new Attempt(now+5*60_000,session.scopeIdentity(),count,false));int request=++revision;
            if(loader!=null)loader.load(item,new Repository.Result<Document>(){
                public void success(Document doc){if(valid(request))complete(item,doc);}
                public void failure(String why){failed(request,why);}
                public void failure(String why,int status,long retryAt){failed(request,ArticleFailure.http(why,status,retryAt));}
                public void failure(ArticleFailure failure){failed(request,failure);}
            });
            else if(refreshHidden)loadNetwork(item,request);
            else repo.restoreArticle(item,restored->{if(!valid(request))return;trace("disk="+(restored!=null)+" metadata="+(restored!=null&&ArticleVersion.matches(item,restored))+" offline="+repo.offline());if(restored!=null&&cachedVersion(item,restored))complete(item,restored,false);else loadNetwork(item,request);});
            return;
        }
        if(!queue.isEmpty()&&earliest!=Long.MAX_VALUE){main.postDelayed(pump,Math.max(1,earliest-SystemClock.elapsedRealtime()));return;}
        if(!queue.isEmpty()){main.postDelayed(pump,16);return;}idle();
    }
    /** Must be called before replacing the board. A true result now owns the callback. */
    boolean reserve(Item item){
        if(closed||reserved||foreground!=null||retainedForeground||active==null||active.source!=item.source||!active.url.equals(item.url))return false;
        if(!ArticleVersion.sameRequest(active,item))return false;
        // Explicit reading cannot promote a hidden-video acquisition; stop() cancels it before reopening.
        if(revalidateHidden&&repo.hiddenVideo(item))return false;
        reserved=true;stopWatch();queue.clear();main.removeCallbacks(pump);
        onIdle=()->{};onVideo=()->{};onPrepared=(ignoredItem,ignoredDoc)->{};onFailure=ignoredItem->{};
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
        onVideo=()->{};onIdle=()->{};onPrepared=(item,doc)->{};onFailure=item->{};
        if(dynamic!=null)dynamic.detach();
    }
    void resumeForeground(Activity owner,Item item,Repository.Result<Document> callback,Runnable video){
        if(!retainedForeground)return;
        if(!canRetainForeground(item)){close();callback.failure("登录或缓存状态已变化，请重新读取");return;}
        adopt(owner,video);retainedForeground=false;foreground=callback;
        if(dynamic!=null){dynamic.attach(owner,dynamicResult(item,revision));return;}
        Document result=bufferedResult;ArticleFailure failure=bufferedFailure;boolean acquired=bufferedAcquired;bufferedResult=null;bufferedFailure=null;bufferedAcquired=true;
        if(result!=null)complete(item,result,acquired);
        else if(failure!=null)failed(revision,failure);
        else if(deferredDynamic){deferredDynamic=false;loadDynamic(item,revision);}
        else if(deferredNetwork){deferredNetwork=false;loadNetwork(item,revision);}
    }
    private void loadNetwork(Item item,int request){
        if(!valid(request))return;
        if(retainedForeground){deferredNetwork=true;return;}
        if(repo.offline()){failed(request,"当前离线且正文尚未缓存",false);return;}
        trace("native-route");
        if(SourceSession.usesWeb(activity,item.source)){loadDynamic(item,request);return;}
        repo.article(item,new Repository.Result<Document>(){
            public void success(Document doc){
                if(!valid(request))return;
                // Repository.article has already saved this session-checked acquisition.
                if(SourceSession.nativeDocumentReady(item.source,doc)&&!doc.loginRequired&&!doc.sourceUnavailable)complete(item,doc,false);else loadDynamic(item,request);
            }
            public void failure(String why){if(valid(request))loadDynamic(item,request);}
            public void failure(ArticleFailure failure){if(!valid(request))return;if(failure.mayFallback())loadDynamic(item,request);else failed(request,failure);}
        });
    }
    private void loadDynamic(Item item,int request){
        if(!valid(request))return;
        if(retainedForeground){deferredDynamic=true;return;}
        activeSession=new ArticleSession(activity,item);
        trace(topic!=null&&topicScope.equals(activeSession.scopeIdentity())&&TiebaTopic.reusable(item,topic,System.currentTimeMillis())?"topic-post-route":"browser-route");
        try{dynamic=new DynamicReader(activity,item,dynamicResult(item,request),foreground!=null||retainedForeground,topic,topicScope);}
        catch(RuntimeException|LinkageError unavailable){failed(request,"来源阅读器暂不可用，请稍后重试");}
    }
    private Repository.Result<Document> dynamicResult(Item item,int request){
        return new Repository.Result<Document>(){
            public void success(Document doc){if(valid(request))complete(item,doc);}
            public void failure(String why){failed(request,why);}
            public void failure(ArticleFailure failure){failed(request,failure);}
        };
    }
    private void failed(int request,String why){failed(request,ArticleFailure.unknown(why));}
    private void failed(int request,String why,boolean sourceFailure){
        failed(request,sourceFailure?ArticleFailure.unknown(why):new ArticleFailure(ArticleFailure.Kind.OFFLINE,why));
    }
    private void failed(int request,ArticleFailure failure){
        if(!valid(request))return;
        if(retainedForeground){bufferedFailure=failure;return;}
        boolean obsoleteScope=failure.kind==ArticleFailure.Kind.SESSION_CHANGED||activeSession!=null&&!activeSession.current();
        trace("failed");Item failedItem=active;Repository.Result<Document> cb=foreground;foreground=null;active=null;activeSession=null;dynamic=null;
        String key=attemptKey(failedItem);Attempt previous=attempted.get(key);long now=SystemClock.elapsedRealtime();
        boolean offline=failure.kind==ArticleFailure.Kind.OFFLINE;
        long serverDelay=Math.max(0,failure.retryAt-System.currentTimeMillis());
        // One delayed retry within the existing action window; unaffected items go first.
        // Long server cooldowns never keep an idle timer alive or start a WebView bypass.
        boolean retry=cb==null&&!reserved&&speculationAllowed&&!obsoleteScope&&failure.retryable()&&previous!=null&&previous.count<2&&serverDelay<=1500;
        // The home observer resolves the current account scope. Never attribute a
        // late old-account failure to that new scope or impose its cooldown there.
        if(offline||obsoleteScope)attempted.remove(key);
        else if(previous!=null){long delay=retry?1500:failure.retryable()?60_000:5*60_000;attempted.put(key,new Attempt(now+Math.max(delay,serverDelay),previous.scope,previous.count,retry));}
        if(retry){if(queue.stream().noneMatch(i->ArticleVersion.sameRequest(i,failedItem)))queue.addLast(failedItem);}
        else if(cb==null&&!offline&&!obsoleteScope)onFailure.accept(failedItem);
        if(cb!=null)cb.failure(obsoleteScope?ArticleFailure.session():failure);else main.post(pump);
    }
    private void complete(Item item,Document doc){complete(item,doc,true);}
    private void complete(Item item,Document doc,boolean acquired){
        if(!ArticleSession.contentCurrent(doc)){failed(revision,"登录或缓存状态已变化，请重新读取");return;}
        if(retainedForeground){bufferedResult=doc;bufferedAcquired=acquired;return;}
        trace(doc.hasContent()&&!doc.filteredVideo&&!doc.loginRequired&&!doc.sourceUnavailable?"body-ready":"invalid-body");
        if(doc.title.trim().isEmpty())doc.title=item.title;
        // restoreArticle already validated session/namespace and populated memory.
        // A disk hit is not a new acquisition: keep its file and original freshness.
        if(doc.filteredVideo)repo.rememberVideo(item);else if(acquired)repo.cacheArticle(doc);
        if(revalidateHidden)repo.reconcileVideo(item,doc);
        // A prepared text body is reusable work, not a failed network attempt.
        // Let a later window restore it from disk after its RAM entry is evicted.
        // Keep cooldowns for failures, permission shells and video-only results.
        if(doc.hasContent()&&!doc.filteredVideo&&!doc.loginRequired&&!doc.sourceUnavailable)attempted.remove(attemptKey(item));
        if(!doc.hasContent()||doc.filteredVideo||doc.loginRequired||doc.sourceUnavailable)onFailure.accept(item);
        prepared(item,doc);
        Repository.Result<Document> cb=foreground;foreground=null;active=null;activeSession=null;
        // Keep the delivering DynamicReader reachable while the foreground
        // callback transfers its loaded question to AnswerStream.
        try {
            if(cb!=null)cb.success(doc);
            else {if(doc.filteredVideo)onVideo.run();main.post(pump);}
        } finally {dynamic=null;}
    }
    private void cancelActive(){
        trace("cancelled");revision++;repo.cancelPending();if(loader!=null)loader.cancel();if(dynamic!=null){dynamic.close();dynamic=null;}
        if(active!=null)attempted.remove(attemptKey(active));active=null;foreground=null;activeSession=null;
        retainedForeground=false;bufferedResult=null;bufferedFailure=null;bufferedAcquired=true;deferredNetwork=false;deferredDynamic=false;
    }
    private void stopWatch(){if(policyWatch!=null){policyWatch.close();policyWatch=null;}}
    private void idle(){stopWatch();onIdle.run();}
    void pause(){speculationAllowed=false;stopWatch();queue.clear();main.removeCallbacks(pump);cancelActive();}
    /** Stop launching new speculative work without throwing away a nearly ready page. */
    void finishWindow(){speculationAllowed=false;queue.clear();main.removeCallbacks(pump);if(active==null)idle();}
    void trim(){if(foreground==null&&!retainedForeground)pause();}
    void reattach(){if(dynamic!=null&&activity!=null)dynamic.reattach(activity);}
    android.webkit.WebView takeSource(){return dynamic==null?null:dynamic.takeSource();}
    @Override public void close(){pause();closed=true;repo.close();}
}
