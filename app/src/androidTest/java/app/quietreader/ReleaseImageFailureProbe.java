package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** R8 reader callback coverage using serialized fixtures, never production classes or reflection. */
final class ReleaseImageFailureProbe {
    private final Instrumentation test;
    private Activity activity;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final AtomicBoolean releaseErrors=new AtomicBoolean(),releaseSlow=new AtomicBoolean();
    private final Set<String> requested=ConcurrentHashMap.newKeySet();
    ReleaseImageFailureProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean value,String label){checks++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');if(!value)throw new IllegalStateException(label);}
    private static String hash(String value)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static WebView web(View root){if(root instanceof WebView)return (WebView)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){WebView result=web(((ViewGroup)root).getChildAt(n));if(result!=null)return result;}return null;}
    private String js(WebView page,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] value={null};
        ui(()->{boolean prior=page.getSettings().getJavaScriptEnabled();page.getSettings().setJavaScriptEnabled(true);page.evaluateJavascript(script,result->{page.getSettings().setJavaScriptEnabled(prior);value[0]=result;done.countDown();});});
        if(!done.await(5,TimeUnit.SECONDS))throw new IOException("DOM callback timeout");return value[0];
    }
    private void await(String label,Callable<Boolean> predicate)throws Exception{long deadline=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<deadline){if(predicate.call())return;Thread.sleep(80);}throw new IOException("Deadline: "+label);}
    private static JSONObject block(String type,String value)throws Exception{return new JSONObject().put("type",type).put("value",value).put("images",new JSONArray()).put("links",new JSONArray());}
    void run(){
        try{
            Context context=test.getTargetContext();
            check("app.quietreader.discoveryprobe".equals(context.getPackageName()),"isolated emulator package only");
            check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual target is non-debuggable minified release");
            String id=UUID.randomUUID().toString(),url="https://sspai.com/post/r8-image-"+id,title="发行阅读图片错误布局检查";
            List<String> images=List.of("https://cdn.sspai.com/r8-http-"+id+".png","https://cdn.sspai.com/r8-invalid-"+id+".png","https://cdn.sspai.com/r8-valid-"+id+".png","https://cdn.sspai.com/r8-slow-"+id+".png");
            byte[] png;Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff3c9b72);try(ByteArrayOutputStream out=new ByteArrayOutputStream()){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);png=out.toByteArray();}finally{bitmap.recycle();}
            URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL address)throws IOException{
                int index=images.indexOf(address.toString());if(index<0)throw new IOException("Unexpected fixture destination");requested.add(address.toString());
                return new HttpURLConnection(address){volatile boolean closed;public void connect(){}public void disconnect(){closed=true;}public boolean usingProxy(){return false;}
                    private void waitRelease()throws IOException{long end=SystemClock.elapsedRealtime()+20000;while(!closed&&SystemClock.elapsedRealtime()<end&&(index<2&&!releaseErrors.get()||index==3&&!releaseSlow.get()))try{Thread.sleep(20);}catch(InterruptedException error){throw new InterruptedIOException();}if(closed)throw new InterruptedIOException();}
                    public int getResponseCode()throws IOException{waitRelease();return index==0?503:200;}
                    public String getHeaderField(String key){return "Content-Type".equalsIgnoreCase(key)?"image/png":null;}
                    public InputStream getInputStream()throws IOException{waitRelease();return new ByteArrayInputStream(index==1?"invalid PNG".getBytes(StandardCharsets.UTF_8):png);}
                };
            }}:null);
            JSONArray blocks=new JSONArray();for(int n=0;n<images.size();n++){JSONObject image=block("image",images.get(n));if(n!=1)image.put("width",1000).put("height",600);blocks.put(image);}
            for(int n=0;n<20;n++)blocks.put(block("text","发行构建阅读位置检查第"+n+"段。这是合成布局样本，用于检查失败图片收紧后，当前阅读段落不会发生位置跳动。"));
            JSONObject document=new JSONObject().put("url",url).put("title",title).put("metadataHash",hash("hot-title-v2\n"+title+"\n")).put("sections",new JSONArray().put(new JSONObject().put("id","r8-image-body").put("label","正文").put("answer",false).put("blocks",blocks)));
            String[] cookies={null};ui(()->cookies[0]=android.webkit.CookieManager.getInstance().getCookie(url));
            String namespace=context.getSharedPreferences("article-cache",0).getString("namespace","initial");
            File root=new File(context.getCacheDir(),"article-bodies-v1");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cache directory unavailable");
            File fixture=new File(root,hash(namespace+"\n"+hash(cookies[0]==null?"":cookies[0])+"\n"+url)+".json");
            try(FileOutputStream out=new FileOutputStream(fixture)){out.write(new JSONObject().put("at",System.currentTimeMillis()).put("document",document).put("related",new JSONArray()).toString().getBytes(StandardCharsets.UTF_8));}
            JSONObject item=new JSONObject().put("source","SSPAI").put("title",title).put("url",url).put("detail","").put("video",false);
            activity=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            WebView[] found={null};await("reader",()->{ui(()->found[0]=web(activity.getWindow().getDecorView()));return found[0]!=null;});WebView page=found[0];
            await("four image markup",()->"4".equals(js(page,"document.querySelectorAll('figure img').length")));
            js(page,"document.querySelectorAll('figure img').forEach(i=>i.loading='eager')");await("all requests",()->requested.size()==4);
            await("valid decode",()->"true".equals(js(page,"document.querySelectorAll('figure img')[2].naturalWidth===16")));
            js(page,"document.querySelectorAll('p[data-reading-anchor]')[4].scrollIntoView({block:'start'})");Thread.sleep(200);
            double before=Double.parseDouble(js(page,"document.querySelectorAll('p[data-reading-anchor]')[4].getBoundingClientRect().top"));
            releaseErrors.set(true);Thread.sleep(1000); // Let native callbacks restore their JS policy before test-only DOM inspection.
            check("true".equals(js(page,"Array.from(document.querySelectorAll('figure')).slice(0,2).every(f=>f.classList.contains('image-unavailable')&&f.getBoundingClientRect().height<=90&&f.querySelector('.image-open').textContent.includes('图片未加载'))")),"R8 HTTP503 and invalid image collapse via actual production callbacks");
            check(Math.abs(before-Double.parseDouble(js(page,"document.querySelectorAll('p[data-reading-anchor]')[4].getBoundingClientRect().top")))<3,"R8 collapse preserves the visible paragraph position");
            check("true".equals(js(page,"!document.querySelectorAll('figure')[2].classList.contains('image-unavailable')&&document.querySelectorAll('figure img')[2].naturalWidth===16")),"R8 successful image stays decoded and visible");
            check("true".equals(js(page,"!document.querySelectorAll('figure')[3].classList.contains('image-unavailable')&&!document.querySelectorAll('figure img')[3].complete")),"R8 pending slow image is not mistaken for a failed one");
            releaseSlow.set(true);await("slow decode",()->"true".equals(js(page,"document.querySelectorAll('figure img')[3].naturalWidth===16")));
            check("true".equals(js(page,"!document.querySelectorAll('figure')[3].classList.contains('image-unavailable')")),"R8 delayed image decodes normally");
            Thread.sleep(600);boolean[] disabled={false};ui(()->disabled[0]=!page.getSettings().getJavaScriptEnabled());check(disabled[0],"R8 reader restores disabled JavaScript after native DOM work");
            js(page,"scrollTo(0,0)");Thread.sleep(200);Bitmap screen=test.getUiAutomation().takeScreenshot();if(screen==null)throw new IOException("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),"r8-image-failure-compact.png"))){screen.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screen.recycle();}
            report.append("FIXTURE ").append(url).append("; synthetic body/cache, real minified reader and image HTTP callbacks; not a source-parser or gallery-retry claim\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{releaseErrors.set(true);releaseSlow.set(true);ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
