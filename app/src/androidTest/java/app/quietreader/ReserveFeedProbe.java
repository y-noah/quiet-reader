package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real finite Controller batches and cache; controlled board/body transport. */
final class ReserveFeedProbe {
    private final Instrumentation test;private DiscoveryActivity home;private DiscoveryController controller;private Repository cache;
    private final List<ExecutorService> workers=new ArrayList<>();private final List<Body> bodies=new ArrayList<>();private Repository.Result<List<Item>> board;
    private final boolean failureFirst,coldLifecycle;
    private int checks,failures,boardCalls;private final StringBuilder report=new StringBuilder("FOUR ROUND RESERVE\n");
    private static final class Body {final Item item;final Repository.Result<Document> result;Body(Item item,Repository.Result<Document> result){this.item=item;this.result=result;}}
    ReserveFeedProbe(Instrumentation test){this(test,false);}
    ReserveFeedProbe(Instrumentation test,boolean failureFirst){this(test,failureFirst,false);}
    ReserveFeedProbe(Instrumentation test,boolean failureFirst,boolean coldLifecycle){this.test=test;this.failureFirst=failureFirst;this.coldLifecycle=coldLifecycle;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier action){boolean[] value={false};ui(()->value[0]=action.getAsBoolean());return value[0];}
    private void await(String stage,BooleanSupplier action)throws Exception{long until=SystemClock.elapsedRealtime()+12000;while(!onUi(action)){if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException(stage);Thread.sleep(20);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private Document body(Item item){Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","当前换批缓存受控中文正文，供验证原生阅读准备和独立内容来源。".repeat(25)));ArticleVersion.capture(item,doc);return doc;}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(controller,"rows");}
    private List<Item> supply(long seed,int count){List<Item> items=new ArrayList<>();for(int n=0;n<count;n++)items.add(new Item(Source.THEPAPER,"当前批次中文话题"+(seed+n),"https://www.thepaper.cn/newsDetail_forward_"+(seed+n),""));return items;}
    private int completeBatch(int from,int count)throws Exception{for(int n=0;n<count;n++){int at=from+n;await("full current batch body "+n,()->bodies.size()>at);Body b=bodies.get(at);ui(()->b.result.success(body(b.item)));}return from+count;}
    void run(){String database="test-ui-reserve-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();List<Item> initial=supply(now,(FeedReserve.CAPACITY+20));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){
                for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);
                List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<initial.size();n++){Item item=initial.get(n);candidates.add(new HotCandidate(item.source,item.url,item.title,"","",false,n+1,null,now));}
                seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);seed.finishInterestSetup(List.of(),Map.of(),now);
            }
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",!coldLifecycle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryController old=(DiscoveryController)field(home,"controller");
            if(coldLifecycle){
                await("real auto-prefetch cold home draws before optional reader warmup",()->!((List<?>)field(old,"rows")).isEmpty());
                await("native first draw releases deferred reader startup",()->!ReaderStartup.deferred());
                check(true,"ordinary auto-prefetch startup breaks feed/body/provider circular wait");
            }
            ExecutorService oldWorker=(ExecutorService)field(old,"storage");workers.add(oldWorker);ui(old::close);oldWorker.awaitTermination(8,TimeUnit.SECONDS);
            ui(()->{
                cache=new Repository(home);controller=new DiscoveryController(home,home,true,new HotSourceCollector.Transport(){public boolean offline(){return false;}public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){boardCalls++;board=result;return ()->{};}});
                set(home,"controller",controller);workers.add((ExecutorService)field(controller,"storage"));
                ((ArticlePreloader)field(controller,"preloader")).close();
                ArticlePreloader loader=new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> result){bodies.add(new Body(item,result));}public void cancel(){}},FeedReserve.CAPACITY,()->{},true);
                loader.observeFailure(item->{try{java.lang.reflect.Method method=DiscoveryController.class.getDeclaredMethod("bodyFailed",Item.class);method.setAccessible(true);method.invoke(controller,item);}catch(Exception error){throw new IllegalStateException(error);}});
                loader.observePrepared((item,doc)->{try{java.lang.reflect.Method method=DiscoveryController.class.getDeclaredMethod("bodyPrepared",Item.class,Document.class);method.setAccessible(true);method.invoke(controller,item,doc);}catch(Exception error){throw new IllegalStateException(error);}});
                set(controller,"preloader",loader);controller.start();
            });
            await("initial acquisition begins",()->!bodies.isEmpty());
            check(onUi(()->rows().size()==FeedBatch.SIZE),"cold native titles remain usable before optional body prefetch completes");
            check(onUi(()->rows().stream().anyMatch(row->row.candidate.originalUrl.equals(bodies.get(0).item.url))),"current visible batch gets the first body acquisition before future refresh screens");

            if(coldLifecycle){
                ui(controller::stop);
                ui(()->{try{Method expiry=DiscoveryController.class.getDeclaredMethod("expireCache");expiry.setAccessible(true);expiry.invoke(controller);}catch(Exception error){throw new IllegalStateException(error);}});
                check(onUi(()->rows().size()==FeedBatch.SIZE),"resource expiry before first body preserves cold native titles");
                ui(controller::start);
                check(onUi(()->rows().size()==FeedBatch.SIZE),"foreground return without prepared bodies retains a usable feed");
                ui(()->{controller.stop();controller.start();});
                check(onUi(()->rows().size()==FeedBatch.SIZE),"repeated stop and resume never empties the cold feed");
            }else{
            int first=0;String failedUrl=bodies.get(0).item.url;
            if(failureFirst){int failedAt=first;ui(()->bodies.get(failedAt).result.failure("controlled inaccessible source body"));first++;}
            completeBatch(first,FeedBatch.SIZE);
            await("initial prepared twenty",()->(boolean)field(controller,"ready")&&rows().size()==FeedBatch.SIZE&&field(controller,"admission")==null);
            check(rows().size()==FeedBatch.SIZE,"home publishes exactly twenty when supply permits");
            if(failureFirst){
                check(onUi(()->rows().stream().noneMatch(row->row.candidate.originalUrl.equals(failedUrl))),"an actual failed acquisition is excluded without publishing a failure shell");
                check(onUi(()->rows().get(0).candidate.originalUrl.equals(bodies.get(1).item.url)),"first readable ranked body remains first rather than arbitrary fast-source selection");
                // All twenty published records must still restore with RAM empty.
                List<Item> shownItems=new ArrayList<>();ui(()->{for(FeedStore.Row row:rows())shownItems.add(row.candidate.readerItem());cache.releaseArticleMemory();});
                int restoredCount=0;for(Item item:shownItems){CountDownLatch latch=new CountDownLatch(1);boolean[] restored={false};ui(()->cache.restoreArticle(item,doc->{restored[0]=doc!=null&&ArticleVersion.matches(item,doc);latch.countDown();}));if(latch.await(5,TimeUnit.SECONDS)&&restored[0])restoredCount++;}
                check(restoredCount==FeedBatch.SIZE,"every published body restores after RAM eviction even following an acquisition failure");
                await("first future acquisition",()->bodies.size()>(FeedBatch.SIZE+1));String failedFuture=bodies.get((FeedBatch.SIZE+1)).item.url;
                ui(()->bodies.get((FeedBatch.SIZE+1)).result.failure("controlled future failure"));
                completeBatch((FeedBatch.SIZE+2),(FeedBatch.SIZE*FeedReserve.ROUNDS));await("replacement future preparation settles",()->!((ArticlePreloader)field(controller,"preloader")).busy());
                check(onUi(()->((FeedReserve)field(controller,"reserve")).future().size()==(FeedBatch.SIZE*FeedReserve.ROUNDS)&&((FeedReserve)field(controller,"reserve")).future().stream().noneMatch(row->row.candidate.originalUrl.equals(failedFuture))),"failed future slot is replaced proactively before a refresh is requested");

            }else{
            await("first future body in flight",()->bodies.size()==(FeedBatch.SIZE+1));
            ArticlePreloader preparing=(ArticlePreloader)field(controller,"preloader");Object inFlight=field(preparing,"active");
            java.io.File orphan=new java.io.File(new java.io.File(home.getCacheDir(),"article-bodies-v1"),HotCandidate.hash("publication-cleanup-orphan")+".json");
            java.nio.file.Files.write(orphan.toPath(),"{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Object budget=field(controller,"prefetchBudget");long lease=(long)field(budget,"deadline"),epoch=(long)field(controller,"prefetchEpoch");
            ui(()->{set(controller,"cachePublicationChanged",true);try{Method method=DiscoveryController.class.getDeclaredMethod("cacheAcceptedBatch",boolean.class);method.setAccessible(true);method.invoke(controller,false);}catch(Exception error){throw new IllegalStateException(error);}});
            ((ExecutorService)field(controller,"storage")).submit(()->{}).get(8,TimeUnit.SECONDS);
            check(!orphan.exists(),"publication-only change prunes an unrelated disk body with an unchanged prefetch window");
            check(onUi(()->(long)field(budget,"deadline")==lease&&(long)field(controller,"prefetchEpoch")==epoch&&field(preparing,"active")==inFlight&&bodies.size()==(FeedBatch.SIZE+1)),"publication cleanup does not extend the lease, interrupt the in-flight body or schedule another acquisition");
            ui(()->{try{Method method=DiscoveryController.class.getDeclaredMethod("cacheAcceptedBatch",boolean.class);method.setAccessible(true);method.invoke(controller,true);}catch(Exception error){throw new IllegalStateException(error);}});
            Thread.sleep(500);
            check(onUi(()->field(preparing,"active")==inFlight&&bodies.size()==(FeedBatch.SIZE+1)),"cache window renewal preserves an eligible in-flight body instead of restarting it");
            ui(()->controller.interacting(true));
            check(onUi(()->field(preparing,"active")==inFlight&&bodies.size()==(FeedBatch.SIZE+1)),"scrolling lets the single in-flight body finish without starting another request");
            ui(()->controller.interacting(false));Thread.sleep(800);
            check(onUi(()->field(preparing,"active")==inFlight&&bodies.size()==(FeedBatch.SIZE+1)),"ending a scroll resumes the same acquisition rather than restarting its source");
            completeBatch(FeedBatch.SIZE,(FeedBatch.SIZE*FeedReserve.ROUNDS));await("initial cache settles",()->!((ArticlePreloader)field(controller,"preloader")).busy());
            Field writerField=ArticleDiskCache.class.getDeclaredField("writer");writerField.setAccessible(true);((ExecutorService)writerField.get(null)).submit(()->{}).get(8,TimeUnit.SECONDS);
            ArticleDiskCache disk=new ArticleDiskCache(home);String[] namespace={null};Map<String,String> sessions=new HashMap<>();ui(()->{namespace[0]=disk.namespace();for(Body body:bodies)sessions.put(body.item.url,ArticleDiskCache.session(body.item.url));});
            int saved=0;for(Body body:bodies)if(disk.read(body.item.url,namespace[0],sessions.get(body.item.url),false,System.currentTimeMillis())!=null)saved++;
            check(saved==FeedReserve.CAPACITY,"all one hundred completed bodies survive the disk writer, not just RAM");
            check(onUi(()->bodies.size()==FeedReserve.CAPACITY),"one sequential loader warms current twenty plus eighty reserved bodies");
            check(onUi(()->((FeedReserve)field(controller,"reserve")).future().size()==(FeedBatch.SIZE*FeedReserve.ROUNDS)),"four full future screens are prepared without exposing them");
            List<String> beforePoll=new ArrayList<>();ui(()->beforePoll.addAll(((FeedReserve)field(controller,"reserve")).next("all")));
            long frozen=(long)field(controller,"snapshot");ui(()->{set(controller,"lastPoolRefresh",SystemClock.elapsedRealtime()-FeedReserve.UPDATE_MS);((Runnable)field(controller,"poolUpdate")).run();});await("warm pool refresh starts",()->boardCalls==1);
            check(onUi(()->beforePoll.equals(((FeedReserve)field(controller,"reserve")).next("all"))),"starting five-minute collection does not discard already prepared screens");
            List<Item> updated=new ArrayList<>(supply(now+1000,5));updated.addAll(initial);ui(()->board.success(updated));await("real updated source commit",()->!(boolean)field(controller,"loading")&&(long)field(controller,"pending")>0);
            check(onUi(()->(long)field(controller,"snapshot")==frozen&&beforePoll.equals(((FeedReserve)field(controller,"reserve")).next("all"))),"new hotter source items stage without replacing valid prepared bodies or the visible screen");
            List<Item> firstScreen=new ArrayList<>();
            Set<String> displayed=new HashSet<>();ui(()->rows().forEach(row->{displayed.add(row.eventId);firstScreen.add(row.candidate.readerItem());}));
            for(int round=0;round<4;round++){
                long before=(long)field(controller,"snapshot");List<String> expected=new ArrayList<>();ui(()->expected.addAll(((FeedReserve)field(controller,"reserve")).next("all")));
                ui(controller::refreshRecommendations);await("cached refresh "+round,()->(long)field(controller,"snapshot")!=before&&!(boolean)field(controller,"explicitRefreshRunning"));
                ui(()->{
                    check(rows().size()==FeedBatch.SIZE,"prepared refresh publishes twenty");
                    for(int n=0;n<rows().size();n++){FeedStore.Row row=rows().get(n);check(row.eventId.equals(expected.get(n)),"refresh consumes the prepared sequence");check(displayed.add(row.eventId),"no previous screen repeats");check(cache.cachedArticle(row.candidate.readerItem())!=null,"published body was cached before refresh");}
                });
                check(onUi(()->rows().stream().allMatch(row->{Item item=row.candidate.readerItem();return disk.pinned(item.url,namespace[0],ArticleDiskCache.session(item.url),ArticleVersion.hash(item));})),"manual refresh pins every body in the actual displayed screen");
                boolean firstLeftWindow=onUi(()->{Set<String> keep=new HashSet<>();for(FeedStore.Row row:rows())keep.add(row.candidate.originalUrl);for(FeedStore.Row row:((FeedReserve)field(controller,"reserve")).future())keep.add(row.candidate.originalUrl);return firstScreen.stream().noneMatch(item->keep.contains(item.url));});
                check(firstLeftWindow,"initial screen leaves the current and future cache window after manual refresh");
                long cleanupEnd=SystemClock.elapsedRealtime()+5000;boolean removed=false;
                while(SystemClock.elapsedRealtime()<cleanupEnd){removed=true;for(Item item:firstScreen){java.io.File file=new java.io.File(new java.io.File(home.getCacheDir(),"article-bodies-v1"),HotCandidate.hash(namespace[0]+"\n"+sessions.get(item.url)+"\n"+item.url)+".json");if(file.exists()){removed=false;break;}}if(removed)break;Thread.sleep(25);}
                check(removed,"manual refresh releases obsolete first-screen disk bodies rather than retaining stale publication pins");
            }
            check(displayed.size()==FeedReserve.CAPACITY,"four refreshes plus initial screen cover one hundred unique articles");
            check(onUi(()->boardCalls==1),"rapid refreshes do not repeatedly fetch source boards");
            long visible=(long)field(controller,"snapshot");ui(()->{set(controller,"lastPoolRefresh",SystemClock.elapsedRealtime()-FeedReserve.UPDATE_MS);((Runnable)field(controller,"poolUpdate")).run();});await("due source poll",()->boardCalls==2);
            check(onUi(()->(long)field(controller,"snapshot")==visible),"five-minute source update leaves visible screen untouched");
            ui(()->board.failure("controlled offline"));await("source failure settles",()->!(boolean)field(controller,"loading"));
            check(onUi(()->rows().size()==FeedBatch.SIZE&&(long)field(controller,"snapshot")==visible),"failed source poll preserves the readable cached screen");
            ui(controller::stop);int calls=boardCalls,started=bodies.size();ui(()->{((Runnable)field(controller,"poolUpdate")).run();controller.viewport(0);});Thread.sleep(800);
            check(onUi(()->boardCalls==calls&&bodies.size()==started&&!((ArticlePreloader)field(controller,"preloader")).busy()),"background cancels both polling and speculative body acquisition");
            check(onUi(()->!((Handler)field(controller,"main")).hasCallbacks((Runnable)field(controller,"poolUpdate"))),"no five-minute callback survives background stop");
            }
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(controller!=null)controller.close();if(home!=null)home.finish();if(cache!=null)cache.close();});for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("scope=real controller/cache with controlled source and body transport; no phone battery claim\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
