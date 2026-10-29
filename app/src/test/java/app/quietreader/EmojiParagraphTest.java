package app.quietreader;

import org.junit.Test;
import org.jsoup.Jsoup;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import java.util.*;

public class EmojiParagraphTest {
    private static final String IMAGE="https://tb3.bdstatic.com/emoji/image_emoticon33@2x.png";
    private org.jsoup.nodes.Document render(Block... blocks){
        Document doc=new Document();doc.title="排版边界";doc.url="https://tieba.baidu.com/p/11137152004";
        doc.blocks.addAll(Arrays.asList(blocks));return Jsoup.parse(ReaderHtml.render(doc,Source.TIEBA,19));
    }
    @Test public void separateEmojiLinesKeepTheirParagraphsLabelsAndReadingAnchors(){
        Block emoji=new Block("text","开心",List.of(new InlineImage(0,2,IMAGE,"开心")));
        org.jsoup.nodes.Document html=render(emoji,emoji,emoji);
        assertEquals(3,html.select(".part p.emoji-line").size());
        assertEquals(3,html.select(".part p[data-reading-anchor]").size());
        assertEquals(3,html.select(".part p.emoji-line img.emoji[alt=开心]").size());
        assertEquals(3,new HashSet<>(html.select(".part p").eachAttr("id")).size());
    }
    @Test public void proseAndMixedTextKeepNormalParagraphSpacing(){
        org.jsoup.nodes.Document html=render(new Block("text","普通文字"),new Block("text","文字开心",List.of(new InlineImage(2,4,IMAGE,"开心"))));
        assertEquals(2,html.select(".part p").size());assertTrue(html.select("p.emoji-line").isEmpty());
        assertEquals("文字",html.select(".part p").get(1).text());
    }
    @Test public void multipleInlineEmojiAndWhitespaceUseOneCompactParagraph(){
        Block block=new Block("text"," 开心 赞同 ",List.of(new InlineImage(1,3,IMAGE,"开心"),new InlineImage(4,6,IMAGE,"赞同")));
        org.jsoup.nodes.Document html=render(block);assertEquals(1,html.select("p.emoji-line").size());assertEquals(2,html.select("p.emoji-line img.emoji").size());
    }
    @Test public void invalidOrLinkedSpansDoNotHideReadableFallbackWithCompactSpacing(){
        Block bad=new Block("text","开心",List.of(new InlineImage(0,2,"http://example.org/emoji.png","开心")));
        Block overlap=new Block("text","开心",List.of(new InlineImage(0,2,IMAGE,"开心"),new InlineImage(0,2,IMAGE,"开心")));
        Block link=new Block("text","开心",List.of(new InlineImage(0,2,IMAGE,"开心")),List.of(new InlineLink(0,2,"https://example.org/")));
        org.jsoup.nodes.Document html=render(bad,overlap,link);assertTrue(html.select("p.emoji-line").isEmpty());assertEquals("开心",html.select(".part p").first().text());
    }
}
