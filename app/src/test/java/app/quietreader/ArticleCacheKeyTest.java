package app.quietreader;

import org.junit.Test;
import java.util.*;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class ArticleCacheKeyTest {
    @Test public void independentPostsInTheSameTopicHaveDifferentKeys(){
        String url="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=999";
        Item root=new Item(Source.TIEBA,"话题",url,"");
        List<Item> posts=List.of(new Item(Source.TIEBA,"甲","https://tieba.baidu.com/p/991",""),new Item(Source.TIEBA,"乙","https://tieba.baidu.com/p/992",""));
        Document a=new Document();a.url=posts.get(0).url;TiebaTopic.attach(url,posts,a,"a".repeat(64),1);
        Document b=new Document();b.url=posts.get(1).url;TiebaTopic.attach(url,posts,b,"a".repeat(64),1);
        assertNotEquals(ArticleCacheKey.of(a),ArticleCacheKey.of(b));
        assertEquals(ArticleCacheKey.of(a),ArticleCacheKey.of(TiebaTopic.select(root,a.topic,posts.get(0))));
        assertEquals(url,ArticleCacheKey.root(ArticleCacheKey.of(b)));assertEquals(url,ArticleCacheKey.of(root));
    }
    @Test public void retentionUsesTheTopicFamilyAndLeavesOtherFilesIndependent(){
        String root="a".repeat(64)+".json",variant="a".repeat(64)+"-"+"b".repeat(64)+".json";
        assertEquals(root,ArticleCacheKey.family(variant));assertEquals(root,ArticleCacheKey.family(root));
        assertNotEquals(root,ArticleCacheKey.family("c".repeat(64)+".json"));
        ArticleDiskCache.Retention retained=new ArticleDiskCache.Retention("test",Set.of(root),Map.of(root,Set.of("version")));
        assertTrue(retained.accepts(variant,"version"));assertFalse(retained.accepts(variant,"old"));
        assertFalse(retained.contains("c".repeat(64)+"-"+"b".repeat(64)+".json"));
    }
}
