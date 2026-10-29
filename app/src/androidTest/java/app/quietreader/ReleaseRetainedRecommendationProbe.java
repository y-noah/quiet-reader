package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.*;

/** Retained ordinary data, actual native request button and production controller.
 * Never seeds candidates, feedback, vectors, logits, cookies or source settings. */
final class ReleaseRetainedRecommendationProbe {
    private final Instrumentation test;
    private Activity home;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReleaseRetainedRecommendationProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private void ui(Runnable action){
        Throwable[] error={null};
        test.runOnMainSync(()->{try{action.run();}catch(Throwable failure){error[0]=failure;}});
        if(error[0]!=null)throw new IllegalStateException("UI diagnostic failed",error[0]);
    }
    private int idleSeconds(Bundle args,String key){
        String value=args.getString(key,"0");
        if(!value.matches("[0-9]{1,2}"))throw new IllegalArgumentException(key+" must be 0..90 seconds");
        int seconds=Integer.parseInt(value);
        if(seconds>90)throw new IllegalArgumentException(key+" must be 0..90 seconds");
        return seconds;
    }
    private void phase(String name){
        try{
            String line="MEMORY_PHASE "+new org.json.JSONObject().put("phase",name)
                .put("wallTimeMs",System.currentTimeMillis()).put("elapsedRealtimeMs",SystemClock.elapsedRealtime())
                .put("pid",android.os.Process.myPid()).toString()+"\n";
            report.append(line);Bundle status=new Bundle();status.putString("stream",line);test.sendStatus(0,status);
        }catch(org.json.JSONException impossible){throw new IllegalStateException(impossible);}
    }
    private void idle(int seconds,boolean foreground)throws Exception{
        if(seconds==0)return;
        String label=foreground?"foreground-idle":"background-idle";
        checkFocus(foreground,label+" begins");phase(label+"-start");
        long until=SystemClock.elapsedRealtime()+seconds*1000L;
        while(SystemClock.elapsedRealtime()<until)Thread.sleep(Math.min(1000,Math.max(1,until-SystemClock.elapsedRealtime())));
        checkFocus(foreground,label+" ends");phase(label+"-end");
    }
    private void checkFocus(boolean foreground,String label){
        boolean[] valid={false};ui(()->valid[0]=home!=null&&!home.isFinishing()&&!home.isDestroyed()&&home.hasWindowFocus()==foreground);
        check(valid[0],label+" with retained live activity and expected focus");
    }
    private View find(View root,String label){
        if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;
        if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),label);if(v!=null)return v;}
        return null;
    }
    private ListView list(View root){if(root instanceof ListView)return (ListView)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){ListView v=list(((ViewGroup)root).getChildAt(n));if(v!=null)return v;}return null;}
    private void menu(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<until){
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){
                if(!label.contentEquals(node.getText()==null?"":node.getText()))continue;
                while(node!=null&&!node.isClickable())node=node.getParent();
                if(node!=null&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Missing native menu: "+label);
    }
    private long scalar(String sql){
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}
    }
    private Set<Integer> workers(){
        Set<Integer> pids=new HashSet<>();ActivityManager manager=(ActivityManager)test.getTargetContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> all=manager.getRunningAppProcesses();
        if(all!=null)for(ActivityManager.RunningAppProcessInfo p:all)if(p.processName.equals("app.quietreader:ranking"))pids.add(p.pid);
        return pids;
    }
    void run(Bundle args){
        boolean screenKept=false;
        try{
            int foregroundIdle=idleSeconds(args,"foregroundIdleSeconds"),backgroundIdle=idleSeconds(args,"backgroundIdleSeconds");
            Context context=test.getTargetContext();boolean phone="true".equals(args.getString("phone"));
            check("app.quietreader".equals(context.getPackageName())&&Build.VERSION.SDK_INT==36&&(phone?"PHY110".equals(Build.MODEL):Build.MODEL.startsWith("sdk_gphone")),"retained controller diagnostic is confined to authorized ordinary Find X7 Ultra or ordinary API36 emulator");
            check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual ordinary target is minified and nondebuggable");
            home=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long until=SystemClock.elapsedRealtime()+30000;boolean[] ready={false};
            do{ui(()->{ListView v=list(home.getWindow().getDecorView());ready[0]=home.hasWindowFocus()&&v!=null&&v.getCount()>0&&find(home.getWindow().getDecorView(),"设置")!=null;});if(!ready[0])Thread.sleep(100);}while(!ready[0]&&SystemClock.elapsedRealtime()<until);
            check(ready[0],"real retained home has focus and a nonempty list");
            boolean[] kept={false};ui(()->{kept[0]=(home.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0;home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});screenKept=kept[0];
            long feedbackBefore=scalar("SELECT COUNT(*) FROM feedback"),profileBefore=scalar("SELECT value FROM meta WHERE key='profile'"),vectorsBefore=scalar("SELECT COUNT(*) FROM embeddings"),scoresBefore=scalar("SELECT COUNT(*) FROM reranks"),activeBefore=scalar("SELECT value FROM meta WHERE key='active_snapshot'");
            ui(()->{View settings=find(home.getWindow().getDecorView(),"设置");if(settings==null||!settings.performClick())throw new IllegalStateException("Settings button unavailable");});
            menu("高级与诊断");menu("本机推荐状态");menu("继续整理");
            check(true,"actual Continue organizing native button requests normal controller work");
            phase("controller-observation-start");
            Set<Integer> observed=new HashSet<>();long start=SystemClock.elapsedRealtime();
            while(SystemClock.elapsedRealtime()-start<45000){observed.addAll(workers());Thread.sleep(250);}
            long vectorsAfter=scalar("SELECT COUNT(*) FROM embeddings"),scoresAfter=scalar("SELECT COUNT(*) FROM reranks"),activeAfter=scalar("SELECT value FROM meta WHERE key='active_snapshot'");
            report.append("CONTROLLER observedWorkerCount=").append(observed.size()).append(" vectorsBefore=").append(vectorsBefore).append(" vectorsAfter=").append(vectorsAfter).append(" scoresBefore=").append(scoresBefore).append(" scoresAfter=").append(scoresAfter).append(" activeBefore=").append(activeBefore).append(" activeAfter=").append(activeAfter).append(" elapsedMs=").append(SystemClock.elapsedRealtime()-start).append('\n');
            phase("controller-observation-end");
            idle(foregroundIdle,true);
            if(backgroundIdle>0){
                check(test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME),"actual system Home action accepted");
                long focusUntil=SystemClock.elapsedRealtime()+5000;boolean[] focused={true};
                do{ui(()->focused[0]=home.hasWindowFocus());if(focused[0])Thread.sleep(50);}while(focused[0]&&SystemClock.elapsedRealtime()<focusUntil);
                idle(backgroundIdle,false);
            }
            check(scalar("SELECT COUNT(*) FROM feedback")==feedbackBefore&&scalar("SELECT value FROM meta WHERE key='profile'")==profileBefore,"native request and observation do not manufacture preferences");
            check(!observed.isEmpty(),"normal production controller actually starts a model worker during observation; cached-only runs do not count as resource coverage");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{phase("finishing");boolean original=screenKept;ui(()->{if(home!=null){if(!original)home.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);home.finish();}});}
        report.append("scope=retained real native controller request, read-only counts, no fixture or feedback injection; instrumentation affects process priority and survival, optional idle phases test lifecycle release, not ordinary LMK recovery; observed workers do not prove personal quality, simultaneous PSS completeness or whole-App peak\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
