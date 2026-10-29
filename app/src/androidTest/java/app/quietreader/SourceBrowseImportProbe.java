package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.Button;
import app.quietreader.discovery.HotCandidate;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import static app.quietreader.Models.*;

/** Actual Discovery -> source browse -> snapshot -> reader result; synthetic owned source pages. */
final class SourceBrowseImportProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC MANUAL SOURCE IMPORT; real activities, WebView, result and cache, no real account certification\n");
    SourceBrowseImportProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void open(DiscoveryActivity owner,HotCandidate candidate){try{Method method=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);method.setAccessible(true);method.invoke(owner,candidate);}catch(Exception e){throw new IllegalStateException(e);}}
    private static Button button(View view,String title){if(view instanceof Button&&((Button)view).getText().toString().equals(title))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++){Button found=button(group.getChildAt(i),title);if(found!=null)return found;}}return null;}
    private void tap(LoginActivity page,String title){
        int[] center=new int[2];boolean[] visible={false};ui(()->{Button control=button(page.getWindow().getDecorView(),title);if(control==null)return;android.graphics.Rect rect=new android.graphics.Rect();int[] location=new int[2];control.getLocationOnScreen(location);center[0]=location[0]+control.getWidth()/2;center[1]=location[1]+control.getHeight()/2;visible[0]=control.isShown()&&control.getGlobalVisibleRect(rect)&&rect.contains(center[0],center[1]);});
        check(visible[0],title+": native control center is visible and reachable");if(!visible[0])throw new IllegalStateException("Source control clipped");
        long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,center[0],center[1],0),up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,center[0],center[1],0);
        try{if(!test.getUiAutomation().injectInputEvent(down,true)||!test.getUiAutomation().injectInputEvent(up,true))throw new IllegalStateException("Native tap injection failed");}finally{down.recycle();up.recycle();}
    }
    private void screenshot(String name)throws Exception{android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IllegalStateException("Screenshot unavailable");try(FileOutputStream output=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10_000;while(SystemClock.elapsedRealtime()<end){boolean[] good={false};ui(()->good[0]=condition.getAsBoolean());if(good[0])return;Thread.sleep(50);}throw new IllegalStateException("Manual import timeout");}
    private DiscoveryActivity recreate(DiscoveryActivity old)throws Exception{
        DiscoveryActivity[] created={null};CountDownLatch ready=new CountDownLatch(1);Application app=old.getApplication();
        Application.ActivityLifecycleCallbacks listener=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity activity,Bundle state){if(activity instanceof DiscoveryActivity&&activity!=old){created[0]=(DiscoveryActivity)activity;ready.countDown();}}
            public void onActivityStarted(Activity activity){} public void onActivityResumed(Activity activity){}
            public void onActivityPaused(Activity activity){} public void onActivityStopped(Activity activity){}
            public void onActivitySaveInstanceState(Activity activity,Bundle state){} public void onActivityDestroyed(Activity activity){}
        };
        ui(()->{app.registerActivityLifecycleCallbacks(listener);old.recreate();});
        try{if(!ready.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Discovery recreation timeout");test.waitForIdleSync();return created[0];}
        finally{ui(()->app.unregisterActivityLifecycleCallbacks(listener));}
    }
    private String fixture(Source source,String url,String scenario)throws Exception{
        String prose="这是眼前来源页主动导入的合成中文正文。".repeat(25),state;
        if(source==Source.XHS){String id=XhsArticle.id(url);JSONObject note=new JSONObject().put("noteId",id).put("type","normal").put("title","合成笔记标题").put("desc",prose).put("user",new JSONObject().put("nickname","合成作者"));
            if(scenario.equals("restricted"))note.put("needLogin",true);
            state="window.__INITIAL_STATE__="+new JSONObject().put("note",new JSONObject().put("currentNoteId",id).put("noteDetailMap",new JSONObject().put(id,new JSONObject().put("note",note))))+";";
        }else{String id=java.net.URI.create(url).getPath().split("/")[2];JSONObject owner=new JSONObject("{aweme:{statusCode:0,detail:{awemeId:'456',status:{privateStatus:0},authorInfo:{nickname:'合成作者'}}},comment:{statusCode:0,comments:[]}}").put("hotId",id);owner.getJSONObject("aweme").getJSONObject("detail").put("desc",prose);
            state="self.__pace_f=[];self.__pace_f.push("+new JSONArray().put(1).put("7:"+new JSONArray().put("$").put("fixture").put(JSONObject.NULL).put(owner)+"\n")+");";
            if(scenario.equals("video-empty")){owner.getJSONObject("aweme").getJSONObject("detail").put("desc","").put("itemTitle","仅有视频信息的合成条目").put("video",new JSONObject());state="self.__pace_f=[];self.__pace_f.push("+new JSONArray().put(1).put("7:"+new JSONArray().put("$").put("fixture").put(JSONObject.NULL).put(owner)+"\n")+");";}
        }
        return "<!doctype html><meta charset=utf-8><body><p id=fixture>fixture-ready</p><script>"+state+"</script></body>";
    }
    void run(){
        String database="test-ui-source-import-"+UUID.randomUUID()+".db";DiscoveryActivity discovery=null;LoginActivity login=null;MainActivity reader=null;List<String> urls=new ArrayList<>();
        try{
            for(String scenario:List.of("douyin","xhs","recreated","navigated","video-empty","restricted","cancel")){
                Source source=scenario.equals("xhs")||scenario.equals("navigated")||scenario.equals("restricted")?Source.XHS:Source.DOUYIN;long nonce=System.nanoTime();
                String host=source==Source.XHS?"qr-import-"+nonce+".xiaohongshu.com":"qr-import-"+nonce+".douyin.com";
                String path=source==Source.XHS?"/explore/"+String.format(Locale.ROOT,"%024x",nonce):"/hot/"+nonce+"/title";
                String url="https://"+host+path,target=scenario.equals("navigated")?"https://"+host+"/explore/"+String.format(Locale.ROOT,"%024x",nonce+1):url;urls.add(url);urls.add(target);
                HotCandidate candidate=new HotCandidate(source,url,"原始榜单标题","原始榜单摘要","",false,1,null,System.currentTimeMillis());
                Item unrelated=new Item(Source.HUPU,"已经预取的另一个平台内容","https://qr-other-"+nonce+".hupu.com/123.html","");
                ui(()->{Repository cache=new Repository(test.getTargetContext());Document body=new Document();body.url=unrelated.url;body.blocks.add(new Block("text","其他平台已预取的正文应保留。"));new ArticleSession(test.getTargetContext(),unrelated).stamp(body);cache.cacheArticle(body);cache.close();});
                discovery=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DiscoveryActivity launchHome=discovery;
                Instrumentation.ActivityMonitor loginMonitor=test.addMonitor(LoginActivity.class.getName(),null,false),readerMonitor=test.addMonitor(MainActivity.class.getName(),null,false);
                try{
                    ui(()->{LoginActivity.pendingDocument=new Document();open(launchHome,candidate);});login=(LoginActivity)test.waitForMonitorWithTimeout(loginMonitor,5000);if(login==null)throw new IllegalStateException("Source page missing");LoginActivity page=login;
                    if(scenario.equals("recreated"))discovery=recreate(launchHome);DiscoveryActivity home=discovery;
                    if(scenario.equals("recreated"))ui(()->{HotCandidate restored=(HotCandidate)field(home,"sourceReadCandidate");check(home!=launchHome&&launchHome.isDestroyed()&&restored!=null&&restored.id.equals(candidate.id)&&restored.textHash.equals(candidate.textHash),"recreated: real activity replacement restores pending candidate identity and metadata");});
                    check(!source.unifiedReading(),scenario+": default automatic source migration remains gated");
                    ui(()->{check((boolean)field(page,"browse")&&button(page.getWindow().getDecorView(),"读取当前页")!=null,scenario+": source browser exposes explicit import");check(LoginActivity.pendingDocument==null,scenario+": opening clears any unrelated pending result");});
                    if(scenario.equals("cancel")){
                        tap(page,"返回发现");await(()->page.isFinishing()&&field(home,"sourceReadCandidate")==null);
                        check(readerMonitor.getHits()==0&&LoginActivity.pendingDocument==null,"cancel: returns to discovery without importing or opening a reader");
                        ui(()->{Repository cache=new Repository(test.getTargetContext());try{check(cache.cachedArticle(unrelated)!=null,"cancel: unrelated prefetched content stays available");}finally{cache.close();}});
                    }else{
                        String html=fixture(source,target,scenario);AtomicInteger requests=new AtomicInteger();
                        ui(()->{WebView web=(WebView)field(page,"web");web.stopLoading();WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(request.isForMainFrame())requests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                            @Override public void onPageStarted(WebView view,String loaded,android.graphics.Bitmap bitmap){delegate.onPageStarted(view,loaded,bitmap);}
                            @Override public void onPageFinished(WebView view,String loaded){delegate.onPageFinished(view,loaded);}
                        });web.loadUrl(target);});
                        long end=SystemClock.elapsedRealtime()+8000;boolean ready=false;
                        while(SystemClock.elapsedRealtime()<end){CountDownLatch sampled=new CountDownLatch(1);boolean[] dom={false};ui(()->((WebView)field(page,"web")).evaluateJavascript("document.readyState==='complete'&&!!document.getElementById('fixture')",value->{dom[0]="true".equals(value);sampled.countDown();}));if(sampled.await(2,TimeUnit.SECONDS)&&dom[0]){ready=true;break;}Thread.sleep(50);}
                        if(!ready)throw new IllegalStateException("Synthetic source DOM missing");
                        if(scenario.equals("douyin"))screenshot("source-import-toolbar.png");tap(page,"读取当前页");
                        if(scenario.equals("restricted")){
                            await(()->((android.widget.TextView)field(page,"status")).getText().toString().contains("登录"));
                            check(!page.isFinishing()&&LoginActivity.pendingDocument==null&&readerMonitor.getHits()==0,"restricted: login-required snapshot stays on source page without importing an error as content");continue;
                        }
                        reader=(MainActivity)test.waitForMonitorWithTimeout(readerMonitor,8000);if(reader==null)throw new IllegalStateException("Imported reader missing");MainActivity shown=reader;
                        await(()->field(shown,"readerWeb")!=null&&field(shown,"reading")!=null);int requestCount=requests.get();
                        ui(()->{Document doc=(Document)field(shown,"reading");Item current=(Item)field(shown,"current");Repository cache=(Repository)field(shown,"repo");
                            check(target.equals(doc.url)&&(scenario.equals("video-empty")?doc.unsupportedVideo&&!doc.hasContent():doc.blocks.stream().anyMatch(b->b.value.contains("眼前来源页主动导入"))),scenario+": exact displayed content reaches common reader");
                            check(field(shown,"dynamic")==null&&cache.cachedArticle(current)==doc,scenario+": imported body renders from validated cache without another source load");
                            check(loginMonitor.getHits()==1,scenario+": manual import does not reopen the raw source page");
                            check(doc.cachedNamespace.equals(new ArticleDiskCache(shown).namespace())&&doc.cachedSession.equals(ArticleDiskCache.session(target)),scenario+": provenance survives source-return invalidation");
                            check(cache.cachedArticle(unrelated)!=null,scenario+": manual import preserves unrelated platform prefetches");
                            check(scenario.equals("navigated")?field(shown,"discoveryCandidate")==null:ArticleVersion.matches(candidate.readerItem(),doc)&&field(shown,"discoveryCandidate")!=null,scenario+": navigation cannot attribute new content to original topic");
                            check(LoginActivity.pendingDocument==null&&field(home,"sourceReadCandidate")==null,scenario+": result is consumed once");
                        });Thread.sleep(500);check(requests.get()==requestCount,scenario+": no repeated source request after import");if(scenario.equals("douyin"))screenshot("source-import-reader.png");
                        if(scenario.equals("video-empty")){
                            Field writer=ArticleDiskCache.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);
                            CountDownLatch restored=new CountDownLatch(1);Document[] disk={null};ui(()->{Repository cache=(Repository)field(shown,"repo");cache.releaseArticleMemory();cache.restoreArticle((Item)field(shown,"current"),doc->{disk[0]=doc;restored.countDown();});});
                            check(restored.await(5,TimeUnit.SECONDS)&&disk[0]!=null&&disk[0].unsupportedVideo&&!disk[0].hasContent(),"video-empty: metadata-only fallback restores from real disk without fetching video bytes");
                        }
                    }
                }finally{test.removeMonitor(loginMonitor);test.removeMonitor(readerMonitor);MainActivity endReader=reader;LoginActivity endLogin=login;DiscoveryActivity endHome=discovery;ui(()->{if(endReader!=null)endReader.finish();if(endLogin!=null)endLogin.finish();endHome.finish();});test.waitForIdleSync();reader=null;login=null;discovery=null;}
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{MainActivity endReader=reader;LoginActivity endLogin=login;DiscoveryActivity endDiscovery=discovery;ui(()->{LoginActivity.pendingDocument=null;if(endReader!=null)endReader.finish();if(endLogin!=null)endLogin.finish();if(endDiscovery!=null)endDiscovery.finish();});test.waitForIdleSync();for(String url:urls)test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
