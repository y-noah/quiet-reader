package app.quietreader;
import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
/** Framework UI and read-only aggregate DB evidence against the unchanged minified APK. */
final class ReleaseSourceBalanceProbe {
    private final Instrumentation test;private Activity home,reader;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReleaseSourceBalanceProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private SQLiteDatabase db(){return SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);}
    private long scalar(String sql){try(SQLiteDatabase d=db();Cursor c=d.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private long active(){return scalar("SELECT value FROM meta WHERE key='active_snapshot'");}
    private View refresh(View node){String desc=String.valueOf(node.getContentDescription());if(List.of("刷新","查看更新","正在刷新").contains(desc))return node;if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){View result=refresh(((ViewGroup)node).getChildAt(n));if(result!=null)return result;}return null;}
    private boolean idle(){boolean[] idle={false};ui(()->{View button=refresh(home.getWindow().getDecorView());idle[0]=button!=null&&!"正在刷新".contentEquals(button.getContentDescription());});return idle[0];}
    private JSONObject counts()throws Exception{JSONObject counts=new JSONObject();try(SQLiteDatabase d=db();Cursor c=d.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=? ORDER BY position LIMIT 15",new String[]{""+active()})){while(c.moveToNext()){String source=new JSONObject(c.getString(0)).getString("source");counts.put(source,counts.optInt(source)+1);}}return counts;}
    private JSONArray prefix()throws Exception{JSONArray result=new JSONArray();try(SQLiteDatabase d=db();Cursor c=d.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=? ORDER BY position LIMIT 5",new String[]{""+active()})){while(c.moveToNext())result.put(new JSONObject(c.getString(0)).getString("source"));}return result;}
    private WebView reading(View node){if(node instanceof WebView){String url=((WebView)node).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/?render="))return (WebView)node;}if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){WebView found=reading(((ViewGroup)node).getChildAt(n));if(found!=null)return found;}return null;}
    private JSONObject dom(WebView view)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={"null"};ui(()->{boolean js=view.getSettings().getJavaScriptEnabled();view.getSettings().setJavaScriptEnabled(true);view.evaluateJavascript("JSON.stringify({parts:document.querySelectorAll('main .part').length,chars:(document.querySelector('main')?.textContent||'').length,images:document.querySelectorAll('main img').length,overflow:document.documentElement.scrollWidth>innerWidth+2})",value->{view.getSettings().setJavaScriptEnabled(js);result[0]=value;done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("DOM timeout");Object value=new JSONTokener(result[0]).nextValue();return value instanceof String?new JSONObject((String)value):new JSONObject();}
    void run(){
        try{
            if(!"app.quietreader".equals(test.getTargetContext().getPackageName())||!"PHY110".equals(Build.MODEL))throw new IllegalStateException("authorized ordinary phone only");
            long feedback=scalar("SELECT COUNT(*) FROM feedback");
            home=test.startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            long until=SystemClock.elapsedRealtime()+35000;while((active()==0||!idle())&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
            check(idle(),"home launch ends its refresh state");
            boolean[] focused={false};ui(()->focused[0]=home.hasWindowFocus());check(focused[0],"phone is unlocked and New has foreground focus");
            for(int n=0;n<3;n++){
                long before=active(),start=SystemClock.elapsedRealtime();ui(()->{View button=refresh(home.getWindow().getDecorView());if(button!=null)button.performClick();});
                until=start+20000;while((active()==before||!idle())&&SystemClock.elapsedRealtime()<until)Thread.sleep(80);
                ui(()->focused[0]=home.hasWindowFocus());
                check(active()!=before&&idle(),"refresh "+(n+1)+" changes presentation and ends spinner in "+(SystemClock.elapsedRealtime()-start)+" ms; focus="+focused[0]+" snapshot="+before+"->"+active()+" idle="+idle());
                JSONObject counts=counts();report.append("ROUND ").append(n+1).append(' ').append(counts).append('\n');
                JSONArray first=prefix();Set<String> distinct=new HashSet<>();for(int i=0;i<first.length();i++)distinct.add(first.getString(i));report.append("FIRST5 ").append(first).append('\n');
                check(distinct.size()>=3,"first five cards interleave available platforms");
                check(counts.length()>=3&&counts.optInt("BAIDU")<=2&&counts.optInt("WEIBO")<=2,"actual first15 respects source LESS and has at least three sources");Thread.sleep(600);
            }
            check(scalar("SELECT COUNT(*) FROM sources WHERE id IN ('BILIBILI','DOUYIN') AND enabled=1")==0,"both user-disabled video sources remain off");
            String url="https://cn.nytimes.com/china/20261030/china-communist-party-purges/";
            JSONObject item=new JSONObject().put("source","NYT").put("url",url).put("title","习近平清洗行动继续：中共宣布33名高官被开除党籍").put("detail","").put("video",false);
            reader=test.startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(()->reader.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            JSONObject body=new JSONObject();until=SystemClock.elapsedRealtime()+35000;
            do{WebView[] own={null};ui(()->own[0]=reading(reader.getWindow().getDecorView()));if(own[0]!=null){body=dom(own[0]);if(body.optInt("parts")>0&&body.optInt("chars")>300)break;}Thread.sleep(120);}while(SystemClock.elapsedRealtime()<until);
            check(body.optInt("parts")>0&&body.optInt("chars")>300&&!body.optBoolean("overflow"),"NYT real native reader renders prose without horizontal overflow: "+body);ui(reader::finish);reader=null;
            check(feedback==scalar("SELECT COUNT(*) FROM feedback"),"verification generated no taste feedback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle out=new Bundle();out.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
}
