package app.quietreader;

import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SavedReadingViewportTest {
    private Document doc(){Document d=new Document();d.url="https://www.thepaper.cn/newsDetail_forward_123";d.metadataHash="a".repeat(64);Section s=new Section("main","正文",false);s.blocks.add(new Block("text","第一段内容"));s.blocks.add(new Block("text","正在阅读的第二段独立内容"));d.sections.add(s);d.blocks.addAll(s.blocks);return d;}
    private ReadingViewport viewport(){return ReadingViewport.parse("{\"id\":\""+ReaderHtml.anchorId("main",1)+"\",\"fraction\":0.35,\"gap\":0}");}
    @Test public void jsonRoundTripRetainsGeometryWithoutStoringParagraphText()throws Exception{
        Document d=doc();JSONObject saved=SavedReadingViewport.capture(d,viewport());assertNotNull(saved);ReadingViewport restored=SavedReadingViewport.restore(d,new JSONObject(saved.toString()));assertNotNull(restored);assertEquals(viewport().id,restored.id);assertEquals(.35,restored.fraction,0);assertFalse(saved.toString().contains("正在阅读"));assertTrue(saved.toString().length()<1024);
    }
    @Test public void insertedEarlierParagraphMovesTheTargetInsteadOfReusingItsOldIndex(){
        Document d=doc();JSONObject saved=SavedReadingViewport.capture(d,viewport());d.sections.get(0).blocks.add(0,new Block("text","新插入的一段"));ReadingViewport restored=SavedReadingViewport.restore(d,saved);assertNotNull(restored);assertEquals(ReaderHtml.anchorId("main",2),restored.id);
    }
    @Test public void duplicatedTargetContentIsAmbiguousAndRejected(){Document d=doc();JSONObject saved=SavedReadingViewport.capture(d,viewport());d.sections.get(0).blocks.add(new Block("text","正在阅读的第二段独立内容"));assertNull(SavedReadingViewport.restore(d,saved));}
    @Test public void differentUrlMetadataAndChangedBodyCannotInheritTheAnchor(){
        Document original=doc();JSONObject saved=SavedReadingViewport.capture(original,viewport());Document d=doc();d.url+="?other=1";assertNull(SavedReadingViewport.restore(d,saved));d=doc();d.metadataHash="b".repeat(64);assertNull(SavedReadingViewport.restore(d,saved));d=doc();d.sections.get(0).blocks.set(1,new Block("text","内容已经改变"));assertNull(SavedReadingViewport.restore(d,saved));
    }
    @Test public void missingMalformedOrFutureRecordsFallBackSafely()throws Exception{
        Document d=doc();assertNull(SavedReadingViewport.restore(d,null));assertNull(SavedReadingViewport.restore(d,new JSONObject()));JSONObject saved=SavedReadingViewport.capture(d,viewport());saved.put("version",2);assertNull(SavedReadingViewport.restore(d,saved));saved.put("version",1).put("geometry",new JSONObject().put("id",viewport().id).put("fraction",2));assertNull(SavedReadingViewport.restore(d,saved));
    }
    @Test public void missingSectionOrUnsupportedBlockCannotProduceARestore(){Document d=doc();JSONObject saved=SavedReadingViewport.capture(d,viewport());d.sections.clear();d.blocks.clear();assertNull(SavedReadingViewport.restore(d,saved));d=doc();d.sections.get(0).blocks.set(1,new Block("separator",""));assertNull(SavedReadingViewport.capture(d,viewport()));assertNull(SavedReadingViewport.capture(d,null));}
}
