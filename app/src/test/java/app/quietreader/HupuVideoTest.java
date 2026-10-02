package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Synthetic DOM reproducing observed Hupu containers; never a live-source certification. */
public class HupuVideoTest {
    private static final String URL="https://bbs.hupu.com/642707483.html";
    private static String page(String main,String replies,String other) {
        return "<html><head><title>合成视频主帖测试</title></head><body><h1>合成视频主帖测试</h1>"
                +"<div class='post-content_main-post-info__fixture'><div class='bbs-thread-comp main-thread'>"
                +main+"</div></div><div class='reply-list-wrapper'>"+replies+"</div>"+other+"</body></html>";
    }
    private static String videoMain(){
        return "<div class='thread-content-detail'><p></p></div><div class='video-shell'><video src='https://example.invalid/synthetic.mp4'></video></div>";
    }
    private static String reply(String text){return "<div class='reply-list-item'><div class='thread-content-detail'><p>"+text+"</p></div></div>";}
    private static Document parse(String main,String replies,String other){return SourceParser.article(Source.HUPU,page(main,replies,other),URL);}

    @Test public void siblingMainVideoFiltersWholeTopicInsteadOfPresentingEmojiReplies() {
        Document d=parse(videoMain(),reply("🐎"),"");
        assertTrue(d.filteredVideo);assertTrue(d.notice.contains("过滤视频主题"));
        assertTrue(d.sections.isEmpty());assertFalse(d.hasContent());
    }

    @Test public void videoOnlyNoticeSurvivesNoReadableTextFallback() {
        Document d=parse(videoMain(),"","");
        assertFalse("An unsupported video must not become invented readable text",d.hasContent());
        assertTrue(d.sections.isEmpty());
        assertTrue("Confirmed unsupported video is presentable without waiting for a login timeout",d.canPresent());
        assertTrue(d.filteredVideo);
        assertTrue("Generic no-content fallback must not erase confirmed media type",d.notice.contains("视频"));
        assertTrue("User now requests filtering, not a source-page playback prompt",d.notice.contains("过滤"));
    }

    @Test public void textMainAndReplyHaveDistinctIdentityAndKeepTheirText() {
        Document d=parse("<div class='thread-content-detail'><p>合成主帖：动作说明</p></div>",reply("合成回复：补充讨论"),"");
        assertEquals(2,d.sections.size());
        assertTrue("Main-post identity must come from the main container",d.sections.get(0).label.startsWith("主帖"));
        assertTrue("Reply identity must come from reply-list-wrapper",d.sections.get(1).label.startsWith("回复"));
        assertEquals("合成主帖：动作说明",d.sections.get(0).blocks.get(0).value);
        assertEquals("合成回复：补充讨论",d.sections.get(1).blocks.get(0).value);
        assertFalse(d.notice.contains("包含视频"));
    }

    @Test public void recommendationAndOutsideVideosDoNotMasqueradeAsMainVideo() {
        String main="<div class='thread-content-detail'><p>合成纯文字主帖</p></div>"
                +"<aside class='recommend'><video src='https://example.invalid/recommend.mp4'></video></aside>";
        Document d=parse(main,reply("合成回复"),"<div class='other-player'><video src='https://example.invalid/other.mp4'></video></div>");
        assertFalse("Recommendation and unrelated players are not this post's media",d.notice.contains("包含视频"));
        assertEquals(2,d.sections.size());
        assertFalse(d.blocks.stream().anyMatch(b->b.value.contains("example.invalid")));
    }

    @Test public void nestedRecommendationPlayerIsNotArticleVideo() {
        Document d=parse("<div class='thread-content-detail'><p>真实文字</p><aside class='recommend'><video src='https://example.invalid/ad.mp4'></video></aside></div>","","");
        assertFalse(d.unsupportedVideo);
        assertFalse(d.notice.contains("包含视频"));
        assertEquals("真实文字",d.sections.get(0).blocks.get(0).value);
    }

    @Test public void emptyUnknownPageStillNeedsSourceAssistance() {
        Document d=parse("<div class='thread-content-detail'></div>","","");
        assertFalse(d.unsupportedVideo);
        assertFalse(d.canPresent());
        assertTrue(d.notice.contains("尚未取得"));
    }
}
