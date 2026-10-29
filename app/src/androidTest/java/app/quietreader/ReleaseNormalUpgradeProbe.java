package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.webkit.CookieManager;
import android.widget.TextView;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Retained original v46 -> local v47 normal-package upgrade; stable Android APIs only. */
final class ReleaseNormalUpgradeProbe {
    private static final String MARKER="normal-package-upgrade-probe",DATABASE="personalized-hot-topics-v1.db";
    private final Instrumentation test;
    private final boolean seed;
    private final boolean originalVerify;
    private final boolean renewExpiredCookie;
    private final boolean beforeUpgrade;
    private int checks,failures;
    private Activity activity;
    private final StringBuilder report=new StringBuilder("NORMAL PACKAGE UPGRADE; synthetic legacy state, not a real authenticated account\n");
    ReleaseNormalUpgradeProbe(Instrumentation test,String phase){this.test=test;seed=phase.equals("normal-upgrade-seed")||phase.equals("normal-upgrade-original-verify");originalVerify=phase.equals("normal-upgrade-original-verify");renewExpiredCookie=phase.equals("normal-upgrade-renew-expired-cookie");beforeUpgrade=phase.equals("normal-upgrade-before");}
    private Context context(){return test.getTargetContext();}
    private SharedPreferences marker(){return context().getSharedPreferences(MARKER,0);}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View root,String label){if(android.text.TextUtils.equals(label,root.getContentDescription())||root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void launch(String name){activity=test.startActivitySync(new Intent().setClassName(context().getPackageName(),name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();}
    private boolean visible(String label){boolean[] result={false};ui(()->{View found=find(activity.getWindow().getDecorView(),label);result[0]=found!=null&&found.isShown();});return result[0];}
    private void waitVisible(String label)throws Exception{long until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until){if(visible(label))return;Thread.sleep(50);}throw new IllegalStateException("Missing upgrade UI: "+label);}
    private void waitLegacyImport()throws Exception{
        long until=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<until){
            if(context().getDatabasePath(DATABASE).exists())try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context().getDatabasePath(DATABASE).getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT value FROM meta WHERE key='legacy_saved_imported'",null)){
                if(c.moveToFirst()&&c.getInt(0)==1)return;
            }catch(android.database.sqlite.SQLiteException preparing){}
            Thread.sleep(50);
        }
        throw new IllegalStateException("Legacy migration did not commit");
    }
    private static JSONObject item(String source,String title,String url)throws Exception{return new JSONObject().put("source",source).put("title",title).put("url",url).put("detail","合成升级收藏").put("video",false);}
    private void seed()throws Exception{
        if(marker().contains("nonce"))throw new IllegalStateException("Existing original fixture must be verified, never reseeded");
        if(context().getDatabasePath(DATABASE).exists()||new JSONArray(context().getSharedPreferences("MainActivity",0).getString("saved","[]")).length()!=0||!context().getSharedPreferences("reading-history",0).getAll().isEmpty())throw new IllegalStateException("Refuse to overwrite existing app data");
        String nonce=UUID.randomUUID().toString(),url="https://www.v2ex.com/t/9900970101";
        JSONArray saved=new JSONArray().put(item("V2EX","旧版收藏：文字讨论保留",url)).put(item("ZHIHU","旧版收藏：问题补充保留","https://www.zhihu.com/question/9900970102"));
        long now=System.currentTimeMillis();String history=new JSONObject().put("at",now).put("position",321).put("expanded",new JSONArray().put("legacy-body")).toString();
        context().getSharedPreferences("MainActivity",0).edit().putString("saved",saved.toString()).putInt("font",23).putString("selected","ZHIHU").commit();
        context().getSharedPreferences("reading-history",0).edit().putString(url,history).commit();
        context().getSharedPreferences("appearance",0).edit().putInt("mode",2).putInt("spacing",2).commit();
        context().getSharedPreferences("source-session",0).edit().putBoolean("ZHIHU-desktop",true).putBoolean("V2EX-web",true).commit();
        writeSyntheticCookie(nonce);
        marker().edit().putString("nonce",nonce).putString("saved",saved.toString()).putString("url",url).putString("history",history).commit();
        launch("app.quietreader.MainActivity");check(!activity.isFinishing(),"retained original non-debuggable MainActivity starts with synthetic legacy preferences");
        verifyPreferences();check(!context().getDatabasePath(DATABASE).exists(),"actual original app has no new recommendation database");
    }
    private void writeSyntheticCookie(String nonce)throws Exception{
        CountDownLatch written=new CountDownLatch(1);boolean[] accepted={false};ui(()->CookieManager.getInstance().setCookie("https://normal-upgrade.quiet-reader.invalid/","qr_normal_upgrade="+nonce+"; Max-Age=86400; Secure; SameSite=Lax",ok->{accepted[0]=ok;written.countDown();}));
        if(!written.await(5,TimeUnit.SECONDS)||!accepted[0])throw new IllegalStateException("Synthetic cookie rejected");CookieManager.getInstance().flush();
    }
    private void renewExpiredCookie()throws Exception{
        String nonce=marker().getString("nonce","");if(!nonce.matches("[0-9a-f-]{36}"))throw new IllegalStateException("Existing synthetic fixture is missing");
        long at=new JSONObject(marker().getString("history","{}")).optLong("at"),age=System.currentTimeMillis()-at;
        if(at<=0||age<=86400_000L)throw new IllegalStateException("Do not renew a fixture that has not outlived its original cookie TTL");
        String[] current={null};ui(()->current[0]=CookieManager.getInstance().getCookie("https://normal-upgrade.quiet-reader.invalid/"));
        if(current[0]!=null&&!current[0].isEmpty())throw new IllegalStateException("Do not overwrite an existing cookie while preparing retention verification");
        check(true,"original synthetic cookie TTL elapsed and fixture domain is empty before renewal");
        writeSyntheticCookie(nonce);marker().edit().putLong("cookie_renewed_at",System.currentTimeMillis()).commit();
        verifyPreferences();
    }
    private void verifyPreferences()throws Exception{
        String expected=marker().getString("canonical_saved",marker().getString("saved","")),actual=context().getSharedPreferences("MainActivity",0).getString("saved","");
        if(originalVerify&&!marker().contains("canonical_saved")){
            // The retained v46 binary predates Item.snippet. Preserve the actual
            // original format as the baseline, without rewriting app preferences.
            JSONArray original=new JSONArray(expected);for(int n=0;n<original.length();n++)original.getJSONObject(n).remove("snippet");
            boolean onlyUnsupportedField=original.toString().equals(actual);
            check(onlyUnsupportedField,"original binary retains every legacy saved field while omitting unsupported snippet");
            if(!onlyUnsupportedField)throw new IllegalStateException("Unexpected original saved-data change");
            marker().edit().putString("canonical_saved",actual).commit();expected=actual;
        }
        if(!expected.equals(actual))report.append("syntheticSavedExpected=").append(expected).append("\nsyntheticSavedActual=").append(actual).append('\n');
        check(expected.equals(actual),"legacy saved JSON remains byte-for-byte unchanged");
        check(context().getSharedPreferences("MainActivity",0).getInt("font",0)==23&&context().getSharedPreferences("appearance",0).getInt("mode",0)==2&&context().getSharedPreferences("appearance",0).getInt("spacing",0)==2,"reader font and appearance preferences remain intact");
        check(context().getSharedPreferences("source-session",0).getBoolean("ZHIHU-desktop",false)&&context().getSharedPreferences("source-session",0).getBoolean("V2EX-web",false),"legacy source session-mode preferences remain intact");
        check(marker().getString("history","").equals(context().getSharedPreferences("reading-history",0).getString(marker().getString("url",""),"")),"old reading position and expanded-section metadata remain intact");
        String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie("https://normal-upgrade.quiet-reader.invalid/"));check(cookie[0]!=null&&Arrays.asList(cookie[0].split(";\\s*")).contains("qr_normal_upgrade="+marker().getString("nonce","")),"persistent synthetic WebView cookie survives without exporting its value");
    }
    private void verify()throws Exception{
        if(!marker().contains("nonce"))throw new IllegalStateException("Original v46 fixture is missing");
        launch("app.quietreader.DiscoveryActivity");waitVisible("设置");
        check(!visible("稍后读"),"current compact home keeps the removed read-later entry absent");
        waitLegacyImport();
        verifyPreferences();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context().getDatabasePath(DATABASE).getPath(),null,SQLiteDatabase.OPEN_READONLY)){
            int expectedSchema=beforeUpgrade?6:9;
            check(db.getVersion()==expectedSchema,"recommendation schema "+db.getVersion()+" matches "+expectedSchema+(beforeUpgrade?" before upgrade":" after upgrade"));
            try(Cursor c=db.rawQuery("SELECT COUNT(*),COUNT(DISTINCT id) FROM saved",null)){c.moveToFirst();check(c.getInt(0)==2&&c.getInt(1)==2,"legacy migration retains exactly two distinct saved articles without requiring a removed UI");}
            JSONArray expected=new JSONArray(marker().getString("canonical_saved",marker().getString("saved","[]")));Set<String> matched=new HashSet<>();boolean preserved=true;
            try(Cursor c=db.rawQuery("SELECT payload FROM saved",null)){while(c.moveToNext()){
                JSONObject actual=new JSONObject(c.getString(0));JSONObject original=null;
                for(int n=0;n<expected.length();n++)if(expected.getJSONObject(n).getString("url").equals(actual.getString("url")))original=expected.getJSONObject(n);
                if(original==null){preserved=false;continue;}
                preserved&=matched.add(original.getString("url"))&&original.getString("source").equals(actual.getString("source"))&&original.getString("title").equals(actual.getString("title"))&&original.getString("detail").equals(actual.getString("heat"))&&original.getBoolean("video")==actual.getBoolean("video");
            }}
            check(preserved&&matched.size()==expected.length(),"retained saved payloads preserve each original URL source title detail and video flag");
            try(Cursor c=db.rawQuery("SELECT value FROM meta WHERE key='legacy_saved_imported'",null)){check(c.moveToFirst()&&c.getInt(0)==1,"production legacy-import completion marker is committed");}
        }
    }
    void run(){
        try{
            if(!context().getPackageName().equals("app.quietreader")||!Build.HARDWARE.equals("ranchu")||!Build.PRODUCT.startsWith("sdk_gphone")||Build.VERSION.SDK_INT!=36)throw new IllegalStateException("Dedicated API36 emulator and normal package required");
            ApplicationInfo info=context().getApplicationInfo();check((info.flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual normal-package target is non-debuggable");
            PackageInfo pkg=context().getPackageManager().getPackageInfo(context().getPackageName(),0);check((seed?pkg.getLongVersionCode()==46:pkg.getLongVersionCode()==47||pkg.getLongVersionCode()==48||pkg.getLongVersionCode()==49)&&info.targetSdkVersion==(seed?35:36),"expected original v46, retained v47/v48 or current v49 and target SDK are installed");
            if(renewExpiredCookie)renewExpiredCookie();else if(originalVerify){if(!marker().contains("nonce"))throw new IllegalStateException("Original fixture missing");verifyPreferences();}else if(seed)seed();else verify();
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{if(activity!=null){ui(activity::finish);test.waitForIdleSync();}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
