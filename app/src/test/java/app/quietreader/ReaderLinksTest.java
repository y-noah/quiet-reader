package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
public class ReaderLinksTest {
    @Test public void bareDealUrlsAreClickableWithoutChinesePunctuation(){
        String html=ReaderLinks.plain("款 https://item.jd.com/100291533956.html，好价 https://www.smzdm.com/p/1832826816/，也是好价！");
        assertTrue(html.contains("href='https://item.jd.com/100291533956.html'"));
        assertTrue(html.contains("href='https://www.smzdm.com/p/1832826816/'"));
        assertTrue(html.contains("</a>，也是好价"));
    }
    @Test public void escapingAndForbiddenSchemesRemainSafe(){
        assertEquals("&lt;script&gt;",ReaderLinks.plain("<script>"));
        for(String url:new String[]{"javascript:alert(1)","intent://jd","file:///data/a","https://u:p@example.com/a",ReaderHtml.ACTION+"more"})assertFalse(ReaderLinks.allowed(url));
        assertTrue(ReaderLinks.plain("https://example.com/?a=1&b=2").contains("a=1&amp;b=2"));
    }
    @Test public void sourceAnchorLabelAndRelativeDestinationSurviveParsing(){
        Document doc=SourceParser.article(Source.HUPU,"<div class='thread-content-detail'>正文 <a href='/123.html'><b>另一个帖子</b></a> 和 <a href='https://item.jd.com/1.html'>去购买</a></div>","https://bbs.hupu.com/1.html");
        String html=ReaderHtml.render(doc,Source.HUPU,19);
        assertTrue(html.contains("href='https://bbs.hupu.com/123.html'>另一个帖子</a>"));
        assertTrue(html.contains("href='https://item.jd.com/1.html'>去购买</a>"));
    }
    @Test public void exactlyFiveDomesticSourcesVisible(){
        assertEquals(5,java.util.Arrays.stream(Source.values()).filter(Source::visible).count());
        assertFalse(Source.AGGREGATE.visible());assertEquals(6,Source.values().length);
    }
    @Test public void shoppingRedirectsAndUnadaptedPagesGoToBrowser(){
        assertNull(ReaderLinks.readerSource("https://www.smzdm.com/p/1832826816/"));
        assertNull(ReaderLinks.readerSource("https://tieba.baidu.com/p/123"));
        assertNull(ReaderLinks.readerSource("https://go.smzdm.com/abc/"));
        assertNull(ReaderLinks.readerSource("https://link.zhihu.com/?target=https%3A%2F%2Fexample.com"));
        assertNull(ReaderLinks.readerSource("https://item.jd.com/100291533956.html"));
    }
}
