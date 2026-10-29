package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Controlled source HTML, real parser purposes/cache/default Activity entry; no live-source claim. */
final class ExplicitVideoFlowProbe {
    private final Instrumentation test;private final boolean multiple;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC VIDEO TOPIC; real WebView/parser/session/cache/production entry; recommendation filter remains enabled\n");
    ExplicitVideoFlowProbe(Instrumentation test){this(test,false);}
    ExplicitVideoFlowProbe(Instrumentation test,boolean multiple){this.test=test;this.multiple=multiple;}
    private String evaluate(MainActivity activity,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={""};
        test.runOnMainSync(()->{try{WebView web=(WebView)field(activity,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(false);done.countDown();});}catch(Exception e){throw new IllegalStateException(e);}});
        if(!done.await(4,TimeUnit.SECONDS))throw new IOException("DOM callback timeout");return result[0];
    }
    private static Object field(Object owner,String name)throws Exception{Class<?> type=owner instanceof Class?(Class<?>)owner:owner.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(java.util.concurrent.Callable<Boolean> condition,long ms)throws Exception{long until=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<until){boolean[] good={false};test.runOnMainSync(()->{try{good[0]=condition.call();}catch(Exception e){throw new IllegalStateException(e);}});if(good[0])return true;Thread.sleep(100);}return false;}
    void run(){
        DiscoveryActivity home=null;MainActivity reader=null;DynamicReader[] acquisition={null};Repository[] repo={null};List<Item> items=new ArrayList<>();String database="test-ui-"+UUID.randomUUID()+".db";Instrumentation.ActivityMonitor monitor=null,login=null;
        try{
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DiscoveryActivity activity=home;
            check(await(()->(boolean)field(field(activity,"controller"),"ready"),7000),"discovery controller ready without source fetching");
            Document reading=null;
            for(boolean explicit:new boolean[]{false,true}){
                String url="https://bbs.hupu.com/"+(System.currentTimeMillis())+".html";
                Item item=new Item(Source.HUPU,"合成视频主题",url,"",true);items.add(item);
                String html="<html><head><meta charset='utf-8'></head><body><h1>合成视频主题</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail>这是视频主帖的原作者说明。</div><video preload=none src='https://v.hupucdn.com/fixture.mp4' poster='https://v.hupucdn.com/fixture.jpg'></video>"+(multiple?"<video preload=none src='https://v.hupucdn.com/second.mp4' poster='https://v.hupucdn.com/second.jpg'></video>":"")+"</div><div class=reply-list-wrapper><div class=reply-list-item><div class=thread-content-detail>这是本页已加载的普通回复。</div></div></div></body></html>";
                CountDownLatch done=new CountDownLatch(1);Document[] accepted={null};String[] error={""};
                test.runOnMainSync(()->{try{
                    acquisition[0]=new DynamicReader(activity,item,new Repository.Result<Document>(){public void success(Document d){accepted[0]=d;done.countDown();}public void failure(String why){error[0]=why;done.countDown();}},explicit);
                    WebView web=(WebView)field(acquisition[0],"web");WebViewClient delegate=web.getWebViewClient();
                    web.setWebViewClient(new WebViewClient(){
                        @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                        @Override public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}
                        @Override public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}
                    });
                }catch(Exception e){throw new IllegalStateException(e);}});
                check(done.await(12,TimeUnit.SECONDS)&&accepted[0]!=null,"bounded source extraction explicit="+explicit+" "+error[0]);if(accepted[0]==null)throw new IllegalStateException("No accepted video result");
                Document doc=accepted[0];
                if(!explicit)check(doc.filteredVideo&&!doc.hasContent(),"speculative extraction still filters whole video topic");
                else{
                    check(doc.videoTopic&&doc.unsupportedVideo&&!doc.filteredVideo&&doc.sections.size()==2,"explicit extraction keeps source description and discussion");
                    check(!doc.videoPoster.isEmpty()&&!doc.videoUrl.isEmpty(),"sibling main video owns its observed poster and file");
                    check(doc.cachedSession!=null&&doc.cachedSession.equals(ArticleDiskCache.session(item.url)),"explicit result has actual session stamp");reading=doc;
                }
                test.runOnMainSync(()->{acquisition[0].close();acquisition[0]=null;});
            }
            Item item=items.get(1);Document doc=reading;HotCandidate candidate=new HotCandidate(item.source,item.url,item.title,"","",true,1,null,System.currentTimeMillis());
            check(CandidateOrder.arrange(List.of(candidate),new org.json.JSONArray(),false).isEmpty(),"known video remains excluded from personalized recommendations");
            test.runOnMainSync(()->{try{
                repo[0]=new Repository(activity);repo[0].rememberVideo(item);check(repo[0].hiddenVideo(item)&&repo[0].visibleItems(List.of(item)).isEmpty(),"source board filtering remains enabled for known video");repo[0].cacheArticle(doc);check(repo[0].cachedArticle(item)==doc,"explicit body is reusable in shared session-bound cache");
                ReaderImages images=(ReaderImages)field(MainActivity.class,"images");images.attach(activity);
                int color=0xff87a9bd;for(VideoAttachment attachment:ReaderMedia.attachments(doc)){android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(320,180,android.graphics.Bitmap.Config.ARGB_8888);image.eraseColor(color);color=0xffbc9c80;ByteArrayOutputStream bytes=new ByteArrayOutputStream();image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bytes);image.recycle();images.put(attachment.poster,doc.url,bytes.toByteArray());}
            }catch(Exception e){throw new IllegalStateException(e);}});
            monitor=test.addMonitor(MainActivity.class.getName(),null,false);login=test.addMonitor(LoginActivity.class.getName(),null,false);
            test.runOnMainSync(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(activity,candidate);}catch(Exception e){throw new IllegalStateException(e);}});
            reader=(MainActivity)test.waitForMonitorWithTimeout(monitor,5000);check(reader!=null&&login.getHits()==0,"explicit production entry reaches common reader despite video filter");if(reader==null)throw new IllegalStateException("Reader absent");MainActivity shown=reader;
            check(await(()->field(shown,"readerWeb")!=null,5000),"video fallback remains visible rather than returning to board");
            test.runOnMainSync(()->{try{
                Document active=(Document)field(shown,"reading");String html=(String)field(shown,"readerHtml");check(active!=null&&active.videoTopic&&active.url.equals(item.url),"reader retains exact video topic identity");
                check(html.contains("data-reader-layout='video'")&&html.contains("原作者说明")&&html.contains("普通回复"),"common card retains description and discussion");
                check(field(shown,"dynamic")==null&&field(shown,"videoPreview")==null,"cache hit neither reloads source nor starts a player");
                check(repo[0].hiddenVideo(item),"explicit reading does not clear default video exclusion");
            }catch(Exception e){throw new IllegalStateException(e);}});
            boolean painted=false;long deadline=SystemClock.elapsedRealtime()+10000;
            while(!painted&&SystemClock.elapsedRealtime()<deadline){
                CountDownLatch evaluated=new CountDownLatch(1);String[] state={""};
                test.runOnMainSync(()->{try{WebView web=(WebView)field(shown,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("(function(){var t=document.body?document.body.innerText:'';var p=document.querySelector('img');return t.includes('原作者说明')&&t.includes('普通回复')&&!!p&&p.complete&&p.naturalWidth>0&&document.querySelectorAll('video,iframe').length===0;})()",value->{state[0]=value;web.getSettings().setJavaScriptEnabled(false);evaluated.countDown();});}catch(Exception e){throw new IllegalStateException(e);}});
                painted=evaluated.await(3,TimeUnit.SECONDS)&&"true".equals(state[0]);if(!painted)Thread.sleep(150);
            }
            check(painted,"actual DOM contains source prose, discussion and decoded poster without embedded player");
            CountDownLatch visual=new CountDownLatch(1);
            test.runOnMainSync(()->{try{WebView web=(WebView)field(shown,"readerWeb");web.postVisualStateCallback(1,new WebView.VisualStateCallback(){@Override public void onComplete(long id){visual.countDown();}});}catch(Exception e){throw new IllegalStateException(e);}});
            check(visual.await(5,TimeUnit.SECONDS),"common reader compositor completed before visual inspection");
            if(multiple){
                check("true".equals(evaluate(shown,"(function(){var c=document.querySelectorAll('.media-fallback');return c.length===2&&c[0].querySelector('img').src.endsWith('/fixture.jpg')&&c[1].querySelector('img').src.endsWith('/second.jpg')&&c[1].querySelector('a.media-play').href.endsWith('play?index=1');})()")),"each actual media card retains its matching observed poster and action");
                evaluate(shown,"document.querySelectorAll('.media-play')[1].click()");
                check(await(()->field(shown,"videoPreview")!=null,3000),"second media card opens production common player");Object previous=field(shown,"videoPreview");
                evaluate(shown,"(function(){var a=document.createElement('a');a.href='https://quiet-reader.invalid/play?index=99';document.body.append(a);a.click();a.remove();})()");
                test.runOnMainSync(()->{try{check(field(shown,"videoPreview")==previous,"invalid attachment action does not replace active player");}catch(Exception e){throw new IllegalStateException(e);}});
                evaluate(shown,"document.querySelectorAll('.media-play')[0].click()");
                check(await(()->field(shown,"videoPreview")!=previous,3000),"choosing another attachment replaces the player");
                test.runOnMainSync(()->{try{check((boolean)field(previous,"closed")&&field(previous,"current")==null,"previous decoder is synchronously released on attachment switch");Method close=MainActivity.class.getDeclaredMethod("closeVideo");close.setAccessible(true);close.invoke(shown);check(field(shown,"reading")==doc&&doc.videos.size()==2,"playing another attachment does not mutate shared cached document");}catch(Exception e){throw new IllegalStateException(e);}});
            }
            test.runOnMainSync(()->{try{WebView web=(WebView)field(shown,"readerWeb");android.view.View root=(android.view.View)field(shown,"root");report.append("LAYOUT web=").append(web.getWidth()).append('x').append(web.getHeight()).append(" shown=").append(web.isShown()).append(" root=").append(root.getWidth()).append('x').append(root.getHeight()).append(" focus=").append(shown.hasWindowFocus()).append('\n');check(web.isShown()&&web.getHeight()>100,"reader occupies visible screen area");}catch(Exception e){throw new IllegalStateException(e);}});
            test.waitForIdleSync();Thread.sleep(1500);android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot!=null){File folder=new File(test.getTargetContext().getExternalFilesDir(null),"explicit-video-reading");folder.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(folder,"fallback.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{MainActivity end=reader;DiscoveryActivity start=home;if(monitor!=null)test.removeMonitor(monitor);if(login!=null)test.removeMonitor(login);test.runOnMainSync(()->{if(acquisition[0]!=null)acquisition[0].close();if(repo[0]!=null)repo[0].close();if(end!=null)end.finish();if(start!=null)start.finish();});test.waitForIdleSync();android.content.SharedPreferences.Editor videos=test.getTargetContext().getSharedPreferences("video-filter",0).edit(),history=test.getTargetContext().getSharedPreferences("reading-history",0).edit();for(Item item:items){videos.remove(VideoPolicy.key(item));history.remove(item.url);}videos.commit();history.commit();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
