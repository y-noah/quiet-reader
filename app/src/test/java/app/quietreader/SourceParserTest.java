package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SourceParserTest {
    @Test public void rejectsActiveAndLocalSchemes() {
        assertFalse(UrlPolicy.https("javascript:alert(1)"));
        assertFalse(UrlPolicy.belongs(Source.WEIBO,"https://weibo.com.evil.test/post"));
        assertFalse(UrlPolicy.https("https://user:pass@weibo.com/"));
        assertFalse(UrlPolicy.https("file:///data/private"));
        assertFalse(UrlPolicy.https("intent://weibo"));
        assertTrue(UrlPolicy.belongs(Source.WEIBO,"https://s.weibo.com/weibo?q=x"));
    }
    @Test public void articleDoesNotIncludeAdsRecommendationsOrScripts() {
        Document d=SourceParser.article(Source.TOUTIAO,"<h1>长文</h1><article><p>第一段</p><p>第二段<img src='/a.jpg'></p><script>alert(1)</script><aside>推荐</aside></article><div>下载App</div>","https://www.toutiao.com/article/1/");
        assertEquals("长文",d.title); assertEquals(3,d.blocks.size());
        assertEquals("第一段",d.blocks.get(0).value); assertEquals("https://www.toutiao.com/a.jpg",d.blocks.get(2).value);
    }
    @Test public void noFallbackToWholePageOnLoginWall() {
        Document d=SourceParser.article(Source.ZHIHU,"<h1>登录</h1><main>手机号 验证码 下载App</main>","https://www.zhihu.com/signin");
        assertFalse(d.hasContent()); assertFalse(d.notice.isEmpty());
    }
    @Test public void partialContentIsLabelled() {
        Document d=SourceParser.article(Source.TIEBA,"<div class='d_post_content'>开头</div><p>登录后查看全部评论</p>","https://tieba.baidu.com/p/1");
        assertTrue(d.hasContent()); assertTrue(d.notice.contains("部分"));
    }
    @Test public void parsesTopicLinksWithoutForeignDestinations() {
        Document d=SourceParser.article(Source.TOUTIAO,"<a href='/article/123/'>这是一篇新闻文章</a><a href='https://evil.test/article/456/'>恶意外部站点文章</a>","https://www.toutiao.com/trending/1/");
        assertEquals(1,d.related.size());
    }
    @Test public void deduplicatesAndRejectsPromoLinks() throws Exception {
        String raw="<div id='pl_top_realtimehot'><table><tr><td class='td-02'><a href='/weibo?q=hello'>热搜标题</a><a href='javascript:void(0)'>广告</a><a href='/weibo?q=hello'>热搜标题</a></td></tr></table></div>";
        assertEquals(1,SourceParser.list(Source.WEIBO,raw).size());
        assertEquals("https://s.weibo.com/weibo?q=hello",SourceParser.list(Source.WEIBO,raw).get(0).url);
    }
    @Test public void blockedArticleDoesNotBecomeRecommendationFeed() {
        Document d=SourceParser.article(Source.TOUTIAO,"<h1>请登录</h1><a href='/article/123/'>这是一篇推荐文章</a>","https://www.toutiao.com/article/456/");
        assertFalse(d.hasContent());assertTrue(d.related.isEmpty());
        Document topic=SourceParser.article(Source.TOUTIAO,"<aside><a href='/article/123/'>这是一篇推荐文章</a></aside><main><a href='/article/456/'>这是一篇话题文章</a></main>","https://www.toutiao.com/trending/1/");
        assertEquals(1,topic.related.size());assertTrue(topic.related.get(0).url.contains("456"));
    }
    @Test public void videoPlaceholderIsNotArticleText() {
        Document d=SourceParser.article(Source.TOUTIAO,"<article><div>视频加载中...</div><video src='x'></video><p>视频的文字介绍</p></article>","https://www.toutiao.com/article/123/");
        assertEquals(1,d.blocks.size());assertEquals("视频的文字介绍",d.blocks.get(0).value);assertTrue(d.notice.contains("包含视频"));
    }
    @Test public void modernTiebaSeparatesPostsFromAdsAndControls() {
        Document d=SourceParser.article(Source.TIEBA,"<span class='pb-title'>讨论标题</span><div class='pb-content-wrap'><div class='richtext-item'>正文</div><img data-src='https://tiebapic.baidu.com/example.jpg'></div><div class='comment-content'><div class='pb-rich-text'>回复内容</div><div class='pc-pb-comments-desc'>赞 回复</div></div><div class='feed-ad-container'><div class='ad-comment-content'>广告</div></div>","https://tieba.baidu.com/p/123");
        assertEquals("讨论标题",d.title);assertEquals(4,d.blocks.size());
        assertEquals("回复内容",d.blocks.get(3).value);assertTrue(d.notice.contains("当前页"));
        assertFalse(d.blocks.stream().anyMatch(b->b.value.contains("广告")||b.value.contains("赞 回复")));
    }
    @Test public void nextPageMustRemainInSameThread() {
        String base="https://bbs.hupu.com/123.html";
        Document d=SourceParser.article(Source.HUPU,"<meta name='author' content='原作者'><div class='thread-content'>内容</div><a rel='next' href='/456-2.html'>下一页</a><a href='/123-2.html'>下一页</a>",base);
        assertEquals("原作者",d.byline);assertEquals("https://bbs.hupu.com/123-2.html",d.nextUrl);
        assertTrue(ReaderHtml.render(d,Source.HUPU,19).contains("继续阅读下一页"));
        assertFalse(UrlPolicy.sameThread(Source.HUPU,base,"https://evil.test/123-2.html"));
        assertTrue(UrlPolicy.sameThread(Source.TIEBA,"https://tieba.baidu.com/p/123","https://tieba.baidu.com/p/123?pn=2"));
    }
    @Test public void parsesJsonBoards() throws Exception {
        assertEquals("标题",SourceParser.list(Source.TOUTIAO,"{\"data\":[{\"Title\":\"标题\",\"ClusterIdStr\":\"123\"}]}").get(0).title);
        assertEquals(1,SourceParser.list(Source.TIEBA,"{\"data\":{\"bang_topic\":{\"topic_list\":[{\"topic_name\":\"标题\",\"topic_url\":\"https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=1\"}]}}}").size());
    }
}
