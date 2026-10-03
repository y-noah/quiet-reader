package app.quietreader;

import org.junit.Test;
import java.util.*;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

/** Independent policy regression; artificial rows do not prove real-world popularity. */
public class Top100PolicyTest {
    private static final long NOW=1_800_000_000_000L;
    private static final long MINUTE=60_000L;
    private static final long HOUR=60*MINUTE;

    private Item item(Source source,String title,int id) {
        return new Item(source,title,"https://example.test/"+source.name()+"/"+id,"");
    }
    private AggregateRanker.Feed feed(Source source,int count,long age) {
        List<Item> rows=new ArrayList<>();
        for(int n=1;n<=count;n++)rows.add(item(source,source.name()+"独立事件"+n,n));
        return new AggregateRanker.Feed(source,rows,NOW-age);
    }
    private AggregateRanker.Feed feed(Source source,long at,Item... rows) {
        return new AggregateRanker.Feed(source,Arrays.asList(rows),at);
    }
    private List<AggregateRanker.Entry> rank(AggregateRanker.Feed... feeds) {
        return AggregateRanker.rank(Arrays.asList(feeds),NOW);
    }
    private List<String> urls(List<AggregateRanker.Entry> rows) {
        List<String> result=new ArrayList<>();for(AggregateRanker.Entry row:rows)result.add(row.primary.url);return result;
    }
    private int expectedCap(Source source) {
        if(source==Source.ZHIHU||source==Source.WEIBO)return 20;
        if(source==Source.DOUBAN||source==Source.HUPU||source==Source.CLS||source==Source.WALLSTREET)return 12;
        return 8;
    }

    @Test public void enoughDistinctEligibleNewsYieldsExactlyOneHundred() {
        List<AggregateRanker.Feed> feeds=new ArrayList<>();
        for(Source source:Source.aggregateSources())feeds.add(feed(source,50,0));
        List<AggregateRanker.Entry> result=AggregateRanker.rank(feeds,NOW);
        assertEquals(100,result.size());
        assertEquals(100,new HashSet<>(urls(result)).size());
        for(int i=1;i<result.size();i++)assertTrue(result.get(i-1).score>=result.get(i).score);
    }

    @Test public void missingBoardsNeverCauseInventedRowsOrQuotaRelaxation() {
        assertTrue(rank().isEmpty());
        assertEquals(3,rank(feed(Source.ZHIHU,3,0)).size());
        assertEquals(20,rank(feed(Source.ZHIHU,50,0)).size());
        assertEquals(8,rank(feed(Source.JUEJIN,50,0)).size());
    }

    @Test public void eachSourceAloneHonorsItsOwnHardCap() {
        for(Source source:Source.aggregateSources())
            assertEquals(source.name(),expectedCap(source),rank(feed(source,50,0)).size());
    }

    @Test public void equalRankTechnologyAndShoppingDoNotEqualBroadSocialTopics() {
        List<AggregateRanker.Entry> result=rank(feed(Source.JUEJIN,1,0),feed(Source.SMZDM,1,0),
                feed(Source.ZHIHU,1,0),feed(Source.WEIBO,1,0));
        assertEquals(Source.ZHIHU,result.get(0).primary.source);
        assertEquals(Source.WEIBO,result.get(1).primary.source);
        assertTrue(result.get(1).score>2*result.get(2).score);
        assertEquals(Source.JUEJIN,result.get(2).primary.source);
    }

    @Test public void changedRawHeatStringsCannotOverrideEditorialWeights() {
        Item original=item(Source.JUEJIN,"技术文章",1);
        Item inflated=new Item(original.source,original.title,original.url,"999999999999亿热度");
        AggregateRanker.Feed broad=feed(Source.ZHIHU,1,0);
        List<AggregateRanker.Entry> a=rank(broad,feed(Source.JUEJIN,NOW,original));
        List<AggregateRanker.Entry> b=rank(broad,feed(Source.JUEJIN,NOW,inflated));
        assertEquals(urls(a),urls(b));assertEquals(a.get(1).score,b.get(1).score,0.000001);
    }

    @Test public void matchingCrossPlatformHeadlineKeepsRealDestinationsAndAddsOnlyOneVoteEach() {
        String title="同一公共事件的完整标题";
        Item z=item(Source.ZHIHU,title,1),w=item(Source.WEIBO,title,2),j=item(Source.JUEJIN,title,3);
        AggregateRanker.Feed zf=feed(Source.ZHIHU,NOW,z,z);
        List<AggregateRanker.Entry> rows=rank(zf,zf,feed(Source.WEIBO,NOW,w),feed(Source.JUEJIN,NOW,j));
        assertEquals(1,rows.size());
        AggregateRanker.Entry row=rows.get(0);
        assertSame(z,row.primary);assertEquals(3,row.alternatives.size());
        assertEquals(100+0.4*100+0.4*35,row.score,0.000001);
        assertEquals(new HashSet<>(Arrays.asList(z.url,w.url,j.url)),new HashSet<>(Arrays.asList(
                row.alternatives.get(0).url,row.alternatives.get(1).url,row.alternatives.get(2).url)));
    }

    @Test public void decimalAndSignedNumbersNeverCollapseIntoOtherNews() {
        List<AggregateRanker.Entry> rows=rank(feed(Source.ZHIHU,NOW,
                item(Source.ZHIHU,"增长3.5%",1),item(Source.ZHIHU,"温度-3度",2)),
                feed(Source.WEIBO,NOW,item(Source.WEIBO,"增长35%",3),item(Source.WEIBO,"温度3度",4)));
        assertEquals(4,rows.size());
    }

    @Test public void clockTicksInsideSameAgeBucketDoNotChangeScoresOrOrder() {
        List<AggregateRanker.Feed> feeds=Arrays.asList(feed(Source.ZHIHU,3,2*MINUTE),
                feed(Source.WEIBO,3,4*MINUTE),feed(Source.JUEJIN,3,6*MINUTE));
        List<AggregateRanker.Entry> a=AggregateRanker.rank(feeds,NOW);
        List<AggregateRanker.Entry> b=AggregateRanker.rank(feeds,NOW+MINUTE);
        assertEquals(urls(a),urls(b));
        for(int i=0;i<a.size();i++)assertEquals(a.get(i).score,b.get(i).score,0);
    }

    @Test public void cacheAgeHalvesWeightAtTwelveHoursAndExpiresAfterOneDay() {
        double fresh=rank(feed(Source.ZHIHU,1,0)).get(0).score;
        assertEquals(fresh/2,rank(feed(Source.ZHIHU,1,12*HOUR)).get(0).score,0.000001);
        assertEquals(1,rank(feed(Source.ZHIHU,1,24*HOUR)).size());
        assertTrue(rank(feed(Source.ZHIHU,1,24*HOUR+1)).isEmpty());
        assertEquals(fresh,rank(feed(Source.ZHIHU,1,-HOUR)).get(0).score,0.000001);
    }

    @Test public void newestSourceSnapshotWinsRegardlessOfFeedCompletionOrder() {
        AggregateRanker.Feed old=feed(Source.ZHIHU,NOW-HOUR,item(Source.ZHIHU,"旧快照事件",1));
        AggregateRanker.Feed fresh=feed(Source.ZHIHU,NOW,item(Source.ZHIHU,"新快照事件",2));
        assertEquals(urls(rank(old,fresh)),urls(rank(fresh,old)));
        assertEquals("新快照事件",rank(old,fresh).get(0).primary.title);
    }

    @Test public void feedArrivalOrderDoesNotChangeTiedHeadlines() {
        List<AggregateRanker.Feed> feeds=new ArrayList<>();
        for(Source source:Source.aggregateSources())feeds.add(feed(source,15,0));
        List<String> expected=urls(AggregateRanker.rank(feeds,NOW));
        Collections.shuffle(feeds,new Random(713));
        assertEquals(expected,urls(AggregateRanker.rank(feeds,NOW)));
    }

    @Test public void ifanrReplacesOnlyTheIndividualGeekparkTab() {
        assertArrayEquals(new Source[]{Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.IFANR},Source.displayOrder());
        assertFalse(Source.GEEKPARK.visible());assertTrue(Source.IFANR.visible());
        assertTrue(Arrays.asList(Source.aggregateSources()).contains(Source.GEEKPARK));
        assertTrue(Arrays.asList(Source.aggregateSources()).contains(Source.IFANR));
    }
}
