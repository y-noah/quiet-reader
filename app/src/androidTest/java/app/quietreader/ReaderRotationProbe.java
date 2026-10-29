package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.*;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.webkit.CookieManager;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.net.*;
import java.util.concurrent.atomic.*;
import static app.quietreader.Models.*;

/** Actual orientation changes at a nonzero semantic reading position, no source requests. */
final class ReaderRotationProbe {
    private final Instrumentation test;private MainActivity reader;private Repository repo;
    private final boolean slowImage;
    private final AtomicBoolean releaseImage=new AtomicBoolean();
    private final CountDownLatch imageRequested=new CountDownLatch(1);
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReaderRotationProbe(Instrumentation test){this(test,false);}
    ReaderRotationProbe(Instrumentation test,boolean slowImage){this.test=test;this.slowImage=slowImage;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Reader rotation deadline: "+label);Thread.sleep(40);}}
    private String js(String code)throws Exception{WebView web=(WebView)field(reader,"readerWeb");CountDownLatch done=new CountDownLatch(1);String[] value={""};ui(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Reader DOM callback missing");return value[0];}
    private void cookie(String url,boolean present)throws Exception{CountDownLatch done=new CountDownLatch(1);String path=android.net.Uri.parse(url).getPath();ui(()->CookieManager.getInstance().setCookie(url,"qr_rotation=changed; Path="+path+"; Secure; SameSite=Lax"+(present?"":"; Max-Age=0"),ignored->done.countDown()));if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic cookie callback missing");}
    private void ready()throws Exception{
        await("reader exists",()->field(reader,"readerWeb")!=null&&field(reader,"reading")!=null);
        long end=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<end){String expected=(String)field(reader,"readerPageUrl");String stage=slowImage?"document.readyState!=='loading'":"document.readyState==='complete'";if("true".equals(js("location.href==="+JSONObject.quote(expected)+"&&"+stage+"&&!!document.querySelector('.reader-content')"))&&onUi(()->(boolean)field(field(reader,"engagement"),"ready"))){Thread.sleep(700);return;}Thread.sleep(50);}throw new IllegalStateException("Current reader did not become readable");
    }
    private JSONObject position()throws Exception{return new JSONObject(js("(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){let r=a.getBoundingClientRect();if(r.bottom>0&&r.top<=1)return {id:a.id,fraction:Math.max(0,-r.top)/r.height,y:scrollY,width:innerWidth}}return {id:'',y:scrollY,width:innerWidth}})()"));}
    private void click(String selector)throws Exception{
        js("document.querySelector("+JSONObject.quote(selector)+").scrollIntoView({block:'center'})");Thread.sleep(150);
        JSONObject point=new JSONObject(js("(()=>{let r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})()"));float[] xy={0,0};
        ui(()->{WebView web=(WebView)field(reader,"readerWeb");int[] location=new int[2];web.getLocationOnScreen(location);xy[0]=location[0]+(float)point.optDouble("x")*web.getScale();xy[1]=location[1]+(float)point.optDouble("y")*web.getScale();});
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,xy[0],xy[1],0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
    }
    private MainActivity rotate(int orientation)throws Exception{
        MainActivity previous=reader;Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
        try{ui(()->previous.setRequestedOrientation(orientation));Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Orientation did not recreate reader");reader=(MainActivity)next;}finally{test.removeMonitor(monitor);}
        ready();check(reader!=previous&&previous.isDestroyed(),"orientation creates a new reader and destroys its previous Activity");return previous;
    }
    void run(){String url="https://bbs.hupu.com/"+System.currentTimeMillis()+".html";boolean accepted=CookieManager.getInstance().acceptCookie();
        try{
            Item item=new Item(Source.HUPU,"旋转阅读位置受控样本：长标题在不同宽度下换行，仍应保持段落进度",url,"");Document doc=new Document();doc.url=url;doc.title=item.title;
            Section body=new Section("rotation-main","主帖",false);
            if(slowImage){String image="https://9.9.9.9/qr-rotation-"+System.currentTimeMillis()+".png";body.blocks.add(new Block("image",image));gateImage(image);}
            for(int n=0;n<24;n++)body.blocks.add(new Block("text","第"+n+"段："+"横竖屏重新排版后，应继续显示同一段内容及段内阅读进度，不能只恢复旧的像素距离。".repeat(12)));
            Section reply=new Section("rotation-reply","回复 · 读者",false);reply.blocks.add(new Block("text","这条较长的讨论由用户主动展开，旋转后应保留展开状态。".repeat(60)));doc.sections.add(body);doc.sections.add(reply);doc.blocks.addAll(body.blocks);doc.blocks.addAll(reply.blocks);ArticleVersion.capture(item,doc);
            ui(()->{repo=new Repository(test.getTargetContext());repo.cacheArticle(doc);});
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));ready();
            if(slowImage)check(imageRequested.await(5,TimeUnit.SECONDS)&&!releaseImage.get()&&"true".equals(js("document.readyState!=='complete'")),"text is readable while a real production image resource remains blocked before page completion");
            if(reader.getResources().getConfiguration().orientation!=Configuration.ORIENTATION_PORTRAIT)rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            click("a[href='"+ReaderHtml.ACTION+"toggle?index=1']");await("reply expanded",()->((Set<?>)field(reader,"expanded")).contains(reply.id));ready();
            String anchor=ReaderHtml.anchorId(body.id,slowImage?10:9);js("(()=>{let r=document.getElementById("+JSONObject.quote(anchor)+").getBoundingClientRect();scrollTo(0,scrollY+r.top+r.height*.35)})()");Thread.sleep(500);
            JSONObject before=position();report.append("before=").append(before).append('\n');check(before.getString("id").equals(anchor)&&before.getInt("y")>0,"baseline is inside a non-initial paragraph at a known reading fraction");
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);JSONObject landscape=position();report.append("landscape=").append(landscape).append('\n');
            check(reader.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE,"actual configuration changes to landscape");
            check(landscape.getString("id").equals(before.getString("id"))&&Math.abs(landscape.optDouble("fraction")-before.getDouble("fraction"))<.08,"landscape preserves paragraph identity and within-paragraph reading progress after reflow");
            screenshot("landscape");
            if(slowImage)check(!releaseImage.get()&&"true".equals(js("document.readyState!=='complete'")),"semantic position is restored in landscape without waiting for the pending image or page completion");
            check(onUi(()->((Set<?>)field(reader,"expanded")).contains(reply.id)&&field(reader,"dynamic")==null&&field(reader,"reading")==doc),"expanded discussion and retained prepared body survive without source reload");
            rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);JSONObject portrait=position();report.append("portrait=").append(portrait).append('\n');
            check(portrait.getString("id").equals(before.getString("id"))&&Math.abs(portrait.optDouble("fraction")-before.getDouble("fraction"))<.08,"return to portrait preserves the original semantic reading position");
            check("true".equals(js("document.documentElement.scrollWidth<=innerWidth+1")),"rotated reader has no horizontal overflow");
            screenshot("portrait");
            if(!slowImage){ui(()->CookieManager.getInstance().setAcceptCookie(true));cookie(url,true);
            Document fresh=AnswerStream.copy(doc);fresh.cachedAt=0;fresh.cachedSession=null;fresh.cachedNamespace=null;
            ui(()->repo.cacheArticle(fresh));check(onUi(()->repo.cachedArticle(item)==fresh),"a current-session prepared body replaces the old cookie-scoped cache before rotation");
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            check(onUi(()->field(reader,"reading")==fresh&&field(reader,"dynamic")==null),"rotation after session change rejects the retained old-session body and uses the current prepared body");
            js("scrollTo(0,0)");Thread.sleep(500);rotate(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            check(position().getDouble("y")<2,"an article at its very top keeps the full title visible after rotating to portrait");
            rotate(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);check(position().getDouble("y")<2,"top-of-article rotation does not accumulate title-clipping scroll offsets");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{releaseImage.set(true);MainActivity owner=reader;ui(()->{if(owner!=null)owner.finish();if(repo!=null)repo.close();});test.waitForIdleSync();try{cookie(url,false);}catch(Exception error){failures++;report.append("ERROR synthetic cookie cleanup\n");}ui(()->CookieManager.getInstance().setAcceptCookie(accepted));test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void screenshot(String shape)throws Exception{
        CountDownLatch painted=new CountDownLatch(1);ui(()->((WebView)field(reader,"readerWeb")).postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){painted.countDown();}}));if(!painted.await(4,TimeUnit.SECONDS))throw new IllegalStateException("Reader visible frame unavailable");
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Reader screenshot unavailable");String name="reader-rotation-"+(slowImage?"loading-":"")+shape+".png";File file=new File(test.getTargetContext().getExternalFilesDir(null),name);
        try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}report.append("SCREENSHOT ").append(name).append('\n');
    }
    private void gateImage(String target){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){@Override protected URLConnection openConnection(URL url)throws IOException{
        if(!target.equals(url.toString()))throw new IOException("Unexpected controlled image URL");
        return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}@Override public int getResponseCode(){imageRequested.countDown();return 200;}@Override public long getContentLengthLong(){return 0;}@Override public InputStream getInputStream(){return new InputStream(){@Override public int read()throws IOException{long until=SystemClock.elapsedRealtime()+30000;while(!releaseImage.get()){if(SystemClock.elapsedRealtime()>until)throw new IOException("Controlled image wait expired");try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Controlled image interrupted");}}return -1;}};}};
    }});}
}
