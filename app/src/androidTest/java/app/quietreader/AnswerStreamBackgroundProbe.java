package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real HOME/onStop releases both idle and active Zhihu continuation sources. Synthetic DOM only. */
final class AnswerStreamBackgroundProbe {
    private final Instrumentation test;
    private MainActivity activity;
    private Repository cache;
    private AnswerStream stream;
    private WebView source;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    AnswerStreamBackgroundProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String name,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Deadline: "+name);Thread.sleep(30);}}
    private void home()throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent 3");java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}await("onStop",()->(boolean)field(activity,"wasStopped"));}
    private void resume()throws Exception{ui(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));await("resume",()->!(boolean)field(activity,"wasStopped")&&activity.hasWindowFocus());}
    private void installSource(Item item)throws Exception{
        CountDownLatch ready=new CountDownLatch(1);
        String html="<html><body><div class='AnswerItem' data-zop='{&quot;itemId&quot;:123}'><div class='RichContent-inner'><div class='RichText'>已有的同一条回答</div></div></div></body></html>";
        ui(()->{source=new WebView(activity);SourceSession.configure(activity,item.source,source);source.getSettings().setJavaScriptEnabled(true);source.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView web,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}@Override public void onPageFinished(WebView web,String url){if(item.url.equals(url))ready.countDown();}});source.loadUrl(item.url);});
        if(!ready.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Source fixture not ready");
        ui(()->{stream=new AnswerStream(activity,item,source);WebViewClient delegate=source.getWebViewClient();source.setWebViewClient(new WebViewClient(){@Override public WebResourceResponse shouldInterceptRequest(WebView web,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream(html.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}@Override public void onPageFinished(WebView web,String url){delegate.onPageFinished(web,url);}});set(activity,"answers",stream);check(item.url.equals(source.getUrl())&&AnswerStream.sameQuestion(item,source.getUrl()),"source fixture retains the exact current question URL before extraction");});
    }
    void run(){
        try{
            long now=System.currentTimeMillis();Item item=new Item(Source.ZHIHU,"中文知乎后台资源样本","https://qr-background-"+now+".zhihu.com/question/123","");Document doc=new Document();doc.url=item.url;doc.title=item.title;Section answer=new Section("zhihu-123","回答 · 受控作者",true);answer.blocks.add(new Block("text","已有的同一条回答。正文足够长，后台恢复不应重复加载，也不应丢失已读文字。\n".repeat(200)));doc.sections.add(answer);doc.blocks.addAll(answer.blocks);ArticleVersion.capture(item,doc);
            ui(()->{cache=new Repository(test.getTargetContext());new ArticleSession(test.getTargetContext(),item).stamp(doc);cache.cacheArticle(doc);});
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("discovery_item",item.json().toString()));await("reader",()->field(activity,"reading")!=null&&activity.hasWindowFocus());
            // This fixture has enough text to avoid automatic near-bottom continuation.
            installSource(item);AnswerStream idle=stream;WebView idleWeb=source;check(onUi(()->!idle.busy()&&idleWeb.getParent()!=null),"idle source is attached and reusable before leaving the reader");
            home();check(onUi(()->field(activity,"answers")==null&&(boolean)field(idle,"closed")&&((ExecutorService)field(idle,"parser")).isShutdown()&&idleWeb.getParent()==null),"HOME releases the idle source WebView and parser instead of retaining a live website in background");
            check(onUi(()->field(activity,"reading")==doc),"idle source release preserves the displayed document");resume();check(onUi(()->field(activity,"answers")==null&&!(boolean)field(activity,"morePaused")),"foreground return keeps cached reading without reopening an idle source");

            installSource(item);AnswerStream active=stream;WebView activeWeb=source;
            ui(()->{try{Method more=MainActivity.class.getDeclaredMethod("loadMore");more.setAccessible(true);more.invoke(activity);}catch(Exception e){throw new IllegalStateException(e);}});
            check(onUi(()->active.busy()&&(boolean)field(activity,"loadingMore")),"actual reader continuation owns an active source request before HOME");
            home();check(onUi(()->field(activity,"answers")==null&&!active.busy()&&(boolean)field(active,"closed")&&((ExecutorService)field(active,"parser")).isShutdown()&&activeWeb.getParent()==null&&!(boolean)field(activity,"loadingMore")),"HOME cancels active answer extraction, its handler work, parser and source viewport");
            Thread.sleep(1500);check(onUi(()->field(activity,"reading")==doc&&field(activity,"answers")==null),"late extraction callbacks do not replace cached reading while backgrounded");
            resume();check(onUi(()->field(activity,"reading")==doc&&field(activity,"answers")==null&&(boolean)field(activity,"morePaused")&&doc.moreStatus.equals("error")),"interrupted continuation returns paused with existing answers and no automatic source restart");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(stream!=null)stream.close();if(activity!=null)activity.finish();if(cache!=null)cache.close();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
