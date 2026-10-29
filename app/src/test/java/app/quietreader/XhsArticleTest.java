package app.quietreader;

import org.json.*;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class XhsArticleTest {
    private static final String ID="6ab1c23d000000000d00b91d",URL="https://www.xiaohongshu.com/explore/"+ID+"?xsec_token=fixture%2Fpublic";
    private JSONObject fixture(boolean video)throws Exception{
        try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/xhs-"+(video?"note":"image-note")+"-data-20261024.json")){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
    }
    private String html(JSONObject data){return "<script>window.__INITIAL_STATE__="+data+";evil()</script><article>推荐与广告不能当正文</article>";}
    private JSONObject note(JSONObject state)throws Exception{return state.getJSONObject("note").getJSONObject("noteDetailMap").getJSONObject(ID).getJSONObject("note");}
    private Document parse(JSONObject state){return SourceParser.article(Source.XHS,html(state),URL);}
    @Test public void realImageNoteKeepsOwnedTextImagesAndDimensions()throws Exception{
        Document d=parse(fixture(false));assertEquals("馄饨也有新翻译了！",d.title);assertEquals("白斤慢",d.byline);assertTrue(d.hasContent());assertFalse(d.unsupportedVideo);
        assertTrue(d.blocks.stream().anyMatch(b->b.type.equals("image")&&b.imageWidth>0&&b.imageHeight>0&&b.value.startsWith("https://")));
        assertTrue(d.blocks.stream().filter(b->b.type.equals("text")).allMatch(b->!b.value.contains("[话题]")));
        java.util.Set<String> expanded=new java.util.HashSet<>();ReaderHtml.expandPrimary(d,expanded);String html=ReaderHtml.render(d,Source.XHS,19,false,expanded,false);
        assertTrue(html.contains("data-reader-layout='article'"));assertTrue(html.contains("<img"));assertFalse(html.contains("推荐与广告"));assertFalse(html.contains("<script"));
    }
    @Test public void realVideoNoteUsesPosterAndRetainsDescription()throws Exception{
        JSONObject state=fixture(true);String id=state.getJSONObject("note").getJSONObject("noteDetailMap").keys().next();
        Document d=SourceParser.article(Source.XHS,html(state),"https://www.xiaohongshu.com/explore/"+id);
        assertTrue(d.unsupportedVideo);assertTrue(d.hasContent());assertFalse(d.videoPoster.isEmpty());assertTrue(d.blocks.stream().noneMatch(b->b.type.equals("image")));
        String html=ReaderHtml.render(d,Source.XHS,19,false,java.util.Set.of(d.sections.get(0).id),false);assertTrue(html.contains("data-reader-layout='video'"));assertFalse(html.contains("<video"));assertFalse(html.contains("<iframe"));
    }
    @Test public void ownershipAndNavigationMustRemainOnSameNote()throws Exception{
        JSONObject state=fixture(false);note(state).put("noteId","6ab1c23d000000000d00b91e");assertFalse(parse(state).canPresent());
        state=fixture(false);state.getJSONObject("note").put("currentNoteId","6ab1c23d000000000d00b91e");assertFalse(parse(state).canPresent());
        assertTrue(XhsArticle.sameNote(URL,"https://www.xiaohongshu.com/discovery/item/"+ID+"/?xsec_token=changed"));
        for(String next:new String[]{"https://www.xiaohongshu.com/explore/6ab1c23d000000000d00b91e","https://www.xiaohongshu.com/explore","https://unrelated.example/explore/"+ID})assertFalse(XhsArticle.sameNote(URL,next));
    }
    @Test public void restrictedMissingOrUnknownNotesNeverBecomeWholePageArticles()throws Exception{
        for(String gate:new String[]{"isPrivate","isDeleted","isBlocked","needLogin"}){JSONObject state=fixture(false);note(state).put(gate,true);Document d=parse(state);assertFalse(d.hasContent());assertTrue(d.loginRequired||d.sourceUnavailable);}
        JSONObject state=fixture(false);note(state).put("type","unknown");assertFalse(parse(state).canPresent());
        assertFalse(SourceParser.article(Source.XHS,"<article>请登录推荐文字</article>",URL).canPresent());
        assertFalse(SourceParser.article(Source.XHS,html(fixture(false)),"https://www.xiaohongshu.com/explore/6ab1c23d000000000d00b91e").canPresent());
    }
    @Test public void commentsKeepAuthorsAndDeduplicateIdsNotText()throws Exception{
        JSONObject state=fixture(false),container=state.getJSONObject("note").getJSONObject("noteDetailMap").getJSONObject(ID);
        JSONObject one=new JSONObject("{id:'one',content:'相同评论',userInfo:{nickname:'甲'}}"),two=new JSONObject("{id:'two',content:'相同评论',userInfo:{nickname:'乙'}}");
        container.getJSONObject("comments").put("list",new JSONArray().put(one).put(two).put(one));Document d=parse(state);
        assertEquals(3,d.sections.size());assertEquals("评论 · 甲",d.sections.get(1).label);assertEquals("评论 · 乙",d.sections.get(2).label);
    }
    @Test public void imageMetadataDoesNotPermitUntrustedUrlsOrDuplicatePhotos()throws Exception{
        JSONObject state=fixture(false);JSONObject n=note(state),image=n.getJSONArray("imageList").getJSONObject(0);n.put("imageList",new JSONArray().put(image).put(image));
        assertEquals(1,parse(state).blocks.stream().filter(b->b.type.equals("image")).count());
        n.put("imageList",new JSONArray().put(new JSONObject("{urlDefault:'javascript:evil()',url:'https://unrelated.example/image.png'}")));
        assertEquals(0,parse(state).blocks.stream().filter(b->b.type.equals("image")).count());
    }
    @Test public void undefinedSourceLiteralsAreNotExecutedAndContentIsEscaped()throws Exception{
        JSONObject state=fixture(false);note(state).put("desc","<img src=x onerror=evil()>");
        String raw="<script>window.__INITIAL_STATE__={global:{missing:undefined},note:"+state.getJSONObject("note")+"};evil()</script>";
        Document d=SourceParser.article(Source.XHS,raw,URL);assertTrue(d.hasContent());String html=ReaderHtml.render(d,Source.XHS,19,false,java.util.Set.of(d.sections.get(0).id),false);assertTrue(html.contains("&lt;img"));assertFalse(html.contains("<script"));
    }
    @Test public void positiveVideoMetadataIsRetainedBySourceListForRecommendationFilter()throws Exception{
        JSONObject state=new JSONObject("{feed:{feeds:[{id:'"+ID+"',noteCard:{type:'video'}}]}}");
        String raw=html(state)+"<section class=note-item><a href='/explore/"+ID+"?xsec_token=fixture'><span class=title>来源视频</span></a></section>";
        java.util.List<Item> list=SourceParser.list(Source.XHS,raw);assertEquals(1,list.size());assertTrue(list.get(0).video);
    }
}
