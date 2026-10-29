package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import app.quietreader.discovery.HotCandidate;
import static app.quietreader.Models.*;

/** Real WebView/parser/Activity ownership, with intercepted synthetic HTML only. */
final class DynamicHandoffProbe {
    private final Instrumentation test;
    private final boolean rotatePending;
    private final boolean backgroundPending;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC HTML; real WebView and source parser, not live account validation\n");
    DynamicHandoffProbe(Instrumentation test){this(test,false);}
    DynamicHandoffProbe(Instrumentation test,boolean rotatePending){this(test,rotatePending,false);}
    DynamicHandoffProbe(Instrumentation test,boolean rotatePending,boolean backgroundPending){this.test=test;this.rotatePending=rotatePending;this.backgroundPending=backgroundPending;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private void destroyed(Activity activity)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+5_000;AtomicBoolean done=new AtomicBoolean();
        while(SystemClock.elapsedRealtime()<deadline&&!done.get()){ui(()->done.set(activity.isDestroyed()));if(!done.get())Thread.sleep(25);}
        if(!done.get())throw new IllegalStateException("Activity destruction timed out");
    }
    void run(){
        String database="test-ui-"+UUID.randomUUID()+".db";
        Activity[] home={null},reader={null};ArticlePreloader[] loader={null};DynamicReader[] dynamic={null};WebView[] source={null};Object[] continuation={null};
        CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger requests=new AtomicInteger();
        Instrumentation.ActivityMonitor monitor=null;
        Item item=new Item(Source.ZHIHU,"动态交接合成问题","https://www.zhihu.com/question/"+System.nanoTime(),"");
        try{
            home[0]=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ui(()->{
                DiscoveryController controller=(DiscoveryController)field(home[0],"controller");controller.interacting(true);
                loader[0]=new ArticlePreloader(home[0],()->{},new ArticlePreloader.Loader(){
                    public void cancel(){}
                    public void load(Item value,Repository.Result<Document> unused){
                        try{Method method=ArticlePreloader.class.getDeclaredMethod("loadDynamic",Item.class,int.class);method.setAccessible(true);method.invoke(loader[0],value,(int)field(loader[0],"revision"));}catch(Exception e){throw new IllegalStateException(e);}
                        dynamic[0]=(DynamicReader)field(loader[0],"dynamic");source[0]=(WebView)field(dynamic[0],"web");WebViewClient production=source[0].getWebViewClient();
                        source[0].setWebViewClient(new WebViewClient(){
                            @Override public WebResourceResponse shouldInterceptRequest(WebView web,WebResourceRequest request){
                                String html="";
                                if(request.isForMainFrame()&&item.url.equals(request.getUrl().toString())){
                                    requests.incrementAndGet();requested.countDown();try{release.await(15,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
                                    html="<html><head><meta charset='utf-8'></head><body><h1>动态交接合成问题</h1><div class='QuestionRichText'>问题补充说明。</div><div class='AnswerItem' data-zop='{&quot;itemId&quot;:123}'><div class='AuthorInfo-name'>合成作者</div><div class='RichContent-inner'><div class='RichText'><p>这是用于验证动态网页交接和续读复用的合成正文，不是真实平台内容。</p></div></div></div></body></html>";
                                }
                                return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                            }
                            @Override public void onPageCommitVisible(WebView web,String url){production.onPageCommitVisible(web,url);}
                            @Override public void onPageFinished(WebView web,String url){production.onPageFinished(web,url);}
                            @Override public boolean shouldOverrideUrlLoading(WebView web,WebResourceRequest request){return production.shouldOverrideUrlLoading(web,request);}
                        });
                    }
                },20,()->{});
                ((ArticlePreloader)field(controller,"preloader")).close();set(controller,"preloader",loader[0]);loader[0].offer(Collections.singletonList(item));
            });
            check(requested.await(8,TimeUnit.SECONDS),"real speculative WebView begins loading before click");
            monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            ui(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(home[0],new HotCandidate(item.source,item.url,item.title,"","",false,1,null,System.currentTimeMillis()));}catch(Exception e){throw new IllegalStateException(e);}});
            reader[0]=test.waitForMonitorWithTimeout(monitor,5000);check(reader[0]!=null,"reader receives the in-flight dynamic request");
            if(reader[0]==null)throw new IllegalStateException("Reader absent");
            ui(()->{
                check(field(reader[0],"preloader")==loader[0],"dynamic preloader ownership transfers to reader");
                check(source[0].getContext() instanceof MutableContextWrapper&&((MutableContextWrapper)source[0].getContext()).getBaseContext()==reader[0],"WebView context is rebound to reader Activity");
                check(source[0].getParent()==reader[0].findViewById(android.R.id.content),"same WebView is attached behind reader UI");home[0].finish();
            });
            destroyed(home[0]);
            if(rotatePending)for(int turn=0;turn<2;turn++){
                Activity previous=reader[0];int orientation=previous.getResources().getConfiguration().orientation;
                test.removeMonitor(monitor);monitor=test.addMonitor(MainActivity.class.getName(),null,false);
                ui(()->previous.setRequestedOrientation(orientation==1?android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));
                Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(next==null)throw new IllegalStateException("Rotated promoted reader absent");reader[0]=next;destroyed(previous);
                ui(()->{
                    check(field(reader[0],"preloader")==loader[0]&&field(loader[0],"dynamic")==dynamic[0],"rotation retains the promoted loader and its original pending extractor");
                    check(source[0].getContext() instanceof MutableContextWrapper&&((MutableContextWrapper)source[0].getContext()).getBaseContext()==reader[0]&&source[0].getParent()==reader[0].findViewById(android.R.id.content),"promoted source context and viewport move to the rotated reader");
                    check(field(previous,"preloader")==null,"destroyed reader relinquishes its promoted loader ownership");
                });
            }
            if(backgroundPending){
                try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent 3");java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}
                long stoppedBy=SystemClock.elapsedRealtime()+5000;AtomicBoolean stopped=new AtomicBoolean();while(!stopped.get()&&SystemClock.elapsedRealtime()<stoppedBy){ui(()->stopped.set((boolean)field(reader[0],"wasStopped")));if(!stopped.get())Thread.sleep(25);}
                check(stopped.get(),"promoted dynamic reader actually stops after HOME");
                ui(()->check(field(loader[0],"active")==null&&field(loader[0],"dynamic")==null&&field(loader[0],"foreground")==null&&(boolean)field(dynamic[0],"closed")&&field(dynamic[0],"owner")==null&&source[0].getParent()==null,"HOME cancels promoted dynamic extraction and releases its source and callback"));
                release.countDown();Thread.sleep(400);
                ui(()->{check(field(reader[0],"reading")==null&&((Repository)field(loader[0],"repo")).cachedArticle(item)==null,"late promoted dynamic HTML cannot publish into the background reader or body cache");reader[0].finish();});
                destroyed(reader[0]);ui(()->check((boolean)field(loader[0],"closed"),"destroying the background reader closes its promoted loader"));
            }else{
            release.countDown();
            long end=SystemClock.elapsedRealtime()+12_000;AtomicBoolean rendered=new AtomicBoolean();
            while(SystemClock.elapsedRealtime()<end&&!rendered.get()){ui(()->rendered.set(field(reader[0],"reading")!=null));if(!rendered.get())Thread.sleep(50);}
            ui(()->{
                Document doc=(Document)field(reader[0],"reading");check(doc!=null&&doc.hasContent()&&!doc.sections.isEmpty(),"production snapshot and parser render body after original Activity finishes");
                continuation[0]=field(reader[0],"answers");check(continuation[0]!=null&&field(continuation[0],"web")==source[0],"Zhihu continuation owns the exact already-loaded WebView");
                check(requests.get()==1,"handoff and continuation setup do not reload the source");
                check((boolean)field(dynamic[0],"ended")&&!(boolean)field(dynamic[0],"ownsWeb"),"extractor stops while transferring WebView ownership");
                reader[0].finish();
            });destroyed(reader[0]);
            ui(()->check(continuation[0]!=null&&(boolean)field(continuation[0],"closed")&&source[0].getParent()==null,"reader destruction closes continuation and detaches source"));
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{release.countDown();if(monitor!=null)test.removeMonitor(monitor);ui(()->{if(reader[0]!=null)reader[0].finish();if(home[0]!=null)home[0].finish();if(loader[0]!=null)loader[0].close();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
