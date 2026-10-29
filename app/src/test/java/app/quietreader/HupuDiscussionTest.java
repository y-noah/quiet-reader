package app.quietreader;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class HupuDiscussionTest {
    private static final String URL="https://bbs.hupu.com/123.html";
    private JSONObject reply(String id,String name,String text)throws Exception{return new JSONObject().put("pid",id).put("author",new JSONObject().put("puname",name)).put("content","<p>"+text+"</p>");}
    private JSONObject state(int page)throws Exception{
        JSONObject thread=new JSONObject().put("tid","123").put("url","/123.html").put("title","真实身份的测试主帖").put("content","<p>主帖正文</p>").put("hasVideo",false).put("status",0).put("visibleRange","ALL_SEE").put("author",new JSONObject().put("puname","楼主"));
        JSONObject detail=new JSONObject().put("thread",thread).put("visibleRange","ALL_SEE").put("lights",page==1?new JSONArray().put(reply("10","甲","热门回复")):new JSONArray()).put("replies",new JSONObject().put("current",page).put("total",3).put("list",new JSONArray().put(reply(page==1?"10":"11",page==1?"甲":"乙",page==1?"热门回复":"后续回复"))));
        return new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("detail",detail)));
    }
    private JSONObject detail(JSONObject state)throws Exception{return state.getJSONObject("props").getJSONObject("pageProps").getJSONObject("detail");}
    private String html(JSONObject state){return "<h1>真实身份的测试主帖</h1><script id='__NEXT_DATA__' type='application/json'>"+state+"</script><ul class='hupu-rc-pagination'><li><a href='/123.html'>1</a></li><li><a href='/123-2.html'>2</a></li><li><a href='/123-3.html'>3</a></li></ul><aside><div class='thread-content-detail'>推荐污染</div></aside>";}
    private void stampAll(JSONObject value,Object stamp)throws Exception{
        JSONObject d=detail(value);
        d.getJSONArray("lights").getJSONObject(0).put("createdAt",stamp);
        d.getJSONObject("replies").getJSONArray("list").getJSONObject(0).put("createdAt",stamp);
    }
    @Test public void replyCreationTimeSurvivesParserAndHistoryWithoutTimestampingMain()throws Exception{
        JSONObject fixture;
        try(java.io.InputStream input=getClass().getResourceAsStream("/fixtures/hupu-reply-metadata-20261028.json")){
            assertNotNull(input);fixture=new JSONObject(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        long at=fixture.getJSONArray("replies").getJSONObject(0).getLong("createdAt");
        JSONObject value=state(1);String url=fixture.getString("url"),tid=HupuDiscussion.threadId(url);
        detail(value).getJSONObject("thread").put("tid",tid).put("url","/"+tid+".html");
        detail(value).put("lights",new JSONArray());JSONArray rows=new JSONArray();
        for(int n=0;n<fixture.getJSONArray("replies").length();n++){
            JSONObject metadata=fixture.getJSONArray("replies").getJSONObject(n);
            rows.put(reply(metadata.getString("pid"),"测试作者","合成回复正文").put("createdAt",metadata.getLong("createdAt")).put("createdAtFormat","刚刚"));
        }
        detail(value).getJSONObject("replies").put("list",rows);
        Document doc=SourceParser.article(Source.HUPU,html(value),url);
        assertEquals(4,doc.sections.size());assertEquals("hupu-reply-75667",doc.sections.get(1).id);
        assertEquals(0,doc.sections.get(0).sourceReplyAt);
        assertEquals(at,doc.sections.get(1).sourceReplyAt);
        assertEquals(DiscussionReplyEvidence.HUPU_VERSION,doc.sections.get(1).sourceReplyEvidence);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));
        assertEquals(at,restored.sections.get(1).sourceReplyAt);
        assertEquals(DiscussionReplyEvidence.HUPU_VERSION,restored.sections.get(1).sourceReplyEvidence);
        for(int n=0;n<rows.length();n++)assertEquals(rows.getJSONObject(n).getLong("createdAt"),restored.sections.get(n+1).sourceReplyAt);
    }
    @Test public void relativeInvalidWrongUnitAndFutureReplyTimesAreNotInferred()throws Exception{
        for(Object raw:new Object[]{"1793062169000",1793062169L,1793062169000.5,-1,Long.MAX_VALUE,JSONObject.NULL}){
            JSONObject value=state(1);stampAll(value,raw);
            detail(value).getJSONArray("lights").getJSONObject(0).put("createdAtFormat","2026-10-27 09:00:00");
            Document doc=SourceParser.article(Source.HUPU,html(value),URL);
            assertEquals(0,doc.sections.get(1).sourceReplyAt);assertEquals("",doc.sections.get(1).sourceReplyEvidence);
        }
    }
    @Test public void conflictingHighlightedAndOrdinaryCopyTimesAreNotTrusted()throws Exception{
        JSONObject value=state(1);stampAll(value,1793062169000L);
        detail(value).getJSONObject("replies").getJSONArray("list").getJSONObject(0).put("createdAt",1793062169001L);
        Document doc=SourceParser.article(Source.HUPU,html(value),URL);
        assertEquals(0,doc.sections.get(1).sourceReplyAt);
    }
    @Test public void sourceEvidenceCannotBeReattachedAcrossPlatformOrToMain()throws Exception{
        JSONObject value=state(1);stampAll(value,1793062169000L);
        JSONObject stored=ReadingHistory.encode(SourceParser.article(Source.HUPU,html(value),URL));
        JSONObject reply=stored.getJSONArray("sections").getJSONObject(1);
        reply.put("id","hupu-main-123");assertEquals(0,ReadingHistory.decode(stored).sections.get(1).sourceReplyAt);
        reply.put("id","hupu-reply-10");stored.put("url","https://www.v2ex.com/t/123");
        assertEquals(0,ReadingHistory.decode(stored).sections.get(1).sourceReplyAt);
        stored.put("url",URL);reply.getJSONObject("sourceReplyEvidence").put("version",DiscussionReplyEvidence.VERSION);
        assertEquals(0,ReadingHistory.decode(stored).sections.get(1).sourceReplyAt);
    }
    @Test public void deniedAudienceStillCannotExportReplyTimeOrContent()throws Exception{
        JSONObject value=state(1);stampAll(value,1793062169000L);detail(value).put("visibleRange","LOGIN_SEE");
        Document doc=SourceParser.article(Source.HUPU,html(value),URL);
        assertTrue(doc.loginRequired);assertTrue(doc.sections.isEmpty());
    }
    @Test public void realSuffixPaginationUsesNearestObservedForwardPage()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL);
        assertTrue(DiscussionPages.supports(Source.HUPU,URL));assertEquals(1,first.discussionPage);assertEquals("https://bbs.hupu.com/123-2.html",first.nextUrl);assertTrue(DiscussionPages.next(Source.HUPU,first));
        assertEquals(2,DiscussionPages.page(Source.HUPU,first.nextUrl));assertEquals(-1,DiscussionPages.page(Source.HUPU,"https://bbs.hupu.com/123-0.html"));
        assertEquals(-1,DiscussionPages.page(Source.HUPU,"https://bbs.hupu.com/123-1000000.html"));
    }
    @Test public void stableReplyIdsPreserveAuthorAndDeduplicateHighlightedOverlap()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL);
        assertEquals(2,first.sections.size());assertEquals("hupu-main-123",first.sections.get(0).id);assertEquals("主帖 · 楼主",first.sections.get(0).label);
        assertEquals("hupu-reply-10",first.sections.get(1).id);assertEquals("回复 · 甲",first.sections.get(1).label);assertEquals("楼主",first.byline);
        assertFalse(first.blocks.stream().anyMatch(b->b.value.contains("推荐污染")));
    }
    @Test public void uncorroboratedHighlightCannotReserveTheIdentityOfAVisibleRegularReply()throws Exception{
        for(boolean signed:new boolean[]{false,true}){
            JSONObject value=signed?signedIn(1):publicRenderedState();
            detail(value).getJSONArray("lights").getJSONObject(0).put("content","<p>没有出现在来源渲染里的旧版热门回复</p>");
            Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1),URL);
            assertFalse(result.sourceUnavailable);assertFalse(result.loginRequired);
            assertEquals(2,result.sections.size());assertEquals("hupu-reply-10",result.sections.get(1).id);
            assertEquals("热门回复",result.sections.get(1).blocks.get(0).value);
            assertFalse(result.blocks.stream().anyMatch(block->block.value.contains("旧版")));
        }
    }
    @Test public void emptyHighlightCannotSuppressReadableRegularReplyWithTheSameId()throws Exception{
        JSONObject value=state(1);detail(value).getJSONArray("lights").getJSONObject(0).put("content","<p> </p>");
        Document result=SourceParser.article(Source.HUPU,html(value),URL);
        assertEquals(2,result.sections.size());assertEquals("热门回复",result.sections.get(1).blocks.get(0).value);
    }
    @Test public void acceptedHighlightedReplyStillWinsAndFilteredVideoCannotReappearAsItsDuplicate()throws Exception{
        JSONObject value=state(1);detail(value).getJSONObject("replies").getJSONArray("list").getJSONObject(0).put("content","<p>同一回复的另一版本</p>");
        Document result=SourceParser.article(Source.HUPU,html(value),URL);
        assertEquals(2,result.sections.size());assertEquals("热门回复",result.sections.get(1).blocks.get(0).value);
        detail(value).getJSONArray("lights").getJSONObject(0).put("content","<video src='https://v.hupucdn.com/reply.mp4'></video>");
        result=SourceParser.article(Source.HUPU,html(value),URL);
        assertEquals(1,result.sections.size());assertEquals(1,result.filteredVideos);assertTrue(result.filteredSectionIds.contains("hupu-reply-10"));
    }
    @Test public void secondPageOnlyAddsRepliesAndKeepsOriginalMainAndMetadata()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL),second=SourceParser.article(Source.HUPU,html(state(2)),"https://bbs.hupu.com/123-2.html");
        assertEquals(1,second.sections.size());assertTrue(ReaderHtml.discussion(second.sections.get(0)));first.metadataHash="a".repeat(64);
        Document merged=DiscussionPages.merge(Source.HUPU,first,second,first.nextUrl);
        assertEquals(3,merged.sections.size());assertSame(first.sections.get(0),merged.sections.get(0));assertEquals(first.metadataHash,merged.metadataHash);assertEquals(2,merged.discussionPage);
        assertEquals(1,org.jsoup.Jsoup.parse(ReaderHtml.render(merged,Source.HUPU,19)).select("#reader-continuation[data-continuation=ready]").size());
    }
    @Test public void wrongThreadAndWrongStatePageCannotMasqueradeAsReadableContent()throws Exception{
        JSONObject wrong=state(1);detail(wrong).getJSONObject("thread").put("tid","999");Document result=SourceParser.article(Source.HUPU,html(wrong),URL);assertTrue(result.sourceUnavailable);assertFalse(result.hasContent());
        result=SourceParser.article(Source.HUPU,html(state(1)),"https://bbs.hupu.com/123-2.html");assertTrue(result.sourceUnavailable);assertFalse(result.hasContent());
    }
    @Test public void restrictedStateCannotExposeEmbeddedPrivateBody()throws Exception{
        JSONObject privateState=state(1);detail(privateState).put("visibleRange","LOGIN_SEE");Document result=SourceParser.article(Source.HUPU,html(privateState),URL);
        assertTrue(result.loginRequired);assertFalse(result.hasContent());
    }
    private JSONObject signedIn(int page)throws Exception{
        JSONObject value=state(page);detail(value).put("visibleRange","LOGIN_SEE").put("user",new JSONObject().put("puid","789"));
        value.getJSONObject("props").getJSONObject("pageProps").put("detailErrorInfo",new JSONObject().put("code",200));return value;
    }
    private String rendered(int page){return (page==1?"<div class='post-content_main-post-info__fixture'><div class='thread-content-detail'>主帖正文</div></div>":"")+"<div class='reply-list-wrapper'><div class='thread-content-detail'>"+(page==1?"热门回复":"后续回复")+"</div></div>";}
    @Test public void observedStatusThreePublicThreadNeedsSuccessfulRenderedContent()throws Exception{
        JSONObject value=state(1);detail(value).getJSONObject("thread").put("status",3);
        value.getJSONObject("props").getJSONObject("pageProps").put("detailErrorInfo",new JSONObject().put("code",200));
        detail(value).getJSONObject("replies").getJSONArray("list").put(reply("99","其他作者","仅在JSON内出现的回复"));
        Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1),URL);
        assertFalse(result.sourceUnavailable);assertFalse(result.loginRequired);assertEquals(2,result.sections.size());
        assertEquals("hupu-main-123",result.sections.get(0).id);assertFalse(result.blocks.stream().anyMatch(b->b.value.contains("仅在JSON")));
        assertTrue(SourceParser.article(Source.HUPU,html(value),URL).sourceUnavailable);
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1).replace("主帖正文","另一个帖子"),URL).sourceUnavailable);
    }
    @Test public void observedStatusThreeKeepsAudienceResponseAndUnknownStatusGates()throws Exception{
        for(int status:new int[]{-1,1,2,4,99}){JSONObject value=state(1);detail(value).getJSONObject("thread").put("status",status);assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).sourceUnavailable);}
        JSONObject value=state(1);detail(value).getJSONObject("thread").put("status",3);
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).sourceUnavailable);
        value.getJSONObject("props").getJSONObject("pageProps").put("detailErrorInfo",new JSONObject().put("code",403));
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).sourceUnavailable);
        value.getJSONObject("props").getJSONObject("pageProps").getJSONObject("detailErrorInfo").put("code",200);
        detail(value).put("visibleRange","LOGIN_SEE");assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).loginRequired);
        detail(value).put("user",new JSONObject().put("puid","789"));assertFalse(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).loginRequired);
        detail(value).getJSONObject("thread").put("visibleRange","SELF_SEE");assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).loginRequired);
    }
    @Test public void statusThreeContinuationRequiresItsOwnRenderedReplyAndPage()throws Exception{
        JSONObject value=state(2);detail(value).getJSONObject("thread").put("status",3);
        value.getJSONObject("props").getJSONObject("pageProps").put("detailErrorInfo",new JSONObject().put("code",200));
        Document result=SourceParser.article(Source.HUPU,html(value)+rendered(2),"https://bbs.hupu.com/123-2.html");
        assertFalse(result.sourceUnavailable);assertEquals(1,result.sections.size());assertEquals("hupu-reply-11",result.sections.get(0).id);
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),"https://bbs.hupu.com/123-2.html").sourceUnavailable);
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(2),URL).sourceUnavailable);
    }
    @Test public void signedInLoginAudienceReadsOnlyMatchingRenderedBodyAndReplies()throws Exception{
        JSONObject value=signedIn(1);detail(value).getJSONObject("replies").getJSONArray("list").put(reply("99","私密作者","未在来源页显示的回复"));
        Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1),URL);
        assertFalse(result.loginRequired);assertTrue(result.hasContent());assertEquals(2,result.sections.size());assertEquals("hupu-main-123",result.sections.get(0).id);
        assertFalse(result.blocks.stream().anyMatch(b->b.value.contains("未在来源页")));
    }
    @Test public void signedInRestrictedCommentsContinueOnSameThread()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(signedIn(1))+rendered(1),URL);
        Document second=SourceParser.article(Source.HUPU,html(signedIn(2))+rendered(2),"https://bbs.hupu.com/123-2.html");
        assertFalse(second.loginRequired);assertEquals(1,second.sections.size());assertEquals(3,DiscussionPages.merge(Source.HUPU,first,second,first.nextUrl).sections.size());
    }
    @Test public void accountMetadataAloneCannotUnlockEmbeddedRestrictedState()throws Exception{
        assertTrue(SourceParser.article(Source.HUPU,html(signedIn(1)),URL).loginRequired);
        JSONObject value=signedIn(1);detail(value).getJSONObject("user").put("puid","0");
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).loginRequired);
    }
    @Test public void deniedResponseUnknownAudienceAndSelfOnlyStayClosed()throws Exception{
        JSONObject value=signedIn(1);value.getJSONObject("props").getJSONObject("pageProps").getJSONObject("detailErrorInfo").put("code",403);
        assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).loginRequired);
        for(String range:new String[]{"SELF_SEE","UNKNOWN",""}){value=signedIn(1);detail(value).getJSONObject("thread").put("visibleRange",range);assertTrue(SourceParser.article(Source.HUPU,html(value)+rendered(1),URL).loginRequired);}
    }
    @Test public void unrelatedHiddenOrDifferentBodyCannotUnlockRestrictedState()throws Exception{
        for(String body:new String[]{"<aside>"+rendered(1)+"</aside>","<div hidden>"+rendered(1)+"</div>","<div style='display: none'>"+rendered(1)+"</div>",rendered(1).replace("主帖正文","其他帖正文")})
            assertTrue(SourceParser.article(Source.HUPU,html(signedIn(1))+body,URL).loginRequired);
    }
    @Test public void logoutCannotReuseSignedInRenderedContent()throws Exception{
        JSONObject value=signedIn(1);detail(value).remove("user");
        Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1),URL);assertTrue(result.loginRequired);assertFalse(result.hasContent());
    }
    @Test public void hiddenRepliesAndVideoRepliesAreExcludedWithoutLosingTextReplies()throws Exception{
        JSONObject value=state(1);JSONArray rows=detail(value).getJSONObject("replies").getJSONArray("list");rows.put(reply("11","乙","隐藏回复").put("isHidden",true));rows.put(reply("14","戊","隐藏回复").put("isDelete",1));rows.put(reply("12","丙","视频配文").put("content","<video src='https://v.hupucdn.com/a.mp4'></video>"));rows.put(reply("13","丁","正常回复"));
        Document result=SourceParser.article(Source.HUPU,html(value),URL);assertEquals(3,result.sections.size());assertEquals(1,result.filteredVideos);assertFalse(result.blocks.stream().anyMatch(b->b.value.contains("隐藏回复")));
    }
    @Test public void bareDomWithoutKnownStateKeepsExistingReaderCompatibility(){
        Document result=SourceParser.article(Source.HUPU,"<div class='post-content_main-post-info'><div class='thread-content-detail'>兼容旧主帖</div></div><a rel=next href='/123-2.html'>下一页</a>",URL);assertTrue(result.hasContent());assertEquals("主帖",result.sections.get(0).label);
        assertFalse(DiscussionPages.usesStream(Source.HUPU,result));String reader=ReaderHtml.render(result,Source.HUPU,19);assertTrue(reader.contains("继续阅读下一页"));assertFalse(reader.contains("data-continuation='ready'"));
    }
    @Test public void legacyRepliesNeverMixWithSourceIdsAndDuplicateOldText()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL),legacy=SourceParser.article(Source.HUPU,"<div class='reply-list-wrapper'><div class='thread-content-detail'>旧无身份回复</div></div>","https://bbs.hupu.com/123-2.html");
        try{DiscussionPages.merge(Source.HUPU,first,legacy,first.nextUrl);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("身份"));}
        assertEquals(2,first.sections.size());assertEquals(1,first.discussionPage);
    }
    @Test public void untrustedMainVideoStateKeepsEstablishedVideoPolicy()throws Exception{
        JSONObject value=state(1);detail(value).getJSONObject("thread").put("hasVideo",true);
        String body=html(value)+"<div class='post-content_main-post-info'><div class='thread-content-detail'>视频主题说明</div><video src='https://v.hupucdn.com/main.mp4'></video></div>";
        Document filtered=SourceParser.article(Source.HUPU,body,URL);assertTrue(filtered.filteredVideo);assertFalse(filtered.hasContent());
        Document explicit=SourceParser.readingArticle(Source.HUPU,body,URL);assertTrue(explicit.hasContent());assertTrue(explicit.videoTopic);
        String unhydrated=html(value)+"<div class='post-content_main-post-info'><div class='thread-content-detail'>尚未加载播放器的视频配文</div></div>";
        assertTrue(SourceParser.article(Source.HUPU,unhydrated,URL).filteredVideo);assertTrue(SourceParser.readingArticle(Source.HUPU,unhydrated,URL).videoTopic);
    }
    @Test public void croppedPublicPagesKeepActualReplyIdsAuthorsAndForwardCursor()throws Exception{
        Document first=SourceParser.article(Source.HUPU,fixture(1),"https://bbs.hupu.com/643184294.html"),second=SourceParser.article(Source.HUPU,fixture(2),"https://bbs.hupu.com/643184294-2.html");
        assertEquals(65,first.sections.size());assertEquals(20,second.sections.size());assertEquals("hupu-reply-124435",first.sections.get(1).id);assertEquals("回复 · zhaodongchao",first.sections.get(1).label);
        assertEquals("https://bbs.hupu.com/643184294-2.html",first.nextUrl);assertEquals("https://bbs.hupu.com/643184294-3.html",second.nextUrl);
        Document merged=DiscussionPages.merge(Source.HUPU,first,second,first.nextUrl);assertEquals(2,merged.discussionPage);assertSame(first.sections.get(0),merged.sections.get(0));assertEquals(84,merged.sections.size());
        assertEquals(merged.sections.size(),merged.sections.stream().map(s->s.id).distinct().count());
    }
    private String fixture(int page)throws Exception{try(java.io.InputStream input=getClass().getResourceAsStream("/hupu-discussion-page"+page+".html")){assertNotNull(input);return new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}}
    @Test public void freshlyObservedPublicStatusThreeMainIsReadableWithoutLogin()throws Exception{
        String html;try(java.io.InputStream input=getClass().getResourceAsStream("/hupu-public-status3-20261027.html")){assertNotNull(input);html=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        Document result=SourceParser.article(Source.HUPU,html,"https://bbs.hupu.com/643330244.html");
        assertFalse(result.sourceUnavailable);assertFalse(result.loginRequired);assertTrue(result.hasContent());assertEquals("hupu-main-643330244",result.sections.get(0).id);
    }
    private Document videoPage(int page,String pid)throws Exception{
        JSONObject value=state(page);detail(value).getJSONObject("replies").put("list",new JSONArray().put(reply(pid,"视频作者","").put("content","<video src='https://v.hupucdn.com/reply.mp4'></video>")));
        return SourceParser.article(Source.HUPU,html(value),"https://bbs.hupu.com/123-"+page+".html");
    }
    @Test public void videoOnlyNextPageAdvancesWithoutDownloadingOrDisplayingVideo()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL),video=videoPage(2,"11");
        assertFalse(video.sourceUnavailable);assertFalse(video.hasContent());assertEquals(1,video.filteredVideos);
        Document merged=DiscussionPages.merge(Source.HUPU,first,video,first.nextUrl);
        assertEquals(2,merged.discussionPage);assertEquals(first.sections,merged.sections);assertEquals("https://bbs.hupu.com/123-3.html",merged.nextUrl);assertEquals(1,merged.filteredVideos);assertTrue(merged.notice.contains("视频"));
        assertFalse(merged.videoTopic);assertFalse(merged.filteredVideo);assertTrue(merged.videos.isEmpty());assertTrue(ReaderHtml.render(merged,Source.HUPU,19).contains("data-continuation='ready'"));
        Document restored=ReadingHistory.decode(ReadingHistory.encode(merged));assertTrue(restored.filteredSectionIds.contains("hupu-reply-11"));
    }
    @Test public void laterDuplicateOfFilteredReplyCannotReturnAsPlainText()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL),merged=DiscussionPages.merge(Source.HUPU,first,videoPage(2,"11"),first.nextUrl);
        JSONObject value=state(3);detail(value).getJSONObject("replies").put("list",new JSONArray().put(reply("11","视频作者","旧视频配文")).put(reply("12","读者","新的文字回复")));
        Document third=SourceParser.article(Source.HUPU,html(value),merged.nextUrl),last=DiscussionPages.merge(Source.HUPU,merged,third,merged.nextUrl);
        assertEquals(3,last.sections.size());assertFalse(last.sections.stream().anyMatch(s->s.id.equals("hupu-reply-11")));assertEquals(1,last.filteredVideos);assertEquals("end",last.moreStatus);
    }
    @Test public void reclassifiedExistingReplyIsRemovedButOriginalMainAndOtherRepliesStay()throws Exception{
        Document first=SourceParser.article(Source.HUPU,html(state(1)),URL),video=videoPage(2,"10"),merged=DiscussionPages.merge(Source.HUPU,first,video,first.nextUrl);
        assertEquals(1,merged.sections.size());assertSame(first.sections.get(0),merged.sections.get(0));assertEquals(2,first.sections.size());assertEquals(2,merged.discussionPage);
    }
    private static final String MEDIA="https://i1.hoopchina.com.cn/test-only/visible-image.png";
    private JSONObject publicRenderedState()throws Exception{
        JSONObject value=state(1);detail(value).getJSONObject("thread").put("status",3);
        value.getJSONObject("props").getJSONObject("pageProps").put("detailErrorInfo",new JSONObject().put("code",200));return value;
    }
    @Test public void renderedImageOnlyMainIsReadableWithoutGuessingLoginFromItsEmptyText()throws Exception{
        JSONObject value=publicRenderedState();detail(value).getJSONObject("thread").put("content","<p><img src='"+MEDIA+"'></p>");
        String source="<div class=post-content_main-post-info__fixture><div class=thread-content-detail><img src='data:image/gif;base64,AA' data-src='"+MEDIA+"'></div></div>";
        Document result=SourceParser.article(Source.HUPU,html(value)+source,URL);
        assertFalse(result.sourceUnavailable);assertFalse(result.loginRequired);assertTrue(result.containsImage(MEDIA));
    }
    @Test public void renderedImageOnlyReplySurvivesPublicAndSignedInGates()throws Exception{
        for(boolean signed:new boolean[]{false,true}){
            JSONObject value=signed?signedIn(1):publicRenderedState();
            detail(value).getJSONObject("replies").put("list",new JSONArray().put(reply("88","图片作者","<img src='"+MEDIA+"'>")));detail(value).put("lights",new JSONArray());
            String source=rendered(1)+"<div class=reply-list-wrapper><div class=thread-content-detail><img data-original='"+MEDIA+"'></div></div>";
            Document result=SourceParser.article(Source.HUPU,html(value)+source,URL);
            assertTrue(result.sections.stream().anyMatch(part->part.id.equals("hupu-reply-88")));assertTrue(result.containsImage(MEDIA));
        }
    }
    @Test public void matchingVisibleTextDoesNotAuthorizeAnUnrenderedJsonAttachment()throws Exception{
        JSONObject value=signedIn(1);detail(value).getJSONObject("thread").put("content","<p>主帖正文<img src='"+MEDIA+"'></p>");
        Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1),URL);
        assertTrue(result.loginRequired);assertFalse(result.containsImage(MEDIA));assertFalse(result.hasContent());
    }
    @Test public void hiddenSourceImageAndDifferentSourceImageCannotCorroborateJsonMedia()throws Exception{
        for(String image:new String[]{"<img hidden src='"+MEDIA+"'>","<span style='display: none'><img src='"+MEDIA+"'></span>","<img src='https://i1.hoopchina.com.cn/test-only/other.png'>"}){
            JSONObject value=signedIn(1);detail(value).getJSONObject("thread").put("content","<p>主帖正文<img src='"+MEDIA+"'></p>");
            Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1).replace("主帖正文","主帖正文"+image),URL);
            assertTrue(result.loginRequired);assertFalse(result.hasContent());
        }
    }

    @Test public void hiddenMarkupDoesNotTurnVisibleSignedInMainIntoLoginDenial()throws Exception{
        for(String hidden:new String[]{"hidden","aria-hidden='true'","style='display: none'","style='visibility: hidden'"}){
            String content="<p>主帖正文<span "+hidden+">隐藏说明<img src='"+MEDIA+"'></span></p>";
            JSONObject value=signedIn(1);detail(value).getJSONObject("thread").put("content",content);
            String source=rendered(1).replace("主帖正文",content);
            Document result=SourceParser.article(Source.HUPU,html(value)+source,URL);
            assertFalse(hidden,result.loginRequired);assertFalse(result.sourceUnavailable);assertTrue(result.hasContent());
            assertFalse(result.blocks.stream().anyMatch(block->block.value.contains("隐藏说明")));assertFalse(result.containsImage(MEDIA));
        }
    }
    @Test public void hiddenMarkupDoesNotDropVisiblePublicOrSignedInReply()throws Exception{
        for(boolean signed:new boolean[]{false,true}){
            JSONObject value=signed?signedIn(1):publicRenderedState();String content="热门回复<span hidden>隐藏回复内容<img src='"+MEDIA+"'></span>";
            detail(value).put("lights",new JSONArray());detail(value).getJSONObject("replies").put("list",new JSONArray().put(reply("10","甲",content)));
            Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1).replace("热门回复",content),URL);
            assertFalse(result.loginRequired);assertFalse(result.sourceUnavailable);
            assertTrue(result.sections.stream().anyMatch(part->part.id.equals("hupu-reply-10")));
            assertFalse(result.blocks.stream().anyMatch(block->block.value.contains("隐藏回复内容")));assertFalse(result.containsImage(MEDIA));
        }
    }
    @Test public void publicMainDoesNotExportSourceHiddenTextOrImages()throws Exception{
        JSONObject value=state(1);detail(value).getJSONObject("thread").put("content","<p>主帖正文<span hidden>隐藏说明<img src='"+MEDIA+"'></span></p>");
        Document result=SourceParser.article(Source.HUPU,html(value),URL);
        assertTrue(result.hasContent());assertFalse(result.blocks.stream().anyMatch(block->block.value.contains("隐藏说明")));assertFalse(result.containsImage(MEDIA));
    }
    @Test public void whollyHiddenJsonCannotBorrowAnUnrelatedVisibleBody()throws Exception{
        JSONObject value=signedIn(1);detail(value).getJSONObject("thread").put("content","<div hidden>主帖正文<img src='"+MEDIA+"'></div>");
        Document result=SourceParser.article(Source.HUPU,html(value)+rendered(1),URL);
        assertTrue(result.loginRequired);assertFalse(result.hasContent());assertFalse(result.containsImage(MEDIA));
    }
    @Test public void olderBodySnapshotsRefreshOnlineAndKeepOfflineRevisionWithoutLaundering()throws Exception{
        Document current=SourceParser.article(Source.HUPU,html(state(1)),URL);JSONObject encoded=ReadingHistory.encode(current);
        assertEquals(HupuDiscussion.BODY_REVISION,encoded.getInt("hupuBodyRevision"));encoded.remove("hupuBodyRevision");Document old=ReadingHistory.decode(encoded);
        assertFalse(HupuDiscussion.canReuse(old,false));assertTrue(HupuDiscussion.canReuse(old,true));
        assertEquals(0,ReadingHistory.decode(ReadingHistory.encode(old)).hupuBodyRevision);assertEquals(0,AnswerStream.copy(old).hupuBodyRevision);
        assertTrue(HupuDiscussion.canReuse(current,false));assertEquals(DocumentFingerprint.of(current),DocumentFingerprint.of(ReadingHistory.decode(ReadingHistory.encode(current))));
        Document revisionOne=ReadingHistory.decode(ReadingHistory.encode(current));revisionOne.hupuBodyRevision=1;
        assertFalse(HupuDiscussion.canReuse(revisionOne,false));assertTrue(HupuDiscussion.canReuse(revisionOne,true));
        assertEquals(1,ReadingHistory.decode(ReadingHistory.encode(revisionOne)).hupuBodyRevision);
        old.url="https://www.v2ex.com/t/123";assertTrue(HupuDiscussion.canReuse(old,false));
    }

}
