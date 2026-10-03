package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Bounded regression journey: actual scroll gestures and the user's public IT之家 article. */
public final class NavigationImageInstrumentation extends Instrumentation {
    private Activity activity; private File output; private int checks;
    private final StringBuilder report=new StringBuilder();
    private static final String ARTICLE="https://www.ithome.com/1/009/284.htm";
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            output=new File(getTargetContext().getExternalFilesDir(null),"navigation-image/run-"+System.currentTimeMillis());
            if(!output.mkdirs())throw new IOException("Evidence directory failed");
            activity=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"app.quietreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            SystemClock.sleep(1800);
            runOnMainSync(()->{try{Field f=MainActivity.class.getDeclaredField("selected");f.setAccessible(true);f.set(activity,Source.AGGREGATE);call("home",new Class[]{boolean.class},false);}catch(Exception e){throw new RuntimeException(e);}});
            long until=SystemClock.elapsedRealtime()+25000;
            while(SystemClock.elapsedRealtime()<until){if(value("content",LinearLayout.class).getChildCount()>30&&value("platformNavigation",LinearLayout.class).getHeight()>0)break;SystemClock.sleep(300);}
            ScrollView scroll=value("scroll",ScrollView.class);LinearLayout nav=value("platformNavigation",LinearLayout.class);
            float density=activity.getResources().getDisplayMetrics().density;
            check(Math.abs(nav.getHeight()/density-48)<1,"Compact bar is 48 dp, without old 8 dp padding");
            check(nav.getChildCount()==7,"All seven platform targets remain");
            for(int i=0;i<7;i++)check(nav.getChildAt(i).getHeight()/density>=47.5,"Touch target remains 48 dp");
            int viewport=scroll.getHeight();shot("01-bar-visible");
            swipe(scroll,.78f,.30f);SystemClock.sleep(650);
            check(scroll.getScrollY()>0,"Real upward finger swipe advances content");
            check(nav.getTranslationY()>=nav.getHeight()-1,"Bar hides while reading down");
            check(scroll.getHeight()==viewport,"Hiding bar does not resize content viewport");shot("02-bar-hidden");
            swipe(scroll,.35f,.56f);SystemClock.sleep(600);
            check(nav.getTranslationY()==0,"Reverse scroll brings platform bar back");shot("03-bar-returned");
            runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));SystemClock.sleep(600);shot("04-list-end");
            check(scroll.getChildAt(0).getBottom()<=scroll.getScrollY()+scroll.getHeight()+1,"Last row remains reachable");
            runOnMainSync(()->scroll.scrollTo(0,0));SystemClock.sleep(350);
            check(nav.getTranslationY()==0,"At top navigation is visible");
            runOnMainSync(()->nav.getChildAt(1).performClick());SystemClock.sleep(400);
            check(value("selected",Source.class)==Source.ZHIHU,"Revealed navigation still switches source");
            runOnMainSync(()->call("open",new Class[]{Item.class,boolean.class},new Item(Source.ITHOME,"IT之家图片回归",ARTICLE,""),true));
            until=SystemClock.elapsedRealtime()+35000;
            while(value("readerWeb",WebView.class)==null&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(300);
            check(value("readerWeb",WebView.class)!=null,"Actual article opens in reader");SystemClock.sleep(800);
            dom("(function(){var a=Array.from(document.querySelectorAll('a.action')).find(e=>e.textContent.includes('展开阅读'));if(a)a.click();return {};})()");SystemClock.sleep(500);
            JSONObject images=dom("({count:document.images.length,urls:Array.from(document.images).map(i=>i.src)})");
            report.append("Actual article images: ").append(images).append('\n');
            check(images.getInt("count")==5,"User article extracts all five original images");
            for(int i=0;i<images.getInt("count");i++){
                final int index=i;
                dom("(function(){document.images["+i+"].scrollIntoView({block:'center'});return {};})()");
                JSONObject size=null;until=SystemClock.elapsedRealtime()+15000;
                while(SystemClock.elapsedRealtime()<until){size=dom("({w:document.images["+i+"].naturalWidth,h:document.images["+i+"].naturalHeight})");if(size.optInt("w")>10&&size.optInt("h")>10)break;SystemClock.sleep(250);}
                check(size!=null&&size.optInt("w")>10&&size.optInt("h")>10,"Real article image "+index+" decodes: "+size);
                shot("05-article-image-"+i);
            }
            check(!images.toString().contains("/images/v2/t.png"),"No transparent placeholder remains");
            dom("(function(){document.images[0].scrollIntoView({block:'center'});document.images[0].closest('a').click();return {};})()");
            until=SystemClock.elapsedRealtime()+15000;ImagePreview preview=null;TextView status=null;
            while(SystemClock.elapsedRealtime()<until){preview=value("imagePreview",ImagePreview.class);if(preview!=null){status=member(preview,"status",TextView.class);if(status.getText().toString().contains("图片已显示"))break;}SystemClock.sleep(250);}
            check(status!=null&&status.getText().toString().contains("图片已显示"),"Original image preview succeeds");shot("06-original-preview");
            ImagePreview previous=preview;runOnMainSync(previous::dismiss);
            // Exercise stale placeholder protection through the real image loader (no request needed).
            runOnMainSync(()->call("zoom",new Class[]{String.class,String.class},"https://img.ithome.com/images/v2/t.png",ARTICLE));SystemClock.sleep(1200);
            ImagePreview rejected=value("imagePreview",ImagePreview.class);String failed=member(rejected,"status",TextView.class).getText().toString();
            check(failed.contains("失败")&&!failed.contains("已显示"),"Known placeholder cannot report image success");shot("07-placeholder-rejected");
            runOnMainSync(()->member(rejected,"dialog",AlertDialog.class).getButton(AlertDialog.BUTTON_NEGATIVE).performClick());SystemClock.sleep(800);
            check(!member(rejected,"status",TextView.class).getText().toString().contains("已显示"),"Retry does not restore false success");runOnMainSync(rejected::dismiss);
            runOnMainSync(()->activity.onBackPressed());SystemClock.sleep(400);
            check(value("current",Item.class)==null,"Reader back returns to platform list");
            check(value("platformNavigation",LinearLayout.class).getTranslationY()==0,"Returning from reader shows navigation");
            report.append("PASS ").append(checks).append(" assertions\n");result.putString("stream",report+"Output: "+output+"\n");write();finish(Activity.RESULT_OK,result);
        }catch(Throwable error){report.append("FAIL ").append(error).append('\n');try{shot("failure");write();}catch(Exception ignored){}result.putString("stream",report+"Output: "+output+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
    private void check(boolean condition,String text){checks++;report.append(condition?"PASS ":"FAIL ").append(text).append('\n');if(!condition)throw new AssertionError(text);}
    private <T>T value(String name,Class<T> type){return member(activity,name,type);}
    private <T>T member(Object object,String name,Class<T> type){try{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return type.cast(field.get(object));}catch(Exception e){throw new RuntimeException(e);}}
    private void call(String name,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new RuntimeException(e);}}
    private void swipe(View view,float from,float to){int[] location=new int[2];runOnMainSync(()->view.getLocationOnScreen(location));float x=location[0]+view.getWidth()/2f;long start=SystemClock.uptimeMillis();for(int i=0;i<=16;i++){long time=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(start,time,i==0?MotionEvent.ACTION_DOWN:i==16?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,location[1]+view.getHeight()*(from+(to-from)*i/16),0);sendPointerSync(event);event.recycle();SystemClock.sleep(22);}}
    private JSONObject dom(String expression)throws Exception{String[] raw={null};CountDownLatch latch=new CountDownLatch(1);runOnMainSync(()->{WebView web=value("readerWeb",WebView.class);web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify("+expression+")",r->{web.getSettings().setJavaScriptEnabled(false);raw[0]=r;latch.countDown();});});if(!latch.await(5,TimeUnit.SECONDS))throw new IOException("DOM timeout");return new JSONObject((String)new JSONTokener(raw[0]).nextValue());}
    private void shot(String name)throws Exception{waitForIdleSync();SystemClock.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();try(FileOutputStream file=new FileOutputStream(new File(output,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,file);}bitmap.recycle();}
    private void write()throws Exception{try(FileOutputStream file=new FileOutputStream(new File(output,"report.txt"))){file.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
}
