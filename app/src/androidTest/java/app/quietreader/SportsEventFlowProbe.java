package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real source-evidence grouping, home actions and SQLite; no model scores injected. */
final class SportsEventFlowProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    SportsEventFlowProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Deadline: "+label);Thread.sleep(25);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") void run(){
        String database="test-ui-sports-"+UUID.randomUUID()+".db";DiscoveryActivity[] home={null};ExecutorService serial=null;
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated package required");
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-fresh-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            List<HotCandidate> candidates=new ArrayList<>();Map<Models.Source,List<HotCandidate>> bySource=new EnumMap<>(Models.Source.class);
            for(int index:new int[]{3,4})for(String side:List.of("left","right")){HotCandidate c=HotCandidate.fromJson(fixture.getJSONArray("pairs").getJSONObject(index).getJSONObject(side));candidates.add(c);bySource.computeIfAbsent(c.source,k->new ArrayList<>()).add(c);}
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Map.Entry<Models.Source,List<HotCandidate>> entry:bySource.entrySet())seed.upsertSnapshot(entry.getKey(),seed.beginRefresh(entry.getKey()),entry.getValue(),System.currentTimeMillis());}
            home[0]=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            DiscoveryController controller=(DiscoveryController)field(home[0],"controller");serial=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");
            await("initial evidence cards",()->(boolean)field(controller,"ready")&&((List<?>)field(home[0],"feed")).size()==2);ui(()->controller.interacting(true));
            List<FeedStore.Row>[] visible=new List[]{null};long[] original={0};ui(()->{visible[0]=new ArrayList<>((List<FeedStore.Row>)field(home[0],"feed"));original[0]=(long)field(controller,"snapshot");});
            check(visible[0].size()==2&&visible[0].stream().allMatch(row->row.entries.size()==2),"real homepage groups four recorded sports entries into two cards");
            check(serial.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==0&&store.readSnapshot(original[0],200,0).size()==2).get(5,TimeUnit.SECONDS),"cold SQLite snapshot groups evidence without fabricated interests");
            List<FeedStore.Row> restored=serial.submit(()->store.readSnapshot(original[0],200,0)).get(5,TimeUnit.SECONDS);
            for(Models.Source source:List.of(Models.Source.TIEBA,Models.Source.ZHIHU))check(SourceFilter.apply(restored,source.name()).size()==2&&SourceFilter.apply(restored,source.name()).stream().allMatch(row->row.candidate.source==source&&row.entries.size()==2),"SQLite source filter preserves actual reader entries: "+source);
            FeedStore.Row selected=visible[0].get(0);String member=selected.entries.get(1).id;
            serial.submit(()->{store.anchor(original[0],member,-17);return null;}).get(5,TimeUnit.SECONDS);
            check(serial.submit(()->EventGroups.matches(store.readSnapshot(original[0],200,0).get(0),member)&&store.snapshotInfo(original[0]).getString("anchor").equals(member)&&store.snapshotInfo(original[0]).getInt("offset")==-17).get(5,TimeUnit.SECONDS),"non-primary member anchor and offset survive SQLite restore");
            ui(()->controller.separate(selected));await("explicit split",()->((List<?>)field(home[0],"feed")).size()==3);
            check(serial.submit(()->store.activeFeedback(System.currentTimeMillis()).getJSONObject(0).getString("action").equals("SEPARATE")).get(5,TimeUnit.SECONDS),"actual Controller split action persists and displays three cards");
            check(serial.submit(()->store.readSnapshot(original[0],200,0).size()==2&&store.readSnapshot(original[0],200,0).stream().allMatch(row->row.entries.size()==2)).get(5,TimeUnit.SECONDS),"first split leaves retained original snapshot membership unchanged");
            String split=serial.submit(()->store.activeFeedback(System.currentTimeMillis()).getJSONObject(0).getString("id")).get(5,TimeUnit.SECONDS);
            ui(()->controller.undo(split));await("split undo",()->((List<?>)field(home[0],"feed")).size()==2);check(true,"actual Controller undo restores two sports groups");
            for(String action:List.of("BLOCK","ENOUGH")){
                FeedStore.Row[] current={null};ui(()->current[0]=((List<FeedStore.Row>)field(home[0],"feed")).stream().filter(row->EventGroups.matches(row,member)).findFirst().orElseThrow());
                ui(()->controller.feedback(current[0],action));await(action,()->((List<?>)field(home[0],"feed")).size()==1);
                check(onUi(()->((List<FeedStore.Row>)field(home[0],"feed")).stream().noneMatch(row->EventGroups.matches(row,member))),"actual "+action+" hides both captured source entries");
                String id=serial.submit(()->store.activeFeedback(System.currentTimeMillis()).getJSONObject(0).getString("id")).get(5,TimeUnit.SECONDS);
                ui(()->controller.undo(id));await(action+" undo",()->((List<?>)field(home[0],"feed")).size()==2);check(true,"actual "+action+" undo restores both reader entries");
            }
            Object[] beforePreference={null};ui(()->beforePreference[0]=field(home[0],"feed"));
            ui(()->controller.platformFeedback(Models.Source.ZHIHU,true));await("platform pending",()->(long)field(controller,"pending")>0);
            check(onUi(()->field(home[0],"feed")==beforePreference[0]),"platform preference preparation keeps visible reader choices frozen until update acceptance");
            ui(()->((android.widget.TextView)field(home[0],"updates")).performClick());await("preferred source selected",()->(long)field(controller,"pending")==0&&((List<FeedStore.Row>)field(home[0],"feed")).stream().allMatch(row->row.candidate.source==Models.Source.ZHIHU));
            check(onUi(()->((List<FeedStore.Row>)field(home[0],"feed")).size()==2&&((List<FeedStore.Row>)field(home[0],"feed")).stream().allMatch(row->row.entries.size()==2&&row.candidate.source==Models.Source.ZHIHU)),"accepted explicit platform preference selects Zhihu title/reader and retains Tieba entrance for both groups");
            check(serial.submit(()->{
                long active=store.activeSnapshot();try(android.database.Cursor cursor=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM snapshots",null)){
                    return cursor.moveToFirst()&&cursor.getInt(0)<=3&&store.snapshotInfo(original[0])==null&&store.readSnapshot(active,200,0).size()==2&&store.readSnapshot(active,200,0).stream().allMatch(row->row.entries.size()==2);
                }
            }).get(5,TimeUnit.SECONDS),"bounded snapshot retention evicts old snapshot and preserves current two groups after repeated actions");
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(home[0]!=null)home[0].finish();});test.waitForIdleSync();if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=isolated recorded sports evidence/home/SQLite flow; not live accounts, universal sports recall, ARM or battery acceptance\n").toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
