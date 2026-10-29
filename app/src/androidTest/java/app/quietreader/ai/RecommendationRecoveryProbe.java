package app.quietreader.ai;

import android.app.*;
import android.os.*;
import app.quietreader.Models;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Controlled model selection, real Messenger worker and SQLite. The deliberately
 * unapproved revision fails at worker validation, not inside native inference.
 * The replacement uses the real APK model; this is not quality/upgrade evidence. */
public final class RecommendationRecoveryProbe {
    private final Instrumentation test;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final StringBuilder report=new StringBuilder();private int checks,failures;
    private LocalRecommendation current;
    private static final String POSITIVE="中国空间站完成新的科学实验",NEGATIVE="明星恋情娱乐八卦";
    public RecommendationRecoveryProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private static FeedStore.Row row(int id,String title){HotCandidate c=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+id,title,"","",false,id,null,1);return new FeedStore.Row(c.id,c,"");}
    private static final class Result {
        final CountDownLatch done=new CountDownLatch(1);int count,total,refined,completions;boolean semantic,fully;String status="";
        final List<Long> deadlines=new ArrayList<>();
    }
    private long deadline(){try{java.lang.reflect.Field field=LocalRecommendation.class.getDeclaredField("deadline");field.setAccessible(true);return field.getLong(current);}catch(Exception error){throw new IllegalStateException(error);}}
    private void start(FeedStore store,SemanticOrder.Profile profile,List<FeedStore.Row> rows,LocalRecommendation.ModelAccess access,Result result)throws Exception{
        test.runOnMainSync(()->{
            current=new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,11,12,13,new LocalRecommendation.Listener(){
                public void status(String message){if(message.equals("正在按你的反馈整理"))result.deadlines.add(deadline());}
                public void complete(List<FeedStore.Row> ordered,boolean semantic,int count,int total,int refined,String status){result.count=count;result.total=total;result.refined=refined;result.semantic=semantic;result.status=status;result.completions++;result.fully=current.fullyComputed();result.done.countDown();}
            },()->false,access);current.start();
        });
    }
    private void await(Result result)throws Exception{if(!result.done.await(30,TimeUnit.SECONDS))throw new IllegalStateException("Recovery deadline exceeded");test.waitForIdleSync();report.append("result: ").append(result.status).append(" vectors=").append(result.count).append('/').append(result.total).append(" refined=").append(result.refined).append('\n');}
    private void seed(FeedStore store,ModelFiles failed)throws Exception{
        storage.submit(()->{store.clearDerivedCache();float[] vector=new float[512];vector[0]=1;store.putVector(SemanticOrder.key(POSITIVE),failed.revision(),"wordpiece-v1",vector,System.currentTimeMillis());}).get();
    }
    public void run(){String database="test-recommendation-recovery-"+UUID.randomUUID()+".db";FeedStore store=new FeedStore(test.getTargetContext(),database);
        try{
            ModelFiles good=ModelFiles.prepare(test.getTargetContext());
            ModelFiles bad=new ModelFiles(test.getTargetContext(),good.manifest()+"\n ");
            ModelFiles badReplacement=new ModelFiles(test.getTargetContext(),good.manifest()+"\n  ");
            SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(new JSONObject().put("id","positive").put("action","MORE").put("sample",POSITIVE)).put(new JSONObject().put("id","negative").put("action","LESS").put("sample",NEGATIVE)));
            List<FeedStore.Row> rows=Arrays.asList(row(1,NEGATIVE),row(2,POSITIVE),row(3,POSITIVE));
            AtomicInteger attempts=new AtomicInteger();seed(store,bad);Result success=new Result();
            start(store,profile,rows,new LocalRecommendation.ModelAccess(){
                public ModelFiles prepare(){return bad;}
                public ModelFiles fallback(String revision){check(revision.equals(bad.revision()),"fallback receives actual failed revision");attempts.incrementAndGet();return good;}
            },success);await(success);
            check(attempts.get()==1&&success.completions==1&&success.semantic&&success.fully&&success.count==2&&success.total==2&&success.refined==2,"one fallback resumes real embedding and reranking and publishes once");
            check(success.deadlines.size()==2&&success.deadlines.get(0).equals(success.deadlines.get(1)),"replacement shares original foreground deadline");
            boolean isolated=storage.submit(()->{
                float[] original=store.vector(FeedStore.vectorKey(SemanticOrder.key(POSITIVE),bad.revision(),"wordpiece-v1"),System.currentTimeMillis());
                float[] replacement=store.vector(FeedStore.vectorKey(SemanticOrder.key(POSITIVE),good.revision(),"wordpiece-v1"),System.currentTimeMillis());
                return original!=null&&original[0]==1&&replacement!=null&&!Arrays.equals(original,replacement);
            }).get();check(isolated,"failed-version cached vector survives only under its own key and is recomputed for replacement");

            Map<String,float[]> genuine=storage.submit(()->{
                Map<String,float[]> result=new HashMap<>();for(String text:Arrays.asList(POSITIVE,NEGATIVE)){String key=SemanticOrder.key(text);result.put(key,store.vector(FeedStore.vectorKey(key,good.revision(),"wordpiece-v1"),System.currentTimeMillis()));}return result;
            }).get();
            Thread.sleep(300);storage.submit(()->{store.clearDerivedCache();for(Map.Entry<String,float[]> entry:genuine.entrySet())store.putVector(entry.getKey(),bad.revision(),"wordpiece-v1",entry.getValue(),System.currentTimeMillis());}).get();
            Result reranker=new Result();attempts.set(0);
            start(store,profile,rows,new LocalRecommendation.ModelAccess(){
                public ModelFiles prepare(){return bad;}
                public ModelFiles fallback(String revision)throws Exception{
                    attempts.incrementAndGet();test.runOnMainSync(()->{try{java.lang.reflect.Field field=LocalRecommendation.class.getDeclaredField("kind");field.setAccessible(true);check("reranker".equals(field.get(current)),"fully cached failed-version embeddings reach reranker error boundary");}catch(Exception error){throw new IllegalStateException(error);}});return good;
                }
            },reranker);await(reranker);
            check(attempts.get()==1&&reranker.semantic&&reranker.fully&&reranker.count==2&&reranker.refined==2,"reranker failure restarts both stages with replacement revision");

            // Let the released worker retire before starting another independent window.
            Thread.sleep(300);seed(store,bad);attempts.set(0);Result twice=new Result();
            start(store,profile,rows,new LocalRecommendation.ModelAccess(){public ModelFiles prepare(){return bad;}public ModelFiles fallback(String revision){attempts.incrementAndGet();return badReplacement;}},twice);await(twice);
            check(attempts.get()==1&&twice.completions==1&&!twice.fully&&twice.count==0&&twice.refined==0&&twice.status.contains("暂不可用"),"replacement failure stops without another fallback or primary-version results");

            Thread.sleep(300);seed(store,bad);Result absent=new Result();
            start(store,profile,rows,new LocalRecommendation.ModelAccess(){public ModelFiles prepare(){return bad;}public ModelFiles fallback(String revision){return null;}},absent);await(absent);
            check(absent.count==1&&absent.refined==0&&!absent.fully&&absent.status.contains("暂不可用"),"no replacement retains partial results without reporting complete inference");

            Thread.sleep(300);seed(store,bad);Result cancelled=new Result();CountDownLatch checking=new CountDownLatch(1),interrupted=new CountDownLatch(1);
            start(store,profile,rows,new LocalRecommendation.ModelAccess(){public ModelFiles prepare(){return bad;}public ModelFiles fallback(String revision)throws Exception{checking.countDown();try{new CountDownLatch(1).await();}catch(InterruptedException expected){interrupted.countDown();throw expected;}return good;}},cancelled);
            check(checking.await(10,TimeUnit.SECONDS),"cancellation reaches replacement validation after worker error");
            test.runOnMainSync(()->current.close());
            check(interrupted.await(2,TimeUnit.SECONDS)&&!cancelled.done.await(500,TimeUnit.MILLISECONDS),"close interrupts validation and prevents late publication");

            Thread.sleep(300);seed(store,bad);Result expired=new Result();CountDownLatch validating=new CountDownLatch(1),release=new CountDownLatch(1);
            start(store,profile,rows,new LocalRecommendation.ModelAccess(){public ModelFiles prepare(){return bad;}public ModelFiles fallback(String revision)throws Exception{validating.countDown();release.await();return good;}},expired);
            if(!validating.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Missing expiry validation boundary");
            test.runOnMainSync(()->{try{java.lang.reflect.Field field=LocalRecommendation.class.getDeclaredField("deadline");field.setAccessible(true);field.setLong(current,SystemClock.elapsedRealtime()-1);}catch(Exception error){throw new IllegalStateException(error);}});
            release.countDown();await(expired);
            check(expired.status.contains("暂停")&&!expired.fully&&expired.count==1&&expired.refined==0&&expired.deadlines.size()==1,"expired foreground budget retains partial results and cannot start replacement inference");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{test.runOnMainSync(()->{if(current!=null)current.close();});try{storage.submit(store::close).get();}catch(Exception ignored){}storage.shutdownNow();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
