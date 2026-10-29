package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.provider.Settings;
import android.webkit.WebView;
import app.quietreader.discovery.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Current public hot candidates -> unmodified discovery prefetch -> offline real card/reader.
 * No transport replacement, image load, source-body load, or cache invalidation prepares the test. */
final class LiveImagePrefetchProbe {
    private final Instrumentation test;private final Source source;
    private DiscoveryActivity discovery;private MainActivity reader;private DiscoveryController controller;
    private Repository repo;private ExecutorService storage;
    private final StringBuilder report=new StringBuilder();private int checks,failures;
    private int wifi=-1,mobile=-1;private boolean radiosChanged;
    LiveImagePrefetchProbe(Instrumentation test,String source){this.test=test;this.source=source==null?Source.THEPAPER:Source.valueOf(source);}
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier test){boolean[] result={false};ui(()->result[0]=test.getAsBoolean());return result[0];}
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);return out.toString(StandardCharsets.UTF_8.name());}}
    private void await(String stage,BooleanSupplier condition,long ms)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Public image prefetch deadline: "+stage);Thread.sleep(75);}}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private String js(WebView web,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(4,TimeUnit.SECONDS))throw new IllegalStateException("Reader DOM callback missing");return result[0];}
    @SuppressWarnings("unchecked") private Set<String> memoryKeys(){synchronized(ReaderImages.shared){return new HashSet<>(((Map<String,?>)field(ReaderImages.shared,"entries")).keySet());}}
    private String selector(String image){return "Array.from(document.querySelectorAll('.part img:not(.emoji)')).find(i=>i.src==="+JSONObject.quote(image)+")";}
    void run(){String database="test-ui-live-image-prefetch-"+UUID.randomUUID()+".db";
        try{
            if(!"1".equals(shell("getprop ro.kernel.qemu").trim()))throw new IllegalStateException("This network-switch diagnostic requires the project emulator");
            wifi=Settings.Global.getInt(test.getTargetContext().getContentResolver(),"wifi_on",-1);mobile=Settings.Global.getInt(test.getTargetContext().getContentResolver(),"mobile_data",-1);
            if((wifi!=0&&wifi!=1)||(mobile!=0&&mobile!=1))throw new IllegalStateException("Unknown initial radio state");
            List<Item> board=new ArrayList<>();CountDownLatch fetched=new CountDownLatch(1);String[] error={""};
            ui(()->{repo=new Repository(test.getTargetContext());repo.board(source,new Repository.Result<List<Item>>(){public void success(List<Item> items){board.addAll(items);fetched.countDown();}public void failure(String why){error[0]=why;fetched.countDown();}});});
            if(!fetched.await(30,TimeUnit.SECONDS)||board.isEmpty())throw new IllegalStateException("Current public board unavailable: "+error[0]);
            List<HotCandidate> candidates=new ArrayList<>();Set<String> warmBodies=new HashSet<>();long now=System.currentTimeMillis();
            for(int n=0;n<board.size()&&candidates.size()<12;n++){
                Item item=board.get(n);if(item.video||VideoPolicy.url(item.source,item.url)||!item.source.unifiedReading())continue;
                candidates.add(new HotCandidate(item.source,item.url,item.title,item.snippet,"",false,n+1,item.publishedAt,now));
                if(onUi(()->{Document doc=repo.cachedArticle(item);return doc!=null&&ArticleVersion.matches(item,doc);}))warmBodies.add(item.url);
            }
            if(candidates.isEmpty())throw new IllegalStateException("No public text candidates");
            File imageRoot=new File(test.getTargetContext().getCacheDir(),"reader-images-v1");Set<String> oldFiles=new HashSet<>();File[] files=imageRoot.listFiles();if(files!=null)for(File file:files)oldFiles.add(file.getName());Set<String> oldMemory=memoryKeys();
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source candidateSource:Source.values())store.enable(candidateSource,candidateSource==source);store.upsertSnapshot(source,store.beginRefresh(source),candidates,now);}
            long started=SystemClock.elapsedRealtime();discovery=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(discovery,"controller");storage=(ExecutorService)field(controller,"storage");
            ImageDiskCache disk=new ImageDiskCache(imageRoot);HotCandidate selected=null;String image="";byte[] acquired=null;long until=SystemClock.elapsedRealtime()+50000;
            Set<String> preparedBodies=new HashSet<>(),consideredImages=new HashSet<>();
            while(selected==null&&SystemClock.elapsedRealtime()<until){
                for(HotCandidate candidate:candidates){
                    Document[] body={null};ui(()->body[0]=repo.cachedArticle(candidate.readerItem()));Document doc=body[0];
                    if(doc==null||!ArticleVersion.matches(candidate.readerItem(),doc))continue;preparedBodies.add(candidate.originalUrl);
                    for(String url:ImagePreloader.select(doc)){
                        consideredImages.add(url);String key=doc.cachedNamespace+'\0'+url+'\0'+doc.url;
                        String file=HotCandidate.hash(url)+'-'+HotCandidate.hash(key)+".img";
                        if(oldFiles.contains(file)||oldMemory.contains(key))continue;
                        ImageDiskCache.Hit hit=disk.read(key,url,System.currentTimeMillis());
                        if(hit!=null){selected=candidate;image=url;acquired=hit.bytes;break;}
                    }
                    if(selected!=null)break;
                }
                if(selected==null)Thread.sleep(150);
            }
            report.append("source=").append(source).append(" candidates=").append(candidates.size()).append(" preparedBodies=").append(preparedBodies.size()).append(" eligibleImages=").append(consideredImages.size()).append('\n');
            if(selected==null)throw new IllegalStateException("No newly prefetched public image before any click; cannot claim a cache hit");
            HotCandidate target=selected;String targetImage=image;byte[] expected=acquired;
            check(true,"an actual current public hot article gains a new disk image before any card is opened, without injected acquisition");
            report.append("target=").append(target.originalUrl).append(" sourceRank=").append(target.sourceRank).append(" bodyInitiallyInMemory=").append(warmBodies.contains(target.originalUrl)).append(" imageBytes=").append(expected.length).append(" automaticImagePrepareMs=").append(SystemClock.elapsedRealtime()-started).append('\n');
            check(storage.submit(()->((FeedStore)field(controller,"store")).activeFeedback(System.currentTimeMillis()).length()==0).get(5,TimeUnit.SECONDS),"real body and image prefetch creates no interest event");
            ui(()->controller.suspendRecommendation("公开配图离线复用诊断"));
            radiosChanged=true;shell("svc wifi disable");shell("svc data disable");await("both radios offline",repo::offline,10000);
            ReaderImages offline=new ReaderImages(u->android.webkit.CookieManager.getInstance().getCookie(u),(url,ref,cookies)->{throw new IOException("Offline reader must reuse disk");},8192);offline.attach(test.getTargetContext());
            check(Arrays.equals(expected,offline.load(targetImage,target.originalUrl)),"with actual network disabled a new ReaderImages instance restores the same proactively acquired bytes");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false),login=test.addMonitor(LoginActivity.class.getName(),null,false);long clicked=SystemClock.elapsedRealtime();
            try{ui(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(discovery,target);}catch(Exception e){throw new IllegalStateException(e);}});Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Common reader missing");reader=(MainActivity)next;check(login.getHits()==0,"real card opens the unified reader without login/source browser while offline");}finally{test.removeMonitor(monitor);test.removeMonitor(login);}
            await("prepared text reader",()->field(reader,"readerWeb")!=null&&field(reader,"reading")!=null&&(boolean)field(field(reader,"engagement"),"ready"),10000);
            check(onUi(()->field(reader,"dynamic")==null&&ArticleVersion.matches(target.readerItem(),(Document)field(reader,"reading"))),"matching prepared body is displayed without a source WebView reload");
            report.append("offlineTextVisibleMs=").append(SystemClock.elapsedRealtime()-clicked).append('\n');Thread.sleep(700);
            WebView web=(WebView)field(reader,"readerWeb");String node=selector(targetImage);js(web,"(()=>{let i="+node+";if(!i)return false;i.scrollIntoView({block:'center'});return true})()");
            boolean decoded=false;long decodeUntil=SystemClock.elapsedRealtime()+10000;
            while(!decoded&&SystemClock.elapsedRealtime()<decodeUntil){decoded="true".equals(js(web,"(()=>{let i="+node+";return !!i&&i.complete&&i.naturalWidth>0})()"));if(!decoded)Thread.sleep(100);}
            check(decoded,"the actual public image decodes inside the production unified reader with both radios disabled");
            String metrics=js(web,"JSON.stringify((()=>{let i="+node+",r=i&&i.getBoundingClientRect();return {overflow:document.documentElement.scrollWidth>innerWidth+1,imageWidth:i&&i.naturalWidth,visible:!!r&&r.bottom>0&&r.top<innerHeight,players:document.querySelectorAll('video,iframe').length}})())");
            report.append("offlineReaderMetrics=").append(metrics).append('\n');check(metrics.contains("\\\"overflow\\\":false")&&metrics.contains("\\\"visible\\\":true"),"offline public image is in the viewport and the unified document has no horizontal overflow");
            CountDownLatch painted=new CountDownLatch(1);ui(()->web.postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){painted.countDown();}}));if(!painted.await(4,TimeUnit.SECONDS))throw new IllegalStateException("Visible frame missing");Thread.sleep(250);
            android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot==null)throw new IllegalStateException("Screenshot unavailable");
            File folder=new File(test.getTargetContext().getExternalFilesDir(null),"live-image-prefetch");folder.mkdirs();File saved=new File(folder,source.name()+"-"+System.currentTimeMillis()+".png");try(FileOutputStream out=new FileOutputStream(saved)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}report.append("screenshot=").append(saved).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{
            ui(()->{if(reader!=null)reader.finish();if(discovery!=null)discovery.finish();if(repo!=null)repo.close();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            if(radiosChanged)try{shell("svc wifi "+(wifi==1?"enable":"disable"));shell("svc data "+(mobile==1?"enable":"disable"));report.append("PASS original radio settings restored\n");}catch(Exception error){failures++;report.append("ERROR radio restoration failed\n");}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
