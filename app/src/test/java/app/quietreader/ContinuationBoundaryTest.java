package app.quietreader;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Independent review regressions: duplicate pages, excluded media and malformed cursors. */
public class ContinuationBoundaryTest {
    private final Item item=new Item(Source.ZHIHU,"问题","https://www.zhihu.com/question/123","");
    private Document page(String data)throws Exception {
        return ZhihuAnswers.parse(item,new JSONObject("{\"data\":"+data+"}"));
    }
    @Test public void repeatingApiPageDoesNotInventProgress()throws Exception {
        Document first=page("[{\"id\":1,\"content\":\"<p>原回答</p>\"}]");
        Set<String> ids=AnswerStream.answerIds(first);
        AnswerStream.merge(first,page("[{\"id\":1,\"content\":\"<p>原回答增加正文</p>\"}]"));
        assertEquals(1,AnswerStream.answers(first));
        assertFalse(AnswerStream.hasNewAnswers(first,ids));
        assertTrue(ReaderHtml.render(first,Source.ZHIHU,19).contains("增加正文"));
    }
    @Test public void mediaReclassificationNeverRevivesOnLaterDuplicate()throws Exception {
        Document first=page("[{\"id\":1,\"content\":\"<p>旧预览</p>\"}]");
        AnswerStream.merge(first,page("[{\"id\":1,\"type\":\"video_answer\",\"content\":\"<p>视频回答</p>\"},{\"id\":2,\"content\":\"<p>文字回答</p>\"}]"));
        AnswerStream.merge(first,page("[{\"id\":1,\"content\":\"<p>旧预览又返回</p>\"}]"));
        assertEquals(new HashSet<>(Arrays.asList("2")),AnswerStream.answerIds(first));
        assertEquals(1,first.filteredVideos);
    }
    @Test public void emptyApiPageKeepsPriorAnswers()throws Exception {
        Document first=page("[{\"id\":1,\"content\":\"<p>已有内容</p>\"}]");
        AnswerStream.merge(first,page("[]"));
        assertEquals(1,AnswerStream.answers(first));
    }
    @Test public void malformedAndOffOriginCursorsAreRejected(){
        for(String bad:new String[]{"", "/api/v4/questions/123/answers?offset=10", "https://www.zhihu.com.evil.test/api/v4/questions/123/answers", "https://www.zhihu.com/api/v4/questions/123/answers/../456/answers", "https://www.zhihu.com/api/v4/questions/123/answers%2fextra"})
            assertEquals("",ZhihuAnswers.next(item,bad));
    }
    @Test public void retiredDoubanRejectsAllReaderEntries()throws Exception {
        Item topic=new Item(Source.DOUBAN,"话题","https://www.douban.com/gallery/topic/1/","");
        JSONObject post=new JSONObject().put("title","帖子").put("url","https://www.douban.com/group/topic/42/");
        JSONArray rows=new JSONArray().put(new JSONObject().put("target",post)).put(new JSONObject().put("target",post))
            .put(new JSONObject().put("is_ad",true).put("target",post))
            .put(new JSONObject().put("target",new JSONObject().put("title","外站").put("url","https://evil.test/group/topic/42/")));
        Document result=AdditionalSources.doubanTopic(topic,new JSONObject().put("items",rows).toString());
        assertTrue(result.related.isEmpty());assertTrue(result.sourceUnavailable);
    }
}
