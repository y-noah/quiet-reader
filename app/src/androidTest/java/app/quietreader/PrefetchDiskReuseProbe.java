package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.HotCandidate;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real disk restoration through the ordinary preloader; no network body fixture. */
final class PrefetchDiskReuseProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;
    private ArticlePreloader preloader;
    private Repository cache;
    private ExecutorService storage;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final List<File> files=new ArrayList<>();
    PrefetchDiskReuseProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private Object field(Object owner,Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){if(onUi(condition))return true;Thread.sleep(30);}return onUi(condition);}
    private void drainWrites()throws Exception{ExecutorService writer=(ExecutorService)field(null,ArticleDiskCache.class,"writer");writer.submit(()->{}).get(10,TimeUnit.SECONDS);}
    private Document body(Item item,String text){Document d=new Document();d.url=item.url;d.title=item.title;d.blocks.add(new Block("text",text));ArticleVersion.capture(item,d);return d;}
    private File file(Item item,String namespace,String session){File f=new File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(namespace+"\n"+session+"\n"+item.url)+".json");files.add(f);return f;}
    void run(){String database="test-ui-prefetch-disk-"+UUID.randomUUID()+".db";
        try{
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            Object controller=field(home,DiscoveryActivity.class,"controller");storage=(ExecutorService)field(controller,DiscoveryController.class,"storage");
            ui(()->cache=new Repository(home));
            ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());String namespace=disk.namespace();long now=System.currentTimeMillis();
            List<Item> items=new ArrayList<>();List<String> sessions=new ArrayList<>();List<Long> inodes=new ArrayList<>();
            for(int n=0;n<3;n++){
                Item item=new Item(Source.ITHOME,"预缓存磁盘命中测试"+n,"https://www.ithome.com/0/prefetch-disk-"+now+"-"+n+".htm","");items.add(item);
                String[] session={null};ui(()->session[0]=ArticleDiskCache.session(item.url));sessions.add(session[0]);
                File target=file(item,namespace,session[0]);disk.write(body(item,"此前已缓存的中文正文，用于验证重开后不会重复写入。".repeat(30)),namespace,session[0],now);
                inodes.add(android.system.Os.stat(target.getPath()).st_ino);
            }
            ui(cache::releaseArticleMemory);
            check(onUi(()->items.stream().allMatch(i->cache.cachedArticle(i)==null)),"all three bodies start absent from the memory cache");
            List<Document> prepared=new ArrayList<>();
            ui(()->{preloader=new ArticlePreloader(home,()->{},null,20,()->{},true);preloader.observePrepared((item,doc)->prepared.add(doc));preloader.offer(items);});
            check(await(()->prepared.size()==3&&!preloader.busy()),"ordinary preloader restores all three disk bodies and reaches idle");drainWrites();
            check(onUi(()->items.stream().allMatch(i->ArticleVersion.matches(i,cache.cachedArticle(i)))),"restored bodies are shared with exact candidate metadata");
            for(int n=0;n<items.size();n++){
                File target=files.get(n);long actual=android.system.Os.stat(target.getPath()).st_ino;
                report.append("DISK_IDENTITY item=").append(n).append(" before=").append(inodes.get(n)).append(" after=").append(actual).append('\n');
                check(actual==inodes.get(n),"disk restore preserves the existing file rather than atomically replacing item"+n);
                ArticleDiskCache.Hit hit=disk.read(items.get(n).url,namespace,sessions.get(n),false,System.currentTimeMillis());
                check(hit!=null&&hit.at==now,"disk restore retains original acquisition freshness item"+n);
            }
            ui(()->preloader.offer(items));check(await(()->prepared.size()==6&&!preloader.busy()),"repeated viewport offer reuses all three memory bodies");drainWrites();
            check(files.subList(0,3).stream().allMatch(File::isFile),"repeated offer retains all body files");
            ui(cache::releaseArticleMemory);
            ui(()->preloader.offer(items));
            check(await(()->prepared.size()==9&&!preloader.busy()),"the same preloader restores disk bodies again after RAM eviction without a five-minute retry delay");
            drainWrites();
            check(onUi(()->items.stream().allMatch(i->{Document body=cache.cachedArticle(i);return body!=null&&ArticleVersion.matches(i,body);})),"returning viewport republishes matching disk bodies into RAM in the existing preloader");
            ui(preloader::close);
            ui(cache::releaseArticleMemory);
            Item retained=items.get(0);List<Document> delivered=new ArrayList<>();
            Repository.Result<Document> callback=new Repository.Result<Document>(){public void success(Document d){delivered.add(d);}public void failure(String why){check(false,"retained callback: "+why);}};
            ui(()->{
                preloader=new ArticlePreloader(home,()->{},null,20,()->{},true);preloader.offer(Collections.singletonList(retained));
                // Run its normal scheduled pump in this same main-loop turn so detach
                // deterministically precedes the asynchronous disk callback.
                try{((Runnable)field(preloader,ArticlePreloader.class,"pump")).run();}catch(Exception e){throw new IllegalStateException(e);}
                check(preloader.promote(retained,callback),"in-flight disk restore can become the explicit reading request");preloader.detachForeground();
            });
            check(await(()->{try{return field(preloader,ArticlePreloader.class,"bufferedResult")!=null;}catch(Exception e){throw new IllegalStateException(e);}}),"detached reader buffers the real disk restoration before its next owner resumes");
            ui(()->preloader.resumeForeground(home,retained,callback,()->{}));
            check(await(()->delivered.size()==1&&!preloader.busy()),"new owner receives exactly one restored body");drainWrites();
            check(android.system.Os.stat(files.get(0).getPath()).st_ino==inodes.get(0),"restoration buffered across reader recreation also preserves the disk file");
            ui(preloader::close);
            Item fresh=new Item(Source.ITHOME,"新获取正文测试","https://www.ithome.com/0/prefetch-new-"+now+".htm","");
            String[] session={null};ui(()->session[0]=ArticleDiskCache.session(fresh.url));File target=file(fresh,namespace,session[0]);
            List<Document> acquired=new ArrayList<>();
            ui(()->{preloader=new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> result){result.success(body(item,"新获取的正文必须正常落盘，不能为了省写入而丢失缓存。"));}public void cancel(){}},20,()->{},true);preloader.observePrepared((item,doc)->acquired.add(doc));preloader.offer(Collections.singletonList(fresh));});
            check(await(()->acquired.size()==1&&!preloader.busy()),"controlled new acquisition completes through the ordinary preloader callback");drainWrites();
            ArticleDiskCache.Hit freshHit=disk.read(fresh.url,namespace,session[0],false,System.currentTimeMillis());
            check(target.isFile()&&freshHit!=null&&ArticleVersion.matches(fresh,freshHit.document),"newly acquired body still writes a readable versioned disk cache");
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{
            ui(()->{if(preloader!=null)preloader.close();if(cache!=null){cache.releaseArticleMemory();cache.close();}if(home!=null)home.finish();});test.waitForIdleSync();
            if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            try{drainWrites();}catch(Exception error){check(false,"writer cleanup: "+error.getClass().getSimpleName());}
            for(File file:files)if(file.exists()&&!file.delete())check(false,"fixture file cleanup");
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
