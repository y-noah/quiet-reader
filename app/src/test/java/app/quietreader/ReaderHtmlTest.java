package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
public class ReaderHtmlTest {
    @Test public void shortBodyAndCommentsReadDirectlyWithoutRedundantActions(){
        Document d=new Document();d.title="短正文与评论";
        for(String label:new String[]{"正文","评论 · 甲","回复 · 乙"}){Section part=new Section(label,label,false);part.blocks.add(new Block("text",label+"的完整内容"));d.sections.add(part);}
        for(Source source:Source.values()){
            org.jsoup.nodes.Document html=org.jsoup.Jsoup.parse(ReaderHtml.render(d,source,19));
            assertEquals(3,html.select("section.part p:not(.preview)").size());
            assertEquals(2,html.select("section.discussion").size());
            assertTrue(html.select("a[href*='toggle?'],a[href*='jump?']").isEmpty());
        }
    }
    @Test public void longCommentHasOneToggleAndAnswersKeepDestinationNavigation(){
        Document d=new Document();Section body=new Section("body","正文",false),comment=new Section("comment","评论 · 甲",false);
        body.blocks.add(new Block("text","正文"));comment.blocks.add(new Block("text","长评论内容".repeat(220)));d.sections.add(body);d.sections.add(comment);
        org.jsoup.nodes.Document folded=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.V2EX,19));
        assertEquals(1,folded.select("a[href*='toggle?']").size());assertTrue(folded.select("#part-1 p:not(.preview)").isEmpty());
        assertEquals("2 · 展开阅读 ↓",folded.selectFirst("a[href*='toggle?']").text());assertEquals("false",folded.selectFirst("a[href*='toggle?']").attr("aria-expanded"));assertEquals("甲",folded.selectFirst("#part-1 .section-label").text());
        org.jsoup.nodes.Document open=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.V2EX,19,false,java.util.Set.of("comment"),false));
        assertEquals(1,open.select("#part-1 p:not(.preview)").size());assertEquals(1,open.select("a[href*='toggle?']").size());assertTrue(open.select("a[href*='jump?']").isEmpty());
        assertEquals("2 · 收起 ↑",open.selectFirst("a[href*='toggle?']").text());assertEquals("true",open.selectFirst("a[href*='toggle?']").attr("aria-expanded"));assertEquals("甲",open.selectFirst("#part-1 .section-label").text());
        Section answer=new Section("answer","回答",true);answer.blocks.add(new Block("text","后续回答"));d.sections.add(answer);
        assertEquals(1,org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19,false,java.util.Set.of("comment"),false)).select("a[href*='jump?index=2']").size());
    }
    @Test public void sourceSummariesDoNotAddExplanatoryChromeOrClaimOriginalFullText(){
        for(String label:new String[]{"搜索结果 · 来源摘要","相关帖子 · 摘要"}){
            Document d=new Document();d.title="话题";d.notice="当前仅获取摘要，不是完整原文。";
            Section entry=new Section("entry",label,false);entry.blocks.add(new Block("text","来源内容"));d.sections.add(entry);
            String html=ReaderHtml.render(d,Source.BAIDU,19);
            assertFalse(html.contains("summary-scope"));
            assertFalse(html.contains("话题与来源摘要"));assertFalse(html.contains("原作者内容"));
            assertFalse(html.contains("toggle?index=0"));assertTrue(html.contains("来源内容"));
        }
        Document article=new Document();article.notice="仅展示已加载的回复";article.blocks.add(new Block("text","正常正文"));
        assertFalse(ReaderHtml.render(article,Source.TIEBA,19).contains("阅读提示"));
        assertFalse(ReaderHtml.render(article,Source.TIEBA,19).contains("仅展示已加载的回复"));
    }
    @Test public void unifiedDiscussionSeparatesBodyFromRepliesAcrossSources(){
        Document d=new Document();d.title="正文与讨论";d.url="https://www.v2ex.com/t/1";
        Section body=new Section("body","正文",false),reply=new Section("reply","回复 · 用户",false);
        body.blocks.add(new Block("text","正文内容"));reply.blocks.add(new Block("text","回复内容"));d.sections.add(body);d.sections.add(reply);
        for(Source source:new Source[]{Source.V2EX,Source.HUPU,Source.ZHIHU}){
            String html=ReaderHtml.render(d,source,19,false,java.util.Set.of("body","reply"),false);
            assertTrue(html.contains("data-reader-layout='discussion'"));
            assertEquals(1,html.split("<h2 class='discussion-heading'>",-1).length-1);
            assertTrue(html.indexOf("正文内容")<html.indexOf("discussion-heading'>讨论"));
            assertTrue(html.contains("class='part discussion' id='part-1'"));
        }
    }
    @Test public void videoFallbackKeepsTextAndNeverEagerlyEmbedsMedia(){
        Document d=new Document();d.title="带视频的图文";d.url="https://www.cls.cn/detail/1";d.unsupportedVideo=true;d.blocks.add(new Block("text","保留已获取的正文"));
        String html=ReaderHtml.render(d,Source.CLS,19);
        assertTrue(html.contains("data-reader-layout='video'"));assertTrue(html.contains("保留已获取的正文"));
        assertTrue(html.contains("class='media-fallback'"));assertTrue(html.contains(ReaderHtml.ACTION+"login"));
        assertFalse(html.contains("<video"));assertFalse(html.contains("<iframe"));assertFalse(html.contains("<script"));
    }
    @Test public void imageCaptionIsAnIndependentRetryEntryWithoutPageScripts(){
        Document d=new Document();d.title="Image";d.url="https://www.cls.cn/detail/1";
        String image="https://example.com/photo.png?a=1&b=2";d.blocks.add(new Block("image",image));
        for(boolean dark:new boolean[]{false,true}){
            String html=ReaderHtml.render(d,Source.CLS,19,dark,java.util.Collections.emptySet(),false);
            assertTrue(html.contains("<figcaption><a class='image-open' href='https://example.com/photo.png?a=1&amp;b=2'"));
            assertTrue(html.contains("aria-label='查看正文图片大图，加载失败可在大图中重试'>查看大图</a>"));
            assertFalse(html.contains("图片可能稍后显示"));
            assertFalse(html.contains("轻点图片放大"));assertFalse(html.contains("<script"));assertFalse(html.contains("onload="));
            assertTrue(html.contains("default-src 'none'"));
        }
    }
    @Test public void shortImageCollectionsFoldAcrossPlatforms(){
        Document d=new Document();d.title="Pictures";d.url="https://bbs.hupu.com/1.html";
        d.blocks.add(new Block("text","Short caption"));for(int i=0;i<12;i++)d.blocks.add(new Block("image","https://example.com/"+i+".png"));
        for(Source source:Source.values()){String html=ReaderHtml.render(d,source,19);assertTrue(html.contains("展开阅读"));assertTrue(html.contains("包含 12 张图片"));assertFalse(html.contains("<img "));}
    }
    @Test public void readingAnchorsSurviveGrowthOfEarlierSection(){
        Document d=new Document();d.url="https://www.zhihu.com/question/1";
        Section first=new Section("first","One",true),second=new Section("second","Two",true);first.blocks.add(new Block("text","a"));second.blocks.add(new Block("text","b"));d.sections.add(first);d.sections.add(second);
        java.util.Set<String> expanded=new java.util.HashSet<>(java.util.Arrays.asList("first","second"));
        String anchor="id='"+ReaderHtml.anchorId("second",0)+"'";assertTrue(ReaderHtml.render(d,Source.ZHIHU,19,false,expanded,false).contains(anchor));
        first.blocks.add(new Block("text","new"));assertTrue(ReaderHtml.render(d,Source.ZHIHU,19,false,expanded,false).contains(anchor));
    }
    @Test public void remoteMarkupIsEscapedNotExecuted() {
        Document d=new Document();d.title="<script>evil()</script>";d.url="https://www.ifanr.com/1";
        d.blocks.add(new Block("text","<img src=x onerror=evil()>"));d.blocks.add(new Block("image","javascript:evil()"));
        String html=ReaderHtml.render(d,Source.IFANR,19);
        assertFalse(html.contains("<script>"));assertFalse(html.contains("src='javascript:"));assertTrue(html.contains("&lt;img"));assertTrue(html.contains("default-src 'none'"));
    }
    @Test public void preservesPartialContentWarning() {
        Document d=new Document();d.notice="部分内容";d.title="Test";d.url="https://www.zhihu.com/question/1";
        assertTrue(ReaderHtml.render(d,Source.ZHIHU,100).contains("部分内容"));
        assertFalse(ReaderHtml.render(d,Source.ZHIHU,100).contains("font-size:100px"));
    }
}
