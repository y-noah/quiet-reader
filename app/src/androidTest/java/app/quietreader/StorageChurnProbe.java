package app.quietreader;

import android.app.Instrumentation;
import android.content.*;
import android.system.Os;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.BiConsumer;
import static app.quietreader.Models.*;

/** Isolated sparse/physical size boundary plus bounded cache churn; no source network. */
final class StorageChurnProbe {
    static void run(Instrumentation test,BiConsumer<Boolean,String> check)throws Exception {
        run(test,check,false);
    }
    static void run(Instrumentation test,BiConsumer<Boolean,String> check,boolean physical)throws Exception {
        if(physical&&(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName())||!android.os.Build.HARDWARE.equals("ranchu")))throw new IllegalStateException("Physical storage workload requires the isolated emulator target");
        String name="test-storage-churn-"+UUID.randomUUID(),database=name+".db";
        Context base=test.getTargetContext();File root=new File(base.getCacheDir(),name);
        Context context=new ContextWrapper(base){
            @Override public Context getApplicationContext(){return this;}
            @Override public File getCacheDir(){return new File(root,"cache");}
            @Override public File getDataDir(){return root;}
            @Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}
        };
        try(FeedStore store=new FeedStore(base,database)) {
            File model=new File(root,"files/models/"+(physical?"physical":"sparse")+"-size-boundary.bin");
            if(!model.getParentFile().mkdirs())throw new IOException("Test directory creation failed");
            long size=2L*1024*1024*1024+4096;
            if(physical)writePhysical(model,size);
            else try(RandomAccessFile file=new RandomAccessFile(model,"rw")){file.setLength(size);file.seek(file.length()-1);file.write(7);}
            long allocated=Os.stat(model.getPath()).st_blocks*512L;
            check.accept(model.length()==size&&(physical?allocated>=size:allocated<size),(physical?"physical full writes exceed 2GiB with allocated bytes=":"sparse fixture crosses 2GiB logical size with allocated bytes=")+allocated+" logical bytes="+model.length());
            File web=new File(root,"app_webview/Default/Cookies"),prefs=new File(root,"shared_prefs/protected.xml");
            web.getParentFile().mkdirs();prefs.getParentFile().mkdirs();
            byte[] marker="synthetic protected session metadata".getBytes(StandardCharsets.UTF_8);
            Files.write(web.toPath(),marker);Files.write(prefs.toPath(),marker);
            long now=System.currentTimeMillis();
            store.feedback(name,"MORE","sample","test-target",now,null,"中文兴趣保留",Source.ZHIHU);
            String feedback=store.activeFeedback(now).toString();long profile=store.version("profile");
            DataStorageBudget.Snapshot measured=DataStorageBudget.measure(root);
            check.accept(measured.complete&&measured.bytes>2L*1024*1024*1024&&measured.webBytes==marker.length,"real metadata inventory reports logical data above 2GiB and separate protected web bytes");
            check.accept(DataStorageBudget.freeSpace(root).allowsPrefetch(),"real filesystem free-space admission stays open above 2GiB logical data");
            final long budget=120000;
            ArticleDiskCache disk=new ArticleDiskCache(context,budget);String namespace=disk.namespace(),session=HotCandidate.hash("synthetic-session");
            List<String> urls=new ArrayList<>();boolean bounded=true,latest=true;
            for(int n=0;n<40;n++){
                String url="https://www.zhihu.com/question/99008800?batch="+n;urls.add(url);
                Document doc=new Document();doc.url=url;doc.title="缓存轮换"+n;doc.blocks.add(new Block("text","中文正文".repeat(1800)));
                disk.write(doc,namespace,session,now+n);
                latest&=new ArticleDiskCache(context,budget).read(url,namespace,session,false,now+n)!=null;
                bounded&=bytes(new File(context.getCacheDir(),"article-bodies-v1"))<=budget;
            }
            check.accept(latest&&bounded,"40 actual body writes remain bounded and newest bodies reopen above 2GiB logical total");
            check.accept(disk.read(urls.get(0),namespace,session,false,now+40)==null,"old body evicted while current body remains available");
            String recent=urls.get(39);File bodies=new File(context.getCacheDir(),"article-bodies-v1");
            File expired=new File(bodies,"expired.json"),partial=new File(bodies,"body-abandoned.partial");
            Files.write(expired.toPath(),marker);expired.setLastModified(now-ArticleDiskCache.MAX_AGE-60000);
            Files.write(partial.toPath(),marker);partial.setLastModified(now-7200000);
            Document next=new Document();next.url=recent;next.title="更新正文";next.blocks.add(new Block("text","更新后仍正常缓存"));
            disk.write(next,namespace,session,now+50);
            check.accept(!expired.exists()&&!partial.exists(),"next body action removes expired cache and abandoned temporary record");
            File images=new File(context.getCacheDir(),"reader-images-v1");
            ImageDiskCache image=new ImageDiskCache(images,8192,3,root::getUsableSpace);
            byte[] png=android.util.Base64.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jr1kAAAAASUVORK5CYII=",0);
            Map<String,String> sessions=Collections.singletonMap("https://example.org/",session);
            boolean imageBound=true;
            for(int n=0;n<30;n++){image.write("image-"+n,"https://example.org/"+n+".png",sessions,png,now+n,()->true);imageBound&=images.listFiles()!=null&&images.listFiles().length<=3&&bytes(images)<=8192;}
            check.accept(imageBound&&image.read("image-29","https://example.org/29.png",now+31)!=null&&image.read("image-0","https://example.org/0.png",now+31)==null,"30 actual image writes evict old images and retain latest within byte/count budgets");
            ImageDiskCache low=new ImageDiskCache(images,8192,3,()->DataStorageBudget.MIN_FREE-1);
            low.write("low-space","https://example.org/low.png",sessions,png,now+32,()->true);
            check.accept(low.read("low-space","https://example.org/low.png",now+32)==null&&image.read("image-29","https://example.org/29.png",now+32)!=null,"low-space image admission preserves existing valid cache");
            disk.invalidate();check.accept(disk.clearFiles()&&image.clearFiles(),"explicit derivative cleanup succeeds after cache churn");
            store.clearDerivedCache();
            check.accept(feedback.equals(store.activeFeedback(now).toString())&&profile==store.version("profile"),"actual SQLite interest feedback and profile revision survive derivative cleanup");
            check.accept(Arrays.equals(marker,Files.readAllBytes(web.toPath()))&&Arrays.equals(marker,Files.readAllBytes(prefs.toPath()))&&model.length()>2L*1024*1024*1024,"cache churn and cleanup preserve synthetic web/preferences markers and model-sized file");
            check.accept(DataStorageBudget.freeSpace(root).allowsPrefetch(),"space admission remains available after repeated writes and cleanup");
        } finally {
            base.deleteDatabase(database);base.deleteSharedPreferences(name+"article-cache");delete(root,root);
            check.accept(!root.exists()&&!base.getDatabasePath(database).exists(),"isolated size fixture and temporary database fully removed");
        }
    }
    private static void writePhysical(File file,long size)throws IOException {
        if(file.getParentFile().getUsableSpace()<size+512L*1024*1024)throw new IOException("Insufficient space for bounded physical fixture plus safety reserve");
        byte[] block=new byte[1024*1024];new Random(20261027).nextBytes(block);
        long deadline=android.os.SystemClock.elapsedRealtime()+120_000;
        try(FileOutputStream out=new FileOutputStream(file)){
            for(long remaining=size;remaining>0;){
                if(Thread.currentThread().isInterrupted()||android.os.SystemClock.elapsedRealtime()>deadline)throw new InterruptedIOException("Physical fixture write cancelled or timed out");
                int count=(int)Math.min(block.length,remaining);out.write(block,0,count);remaining-=count;
            }
            out.getFD().sync();
        }
    }
    private static long bytes(File root){long sum=0;File[] files=root.listFiles();if(files!=null)for(File file:files)sum+=file.length();return sum;}
    private static void delete(File file,File root)throws IOException {
        String path=file.getCanonicalPath(),base=root.getCanonicalPath();if(!path.equals(base)&&!path.startsWith(base+File.separator))throw new IOException("Test path escape");
        File[] files=file.listFiles();if(files!=null)for(File child:files)delete(child,root);if(file.exists()&&!file.delete())throw new IOException("Test cleanup failed");
    }
}
