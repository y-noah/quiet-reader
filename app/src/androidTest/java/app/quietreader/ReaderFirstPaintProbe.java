package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.lang.reflect.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real native loading surface -> Chromium visual commit, with no source network. */
final class ReaderFirstPaintProbe {
    private final Instrumentation test;
    private MainActivity reader;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReaderFirstPaintProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String text){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(text).append('\n');}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}
    private void call(String name,Class<?>[] types,Object... args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(reader,args);}
    private void ui(Runnable action){test.runOnMainSync(action);}
    void run(){
        String database="test-ui-first-paint.db";
        try{
            // No initial item: onCreate finishes; prevent finish in the lifecycle by
            // supplying a preseeded memory cache, just as a real cached opening does.
            Item item=new Item(Source.ITHOME,"正文首帧切换验证","https://www.ithome.com/0/first-paint-987654.htm","");
            Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","可见正文首帧，加载界面只能在这些文字已绘制后撤下。".repeat(20)));ArticleVersion.capture(item,doc);
            Repository cache=new Repository(test.getTargetContext());cache.cacheArticle(doc);cache.close();
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database));
            for(int round=0;round<3;round++){
                View[] retained={null};WebView[] target={null};
                ui(()->{try{
                    call("frame",new Class<?>[]{});call("scroller",new Class<?>[]{});
                    Field reading=MainActivity.class.getDeclaredField("reading");reading.setAccessible(true);reading.set(reader,null);
                    call("status",new Class<?>[]{String.class,String.class},"正在整理内容…","保持到正文实际绘制");
                    retained[0]=(View)field("scroll");ViewGroup content=(ViewGroup)field("content");View heading=content.getChildAt(1);
                    call("status",new Class<?>[]{String.class,String.class},"正在整理内容…","保持到正文实际绘制");
                    check(content.getChildAt(1)==heading,"unchanged acquisition status does not rebuild spinner/text");
                    call("renderCached",new Class<?>[]{Document.class},doc);target[0]=(WebView)field("readerWeb");
                    check(field("readerLoadingSurface")==retained[0]&&retained[0].getParent()==field("readerSurface"),"loading surface survives data arrival before any body frame");
                    check(target[0].getAlpha()==0f,"unpainted WebView cannot expose an empty frame");
                }catch(Exception e){throw new IllegalStateException(e);}});
                long deadline=SystemClock.elapsedRealtime()+10000;boolean[] revealed={false};
                do{ui(()->{try{revealed[0]=field("readerLoadingSurface")==null&&target[0].getAlpha()==1f;}catch(Exception e){throw new IllegalStateException(e);}});if(!revealed[0])Thread.sleep(20);}while(!revealed[0]&&SystemClock.elapsedRealtime()<deadline);
                check(revealed[0],"visual commit reveals body and removes loader, round "+round);
                check(retained[0].getParent()==null,"loading overlay does not intercept reading or scrolling");
                CountDownLatch done=new CountDownLatch(1);String[] body={""};ui(()->target[0].evaluateJavascript("document.body.innerText.includes('可见正文首帧')",v->{body[0]=v;done.countDown();}));
                check(done.await(3,TimeUnit.SECONDS)&&"true".equals(body[0]),"revealed page contains exact body, not just a ready cache flag");
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(reader!=null)reader.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
