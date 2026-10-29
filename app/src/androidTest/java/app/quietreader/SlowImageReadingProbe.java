package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real WebView visual commit with a gated image response, no external image request. */
final class SlowImageReadingProbe {
    private final Instrumentation test;private MainActivity activity;private int checks,failures;
    private final StringBuilder report=new StringBuilder();private final CountDownLatch imageRequested=new CountDownLatch(1),releaseImage=new CountDownLatch(1);
    private final boolean imageOnly;
    private volatile int commits,finishes;private volatile boolean released;
    SlowImageReadingProbe(Instrumentation test){this(test,false);}
    SlowImageReadingProbe(Instrumentation test,boolean imageOnly){this.test=test;this.imageOnly=imageOnly;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String label,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Slow-image deadline: "+label);Thread.sleep(30);}}
    void run(){String database="test-ui-slow-image-"+UUID.randomUUID()+".db",image="https://image.example.invalid/gated.png";
        HotCandidate candidate=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+System.nanoTime(),"慢图片不应阻挡文字阅读学习","","",false,1,null,System.currentTimeMillis());
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            ui(()->{Repository repo=new Repository(test.getTargetContext());Document doc=new Document();doc.url=candidate.originalUrl;doc.title=candidate.title;for(int n=0;n<70;n++)doc.blocks.add(new Block("text","这是验证可见文字阅读的合成段落，不代表真实兴趣。第"+n+"段。"));ArticleVersion.capture(candidate.readerItem(),doc);repo.cacheArticle(doc);repo.close();});
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",candidate.readerItem().json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("initial reader",()->field(activity,"readerWeb")!=null&&(boolean)field(field(activity,"engagement"),"ready"));
            ui(()->{WebView web=(WebView)field(activity,"readerWeb");WebViewClient production=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
                        if(req.getUrl().toString().equals(image)){imageRequested.countDown();return new WebResourceResponse("image/png",null,new InputStream(){public int read()throws IOException{try{if(!releaseImage.await(45,TimeUnit.SECONDS))throw new IOException("Fixture image deadline");released=true;return -1;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}}});}
                        return production.shouldInterceptRequest(view,req);
                    }
                    @Override public void onPageCommitVisible(WebView view,String url){commits++;production.onPageCommitVisible(view,url);}
                    @Override public void onPageFinished(WebView view,String url){finishes++;production.onPageFinished(view,url);}
                    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req){return production.shouldOverrideUrlLoading(view,req);}
                });
                Document doc=(Document)field(activity,"reading");if(imageOnly){doc.blocks.clear();doc.sections.clear();doc.blocks.add(new Block("image",image));}else doc.blocks.add(1,new Block("image",image));((ReadingEngagement)field(activity,"engagement")).reset(SystemClock.elapsedRealtime());
                try{Method reload=MainActivity.class.getDeclaredMethod("reloadReader",Document.class);reload.setAccessible(true);reload.invoke(activity,doc);}catch(Exception e){throw new IllegalStateException(e);}
            });
            check(imageRequested.await(8,TimeUnit.SECONDS),"real reader requests controlled image while text is locally available");await("actual visual commit",()->commits>0);
            // Observe a completed compositor update before asking whether text is eligible.
            CountDownLatch visual=new CountDownLatch(1);ui(()->((WebView)field(activity,"readerWeb")).postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){visual.countDown();}}));check(visual.await(5,TimeUnit.SECONDS),"actual WebView visual frame completes while image response remains blocked");
            check(onUi(()->(boolean)field(field(activity,"engagement"),"ready")==!imageOnly&&finishes==0&&!released),imageOnly?"image-only placeholder does not become reading-ready on visual commit":"visible text becomes reading-ready before slow image and page completion");
            check(store.activeFeedback(System.currentTimeMillis()).length()==0,"visible text and image loading alone create no interest event");
            if(!imageOnly){await("scroll restoration guard",()->System.currentTimeMillis()>(long)field(activity,"suppressAutoUntil"));
            ui(()->{activity.onUserInteraction();((WebView)field(activity,"readerWeb")).scrollTo(0,400);});
            await("qualified manual scroll",()->(boolean)field(field(activity,"engagement"),"scrolled"));
            check(true,"actual scroll is qualified after automatic restoration suppression ends");Thread.sleep(20_100);
            check(!released&&finishes==0,"image stays pending for the whole qualified foreground reading interval");ui(activity::finish);
            long end=SystemClock.elapsedRealtime()+5000;int count;do{count=store.activeFeedback(System.currentTimeMillis()).length();if(count>0)break;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<end);
            check(count==1&&"READ".equals(store.activeFeedback(System.currentTimeMillis()).optJSONObject(0)==null?"":store.activeFeedback(System.currentTimeMillis()).optJSONObject(0).optString("action")),"reading visible text for twenty seconds and scrolling records one READ despite pending image");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{releaseImage.countDown();ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
