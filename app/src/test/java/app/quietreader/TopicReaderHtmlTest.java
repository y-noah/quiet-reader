package app.quietreader;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class TopicReaderHtmlTest {
    @Test public void openPostUsesTheSameStickyCollapseControlAsAnswers(){
        org.jsoup.nodes.Document open=page(thread(A,"首帖","评论"),Set.of());Element control=open.selectFirst(".topic-post-toggle.section-collapse");
        assertNotNull(control);assertEquals("收起当前内容",control.attr("aria-label"));assertNotNull(control.selectFirst("svg"));assertEquals("",control.text());assertEquals("reading-topic-0",control.attr("aria-controls"));
        assertTrue(open.outerHtml().contains("position:sticky"));
        org.jsoup.nodes.Document closed=page(thread(A,"首帖","评论"),Set.of("topic-collapsed:"+A));assertNull(closed.selectFirst(".topic-post-toggle.section-collapse"));assertEquals("展开阅读 ↓",closed.selectFirst(".topic-post-toggle").text());
    }
    private static final String ROOT="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123",A="https://tieba.baidu.com/p/456",B="https://tieba.baidu.com/p/789";
    private final Item topic=new Item(Source.TIEBA,"美国网友怒喷TheShy操作太混",ROOT,"热议");
    private List<Item> choices(){return List.of(new Item(Source.TIEBA,"韩网热议浪漫上单选手",A,"最热",false,"首帖摘要",null),new Item(Source.TIEBA,"另一个独立帖子",B,"最热",false,"另一帖摘要",null));}
    private Document thread(String url,String title,String comment){
        Document doc=new Document();doc.url=url;doc.title=title;doc.byline="帖子作者";
        Section body=new Section("body-"+url.substring(url.lastIndexOf('/')+1),"正文",false);body.blocks.add(new Block("text","这篇帖子自己的正文"));
        Section reply=new Section("reply-"+url.substring(url.lastIndexOf('/')+1),"回复 · 评论者",false);reply.blocks.add(new Block("text",comment));
        doc.sections.add(body);doc.sections.add(reply);doc.blocks.addAll(body.blocks);doc.blocks.addAll(reply.blocks);
        TiebaTopic.attach(ROOT,choices(),doc,"a".repeat(64),123);ArticleVersion.capture(topic,doc);return doc;
    }
    private org.jsoup.nodes.Document page(Document doc,Set<String> expanded){return Jsoup.parse(TopicReaderHtml.decorate(topic,doc,ReaderHtml.render(doc,Source.TIEBA,19,false,expanded,false),expanded));}
    @Test public void topicHeadingDoesNotBecomeTheDefaultThreadTitle(){
        Document doc=thread(A,"韩网热议浪漫上单选手","只属于第一帖的评论");org.jsoup.nodes.Document html=page(doc,Set.of());
        assertEquals(topic.title,html.selectFirst("h1").text());assertEquals(doc.title,html.selectFirst(".topic-answer-title").text());
        assertEquals(1,html.select("article.topic-answer").size());assertEquals(A,html.selectFirst(".topic-post-body").parent().attr("data-post-url"));
        assertTrue(html.text().contains("这篇帖子自己的正文"));assertFalse(html.text().contains("只属于第一帖的评论"));
        assertEquals("false",html.selectFirst(".topic-comments-toggle").attr("aria-expanded"));
    }
    @Test public void commentsAndContinuationRemainInsideTheirOwnAnswer(){
        Document doc=thread(A,"首帖标题","第一帖评论");doc.nextUrl=A+"?pn=2";
        org.jsoup.nodes.Document html=page(doc,Set.of(TopicReaderHtml.commentsKey(doc)));
        Element comments=html.selectFirst(".topic-comments");assertNotNull(comments);assertEquals(A,comments.attr("data-post-url"));assertTrue(comments.text().contains("第一帖评论"));
        assertEquals(A,comments.closest("article.topic-answer").attr("data-post-url"));
        assertNotNull(comments.selectFirst("#reader-continuation"));assertNull(html.getElementById("reading-topic-1"));
    }
    @Test public void selectingAnotherPostCannotReuseThePreviousPostsComments(){
        Document first=thread(A,"第一帖","第一帖私有评论"),second=thread(B,"第二帖","第二帖自己的评论");
        Set<String> firstExpanded=Set.of(TopicReaderHtml.commentsKey(first));
        assertFalse(TopicReaderHtml.commentsVisible(second,firstExpanded));assertFalse(page(second,firstExpanded).text().contains("第一帖私有评论"));
        org.jsoup.nodes.Document html=page(second,Set.of(TopicReaderHtml.commentsKey(second)));
        assertFalse(html.text().contains("第一帖私有评论"));assertTrue(html.selectFirst(".topic-comments").text().contains("第二帖自己的评论"));
        assertEquals(B,html.selectFirst(".topic-post-body").parent().attr("data-post-url"));assertNull(html.getElementById("reading-topic-0"));assertFalse(html.text().contains("正文尚在准备"));
        assertFalse(ArticleVersion.matches(TiebaTopic.select(topic,first.topic,choices().get(0)),second));
    }
    @Test public void collapsingAPostHidesItsBodyAndCommentsAndStopsContinuation(){
        Document doc=thread(A,"首帖","本帖评论");Set<String> state=Set.of(TopicReaderHtml.commentsKey(doc),TopicReaderHtml.collapsedKey(doc));
        org.jsoup.nodes.Document html=page(doc,state);
        assertFalse(TopicReaderHtml.bodyVisible(doc,state));assertFalse(TopicReaderHtml.commentsVisible(doc,state));
        assertNull(html.selectFirst(".topic-post-body"));assertNull(html.selectFirst(".topic-comments"));assertNull(html.getElementById("reader-continuation"));
        assertFalse(html.text().contains("这篇帖子自己的正文"));assertFalse(html.text().contains("本帖评论"));
        assertEquals(1,html.select("article.topic-answer").size());
    }
    @Test public void commentsClosedByDefaultDoNotStartAutomaticPaging(){
        Document doc=thread(A,"首帖","评论");doc.nextUrl=A+"?pn=2";
        assertTrue(ReaderContinuation.automatic(Source.TIEBA,doc));assertFalse(TopicReaderHtml.commentsVisible(doc,Set.of()));
        assertTrue(TopicReaderHtml.commentsVisible(doc,Set.of(TopicReaderHtml.commentsKey(doc))));
    }
    @Test public void historyRoundTripRetainsPostOwnershipAndDoesNotRelabelTheCache()throws Exception{
        Document original=thread(B,"第二帖真实标题","第二帖评论");String fingerprint=DocumentFingerprint.of(original);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(original));
        org.jsoup.nodes.Document html=page(restored,Set.of(TopicReaderHtml.commentsKey(restored)));
        assertEquals(topic.title,html.selectFirst("h1").text());assertEquals("第二帖真实标题",html.select(".topic-answer-title").get(0).text());
        assertEquals(B,html.selectFirst(".topic-comments").attr("data-post-url"));
        assertEquals("第二帖真实标题",restored.title);assertEquals(fingerprint,DocumentFingerprint.of(original));assertTrue(ArticleVersion.matches(topic,restored));
    }
    @Test public void commentPagingKeepsTheSelectedPostRatherThanMergingAnotherThread(){
        Document before=thread(A,"第一帖","第一页回复");before.nextUrl=A+"?pn=2";
        Document next=new Document();next.url=A+"?pn=2";Section reply=new Section("next-reply","回复 · 下一页作者",false);reply.blocks.add(new Block("text","第二页回复"));next.sections.add(reply);next.blocks.addAll(reply.blocks);
        Document merged=DiscussionPages.merge(Source.TIEBA,before,next,next.url);
        org.jsoup.nodes.Document html=page(merged,Set.of(TopicReaderHtml.commentsKey(merged)));
        assertTrue(html.selectFirst(".topic-comments").text().contains("第一页回复"));assertTrue(html.selectFirst(".topic-comments").text().contains("第二页回复"));
        next.url=B+"?pn=2";try{DiscussionPages.merge(Source.TIEBA,before,next,next.url);fail("another thread must be rejected");}catch(IllegalArgumentException expected){}
    }
    @Test public void answerWindowStartsSmallAndKeepsFutureBodiesOffScreen(){
        List<Item> posts=new ArrayList<>();for(int n=0;n<8;n++)posts.add(new Item(Source.TIEBA,"帖子"+n,"https://tieba.baidu.com/p/"+(1000+n),""));
        Document whole=null;for(Item post:posts){Document part=thread(A,post.title,"评论");part.topic=new TiebaTopic.Resolved(ROOT,post.url,"a".repeat(64),123,posts);if(whole==null)whole=part;else TopicBundle.merge(whole,part);}
        assertTrue(TopicBundle.complete(whole));org.jsoup.nodes.Document initial=page(whole,Set.of());
        assertEquals(3,initial.select("article.topic-answer").size());assertNotNull(initial.selectFirst(".topic-batch-more"));assertFalse(initial.text().contains("正文尚在准备"));
        Set<String> state=new HashSet<>();TopicReaderHtml.visible(state,6);org.jsoup.nodes.Document next=page(whole,state);
        assertEquals(6,next.select("article.topic-answer").size());assertNotNull(next.getElementById("reading-topic-0"));assertNull(next.getElementById("reading-topic-6"));
    }
    @Test public void existingParagraphAnchorsAndToggleIndicesRemainStable(){
        Document doc=thread(A,"第一帖","评论正文".repeat(250));Set<String> state=Set.of(TopicReaderHtml.commentsKey(doc));
        Set<String> all=new HashSet<>(state);for(Section section:ReaderHtml.sections(doc))all.add(section.id);org.jsoup.nodes.Document original=Jsoup.parse(ReaderHtml.render(doc,Source.TIEBA,19,false,all,false)),html=page(doc,state);
        assertEquals(original.select("[data-reading-anchor]").eachAttr("id"),html.select("[data-reading-anchor]").eachAttr("id"));
        assertTrue(html.select("a[href*='toggle?index=']").isEmpty());
        assertNotNull(html.getElementById("reading-topic-0"));assertNotNull(html.getElementById("part-1"));
    }
    @Test public void titlesAreTextAndNeverIntroduceSourceMarkup(){
        Document doc=thread(A,"<img src=x onerror=alert(1)>","安全评论");String html=TopicReaderHtml.decorate(new Item(Source.TIEBA,"<script>topic</script>",ROOT,""),doc,ReaderHtml.render(doc,Source.TIEBA,19),Set.of());
        org.jsoup.nodes.Document parsed=Jsoup.parse(html);assertTrue(parsed.select("script,img[src=x]").isEmpty());assertEquals("<script>topic</script>",parsed.selectFirst("h1").text());
    }
    @Test public void normalArticlesAndDirectPostLinksAreNotTurnedIntoTopics(){
        for(Source source:Source.values()){
            Item item=new Item(source,"正常文章",source.login,"");Document doc=new Document();doc.url=item.url;doc.title="真实原文标题";doc.blocks.add(new Block("text","正文"));String html=ReaderHtml.render(doc,source,19);
            if(source==Source.WEIBO&&VideoPolicy.weiboTopic(item.url))continue;
            assertEquals(source.name(),html,TopicReaderHtml.decorate(item,doc,html,Set.of()));assertTrue(TopicReaderHtml.commentsVisible(doc,Set.of()));
        }
    }
    @Test public void weiboTopicKeepsItsTitleAndAllIndependentOriginalPosts(){
        String url="https://s.weibo.com/weibo?q=%23test%23";
        Item item=new Item(Source.WEIBO,"微博热议主题",url,"");
        Document doc=SourceParser.article(Source.WEIBO,"<h1>来源页的标题</h1><div class=card-wrap><div class=info><a class=name>作者甲</a></div><p class=txt>第一条微博自己的内容</p></div><div class=card-wrap><div class=info><a class=name>作者乙</a></div><p class=txt>第二条微博自己的内容</p></div>",url);
        org.jsoup.nodes.Document html=Jsoup.parse(TopicReaderHtml.decorate(item,doc,ReaderHtml.render(doc,Source.WEIBO,19,false,Set.of(),false),Set.of()));
        assertEquals(item.title,html.selectFirst("h1").text());assertEquals(2,html.select(".topic-answer").size());
        assertTrue(html.select(".topic-answer").get(0).text().contains("第一条微博"));assertFalse(html.select(".topic-answer").get(0).text().contains("第二条微博"));
        assertTrue(html.select(".topic-answer").get(1).text().contains("第二条微博"));assertTrue(html.select(".topic-comments").isEmpty());
    }
    @Test public void directWeiboPostAndBaiduSummaryKeepTheirExistingIdentity(){
        for(Source source:List.of(Source.WEIBO,Source.BAIDU,Source.ZHIHU,Source.HUPU,Source.V2EX)){
            String url=source==Source.WEIBO?"https://m.weibo.cn/detail/123456":source==Source.BAIDU?"https://www.baidu.com/s?wd=test":source.login;
            Item item=new Item(source,"输入标题",url,"");Document doc=new Document();doc.url=url;doc.title="原文或摘要标题";doc.blocks.add(new Block("text","内容"));String html=ReaderHtml.render(doc,source,19);
            assertEquals(html,TopicReaderHtml.decorate(item,doc,html,Set.of()));
        }
    }
}
