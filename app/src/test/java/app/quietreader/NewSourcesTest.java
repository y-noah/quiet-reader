package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class NewSourcesTest {
    @Test public void clsPreservesOfficialOrderNotReadCountAndSkipsVideo()throws Exception{
        String raw="{\"errno\":0,\"data\":[{\"id\":12,\"title\":\"第一\",\"readNum\":5},{\"id\":13,\"title\":\"视频\",\"article_schema\":\"cailianshe://normal_video_detail?video_id=13\"},{\"id\":14,\"title\":\"第二\",\"readNum\":500},{\"id\":12,\"title\":\"重复\"},{\"id\":\"../invalid\",\"title\":\"错误\"}]}";
        java.util.List<Item> items=SourceParser.list(Source.CLS,raw);
        assertEquals(2,items.size());assertEquals("第一",items.get(0).title);assertEquals("第二",items.get(1).title);
        assertEquals("https://api3.cls.cn/share/article/12?os=android&sv=835",items.get(0).url);
    }
    @Test(expected=IllegalStateException.class) public void clsErrorIsNotAnEmptySuccessfulBoard()throws Exception{
        SourceParser.list(Source.CLS,"{\"errno\":10012,\"data\":[]}");
    }
    @Test public void clsOnlyExtractsOwnBodyAndKeepsLinks(){
        Document d=SourceParser.article(Source.CLS,"<title>页面</title><section class=title-box>本篇</section><section class=information-box>财联社 · 今天</section><section class=content-box><div class=content><p>正文 <a href='/share/article/2'>相关报道</a></p></div></section><div class=related-article-box><section class=content-box><div class=content>推荐污染</div></section></div><div class=openapp>下载APP</div>","https://api3.cls.cn/share/article/1");
        assertEquals("本篇",d.title);assertEquals(1,d.sections.size());
        String html=ReaderHtml.render(d,Source.CLS,19);
        assertTrue(html.contains("正文"));assertTrue(html.contains("https://api3.cls.cn/share/article/2"));
        assertFalse(html.contains("推荐污染"));assertFalse(html.contains("下载APP"));
    }
}
