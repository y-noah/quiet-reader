package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Actual bundled models and SQLite; deliberately small controlled examples.
 * This verifies plumbing/cache reuse, not general recommendation quality. */
final class RecommendationProbe {
    private final Instrumentation test;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final StringBuilder report=new StringBuilder();
    private int checks,failures;
    private LocalRecommendation current;
    RecommendationProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private static FeedStore.Row row(int id,String text){HotCandidate c=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+id,text,"","",false,id,null,1);return new FeedStore.Row(c.id,c,"");}
    private static final class Result {List<FeedStore.Row> rows;boolean semantic;int count,total,fine;String status;}
    private String shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);java.io.InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[1024];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);return output.toString("UTF-8").trim();
        }
    }
    private void resourceSignals()throws Exception{
        app.quietreader.ai.ProcessMemoryBudget.Snapshot memory=app.quietreader.ai.ProcessMemoryBudget.sample(test.getTargetContext());
        check(memory.includesMain&&memory.pssKiB>0&&memory.measuredProcesses>0,"worker PSS sample observes the app process through public API");
        check(memory==app.quietreader.ai.ProcessMemoryBudget.sample(test.getTargetContext()),"adjacent inference boundaries reuse the bounded memory sample");
        test.runOnMainSync(()->{boolean rejected=false;try{app.quietreader.ai.ProcessMemoryBudget.sample(test.getTargetContext());}catch(IllegalStateException expected){rejected=true;}check(rejected,"PSS sampling refuses to block the UI thread");});
        if(!"1".equals(shell("getprop ro.kernel.qemu")))throw new IllegalStateException("Resource injection is emulator-only");
        RecommendationPolicy.Watch[] watch=new RecommendationPolicy.Watch[1];CountDownLatch notified=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        try{
            shell("cmd thermalservice override-status 0");
            test.runOnMainSync(()->{watch[0]=new RecommendationPolicy.Watch(test.getTargetContext(),reason->{calls.incrementAndGet();if(reason.contains("温度"))notified.countDown();});watch[0].start();});
            shell("cmd thermalservice override-status 2");
            check(notified.await(5,TimeUnit.SECONDS),"public thermal callback interrupts without waiting for next batch");
            test.runOnMainSync(()->watch[0].close());int before=calls.get();
            shell("cmd thermalservice override-status 0");shell("cmd thermalservice override-status 3");test.waitForIdleSync();Thread.sleep(200);
            check(calls.get()==before,"closed resource watcher receives no further thermal notifications");
        }finally{test.runOnMainSync(()->{if(watch[0]!=null)watch[0].close();});shell("cmd thermalservice reset");}
    }
    private Result runOnce(FeedStore store,List<FeedStore.Row> rows,SemanticOrder.Profile profile)throws Exception{
        Result result=new Result();CountDownLatch done=new CountDownLatch(1);
        test.runOnMainSync(()->{
            current=new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,1,0,0,new LocalRecommendation.Listener(){
                public void status(String message){}
                public void complete(List<FeedStore.Row> ordered,boolean semantic,int count,int total,int fine,String status){result.rows=ordered;result.semantic=semantic;result.count=count;result.total=total;result.fine=fine;result.status=status;done.countDown();}
            });current.start();
        });
        if(!done.await(45,TimeUnit.SECONDS))throw new IllegalStateException("Recommendation deadline exceeded");
        test.waitForIdleSync();report.append("result: ").append(result.status).append(" vectors=").append(result.count).append('/').append(result.total).append(" reranked=").append(result.fine).append('\n');return result;
    }
    void run(){String database="test-semantic-"+UUID.randomUUID()+".db";FeedStore store=new FeedStore(test.getTargetContext(),database);
        try{
            String positive="中国空间站完成新的科学实验",negative="明星恋情娱乐八卦";
            SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(new JSONObject().put("id","positive").put("action","MORE").put("sample",positive)).put(new JSONObject().put("id","negative").put("action","LESS").put("sample",negative)));
            List<FeedStore.Row> rows=Arrays.asList(row(1,negative),row(2,positive),row(3,positive));
            Result first=runOnce(store,rows,profile);
            check(first.semantic&&first.count==first.total&&first.total==2,"actual embedding pipeline caches unique inputs");
            check(first.rows.get(2).candidate.title.equals(negative),"actual negative example moves behind positive examples");
            check(first.fine==2,"actual reranker executes for the matched query group");
            long used=storage.submit(()->{try(android.database.Cursor c=store.getWritableDatabase().rawQuery("SELECT COUNT(*) FROM reranks",null)){c.moveToFirst();return c.getLong(0);}}).get();
            check(used>0,"reranker results persist in isolated SQLite");
            Result second=runOnce(store,rows,profile);
            check(second.semantic&&second.count==2&&second.fine==0,"warm pipeline reuses embeddings and reranker cache");
            check(second.rows.get(0).eventId.equals(first.rows.get(0).eventId),"warm result preserves ordering");
            storage.submit(()->{store.clearDerivedCache();return null;}).get();
            CountDownLatch pressureDone=new CountDownLatch(1);boolean[] pressureResult={false};java.util.concurrent.atomic.AtomicInteger pressureChecks=new java.util.concurrent.atomic.AtomicInteger();
            test.runOnMainSync(()->{
                current=new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,3,0,0,new LocalRecommendation.Listener(){
                    public void status(String value){}
                    public void complete(List<FeedStore.Row> result,boolean semantic,int encoded,int total,int refined,String status){pressureResult[0]=!semantic&&encoded==0&&refined==0&&result.size()==rows.size()&&status.contains("内存");pressureDone.countDown();}
                },()->{pressureChecks.incrementAndGet();return true;});current.start();
            });
            check(pressureDone.await(10,TimeUnit.SECONDS)&&pressureResult[0]&&pressureChecks.get()==1,"injected memory pressure stops before inference and retains readable candidates");
            CountDownLatch published=new CountDownLatch(1);
            test.runOnMainSync(()->{current=new LocalRecommendation(test.getTargetContext(),store,storage,rows,profile,2,0,0,new LocalRecommendation.Listener(){public void status(String s){}public void complete(List<FeedStore.Row> r,boolean s,int c,int t,int f,String m){published.countDown();}});current.start();current.close();});
            check(!published.await(500,TimeUnit.MILLISECONDS),"cancelled installation cannot publish a feed");
            resourceSignals();
        }catch(Throwable ex){failures++;report.append("ERROR ").append(ex.toString()).append('\n');}
        finally{test.runOnMainSync(()->{if(current!=null)current.close();});try{storage.submit(store::close).get();}catch(Exception ignored){}storage.shutdown();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());result.putInt("failures",failures);test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
