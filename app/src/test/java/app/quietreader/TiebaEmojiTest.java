package app.quietreader;

import static app.quietreader.Models.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TiebaEmojiTest {
    private static final String URL="https://tieba.baidu.com/p/991081",IMAGE="https://tb3.bdstatic.com/emoji/image_emoticon33@2x.png";
    private Document post(String body){return SourceParser.article(Source.TIEBA,"<h1>表情测试</h1><div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_991081>"+body+"</div></div></div>",URL);}
    @Test public void oldNewAndContainerEmojiStayInlineAmongUnicodeText(){
        Document d=post("<p>开头🐮<img class=BDE_Smiley src='"+IMAGE+"' alt='微笑'>中间<img class=emoji-img src='"+IMAGE+"' title='开心'><span class=pb-emot><img src='"+IMAGE+"'></span>结束</p>");
        assertEquals(1,d.blocks.size());Block b=d.blocks.get(0);assertEquals(3,b.inlineImages.size());assertTrue(b.value.startsWith("开头🐮"));assertTrue(b.value.endsWith("结束"));
        org.jsoup.nodes.Document html=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.TIEBA,19));assertEquals(3,html.select("p img.emoji").size());assertEquals(0,html.select("figure").size());assertEquals(0,html.select("img[onerror],script").size());
        assertEquals(List.of("微笑","开心","表情"),html.select("img.emoji").eachAttr("alt"));assertFalse(html.select("p").text().contains("[表情]"));
    }
    @Test public void lazyCandidatesSkipInvalidPlaceholdersAndUseAnAvailableHttpsImage(){
        for(String attributes:List.of("src='data:image/gif;base64,AA' data-src='data:image/gif;base64,BB' data-original='"+IMAGE+"'","src='' data-src='javascript:alert(1)' data-original='"+IMAGE+"'","src='blob:placeholder' data-original='"+IMAGE+"'","data-src='//tb3.bdstatic.com/emoji/image_emoticon33@2x.png'")){
            Document d=post("<p>正文<img class=BDE_Smiley "+attributes+" alt='微笑'>继续</p>");assertEquals(attributes,1,d.blocks.get(0).inlineImages.size());assertEquals(IMAGE,d.blocks.get(0).inlineImages.get(0).url);
        }
    }
    @Test public void missingImageHasSpecificTextFallbackWithoutAnObjectReplacementGlyph(){
        Document d=post("<p>前<img class=emoji-img src='javascript:bad' alt='微笑'>中<img class=emoji-img src='data:bad'>后</p>");
        assertEquals("前微笑中后",d.blocks.get(0).value);assertTrue(d.blocks.get(0).inlineImages.isEmpty());
        assertFalse(ReaderHtml.render(d,Source.TIEBA,19).contains("\uFFFC"));
    }
    @Test public void oldNewReplyAndNestedEmojiKeepAuthorsAndSnapshotSpans()throws Exception{
        String html="<div id=j_p_postlist><div class=l_post><a class=d_name>楼主</a><div class=d_post_content id=post_content_991081>主帖<img class=BDE_Smiley src='"+IMAGE+"' alt='笑'></div></div><div class=l_post><a class=d_name>读者</a><div class=d_post_content id=post_content_991082>回复<img class=emoji-img src='"+IMAGE+"' alt='开心'></div><div class=lzl_single_post><a class=j_user_card>子回复</a><span class=lzl_content_main>楼中楼<span class=pb-emot><img src='"+IMAGE+"' alt='赞'></span></span></div></div></div>";
        Document d=SourceParser.article(Source.TIEBA,html,URL),restored=ReadingHistory.decode(ReadingHistory.encode(d));assertEquals(3,restored.sections.size());
        assertEquals(List.of("正文 · 楼主","回复 · 读者","回复 · 子回复"),restored.sections.stream().map(s->s.label).toList());assertEquals(3,restored.blocks.stream().mapToInt(b->b.inlineImages.size()).sum());assertTrue(restored.containsImage(IMAGE));
        assertEquals(ReaderHtml.render(d,Source.TIEBA,19),ReaderHtml.render(restored,Source.TIEBA,19));
    }
    @Test public void untrustedAltMarkupIsOnlyAnAccessibleLabel(){
        Document d=post("正文<img class=emoji-img src='"+IMAGE+"' alt='&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;'>结束");String html=ReaderHtml.render(d,Source.TIEBA,19);
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(html);assertEquals(1,page.select("img.emoji").size());assertTrue(page.select("script,[onerror]").isEmpty());assertTrue(page.selectFirst("img").attr("alt").contains("<script>"));
    }
}
