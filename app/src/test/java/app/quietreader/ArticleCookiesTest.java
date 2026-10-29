package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
public class ArticleCookiesTest {
    @Test public void authenticatedZhihuPageContextRotationCannotInvalidateSameAccountBody(){
        String url="https://www.zhihu.com/question/123",first="z_c0=account-one; d_c0=device; BEC=one; JOID=one; osd=one; SESSIONID=one; Hm_lpvt_98beee57fd2ef70ccdd5ca52b9740c49=one";
        String second=first.replace("BEC=one","BEC=two").replace("JOID=one","JOID=two").replace("osd=one","osd=two").replace("SESSIONID=one","SESSIONID=two").replace("c49=one","c49=two");
        assertEquals(ArticleCookies.fingerprint(url,first),ArticleCookies.fingerprint(url,second));
        assertNotEquals(ArticleCookies.fingerprint(url,first),ArticleCookies.fingerprint(url,second.replace("account-one","account-two")));
        assertNotEquals(ArticleCookies.fingerprint(url,first),ArticleCookies.fingerprint(url,second.replace("d_c0=device","d_c0=other")));
        assertNotEquals(ArticleCookies.fingerprint(url,first),ArticleCookies.fingerprint(url,second+"; unknown_risk=new"));
        assertNotEquals(ArticleCookies.fingerprint(url,first),ArticleCookies.fingerprint(url,second.replace("z_c0=account-one; ","")));
    }
    @Test public void anonymousAndNonContentZhihuKeepPageSessionIdentity(){
        String first="SESSIONID=one; BEC=one; JOID=one; osd=one",second=first.replace("one","two");
        for(String url:new String[]{"https://www.zhihu.com/question/123","https://www.zhihu.com/","https://www.zhihu.com/signin","https://www.zhihu.com.evil.test/question/123"})
            assertNotEquals(url,ArticleCookies.fingerprint(url,first),ArticleCookies.fingerprint(url,second));
        assertNotEquals(ArticleCookies.fingerprint("https://www.zhihu.com/question/123","z_c0=; "+first),ArticleCookies.fingerprint("https://www.zhihu.com/question/123","z_c0=; "+second));
    }
    @Test public void zhihuRequestContextAndDistinctCookieOrderDoNotInvalidateBody(){
        String url="https://www.zhihu.com/question/123/answer/456";
        assertEquals(ArticleCookies.fingerprint(url,"z_c0=account; d_c0=device; _xsrf=before; _zap=before; _ga_ABC=one"),ArticleCookies.fingerprint(url,"_xsrf=after; d_c0=device; z_c0=account; _zap=after; _ga_ABC=two"));
        for(String key:new String[]{"z_c0","d_c0","__zse_ck","unknown_auth","unknown_field"})
            assertNotEquals(key,ArticleCookies.fingerprint(url,key+"=before; _xsrf=first"),ArticleCookies.fingerprint(url,key+"=after; _xsrf=second"));
        assertNotEquals(ArticleCookies.fingerprint(url,"z_c0=account"),ArticleCookies.fingerprint(url,""));
        assertNotEquals(ArticleCookies.fingerprint(url,"z_c0=path; z_c0=root"),ArticleCookies.fingerprint(url,"z_c0=root; z_c0=path"));
        for(String other:new String[]{"https://www.zhihu.com/","https://www.zhihu.com/signin","https://www.zhihu.com.evil.test/question/123","https://other.zhihu.com/question/123"})
            assertNotEquals(other,ArticleCookies.fingerprint(other,"_xsrf=first"),ArticleCookies.fingerprint(other,"_xsrf=second"));
    }
    @Test public void douyinActivityFlagDoesNotChangeAccountIdentity(){
        for(String url:new String[]{"https://www.douyin.com/hot/123/title","https://www.douyin.com/video/456"}){
            assertEquals(ArticleCookies.fingerprint(url,"sessionid=account; IsDouyinActive=false"),ArticleCookies.fingerprint(url,"sessionid=account; IsDouyinActive=true"));
            assertEquals(ArticleCookies.fingerprint(url,null),ArticleCookies.fingerprint(url,"IsDouyinActive=true"));
            assertEquals(ArticleCookies.fingerprint(url,"sessionid=account; stream_recommend_feed_params=first"),ArticleCookies.fingerprint(url,"sessionid=account; stream_recommend_feed_params=second; IsDouyinActive=true"));
            for(String key:new String[]{"sessionid","sessionid_ss","passport_csrf_token","unknown_auth","unknown_field","__ac_nonce","__ac_signature","strategyABtestKey"})
                assertNotEquals(key,ArticleCookies.fingerprint(url,key+"=before; IsDouyinActive=false"),ArticleCookies.fingerprint(url,key+"=after; IsDouyinActive=true"));
        }
    }
    @Test public void activityExceptionIsLimitedToKnownDouyinContentPaths(){
        for(String url:new String[]{"https://www.douyin.com/","https://www.douyin.com/hot","https://www.douyin.com/login","https://www.douyin.com/hot/123/title/extra","https://www.douyin.com.evil.test/hot/123","https://www.zhihu.com/question/1"})
            assertNotEquals(url,ArticleCookies.fingerprint(url,"IsDouyinActive=false"),ArticleCookies.fingerprint(url,"IsDouyinActive=true"));
    }
    @Test public void baiduSearchAccountIdentitySurvivesRequestContextRotation(){
        String url="https://www.baidu.com/s?wd=test";
        assertEquals(ArticleCookies.fingerprint(url,"BDUSS=account; H_PS_645EC=first"),ArticleCookies.fingerprint(url,"BDUSS=account; H_PS_645EC=second"));
        assertNotEquals(ArticleCookies.fingerprint(url,"BDUSS=first; H_PS_645EC=a"),ArticleCookies.fingerprint(url,"BDUSS=second; H_PS_645EC=a"));
        assertNotEquals(ArticleCookies.fingerprint(url,"STOKEN=first"),ArticleCookies.fingerprint(url,"STOKEN=second"));
        assertNotEquals(ArticleCookies.fingerprint(url,"unknown_auth=first"),ArticleCookies.fingerprint(url,"unknown_auth=second"));
        assertNotEquals(ArticleCookies.fingerprint(url,"session_id=first"),ArticleCookies.fingerprint(url,"session_id=second"));
        assertNotEquals(ArticleCookies.fingerprint(url,"BDUSS_BFESS=first"),ArticleCookies.fingerprint(url,"BDUSS_BFESS=second"));
        assertEquals(ArticleCookies.fingerprint(url,"BDUSS=account; BA_HECTOR=first; PSINO=a; ZFY=a"),ArticleCookies.fingerprint(url,"BDUSS=account; BA_HECTOR=second; PSINO=b; ZFY=b"));
    }
    @Test public void otherPagesAndHostsKeepAllCookieEvidence(){
        for(String url:new String[]{"https://www.baidu.com/","https://www.zhihu.com/question/1","https://www.baidu.com.evil.test/s","https://tieba.baidu.com/f"})
            assertNotEquals(ArticleCookies.fingerprint(url,"H_PS_645EC=a"),ArticleCookies.fingerprint(url,"H_PS_645EC=b"));
        assertEquals(ArticleCookies.fingerprint("https://www.baidu.com/s",null),ArticleCookies.fingerprint("https://www.baidu.com/s","H_PS_645EC=page-context"));
        assertNotEquals(ArticleCookies.fingerprint("https://tieba.baidu.com/p/1","BDUSS=first"),ArticleCookies.fingerprint("https://tieba.baidu.com/p/1","BDUSS=second"));
    }
    @Test public void tiebaVisitorAndAnalyticsIdsDoNotMasqueradeAsAccountChanges(){
        for(String url:new String[]{"https://tieba.baidu.com/p/123","https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=123"}){
            String visitor="BAIDUID=one; BAIDUID_BFESS=one; BAIDU_WISE_UID=one; HMACCOUNT=one";
            assertEquals(ArticleCookies.fingerprint(url,"BDUSS=account; "+visitor),ArticleCookies.fingerprint(url,"BDUSS=account; "+visitor.replace("one","two")));
            assertEquals(ArticleCookies.fingerprint(url,null),ArticleCookies.fingerprint(url,visitor));
            for(String key:new String[]{"BDUSS","BDUSS_BFESS","PTOKEN","STOKEN","PASSID","unknown_auth","some_uid","session_id","USER_JUMP"})
                assertNotEquals(key,ArticleCookies.fingerprint(url,key+"=one; "+visitor),ArticleCookies.fingerprint(url,key+"=two; "+visitor));
        }
        for(String url:new String[]{"https://tieba.baidu.com/","https://tieba.baidu.com/f","https://tieba.baidu.com.evil.test/p/123"})
            assertNotEquals(url,ArticleCookies.fingerprint(url,"HMACCOUNT=one"),ArticleCookies.fingerprint(url,"HMACCOUNT=two"));
    }
}
