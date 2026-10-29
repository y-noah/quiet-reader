package app.quietreader.ai;

import android.app.*;
import android.os.*;
import app.quietreader.Models;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Distinct real execution stages, not three orders derived after dual inference. */
public final class RecommendationStagesProbe {
    private final Instrumentation test;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final StringBuilder report=new StringBuilder("EXECUTION STAGES: synthetic fixtures; no energy or preference-quality claim\n");
    private int checks,failures;
    private LocalRecommendation current;
    public RecommendationStagesProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private static final class Result {
        final CountDownLatch done=new CountDownLatch(1);
        final AtomicInteger prepares=new AtomicInteger(),submissions=new AtomicInteger();
        List<FeedStore.Row> rows;int encoded,total,refined;boolean semantic,fully,continueAutomatically;String work,status;
    }
    private long count(FeedStore store,String table)throws Exception{
        return storage.submit(()->{try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}).get(5,TimeUnit.SECONDS);
    }
    private Result rank(FeedStore store,List<FeedStore.Row> rows,SemanticOrder.Profile profile,LocalRecommendation.Stages stages)throws Exception{
        Result r=new Result();
        test.runOnMainSync(()->{
            LocalRecommendation.ModelAccess access=new LocalRecommendation.ModelAccess(){
                public ModelFiles prepare()throws Exception{r.prepares.incrementAndGet();if(stages==LocalRecommendation.Stages.NONE)throw new IllegalStateException("Baseline must not prepare models");return ModelFiles.prepare(test.getTargetContext());}
                public ModelFiles fallback(String revision){return null;}
            };
            LocalRecommendation.Listener listener=new LocalRecommendation.Listener(){
                public void status(String message){}
                public void complete(List<FeedStore.Row> ordered,boolean semantic,int encoded,int total,int refined,String status){
                    r.rows=ordered;r.semantic=semantic;r.encoded=encoded;r.total=total;r.refined=refined;r.fully=current.fullyComputed();r.continueAutomatically=current.canContinueAutomatically();r.work=current.resourceDescription();r.status=status;r.done.countDown();
                }
            };
            // DUAL intentionally uses the pre-existing constructor to check the default.
            current=stages==LocalRecommendation.Stages.DUAL
                ?new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,1,1,1,listener,()->{r.submissions.incrementAndGet();return false;},access)
                :new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,1,1,1,listener,()->{r.submissions.incrementAndGet();return false;},access,stages);
            current.start();
        });
        if(!r.done.await(60,TimeUnit.SECONDS))throw new IllegalStateException("Stage completion timeout");
        test.waitForIdleSync();report.append(stages).append(" prepare=").append(r.prepares).append(" submissions=").append(r.submissions).append(" encoded=").append(r.encoded).append('/').append(r.total).append(" refined=").append(r.refined).append(" fully=").append(r.fully).append(" status=").append(r.status).append(" work=").append(r.work).append('\n');
        return r;
    }
    public void run(){
        List<String> databases=new ArrayList<>();List<FeedStore> stores=new ArrayList<>();
        try{
            String[] titles={"中国空间站开展生命科学实验","中国空间站生命科学实验取得新进展","明星恋情娱乐八卦"};
            List<FeedStore.Row> rows=new ArrayList<>();int n=0;
            for(String title:titles){HotCandidate c=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+(++n),title,"","",false,n,null,1);rows.add(new FeedStore.Row(c.id,c,""));}
            SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray()
                .put(new JSONObject().put("id","more").put("scope","sample").put("action","MORE").put("sample",titles[0]))
                .put(new JSONObject().put("id","less").put("scope","sample").put("action","LESS").put("sample",titles[2])));
            for(LocalRecommendation.Stages stages:LocalRecommendation.Stages.values()){
                String name="test-execution-stages-"+UUID.randomUUID()+".db";databases.add(name);
                FeedStore store=new FeedStore(test.getTargetContext(),name);stores.add(store);
                Result r=rank(store,rows,profile,stages);
                // Preserve the production foreground window, including partial
                // caches. A cold stage may need an explicit subsequent window.
                for(int attempt=1;stages==LocalRecommendation.Stages.DUAL&&!r.fully&&attempt<4;attempt++){
                    report.append("continuationWindow=").append(attempt+1).append('\n');
                    r=rank(store,rows,profile,stages);
                }
                check(!r.continueAutomatically,"completed "+stages+" requests no additional automatic window");
                check(r.fully&&r.rows.size()==rows.size(),stages+" completes and preserves the complete candidate pool");
                check(r.rows.get(r.rows.size()-1).candidate.title.equals(titles[2]),stages+" preserves explicit-negative safeguards");
                if(stages==LocalRecommendation.Stages.NONE){
                    check(r.prepares.get()==0&&r.submissions.get()==0&&!r.semantic&&r.refined==0,"NONE performs no model preparation or service submission");
                    check(count(store,"embeddings")==0&&count(store,"reranks")==0,"NONE writes no model cache rows");
                }else if(stages==LocalRecommendation.Stages.EMBEDDING){
                    check(r.prepares.get()==1&&r.submissions.get()>0&&r.semantic&&r.encoded==r.total&&r.total>0,"EMBEDDING executes real complete vectors");
                    check(r.refined==0&&count(store,"reranks")==0&&count(store,"embeddings")>0,"EMBEDDING bypasses reranker and writes vectors only");
                    Result warm=rank(store,rows,profile,stages);
                    check(warm.fully&&!warm.continueAutomatically&&warm.semantic&&warm.submissions.get()==0&&warm.refined==0,"warm EMBEDDING reuses vectors without either model submission");
                }else{
                    check(r.semantic&&r.encoded==r.total&&count(store,"reranks")>=2,"ordinary default constructor executes both actual stages");
                }
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{
            test.runOnMainSync(()->{if(current!=null)current.close();});storage.shutdown();try{if(!storage.awaitTermination(10,TimeUnit.SECONDS))storage.shutdownNow();}catch(InterruptedException ignored){storage.shutdownNow();Thread.currentThread().interrupt();}
            for(FeedStore store:stores)store.close();for(String name:databases)test.getTargetContext().deleteDatabase(name);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
