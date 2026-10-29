package app.quietreader.ai;

import android.content.*;
import android.os.*;
import java.util.*;

/** Main-thread bound connection. No automatic retries or reconnects on process death.
 * Release on reading, background, thermal/power restrictions, or owner destruction.
 */
public final class RankingClient implements AutoCloseable {
    public interface Listener {void onResult(int kind,Bundle result);void onClosed();}
    private final Context context;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Messenger inbox=new Messenger(new Handler(Looper.getMainLooper(),this::received));
    private Messenger service;
    // Main-thread ownership: a released worker must actually die before a new
    // binding can reuse its process and be killed by its pending retirement.
    private static IBinder retiringService;
    // A connection may already exist in Android before its main-thread callback
    // reaches us. Keep that binding just long enough to obtain/release its binder;
    // otherwise onUnbind can kill an immediate successor in the same process.
    private static final Set<RankingClient> pendingReleases=new LinkedHashSet<>();
    private static final Set<RankingClient> pendingReleaseWaiters=new LinkedHashSet<>();
    private IBinder waitingFor;
    private IBinder.DeathRecipient waitingDeath;
    private boolean bound,closed,pendingRelease;
    private Bundle pending;
    private final Runnable timeout=this::close;
    private final Runnable releaseTimeout=()->{releaseConnection();finishPendingRelease(false);};
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){
            if(closed){if(pendingRelease){service=new Messenger(binder);releaseConnection();finishPendingRelease(true);}return;}
            service=new Messenger(binder);handler.removeCallbacks(timeout);send(RankingService.HELLO,new Bundle());
            if(pending!=null){Bundle request=pending;pending=null;send(RankingService.RUN,request);}
        }
        @Override public void onServiceDisconnected(ComponentName name){disconnected(true);}
        @Override public void onBindingDied(ComponentName name){disconnected(true);}
        @Override public void onNullBinding(ComponentName name){disconnected(false);}
    };
    public RankingClient(Context context,Listener listener){this.context=context.getApplicationContext();this.listener=listener;}
    public void submit(Bundle request){
        mainThread();if(closed)throw new IllegalStateException("Ranking client is closed");
        if(service!=null){send(RankingService.RUN,new Bundle(request));return;}
        if(pending!=null)throw new IllegalStateException("Ranking bind already has a request");
        pending=new Bundle(request);
        if(!bound){handler.postDelayed(timeout,10000);beginBind();}
    }
    private void beginBind(){
        if(closed||bound||pending==null)return;
        if(!pendingReleases.isEmpty()){pendingReleaseWaiters.add(this);return;}
        pendingReleaseWaiters.remove(this);
        IBinder retiring=retiringService;
        if(retiring!=null&&retiring.isBinderAlive()){
            waitingFor=retiring;waitingDeath=()->handler.post(this::retirementFinished);
            try{retiring.linkToDeath(waitingDeath,0);}catch(RemoteException alreadyDead){retirementFinished();}
            return;
        }
        if(retiringService==retiring)retiringService=null;
        bound=context.bindService(new Intent(context,RankingService.class),connection,Context.BIND_AUTO_CREATE);if(!bound)close();
    }
    private void retirementFinished(){
        IBinder previous=waitingFor;clearRetirementWait();if(retiringService==previous)retiringService=null;
        beginBind();
    }
    private void clearRetirementWait(){
        if(waitingFor!=null&&waitingDeath!=null)try{waitingFor.unlinkToDeath(waitingDeath,0);}catch(java.util.NoSuchElementException ignored){}
        waitingFor=null;waitingDeath=null;
    }
    private void disconnected(boolean dead){
        if(pendingRelease){releaseConnection();finishPendingRelease(dead);}
        else{if(service==null&&bound){context.unbindService(connection);bound=false;}close();}
    }
    private void releaseConnection(){
        if(service!=null&&service.getBinder().isBinderAlive())retiringService=service.getBinder();
        send(RankingService.RELEASE,new Bundle());service=null;
        if(bound){context.unbindService(connection);bound=false;}
    }
    private void finishPendingRelease(boolean knownRetirement){
        if(!pendingRelease)return;
        pendingRelease=false;handler.removeCallbacks(releaseTimeout);pendingReleases.remove(this);
        if(knownRetirement&&!pendingReleases.isEmpty())return;
        List<RankingClient> waiting=new ArrayList<>(pendingReleaseWaiters);pendingReleaseWaiters.clear();
        // An unknown/failed handshake must not start a replacement automatically.
        for(RankingClient client:waiting)if(knownRetirement)client.beginBind();else client.close();
    }
    private boolean received(Message message){
        if(closed)return true;
        if(message.what==RankingService.RETIRING){close();return true;}
        listener.onResult(message.what,new Bundle(message.getData()));return true;
    }
    private void send(int kind,Bundle data){
        if(service==null)return;Message message=Message.obtain(null,kind);message.setData(data);message.replyTo=inbox;
        try{service.send(message);}catch(RemoteException ex){close();}
    }
    private static void mainThread(){if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Ranking connection requires main thread");}
    @Override public void close(){
        mainThread();if(closed)return;closed=true;handler.removeCallbacksAndMessages(null);pending=null;clearRetirementWait();pendingReleaseWaiters.remove(this);
        if(bound&&service==null){pendingRelease=true;pendingReleases.add(this);handler.postDelayed(releaseTimeout,10000);}
        else releaseConnection();
        listener.onClosed();
    }
}
