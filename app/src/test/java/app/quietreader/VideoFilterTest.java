package app.quietreader;

import org.junit.Test;
import org.json.JSONObject;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Synthetic metadata/DOM contracts, not claims of every platform's current markup. */
public class VideoFilterTest {
    private static final String Z="https://www.zhihu.com/question/123";
    private static final String T="https://tieba.baidu.com/p/123";
    private static String answer(String id,String body,String extra){return "<div class='AnswerItem "+extra+"' data-zop='{\"itemId\":\""+id+"\"}'><div class='RichContent-inner'><div class='RichText'>"+body+"</div></div></div>";}
    @Test public void titlesAndThumbnailsAreNotMediaTypes()throws Exception{
        assertFalse(VideoPolicy.metadata(new JSONObject("{\"title\":\"视频行业的文字讨论\",\"thumbnail\":\"x\",\"attachment\":{\"type\":\"video\"}}")));
        assertTrue(VideoPolicy.metadata(new JSONObject("{\"content_type\":\"video\"}")));
        assertTrue(VideoPolicy.metadata(new JSONObject("{\"is_video\":1}")));
        assertFalse(VideoPolicy.metadata(new JSONObject("{\"is_video\":0}")));
    }
    @Test public void videoMetadataRoundTripsAndLegacyItemsRemainReadable()throws Exception{
        Item video=new Item(Source.TIEBA,"视频","https://tieba.baidu.com/p/1","",true);
        assertTrue(Item.from(video.json()).video);
        assertFalse(Item.from(new JSONObject("{\"source\":\"TIEBA\",\"title\":\"旧文字帖\",\"url\":\"https://tieba.baidu.com/p/2\"}")).video);
        assertTrue(VideoPolicy.filter(Collections.singletonList(video)).isEmpty());
    }
    @Test public void filtersZhihuVideoUrlWithoutDeletingVideoDiscussion()throws Exception{
        String raw="{\"data\":[{\"target\":{\"title_area\":{\"text\":\"视频内容\"},\"link\":{\"url\":\"https://www.zhihu.com/zvideo/99\"}}},{\"target\":{\"title_area\":{\"text\":\"如何看待短视频行业\"},\"link\":{\"url\":\""+Z+"\"}}}]}";
        List<Item> list=SourceParser.list(Source.ZHIHU,raw);assertEquals(1,list.size());assertEquals(Z,list.get(0).url);
    }
    @Test public void jsonBoardsFilterKnownTypesAndAllVideoIsValidEmpty()throws Exception{
        assertTrue(SourceParser.list(Source.WEIBO,"{\"data\":{\"realtime\":[{\"word\":\"合成视频\",\"is_video\":1}]}}").isEmpty());
        String raw="{\"data\":{\"bang_topic\":{\"topic_list\":[{\"topic_name\":\"合成视频\",\"topic_url\":\""+T+"\",\"media_type\":\"video\"}]}}}";
        assertTrue(SourceParser.list(Source.TIEBA,raw).isEmpty());
    }
    @Test public void hupuBoardUsesOwnRowPlayerNotOtherRow()throws Exception{
        String html="<ul><li><a class='p-title' href='/1.html'>视频卡片</a><video></video></li><li><a class='p-title' href='/2.html'>视频行业文字讨论</a></li></ul>";
        List<Item> list=SourceParser.list(Source.HUPU,html);assertEquals(1,list.size());assertTrue(list.get(0).url.endsWith("/2.html"));
    }
    @Test public void tiebaMainVideoFiltersRepliesButReplyVideoDoesNotDeleteMain(){
        Document main=SourceParser.article(Source.TIEBA,"<div class='pb-content-wrap'><p>主视频配文</p><video></video></div><div class='comment-content'>文字回复</div>",T);
        assertTrue(main.filteredVideo);assertFalse(main.hasContent());
        Document reply=SourceParser.article(Source.TIEBA,"<div class='pb-content-wrap'><p>纯文字主帖</p></div><div class='comment-content'><div class='pb-rich-text'>视频回复配文</div><video></video></div>",T);
        assertFalse(reply.filteredVideo);assertEquals(1,reply.filteredVideos);assertEquals(1,reply.sections.size());assertEquals("纯文字主帖",reply.blocks.get(0).value);
    }
    @Test public void desktopTiebaUsesExplicitFirstFloorNotFirstVisibleReply(){
        String template="<div class='l_post' data-field='{\"content\":{\"post_no\":%d}}'><div class='d_post_content'>配文</div><video></video></div>";
        assertTrue(SourceParser.article(Source.TIEBA,String.format(template,1),T).filteredVideo);
        Document later=SourceParser.article(Source.TIEBA,String.format(template,20),T+"?pn=2");assertFalse(later.filteredVideo);assertEquals(1,later.filteredVideos);
    }
    @Test public void weiboFeedDropsOnlyVideoCardIncludingCaption(){
        String html="<div class='card-wrap'><p class='txt'>纯文字微博讨论</p></div><div class='card-wrap'><p class='txt'>视频配文不当正文</p><div class='WB_video'><video></video></div></div>";
        Document d=SourceParser.article(Source.WEIBO,html,"https://s.weibo.com/weibo?q=test");assertFalse(d.filteredVideo);assertEquals(1,d.sections.size());assertEquals("纯文字微博讨论",d.blocks.get(0).value);assertEquals(1,d.filteredVideos);
    }
    @Test public void recommendationsAndQuotedVideosDoNotDeleteTextPosts(){
        String html="<div class='card-wrap'><p class='txt'>文字微博</p><aside class='recommend'><video></video></aside><div class='WB_feed_expand'><video></video></div></div>";
        Document d=SourceParser.article(Source.WEIBO,html,"https://s.weibo.com/weibo?q=test");assertEquals(0,d.filteredVideos);assertTrue(d.hasContent());
    }
    @Test public void videoQuestionRetainsTextAnswersAndMore(){
        Document d=SourceParser.article(Source.ZHIHU,"<div class='QuestionRichText'><video></video></div>"+answer("text","<p>文字回答</p>","")+answer("video","<p>视频配文</p><video></video>","VideoAnswer"),Z);
        assertFalse(d.filteredVideo);assertTrue(d.unsupportedVideo);assertEquals(1,d.filteredVideos);assertEquals(1,AnswerStream.answers(d));assertEquals("text",d.sections.get(0).id);
        assertTrue(ReaderHtml.render(d,Source.ZHIHU,19).contains("加载下一批回答"));
    }
    @Test public void entirelyVideoAnswersStillExposeContinuation(){
        Document d=SourceParser.article(Source.ZHIHU,answer("v","<video></video>",""),Z);
        assertEquals(1,d.filteredVideos);assertFalse(d.hasContent());assertTrue(d.canPresent());assertFalse(d.filteredVideo);
        assertTrue(ReaderHtml.render(d,Source.ZHIHU,19).contains("加载下一批回答"));
    }
    @Test public void proseAnswerWithEmbeddedClipRemainsReadable(){
        Document d=SourceParser.article(Source.ZHIHU,answer("text","<p>分析正文第一段</p><div class='VideoCard'><video></video></div><p>分析正文第二段</p>",""),Z);
        assertEquals(0,d.filteredVideos);assertEquals(1,AnswerStream.answers(d));assertTrue(d.unsupportedVideo);
    }
    @Test public void lateVideoClassificationRemovesCachedAnswerAndDoesNotResurrect(){
        Document old=SourceParser.article(Source.ZHIHU,answer("v","<p>先到的配文</p>","")+answer("text","<p>真正文字回答</p>",""),Z);
        Document video=SourceParser.article(Source.ZHIHU,answer("v","<p>先到的配文</p><video></video>","VideoAnswer"),Z);
        Document accumulated=AnswerStream.copy(old);AnswerStream.merge(accumulated,video);assertEquals(1,AnswerStream.answers(accumulated));assertEquals("text",accumulated.sections.get(0).id);
        AnswerStream.merge(accumulated,old);assertEquals(1,AnswerStream.answers(accumulated));assertEquals(1,accumulated.filteredVideos);
    }
    @Test public void differentTopicsDoNotSharePersistedVideoIdentity(){
        assertNotEquals(VideoPolicy.key(new Item(Source.WEIBO,"a","https://s.weibo.com/weibo?q=a","")),VideoPolicy.key(new Item(Source.WEIBO,"b","https://s.weibo.com/weibo?q=b","")));
        assertEquals(VideoPolicy.key(new Item(Source.TIEBA,"a",T,"")),VideoPolicy.key(new Item(Source.TIEBA,"a",T+"?pn=2","")));
    }
    @Test public void ordinaryArticlesWithVideoAttachmentsAreNotVideoFeeds(){
        Document d=SourceParser.article(Source.SMZDM,"<div class='txt-detail'><p>商品优惠说明</p><video></video></div>","https://www.smzdm.com/p/123/");
        assertFalse(d.filteredVideo);assertTrue(d.hasContent());assertTrue(d.unsupportedVideo);
    }
    @Test public void nestedVideoReplyDoesNotRemoveHupuTextMain(){
        String html="<div class='post-content_main-post-info__fixture'><div class='thread-content-detail'>保留文字主帖</div><div class='reply-list-wrapper'><div class='reply-list-item'><div class='thread-content-detail'>删除视频回复</div><video></video></div></div></div>";
        Document d=SourceParser.article(Source.HUPU,html,"https://bbs.hupu.com/123.html");
        assertFalse(d.filteredVideo);assertEquals(1,d.filteredVideos);assertEquals(1,d.sections.size());assertEquals("保留文字主帖",d.blocks.get(0).value);
    }
    @Test public void questionMetadataDoesNotMistakeAttachmentForWholeQuestion()throws Exception{
        assertFalse(VideoPolicy.metadata(new JSONObject("{\"type\":\"question\",\"is_video\":1}")));
    }
}
