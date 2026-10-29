package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Structural media identities must survive persistence and reordered merged attachments. */
public class ReaderMediaPositionTest {
    private static final String PAGE="https://sspai.com/post/123";
    private static String clip(int n){return "<video src='https://cdn.sspai.com/"+n+".mp4' poster='https://cdn.sspai.com/"+n+".jpg'></video>";}
    private static Document parse(String body){return SourceParser.article(Source.SSPAI,"<div class=ss__article__body>"+body+"</div>",PAGE);}
    @Test public void mediaKeepsItsPositionBetweenTextAndImages(){
        Document doc=parse("<p>前文</p>"+clip(1)+"<p>中间</p><img src='https://cdn.sspai.com/body.jpg'>"+clip(2)+"<p>后文</p>");
        assertEquals(java.util.List.of("text","video","text","image","video","text"),doc.blocks.stream().map(b->b.type).toList());
        assertEquals(0,ReaderMedia.index(doc,doc.blocks.get(1).value));assertEquals(1,ReaderMedia.index(doc,doc.blocks.get(4).value));
        assertFalse(doc.videoTopic);assertTrue(doc.hasContent());assertTrue(doc.unsupportedVideo);
    }
    @Test public void stableKeysSurviveSnapshotAndAttachmentArrayChanges()throws Exception{
        Document original=parse("<p>原文</p>"+clip(1)+clip(2));
        Document restored=ReadingHistory.decode(ReadingHistory.encode(original));
        String key=restored.blocks.get(1).value;assertEquals(0,ReaderMedia.index(restored,key));
        java.util.Collections.reverse(restored.videos);assertEquals(1,ReaderMedia.index(restored,key));
        assertEquals("https://cdn.sspai.com/1.mp4",restored.videos.get(ReaderMedia.index(restored,key)).url);
        assertEquals(key,AnswerStream.copy(original).blocks.get(1).value);
        Document merged=new Document();merged.url=PAGE;merged.videoUrl="https://cdn.sspai.com/old.mp4";
        AnswerStream.merge(merged,original);assertEquals(1,ReaderMedia.index(merged,key));
    }
    @Test public void mediaAloneCannotInventReadableBody(){
        Document only=parse(clip(1));assertFalse(only.hasContent());assertTrue(only.canPresent());
        assertTrue(only.blocks.isEmpty());assertTrue(only.sections.isEmpty());
    }
    @Test public void sourceCannotForgeMarkersAndQuotedProseIsPreserved(){
        String forged=ReaderMedia.key(new VideoAttachment("https://cdn.sspai.com/1.mp4","https://cdn.sspai.com/1.jpg"));
        Document doc=parse("<quiet-reader-media data-key='"+forged+"'></quiet-reader-media><blockquote>保留引用文字"+clip(2)+"</blockquote><p>正文</p>"+clip(1));
        assertEquals(1,doc.blocks.stream().filter(b->b.type.equals("video")).count());
        assertEquals(1,doc.videos.size());assertTrue(doc.blocks.stream().anyMatch(b->b.value.contains("保留引用文字")));
    }
    @Test public void nestedPlayerEmitsOneAttachmentAndUnsupportedMediaStaysLocal(){
        Document doc=parse("<p>说明</p><div class=video-player>"+clip(1)+"</div><p>分隔</p><video src='https://unknown.example/2.mp4'></video><p>尾文</p>");
        assertEquals(2,doc.blocks.stream().filter(b->b.type.equals("video")).count());
        assertEquals(2,doc.videos.size());assertEquals("",doc.videos.get(1).url);
        assertEquals(1,ReaderMedia.index(doc,doc.blocks.get(3).value));
    }
}
