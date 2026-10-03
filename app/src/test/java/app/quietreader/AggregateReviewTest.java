package app.quietreader;

import org.junit.Test;
import java.util.*;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

/** Independent acceptance cases: no live feeds, account state, or Android UI required. */
public class AggregateReviewTest {
    private static final long NOW=1_800_000_000_000L;
    private static final long HOUR=60L*60*1000;

    private Item item(Source source,String title,int id) {
        String host=source==Source.ZHIHU?"www.zhihu.com":source==Source.WEIBO?"weibo.com":
                source==Source.HUPU?"bbs.hupu.com":source==Source.SMZDM?"www.smzdm.com":
                source==Source.CLS?"www.cls.cn":"www.geekpark.net";
        return new Item(source,title,"https://"+host+"/"+id,"");
    }
    private AggregateRanker.Feed feed(Source source,Item... items) {
        return new AggregateRanker.Feed(source,Arrays.asList(items),NOW);
    }
    private List<AggregateRanker.Entry> rank(AggregateRanker.Feed... feeds) {
        return AggregateRanker.rank(Arrays.asList(feeds),NOW);
    }
    private Set<String> urls(AggregateRanker.Entry entry) {
        Set<String> urls=new HashSet<>();urls.add(entry.primary.url);
        for(Item alternative:entry.alternatives)urls.add(alternative.url);
        return urls;
    }
    private List<String> order(List<AggregateRanker.Entry> entries) {
        List<String> result=new ArrayList<>();for(AggregateRanker.Entry e:entries)result.add(e.primary.url);return result;
    }

    @Test public void emptyInputAndEmptyBoardsDoNotInventRows() {
        assertTrue(rank().isEmpty());assertTrue(rank(feed(Source.ZHIHU),feed(Source.WEIBO)).isEmpty());
    }
    @Test public void hundredIsAnUpperBoundAndEachPlatformRespectsItsQuota() {
        List<AggregateRanker.Feed> feeds=new ArrayList<>();
        for(Source source:Source.aggregateSources()) {
            List<Item> items=new ArrayList<>();
            for(int n=1;n<=30;n++)items.add(item(source,source.label+"独立事件编号"+(source.ordinal()*1000+n)+"最新消息",n));
            feeds.add(new AggregateRanker.Feed(source,items,NOW));
        }
        List<AggregateRanker.Entry> entries=AggregateRanker.rank(feeds,NOW);
        assertEquals(100,entries.size());
        Map<Source,Integer> counts=new EnumMap<>(Source.class);
        for(AggregateRanker.Entry e:entries)counts.put(e.primary.source,counts.getOrDefault(e.primary.source,0)+1);
        for(Map.Entry<Source,Integer> count:counts.entrySet())assertTrue("A single source may not crowd out the list",count.getValue()<=AggregateRanker.sourceLimit(count.getKey()));
    }
    @Test public void scarceSourcesDoNotRelaxTheirHardQuotaToFillOneHundred() {
        List<Item> items=new ArrayList<>();
        for(int n=1;n<=60;n++)items.add(item(Source.ZHIHU,"独立事件编号"+n+"发布最新进展",n));
        assertEquals(20,rank(new AggregateRanker.Feed(Source.ZHIHU,items,NOW)).size());
    }
    @Test public void duplicateUrlWithinOneSourceIsNeitherAnotherRowNorExtraHeat() {
        Item original=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        Item repeated=new Item(Source.ZHIHU,original.title,original.url,"999999999999热度");
        List<AggregateRanker.Entry> clean=rank(feed(Source.ZHIHU,original));
        List<AggregateRanker.Entry> duplicated=rank(feed(Source.ZHIHU,original,repeated,repeated));
        assertEquals(1,duplicated.size());assertEquals(clean.get(0).score,duplicated.get(0).score,0.000001);
    }
    @Test public void duplicateTitleWithinOneSourceDoesNotIncreaseScore() {
        String title="中国空间站完成新一轮科学实验任务";
        Item first=item(Source.ZHIHU,title,1),second=item(Source.ZHIHU,title,2);
        assertEquals(1,rank(feed(Source.ZHIHU,first,second)).size());
        assertEquals(rank(feed(Source.ZHIHU,first)).get(0).score,
                rank(feed(Source.ZHIHU,first,second)).get(0).score,0.000001);
    }
    @Test public void repeatedFeedCannotCountSamePlatformTwice() {
        Item original=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        AggregateRanker.Feed feed=feed(Source.ZHIHU,original);
        assertEquals(rank(feed).get(0).score,rank(feed,feed).get(0).score,0.000001);
    }
    @Test public void crossPlatformAgreementMergesAndPreservesOriginalDestinations() {
        String title="中国空间站完成新一轮科学实验任务";
        Item zhihu=item(Source.ZHIHU,title,1),weibo=item(Source.WEIBO,title,2);
        List<AggregateRanker.Entry> entries=rank(feed(Source.ZHIHU,zhihu),feed(Source.WEIBO,weibo));
        assertEquals(1,entries.size());assertEquals(new HashSet<>(Arrays.asList(zhihu.url,weibo.url)),urls(entries.get(0)));
        assertTrue(entries.get(0).score>rank(feed(Source.ZHIHU,zhihu)).get(0).score);
    }
    @Test public void punctuationDifferencesDoNotSplitSameEvent() {
        Item first=item(Source.ZHIHU,"中国空间站：完成新一轮科学实验任务",1);
        Item second=item(Source.WEIBO,"#中国空间站完成新一轮科学实验任务#",2);
        assertEquals(1,rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second)).size());
    }
    @Test public void distinctProductNumbersAreNotMergedDespiteOtherwiseIdenticalTitles() {
        Item first=item(Source.ZHIHU,"苹果正式发布 iPhone 16 新款手机，售价和配置公布",1);
        Item second=item(Source.WEIBO,"苹果正式发布 iPhone 17 新款手机，售价和配置公布",2);
        assertEquals(2,rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second)).size());
    }
    @Test public void contradictoryNegationMustNotBeFuzzyMerged() {
        Item first=item(Source.ZHIHU,"当地教育部门宣布学校明日恢复正常教学",1);
        Item second=item(Source.WEIBO,"当地教育部门宣布学校明日不恢复正常教学",2);
        assertEquals(2,rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second)).size());
    }
    @Test public void SameNewsTemplateInDifferentCitiesIsNotTheSameEvent() {
        Item first=item(Source.ZHIHU,"北京一小区发生火灾，目前已造成3人受伤",1);
        Item second=item(Source.WEIBO,"上海一小区发生火灾，目前已造成3人受伤",2);
        assertEquals(2,rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second)).size());
    }
    @Test public void decimalPointCannotBeDiscardedAsDecorativePunctuation() {
        Item first=item(Source.ZHIHU,"当地最新统计显示今年生产总值增长3.5%",1);
        Item second=item(Source.WEIBO,"当地最新统计显示今年生产总值增长35%",2);
        assertEquals(2,rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second)).size());
    }
    @Test public void signedTemperaturesAreDifferentEvents() {
        Item first=item(Source.ZHIHU,"当地气象台预报明日最低气温-3度",1);
        Item second=item(Source.WEIBO,"当地气象台预报明日最低气温3度",2);
        assertEquals(2,rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second)).size());
    }
    @Test public void rawHeatStringsHaveNoEffectOnCrossPlatformRanking() {
        Item first=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        Item second=item(Source.WEIBO,"深海科学考察船成功完成首次海底测绘",2);
        List<AggregateRanker.Entry> clean=rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,second));
        Item exaggerated=new Item(second.source,second.title,second.url,"999999999999亿热度");
        List<AggregateRanker.Entry> changed=rank(feed(Source.ZHIHU,first),feed(Source.WEIBO,exaggerated));
        assertEquals(order(clean),order(changed));
        for(int n=0;n<clean.size();n++)assertEquals(clean.get(n).score,changed.get(n).score,0.000001);
    }
    @Test public void olderThanOneDayCannotReturnAsCurrentHeat() {
        Item item=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        assertTrue(rank(new AggregateRanker.Feed(Source.ZHIHU,Arrays.asList(item),NOW-24*HOUR-1)).isEmpty());
        assertEquals(1,rank(new AggregateRanker.Feed(Source.ZHIHU,Arrays.asList(item),NOW-24*HOUR)).size());
    }
    @Test public void futureFetchTimestampCannotArtificiallyBoostRanking() {
        Item item=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        assertEquals(rank(feed(Source.ZHIHU,item)).get(0).score,
                rank(new AggregateRanker.Feed(Source.ZHIHU,Arrays.asList(item),NOW+10*HOUR)).get(0).score,0.000001);
    }
    @Test public void freshEquivalentBoardScoresAtLeastAsHighAsItsOldCache() {
        Item item=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        double fresh=rank(feed(Source.ZHIHU,item)).get(0).score;
        double old=rank(new AggregateRanker.Feed(Source.ZHIHU,Arrays.asList(item),NOW-12*HOUR)).get(0).score;
        assertTrue(fresh>old);
    }
    @Test public void concurrentFeedCompletionOrderDoesNotShuffleTiedRows() {
        AggregateRanker.Feed first=feed(Source.ZHIHU,item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1));
        AggregateRanker.Feed second=feed(Source.WEIBO,item(Source.WEIBO,"深海科学考察船成功完成首次海底测绘",2));
        assertEquals(order(rank(first,second)),order(rank(second,first)));
    }
    @Test public void entryKeepsOriginalSourceRatherThanPretendingToBeAggregate() {
        Item original=item(Source.ZHIHU,"中国空间站完成新一轮科学实验任务",1);
        AggregateRanker.Entry entry=rank(feed(Source.ZHIHU,original)).get(0);
        assertSame(original,entry.primary);assertEquals(Source.ZHIHU,entry.primary.source);
    }
}
