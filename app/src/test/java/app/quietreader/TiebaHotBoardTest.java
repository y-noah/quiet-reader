package app.quietreader;

import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class TiebaHotBoardTest {
    private JSONObject topic(long id,String title)throws Exception{return new JSONObject().put("topic_id",id).put("topic_name",title).put("topic_url","https://tieba.baidu.com/hottopic/browse/hottopic?topic_id="+id+"&amp;topic_name=test%26name").put("discuss_num",123).put("create_time",1792729334L).put("topic_desc","来源真实摘要 <video>");}
    private JSONObject response(JSONArray rows)throws Exception{return new JSONObject().put("errno",0).put("data",new JSONObject().put("bang_topic",new JSONObject().put("topic_list",rows)).put("sug_topic",new JSONObject().put("topic_list",new JSONArray().put(topic(999,"个人推荐")))));}
    @Test public void officialHotTopicsPreserveMetadataAndEncodedUrl()throws Exception{
        Item item=SourceParser.list(Source.TIEBA,response(new JSONArray().put(topic(12,"真实热议"))).toString()).get(0);
        assertEquals("真实热议",item.title);assertEquals("123 讨论",item.detail);assertEquals(Long.valueOf(1792729334000L),item.publishedAt);assertEquals("来源真实摘要 <video>",item.snippet);
        assertTrue(item.url.contains("&topic_name=test%26name"));assertFalse(item.url.contains("&amp;"));
        assertEquals(item.publishedAt,Item.from(item.json()).publishedAt);assertEquals(item.snippet,Item.from(item.json()).snippet);
    }
    @Test public void skipsDuplicatesAndForeignOrMismatchedTargets()throws Exception{
        JSONArray rows=new JSONArray().put(topic(12,"第一条")).put(topic(12,"重复"))
            .put(topic(13,"错误ID").put("topic_url","https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=999"))
            .put(topic(14,"外站").put("topic_url","https://evil.test/hottopic/browse/hottopic?topic_id=14"))
            .put(topic(15,"主页帖子").put("topic_url","https://tieba.baidu.com/p/15"));
        assertEquals(1,SourceParser.list(Source.TIEBA,response(rows).toString()).size());
    }
    @Test public void missingTimeIsNotReplacedWithFetchTime()throws Exception{
        JSONObject row=topic(1,"无时间");row.remove("create_time");row.remove("topic_desc");
        Item item=SourceParser.list(Source.TIEBA,response(new JSONArray().put(row)).toString()).get(0);assertNull(item.publishedAt);assertEquals("",item.snippet);
        Item legacy=Item.from(new Item(Source.ZHIHU,"旧条目","https://www.zhihu.com/question/1","").json());assertNull(legacy.publishedAt);assertEquals("",legacy.snippet);
    }
    @Test public void failureAndPersonalSuggestionsCannotBecomeHotSuccess()throws Exception{
        for(String raw:new String[]{response(new JSONArray()).toString(),response(new JSONArray().put(topic(1,"不能接受"))).put("errno",1).toString(),"{\"errno\":0,\"data\":{\"sug_topic\":{\"topic_list\":[]}}}"}){
            try{SourceParser.list(Source.TIEBA,raw);fail("Accepted invalid hot list");}catch(IllegalStateException|JSONException expected){}
        }
    }
}
