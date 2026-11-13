package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class WindowDistributionTest {
    private FeedStore.Row row(Source source,int n){HotCandidate c=new HotCandidate(source,"https://example.com/"+source+"/"+n,"平台内容 "+source+" "+n,"","",false,n+1,null,1);return new FeedStore.Row(c.id,c,"",List.of(c),1);}
    private List<FeedStore.Row> uneven(){List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<45;n++)rows.add(row(Source.V2EX,n));for(Source s:List.of(Source.BBC,Source.THEPAPER,Source.ITHOME))for(int n=0;n<10;n++)rows.add(row(s,n));return rows;}
    private long count(List<FeedStore.Row> rows,Source source){return rows.stream().filter(r->r.candidate.source==source).count();}
    @Test public void reproducesOldAllV2exFifthPageAndSpreadsActualMembershipAcrossAllFive(){
        List<FeedStore.Row> pool=uneven();assertEquals(15,count(FeedSourceBalance.arrange(pool).subList(60,75),Source.V2EX));
        List<FeedStore.Row> displayed=FeedSourceBalance.spread(pool);
        for(int n=0;n<5;n++){List<FeedStore.Row> page=displayed.subList(15*n,15*n+15);assertEquals(9,count(page,Source.V2EX));assertEquals(4,page.stream().map(r->r.candidate.source).distinct().count());}
        assertEquals(new HashSet<>(pool),new HashSet<>(displayed));
        for(Source source:Source.values())assertEquals(pool.stream().filter(r->r.candidate.source==source).toList(),displayed.stream().filter(r->r.candidate.source==source).toList());
    }
    @Test public void reprojectingRemainingPoolOnEachRefreshCannotMoveMinoritySourcesToTheFront(){
        List<FeedStore.Row> remaining=uneven();Set<String> seen=new HashSet<>();
        for(int round=0;round<5;round++){List<FeedStore.Row> ordered=FeedPresentationOrder.arrange(remaining,Map.of(),1000);List<FeedStore.Row> page=new ArrayList<>(ordered.subList(0,15));assertEquals(9,count(page,Source.V2EX));assertEquals(4,page.stream().map(r->r.candidate.source).distinct().count());for(FeedStore.Row row:page)assertTrue(seen.add(row.eventId));remaining.removeAll(page);}
        assertEquals(75,seen.size());assertTrue(remaining.isEmpty());
    }
    @Test public void dislikedSourceBudgetComesFromActualNormalSelectionNotRawFirstSeventyFive()throws Exception{
        List<FeedStore.Row> raw=new ArrayList<>();for(int n=0;n<200;n++)raw.add(row(Source.V2EX,n));for(Source source:List.of(Source.BBC,Source.THEPAPER,Source.ITHOME,Source.ZHIHU))for(int n=0;n<25;n++)raw.add(row(source,n));
        PlatformPreference less=new PlatformPreference(new JSONArray().put(new JSONObject().put("action","LESS").put("scope","source").put("target","V2EX")));
        assertEquals(15,count(FeedSourceBalance.arrange(raw,less).subList(0,75),Source.V2EX));
        List<FeedStore.Row> eligible=InterestPool.eligible(raw,less);assertEquals(3,count(eligible,Source.V2EX));
        List<FeedStore.Row> selected=RecommendationWindow.select(FeedSourceBalance.arrange(eligible,less),new HashMap<>(),Set.of());assertEquals(75,selected.size());assertEquals(3,count(selected,Source.V2EX));
        List<FeedStore.Row> visible=FeedSourceBalance.spread(selected);for(int start=0;start<75;start+=15)assertTrue(count(visible.subList(start,start+15),Source.V2EX)<=1);
    }
    @Test public void aSingleAvailableSourceIsNeverDuplicatedOrPretendedToBeDiverse(){
        List<FeedStore.Row> one=new ArrayList<>();for(int n=0;n<18;n++)one.add(row(Source.V2EX,n));assertEquals(one,FeedSourceBalance.spread(one));
    }
    @Test public void onlyManualFeedbackForThisSourceChangesItsBudgetRevision()throws Exception{
        JSONArray feedback=new JSONArray();long original=InterestPool.sourceRevision(feedback,Source.V2EX);
        for(String action:List.of("READ","QUICK_EXIT"))feedback.put(new JSONObject().put("action",action).put("scope","sample").put("source","V2EX").put("target","body"));
        feedback.put(new JSONObject().put("action","LESS").put("scope","source").put("target","BBC"));
        assertEquals(original,InterestPool.sourceRevision(feedback,Source.V2EX));
        feedback.put(new JSONObject().put("id","manual").put("action","LESS").put("scope","source").put("target","V2EX"));
        assertNotEquals(original,InterestPool.sourceRevision(feedback,Source.V2EX));
        feedback.remove(feedback.length()-1);assertEquals(original,InterestPool.sourceRevision(feedback,Source.V2EX));
    }
    @Test public void randomizedUnevenPoolsPreserveMembershipOrderAndDoNotCreateNegativeHeads(){
        for(int seed=0;seed<300;seed++){
            Random random=new Random(seed);List<FeedStore.Row> input=new ArrayList<>();
            int size=1+random.nextInt(300);for(int n=0;n<size;n++){
                FeedStore.Row r=row(List.of(Source.V2EX,Source.BBC,Source.THEPAPER,Source.ITHOME,Source.ZHIHU).get(random.nextInt(5)),seed*300+n);
                input.add(new FeedStore.Row(r.eventId,r.candidate,"",r.entries,random.nextInt(3)));
            }
            Collections.shuffle(input,random);List<FeedStore.Row> output=FeedSourceBalance.spread(input);
            assertEquals("seed="+seed,input.size(),output.size());assertEquals(new HashSet<>(input),new HashSet<>(output));
            boolean negative=false;for(FeedStore.Row r:output){if(r.interestBand==2)negative=true;else assertFalse("negative before usable, seed="+seed,negative);}
            for(Source source:Source.values())for(boolean band:List.of(false,true))assertEquals(input.stream().filter(r->r.candidate.source==source&&(r.interestBand==2)==band).toList(),output.stream().filter(r->r.candidate.source==source&&(r.interestBand==2)==band).toList());
        }
    }
}
