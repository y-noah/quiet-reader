package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Lifecycle / pre-draw observations, not physical frame or ARM performance claims. */
final class FeedStartupProbe {
    private final Instrumentation test;
    private final boolean sampleStacks;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures;
    private DiscoveryActivity activity;
    private long started,preCreated,created,postCreated,resumed,chromeDraw,maxHeartbeatDelay;
    private volatile long cachedDraw,lastHeartbeat;
    private boolean providerDeferredAtCachedDraw;
    private ScheduledExecutorService sampler;
    private final List<String> stalls=Collections.synchronizedList(new ArrayList<>());
    private final Handler main=new Handler(Looper.getMainLooper());
    private ViewTreeObserver.OnPreDrawListener draw;
    private Runnable heartbeat;
    private CountDownLatch cached;
    private final String firstTitle="已缓存首页诊断话题000";
    FeedStartupProbe(Instrumentation test){this(test,true);}
    FeedStartupProbe(Instrumentation test,boolean sampleStacks){this.test=test;this.sampleStacks=sampleStacks;}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static boolean providerDeferred(){try{Field f=ReaderStartup.class.getDeclaredField("startupWorker");f.setAccessible(true);return f.get(null)==null&&ReaderStartup.pending();}catch(Exception error){throw new IllegalStateException(error);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static boolean text(View v,String target){if(v instanceof TextView&&target.contentEquals(((TextView)v).getText()))return true;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)if(text(((ViewGroup)v).getChildAt(i),target))return true;return false;}
    void run(){
        String database="test-ui-feed-startup-"+UUID.randomUUID()+".db";Application app=(Application)test.getTargetContext().getApplicationContext();
        List<ExecutorService> workers=new ArrayList<>();
        Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){
            public void onActivityPreCreated(Activity a,Bundle state){if(a instanceof DiscoveryActivity)preCreated=SystemClock.elapsedRealtime();}
            public void onActivityCreated(Activity a,Bundle state){if(!(a instanceof DiscoveryActivity))return;activity=(DiscoveryActivity)a;created=SystemClock.elapsedRealtime();lastHeartbeat=created;if(sampler!=null)sampler.scheduleAtFixedRate(()->{if(cachedDraw!=0||SystemClock.elapsedRealtime()-lastHeartbeat<100||stalls.size()>=8)return;StringBuilder stack=new StringBuilder();StackTraceElement[] trace=Looper.getMainLooper().getThread().getStackTrace();for(int n=0;n<Math.min(18,trace.length);n++)stack.append(trace[n]).append('\n');String value=stack.toString();if(!stalls.contains(value))stalls.add(value);},25,25,TimeUnit.MILLISECONDS);View root=a.getWindow().getDecorView();
                draw=()->{long now=SystemClock.elapsedRealtime();if(chromeDraw==0)chromeDraw=now;if(cachedDraw==0&&text(root,firstTitle)){providerDeferredAtCachedDraw=providerDeferred();cachedDraw=now;cached.countDown();}return true;};root.getViewTreeObserver().addOnPreDrawListener(draw);
                heartbeat=new Runnable(){long expected=SystemClock.elapsedRealtime();public void run(){long now=SystemClock.elapsedRealtime();lastHeartbeat=now;maxHeartbeatDelay=Math.max(maxHeartbeatDelay,now-expected);if(cachedDraw==0){expected=now+16;main.postDelayed(this,16);}}};main.post(heartbeat);
            }
            public void onActivityResumed(Activity a){if(a instanceof DiscoveryActivity)resumed=SystemClock.elapsedRealtime();}
            public void onActivityPostCreated(Activity a,Bundle state){if(a instanceof DiscoveryActivity)postCreated=SystemClock.elapsedRealtime();}
            public void onActivityStarted(Activity a){} public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
        };
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target only");
            long now=System.currentTimeMillis(),snapshot;List<HotCandidate> pool=new ArrayList<>();List<FeedStore.Row> rows=new ArrayList<>();
            for(int i=0;i<384;i++){String title=i==0?firstTitle:String.format(Locale.ROOT,"已缓存首页诊断话题%03d",i);HotCandidate c=new HotCandidate(Models.Source.ITHOME,"https://www.ithome.com/0/990/"+i+".htm",title,"已经保存的中文文字简介","热度仅保留来源原值",false,i+1,null,now);pool.add(c);rows.add(new FeedStore.Row(c.id,c,"cached startup fixture"));}
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){store.ensureCandidatePolicy();long revision=store.beginRefresh(Models.Source.ITHOME);if(!store.upsertSnapshot(Models.Source.ITHOME,revision,pool,now))throw new IllegalStateException("Fixture commit failed");snapshot=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"cold","all",rows);store.activateSnapshot(snapshot);}
            app.registerActivityLifecycleCallbacks(observer);
            for(String phase:List.of("first-home-in-process","provider-warm-reopen")){
                started=preCreated=created=postCreated=resumed=chromeDraw=cachedDraw=maxHeartbeatDelay=0;cached=new CountDownLatch(1);activity=null;stalls.clear();sampler=sampleStacks?Executors.newSingleThreadScheduledExecutor():null;
                test.runOnMainSync(()->{started=SystemClock.elapsedRealtime();test.getTargetContext().startActivity(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));});
                check(cached.await(15,TimeUnit.SECONDS),phase+" draws exact first cached title without waiting for source collection");
                if(cachedDraw==0)throw new IllegalStateException("Cached first draw missing");
                if(phase.equals("first-home-in-process"))check(providerDeferredAtCachedDraw,"optional reader startup is not dispatched before the cached title preDraw");
                if(sampler!=null){sampler.shutdownNow();sampler.awaitTermination(2,TimeUnit.SECONDS);}
                for(String stack:stalls)report.append("MAIN_STALL ").append(phase).append('\n').append(stack);
                JSONObject value=new JSONObject().put("phase",phase).put("stackSampling",sampleStacks).put("candidateCount",384).put("launchToPreCreateMs",preCreated-started).put("preCreateToCreatedCallbackMs",created-preCreated).put("preCreateToPostCreatedMs",postCreated-preCreated).put("launchToResumedMs",resumed-started).put("chromePreDrawMs",chromeDraw-started).put("cachedTitlePreDrawMs",cachedDraw-started).put("postCreateHeartbeatMaxDelayMs",maxHeartbeatDelay);
                report.append("STARTUP ").append(value).append('\n');
                test.runOnMainSync(()->{try{DiscoveryController controller=(DiscoveryController)field(activity,"controller");controller.interacting(true);workers.add((ExecutorService)field(controller,"storage"));}catch(Exception error){throw new IllegalStateException(error);}if(draw!=null)activity.getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(draw);main.removeCallbacks(heartbeat);});
                try(FeedStore store=new FeedStore(test.getTargetContext(),database)){check(store.activeSnapshot()==snapshot&&store.readSnapshot(snapshot,200,0).size()+store.readSnapshot(snapshot,200,200).size()==384,phase+" retains the original accepted cached snapshot and all members");check(store.activeFeedback(now).length()==0,phase+" does not invent reading preferences");}
                if(phase.equals("first-home-in-process")){long until=SystemClock.elapsedRealtime()+15000;boolean[] pending={true};do{test.runOnMainSync(()->pending[0]=ReaderStartup.pending());if(!pending[0])break;Thread.sleep(50);}while(SystemClock.elapsedRealtime()<until);check(!pending[0]&&!ReaderStartup.failed(),"optional reader startup succeeds before warm comparison");}
                test.runOnMainSync(activity::finish);test.waitForIdleSync();activity=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{
            if(sampler!=null)sampler.shutdownNow();
            test.runOnMainSync(()->{if(heartbeat!=null)main.removeCallbacks(heartbeat);if(activity!=null){if(draw!=null)activity.getWindow().getDecorView().getViewTreeObserver().removeOnPreDrawListener(draw);activity.finish();}});test.waitForIdleSync();app.unregisterActivityLifecycleCallbacks(observer);
            for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
            check(test.getTargetContext().deleteDatabase(database),"isolated startup database removed after controller storage closes");
        }
        report.append("scope=synthetic persisted native feed, source fetch disabled, main-process lifecycle/pre-draw observations; not recommendation completion, physical display frames, cold OS cache or ARM performance\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}

