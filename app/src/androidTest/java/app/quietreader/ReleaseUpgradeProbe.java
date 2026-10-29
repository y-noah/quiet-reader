package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Cross-APK fixture using only stable Android storage/UI APIs, never production classes. */
final class ReleaseUpgradeProbe {
    private static final String DATABASE="personalized-hot-topics-v1.db",MARKER="release-upgrade-probe";
    private final Instrumentation test;private final boolean seed;
    private final StringBuilder report=new StringBuilder("ISOLATED MINIFIED CROSS-APK DATA; synthetic fixture, not a real authenticated account\n");
    private int checks,failures;private Activity activity;
    ReleaseUpgradeProbe(Instrumentation test,boolean seed){this.test=test;this.seed=seed;}
    private Context context(){return test.getTargetContext();}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private void check(boolean ok,String message){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(message).append('\n');}
    private SQLiteDatabase db(){return SQLiteDatabase.openDatabase(context().getDatabasePath(DATABASE).getPath(),null,SQLiteDatabase.OPEN_READWRITE);}
    private static long number(SQLiteDatabase db,String sql,String... args){try(Cursor c=db.rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
    private static String text(SQLiteDatabase db,String sql,String... args){try(Cursor c=db.rawQuery(sql,args)){return c.moveToFirst()?c.getString(0):"";}}
    private static boolean column(SQLiteDatabase db,String table,String name){try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){while(c.moveToNext())if(name.equals(c.getString(c.getColumnIndexOrThrow("name"))))return true;}return false;}
    private static String hash(String value)throws Exception{StringBuilder out=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)))out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
    private View find(View root,String value){if(root instanceof TextView&&value.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),value);if(found!=null)return found;}return null;}
    private void launch(){activity=test.startActivitySync(new Intent().setClassName(context().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();}
    private void feedback(SQLiteDatabase db,String id,String action,String scope,String target,String sample,String source,long at){ContentValues v=new ContentValues();v.put("id",id);v.put("action",action);v.put("scope",scope);v.put("target",target);v.put("at",at);v.put("sample",sample);if(column(db,"feedback","source"))v.put("source",source);db.insertOrThrow("feedback",null,v);}
    private void seed()throws Exception{
        if(context().getSharedPreferences(MARKER,0).contains("id"))throw new IllegalStateException("Fixture already exists; preserve it and verify rather than reseed");
        if(!context().getDatabasePath(DATABASE).exists()){launch();ui(activity::finish);test.waitForIdleSync();activity=null;}
        long now=System.currentTimeMillis();String nonce=UUID.randomUUID().toString(),url="https://www.zhihu.com/question/9900950101",title="升级保留测试：文字与讨论阅读",snippet="这是一条合成收藏与反馈，用于验证跨安装包保留数据。",id=hash("ZHIHU\n"+url),metadata=hash("hot-title-v2\n"+title+"\n"+snippet);
        JSONObject payload=new JSONObject().put("source","ZHIHU").put("url",url).put("title",title).put("snippet",snippet).put("heat","").put("video",false).put("rank",1).put("publishedAt",JSONObject.NULL).put("observedAt",now);
        String history=new JSONObject().put("position",321).put("expanded",new JSONArray().put("fixture-section")).put("at",now).toString();
        String sourceValue;int version;
        try(SQLiteDatabase db=db()){
            version=db.getVersion();sourceValue=column(db,"feedback","source")?"ZHIHU":"";db.beginTransaction();try{
                db.execSQL("UPDATE sources SET enabled=0");db.execSQL("UPDATE sources SET enabled=1,retry_at=?,status='STALE' WHERE id='ZHIHU'",new Object[]{now+86400000L});
                ContentValues c=new ContentValues();c.put("id",id);c.put("source","ZHIHU");c.put("payload",payload.toString());c.put("text_hash",metadata);c.put("first_seen",now);c.put("last_seen",now);c.put("rank",1);c.put("active",1);db.insertOrThrow("candidates",null,c);
                ContentValues saved=new ContentValues();saved.put("id",id);saved.put("event_id",id);saved.put("payload",payload.toString());saved.put("saved_at",now);db.insertOrThrow("saved",null,saved);
                feedback(db,"upgrade-content-"+nonce,"MORE","sample",id+"@"+metadata,title+"\n"+snippet,"ZHIHU",now);
                feedback(db,"upgrade-platform-"+nonce,"LESS","source","V2EX","V2EX","V2EX",now+1);
                db.execSQL("UPDATE meta SET value=value+1 WHERE key IN ('sources','profile')");db.execSQL("INSERT OR REPLACE INTO meta VALUES ('interest_setup_done',1)");db.execSQL("INSERT OR REPLACE INTO meta VALUES ('read_learning_disabled',1)");db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        context().getSharedPreferences("reading-history",0).edit().putString(url,history).commit();
        context().getSharedPreferences("appearance",0).edit().putInt("mode",2).putInt("spacing",2).commit();context().getSharedPreferences("MainActivity",0).edit().putInt("font",23).commit();
        context().getSharedPreferences("source-session",0).edit().putBoolean("ZHIHU-desktop",true).putBoolean("V2EX-web",true).commit();
        CountDownLatch cookie=new CountDownLatch(1);boolean[] accepted={false};ui(()->CookieManager.getInstance().setCookie("https://upgrade.quiet-reader.invalid/","qr_upgrade_probe="+nonce+"; Max-Age=86400; Secure; SameSite=Lax",ok->{accepted[0]=ok;cookie.countDown();}));
        if(!cookie.await(5,TimeUnit.SECONDS)||!accepted[0])throw new IllegalStateException("Synthetic Cookie fixture rejected");CookieManager.getInstance().flush();
        context().getSharedPreferences(MARKER,0).edit().putString("id",id).putString("nonce",nonce).putString("url",url).putString("title",title).putString("payload",payload.toString()).putString("history",history).putString("source",sourceValue).putInt("schema",version).commit();
        report.append("seedSchema=").append(version).append('\n');verifyStorage(false);
    }
    private void verifyStorage(boolean upgraded)throws Exception{
        android.content.SharedPreferences marker=context().getSharedPreferences(MARKER,0);String id=marker.getString("id",""),nonce=marker.getString("nonce",""),url=marker.getString("url","");if(id.isEmpty())throw new IllegalStateException("Missing persisted fixture marker");
        try(SQLiteDatabase db=db()){
            check(db.getVersion()==(upgraded?6:marker.getInt("schema",-1)),"expected database schema is readable after package transition");
            check(marker.getString("payload","").equals(text(db,"SELECT payload FROM saved WHERE id=?",id)),"saved article identity and payload remain unchanged");
            check(number(db,"SELECT COUNT(*) FROM candidates WHERE id=? AND active=1",id)==1,"cached candidate survives package replacement");
            check(number(db,"SELECT COUNT(*) FROM feedback WHERE id IN (?,?) AND undo_of IS NULL","upgrade-content-"+nonce,"upgrade-platform-"+nonce)==2,"explicit content and platform feedback both survive");
            if(column(db,"feedback","source"))check(marker.getString("source","").equals(text(db,"SELECT source FROM feedback WHERE id=?","upgrade-content-"+nonce)),"known or legacy-unknown feedback source is preserved without guessing");
            check(number(db,"SELECT enabled FROM sources WHERE id='ZHIHU'")==1&&number(db,"SELECT enabled FROM sources WHERE id='HUPU'")==0&&number(db,"SELECT enabled FROM sources WHERE id='V2EX'")==0,"existing source switches are not reset");
        }
        check(marker.getString("history","").equals(context().getSharedPreferences("reading-history",0).getString(url,"")),"reading position and expanded section metadata remain unchanged");
        check(context().getSharedPreferences("appearance",0).getInt("mode",-1)==2&&context().getSharedPreferences("appearance",0).getInt("spacing",-1)==2&&context().getSharedPreferences("MainActivity",0).getInt("font",-1)==23,"reader appearance and font preferences survive");
        check(context().getSharedPreferences("source-session",0).getBoolean("ZHIHU-desktop",false)&&context().getSharedPreferences("source-session",0).getBoolean("V2EX-web",false),"source desktop and browser session preferences survive");
        String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie("https://upgrade.quiet-reader.invalid/"));check(cookie[0]!=null&&Arrays.asList(cookie[0].split(";\\s*")).contains("qr_upgrade_probe="+nonce),"persistent synthetic WebView Cookie survives; values are not exported");
    }
    void run(){
        try{
            if(!context().getPackageName().equals("app.quietreader.installprobe"))throw new IllegalStateException("Upgrade fixture is allowed only in the isolated package");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"target is a non-debuggable minified package");
            if(seed)seed();else{
                launch();long until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){boolean[] ready={false};ui(()->ready[0]=find(activity.getWindow().getDecorView(),"稍后读")!=null);if(ready[0])break;Thread.sleep(50);}
                ui(()->{View tab=find(activity.getWindow().getDecorView(),"稍后读");if(tab==null)throw new IllegalStateException("Saved entry unavailable");tab.performClick();});
                boolean shown=false;until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){boolean[] ready={false};ui(()->ready[0]=find(activity.getWindow().getDecorView(),context().getSharedPreferences(MARKER,0).getString("title",""))!=null);if(ready[0]){shown=true;break;}Thread.sleep(50);}
                check(shown,"upgraded production saved page displays the retained article");verifyStorage(true);feedbackUi();
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append('\n');}
        finally{if(activity!=null){ui(activity::finish);test.waitForIdleSync();}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private boolean visible(String value){android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root==null)return false;for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(value))if(node.isVisibleToUser()&&value.contentEquals(node.getText()==null?"":node.getText()))return true;return false;}
    private void menu(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<until){android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(label)){
            if(!node.isVisibleToUser()||!label.contentEquals(node.getText()==null?"":node.getText()))continue;
            android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);if(bounds.isEmpty())continue;long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}test.waitForIdleSync();return;
        }Thread.sleep(50);}throw new IllegalStateException("Expected release menu unavailable");
    }
    private void feedbackUi()throws Exception{
        ui(()->{View entry=find(activity.getWindow().getDecorView(),"设置");if(entry!=null)entry.performClick();});menu("反馈记录 / 撤销");menu("筛选");menu("平台偏好");menu("平台偏好 · 少看 · V2EX");
        long until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until&&!visible("撤销反馈"))Thread.sleep(50);
        check(visible("反馈详情")&&visible("撤销反馈"),"retained platform preference opens through the minified feedback filter and detail UI");
        String nonce=context().getSharedPreferences(MARKER,0).getString("nonce","");try(SQLiteDatabase db=db()){check(number(db,"SELECT COUNT(*) FROM feedback WHERE id IN (?,?) AND undo_of IS NULL","upgrade-content-"+nonce,"upgrade-platform-"+nonce)==2,"viewing retained feedback does not undo either explicit decision");}
        menu("返回");menu("关闭");
    }
}