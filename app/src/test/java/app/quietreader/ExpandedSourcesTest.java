package app.quietreader;
import org.junit.Test;
import java.util.*;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;
public class ExpandedSourcesTest {
    @Test public void baiduSkipsPinnedAndKeepsRealHeat()throws Exception{
        String html="<div class='category-wrap_x'><span class='index_x'>置顶</span><a href='https://www.baidu.com/s?wd=top'><span class='c-single-text-ellipsis'>置顶</span></a></div><div class='category-wrap_x'><span class='index_x'>1</span><a href='https://www.baidu.com/s?wd=one'><span class='c-single-text-ellipsis'>真实热搜</span></a><span class='hot-index_x'>10000</span></div>";
        List<Item> rows=SourceParser.list(Source.BAIDU,html);assertEquals(1,rows.size());assertEquals("真实热搜",rows.get(0).title);assertTrue(rows.get(0).detail.contains("10000"));
    }
    @Test public void tiebaTopicIsNotMistakenForOnePost()throws Exception{
        List<Item> rows=SourceParser.list(Source.TIEBA,"<a href='/hottopic/browse/hottopic?topic_id=12&topic_name=%E7%83%AD%E8%AE%AE'>4 热议</a><a href='/f?kw=abc'>吧</a>");assertEquals(1,rows.size());assertEquals("热议",rows.get(0).title);assertTrue(rows.get(0).url.contains("topic_id=12"));assertTrue(rows.get(0).source.webReading());
    }
    @Test public void douyinSkipsPinnedAndRetainsVideo()throws Exception{
        String html="<div><div><a href='/hot/1/x'><h3>置顶</h3></a></div></div><div><div><a href='/hot/2/y'><h3>热榜视频</h3></a></div><div>1209.5万 热度</div></div>";
        List<Item> rows=SourceParser.list(Source.DOUYIN,html);assertEquals(1,rows.size());assertEquals("热榜视频",rows.get(0).title);assertTrue(rows.get(0).video);assertEquals(1,VideoPolicy.filter(rows).size());
    }
    @Test public void bilibiliApiErrorDoesNotMasqueradeAsSuccessfulEmptyBoard()throws Exception{
        List<Item> rows=SourceParser.list(Source.BILIBILI,"{\"code\":0,\"data\":{\"list\":[{\"bvid\":\"BV123abc\",\"title\":\"视频\"}]}}");assertEquals(1,rows.size());assertTrue(rows.get(0).video);
        try{SourceParser.list(Source.BILIBILI,"{\"code\":-412}");fail();}catch(IllegalStateException expected){}
    }
    @Test public void xhsRetainsSignedDestinationWithoutInventingGlobalRank()throws Exception{
        List<Item> rows=SourceParser.list(Source.XHS,"<section class='note-item'><a href='/explore/abc'></a><a href='/explore/abc?xsec_token=test%2Ftoken&amp;xsec_source=pc'></a><a class='title'>笔记</a><span class='like-wrapper'><span class='count'>1.4万</span></span></section>");
        assertEquals(1,rows.size());assertTrue(rows.get(0).url.contains("xsec_token=test%2Ftoken&xsec_source=pc"));assertTrue(rows.get(0).detail.contains("1.4万"));
    }
    @Test public void sspaiUsesArticleCardsNotNavigation()throws Exception{
        List<Item> rows=SourceParser.list(Source.SSPAI,"<a href='/post/1'>导航</a><article class='article__card'><a class='article__card__link' href='/post/2'><p class='article__card__title'>正文标题</p><p>作者与摘要</p></a></article>");assertEquals(1,rows.size());assertEquals("正文标题",rows.get(0).title);
    }
    @Test public void v2exHotPageAndReaderKeepRepliesAndPagination()throws Exception{
        List<Item> rows=SourceParser.list(Source.V2EX,"<div id=Main><div class='cell item'><span class=item_title><a href='/t/42'>讨论</a></span></div></div>");assertEquals("https://www.v2ex.com/t/42",rows.get(0).url);
        Document doc=SourceParser.article(Source.V2EX,"<h1>讨论</h1><div class='topic_content'>主帖</div><div class='reply_content'>回复</div><aside>推荐</aside><a rel='next' href='/t/42?p=2'>下一页</a>",rows.get(0).url);
        assertEquals(2,doc.sections.size());assertEquals("https://www.v2ex.com/t/42?p=2",doc.nextUrl);assertFalse(ReaderHtml.render(doc,Source.V2EX,19).contains("推荐"));
    }
    @Test public void newPlatformAllowlistRejectsLookalikesAndAppSchemes(){
        for(Source s:Source.values())if(s.readable()){
            assertTrue(s.name(),UrlPolicy.loginAllowed(s,s.login));assertFalse(UrlPolicy.belongs(s,"https://"+UrlPolicy.host(s.endpoint)+".evil.test/"));assertFalse(UrlPolicy.belongs(s,"intent://example"));
        }
        assertTrue(UrlPolicy.loginAllowed(Source.TIEBA,"https://passport.baidu.com/v2/"));assertFalse(UrlPolicy.belongs(Source.BAIDU,"https://tieba.baidu.com/p/1"));
    }
    @Test public void wrongHostCannotBecomeAPlatformRow()throws Exception{
        try{SourceParser.list(Source.SSPAI,"<article class='article__card'><a class='article__card__link' href='https://evil.test/post/2'><p class='article__card__title'>假冒</p></a></article>");fail();}catch(IllegalStateException expected){}
    }

    @Test public void v2exUsesOnlySelectedHotPageMainListInOrder()throws Exception{
        assertEquals("https://www.v2ex.com/?tab=hot",Source.V2EX.endpoint);
        String html="<div id='Rightbar'><div class='cell item'><span class='item_title'><a href='/t/999'>侧栏</a></span></div></div><div id='Main'><div class='cell item'><span class='item_title'><a href='/t/12'>最热第一项</a></span></div><div class='cell item'><span class='item_title'><a href='/t/8'>最热第二项</a></span></div></div>";
        List<Item> rows=SourceParser.list(Source.V2EX,html);
        assertEquals(2,rows.size());assertTrue(rows.get(0).url.endsWith("/t/12"));assertTrue(rows.get(1).url.endsWith("/t/8"));
    }
}
