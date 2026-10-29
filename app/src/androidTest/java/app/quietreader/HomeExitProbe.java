package app.quietreader;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.KeyEvent;
import java.lang.reflect.Method;

/** Reproduce retained same-app home activities without touching phone data. */
final class HomeExitProbe {
 private final Instrumentation test; private int checks,failures;
 private final StringBuilder log=new StringBuilder();
 HomeExitProbe(Instrumentation test){this.test=test;}
 private void check(boolean value,String label){checks++;if(!value)failures++;log.append(value?"PASS ":"FAIL ").append(label).append('\n');}
 void run(){
  Activity first=null,second=null;String db="test-home-exit-"+System.nanoTime()+".db";
  try{
   if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Isolated emulator required");
   Intent home=new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",db).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   first=test.startActivitySync(new Intent(home).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK));
   Instrumentation.ActivityMonitor monitor=test.addMonitor(DiscoveryActivity.class.getName(),null,false);
   Activity root=first;test.runOnMainSync(()->root.startActivity(new Intent(home).setFlags(0)));
   second=test.waitForMonitorWithTimeout(monitor,5000);test.removeMonitor(monitor);
   if(second==null)throw new IllegalStateException("Second home was not created");test.waitForIdleSync();
   Activity a=first,b=second;
   check(a!=b&&a.getTaskId()==b.getTaskId(),"controlled explicit entry retains two homes in one task");
   test.runOnMainSync(()->{try{Method m=DiscoveryActivity.class.getDeclaredMethod("show",String.class);m.setAccessible(true);m.invoke(b,"settings");}catch(Exception e){throw new RuntimeException(e);}});
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
   java.lang.reflect.Field page=DiscoveryActivity.class.getDeclaredField("page");page.setAccessible(true);
   check("feed".equals(page.get(b))&&!a.isFinishing()&&!b.isFinishing(),"settings back returns to home without exiting the app task");
   test.runOnMainSync(()->{try{Method m=DiscoveryActivity.class.getDeclaredMethod("updateAndReturnToTop");m.setAccessible(true);for(int i=0;i<3;i++)m.invoke(b);}catch(Exception e){throw new RuntimeException(e);}});
   test.waitForIdleSync();check(!a.isFinishing()&&!b.isFinishing(),"refresh updates existing homes without closing or replacing the screen");
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
   long end=SystemClock.elapsedRealtime()+5000;boolean[] ended={false};
   do{test.runOnMainSync(()->ended[0]=(a.isFinishing()||a.isDestroyed())&&(b.isFinishing()||b.isDestroyed()));if(!ended[0])Thread.sleep(50);}while(!ended[0]&&SystemClock.elapsedRealtime()<end);
   check(ended[0],"one system back from feed closes retained same-app home stack");
  }catch(Throwable e){failures++;log.append(android.util.Log.getStackTraceString(e));}
  finally{Activity a=first,b=second;test.runOnMainSync(()->{if(b!=null&&!b.isDestroyed())b.finish();if(a!=null&&!a.isDestroyed())a.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(db);}
  log.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle out=new Bundle();out.putString("stream",log.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
 }
}
