package app.quietreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class WeiboContinuationTest {
    private static final String SEARCH="https://m.weibo.cn/search?containerid=synthetic";
    private static final String POST="https://m.weibo.cn/status/Ab123";
    private static String card(String text,String link){return "<div class='card-wrap'><div class='weibo-text'>"+text+link+"</div></div>";}
    private static String link(String url){return "<a href='"+url+"'>全文</a>";}
    private static Document parse(String html){return SourceParser.article(Source.WEIBO,html,SEARCH);}
    @Test public void retainsObservedFulltextDestinationWithoutTurningItIntoBodyText(){
        Document d=parse(card("原帖摘要…",link(POST)));
        assertEquals(1,d.sections.size());assertEquals(POST,d.sections.get(0).continuationUrl);
        assertEquals("weibo-Ab123",d.sections.get(0).id);assertEquals("原帖摘要…",d.blocks.get(0).value);
        assertTrue(d.notice.contains("摘要"));
        assertTrue(ReaderHtml.render(d,Source.WEIBO,19).contains("继续读取原帖"));
    }
    @Test public void fulltextEntryBelongsOnlyToItsExpandedSection(){
        Document d=parse(card("第一个摘要",link(POST))+card("第二个摘要",link("https://m.weibo.cn/status/Cd456")));
        String folded=ReaderHtml.render(d,Source.WEIBO,19,true,Collections.emptySet(),false);
        assertTrue(Jsoup.parse(folded).select("a[href^='https://quiet-reader.invalid/post?index=']").isEmpty());
        org.jsoup.nodes.Document open=Jsoup.parse(ReaderHtml.render(d,Source.WEIBO,19,true,Collections.singleton("weibo-Ab123"),false));
        assertEquals(1,open.select("a[href='https://quiet-reader.invalid/post?index=0']").size());
        assertTrue(open.select("a[href='https://quiet-reader.invalid/post?index=1']").isEmpty());
        assertFalse(open.html().contains("<script"));
    }
    @Test public void relativeEntryUsesTheActualMobileDocumentBase(){
        Document d=parse(card("原帖摘要",link("/status/Ab123")));
        assertEquals(POST,d.sections.get(0).continuationUrl);
    }
    @Test public void identicalTeasersWithDifferentPostDestinationsDoNotCollapseTogether(){
        Document d=parse(card("相同摘要",link(POST))+card("相同摘要",link("https://m.weibo.cn/status/Cd456")));
        assertEquals(2,d.sections.size());assertNotEquals(d.sections.get(0).id,d.sections.get(1).id);
    }
    @Test public void exactRepeatedPostAndSnippetIsStillDeduplicated(){
        Document d=parse(card("相同摘要",link(POST))+card("相同摘要",link(POST)));
        assertEquals(1,d.sections.size());
    }
    @Test public void videosCannotGetAContinuationCard(){
        Document d=parse("<div class='card-wrap'><div class='weibo-text'>视频配文"+link(POST)
                +"</div><div class='card-video type-video'><div class='mwb-video'></div></div></div>");
        assertFalse(d.filteredVideo);assertEquals(1,d.filteredVideos);assertTrue(d.sections.isEmpty());
        assertFalse(ReaderHtml.render(d,Source.WEIBO,19).contains("继续读取原帖"));
    }
    @Test public void unrelatedExternalOrUnsafeFulltextLinksAreNotFollowed(){
        for(String url:new String[]{"https://example.com/status/Ab123","https://m.weibo.cn.evil.example/status/Ab123",
                "https://user@m.weibo.cn/status/Ab123","https://m.weibo.cn:8443/status/Ab123",
                "javascript:alert(1)","intent://status/Ab123","https://m.weibo.cn/status/Ab123/extra",
                "https://m.weibo.cn/login?next=/status/Ab123","https://m.weibo.cn/status/Ab%31"}){
            Document d=parse(card("保留摘要",link(url)));assertEquals(url,"",d.sections.get(0).continuationUrl);
        }
    }
    @Test public void recommendationOrQuotedFulltextIsNotTheCurrentPost(){
        Document d=parse(card("正常正文","<aside class='recommend'>"+link(POST)+"</aside><blockquote>"+link(POST)+"</blockquote>"));
        assertEquals("",d.sections.get(0).continuationUrl);
    }
    @Test public void ambiguousPostLinksDoNotChooseAnArbitraryArticle(){
        Document d=parse(card("正文",link(POST)+link("https://m.weibo.cn/status/Other")));
        assertEquals("",d.sections.get(0).continuationUrl);
    }
    @Test public void wordFulltextWithoutAnEntryAndUnrelatedAnchorRemainOrdinaryText(){
        Document d=parse(card("我在讨论全文阅读功能。", "<a href='"+POST+"'>另一个人的帖子</a>"));
        assertEquals("",d.sections.get(0).continuationUrl);assertTrue(d.blocks.get(0).value.contains("全文阅读"));
    }
    @Test public void onlyKnownTopicPagesCanOfferContinuation(){
        Document d=SourceParser.article(Source.WEIBO,card("单条正文",link(POST)),POST);
        assertEquals("",d.sections.get(0).continuationUrl);
        assertFalse(ReaderHtml.render(d,Source.WEIBO,19).contains("继续读取原帖"));
        Section injected=new Section("a","文字",false);injected.continuationUrl=POST;
        assertFalse(WeiboPost.canContinue(Source.ZHIHU,SEARCH,injected));
        assertFalse(WeiboPost.canContinue(Source.WEIBO,POST,injected));
    }
    @Test public void destinationChangesBreakStabilityAndSurviveMemoryCopy(){
        Document d=parse(card("原帖摘要",link(POST)));String before=DocumentFingerprint.of(d);
        d.sections.get(0).continuationUrl="https://m.weibo.cn/status/Other";
        assertNotEquals(before,DocumentFingerprint.of(d));
        assertEquals(d.sections.get(0).continuationUrl,AnswerStream.copy(d).sections.get(0).continuationUrl);
    }
    @Test public void explicitMobileAliasesCompareOnlyExactIds(){
        assertTrue(WeiboPost.same(POST,"https://m.weibo.cn/detail/Ab123?tracking=1"));
        assertFalse(WeiboPost.same(POST,"https://m.weibo.cn/detail/ab123"));
        assertFalse(WeiboPost.same(POST,"https://m.weibo.cn/detail/123"));
    }
    @Test public void mobileOriginalPhotosFollowItsTextButAvatarsRecommendationsAndQuotesDoNot(){
        String photos="<div class='weibo-media-wraps'><img src='https://example.com/photo.jpg'><img src='https://example.com/photo.jpg'><img data-src='/photo2.jpg'></div>";
        Document d=parse("<div class='card-wrap'><header><img src='https://example.com/avatar.jpg'></header><article class='weibo-main'><div class='weibo-og'>"
                +"<div class='weibo-text'>原帖配文"+link(POST)+"</div><div class='weibo-media'>"+photos+"</div>"
                +"<aside class='recommend'><div class='weibo-media-wraps'><img src='https://example.com/ad.jpg'></div></aside>"
                +"<blockquote><div class='weibo-media-wraps'><img src='https://example.com/quote.jpg'></div></blockquote>"
                +"</div></article></div>");
        assertEquals(3,d.blocks.size());assertEquals("原帖配文",d.blocks.get(0).value);
        assertEquals("https://example.com/photo.jpg",d.blocks.get(1).value);
        assertEquals("https://m.weibo.cn/photo2.jpg",d.blocks.get(2).value);
        assertEquals(3,d.sections.get(0).blocks.size());
    }
    @Test public void videoPosterCannotMasqueradeAsAnExtractedPhoto(){
        Document d=parse("<div class='card-wrap'><div class='weibo-og'><div class='weibo-text'>视频配文</div>"
                +"<div class='weibo-media-wraps'><div class='card-video type-video'><img src='https://example.com/poster.jpg'></div></div></div></div>");
        assertEquals(1,d.filteredVideos);assertTrue(d.blocks.isEmpty());
    }
    private static String photoCard(String text,String image){
        return "<div class='card-wrap'><div class='weibo-og'><div class='weibo-text'>"+text
                +"</div><div class='weibo-media-wraps'><img src='"+image+"'></div></div></div>";
    }
    @Test public void equalCaptionsWithDifferentOriginalPhotosRemainSeparateAndFoldIndependently(){
        Document d=parse(photoCard("同一话题的配文","https://example.com/first.jpg")
                +photoCard("同一话题的配文","https://example.com/second.jpg"));
        assertEquals(2,d.sections.size());assertNotEquals(d.sections.get(0).id,d.sections.get(1).id);
        assertTrue(d.containsImage("https://example.com/first.jpg"));assertTrue(d.containsImage("https://example.com/second.jpg"));
        org.jsoup.nodes.Document opened=Jsoup.parse(ReaderHtml.render(d,Source.WEIBO,19,true,Collections.singleton(d.sections.get(0).id),false));
        assertEquals(1,opened.select("#part-0 figure").size());assertTrue(opened.select("#part-1 figure").isEmpty());
    }
    @Test public void photoOnlyMobilePostsAreNotMergedBecauseTheirTextIsEmpty(){
        Document d=parse(photoCard("","https://example.com/first.jpg")+photoCard("","https://example.com/second.jpg"));
        assertEquals(2,d.sections.size());assertNotEquals(d.sections.get(0).id,d.sections.get(1).id);
    }
    @Test public void repeatedOriginalPhotoAndCaptionStillDeduplicate(){
        String post=photoCard("配文","https://example.com/first.jpg");
        assertEquals(1,parse(post+post).sections.size());
    }
}
