package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.TextView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Controlled result publication around an actual update click and SQLite read. */
final class PendingAcceptanceRaceProbe {
    private final Instrumentation test;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    PendingAcceptanceRaceProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void publish(DiscoveryController controller,long id){try{Field f=DiscoveryController.class.getDeclaredField("pending");f.setAccessible(true);f.setLong(controller,id);Method state=DiscoveryController.class.getDeclaredMethod("state");state.setAccessible(true);state.invoke(controller);}catch(Exception error){throw new IllegalStateException(error);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") void run(){
        for(String scenario:List.of("current-neutral","stale-profile","stale-source")){
            String database="test-ui-pending-race-"+UUID.randomUUID()+".db";DiscoveryActivity[] home={null};ExecutorService serial=null;CountDownLatch release=new CountDownLatch(1);
            try{
                long now=System.currentTimeMillis();List<HotCandidate> candidates=List.of(new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990101","家庭NAS如何做好异地数据备份","","",false,1,null,now),new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990102","欧洲火星探测器公布新的地质发现","","",false,2,null,now));
                try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())store.enable(source,source==Source.V2EX);store.upsertSnapshot(Source.V2EX,store.beginRefresh(Source.V2EX),candidates,now);}
                home[0]=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
                DiscoveryController controller=(DiscoveryController)field(home[0],"controller");
                long until=SystemClock.elapsedRealtime()+15000;while(!onUi(()->(boolean)field(controller,"ready")&&((List<?>)field(home[0],"feed")).size()==2)){if(SystemClock.elapsedRealtime()>until){ui(()->report.append("FIXTURE_STATE ready=").append(field(controller,"ready")).append(" rows=").append(((List<?>)field(home[0],"feed")).size()).append('\n'));throw new IllegalStateException("Fixture home unavailable");}Thread.sleep(25);}
                ui(()->controller.interacting(true));
                serial=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");
                Object originalFeed=field(home[0],"feed");long original=(long)field(controller,"snapshot");List<FeedStore.Row> rows=new ArrayList<>((List<FeedStore.Row>)originalFeed),reversed=new ArrayList<>(rows);Collections.reverse(reversed);
                long[] ids=serial.submit(()->{
                    long beforeProfile=store.profile(now).getLong("version"),beforeSource=store.version("sources");long old=store.commitRanking(beforeSource,beforeProfile,now,"cold","all",rows);
                    if(scenario.equals("stale-profile"))store.feedback("controlled-race","MORE","sample",candidates.get(0).id,now,null,candidates.get(0).modelText(),Source.V2EX);
                    if(scenario.equals("stale-source")){
                        List<HotCandidate> updated=new ArrayList<>(candidates);updated.add(new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990103","城市雨洪管理和排水设施改造","","",false,3,null,now+1));
                        store.upsertSnapshot(Source.V2EX,store.beginRefresh(Source.V2EX),updated,now+1);
                        reversed.clear();reversed.addAll(CandidateOrder.arrange(store.candidates(5000,0),new org.json.JSONArray(),false));Collections.reverse(reversed);
                    }
                    long afterSource=store.version("sources"),afterProfile=store.profile(now+1).getLong("version");
                    long newer=store.commitRanking(afterSource,afterProfile,now+1,"personal","all",reversed);
                    return new long[]{old,newer,beforeProfile,beforeSource,afterProfile,afterSource};
                }).get(5,TimeUnit.SECONDS);
                check(ids[1]>ids[0]&&(scenario.equals("current-neutral")||scenario.equals("stale-profile")&&ids[4]>ids[2]||scenario.equals("stale-source")&&ids[5]>ids[3]),scenario+": actual SQLite commits have the intended current or advanced data version");
                CountDownLatch entered=new CountDownLatch(1);serial.submit(()->{entered.countDown();try{if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Controlled storage barrier expired");}catch(InterruptedException error){Thread.currentThread().interrupt();}});
                if(!entered.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Storage barrier not entered");
                ui(()->{publish(controller,ids[0]);((TextView)field(home[0],"updates")).performClick();publish(controller,ids[1]);});
                check(onUi(()->field(home[0],"feed")==originalFeed&&(long)field(controller,"snapshot")==original),scenario+": pending publication and the real update click preserve visible cards while storage is blocked");
                release.countDown();until=SystemClock.elapsedRealtime()+8000;
                while(onUi(()->(long)field(controller,"snapshot")==original&&(long)field(controller,"pending")==ids[1])&&SystemClock.elapsedRealtime()<until)Thread.sleep(25);
                check(onUi(()->(long)field(controller,"snapshot")==ids[1]&&"personal".equals(field(controller,"visibleMode"))),scenario+": a newer personal result superseding the clicked result is actually accepted");
                check(onUi(()->(long)field(controller,"pending")==0&&((List<FeedStore.Row>)field(home[0],"feed")).get(0).eventId.equals(reversed.get(0).eventId)),scenario+": the accepted view uses the new order and clears only the result it consumed");
                long active=serial.submit(store::activeSnapshot).get(5,TimeUnit.SECONDS);
                check(active==ids[1],scenario+": SQLite active identity matches the newer result rather than the older read");
            }catch(Throwable error){check(false,scenario+": "+android.util.Log.getStackTraceString(error));}
            finally{release.countDown();ui(()->{if(home[0]!=null)home[0].finish();});test.waitForIdleSync();if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
