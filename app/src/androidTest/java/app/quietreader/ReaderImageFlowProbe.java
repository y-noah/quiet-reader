package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.Button;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import static app.quietreader.Models.*;

/** Actual pointer events: own reader image -> production cache/preview -> gallery -> close. */
final class ReaderImageFlowProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC cached PNGs; real reader link and native gallery pointer events\n");
    ReaderImageFlowProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String key){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(true){boolean[] good={false};test.runOnMainSync(()->good[0]=condition.getAsBoolean());if(good[0])return;if(SystemClock.elapsedRealtime()>end)throw new IOException("Image flow wait expired");Thread.sleep(50);}}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Image DOM timeout");return result[0];}
    private void pointer(float x,float y){long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}}
    private void tap(View view){float[] p={0,0};test.runOnMainSync(()->{int[] xy=new int[2];view.getLocationOnScreen(xy);p[0]=xy[0]+view.getWidth()/2f;p[1]=xy[1]+view.getHeight()/2f;});pointer(p[0],p[1]);}
    private String tapCaption(WebView reader)throws Exception{
        js(reader,"document.querySelectorAll('figcaption a')[1].scrollIntoView({block:'center'})");test.waitForIdleSync();Thread.sleep(200);
        JSONObject point=new JSONObject(js(reader,"(()=>{const r=document.querySelectorAll('figcaption a')[1].getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})()"));String position=js(reader,"JSON.stringify({y:scrollY,url:location.href})");float[] screen={0,0};test.runOnMainSync(()->{int[] xy=new int[2];reader.getLocationOnScreen(xy);screen[0]=xy[0]+(float)point.optDouble("x")*reader.getScale();screen[1]=xy[1]+(float)point.optDouble("y")*reader.getScale();});pointer(screen[0],screen[1]);return position;
    }
    private byte[] png(int width,int height,int color){Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();return bytes.toByteArray();}
    void run(){
        MainActivity activity=null;
        try{
            String first="https://reader-image-fixture.invalid/first.png",second="https://reader-image-fixture.invalid/second.png";
            Document body=new Document();body.url="https://www.cls.cn/detail/"+System.currentTimeMillis();body.title="正文图片入口与图库合成检查";
            for(int n=0;n<24;n++){body.blocks.add(new Block("text","这是合成图文阅读段落，用于确认图片入口、返回位置与画廊切换。段落编号 "+n));if(n==5||n==10||n==15)body.blocks.add(new Block("image",n==10?second:first));}
            Item item=new Item(Source.CLS,body.title,body.url,"");ArticleVersion.capture(item,body);
            test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(body);repo.close();});
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity owner=activity;
            await(()->field(owner,"readerWeb")!=null);WebView reader=(WebView)field(owner,"readerWeb");
            byte[] one=png(500,1800,0xff82aacc),two=png(700,1000,0xffd9ab74);
            test.runOnMainSync(()->{try{
                ReaderImages images=(ReaderImages)field(owner,"images");images.put(first,body.url,one);images.put(second,body.url,two);
                Method reload=MainActivity.class.getDeclaredMethod("reloadReader",Document.class);reload.setAccessible(true);reload.invoke(owner,body);
            }catch(Exception e){throw new IllegalStateException(e);}});
            String page=(String)field(owner,"readerPageUrl");long until=SystemClock.elapsedRealtime()+8000;boolean ready=false;
            while(SystemClock.elapsedRealtime()<until){if("true".equals(js(reader,"location.href==="+JSONObject.quote(page)+"&&!!document.querySelector('figure img')"))){ready=true;break;}Thread.sleep(50);}if(!ready)throw new IOException("Current image reader did not commit");
            js(reader,"document.querySelector('figure a').scrollIntoView({block:'center'})");test.waitForIdleSync();Thread.sleep(200);
            JSONObject point=new JSONObject(js(reader,"(()=>{const r=document.querySelector('figure a img').getBoundingClientRect();return {x:r.left+r.width/2,y:Math.max(1,r.top)+Math.min(80,(Math.min(innerHeight,r.bottom)-Math.max(1,r.top))/2),scroll:scrollY}})()"));
            check(point.getDouble("scroll")>0,"actual image link is reached below the article top");
            String position=js(reader,"JSON.stringify({y:scrollY,url:location.href})");float[] screen={0,0};test.runOnMainSync(()->{int[] xy=new int[2];reader.getLocationOnScreen(xy);screen[0]=xy[0]+(float)point.optDouble("x")*reader.getScale();screen[1]=xy[1]+(float)point.optDouble("y")*reader.getScale();});pointer(screen[0],screen[1]);
            await(()->field(owner,"imagePreview")!=null);ImagePreview preview=(ImagePreview)field(owner,"imagePreview");await(()->Boolean.TRUE.equals(field(preview,"settled")));
            check((int)field(preview,"index")==0&&((java.util.List<?>)field(preview,"urls")).size()==2,"actual reader image tap opens matching image and deduplicates gallery URLs");
            WebView original=(WebView)field(preview,"current");check("true".equals(js(original,"document.images[0].naturalWidth===500&&document.images[0].naturalHeight===1800")),"production image response decodes cached first image");
            Button next=(Button)field(preview,"next"),previous=(Button)field(preview,"previous");tap(next);
            await(()->(int)field(preview,"index")==1&&field(preview,"current")!=original&&Boolean.TRUE.equals(field(preview,"settled")));
            WebView later=(WebView)field(preview,"current");check("true".equals(js(later,"document.images[0].naturalWidth===700&&document.images[0].naturalHeight===1000")),"actual next button displays distinct second image");
            check(previous.isEnabled()&&!next.isEnabled(),"last image has exactly one valid previous direction");tap(previous);
            await(()->(int)field(preview,"index")==0&&field(preview,"current")!=later&&Boolean.TRUE.equals(field(preview,"settled")));
            check("true".equals(js((WebView)field(preview,"current"),"document.images[0].naturalWidth===500")),"actual previous button returns to first image");
            AlertDialog dialog=(AlertDialog)field(preview,"dialog");tap(dialog.getButton(AlertDialog.BUTTON_POSITIVE));await(()->field(preview,"current")==null);
            check(!dialog.isShowing(),"actual close button dismisses and releases preview");check(reader==field(owner,"readerWeb")&&position.equals(js(reader,"JSON.stringify({y:scrollY,url:location.href})")),"actual image/gallery/close path preserves original reader and scrolled position");
            check(field(owner,"dynamic")==null,"cached image flow does not start source-page loading");
            check("true".equals(js(reader,"Array.from(document.querySelectorAll('figcaption a')).every(a=>{let r=a.getBoundingClientRect();return r.width>=44&&r.height>=44&&!!a.getAttribute('aria-label')})")),"caption links provide at least 44 CSS px targets and accessible names");
            String captionPosition=tapCaption(reader);await(()->field(owner,"imagePreview")!=preview);ImagePreview caption=(ImagePreview)field(owner,"imagePreview");await(()->Boolean.TRUE.equals(field(caption,"settled")));
            check((int)field(caption,"index")==1&&"true".equals(js((WebView)field(caption,"current"),"document.images[0].naturalWidth===700")),"actual caption link opens its own second image");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(()->field(caption,"current")==null);check(reader==field(owner,"readerWeb")&&captionPosition.equals(js(reader,"JSON.stringify({y:scrollY,url:location.href})")),"system Back closes preview and preserves caption reading position");
            String backgroundPosition=tapCaption(reader);await(()->field(owner,"imagePreview")!=caption);ImagePreview background=(ImagePreview)field(owner,"imagePreview");await(()->Boolean.TRUE.equals(field(background,"settled")));
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);await(()->Boolean.TRUE.equals(field(owner,"wasStopped")));test.waitForIdleSync();
            check(field(background,"current")==null&&Boolean.TRUE.equals(field(background,"closed")),"actual Home releases hidden preview WebView");
            test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));await(owner::hasWindowFocus);
            check(reader==field(owner,"readerWeb")&&backgroundPosition.equals(js(reader,"JSON.stringify({y:scrollY,url:location.href})")),"foreground return preserves original article position");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        finally{MainActivity owner=activity;test.runOnMainSync(()->{if(owner!=null)owner.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
