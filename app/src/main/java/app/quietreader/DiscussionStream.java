package app.quietreader;

import android.app.Activity;
import android.content.Context;
import static app.quietreader.Models.*;

/** One explicit discussion request at a time, sharing source login without a JS bridge. */
final class DiscussionStream {
    interface Loader {void load(Item page,Repository.Result<Document> callback);void cancel();default void close(){cancel();}}
    private final Context context;
    private final Item item;
    private final Loader loader;
    private boolean busy,closed;
    private int generation;
    DiscussionStream(Activity activity,Item item){this(activity,item,new Loader(){
        private final Repository repo=new Repository(activity);
        private DynamicReader dynamic;
        public void load(Item page,Repository.Result<Document> callback){
            if(SourceSession.usesWeb(activity,page.source)){dynamic=new DynamicReader(activity,page,callback,true);return;}
            repo.articleForReading(page,new Repository.Result<>(){
                public void success(Document doc){if(SourceSession.nativeDocumentReady(page.source,doc))callback.success(doc);else dynamic=new DynamicReader(activity,page,callback,true);}
                public void failure(String reason){dynamic=new DynamicReader(activity,page,callback,true);}
            });
        }
        public void cancel(){repo.cancelPending();if(dynamic!=null){dynamic.close();dynamic=null;}}
        public void close(){cancel();repo.close();}
    });}
    DiscussionStream(Context context,Item item,Loader loader){this.context=context.getApplicationContext();this.item=item;this.loader=loader;}
    boolean busy(){return busy;}
    void more(Document before,Repository.Result<Document> callback){
        if(closed||busy)return;
        if(!item.url.equals(before.url)||!DiscussionPages.usesStream(item.source,before)||!DiscussionPages.next(item.source,before)){callback.failure("来源未提供可用的后续页");return;}
        String namespace=new ArticleDiskCache(context).namespace(),session=ArticleDiskCache.session(item.url);long epoch=Repository.articleCacheEpoch();
        if(!ArticleSession.contentCurrent(before)||!session.equals(before.cachedSession)||!namespace.equals(before.cachedNamespace)){callback.failure("登录或缓存状态已变化，请重新读取正文后续读");return;}
        Document base=AnswerStream.copy(before);String requested=base.nextUrl;int request=++generation;busy=true;
        Item page=new Item(item.source,item.title,requested,item.detail,item.video,item.snippet,item.publishedAt,item.sourceTag);
        loader.load(page,new Repository.Result<>(){
            private boolean active(){return !closed&&busy&&request==generation;}
            private void fail(String reason){if(!active())return;busy=false;loader.cancel();callback.failure(reason);}
            public void success(Document doc){
                if(!active())return;
                if(!ArticleSession.contentCurrent(base)||epoch!=Repository.articleCacheEpoch()||!namespace.equals(new ArticleDiskCache(context).namespace())||!session.equals(ArticleDiskCache.session(item.url))){fail("登录或缓存状态已变化，已读内容保留，请重新读取正文");return;}
                if(!doc.loginRequired&&(doc.cachedSession==null||!doc.cachedSession.equals(ArticleDiskCache.session(doc.url))||!namespace.equals(doc.cachedNamespace))){fail("后续页会话已变化，已读内容保留，请重试");return;}
                try{Document merged=DiscussionPages.merge(item.source,base,doc,requested);busy=false;loader.cancel();callback.success(merged);}catch(IllegalArgumentException invalid){fail(invalid.getMessage());}
            }
            public void failure(String reason){fail(reason);}
        });
    }
    void close(){if(closed)return;closed=true;busy=false;generation++;loader.close();}
}
