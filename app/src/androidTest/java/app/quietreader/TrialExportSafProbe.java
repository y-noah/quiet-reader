package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import app.quietreader.discovery.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Real Android document picker using only synthetic local observations. */
final class TrialExportSafProbe {
    private final Instrumentation test;private Activity activity;private int checks,failures;private final StringBuilder report=new StringBuilder();
    TrialExportSafProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String text,boolean editable){if(root==null)return null;if(root.isVisibleToUser()&&(editable?root.isEditable():text.equalsIgnoreCase(root.getText()==null?"":root.getText().toString())))return root;for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo value=find(root.getChild(n),text,editable);if(value!=null)return value;}return null;}
    private AccessibilityNodeInfo waitNode(String text,boolean editable)throws Exception{long until=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<until){AccessibilityNodeInfo value=find(test.getUiAutomation().getRootInActiveWindow(),text,editable);if(value!=null)return value;Thread.sleep(80);}throw new IOException("Missing visible control: "+text);}
    private void tap(String text)throws Exception{AccessibilityNodeInfo node=waitNode(text,false);if(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}android.graphics.Rect r=new android.graphics.Rect();node.getBoundsInScreen(r);long at=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,r.centerX(),r.centerY(),0),up=MotionEvent.obtain(at,at+60,MotionEvent.ACTION_UP,r.centerX(),r.centerY(),0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}test.waitForIdleSync();}
    private Object field(Object owner,String name)throws Exception{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void screenshot(String name)throws Exception{android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
    void run(){String suffix=UUID.randomUUID().toString(),feed="test-ui-export-"+suffix+".db",trial="test-trial-"+feed,filename="synthetic-evaluation-"+suffix+".db";File copy=new File(test.getTargetContext().getExternalFilesDir(null),filename);
        try{
            long now=System.currentTimeMillis();HotCandidate c=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/930003","系统保存选择器合成文字讨论","私有摘要不导出","",false,1,null,now);
            try(FeedStore store=new FeedStore(test.getTargetContext(),feed)){store.upsertSnapshot(Models.Source.V2EX,store.beginRefresh(Models.Source.V2EX),List.of(c),now);store.commitRanking(store.version("sources"),store.version("profile"),now,"base","all",List.of(new FeedStore.Row(c.id,c,"")));}
            try(RecommendationTrialStore store=new RecommendationTrialStore(test.getTargetContext(),trial)){String id=store.create(RecommendationTrialStore.freeze(List.of(new FeedStore.Row(c.id,c,"")),1,1,1,"all","base",now));store.label(id,0,"unsure",now+1);}
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",feed).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            tap("设置");tap("本轮推荐评价");tap("选择操作");tap("导出评价记录");tap("选择保存位置");
            AccessibilityNodeInfo edit=waitNode("filename",true);Bundle name=new Bundle();name.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,filename);check(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,name),"real system document picker accepts the synthetic export filename");
            screenshot("trial-export-system-picker.png");
            AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();boolean system=root!=null&&root.getPackageName()!=null&&!test.getTargetContext().getPackageName().contentEquals(root.getPackageName());check(system,"save destination is chosen in a separate system document provider");
            tap("Save");
            long until=SystemClock.elapsedRealtime()+12000;boolean finished=false;while(SystemClock.elapsedRealtime()<until){boolean[] value={false};test.runOnMainSync(()->{try{Object export=field(field(activity,"trialEvaluation"),"export");value[0]=field(export,"pending")==null&&!((boolean)field(export,"busy"));}catch(Exception error){throw new IllegalStateException(error);}});if(value[0]){finished=true;break;}Thread.sleep(60);}check(finished,"actual system URI result finishes the asynchronous production export");
            try(ParcelFileDescriptor descriptor=test.getUiAutomation().executeShellCommand("cat /sdcard/Download/"+filename);InputStream input=new FileInputStream(descriptor.getFileDescriptor());OutputStream output=new FileOutputStream(copy)){byte[] buffer=new byte[32768];int length;while((length=input.read(buffer))!=-1)output.write(buffer,0,length);}
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(copy.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor cursor=db.rawQuery("SELECT payload FROM sessions",null)){check(cursor.moveToFirst()&&!cursor.getString(0).contains("私有摘要")&&!cursor.getString(0).contains("https://"),"document saved through the real provider is a readable metadata-only SQLite snapshot");try(Cursor labels=db.rawQuery("SELECT choice FROM labels",null)){check(labels.moveToFirst()&&"unsure".equals(labels.getString(0)),"actual SAF bytes preserve the original uncertain choice");}}
            File staging=new File(activity.getCacheDir(),"trial-exports");check(staging.listFiles()==null||staging.listFiles().length==0,"successful real provider write removes private export staging");
            Object export=field(field(activity,"trialEvaluation"),"export");String restored="trial-observations-"+UUID.randomUUID()+".db";File staged=new File(staging,restored);TrialObservationSnapshot.create(test.getTargetContext().getDatabasePath(trial),staged);Bundle state=new Bundle();state.putString("trialExportFile",restored);
            test.runOnMainSync(()->{TrialExportUi owner=(TrialExportUi)export;owner.restore(state);Bundle saved=new Bundle();owner.save(saved);check(restored.equals(saved.getString("trialExportFile")),"pending export survives save and restoration of activity state");owner.result(Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://invalid.synthetic.export-provider/missing")));});
            until=SystemClock.elapsedRealtime()+8000;while(staged.exists()&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);check(!staged.exists(),"unavailable destination provider cleans the staging snapshot and permits retry");
            String missing="trial-observations-"+UUID.randomUUID()+".db";Bundle lost=new Bundle();lost.putString("trialExportFile",missing);
            test.runOnMainSync(()->{TrialExportUi owner=(TrialExportUi)export;owner.restore(lost);Bundle saved=new Bundle();owner.save(saved);check(missing.equals(saved.getString("trialExportFile")),"restored missing staging remains a known export request so failure is reported instead of silently ignored");owner.result(Activity.RESULT_OK,new Intent().setData(android.net.Uri.parse("content://invalid.synthetic.export-provider/missing")));});
            until=SystemClock.elapsedRealtime()+8000;boolean settled=false;while(SystemClock.elapsedRealtime()<until){boolean[] done={false};test.runOnMainSync(()->{try{done[0]=field(export,"pending")==null&&!((boolean)field(export,"busy"));}catch(Exception error){throw new IllegalStateException(error);}});if(done[0]){settled=true;break;}Thread.sleep(50);}check(settled,"missing restored staging releases export state after a failed save");
            screenshot("trial-export-after-save.png");check(copy.setReadable(true,false),"synthetic SAF copy available for read-only desktop compatibility verification");report.append("synthetic_snapshot=").append(filename).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');try{screenshot("trial-export-saf-failure.png");}catch(Exception ignored){}}
        finally{test.runOnMainSync(()->{if(activity!=null&&!activity.isFinishing())activity.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(feed);test.getTargetContext().deleteDatabase(trial);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
