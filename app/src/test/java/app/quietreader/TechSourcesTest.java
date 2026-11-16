package app.quietreader;

import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public final class TechSourcesTest {
    private String fixture(String name)throws Exception{try(var stream=getClass().getResourceAsStream("/tech-20261116/"+name+".html")){return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}}
    @Test public void solidotUsesObservedPopularRailAndFreeBody()throws Exception{
        List<Item> rows=SourceParser.list(Source.SOLIDOT,fixture("solidot-home"));assertEquals(10,rows.size());assertEquals("https://www.solidot.org/story?sid=85889",rows.get(0).url);
        assertTrue(rows.stream().noneMatch(row->row.title.contains("免费领取")));assertEquals(Source.SOLIDOT,ReaderLinks.readerSource(rows.get(0).url));
        Document body=SourceParser.article(Source.SOLIDOT,fixture("solidot-story"),rows.get(0).url);assertTrue(body.hasContent());assertTrue(body.title.contains("麻疹"));assertTrue(body.blocks.stream().anyMatch(b->b.type.equals("text")&&b.value.contains("麻疹")));assertFalse(ReaderHtml.render(body,Source.SOLIDOT,19).contains("热门评论"));
    }
    @Test public void infoqReadsOfficialChineseRssAndEngineeringBody()throws Exception{
        List<Item> rows=SourceParser.list(Source.INFOQ,fixture("infoq-feed"));assertEquals(19,rows.size());assertTrue(rows.stream().noneMatch(row->row.url.contains("/video/")));assertTrue(rows.get(0).title.contains("Flink"));assertFalse(rows.get(0).url.contains("utm_"));assertTrue(rows.get(0).detail.contains("非热榜"));
        Document body=SourceParser.article(Source.INFOQ,fixture("infoq-story"),rows.get(0).url);assertTrue(body.hasContent());assertTrue(body.blocks.stream().filter(b->b.type.equals("text")).mapToInt(b->b.value.length()).sum()>3000);assertEquals(Source.INFOQ,ReaderLinks.readerSource(rows.get(0).url));
    }
    @Test public void technologySourcesDoNotAcceptLoginOrLookalikePages(){
        assertFalse(SolidotSource.articleUrl("https://www.solidot.org.evil.test/story?sid=12"));assertFalse(SolidotSource.articleUrl("https://www.solidot.org/story?sid=12&redirect=evil"));assertEquals("",InfoqSource.canonical("https://www.infoq.cn/login"));assertEquals("",InfoqSource.canonical("https://www.infoq.cn.evil.test/article/abc"));
    }
}
