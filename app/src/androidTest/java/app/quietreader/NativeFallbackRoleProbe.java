package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import static app.quietreader.Models.*;

/** Real native acquisition then one controlled extraction WebView; no live accounts. */
final class NativeFallbackRoleProbe {
    private final Instrumentation test;private Activity home;private PendingArticle pending;private DynamicReader dynamic;
    private int checks,failures;private final StringBuilder report=new StringBuilder("NATIVE FALLBACK ROLE; controlled transport, not live source certification\n");
    NativeFallbackRoleProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private String hydrated(String id)throws Exception{
        JSONObject detail=new JSONObject().put("contId",id).put("contType",0).put("name","受控动态正文").put("content","<p>浏览器取得的完整中文正文，应当替换原生壳页。</p>");
        JSONObject state=new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("contId",id).put("detailData",new JSONObject().put("contentDetail",detail))));
        return "<html><meta charset=utf-8><body><script id=__NEXT_DATA__ type=application/json>"+state+"</script></body></html>";
    }
    void run(){String database="test-ui-native-fallback-"+UUID.randomUUID();AtomicInteger nativeRequests=new AtomicInteger();Set<String> allowed=ConcurrentHashMap.newKeySet();
        try{
            URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{if(!allowed.contains(url.toString()))throw new IOException("Unexpected fixture URL");return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){nativeRequests.incrementAndGet();return 200;}public InputStream getInputStream(){return new ByteArrayInputStream("<html><body><div id=app></div></body></html>".getBytes(StandardCharsets.UTF_8));}};}});
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(boolean readable:new boolean[]{true,false}){
                String id=Long.toString(System.currentTimeMillis()),url="https://www.thepaper.cn/newsDetail_forward_"+id;allowed.add(url);Item item=new Item(Source.THEPAPER,"受控原生壳页",url,"");
                String html=readable?hydrated(id):"<html><body><div id=app></div></body></html>";CountDownLatch done=new CountDownLatch(1);AtomicInteger nativeCallbacks=new AtomicInteger(),browserStarts=new AtomicInteger(),browserRequests=new AtomicInteger(),terminal=new AtomicInteger();Document[] result={null};String[] failure={""};int before=nativeRequests.get();
                ui(()->{pending=new PendingArticle(home,item);pending.attach(new Repository.Result<Document>(){
                    public void success(Document doc){nativeCallbacks.incrementAndGet();check(doc.sourceUnavailable&&!doc.hasContent(),"real native parser returns an unavailable shell");
                        if(SourceSession.nativeDocumentReady(item.source,doc)){result[0]=doc;terminal.incrementAndGet();done.countDown();return;}
                        browserStarts.incrementAndGet();dynamic=new DynamicReader(home,item,new Repository.Result<Document>(){public void success(Document body){result[0]=body;terminal.incrementAndGet();done.countDown();}public void failure(String why){failure[0]=why;terminal.incrementAndGet();done.countDown();}},true);
                        WebView web=(WebView)field(dynamic,"web");WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(request.isForMainFrame())browserRequests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                            public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                            public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                        });
                    }
                    public void failure(String why){failure[0]=why;terminal.incrementAndGet();done.countDown();}
                });pending.start();});
                check(done.await(15,TimeUnit.SECONDS),"native shell fallback settles: readable="+readable);
                check(nativeRequests.get()==before+1&&nativeCallbacks.get()==1,"one native acquisition returns once");
                check(browserStarts.get()==1&&browserRequests.get()==1,"exactly one browser fallback navigation");
                check(terminal.get()==1,"exactly one terminal result");
                check(readable?result[0]!=null&&result[0].hasContent()&&!result[0].sourceUnavailable&&result[0].blocks.stream().anyMatch(b->b.value.contains("浏览器取得的完整中文正文")):result[0]!=null&&result[0].sourceUnavailable&&!result[0].hasContent(),readable?"hydrated body replaces native shell":"second unavailable shell remains unavailable without invented prose");
                Thread.sleep(700);check(nativeRequests.get()==before+1&&browserStarts.get()==1&&terminal.get()==1,"terminal browser result never loops back into native acquisition");
                ui(()->{if(pending!=null)pending.close();if(dynamic!=null)dynamic.close();pending=null;dynamic=null;});
                if(!failure[0].isEmpty())report.append("sourceFailure=").append(failure[0]).append('\n');
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(pending!=null)pending.close();if(dynamic!=null)dynamic.close();if(home!=null)home.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("scope=PendingArticle, parser, native readiness decision and DynamicReader; not MainActivity navigation or real permissions\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
