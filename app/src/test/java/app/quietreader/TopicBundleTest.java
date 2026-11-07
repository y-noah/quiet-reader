package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class TopicBundleTest {
    private static final String ROOT="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123",A="https://tieba.baidu.com/p/456",B="https://tieba.baidu.com/p/789";
    private Document body(String post,String text){Document doc=new Document();doc.url=post;doc.title=text;doc.blocks.add(new Block("text",text));TiebaTopic.attach(ROOT,List.of(new Item(Source.TIEBA,"甲",A,""),new Item(Source.TIEBA,"乙",B,"")),doc,"a".repeat(64),System.currentTimeMillis());doc.cachedAt=System.currentTimeMillis();doc.cachedSession="b".repeat(64);doc.cachedNamespace="scope";return doc;}
    @Test public void wholeTopicRoundTripContainsBothIndependentBodiesAndTheirStamps()throws Exception{
        Document first=body(A,"甲正文"),second=body(B,"乙正文");assertTrue(TopicBundle.merge(first,second));
        Document restored=ReadingHistory.decode(ReadingHistory.encode(first));assertEquals("甲正文",restored.blocks.get(0).value);assertEquals("乙正文",TopicBundle.find(restored,B).blocks.get(0).value);
        assertEquals(second.cachedAt,TopicBundle.find(restored,B).cachedAt);assertEquals(second.cachedSession,TopicBundle.find(restored,B).cachedSession);assertTrue(TopicBundle.find(restored,B).topicBodies.isEmpty());
        Document focused=TopicBundle.focus(restored,B);assertEquals(B,focused.sourceUrl());assertEquals("甲正文",TopicBundle.find(focused,A).blocks.get(0).value);assertTrue(TopicBundle.find(focused,A).topicBodies.isEmpty());
    }
    @Test public void bundleRejectsAnotherAccountAndNamespace(){Document first=body(A,"甲"),second=body(B,"乙");second.cachedSession="c".repeat(64);assertFalse(TopicBundle.merge(first,second));second.cachedSession=first.cachedSession;second.cachedNamespace="other";assertFalse(TopicBundle.merge(first,second));}
    @Test public void firstBodyIsNotACompleteTopic(){Document first=body(A,"甲");assertFalse(TopicBundle.complete(first));TopicBundle.merge(first,body(B,"乙"));assertTrue(TopicBundle.complete(first));first.topicBodies.get(B).sourceUnavailable=true;assertFalse(TopicBundle.complete(first));}
    @Test public void backgroundUpdatesCannotDowngradeAnAlreadyLongerDiscussion(){Document first=body(A,"甲"),second=body(B,"乙");TopicBundle.merge(first,second);Document stale=body(B,"旧");stale.cachedAt=second.cachedAt-1000;assertFalse(TopicBundle.merge(first,stale));assertEquals("乙",TopicBundle.find(first,B).title);}
    @Test public void sizeAndSerializationBoundsIncludeEveryChild(){Document first=body(A,"甲"),second=body(B,"乙".repeat(10000));int before=DocumentSize.estimate(first);TopicBundle.merge(first,second);assertTrue(DocumentSize.estimate(first)>before+10000);assertFalse(DocumentSize.serializationFits(first,10000));assertTrue(DocumentSize.serializationFits(first,1024*1024));}
}
