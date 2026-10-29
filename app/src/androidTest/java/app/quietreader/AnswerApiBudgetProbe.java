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

/** Real same-origin WebView XHR; synthetic responses, no real account. */
final class AnswerApiBudgetProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC Zhihu API boundary; not real login certification\n");
    AnswerApiBudgetProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private void check(boolean good,String message){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(message).append('\n');}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private static void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception error){throw new IllegalStateException(error);}}
    void run(){Activity owner=null;
        try{
            owner=test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(int count:new int[]{599,598,1})scenario(owner,count);
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{Activity end=owner;if(end!=null)ui(end::finish);test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void scenario(Activity owner,int count)throws Exception{
        Item item=new Item(Source.ZHIHU,"接口预算合成问题","https://www.zhihu.com/question/"+System.currentTimeMillis(),"");
        Document previous=new Document();previous.title=item.title;previous.url=item.url;previous.metadataHash="original-metadata";previous.cachedAt=123;previous.cachedSession="fixture-session";
        for(int i=0;i<count;i++){Section section=new Section("old-"+i,"回答",true);section.blocks.add(new Block("text","已读回答"+i));previous.sections.add(section);previous.blocks.addAll(section.blocks);}
        String first=ZhihuAnswers.first(item),next=first.replace("offset=0","offset=20");
        org.json.JSONArray rows=new org.json.JSONArray();for(int i=0;i<2;i++)rows.put(new org.json.JSONObject().put("id",900001+i).put("content","<p>接口新增回答"+i+"</p>"));
        String response=new org.json.JSONObject().put("data",rows).put("paging",new org.json.JSONObject().put("is_end",false).put("next",next)).toString();
        AtomicInteger apiRequests=new AtomicInteger();AnswerStream[] stream={null};CountDownLatch done=new CountDownLatch(1);Document[] result={null};int[] callbacks={0};String[] failure={""};
        try{
            ui(()->{
                stream[0]=new AnswerStream(owner,item);set(stream[0],"nextApi",first);WebView web=(WebView)field(stream[0],"web");WebViewClient delegate=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){boolean api=request.getUrl().getPath().contains("/api/v4/");if(api)apiRequests.incrementAndGet();String body=api?response:"<html><body><h1>接口测试问题</h1></body></html>";return new WebResourceResponse(api?"application/json":"text/html","UTF-8",new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageFinished(WebView w,String url){delegate.onPageFinished(w,url);}
                    @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest request){return delegate.shouldOverrideUrlLoading(w,request);}
                });
                stream[0].more(previous,new Repository.Result<>(){public void success(Document doc){result[0]=doc;callbacks[0]++;done.countDown();}public void failure(String why){failure[0]=why;callbacks[0]+=100;done.countDown();}});
            });
            check(done.await(12,TimeUnit.SECONDS)&&result[0]!=null,"count="+count+": production XHR yields result: "+failure[0]);
            Thread.sleep(400);
            ui(()->{
                check(apiRequests.get()==1&&(int)field(stream[0],"apiPages")==1,"count="+count+": exactly one same-origin API page requested");
                if(result[0]==null)return;
                Document current=result[0];boolean limit=count>=598;
                check(current.sections.size()==(count==599?599:count+2)&&current.moreStatus.equals(limit?"limit":"loaded"),"count="+count+": reject overflowing batch, accept exact bound or ordinary append");
                check(current.sections.get(0)==previous.sections.get(0)&&current.metadataHash.equals(previous.metadataHash)&&current.cachedAt==123&&current.cachedSession.equals(previous.cachedSession),"count="+count+": original read sections and acquisition provenance retained");
                check(field(stream[0],"nextApi").equals(limit?first:next),"count="+count+": only continuing accepted batch advances API cursor");
                check(callbacks[0]==1&&(boolean)field(stream[0],"closed")==limit&&((ExecutorService)field(stream[0],"parser")).isShutdown()==limit,"count="+count+": one callback and appropriate source/parser lifetime");
            });
        }finally{ui(()->{if(stream[0]!=null)stream[0].close();});}
    }
}
