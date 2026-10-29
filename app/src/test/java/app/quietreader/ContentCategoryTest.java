package app.quietreader;

import app.quietreader.discovery.*;
import org.junit.Test;
import java.util.*;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class ContentCategoryTest {
    @Test public void capturedThepaperNodesPopulateCategoriesWithoutUsingTitles()throws Exception{
        String raw;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/thepaper-hot-nodes-20261025.json")){assertNotNull(in);raw=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
        List<Item> items=SourceParser.list(Source.THEPAPER,raw);assertEquals(20,items.size());
        Map<ContentCategory,Integer> counts=new EnumMap<>(ContentCategory.class);int text=0;
        for(Item item:items){if(item.video)continue;text++;assertFalse(item.sourceTag.isEmpty());
            HotCandidate candidate=new HotCandidate(item.source,item.url,item.title,item.snippet,item.detail,item.video,text,item.publishedAt,1000,ContentLanguage.Body.UNKNOWN,item.sourceTag);
            counts.merge(ContentCategory.of(HotCandidate.fromJson(candidate.json())),1,Integer::sum);
        }
        assertEquals(17,text);assertEquals(Integer.valueOf(12),counts.get(ContentCategory.SOCIETY));
        assertEquals(Integer.valueOf(1),counts.get(ContentCategory.TECH));assertEquals(Integer.valueOf(1),counts.get(ContentCategory.CULTURE));assertEquals(Integer.valueOf(3),counts.get(ContentCategory.OTHER));
    }
    @Test public void thepaperCategoryMappingRequiresExactSourceAndUnambiguousNode(){
        assertEquals(ContentCategory.TECH,ContentCategory.of(Source.THEPAPER,"对齐Lab"));
        assertEquals(ContentCategory.CULTURE,ContentCategory.of(Source.THEPAPER,"有戏"));
        for(String tag:List.of("教育家","澎湃防务","浦江头条","一号专案","大国外交","中国政库","自贸区连线","全球速报"))assertEquals(ContentCategory.SOCIETY,ContentCategory.of(Source.THEPAPER,tag));
        for(String tag:List.of("澎湃评论","运动家","未知栏目","对齐Lab推荐","node:programmer",""))assertEquals(ContentCategory.OTHER,ContentCategory.of(Source.THEPAPER,tag));
        assertEquals(ContentCategory.OTHER,ContentCategory.of(Source.THEPAPER,null));
        assertEquals(ContentCategory.OTHER,ContentCategory.of(Source.ZHIHU,"对齐Lab"));
        assertEquals(ContentCategory.OTHER,ContentCategory.of(Source.V2EX,"有戏"));
    }
    private String row(int id,String node){return "<div class='cell item'><span class='item_title'><a href='/t/"+id+"'>中文科技话题</a></span>"+node+"</div>";}
    @Test public void onlyExplicitTrustedNodeIsPreserved()throws Exception{
        List<Item> items=SourceParser.list(Source.V2EX,"<div id='Main'>"+row(1,"<a class='node' href='/go/programmer'>程序员</a>")+row(2,"<a class='node' href='https://evil.test/go/programmer'>程序员</a>")+row(3,"")+"</div>");
        assertEquals("node:programmer",items.get(0).sourceTag);assertEquals("",items.get(1).sourceTag);assertEquals("",items.get(2).sourceTag);
        assertEquals("node:programmer",Item.from(items.get(0).json()).sourceTag);
        assertEquals(ContentCategory.TECH,ContentCategory.of(Source.V2EX,items.get(0).sourceTag));
        assertEquals(ContentCategory.OTHER,ContentCategory.of(Source.V2EX,"node:qna"));
        assertEquals(ContentCategory.OTHER,ContentCategory.of(Source.ZHIHU,"node:programmer"));
    }
    @Test public void categoryEvidenceSurvivesCandidateRoundTripWithoutChangingModelText()throws Exception{
        HotCandidate tagged=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/123","中文话题","摘要","",false,1,null,1,ContentLanguage.Body.UNKNOWN,"node:books");
        HotCandidate plain=new HotCandidate(Source.V2EX,tagged.originalUrl,tagged.title,tagged.snippet,"",false,1,null,1);
        assertEquals(ContentCategory.CULTURE,ContentCategory.of(HotCandidate.fromJson(tagged.json())));
        assertEquals(tagged.textHash,plain.textHash);assertEquals(tagged.modelText(),plain.modelText());assertEquals("node:books",tagged.readerItem().sourceTag);
        assertEquals(ContentCategory.OTHER,ContentCategory.of(plain));
    }
    @Test public void sourceAndCategoryMustMatchTheSameOriginalEntry(){
        HotCandidate tech=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/1","中文话题","","",false,1,null,1,ContentLanguage.Body.UNKNOWN,"node:python");
        HotCandidate unknown=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/1","中文话题","","",false,1,null,1);
        List<FeedStore.Row> rows=Collections.singletonList(new FeedStore.Row("event",unknown,"原顺序",Arrays.asList(unknown,tech)));
        assertTrue(SourceFilter.apply(rows,"ZHIHU|TECH").isEmpty());
        assertSame(tech,SourceFilter.apply(rows,"all|TECH").get(0).candidate);
        assertSame(unknown,SourceFilter.apply(rows,"ZHIHU|OTHER").get(0).candidate);
        assertEquals("V2EX|CULTURE",SourceFilter.combine("V2EX",ContentCategory.CULTURE));
        assertEquals("V2EX",SourceFilter.normalize("V2EX|INVALID"));
    }
}
