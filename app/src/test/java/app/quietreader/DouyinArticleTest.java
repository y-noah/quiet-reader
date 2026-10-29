package app.quietreader;

import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Positive cases are synthetic contracts; captured real topic currently has no aweme. */
public class DouyinArticleTest {
    private static final String URL="https://www.douyin.com/hot/123";
    private JSONObject owner()throws Exception{return new JSONObject("{hotId:'123',awemeId:'456',aweme:{statusCode:0,detail:{awemeId:'456',desc:'合成视频说明',authorInfo:{nickname:'作者'},status:{privateStatus:0},video:{cover:'https://p3.douyinpic.com/cover.jpg'}}},comment:{statusCode:0,comments:[{cid:'10',text:'第一条讨论',user:{nickname:'甲'}},{cid:'11',text:'第二条讨论',user:{nickname:'乙'}}],hasMore:true}}");}
    private String html(JSONObject data,boolean split){
        String stream="7:"+new JSONArray().put("$").put("fixture").put(JSONObject.NULL).put(data)+"\n";
        int at=split?stream.indexOf("视频"):stream.length();
        if(at<0)at=stream.length()/2;
        String first=new JSONArray().put(1).put(stream.substring(0,at)).toString(),second=new JSONArray().put(1).put(stream.substring(at)).toString();
        return "<script>self.__pace_f.push("+first+")</script><aside>推荐和登录界面不能变成正文</aside><script>self.__pace_f.push("+second+")</script>";
    }
    private Document parse(JSONObject data)throws Exception{return SourceParser.article(Source.DOUYIN,html(data,true),URL);}
    @Test public void flightChunksAreJoinedAndCommentsKeepTheirOwnAuthors()throws Exception{
        Document d=parse(owner());assertTrue(d.unsupportedVideo);assertEquals("作者",d.byline);assertEquals(3,d.sections.size());
        assertEquals("评论 · 甲",d.sections.get(1).label);assertEquals("评论 · 乙",d.sections.get(2).label);
        java.util.Set<String> expanded=new java.util.HashSet<>();ReaderHtml.expandPrimary(d,expanded);
        String html=ReaderHtml.render(d,Source.DOUYIN,19,false,expanded,false);
        assertTrue(html.contains("data-reader-layout='video'"));assertTrue(html.contains("合成视频说明"));assertTrue(html.contains("discussion-heading'>讨论"));
        assertFalse(html.contains("推荐和登录界面"));assertFalse(html.contains("<video"));assertFalse(html.contains("<script"));
    }
    @Test public void matchingPageIdentityIsRequiredForBothTopicsAndVideos()throws Exception{
        JSONObject data=owner();data.put("hotId","999");assertFalse(parse(data).canPresent());
        data=owner();data.put("awemeId","999");assertFalse(parse(data).canPresent());
        assertTrue(SourceParser.article(Source.DOUYIN,html(owner(),false),"https://www.douyin.com/video/456").hasContent());
        assertFalse(SourceParser.article(Source.DOUYIN,html(owner(),false),"https://www.douyin.com/video/999").canPresent());
        assertFalse(SourceParser.article(Source.DOUYIN,html(owner(),false),"https://unrelated.example/hot/123").canPresent());
    }
    @Test public void knownPermissionsNeverLeakDescriptionOrComments()throws Exception{
        for(String field:new String[]{"pcNeedLogin","isPrivate","isFriendLimit","chargeInfo","entertainmentPreviewConfig","entertainmentVideoPaidWay"}){
            JSONObject data=owner();data.getJSONObject("aweme").getJSONObject("detail").put(field,true);Document d=parse(data);
            assertFalse(field,d.hasContent());assertFalse(d.unsupportedVideo);assertEquals("",d.videoPoster);
            assertTrue(d.loginRequired||d.sourceUnavailable);
        }
        for(String field:new String[]{"isDelete","isProhibited","isReviewing","partSee"}){
            JSONObject data=owner();data.getJSONObject("aweme").getJSONObject("detail").getJSONObject("status").put(field,true);
            assertTrue(parse(data).sourceUnavailable);assertFalse(parse(data).hasContent());
        }
    }
    @Test public void actualEmptyTopicDoesNotTurnIntoFabricatedContent()throws Exception{
        String raw;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/douyin-empty-topic-20261024.json")){raw=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        Document d=SourceParser.article(Source.DOUYIN,html(new JSONObject(raw),true),"https://www.douyin.com/hot/2689461");
        assertFalse(d.canPresent());assertTrue(d.notice.contains("尚未返回"));
        assertFalse(SourceParser.article(Source.DOUYIN,"<article>这是推荐</article>",URL).canPresent());
    }
    @Test public void pendingRecordDoesNotHideLaterAvailableRecord()throws Exception{
        JSONObject empty=new JSONObject().put("hotId","123").put("aweme",JSONObject.NULL);
        assertTrue(SourceParser.article(Source.DOUYIN,html(empty,false)+html(owner(),true),URL).hasContent());
    }
    @Test public void commentsDeduplicateByIdAndTextIsEscaped()throws Exception{
        JSONObject data=owner();JSONArray comments=data.getJSONObject("comment").getJSONArray("comments");
        comments.put(comments.getJSONObject(0));comments.getJSONObject(1).put("text",comments.getJSONObject(0).getString("text"));
        data.getJSONObject("aweme").getJSONObject("detail").put("desc","<script>alert(1)</script>");
        Document d=parse(data);assertEquals(3,d.sections.size());
        String html=ReaderHtml.render(d,Source.DOUYIN,19,false,java.util.Set.of(d.sections.get(0).id),false);
        assertFalse(html.contains("<script>"));assertTrue(html.contains("&lt;script&gt;"));
    }
    @Test public void canonicalNavigationMustKeepTheSameTopicOrVideo(){
        assertTrue(DouyinArticle.sameContent(URL,URL+"/?foo=bar"));
        assertTrue(DouyinArticle.sameContent(URL,"https://www.douyin.com/video/456?sentenceId=123"));
        for(String destination:new String[]{"https://www.douyin.com/video/456","https://www.douyin.com/video/456?sentenceId=999","https://www.douyin.com/video/456?sentenceId=123&sentenceId=999","https://www.douyin.com/hot/999","https://www.douyin.com/"})assertFalse(destination,DouyinArticle.sameContent(URL,destination));
        assertTrue(DouyinArticle.sameContent("https://www.douyin.com/video/456","https://www.douyin.com/video/456/?modeFrom=hotDetail"));
        assertFalse(DouyinArticle.sameContent("https://www.douyin.com/video/456","https://www.douyin.com/video/999"));
    }
    @Test public void observedBoardTitlePathsReachTheOwnedTopicParser()throws Exception{
        String link=URL+"/%E5%90%88%E6%88%90%E8%AF%9D%E9%A2%98";
        java.util.List<Item> items=SourceParser.list(Source.DOUYIN,"<div><a href='"+link+"'><h3>合成话题</h3></a>100万 热度</div>");
        assertEquals(1,items.size());assertEquals(link,items.get(0).url);
        Document doc=SourceParser.article(Source.DOUYIN,html(owner(),true),items.get(0).url);
        assertTrue(doc.hasContent());assertEquals(link,doc.url);assertEquals("作者",doc.byline);
        assertTrue(DouyinArticle.sameContent(link,URL));assertTrue(DouyinArticle.sameContent(URL,link+"/?from=hot"));
        assertTrue(DouyinArticle.sameContent(link,"https://www.douyin.com/video/456?sentenceId=123"));
        assertFalse(DouyinArticle.sameContent(link,"https://www.douyin.com/video/456?sentenceId=999"));
        JSONObject other=owner().put("hotId","999");assertFalse(SourceParser.article(Source.DOUYIN,html(other,false),link).canPresent());
    }
    @Test public void titleAliasesDoNotAllowUnrelatedOrNestedRoutes()throws Exception{
        for(String url:new String[]{URL+"/title/extra",URL+"//title","https://www.douyin.com/hot/not-an-id/title","https://unrelated.example/hot/123/title","https://www.douyin.com/video/456/title"}){
            assertFalse(url,DouyinArticle.sameContent(URL,url));
            assertFalse(url,SourceParser.article(Source.DOUYIN,html(owner(),true),url).canPresent());
        }
        assertFalse(DouyinArticle.sameContent(URL+"/title", "https://www.douyin.com/hot/999/title"));
    }
    @Test public void unavailableAndUntrustedCoverRemainHonest()throws Exception{
        JSONObject data=owner();data.getJSONObject("aweme").put("statusCode",1);assertTrue(parse(data).sourceUnavailable);assertFalse(parse(data).hasContent());
        data.getJSONObject("aweme").put("detail",JSONObject.NULL);assertTrue(parse(data).sourceUnavailable);
        data=owner();data.getJSONObject("aweme").getJSONObject("detail").getJSONObject("video").put("cover","https://unrelated.example/cover.jpg");assertEquals("",parse(data).videoPoster);
    }
}
