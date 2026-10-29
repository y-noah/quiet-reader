package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Synthetic token-boundary and SQLite evidence tests, never news-quality labels. */
final class EventInputCoverageProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    EventInputCoverageProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    static List<HotCandidate> longPair(){
        long now=System.currentTimeMillis();String prefix="王沪宁陪同金正恩访问丹东，".repeat(14);
        return Arrays.asList(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/881",prefix+"最终访问北京","","",false,1,null,now),new HotCandidate(Models.Source.WEIBO,"https://s.weibo.com/weibo?q=coverage-test",prefix+"最终访问上海","","",false,1,null,now));
    }
    static EventVerificationPlan.Task binding(List<HotCandidate> pair,String revision){float[] unit=new float[512];unit[0]=1;Map<String,float[]> vectors=new HashMap<>();for(HotCandidate c:pair)vectors.put(SemanticOrder.key(c.modelText()),unit);return EventVerificationPlan.create(pair,vectors,revision).get(0);}
    void run(){String database="test-event-coverage-"+UUID.randomUUID()+".db";
        try{
            ModelFiles files=ModelFiles.prepare(test.getTargetContext());
            JSONObject golden;try(InputStream in=test.getContext().getAssets().open("local-ai-golden.json")){golden=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            try(LocalModelEngine engine=new LocalModelEngine(files.directory(),files.manifest())){
                JSONArray fixtures=golden.getJSONArray("reranks");
                for(int n=0;n<fixtures.length();n++){
                    JSONObject fixture=fixtures.getJSONObject(n);String query=fixture.getString("query"),text=fixture.getString("text");long[] actual=engine.pair(query,text);JSONArray expected=fixture.getJSONArray("ids");boolean same=expected.length()==actual.length;for(int i=0;same&&i<actual.length;i++)same=actual[i]==expected.getLong(i);
                    check(same,"approved pair token IDs unchanged: "+n);LocalModelEngine.RerankResult score=engine.rerankResult(query,text);
                    check(Math.abs(score.logit-fixture.getDouble("logit"))<0.05,"approved INT8 score parity unchanged: "+n);
                    check(score.originalTokens==engine.tokenize("reranker",query).length+engine.tokenize("reranker",text).length&&score.usedTokens==actual.length&&score.complete==(score.originalTokens<=256),"coverage reports actual pre/post truncation token counts: "+n);
                }
                List<HotCandidate> pair=longPair();HotCandidate a=pair.get(0),b=pair.get(1);LocalModelEngine.RerankResult forward=engine.rerankResult(a.title,b.title),backward=engine.rerankResult(b.title,a.title);
                check(!forward.complete&&!backward.complete&&forward.originalTokens>256&&forward.usedTokens==256,"synthetic long titles explicitly report truncation in both directions");
                EventVerificationPlan.Task task=binding(pair,files.revision());List<FeedStore.Row> rows=CandidateOrder.forModels(pair,new JSONArray());
                check(EventGroupAssembly.apply(rows,new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(task,forward.logit,backward.logit,forward.complete&&backward.complete)),files.revision()).size()==2,"actual truncated logits cannot merge synthetic different ending claims");
                report.append("SYNTHETIC_LONG_PAIR forward=").append(forward.logit).append(" backward=").append(backward.logit).append(" original_tokens=").append(forward.originalTokens).append(" used_tokens=").append(forward.usedTokens).append("; not real news evidence\n");
                try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                    String key=EventRerankCacheKey.of(task,false),coverage=EventRerankCacheKey.coverage(key);long now=System.currentTimeMillis();
                    store.putRerank(key,99,now);check(store.eventRerank(key,now)==null,"legacy raw high score without coverage is a cache miss");
                    store.putEventRerank(key,10,false,now+1);FeedStore.EventRerank value=store.eventRerank(key,now+2);check(value!=null&&value.logit==10&&!value.complete,"known truncated result is cached without authorizing a merge");
                    store.getWritableDatabase().execSQL("CREATE TRIGGER fail_event_coverage BEFORE INSERT ON reranks WHEN NEW.key='"+coverage+"' BEGIN SELECT RAISE(ABORT,'controlled coverage failure'); END");
                    boolean rolledBack=false;try{store.putEventRerank(key,99,true,now+3);}catch(android.database.sqlite.SQLiteException expected){rolledBack=true;}
                    value=store.eventRerank(key,now+4);check(rolledBack&&value!=null&&value.logit==10&&!value.complete,"failed marker write rolls back raw score and keeps original pair atomic");store.getWritableDatabase().execSQL("DROP TRIGGER fail_event_coverage");
                    store.putRerank(coverage,0.5f,now+5);check(store.eventRerank(key,now+6)==null,"invalid coverage marker never qualifies a raw score");
                    store.putEventRerank(key,9,true,now+7);store.getWritableDatabase().delete("reranks","key=?",new String[]{coverage});check(store.eventRerank(key,now+8)==null,"evicted or missing marker cannot leave an eligible orphan score");
                    store.putEventRerank(key,9,true,now+9);value=store.eventRerank(key,now+10);check(value!=null&&value.complete&&value.logit==9,"complete-input result persists with its raw score");
                }
            }
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=synthetic x86_64 token/SQLite probe, not semantic recall, ARM or energy acceptance\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
