package app.quietreader.ai;

import android.app.*;
import android.os.*;
import app.quietreader.Models;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real two-model warm-up followed by platform/content changes; isolated SQLite. */
public final class RerankReuseProbe {
    private static final String POSITIVE="中国空间站开展生命科学实验",NEGATIVE="明星恋情娱乐八卦";
    private final Instrumentation test;private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private LocalRecommendation current;private int checks,failures;private final StringBuilder report=new StringBuilder();
    private static final class Result {
        final CountDownLatch done=new CountDownLatch(1);final AtomicInteger submissions=new AtomicInteger();
        List<FeedStore.Row> rows;int refined,encoded,total;boolean semantic,fully;String message;
    }
    public RerankReuseProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private JSONObject feedback(String id,String action,String text)throws Exception{return new JSONObject().put("id",id).put("action",action).put("scope","sample").put("sample",text);}
    private FeedStore.Row row(Models.Source source,int n,String text){HotCandidate c=new HotCandidate(source,"https://example.org/reuse/"+n,text,"","",false,n,null,System.currentTimeMillis());return new FeedStore.Row(c.id,c,"");}
    private Result run(FeedStore store,ModelFiles files,List<FeedStore.Row> neutral,SemanticOrder.Profile profile)throws Exception{
        Result result=new Result();
        test.runOnMainSync(()->{
            current=new LocalRecommendation(test.getTargetContext(),store,storage,neutral,profile,1,2,3,new LocalRecommendation.Listener(){
                public void status(String message){}
                public void complete(List<FeedStore.Row> rows,boolean semantic,int encoded,int total,int refined,String message){result.rows=rows;result.semantic=semantic;result.encoded=encoded;result.total=total;result.refined=refined;result.message=message;result.fully=current.fullyComputed();result.done.countDown();}
            },()->{result.submissions.incrementAndGet();return false;},new LocalRecommendation.ModelAccess(){public ModelFiles prepare(){return files;}public ModelFiles fallback(String failed){return null;}});
            current.start();
        });
        if(!result.done.await(35,TimeUnit.SECONDS))throw new IllegalStateException("Reuse inference timed out");test.waitForIdleSync();
        report.append("result refined=").append(result.refined).append(" submitChecks=").append(result.submissions.get()).append(" vectors=").append(result.encoded).append('/').append(result.total).append(" fully=").append(result.fully).append('\n');
        return result;
    }
    private long logitCount(FeedStore store)throws Exception{return storage.submit(()->{try(android.database.Cursor c=store.getWritableDatabase().rawQuery("SELECT COUNT(*) FROM reranks",null)){c.moveToFirst();return c.getLong(0);}}).get(5,TimeUnit.SECONDS);}
    public void run(){String database="test-rerank-reuse-"+UUID.randomUUID()+".db";FeedStore store=new FeedStore(test.getTargetContext(),database);
        try{
            ModelFiles files=ModelFiles.prepare(test.getTargetContext());
            List<FeedStore.Row> neutral=Arrays.asList(row(Models.Source.ZHIHU,1,POSITIVE),row(Models.Source.WEIBO,2,"中国空间站生命科学实验取得新进展"),row(Models.Source.HUPU,3,NEGATIVE));
            JSONArray base=new JSONArray().put(feedback("positive","MORE",POSITIVE)).put(feedback("negative","LESS",NEGATIVE));
            SemanticOrder.Profile initial=new SemanticOrder.Profile(base);Result first=run(store,files,neutral,initial);
            check(first.fully&&first.semantic&&first.refined>=2&&first.submissions.get()>0,"initial cold pairs execute actual embedding and reranking before reuse is tested");
            long original=logitCount(store);check(original>=2,"actual pair logits are persisted in SQLite");
            base.put(new JSONObject().put("id","platform").put("action","MORE").put("scope","source").put("target","WEIBO").put("sample","微博"));
            SemanticOrder.Profile platform=new SemanticOrder.Profile(base);check(!initial.signature.equals(platform.signature),"platform feedback changes the complete profile identity");
            Result second=run(store,files,neutral,platform);
            check(second.fully&&second.semantic&&second.encoded==second.total&&second.refined==0&&second.submissions.get()==0,"platform-only change reuses both model caches without submitting any inference");
            check(logitCount(store)==original,"platform feedback creates no duplicate profile-specific logit rows");
            check(second.rows.get(0).candidate.source==Models.Source.WEIBO,"current platform preference still changes final order after cached semantic scoring");
            check(second.rows.get(second.rows.size()-1).candidate.title.equals(NEGATIVE),"cached scores do not bypass the current explicit negative safeguard");
            base.put(feedback("reconsidered","MORE",NEGATIVE));SemanticOrder.Profile changed=new SemanticOrder.Profile(base);
            check(!changed.signature.equals(platform.signature),"explicit content reconsideration changes the semantic profile");
            Result third=run(store,files,neutral,changed);
            check(third.fully&&third.semantic&&third.refined==0&&third.submissions.get()==0&&logitCount(store)==original,"unrelated profile change retains identical pair logits while current grouping is rebuilt");
            check(third.rows.stream().anyMatch(row->row.candidate.title.equals(NEGATIVE)&&!row.reason.contains("少看")),"the later explicit positive is applied rather than retaining a cached profile penalty");
        }catch(Throwable e){check(false,e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            test.runOnMainSync(()->{if(current!=null)current.close();});
            try{storage.submit(store::close).get(5,TimeUnit.SECONDS);}catch(Exception e){check(false,"Store close failed");}
            storage.shutdown();test.getTargetContext().deleteDatabase(database);
            Bundle result=new Bundle();result.putString("report",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
