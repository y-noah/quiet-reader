package app.quietreader;

import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class TiebaResolutionTest {
    private static final String ROOT="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123",POST="https://tieba.baidu.com/p/456",SESSION="a".repeat(64);
    private final Item candidate=new Item(Source.TIEBA,"原热搜标题",ROOT,"热榜",false,"热搜摘要",null);
    private List<Item> choices(){return List.of(new Item(Source.TIEBA,"真实帖子标题",POST,"最热"),new Item(Source.TIEBA,"另一个帖子","https://tieba.baidu.com/p/789","最热"));}
    private Document resolved(){
        Document thread=SourceParser.article(Source.TIEBA,"<h1>真实帖子标题</h1><div id=j_p_postlist><div class=l_post><a class=d_name>真实作者</a><div class=d_post_content id=post_content_1>正文内容</div></div></div><a rel=next href='?pn=2'>下一页</a>",POST);
        TiebaTopic.attach(ROOT,choices(),thread,SESSION,123);ArticleVersion.capture(candidate,thread);return thread;
    }
    @Test public void actualPostAndOriginalRecommendationHaveExplicitIndependentIdentities(){
        Document doc=resolved();assertEquals(ROOT,doc.url);assertEquals(POST,doc.sourceUrl());assertEquals("真实帖子标题",doc.title);assertTrue(ArticleVersion.matches(candidate,doc));
        assertFalse(ArticleVersion.matches(choices().get(0),doc));assertEquals("最热",doc.topic.choices.get(0).detail);
    }
    @Test public void copyHistoryAndFingerprintRetainResolutionWithoutTimestampInstability()throws Exception{
        Document doc=resolved(),copy=AnswerStream.copy(doc),restored=ReadingHistory.decode(ReadingHistory.encode(doc));
        for(Document next:List.of(copy,restored)){assertEquals(POST,next.sourceUrl());assertEquals(SESSION,next.topic.postSession);assertEquals(2,next.topic.choices.size());assertTrue(ArticleVersion.matches(candidate,next));assertEquals(DocumentFingerprint.of(doc),DocumentFingerprint.of(next));}
        String before=DocumentFingerprint.of(doc);doc.topic=new TiebaTopic.Resolved(ROOT,POST,SESSION,456,choices());assertEquals(before,DocumentFingerprint.of(doc));
        doc.topic=new TiebaTopic.Resolved(ROOT,POST,"b".repeat(64),456,choices());assertNotEquals(before,DocumentFingerprint.of(doc));
    }
    @Test public void paginationUsesActualPostAndPreservesTopicProvenance(){
        Document doc=resolved();assertTrue(DiscussionPages.usesStream(Source.TIEBA,doc));assertTrue(DiscussionPages.next(Source.TIEBA,doc));
        Document page=SourceParser.article(Source.TIEBA,"<div id=j_p_postlist><div class=d_post_content id=post_content_2>后续回复</div></div>",doc.nextUrl);
        Document merged=DiscussionPages.merge(Source.TIEBA,doc,page,doc.nextUrl);
        assertEquals(ROOT,merged.url);assertEquals(POST,merged.sourceUrl());assertSame(doc.topic,merged.topic);assertEquals(2,merged.discussionPage);assertEquals(2,merged.sections.size());assertEquals(doc.sections.get(0),merged.sections.get(0));assertTrue(ArticleVersion.matches(candidate,merged));
        page.url="https://tieba.baidu.com/p/789?pn=2";try{DiscussionPages.merge(Source.TIEBA,doc,page,doc.nextUrl);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void oldTopicSummaryCannotSatisfyCurrentCandidate(){Document doc=new Document();doc.url=ROOT;doc.blocks.add(new Block("text","旧聚合摘要"));ArticleVersion.capture(candidate,doc);assertFalse(ArticleVersion.matches(candidate,doc));}
    @Test public void invalidResolutionAndMismatchedSerializedIdentityAreRejected()throws Exception{
        for(String post:List.of("https://example.com/p/456","https://tieba.baidu.com/p/999",POST+"?pn=2"))try{new TiebaTopic.Resolved(ROOT,post,SESSION,123,choices());fail(post);}catch(IllegalArgumentException expected){}
        JSONObject json=ReadingHistory.encode(resolved());json.put("url",POST);try{ReadingHistory.decode(json);fail();}catch(JSONException expected){}
        try{new TiebaTopic.Resolved(ROOT,POST,"invalid",123,choices());fail();}catch(IllegalArgumentException expected){}
        List<Item> duplicate=new ArrayList<>(choices());duplicate.add(choices().get(0));try{new TiebaTopic.Resolved(ROOT,POST,SESSION,123,duplicate);fail();}catch(IllegalArgumentException expected){}
    }
    @Test public void choicesAreImmutableAndIncludedInSerializedBodyBudget(){
        Document doc=resolved();try{doc.topic.choices.clear();fail();}catch(UnsupportedOperationException expected){}
        int normal=DocumentSize.estimate(doc);doc.topic=new TiebaTopic.Resolved(ROOT,POST,SESSION,123,List.of(new Item(Source.TIEBA,"大标题".repeat(1000),POST,"最热")));assertTrue(DocumentSize.estimate(doc)>normal);assertFalse(DocumentSize.serializationFits(doc,100));
    }
    @Test public void selectingAnotherPostKeepsOriginalMetadataAndSurvivesRequestSerialization()throws Exception{
        Document doc=resolved();Item selected=TiebaTopic.select(candidate,doc.topic,choices().get(1));
        assertEquals(ROOT,selected.url);assertEquals(candidate.title,selected.title);assertEquals(candidate.snippet,selected.snippet);assertEquals(ArticleVersion.hash(candidate),ArticleVersion.hash(selected));
        Item restored=Item.from(selected.json());assertTrue(ArticleVersion.sameRequest(selected,restored));assertFalse(ArticleVersion.sameRequest(candidate,restored));
        assertFalse(ArticleVersion.matches(selected,doc));assertEquals(choices().get(1).url,TiebaTopic.target(restored,choices()).url);
        assertNull(TiebaTopic.target(restored,List.of(choices().get(0))));
    }
    @Test public void selectionCannotChooseOutsideTopicOrAcceptMalformedSerializedTargets()throws Exception{
        Document doc=resolved();try{TiebaTopic.select(candidate,doc.topic,new Item(Source.TIEBA,"无关帖","https://tieba.baidu.com/p/888",""));fail();}catch(IllegalArgumentException expected){}
        for(String url:List.of("https://example.com/p/456",POST+"?pn=2",ROOT,"https://tieba.baidu.com/p/0456"))try{Item.from(candidate.json().put("selectedTopicPost",url));fail(url);}catch(IllegalArgumentException expected){}
    }
    @Test public void selectedPostBodyStillQualifiesAsOriginalCandidateWithoutBorrowingItsTitle(){
        Document doc=resolved();Item selected=TiebaTopic.select(candidate,doc.topic,choices().get(1));
        Document post=SourceParser.article(Source.TIEBA,"<h1>另一帖真实标题</h1><div id=j_p_postlist><div class=d_post_content id=post_content_55>另一帖正文</div></div>",choices().get(1).url);
        TiebaTopic.attach(ROOT,choices(),post,SESSION,456);ArticleVersion.capture(selected,post);
        assertTrue(ArticleVersion.matches(candidate,post));assertTrue(ArticleVersion.matches(selected,post));assertEquals("另一帖真实标题",post.title);assertFalse(ArticleVersion.matches(choices().get(1),post));
    }
}
