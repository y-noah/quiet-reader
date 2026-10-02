package app.quietreader;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import org.json.JSONTokener;
import static app.quietreader.Models.*;

/** Explicit source/login screen. No native JS bridge, no cookie export, no app intents. */
public final class LoginActivity extends Activity {
    private WebView web;
    private Source source;
    private TextView status;
    private boolean desktopMode=true;
    private String initialUrl="",pageIssue="";
    private final java.util.Set<String> upgradedUrls=new java.util.HashSet<>();
    public static Document pendingDocument;
    @Override public void onCreate(Bundle state) {
        Theme.apply(this);super.onCreate(state);
        try { source=Source.valueOf(getIntent().getStringExtra("source")); } catch(Exception e) { finish(); return; }
        final boolean board=getIntent().getBooleanExtra("board",false);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Theme.background(this)); root.setFitsSystemWindows(true);
        LinearLayout bar=new LinearLayout(this);
        Button back=new Button(this); back.setText("返回"); back.setOnClickListener(v->finish()); bar.addView(back);
        Button read=new Button(this); read.setText(board?"返回热榜":"读取到静读"); read.setOnClickListener(v->{if(board)finish();else extract();}); bar.addView(read);
        desktopMode=state==null?source!=Source.WEIBO:state.getBoolean("desktop",source!=Source.WEIBO);
        Button desktop=new Button(this); desktop.setText(desktopMode?"手机版":"桌面版"); desktop.setOnClickListener(v->{ desktopMode=!desktopMode;desktop.setText(desktopMode?"手机版":"桌面版");web.getSettings().setUserAgentString(desktopMode?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(this)); reloadSource(); }); bar.addView(desktop);
        root.addView(bar);
        status=new TextView(this); status.setText("来源页 · "+source.label+"｜登录仅保存在本机"); status.setPadding(16,8,16,8); root.addView(status);
        status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("来源状态").setMessage(status.getText()).setPositiveButton("关闭",null).show());
        web=new WebView(this); Theme.configureWeb(web,true); root.addView(web,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(desktopMode?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(this)); settings.setSupportMultipleWindows(false); settings.setMediaPlaybackRequiresUserGesture(true);
        web.setWebChromeClient(new WebChromeClient());
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,source==Source.WEIBO);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
                String url=req.getUrl().toString();
                String secure=UrlPolicy.upgradePlatformNavigation(source,url);
                if(req.isForMainFrame()&&!secure.isEmpty()) {
                    if(upgradedUrls.size()<4&&upgradedUrls.add(secure)){starting(secure);view.loadUrl(secure);}
                    else issue("来源反复跳转，已停止加载；可切换显示模式重试");
                    return true;
                }
                if(!UrlPolicy.loginAllowed(source,url)) { if(req.isForMainFrame())issue("已阻止外部跳转（"+UrlPolicy.host(url)+"）；请使用平台账号直接登录"); return true; }
                if(req.isForMainFrame())starting(url);
                return false;
            }
            // Chromium can deliver this AFTER a blocked redirect or HTTP error from
            // the same document. Only an explicitly accepted navigation may clear it.
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {if(pageIssue.isEmpty())status.setText("正在加载来源页 · "+UrlPolicy.host(url));}
            @Override public void onPageFinished(WebView view,String url) {
                CookieManager.getInstance().flush();
                if(!pageIssue.isEmpty())return;
                if(view.getUrl()==null){issue("来源未返回可显示页面；可切换显示模式重新加载");return;}
                if(!url.equals(view.getUrl()))return; // A previous redirect may finish after the new request starts.
                status.setText("来源页 · "+UrlPolicy.host(url)+(board?"｜完成访问或登录后点「返回热榜」":"｜完成登录或展开后点「读取到静读」"));
            }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError err) { handler.cancel(); issue("连接证书异常，已停止加载"); }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())issue("来源页连接失败："+e.getErrorCode()+"。可切换手机版/桌面版后重试");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame())issue("来源页返回 HTTP "+response.getStatusCode()+"；可稍后重试，登录不一定能解决");}
        });
        web.setDownloadListener((u,a,c,m,n)->status.setText("下载已拦截；静读不要求安装来源 App"));
        String url=getIntent().getStringExtra("url");
        initialUrl=UrlPolicy.loginAllowed(source,url)?url:source.login;
        if(state==null||web.restoreState(state)==null)web.loadUrl(initialUrl);
    }
    private void issue(String message){pageIssue=message;status.setText(message);}
    private void starting(String url){pageIssue="";status.setText("正在加载来源页 · "+UrlPolicy.host(url));}
    private void reloadSource(){
        upgradedUrls.clear();
        if(UrlPolicy.loginAllowed(source,web.getUrl())){starting(web.getUrl());web.reload();}
        else {starting(initialUrl);web.loadUrl(initialUrl);}
    }
    private void extract() {
        String url=web.getUrl();
        if(!UrlPolicy.belongs(source,url)) { status.setText("仅允许读取当前平台内容"); return; }
        web.evaluateJavascript("document.documentElement.outerHTML",encoded->{
            try {
                Object value=new JSONTokener(encoded).nextValue();
                if(!(value instanceof String)||((String)value).length()>4*1024*1024) { status.setText("页面过大或尚未加载"); return; }
                Document doc=SourceParser.article(source,(String)value,url);
                if(!doc.canPresent()) { status.setText(doc.notice); return; }
                pendingDocument=doc;
                setResult(RESULT_OK,new Intent().putExtra("source",source.name())); finish();
            } catch(Exception e) { status.setText("读取失败，请等待页面加载后重试"); }
        });
    }
    @Override public void onBackPressed() { if(web!=null&&web.canGoBack()) {pageIssue="";upgradedUrls.clear();status.setText("正在返回上一来源页…");web.goBack();} else super.onBackPressed(); }
    @Override protected void onSaveInstanceState(Bundle state) {state.putBoolean("desktop",desktopMode);if(web!=null)web.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onPause() { if(web!=null) { web.onPause(); CookieManager.getInstance().flush(); } super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web!=null) web.onResume(); }
    @Override protected void onDestroy() { if(web!=null) { web.stopLoading(); web.destroy(); } super.onDestroy(); }
}
