package app.quietreader;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ThepaperSourceTest {
    private JSONObject entry(String id,String title,int type)throws Exception{return new JSONObject().put("contId",id).put("name",title).put("contType",type).put("forwardType","0");}
    private String board(JSONArray hot)throws Exception{return new JSONObject().put("resultCode",1).put("data",new JSONObject().put("hotNews",hot).put("editorHandpicked",new JSONArray().put(entry("999","编辑精选不能混入",0)))).toString();}
    private String article(JSONObject detail,String pageId)throws Exception{return "<aside>侧栏不能混入</aside><script id=__NEXT_DATA__ type=application/json>"+new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("contId",pageId).put("detailData",new JSONObject().put("contentDetail",detail))))+"</script>";}
    private JSONObject body()throws Exception{return entry("12","真实文章标题",0).put("author","测试作者").put("summary","摘要不能替代正文").put("content","<p>"+"这是完整正文。".repeat(30)+"</p><img src='https://image.thepaper.cn/real.jpg'><script>untrusted()</script>");}
    @Test public void onlyHotRowsKeepOrderMetadataAndVideoFlags()throws Exception{
        JSONArray hot=new JSONArray().put(entry("12","第一篇热门",0).put("pubTimeLong",123456).put("summary","真实摘要")).put(entry("13","热门视频",9)).put(entry("12","重复",0)).put(entry("../15","坏编号",0)).put(entry("14","外站",0).put("link","https://other.test/")).put(entry("15","未知直播",25));
        List<Item> rows=SourceParser.list(Source.THEPAPER,board(hot));assertEquals(2,rows.size());assertEquals("第一篇热门",rows.get(0).title);assertEquals(Long.valueOf(123456),rows.get(0).publishedAt);assertEquals("真实摘要",rows.get(0).snippet);assertTrue(rows.get(1).video);assertEquals("热榜 #2",rows.get(1).detail);assertEquals(1,VideoPolicy.filter(rows).size());
        assertTrue(app.quietreader.discovery.HotSourceCatalog.defaultEnabled(Source.THEPAPER));
        List<app.quietreader.discovery.HotCandidate> candidates=new ArrayList<>();for(Item item:rows)candidates.add(new app.quietreader.discovery.HotCandidate(item.source,item.url,item.title,item.snippet,"",item.video,candidates.size()+1,item.publishedAt,1));
        List<app.quietreader.discovery.FeedStore.Row> feed=app.quietreader.discovery.CandidateOrder.arrange(candidates,new JSONArray(),false);assertEquals(1,feed.size());assertEquals("第一篇热门",feed.get(0).candidate.title);
    }
    @Test public void missingHotOrErrorNeverUsesEditorialFeed()throws Exception{
        for(String raw:Arrays.asList("{\"resultCode\":0,\"data\":{\"hotNews\":[]}}","{\"resultCode\":1,\"data\":{\"editorHandpicked\":[]}}",board(new JSONArray())))try{SourceParser.list(Source.THEPAPER,raw);fail();}catch(JSONException|IllegalStateException expected){}
    }
    @Test public void matchedBodyAndBylineEnterCommonReaderWithoutSidebarOrScript()throws Exception{
        Document doc=SourceParser.article(Source.THEPAPER,article(body(),"12"),"https://www.thepaper.cn/newsDetail_forward_12");assertTrue(doc.hasContent());assertEquals("真实文章标题",doc.title);assertEquals("测试作者",doc.byline);assertEquals(1,doc.sections.size());assertTrue(doc.blocks.stream().anyMatch(b->b.type.equals("image")&&b.value.endsWith("real.jpg")));
        String rendered=ReaderHtml.render(doc,Source.THEPAPER,19);assertFalse(rendered.contains("侧栏不能混入"));assertFalse(rendered.contains("摘要不能替代正文"));assertFalse(rendered.contains("untrusted()"));
    }
    @Test public void mismatchedIdentityMissingBodyAndPaywallFailClosed()throws Exception{
        for(JSONObject detail:Arrays.asList(body().put("contId","13"),body().put("content",""),body().put("paywalled",true),body().put("contType",9),body().put("link","https://other.test/"))){Document doc=SourceParser.article(Source.THEPAPER,article(detail,"12"),"https://www.thepaper.cn/newsDetail_forward_12");assertFalse(doc.hasContent());assertTrue(doc.sourceUnavailable);}
        assertTrue(SourceParser.article(Source.THEPAPER,article(body(),"13"),"https://www.thepaper.cn/newsDetail_forward_12").sourceUnavailable);
    }
    @Test public void onlyOwnedArticlePathsCanOpenNativeReader(){
        assertEquals(Source.THEPAPER,ReaderLinks.readerSource("https://www.thepaper.cn/newsDetail_forward_12"));assertNull(ReaderLinks.readerSource("https://www.thepaper.cn/"));assertNull(ReaderLinks.readerSource("https://image.thepaper.cn/newsDetail_forward_12"));assertEquals("",ThepaperSource.articleId("https://www.thepaper.cn.evil.test/newsDetail_forward_12"));assertEquals("",ThepaperSource.articleId("https://www.thepaper.cn/newsDetail_forward_12/other"));
    }
}
