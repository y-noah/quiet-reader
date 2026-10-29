package app.quietreader;
import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class EventGroupsTest {
    @Test public void currentIdenticalHotHeadlinesDoNotNeedInventedExcerptsOrPublicationTimes(){
        long now=1_798_100_000_000L;
        HotCandidate a=new HotCandidate(Models.Source.BAIDU,"https://www.baidu.com/s?wd=county","县委书记回应被扔下水","","1000",false,2,null,now);
        HotCandidate b=new HotCandidate(Models.Source.WEIBO,"https://s.weibo.com/weibo?q=county","#县委书记回应被扔下水#","","5000",false,1,null,now+1000);
        List<FeedStore.Row> grouped=CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false);
        assertEquals(1,grouped.size());assertEquals(2,grouped.get(0).entries.size());
        assertTrue(grouped.get(0).candidate.rawHeat.equals("1000")||grouped.get(0).candidate.rawHeat.equals("5000"));
        HotCandidate later=new HotCandidate(Models.Source.WEIBO,b.originalUrl,b.title,"","5000",false,1,null,now+86_400_000L);
        assertEquals(2,CandidateOrder.arrange(Arrays.asList(a,later),new JSONArray(),false).size());
        HotCandidate unrelated=new HotCandidate(Models.Source.WEIBO,b.originalUrl,"县委书记回应被扔下水的传闻系谣言","","5000",false,1,null,now);
        assertEquals(2,CandidateOrder.arrange(Arrays.asList(a,unrelated),new JSONArray(),false).size());
    }
    private HotCandidate thread(String url,String title){return new HotCandidate(Models.Source.V2EX,url,title,"","",false,1,null,1);}
    private HotCandidate completeThread(String url,String title,String excerpt,long published){return new HotCandidate(Models.Source.V2EX,url,title,excerpt,"最热",false,1,published,1_798_100_000_000L);}
    @Test public void explicitThreadIdentityWinsOverCompleteMetadataAndRetainsAllEntrances(){
        HotCandidate a=completeThread("https://www.v2ex.com/t/1251410#reply72","同一讨论的完整中文标题一",summary,1000L);
        HotCandidate b=completeThread("https://www.v2ex.com/t/1251410#reply73","同一讨论修改后的完整标题二",summary+"新增了一条说明。",2000L);
        List<FeedStore.Row> rows=CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false);
        assertEquals(1,rows.size());assertEquals(2,rows.get(0).entries.size());
        assertEquals(CandidateOrder.arrange(Collections.singletonList(thread("https://www.v2ex.com/t/1251410","同帖没有完整元数据时")),new JSONArray(),false).get(0).eventId,rows.get(0).eventId);
        HotCandidate other=completeThread("https://www.v2ex.com/t/1251411#reply72",a.title,a.snippet,1000L);
        assertEquals("Identical text cannot replace two explicit discussion IDs",2,CandidateOrder.arrange(Arrays.asList(a,other),new JSONArray(),false).size());
    }
    @Test public void threadIdentityNeverFallsBackToSharedCompleteTextForUnknownRoutes(){
        HotCandidate a=completeThread("https://www.v2ex.com/t/1251410#reply72","相同完整文字但独立讨论入口",summary,1000L);
        for(String url:Arrays.asList("https://www.v2ex.com/t/1251410?p=2#reply72","https://www.v2ex.com/t/1251410#/reply72","https://www.v2ex.com.evil.example/t/1251410#reply72"))
            assertEquals(url,2,CandidateOrder.arrange(Arrays.asList(a,completeThread(url,a.title,a.snippet,1000L)),new JSONArray(),false).size());
    }
    @Test public void completeThreadKeepsLegacyTargetsAndReversibleCurrentFeedback()throws Exception{
        HotCandidate a=completeThread("https://www.v2ex.com/t/1251410#reply72","存款利率1.5%的完整讨论标题",summary,1000L);
        HotCandidate b=completeThread("https://www.v2ex.com/t/1251410#reply73","同一个帖子更新内容后的完整标题",summary+"新增说明。",2000L);
        FeedStore.Row row=CandidateOrder.arrange(Collections.singletonList(a),new JSONArray(),false).get(0);
        String old="group-"+HotCandidate.hash("exact-evidence-v1\n"+a.title+"\n"+a.snippet+"\n0");
        assertTrue("Old numeric complete-evidence anchor remains known",EventGroups.matches(row,old));
        JSONArray legacy=new JSONArray().put(new JSONObject().put("scope","event").put("action","BLOCK").put("target",old));
        assertTrue(CandidateOrder.arrange(Collections.singletonList(a),legacy,false).isEmpty());
        assertEquals("Unknown changed-metadata legacy targets are not invented",1,CandidateOrder.arrange(Collections.singletonList(b),legacy,false).size());
        JSONArray block=new JSONArray().put(new JSONObject().put("scope","event").put("action","BLOCK").put("target",EventTargets.capture(row,false)));
        assertTrue(CandidateOrder.arrange(Collections.singletonList(b),block,false).isEmpty());
        JSONArray enough=new JSONArray().put(new JSONObject().put("scope","event").put("action","ENOUGH").put("target",EventTargets.capture(row,true)));
        assertTrue(CandidateOrder.arrange(Collections.singletonList(a),enough,false).isEmpty());
        assertEquals(1,CandidateOrder.arrange(Collections.singletonList(b),enough,false).size());
        FeedStore.Row both=CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false).get(0);
        JSONArray separate=new JSONArray().put(new JSONObject().put("scope","event").put("action","SEPARATE").put("target",EventTargets.capture(both,false)));
        assertEquals(2,CandidateOrder.arrange(Arrays.asList(a,b),separate,false).size());
        assertEquals(1,CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false).size());
    }
    @Test public void uniqueCrossSourceCopyStillGroupsButCannotUnionDistinctThreadsOrExcerpts(){
        HotCandidate a=completeThread("https://www.v2ex.com/t/1251410#reply72","完整来源支持同一事件的中文标题",summary,1000L);
        HotCandidate paper=new HotCandidate(Models.Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_34325705",a.title,a.snippet,"",false,2,1000L,a.observedAt);
        assertEquals(1,CandidateOrder.arrange(Arrays.asList(a,paper),new JSONArray(),false).size());
        HotCandidate other=completeThread("https://www.v2ex.com/t/1251411#reply72",a.title,a.snippet,1000L);
        assertEquals("Copied text cannot union two discussion IDs through a news entry",3,CandidateOrder.arrange(Arrays.asList(a,paper,other),new JSONArray(),false).size());
        HotCandidate edited=completeThread("https://www.v2ex.com/t/1251410#reply73",a.title,a.snippet+"新增不同完整事实。",1000L);
        List<FeedStore.Row> conflict=CandidateOrder.arrange(Arrays.asList(a,paper,edited),new JSONArray(),false);
        assertEquals("Conflicting full excerpts disable cross-source evidence bridging",2,conflict.size());
        assertEquals(3,conflict.stream().mapToInt(r->r.entries.size()).sum());
    }
    @Test public void newSingleThreadFeedbackCannotCaptureAnotherThreadsLegacyCopyIdentity()throws Exception{
        HotCandidate a=completeThread("https://www.v2ex.com/t/1251410#reply72","完全相同文字的两个独立讨论标题",summary,1000L);
        HotCandidate b=completeThread("https://www.v2ex.com/t/1251411#reply72",a.title,a.snippet,1000L);
        FeedStore.Row first=CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false).stream().filter(r->r.candidate.id.equals(a.id)).findFirst().get();
        for(String action:Arrays.asList("BLOCK","ENOUGH")){
            JSONArray feedback=new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",EventTargets.capture(first,"ENOUGH".equals(action))));
            List<FeedStore.Row> visible=CandidateOrder.arrange(Arrays.asList(a,b),feedback,false);
            assertEquals(action+" affects one explicit thread",1,visible.size());assertEquals(b.id,visible.get(0).candidate.id);
            assertEquals(2,CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false).size());
        }
    }
    @Test public void observedV2exReplyCountAnchorsShareThreadWithoutRewritingStoredIdentities(){
        HotCandidate a=thread("https://www.v2ex.com/t/1251410#reply72","同一讨论的中文标题"),b=thread("https://www.v2ex.com/t/1251410#reply73","同一讨论的中文标题");
        List<FeedStore.Row> rows=CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false);
        assertNotEquals(a.id,b.id);assertEquals(1,rows.size());assertEquals(2,rows.get(0).entries.size());
        assertTrue(rows.get(0).entries.stream().anyMatch(c->c.originalUrl.equals(a.originalUrl)));assertTrue(rows.get(0).entries.stream().anyMatch(c->c.originalUrl.equals(b.originalUrl)));
        assertEquals(rows.get(0).eventId,CandidateOrder.arrange(Arrays.asList(thread("https://www.v2ex.com/t/1251410","标题更新后仍是同一个讨论")),new JSONArray(),false).get(0).eventId);
    }
    @Test public void v2exThreadFeedbackSurvivesReplyCountButEnoughAllowsNewText()throws Exception{
        HotCandidate a=thread("https://www.v2ex.com/t/1251410#reply72","同一讨论的中文标题"),b=thread("https://www.v2ex.com/t/1251410#reply73","同一讨论的中文标题");
        FeedStore.Row row=CandidateOrder.arrange(Arrays.asList(a),new JSONArray(),false).get(0);
        JSONArray block=new JSONArray().put(new JSONObject().put("scope","event").put("action","BLOCK").put("target",EventTargets.capture(row,false)));
        assertTrue(CandidateOrder.arrange(Arrays.asList(b),block,false).isEmpty());
        JSONArray enough=new JSONArray().put(new JSONObject().put("scope","event").put("action","ENOUGH").put("target",EventTargets.capture(row,true)));
        assertTrue(CandidateOrder.arrange(Arrays.asList(b),enough,false).isEmpty());
        assertEquals(1,CandidateOrder.arrange(Arrays.asList(thread(b.originalUrl,"该帖子有实质标题更新后的版本")),enough,false).size());
        assertTrue(CandidateOrder.arrange(Arrays.asList(thread(b.originalUrl,"该帖子有实质标题更新后的版本")),block,false).isEmpty());
        assertEquals(1,CandidateOrder.arrange(Arrays.asList(b),new JSONArray(),false).size());
    }
    @Test public void v2exIdentityDoesNotCollapseQueriesRoutesOrForeignLookalikes(){
        HotCandidate a=thread("https://www.v2ex.com/t/1251410#reply72","同一讨论的中文标题");
        for(String url:Arrays.asList("https://www.v2ex.com/t/1251411#reply72","https://www.v2ex.com/t/1251410?p=2#reply72","https://www.v2ex.com/t/1251410#/reply72","https://www.v2ex.com.evil.example/t/1251410#reply72","https://other.v2ex.com/t/1251410#reply72"))assertEquals(url,2,CandidateOrder.arrange(Arrays.asList(a,thread(url,a.title)),new JSONArray(),false).size());
    }
    private HotCandidate candidate(int id,String summary,Long date){return new HotCandidate(id==1?Models.Source.ZHIHU:Models.Source.WEIBO,"https://example.org/article/"+id,"空间站完成一项新的科学实验验证",summary,"999",false,2,date,1);}
    private final String summary="空间站乘组在实验舱完成了新的科学实验，记录了实验数据并传回地面研究团队。";
    @Test public void exactEvidenceGroupsWithoutDuplicatingHeatOrChangingIdentity(){
        HotCandidate a=candidate(1,summary,1000L),b=candidate(2,summary,2000L);
        List<FeedStore.Row> one=CandidateOrder.arrange(Arrays.asList(a),new JSONArray(),false),two=CandidateOrder.arrange(Arrays.asList(a,b,b),new JSONArray(),false);
        assertEquals(1,two.size());assertEquals(2,two.get(0).entries.size());assertEquals(one.get(0).eventId,two.get(0).eventId);assertEquals("999",two.get(0).candidate.rawHeat);
    }
    @Test public void differentExcerptsAndDifferentDaysStaySeparate(){
        HotCandidate a=candidate(1,summary,1000L);
        for(HotCandidate b:Arrays.asList(candidate(2,summary+"后续研究尚待确认。",1000L),candidate(2,summary,86_400_000L)))assertEquals(2,CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false).size());
    }
    @Test public void userSeparationIsReversibleAndGroupFeedbackCoversNewEntries()throws Exception{
        HotCandidate a=candidate(1,summary,1000L),b=candidate(2,summary,2000L);List<HotCandidate> candidates=Arrays.asList(a,b);
        String event=CandidateOrder.arrange(candidates,new JSONArray(),false).get(0).eventId;
        JSONArray separate=new JSONArray().put(new JSONObject().put("action","SEPARATE").put("scope","event").put("target",event));
        assertEquals(2,CandidateOrder.arrange(candidates,separate,false).size());assertEquals(1,CandidateOrder.arrange(candidates,new JSONArray(),false).size());
        JSONArray enough=new JSONArray().put(new JSONObject().put("action","ENOUGH").put("scope","event").put("target",event+"@"+a.textHash));
        assertTrue(CandidateOrder.arrange(candidates,enough,false).isEmpty());
    }
    @Test public void semanticReasonsRetainAllSourceEntries(){
        List<FeedStore.Row> rows=CandidateOrder.arrange(Arrays.asList(candidate(1,summary,1000L),candidate(2,summary,2000L)),new JSONArray(),false);
        assertEquals(2,SemanticOrder.rank(rows,new SemanticOrder.Profile(new JSONArray()),Collections.emptyMap()).get(0).row.entries.size());
    }
    @Test public void anchorsResolveAcrossGroupingAndSeparation()throws Exception{
        HotCandidate a=candidate(1,summary,1000L),b=candidate(2,summary,2000L);List<HotCandidate> candidates=Arrays.asList(a,b);
        FeedStore.Row grouped=CandidateOrder.arrange(candidates,new JSONArray(),false).get(0);
        assertTrue(EventGroups.matches(grouped,a.id));assertTrue(EventGroups.matches(grouped,b.id));assertFalse(EventGroups.matches(grouped,"unrelated"));
        JSONArray separated=new JSONArray().put(new JSONObject().put("action","SEPARATE").put("scope","event").put("target",grouped.eventId));
        List<FeedStore.Row> split=CandidateOrder.arrange(candidates,separated,false);assertTrue(EventGroups.matches(split.get(0),grouped.eventId));
        separated.put(new JSONObject().put("action","BLOCK").put("scope","event").put("target",grouped.eventId));
        assertTrue(CandidateOrder.arrange(candidates,separated,false).isEmpty());
    }
}
