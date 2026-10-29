package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.widget.TextView;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual cold-profile Controller results and real update clicks; isolated replay only. */
final class EventControllerProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    EventControllerProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+60000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Deadline: "+label);Thread.sleep(25);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") void run(){
        if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated package required");
        for(String scenario:List.of("current","profile-change","source-change")){
            String database="test-ui-event-controller-"+UUID.randomUUID()+".db";
            DiscoveryActivity[] home={null};ExecutorService serial=null;
            try{
                JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-holdout-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                Map<Models.Source,List<HotCandidate>> sources=new EnumMap<>(Models.Source.class);
                for(int n=0;n<fixture.getJSONArray("pairs").length();n++)for(String side:List.of("left","right")){HotCandidate c=HotCandidate.fromJson(fixture.getJSONArray("pairs").getJSONObject(n).getJSONObject(side));sources.computeIfAbsent(c.source,k->new ArrayList<>()).add(c);}
                long now=System.currentTimeMillis();try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Map.Entry<Models.Source,List<HotCandidate>> entry:sources.entrySet())seed.upsertSnapshot(entry.getKey(),seed.beginRefresh(entry.getKey()),entry.getValue(),now);}
                home[0]=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
                DiscoveryController controller=(DiscoveryController)field(home[0],"controller");
                serial=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");
                await("cold result",()->(long)field(controller,"pending")>0&&(long)field(controller,"completedProfile")>=0);
                Object[] visible={null};long[] ids={0,0};ui(()->{visible[0]=field(home[0],"feed");ids[0]=(long)field(controller,"snapshot");ids[1]=(long)field(controller,"pending");});
                List<FeedStore.Row> prepared=serial.submit(()->store.readSnapshot(ids[1],200,0)).get(5,TimeUnit.SECONDS);
                check(onUi(()->((List<?>)visible[0]).size()==24&&(long)field(controller,"snapshot")==ids[0]),scenario+": actual cold home remains at 24 while result is pending");
                check(prepared.size()==23&&serial.submit(()->store.snapshotInfo(ids[1]).getString("mode").equals("cold")&&store.activeFeedback(System.currentTimeMillis()).length()==0).get(5,TimeUnit.SECONDS),scenario+": actual Controller prepares 23 events with no invented interests");
                FeedStore.Row merged=prepared.stream().filter(row->row.entries.size()>1).findFirst().orElseThrow();
                if(scenario.equals("profile-change"))serial.submit(()->store.feedback("controller-split","SEPARATE","event",EventTargets.capture(merged,false),System.currentTimeMillis(),null,merged.candidate.title)).get(5,TimeUnit.SECONDS);
                if(scenario.equals("source-change"))serial.submit(()->{
                    List<HotCandidate> changed=new ArrayList<>(sources.get(Models.Source.ZHIHU));
                    changed.add(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/99009399","合成测试：城市古树保护的实地观察","","",false,99,null,System.currentTimeMillis()));
                    store.upsertSnapshot(Models.Source.ZHIHU,store.beginRefresh(Models.Source.ZHIHU),changed,System.currentTimeMillis());return null;
                }).get(5,TimeUnit.SECONDS);
                ui(()->((TextView)field(home[0],"updates")).performClick());
                if(!scenario.equals("current")){
                    await("obsolete pending rejected",()->(long)field(controller,"pending")!=ids[1]);
                    check(onUi(()->field(home[0],"feed")==visible[0]&&(long)field(controller,"snapshot")==ids[0]),scenario+": update click rejects obsolete grouped snapshot without moving visible cards");
                    await("current recomputation",()->(long)field(controller,"pending")>0&&(long)field(controller,"completedProfile")>=0);
                    ui(()->((TextView)field(home[0],"updates")).performClick());
                }
                await("accepted",()->(long)field(controller,"snapshot")!=ids[0]&&(long)field(controller,"pending")==0);
                int expected=scenario.equals("current")?23:24;
                check(onUi(()->((List<?>)field(home[0],"feed")).size()==expected),scenario+": real update click displays current grouped/split/source count "+expected);
                long[] accepted={0};ui(()->accepted[0]=(long)field(controller,"snapshot"));
                check(serial.submit(()->store.activeSnapshot()==accepted[0]&&store.snapshotInfo(accepted[0]).getLong("sourceVersion")==store.version("sources")&&store.snapshotInfo(accepted[0]).getLong("profileVersion")==store.profile(System.currentTimeMillis()).getLong("version")&&store.readSnapshot(ids[0],200,0).size()==24).get(5,TimeUnit.SECONDS),scenario+": SQLite active identity and revisions match, original snapshot remains frozen");
            }catch(Throwable e){check(false,scenario+": "+android.util.Log.getStackTraceString(e));}
            finally{ui(()->{if(home[0]!=null)home[0].finish();});test.waitForIdleSync();if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        }
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=isolated x86_64 Controller replay; no ARM, personal relevance or live account acceptance\n").toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
