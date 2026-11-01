package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.webkit.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real extraction WebViews with local intercepted HTML. No live platforms or phone accounts. */
final class DynamicReliabilityProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;
    private DynamicReader reader;
    private Document document;
    private ArticleFailure failure;
    private int callbacks,rounds,failures;
    private final AtomicInteger requests=new AtomicInteger();
    private final StringBuilder report=new StringBuilder();
    DynamicReliabilityProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void call(String name,Class<?>[] types,Object... args){try{Method m=DynamicReader.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(reader,args);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier action){boolean[] value={false};ui(()->value[0]=action.getAsBoolean());return value[0];}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void await(BooleanSupplier condition,String message)throws Exception{long end=SystemClock.elapsedRealtime()+7000;do{if(onUi(condition))return;Thread.sleep(15);}while(SystemClock.elapsedRealtime()<end);require(onUi(condition),message);}
    private Repository.Result<Document> result(){return new Repository.Result<>(){
        public void success(Document doc){document=doc;callbacks++;}
        public void failure(String why){failure(ArticleFailure.unknown(why));}
        public void failure(ArticleFailure error){failure=error;callbacks++;}
    };}
    private WebResourceRequest request(String url){return new WebResourceRequest(){public Uri getUrl(){return Uri.parse(url);}public boolean isForMainFrame(){return true;}public boolean isRedirect(){return false;}public boolean hasGesture(){return false;}public String getMethod(){return "GET";}public Map<String,String> getRequestHeaders(){return Collections.emptyMap();}};}
    private void scenario(int seed,int mode)throws Exception{
        String url="https://www.zhihu.com/question/"+(System.currentTimeMillis()+seed),marker="完整正文末尾样本"+seed;
        Item item=new Item(Source.ZHIHU,"隔离动态读取样本"+seed,url,"");
        String html="<html><meta charset=utf-8><body><h1>隔离动态读取样本</h1><div class=QuestionRichText>"+marker+"。".repeat(1+seed%19)+"</div></body></html>";
        WebView[] source={null};WebViewClient[] delegate={null};ui(()->{
            document=null;failure=null;callbacks=0;requests.set(0);reader=new DynamicReader(home,item,result(),true);source[0]=(WebView)field(reader,"web");delegate[0]=source[0].getWebViewClient();
            source[0].setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView web,WebResourceRequest request){requests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                @Override public void onPageCommitVisible(WebView web,String url){delegate[0].onPageCommitVisible(web,url);}
                @Override public void onPageFinished(WebView web,String url){delegate[0].onPageFinished(web,url);}
            });
            if(mode==3)reader.close();
        });
        if(mode==3){Thread.sleep(150+seed%7*10);require(onUi(()->callbacks==0&&requests.get()==0),"immediate close still navigated or delivered");return;}
        if(mode>=8){
            // Real client error path, before/after WebView start depending on seed.
            if(seed%2==0)Thread.sleep(35+seed%13);
            ui(()->{
                int status=mode==8?429:mode==9?503:408;delegate[0].onReceivedHttpError(source[0],request(url),new WebResourceResponse("text/html","UTF-8",status,"controlled",mode==10?Collections.emptyMap():Map.of("Retry-After","60"),new ByteArrayInputStream(new byte[0])));
            });
            await(()->callbacks==1,"HTTP/network failure callback absent");require(onUi(()->document==null&&failure!=null),"HTTP/network failure became content");
            if(mode==8)require(onUi(()->failure.kind==ArticleFailure.Kind.RATE_LIMIT&&failure.httpStatus==429&&!failure.mayFallback()),"rate limit or Retry-After dropped");
            if(mode==9)require(onUi(()->failure.retryable()&&failure.httpStatus==503&&!failure.mayFallback()),"server cooldown dropped");
            if(mode==10)require(onUi(()->failure.retryable()&&failure.httpStatus==408),"HTTP request timeout misclassified");
        }else{
            await(()->field(reader,"best")!=null,"real HTML parser never captured body");
            require(onUi(()->callbacks==0),"body unexpectedly delivered before scenario transition");
            switch(mode){
                case 0 -> await(()->callbacks==1,"stable dynamic body did not settle");
                case 1 -> ui(()->call("expire",new Class<?>[0]));
                case 2 -> {ui(()->{reader.detach();call("expire",new Class<?>[0]);});require(onUi(()->callbacks==0),"detached reader delivered to old owner");ui(()->reader.attach(home,result()));}
                case 4 -> {ui(()->{reader.close();call("expire",new Class<?>[0]);});Thread.sleep(160);require(onUi(()->callbacks==0),"cancelled snapshot delivered late");return;}
                case 5 -> ui(()->{new ArticleDiskCache(home).invalidate();call("expire",new Class<?>[0]);});
                case 6 -> {ui(()->{reader.detach();call("expire",new Class<?>[0]);new ArticleDiskCache(home).invalidate();reader.attach(home,result());});}
                case 7 -> ui(()->{call("navigating",new Class<?>[]{String.class},url+"?next=1");call("expire",new Class<?>[0]);});
                default -> throw new IllegalArgumentException();
            }
            await(()->callbacks==1,"terminal result absent");
            if(mode<=2){require(onUi(()->document!=null&&failure==null&&document.blocks.stream().anyMatch(block->block.value.contains(marker))&&ArticleVersion.matches(item,document)),"valid captured body lost or identity changed");}
            else require(onUi(()->document==null&&failure!=null),"retired/navigated body was accepted");
        }
        ui(()->{call("expire",new Class<?>[0]);reader.close();});Thread.sleep(100+seed%5*10);require(onUi(()->callbacks==1),"late event delivered a second terminal result");
    }
    void run(){String database="test-dynamic-reliability-"+UUID.randomUUID()+".db";ExecutorService storage=null;
        try{
            require(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"bulk WebView testing forbidden on user phone");
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));storage=(ExecutorService)field(field(home,"controller"),"storage");
            try(FileOutputStream output=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),"dynamic-reliability-rounds.jsonl"))){for(int n=0;n<220;n++){long start=SystemClock.elapsedRealtime();String error="";int mode=n%11;
                try{scenario(n,mode);}catch(Throwable problem){error=problem.toString();failures++;}finally{ui(()->{if(reader!=null)reader.close();});}
                rounds++;JSONObject row=new JSONObject().put("round",n+1).put("scenario",mode).put("seed",n).put("elapsedMs",SystemClock.elapsedRealtime()-start).put("requests",requests.get()).put("callbacks",callbacks).put("pass",error.isEmpty()).put("error",error);output.write((row+"\n").getBytes(StandardCharsets.UTF_8));output.flush();if(!error.isEmpty())report.append("FAIL ").append(row).append('\n');
                if(n%10==0){Bundle progress=new Bundle();progress.putString("stream","dynamic rounds="+rounds+" failures="+failures+"\n");test.sendStatus(0,progress);}
            }}
        }catch(Throwable problem){failures++;report.append("INFRA ").append(problem).append('\n');}
        finally{ui(()->{if(reader!=null)reader.close();if(home!=null)home.finish();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("rounds=").append(rounds).append(" failures=").append(failures).append("; isolated WebView extraction and callback lifecycle, not live source availability\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
