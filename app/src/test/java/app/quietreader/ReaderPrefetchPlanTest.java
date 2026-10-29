package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReaderPrefetchPlanTest {
    private HotCandidate candidate(int n){return new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+n,"中文预读话题"+n,"文字摘要","",false,n+1,null,100);}
    private List<FeedStore.Row> rows(int size){List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<size;n++){HotCandidate c=candidate(n);rows.add(new FeedStore.Row(c.id,c,""));}return rows;}
    @Test public void frozenNeighborhoodCoversBeyondThreeWithoutIncludingCurrentEvent(){
        List<FeedStore.Row> rows=rows(100);ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows,rows.get(30).candidate);
        assertEquals(40,plan.candidates.size());assertEquals(candidate(31).id,plan.candidates.get(0).id);assertEquals(candidate(62).id,plan.candidates.get(31).id);
        assertEquals(candidate(29).id,plan.candidates.get(32).id);assertEquals(candidate(22).id,plan.candidates.get(39).id);
        assertTrue(plan.candidates.stream().noneMatch(c->c.id.equals(candidate(30).id)));assertEquals(candidate(0).id,rows.get(0).candidate.id);
    }
    @Test public void alternateSourceUsesSameEventPositionAndDoesNotCacheSiblingAsAnotherTopic(){
        List<FeedStore.Row> rows=rows(5);HotCandidate alternate=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/123","同事件另一个来源","","",false,1,null,100);
        FeedStore.Row row=rows.get(2);rows.set(2,new FeedStore.Row(row.eventId,row.candidate,"",Arrays.asList(row.candidate,alternate)));
        ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows,alternate);assertEquals(4,plan.candidates.size());assertEquals(candidate(3).id,plan.candidates.get(0).id);
        assertTrue(plan.candidates.stream().noneMatch(c->c.id.equals(row.candidate.id)));
    }
    @Test public void unrelatedOrChangedMetadataDoesNotAuthorizeOldNeighborhood(){
        HotCandidate original=candidate(2),changed=new HotCandidate(original.source,original.originalUrl,"标题已经变化","","",false,3,null,100);
        ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows(10),original);assertTrue(plan.matches(original.readerItem()));assertFalse(plan.matches(changed.readerItem()));
        assertFalse(plan.matches(candidate(3).readerItem()));assertTrue(ReaderPrefetchPlan.from(rows(10),changed).candidates.isEmpty());
    }
    @Test public void roundTripIsBoundedAndRejectsMalformedOrOversizedPayload()throws Exception{
        ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows(100),candidate(30)),restored=ReaderPrefetchPlan.decode(plan.encode());assertNotNull(restored);assertTrue(restored.matches(candidate(30).readerItem()));assertEquals(40,restored.candidates.size());
        assertNull(ReaderPrefetchPlan.decode(null));assertNull(ReaderPrefetchPlan.decode("{}"));assertNull(ReaderPrefetchPlan.decode("x".repeat(ReaderPrefetchPlan.MAX_BYTES+1)));
        JSONObject bad=new JSONObject(plan.encode());bad.getJSONArray("candidates").put(candidate(99).json());assertNull(ReaderPrefetchPlan.decode(bad.toString()));
    }
    @Test public void videoAndCrossPlatformUrlsAreNotSpeculativeTargets(){
        List<FeedStore.Row> rows=rows(4);HotCandidate video=new HotCandidate(Source.THEPAPER,candidate(1).originalUrl,"视频话题","","",true,2,null,100),foreign=new HotCandidate(Source.THEPAPER,"https://example.com/not-the-source","错误平台地址","","",false,3,null,100);
        rows.set(1,new FeedStore.Row(video.id,video,""));rows.set(2,new FeedStore.Row(foreign.id,foreign,""));ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows,candidate(0));assertEquals(1,plan.candidates.size());assertEquals(candidate(3).id,plan.candidates.get(0).id);
    }
    @Test public void openingDuringUpwardBrowsingKeepsTheFrozenFeedButChangesOnlyPreparationPriority(){
        List<FeedStore.Row> rows=rows(100);ReaderPrefetchPlan plan=ReaderPrefetchPlan.from(rows,candidate(50),-1,true);
        assertEquals(40,plan.candidates.size());assertEquals(candidate(49).id,plan.candidates.get(0).id);assertEquals(candidate(14).id,plan.candidates.get(35).id);assertEquals(candidate(51).id,plan.candidates.get(36).id);assertEquals(candidate(0).id,rows.get(0).candidate.id);assertEquals(40,ReaderPrefetchPlan.decode(plan.encode()).candidates.size());
    }
}
