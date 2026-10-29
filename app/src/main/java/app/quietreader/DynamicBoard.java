package app.quietreader;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.webkit.*;
import org.json.JSONTokener;
import java.util.List;
import static app.quietreader.Models.*;

/** Normal browser fallback when an anonymous HTTP fetch cannot obtain the board. */
public final class DynamicBoard {
    private final WebView web;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Source source;
    private final Repository.Result<List<Item>> callback;
    private int tries=0;
    private boolean ended=false;
    private final java.util.concurrent.ExecutorService parser=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.Set<String> upgraded=new java.util.HashSet<>();
    public DynamicBoard(Activity activity,Source source,Repository.Result<List<Item>> callback) {
        this.source=source;this.callback=callback;web=new WebView(activity);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setUserAgentString(Repository.DESKTOP_UA);
        SourceSession.configure(activity,source,web);
        // A board fallback follows an explicit refresh or an expired snapshot. Preserve cookies/storage.
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setBlockNetworkImage(true);s.setMediaPlaybackRequiresUserGesture(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String path=request.getUrl().getPath();if(!request.isForMainFrame()&&path!=null&&path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(mp4|m4s|webm|m3u8|mp3|m4a|aac|ts)$"))return new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0]));return null;}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String url=r.getUrl().toString(),secure=UrlPolicy.upgradePlatformNavigation(source,url);if(r.isForMainFrame()&&!secure.isEmpty()){if(upgraded.size()<4&&upgraded.add(secure))v.loadUrl(secure);return true;}return !UrlPolicy.loginAllowed(source,url);}
            // The list may be usable while an unrelated async resource still keeps
            // page-finished pending. Begin the bounded polling loop at first paint.
            @Override public void onPageCommitVisible(WebView v,String url){if(tries==0&&!ended)inspect();}
            @Override public void onPageFinished(WebView v,String url){if(tries==0&&!ended)inspect();}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();fail("来源网页安全连接失败，请稍后重试");}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail("来源网页加载失败，请检查网络后重试");}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){
                if(!request.isForMainFrame()||ended||response.getStatusCode()<400)return;
                int status=response.getStatusCode();long retryAt=0;
                if(response.getResponseHeaders()!=null)for(java.util.Map.Entry<String,String> entry:response.getResponseHeaders().entrySet())if("Retry-After".equalsIgnoreCase(entry.getKey()))retryAt=app.quietreader.discovery.RefreshBackoff.retryAfter(entry.getValue(),System.currentTimeMillis());
                close();callback.failure(status==429?"平台限制了刷新频率，请稍后重试":status==401||status==403?"来源要求登录或限制了访问，请打开来源页确认":"来源暂时返回错误",status,retryAt);
            }
        });
        handler.postDelayed(this::fail,30000);
        SourceSurface.attach(activity,web);
        if(SourceSession.desktop(activity,source)){android.view.ViewGroup.LayoutParams size=web.getLayoutParams();size.width=Math.round(1280*activity.getResources().getDisplayMetrics().density);size.height=Math.round(960*activity.getResources().getDisplayMetrics().density);web.setLayoutParams(size);web.getSettings().setUseWideViewPort(false);web.getSettings().setLoadWithOverviewMode(false); }
        web.loadUrl(jsonBoard()?source.endpoint:source.boardPage());
    }
    private void inspect(){
        if(ended)return;tries++;
        String js=jsonBoard()?"document.body.innerText.slice(0,4000000)":"document.documentElement.outerHTML.slice(0,4000000)";
        web.evaluateJavascript(js,encoded->{
            if(ended)return;
            parser.execute(()->{
                List<Item> list=null;
                try{Object raw=new JSONTokener(encoded).nextValue();if(raw instanceof String&&((String)raw).length()<4_000_000)list=SourceParser.list(source,(String)raw);}catch(Exception ignored){}
                List<Item> result=list;handler.post(()->{
                    if(ended)return;
                    if(result!=null&&!result.isEmpty()){close();callback.success(result);return;}
                    // The existing 30-second owner deadline bounds all attempts. A second
                    // attempt-count cutoff could abandon a slowly hydrated page early.
                    handler.postDelayed(this::inspect,1500);
                });
            });
        });
    }
    private void fail(){fail(tries==0?"来源网页加载超时，请检查网络或打开来源页确认":"来源网页已加载，但尚未读到热门列表。可打开来源页确认内容或完成登录，返回后重试。");}
    private void fail(String reason){if(ended)return;close();callback.failure(reason);}
    private boolean jsonBoard(){return source==Source.ZHIHU||source==Source.CLS||source==Source.TIEBA;}
    public void close(){if(ended)return;ended=true;handler.removeCallbacksAndMessages(null);parser.shutdownNow();SourceSurface.release(web);}
}
