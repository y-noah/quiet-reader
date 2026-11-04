package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.widget.TextView;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** One actual Zhihu pool topic; no fabricated phone content or outgoing share. */
final class ReleasePhoneReaderActionsProbe {
    private final Instrumentation test;private Activity reader;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReleasePhoneReaderActionsProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View hit=find(((ViewGroup)root).getChildAt(n),label);if(hit!=null)return hit;}return null;}
    private WebView reading(View root){if(root instanceof WebView){String url=((WebView)root).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/?render="))return (WebView)root;}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){WebView hit=reading(((ViewGroup)root).getChildAt(n));if(hit!=null)return hit;}return null;}
    private String js(WebView web,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,result->{web.getSettings().setJavaScriptEnabled(false);value[0]=result;done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("DOM callback timeout");return value[0];}
    private boolean await(WebView web,String code)throws Exception{long until=SystemClock.elapsedRealtime()+5000;do{if("true".equals(js(web,code)))return true;Thread.sleep(60);}while(SystemClock.elapsedRealtime()<until);return false;}
    private void labels(AccessibilityNodeInfo node,List<String> out){if(node==null)return;if(node.getText()!=null)out.add(node.getText().toString());for(int n=0;n<node.getChildCount();n++)labels(node.getChild(n),out);}
    void run(){Instrumentation.ActivityMonitor monitor=null;
        try{
            Context context=test.getTargetContext();if(!context.getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("Authorized phone only");
            test.getUiAutomation();JSONObject item;
            try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor row=db.rawQuery("SELECT payload FROM candidates WHERE source='ZHIHU' AND active=1 ORDER BY rank LIMIT 1",null)){if(!row.moveToFirst())throw new IllegalStateException("No actual Zhihu pool topic");item=new JSONObject(row.getString(0));}
            String url=item.getString("url");report.append("ACTUAL topic=").append(item.optString("title")).append('\n');long started=SystemClock.elapsedRealtime();
            reader=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            WebView[] own={null};long until=SystemClock.elapsedRealtime()+45000;while(SystemClock.elapsedRealtime()<until){test.runOnMainSync(()->own[0]=reading(reader.getWindow().getDecorView()));if(own[0]!=null&&"true".equals(js(own[0],"document.querySelectorAll('main .part').length>0")))break;Thread.sleep(120);}WebView web=own[0];check(web!=null,"real Zhihu topic opens own reader");if(web==null)throw new IllegalStateException("Own reader unavailable");report.append("READ elapsedMs=").append(SystemClock.elapsedRealtime()-started).append('\n');
            String page=js(web,"location.href");check("true".equals(js(web,"!document.querySelector('a[href*=\"jump?\"]')")),"no next-answer duplicate control");
            String raw=js(web,"(()=>{let a=document.querySelector('section.discussion .section-collapse');return a?a.closest('section').id:''})()");String id=(String)new JSONTokener(raw).nextValue();if(!id.matches("part-[0-9]+"))throw new IllegalStateException("No expanded actual answer");String quoted=JSONObject.quote(id);
            js(web,"(()=>{let p=document.getElementById("+quoted+");scrollTo(0,p.offsetTop+Math.min(400,Math.max(0,p.offsetHeight-innerHeight/2)))})()");
            check("true".equals(js(web,"(()=>{let r=document.getElementById("+quoted+").querySelector('.section-collapse').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})()")),"collapse reachable at actual answer reading position");
            js(web,"document.getElementById("+quoted+").querySelector('.section-collapse').click()");check(await(web,"!!document.getElementById("+quoted+").querySelector('.preview')"),"actual answer folds without returning to source");check(page.equals(js(web,"location.href")),"fold does not reload the page or add navigation history");
            check("true".equals(js(web,"(()=>{let n=document.getElementById("+quoted+").nextElementSibling;if(!n||!n.matches('section'))return false;let r=n.getBoundingClientRect();return r.top>=0&&r.top<innerHeight})()")),"next actual answer preview visible after folding");
            js(web,"document.getElementById("+quoted+").querySelector('a[href*=\"toggle?\"]').click()");check(await(web,"!!document.getElementById("+quoted+").querySelector('.section-collapse')"),"actual answer expands again");
            js(web,"scrollTo(0,0)");final Intent[] chooser={null};monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CHOOSER.equals(intent.getAction())){chooser[0]=intent;return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};test.addMonitor(monitor);
            test.runOnMainSync(()->{View button=find(reader.getWindow().getDecorView(),"分享原文链接");if(button!=null)button.performClick();});Intent send=chooser[0]==null?null:chooser[0].getParcelableExtra(Intent.EXTRA_INTENT);check(send!=null&&url.equals(send.getStringExtra(Intent.EXTRA_TEXT)),"phone share chooser contains original source URL; no recipient selected");
            test.runOnMainSync(()->find(reader.getWindow().getDecorView(),"更多选项").performClick());test.waitForIdleSync();List<String> menu=new ArrayList<>();labels(test.getUiAutomation().getRootInActiveWindow(),menu);check(!menu.contains("阅读字号")&&!menu.contains("外观")&&!menu.contains("阅读设置")&&menu.contains("重新读取")&&menu.contains("来源 / 登录"),"phone reader menu has source actions without global settings");test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{if(monitor!=null)test.removeMonitor(monitor);test.runOnMainSync(()->{if(reader!=null)reader.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; one real phone topic, intercepted share chooser\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
