package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.util.function.BooleanSupplier;

/** Framework UI only: no production reflection, fixtures, model bypass, or phone access. */
final class ReleaseMemoryRecoveryProbe {
    private final Instrumentation test;
    private Activity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("R8 MEMORY RECOVERY: injected Activity trim, not an actual system low-memory event\n");
    ReleaseMemoryRecoveryProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error[0]=failure;}});if(error[0]!=null)throw new IllegalStateException("Test UI action",error[0]);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View root,String label){if(android.text.TextUtils.equals(label,root.getContentDescription())||root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void click(String label){ui(()->{View found=find(activity.getWindow().getDecorView(),label);if(found==null)throw new IllegalStateException("Missing control: "+label);found.performClick();});test.waitForIdleSync();}
    private void describe(android.view.accessibility.AccessibilityNodeInfo root,StringBuilder text){if(root==null)return;if(root.getText()!=null)text.append(root.getText()).append('|');for(int n=0;n<root.getChildCount();n++)describe(root.getChild(n),text);}
    private boolean dialogContains(String text){long until=SystemClock.elapsedRealtime()+2000;StringBuilder actual=new StringBuilder();do{android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return true;actual.setLength(0);describe(root,actual);SystemClock.sleep(40);}while(SystemClock.elapsedRealtime()<until);report.append("DIAGNOSTIC_EXPECT ").append(text).append(" ACTUAL ").append(actual).append('\n');return false;}
    private void dialog(String label){long until=SystemClock.elapsedRealtime()+4000;do{android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){if(!android.text.TextUtils.equals(label,node.getText()))continue;while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}}SystemClock.sleep(30);}while(SystemClock.elapsedRealtime()<until);throw new IllegalStateException("Missing dialog action: "+label);}
    private void details(){click("设置");click("高级与诊断");click("本机推荐状态");}
    private boolean rankingAlive(){ActivityManager manager=(ActivityManager)test.getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);java.util.List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();if(processes!=null)for(ActivityManager.RunningAppProcessInfo process:processes)if(process.processName.equals(test.getTargetContext().getPackageName()+":ranking"))return true;return false;}
    void run(){try{
        if(!Build.HARDWARE.equals("ranchu")||!Build.PRODUCT.startsWith("sdk_gphone")||Build.VERSION.SDK_INT!=36||!test.getTargetContext().getPackageName().equals("app.quietreader")||!test.getTargetContext().getSharedPreferences("normal-package-upgrade-probe",0).contains("nonce"))throw new IllegalStateException("Dedicated retained upgrade fixture required; never run on user phone");
        check((test.getTargetContext().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"installed target is non-debuggable ordinary package");
        activity=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
        long ready=SystemClock.elapsedRealtime()+5000;while(!onUi(()->find(activity.getWindow().getDecorView(),"设置")!=null)){if(SystemClock.elapsedRealtime()>ready)throw new IllegalStateException("Native home unavailable");Thread.sleep(25);}
        ui(()->activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));details();
        check(dialogContains("内存紧张"),"real R8 Activity trim exposes the memory-pause reason in diagnostics");
        dialog("继续整理");Thread.sleep(1500);
        check(onUi(()->find(activity.getWindow().getDecorView(),"设置")!=null),"explicit continue returns to the minimal feed during recovery");
        check(!rankingAlive(),"explicit continue does not launch a ranking process during memory recovery");
        details();check(dialogContains("内存"),"early explicit continuation retains a memory-recovery explanation");dialog("关闭");click("返回");click("返回");
        Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.DiscoveryActivity",null,false);
        try{ui(activity::recreate);Activity recreated=test.waitForMonitorWithTimeout(monitor,8000);if(recreated==null)throw new IllegalStateException("Recreated home unavailable");activity=recreated;test.waitForIdleSync();}finally{test.removeMonitor(monitor);}
        details();dialog("继续整理");Thread.sleep(1500);details();
        check(dialogContains("内存"),"recreated R8 controller shares the existing process memory-recovery guard");
        check(!rankingAlive(),"recreation and another explicit continue still do not reload a model process");dialog("关闭");
    }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
    finally{if(activity!=null){ui(activity::finish);test.waitForIdleSync();}}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
