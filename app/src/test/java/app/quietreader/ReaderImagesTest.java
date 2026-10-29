package app.quietreader;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;

public class ReaderImagesTest {
    private static final String IMAGE="https://images.example.com/a.png",PAGE="https://example.com/article/1",REDIRECT="https://cdn.example.com/final.png";
    private static byte[] image(){return "GIF89a123456789".getBytes(StandardCharsets.US_ASCII);}
    @Test public void reusesOnlyMatchingImageAndArticleSessions()throws Exception{
        Map<String,String> cookies=new HashMap<>();cookies.put(IMAGE,"image_account=a");cookies.put(PAGE,"article_account=a");AtomicInteger calls=new AtomicInteger();
        ReaderImages cache=new ReaderImages(cookies::get,(url,ref,scoped)->{calls.incrementAndGet();assertEquals(cookies.get(IMAGE),scoped.apply(url));return image();},4096);
        cache.load(IMAGE,PAGE);cache.load(IMAGE,PAGE);assertEquals(1,calls.get());
        cookies.put(PAGE,"article_account=b");cache.load(IMAGE,PAGE);assertEquals(2,calls.get());
        cookies.put(IMAGE,"image_account=b");cache.load(IMAGE,PAGE);assertEquals(3,calls.get());
        cookies.remove(IMAGE);cache.load(IMAGE,PAGE);assertEquals(4,calls.get());
    }
    @Test public void redirectedCookieIdentityAlsoInvalidatesCache()throws Exception{
        Map<String,String> cookies=new HashMap<>();cookies.put(REDIRECT,"cdn=a");AtomicInteger calls=new AtomicInteger();
        ReaderImages cache=new ReaderImages(cookies::get,(url,ref,scoped)->{calls.incrementAndGet();scoped.apply(url);scoped.apply(REDIRECT);return image();},4096);
        cache.load(IMAGE,PAGE);cache.load(IMAGE,PAGE);assertEquals(1,calls.get());cookies.put(REDIRECT,"cdn=b");cache.load(IMAGE,PAGE);assertEquals(2,calls.get());
    }
    @Test public void accountChangeDuringDownloadIsRejectedAndNotCached()throws Exception{
        Map<String,String> cookies=new HashMap<>();cookies.put(PAGE,"account=a");AtomicInteger calls=new AtomicInteger();
        ReaderImages cache=new ReaderImages(cookies::get,(url,ref,scoped)->{if(calls.incrementAndGet()==1)cookies.put(PAGE,"account=b");return image();},4096);
        try{cache.load(IMAGE,PAGE);fail("late old account image accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("状态已变化"));}
        cache.load(IMAGE,PAGE);assertEquals(2,calls.get());cache.load(IMAGE,PAGE);assertEquals(2,calls.get());
    }
    @Test public void temporaryRotationWithinRedirectChainCannotBeHiddenByRotatingBack()throws Exception{
        Map<String,String> cookies=new HashMap<>();cookies.put(IMAGE,"account=a");
        ReaderImages cache=new ReaderImages(cookies::get,(url,ref,scoped)->{cookies.put(IMAGE,"account=b");scoped.apply(url);cookies.put(IMAGE,"account=a");return image();},4096);
        try{cache.load(IMAGE,PAGE);fail("mixed-session image accepted");}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("状态已变化"));}
    }
    @Test public void sourceReferencesAndRetryHaveIndependentCacheScope()throws Exception{
        AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(url->null,(url,ref,scoped)->{calls.incrementAndGet();return image();},4096);
        cache.load(IMAGE,PAGE);cache.load(IMAGE,PAGE+"/other");assertEquals(2,calls.get());cache.remove(IMAGE);cache.load(IMAGE,PAGE);cache.load(IMAGE,PAGE+"/other");assertEquals(4,calls.get());cache.evictAll();cache.load(IMAGE,PAGE);assertEquals(5,calls.get());
    }
    @Test public void invalidResponsesAndOversizedEntriesAreNotCached()throws Exception{
        AtomicInteger calls=new AtomicInteger();ReaderImages invalid=new ReaderImages(url->null,(url,ref,scoped)->{calls.incrementAndGet();return "login html".getBytes(StandardCharsets.US_ASCII);},4096);
        for(int n=0;n<2;n++)try{invalid.load(IMAGE,PAGE);fail("HTML accepted as image");}catch(java.io.IOException expected){}assertEquals(2,calls.get());
        calls.set(0);ReaderImages small=new ReaderImages(url->null,(url,ref,scoped)->{calls.incrementAndGet();return image();},1);small.load(IMAGE,PAGE);small.load(IMAGE,PAGE);assertEquals(2,calls.get());
    }
    @Test public void manyTinyImagesAreBoundedAndRecentlyReadImageSurvivesEviction()throws Exception{
        AtomicInteger calls=new AtomicInteger();ReaderImages cache=new ReaderImages(url->null,(url,ref,scoped)->{calls.incrementAndGet();return image();},1024*1024);
        for(int n=0;n<128;n++)cache.put(IMAGE+n,PAGE,image());
        cache.load(IMAGE+0,PAGE);cache.put(IMAGE+128,PAGE,image());cache.load(IMAGE+0,PAGE);assertEquals(0,calls.get());cache.load(IMAGE+1,PAGE);assertEquals(1,calls.get());
    }
}
