package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Run prepare and restore in separate instrumented processes to verify disk resume. */
public final class BrowsingExperienceInstrumentation extends Instrumentation {
    private static final String URL="https://www.zhihu.com/question/9876501030";
    private MainActivity activity;private String phase;private int checks,failures;private File output;
    private final StringBuilder report=new StringBuilder();
    interface Task{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","prepare");start();}
    private void ui(Task task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void call(String name,Class<?>[] types,Object...values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values);}
    private void quiet()throws Exception{((Repository)field(activity,"repo")).cancelPending();((ArticlePreloader)field(activity,"preloader")).pause();((Handler)field(activity,"preloadHandler")).removeCallbacksAndMessages(null);}
    private String js(String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={""};
        ui(()->{WebView web=(WebView)field(activity,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});
        if(!done.await(4,TimeUnit.SECONDS))throw new IOException("DOM timeout");return result[0];
    }
    private static View find(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),label);if(v!=null)return v;}return null;}
    private Document fixture(){
        Document d=new Document();d.url=URL;d.title="浏览体验合成测试";d.notice="仅展示本次可见内容";
        for(int i=0;i<3;i++){Section s=new Section("browse-"+i,i==0?"问题补充":"回答 "+i,i>0);for(int j=0;j<(i==0?1:15);j++)s.blocks.add(new Block("text","合成段落 "+i+" / "+j+"：阅读过程中保持位置，重复按钮只保留一个入口。"));d.sections.add(s);d.blocks.addAll(s.blocks);}
        return d;
    }
    private void click(String selector)throws Exception{check(js("(function(){var a=document.querySelector("+JSONObject.quote(selector)+");if(a)a.click();return !!a;})()").equals("true"),"Reader action "+selector);Thread.sleep(600);}
    private void prepare()throws Exception{
        getTargetContext().getSharedPreferences("reading-history",0).edit().remove(URL).commit();
        Document d=fixture();ui(()->{quiet();set("current",new Item(Source.ZHIHU,d.title,URL,""));set("primaryPending",true);call("frame",new Class[]{String.class,String.class},"知乎","news");call("scroller",new Class[0]);call("render",new Class[]{Document.class},d);set("suppressAutoUntil",System.currentTimeMillis()+60000);});
        Thread.sleep(850);
        check(js("document.querySelectorAll('#part-1 p:not(.preview)').length").equals("15"),"First answer opens in full");
        check(js("document.querySelectorAll('#part-2 p.preview').length").equals("1"),"Later answers keep compact previews");
        check(js("document.querySelectorAll('#part-1 a[href*=toggle]').length").equals("1"),"Only one collapse control per answer");
        check(js("document.querySelectorAll('#part-1 a[href*=jump]').length").equals("1"),"Only one next-answer control per answer");
        click("#part-1 a[href*=toggle]");
        check(js("document.querySelectorAll('#part-1 p.preview').length").equals("1"),"Collapse shows preview");
        click("#part-1 a[href*=toggle]");
        check(js("document.querySelectorAll('#part-1 p:not(.preview)').length").equals("15"),"Expand restores complete answer");
        click("#part-1 a[href*=jump]");
        check(js("document.querySelectorAll('#part-2 p:not(.preview)').length").equals("15"),"Next-answer action opens its destination");
        ui(()->{WebView web=(WebView)field(activity,"readerWeb");web.scrollTo(0,600);call("rememberReading",new Class[0]);getTargetContext().getSharedPreferences("browse-test",0).edit().putInt("position",web.getScrollY()).commit();check(web.getScrollY()>100,"Nonzero position saved");});
        check(new ReadingHistory(getTargetContext()).get(URL).has("document"),"Loaded answer snapshot stored on disk");
    }
    private void restore()throws Exception{
        ui(()->{quiet();((android.util.LruCache<?,?>)field(activity,"marks")).evictAll();((Repository)field(activity,"repo")).invalidateArticles();call("open",new Class[]{Item.class,boolean.class},new Item(Source.ZHIHU,"浏览体验合成测试",URL,""),false);});
        Thread.sleep(1100);
        check(js("document.querySelectorAll('section.part').length").equals("3"),"New process restores all loaded sections from disk");
        check(js("document.querySelectorAll('#part-2 p:not(.preview)').length").equals("15"),"Expanded destination survives process restart");
        ui(()->{WebView web=(WebView)field(activity,"readerWeb");int expected=getTargetContext().getSharedPreferences("browse-test",0).getInt("position",-1);check(expected>100&&Math.abs(web.getScrollY()-expected)<5,"Reading position restored within 5 pixels");check(field(activity,"dynamic")==null,"Resume uses saved content without a source request");});
        check(new ReadingHistory(getTargetContext()).seen(URL),"Read marker survives process restart");
        gallery();
    }
    private void gallery()throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();Bitmap bitmap=Bitmap.createBitmap(500,1400,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff82aacc);bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        int[] loads={0};ImagePreview[] preview={null};
        ui(()->{preview[0]=new ImagePreview(activity,Arrays.asList("https://example.com/1.png","https://example.com/2.png"),0,false,u->{loads[0]++;return new WebResourceResponse("image/png",null,new ByteArrayInputStream(bytes.toByteArray()));},u->{});preview[0].show();});Thread.sleep(900);
        ui(()->{LinearLayout layout=(LinearLayout)field(preview[0],"layout");check(!find(layout,"上一张").isEnabled()&&find(layout,"下一张").isEnabled(),"Gallery bounds correctly enabled");find(layout,"下一张").performClick();});Thread.sleep(800);
        ui(()->{check((int)field(preview[0],"index")==1,"Next image changes active image");LinearLayout layout=(LinearLayout)field(preview[0],"layout");check(!find(layout,"下一张").isEnabled(),"Last image has no false next action");AlertDialog dialog=(AlertDialog)field(preview[0],"dialog");dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});Thread.sleep(800);
        check(loads[0]>=3,"Retry reloads current image in place");
        int[] location=new int[2],size=new int[2];
        ui(()->{WebView web=(WebView)field(preview[0],"current");web.getLocationOnScreen(location);size[0]=web.getWidth();size[1]=web.getHeight();});
        long down=SystemClock.uptimeMillis();float y=location[1]+size[1]*.4f;
        for(int n=0;n<=12;n++){
            int action=n==0?MotionEvent.ACTION_DOWN:n==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,location[0]+size[0]*(.2f+.6f*n/12),y,0);sendPointerSync(event);event.recycle();Thread.sleep(25);
        }
        Thread.sleep(800);
        ui(()->check((int)field(preview[0],"index")==0,"Real horizontal swipe switches to previous image"));
        ui(()->{WebView web=(WebView)field(preview[0],"current");check(web.getSettings().getBuiltInZoomControls(),"Gallery supports pinch zoom");check(web.getContentHeight()>web.getHeight()/web.getScale(),"Long image can scroll vertically");preview[0].dismiss();});
    }
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            output=new File(getTargetContext().getExternalFilesDir(null),"browsing-qa/"+phase+"-"+System.currentTimeMillis());output.mkdirs();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(600);
            if(phase.equals("restore"))restore();else prepare();
            Bitmap shot=getUiAutomation().takeScreenshot();try(FileOutputStream file=new FileOutputStream(new File(output,"reader.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,file);}shot.recycle();
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{try{if(activity!=null)ui(()->{quiet();activity.finish();});if(phase.equals("restore"))getTargetContext().getSharedPreferences("reading-history",0).edit().remove(URL).commit();report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');try(FileOutputStream file=new FileOutputStream(new File(output,"report.txt"))){file.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception e){failures++;}result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
