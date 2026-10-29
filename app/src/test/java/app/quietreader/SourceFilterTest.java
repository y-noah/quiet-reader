package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SourceFilterTest {
    @Test public void categoryKeepsTheAlreadyPreferredEligibleEntry()throws Exception{
        HotCandidate paper=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_101","相同中文科技话题","","",false,1,null,1,ContentLanguage.Body.UNKNOWN,"对齐Lab");
        HotCandidate v2ex=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/101",paper.title,"","",false,2,null,1,ContentLanguage.Body.UNKNOWN,"node:programmer");
        FeedStore.Row group=new FeedStore.Row("group",paper,"原顺序",List.of(paper,v2ex));
        PlatformPreference profile=new PlatformPreference(new org.json.JSONArray().put(new org.json.JSONObject().put("scope","source").put("action","MORE").put("target","V2EX")));
        List<FeedStore.Row> preferred=profile.apply(List.of(group));assertSame(v2ex,preferred.get(0).candidate);
        List<FeedStore.Row> filtered=SourceFilter.apply(preferred,"all|TECH");
        assertSame(v2ex,filtered.get(0).candidate);assertEquals(preferred.get(0).reason,filtered.get(0).reason);
        assertEquals(preferred.get(0).entries,filtered.get(0).entries);assertSame(v2ex,preferred.get(0).candidate);
        assertSame(paper,SourceFilter.apply(preferred,"THEPAPER|TECH").get(0).candidate);
    }
    @Test public void preferredEntryCannotBypassSourceOrCategoryAndDoesNotMutateBase(){
        HotCandidate culture=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_102","中文文化话题","","",false,1,null,1,ContentLanguage.Body.UNKNOWN,"有戏");
        HotCandidate tech=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/102",culture.title,"","",false,2,null,1,ContentLanguage.Body.UNKNOWN,"node:python");
        FeedStore.Row group=new FeedStore.Row("group",culture,"原顺序",List.of(tech,culture));
        assertSame(culture,SourceFilter.apply(List.of(group),"all|CULTURE").get(0).candidate);
        assertSame(tech,SourceFilter.apply(List.of(group),"all|TECH").get(0).candidate);
        assertTrue(SourceFilter.apply(List.of(group),"THEPAPER|TECH").isEmpty());
        assertSame(culture,SourceFilter.apply(List.of(group),"all").get(0).candidate);
        assertSame(culture,group.candidate);
    }
    private HotCandidate item(Source source,int id){return new HotCandidate(source,"https://example.org/"+id,"中文测试"+id,"","",false,id,null,1);}
    @Test public void filterKeepsOrderAndSelectsMatchingGroupedEntry(){
        HotCandidate a=item(Source.ZHIHU,1),b=item(Source.WEIBO,2),c=item(Source.WEIBO,3);
        FeedStore.Row grouped=new FeedStore.Row("event",a,"原顺序",Arrays.asList(a,b));
        List<FeedStore.Row> rows=Arrays.asList(grouped,new FeedStore.Row("later",c,"原顺序"));
        List<FeedStore.Row> selected=SourceFilter.apply(rows,"WEIBO");
        assertEquals(2,selected.size());assertSame(b,selected.get(0).candidate);assertEquals("event",selected.get(0).eventId);
        assertEquals(2,selected.get(0).entries.size());assertSame(c,selected.get(1).candidate);assertSame(a,grouped.candidate);
        assertEquals(rows,SourceFilter.apply(rows,"all"));
    }
    @Test public void unsupportedFilterFallsBackAndAbsentSourceIsEmpty(){
        FeedStore.Row row=new FeedStore.Row("event",item(Source.ZHIHU,1),"");
        assertEquals("all",SourceFilter.normalize("IFANR"));assertEquals("all",SourceFilter.normalize(null));
        assertEquals(1,SourceFilter.apply(Collections.singletonList(row),"bad").size());
        assertTrue(SourceFilter.apply(Collections.singletonList(row),"V2EX").isEmpty());
    }
}
