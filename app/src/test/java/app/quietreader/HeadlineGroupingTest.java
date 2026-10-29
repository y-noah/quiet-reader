package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Controlled titles, not a claim about live-source semantic deduplication quality. */
public class HeadlineGroupingTest {
    private static final long NOW=1_798_100_000_000L;
    private static final String CORE="县委书记回应被扔下水";
    private HotCandidate item(int n,String title){return new HotCandidate(n%2==0?Models.Source.WEIBO:Models.Source.ZHIHU,"https://example.org/topic/"+n,title,"","source-heat-"+n,false,n,null,NOW);}
    private List<FeedStore.Row> rows(JSONArray feedback,HotCandidate...items){return CandidateOrder.arrange(Arrays.asList(items),feedback,false);}
    private JSONArray feedback(String action,String target)throws Exception{return new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",target));}
    @Test public void presentationAndOpinionWrappersGroupWithoutAddingHeat(){
        String[] titles={CORE,"#"+CORE+"#","如何看待"+CORE+"？","如何评价「"+CORE+"」一事？","热议："+CORE,CORE+"，你怎么看？","如何看待"+CORE+"？有哪些信息值得关注？"};
        HotCandidate[] candidates=new HotCandidate[titles.length];for(int n=0;n<titles.length;n++)candidates[n]=item(n+1,titles[n]);
        List<FeedStore.Row> result=rows(new JSONArray(),candidates);assertEquals(1,result.size());assertEquals(titles.length,result.get(0).entries.size());
        assertEquals(candidates[0].id,result.get(0).candidate.id);assertEquals(candidates[0].rawHeat,result.get(0).candidate.rawHeat);
        assertEquals(rows(new JSONArray(),candidates[0]).get(0).eventId,result.get(0).eventId);
        assertEquals(Set.of(titles),new HashSet<>(result.get(0).entries.stream().map(c->c.title).toList()));
    }
    @Test public void factsDenialsQuestionsNumbersAndProgressAreNeverRemoved(){
        for(String other:new String[]{"如何看待县委书记否认被扔下水？","如何看待县委书记回应被扔下水系谣言？",CORE+"？是否属实？",CORE+"？官方辟谣了？",CORE+"？有什么后续？","县委书记回应被扔下水的调查结果公布","县委书记回应被扔下水造成3人受伤","县委书记回应被扔下水造成30人受伤"})
            assertEquals(other,2,rows(new JSONArray(),item(1,CORE),item(2,other)).size());
        assertEquals(2,rows(new JSONArray(),item(1,"如何看待人工智能？"),item(2,"人工智能")).size());
    }
    @Test public void currentGroupingCanBeSeparatedBlockedAndUndone()throws Exception{
        HotCandidate a=item(1,CORE),b=item(2,"如何看待"+CORE+"？");FeedStore.Row group=rows(new JSONArray(),a,b).get(0);
        assertEquals(2,rows(feedback("SEPARATE",group.eventId),a,b).size());
        String target=EventTargets.capture(group,false);
        assertTrue(rows(feedback("BLOCK",target),a,b,item(3,"热议："+CORE)).isEmpty());
        assertEquals(1,rows(new JSONArray(),a,b).size());
        assertEquals(1,rows(feedback("BLOCK",target),item(4,"县委书记回应被扔下水的最新调查结果公布")).size());
    }
    @Test public void enoughIgnoresCosmeticRewrappingButAllowsSubstantiveNewText()throws Exception{
        FeedStore.Row group=rows(new JSONArray(),item(1,"如何看待"+CORE+"？")).get(0);
        JSONArray enough=feedback("ENOUGH",EventTargets.capture(group,true));
        assertTrue(rows(enough,item(2,CORE),item(3,"热议："+CORE)).isEmpty());
        assertEquals(1,rows(enough,item(2,"县委书记回应被扔下水的调查结果公布")).size());
        HotCandidate withContext=new HotCandidate(Models.Source.ZHIHU,"https://example.org/topic/4",CORE,"来源新增实质说明内容","",false,1,null,NOW);
        assertEquals(1,rows(enough,withContext).size());
    }
    @Test public void legacyWrapperGroupAnchorsAndFeedbackRemainApplicable()throws Exception{
        HotCandidate wrapped=item(1,"如何看待"+CORE+"？"),plain=item(2,CORE);
        long day=Math.floorDiv(NOW+8L*3600_000,86_400_000L);
        String oldKey="group-"+HotCandidate.hash("hot-title-v1\n如何看待"+CORE+"\n"+day);
        FeedStore.Row group=rows(new JSONArray(),wrapped,plain).get(0);
        assertTrue(EventGroups.matches(group,oldKey));
        assertEquals(2,rows(feedback("SEPARATE",oldKey),wrapped,plain).size());
        assertFalse(rows(feedback("BLOCK",oldKey),wrapped,plain).stream().flatMap(r->r.entries.stream()).anyMatch(c->c.id.equals(wrapped.id)));
        assertTrue(EventTargets.expand(EventTargets.capture(group,false)).contains(oldKey));
        FeedStore.Row legacy=new FeedStore.Row(oldKey,wrapped,"old persisted group");
        HotCandidate other=item(3,"这是一个无关的独立文字话题");
        List<FeedStore.Row> reordered=CandidateOrder.preserveVisibleOrder(rows(new JSONArray(),other,plain),List.of(legacy));
        assertEquals(plain.id,reordered.get(0).candidate.id);
    }
    @Test public void observedDayAndKnownThreadBoundariesRemainAuthoritative(){
        HotCandidate a=item(1,CORE),wrapped=item(2,"如何看待"+CORE+"？");
        HotCandidate later=new HotCandidate(wrapped.source,wrapped.originalUrl,wrapped.title,"","",false,1,null,NOW+86_400_000);
        assertEquals(2,rows(new JSONArray(),a,later).size());
        HotCandidate thread=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/12345",wrapped.title,"","",false,1,null,NOW);
        assertEquals(2,rows(new JSONArray(),a,thread).size());
    }
    @Test public void numericPunctuationPreservesDecimalsSignsRatiosAndPercentages()throws Exception{
        String[][] facts={{"今年本地区经济同比增长1.5%","今年本地区经济同比增长15%"},{"本地气温即将降至-15摄氏度","本地气温即将降至15摄氏度"},{"本场比赛最终比分确定为1:2","本场比赛最终比分确定为12"},{"新版本工具1.2发布正式启用","新版本工具12发布正式启用"},{"本地区经济今年同比增长15%","本地区经济今年同比增长15"},{"如何看待价格1.5？","如何看待价格15？"}};
        for(String[] pair:facts){
            HotCandidate a=item(1,pair[0]),b=item(2,pair[1]);assertEquals(Arrays.toString(pair),2,rows(new JSONArray(),a,b).size());
            String block=EventTargets.capture(rows(new JSONArray(),a).get(0),false);
            assertEquals(Arrays.toString(pair),1,rows(feedback("BLOCK",block),a,b).size());
            assertEquals(b.id,rows(feedback("BLOCK",block),a,b).get(0).candidate.id);
        }
        assertEquals(1,rows(new JSONArray(),item(1,"今年本地区经济同比增长１．５％"),item(2,"如何看待今年本地区经济同比增长1.5%？")).size());
    }
    @Test public void oldLossyNumericGroupNeverBecomesAnAliasOfADifferentFact()throws Exception{
        HotCandidate decimal=item(1,"今年本地区经济同比增长1.5%"),integer=item(2,"今年本地区经济同比增长15%");
        long day=Math.floorDiv(NOW+8L*3600_000,86_400_000L);
        String lossy="group-"+HotCandidate.hash("hot-title-v1\n今年本地区经济同比增长15\n"+day);
        String oldCaptured=new JSONObject().put("format","event-targets-v1").put("targets",new JSONArray().put(lossy).put(decimal.id)).toString();
        List<FeedStore.Row> result=rows(feedback("BLOCK",oldCaptured),decimal,integer);
        assertEquals(1,result.size());assertEquals(integer.id,result.get(0).candidate.id);
        assertFalse(EventTargets.expand(EventTargets.capture(rows(new JSONArray(),integer).get(0),false)).contains(lossy));
    }
}
