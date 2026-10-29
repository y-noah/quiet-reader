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

/** Component contract only: synthetic Flight data; default Douyin routing remains unmigrated. */
final class DouyinReaderProbe {
    private final boolean activityCookie;
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC DOUYIN CONTRACT; real DynamicReader/parser/session/cache/renderer, not live-source or default-entry acceptance\n");
    DouyinReaderProbe(Instrumentation test){this(test,false);}
    DouyinReaderProbe(Instrumentation test,boolean activityCookie){this.test=test;this.activityCookie=activityCookie;}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private static void call(Object owner,String name,Class<?>[] types,Object...args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,args);}
    private void check(boolean good,String message){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(message).append('\n');}
    void run(){
        MainActivity activity=null;DynamicReader[] reader={null};String id=String.valueOf(System.currentTimeMillis()),url="https://"+(activityCookie?"qr-activity-"+id+".douyin.com":"www.douyin.com")+"/hot/"+id+"/%E5%90%88%E6%88%90%E8%AF%9D%E9%A2%98";
        try{
            Item item=new Item(Source.DOUYIN,"合成来源说明",url,"",true);
            JSONObject owner=new JSONObject("{aweme:{statusCode:0,detail:{awemeId:'456',desc:'合成视频说明：保留图文与讨论',status:{privateStatus:0},video:{},authorInfo:{nickname:'合成作者'}}},comment:{statusCode:0,comments:[{cid:'1',text:'合成评论甲',user:{nickname:'甲'}},{cid:'2',text:'合成评论乙',user:{nickname:'乙'}}]}}").put("hotId",id);
            String stream="7:"+new JSONArray().put("$").put("fixture").put(JSONObject.NULL).put(owner)+"\n";int at=stream.length()/2;
            String html="<html><head><meta charset='utf-8'></head><body><script>self.__pace_f=[];self.__pace_f.push("+new JSONArray().put(1).put(stream.substring(0,at))+" );</script><script>self.__pace_f.push("+new JSONArray().put(1).put(stream.substring(at))+" );</script></body></html>";
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity home=activity;
            CountDownLatch done=new CountDownLatch(1);Document[] accepted={null};String[] error={""};AtomicInteger callbacks=new AtomicInteger(),mediaRequests=new AtomicInteger();java.util.Set<String> otherPaths=java.util.Collections.synchronizedSet(new java.util.TreeSet<>());
            test.runOnMainSync(()->{try{
                ((Repository)field(home,"repo")).cancelPending();((ArticlePreloader)field(home,"preloader")).pause();((Handler)field(home,"preloadHandler")).removeCallbacksAndMessages(null);
                DynamicBoard board=(DynamicBoard)field(home,"dynamicBoard");if(board!=null)board.close();
                if(activityCookie){CookieManager.getInstance().setCookie(url,"qr_account=first; Path=/; Secure");CookieManager.getInstance().setCookie(url,"IsDouyinActive=false; Path=/; Secure");CookieManager.getInstance().setCookie(url,"stream_recommend_feed_params=before; Path=/; Secure");}
                reader[0]=new DynamicReader(home,item,new Repository.Result<Document>(){public void success(Document doc){accepted[0]=doc;callbacks.incrementAndGet();done.countDown();}public void failure(String why){error[0]=why;callbacks.incrementAndGet();done.countDown();}});
                if(activityCookie){CookieManager.getInstance().setCookie(url,"IsDouyinActive=true; Path=/; Secure");CookieManager.getInstance().setCookie(url,"stream_recommend_feed_params=after; Path=/; Secure");}
                WebView web=(WebView)field(reader[0],"web");WebViewClient delegate=web.getWebViewClient();
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(!request.isForMainFrame()){String path=request.getUrl().getPath();if(path!=null&&(path.matches("(?i).*\\.(mp4|m4s|webm|m3u8|mp3|m4a|aac|ts)$")||path.contains("/aweme/v1/play")))mediaRequests.incrementAndGet();else otherPaths.add(path==null?"":path);}return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}
                    @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}
                });
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(done.await(15,TimeUnit.SECONDS),"dynamic extraction finishes within bounded wait");
            check(callbacks.get()==1&&accepted[0]!=null,"one successful normalized document: "+error[0]);
            if(accepted[0]==null)throw new IllegalStateException("No accepted document");Document doc=accepted[0];
            check(doc.sections.size()==3&&doc.sections.get(1).label.equals("评论 · 甲")&&doc.sections.get(2).label.equals("评论 · 乙"),"split source frames retain separate comment authors");
            check(doc.cachedSession!=null&&doc.cachedSession.equals(ArticleDiskCache.session(item.url)),"actual acquisition stamps the current session");
            if(activityCookie)check(field(reader[0],"replacement")==null,"activity/feed context rotation does not restart the real acquisition");
            report.append("nonMediaSubresourcePaths=").append(otherPaths).append('\n');
            check(mediaRequests.get()==0,"synthetic extraction initializes no media-file or playback requests");
            test.runOnMainSync(()->{try{
                Repository repo=(Repository)field(home,"repo");check(repo.cacheArticle(doc,Repository.articleCacheEpoch())&&repo.cachedArticle(item)==doc,"normalized body is reusable in common session-bound cache");
                set(home,"current",item);set(home,"primaryPending",true);call(home,"frame",new Class[]{String.class,String.class},"抖音","news");call(home,"scroller",new Class[0]);call(home,"render",new Class[]{Document.class},doc);
                String rendered=(String)field(home,"readerHtml");
                check(rendered.contains("data-reader-layout='video'")&&rendered.contains("discussion-heading'>讨论")&&rendered.contains("合成视频说明"),"actual common renderer retains video description and discussion");
                check(!rendered.contains("<video")&&!rendered.contains("<iframe")&&!rendered.contains("<script"),"common renderer embeds no source players or scripts");
                if(activityCookie){
                    ArticleSession beforeSwitch=new ArticleSession(home,item);CookieManager.getInstance().setCookie(url,"qr_account=second; Path=/; Secure");
                    Document stale=new Document();stale.url=url;
                    check(!beforeSwitch.stamp(stale),"unknown account cookie change still rejects prior-session stamping");
                    check(repo.cachedArticle(item)==null,"account change still isolates the actual cached body");
                }
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(!Source.DOUYIN.unifiedReading(),"default source entry waits for real positive acceptance");
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{MainActivity end=activity;test.runOnMainSync(()->{if(reader[0]!=null)reader[0].close();if(end!=null)end.finish();if(activityCookie){CookieManager.getInstance().setCookie(url,"qr_account=; Max-Age=0; Path=/; Secure");CookieManager.getInstance().setCookie(url,"IsDouyinActive=; Max-Age=0; Path=/; Secure");CookieManager.getInstance().setCookie(url,"stream_recommend_feed_params=; Max-Age=0; Path=/; Secure");}});test.waitForIdleSync();test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
