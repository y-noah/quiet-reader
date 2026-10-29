package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.ListView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Actual home touch/presentation boundaries; controlled collection state, no source transport. */
final class RefreshPresentationProbe {
    private final Instrumentation test;private final boolean pull,animation,batch;private DiscoveryActivity home;private DiscoveryController controller;private FeedStore store;private ExecutorService serial;private ListView list;
    private int checks,failures;private final StringBuilder report=new StringBuilder();private final CountDownLatch release=new CountDownLatch(1);
    RefreshPresentationProbe(Instrumentation test,boolean pull){this(test,pull,false);}
    RefreshPresentationProbe(Instrumentation test,boolean pull,boolean animation){this(test,pull,animation,false);}
    RefreshPresentationProbe(Instrumentation test,boolean pull,boolean animation,boolean batch){this.test=test;this.pull=pull;this.animation=animation;this.batch=batch;}
    private Object field(Object owner,String name){try{Field value=owner.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new IllegalStateException(failure[0]);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException(stage);Thread.sleep(20);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean containsText(View view,String text){if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)if(containsText(((ViewGroup)view).getChildAt(n),text))return true;return false;}
    private void state(long pending,boolean loading){try{Field p=DiscoveryController.class.getDeclaredField("pending"),l=DiscoveryController.class.getDeclaredField("loading");p.setAccessible(true);l.setAccessible(true);p.setLong(controller,pending);l.setBoolean(controller,loading);Method notify=DiscoveryController.class.getDeclaredMethod("state");notify.setAccessible(true);notify.invoke(controller);}catch(Exception e){throw new IllegalStateException(e);}}
    private void pull(boolean cancel)throws Exception{int[] bounds=new int[4];float density=home.getResources().getDisplayMetrics().density;ui(()->{int[] point=new int[2];list.getLocationOnScreen(point);bounds[0]=point[0];bounds[1]=point[1];bounds[2]=list.getWidth();bounds[3]=list.getHeight();});float x=bounds[0]+bounds[2]*.5f,from=bounds[1]+110*density,to=from+220*density;long start=SystemClock.uptimeMillis();
        for(int n=0;n<=13;n++){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==13?(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP):MotionEvent.ACTION_MOVE,x,from+(to-from)*n/13f,0);try{test.sendPointerSync(event);}finally{event.recycle();}if(n<13)Thread.sleep(14);}test.waitForIdleSync();}
    private void blocked()throws Exception{CountDownLatch entered=new CountDownLatch(1);serial.submit(()->{entered.countDown();try{release.await(12,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});if(!entered.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Storage gate absent");}
    private long shown(FeedStore.Row row)throws Exception{return serial.submit(()->{FeedRefreshOrder.Recent recent=store.recentExposure(List.of(row),System.currentTimeMillis()).get(row.eventId);return recent==null?0:recent.shownAt;}).get(5,TimeUnit.SECONDS);}
    @SuppressWarnings("unchecked") private void pullCase(List<FeedStore.Row> rows,long original)throws Exception{
        int seenCount=Math.min(10,rows.size()-1);List<String> seen=new ArrayList<>();for(int n=0;n<seenCount;n++)seen.add(rows.get(n).eventId);serial.submit(()->store.recordExposed(original,seen,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
        int generation=(int)field(controller,"feedGeneration");long epoch=(long)field(controller,"explicitRefreshEpoch");ui(()->state(original,true));
        check(onUi(()->(boolean)field(home,"loading")&&(boolean)field(home,"updateAvailable")),"actual home exposes an available pending batch while source collection is still loading");
        pull(false);Thread.sleep(600);boolean accepted=onUi(()->(long)field(controller,"explicitRefreshEpoch")>epoch);check(accepted,"real top pull accepts already available recommendations during collection");if(!accepted)return;
        await("pulled batch applied",()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=original);
        check(onUi(()->((List<FeedStore.Row>)field(home,"feed")).get(0).eventId.equals(rows.get(seenCount).eventId)),"pull presents the first same-quality unseen item through the real controller");
        check(onUi(()->(boolean)field(controller,"loading")&&(int)field(controller,"feedGeneration")==generation),"accepting pending content does not cancel or restart source collection");
        long after=(long)field(controller,"explicitRefreshEpoch");pull(false);await("loading-time local refresh ends",()->!(boolean)field(controller,"explicitRefreshRunning"));check(onUi(()->(long)field(controller,"explicitRefreshEpoch")==after+1&&(boolean)field(controller,"loading")&&(int)field(controller,"feedGeneration")==generation),"with no pending batch a loading-time pull refreshes locally without restarting collection");
        final long cancelledEpoch=(long)field(controller,"explicitRefreshEpoch");
        ui(()->state((long)field(controller,"snapshot"),true));pull(true);Thread.sleep(200);check(onUi(()->(long)field(controller,"explicitRefreshEpoch")==cancelledEpoch&&list.getTranslationY()==0),"cancelled pull leaves the pending batch untouched and resets displacement");
    }
    private void exposureCase(List<FeedStore.Row> rows,long original)throws Exception{
        Thread.sleep(1700);FeedStore.Row row=rows.get(0);long before=shown(row);check(before>0,"first card has a real completed visible dwell before any refresh request");
        Object view=field(home,"feed");blocked();ui(()->{state(original,true);((View)field(home,"updates")).performClick();((View)field(home,"updates")).performClick();});Thread.sleep(1700);
        ui(controller::stop);release.countDown();serial.submit(()->{}).get(5,TimeUnit.SECONDS);test.waitForIdleSync();long after=shown(row);report.append("EXPOSURE_BOUNDARY before=").append(before).append(" afterCancelled=").append(after).append('\n');
        check(before==after,"queued and coalesced refresh cancelled before presentation does not re-expose the unchanged old list");
        check(onUi(()->field(home,"feed")==view&&(long)field(controller,"snapshot")==original),"cancelled request preserves actual feed identity");
        ui(()->{controller.start();controller.interacting(true);state(original,true);((View)field(home,"updates")).performClick();});await("successful same-batch presentation",()->!(boolean)field(controller,"explicitRefreshRunning"));Thread.sleep(1700);
        check(shown(row)>after,"an actually accepted refresh allows the same visible card to earn a new dwell");
        check(onUi(()->((FeedExposureTracker)field(home,"exposure")).dueAt()==-1),"accepted dwell settles without a periodic exposure timer");
    }
    private void capture(String name)throws Exception{CountDownLatch drawn=new CountDownLatch(1);ui(()->{View root=home.getWindow().getDecorView();root.postOnAnimation(()->root.postOnAnimation(()->root.post(drawn::countDown)));});if(!drawn.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Frame capture deadline");android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();if(image==null)throw new IllegalStateException("Screenshot unavailable");try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),name))){if(!image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out))throw new IllegalStateException("PNG failed");}finally{image.recycle();}}
    private boolean fitsOneSwipe(){
        long total=0;for(int n=0;n<list.getCount();n++){View row=list.getAdapter().getView(n,null,list);row.measure(View.MeasureSpec.makeMeasureSpec(list.getWidth(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));total+=row.getMeasuredHeight();}
        int viewport=list.getHeight()-list.getPaddingTop()-list.getPaddingBottom();report.append("BATCH count=").append(list.getCount()).append(" rowsPx=").append(total).append(" viewportPx=").append(viewport).append('\n');return list.getCount()==1||total<=(long)viewport*8/5;
    }
    private void swipeUp()throws Exception{
        int[] bounds=new int[4];ui(()->{int[] point=new int[2];list.getLocationOnScreen(point);bounds[0]=point[0];bounds[1]=point[1];bounds[2]=list.getWidth();bounds[3]=list.getHeight();});
        float x=bounds[0]+bounds[2]*.5f,from=bounds[1]+bounds[3]*.85f,to=bounds[1]+bounds[3]*.20f;long start=SystemClock.uptimeMillis();
        for(int n=0;n<=20;n++){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==20?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,from+(to-from)*n/20,0);try{test.sendPointerSync(event);}finally{event.recycle();}if(n<20)Thread.sleep(25);}test.waitForIdleSync();
    }
    @SuppressWarnings("unchecked") private void batchCase(List<FeedStore.Row> rows,long original)throws Exception{
        int size=rows.size();check(size>0&&size<20&&onUi(()->list.getCount()==size&&((List<?>)field(controller,"allRows")).size()==70),"home adapts its finite batch while keeping all 70 ranked candidates");
        check(onUi(()->fitsOneSwipe()),"actual measured rows fit the viewport plus one swipe");
        swipeUp();await("one real swipe reaches list bottom",()->!list.canScrollVertically(1));Thread.sleep(250);
        long epoch=(long)field(controller,"explicitRefreshEpoch");pull(false);
        check(onUi(()->list.getCount()==size&&((List<?>)field(home,"feed")).size()==size&&(long)field(controller,"explicitRefreshEpoch")==epoch),"dragging at the bottom neither grows the batch nor starts a refresh");
        List<String> exposed=new ArrayList<>();for(FeedStore.Row row:rows)exposed.add(row.eventId);
        serial.submit(()->store.recordExposed(original,exposed,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
        String next=((List<FeedStore.Row>)field(controller,"allRows")).get(size).eventId;
        String nextTitle=((List<FeedStore.Row>)field(controller,"allRows")).get(size).candidate.title;
        ViewTreeObserver.OnPreDrawListener gate=()->false;java.util.concurrent.atomic.AtomicInteger draws=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger firstFrameBusy=new java.util.concurrent.atomic.AtomicInteger(-1);
        java.util.concurrent.atomic.AtomicInteger firstFrameContent=new java.util.concurrent.atomic.AtomicInteger(-1);
        ViewTreeObserver.OnDrawListener drawn=()->{draws.incrementAndGet();if((long)field(controller,"snapshot")!=original&&firstFrameBusy.compareAndSet(-1,(boolean)field((RefreshIndicatorDrawable)field(home,"refreshIndicator"),"busy")?1:0))firstFrameContent.set(containsText(list.getChildAt(0),nextTitle)?1:0);};
        ui(()->{list.setSelectionFromTop(0,0);list.getViewTreeObserver().addOnPreDrawListener(gate);list.getViewTreeObserver().addOnDrawListener(drawn);state(original,true);((View)field(home,"updates")).performClick();});
        try{
            await("new batch completes layout while frame is held",()->(long)field(controller,"snapshot")!=original&&!(boolean)field(controller,"explicitRefreshRunning"));
            check(onUi(()->!(boolean)field((RefreshIndicatorDrawable)field(home,"refreshIndicator"),"busy")&&draws.get()==0),"replacement layout settles the indicator before its first visible frame");
            check(onUi(()->list.getCount()>0&&list.getCount()<=20&&((List<FeedStore.Row>)field(home,"feed")).get(0).eventId.equals(next)),"explicit refresh prepares the next unseen adaptive batch from the retained pool");
        }finally{ui(()->{list.getViewTreeObserver().removeOnPreDrawListener(gate);list.invalidate();});}
        await("drawn batch finishes refresh",()->draws.get()>0&&!(boolean)field(controller,"explicitRefreshRunning"));
        check(onUi(()->draws.get()>0&&!(boolean)field((RefreshIndicatorDrawable)field(home,"refreshIndicator"),"busy")&&(boolean)field(controller,"loading")),"animation finishes after a real new-list draw and ignores unrelated collection");
        check(firstFrameBusy.get()==0,"the first actual frame of the replacement list already has an idle refresh dot");
        check(firstFrameContent.get()==1,"that same first frame contains the replacement headline in the actual first card view");
        ui(()->list.getViewTreeObserver().removeOnDrawListener(drawn));
        check(serial.submit(()->store.snapshotInfo((long)field(controller,"snapshot")).getInt("count")).get(5,TimeUnit.SECONDS)==70,"refresh retains the full pool rather than truncating stored ranking");
        int refreshedSize=onUi(()->fitsOneSwipe())?list.getCount():0;swipeUp();await("second batch bottom",()->!list.canScrollVertically(1));Thread.sleep(300);
        check(refreshedSize>0&&onUi(()->list.getCount()==refreshedSize),"the refreshed batch also fits one swipe without automatic pagination");
        DiscoveryActivity previous=home;DiscoveryController previousController=controller;
        blocked();
        ui(()->{list.setSelectionFromTop(0,0);((View)field(home,"updates")).performClick();});
        check(onUi(()->(boolean)field(controller,"explicitRefreshRunning")),"queued replacement still shows busy before data is available");
        Instrumentation.ActivityMonitor monitor=test.addMonitor(DiscoveryActivity.class.getName(),null,false);
        try{
            ui(previous::recreate);release.countDown();Activity replacement=test.waitForMonitorWithTimeout(monitor,8000);
            if(!(replacement instanceof DiscoveryActivity)||replacement==previous)throw new IllegalStateException("Actual replacement home not observed");
            home=(DiscoveryActivity)replacement;controller=(DiscoveryController)field(home,"controller");list=(ListView)field(home,"list");serial=(ExecutorService)field(controller,"storage");store=(FeedStore)field(controller,"store");
            await("recreated home draws its bounded batch",()->home.hasWindowFocus()&&(boolean)field(controller,"ready")&&list.getChildCount()>0);
            check(onUi(()->list.getCount()>0&&list.getCount()<20&&fitsOneSwipe()&&((List<?>)field(controller,"allRows")).size()==70),"real activity recreation preserves the adaptive batch and complete ranked pool");
            check(onUi(()->!(boolean)field(previousController,"explicitRefreshRunning")&&field(previous,"refreshDraw")==null&&!(boolean)field(home,"refreshing")),"recreation cancels the old queued refresh and does not revive its animation in the new home");
        }finally{test.removeMonitor(monitor);release.countDown();}
    }
    private void animationCase(long original)throws Exception{
        RefreshIndicatorDrawable indicator=(RefreshIndicatorDrawable)field(home,"refreshIndicator");android.widget.TextView button=(android.widget.TextView)field(home,"updates");
        ui(()->state(original,true));check(onUi(()->!indicator.animating()&&!(boolean)field(indicator,"busy")),"ongoing collection behind an already displayed feed does not animate the completed refresh");
        ui(()->home.refreshWork(true));check(onUi(indicator::animating),"the explicit refresh visibly animates the existing control");
        float[] angles={0,0};ui(()->angles[0]=(float)field(indicator,"angle"));Thread.sleep(120);ui(()->angles[1]=(float)field(indicator,"angle"));
        check(angles[0]!=angles[1],"ring angle actually advances across rendered frames");
        check(onUi(()->button.getText().length()==0&&button.getCompoundDrawables()[0]==indicator&&button.getWidth()>=Math.round(48*home.getResources().getDisplayMetrics().density)),"20dp ring keeps the original48dp clickable target and adds no extra button");
        FeedNavigationLayout navigation=(FeedNavigationLayout)field(home,"navigation");int distance=Math.round(300*home.getResources().getDisplayMetrics().density);
        ui(()->list.smoothScrollBy(distance,180));await("forward scroll hides indicator",()->!navigation.toolbarVisible()&&!indicator.animating());
        check(onUi(()->!indicator.animating()),"actual list scroll callbacks stop animation while toolbar is hidden");
        ui(()->list.smoothScrollBy(-distance,180));await("reverse scroll restores indicator",()->navigation.toolbarVisible()&&indicator.animating());
        check(onUi(indicator::animating),"reverse list scroll reveals the still-active indicator");
        ui(()->test.callActivityOnPause(home));check(onUi(()->!indicator.animating()),"pause callback cancels animation frames");
        ui(()->{test.callActivityOnResume(home);controller.interacting(true);state(original,true);home.refreshWork(true);});check(onUi(indicator::animating),"return to focused feed resumes only its still-active explicit refresh");
        ui(()->home.refreshWork(false));check(onUi(()->!indicator.animating()&&!(boolean)field(indicator,"busy")&&(boolean)field(home,"loading")&&!String.valueOf(button.getStateDescription()).contains("正在刷新")),"explicit completion restores dot and accessibility while collection still runs");
        ui(()->state(original,false));
        blocked();
        long epoch=(long)field(controller,"explicitRefreshEpoch");Object visible=field(home,"feed");
        ui(()->{button.performClick();button.performClick();button.performClick();});
        ui(()->{try{Method work=DiscoveryController.class.getDeclaredMethod("work",Callable.class,java.util.function.Consumer.class);work.setAccessible(true);work.invoke(controller,(Callable<Object>)()->{throw new java.io.IOException("synthetic storage failure");},(java.util.function.Consumer<Object>)ignored->{});}catch(Exception e){throw new IllegalStateException(e);}});
        Thread.sleep(120);
        check(onUi(()->(boolean)field(controller,"explicitRefreshRunning")&&(boolean)field(home,"refreshing")&&indicator.animating()&&!(boolean)field(home,"loading")),"real queued local refresh animates before any network/source loading");
        check(onUi(()->(long)field(controller,"explicitRefreshEpoch")==epoch+1&&list.getFirstVisiblePosition()==0),"three taps share one request and retain return-to-top behavior");capture("refresh-busy.png");
        release.countDown();await("failed queued refresh stopped",()->!(boolean)field(controller,"explicitRefreshRunning")&&!indicator.animating());serial.submit(()->{}).get(5,TimeUnit.SECONDS);test.waitForIdleSync();
        check(onUi(()->!(boolean)field(home,"refreshing")&&!(boolean)field(indicator,"busy")&&field(home,"feed")==visible),"actual storage error restores the dot and retains the displayed list");capture("refresh-idle.png");
        long nextEpoch=(long)field(controller,"explicitRefreshEpoch");ui(()->{state((long)field(controller,"snapshot"),true);button.performClick();});
        await("local publication during collection",()->!(boolean)field(controller,"explicitRefreshRunning"));
        check(onUi(()->(long)field(controller,"explicitRefreshEpoch")==nextEpoch+1&&(boolean)field(home,"loading")&&!indicator.animating()&&!(boolean)field(indicator,"busy")),"real local publication stops its ring without waiting for ongoing source collection");
    }
    @SuppressWarnings("unchecked") void run(){String database="test-ui-refresh-boundary-"+UUID.randomUUID()+".db";try{
        if(animation&&(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.MODEL.startsWith("sdk_gphone")))throw new IllegalStateException("Animation fixture requires isolated emulator");
        int count=pull||animation||batch?70:3;long now=System.currentTimeMillis();List<HotCandidate> items=new ArrayList<>();for(int n=0;n<count;n++)items.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99800000+n),"刷新展示边界受控中文话题"+n,"","",false,n+1,null,now));
        try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),items,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
        home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(home,"controller");list=(ListView)field(home,"list");serial=(ExecutorService)field(controller,"storage");store=(FeedStore)field(controller,"store");
        await("actual focused list",()->home.hasWindowFocus()&&(boolean)field(controller,"ready")&&!((List<?>)field(home,"feed")).isEmpty()&&list.getChildCount()>=3);ui(()->controller.interacting(true));List<FeedStore.Row> rows=new ArrayList<>((List<FeedStore.Row>)field(home,"feed"));long original=(long)field(controller,"snapshot");
        if(batch)batchCase(rows,original);else if(animation)animationCase(original);else if(pull)pullCase(rows,original);else exposureCase(rows,original);
    }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
    finally{release.countDown();ui(()->{if(home!=null)home.finish();});test.waitForIdleSync();if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
