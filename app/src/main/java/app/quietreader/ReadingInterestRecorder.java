package app.quietreader;

import android.content.Context;
import app.quietreader.discovery.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

final class ReadingInterestRecorder {
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->new Thread(r,"reading-interest"),new ThreadPoolExecutor.DiscardPolicy());
    static void record(Context context,String diagnosticDatabase,HotCandidate candidate){
        record(context,diagnosticDatabase,candidate,false);
    }
    static void quickExit(Context context,String diagnosticDatabase,HotCandidate candidate){record(context,diagnosticDatabase,candidate,true);}
    static void record(Context context,String database,HotCandidate candidate,Document document){record(context,database,candidate,false,guard(context,candidate,document),ArticleProgressBinding.capture(context,candidate,document));}
    static void quickExit(Context context,String database,HotCandidate candidate,Document document){record(context,database,candidate,true,guard(context,candidate,document));}
    private static BooleanSupplier guard(Context context,HotCandidate candidate,Document doc){
        if(doc==null||!ArticleVersion.matches(candidate.readerItem(),doc)||!ArticleSession.contentCurrent(doc)||doc.cachedSession==null||!doc.cachedSession.equals(ArticleDiskCache.session(doc.url))||!new ArticleDiskCache(context).namespace().equals(doc.cachedNamespace))return ()->false;
        ArticleSession session=new ArticleSession(context,candidate.readerItem());TiebaTopic.Resolved topic=doc.topic;
        return ()->session.current()&&(topic==null||topic.postSession.equals(ArticleDiskCache.session(topic.postUrl)));
    }
    private static void record(Context context,String diagnosticDatabase,HotCandidate candidate,boolean quickExit){
        record(context,diagnosticDatabase,candidate,quickExit,()->true);
    }
    private static void record(Context context,String diagnosticDatabase,HotCandidate candidate,boolean quickExit,BooleanSupplier current){
        record(context,diagnosticDatabase,candidate,quickExit,current,null);
    }
    private static void record(Context context,String diagnosticDatabase,HotCandidate candidate,boolean quickExit,BooleanSupplier current,ArticleProgressBinding progress){
        Context app=context.getApplicationContext();long at=System.currentTimeMillis();
        String database=(app.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&diagnosticDatabase!=null&&diagnosticDatabase.matches("test-ui-[a-z0-9-]+\\.db")?diagnosticDatabase:FeedStore.DATABASE;
        writer.execute(()->{if(!current.getAsBoolean())return;try(FeedStore store=new FeedStore(app,database)){if(!current.getAsBoolean())return;if(quickExit)store.recordQuickExit(candidate,at);else{store.recordRead(candidate,at);if(progress!=null&&current.getAsBoolean())try{progress.consumed(store,at);}catch(RuntimeException ignored){/* Weak feedback/read facts do not depend on derivative body storage. */}}}catch(RuntimeException ignored){/* Optional weak evidence must not interrupt reading. */}});
    }
}
