package app.quietreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class TiebaReplyRelationsTest {
    private static final String URL="https://tieba.baidu.com/p/456";
    private static final String TARGET="<a href='/home/main?un=parent'>被回复者</a>";
    private Document document(String child){return SourceParser.article(Source.TIEBA,"<div class=pc-pb-box><div class=image-text><a class=head-name>楼主</a><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=11><a class=user-name>楼层作者</a><div class=comment-content><div class=pb-rich-text>普通楼层</div></div>"+child+"</div><div class=pb-comment-item data-id=12><a class=user-name>下一楼作者</a><div class=comment-content><div class=pb-rich-text>下一楼</div></div></div></div></div>",URL);}
    private String child(String text){return "<div class=pb-lzl-item data-id=101><a class=head-name>楼内作者</a><div class=comment-content><div class=pb-rich-text>"+text+"</div></div></div>";}
    @Test public void explicitReplyPrefixBecomesCompactMetadataAndKeepsRealBodyAndParent(){
        Document doc=document(child("<div>回复</div><div>"+TARGET+"</div><div>：真正的正文<img class=emoji-img src='https://imgsa.baidu.com/emote.png' alt='笑'></div>"));
        Section floor=doc.sections.get(1),sub=doc.sections.get(2);
        assertFalse(floor.nestedReply);assertTrue(floor.replyToAuthor.isEmpty());
        assertTrue(sub.nestedReply);assertEquals(floor.id,sub.parentSectionId);assertEquals("被回复者",sub.replyToAuthor);
        assertEquals("真正的正文笑",sub.blocks.get(0).value);assertEquals(1,sub.blocks.get(0).inlineImages.size());
        org.jsoup.nodes.Document html=Jsoup.parse(ReaderHtml.render(doc,Source.TIEBA,19));
        assertEquals("楼层作者",html.select("#part-1 .section-label").text());
        assertEquals("楼内作者 · 楼内回复 · 回复 被回复者",html.select("#part-2 .section-label").text());
        assertEquals(1,html.select(".nested-reply").size());assertEquals(0,html.select("#part-2 p .content-link").size());
    }
    @Test public void ordinaryCommentsDoNotInventTargetsAndBodyMentionsRemainUnchanged(){
        for(String value:List.of("我想回复"+TARGET+"：这句话", "回复<a href='https://example.com/home/main'>被回复者</a>：正文", "回复"+TARGET+"的问题", "回复某人：正文")){
            Element body=Jsoup.parseBodyFragment(value,URL).body();String before=body.html();
            assertEquals("",TiebaReplyRelations.extractTarget(body,URL));assertEquals(before,body.html());
        }
    }
    @Test public void nestedRelationshipSurvivesSnapshotsAndHistoryWithoutReorderingNextFloor()throws Exception{
        Document before=document(""),incoming=document(child("回复"+TARGET+"：楼内正文"));
        Document merged=TiebaReplies.merge(before,incoming);
        assertEquals(4,merged.sections.size());assertTrue(merged.sections.get(2).nestedReply);assertEquals("下一楼",merged.sections.get(3).blocks.get(0).value);
        assertEquals(4,TiebaReplies.merge(merged,incoming).sections.size());
        Document restored=ReadingHistory.decode(ReadingHistory.encode(merged));
        assertEquals(DocumentFingerprint.of(merged),DocumentFingerprint.of(restored));
        assertEquals(merged.sections.get(1).id,restored.sections.get(2).parentSectionId);assertEquals("被回复者",restored.sections.get(2).replyToAuthor);
        assertEquals(1,Jsoup.parse(ReaderHtml.render(restored,Source.TIEBA,19)).select(".nested-reply").size());
    }
    @Test public void desktopNestedOwnerIsPreservedWithoutInventingReplyTarget(){
        String html="<div id=j_p_postlist><div class=l_post><a class=d_name>甲</a><div class=d_post_content id=post_content_11>楼层</div><ul><li class=lzl_single_post data-field='{&quot;spid&quot;:101}'><a class=j_user_card>乙</a><span class=lzl_content_main>楼内内容</span></li></ul></div></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,URL);assertTrue(doc.sections.get(1).nestedReply);assertEquals(doc.sections.get(0).id,doc.sections.get(1).parentSectionId);assertTrue(doc.sections.get(1).replyToAuthor.isEmpty());
    }
    @Test public void legacySnapshotsRefreshOnlineButRemainReadableOfflineWithoutBeingRelabeled()throws Exception{
        Document current=document(child("楼内文字"));
        org.json.JSONObject saved=ReadingHistory.encode(current);saved.remove("tiebaReplyRevision");
        for(int n=0;n<saved.getJSONArray("sections").length();n++){
            org.json.JSONObject section=saved.getJSONArray("sections").getJSONObject(n);
            section.remove("nestedReply");section.remove("parentSectionId");section.remove("replyToAuthor");
        }
        Document legacy=ReadingHistory.decode(saved);
        assertTrue(legacy.hasContent());assertFalse(TiebaReplyRelations.canReuse(legacy,false));assertTrue(TiebaReplyRelations.canReuse(legacy,true));
        assertFalse(TiebaReplyRelations.canReuse(AnswerStream.copy(legacy),false));
        assertFalse(TiebaReplyRelations.canReuse(ReadingHistory.decode(ReadingHistory.encode(legacy)),false));
        assertTrue(TiebaReplyRelations.canReuse(ReadingHistory.decode(ReadingHistory.encode(current)),false));
        Document revisionOne=ReadingHistory.decode(ReadingHistory.encode(current));revisionOne.tiebaReplyRevision=1;
        assertFalse(TiebaReplyRelations.canReuse(revisionOne,false));assertTrue(TiebaReplyRelations.canReuse(revisionOne,true));
        assertEquals(1,ReadingHistory.decode(ReadingHistory.encode(revisionOne)).tiebaReplyRevision);
        Document revisionTwo=ReadingHistory.decode(ReadingHistory.encode(current));revisionTwo.tiebaReplyRevision=2;
        assertFalse(TiebaReplyRelations.canReuse(revisionTwo,false));assertTrue(TiebaReplyRelations.canReuse(revisionTwo,true));
        assertEquals(2,ReadingHistory.decode(ReadingHistory.encode(revisionTwo)).tiebaReplyRevision);

        legacy.url="https://www.v2ex.com/t/123";assertTrue(TiebaReplyRelations.canReuse(legacy,false));
    }
    @Test public void legacyReplyIdentitiesRemainReadableButCannotContinueIntoCurrentWindows()throws Exception{
        Document current=document(child("楼内文字"));
        assertTrue(TiebaReplies.supports(Source.TIEBA,current));
        for(int revision=0;revision<TiebaReplyRelations.REVISION;revision++){
            Document legacy=ReadingHistory.decode(ReadingHistory.encode(current));legacy.tiebaReplyRevision=revision;
            String fingerprint=DocumentFingerprint.of(legacy);
            assertTrue(legacy.hasContent());assertTrue(TiebaReplyRelations.canReuse(legacy,true));
            assertFalse(TiebaReplyRelations.canReuse(legacy,false));
            assertFalse(TiebaReplies.supports(Source.TIEBA,legacy));
            assertThrows(IllegalArgumentException.class,()->TiebaReplies.merge(legacy,current));
            assertThrows(IllegalArgumentException.class,()->TiebaReplies.merge(current,legacy));
            assertEquals(fingerprint,DocumentFingerprint.of(legacy));
            assertEquals(revision,ReadingHistory.decode(ReadingHistory.encode(legacy)).tiebaReplyRevision);
        }
    }
    private Document anonymousChildWindow(String floor){
        String html="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id='"+floor+"'><a class=user-name>楼层作者</a><div class=comment-content><div class=pb-rich-text>楼层正文</div></div>"+child("相同的楼内回复").replace(" data-id=101","")+"</div></div></div></div>";
        return SourceParser.article(Source.TIEBA,html,URL);
    }
    @Test public void identicalUnidentifiedChildrenInDifferentVirtualFloorsKeepTheirOwnParent(){
        Document first=anonymousChildWindow("11"),second=anonymousChildWindow("12");
        Section left=first.sections.get(2),right=second.sections.get(2);
        assertNotEquals(left.id,right.id);
        Document merged=TiebaReplies.merge(first,second);
        assertEquals(5,merged.sections.size());
        assertEquals(first.sections.get(1).id,merged.sections.get(2).parentSectionId);
        assertEquals(second.sections.get(1).id,merged.sections.get(4).parentSectionId);
        assertEquals(5,TiebaReplies.merge(merged,second).sections.size());
        // Stable source floor identity survives its position in a virtual window.
        Document repeated=anonymousChildWindow("11");
        assertEquals(left.id,repeated.sections.get(2).id);
    }

    private Document emojiWindow(String attributes,String childId){
        String body="<img class=emoji-img "+attributes+" alt='表情'>";
        String sub=child(body).replace(" data-id=101",childId.isEmpty()?"":" data-id="+childId);
        return document(sub);
    }
    @Test public void unidentifiedLazyEmojiWindowsDoNotCollapseDifferentImages(){
        Document left=emojiWindow("src='data:image/gif;base64,AA' data-src='https://tb3.bdstatic.com/emoji/image_emoticon1.png'","");
        Document right=emojiWindow("src='data:image/gif;base64,AA' data-src='https://tb3.bdstatic.com/emoji/image_emoticon2.png'","");
        assertNotEquals(left.sections.get(2).id,right.sections.get(2).id);
        Document merged=TiebaReplies.merge(left,right);
        assertEquals(5,merged.sections.size());
        assertEquals(5,TiebaReplies.merge(merged,right).sections.size());
        assertEquals(merged.sections.get(1).id,merged.sections.get(2).parentSectionId);
        assertEquals(merged.sections.get(1).id,merged.sections.get(3).parentSectionId);
    }
    @Test public void emojiLazyLoadAttributeTransitionKeepsFallbackIdentity()throws Exception{
        String image="https://tb3.bdstatic.com/emoji/image_emoticon1.png";
        Document lazy=emojiWindow("src='data:image/gif;base64,AA' data-original='"+image+"'","");
        Document loaded=emojiWindow("src='"+image+"'","");
        assertEquals(lazy.sections.get(2).id,loaded.sections.get(2).id);
        Document merged=TiebaReplies.merge(lazy,loaded);
        assertEquals(4,merged.sections.size());
        Document restored=ReadingHistory.decode(ReadingHistory.encode(merged));
        assertEquals(DocumentFingerprint.of(merged),DocumentFingerprint.of(restored));
        assertEquals(image,restored.sections.get(2).blocks.get(0).inlineImages.get(0).url);
    }
    @Test public void explicitChildIdentityPreservesDistinctIdenticalEmojiReplies(){
        String image="src='https://tb3.bdstatic.com/emoji/image_emoticon1.png'";
        Document left=emojiWindow(image,"101"),right=emojiWindow(image,"102");
        assertNotEquals(left.sections.get(2).id,right.sections.get(2).id);
        Document merged=TiebaReplies.merge(left,right);
        assertEquals(5,merged.sections.size());assertEquals(5,TiebaReplies.merge(merged,right).sections.size());
    }

}
