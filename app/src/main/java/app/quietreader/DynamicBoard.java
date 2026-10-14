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
    private final java.util.Set<String> upgraded=new java.util.HashSet<>();
    public DynamicBoard(Activity activity,Source source,Repository.Result<List<Item>> callback) {
        this.source=source;this.callback=callback;web=new WebView(activity);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setUserAgentString(Repository.DESKTOP_UA);
        SourceSession.configure(activity,source,web);
        // A board fallback follows an explicit refresh or an expired snapshot. Preserve cookies/storage.
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){String url=r.getUrl().toString(),secure=UrlPolicy.upgradePlatformNavigation(source,url);if(r.isForMainFrame()&&!secure.isEmpty()){if(upgraded.size()<4&&upgraded.add(secure))v.loadUrl(secure);return true;}return !UrlPolicy.loginAllowed(source,url);}
            @Override public void onPageFinished(WebView v,String url){if(tries==0&&!ended)inspect();}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();fail();}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail();}
        });
        handler.postDelayed(this::fail,30000);
        SourceSurface.attach(activity,web);
        if(SourceSession.desktop(activity,source)){android.view.ViewGroup.LayoutParams size=web.getLayoutParams();size.width=Math.round(1280*activity.getResources().getDisplayMetrics().density);size.height=Math.round(960*activity.getResources().getDisplayMetrics().density);web.setLayoutParams(size);web.getSettings().setUseWideViewPort(false);web.getSettings().setLoadWithOverviewMode(false); }
        web.loadUrl(source==Source.ZHIHU||source==Source.CLS?source.endpoint:source.boardPage());
    }
    private void inspect(){
        if(ended)return;tries++;
        String js=source==Source.ZHIHU||source==Source.CLS?"document.body.innerText":"document.documentElement.outerHTML";
        web.evaluateJavascript(js,encoded->{
            if(ended)return;
            try {Object raw=new JSONTokener(encoded).nextValue();if(raw instanceof String){List<Item> list=SourceParser.list(source,(String)raw);close();callback.success(list);return;}}catch(Exception ignored){}
            if(tries<12)handler.postDelayed(this::inspect,1500);else fail();
        });
    }
    private void fail(){if(ended)return;close();callback.failure("来源暂不可用，或需要先建立网页会话。可打开来源页完成访问或登录，返回后会自动重试。");}
    public void close(){if(ended)return;ended=true;handler.removeCallbacksAndMessages(null);SourceSurface.release(web);}
}
