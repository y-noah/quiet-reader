package app.quietreader;

import android.app.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real Android PNG/cache bytes; controlled transport, not network or peak-RAM evidence. */
final class ImageCacheLimitProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ImageCacheLimitProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)throw new IllegalStateException(label);}
    private void rejects(ReaderImages images,String image,String page)throws Exception{
        boolean rejected=false;try{images.load(image,page,null,()->true,ImagePreloader.IMAGE_LIMIT,false);}catch(ImageDiskCache.PayloadLimitException expected){rejected=true;}
        check(rejected,"speculative image limit rejects a previously acquired large payload");
    }
    void run(){File root=new File(test.getTargetContext().getCacheDir(),"test-image-limit-"+UUID.randomUUID());
        try{
            check(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator target only");
            if(!root.mkdir())throw new IOException("Cannot create isolated image directory");
            Bitmap bitmap=Bitmap.createBitmap(640,640,Bitmap.Config.ARGB_8888);int[] pixels=new int[640*640];Random random=new Random(1729);for(int n=0;n<pixels.length;n++)pixels[n]=0xff000000|random.nextInt(0x1000000);bitmap.setPixels(pixels,0,640,0,0,640,640);
            byte[] png;try(ByteArrayOutputStream out=new ByteArrayOutputStream()){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG compression failed");png=out.toByteArray();}finally{bitmap.recycle();}
            report.append("PNG_BYTES ").append(png.length).append('\n');check(png.length>ImagePreloader.IMAGE_LIMIT&&png.length<2*1024*1024,"actual decodable PNG exceeds speculation but fits normal reader memory budget");
            String image="https://images.example.org/cache-limit.png",page="https://example.org/cache-limit";AtomicInteger requests=new AtomicInteger();
            ImageDiskCache disk=new ImageDiskCache(root,16*1024*1024,8,()->DataStorageBudget.MIN_FREE+32*1024*1024);
            ReaderImages writer=new ReaderImages(u->null,(url,ref,cookies)->{requests.incrementAndGet();return png;},2*1024*1024);writer.attach(disk,()->"isolated-limit");
            byte[] full=writer.load(image,page);check(Arrays.equals(full,png)&&requests.get()==1,"normal reader acquisition accepts and caches the large PNG");
            rejects(writer,image,page);check(writer.load(image,page)==full&&requests.get()==1,"limited memory lookup retains full normal reader cache without another acquisition");
            File[] files=root.listFiles();check(files!=null&&files.length==1,"normal acquisition creates exactly one image record");File record=files[0];if(!record.setLastModified(System.currentTimeMillis()-10000))throw new IOException("Cannot set record timestamp");long at=record.lastModified(),length=record.length();
            ReaderImages reader=new ReaderImages(u->null,(url,ref,cookies)->{requests.incrementAndGet();throw new IOException("Offline controlled transport");},2*1024*1024);reader.attach(disk,()->"isolated-limit");
            rejects(reader,image,page);check(record.isFile()&&record.length()==length&&record.lastModified()==at,"limited disk lookup neither deletes nor renews the normal reader record");check(requests.get()==1,"oversized disk hit does not fall through to another download");
            byte[] restored=reader.load(image,page);check(Arrays.equals(restored,png)&&requests.get()==1,"normal reader restores full PNG offline after speculative rejection");
            Bitmap decoded=BitmapFactory.decodeByteArray(restored,0,restored.length);try{check(decoded!=null&&decoded.getWidth()==640&&decoded.getHeight()==640,"restored full reader payload still decodes at its original dimensions");}finally{if(decoded!=null)decoded.recycle();}
            rejects(reader,image,page);check(reader.load(image,page)==restored&&requests.get()==1,"restored memory cache remains reusable after another restricted lookup");
            writer.evictAll();reader.evictAll();
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{File[] files=root.listFiles();if(files!=null)for(File file:files)if(!file.delete())failures++;if(root.exists()&&!root.delete())failures++;}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
