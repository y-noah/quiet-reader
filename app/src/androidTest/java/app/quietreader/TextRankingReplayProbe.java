package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.ai.LocalRecommendation;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Fixed real candidate pool + declared developer test profile. No personal labels
 * or quality claims. Never changes the user's database, settings or feedback. */
final class TextRankingReplayProbe {
    private final Instrumentation test;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LocalRecommendation current;
    private int checks,failures;
    private final boolean feedbackReplay;
    private final StringBuilder report=new StringBuilder("REAL TEXT HOT CANDIDATES / DEVELOPER PROFILE REPLAY\nNot user preference evaluation; no P@20 or relevance labels inferred.\n");
    TextRankingReplayProbe(Instrumentation test){this(test,false);}
    TextRankingReplayProbe(Instrumentation test,boolean feedbackReplay){this.test=test;this.feedbackReplay=feedbackReplay;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private static final class Result {List<FeedStore.Row> rows,embedding=Collections.emptyList();RecommendationComparison comparison;int encoded,total,fine,logits;boolean semantic,complete;String status,revision="",resources;long elapsed;}
    private Result rank(FeedStore store,List<FeedStore.Row> rows,SemanticOrder.Profile profile)throws Exception{
        return rank(store,rows,profile,true,false);
    }
    private Result rank(FeedStore store,List<FeedStore.Row> rows,SemanticOrder.Profile profile,boolean compare,boolean forcePause)throws Exception{
        Result result=new Result();CountDownLatch done=new CountDownLatch(1);long start=SystemClock.elapsedRealtime();
        test.runOnMainSync(()->{LocalRecommendation.Listener listener=new LocalRecommendation.Listener(){
            public void status(String status){}
            public void comparison(RecommendationComparison value){result.comparison=value;result.embedding=value.embedding;result.revision=value.modelRevision;result.logits=value.finiteRerankScores;}
            public void complete(List<FeedStore.Row> ordered,boolean semantic,int encoded,int total,int fine,String status){result.rows=ordered;result.semantic=semantic;result.encoded=encoded;result.total=total;result.fine=fine;result.status=status;result.complete=current.fullyComputed();result.resources=current.resourceDescription();result.elapsed=SystemClock.elapsedRealtime()-start;done.countDown();}
        };current=forcePause?new LocalRecommendation(test.getTargetContext(),store,worker,rows,profile,1,1,1,listener,()->true):new LocalRecommendation(test.getTargetContext(),store,worker,rows,profile,1,1,1,listener);if(compare)current.requestComparison();current.start();});
        if(!done.await(45,TimeUnit.SECONDS))throw new IllegalStateException("Replay completion timeout");test.waitForIdleSync();return result;
    }
    private JSONArray identities(List<FeedStore.Row> rows){JSONArray result=new JSONArray();for(FeedStore.Row row:rows)result.put(row.eventId);return result;}
    private Set<String> identitySet(List<FeedStore.Row> rows){Set<String> result=new HashSet<>();for(FeedStore.Row row:rows)result.add(row.eventId);return result;}
    private void exportCapture(JSONObject corpus,List<FeedStore.Row> input,JSONArray feedback,SemanticOrder.Profile profile,long declaredAt,List<Result> runs)throws Exception{
        Result result=runs.get(runs.size()-1);Set<String> expected=identitySet(input);
        boolean valid=result.complete&&result.semantic&&result.encoded==result.total&&result.logits>0&&!result.revision.isEmpty()
                &&expected.size()==input.size()&&identitySet(result.embedding).equals(expected)&&identitySet(result.rows).equals(expected)
                &&result.embedding.size()==input.size()&&result.rows.size()==input.size();
        check(valid,"three-stage capture has complete vectors, actual finite rerank scores and identical unique event pools");
        if(!valid)return;
        List<FeedStore.Row> neutral=result.comparison.neutral;
        check(result.comparison.profileSignature.equals(profile.signature)&&result.comparison.generation==1&&result.comparison.profileVersion==1&&result.comparison.sourceVersion==1
                &&identities(result.comparison.dual).toString().equals(identities(result.rows).toString()),"public comparison callback retains same-run profile, versions and exact delivered dual order");
        JSONArray candidates=new JSONArray();
        for(FeedStore.Row row:input){JSONArray entries=new JSONArray();for(HotCandidate entry:row.entries)entries.put(entry.json());candidates.put(new JSONObject().put("event_key",row.eventId).put("primary",row.candidate.json()).put("entries",entries));}
        JSONArray work=new JSONArray();for(Result run:runs)work.put(new JSONObject().put("model_revision",run.revision).put("fully_computed",run.complete).put("elapsed_ms",run.elapsed).put("cached_vectors",run.encoded).put("total_vectors",run.total).put("new_rerank_items",run.fine).put("finite_rerank_scores",run.logits).put("reported_model_work",run.resources));
        JSONObject export=new JSONObject().put("capture_schema",1).put("purpose","technical three-order capture; not personal holdout evaluation")
                .put("label_origin","developer").put("holdout_qualified",false).put("labels",new JSONArray())
                .put("holdout_limit","Saved public corpus predates this declared developer profile; canonical event identities are not human adjudicated. No personal labels are inferred.")
                .put("source_captured_at_ms",corpus.getLong("capturedAt")).put("exported_at_ms",System.currentTimeMillis())
                .put("profile_declared_at_ms",declaredAt).put("profile_signature",profile.signature).put("developer_profile",feedback)
                .put("source_corpus_json_sha256",HotCandidate.hash(corpus.toString())).put("selection","first 100 neutral eligible events; not the full pool")
                .put("model_revision",result.revision).put("semantic_policy",SemanticOrder.VERSION).put("platform_policy",PlatformPreference.VERSION)
                .put("metadata_normalization",HotCandidate.NORMALIZATION).put("tokenizer_cache_version","wordpiece-v1")
                .put("embedding_order_method","production SemanticOrder from final-run vectors, empty rerank scores, same platform prior")
                .put("rankings",new JSONObject().put("neutral",identities(neutral)).put("embedding",identities(result.embedding)).put("dual",identities(result.rows)))
                .put("candidates",candidates).put("runs",work);
        File target=new File(test.getTargetContext().getExternalFilesDir(null),"text-ranking-capture.json"),temporary=new File(target.getParentFile(),"text-ranking-capture.partial");
        try(FileOutputStream stream=new FileOutputStream(temporary)){stream.write(export.toString().getBytes(StandardCharsets.UTF_8));stream.getFD().sync();}
        if(!temporary.renameTo(target))throw new IOException("Capture replacement failed");
        report.append("CAPTURE text-ranking-capture.json events=").append(input.size()).append(" finiteRerankScores=").append(result.logits).append(" no labels; holdout_qualified=false\n");
    }
    private void dump(String name,List<FeedStore.Row> rows){report.append(name).append('\n');for(int n=0;n<Math.min(20,rows.size());n++){FeedStore.Row row=rows.get(n);report.append(n+1).append(" ").append(row.candidate.source.name()).append(" ").append(row.candidate.title).append(" [").append(row.reason).append("]\n");}}
    private void similarities(FeedStore store,List<FeedStore.Row> rows,SemanticOrder.Profile profile)throws Exception{
        String revision=app.quietreader.ai.ModelFiles.prepare(test.getTargetContext()).revision();
        Map<String,float[]> vectors=worker.submit(()->{Map<String,float[]> found=new HashMap<>();for(SemanticOrder.Sample sample:profile.samples){float[] vector=store.vector(FeedStore.vectorKey(sample.key,revision,"wordpiece-v1"),System.currentTimeMillis());if(vector!=null)found.put(sample.key,vector);}for(FeedStore.Row row:rows){String key=SemanticOrder.key(row.candidate.modelText());float[] vector=store.vector(FeedStore.vectorKey(key,revision,"wordpiece-v1"),System.currentTimeMillis());if(vector!=null)found.put(key,vector);}return found;}).get(10,TimeUnit.SECONDS);
        List<SemanticOrder.Scored> scored=SemanticOrder.rank(rows,profile,vectors);int[] categories=new int[3];for(SemanticOrder.Scored row:scored)categories[row.category]++;
        report.append("coarseCategories positive/neutral/negative=").append(Arrays.toString(categories)).append('\n');
        scored.sort(Comparator.comparingDouble((SemanticOrder.Scored value)->-value.score));report.append("TOP10 RAW COARSE SCORES (diagnostic only, not reordered product output)\n");
        for(int n=0;n<Math.min(10,scored.size());n++){SemanticOrder.Scored row=scored.get(n);report.append(String.format(Locale.ROOT,"%.4f",row.score)).append(" category=").append(row.category).append(" ").append(row.row.candidate.title).append('\n');}
    }
    private void stage(String name,Result result)throws Exception{
        report.append("STAGE ").append(name).append(" elapsedMs=").append(result.elapsed).append(" vectors=").append(result.encoded).append('/').append(result.total).append(" fine=").append(result.fine).append(" fullyComputed=").append(result.complete).append(" status=").append(result.status).append('\n');
        app.quietreader.ai.ProcessMemoryBudget.Snapshot memory=worker.submit(()->app.quietreader.ai.ProcessMemoryBudget.sample(test.getTargetContext())).get(5,TimeUnit.SECONDS);
        report.append("sameUidPssKiB=").append(memory.pssKiB).append(" processes=").append(memory.measuredProcesses).append('/').append(memory.requestedProcesses).append(" includesMain=").append(memory.includesMain).append(" memoryPause=").append(memory.shouldPause()).append("; lower bound, isolated WebView may be invisible\n");dump(name+" TOP20",result.rows);
    }
    private int position(List<FeedStore.Row> rows,String id){for(int n=0;n<rows.size();n++)if(rows.get(n).eventId.equals(id))return n;return -1;}
    private void explainConflicts(FeedStore store,List<FeedStore.Row> baseline,SemanticOrder.Profile profile,Result result)throws Exception{
        String revision=app.quietreader.ai.ModelFiles.prepare(test.getTargetContext()).revision();
        Map<String,float[]> vectors=worker.submit(()->{Map<String,float[]> found=new HashMap<>();for(SemanticOrder.Sample sample:profile.samples){float[] vector=store.vector(FeedStore.vectorKey(sample.key,revision,"wordpiece-v1"),System.currentTimeMillis());if(vector!=null)found.put(sample.key,vector);}for(FeedStore.Row row:baseline){String key=SemanticOrder.key(row.candidate.modelText());float[] vector=store.vector(FeedStore.vectorKey(key,revision,"wordpiece-v1"),System.currentTimeMillis());if(vector!=null)found.put(key,vector);}return found;}).get(10,TimeUnit.SECONDS);
        List<SemanticOrder.Scored> coarse=SemanticOrder.rank(baseline,profile,vectors);List<FeedStore.Row> coarseRows=new ArrayList<>();for(SemanticOrder.Scored row:coarse)coarseRows.add(row.row);
        int conflicts=0;boolean held=true;for(SemanticOrder.Scored row:coarse)if(row.category==1&&row.match!=null&&!row.positiveLead){conflicts++;held&=position(result.rows,row.row.eventId)==position(coarseRows,row.row.eventId);}
        check(held,"positive-only fine ranking preserves all "+conflicts+" conflicted coarse positions");
        report.append("CONFLICT TRACE: finePosition coarsePosition positiveCosine negativeCosine; not probabilities\n");
        for(int n=0;n<Math.min(20,result.rows.size());n++){
            FeedStore.Row row=result.rows.get(n);float[] vector=vectors.get(SemanticOrder.key(row.candidate.modelText()));double positive=0,negative=0;
            if(vector!=null)for(SemanticOrder.Sample sample:profile.samples){float[] example=vectors.get(sample.key);if(example==null)continue;double cosine=SemanticOrder.cosine(vector,example);if(sample.positive)positive=Math.max(positive,cosine*(sample.read?0.65:sample.weak?0.75:1));else negative=Math.max(negative,cosine);}
            report.append(n+1).append(' ').append(position(coarseRows,row.eventId)+1).append(' ').append(String.format(Locale.ROOT,"%.4f %.4f",positive,negative)).append(' ').append(row.candidate.title).append('\n');
        }
    }
    private void feedbackReplay(FeedStore store,List<FeedStore.Row> all,List<FeedStore.Row> baseline,JSONArray feedback,Result reference)throws Exception{
        worker.submit(()->{for(int n=0;n<feedback.length();n++){JSONObject event=feedback.getJSONObject(n);store.feedback(event.getString("id"),event.getString("action"),"sample",event.getString("id"),System.currentTimeMillis(),null,event.getString("sample"));}return null;}).get(5,TimeUnit.SECONDS);
        FeedStore.Row chosen=reference.rows.get(0);String lessId="developer-actual-less";
        check(worker.submit(()->store.feedback(lessId,"LESS","sample",CandidateOrder.versionTarget(chosen.candidate),System.currentTimeMillis(),null,chosen.candidate.modelText(),chosen.candidate.source)).get(5,TimeUnit.SECONDS),"isolated actual feedback store accepts LESS for a real candidate");
        SemanticOrder.Profile less=worker.submit(()->new SemanticOrder.Profile(store.activeFeedback(System.currentTimeMillis()))).get(5,TimeUnit.SECONDS);
        Result reduced=rank(store,baseline,less);stage("REAL CANDIDATE LESS",reduced);
        check(reduced.complete&&position(reduced.rows,chosen.eventId)>=20,"explicit LESS removes the former first candidate from top20 on the fixed pool");
        check(worker.submit(()->store.undo(lessId,System.currentTimeMillis())).get(5,TimeUnit.SECONDS),"actual feedback undo succeeds");
        SemanticOrder.Profile undone=worker.submit(()->new SemanticOrder.Profile(store.activeFeedback(System.currentTimeMillis()))).get(5,TimeUnit.SECONDS);
        Result restored=rank(store,baseline,undone);stage("UNDO",restored);boolean same=restored.complete&&reference.complete;
        for(int n=0;n<reference.rows.size();n++)same&=reference.rows.get(n).eventId.equals(restored.rows.get(n).eventId);
        check(same,"undo rebuilds the complete original order, without opposite-feedback drift");
        JSONArray lifeFeedback=new JSONArray().put(new JSONObject().put("id","developer-life-positive").put("scope","sample").put("action","MORE").put("sample","婚姻家庭、育儿教育、日常生活中的实际问题与社会制度"))
            .put(new JSONObject().put("id","developer-sports-negative").put("scope","sample").put("action","LESS").put("sample","电竞游戏比赛、体育赛事胜负与明星八卦"));
        Result life=rank(store,baseline,new SemanticOrder.Profile(lifeFeedback));stage("ALTERNATIVE LIFE PROFILE",life);Set<String> techTop=new HashSet<>(),lifeTop=new HashSet<>();for(int n=0;n<20;n++){techTop.add(reference.rows.get(n).eventId);lifeTop.add(life.rows.get(n).eventId);}
        check(life.complete&&!techTop.equals(lifeTop),"different declared interests change the top20 event set on identical candidates");
        explainConflicts(store,baseline,new SemanticOrder.Profile(lifeFeedback),life);
        JSONArray focusedFeedback=new JSONArray().put(lifeFeedback.getJSONObject(0))
            .put(new JSONObject().put("id","developer-focused-gossip-negative").put("scope","sample").put("action","LESS").put("sample","明星恋情、婚恋绯闻和粉丝八卦"))
            .put(new JSONObject().put("id","developer-focused-sport-negative").put("scope","sample").put("action","LESS").put("sample","电竞游戏比赛和体育赛事胜负"));
        SemanticOrder.Profile focused=new SemanticOrder.Profile(focusedFeedback);Result focusedResult=rank(store,baseline,focused);stage("FOCUSED NEGATIVE EXAMPLES",focusedResult);explainConflicts(store,baseline,focused,focusedResult);
        check(focusedResult.complete,"separate declared negative interests complete within the same foreground policy");
        Set<String> used=new HashSet<>();for(FeedStore.Row row:baseline)for(HotCandidate entry:row.entries)used.add(entry.id);
        List<FeedStore.Row> unseen=new ArrayList<>();for(FeedStore.Row row:all){if(row.entries.stream().noneMatch(entry->used.contains(entry.id)))unseen.add(row);if(unseen.size()==100)break;}
        check(unseen.size()==100,"second technical pool has 100 candidate identities absent from first pool");
        Result other=rank(store,unseen,new SemanticOrder.Profile(feedback));stage("OTHER 100 IDENTITIES",other);
        report.append("Other pool is an identity-disjoint technical exercise, NOT independently event/time-labelled user holdout.\n");
        check(other.complete&&other.semantic,"models complete on the second real candidate pool");
    }
    void run(){
        String database="test-text-replay-"+UUID.randomUUID()+".db";FeedStore store=new FeedStore(test.getTargetContext(),database);
        try{
            JSONObject corpus;try(InputStream input=new FileInputStream(new File(test.getTargetContext().getExternalFilesDir(null),"text-hot-replay.json"))){byte[] bytes=input.readNBytes(4*1024*1024+1);if(bytes.length>4*1024*1024)throw new IllegalStateException("Corpus too large");corpus=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
            if(corpus.getInt("schema")!=1)throw new IllegalStateException("Corpus schema");List<HotCandidate> candidates=new ArrayList<>();JSONArray pool=corpus.getJSONArray("candidates");
            for(int n=0;n<pool.length();n++){HotCandidate candidate=HotCandidate.fromJson(pool.getJSONObject(n));if(!HotSourceCatalog.TEXT_HOT.contains(candidate.source)||!UrlPolicy.belongs(candidate.source,candidate.originalUrl))throw new IllegalStateException("Corpus source boundary");candidates.add(candidate);}
            long declaredAt=System.currentTimeMillis();JSONArray feedback=new JSONArray().put(new JSONObject().put("id","developer-tech-positive").put("scope","sample").put("action","MORE").put("sample","软件开发、程序员的技术实践、开源工具、电脑和手机硬件的实际使用体验"))
                .put(new JSONObject().put("id","developer-entertainment-negative").put("scope","sample").put("action","LESS").put("sample","娱乐明星的恋情、绯闻、粉丝之间的争吵"));
            List<FeedStore.Row> all=CandidateOrder.forModels(candidates,feedback);List<FeedStore.Row> baseline=new ArrayList<>(all.subList(0,Math.min(100,all.size())));
            report.append("capturedAt=").append(corpus.getLong("capturedAt")).append(" raw=").append(pool.length()).append(" eligibleEvents=").append(all.size()).append(" evaluated=").append(baseline.size()).append('\n');
            report.append("profile=developer tech positive / celebrity gossip negative; not the user's interests\n");check(baseline.size()==100,"fixed pool contains 100 eligible distinct events");dump("BASELINE TOP20",baseline);
            SemanticOrder.Profile profile=new SemanticOrder.Profile(feedback);List<Result> runs=new ArrayList<>();
            for(int round=0;round<2;round++){
                Result result=rank(store,baseline,profile);runs.add(result);report.append("round=").append(round+1).append(" elapsedMs=").append(result.elapsed).append(" vectors=").append(result.encoded).append('/').append(result.total).append(" fine=").append(result.fine).append(" fullyComputed=").append(result.complete).append(" semantic=").append(result.semantic).append(" status=").append(result.status).append('\n');
                Set<String> ids=new HashSet<>();for(FeedStore.Row row:result.rows)ids.add(row.eventId);Set<String> expected=new HashSet<>();for(FeedStore.Row row:baseline)expected.add(row.eventId);
                check(result.rows.size()==baseline.size()&&ids.equals(expected),"round "+(round+1)+": ranking retains the exact candidate event set");
                int changed=0;for(int n=0;n<result.rows.size();n++)if(!result.rows.get(n).eventId.equals(baseline.get(n).eventId))changed++;report.append("changedPositions=").append(changed).append('\n');dump("RANKED TOP20 ROUND "+(round+1),result.rows);
                if(round==0)similarities(store,baseline,profile);
            }
            Result first=runs.get(0),second=runs.get(1);if(first.complete&&second.complete){boolean same=true;for(int n=0;n<first.rows.size();n++)same&=first.rows.get(n).eventId.equals(second.rows.get(n).eventId);check(same,"fully computed warm replay preserves fixed ordering");}
            long feedbackRows=worker.submit(()->{try(android.database.Cursor c=store.getWritableDatabase().rawQuery("SELECT COUNT(*) FROM feedback",null)){c.moveToFirst();return c.getLong(0);}}).get(5,TimeUnit.SECONDS);check(feedbackRows==0,"developer test profile is never persisted as user feedback");
            exportCapture(corpus,baseline,feedback,profile,declaredAt,runs);
            Result ordinary=rank(store,baseline,profile,false,false);
            check(ordinary.complete&&ordinary.comparison==null&&identities(ordinary.rows).toString().equals(identities(second.rows).toString()),"ordinary unrequested ranking emits no comparison and retains the same complete order");
            HotCandidate fresh=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/99999999","中文缺失向量暂停验证 "+UUID.randomUUID(),"用于检查不完整计算不产生对比记录","",false,1,null,System.currentTimeMillis());
            Result paused=rank(store,List.of(new FeedStore.Row(fresh.id,fresh,"")),profile,true,true);
            check(!paused.complete&&paused.comparison==null&&paused.fine==0,"memory-paused incomplete inference never emits a complete comparison");
            if(feedbackReplay)feedbackReplay(store,all,baseline,feedback,second);
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{if(current!=null)current.close();});try{worker.submit(store::close).get(5,TimeUnit.SECONDS);}catch(Exception ignored){}worker.shutdown();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
