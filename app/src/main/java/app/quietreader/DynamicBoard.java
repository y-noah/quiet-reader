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
    public DynamicBoard(Activity activity,Source source,Repository.Result<List<Item>> callback) {
        this.source=source;this.callback=callback;web=new WebView(activity);
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setUserAgentString(Repository.DESKTOP_UA);
        CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !UrlPolicy.belongs(source,r.getUrl().toString());}
            @Override public void onPageFinished(WebView v,String url){if(tries==0&&!ended)inspect();}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();fail();}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail();}
        });
        handler.postDelayed(this::fail,30000);
        SourceSurface.attach(activity,web);
        web.loadUrl(source==Source.WEIBO?"https://s.weibo.com/top/summary":source.endpoint);
    }
    private void inspect(){
        if(ended)return;tries++;
        String js=source==Source.WEIBO||source==Source.HUPU||source==Source.HACKERNEWS||source==Source.SMZDM?"document.documentElement.outerHTML":"document.body.innerText";
        web.evaluateJavascript(js,encoded->{
            if(ended)return;
            try {Object raw=new JSONTokener(encoded).nextValue();if(raw instanceof String){List<Item> list=SourceParser.list(source,(String)raw);close();callback.success(list);return;}}catch(Exception ignored){}
            if(tries<12)handler.postDelayed(this::inspect,1500);else fail();
        });
    }
    private void fail(){if(ended)return;close();callback.failure("来源暂不可用，或需要先建立网页会话。可打开来源页完成访问或登录，返回后会自动重试。");}
    public void close(){if(ended)return;ended=true;handler.removeCallbacksAndMessages(null);SourceSurface.release(web);}
}
