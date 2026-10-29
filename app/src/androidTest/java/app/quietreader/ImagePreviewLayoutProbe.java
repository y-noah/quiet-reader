package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.View;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Controlled failed image and retry in the real preview; no claim about source authentication. */
final class ImagePreviewLayoutProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC image failure/retry, actual preview window\n");
    ImagePreviewLayoutProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String key)throws Exception{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private String js(WebView web,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Reader DOM timeout");return value[0];}
    private boolean within(View view,Rect bounds){int[] xy=new int[2];view.getLocationOnScreen(xy);report.append("BOUNDS ").append(view.getClass().getSimpleName()).append(' ').append(xy[0]).append(',').append(xy[1]).append(' ').append(view.getWidth()).append('x').append(view.getHeight()).append('\n');return view.getWidth()>0&&view.getHeight()>0&&xy[0]>=bounds.left&&xy[1]>=bounds.top&&xy[0]+view.getWidth()<=bounds.right&&xy[1]+view.getHeight()<=bounds.bottom;}
    void run(){
        MainActivity owner=null;ImagePreview[] preview={null};
        try{
            String url="https://www.cls.cn/detail/"+System.currentTimeMillis();Item item=new Item(Source.CLS,"图片关闭位置合成检查",url,"");Document body=new Document();body.url=url;body.title=item.title;
            for(int n=0;n<35;n++)body.blocks.add(new Block("text","图片预览关闭后应停留在原文的相同位置。这是用于验证阅读位置的合成段落。"));ArticleVersion.capture(item,body);
            test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(body);repo.close();});
            owner=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            WebView reader=null;long readyUntil=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<readyUntil){reader=(WebView)field(owner,"readerWeb");if(reader!=null&&"true".equals(js(reader,"document.readyState==='complete'&&!!document.querySelector('.reader-content')")))break;Thread.sleep(50);}
            if(reader==null)throw new IOException("Reader missing");js(reader,"scrollTo(0,700)");test.waitForIdleSync();Thread.sleep(200);check("true".equals(js(reader,"scrollY>0")),"underlying reader has a nonzero position to preserve");String position=js(reader,"JSON.stringify({y:scrollY,url:location.href})");
            MainActivity activity=owner;AtomicInteger loads=new AtomicInteger(),invalidations=new AtomicInteger();
            Bitmap image=Bitmap.createBitmap(500,1800,Bitmap.Config.ARGB_8888);image.eraseColor(0xff82aacc);ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(Bitmap.CompressFormat.PNG,100,bytes);image.recycle();
            test.runOnMainSync(()->{preview[0]=new ImagePreview(activity,Arrays.asList("https://example.com/preview-1.png","https://example.com/preview-2.png"),0,false,u->{if(loads.incrementAndGet()==1)throw new IOException("Controlled first failure");return new WebResourceResponse("image/png",null,new ByteArrayInputStream(bytes.toByteArray()));},u->invalidations.incrementAndGet());preview[0].show();});
            AlertDialog dialog=(AlertDialog)field(preview[0],"dialog");
            long deadline=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<deadline&&!((Boolean)field(preview[0],"settled")))Thread.sleep(50);
            test.runOnMainSync(()->{try{
                check(((android.widget.TextView)field(preview[0],"status")).getText().toString().contains("失败"),"failure is explicit and retry is enabled");
                check(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled(),"failed image offers retry");
                Rect screen=new Rect();dialog.getWindow().getDecorView().getWindowVisibleDisplayFrame(screen);report.append("VISIBLE ").append(screen.toShortString()).append('\n');
                check(within(dialog.getButton(AlertDialog.BUTTON_POSITIVE),screen)&&within(dialog.getButton(AlertDialog.BUTTON_NEGATIVE),screen),"close and retry fully inside available screen");
                View viewport=(View)field(preview[0],"current");
                check(within(viewport,screen)&&viewport.getHeight()>=48*activity.getResources().getDisplayMetrics().density,"image viewport inside screen with at least 48dp usable height");
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            }catch(Exception e){throw new IllegalStateException(e);}});
            deadline=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<deadline&&!((Boolean)field(preview[0],"settled")))Thread.sleep(50);
            test.waitForIdleSync();Thread.sleep(250);
            test.runOnMainSync(()->{try{
                check(((android.widget.TextView)field(preview[0],"status")).getText().toString().contains("已显示"),"retry decodes image in fresh WebView");
                check(loads.get()==2&&invalidations.get()==1,"retry fetches once and invalidates only current image");
                WebView web=(WebView)field(preview[0],"current");report.append("IMAGE content=").append(web.getContentHeight()).append(" height=").append(web.getHeight()).append(" scale=").append(web.getScale()).append('\n');check(web.getSettings().getBuiltInZoomControls()&&web.getContentHeight()>web.getHeight()/web.getScale(),"long image retains zoom and vertical scrolling");
            }catch(Exception e){throw new IllegalStateException(e);}});
            CountDownLatch painted=new CountDownLatch(1);test.runOnMainSync(()->{try{((WebView)field(preview[0],"current")).postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){painted.countDown();}});}catch(Exception e){throw new IllegalStateException(e);}});check(painted.await(3,TimeUnit.SECONDS),"resized image frame is committed before screenshot");
            Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot!=null){try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"image-preview-layout.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
            test.runOnMainSync(()->preview[0].dismiss());test.waitForIdleSync();check(field(preview[0],"current")==null,"closing releases image WebView");
            check(reader==field(owner,"readerWeb")&&position.equals(js(reader,"JSON.stringify({y:scrollY,url:location.href})")),"closing preview preserves underlying reader instance, URL and scroll position");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        finally{MainActivity activity=owner;test.runOnMainSync(()->{if(preview[0]!=null)preview[0].dismiss();if(activity!=null)activity.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
