package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.Button;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Controlled production HTTP -> real reader image failure, layout, and gallery retry. */
final class ReaderImageFailureProbe {
    private final Instrumentation test;private MainActivity owner;private Repository repo;
    private final AtomicBoolean failReleased=new AtomicBoolean(),slowReleased=new AtomicBoolean(),retryWorks=new AtomicBoolean();
    private final Set<String> requested=ConcurrentHashMap.newKeySet();private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReaderImageFailureProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private Object field(Object object,String key){try{Field f=object.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(object);}catch(Exception error){throw new IllegalStateException(error);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private String js(WebView web,String script)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);String[] value={"null"};
        ui(()->{try{if(web!=field(owner,"readerWeb")){boolean prior=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(prior);ready.countDown();});return;}Method method=MainActivity.class.getDeclaredMethod("readerScript",WebView.class,String.class,Consumer.class);method.setAccessible(true);method.invoke(owner,web,script,(Consumer<String>)(result->{value[0]=result;ready.countDown();}));}catch(Exception error){throw new IllegalStateException(error);}});
        if(!ready.await(3,TimeUnit.SECONDS))throw new IOException("DOM callback missing");return value[0];
    }
    private void await(String label,Callable<Boolean> predicate)throws Exception{long until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){if(predicate.call())return;Thread.sleep(60);}throw new IOException("Deadline: "+label);}
    private void tap(View view)throws Exception{float[] point=new float[2];ui(()->{int[] xy=new int[2];view.getLocationOnScreen(xy);point[0]=xy[0]+view.getWidth()/2f;point[1]=xy[1]+view.getHeight()/2f;});pointer(point[0],point[1]);}
    private void pointer(float x,float y){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,0,x,y,0),up=MotionEvent.obtain(now,now+70,1,x,y,0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}test.waitForIdleSync();}
    private void screenshot(String name)throws Exception{Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new IOException("Screenshot missing");try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}}
    void run(){
        String suffix=UUID.randomUUID().toString(),page="https://sspai.com/post/image-failure-"+suffix;
        String http="https://cdn.sspai.com/failure-http-"+suffix+".png",invalid="https://cdn.sspai.com/failure-bytes-"+suffix+".png",valid="https://cdn.sspai.com/failure-valid-"+suffix+".png",slow="https://cdn.sspai.com/failure-slow-"+suffix+".png";
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated emulator target only");
            byte[] png;Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff3c9b72);try(ByteArrayOutputStream out=new ByteArrayOutputStream()){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);png=out.toByteArray();}finally{bitmap.recycle();}
            URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
                String value=url.toString();if(!List.of(http,invalid,valid,slow).contains(value))throw new IOException("Unexpected controlled-test destination");
                requested.add(value);return new HttpURLConnection(url){boolean closed;public void disconnect(){closed=true;}public boolean usingProxy(){return false;}public void connect(){}
                    private void released()throws IOException{long until=SystemClock.elapsedRealtime()+15000;while(!closed&&SystemClock.elapsedRealtime()<until&&((value.equals(http)||value.equals(invalid))&&!failReleased.get()||value.equals(slow)&&!slowReleased.get()))try{Thread.sleep(20);}catch(InterruptedException error){throw new InterruptedIOException();}if(closed)throw new InterruptedIOException();}
                    public int getResponseCode()throws IOException{released();return value.equals(http)&&!retryWorks.get()?503:200;}
                    public String getHeaderField(String key){return "Content-Type".equalsIgnoreCase(key)?"image/png":null;}
                    public InputStream getInputStream()throws IOException{released();return new ByteArrayInputStream(value.equals(invalid)?"invalid PNG bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8):png);}
                };}}:null);
            Item item=new Item(Source.SSPAI,"图片错误布局检查",page,"受控样本");Document doc=new Document();doc.url=page;doc.title=item.title;Section part=new Section("failure-body","正文",false);
            for(String url:List.of(http,invalid,valid,slow)){Block image=new Block("image",url);if(!url.equals(invalid)){image.imageWidth=1000;image.imageHeight=600;}part.blocks.add(image);}
            for(int n=0;n<20;n++)part.blocks.add(new Block("text","阅读位置检查第"+n+"段：此正文是受控布局样本，检查图片失败收紧之后，当前段落仍然保持在原来的屏幕位置。"));
            doc.sections.add(part);doc.blocks.addAll(part.blocks);ArticleVersion.capture(item,doc);ui(()->{repo=new Repository(test.getTargetContext());repo.cacheArticle(doc);for(String url:List.of(http,invalid,valid,slow))ReaderImages.shared.remove(url);});
            owner=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("reader",()->field(owner,"readerWeb")!=null);WebView web=(WebView)field(owner,"readerWeb");
            await("image markup",()->"4".equals(js(web,"document.querySelectorAll('figure img').length")));
            // This test explicitly requests all four images; not evidence about speculative prefetch.
            js(web,"document.querySelectorAll('figure img').forEach(i=>i.loading='eager')");await("four controlled requests",()->requested.size()==4);
            await("successful PNG",()->"true".equals(js(web,"document.querySelectorAll('figure img')[2].naturalWidth===16")));
            js(web,ReaderImageState.settle((String)field(owner,"readerPageUrl"),valid));
            check("true".equals(js(web,"!document.querySelectorAll('figure')[2].classList.contains('image-unavailable')")),"late same-URL error notification cannot collapse an already decoded image");
            js(web,ReaderImageState.settle((String)field(owner,"readerPageUrl"),slow));
            check("true".equals(js(web,"!document.querySelectorAll('figure')[3].classList.contains('image-unavailable')")),"same-URL stale notification cannot label an unfinished image as failed");
            js(web,"document.querySelectorAll('p[data-reading-anchor]')[4].scrollIntoView({block:'start'})");Thread.sleep(150);
            double before=Double.parseDouble(js(web,"document.querySelectorAll('p[data-reading-anchor]')[4].getBoundingClientRect().top"));
            failReleased.set(true);
            await("failed images complete",()->"true".equals(js(web,"Array.from(document.querySelectorAll('figure img')).slice(0,2).every(i=>i.complete&&i.naturalWidth===0)")));
            Thread.sleep(350);
            check("true".equals(js(web,"Array.from(document.querySelectorAll('figure')).slice(0,2).every(f=>f.getBoundingClientRect().height<=90&&f.querySelector('.image-open').textContent.includes('图片未加载'))")),"HTTP failure and invalid bytes become compact actionable image rows");
            check(Math.abs(before-Double.parseDouble(js(web,"document.querySelectorAll('p[data-reading-anchor]')[4].getBoundingClientRect().top")))<3,"failed images above the viewport preserve the visible paragraph position");
            check("true".equals(js(web,"!document.querySelectorAll('figure')[2].classList.contains('image-unavailable')&&document.querySelectorAll('figure img')[2].naturalWidth===16")),"successfully decoded image is not collapsed");
            check("true".equals(js(web,"!document.querySelectorAll('figure')[3].classList.contains('image-unavailable')&&!document.querySelectorAll('figure img')[3].complete")),"unfinished slow image is not labelled as failed");
            slowReleased.set(true);await("slow successful image",()->"true".equals(js(web,"document.querySelectorAll('figure img')[3].naturalWidth===16")));
            check("true".equals(js(web,"!document.querySelectorAll('figure')[3].classList.contains('image-unavailable')")),"slow image displays normally when its transfer succeeds");
            js(web,"document.querySelector('figure .image-open').scrollIntoView({block:'center'})");Thread.sleep(150);JSONObject point=new JSONObject(js(web,"(()=>{let r=document.querySelector('figure .image-open').getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})()"));
            float[] xy=new float[2];ui(()->{int[] position=new int[2];web.getLocationOnScreen(position);xy[0]=position[0]+(float)point.optDouble("x")*web.getScale();xy[1]=position[1]+(float)point.optDouble("y")*web.getScale();});pointer(xy[0],xy[1]);
            await("gallery",()->field(owner,"imagePreview")!=null);ImagePreview gallery=(ImagePreview)field(owner,"imagePreview");await("initial gallery error",()->Boolean.TRUE.equals(field(gallery,"settled")));
            retryWorks.set(true);AlertDialog dialog=(AlertDialog)field(gallery,"dialog");tap(dialog.getButton(AlertDialog.BUTTON_NEGATIVE));
            await("gallery retry decodes",()->{WebView large=(WebView)field(gallery,"current");return Boolean.TRUE.equals(field(gallery,"settled"))&&large!=null&&"true".equals(js(large,"document.images[0].naturalWidth===16"));});
            check(true,"actual compact caption opens gallery and actual Retry displays the recovered PNG");tap(dialog.getButton(AlertDialog.BUTTON_POSITIVE));
            check(field(owner,"readerWeb")==web,"gallery retry and close preserve the existing article WebView");Thread.sleep(400);screenshot("reader-image-failure-compact.png");
            await("native script policy restored",()->{boolean[] done={false};ui(()->done[0]=!web.getSettings().getJavaScriptEnabled()&&((Map<?,?>)field(owner,"readerScripts")).isEmpty());return done[0];});
            check(true,"native DOM work restores script-disabled reader policy");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{failReleased.set(true);slowReleased.set(true);ui(()->{if(owner!=null)owner.finish();if(repo!=null)repo.close();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
