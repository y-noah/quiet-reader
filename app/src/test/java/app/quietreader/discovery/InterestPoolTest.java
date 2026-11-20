package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class InterestPoolTest {
    private List<FeedStore.Row> pool(int count){
        List<FeedStore.Row> rows=new ArrayList<>();
        for(int n=0;n<count;n++){
            Source source=n%3==0?Source.V2EX:n%3==1?Source.ZHIHU:Source.TIEBA;
            String host=source==Source.V2EX?"https://www.v2ex.com/t/":source==Source.ZHIHU?"https://www.zhihu.com/question/":"https://tieba.baidu.com/p/";
            HotCandidate item=new HotCandidate(source,host+(800000+n),"候选内容 "+n,"","",false,n+1,null,1);
            rows.add(new FeedStore.Row(item.id,item,"原排名",List.of(item),1));
        }
        return rows;
    }
    private JSONObject less(Source source)throws Exception{return new JSONObject().put("action","LESS").put("scope","source").put("target",source.name());}
    private List<FeedStore.Row> selected(List<FeedStore.Row> pool,JSONArray feedback){return RecommendationWindow.select(FeedSourceBalance.arrange(InterestPool.eligible(pool),new PlatformPreference(feedback)),new HashMap<>(),Set.of());}
    private long count(List<FeedStore.Row> rows,Source source){return rows.stream().filter(r->r.candidate.source==source).count();}
    @Test public void sourceLessReducesShareWithoutDeletingCandidates()throws Exception{
        List<FeedStore.Row> pool=pool(300),chosen=selected(pool,new JSONArray().put(less(Source.V2EX)));
        assertEquals(RecommendationWindow.SIZE,chosen.size());assertTrue(count(chosen,Source.V2EX)>0);assertTrue(count(chosen,Source.V2EX)<count(chosen,Source.ZHIHU));
        assertEquals(pool,InterestPool.eligible(pool));assertTrue(chosen.stream().anyMatch(r->r.candidate.source==Source.V2EX&&r.candidate.sourceRank==1));
    }
    @Test public void multipleLessSourcesRemainAvailable()throws Exception{
        List<FeedStore.Row> chosen=selected(pool(300),new JSONArray().put(less(Source.V2EX)).put(less(Source.ZHIHU)));
        assertEquals(RecommendationWindow.SIZE,chosen.size());for(Source source:List.of(Source.V2EX,Source.ZHIHU)){assertTrue(count(chosen,source)>0);assertTrue(count(chosen,source)<count(chosen,Source.TIEBA));}
    }
    @Test public void scarcityDoesNotTurnASoftDislikeIntoEmptySpace()throws Exception{
        List<FeedStore.Row> pool=pool(45);assertEquals(new HashSet<>(pool),new HashSet<>(selected(pool,new JSONArray().put(less(Source.V2EX)))));
        List<FeedStore.Row> one=pool(300).stream().filter(r->r.candidate.source==Source.V2EX).toList();
        assertEquals(RecommendationWindow.SIZE,selected(one,new JSONArray().put(less(Source.V2EX))).size());
    }
    @Test public void semanticNegativeContentIsExcludedEvenWhenFewerThanSeventyFiveRemain(){
        List<FeedStore.Row> pool=pool(80);for(int n=10;n<pool.size();n++)pool.set(n,pool.get(n).withInterestBand(2));
        assertEquals(pool.subList(0,10),selected(pool,new JSONArray()));
    }
    @Test public void undoRestoresNormalMembershipAndNeverChangesRanking()throws Exception{
        List<FeedStore.Row> pool=pool(300);assertEquals(RecommendationWindow.SIZE/3,selected(pool,new JSONArray()).stream().filter(r->r.candidate.source==Source.V2EX).count());
        PlatformPreference preference=new PlatformPreference(new JSONArray().put(less(Source.V2EX)));
        assertEquals(pool.stream().map(r->r.eventId).toList(),preference.apply(pool).stream().map(r->r.eventId).toList());
    }
    @Test public void onlyExplicitFeedbackRevisesMembership()throws Exception{
        JSONArray feedback=new JSONArray();long base=InterestPool.revision(feedback);
        feedback.put(new JSONObject().put("action","READ").put("scope","sample").put("target","one"));assertEquals(base,InterestPool.revision(feedback));
        feedback.put(less(Source.V2EX));assertNotEquals(base,InterestPool.revision(feedback));
    }
    @Test public void categoryDislikeRemovesRelatedMembershipWithoutDemotingUnrelatedRows()throws Exception{
        List<FeedStore.Row> pool=pool(90);String example=pool.get(20).candidate.modelText();
        SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(new JSONObject().put("action","LESS").put("scope","sample").put("sample",example)));
        float[] related=new float[512],other=new float[512];related[0]=1;other[1]=1;
        Map<String,float[]> vectors=new HashMap<>();for(int n=0;n<pool.size();n++)vectors.put(SemanticOrder.key(pool.get(n).candidate.modelText()),n%3==0?related:other);vectors.put(SemanticOrder.key(example),related);
        List<FeedStore.Row> ranked=SemanticOrder.fuse(SemanticOrder.rank(pool,profile,vectors),Map.of(),profile.platforms);
        List<FeedStore.Row> chosen=selected(ranked,profile.feedback());
        List<String> expected=new ArrayList<>();for(int n=0;n<pool.size();n++)if(n%3!=0&&n!=20)expected.add(pool.get(n).eventId);
        assertEquals(59,chosen.size());assertEquals(new HashSet<>(expected),new HashSet<>(chosen.stream().map(row->row.eventId).toList()));
    }
    @Test public void explicitDislikedIdentityCannotReturnAfterTitleEditOrGroupRepresentativeChange()throws Exception{
        List<FeedStore.Row> pool=pool(3);HotCandidate old=pool.get(0).candidate;
        HotCandidate updated=new HotCandidate(old.source,old.originalUrl,"来源修改了标题和摘要","全新的摘要","",false,1,null,2);
        JSONObject feedback=new JSONObject().put("action","LESS").put("scope","sample").put("target",CandidateOrder.versionTarget(old)).put("sample",old.modelText());
        SemanticOrder.Profile profile=new SemanticOrder.Profile(new JSONArray().put(feedback));
        FeedStore.Row group=new FeedStore.Row("group",pool.get(1).candidate,"",List.of(pool.get(1).candidate,updated));
        List<FeedStore.Row> ranked=SemanticOrder.fuse(SemanticOrder.rank(List.of(group,pool.get(2)),profile,Map.of()),Map.of());
        assertEquals(List.of(pool.get(2).eventId),selected(ranked,profile.feedback()).stream().map(row->row.eventId).toList());
    }
}
