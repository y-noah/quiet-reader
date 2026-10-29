package app.quietreader.ai;

import android.app.*;
import android.os.*;
import app.quietreader.Models.Source;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual stored feedback and LocalRecommendation completion with model preparation unavailable. */
public final class FeedbackFallbackProbe {
    private final Instrumentation test;private LocalRecommendation current;private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final java.util.concurrent.atomic.AtomicInteger preparations=new java.util.concurrent.atomic.AtomicInteger();
    public FeedbackFallbackProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private static final class Result {final CountDownLatch done=new CountDownLatch(1);List<FeedStore.Row> rows;boolean semantic,continueAutomatically;int encoded,refined;}
    private Result run(FeedStore store,List<HotCandidate> candidates)throws Exception{
        JSONArray feedback=store.activeFeedback(System.currentTimeMillis());SemanticOrder.Profile profile=new SemanticOrder.Profile(feedback);Result result=new Result();int before=preparations.get();
        test.runOnMainSync(()->{current=new LocalRecommendation(test.getTargetContext(),store,storage,CandidateOrder.forModels(candidates,feedback),profile,1,1,1,new LocalRecommendation.Listener(){
            public void status(String message){}
            public void complete(List<FeedStore.Row> rows,boolean semantic,int encoded,int total,int refined,String status){result.rows=rows;result.semantic=semantic;result.encoded=encoded;result.refined=refined;result.continueAutomatically=current.canContinueAutomatically();result.done.countDown();}
        },()->false,new LocalRecommendation.ModelAccess(){public ModelFiles prepare()throws Exception{preparations.incrementAndGet();throw new java.io.IOException("controlled unavailable model fixture");}public ModelFiles fallback(String revision){throw new IllegalStateException("Preparation failure must not retry inference");}});current.start();});
        if(!result.done.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Feedback fallback deadline");test.waitForIdleSync();if(preparations.get()!=before+1)throw new IllegalStateException("Controlled preparation failure was not exercised exactly once");return result;
    }
    public void run(){String database="test-feedback-fallback-"+UUID.randomUUID()+".db";
        HotCandidate dislike=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/99007001","明确少看的中文内容","","",false,1,null,1),other=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/99007002","另一条尚无偏好的中文内容","","",false,2,null,1);List<HotCandidate> candidates=List.of(dislike,other);
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            store.feedback("negative","LESS","sample",CandidateOrder.versionTarget(dislike),System.currentTimeMillis(),null,dislike.modelText(),dislike.source);
            check(CandidateOrder.arrange(candidates,store.activeFeedback(System.currentTimeMillis()),false).get(1).candidate.id.equals(dislike.id),"base snapshot respects exact dislike before any model pipeline is started");
            Result first=run(store,candidates);check(!first.continueAutomatically,"unavailable models with no new computation do not request an automatic retry");check(first.rows.get(1).candidate.id.equals(dislike.id),"exact known dislike stays below unknown content when model preparation fails");
            check(!first.semantic&&first.encoded==0&&first.refined==0,"fallback remains honestly nonsemantic with no model inference");
            store.feedback("platform-more","MORE","source","V2EX",System.currentTimeMillis(),null,"V2EX",Source.V2EX);app.quietreader.discovery.LegacySavedFixture.saveWithInterest(store,dislike.id,dislike,System.currentTimeMillis());
            check(CandidateOrder.arrange(candidates,store.activeFeedback(System.currentTimeMillis()),false).get(1).candidate.id.equals(dislike.id),"model-free base snapshot keeps content dislike ahead of platform and weak save evidence");
            Result preferred=run(store,candidates);check(preferred.rows.get(1).candidate.id.equals(dislike.id)&&preferred.rows.get(1).reason.contains("少看"),"platform MORE and later weak save cannot promote exact content dislike without vectors");
            store.undo("negative",System.currentTimeMillis());Result restored=run(store,candidates);check(restored.rows.get(0).candidate.id.equals(dislike.id),"undo removes the exact dislike guard and restores permitted platform preference");
            store.feedback("negative-again","LESS","sample",CandidateOrder.versionTarget(dislike),System.currentTimeMillis(),null,dislike.modelText(),dislike.source);
            for(int n=0;n<40;n++)store.feedback("other-negative-"+n,"LESS","sample",HotCandidate.hash("other-negative-"+n),System.currentTimeMillis(),null,"其他独立负样本"+n,Source.ZHIHU);
            SemanticOrder.Profile bounded=new SemanticOrder.Profile(store.activeFeedback(System.currentTimeMillis()));check(bounded.samples.stream().filter(sample->!sample.positive).count()==32&&bounded.samples.stream().noneMatch(sample->sample.key.equals(SemanticOrder.key(dislike.modelText()))),"older exact dislike can leave the 32 model samples without increasing inference budget");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){Result persisted=run(reopened,candidates);check(persisted.rows.get(1).candidate.id.equals(dislike.id),"reopened SQLite feedback preserves the same unavailable-model negative guard");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{if(current!=null)current.close();});storage.shutdown();try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
