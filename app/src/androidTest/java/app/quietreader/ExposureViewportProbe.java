package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.widget.ListView;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Actual bound ListView rows and lifecycle; no source acquisition or synthetic visibility injection. */
final class ExposureViewportProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private Activity settings;private ListView list;private DiscoveryController controller;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    ExposureViewportProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier task){boolean[] result={false};ui(()->result[0]=task.getAsBoolean());return result[0];}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(stage);Thread.sleep(20);}}
    private void move(int position)throws Exception{ui(()->list.setSelectionFromTop(position,0));test.waitForIdleSync();await("target included by automatic list growth",()->list.getCount()>position);ui(()->list.setSelectionFromTop(position,0));test.waitForIdleSync();await("fully visible bound target "+position,()->{for(int n=0;n<list.getChildCount();n++){View child=list.getChildAt(n);if(child.getTag()!=null&&list.getPositionForView(child)==position&&child.getTop()>=list.getPaddingTop()&&child.getBottom()<=list.getHeight())return true;}return false;});}
    @SuppressWarnings("unchecked") void run(){String database="test-ui-exposure-"+UUID.randomUUID()+".db";ExecutorService worker=null;List<FeedStore.Row> rows=null;
        try{
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<70;n++)candidates.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99000000+n),"实际列表曝光验证话题第"+n+"项","","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");list=(ListView)field(activity,"list");
            await("focused bound feed",()->activity.hasWindowFocus()&&list.getCount()>=40&&list.getChildCount()>3&&list.getChildAt(2).getTag()!=null);
            rows=new ArrayList<>((List<FeedStore.Row>)field(activity,"feed"));FeedStore store=(FeedStore)field(controller,"store");List<FeedStore.Row> frozen=rows;long profile=worker.submit(()->store.version("profile")).get(5,TimeUnit.SECONDS);String[] visible={null};ui(()->visible[0]=(String)list.getChildAt(2).getTag());
            Thread.sleep(1600);Map<String,FeedRefreshOrder.Recent> history=worker.submit(()->store.recentExposure(frozen,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            check(history.containsKey(visible[0]),"actual continuously visible bound card is persisted after dwell");check(!history.containsKey(rows.get(60).eventId),"candidate merely loaded into the feed is never marked seen");
            check(onUi(()->((FeedExposureTracker)field(activity,"exposure")).dueAt()==-1),"settled visible cards leave no periodic callback deadline");
            move(20);Thread.sleep(100);move(40);Thread.sleep(1600);history=worker.submit(()->store.recentExposure(frozen,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            report.append("VIEWPORT_FACTS quick20=").append(history.containsKey(rows.get(20).eventId)).append(" stable40=").append(history.containsKey(rows.get(40).eventId)).append('\n');check(!history.containsKey(rows.get(20).eventId)&&history.containsKey(rows.get(40).eventId),"quickly passed viewport is not seen; next stable viewport is seen");
            move(55);Thread.sleep(100);ui(()->((View)field(activity,"settingsButton")).performClick());test.waitForIdleSync();Thread.sleep(1500);history=worker.submit(()->store.recentExposure(frozen,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            check(!history.containsKey(rows.get(55).eventId)&&onUi(()->((FeedExposureTracker)field(activity,"exposure")).dueAt()==-1),"opening actual settings interrupts incomplete dwell and removes timer");
            ui(()->((View)field(activity,"settingsButton")).performClick());test.waitForIdleSync();move(60);Thread.sleep(100);settings=test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();Thread.sleep(1500);history=worker.submit(()->store.recentExposure(frozen,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            check(!history.containsKey(rows.get(60).eventId)&&onUi(()->((FeedExposureTracker)field(activity,"exposure")).dueAt()==-1),"actual Activity pause prevents background dwell and leaves no timer");
            check(worker.submit(()->store.version("profile")==profile&&store.activeFeedback(System.currentTimeMillis()).length()==0).get(5,TimeUnit.SECONDS),"actual UI exposure creates no interest feedback or profile-version change");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(settings!=null)settings.finish();if(activity!=null)activity.finish();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
