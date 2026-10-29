package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import app.quietreader.discovery.HotCandidate;

public class ArticleVersionTest {
    private Item item(String title,String snippet){return new Item(Source.HUPU,title,"https://bbs.hupu.com/123.html","",false,snippet,null,"");}
    @Test public void metadataVersionUsesTheSameNormalizationAsRecommendationInput(){
        Item item=item("中文标题 cafe\u0301", "摘  要");HotCandidate candidate=new HotCandidate(item.source,item.url,item.title,item.snippet,"",false,1,null,1);
        assertEquals(candidate.textHash,ArticleVersion.hash(item));
        Document doc=new Document();doc.url=item.url;ArticleVersion.capture(item,doc);assertTrue(ArticleVersion.matches(item,doc));
        assertFalse(ArticleVersion.matches(item("另一个中文标题",item.snippet),doc));assertFalse(ArticleVersion.matches(item(item.title,"另一段摘要"),doc));
    }
    @Test public void reusedBodyCannotBeInferredAsANewVersionByMerelyRecachingIt(){
        Item before=item("旧标题","旧摘要"),after=item("新标题","新摘要");Document doc=new Document();doc.url=before.url;ArticleVersion.capture(before,doc);
        Document copy=AnswerStream.copy(doc);assertTrue(ArticleVersion.matches(before,copy));assertFalse(ArticleVersion.matches(after,copy));assertEquals(doc.metadataHash,copy.metadataHash);
        Document other=new Document();other.url=before.url;other.metadataHash=ArticleVersion.hash(after);AnswerStream.merge(copy,other);assertTrue(ArticleVersion.matches(before,copy));
    }
    @Test public void persistedProvenanceIsOptionalAndUnknownOldSnapshotsRemainReadable()throws Exception{
        Item item=item("中文标题","摘要");Document doc=new Document();doc.url=item.url;doc.blocks.add(new Block("text","缓存正文"));ArticleVersion.capture(item,doc);
        org.json.JSONObject encoded=ReadingHistory.encode(doc);assertTrue(ArticleVersion.matches(item,ReadingHistory.decode(encoded)));
        encoded.remove("metadataHash");Document old=ReadingHistory.decode(encoded);assertTrue(old.hasContent());assertFalse(ArticleVersion.matches(item,old));
        encoded.put("metadataHash","not-a-hash");assertEquals("",ReadingHistory.decode(encoded).metadataHash);
    }
    @Test public void provenanceAffectsCacheAccountingAndCannotCrossContentUrls(){
        Item item=item("中文标题","摘要");Document doc=new Document();doc.url=item.url;String before=DocumentFingerprint.of(doc);int size=DocumentSize.estimate(doc);ArticleVersion.capture(item,doc);
        assertNotEquals(before,DocumentFingerprint.of(doc));assertTrue(DocumentSize.estimate(doc)>size);doc.url="https://bbs.hupu.com/456.html";assertFalse(ArticleVersion.matches(item,doc));
    }
}
