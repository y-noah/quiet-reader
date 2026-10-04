package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.Source;

public class ForumIdentityTest {
    private static final String H="https://bbs.hupu.com/123.html";
    @Test public void sameDocumentAndPaginationHaveOneForumIdentity(){
        assertTrue(UrlPolicy.sameForumPost(Source.HUPU,H,H+"?page=2#reply"));
        assertTrue(UrlPolicy.sameForumPost(Source.HUPU,H,H));
        assertTrue(UrlPolicy.sameForumPost(Source.HUPU,H,"https://bbs.hupu.com/123-2.html"));
    }
    @Test public void differentPostIdsNeverMatch(){
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,H,"https://bbs.hupu.com/124-2.html"));
    }
    @Test public void loginAndHomeAreNotTheRequestedPost(){
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,H,"https://bbs.hupu.com/login"));
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,H,"https://bbs.hupu.com/"));
    }
    @Test public void bothEndpointsMustBeSafePlatformUrls(){
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,"https://evil.test/123.html",H));
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,H,"https://bbs.hupu.com.evil.test/123.html"));
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,H,"https://user@bbs.hupu.com/123.html"));
        assertFalse(UrlPolicy.sameForumPost(Source.HUPU,H,"https://bbs.hupu.com:8443/123.html"));
    }
    @Test public void nextPageStillExcludesTheIdenticalUrlAndOtherPlatforms(){
        assertFalse(UrlPolicy.sameThread(Source.HUPU,H,H));
        assertTrue(UrlPolicy.sameThread(Source.HUPU,H,"https://bbs.hupu.com/123-2.html"));
        assertFalse(UrlPolicy.sameThread(Source.HUPU,H,"https://passport.baidu.com/p/123?pn=2"));
        assertFalse(UrlPolicy.sameForumPost(Source.ZHIHU,"https://www.zhihu.com/question/123","https://www.zhihu.com/question/123"));
    }
}
