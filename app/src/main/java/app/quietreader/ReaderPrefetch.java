package app.quietreader;

import android.app.Activity;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import app.quietreader.discovery.HotCandidate;
import static app.quietreader.Models.*;

/** Action-leased reading prefetch. Shares the promoted loader; never starts models/images/video. */
final class ReaderPrefetch implements AutoCloseable {
    private final Activity activity;
    private final Supplier<ArticlePreloader> loader;
    private final BooleanSupplier available;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ActionPrefetchBudget budget=new ActionPrefetchBudget();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->new Thread(r,"reader-prefetch-space"));
    private ReaderPrefetchPlan plan;
    private Item current;
    private boolean foreground,ready,speculating,closed;
    private long epoch,spaceAt;
    private DataStorageBudget.FreeSpace space;
    private Future<?> checking;
    private final Set<String> recorded=new LinkedHashSet<>();
    private final Runnable start=this::start;
    private final Runnable expire=()->{budget.clear();defer();};
    ReaderPrefetch(Activity activity,ReaderPrefetchPlan plan,Supplier<ArticlePreloader> loader,BooleanSupplier available){this.activity=activity;this.plan=plan;this.loader=loader;this.available=available;}
    /** Keep a pending body available to MainActivity.promote(), rather than duplicating it. */
    void opening(Item item){
        epoch++;main.removeCallbacks(start);main.removeCallbacks(expire);cancelCheck();budget.clear();ready=false;speculating=false;current=item;
        ArticlePreloader preloader=loader.get();if(preloader!=null)preloader.observePrepared((ignoredItem,ignoredDoc)->{});
        if(foreground)action();
    }
    void resume(){if(closed)return;foreground=true;action();}
    void action(){
        if(!foreground||closed||plan==null||current==null||!plan.matches(current))return;
        budget.action(SystemClock.elapsedRealtime());main.removeCallbacks(expire);main.postDelayed(expire,ActionPrefetchBudget.WINDOW_MS);settled();
    }
    void ready(){ready=true;settled();}
    /** Rendering, scrolling and task callbacks may delay/check work, never renew credit. */
    void settled(){main.removeCallbacks(start);if(eligible())main.postDelayed(start,650);}
    void rendering(){ready=false;defer();}
    void defer(){
        epoch++;main.removeCallbacks(start);cancelCheck();
        if(speculating){speculating=false;ArticlePreloader preloader=loader.get();if(preloader!=null)preloader.pause();}
    }
    void pause(){foreground=false;trim();}
    void trim(){budget.clear();main.removeCallbacks(expire);defer();}
    void invalidate(){trim();plan=null;}
    private boolean eligible(){return !closed&&foreground&&ready&&plan!=null&&!plan.candidates.isEmpty()&&current!=null&&plan.matches(current)&&budget.active(SystemClock.elapsedRealtime())&&activity.hasWindowFocus()&&available.getAsBoolean()&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void start(){
        if(!eligible()||speculating||!app.quietreader.ai.RecommendationPolicy.restriction(activity).isEmpty())return;
        if(space!=null&&SystemClock.elapsedRealtime()-spaceAt<15_000){offer();return;}
        if(checking!=null)return;
        long request=epoch;
        checking=worker.submit(()->{
            DataStorageBudget.FreeSpace result=DataStorageBudget.freeSpace(activity.getDataDir());
            main.post(()->{if(closed||request!=epoch)return;checking=null;space=result;spaceAt=SystemClock.elapsedRealtime();if(eligible())offer();});
        });
    }
    private void offer(){
        if(!eligible()||space==null||!space.allowsPrefetch()||!app.quietreader.ai.RecommendationPolicy.restriction(activity).isEmpty())return;
        ArticlePreloader preloader=loader.get();if(preloader==null)return;
        List<Item> items=new ArrayList<>();for(HotCandidate candidate:plan.candidates)items.add(candidate.readerItem());
        long request=epoch;
        preloader.observePrepared((item,document)->{
            if(!eligible()||request!=epoch)return;
            for(HotCandidate candidate:plan.candidates)if(ArticleVersion.sameRequest(candidate.readerItem(),item)){
                String key=candidate.id+"@"+document.cachedAt+"/"+document.cachedSession+"/"+document.cachedNamespace;
                if(recorded.add(key)){
                    if(recorded.size()>80)recorded.remove(recorded.iterator().next());
                    ReadingLanguageRecorder.record(activity,activity.getIntent().getStringExtra("test_database"),candidate,document);
                }break;
            }
        });
        speculating=true;preloader.offer(items,40);
    }
    private void cancelCheck(){if(checking!=null){checking.cancel(true);checking=null;}worker.getQueue().clear();}
    @Override public void close(){if(closed)return;pause();closed=true;main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
