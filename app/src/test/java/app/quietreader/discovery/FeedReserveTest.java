package app.quietreader.discovery;

import app.quietreader.Models.Source;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FeedReserveTest {
    private List<FeedStore.Row> pool(int count){
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<count;n++){
            HotCandidate c=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+n,"新闻"+n,"","",false,n+1,null,1000);
            rows.add(new FeedStore.Row(c.id,c,"",List.of(c),0));
        }return rows;
    }
    @Test public void fourPreparedRoundsDoNotRepeatOrCountAsExposure(){
        List<FeedStore.Row> pool=pool(100);FeedReserve reserve=new FeedReserve();Set<String> seen=new HashSet<>();
        List<FeedStore.Row> visible=FeedBatch.first(pool);reserve.accepted(visible);visible.forEach(row->seen.add(row.eventId));reserve.stage(pool,"all");
        assertEquals(80,reserve.future().size());assertEquals(20,reserve.next("all").size());
        assertEquals(reserve.next("all"),reserve.next("all"));
        for(int n=0;n<4;n++){
            List<FeedStore.Row> ordered=FeedReserve.promote(pool,reserve.next("all"));visible=FeedBatch.first(ordered);
            for(FeedStore.Row row:visible)assertTrue("No earlier screen repeats",seen.add(row.eventId));
            reserve.accepted(visible);reserve.stage(ordered,"all");
        }
        assertEquals(100,seen.size());assertEquals(100,pool.size());
    }
    @Test public void sourceChangesAndExhaustionNeverInventEntries(){
        List<FeedStore.Row> pool=pool(23);FeedReserve reserve=new FeedReserve();reserve.accepted(FeedBatch.first(pool));reserve.stage(pool,"all");
        assertEquals(3,reserve.future().size());assertTrue(reserve.next("WEIBO").isEmpty());
        assertSame(pool,FeedReserve.promote(pool,List.of("deleted-event")));
        reserve.accepted(reserve.future());reserve.stage(pool,"all");assertTrue(reserve.future().isEmpty());
    }
    @Test public void stagedUpdatesKeepCurrentAndPreviousPresentationsOut(){
        List<FeedStore.Row> pool=pool(120);FeedReserve reserve=new FeedReserve();reserve.accepted(FeedBatch.first(pool));
        Collections.reverse(pool);reserve.stage(pool,"all");assertEquals(pool.get(0).eventId,reserve.next("all").get(0));
        Set<String> initial=new HashSet<>();for(FeedStore.Row row:FeedBatch.first(pool(120)))initial.add(row.eventId);
        for(FeedStore.Row row:reserve.future())assertFalse(initial.contains(row.eventId));
        reserve.clear();assertTrue(reserve.next("all").isEmpty());
    }
    @Test public void preparedBodiesSurviveLanguageEvidenceButRejectChangedContent(){
        List<FeedStore.Row> original=pool(100);FeedReserve reserve=new FeedReserve();reserve.accepted(FeedBatch.first(original));reserve.stage(original,"all");
        List<FeedStore.Row> current=new ArrayList<>(original);FeedStore.Row old=original.get(20);HotCandidate c=old.candidate;
        HotCandidate verified=new HotCandidate(c.source,c.originalUrl,c.title,c.snippet,c.rawHeat,c.video,c.sourceRank,c.publishedAt,c.observedAt,ContentLanguage.Body.CHINESE);
        current.set(20,new FeedStore.Row(old.eventId,verified,"",List.of(verified),0));
        List<FeedStore.Row> promoted=FeedReserve.promotePrepared(current,reserve.plan("all"));
        assertSame(verified,promoted.get(0).candidate);
        HotCandidate changed=new HotCandidate(c.source,c.originalUrl,"新标题",c.snippet,c.rawHeat,c.video,c.sourceRank,c.publishedAt,c.observedAt);
        current.set(20,new FeedStore.Row(old.eventId,changed,"",List.of(changed),0));
        promoted=FeedReserve.promotePrepared(current,reserve.plan("all"));
        assertEquals(original.get(21).eventId,promoted.get(0).eventId);
        assertEquals(original.get(40).eventId,promoted.get(19).eventId);
        assertTrue(reserve.plan("WEIBO").isEmpty());
    }    @Test public void intermediateRankingsKeepPreparedOrderUntilCollectionResetsIt(){
        List<FeedStore.Row> rows=pool(120);FeedReserve reserve=new FeedReserve();reserve.accepted(FeedBatch.first(rows));reserve.stage(rows,"all");
        List<String> prepared=reserve.next("all");Collections.reverse(rows);reserve.stage(rows,"all");assertEquals(prepared,reserve.next("all"));
        reserve.clear();reserve.stage(rows,"all");assertEquals(rows.get(0).eventId,reserve.next("all").get(0));
    }    @Test public void failedFirstScreenSlotFallsThroughToTheNextPreparedScreen(){
        List<FeedStore.Row> rows=pool(120);FeedReserve reserve=new FeedReserve();reserve.accepted(FeedBatch.first(rows));reserve.stage(rows,"all");
        List<FeedStore.Row> promoted=FeedReserve.promotePrepared(rows,reserve.plan("all"));
        // Acquisition fails after staging; removing that concrete failed body must
        // keep the next future body ahead of already presented original ranks 0..19.
        promoted.remove(3);Set<String> presented=new HashSet<>();for(FeedStore.Row row:FeedBatch.first(rows))presented.add(row.eventId);
        for(int n=0;n<79;n++){assertFalse(presented.contains(promoted.get(n).eventId));assertEquals(rows.get(20+n+(n>=3?1:0)).eventId,promoted.get(n).eventId);}
    }
}
