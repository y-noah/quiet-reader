package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.UUID;

/** Current source controls must never send a reader back to the removed tabbed home. */
final class LegacyNavigationRemovalProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    LegacyNavigationRemovalProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable work){test.runOnMainSync(work);test.waitForIdleSync();}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View view,String label){
        if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())||view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=find(((ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}
        return null;
    }
    private ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){ListView found=list(((ViewGroup)view).getChildAt(n));if(found!=null)return found;}return null;}
    private void click(String label){ui(()->{View found=find(home.getWindow().getDecorView(),label);check(found!=null&&found.performClick(),"open "+label);});}
    private AccessibilityNodeInfo window(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+5000;
        do{AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(label).isEmpty())return root;Thread.sleep(50);}while(SystemClock.elapsedRealtime()<until);
        return null;
    }
    private void back(){check(test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK),"dispatch system back");test.waitForIdleSync();}
    private boolean shown(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+5000;boolean[] visible={false};
        do{ui(()->{View found=find(home.getWindow().getDecorView(),label);visible[0]=found!=null&&found.isShown();});if(visible[0])return true;Thread.sleep(50);}while(SystemClock.elapsedRealtime()<until);
        return false;
    }
    void run(){
        String database="test-legacy-navigation-"+UUID.randomUUID()+".db";
        try{
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            click("设置");click("内容来源");
            ui(()->{ListView sources=list(home.getWindow().getDecorView());View row=sources==null?null:sources.getChildAt(0);View card=row instanceof ViewGroup&&((ViewGroup)row).getChildCount()>0?((ViewGroup)row).getChildAt(0):null;check(card!=null&&card.performClick(),"open actual source card menu");});
            AccessibilityNodeInfo root=window("登录 / 打开来源页");
            check(root!=null,"source menu has an accessibility window");
            if(root!=null){
                check(root.findAccessibilityNodeInfosByText("浏览该平台榜单").isEmpty(),"removed board entry is absent from source menu");
                for(String action:new String[]{"登录 / 打开来源页","刷新候选","通过网页读取","多看这个平台","少看这个平台"})check(!root.findAccessibilityNodeInfosByText(action).isEmpty(),"source control remains reachable: "+action);
                check(!root.findAccessibilityNodeInfosByText("查看最近更新").isEmpty()||!root.findAccessibilityNodeInfosByText("查看失败详情").isEmpty(),"source status remains reachable for both successful and failed updates");
            }
            back();check(window("内容来源")!=null,"source menu closes to source controls");back();
            check(shown("阅读外观"),"source controls return to grouped settings");
            back();check(shown("设置"),"settings returns to the single feed");
            ui(()->check(find(home.getWindow().getDecorView(),"平台选择")==null,"single feed has no old platform bar"));
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            try{
                ui(()->home.startActivity(new Intent(home,MainActivity.class)));
                Activity empty=test.waitForMonitorWithTimeout(monitor,4000);
                long until=SystemClock.elapsedRealtime()+4000;while(empty!=null&&!empty.isFinishing()&&SystemClock.elapsedRealtime()<until)Thread.sleep(40);
                check(empty!=null&&empty.isFinishing(),"reader without an article exits instead of constructing a legacy board");
            }finally{test.removeMonitor(monitor);}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append('\n');}
        finally{if(home!=null)ui(home::finish);test.getTargetContext().deleteDatabase(database);}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append('\n').toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
