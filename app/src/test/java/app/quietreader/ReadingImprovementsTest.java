package app.quietreader;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReadingImprovementsTest {
    @Test public void mainPostOpensButAnswersStayFolded(){
        Document d=new Document();Section main=new Section("main","主帖",false),answer=new Section("answer","回复",true);
        main.blocks.add(new Block("text","主帖正文"));answer.blocks.add(new Block("text","回答正文"));d.sections.add(main);d.sections.add(answer);
        Set<String> expanded=new HashSet<>();ReaderHtml.expandPrimary(d,expanded);
        assertEquals(Collections.singleton("main"),expanded);
        String html=ReaderHtml.render(d,Source.HUPU,19,true,expanded,false);
        assertTrue(html.contains("jump?index=1"));assertTrue(html.contains("回复 · 2 · 展开阅读"));
        Document question=new Document();question.sections.add(answer);expanded.clear();ReaderHtml.expandPrimary(question,expanded);assertTrue(expanded.isEmpty());
    }
    @Test public void compactLinksKeepAddressAndDontSpoofDomain(){
        String url="https://item.jd.com/100291533956.html";String html=ReaderLinks.plain(url);
        assertTrue(html.contains("href='"+url+"'"));assertTrue(html.contains(">京东商品 ↗</a>"));
        assertEquals("jd.com.evil.example ↗",ReaderLinks.shortLabel("https://jd.com.evil.example/a"));
    }
    @Test public void styleValuesAreBoundedAndApplied(){
        ReaderStyle style=new ReaderStyle(900,-8,400,-1);
        assertEquals(2,style.palette);assertEquals(0,style.accent);assertEquals(2,style.spacing);assertEquals(0,style.face);
        assertTrue(style.css(true).contains("#101112"));assertEquals("2.05",style.lineHeight());
    }
    @Test public void cacheCountsSectionOnlyTextAndMetadata(){
        Document d=new Document();int empty=DocumentSize.estimate(d);Section section=new Section("a","主帖",false);
        section.blocks.add(new Block("text",String.join("",Collections.nCopies(1000,"字"))));d.sections.add(section);
        assertTrue(DocumentSize.estimate(d)>empty+2000);int sections=DocumentSize.estimate(d);d.blocks.addAll(section.blocks);
        assertTrue(DocumentSize.estimate(d)<sections+100);d.notice=String.join("",Collections.nCopies(1000,"n"));assertTrue(DocumentSize.estimate(d)>sections+2000);
    }
    @Test public void tiebaFirstFloorIsMainPost(){
        Document d=SourceParser.article(Source.TIEBA,"<div class='l_post' data-field='{\"content\":{\"post_no\":1}}'><div class='d_post_content'>这里是一楼正文，应该优先展示。</div></div><div class='l_post' data-field='{\"content\":{\"post_no\":2}}'><div class='d_post_content'>这是二楼回复，应当独立折叠。</div></div>","https://tieba.baidu.com/p/123");
        assertFalse(d.sections.isEmpty());assertEquals("主帖",d.sections.get(0).label);
    }
}
