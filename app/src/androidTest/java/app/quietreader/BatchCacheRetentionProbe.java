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
            check(disk.read(old.url,namespace,ArticleDiskCache.session(old.url),false,now+2)==null,"queued old-batch writer cannot resurrect its pruned file");
            ArticleDiskCache.Hit kept=disk.read(shared.url,namespace,ArticleDiskCache.session(shared.url),false,now+2);
            check(kept!=null&&kept.at==now,"shared article survives without a freshness rewrite or network request");
            test.runOnMainSync(()->{
                check(repo[0].cachedArticle(new Item(Source.THEPAPER,old.title,old.url,""))==null,"old body is removed from RAM too");
                check(repo[0].cachedArticle(new Item(Source.THEPAPER,shared.title,shared.url,""))!=null,"shared body remains immediately available in RAM");
                check(new ReadingHistory(context).get(old.url).optInt("position")==99,"history and reading position survive derivative cache pruning");
            });
            ArticleDiskCache.Retention[] newer={null};test.runOnMainSync(()->newer[0]=disk.retainBatch(Set.of(fresh.url)));
            check(disk.pruneBatch(retained[0])&&disk.read(shared.url,namespace,ArticleDiskCache.session(shared.url),false,now+3)!=null,"superseded cleanup cannot delete files using an old keep set");
            check(disk.pruneBatch(newer[0])&&disk.read(shared.url,namespace,ArticleDiskCache.session(shared.url),false,now+3)==null&&disk.read(fresh.url,namespace,ArticleDiskCache.session(fresh.url),false,now+3)!=null,"latest retention replaces the keep set while retaining its new body");
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
        finally{test.runOnMainSync(()->{if(repo[0]!=null)repo[0].close();});File root=new File(parent,"article-bodies-v1");File[] files=root.listFiles();if(files!=null)for(File file:files)file.delete();root.delete();parent.delete();context.getSharedPreferences("article-cache",0).edit().clear().apply();context.getSharedPreferences("reading-history",0).edit().clear().apply();}
        report.append("scope=isolated controlled bodies and real queue/disk, not remote acquisition or phone energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
