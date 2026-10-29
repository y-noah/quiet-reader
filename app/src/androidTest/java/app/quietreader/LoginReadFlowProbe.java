package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Actual login Activity, DOM extraction, result delivery and reader render.
 * All pages and cookies are controlled fixtures, never real account credentials. */
final class LoginReadFlowProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    LoginReadFlowProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private static void call(Object owner,String name,Class<?>[] types,Object... args){try{Method m=owner.getClass().getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(owner,args);}catch(Exception e){throw new IllegalStateException(e);}}
    private void await(java.util.function.BooleanSupplier predicate)throws Exception{long end=SystemClock.elapsedRealtime()+10_000;while(true){boolean[] good={false};ui(()->good[0]=predicate.getAsBoolean());if(good[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Login import timeout");Thread.sleep(50);}}
    private static Button readButton(View view){if(view instanceof Button&&((Button)view).getText().toString().equals("读取当前页"))return (Button)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){Button b=readButton(group.getChildAt(n));if(b!=null)return b;}}return null;}
    private void drain()throws Exception{Field f=ReadingLanguageRecorder.class.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(3,TimeUnit.SECONDS);}
    private void readGesture(MainActivity owner)throws Exception{
        await(()->{WebView web=(WebView)field(owner,"readerWeb");return owner.hasWindowFocus()&&web!=null&&web.getHeight()>0&&web.getContentHeight()*web.getScale()>web.getHeight()+300&&field(owner,"pendingViewport")==null&&!(boolean)field(owner,"viewportRestoreRunning")&&System.currentTimeMillis()>(long)field(owner,"suppressAutoUntil");});
        float[] point=new float[3];ui(()->{WebView web=(WebView)field(owner,"readerWeb");int[] at=new int[2];web.getLocationOnScreen(at);point[0]=at[0]+web.getWidth()*.5f;point[1]=at[1]+web.getHeight()*.75f;point[2]=at[1]+web.getHeight()*.35f;});
        long began=SystemClock.uptimeMillis();
        for(int n=0;n<=13;n++){
            if(n>0)Thread.sleep(25);
            int action=n==0?MotionEvent.ACTION_DOWN:n==13?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;
            MotionEvent event=MotionEvent.obtain(began,SystemClock.uptimeMillis(),action,point[0],point[1]+(point[2]-point[1])*Math.min(12,n)/12,0);
            try{test.sendPointerSync(event);}finally{event.recycle();}
        }
        await(()->(boolean)field(field(owner,"engagement"),"scrolled")&&((WebView)field(owner,"readerWeb")).getScrollY()>0);
    }
    private void awaitDom(LoginActivity page)throws Exception{
        long end=SystemClock.elapsedRealtime()+8000;
        while(SystemClock.elapsedRealtime()<end){CountDownLatch done=new CountDownLatch(1);boolean[] ready={false};ui(()->((WebView)field(page,"web")).evaluateJavascript("document.readyState==='complete'&&document.body&&document.body.innerText.includes('这是登录页面读取后应当直接显示的中文正文')",value->{ready[0]="true".equals(value);done.countDown();}));if(done.await(2,TimeUnit.SECONDS)&&ready[0])return;Thread.sleep(50);}
        throw new IllegalStateException("Controlled DOM is not ready for source extraction");
    }
    String fixture(Source source,String title){String prose="这是登录页面读取后应当直接显示的中文正文，不应再次等待来源加载。".repeat(35);if(source==Source.HUPU){
        try{
            String hidden="<span hidden>隐藏来源辅助内容</span>";
            org.json.JSONObject thread=new org.json.JSONObject().put("tid","123").put("url","/123.html").put("title",title).put("content","<p>"+prose+hidden+"</p>").put("status",0).put("visibleRange","LOGIN_SEE");
            org.json.JSONObject reply=new org.json.JSONObject().put("pid","10").put("content","<p>已登录可见的中文回复"+hidden+"</p>");
            org.json.JSONObject detail=new org.json.JSONObject().put("user",new org.json.JSONObject().put("puid","789")).put("visibleRange","LOGIN_SEE").put("thread",thread).put("lights",new org.json.JSONArray()).put("replies",new org.json.JSONObject().put("current",1).put("list",new org.json.JSONArray().put(reply)));
            org.json.JSONObject state=new org.json.JSONObject().put("props",new org.json.JSONObject().put("pageProps",new org.json.JSONObject().put("detail",detail).put("detailErrorInfo",new org.json.JSONObject().put("code",200))));
            return "<html><head><meta charset=utf-8></head><body><h1>"+title+"</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail>"+prose+hidden+"</div></div><div class=reply-list-wrapper><div class=thread-content-detail>已登录可见的中文回复"+hidden+"</div></div><script id=__NEXT_DATA__ type=application/json>"+state+"</script></body></html>";
        }catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }if(source==Source.TIEBA)return "<html><head><meta charset=utf-8></head><body><h1>"+title+"</h1><div id=j_p_postlist><div class=d_post_content>"+prose+"</div><div class=d_post_content>合成讨论回复</div></div></body></html>";return "<html><head><meta charset=utf-8></head><body><h1>"+title+"</h1><div class=QuestionRichText>"+prose+"</div></body></html>";}
    void run(){
        String database="test-ui-login-read-"+UUID.randomUUID()+".db";MainActivity reader=null;LoginActivity login=null;
        Map<String,?> sessionBefore=test.getTargetContext().getSharedPreferences("source-session",0).getAll();
        try{
            for(String scenario:List.of("matching","navigated","cookie-race","tieba","hupu")){
                Source source=scenario.equals("hupu")?Source.HUPU:scenario.equals("tieba")?Source.TIEBA:Source.ZHIHU;long now=System.currentTimeMillis();
                String url=source==Source.HUPU?"https://qr-login-"+now+".hupu.com/123.html":source==Source.TIEBA?"https://tieba.baidu.com/p/"+now:"https://qr-login-"+now+".zhihu.com/question/123";
                HotCandidate candidate=new HotCandidate(source,url,"原始中文话题标题","原始榜单摘要","",false,1,null,now);
                try(FeedStore store=new FeedStore(test.getTargetContext(),database)){store.upsertSnapshot(source,store.beginRefresh(source),List.of(candidate),now);}
                Document seed=new Document();seed.url=url;seed.title=candidate.title;seed.blocks.add(new Block("text","读取前的合成缓存"));ArticleVersion.capture(candidate.readerItem(),seed);
                ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(seed);repo.close();});
                reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",candidate.readerItem().json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database));
                MainActivity owner=reader;await(()->field(owner,"readerWeb")!=null&&field(owner,"reading")==seed);
                Instrumentation.ActivityMonitor monitor=test.addMonitor(LoginActivity.class.getName(),null,false);
                try{ui(()->call(owner,"login",new Class<?>[]{Item.class},candidate.readerItem()));login=(LoginActivity)test.waitForMonitorWithTimeout(monitor,5000);}finally{test.removeMonitor(monitor);}
                if(login==null)throw new IllegalStateException("Login Activity missing");LoginActivity page=login;
                AtomicInteger requests=new AtomicInteger();CountDownLatch loaded=new CountDownLatch(1);String target=scenario.equals("navigated")?url.replace("/123","/456"):url;
                ui(()->{
                    check(!((Boolean)field(page,"browse"))&&readButton(page.getWindow().getDecorView())!=null,scenario+": unified source offers source snapshot import in sliding login window");
                    check(((Item)field(page,"requestedItem")).snippet.equals(candidate.snippet),scenario+": original metadata is passed before navigation");
                    WebView web=(WebView)field(page,"web");web.stopLoading();WebViewClient delegate=web.getWebViewClient();
                    web.setWebViewClient(new WebViewClient(){
                        @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest request){if(request.isForMainFrame())requests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(fixture(source,"页面实际中文标题").getBytes(StandardCharsets.UTF_8)));}
                        @Override public void onPageStarted(WebView v,String u,android.graphics.Bitmap bitmap){delegate.onPageStarted(v,u,bitmap);}
                        @Override public void onPageFinished(WebView v,String u){delegate.onPageFinished(v,u);if(u.equals(target))loaded.countDown();}
                    });
                    web.loadUrl(target);
                });
                check(loaded.await(8,TimeUnit.SECONDS),scenario+": controlled source DOM is loaded in real WebView");
                // onPageFinished may also arrive for the stopped initial load.
                // Wait for the actual fixture DOM rather than trusting that event.
                awaitDom(page);
                if(scenario.equals("cookie-race")){
                    ui(()->CookieManager.getInstance().setCookie(url,"qr_login_fixture=before; Path=/; Secure"));
                    await(()->{String cookies=CookieManager.getInstance().getCookie(url);return cookies!=null&&cookies.contains("qr_login_fixture=before");});
                }
                ui(()->{
                    if(scenario.equals("cookie-race")){
                        // Hold the controlled renderer briefly so the Cookie IPC
                        // completes before the queued snapshot callback is delivered.
                        ((WebView)field(page,"web")).evaluateJavascript("(()=>{const end=performance.now()+400;while(performance.now()<end){}return true})()",null);
                    }
                    readButton(page.getWindow().getDecorView()).performClick();
                    if(scenario.equals("cookie-race")){CookieManager.getInstance().setCookie(url,"qr_login_fixture=after; Path=/; Secure");check(CookieManager.getInstance().getCookie(url).contains("qr_login_fixture=after"),"cookie-race: controlled identity actually changed before snapshot callback");}
                });
                if(scenario.equals("cookie-race")){
                    try{await(()->((TextView)field(page,"status")).getText().toString().contains("状态已变化"));}catch(Exception error){ui(()->report.append("DIAGNOSTIC cookie-race finishing=").append(page.isFinishing()).append(" status=").append(((TextView)field(page,"status")).getText()).append(" currentUrlMatches=").append(url.equals(((WebView)field(page,"web")).getUrl())).append('\n'));throw error;}
                    ui(()->{check(!page.isFinishing()&&LoginActivity.pendingDocument==null,"cookie-race: changed session refuses old DOM without ending login");readButton(page.getWindow().getDecorView()).performClick();});
                }
                try{await(()->page.isFinishing()&&field(owner,"reading")!=seed&&field(owner,"reading")!=null&&target.equals(((Document)field(owner,"reading")).url));}catch(Exception error){ui(()->report.append("DIAGNOSTIC ").append(scenario).append(" finishing=").append(page.isFinishing()).append(" status=").append(((TextView)field(page,"status")).getText()).append('\n'));throw error;}
                int count=requests.get();
                ui(()->{
                    Document imported=(Document)field(owner,"reading");Repository repo=(Repository)field(owner,"repo");Item current=(Item)field(owner,"current");
                    check(imported.blocks.stream().anyMatch(b->b.value.contains("登录页面读取")),scenario+": source snapshot appears in shared reader");
                    check(field(owner,"dynamic")==null&&field(owner,"readerWeb")!=null&&repo.cachedArticle(current)==imported,scenario+": result is cached and rendered without another source read");
                    check(imported.cachedSession.equals(ArticleDiskCache.session(target))&&imported.cachedNamespace.equals(new ArticleDiskCache(owner).namespace()),scenario+": imported acquisition remains valid across result cache invalidation");
                    check(scenario.equals("navigated")?imported.metadataHash.isEmpty()&&!ArticleVersion.matches(candidate.readerItem(),imported):ArticleVersion.matches(candidate.readerItem(),imported)&&current.snippet.equals(candidate.snippet),scenario+": acquisition metadata belongs only to original exact URL");
                    check(LoginActivity.pendingDocument==null,scenario+": pending result consumed once");
                    if(source==Source.HUPU){check(imported.blocks.stream().anyMatch(b->b.value.contains("已登录可见的中文回复")),"hupu: visible reply survives hidden markup through actual source import");check(imported.blocks.stream().noneMatch(b->b.value.contains("隐藏来源辅助内容")),"hupu: hidden source text is absent from imported body");}
                });
                drain();try(FeedStore store=new FeedStore(test.getTargetContext(),database)){HotCandidate found=store.candidates(100,0).stream().filter(c->c.id.equals(candidate.id)).findFirst().orElseThrow();check(found.bodyLanguage==(scenario.equals("navigated")?ContentLanguage.Body.UNKNOWN:ContentLanguage.Body.CHINESE),scenario+": only original matched body updates candidate evidence");}
                check(requests.get()==count,scenario+": import does not reload source page");
                if(scenario.equals("hupu"))ui(()->{
                    Document imported=(Document)field(owner,"reading");
                    check(!imported.loginRequired&&imported.sections.size()==2&&imported.sections.get(1).id.equals("hupu-reply-10"),"hupu: login-audience main and comment reach the actual reader");
                    check(!SourceSession.usesWeb(owner,Source.HUPU),"hupu: past login does not force JavaScript reload on subsequent shared-cookie reads");
                    Repository repo=new Repository(owner);
                    try{
                        check(repo.cachedArticle(candidate.readerItem())!=null,"hupu: imported authenticated body is available in current session");
                        CookieManager.getInstance().setCookie(url,"qr_hupu_account=changed; Path=/; Secure");
                        check(!imported.cachedSession.equals(ArticleDiskCache.session(url))&&repo.cachedArticle(candidate.readerItem())==null,"hupu: changed account cookie invalidates the imported cached body");
                    }finally{CookieManager.getInstance().setCookie(url,"qr_hupu_account=; Max-Age=0; Path=/; Secure");repo.close();}
                });
                if(scenario.equals("matching")){
                    await(()->{Object engagement=field(owner,"engagement");return (boolean)field(engagement,"ready")&&(boolean)field(engagement,"visible");});
                    try(FeedStore store=new FeedStore(test.getTargetContext(),database)){check(store.activeFeedback(System.currentTimeMillis()).length()==0,"source import alone creates no weak reading feedback");}
                    readGesture(owner);Thread.sleep(20_100);
                }
                ui(()->{if(scenario.equals("cookie-race"))CookieManager.getInstance().setCookie(url,"qr_login_fixture=; Max-Age=0; Path=/; Secure");owner.finish();});test.waitForIdleSync();reader=null;login=null;
                if(scenario.equals("matching")){
                    Field writer=ReadingInterestRecorder.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(3,TimeUnit.SECONDS);
                    try(FeedStore store=new FeedStore(test.getTargetContext(),database)){org.json.JSONArray feedback=store.activeFeedback(System.currentTimeMillis());check(feedback.length()==1&&"READ".equals(feedback.optJSONObject(0).optString("action")),"actual foreground reading of imported body emits one weak feedback after real scroll and elapsed time");}
                }
            }
            importLifecycle();importRejections();
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{MainActivity endReader=reader;LoginActivity endLogin=login;ui(()->{if(endLogin!=null)endLogin.finish();if(endReader!=null)endReader.finish();LoginActivity.pendingDocument=null;android.content.SharedPreferences.Editor edit=test.getTargetContext().getSharedPreferences("source-session",0).edit().clear();for(Map.Entry<String,?> entry:sessionBefore.entrySet())if(entry.getValue() instanceof Boolean)edit.putBoolean(entry.getKey(),(Boolean)entry.getValue());edit.apply();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void importLifecycle()throws Exception{
        Repository[] repo={null};Document[] imported={null},restored={null};ArticleSession[] oldSession={null};String url="https://qr-import-disk-"+System.nanoTime()+".zhihu.com/question/123";Item item=new Item(Source.ZHIHU,"缓存中文标题",url,"",false,"缓存摘要",null);
        long[] acquired={0};String[] metadata={null};CountDownLatch loaded=new CountDownLatch(1);
        try{
            ui(()->{
                repo[0]=new Repository(test.getTargetContext());oldSession[0]=new ArticleSession(test.getTargetContext(),item);
                Document other=new Document();other.url=url.replace("/123","/456");other.blocks.add(new Block("text","旧会话其他正文"));repo[0].cacheArticle(other);
                Document doc=new Document();doc.url=url;doc.blocks.add(new Block("text","来源主动读取后缓存应可从磁盘恢复的正文。".repeat(20)));new ArticleSession(test.getTargetContext(),item).stamp(doc);acquired[0]=doc.cachedAt;metadata[0]=doc.metadataHash;String oldNamespace=doc.cachedNamespace;
                check(repo[0].importSourceRead(doc),"fresh source import survives cache invalidation");imported[0]=doc;
                check(doc.cachedAt==acquired[0]&&doc.metadataHash.equals(metadata[0])&&!doc.cachedNamespace.equals(oldNamespace),"import preserves actual acquisition time and metadata while transferring namespace");
                check(repo[0].cachedArticle(new Item(Source.ZHIHU,"其他正文",other.url,""))==null,"login import invalidates unrelated old cached bodies");
                Document late=new Document();late.url=url;check(!oldSession[0].stamp(late),"source import invalidates earlier in-flight sessions");
                repo[0].cacheArticle(other);check(repo[0].cachedArticle(new Item(Source.ZHIHU,"其他正文",other.url,""))==null,"old namespace cannot be republished after import");
            });
            Field f=ArticleDiskCache.class.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);
            ui(()->{repo[0].releaseArticleMemory();repo[0].restoreArticle(item,doc->{restored[0]=doc;loaded.countDown();});});
            check(loaded.await(5,TimeUnit.SECONDS)&&restored[0]!=null,"imported source body restores from actual disk after releasing memory");
            ui(()->{Document doc=restored[0];check(doc!=null&&doc!=imported[0]&&ArticleVersion.matches(item,doc)&&doc.cachedAt==acquired[0]&&doc.cachedSession.equals(ArticleDiskCache.session(url)),"disk restoration retains imported provenance and original acquisition time");});
        }finally{ui(()->{if(repo[0]!=null)repo[0].close();});}
    }
    private void importRejections(){ui(()->{
        Repository repo=new Repository(test.getTargetContext());String url="https://qr-import-"+System.nanoTime()+".zhihu.com/question/123";Item item=new Item(Source.ZHIHU,"合成标题",url,"");
        try{
            for(String reason:List.of("cookie","namespace","unknown","future","expired")){
                Document doc=new Document();doc.url=url;doc.blocks.add(new Block("text","合成正文"));new ArticleSession(test.getTargetContext(),item).stamp(doc);String hash=doc.metadataHash;
                if(reason.equals("cookie"))CookieManager.getInstance().setCookie(url,"qr_import_fixture=after; Path=/; Secure");
                if(reason.equals("namespace"))repo.invalidateArticles();
                if(reason.equals("unknown"))doc.cachedSession=null;
                if(reason.equals("future"))doc.cachedAt=System.currentTimeMillis()+60_000;
                if(reason.equals("expired"))doc.cachedAt=System.currentTimeMillis()-11*60_000;
                check(!repo.importSourceRead(doc)&&repo.cachedArticle(item)==null,"source handoff rejects "+reason+" snapshot");
                check(hash.equals(doc.metadataHash),"rejected handoff never relabels acquisition metadata: "+reason);
            }
        }finally{CookieManager.getInstance().setCookie(url,"qr_import_fixture=; Max-Age=0; Path=/; Secure");repo.close();}
    });}
}
