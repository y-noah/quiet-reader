package app.quietreader;

import app.quietreader.Models.*;
import app.quietreader.discovery.HotCandidate;
import org.json.*;
import org.junit.Test;
import java.util.Random;
import static org.junit.Assert.*;
import static app.quietreader.ArticleProgressEvidence.*;

public class HupuProgressEvidenceTest {
    private static final long NOW=System.currentTimeMillis(),OLD=NOW-10000,SEEN=NOW-8000,NEW=NOW-2000;
    private static final String URL="https://bbs.hupu.com/643330244.html",SCOPE=HotCandidate.hash("test-hupu-scope");
    private static String prose(int seed){Random r=new Random(seed);StringBuilder s=new StringBuilder();for(int n=0;n<300;n++)s.append((char)(0x4e00+r.nextInt(3000)));return s.toString();}
    private static JSONObject reply(String id,Object at,String text)throws Exception{return new JSONObject().put("pid",id).put("createdAt",at).put("content","<p>"+text+"</p>");}
    static Document document(String url,long acquired,JSONArray rows)throws Exception{
        String id=HupuDiscussion.threadId(url);
        JSONObject thread=new JSONObject().put("tid",id).put("url",java.net.URI.create(url).getPath()).put("title","合成中文讨论").put("content","<p>"+prose(1)+"</p>").put("status",0).put("visibleRange","ALL_SEE");
        JSONObject detail=new JSONObject().put("thread",thread).put("visibleRange","ALL_SEE").put("lights",new JSONArray()).put("replies",new JSONObject().put("current",HupuDiscussion.page(url)).put("list",rows));
        JSONObject state=new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("detail",detail)));
        Document doc=SourceParser.article(Source.HUPU,"<script id=__NEXT_DATA__ type=application/json>"+state+"</script>",url);doc.cachedAt=acquired;return doc;
    }
    private static Document doc(long acquired,String id,Object at,String text)throws Exception{return document(URL,acquired,new JSONArray().put(reply("1",OLD-1000,prose(2))).put(reply(id,at,text)));}
    private static Snapshot snap(Document doc){Snapshot s=capture(Source.HUPU,doc,SCOPE,NOW);assertNotNull(s);return s;}
    private static Snapshot before()throws Exception{return snap(document(URL,OLD,new JSONArray().put(reply("1",OLD-1000,prose(2)))));}
    @Test public void parsedNewMarkedReplyCanReturnWithHashOnlyRoundtrip()throws Exception{
        Snapshot after=snap(doc(NOW,"2",NEW,"【更新】"+prose(3)));
        assertEquals(NEW,verifiedProgress(Snapshot.read(before().json(),NOW),Snapshot.read(after.json(),NOW),SEEN,NOW));
        assertEquals(HotCandidate.hash(Source.HUPU.name()+"\n"+HotCandidate.canonical(URL)),after.identity);
        assertFalse(after.json().toString().contains(prose(3)));assertFalse(after.json().toString().contains("hupu-reply-"));
    }
    @Test public void historicalUnknownUnmarkedAndCopiedRepliesDoNotPromote()throws Exception{
        for(Document after:new Document[]{doc(NOW,"2",OLD-3000,"【更新】"+prose(3)),doc(NOW,"2",JSONObject.NULL,"【更新】"+prose(3)),doc(NOW,"2",NEW,prose(3)),doc(NOW,"2",NEW,"【更新】"+prose(2))})assertEquals(0,verifiedProgress(before(),snap(after),SEEN,NOW));
    }
    @Test public void editingKnownReplyCannotBecomeNewDiscussion()throws Exception{
        Document after=document(URL,NOW,new JSONArray().put(reply("1",NEW,"【更新】"+prose(3))));
        assertEquals(0,verifiedProgress(before(),snap(after),SEEN,NOW));
    }
    @Test public void warningPermissionAndOldBodyRevisionRemainClosed()throws Exception{
        for(int n=0;n<4;n++){Document doc=doc(NOW,"2",NEW,"【更新】"+prose(3));if(n==0)doc.notice="需要登录";if(n==1)doc.loginRequired=true;if(n==2)doc.sourceUnavailable=true;if(n==3)doc.hupuBodyRevision=2;assertNull(freeze(Source.HUPU,doc,SCOPE,NOW));}
    }
    @Test public void secondPageWithoutMainAndCrossPlatformInputsAreRejected()throws Exception{
        Document second=document("https://bbs.hupu.com/643330244-2.html",NOW,new JSONArray().put(reply("2",NEW,"【更新】"+prose(3))));
        assertNull(freeze(Source.HUPU,second,SCOPE,NOW));assertNull(freeze(Source.V2EX,doc(NOW,"2",NEW,"【更新】"+prose(3)),SCOPE,NOW));
    }
    @Test public void accountReadingAndDifferentThreadPreventPromotion()throws Exception{
        Document doc=doc(NOW,"2",NEW,"【更新】"+prose(3));Snapshot after=snap(doc);
        assertEquals(0,verifiedProgress(before(),capture(Source.HUPU,doc,HotCandidate.hash("other-account"),NOW),SEEN,NOW));
        assertEquals(0,verifiedProgress(before(),after,NOW-1000,NOW));
        Snapshot other=snap(document("https://bbs.hupu.com/643330245.html",NOW,new JSONArray().put(reply("2",NEW,"【更新】"+prose(3)))));
        assertEquals(0,verifiedProgress(before(),other,SEEN,NOW));assertEquals(0,verifiedFollowup(before(),other,SEEN,NOW));
    }
    @Test public void sourceTimestampBeyondAcquisitionRejectsMixedPageSnapshot()throws Exception{assertNull(freeze(Source.HUPU,doc(OLD,"2",NEW,"【更新】"+prose(3)),SCOPE,NOW));}
    @Test public void mainOnlyBaselineAndFrozenInputWork()throws Exception{
        Snapshot before=snap(document(URL,OLD,new JSONArray()));Document doc=doc(NOW,"2",NEW,"【更新】"+prose(3));Input frozen=freeze(Source.HUPU,doc,SCOPE,NOW);assertNotNull(frozen);doc.sections.clear();assertEquals(NEW,verifiedProgress(before,frozen.finish(NOW),SEEN,NOW));
    }
}