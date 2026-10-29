package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Controlled timeout callback; actual home card, error dialog, rewarming and reader launch. */
final class ReaderStartupFailureProbe {
    private final Instrumentation test;
    private final boolean cancel;
    private DiscoveryActivity home;private Activity reader;private Repository cache;private ExecutorService storage;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReaderStartupFailureProbe(Instrumentation test){this(test,false);}
    ReaderStartupFailureProbe(Instrumentation test,boolean cancel){this.test=test;this.cancel=cancel;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private Object field(Object owner,Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private boolean await(BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+20000;do{if(onUi(condition))return true;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<end);return onUi(condition);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View child=find(((ViewGroup)v).getChildAt(n),text);if(child!=null)return child;}return null;}
    private AccessibilityNodeInfo retry(){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return null;for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("重试准备"))if("重试准备".contentEquals(n.getText()))return n;return null;}
    private void dump(AccessibilityNodeInfo node,int depth){if(node==null||depth>12)return;report.append("UI ").append(node.getClassName()).append(" text=").append(node.getText()).append(" description=").append(node.getContentDescription()).append('\n');for(int n=0;n<node.getChildCount();n++)dump(node.getChild(n),depth+1);}
    void run(){String database="test-ui-reader-failure-"+UUID.randomUUID()+".db";String title="阅读器临时失败后恢复的受控话题";
        Application app=(Application)test.getTargetContext().getApplicationContext();int[] actualStarted={0};List<String> lifecycle=new ArrayList<>();
        Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){
            public void onActivityStarted(Activity activity){actualStarted[0]++;lifecycle.add("start "+activity.getClass().getSimpleName()+" count="+actualStarted[0]);}
            public void onActivityStopped(Activity activity){actualStarted[0]--;lifecycle.add("stop "+activity.getClass().getSimpleName()+" count="+actualStarted[0]);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
        };app.registerActivityLifecycleCallbacks(observer);
        try{
            long now=System.currentTimeMillis();HotCandidate candidate=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/"+now,title,"","",false,1,null,now);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())store.enable(source,source==Source.V2EX);store.upsertSnapshot(Source.V2EX,store.beginRefresh(Source.V2EX),List.of(candidate),now);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            Object controller=field(home,DiscoveryActivity.class,"controller");storage=(ExecutorService)field(controller,DiscoveryController.class,"storage");
            check(await(()->!ReaderStartup.pending()&&field(null,ReaderStartup.class,"blank")!=null&&find(home.getWindow().getDecorView(),title)!=null),"real foreground prepares one reader and the actual fixture card");
            ui(()->{
                cache=new Repository(home);Document body=new Document();body.url=candidate.originalUrl;body.title=title;body.blocks.add(new Block("text","阅读器恢复后的正文。".repeat(60)));ArticleVersion.capture(candidate.readerItem(),body);cache.cacheArticle(body);
                ((Application)test.getTargetContext().getApplicationContext()).onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW);
                ReaderStartup.prepare(test.getTargetContext(),()->{});
                // Invoke the actual timeout callback while its normal pending state is
                // active. No private flags/engine result or source credentials are changed.
                ((Runnable)field(null,ReaderStartup.class,"warmTimeout")).run();
            });
            check(onUi(()->ReaderStartup.failed()&&!ReaderStartup.pending()&&field(null,ReaderStartup.class,"blank")==null),"controlled warm timeout releases the optional view and records failure");
            ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isClickable())card=(View)card.getParent();if(card==null)throw new IllegalStateException("fixture card missing");card.performClick();});test.waitForIdleSync();
            AccessibilityNodeInfo retry=retry();long dialogDeadline=SystemClock.elapsedRealtime()+5000;
            while(retry==null&&SystemClock.elapsedRealtime()<dialogDeadline){Thread.sleep(100);retry=retry();}
            if(retry==null){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();dump(root,0);}
            check(retry!=null,"actual reader failure dialog offers an explicit retry without restarting the App");
            if(retry!=null&&cancel){
                test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
                Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
                try{
                    ui(()->{try{
                        long oldEpoch=(long)field(null,ReaderStartup.class,"startupEpoch");ExecutorService oldWorker=(ExecutorService)field(null,ReaderStartup.class,"startupWorker");
                        Method method=DiscoveryActivity.class.getDeclaredMethod("prepareReader",HotCandidate.class,boolean.class);method.setAccessible(true);method.invoke(home,candidate,true);
                        Method oldWarm=ReaderStartup.class.getDeclaredMethod("warmBlank",android.content.Context.class,ExecutorService.class,long.class);oldWarm.setAccessible(true);oldWarm.invoke(null,test.getTargetContext().getApplicationContext(),oldWorker,oldEpoch);
                        Method oldFailure=ReaderStartup.class.getDeclaredMethod("finish",boolean.class,ExecutorService.class,long.class);oldFailure.setAccessible(true);oldFailure.invoke(null,true,oldWorker,oldEpoch);
                        check(ReaderStartup.pending()&&!ReaderStartup.failed()&&field(null,ReaderStartup.class,"blank")==null,"late warm/error callbacks from the prior attempt cannot allocate a view or fail the new retry");
                        home.finish();
                    }catch(Exception e){throw new IllegalStateException(e);}});
                    reader=test.waitForMonitorWithTimeout(monitor,4000);test.waitForIdleSync();
                    check(reader==null&&monitor.getHits()==0,"finishing home during retry never opens a reader from a late ready callback");
                    check(await(()->(int)field(null,ReaderStartup.class,"foregroundActivities")==0),"canceled retry leaves no foreground owner");
                }finally{test.removeMonitor(monitor);}
            }else if(retry!=null){
                Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
                try{check(retry.performAction(AccessibilityNodeInfo.ACTION_CLICK),"retry is reachable through its real dialog action");reader=test.waitForMonitorWithTimeout(monitor,20000);test.waitForIdleSync();check(reader!=null&&monitor.getHits()==1,"successful rewarming opens exactly one requested unified reader");}
                finally{test.removeMonitor(monitor);}
                check(onUi(()->!ReaderStartup.failed()&&!ReaderStartup.pending()),"successful retry clears the prior failure state");
                ui(()->report.append("RETRY_TRANSITION tracked=").append(field(null,ReaderStartup.class,"foregroundActivities")).append(" observedStarted=").append(actualStarted[0]).append('\n'));
                check(await(()->actualStarted[0]==1&&(int)field(null,ReaderStartup.class,"foregroundActivities")==1),"retry settles to one actually started reader with matching lifecycle count");
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{
            ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();if(cache!=null){cache.releaseArticleMemory();cache.close();}});test.waitForIdleSync();
            try{check(await(()->actualStarted[0]==0&&(int)field(null,ReaderStartup.class,"foregroundActivities")==0),"all actual foreground owners and tracked lifecycle counts return to zero");}catch(Exception error){check(false,"lifecycle cleanup observation failed");}
            ui(()->{report.append("LIFECYCLE ").append(lifecycle).append('\n');app.unregisterActivityLifecycleCallbacks(observer);});
            if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
