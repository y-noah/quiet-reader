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
import static app.quietreader.Models.*;

/** Synthetic HTML and a stalled async script; delegates real browser lifecycle to DynamicBoard. */
final class BoardCommitProbe {
    private final Instrumentation test;
    private final boolean hydration;
    private final StringBuilder report=new StringBuilder("SYNTHETIC BOARD COMMIT; real WebView, parser and production lifecycle; no source network\n");
    private int checks,failures;
    BoardCommitProbe(Instrumentation test){this(test,false);}
    BoardCommitProbe(Instrumentation test,boolean hydration){this.test=test;this.hydration=hydration;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){
        String database="test-ui-board-commit-"+UUID.randomUUID()+".db";
        Activity activity=null;DynamicBoard[] board={null};
        CountDownLatch release=new CountDownLatch(1),scriptStarted=new CountDownLatch(1),committed=new CountDownLatch(1),finished=new CountDownLatch(1),done=new CountDownLatch(1);
        AtomicInteger callbacks=new AtomicInteger();List<Item> rows=new ArrayList<>();String[] error={""};
        try{
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Activity owner=activity;
            String row="<div><a href='https://www.douyin.com/hot/123456'><h3>合成热榜条目</h3></a><span>100万 热度</span></div>";
            String html="<!doctype html><meta charset=utf-8><body>"+(hydration?"<div id='rows'></div><script>setTimeout(function(){document.getElementById('rows').innerHTML="+org.json.JSONObject.quote(row)+"},3000)</script>":row)+"<script async src='/__qr_stalled.js'></script></body>";
            test.runOnMainSync(()->{try{
                board[0]=new DynamicBoard(owner,Source.DOUYIN,new Repository.Result<List<Item>>(){public void success(List<Item> value){rows.addAll(value);callbacks.incrementAndGet();done.countDown();}public void failure(String why){error[0]=why;callbacks.incrementAndGet();done.countDown();}});
                Field field=DynamicBoard.class.getDeclaredField("web");field.setAccessible(true);WebView web=(WebView)field.get(board[0]);WebViewClient delegate=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                        if(request.getUrl().getPath().equals("/__qr_stalled.js")){
                            scriptStarted.countDown();return new WebResourceResponse("application/javascript","UTF-8",new InputStream(){boolean waited;public int read(){if(!waited){waited=true;try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}return -1;}});
                        }
                        return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                    }
                    @Override public void onPageCommitVisible(WebView view,String url){committed.countDown();delegate.onPageCommitVisible(view,url);}
                    @Override public void onPageFinished(WebView view,String url){finished.countDown();delegate.onPageFinished(view,url);}
                });
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(scriptStarted.await(5,TimeUnit.SECONDS),"controlled async script starts without contacting the source");
            check(committed.await(5,TimeUnit.SECONDS),"real page commits while an unrelated script remains pending");
            if(hydration)check(callbacks.get()==0,"empty initial shell is not delivered as a successful board");
            boolean early=done.await(5,TimeUnit.SECONDS);
            check(early&&finished.getCount()==1,"usable board completes before page-finished instead of waiting for unrelated resources");
            release.countDown();check(done.await(5,TimeUnit.SECONDS),"source-shaped fixture is accepted by production parser: "+error[0]);
            check(rows.size()==1&&rows.get(0).title.equals("合成热榜条目"),"real callback retains expected topic identity");
            Thread.sleep(350);check(callbacks.get()==1,"late page completion cannot deliver a second callback");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        finally{release.countDown();Activity end=activity;test.runOnMainSync(()->{if(board[0]!=null)board[0].close();if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
