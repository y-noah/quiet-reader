package app.quietreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReaderMediaTest {
    private static final String PAGE="https://sspai.com/post/123",MEDIA="https://cdn.sspai.com/clip.mp4?signature=source%2Fbytes";
    private Document parse(String body){return SourceParser.article(Source.SSPAI,"<h1>图文带附件</h1><div class=ss__article__body>"+body+"</div>",PAGE);}
    @Test public void ownedEmbeddedFileRetainsProseAndDefersAllPlayback(){
        Document doc=parse("<p>保留正文</p><video src='"+MEDIA+"'></video>");assertTrue(doc.unsupportedVideo);assertFalse(doc.videoTopic);assertFalse(BodyVideoEvidence.classify(Source.SSPAI,doc));assertEquals(MEDIA,doc.videoUrl);assertTrue(doc.hasContent());
        String html=ReaderHtml.render(doc,Source.SSPAI,19);assertTrue(html.contains("media-play"));assertTrue(html.contains("href='"+ReaderHtml.ACTION+"play?index=0&amp;anchor="));assertFalse(html.contains(MEDIA));assertFalse(html.contains("<iframe"));assertTrue(html.contains("保留正文"));
        org.jsoup.nodes.Element video=Jsoup.parse(html).selectFirst(".inline-media video");assertNotNull(video);assertEquals("none",video.attr("preload"));assertFalse(video.hasAttr("src"));assertFalse(video.hasAttr("autoplay"));
        assertFalse(video.hasAttr("controls"));assertTrue(html.contains("data-reader-layout='article'"));assertEquals(1,Jsoup.parse(html).select(".inline-media .media-play").size());
    }
    @Test public void playerSourcesMustBeExplicitFilesOnTheirKnownSourceDomains(){
        for(String media:new String[]{"http://cdn.sspai.com/clip.mp4","https://sspai.com.evil.example/x.mp4","https://evil.example/clip.mp4","https://u@sspai.com/x.mp4","https://sspai.com:8443/x.mp4","https://sspai.com/manifest.m3u8","https://sspai.com/api/play","file:///x.mp4","data:video/mp4;base64,x","blob:https://sspai.com/id"})assertFalse(media,ReaderMedia.allowed(PAGE,media));
        assertFalse(ReaderMedia.allowed("https://www.bilibili.com/video/BV123",MEDIA));assertTrue(ReaderMedia.allowed(PAGE,MEDIA));assertTrue(ReaderMedia.allowed(PAGE,"https://cdn.sspai.net/clip.webm"));
    }
    @Test public void recommendationsAndQuotedPlayersCannotBecomeTheCurrentAttachment(){
        Document doc=parse("<p>正文</p><aside><video src='"+MEDIA+"'></video></aside><blockquote><video src='"+MEDIA+"'></video></blockquote><div class=recommend><video src='"+MEDIA+"'></video></div>");assertEquals("",doc.videoUrl);
        doc=parse("<p>正文</p><video><source type='application/x-mpegURL' src='https://sspai.com/invalid.mp4'><source type='video/mp4' src='"+MEDIA+"'></video>");assertEquals(MEDIA,doc.videoUrl);
    }
    @Test public void persistedFilesAreRevalidatedAndOlderSnapshotsRemainReadable()throws Exception{
        Document doc=parse("<p>正文</p><video src='"+MEDIA+"'></video>");org.json.JSONObject encoded=ReadingHistory.encode(doc);
        assertEquals(MEDIA,ReadingHistory.decode(encoded).videoUrl);encoded.put("videoUrl","https://evil.example/clip.mp4");assertEquals("",ReadingHistory.decode(encoded).videoUrl);
        encoded.remove("videoUrl");encoded.remove("videos");assertEquals("",ReadingHistory.decode(encoded).videoUrl);assertTrue(ReadingHistory.decode(encoded).hasContent());
    }
    @Test public void copiesMergesFingerprintsAndBudgetsRetainMediaIdentity(){
        Document doc=parse("<p>正文</p><video src='"+MEDIA+"'></video>");String before=DocumentFingerprint.of(doc);int bytes=DocumentSize.estimate(doc);
        assertEquals(MEDIA,AnswerStream.copy(doc).videoUrl);Document other=new Document();other.url=PAGE;AnswerStream.merge(other,doc);assertEquals(MEDIA,other.videoUrl);
        doc.videoUrl="";assertNotEquals(before,DocumentFingerprint.of(doc));assertTrue(DocumentSize.estimate(doc)<bytes);
    }
    @Test public void unavailableAndUnknownMediaRetainsTheSourceFallback(){
        Document doc=parse("<p>正文</p><video src='https://unadapted.example/clip.mp4'></video>");assertTrue(doc.unsupportedVideo);assertEquals("",doc.videoUrl);
        org.jsoup.nodes.Document html=Jsoup.parse(ReaderHtml.render(doc,Source.SSPAI,19));assertTrue(html.select(".media-play").isEmpty());assertEquals(1,html.select(".inline-media a").size());assertTrue(html.select("video,.media-fallback").isEmpty());
    }
}
