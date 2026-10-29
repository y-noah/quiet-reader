package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Synthetic authenticated HTML through real foreground HTTP and WebView fallback. */
final class HupuNativeRoutingProbe {
    private final Instrumentation test;
    private MainActivity owner;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures;
    private volatile String target,html;
    private volatile boolean forbidden,cookieSent,released;
    private String stage="initial";
    private final AtomicInteger nativeRequests=new AtomicInteger();
    HupuNativeRoutingProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void cookie(boolean present)throws Exception{CountDownLatch done=new CountDownLatch(1);ui(()->CookieManager.getInstance().setCookie(target,"qr_hupu_route="+(present?"synthetic-account":"")+"; Path=/123.html; Secure"+(present?"":"; Max-Age=0"),ignored->done.countDown()));if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic cookie callback missing");}
    private void check(boolean good,String message){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(message).append('\n');}
    private Object field(Object object,String name){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}catch(Exception e){throw new IllegalStateException(e);}}
    private void await(BooleanSupplier condition)throws Exception{long deadline=SystemClock.elapsedRealtime()+12000;while(true){boolean[] good={false};ui(()->good[0]=condition.getAsBoolean());if(good[0])return;if(SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("Hupu route deadline");Thread.sleep(30);}}
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
        if(!url.toString().equals(target))throw new IOException("Unexpected synthetic URL");
        return new HttpURLConnection(url){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}
            public int getResponseCode()throws IOException{nativeRequests.incrementAndGet();String cookie=getRequestProperty("Cookie");cookieSent=cookie!=null&&cookie.contains("qr_hupu_route=synthetic-account");long end=SystemClock.elapsedRealtime()+6000;while(!released){if(SystemClock.elapsedRealtime()>end)throw new IOException("Controlled HTTP release deadline");try{Thread.sleep(10);}catch(InterruptedException interrupted){throw new IOException(interrupted);}}return forbidden?403:200;}
            public long getContentLengthLong(){return html.getBytes(StandardCharsets.UTF_8).length;}
            public InputStream getInputStream(){return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));}
        };
    }});}
    void run(){
        android.content.SharedPreferences prefs=test.getTargetContext().getSharedPreferences("source-session",0);
        boolean existed=prefs.contains("HUPU-web"),old=prefs.getBoolean("HUPU-web",false);
        try{
            transport();String readable=new LoginReadFlowProbe(test).fixture(Source.HUPU,"受控虎扑登录正文");
            String restricted=readable.replace("\"puid\":\"789\"","\"puid\":\"0\"");
            for(String scenario:new String[]{"native","login-shell","http-error","still-restricted"}){
                target="https://bbs.hupu.com/123.html?qr_route="+scenario+System.nanoTime();
                Item item=new Item(Source.HUPU,"受控路由",target,"");forbidden=scenario.equals("http-error");html=scenario.equals("native")?readable:restricted;cookieSent=false;released=false;nativeRequests.set(0);
                cookie(true);ui(()->prefs.edit().putBoolean("HUPU-web",true).commit());
                owner=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                AtomicInteger browsers=new AtomicInteger();
                ui(()->((android.view.ViewGroup)owner.findViewById(android.R.id.content)).setOnHierarchyChangeListener(new android.view.ViewGroup.OnHierarchyChangeListener(){
                    public void onChildViewRemoved(android.view.View parent,android.view.View child){}
                    public void onChildViewAdded(android.view.View parent,android.view.View child){if(!(child instanceof WebView))return;
                        browsers.incrementAndGet();WebView web=(WebView)child;WebViewClient delegate=web.getWebViewClient();String body=scenario.equals("still-restricted")?restricted:readable;
                        // Install before DynamicReader posts its FIRST navigation;
                        // stopping a live navigation creates stale ready callbacks.
                        web.setWebViewClient(new WebViewClient(){public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));}public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}});
                    }
                }));released=true;
                stage=scenario+" waiting reading";await(()->field(owner,"reading")!=null);
                ui(()->{
                    Document document=(Document)field(owner,"reading");Repository repo=(Repository)field(owner,"repo");
                    check(nativeRequests.get()==1&&cookieSent,scenario+": one native request carries the shared synthetic session despite past web preference");
                    check(browsers.get()==(scenario.equals("native")?0:1),scenario+": browser is created exactly when native response needs fallback");
                    if(scenario.equals("still-restricted"))check(document.loginRequired&&repo.cachedArticle(item)==null,scenario+": browser permission gate remains restricted and uncached");
                    else{check(!document.loginRequired&&document.sections.size()==2,scenario+": authenticated main and comment reach actual reader");check(document.cachedSession.equals(ArticleDiskCache.session(item.url))&&repo.cachedArticle(item)==document,scenario+": returned body keeps its real acquisition session");}
                    if(scenario.equals("native"))check(field(owner,"dynamic")==null,"native: no JavaScript acquisition is created");
                    owner.finish();
                });test.waitForIdleSync();cookie(false);owner=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(stage).append(' ').append(error).append('\n');ui(()->{if(owner!=null){Object dynamic=field(owner,"dynamic");report.append("DIAGNOSTIC nativeRequests=").append(nativeRequests.get()).append(" reading=").append(field(owner,"reading")!=null).append(" dynamic=").append(dynamic!=null);if(dynamic!=null)report.append(" ended=").append(field(dynamic,"ended")).append(" attempts=").append(field(dynamic,"attempts")).append(" replacement=").append(field(dynamic,"replacement")!=null);android.view.ViewGroup content=(android.view.ViewGroup)field(owner,"content");for(int n=0;n<content.getChildCount();n++)if(content.getChildAt(n) instanceof android.widget.TextView)report.append(" status=").append(((android.widget.TextView)content.getChildAt(n)).getText());report.append('\n');}});}
        finally{ui(()->{if(owner!=null)owner.finish();if(target!=null)CookieManager.getInstance().setCookie(target,"qr_hupu_route=; Max-Age=0; Path=/123.html; Secure");android.content.SharedPreferences.Editor edit=prefs.edit();if(existed)edit.putBoolean("HUPU-web",old);else edit.remove("HUPU-web");edit.commit();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
