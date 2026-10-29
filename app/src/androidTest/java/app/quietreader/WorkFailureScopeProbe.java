package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.json.JSONObject;
import static app.quietreader.Models.*;

/** Real storage queue/controller, controlled failure timing; no source or model requests. */
final class WorkFailureScopeProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;private DiscoveryController controller;
    private final List<ExecutorService> workers=new ArrayList<>();
    private final List<CountDownLatch> gates=new ArrayList<>();
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final List<String> errors=new ArrayList<>();private final List<Runnable> draws=new ArrayList<>();
    private int feeds,successes;private boolean refreshing;
    WorkFailureScopeProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field value=owner.getClass().getDeclaredField(name);value.setAccessible(true);return value.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier task){boolean[] value={false};ui(()->value[0]=task.getAsBoolean());return value[0];}
    private boolean await(BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+5000;do{if(onUi(condition))return true;Thread.sleep(20);}while(SystemClock.elapsedRealtime()<end);return onUi(condition);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void work(Callable<Object> task){work(task,null);}
    private void work(Callable<Object> task,BooleanSupplier current){ui(()->{try{Method method=current==null?DiscoveryController.class.getDeclaredMethod("work",Callable.class,Consumer.class):DiscoveryController.class.getDeclaredMethod("work",Callable.class,Consumer.class,BooleanSupplier.class);method.setAccessible(true);if(current==null)method.invoke(controller,task,(Consumer<Object>)ignored->successes++);else method.invoke(controller,task,(Consumer<Object>)ignored->successes++,current);}catch(Exception e){throw new IllegalStateException(e);}});}
    private CountDownLatch blockedFailure()throws Exception{
        return blockedFailure(null);
    }
    private CountDownLatch blockedFailure(BooleanSupplier current)throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);gates.add(release);
        work(()->{entered.countDown();if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Controlled gate timed out");throw new java.io.IOException("Controlled obsolete storage operation");},current);
        if(!entered.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Storage gate did not start");return release;
    }
    private void drained()throws Exception{((ExecutorService)field(controller,"storage")).submit(()->{}).get(5,TimeUnit.SECONDS);test.waitForIdleSync();}
    void run(){String database="test-ui-work-failure-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();List<HotCandidate> items=new ArrayList<>();for(int n=0;n<8;n++)items.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"存储错误隔离受控文字样本"+n,"","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),items,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryController original=(DiscoveryController)field(home,"controller");ExecutorService old=(ExecutorService)field(original,"storage");workers.add(old);ui(original::close);old.awaitTermination(8,TimeUnit.SECONDS);
            ui(()->{controller=new DiscoveryController(home,new DiscoveryController.Listener(){
                public void feed(List<FeedStore.Row> rows,int position,int offset,boolean newest){feeds++;}
                public void sources(Map<Source,JSONObject> states){}
                public void state(boolean loading,boolean available){}
                public void error(String message){errors.add(message);}
                public void refreshWork(boolean running){refreshing=running;}
                public void beforeFeedDraw(Runnable drawn){draws.add(drawn);}
            },false,new HotSourceCollector.Transport(){public boolean offline(){return true;}public boolean browserFallback(Source source){return false;}public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){throw new IllegalStateException("No source request permitted");}});controller.start();controller.interacting(true);});workers.add((ExecutorService)field(controller,"storage"));
            if(!await(()->(boolean)field(controller,"ready")&&feeds>0))throw new IllegalStateException("Controlled controller not ready");drained();

            int beforeFeeds=feeds,errorsBefore=errors.size();CountDownLatch stale=blockedFailure();
            ui(controller::refreshRecommendations);long request=(long)field(controller,"explicitRefreshEpoch");stale.countDown();boolean published=await(()->!draws.isEmpty());
            check(published,"obsolete storage failure does not discard the newer real refresh publication");
            check(onUi(()->errors.size()==errorsBefore&&(long)field(controller,"explicitRefreshEpoch")==request),"obsolete error neither reports a current failure nor cancels its request identity");
            check(onUi(()->published&&refreshing&&feeds==beforeFeeds+1),"new refresh remains busy until its newly published list is drawn even when the unchanged snapshot is reused");
            if(published)ui(()->draws.remove(0).run());
            check(onUi(()->published&&!refreshing),"actual draw completion still ends the newer refresh");
            drained();ui(controller::stop);ui(()->{controller.start();controller.interacting(true);});drained();

            int beforeSortErrors=errors.size(),beforeSortFeeds=feeds;CountDownLatch generation=blockedFailure();ui(()->controller.sort(false));generation.countDown();
            boolean rebuilt=await(()->feeds>beforeSortFeeds);check(rebuilt&&onUi(()->errors.size()==beforeSortErrors),"old generation error cannot report failure against the rebuilt feed");drained();

            int beforeCancelErrors=errors.size();CountDownLatch ranking=blockedFailure();ui(controller::requestRecommendation);ranking.countDown();drained();
            check(onUi(()->errors.size()==beforeCancelErrors),"a newer explicit recommendation request cannot receive the old generation storage error");

            long modelEpoch=(long)field(controller,"recommendationEpoch");int ownedErrors=errors.size();
            CountDownLatch owned=blockedFailure(()->(long)field(controller,"recommendationEpoch")==modelEpoch);ui(()->controller.interacting(true));owned.countDown();drained();
            check(onUi(()->errors.size()==ownedErrors),"model-owned failure is discarded after its own recommendation epoch is cancelled");
            int unownedErrors=errors.size();CountDownLatch unowned=blockedFailure();ui(()->controller.interacting(true));unowned.countDown();drained();
            check(onUi(()->errors.size()==unownedErrors+1),"unrelated interaction does not hide a current non-model storage failure");

            // Hold the queue first, start a fresh request, then enqueue an error
            // captured under that same request: this one must still end busy state.
            CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);gates.add(release);
            ((ExecutorService)field(controller,"storage")).submit(()->{entered.countDown();try{release.await(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});if(!entered.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Current gate absent");
            long currentSnapshot=(long)field(controller,"snapshot");int currentErrors=errors.size(),currentFeeds=feeds;
            ui(controller::refreshRecommendations);work(()->{throw new java.io.IOException("Controlled current storage failure");});check(onUi(()->refreshing),"current request starts its busy indication before storage work returns");release.countDown();
            boolean failed=await(()->errors.size()>currentErrors&&!refreshing);drained();
            check(failed&&onUi(()->(long)field(controller,"snapshot")==currentSnapshot&&feeds==currentFeeds),"current-context storage failure still ends busy state and keeps the readable feed");
            check(onUi(()->successes==0),"failed work never invokes a success consumer");
            check(onUi(()->field(controller,"recommendation")==null),"controlled error checks do not start an inference model");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{for(CountDownLatch gate:gates)gate.countDown();ui(()->{if(controller!=null)controller.close();if(home!=null)home.finish();});test.waitForIdleSync();for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("scope=controlled local queue failure ownership; not live-source, device energy or personal relevance\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
