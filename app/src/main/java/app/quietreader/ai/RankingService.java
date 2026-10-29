package app.quietreader.ai;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;

/** Private bound worker. No alarms, sticky restart, foreground service or network.
 * One bounded request at a time; the UI owns generation checks and publication.
 * The process retires on release, client death, unbind, trim or eight idle seconds.
 */
public final class RankingService extends Service {
    public static final int RUN=1,RELEASE=2,HELLO=3,ITEM=10,DONE=11,ERROR=12,RETIRING=13;
    private final Handler main=new Handler(Looper.getMainLooper(),this::message);
    private final Messenger endpoint=new Messenger(main);
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private volatile LocalModelEngine engine;
    private String engineRevision="";
    private Future<?> task;
    private boolean busy,retiring;
    private boolean recoveryAttempted;
    private IBinder owner;
    private Messenger ownerMessenger;
    private final IBinder.DeathRecipient death=()->main.post(this::retire);
    private final Runnable idle=this::retire;

    @Override public IBinder onBind(Intent intent){main.removeCallbacks(idle);main.postDelayed(idle,8000);return endpoint.getBinder();}
    @Override public boolean onUnbind(Intent intent){retire();return false;}
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    private boolean message(Message msg){
        if(msg.what==RELEASE){retire();return true;}
        if(msg.replyTo==null||retiring)return true;
        if(owner==null){owner=msg.replyTo.getBinder();ownerMessenger=msg.replyTo;try{owner.linkToDeath(death,0);}catch(RemoteException e){retire();return true;}}
        if(!owner.equals(msg.replyTo.getBinder()))return true;
        Bundle request=msg.getData();
        if(msg.what==HELLO){Bundle hello=new Bundle();hello.putInt("pid",android.os.Process.myPid());send(msg.replyTo,HELLO,hello);return true;}
        if(msg.what!=RUN)return true;
        Messenger reply=msg.replyTo;
        if(busy){error(reply,request,"BUSY");return true;}
        String kind=request.getString("kind","");
        String[] texts=request.getStringArray("texts"),ids=request.getStringArray("ids");
        String query=request.getString("query","");
        if(!valid(kind,texts,ids,query)){error(reply,request,"INVALID_REQUEST");return true;}
        busy=true;main.removeCallbacks(idle);
        long budget=Math.max(50,Math.min(3000,request.getLong("budgetMs",2000)));
        task=worker.submit(()->{
            int completed=0;
            try{
                long loadStart=SystemClock.elapsedRealtime(),loadCpuStart=android.os.Process.getElapsedCpuTime();
                ModelFiles files=ModelFiles.forRevision(this,request.getString("modelRevision",""));
                if(!files.installed())throw new IllegalStateException("MODEL_NOT_INSTALLED");
                if(engine==null||!engineRevision.equals(files.revision())){if(engine!=null)engine.close();engine=new LocalModelEngine(files.directory(),files.manifest());engineRevision=files.revision();}
                boolean recovered=false;
                try{engine.load(kind);}
                catch(Exception loadFailure){
                    if(recoveryAttempted||Thread.currentThread().isInterrupted()||loadFailure instanceof java.io.InterruptedIOException)throw loadFailure;
                    recoveryAttempted=true;
                    // Rebuild only APK-trusted resources, once per worker lifetime.
                    engine.close();engine=null;files.repair();
                    if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("Model recovery cancelled");
                    engine=new LocalModelEngine(files.directory(),files.manifest());engine.load(kind);recovered=true;
                }
                long loadMs=SystemClock.elapsedRealtime()-loadStart;
                long loadCpuMs=android.os.Process.getElapsedCpuTime()-loadCpuStart;
                long deadline=SystemClock.elapsedRealtime()+budget;
                for(int i=0;i<texts.length&&!Thread.currentThread().isInterrupted();i++){
                    if(SystemClock.elapsedRealtime()>=deadline)break;
                    Bundle output=identity(request);output.putString("id",ids[i]);
                    long start=SystemClock.elapsedRealtime(),cpu=Debug.threadCpuTimeNanos(),processCpu=android.os.Process.getElapsedCpuTime();
                    if(kind.equals("embedding"))output.putFloatArray("vector",engine.embed(texts[i]));
                    else{LocalModelEngine.RerankResult score=engine.rerankResult(query,texts[i]);output.putFloat("logit",score.logit);output.putBoolean("inputComplete",score.complete);output.putInt("inputTokens",score.originalTokens);output.putInt("usedTokens",score.usedTokens);}
                    output.putLong("elapsedMs",SystemClock.elapsedRealtime()-start);
                    output.putLong("cpuNanos",Debug.threadCpuTimeNanos()-cpu);
                    output.putLong("processCpuMs",android.os.Process.getElapsedCpuTime()-processCpu);
                    send(reply,ITEM,output);completed++;
                }
                Bundle done=identity(request);done.putInt("completed",completed);done.putLong("loadMs",loadMs);
                done.putLong("loadCpuMs",loadCpuMs);
                done.putBoolean("recovered",recovered);
                done.putBoolean("partial",completed<texts.length);main.post(()->finishRequest(reply,DONE,done));
            }catch(Exception ex){Bundle failure=identity(request);failure.putString("code",ex instanceof IllegalStateException?"MODEL_NOT_INSTALLED":"MODEL_EXECUTION_FAILED");main.post(()->finishRequest(reply,ERROR,failure));}
        });
        return true;
    }
    private void finishRequest(Messenger reply,int kind,Bundle data){busy=false;if(retiring)return;main.postDelayed(idle,8000);send(reply,kind,data);}
    static boolean valid(String kind,String[] texts,String[] ids,String query){
        if(!kind.equals("embedding")&&!kind.equals("reranker")||texts==null||ids==null||texts.length==0||texts.length>48||texts.length!=ids.length||query.length()>512)return false;
        if(kind.equals("reranker")&&query.trim().isEmpty())return false;
        Set<String> seen=new HashSet<>();int total=query.length();
        for(int i=0;i<texts.length;i++){
            if(texts[i]==null||texts[i].length()>4096||ids[i]==null||ids[i].isEmpty()||ids[i].length()>128||!seen.add(ids[i]))return false;
            total+=texts[i].length();
        }
        return total<=50_000;
    }
    private static Bundle identity(Bundle request){Bundle b=new Bundle();b.putString("modelRevision",request.getString("modelRevision",""));b.putLong("generation",request.getLong("generation"));b.putLong("profileVersion",request.getLong("profileVersion"));b.putLong("sourceRevision",request.getLong("sourceRevision"));b.putLong("batch",request.getLong("batch"));return b;}
    private void error(Messenger reply,Bundle request,String code){Bundle b=identity(request);b.putString("code",code);send(reply,ERROR,b);}
    private void send(Messenger reply,int what,Bundle data){Message m=Message.obtain(null,what);m.setData(data);try{reply.send(m);}catch(RemoteException e){main.post(this::retire);}}
    private void retire(){
        if(retiring)return;retiring=true;main.removeCallbacks(idle);
        // Clients must unbind before process exit to prevent BIND_AUTO_CREATE restart.
        if(ownerMessenger!=null)send(ownerMessenger,RETIRING,new Bundle());
        LocalModelEngine current=engine;if(current!=null)current.cancel();
        if(task!=null)task.cancel(true);worker.shutdownNow();
        if(owner!=null){owner.unlinkToDeath(death,0);owner=null;}
        // Native session loading need not respond to Java interruption. The process
        // boundary is intentional; never wait for a native thread on the UI thread.
        main.postDelayed(()->{
            ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
            if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)
                if(p.pid==android.os.Process.myPid()&&p.processName.equals(getPackageName()+":ranking")){android.os.Process.killProcess(p.pid);return;}
            stopSelf();
        },100);
    }
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW)retire();}
    @Override public void onLowMemory(){super.onLowMemory();retire();}
    @Override public void onDestroy(){retire();super.onDestroy();}
}
