package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.TextView;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Isolated, prepared reader. Intercepts the share chooser; never sends a message. */
final class ReaderInteractionProbe {
    private final Instrumentation test;private final boolean dark;private MainActivity reader;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReaderInteractionProbe(Instrumentation test,boolean dark){this.test=test;this.dark=dark;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}
    private void call(String name)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(reader);}
    private View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription())||v instanceof TextView&&label.contentEquals(((TextView)v).getText()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View r=find(((ViewGroup)v).getChildAt(n),label);if(r!=null)return r;}return null;}
    private String js(WebView web,String expression)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(expression,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("DOM timeout");return value[0];}
    private void await(WebView web,String expression)throws Exception{long until=SystemClock.elapsedRealtime()+7000;do{if("true".equals(js(web,expression)))return;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<until);throw new IllegalStateException("DOM condition timeout: "+expression);}
    void run(){Context context=test.getTargetContext();String url="https://www.zhihu.com/question/"+(System.nanoTime()%1000000000+1000000000),database="test-reader-actions-"+System.nanoTime()+".db";Instrumentation.ActivityMonitor monitor=null;
        android.content.SharedPreferences appearance=context.getSharedPreferences("appearance",0);boolean hadMode=appearance.contains("mode");int oldMode=appearance.getInt("mode",0);
        try{
            if(!Build.HARDWARE.equals("ranchu")||!context.getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Isolated emulator only");
            test.getUiAutomation();
            appearance.edit().putInt("mode",dark?2:1).commit();
            Item item=new Item(Source.ZHIHU,"回答交互验证",url,"");Document body=new Document();body.url=url;body.title=item.title;
            for(int n=0;n<3;n++){Section answer=new Section("answer-"+n,"回答 · 作者"+n,true);for(int p=0;p<15;p++)answer.blocks.add(new Block("text","第"+n+"条回答段落"+p+"，用于验证长回答滚动及原位收起。".repeat(6)));body.sections.add(answer);body.blocks.addAll(answer.blocks);}
            String image="https://picx.zhimg.com/reader-patch-test.png";Block photo=new Block("image",image);body.sections.get(0).blocks.add(0,photo);body.blocks.add(0,photo);body.sections.get(0).blocks.add(1,new Block("image",image));body.blocks.add(1,new Block("image",image));ReaderImages.shared.attach(context);android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff526a7b);java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,encoded);bitmap.recycle();ReaderImages.shared.put(image,url,encoded.toByteArray());
            ArticleVersion.capture(item,body);test.runOnMainSync(()->{Repository repo=new Repository(context);repo.cacheArticle(body);repo.close();});
            reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long until=SystemClock.elapsedRealtime()+7000;WebView target=null;while(SystemClock.elapsedRealtime()<until){target=(WebView)field("readerWeb");if(target!=null)break;Thread.sleep(40);}if(target==null)throw new IllegalStateException("reader absent");WebView web=target;
            await(web,"document.readyState==='complete'&&document.querySelectorAll('#part-0 .section-collapse').length===1");
            check("true".equals(js(web,"document.querySelectorAll('a[href*=\"jump?\"]').length===0")),"reader has no duplicate next-answer control");
            String page=(String)field("readerPageUrl");
            for(int round=0;round<6;round++){
                js(web,round%2==0?"scrollTo(0,document.getElementById('part-0').offsetTop+700)":"(()=>{let p=document.getElementById('part-0');scrollTo(0,p.offsetTop+p.offsetHeight-innerHeight+24)})()");
                check("true".equals(js(web,"(()=>{let r=document.querySelector('#part-0 .section-collapse').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight&&r.height>=48})()")),"collapse reachable mid-answer: round="+round);
                if(round==0)screenshot(context,"expanded");
                js(web,"document.querySelector('#part-0 .section-collapse').click()");await(web,"!!document.querySelector('#part-0 .preview')");
                check(page.equals(field("readerPageUrl")),"fold replaces section without page reload: round="+round);
                check("true".equals(js(web,"(()=>{let r=document.getElementById('part-1').getBoundingClientRect();return r.top>=0&&r.top<innerHeight})()")),"fold reveals next answer preview: round="+round);
                if(round==0)screenshot(context,"folded");
                js(web,"document.querySelector('#part-0 a[href*=\"toggle?\"]').click()");await(web,"!!document.querySelector('#part-0 .section-collapse')");
                check("true".equals(js(web,"document.querySelectorAll('#part-0 a[href*=\"toggle?\"]').length===1&&document.querySelectorAll('#part-1 .preview').length===1")),"one control and next answer remains folded: round="+round);
            }
            js(web,"scrollTo(0,0)");await(web,"document.querySelectorAll('#part-0 img').length===2&&Array.from(document.querySelectorAll('#part-0 img')).every(i=>i.complete&&i.naturalWidth>0)");
            js(web,"window.retainedPhotos=Array.from(document.querySelectorAll('#part-0 img'));window.initialImageResources=performance.getEntriesByType('resource').filter(r=>r.initiatorType==='img').length");
            for(int update=0;update<24;update++){
                Document changed=AnswerStream.copy((Document)field("reading"));String marker="续载验证正文第"+update+"次";
                changed.sections.get(0).blocks.add(new Block("text",marker));changed.blocks.add(new Block("text",marker));
                test.runOnMainSync(()->{try{Method append=MainActivity.class.getDeclaredMethod("appendContentAtReadingPosition",Document.class,int.class,boolean.class);append.setAccessible(true);append.invoke(reader,changed,(int)field("generation"),true);}catch(Exception e){throw new IllegalStateException(e);}});
                await(web,"document.body.innerText.includes("+org.json.JSONObject.quote(marker)+")");
                check(page.equals(field("readerPageUrl"))&&"true".equals(js(web,"document.querySelectorAll('#part-0 img').length===2&&window.retainedPhotos.every((i,n)=>i===document.querySelectorAll('#part-0 img')[n]&&i.naturalWidth>0)&&performance.getEntriesByType('resource').filter(r=>r.initiatorType==='img').length===window.initialImageResources")),"continuation preserves decoded image and request count: update="+update);
            }
            final Intent[] chooser={null};monitor=new Instrumentation.ActivityMonitor(){@Override public Instrumentation.ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CHOOSER.equals(intent.getAction())){chooser[0]=intent;return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};test.addMonitor(monitor);
            test.runOnMainSync(()->{View share=find(reader.getWindow().getDecorView(),"分享原文链接");check(share!=null&&share.getWidth()>=48*context.getResources().getDisplayMetrics().density,"dedicated share target at least 48dp");if(share!=null)share.performClick();});
            Intent send=chooser[0]==null?null:chooser[0].getParcelableExtra(Intent.EXTRA_INTENT);
            check(send!=null&&Intent.ACTION_SEND.equals(send.getAction())&&"text/plain".equals(send.getType())&&url.equals(send.getStringExtra(Intent.EXTRA_TEXT)),"share uses the original source URL via system chooser");
            test.runOnMainSync(()->{try{call("options");}catch(Exception e){throw new IllegalStateException(e);}});Dialog dialog=(Dialog)field("readerActions");
            test.runOnMainSync(()->{View root=dialog.getWindow().getDecorView();check(find(root,"阅读字号")==null&&find(root,"外观")==null&&find(root,"阅读设置")==null,"reader menu excludes global settings");check(find(root,"重新读取")!=null&&find(root,"来源 / 登录")!=null,"reader-specific tools retained");dialog.dismiss();});
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{if(monitor!=null)test.removeMonitor(monitor);test.runOnMainSync(()->{if(reader!=null)reader.finish();Repository repo=new Repository(context);repo.releaseArticleMemory();repo.close();});context.getSharedPreferences("reading-history",0).edit().remove(url).commit();context.deleteDatabase(database);android.content.SharedPreferences.Editor restore=appearance.edit();if(hadMode)restore.putInt("mode",oldMode);else restore.remove("mode");restore.commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; synthetic article, real reader/WebView and intercepted chooser\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void screenshot(Context context,String stage)throws Exception{WebView web=(WebView)field("readerWeb");CountDownLatch frame=new CountDownLatch(1);test.runOnMainSync(()->web.postVisualStateCallback(SystemClock.uptimeMillis(),new WebView.VisualStateCallback(){@Override public void onComplete(long id){web.invalidate();web.postOnAnimation(()->web.postOnAnimation(frame::countDown));}}));if(!frame.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Reader frame not committed");test.waitForIdleSync();Thread.sleep(150);android.graphics.Bitmap screen=test.getUiAutomation().takeScreenshot();if(screen==null)throw new IllegalStateException("Screenshot absent");String name="reader-actions-"+(dark?"dark":"light")+"-"+stage+".png";try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screen.recycle();}report.append("SCREENSHOT ").append(name).append('\n');}
}
