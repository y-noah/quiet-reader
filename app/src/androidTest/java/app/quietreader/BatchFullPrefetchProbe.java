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
final class BatchFullPrefetchProbe {
    private final Instrumentation test;private DiscoveryActivity home;private DiscoveryController controller;private Repository cache;
    private final List<ExecutorService> workers=new ArrayList<>();private final List<Body> bodies=new ArrayList<>();private Repository.Result<List<Item>> board;
    private int checks,failures;private final StringBuilder report=new StringBuilder("CURRENT FULL BATCH PREFETCH\n");
    private static final class Body {final Item item;final Repository.Result<Document> result;Body(Item item,Repository.Result<Document> result){this.item=item;this.result=result;}}
    BatchFullPrefetchProbe(Instrumentation test){this.test=test;}
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
    void run(){String database="test-ui-full-batch-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();List<Item> initial=supply(now,12),incoming=supply(now+10000,12);
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<initial.size();n++){Item item=initial.get(n);candidates.add(new HotCandidate(item.source,item.url,item.title,"","",false,n+1,null,now));}seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryController old=(DiscoveryController)field(home,"controller");ExecutorService oldWorker=(ExecutorService)field(old,"storage");workers.add(oldWorker);ui(old::close);oldWorker.awaitTermination(8,TimeUnit.SECONDS);
            ui(()->{
                cache=new Repository(home);controller=new DiscoveryController(home,home,true,new HotSourceCollector.Transport(){public boolean browserFallback(Source source){return false;}public boolean offline(){return false;}public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){board=result;return ()->{};}});set(home,"controller",controller);workers.add((ExecutorService)field(controller,"storage"));
                ((ArticlePreloader)field(controller,"preloader")).close();ArticlePreloader preloader=new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> result){bodies.add(new Body(item,result));}public void cancel(){}},20,()->{},true);set(controller,"preloader",preloader);controller.start();
            });
            await("initial accepted batch",()->(boolean)field(controller,"ready")&&!rows().isEmpty());int size=rows().size();Set<String> first=new HashSet<>();ui(()->{for(FeedStore.Row row:rows())first.add(row.candidate.originalUrl);});
            check(size>3&&size<=20,"actual finite presentation supplies more than three items to exercise whole-batch acquisition");int completed=completeBatch(0,size);
            check(onUi(()->bodies.size()==size&&bodies.stream().allMatch(b->first.contains(b.item.url))),"initialization acquires every displayed article and no undisplayed pool member");
            long before=(long)field(controller,"snapshot");ui(()->controller.refresh(List.of(Source.THEPAPER)));await("controlled board refresh",()->board!=null);ui(()->board.success(incoming));await("new snapshot remains pending",()->(long)field(controller,"pending")>0);
            Thread.sleep(900);check(onUi(()->(long)field(controller,"snapshot")==before&&bodies.size()==size),"unaccepted candidate supply neither replaces the screen nor consumes body requests");
            ui(controller::acceptUpdates);await("refresh accepts new current batch",()->(long)field(controller,"snapshot")!=before);
            Set<String> next=new HashSet<>();ui(()->{for(FeedStore.Row row:rows())next.add(row.candidate.originalUrl);});int nextSize=next.size();completeBatch(completed,nextSize);
            check(onUi(()->bodies.subList(size,bodies.size()).stream().allMatch(b->next.contains(b.item.url))),"refresh immediately drains the full newly accepted batch instead of a viewport subset");
            check(onUi(()->cache.cachedArticle(bodies.get(0).item)==null),"successful replacement evicts old non-shared body from RAM");
            ui(()->controller.stop());int stopped=bodies.size();ui(()->controller.viewport(0));Thread.sleep(750);check(onUi(()->bodies.size()==stopped&&!((ArticlePreloader)field(controller,"preloader")).busy()),"background stop cancels speculative work and layout cannot restart it");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(controller!=null)controller.close();if(home!=null)home.finish();if(cache!=null)cache.close();});for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("scope=controlled transport with real finite-batch Controller/cache lifecycle, not live login or phone energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
