package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.webkit.*;
import android.widget.TextView;
import org.json.JSONArray;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Controlled synthetic sources through the real production readers, parsers and callbacks.
 * The source's own outerHTML getter advances snapshots on production reads, not wall time.
 * No inspect/merge/callback substitution. No cookies or source HTML are exported.
 */
public final class DynamicReadingInstrumentation extends Instrumentation {
    private Activity activity;
    private File directory;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures,completed;
    private static final String URL="https://www.zhihu.com/question/9000000000000000001";
    interface Checked {void run() throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private synchronized void log(String s){report.append(s).append('\n');}
    private void check(boolean pass,String s){checks++;if(!pass)failures++;log((pass?"PASS ":"FAIL ")+s);}
    private void ui(Checked action)throws Exception{
        Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new Exception("Main-thread test setup failed",error[0]);
    }
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private static String answer(String id,String text){return "<div class='AnswerItem' data-zop='{&quot;itemId&quot;:&quot;"+id+"&quot;}'><span class='AuthorInfo-name'>合成作者</span><div class='RichContent-inner'><div class='RichText'><p>"+text+"</p></div></div></div>";}
    private static String page(String body){return "<!doctype html><html><head><title>合成动态读取回归</title></head><body><h1>合成测试，不是真实平台</h1>"+body+"</body></html>";}
    private static Document previous(int count){String html="";for(int i=1;i<=count;i++)html+=answer("fixture-"+i,"合成已读回答 "+i);return SourceParser.article(Source.ZHIHU,page(html),URL);}
    private static String text(Document d){StringBuilder b=new StringBuilder();for(Block block:d.blocks)b.append(block.value);return b.toString();}

    private final class Trial {
        final String name;final String[] snapshots;final boolean login;
        final CountDownLatch done=new CountDownLatch(1);
        volatile int reads,intercepts,callbacks;volatile long firstRead=-1,lastRead=-1,callbackAt=-1;
        volatile Document result;volatile String failure="";
        DynamicReader dynamic;AnswerStream stream;long start;
        Trial(String n,boolean l,String... s){name=n;login=l;snapshots=s;}
        long elapsed(){return SystemClock.elapsedRealtime()-start;}
        final Repository.Result<Document> callback=new Repository.Result<Document>(){
            @Override public void success(Document d){callbacks++;result=d;callbackAt=elapsed();done.countDown();}
            @Override public void failure(String reason){callbacks++;failure=reason;callbackAt=elapsed();done.countDown();}
        };
        String fixture(){
            JSONArray a=new JSONArray();for(String s:snapshots)a.put(s);
            // ES5 only: API 26's Chrome 69 must execute this fixture too.
            String script="(function(){var values="+a.toString().replace("</","<\\/")+",n=0;Object.defineProperty(document.documentElement,'outerHTML',{configurable:true,get:function(){n++;console.log('QR_SYNTHETIC_READ:'+n);return values[Math.min(n-1,values.length-1)];}});console.log('QR_SYNTHETIC_READY');})();";
            return "<!doctype html><html><head><meta charset='utf-8'><title>Synthetic controlled source</title></head><body><p>合成来源，只测试读取器，不是真实网站</p>"+(login?"<div class='SignContainer-content' style='width:200px;height:80px'>合成登录限制</div>":"")+"<script>"+script+"</script></body></html>";
        }
        void install(WebView web){
            WebViewClient delegate=web.getWebViewClient();
            byte[] html=fixture().getBytes(StandardCharsets.UTF_8);
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage message){
                String s=message.message();if(s.startsWith("QR_SYNTHETIC_READ:")){reads=Integer.parseInt(s.substring(s.indexOf(':')+1));lastRead=elapsed();if(firstRead<0)firstRead=lastRead;log(name+" source.outerHTML read="+reads+" ms="+lastRead);}return true;
            }});
            web.setWebViewClient(new WebViewClient(){
                private WebResourceResponse intercept(String url){
                    if(URL.equals(url)){intercepts++;return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html));}
                    // Even unintended subresources are intercepted: no fixture request reaches a platform.
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return intercept(r.getUrl().toString());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,String u){return intercept(u);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,String u){return delegate.shouldOverrideUrlLoading(w,u);}
                @Override public void onPageStarted(WebView w,String u,Bitmap icon){delegate.onPageStarted(w,u,icon);}
                @Override public void onPageCommitVisible(WebView w,String u){delegate.onPageCommitVisible(w,u);}
                @Override public void onPageFinished(WebView w,String u){delegate.onPageFinished(w,u);}
                @Override public void onLoadResource(WebView w,String u){delegate.onLoadResource(w,u);}
                @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){delegate.onReceivedError(w,r,e);}
                @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){delegate.onReceivedHttpError(w,r,e);}
                @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){delegate.onReceivedSslError(w,h,e);}
                @Override public void doUpdateVisitedHistory(WebView w,String u,boolean reload){delegate.doUpdateVisitedHistory(w,u,reload);}
                @Override public boolean onRenderProcessGone(WebView w,RenderProcessGoneDetail detail){return delegate.onRenderProcessGone(w,detail);}
            });
        }
        void beginDynamic()throws Exception{
            start=SystemClock.elapsedRealtime();dynamic=new DynamicReader(activity,new Item(Source.ZHIHU,"合成测试",URL,""),callback);
            // Same UI turn as construction, before asynchronous navigation callbacks dispatch.
            install((WebView)field(dynamic,"web"));
        }
        void beginStream(Document before)throws Exception{
            start=SystemClock.elapsedRealtime();stream=new AnswerStream(activity,new Item(Source.ZHIHU,"合成测试",URL,""));
            install((WebView)field(stream,"web"));stream.more(before,callback);
        }
        void stop(){if(dynamic!=null)dynamic.close();if(stream!=null)stream.close();}
        void await()throws Exception{
            check(done.await(27,TimeUnit.SECONDS),name+" production callback within bounded timeout");
            check(intercepts>=1,name+" source document was intercepted locally; requests="+intercepts);
            check(callbacks==1&&result!=null&&failure.isEmpty(),name+" exactly one production success, failure="+failure);
            log(name+" callbackMs="+callbackAt+" firstReadMs="+firstRead+" lastReadMs="+lastRead+" reads="+reads+" answers="+(result==null?-1:AnswerStream.answers(result)));
        }
    }
    private void snapshot(String name,Trial t)throws Exception{
        ui(()->{TextView view=new TextView(activity);view.setTextSize(18);view.setTextColor(0xffe3eae5);view.setBackgroundColor(0xff141a18);view.setPadding(28,64,28,28);view.setText("合成来源读取回归\n不是实网或正文视觉验收\n"+name+"\n生产读取次数: "+t.reads+"\n回调: "+t.callbackAt+" ms\n回答数: "+(t.result==null?-1:AnswerStream.answers(t.result))+"\n"+(t.result==null?t.failure:t.result.notice));activity.setContentView(view);});
        Thread.sleep(250);Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new AssertionError("Screenshot unavailable");
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    private void runTrial(Trial t,Document before,Checked assertions){
        log("\nCASE "+t.name);
        try{ui(()->{if(before==null)t.beginDynamic();else t.beginStream(before);});t.await();assertions.run();snapshot(t.name,t);completed++;}
        catch(Throwable e){failures++;log("ERROR "+t.name+" "+e);}
        finally{try{ui(t::stop);}catch(Exception e){failures++;log("ERROR cleanup "+e);}}
    }
    @Override public void onStart(){
        directory=new File(getTargetContext().getExternalFilesDir(null),"dynamic-reading-qa/run-"+System.currentTimeMillis());directory.mkdirs();
        try{
            log("SYNTHETIC ONLY. Real DynamicReader/AnswerStream parse and callback paths; locally intercepted HTML, no account login or real-platform completeness claim. No fixture cookies/storage writes. Source snapshots advance only when production reads outerHTML; console counters do not inspect DOM.");
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(()->{((Repository)field(activity,"repo")).cancelPending();Object board=field(activity,"dynamicBoard");if(board!=null)((DynamicBoard)board).close();});
            String[] changingPages=new String[3];
            for(int i=0;i<3;i++){
                String phase=String.valueOf((char)('A'+i));
                changingPages[i]=page(answer("fixture-"+phase,"合成阶段"+phase+"<img src='https://picx.zhimg.com/synthetic-"+phase+".png'>"));
            }
            Trial changing=new Trial("01-same-count-changing-content",false,changingPages);
            runTrial(changing,null,()->{
                for(String html:changingPages)check(SourceParser.article(Source.ZHIHU,html,URL).blocks.size()==2,"Each synthetic A/B/C snapshot has identical two-block count");
                check(changing.result!=null&&text(changing.result).contains("合成阶段C"),"Same block counts must not finish on changing phase B");
                check(changing.reads>=4,"C must be observed again before stable completion; reads="+changing.reads);
                check(changing.result!=null&&changing.result.sections.size()==1&&changing.result.sections.get(0).id.equals("fixture-C"),"Final answer ID C replaces equal-sized earlier source snapshot");
                check(changing.result!=null&&changing.result.containsImage("https://picx.zhimg.com/synthetic-C.png")&&!changing.result.containsImage("https://picx.zhimg.com/synthetic-B.png"),"Final image URL C retained rather than equal-count B");
                check(changing.result!=null&&changing.result.blocks.size()==2,"Result retains both final text and image blocks");
            });
            String old=answer("fixture-1","合成已读回答 1"),added=answer("fixture-2","合成唯一新增回答");
            Trial one=new Trial("02-one-new-answer",false,page(old+added));
            runTrial(one,previous(1),()->{
                check(one.result!=null&&AnswerStream.answers(one.result)==2&&text(one.result).contains("合成唯一新增回答"),"One real parsed new answer merged without duplicates");
                check(one.reads>=2&&one.reads<=3,"One stable new answer is delivered after approximately one observation interval, not +5/12 attempts; reads="+one.reads);
                check(one.firstRead>=0&&one.callbackAt-one.firstRead<4000,"Parsed new answer to callback <4s with scheduler tolerance; gap="+(one.callbackAt-one.firstRead));
            });
            String[] growingPages=new String[3];
            String[] growingText={"合成新增摘要A","合成新增中间回答B，正文仍在增长，不能提前提交。","合成新增最终完整回答C，经过三轮读取才获得这段完整文字和最终图片，请保留既有回答并等待内容稳定。"};
            for(int i=0;i<3;i++){
                String phase=String.valueOf((char)('A'+i));
                growingPages[i]=page(old+answer("fixture-2",growingText[i]+"<img src='https://picx.zhimg.com/synthetic-growth-"+phase+".png'>"));
            }
            Trial growing=new Trial("05-new-answer-still-growing",false,growingPages);
            runTrial(growing,previous(1),()->{
                boolean equalCounts=true;for(String html:growingPages)equalCounts&=SourceParser.article(Source.ZHIHU,html,URL).blocks.size()==4;
                check(equalCounts,"Growing stream A/B/C snapshots keep identical four-block count and same new-answer ID");
                check(growing.result!=null&&AnswerStream.answers(growing.result)==2&&text(growing.result).contains("合成已读回答 1"),"Growing stream retains old answer and deduplicates same-ID new answer");
                check(growing.result!=null&&text(growing.result).contains(growingText[2])&&!text(growing.result).contains(growingText[1]),"Growing stream delivers final complete C text, not the shorter B preview");
                check(growing.result!=null&&growing.result.containsImage("https://picx.zhimg.com/synthetic-growth-C.png")&&!growing.result.containsImage("https://picx.zhimg.com/synthetic-growth-B.png"),"Growing stream retains final C image rather than earlier B image");
                check(growing.reads>=4,"New answer existence alone must not finish while its content is changing; reads="+growing.reads);
            });
            Trial none=new Trial("03-no-new-answer",false,page(old));
            runTrial(none,previous(1),()->{
                check(none.result!=null&&AnswerStream.answers(none.result)==1,"No-new source retains exactly the prior answer");
                check(none.result!=null&&none.result.notice.contains("不等于已读完")&&!none.result.moreStatus.equals("complete"),"No-new outcome explicitly does not claim all answers read");
            });
            Trial login=new Trial("04-login-preserves-answers",true,page(old));
            runTrial(login,previous(2),()->{
                check(login.result!=null&&AnswerStream.answers(login.result)==2&&text(login.result).contains("合成已读回答 2"),"Visible login restriction retains both previous answers");
                check(login.result!=null&&login.result.moreStatus.equals("login")&&login.result.notice.contains("登录"),"Production login status and guidance preserved");
                check(login.reads==1,"Visible login gate stops after first source inspection");
            });
        }catch(Throwable e){failures++;log("ERROR SETUP "+e);}
        finally{
            log("COMPLETED "+completed+" dynamic reading trials; checks="+checks+" failures="+failures);
            try(FileOutputStream out=new FileOutputStream(new File(directory,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){failures++;log("ERROR report write "+e);}
            Bundle result=new Bundle();result.putString("stream",report+"\nOutput: "+directory);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
