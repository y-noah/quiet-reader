package app.quietreader;

import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReadingRepairTest {
    private final Item question=new Item(Source.ZHIHU,"问题","https://www.zhihu.com/question/123","");
    @Test public void paginationAcceptsOnlySameQuestionAndSecureOrigin(){
        assertTrue(ZhihuAnswers.first(question).contains("offset=0"));
        String good="https://www.zhihu.com/api/v4/questions/123/answers?offset=10&limit=10";
        assertEquals(good,ZhihuAnswers.next(question,good));
        for(String bad:new String[]{good.replace("123","456"),good.replace("https:","http:"),good.replace("www.zhihu.com","evil.test"),good.replace("www.zhihu.com","www.zhihu.com:444"),good.replace("www.zhihu.com","user@www.zhihu.com"),good+"#fragment","garbage"})assertEquals("",ZhihuAnswers.next(question,bad));
    }
    @Test public void apiAnswersMergeWithDomAndNeverRunRemoteMarkup()throws Exception{
        Document before=SourceParser.article(Source.ZHIHU,"<div class='AnswerItem' data-zop='{\"itemId\":11}'><div class='RichContent-inner'><div class='RichText'><p>旧回答</p></div></div></div>",question.url);
        JSONObject page=new JSONObject("{\"data\":[{\"id\":11,\"content\":\"<p>旧回答</p>\"},{\"id\":12,\"question\":{\"id\":123},\"author\":{\"name\":\"新作者\"},\"content\":\"<p>真正新增的回答</p><script>alert(1)</script>\"},{\"id\":13,\"question\":{\"id\":999},\"content\":\"<p>其他问题</p>\"}]}");
        Set<String> ids=AnswerStream.answerIds(before);AnswerStream.merge(before,ZhihuAnswers.parse(question,page));
        assertEquals(2,AnswerStream.answers(before));assertTrue(AnswerStream.hasNewAnswers(before,ids));
        String html=ReaderHtml.render(before,Source.ZHIHU,19);assertTrue(html.contains("真正新增"));assertFalse(html.contains("alert(1)"));assertFalse(html.contains("其他问题"));
    }
    @Test public void continuationFailureIsVisibleAtBottom(){
        Document d=new Document();d.url=question.url;d.moreStatus="login";d.notice="登录后重试，已读回答保留";
        String html=ReaderHtml.render(d,Source.ZHIHU,19);assertTrue(html.contains("continuation-status"));assertTrue(html.contains("需先登录来源"));
    }
}
