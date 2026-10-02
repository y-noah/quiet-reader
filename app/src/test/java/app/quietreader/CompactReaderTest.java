package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import java.util.Collections;

public final class CompactReaderTest {
    @Test public void sectionRoleIsEscapedWithoutChangingOriginalText() {
        Document d=new Document();d.title="合成身份转义";d.url="https://bbs.hupu.com/1.html";
        String label="主帖 · ' \" <img src=x> & 作者";
        Section s=new Section("main",label,true);s.blocks.add(new Block("text","原文🐎仍完整保留"));d.sections.add(s);d.blocks.addAll(s.blocks);
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.HUPU,19,true,Collections.singleton("main"),false));
        assertEquals(label,page.selectFirst("section.part").attr("data-section-label"));
        assertTrue(page.select("img").isEmpty());
        assertEquals("原文🐎仍完整保留",page.select("section.part p:not(.preview)").text());
        assertTrue(page.select("a.action").text().contains(label));
    }

    @Test public void shortUnfoldedSectionKeepsExplicitMainOrReplyIdentity() {
        for(String label:new String[]{"主帖","回复 1"}){
            Document d=new Document();d.title="合成短内容";d.url="https://bbs.hupu.com/1.html";
            Section s=new Section("short",label,false);s.blocks.add(new Block("text","🐎"));d.sections.add(s);d.blocks.addAll(s.blocks);
            org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.HUPU,19));
            assertEquals(label,page.selectFirst("section.part").attr("data-section-label"));
            assertTrue(page.select("a.action").isEmpty());
            assertEquals("Unfolded identity must be visible, not metadata alone",label,page.select("section.part .section-label").text());
            assertEquals("🐎",page.select("section.part p").text());
        }
    }

    @Test public void sourceIdentityIsEscapedMetadataNotPersistentLoginAction() {
        Document d=new Document();d.title="正文";
        d.url="https://www.zhihu.com/question/123?label='><script>alert(1)</script>&q=\"x\"";
        d.blocks.add(new Block("text","实际正文"));
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19));
        assertEquals(d.url,page.selectFirst("head meta[name=quiet-reader-source]").attr("content"));
        assertTrue(page.select("script").isEmpty());
        assertFalse(page.body().text().contains(d.url));
        assertTrue(page.select("a").stream().noneMatch(a->a.attr("href").equals(d.url)));
        assertFalse(page.body().text().contains("登录"));
        assertTrue(page.body().text().contains("实际正文"));
    }

    @Test public void compactChromeDoesNotRemoveAnswerInteractions() {
        Document d=new Document();d.title="多回答问题";d.url="https://www.zhihu.com/question/123";
        for(int n=0;n<2;n++) {Section s=new Section("a"+n,"作者"+n,true);s.blocks.add(new Block("text","回答正文"+n));d.sections.add(s);d.blocks.addAll(s.blocks);}
        org.jsoup.nodes.Document closed=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19,true,Collections.emptySet(),false));
        assertEquals(2,closed.select("a[href*='toggle?index=']").size());
        assertEquals(1,closed.select("a[href='https://quiet-reader.invalid/more']").size());
        assertEquals(2,closed.select("p.preview").size());
        org.jsoup.nodes.Document open=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19,true,Collections.singleton("a1"),false));
        assertEquals("回答正文1",open.select("#part-1 p:not(.preview)").text());
        assertTrue(open.select("#part-1 a").text().contains("收起"));
        assertFalse(open.body().text().contains("收藏"));
    }

    @Test public void previewUsesLineClampWithoutIndependentHeightCap() {
        Document d=new Document();d.title="折叠预览";d.url="https://www.zhihu.com/question/123";
        Section s=new Section("long","长回答",true);s.blocks.add(new Block("text","长回答正文".repeat(80)));d.sections.add(s);d.blocks.addAll(s.blocks);
        String html=ReaderHtml.render(d,Source.ZHIHU,25,true,Collections.emptySet(),false);
        assertTrue(html.contains("-webkit-line-clamp:3"));
        assertFalse(html.contains("max-height:5.4em"));
        assertEquals(1,org.jsoup.Jsoup.parse(html).select("p.preview").size());
    }
}
