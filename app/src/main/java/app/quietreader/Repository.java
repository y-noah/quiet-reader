package app.quietreader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import static app.quietreader.Models.*;

public final class Repository {
    public static final String UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    public static final String DESKTOP_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    public interface Result<T> { void success(T result); void failure(String reason); default void failure(String reason,int httpStatus){failure(reason);} default void failure(String reason,int httpStatus,long retryAt){failure(reason,httpStatus);} }
    private final RequestQueue requests=new RequestQueue();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private final ArticleDiskCache disk;
    private static long articleEpoch;
    private static final java.util.concurrent.atomic.AtomicBoolean retiredCleanupStarted=new java.util.concurrent.atomic.AtomicBoolean();

    static synchronized long articleCacheEpoch(){return articleEpoch;}
    private static final class CachedDocument {
        final Document document;final int bytes;
        CachedDocument(Document document){this.document=document;bytes=DocumentSize.estimate(document);}
    }
    private static final android.util.LruCache<String,CachedDocument> articles=new android.util.LruCache<String,CachedDocument>(32*1024*1024){
        @Override protected int sizeOf(String key,CachedDocument entry){return entry.bytes;}
    };
    private static final android.util.LruCache<String,Long> articleTimes=new android.util.LruCache<>(512);
    private static final android.util.LruCache<String,String> articleSessions=new android.util.LruCache<>(512);
    public Document cachedArticle(Item i){return cachedArticle(i,null);}
    Document cachedArticle(Item i,ArticleSession captured){if(!i.source.readable())return null;String session=captured==null?ArticleDiskCache.session(i.url):captured.cookieFingerprint(i.url);synchronized(Repository.class){
        Long at=articleTimes.get(i.url);long age=at==null?-1:System.currentTimeMillis()-at;
        if(!session.equals(articleSessions.get(i.url))||age<0||age>(offline()?ArticleDiskCache.MAX_AGE:10*60_000)){articles.remove(i.url);articleTimes.remove(i.url);articleSessions.remove(i.url);return null;}CachedDocument entry=articles.get(i.url);if(entry==null)return null;if(!ArticleSession.contentCurrent(entry.document)||!TiebaReplyRelations.canReuse(entry.document,offline())||!HupuDiscussion.canReuse(entry.document,offline())){articles.remove(i.url);articleTimes.remove(i.url);articleSessions.remove(i.url);return null;}if(!ArticleVersion.selected(i,entry.document))return null;articles.put(i.url,new CachedDocument(entry.document));return articles.get(i.url)==null?null:entry.document;
    }}
    public boolean offline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);return cm!=null&&cm.getActiveNetwork()==null;}
    public void cacheArticle(Document d){
        cacheArticle(d,articleCacheEpoch());
    }
    boolean cacheArticle(Document d,long expectedEpoch){return cacheArticle(d,expectedEpoch,ArticleDiskCache.session(d.url));}
    private boolean cacheArticle(Document d,long expectedEpoch,String session){synchronized(Repository.class){
        if(expectedEpoch!=articleEpoch||!ArticleSession.contentCurrent(d)||!session.equals(ArticleDiskCache.session(d.url))||(d.cachedSession!=null&&(!session.equals(d.cachedSession)||!disk.namespace().equals(d.cachedNamespace))))return false;
        if(d.filteredVideo||d.loginRequired||d.sourceUnavailable){
            // Retain the validated acquisition stamp for type evidence without caching video bodies.
            if(d.filteredVideo&&d.cachedSession==null){d.cachedAt=System.currentTimeMillis();d.cachedSession=session;d.cachedNamespace=disk.namespace();}
            articles.remove(d.url);articleTimes.remove(d.url);articleSessions.remove(d.url);return true;
        }
        // A confirmed empty result must replace earlier unclassified video prose too.
        // Preserve the excluded IDs so reopening and later continuation cannot revive it.
        if(d.hasContent()||d.unsupportedVideo||!d.related.isEmpty()||d.filteredVideos>0||!d.filteredSectionIds.isEmpty()){long at=d.cachedAt>0?d.cachedAt:System.currentTimeMillis();d.cachedAt=at;d.cachedSession=session;d.cachedNamespace=disk.namespace();articles.put(d.url,new CachedDocument(d));articleTimes.put(d.url,at);articleSessions.put(d.url,session);disk.enqueue(d,disk.namespace(),session,at);}
        return true;
    }}
    public boolean hiddenVideo(Item item){
        if(item.source.webReading())return false;
        return item.video||VideoPolicy.url(item.source,item.url)||(!(item.source==Source.WEIBO&&VideoPolicy.weiboTopic(item.url))&&System.currentTimeMillis()-context.getSharedPreferences("video-filter",0).getLong(VideoPolicy.key(item),0)<7L*24*60*60*1000);
    }
    public List<Item> visibleItems(List<Item> items){List<Item> result=new java.util.ArrayList<>();for(Item item:items)if(item.source.readable()&&!hiddenVideo(item))result.add(item);return result;}
    /** A later, session-valid text acquisition may retire an unversioned URL marker. */
    void reconcileVideo(Item item,Document doc){
        if(item.video||VideoPolicy.url(item.source,item.url)||!item.url.equals(doc.url)||!doc.hasContent()||doc.loginRequired||doc.sourceUnavailable||BodyVideoEvidence.classify(item.source,doc))return;
        // Hupu markers cover the entire thread. Reply-only pages cannot reclassify its main post.
        if(item.source==Source.HUPU&&(java.net.URI.create(item.url).getPath().matches("/\\d+-\\d+\\.html")||doc.sections.stream().noneMatch(s->s.label.equals("主帖")||s.label.startsWith("主帖 · "))))return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("video-filter",0);String key=VideoPolicy.key(item);long marked=prefs.getLong(key,0);
        if(marked<=0||doc.cachedAt<marked||doc.cachedAt>System.currentTimeMillis())return;
        if(doc.cachedSession==null||!doc.cachedSession.equals(ArticleDiskCache.session(doc.url))||!disk.namespace().equals(doc.cachedNamespace))return;
        prefs.edit().remove(key).apply();
    }
    public void rememberVideo(Item item){
        // A dynamic topic may gain text posts later; never persist a card's type as its topic.
        if(item.source==Source.WEIBO&&VideoPolicy.weiboTopic(item.url))return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("video-filter",0);
        android.content.SharedPreferences.Editor edit=prefs.edit().putLong(VideoPolicy.key(item),System.currentTimeMillis());
        java.util.List<java.util.Map.Entry<String,?>> entries=new java.util.ArrayList<>(prefs.getAll().entrySet());
        entries.sort(java.util.Comparator.comparingLong(e->e.getValue() instanceof Long?(Long)e.getValue():0));
        for(int n=0;n<entries.size()-255;n++)edit.remove(entries.get(n).getKey());edit.apply();
        articles.remove(item.url);articleTimes.remove(item.url);
        // Remove the row from the saved board too, without making its freshness look newer.
        android.content.SharedPreferences boards=context.getSharedPreferences("boards",0);
        try{List<Item> rows=Models.fromJson(new JSONArray(boards.getString(item.source.name(),"[]")));rows.removeIf(row->VideoPolicy.key(row).equals(VideoPolicy.key(item)));boards.edit().putString(item.source.name(),Models.toJson(rows).toString()).apply();}catch(Exception ignored){}
    }
    public void invalidateArticles(){synchronized(Repository.class){articleEpoch++;articles.evictAll();articleTimes.evictAll();articleSessions.evictAll();disk.invalidate();}}
    /** Login return invalidates all earlier bodies but may carry its fresh,
     * session-checked DOM across that one invalidation. Never assigns metadata. */
    boolean importSourceRead(Document doc){synchronized(Repository.class){
        long age=doc==null?-1:System.currentTimeMillis()-doc.cachedAt;
        boolean valid=doc!=null&&doc.canPresent()&&ArticleSession.contentCurrent(doc)&&age>=0&&age<=10*60_000
            &&ArticleDiskCache.session(doc.url).equals(doc.cachedSession)&&disk.namespace().equals(doc.cachedNamespace);
        invalidateArticles();
        if(!valid)return false;
        // Namespace changes here solely because this method invalidated the old
        // cache after checking the actual source acquisition. Keep time/session/hash.
        doc.cachedNamespace=disk.namespace();
        return cacheArticle(doc,articleEpoch,doc.cachedSession);
    }}
    /** A manually selected browser snapshot is already session-bound. Preserve
     * unrelated prefetches; account changes remain isolated by each URL's cookies. */
    boolean importBrowserSnapshot(Document doc){synchronized(Repository.class){
        long age=doc==null?-1:System.currentTimeMillis()-doc.cachedAt;
        if(doc==null||!doc.canPresent()||!ArticleSession.contentCurrent(doc)||doc.loginRequired||doc.sourceUnavailable||age<0||age>10*60_000
            ||!ArticleDiskCache.session(doc.url).equals(doc.cachedSession)||!disk.namespace().equals(doc.cachedNamespace))return false;
        return cacheArticle(doc,articleEpoch,doc.cachedSession);
    }}
    public void releaseArticleMemory(){synchronized(Repository.class){articles.evictAll();articleTimes.evictAll();articleSessions.evictAll();}}
    ArticleDiskCache.Retention retainBatch(java.util.Collection<String> urls){synchronized(Repository.class){
        articleEpoch++;java.util.Set<String> keep=new java.util.HashSet<>(urls);
        for(String url:articles.snapshot().keySet())if(!keep.contains(url)){articles.remove(url);articleTimes.remove(url);articleSessions.remove(url);}
        return disk.retainBatch(keep);
    }}
    public Repository(Context context) { this.context=context.getApplicationContext();disk=new ArticleDiskCache(this.context); scheduleRetiredCleanup(this.context); }
    public void restoreArticle(Item item,java.util.function.Consumer<Document> callback){
        long epoch=articleCacheEpoch();String namespace=disk.namespace(),session=ArticleDiskCache.session(item.url);boolean offline=offline();
        requests.submit(ticket->{ArticleDiskCache.Hit hit=disk.read(item.url,namespace,session,offline,System.currentTimeMillis());
            main.post(()->{if(ticket.cancelled())return;Document document=null;synchronized(Repository.class){if(epoch==articleEpoch&&hit!=null&&ArticleVersion.selected(item,hit.document)&&ArticleSession.contentCurrent(hit.document)&&namespace.equals(disk.namespace())&&session.equals(ArticleDiskCache.session(item.url))){document=hit.document;document.cachedAt=hit.at;document.cachedSession=session;document.cachedNamespace=namespace;articles.put(item.url,new CachedDocument(document));articleTimes.put(item.url,hit.at);articleSessions.put(item.url,session);}}callback.accept(document);});
        });
    }
    private static void scheduleRetiredCleanup(Context application) {
        if(!retiredCleanupStarted.compareAndSet(false,true))return;
        // This migration only removes unsupported namespaces. Readers already
        // reject those sources, so no screen needs to wait for preference IO.
        // One finite task per process; never retain an Activity or idle worker.
        Thread worker=new Thread(()->{
            try{
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                clearRetiredSources(application);
            }catch(RuntimeException error){android.util.Log.w("New","Deferred source cache cleanup could not finish");}
        },"source-cache-migration");
        worker.setDaemon(true);
        try{worker.start();}catch(RuntimeException error){retiredCleanupStarted.set(false);android.util.Log.w("New","Deferred source cache cleanup could not start");}
    }
    private static void clearRetiredSources(Context context) {
        android.content.SharedPreferences boards=context.getSharedPreferences("boards",0);
        android.content.SharedPreferences videos=context.getSharedPreferences("video-filter",0);
        android.content.SharedPreferences.Editor boardEdit=boards.edit(),videoEdit=videos.edit();
        // Keep every supported source namespace, including platforms available through the source menu.
        java.util.Set<String> names=new java.util.HashSet<>();
        for(Source source:Source.values())if(source.readable())names.add(source.name());
        for(String key:boards.getAll().keySet()){
            String name=key.contains("_")?key.substring(0,key.indexOf('_')):key;
            if(!names.contains(name))boardEdit.remove(key);
        }
        for(String key:videos.getAll().keySet()){
            String name=key.contains("|")?key.substring(0,key.indexOf('|')):key;
            if(!names.contains(name))videoEdit.remove(key);
        }
        boardEdit.apply();videoEdit.apply();
        context.getSharedPreferences("source-session",0).edit().remove("tieba-desktop").apply();
    }
    private boolean validBoardCache(Source s) {
        String endpoint=context.getSharedPreferences("boards",0).getString(s.name()+"_endpoint","");
        return s.readable()&&(s.endpoint.equals(endpoint)||s==Source.WEIBO&&WeiboBoard.compatibleCache(endpoint));
    }
    public long cachedAt(Source s) { return validBoardCache(s)?context.getSharedPreferences("boards",0).getLong(s.name()+"_time",0):0; }
    public void cache(Source s,List<Item> items) {if(!s.readable())return;android.content.SharedPreferences.Editor edit=context.getSharedPreferences("boards",0).edit().putString(s.name(),Models.toJson(visibleItems(items)).toString()).putString(s.name()+"_endpoint",s.endpoint).putLong(s.name()+"_time",System.currentTimeMillis());edit.apply();}
    public List<Item> cached(Source s) {
        if(!validBoardCache(s))return java.util.Collections.emptyList();
        try { return visibleItems(Models.fromJson(new JSONArray(context.getSharedPreferences("boards",0).getString(s.name(),"[]")))); }
        catch(Exception e) { return java.util.Collections.emptyList(); }
    }
    public void board(Source s,Result<List<Item>> cb) {
        if(!s.readable()){cb.failure("此平台已移除");return;}
        String cookie=CookieManager.getInstance().getCookie(s.endpoint);
        String legacyCookie=s==Source.WEIBO?CookieManager.getInstance().getCookie(WeiboBoard.LEGACY):null;
        String userAgent=SourceSession.userAgent(context,s);
        requests.submit(ticket->{
            try {
                List<Item> items=s==Source.WEIBO
                        ?WeiboBoard.load(url->fetch(url,url.equals(WeiboBoard.PRIMARY)?cookie:legacyCookie,s.boardPage(),ticket,userAgent))
                        :SourceParser.list(s,fetch(s.endpoint,cookie,s.endpoint,ticket,userAgent));
                ticket.check();
                main.post(()->{if(!ticket.cancelled()){cache(s,items);cb.success(items);}});
            } catch(Exception e) { main.post(()->{if(!ticket.cancelled())cb.failure(explain(e),e instanceof HttpFailure?((HttpFailure)e).status:0,e instanceof HttpFailure?((HttpFailure)e).retryAt:0);}); }
        });
    }
    public void article(Item item,Result<Document> cb) {
        article(item,cb,false);
    }
    public void articleForReading(Item item,Result<Document> cb){article(item,cb,true);}
    private void article(Item item,Result<Document> cb,boolean explicitReading){
        if(!item.source.readable()){cb.failure("此平台已移除");return;}
        String cookie=CookieManager.getInstance().getCookie(item.url);
        String userAgent=SourceSession.userAgent(context,item.source);
        long cacheEpoch=articleCacheEpoch();
        String cacheSession=ArticleCookies.fingerprint(item.url,cookie);
        requests.submit(ticket->{
            try {
                boolean topic=item.source==Source.TIEBA&&TiebaTopic.isTopic(item.url);
                java.util.function.Predicate<String> pageGuard=topic?target->TiebaTopic.id(target)==TiebaTopic.id(item.url):DiscussionPages.supports(item.source,item.url)?target->DiscussionPages.samePage(item.source,item.url,target):null;
                String html=new String(bytes(item.url,cookie,item.url,4*1024*1024,ticket,userAgent,pageGuard),StandardCharsets.UTF_8);
                Document doc=explicitReading?SourceParser.readingArticle(item.source,html,item.url):SourceParser.article(item.source,html,item.url);
                if(topic){
                    List<Item> choices=List.copyOf(doc.related);Item post=TiebaTopic.target(item,choices);if(post==null)throw new java.io.IOException("话题当前没有所选的可读取帖子");
                    ticket.check();String postCookie=CookieManager.getInstance().getCookie(post.url),postSession=ArticleCookies.fingerprint(post.url,postCookie);long selectedAt=System.currentTimeMillis();
                    String body=new String(bytes(post.url,postCookie,item.url,4*1024*1024,ticket,userAgent,target->DiscussionPages.samePage(Source.TIEBA,post.url,target)),StandardCharsets.UTF_8);
                    Document thread=explicitReading?SourceParser.readingArticle(Source.TIEBA,body,post.url):SourceParser.article(Source.TIEBA,body,post.url);
                    if(thread.title.trim().isEmpty())thread.title=post.title;
                    doc=TiebaTopic.attach(item.url,choices,thread,postSession,selectedAt);
                }
                if(doc.title.trim().isEmpty()&&!topic)doc.title=item.title;
                ArticleVersion.capture(item,doc);
                ticket.check();if(!cacheArticle(doc,cacheEpoch,cacheSession)){main.post(()->{if(!ticket.cancelled())cb.failure("登录或缓存状态已变化，请重新读取");});return;}Document result=doc;main.post(()->{if(!ticket.cancelled()){if(cacheEpoch==articleCacheEpoch()&&ArticleSession.contentCurrent(result)&&cacheSession.equals(ArticleDiskCache.session(item.url)))cb.success(result);else cb.failure("登录或缓存状态已变化，请重新读取");}});
            }
            catch(Exception e) { main.post(()->{if(!ticket.cancelled())cb.failure(explain(e));}); }
        });
    }
    public static String explain(Exception e) {
        if(e instanceof HttpFailure&&(((HttpFailure)e).status==401||((HttpFailure)e).status==403))return "来源要求登录或限制了当前访问，请在来源页登录后重新读取";
        if(e instanceof java.net.SocketTimeoutException) return "连接超时；可重试，或在来源页登录后读取";
        if(e instanceof java.net.UnknownHostException) return "网络暂不可用，已保留缓存";
        if(e instanceof SecurityException) return "来源链接不符合安全规则";
        return "平台暂未返回可用内容；可能需要登录或更新适配";
    }
    public static String fetch(String url,String cookie,String referer) throws Exception {
        return new String(bytes(url,cookie,referer,4*1024*1024),StandardCharsets.UTF_8);
    }
    static final class HttpFailure extends java.io.IOException {
        final int status;final long retryAt;
        HttpFailure(int status){this(status,0);}
        HttpFailure(int status,long retryAt){super("HTTP "+status);this.status=status;this.retryAt=retryAt;}
    }
    static String fetch(String url,String cookie,String referer,RequestQueue.Ticket ticket,String userAgent)throws Exception{return new String(bytes(url,cookie,referer,4*1024*1024,ticket,userAgent),StandardCharsets.UTF_8);}
    public static byte[] bytes(String url,String cookie,String referer,int limit) throws Exception {
        return bytes(url,cookie,referer,limit,null);
    }
    static byte[] imageBytes(String url,String referer,java.util.function.Function<String,String> cookies)throws Exception{
        return imageBytes(url,referer,cookies,null,8*1024*1024);
    }
    static byte[] imageBytes(String url,String referer,java.util.function.Function<String,String> cookies,RequestQueue.Ticket ticket,int limit)throws Exception{
        return bytes(url,null,referer,limit,ticket,DESKTOP_UA,null,cookies);
    }
    private static byte[] bytes(String url,String cookie,String referer,int limit,RequestQueue.Ticket ticket) throws Exception {
        return bytes(url,cookie,referer,limit,ticket,DESKTOP_UA);
    }
    private static byte[] bytes(String url,String cookie,String referer,int limit,RequestQueue.Ticket ticket,String userAgent) throws Exception {
        return bytes(url,cookie,referer,limit,ticket,userAgent,null);
    }
    private static byte[] bytes(String url,String cookie,String referer,int limit,RequestQueue.Ticket ticket,String userAgent,java.util.function.Predicate<String> pageGuard) throws Exception {
        return bytes(url,cookie,referer,limit,ticket,userAgent,pageGuard,null);
    }
    private static byte[] bytes(String url,String cookie,String referer,int limit,RequestQueue.Ticket ticket,String userAgent,java.util.function.Predicate<String> pageGuard,java.util.function.Function<String,String> imageCookies) throws Exception {
        String current=url;
        for(int redirect=0;redirect<5;redirect++) {
            if(ticket!=null)ticket.check();
            if(pageGuard!=null&&!pageGuard.test(current))throw new SecurityException("来源跳转到了其他帖子或页码");
            UrlPolicy.requirePublicHost(current);
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            if(imageCookies!=null)c.setUseCaches(false);
            if(WeiboBoard.endpoint(current)){c.setUseCaches(false);c.setRequestProperty("Cache-Control","no-cache");}
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(5000); c.setReadTimeout(7000);
            c.setRequestProperty("User-Agent",userAgent);
            c.setRequestProperty("Accept","text/html,application/json,image/*;q=0.8,*/*;q=0.5");
            if(referer!=null) c.setRequestProperty("Referer",referer);
            // Never forward a session cookie to a different host on a redirect.
            if(imageCookies!=null){String scoped=imageCookies.apply(current);if(scoped!=null&&!scoped.isEmpty())c.setRequestProperty("Cookie",scoped);}
            else if(cookie!=null&&UrlPolicy.host(current).equals(UrlPolicy.host(url))) c.setRequestProperty("Cookie",cookie);
            try {
                if(ticket!=null){ticket.bind(c);ticket.check();}
                int code=c.getResponseCode();
                if(ticket!=null)ticket.check();
                if(code>=300&&code<400) { current=UrlPolicy.normalize(current,c.getHeaderField("Location")); continue; }
                if(code!=200) throw new HttpFailure(code,app.quietreader.discovery.RefreshBackoff.retryAfter(c.getHeaderField("Retry-After"),System.currentTimeMillis()));
                if(c.getContentLengthLong()>limit) throw new java.io.IOException("Response too large");
                try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] block=new byte[8192]; int size;
                    while((size=in.read(block))!=-1) { if(ticket!=null)ticket.check();if(out.size()+size>limit) throw new java.io.IOException("Response too large"); out.write(block,0,size); }
                    return out.toByteArray();
                }
            } finally { if(ticket!=null)ticket.unbind(c);c.disconnect(); }
        }
        throw new java.io.IOException("Too many redirects");
    }
    public void cancelPending(){requests.cancelPending();}
    public void close() { requests.close(); }
}
