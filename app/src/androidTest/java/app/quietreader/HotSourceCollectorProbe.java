package app.quietreader;

import android.app.Instrumentation;
import android.os.SystemClock;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import static app.quietreader.Models.*;

/** Real main looper + serial SQLite, controlled transport; no live platform requests. */
final class HotSourceCollectorProbe {
    private final Instrumentation instrumentation;
    private final BiConsumer<Boolean,String> check;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final List<Fetch> calls=new CopyOnWriteArrayList<>();
    private final AtomicInteger finished=new AtomicInteger(),errors=new AtomicInteger();
    private FeedStore store;
    private HotSourceCollector collector;
    private volatile int peak;
    private final java.util.concurrent.atomic.AtomicBoolean cancelOnNotification=new java.util.concurrent.atomic.AtomicBoolean(),cancelOnStart=new java.util.concurrent.atomic.AtomicBoolean();
    private static final class Fetch {
        final Source source;final boolean browser;final Repository.Result<List<Item>> result;volatile boolean closed;
        Fetch(Source source,boolean browser,Repository.Result<List<Item>> result){this.source=source;this.browser=browser;this.result=result;}
    }
    HotSourceCollectorProbe(Instrumentation instrumentation,BiConsumer<Boolean,String> check){this.instrumentation=instrumentation;this.check=check;}
    private void ui(Runnable task){instrumentation.runOnMainSync(task);}
    private <T>T db(Callable<T> task)throws Exception{return storage.submit(task).get(5,TimeUnit.SECONDS);}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+5000;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<until)Thread.sleep(10);if(!condition.getAsBoolean())throw new IllegalStateException("collector probe timed out");}
    private List<Item> rows(Source source,String title){return Collections.singletonList(new Item(source,title,source.boardPage(),"123"));}
    private Fetch last(){return calls.get(calls.size()-1);}
    private String title()throws Exception{return db(()->store.candidates(100,0).stream().filter(c->c.source==Source.ZHIHU).findFirst().get().title);}
    private CountDownLatch blockStorage()throws Exception{
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        storage.execute(()->{entered.countDown();try{if(!release.await(5,TimeUnit.SECONDS))throw new IllegalStateException("unreleased test barrier");}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
        if(!entered.await(5,TimeUnit.SECONDS))throw new IllegalStateException("test barrier not entered");return release;
    }
    void run()throws Exception{
        String database="test-collector-"+UUID.randomUUID()+".db";
        store=new FeedStore(instrumentation.getTargetContext(),database);
        try{
            check.accept(db(()->{for(Source source:HotSourceCatalog.TEXT_HOT)if(!store.sourceState(source).getBoolean("enabled"))return false;return true;}),"fresh install enables text-oriented popular feeds without platform score bonuses");
            db(()->{long r=store.beginRefresh(Source.ZHIHU);store.upsertSnapshot(Source.ZHIHU,r,Collections.singletonList(new HotCandidate(Source.ZHIHU,Source.ZHIHU.boardPage(),"保留旧话题","","",false,1,null,100)),100);return null;});
            ui(()->collector=new HotSourceCollector(store,storage,new HotSourceCollector.Listener(){
                public void onSourceChanged(Source source){if(cancelOnNotification.getAndSet(false))collector.cancel();}
                public void onFinished(){finished.incrementAndGet();}
                public void onStorageFailure(){errors.incrementAndGet();}
            },new HotSourceCollector.Transport(){
                public boolean browserBoard(Source source){return source==Source.BILIBILI||source==Source.DOUYIN;}
                public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){
                    Fetch f=new Fetch(source,browser,result);calls.add(f);if(cancelOnStart.getAndSet(false))collector.cancel();peak=Math.max(peak,(int)calls.stream().filter(x->!x.closed).count());return ()->f.closed=true;
                }
                public boolean offline(){return true;}
            }));
            ui(()->collector.refresh(Arrays.asList(Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.V2EX,Source.ZHIHU,Source.IFANR)));
            await(()->calls.size()==3);check.accept(peak==3,"collector starts at most three independent sources");
            Fetch first=calls.get(0);
            ui(()->first.result.failure("offline"));await(()->calls.size()==4);
            check.accept(title().equals("保留旧话题")&&db(()->store.sourceState(Source.ZHIHU).getLong("lastSuccessAt"))==100,"collector failure preserves successful contents and timestamp");
            ui(()->{Fetch f=calls.get(1);f.result.success(rows(f.source,"新的中文话题"));f.result.failure("duplicate callback");});
            await(()->calls.size()==5);
            ui(()->{for(int n=2;n<5;n++){Fetch f=calls.get(n);f.result.success(rows(f.source,"新的中文话题"));}});
            await(()->finished.get()==1);check.accept(peak==3&&calls.size()==5&&errors.get()==0,"all five finish independently; duplicate and non-hot sources excluded");
            check.accept(db(()->store.sourceState(Source.WEIBO).getString("status")).equals("READY"),"duplicate failure cannot overwrite successful source");

            int count=calls.size();ui(()->collector.refresh(Collections.singletonList(Source.ZHIHU)));await(()->calls.size()==count+1);Fetch stale=last();
            ui(()->collector.refresh(Collections.singletonList(Source.ZHIHU)));await(()->calls.size()==count+2);Fetch fresh=last();
            ui(()->{stale.result.success(rows(Source.ZHIHU,"过期请求"));fresh.result.success(rows(Source.ZHIHU,"当前请求"));});await(()->finished.get()==2);
            check.accept(stale.closed&&title().equals("当前请求"),"replacement refresh ignores late network success");

            int before=calls.size();CountDownLatch pending=blockStorage();
            ui(()->{collector.refresh(Collections.singletonList(Source.ZHIHU));collector.cancel();});pending.countDown();db(()->null);instrumentation.waitForIdleSync();
            check.accept(calls.size()==before,"cancel before database preparation starts no network request");
            ui(()->collector.refresh(Collections.singletonList(Source.ZHIHU)));await(()->calls.size()==before+1);Fetch queued=last();
            CountDownLatch commit=blockStorage();ui(()->{queued.result.success(rows(Source.ZHIHU,"取消后不能写入"));collector.cancel();});commit.countDown();db(()->null);instrumentation.waitForIdleSync();
            check.accept(title().equals("当前请求"),"cancel rejects success waiting in the database queue");
            check.accept(!db(()->store.sourceState(Source.ZHIHU).getString("status")).equals("REFRESHING"),"cancel clears persisted refreshing state");

            int next=calls.size();ui(()->collector.refresh(Collections.singletonList(Source.ZHIHU)));await(()->calls.size()==next+1);
            long deadline=System.currentTimeMillis()+300_000;Fetch limited=last();ui(()->limited.result.failure("HTTP429",429,deadline));await(()->finished.get()==3);
            check.accept(db(()->store.sourceState(Source.ZHIHU).getLong("retryAt"))==deadline,"HTTP429 preserves server deadline even if connectivity flag is offline");
            int limitedCount=calls.size();ui(()->collector.refresh(Arrays.asList(Source.ZHIHU,Source.WEIBO)));await(()->calls.size()==limitedCount+1);Fetch allowed=last();
            check.accept(allowed.source==Source.WEIBO,"limited source does not stop another platform refreshing");
            ui(()->allowed.result.success(rows(Source.WEIBO,"仍然可以更新")));await(()->finished.get()==4);
            int browserCount=calls.size();ui(()->collector.retryInBrowser(Source.ZHIHU));await(()->finished.get()==5);
            check.accept(calls.size()==browserCount,"browser retry cannot bypass rate limit");
            db(()->{store.enable(Source.HUPU,false);return null;});ui(()->collector.retryInBrowser(Source.HUPU));await(()->finished.get()==6);
            check.accept(calls.size()==browserCount,"disabled source cannot create a browser");
            ui(()->collector.retryInBrowser(Source.CLS));await(()->calls.size()==browserCount+1);Fetch browser=last();
            check.accept(browser.browser&&calls.stream().filter(x->!x.closed).count()==1,"explicit eligible browser retry is single and isolated");
            ui(()->collector.cancel());db(()->null);check.accept(browser.closed,"entering reader cancels browser work");

            int optionalCalls=calls.size(),optionalFinished=finished.get();
            check.accept(!db(()->store.sourceState(Source.DOUYIN).getBoolean("enabled"))&&!db(()->store.sourceState(Source.BILIBILI).getBoolean("enabled")),"fresh install leaves video-centric boards disabled");
            ui(()->collector.refresh(Arrays.asList(Source.BILIBILI,Source.DOUYIN)));await(()->finished.get()==optionalFinished+1);
            check.accept(calls.size()==optionalCalls,"disabled optional boards create no transport or browser work");
            db(()->{store.enable(Source.BILIBILI,true);store.enable(Source.DOUYIN,true);return null;});
            db(()->{store.close();store=new FeedStore(instrumentation.getTargetContext(),database);return null;});
            check.accept(db(()->store.sourceState(Source.BILIBILI).getBoolean("enabled")&&store.sourceState(Source.DOUYIN).getBoolean("enabled")&&!store.sourceState(Source.HUPU).getBoolean("enabled")),"reopening preserves explicit optional opt-ins and text source opt-outs");
            int mixed=calls.size(),mixedFinished=finished.get();
            ui(()->collector.refresh(Arrays.asList(Source.BILIBILI,Source.DOUYIN,Source.WEIBO)));await(()->calls.size()==mixed+2);
            check.accept(calls.get(mixed).source==Source.BILIBILI&&calls.get(mixed).browser&&calls.get(mixed+1).source==Source.WEIBO&&!calls.get(mixed+1).browser,"automatic browser source remains serial while native source uses another slot");
            ui(()->calls.get(mixed).result.success(rows(Source.BILIBILI,"综合热门合成标题")));await(()->calls.size()==mixed+3);
            Fetch secondBrowser=calls.get(mixed+2);
            check.accept(secondBrowser.source==Source.DOUYIN&&secondBrowser.browser&&calls.get(mixed).closed&&calls.stream().filter(x->x.browser&&!x.closed).count()==1,"second browser begins only after first source releases its WebView slot");
            ui(()->{calls.get(mixed+1).result.success(rows(Source.WEIBO,"微博热门合成标题"));secondBrowser.result.success(rows(Source.DOUYIN,"抖音热榜合成标题"));});await(()->finished.get()==mixedFinished+1);

            int reentrantBefore=calls.size();cancelOnNotification.set(true);
            ui(()->collector.refresh(Collections.singletonList(Source.CLS)));await(()->!cancelOnNotification.get());db(()->null);instrumentation.waitForIdleSync();
            check.accept(calls.size()==reentrantBefore,"cancellation from source notification cannot start a new transport");
            check.accept(!db(()->store.sourceState(Source.CLS).getString("status")).equals("REFRESHING"),"reentrant notification cancellation clears persisted refresh state");
            cancelOnStart.set(true);ui(()->collector.refresh(Collections.singletonList(Source.CLS)));await(()->calls.size()==reentrantBefore+1);instrumentation.waitForIdleSync();db(()->null);
            check.accept(last().closed,"request returned after synchronous startup cancellation is closed immediately");

            int languageCalls=calls.size(),languageDone=finished.get();
            ui(()->collector.refresh(Collections.singletonList(Source.V2EX)));await(()->calls.size()==languageCalls+1);
            Fetch language=last();
            ui(()->language.result.success(Arrays.asList(
                new Item(Source.V2EX,"GPT-5","https://www.v2ex.com/t/99001","",false,"这个模型的中文表现如何？",null,""),
                new Item(Source.V2EX,"東京の新しい研究","https://www.v2ex.com/t/99002",""),
                new Item(Source.V2EX,"New research on machine learning","https://www.v2ex.com/t/99003",""))));
            await(()->finished.get()==languageDone+1);
            List<HotCandidate> languageRows=db(()->{List<HotCandidate> result=new ArrayList<>();for(HotCandidate c:store.candidates(100,0))if(c.source==Source.V2EX)result.add(c);return result;});
            check.accept(languageRows.size()==1&&languageRows.get(0).title.equals("GPT-5")&&languageRows.get(0).snippet.contains("中文"),"collector retains short brand headings supported by Chinese summaries and excludes foreign headings");
            check.accept(CandidateOrder.arrange(languageRows,new org.json.JSONArray(),false).size()==1,"collector and ranking apply the same Chinese metadata eligibility");

            CountDownLatch closing=blockStorage();int doneBefore=finished.get(),callsBefore=calls.size();
            ui(()->{collector.refresh(Collections.singletonList(Source.CLS));collector.close();});
            storage.execute(store::close);storage.shutdown();closing.countDown();
            check.accept(storage.awaitTermination(5,TimeUnit.SECONDS),"owner drains storage safely after collector close");
            instrumentation.waitForIdleSync();check.accept(calls.size()==callsBefore&&finished.get()==doneBefore&&errors.get()==0,"late callbacks after close neither restart work nor notify destroyed UI");
        }finally{
            if(collector!=null)ui(collector::close);
            if(!storage.isShutdown()){storage.submit(store::close).get(5,TimeUnit.SECONDS);storage.shutdown();}
            storage.awaitTermination(5,TimeUnit.SECONDS);instrumentation.getTargetContext().deleteDatabase(database);
        }
    }
}

