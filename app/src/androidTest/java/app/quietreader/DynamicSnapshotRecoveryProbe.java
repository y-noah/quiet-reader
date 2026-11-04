package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Deterministic lost WebView callback/navigation reproduction, on the isolated emulator only. */
final class DynamicSnapshotRecoveryProbe {
    private final Instrumentation test;private final StringBuilder report=new StringBuilder();private int checks,failures;
    DynamicSnapshotRecoveryProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Field field(String name)throws Exception{Field field=DynamicReader.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static final class LostCallbackView extends WebView {
        ValueCallback<String> lost;int evaluations;boolean drop=true;
        LostCallbackView(Context context){super(context);}
        @Override public void evaluateJavascript(String script,ValueCallback<String> result){evaluations++;if(drop&&lost==null){lost=result;return;}super.evaluateJavascript(script,result);}
    }
    void run(boolean baseline){Activity home=null;String database="test-ui-snapshot-"+System.nanoTime()+".db";
        try{
            if(!Build.HARDWARE.equals("ranchu")||!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Isolated emulator only");
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(int scenario=0;scenario<4;scenario++){
                boolean navigate=scenario%2==1,missingReady=scenario>=2;
                Activity owner=home;DynamicReader[] reader={null};LostCallbackView[] view={null};Document[] body={null};String[] failure={""};AtomicInteger callbacks=new AtomicInteger();CountDownLatch done=new CountDownLatch(1);
                String url="https://qr-snapshot-"+System.nanoTime()+".weibo.com/weibo?q=original";
                long started=SystemClock.elapsedRealtime();
                try{
                    test.runOnMainSync(()->{try{
                        reader[0]=new DynamicReader(owner,new Item(Source.WEIBO,"合成微博",url,""),new Repository.Result<>(){public void success(Document document){body[0]=document;callbacks.incrementAndGet();done.countDown();}public void failure(String reason){failure[0]=reason;callbacks.incrementAndGet();done.countDown();}},true);
                        WebView original=(WebView)field("web").get(reader[0]);WebViewClient delegate=original.getWebViewClient();
                        view[0]=new LostCallbackView(owner);view[0].drop=!missingReady;view[0].getSettings().setJavaScriptEnabled(true);field("web").set(reader[0],view[0]);SourceSurface.release(original);SourceSurface.attach(owner,view[0]);
                        view[0].setWebViewClient(new WebViewClient(){
                            @Override public WebResourceResponse shouldInterceptRequest(WebView web,WebResourceRequest request){String text=request.getUrl().toString().contains("next=1")?"新页面真实正文末尾":"原页面真实正文末尾";return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(("<html><meta charset=utf-8><body><h1>合成微博</h1><div class=card-wrap><p class=txt>"+text.repeat(12)+"</p></div></body></html>").getBytes(StandardCharsets.UTF_8)));}
                            @Override public void onPageCommitVisible(WebView web,String current){if(!missingReady)delegate.onPageCommitVisible(web,current);}
                            @Override public void onPageFinished(WebView web,String current){if(!missingReady)delegate.onPageFinished(web,current);}
                        });
                    }catch(Exception error){throw new IllegalStateException(error);}});
                    long until=SystemClock.elapsedRealtime()+7000;boolean[] dropped={false};do{test.runOnMainSync(()->dropped[0]=missingReady?view[0].getProgress()==100:view[0].lost!=null);if(dropped[0])break;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<until);
                    check(dropped[0],"source loaded with missingReady="+missingReady+" navigation="+navigate);
                    if(navigate)test.runOnMainSync(()->{try{Method move=DynamicReader.class.getDeclaredMethod("navigating",String.class);move.setAccessible(true);move.invoke(reader[0],url+"&next=1");view[0].loadUrl(url+"&next=1");}catch(Exception error){throw new IllegalStateException(error);}});
                    if(baseline){Thread.sleep(4500);check(callbacks.get()==0&&view[0].evaluations<=1,"baseline stalls despite available source HTML: missingReady="+missingReady+" navigation="+navigate);}
                    else{
                        check(done.await(10,TimeUnit.SECONDS),"recovers without waiting for the 22-second deadline: navigation="+navigate);
                        check(body[0]!=null&&body[0].blocks.stream().anyMatch(block->block.value.contains(navigate?"新页面真实正文末尾":"原页面真实正文末尾")),"actual latest source body recovered: navigation="+navigate);
                        check(callbacks.get()==1&&failure[0].isEmpty(),"only one success callback: navigation="+navigate);
                        int before=callbacks.get();test.runOnMainSync(()->{if(view[0].lost!=null)view[0].lost.onReceiveValue("null");});Thread.sleep(100);check(callbacks.get()==before,"late abandoned snapshot cannot deliver again");
                    }
                    report.append("SCENARIO missingReady=").append(missingReady).append(" navigation=").append(navigate).append(" elapsedMs=").append(SystemClock.elapsedRealtime()-started).append(" evaluations=").append(view[0].evaluations).append('\n');
                }finally{test.runOnMainSync(()->{if(reader[0]!=null)reader[0].close();});}
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{Activity last=home;test.runOnMainSync(()->{if(last!=null)last.finish();});test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; synthetic transport and withheld callback, no phone login claim\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
