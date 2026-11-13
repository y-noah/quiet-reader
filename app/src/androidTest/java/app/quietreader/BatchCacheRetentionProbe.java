package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Actual asynchronous writer, disk reopen, RAM eviction and retained history. */
final class BatchCacheRetentionProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("CURRENT BATCH CACHE RETENTION\n");
    BatchCacheRetentionProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private Document body(String url,String title){Document doc=new Document();doc.url=url;doc.title=title;doc.blocks.add(new Block("text","这是供缓存换批验证的中文正文，记录本轮正文和来源信息。".repeat(30)));return doc;}
    void run(){String name="test-batch-retain-"+UUID.randomUUID();File parent=new File(test.getTargetContext().getCacheDir(),name);
        Context context=new ContextWrapper(test.getTargetContext()){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getCacheDir(){return parent;}
            @Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}
        };Repository[] repo={null};
        try{
            ArticleDiskCache disk=new ArticleDiskCache(context);long now=System.currentTimeMillis();String namespace=disk.namespace();
            Document old=body("https://www.thepaper.cn/newsDetail_forward_998851","旧批正文"),shared=body("https://www.thepaper.cn/newsDetail_forward_998852","两批共有正文"),fresh=body("https://www.thepaper.cn/newsDetail_forward_998853","新批正文");
            for(Document doc:List.of(old,shared,fresh)){doc.cachedAt=now;disk.write(doc,namespace,ArticleDiskCache.session(doc.url),now);}
            test.runOnMainSync(()->{repo[0]=new Repository(context);repo[0].cacheArticle(old);repo[0].cacheArticle(shared);new ReadingHistory(context).save(old.url,old,Set.of("body"),99);});
            Field lockField=ArticleDiskCache.class.getDeclaredField("IO_LOCK"),writerField=ArticleDiskCache.class.getDeclaredField("writer");lockField.setAccessible(true);writerField.setAccessible(true);ExecutorService writer=(ExecutorService)writerField.get(null);
            writer.submit(()->{}).get(5,TimeUnit.SECONDS);
            ArticleDiskCache.Retention[] retained={null};
            synchronized(lockField.get(null)){
                // Queue an old-batch write while the real IO worker cannot commit it.
                test.runOnMainSync(()->{disk.enqueue(old,namespace,ArticleDiskCache.session(old.url),now+1);retained[0]=repo[0].retainBatch(Set.of(shared.url,fresh.url));});
            }
            check(disk.pruneBatch(retained[0]),"worker prunes only records outside the newly accepted batch");writer.submit(()->{}).get(5,TimeUnit.SECONDS);
            check(disk.read(old.url,namespace,ArticleDiskCache.session(old.url),false,now+2)!=null,"batch switching keeps the already-persisted old body while retiring speculative writes");
            ArticleDiskCache.Hit kept=disk.read(shared.url,namespace,ArticleDiskCache.session(shared.url),false,now+2);
            check(kept!=null&&kept.at==now,"shared article survives without a freshness rewrite or network request");
            test.runOnMainSync(()->{
                check(repo[0].cachedArticle(new Item(Source.THEPAPER,old.title,old.url,""))==null,"old body is removed from RAM too");
                check(repo[0].cachedArticle(new Item(Source.THEPAPER,shared.title,shared.url,""))!=null,"shared body remains immediately available in RAM");
                check(new ReadingHistory(context).get(old.url).optInt("position")==99,"history and reading position survive derivative cache pruning");
            });
            ArticleDiskCache.Retention[] newer={null};test.runOnMainSync(()->newer[0]=disk.retainBatch(Set.of(fresh.url)));
            check(disk.pruneBatch(retained[0])&&disk.read(shared.url,namespace,ArticleDiskCache.session(shared.url),false,now+3)!=null,"superseded cleanup cannot delete files using an old keep set");
            check(disk.pruneBatch(newer[0])&&disk.read(shared.url,namespace,ArticleDiskCache.session(shared.url),false,now+3)!=null&&disk.read(fresh.url,namespace,ArticleDiskCache.session(fresh.url),false,now+3)!=null,"latest retention keeps both old persisted bodies and the new batch");
            Item versioned=new Item(Source.THEPAPER,"新版本标题",fresh.url,"");Document versionedDoc=body(fresh.url,versioned.title);
            ArticleVersion.capture(versioned,versionedDoc);ArticleSession[] inflight={null};
            test.runOnMainSync(()->{inflight[0]=new ArticleSession(context,versioned);repo[0].retainItems(List.of(versioned));});
            test.runOnMainSync(()->{
                check(inflight[0].current(),"batch retention does not invalidate an in-flight acquisition from the same account");
                check(inflight[0].stamp(versionedDoc),"retained valid acquisition can still stamp its original identity");
            });
            disk.write(versionedDoc,namespace,ArticleDiskCache.session(versioned.url),versionedDoc.cachedAt);
            Document obsolete=body(fresh.url,"旧版本标题");ArticleVersion.capture(new Item(Source.THEPAPER,obsolete.title,fresh.url,""),obsolete);
            disk.write(obsolete,namespace,ArticleDiskCache.session(versioned.url),versionedDoc.cachedAt+1);
            ArticleDiskCache.Hit current=disk.read(versioned.url,namespace,ArticleDiskCache.session(versioned.url),false,System.currentTimeMillis());
            check(current!=null&&ArticleVersion.matches(versioned,current.document),"late same-URL old metadata cannot overwrite the retained body version");
            CountDownLatch verified=new CountDownLatch(1);boolean[] durable={false};
            test.runOnMainSync(()->disk.verify(versionedDoc,hit->{durable[0]=hit!=null&&ArticleVersion.matches(versioned,hit.document);verified.countDown();}));
            check(verified.await(5,TimeUnit.SECONDS)&&durable[0],"ready receipt follows a successful serialized write and readback");
            // A verification job is a writer too: capture its original retention owner.
            Document retired=body(old.url,"已离开窗口的验证正文");ArticleVersion.capture(new Item(Source.THEPAPER,retired.title,old.url,""),retired);
            test.runOnMainSync(()->{disk.retainBatch(Set.of(old.url));new ArticleSession(context,new Item(Source.THEPAPER,retired.title,old.url,"")).stamp(retired);});
            CountDownLatch blockedWriter=new CountDownLatch(1),releaseWriter=new CountDownLatch(1),retiredReceipt=new CountDownLatch(1);boolean[] resurrected={false};
            writer.submit(()->{blockedWriter.countDown();try{releaseWriter.await(5,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}});
            if(!blockedWriter.await(5,TimeUnit.SECONDS))throw new IllegalStateException("writer barrier");
            try{test.runOnMainSync(()->{disk.verify(retired,hit->{resurrected[0]=hit!=null;retiredReceipt.countDown();});disk.retainItems(List.of(versioned));});}finally{releaseWriter.countDown();}
            check(retiredReceipt.await(5,TimeUnit.SECONDS)&&!resurrected[0]&&disk.read(old.url,namespace,ArticleDiskCache.session(old.url),false,System.currentTimeMillis())!=null,"retired new-version verification stays unready while the original persisted body remains readable");
            test.runOnMainSync(()->disk.publishItems(List.of(versioned)));
            ArticleDiskCache.Hit pinned=disk.read(versioned.url,namespace,ArticleDiskCache.session(versioned.url),false,versionedDoc.cachedAt+11*60_000);
            check(pinned!=null&&pinned.at==versionedDoc.cachedAt,"displayed body remains readable beyond online TTL without forging a new acquisition time");
            check(disk.read(versioned.url,namespace,"changed-account",false,versionedDoc.cachedAt+11*60_000)==null,"a published pin never crosses an account fingerprint");
            test.runOnMainSync(()->{disk.publishItems(List.of());repo[0].invalidateArticles();check(!inflight[0].current()&&!inflight[0].stamp(versionedDoc),"real namespace invalidation still rejects the older in-flight session");});
            context.getSharedPreferences("article-cache",0).edit().putString("namespace",namespace).commit();
            List<Document> burst=new ArrayList<>();Set<String> urls=new HashSet<>();for(int n=0;n<20;n++){Document doc=body("https://www.thepaper.cn/newsDetail_forward_"+(998860+n),"快速完成缓存第"+n+"条");burst.add(doc);urls.add(doc.url);}
            ArticleDiskCache.Retention[] full={null};test.runOnMainSync(()->full[0]=disk.retainBatch(urls));disk.pruneBatch(full[0]);
            synchronized(lockField.get(null)){test.runOnMainSync(()->{for(Document doc:burst)disk.enqueue(doc,namespace,ArticleDiskCache.session(doc.url),now);});}
            ThreadPoolExecutor queue=(ThreadPoolExecutor)writer;long until=SystemClock.elapsedRealtime()+5000;while((queue.getActiveCount()>0||!queue.getQueue().isEmpty())&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);
            int saved=0;for(Document doc:burst)if(disk.read(doc.url,namespace,ArticleDiskCache.session(doc.url),false,now+4)!=null)saved++;
            report.append("FAST_BATCH queued=20 diskSaved=").append(saved).append('\n');check(saved==20,"a fast complete finite batch does not silently lose disk writes when IO is briefly busy");
            List<Document> replacement=new ArrayList<>();Set<String> nextUrls=new HashSet<>();for(int n=0;n<20;n++){Document doc=body("https://www.thepaper.cn/newsDetail_forward_"+(998960+n),"紧接着换批第"+n+"条");replacement.add(doc);nextUrls.add(doc.url);}
            ArticleDiskCache.Retention[] latest={null};synchronized(lockField.get(null)){
                test.runOnMainSync(()->{for(Document doc:burst)disk.enqueue(doc,namespace,ArticleDiskCache.session(doc.url),now);latest[0]=disk.retainBatch(nextUrls);for(Document doc:replacement)disk.enqueue(doc,namespace,ArticleDiskCache.session(doc.url),now);});
            }
            disk.pruneBatch(latest[0]);until=SystemClock.elapsedRealtime()+5000;while((queue.getActiveCount()>0||!queue.getQueue().isEmpty())&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);
            saved=0;for(Document doc:replacement)if(disk.read(doc.url,namespace,ArticleDiskCache.session(doc.url),false,now+5)!=null)saved++;
            check(saved==20,"rapid replacement frees old queued work so the entire new batch can still persist");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{test.runOnMainSync(()->{if(repo[0]!=null)repo[0].close();});File root=new File(parent,"article-bodies-v1");File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();root.delete();parent.delete();context.getSharedPreferences("source-session",0).edit().clear().apply();context.getSharedPreferences("article-cache",0).edit().clear().apply();context.getSharedPreferences("reading-history",0).edit().clear().apply();}
        report.append("scope=isolated controlled bodies and real queue/disk, not remote acquisition or phone energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
