package app.quietreader;

import app.quietreader.discovery.HotCandidate;
import static app.quietreader.Models.*;

/** Capture source metadata before acquisition; never relabel a reused body. */
final class ArticleVersion {
    static Document preferredCached(Document history,Document memory,java.util.function.Predicate<Document> usable){
        if(history!=null&&!usable.test(history))history=null;
        if(memory!=null&&!usable.test(memory))memory=null;
        // Equal acquisition times may represent history with more manually expanded pages.
        return history==null?memory:memory!=null&&memory.cachedAt>history.cachedAt?memory:history;
    }
    static String hash(Item item){return HotCandidate.metadataHash(item.title,item.snippet);}
    static boolean selected(Item item,Document doc){return item.selectedTopicPost.isEmpty()||doc.topic!=null&&item.selectedTopicPost.equals(doc.topic.postUrl);}
    static boolean sameRequest(Item a,Item b){return a!=null&&b!=null&&a.source==b.source&&a.url.equals(b.url)&&a.selectedTopicPost.equals(b.selectedTopicPost)&&hash(a).equals(hash(b));}
    static boolean matches(Item item,Document doc){return selected(item,doc)&&(!TiebaTopic.isTopic(item.url)||doc.topic!=null)&&item.url.equals(doc.url)&&!doc.metadataHash.isEmpty()&&hash(item).equals(doc.metadataHash);}
    static boolean readableCached(Item item,Document doc,long now,boolean offline){
        if(!selected(item,doc)||!item.url.equals(doc.url)||doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo)return false;
        if(matches(item,doc)||offline)return true;
        // Metadata edits do not require reopening a recently acquired body of the same URL.
        // Preserve its original metadataHash: learning/readiness still require exact evidence.
        long age=now-doc.cachedAt;return doc.hasContent()&&doc.cachedAt>0&&age>=0&&age<=10*60_000;
    }
    static void capture(Item item,Document doc){if(item.url.equals(doc.url))doc.metadataHash=hash(item);}
}
