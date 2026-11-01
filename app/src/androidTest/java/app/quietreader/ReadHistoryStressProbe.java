package app.quietreader;

import android.app.*;
import android.os.*;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Deterministic multi-source stress on real SQLite; never runs on a user's phone. */
final class ReadHistoryStressProbe {
    private final Instrumentation test;private int checks,failures;private StringBuilder report=new StringBuilder();
    ReadHistoryStressProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String text){checks++;if(!ok){failures++;report.append("FAIL ").append(text).append('\n');}}
    private String url(Source source,int n){switch(source){case V2EX:return "https://www.v2ex.com/t/"+n+"#reply10";case ZHIHU:return "https://www.zhihu.com/question/"+n;case ITHOME:return "https://www.ithome.com/0/900/"+n+".htm";case CLS:return "https://www.cls.cn/detail/"+n;case THEPAPER:return "https://www.thepaper.cn/newsDetail_forward_"+n;default:return "https://www.bbc.com/zhongwen/articles/c"+n;}}
    private List<FeedStore.Row> all(FeedStore store,long id)throws Exception{List<FeedStore.Row> rows=new ArrayList<>();int count=store.snapshotInfo(id).getInt("count");for(int n=0;n<count;n+=200)rows.addAll(store.readSnapshot(id,200,n));return rows;}
    void run(){String database="test-ui-read-stress-"+UUID.randomUUID()+".db";long start=SystemClock.elapsedRealtime();
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("emulator only");
            Source[] sources={Source.V2EX,Source.ZHIHU,Source.ITHOME,Source.CLS,Source.THEPAPER,Source.BBC};int[] supplies={180,80,50,65,90,40};long now=System.currentTimeMillis();Set<String> consumed=new HashSet<>();ArticleReadHistory history=new ArticleReadHistory(test.getTargetContext(),database);
            List<FeedStore.Row> initial=new ArrayList<>();
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){
                for(Source s:Source.values())seed.enable(s,Arrays.asList(sources).contains(s));
                for(int s=0;s<sources.length;s++){List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<supplies[s];n++){HotCandidate c=new HotCandidate(sources[s],url(sources[s],800000+s*10000+n),"混排已读独立事件"+(s*10000+n),"不依靠候选数量决定平台权重","",false,n+1,null,now);candidates.add(c);initial.add(new FeedStore.Row(c.id,c,""));}seed.upsertSnapshot(sources[s],seed.beginRefresh(sources[s]),candidates,now);}
                long id=seed.commitRanking(seed.version("sources"),seed.profile(now).getLong("version"),now,"cold","all",initial);seed.activateSnapshot(id);
            }
            long maxMs=0;for(int round=0;round<120;round++)try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                long began=SystemClock.elapsedRealtime(),at=System.currentTimeMillis();List<FeedStore.Row> before=all(store,store.activeSnapshot());
                if(round%10==0){List<FeedStore.Row> updated=new ArrayList<>();List<HotCandidate> v2ex=new ArrayList<>();for(FeedStore.Row row:initial){HotCandidate c=row.candidate;if(c.source==Source.V2EX){c=new HotCandidate(c.source,c.originalUrl.replace("reply10","reply"+(100+round)),c.title+"更新"+round,c.snippet,"9999",false,c.sourceRank,null,at);v2ex.add(c);}updated.add(new FeedStore.Row(c.id,c,""));}store.upsertSnapshot(Source.V2EX,store.beginRefresh(Source.V2EX),v2ex,at);long id=store.commitRanking(store.version("sources"),store.profile(at).getLong("version"),at,"personal","all",updated);store.activateSnapshot(id);before=all(store,id);}
                long id=round%2==0?store.presentationSnapshot(store.activeSnapshot(),"all",before.subList(0,Math.min(75,before.size())),at):store.refreshSnapshot(store.activeSnapshot(),"all",at);
                check(id>0,"round "+round+" snapshot accepted");if(id<0)throw new IllegalStateException("invalid snapshot "+round);store.activateSnapshot(id);List<FeedStore.Row> all=all(store,id),batch=FeedBatch.first(all);
                check(all.stream().noneMatch(r->consumed.contains(ArticleReadIdentity.key(r.candidate))),"round "+round+" no consumed identity across all saved rows");
                check(batch.size()==15,"round "+round+" still has a full unread batch");
                Map<Source,Integer> available=new EnumMap<>(Source.class),counts=new EnumMap<>(Source.class);for(FeedStore.Row r:all)available.merge(r.candidate.source,1,Integer::sum);for(FeedStore.Row r:batch)counts.merge(r.candidate.source,1,Integer::sum);
                if(available.size()==6&&Collections.min(available.values())>=3)check(counts.size()==6&&Collections.max(counts.values())-Collections.min(counts.values())<=1,"round "+round+" equal shares after read filtering despite 180 vs 40 supply");
                store.recordPresentation(batch,at,true);
                for(int offset:new int[]{0,8}){HotCandidate c=batch.get(offset).candidate;history.mark(c,at);consumed.add(ArticleReadIdentity.key(c));}
                if(round%13==0){
                    List<HotCandidate> retained=store.candidates(5000,0);store.clearDerivedCache();List<FeedStore.Row> restored=new ArrayList<>();
                    for(Source source:sources){List<HotCandidate> group=new ArrayList<>();for(HotCandidate c:retained)if(c.source==source){group.add(c);restored.add(new FeedStore.Row(c.id,c,""));}store.upsertSnapshot(source,store.beginRefresh(source),group,at);}
                    long restoredId=store.commitRanking(store.version("sources"),store.profile(at).getLong("version"),at,"cold","all",restored);store.activateSnapshot(restoredId);
                    check(all(store,restoredId).stream().noneMatch(r->consumed.contains(ArticleReadIdentity.key(r.candidate))),"round "+round+" cache clear and recollection do not restore read originals");
                }
                check(store.activeFeedback(at).length()==0,"round "+round+" no synthetic preferences from reads");
                check(new ArticleReadHistory(test.getTargetContext(),database).seen(batch.get(0).candidate),"round "+round+" record survives new owner/cache clear");
                maxMs=Math.max(maxMs,SystemClock.elapsedRealtime()-began);
                if(round%20==19){Bundle progress=new Bundle();progress.putString("stream","mixed-source rounds="+(round+1)+" failures="+failures+"\n");test.sendStatus(0,progress);}
            }
            report.append("rounds=120 sourceCount=6 originalCandidates=505 readOriginals=").append(consumed.size()).append(" elapsedMs=").append(SystemClock.elapsedRealtime()-start).append(" maxRoundMs=").append(maxMs).append('\n');
        }catch(Throwable e){failures++;report.append(android.util.Log.getStackTraceString(e));}
        finally{test.getTargetContext().deleteDatabase(database);test.getTargetContext().deleteSharedPreferences("article-reads-"+HotCandidate.hash(database));}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append('\n').toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
