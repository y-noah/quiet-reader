package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Real renderer timers and source snapshot script; controlled HTML, no account data. */
final class ZhihuExpansionProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("CONTROLLED ZHIHU EXPANSION: real WebView timers and parser, not live source authentication\n");
    ZhihuExpansionProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private String fixture(String scenario){
        String action="window.qrClicks=(window.qrClicks||0)+1;document.title='qr-expansion-click-'+window.qrClicks;";
        if(scenario.equals("slow"))action+="setTimeout(()=>{document.getElementById('prose').textContent='问题摘要以及完整补充尾部验证';document.getElementById('expand').remove()},9500);";
        return "<html><meta charset=utf-8><body><h1>受控展开问题</h1><div class=QuestionRichText><span id=prose>"+(scenario.equals("hidden")?"问题摘要以及完整补充尾部验证":"问题摘要")+"</span><button id=expand class=QuestionRichText-more "+(scenario.equals("hidden")?"style='visibility:hidden'":"")+" onclick=\""+action+"\">显示全部</button></div></body></html>";
    }
    void run(){
        String database="test-ui-zhihu-expansion-"+System.nanoTime()+".db";Activity activity=null;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Expansion fixture requires isolated emulator");
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Activity owner=activity;
            for(String scenario:new String[]{"slow","stuck","hidden"}){
                String url="https://qr-expansion-"+System.nanoTime()+".zhihu.com/question/123";
                Item item=new Item(Source.ZHIHU,"受控展开问题",url,"");String html=fixture(scenario);
                DynamicReader[] reader={null};Document[] result={null};String[] error={""};
                AtomicInteger callbacks=new AtomicInteger(),clicks=new AtomicInteger();CountDownLatch done=new CountDownLatch(1);long began=SystemClock.elapsedRealtime();
                try{
                    ui(()->{
                        reader[0]=new DynamicReader(owner,item,new Repository.Result<>(){public void success(Document value){result[0]=value;callbacks.incrementAndGet();done.countDown();}public void failure(String why){error[0]=why;callbacks.incrementAndGet();done.countDown();}},true);
                        WebView web=(WebView)field(reader[0],"web");WebViewClient delegate=web.getWebViewClient();
                        web.setWebChromeClient(new WebChromeClient(){@Override public void onReceivedTitle(WebView view,String title){if(title.startsWith("qr-expansion-click-"))clicks.set(Integer.parseInt(title.substring("qr-expansion-click-".length())));}});
                        web.setWebViewClient(new WebViewClient(){
                            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                            @Override public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                            @Override public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                        });
                    });
                    check(done.await(25,TimeUnit.SECONDS),scenario+": existing bounded acquisition terminates");
                    check(callbacks.get()==1,scenario+": exactly one terminal callback");
                    if(scenario.equals("stuck"))check(result[0]==null&&!error[0].isEmpty(),"stuck: unresolved preview is never delivered as a complete document");
                    else{
                        check(result[0]!=null&&result[0].blocks.stream().anyMatch(block->block.value.contains("完整补充尾部验证")),scenario+": full question supplement is delivered rather than the stable preview");
                        check(result[0]!=null&&ArticleVersion.matches(item,result[0]),scenario+": accepted body retains acquisition identity");
                    }
                    check(clicks.get()==(scenario.equals("hidden")?0:1),scenario+": only one visible expansion action; hidden control is untouched");
                    report.append("TIMING ").append(scenario).append(" elapsedMs=").append(SystemClock.elapsedRealtime()-began).append('\n');
                }finally{ui(()->{if(reader[0]!=null)reader[0].close();});}
                Thread.sleep(100);check(callbacks.get()==1,scenario+": closed acquisition cannot deliver a late duplicate");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{Activity ending=activity;ui(()->{if(ending!=null)ending.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
