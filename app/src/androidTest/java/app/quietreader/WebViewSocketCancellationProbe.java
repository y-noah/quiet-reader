package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.net.http.*;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import javax.net.ssl.*;
import static app.quietreader.Models.*;

/** Chromium's own real TLS body stream, not shouldInterceptRequest or a mocked loader. */
final class WebViewSocketCancellationProbe {
    private final Instrumentation test;
    private MainActivity activity;
    private Repository cache;
    private WebView web;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    WebViewSocketCancellationProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String stage,BooleanSupplier predicate)throws Exception{
        long end=SystemClock.elapsedRealtime()+8000;while(true){boolean[] good={false};ui(()->good[0]=predicate.getAsBoolean());if(good[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Deadline: "+stage);Thread.sleep(30);}
    }
    private void scenario(LoopbackTestTls tls,boolean home,Item item)throws Exception{
        String name=home?"actual HOME/onStop":"direct source release";
        CountDownLatch sent=new CountDownLatch(1),peerClosed=new CountDownLatch(1),committed=new CountDownLatch(1);
        AtomicReference<Throwable> serverError=new AtomicReference<>();AtomicInteger pins=new AtomicInteger(),requests=new AtomicInteger();ExecutorService server=Executors.newSingleThreadExecutor();
        try(SSLServerSocket listener=(SSLServerSocket)tls.context.getServerSocketFactory().createServerSocket(0,2,InetAddress.getByName("127.0.0.1"))){
            listener.setSoTimeout(8000);String url="https://127.0.0.1:"+listener.getLocalPort()+"/blocked";
            server.submit(()->{
                try{
                    // Chromium may retry TLS after the certificate pin decision.
                    for(int attempt=0;attempt<5;attempt++)try(SSLSocket socket=(SSLSocket)listener.accept()){
                        socket.setSoTimeout(8000);
                        try{socket.startHandshake();}catch(SSLHandshakeException retry){continue;}
                        InputStream in=socket.getInputStream();int tail=0,b;while((b=in.read())!=-1){tail=(tail<<8)|b;if(tail==0x0d0a0d0a)break;}
                        if(b<0)continue;requests.incrementAndGet();
                        byte[] body=("<!doctype html><html><head><meta charset=utf-8></head><body><p>测试来源正文正在传输。</p>"+"这段正文来自本地测试服务，保留未完成的网络流。".repeat(600)).getBytes(StandardCharsets.UTF_8);
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: 1000000\r\nConnection: close\r\n\r\n".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().write(body);socket.getOutputStream().flush();sent.countDown();
                        try{if(in.read()==-1)peerClosed.countDown();else throw new IOException("Unexpected HTTP pipeline bytes");}catch(SocketException closed){peerClosed.countDown();}return;
                    }
                    throw new IOException("No Chromium body request after pinned TLS retry");
                }catch(Throwable error){serverError.set(error);sent.countDown();}
            });
            ui(()->{
                web=new WebView(activity);web.clearSslPreferences();SourceSession.configure(activity,item.source,web);
                if(home)set(activity,"answers",new AnswerStream(activity,item,web));else SourceSurface.attach(activity,web);
                web.setWebViewClient(new WebViewClient(){
                    @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,SslError error){
                        Bundle state=SslCertificate.saveState(error.getCertificate());byte[] actual=state==null?null:state.getByteArray("x509-certificate");
                        // Test APK only: accept exactly this fixture's certificate
                        // on exactly this loopback URL. No global trust override.
                        if(url.equals(error.getUrl())&&Arrays.equals(tls.certificate,actual)){pins.incrementAndGet();handler.proceed();}else handler.cancel();
                    }
                    @Override public void onPageCommitVisible(WebView view,String current){if(url.equals(current))committed.countDown();}
                });web.loadUrl(url);
            });
            if(!sent.await(10,TimeUnit.SECONDS)||serverError.get()!=null)throw new IllegalStateException("Local TLS body setup "+name,serverError.get());
            if(!committed.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Chromium did not commit the streaming body "+name);
            check(requests.get()==1&&peerClosed.getCount()==1,name+" owns a real incomplete Chromium HTTPS body before cancellation");
            check(pins.get()>0,name+" test-only certificate decision is bound to the exact local URL and certificate");
            ui(()->web.clearSslPreferences());
            long start=SystemClock.elapsedRealtime();
            if(home){
                try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent 3");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] block=new byte[1024];while(in.read(block)!=-1){}}
                await("real onStop",()->(boolean)field(activity,"wasStopped"));
            }else ui(()->SourceSurface.release(web));
            boolean closed=peerClosed.await(2500,TimeUnit.MILLISECONDS);report.append("CLOSE ").append(name).append(" observedMs=").append(SystemClock.elapsedRealtime()-start).append(" peerClosed=").append(closed).append(" serverError=").append(serverError.get()==null?"none":serverError.get().getClass().getSimpleName()).append('\n');
            check(closed,name+" closes the real Chromium TLS stream before the server timeout");
            ui(()->{check(web.getParent()==null,name+" removes the source surface");if(home)check(field(activity,"answers")==null,name+" clears the retained source owner");});
            web=null;
        }finally{server.shutdownNow();server.awaitTermination(9,TimeUnit.SECONDS);}
    }
    void run(){
        try{
            LoopbackTestTls tls=new LoopbackTestTls(test);Item item=new Item(Source.ZHIHU,"本地联网释放测试","https://qr-web-cancel-"+System.nanoTime()+".zhihu.com/question/123","");Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","已缓存的阅读内容保持不变，只用测试来源页观察底层联网退出。".repeat(200)));ArticleVersion.capture(item,doc);
            ui(()->{cache=new Repository(test.getTargetContext());new ArticleSession(test.getTargetContext(),item).stamp(doc);cache.cacheArticle(doc);});
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("discovery_item",item.json().toString()));await("reader focus",()->activity.hasWindowFocus()&&field(activity,"reading")!=null);
            scenario(tls,false,item);scenario(tls,true,item);
            ui(()->check(field(activity,"reading")==doc,"background network teardown retains the displayed cached body"));
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(activity!=null){AnswerStream retained=(AnswerStream)field(activity,"answers");if(retained!=null){retained.close();set(activity,"answers",null);}}if(web!=null&&web.getParent()!=null){web.clearSslPreferences();SourceSurface.release(web);}if(activity!=null)activity.finish();if(cache!=null)cache.close();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
