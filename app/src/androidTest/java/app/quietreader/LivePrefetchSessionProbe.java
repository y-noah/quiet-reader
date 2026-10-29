package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import static app.quietreader.Models.*;

/** Current public board and unmodified controller/preloader/network, no synthetic bodies.
 * Models are explicitly paused through the product controller before each action.
 * This bounded session does not establish days-long churn, ARM peaks or energy. */
final class LivePrefetchSessionProbe {
    private final Instrumentation test;
    private final boolean warmAllowed;
    private final boolean multiSource;
    private final boolean longSession;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private Repository repo;
    private ExecutorService storage;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("LIVE PREFETCH SESSION; real public supply/bodies; no injected transport/loader; models paused\n");
    private final Map<String,Stamp> acquired=new LinkedHashMap<>();
    private final Set<String> initiallyCold=new HashSet<>();
    private final List<HotCandidate> candidates=new ArrayList<>();
    private long started;
    private static final class Stamp {long at,inode;String fingerprint;Stamp(long at,long inode,String fingerprint){this.at=at;this.inode=inode;this.fingerprint=fingerprint;}}
    LivePrefetchSessionProbe(Instrumentation test){this(test,false);}
    LivePrefetchSessionProbe(Instrumentation test,boolean warmAllowed){this(test,warmAllowed,false);}
    LivePrefetchSessionProbe(Instrumentation test,boolean warmAllowed,boolean multiSource){this(test,warmAllowed,multiSource,false);}
    LivePrefetchSessionProbe(Instrumentation test,boolean warmAllowed,boolean multiSource,boolean longSession){this.test=test;this.warmAllowed=warmAllowed;this.multiSource=multiSource;this.longSession=longSession;}
    private Object field(Object owner,String name){try{Class<?> type=owner instanceof Class?(Class<?>)owner:owner.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier predicate){boolean[] value={false};ui(()->value[0]=predicate.getAsBoolean());return value[0];}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)throw new IllegalStateException(label);}
    private void await(String label,BooleanSupplier predicate,long ms)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!onUi(predicate)){if(SystemClock.elapsedRealtime()>end)throw new IOException("Deadline: "+label);Thread.sleep(80);}}
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    private void drainWrites()throws Exception{((ExecutorService)field(ArticleDiskCache.class,"writer")).submit(()->{}).get(10,TimeUnit.SECONDS);}
    private File bodyFile(Item item){String[] identity={null};ui(()->{ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());identity[0]=HotCandidate.hash(disk.namespace()+"\n"+ArticleDiskCache.session(item.url)+"\n"+item.url);});return new File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+identity[0]+".json");}
    private Document cached(HotCandidate item){Document[] body={null};ui(()->body[0]=repo.cachedArticle(item.readerItem()));return body[0];}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(controller,"rows");}
    private void action(int first){ui(()->{controller.suspendRecommendation("真实网络预读诊断");controller.scrollState(1);controller.scrollViewport(first,5,0);controller.scrollState(0);});}
    private void refresh(List<Source> sources)throws Exception{
        long before=storage.submit(()->((FeedStore)field(controller,"store")).version("sources")).get(5,TimeUnit.SECONDS);
        ui(()->{controller.suspendRecommendation("真实网络来源刷新诊断");controller.refresh(sources);});
        await("real board refresh finishes",()->!(boolean)field(controller,"loading"),60000);
        long after=storage.submit(()->((FeedStore)field(controller,"store")).version("sources")).get(5,TimeUnit.SECONDS);
        check(after>before,"explicit refresh commits a new real public source snapshot");
        await("refreshed source version displayed",()->{if((long)field(controller,"pending")>0)controller.applyPending(false);return (long)field(controller,"visibleSourceVersion")>=after;},10000);
        for(Source source:sources){String status=storage.submit(()->((FeedStore)field(controller,"store")).sourceState(source).optString("status")).get(5,TimeUnit.SECONDS);report.append("REFRESH source=").append(source).append(" status=").append(status).append('\n');}
    }
    private void sample()throws Exception{
        for(HotCandidate item:candidates){if(!warmAllowed&&!initiallyCold.contains(item.originalUrl)||acquired.containsKey(item.originalUrl))continue;
            Document body=cached(item);if(body==null||!body.hasContent()||body.loginRequired||body.sourceUnavailable||!ArticleVersion.matches(item.readerItem(),body)||!warmAllowed&&body.cachedAt<started)continue;
            File file=bodyFile(item.readerItem());if(!file.isFile())continue;
            JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            if(saved.optLong("at")!=body.cachedAt)continue;
            Document stored=ReadingHistory.decode(saved.getJSONObject("document"));
            if(!DocumentFingerprint.of(body).equals(DocumentFingerprint.of(stored)))continue;
            acquired.put(item.originalUrl,new Stamp(body.cachedAt,android.system.Os.stat(file.getPath()).st_ino,DocumentFingerprint.of(stored)));
        }
    }
    private Map<String,String> inventory()throws Exception{
        Map<String,String> result=new TreeMap<>();File root=new File(test.getTargetContext().getCacheDir(),"article-bodies-v1");File[] files=root.listFiles();if(files!=null)for(File file:files)if(file.isFile())result.put(file.getName(),file.length()+":"+file.lastModified()+":"+android.system.Os.stat(file.getPath()).st_ino);return result;
    }
    void run(){String database="test-ui-live-session-"+UUID.randomUUID()+".db";try{
        Context context=test.getTargetContext();check(context.getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu")&&(context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0,"isolated debug emulator only");
        List<Source> sources=multiSource?Arrays.asList(Source.ITHOME,Source.THEPAPER,Source.BBC):Collections.singletonList(Source.ITHOME);
        ui(()->repo=new Repository(context));long now=System.currentTimeMillis();
        for(Source source:sources){
            List<Item> board=new ArrayList<>();String[] error={""};CountDownLatch done=new CountDownLatch(1);
            ui(()->repo.board(source,new Repository.Result<List<Item>>(){public void success(List<Item> items){board.addAll(items);done.countDown();}public void failure(String why){error[0]=why;done.countDown();}}));
            check(done.await(30,TimeUnit.SECONDS)&&board.size()>=8,"real public board has at least eight candidates: "+source+" "+error[0]);
            int added=0;for(int n=0;n<board.size()&&added<30;n++){Item item=board.get(n);if(item.video||!UrlPolicy.belongs(source,item.url)||source==Source.ITHOME&&!IthomeSource.articleUrl(item.url))continue;HotCandidate candidate=new HotCandidate(item.source,item.url,item.title,item.snippet,"",false,n+1,item.publishedAt,now);candidates.add(candidate);added++;if(cached(candidate)==null&&!bodyFile(candidate.readerItem()).exists())initiallyCold.add(candidate.originalUrl);}
        }
        check(warmAllowed?candidates.size()>=8:initiallyCold.size()>=8,warmAllowed?"warm replay explicitly permits existing disk bodies; no cold-acquisition claim":"at least eight actual targets begin without memory or disk bodies; no cache clearing used");
        if(warmAllowed)ui(repo::releaseArticleMemory);
        try(FeedStore store=new FeedStore(context,database)){for(Source source:Source.values())store.enable(source,sources.contains(source));for(Source source:sources){List<HotCandidate> subset=new ArrayList<>();for(HotCandidate candidate:candidates)if(candidate.source==source)subset.add(candidate);store.upsertSnapshot(source,store.beginRefresh(source),subset,now);}}
        started=System.currentTimeMillis();long sessionStart=SystemClock.elapsedRealtime();
        activity=(DiscoveryActivity)test.startActivitySync(new Intent(context,DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        controller=(DiscoveryController)field(activity,"controller");storage=(ExecutorService)field(controller,"storage");
        await("ordinary controller ready",()->(boolean)field(controller,"ready")&&(boolean)field(controller,"foreground")&&rows().size()>=8,15000);
        check(onUi(()->(boolean)field(controller,"autoRefresh")&&field(field(controller,"preloader"),"loader")==null),"automatic caching uses the ordinary controller and native loader");
        // Explicit action windows include reversals and distant items. Each is bounded;
        // automatic callbacks alone never extend its lease.
        for(int round=0;round<(longSession?72:multiSource?30:10);round++){
            int first=(round%2==0)?Math.min(candidates.size()-1,round*3):Math.max(0,candidates.size()-1-round*2);
            if(multiSource){int[] selected={0};int step=round;ui(()->{Source desired=sources.get(step%sources.size());List<Integer> positions=new ArrayList<>();for(int n=0;n<rows().size();n++)if(rows().get(n).candidate.source==desired)positions.add(n);if(!positions.isEmpty())selected[0]=positions.get((step/3*5)%positions.size());});first=selected[0];}
            action(first);
            long end=SystemClock.elapsedRealtime()+(multiSource?10000:6000);while(SystemClock.elapsedRealtime()<end){sample();Thread.sleep(200);}
            report.append("WINDOW round=").append(round).append(" first=").append(first).append(" preparedDiskBodies=").append(acquired.size()).append('\n');
            if(multiSource){if(!longSession||round%6==5){Bundle progress=new Bundle();progress.putString("stream","WINDOW round="+round+" prepared="+acquired.size()+" elapsedMs="+(SystemClock.elapsedRealtime()-sessionStart)+"\n");test.sendStatus(1,progress);}if(longSession?(round==19||round==39||round==59):(round==9||round==19))refresh(sources);}
        }
        drainWrites();sample();check(acquired.size()>=6,warmAllowed?"at least six public articles are proactively restored/prepared with matching real disk bodies":"at least six initially cold public articles are proactively acquired and match actual disk bodies");
        check(candidates.stream().skip(3).anyMatch(c->acquired.containsKey(c.originalUrl)),"actual acquired topics extend beyond the first three candidates");
        if(multiSource)for(Source source:sources){long count=candidates.stream().filter(c->c.source==source&&acquired.containsKey(c.originalUrl)).count();check(count>=2,"at least two actual matching prepared bodies from "+source+": "+count);}
        refresh(sources);
        // Make a prepared body cold in RAM, then return through the normal preloader.
        HotCandidate target=candidates.stream().filter(c->acquired.containsKey(c.originalUrl)).findFirst().orElseThrow();Stamp original=acquired.get(target.originalUrl);
        int[] index={-1};ui(()->{for(int n=0;n<rows().size();n++)if(ArticleVersion.sameRequest(rows().get(n).candidate.readerItem(),target.readerItem())){index[0]=n;break;}});
        check(index[0]>=0,"refreshed feed retains one exact prepared candidate version");
        if(longSession){
            check(System.currentTimeMillis()-original.at>10*60_000,"actual wall-clock time crosses the original body's online freshness; no timestamp rewrite");action(index[0]);
            await("fresh real body after natural expiry",()->{Document body=repo.cachedArticle(target.readerItem());return body!=null&&body.cachedAt>original.at&&ArticleVersion.matches(target.readerItem(),body);},30000);drainWrites();
            Document renewed=cached(target);File renewedFile=bodyFile(target.readerItem());JSONObject renewedRecord=new JSONObject(new String(java.nio.file.Files.readAllBytes(renewedFile.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            check(renewedRecord.getLong("at")==renewed.cachedAt&&DocumentFingerprint.of(ReadingHistory.decode(renewedRecord.getJSONObject("document"))).equals(DocumentFingerprint.of(renewed)),"naturally expired article is prepared again with a newer matching persisted body");
            acquired.put(target.originalUrl,new Stamp(renewed.cachedAt,android.system.Os.stat(renewedFile.getPath()).st_ino,DocumentFingerprint.of(renewed)));
        }
        Stamp reusable=acquired.get(target.originalUrl);
        ui(()->{controller.suspendRecommendation("真实正文磁盘复用诊断");repo.releaseArticleMemory();});action(index[0]);
        await("ordinary disk restoration",()->{Document body=repo.cachedArticle(target.readerItem());return body!=null&&ArticleVersion.matches(target.readerItem(),body);},15000);drainWrites();
        Document restored=cached(target);File retained=bodyFile(target.readerItem());
        check(restored.cachedAt==reusable.at&&DocumentFingerprint.of(restored).equals(reusable.fingerprint)&&android.system.Os.stat(retained.getPath()).st_ino==reusable.inode,"return after RAM eviction reuses exact disk body without renewing acquisition time or replacing its file");
        // Stop via actual Android HOME; allow pre-existing writer work to finish first.
        shell("input keyevent 3");await("actual background",()->!(boolean)field(controller,"foreground"),5000);drainWrites();
        Map<String,String> stopped=inventory();long writerCount=((ThreadPoolExecutor)field(ArticleDiskCache.class,"writer")).getCompletedTaskCount();
        for(int n=0;n<10;n++){ui(()->controller.viewport((int)field(controller,"viewport")));Thread.sleep(500);}
        check(onUi(()->!((ArticlePreloader)field(controller,"preloader")).busy()&&!((ActionPrefetchBudget)field(controller,"prefetchBudget")).active(SystemClock.elapsedRealtime())),"background layout callbacks leave speculation and action credit stopped");
        check(stopped.equals(inventory())&&writerCount==((ThreadPoolExecutor)field(ArticleDiskCache.class,"writer")).getCompletedTaskCount(),"no new body file or queued write is created during the background observation");
        long feedback=storage.submit(()->((FeedStore)field(controller,"store")).activeFeedback(System.currentTimeMillis()).length()).get(5,TimeUnit.SECONDS);
        check(feedback==0,"real source/body prefetch and refresh create no personal interest event");
        report.append("SESSION elapsedMs=").append(SystemClock.elapsedRealtime()-sessionStart).append(" candidates=").append(candidates.size()).append(" initiallyCold=").append(initiallyCold.size()).append(" prepared=").append(acquired.size()).append(" warmAllowed=").append(warmAllowed).append("; public requests, no source account proof/ARM energy claim\n");
    }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
    finally{ui(()->{if(activity!=null)activity.finish();if(repo!=null){repo.releaseArticleMemory();repo.close();}});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}try{drainWrites();}catch(Exception error){failures++;report.append("ERROR writer cleanup\n");}test.getTargetContext().deleteDatabase(database);}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
