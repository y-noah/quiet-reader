package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import app.quietreader.ai.*;
import java.util.*;
import java.util.concurrent.*;

/** Synthetic IPC/process lifecycle probe; it never starts a reader or signs in. */
final class RankingServiceProbe implements AutoCloseable {
    interface Check {void run(boolean good,String label);}
    private final Context context;
    private final Check check;
    private final StringBuilder report;
    private final java.util.function.Consumer<String> memoryProbe;
    private final BlockingQueue<Message> replies=new LinkedBlockingQueue<>();
    private final Messenger receiver=new Messenger(new Handler(Looper.getMainLooper(),message->{if(message.what==RankingService.RETIRING)detach();replies.offer(Message.obtain(message));return true;}));
    private final CountDownLatch connected=new CountDownLatch(1);
    private Messenger service;
    private volatile boolean bound;
    private int pid;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){service=new Messenger(binder);connected.countDown();}
        @Override public void onServiceDisconnected(ComponentName name){detach();}
    };
    RankingServiceProbe(Context context,Check check,StringBuilder report,java.util.function.Consumer<String> memoryProbe){this.context=context;this.check=check;this.report=report;this.memoryProbe=memoryProbe;}
    void bind()throws Exception{
        bound=context.bindService(new Intent(context,RankingService.class),connection,Context.BIND_AUTO_CREATE);
        if(!bound||!connected.await(10,TimeUnit.SECONDS))throw new Exception("Ranking bind timeout");
        send(RankingService.HELLO,new Bundle());pid=await(RankingService.HELLO,10000).getInt("pid");
        check.run(pid!=android.os.Process.myPid(),"ranking has a separate process");
        report.append("RANKING_PID ").append(pid).append('\n');
    }
    void run()throws Exception{
        memoryProbe.accept("worker-bound");
        Bundle request=request("embedding",7,2);send(RankingService.RUN,request);
        int received=0;
        while(true){Message response=next(15000);Bundle data=response.getData();
            check.run(data.getLong("generation")==7&&data.getLong("profileVersion")==3&&data.getLong("sourceRevision")==9,"IPC preserves ranking identity");
            if(response.what==RankingService.ITEM){float[] vector=data.getFloatArray("vector");check.run(vector!=null&&vector.length==512,"IPC carries embedding result");received++;}
            else{check.run(response.what==RankingService.DONE&&data.getInt("completed")==2&&received==2,"bounded embedding batch completes");break;}
        }
        Bundle invalid=request("reranker",8,2);invalid.putString("query","");send(RankingService.RUN,invalid);
        memoryProbe.accept("embedding-complete");
        check.run("INVALID_REQUEST".equals(await(RankingService.ERROR,5000).getString("code")),"invalid request rejected without model work");
        send(RankingService.RUN,request("reranker",9,48));
        Bundle item=await(RankingService.ITEM,15000);
        check.run(Float.isFinite(item.getFloat("logit")),"separate process executes real reranker");
        memoryProbe.accept("reranker-active");
        send(RankingService.RELEASE,new Bundle());
        detach();
        long deadline=SystemClock.elapsedRealtime()+5000;
        while(alive()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(30);
        check.run(!alive(),"release during active rerank removes worker process within 5 seconds");
        check.run(android.os.Process.myPid()!=pid,"main test process survives ranking cancellation");
        memoryProbe.accept("worker-released");
    }
    void idleExit()throws Exception{
        long deadline=SystemClock.elapsedRealtime()+12000;
        while(alive()&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
        check.run(!alive(),"idle ranking worker exits without background polling");
        Thread.sleep(1000);
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        boolean restarted=false;for(ActivityManager.RunningAppProcessInfo p:manager.getRunningAppProcesses())if(p.processName.equals(context.getPackageName()+":ranking"))restarted=true;
        check.run(!restarted,"idle shutdown does not auto-restart bound service");
    }
    private boolean alive(){
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> list=manager.getRunningAppProcesses();
        if(list!=null)for(ActivityManager.RunningAppProcessInfo p:list)if(p.pid==pid)return true;return false;
    }
    private static Bundle request(String kind,long generation,int count){
        Bundle b=new Bundle();b.putString("kind",kind);b.putLong("generation",generation);b.putLong("profileVersion",3);b.putLong("sourceRevision",9);b.putLong("budgetMs",3000);
        b.putString("query","家庭数据备份的实际经验");String[] texts=new String[count],ids=new String[count];
        String title=kind.equals("embedding")?"家庭 NAS 备份如何避免数据丢失？":String.join("",Collections.nCopies(100,"家庭硬盘备份恢复经验"));
        for(int i=0;i<count;i++){texts[i]=title;ids[i]="fixture-"+i;}b.putStringArray("texts",texts);b.putStringArray("ids",ids);return b;
    }
    private void send(int what,Bundle data)throws RemoteException{Message m=Message.obtain(null,what);m.replyTo=receiver;m.setData(data);service.send(m);}
    private Message next(long timeout)throws Exception{Message m=replies.poll(timeout,TimeUnit.MILLISECONDS);if(m==null)throw new Exception("Ranking response timeout");return m;}
    private Bundle await(int what,long timeout)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<deadline){Message m=next(Math.max(1,deadline-SystemClock.elapsedRealtime()));if(m.what==what)return m.getData();if(m.what==RankingService.ERROR)throw new Exception("Ranking error: "+m.getData().getString("code"));}
        throw new Exception("Ranking expected response absent");
    }
    private synchronized void detach(){if(bound){context.unbindService(connection);bound=false;}}
    @Override public void close(){if(bound){try{send(RankingService.RELEASE,new Bundle());}catch(Exception ignored){}detach();}}
}
