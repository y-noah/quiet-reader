package app.quietreader;

import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import static app.quietreader.ArticleProgressEvidence.*;
import app.quietreader.discovery.HotCandidate;

public final class TiebaProgressEvidenceTest {
    private static final long NOW=System.currentTimeMillis()/1000*1000,OLD=NOW-10000,SEEN=NOW-8000,NEW=NOW-2000;
    private static final String URL="https://tieba.baidu.com/p/11139792603",TOPIC="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123",SCOPE=HotCandidate.hash("tieba-test-scope");
    private static String prose(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    private static String child(String id,String text){return "<div class=pb-lzl-item data-id='"+id+"'><div class=comment-content><div class=pb-rich-text>"+text+"</div></div></div>";}
    private static Document document(String url,long acquired,String extra,String id,long at)throws Exception{
        String html="<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>"+prose(1)+"</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=154074790043><div class=comment-content><div class=pb-rich-text>"+prose(2)+"</div></div>"+child("154076372041",prose(4))+extra+"</div></div></div></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,url);doc.cachedAt=acquired;
        JSONArray rows=new JSONArray().put(new JSONArray().put("154074790043").put("154076372041").put((OLD-1000)/1000));
        if(!id.isEmpty()&&at>0)rows.put(new JSONArray().put("154074790043").put(id).put(at/1000));
        TiebaReplyTimes.attach(doc,new JSONObject().put("url",url).put("rows",rows),NOW);return doc;
    }
    private static Document beforeDoc()throws Exception{return document(URL,OLD,"","",0);}
    private static Document afterDoc(String id,long at,String text)throws Exception{return document(URL,NOW,child(id,text),id,at);}
    private static Snapshot snap(Document doc){Snapshot value=capture(Source.TIEBA,doc,SCOPE,NOW);assertNotNull(value);return value;}
    private static Document topic(Document doc){return TiebaTopic.attach(TOPIC,List.of(new Item(Source.TIEBA,"合成帖子",doc.url,"")),doc,"a".repeat(64),doc.cachedAt);}
    @Test public void parsedChildNewProgressAndHashOnlyRoundtrip()throws Exception{
        Snapshot before=snap(beforeDoc()),after=snap(afterDoc("154076383836",NEW,"【更新】"+prose(3)));
        assertEquals(NEW,verifiedProgress(Snapshot.read(before.json(),NOW),Snapshot.read(after.json(),NOW),SEEN,NOW));
        assertEquals(HotCandidate.hash(Source.TIEBA.name()+"\n"+HotCandidate.canonical(URL)),after.identity);
        String raw=after.json().toString();assertFalse(raw.contains(URL));assertFalse(raw.contains("154076383836"));assertFalse(raw.contains(prose(3)));
    }
    @Test public void topicCandidateKeepsIdentityAndActualPostContinuity()throws Exception{
        Snapshot before=snap(topic(beforeDoc())),after=snap(topic(afterDoc("154076383836",NEW,"【更新】"+prose(3))));
        assertEquals(HotCandidate.hash(Source.TIEBA.name()+"\n"+HotCandidate.canonical(TOPIC)),after.identity);assertEquals(NEW,verifiedProgress(before,after,SEEN,NOW));
        Document changed=document("https://tieba.baidu.com/p/11139792604",NOW,child("154076383836","【更新】"+prose(3)),"154076383836",NEW);
        assertEquals(0,verifiedProgress(before,snap(topic(changed)),SEEN,NOW));
        Document changedAccount=topic(afterDoc("154076383836",NEW,"【更新】"+prose(3)));
        changedAccount.topic=new TiebaTopic.Resolved(TOPIC,URL,"b".repeat(64),NOW,changedAccount.topic.choices);
        assertEquals(0,verifiedProgress(before,snap(changedAccount),SEEN,NOW));
    }
    @Test public void oldMissingUnmarkedAndCopiedChildrenCannotPromote()throws Exception{
        for(Document doc:new Document[]{afterDoc("154076383836",OLD-3000,"【更新】"+prose(3)),afterDoc("154076383836",0,"【更新】"+prose(3)),afterDoc("154076383836",NEW,prose(3)),afterDoc("154076383836",NEW,"【更新】"+prose(4))})assertEquals(0,verifiedProgress(snap(beforeDoc()),snap(doc),SEEN,NOW));
    }
    @Test public void knownChildEditAndRecentReadingCannotPromote()throws Exception{
        Document edited=beforeDoc();edited.cachedAt=NOW;
        Section child=edited.sections.stream().filter(s->s.nestedReply).findFirst().orElseThrow();child.blocks.clear();child.blocks.add(new Block("text","【更新】"+prose(3)));child.sourceReplyAt=NEW;
        assertEquals(0,verifiedProgress(snap(beforeDoc()),snap(edited),SEEN,NOW));
        assertEquals(0,verifiedProgress(snap(beforeDoc()),snap(afterDoc("154076383836",NEW,"【更新】"+prose(3))),NOW-1000,NOW));
    }
    @Test public void parentTopLevelAndUnknownTimestampCannotBorrowTime()throws Exception{
        Document doc=beforeDoc();doc.cachedAt=NOW;Section floor=doc.sections.stream().filter(s->!s.sourcePostId.isEmpty()).findFirst().orElseThrow();floor.blocks.clear();floor.blocks.add(new Block("text","【更新】"+prose(3)));floor.sourceReplyAt=NEW;floor.sourceReplyEvidence=TiebaReplyTimes.VERSION;
        Snapshot after=snap(doc);assertEquals(OLD-1000,after.modifiedAt);assertTrue(after.marked.isEmpty());assertEquals(0,verifiedProgress(snap(beforeDoc()),after,SEEN,NOW));
    }
    @Test public void missingParentDuplicateAndWrongParentIdentityAreRejected()throws Exception{
        for(int n=0;n<3;n++){Document doc=afterDoc("154076383836",NEW,"【更新】"+prose(3));Section floor=doc.sections.stream().filter(s->!s.sourcePostId.isEmpty()).findFirst().orElseThrow();if(n==0)doc.sections.remove(floor);if(n==1)doc.sections.add(doc.sections.get(0));if(n==2)floor.sourcePostId="999";assertNull(freeze(Source.TIEBA,doc,SCOPE,NOW));}
    }
    @Test public void warningPermissionRevisionAndMissingMainAreRejected()throws Exception{
        for(int n=0;n<7;n++){Document doc=afterDoc("154076383836",NEW,"【更新】"+prose(3));if(n==0)doc.loginRequired=true;if(n==1)doc.sourceUnavailable=true;if(n==2)doc.moreStatus="login";if(n==3)doc.moreStatus="error";if(n==4)doc.notice="尚未完整读取";if(n==5)doc.tiebaReplyRevision=0;if(n==6)doc.sections.remove(0);assertNull(freeze(Source.TIEBA,doc,SCOPE,NOW));}
    }
    @Test public void secondPageAndLateWindowCannotAdvanceAcquisitionClock()throws Exception{
        Document doc=afterDoc("154076383836",NEW,"【更新】"+prose(3));doc.cachedAt=OLD;assertNull(freeze(Source.TIEBA,doc,SCOPE,NOW));
        doc=afterDoc("154076383836",NEW,"【更新】"+prose(3));doc.url=URL+"?pn=2";assertNull(freeze(Source.TIEBA,doc,SCOPE,NOW));
        doc=afterDoc("154076383836",NEW,"【更新】"+prose(3));doc.discussionPage=2;assertNull(freeze(Source.TIEBA,doc,SCOPE,NOW));
    }
    @Test public void scopeMainChangesAndCrossUrlFollowupCannotBypass()throws Exception{
        Document doc=afterDoc("154076383836",NEW,"【更新】"+prose(3));Snapshot before=snap(beforeDoc());
        assertEquals(0,verifiedProgress(before,capture(Source.TIEBA,doc,HotCandidate.hash("other-scope"),NOW),SEEN,NOW));
        Snapshot after=snap(doc);assertEquals(0,verifiedFollowup(before,after,SEEN,NOW));
        doc.sections.get(0).blocks.clear();doc.sections.get(0).blocks.add(new Block("text",prose(9)));assertEquals(0,verifiedProgress(before,snap(doc),SEEN,NOW));
    }
    @Test public void frozenInputAndResolvedHistoryRetainExactDecision()throws Exception{
        Document doc=topic(afterDoc("154076383836",NEW,"【更新】"+prose(3)));Snapshot before=snap(topic(beforeDoc()));
        Input input=freeze(Source.TIEBA,doc,SCOPE,NOW);assertNotNull(input);
        Document restored=ReadingHistory.restore(new JSONObject().put("document",ReadingHistory.encode(doc)).put("bodyAt",doc.cachedAt).put("session","test-session").put("namespace","test-namespace"));assertEquals(NEW,verifiedProgress(before,snap(restored),SEEN,NOW));
        doc.sections.clear();assertEquals(NEW,verifiedProgress(before,input.finish(NOW),SEEN,NOW));
    }
    @Test public void missingMalformedAndMismatchedThreadVersionRejectPersistedEvidence()throws Exception{
        Snapshot after=snap(afterDoc("154076383836",NEW,"【更新】"+prose(3)));
        for(Object value:new Object[]{JSONObject.NULL,"",123,"bad"}){JSONObject json=after.json();json.getJSONObject("discussion").put("thread",value);assertNull(Snapshot.read(json,NOW));}
        JSONObject json=after.json();json.getJSONObject("discussion").remove("thread");assertNull(Snapshot.read(json,NOW));
        json=after.json();json.put("version",DiscussionProgressEvidence.VERSION);assertNull(Snapshot.read(json,NOW));
    }
}
