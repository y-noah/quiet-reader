package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import java.util.concurrent.*;

/** Real service release -> immediate successor bind, without test sleeps. */
final class RankingHandoffProbe {
    private final Instrumentation test;private RankingClient current;private String revision;
    private final StringBuilder report=new StringBuilder("REAL RANKING CLIENT IMMEDIATE HANDOFF\n");
    private final CountDownLatch done=new CountDownLatch(1);private int completed,items,checks,failures,cancelledResults,cancelledClosed;private final int[] pids=new int[2];
    RankingHandoffProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private Bundle request(int index){Bundle request=new Bundle();request.putString("kind","embedding");request.putString("modelRevision",revision);request.putStringArray("texts",new String[]{"家庭备份与软件开发的中文测试"});request.putStringArray("ids",new String[]{"handoff-"+index});request.putLong("generation",index+1);request.putLong("profileVersion",1);request.putLong("sourceRevision",1);request.putLong("budgetMs",1000);return request;}
    private void cancelledWait(){
        RankingClient cancelled=new RankingClient(test.getTargetContext(),new RankingClient.Listener(){public void onResult(int kind,Bundle result){cancelledResults++;}public void onClosed(){cancelledClosed++;}});
        cancelled.submit(request(99));
        try{java.lang.reflect.Field waiting=RankingClient.class.getDeclaredField("waitingFor"),bound=RankingClient.class.getDeclaredField("bound");waiting.setAccessible(true);bound.setAccessible(true);check(waiting.get(cancelled)!=null&&!bound.getBoolean(cancelled),"successor waits for the real retiring binder before creating any binding");}catch(Exception error){throw new IllegalStateException(error);}finally{cancelled.close();}
    }
    private void start(int index){
        boolean[] finished={false};RankingClient[] owned=new RankingClient[1];
        owned[0]=new RankingClient(test.getTargetContext(),new RankingClient.Listener(){
            public void onResult(int kind,Bundle result){
                if(kind==RankingService.HELLO){pids[index]=result.getInt("pid");report.append("HELLO client=").append(index).append(" pid=").append(pids[index]).append('\n');}
                if(kind==RankingService.ITEM){items++;check(result.getLong("generation")==index+1&&result.getFloatArray("vector")!=null,"client "+index+": real embedding retains generation identity");}
                if(kind==RankingService.DONE){finished[0]=true;completed++;report.append("DONE client=").append(index).append('\n');owned[0].close();if(index==0){cancelledWait();start(1);}else done.countDown();}
                if(kind==RankingService.ERROR){report.append("ERROR client=").append(index).append(" code=").append(result.getString("code")).append('\n');done.countDown();}
            }
            public void onClosed(){report.append("CLOSED client=").append(index).append(" completed=").append(finished[0]).append('\n');if(!finished[0])done.countDown();}
        });current=owned[0];owned[0].submit(request(index));
    }
    void run(){
        try{revision=ModelFiles.prepare(test.getTargetContext()).revision();test.runOnMainSync(()->start(0));check(done.await(20,TimeUnit.SECONDS),"handoff reaches a terminal callback within 20 seconds");test.waitForIdleSync();check(completed==2&&items==2,"both immediate clients complete real inference without a retry");check(pids[0]!=0&&pids[1]!=0&&pids[0]!=pids[1],"successor uses a fresh worker after predecessor retires");check(cancelledClosed==1&&cancelledResults==0,"cancelled waiter never binds or receives a delayed result after worker death");}
        catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{if(current!=null)current.close();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
