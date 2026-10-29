package app.quietreader.discovery;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FeedRefreshOrderTest {
    private static final long NOW=100_000_000L;
    private List<String> order(List<List<String>> bands,Map<String,FeedRefreshOrder.Recent> history){return FeedRefreshOrder.arrange(bands,history::get,NOW);}
    @Test public void unseenReplacesRecentExposureWithinSameInterestBand(){List<String> ranked=List.of("shown-best","new-best","new-next","shown-next");Map<String,FeedRefreshOrder.Recent> history=Map.of("shown-best",new FeedRefreshOrder.Recent(NOW-1000,0,0),"shown-next",new FeedRefreshOrder.Recent(NOW-500,0,0));assertEquals(List.of("new-best","new-next","shown-best","shown-next"),order(List.of(ranked),history));assertEquals("shown-best",ranked.get(0));}
    @Test public void readingSuppressesMoreThanJustSeeing(){Map<String,FeedRefreshOrder.Recent> history=Map.of("read",new FeedRefreshOrder.Recent(NOW-2000,NOW-1000,0),"shown",new FeedRefreshOrder.Recent(NOW-1000,0,0));assertEquals(List.of("new","shown","read"),order(List.of(List.of("read","shown","new")),history));}
    @Test public void noveltyNeverPromotesLowerQualityAcrossBands(){Map<String,FeedRefreshOrder.Recent> history=Map.of("strong-read",new FeedRefreshOrder.Recent(NOW-1000,NOW-1000,0));assertEquals(List.of("strong-read","neutral-new","negative-new"),order(List.of(List.of("strong-read"),List.of("neutral-new"),List.of("negative-new")),history));}
    @Test public void allSeenPoolDoesNotShuffleOrFabricateNewItems(){List<List<String>> bands=List.of(List.of("best","next"));Map<String,FeedRefreshOrder.Recent> history=Map.of("best",new FeedRefreshOrder.Recent(NOW-1000,0,0),"next",new FeedRefreshOrder.Recent(NOW-500,0,0));assertEquals(List.of("best","next"),order(bands,history));assertEquals(order(bands,history),order(bands,history));}
    @Test public void expiryRestoresNormalRelevanceOrder(){Map<String,FeedRefreshOrder.Recent> history=Map.of("a",new FeedRefreshOrder.Recent(NOW-FeedRefreshOrder.SHOWN_COOLDOWN_MS,0,0),"b",new FeedRefreshOrder.Recent(0,NOW-FeedRefreshOrder.READ_COOLDOWN_MS,0));assertEquals(List.of("a","b","new"),order(List.of(List.of("a","b","new")),history));}
    @Test public void onlyVerifiedNewerProgressBypassesCooldown(){Map<String,FeedRefreshOrder.Recent> history=Map.of("update",new FeedRefreshOrder.Recent(NOW-2000,NOW-1000,NOW-500),"no-progress",new FeedRefreshOrder.Recent(NOW-1000,0,0));assertEquals(List.of("update","new","no-progress"),order(List.of(List.of("no-progress","update","new")),history));}
    @Test public void OldOrFutureProgressCannotBypassCooldown(){for(long progress:new long[]{NOW-2000,NOW-1000,NOW+1000}){Map<String,FeedRefreshOrder.Recent> history=Map.of("read",new FeedRefreshOrder.Recent(NOW-2000,NOW-1000,progress));assertEquals(List.of("new","read"),order(List.of(List.of("read","new")),history));}}
    @Test public void futureOrNegativeHistoryDoesNotHideCandidatesIndefinitely(){Map<String,FeedRefreshOrder.Recent> history=Map.of("a",new FeedRefreshOrder.Recent(NOW+1000,-1000,0));assertEquals(List.of("a","b"),order(List.of(List.of("a","b")),history));}
    @Test public void noHistoryRetainsTheActualModelOrder(){List<List<String>> bands=List.of(List.of("a","b"),List.of("c"));assertEquals(List.of("a","b","c"),order(bands,Map.of()));}
}
