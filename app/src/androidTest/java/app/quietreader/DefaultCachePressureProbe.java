package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.os.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static app.quietreader.Models.*;

/** Actual default byte quotas and filesystem eviction, synthetic payloads; no source network. */
final class DefaultCachePressureProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    DefaultCachePressureProbe(Instrumentation test){this.test=test;}
    private void check(boolean okay,String label){checks++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');if(!okay)throw new IllegalStateException(label);}
    private long bytes(File root){long size=0;File[] files=root.listFiles();if(files!=null)for(File file:files)size+=file.length();return size;}
    private long allocated(File root)throws Exception{long size=0;File[] files=root.listFiles();if(files!=null)for(File file:files)size+=android.system.Os.stat(file.getPath()).st_blocks*512L;return size;}
    private byte[] png()throws Exception{int width=512,height=512;int[] pixels=new int[width*height];Random random=new Random(20261028);for(int n=0;n<pixels.length;n++)pixels[n]=0xff000000|random.nextInt(0x1000000);Bitmap bitmap=Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888);try(ByteArrayOutputStream out=new ByteArrayOutputStream()){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG generation failed");return out.toByteArray();}finally{bitmap.recycle();}}
    private void delete(File file,File root)throws Exception{String path=file.getCanonicalPath(),base=root.getCanonicalPath();if(!path.equals(base)&&!path.startsWith(base+File.separator))throw new IOException("Test path escape");File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child,root);if(file.exists()&&!file.delete())throw new IOException("Test cleanup failed");}
    void run(){Context base=test.getTargetContext();String name="test-default-cache-"+UUID.randomUUID(),database=name+".db";File isolated=new File(base.getCacheDir(),name);
        Context context=new ContextWrapper(base){@Override public Context getApplicationContext(){return this;}@Override public File getCacheDir(){return isolated;}@Override public SharedPreferences getSharedPreferences(String key,int mode){return super.getSharedPreferences(name+key,mode);}};
        try{
            check(base.getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu")&&(base.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)!=0,"isolated debug emulator only");
            check(base.getCacheDir().getUsableSpace()>512L*1024*1024,"filesystem has more than512MiB spare before bounded quota workload");
            try(FeedStore store=new FeedStore(base,database)){
                long now=System.currentTimeMillis();store.feedback(name,"MORE","sample","test-target",now,null,"默认额度测试兴趣保留",Source.THEPAPER);String feedback=store.activeFeedback(now).toString();long profile=store.version("profile");
                ArticleDiskCache bodyCache=new ArticleDiskCache(context);String namespace=bodyCache.namespace(),session=HotCandidate.hash("synthetic-default-quota");File bodies=new File(isolated,"article-bodies-v1");
                String payload="中".repeat(250_000),keepUrl="https://www.thepaper.cn/newsDetail_forward_990288000";boolean bounded=true,latest=true,kept=true;long peakBodies=0;int bodyWrites=160;
                for(int n=0;n<bodyWrites;n++){
                    long at=System.currentTimeMillis();if(n>0)kept&=bodyCache.read(keepUrl,namespace,session,false,at)!=null;
                    Document doc=new Document();doc.url=n==0?keepUrl:"https://www.thepaper.cn/newsDetail_forward_990288"+n;doc.title="默认额度正文"+n;doc.blocks.add(new Block("text",payload));bodyCache.write(doc,namespace,session,at);
                    latest&=new ArticleDiskCache(context).read(doc.url,namespace,session,false,System.currentTimeMillis())!=null;long used=bytes(bodies);peakBodies=Math.max(peakBodies,used);bounded&=used<=ArticleDiskCache.MAX_BYTES;
                    if(n%40==39){Bundle progress=new Bundle();progress.putString("stream","bodyWrites="+(n+1)+" bytes="+used+"\n");test.sendStatus(1,progress);}
                }
                long bodyBytes=bytes(bodies),bodyAllocated=allocated(bodies);report.append("BODIES writes=").append(bodyWrites).append(" peakBytes=").append(peakBodies).append(" finalBytes=").append(bodyBytes).append(" allocatedBytes=").append(bodyAllocated).append('\n');
                check(bounded&&peakBodies>ArticleDiskCache.MAX_BYTES-2L*ArticleDiskCache.MAX_ENTRY,"actual body files reach near default96MiB budget and remain bounded after each write");
                check(latest&&kept&&bodyCache.read(keepUrl,namespace,session,false,System.currentTimeMillis())!=null,"latest and repeatedly read body survive actual byte-pressure eviction");
                check(bodyCache.read("https://www.thepaper.cn/newsDetail_forward_9902881",namespace,session,false,System.currentTimeMillis())==null,"old unused body is actually evicted at default byte quota");
                File images=new File(isolated,"reader-images-v1");ImageDiskCache imageCache=new ImageDiskCache(images);byte[] png=png();Map<String,String> sessions=Collections.singletonMap("https://example.org/",session);String keepImage="https://example.org/default-kept.png";int imageWrites=(int)(ImageDiskCache.MAX_BYTES/png.length)+28;long peakImages=0;bounded=true;latest=true;kept=true;
                for(int n=0;n<imageWrites;n++){
                    long at=System.currentTimeMillis();if(n>0)kept&=imageCache.read("kept",keepImage,at)!=null;String key=n==0?"kept":"image"+n,url=n==0?keepImage:"https://example.org/default-"+n+".png";
                    imageCache.write(key,url,sessions,png,at,()->true);ImageDiskCache.Hit hit=imageCache.read(key,url,System.currentTimeMillis());latest&=hit!=null&&Arrays.equals(png,hit.bytes);long used=bytes(images);peakImages=Math.max(peakImages,used);bounded&=used<=ImageDiskCache.MAX_BYTES;
                    if(n%40==39){Bundle progress=new Bundle();progress.putString("stream","imageWrites="+(n+1)+" bytes="+used+"\n");test.sendStatus(1,progress);}
                }
                report.append("IMAGES writes=").append(imageWrites).append(" payloadBytes=").append(png.length).append(" peakBytes=").append(peakImages).append(" finalBytes=").append(bytes(images)).append(" allocatedBytes=").append(allocated(images)).append('\n');
                check(bounded&&peakImages>ImageDiskCache.MAX_BYTES-2L*png.length,"actual image files reach near default128MiB budget and remain bounded after each write");
                check(latest&&kept&&imageCache.read("kept",keepImage,System.currentTimeMillis())!=null&&imageCache.read("image1","https://example.org/default-1.png",System.currentTimeMillis())==null,"latest and recently read images survive while unused image is physically evicted");
                ImageDiskCache.Hit restored=new ImageDiskCache(images).read("kept",keepImage,System.currentTimeMillis());Bitmap decoded=restored==null?null:BitmapFactory.decodeByteArray(restored.bytes,0,restored.bytes.length);check(decoded!=null&&decoded.getWidth()==512&&decoded.getHeight()==512,"fresh image-cache instance restores a genuinely decodable512px PNG after pressure");if(decoded!=null)decoded.recycle();
                long before=bytes(images);imageCache.write("cancelled","https://example.org/cancelled.png",sessions,png,System.currentTimeMillis(),()->false);check(bytes(images)==before&&imageCache.read("kept",keepImage,System.currentTimeMillis())!=null,"already cancelled admission creates no new image and preserves existing cache");
                check(feedback.equals(store.activeFeedback(now).toString())&&profile==store.version("profile"),"default-quota churn preserves independent SQLite feedback and profile revision");
                bodyCache.invalidate();check(bodyCache.clearFiles()&&imageCache.clearFiles()&&bytes(bodies)==0&&bytes(images)==0,"explicit derivative cleanup removes actual default-quota records");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{base.deleteDatabase(database);base.deleteSharedPreferences(name+"article-cache");try{delete(isolated,isolated);check(!isolated.exists()&&!base.getDatabasePath(database).exists(),"temporary cache directory and database are fully removed");}catch(Exception error){failures++;report.append("ERROR cleanup ").append(error).append('\n');}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
