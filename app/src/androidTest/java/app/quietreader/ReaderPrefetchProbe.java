package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.MotionEvent;
import android.webkit.WebView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real reader actions, lifecycle and promotion; controlled transports, no live source claims. */
final class ReaderPrefetchProbe {
    private final Instrumentation test;
    private MainActivity activity;
    private Repository cache;
    private ReaderPrefetch scheduler;
    private ArticlePreloader preloader;
    private int checks,failures,cancels;
    private final List<Fetch> calls=new ArrayList<>();
    private final StringBuilder report=new StringBuilder();
    private static final class Fetch {final Item item;final Repository.Result<Document> callback;final ArticleSession session;Fetch(Activity a,Item i,Repository.Result<Document> cb){item=i;callback=cb;session=new ArticleSession(a,i);}}
    ReaderPrefetchProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void field(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void invoke(String name,Class<?>[] types,Object...args){try{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Deadline: "+stage+" calls="+calls.size()+" ready="+field(scheduler,"ready")+" foreground="+field(scheduler,"foreground")+" remaining="+((ActionPrefetchBudget)field(scheduler,"budget")).remaining(SystemClock.elapsedRealtime())+" focus="+activity.hasWindowFocus()+" restricted="+app.quietreader.ai.RecommendationPolicy.restriction(activity));Thread.sleep(30);}}
    private void passive(){invoke("readerReady",new Class<?>[]{WebView.class,String.class},field(activity,"readerWeb"),field(activity,"readerPageUrl"));scheduler.settled();}
    private void touch(){ui(()->{long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,200,300,0),up=MotionEvent.obtain(now,now+30,MotionEvent.ACTION_UP,200,300,0);try{activity.dispatchTouchEvent(down);activity.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}});}
    private Document body(Item item){Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","受控缓存正文，验证来源身份、阅读优先和生命周期，不代表平台实际文章。".repeat(12)));ArticleVersion.capture(item,doc);return doc;}
    private void finish(Fetch fetch){Document doc=body(fetch.item);if(!fetch.session.stamp(doc))throw new IllegalStateException("Fixture acquisition expired");fetch.callback.success(doc);}
    private void shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] b=new byte[1024];while(in.read(b)!=-1){}}}
    void run(){String database="test-ui-reader-prefetch-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();HotCandidate anchor=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/"+now,"中文阅读预缓存受控样本","","",false,11,null,now);
            List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<70;n++){HotCandidate c=n==10?anchor:new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"中文缓存候选"+n,"","",false,n+1,null,now);rows.add(new FeedStore.Row(c.id,c,""));}
            ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows,anchor);Item item=anchor.readerItem();
            // No plan at launch: inject the controlled loader before granting speculative work.
            ui(()->{cache=new Repository(test.getTargetContext());Document doc=body(item);doc.blocks.clear();doc.blocks.add(new Block("text","这是长篇阅读正文，用于确保准备邻居内容不会遮挡当前内容或提前触发回复续页。\n".repeat(200)));new ArticleSession(test.getTargetContext(),item).stamp(doc);cache.cacheArticle(doc);});
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("discovery_item",item.json().toString()).putExtra("discovery_candidate",anchor.json().toString()).putExtra("test_database",database));
            scheduler=(ReaderPrefetch)field(activity,"readerPrefetch");await("reader ready",()->(boolean)field(scheduler,"ready")&&activity.hasWindowFocus());
            check(onUi(()->(boolean)field(field(activity,"preloader"),"revalidateHidden")),"ordinary reader preloader requires current candidate metadata when reusing bodies");
            ui(()->{Item next=rows.get(11).candidate.readerItem(),old=new Item(next.source,"已经过期的旧标题",next.url,next.detail,false);Document stale=body(old);new ArticleSession(activity,old).stamp(stale);cache.cacheArticle(stale);});
            ui(()->{((ArticlePreloader)field(activity,"preloader")).close();preloader=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void load(Item i,Repository.Result<Document> cb){calls.add(new Fetch(activity,i,cb));}public void cancel(){cancels++;}},3,()->{},true);field(activity,"preloader",preloader);field(scheduler,"plan",plan);activity.getIntent().putExtra("reader_prefetch_plan",plan.encode());passive();});
            Thread.sleep(1200);check(onUi(()->calls.isEmpty()),"render/layout callbacks cannot create action credit or a speculative request");
            touch();await("action starts body",()->!calls.isEmpty());Fetch first=calls.get(0);
            check(first.item.url.equals(rows.get(11).candidate.originalUrl),"real reader touch reacquires the next frozen-feed event instead of accepting its stale-metadata cached body");
            check(onUi(()->((Deque<?>)field(preloader,"queue")).size()==39),"reader accepts exactly forty targets including its one active body, overriding the old three-item handoff window");
            ui(()->finish(first));await("second body",()->calls.size()==2);
            check(onUi(()->ArticleVersion.matches(first.item,cache.cachedArticle(first.item))),"prepared neighbor is reusable through the actual shared body cache");

            Fetch abandoned=calls.get(1);int beforeMore=cancels;Repository.Result<Document>[] reply=new Repository.Result[1];
            ui(()->{Document doc=(Document)field(activity,"reading");doc.nextUrl=item.url+"?p=2";doc.discussionPage=1;field(activity,"discussions",new DiscussionStream(activity,item,new DiscussionStream.Loader(){public void load(Item page,Repository.Result<Document> cb){reply[0]=cb;}public void cancel(){}public void close(){}}));invoke("loadMore",new Class<?>[]{});});
            check(onUi(()->(boolean)field(activity,"loadingMore")&&reply[0]!=null&&cancels>beforeMore&&!preloader.busy()),"actual comment continuation preempts and cancels speculative acquisition");
            ui(()->finish(abandoned));check(onUi(()->cache.cachedArticle(abandoned.item)==null),"late speculative completion cannot publish during explicit comment loading");
            int held=calls.size();ui(this::passive);Thread.sleep(1200);check(onUi(()->calls.size()==held),"paint callbacks while comments load cannot restart speculation");
            ui(()->{Document incoming=new Document();incoming.url=item.url+"?p=2";incoming.discussionPage=2;Section section=new Section("controlled-reply-2","受控回复",true);section.blocks.add(new Block("text","第二页回复已到达，保留正文及阅读位置。"));incoming.sections.add(section);incoming.blocks.addAll(section.blocks);new ArticleSession(activity,incoming.url).stamp(incoming);reply[0].success(incoming);});
            await("continuation appended",()->!(boolean)field(activity,"loadingMore")&&((Document)field(activity,"reading")).sections.stream().anyMatch(s->s.id.equals("controlled-reply-2")));
            await("speculation resumes",()->calls.size()>held);check(true,"successful comment append resumes only the remaining action lease, without a new action");

            // Complete more than three, then leave one pending to exercise expiry and cancellation.
            for(int n=0;n<7;n++){Fetch active=calls.get(calls.size()-1);int count=calls.size();ui(()->finish(active));await("next serial body "+n,()->calls.size()>count);}
            check(onUi(()->calls.stream().map(f->f.item.url).distinct().count()>3&&preloader.busy()),"reader window actually acquires beyond three topics with one serial speculative body");
            Fetch expiring=calls.get(calls.size()-1);int idleCount=calls.size(),idleCancels=cancels;long deadline=SystemClock.elapsedRealtime()+ActionPrefetchBudget.WINDOW_MS+1000;
            while(SystemClock.elapsedRealtime()<deadline){ui(this::passive);Thread.sleep(250);}
            check(onUi(()->!((ActionPrefetchBudget)field(scheduler,"budget")).active(SystemClock.elapsedRealtime())&&!preloader.busy()&&cancels>idleCancels&&calls.size()==idleCount),"repeated passive reader callbacks cannot extend twenty-second credit; expiry cancels pending body");
            ui(()->finish(expiring));check(onUi(()->cache.cachedArticle(expiring.item)==null),"expired reader response cannot enter the body cache");
            touch();await("new touch after idle",()->calls.size()>idleCount);check(true,"new user action resumes canceled work immediately after idle expiry");

            Fetch home=calls.get(calls.size()-1);int homeCount=calls.size();shell("input keyevent 3");await("real HOME",()->!(boolean)field(scheduler,"foreground"));
            ui(()->finish(home));Thread.sleep(1300);check(onUi(()->!preloader.busy()&&calls.size()==homeCount&&cache.cachedArticle(home.item)==null),"HOME stops reader speculation and rejects late background data");
            ui(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));await("foreground return",()->(boolean)field(scheduler,"foreground")&&calls.size()>homeCount);check(true,"real foreground return grants a fresh finite reading opportunity");

            Fetch promoted=calls.get(calls.size()-1);int beforePromotion=calls.size();ui(()->invoke("open",new Class<?>[]{Item.class,boolean.class},promoted.item,true));
            ui(scheduler::trim);check(onUi(()->preloader.canRetainForeground(promoted.item)&&calls.size()==beforePromotion),"opening a pending neighbor promotes the same acquisition; speculative trim preserves explicit reading");
            ui(()->finish(promoted));await("promoted body renders",()->field(activity,"reading")!=null&&((Document)field(activity,"reading")).url.equals(promoted.item.url));check(true,"promoted body reaches the actual reader without duplicate loading");
            ui(this::passive);Thread.sleep(1200);check(onUi(()->calls.size()==beforePromotion),"navigating outside the original topic cannot reuse its old prediction neighborhood");

            ui(()->invoke("goBack",new Class<?>[]{}));await("return to original article",()->((Item)field(activity,"current")).url.equals(item.url)&&field(activity,"reading")!=null);
            int beforeImport=calls.size();String importedUrl="https://www.thepaper.cn/newsDetail_forward_"+(now+1000);
            ui(()->{Item imported=new Item(Source.THEPAPER,"来源页另选的中文文章",importedUrl,"");Document doc=body(imported);new ArticleSession(activity,imported).stamp(doc);LoginActivity.pendingDocument=doc;invoke("onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},100,Activity.RESULT_OK,new Intent().putExtra("source",Source.THEPAPER.name()));});
            await("imported article renders",()->field(activity,"reading")!=null&&((Document)field(activity,"reading")).url.equals(importedUrl));
            ui(this::passive);Thread.sleep(1200);check(onUi(()->((Item)field(scheduler,"current")).url.equals(importedUrl)&&calls.size()==beforeImport),"source-browser import changes the prediction anchor and cannot restart the prior article neighborhood");

            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            try{ui(()->invoke("onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},101,Activity.RESULT_CANCELED,null));Activity next=test.waitForMonitorWithTimeout(monitor,8000);if(!(next instanceof MainActivity))throw new IllegalStateException("Settings recreation missing");activity=(MainActivity)next;scheduler=(ReaderPrefetch)field(activity,"readerPrefetch");}finally{test.removeMonitor(monitor);}
            await("settings return",()->(boolean)field(scheduler,"ready"));check(onUi(()->field(scheduler,"plan")==null&&!activity.getIntent().hasExtra("reader_prefetch_plan")),"settings return removes obsolete source/preferences prediction context across recreation");
            ActivityManager manager=(ActivityManager)test.getTargetContext().getSystemService(android.content.Context.ACTIVITY_SERVICE);List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();check(processes==null||processes.stream().noneMatch(p->p.processName.equals(test.getTargetContext().getPackageName()+":ranking")),"reading-only action cache does not start a model process");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{
            ui(()->{if(activity!=null)activity.finish();if(cache!=null)cache.close();});test.waitForIdleSync();
            try{ThreadPoolExecutor writer=(ThreadPoolExecutor)fieldStatic(ReadingLanguageRecorder.class,"writer");writer.submit(()->{}).get(5,TimeUnit.SECONDS);}catch(Exception ignored){}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private Object fieldStatic(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
}
