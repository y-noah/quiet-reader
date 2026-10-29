package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.lang.reflect.*;
import org.json.*;
import static app.quietreader.Models.*;

/** SQLite migration/provenance and actual source-preference menu/controller. */
final class PlatformPreferenceProbe {
    private final Instrumentation test;private final boolean dark;private DiscoveryActivity activity;private ExecutorService storage;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    PlatformPreferenceProbe(Instrumentation test){this(test,false);}
    PlatformPreferenceProbe(Instrumentation test,boolean dark){this.test=test;this.dark=dark;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private HotCandidate candidate(Source source,int n){return new HotCandidate(source,"https://"+(source==Source.ZHIHU?"www.zhihu.com/question/":"www.v2ex.com/t/")+ (99009000+n),"中文平台偏好测试话题"+n,"","",false,n+1,null,System.currentTimeMillis());}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+7000;while(true){boolean[] ok={false};ui(()->ok[0]=condition.getAsBoolean());if(ok[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Platform UI deadline exceeded");Thread.sleep(30);}}
    private void dialogClick(String label)throws Exception{
        long end=SystemClock.elapsedRealtime()+4000;
        while(SystemClock.elapsedRealtime()<end){test.waitForIdleSync();AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText()==null?"":node.getText())){
            if(node.isVisibleToUser()){
                android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
                if(!bounds.isEmpty()){long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
                    try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}test.waitForIdleSync();return;}
            }
        }if(root!=null)scroll(root);Thread.sleep(100);}throw new IllegalStateException("Menu action missing: "+label);
    }
    private boolean scroll(AccessibilityNodeInfo node){if(node.isScrollable()&&node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int n=0;n<node.getChildCount();n++){AccessibilityNodeInfo child=node.getChild(n);if(child!=null&&scroll(child))return true;}return false;}
    private void migration(String database)throws Exception{
        HotCandidate old=candidate(Source.ZHIHU,1);long snapshot;
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            store.upsertSnapshot(old.source,store.beginRefresh(old.source),List.of(old),100);store.feedback("legacy","MORE","sample",CandidateOrder.versionTarget(old),101,null,old.modelText());app.quietreader.discovery.LegacySavedFixture.save(store,old.id,old,102);
            snapshot=store.commitRanking(store.version("sources"),store.version("profile"),103,"cold","all",List.of(new FeedStore.Row(old.id,old,"")));
            store.getWritableDatabase().execSQL("ALTER TABLE feedback DROP COLUMN source");store.getWritableDatabase().execSQL("DROP TABLE feed_exposure");store.getWritableDatabase().execSQL("DROP TABLE article_progress");store.getWritableDatabase().setVersion(3);
        }
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            JSONArray legacy=store.activeFeedback(104);
            check(store.getWritableDatabase().getVersion()==9&&legacy.length()==1&&legacy.getJSONObject(0).optString("source").isEmpty(),"v3 upgrade preserves legacy feedback with unknown source rather than guessing");
            check(app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.candidates(10,0).size()==1&&store.readSnapshot(snapshot,10,0).size()==1,"v3 upgrade preserves saved articles, candidates and frozen snapshot");
            check(new PlatformPreference(legacy).empty(),"old unknown source feedback cannot become artificial platform preference");
            long now=System.currentTimeMillis();for(int n=2;n<=4;n++)store.recordRead(candidate(Source.V2EX,n),now+n);
            JSONArray read=store.activeFeedback(now+10);check(read.length()==4&&new PlatformPreference(read).score(Source.V2EX)>0,"three distinct qualified reads persist actual platform and produce bounded weak prior");
            check(read.getJSONObject(1).getString("source").equals("V2EX"),"read feedback stores actual chosen platform");
            store.readLearningEnabled(false,now+11);check(new PlatformPreference(store.activeFeedback(now+12)).empty(),"disabling reading learning retracts its platform prior as well as content samples");
            HotCandidate saved=candidate(Source.V2EX,5);store.feedback("legacy-saved","SAVED","sample","saved:"+saved.id,now+13,now+14L*86_400_000,saved.modelText(),saved.source);
            check(store.activeFeedback(now+14).getJSONObject(1).getString("source").equals("V2EX"),"historical saved interest retains platform provenance");
            store.undo("legacy-saved",now+15);check(store.activeFeedback(now+16).length()==1,"undo retracts historical platform evidence without altering other events");
            store.feedback("source-more","MORE","source","V2EX",now+17,null,"V2EX",Source.V2EX);store.feedback("source-less","LESS","source","V2EX",now+18,null,"V2EX",Source.V2EX);
            check(new PlatformPreference(store.activeFeedback(now+19)).score(Source.V2EX)==-1,"latest explicit platform feedback has priority");store.undo("source-less",now+20);
            check(new PlatformPreference(store.activeFeedback(now+21)).score(Source.V2EX)==1,"undo restores previous explicit platform preference");
            store.resetProfile();check(store.activeFeedback(now+22).length()==0&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1,"reset clears content and platform preferences while preserving original saved article");
        }
    }
    void run(){String migrationDb="test-platform-migrate-"+UUID.randomUUID()+".db",database="test-ui-platform-"+UUID.randomUUID()+".db";
        android.content.SharedPreferences appearance=test.getTargetContext().getSharedPreferences("appearance",0);boolean hadAppearance=appearance.contains("mode");int priorAppearance=appearance.getInt("mode",0);
        try{
            appearance.edit().putInt("mode",dark?2:1).commit();migration(migrationDb);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){HotCandidate a=candidate(Source.ZHIHU,1),b=candidate(Source.V2EX,2);store.upsertSnapshot(a.source,store.beginRefresh(a.source),List.of(a),System.currentTimeMillis());store.upsertSnapshot(b.source,store.beginRefresh(b.source),List.of(b),System.currentTimeMillis());}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            android.content.res.Configuration config=activity.getResources().getConfiguration();report.append("CONFIG widthDp=").append(config.screenWidthDp).append(" fontScale=").append(config.fontScale).append(" dark=").append(dark).append('\n');
            DiscoveryController controller=(DiscoveryController)field(activity,"controller");storage=(ExecutorService)field(controller,"storage");await(()->(boolean)field(controller,"ready")&&((List<?>)field(activity,"feed")).size()==2);
            ui(()->{try{Method menu=DiscoveryActivity.class.getDeclaredMethod("sourceMenu",Source.class);menu.setAccessible(true);menu.invoke(activity,Source.V2EX);}catch(Exception error){throw new IllegalStateException(error);}});dialogClick("多看这个平台");await(()->(long)field(controller,"pending")>0);
            FeedStore store=(FeedStore)field(controller,"store");JSONArray feedback=storage.submit(()->store.activeFeedback(System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            check(feedback.length()==1&&feedback.getJSONObject(0).getString("scope").equals("source")&&feedback.getJSONObject(0).getString("target").equals("V2EX"),"actual source menu writes independent explicit platform feedback");
            check(new SemanticOrder.Profile(feedback).empty(),"source preference is not embedded as a content topic and needs no model inference");
            ui(()->controller.applyPending(true));await(()->((List<FeedStore.Row>)field(activity,"feed")).get(0).candidate.source==Source.V2EX);
            check(((List<FeedStore.Row>)field(activity,"feed")).get(0).reason.contains("平台偏好"),"applied real snapshot prefers requested platform with a visible reason");
            // Undo applies its rebuilt snapshot automatically. Waiting for pending
            // races accept(), which clears that transient value on the UI thread.
            ui(()->controller.undo(feedback.optJSONObject(0).optString("id")));
            await(()->((List<FeedStore.Row>)field(activity,"feed")).get(0).candidate.source==Source.ZHIHU);
            check(storage.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==0).get(5,TimeUnit.SECONDS),"controller undo restores neutral order and removes active platform preference");
            feedbackManagement(controller,store);
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{if(activity!=null)ui(activity::finish);test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);test.getTargetContext().deleteDatabase(migrationDb);android.content.SharedPreferences.Editor restore=appearance.edit();if(hadAppearance)restore.putInt("mode",priorAppearance);else restore.remove("mode");restore.commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private boolean visible(String label){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return false;for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(node.isVisibleToUser())return true;return false;}
    private void feedbackManagement(DiscoveryController controller,FeedStore store)throws Exception{
        long now=System.currentTimeMillis();HotCandidate kept=candidate(Source.ZHIHU,500);
        HotCandidate longContent=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/99009501","城市生活与公共服务观察".repeat(18),"完整反馈文字".repeat(90)+"尾部保留标记","",false,1,null,now);
        storage.submit(()->{
            app.quietreader.discovery.LegacySavedFixture.save(store,kept.id,kept,now-400000);store.feedback("history-source-more","MORE","source","V2EX",now-300000,null,"V2EX",Source.V2EX);
            for(int n=0;n<120;n++)store.recordRead(candidate(Source.ZHIHU,100+n),now-200000+n);
            store.feedback("history-long-content","MORE","sample",CandidateOrder.versionTarget(longContent),now-1,null,longContent.modelText(),Source.ZHIHU);
            store.feedback("history-source-less","LESS","source","V2EX",now,null,"V2EX",Source.V2EX);
        }).get(5,TimeUnit.SECONDS);
        JSONArray history=storage.submit(()->store.activeFeedback(System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
        ui(()->{try{Method m=DiscoveryActivity.class.getDeclaredMethod("feedbackHistory",JSONArray.class);m.setAccessible(true);m.invoke(activity,history);}catch(Exception error){throw new IllegalStateException(error);}});test.waitForIdleSync();
        boolean filter=visible("筛选");check(filter,"feedback history offers a filter when 120 weak reads coexist with explicit platform choices");
        if(!filter){test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);return;}
        check(visible("平台偏好 · 少看 · V2EX"),"latest explicit preference is reachable before older reading observations");
        dialogClick("筛选");dialogClick("平台偏好");test.waitForIdleSync();
        await(()->visible("反馈记录 · 平台偏好（2）"));capture("feedback-history-platform.png");
        check(visible("反馈记录 · 平台偏好（2）")&&!visible("近期阅读 · 弱参考"),"platform filter isolates both source decisions from reading evidence");
        dialogClick("平台偏好 · 少看 · V2EX");test.waitForIdleSync();
        await(()->visible("反馈详情"));capture("feedback-history-detail.png");
        check(visible("撤销反馈")&&storage.submit(()->new PlatformPreference(store.activeFeedback(System.currentTimeMillis())).score(Source.V2EX)==-1).get(5,TimeUnit.SECONDS),"opening feedback details does not undo its explicit choice");
        dialogClick("返回");dialogClick("筛选");dialogClick("阅读弱参考");test.waitForIdleSync();
        await(()->visible("反馈记录 · 阅读弱参考（120）"));
        check(visible("反馈记录 · 阅读弱参考（120）")&&!visible("平台偏好 · 少看 · V2EX"),"reading filter preserves all independent observations and excludes source choices");
        dialogClick("筛选");dialogClick("内容偏好");await(()->visible("反馈记录 · 内容偏好（1）"));
        check(!visible("尾部保留标记"),"content list uses a short preview rather than expanding the full long feedback");
        dialogClick("多看 · "+HotCandidate.clean(longContent.modelText(),80)+"…");await(()->visible("反馈详情"));
        check(visible("尾部保留标记")&&scroll(test.getUiAutomation().getRootInActiveWindow()),"long feedback details retain the complete sample in a scrollable message");
        capture("feedback-history-content-detail.png");check(fullAction("返回")&&fullAction("撤销反馈"),"long feedback detail keeps both complete actions visible at least 48dp high");dialogClick("返回");
        dialogClick("筛选");dialogClick("平台偏好");dialogClick("平台偏好 · 少看 · V2EX");dialogClick("撤销反馈");
        await(()->!visible("撤销反馈"));storage.submit(()->{}).get(5,TimeUnit.SECONDS);
        check(storage.submit(()->{JSONArray active=store.activeFeedback(System.currentTimeMillis());return active.length()==122&&new PlatformPreference(active).score(Source.V2EX)==1&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1;}).get(5,TimeUnit.SECONDS),"confirmed undo restores prior source choice while retaining weak reads and saved content");
    }
    private void capture(String name)throws Exception{
        Thread.sleep(350);String fileName=name.replace(".png",dark?"-dark.png":"-light.png");
        CountDownLatch frames=new CountDownLatch(1);ui(()->activity.getWindow().getDecorView().postOnAnimation(()->activity.getWindow().getDecorView().postOnAnimation(frames::countDown)));if(!frames.await(3,TimeUnit.SECONDS))throw new IllegalStateException("History visible frame timeout");
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("History screenshot unavailable");
        try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),fileName))){if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file))throw new IllegalStateException("History screenshot failed");}finally{bitmap.recycle();}
        report.append("SCREENSHOT ").append(fileName).append('\n');
    }
    private boolean fullAction(String label){
        AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return false;
        android.graphics.Rect frame=new android.graphics.Rect();root.getBoundsInScreen(frame);
        for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label))if(label.contentEquals(node.getText()==null?"":node.getText())&&node.isVisibleToUser()){
            android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);report.append("ACTION ").append(label).append(" bounds=").append(bounds).append(" frame=").append(frame).append('\n');
            return frame.contains(bounds)&&bounds.height()+1>=48*activity.getResources().getDisplayMetrics().density;
        }return false;
    }
}
