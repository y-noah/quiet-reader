package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class AdditionalSourcesTest {
    @Test public void aggregateAddsOneEntryAndKeepsSixTabs(){
        assertArrayEquals(Source.displayOrder(),Source.aggregateSources());
        assertEquals(5,new HashSet<>(Arrays.asList(Source.aggregateSources())).size());
        assertEquals(6,Source.navigationOrder().length);
        assertEquals(Source.AGGREGATE,Source.navigationOrder()[0]);
        assertFalse(Source.AGGREGATE.readable());
        for(Source source:Source.aggregateSources())assertTrue(source.readable());
    }
    @Test public void ifanrListUsesObservedArticleContainersAndSkipsForeignDuplicates()throws Exception{
        List<Item> rows=SourceParser.list(Source.IFANR,"<div class='article-info'><h3><a href='/123'>新文章</a><a href='/123'>重复文章</a><a href='https://evil.test/124'>外站</a></h3></div><nav><a href='/125'>导航</a></nav><a class='js-title-transform' href='/126' title='第二篇'>摘要</a>");
        assertEquals(2,rows.size());assertEquals("https://www.ifanr.com/123",rows.get(0).url);
        assertEquals("第二篇",rows.get(1).title);assertTrue(rows.get(0).detail.contains("非热榜"));
    }
    @Test public void ifanrVideosAreExcludedWithoutSuppressingText()throws Exception{
        List<Item> rows=SourceParser.list(Source.IFANR,"<article class='article-info' data-type='video'><h3><a href='/1'>视频</a></h3></article><article class='article-info'><h3><a href='/2'>文字</a></h3></article>");
        assertEquals(1,rows.size());assertEquals("文字",rows.get(0).title);
    }
    @Test(expected=IllegalStateException.class) public void failedIfanrPageCannotBecomeEmptySuccess()throws Exception{
        SourceParser.list(Source.IFANR,"<h1>登录</h1><a href='/123'>未确认导航</a>");
    }
    @Test public void ifanrReaderExcludesRecommendationsAndPreservesArticleLinks(){
        String url="https://www.ifanr.com/123";
        Document doc=SourceParser.article(Source.IFANR,"<h1>标题</h1><article class='c-article-content'><p>文章正文 <a href='/124'>相关原文</a></p><aside>推荐污染</aside></article><button>下载App</button>",url);
        String html=ReaderHtml.render(doc,Source.IFANR,19);
        assertTrue(doc.hasContent());assertTrue(html.contains("文章正文"));assertTrue(html.contains("https://www.ifanr.com/124"));
        assertFalse(html.contains("推荐污染"));assertFalse(html.contains("下载App"));
        assertEquals(Source.IFANR,ReaderLinks.readerSource(url));assertFalse(UrlPolicy.belongs(Source.IFANR,"https://www.ifanr.com.evil.test/123"));
    }
}
