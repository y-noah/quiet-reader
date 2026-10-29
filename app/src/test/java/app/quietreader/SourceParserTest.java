package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SourceParserTest {
    @Test public void renderedV2exImageDoesNotLeaveEmptyMarkdownSyntax()throws Exception{
        String html;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/v2ex-hybrid-image-fragment-20261027.html")){assertNotNull(in);html=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        Document d=SourceParser.article(Source.V2EX,html,"https://www.v2ex.com/t/1251768");
        assertEquals(1,d.sections.size());assertEquals("v2ex-main",d.sections.get(0).id);
        assertEquals(2,d.blocks.size());assertEquals("补充说明",d.blocks.get(0).value);
        assertEquals("image",d.blocks.get(1).type);assertEquals("https://i.imgur.com/eJmm0o8.jpeg",d.blocks.get(1).value);
    }
    @Test public void hybridImageCleanupPreservesProseRepliesAndImageOrder(){
        String image="<a href='https://i.imgur.com/one.jpg'><img src='https://i.imgur.com/one.jpg'></a>";
        String html="<div class=topic_content>前文![]("+image+")后文![](<img src='https://i.imgur.com/two.jpg'>)末尾</div><div class=cell id=r_10><strong><a href='/member/example'>回复作者</a></strong><div class=reply_content>回复前文![]("+image+")回复后文</div></div>";
        Document d=SourceParser.article(Source.V2EX,html,"https://www.v2ex.com/t/123");
        assertEquals(2,d.sections.size());assertEquals("v2ex-r_10",d.sections.get(1).id);assertTrue(d.sections.get(1).label.contains("回复作者"));
        assertEquals(java.util.List.of("前文","https://i.imgur.com/one.jpg","后文","https://i.imgur.com/two.jpg","末尾"),d.sections.get(0).blocks.stream().map(b->b.value).toList());
        assertEquals(java.util.List.of("回复前文","https://i.imgur.com/one.jpg","回复后文"),d.sections.get(1).blocks.stream().map(b->b.value).toList());
    }
    @Test public void hybridImageCleanupNeverGuessesAtLiteralOrUnmatchedSyntax(){
        String image="<a href='https://i.imgur.com/one.jpg'><img src='https://i.imgur.com/one.jpg'></a>";
        for(String fragment:new String[]{"<pre>![]("+image+")</pre>","<code>![]("+image+")</code>","![原始说明]("+image+")","![]("+image,"文字"+image+")","![](<a href='https://example.org/article'><img src='https://i.imgur.com/one.jpg'></a>)","![](<img src='javascript:bad'>)","![](<span>说明</span>"+image+")","![]("+image+"<br>)"}){
            Document d=SourceParser.article(Source.V2EX,"<div class=topic_content>"+fragment+"</div>","https://www.v2ex.com/t/123");
            String text=d.blocks.stream().filter(b->b.type.equals("text")).map(b->b.value).collect(java.util.stream.Collectors.joining());
            assertTrue(fragment,text.contains(fragment.startsWith("文字")?")":fragment.startsWith("![原始")?"![原始说明](":"![]("));
        }
        Document other=SourceParser.article(Source.IFANR,"<article class=c-article-content>![]("+image+")</article>","https://www.ifanr.com/article/123/");
        assertTrue(other.blocks.stream().anyMatch(b->b.value.contains("![](")));
    }
    @Test public void rejectsActiveAndLocalSchemes() {
        assertFalse(UrlPolicy.https("javascript:alert(1)"));
        assertFalse(UrlPolicy.belongs(Source.WEIBO,"https://weibo.com.evil.test/post"));
        assertFalse(UrlPolicy.https("https://user:pass@weibo.com/"));
        assertFalse(UrlPolicy.https("file:///data/private"));
        assertFalse(UrlPolicy.https("intent://weibo"));
        assertTrue(UrlPolicy.belongs(Source.WEIBO,"https://s.weibo.com/weibo?q=x"));
    }
    @Test public void articleDoesNotIncludeAdsRecommendationsOrScripts() {
        Document d=SourceParser.article(Source.IFANR,"<h1>长文</h1><article class=c-article-content><p>第一段</p><p>第二段<img src='/a.jpg'></p><script>alert(1)</script><aside>推荐</aside></article><div>下载App</div>","https://www.ifanr.com/article/1/");
        assertEquals("长文",d.title); assertEquals(3,d.blocks.size());
        assertEquals("第一段",d.blocks.get(0).value); assertEquals("https://www.ifanr.com/a.jpg",d.blocks.get(2).value);
    }
    @Test public void noFallbackToWholePageOnLoginWall() {
        Document d=SourceParser.article(Source.ZHIHU,"<h1>登录</h1><main>手机号 验证码 下载App</main>","https://www.zhihu.com/signin");
        assertFalse(d.hasContent()); assertFalse(d.notice.isEmpty());
    }
    @Test public void partialContentIsLabelled() {
        Document d=SourceParser.article(Source.HUPU,"<div class='thread-content'>开头</div><p>登录后查看全部评论</p>","https://bbs.hupu.com/1.html");
        assertTrue(d.hasContent()); assertTrue(d.notice.contains("部分"));
    }
    @Test public void parsesTopicLinksWithoutForeignDestinations() {
        Document d=SourceParser.article(Source.WEIBO,"<a href='/detail/123/'>这是一篇新闻文章</a><a href='https://evil.test/detail/456/'>恶意外部站点文章</a>","https://s.weibo.com/weibo?q=1");
        assertEquals(1,d.related.size());
    }
    @Test public void deduplicatesAndRejectsPromoLinks() throws Exception {
        String raw="<div id='pl_top_realtimehot'><table><tr><td class='td-02'><a href='/weibo?q=hello'>热搜标题</a><a href='javascript:void(0)'>广告</a><a href='/weibo?q=hello'>热搜标题</a></td></tr></table></div>";
        assertEquals(1,SourceParser.list(Source.WEIBO,raw).size());
        assertEquals("https://s.weibo.com/weibo?q=hello",SourceParser.list(Source.WEIBO,raw).get(0).url);
    }
    @Test public void blockedArticleDoesNotBecomeRecommendationFeed() {
        Document d=SourceParser.article(Source.IFANR,"<h1>请登录</h1><a href='/detail/123/'>这是一篇推荐文章</a>","https://www.ifanr.com/article/456/");
        assertFalse(d.hasContent());assertTrue(d.related.isEmpty());
        Document topic=SourceParser.article(Source.WEIBO,"<aside><a href='/detail/123/'>这是一篇推荐文章</a></aside><main><a href='/detail/456/'>这是一篇话题文章</a></main>","https://s.weibo.com/weibo?q=1");
        assertEquals(1,topic.related.size());assertTrue(topic.related.get(0).url.contains("456"));
    }
    @Test public void videoPlaceholderIsNotArticleText() {
        Document d=SourceParser.article(Source.IFANR,"<article class=c-article-content><div>视频加载中...</div><video src='x'></video><p>视频的文字介绍</p></article>","https://www.ifanr.com/article/123/");
        assertEquals(1,d.blocks.stream().filter(b->b.type.equals("text")).count());assertEquals("视频的文字介绍",d.blocks.stream().filter(b->b.type.equals("text")).findFirst().get().value);assertTrue(d.notice.contains("包含视频"));
    }
    @Test public void nextPageMustRemainInSameThread() {
        String base="https://bbs.hupu.com/123.html";
        Document d=SourceParser.article(Source.HUPU,"<meta name='author' content='原作者'><div class='thread-content'>内容</div><a rel='next' href='/456-2.html'>下一页</a><a href='/123-2.html'>下一页</a>",base);
        assertEquals("原作者",d.byline);assertEquals("https://bbs.hupu.com/123-2.html",d.nextUrl);
        assertTrue(ReaderHtml.render(d,Source.HUPU,19).contains("继续阅读下一页"));
        assertFalse(UrlPolicy.sameThread(Source.HUPU,base,"https://evil.test/123-2.html"));
    }
}
