package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.net.ConnectivityManager;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Captures are assertions only: no prose injection or production reflection; explicit reread uses the product menu. */
final class ReleaseLiveReaderProbe {
    private final Instrumentation test;
    private Activity reader;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("MINIFIED LIVE READER: independent public BBC paragraphs; no body seeding\n");
    ReleaseLiveReaderProbe(Instrumentation test){this.test=test;}
    private Context context(){return test.getTargetContext();}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean ok,String message){checks++;report.append(ok?"PASS ":"FAIL ").append(message).append('\n');if(!ok)throw new IllegalStateException(message);}
    private static String hash(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private static String norm(String text){return text.replaceAll("[\\s\\p{Z}]+"," ").trim();}
    File cacheFile(String url)throws Exception{
        String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie(url));
        String namespace=context().getSharedPreferences("article-cache",0).getString("namespace","initial");
        // BBC uses the ordinary raw-cookie identity; serialization reference only.
        return new File(context().getCacheDir(),"article-bodies-v1/"+hash(namespace+"\n"+hash(cookie[0]==null?"":cookie[0])+"\n"+url)+".json");
    }
    private static JSONObject read(File file)throws Exception{return new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));}
    private static WebView web(View node){if(node instanceof WebView)return (WebView)node;if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){WebView found=web(((ViewGroup)node).getChildAt(n));if(found!=null)return found;}return null;}
    JSONObject dom(WebView view)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={null};
        ui(()->{boolean scripts=view.getSettings().getJavaScriptEnabled();view.getSettings().setJavaScriptEnabled(true);view.evaluateJavascript("JSON.stringify({text:document.body?document.body.innerText:'',parts:document.querySelectorAll('main .part').length,overflow:document.documentElement.scrollWidth>innerWidth+2,images:Array.from(document.images).filter(i=>i.complete&&i.naturalWidth>0).length,targetWidth:document.querySelector('main .part img')?.naturalWidth||0,imageDetails:Array.from(document.images).slice(0,4).map(i=>({src:i.currentSrc||i.src,width:i.naturalWidth,complete:i.complete,top:i.getBoundingClientRect().top})),players:document.querySelectorAll('video').length})",value->{view.getSettings().setJavaScriptEnabled(scripts);result[0]=value;done.countDown();});});
        if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("DOM callback timed out");
        Object decoded=new JSONTokener(result[0]).nextValue();return decoded instanceof String?new JSONObject((String)decoded):new JSONObject();
    }
    int matched(String text,JSONArray expected)throws Exception{String haystack=norm(text);int count=0;for(int n=0;n<expected.length();n++)if(haystack.contains(norm(expected.getString(n))))count++;return count;}
    /** Screenshot evidence inside the actual WebView rectangle, excluding native bars/scrollbar. No invalidation or forced draw. */
    long captureDrawnBody(WebView page,String name)throws Exception{
        long started=SystemClock.elapsedRealtime();
        while(SystemClock.elapsedRealtime()-started<5000){
            int[] rect=new int[4];ui(()->{int[] location=new int[2];page.getLocationOnScreen(location);rect[0]=location[0];rect[1]=location[1];rect[2]=page.getWidth();rect[3]=page.getHeight();});
            android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Screenshot unavailable");
            try{
                int left=Math.max(0,rect[0])+16,top=Math.max(0,rect[1])+16,right=Math.min(bitmap.getWidth(),rect[0]+rect[2])-16,bottom=Math.min(bitmap.getHeight()-80,rect[1]+rect[3])-16;
                int ink=0;if(left<right&&top<bottom){int background=bitmap.getPixel(left,top);
                    for(int y=top;y<bottom;y+=4)for(int x=left;x<right;x+=4){int pixel=bitmap.getPixel(x,y);int delta=Math.abs(android.graphics.Color.red(pixel)-android.graphics.Color.red(background))+Math.abs(android.graphics.Color.green(pixel)-android.graphics.Color.green(background))+Math.abs(android.graphics.Color.blue(pixel)-android.graphics.Color.blue(background));if(delta>90)ink++;}
                }
                if(ink>200){try(FileOutputStream out=new FileOutputStream(new File(context().getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}return SystemClock.elapsedRealtime()-started;}
            }finally{bitmap.recycle();}
            Thread.sleep(100);
        }
        throw new IllegalStateException("DOM is present but no nonblank body screenshot inside the WebView after5 seconds");
    }
    private static View findNative(View view,String label){
        if(label.equals(String.valueOf(view.getContentDescription()))||view instanceof android.widget.TextView&&label.equals(String.valueOf(((android.widget.TextView)view).getText())))return view;
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=findNative(((ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}return null;
    }
    void run(Bundle args){boolean warm="true".equals(args.getString("reuse","false")),inspect=args.containsKey("inspectIndex"),refresh="true".equals(args.getString("refresh","false"));
        boolean phone="true".equals(args.getString("phone","false"));
        String capture=args.getString("capture","");boolean controlled="true".equals(args.getString("capturedTransport","false"));
        java.util.concurrent.atomic.AtomicInteger capturedFetches=null;
        try{
            check(phone?"app.quietreader".equals(context().getPackageName())&&"PHY110".equals(Build.MODEL)&&Build.VERSION.SDK_INT==36:"app.quietreader.discoveryprobe".equals(context().getPackageName()),"explicit phone diagnostic is confined to the authorized Find X7 Ultra; other runs require isolation");
            if(phone)check(!controlled&&!refresh&&!warm&&inspect&&capture.equals("social"),"phone reader preserves caches and uses an explicit public article without preference markers");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"installed target is a non-debuggable release");
            check(capture.isEmpty()||capture.equals("social"),"public capture selection is whitelisted");check(!refresh||!warm&&!inspect,"explicit reread is separate from offline and inspect modes");
            JSONArray records;try(InputStream in=test.getContext().getAssets().open(capture.equals("social")?"release-live-bbc-social-20261027.json":"release-live-bbc-20261026.json")){records=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("records");}
            android.content.SharedPreferences probe=context().getSharedPreferences("release-live-reader-probe"+(capture.isEmpty()?"":"-"+capture),0);
            int selected=-1;
            if(inspect){selected=Integer.parseInt(args.getString("inspectIndex"));check(!warm&&selected>=0&&selected<records.length(),"diagnostic existing-body selection is explicit and is not a cold acquisition claim");}
            else if(warm){selected=probe.getInt("selected",-1);check(selected>=0&&selected<records.length(),"warm run has a previously fetched public article");}
            else if(refresh)selected=0;
            else for(int n=0;n<records.length();n++){String url=records.getJSONObject(n).getString("url");if(!cacheFile(url).exists()&&!context().getSharedPreferences("reading-history",0).contains(url)){selected=n;break;}}
            check(selected>=0,"select a valid public article without removing existing data");
            JSONObject sample=records.getJSONObject(selected);String url=sample.getString("url"),title=sample.getString("title");JSONArray expected=sample.getJSONArray("expectedParagraphs");
            if(controlled){
                check(capture.equals("social")&&!inspect&&(warm||refresh),"captured response diagnostic requires isolated reread or offline reuse");
                if(!warm)capturedFetches=CapturedBbcTransport.install(test,url);report.append("TRANSPORT captured public HTML only; not live availability, TLS, image decode or account certification\n");
            }
            File before=cacheFile(url);long savedAt=warm?probe.getLong("cachedAt",0):0,savedInode=warm?probe.getLong("inode",0):0;
            if(warm){
                long until=SystemClock.elapsedRealtime()+5000;ConnectivityManager manager=context().getSystemService(ConnectivityManager.class);
                while(manager.getActiveNetwork()!=null&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
                check(manager.getActiveNetwork()==null,"offline reuse has no active system network");
                check(before.isFile()&&read(before).getLong("at")==savedAt&&android.system.Os.stat(before.getPath()).st_ino==savedInode,"offline run starts with the exact acquired body");
            }else if(!inspect&&!refresh)check(!before.exists(),"cold target has no disk body before opening");
            report.append("LIVE_TARGET ").append(url).append(" expectedParagraphs=").append(expected.length()).append(" reuse=").append(warm).append(" inspect=").append(inspect).append(" explicitReread=").append(refresh).append('\n');
            JSONObject item=new JSONObject().put("source","BBC").put("url",url).put("title",title).put("detail","").put("video",false);
            JSONObject candidate=new JSONObject().put("source","BBC").put("url",url).put("title",title).put("snippet","").put("heat","").put("video",false).put("rank",selected+1).put("publishedAt",JSONObject.NULL).put("observedAt",System.currentTimeMillis());
            long openedAt=System.currentTimeMillis(),started=SystemClock.elapsedRealtime();
            Intent launch=new Intent().setClassName(context().getPackageName(),"app.quietreader.MainActivity").putExtra("discovery_item",item.toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if(!phone)launch.putExtra("discovery_candidate",candidate.toString());
            reader=test.startActivitySync(launch);
            if(refresh){
                test.waitForIdleSync();ui(()->{View more=findNative(reader.getWindow().getDecorView(),"更多选项");if(more==null||!more.performClick())throw new IllegalStateException("Reread menu missing");});
                test.waitForIdleSync();long menuDeadline=SystemClock.elapsedRealtime()+5000;boolean clicked=false;
                while(!clicked&&SystemClock.elapsedRealtime()<menuDeadline){android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo option:root.findAccessibilityNodeInfosByText("重新读取")){if(!"重新读取".equals(String.valueOf(option.getText())))continue;android.view.accessibility.AccessibilityNodeInfo target=option;while(target!=null&&!target.isClickable())target=target.getParent();if(target!=null)clicked=target.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);if(clicked)break;}if(!clicked)Thread.sleep(50);}
                check(clicked,"explicit product reread requests fresh acquisition instead of reusing an earlier Debug body");
            }
            WebView[] view={null};JSONObject observed=null;long until=SystemClock.elapsedRealtime()+40000;
            while(SystemClock.elapsedRealtime()<until){ui(()->view[0]=web(reader.getWindow().getDecorView()));if(view[0]!=null){observed=dom(view[0]);if(observed.optInt("parts")>0&&matched(observed.optString("text"),expected)==expected.length())break;}Thread.sleep(100);}
            if(observed!=null)report.append("BODY_DIAGNOSTIC parts=").append(observed.optInt("parts")).append(" matched=").append(matched(observed.optString("text"),expected)).append('/').append(expected.length()).append(" textLength=").append(observed.optString("text").length()).append(" images=").append(observed.optJSONArray("imageDetails")).append(" prefix=").append(JSONObject.quote(observed.optString("text").substring(0,Math.min(400,observed.optString("text").length())))).append('\n');
            check(observed!=null&&observed.optInt("parts")>0&&matched(observed.optString("text"),expected)==expected.length(),"common reader DOM contains every captured paragraph including the tail");
            report.append("LIVE_DOM elapsedMs=").append(SystemClock.elapsedRealtime()-started).append(" paragraphs=").append(expected.length()).append(controlled?"; captured-response/cache path, not real network or presented-frame timing\n":inspect||warm?"; existing cache permitted, not cold acquisition or presented-frame timing\n":"; acquisition path includes network/cache work, not presented-frame timing\n");
            File body=cacheFile(url);until=SystemClock.elapsedRealtime()+5000;while(!body.isFile()&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
            check(body.isFile(),controlled?"production reader saves a body parsed from the captured response":"production reader saves a real acquired body");JSONObject stored=read(body),document=stored.getJSONObject("document");
            check(url.equals(document.getString("url"))&&document.getJSONArray("sections").getJSONObject(0).getString("id").startsWith("bbc-article-"),"saved body has native BBC parser identity and exact article URL");
            StringBuilder prose=new StringBuilder();JSONArray sections=document.getJSONArray("sections");for(int s=0;s<sections.length();s++){JSONArray blocks=sections.getJSONObject(s).getJSONArray("blocks");for(int b=0;b<blocks.length();b++)prose.append(blocks.getJSONObject(b).optString("value")).append('\n');}
            check(matched(prose.toString(),expected)==expected.length(),"saved native body contains every captured paragraph");
            if(!inspect)check(warm?stored.getLong("at")==savedAt:stored.getLong("at")>=openedAt,warm?"offline reopening preserves acquisition time":refresh?"explicit reread acquisition time is after opening":"cold acquisition time is after opening");
            if(controlled&&!warm)check(capturedFetches!=null&&capturedFetches.get()>0,"minified native parser actually consumed the captured HTTP response, not an old body");
            if(sample.optBoolean("hasImages")&&!controlled){
                report.append("IMAGE_BEFORE_VIEW ").append(observed.optJSONArray("imageDetails")).append('\n');
                CountDownLatch positioned=new CountDownLatch(1);ui(()->{boolean scripts=view[0].getSettings().getJavaScriptEnabled();view[0].getSettings().setJavaScriptEnabled(true);view[0].evaluateJavascript("(()=>{const image=document.querySelector('main .part img');if(image)image.scrollIntoView({block:'center'});return !!image})()",value->{view[0].getSettings().setJavaScriptEnabled(scripts);positioned.countDown();});});
                if(!positioned.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Image positioning callback missing");
                until=SystemClock.elapsedRealtime()+15000;while(observed.optInt("targetWidth")==0&&SystemClock.elapsedRealtime()<until){Thread.sleep(100);observed=dom(view[0]);}
                report.append("IMAGE_AFTER_VIEW ").append(observed.optJSONArray("imageDetails")).append('\n');
                check(observed.optInt("targetWidth")>0,"the specific first article image brought into view actually decodes");
            }
            check(!observed.optBoolean("overflow")&&observed.optInt("players")==0,"no horizontal overflow or unsolicited video player");
            if(warm)check(android.system.Os.stat(body.getPath()).st_ino==savedInode,"offline reopening does not rewrite the acquired body");
            else check(probe.edit().putInt("selected",selected).putLong("cachedAt",stored.getLong("at")).putLong("inode",android.system.Os.stat(body.getPath()).st_ino).commit(),"retain actual acquisition identity for a separate-process offline run");
            android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Screenshot unavailable");
            try(FileOutputStream out=new FileOutputStream(new File(context().getExternalFilesDir(null),warm?"release-live-reader-offline.png":"release-live-reader-cold.png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{
            if(failures>0&&reader!=null)try{android.graphics.Bitmap failed=test.getUiAutomation().takeScreenshot();if(failed!=null)try(FileOutputStream out=new FileOutputStream(new File(context().getExternalFilesDir(null),"release-live-reader-failure.png"))){failed.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{failed.recycle();}}catch(Exception captureFailure){report.append("FAILURE_SCREENSHOT ").append(captureFailure.getClass().getSimpleName()).append('\n');}
            ui(()->{if(reader!=null&&!reader.isFinishing())reader.finish();});test.waitForIdleSync();
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
