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
    private final Repository.Result<Document> callback;
    private boolean ended=false;
    private boolean delivering=false,ownsWeb=true;
    private int attempts=0;
    private Document best;
    private String lastSnapshot="";
    private final ReadNavigation navigation=new ReadNavigation();
    private final java.util.Set<String> upgradedUrls=new java.util.HashSet<>();
    private final java.util.concurrent.ExecutorService parser=java.util.concurrent.Executors.newSingleThreadExecutor();
    public DynamicReader(Activity activity,Item item,Repository.Result<Document> cb) {
        this.item=item; callback=cb; web=new WebView(activity);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false);
        s.setUserAgentString(item.source==Source.WEIBO?WebSettings.getDefaultUserAgent(activity):Repository.DESKTOP_UA); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setMediaPlaybackRequiresUserGesture(true);
        // This surface only extracts text and image URLs; the own reader loads actual pictures.
        // Do not apply this to the explicit login view, where image challenges must stay visible.
        s.setBlockNetworkImage(true);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
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
        });
        web.setDownloadListener((u,a,c,m,n)->{});
        SourceSurface.attach(activity,web);
        handler.postDelayed(()->fail("未能加载完整页面，可登录来源后重试"),22000);
        // Start after construction so the owner is assigned before navigation callbacks.
        // Closing in the same UI turn cancels this pending navigation too.
        handler.post(()->{if(!ended)web.loadUrl(item.url);});
    }
    private void navigating(String url){navigation.begin(url);best=null;lastSnapshot="";}
    private void ready(WebView view,String url){
        if(!ended&&navigation.ready(url,view.getUrl())&&attempts==0)inspect();
    }
    private void inspect() {
        if(ended)return;
        if(navigation.pending()){handler.postDelayed(this::inspect,1200);return;}
        attempts++;
        if(!expectedSource(web.getUrl())) { fail("来源跳转到登录、其他问题或不支持的页面，请重新读取"); return; }
        final long revision=navigation.revision();
        web.evaluateJavascript("(function(){var html=document.documentElement.outerHTML;return JSON.stringify({url:location.href,html:html.length<4194304?html:null});})()",raw->{
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
                            parsed=SourceParser.article(item.source,html,payload.getString("url"));
                            // Resolve relative links against the loaded document while retaining
                            // the requested reading/cache identity already validated above.
                            parsed.url=item.url;
                        }
                    }
                }
            } catch(Exception ignored) {}
            Document doc=parsed;boolean wrongSource=wrongPage;
            String snapshot=doc==null?"":DocumentFingerprint.of(doc);
            handler.post(()->{
                if(ended)return;
                if(!navigation.current(revision)){lastSnapshot="";handler.postDelayed(this::inspect,1200);return;}
                if(wrongSource||!expectedSource(web.getUrl())) { fail("来源跳转到登录、其他问题或不支持的页面，请重新读取");return; }
                if(doc!=null){
                    if(doc.canPresent()) {
                        boolean stable=snapshot.equals(lastSnapshot);
                        best=doc;
                        if(attempts>=2&&stable) { finish(doc); return; }
                    }
                }
                // An empty/failed snapshot breaks consecutiveness too.
                lastSnapshot=snapshot;
            if(attempts>=14) { if(best!=null)finish(best); else fail("页面未提供可提取正文，请登录后重试"); }
            else handler.postDelayed(this::inspect,1200);
            });
            });
        });
    }
    private boolean expectedSource(String url) {
        if(!UrlPolicy.belongs(item.source,url))return false;
        if(UrlPolicy.sameForumPost(item.source,item.url,item.url))
            return UrlPolicy.sameForumPost(item.source,item.url,url);
        if(item.source==Source.WEIBO&&!WeiboPost.id(item.url).isEmpty())return WeiboPost.same(item.url,url);
        return !AnswerStream.sameQuestion(item,item.url)||AnswerStream.sameQuestion(item,url);
    }
    /** Only the successful callback may transfer a loaded question to its continuation owner. */
    WebView takeSource() {
        if(!delivering||!ownsWeb||!AnswerStream.canReuse(item,web.getUrl()))return null;
        ownsWeb=false;
        return web;
    }
    private void stopInspecting() {
        if(ended)return;
        ended=true;handler.removeCallbacksAndMessages(null);parser.shutdownNow();
    }
    private void finish(Document doc) {
        if(ended)return;
        stopInspecting();delivering=true;
        try { callback.success(doc); }
        finally { delivering=false;close(); }
    }
    private void fail(String reason) { if(ended)return; close(); callback.failure(reason); }
    public void close() {
        stopInspecting();
        if(ownsWeb) { ownsWeb=false;SourceSurface.release(web); }
    }
}
