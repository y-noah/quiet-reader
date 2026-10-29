package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShortHeadlineGroupingTest {
    private static final long NOW=1792983557214L;
    private HotCandidate item(Models.Source source,String path,String title,String snippet,Long published,long observed,boolean video){return new HotCandidate(source,"https://example.org/"+path,title,snippet,"raw-heat-"+path,video,1,published,observed);}
    private HotCandidate item(Models.Source source,String path,String title){return item(source,path,title,"",null,NOW,false);}
    private List<FeedStore.Row> group(HotCandidate...items){return CandidateOrder.arrange(Arrays.asList(items),new JSONArray(),false);}
    private JSONArray feedback(String action,FeedStore.Row row,boolean versioned)throws Exception{return new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",EventTargets.capture(row,versioned)));}
    @Test public void actualExactShortCrossSourceTitlesRetainBothOriginalEntrypoints()throws Exception{
        JSONObject corpus;try(java.io.InputStream in=getClass().getClassLoader().getResourceAsStream("fixtures/observed-event-fresh-window-20261026.json")){corpus=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        for(String title:List.of("香港将上线网约车","杨幂给林俊杰伴舞","住房公积金更好用了")){
            List<HotCandidate> candidates=new ArrayList<>();JSONArray records=corpus.getJSONArray("candidates");for(int n=0;n<records.length();n++)if(title.equals(records.getJSONObject(n).getString("title")))candidates.add(HotCandidate.fromJson(records.getJSONObject(n)));
            assertEquals(title,2,candidates.size());List<FeedStore.Row> rows=CandidateOrder.arrange(candidates,new JSONArray(),false);assertEquals(title,1,rows.size());
            assertEquals(title,Set.copyOf(candidates.stream().map(c->c.id).toList()),Set.copyOf(rows.get(0).entries.stream().map(c->c.id).toList()));
            assertEquals(candidates.get(0).id,rows.get(0).candidate.id);assertEquals(candidates.get(0).rawHeat,rows.get(0).candidate.rawHeat);
        }
    }
    @Test public void genericNumericShortAndDistinctWordingStaySeparate(){
        for(String title:List.of("黄金","今天有什么好消息","这个消息值得关注","好消息终于传来了","股价今日上涨百分五","香港将上线网约车吗","香港将上线网约车1"))assertEquals(title,2,group(item(Models.Source.WEIBO,"a",title),item(Models.Source.BAIDU,"b",title)).size());
        assertEquals(2,group(item(Models.Source.WEIBO,"a","香港将上线网约车"),item(Models.Source.BAIDU,"b","香港已上线网约车")).size());
    }
    @Test public void completePublicWindowConservesMembersWithThreeShortAndOneAppointmentGroup()throws Exception{
        JSONObject corpus;try(java.io.InputStream in=getClass().getClassLoader().getResourceAsStream("fixtures/observed-event-fresh-window-20261026.json")){corpus=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        List<HotCandidate> candidates=new ArrayList<>();JSONArray pool=corpus.getJSONArray("candidates");for(int n=0;n<pool.length();n++)candidates.add(HotCandidate.fromJson(pool.getJSONObject(n)));
        List<FeedStore.Row> rows=CandidateOrder.forModels(candidates,new JSONArray());Set<String> members=new HashSet<>();int groups=0,shortGroups=0,appointments=0;
        Set<String> shortTitles=Set.of("香港将上线网约车","杨幂给林俊杰伴舞","住房公积金更好用了");
        for(FeedStore.Row row:rows){for(HotCandidate c:row.entries)assertTrue("Every identity occurs exactly once",members.add(c.id));if(row.entries.size()>1){groups++;assertEquals(2,row.entries.size());if(shortTitles.contains(row.candidate.title))shortGroups++;
            if(row.entries.stream().anyMatch(c->c.title.contains("总导演"))){appointments++;assertEquals(Set.of("2027 羊年央视春晚总导演公布，于蕾连续五年扛起重任","于蕾出任2027羊年央视春晚总导演，连续第5年执导春晚"),Set.copyOf(row.entries.stream().map(c->c.title).toList()));}
        }}
        assertEquals(270,candidates.size());assertEquals(269,members.size());assertEquals(260,rows.size());assertEquals(9,groups);assertEquals(3,shortGroups);assertEquals(1,appointments);
    }
    @Test public void samePlatformAmbiguityExplicitThreadsAndSubstantiveContextStaySeparate(){
        String title="香港将上线网约车";HotCandidate a=item(Models.Source.WEIBO,"a",title),b=item(Models.Source.BAIDU,"b",title);
        assertEquals(2,group(a,item(Models.Source.WEIBO,"c",title)).size());
        assertEquals(3,group(a,b,item(Models.Source.WEIBO,"c",title)).size());
        assertEquals(2,group(a,item(Models.Source.V2EX,"thread",title)).size());
        for(String text:List.of("另一个城市的同名政策。","这是不同时间发生的新闻，不确认同一事件。"))assertEquals(2,group(a,item(Models.Source.BAIDU,"b",title,text,null,NOW,false)).size());
        assertEquals(1,group(a,item(Models.Source.BAIDU,"b",title,title,null,NOW,false)).size());
    }
    @Test public void datesVideoAndUnknownObservationCannotCreateShortIdentity(){
        String title="香港将上线网约车";HotCandidate a=item(Models.Source.WEIBO,"a",title);
        for(HotCandidate b:List.of(item(Models.Source.BAIDU,"b",title,"",null,NOW+86400000L,false),item(Models.Source.BAIDU,"b",title,"",NOW-86400000L,NOW,false),item(Models.Source.BAIDU,"b",title,"",NOW+3600000L,NOW,false),item(Models.Source.BAIDU,"b",title,"",null,0,false),item(Models.Source.BAIDU,"b",title,"",null,NOW,true)))assertEquals(2,EventGroups.group(List.of(new FeedStore.Row(a.id,a,"one"),new FeedStore.Row(b.id,b,"two")),new JSONArray()).size());
    }
    @Test public void explicitSplitBlockEnoughAndMemberMetadataChangesPreserveScope()throws Exception{
        HotCandidate a=item(Models.Source.WEIBO,"a","香港将上线网约车"),b=item(Models.Source.BAIDU,"b",a.title);FeedStore.Row row=group(a,b).get(0);assertEquals(2,row.entries.size());
        assertEquals(2,CandidateOrder.arrange(List.of(a,b),feedback("SEPARATE",row,false),false).size());
        assertTrue(CandidateOrder.arrange(List.of(a,b),feedback("BLOCK",row,false),false).isEmpty());
        assertTrue(CandidateOrder.arrange(List.of(a,b),feedback("ENOUGH",row,true),false).isEmpty());
        HotCandidate changed=item(b.source,"b","香港将上线网约车新进展");assertTrue(CandidateOrder.arrange(List.of(changed),feedback("BLOCK",row,false),false).isEmpty());
        assertEquals(1,CandidateOrder.arrange(List.of(changed),feedback("ENOUGH",row,true),false).size());assertEquals(1,group(a,b).size());
    }
}
