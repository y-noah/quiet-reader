package app.quietreader.discovery;

import app.quietreader.Models;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedBatchTest {
    private List<FeedStore.Row> ranked(int count){List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<count;n++){HotCandidate c=new HotCandidate(Models.Source.THEPAPER,"https://example.org/"+n,"独立新闻事件"+n,"","",false,n+1,null,1000);rows.add(new FeedStore.Row(c.id,c,"",List.of(c),0));}return rows;}
    @Test public void boundedPresentationPreservesPoolForNextExplicitRefresh(){
        List<FeedStore.Row> ranked=ranked(65),first=FeedBatch.first(ranked);
        assertEquals(20,first.size());assertEquals(65,ranked.size());
        Map<String,FeedRefreshOrder.Recent> history=new HashMap<>();for(FeedStore.Row row:first)history.put(row.eventId,new FeedRefreshOrder.Recent(1000,0,0));
        List<FeedStore.Row> next=FeedBatch.first(FeedRefreshOrder.forRows(ranked,history,1001));
        assertEquals(20,next.size());assertEquals(ranked.get(20).eventId,next.get(0).eventId);
        assertTrue(Collections.disjoint(first,next));assertEquals(ranked.get(0).eventId,first.get(0).eventId);
        ranked.clear();assertEquals("The visible batch owns its membership",20,first.size());
        try{first.clear();fail("immutable presentation required");}catch(UnsupportedOperationException expected){}
    }
    @Test public void limitedSupplyIsNotPaddedWithDuplicates(){assertTrue(FeedBatch.first(List.of()).isEmpty());assertEquals(7,FeedBatch.first(ranked(7)).size());}
    @Test public void oneSwipeBudgetAdaptsToViewportAndTypography(){
        assertEquals(12,FeedBatch.countForHeight(70,800,n->100));
        assertEquals(8,FeedBatch.countForHeight(70,500,n->100));
        assertEquals(6,FeedBatch.countForHeight(70,800,n->200));
        assertEquals(4,FeedBatch.countForHeight(70,800,n->n==0?600:200));
        assertEquals(1,FeedBatch.countForHeight(70,800,n->2000));
        assertEquals(3,FeedBatch.countForHeight(3,800,n->100));
        assertEquals(0,FeedBatch.countForHeight(0,800,n->{throw new AssertionError("No cards to measure");}));
    }
    @Test public void adaptiveBatchDoesNotDiscardUnshownCandidates(){
        List<FeedStore.Row> pool=ranked(70);int capacity=FeedBatch.countForHeight(pool.size(),600,n->140);
        List<FeedStore.Row> batch=FeedBatch.first(pool,capacity);assertEquals(6,batch.size());
        Map<String,FeedRefreshOrder.Recent> history=new HashMap<>();for(FeedStore.Row row:batch)history.put(row.eventId,new FeedRefreshOrder.Recent(1000,0,0));
        assertEquals(pool.get(6).eventId,FeedBatch.first(FeedRefreshOrder.forRows(pool,history,1001),capacity).get(0).eventId);
        assertEquals(70,pool.size());assertEquals(1,FeedBatch.countForHeight(70,Integer.MAX_VALUE,n->Integer.MAX_VALUE));
    }
}
