package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real font menu, decoded inline image, native touch and ordinary reader rerender. */
final class ReaderImageFontContinuationProbe {
    private final Instrumentation test;private final boolean background;private MainActivity reader;private Repository cache;
    private int calls,checks,failures;private final StringBuilder report=new StringBuilder();
    ReaderImageFontContinuationProbe(Instrumentation test,boolean background){this.test=test;this.background=background;}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private View more(View root){if("更多选项".contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=more(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
    private void menu(String text)throws Exception{long until=SystemClock.elapsedRealtime()+5000;do{android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(!text.contentEquals(node.getText()==null?"":node.getText())||!node.isVisibleToUser())continue;while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}}Thread.sleep(20);}while(SystemClock.elapsedRealtime()<until);throw new IllegalStateException("Missing menu "+text);}
    private String js(String expression)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};ui(()->{WebView web=(WebView)field("readerWeb");boolean enabled=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(expression,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("DOM inspection timeout");return result[0];}
    void run(){Context context=test.getTargetContext();String database="test-image-font-"+UUID.randomUUID()+".db",url="https://www.v2ex.com/t/"+System.currentTimeMillis(),image="https://images.example.org/"+UUID.randomUUID()+".png";
        android.content.SharedPreferences prefs=context.getSharedPreferences("MainActivity",0);boolean hadFont=prefs.contains("font");int oldFont=prefs.getInt("font",19);
        try{
            prefs.edit().putInt("font",19).commit();Item item=new Item(Source.V2EX,"图片排版后的早期滑动",url,"",false);
            Bitmap bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff307050);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
            ui(()->{ReaderImages.shared.attach(context);ReaderImages.shared.put(image,url,bytes.toByteArray());cache=new Repository(context);Document doc=new Document();doc.url=url;doc.title=item.title;doc.discussionPage=1;doc.nextUrl=url+"?p=2";Section main=new Section("image-font-body","正文",false);
                main.blocks.add(new Block("text","[图片]图片参与字号重排",List.of(new InlineImage(0,4,image,"图片"))));for(int n=0;n<10;n++)main.blocks.add(new Block("text","第"+n+"段，快速阅读缓存正文，滑到后面应当继续取得回复。这里使用受控正文验证真实滑动与图片页面恢复之间的时序。"));doc.sections.add(main);doc.blocks.addAll(main.blocks);ArticleVersion.capture(item,doc);new ArticleSession(context,item).stamp(doc);cache.cacheArticle(doc);});
            reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long until=SystemClock.elapsedRealtime()+10000;boolean ready=false;
            do{boolean[] attached={false};ui(()->attached[0]=field("readerWeb")!=null&&reader.hasWindowFocus());if(attached[0])try{ready="true".equals(js("Array.from(document.images).some(i=>i.complete&&i.naturalWidth===24)"));}catch(Exception notReady){}if(!ready)Thread.sleep(30);}while(!ready&&SystemClock.elapsedRealtime()<until);
            check(ready,"ordinary reader decodes the actual scoped24px inline PNG before changing font");Thread.sleep(1800);
            ui(()->{try{Field f=MainActivity.class.getDeclaredField("discussions");f.setAccessible(true);f.set(reader,new DiscussionStream(reader,item,new DiscussionStream.Loader(){public void load(Item page,Repository.Result<Document> callback){calls++;report.append("LOADER at=").append(SystemClock.elapsedRealtime()).append(" resumed=").append(field("readingResumed")).append(" focused=").append(reader.hasWindowFocus()).append('\n');}public void cancel(){}}));}catch(Exception e){throw new IllegalStateException(e);}});
            int before=(int)field("readerRevision");ui(()->{View button=more(reader.getWindow().getDecorView());if(button==null)throw new IllegalStateException("Missing reader menu");button.performClick();});menu("阅读字号");long changed=SystemClock.elapsedRealtime();menu("舒适 · 17");
            int[] bounds=new int[4];ui(()->{WebView web=(WebView)field("readerWeb");int[] point=new int[2];web.getLocationOnScreen(point);bounds[0]=point[0];bounds[1]=point[1];bounds[2]=web.getWidth();bounds[3]=web.getHeight();});
            long down=SystemClock.uptimeMillis();float x=bounds[0]+bounds[2]*.5f,from=bounds[1]+bounds[3]*.9f,to=bounds[1]+bounds[3]*.12f;
            for(int n=0;n<=12;n++){MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,from+(to-from)*n/12f,0);try{test.sendPointerSync(event);}finally{event.recycle();}if(n<12)Thread.sleep(15);}
            ui(()->{WebView web=(WebView)field("readerWeb");float distance=web.getContentHeight()*web.getScale()-web.getScrollY()-web.getHeight();long guard=(long)field("suppressAutoUntil")-System.currentTimeMillis();report.append("IMAGE_FONT elapsedMs=").append(SystemClock.elapsedRealtime()-changed).append(" revisionBefore=").append(before).append(" revisionAfter=").append(field("readerRevision")).append(" y=").append(web.getScrollY()).append(" distance=").append(distance).append(" height=").append(web.getHeight()).append(" guardRemainingMs=").append(guard).append(" calls=").append(calls).append('\n');check((int)field("readerRevision")>before,"actual font menu takes the image-dependent document rerender branch");check(guard>0&&web.getScrollY()>0&&distance<web.getHeight()*.75f&&calls==0,"real early forward touch reaches continuation range during image/font restoration guard");});
            if(background){ui(()->{report.append("LEAVE at=").append(SystemClock.elapsedRealtime()).append(" callsBefore=").append(calls).append(" guardRemainingMs=").append((long)field("suppressAutoUntil")-System.currentTimeMillis()).append('\n');reader.startActivity(new Intent(reader,SettingsActivity.class));});test.waitForIdleSync();ui(()->{report.append("PAUSED at=").append(SystemClock.elapsedRealtime()).append(" calls=").append(calls).append(" resumed=").append(field("readingResumed")).append(" pending=").append(field("deferredContinuation")!=null).append('\n');check(!(boolean)field("readingResumed")&&field("deferredContinuation")==null,"actual settings navigation pauses reader and removes the finite check");});}
            Thread.sleep(2600);ui(()->check(calls==(background?0:1),background?"leaving image reader cancels deferred continuation":"image/font rerender retains one real gesture and continues exactly once"));
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(cache!=null){cache.releaseArticleMemory();cache.close();}ReaderImages.shared.remove(image);});test.waitForIdleSync();android.content.SharedPreferences.Editor edit=prefs.edit();if(hadFont)edit.putInt("font",oldFont);else edit.remove("font");edit.commit();context.getSharedPreferences("reading-history",0).edit().remove(url).commit();context.deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
