package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import java.util.*;

public class DiscussionPagesTest {
    @Test public void tiebaNestedReplyKeepsItsOwnNamespaceAndDeduplicatesAcrossPages()throws Exception{
        String main="<div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_1>主帖</div></div>";
        String nested="<div class=l_post><div class=d_post_content id=post_content_2>外层回复</div><li class=lzl_single_post data-field='{\"spid\":2}'><a class=j_user_card>甲</a><span class=lzl_content_main>嵌套回复</span></li></div>";
        Document first=SourceParser.article(Source.TIEBA,"<div id=j_p_postlist>"+main+nested+"</div><a rel=next href='?pn=2'>下一页</a>","https://tieba.baidu.com/p/123");
        String added="<li class=lzl_single_post data-field='{\"spid\":3}'><a class=j_user_card>乙</a><span class=lzl_content_main>嵌套回复</span></li>";
        Document next=SourceParser.article(Source.TIEBA,"<div id=j_p_postlist>"+nested+added+"</div>",first.nextUrl);
        first=ReadingHistory.decode(ReadingHistory.encode(first));next=ReadingHistory.decode(ReadingHistory.encode(next));
        Document merged=DiscussionPages.merge(Source.TIEBA,first,next,first.nextUrl);assertEquals(4,merged.sections.size());assertEquals("正文 · 楼主",merged.sections.get(0).label);assertEquals("回复 · 乙",merged.sections.get(3).label);assertEquals(3,first.sections.size());
    }
    private String reply(int id,String author,String text){return "<div class=cell id=r_"+id+"><strong><a href='/member/"+author+"'>"+author+"</a></strong><div class=reply_content>"+text+"</div></div>";}
    private Document v2(int page,String replies,String next){return SourceParser.article(Source.V2EX,"<h1>合成讨论</h1><div class=topic_content>始终保留的主帖正文</div>"+replies+(next.isEmpty()?"":"<a class=page_normal href='"+next+"'>下一页</a>"),"https://www.v2ex.com/t/123?p="+page);}
    @Test public void forumRepliesUseSourceIdsAndKeepDifferentAuthorsWithIdenticalText(){
        Document doc=v2(1,reply(10,"甲","相同回复")+reply(11,"乙","相同回复"),"?p=2");
        assertEquals(3,doc.sections.size());assertEquals("v2ex-r_10",doc.sections.get(1).id);assertEquals("回复 · 甲",doc.sections.get(1).label);assertEquals("回复 · 乙",doc.sections.get(2).label);assertFalse(ReaderHtml.discussion(doc.sections.get(0)));
        Document fallback=SourceParser.article(Source.V2EX,"<div class=cell><div class=reply_content>相同回复</div></div><div class=cell><div class=reply_content>相同回复</div></div>",doc.url);assertEquals(2,fallback.sections.size());assertNotEquals(fallback.sections.get(0).id,fallback.sections.get(1).id);
    }
    @Test public void onlyForwardPagesOfTheSamePostCanBecomeTheNextCursor(){
        String html="<div class=topic_content>主帖</div><nav><a class=page_normal href='?p=4'>4</a><a class=page_normal href='?p=2'>2</a></nav><a rel=next href='/t/999?p=2'>下一页</a><a rel=next href='https://evil.test/t/123?p=2'>下一页</a>";
        Document doc=SourceParser.article(Source.V2EX,html,"https://www.v2ex.com/t/123?p=1");assertEquals("https://www.v2ex.com/t/123?p=2",doc.nextUrl);assertTrue(DiscussionPages.next(Source.V2EX,doc));
        doc.discussionPage=2;assertFalse(DiscussionPages.next(Source.V2EX,doc));
        for(String query:List.of("?p=0","?p=2&p=3","?p=-1","?p=1000000","?p=bad"))assertEquals(-1,DiscussionPages.page(Source.V2EX,"https://www.v2ex.com/t/123"+query));
        assertTrue(DiscussionPages.samePage(Source.V2EX,"https://www.v2ex.com/t/123?p=2","https://www.v2ex.com/t/123?p=2#reply100"));assertFalse(DiscussionPages.samePage(Source.V2EX,"https://www.v2ex.com/t/123?p=2","https://www.v2ex.com/t/123?p=1"));assertFalse(DiscussionPages.samePage(Source.V2EX,"https://www.v2ex.com/t/123?p=2","https://www.v2ex.com/t/999?p=2"));
    }
    @Test public void mainSupplementsHaveIndependentIdsAndNeverBecomeReplies(){
        Document doc=SourceParser.article(Source.V2EX,"<div class=header><small class=gray><a href='/member/楼主'>楼主</a></small></div><div class=topic_content>主帖</div><div class=subtle><div class=topic_content>补充内容</div></div>"+reply(10,"甲","回复"),"https://www.v2ex.com/t/123");
        assertEquals("楼主",doc.byline);assertEquals(3,doc.sections.size());assertNotEquals(doc.sections.get(0).id,doc.sections.get(1).id);assertFalse(ReaderHtml.discussion(doc.sections.get(1)));assertTrue(ReaderHtml.discussion(doc.sections.get(2)));
    }
    @Test public void tiebaPagerOutsidePostListAndPageTwoNeverPromotesFirstReplyToMain(){
        String html="<div id=j_p_postlist><div class=d_post_content id=post_content_9>后续页回复</div></div><div class=l_pager><a href='?pn=1'>上一页</a><a href='?pn=3'>下一页</a></div>";
        Document doc=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123?pn=2");assertEquals(2,doc.discussionPage);assertEquals("https://tieba.baidu.com/p/123?pn=3",doc.nextUrl);assertTrue(ReaderHtml.discussion(doc.sections.get(0)));
    }
    @Test public void tiebaSourcePostIdentitySurvivesSwitchFromDomIdToDataField(){
        String first="<div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_1>主帖</div></div><div class=l_post><a class=d_name>甲</a><div class=d_post_content id=post_content_2>相同回复</div></div></div><a rel=next href='?pn=2'>下一页</a>";
        String second="<div id=j_p_postlist><div class=l_post data-field='{\"content\":{\"post_id\":2,\"post_no\":2}}'><a class=d_name>甲</a><div class=d_post_content>相同回复</div></div><div class=l_post data-field='{\"content\":{\"post_id\":3,\"post_no\":3}}'><a class=d_name>乙</a><div class=d_post_content>相同回复</div></div></div>";
        Document a=SourceParser.article(Source.TIEBA,first,"https://tieba.baidu.com/p/123"),b=SourceParser.article(Source.TIEBA,second,"https://tieba.baidu.com/p/123?pn=2");
        assertEquals(a.sections.get(1).id,b.sections.get(0).id);
        Document merged=DiscussionPages.merge(Source.TIEBA,a,b,a.nextUrl);assertEquals(3,merged.sections.size());assertEquals("正文 · 楼主",merged.sections.get(0).label);assertEquals("回复 · 乙",merged.sections.get(2).label);
    }
    @Test public void oldNumericReplyCacheDoesNotDuplicateWhenNextPageUsesCanonicalIds()throws Exception{
        String html="<div id=j_p_postlist><div class=l_post><div class=d_post_content id=post_content_1>主帖</div></div><div class=l_post><a class=d_name>甲</a><div class=d_post_content id=post_content_2>相同回复</div></div></div><a rel=next href='?pn=2'>下一页</a>";
        Document first=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/123");Section previous=first.sections.get(1),legacy=new Section("tieba-post-"+app.quietreader.discovery.HotCandidate.hash("2").substring(0,24),previous.label,false);legacy.blocks.addAll(previous.blocks);first.sections.set(1,legacy);first=ReadingHistory.decode(ReadingHistory.encode(first));
        Document next=SourceParser.article(Source.TIEBA,"<div id=j_p_postlist><div class=l_post><a class=d_name>甲</a><div class=d_post_content id=post_content_2>相同回复</div></div><div class=l_post><a class=d_name>乙</a><div class=d_post_content id=post_content_3>相同回复</div></div></div>",first.nextUrl);
        next=ReadingHistory.decode(ReadingHistory.encode(next));assertEquals("2",next.sections.get(0).sourcePostId);
        Document merged=DiscussionPages.merge(Source.TIEBA,first,next,first.nextUrl);assertEquals(3,merged.sections.size());assertEquals(legacy.id,merged.sections.get(1).id);assertEquals("回复 · 乙",merged.sections.get(2).label);
    }
    @Test public void mergeAppendsOnlyNewRepliesWithoutReplacingMainOrOriginProvenance(){
        Document first=v2(1,reply(10,"甲","旧回复"),"?p=2"),second=v2(2,reply(10,"甲","旧回复")+reply(11,"乙","新回复"),"?p=3");first.metadataHash="a".repeat(64);first.cachedAt=123;second.metadataHash="b".repeat(64);
        Document merged=DiscussionPages.merge(Source.V2EX,first,second,first.nextUrl);
        assertEquals(3,merged.sections.size());assertEquals(first.sections.get(0),merged.sections.get(0));assertEquals(2,merged.discussionPage);assertEquals("loaded",merged.moreStatus);assertEquals(first.url,merged.url);assertEquals(first.metadataHash,merged.metadataHash);assertEquals(123,merged.cachedAt);assertEquals(2,first.sections.size());assertEquals(1,first.discussionPage);
        assertEquals("https://www.v2ex.com/t/123?p=3",merged.nextUrl);
    }
    @Test public void duplicateOrWrongPagesCannotSilentlyAdvanceTheCursor(){
        Document first=v2(1,reply(10,"甲","旧回复"),"?p=2"),same=v2(2,reply(10,"甲","旧回复"),"?p=3");
        try{DiscussionPages.merge(Source.V2EX,first,same,first.nextUrl);fail();}catch(IllegalArgumentException expected){}
        same.url="https://www.v2ex.com/t/999?p=2";try{DiscussionPages.merge(Source.V2EX,first,same,first.nextUrl);fail();}catch(IllegalArgumentException expected){}
        assertEquals(1,first.discussionPage);assertEquals(2,first.sections.size());
    }
    @Test public void gatePreservesReadContentAndRetryCursorAndEndIsNotAllHistory(){
        Document first=v2(1,reply(10,"甲","旧回复"),"?p=2"),gate=new Document();gate.url=first.nextUrl;gate.loginRequired=true;
        Document restricted=DiscussionPages.merge(Source.V2EX,first,gate,first.nextUrl);assertEquals("login",restricted.moreStatus);assertEquals(first.nextUrl,restricted.nextUrl);assertEquals(first.sections,restricted.sections);
        Document end=DiscussionPages.merge(Source.V2EX,first,v2(2,reply(11,"乙","新回复"),""),first.nextUrl);assertEquals("end",end.moreStatus);assertEquals("",end.nextUrl);assertTrue(end.notice.contains("不代表"));
    }
    @Test public void replyLimitNeverPartiallyAppendsOrDropsTheAlreadyReadContent(){
        Document first=v2(1,"","?p=2");for(int n=0;n<DiscussionPages.MAX_REPLIES;n++){Section section=new Section("existing"+n,"回复",false);section.blocks.add(new Block("text","已读回复"));first.sections.add(section);first.blocks.addAll(section.blocks);}
        Document bounded=DiscussionPages.merge(Source.V2EX,first,v2(2,reply(999,"乙","额外回复"),"?p=3"),first.nextUrl);assertEquals("limit",bounded.moreStatus);assertEquals(first.sections,bounded.sections);assertEquals(first.nextUrl,bounded.nextUrl);assertEquals(1,bounded.discussionPage);
    }
    @Test public void cursorSurvivesCopyAndJsonButLegacyAndInvalidFieldsStayCompatible()throws Exception{
        Document doc=v2(2,reply(10,"甲","回复"),"?p=3");assertEquals(2,AnswerStream.copy(doc).discussionPage);org.json.JSONObject json=ReadingHistory.encode(doc);assertEquals(2,ReadingHistory.decode(json).discussionPage);
        json.remove("discussionPage");assertEquals(2,DiscussionPages.current(Source.V2EX,ReadingHistory.decode(json)));json.put("discussionPage",Integer.MAX_VALUE);assertEquals(0,ReadingHistory.decode(json).discussionPage);
        String before=DocumentFingerprint.of(doc);doc.discussionPage=3;assertNotEquals(before,DocumentFingerprint.of(doc));
    }
    @Test public void readerHasAutomaticContinuationAndKeepsLoadingEndAndLimitDistinct(){
        Document doc=v2(1,reply(10,"甲","回复"),"?p=2");String html=ReaderHtml.render(doc,Source.V2EX,19);assertEquals(1,count(html,"data-continuation='ready'"));assertFalse(html.contains("href='"+ReaderHtml.ACTION+"more'"));assertFalse(html.contains("继续阅读下一页"));
        html=ReaderHtml.render(doc,Source.V2EX,19,false,Collections.emptySet(),true);assertFalse(html.contains("href='"+ReaderHtml.ACTION+"more'"));assertTrue(html.contains("正在加载…"));
        doc.moreStatus="limit";doc.notice="达到本次阅读预算";html=ReaderHtml.render(doc,Source.V2EX,19);assertFalse(html.contains("href='"+ReaderHtml.ACTION+"more'"));assertTrue(html.contains("在来源页继续阅读"));
        doc.moreStatus="end";doc.nextUrl="";html=ReaderHtml.render(doc,Source.V2EX,19);assertFalse(html.contains("href='"+ReaderHtml.ACTION+"more'"));
    }
    private int count(String value,String needle){return (value.length()-value.replace(needle,"").length())/needle.length();}
    @Test public void filteredIdentitiesCountTowardLimitAndNeverPartiallyChangeReadContent(){
        Document first=v2(1,reply(10,"甲","已读回复"),"?p=2"),second=v2(2,reply(11,"乙","新回复"),"?p=3");
        for(int n=0;n<DiscussionPages.MAX_REPLIES;n++)second.filteredSectionIds.add("filtered-"+n);
        Document result=DiscussionPages.merge(Source.V2EX,first,second,first.nextUrl);assertEquals("limit",result.moreStatus);assertEquals(first.sections,result.sections);assertTrue(result.filteredSectionIds.isEmpty());assertEquals(1,result.discussionPage);
    }
    @Test public void repeatedFilteredPageCannotAdvanceWithoutNewReplyEvidence(){
        Document first=v2(1,reply(10,"甲","已读回复"),"?p=2");first.filteredSectionIds.add("v2ex-r_11");first.filteredVideos=1;
        Document second=v2(2,"","?p=3");second.sections.clear();second.blocks.clear();second.filteredSectionIds.add("v2ex-r_11");second.filteredVideos=1;
        try{DiscussionPages.merge(Source.V2EX,first,second,first.nextUrl);fail();}catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("没有取得新的回复"));}
        assertEquals(1,first.discussionPage);assertEquals(2,first.sections.size());
    }
}
