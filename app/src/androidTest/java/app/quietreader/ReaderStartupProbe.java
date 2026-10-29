package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real foreground/background lifecycle and blank WebView ownership; no private state mutation. */
final class ReaderStartupProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final List<ExecutorService> workers=new ArrayList<>();
    ReaderStartupProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private Object field(Object owner,Class<?> type,String name){try{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private WebView blank(){return (WebView)field(null,ReaderStartup.class,"blank");}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private boolean await(long timeout,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;do{if(onUi(condition))return true;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<end);return onUi(condition);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void launch(String database){home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));Object controller=field(home,DiscoveryActivity.class,"controller");workers.add((ExecutorService)field(controller,DiscoveryController.class,"storage"));}
    void run(){String database="test-ui-reader-startup-"+UUID.randomUUID()+".db";WebView[] taken={null};
        try{
            launch(database);
            check(await(20000,()->!ReaderStartup.pending()&&blank()!=null),"initial foreground prepares a real unclaimed blank reader");
            check(onUi(()->blank()!=null&&blank().getSettings().getBlockNetworkLoads()&&!blank().getSettings().getJavaScriptEnabled()),"unclaimed blank cannot issue network loads or JavaScript");
            ui(home::finish);test.waitForIdleSync();
            check(await(5000,()->blank()==null&&(int)field(null,ReaderStartup.class,"foregroundActivities")==0),"leaving the App releases the unclaimed WebView after the grace period");
            check(onUi(()->!ReaderStartup.failed()),"background release is not a provider failure");
            launch(database);
            boolean restored=await(8000,()->blank()!=null&&!ReaderStartup.pending());
            check(restored,"returning to a newly created home restores one unclaimed warm reader");
            ui(()->report.append("RETURN_STATE foreground=").append(field(null,ReaderStartup.class,"foregroundActivities")).append(" engineReady=").append(field(null,ReaderStartup.class,"engineReady")).append(" claimed=").append(field(null,ReaderStartup.class,"claimed")).append(" blank=").append(blank()!=null).append(" pending=").append(ReaderStartup.pending()).append('\n'));
            if(restored){
                WebView[] expected={null};ui(()->{expected[0]=blank();taken[0]=ReaderStartup.take(home);});
                check(taken[0]==expected[0],"first reading claims the exact prepared WebView rather than constructing another");
                check(onUi(()->blank()==null&&!taken[0].getSettings().getBlockNetworkLoads()),"claim transfers the sole warm view and releases its network block");
                ui(()->{taken[0].destroy();taken[0]=null;home.finish();});test.waitForIdleSync();
                check(await(5000,()->(int)field(null,ReaderStartup.class,"foregroundActivities")==0),"claimed-reader owner can stop normally");
                launch(database);Thread.sleep(400);
                check(onUi(()->blank()==null&&!ReaderStartup.pending()),"already claimed startup does not allocate a second speculative WebView");
            }
        }catch(Throwable error){check(false,error.getClass().getSimpleName()+": "+error.getMessage());}
        finally{
            ui(()->{if(taken[0]!=null)taken[0].destroy();if(home!=null)home.finish();});test.waitForIdleSync();
            for(ExecutorService worker:workers)try{if(!worker.awaitTermination(8,TimeUnit.SECONDS))check(false,"storage owner stops");}catch(InterruptedException error){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
