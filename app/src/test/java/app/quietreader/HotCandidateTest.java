package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import org.junit.Test;
import static org.junit.Assert.*;

public class HotCandidateTest {
    @Test public void cacheIdentityKeepsPublishedSha256Vectors(){
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",HotCandidate.hash(""));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",HotCandidate.hash("abc"));
    }
    private HotCandidate candidate(String url,String title,long time){return new HotCandidate(Models.Source.ZHIHU,url,title,"摘要","1万热度",false,1,null,time);}
    @Test public void refreshAndHeatDoNotChangeEmbeddingIdentity(){
        HotCandidate a=candidate("https://www.zhihu.com/question/123?utm_source=feed","中文标题",1);
        HotCandidate b=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/123","中文标题","摘要","9亿热度",false,9,null,999);
        assertEquals(a.id,b.id);assertEquals(a.textHash,b.textHash);assertNull(b.publishedAt);
    }
    @Test public void identityPreservesContentQueriesAndHashRoutes(){
        assertNotEquals(candidate("https://example.org/?id=1&page=1","标题",1).id,candidate("https://example.org/?id=1&page=2","标题",1).id);
        assertEquals("https://example.org/?sig=a%2Bb&id=2#/page",HotCandidate.canonical("https://EXAMPLE.org:443/?sig=a%2Bb&utm_medium=x&id=2#/page"));
    }
    @Test public void normalizationRemovesInvalidControlsAndKeepsFacts()throws Exception{
        HotCandidate a=candidate("https://www.zhihu.com/question/123","<b>标题</b>\u0000\u0001  cafe\u0301 👩‍💻",1);
        assertFalse(a.title.contains("\0"));assertTrue(a.title.contains("café"));
        assertEquals(a.id,HotCandidate.fromJson(a.json()).id);assertEquals(a.originalUrl,a.readerItem().url);
        assertEquals("中🙂",HotCandidate.clean("中🙂文",2));
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsNonSourceScheme(){candidate("javascript:alert(1)","标题",1);}
    @Test public void literalTechnicalTitlesSurviveNormalization(){
        assertEquals("为什么 <video> 不播放？a < b &amp; c > d",HotCandidate.clean("为什么 <video> 不播放？a < b &amp; c > d",240));
        assertEquals("git checkout <branch> 有什么限制",HotCandidate.clean("git checkout <branch> 有什么限制",240));
    }
}
