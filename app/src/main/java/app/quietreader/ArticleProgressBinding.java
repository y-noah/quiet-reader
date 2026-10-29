package app.quietreader;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import app.quietreader.discovery.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Captures the acquisition's scope, never a later account's scope. No raw cookies. */
final class ArticleProgressBinding {
    /** Cheap action boundary; does not read cookies for every visible candidate. */
    static final class CacheContext {
        private final ArticleDiskCache disk;private final String namespace;private final long epoch;
        CacheContext(Context context){disk=new ArticleDiskCache(context);synchronized(Repository.class){namespace=disk.namespace();epoch=Repository.articleCacheEpoch();}}
        boolean current(){synchronized(Repository.class){return epoch==Repository.articleCacheEpoch()&&namespace.equals(disk.namespace());}}
    }
    static final class Scope {
        final String key;private final ArticleSession token;
        private Scope(Context context,String url){token=new ArticleSession(context,url);key=HotCandidate.hash("body-progress-scope-v1\n"+token.scopeIdentity());}
        boolean current(){return token.current();}
        boolean owns(Document document){return token.owns(document);}
    }
    final HotCandidate candidate;final Scope scope;final ArticleProgressEvidence.Input input;
    private final ArticleSession selectedPost;
    private ArticleProgressBinding(HotCandidate candidate,Scope scope,ArticleProgressEvidence.Input input,ArticleSession selectedPost){this.candidate=candidate;this.scope=scope;this.input=input;this.selectedPost=selectedPost;}
    private boolean current(){return scope.current()&&(selectedPost==null||selectedPost.current());}
    static boolean supported(Source source){return DiscussionProgressEvidence.supported(source)||ArticleUpdateEvidence.supported(source);}
    static Scope scope(Context context,HotCandidate candidate){if(candidate.video||!supported(candidate.source))return null;Scope scope=new Scope(context.getApplicationContext(),candidate.originalUrl);return scope.current()?scope:null;}
    static ArticleProgressBinding capture(Context context,HotCandidate candidate,Document document){
        if(document==null||!DiscussionProgressEvidence.supported(candidate.source)&&!ArticleUpdateEvidence.VERSION.equals(document.sourceUpdateEvidence)||!ArticleVersion.matches(candidate.readerItem(),document)||!ArticleSession.contentCurrent(document))return null;
        Scope scope=scope(context,candidate);if(scope==null||!scope.owns(document))return null;
        ArticleSession selectedPost=document.topic==null?null:new ArticleSession(context,document.sourceUrl());
        if(!ArticleSession.contentCurrent(document))return null;
        ArticleProgressEvidence.Input input=ArticleProgressEvidence.freeze(candidate.source,document,scope.key,System.currentTimeMillis());
        return input!=null&&scope.current()&&(selectedPost==null||selectedPost.current())?new ArticleProgressBinding(candidate,scope,input,selectedPost):null;
    }
    void observe(FeedStore store,BooleanSupplier active){
        if(!active.getAsBoolean()||!current())return;long now=System.currentTimeMillis();ArticleProgressEvidence.Snapshot value=input.finish(now);if(value==null||!active.getAsBoolean()||!current())return;
        SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
            if(active.getAsBoolean()&&current()&&store.currentCandidate(candidate))new ArticleProgressHistory(db).observe(value,now);
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    void consumed(FeedStore store,long at){
        if(!current())return;long now=System.currentTimeMillis();ArticleProgressEvidence.Snapshot value=input.finish(now);
        if(value!=null&&value.acquiredAt<=at&&current())new ArticleProgressHistory(store.getWritableDatabase()).consumed(value,at,now);
    }
}
