package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecommendationTrialComparisonTest {
    private JSONObject frozen()throws Exception{
        List<FeedStore.Row> a=new ArrayList<>();for(int n=0;n<100;n++){HotCandidate c=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/"+(1000+n),"中文讨论第"+n+"项","摘要","",false,n+1,null,100);a.add(new FeedStore.Row("event-"+n,c,""));}
        List<FeedStore.Row> b=new ArrayList<>(a),c=new ArrayList<>(a);Collections.rotate(b,-20);Collections.rotate(c,-40);
        return RecommendationTrialStore.freezeComparison(new RecommendationComparison(a,b,c,"model","profile",1,2,3,4),200,new Random(7));
    }
    @Test public void distinctFrontsAreShuffledDeduplicatedAndDoNotExposeMode()throws Exception{
        JSONObject value=frozen();RecommendationTrialStore.validate(value);assertEquals(60,value.getJSONArray("items").length());assertEquals(100,value.getJSONArray("pool").length());assertFalse(value.getBoolean("holdout_qualified"));
        Set<String> ids=new HashSet<>();for(int n=0;n<60;n++){JSONObject item=value.getJSONArray("items").getJSONObject(n);assertEquals(2,item.length());ids.add(item.getString("event_key"));}
        assertEquals(60,ids.size());assertNotEquals("event-0",value.getJSONArray("items").getJSONObject(0).getString("event_key"));assertEquals(value.toString(),frozen().toString());
    }
    @Test public void rejectsMissingFrontLabelsAndMismatchedRankPool()throws Exception{
        JSONObject missing=frozen();missing.getJSONArray("items").remove(0);assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.validate(missing));
        JSONObject unequal=frozen();unequal.getJSONObject("rankings").getJSONArray("dual").put(99,"unknown-event");assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.validate(unequal));
    }
    @Test public void rejectsChangedDisplayContentAndFalseHoldoutClaim()throws Exception{
        JSONObject changed=frozen();changed.getJSONArray("items").getJSONObject(0).getJSONObject("candidate").put("title","替换成别的文字内容");assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.validate(changed));
        JSONObject holdout=frozen().put("holdout_qualified",true);assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.validate(holdout));
    }
    @Test public void summaryKeepsUnknownSeparateAndCountsEachModeByItsOwnFront()throws Exception{
        JSONObject value=frozen(),labels=new JSONObject();labels.put("event-0",new JSONObject().put("choice","want"));labels.put("event-1",new JSONObject().put("choice","skip"));labels.put("event-2",new JSONObject().put("choice","unsure"));value.put("labels",labels);
        String summary=RecommendationTrialStore.comparisonSummary(value);assertTrue(summary.contains("基础顺序：想看 1/20，不想看 1，不确定或未评价 18"));assertTrue(summary.contains("兴趣初筛：想看 0/20，不想看 0，不确定或未评价 20"));
        assertFalse(summary.contains("准确率"));
    }
    @Test public void completedSummaryRejectsPartialAndKeepsUncertainSeparate()throws Exception{
        JSONObject value=frozen().put("labels",new JSONObject());
        assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.completedSummary(value));
        JSONArray items=value.getJSONArray("items");JSONObject labels=value.getJSONObject("labels");
        for(int n=0;n<items.length();n++)labels.put(items.getJSONObject(n).getString("event_key"),new JSONObject().put("choice",n==0?"want":n==1?"skip":"unsure"));
        String summary=RecommendationTrialStore.completedSummary(value);assertTrue(summary.contains("60 项：想看 1，不想看 1，不确定 58"));assertTrue(summary.contains("这些选择不会改变阅读偏好"));assertTrue(summary.contains("基础顺序"));assertFalse(summary.contains("准确率"));
    }
    @Test public void completedSingleListSummaryDoesNotInventThreeOrderComparison()throws Exception{
        HotCandidate c=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/123","中文话题","","",false,1,null,100);
        JSONObject value=RecommendationTrialStore.freeze(List.of(new FeedStore.Row("event",c,"")),1,1,1,"all","cold",200).put("labels",new JSONObject().put("event",new JSONObject().put("choice","unsure")));
        String summary=RecommendationTrialStore.completedSummary(value);assertTrue(summary.contains("想看 0，不想看 0，不确定 1"));assertFalse(summary.contains("基础顺序"));
    }}
