package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Synthetic gated HTML, production foreground dynamic acquisition and actual lifecycle. */
final class DynamicRotationProbe {
    private final Instrumentation test;
    private MainActivity reader;private Repository repo;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);
    private final AtomicInteger requests=new AtomicInteger();
    private DynamicReader initial;private WebView source;
    private final java.util.List<CountDownLatch> gates=new java.util.ArrayList<>();
    private final java.util.List<DynamicReader> standalone=new java.util.ArrayList<>();
    DynamicRotationProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Dynamic rotation deadline: "+label);Thread.sleep(30);}}
    private void rotate()throws Exception{
        MainActivity previous=reader;int orientation=previous.getResources().getConfiguration().orientation;
        Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
        try{ui(()->previous.setRequestedOrientation(orientation==1?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Replacement reader missing");reader=(MainActivity)next;}finally{test.removeMonitor(monitor);}
        await("old reader destroyed",previous::isDestroyed);
    }
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] bytes=new byte[1024];while(in.read(bytes)!=-1){}}}
    private void cookie(Item item,boolean present)throws Exception{CountDownLatch done=new CountDownLatch(1);ui(()->CookieManager.getInstance().setCookie(item.url,"qr_dynamic_rotation=changed; Path="+android.net.Uri.parse(item.url).getPath()+"; Secure; SameSite=Lax"+(present?"":"; Max-Age=0"),ignored->done.countDown()));if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic cookie callback missing");}
    private DynamicReader refresh(Item item,CountDownLatch started,CountDownLatch gate,AtomicInteger count){
        try{
            Field force=MainActivity.class.getDeclaredField("forceSourceRead");force.setAccessible(true);force.setBoolean(reader,true);
            Method open=MainActivity.class.getDeclaredMethod("open",Item.class,boolean.class);open.setAccessible(true);open.invoke(reader,item,false);
            DynamicReader dynamic=(DynamicReader)field(reader,"dynamic");intercept(dynamic,item,started,gate,count);return dynamic;
        }catch(Exception error){throw new IllegalStateException(error);}
    }
    private void intercept(DynamicReader dynamic,Item item,CountDownLatch started,CountDownLatch gate,AtomicInteger count){
        gates.add(gate);WebView web=(WebView)field(dynamic,"web");WebViewClient production=web.getWebViewClient();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String html="";if(request.isForMainFrame()&&request.getUrl().toString().equals(item.url)){
                    count.incrementAndGet();started.countDown();try{gate.await(25,TimeUnit.SECONDS);}catch(InterruptedException ignored){}
                    html="<html><head><meta charset='utf-8'></head><body><h1>动态转屏合成问题</h1><div class='QuestionRichText'>合成问题补充。</div><div class='AnswerItem' data-zop='{&quot;itemId&quot;:123}'><div class='AuthorInfo-name'>合成作者</div><div class='RichContent-inner'><div class='RichText'><p>这是转屏后应继续取得的完整合成回答，不是真实平台内容。</p></div></div></div></body></html>";
                }return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
            }
            @Override public void onPageCommitVisible(WebView view,String url){production.onPageCommitVisible(view,url);}
            @Override public void onPageFinished(WebView view,String url){production.onPageFinished(view,url);}
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return production.shouldOverrideUrlLoading(view,request);}
        });
    }
    void run(){
        Item item=new Item(Source.ZHIHU,"动态转屏合成问题","https://www.zhihu.com/question/"+System.currentTimeMillis(),"");
        boolean accepted=CookieManager.getInstance().acceptCookie(),cookieWritten=false;
        try{
            Document seed=new Document();seed.url=item.url;seed.title=item.title;seed.blocks.add(new Block("text","用于进入真实阅读页的合成旧正文。"));ArticleVersion.capture(item,seed);
            ui(()->{repo=new Repository(test.getTargetContext());repo.cacheArticle(seed);});
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("seed reading",()->field(reader,"reading")!=null);
            ui(()->{initial=refresh(item,requested,release,requests);source=(WebView)field(initial,"web");});
            check(requested.await(8,TimeUnit.SECONDS),"actual foreground dynamic WebView is pending before rotation");MainActivity original=reader;
            rotate();
            check(onUi(()->field(reader,"dynamic")==initial),"rotation transfers the existing dynamic extraction rather than creating another");
            check(onUi(()->source.getContext() instanceof MutableContextWrapper&&((MutableContextWrapper)source.getContext()).getBaseContext()==reader&&source.getParent()==reader.findViewById(android.R.id.content)),"pending source context and viewport belong to the replacement reader");
            rotate();
            check(onUi(()->field(reader,"dynamic")==initial&&requests.get()==1&&field(original,"dynamic")==null),"consecutive rotation preserves one request and releases the old Activity owner");
            release.countDown();await("current reader receives dynamic body",()->field(reader,"reading")!=null&&field(reader,"answers")!=null);
            check(onUi(()->((Document)field(reader,"reading")).blocks.stream().anyMatch(b->b.value.contains("完整合成回答"))),"production parser delivers the matching complete answer to the current reader");
            check(onUi(()->field(field(reader,"answers"),"web")==source&&requests.get()==1),"continuation reuses the exact source WebView without a new request");
            Document prepared=(Document)field(reader,"reading");
            CountDownLatch backgroundStarted=new CountDownLatch(1),backgroundRelease=new CountDownLatch(1);AtomicInteger backgroundCount=new AtomicInteger();DynamicReader[] background={null};WebView[] backgroundWeb={null};
            ui(()->{background[0]=refresh(item,backgroundStarted,backgroundRelease,backgroundCount);backgroundWeb[0]=(WebView)field(background[0],"web");});
            check(backgroundStarted.await(8,TimeUnit.SECONDS),"a second explicit foreground refresh is actually pending before HOME");
            shell("input keyevent 3");await("background reader",()->(boolean)field(reader,"wasStopped"));
            check(onUi(()->field(reader,"dynamic")==null&&(boolean)field(background[0],"closed")&&field(background[0],"owner")==null&&backgroundWeb[0].getParent()==null),"HOME closes extraction and releases its Activity and source viewport");
            backgroundRelease.countDown();Thread.sleep(350);
            check(onUi(()->field(reader,"reading")==null&&repo.cachedArticle(item)==prepared),"late background HTML cannot replace the reader or the last completed cache body");
            ui(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            await("prepared body after foreground return",()->field(reader,"reading")!=null&&((Document)field(reader,"reading")).blocks.stream().anyMatch(b->b.value.contains("完整合成回答"))&&!(boolean)field(reader,"wasStopped"));
            check(backgroundCount.get()==1&&onUi(()->field(reader,"dynamic")==null),"foreground return reuses the last valid body without restarting the cancelled dynamic page");

            CountDownLatch gapStarted=new CountDownLatch(1),gapRelease=new CountDownLatch(1);AtomicInteger gapRequests=new AtomicInteger(),oldDeliveries=new AtomicInteger(),newDeliveries=new AtomicInteger();DynamicReader[] gap={null};
            ui(()->{gap[0]=new DynamicReader(reader,item,new Repository.Result<Document>(){public void success(Document doc){oldDeliveries.incrementAndGet();}public void failure(String why){oldDeliveries.incrementAndGet();}},true);standalone.add(gap[0]);intercept(gap[0],item,gapStarted,gapRelease,gapRequests);});
            if(!gapStarted.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Detached source did not start");
            ui(()->gap[0].detach());gapRelease.countDown();await("detached dynamic completion",()->field(gap[0],"pendingResult")!=null);
            check(onUi(()->oldDeliveries.get()==0&&field(gap[0],"owner")==null&&field(gap[0],"callback")==null&&gap[0].matches(item)),"completion in the ownership gap buffers the body without retaining the old owner or callback");
            Repository.Result<Document> receiver=new Repository.Result<Document>(){public void success(Document doc){if(doc.hasContent())newDeliveries.incrementAndGet();}public void failure(String why){throw new IllegalStateException("Buffered source failed");}};
            ui(()->{gap[0].attach(reader,receiver);gap[0].attach(reader,receiver);});
            check(newDeliveries.get()==1&&oldDeliveries.get()==0&&gapRequests.get()==1,"buffered dynamic result is delivered exactly once to the replacement callback");
            CountDownLatch staleStarted=new CountDownLatch(1),staleRelease=new CountDownLatch(1);AtomicInteger staleRequests=new AtomicInteger(),staleSuccess=new AtomicInteger(),staleFailure=new AtomicInteger();DynamicReader[] stale={null};
            Repository.Result<Document> staleReceiver=new Repository.Result<Document>(){public void success(Document doc){staleSuccess.incrementAndGet();}public void failure(String why){staleFailure.incrementAndGet();}};
            ui(()->{stale[0]=new DynamicReader(reader,item,staleReceiver,true);standalone.add(stale[0]);intercept(stale[0],item,staleStarted,staleRelease,staleRequests);});
            if(!staleStarted.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Stale source did not start");
            ui(()->stale[0].detach());staleRelease.countDown();await("buffered source before session change",()->field(stale[0],"pendingResult")!=null);
            cookieWritten=true;cookie(item,true);
            check(onUi(()->!stale[0].matches(item)),"a buffered old-session source cannot be adopted after cookie identity changes");
            ui(()->stale[0].attach(reader,staleReceiver));
            check(staleSuccess.get()==0&&staleFailure.get()==1&&onUi(()->(boolean)field(stale[0],"closed")),"delivery checks the session again and releases stale buffered HTML without publishing its body");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{release.countDown();for(CountDownLatch gate:gates)gate.countDown();ui(()->{if(reader!=null)reader.finish();if(initial!=null)initial.close();for(DynamicReader dynamic:standalone)dynamic.close();if(repo!=null)repo.close();});test.waitForIdleSync();if(cookieWritten)try{cookie(item,false);}catch(Exception error){failures++;report.append("ERROR synthetic cookie cleanup\n");}ui(()->CookieManager.getInstance().setAcceptCookie(accepted));test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(item.url).commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
