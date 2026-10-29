package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.CookieManager;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Actual controller cleanup; synthetic saved/feedback in isolated DB, preserved cookie/reading marker. */
final class ImageCacheClearProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC content; actual controller reading-cache cleanup\n");
    ImageCacheClearProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String key){try{Field f=owner.getClass().getDeclaredField(key);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void cookie(String url,boolean present)throws Exception{CountDownLatch done=new CountDownLatch(1);test.runOnMainSync(()->{CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setCookie(url,"qr_cache_clear="+(present?"preserve":"")+"; Path=/; Secure"+(present?"":"; Max-Age=0"),ok->done.countDown());});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Cookie setup timeout");}
    void run(){DiscoveryActivity activity=null;String database="test-image-clear-"+UUID.randomUUID()+".db",url="https://www.cls.cn/detail/"+System.currentTimeMillis(),image="https://qr-clear-"+System.nanoTime()+".invalid/image.gif";
        boolean acceptedCookies=CookieManager.getInstance().acceptCookie();
        try{
            cookie(image,true);long now=System.currentTimeMillis();HotCandidate candidate=new HotCandidate(Source.CLS,url,"合成缓存清理检查","","",false,1,null,now);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){app.quietreader.discovery.LegacySavedFixture.save(store,"event-image-clear",candidate,now);store.feedback("image-clear-feedback","MORE","sample",candidate.id,now,null,candidate.modelText(),Source.CLS);}
            Document body=new Document();body.url=url;body.title=candidate.title;body.blocks.add(new Block("text","清理副本仍应保留阅读进度。"));ArticleVersion.capture(candidate.readerItem(),body);
            ReadingHistory history=new ReadingHistory(test.getTargetContext());test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(body);repo.close();history.save(url,body,Collections.singleton("synthetic-expanded"),321);});
            ReaderImages acquired=new ReaderImages(u->CookieManager.getInstance().getCookie(u),(u,ref,scoped)->"GIF89a123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII),8192);acquired.attach(test.getTargetContext());acquired.load(image,url);
            File imageRoot=new File(test.getTargetContext().getCacheDir(),"reader-images-v1");check(imageRoot.listFiles()!=null&&imageRoot.listFiles().length>0,"image file exists before user cleanup");String namespace=new ArticleDiskCache(test.getTargetContext()).namespace();
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DiscoveryActivity owner=activity;DiscoveryController controller=(DiscoveryController)field(owner,"controller");
            test.runOnMainSync(controller::clearArticleCache);long until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){boolean[] busy={true};test.runOnMainSync(()->busy[0]=(boolean)field(controller,"clearingBodies"));if(!busy[0])break;Thread.sleep(50);}
            check(!namespace.equals(new ArticleDiskCache(test.getTargetContext()).namespace()),"controller invalidates namespace before clearing derived files");check(imageRoot.listFiles()!=null&&imageRoot.listFiles().length==0,"user reading-cache cleanup physically removes image records");
            org.json.JSONObject marker=history.get(url),raw=new org.json.JSONObject(test.getTargetContext().getSharedPreferences("reading-history",0).getString(url,"{}"));check(marker!=null&&marker.optInt("position")==321&&!raw.has("document")&&marker.getJSONArray("expanded").getString(0).equals("synthetic-expanded"),"reading progress/expanded marker preserved while body copy removed");
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){check(app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.activeFeedback(now).length()==1,"saved item and interest feedback survive reading-cache cleanup");}
            check(String.valueOf(CookieManager.getInstance().getCookie(image)).contains("qr_cache_clear=preserve"),"platform cookie preserved by reading-cache cleanup");
            acquired.evictAll();ReaderImages unavailable=new ReaderImages(u->CookieManager.getInstance().getCookie(u),(u,ref,scoped)->{throw new IOException("offline");},8192);unavailable.attach(test.getTargetContext());boolean miss=false;try{unavailable.load(image,url);}catch(IOException expected){miss=true;}check(miss,"cleared image cannot be resurrected from old disk namespace");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}
        finally{DiscoveryActivity owner=activity;if(owner!=null)test.runOnMainSync(owner::finish);test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();try{cookie(image,false);}catch(Exception e){failures++;}test.runOnMainSync(()->CookieManager.getInstance().setAcceptCookie(acceptedCookies));}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
