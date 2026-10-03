package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class AdditionalSourcesTest {
    @Test public void aggregateAddsOneEntryAndKeepsSixTabs(){
        assertEquals(12,Source.aggregateSources().length);assertEquals(12,new HashSet<>(Arrays.asList(Source.aggregateSources())).size());
        assertEquals(6,Source.navigationOrder().length);assertEquals(Source.AGGREGATE,Source.navigationOrder()[0]);
        assertEquals(Source.ZHIHU,Source.navigationOrder()[1]);assertFalse(Source.AGGREGATE.readable());
        for(Source source:Source.aggregateSources())assertTrue(source.readable());assertFalse(Source.TIEBA.readable());
    }
    @Test public void ithomeOnlyUsesDayListAndOwnedArticleLinks()throws Exception{
        List<Item> rows=SourceParser.list(Source.ITHOME,"<ul id=d-1><li><a href='https://www.ithome.com/1/009/232.htm'>日榜</a><a href='https://evil.test/1/009/233.htm'>外部</a></li></ul><ul id=d-2><li><a href='https://www.ithome.com/1/009/234.htm'>周榜</a></li></ul>");
        assertEquals(1,rows.size());assertEquals("日榜",rows.get(0).title);
    }
    @Test public void doubanOmitsAdsAndUsesWebTopicRatherThanAppUri()throws Exception{
        List<Item> rows=SourceParser.list(Source.DOUBAN,"{\"items\":[{\"id\":12,\"title\":\"话题\",\"uri\":\"douban://x\"},{\"id\":13,\"title\":\"广告\",\"is_ad\":true},{\"id\":14,\"title\":\"非公开\",\"is_public\":false}]}");
        assertEquals(1,rows.size());assertEquals("https://www.douban.com/gallery/topic/12/",rows.get(0).url);
    }
    @Test public void juejinKeepsApiOrderAndRejectsInvalidIds()throws Exception{
        List<Item> rows=SourceParser.list(Source.JUEJIN,"{\"err_no\":0,\"data\":[{\"content\":{\"content_id\":123,\"title\":\"第一\"}},{\"content\":{\"content_id\":\"../456\",\"title\":\"无效\"}},{\"content\":{\"content_id\":124,\"title\":\"第二\"}}]}");
        assertEquals(2,rows.size());assertEquals("第一",rows.get(0).title);assertEquals("https://juejin.cn/post/124",rows.get(1).url);
    }
    @Test public void sspaiIsSelectionsAndExcludesAdvertising()throws Exception{
        List<Item> rows=SourceParser.list(Source.SSPAI,"{\"error\":0,\"data\":[{\"id\":1,\"title\":\"选文\"},{\"id\":2,\"title\":\"投放\",\"advertisement_url\":\"https://example.com/\"}]}");
        assertEquals(1,rows.size());assertEquals("热门选文",rows.get(0).detail);
        assertTrue(Source.GUOKR.category.contains("非热榜"));assertTrue(Source.IFANR.category.contains("非热榜"));
    }
    @Test(expected=IllegalStateException.class) public void failureIsNotAnEmptySuccessfulList()throws Exception{
        SourceParser.list(Source.JUEJIN,"{\"err_no\":403,\"data\":[]}");
    }
    @Test public void allNewReadersExcludeRecommendationsAndPreserveLinks(){
        Map<Source,String[]> samples=new EnumMap<>(Source.class);
        samples.put(Source.GUOKR,new String[]{"https://www.guokr.com/article/1","<div class='styled__ArticleContent-abc'>%s</div>"});
        samples.put(Source.ITHOME,new String[]{"https://www.ithome.com/1/009/232.htm","<div id=paragraph class=post_content>%s</div>"});
        samples.put(Source.IFANR,new String[]{"https://www.ifanr.com/123","<article class=c-article-content>%s</article>"});
        samples.put(Source.JUEJIN,new String[]{"https://juejin.cn/post/123","<div id=article-root><div class=article-viewer>%s</div></div>"});
        samples.put(Source.SSPAI,new String[]{"https://sspai.com/post/123","<div class=article-body><div class=wangEditor-txt>%s</div></div>"});
        for(Map.Entry<Source,String[]> sample:samples.entrySet()){
            String[] values=sample.getValue();String raw="<h1>标题</h1>"+String.format(values[1],"<p>文章正文 <a href='"+values[0]+"'>相关原文</a></p>")+"<aside>推荐污染</aside><button>下载App</button>";
            Document doc=SourceParser.article(sample.getKey(),raw,values[0]);String html=ReaderHtml.render(doc,sample.getKey(),19);
            assertTrue(sample.getKey().name(),doc.hasContent());assertTrue(html.contains("文章正文"));assertTrue(html.contains(values[0]));assertFalse(html.contains("推荐污染"));assertFalse(html.contains("下载App"));
            assertEquals(sample.getKey(),ReaderLinks.readerSource(values[0]));assertFalse(UrlPolicy.belongs(sample.getKey(),values[0].replace(".com/",".com.evil.test/").replace(".cn/",".cn.evil.test/")));
        }
    }
    @Test public void doubanTopicListsOnlySafeNonVideoPublicDestinations()throws Exception{
        Item topic=new Item(Source.DOUBAN,"话题","https://www.douban.com/gallery/topic/12/","");
        Document doc=AdditionalSources.doubanTopic(topic,"{\"items\":[{\"target\":{\"title\":\"原帖\",\"url\":\"https://www.douban.com/topic/1/\"}},{\"target\":{\"title\":\"视频\",\"url\":\"https://www.douban.com/topic/2/\",\"video_info\":{}}},{\"target\":{\"title\":\"外部\",\"url\":\"https://evil.test/topic/3/\"}}]}");
        assertEquals(1,doc.related.size());assertEquals(1,doc.filteredVideos);assertTrue(doc.notice.contains("不是全部"));assertEquals("12",AdditionalSources.doubanTopicId(topic.url));
        assertEquals("",AdditionalSources.doubanTopicId("https://evil.test/gallery/topic/12/"));
    }
}
