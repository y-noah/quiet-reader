package app.quietreader;

import org.jsoup.Jsoup;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class MultipleReaderMediaTest {
    private static final String PAGE="https://sspai.com/post/123";
    private Document parse(String body){return SourceParser.article(Source.SSPAI,"<h1>附件归属</h1><div class=ss__article__body><p>原文说明</p>"+body+"</div>",PAGE);}
    private String video(int n){return "<video src='https://cdn.sspai.com/"+n+".mp4' poster='https://cdn.sspai.com/"+n+".jpg'></video>";}
    @Test public void eachObservedAttachmentKeepsItsOwnPosterAndAction(){
        Document d=parse(video(1)+video(2));assertEquals(2,d.videos.size());
        org.jsoup.nodes.Document html=Jsoup.parse(ReaderHtml.render(d,Source.SSPAI,19));
        assertEquals(2,html.select(".inline-media").size());
        for(int n=0;n<2;n++){org.jsoup.nodes.Element card=html.select(".inline-media").get(n);assertEquals("https://cdn.sspai.com/"+(n+1)+".jpg",card.selectFirst("video").attr("poster"));assertEquals(ReaderHtml.ACTION+"play?index="+n+"&anchor="+card.id(),card.selectFirst(".media-play").attr("href"));assertTrue(d.containsImage(d.videos.get(n).poster));assertFalse(card.selectFirst("video").hasAttr("src"));}
        assertTrue(html.select("iframe,script,.media-fallback").isEmpty());assertFalse(html.html().contains(".mp4"));
    }
    @Test public void unplayableAttachmentDoesNotBorrowTheNextFileOrPoster(){
        Document d=parse("<video src='https://other.example/1.mp4' poster='https://cdn.sspai.com/1.jpg'></video>"+video(2));
        assertEquals("",d.videos.get(0).url);assertEquals("https://cdn.sspai.com/2.mp4",d.videos.get(1).url);
        org.jsoup.nodes.Document html=Jsoup.parse(ReaderHtml.render(d,Source.SSPAI,19));assertTrue(html.select(".inline-media").get(0).select(".media-play,video").isEmpty());assertTrue(html.selectFirst(".media-play").attr("href").startsWith(ReaderHtml.ACTION+"play?index=1&anchor="));
    }
    @Test public void repeatedSnapshotsAndExcludedPlayersDoNotCreateExtraCards(){
        Document d=parse(video(1)+video(1)+"<aside>"+video(2)+"</aside><blockquote>"+video(3)+"</blockquote>");assertEquals(1,d.videos.size());assertEquals("https://cdn.sspai.com/1.mp4",d.videos.get(0).url);
        ReaderMedia.capture(d,Jsoup.parse(video(1)));assertEquals(1,d.videos.size());
    }
    @Test public void persistenceRevalidatesEveryMediaAndCapsUntrustedArrays()throws Exception{
        Document d=parse(video(1)+video(2));org.json.JSONObject snapshot=ReadingHistory.encode(d);Document restored=ReadingHistory.decode(snapshot);assertEquals(2,restored.videos.size());assertEquals(d.videos.get(1).url,restored.videos.get(1).url);
        snapshot.getJSONArray("videos").getJSONObject(1).put("url","https://evil.example/x.mp4").put("poster","https://evil.example/x.jpg");restored=ReadingHistory.decode(snapshot);assertEquals("",restored.videos.get(1).url);assertEquals("",restored.videos.get(1).poster);
        org.json.JSONArray excessive=new org.json.JSONArray();for(int n=0;n<100;n++)excessive.put(new org.json.JSONObject().put("url","https://cdn.sspai.com/"+n+".mp4"));snapshot.put("videos",excessive);assertEquals(ReaderMedia.MAX_ATTACHMENTS,ReadingHistory.decode(snapshot).videos.size());
        snapshot.remove("videos");assertEquals(d.videoUrl,ReaderMedia.attachments(ReadingHistory.decode(snapshot)).get(0).url);
    }
    @Test public void copiesAndContinuationRetainAllAttachmentsWithoutDuplicates(){
        Document d=parse(video(1)+video(2));Document copy=AnswerStream.copy(d);assertEquals(2,copy.videos.size());copy.videos.clear();assertEquals(2,d.videos.size());
        Document into=new Document();into.url=PAGE;AnswerStream.merge(into,d);AnswerStream.merge(into,d);assertEquals(2,into.videos.size());
        Document legacy=new Document();legacy.url=PAGE;legacy.videoUrl="https://cdn.sspai.com/legacy.mp4";AnswerStream.merge(legacy,d);assertEquals(3,legacy.videos.size());assertEquals("https://cdn.sspai.com/legacy.mp4",legacy.videos.get(0).url);
        Document tail=new Document();tail.url=PAGE;tail.videoUrl="https://cdn.sspai.com/tail.mp4";AnswerStream.merge(legacy,tail);assertEquals(4,legacy.videos.size());
    }
    @Test public void laterAttachmentChangesFingerprintAndCacheAccounting(){
        Document d=parse(video(1)+video(2));String fingerprint=DocumentFingerprint.of(d);int size=DocumentSize.estimate(d);d.videos.remove(1);assertNotEquals(fingerprint,DocumentFingerprint.of(d));assertTrue(DocumentSize.estimate(d)<size);
        assertFalse(DocumentSize.serializationFits(parse(video(1)+video(2)),1));
    }
    @Test public void boundedCaptureHasAnExplicitSourceFallback(){
        StringBuilder body=new StringBuilder();for(int n=0;n<30;n++)body.append(video(n));Document d=parse(body.toString());assertEquals(ReaderMedia.MAX_ATTACHMENTS,d.videos.size());assertTrue(ReaderHtml.render(d,Source.SSPAI,19).contains("其余请在来源页查看"));
    }
}
