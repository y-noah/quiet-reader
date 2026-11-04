package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class StructuredSourcesTest {
    @Test public void mobileSummaryNavigationWrapperRendersAsOrdinaryProse(){
        String body="近期，面对市场阶段性波动，公司表示持续推进产品结构升级与技术迭代。";
        String html="<div id=results><div tpl=rel_brief_head><a class=summary_24I7N href='https://baijiahao.baidu.com/s?id=1880827045919051248'>"+body+"</a></div></div>";
        Document doc=SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test");
        assertEquals(body,doc.blocks.get(0).value);assertTrue(doc.blocks.get(0).inlineLinks.isEmpty());
        org.jsoup.nodes.Document rendered=org.jsoup.Jsoup.parse(ReaderHtml.render(doc,Source.BAIDU,19));
        assertEquals(body,rendered.selectFirst(".part p").text());assertTrue(rendered.select(".part p a").isEmpty());
    }
    @Test public void persistedWholeSummaryLinkIsRepairedAndRealCitationsSurvive()throws Exception{
        Document doc=new Document();doc.url="https://www.baidu.com/s?wd=test";Section section=new Section("search-intro","来源摘要",false);
        String whole="这段摘要被来源整个包在跳转容器中。",partial="根据公告，事件进展如下。";
        section.blocks.add(new Block("text",whole,java.util.List.of(),java.util.List.of(new InlineLink(0,whole.length(),"https://example.org/wrapper"))));
        section.blocks.add(new Block("text",partial,java.util.List.of(),java.util.List.of(new InlineLink(2,4,"https://example.org/citation"))));doc.sections.add(section);doc.blocks.addAll(section.blocks);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));
        assertEquals(2,restored.blocks.size());assertTrue(restored.blocks.get(0).inlineLinks.isEmpty());assertEquals(1,restored.blocks.get(1).inlineLinks.size());
        String html=ReaderHtml.render(restored,Source.BAIDU,19);assertFalse(html.contains("https://example.org/wrapper"));assertTrue(html.contains("https://example.org/citation"));
        doc.url="https://sspai.com/post/123";Document other=ReadingHistory.decode(ReadingHistory.encode(doc));assertEquals(1,other.blocks.get(0).inlineLinks.size());
    }
    @Test public void capturedMobileEventBriefProducesReusableText()throws Exception{
        String html;try(java.io.InputStream input=getClass().getResourceAsStream("/fixtures/baidu-mobile-event-brief-20261103.html")){html=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        Document doc=SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test");
        assertTrue(doc.hasContent());assertEquals(1,doc.sections.size());assertTrue(doc.blocks.get(0).value.contains("6750万余元"));assertTrue(doc.related.isEmpty());
    }
    @Test public void mobileSearchKeepsTheEventBriefAndRejectsUnrelatedCards(){
        String html="<title>事件 - 百度搜索</title><div id=results><div class=result tpl=rel_brief_head><article><a class='summary_24I7N cos-block'>法院一审公开宣判案件，依法作出判决。这是来源返回的事件摘要。</a></article></div><div class=result tpl=rel_entity><div class=abstract_7LFuU>人物履历</div></div><div class=result tpl=recommend_list_san><a href='https://example.org'>推荐文章</a></div></div>";
        Document doc=SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test");
        assertTrue(doc.hasContent());assertEquals(1,doc.sections.size());
        String rendered=ReaderHtml.render(doc,Source.BAIDU,19);
        assertTrue(rendered.contains("法院一审公开宣判案件"));assertFalse(rendered.contains("人物履历"));assertFalse(rendered.contains("推荐文章"));
        assertTrue(doc.notice.contains("不是新闻原文"));
    }
    @Test public void mobileSearchFallbackOnlyUsesAnOrganicTextSummary(){
        String html="<div id=results><div tpl=rel_entity><div class=abstract_person>人物履历</div></div><div tpl=vid_recommend><span class=abstract_video>视频摘要</span></div><div tpl=www_index data-tuiguang=1><div class=c-abstract>广告</div></div><div tpl=www_index><div class=c-abstract>这才是第一条自然搜索结果摘要</div></div><div tpl=www_index><div class=c-abstract>后续推荐</div></div></div>";
        Document doc=SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test");
        assertEquals(1,doc.blocks.size());assertEquals("这才是第一条自然搜索结果摘要",doc.blocks.get(0).value);assertTrue(doc.blocks.get(0).inlineLinks.isEmpty());
    }
    @Test public void mobileSearchDoesNotInventABodyFromEntitiesOrChallengePages(){
        for(String html:new String[]{"<title>百度安全验证</title><div>网络不给力，请稍后重试</div>","<div id=results><div tpl=rel_entity><div class=abstract_person>人物履历</div></div></div>"})assertFalse(SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test").hasContent());
    }
    @Test public void desktopLoadedNestedRepliesAreSeparateAndUseStableSubPostIdentity(){
        String html="<div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_1>主帖</div><ul><li class=lzl_single_post data-field='{\"spid\":101}'><a class=j_user_card>甲</a><span class=lzl_content_main>楼中楼内容</span></li><li class=lzl_single_post data-field='{\"spid\":102}'><a class=j_user_card>乙</a><span class=lzl_content_main>楼中楼内容</span></li></ul></div></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");
        assertEquals(3,doc.sections.size());assertEquals("正文 · 楼主",doc.sections.get(0).label);assertEquals("回复 · 甲",doc.sections.get(1).label);assertEquals("回复 · 乙",doc.sections.get(2).label);
        assertNotEquals(doc.sections.get(1).id,doc.sections.get(2).id);assertTrue(doc.sections.get(1).sourcePostId.isEmpty());assertEquals("楼主",doc.byline);
        Document shifted=SourceParser.article(Source.TIEBA,html.replace("楼中楼内容","修改后的回复"),"https://tieba.baidu.com/p/123?pn=2");assertEquals(doc.sections.get(1).id,shifted.sections.get(1).id);
    }
    @Test public void missingParentAuthorDoesNotBorrowNestedAuthorOrQuotedName(){
        String html="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖<a class=head-name>正文引用的人名</a></div><div class=pb-lzl-item><a class=head-name>子回复作者</a><div class=comment-content><div class=pb-rich-text>子回复</div></div></div></div></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");assertEquals("正文",doc.sections.get(0).label);assertTrue(doc.byline.isEmpty());
    }
    @Test public void mixedKnownStructuresAndNestedContentDoNotDropOrDuplicateReplies(){
        String html="<div class=pc-pb-box><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_1>主帖<li class=lzl_single_post data-field='{\"spid\":101}'><a class=j_user_card>甲</a><span class=lzl_content_main>子回复内容</span></li></div></div><div class=pc-pb-reply-list><div class=pb-comment-item><a class=head-name>乙</a><div class=comment-content><div class=pb-rich-text>现代结构回复</div></div></div></div></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");assertEquals(3,doc.sections.size());assertFalse(doc.sections.get(0).blocks.stream().anyMatch(b->b.value.contains("子回复内容")));assertTrue(doc.sections.get(2).label.contains("乙"));
    }
    @Test public void searchShowsOnlyTopicIntroductionWithoutARecommendedLinkDirectory(){
        String html="<title>话题_百度搜索</title><div id=content_left><div><div class=rel-baikan-content><p>来源介绍</p><button>收听</button></div></div><div><h3><a href='https://www.cls.cn/detail/12'>结果标题</a></h3><div class=c-abstract>只是摘要</div></div></div><aside>侧栏推荐</aside>";
        Document d=SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test");
        assertEquals("话题",d.title);assertEquals(1,d.sections.size());assertTrue(d.notice.contains("不是新闻原文"));
        String rendered=ReaderHtml.render(d,Source.BAIDU,19);
        assertTrue(rendered.contains("来源介绍"));assertFalse(rendered.contains("cls.cn/detail/12"));assertFalse(rendered.contains("只是摘要"));assertFalse(rendered.contains("侧栏推荐"));
    }
    @Test public void searchWithoutIntroductionHasOnePlainSummaryAndSkipsAds(){
        String html="<div id=content_left><div class=ec-tuiguang><div class=c-abstract>推广</div></div><div><h3><a href='https://example.org/1'>入口</a></h3><div class=c-abstract>第一条摘要</div></div><div><div class=c-abstract>第二条摘要</div></div></div>";
        Document d=SourceParser.article(Source.BAIDU,html,"https://www.baidu.com/s?wd=test");
        assertEquals(1,d.sections.size());assertEquals("第一条摘要",d.blocks.get(0).value);assertTrue(d.blocks.get(0).inlineLinks.isEmpty());
    }
    @Test public void oldPersistedSearchDirectoriesAreCleanedOnRestore()throws Exception{
        Document old=new Document();old.url="https://www.baidu.com/s?wd=test";
        Section intro=new Section("search-intro","话题介绍",false);intro.blocks.add(new Block("text","介绍"));old.sections.add(intro);
        Section result=new Section("search-123","搜索结果",false);result.blocks.add(new Block("text","推荐新闻"));old.sections.add(result);old.blocks.addAll(result.blocks);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(old));
        assertEquals(1,restored.sections.size());assertEquals("介绍",restored.blocks.get(0).value);
    }
    @Test public void topicPostsAreLinksAndNeverMasqueradeAsComments(){
        String html="<div class=topic-wrapper><h2 class=topic-title>话题</h2><div class=topic-desc>话题介绍</div><ul class=thread-list><li><a class=title href='/p/123'>帖子标题</a><div class=content>帖子摘要</div></li><li><a class=title href='/p/123'>重复帖子</a></li><li><a class=title href='javascript:alert(1)'>不可信入口</a></li></ul></div><div class=recommend>无关推荐</div>";
        Document d=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=1");
        assertEquals("话题",d.title);assertEquals(2,d.sections.size());assertTrue(d.notice.contains("不是完整帖子"));
        assertEquals("https://tieba.baidu.com/p/123",d.sections.get(1).blocks.get(0).inlineLinks.get(0).url);
        assertFalse(d.sections.stream().anyMatch(ReaderHtml::discussion));
    }
    @Test public void forumPostSeparatesRepliesPreservesImagesAndNextPage(){
        String html="<h1>帖子</h1><div id=j_p_postlist><div class=l_post><a class=d_name>作者</a><div class=d_post_content id=post_content_1><p>主帖</p><img src='/image.png'><div class=ad>广告文字</div><video src='/clip.mp4'></video></div></div><div class=l_post><a class=d_name>读者</a><div class=d_post_content id=post_content_2>回复文字</div></div><div class=l_pager><a href='/p/123?pn=2'>下一页</a></div></div><aside>无关内容</aside>";
        Document d=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");
        assertEquals(2,d.sections.size());assertTrue(d.sections.get(0).label.startsWith("正文"));assertTrue(d.sections.get(1).label.startsWith("回复"));
        assertTrue(d.unsupportedVideo);assertFalse(d.filteredVideo);assertEquals("https://tieba.baidu.com/p/123?pn=2",d.nextUrl);
        assertTrue(d.containsImage("https://tieba.baidu.com/image.png"));assertFalse(d.blocks.stream().anyMatch(b->b.value.contains("广告文字")||b.value.contains("无关内容")));
    }
    @Test public void unknownOrLoginPagesNeverFallBackToWholeBody(){
        for(Source s:new Source[]{Source.BAIDU,Source.TIEBA}){
            Document d=SourceParser.article(s,"<body><h1>登录</h1><p>手机号，验证码</p><article>猜你喜欢</article></body>",s.login);
            assertFalse(d.hasContent());assertFalse(d.notice.isEmpty());
        }
    }
    @Test public void currentForumDomKeepsWholeBodyAndOneSectionPerReply(){
        String html="<div class=pc-pb-box><div class=image-text><a class=head-name>楼主</a><span class=pb-title>标题</span><div class=pb-content-wrap><div class=richtext-item>主帖第一段</div><div class=richtext-item>第二段<img src='https://example.com/post.jpg'></div></div></div><div class=pc-pb-reply-list><div class=pb-comment-list><a class=head-name>读者</a><div class=comment-content><div class=pb-rich-text><div class=pb-content-item>一条回复第一段</div><div class=pb-content-item>第二段<span class=pb-emot><img class=emoji-img src='https://example.com/emote.png' alt='微笑'></span></div></div><div class=lzl-wrapper><div class=pb-lzl-item><a class=head-name>子回复作者</a><div class=comment-content><div class=pb-rich-text>子回复内容</div></div></div></div></div></div></div></div>";
        Document d=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");
        assertEquals(3,d.sections.size());assertEquals("正文 · 楼主",d.sections.get(0).label);assertEquals("回复 · 读者",d.sections.get(1).label);assertEquals("回复 · 子回复作者",d.sections.get(2).label);
        assertEquals("tieba-post-"+app.quietreader.discovery.HotCandidate.hash("lzl-parent:"+d.sections.get(1).id+"|子回复作者|page:1|子回复内容[]|0").substring(0,24),d.sections.get(2).id);
        assertEquals(3,d.sections.get(0).blocks.size());assertTrue(d.containsImage("https://example.com/emote.png"));
        assertTrue(d.sections.get(1).blocks.stream().anyMatch(b->b.value.contains("微笑")));
        assertTrue(d.sections.get(1).blocks.stream().anyMatch(b->b.inlineImages.stream().anyMatch(i->i.url.equals("https://example.com/emote.png")&&i.alt.equals("微笑"))));
        org.jsoup.nodes.Document rendered=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.TIEBA,19));
        assertEquals(1,rendered.select(".discussion p img.emoji[src='https://example.com/emote.png']").size());
        assertEquals(0,rendered.select("figure img[src='https://example.com/emote.png']").size());
        java.util.Set<String> expanded=new java.util.HashSet<>();ReaderHtml.expandPrimary(d,expanded);assertTrue(expanded.contains(d.sections.get(0).id));
    }
    @Test public void distinctPeopleWithSameReplyAreNotCollapsedOrMisattributed(){
        String html="<div class=pc-pb-box><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item><a class=head-name>甲</a><div class=comment-content><div class=pb-rich-text>相同回复</div></div></div><div class=pb-comment-item><a class=head-name>乙</a><div class=comment-content><div class=pb-rich-text>相同回复</div></div></div></div></div></div>";
        Document d=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");
        assertEquals(2,d.sections.size());assertEquals("回复 · 甲",d.sections.get(0).label);assertEquals("回复 · 乙",d.sections.get(1).label);assertNotEquals(d.sections.get(0).id,d.sections.get(1).id);
    }
    @Test public void emptyFirstFloorNeverPromotesLaterReplyOrChangesMainAuthor(){
        for(String firstBody:new String[]{"", "<video src='/clip.mp4'></video>"}){
            String html="<h1>帖子</h1><div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_1>"+firstBody+"</div></div><div class=l_post><a class=d_name>读者</a><div class=d_post_content id=post_content_2>后续文字回复</div></div></div>";
            Document doc=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");
            assertEquals("楼主",doc.byline);assertEquals(1,doc.sections.size());
            assertEquals("回复 · 读者",doc.sections.get(0).label);
            assertTrue(ReaderHtml.discussion(doc.sections.get(0)));
            assertEquals(!firstBody.isEmpty(),doc.unsupportedVideo);
        }
    }
    @Test public void emptyIntermediateFloorDoesNotConsumeVisibleReplyBudget(){
        StringBuilder html=new StringBuilder("<div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_1></div></div>");
        for(int n=0;n<105;n++)html.append("<div class=l_post><div class=d_post_content id='empty_").append(n).append("'></div></div>");
        html.append("<div class=l_post><a class=d_name>读者</a><div class=d_post_content id=post_content_2>文字回复</div></div></div>");
        Document doc=SourceParser.article(Source.TIEBA,html.toString(),"https://tieba.baidu.com/p/123");
        assertEquals(1,doc.sections.size());assertEquals("回复 · 读者",doc.sections.get(0).label);
    }
}
