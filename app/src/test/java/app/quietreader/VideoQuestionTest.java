package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Synthetic media-only question. Never substitutes for source authentication evidence. */
public final class VideoQuestionTest {
    private Document videoQuestion() {
        return SourceParser.article(Source.ZHIHU,"<h1>合成视频问题</h1><div class='QuestionRichText'><video src='https://example.invalid/video.mp4'></video></div>","https://www.zhihu.com/question/123");
    }
    @Test public void emptyMediaQuestionHasContinuationButNoInventedBodySection() {
        Document d=videoQuestion();assertFalse(d.hasContent());assertTrue(d.unsupportedVideo);
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19));
        assertTrue(page.select("section.part").isEmpty());
        assertEquals(1,page.select("a[href='https://quiet-reader.invalid/more']").size());
        assertTrue(page.select(".notice").text().contains("视频"));
    }
    @Test public void copyingForContinuationKeepsKnownMediaLimit() {
        Document copy=AnswerStream.copy(videoQuestion());
        assertTrue(copy.unsupportedVideo);assertFalse(copy.hasContent());
    }
    @Test public void mergingDoesNotLoseObservedMediaWhenLaterSnapshotsOmitIt() {
        Document accumulated=new Document();
        AnswerStream.merge(accumulated,videoQuestion());
        assertTrue(accumulated.unsupportedVideo);
        Document later=new Document();Section answer=new Section("a","回答",true);answer.blocks.add(new Block("text","稍后加载的真实文字"));later.sections.add(answer);
        AnswerStream.merge(accumulated,later);
        assertTrue(accumulated.unsupportedVideo);assertEquals(1,AnswerStream.answers(accumulated));
        assertEquals("稍后加载的真实文字",accumulated.blocks.get(0).value);
    }
}
