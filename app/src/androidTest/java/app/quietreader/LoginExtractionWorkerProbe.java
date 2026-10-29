package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Deterministic queue/lifecycle races with synthetic DOM snapshots and cookies. */
final class LoginExtractionWorkerProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC SOURCE IMPORT WORKER: actual DOM/parser/CookieManager; not phone or source permissions\n");
    LoginExtractionWorkerProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Button button(View view){if(view instanceof Button&&"读取当前页".equals(((Button)view).getText().toString()))return (Button)view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){Button found=button(((ViewGroup)view).getChildAt(n));if(found!=null)return found;}return null;}
    private void await(java.util.function.BooleanSupplier predicate)throws Exception{long end=SystemClock.elapsedRealtime()+8000;do{boolean[] ok={false};ui(()->ok[0]=predicate.getAsBoolean());if(ok[0])return;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<end);throw new IllegalStateException("controlled import condition timed out");}
    private void awaitDom(WebView web)throws Exception{long end=SystemClock.elapsedRealtime()+8000;do{CountDownLatch done=new CountDownLatch(1);boolean[] ok={false};ui(()->web.evaluateJavascript("document.readyState==='complete'&&!!document.querySelector('.QuestionRichText')&&document.body.innerText.includes('末尾核对')",value->{ok[0]="true".equals(value);done.countDown();}));if(done.await(2,TimeUnit.SECONDS)&&ok[0])return;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<end);throw new IllegalStateException("controlled source DOM was not ready");}
    void run(){
        LoginActivity page=null;
        java.util.Map<String,?> prior=test.getTargetContext().getSharedPreferences("source-session",0).getAll();
        try{
            for(String scenario:new String[]{"read","burst","navigation","account","pause","destroy"}){
                String url="https://qr-worker-"+System.nanoTime()+".zhihu.com/question/123";
                CountDownLatch release=new CountDownLatch(1),started=new CountDownLatch(1);
                page=(LoginActivity)test.startActivitySync(new Intent(test.getTargetContext(),LoginActivity.class).putExtra("source","ZHIHU").putExtra("url",url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));LoginActivity owner=page;
                try{
                    ThreadPoolExecutor worker=(ThreadPoolExecutor)field(owner,"extractor");WebView web=(WebView)field(owner,"web");CountDownLatch loaded=new CountDownLatch(1);
                    ui(()->{LoginActivity.pendingDocument=null;web.stopLoading();WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(("<html><meta charset=utf-8><body><h1>合成内容</h1><div class=QuestionRichText>"+"来源导入完整中文正文。".repeat(2000)+"末尾核对</div></body></html>").getBytes(StandardCharsets.UTF_8)));}
                        @Override public void onPageStarted(WebView view,String current,android.graphics.Bitmap icon){delegate.onPageStarted(view,current,icon);}
                        @Override public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);loaded.countDown();}
                    });web.loadUrl(url);});
                    check(loaded.await(8,TimeUnit.SECONDS),"controlled source loaded: "+scenario);
                    awaitDom(web);
                    worker.execute(()->{check(Looper.myLooper()!=Looper.getMainLooper(),"snapshot executor runs off the UI thread: "+scenario);started.countDown();try{release.await(10,TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}});
                    check(started.await(2,TimeUnit.SECONDS),"controlled worker held before source capture: "+scenario);
                    ui(()->button(owner.getWindow().getDecorView()).performClick());
                    await(()->worker.getQueue().size()==1);
                    if(scenario.equals("burst")){for(int n=0;n<12;n++){ui(()->button(owner.getWindow().getDecorView()).performClick());Thread.sleep(20);}await(()->worker.getQueue().size()==1);}
                    ui(()->{check(!owner.isFinishing()&&LoginActivity.pendingDocument==null,"UI still processes actions while parsing is queued: "+scenario);check(worker.getQueue().size()<=1&&worker.getPoolSize()==1,"source parsing has one worker and bounded pending work: "+scenario);});
                    if(scenario.equals("navigation")){ui(()->web.loadUrl(url.replace("/123","/456")));await(()->web.getUrl().endsWith("/456"));}
                    if(scenario.equals("account"))ui(()->CookieManager.getInstance().setCookie(url,"qr_worker=changed; Path=/; Secure"));
                    if(scenario.equals("pause")){ui(()->test.callActivityOnPause(owner));check(worker.getQueue().isEmpty(),"pause drops queued source parsing");}
                    if(scenario.equals("destroy")){ui(owner::finish);await(owner::isDestroyed);check(worker.isShutdown()&&worker.getQueue().isEmpty(),"destroy cancels pending work and shuts down its executor");}
                    release.countDown();
                    if(scenario.equals("read")||scenario.equals("burst")){
                        await(owner::isFinishing);Document doc=LoginActivity.pendingDocument;
                        check(doc!=null&&doc.blocks.stream().anyMatch(b->b.value.endsWith("末尾核对")),"actual queued snapshot parses through its body end: "+scenario);
                        check(doc!=null&&doc.cachedSession.equals(ArticleDiskCache.session(url)),"actual captured session is preserved: "+scenario);
                    }else if(scenario.equals("account")){
                        await(()->((TextView)field(owner,"status")).getText().toString().contains("状态已变化"));
                        check(LoginActivity.pendingDocument==null&&!owner.isFinishing(),"changed account refuses the earlier captured snapshot");
                    }else{
                        if(!worker.isShutdown())worker.submit(()->{}).get(3,TimeUnit.SECONDS);
                        ui(()->check(LoginActivity.pendingDocument==null,"obsolete queued source cannot publish after "+scenario));
                        if(scenario.equals("pause")){ui(()->test.callActivityOnResume(owner));check(worker.getQueue().isEmpty(),"resume does not restart source parsing without another action");}
                    }
                }finally{release.countDown();ui(()->{CookieManager.getInstance().setCookie(url,"qr_worker=; Max-Age=0; Path=/; Secure");LoginActivity.pendingDocument=null;if(!owner.isFinishing())owner.finish();});test.waitForIdleSync();}page=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{LoginActivity end=page;ui(()->{if(end!=null&&!end.isFinishing())end.finish();LoginActivity.pendingDocument=null;android.content.SharedPreferences.Editor edit=test.getTargetContext().getSharedPreferences("source-session",0).edit().clear();for(java.util.Map.Entry<String,?> entry:prior.entrySet())if(entry.getValue() instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)entry.getValue());edit.apply();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
