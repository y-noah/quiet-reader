package app.quietreader;

import static app.quietreader.Models.*;
import static org.junit.Assert.*;
import org.junit.Test;

public class AnswerBudgetTest {
    private Document document(int count){
        Document d=new Document();d.url="https://www.zhihu.com/question/123";d.title="合成问题";
        for(int i=0;i<count;i++)add(d,"answer-"+i,"已读回答"+i);
        return d;
    }
    private void add(Document d,String id,String text){Section s=new Section(id,"回答",true);s.blocks.add(new Block("p",text));d.sections.add(s);d.blocks.addAll(s.blocks);}
    @Test public void acceptedAppendAndExpansionKeepOriginalProvenance(){
        Document d=document(1);d.cachedAt=123;d.cachedSession="session";d.cachedNamespace="namespace";d.metadataHash="metadata";d.nextUrl="previous";d.loginRequired=true;d.sourceUnavailable=true;
        Document incoming=document(0);add(incoming,"answer-0","已读回答展开后的完整内容");add(incoming,"answer-1","新增回答");
        assertTrue(AnswerStream.mergeWithinBudget(d,incoming));assertEquals(2,AnswerStream.answers(d));assertEquals("已读回答展开后的完整内容",d.blocks.get(0).value);
        assertEquals(123,d.cachedAt);assertEquals("session",d.cachedSession);assertEquals("namespace",d.cachedNamespace);assertEquals("metadata",d.metadataHash);assertEquals("previous",d.nextUrl);
        Document copy=AnswerStream.copy(d);assertTrue(copy.loginRequired);assertTrue(copy.sourceUnavailable);
    }
    @Test public void rejectedBatchLeavesEveryPreviousAnswerAndCursorIntact(){
        Document d=document(599);Section first=d.sections.get(0);Block block=d.blocks.get(0);d.nextUrl="old-cursor";
        Document incoming=document(0);add(incoming,"answer-599","新增一");add(incoming,"answer-600","新增二");incoming.filteredSectionIds.add("filtered");incoming.videoUrl="https://example.test/video.mp4";
        assertFalse(AnswerStream.mergeWithinBudget(d,incoming));assertEquals("limit",d.moreStatus);assertEquals(599,d.sections.size());assertSame(first,d.sections.get(0));assertSame(block,d.blocks.get(0));assertEquals("old-cursor",d.nextUrl);assertTrue(d.filteredSectionIds.isEmpty());assertEquals("",d.videoUrl);
    }
    @Test public void exactLimitIsAcceptedAndFilteredIdentitiesParticipate(){
        Document d=document(598),incoming=document(0);add(incoming,"answer-598","最后回答");incoming.filteredSectionIds.add("filtered-1");
        assertTrue(AnswerStream.mergeWithinBudget(d,incoming));assertEquals(599,AnswerStream.answers(d));assertEquals("limit",d.moreStatus);assertEquals(1,d.filteredSectionIds.size());
        Document extra=document(0);extra.filteredSectionIds.add("filtered-2");assertFalse(AnswerStream.mergeWithinBudget(d,extra));assertEquals(1,d.filteredSectionIds.size());
    }
    @Test public void oversizedBodyAndEscapedSerializationRejectWholeBatch(){
        for(String text:new String[]{"中".repeat(1100000),"\u0001".repeat(400000)}){
            Document d=document(1),incoming=document(0);add(incoming,"answer-new",text);
            if(text.charAt(0)==1){Document proposed=AnswerStream.copy(d);AnswerStream.merge(proposed,incoming);assertTrue(DocumentSize.estimate(proposed)<ArticleDiskCache.MAX_ENTRY);assertFalse(DocumentSize.serializationFits(proposed,ArticleDiskCache.MAX_ENTRY));}
            assertFalse(AnswerStream.mergeWithinBudget(d,incoming));assertEquals(1,AnswerStream.answers(d));assertEquals("已读回答0",d.blocks.get(0).value);assertEquals("limit",d.moreStatus);
        }
    }
    @Test public void limitSurvivesDiskRoundTripAndOffersOnlyOneSourceAction()throws Exception{
        Document d=document(599),incoming=document(0);add(incoming,"answer-599","最后回答");assertTrue(AnswerStream.mergeWithinBudget(d,incoming));
        d=ReadingHistory.decode(ReadingHistory.encode(d));assertEquals("limit",d.moreStatus);assertEquals(600,AnswerStream.answers(d));
        String html=ReaderHtml.render(d,Source.ZHIHU,19),action="href='"+ReaderHtml.ACTION+"login'";
        assertEquals(1,html.split(java.util.regex.Pattern.quote(action),-1).length-1);assertFalse(html.contains("href='"+ReaderHtml.ACTION+"more'"));assertTrue(html.contains("已读内容保留"));
    }
}
