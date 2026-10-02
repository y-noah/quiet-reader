package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class WeiboImageTest {
    @Test public void observedGalleryRenditionKeepsTheExactImageIdentity(){
        for(int host=1;host<=4;host++)assertEquals("https://wx"+host+".sinaimg.cn/mw2000/Example123.jpg",
                WeiboImage.preview(Source.WEIBO,"https://wx"+host+".sinaimg.cn/orj360/Example123.jpg"));
    }
    @Test public void unknownSignedOrUnsafeRoutesAreNotRewritten(){
        for(String url:new String[]{"http://wx4.sinaimg.cn/orj360/a.jpg","https://wx4.sinaimg.cn.evil.example/orj360/a.jpg",
                "https://user@wx4.sinaimg.cn/orj360/a.jpg","https://wx4.sinaimg.cn:8443/orj360/a.jpg",
                "https://wx4.sinaimg.cn/orj360/a.jpg?signature=example","https://wx4.sinaimg.cn/orj360/a.jpg#part",
                "https://wx4.sinaimg.cn/orj360/a%31.jpg","https://wx4.sinaimg.cn/orj360/path/a.jpg",
                "https://wx4.sinaimg.cn/mw2000/a.jpg","https://wx4.sinaimg.cn/other/a.jpg",
                "https://wx4.sinaimg.cn/orj360/a.gif","https://wx9.sinaimg.cn/orj360/a.jpg","javascript:alert(1)"})
            assertEquals(url,url,WeiboImage.preview(Source.WEIBO,url));
    }
    @Test public void otherPlatformsKeepTheirImageRequestUnchanged(){
        String url="https://wx4.sinaimg.cn/orj360/a.jpg";
        for(Source source:Source.values())if(source!=Source.WEIBO)assertEquals(url,WeiboImage.preview(source,url));
    }
    @Test public void readerStillUsesTheSmallImageUntilTheUserRequestsPreview(){
        String url="https://wx4.sinaimg.cn/orj360/a.jpg";
        Document d=new Document();d.url="https://m.weibo.cn/status/123";d.blocks.add(new Block("image",url));
        String html=ReaderHtml.render(d,Source.WEIBO,19);
        assertTrue(html.contains("src='"+url+"'"));assertFalse(html.contains("/mw2000/"));
        assertEquals(url,d.blocks.get(0).value);
    }
}
