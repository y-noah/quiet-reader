package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.json.*;

public class HotPoolSearchTest {
    private FeedStore.Row row(int id,String title,String snippet){
        HotCandidate c=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+id,title,snippet,"",false,id+1,null,1000);
        return new FeedStore.Row(c.id,c,"");
    }
    @Test public void searchesBeyondTheVisibleHomeAndReserve(){
        List<FeedStore.Row> pool=new ArrayList<>();for(int n=0;n<130;n++)pool.add(row(n,n==129?"寒武纪发布重要公告":"今日新闻第"+n+"条",""));
        assertEquals(20,FeedBatch.first(pool).size());
        HotPoolSearch.Result result=HotPoolSearch.search(pool,"寒武纪");assertEquals(130,result.poolSize);assertEquals(List.of(pool.get(129)),result.rows);assertEquals(130,pool.size());
    }
    @Test public void blankAndUnrelatedQueriesNeverProduceFallbackNews(){
        List<FeedStore.Row> pool=List.of(row(1,"华为芯片发布",""));
        assertTrue(HotPoolSearch.search(pool,"  ").rows.isEmpty());assertEquals(1,HotPoolSearch.search(pool,"").poolSize);
        assertTrue(HotPoolSearch.search(pool,"巴黎奥运会").rows.isEmpty());assertTrue(HotPoolSearch.search(pool,"！").rows.isEmpty());
    }
    @Test public void titleExactRanksAboveSnippetAndTypo(){
        FeedStore.Row typo=row(1,"寒武记发布重要公告",""),summary=row(2,"半导体公司公告","寒武纪发布重要公告"),exact=row(3,"寒武纪发布重要公告","");
        assertEquals(List.of(exact,summary,typo),HotPoolSearch.search(List.of(typo,summary,exact),"寒武纪发布重要公告").rows);
    }
    @Test public void recognizesSeparatedAndReorderedKeywords(){
        FeedStore.Row hit=row(1,"华为公布最新芯片进展","");
        assertEquals(List.of(hit),HotPoolSearch.search(List.of(hit),"华为芯片").rows);
        assertEquals(List.of(hit),HotPoolSearch.search(List.of(hit),"芯片 华为").rows);
        assertTrue(HotPoolSearch.search(List.of(hit),"华为 苹果").rows.isEmpty());
    }
    @Test public void supportsSingleTypoButNotArbitraryPartialCharacterOverlap(){
        FeedStore.Row hit=row(1,"寒武纪发布公告","");
        assertEquals(List.of(hit),HotPoolSearch.search(List.of(hit),"寒武记发布").rows);
        assertTrue(HotPoolSearch.search(List.of(hit),"寒山公告").rows.isEmpty());
    }
    @Test public void shortNamesAndNumbersDoNotGetTypoExpansion(){
        List<FeedStore.Row> pool=List.of(row(1,"小米发布新手机16", ""));
        assertTrue(HotPoolSearch.search(pool,"小明").rows.isEmpty());assertTrue(HotPoolSearch.search(pool,"小米手机17").rows.isEmpty());
        assertEquals(1,HotPoolSearch.search(pool,"小米").rows.size());
    }
    @Test public void widthCaseAndPunctuationAreNormalized(){
        FeedStore.Row hit=row(1,"OpenAI 发布 GPT-5 更新","");
        assertEquals(List.of(hit),HotPoolSearch.search(List.of(hit),"ｇｐｔ５").rows);
    }
    @Test public void alternateSourceMatchBecomesTheReadableRepresentative(){
        FeedStore.Row first=row(1,"科技行业消息",""),alternate=row(2,"华为芯片最新进展","");
        FeedStore.Row group=new FeedStore.Row(first.eventId,first.candidate,"",List.of(first.candidate,alternate.candidate));
        HotPoolSearch.Result result=HotPoolSearch.search(List.of(group),"华为");
        assertEquals(2,result.poolSize);assertEquals(1,result.rows.size());assertSame(alternate.candidate,result.rows.get(0).candidate);assertEquals(first.eventId,result.rows.get(0).eventId);assertEquals(2,result.rows.get(0).entries.size());assertSame(first.candidate,group.candidate);
    }
    @Test public void allMatchingResultsAreReturnedAndTiesStayStable(){
        List<FeedStore.Row> pool=new ArrayList<>();for(int n=0;n<140;n++)pool.add(row(n,"芯片行业消息第"+n+"条",""));
        HotPoolSearch.Result result=HotPoolSearch.search(pool,"芯片");assertEquals(pool,result.rows);
        try{result.rows.clear();fail("immutable result");}catch(UnsupportedOperationException expected){}
    }
    @Test public void emptyPoolAndSupplementaryCharactersAreSafe(){
        assertEquals(0,HotPoolSearch.search(List.of(),"测试").poolSize);
        FeedStore.Row hit=row(1,"𠮷野家发布新品","");assertEquals(List.of(hit),HotPoolSearch.search(List.of(hit),"𠮷野家").rows);
    }
    @Test public void explicitLookupIgnoresEnoughButHonorsBlocks()throws Exception{
        HotCandidate enough=row(1,"芯片公司新公告","").candidate,blocked=row(2,"芯片行业新消息","").candidate;
        JSONArray feedback=new JSONArray().put(new JSONObject().put("scope","event").put("action","ENOUGH").put("target",enough.id))
            .put(new JSONObject().put("scope","event").put("action","BLOCK").put("target",blocked.id));
        HotPoolSearch.Result result=HotPoolSearch.search(HotPoolSearch.pool(List.of(enough,blocked),feedback),"芯片");
        assertEquals(1,result.rows.size());assertSame(enough,result.rows.get(0).candidate);assertEquals(2,feedback.length());
    }
}
