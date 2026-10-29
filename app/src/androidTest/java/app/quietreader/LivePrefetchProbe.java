package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Public live board -> uncached text -> real viewport prefetch -> production card click. */
final class LivePrefetchProbe {
    private final Instrumentation test;private final Source source;private DiscoveryActivity discovery;private MainActivity reader;private Repository repo;private ExecutorService worker;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    LivePrefetchProbe(Instrumentation test){this(test,null);}
    private final String target;
    LivePrefetchProbe(Instrumentation test,String requested){this(test,requested,null);}
    LivePrefetchProbe(Instrumentation test,String requested,String target){this.test=test;this.source=requested==null?Source.THEPAPER:Source.valueOf(requested);this.target=target;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Live prefetch deadline: "+label);Thread.sleep(50);}}
    private ThreadPoolExecutor writer()throws Exception{Field f=ArticleDiskCache.class.getDeclaredField("writer");f.setAccessible(true);return (ThreadPoolExecutor)f.get(null);}
    private void drainWrites()throws Exception{writer().submit(()->{}).get(10,TimeUnit.SECONDS);}
    void run(){String database="test-ui-live-prefetch-"+UUID.randomUUID()+".db";
        try{
            List<Item> rows=new ArrayList<>();CountDownLatch board=new CountDownLatch(1);String[] error={""};
            ui(()->{repo=new Repository(test.getTargetContext());repo.board(source,new Repository.Result<List<Item>>(){public void success(List<Item> items){rows.addAll(items);board.countDown();}public void failure(String why){error[0]=why;board.countDown();}});});
            if(!board.await(30,TimeUnit.SECONDS)||rows.isEmpty())throw new IllegalStateException("Live board unavailable: "+error[0]);
            Item selected=null;int rank=0;
            for(int n=0;n<rows.size();n++){
                Item item=rows.get(n);if(item.video||target!=null&&!target.equals(item.url))continue;CountDownLatch read=new CountDownLatch(1);Document[] existing={null};
                ui(()->repo.restoreArticle(item,doc->{existing[0]=doc;read.countDown();}));if(!read.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Cache inspection timeout");
                if(existing[0]==null||!ArticleVersion.matches(item,existing[0])){selected=item;rank=n+1;break;}
            }
            if(selected==null)throw new IllegalStateException("All current text bodies are already cached; cannot prove new acquisition without deleting data");
            Item item=selected;HotCandidate candidate=new HotCandidate(item.source,item.url,item.title,item.snippet,"",false,rank,item.publishedAt,System.currentTimeMillis());
            check(onUi(()->{Document doc=repo.cachedArticle(item);return doc==null||!ArticleVersion.matches(item,doc);}),"chosen current hot article has no matching body before discovery opens");
            report.append("target=").append(item.url).append(" sourceRank=").append(rank).append('\n');
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source candidateSource:Source.values())seed.enable(candidateSource,candidateSource==source);seed.upsertSnapshot(source,seed.beginRefresh(source),List.of(candidate),System.currentTimeMillis());}
            drainWrites();long writesBefore=writer().getCompletedTaskCount();
            long acquireAfter=System.currentTimeMillis(),begin=SystemClock.elapsedRealtime();discovery=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryController controller=(DiscoveryController)field(discovery,"controller");worker=(ExecutorService)field(controller,"storage");
            await("automatic prepared body",()->{Document doc=repo.cachedArticle(item);return doc!=null&&doc.hasContent()&&ArticleVersion.matches(item,doc);},45000);
            report.append("automaticPrepareMs=").append(SystemClock.elapsedRealtime()-begin).append('\n');
            check(true,"real foreground viewport fetches matching public article before any click, without injected loader");
            String[] ns={null},session={null};ui(()->{ns[0]=new ArticleDiskCache(test.getTargetContext()).namespace();session[0]=ArticleDiskCache.session(item.url);});
            ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());ArticleDiskCache.Hit hit=null;long diskDeadline=SystemClock.elapsedRealtime()+8000;
            while(SystemClock.elapsedRealtime()<diskDeadline){hit=disk.read(item.url,ns[0],session[0],false,System.currentTimeMillis());if(hit!=null&&ArticleVersion.matches(item,hit.document))break;Thread.sleep(50);}
            if(hit==null||!ArticleVersion.matches(item,hit.document))throw new IllegalStateException("Actual prefetch did not persist matching body");
            long firstInode=android.system.Os.stat(new java.io.File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(ns[0]+"\n"+session[0]+"\n"+item.url)+".json").getPath()).st_ino;
            test.waitForIdleSync();drainWrites();long acquisitionWrites=writer().getCompletedTaskCount()-writesBefore-1;
            check(acquisitionWrites==1,"one native acquisition schedules exactly one body write (actual="+acquisitionWrites+")");
            long acquiredAt=hit.at;check(acquiredAt>=acquireAfter,"matching disk body was acquired in this live discovery run");
            java.io.File bodyFile=new java.io.File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(ns[0]+"\n"+session[0]+"\n"+item.url)+".json");long inode=android.system.Os.stat(bodyFile.getPath()).st_ino;
            report.append("acquisitionFirstInode=").append(firstInode).append(" settledInode=").append(inode).append(" writerTasks=").append(acquisitionWrites).append('\n');
            CountDownLatch restored=new CountDownLatch(1);Document[] diskBody={null};
            ui(()->{repo.releaseArticleMemory();repo.close();repo=new Repository(test.getTargetContext());repo.restoreArticle(item,doc->{diskBody[0]=doc;restored.countDown();});});
            if(!restored.await(8,TimeUnit.SECONDS))throw new IllegalStateException("New repository disk restoration timeout");
            check(diskBody[0]!=null&&diskBody[0].hasContent()&&ArticleVersion.matches(item,diskBody[0])&&diskBody[0].cachedAt==acquiredAt,"after shared-memory eviction a new Repository restores exact version/session-bound disk body");
            report.append("diskRestored source=").append(source).append(" acquiredAt=").append(acquiredAt).append(" sections=").append(diskBody[0]==null?0:diskBody[0].sections.size()).append(" blocks=").append(diskBody[0]==null?0:diskBody[0].blocks.size()).append("; no Cookie values exported\n");
            FeedStore store=(FeedStore)field(controller,"store");check(worker.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==0).get(5,TimeUnit.SECONDS),"speculative body acquisition creates no reading or platform interest event");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false),login=test.addMonitor(LoginActivity.class.getName(),null,false);
            long click=SystemClock.elapsedRealtime();
            try{ui(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(discovery,candidate);}catch(Exception e){throw new IllegalStateException(e);}});Activity opened=test.waitForMonitorWithTimeout(monitor,6000);if(!(opened instanceof MainActivity))throw new IllegalStateException("Common reader missing");reader=(MainActivity)opened;check(login.getHits()==0,"production card opens the unified reader without source browser/login");}finally{test.removeMonitor(monitor);test.removeMonitor(login);}
            await("cached reader body",()->{Document doc=(Document)field(reader,"reading");return field(reader,"readerWeb")!=null&&doc!=null&&doc.hasContent()&&ArticleVersion.matches(item,doc)&&(boolean)field(field(reader,"engagement"),"ready");},8000);
            report.append("cachedReaderVisibleMs=").append(SystemClock.elapsedRealtime()-click).append("; reading-ready visual text, not all images completed\n");
            check(onUi(()->field(reader,"dynamic")==null),"prepared body reaches real reader without a source WebView reload");
            if(target!=null)check(onUi(()->((Document)field(reader,"reading")).blocks.stream().flatMap(b->b.inlineLinks.stream()).anyMatch(link->link.url.equals("https://public.flourish.studio/visualisation/30709667"))),"previously unsupported real chart survives proactive cache and actual reader opening");
            check(onUi(()->((Document)field(reader,"reading")).cachedAt==acquiredAt),"unified reader uses the already acquired body rather than stamping a new fetch");
            drainWrites();
            check(bodyFile.isFile()&&android.system.Os.stat(bodyFile.getPath()).st_ino==inode,"disk restore and real reader preserve the acquired body file without rewriting");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(discovery!=null)discovery.finish();if(repo!=null)repo.close();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
