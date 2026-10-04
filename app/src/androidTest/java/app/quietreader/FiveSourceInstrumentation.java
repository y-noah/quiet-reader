package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static app.quietreader.Models.*;

/** Synthetic upgrade fixtures, actual activity lifecycle/navigation. Never touches cookies.
 * Preference snapshots stay in memory and are restored; no account data is exported.
 */
public final class FiveSourceInstrumentation extends Instrumentation {
    private final Source[] allowed={Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.IFANR};
    private final Map<String,Map<String,?>> backups=new LinkedHashMap<>();
    private final StringBuilder report=new StringBuilder();private MainActivity activity;private Bundle restored;private volatile Activity rejectedLogin;
    private File output;private int checks,failures;private long fixtureTime;
    interface Work{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private boolean enabled(Source s){return Arrays.asList(allowed).contains(s);}
    private void check(boolean pass,String message){checks++;if(!pass)failures++;report.append(pass?"PASS ":"FAIL ").append(message).append('\n');}
    private void ui(Work work)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{work.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void invoke(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,0);}
    private String url(Source s,int i){String id="99000000"+(100+i);switch(s){case ZHIHU:return "https://www.zhihu.com/question/"+id;case WEIBO:return "https://m.weibo.cn/detail/"+id;case HUPU:return "https://bbs.hupu.com/"+id+".html";case CLS:return "https://www.cls.cn/detail/"+id;case IFANR:return "https://www.ifanr.com/"+id;default:return s.login;}}
    private List<Item> rows(Source s){List<Item> rows=new ArrayList<>();for(int i=0;i<30;i++)rows.add(new Item(s,"合成升级榜单 · "+s.label+" · 条目 "+(i+1),url(s,i),"合成缓存，非实网"));return rows;}
    private String videoKey(Source s){return s.name()+"|https://synthetic.invalid/video-"+s.name();}
    private void seed()throws Exception{
        fixtureTime=System.currentTimeMillis();for(String name:new String[]{"boards","video-filter","MainActivity","appearance","source-session"})backups.put(name,new HashMap<>(prefs(name).getAll()));
        SharedPreferences.Editor boards=prefs("boards").edit().clear(),videos=prefs("video-filter").edit().clear();List<Item> saved=new ArrayList<>();
        for(Source s:Source.values())if(s!=Source.AGGREGATE){boards.putString(s.name(),Models.toJson(rows(s)).toString()).putLong(s.name()+"_time",fixtureTime).putString(s.name()+"_endpoint",s.endpoint).putInt(s.name()+"_schema",2);videos.putLong(videoKey(s),fixtureTime);saved.add(rows(s).get(0));}
        boards.commit();videos.commit();prefs("MainActivity").edit().clear().putString("saved",Models.toJson(saved).toString()).putString("selected",Source.DOUBAN.name()).putInt("font",19).commit();prefs("appearance").edit().clear().putInt("mode",2).putInt("palette",0).putInt("spacing",1).putInt("synthetic-preserve",71).commit();
    }
    private void quiet(MainActivity a)throws Exception{((Repository)field(a,"repo")).cancelPending();((AggregateLoader)field(a,"aggregateLoader")).cancel();((ArticlePreloader)field(a,"preloader")).pause();((Handler)field(a,"preloadHandler")).removeCallbacksAndMessages(null);}
    @Override public void callActivityOnCreate(Activity a,Bundle state){Bundle use=a instanceof MainActivity&&restored!=null?restored:state;restored=null;super.callActivityOnCreate(a,use);if(a instanceof LoginActivity)rejectedLogin=a;if(a instanceof MainActivity){activity=(MainActivity)a;try{quiet(activity);}catch(Exception e){throw new RuntimeException(e);}}}
    private void launch()throws Exception{activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(450);}
    private void finishMain()throws Exception{if(activity!=null)ui(()->{quiet(activity);activity.finish();});Thread.sleep(200);}
    private String text(View v){StringBuilder b=new StringBuilder();if(v instanceof TextView)b.append(((TextView)v).getText()).append('\n');if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)b.append(text(((ViewGroup)v).getChildAt(i)));return b.toString();}
    private View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),label);if(f!=null)return f;}return null;}
    private void shot(String name)throws Exception{Thread.sleep(300);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Missing screenshot");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void migration()throws Exception{
        Repository[] repository={null};ui(()->repository[0]=new Repository(getTargetContext()));Repository repo=repository[0];
        try{for(Source s:Source.values())if(s!=Source.AGGREGATE){boolean keep=enabled(s);check(prefs("boards").contains(s.name())==keep,"migration board "+s.name());check(prefs("boards").contains(s.name()+"_time")==keep,"migration timestamp "+s.name());check(prefs("boards").contains(s.name()+"_endpoint")==keep,"migration endpoint "+s.name());check(prefs("boards").contains(s.name()+"_schema")==keep,"migration schema "+s.name());check(prefs("video-filter").contains(videoKey(s))==keep,"migration video marker "+s.name());if(keep){check(repo.cached(s).size()==30,"retained platform cache rows "+s.name());check(repo.cachedAt(s)==fixtureTime,"retained platform cache age "+s.name());}else check(repo.cached(s).isEmpty()&&repo.cachedAt(s)==0,"disabled repository cache is inaccessible "+s.name());}check(prefs("appearance").getInt("synthetic-preserve",0)==71,"migration preserves appearance");}finally{ui(repo::close);}
    }
    private void home(String screenshot)throws Exception{
        ui(()->{check(field(activity,"selected")==Source.AGGREGATE,"default/restored home is aggregate");String nativeText=text(activity.getWindow().getDecorView());check(nativeText.contains("Top50")&&!nativeText.contains("Top100"),"visible home Top50 label");List<Item> items=(List<Item>)field(activity,"boardItems");check(items.size()==50,"enough cached input produces exactly 50 rows");check(items.stream().allMatch(i->enabled(i.source)),"all ranked sources in enabled five");LinearLayout nav=(LinearLayout)field(activity,"platformNavigation");check(nav.getChildCount()==6,"aggregate plus five navigation items");for(int i=0;i<nav.getChildCount();i++)check(Math.abs(nav.getChildAt(i).getWidth()-nav.getWidth()/6)<=2,"equal navigation width "+i);for(Source old:Source.values())if(old!=Source.AGGREGATE&&!enabled(old))check(!nativeText.contains(old.label),"no obsolete platform text on home "+old.name());});shot(screenshot);
    }
    private void navigation()throws Exception{
        for(Source source:allowed){ui(()->{View tab=find(activity.getWindow().getDecorView(),source.label);check(tab!=null&&tab.performClick(),"actual tab click "+source.label);quiet(activity);check(field(activity,"selected")==source,"correct selected source "+source.label);});}
        ui(()->{find(activity.getWindow().getDecorView(),"总榜").performClick();quiet(activity);invoke("aggregateLogin",new Class<?>[0]);});Thread.sleep(200);
        AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();check(root!=null&&!root.findAccessibilityNodeInfosByText("选择来源 / 登录").isEmpty(),"actual source login chooser opened");for(Source s:allowed)check(root!=null&&root.findAccessibilityNodeInfosByText(s.label).size()==1,"one login chooser row "+s.label);for(Source s:Source.values())if(s!=Source.AGGREGATE&&!enabled(s))check(root!=null&&root.findAccessibilityNodeInfosByText(s.label).isEmpty(),"old source absent from login chooser "+s.name());shot("02-five-source-login-dialog");sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
    }
    private void restoredOld()throws Exception{
        List<Item> saved=Models.fromJson(new org.json.JSONArray(prefs("MainActivity").getString("saved","[]")));check(saved.size()==5&&saved.stream().allMatch(i->enabled(i.source)),"legacy bookmarks migrated to five platforms");
        finishMain();restored=new Bundle();restored.putString("source",Source.DOUBAN.name());restored.putString("current",rows(Source.DOUBAN).get(0).json().toString());restored.putString("history",Models.toJson(Arrays.asList(rows(Source.SMZDM).get(0),rows(Source.WEIBO).get(0))).toString());launch();ui(()->{check(field(activity,"current")==null,"old restored article does not reappear");Deque<Item> history=(Deque<Item>)field(activity,"history");check(history.stream().allMatch(i->enabled(i.source)),"restored history has no removed sources");});home("03-restored-old-source-home");
        rejectedLogin=null;ui(()->activity.startActivity(new Intent(activity,LoginActivity.class).putExtra("source",Source.DOUBAN.name()).putExtra("url",Source.DOUBAN.login)));long until=SystemClock.elapsedRealtime()+4000;while(rejectedLogin==null&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);ui(()->{check(rejectedLogin!=null&&(rejectedLogin.isFinishing()||rejectedLogin.isDestroyed()),"removed-source LoginActivity rejects launch");if(rejectedLogin!=null&&!rejectedLogin.isFinishing())rejectedLogin.finish();});
        finishMain();prefs("appearance").edit().putInt("mode",1).commit();launch();home("04-light-total50");
    }
    private void restore(){for(Map.Entry<String,Map<String,?>> snapshot:backups.entrySet()){SharedPreferences.Editor edit=prefs(snapshot.getKey()).edit().clear();for(Map.Entry<String,?> item:snapshot.getValue().entrySet()){Object v=item.getValue();String k=item.getKey();if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);else if(v instanceof Float)edit.putFloat(k,(Float)v);else if(v instanceof Set)edit.putStringSet(k,new HashSet<>((Set<String>)v));}edit.commit();}}
    @Override public void onStart(){Bundle result=new Bundle();try{output=new File(getTargetContext().getExternalFilesDir(null),"five-source-qa/run-"+System.currentTimeMillis());output.mkdirs();report.append("SYNTHETIC cache/upgrade fixtures, actual Android lifecycle and click handlers. No live-source success claim. Preference backups remain in memory and are restored. No cookie access.\n");seed();migration();launch();home("01-dark-total50");navigation();restoredOld();}catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}finally{try{finishMain();restore();report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');try(FileOutputStream out=new FileOutputStream(new File(output,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception e){failures++;report.append("CLEANUP ERROR ").append(e);}result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}}
}
