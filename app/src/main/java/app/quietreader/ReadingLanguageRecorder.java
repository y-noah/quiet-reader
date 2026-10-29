package app.quietreader;

import android.content.Context;
import app.quietreader.discovery.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Body language and whole-topic media evidence; bounded writes never copy the body. */
final class ReadingLanguageRecorder {
    private static final ThreadPoolExecutor writer=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),r->new Thread(r,"reading-language"),new ThreadPoolExecutor.DiscardPolicy());
    static void record(Context context,String diagnosticDatabase,HotCandidate candidate,Document document){
        if(candidate==null||document.cachedSession==null)return;
        Document snapshot=snapshot(document);
        Context app=context.getApplicationContext();
        String database=(app.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&diagnosticDatabase!=null&&diagnosticDatabase.matches("test-ui-[a-z0-9-]+\\.db")?diagnosticDatabase:FeedStore.DATABASE;
        writer.execute(()->{try{
            ArticleDiskCache cache=new ArticleDiskCache(app);
            if(!ArticleSession.contentCurrent(snapshot)||!ArticleVersion.matches(candidate.readerItem(),snapshot)||!cache.namespace().equals(snapshot.cachedNamespace)
                    ||!snapshot.cachedSession.equals(ArticleDiskCache.session(snapshot.url)))return;
            ContentLanguage.Body language=BodyLanguageEvidence.classify(snapshot);
            boolean video=BodyVideoEvidence.classify(candidate.source,snapshot),textTopic=BodyVideoEvidence.textTopic(candidate.source,snapshot);
            if(language==ContentLanguage.Body.UNKNOWN&&!video&&!textTopic)return;
            // Login or cache clearing may happen while this optional work is queued.
            // Never publish evidence using a newer identity than the acquired body.
            if(!ArticleSession.contentCurrent(snapshot)||!cache.namespace().equals(snapshot.cachedNamespace)||!snapshot.cachedSession.equals(ArticleDiskCache.session(snapshot.url)))return;
            try(FeedStore store=new FeedStore(app,database)){
                if(video)store.recordVideo(candidate.id,candidate.textHash,snapshot.cachedAt);
                else{if(textTopic)store.recordTextTopic(candidate.id,candidate.textHash,snapshot.cachedAt);store.recordBodyLanguage(candidate.id,candidate.textHash,language,snapshot.cachedAt);}
            }
        }catch(RuntimeException ignored){/* Reading remains available if optional evidence cannot be saved. */}});
    }
    /** Copy mutable lists and acquisition fields, sharing only immutable prose blocks. */
    static Document snapshot(Document document){
        Document copy=new Document();copy.url=document.url;copy.topic=document.topic;copy.metadataHash=document.metadataHash;
        copy.cachedAt=document.cachedAt;copy.cachedSession=document.cachedSession;copy.cachedNamespace=document.cachedNamespace;
        copy.loginRequired=document.loginRequired;copy.sourceUnavailable=document.sourceUnavailable;copy.filteredVideo=document.filteredVideo;copy.videoTopic=document.videoTopic;
        copy.blocks.addAll(document.blocks);
        for(Section section:document.sections){Section item=new Section(section.id,section.label,section.answer);item.blocks.addAll(section.blocks);copy.sections.add(item);}
        return copy;
    }
}
