package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Public captured metadata through the real component chain; not live source acceptance. */
final class XhsReaderProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("CAPTURED PUBLIC XHS METADATA; intercepted source HTML, synthetic comments and image bytes; not live-source/default-entry acceptance\n");
    XhsReaderProbe(Instrumentation test){this.test=test;}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static void call(Object owner,String name,Class<?>[] types,Object...args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,args);}
    private void check(boolean good,String message){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(message).append('\n');}
    void run(){
        MainActivity activity=null;DynamicReader[] reader={null};java.util.List<String> urls=new java.util.ArrayList<>();
        try{
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity home=activity;
            test.runOnMainSync(()->{try{((Repository)field(home,"repo")).cancelPending();((ArticlePreloader)field(home,"preloader")).pause();((Handler)field(home,"preloadHandler")).removeCallbacksAndMessages(null);DynamicBoard board=(DynamicBoard)field(home,"dynamicBoard");if(board!=null)board.close();}catch(Exception e){throw new IllegalStateException(e);}});
            for(boolean video:new boolean[]{false,true}){
                JSONObject state;try(InputStream in=test.getContext().getAssets().open("fixtures/xhs-"+(video?"note":"image-note")+"-data-20261024.json")){state=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                JSONObject map=state.getJSONObject("note").getJSONObject("noteDetailMap");String id=map.keys().next();
                JSONObject container=map.getJSONObject(id);container.getJSONObject("comments").put("list",new JSONArray("[{id:'fixture-1',content:'合成评论内容',userInfo:{nickname:'合成评论作者'}}]"));
                // undefined is present in real source state. Exercise Android JSONTokener,
                // rather than only the different JVM org.json dependency.
                String html="<html><head><meta charset='utf-8'></head><body><script>window.__INITIAL_STATE__={unused:undefined,note:"+state.getJSONObject("note")+"}</script><p>推荐广告不能当正文</p></body></html>";
                Item item=new Item(Source.XHS,"公开裁剪样本","https://www.xiaohongshu.com/explore/"+id+"?probe="+System.nanoTime(),"",video);urls.add(item.url);
                CountDownLatch done=new CountDownLatch(1);Document[] accepted={null};String[] error={""};AtomicInteger callbacks=new AtomicInteger(),mediaRequests=new AtomicInteger();
                test.runOnMainSync(()->{try{
                    reader[0]=new DynamicReader(home,item,new Repository.Result<Document>(){public void success(Document doc){accepted[0]=doc;callbacks.incrementAndGet();done.countDown();}public void failure(String why){error[0]=why;callbacks.incrementAndGet();done.countDown();}});
                    WebView web=(WebView)field(reader[0],"web");WebViewClient delegate=web.getWebViewClient();
                    web.setWebViewClient(new WebViewClient(){
                        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String path=request.getUrl().getPath();if(path!=null&&path.matches("(?i).*\\.(mp4|m4s|webm|m3u8|ts)$"))mediaRequests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                        @Override public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}
                        @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}
                    });
                }catch(Exception e){throw new IllegalStateException(e);}});
                check(done.await(15,TimeUnit.SECONDS),"bounded extraction video="+video);
                check(callbacks.get()==1&&accepted[0]!=null,"single normalized document: "+error[0]);
                if(accepted[0]==null)throw new IllegalStateException("No accepted XHS document");Document doc=accepted[0];
                check(doc.sections.size()==2&&doc.sections.get(1).label.equals("评论 · 合成评论作者"),"owned body and synthetic comment author remain separate");
                check(doc.unsupportedVideo==video&&(video?!doc.videoPoster.isEmpty():doc.blocks.stream().anyMatch(b->b.type.equals("image"))),"correct shared article/video media representation");
                check(doc.cachedSession!=null&&doc.cachedSession.equals(ArticleDiskCache.session(item.url)),"acquisition stamps actual session");
                check(mediaRequests.get()==0,"extraction makes no media-file requests");
                test.runOnMainSync(()->{try{
                    reader[0].close();reader[0]=null;Repository repo=(Repository)field(home,"repo");check(repo.cacheArticle(doc,Repository.articleCacheEpoch())&&repo.cachedArticle(item)==doc,"shared session-bound cache reuses normalized body");
                    // Synthetic image bytes exercise the authorized reader image pipeline;
                    // expiring captured CDN URLs cannot prove remote availability.
                    android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(513,372,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff87a9bd);ByteArrayOutputStream bytes=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
                    ReaderImages images=(ReaderImages)field(home,"images");
                    String image=video?doc.videoPoster:doc.blocks.stream().filter(b->b.type.equals("image")).findFirst().get().value;images.put(image,doc.url,bytes.toByteArray());
                    set(home,"current",item);set(home,"primaryPending",true);call(home,"frame",new Class[]{String.class,String.class},"小红书","news");call(home,"scroller",new Class[0]);call(home,"render",new Class[]{Document.class},doc);
                    String rendered=(String)field(home,"readerHtml");check(rendered.contains("data-reader-layout='"+(video?"video":"discussion")+"'")&&rendered.contains("discussion-heading'>讨论")&&!rendered.contains("推荐广告"),"common layout retains owned body and discussion");
                    check(!rendered.contains("<video")&&!rendered.contains("<iframe")&&!rendered.contains("<script"),"no source player or script in display");
                }catch(Exception e){throw new IllegalStateException(e);}});
                long until=SystemClock.elapsedRealtime()+7000;boolean[] loaded={false};
                while(SystemClock.elapsedRealtime()<until){CountDownLatch sampled=new CountDownLatch(1);test.runOnMainSync(()->{try{WebView web=(WebView)field(home,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("!!document.querySelector('img')&&document.querySelector('img').naturalWidth>0&&document.documentElement.scrollWidth<=innerWidth+1",value->{loaded[0]="true".equals(value);web.getSettings().setJavaScriptEnabled(false);sampled.countDown();});}catch(Exception e){throw new IllegalStateException(e);}});if(!sampled.await(2,TimeUnit.SECONDS))break;if(loaded[0])break;Thread.sleep(100);}
                check(loaded[0],"authorized synthetic image decodes without horizontal overflow video="+video);
                test.waitForIdleSync();Thread.sleep(700); // Decode/DOM readiness precedes the compositor's visible frame.
                android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot!=null){File folder=new File(test.getTargetContext().getExternalFilesDir(null),"xhs-reader-contract");folder.mkdirs();File file=new File(folder,video?"video.png":"discussion.png");try(FileOutputStream out=new FileOutputStream(file)){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}report.append("screenshot=").append(file).append('\n');}
            }
            check(!Source.XHS.unifiedReading(),"default source migration still waits for live acceptance");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{MainActivity end=activity;test.runOnMainSync(()->{if(reader[0]!=null)reader[0].close();if(end!=null)end.finish();});test.waitForIdleSync();android.content.SharedPreferences.Editor history=test.getTargetContext().getSharedPreferences("reading-history",0).edit();for(String url:urls)history.remove(url);history.commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
