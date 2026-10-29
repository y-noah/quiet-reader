package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import org.json.JSONObject;
import static app.quietreader.Models.*;

/** Two instrumented processes; script disables radios before restore. Synthetic private image. */
final class ImageOfflineProbe {
    private final Instrumentation test;private final String phase,id,url,imageUrl;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC body/image; actual disk, CookieManager, restart and offline reader\n");
    ImageOfflineProbe(Instrumentation test,String phase,String id){this.test=test;this.phase=phase;this.id=id;url="https://www.cls.cn/detail/"+id;imageUrl="https://qr-offline-"+id+".invalid/image.png";}
    private Object field(Object owner,String key){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Offline image DOM timeout");return value[0];}
    private void cookie(boolean present)throws Exception{CountDownLatch done=new CountDownLatch(1);test.runOnMainSync(()->{CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setCookie(imageUrl,"qr_offline="+(present?"synthetic-session":"")+"; Path=/; Secure"+(present?"":"; Max-Age=0"),ok->done.countDown());});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Cookie setup timeout");CookieManager.getInstance().flush();}
    void run(){MainActivity reader=null;
        try{
            if(id==null||!id.matches("[0-9]{13}"))throw new IOException("Invalid synthetic identity");
            if(phase.equals("prepare")){
                cookie(true);Document body=new Document();body.url=url;body.title="重启断网图文恢复检查";body.blocks.add(new Block("text","已获取的图片应在进程重启、网络关闭后继续可读。这是合成验收内容。"));Block image=new Block("image",imageUrl);image.imageWidth=500;image.imageHeight=1000;body.blocks.add(image);Item item=new Item(Source.CLS,body.title,url,"");ArticleVersion.capture(item,body);
                test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(body);repo.close();});
                ArticleDiskCache bodies=new ArticleDiskCache(test.getTargetContext());long until=SystemClock.elapsedRealtime()+5000;ArticleDiskCache.Hit hit=null;while(SystemClock.elapsedRealtime()<until&&(hit=bodies.read(url,bodies.namespace(),ArticleDiskCache.session(url),true,System.currentTimeMillis()))==null)Thread.sleep(50);check(hit!=null,"synthetic body actually persisted before process stop");
                Bitmap bitmap=Bitmap.createBitmap(500,1000,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff82aacc);ByteArrayOutputStream output=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,output);bitmap.recycle();
                ReaderImages acquired=new ReaderImages(u->CookieManager.getInstance().getCookie(u),(u,ref,scoped)->{if(!String.valueOf(scoped.apply(u)).contains("qr_offline=synthetic-session"))throw new IOException("Missing synthetic session");return output.toByteArray();},2*1024*1024);acquired.attach(test.getTargetContext());check(ImageType.detect(acquired.load(imageUrl,url))!=null,"acquired image written through production image cache with synthetic cookie scope");
            }else{
                check(((java.util.Map<?,?>)field(ReaderImages.shared,"entries")).isEmpty(),"new process starts without memory image entries");
                Repository probe=new Repository(test.getTargetContext());long until=SystemClock.elapsedRealtime()+5000;while(!probe.offline()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);check(probe.offline(),"Android reports no active network before opening reader");probe.close();
                check(String.valueOf(CookieManager.getInstance().getCookie(imageUrl)).contains("qr_offline=synthetic-session"),"synthetic image cookie survives process restart");
                Item item=new Item(Source.CLS,"重启断网图文恢复检查",url,"");reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity owner=reader;WebView web=null;boolean decoded=false;until=SystemClock.elapsedRealtime()+10000;
                while(SystemClock.elapsedRealtime()<until){web=(WebView)field(owner,"readerWeb");if(web!=null&&"true".equals(js(web,"document.readyState==='complete'&&document.images.length===1&&document.images[0].naturalWidth===500&&document.images[0].naturalHeight===1000"))){decoded=true;break;}Thread.sleep(100);}
                check(decoded,"offline reader decodes previously acquired image from disk after restart");if(!decoded)throw new IOException("Disk image did not reach offline reader");check(field(owner,"dynamic")==null,"offline restored body does not open source WebView");
                ReaderImages.shared.evictAll();WebView target=web;js(target,"document.querySelector('figure a').scrollIntoView({block:'center'})");Thread.sleep(200);JSONObject point=new JSONObject(js(target,"(()=>{const r=document.querySelector('figure img').getBoundingClientRect();return {x:r.left+r.width/2,y:Math.max(1,r.top)+40}})()"));float[] screen={0,0};test.runOnMainSync(()->{int[] xy=new int[2];target.getLocationOnScreen(xy);screen[0]=xy[0]+(float)point.optDouble("x")*target.getScale();screen[1]=xy[1]+(float)point.optDouble("y")*target.getScale();});long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,0,screen[0],screen[1],0),up=android.view.MotionEvent.obtain(now,now+60,1,screen[0],screen[1],0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
                ImagePreview preview=null;until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){preview=(ImagePreview)field(owner,"imagePreview");if(preview!=null&&Boolean.TRUE.equals(field(preview,"settled")))break;Thread.sleep(50);}check(preview!=null&&"true".equals(js((WebView)field(preview,"current"),"document.images[0].naturalWidth===500")),"actual offline image tap restores large preview after memory eviction");
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        finally{MainActivity owner=reader;if(owner!=null)test.runOnMainSync(owner::finish);if(!phase.equals("prepare")){try{new ImageDiskCache(new File(test.getTargetContext().getCacheDir(),"reader-images-v1")).removeUrl(imageUrl);cookie(false);}catch(Exception e){failures++;}}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
