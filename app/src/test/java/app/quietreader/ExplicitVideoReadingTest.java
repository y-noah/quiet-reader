package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ExplicitVideoReadingTest {
    private static final String URL="https://bbs.hupu.com/123.html";
    private static String page(String media){return "<h1>已明确打开的视频主题</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail><p>原作者视频说明</p></div>"+media+"</div><div class=reply-list-wrapper><div class=reply-list-item><div class=thread-content-detail>已加载的普通回复</div></div><div class=reply-list-item><div class=thread-content-detail>第二个视频回复不当文字</div><video></video></div></div>";}
    @Test public void explicitOpeningHasSharedFallbackWhileSpeculationStillFilters(){
        String raw=page("<video src='https://v.hupucdn.com/clip.mp4' poster='https://v.hupucdn.com/cover.jpg'></video>");
        Document filtered=SourceParser.article(Source.HUPU,raw,URL);assertTrue(filtered.filteredVideo);assertFalse(filtered.hasContent());
        Document reading=SourceParser.readingArticle(Source.HUPU,raw,URL);assertTrue(reading.videoTopic);assertFalse(reading.filteredVideo);assertTrue(reading.unsupportedVideo);assertEquals(2,reading.sections.size());assertEquals("https://v.hupucdn.com/clip.mp4",reading.videoUrl);assertEquals("https://v.hupucdn.com/cover.jpg",reading.videoPoster);
        assertTrue(reading.blocks.stream().anyMatch(b->b.value.equals("原作者视频说明")));assertFalse(reading.blocks.stream().anyMatch(b->b.value.contains("第二个视频回复")));
        String html=ReaderHtml.render(reading,Source.HUPU,19,false,java.util.Set.of(reading.sections.get(0).id),false);assertTrue(html.contains("data-reader-layout='video'"));assertTrue(html.contains("原作者视频说明"));assertTrue(html.contains("讨论"));assertFalse(html.contains("<video"));
    }
    @Test public void unsupportedVideoStillPresentsWithoutInventingProse(){
        Document d=SourceParser.readingArticle(Source.HUPU,"<h1>视频</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail></div><video src='https://unadapted.example/clip.mp4'></video></div>",URL);
        assertTrue(d.canPresent());assertFalse(d.hasContent());assertTrue(d.unsupportedVideo);assertFalse(d.filteredVideo);assertEquals("",d.videoUrl);
    }
    @Test public void questionVideoAnswerFilteringDoesNotChangeForExplicitReading(){
        String raw="<div class=QuestionRichText>问题说明</div><div class='AnswerItem VideoAnswer'><div class=RichContent-inner><div class=RichText>视频回答配文<video></video></div></div></div>";
        Document d=SourceParser.readingArticle(Source.ZHIHU,raw,"https://www.zhihu.com/question/123");assertFalse(d.videoTopic);assertEquals(1,d.filteredVideos);assertEquals(1,d.sections.size());assertFalse(d.blocks.stream().anyMatch(b->b.value.contains("视频回答配文")));
    }
    @Test public void topicVideoMarkerSurvivesCacheAndNeverCertifiesChineseProse()throws Exception{
        Document d=SourceParser.readingArticle(Source.HUPU,page("<video></video>"),URL);d.blocks.add(new Block("text","中文说明".repeat(100)));
        assertTrue(AnswerStream.copy(d).videoTopic);assertTrue(ReadingHistory.decode(ReadingHistory.encode(d)).videoTopic);Document merged=new Document();AnswerStream.merge(merged,d);assertTrue(merged.videoTopic);
        assertEquals(app.quietreader.discovery.ContentLanguage.Body.UNKNOWN,BodyLanguageEvidence.classify(d));String before=DocumentFingerprint.of(d);d.videoTopic=false;assertNotEquals(before,DocumentFingerprint.of(d));
    }
    @Test public void posterMustBelongToSelectedVideoAndKnownSource(){
        Document d=SourceParser.readingArticle(Source.IFANR,"<meta property='og:type' content=video><article class=c-article-content><video src='https://unadapted.example/first.mp4' poster='https://www.ifanr.com/first.jpg'></video><video src='https://www.ifanr.com/second.mp4' poster='https://unadapted.example/second.jpg'></video></article>","https://www.ifanr.com/123");
        assertEquals("https://www.ifanr.com/second.mp4",d.videoUrl);assertEquals("",d.videoPoster);
    }
    @Test public void multipleWeiboTopicPostsAreStillFilteredIndividually(){
        String raw="<div class=card-wrap><p class=txt>文字微博</p></div><div class=card-wrap><p class=txt>视频配文</p><video></video></div>";
        Document d=SourceParser.readingArticle(Source.WEIBO,raw,"https://s.weibo.com/weibo?q=topic");assertFalse(d.videoTopic);assertFalse(d.filteredVideo);assertEquals(1,d.filteredVideos);assertEquals(1,d.sections.size());
    }
}
