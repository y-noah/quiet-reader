package app.quietreader;
import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class CandidateOrderTest {
    @Test public void exactSoftNegativeSurvivesBaseSnapshotWithoutBecomingAHardBlock()throws Exception{
        HotCandidate disliked=row(Models.Source.V2EX,1,"明确少看的原文",200L,1),unknown=row(Models.Source.ZHIHU,2,"未评价的另一篇",100L,1);
        JSONArray feedback=new JSONArray().put(new JSONObject().put("action","LESS").put("scope","sample").put("target",CandidateOrder.versionTarget(disliked)).put("sample",disliked.modelText())).put(new JSONObject().put("action","MORE").put("scope","source").put("target","V2EX"));
        List<FeedStore.Row> base=CandidateOrder.arrange(List.of(disliked,unknown),feedback,false);assertEquals(2,base.size());assertEquals(disliked.id,base.get(1).candidate.id);assertTrue(base.get(1).reason.contains("少看"));
        assertEquals(disliked.id,CandidateOrder.forModels(List.of(disliked,unknown),feedback).get(0).candidate.id);
        assertEquals(disliked.id,CandidateOrder.arrange(List.of(disliked,unknown),feedback,true).get(0).candidate.id);
        assertEquals(disliked.id,CandidateOrder.arrange(List.of(disliked,unknown),new JSONArray(),false).get(0).candidate.id);
    }
    @Test public void videoMetadataIsExcludedBeforeGroupingAcrossAllSources(){
        List<HotCandidate> input=new ArrayList<>();
        for(Models.Source source:Models.Source.values())input.add(new HotCandidate(source,"https://example.org/video/"+source.name(),"中文视频标题"+source.name(),"","",true,1,null,1));
        HotCandidate article=row(Models.Source.ZHIHU,2,"图文标题",null,1);input.add(article);
        List<FeedStore.Row> result=CandidateOrder.arrange(input,new JSONArray(),false);
        assertEquals(1,result.size());assertEquals(article.id,result.get(0).candidate.id);
    }
    @Test public void permanentBlockSurvivesTextChangesButDoesNotBlockSimilarItems()throws Exception{
        HotCandidate original=row(Models.Source.ZHIHU,1,"一个事件",null,1),changed=row(Models.Source.ZHIHU,1,"更新后的事件",null,2),similar=row(Models.Source.ZHIHU,2,"一个事件的新角度",null,1);
        JSONArray feedback=new JSONArray().put(new JSONObject().put("action","BLOCK").put("scope","event").put("target",original.id));
        List<FeedStore.Row> result=CandidateOrder.arrange(Arrays.asList(changed,similar),feedback,false);
        assertEquals(1,result.size());assertEquals(similar.id,result.get(0).candidate.id);
    }
    @Test public void excludingCardPreservesPersonalOrderAndAppendsNewItems(){
        List<FeedStore.Row> base=CandidateOrder.arrange(Arrays.asList(row(Models.Source.ZHIHU,1,"甲",null,1),row(Models.Source.ZHIHU,2,"乙",null,1),row(Models.Source.ZHIHU,3,"丙",null,1)),new JSONArray(),false);
        List<FeedStore.Row> visible=Arrays.asList(base.get(2),base.get(0));
        List<FeedStore.Row> kept=CandidateOrder.preserveVisibleOrder(Arrays.asList(base.get(1),base.get(2)),visible);
        assertEquals(base.get(2).eventId,kept.get(0).eventId);assertEquals(base.get(1).eventId,kept.get(1).eventId);
    }
    private HotCandidate row(Models.Source source,int rank,String title,Long date,long observed){return new HotCandidate(source,"https://"+(source==Models.Source.ZHIHU?"www.zhihu.com":"www.v2ex.com")+"/topic/"+rank,title,"","9999999",false,rank,date,observed);}
    @Test public void sourceHeatAndArrivalOrderDoNotPromoteDuplicates(){
        HotCandidate a=row(Models.Source.ZHIHU,2,"次名",null,1),b=row(Models.Source.V2EX,1,"首名",null,1);
        List<FeedStore.Row> result=CandidateOrder.arrange(Arrays.asList(a,b,b),new JSONArray(),false);
        assertEquals(2,result.size());assertEquals(b.id,result.get(0).eventId);
        assertEquals(result.get(0).eventId,CandidateOrder.arrange(Arrays.asList(b,a),new JSONArray(),false).get(0).eventId);
    }
    @Test public void unknownPublicationDatesNeverBecomeNewest(){
        HotCandidate unknown=row(Models.Source.ZHIHU,1,"刚抓到但无日期",null,99999),known=row(Models.Source.V2EX,9,"已知日期",100L,101);
        assertEquals(known.id,CandidateOrder.arrange(Arrays.asList(unknown,known),new JSONArray(),true).get(0).eventId);
    }
    @Test public void enoughSuppressesOnlyTheVersionSeen()throws Exception{
        HotCandidate before=row(Models.Source.ZHIHU,1,"旧事件内容",null,1),changed=row(Models.Source.ZHIHU,1,"有事实更新的内容",null,2);
        JSONArray feedback=new JSONArray().put(new JSONObject().put("action","ENOUGH").put("scope","event").put("target",CandidateOrder.versionTarget(before)));
        assertTrue(CandidateOrder.arrange(Arrays.asList(before),feedback,false).isEmpty());
        assertEquals(1,CandidateOrder.arrange(Arrays.asList(changed),feedback,false).size());
    }
}
