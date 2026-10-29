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

/** Actual controller/lifecycle scheduling; controlled body transport, never live source data. */
final class PrefetchLifecycleProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private DiscoveryController controller;
    private final List<ExecutorService> workers=new ArrayList<>();private final List<Fetch> calls=new ArrayList<>();
    private int checks,failures,cancellations;private final StringBuilder report=new StringBuilder();
    private static final class Fetch {final Item item;final Repository.Result<Document> result;Fetch(Item i,Repository.Result<Document> r){item=i;result=r;}}
    PrefetchLifecycleProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end){ui(()->report.append("SCHEDULER ready=").append(field(controller,"ready")).append(" foreground=").append(field(controller,"foreground")).append(" page=").append(field(controller,"feedPage")).append(" interacting=").append(field(controller,"interacting")).append(" remaining=").append(((ActionPrefetchBudget)field(controller,"prefetchBudget")).remaining(SystemClock.elapsedRealtime())).append(" readerPending=").append(ReaderStartup.pending()).append(" readerFailed=").append(ReaderStartup.failed()).append(" busy=").append(((ArticlePreloader)field(controller,"preloader")).busy()).append(" restriction=").append(app.quietreader.ai.RecommendationPolicy.restriction(activity)).append(" phase=").append(((RecommendationState)field(controller,"recommendationState")).detail).append('\n'));throw new IllegalStateException("Prefetch deadline: "+stage);}Thread.sleep(30);}}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}}}
    private void inject(){
        controller=(DiscoveryController)field(activity,"controller");workers.add((ExecutorService)field(controller,"storage"));
        ui(()->{try{((ArticlePreloader)field(controller,"preloader")).close();ArticlePreloader controlled=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> result){calls.add(new Fetch(item,result));}public void cancel(){cancellations++;}},40,()->{},true);Field f=DiscoveryController.class.getDeclaredField("preloader");f.setAccessible(true);f.set(controller,controlled);}catch(Exception e){throw new IllegalStateException(e);}});
    }
    private void scrollTo(int first){controller.interacting(true);controller.viewport(first);controller.interacting(false);}
    private Document body(Fetch fetch){Document doc=new Document();doc.url=fetch.item.url;doc.title=fetch.item.title;doc.blocks.add(new Block("text","这是受控预读正文，不代表真实平台内容或用户偏好。".repeat(20)));ArticleVersion.capture(fetch.item,doc);return doc;}
    void run(){String database="test-ui-prefetch-"+UUID.randomUUID()+".db";Repository[] cache={null};
        try{
            long now=System.currentTimeMillis();long fixture=now;List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<70;n++)candidates.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(fixture+n),"中文文字预读生命周期样本第"+n+"项","","",false,n+1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())store.enable(source,source==Source.THEPAPER);store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database));inject();ui(()->cache[0]=new Repository(activity));
            await("initial body acquisition",()->!calls.isEmpty());Fetch first=calls.get(0);
            @SuppressWarnings("unchecked") List<FeedStore.Row> displayed=(List<FeedStore.Row>)field(controller,"rows");
            int far=displayed.size()-1;if(far<3)throw new IllegalStateException("Finite batch needs four distinct rows for viewport cancellation");String farUrl=displayed.get(far).candidate.originalUrl;
            check(onUi(()->(boolean)field(controller,"autoRefresh")),"initial foreground enables automatic prefetch");
            ui(()->first.result.success(body(first)));await("prepared cache",()->cache[0].cachedArticle(first.item)!=null);
            check(onUi(()->ArticleVersion.matches(first.item,cache[0].cachedArticle(first.item))),"viewport scheduling prepares a matching shared body before any reader opens");
            await("next acquisition",()->calls.size()>=2);Fetch old=calls.get(1);int previous=calls.size(),beforeCancel=cancellations;
            ui(()->scrollTo(far));await("shifted viewport acquisition",()->calls.size()>previous);Fetch shifted=calls.get(calls.size()-1);
            check(cancellations>beforeCancel&&shifted.item.url.equals(farUrl),"moving to the last displayed item replaces old work with the finite batch viewport");
            ui(()->old.result.success(body(old)));check(onUi(()->cache[0].cachedArticle(old.item)==null),"late completion from the abandoned viewport cannot populate cache");
            int beforeReturn=calls.size(),returnCancellations=cancellations;
            ui(()->scrollTo(0));await("return to canceled article",()->calls.size()>beforeReturn);Fetch retried=calls.get(calls.size()-1);
            check(retried.item.url.equals(old.item.url)&&cancellations>returnCancellations,"quick return immediately retries canceled work instead of imposing the failure cooldown");
            check(onUi(()->cache[0].cachedArticle(first.item)!=null&&calls.stream().filter(fetch->fetch.item.url.equals(first.item.url)).count()==1),"returning window reuses its completed body without a second acquisition");
            ui(()->shifted.result.success(body(shifted)));check(onUi(()->cache[0].cachedArticle(shifted.item)==null),"abandoned far-window response cannot cache after returning nearby");
            ui(()->old.result.success(body(old)));check(onUi(()->cache[0].cachedArticle(old.item)==null),"old same-URL callback cannot fulfill the newer retry");
            ui(()->retried.result.success(body(retried)));await("retried body cached",()->cache[0].cachedArticle(retried.item)!=null);
            check(onUi(()->ArticleVersion.matches(retried.item,cache[0].cachedArticle(retried.item))),"current retry publishes its matching body normally");
            await("work before memory pressure",()->((ArticlePreloader)field(controller,"preloader")).busy());
            Fetch trimmed=calls.get(calls.size()-1);int trimCount=calls.size(),trimCancels=cancellations;long trimmedAt=SystemClock.elapsedRealtime();
            ui(()->activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
            check(onUi(()->!((ActionPrefetchBudget)field(controller,"prefetchBudget")).active(SystemClock.elapsedRealtime())&&!((ArticlePreloader)field(controller,"preloader")).busy()&&cancellations>trimCancels),"real Activity memory trim cancels pending speculation and clears its action lease");
            check(onUi(()->app.quietreader.ai.RecommendationPolicy.restriction(activity.getApplicationContext()).contains("内存恢复")),"memory recovery admission is shared by other foreground pipelines in the App process");
            for(int n=0;n<8;n++){ui(()->controller.viewport((int)field(controller,"viewport")));Thread.sleep(250);}
            check(onUi(()->calls.size()==trimCount),"passive layout callbacks cannot restart prefetch immediately after memory trim");
            ui(()->{scrollTo(far);controller.requestRecommendation();});Thread.sleep(1100);
            check(onUi(()->calls.size()==trimCount&&field(controller,"recommendation")==null),"new scroll and explicit model request respect the memory recovery interval");
            ui(()->trimmed.result.success(body(trimmed)));
            check(onUi(()->cache[0].cachedArticle(trimmed.item)==null),"late response from memory-trimmed work cannot populate shared cache");
            while(SystemClock.elapsedRealtime()<trimmedAt+app.quietreader.ai.RecommendationPolicy.MEMORY_RECOVERY_MS+500)Thread.sleep(250);
            check(onUi(()->calls.size()==trimCount&&!((ArticlePreloader)field(controller,"preloader")).busy()&&field(controller,"recommendation")==null),"memory recovery has no timer that restarts speculation or models while idle");
            ui(()->activity.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN));
            check(onUi(()->app.quietreader.ai.RecommendationPolicy.restriction(activity).isEmpty()),"UI-hidden notification alone is not treated as new memory pressure after recovery");
            ui(()->scrollTo(0));await("fresh action after memory recovery",()->calls.size()>trimCount);
            check(true,"a new action after memory recovery can resume prefetch when system restrictions are clear");
            int beforeFarAgain=calls.size();ui(()->scrollTo(far));await("far window retried",()->calls.size()>beforeFarAgain);Fetch recreatedWork=calls.get(calls.size()-1);
            check(recreatedWork.item.url.equals(shifted.item.url),"far window can also retry immediately after cancellation");
            int beforeRecreate=calls.size();Instrumentation.ActivityMonitor monitor=test.addMonitor(DiscoveryActivity.class.getName(),null,false);
            try{ui(activity::recreate);Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof DiscoveryActivity))throw new IllegalStateException("Recreated discovery missing");activity=(DiscoveryActivity)next;inject();}finally{test.removeMonitor(monitor);}
            await("recreated controller ready",()->(boolean)field(controller,"ready")&&(boolean)field(controller,"foreground"));
            boolean enabled=onUi(()->(boolean)field(controller,"autoRefresh"));check(enabled,"saved-state recreation preserves automatic caching permission");
            if(enabled){await("recreated automatic acquisition",()->calls.size()>beforeRecreate);check(true,"recreated page resumes viewport prefetch without manual refresh");}
            else check(false,"recreated page resumes viewport prefetch without manual refresh");
            ui(()->recreatedWork.result.success(body(recreatedWork)));check(onUi(()->cache[0].cachedArticle(recreatedWork.item)==null),"destroyed controller cannot publish its late body into the replacement page");
            Fetch beforeHome=calls.get(calls.size()-1);int homeCount=calls.size();shell("input keyevent 3");await("actual background",()->!(boolean)field(controller,"foreground"));
            ui(()->beforeHome.result.success(body(beforeHome)));check(onUi(()->cache[0].cachedArticle(beforeHome.item)==null&&!((ArticlePreloader)field(controller,"preloader")).busy()),"HOME cancels speculative work and rejects its late body");
            shell("am start -n "+test.getTargetContext().getPackageName()+"/app.quietreader.DiscoveryActivity -f 0x10020000");await("foreground automatic restart",()->(boolean)field(controller,"foreground")&&calls.size()>homeCount);
            check(true,"returning from HOME resumes viewport prefetch without manual refresh");
            Fetch beforeSleep=calls.get(calls.size()-1);int sleepCount=calls.size();
            shell("input keyevent 223");
            await("actual screen off",()->!((PowerManager)activity.getSystemService(android.content.Context.POWER_SERVICE)).isInteractive()&&!(boolean)field(controller,"foreground"));
            check(onUi(()->!((ActionPrefetchBudget)field(controller,"prefetchBudget")).active(SystemClock.elapsedRealtime())&&!((ArticlePreloader)field(controller,"preloader")).busy()),"screen off clears the action lease and cancels the real speculative loader");
            ui(()->beforeSleep.result.success(body(beforeSleep)));
            check(onUi(()->cache[0].cachedArticle(beforeSleep.item)==null),"screen-off late body cannot publish into the shared cache");
            for(int n=0;n<8;n++){ui(()->controller.viewport((int)field(controller,"viewport")));Thread.sleep(250);}
            check(onUi(()->calls.size()==sleepCount&&!((ArticlePreloader)field(controller,"preloader")).busy()),"screen-off layout callbacks do not restart requests");
            shell("input keyevent 224");shell("wm dismiss-keyguard");
            await("screen-on foreground restart",()->((PowerManager)activity.getSystemService(android.content.Context.POWER_SERVICE)).isInteractive()&&(boolean)field(controller,"foreground")&&calls.size()>sleepCount);
            check(true,"waking the unsecured test emulator resumes foreground prefetch without manual refresh");
            Fetch idleWork=calls.get(calls.size()-1);int idleCount=calls.size(),idleCancels=cancellations;
            long deadline=SystemClock.elapsedRealtime()+((ActionPrefetchBudget)field(controller,"prefetchBudget")).remaining(SystemClock.elapsedRealtime())+1500;
            while(SystemClock.elapsedRealtime()<deadline){
                // Exercise the actual controller path used by layout and automatic callbacks.
                ui(()->controller.viewport((int)field(controller,"viewport")));Thread.sleep(250);
            }
            check(onUi(()->!((ActionPrefetchBudget)field(controller,"prefetchBudget")).active(SystemClock.elapsedRealtime())&&!((ArticlePreloader)field(controller,"preloader")).busy()),"passive viewport callbacks cannot renew the action lease; expiry cancels real pending work");
            check(onUi(()->calls.size()==idleCount&&cancellations>idleCancels),"no speculative request starts after idle expiry despite repeated layout callbacks");
            ui(()->idleWork.result.success(body(idleWork)));
            check(onUi(()->cache[0].cachedArticle(idleWork.item)==null),"expired speculative response cannot publish into shared cache");
            ui(()->scrollTo(far));await("fresh action renews idle budget",()->calls.size()>idleCount);
            check(true,"a new scroll renews the budget and resumes acquisition after idle expiry");

            ExecutorService worker=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");check(worker.submit(()->store.sourceState(Source.THEPAPER).getLong("requested")==1).get(5,TimeUnit.SECONDS),"recreation and foreground return do not refresh a still-fresh source");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(activity!=null)activity.finish();if(cache[0]!=null)cache[0].close();});test.waitForIdleSync();for(ExecutorService worker:workers)try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
