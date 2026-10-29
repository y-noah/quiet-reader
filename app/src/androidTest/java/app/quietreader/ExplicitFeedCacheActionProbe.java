package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Actual update button and SQLite snapshot, controlled body transport. */
final class ExplicitFeedCacheActionProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;private DiscoveryController controller;
    private int calls,checks,failures;private final StringBuilder report=new StringBuilder();
    ExplicitFeedCacheActionProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void invoke(String name){try{Method m=DiscoveryController.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(controller);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier value){boolean[] result={false};ui(()->result[0]=value.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(stage);Thread.sleep(30);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private ActionPrefetchBudget budget(){return (ActionPrefetchBudget)field(controller,"prefetchBudget");}
    private boolean active(){return budget().active(SystemClock.elapsedRealtime());}
    private boolean fromPool(List<FeedStore.Row> left,List<FeedStore.Row> right){Set<String> a=new HashSet<>(),b=new HashSet<>();for(FeedStore.Row row:left)for(HotCandidate item:row.entries)a.add(item.id+"@"+item.textHash);for(FeedStore.Row row:right)for(HotCandidate item:row.entries)b.add(item.id+"@"+item.textHash);return !a.isEmpty()&&left.size()<=FeedBatch.SIZE&&b.containsAll(a);}
    private void clear(){invoke("pausePreload");budget().clear();}
    private void publish(long id){try{Field f=DiscoveryController.class.getDeclaredField("pending");f.setAccessible(true);f.setLong(controller,id);invoke("state");}catch(Exception e){throw new IllegalStateException(e);}}
    @SuppressWarnings("unchecked") void run(){String database="test-ui-explicit-cache-"+UUID.randomUUID()+".db";ExecutorService worker=null;
        try{
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();
            for(int n=0;n<70;n++)candidates.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"中文预读按钮验证话题第"+n+"项","","",false,n+1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())store.enable(source,source==Source.THEPAPER);store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database));
            controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");
            ui(()->{try{((ArticlePreloader)field(controller,"preloader")).close();ArticlePreloader loader=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> result){calls++;}public void cancel(){}},40,()->{},true);Field f=DiscoveryController.class.getDeclaredField("preloader");f.setAccessible(true);f.set(controller,loader);}catch(Exception e){throw new IllegalStateException(e);}});
            await("initial foreground prefetch",()->calls>0&&!((List<?>)field(controller,"rows")).isEmpty()&&((List<?>)field(controller,"rows")).size()<=FeedBatch.SIZE);
            check(true,"actual foreground controller starts body transport with fresh source data");
            ui(this::clear);int before=calls;
            FeedStore store=(FeedStore)field(controller,"store");List<FeedStore.Row> rows=new ArrayList<>((List<FeedStore.Row>)field(controller,"allRows"));Collections.reverse(rows);
            long passive=worker.submit(()->store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now+1,"cold","all",rows)).get(5,TimeUnit.SECONDS);
            ui(()->{publish(passive);controller.applyPending(false);});
            await("passive snapshot applied",()->(long)field(controller,"snapshot")==passive);
            Thread.sleep(1100);
            check(onUi(()->!active()&&calls==before),"automatic snapshot application and layout do not grant cache credit");
            Collections.reverse(rows);long clicked=worker.submit(()->store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now+2,"cold","all",rows)).get(5,TimeUnit.SECONDS);
            ui(()->{clear();publish(clicked);((View)field(activity,"updates")).performClick();});
            check(onUi(this::active),"real update button grants a finite cache window after old credit was explicitly cleared");
            // Explicit presentation may commit a new identity to cool down
            // measured exposures; accepting the clicked pool need not reuse its id.
            await("clicked pool applied",()->(long)field(controller,"snapshot")>=clicked&&!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"pending")==0&&fromPool((List<FeedStore.Row>)field(controller,"rows"),rows));
            report.append("ACCEPTED_POOL clicked=").append(clicked).append(" actual=").append(field(controller,"snapshot")).append(" finite-members-belong-to-clicked-pool=true\n");
            long end=SystemClock.elapsedRealtime()+1800;while(SystemClock.elapsedRealtime()<end&&onUi(()->calls==before))Thread.sleep(30);
            check(onUi(()->calls>before),"accepted update starts new body transport without scrolling");
            int afterClick=calls;long expiry=SystemClock.elapsedRealtime()+((ActionPrefetchBudget)field(controller,"prefetchBudget")).remaining(SystemClock.elapsedRealtime())+1000;
            while(SystemClock.elapsedRealtime()<expiry){ui(()->controller.viewport(0));Thread.sleep(250);}
            check(onUi(()->!active()&&!((ArticlePreloader)field(controller,"preloader")).busy()&&calls==afterClick),"clicked window naturally expires despite repeated passive layout callbacks");
            ui(controller::stop);int stopped=calls;
            ui(()->{publish(clicked);((View)field(activity,"updates")).performClick();controller.viewport(0);});Thread.sleep(900);
            check(onUi(()->!active()&&calls==stopped),"background update callbacks cannot grant credit or start transport");
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
