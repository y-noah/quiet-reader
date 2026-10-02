package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.webkit.*;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Synthetic same-origin history navigation, not HTTP 302 or real-platform verification. */
public final class IdentityInstrumentation extends Instrumentation {
    private Activity activity;private File directory;
    private final StringBuilder report=new StringBuilder();private int checks,failures,completed;
    interface Action{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private synchronized void log(String value){report.append(value).append('\n');}
    private void check(boolean pass,String label){checks++;if(!pass)failures++;log((pass?"PASS ":"FAIL ")+label);}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void ui(Action action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private static String text(Document d){StringBuilder s=new StringBuilder();if(d!=null)for(Block b:d.blocks)if(b.type.equals("text"))s.append(b.value);return s.toString();}
    private final class Trial{
        final String name,url,path,marker;final Source source;final boolean reject;
        final CountDownLatch done=new CountDownLatch(1);volatile int requests,callbacks;volatile boolean sourceReady;volatile String observed="",failure="";
        volatile Document result;DynamicReader reader;WebView web;long start,callbackMs;
        Trial(String n,Source s,String u,String p,boolean r){name=n;source=s;url=u;path=p;reject=r;marker=r?"SYNTHETIC_WRONG_POST_BODY":"SYNTHETIC_EXPECTED_POST_BODY";}
        String html(){String content=source==Source.TIEBA?"<div class='comment-content'><div class='pb-rich-text'><p>"+marker+"</p></div></div>":"<div class='post-content_main-post-info__fixture'><div class='bbs-thread-comp main-thread'><div class='thread-content-detail'><p>"+marker+"</p></div></div></div>";
            return "<!doctype html><html><head><meta charset='utf-8'><title>合成帖子身份测试</title><script>history.replaceState(null,'',"+JSONObject.quote(path)+");console.log('QR_IDENTITY_READY:'+location.href);</script></head><body><h1>合成来源身份，不是真实帖</h1>"+content+"</body></html>";}
        void begin()throws Exception{
            start=SystemClock.elapsedRealtime();reader=new DynamicReader(activity,new Item(source,"合成帖子身份",url,""),new Repository.Result<Document>(){
                @Override public void success(Document d){result=d;callbacks++;callbackMs=SystemClock.elapsedRealtime()-start;done.countDown();}
                @Override public void failure(String reason){failure=reason;callbacks++;callbackMs=SystemClock.elapsedRealtime()-start;done.countDown();}
            });web=(WebView)field(reader,"web");WebViewClient delegate=web.getWebViewClient();byte[] bytes=html().getBytes(StandardCharsets.UTF_8);
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.message().startsWith("QR_IDENTITY_READY:")){observed=m.message().substring("QR_IDENTITY_READY:".length());sourceReady=true;}return true;}});
            web.setWebViewClient(new WebViewClient(){
                private WebResourceResponse response(String u){if(url.equals(u)){requests++;return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(bytes));}return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return response(r.getUrl().toString());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,String u){return response(u);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,String u){return delegate.shouldOverrideUrlLoading(w,u);}
                @Override public void onPageStarted(WebView w,String u,Bitmap i){delegate.onPageStarted(w,u,i);}
                @Override public void onPageCommitVisible(WebView w,String u){delegate.onPageCommitVisible(w,u);}
                @Override public void onPageFinished(WebView w,String u){delegate.onPageFinished(w,u);}
                @Override public void onLoadResource(WebView w,String u){delegate.onLoadResource(w,u);}
                @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){delegate.onReceivedError(w,r,e);}
                @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){delegate.onReceivedHttpError(w,r,e);}
                @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){delegate.onReceivedSslError(w,h,e);}
                @Override public void doUpdateVisitedHistory(WebView w,String u,boolean reload){delegate.doUpdateVisitedHistory(w,u,reload);}
                @Override public boolean onRenderProcessGone(WebView w,RenderProcessGoneDetail d){return delegate.onRenderProcessGone(w,d);}
            });
        }
        void run(){log("CASE "+name+" requested="+url+" historyPath="+path+" expectReject="+reject);try{
            check(SourceParser.article(source,html(),url).hasContent(),name+" fixture has real parseable synthetic body before navigation guard");
            ui(this::begin);boolean received=done.await(26,TimeUnit.SECONDS);
            check(received,name+" production callback within 26s");check(requests==1,name+" initial source intercepted exactly once");
            String expected=java.net.URI.create(url).resolve(path).toString();check(sourceReady&&observed.equals(expected),name+" source script loaded and actual history URL equals fixture target");
            check(callbacks==1,name+" exactly one actual production callback");
            check(reject?result==null&&!failure.isEmpty():result!=null&&failure.isEmpty(),name+" expected failure/success without callback substitution");
            check(reject?result==null:result!=null&&text(result).contains(marker)&&result.url.equals(url),name+" no wrong-post document / expected body and requested identity retained");
            ui(()->check((Boolean)field(reader,"ended")&&!(Boolean)field(reader,"ownsWeb")&&web.getParent()==null,name+" completed source ownership released"));
            log(name+" callbackMs="+callbackMs+" sourceReady="+sourceReady+" actualTarget="+observed+" resultUrl="+(result==null?"<none>":result.url)+" wrongMarkerReturned="+text(result).contains("SYNTHETIC_WRONG_POST_BODY")+" failure="+failure);
            summary(this);completed++;
        }catch(Throwable t){failures++;log("ERROR "+name+" "+t);}finally{try{ui(()->{if(reader!=null)reader.close();});save();}catch(Exception e){failures++;log("ERROR cleanup "+e);}}}
    }
    private void summary(Trial t)throws Exception{ui(()->{TextView v=new TextView(activity);v.setTextSize(18);v.setTextColor(0xffcdcfd5);v.setBackgroundColor(0xff1f2025);v.setPadding(28,65,28,28);v.setText("合成帖子身份回归\n非实网 / 非 HTTP 302\n"+t.name+"\n期望拒绝: "+t.reject+"\n来源目标: "+t.observed+"\n回调结果: "+(t.result==null?"失败":"成功")+"\n返回错误帖正文: "+text(t.result).contains("SYNTHETIC_WRONG_POST_BODY")+"\n回调耗时: "+t.callbackMs+"ms\n"+t.failure);activity.setContentView(v);});Thread.sleep(200);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("No summary screenshot");try(FileOutputStream o=new FileOutputStream(new File(directory,t.name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,o);}b.recycle();}
    private void save()throws Exception{try(FileOutputStream o=new FileOutputStream(new File(directory,"report.txt"))){o.write(report.toString().getBytes(StandardCharsets.UTF_8));}}
    @Override public void onStart(){directory=new File(getTargetContext().getExternalFilesDir(null),"identity-qa/run-"+System.currentTimeMillis());directory.mkdirs();try{
        log("SYNTHETIC ONLY. Real DynamicReader source parse/callback; same-origin history.replaceState, NOT HTTP302 proof. All fixture requests intercepted; no cookie/storage use. Six summary screenshots are diagnostics, not real reading UI.");
        activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->{((Repository)field(activity,"repo")).cancelPending();Object b=field(activity,"dynamicBoard");if(b!=null)((DynamicBoard)b).close();});
        String tieba="https://tieba.baidu.com/p/900000000000000001",hupu="https://bbs.hupu.com/900000000000000001.html";
        new Trial("01-tieba-wrong-post",Source.TIEBA,tieba,"/p/900000000000000002",true).run();
        new Trial("02-tieba-same-post",Source.TIEBA,tieba,"/p/900000000000000001",false).run();
        new Trial("03-tieba-same-pagination",Source.TIEBA,tieba,"/p/900000000000000001?pn=2",false).run();
        new Trial("04-hupu-wrong-post",Source.HUPU,hupu,"/900000000000000002.html",true).run();
        new Trial("05-hupu-same-post",Source.HUPU,hupu,"/900000000000000001.html",false).run();
        new Trial("06-hupu-same-pagination",Source.HUPU,hupu,"/900000000000000001-2.html",false).run();
    }catch(Throwable t){failures++;log("ERROR SETUP "+t);}finally{log("COMPLETED "+completed+" identity trials; checks="+checks+" failures="+failures);try{save();}catch(Exception e){failures++;}Bundle b=new Bundle();b.putString("stream",report+"\nOutput: "+directory);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);}}
}
