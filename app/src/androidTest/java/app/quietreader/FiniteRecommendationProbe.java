package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.ListView;
import android.view.accessibility.AccessibilityEvent;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import java.io.*;
import static app.quietreader.Models.*;

/** Isolated real SQLite + actual home controls. Does not use live source accounts. */
final class FiniteRecommendationProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;private DiscoveryController controller;private FeedStore store;private ExecutorService serial;
    private int checks,failures;private final StringBuilder report=new StringBuilder();private volatile boolean exhaustedToast;
    FiniteRecommendationProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String label,BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+15000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException(label);Thread.sleep(30);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> visible(){return new ArrayList<>((List<FeedStore.Row>)field(home,"feed"));}
    private boolean text(View view,String value){if(view instanceof android.widget.TextView&&value.contentEquals(((android.widget.TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)if(text(((ViewGroup)view).getChildAt(n),value))return true;return false;}
    private void shot(String name)throws Exception{test.waitForIdleSync();Thread.sleep(200);android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name+".png"))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    private void pull()throws Exception{
        ListView list=(ListView)field(home,"list");int[] point=new int[2];ui(()->list.getLocationOnScreen(point));
        float density=home.getResources().getDisplayMetrics().density,x=list.getWidth()*.5f,from=point[1]+110*density,to=from+220*density;long start=SystemClock.uptimeMillis();
        for(int n=0;n<=13;n++){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==13?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,from+(to-from)*n/13f,0);try{test.sendPointerSync(event);}finally{event.recycle();}if(n<13)Thread.sleep(14);}
        await("pull stopped",()->!(boolean)field(controller,"explicitRefreshRunning"));serial.submit(()->{}).get(10,TimeUnit.SECONDS);test.waitForIdleSync();
    }
    private void refresh()throws Exception{ui(()->((View)field(home,"updates")).performClick());await("refresh stopped",()->!(boolean)field(controller,"explicitRefreshRunning"));serial.submit(()->{}).get(10,TimeUnit.SECONDS);test.waitForIdleSync();}
    void run(){String database="test-ui-finite-"+UUID.randomUUID()+".db";
        try{
            List<HotCandidate> candidates=new ArrayList<>();List<FeedStore.Row> ranked=new ArrayList<>();long now=System.currentTimeMillis();
            for(int n=0;n<300;n++){HotCandidate c=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99700000+n),"有限推荐验证中文话题 "+n,"","",false,n+1,null,now);candidates.add(c);ranked.add(new FeedStore.Row(c.id,c,"受控偏好匹配",List.of(c),n>=225?0:2));}
            Collections.reverse(ranked);
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){
                seed.ensureCandidatePolicy();for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);
                seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);seed.finishInterestSetup(List.of(),Map.of(),now);
                List<FeedStore.Row> selected=seed.selectRecommendations(ranked,now,false);
                check(selected.size()==75&&selected.get(0).candidate.sourceRank==300,"preference selection admits 75 relevant rows rather than top source heat ranks");
                long id=seed.commitRanking(seed.version("sources"),seed.profile(now).getLong("version"),now,"personal","all",selected);seed.activateSnapshot(id);
            }
            test.getUiAutomation().setOnAccessibilityEventListener(event->{if(event.getEventType()==AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED&&event.getText().toString().contains("暂时没有新的推荐内容"))exhaustedToast=true;});
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(home,"controller");serial=(ExecutorService)field(controller,"storage");store=(FeedStore)field(controller,"store");
            await("focused fifteen",()->home.hasWindowFocus()&&(boolean)field(controller,"ready")&&visible().size()==15);ui(()->controller.interacting(true));
            Set<String> seen=new HashSet<>();
            for(int round=0;round<5;round++){
                if(round>0){if(round%2==0)pull();else refresh();}
                List<FeedStore.Row> rows=visible();check(rows.size()==15,"screen "+(round+1)+" contains exactly fifteen recommendations");
                for(FeedStore.Row row:rows)check(seen.add(row.eventId),"new visible identity "+row.candidate.title);
                await("bound first title",()->text(home.getWindow().getDecorView(),rows.get(0).candidate.title));
                int expectedCount=round==4?16:15;check(onUi(()->((ListView)field(home,"list")).getCount()==expectedCount),"actual adapter has fifteen data rows and only the final exhaustion footer, round "+(round+1));shot("finite-round-"+(round+1));
            }
            ListView finalList=(ListView)field(home,"list");ui(()->finalList.setSelection(15));
            await("visible exhaustion footer",()->finalList.getFirstVisiblePosition()>0&&text(home.getWindow().getDecorView(),"暂时没有新的推荐内容"));check(true,"last batch has a persistent, actually bound exhaustion footer");shot("finite-footer");ui(()->finalList.setSelection(0));
            long latestBefore=serial.submit(store::latestSnapshot).get(10,TimeUnit.SECONDS);
            long snapshot=(long)field(controller,"snapshot");String first=visible().get(0).eventId;
            refresh();Thread.sleep(300);check(exhaustedToast,"actual exhausted refresh displays no-new-recommendations message");
            check((long)field(controller,"snapshot")==snapshot&&visible().get(0).eventId.equals(first),"exhaustion preserves the actual last screen rather than cycling rejected content");shot("finite-exhausted");
            check(seen.size()==75,"five real home batches contain exactly 75 distinct recommendations");
            refresh();check(serial.submit(store::latestSnapshot).get(10,TimeUnit.SECONDS)==latestBefore,"repeated exhausted refresh is rejected without rebuilding snapshots");
            serial.submit(()->{
                List<FeedStore.Row> again=store.selectRecommendations(ranked,System.currentTimeMillis(),true);
                long id=store.commitRanking(store.version("sources"),store.profile(System.currentTimeMillis()).getLong("version"),System.currentTimeMillis(),"personal","all",again);
                long empty=store.presentationSnapshot(id,"all",List.of(),System.currentTimeMillis());
                check(store.snapshotInfo(empty).optInt("count")==0,"re-ranking same pool cannot refill exhausted selection");
                check(store.unpresented(again,System.currentTimeMillis()+8L*86400000).isEmpty(),"selection delivery survives expiry of seven-day presentation history");
                long chronological=store.commitRanking(store.version("sources"),store.profile(System.currentTimeMillis()).getLong("version"),System.currentTimeMillis(),"newest","all",again);
                long latest=store.refreshSnapshot(chronological,"all",System.currentTimeMillis());
                check(store.snapshotInfo(latest).optInt("count")==0,"chronological display also stops when its selected content is exhausted");
                return null;
            }).get(10,TimeUnit.SECONDS);
            HotCandidate newcomer=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_99700301","新增内容才能重新开放推荐","","",false,1,null,System.currentTimeMillis());
            candidates.add(newcomer);
            serial.submit(()->{store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),candidates,System.currentTimeMillis());return null;}).get(10,TimeUnit.SECONDS);
            ui(()->{try{Method prepare=DiscoveryController.class.getDeclaredMethod("prepare",boolean.class);prepare.setAccessible(true);prepare.invoke(controller,false);}catch(Exception error){throw new IllegalStateException(error);}});
            await("new content prepared",()->(long)field(controller,"pending")>0&&!(boolean)field(controller,"recommendationExhausted"));
            serial.submit(()->{List<FeedStore.Row> model=new ArrayList<>(ranked);model.add(0,new FeedStore.Row(newcomer.id,newcomer,"新内容兴趣匹配",List.of(newcomer),0));
                List<FeedStore.Row> refined=store.selectRecommendations(model,System.currentTimeMillis(),true);
                check(store.unpresented(refined,System.currentTimeMillis()).size()==1,"new arrival retains its model allowance when the previous window never finished inference");return null;
            }).get(10,TimeUnit.SECONDS);
            refresh();await("new title bound",()->visible().size()==1&&visible().get(0).candidate.id.equals(newcomer.id)&&text(home.getWindow().getDecorView(),newcomer.title));
            check(visible().size()==1,"genuine new arrival reopens recommendation with one item, without filling from rejected old tail");shot("finite-new-arrival");
            ui(()->home.finish());serial.awaitTermination(10,TimeUnit.SECONDS);
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(home,"controller");serial=(ExecutorService)field(controller,"storage");store=(FeedStore)field(controller,"store");
            await("restored actual screen",()->(boolean)field(controller,"ready")&&!visible().isEmpty());ui(()->controller.interacting(true));
            String restored=visible().get(0).eventId;refresh();
            check(visible().size()==1&&visible().get(0).eventId.equals(restored),"activity/store reopen preserves exhausted selection and last screen");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{test.getUiAutomation().setOnAccessibilityEventListener(null);ui(()->{if(home!=null)home.finish();});if(serial!=null)try{serial.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
