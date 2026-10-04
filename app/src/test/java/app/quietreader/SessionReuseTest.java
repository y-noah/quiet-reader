package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SessionReuseTest {
    private Item question(String url){return new Item(Source.ZHIHU,"测试问题",url,"");}
    @Test public void sameQuestionWithQueryOrFragmentCanBeReused() {
        Item item=question("https://www.zhihu.com/question/123?from=hot");
        assertTrue(AnswerStream.canReuse(item,"https://www.zhihu.com/question/123#answer"));
        assertTrue(AnswerStream.canReuse(item,"https://www.zhihu.com/question/123/"));
    }
    @Test public void answerPermalinkUsesFreshQuestionForContinuation() {
        Item item=question("https://www.zhihu.com/question/123/answer/456");
        assertFalse(AnswerStream.canReuse(item,item.url));
        assertFalse(AnswerStream.canReuse(item,"https://www.zhihu.com/question/123"));
        assertTrue(AnswerStream.sameQuestion(item,"https://www.zhihu.com/question/123"));
        assertFalse(AnswerStream.sameQuestion(item,"https://www.zhihu.com/question/124/answer/456"));
    }
    @Test public void neverReuseOtherQuestionOrAuthPage() {
        Item item=question("https://www.zhihu.com/question/123");
        for(String url:new String[]{"https://www.zhihu.com/question/1234","https://www.zhihu.com/signin","https://www.zhihu.com/question/123/answer/456","about:blank",null})
            assertFalse("Unexpected reusable page: "+url,AnswerStream.canReuse(item,url));
        assertFalse(AnswerStream.sameQuestion(item,"https://www.zhihu.com/signin"));
        assertTrue(AnswerStream.sameQuestion(item,"https://www.zhihu.com/question/123/answer/456"));
    }
    @Test public void rejectForeignHostsNonHttpsAndNonQuestionRoutes() {
        Item item=question("https://www.zhihu.com/question/123");
        for(String url:new String[]{"https://www.zhihu.com.evil.test/question/123","http://www.zhihu.com/question/123","https://user@www.zhihu.com/question/123","https://www.zhihu.com:444/question/123","https://zhuanlan.zhihu.com/p/123"}) {
            assertFalse(AnswerStream.sameQuestion(item,url));assertFalse(AnswerStream.canReuse(item,url));
        }
        assertFalse(AnswerStream.canReuse(new Item(Source.HUPU,"","https://bbs.hupu.com/123.html",""),"https://www.zhihu.com/question/123"));
        assertFalse(AnswerStream.sameQuestion(question("https://zhuanlan.zhihu.com/p/123"),"https://zhuanlan.zhihu.com/p/123"));
    }
}
