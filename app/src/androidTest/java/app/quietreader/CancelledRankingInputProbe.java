package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Isolated queued-input cancellation, real Controller/SQLite, no model binding seam. */
final class CancelledRankingInputProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;
    private DiscoveryController controller;
    private ExecutorService serial;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    CancelledRankingInputProbe(Instrumentation test){this.test=test;}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)failures++;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private long watermark(FeedStore store){try(Cursor c=store.getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key='profile_time'",null)){return c.moveToFirst()?c.getLong(0):-1;}}
    private void begin(){try{Method m=DiscoveryController.class.getDeclaredMethod("beginRecommendation");m.setAccessible(true);m.invoke(controller);}catch(Exception e){throw new IllegalStateException(e);}}
    void run(){
        String database="test-ui-cancel-input-"+UUID.randomUUID()+".db";CountDownLatch release=null;
        try{
            Context context=test.getTargetContext();
            if(!context.getPackageName().equals("app.quietreader.discoveryprobe")||!Build.MODEL.startsWith("sdk_gphone")||Build.VERSION.SDK_INT!=36||(context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new IllegalStateException("Isolated debuggable API36 emulator required");
            long now=System.currentTimeMillis();
            try(FeedStore seed=new FeedStore(context,database)){seed.upsertSnapshot(Models.Source.ZHIHU,seed.beginRefresh(Models.Source.ZHIHU),List.of(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/99009601","合成测试：城市古树保护观察","","",false,1,null,now)),now);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(context,DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database).putExtra("test_skip_fetch",true));
            controller=(DiscoveryController)field(home,"controller");serial=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");
            ui(()->controller.interacting(true));
            long end=SystemClock.elapsedRealtime()+10000;
            boolean ready=false;while(SystemClock.elapsedRealtime()<end){boolean[] value={false};ui(()->{try{value[0]=(boolean)field(controller,"ready")&&!ReaderStartup.pending();}catch(Exception e){throw new IllegalStateException(e);}});if(value[0]){ready=true;break;}Thread.sleep(25);}
            if(!ready)throw new IllegalStateException("Home not ready");
            for(String scenario:List.of("current","stop","superseded")){
                ui(()->{controller.start();controller.interacting(true);controller.memoryPressure();});
                for(int n=0;n<3;n++){serial.submit(()->{}).get(5,TimeUnit.SECONDS);test.waitForIdleSync();}
                CountDownLatch entered=new CountDownLatch(1);release=new CountDownLatch(1);CountDownLatch gate=release;long[] before={0};
                serial.execute(()->{before[0]=watermark(store);entered.countDown();try{if(!gate.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Input gate timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();}});
                if(!entered.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Storage gate not entered");Thread.sleep(25);
                long[] epoch={0,0};
                ui(()->{try{
                    // Remove only the interaction flag to invoke the actual private
                    // preparation entry deterministically before the worker gate.
                    Field f=DiscoveryController.class.getDeclaredField("interacting");f.setAccessible(true);f.setBoolean(controller,false);
                    epoch[0]=(long)field(controller,"recommendationEpoch");begin();epoch[1]=(long)field(controller,"recommendationEpoch");
                    if(scenario.equals("stop"))controller.stop();
                    if(scenario.equals("superseded"))controller.interacting(true);
                }catch(Exception e){throw new IllegalStateException(e);}});
                check(epoch[1]>epoch[0],scenario+": actual preparation entry passed its admission gates and queued work");
                release.countDown();release=null;
                long after=serial.submit(()->watermark(store)).get(5,TimeUnit.SECONDS);
                report.append("INPUT_WATERMARK scenario=").append(scenario).append(" before=").append(before[0]).append(" after=").append(after).append('\n');
                check(scenario.equals("current")?after>before[0]:after==before[0],scenario+": "+(scenario.equals("current")?"positive control performs the real profile read":"cancelled queued preparation performs no profile read"));
                ui(controller::stop);serial.submit(()->{}).get(5,TimeUnit.SECONDS);test.waitForIdleSync();
                check(serial.submit(()->{try(Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM feedback",null)){return c.moveToFirst()?c.getLong(0):-1;}}).get(5,TimeUnit.SECONDS)==0,scenario+": no invented preference feedback");
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(e));}
        finally{
            if(release!=null)release.countDown();ui(()->{if(home!=null)home.finish();});test.waitForIdleSync();
            if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("scope=isolated queued storage input, not already-running work, model/battery/phone acceptance\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
