package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.Source;

public class PlatformNavigationTest {
    @Test public void onlyFiveVisibleSourcesCanBeReopenedAsReaderEntries(){
        for(Source source:Source.values())assertEquals(source.visible(),source.readable());
        for(String url:new String[]{"https://www.douban.com/gallery/topic/1/","https://www.douban.com/group/topic/1/","https://www.guokr.com/article/1/","https://www.ithome.com/1/009/232.htm","https://juejin.cn/post/1","https://sspai.com/post/1","https://wallstreetcn.com/articles/1","https://www.geekpark.net/news/1"})
            assertNull(ReaderLinks.readerSource(url));
    }
    @Test public void observedMobileSearchUpgradesWithoutChangingEncodedSearch(){
        String suffix="/search?containerid=100103type%3D1%26q%3D%23test%23&q=a%2Fb+%2520#part%2F1";
        assertEquals("https://m.weibo.cn"+suffix,UrlPolicy.upgradePlatformNavigation(Source.WEIBO,"http://m.weibo.cn"+suffix));
        assertEquals("https://m.weibo.cn/search",UrlPolicy.upgradePlatformNavigation(Source.WEIBO,"HTTP://M.WEIBO.CN:80/search"));
        assertFalse(UrlPolicy.loginAllowed(Source.WEIBO,"http://m.weibo.cn/search"));
    }
    @Test public void secureAndCustomSchemesDoNotTriggerUpgrade(){
        for(String url:new String[]{"https://m.weibo.cn/search","sinaweibo://m.weibo.cn/search","intent://m.weibo.cn/search","javascript:alert(1)","//m.weibo.cn/search",null})
            assertEquals("",UrlPolicy.upgradePlatformNavigation(Source.WEIBO,url));
    }
    @Test public void credentialsNonstandardPortsAndMalformedUrlsStayBlocked(){
        for(String url:new String[]{"http://user@m.weibo.cn/search","http://m.weibo.cn:443/search","http://m.weibo.cn:8080/search","http://m.weibo.cn/a b","http:///m.weibo.cn/search","http://m.weibo.cn%2Eevil.test/search"})
            assertEquals("",UrlPolicy.upgradePlatformNavigation(Source.WEIBO,url));
    }
    @Test public void noOtherPlatformLookalikeOrLoginExceptionIsUpgraded(){
        for(String url:new String[]{"http://m.weibo.cn.evil.test/search","http://evilweibo.cn/search","http://login.sina.com.cn/sso/login.php","http://passport.sina.cn/signin","http://www.zhihu.com/question/1","http://127.0.0.1/"})
            assertEquals("",UrlPolicy.upgradePlatformNavigation(Source.WEIBO,url));
    }
    @Test public void otherPlatformsKeepTheirOwnAllowlist(){
        assertEquals("https://bbs.hupu.com/123-2.html",UrlPolicy.upgradePlatformNavigation(Source.HUPU,"http://bbs.hupu.com/123-2.html"));
        assertEquals("",UrlPolicy.upgradePlatformNavigation(Source.HUPU,"http://m.weibo.cn/search"));
        assertEquals("",UrlPolicy.upgradePlatformNavigation(Source.HUPU,"http://evil.ycombinator.com/"));
    }
}
