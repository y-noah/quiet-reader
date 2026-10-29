package app.quietreader;

import app.quietreader.Models.*;
import app.quietreader.discovery.HotCandidate;
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.ArticleProgressEvidence.*;

public class DiscussionProgressEvidenceTest {
    private static final long NOW=System.currentTimeMillis()/1000*1000,OLD=NOW-10000,SEEN=NOW-8000,NEW=NOW-2000;
    private static final String URL="https://www.v2ex.com/t/1251768",SCOPE=HotCandidate.hash("synthetic-scope");
    private static String prose(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    private static String reply(int id,long at,String text){String stamp=at==0?"":DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx").format(Instant.ofEpochMilli(at).atOffset(ZoneOffset.ofHours(8)));return "<div class=cell id='r_"+id+"'><span class=ago title='"+stamp+"'>刚刚</span><div class=reply_content>"+text+"</div></div>";}
    private static Document doc(long acquired,String extra){Document doc=SourceParser.article(Source.V2EX,"<div class=topic_content>"+prose(1)+"</div>"+extra,URL);doc.cachedAt=acquired;return doc;}
    private static Snapshot snap(Document doc){Snapshot result=capture(Source.V2EX,doc,SCOPE,NOW);assertNotNull(result);assertNotNull(result.discussion);return result;}
    private static Snapshot before(){return snap(doc(OLD,reply(1,OLD-1000,prose(2))));}
    private static Snapshot after(String extra){return snap(doc(NOW,reply(1,OLD-1000,prose(2))+extra));}
    @Test public void newMarkedSubstantialReplyCanReenterSameThread(){assertEquals(NEW,verifiedProgress(before(),after(reply(2,NEW,"【最新进展】"+prose(3))),SEEN,NOW));}
    @Test public void mainOnlyBaselineCanLaterReceiveFirstSubstantialReply()throws Exception{
        Snapshot before=snap(doc(OLD,""));assertEquals(0,before.modifiedAt);assertNotNull(Snapshot.read(before.json(),NOW));
        assertEquals(NEW,verifiedProgress(before,snap(doc(NOW,reply(2,NEW,"【更新】"+prose(3)))),SEEN,NOW));
    }
    @Test public void historicalPaginationCannotBecomeNewProgress(){assertEquals(0,verifiedProgress(before(),after(reply(8,OLD-3000,"【最新进展】"+prose(3))),SEEN,NOW));}
    @Test public void unmarkedNewReplyDoesNotClaimImportantProgress(){assertEquals(0,verifiedProgress(before(),after(reply(2,NEW,prose(3))),SEEN,NOW));}
    @Test public void editingKnownReplyCannotBecomeNewReplyEvenWithNewTimestamp(){assertEquals(0,verifiedProgress(before(),snap(doc(NOW,reply(1,NEW,"【更新】"+prose(3)))),SEEN,NOW));}
    @Test public void copiedBodyUnderDifferentIdIsNotSubstantialNewInformation(){assertEquals(0,verifiedProgress(before(),after(reply(2,NEW,"【更新】"+prose(2))),SEEN,NOW));}
    @Test public void tinyEditAndMarkerCannotManufactureNovelty(){assertEquals(0,verifiedProgress(before(),after(reply(2,NEW,"【更新】"+prose(2).substring(0,150)+"字"+prose(2).substring(150))),SEEN,NOW));}
    @Test public void aDifferentMainBodyPreventsDiscussionContinuity(){Document after=doc(NOW,reply(2,NEW,"【更新】"+prose(3)));after.sections.get(0).blocks.clear();after.sections.get(0).blocks.add(new Block("text",prose(9)));assertEquals(0,verifiedProgress(before(),snap(after),SEEN,NOW));}
    @Test public void missingCreationTimeDoesNotUseAcquisitionTime(){Snapshot after=after(reply(2,0,"【更新】"+prose(3)));assertEquals(0,verifiedProgress(before(),after,SEEN,NOW));assertTrue(after.marked.isEmpty());}
    @Test public void latestEventReadingTimeStillWinsOverReplyTime(){assertEquals(0,verifiedProgress(before(),after(reply(2,NEW,"【更新】"+prose(3))),NOW-1000,NOW));}
    @Test public void accountAndThreadIdentityAreRequired(){Snapshot after=after(reply(2,NEW,"【更新】"+prose(3)));Snapshot other=new Snapshot(after.identity,HotCandidate.hash("another-scope"),after.acquiredAt,after.modifiedAt,after.body,after.marked,after.discussion);assertEquals(0,verifiedProgress(before(),other,SEEN,NOW));other=new Snapshot(HotCandidate.hash("another-thread"),after.scope,after.acquiredAt,after.modifiedAt,after.body,after.marked,after.discussion);assertEquals(0,verifiedProgress(before(),other,SEEN,NOW));}
    @Test public void replyTimestampLaterThanAcquisitionRejectsInconsistentDocument(){Document doc=doc(OLD,reply(2,NEW,"【更新】"+prose(3)));assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));}
    @Test public void frozenInputDoesNotObserveLaterSectionMutation(){Document doc=doc(NOW,reply(2,NEW,"【更新】"+prose(3)));Input frozen=freeze(Source.V2EX,doc,SCOPE,NOW);assertNotNull(frozen);doc.sections.get(1).blocks.clear();doc.sections.get(1).sourceReplyAt=0;doc.sections.clear();assertEquals(NEW,verifiedProgress(before(),frozen.finish(NOW),SEEN,NOW));}
    @Test public void hashOnlyRoundtripKeepsDiscussionDecision()throws Exception{Snapshot after=after(reply(2,NEW,"【更新】"+prose(3)));JSONObject json=after.json();String raw=json.toString();assertFalse(raw.contains("v2ex-r_"));assertFalse(raw.contains(prose(3)));assertEquals(NEW,verifiedProgress(Snapshot.read(before().json(),NOW),Snapshot.read(json,NOW),SEEN,NOW));assertTrue(raw.length()<ArticleProgressHistory.MAX_PAYLOAD);}
    @Test public void malformedOrConflictingPersistedReplyTimesAreRejected()throws Exception{
        for(Object value:new Object[]{"123",NEW+0.5,Long.MAX_VALUE,-1}){JSONObject json=after(reply(2,NEW,"【更新】"+prose(3))).json();json.getJSONObject("discussion").getJSONArray("replies").getJSONObject(1).put("at",value);assertNull(Snapshot.read(json,NOW));}
    }
    @Test public void missingVersionDuplicateIdentityAndUnionMismatchAreRejected()throws Exception{
        JSONObject json=after(reply(2,NEW,"【更新】"+prose(3))).json();json.getJSONObject("discussion").put("version","unknown");assertNull(Snapshot.read(json,NOW));
        json=after(reply(2,NEW,"【更新】"+prose(3))).json();JSONArray entries=json.getJSONObject("discussion").getJSONArray("replies");entries.put(entries.getJSONObject(0));assertNull(Snapshot.read(json,NOW));
        json=after(reply(2,NEW,"【更新】"+prose(3))).json();json.getJSONObject("discussion").put("main",new JSONArray());assertNull(Snapshot.read(json,NOW));
    }
    @Test public void noDiscussionCrossUrlFollowupBypass(){Snapshot after=after(reply(2,NEW,"【更新】"+prose(3)));Snapshot other=new Snapshot(HotCandidate.hash("different-url"),SCOPE,after.acquiredAt,after.modifiedAt,after.body,after.marked,after.discussion);assertEquals(0,verifiedFollowup(before(),other,SEEN,NOW));}
    @Test public void serializedDiscussionCannotSilentlyDegradeToNewsEvidence()throws Exception{JSONObject json=after(reply(2,NEW,"【更新】"+prose(3))).json();json.remove("discussion");assertNull(Snapshot.read(json,NOW));json=after(reply(2,NEW,"【更新】"+prose(3))).json();json.put("version",VERSION);assertNull(Snapshot.read(json,NOW));}
    @Test public void cumulativeHashBudgetStopsBeforeOversizeSerialization(){StringBuilder replies=new StringBuilder();for(int n=2;n<36;n++)replies.append(reply(n,NEW,"【更新】"+prose(n)));Document doc=doc(NOW,replies.toString());Input input=freeze(Source.V2EX,doc,SCOPE,NOW);assertNotNull(input);assertNull(input.finish(NOW));}
    @Test public void unknownIdOrDuplicateSectionCannotSupplyProgress(){Document doc=doc(NOW,reply(2,NEW,"【更新】"+prose(3)));doc.sections.add(doc.sections.get(1));assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));doc=doc(NOW,"");Section fallback=new Section("v2ex-reply-fallback","回复",false);fallback.blocks.add(new Block("text",prose(2)));doc.sections.add(fallback);assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));}
    @Test public void boundedInputRejectsOversizeInsteadOfTruncatingEvidence(){Document doc=doc(NOW,"");doc.sections.get(0).blocks.add(new Block("text","字".repeat(MAX_CHARS+1)));assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));doc=doc(NOW,"");for(int n=1;n<=DiscussionProgressEvidence.MAX_SECTIONS;n++)doc.sections.add(new Section("v2ex-r_"+n,"回复",false));assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));}
    @Test public void unavailableLoginAndOtherPlatformsRemainExcluded(){Document doc=doc(NOW,reply(2,NEW,"【更新】"+prose(3)));doc.loginRequired=true;assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));doc.loginRequired=false;doc.sourceUnavailable=true;assertNull(freeze(Source.V2EX,doc,SCOPE,NOW));doc.sourceUnavailable=false;assertNull(freeze(Source.TIEBA,doc,SCOPE,NOW));}
}
