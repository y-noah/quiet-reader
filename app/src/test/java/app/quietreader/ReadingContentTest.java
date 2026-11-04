package app.quietreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReadingContentTest {
    @Test public void recommendationModulesNeverBecomeArticleTextOrLinks(){
        String html="<article class=c-article-content><p>正文<a href='https://example.org/citation'>引用来源</a></p><div class=recommend-container><a href='https://example.org/promo'>推荐文章</a></div><h3>推荐阅读</h3><ul><li><a href='https://example.org/other'>其他文章</a></li></ul></article>";
        Document doc=SourceParser.article(Source.IFANR,html,"https://www.ifanr.com/123");
        String rendered=ReaderHtml.render(doc,Source.IFANR,19);
        assertTrue(rendered.contains("引用来源"));assertTrue(rendered.contains("https://example.org/citation"));
        assertFalse(rendered.contains("推荐文章"));assertFalse(rendered.contains("其他文章"));assertFalse(rendered.contains("推荐阅读"));
    }
    @Test public void ordinaryRecommendationDiscussionAndLinkedProseArePreserved(){
        org.jsoup.nodes.Document page=Jsoup.parse("<article><h2>推荐算法如何工作</h2><p>我们研究推荐阅读，并<a href='https://example.org'>引用原文</a>进行分析。</p><h3>相关阅读</h3><p>作者对相关研究的讨论。</p></article>");
        ReadingContent.prune(page);
        assertTrue(page.text().contains("推荐算法如何工作"));assertTrue(page.text().contains("作者对相关研究的讨论"));assertEquals(1,page.select("a").size());
    }
    @Test public void recommendationParagraphRunsDoNotSurviveWithoutARecognizedModuleClass(){
        org.jsoup.nodes.Document page=Jsoup.parse("<article><p>BODY</p><h3>推荐阅读</h3><p><a href='https://example.org/promo1'>PROMO_ONE</a></p><p><a href='https://example.org/promo2'>PROMO_TWO</a></p><a href='https://example.org/promo3'>PROMO_THREE</a><p>BODY_AFTER</p></article>");
        ReadingContent.prune(page);
        assertFalse(page.text().contains("PROMO"));assertFalse(page.text().contains("推荐阅读"));assertEquals(0,page.select("a").size());assertTrue(page.text().contains("BODY_AFTER"));
    }
    @Test public void consecutiveBareRecommendationLinksAreAllRemoved(){
        org.jsoup.nodes.Document page=Jsoup.parse("<article><p>BODY</p><h3>相关阅读</h3><a href='https://example.org/promo1'>PROMO_ONE</a><a href='https://example.org/promo2'>PROMO_TWO</a></article>");
        ReadingContent.prune(page);
        assertEquals(0,page.select("a").size());assertFalse(page.text().contains("相关阅读"));assertEquals("BODY",page.text());
    }
    @Test public void cleaningLinkRunsStopsAtNormalLinkedProse(){
        org.jsoup.nodes.Document page=Jsoup.parse("<article><h3>推荐阅读</h3><p><a href='https://example.org/promo'>PROMO</a></p><p>作者继续分析，并<a href='https://example.org/citation'>引用原文</a>提供依据。</p><p><a href='https://example.org/later'>随后正文引用</a></p></article>");
        ReadingContent.prune(page);
        assertFalse(page.text().contains("PROMO"));assertEquals(2,page.select("a").size());assertTrue(page.text().contains("作者继续分析"));assertTrue(page.text().contains("随后正文引用"));
    }
    @Test public void unwrappedProseBetweenHeadingAndLinkProtectsTheCitation(){
        org.jsoup.nodes.Document page=Jsoup.parse("<article><h3>相关阅读</h3>这里是作者的正常说明，并非推荐目录：<a href='https://example.org/citation'>引用原文</a></article>");
        ReadingContent.prune(page);
        assertTrue(page.text().contains("这里是作者的正常说明"));assertEquals(1,page.select("a").size());assertEquals(1,page.select("h3").size());
    }
    @Test public void linkedImagesCaptionsAndForumNavigationRemain(){
        org.jsoup.nodes.Document page=Jsoup.parse("<article><h3>相关阅读</h3><p><a href='https://example.org/photo'><img src='https://example.org/photo.png'></a></p><figcaption>正常图片说明</figcaption><h3>更多推荐</h3><nav><a href='https://example.org/page2'>第二页</a></nav></article>");
        ReadingContent.prune(page);
        assertEquals(1,page.select("img").size());assertTrue(page.text().contains("正常图片说明"));assertEquals(1,page.select("nav a").size());
    }
}
