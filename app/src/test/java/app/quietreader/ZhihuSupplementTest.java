package app.quietreader;

import static app.quietreader.Models.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class ZhihuSupplementTest {
    private Document question(String body,String extra,String url){return SourceParser.article(Source.ZHIHU,"<h1>合成问题</h1><div class=QuestionRichText>"+body+"</div>"+extra,url);}
    @Test public void expandedSupplementKeepsQuestionIdentityAndReplacesPreview(){
        Document preview=question("问题摘要","","https://www.zhihu.com/question/123"),full=question("问题摘要以及展开后才出现的完整补充说明","","https://www.zhihu.com/question/123/answer/456");
        assertEquals(preview.sections.get(0).id,full.sections.get(0).id);AnswerStream.merge(preview,full);assertEquals(1,preview.sections.size());assertEquals(0,AnswerStream.answers(preview));assertTrue(preview.blocks.get(0).value.contains("完整补充说明"));
    }
    @Test public void legacySupplementRetainsItsVisibleAnchorAndOtherQuestionsAreNotCollapsed(){
        Document preview=question("问题摘要","","https://www.zhihu.com/question/123");Section legacy=new Section("part-old-cached-id","问题补充",false);legacy.blocks.addAll(preview.sections.get(0).blocks);preview.sections.set(0,legacy);
        AnswerStream.merge(preview,question("问题摘要以及新取得的完整补充","","https://www.zhihu.com/question/123"));assertEquals(1,preview.sections.size());assertEquals(legacy.id,preview.sections.get(0).id);assertTrue(preview.blocks.get(0).value.contains("完整补充"));
        AnswerStream.merge(preview,question("其他问题的补充","","https://www.zhihu.com/question/999"));assertEquals(2,preview.sections.size());
    }
    @Test public void sourcePartialContentWarningSurvivesZhihuReadingInstructions(){
        Document partial=question("来源只返回摘要","<button>展开阅读全文</button>","https://www.zhihu.com/question/123");assertTrue(partial.notice.contains("部分内容"));assertTrue(partial.notice.contains("未加载部分"));
        Document normal=question("来源当前补充","","https://www.zhihu.com/question/123");assertFalse(normal.notice.contains("部分内容"));
    }
    @Test public void parallelDesktopAndMobileContainersDoNotDuplicateTheSameSupplement(){
        Document both=question("摘要","<div class=QuestionRichText>摘要以及完整的问题补充</div>","https://www.zhihu.com/question/123");assertEquals(1,both.sections.size());assertEquals(1,both.blocks.size());assertEquals("摘要以及完整的问题补充",both.blocks.get(0).value);
    }
}
