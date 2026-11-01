package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ArticleReadIdentityTest {
    @Test public void actualOriginalCanBeReadWithoutMatchingRankingMetadata(){
        Item item=new Item(Source.V2EX,"标题更新了","https://www.v2ex.com/t/123","");
        Document doc=new Document();doc.url=item.url;doc.title="旧标题";doc.blocks.add(new Block("text","离线缓存里实际显示的完整正文"));
        doc.metadataHash="0".repeat(64);
        assertTrue(ArticleReadHistory.displayed(item,doc));
        assertFalse(ArticleVersion.matches(item,doc));
        doc.url="https://www.v2ex.com/t/124";assertFalse(ArticleReadHistory.displayed(item,doc));
        doc.url=item.url;doc.loginRequired=true;assertFalse(ArticleReadHistory.displayed(item,doc));
        assertFalse(ArticleReadHistory.displayed(null,doc));
    }
    @Test public void forumReplyCountPaginationAndMobileHostAreOneOriginal(){
        assertEquals(ArticleReadIdentity.key(Source.V2EX,"https://www.v2ex.com/t/123#reply20"),ArticleReadIdentity.key(Source.V2EX,"https://v2ex.com/t/123?p=2#reply40"));
        assertEquals(ArticleReadIdentity.key(Source.TIEBA,"https://tieba.baidu.com/p/123"),ArticleReadIdentity.key(Source.TIEBA,"https://tieba.baidu.com/p/123?pn=2"));
        assertEquals(ArticleReadIdentity.key(Source.HUPU,"https://bbs.hupu.com/123.html"),ArticleReadIdentity.key(Source.HUPU,"https://bbs.hupu.com/123-2.html"));
    }
    @Test public void distinctOriginalsAndAnswersStayDistinct(){
        assertNotEquals(ArticleReadIdentity.key(Source.V2EX,"https://v2ex.com/t/123"),ArticleReadIdentity.key(Source.V2EX,"https://v2ex.com/t/124"));
        assertNotEquals(ArticleReadIdentity.key(Source.ZHIHU,"https://www.zhihu.com/question/123/answer/1"),ArticleReadIdentity.key(Source.ZHIHU,"https://www.zhihu.com/question/123/answer/2"));
        assertNotEquals(ArticleReadIdentity.key(Source.BAIDU,"https://www.baidu.com/s?wd=one"),ArticleReadIdentity.key(Source.BAIDU,"https://www.baidu.com/s?wd=two"));
    }
    @Test public void anchorsAndTrackingDoNotBypassButRoutesRemainDistinct(){
        assertEquals(ArticleReadIdentity.key(Source.BBC,"https://www.bbc.com/zhongwen/articles/123"),ArticleReadIdentity.key(Source.BBC,"https://www.bbc.com/zhongwen/articles/123?utm_source=feed#section2"));
        assertNotEquals(ArticleReadIdentity.key(Source.BBC,"https://www.bbc.com/#/123"),ArticleReadIdentity.key(Source.BBC,"https://www.bbc.com/#/124"));
    }
    @Test public void failedRestrictedOrEmptyContentIsNotRead(){
        Document doc=new Document();assertFalse(ArticleReadHistory.readable(doc));
        doc.blocks.add(new Block("text","可阅读的完整正文"));assertTrue(ArticleReadHistory.readable(doc));
        doc.loginRequired=true;assertFalse(ArticleReadHistory.readable(doc));doc.loginRequired=false;
        doc.sourceUnavailable=true;assertFalse(ArticleReadHistory.readable(doc));doc.sourceUnavailable=false;
        doc.filteredVideo=true;assertFalse(ArticleReadHistory.readable(doc));doc.filteredVideo=false;
        doc.unsupportedVideo=true;assertFalse(ArticleReadHistory.readable(doc));
    }
}
