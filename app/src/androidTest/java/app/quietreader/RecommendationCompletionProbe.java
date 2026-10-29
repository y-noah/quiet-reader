package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Actual controller completion, without opening settings or treating cache existence as completion.
 * Public title corpus plus explicit developer fixture interests; not personal-quality evidence. */
final class RecommendationCompletionProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;private DiscoveryController controller;private ExecutorService storage;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    RecommendationCompletionProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private long now(){return SystemClock.elapsedRealtime();}
    private void await(String label,long timeout,java.util.function.BooleanSupplier condition)throws Exception{long end=now()+timeout;while(!condition.getAsBoolean()){if(now()>end)throw new IllegalStateException("Timed out: "+label);Thread.sleep(25);}}
    private static String order(FeedStore store,long snapshot){StringBuilder s=new StringBuilder();for(FeedStore.Row row:store.readSnapshot(snapshot,200,0))s.append(row.eventId).append('\n');return s.toString();}
    private static final class State {long completedSource,completedProfile,pending;boolean running,foreground;RecommendationState status;}
    private State state(){State s=new State();ui(()->{s.completedSource=(long)field(controller,"completedSource");s.completedProfile=(long)field(controller,"completedProfile");s.pending=(long)field(controller,"pending");s.running=field(controller,"recommendation")!=null;s.foreground=(boolean)field(controller,"foreground");s.status=(RecommendationState)field(controller,"recommendationState");});return s;}
    private State slice(long source,long profile,int round)throws Exception{
        long previous=state().pending,started=now();ui(controller::requestRecommendation);
        State[] finalState={null};
        await("actual committed recommendation slice "+round,45000,()->{State s=state();boolean complete=s.completedSource==source&&s.completedProfile==profile;
            if(s.pending>previous&&!s.running&&(complete||s.status.phase==RecommendationState.Phase.PARTIAL||s.status.phase==RecommendationState.Phase.PAUSED)){finalState[0]=s;return true;}return false;});
        State s=finalState[0];report.append("SLICE ").append(round).append(" elapsedMs=").append(now()-started).append(" pending=").append(s.pending).append(" phase=").append(s.status.phase).append(" completedSource=").append(s.completedSource).append(" completedProfile=").append(s.completedProfile).append(" expectedSource=").append(source).append(" expectedProfile=").append(profile).append(" detail=").append(s.status.detail.replace('\n',' ')).append('\n');return s;
    }
    void run(){String database="test-ui-completion-"+UUID.randomUUID()+".db";
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target required");
            JSONObject fixture;try(java.io.InputStream in=test.getContext().getAssets().open("energy-replay-100-v2.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            Map<Models.Source,List<HotCandidate>> pools=new EnumMap<>(Models.Source.class);JSONArray candidates=fixture.getJSONArray("candidates");
            for(int n=0;n<candidates.length();n++){HotCandidate c=HotCandidate.fromJson(candidates.getJSONObject(n));pools.computeIfAbsent(c.source,k->new ArrayList<>()).add(c);}
            long at=System.currentTimeMillis();
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                for(Map.Entry<Models.Source,List<HotCandidate>> pool:pools.entrySet())store.upsertSnapshot(pool.getKey(),store.beginRefresh(pool.getKey()),pool.getValue(),at);
                JSONArray interests=fixture.getJSONArray("developerProfile");for(int n=0;n<interests.length();n++){JSONObject f=interests.getJSONObject(n);store.feedback(f.getString("id"),f.getString("action"),"sample",f.getString("id"),at,null,f.getString("sample"),null);}
            }
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database).putExtra("test_skip_fetch",true));
            controller=(DiscoveryController)field(home,"controller");storage=(ExecutorService)field(controller,"storage");
            await("ready home and reader provider",25000,()->{boolean[] ready={false};ui(()->ready[0]=(boolean)field(controller,"ready")&&!ReaderStartup.pending()&&!ReaderStartup.failed());return ready[0];});
            try(FeedStore observation=new FeedStore(test.getTargetContext(),database)){
                long source=observation.version("sources"),profile=observation.profile(System.currentTimeMillis()).getLong("version"),active=observation.activeSnapshot();String initial=order(observation,active);
                check(candidates.length()==100&&observation.readSnapshot(active,200,0).size()==100,"actual controller begins with100 distinct public-title events");
                State result=null;
                for(int round=1;round<=6;round++){
                    result=slice(source,profile,round);
                    check(observation.activeSnapshot()==active&&initial.equals(order(observation,active)),"slice "+round+" leaves the entire active feed frozen");
                    if(result.completedSource==source&&result.completedProfile==profile)break;
                }
                boolean complete=result!=null&&result.completedSource==source&&result.completedProfile==profile;
                check(complete&&result.status.phase==RecommendationState.Phase.READY,"actual fullyComputed completion reaches READY for exact current source and profile versions");
                if(!complete)throw new IllegalStateException("Six foreground slices did not fully complete; no cache-count completion substitution");
                JSONObject pending=observation.snapshotInfo(result.pending);
                check(pending!=null&&pending.getLong("sourceVersion")==source&&pending.getLong("profileVersion")==profile&&"personal".equals(pending.getString("mode")),"complete pending is a real personal snapshot for current versions");
                State cached=slice(source,profile,7);
                check(cached.completedSource==source&&cached.completedProfile==profile&&cached.status.detail.contains("初筛 0 条，精排 0 条"),"same-version complete repeat reaches READY with zero new embedding or reranking");
                long accepted=cached.pending;ui(()->controller.applyPending(false));
                await("explicit pending acceptance",8000,()->observation.activeSnapshot()==accepted);
                check(observation.activeSnapshot()!=active&&"personal".equals(observation.snapshotInfo(accepted).optString("mode")),"only explicit acceptance changes active snapshot to completed personal order");
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(home!=null)home.finish();});test.waitForIdleSync();try{if(storage!=null)storage.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle b=new Bundle();b.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);
    }
}
