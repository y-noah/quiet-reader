package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.ListView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real update dot/list/controller with controlled source requests, no live account data. */
final class UpdateDotTopProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private DiscoveryController controller;private ListView list;
    private int calls,checks,failures;private final StringBuilder report=new StringBuilder();
    UpdateDotTopProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier test){boolean[] result={false};ui(()->result[0]=test.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(stage);Thread.sleep(30);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void lower()throws Exception{ui(()->list.setSelectionFromTop(12,-30));test.waitForIdleSync();try{await("list moves away from top",()->list.getFirstVisiblePosition()>0&&list.canScrollVertically(-1));}catch(Exception e){ui(()->report.append("LIST first=").append(list.getFirstVisiblePosition()).append(" count=").append(list.getCount()).append(" children=").append(list.getChildCount()).append(" page=").append(field(activity,"page")).append(" height=").append(list.getHeight()).append('\n'));throw e;}}
    private void click() {ui(()->check(((View)field(activity,"updates")).performClick(),"actual dot accepts click"));}
    private void top(String stage)throws Exception{await(stage,()->list.getFirstVisiblePosition()==0&&!list.canScrollVertically(-1));Thread.sleep(400);check(onUi(()->list.getFirstVisiblePosition()==0&&!list.canScrollVertically(-1)),stage+" remains at top after layout");}
    @SuppressWarnings("unchecked") void run(){String database="test-ui-update-dot-"+UUID.randomUUID()+".db";ExecutorService storage=null;
        try{
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<70;n++)candidates.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"圆点回顶部测试话题第"+n+"项","","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(activity,"controller");list=(ListView)field(activity,"list");storage=(ExecutorService)field(controller,"storage");
            await("seed feed ready",()->((List<?>)field(controller,"rows")).size()==70&&((List<?>)field(activity,"feed")).size()==70&&list.getCount()>=40&&list.getChildCount()>0);
            ui(()->{try{Object collector=field(controller,"collector");Field transport=HotSourceCollector.class.getDeclaredField("transport");transport.setAccessible(true);transport.set(collector,new HotSourceCollector.Transport(){public boolean offline(){return false;}public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){calls++;return ()->{};}});}catch(Exception e){throw new IllegalStateException(e);}});
            lower();check(onUi(()->!(boolean)field(activity,"updateAvailable")&&!(boolean)field(activity,"loading")),"idle dot has no pending update");click();top("idle click returns to top");await("idle click keeps existing refresh",()->calls>0&&(boolean)field(activity,"loading"));
            lower();int generation=(int)field(controller,"feedGeneration"),count=calls;check(onUi(()->((View)field(activity,"updates")).isEnabled()),"loading dot remains enabled for navigation");click();top("loading click returns to top");check(onUi(()->(int)field(controller,"feedGeneration")==generation&&calls==count),"loading click does not restart collection");
            FeedStore store=(FeedStore)field(controller,"store");List<FeedStore.Row> rows=new ArrayList<>((List<FeedStore.Row>)field(controller,"allRows"));Collections.reverse(rows);long pending=storage.submit(()->store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now+1,"cold","all",rows)).get(5,TimeUnit.SECONDS);
            ui(()->{try{Field f=DiscoveryController.class.getDeclaredField("pending");f.setAccessible(true);f.setLong(controller,pending);Method state=DiscoveryController.class.getDeclaredMethod("state");state.setAccessible(true);state.invoke(controller);}catch(Exception e){throw new IllegalStateException(e);}});
            lower();click();await("pending snapshot accepted",()->(long)field(controller,"snapshot")==pending);top("pending update returns to top after asynchronous acceptance");
            ui(()->((View)field(activity,"settingsButton")).performClick());check(onUi(()->"settings".equals(field(activity,"page"))),"real settings action leaves feed");click();top("dot from settings returns to feed top");check(onUi(()->"feed".equals(field(activity,"page"))),"dot shows the feed");
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
