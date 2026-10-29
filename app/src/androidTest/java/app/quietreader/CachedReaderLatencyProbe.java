package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import app.quietreader.discovery.HotCandidate;
import static app.quietreader.Models.*;

/** Focused synthetic cached reads, without startActivitySync/waitForIdle in the measured interval.
 * Observation callbacks include emulator scheduling; they are not physical frame timings. */
final class CachedReaderLatencyProbe {
    private final Instrumentation test;private final boolean warm;
    private volatile MainActivity reader;
    private DiscoveryActivity home;private Repository cache;private ExecutorService storage;
    private volatile long began,created,postCreated,resumed;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final List<String> urls=new ArrayList<>();
    private final List<java.io.File> files=new ArrayList<>();
    CachedReaderLatencyProbe(Instrumentation test,boolean warm){this.test=test;this.warm=warm;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private Object field(Object owner,Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private long now(){return SystemClock.elapsedRealtime();}
    private void await(String label,java.util.function.BooleanSupplier condition)throws Exception{long end=now()+20000;while(!condition.getAsBoolean()){if(now()>end)throw new IllegalStateException("Timed out: "+label);Thread.sleep(25);}}
    private String js(String expression)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={""};
        ui(()->{WebView w=(WebView)field(reader,MainActivity.class,"readerWeb");if(w==null){done.countDown();return;}boolean enabled=w.getSettings().getJavaScriptEnabled();w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript(expression,value->{result[0]=value;w.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});
        if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("DOM callback timeout");return result[0];
    }
    void run(){String database="test-ui-cached-latency-"+UUID.randomUUID()+".db";Application app=(Application)test.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityPreCreated(Activity a,Bundle b){if(a instanceof MainActivity)created=now();}
            public void onActivityCreated(Activity a,Bundle b){}
            public void onActivityPostCreated(Activity a,Bundle b){if(a instanceof MainActivity){postCreated=now();reader=(MainActivity)a;}}
            public void onActivityStarted(Activity a){}
            public void onActivityResumed(Activity a){if(a instanceof MainActivity)resumed=now();}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){}
        };
        app.registerActivityLifecycleCallbacks(lifecycle);
        try{
            report.append("SYNTHETIC cached body; warmHome=").append(warm).append("; no models/network; DOM and visual callbacks are observations, not physical frames\n");
            if(warm){
                home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
                storage=(ExecutorService)field(field(home,DiscoveryActivity.class,"controller"),DiscoveryController.class,"storage");
                await("warm blank",()->{boolean[] ready={false};ui(()->ready[0]=!ReaderStartup.pending()&&!ReaderStartup.failed()&&field(null,ReaderStartup.class,"blank")!=null);return ready[0];});
                check(true,"normal home prepares the real blank reader before measured opens");
            }
            ui(()->cache=new Repository(test.getTargetContext()));
            for(int index=0;index<3;index++){
                boolean disk=index==1;String marker="缓存首屏诊断正文"+index;
                Item item=new Item(Source.ITHOME,"缓存阅读延迟检查"+index,"https://www.ithome.com/0/cached-latency-"+System.currentTimeMillis()+"-"+index+".htm","");urls.add(item.url);
                Document doc=new Document();doc.url=item.url;doc.title=item.title;for(int p=0;p<45;p++)doc.blocks.add(new Block("text",marker+"，这是预先准备的文字段落，用于区分缓存读取、页面创建和实际正文显示。段落"+p));ArticleVersion.capture(item,doc);
                ui(()->{cache.cacheArticle(doc);String key=new ArticleDiskCache(test.getTargetContext()).namespace()+"\n"+ArticleDiskCache.session(item.url)+"\n"+item.url;files.add(new java.io.File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(key)+".json"));});
                ((ExecutorService)field(null,ArticleDiskCache.class,"writer")).submit(()->{}).get(10,TimeUnit.SECONDS);
                if(disk)ui(cache::releaseArticleMemory);
                boolean[] memory={false};ui(()->memory[0]=cache.cachedArticle(item)!=null);check(memory[0]!=disk,"expected "+(disk?"disk-only":"memory")+" cache before open "+index);
                reader=null;created=postCreated=resumed=0;
                HotCandidate candidate=new HotCandidate(item.source,item.url,item.title,item.snippet,item.detail,false,index+1,null,System.currentTimeMillis());
                ui(()->{began=now();if(warm){try{java.lang.reflect.Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(home,candidate);}catch(Exception e){throw new IllegalStateException(e);}}else test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database));});
                await("reader resumed",()->reader!=null&&resumed>0);
                long dom=0;long deadline=now()+15000;
                while(now()<deadline){String page=(String)field(reader,MainActivity.class,"readerPageUrl");if("true".equals(js("location.href==="+JSONObject.quote(page)+"&&document.readyState==='complete'&&document.body.innerText.includes("+JSONObject.quote(marker)+")"))){dom=now();break;}Thread.sleep(25);}
                check(dom>0,"current app-owned DOM displays exact cached body "+index);
                CountDownLatch visible=new CountDownLatch(1);long[] visual={0};
                ui(()->{WebView web=(WebView)field(reader,MainActivity.class,"readerWeb");web.postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){visual[0]=now();visible.countDown();}});web.invalidate();});
                check(visible.await(5,TimeUnit.SECONDS),"cached body reaches WebView visual callback "+index);
                check(field(reader,MainActivity.class,"dynamic")==null&&field(reader,MainActivity.class,"pendingArticle")==null,"cache hit avoids source fetch and source WebView "+index);
                JSONObject row=new JSONObject().put("index",index).put("cache",disk?"disk":"memory").put("preCreateMs",created-began).put("onCreateMs",postCreated-created).put("resumedMs",resumed-began).put("domObservedMs",dom>0?dom-began:-1).put("visualObservedMs",visual[0]>0?visual[0]-began:-1);
                report.append("LATENCY ").append(row).append('\n');
                ui(reader::finish);test.waitForIdleSync();reader=null;
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{
            ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();if(cache!=null){cache.releaseArticleMemory();cache.close();}});test.waitForIdleSync();app.unregisterActivityLifecycleCallbacks(lifecycle);
            try{((ExecutorService)field(null,ArticleDiskCache.class,"writer")).submit(()->{}).get(10,TimeUnit.SECONDS);}catch(Exception ignored){}
            try{((ExecutorService)field(null,ReadingLanguageRecorder.class,"writer")).submit(()->{}).get(10,TimeUnit.SECONDS);if(storage!=null)storage.awaitTermination(8,TimeUnit.SECONDS);}catch(Exception ignored){}
            for(java.io.File file:files)file.delete();for(String url:urls)test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
