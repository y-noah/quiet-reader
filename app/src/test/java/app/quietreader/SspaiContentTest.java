package app.quietreader;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SspaiContentTest {
    private Document captured(int index)throws Exception{
        String html;try(java.io.InputStream in=getClass().getResourceAsStream("/sspai-20261103/article-"+index+".html")){html=new String(Objects.requireNonNull(in).readAllBytes(),StandardCharsets.UTF_8);}
        return SourceParser.article(Source.SSPAI,html,"https://sspai.com/post/"+(index==0?118232:118240));
    }
    private String text(Document doc){return doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining("\n"));}
    @Test public void realMorningPaperIncludesAllNewsSectionsInsteadOfOnlyItsPromotionFooter()throws Exception{
        Document doc=captured(0);String text=text(doc);
        assertTrue(text.contains("猛玛推出探境"));assertTrue(text.contains("LongCat-2.5"));assertTrue(text.contains("Keychron"));assertTrue(text.contains("名越工作室"));
        assertTrue(doc.sections.size()>=4);assertTrue(text.length()>1000);assertFalse(text.contains("少数派的近期动态"));assertFalse(text.contains("你可能错过的好文章"));assertFalse(text.contains("下载少数派客户端"));
        assertTrue(doc.blocks.stream().anyMatch(b->!b.inlineLinks.isEmpty()));
    }
    @Test public void realReviewIncludesItsApplicationSectionsAndKeepsUsefulBodyLinks()throws Exception{
        Document doc=captured(1);String text=text(doc);
        assertTrue(text.contains("Local Code"));assertTrue(text.contains("本地优先"));assertTrue(text.length()>1000);assertFalse(text.contains("下载少数派客户端"));assertFalse(text.contains("少数派为你呈现"));
        assertTrue(doc.blocks.stream().anyMatch(b->!b.inlineLinks.isEmpty()));
    }
    @Test public void legacyFooterOnlyCacheBecomesEmptyRatherThanPretendingToHaveBody()throws Exception{
        Document doc=new Document();doc.url="https://sspai.com/post/118232";Section section=new Section("old","正文",false);section.blocks.add(new Block("text","少数派的近期动态"));section.blocks.add(new Block("text","无关的商品推广"));doc.sections.add(section);doc.blocks.addAll(section.blocks);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));assertFalse(restored.hasContent());assertFalse(restored.canPresent());
    }
    @Test public void legacyFullBodyKeepsProseButDropsTheRecommendedTail()throws Exception{
        Document doc=new Document();doc.url="https://sspai.com/post/118232";Section section=new Section("old","正文",false);section.blocks.add(new Block("text","正文讨论值得阅读的应用软件"));section.blocks.add(new Block("text","你可能错过的好文章"));section.blocks.add(new Block("text","无关的推荐文章"));doc.sections.add(section);doc.blocks.addAll(section.blocks);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));assertEquals("正文讨论值得阅读的应用软件",text(restored));assertTrue(restored.hasContent());
    }
}
