package app.quietreader;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class TiebaReplyTimesTest {
    private static final String URL="https://tieba.baidu.com/p/11139792603";
    private static final long NOW=System.currentTimeMillis();
    private JSONObject fixture()throws Exception{try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/tieba-child-times-20261028.json")){assertNotNull(in);return new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}}
    private Document doc()throws Exception{
        JSONArray rows=fixture().getJSONArray("replies");StringBuilder html=new StringBuilder("<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list>");
        String parent="";
        for(int n=0;n<rows.length();n++){
            JSONObject row=rows.getJSONObject(n);String next=row.getString("post_id");
            if(!next.equals(parent)){if(!parent.isEmpty())html.append("</div>");parent=next;html.append("<div class=pb-comment-item data-id='").append(parent).append("'><div class=comment-content><div class=pb-rich-text>合成父楼正文</div></div>");}
            html.append("<div class=pb-lzl-item data-id='").append(row.getString("comment_id")).append("'><div class=comment-content><div class=pb-rich-text>合成楼内正文</div></div></div>");
        }
        return SourceParser.article(Source.TIEBA,html+"</div></div></div></div>",URL);
    }
    private Section child(Document doc,int row)throws Exception{String id="tieba-post-"+app.quietreader.discovery.HotCandidate.hash("lzl:"+fixture().getJSONArray("replies").getJSONObject(row).getString("comment_id")).substring(0,24);return doc.sections.stream().filter(s->s.id.equals(id)).findFirst().orElseThrow();}
    private JSONObject payload()throws Exception{JSONArray rows=fixture().getJSONArray("replies"),out=new JSONArray();for(int n=0;n<rows.length();n++){JSONObject r=rows.getJSONObject(n);out.put(new JSONArray().put(r.getString("post_id")).put(r.getString("comment_id")).put(r.getLong("now_time")));}return new JSONObject().put("url",URL).put("rows",out);}
    private void absent(Document doc){for(Section s:doc.sections){assertEquals(0,s.sourceReplyAt);assertEquals("",s.sourceReplyEvidence);}}
    @Test public void exactPublicChildAndParentTimesSurviveHistory()throws Exception{
        Document doc=doc();TiebaReplyTimes.attach(doc,payload(),NOW);assertEquals(6,doc.sections.size());assertEquals(0,doc.sections.get(0).sourceReplyAt);assertEquals(0,doc.sections.get(1).sourceReplyAt);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));JSONArray rows=fixture().getJSONArray("replies");
        for(int n=0;n<rows.length();n++){Section s=child(restored,n);assertTrue(s.nestedReply);assertTrue(restored.sections.stream().anyMatch(f->f.id.equals(s.parentSectionId)&&!f.nestedReply));assertEquals(rows.getJSONObject(n).getLong("now_time")*1000,s.sourceReplyAt);assertEquals(TiebaReplyTimes.VERSION,s.sourceReplyEvidence);}
    }
    @Test public void differentThreadOrPageCannotBorrowTime()throws Exception{
        for(String url:new String[]{"https://tieba.baidu.com/p/11139792604",URL+"?pn=2","https://example.com/p/11139792603"}){Document doc=doc();TiebaReplyTimes.attach(doc,payload().put("url",url),NOW);absent(doc);}
    }
    @Test public void visibleRenderedMetadataSurvivesContinuationGateButCannotPromote()throws Exception{
        Document api=doc();api.moreStatus="login";TiebaReplyTimes.attach(api,payload(),NOW);absent(api);
        Document rendered=doc();rendered.moreStatus="login";
        TiebaReplyTimes.attach(rendered,payload().put("kind",TiebaRenderedReplies.KIND),NOW);
        assertTrue(child(rendered,0).sourceReplyAt>0);assertEquals("login",rendered.moreStatus);
        assertNull(DiscussionProgressEvidence.freeze(Source.TIEBA,rendered,"a".repeat(64),NOW));
        rendered=doc();rendered.loginRequired=true;TiebaReplyTimes.attach(rendered,payload().put("kind",TiebaRenderedReplies.KIND),NOW);absent(rendered);
        rendered=doc();TiebaReplyTimes.attach(rendered,payload().put("kind","unknown"),NOW);absent(rendered);
    }
    @Test public void renderedIdentityAnnotationsMustMatchActualParentAndExistingId()throws Exception{
        String prefix="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=154074790043><div class=comment-content><div class=pb-rich-text>父楼</div></div>";
        String suffix="<div class=comment-content><div class=pb-rich-text>楼中楼</div></div></div></div></div></div></div>";
        String expected="tieba-post-"+app.quietreader.discovery.HotCandidate.hash("lzl:154076372041").substring(0,24);
        Document valid=SourceParser.article(Source.TIEBA,prefix+"<div class=pb-lzl-item data-qr-child-id=154076372041 data-qr-parent-id=154074790043>"+suffix,URL);
        assertEquals(expected,valid.sections.get(2).id);assertEquals(valid.sections.get(1).id,valid.sections.get(2).parentSectionId);
        Document wrong=SourceParser.article(Source.TIEBA,prefix+"<div class=pb-lzl-item data-qr-child-id=154076372041 data-qr-parent-id=999>"+suffix,URL);assertNotEquals(expected,wrong.sections.get(2).id);
        wrong=SourceParser.article(Source.TIEBA,prefix+"<div class=pb-lzl-item data-id=999 data-qr-child-id=154076372041 data-qr-parent-id=154074790043>"+suffix,URL);assertNotEquals(expected,wrong.sections.get(2).id);
    }
    @Test public void resolvedTopicUsesActualPostForMetadataAndHistory()throws Exception{
        String topic="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123";
        Document doc=doc();
        TiebaTopic.attach(topic,java.util.List.of(new Item(Source.TIEBA,"合成帖子",URL,"")),doc,"a".repeat(64),NOW);
        TiebaReplyTimes.attach(doc,payload(),NOW);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));
        assertEquals(topic,restored.url);assertEquals(URL,restored.sourceUrl());
        for(int n=0;n<3;n++)assertEquals(fixture().getJSONArray("replies").getJSONObject(n).getLong("now_time")*1000,child(restored,n).sourceReplyAt);
        assertNull("Decoded body without its acquisition envelope cannot create progress",DiscussionProgressEvidence.freeze(Source.TIEBA,restored,"a".repeat(64),NOW));
    }
    @Test public void changedSelectedPostCannotReuseOldChildTimesEvenWithSameTopic()throws Exception{
        String topic="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123",other="https://tieba.baidu.com/p/11139792604";
        Document doc=doc();doc.url=other;
        TiebaTopic.attach(topic,java.util.List.of(new Item(Source.TIEBA,"另一个帖子",other,"")),doc,"a".repeat(64),NOW);
        TiebaReplyTimes.attach(doc,payload(),NOW);absent(doc);
    }
    @Test public void completedMetadataEnrichesOverlapWithoutMutatingDisplayedBaseline()throws Exception{
        Document before=doc(),incoming=doc();TiebaReplyTimes.attach(incoming,payload(),NOW);
        Document result=TiebaReplies.merge(before,incoming);absent(before);
        assertEquals(before.sections.size(),result.sections.size());
        for(int n=0;n<3;n++){assertEquals(child(incoming,n).sourceReplyAt,child(result,n).sourceReplyAt);assertNotSame(child(before,n),child(result,n));assertEquals(child(before,n).blocks,child(result,n).blocks);}
        incoming.moreStatus="login";result=TiebaReplies.merge(before,incoming);absent(result);
    }
    @Test public void invalidTypesUnitsFutureAndDuplicatesRejectWholePayload()throws Exception{
        for(Object raw:new Object[]{"1793074275",1793074275.5,1793074275000L,-1,Long.MAX_VALUE}){Document doc=doc();JSONObject p=payload();p.getJSONArray("rows").getJSONArray(2).put(2,raw);TiebaReplyTimes.attach(doc,p,NOW);absent(doc);}
        Document doc=doc();JSONObject p=payload();p.getJSONArray("rows").put(p.getJSONArray("rows").getJSONArray(0));TiebaReplyTimes.attach(doc,p,NOW);absent(doc);
    }
    @Test public void unknownOrUnloadedParentDoesNotStampChild()throws Exception{
        Document doc=doc();doc.sections.removeIf(s->!s.sourcePostId.isEmpty());TiebaReplyTimes.attach(doc,payload(),NOW);absent(doc);
        doc=doc();for(Section s:doc.sections)if(!s.sourcePostId.isEmpty())s.sourcePostId="999";TiebaReplyTimes.attach(doc,payload(),NOW);absent(doc);
    }
    @Test public void parentAndSiblingNeverInheritChildTime()throws Exception{
        Document doc=doc();child(doc,0).nestedReply=false;child(doc,1).parentSectionId=doc.sections.get(0).id;
        TiebaReplyTimes.attach(doc,payload(),NOW);assertEquals(0,doc.sections.get(2).sourceReplyAt);assertEquals(0,doc.sections.get(3).sourceReplyAt);assertTrue(child(doc,2).sourceReplyAt>0);
    }
    @Test public void duplicateParsedIdentityAndPermissionAreRejected()throws Exception{
        Document doc=doc();doc.sections.addAll(new java.util.ArrayList<>(doc.sections.stream().filter(s->!s.sourcePostId.isEmpty()).toList()));TiebaReplyTimes.attach(doc,payload(),NOW);absent(doc);
        doc=doc();doc.loginRequired=true;TiebaReplyTimes.attach(doc,payload(),NOW);absent(doc);
        doc=doc();doc.sourceUnavailable=true;TiebaReplyTimes.attach(doc,payload(),NOW);absent(doc);
    }
    @Test public void metadataBoundAndMalformedShapeLeaveProseIntact()throws Exception{
        Document doc=doc();int blocks=doc.blocks.size();JSONObject p=payload();for(int n=0;n<129;n++)p.getJSONArray("rows").put(new JSONArray());TiebaReplyTimes.attach(doc,p,NOW);absent(doc);assertEquals(blocks,doc.blocks.size());
        doc=doc();p=payload();p.getJSONArray("rows").put("bad");TiebaReplyTimes.attach(doc,p,NOW);absent(doc);
    }
    @Test public void crossSourceAndTopLevelCacheMigrationRejectEvidence()throws Exception{
        Document doc=doc();TiebaReplyTimes.attach(doc,payload(),NOW);JSONObject saved=ReadingHistory.encode(doc);saved.put("url","https://www.v2ex.com/t/11139792603");absent(ReadingHistory.decode(saved));
        saved=ReadingHistory.encode(doc);saved.getJSONArray("sections").getJSONObject(2).put("nestedReply",false);assertEquals(0,ReadingHistory.decode(saved).sections.get(2).sourceReplyAt);
    }
}
