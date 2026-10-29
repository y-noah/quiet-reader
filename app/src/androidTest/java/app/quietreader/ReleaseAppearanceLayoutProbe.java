package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.*;
import java.util.*;

/** Actual minified appearance controls via the connected accessibility tree; no reflection. */
final class ReleaseAppearanceLayoutProbe {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures;
    private Activity activity;
    ReleaseAppearanceLayoutProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private AccessibilityNodeInfo node(AccessibilityNodeInfo root,String prefix){
        if(root==null)return null;
        String text=String.valueOf(root.getText()),description=String.valueOf(root.getContentDescription());
        if(root.isVisibleToUser()&&(text.startsWith(prefix)||description.startsWith(prefix)))return root;
        for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=node(root.getChild(n),prefix);if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo scroll(AccessibilityNodeInfo root){
        if(root==null)return null;if(root.isScrollable())return root;
        for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=scroll(root.getChild(n));if(found!=null)return found;}return null;
    }
    private AccessibilityNodeInfo range(AccessibilityNodeInfo root){
        if(root==null)return null;if("android.widget.SeekBar".contentEquals(root.getClassName())&&root.isVisibleToUser())return root;
        for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=range(root.getChild(n));if(found!=null)return found;}return null;
    }
    private android.widget.TextView preview(android.view.View root){
        if(root instanceof android.widget.TextView&&((android.widget.TextView)root).getText().toString().startsWith("阅读预览"))return (android.widget.TextView)root;
        if(root instanceof android.view.ViewGroup)for(int n=0;n<((android.view.ViewGroup)root).getChildCount();n++){android.widget.TextView found=preview(((android.view.ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;
    }
    private void fontRange()throws Exception{
        AccessibilityNodeInfo slider=range(test.getUiAutomation().getRootInActiveWindow());
        AccessibilityNodeInfo.RangeInfo info=slider==null?null:slider.getRangeInfo();
        check(info!=null&&info.getMin()==16&&info.getMax()==28,"font accessibility range reports actual sizes 16 to 28");
        if(slider==null)throw new IllegalStateException("Font slider absent");
        Bundle value=new Bundle();value.putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,28);
        check(slider.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),value),"connected font range accepts actual size 28");test.waitForIdleSync();Thread.sleep(150);
        check(test.getTargetContext().getSharedPreferences("MainActivity",0).getInt("font",19)==28,"accessibility font change persists the actual size");
        final boolean[] live={false};test.runOnMainSync(()->{android.widget.TextView view=preview(activity.getWindow().getDecorView());float expected=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,28,activity.getResources().getDisplayMetrics());live[0]=view!=null&&Math.abs(view.getTextSize()-expected)<.1;report.append("FONT_PREVIEW actualPx=").append(view==null?-1:view.getTextSize()).append(" expectedPx=").append(expected).append('\n');});
        check(live[0],"font preview updates immediately without a touch-release callback");
        check(node(test.getUiAutomation().getRootInActiveWindow(),"阅读字号 · 28")!=null,"visible font value matches accessibility selection");
    }
    private AccessibilityNodeInfo reach(String prefix)throws Exception{
        for(int attempt=0;attempt<16;attempt++){
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow(),found=node(root,prefix);
            if(found!=null){Rect bounds=new Rect();found.getBoundsInScreen(bounds);float density=test.getTargetContext().getResources().getDisplayMetrics().density;
                if(bounds.height()>=48*density-1)return found;}
            AccessibilityNodeInfo container=scroll(root);if(container==null||!container.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break;
            test.waitForIdleSync();Thread.sleep(120);
        }
        throw new IllegalStateException("No fully reachable action: "+prefix);
    }
    private void action(String prefix)throws Exception{
        AccessibilityNodeInfo target=reach(prefix);Rect rect=new Rect();target.getBoundsInScreen(rect);
        float density=test.getTargetContext().getResources().getDisplayMetrics().density;
        check("android.widget.Button".contentEquals(target.getClassName())&&target.isFocusable()&&target.isClickable(),"button role and focusability: "+prefix);
        check(rect.width()>=48*density-1&&rect.height()>=48*density-1,"reachable 48dp target: "+prefix);
        check(target.performAction(AccessibilityNodeInfo.ACTION_CLICK),"connected accessibility action accepted: "+prefix);
        test.waitForIdleSync();
    }
    private void capture(String suffix)throws Exception{
        // Let normal window transitions settle; this is visual QA, not latency evidence.
        Thread.sleep(400);
        Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Screenshot unavailable");
        String name="release-appearance-"+suffix+".png";
        try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        report.append("SCREENSHOT ").append(name).append('\n');
    }
    private void cancel(String label)throws Exception{
        AccessibilityNodeInfo target=node(test.getUiAutomation().getRootInActiveWindow(),"取消");
        if(target==null||!target.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new IllegalStateException("Cannot cancel "+label);
        test.waitForIdleSync();long until=SystemClock.elapsedRealtime()+5000;boolean closed=false;
        while(SystemClock.elapsedRealtime()<until){AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(node(root,"取消")==null&&node(root,"返回 · 阅读设置")!=null){closed=true;break;}Thread.sleep(100);}
        check(closed,"cancel returns to the actual appearance page: "+label);if(!closed)throw new IllegalStateException("Chooser still open");
    }
    void run(Bundle args){
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences("appearance",0);Map<String,?> before=new HashMap<>(prefs.getAll());
        SharedPreferences readerPrefs=test.getTargetContext().getSharedPreferences("MainActivity",0);boolean hadFont=readerPrefs.contains("font");int priorFont=readerPrefs.getInt("font",19);
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target only");
            int mode="dark".equals(args.getString("theme"))?2:1;prefs.edit().putInt("mode",mode).commit();
            Map<String,?> expected=new HashMap<>(prefs.getAll());
            activity=test.startActivitySync(new Intent().setClassName(test.getTargetContext(),"app.quietreader.SettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->activity.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));test.waitForIdleSync();Thread.sleep(400);
            android.content.res.Configuration config=activity.getResources().getConfiguration();report.append("CONFIG widthDp=").append(config.screenWidthDp).append(" fontScale=").append(config.fontScale).append(" theme=").append(mode).append('\n');
            AccessibilityNodeInfo top=null;long until=SystemClock.elapsedRealtime()+5000;
            while(top==null&&SystemClock.elapsedRealtime()<until){top=node(test.getUiAutomation().getRootInActiveWindow(),"返回 · 阅读设置");if(top==null)Thread.sleep(100);}
            report.append("INITIAL_BACK class=").append(top==null?"absent":top.getClassName()).append(" focusable=").append(top!=null&&top.isFocusable()).append('\n');
            check(top!=null&&"android.widget.Button".contentEquals(top.getClassName())&&top.isFocusable(),"appearance back exposes an accessible button");
            fontRange();capture(args.getString("theme","light")+"-top");
            for(String label:new String[]{"显示模式","阅读配色","链接与选中颜色","正文字体","正文行距"}){
                action(label);check(node(test.getUiAutomation().getRootInActiveWindow(),"取消")!=null,"actual chooser opens: "+label);
                cancel(label);
            }
            action("恢复默认阅读设置");check(node(test.getUiAutomation().getRootInActiveWindow(),"恢复字号、字体、行距与配色？")!=null,"reset opens its confirmation instead of changing preferences");
            capture(args.getString("theme","light")+"-reset-confirmation");cancel("恢复默认阅读设置");
            check(expected.equals(prefs.getAll()),"opening and cancelling all controls preserves appearance preferences");capture(args.getString("theme","light")+"-bottom");
            // Back is fixed outside the scrolling content.
            action("返回 · 阅读设置");check(activity.isFinishing(),"accessible back actually closes appearance settings");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{
            test.runOnMainSync(()->{if(activity!=null){activity.getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);activity.finish();}});test.waitForIdleSync();
            SharedPreferences.Editor restore=prefs.edit();if(before.containsKey("mode"))restore.putInt("mode",(Integer)before.get("mode"));else restore.remove("mode");restore.commit();
            check(before.equals(prefs.getAll()),"probe restores appearance preference state");
            SharedPreferences.Editor fontRestore=readerPrefs.edit();if(hadFont)fontRestore.putInt("font",priorFont);else fontRestore.remove("font");fontRestore.commit();
            check(readerPrefs.contains("font")==hadFont&&readerPrefs.getInt("font",19)==priorFont,"probe restores original font preference");
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
