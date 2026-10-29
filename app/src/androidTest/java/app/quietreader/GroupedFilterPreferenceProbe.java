package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Source preference through a real grouped snapshot, category button and reopening. */
final class GroupedFilterPreferenceProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private ExecutorService worker;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    GroupedFilterPreferenceProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Grouped filter deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(activity,"feed");}
    private FeedStore.Row group(){return rows().stream().filter(row->row.entries.size()==2).findFirst().orElseThrow();}
    private View find(View root,String label){if(root instanceof TextView&&root.isClickable()&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void categoryClick()throws Exception{
        long[] before={0};boolean[] clicked={false};ui(()->{before[0]=(long)field(controller,"snapshot");View target=find(activity.getWindow().getDecorView(),"科技");android.graphics.Rect rect=new android.graphics.Rect();clicked[0]=target!=null&&target.getGlobalVisibleRect(rect)&&target.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);});
        if(!clicked[0])throw new IllegalStateException("Category button unavailable");await("category accepted",()->(long)field(controller,"snapshot")!=before[0]&&controller.categoryFilter()==ContentCategory.TECH&&rows().size()==2);
    }
    private void filterSource(String source)throws Exception{long[] before={0};ui(()->{before[0]=(long)field(controller,"snapshot");controller.filterSource(source);});await("source accepted",()->(long)field(controller,"snapshot")!=before[0]&&source.equals(controller.sourceFilter()));}
    private void open(String database)throws Exception{
        activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");await("ready",()->(boolean)field(controller,"ready")&&rows().size()==2);
    }
    private void finishActivity()throws Exception{ui(activity::finish);test.waitForIdleSync();worker.awaitTermination(5,TimeUnit.SECONDS);}
    void run(){String database="test-ui-group-filter-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();String title="开源文字阅读工具发布新版本的使用体验讨论",snippet="这是合成的同一事件摘要，用来验证两个明确科技栏目来源合并后的平台偏好入口，不代表真实新闻或用户兴趣。";
            HotCandidate paper=new HotCandidate(Models.Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_998871",title,snippet,"",false,1,now,now,ContentLanguage.Body.UNKNOWN,"对齐Lab");
            HotCandidate v2ex=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/998871",title,snippet,"",false,2,now,now,ContentLanguage.Body.UNKNOWN,"node:programmer");
            HotCandidate other=new HotCandidate(Models.Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_998872","另一项独立的中文技术讨论","不能因筛选重新学习或改变列表顺序。","",false,3,now,now,ContentLanguage.Body.UNKNOWN,"对齐Lab");
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){seed.ensureCandidatePolicy();seed.upsertSnapshot(Models.Source.THEPAPER,seed.beginRefresh(Models.Source.THEPAPER),List.of(paper,other),now);seed.upsertSnapshot(Models.Source.V2EX,seed.beginRefresh(Models.Source.V2EX),List.of(v2ex),now);seed.feedback("prefer-v2ex","MORE","source","V2EX",now,null,"V2EX",Models.Source.V2EX);}
            open(database);check(onUi(()->group().candidate.source==Models.Source.V2EX&&group().entries.get(0).source==Models.Source.THEPAPER),"real SQLite snapshot chooses preferred V2EX despite Thepaper being first group entry");
            List<String> base=new ArrayList<>();ui(()->rows().forEach(row->base.add(row.eventId)));categoryClick();
            check(onUi(()->group().candidate.source==Models.Source.V2EX),"actual category click retains the eligible preferred reading entry");
            List<String> filtered=new ArrayList<>();ui(()->rows().forEach(row->filtered.add(row.eventId)));check(base.equals(filtered),"category view preserves event order and grouped alternatives");
            filterSource("THEPAPER");check(onUi(()->group().candidate.source==Models.Source.THEPAPER&&rows().stream().allMatch(row->row.candidate.source==Models.Source.THEPAPER)),"explicit source filter overrides incompatible preferred entry");
            filterSource("all");check(onUi(()->group().candidate.source==Models.Source.V2EX&&controller.categoryFilter()==ContentCategory.TECH),"returning to all sources restores preferred entry within the same category");
            finishActivity();open(database);check(onUi(()->group().candidate.source==Models.Source.V2EX&&controller.categoryFilter()==ContentCategory.TECH),"reopening restores category and preferred entry from separately deserialized SQLite fields");
            FeedStore store=(FeedStore)field(controller,"store");check(worker.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==1).get(),"filter changes do not add feedback or repeat platform learning");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{if(activity!=null)try{finishActivity();}catch(Exception ignored){}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
