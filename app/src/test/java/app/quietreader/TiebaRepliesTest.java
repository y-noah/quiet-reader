package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class TiebaRepliesTest {
    private static final String URL="https://tieba.baidu.com/p/456";
    private Document window(String ids,String status){
        StringBuilder html=new StringBuilder("<h1>真实标题</h1><div class=pc-pb-box><div class=image-text><a class=author-name>作者</a><div class=pb-content-wrap>主帖内容</div></div><div class=pc-pb-reply-list><div class=pb-comment-list>");
        for(String id:ids.split(","))html.append("<div class=pb-comment-item data-id='").append(id).append("'><a class=user-name>回复者</a><div class=comment-content><div class=pb-rich-text>相同文字</div></div></div>");
        html.append("</div>").append(status).append("</div></div>");
        return SourceParser.article(Source.TIEBA,html.toString(),URL);
    }
    private List<String> ids(Document doc){List<String> ids=new ArrayList<>();for(Section s:doc.sections)if(ReaderHtml.discussion(s))ids.add(s.sourcePostId);return ids;}
    @Test public void virtualWindowsUseStableSourceIdsRatherThanTextOrPosition(){
        Document a=window("11,12",""),b=window("12,13","");assertTrue(a.virtualDiscussion);
        Document merged=TiebaReplies.merge(a,b);assertEquals(List.of("11","12","13"),ids(merged));assertSame(a.sections.get(0),merged.sections.get(0));assertEquals("真实标题",merged.title);
        assertEquals(ids(merged),ids(TiebaReplies.merge(merged,b)));
    }
    @Test public void invalidIdsCannotBeAppendedAndWrongThreadOrPageIsRejected(){
        Document a=window("11",""),b=window("0,bad,13","");assertEquals(List.of("11","13"),ids(TiebaReplies.merge(a,b)));
        for(String url:List.of("https://tieba.baidu.com/p/789",URL+"?pn=2","https://example.com/p/456")){
            b.url=url;try{TiebaReplies.merge(a,b);fail(url);}catch(IllegalArgumentException expected){}
        }
    }
    @Test public void downloadEndMarkerDoesNotMeanAllVirtualRowsHaveBeenRead(){
        Document doc=window("11","<div class=loading>—— 已加载全部评论 ——</div>");
        assertEquals("",doc.moreStatus);assertTrue(ReaderContinuation.automatic(Source.TIEBA,doc));
        assertTrue(ReaderHtml.render(doc,Source.TIEBA,19).contains("data-continuation='ready'"));
        doc.moreStatus="end";assertFalse(ReaderContinuation.automatic(Source.TIEBA,doc));
    }
    @Test public void loginAndSourceErrorsStopAutomaticRequestsWithoutLosingReadReplies(){
        Document before=window("11","");
        for(String state:List.of("login","error")){
            Document incoming=window("12",state.equals("login")?"<div class=login-guard-mask></div>":"<div class=load-error-wrapper></div>");
            assertTrue(incoming.notice.contains(state.equals("login")?"需要来源登录":"加载失败"));assertFalse(incoming.notice.contains("仅展示当前页"));
            Document merged=TiebaReplies.merge(before,incoming);assertEquals(state,merged.moreStatus);assertEquals(List.of("11","12"),ids(merged));assertFalse(ReaderContinuation.automatic(Source.TIEBA,merged));
        }
    }
    @Test public void copiesAndHistoryRetainContinuationEligibility()throws Exception{
        Document doc=window("11,12","");
        for(Document copy:List.of(AnswerStream.copy(doc),ReadingHistory.decode(ReadingHistory.encode(doc)))){
            assertTrue(copy.virtualDiscussion);assertEquals(ids(doc),ids(copy));assertEquals(DocumentFingerprint.of(doc),DocumentFingerprint.of(copy));
        }
        doc.virtualDiscussion=false;assertFalse(ReaderContinuation.automatic(Source.TIEBA,doc));
    }
    @Test public void sourceOwnedReplyImagesStayWithTheirComment(){
        String html="<div class=pc-pb-box><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=11><div class=comment-content><div class=pb-rich-text>图片回复</div><div class=pb-image-list><img src='https://imgsa.baidu.com/forum/a.jpg'></div></div><aside><img src='https://imgsa.baidu.com/forum/ad.jpg'></aside></div></div></div></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,URL);assertEquals(List.of("11"),ids(doc));assertTrue(doc.containsImage("https://imgsa.baidu.com/forum/a.jpg"));assertFalse(doc.containsImage("https://imgsa.baidu.com/forum/ad.jpg"));
    }
    private Document imageOnlyFloor(boolean loaded){
        String pictures="";for(String name:List.of("a","b"))pictures+="<img "+(loaded?"src='https://tiebapic.baidu.com/"+name+".jpg'":"src='data:image/gif;base64,AA' data-src='https://tiebapic.baidu.com/"+name+".jpg'")+">";
        String html="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=11><div class=comment-content><div class=pb-rich-text><div class=pb-content-item></div></div><div class=image-card-wrapper>"+pictures+"</div><div class=lzl-wrapper><div class=pb-lzl-item data-qr-child-id=101 data-qr-parent-id=11><div class=comment-content><div class=pb-rich-text>楼中楼<img class=emoji-img src='https://tiebapic.baidu.com/emoji.png' alt='笑'></div></div></div></div></div><aside><div class=image-card-wrapper><img src='https://tiebapic.baidu.com/ad.jpg'></div></aside></div><div class=pb-comment-item data-id=12><div class=comment-content><div class=pb-rich-text>下一楼</div></div></div></div><div class=login-guard-mask></div></div></div>";
        return SourceParser.article(Source.TIEBA,html,URL);
    }
    @Test public void imageOnlySourceFloorKeepsItsChildrenAndCreationTimeThroughHistory()throws Exception{
        Document doc=imageOnlyFloor(false);assertEquals(4,doc.sections.size());
        Section parent=doc.sections.get(1),child=doc.sections.get(2);
        assertEquals("11",parent.sourcePostId);assertFalse(parent.nestedReply);
        assertEquals(List.of("https://tiebapic.baidu.com/a.jpg","https://tiebapic.baidu.com/b.jpg"),parent.blocks.stream().filter(b->b.type.equals("image")).map(b->b.value).toList());
        assertTrue(child.nestedReply);assertEquals(parent.id,child.parentSectionId);assertEquals(1,child.blocks.get(0).inlineImages.size());
        assertFalse(doc.containsImage("https://tiebapic.baidu.com/ad.jpg"));assertEquals("12",doc.sections.get(3).sourcePostId);
        org.json.JSONObject times=new org.json.JSONObject().put("url",URL).put("kind",TiebaRenderedReplies.KIND).put("rows",new org.json.JSONArray().put(new org.json.JSONArray().put("11").put("101").put(1700000000L)));
        TiebaReplyTimes.attach(doc,times,System.currentTimeMillis());assertEquals(1700000000000L,child.sourceReplyAt);assertEquals(0,parent.sourceReplyAt);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));assertEquals(DocumentFingerprint.of(doc),DocumentFingerprint.of(restored));
        assertEquals(parent.id,restored.sections.get(2).parentSectionId);assertEquals(child.sourceReplyAt,restored.sections.get(2).sourceReplyAt);
    }
    @Test public void imageOnlyFloorLazyTransitionDoesNotDuplicateItsReplyWindow(){
        Document lazy=imageOnlyFloor(false),loaded=imageOnlyFloor(true);
        assertEquals(4,lazy.sections.size());assertEquals(DocumentFingerprint.of(lazy),DocumentFingerprint.of(loaded));
        Document merged=TiebaReplies.merge(lazy,loaded);assertEquals(4,merged.sections.size());
        assertEquals(lazy.sections.get(1).id,merged.sections.get(2).parentSectionId);
        assertEquals(4,TiebaReplies.merge(merged,loaded).sections.size());
    }
    private Document missingEmoji(boolean loaded,boolean child){
        String emoji="<img class=emoji-img alt='表情' "+(loaded?"src='https://tb3.bdstatic.com/emoji/image_emoticon1.png'":"src='data:image/gif;base64,AA'")+">";
        String floor="<div class=pb-comment-item data-id=11><a class=user-name>楼层作者</a><div class=comment-content><div class=pb-rich-text>楼层正文"+(child?"":emoji)+"</div></div>";
        if(child)floor+="<div class=pb-lzl-item data-id=101><a class=head-name>楼内作者</a><div class=comment-content><div class=pb-rich-text>楼内正文"+emoji+"</div></div></div>";
        return SourceParser.article(Source.TIEBA,"<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list>"+floor+"</div></div></div></div>",URL);
    }
    @Test public void sameExplicitReplyCanGainMissingInlineEmojiWithoutNewFloor()throws Exception{
        String image="https://tb3.bdstatic.com/emoji/image_emoticon1.png";
        for(boolean child:new boolean[]{false,true}){
            Document before=missingEmoji(false,child),incoming=missingEmoji(true,child);
            assertFalse(before.containsImage(image));assertTrue(incoming.containsImage(image));
            Document merged=TiebaReplies.merge(before,incoming);
            assertTrue("same explicit "+(child?"child":"floor")+" must gain its emoji",merged.containsImage(image));
            assertEquals(before.sections.size(),merged.sections.size());assertFalse(before.containsImage(image));
            assertEquals(DocumentFingerprint.of(merged),DocumentFingerprint.of(TiebaReplies.merge(merged,incoming)));
            Document restored=ReadingHistory.decode(ReadingHistory.encode(merged));
            assertTrue(restored.containsImage(image));assertEquals(DocumentFingerprint.of(merged),DocumentFingerprint.of(restored));
            var method=TiebaReplies.class.getDeclaredMethod("progressIds",Document.class);method.setAccessible(true);
            Set<?> old=(Set<?>)method.invoke(null,before),now=(Set<?>)method.invoke(null,merged);
            assertFalse("media enrichment must finish actual continuation even with unchanged floor identities",old.containsAll(now));
        }
    }
}
