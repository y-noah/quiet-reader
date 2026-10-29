package app.quietreader.ai;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** A real :ranking process appears inside the five-second PSS reuse window. */
public final class MemoryProcessSetProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private boolean bound;
    private Messenger remote;
    private final CountDownLatch connected=new CountDownLatch(1),hello=new CountDownLatch(1);
    private int workerPid;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){remote=new Messenger(binder);connected.countDown();}public void onServiceDisconnected(ComponentName name){}};
    public MemoryProcessSetProbe(Instrumentation test){this.test=test;}
    private Context context(){return test.getTargetContext();}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean workerListed(){List<ActivityManager.RunningAppProcessInfo> list=((ActivityManager)context().getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();if(list!=null)for(ActivityManager.RunningAppProcessInfo p:list)if(p.processName.equals(context().getPackageName()+":ranking"))return true;return false;}
    private void send(int type,Messenger reply)throws Exception{Message message=Message.obtain(null,type);message.replyTo=reply;remote.send(message);}
    public void run(){
        try{
            long until=SystemClock.elapsedRealtime()+10000;while(workerListed()&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(!workerListed(),"baseline starts without a model worker; no existing process is killed");if(workerListed())throw new IllegalStateException("Existing worker did not finish");
            long start=SystemClock.elapsedRealtime();ProcessMemoryBudget.Snapshot before=ProcessMemoryBudget.sample(context());
            check(before.includesMain&&before.measuredProcesses==1&&before.requestedProcesses==1,"first real PSS sample covers the main process alone");
            Messenger[] inbox={null};test.runOnMainSync(()->{
                inbox[0]=new Messenger(new Handler(Looper.getMainLooper(),message->{if(message.what==3){workerPid=message.getData().getInt("pid");hello.countDown();}return true;}));
                bound=context().bindService(new Intent().setClassName(context().getPackageName(),"app.quietreader.ai.RankingService"),connection,Context.BIND_AUTO_CREATE);
            });
            if(!bound||!connected.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Worker bind failed");send(3,inbox[0]);if(!hello.await(2,TimeUnit.SECONDS))throw new IllegalStateException("Worker identity timeout");
            check(workerPid>0&&workerPid!=android.os.Process.myPid()&&workerListed(),"actual separately bound ranking process is visible to ActivityManager");
            ProcessMemoryBudget.Snapshot after=ProcessMemoryBudget.sample(context());long elapsed=SystemClock.elapsedRealtime()-start;
            report.append("processAppearanceMs=").append(elapsed).append(" beforePssKiB=").append(before.pssKiB).append(" afterPssKiB=").append(after.pssKiB).append('\n');
            check(elapsed<5000,"second sample really occurs inside the old five-second reuse window");
            check(after.requestedProcesses>=2&&after.measuredProcesses>=2&&after.includesMain,"new model process invalidates cached main-only sample and is actually measured");
            ProcessMemoryBudget.Snapshot repeated=ProcessMemoryBudget.sample(context());check(repeated==after,"unchanged process set still reuses the short-lived sample");
            send(2,inbox[0]);test.runOnMainSync(()->{context().unbindService(connection);bound=false;});
            until=SystemClock.elapsedRealtime()+4000;while(workerListed()&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(!workerListed(),"released test worker exits without disturbing app data");
            ProcessMemoryBudget.Snapshot ended=ProcessMemoryBudget.sample(context());
            check(ended.requestedProcesses==1&&ended.measuredProcesses==1&&ended.includesMain,"worker exit also invalidates the previous process-set sample");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{if(bound){try{send(2,null);}catch(Exception ignored){}context().unbindService(connection);bound=false;}});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
