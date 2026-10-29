package app.quietreader;

import java.util.*;
import org.jsoup.Jsoup;
import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public final class TiebaTopicTest {
    private static final String TOPIC="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=28368032";
    private List<Item> posts(String rows){return TiebaTopic.posts(Jsoup.parse("<div class=topic-wrapper><span class=recom-sort-selected>最热</span><ul class=thread-list>"+rows+"</ul></div>",TOPIC),TOPIC);}
    private String row(String link,String title){return "<li><a class='title track-thread-title' href='"+link+"'>"+title+"</a><div class=content>真实列表摘要</div></li>";}
    @Test public void onlyTheExactTopicRouteAndAnUnambiguousIdAreAccepted(){
        assertTrue(TiebaTopic.isTopic(TOPIC));
        for(String url:List.of(TOPIC+"&topic_id=2",TOPIC.replace("28368032","0"),TOPIC.replace("28368032","%31"),TOPIC.replace("tieba.baidu.com","evil-tieba.baidu.com"),"https://tieba.baidu.com/p/123","https://tieba.baidu.com/hottopic/browse/hottopic"))assertFalse(url,TiebaTopic.isTopic(url));
    }
    @Test public void sourceOrderAndActualTitlesAreKeptWithoutSemanticRewriting(){
        // Title/link pairs observed in the saved official 2026-10-26 topic snapshot.
        List<Item> rows=posts(row("/p/11134197308","答疑贴（勿刷屏）")+row("/p/11133815523","留学东京时，遇见一位北海道女孩的故事")+row("/p/11130891790","留学日本时，一个下海学姐的故事"));
        assertEquals(3,rows.size());assertEquals("https://tieba.baidu.com/p/11134197308",rows.get(0).url);
        assertEquals("答疑贴（勿刷屏）",rows.get(0).title);assertEquals("最热",rows.get(0).detail);
        assertEquals("真实列表摘要",rows.get(0).snippet);assertEquals("https://tieba.baidu.com/p/11133815523",rows.get(1).url);
    }
    @Test public void capturedOfficialTitleLinksKeepAllTenChoicesAndTheSelectedSort()throws Exception{
        try(java.io.InputStream stream=getClass().getResourceAsStream("/tieba-topic-official-links-20261026.html")){
            assertNotNull(stream);
            List<Item> rows=TiebaTopic.posts(Jsoup.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),TOPIC),TOPIC);
            assertEquals(10,rows.size());assertEquals("最热",rows.get(0).detail);
            assertEquals("https://tieba.baidu.com/p/11134197308",rows.get(0).url);
            assertEquals("https://tieba.baidu.com/p/11133815523",rows.get(1).url);
            assertEquals("https://tieba.baidu.com/p/11130891790",rows.get(2).url);
            assertEquals("答疑贴（勿刷屏）",rows.get(0).title);
        }
    }
    @Test public void twoMorePublicTopicsUseTheirOwnOrderedChoices()throws Exception{
        long[][] cases={{28368027,11137152004L},{28368028,11137219143L}};
        for(long[] sample:cases){
            String url=TOPIC.replace("28368032",Long.toString(sample[0]));
            try(java.io.InputStream stream=getClass().getResourceAsStream("/tieba-topic-official-links-"+sample[0]+".html")){
                assertNotNull(stream);
                List<Item> rows=TiebaTopic.posts(Jsoup.parse(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),url),url);
                assertEquals(10,rows.size());assertEquals("最热",rows.get(0).detail);
                assertEquals("https://tieba.baidu.com/p/"+sample[1],rows.get(0).url);
            }
        }
    }
    @Test public void linksMustBeActualFirstPagePostTitles(){
        String invalid=row("https://example.com/p/1","外站")+row("/foo/p/2","错误路径")+row("/p/%33","编码路径")+row("/p/4?pn=2","后续页")+row("javascript:alert(1)","脚本")+"<li><a href='/p/5'>没有标题身份的链接</a></li>";
        List<Item> rows=posts(invalid+row("//tieba.baidu.com/p/6?pn=1&from=topic#reply","有效标题"));
        assertEquals(1,rows.size());assertEquals("https://tieba.baidu.com/p/6",rows.get(0).url);
    }
    @Test public void adsAndRecommendationsCannotBecomeTheDefaultPost(){
        String html="<a class=title href='/p/1'>全页导航</a><div class=topic-wrapper><aside><ul class=thread-list>"+row("/p/2","侧栏")+"</ul></aside><ul class=thread-list><li class=ad><a class=title href='/p/3'>广告</a></li><li data-tuiguang=1><a class=title href='/p/4'>推广</a></li>"+row("/p/5","有效正文入口")+"</ul></div>";
        List<Item> rows=TiebaTopic.posts(Jsoup.parse(html,TOPIC),TOPIC);
        assertEquals(1,rows.size());assertEquals("https://tieba.baidu.com/p/5",rows.get(0).url);
    }
    @Test public void duplicateTrackingVariantsDoNotOccupyMultipleChoices(){
        List<Item> rows=posts(row("/p/123?from=topic","第一份")+row("/p/123/#x","重复")+row("/p/456","下一帖"));
        assertEquals(2,rows.size());assertEquals("第一份",rows.get(0).title);
        try{rows.clear();fail("selection must be immutable");}catch(UnsupportedOperationException expected){}
    }
    @Test public void emptyAndVideoRowsRemainExplicitRatherThanInventingContent(){
        assertTrue(posts("").isEmpty());
        List<Item> rows=posts("<li><a class=title href='/p/10'>视频帖</a><video src='/clip.mp4'></video></li>"+row("/p/11","图文帖"));
        assertTrue(rows.get(0).video);assertFalse(rows.get(1).video);
        assertTrue(TiebaTopic.posts(Jsoup.parse("<ul class=thread-list>"+row("/p/1","登录页误链接")+"</ul>"),TOPIC).isEmpty());
    }
    @Test public void boundedSelectionAlsoFeedsTheExistingParser(){
        StringBuilder html=new StringBuilder();for(int n=1;n<=60;n++)html.append(row("/p/"+n,"帖子"+n));
        List<Item> rows=posts(html.toString());assertEquals(40,rows.size());
        Document doc=SourceParser.article(Source.TIEBA,"<div class=topic-wrapper><ul class=thread-list>"+html+"</ul></div>",TOPIC);
        assertEquals(40,doc.related.size());assertEquals(40,doc.sections.size());
        assertEquals(rows.get(0).url,doc.related.get(0).url);
        assertFalse(doc.sections.stream().anyMatch(ReaderHtml::discussion));
    }
}
