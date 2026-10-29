package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import app.quietreader.discovery.*;
import org.json.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Three invocations separated by external process stops. Synthetic isolated data only. */
final class FeedbackRestartProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private FeedStore store;
    private ExecutorService worker;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    FeedbackRestartProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Feedback restart deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(activity,"feed");}
    private View button(View root,String label){if(root instanceof TextView&&root.isClickable()&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=button(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void apply()throws Exception{
        await("pending",()->(long)field(controller,"pending")>0);long[] before={0};boolean[] clicked={false};
        ui(()->{before[0]=(long)field(controller,"snapshot");View target=button(activity.getWindow().getDecorView(),"查看更新");clicked[0]=target!=null&&target.isShown()&&target.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);});
        if(!clicked[0])throw new IllegalStateException("Actual update button unavailable");
        await("accepted",()->(long)field(controller,"snapshot")!=before[0]&&(long)field(controller,"pending")==0);
    }
    private List<HotCandidate> candidates(long at){return List.of(
        new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/99881001","明确少看的合成讨论话题","这是反馈持久化测试，不是真实热门内容。","",false,1,null,at),
        new HotCandidate(Models.Source.ITHOME,"https://www.ithome.com/1/998/002.htm","另一平台尚无反馈的合成内容","","",false,2,null,at),
        new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/99881003","同平台独立的其他文字讨论","","",false,3,null,at));}
    private void publish(List<HotCandidate> all)throws Exception{worker.submit(()->{for(Models.Source source:List.of(Models.Source.V2EX,Models.Source.ITHOME)){List<HotCandidate> part=new ArrayList<>();for(HotCandidate c:all)if(c.source==source)part.add(c);store.upsertSnapshot(source,store.beginRefresh(source),part,System.currentTimeMillis());}}).get(5,TimeUnit.SECONDS);}
    private void inspect(boolean disliked,String id)throws Exception{
        check(onUi(()->rows().size()==3),"all three candidates remain available; LESS does not block a platform");
        check(onUi(()->rows().get(disliked?2:0).candidate.id.equals(id)),disliked?"known exact dislike remains below unrelated content despite source MORE":"undo restores the source-preferred candidate to its base position");
        check(onUi(()->field(controller,"recommendation")==null),"interaction-paused path has no active model work");
        check(worker.submit(()->{try(android.database.Cursor c=store.getWritableDatabase().rawQuery("SELECT (SELECT COUNT(*) FROM embeddings)+(SELECT COUNT(*) FROM reranks)",null)){c.moveToFirst();return c.getLong(0)==0;}}).get(),"feedback protection uses neither embedding nor reranker cache");
    }
    void run(String phase,String probeId){String database=null;SharedPreferences marker=null;boolean complete=false;
        try{
            if(probeId==null||!probeId.matches("[a-f0-9]{32}"))throw new IllegalArgumentException("Invalid probe id");
            database="test-ui-feedback-restart-"+probeId+".db";marker=test.getTargetContext().getSharedPreferences("test-feedback-restart-"+probeId,0);
            List<HotCandidate> all=candidates(System.currentTimeMillis());String disliked=all.get(0).id;
            boolean write=phase.equals("feedback-restart-write"),last=phase.equals("feedback-restart-final");
            if(write){
                if(test.getTargetContext().getDatabasePath(database).exists()||marker.contains("pid"))throw new IllegalStateException("Fixture already exists; refusing reseed");
                try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){seed.ensureCandidatePolicy();for(Models.Source source:List.of(Models.Source.V2EX,Models.Source.ITHOME)){List<HotCandidate> part=new ArrayList<>();for(HotCandidate c:all)if(c.source==source)part.add(c);seed.upsertSnapshot(source,seed.beginRefresh(source),part,System.currentTimeMillis());}}
            }else{
                if(!test.getTargetContext().getDatabasePath(database).exists()||!marker.contains("pid"))throw new IllegalStateException("Writer fixture missing");
                check(marker.getInt("pid",-1)!=android.os.Process.myPid(),"verification runs in a different process from the previous phase");
                report.append("PROCESS previous=").append(marker.getInt("pid",-1)).append(" current=").append(android.os.Process.myPid()).append('\n');
            }
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(activity,"controller");store=(FeedStore)field(controller,"store");worker=(ExecutorService)field(controller,"storage");
            // Real scrolling policy pauses inference; this probes the readable fallback, not model quality.
            ui(()->controller.interacting(true));await("initial snapshot",()->(boolean)field(controller,"ready")&&rows().size()==3);
            if(write){
                check(onUi(()->rows().get(0).candidate.id.equals(disliked)),"fixture initially puts the soon-disliked candidate first");
                FeedStore.Row target=rows().get(0);ui(()->controller.feedback(target,"LESS"));apply();inspect(true,disliked);
                ui(()->controller.platformFeedback(Models.Source.V2EX,true));apply();inspect(true,disliked);
                JSONArray feedback=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
                check(feedback.length()==2,"actual feedback actions persist one content LESS and one platform MORE");
            }else if(!last){
                inspect(true,disliked);JSONArray active=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
                check(active.length()==2,"content and platform feedback both survive process termination");
                long[] before={0};ui(()->before[0]=(long)field(controller,"snapshot"));publish(candidates(System.currentTimeMillis()+1));ui(()->controller.sort(false));await("source snapshot rebuilt",()->(long)field(controller,"snapshot")!=before[0]);inspect(true,disliked);
                String less=null;for(int n=0;n<active.length();n++)if("LESS".equals(active.getJSONObject(n).getString("action")))less=active.getJSONObject(n).getString("id");if(less==null)throw new IllegalStateException("LESS event missing");String undo=less;ui(()->{before[0]=(long)field(controller,"snapshot");controller.undo(undo);});await("undo applied",()->(long)field(controller,"snapshot")!=before[0]&&(long)field(controller,"pending")==0);inspect(false,disliked);
                JSONArray remaining=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();check(remaining.length()==1&&"source".equals(remaining.getJSONObject(0).getString("scope")),"undo removes only the selected content feedback and preserves platform preference");
            }else{
                inspect(false,disliked);JSONArray active=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();check(active.length()==1&&"MORE".equals(active.getJSONObject(0).getString("action")),"undo remains effective after another process termination");
            }
            worker.submit(()->{}).get(5,TimeUnit.SECONDS);
            if(failures==0&&!marker.edit().putInt("pid",android.os.Process.myPid()).commit())throw new IllegalStateException("Marker write failed");
            complete=failures==0&&last;
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{
            ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            if(complete){test.getTargetContext().deleteDatabase(database);test.getTargetContext().deleteSharedPreferences("test-feedback-restart-"+probeId);}
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
