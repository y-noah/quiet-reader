package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Actual controller and shared body cache; controlled board/body transports. */
final class DiscoveryPrefetchSupplyProbe {
    private final Instrumentation test;
    private final boolean warm;
    private DiscoveryActivity activity;private DiscoveryController controller;private Repository cache;
    private final List<ExecutorService> workers=new ArrayList<>();
    private final List<Board> boards=new ArrayList<>();private final List<Body> bodies=new ArrayList<>();
    private int checks,failures,cancellations;private final StringBuilder report=new StringBuilder();
    private static final class Board {
        final Source source;final boolean browser;final Repository.Result<List<Item>> result;boolean closed;
        Board(Source source,boolean browser,Repository.Result<List<Item>> result){this.source=source;this.browser=browser;this.result=result;}
    }
    private static final class Body {final Item item;final Repository.Result<Document> result;Body(Item item,Repository.Result<Document> result){this.item=item;this.result=result;}}
    DiscoveryPrefetchSupplyProbe(Instrumentation test){this(test,false);}
    DiscoveryPrefetchSupplyProbe(Instrumentation test,boolean warm){this.test=test;this.warm=warm;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Supply prefetch deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private Board board(Source source,boolean browser){return boards.stream().filter(b->b.source==source&&b.browser==browser&&!b.closed).findFirst().get();}
    private boolean loading(){return (boolean)field(controller,"loading");}
    private Document document(Body body){Document doc=new Document();doc.url=body.item.url;doc.title=body.item.title;doc.blocks.add(new Block("text","受控文字热门内容正文，用于验证提前准备和取消，不代表真实来源或推荐质量。".repeat(16)));ArticleVersion.capture(body.item,doc);return doc;}
    private void settle(long millis)throws Exception{test.waitForIdleSync();Thread.sleep(millis);test.waitForIdleSync();}
    void run(){String database="test-ui-prefetch-supply-"+UUID.randomUUID()+".db";
        try{
            long fixture=System.currentTimeMillis();List<Item> articles=new ArrayList<>();for(int n=0;n<3;n++)articles.add(new Item(Source.THEPAPER,"文字首批提前准备样本"+n,"https://www.thepaper.cn/newsDetail_forward_"+(fixture+n),""));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                for(Source source:Source.values())store.enable(source,source==Source.THEPAPER||source==Source.WEIBO||source==Source.V2EX);
                if(warm){long stale=fixture-20*60_000;List<HotCandidate> known=new ArrayList<>();for(int n=0;n<articles.size();n++){Item item=articles.get(n);known.add(new HotCandidate(item.source,item.url,item.title,item.snippet,item.detail,false,n+1,null,stale));}store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),known,stale);}
            }
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            DiscoveryController original=(DiscoveryController)field(activity,"controller");ExecutorService old=(ExecutorService)field(original,"storage");workers.add(old);ui(original::close);old.awaitTermination(8,TimeUnit.SECONDS);
            ui(()->{
                controller=new DiscoveryController(activity,activity,true,new HotSourceCollector.Transport(){
                    public boolean browserFallback(Source source){return source==Source.V2EX;}
                    public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){Board call=new Board(source,browser,result);boards.add(call);return ()->call.closed=true;}
                    public boolean offline(){return false;}
                });
                set(activity,"controller",controller);workers.add((ExecutorService)field(controller,"storage"));
                ((ArticlePreloader)field(controller,"preloader")).close();
                ArticlePreloader preloader=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){
                    public void load(Item item,Repository.Result<Document> result){bodies.add(new Body(item,result));}
                    public void cancel(){cancellations++;}
                },20,()->{},true);
                set(controller,"preloader",preloader);cache=new Repository(activity);controller.start();
            });
            await("three automatic source requests",()->boards.size()==3);
            if(warm){await("known candidate cards",()->((List<?>)field(activity,"feed")).size()==3);ui(()->controller.viewport(0));settle(1400);check(onUi(()->bodies.isEmpty()&&loading()&&boards.stream().filter(b->!b.closed).count()==3),"three occupied board slots postpone speculation even when known cards are readable");}
            ui(()->board(Source.THEPAPER,false).result.success(articles));
            await("partial cards",()->((List<?>)field(activity,"feed")).size()==3);
            await("body before all boards finish",()->!bodies.isEmpty());
            check(onUi(()->loading()&&boards.stream().filter(b->!b.closed).count()==2),"body acquisition starts while two ordinary board requests remain pending");
            Body first=bodies.get(0);ui(()->first.result.success(document(first)));
            await("shared body ready",()->cache.cachedArticle(first.item)!=null);
            check(onUi(()->ArticleVersion.matches(first.item,cache.cachedArticle(first.item))&&loading()),"partial supply has a matching prepared body before refresh completes or any click");
            await("second speculative body",()->bodies.size()==2);Body interrupted=bodies.get(1);int beforeCancel=cancellations;
            ui(()->board(Source.V2EX,false).result.failure("受控原生请求需要浏览器回退",403));
            await("browser fallback",()->boards.stream().anyMatch(b->b.source==Source.V2EX&&b.browser&&!b.closed));
            check(onUi(()->cancellations>beforeCancel&&!((ArticlePreloader)field(controller,"preloader")).busy()),"browser board acquisition preempts the speculative body pipeline");
            ui(()->interrupted.result.success(document(interrupted)));
            check(onUi(()->cache.cachedArticle(interrupted.item)==null),"late preempted body cannot enter the shared cache");
            int paused=bodies.size();ui(()->controller.viewport(0));settle(1400);
            check(onUi(()->bodies.size()==paused&&loading()),"viewport scheduling cannot restart prefetch while a browser board is active");
            ui(()->board(Source.V2EX,true).result.success(Collections.singletonList(new Item(Source.V2EX,"中文论坛受控热门讨论","https://www.v2ex.com/t/99008001",""))));
            await("body resumes before last ordinary board",()->bodies.size()>paused);
            Body resumed=bodies.get(bodies.size()-1);
            check(onUi(()->resumed.item.url.equals(interrupted.item.url)&&loading()&&!board(Source.WEIBO,false).closed),"browser completion resumes the interrupted viewport body while another ordinary source is pending");
            int stopped=bodies.size();ui(controller::stop);ui(()->resumed.result.success(document(resumed)));settle(1100);
            check(onUi(()->bodies.size()==stopped&&cache.cachedArticle(resumed.item)==null&&!loading()),"leaving cancels resumed work and rejects its late body without automatic restart");
        }catch(Throwable e){check(false,e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            ui(()->{if(activity!=null)activity.finish();if(cache!=null)cache.close();});test.waitForIdleSync();
            for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
            Bundle result=new Bundle();result.putString("report",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
