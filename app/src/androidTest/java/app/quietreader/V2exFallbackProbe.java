package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static app.quietreader.Models.*;

/** Real collector scheduling and SQLite; controlled transport, not source availability. */
final class V2exFallbackProbe {
    private final Instrumentation test;private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final List<Fetch> calls=new CopyOnWriteArrayList<>();private final AtomicInteger finished=new AtomicInteger(),errors=new AtomicInteger();
    private final AtomicBoolean offline=new AtomicBoolean(),synchronousFailure=new AtomicBoolean();
    private final StringBuilder report=new StringBuilder();private int checks,failures;private FeedStore store;private HotSourceCollector collector;
    private static final class Fetch {
        final Source source;final boolean browser;final Repository.Result<List<Item>> result;volatile boolean closed;
        Fetch(Source source,boolean browser,Repository.Result<List<Item>> result){this.source=source;this.browser=browser;this.result=result;}
    }
    V2exFallbackProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private <T>T db(Callable<T> action)throws Exception{return storage.submit(action).get(5,TimeUnit.SECONDS);}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+5000;while(!condition.getAsBoolean()){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Fallback probe timed out");Thread.sleep(10);}}
    private org.json.JSONObject state()throws Exception{return db(()->store.sourceState(Source.V2EX));}
    private List<Item> rows(Source source,String title){return List.of(new Item(source,title,source==Source.V2EX?"https://www.v2ex.com/t/99008801":source.boardPage(),""));}
    private void resetBackoff()throws Exception{db(()->{store.getWritableDatabase().execSQL("UPDATE sources SET retry_at=0 WHERE id='V2EX'");return null;});}
    void run(){String database="test-v2ex-fallback-"+UUID.randomUUID()+".db";
        try{
            store=new FeedStore(test.getTargetContext(),database);
            db(()->{store.enable(Source.BILIBILI,true);store.enable(Source.DOUYIN,true);store.upsertSnapshot(Source.V2EX,store.beginRefresh(Source.V2EX),List.of(new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/99008800","保留旧中文热帖","","",false,1,null,100)),100);return null;});
            ui(()->collector=new HotSourceCollector(store,storage,new HotSourceCollector.Listener(){public void onSourceChanged(Source s){}public void onFinished(){finished.incrementAndGet();}public void onStorageFailure(){errors.incrementAndGet();}},new HotSourceCollector.Transport(){
                public boolean browserBoard(Source source){return source==Source.BILIBILI||source==Source.DOUYIN;}
                public boolean browserFallback(Source source){return source==Source.V2EX;}
                public boolean offline(){return offline.get();}
                public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){Fetch fetch=new Fetch(source,browser,result);calls.add(fetch);if(!browser&&source==Source.V2EX&&synchronousFailure.getAndSet(false))result.failure("controlled synchronous failure",403);return ()->fetch.closed=true;}
            }));
            ui(()->collector.refresh(List.of(Source.BILIBILI,Source.DOUYIN,Source.V2EX,Source.WEIBO)));await(()->calls.size()==3);
            Fetch bili=calls.get(0),nativeV2=calls.get(1),weibo=calls.get(2);long revision=state().getLong("requested");
            ui(()->nativeV2.result.failure("native restricted",403));db(()->null);test.waitForIdleSync();
            check(nativeV2.closed&&calls.size()==3,"native failure closes request and queues fallback behind active browser");
            check(state().getString("status").equals("REFRESHING")&&state().getLong("requested")==revision&&state().getLong("lastSuccessAt")==100,"queued fallback preserves one refresh revision and old successful timestamp");
            ui(()->bili.result.success(rows(Source.BILIBILI,"综合热门中文标题")));await(()->calls.size()==4);Fetch douyin=calls.get(3);
            check(douyin.browser&&douyin.source==Source.DOUYIN&&bili.closed&&calls.stream().filter(c->c.browser&&!c.closed).count()==1,"pre-existing browser queue keeps single WebView slot");
            ui(()->douyin.result.success(rows(Source.DOUYIN,"当前抖音中文热榜")));await(()->calls.size()==5);Fetch fallback=calls.get(4);
            check(fallback.source==Source.V2EX&&fallback.browser&&calls.stream().filter(c->c.browser&&!c.closed).count()==1,"V2EX fallback starts only after previous browser releases slot");
            ui(()->{nativeV2.result.success(rows(Source.V2EX,"过期原生回调"));weibo.result.success(rows(Source.WEIBO,"微博中文热门"));fallback.result.success(rows(Source.V2EX,"网页获取的新中文热帖"));});await(()->finished.get()==1);
            check(state().getString("status").equals("READY")&&state().getLong("requested")==revision&&state().getLong("committed")==revision&&state().getInt("failures")==0,"browser success commits original revision without failure backoff or extra refresh");
            check(db(()->store.candidates(100,0).stream().anyMatch(c->c.source==Source.V2EX&&c.title.equals("网页获取的新中文热帖"))),"late native callback cannot replace browser snapshot");

            int base=calls.size();ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==base+1);Fetch nativeAgain=calls.get(base);
            ui(()->nativeAgain.result.failure("restricted",403));await(()->calls.size()==base+2);Fetch failedBrowser=calls.get(base+1);long deadline=System.currentTimeMillis()+300000;
            ui(()->failedBrowser.result.failure("browser rate limited",429,deadline));await(()->finished.get()==2);
            check(state().getString("status").equals("RATE_LIMITED")&&state().getInt("failures")==1&&state().getLong("retryAt")==deadline&&failedBrowser.closed,"browser failure settles once and preserves rate-limit deadline");
            int held=calls.size();ui(()->collector.refresh(List.of(Source.V2EX)));await(()->finished.get()==3);
            check(calls.size()==held,"automatic fallback cannot bypass an existing server retry deadline");

            resetBackoff();offline.set(true);ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==held+1);Fetch disconnected=calls.get(held);
            ui(()->disconnected.result.failure("network unavailable"));await(()->finished.get()==4);
            check(calls.size()==held+1&&state().getString("status").equals("OFFLINE"),"offline native failure does not start WebView");offline.set(false);
            int rate=calls.size();ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==rate+1);Fetch limited=calls.get(rate);
            ui(()->limited.result.failure("native 429",429,deadline));await(()->finished.get()==5);
            check(calls.size()==rate+1&&state().getString("status").equals("RATE_LIMITED"),"native 429 never triggers browser fallback");
            resetBackoff();int waiting=calls.size();ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==waiting+1);Fetch unavailable=calls.get(waiting);
            ui(()->unavailable.result.failure("native 503 retry later",503,deadline));await(()->finished.get()==6);
            check(calls.size()==waiting+1&&state().getLong("retryAt")>=deadline,"non-429 Retry-After also prevents automatic browser request");

            resetBackoff();int queued=calls.size();ui(()->collector.refresh(List.of(Source.BILIBILI,Source.V2EX)));await(()->calls.size()==queued+2);Fetch activeBoard=calls.get(queued),queuedNative=calls.get(queued+1);
            ui(()->{queuedNative.result.failure("restricted",403);collector.cancel();});db(()->null);test.waitForIdleSync();
            check(activeBoard.closed&&queuedNative.closed&&calls.size()==queued+2&&!state().getString("status").equals("REFRESHING"),"cancellation clears queued fallback revision without starting another browser");
            ui(()->{queuedNative.result.success(rows(Source.V2EX,"取消后原生结果"));activeBoard.result.success(rows(Source.BILIBILI,"取消后网页结果"));});db(()->null);test.waitForIdleSync();
            check(finished.get()==6,"late callbacks after queued cancellation do not complete or restart refresh");

            int disable=calls.size();ui(()->collector.refresh(List.of(Source.BILIBILI,Source.V2EX)));await(()->calls.size()==disable+2);Fetch blocker=calls.get(disable),disableNative=calls.get(disable+1);
            ui(()->disableNative.result.failure("restricted",403));db(()->{store.enable(Source.V2EX,false);return null;});ui(()->blocker.result.success(rows(Source.BILIBILI,"正常中文榜单")));await(()->finished.get()==7);
            check(calls.size()==disable+2&&!state().getBoolean("enabled")&&!state().getString("status").equals("REFRESHING"),"disabling while fallback is queued prevents browser creation and clears refreshing state");
            db(()->{store.enable(Source.V2EX,true);return null;});

            int sync=calls.size();synchronousFailure.set(true);ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==sync+2);
            check(calls.get(sync).closed&&calls.get(sync+1).browser,"synchronous native error still closes returned handle and schedules one fallback");
            ui(()->calls.get(sync+1).result.success(rows(Source.V2EX,"同步失败后网页成功")));await(()->finished.get()==8);
            check(state().getString("status").equals("READY")&&errors.get()==0,"reentrant fallback finishes successfully without storage errors");

            int empty=calls.size();ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==empty+1);ui(()->calls.get(empty).result.success(Collections.emptyList()));await(()->calls.size()==empty+2);
            ui(()->calls.get(empty+1).result.failure("no board content"));await(()->finished.get()==9);
            check(calls.size()==empty+2&&state().getInt("failures")==1,"empty native board tries browser once and final failure counts once");

            resetBackoff();int filtered=calls.size(),filteredDone=finished.get();long previousSuccess=state().getLong("lastSuccessAt");
            ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==filtered+1);long filteredRevision=state().getLong("requested");
            List<Item> unusable=Arrays.asList(null,new Item(Source.V2EX,"New research on machine learning","https://www.v2ex.com/t/99008802",""),new Item(Source.WEIBO,"其他平台的中文条目",Source.WEIBO.boardPage(),""));
            ui(()->calls.get(filtered).result.success(unusable));await(()->calls.size()==filtered+2);
            check(calls.get(filtered).closed&&calls.get(filtered+1).browser&&state().getLong("requested")==filteredRevision&&state().getLong("lastSuccessAt")==previousSuccess,"fully filtered native board transfers same revision to one browser without fake freshness");
            ui(()->calls.get(filtered+1).result.success(rows(Source.V2EX,"过滤之后网页获取的中文热帖")));await(()->finished.get()==filteredDone+1);
            check(state().getString("status").equals("READY")&&state().getInt("failures")==0&&db(()->store.candidates(100,0).stream().anyMatch(c->c.title.equals("过滤之后网页获取的中文热帖"))),"filtered-native fallback commits actual usable browser titles");

            int mixed=calls.size(),mixedDone=finished.get();ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==mixed+1);
            List<Item> usable=new ArrayList<>(unusable);usable.addAll(rows(Source.V2EX,"原生已有可用中文热帖"));ui(()->calls.get(mixed).result.success(usable));await(()->finished.get()==mixedDone+1);
            check(calls.size()==mixed+1&&state().getString("status").equals("READY"),"mixed native list with usable Chinese content creates no unnecessary browser");

            int offlineFiltered=calls.size(),offlineDone=finished.get();long offlineSuccess=state().getLong("lastSuccessAt");offline.set(true);ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==offlineFiltered+1);
            ui(()->calls.get(offlineFiltered).result.success(unusable));await(()->finished.get()==offlineDone+1);
            check(calls.size()==offlineFiltered+1&&state().getLong("lastSuccessAt")==offlineSuccess&&db(()->store.candidates(100,0).stream().anyMatch(c->c.title.equals("原生已有可用中文热帖"))),"fully filtered result while offline preserves prior snapshot without browser work");offline.set(false);

            resetBackoff();int both=calls.size(),bothDone=finished.get(),priorFailures=state().getInt("failures");ui(()->collector.refresh(List.of(Source.V2EX)));await(()->calls.size()==both+1);
            ui(()->calls.get(both).result.success(unusable));await(()->calls.size()==both+2);long beforeBrowser=state().getLong("lastSuccessAt");
            ui(()->calls.get(both+1).result.success(unusable));await(()->finished.get()==bothDone+1);
            check(calls.size()==both+2&&state().getInt("failures")==priorFailures+1&&state().getLong("lastSuccessAt")==beforeBrowser,"fully filtered browser result settles once without retry loop or refreshed timestamp");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{if(collector!=null)ui(collector::close);if(store!=null)try{db(()->{store.close();return null;});}catch(Exception ignored){}storage.shutdownNow();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
