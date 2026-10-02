package app.quietreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.util.Collections;

/** One-image, script-free preview. Each retry owns a new WebView; old callbacks are ignored. */
final class ImagePreview {
    interface Loader { WebResourceResponse load() throws Exception; }
    private static final String PAGE="https://quiet-reader.invalid/image-preview";
    private final Activity activity;
    private final String url;
    private final boolean dark;
    private final Loader loader;
    private final Runnable invalidate;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LinearLayout layout;
    private final TextView status;
    private final ProgressBar progress;
    private final AlertDialog dialog;
    private volatile WebView current;
    private volatile boolean closed;
    private boolean settled,checking;
    private Runnable slow,inspectionTimeout,navigation;

    ImagePreview(Activity activity,String url,boolean dark,Loader loader,Runnable invalidate){
        this.activity=activity;this.url=url;this.dark=dark;this.loader=loader;this.invalidate=invalidate;
        int padding=(int)(16*activity.getResources().getDisplayMetrics().density);
        layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(dark?0xff1f2025:0xfffafafa);
        status=new TextView(activity);status.setTextSize(14);status.setTextColor(dark?0xff9b9da7:0xff696c76);
        status.setPadding(padding,padding,padding,padding);status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);layout.addView(status);
        progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);layout.addView(progress);
        dialog=new AlertDialog.Builder(activity).setTitle("正文图片 · 双指缩放").setView(layout).setNegativeButton("重试",null).setPositiveButton("关闭",null).create();
        dialog.setOnDismissListener(ignored->{closed=true;release();});
    }
    void show(){dialog.show();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{if(closed)return;invalidate.run();start();});start();}
    void dismiss(){dialog.dismiss();}
    private boolean active(WebView web){return !closed&&web==current;}
    private void release(){
        if(slow!=null)handler.removeCallbacks(slow);
        if(inspectionTimeout!=null)handler.removeCallbacks(inspectionTimeout);
        if(navigation!=null)handler.removeCallbacks(navigation);
        WebView old=current;current=null;checking=false;
        if(old!=null){old.getSettings().setJavaScriptEnabled(false);old.stopLoading();layout.removeView(old);old.destroy();}
    }
    private void finish(WebView web,boolean ready,String message){
        if(!active(web)||settled)return;
        settled=true;checking=false;web.getSettings().setJavaScriptEnabled(false);
        if(slow!=null)handler.removeCallbacks(slow);
        if(inspectionTimeout!=null)handler.removeCallbacks(inspectionTimeout);
        progress.setVisibility(View.GONE);status.setText(message);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
    }
    private void start(){
        if(closed)return;release();settled=false;
        status.setText("正在加载图片…");progress.setVisibility(View.VISIBLE);dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
        WebView web=new WebView(activity);current=web;
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        Theme.configureWeb(web,false);
        if(android.os.Build.VERSION.SDK_INT<=27)web.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        // Limit the preview to the available window; large images remain scrollable/zoomable.
        int height=(int)(activity.getResources().getDisplayMetrics().heightPixels*.55);
        layout.addView(web,new LinearLayout.LayoutParams(-1,height));
        final String html="<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><style>body{margin:0;background:"+(dark?"#1f2025;color:#cdcfd5":"#fafafa;color:#30323a")+"}img{width:100%;height:auto}</style></head><body><img src='"+ReaderHtml.escape(url)+"' alt='图片暂不可用'></body></html>";
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){
                if(!active(w))return unavailable();
                String requested=request.getUrl().toString();
                if(request.isForMainFrame()&&PAGE.equals(requested))return new WebResourceResponse("text/html","UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(!url.equals(requested))return unavailable();
                try{WebResourceResponse response=loader.load();if(response==null)throw new java.io.IOException("Missing image response");return response;}
                catch(Exception failure){handler.post(()->finish(w,false,"图片加载失败，请点「重试」；也可以关闭后继续阅读。"));return unavailable();}
            }
            @Override public void onReceivedError(WebView w,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame()||url.equals(request.getUrl().toString()))finish(w,false,"图片加载失败，请点「重试」；也可以关闭后继续阅读。");
            }
            @Override public void onReceivedHttpError(WebView w,WebResourceRequest request,WebResourceResponse response){
                if(request.isForMainFrame()||url.equals(request.getUrl().toString()))finish(w,false,"图片加载失败，请点「重试」；也可以关闭后继续阅读。");
            }
            @Override public void onPageFinished(WebView w,String page){
                if(!active(w)||settled||checking||!PAGE.equals(w.getUrl()))return;
                checking=true;
                // Fixed read-only inspection of our escaped one-image document, never a source page.
                // CSP forbids page scripts and navigation; scripting is off before status is changed.
                inspectionTimeout=()->finish(w,false,"图片尚未显示，请点「重试」。");handler.postDelayed(inspectionTimeout,3000);
                w.getSettings().setJavaScriptEnabled(true);
                w.evaluateJavascript("(document.images.length===1&&document.images[0].complete&&document.images[0].naturalWidth>0&&document.images[0].naturalHeight>0)?'ready':'unavailable'",result->{
                    if(!active(w)||!checking)return;
                    boolean ready="\"ready\"".equals(result);finish(w,ready,ready?"图片已显示 · 双指缩放查看细节":"图片未能显示，请点「重试」。");
                });
            }
        });
        slow=()->{if(active(web)){status.setText("图片加载较慢，可以重试或关闭后继续阅读。");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);}};
        handler.postDelayed(slow,15000);
        // Serve the exact own document like the main reader. Do not rely on a data-document
        // base URL becoming the WebView's actual committed URL. Mount before navigation.
        navigation=()->{if(active(web))web.loadUrl(PAGE);};handler.post(navigation);
    }
    private static WebResourceResponse unavailable(){return new WebResourceResponse("text/plain","utf-8",404,"Unavailable",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
}
