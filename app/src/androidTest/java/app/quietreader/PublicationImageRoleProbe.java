package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** A publication-only body cleanup must not discard a retained, slow image. */
final class PublicationImageRoleProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;private DiscoveryController controller;private Repository repo;
    private final AtomicBoolean release=new AtomicBoolean();private final AtomicInteger requests=new AtomicInteger();
    private int checks,failures;private byte[] png;
    private final String image="https://9.9.9.9/publication-image-"+UUID.randomUUID()+".png";
    private final StringBuilder report=new StringBuilder("PUBLICATION IMAGE ROLE\n");
    PublicationImageRoleProbe(Instrumentation test){this.test=test;}
    private Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier action){boolean[] result={false};ui(()->result[0]=action.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(stage);Thread.sleep(20);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){
        protected URLConnection openConnection(URL url)throws IOException{
            if(!image.equals(url.toString()))throw new IOException("Unexpected controlled URL");
            return new HttpURLConnection(url){
                public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}
                public int getResponseCode(){requests.incrementAndGet();while(!release.get())try{Thread.sleep(10);}catch(InterruptedException ignored){}return 200;}
                public long getContentLengthLong(){return png.length;}
                public InputStream getInputStream(){return new ByteArrayInputStream(png);}
            };
        }
    });}
    void run(){String database="test-ui-publication-image-"+UUID.randomUUID()+".db";List<HotCandidate> candidates=new ArrayList<>();ExecutorService storage=null;
        try{
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff526a7b);ByteArrayOutputStream encoded=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,encoded);bitmap.recycle();png=encoded.toByteArray();transport();
            long now=System.currentTimeMillis();for(int n=0;n<15;n++)candidates.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"发布清理中文图片缓存验证第"+n+"项","","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
            ui(()->{repo=new Repository(test.getTargetContext());for(HotCandidate candidate:candidates){Item item=candidate.readerItem();Document doc=new Document();doc.url=item.url;doc.title=item.title;Section body=new Section("body","正文",false);body.blocks.add(new Block("text","受控的中文正文，检验同一缓存窗口发布时正在下载的图片仍可完成。".repeat(20)));if(candidate==candidates.get(0))body.blocks.add(new Block("image",image));doc.sections.add(body);doc.blocks.addAll(body.blocks);ArticleVersion.capture(item,doc);repo.cacheArticle(doc);}});
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(home,"controller");storage=(ExecutorService)field(controller,"storage");
            await("retained image starts downloading",()->requests.get()==1);
            ImagePreloader images=(ImagePreloader)field(controller,"imagePreloader");
            check(onUi(images::busy),"production image preloader owns the controlled slow request");
            ui(()->{try{Field changed=DiscoveryController.class.getDeclaredField("cachePublicationChanged");changed.setAccessible(true);changed.setBoolean(controller,true);Method cleanup=DiscoveryController.class.getDeclaredMethod("cacheAcceptedBatch",boolean.class);cleanup.setAccessible(true);cleanup.invoke(controller,false);}catch(Exception e){throw new IllegalStateException(e);}});
            storage.submit(()->{}).get(5,TimeUnit.SECONDS);release.set(true);
            await("slow image completes",()->!images.busy());
            ReaderImages diskOnly=new ReaderImages(url->android.webkit.CookieManager.getInstance().getCookie(url),(url,ref,cookies)->{throw new IOException("Expected proactive disk hit");},8192);diskOnly.attach(test.getTargetContext());
            boolean restored=false;try{restored=Arrays.equals(png,diskOnly.load(image,candidates.get(0).originalUrl));}catch(IOException expected){}
            check(restored,"same-window publication cleanup preserves and stores the in-flight image");
            ui(images::start);Thread.sleep(300);
            check(requests.get()==1,"restarting image preparation neither redownloads a retained image nor needs cooldown recovery");
            check(onUi(()->!images.busy()),"retained image preparation returns to idle");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{release.set(true);ui(()->{if(controller!=null)controller.close();if(home!=null)home.finish();if(repo!=null)repo.close();});if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}ReaderImages.shared.remove(image);new ImageDiskCache(new File(test.getTargetContext().getCacheDir(),"reader-images-v1")).removeUrl(image);test.getTargetContext().deleteDatabase(database);}
        report.append("scope=controlled image transport and real foreground controller; no phone battery claim\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
