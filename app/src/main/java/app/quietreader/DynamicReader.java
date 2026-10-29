package app.quietreader;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import org.json.JSONTokener;
import static app.quietreader.Models.*;

/** Loads a source using the device session. Only normalized text is passed to the UI. */
public final class DynamicReader {
    private final WebView web;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Item item;
    private Repository.Result<Document> callback;
    private final ArticleSession session;
    private Activity owner;
    private final int sessionRetries;
    private boolean explicitReading;
    private int attemptLimit;
    private long deadlineAt;
    private final long startedAt;
    private final Runnable deadline=this::expire;
    private DynamicReader replacement;
    private boolean ended=false;
    private boolean delivering=false,ownsWeb=true;
    private boolean closed;
    private Document pendingResult;
    private String pendingFailure;
    private int attempts=0;
    private Document best;
    private volatile java.util.List<Item> topicChoices=java.util.Collections.emptyList();
    private volatile String topicPostUrl="",topicPostSession="";
    private long topicSelectedAt;
    private String lastSnapshot="";
    private final ReadNavigation navigation=new ReadNavigation();
    private final java.util.Set<String> upgradedUrls=new java.util.HashSet<>();
    private final java.util.concurrent.ExecutorService parser=java.util.concurrent.Executors.newSingleThreadExecutor();
    public DynamicReader(Activity activity,Item item,Repository.Result<Document> cb) {
        this(activity,item,cb,1,false);
    }
    DynamicReader(Activity activity,Item item,Repository.Result<Document> cb,boolean explicitReading){this(activity,item,cb,1,explicitReading);}
    private DynamicReader(Activity activity,Item item,Repository.Result<Document> cb,int sessionRetries,boolean explicitReading) {
        this(activity,item,cb,sessionRetries,explicitReading,0,0);
    }
    private DynamicReader(Activity activity,Item item,Repository.Result<Document> cb,int sessionRetries,boolean explicitReading,long inheritedDeadline,long inheritedStart) {
        this.owner=activity;this.sessionRetries=sessionRetries;this.explicitReading=explicitReading;
        // Douyin hydration and Tieba's topic -> JS-rendered post plus one initial
        // session reacquisition can exceed 22s in observed public reads. Only an
        // explicit read receives this budget; speculation keeps its original limit.
        boolean slowForeground=explicitReading&&(item.source==Source.DOUYIN||item.source==Source.TIEBA&&TiebaTopic.isTopic(item.url));
        attemptLimit=slowForeground?28:14;
        long now=android.os.SystemClock.elapsedRealtime();
        startedAt=inheritedStart>0?inheritedStart:now;
        deadlineAt=inheritedDeadline>0?inheritedDeadline:now+(slowForeground?40000:22000);
        this.item=item; callback=cb; session=new ArticleSession(activity,item);web=new WebView(new android.content.MutableContextWrapper(activity));
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        SourceSession.configure(activity,item.source,web); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setMediaPlaybackRequiresUserGesture(true);
        // This surface only extracts text and image URLs; the own reader loads actual pictures.
        // Do not apply this to the explicit login view, where image challenges must stay visible.
        s.setBlockNetworkImage(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){
                String path=r.getUrl().getPath();
                if(!r.isForMainFrame()&&path!=null&&path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m4s|webm|m3u8|mp3|m4a|aac|ts)$"))
                    return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));
                return null;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r) {
                String url=r.getUrl().toString(),secure=UrlPolicy.upgradePlatformNavigation(item.source,url);
                if(r.isForMainFrame()&&!secure.isEmpty()) {
                    if(upgradedUrls.size()<4&&upgradedUrls.add(secure)){navigating(secure);w.loadUrl(secure);}
                    else fail("来源反复跳转，已停止读取；可打开来源页后重试");
                    return true;
                }
                boolean blocked=!UrlPolicy.belongs(item.source,url);
                if(r.isForMainFrame()&&!blocked)navigating(url);
                return blocked;
            }
            @Override public void onPageCommitVisible(WebView w,String url) { ready(w,url); }
            @Override public void onPageFinished(WebView w,String url) { ready(w,url); }
            @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e) { h.cancel(); fail("来源证书异常，已停止读取"); }
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail("来源连接失败，请检查网络后重试");}
            @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse response){
                if(r.isForMainFrame()&&response.getStatusCode()>=400){
                    boolean restricted=response.getStatusCode()==401||response.getStatusCode()==403;
                    if(restricted&&retryChangedSession())return;
                    fail(restricted?"来源要求登录或限制了访问。请在来源页确认权限后重新读取。":"来源暂时返回错误，可稍后重试或打开来源页确认。");
                }
            }
        });
        web.setDownloadListener((u,a,c,m,n)->{});
        SourceSurface.attach(activity,web);
        handler.postDelayed(deadline,Math.max(0,deadlineAt-android.os.SystemClock.elapsedRealtime()));
        // Start after construction so the owner is assigned before navigation callbacks.
        // Closing in the same UI turn cancels this pending navigation too.
        handler.post(()->{if(!ended)web.loadUrl(item.url);});
    }
    private void navigating(String url){navigation.begin(url);best=null;lastSnapshot="";}
    /** A user opening speculative work owns the foreground policy, without reloading it. */
    void promote(){
        if(closed)return;
        if(replacement!=null){replacement.promote();return;}
        if(ended||explicitReading||android.os.SystemClock.elapsedRealtime()>=deadlineAt)return;
        explicitReading=true;lastSnapshot="";
        if(item.source==Source.DOUYIN||item.source==Source.TIEBA&&TiebaTopic.isTopic(item.url)){
            attemptLimit=28;deadlineAt=startedAt+40000;
            handler.removeCallbacks(deadline);
            handler.postDelayed(deadline,Math.max(0,deadlineAt-android.os.SystemClock.elapsedRealtime()));
        }
    }
    private void ready(WebView view,String url){
        if(!ended&&navigation.ready(url,view.getUrl())&&attempts==0)inspect();
    }
    private void inspect() {
        if(ended)return;
        if(navigation.pending()){handler.postDelayed(this::inspect,1200);return;}
        attempts++;
        if(!expectedSource(web.getUrl())) { fail("来源跳转到登录、其他问题或不支持的页面，请重新读取"); return; }
        final long revision=navigation.revision();
        final boolean requestedExplicit=explicitReading;
        web.evaluateJavascript(SourceSnapshot.script(item.source),raw->{
            if(ended)return;
            parser.execute(()->{
            Document parsed=null;boolean wrongPage=false;
            try {
                Object value=new JSONTokener(raw).nextValue();
                if(value instanceof String) {
                    org.json.JSONObject payload=new org.json.JSONObject((String)value);
                    wrongPage=!expectedSource(payload.optString("url"));
                    if(!wrongPage&&!payload.isNull("html")) {
                        String html=payload.getString("html");
                        if(html.length()<4*1024*1024){
                            parsed=requestedExplicit?SourceParser.readingArticle(item.source,html,payload.getString("url")):SourceParser.article(item.source,html,payload.getString("url"));
                            if(item.source==Source.TIEBA)TiebaReplyTimes.attach(parsed,payload.optJSONObject("replyTimes"),System.currentTimeMillis());
                            // Resolve relative links against the loaded document while retaining
                            // the requested reading/cache identity already validated above.
                            if(!topicPostUrl.isEmpty()){
                                parsed.url=topicPostUrl;
                                if(parsed.title.trim().isEmpty())for(Item choice:topicChoices)if(choice.url.equals(topicPostUrl)){parsed.title=choice.title;break;}
                                parsed=TiebaTopic.attach(item.url,topicChoices,parsed,topicPostSession,topicSelectedAt);
                            }else parsed.url=item.url;
                            if(parsed.title.trim().isEmpty()&&topicPostUrl.isEmpty())parsed.title=item.title;
                        }
                    }
                }
            } catch(Exception ignored) {}
            Document doc=parsed;boolean wrongSource=wrongPage;
            String snapshot=doc==null?"":DocumentFingerprint.of(doc);
            handler.post(()->{
                if(ended)return;
                if(!navigation.current(revision)){lastSnapshot="";handler.postDelayed(this::inspect,1200);return;}
                if(requestedExplicit!=explicitReading){lastSnapshot="";handler.post(this::inspect);return;}
                if(wrongSource||!expectedSource(web.getUrl())) { fail("来源跳转到登录、其他问题或不支持的页面，请重新读取");return; }
                if(item.source==Source.TIEBA&&TiebaTopic.isTopic(item.url)&&topicPostUrl.isEmpty()){
                    Item target=doc==null?null:TiebaTopic.target(item,doc.related);
                    if(target!=null){
                        topicChoices=java.util.List.copyOf(doc.related);topicSelectedAt=System.currentTimeMillis();topicPostSession=ArticleDiskCache.session(target.url);topicPostUrl=target.url;
                        attempts=0;navigating(target.url);web.loadUrl(target.url);return;
                    }
                    if(attempts>=2){fail(doc!=null&&doc.loginRequired?"来源要求登录后才能读取该话题。":"话题当前没有可读取的图文帖子，可在来源页确认后重试。");return;}
                }
                if(doc!=null){
                    if(doc.canPresent()&&(!TiebaTopic.isTopic(item.url)||doc.topic!=null)) {
                        boolean stable=snapshot.equals(lastSnapshot);
                        best=doc;
                        // A login mask may precede the asynchronous session/reply response.
                        if(attempts>=2&&stable&&(!doc.loginRequired||attempts>=5)) { finish(doc); return; }
                    }
                }
                // An empty/failed snapshot breaks consecutiveness too.
                lastSnapshot=snapshot;
            if(attempts>=attemptLimit) { if(best!=null)finish(best); else fail("页面未提供可提取正文，可打开来源页确认后重试"); }
            else handler.postDelayed(this::inspect,1200);
            });
            });
        });
    }
    private boolean expectedSource(String url) {
        if(!UrlPolicy.belongs(item.source,url))return false;
        if(item.source==Source.TIEBA&&TiebaTopic.isTopic(item.url))return topicPostUrl.isEmpty()?TiebaTopic.id(url)==TiebaTopic.id(item.url):DiscussionPages.samePage(Source.TIEBA,topicPostUrl,url);
        if(item.source==Source.BILIBILI)return BilibiliArticle.sameVideo(item.url,url);
        if(item.source==Source.DOUYIN)return DouyinArticle.sameContent(item.url,url);
        if(item.source==Source.XHS)return XhsArticle.sameNote(item.url,url);
        if(DiscussionPages.supports(item.source,item.url))return DiscussionPages.samePage(item.source,item.url,url);
        if(UrlPolicy.sameForumPost(item.source,item.url,item.url))
            return UrlPolicy.sameForumPost(item.source,item.url,url);
        if(item.source==Source.WEIBO&&!WeiboPost.id(item.url).isEmpty())return WeiboPost.same(item.url,url);
        return !AnswerStream.sameQuestion(item,item.url)||AnswerStream.sameQuestion(item,url);
    }
    /** Validate the retained acquisition before attaching a replacement Activity. */
    boolean matches(Item current){return !closed&&current!=null&&ArticleVersion.sameRequest(current,item)&&(replacement!=null?replacement.matches(current):session.current()&&topicCurrent());}
    private boolean topicCurrent(){return topicPostUrl.isEmpty()||topicPostSession.equals(ArticleDiskCache.session(topicPostUrl));}
    /** Remove every old Activity reference while keeping one configuration-change acquisition. */
    void detach(){
        callback=null;owner=null;
        if(replacement!=null)replacement.detach();
        if(ownsWeb){
            if(web.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)web.getParent()).removeView(web);
            if(web.getContext() instanceof android.content.MutableContextWrapper){android.content.MutableContextWrapper context=(android.content.MutableContextWrapper)web.getContext();context.setBaseContext(context.getApplicationContext());}
        }
    }
    void attach(Activity activity,Repository.Result<Document> result){
        if(closed)return;callback=result;reattach(activity);
        if(replacement!=null)replacement.attach(activity,result);else deliver();
    }
    void reattach(Activity activity){if(closed)return;owner=activity;if(replacement!=null){replacement.reattach(activity);return;}if(ownsWeb){if(web.getContext() instanceof android.content.MutableContextWrapper)((android.content.MutableContextWrapper)web.getContext()).setBaseContext(activity);SourceSurface.attach(activity,web);}}
    /** Only the successful callback may transfer a loaded question to its continuation owner. */
    WebView takeSource() {
        if(replacement!=null)return replacement.takeSource();
        boolean virtualTieba=best!=null&&TiebaReplies.supports(item.source,best)&&DiscussionPages.samePage(Source.TIEBA,best.sourceUrl(),web.getUrl());
        if(!delivering||!ownsWeb||!AnswerStream.canReuse(item,web.getUrl())&&!virtualTieba)return null;
        ownsWeb=false;
        return web;
    }
    private void stopInspecting() {
        if(ended)return;
        ended=true;handler.removeCallbacksAndMessages(null);parser.shutdownNow();
    }
    private void expire(){
        if(ended)return;
        // A complete snapshot may already exist while a subsequent hydration/parse
        // consumes the remaining budget. Keep that actual acquisition, never extend
        // the deadline or revive a page from before navigation/account changes.
        if(best!=null&&!best.loginRequired&&!best.sourceUnavailable&&!navigation.pending()
                &&expectedSource(web.getUrl())&&session.current()&&topicCurrent()){
            finish(best);return;
        }
        fail("未能及时取得正文，可打开来源页确认后重试");
    }
    private void finish(Document doc) {
        if(ended)return;
        if(!session.stamp(doc)){
            // A source can set its first cookies while loading. Never relabel that old
            // DOM with the new session: release it and perform one fresh acquisition.
            if(retryChangedSession())return;
            fail("登录或缓存状态已变化，请重新读取");return;
        }
        stopInspecting();pendingResult=doc;deliver();
    }
    private boolean retryChangedSession(){
        if(ended||sessionRetries<=0||(!session.cookieChanged()&&topicCurrent())||android.os.SystemClock.elapsedRealtime()>=deadlineAt||owner==null||owner.isFinishing()||owner.isDestroyed())return false;
        // Initial cookies can arrive on a restricted HTTP response before a DOM
        // exists. Use the same single fresh acquisition and original time budget.
        stopInspecting();if(ownsWeb){ownsWeb=false;SourceSurface.release(web);}
        replacement=new DynamicReader(owner,item,callback,sessionRetries-1,explicitReading,deadlineAt,startedAt);return true;
    }
    private void fail(String reason) { if(ended)return;stopInspecting();pendingFailure=reason;if(ownsWeb){ownsWeb=false;SourceSurface.release(web);}deliver(); }
    private void deliver(){
        if(closed||callback==null||(pendingResult==null&&pendingFailure==null))return;
        Repository.Result<Document> result=callback;Document document=pendingResult;String reason=pendingFailure;
        callback=null;pendingResult=null;pendingFailure=null;delivering=true;
        try{if(!session.current()||!topicCurrent())result.failure("登录或缓存状态已变化，请重新读取");else if(document!=null)result.success(document);else result.failure(reason);}
        finally{delivering=false;close();}
    }
    public void close() {
        closed=true;callback=null;pendingResult=null;pendingFailure=null;owner=null;
        if(replacement!=null)replacement.close();
        stopInspecting();
        if(ownsWeb) { ownsWeb=false;SourceSurface.release(web); }
    }
}
