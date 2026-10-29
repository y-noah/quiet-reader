package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Requests and destruction before a first native frame must not strand deferred startup. */
final class ReaderNativeFrameProbe {
    private final Instrumentation test;private final boolean cancel;
    private final StringBuilder report=new StringBuilder();private int checks,failures;
    private DiscoveryActivity home;private ExecutorService storage;
    ReaderNativeFrameProbe(Instrumentation test,boolean cancel){this.test=test;this.cancel=cancel;}
    private static Object field(Object owner,Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};test.runOnMainSync(()->result[0]=condition.getAsBoolean());return result[0];}
    private boolean await(long timeout,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;do{if(onUi(condition))return true;Thread.sleep(25);}while(SystemClock.elapsedRealtime()<end);return onUi(condition);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){
        String database="test-ui-native-first-"+UUID.randomUUID()+".db";
        Application app=(Application)test.getTargetContext().getApplicationContext();CountDownLatch created=new CountDownLatch(1);AtomicInteger ready=new AtomicInteger();
        boolean[] initiallyDeferred={false},explicitDispatched={false};RuntimeException[] callbackError={null};
        Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){
            public void onActivityPostCreated(Activity activity,Bundle state){if(!(activity instanceof DiscoveryActivity))return;home=(DiscoveryActivity)activity;
                try{
                    Object controller=field(home,DiscoveryActivity.class,"controller");storage=(ExecutorService)field(controller,DiscoveryController.class,"storage");
                    initiallyDeferred[0]=ReaderStartup.pending()&&field(null,ReaderStartup.class,"deferredStart")!=null&&field(null,ReaderStartup.class,"startupWorker")==null;
                    if(cancel)activity.finish();else{ReaderStartup.prepare(app,ready::incrementAndGet);explicitDispatched[0]=field(null,ReaderStartup.class,"deferredStart")==null&&field(null,ReaderStartup.class,"startupWorker")!=null;}
                }catch(RuntimeException error){callbackError[0]=error;}finally{created.countDown();}
            }
            public void onActivityCreated(Activity a,Bundle state){} public void onActivityStarted(Activity a){} public void onActivityResumed(Activity a){} public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
        };
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target only");
            app.registerActivityLifecycleCallbacks(observer);
            test.runOnMainSync(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database).putExtra("test_skip_fetch",true)));
            check(created.await(5,TimeUnit.SECONDS),"actual home completes onCreate before the first native frame");if(callbackError[0]!=null)throw callbackError[0];
            check(initiallyDeferred[0],"initial optional reader work is deferred with no provider worker dispatched");
            if(cancel){
                check(await(5000,()->!ReaderStartup.pending()),"destroying home before its first frame retires the deferred pending state");
                check(onUi(()->field(null,ReaderStartup.class,"startupWorker")==null&&field(null,ReaderStartup.class,"deferredStart")==null),"cancelled first-frame startup never dispatches a provider worker");
                check(onUi(()->!ReaderStartup.failed()&&field(null,ReaderStartup.class,"blank")==null),"first-frame cancellation neither creates a WebView nor records provider failure");
            }else{
                check(explicitDispatched[0],"explicit prepare before a native frame releases the gate immediately");
                check(await(20000,()->ready.get()==1&&!ReaderStartup.pending()&&!ReaderStartup.failed()&&field(null,ReaderStartup.class,"blank")!=null),"explicit early prepare completes exactly once with a real warm reader");
                test.runOnMainSync(home::finish);
                check(await(5000,()->field(null,ReaderStartup.class,"blank")==null&&(int)field(null,ReaderStartup.class,"foregroundActivities")==0),"early explicit reader is released when its foreground owner exits");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{
            test.runOnMainSync(()->{if(home!=null&&!home.isDestroyed())home.finish();});test.waitForIdleSync();app.unregisterActivityLifecycleCallbacks(observer);
            if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            check(!test.getTargetContext().getDatabasePath(database).exists()||test.getTargetContext().deleteDatabase(database),"isolated empty-start database is absent after its owner closes");
        }
        report.append("scope=actual Activity lifecycle before native first draw, isolated empty feed; not physical presented frames or ARM timings\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
