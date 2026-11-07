package app.quietreader;

import java.util.*;
import static app.quietreader.Models.*;

/** One topic snapshot. Child bodies are flat, account-bound values, never nested bundles. */
final class TopicBundle {
    private TopicBundle(){}
    static boolean complete(Document doc){
        if(doc==null||!doc.hasContent()||doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo)return false;
        if(doc.topic==null)return !TiebaTopic.isTopic(doc.url);
        for(Item choice:doc.topic.choices)if(!choice.video){Document body=find(doc,choice.url);if(body==null||!body.hasContent()||body.loginRequired||body.sourceUnavailable||body.filteredVideo)return false;}
        return true;
    }
    static final int PAGE=3;
    static boolean initialReady(Document doc){return prepared(doc,PAGE);}
    static boolean prepared(Document doc,int limit){
        if(doc==null||!doc.hasContent()||doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo)return false;
        if(doc.topic==null)return !TiebaTopic.isTopic(doc.url);
        int n=0;for(Item choice:doc.topic.choices)if(!choice.video){
            if(n++>=limit)break;Document body=find(doc,choice.url);
            if(body==null||!body.hasContent()||body.loginRequired||body.sourceUnavailable||body.filteredVideo)return false;
        }
        return n>0;
    }
    static int total(Document doc){int n=0;if(doc!=null&&doc.topic!=null)for(Item choice:doc.topic.choices)if(!choice.video)n++;return n;}
    static Document find(Document root,String post){return root.topic==null?null:root.sourceUrl().equals(post)?root:root.topicBodies.get(post);}
    static Document leaf(Document doc){return ArticleDiskCache.snapshot(doc,false);}
    static boolean merge(Document into,Document from){
        if(into.topic==null||from.topic==null||!into.url.equals(from.url))return false;
        boolean changed=put(into,from);for(Document body:from.topicBodies.values())changed|=put(into,body);return changed;
    }
    private static boolean put(Document into,Document body){
        if(body.topic==null||body.sourceUrl().equals(into.sourceUrl())||!body.hasContent()||body.loginRequired||body.sourceUnavailable||body.filteredVideo||!body.url.equals(into.url))return false;
        if(into.topic.choices.stream().noneMatch(post->!post.video&&post.url.equals(body.sourceUrl())))return false;
        if(into.cachedNamespace!=null&&body.cachedNamespace!=null&&!into.cachedNamespace.equals(body.cachedNamespace)||into.cachedSession!=null&&body.cachedSession!=null&&!into.cachedSession.equals(body.cachedSession))return false;
        Document previous=into.topicBodies.get(body.sourceUrl());
        if(previous!=null&&(previous.cachedAt>body.cachedAt||previous.cachedAt==body.cachedAt&&DocumentSize.estimate(previous)>=DocumentSize.estimate(body)))return false;
        into.topicBodies.put(body.sourceUrl(),leaf(body));return true;
    }
    static Document focus(Document bundle,String post){Document body=find(bundle,post);if(body==null)return null;if(body==bundle)return bundle;Document focused=leaf(body);merge(focused,bundle);return focused;}
    static String imageReferer(Document bundle,String url){for(Document body:bundle.topicBodies.values())if(body.containsImage(url)&&ArticleSession.contentCurrent(body))return body.sourceUrl();return bundle.sourceUrl();}
}
