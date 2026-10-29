package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real optional labeling UI and SQLite; synthetic choices never enter user learning. */
final class RecommendationTrialProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    RecommendationTrialProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor descriptor=test.getUiAutomation().executeShellCommand(command);java.io.FileInputStream input=new java.io.FileInputStream(descriptor.getFileDescriptor())){input.readAllBytes();}}
    private void visible(String label)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(find(test.getUiAutomation().getRootInActiveWindow(),label)==null){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Trial view absent: "+label);Thread.sleep(40);}}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Trial deadline: "+stage);Thread.sleep(30);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String label){if(root==null)return null;if(root.isVisibleToUser()&&label.contentEquals(root.getText()==null?"":root.getText()))return root;for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=find(root.getChild(n),label);if(found!=null)return found;}return null;}
    private AccessibilityNodeInfo scrollable(AccessibilityNodeInfo root){if(root==null)return null;if(root.isScrollable()&&root.isVisibleToUser())return root;for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=scrollable(root.getChild(n));if(found!=null)return found;}return null;}
    private void tap(String label)throws Exception{
        long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){test.waitForIdleSync();AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow(),target=find(root,label);
            if(target!=null){if(target.performAction(AccessibilityNodeInfo.ACTION_CLICK))return;android.graphics.Rect bounds=new android.graphics.Rect();target.getBoundsInScreen(bounds);if(!bounds.isEmpty()){long at=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(at,at+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}return;}}
            AccessibilityNodeInfo scroll=scrollable(root);if(scroll!=null)scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(100);
        }throw new IllegalStateException("Visible trial action absent: "+label);
    }
    private List<FeedStore.Row> rows(int count){List<FeedStore.Row> values=new ArrayList<>();for(int n=0;n<count;n++){HotCandidate c=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/9989"+n,"中文文字流推荐试用评价样本第"+n+"项","这是一段合成摘要，只用于验证界面与本机记录，不能冒充用户偏好或真实新闻。","",false,n+1,null,100);values.add(new FeedStore.Row(c.id,c,""));}return values;}
    private void storeChecks(String name)throws Exception{
        String id;try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),name)){
            List<FeedStore.Row> rows=rows(21);JSONObject frozen=RecommendationTrialStore.freeze(rows,7,8,9,"all","personal",100);rows.clear();check(frozen.getJSONArray("items").length()==20,"evaluation freezes a bounded independent copy of twenty event entries");
            id=store.create(frozen);store.label(id,0,"want",101);store.label(id,1,"unsure",102);check(RecommendationTrialStore.next(store.read(id))==2,"uncertain is persisted as its own choice, not a negative label");
            boolean rejected=true;for(String choice:List.of("MORE","LESS","")){try{store.label(id,2,choice,103);rejected=false;}catch(IllegalArgumentException expected){}}try{store.label(id,100,"skip",103);rejected=false;}catch(IllegalArgumentException expected){}try{store.label(id,2,"skip",99);rejected=false;}catch(IllegalArgumentException expected){}check(rejected,"invalid training actions, index and pre-freeze label time are rejected");
        }
        try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),name)){JSONObject value=store.read(id);check(value.getJSONObject("labels").length()==2&&!value.getBoolean("holdout_qualified")&&"single_snapshot".equals(value.getString("kind")),"reopened SQLite retains labels and non-holdout provenance");
            check(store.latestCompleted()==null,"partial evaluations cannot expose comparison results through history");
            String complete=store.create(RecommendationTrialStore.freeze(rows(1),1,8,9,"all","cold",150));store.label(complete,0,"unsure",151);store.create(RecommendationTrialStore.freeze(rows(1),2,8,9,"all","cold",160));check(complete.equals(store.latestCompleted())&&id.equals(store.read(id).getString("id")),"completed history skips newer partial sessions without changing prior labels");
            for(int n=0;n<31;n++)store.create(RecommendationTrialStore.freeze(rows(1),n,8,9,"all","cold",200+n));check(store.read(id)==null,"thirty-session eviction also retires the oldest labeled session");
            try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM labels",null)){c.moveToFirst();check(c.getInt(0)==0,"eviction leaves no orphan labels");}store.clear();check(store.unfinished()==null&&store.latestCompleted()==null,"clear removes trial sessions and resumable state");}
    }
    private void finishActivity()throws Exception{
        if(activity==null)return;DiscoveryController controller=(DiscoveryController)field(activity,"controller");RecommendationTrialUi trial=(RecommendationTrialUi)field(activity,"trialEvaluation");ExecutorService trialWorker=trial==null?null:(ExecutorService)field(trial,"worker");ui(activity::finish);test.waitForIdleSync();((ExecutorService)field(controller,"storage")).awaitTermination(5,TimeUnit.SECONDS);if(trialWorker!=null)trialWorker.awaitTermination(5,TimeUnit.SECONDS);
    }
    void run(){String suffix=UUID.randomUUID().toString(),database="test-ui-trial-"+suffix+".db",trialName="test-trial-"+database,storeName="test-trial-store-"+suffix+".db";
        try{
            storeChecks(storeName);List<FeedStore.Row> rows=rows(3);try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){seed.ensureCandidatePolicy();List<HotCandidate> candidates=new ArrayList<>();for(FeedStore.Row row:rows)candidates.add(row.candidate);seed.upsertSnapshot(Models.Source.V2EX,seed.beginRefresh(Models.Source.V2EX),candidates,System.currentTimeMillis());}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await("discovery ready",()->(boolean)field(field(activity,"controller"),"ready"));
            long snapshot=(long)field(field(activity,"controller"),"snapshot");tap("设置");tap("本轮推荐评价");tap("选择操作");tap("评价发现页列表");tap("想看");tap("不确定");
            await("third item",()->field(activity,"trialEvaluation")!=null&&((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession()!=null);String id=((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession();
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){long end=SystemClock.elapsedRealtime()+5000;while(RecommendationTrialStore.next(store.read(id))<2&&SystemClock.elapsedRealtime()<end)Thread.sleep(30);check(RecommendationTrialStore.next(store.read(id))==2,"real preference entry and two actual choices persist without auto-training");}
            visible("本轮评价 · 3/3");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("trial canceled",()->((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession()==null);
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("继续上次评价");visible("本轮评价 · 3/3");check(onUi(()->id.equals(((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession())),"back dismisses safely and explicit continue restores the same unfinished session");
            shell("input keyevent 3");await("trial background",()->(boolean)field(field(activity,"trialEvaluation"),"suspended"));
            shell("am start -n "+test.getTargetContext().getPackageName()+"/app.quietreader.DiscoveryActivity -f 0x10020000");visible("本轮评价 · 3/3");check(onUi(()->id.equals(((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession())),"actual HOME and foreground return restore the pending item without consuming a choice");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(DiscoveryActivity.class.getName(),null,false);try{ui(activity::recreate);Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof DiscoveryActivity))throw new IllegalStateException("Trial recreation missing");activity=(DiscoveryActivity)next;}finally{test.removeMonitor(monitor);}
            tap("不想看");tap("完成");check(true,"rotation restores the unfinished trial and completes the remaining item");
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){JSONObject value=store.read(id);Set<String> choices=new HashSet<>();JSONObject labels=value.getJSONObject("labels");for(Iterator<String> it=labels.keys();it.hasNext();)choices.add(labels.getJSONObject(it.next()).getString("choice"));check(RecommendationTrialStore.next(value)==3&&choices.equals(Set.of("want","skip","unsure")),"three actual UI choices retain separate want/skip/unsure semantics");}
            DiscoveryController owner=(DiscoveryController)field(activity,"controller");FeedStore learning=(FeedStore)field(owner,"store");ExecutorService storage=(ExecutorService)field(owner,"storage");check(storage.submit(()->learning.activeFeedback(System.currentTimeMillis()).length()==0).get(),"evaluation choices never enter interest or platform feedback");check(onUi(()->(long)field(owner,"snapshot")==snapshot),"evaluation and recreation preserve the discovery snapshot");
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("清除评价记录");tap("清除");
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){long end=SystemClock.elapsedRealtime()+5000;while(store.read(id)!=null&&SystemClock.elapsedRealtime()<end)Thread.sleep(30);check(store.read(id)==null,"actual explicit clear action removes only evaluation records");}
            check(storage.submit(()->learning.candidates(100,0).size()==3&&learning.activeFeedback(System.currentTimeMillis()).length()==0).get(),"clearing trial records preserves reading candidates and learning state");
            tap("关闭");ui(()->owner.interacting(true));tap("设置");tap("本轮推荐评价");tap("选择操作");tap("对比推荐效果");visible("本轮对比未开始");
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){check(store.unfinished()==null,"empty interest profile refuses a fabricated model comparison");}tap("关闭");
            storage.submit(()->learning.feedback("trial-developer-profile","MORE","sample","trial-sample",System.currentTimeMillis(),null,"程序员讨论、软件开发和日常实用经验")).get();
            CountDownLatch release=new CountDownLatch(1);Future<?> held=storage.submit(()->{try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
            try{tap("设置");tap("本轮推荐评价");tap("选择操作");tap("对比推荐效果");tap("取消");check(onUi(()->field(owner,"trialFailure")==null),"actual cancel stops an in-flight comparison preparation");}finally{release.countDown();}held.get(5,TimeUnit.SECONDS);storage.submit(()->{}).get();test.waitForIdleSync();
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){check(store.unfinished()==null,"canceled preparation cannot later create a stale evaluation session");}
            long beforeComparison=storage.submit(learning::activeSnapshot).get();tap("设置");tap("本轮推荐评价");tap("选择操作");tap("对比推荐效果");
            long deadline=SystemClock.elapsedRealtime()+30000;while(onUi(()->((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession()==null)&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);
            String[] pairedId={null};ui(()->pairedId[0]=((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession());if(pairedId[0]==null)throw new IllegalStateException("Paired trial did not start");
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){JSONObject paired=store.read(pairedId[0]);RecommendationTrialStore.validate(paired);check("paired_snapshot".equals(paired.getString("kind"))&&paired.getJSONArray("items").length()==3&&!paired.getBoolean("holdout_qualified"),"actual comparison entry runs local models and persists deduplicated blinded fronts");
                JSONObject provenance=paired.getJSONObject("learning_provenance");
                check(provenance.getInt("ledger_rows")==1&&provenance.getJSONArray("candidate_events").length()==3&&provenance.getLong("learning_cutoff_ms")<=paired.getLong("frozen_at_ms")&&!provenance.getBoolean("holdout_qualified"),"actual model comparison persists its pre-ranking learning capture without claiming holdout");}
            visible("本轮评价 · 1/3");check(onUi(()->{AlertDialog dialog=(AlertDialog)field(field(activity,"trialEvaluation"),"dialog");String message=((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString();return !message.contains("基础顺序")&&!message.contains("双模型顺序");}),"blinded question does not expose ranking-mode attribution");
            tap("想看");tap("不想看");tap("不确定");visible("本轮评价已保存");check(onUi(()->{AlertDialog dialog=(AlertDialog)field(field(activity,"trialEvaluation"),"dialog");String message=((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString();return message.contains("基础顺序：想看 1/3")&&message.contains("双模型顺序：想看 1/3")&&message.contains("不确定或未评价 1");}),"completed paired UI reveals per-order counts while retaining uncertain labels");tap("完成");
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){check(RecommendationTrialStore.next(store.read(pairedId[0]))==3,"all paired-session choices use the resumable evaluation store");}
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("查看最近完成评价");visible("最近完成的评价");
            check(onUi(()->{AlertDialog dialog=(AlertDialog)field(field(activity,"trialEvaluation"),"dialog");String message=((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString();return message.contains("3 项：想看 1，不想看 1，不确定 1")&&message.contains("双模型顺序：想看 1/3");}),"actual completed history reopens frozen per-order counts with uncertain choices retained");tap("关闭");
            check(storage.submit(()->learning.activeFeedback(System.currentTimeMillis()).length()==1&&learning.activeSnapshot()==beforeComparison).get()&&onUi(()->(long)field(owner,"snapshot")==snapshot),"paired comparison does not alter learning feedback or the active visible feed");
            android.content.IntentFilter exportFilter=new android.content.IntentFilter(Intent.ACTION_CREATE_DOCUMENT);exportFilter.addCategory(Intent.CATEGORY_OPENABLE);exportFilter.addDataType("application/vnd.sqlite3");
            Instrumentation.ActivityMonitor exportMonitor=test.addMonitor(exportFilter,new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
            try{
                tap("设置");tap("本轮推荐评价");tap("选择操作");tap("导出评价记录");visible("选择保存位置");tap("取消");
                check(exportMonitor.getHits()==0,"canceling export explanation never opens the document picker");
                tap("设置");tap("本轮推荐评价");tap("选择操作");tap("导出评价记录");tap("选择保存位置");
                long until=SystemClock.elapsedRealtime()+10000;while(exportMonitor.getHits()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
                check(exportMonitor.getHits()==1,"actual export menu opens ACTION_CREATE_DOCUMENT after creating a metadata-only snapshot");
                await("export canceled cleanup",()->field(field(field(activity,"trialEvaluation"),"export"),"pending")==null&&!((boolean)field(field(field(activity,"trialEvaluation"),"export"),"busy")));
                java.io.File folder=new java.io.File(activity.getCacheDir(),"trial-exports");java.io.File[] exports=folder.listFiles();check(exports==null||exports.length==0,"system picker cancellation removes the private staging database");
                check(storage.submit(()->learning.activeFeedback(System.currentTimeMillis()).length()==1&&learning.activeSnapshot()==beforeComparison).get(),"export and cancellation preserve learning and the visible ranking");
            }finally{test.removeMonitor(exportMonitor);}
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("准备新话题评价");visible("准备新话题评价");tap("开始准备");visible("已准备新话题评价");
            String baselineId;try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){JSONObject baseline=store.protocol();baselineId=baseline.getString("id");check(baseline.getJSONArray("known_identities").length()>=3&&!baseline.getBoolean("holdout_qualified"),"actual prepare entry saves existing candidates and non-independent baseline");}tap("知道了");
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("评价准备后的新话题");visible("本轮对比未开始");
            check(onUi(()->{AlertDialog dialog=(AlertDialog)field(field(activity,"trialEvaluation"),"dialog");return ((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("还没有足够的新话题");}),"actual future comparison refuses an unchanged old pool");tap("关闭");
            storage.submit(()->{long now=System.currentTimeMillis();HotCandidate fresh=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/99899999","新的中文热门技术讨论用于准备后采集验证","受控新候选，仅验证时间边界和本地比较流程。","",false,1,null,now);learning.upsertSnapshot(Models.Source.V2EX,learning.beginRefresh(Models.Source.V2EX),List.of(fresh),now);}).get();
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("评价准备后的新话题");visible("本轮评价 · 1/1");
            String[] futureId={null};ui(()->futureId[0]=((RecommendationTrialUi)field(activity,"trialEvaluation")).activeSession());
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trialName)){JSONObject value=store.read(futureId[0]);RecommendationTrialStore.validate(value);check(value.getJSONObject("prospective_protocol").getString("id").equals(baselineId)&&value.getJSONArray("pool").length()==1,"actual local model future comparison retains the pre-collection baseline and only new candidate");check(store.protocol().getJSONArray("used_identities").length()==1,"actual UI consumes full future pool before labels");}
            tap("不确定");visible("本轮评价已保存");tap("完成");
            check(storage.submit(()->learning.activeFeedback(System.currentTimeMillis()).length()==1&&learning.activeSnapshot()==beforeComparison).get(),"prospective UI choices preserve learning profile and active feed snapshot");
            storage.submit(()->learning.feedback("trial-new-platform","MORE","source",Models.Source.V2EX.name(),System.currentTimeMillis(),null,"",Models.Source.V2EX)).get();
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("评价准备后的新话题");visible("本轮对比未开始");
            check(onUi(()->{AlertDialog dialog=(AlertDialog)field(field(activity,"trialEvaluation"),"dialog");return ((android.widget.TextView)dialog.findViewById(android.R.id.message)).getText().toString().contains("偏好记录已变化或过期");}),"actual changed preference reports explicit re-preparation requirement");tap("关闭");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{try{finishActivity();}catch(Exception ignored){}test.getTargetContext().deleteDatabase(database);test.getTargetContext().deleteDatabase(trialName);test.getTargetContext().deleteDatabase(storeName);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
