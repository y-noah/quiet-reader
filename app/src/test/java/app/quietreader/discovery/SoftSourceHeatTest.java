package app.quietreader.discovery;

import java.util.*;
import org.json.*;
import org.junit.Test;
import app.quietreader.Models.Source;
import static org.junit.Assert.*;

public class SoftSourceHeatTest {
    private HotCandidate item(Source source,int id,int rank){return new HotCandidate(source,"https://example.com/"+source+"/"+id,"真实话题 "+id,"","",false,rank,null,1);}
    private PlatformPreference less()throws Exception{return new PlatformPreference(new JSONArray().put(new JSONObject().put("action","LESS").put("scope","source").put("target","V2EX")));}
    private FeedStore.Row row(HotCandidate item){return new FeedStore.Row(item.id,item,"",List.of(item),1);}
    @Test public void hotTopicCanEscapeTheTailOfADislikedPlatform()throws Exception{
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<100;n++)for(Source source:List.of(Source.V2EX,Source.ZHIHU,Source.BBC))rows.add(row(item(source,n,n+10)));
        HotCandidate hot=item(Source.V2EX,999,1);rows.add(row(hot));
        List<FeedStore.Row> selected=RecommendationWindow.select(FeedSourceBalance.arrange(InterestPool.eligible(rows),less()),new HashMap<>(),Set.of());
        assertTrue(selected.stream().anyMatch(r->r.candidate.id.equals(hot.id)));
        long fewer=selected.stream().filter(r->r.candidate.source==Source.V2EX).count(),normal=selected.stream().filter(r->r.candidate.source==Source.ZHIHU).count();
        assertTrue(fewer>0);assertTrue(fewer<normal);assertEquals(rows.size(),InterestPool.eligible(rows).size());
    }
    @Test public void heatNeverOverridesAnExplicitTopicDislikeOrPlatformBlock()throws Exception{
        HotCandidate hot=item(Source.V2EX,1,1);assertTrue(InterestPool.eligible(List.of(row(hot).withInterestBand(2))).isEmpty());
        JSONArray feedback=new JSONArray().put(new JSONObject().put("action","BLOCK").put("scope","source").put("target","V2EX"));
        assertTrue(CandidateOrder.arrange(List.of(hot),feedback,false).isEmpty());
    }
    @Test public void sourceRanksUseOneScaleAndLatestFeedsDoNotPretendToBeHot(){
        assertEquals(SourceHeat.strength(item(Source.WEIBO,1,1)),SourceHeat.strength(item(Source.ZHIHU,2,1)),0);
        assertTrue(SourceHeat.strength(item(Source.V2EX,1,1))>SourceHeat.strength(item(Source.V2EX,2,10)));
        assertEquals(0,SourceHeat.strength(item(Source.INFOQ,1,1)),0);assertEquals(0,SourceHeat.strength(item(Source.LEIPHONE_AI,1,1)),0);
        assertEquals(1,SourceHeat.strength(item(Source.QBITAI,1,1)),0);
    }
    @Test public void heatSoftensAPenaltyWithoutTurningItIntoAPreference()throws Exception{
        PlatformPreference preference=less();double hot=preference.selectionWeight(item(Source.V2EX,1,1),20),cold=preference.selectionWeight(item(Source.V2EX,2,100),20);
        assertTrue(hot>cold);assertTrue(hot<1);assertTrue(hot<=preference.exposureWeight(Source.V2EX)+0.05+1e-8);
    }
    @Test public void fifteenPerPageSixtyPerWindowAndSixtyPrefetchTasks(){
        assertEquals(15,FeedBatch.SIZE);assertEquals(60,RecommendationWindow.SIZE);assertEquals(60,FeedReserve.CAPACITY);
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<100;n++)rows.add(row(item(Source.BBC,n,n+1)));
        assertEquals(15,FeedBatch.first(rows).size());FeedReserve reserve=new FeedReserve();reserve.accepted(FeedBatch.first(rows));reserve.stage(rows,"all");assertTrue(reserve.future().size()<=45);
    }
}
