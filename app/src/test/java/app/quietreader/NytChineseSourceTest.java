package app.quietreader;
import org.junit.Test;
import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
public class NytChineseSourceTest {
    private String resource(String name)throws Exception{try(java.io.InputStream in=getClass().getResourceAsStream("/nyt-chinese-20261101/"+name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    @Test public void officialDailyListRetainsRankWithoutUsingTrafficAsCrossSourceHeat()throws Exception{
        List<Item> items=SourceParser.list(Source.NYT,resource("mostviewed.json"));assertEquals(10,items.size());
        assertEquals("每日热门 #1",items.get(0).detail);assertTrue(items.stream().allMatch(i->!i.video&&NytChineseSource.articleUrl(i.url)));
        assertTrue(app.quietreader.discovery.HotSourceCatalog.defaultEnabled(Source.NYT));
        assertEquals(Source.NYT,ReaderLinks.readerSource(items.get(0).url));
    }
    @Test public void allObservedProseSurvivesWithImagesAndWithoutAdRail()throws Exception{
        List<Item> items=SourceParser.list(Source.NYT,resource("mostviewed.json"));
        for(int n=0;n<3;n++){
            String html=resource("article-"+n+".html");Document doc=SourceParser.article(Source.NYT,html,items.get(n).url);
            assertTrue(doc.hasContent());assertFalse(doc.sourceUnavailable);assertFalse(doc.loginRequired);
            String text=doc.blocks.stream().map(b->b.value).collect(java.util.stream.Collectors.joining(" ")).replaceAll("\\s+","");
            int checked=0;for(Element paragraph:Jsoup.parse(html).select(".article-body-item .article-paragraph")){
                Element copy=paragraph.clone();copy.select("figure,script,style,iframe").remove();String expected=copy.text().replaceAll("\\s+","");
                if(!expected.isEmpty()){assertTrue("article "+n+" paragraph "+checked,text.contains(expected));checked++;}
            }
            assertTrue(checked>=15);assertTrue(doc.blocks.stream().anyMatch(b->b.type.equals("image")));
            String rendered=ReaderHtml.render(doc,Source.NYT,19,true,Set.of(doc.sections.get(0).id),false);
            assertFalse(rendered.contains("medium-rectangle-ad"));assertFalse(rendered.contains("<iframe"));
        }
    }
    @Test public void wrongIdentityMissingBodyAndForeignHostCannotBecomeArticle()throws Exception{
        String html=resource("article-0.html"),url=SourceParser.list(Source.NYT,resource("mostviewed.json")).get(0).url;
        assertTrue(SourceParser.article(Source.NYT,html,"https://cn.nytimes.com/china/20261030/another-story/").sourceUnavailable);
        assertTrue(SourceParser.article(Source.NYT,"<article>订阅后阅读</article>",url).sourceUnavailable);
        assertFalse(NytChineseSource.articleUrl(url.replace("cn.nytimes.com","cn.nytimes.com.evil.test")));
        assertFalse(NytChineseSource.articleUrl(url+"dual/"));
    }
}
