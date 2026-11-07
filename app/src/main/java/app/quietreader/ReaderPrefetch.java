package app.quietreader;

import android.app.Activity;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import app.quietreader.discovery.HotCandidate;
import static app.quietreader.Models.*;

/** Action-leased reading prefetch, with body-first preparation of a topic's posts. */
final class ReaderPrefetch implements AutoCloseable {
    private final Activity activity;
    private final Supplier<ArticlePreloader> loader;
    private final BooleanSupplier available;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ActionPrefetchBudget budget=new ActionPrefetchBudget();
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->new Thread(r,"reader-prefetch-space"));
    private ReaderPrefetchPlan plan;
    private List<Item> topicPosts=Collections.emptyList();
    private String topicUrl="";
    private boolean topicLease;
    private final ImagePreloader topicImages;
    private Consumer<Document> topicPrepared=doc->{};
    void observeTopic(Consumer<Document> observer){topicPrepared=observer;}
    void prioritize(Item post){List<Item> ordered=new ArrayList<>();ordered.add(post);for(Item item:topicPosts)if(!ArticleVersion.sameRequest(item,post))ordered.add(item);topicPosts=ordered;speculating=false;action();}
    private Item current;
    private boolean foreground,ready,speculating,closed;
    private long epoch,spaceAt;
    private DataStorageBudget.FreeSpace space;
    private Future<?> checking;
    private final Set<String> recorded=new LinkedHashSet<>();
    private final Runnable start=this::start;
    private final Runnable expire=()->{budget.clear();defer();};
    ReaderPrefetch(Activity activity,ReaderPrefetchPlan plan,Supplier<ArticlePreloader> loader,BooleanSupplier available){this.activity=activity;this.plan=plan;this.loader=loader;this.available=available;topicImages=new ImagePreloader(activity,()->eligible()&&!topicPosts.isEmpty());}
    void topic(Item item,Document doc){
        if(item.source!=Source.TIEBA||doc.topic==null)return;
        List<Item> posts=new ArrayList<>();for(Item post:doc.topic.choices)if(!post.video)posts.add(TiebaTopic.select(item,doc.topic,post));
        topicUrl=item.url;topicPosts=posts;topicImages.offer(posts);topicImages.prepared(item,doc);
        ArticlePreloader preloader=loader.get();if(preloader!=null)preloader.topic(doc);
        if(topicLease){topicLease=false;budget.reserve(SystemClock.elapsedRealtime(),posts.size());main.removeCallbacks(expire);main.postDelayed(expire,budget.remaining(SystemClock.elapsedRealtime()));}
    }
    /** Keep a pending body available to MainActivity.promote(), rather than duplicating it. */
    void opening(Item item){
        epoch++;main.removeCallbacks(start);main.removeCallbacks(expire);cancelCheck();topicImages.pause();budget.clear();ready=false;speculating=false;current=item;topicLease=true;
        if(!item.url.equals(topicUrl)){topicUrl="";topicPosts=Collections.emptyList();}
        ArticlePreloader preloader=loader.get();if(preloader!=null)preloader.observePrepared((ignoredItem,ignoredDoc)->{});
        if(foreground)action();
    }
    void resume(){if(closed)return;foreground=true;action();}
    void action(){
        if(!foreground||closed||current==null||topicPosts.isEmpty()&&(plan==null||!plan.matches(current)))return;
        budget.action(SystemClock.elapsedRealtime());main.removeCallbacks(expire);main.postDelayed(expire,budget.remaining(SystemClock.elapsedRealtime()));settled();
    }
    void ready(){ready=true;settled();}
    /** Rendering, scrolling and task callbacks may delay/check work, never renew credit. */
    void settled(){main.removeCallbacks(start);if(eligible())main.postDelayed(start,topicPosts.isEmpty()?650:150);}
    void rendering(){ready=false;defer();}
    void defer(){
        epoch++;main.removeCallbacks(start);cancelCheck();topicImages.pause();
        if(speculating){speculating=false;ArticlePreloader preloader=loader.get();if(preloader!=null){if(topicPosts.isEmpty())preloader.pause();else preloader.finishWindow();}}
    }
    void pause(){foreground=false;trim();}
    void trim(){budget.clear();main.removeCallbacks(expire);defer();ArticlePreloader preloader=loader.get();if(preloader!=null)preloader.trim();}
    void invalidate(){trim();plan=null;}
    private boolean eligible(){return !closed&&foreground&&ready&&current!=null&&(!topicPosts.isEmpty()&&current.url.equals(topicUrl)||plan!=null&&!plan.candidates.isEmpty()&&plan.matches(current))&&budget.active(SystemClock.elapsedRealtime())&&activity.hasWindowFocus()&&available.getAsBoolean()&&!activity.isFinishing()&&!activity.isDestroyed();}
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
        List<Item> items=new ArrayList<>(topicPosts);if(items.isEmpty()&&plan!=null)for(HotCandidate candidate:plan.candidates)items.add(candidate.readerItem());
        long request=epoch;
        preloader.observePrepared((item,document)->{
            if(!eligible()||request!=epoch)return;
            if(!topicPosts.isEmpty()){topicPrepared.accept(document);topicImages.prepared(item,document);topicImages.start();return;}
            if(plan==null)return;
            for(HotCandidate candidate:plan.candidates)if(ArticleVersion.sameRequest(candidate.readerItem(),item)){
                String key=candidate.id+"@"+document.cachedAt+"/"+document.cachedSession+"/"+document.cachedNamespace;
                if(recorded.add(key)){
                    if(recorded.size()>80)recorded.remove(recorded.iterator().next());
                    ReadingLanguageRecorder.record(activity,activity.getIntent().getStringExtra("test_database"),candidate,document);
                }break;
            }
        });
        speculating=true;preloader.offer(items,40,true);topicImages.start();
    }
    private void cancelCheck(){if(checking!=null){checking.cancel(true);checking=null;}worker.getQueue().clear();}
    @Override public void close(){if(closed)return;pause();closed=true;topicImages.close();main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}
