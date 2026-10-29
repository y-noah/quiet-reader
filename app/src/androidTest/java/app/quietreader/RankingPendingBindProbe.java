package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import app.quietreader.ai.*;
import java.util.*;
import java.util.concurrent.*;

/** Delay delivery of a real platform connection, then cancel and replace it. */
final class RankingPendingBindProbe {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder("PENDING REAL RANKING BIND CANCELLATION\n");
    private final CountDownLatch done=new CountDownLatch(1);
    private RankingClient cancelled,successor;
    private DeferredContext context;
    private int checks,failures,cancelledResults,cancelledClosed,items,completed,waiterResults,waiterClosed;
    private String revision;
    RankingPendingBindProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private final class DeferredContext extends ContextWrapper {
        final Map<ServiceConnection,ServiceConnection> bridges=new HashMap<>();
        final CountDownLatch connected=new CountDownLatch(1);
        ServiceConnection held;ComponentName name;IBinder binder;int binds;
        DeferredContext(Context base){super(base);}
        @Override public Context getApplicationContext(){return this;}
        @Override public boolean bindService(Intent intent,ServiceConnection connection,int flags){
            boolean defer=++binds==1;
            ServiceConnection bridge=new ServiceConnection(){
                public void onServiceConnected(ComponentName n,IBinder b){if(defer){held=connection;name=n;binder=b;connected.countDown();}else connection.onServiceConnected(n,b);}
                public void onServiceDisconnected(ComponentName n){connection.onServiceDisconnected(n);}
                public void onBindingDied(ComponentName n){connection.onBindingDied(n);}
                public void onNullBinding(ComponentName n){connection.onNullBinding(n);}
            };
            bridges.put(connection,bridge);return super.bindService(intent,bridge,flags);
        }
        @Override public void unbindService(ServiceConnection connection){ServiceConnection bridge=bridges.remove(connection);if(bridge==null)throw new IllegalStateException("Unknown binding");super.unbindService(bridge);}
        void deliver(){held.onServiceConnected(name,binder);held=null;}
    }
    private Bundle request(int generation){Bundle b=new Bundle();b.putString("kind","embedding");b.putString("modelRevision",revision);b.putStringArray("texts",new String[]{"中文家庭数据备份的实际经验"});b.putStringArray("ids",new String[]{"pending-"+generation});b.putLong("generation",generation);b.putLong("profileVersion",1);b.putLong("sourceRevision",1);b.putLong("budgetMs",1000);return b;}
    private void timeoutCase()throws Exception{
        DeferredContext delayed=new DeferredContext(test.getTargetContext());int[] closed={0},results={0};RankingClient[] pair=new RankingClient[2];
        RankingClient.Listener listener=new RankingClient.Listener(){public void onResult(int kind,Bundle result){results[0]++;}public void onClosed(){closed[0]++;}};
        test.runOnMainSync(()->{pair[0]=new RankingClient(delayed,listener);pair[0].submit(request(4));});
        if(!delayed.connected.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout scenario did not obtain real binding");
        try{test.runOnMainSync(()->{
            pair[0].close();pair[1]=new RankingClient(delayed,listener);pair[1].submit(request(5));
            try{java.lang.reflect.Field field=RankingClient.class.getDeclaredField("releaseTimeout");field.setAccessible(true);((Runnable)field.get(pair[0])).run();}catch(Exception error){throw new IllegalStateException(error);}
            check(delayed.binds==1&&delayed.bridges.isEmpty(),"controlled retirement timeout detaches the pending real binding without starting a replacement");
            delayed.deliver();
            check(closed[0]==2&&results[0]==0,"unknown retirement closes the waiter once and ignores subsequent connection delivery");
        });}finally{test.runOnMainSync(()->{for(RankingClient client:pair)if(client!=null)client.close();});}
    }
    void run(){
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Isolated package required");
            revision=ModelFiles.prepare(test.getTargetContext()).revision();context=new DeferredContext(test.getTargetContext());
            test.runOnMainSync(()->{cancelled=new RankingClient(context,new RankingClient.Listener(){public void onResult(int kind,Bundle result){cancelledResults++;}public void onClosed(){cancelledClosed++;}});cancelled.submit(request(1));});
            check(context.connected.await(10,TimeUnit.SECONDS),"real service binds while application connection delivery is deferred");
            test.runOnMainSync(()->{
                cancelled.close();
                cancelled.close();
                RankingClient waiter=new RankingClient(context,new RankingClient.Listener(){public void onResult(int kind,Bundle result){waiterResults++;}public void onClosed(){waiterClosed++;}});
                waiter.submit(request(3));waiter.close();
                successor=new RankingClient(context,new RankingClient.Listener(){
                    public void onResult(int kind,Bundle result){if(kind==RankingService.ITEM){items++;check(result.getLong("generation")==2&&result.getFloatArray("vector")!=null,"successor receives actual generation-2 embedding");}if(kind==RankingService.DONE){completed++;successor.close();done.countDown();}if(kind==RankingService.ERROR){report.append("ERROR ").append(result.getString("code")).append('\n');done.countDown();}}
                    public void onClosed(){if(completed==0)done.countDown();}
                });
                successor.submit(request(2));
                check(context.binds==1,"successor creates no binding until cancelled pending connection is retired");
                context.deliver();
            });
            check(done.await(20,TimeUnit.SECONDS),"successor reaches a terminal callback within 20 seconds");test.waitForIdleSync();
            check(completed==1&&items==1,"successor completes real inference without a retry");
            check(cancelledClosed==1&&cancelledResults==0,"cancelled pending client closes once and receives no late result");
            check(waiterClosed==1&&waiterResults==0,"cancelled successor waiter receives no model output or repeated closure");
            test.runOnMainSync(()->check(context.bridges.isEmpty(),"both real bindings are released"));
            timeoutCase();
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{test.runOnMainSync(()->{if(cancelled!=null)cancelled.close();if(successor!=null)successor.close();});}
        report.append("scope=controlled delayed delivery of real Android binding and actual local inference; not phone or energy acceptance\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
