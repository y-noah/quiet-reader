package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.MotionEvent;
import android.webkit.WebView;
import java.lang.reflect.Field;
import java.util.*;
import static app.quietreader.Models.*;

/** Natural early touch against a cached forum body; controlled transport, not a live Tieba claim. */
final class ReaderEarlyContinuationProbe {
    private final Instrumentation test;private final String mode;private MainActivity reader;private Repository cache;
    private int calls,checks,failures;private final StringBuilder report=new StringBuilder();
    ReaderEarlyContinuationProbe(Instrumentation test,String mode){this.test=test;this.mode=mode;}
    private void ui(Runnable r){Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    void run(){Context context=test.getTargetContext();String database="test-early-continuation-"+UUID.randomUUID()+".db",url="https://www.v2ex.com/t/"+System.currentTimeMillis();
        try{
            Item item=new Item(Source.V2EX,"快速阅读自动续页",url,"",false);
            ui(()->{cache=new Repository(context);Document doc=new Document();doc.url=url;doc.title=item.title;doc.discussionPage=1;doc.nextUrl=url+"?p=2";
                Section main=new Section("early-main","正文",false);for(int n=0;n<10;n++)main.blocks.add(new Block("text","第"+n+"段，快速阅读缓存正文，滑到后面应当继续取得回复。这里使用受控正文验证真实滑动与页面恢复之间的时序。"));doc.sections.add(main);doc.blocks.addAll(main.blocks);ArticleVersion.capture(item,doc);new ArticleSession(context,item).stamp(doc);cache.cacheArticle(doc);});
            // Warm the ordinary WebView runtime first, as when opening a second topic.
            WebView[] warm={null};ui(()->{warm[0]=new WebView(context);warm[0].loadData("<html><body>runtime warmup</body></html>","text/html","UTF-8");});Thread.sleep(2200);
            reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ui(()->warm[0].destroy());
            ui(()->{try{Field f=MainActivity.class.getDeclaredField("discussions");f.setAccessible(true);f.set(reader,new DiscussionStream(reader,item,new DiscussionStream.Loader(){public void load(Item page,Repository.Result<Document> cb){calls++;}public void cancel(){}}));}catch(Exception e){throw new IllegalStateException(e);}});
            int[] bounds=new int[4];long[] remaining={0};boolean[] ready={false};long end=SystemClock.elapsedRealtime()+6000;
            do{ui(()->{WebView web=(WebView)field("readerWeb");if(web==null)return;int[] p=new int[2];web.getLocationOnScreen(p);bounds[0]=p[0];bounds[1]=p[1];bounds[2]=web.getWidth();bounds[3]=web.getHeight();remaining[0]=(long)field("suppressAutoUntil")-System.currentTimeMillis();ready[0]=reader.hasWindowFocus()&&web.getHeight()>0&&web.getContentHeight()*web.getScale()>web.getHeight()*1.1f;});if(!ready[0])Thread.sleep(10);}while(!ready[0]&&SystemClock.elapsedRealtime()<end);
            report.append("ready=").append(ready[0]).append(" guardRemainingMs=").append(remaining[0]).append('\n');check(ready[0]&&remaining[0]>350,"real rendered scrollable reader has at least 350ms natural restoration guard remaining");
            if(!mode.equals("passive")){
            float x=bounds[0]+bounds[2]*.5f,from=bounds[1]+bounds[3]*.9f,to=bounds[1]+bounds[3]*.12f;long time=SystemClock.uptimeMillis();
            for(int n=0;n<=12;n++){MotionEvent e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,from+(to-from)*n/12f,0);try{test.sendPointerSync(e);}finally{e.recycle();}if(n<12)Thread.sleep(15);}
            ui(()->{WebView web=(WebView)field("readerWeb");long guard=(long)field("suppressAutoUntil")-System.currentTimeMillis();float distance=web.getContentHeight()*web.getScale()-web.getScrollY()-web.getHeight();report.append("y=").append(web.getScrollY()).append(" distance=").append(distance).append(" height=").append(web.getHeight()).append(" guardRemainingMs=").append(guard).append(" calls=").append(calls).append('\n');check(web.getScrollY()>0&&distance<web.getHeight()*.75f&&guard>0&&calls==0,"actual early forward gesture reaches continuation threshold while restoration guard still rejects work");});
            if(mode.equals("background")){ui(()->{check(field("deferredContinuation")!=null,"gesture has a pending finite check before leaving reader");reader.startActivity(new Intent(reader,SettingsActivity.class));});test.waitForIdleSync();ui(()->check(!(boolean)field("readingResumed")&&field("deferredContinuation")==null,"actual Settings navigation pauses reader and cancels the pending check"));}
            }
            Thread.sleep(Math.max(0,remaining[0])+1800);
            ui(()->check(calls==(mode.equals("forward")?1:0),mode.equals("forward")?"one deferred continuation starts after guard without another gesture":"no continuation is acquired for "+mode));
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(cache!=null){cache.releaseArticleMemory();cache.close();}});test.waitForIdleSync();context.getSharedPreferences("reading-history",0).edit().remove(url).commit();context.deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
