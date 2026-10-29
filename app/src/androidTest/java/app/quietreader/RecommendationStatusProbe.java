package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual controller cancellation, bundled models, and native status UI; isolated fixtures. */
final class RecommendationStatusProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private DiscoveryController controller;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    RecommendationStatusProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new IllegalStateException("Test UI action failed",failure[0]);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,long timeout,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Status deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private TextView subtitle(){return (TextView)field(activity,"settingsButton");}
    private View find(View root,String text){if(root.getContentDescription()!=null&&text.contentEquals(root.getContentDescription())||root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),text);if(found!=null)return found;}return null;}
    private void click(String label){ui(()->{View target=find(activity.getWindow().getDecorView(),label);if(target==null)throw new IllegalStateException("Missing control: "+label);target.performClick();});test.waitForIdleSync();}
    private TextView updates(){return (TextView)field(activity,"updates");}
    private String status(){CharSequence value=updates().getStateDescription();return value==null?"":value.toString();}
    private boolean personal(){return "personal".equals(field(controller,"visibleMode"));}
    private void details() {click("设置");click("高级与诊断");click("本机推荐状态");}
    private void awaitModelCompletion()throws Exception{
        long started=SystemClock.elapsedRealtime(),deadline=started+60000;int windows=1;long acknowledged=0;
        while(!onUi(()->(long)field(controller,"completedProfile")>=0&&(long)field(controller,"pending")>0)){
            if(SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("Status deadline: actual model completion");
            long[] committed={0};ui(()->{if(field(controller,"recommendation")==null&&controller.recommendationStatus().contains("已回报模型工作量"))committed[0]=(long)field(controller,"pending");});
            // A real foreground window can stop at the production 15s budget.
            // Continue only after its partial result committed; retain the
            // original 60s total and fully-computed completion assertion.
            if(committed[0]>acknowledged&&windows<4){
                acknowledged=committed[0];int finishedWindow=windows;
                ui(()->report.append("STATUS_WINDOW window=").append(finishedWindow).append(" elapsedMs=").append(SystemClock.elapsedRealtime()-started).append(" snapshot=").append(committed[0]).append(" detail=").append(controller.recommendationStatus()).append('\n'));
                windows++;ui(controller::requestRecommendation);
            }
            Thread.sleep(25);
        }
        report.append("STATUS_EXPLICIT_WINDOWS ").append(windows).append('\n');
    }
    void run(){String database="test-ui-status-"+UUID.randomUUID()+".db";ExecutorService worker=null;
        try{
            long now=System.currentTimeMillis();String[] titles={"中国空间站生命科学实验","中国空间站生命科学实验取得新进展","明星恋情娱乐八卦"};List<HotCandidate> candidates=new ArrayList<>();
            for(int n=0;n<titles.length;n++)candidates.add(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+(99008700+n),titles[n],"","",false,n+1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){store.upsertSnapshot(Models.Source.ZHIHU,store.beginRefresh(Models.Source.ZHIHU),candidates,now);store.feedback("positive","MORE","sample",candidates.get(0).id,now,null,candidates.get(0).modelText());}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");
            await("actual recommendation starts",15000,()->field(controller,"recommendation")!=null);
            Object visible=field(activity,"feed");long snapshot=(long)field(controller,"snapshot");
            check(onUi(()->status().contains("正在")&&"设置".contentEquals(subtitle().getContentDescription())),"running work exposes a separate refresh-button state while settings keeps its action label");
            ui(()->controller.interacting(true));
            check(onUi(()->field(controller,"recommendation")==null&&status().contains("暂停")),"interaction cancels real work and replaces running accessibility state");
            check(onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot&&!personal()),"cancellation preserves the displayed non-personal snapshot");
            check(onUi(()->subtitle().isClickable()&&subtitle().getHeight()>=Math.round(48*activity.getResources().getDisplayMetrics().density)),"minimal settings icon retains a 48dp touch target");
            details();AlertDialog paused=(AlertDialog)field(activity,"recommendationDialog");
            check(onUi(()->paused!=null&&paused.isShowing()&&((TextView)paused.findViewById(android.R.id.message)).getText().toString().equals(controller.recommendationStatus())),"advanced settings opens the actual paused-work diagnostic");
            ui(()->{controller.interacting(false);paused.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
            await("continue dismisses diagnostics",3000,()->field(activity,"recommendationDialog")==null&&"feed".equals(field(activity,"page")));
            check(true,"continue returns to the feed and releases the dismissed dialog reference");
            awaitModelCompletion();
            check(onUi(()->controller.recommendationStatus().contains("已回报模型工作量")&&"查看更新".contentEquals(updates().getContentDescription())&&!personal()&&field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot),"completed real model work stays pending without replacing or relabelling the displayed snapshot");
            details();AlertDialog completed=(AlertDialog)field(activity,"recommendationDialog");
            check(onUi(()->completed!=null&&completed.isShowing()&&((TextView)completed.findViewById(android.R.id.message)).getText().toString().equals(controller.recommendationStatus())),"reopened diagnostics shows actual completed workload");
            ui(()->completed.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
            await("close diagnostic",3000,()->field(activity,"recommendationDialog")==null);click("返回");click("返回");
            ui(()->updates().performClick());await("personal snapshot accepted",8000,this::personal);
            check(onUi(()->(long)field(controller,"snapshot")!=snapshot&&"feed".equals(field(activity,"page"))),"explicit update-button acceptance activates the personal snapshot");
            Object accepted=field(activity,"feed");ui(()->activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
            check(onUi(()->status().contains("暂停")&&controller.recommendationStatus().equals("内存紧张，已暂停整理")&&field(activity,"feed")==accepted&&personal()),"actual memory trim exposes its reason while retaining the accepted personal list");
            details();AlertDialog pressure=(AlertDialog)field(activity,"recommendationDialog");
            check(onUi(()->pressure!=null&&((TextView)pressure.findViewById(android.R.id.message)).getText().toString().contains("内存紧张")),"memory pause reason remains available in advanced diagnostics");
            ui(()->pressure.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());await("close resource diagnostic",3000,()->field(activity,"recommendationDialog")==null);click("返回");click("内容来源");
            ui(()->controller.suspendRecommendation("测试来源页暂停"));
            check(onUi(()->"sources".equals(field(activity,"page"))&&"返回".contentEquals(subtitle().getContentDescription())),"status callbacks preserve the source-page back action");
        }catch(Throwable error){check(false,error.getClass().getSimpleName()+": "+error.getMessage());}
        finally{
            ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);
            report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
