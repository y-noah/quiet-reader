package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class NavigationAndImagesTest {
    @Test public void scrollDirectionHasHysteresisAndTopAlwaysShows(){
        NavigationScroll bar=new NavigationScroll();
        assertFalse(bar.update(10,10));assertTrue(bar.update(20,10));
        assertTrue(bar.update(18,-2));assertTrue(bar.update(19,1));
        assertFalse(bar.update(10,-9));assertFalse(bar.update(11,1));
        assertTrue(bar.update(30,19));assertFalse(bar.update(0,-30));
        assertTrue(bar.update(100,100));bar.reset();assertFalse(bar.update(100,0));
    }
    private Document parse(String images){return SourceParser.article(Source.ITHOME,"<h1>图片</h1><div id='paragraph' class='post_content'><p>正文</p>"+images+"</div>","https://www.ithome.com/1/009/284.htm");}
    @Test public void ithomeUsesOriginalNotTransparentHttpPlaceholder(){
        Document doc=parse("<img src='//img.ithome.com/images/v2/t.png' data-original='https://img14.360buyimg.com/a.png'><img src='//img.ithome.com/images/v2/t.png' data-original='https://img.ithome.com/news/a.png?x=1&amp;y=2'>");
        assertEquals(3,doc.blocks.size());assertEquals("https://img14.360buyimg.com/a.png",doc.blocks.get(1).value);
        assertEquals("https://img.ithome.com/news/a.png?x=1&y=2",doc.blocks.get(2).value);
    }
    @Test public void ithomeFallbacksStaySafeAndDontInventMissingPhotos(){
        Document doc=parse("<img src='https://img.ithome.com/real.png'><img src='//img.ithome.com/images/v2/t.png'><img data-original='javascript:alert(1)' data-src='//img.ithome.com/fallback.png'>");
        assertEquals(3,doc.blocks.size());assertEquals("https://img.ithome.com/real.png",doc.blocks.get(1).value);
        assertEquals("https://img.ithome.com/fallback.png",doc.blocks.get(2).value);
        assertTrue(SourceParser.isImagePlaceholder("https://img.ithome.com/images/v2/t.png?x=1"));
        assertFalse(SourceParser.isImagePlaceholder("https://img.ithome.com.evil.test/images/v2/t.png"));
    }
}
