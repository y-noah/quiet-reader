package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static app.quietreader.Models.*;

/** Actual reader WebView and decoder; source transport replaced with the CC0 asset. */
final class InlineVideoProbe {
    private final Instrumentation test;private MainActivity activity;
    private Object readerInstance;
    private final boolean capture;
    private int checks,failures;private final StringBuilder log=new StringBuilder("LOCAL CC0 video: actual inline WebView playback, not platform transport acceptance\n");
    private final AtomicInteger requests=new AtomicInteger(),released=new AtomicInteger();private final AtomicBoolean unavailable=new AtomicBoolean();
    InlineVideoProbe(Instrumentation test){this(test,true);}
    InlineVideoProbe(Instrumentation test,boolean capture){this.test=test;this.capture=capture;log.append("diagnostic screenshots=").append(capture).append('\n');}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;log.append(ok?"PASS ":"FAIL ").append(label).append('\n');Bundle progress=new Bundle();progress.putString("stream",(ok?"PASS ":"FAIL ")+label+"\n");test.sendStatus(0,progress);}
    private void screenshot(String name)throws Exception{
        if(!capture)return;
        CountDownLatch committed=new CountDownLatch(1);
        test.runOnMainSync(()->{try{if(field(activity,"inlineFullscreen")!=null){committed.countDown();return;}WebView web=(WebView)field(activity,"readerWeb");web.postVisualStateCallback(System.nanoTime(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){committed.countDown();}});}catch(Exception error){throw new IllegalStateException(error);}});
        if(!committed.await(3,TimeUnit.SECONDS))throw new IOException("Screenshot compositor commit timed out");
        test.waitForIdleSync();
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("No screenshot");
        File folder=new File(test.getTargetContext().getExternalFilesDir(null),"inline-video-probe");folder.mkdirs();
        try(OutputStream output=new FileOutputStream(new File(folder,name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private String evaluate(String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] value={"null"};
        test.runOnMainSync(()->{try{Method m=MainActivity.class.getDeclaredMethod("readerScript",WebView.class,String.class,Consumer.class);m.setAccessible(true);m.invoke(activity,field(activity,"readerWeb"),script,(Consumer<String>)result->{value[0]=result;done.countDown();});}catch(Exception error){throw new IllegalStateException(error);}});
        if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Script observation timed out");return value[0];
    }
    private boolean awaitScript(String script,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;do{if("true".equals(evaluate(script)))return true;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<end);return false;}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}}
    private boolean awaitState(BooleanSupplier condition,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;do{AtomicBoolean value=new AtomicBoolean();test.runOnMainSync(()->value.set(condition.getAsBoolean()));if(value.get())return true;Thread.sleep(50);}while(SystemClock.elapsedRealtime()<end);return false;}
    private boolean sleeping(){try{ReaderImageOwner images=(ReaderImageOwner)field(activity,"imageOwner");return !((PowerManager)activity.getSystemService(android.content.Context.POWER_SERVICE)).isInteractive()&&!(boolean)field(activity,"readingResumed")&&(boolean)field(activity,"wasStopped")&&field(activity,"inlineVideoRequests")==null&&field(activity,"inlineVideoMonitor")==null&&images!=null&&images.paused();}catch(Exception error){throw new IllegalStateException(error);}}
    private boolean resumed(){try{return ((PowerManager)activity.getSystemService(android.content.Context.POWER_SERVICE)).isInteractive()&&(boolean)field(activity,"readingResumed")&&activity.hasWindowFocus()&&field(activity,"readerWeb")==readerInstance;}catch(Exception error){throw new IllegalStateException(error);}}
    private void fullscreenTap()throws Exception{
        evaluate("(()=>{const v=document.querySelector('.inline-media video');v.scrollIntoView({block:'end'});window.scrollBy(0,48);return true})()");
        if(!awaitScript("(()=>{const r=document.querySelector('.inline-media video').getBoundingClientRect();return r.bottom>48&&r.bottom<innerHeight-12})()",3000))throw new IOException("Video controls not within the visible reader viewport");
        org.json.JSONObject rect=new org.json.JSONObject(evaluate("(()=>{const r=document.querySelector('.inline-media video').getBoundingClientRect();return {x:r.right-70,y:r.bottom-48,cx:r.left+r.width/2,cy:r.top+r.height/2}})()"));
        float[] point=new float[4];test.runOnMainSync(()->{try{WebView web=(WebView)field(activity,"readerWeb");int[] location=new int[2];web.getLocationOnScreen(location);point[0]=location[0]+(float)rect.optDouble("x")*web.getScale();point[1]=location[1]+(float)rect.optDouble("y")*web.getScale();point[2]=location[0]+(float)rect.optDouble("cx")*web.getScale();point[3]=location[1]+(float)rect.optDouble("cy")*web.getScale();}catch(Exception error){throw new IllegalStateException(error);}});
        log.append("FULLSCREEN_TOUCH rect=").append(rect).append(" physical=").append(java.util.Arrays.toString(point)).append('\n');
        tap(point[2],point[3]);Thread.sleep(150);
        android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
        if(root==null||!clickFullscreen(root))tap(point[0],point[1]);
    }
    private boolean clickFullscreen(android.view.accessibility.AccessibilityNodeInfo node){
        String label=(String.valueOf(node.getText())+" "+String.valueOf(node.getContentDescription())).toLowerCase(java.util.Locale.ROOT);
        if(label.contains("fullscreen")||label.contains("full screen")||label.contains("全屏")){
            log.append("NATIVE_FULLSCREEN_NODE ").append(label).append('\n');
            if(node.isVisibleToUser()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return true;
        }
        for(int n=0;n<node.getChildCount();n++){android.view.accessibility.AccessibilityNodeInfo child=node.getChild(n);if(child!=null&&clickFullscreen(child))return true;}
        return false;
    }
    private void tap(float x,float y)throws Exception{long at=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(at,at,0,x,y,0);try{test.sendPointerSync(down);Thread.sleep(60);android.view.MotionEvent up=android.view.MotionEvent.obtain(at,SystemClock.uptimeMillis(),1,x,y,0);try{test.sendPointerSync(up);}finally{up.recycle();}}finally{down.recycle();}}
    private ReaderVideoRequests fixture(String page,VideoAttachment video,byte[] bytes){return new ReaderVideoRequests(page,video){
        @Override WebResourceResponse response(String url,Map<String,String> headers)throws Exception{
            if(!owns(url))throw new IOException("Inactive fixture");requests.incrementAndGet();
            if(unavailable.get())return new WebResourceResponse("text/plain",null,404,"Unavailable",Map.of("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));
            String range=null;for(Map.Entry<String,String> header:headers.entrySet())if(header.getKey().equalsIgnoreCase("Range"))range=header.getValue();
            int start=0,end=bytes.length-1;boolean partial=range!=null;
            if(range!=null){if(!range.matches("bytes=[0-9]+-[0-9]*"))throw new IOException("Unexpected decoder range");String[] pair=range.substring(6).split("-",-1);start=Integer.parseInt(pair[0]);if(!pair[1].isEmpty())end=Math.min(end,Integer.parseInt(pair[1]));}
            if(start>end||start>=bytes.length)throw new IOException("Invalid decoder range");
            Map<String,String> response=new HashMap<>();response.put("Cache-Control","no-store");response.put("Accept-Ranges","bytes");response.put("Content-Length",String.valueOf(end-start+1));if(partial)response.put("Content-Range","bytes "+start+"-"+end+"/"+bytes.length);
            return new WebResourceResponse("video/mp4",null,partial?206:200,partial?"Partial Content":"OK",response,new ByteArrayInputStream(bytes,start,end-start+1));
        }
        @Override public void close(){released.incrementAndGet();super.close();}
    };}
    void run(){
        String url="https://sspai.com/post/9876501051";
        try{
            byte[] bytes;try(InputStream input=test.getContext().getAssets().open("media/flower.mp4")){bytes=input.readAllBytes();}
            Document doc=SourceParser.article(Source.SSPAI,"<div class=ss__article__body><p>视频前的原文段落</p><video src='https://cdn.sspai.com/first.mp4'></video><p>两个视频之间的文字</p><video src='https://cdn.sspai.com/second.mp4'></video><p>视频后的原文段落</p></div>",url);
            Item item=new Item(Source.SSPAI,"行内视频受控测试",url,"");
            test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(doc);repo.close();});
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long end=SystemClock.elapsedRealtime()+8000;AtomicBoolean ready=new AtomicBoolean();
            do{test.runOnMainSync(()->{try{ready.set(field(activity,"readerWeb")!=null&&field(activity,"reading")!=null);}catch(Exception error){throw new IllegalStateException(error);}});if(!ready.get())Thread.sleep(50);}while(!ready.get()&&SystemClock.elapsedRealtime()<end);
            check(ready.get(),"production reader opens cached text with video attachments");if(!ready.get())throw new IOException("No reader");
            test.runOnMainSync(()->{try{readerInstance=field(activity,"readerWeb");Field f=MainActivity.class.getDeclaredField("videoRequestFactory");f.setAccessible(true);f.set(activity,(BiFunction<String,VideoAttachment,ReaderVideoRequests>)(page,video)->fixture(page,video,bytes));}catch(Exception error){throw new IllegalStateException(error);}});
            check(awaitScript("(()=>{const v=document.querySelectorAll('.inline-media video');return v.length===2&&Array.from(v).every(x=>!x.hasAttribute('src')&&x.preload==='none')})()",5000),"two actual inline players have no media source before an explicit play");
            Thread.sleep(700);check(requests.get()==0,"opening and displaying reader make zero video transport requests");
            screenshot("before-play");
            // The CC0 asset is only five seconds: loop it during touch/fullscreen
            // checks, then separately verify normal completion below.
            evaluate("document.querySelectorAll('.inline-media video').forEach(v=>v.loop=true)");
            evaluate("document.querySelectorAll('.media-play')[0].click()");
            check(awaitScript("(()=>{const v=document.querySelectorAll('.inline-media video');return v[0].currentTime>.2&&!v[0].paused&&v[0].readyState>=2&&!v[1].hasAttribute('src')})()",12000),"first video actually decodes and advances inside the original article");
            log.append("MEDIA_STATE ").append(evaluate("(()=>{const f=document.querySelector('.inline-media'),v=f.querySelector('video');return {ready:v.readyState,paused:v.paused,time:v.currentTime,duration:Number.isFinite(v.duration)?v.duration:0,error:v.error?v.error.code:0,result:f.dataset.playResult||'',hasSource:v.hasAttribute('src')}})()")).append('\n');
            check(requests.get()>0,"only explicit play activates controlled streaming");
            screenshot("playing");
            fullscreenTap();
            AtomicBoolean fullscreen=new AtomicBoolean();end=SystemClock.elapsedRealtime()+2500;
            do{test.runOnMainSync(()->{try{fullscreen.set(field(activity,"inlineFullscreen")!=null);}catch(Exception error){throw new IllegalStateException(error);}});if(!fullscreen.get())Thread.sleep(50);}while(!fullscreen.get()&&SystemClock.elapsedRealtime()<end);
            check(fullscreen.get(),"actual native fullscreen control opens the common fullscreen view");
            screenshot("fullscreen-attempt");
            log.append("FULLSCREEN_STATE ").append(evaluate("(()=>{const v=document.querySelector('.inline-media video');return {fullscreen:!!document.fullscreenElement,webkit:!!document.webkitFullscreenElement,time:v.currentTime,paused:v.paused,source:v.hasAttribute('src'),error:v.error?v.error.code:0}})()")).append('\n');
            if(fullscreen.get()){
                screenshot("fullscreen");test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(300);
                AtomicBoolean returned=new AtomicBoolean();test.runOnMainSync(()->{try{returned.set(field(activity,"inlineFullscreen")==null&&!activity.isFinishing()&&field(activity,"readerWeb")==readerInstance);}catch(Exception error){throw new IllegalStateException(error);}});
                check(returned.get(),"actual Back leaves fullscreen and retains the same article");
            }
            evaluate("document.querySelector('.inline-media video').play().catch(()=>{})");
            check(awaitScript("(()=>{const v=document.querySelector('.inline-media video');return v.currentTime>.2&&!v.paused})()",2500),"inline playback remains available after fullscreen interaction");
            evaluate("document.querySelector('.inline-media video').pause()");Thread.sleep(300);
            double before=Double.parseDouble(evaluate("document.querySelector('.inline-media video').currentTime"));Thread.sleep(600);
            double after=Double.parseDouble(evaluate("document.querySelector('.inline-media video').currentTime"));check(before>.2&&Math.abs(after-before)<.15,"inline native pause stops actual playback time after real progress");
            int oldReleased=released.get();evaluate("document.querySelectorAll('.media-play')[1].click()");
            check(awaitScript("(()=>{const v=document.querySelectorAll('.inline-media video');return !v[0].hasAttribute('src')&&v[1].currentTime>.2&&!v[1].paused})()",10000)&&released.get()>oldReleased,"switching attachments releases previous owner and leaves one active inline video");
            unavailable.set(true);evaluate("document.querySelectorAll('.media-play')[0].click()");
            check(awaitScript("(()=>{const f=document.querySelector('.inline-media');return getComputedStyle(f.querySelector('video')).display==='none'&&f.querySelector('.media-status').textContent.includes('暂时无法播放')})()",7000),"unavailable video collapses its black frame into a compact retry/source fallback");
            screenshot("unavailable");
            unavailable.set(false);evaluate("document.querySelectorAll('.media-play')[0].click()");
            check(awaitScript("(()=>{const v=document.querySelector('.inline-media video');return getComputedStyle(v).display!=='none'&&v.currentTime>.2&&!v.paused})()",10000),"deliberate retry restores actual inline playback after failure");
            evaluate("(()=>{const v=document.querySelector('.inline-media video');v.loop=false;v.currentTime=v.duration-.3;v.play().catch(()=>{})})()");
            check(awaitScript("(()=>{const v=document.querySelector('.inline-media video');return !v.hasAttribute('src')&&v.paused})()",3500),"normal completion releases the source and restores the single play entry");
            evaluate("document.querySelectorAll('.media-play')[0].click()");
            check(awaitScript("(()=>{const v=document.querySelector('.inline-media video');return v.currentTime>.2&&!v.paused})()",5000),"completed attachment can be explicitly played again");
            int releasesBeforeSleep=released.get();shell("input keyevent 223");
            check(awaitState(this::sleeping,5000)&&released.get()>releasesBeforeSleep,"actual screen off releases video transport and monitor, pauses reading and closes image ownership");
            int asleepRequests=requests.get();Thread.sleep(1500);
            check(requests.get()==asleepRequests,"screen-off decoder does not make additional fixture transport requests during observation");
            shell("input keyevent 224");shell("wm dismiss-keyguard");
            check(awaitState(this::resumed,5000),"wake returns to the same reader WebView without rebuilding the article");
            check(awaitScript("Array.from(document.querySelectorAll('.inline-media video')).every(v=>v.paused&&!v.hasAttribute('src'))&&document.body.textContent.includes('视频后的原文段落')",3000),"wake preserves the article and leaves every attachment stopped without automatic playback");
            evaluate("document.querySelectorAll('.media-play')[0].click()");
            check(awaitScript("(()=>{const v=document.querySelector('.inline-media video');return v.currentTime>.2&&!v.paused&&v.readyState>=2})()",5000),"explicit play after wake starts actual decoding again");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME);Thread.sleep(800);
            AtomicBoolean stopped=new AtomicBoolean();test.runOnMainSync(()->{try{stopped.set(field(activity,"inlineVideoRequests")==null&&field(activity,"inlineVideoMonitor")==null);}catch(Exception error){throw new IllegalStateException(error);}});
            check(stopped.get(),"actual Home/background lifecycle cancels transport owner and playback monitor");
            check(awaitScript("Array.from(document.querySelectorAll('.inline-media video')).every(v=>v.paused&&!v.hasAttribute('src'))",3000),"background removes video sources and pauses all inline players");
        }catch(Throwable error){failures++;log.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();}
        log.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
