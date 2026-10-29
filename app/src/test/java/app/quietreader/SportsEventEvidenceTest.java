package app.quietreader;

import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SportsEventEvidenceTest {
    private static final long NOW=1_792_983_550_000L;
    private HotCandidate candidate(String id,String title,String excerpt,Long published,long observed){return new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+id,title,excerpt,"",false,1,published,observed);}
    private HotCandidate report(){return candidate("first","TES击败G2","S16瑞士轮第三轮，TES击败G2，晋级下一轮。",NOW-1000,NOW);}
    private HotCandidate score(){return candidate("score","S16 瑞士轮 G2 0:1 TES，如何评价这场比赛？","",null,NOW);}
    private List<FeedStore.Row> rows(HotCandidate... candidates){return CandidateOrder.arrange(Arrays.asList(candidates),new JSONArray(),false);}
    @Test public void observedColloquialHeadlinesUseExplicitRoundAndScoreWithoutModelOrInventedNumbers()throws Exception{
        JSONObject fixture;try(InputStream in=getClass().getResourceAsStream("/fixtures/observed-event-fresh-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
        for(int index:new int[]{3,4}){
            JSONObject pair=fixture.getJSONArray("pairs").getJSONObject(index);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));
            List<FeedStore.Row> grouped=rows(a,b);assertEquals(a.title,1,grouped.size());assertEquals(2,grouped.get(0).entries.size());
            assertEquals(new HashSet<>(Arrays.asList(a.id,b.id)),new HashSet<>(Arrays.asList(grouped.get(0).entries.get(0).id,grouped.get(0).entries.get(1).id)));
            assertEquals(a.title,a.json().getString("title"));assertEquals(a.snippet,a.json().getString("snippet"));assertEquals(a.originalUrl,grouped.get(0).entries.stream().filter(c->c.id.equals(a.id)).findFirst().get().originalUrl);
        }
    }
    @Test public void complementaryEvidenceHasStableIdentityAndAllEntries(){
        HotCandidate a=report(),b=score();FeedStore.Row first=rows(a,b).get(0);
        assertEquals(1,rows(a,b).size());assertEquals(2,first.entries.size());assertEquals(first.eventId,rows(b,a).get(0).eventId);
        assertTrue(EventGroups.matches(first,a.id));assertTrue(EventGroups.matches(first,b.id));assertEquals("",a.rawHeat);
    }
    @Test public void missingRoundScoreSeasonPublicationOrUnsupportedStageCannotCreateIdentity(){
        HotCandidate a=report(),b=score();
        assertEquals(2,rows(candidate("no-round",a.title,a.snippet.replace("第三轮",""),a.publishedAt,NOW),b).size());
        assertEquals(2,rows(a,candidate("no-score","S16瑞士轮TES战胜G2","",null,NOW)).size());
        assertEquals(2,rows(candidate("no-season",a.title,a.snippet.replace("S16",""),a.publishedAt,NOW),b).size());
        assertEquals(2,rows(candidate("no-publication",a.title,a.snippet,null,NOW),b).size());
        assertEquals(2,rows(candidate("other-stage",a.title,a.snippet.replace("瑞士轮","淘汰赛"),a.publishedAt,NOW),b).size());
    }
    @Test public void incompatibleResultsAndRoundsCannotBeBridgedByMissingDetails(){
        HotCandidate a=report(),b=score();
        assertEquals(2,rows(a,candidate("opposite","S16 瑞士轮 TES 0-1 G2","",null,NOW)).size());
        assertEquals(3,rows(a,b,candidate("score-conflict","S16 瑞士轮 G2 0-2 TES","",null,NOW)).size());
        assertEquals(3,rows(a,b,candidate("round-conflict","TES战胜G2","S16瑞士轮第四轮，TES击败G2。",NOW-1000,NOW)).size());
        assertEquals(2,rows(a,candidate("season-conflict",b.title.replace("S16","S17"),"",null,NOW)).size());
        assertEquals(2,rows(a,candidate("team-conflict",b.title.replace("G2","HLE"),"",null,NOW)).size());
    }
    @Test public void interviewsPredictionsSubgamesNegationsAndCriminalArrestsStaySeparate(){
        HotCandidate a=report(),b=score();
        for(String prefix:new String[]{"赛后回应：","采访：","预测：","若","网传","复盘：","去年","女子","第二局"})assertEquals(prefix,2,rows(a,candidate("excluded-"+prefix,prefix+b.title,"",null,NOW)).size());
        assertEquals(2,rows(candidate("no-result","TES与G2即将比赛",a.snippet,a.publishedAt,NOW),b).size());
        assertEquals(2,rows(candidate("arrest","TES逮捕G2","S16瑞士轮第三轮，警方正在调查。",a.publishedAt,NOW),b).size());
        assertEquals(2,rows(candidate("criminal","TES逮捕G2涉嫌假赛",a.snippet,a.publishedAt,NOW),b).size());
    }
    @Test public void contradictoryOrMultipleSourceClaimsNeverOverrideTitle(){
        HotCandidate a=report(),b=score();
        for(String excerpt:new String[]{"S16瑞士轮第三轮，G2击败TES。","S16瑞士轮第三轮，TES击败G2，随后HLE击败IG。","S16瑞士轮第三轮，S17瑞士轮第三轮，TES击败G2。","S16瑞士轮第三轮，瑞士轮第四轮，TES击败G2。","S16瑞士轮第三轮，网传TES击败G2，其实并未发生。"})assertEquals(excerpt,2,rows(candidate("bad-summary",a.title,excerpt,a.publishedAt,NOW),b).size());
        for(String score:new String[]{"0-0","0-6"})assertEquals(2,rows(a,candidate("bad-score","S16 瑞士轮 G2 "+score+" TES","",null,NOW)).size());
    }
    @Test public void publicationAndCoObservationGuardsDoNotInventEventDates(){
        HotCandidate a=report(),b=score();
        assertEquals(2,rows(candidate("old",a.title,a.snippet,NOW-86_400_000,NOW),b).size());
        assertEquals(2,rows(candidate("future",a.title,a.snippet,NOW+600_001,NOW),b).size());
        assertEquals(2,rows(a,candidate("later",b.title,"",null,NOW+86_400_000)).size());
        assertEquals(2,rows(candidate("unknown-time",a.title,a.snippet,a.publishedAt,0),b).size());
    }
    @Test public void explicitThreadsAndCapturedFeedbackRemainAuthoritative()throws Exception{
        HotCandidate a=report(),b=score();
        HotCandidate thread=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/1234567",a.title,a.snippet,"",false,1,a.publishedAt,NOW);
        assertEquals(2,rows(thread,b).size());
        FeedStore.Row merged=rows(a,b).get(0);
        for(String action:Arrays.asList("SEPARATE","BLOCK","ENOUGH")){
            JSONArray feedback=new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",EventTargets.capture(merged,action.equals("ENOUGH"))));
            assertEquals(action,action.equals("SEPARATE")?2:0,CandidateOrder.arrange(Arrays.asList(a,b),feedback,false).size());
        }
        assertEquals(1,rows(a,b).size());
    }
}
