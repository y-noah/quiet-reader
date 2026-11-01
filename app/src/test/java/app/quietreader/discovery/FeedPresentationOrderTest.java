package app.quietreader.discovery;

import app.quietreader.Models.Source;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FeedPresentationOrderTest {
    private FeedStore.Row row(int n){HotCandidate c=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+n,"中文话题"+n,"","",false,n+1,null,100);return new FeedStore.Row(c.id,c,"");}
    @Test public void deliveryRotatesUnclassifiedRowsWithoutChangingTheirRelevanceOrder(){
        List<FeedStore.Row> rows=List.of(row(1),row(2),row(3),row(4));Map<String,Long> history=Map.of(rows.get(0).candidate.id,500L,rows.get(1).candidate.id,500L);
        assertEquals(List.of(rows.get(2),rows.get(3),rows.get(0),rows.get(1)),FeedPresentationOrder.arrange(rows,history,1000));
    }
    @Test public void exhaustedPoolRecyclesOldestInsteadOfAlwaysReturningTopRanks(){
        List<FeedStore.Row> rows=List.of(row(1),row(2),row(3));Map<String,Long> history=Map.of(rows.get(0).candidate.id,900L,rows.get(1).candidate.id,500L,rows.get(2).candidate.id,700L);
        assertEquals(List.of(rows.get(1),rows.get(2),rows.get(0)),FeedPresentationOrder.arrange(rows,history,1000));
    }
    @Test public void futureClockRecordsCannotSuppressUnseenContent(){
        List<FeedStore.Row> rows=List.of(row(1),row(2));assertEquals(rows,FeedPresentationOrder.arrange(rows,Map.of(rows.get(0).candidate.id,5000L),1000));
    }
    @Test public void groupedAlternateEntryCountsAsDeliveredWithoutAddingInterestFeedback(){
        FeedStore.Row first=row(1),alternate=row(2),unseen=row(3);FeedStore.Row group=new FeedStore.Row(first.eventId,first.candidate,"",List.of(first.candidate,alternate.candidate));
        assertEquals(List.of(unseen,group),FeedPresentationOrder.arrange(List.of(group,unseen),Map.of(alternate.candidate.id,500L),1000));
    }
    @Test public void deliveredPositiveCannotJumpAheadOfUnseenNeutralFromTheSameSource(){
        FeedStore.Row liked=row(1).withInterestBand(0),unseen=row(2).withInterestBand(1),negative=row(3).withInterestBand(2);
        assertEquals(List.of(unseen,liked,negative),FeedPresentationOrder.arrange(List.of(liked,unseen,negative),Map.of(liked.candidate.id,900L),1000));
        assertEquals(List.of(liked,unseen,negative),FeedPresentationOrder.arrange(List.of(liked,unseen,negative),Collections.emptyMap(),1000));
    }
}
