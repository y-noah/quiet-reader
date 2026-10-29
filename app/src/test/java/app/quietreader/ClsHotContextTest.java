package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.json.*;
import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class ClsHotContextTest {
    private JSONObject row()throws Exception{return new JSONObject().put("id",99008001).put("title","城市树荫与体感温度").put("readNum",123);}
    private Item parse(JSONObject row)throws Exception{return SourceParser.list(Source.CLS,new JSONObject().put("errno",0).put("data",new JSONArray().put(row)).toString()).get(0);}
    @Test public void plainBriefSurvivesStorageAndReachesModel()throws Exception{
        String plain="① a < b；<video> 标签 &amp; 👩‍💻";
        Item item=parse(row().put("brief",plain));
        assertEquals(plain,item.snippet);assertEquals("123 阅读",item.detail);assertNull(item.publishedAt);assertFalse(item.video);
        Item restored=Item.from(item.json());assertEquals(plain,restored.snippet);
        HotCandidate candidate=new HotCandidate(restored.source,restored.url,restored.title,restored.snippet,restored.detail,false,1,null,100);
        assertEquals(item.title+"\n"+plain,candidate.modelText());assertEquals(plain,candidate.readerItem().snippet);
        HotCandidate titleOnly=new HotCandidate(restored.source,restored.url,restored.title,"",restored.detail,false,1,null,100);
        assertNotEquals(titleOnly.textHash,candidate.textHash);
        assertFalse(SourceParser.article(Source.CLS,"<h1>页面标题</h1>",item.url).hasContent());
    }
    @Test public void missingOrMalformedBriefStaysEmpty()throws Exception{
        assertEquals("",parse(row()).snippet);
        for(Object value:new Object[]{JSONObject.NULL,12,true,new JSONObject().put("text","非字符串"),new JSONArray().put("非字符串")})assertEquals("",parse(row().put("brief",value)).snippet);
    }
    @Test public void briefIsBoundedByCodePointsAndCleansControls()throws Exception{
        String raw=" \t"+"🙂".repeat(599)+"中尾部\u0000\u0001";
        String snippet=parse(row().put("brief",raw)).snippet;
        assertEquals(600,snippet.codePointCount(0,snippet.length()));assertTrue(snippet.endsWith("中"));assertFalse(snippet.contains("\u0000"));
        assertEquals("①说明 ②内容",parse(row().put("brief","①说明\n\t②内容")).snippet);
    }
    @Test public void officialSecondsSurviveStorageAndDriveLatestOrder()throws Exception{
        Item older=parse(row().put("ctime",1792806728));
        Item newer=parse(row().put("id",99008002).put("ctime",1792833086L));
        assertEquals(Long.valueOf(1792806728000L),older.publishedAt);
        Item restored=Item.from(older.json());assertEquals(older.publishedAt,restored.publishedAt);
        HotCandidate a=new HotCandidate(restored.source,restored.url,restored.title,restored.snippet,restored.detail,false,1,restored.publishedAt,9999999999999L);
        HotCandidate b=new HotCandidate(newer.source,newer.url,newer.title,newer.snippet,newer.detail,false,2,newer.publishedAt,1);
        Item unknown=parse(row().put("id",99008003));
        HotCandidate c=new HotCandidate(unknown.source,unknown.url,unknown.title,"","",false,1,null,9999999999999L);
        java.util.List<app.quietreader.discovery.FeedStore.Row> latest=app.quietreader.discovery.CandidateOrder.arrange(java.util.Arrays.asList(a,c,b),new JSONArray(),true);
        assertEquals(b.id,latest.get(0).candidate.id);assertEquals(a.id,latest.get(1).candidate.id);assertEquals(c.id,latest.get(2).candidate.id);
        assertEquals(a.publishedAt,a.readerItem().publishedAt);
        assertEquals(a.id,app.quietreader.discovery.CandidateOrder.arrange(java.util.Arrays.asList(a,b),new JSONArray(),false).get(0).candidate.id);
    }
    @Test public void invalidDatesNeverBecomeFetchTimeOrWrongUnitDates()throws Exception{
        for(Object value:new Object[]{JSONObject.NULL,0,-1,1792806728.5,"1792806728",1792806728000L,Long.MAX_VALUE,new JSONObject(),new JSONArray(),true})assertNull(parse(row().put("ctime",value)).publishedAt);
        assertNull(parse(row()).publishedAt);
        assertEquals(Long.valueOf(253402300799000L),parse(row().put("ctime",253402300799L)).publishedAt);
        assertNull(parse(row().put("ctime",253402300800L)).publishedAt);
    }
}
