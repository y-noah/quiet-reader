package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class WeiboBoardTest {
    private String feed(String key,String rows){return "{\"ok\":1,\"data\":{\""+key+"\":["+rows+"]}}";}
    private String row(String title){return "{\"word\":\""+title+"\",\"num\":12345}";}
    @Test public void officialBandKeepsSourceOrderAndMetrics()throws Exception{
        List<Item> rows=SourceParser.list(Source.WEIBO,feed("band_list",row("第二个字母B")+","+row("第一个字母A")));
        assertEquals(2,rows.size());assertEquals("第二个字母B",rows.get(0).title);assertEquals("12345",rows.get(0).detail);
        assertTrue(rows.get(0).url.startsWith("https://s.weibo.com/weibo?q="));
    }
    @Test public void oldSchemaStillWorks()throws Exception{assertEquals("旧接口",SourceParser.list(Source.WEIBO,feed("realtime",row("旧接口"))).get(0).title);}
    @Test public void skipsAdsAndDuplicates()throws Exception{
        assertEquals(1,SourceParser.list(Source.WEIBO,feed("band_list",row("正常")+","+row("正常")+",{\"word\":\"广告\",\"is_ad\":1}")).size());
    }
    @Test public void noteFallbackDoesNotAcceptBlankTitle()throws Exception{
        List<Item> rows=SourceParser.list(Source.WEIBO,feed("band_list","{\"word\":\"\",\"note\":\"标题 & 标点?\",\"num\":9},{\"word\":\" \"}"));
        assertEquals(1,rows.size());assertEquals("标题 & 标点?",rows.get(0).title);assertTrue(rows.get(0).url.contains("%26"));
    }
    @Test public void primarySuccessDoesNotFetchLegacy()throws Exception{
        List<String> calls=new ArrayList<>();WeiboBoard.load(url->{calls.add(url);return feed("band_list",row("新榜"));});
        assertEquals(Collections.singletonList(WeiboBoard.PRIMARY),calls);
    }
    @Test public void primaryFailureTriesOfficialLegacy()throws Exception{
        List<String> calls=new ArrayList<>();List<Item> rows=WeiboBoard.load(url->{calls.add(url);if(url.equals(WeiboBoard.PRIMARY))throw new java.io.IOException("403");return feed("realtime",row("备用"));});
        assertEquals(Arrays.asList(WeiboBoard.PRIMARY,WeiboBoard.LEGACY),calls);assertEquals("备用",rows.get(0).title);
    }
    @Test public void visitorPageIsFailureNotFreshEmptyBoard()throws Exception{
        assertEquals("备用",WeiboBoard.load(url->url.equals(WeiboBoard.PRIMARY)?"<title>Sina Visitor System</title>":feed("realtime",row("备用"))).get(0).title);
    }
    @Test public void bothFailuresAreReported()throws Exception{
        try{WeiboBoard.load(url->"<h1>请登录</h1>");fail("Invalid responses accepted");}catch(IllegalStateException expected){assertEquals(1,expected.getSuppressed().length);}
    }
    @Test public void unsuccessfulStatusIsRejectedEvenWithRows()throws Exception{
        try{SourceParser.list(Source.WEIBO,feed("band_list",row("不能发布")).replace("\"ok\":1","\"ok\":0"));fail();}catch(IllegalStateException expected){}
    }
    @Test public void emptyOfficialFeedCannotClearCache()throws Exception{
        try{WeiboBoard.load(url->feed("band_list",""));fail();}catch(IllegalStateException expected){}
    }
    @Test public void onlyKnownOldAndNewCacheEndpointsAreCompatible(){
        assertTrue(WeiboBoard.compatibleCache(WeiboBoard.LEGACY));assertTrue(WeiboBoard.compatibleCache(WeiboBoard.PRIMARY));
        for(String url:new String[]{"", "https://weibo.com/ajax/other","https://evil.test/ajax/statuses/hot_band",WeiboBoard.PRIMARY+"?other=1"})assertFalse(WeiboBoard.compatibleCache(url));
    }
    @Test public void cancellationDoesNotStartFallback()throws Exception{
        List<String> calls=new ArrayList<>();
        try{WeiboBoard.load(url->{calls.add(url);Thread.currentThread().interrupt();throw new java.io.InterruptedIOException();});fail();}
        catch(java.io.InterruptedIOException expected){assertEquals(1,calls.size());}
        finally{Thread.interrupted();}
    }
}
