package app.quietreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class InlineImageTest {
    private static final String EMOJI="https://tb3.bdstatic.com/emoji/image_emoticon95@2x.png";
    private static final String PHOTO="https://tiebapic.baidu.com/forum/pic/item/photo.jpg";
    private Document parse(String body){return SourceParser.article(Source.TIEBA,"<div class=d_post_content>"+body+"</div>","https://tieba.baidu.com/p/11061609054");}
    private String emoji(String alt){return "<img src='"+EMOJI+"' alt='"+alt+"'>";}

    @Test public void emojiStaysInsideParagraphAndPhotoStaysFigure(){
        Document d=parse("<p>  😀前"+emoji("笑")+"后 </p><p>下一段</p><img src='"+PHOTO+"'>");
        assertEquals(3,d.blocks.size());assertEquals("😀前[笑]后",d.blocks.get(0).value);
        InlineImage i=d.blocks.get(0).inlineImages.get(0);assertEquals(3,i.start);assertEquals(6,i.end);
        assertEquals("下一段",d.blocks.get(1).value);assertEquals("image",d.blocks.get(2).type);
        org.jsoup.nodes.Document rendered=Jsoup.parse(ReaderHtml.render(d,Source.TIEBA,18));
        assertEquals(2,rendered.select("section > p").size());
        assertEquals(1,rendered.select("p > img.emoji").size());
        assertEquals("😀前",rendered.selectFirst("section > p").textNodes().get(0).text());
        assertEquals("后",rendered.selectFirst("section > p").textNodes().get(1).text());
        assertEquals(PHOTO,rendered.selectFirst("figure img").attr("src"));
        assertEquals(1,rendered.select("figcaption").size());
        assertTrue(d.containsImage(EMOJI));assertTrue(d.containsImage(PHOTO));assertFalse(d.containsImage("https://evil.test/a.png"));
    }
    @Test public void emojiOnlyAndRepeatedEmojiRemainReadable(){
        Document d=parse("<p>"+emoji("")+emoji("哭")+"</p><p>结尾</p>");
        assertEquals("[表情][哭]",d.blocks.get(0).value);assertEquals(2,d.blocks.get(0).inlineImages.size());
        org.jsoup.nodes.Document h=Jsoup.parse(ReaderHtml.render(d,Source.TIEBA,28));
        assertEquals(2,h.select("p > img.emoji").size());assertEquals(0,h.select("figure").size());
        assertEquals("结尾",h.select("section > p").get(1).text());
    }
    @Test public void unknownSmallImagesNotMisclassifiedAndAltIsEscaped(){
        Document d=parse("<p>前"+emoji("&lt;script&gt;&quot; onclick=&quot;bad")+"后</p><img width=20 height=20 alt='表情' src='"+PHOTO+"'>");
        org.jsoup.nodes.Document h=Jsoup.parse(ReaderHtml.render(d,Source.TIEBA,18));
        assertEquals(1,h.select("figure").size());assertEquals(0,h.select("script,[onclick]").size());
        assertEquals("<script>\" onclick=\"bad",h.selectFirst("img.emoji").attr("alt"));
        Document spoof=parse("<img src='https://tb3.bdstatic.com.evil.test/emoji/image_emoticon95@2x.png'>");
        assertEquals("image",spoof.blocks.get(0).type);
    }
    @Test public void foldingUsesAccessiblePreviewWithoutCountingEmojiAsPhoto(){
        Document d=parse("<p>"+emoji("笑")+"第一楼</p></div><div class=d_post_content><p>第二楼</p>");
        org.jsoup.nodes.Document h=Jsoup.parse(ReaderHtml.render(d,Source.TIEBA,18));
        assertTrue(h.selectFirst(".preview").text().contains("[笑]"));
        assertFalse(h.text().contains("张图片"));assertEquals(0,h.select("img").size());
    }
    @Test public void nativeTextZoomCanSetIndependentInlineImageSize(){
        Document d=parse("<p>前"+emoji("笑")+"后</p>");
        String large=ReaderHtml.render(d,Source.TIEBA,19,true,java.util.Collections.emptySet(),false,25);
        assertTrue(large.contains("p{font-size:19px"));
        assertTrue(large.contains("img.emoji{width:33.75px;height:33.75px}"));
        assertTrue(large.contains("--bg:#1f2025"));
    }
}
