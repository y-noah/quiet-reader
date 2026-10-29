package app.quietreader;

import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class BilibiliArticleTest {
    private static final String URL="https://www.bilibili.com/video/BV1xrnz6gEVD";
    private JSONObject fixture()throws Exception{
        try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/bilibili-video-data-20261024.json")){
            return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
    }
    private Document parse(JSONObject data){return SourceParser.article(Source.BILIBILI,"<script>window.__INITIAL_STATE__="+data+";(function(){throw Error('must never execute')})()</script><aside>不应提取的推荐</aside>",URL);}
    @Test public void navigationMustRemainOnTheRequestedVideo(){
        assertTrue(BilibiliArticle.sameVideo(URL,URL+"/?p=2"));
        assertFalse(BilibiliArticle.sameVideo(URL,"https://www.bilibili.com/video/BVother"));
        assertFalse(BilibiliArticle.sameVideo(URL,"https://www.bilibili.com/"));
        assertFalse(BilibiliArticle.sameVideo(URL,"https://passport.bilibili.com/login"));
        assertFalse(BilibiliArticle.sameVideo(URL,"https://unrelated.example/video/BV1xrnz6gEVD"));
    }
    @Test public void observedVideoMetadataUsesCommonLayoutWithoutRemotePlayer()throws Exception{
        Document d=parse(fixture());assertEquals("影视飓风",d.byline);assertTrue(d.title.contains("iPhone Duo"));
        assertTrue(d.unsupportedVideo);assertFalse(d.filteredVideo);assertTrue(d.hasContent());
        assertTrue(d.videoPoster.startsWith("https://i0.hdslb.com/"));assertTrue(d.containsImage(d.videoPoster));
        java.util.Set<String> expanded=new java.util.HashSet<>();ReaderHtml.expandPrimary(d,expanded);
        String html=ReaderHtml.render(d,Source.BILIBILI,19,false,expanded,false);
        assertTrue(html.contains("data-reader-layout='video'"));assertTrue(html.contains("class='media-poster'"));
        assertTrue(html.contains("相信各位和我们一样"));assertFalse(html.contains("不应提取的推荐"));
        assertFalse(html.contains("<script"));assertFalse(html.contains("<video"));assertFalse(html.contains("<iframe"));
        assertEquals(Source.BILIBILI,ReaderLinks.readerSource(URL));assertTrue(Source.BILIBILI.unifiedReading());
    }
    @Test public void unrelatedUnavailableAndMalformedPagesNeverBecomeReadable()throws Exception{
        JSONObject data=fixture();data.getJSONObject("videoData").put("bvid","BVother");assertFalse(parse(data).canPresent());
        data=fixture();data.getJSONObject("videoData").put("state",-1);Document unavailable=parse(data);
        assertTrue(unavailable.sourceUnavailable);assertFalse(unavailable.hasContent());assertFalse(unavailable.unsupportedVideo);
        for(String raw:new String[]{"<body>登录后查看<article>推荐正文</article></body>","<script>window.__INITIAL_STATE__=broken()</script>"})
            assertFalse(SourceParser.article(Source.BILIBILI,raw,URL).canPresent());
    }
    @Test public void missingCoverAndEmptyDescriptionStillProduceHonestFallback()throws Exception{
        JSONObject data=fixture();data.getJSONObject("videoData").put("pic","https://unrelated.example/cover.jpg").put("desc","");
        Document d=parse(data);assertTrue(d.unsupportedVideo);assertEquals("",d.videoPoster);assertFalse(d.hasContent());
        String html=ReaderHtml.render(d,Source.BILIBILI,19);assertTrue(html.contains("在来源页查看视频"));assertFalse(html.contains("<img"));
    }
    @Test public void metadataIsEscapedAndPosterSurvivesSnapshotWithoutChangingOldData()throws Exception{
        JSONObject data=fixture();data.getJSONObject("videoData").put("desc","<script>evil()</script>");
        Document d=parse(data);Document restored=ReadingHistory.decode(ReadingHistory.encode(d));
        assertEquals(d.videoPoster,restored.videoPoster);assertEquals(DocumentFingerprint.of(d),DocumentFingerprint.of(restored));
        String html=ReaderHtml.render(restored,Source.BILIBILI,19);assertFalse(html.contains("<script>evil"));assertTrue(html.contains("&lt;script&gt;"));
        JSONObject old=ReadingHistory.encode(d);old.remove("videoPoster");assertEquals("",ReadingHistory.decode(old).videoPoster);
        String fingerprint=DocumentFingerprint.of(d);d.videoPoster="";assertNotEquals(fingerprint,DocumentFingerprint.of(d));
    }
}
