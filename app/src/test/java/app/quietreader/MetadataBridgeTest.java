package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/** Exact headline bridge only; conflicting factual excerpts are not inferred equivalent. */
public class MetadataBridgeTest {
    private static final long NOW=1_798_100_000_000L;
    private static final String TITLE="县委书记回应被扔下水";
    private static final String SUMMARY="县委书记回应了被扔下水的相关问题，现场经过和回应原文由记者记录，更多细节仍在核实。";
    private HotCandidate item(int n,String title,String snippet,Long published,long observed){return new HotCandidate(n%2==0?Models.Source.WEIBO:Models.Source.ZHIHU,"https://example.org/bridge/"+n,title,snippet,"heat-"+n,false,n,published,observed);}
    private HotCandidate strong(int n){return item(n,TITLE,SUMMARY,NOW,NOW);}
    private HotCandidate weak(int n){return item(n,"如何看待"+TITLE+"？","",null,NOW);}
    private List<FeedStore.Row> rows(JSONArray feedback,HotCandidate...items){return CandidateOrder.arrange(Arrays.asList(items),feedback,false);}
    private JSONArray feedback(String action,String target)throws Exception{return new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",target));}
    @Test public void missingExcerptAndDateCanJoinOneUnambiguousCompleteEvidenceFamily(){
        HotCandidate a=strong(1),b=weak(2);FeedStore.Row mixed=rows(new JSONArray(),a,b).get(0);
        assertEquals(1,rows(new JSONArray(),a,b).size());assertEquals(2,mixed.entries.size());assertEquals(rows(new JSONArray(),a).get(0).eventId,mixed.eventId);assertEquals("heat-1",mixed.candidate.rawHeat);
        assertEquals(Set.of(a.id,b.id),new HashSet<>(mixed.entries.stream().map(c->c.id).toList()));
    }
    @Test public void matchingExcerptWithMissingDateBridgesButUnknownNewFactsDoNot(){
        assertEquals(1,rows(new JSONArray(),strong(1),item(2,TITLE,SUMMARY,null,NOW)).size());
        assertEquals(2,rows(new JSONArray(),strong(1),item(2,TITLE,"官方表示此前的相关说法不属实",null,NOW)).size());
        assertEquals(2,rows(new JSONArray(),strong(1),item(2,TITLE,"",null,NOW+86_400_000L)).size());
    }
    @Test public void competingCompleteExcerptsNeverUseHeadlineOnlyMembersAsABridge(){
        HotCandidate a=strong(1),b=item(2,TITLE,SUMMARY+"有关部门随后公布了否认该说法的材料。",NOW,NOW),c=weak(3);
        assertEquals(3,rows(new JSONArray(),a,b,c).size());assertEquals(3,rows(new JSONArray(),c,b,a).size());
    }
    @Test public void bridgedSeparationKeepsBothFamiliesWhenEitherMetadataSideDisappears()throws Exception{
        HotCandidate a=strong(1),b=weak(2),c=strong(3),d=weak(4);FeedStore.Row mixed=rows(new JSONArray(),a,b,c,d).get(0);
        JSONArray split=feedback("SEPARATE",EventTargets.capture(mixed,false));
        assertEquals(4,rows(split,a,b,c,d).size());assertEquals(2,rows(split,b,d).size());assertEquals(2,rows(split,a,c).size());assertEquals(1,rows(new JSONArray(),a,b,c,d).size());
    }
    @Test public void blockKeepsObservedHeadlineIdentityAfterCompleteMemberDisappears()throws Exception{
        HotCandidate a=strong(1),b=weak(2);FeedStore.Row mixed=rows(new JSONArray(),a,b).get(0);
        JSONArray blocked=feedback("BLOCK",EventTargets.capture(mixed,false));
        assertTrue(rows(blocked,weak(3)).isEmpty());
        assertTrue(rows(blocked,item(1,TITLE+"的后续调查",SUMMARY+"新进展",NOW,NOW),weak(3)).isEmpty());
        assertEquals(1,rows(blocked,item(4,TITLE+"的后续调查结果公布","",null,NOW)).size());
    }
    @Test public void enoughPreservesCapturedWeakVersionsButAllowsActualNewContext()throws Exception{
        FeedStore.Row mixed=rows(new JSONArray(),strong(1),weak(2)).get(0);JSONArray enough=feedback("ENOUGH",EventTargets.capture(mixed,true));
        assertTrue(rows(enough,item(3,"热议："+TITLE,"",null,NOW)).isEmpty());
        assertEquals(1,rows(enough,item(3,TITLE,"来源提供了新的实质调查信息",null,NOW)).size());
    }
    @Test public void oldCompleteGroupFeedbackStillAppliesWhileHeadlineMemberIsPresent()throws Exception{
        HotCandidate a=strong(1),b=weak(2);String old=rows(new JSONArray(),a).get(0).eventId;
        assertEquals(2,rows(feedback("SEPARATE",old),a,b).size());
        assertTrue(rows(feedback("BLOCK",old),a,b).isEmpty());
        assertTrue(rows(feedback("BLOCK",EventTargets.capture(rows(new JSONArray(),a).get(0),false)),a,b).isEmpty());
    }
    @Test public void bridgeRetainsAnchorsAndVisibleOrderAfterCompleteMemberDisappears(){
        HotCandidate a=strong(1),b=weak(2),other=item(3,"这是与目标事件无关的独立话题","",null,NOW);FeedStore.Row mixed=rows(new JSONArray(),a,b).get(0);
        String weakKey=rows(new JSONArray(),b).get(0).eventId;assertTrue(EventGroups.matches(mixed,weakKey));
        List<FeedStore.Row> ordered=CandidateOrder.preserveVisibleOrder(rows(new JSONArray(),other,b),List.of(mixed));assertEquals(b.id,ordered.get(0).candidate.id);
    }
    @Test public void explicitV2exThreadIsNotReassignedByAHeadlineBridge(){
        HotCandidate thread=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/990071",TITLE,"","",false,1,null,NOW);
        assertEquals(2,rows(new JSONArray(),strong(1),thread).size());
    }
}
