package app.quietreader;

import android.content.Context;
import java.io.InterruptedIOException;
import static app.quietreader.Models.*;

/** Retainable image ownership without Activity, WebView or bitmap references. */
final class ReaderImageOwner implements AutoCloseable {
    private final ReaderImages images;
    private final Item item;
    private final ArticleSession session;
    private final TiebaTopic.Resolved topic;
    private volatile ReaderImages.Scope scope;
    private boolean interrupted;
    ReaderImageOwner(Context context,Item item,ReaderImages images){this(context,item,images,null);}
    ReaderImageOwner(Context context,Item item,ReaderImages images,Document document){this.images=images;this.item=item;topic=document==null?null:document.topic;session=new ArticleSession(context.getApplicationContext(),item);resume();}
    private boolean current(){return session.current()&&(topic==null||topic.postSession.equals(ArticleDiskCache.session(topic.postUrl)));}
    boolean matches(Item current){return ArticleVersion.sameRequest(item,current)&&current();}
    boolean matches(Item current,Document document){return matches(current)&&(topic==null?document.topic==null:document.topic!=null&&topic.postUrl.equals(document.topic.postUrl)&&topic.postSession.equals(document.topic.postSession));}
    boolean paused(){return scope==null;}
    ReaderImages.Scope snapshot(){return scope;}
    boolean takeInterrupted(){boolean value=interrupted;interrupted=false;return value;}
    void resume(){if(scope==null&&current())scope=images.openScope(this::current);}
    byte[] load(String url,String referer)throws Exception{ReaderImages.Scope active=scope;if(active==null)throw new InterruptedIOException("Reader image ownership paused");return active.load(url,referer);}
    @Override public void close(){ReaderImages.Scope active=scope;scope=null;if(active!=null)interrupted|=active.stop();}
}
