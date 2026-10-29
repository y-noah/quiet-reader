package app.quietreader;

import android.content.Context;
import static app.quietreader.Models.*;

/** Identity captured before loading, never inferred from a later displayed page. */
final class ArticleSession {
    static boolean contentCurrent(Document doc){return doc.topic==null?!TiebaTopic.isTopic(doc.url):doc.url.equals(doc.topic.topicUrl)&&doc.topic.postSession.equals(ArticleDiskCache.session(doc.topic.postUrl));}
    private final ArticleDiskCache disk;
    private final String url, session, namespace,metadataHash;
    private final long epoch;
    ArticleSession(Context context,String url){
        this(context,url,"");
    }
    ArticleSession(Context context,Item item){this(context,item.url,ArticleVersion.hash(item));}
    private ArticleSession(Context context,String url,String metadataHash){
        this.metadataHash=metadataHash;
        disk=new ArticleDiskCache(context);this.url=url;
        session=ArticleDiskCache.session(url);namespace=disk.namespace();epoch=Repository.articleCacheEpoch();
    }
    boolean stamp(Document document){
        synchronized(Repository.class){
            if(!url.equals(document.url)||!contentCurrent(document)||epoch!=Repository.articleCacheEpoch()||!namespace.equals(disk.namespace())||!session.equals(ArticleDiskCache.session(url)))return false;
            document.cachedAt=System.currentTimeMillis();document.cachedSession=session;document.cachedNamespace=namespace;
            document.metadataHash=metadataHash;
            return true;
        }
    }
    boolean cookieChanged(){
        synchronized(Repository.class){return epoch==Repository.articleCacheEpoch()&&namespace.equals(disk.namespace())&&!session.equals(ArticleDiskCache.session(url));}
    }
    boolean current(){
        synchronized(Repository.class){return epoch==Repository.articleCacheEpoch()&&namespace.equals(disk.namespace())&&session.equals(ArticleDiskCache.session(url));}
    }
    /** Immutable namespace and cookie fingerprint captured by this same token. No raw cookie. */
    String scopeIdentity(){return namespace+"\n"+session;}
    String cookieFingerprint(String requestedUrl){if(!url.equals(requestedUrl))throw new IllegalArgumentException("Article session URL mismatch");return session;}
    boolean owns(Document document){return url.equals(document.url)&&namespace.equals(document.cachedNamespace)&&session.equals(document.cachedSession)&&current();}
}
