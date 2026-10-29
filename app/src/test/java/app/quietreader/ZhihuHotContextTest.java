package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.json.*;
import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class ZhihuHotContextTest {
    private JSONObject target()throws Exception{return new JSONObject().put("title_area",new JSONObject().put("text","为什么城市树荫会改变体感温度？")).put("link",new JSONObject().put("url","https://www.zhihu.com/question/99008001")).put("metrics_area",new JSONObject().put("text","123万热度"));}
    private Item parse(JSONObject target)throws Exception{return SourceParser.list(Source.ZHIHU,new JSONObject().put("data",new JSONArray().put(new JSONObject().put("target",target))).toString()).get(0);}
    @Test public void suppliedPlainContextReachesCandidateAndReaderMetadata()throws Exception{
        String plain="问题补充：a < b，<video> 标签为什么不同？&amp; 👩‍💻";
        Item item=parse(target().put("excerpt_area",new JSONObject().put("text",plain)));
        assertEquals(plain,item.snippet);assertEquals("123万热度",item.detail);assertNull(item.publishedAt);
        Item restored=Item.from(item.json());assertEquals(plain,restored.snippet);
        HotCandidate candidate=new HotCandidate(restored.source,restored.url,restored.title,restored.snippet,restored.detail,false,1,null,100);
        assertEquals(item.title+"\n"+plain,candidate.modelText());assertEquals(plain,candidate.readerItem().snippet);
        // Context remains metadata; fetching a hot list must not produce a body.
        assertFalse(SourceParser.article(Source.ZHIHU,"<h1>需要登录</h1>",item.url).hasContent());
    }
    @Test public void absentOrMalformedContextNeverBecomesInventedText()throws Exception{
        assertEquals("",parse(target()).snippet);
        for(Object value:new Object[]{JSONObject.NULL,12,new JSONObject().put("html","并非纯文本"),new JSONArray().put("并非纯文本")})assertEquals("",parse(target().put("excerpt_area",new JSONObject().put("text",value))).snippet);
        assertEquals("",parse(target().put("excerpt_area","不是区域对象")).snippet);
    }
    @Test public void sourceContextIsBoundedWithoutSplittingUnicode()throws Exception{
        String raw=" \t"+"🙂".repeat(599)+"中尾部\u0000\u0001";
        String snippet=parse(target().put("excerpt_area",new JSONObject().put("text",raw))).snippet;
        assertEquals(600,snippet.codePointCount(0,snippet.length()));assertTrue(snippet.endsWith("中"));assertFalse(snippet.contains("\u0000"));
        assertEquals("补充 说明",parse(target().put("excerpt_area",new JSONObject().put("text","补充\n\t说明"))).snippet);
    }
}
