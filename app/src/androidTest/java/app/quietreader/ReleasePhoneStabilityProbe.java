package app.quietreader;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Minified signed app, actual UI and delivery history; no synthetic feed or cookies. */
final class ReleasePhoneStabilityProbe {
    private final Instrumentation test;private Activity home;private ListView list;private int checks,failures;
    private final StringBuilder report=new StringBuilder();private File output;
    ReleasePhoneStabilityProbe(Instrumentation test){this.test=test;}
    private void save(){if(output!=null)try{Files.write(output.toPath(),report.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');save();}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String label,BooleanSupplier condition,long timeout)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException(label);Thread.sleep(20);}}
    private SQLiteDatabase db(){return SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);}
    private long scalar(String sql){try(SQLiteDatabase db=db();Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private Set<String> ids(){Set<String> result=new HashSet<>();try(SQLiteDatabase db=db();Cursor c=db.rawQuery("SELECT event_id FROM snapshot_rows WHERE snapshot=(SELECT value FROM meta WHERE key='active_snapshot') ORDER BY position LIMIT 15",null)){while(c.moveToNext())result.add(c.getString(0));}return result;}
    private long active(){return scalar("SELECT value FROM meta WHERE key='active_snapshot'");}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View child=find(((ViewGroup)root).getChildAt(n),label);if(child!=null)return child;}return null;}
    private <T extends View>T view(View root,Class<T> kind){if(kind.isInstance(root))return kind.cast(root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){T child=view(((ViewGroup)root).getChildAt(n),kind);if(child!=null)return child;}return null;}
    private View dot(){View root=home.getWindow().getDecorView();View hit=find(root,"刷新");if(hit==null)hit=find(root,"查看更新");if(hit==null)hit=find(root,"正在刷新");return hit;}
    private void launch()throws Exception{home=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));await("ordinary home has fifteen visible rows",()->{list=view(home.getWindow().getDecorView(),ListView.class);return home.hasWindowFocus()&&list!=null&&list.getCount()==15&&list.getChildCount()>0;},15000);}
    private void pull()throws Exception{
        ui(()->list.setSelectionFromTop(0,0));Thread.sleep(150);int[] b=new int[4];ui(()->{int[] xy=new int[2];list.getLocationOnScreen(xy);b[0]=xy[0];b[1]=xy[1];b[2]=list.getWidth();b[3]=list.getHeight();});float x=b[0]+b[2]*.5f,from=b[1]+b[3]*.2f,to=b[1]+b[3]*.55f;long t=SystemClock.uptimeMillis();for(int n=0;n<=14;n++){MotionEvent event=MotionEvent.obtain(t,t+n*20,n==0?0:n==14?1:2,x,from+(to-from)*n/14f,0);test.sendPointerSync(event);event.recycle();if(n<14)Thread.sleep(20);}
    }
    void run(){output=new File(test.getTargetContext().getExternalFilesDir(null),"phone-presentation-stability.txt");
        try{
            check("app.quietreader".equals(test.getTargetContext().getPackageName())&&"PHY110".equals(Build.MODEL),"authorized phone and original app target");
            long feedback=scalar("SELECT COUNT(*) FROM feedback");launch();check(scalar("PRAGMA user_version")==10,"data migration to delivery history completed in place");
            Set<String> previous=ids(),seen=new HashSet<>(previous);long pool=scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot=(SELECT value FROM meta WHERE key='active_snapshot')");
            report.append("POOL rows=").append(pool).append('\n');
            for(int round=0;round<8;round++){
                long before=active(),started=SystemClock.elapsedRealtime();
                if(round%3==2)pull();else ui(()->{View dot=dot();if(dot==null)throw new IllegalStateException("refresh dot missing");dot.performClick();dot.performClick();dot.performClick();});
                await("refresh ends with next actual batch",()->active()!=before&&find(home.getWindow().getDecorView(),"正在刷新")==null,8000);
                long ms=SystemClock.elapsedRealtime()-started;Set<String> current=ids();Set<String> overlap=new HashSet<>(previous);overlap.retainAll(current);
                report.append("ROUND ").append(round+1).append(" ms=").append(ms).append(" rows=").append(current.size()).append(" previousOverlap=").append(overlap.size()).append('\n');
                check(current.size()==15&&onUi(()->list.getCount()==15),"refresh retains fifteen real UI rows");check(ms<3500,"local refresh completes below 3.5 second regression threshold");check(overlap.isEmpty()||pool<30,"adjacent batches do not repeat while enough content exists");
                long settled=active();Thread.sleep(350);check(active()==settled,"rapid repeated taps produce one presentation, without a late second swap");previous=current;seen.addAll(current);save();
            }
            for(int n=0;n<3;n++){
                Set<String> last=ids();long prior=active();ui(home::finish);test.waitForIdleSync();launch();await("reopened batch is committed",()->active()!=prior,8000);
                Set<String> current=ids(),same=new HashSet<>(last);same.retainAll(current);check(same.isEmpty()||pool<30,"fresh reopen advances persisted batches rather than reusing first fifteen");
            }
            check(feedback==scalar("SELECT COUNT(*) FROM feedback"),"refresh and delivery records do not mutate taste feedback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');save();Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
