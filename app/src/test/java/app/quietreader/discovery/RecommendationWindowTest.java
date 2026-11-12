package app.quietreader.discovery;

import app.quietreader.Models.Source;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class RecommendationWindowTest {
    private List<FeedStore.Row> rows(int count){
        List<FeedStore.Row> rows=new ArrayList<>();
        for(int n=0;n<count;n++){HotCandidate c=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/"+(100000+n),"推荐候选 "+n,"","",false,n+1,null,1);rows.add(new FeedStore.Row(c.id,c,"按兴趣匹配"));}
        return rows;
    }
    @Test public void fiveBatchesThenExhaustedWithoutRecyclingOrTailBackfill(){
        List<FeedStore.Row> pool=rows(300);Map<String,Boolean> decisions=new HashMap<>();Set<String> delivered=new HashSet<>();
        for(int round=0;round<5;round++){
            List<FeedStore.Row> selected=RecommendationWindow.select(pool,decisions,delivered);
            List<FeedStore.Row> batch=FeedBatch.first(RecommendationWindow.undelivered(selected,delivered));
            assertEquals(15,batch.size());for(FeedStore.Row row:batch)assertTrue(delivered.add(row.candidate.id));
        }
        assertEquals(75,delivered.size());
        assertTrue(RecommendationWindow.undelivered(RecommendationWindow.select(pool,decisions,delivered),delivered).isEmpty());
        // A restart restores decisions, not just the current screen.
        Map<String,Boolean> restored=new HashMap<>(decisions);
        assertTrue(RecommendationWindow.undelivered(RecommendationWindow.select(pool.subList(75,300),restored,delivered),delivered).isEmpty());
    }
    @Test public void onlyGenuinelyNewContentReopensAnExhaustedWindow(){
        List<FeedStore.Row> pool=rows(300);Map<String,Boolean> decisions=new HashMap<>();Set<String> delivered=new HashSet<>();
        for(FeedStore.Row row:RecommendationWindow.select(pool,decisions,delivered))delivered.add(row.candidate.id);
        List<FeedStore.Row> updated=rows(301);
        List<FeedStore.Row> fresh=RecommendationWindow.undelivered(RecommendationWindow.select(updated,decisions,delivered),delivered);
        assertEquals(1,fresh.size());assertEquals(updated.get(300).eventId,fresh.get(0).eventId);
    }
    @Test public void explicitDislikesAreNeverUsedToFillTheQuota(){
        List<FeedStore.Row> pool=rows(20);for(int n=3;n<pool.size();n++)pool.set(n,pool.get(n).withInterestBand(2));
        assertEquals(3,RecommendationWindow.select(pool,new HashMap<>(),Collections.emptySet()).size());
    }
    @Test public void newArrivalAfterExhaustionStartsAFreshModelAllowance(){
        List<FeedStore.Row> pool=rows(300);Map<String,Boolean> decisions=new HashMap<>();Set<String> delivered=new HashSet<>();
        RecommendationWindow.select(pool,decisions,delivered);
        assertFalse(RecommendationWindow.startsNewWindow(rows(301),decisions,delivered));
        for(FeedStore.Row row:RecommendationWindow.select(pool,decisions,delivered))delivered.add(row.candidate.id);
        assertFalse(RecommendationWindow.startsNewWindow(pool,decisions,delivered));
        assertTrue(RecommendationWindow.startsNewWindow(rows(301),decisions,delivered));
    }
    @Test public void betterNewContentCanDisplaceAnUnshownChoiceWithoutLaterBackfill(){
        List<FeedStore.Row> pool=rows(300);Map<String,Boolean> decisions=new HashMap<>();Set<String> delivered=new HashSet<>();
        RecommendationWindow.select(pool,decisions,delivered);
        List<FeedStore.Row> updated=new ArrayList<>(pool);FeedStore.Row newcomer=rows(301).get(300);updated.add(0,newcomer);
        List<FeedStore.Row> selected=RecommendationWindow.select(updated,decisions,delivered);
        assertEquals(75,selected.size());assertSame(newcomer,selected.get(0));assertFalse(selected.contains(pool.get(74)));
        for(FeedStore.Row row:selected)delivered.add(row.candidate.id);
        assertTrue(RecommendationWindow.undelivered(RecommendationWindow.select(updated,decisions,delivered),delivered).isEmpty());
    }
    @Test public void firstModelPassCanReplaceProvisionalChoicesButCannotExpandPastSeventyFive(){
        List<FeedStore.Row> pool=rows(300);Map<String,Boolean> decisions=new HashMap<>();Set<String> delivered=new HashSet<>();
        List<FeedStore.Row> initial=RecommendationWindow.select(pool,decisions,delivered);
        for(FeedStore.Row row:FeedBatch.first(initial))delivered.add(row.candidate.id);
        Set<String> provisional=new HashSet<>(decisions.keySet());
        List<FeedStore.Row> ranked=new ArrayList<>(pool);Collections.reverse(ranked);
        List<FeedStore.Row> refined=RecommendationWindow.select(ranked,decisions,delivered,provisional,true);
        List<FeedStore.Row> remaining=RecommendationWindow.undelivered(refined,delivered);
        assertEquals(60,remaining.size());assertSame(pool.get(299),remaining.get(0));
        for(FeedStore.Row row:remaining)delivered.add(row.candidate.id);
        assertEquals(75,delivered.size());
        assertTrue(RecommendationWindow.undelivered(RecommendationWindow.select(pool,decisions,delivered),delivered).isEmpty());
    }
}
