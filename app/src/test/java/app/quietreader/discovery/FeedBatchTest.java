package app.quietreader.discovery;

import app.quietreader.Models;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class FeedBatchTest {
    private List<FeedStore.Row> ranked(int count){List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<count;n++){HotCandidate c=new HotCandidate(Models.Source.THEPAPER,"https://example.org/"+n,"独立新闻事件"+n,"","",false,n+1,null,1000);rows.add(new FeedStore.Row(c.id,c,"",List.of(c),0));}return rows;}
    @Test public void boundedPresentationPreservesPoolForNextExplicitRefresh(){
        List<FeedStore.Row> ranked=ranked(65),first=FeedBatch.first(ranked);
        assertEquals(15,first.size());assertEquals(65,ranked.size());
        Map<String,FeedRefreshOrder.Recent> history=new HashMap<>();for(FeedStore.Row row:first)history.put(row.eventId,new FeedRefreshOrder.Recent(1000,0,0));
        List<FeedStore.Row> next=FeedBatch.first(FeedRefreshOrder.forRows(ranked,history,1001));
        assertEquals(15,next.size());assertEquals(ranked.get(15).eventId,next.get(0).eventId);
        assertTrue(Collections.disjoint(first,next));assertEquals(ranked.get(0).eventId,first.get(0).eventId);
        ranked.clear();assertEquals("The visible batch owns its membership",15,first.size());
        try{first.clear();fail("immutable presentation required");}catch(UnsupportedOperationException expected){}
    }
    @Test public void limitedSupplyIsNotPaddedWithDuplicates(){assertTrue(FeedBatch.first(List.of()).isEmpty());assertEquals(7,FeedBatch.first(ranked(7)).size());}
    @Test public void fixedFifteenKeepsTheRestForRefresh(){
        List<FeedStore.Row> pool=ranked(70);assertEquals(15,FeedBatch.first(pool,100).size());
        assertEquals(7,FeedBatch.first(pool,7).size());assertEquals(70,pool.size());
    }
}
