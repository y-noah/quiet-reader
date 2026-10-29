package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
public class ReaderLinksTest {
    @Test public void supportedDirectAndZhihuWrappedPostsUseReader() throws Exception {
        for(String target:new String[]{"https://tieba.baidu.com/p/123","https://www.v2ex.com/t/123?p=2&text=a%2Bb#reply1","https://www.zhihu.com/question/123/answer/456"}){
            assertEquals(target,ReaderLinks.readerTarget(target));
            assertEquals(target,ReaderLinks.readerTarget("https://link.zhihu.com/?target="+java.net.URLEncoder.encode(target,"UTF-8")));
        }
    }
    @Test public void ambiguousOrUnsupportedWrappersKeepBrowserBehavior(){
        for(String url:new String[]{
            "https://link.zhihu.com/?target=https%3A%2F%2Fexample.com",
            "https://link.zhihu.com/?target=https%3A%2F%2Fwww.v2ex.com%2Ft%2F123&target=https%3A%2F%2Ftieba.baidu.com%2Fp%2F123",
            "https://link.zhihu.com/?target=https%3A%2F%2Fwww.v2ex.com%2Ft%2F123&other=1",
            "https://link.zhihu.com/other?target=https%3A%2F%2Fwww.v2ex.com%2Ft%2F123",
            "https://link.zhihu.com/?target=https%3A%2F%2Fwww.v2ex.com%2Ft%2F123#other",
            "https://link.zhihu.com.evil.example/?target=https%3A%2F%2Fwww.v2ex.com%2Ft%2F123",
            "https://link.zhihu.com/?target=https%253A%252F%252Fwww.v2ex.com%252Ft%252F123",
            "https://link.zhihu.com/?target=http%3A%2F%2Fwww.v2ex.com%2Ft%2F123",
            "https://link.zhihu.com/?target=https%3A%2F%2Fu%3Ap%40www.v2ex.com%2Ft%2F123",
            "https://link.zhihu.com/?target=javascript%3Aalert(1)",
            "https://link.zhihu.com/?target=%ZZ","https://link.zhihu.com/"})assertEquals(url,"",ReaderLinks.readerTarget(url));
    }
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
        assertEquals(15,Source.values().length);
    }
    @Test public void shoppingRedirectsAndUnadaptedPagesGoToBrowser(){
        assertNull(ReaderLinks.readerSource("https://www.smzdm.com/p/1832826816/"));
        assertEquals(Source.TIEBA,ReaderLinks.readerSource("https://tieba.baidu.com/p/123"));
        assertNull(ReaderLinks.readerSource("https://go.smzdm.com/abc/"));
        assertNull(ReaderLinks.readerSource("https://link.zhihu.com/?target=https%3A%2F%2Fexample.com"));
        assertNull(ReaderLinks.readerSource("https://item.jd.com/100291533956.html"));
    }
}
