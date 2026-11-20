package app.quietreader;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class AiChineseSourcesTest {
    private String fixture(String name)throws Exception{try(var in=getClass().getResourceAsStream("/ai-20261120/"+name+".html")){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    @Test public void qbitUsesTheActualHotRailAndExtractsTheFreeArticle()throws Exception{
        List<Item> items=SourceParser.list(Source.QBITAI,fixture("qbit-home"));assertTrue(items.size()>=4);assertTrue(items.size()<=10);
        assertTrue(items.stream().anyMatch(i->i.title.contains("DeepSeek")));assertTrue(items.stream().allMatch(i->!i.title.matches(".*20[0-9]{2}-[0-9]{2}-[0-9]{2}.*")));
        String url="https://www.qbitai.com/2026/11/510003.html";Document doc=SourceParser.article(Source.QBITAI,fixture("qbit-hot-article"),url);
        assertTrue(doc.hasContent());assertTrue(doc.title.contains("DeepSeek"));String body=doc.blocks.stream().filter(b->b.type.equals("text")).map(b->b.value).reduce("",String::concat);
        assertTrue(body.length()>500);assertFalse(body.contains("相关阅读"));assertEquals(1,doc.sections.size());assertEquals(Source.QBITAI,ReaderLinks.readerSource(url));
    }
    @Test public void leiphoneStaysInTheAiColumnAndExcludesRecommendationAndCourseBanners()throws Exception{
        List<Item> items=SourceParser.list(Source.LEIPHONE_AI,fixture("leiphone-ai"));assertTrue(items.size()>=8);assertTrue(items.stream().allMatch(i->i.url.matches(".*/category/(ai|academic|yanxishe)/.*")));assertTrue(items.get(0).detail.contains("非热榜"));
        String url="https://www.leiphone.com/category/ai/0u1BjHIMazUFuNZc.html";Document doc=SourceParser.article(Source.LEIPHONE_AI,fixture("leiphone-article"),url);
        assertTrue(doc.hasContent());assertTrue(doc.title.contains("林达华"));String body=ReaderHtml.render(doc,Source.LEIPHONE_AI,19);
        assertTrue(body.contains("Scaling Law"));assertFalse(body.contains("vip.yanxishe.com"));assertFalse(body.contains("当月热门文章"));assertEquals(Source.LEIPHONE_AI,ReaderLinks.readerSource(url));
        assertTrue(doc.blocks.stream().filter(b->b.type.equals("image")).allMatch(b->!b.value.contains("miss-main-pic")));
    }
    @Test public void adaptersRejectLookalikesNonArticlesAndUnrelatedColumns()throws Exception{
        assertEquals("",AiChineseSources.canonical(Source.QBITAI,"https://www.qbitai.com.evil.test/2026/11/510003.html"));
        assertEquals("",AiChineseSources.canonical(Source.LEIPHONE_AI,"https://www.leiphone.com/category/transportation/0u1BjHIMazUFuNZc.html"));
        assertEquals("",AiChineseSources.canonical(Source.QBITAI,"https://www.qbitai.com/?p=510003"));
        assertFalse(SourceParser.article(Source.QBITAI,"<article>unrelated</article>",Source.QBITAI.login).hasContent());
    }
}
