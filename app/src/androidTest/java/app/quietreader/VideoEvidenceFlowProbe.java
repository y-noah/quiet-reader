package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Cached synthetic video metadata through the real preloader/controller/SQLite snapshot path. */
final class VideoEvidenceFlowProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private ExecutorService storage;
    private final StringBuilder report=new StringBuilder();private int checks,failures;
    VideoEvidenceFlowProbe(Instrumentation test){this.test=test;}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+8000;while(true){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException("Video flow timeout");Thread.sleep(50);}}
    private void cleanupFixture(){
        android.content.SharedPreferences.Editor markers=test.getTargetContext().getSharedPreferences("video-filter",0).edit(),history=test.getTargetContext().getSharedPreferences("reading-history",0).edit();
        for(int n=0;n<3;n++){Item item=new Item(Source.HUPU,"中文合成话题"+n,"https://bbs.hupu.com/"+(99007600+n)+".html","");markers.remove(VideoPolicy.key(item));history.remove(item.url);}markers.commit();history.commit();
    }
    void run(){String database="test-ui-video-"+UUID.randomUUID()+".db";
        try{
            cleanupFixture();
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();
            for(int n=0;n<3;n++)candidates.add(new HotCandidate(Source.HUPU,"https://bbs.hupu.com/"+(99007600+n)+".html","中文合成话题"+n,"","",false,n+1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){long r=store.beginRefresh(Source.HUPU);store.upsertSnapshot(Source.HUPU,r,candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            DiscoveryController controller=(DiscoveryController)field(activity,"controller");storage=(ExecutorService)field(controller,"storage");
            await(()->(boolean)field(controller,"ready")&&((List<?>)field(activity,"feed")).size()==3);
            HotCandidate target=candidates.get(0);Document prose=new Document();prose.url=target.originalUrl;prose.title=target.title;
            prose.videoTopic=true;prose.unsupportedVideo=true;prose.blocks.add(new Block("text","This report describes scientific observations and discusses their implications for future research. ".repeat(10)));
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(target.readerItem(),prose);repo.cacheArticle(prose);repo.close();((ArticlePreloader)field(controller,"preloader")).offer(Collections.singletonList(target.readerItem()));});
            FeedStore store=(FeedStore)field(controller,"store");long until=SystemClock.elapsedRealtime()+8000;boolean recorded=false;
            while(!recorded&&SystemClock.elapsedRealtime()<until){recorded=storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(target.id)&&c.video&&c.videoEvidenceAt>0)).get(3,TimeUnit.SECONDS);if(!recorded)Thread.sleep(50);}
            check(recorded,"discovery preloader persists whole-topic video evidence through controller");
            await(()->(long)field(controller,"pending")>0);
            check(((List<?>)field(activity,"feed")).size()==3,"video evidence keeps the currently displayed feed frozen");
            ui(()->controller.applyPending(true));await(()->((List<?>)field(activity,"feed")).size()==2);
            check(((List<?>)field(activity,"feed")).size()==2,"applying rebuilt feed removes the video candidate");
            check(storage.submit(()->store.candidates(100,0).size()==3).get(3,TimeUnit.SECONDS),"video filtering retains source facts instead of deleting the candidate");
            HotCandidate direct=candidates.get(1);Document directBody=new Document();directBody.url=direct.originalUrl;directBody.title=direct.title;
            directBody.videoTopic=true;directBody.unsupportedVideo=true;directBody.blocks.add(new Block("text","This independent article explains how researchers measure and interpret experimental results. ".repeat(10)));
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(direct.readerItem(),directBody);repo.cacheArticle(directBody);repo.close();});
            Instrumentation.ActivityMonitor readerMonitor=test.addMonitor(MainActivity.class.getName(),null,false);
            ui(()->{try{java.lang.reflect.Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(activity,direct);}catch(Exception e){throw new IllegalStateException(e);}});
            Activity reader=test.waitForMonitorWithTimeout(readerMonitor,5000);test.removeMonitor(readerMonitor);
            check(reader!=null,"direct card opens the real reader without a speculative request");
            if(reader==null)throw new IllegalStateException("Reader absent");
            until=SystemClock.elapsedRealtime()+8000;boolean readRecorded=false;
            while(!readRecorded&&SystemClock.elapsedRealtime()<until){readRecorded=storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(direct.id)&&c.video&&c.videoEvidenceAt>0)).get(3,TimeUnit.SECONDS);if(!readRecorded)Thread.sleep(50);}
            check(readRecorded,"ordinary reader persists whole-topic video evidence without prefetch");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);await(reader::isFinishing);
            await(()->(long)field(controller,"pending")>0);
            ui(()->controller.applyPending(true));await(()->((List<?>)field(activity,"feed")).size()==1);
            check(((List<?>)field(activity,"feed")).size()==1,"returning from reader rebuilds recommendations with new video evidence");
            HotCandidate untouched=candidates.get(2);Document stale=new Document();stale.url=untouched.originalUrl;stale.videoTopic=true;stale.unsupportedVideo=true;stale.blocks.add(new Block("text","合成说明"));
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(untouched.readerItem(),stale);repo.cacheArticle(stale);repo.close();stale.cachedNamespace="obsolete-namespace";ReadingLanguageRecorder.record(activity,database,untouched,stale);});
            Field writerField=ReadingLanguageRecorder.class.getDeclaredField("writer");writerField.setAccessible(true);ExecutorService writer=(ExecutorService)writerField.get(null);writer.submit(()->{}).get(3,TimeUnit.SECONDS);
            check(storage.submit(()->store.candidates(100,0).stream().noneMatch(c->c.id.equals(untouched.id)&&c.video)).get(3,TimeUnit.SECONDS),"invalidated cache namespace cannot contribute video evidence");
            ui(()->{stale.cachedNamespace=new ArticleDiskCache(activity).namespace();stale.cachedSession="obsolete-cookie-session";ReadingLanguageRecorder.record(activity,database,untouched,stale);});writer.submit(()->{}).get(3,TimeUnit.SECONDS);
            check(storage.submit(()->store.candidates(100,0).stream().noneMatch(c->c.id.equals(untouched.id)&&c.video)).get(3,TimeUnit.SECONDS),"old login session cannot contribute video evidence");

            Document corrected=SourceParser.article(Source.HUPU,"<h1>同一话题的新图文正文</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail>"+"这是来源重新取得的完整主帖正文，描述当前话题的详细信息与讨论背景。".repeat(20)+"</div></div>",target.originalUrl);
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(target.readerItem(),corrected);repo.cacheArticle(corrected);repo.close();});
            check(BodyVideoEvidence.textTopic(target.source,corrected),"parsed primary prose supplies explicit text correction evidence");
            long acquiredAt=corrected.cachedAt;String acquiredSession=corrected.cachedSession,acquiredNamespace=corrected.cachedNamespace,acquiredVersion=corrected.metadataHash;
            long oldVideoAt=storage.submit(()->store.candidates(100,0).stream().filter(c->c.id.equals(target.id)).findFirst().get().videoEvidenceAt).get(3,TimeUnit.SECONDS);
            ui(()->{corrected.cachedAt=oldVideoAt-1;ReadingLanguageRecorder.record(activity,database,target,corrected);});writer.submit(()->{}).get(3,TimeUnit.SECONDS);
            check(storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(target.id)&&c.video)).get(3,TimeUnit.SECONDS),"older same-version prose cannot clear newer video observation");
            ui(()->{corrected.cachedAt=acquiredAt;corrected.metadataHash="obsolete-metadata";ReadingLanguageRecorder.record(activity,database,target,corrected);corrected.metadataHash=acquiredVersion;corrected.cachedSession="obsolete-session";ReadingLanguageRecorder.record(activity,database,target,corrected);corrected.cachedSession=acquiredSession;corrected.cachedNamespace="obsolete-namespace";ReadingLanguageRecorder.record(activity,database,target,corrected);});writer.submit(()->{}).get(3,TimeUnit.SECONDS);
            check(storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(target.id)&&c.video)).get(3,TimeUnit.SECONDS),"wrong metadata, login session and namespace cannot clear inferred video");
            ui(()->corrected.cachedNamespace=acquiredNamespace);
            Instrumentation.ActivityMonitor correctionMonitor=test.addMonitor(MainActivity.class.getName(),null,false);
            ui(()->{try{java.lang.reflect.Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(activity,target);}catch(Exception e){throw new IllegalStateException(e);}});
            Activity correctionReader=test.waitForMonitorWithTimeout(correctionMonitor,5000);test.removeMonitor(correctionMonitor);
            check(correctionReader!=null,"explicit re-open uses real reader for updated text body");
            if(correctionReader==null)throw new IllegalStateException("Correction reader absent");
            until=SystemClock.elapsedRealtime()+8000;boolean correctedType=false;
            while(!correctedType&&SystemClock.elapsedRealtime()<until){correctedType=storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(target.id)&&!c.video&&c.videoEvidenceAt==acquiredAt)).get(3,TimeUnit.SECONDS);if(!correctedType)Thread.sleep(50);}
            check(correctedType,"actual reader corrects same-metadata inferred video from newly acquired primary prose");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);await(correctionReader::isFinishing);await(()->(long)field(controller,"pending")>0);
            check(((List<?>)field(activity,"feed")).size()==1,"text correction also preserves currently displayed frozen feed");
            ui(()->controller.applyPending(true));await(()->((List<?>)field(activity,"feed")).size()==2);
            check(((List<?>)field(activity,"feed")).size()==2,"applying corrected snapshot restores readable text topic to discovery");
            long newerVideoAt=System.currentTimeMillis()-1;
            check(storage.submit(()->store.recordVideo(target.id,target.textHash,newerVideoAt)).get(3,TimeUnit.SECONDS),"newer controlled video observation sets up frozen-feed prefetch correction");
            Document prefetched=SourceParser.article(Source.HUPU,"<h1>更新的图文主帖</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail>"+"这是再次从来源获得的完整图文主帖内容，用来验证主动缓存的同版本类型纠正。".repeat(20)+"</div></div>",target.originalUrl);
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(target.readerItem(),prefetched);repo.cacheArticle(prefetched);repo.close();((ArticlePreloader)field(controller,"preloader")).offer(List.of(target.readerItem()));});
            until=SystemClock.elapsedRealtime()+8000;boolean prefetchCorrected=false;
            while(!prefetchCorrected&&SystemClock.elapsedRealtime()<until){prefetchCorrected=storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(target.id)&&!c.video&&c.videoEvidenceAt==prefetched.cachedAt)).get(3,TimeUnit.SECONDS);if(!prefetchCorrected)Thread.sleep(50);}
            check(prefetchCorrected,"actual preloader/controller corrects same-version video from newer primary text");
            ui(()->{Repository repo=new Repository(activity);repo.releaseArticleMemory();repo.close();});
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{if(activity!=null)ui(activity::finish);test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}cleanupFixture();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
