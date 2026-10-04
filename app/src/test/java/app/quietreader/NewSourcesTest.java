package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class NewSourcesTest {
    @Test public void orderedFiveAndRetiredPlatformsStayHidden(){
        assertArrayEquals(new Source[]{Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.IFANR},Source.displayOrder());
        assertTrue(Source.IFANR.visible());assertFalse(Source.GEEKPARK.visible());assertFalse(Source.GEEKPARK.readable());
        assertFalse(Source.TIEBA.visible());assertFalse(Source.WALLSTREET.visible());
        assertNull(ReaderLinks.readerSource("https://wallstreetcn.com/articles/123"));
    }
    @Test public void clsPreservesOfficialOrderNotReadCountAndSkipsVideo()throws Exception{
        String raw="{\"errno\":0,\"data\":[{\"id\":12,\"title\":\"第一\",\"readNum\":5},{\"id\":13,\"title\":\"视频\",\"article_schema\":\"cailianshe://normal_video_detail?video_id=13\"},{\"id\":14,\"title\":\"第二\",\"readNum\":500},{\"id\":12,\"title\":\"重复\"},{\"id\":\"../invalid\",\"title\":\"错误\"}]}";
        java.util.List<Item> items=SourceParser.list(Source.CLS,raw);
        assertEquals(2,items.size());assertEquals("第一",items.get(0).title);assertEquals("第二",items.get(1).title);
        assertEquals("https://api3.cls.cn/share/article/12?os=android&sv=835",items.get(0).url);
    }
    @Test(expected=IllegalStateException.class) public void clsErrorIsNotAnEmptySuccessfulBoard()throws Exception{
        SourceParser.list(Source.CLS,"{\"errno\":10012,\"data\":[]}");
    }
    @Test public void geekparkKeepsSevenDayRankingAndFiltersDedicatedVideos()throws Exception{
        java.util.List<Item> rows=SourceParser.list(Source.GEEKPARK,"{\"posts\":[{\"id\":1,\"title\":\"文字\",\"post_type\":\"text\"},{\"id\":2,\"title\":\"视频\",\"post_type\":\"pure_video\"},{\"id\":3,\"title\":\"采访\",\"post_type\":\"video\"}]}");
        assertEquals(1,rows.size());assertEquals("https://www.geekpark.net/news/1",rows.get(0).url);
    }
    @Test public void clsOnlyExtractsOwnBodyAndKeepsLinks(){
        Document d=SourceParser.article(Source.CLS,"<title>页面</title><section class=title-box>本篇</section><section class=information-box>财联社 · 今天</section><section class=content-box><div class=content><p>正文 <a href='/share/article/2'>相关报道</a></p></div></section><div class=related-article-box><section class=content-box><div class=content>推荐污染</div></section></div><div class=openapp>下载APP</div>","https://api3.cls.cn/share/article/1");
        assertEquals("本篇",d.title);assertEquals(1,d.sections.size());
        String html=ReaderHtml.render(d,Source.CLS,19);
        assertTrue(html.contains("正文"));assertTrue(html.contains("https://api3.cls.cn/share/article/2"));
        assertFalse(html.contains("推荐污染"));assertFalse(html.contains("下载APP"));
    }
    @Test public void geekparkOnlyExtractsMainArticleAndRejectsVideo(){
        String html="<h1>标题</h1><div id=article-body><div class=article-content><p>科技正文</p></div></div><aside><div class=article-content>推荐</div></aside>";
        Document d=SourceParser.article(Source.GEEKPARK,html,"https://www.geekpark.net/news/1");
        assertEquals(1,d.sections.size());assertEquals("科技正文",d.blocks.get(0).value);
        assertTrue(SourceParser.article(Source.GEEKPARK,"<div id=play-room class=video-player></div>"+html,d.url).filteredVideo);
    }
    @Test public void newSourceLinksRequireRealHosts(){
        assertEquals(Source.CLS,ReaderLinks.readerSource("https://api3.cls.cn/share/article/12"));
        assertNull(ReaderLinks.readerSource("https://www.geekpark.net/news/1"));
        assertFalse(UrlPolicy.belongs(Source.CLS,"https://cls.cn.evil.test/detail/1"));
        assertFalse(UrlPolicy.belongs(Source.GEEKPARK,"https://geekpark.net.evil.test/news/1"));
    }
}
