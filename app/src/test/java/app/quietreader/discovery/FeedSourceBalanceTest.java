package app.quietreader.discovery;
import app.quietreader.Models.Source;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class FeedSourceBalanceTest {
    private FeedStore.Row row(Source source,int n,int band){
        String host=source==Source.BAIDU?"www.baidu.com":source==Source.WEIBO?"weibo.com":"www.zhihu.com";
        HotCandidate c=new HotCandidate(source,"https://"+host+"/"+n,"中文新闻"+n,"","",false,n+1,null,1000);
        return new FeedStore.Row(c.id,c,"",List.of(c),band);
    }
    private List<FeedStore.Row> pool(){List<FeedStore.Row> rows=new ArrayList<>();for(Source s:List.of(Source.BAIDU,Source.WEIBO,Source.ZHIHU))for(int n=0;n<50;n++)rows.add(row(s,n,1));return rows;}
    private long count(List<FeedStore.Row> rows,Source s){return rows.stream().filter(r->r.candidate.source==s).count();}
    @Test public void concentratedHistoryAndPreparedQueueCannotMonopolizeNextScreen(){
        List<FeedStore.Row> rows=pool();Map<String,Long> history=new HashMap<>();for(FeedStore.Row r:rows)if(r.candidate.source!=Source.BAIDU)history.put(r.candidate.id,900L);
        Set<String> firstSeen=new HashSet<>();
        for(int round=0;round<7;round++){
            rows=FeedPresentationOrder.arrange(rows,history,1000+round);
            List<FeedStore.Row> batch=FeedBatch.first(rows);
            for(Source s:List.of(Source.BAIDU,Source.WEIBO,Source.ZHIHU))assertTrue(count(batch,s)==5);
            for(FeedStore.Row r:batch){assertTrue(firstSeen.add(r.eventId));history.put(r.candidate.id,1000L+round);}
        }
    }
    @Test public void explicitLessLimitsOccupancyWithoutBlockingSource()throws Exception{
        PlatformPreference preference=new PlatformPreference(new JSONArray().put(new JSONObject().put("action","LESS").put("scope","source").put("target","BAIDU")));
        List<FeedStore.Row> batch=FeedBatch.first(FeedSourceBalance.arrange(pool(),preference));
        assertEquals(2,count(batch,Source.BAIDU));assertEquals(15,batch.size());
    }
    @Test public void interestBeatsDiversityAndSingleSourceStillWorks(){
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<20;n++)rows.add(row(Source.BAIDU,n,0));for(int n=0;n<20;n++)rows.add(row(Source.ZHIHU,n,2));
        assertEquals(15,count(FeedBatch.first(FeedSourceBalance.arrange(rows)),Source.BAIDU));
        assertEquals(rows,FeedSourceBalance.arrange(rows));
    }
    @Test public void everyReserveRoundIsBalancedAndOptInStillFiltersTextSourceVideos(){
        FeedReserve reserve=new FeedReserve();reserve.stage(pool(),"all");
        for(int start=0;start<60;start+=15)for(Source s:List.of(Source.BAIDU,Source.WEIBO,Source.ZHIHU)){long share=count(reserve.future().subList(start,start+15),s);assertTrue(share==5);}
        HotCandidate video=new HotCandidate(Source.BILIBILI,"https://www.bilibili.com/video/BV1234567890","中文视频","","",true,1,null,1000);
        assertFalse(HotSourceCatalog.defaultEnabled(Source.BILIBILI));assertTrue(HotSourceCatalog.contentEligible(video));
        assertEquals(1,CandidateOrder.arrange(List.of(video),new JSONArray(),false).size());
        assertTrue(new FeedReadiness().complete(List.of(new FeedStore.Row(video.id,video,"")),c->"",1000));
        HotCandidate other=new HotCandidate(Source.WEIBO,"https://weibo.com/123","中文视频","","",true,1,null,1000);
        assertFalse(HotSourceCatalog.contentEligible(other));
    }
    @Test public void repeatedRotationsPreserveMembershipAndStableSourceOrder(){
        Random random=new Random(20261101);
        for(int run=0;run<100;run++){
            List<FeedStore.Row> rows=pool();Collections.shuffle(rows,random);Map<String,Long> history=new HashMap<>();
            for(FeedStore.Row row:rows)if(random.nextBoolean())history.put(row.candidate.id,(long)random.nextInt(900)+1);
            List<FeedStore.Row> arranged=FeedPresentationOrder.arrange(rows,history,1000);
            assertEquals(new HashSet<>(rows),new HashSet<>(arranged));assertEquals(rows.size(),arranged.size());
            for(int offset=0;offset+15<=150;offset+=15)for(Source source:List.of(Source.BAIDU,Source.WEIBO,Source.ZHIHU)){long share=count(arranged.subList(offset,offset+15),source);assertTrue(share==5);}
        }
    }
    private List<FeedStore.Row> unequalPool(){
        List<FeedStore.Row> rows=new ArrayList<>();Source[] sources={Source.WEIBO,Source.ZHIHU,Source.BAIDU,Source.HUPU,Source.CLS,Source.BBC,Source.NYT,Source.TIEBA,Source.V2EX,Source.ITHOME};
        for(Source source:sources)for(int n=0;n<(source==Source.WEIBO?100:10);n++)rows.add(row(source,n,1));return rows;
    }
    @Test public void hundredItemSourceHasSameColdStartShareAsTenItemSources(){
        List<FeedStore.Row> rows=unequalPool();Map<Source,Long> totals=new EnumMap<>(Source.class);
        for(int turn=0;turn<100;turn++){
            List<FeedStore.Row> batch=FeedBatch.first(FeedSourceBalance.arrange(rows,null,turn));assertEquals(15,batch.size());
            for(FeedStore.Row row:batch)totals.merge(row.candidate.source,1L,Long::sum);
            for(Source source:totals.keySet()){long size=count(batch,source);assertTrue(size==1||size==2);}
        }
        assertEquals(10,totals.size());for(long total:totals.values())assertTrue("equal long-run allocation: "+totals,Math.abs(total-150)<=1);
    }
    @Test public void largeFavoredV2exPoolCannotOccupyTheFirstFiveCards()throws Exception{
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<100;n++)rows.add(row(Source.V2EX,n,0));
        for(Source source:List.of(Source.WEIBO,Source.BAIDU,Source.ZHIHU,Source.BBC,Source.CLS))for(int n=0;n<20;n++)rows.add(row(source,n,1));
        PlatformPreference more=new PlatformPreference(new JSONArray().put(new JSONObject().put("action","MORE").put("scope","source").put("target","V2EX")));
        for(int turn=0;turn<100;turn++)for(PlatformPreference preference:Arrays.asList(null,more)){
            List<FeedStore.Row> first=FeedSourceBalance.arrange(rows,preference,turn).subList(0,5);
            assertTrue(count(first,Source.V2EX)<=2);
            assertTrue(first.stream().map(r->r.candidate.source).distinct().count()>=4);
        }
    }
    @Test public void moreAndLessChangeSharesWithoutCandidateCountOrPermanentStarvation()throws Exception{
        PlatformPreference preference=new PlatformPreference(new JSONArray()
            .put(new JSONObject().put("action","LESS").put("scope","source").put("target","WEIBO"))
            .put(new JSONObject().put("action","MORE").put("scope","source").put("target","ZHIHU")));
        Map<Source,Long> totals=new EnumMap<>(Source.class);List<FeedStore.Row> rows=unequalPool();
        for(int turn=0;turn<200;turn++)for(FeedStore.Row row:FeedBatch.first(FeedSourceBalance.arrange(rows,preference,turn)))totals.merge(row.candidate.source,1L,Long::sum);
        assertTrue(totals.get(Source.WEIBO)>65&&totals.get(Source.WEIBO)<85);
        assertTrue(totals.get(Source.ZHIHU)>515&&totals.get(Source.ZHIHU)<535);
        assertTrue(totals.get(Source.BAIDU)>290&&totals.get(Source.BAIDU)<310);
        assertEquals(3000L,totals.values().stream().mapToLong(Long::longValue).sum());
    }
    @Test public void extraPoolDepthCannotChangeFirstBatchAndWarmCacheCannotIncreaseShare(){
        List<FeedStore.Row> rows=unequalPool();List<FeedStore.Row> shallow=new ArrayList<>();for(FeedStore.Row row:rows)if(row.candidate.sourceRank<=10)shallow.add(row);
        for(int turn=0;turn<20;turn++){
            List<FeedStore.Row> deep=FeedBatch.first(FeedSourceBalance.arrange(rows,null,turn)),shortPool=FeedBatch.first(FeedSourceBalance.arrange(shallow,null,turn));
            for(Source source:Source.values())assertEquals(count(shortPool,source),count(deep,source));
            List<FeedStore.Row> promoted=FeedReserve.promotePrepared(shallow,rows.subList(0,60));
            List<FeedStore.Row> actual=FeedBatch.first(FeedPresentationOrder.arrange(promoted,Collections.emptyMap(),1000,null,turn));
            for(Source source:Source.values())assertEquals(count(shortPool,source),count(actual,source));
        }
    }
    @Test public void shortagesRedistributeVacanciesAndNeverDuplicateOrLoseCandidates(){
        List<FeedStore.Row> rows=new ArrayList<>();rows.add(row(Source.BAIDU,0,1));for(int n=0;n<25;n++)rows.add(row(Source.WEIBO,n,1));for(int n=0;n<25;n++)rows.add(row(Source.ZHIHU,n,1));
        List<FeedStore.Row> arranged=FeedSourceBalance.arrange(rows);List<FeedStore.Row> batch=FeedBatch.first(arranged);
        assertEquals(1,count(batch,Source.BAIDU));assertEquals(14,count(batch,Source.WEIBO)+count(batch,Source.ZHIHU));assertTrue(Math.abs(count(batch,Source.WEIBO)-count(batch,Source.ZHIHU))<=1);assertEquals(new HashSet<>(rows),new HashSet<>(arranged));assertEquals(rows.size(),arranged.size());
    }
    @Test public void contentMatchesCannotBypassExplicitPlatformDislike()throws Exception{
        List<FeedStore.Row> rows=pool();List<FeedStore.Row> ranked=new ArrayList<>();for(FeedStore.Row row:rows)ranked.add(row.withInterestBand(row.candidate.source==Source.BAIDU?0:1));
        PlatformPreference preference=new PlatformPreference(new JSONArray().put(new JSONObject().put("action","LESS").put("scope","source").put("target","BAIDU")));
        List<FeedStore.Row> batch=FeedBatch.first(FeedSourceBalance.arrange(ranked,preference));assertTrue(count(batch,Source.BAIDU)<=2);
        FeedStore.Row neutral=row(Source.ZHIHU,501,1),positive=row(Source.ZHIHU,502,0);
        assertEquals(List.of(positive,neutral),FeedSourceBalance.arrange(List.of(neutral,positive)));
    }
}
