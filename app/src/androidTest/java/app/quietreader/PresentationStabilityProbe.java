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

/** Real controller and SQLite, with a deliberately never-completing body source. */
final class PresentationStabilityProbe {
    private final Instrumentation test;private DiscoveryActivity home;private DiscoveryController controller;private int checks,failures,startedBodies;private final List<ExecutorService> workers=new ArrayList<>();
    private final StringBuilder report=new StringBuilder();
    PresentationStabilityProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String key){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void set(Object owner,String key,Object value){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] r={false};ui(()->r[0]=condition.getAsBoolean());return r[0];}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(label);Thread.sleep(20);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(controller,"rows");}
    private Set<String> ids(){Set<String> ids=new HashSet<>();ui(()->rows().forEach(row->ids.add(row.eventId)));return ids;}
    void run(){String database="test-ui-stability-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){
                seed.ensureCandidatePolicy();for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);
                List<HotCandidate> pool=new ArrayList<>();for(int n=0;n<120;n++)pool.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"稳定性中文测试话题"+n,"","",false,n+1,null,now));
                seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),pool,now);seed.finishInterestSetup(List.of(),Map.of(),now);
            }
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryController old=(DiscoveryController)field(home,"controller");ExecutorService oldWorker=(ExecutorService)field(old,"storage");workers.add(oldWorker);ui(old::close);oldWorker.awaitTermination(8,TimeUnit.SECONDS);
            ui(()->{
                controller=new DiscoveryController(home,home,true,new HotSourceCollector.Transport(){public boolean offline(){return false;}public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> callback){return ()->{};}});
                set(home,"controller",controller);workers.add((ExecutorService)field(controller,"storage"));((ArticlePreloader)field(controller,"preloader")).close();
                set(controller,"preloader",new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> callback){startedBodies++;}public void cancel(){}},75,()->{},true));controller.start();
            });
            await("cold native feed with hung bodies",()->rows().size()==15);await("never-completing body source starts",()->startedBodies>0);
            Set<String> seen=ids();
            for(int round=0;round<5;round++){
                long before=(long)field(controller,"snapshot"),began=SystemClock.elapsedRealtime();
                ui(()->{controller.refreshRecommendations();controller.refreshRecommendations();controller.refreshRecommendations();});
                await("refresh must complete despite hung bodies",()->(long)field(controller,"snapshot")!=before&&!(boolean)field(controller,"explicitRefreshRunning"));
                Set<String> current=ids();check(current.size()==15,"hung source refresh keeps fifteen actual rows");check(Collections.disjoint(seen,current),"unclassified relevance rows rotate without repeating a prior batch");seen.addAll(current);
                check(SystemClock.elapsedRealtime()-began<4000,"refresh completes using local data instead of waiting for a network body");
                check(onUi(()->field(controller,"admission")==null),"no hidden whole-batch body gate survives refresh");
            }
            ui(()->{controller.stop();controller.start();});check(ids().size()==15,"background and foreground retain readable rows");
            ExecutorService storage=(ExecutorService)field(controller,"storage");storage.submit(()->{}).get(8,TimeUnit.SECONDS);
            Set<String> last=ids();ui(()->{controller.close();home.finish();});storage.awaitTermination(8,TimeUnit.SECONDS);
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(home,"controller");workers.add((ExecutorService)field(controller,"storage"));await("fresh launch restores a different usable batch",()->rows().size()==15);
            check(Collections.disjoint(seen,ids()),"fresh ordinary activity uses persisted delivery history rather than top fifteen");
            Set<String> reopened=ids();ui(controller::stop);ui(controller::start);check(reopened.equals(ids()),"returning to existing home preserves its batch instead of silently rotating");
            try(FeedStore read=new FeedStore(test.getTargetContext(),database)){check(read.profile(System.currentTimeMillis()).getJSONArray("feedback").length()==0,"delivery and test taps do not invent interest feedback");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(controller!=null)controller.close();if(home!=null)home.finish();});for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
