package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.*;
import android.webkit.CookieManager;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Production native HTTP/body parsing with gated synthetic responses and actual lifecycle changes. */
final class PendingArticleRotationProbe {
    // Future.get can return before ThreadPoolExecutor increments its completed count.
    // Sample on the single writer itself; each subsequent sample includes one prior marker.
    private long drainedWrites(ThreadPoolExecutor writer)throws Exception{return writer.submit((Callable<Long>)writer::getCompletedTaskCount).get(10,TimeUnit.SECONDS);}
    private final Instrumentation test;private MainActivity reader;private Repository cache;
    private final boolean promoted;
    private ArticlePreloader launchedLoader;
    private final List<Activity> origins=new ArrayList<>();
    private final List<String> databases=new ArrayList<>();
    private final List<ArticlePreloader> standaloneLoaders=new ArrayList<>();
    private final List<Response> requests=new CopyOnWriteArrayList<>();
    private final Map<String,byte[]> bodies=new ConcurrentHashMap<>();
    private final List<PendingArticle> standalone=new ArrayList<>();
    private final CountDownLatch diskWorkersRelease=new CountDownLatch(1);
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private static final class Response {final String url;final AtomicBoolean release=new AtomicBoolean(),disconnected=new AtomicBoolean();volatile int status=200;Response(String url){this.url=url;}}
    PendingArticleRotationProbe(Instrumentation test){this(test,false);}
    PendingArticleRotationProbe(Instrumentation test,boolean promoted){this.test=test;this.promoted=promoted;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Pending article deadline: "+label);Thread.sleep(30);}}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] block=new byte[1024];while(in.read(block)!=-1){}}}
    private void cookie(String url,boolean present)throws Exception{CountDownLatch done=new CountDownLatch(1);ui(()->CookieManager.getInstance().setCookie(url,"qr_pending_rotation=changed; Path="+android.net.Uri.parse(url).getPath()+"; Secure; SameSite=Lax"+(present?"":"; Max-Age=0"),ignored->done.countDown()));if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Synthetic cookie callback missing");}
    private Item item(long id)throws Exception{
        String url="https://www.thepaper.cn/newsDetail_forward_"+id;String text="这是受控正文，用于验证实际网络请求不会因Activity转屏而重复，也不能在后台发布迟到内容。".repeat(30);
        JSONObject detail=new JSONObject().put("contId",Long.toString(id)).put("name","正文在途转屏合成样本").put("contType",0).put("forwardType","0").put("author","诊断作者").put("content","<p>"+text+"</p>");
        String html="<script id=__NEXT_DATA__ type=application/json>"+new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("contId",Long.toString(id)).put("detailData",new JSONObject().put("contentDetail",detail))))+"</script>";bodies.put(url,html.getBytes(StandardCharsets.UTF_8));return new Item(Source.THEPAPER,"正文在途转屏合成样本",url,"");
    }
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){@Override protected URLConnection openConnection(URL url)throws IOException{
        byte[] bytes=bodies.get(url.toString());if(bytes==null)throw new IOException("Unexpected synthetic article URL");Response response=new Response(url.toString());
        return new HttpURLConnection(url){
            public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){response.disconnected.set(true);}
            @Override public int getResponseCode()throws IOException{requests.add(response);long end=SystemClock.elapsedRealtime()+30000;while(!response.release.get()){if(SystemClock.elapsedRealtime()>end)throw new IOException("Controlled response deadline");try{Thread.sleep(15);}catch(InterruptedException ignored){}}return response.status;}
            @Override public long getContentLengthLong(){return bytes.length;}
            @Override public InputStream getInputStream(){return new ByteArrayInputStream(bytes);}
        };
    }});}
    private MainActivity launch(Item item)throws Exception{
        if(!promoted)return (MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String database="test-ui-"+UUID.randomUUID()+".db";databases.add(database);
        Activity home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));origins.add(home);
        ui(()->{try{DiscoveryController controller=(DiscoveryController)field(home,"controller");controller.interacting(true);((ArticlePreloader)field(controller,"preloader")).close();launchedLoader=new ArticlePreloader(home,()->{},null,20,()->{});Field loader=DiscoveryController.class.getDeclaredField("preloader");loader.setAccessible(true);loader.set(controller,launchedLoader);launchedLoader.offer(Collections.singletonList(item));}catch(Exception error){throw new IllegalStateException(error);}});
        await("native prefetch before click",()->requests.stream().anyMatch(response->response.url.equals(item.url)));
        Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
        try{
            ui(()->{try{java.lang.reflect.Method open=DiscoveryActivity.class.getDeclaredMethod("open",app.quietreader.discovery.HotCandidate.class);open.setAccessible(true);open.invoke(home,new app.quietreader.discovery.HotCandidate(item.source,item.url,item.title,"","",false,1,null,System.currentTimeMillis()));}catch(Exception error){throw new IllegalStateException(error);}});
            Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Promoted native reader missing");
            ui(home::finish);await("original discovery destroyed",home::isDestroyed);return (MainActivity)next;
        }finally{test.removeMonitor(monitor);}
    }
    private void rotate()throws Exception{
        MainActivity previous=reader;int target=previous.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT?ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);try{ui(()->previous.setRequestedOrientation(target));Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Recreated reader missing");reader=(MainActivity)next;}finally{test.removeMonitor(monitor);}
        await("old Activity destroyed",previous::isDestroyed);check(reader!=previous,"actual orientation creates a replacement reader while body is still pending");
    }
    void run(){android.content.SharedPreferences prefs=test.getTargetContext().getSharedPreferences("source-session",0);boolean had=prefs.contains("THEPAPER-web"),before=prefs.getBoolean("THEPAPER-web",false),accepted=CookieManager.getInstance().acceptCookie();List<Item> items=new ArrayList<>();String cookieUrl=null;
        try{
            prefs.edit().putBoolean("THEPAPER-web",false).commit();transport();long id=System.currentTimeMillis();Item first=item(id);items.add(first);ui(()->cache=new Repository(test.getTargetContext()));reader=launch(first);
            await("first native request",()->requests.size()==1);Response original=requests.get(0);MainActivity old=reader;
            check(onUi(()->field(reader,"reading")==null&&field(reader,"dynamic")==null),"baseline is a native body request before any document is displayed");
            rotate();Thread.sleep(400);
            check(requests.size()==1,"turning the screen retains one native HTTP request instead of starting another");
            check(!original.disconnected.get(),"valid in-flight request stays connected across configuration recreation");
            rotate();Thread.sleep(200);check(requests.size()==1&&!original.disconnected.get(),"a second consecutive rotation keeps the same in-flight request");
            if(promoted)check(onUi(()->field(reader,"preloader")==launchedLoader&&field(old,"preloader")==null),"actual native prefetch is promoted once and transferred across both rotations");
            java.lang.reflect.Field writerField=ArticleDiskCache.class.getDeclaredField("writer");writerField.setAccessible(true);
            java.util.concurrent.ThreadPoolExecutor writer=(java.util.concurrent.ThreadPoolExecutor)writerField.get(null);
            long writesBefore=drainedWrites(writer);
            for(Response response:requests)response.release.set(true);
            await("current reader receives document",()->field(reader,"reading")!=null&&(boolean)field(field(reader,"engagement"),"ready"));
            check(onUi(()->ArticleVersion.matches(first,(Document)field(reader,"reading"))&&field(reader,"dynamic")==null&&old.isDestroyed()&&field(old,"reading")==null),"completion reaches the current reader only, with matching metadata and no dynamic fallback");
            long writes=drainedWrites(writer)-writesBefore-1;
            check(writes==1,"native acquisition delivered across rotations writes its body exactly once (actual="+writes+", promoted="+promoted+")");
            Item background=item(id+1);items.add(background);int count=requests.size();reader=launch(background);await("second gated native request",()->requests.size()>count);Response blocked=requests.get(requests.size()-1);shell("input keyevent 3");await("actual background",()->(boolean)field(reader,"wasStopped"));Thread.sleep(200);
            check(blocked.disconnected.get(),"HOME disconnects the foreground body request instead of retaining it as background work");
            blocked.release.set(true);Thread.sleep(400);
            check(onUi(()->field(reader,"reading")==null&&cache.cachedArticle(background)==null),"late background completion cannot publish a body into the stopped reader or cache");
            int stoppedCount=requests.size();ui(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));await("foreground native retry",()->requests.size()>stoppedCount);Response resumed=requests.get(requests.size()-1);
            check(resumed.url.equals(background.url)&&requests.size()==stoppedCount+1,"returning to the unfinished article starts exactly one fresh request");resumed.release.set(true);await("resumed reader ready",()->field(reader,"reading")!=null&&(boolean)field(field(reader,"engagement"),"ready"));
            check(onUi(()->ArticleVersion.matches(background,(Document)field(reader,"reading"))),"foreground retry displays the matching body after background cancellation");

            Item account=item(id+2);items.add(account);int accountBefore=requests.size();reader=launch(account);await("old-session request",()->requests.size()>accountBefore);Response oldSession=requests.get(requests.size()-1);
            ui(()->CookieManager.getInstance().setAcceptCookie(true));cookieUrl=account.url;cookie(account.url,true);rotate();await("new-session request",()->requests.size()>accountBefore+1);Response newSession=requests.get(requests.size()-1);
            check(oldSession.disconnected.get()&&newSession!=oldSession&&requests.size()==accountBefore+2,"session change during rotation discards the old acquisition and starts one current-session request");
            oldSession.release.set(true);Thread.sleep(250);check(onUi(()->field(reader,"reading")==null&&cache.cachedArticle(account)==null),"late old-session response is rejected before presentation and cache publication");
            newSession.release.set(true);await("new-session reader ready",()->field(reader,"reading")!=null&&(boolean)field(field(reader,"engagement"),"ready"));
            check(onUi(()->ArticleDiskCache.session(account.url).equals(((Document)field(reader,"reading")).cachedSession)&&field(reader,"dynamic")==null),"current-session acquisition reaches the actual unified reader without a fallback loop");

            Item gap=item(id+3);items.add(gap);PendingArticle[] buffered={null};AtomicInteger oldDeliveries=new AtomicInteger(),newDeliveries=new AtomicInteger();int gapBefore=requests.size();
            if(promoted){
                ArticlePreloader[] gapLoader={null};
                ui(()->{gapLoader[0]=new ArticlePreloader(reader,()->{},null,20,()->{});standaloneLoaders.add(gapLoader[0]);gapLoader[0].offer(Collections.singletonList(gap));});
                await("native prefetch for ownership gap",()->requests.size()>gapBefore);
                ui(()->{if(!gapLoader[0].promote(gap,new Repository.Result<Document>(){public void success(Document doc){oldDeliveries.incrementAndGet();}public void failure(String why){oldDeliveries.incrementAndGet();}}))throw new IllegalStateException("Gap prefetch did not promote");gapLoader[0].detachForeground();});
                requests.get(requests.size()-1).release.set(true);await("native completion while detached",()->field(gapLoader[0],"bufferedResult")!=null);
                check(onUi(()->gapLoader[0].canRetainForeground(gap)&&field(gapLoader[0],"activity")==null&&field(gapLoader[0],"foreground")==null)&&oldDeliveries.get()==0,"promoted native completion buffers without retaining the old Activity or receiver");
                Repository.Result<Document> receiver=new Repository.Result<Document>(){public void success(Document doc){if(ArticleVersion.matches(gap,doc))newDeliveries.incrementAndGet();}public void failure(String why){throw new IllegalStateException("Buffered prefetch failed");}};
                ui(()->{gapLoader[0].resumeForeground(reader,gap,receiver,()->{});gapLoader[0].resumeForeground(reader,gap,receiver,()->{});});
                check(oldDeliveries.get()==0&&newDeliveries.get()==1&&onUi(()->!gapLoader[0].canRetainForeground(gap)),"buffered promoted native result is delivered once to the new owner");
                long fallbackWritesBefore=drainedWrites(writer);
                Item fallback=item(id+5);items.add(fallback);ArticlePreloader[] fallbackLoader={null};AtomicInteger fallbackDelivered=new AtomicInteger(),fallbackRequests=new AtomicInteger();int fallbackBefore=requests.size();
                ui(()->{fallbackLoader[0]=new ArticlePreloader(reader,()->{},null,20,()->{});standaloneLoaders.add(fallbackLoader[0]);fallbackLoader[0].offer(Collections.singletonList(fallback));});
                await("native failure request before ownership gap",()->requests.size()>fallbackBefore);Response failing=requests.get(requests.size()-1);failing.status=500;
                Repository.Result<Document> fallbackReceiver=new Repository.Result<Document>(){public void success(Document doc){if(ArticleVersion.matches(fallback,doc)&&doc.hasContent())fallbackDelivered.incrementAndGet();}public void failure(String why){throw new IllegalStateException("Deferred fallback failed");}};
                ui(()->{if(!fallbackLoader[0].promote(fallback,fallbackReceiver))throw new IllegalStateException("Fallback did not promote");fallbackLoader[0].detachForeground();});failing.release.set(true);
                await("fallback deferred without an Activity",()->(boolean)field(fallbackLoader[0],"deferredDynamic"));
                check(onUi(()->field(fallbackLoader[0],"activity")==null&&field(fallbackLoader[0],"dynamic")==null&&fallbackLoader[0].canRetainForeground(fallback)),"native failure during ownership gap defers WebView fallback until a new Activity exists");
                ui(()->{
                    fallbackLoader[0].resumeForeground(reader,fallback,fallbackReceiver,()->{});DynamicReader extractor=(DynamicReader)field(fallbackLoader[0],"dynamic");android.webkit.WebView web=(android.webkit.WebView)field(extractor,"web");android.webkit.WebViewClient production=web.getWebViewClient();
                    web.setWebViewClient(new android.webkit.WebViewClient(){
                        @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest request){if(request.isForMainFrame())fallbackRequests.incrementAndGet();byte[] body=request.getUrl().toString().equals(fallback.url)?bodies.get(fallback.url):new byte[0];return new android.webkit.WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(body));}
                        @Override public void onPageCommitVisible(android.webkit.WebView view,String url){production.onPageCommitVisible(view,url);}
                        @Override public void onPageFinished(android.webkit.WebView view,String url){production.onPageFinished(view,url);}
                        @Override public boolean shouldOverrideUrlLoading(android.webkit.WebView view,android.webkit.WebResourceRequest request){return production.shouldOverrideUrlLoading(view,request);}
                    });
                });
                await("deferred dynamic fallback completes",()->fallbackDelivered.get()==1);
                check(fallbackRequests.get()==1&&onUi(()->field(fallbackLoader[0],"active")==null&&field(fallbackLoader[0],"dynamic")==null),"new owner starts one deferred source load and receives its parsed body exactly once");
                long fallbackWrites=drainedWrites(writer)-fallbackWritesBefore-1;
                check(fallbackWrites==1,"fresh dynamic fallback still saves its body exactly once (actual="+fallbackWrites+")");
                ArticleDiskCache fallbackDisk=new ArticleDiskCache(test.getTargetContext());String[] fallbackSession={null};ui(()->fallbackSession[0]=ArticleDiskCache.session(fallback.url));
                ArticleDiskCache.Hit fallbackHit=fallbackDisk.read(fallback.url,fallbackDisk.namespace(),fallbackSession[0],false,System.currentTimeMillis());
                check(fallbackHit!=null&&fallbackHit.document.hasContent()&&ArticleVersion.matches(fallback,fallbackHit.document),"dynamic fallback body remains readable from actual versioned disk cache");
            }else{
            ui(()->{buffered[0]=new PendingArticle(test.getTargetContext(),gap);standalone.add(buffered[0]);buffered[0].attach(new Repository.Result<Document>(){public void success(Document doc){oldDeliveries.incrementAndGet();}public void failure(String reason){oldDeliveries.incrementAndGet();}});buffered[0].start();buffered[0].detach();});
            await("detached acquisition",()->requests.size()>gapBefore);requests.get(requests.size()-1).release.set(true);await("completion while detached",()->(boolean)field(buffered[0],"completed"));
            check(oldDeliveries.get()==0&&onUi(()->buffered[0].matches(gap)),"a request completing between owners buffers its result without invoking the detached callback");
            Repository.Result<Document> receiver=new Repository.Result<Document>(){public void success(Document doc){if(ArticleVersion.matches(gap,doc))newDeliveries.incrementAndGet();}public void failure(String reason){throw new IllegalStateException("Buffered request failed");}};
            ui(()->{buffered[0].attach(receiver);buffered[0].attach(receiver);});check(newDeliveries.get()==1&&oldDeliveries.get()==0,"buffered completion is delivered exactly once to the replacement owner");
            }

            Item diskWaiting=item(id+4);items.add(diskWaiting);int diskBefore=requests.size();CountDownLatch workersOccupied=new CountDownLatch(3);
            java.util.concurrent.ExecutorService workers=(java.util.concurrent.ExecutorService)field(field(field(reader,"repo"),"requests"),"workers");
            for(int n=0;n<3;n++)workers.execute(()->{workersOccupied.countDown();try{diskWorkersRelease.await();}catch(InterruptedException ignored){}});
            if(!workersOccupied.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Disk worker gate missing");
            ui(()->{try{java.lang.reflect.Method open=MainActivity.class.getDeclaredMethod("open",Item.class,boolean.class);open.setAccessible(true);open.invoke(reader,diskWaiting,false);}catch(Exception error){throw new IllegalStateException(error);}});
            check(onUi(()->field(reader,"reading")==null)&&requests.size()==diskBefore,"disk restore is queued before any native request starts");
            shell("input keyevent 3");await("background before disk restore returns",()->(boolean)field(reader,"wasStopped"));diskWorkersRelease.countDown();
            Thread.sleep(350);check(requests.size()==diskBefore&&onUi(()->field(reader,"reading")==null),"background cancellation of queued disk lookup cannot launch a new native request afterward");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{
            diskWorkersRelease.countDown();for(Response response:requests)response.release.set(true);MainActivity owner=reader;ui(()->{if(owner!=null)owner.finish();for(Activity origin:origins)origin.finish();if(cache!=null)cache.close();for(PendingArticle pending:standalone)pending.close();for(ArticlePreloader loader:standaloneLoaders)loader.close();});test.waitForIdleSync();for(String database:databases)test.getTargetContext().deleteDatabase(database);if(cookieUrl!=null)try{cookie(cookieUrl,false);}catch(Exception error){failures++;report.append("ERROR synthetic cookie cleanup\n");}ui(()->CookieManager.getInstance().setAcceptCookie(accepted));for(Item item:items)test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(item.url).commit();android.content.SharedPreferences.Editor edit=prefs.edit();if(had)edit.putBoolean("THEPAPER-web",before);else edit.remove("THEPAPER-web");edit.commit();
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
