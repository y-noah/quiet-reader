package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Observed mobile search route; synthetic cards exercise ownership boundaries. */
public class MobileWeiboVideoTest {
    private static final String SEARCH="https://m.weibo.cn/search?containerid=synthetic";
    private static String card(String id,String text,String media){
        return "<div class='card' id='"+id+"'><div class='weibo-text'>"+text+"</div>"+media+"</div>";
    }
    private static String mobileCard(String id,String text,String media){
        return "<div class='card-wrap' id='"+id+"'><div class='card-main'><article class='weibo-main'>"
                +"<div class='weibo-og'><div class='weibo-text'>"+text+"</div>"
                +"<div><div class='weibo-media f-media'><div class='weibo-media-wraps'>"+media
                +"</div></div></div></div></article></div></div>";
    }
    private static final String POSTER="<div class='card-video type-video vertical'>"
            +"<div class='mwb-video mwbv-play mwbv-info'><div class='m-img-box'><img src='https://example.com/poster.jpg'></div>"
            +"<button class='mwbv-play-button'></button><div class='mwbv-info-bar'>1:01</div></div></div>";
    @Test public void observedPosterWithoutVideoTagIsFilteredWithItsCaption(){
        Document d=SourceParser.article(Source.WEIBO,mobileCard("video","不可显示的视频配文",POSTER)
                +mobileCard("photo","正常图文讨论","<div class='m-img-box'><img src='https://example.com/photo.jpg'></div>"),SEARCH);
        assertFalse(d.filteredVideo);assertEquals(1,d.filteredVideos);assertEquals(1,d.sections.size());
        assertEquals("photo",d.sections.get(0).id);assertEquals("正常图文讨论",d.blocks.get(0).value);
        assertFalse(d.blocks.stream().anyMatch(b->b.value.contains("不可显示")));
    }
    @Test public void observedMobileSinglePosterPostIsExcluded(){
        Document d=SourceParser.article(Source.WEIBO,mobileCard("video","视频配文",POSTER),"https://m.weibo.cn/status/Abc123");
        assertTrue(d.filteredVideo);assertFalse(d.hasContent());
    }
    @Test public void posterRecommendationsAndQuotedMediaDoNotDeleteOwnText(){
        Document d=SourceParser.article(Source.WEIBO,mobileCard("text","正常文字",
                "<aside class='recommend'>"+POSTER+"</aside><blockquote>"+POSTER+"</blockquote>"),SEARCH);
        assertEquals(0,d.filteredVideos);assertTrue(d.hasContent());
    }
    @Test public void videoWordsOrOrdinaryPhotoClassDoNotCountAsPlayers(){
        Document d=SourceParser.article(Source.WEIBO,mobileCard("text","视频行业和播放按钮的设计讨论",
                "<div class='card-video-analysis'><img src='https://example.com/photo.jpg'></div>"),SEARCH);
        assertEquals(0,d.filteredVideos);assertTrue(d.hasContent());
    }
    @Test public void mobileMixedTopicKeepsTextAndDropsVideoCaption(){
        Document d=SourceParser.article(Source.WEIBO,card("v","视频配文","<video></video>")
                +card("text","视频行业的文字讨论",""),SEARCH);
        assertFalse(d.filteredVideo);assertEquals(1,d.filteredVideos);assertEquals(1,d.sections.size());
        assertEquals("text",d.sections.get(0).id);assertEquals("视频行业的文字讨论",d.blocks.get(0).value);
    }
    @Test public void allVideoSearchDoesNotPersistWholeTopicAsVideo(){
        Document d=SourceParser.article(Source.WEIBO,card("v","视频配文","<video></video>"),SEARCH);
        assertFalse(d.filteredVideo);assertEquals(1,d.filteredVideos);assertFalse(d.hasContent());
    }
    @Test public void singleVideoPostIsStillExcluded(){
        Document d=SourceParser.article(Source.WEIBO,card("v","视频配文","<video></video>"),"https://m.weibo.cn/detail/123");
        assertTrue(d.filteredVideo);assertFalse(d.hasContent());
    }
    @Test public void searchPagePreviewMetadataDoesNotOverrideCardOwnership(){
        Document d=SourceParser.article(Source.WEIBO,"<head><meta property='og:type' content='video'></head>"
                +card("text","保留文字微博",""),SEARCH);
        assertFalse(d.filteredVideo);assertTrue(d.hasContent());
    }
    @Test public void recommendationsAndQuotesDoNotOwnTheirParentPost(){
        Document d=SourceParser.article(Source.WEIBO,card("text","正常文字微博",
                "<aside class='recommend'><video></video></aside><blockquote><video></video></blockquote>"),SEARCH);
        assertFalse(d.filteredVideo);assertEquals(0,d.filteredVideos);assertTrue(d.hasContent());
    }
    @Test public void searchIdentityUsesHostAndPathNotQuerySubstring(){
        assertTrue(VideoPolicy.weiboTopic(SEARCH));
        assertTrue(VideoPolicy.weiboTopic("https://s.weibo.com/weibo?q=a"));
        assertTrue(VideoPolicy.weiboTopic("https://m.weibo.cn/search/?q=a"));
        assertFalse(VideoPolicy.weiboTopic("https://m.weibo.cn/detail/123?next=s.weibo.com/weibo"));
        assertFalse(VideoPolicy.weiboTopic("https://m.weibo.cn/searching?q=a"));
        assertFalse(VideoPolicy.weiboTopic("https://example.com/search?q=a"));
        assertFalse(VideoPolicy.weiboTopic("http://m.weibo.cn/search?q=a"));
    }
}
