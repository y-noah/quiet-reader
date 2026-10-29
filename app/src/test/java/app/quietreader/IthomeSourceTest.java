package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class IthomeSourceTest {
    private String board(String rows){return "<div id=rank><ul class=bar><li data-id=1>日榜</li><li data-id=2>周榜</li></ul><ul class=order id=d-1>"+rows+"</ul><ul class=order id=d-2><li><a href='/1/111/999.htm'>周榜另文</a></li></ul></div>";}
    @Test public void onlyObservedDayRegionCountsDespiteDuplicateIdsAndOtherFeedLinks()throws Exception{
        String html="<ul id=d-1><li><a href='/1/111/000.htm'>下载广告重复编号</a></li></ul><a href='/1/111/333.htm'>最新文章</a>"+board("<li><a href='/1/111/001.htm'>日榜第一条</a></li><li><a href='https://www.ithome.com.evil.test/1/111/002.htm'>假来源</a></li><li><a href='/1/111/001.htm'>重复</a></li><li><a href='/1/111/002.htm'>日榜第二条</a></li>");
        List<Item> rows=SourceParser.list(Source.ITHOME,html);assertEquals(2,rows.size());assertEquals("日榜第一条",rows.get(0).title);assertEquals("日榜 #2",rows.get(1).detail);assertFalse(rows.get(0).video);
    }
    @Test public void absentDayDoesNotFallBackToLatestOrWeekly(){
        for(String html:Arrays.asList("<a href='/1/111/001.htm'>最新</a>","<div id=rank><ul class=bar><li data-id=1>日榜</li></ul><ul id=d-2 class=order><li><a href='/1/111/001.htm'>周榜</a></li></ul></div>"))try{SourceParser.list(Source.ITHOME,html);fail();}catch(Exception expected){assertTrue(expected instanceof IllegalStateException);}
    }
    @Test public void articleIsOwnedAndSharedReaderUsesActualLazyImage(){
        String html="<h1>公开图文标题</h1><span id=author_baidu>作者：测试作者</span><div id=paragraph class=post_content><p>"+"这是来源正文内容。".repeat(25)+"</p><img src='//img.ithome.com/images/v2/t.png' data-original='https://img.ithome.com/photo.jpg'></div><aside>推荐与广告不能进入正文</aside><div class=comment>未加载评论不冒充正文</div>";
        Document doc=SourceParser.article(Source.ITHOME,html,"https://www.ithome.com/1/111/001.htm");assertTrue(doc.hasContent());assertEquals("作者：测试作者",doc.byline);assertEquals(1,doc.sections.size());
        assertTrue(doc.blocks.stream().anyMatch(block->block.type.equals("image")&&block.value.equals("https://img.ithome.com/photo.jpg")));assertFalse(ReaderHtml.render(doc,Source.ITHOME,19).contains("推荐与广告不能进入正文"));
        assertTrue(SourceParser.article(Source.ITHOME,html,"https://www.ithome.com/").sourceUnavailable);
    }
    @Test public void readerLinksAndSourceBoundariesRecognizeRealArticlePaths(){
        assertEquals(Source.ITHOME,ReaderLinks.readerSource("https://www.ithome.com/1/017/276.htm"));assertNull(ReaderLinks.readerSource("https://www.ithome.com/"));
        assertFalse(IthomeSource.articleUrl("https://www.ithome.com.evil.test/1/017/276.htm"));assertFalse(IthomeSource.articleUrl("https://img.ithome.com/1/017/276.htm"));assertFalse(IthomeSource.articleUrl("https://www.ithome.com/1/017/276.htm/more"));
    }
}
