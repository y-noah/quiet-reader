package app.quietreader;

import static app.quietreader.Models.*;

/** A topic owns many independently acquired post bodies. */
final class ArticleCacheKey {
    private ArticleCacheKey(){}
    static String of(Item item){return of(item.url,item.selectedTopicPost);}
    static String of(Document doc){return of(doc.url,doc.topic==null?"":doc.topic.postUrl);}
    private static String of(String url,String post){return post.isEmpty()?url:url+'\n'+post;}
    static String root(String key){int at=key.indexOf('\n');return at<0?key:key.substring(0,at);}
    static String family(String file){return file.matches("[a-f0-9]{64}-[a-f0-9]{64}\\.json")?file.substring(0,64)+".json":file;}
}
