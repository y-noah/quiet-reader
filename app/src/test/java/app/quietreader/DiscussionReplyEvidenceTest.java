package app.quietreader;

import app.quietreader.Models.*;
import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import static org.junit.Assert.*;

public class DiscussionReplyEvidenceTest {
    private static final String URL="https://www.v2ex.com/t/1251768",TIME="2026-10-26 09:37:39 +08:00";
    private static final long AT=Instant.parse("2026-10-26T01:37:39Z").toEpochMilli();
    private static String reply(String id,String metadata,String content){return "<div class=cell id='"+id+"'>"+metadata+"<div class=reply_content>"+content+"</div></div>";}
    private static String stamp(String time){return "<span class=ago title='"+time+"'>2 days ago</span>";}
    private static Document parse(String html){return SourceParser.article(Source.V2EX,html,URL);}
    private static void absent(Document doc){assertFalse(doc.sections.isEmpty());for(Section s:doc.sections){assertEquals(0,s.sourceReplyAt);assertEquals("",s.sourceReplyEvidence);}}
    @Test public void capturedPublicMetadataSurvivesUnifiedParsingAndHistory()throws Exception{
        JSONObject fixture;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/v2ex-reply-metadata-20261028.json")){assertNotNull(in);fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
        JSONArray rows=fixture.getJSONArray("replies");StringBuilder html=new StringBuilder("<div class=topic_content>受控主帖文字</div>");
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);html.append(reply(row.getString("id"),stamp(row.getString("title")),"受控测试回复，不是捕获的正文"));}
        Document doc=parse(html.toString());assertEquals(4,doc.sections.size());assertEquals(0,doc.sections.get(0).sourceReplyAt);
        assertEquals("v2ex-r_18219649",doc.sections.get(1).id);assertEquals(AT,doc.sections.get(1).sourceReplyAt);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));
        for(int i=1;i<4;i++){assertEquals(doc.sections.get(i).sourceReplyAt,restored.sections.get(i).sourceReplyAt);assertEquals(DiscussionReplyEvidence.VERSION,restored.sections.get(i).sourceReplyEvidence);}
    }
    @Test public void relativeTextCannotChangeAbsoluteCreationTime(){
        String html=reply("r_12",stamp(TIME),"同一段回复");assertEquals(AT,parse(html).sections.get(0).sourceReplyAt);
        assertEquals(AT,parse(html.replace("2 days ago","刚刚")).sections.get(0).sourceReplyAt);
    }
    @Test public void missingOffsetRelativeOrInvalidCalendarNeverInventTime(){
        for(String time:new String[]{"2026-10-26 09:37:39","2 days ago","2026-02-30 09:37:39 +08:00","2026-10-26 25:37:39 +08:00","2099-10-26 09:37:39 +08:00","1999-10-26 09:37:39 +08:00"})absent(parse(reply("r_12",stamp(time),"回复文字")));
    }
    @Test public void timestampsInsideReplyBodyDoNotTimestampReply(){absent(parse(reply("r_12","","回复文字"+stamp(TIME))));}
    @Test public void nestedCardTimeAndPageHeaderAreNotReplyTime(){absent(parse(stamp(TIME)+reply("r_12","<div class=cell>"+stamp(TIME)+"</div>","回复文字")));}
    @Test public void conflictingOrRepeatedTimeMetadataIsRejected(){absent(parse(reply("r_12",stamp(TIME)+stamp(TIME),"回复文字")));}
    @Test public void fallbackAndInvalidReplyIdsCannotBecomeProgressEvidence(){for(String id:new String[]{"","r_0","r_01","r_bad"})absent(parse(reply(id,stamp(TIME),"回复文字")));}
    @Test public void duplicatedIdsAreAmbiguousEvenOnNonCellElement(){absent(parse(reply("r_12",stamp(TIME),"回复文字")+"<div id=r_12></div>"));}
    @Test public void changingBodyDoesNotChangeStableIdOrCreationTime(){
        Section before=parse(reply("r_12",stamp(TIME),"原回复内容")).sections.get(0),after=parse(reply("r_12",stamp(TIME),"修改之后的回复内容")).sections.get(0);
        assertEquals(before.id,after.id);assertEquals(AT,after.sourceReplyAt);
    }
    @Test public void sourceAndThreadUrlMustMatch(){
        String html=reply("r_12",stamp(TIME),"回复文字");
        for(String url:new String[]{"https://www.v2ex.com/?tab=hot","https://example.com/t/1251768"})absent(SourceParser.article(Source.V2EX,html,url));
    }
    @Test public void oldAndMalformedCachedEvidenceIsNotTrusted()throws Exception{
        JSONObject encoded=ReadingHistory.encode(parse(reply("r_12",stamp(TIME),"回复文字"))),part=encoded.getJSONArray("sections").getJSONObject(0);
        part.remove("sourceReplyEvidence");absent(ReadingHistory.decode(encoded));
        for(Object time:new Object[]{Long.toString(AT),AT+0.5,Long.MAX_VALUE,-1}){part.put("sourceReplyEvidence",new JSONObject().put("version",DiscussionReplyEvidence.VERSION).put("createdAt",time));absent(ReadingHistory.decode(encoded));}
        part.put("sourceReplyEvidence",new JSONObject().put("version","unknown").put("createdAt",AT));absent(ReadingHistory.decode(encoded));
    }
    @Test public void cacheCannotReattachReplyTimeToMainPostOrOtherSource()throws Exception{
        JSONObject encoded=ReadingHistory.encode(parse(reply("r_12",stamp(TIME),"回复文字"))),part=encoded.getJSONArray("sections").getJSONObject(0);
        encoded.put("url","https://tieba.baidu.com/p/1251768");absent(ReadingHistory.decode(encoded));
        encoded.put("url",URL);part.put("id","v2ex-main");absent(ReadingHistory.decode(encoded));
        part.put("id","v2ex-r_12").put("nestedReply",true);absent(ReadingHistory.decode(encoded));
    }
}
