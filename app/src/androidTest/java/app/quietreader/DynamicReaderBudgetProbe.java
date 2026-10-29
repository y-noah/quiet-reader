package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import static app.quietreader.Models.*;

/** Late owned content on an actual WebView, with fully synthetic transport and no source requests. */
final class DynamicReaderBudgetProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC LATE DOUYIN; production reader and elapsed wall time, not live-source acceptance\n");
    DynamicReaderBudgetProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){
        String database="test-ui-reader-budget-"+UUID.randomUUID()+".db";Activity activity=null;DynamicReader[] reader={null};
        try{
            String id=Long.toString(System.currentTimeMillis());Item item=new Item(Source.DOUYIN,"合成延迟内容","https://www.douyin.com/hot/"+id+"/title","",true);
            JSONObject owner=new JSONObject("{aweme:{statusCode:0,detail:{awemeId:'456',desc:'合成延迟视频说明',status:{privateStatus:0},video:{}}},comment:{statusCode:0,comments:[]}}").put("hotId",id);
            String frame="7:"+new JSONArray().put("$").put("fixture").put(JSONObject.NULL).put(owner)+"\n";
            String script="self.__pace_f=[];setTimeout(function(){var s=document.createElement('script');s.textContent="+JSONObject.quote("self.__pace_f.push("+new JSONArray().put(1).put(frame)+");")+";document.body.appendChild(s)},26000)";
            String html="<!doctype html><meta charset=utf-8><body>合成加载页<script>"+script+"</script></body>";
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Activity home=activity;
            CountDownLatch done=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger();Document[] accepted={null};String[] error={""};long start=SystemClock.elapsedRealtime();
            test.runOnMainSync(()->{try{
                reader[0]=new DynamicReader(home,item,new Repository.Result<Document>(){public void success(Document doc){accepted[0]=doc;callbacks.incrementAndGet();done.countDown();}public void failure(String why){error[0]=why;callbacks.incrementAndGet();done.countDown();}},true);
                Field field=DynamicReader.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(reader[0]);WebViewClient delegate=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}
                    @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}
                });
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(done.await(38,TimeUnit.SECONDS),"foreground acquisition ends within its bounded wait");long elapsed=SystemClock.elapsedRealtime()-start;
            report.append("elapsedMs=").append(elapsed).append(" error=").append(error[0]).append('\n');
            check(accepted[0]!=null&&accepted[0].hasContent(),"owned content arriving after 26 seconds is accepted");
            check(accepted[0]!=null&&ArticleVersion.matches(item,accepted[0])&&item.url.equals(accepted[0].url),"late acquisition keeps requested version and topic identity");
            check(accepted[0]!=null&&accepted[0].unsupportedVideo,"video stays a description fallback without source player");
            check(accepted[0]!=null&&elapsed>=26_000&&elapsed<40_000,"wait survives the old cutoff but does not exceed foreground deadline");
            test.runOnMainSync(()->reader[0].close());Thread.sleep(250);check(callbacks.get()==1,"closing after delivery cannot duplicate completion");
            test.runOnMainSync(()->{try{
                AtomicInteger speculativeCallbacks=new AtomicInteger();DynamicReader background=new DynamicReader(home,item,new Repository.Result<Document>(){public void success(Document doc){speculativeCallbacks.incrementAndGet();}public void failure(String why){speculativeCallbacks.incrementAndGet();}});
                try{Field deadline=DynamicReader.class.getDeclaredField("deadlineAt");deadline.setAccessible(true);long remaining=(long)deadline.get(background)-SystemClock.elapsedRealtime();check(remaining>20_000&&remaining<=22_000,"speculative Douyin reader keeps the short original budget");}finally{background.close();}
                check(speculativeCallbacks.get()==0,"speculative reader can be cancelled before navigation without completion");
            }catch(Exception e){throw new IllegalStateException(e);}});
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{Activity end=activity;test.runOnMainSync(()->{if(reader[0]!=null)reader[0].close();if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
