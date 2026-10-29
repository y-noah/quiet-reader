package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Actual tap on a wrapped source link, using synthetic cached articles, never a remote page. */
final class ReaderLinkNavigationProbe {
    private final Instrumentation test;private MainActivity reader;private Repository repo;
    private int checks,failures;private final StringBuilder report=new StringBuilder("SYNTHETIC cached link navigation; no live source/account certification\n");
    ReaderLinkNavigationProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(java.util.function.BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(java.util.function.BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){if(onUi(condition))return true;Thread.sleep(40);}return false;}
    private String js(String code)throws Exception{
        WebView web=(WebView)field(reader,"readerWeb");CountDownLatch done=new CountDownLatch(1);String[] value={""};ui(()->{try{Method method=MainActivity.class.getDeclaredMethod("readerScript",WebView.class,String.class,java.util.function.Consumer.class);method.setAccessible(true);method.invoke(reader,web,code,(java.util.function.Consumer<String>)result->{value[0]=result;done.countDown();});}catch(Exception error){throw new IllegalStateException(error);}});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Reader script timeout");return value[0];
    }
    private void dom()throws Exception{long until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){String expected=(String)field(reader,"readerPageUrl");if(js("location.href==="+JSONObject.quote(expected)+"&&document.readyState==='complete'").equals("true"))return;Thread.sleep(40);}throw new IllegalStateException("Reader DOM not ready");}
    void run(){
        try{
            long stamp=System.currentTimeMillis();Item first=new Item(Source.V2EX,"正文链接浏览测试","https://www.v2ex.com/t/"+stamp,"");Item second=new Item(Source.TIEBA,"链接内容","https://tieba.baidu.com/p/"+(stamp+1),"正文链接");
            String wrapped="https://link.zhihu.com/?target="+java.net.URLEncoder.encode(second.url,java.nio.charset.StandardCharsets.UTF_8);
            Document a=new Document();a.url=first.url;a.title=first.title;Section main=new Section("link-main","正文",false);
            for(int n=0;n<18;n++)main.blocks.add(new Block("text","第"+n+"段：这是一段用于验证跨帖子跳转和返回位置的合成文字。".repeat(3)));
            String label="继续查看引用帖子";main.blocks.add(new Block("text",label,Collections.emptyList(),List.of(new InlineLink(0,label.length(),wrapped))));
            for(int n=18;n<28;n++)main.blocks.add(new Block("text","后续第"+n+"段：保留原始阅读上下文。".repeat(3)));a.sections.add(main);a.blocks.addAll(main.blocks);ArticleVersion.capture(first,a);
            Document b=new Document();b.url=second.url;b.title="引用帖子的实际标题";b.blocks.add(new Block("text","已提前准备的引用帖子正文，无需打开来源网页。"));ArticleVersion.capture(second,b);
            boolean[] cached={false};ui(()->{repo=new Repository(test.getTargetContext());repo.cacheArticle(a);repo.cacheArticle(b);cached[0]=repo.cachedArticle(first)!=null&&repo.cachedArticle(second)!=null;});check(cached[0],"both synthetic article bodies are prepared in the shared cache");
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",first.json().toString()));
            if(!await(()->field(reader,"readerWeb")!=null&&field(reader,"reading")!=null))throw new IllegalStateException("First body unavailable");dom();
            js("document.querySelector('.content-link').scrollIntoView({block:'center'})");Thread.sleep(650);
            JSONObject point=new JSONObject(js("(()=>{const a=document.querySelector('.content-link'),r=a.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,top:r.top,scroll:scrollY}})()"));
            check(point.getDouble("scroll")>0,"source link is tapped from a nonzero reading position");
            float[] xy=new float[2];ui(()->{WebView web=(WebView)field(reader,"readerWeb");int[] location=new int[2];web.getLocationOnScreen(location);xy[0]=location[0]+(float)point.optDouble("x")*web.getScale();xy[1]=location[1]+(float)point.optDouble("y")*web.getScale();});
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,xy[0],xy[1],0);try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
            boolean internal=await(()->{Item current=(Item)field(reader,"current");return current!=null&&current.url.equals(second.url)&&field(reader,"reading")!=null;});check(internal,"actual wrapped-link tap enters the supported target in the unified reader");if(internal){dom();
            check(onUi(()->field(reader,"dynamic")==null&&field(reader,"pendingArticle")==null&&((Deque<?>)field(reader,"history")).size()==1),"cached target uses one reading history entry without starting source acquisition");
            ui(()->{Object mark=((android.util.LruCache<?,?>)field(reader,"marks")).snapshot().get(first.url);report.append("savedPosition=").append(field(mark,"position")).append(" savedViewport=").append(field(mark,"viewport")).append('\n');});
            check(js("document.body.textContent.includes('引用帖子的实际标题')&&document.body.textContent.includes('已提前准备的引用帖子正文')").equals("true"),"target title and body render through the common reader");
            test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            if(!await(()->{Item current=(Item)field(reader,"current");return current!=null&&current.url.equals(first.url)&&field(reader,"reading")!=null;}))throw new IllegalStateException("Original article not restored");dom();
            long until=SystemClock.elapsedRealtime()+6000;double top=Double.NaN;while(SystemClock.elapsedRealtime()<until){top=Double.parseDouble(js("document.querySelector('.content-link').getBoundingClientRect().top"));if(Math.abs(top-point.getDouble("top"))<3)break;Thread.sleep(60);}
            report.append("linkTopBefore=").append(point.getDouble("top")).append(" linkTopAfter=").append(top).append('\n');
            ui(()->report.append("returnedScroll=").append(((WebView)field(reader,"readerWeb")).getScrollY()).append(" pendingViewport=").append(field(reader,"pendingViewport")).append(" openPosition=").append(field(reader,"openPosition")).append('\n'));
            check(Math.abs(top-point.getDouble("top"))<3,"back returns the original link paragraph to its prior visible position");
            check(onUi(()->((Deque<?>)field(reader,"history")).isEmpty()&&field(reader,"dynamic")==null),"back consumes the nested history entry and preserves the cached reader");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append('\n');}
        finally{if(reader!=null){test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);ui(reader::finish);}if(repo!=null)ui(repo::close);test.waitForIdleSync();}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append('\n').toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
