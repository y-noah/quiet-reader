package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class BrowsingExperienceTest {
    private Document question(){
        Document d=new Document();d.url="https://www.zhihu.com/question/123";
        for(int i=0;i<3;i++){Section s=new Section("s"+i,i==0?"问题补充":"回答"+i,i>0);s.blocks.add(new Block("text","正文"+i));d.sections.add(s);d.blocks.addAll(s.blocks);}
        return d;
    }
    @Test public void firstAnswerAndSupplementOpenWithoutDuplicateControls(){
        Document d=question();Set<String> expanded=new HashSet<>();ReaderHtml.expandPrimary(d,expanded);
        assertEquals(new HashSet<>(Arrays.asList("s0","s1")),expanded);
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19,false,expanded,false));
        assertEquals(1,page.select("#part-1 a[href*=toggle]").size());
        assertEquals(1,page.select("#part-1 a[href*=jump]").size());
        assertEquals(1,page.select("p.preview").size());
        assertTrue(page.select("#part-0 p").text().contains("正文0"));
        assertTrue(page.select("#part-1 p").text().contains("正文1"));
    }
    @Test public void continuationNoticeIsNotRepeatedAtTop(){
        Document d=question();d.moreStatus="login";d.notice="需要登录";
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19));
        assertEquals(1,page.select(".notice").size());assertTrue(page.select(".notice").first().hasClass("continuation-status"));
        d.moreStatus="";page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19));
        assertTrue(page.select("details").isEmpty());assertFalse(page.text().contains("阅读提示"));
    }
    @Test public void readingSnapshotPreservesLoadedAnswersImagesAndInlineLinks()throws Exception{
        Document d=question();d.moreStatus="end";d.notice="末页";d.filteredSectionIds.add("video-1");
        Block image=new Block("image","https://example.com/test.png");image.imageWidth=640;image.imageHeight=1800;d.sections.get(1).blocks.add(image);
        Block text=new Block("text","图 链接",Arrays.asList(new InlineImage(0,1,"https://example.com/emoji.png","图")),Arrays.asList(new InlineLink(2,4,"https://example.com/link")));d.sections.get(2).blocks.add(text);
        Document restored=ReadingHistory.decode(ReadingHistory.encode(d));
        assertEquals(3,restored.sections.size());assertEquals("末页",restored.notice);assertEquals(d.filteredSectionIds,restored.filteredSectionIds);
        assertEquals(1800,restored.sections.get(1).blocks.get(1).imageHeight);
        assertEquals("https://example.com/link",restored.sections.get(2).blocks.get(1).inlineLinks.get(0).url);
        assertTrue(restored.containsImage("https://example.com/emoji.png"));
    }
    @Test public void imageDimensionsReserveLayoutAndUnknownImagesUseStablePreview(){
        Document d=SourceParser.article(Source.CLS,"<section class='content-box'><div class='content'><p>正文内容足够长的合成新闻</p><img src='https://example.com/photo.png' width='600' height='900'></div></section>","https://www.cls.cn/detail/123");
        assertEquals(1,d.sections.size());
        Block b=new Block("image","https://example.com/unknown.png");d.blocks.add(b);d.sections.get(0).blocks.add(b);
        Set<String> expanded=new HashSet<>();ReaderHtml.expandPrimary(d,expanded);
        String html=ReaderHtml.render(d,Source.CLS,19,false,expanded,false);
        assertTrue(html.contains("aspect-ratio:600/900"));assertTrue(html.contains("height:240px"));
    }
}
