package app.quietreader;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.webkit.*;
import android.widget.*;
import org.json.JSONTokener;
import static app.quietreader.Models.*;

/** Explicit source/login screen. No native JS bridge, no cookie export, no app intents. */
public final class LoginActivity extends Activity {
    private WebView web;
    private Source source;
    private TextView status;
    private boolean desktopMode=true,browse,mobileLayout=true;
    private android.view.View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private android.widget.FrameLayout fullscreenLayer;
    private String initialUrl="",pageIssue="";
    private Item requestedItem;
    private volatile int extraction;
    private volatile int navigation;
    private volatile boolean extractionClosed;
    private final java.util.concurrent.ThreadPoolExecutor extractor=new java.util.concurrent.ThreadPoolExecutor(
            1,1,0,java.util.concurrent.TimeUnit.MILLISECONDS,new java.util.concurrent.ArrayBlockingQueue<>(1),
            new java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy());
    private boolean positionedPage;
    private int[] restoredPosition;
    private SourceWindow sourceWindow;
    private final java.util.Set<String> upgradedUrls=new java.util.HashSet<>();
    public static Document pendingDocument;
    @Override public void onCreate(Bundle state) {
        Theme.apply(this);super.onCreate(state);
        BackNavigation.install(this,this::systemBack);
        try { source=Source.valueOf(getIntent().getStringExtra("source")); } catch(Exception e) { finish(); return; }
        if(!source.readable()){pendingDocument=null;finish();return;}
        browse=getIntent().getBooleanExtra("browse",false);
        mobileLayout=state==null||state.getBoolean("mobileLayout",true);
        final boolean adapted=browse&&SourceMobileLayout.supports(source);
        final boolean board=getIntent().getBooleanExtra("board",false);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Theme.background(this)); root.setFitsSystemWindows(true);
        LinearLayout bar=new LinearLayout(this);
        Button back=new Button(this); back.setText(board?"返回热榜":getIntent().getBooleanExtra("return_to_discovery",false)?"返回发现":"返回阅读"); back.setOnClickListener(v->returnToReader()); bar.addView(back);
        if(!board&&(!browse||getIntent().getBooleanExtra("reader_import",false))){Button read=new Button(this); read.setText("读取当前页"); read.setOnClickListener(v->extract()); bar.addView(read);}
        if(browse){Button signIn=new Button(this);signIn.setText("登录滑窗");signIn.setOnClickListener(v->{CookieManager.getInstance().flush();startActivityForResult(new Intent(this,LoginActivity.class).putExtra("source",source.name()).putExtra("url",UrlPolicy.belongs(source,web.getUrl())?web.getUrl():source.login).putExtra("board",true),200);});bar.addView(signIn);}
        desktopMode=state==null?SourceSession.desktop(this,source):state.getBoolean("desktop",SourceSession.desktop(this,source));
        Button desktop=new Button(this);
        if(adapted){
            desktop.setText(mobileLayout?"原网页":"手机排版");
            desktop.setOnClickListener(v->{mobileLayout=!mobileLayout;desktop.setText(mobileLayout?"原网页":"手机排版");web.getSettings().setLoadWithOverviewMode(!mobileLayout);web.evaluateJavascript(SourceMobileLayout.script(source,mobileLayout),null);});
        }else{
            desktop.setText(desktopMode?"手机版":"桌面版");
            desktop.setOnClickListener(v->{desktopMode=!desktopMode;SourceSession.setDesktop(this,source,desktopMode);desktop.setText(desktopMode?"手机版":"桌面版");web.getSettings().setUserAgentString(SourceSession.userAgent(this,source));reloadSource();});
        }
        bar.addView(desktop);
        for(int n=0;n<bar.getChildCount();n++){Button b=(Button)bar.getChildAt(n);b.setTextSize(14);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(4,0,4,0);b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(bar);
        Button centerWindow=new Button(this);centerWindow.setText("窗口居中");centerWindow.setOnClickListener(v->sourceWindow.center());
        if(!browse)root.addView(centerWindow,new LinearLayout.LayoutParams(-1,-2));
        status=new TextView(this); status.setText("来源页 · "+source.label+"｜登录仅保存在本机"); status.setPadding(16,8,16,8); root.addView(status);
        status.setMaxLines(2);status.setEllipsize(android.text.TextUtils.TruncateAt.END);
        status.setOnClickListener(v->new android.app.AlertDialog.Builder(this).setTitle("来源状态").setMessage(status.getText()).setPositiveButton("关闭",null).show());
        web=new WebView(this); Theme.configureWeb(web,true);
        if(browse)root.addView(web,new LinearLayout.LayoutParams(-1,0,1));else{sourceWindow=new SourceWindow(this,web);root.addView(sourceWindow,new LinearLayout.LayoutParams(-1,0,1));}setContentView(root);
        WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false); settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(desktopMode?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(this)); settings.setSupportMultipleWindows(false); settings.setMediaPlaybackRequiresUserGesture(true);
        web.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(android.view.View view,CustomViewCallback callback){
                if(!browse||fullscreen!=null){callback.onCustomViewHidden();return;}
                fullscreen=view;fullscreenCallback=callback;fullscreenLayer=new android.widget.FrameLayout(LoginActivity.this);fullscreenLayer.setBackgroundColor(android.graphics.Color.BLACK);
                fullscreenLayer.addView(view,new android.widget.FrameLayout.LayoutParams(-1,-1));
                Button exit=new Button(LoginActivity.this);exit.setText("退出全屏");exit.setOnClickListener(v->hideFullscreen());
                fullscreenLayer.addView(exit,new android.widget.FrameLayout.LayoutParams(-2,-2,android.view.Gravity.TOP|android.view.Gravity.END));addContentView(fullscreenLayer,new android.view.ViewGroup.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView(){hideFullscreen();}
        });
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,source==Source.WEIBO);
        if(!adapted)SourceSession.setDesktop(this,source,desktopMode);
        SourceSession.configure(this,source,web);
        if(adapted)settings.setUserAgentString(Repository.DESKTOP_UA);
        settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(browse&&(!adapted||!mobileLayout));
        settings.setSupportZoom(browse);settings.setBuiltInZoomControls(browse);settings.setDisplayZoomControls(false);
        if(state!=null&&state.containsKey("panX"))restoredPosition=new int[]{state.getInt("panX"),state.getInt("panY"),state.getInt("windowWidth"),state.getInt("windowHeight")};
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
                String url=req.getUrl().toString();
                String secure=UrlPolicy.upgradePlatformNavigation(source,url);
                if(req.isForMainFrame()&&!secure.isEmpty()) {
                    if(upgradedUrls.size()<4&&upgradedUrls.add(secure)){starting(secure);view.loadUrl(secure);}
                    else issue("来源反复跳转，已停止加载；可切换显示模式重试");
                    return true;
                }
                if(!(browse?UrlPolicy.https(url):UrlPolicy.loginAllowed(source,url))) { if(req.isForMainFrame())issue("已阻止外部跳转（"+UrlPolicy.host(url)+"）；请使用平台账号直接登录"); return true; }
                if(req.isForMainFrame())starting(url);
                return false;
            }
            // Chromium can deliver this AFTER a blocked redirect or HTTP error from
            // the same document. Only an explicitly accepted navigation may clear it.
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon) {navigation++;positionedPage=false;if(pageIssue.isEmpty())status.setText("正在加载来源页 · "+UrlPolicy.host(url));}
            @Override public void onPageCommitVisible(WebView view,String url){if(adapted&&url.equals(view.getUrl()))view.evaluateJavascript(SourceMobileLayout.script(source,mobileLayout),null);}
            @Override public void onPageFinished(WebView view,String url) {
                if(adapted&&url.equals(view.getUrl()))view.evaluateJavascript(SourceMobileLayout.script(source,mobileLayout),null);
                CookieManager.getInstance().flush();
                if(!pageIssue.isEmpty())return;
                if(view.getUrl()==null){issue("来源未返回可显示页面；可切换显示模式重新加载");return;}
                if(!url.equals(view.getUrl()))return; // A previous redirect may finish after the new request starts.
                if(!browse&&!positionedPage){
                    positionedPage=true;int revision=navigation;
                    sourceWindow.post(()->{
                        if(isFinishing()||isDestroyed()||revision!=navigation||!url.equals(web.getUrl()))return;
                        if(restoredPosition!=null){sourceWindow.restorePosition(restoredPosition[0]+(restoredPosition[2]-sourceWindow.getWidth())/2,restoredPosition[1]+(restoredPosition[3]-sourceWindow.getHeight())/2);restoredPosition=null;}else sourceWindow.center();
                    });
                }
                status.setText(browse?"来源网页 · "+UrlPolicy.host(url):"滑窗 · 上下左右拖动｜"+UrlPolicy.host(url)+(board?"｜完成后返回热榜":"｜完成后返回阅读；读取当前页可导入眼前内容"));
            }
            @Override public void onReceivedSslError(WebView view,android.webkit.SslErrorHandler handler,android.net.http.SslError err) { handler.cancel(); issue("连接证书异常，已停止加载"); }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())issue("来源页连接失败："+e.getErrorCode()+"。可切换手机版/桌面版后重试");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame())issue("来源页返回 HTTP "+response.getStatusCode()+"；可稍后重试，登录不一定能解决");}
        });
        web.setDownloadListener((u,a,c,m,n)->status.setText("下载已拦截；New 不要求安装来源 App"));
        String url=getIntent().getStringExtra("url");
        initialUrl=UrlPolicy.loginAllowed(source,url)?url:source.login;
        try{Item item=Item.from(new org.json.JSONObject(getIntent().getStringExtra("reader_item")));if(item.source==source&&initialUrl.equals(item.url))requestedItem=item;}catch(Exception ignored){}
        if(state==null||web.restoreState(state)==null)web.loadUrl(initialUrl);
        else if(adapted&&mobileLayout&&web.getUrl()!=null){String restored=web.getUrl(),recovered=SourceMobileLayout.recoveryUrl(source,restored);if(!restored.equals(recovered))web.loadUrl(recovered);}
    }
    private void issue(String message){pageIssue=message;status.setText(message);}
    private void starting(String url){extraction++;pageIssue="";status.setText("正在加载来源页 · "+UrlPolicy.host(url));}
    private void reloadSource(){
        upgradedUrls.clear();
        if(browse?UrlPolicy.https(web.getUrl()):UrlPolicy.loginAllowed(source,web.getUrl())){String current=web.getUrl(),target=browse&&mobileLayout?SourceMobileLayout.recoveryUrl(source,current):current;starting(target);if(current.equals(target))web.reload();else web.loadUrl(target);}
        else {starting(initialUrl);web.loadUrl(initialUrl);}
    }
    private void returnToReader(){
        CookieManager.getInstance().flush();
        finish();
    }
    private void extract() {
        String url=web.getUrl();
        if(!UrlPolicy.belongs(source,url)) { status.setText("仅允许读取当前平台内容"); return; }
        extract(url,++extraction,0);
    }
    private void extract(String url,int request,int attempt) {
        if(isFinishing()||isDestroyed()||request!=extraction||!url.equals(web.getUrl()))return;
        // Capture before JS runs. A navigation, login change or invalidation must
        // never stamp an earlier DOM as if it came from the replacement session.
        int revision=navigation;
        ArticleSession session=requestedItem!=null&&url.equals(requestedItem.url)?new ArticleSession(this,requestedItem):new ArticleSession(this,url);
        web.evaluateJavascript(SourceSnapshot.script(source),encoded->{
            if(!currentExtraction(url,request,revision))return;
            // Large snapshots must not decode/parse on the UI thread. Keep at most
            // one queued request: repeated taps replace obsolete work, not grow RAM.
            extractor.execute(()->parseSnapshot(encoded,url,request,attempt,revision,session));
        });
    }
    private boolean currentExtraction(String url,int request,int revision){
        return !extractionClosed&&!isFinishing()&&!isDestroyed()&&request==extraction&&revision==navigation&&web!=null&&url.equals(web.getUrl());
    }
    private void parseSnapshot(String encoded,String url,int request,int attempt,int revision,ArticleSession session){
        if(extractionClosed||request!=extraction||revision!=navigation)return;
        Document parsed=null;boolean expanding=false;String message="";
        try {
                Object value=new JSONTokener(encoded).nextValue();
                if(!(value instanceof String))throw new IllegalStateException("No snapshot");
                org.json.JSONObject snapshot=new org.json.JSONObject((String)value);
                String actual=snapshot.optString("url");
                if(url.equals(actual)&&snapshot.optBoolean("expanding")){
                    expanding=true;
                }else if(!url.equals(actual)||snapshot.isNull("html")||snapshot.optString("html").length()>4*1024*1024){
                    message="页面已跳转、过大或尚未加载";
                }else{
                    parsed=SourceParser.readingArticle(source,snapshot.getString("html"),actual);
                }
        }catch(Exception invalid){message="读取失败，请等待页面加载后重试";}
        if(extractionClosed||request!=extraction||revision!=navigation)return;
        Document doc=parsed;boolean needsExpansion=expanding;String issue=message;
        runOnUiThread(()->{
                if(!currentExtraction(url,request,revision))return;
                if(needsExpansion){
                    if(attempt>=8){status.setText("来源正文仍未展开，请在网页确认后重试");return;}
                    status.setText("正在展开来源正文…");
                    web.postDelayed(()->extract(url,request,attempt+1),1000);return;
                }
                if(doc==null){status.setText(issue);return;}
                if(!doc.canPresent()||doc.loginRequired||doc.sourceUnavailable) { status.setText(doc.notice); return; }
                if(!session.stamp(doc)){status.setText("登录或缓存状态已变化，请重新读取当前页");return;}
                pendingDocument=doc;
                setResult(RESULT_OK,new Intent().putExtra("source",source.name())); finish();
        });
    }
    private void hideFullscreen(){if(fullscreen==null)return;((android.view.ViewGroup)fullscreenLayer.getParent()).removeView(fullscreenLayer);fullscreen=null;fullscreenLayer=null;WebChromeClient.CustomViewCallback c=fullscreenCallback;fullscreenCallback=null;if(c!=null)c.onCustomViewHidden();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==200&&web!=null){CookieManager.getInstance().flush();reloadSource();}}
    private void systemBack(){if(fullscreen!=null){hideFullscreen();return;}if(web!=null&&web.canGoBack()){pageIssue="";upgradedUrls.clear();status.setText("正在返回上一来源页…");web.goBack();}else finish();}
    @Override public void onBackPressed(){systemBack();}
    @Override protected void onSaveInstanceState(Bundle state) {state.putBoolean("mobileLayout",mobileLayout);state.putBoolean("desktop",desktopMode);if(sourceWindow!=null){state.putInt("panX",sourceWindow.panX());state.putInt("panY",sourceWindow.panY());state.putInt("windowWidth",sourceWindow.getWidth());state.putInt("windowHeight",sourceWindow.getHeight());}if(web!=null)web.saveState(state);super.onSaveInstanceState(state);}
    @Override protected void onPause() { extraction++;extractor.getQueue().clear();if(web!=null) { SourceSession.preferWeb(this,source);web.onPause(); CookieManager.getInstance().flush(); } super.onPause(); }
    @Override protected void onResume() { super.onResume(); if(web!=null) web.onResume(); }
    @Override protected void onDestroy() { extractionClosed=true;extraction++;navigation++;extractor.shutdownNow();hideFullscreen();if(web!=null) {SourceSurface.release(web);web=null;} super.onDestroy(); }
}
