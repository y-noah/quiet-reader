package app.quietreader;

import android.content.Context;
import static app.quietreader.Models.*;

/** One native foreground acquisition, independent of Activity lifetime. Main-thread ownership. */
final class PendingArticle implements AutoCloseable {
    private final Item item;
    private final Repository repository;
    private final ArticleSession session;
    private Repository.Result<Document> listener;
    private Document result;
    private String error;
    private boolean started,completed,closed;
    PendingArticle(Context context,Item item){this.item=item;repository=new Repository(context.getApplicationContext());session=new ArticleSession(context.getApplicationContext(),item);}
    boolean matches(Item current){return !closed&&current!=null&&ArticleVersion.sameRequest(current,item)&&session.current()&&(result==null||ArticleSession.contentCurrent(result));}
    void attach(Repository.Result<Document> callback){if(closed)return;listener=callback;deliver();}
    void detach(){listener=null;}
    void start(){
        if(started||closed)return;started=true;
        repository.articleForReading(item,new Repository.Result<Document>(){
            @Override public void success(Document document){if(closed)return;completed=true;result=document;repository.close();deliver();}
            @Override public void failure(String reason){if(closed)return;completed=true;error=reason;repository.close();deliver();}
        });
    }
    private void deliver(){
        if(closed||!completed||listener==null)return;
        Repository.Result<Document> callback=listener;Document document=result;String reason=error;
        listener=null;result=null;closed=true;
        if(!session.current()||document!=null&&!ArticleSession.contentCurrent(document))callback.failure("登录或缓存状态已变化，请重新读取");
        else if(document!=null)callback.success(document);
        else callback.failure(reason==null?"正文暂未取得，请重试":reason);
    }
    @Override public void close(){if(closed)return;closed=true;listener=null;result=null;repository.close();}
}
