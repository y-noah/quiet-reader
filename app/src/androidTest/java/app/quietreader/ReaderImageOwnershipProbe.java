package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Actual reader/gallery lifecycle, production HTTP and controlled slow PNG bytes. */
final class ReaderImageOwnershipProbe {
    private final Instrumentation test;private MainActivity reader;private Repository repo;
    private final List<Response> requests=new CopyOnWriteArrayList<>();
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private static final class Response {final String url;final AtomicBoolean release=new AtomicBoolean(),disconnected=new AtomicBoolean();Response(String url){this.url=url;}}
    ReaderImageOwnershipProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Reader image deadline: "+label);Thread.sleep(35);}}
    private void invoke(String method,Class<?>[] types,Object... args){try{Method target=MainActivity.class.getDeclaredMethod(method,types);target.setAccessible(true);target.invoke(reader,args);}catch(Exception error){throw new IllegalStateException(error);}}
    private String js(String code)throws Exception{return js((WebView)field(reader,"readerWeb"),code);}
    private String js(WebView web,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->{boolean prior=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(prior);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Reader DOM callback missing");return result[0];}
    private void decoded()throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<end){if("true".equals(js("!!document.querySelector('figure img')&&document.querySelector('figure img').naturalWidth===16")))return;Thread.sleep(40);}throw new IllegalStateException("PNG never decoded in reader");}
    private int owners(){synchronized(ReaderImages.shared){Map<?,?> pending=(Map<?,?>)field(ReaderImages.shared,"flights");int owners=0;for(Object flight:pending.values())owners+=((Map<?,?>)field(flight,"owners")).size();return owners;}}
    private void rotate()throws Exception{MainActivity old=reader;Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);try{int orientation=old.getResources().getConfiguration().orientation;ui(()->old.setRequestedOrientation(orientation==1?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Rotated reader absent");reader=(MainActivity)next;}finally{test.removeMonitor(monitor);}await("old reader destroyed",old::isDestroyed);check(onUi(()->field(old,"imageOwner")==null),"destroyed Activity relinquishes its image ownership");}
    private void transport(String image,byte[] png){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){@Override protected URLConnection openConnection(URL target)throws IOException{if(!image.equals(target.toString())&&!(image+"?gallery=second").equals(target.toString()))throw new IOException("Unexpected synthetic image URL");Response response=new Response(target.toString());return new HttpURLConnection(target){
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){response.disconnected.set(true);}
        @Override public int getResponseCode()throws IOException{requests.add(response);long end=SystemClock.elapsedRealtime()+30000;while(!response.release.get()){if(SystemClock.elapsedRealtime()>end)throw new IOException("Slow image deadline");try{Thread.sleep(15);}catch(InterruptedException ignored){}}return 200;}
        @Override public long getContentLengthLong(){return png.length;}
        @Override public InputStream getInputStream(){return new ByteArrayInputStream(png);}
    };}});}
    void run(){String id=Long.toString(System.currentTimeMillis()),url="https://www.thepaper.cn/newsDetail_forward_"+id,image="https://9.9.9.9/qr-image-owner-"+id+".png",secondImage=image+"?gallery=second";Item item=new Item(Source.THEPAPER,"图片请求生命周期合成样本",url,"");
        try{
            Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff406fb0);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();transport(image,bytes.toByteArray());
            Document doc=new Document();doc.url=url;doc.title=item.title;Section body=new Section("image-owner-main","正文",false);body.blocks.add(new Block("text","这是图片加载生命周期合成正文。转屏共享下载，离开时停止无用请求。".repeat(4)));body.blocks.add(new Block("image",image));for(int n=0;n<12;n++)body.blocks.add(new Block("text","第"+n+"段："+"正常阅读文字不应等待图片完成，也不应因为返回前台丢失位置。".repeat(5)));doc.sections.add(body);doc.blocks.addAll(body.blocks);ArticleVersion.capture(item,doc);
            Section hidden=new Section("gallery-extra","回复 · 合成读者",true);hidden.blocks.add(new Block("image",secondImage));doc.sections.add(hidden);doc.blocks.addAll(hidden.blocks);
            ui(()->{repo=new Repository(test.getTargetContext());repo.cacheArticle(doc);ReaderImages.shared.remove(image);});
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("first image request",()->requests.size()==1);Object original=field(reader,"imageOwner");Response pending=requests.get(0);
            rotate();rotate();await("image owner after rotations",()->field(reader,"imageOwner")==original);
            check(requests.size()==1&&!pending.disconnected.get(),"two actual rotations keep one active image socket and one reader owner");
            ui(()->invoke("zoom",new Class<?>[]{String.class,String.class},image,url));await("gallery joins body image",()->owners()==2);
            check(requests.size()==1,"body and actual large-image gallery share the pending image acquisition");
            ui(()->((ImagePreview)field(reader,"imagePreview")).dismiss());await("gallery releases subscription",()->owners()==1);
            check(!pending.disconnected.get(),"closing the gallery preserves the body subscriber and its socket");
            pending.release.set(true);decoded();check("true".equals(js("document.documentElement.scrollWidth<=innerWidth+1")),"rotated reader decodes the shared PNG without horizontal overflow");
            ui(()->{ReaderImages.shared.remove(image);invoke("reloadReader",new Class<?>[]{Document.class},doc);});await("fresh image request before HOME",()->requests.size()==2);Response background=requests.get(1);
            js("(()=>{let p=document.querySelectorAll('p[data-reading-anchor]')[4],r=p.getBoundingClientRect();scrollTo(0,scrollY+r.top+r.height*.33)})()");Thread.sleep(200);
            org.json.JSONObject beforeHome=new org.json.JSONObject(js("(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){let r=a.getBoundingClientRect();if(r.bottom>0&&r.top<=1)return {id:a.id,fraction:-r.top/r.height}}return {id:'',fraction:0}})()"));
            try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent 3");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
            await("actual background",()->(boolean)field(reader,"wasStopped"));await("background image disconnect",background.disconnected::get);
            check(onUi(()->((ReaderImageOwner)field(reader,"imageOwner")).paused())&&owners()==0,"HOME closes every reader image subscription and disconnects the last socket");
            background.release.set(true);Thread.sleep(250);boolean present=false;synchronized(ReaderImages.shared){for(Object entry:((Map<?,?>)field(ReaderImages.shared,"entries")).values())if(image.equals(field(entry,"url")))present=true;}
            String namespace=test.getTargetContext().getSharedPreferences("article-cache",0).getString("namespace","initial");ImageDiskCache disk=new ImageDiskCache(new File(test.getTargetContext().getCacheDir(),"reader-images-v1"));
            check(!present&&disk.read(namespace+'\0'+image+'\0'+url,image,System.currentTimeMillis())==null,"late background PNG cannot populate memory or disk image cache");
            ui(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            await("new image request on foreground return",()->requests.size()==3);
            long positionBy=SystemClock.elapsedRealtime()+5000;boolean restored=false;while(SystemClock.elapsedRealtime()<positionBy){org.json.JSONObject now=new org.json.JSONObject(js("(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){let r=a.getBoundingClientRect();if(r.bottom>0&&r.top<=1)return {id:a.id,fraction:-r.top/r.height}}return {id:'',fraction:0}})()"));if(!beforeHome.optString("id").isEmpty()&&beforeHome.optString("id").equals(now.optString("id"))&&Math.abs(beforeHome.optDouble("fraction")-now.optDouble("fraction"))<.08){restored=true;break;}Thread.sleep(40);}
            check(restored&&!requests.get(2).release.get()&&"true".equals(js("document.readyState!=='complete'")),"foreground image retry restores the same paragraph progress before the slow image finishes");
            requests.get(2).release.set(true);decoded();
            check(requests.size()==3&&onUi(()->!((ReaderImageOwner)field(reader,"imageOwner")).paused()&&field(reader,"reading")==doc),"foreground return restarts the cancelled image once while retaining its prepared body");
            ui(()->invoke("zoom",new Class<?>[]{String.class,String.class},image,url));ImagePreview preview=(ImagePreview)field(reader,"imagePreview");
            ui(()->((android.widget.Button)field(preview,"next")).performClick());await("gallery-only second image request",()->requests.size()==4);Response obsolete=requests.get(3);
            check(obsolete.url.equals(secondImage),"gallery can load a figure from an unopened section without opening its body section");
            ui(()->((android.widget.Button)field(preview,"previous")).performClick());await("obsolete gallery request disconnected",obsolete.disconnected::get);obsolete.release.set(true);Thread.sleep(200);
            check(requests.size()==4&&onUi(()->(int)field(preview,"index")==0),"switching away cancels the obsolete gallery-only socket and reuses the cached previous figure");
            ui(()->((android.widget.Button)field(preview,"next")).performClick());await("revisited second figure retries",()->requests.size()==5);requests.get(4).release.set(true);
            long galleryBy=SystemClock.elapsedRealtime()+8000;boolean shown=false;while(SystemClock.elapsedRealtime()<galleryBy){WebView web=(WebView)field(preview,"current");if(web!=null&&"true".equals(js(web,"document.images.length===1&&document.images[0].naturalWidth===16"))){shown=true;break;}Thread.sleep(40);}
            check(shown,"revisiting a cancelled gallery figure obtains and decodes one fresh image");
            await("gallery retry enabled",()->((AlertDialog)field(preview,"dialog")).getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled());
            ui(()->((AlertDialog)field(preview,"dialog")).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());await("explicit gallery retry request",()->requests.size()==6);Response retry=requests.get(5);ui(preview::dismiss);await("gallery retry cancelled on dismiss",retry.disconnected::get);retry.release.set(true);Thread.sleep(200);
            check(owners()==0&&disk.read(namespace+'\0'+secondImage+'\0'+url,secondImage,System.currentTimeMillis())==null,"closing a pending explicit gallery retry cancels its socket and rejects late disk publication");
            Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot!=null){try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"reader-image-ownership-final.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
        }catch(Throwable error){failures++;report.append("requests=").append(requests.size()).append("\nERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{for(Response response:requests)response.release.set(true);ui(()->{if(reader!=null)reader.finish();if(repo!=null)repo.close();ReaderImages.shared.remove(image);ReaderImages.shared.remove(secondImage);});test.waitForIdleSync();ImageDiskCache disk=new ImageDiskCache(new File(test.getTargetContext().getCacheDir(),"reader-images-v1"));disk.removeUrl(image);disk.removeUrl(secondImage);test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
