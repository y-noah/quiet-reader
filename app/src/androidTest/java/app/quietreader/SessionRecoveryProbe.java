package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static app.quietreader.Models.*;

/** Real DynamicReader navigation/retry/parser, with every source request locally intercepted. */
final class SessionRecoveryProbe {
    private static final String URL="https://www.zhihu.com/question/9000000000000000116";
    private final Instrumentation test;private Activity home;private DynamicReader reader;private Document document;private String failure="";
    private int checks,failures,httpStatus;private long retryAt;private final StringBuilder report=new StringBuilder();private final AtomicInteger surfaces=new AtomicInteger(),callbacks=new AtomicInteger();
    SessionRecoveryProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private String body(){return "<!doctype html><html><head><title>会话自动恢复受控验证</title></head><body><h1>会话自动恢复受控验证</h1><div class='AnswerItem' data-zop='{&quot;itemId&quot;:&quot;9000000000000000117&quot;}'><span class='AuthorInfo-name'>合成作者</span><div class='RichContent-inner'><div class='RichText'><p>共享登录状态自动恢复后的回答正文，无需手动打开原文。</p></div></div></div></body></html>";}
    private void intercept(WebView web,int number,boolean alwaysLogin,int status){
        WebViewClient delegate=web.getWebViewClient();
        String html=alwaysLogin||number==1?"<!doctype html><html><head><title>合成登录壳</title></head><body><div class='SignContainer-content'>登录知乎，发现更多</div></body></html>":body();
        byte[] bytes=html.getBytes(StandardCharsets.UTF_8);
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){
                if(r.isForMainFrame()&&status>0&&(number==1||alwaysLogin))return new WebResourceResponse("text/html","UTF-8",status,"Controlled response",java.util.Map.of("Retry-After","60"),new ByteArrayInputStream(bytes));
                return new WebResourceResponse(r.isForMainFrame()?"text/html":"text/plain","UTF-8",new ByteArrayInputStream(r.isForMainFrame()?bytes:new byte[0]));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
            @Override public void onPageCommitVisible(WebView w,String url){delegate.onPageCommitVisible(w,url);}
            @Override public void onPageFinished(WebView w,String url){delegate.onPageFinished(w,url);}
            @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){delegate.onReceivedError(w,r,e);}
            @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){delegate.onReceivedHttpError(w,r,e);}
        });
    }
    private void trial(boolean account,boolean alwaysLogin)throws Exception{
        trial(account,alwaysLogin,0,false);
    }
    private void trial(boolean account,boolean alwaysLogin,int status,boolean cancel)throws Exception{
        document=null;failure="";httpStatus=0;retryAt=0;surfaces.set(0);callbacks.set(0);CountDownLatch done=new CountDownLatch(1),cookie=new CountDownLatch(1);
        ui(()->CookieManager.getInstance().setCookie(URL,"z_c0="+(account?"synthetic-local-test":"")+"; Path=/; Secure",ok->cookie.countDown()));
        if(!cookie.await(5,TimeUnit.SECONDS))throw new IOException("Cookie setup timed out");
        ui(()->{
            ViewGroup host=home.findViewById(android.R.id.content);
            host.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){public void onChildViewAdded(View parent,View child){if(child instanceof WebView){int number=surfaces.incrementAndGet();intercept((WebView)child,number,alwaysLogin,status);if(cancel&&number==2)new Handler(Looper.getMainLooper()).post(()->{reader.close();done.countDown();});}}public void onChildViewRemoved(View parent,View child){}});
            reader=new DynamicReader(home,new Item(Source.ZHIHU,"会话自动恢复受控验证",URL,""),new Repository.Result<Document>(){
                public void success(Document doc){document=doc;callbacks.incrementAndGet();done.countDown();}
                public void failure(String reason){failure=reason;callbacks.incrementAndGet();done.countDown();}
                public void failure(String reason,int code,long at){httpStatus=code;retryAt=at;failure(reason);}
            },true);
        });
        if(!done.await(25,TimeUnit.SECONDS))throw new IOException("Bounded dynamic read did not finish");
        if(cancel)Thread.sleep(500);
        report.append("TRIAL account=").append(account).append(" persistentGate=").append(alwaysLogin).append(" status=").append(status).append(" cancel=").append(cancel).append(" surfaces=").append(surfaces.get()).append(" failure=").append(failure).append(" document=").append(document!=null).append(" login=").append(document!=null&&document.loginRequired).append('\n');
        if(surfaces.get()==2){Field replacement=DynamicReader.class.getDeclaredField("replacement"),deadline=DynamicReader.class.getDeclaredField("deadlineAt");replacement.setAccessible(true);deadline.setAccessible(true);check(deadline.getLong(reader)==deadline.getLong(replacement.get(reader)),"recovery inherits original deadline instead of extending the read");}
        ui(()->{reader.close();((ViewGroup)home.findViewById(android.R.id.content)).setOnHierarchyChangeListener(null);});
    }
    void run(){String database="test-ui-session116.db";
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.topicprobe")||!Build.MODEL.startsWith("sdk_gphone"))throw new IllegalStateException("Synthetic cookies allowed only in isolated emulator package");
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            trial(true,false);
            check(failure.isEmpty()&&document!=null&&document.hasContent()&&!document.loginRequired,"authenticated stale login shell recovers to parsed answer without manual source visit");
            check(surfaces.get()==2,"authenticated shell performs exactly one fresh shared-session navigation");
            if(document!=null)check(document.cachedSession!=null&&document.cachedSession.equals(ArticleDiskCache.session(URL)),"recovered body is stamped with its actual current acquisition session");
            trial(false,true);
            check(document!=null&&document.loginRequired,"genuinely logged-out source retains login requirement");check(surfaces.get()==1,"logged-out source does not enter an automatic login retry loop");
            trial(true,true);
            check(document!=null&&document.loginRequired,"unrecoverable authenticated restriction remains visible");check(surfaces.get()==2,"persistent restriction remains bounded to one recovery navigation");
            for(int status:new int[]{401,403}){
                trial(true,false,status,false);
                check(document!=null&&document.hasContent()&&httpStatus==0&&surfaces.get()==2&&callbacks.get()==1,"HTTP "+status+" recovers once to a single parsed-body completion");
                trial(true,true,status,false);
                check(document==null&&httpStatus==status&&surfaces.get()==2&&callbacks.get()==1,"persistent HTTP "+status+" ends after one retry with original status");
            }
            trial(false,true,401,false);
            check(httpStatus==401&&surfaces.get()==1&&callbacks.get()==1,"logged-out HTTP 401 is returned without account recovery");
            long before=System.currentTimeMillis();trial(true,true,429,false);
            check(httpStatus==429&&surfaces.get()==1&&callbacks.get()==1&&retryAt>=before+59000,"HTTP 429 preserves Retry-After and never starts account recovery");
            trial(true,false,401,true);
            check(surfaces.get()==2&&callbacks.get()==0&&document==null,"closing during recovery cancels the replacement without delivering stale content");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(reader!=null)reader.close();if(home!=null)home.finish();CookieManager.getInstance().removeAllCookies(null);});test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
