package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class PlatformPreferenceTest {
    @Test public void repeatedQuickExitsOnlyCreateABoundedWeakPlatformNegative()throws Exception{
        JSONArray events=new JSONArray();for(int n=1;n<=2;n++){HotCandidate c=item(Source.ZHIHU,n);events.put(event("QUICK_EXIT",c).put("target","quick:"+c.id+":"+c.textHash));}
        assertEquals(0,new PlatformPreference(events).score(Source.ZHIHU),0);
        HotCandidate third=item(Source.ZHIHU,3);events.put(event("QUICK_EXIT",third).put("target","quick:"+third.id+":"+third.textHash));
        assertTrue(new PlatformPreference(events).score(Source.ZHIHU)<0);assertTrue(new PlatformPreference(events).score(Source.ZHIHU)>-0.2);
        events.put(explicit(Source.ZHIHU,true));assertEquals(1,new PlatformPreference(events).score(Source.ZHIHU),0);
    }
    private HotCandidate item(Source source,int n){return new HotCandidate(source,"https://"+(source==Source.V2EX?"www.v2ex.com/t/":"www.zhihu.com/question/")+n,"中文测试话题"+n,"","",false,n+1,null,1);}
    private JSONObject event(String action,HotCandidate c)throws Exception{return new JSONObject().put("action",action).put("scope","sample").put("target",action.equals("READ")?"read:"+c.id+":"+c.textHash:action.equals("SAVED")?"saved:"+c.id:CandidateOrder.versionTarget(c)).put("source",c.source.name()).put("sample",c.modelText());}
    private JSONObject explicit(Source source,boolean more)throws Exception{return new JSONObject().put("action",more?"MORE":"LESS").put("scope","source").put("target",source.name()).put("sample",source.label);}
    @Test public void engagementNeedsThreeDistinctArticlesAndNeverCountsActionsOrVersions()throws Exception{
        HotCandidate one=item(Source.ZHIHU,1),two=item(Source.ZHIHU,2),three=item(Source.ZHIHU,3);JSONArray events=new JSONArray().put(event("READ",one)).put(event("SAVED",one)).put(event("MORE",one)).put(event("READ",two));
        events.put(event("READ",one).put("target","read:"+one.id+":new-version"));
        assertTrue(new PlatformPreference(events).empty());events.put(event("READ",three));
        assertTrue(new PlatformPreference(events).score(Source.ZHIHU)>0);assertTrue(new PlatformPreference(events).score(Source.ZHIHU)<=0.35);
    }
    @Test public void explicitSourceChoiceWinsAndLatestChoiceCanBeUndoneByRemovingIt()throws Exception{
        JSONArray events=new JSONArray();for(int n=1;n<=20;n++)events.put(event("READ",item(Source.ZHIHU,n)));events.put(explicit(Source.ZHIHU,false));
        assertEquals(-1,new PlatformPreference(events).score(Source.ZHIHU),0);events.put(explicit(Source.ZHIHU,true));assertEquals(1,new PlatformPreference(events).score(Source.ZHIHU),0);
        events.remove(events.length()-1);assertEquals(-1,new PlatformPreference(events).score(Source.ZHIHU),0);
        assertTrue(new SemanticOrder.Profile(new JSONArray().put(explicit(Source.ZHIHU,true))).empty());
    }
    @Test public void legacyUnknownSourcesFetchesAndFollowingFreeTextDoNotInferPlatform()throws Exception{
        JSONArray events=new JSONArray();for(int n=1;n<=5;n++){events.put(event("READ",item(Source.ZHIHU,n)).put("source",""));events.put(event("FOLLOW",item(Source.V2EX,n)));events.put(event("PREFETCH",item(Source.V2EX,n)));}
        assertTrue(new PlatformPreference(events).empty());
    }
    @Test public void platformPriorIsBoundedAndDoesNotCrossContentNegativeCategory()throws Exception{
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<12;n++){HotCandidate c=item(n==10?Source.V2EX:Source.ZHIHU,n);rows.add(new FeedStore.Row(c.id,c,"原顺序"));}
        PlatformPreference preferences=new PlatformPreference(new JSONArray().put(explicit(Source.V2EX,true)));
        List<FeedStore.Row> ranked=preferences.apply(rows);int promoted=-1;for(int n=0;n<ranked.size();n++)if(ranked.get(n).candidate.source==Source.V2EX)promoted=n;
        assertTrue(promoted>=6&&promoted<10);assertEquals(rows.size(),ranked.size());
        Map<String,Integer> categories=new HashMap<>();categories.put(rows.get(10).eventId,2);ranked=preferences.apply(rows,categories);assertEquals(Source.V2EX,ranked.get(ranked.size()-1).candidate.source);
    }
    @Test public void sameEventChoosesPreferredActualEntryWithoutMultiplyingPreference()throws Exception{
        HotCandidate zhihu=item(Source.ZHIHU,1),v2ex=item(Source.V2EX,2);FeedStore.Row row=new FeedStore.Row("event",zhihu,"原顺序",List.of(zhihu,v2ex));
        PlatformPreference preferences=new PlatformPreference(new JSONArray().put(explicit(Source.V2EX,true)));
        List<FeedStore.Row> result=preferences.apply(List.of(row));assertEquals(1,result.size());assertEquals(v2ex.id,result.get(0).candidate.id);assertEquals(2,result.get(0).entries.size());assertEquals("event",result.get(0).eventId);
    }
    @Test public void latestOrderIgnoresPlatformAndModelInputDoesNotDoubleApplyIt()throws Exception{
        HotCandidate first=item(Source.ZHIHU,1),second=item(Source.V2EX,2);JSONArray preferences=new JSONArray().put(explicit(Source.V2EX,true));
        assertEquals(Source.V2EX,CandidateOrder.arrange(List.of(first,second),preferences,false).get(0).candidate.source);
        assertEquals(Source.ZHIHU,CandidateOrder.arrange(List.of(first,second),preferences,true).get(0).candidate.source);
        assertEquals(Source.ZHIHU,CandidateOrder.forModels(List.of(first,second),preferences).get(0).candidate.source);
    }
    @Test public void weakPositiveCannotOverrideExplicitTopicNegativeAfterFineRanking()throws Exception{
        HotCandidate topic=item(Source.V2EX,1);JSONArray events=new JSONArray().put(explicit(Source.V2EX,true)).put(event("LESS",topic));SemanticOrder.Profile profile=new SemanticOrder.Profile(events);
        float[] unit=new float[512];unit[0]=1;List<SemanticOrder.Scored> coarse=SemanticOrder.rank(List.of(new FeedStore.Row(topic.id,topic,""),new FeedStore.Row("other",item(Source.ZHIHU,2),"")),profile,Map.of(SemanticOrder.key(topic.modelText()),unit));
        assertEquals(topic.id,SemanticOrder.fuse(coarse,Collections.emptyMap(),profile.platforms).get(1).candidate.id);
    }
}
