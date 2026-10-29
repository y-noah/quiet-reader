package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Synthetic transport; captures real parser output before deliberately blocking its worker. */
final class DynamicDeadlineProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC DEADLINE: actual WebView/parser, not real source login validation\n");
    DynamicDeadlineProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    void run(boolean baseline){
        Activity home=null;String database="test-ui-deadline-"+System.nanoTime()+".db";
        try{
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(String scenario:baseline?new String[]{"deadline"}:new String[]{"deadline","navigation","account","gate","cancel"}){
                Activity owner=home;DynamicReader[] reader={null};Document[] accepted={null};String[] error={""};CountDownLatch done=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();
                String url="https://qr-deadline-"+System.nanoTime()+".zhihu.com/question/123";
                try{
                    test.runOnMainSync(()->{try{
                        reader[0]=new DynamicReader(owner,new Item(Source.ZHIHU,"合成正文",url,""),new Repository.Result<>(){public void success(Document doc){accepted[0]=doc;callbacks.incrementAndGet();done.countDown();}public void failure(String reason){error[0]=reason;callbacks.incrementAndGet();done.countDown();}},true);
                        WebView web=(WebView)field(reader[0],"web");WebViewClient delegate=web.getWebViewClient();
                        web.setWebViewClient(new WebViewClient(){
                            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<html><meta charset=utf-8><body><h1>合成正文</h1><div class=QuestionRichText>已经获取的完整正文末尾验证</div></body></html>".getBytes(StandardCharsets.UTF_8)));}
                            @Override public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                            @Override public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                        });
                    }catch(Exception e){throw new IllegalStateException(e);}});
                    boolean[] captured={false};long captureEnd=SystemClock.elapsedRealtime()+10000;
                    do{test.runOnMainSync(()->{try{if(field(reader[0],"best")!=null&&!captured[0]){captured[0]=true;((ExecutorService)field(reader[0],"parser")).submit(()->{try{release.await(30,TimeUnit.SECONDS);}catch(InterruptedException stopped){Thread.currentThread().interrupt();}});}}catch(Exception e){throw new IllegalStateException(e);}});if(!captured[0])Thread.sleep(20);}while(!captured[0]&&SystemClock.elapsedRealtime()<captureEnd);
                    check(captured[0]&&callbacks.get()==0,"complete real parser snapshot retained before a terminal result: "+scenario);
                    if(!scenario.equals("deadline"))test.runOnMainSync(()->{try{
                        if(scenario.equals("navigation")){Method move=DynamicReader.class.getDeclaredMethod("navigating",String.class);move.setAccessible(true);move.invoke(reader[0],url+"?next=1");}
                        if(scenario.equals("account"))CookieManager.getInstance().setCookie(url,"qr_deadline=new-account; Path=/; Secure");
                        if(scenario.equals("gate"))((Document)field(reader[0],"best")).loginRequired=true;
                        if(scenario.equals("cancel"))reader[0].close();
                        Method expire=DynamicReader.class.getDeclaredMethod("expire");expire.setAccessible(true);expire.invoke(reader[0]);
                    }catch(Exception e){throw new IllegalStateException(e);}});
                    if(scenario.equals("cancel")){Thread.sleep(200);check(callbacks.get()==0,"cancelled acquisition does not deliver retained content");}
                    else{
                        check(done.await(25,TimeUnit.SECONDS),"bounded terminal callback: "+scenario);
                        check(callbacks.get()==1,"one terminal callback: "+scenario);
                        if(scenario.equals("deadline")){check(accepted[0]!=null&&accepted[0].blocks.stream().anyMatch(b->b.value.contains("完整正文末尾验证")),"deadline preserves the captured complete body instead of replacing it with timeout");check(accepted[0]!=null&&ArticleVersion.matches(new Item(Source.ZHIHU,"合成正文",url,""),accepted[0]),"retained body keeps actual acquisition identity");}
                        else check(accepted[0]==null&&!error[0].isEmpty(),"no retained-body success after "+scenario);
                    }
                    release.countDown();test.runOnMainSync(()->reader[0].close());Thread.sleep(150);check(callbacks.get()<=1,"late parser completion cannot deliver twice: "+scenario);
                }finally{release.countDown();test.runOnMainSync(()->{if(reader[0]!=null)reader[0].close();CookieManager.getInstance().setCookie(url,"qr_deadline=; Max-Age=0; Path=/; Secure");});}
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{Activity end=home;test.runOnMainSync(()->{if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
