package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.TextView;
import org.json.JSONArray;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Synthetic source pages, real Main.open/loadMore, production parsing and callbacks.
 * No platform authentication claim. No cookie, storage, source HTML or account export.
 */
public final class SessionReuseInstrumentation extends Instrumentation {
    private Activity activity;
    private File directory;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures,completed;
    private final String id=String.valueOf(System.currentTimeMillis());
    private final List<Fixture> fixtures=new ArrayList<>();
    interface Checked { void run() throws Exception; }
    interface Condition { boolean ready() throws Exception; }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private synchronized void log(String s){report.append(s).append('\n');}
    private void check(boolean ok,String description){checks++;if(!ok)failures++;log((ok?"PASS ":"FAIL ")+description);}
    private void ui(Checked action)throws Exception{
        Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});
        if(error[0]!=null)throw new Exception("Main-thread test action",error[0]);
    }
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void call(String name,Class<?>[] types,Object... values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values);}
    private void waitFor(String label,long timeout,Condition condition)throws Exception{
        long end=SystemClock.elapsedRealtime()+timeout;
        while(SystemClock.elapsedRealtime()<end){boolean[] ready={false};ui(()->ready[0]=condition.ready());if(ready[0])return;Thread.sleep(60);}
        throw new AssertionError("Timed out: "+label);
    }
    private Item item(int suffix){return new Item(Source.ZHIHU,"合成页面复用回归","https://www.zhihu.com/question/"+id+suffix,"");}
    private static String answer(String id,String text){return "<div class='AnswerItem' data-zop='{&quot;itemId&quot;:&quot;"+id+"&quot;}'><span class='AuthorInfo-name'>合成作者</span><div class='RichContent-inner'><div class='RichText'><p>"+text+"</p></div></div></div>";}
    private static String page(String body){return "<!doctype html><html><head><title>合成页面复用回归</title></head><body><h1>合成测试：页面复用，不是真实平台</h1>"+body+"</body></html>";}
    private static String text(Document d){StringBuilder s=new StringBuilder();if(d!=null)for(Block b:d.blocks)s.append(b.value);return s.toString();}
    private Document reading()throws Exception{return (Document)field(activity,"reading");}
    private AnswerStream stream()throws Exception{return (AnswerStream)field(activity,"answers");}
    private WebView source(AnswerStream stream)throws Exception{return (WebView)field(stream,"web");}
    private void stopHome()throws Exception{
        call("home",new Class<?>[]{boolean.class},false);
        ((Repository)field(activity,"repo")).cancelPending();
        Object board=field(activity,"dynamicBoard");if(board!=null)((DynamicBoard)board).close();
    }
    private final class Fixture {
        final Item item;final String[] snapshots;final long began=SystemClock.elapsedRealtime();
        volatile int reads,requests;DynamicReader dynamic;WebView initial;String initialPath="",delayedPage="";
        final List<Trace> traces=new ArrayList<>();
        Fixture(Item i,String... s){item=i;snapshots=s;fixtures.add(this);}
        String safeUrl(String url){
            if(url==null)return "null";
            if(url.equals("about:blank"))return "about:blank";
            // Never export an unexpected destination, query, fragment or account URL.
            if(url.matches("https://www\\.zhihu\\.com/question/"+id+"\\d+(?:/answer/\\d+)?"))return url;
            return "<non-fixture URL redacted>";
        }
        final class Trace {
            final WebView web;final int number;
            final AtomicInteger starts=new AtomicInteger(),commits=new AtomicInteger(),finishes=new AtomicInteger(),intercepts=new AtomicInteger(),mainIntercepts=new AtomicInteger(),matched=new AtomicInteger(),other=new AtomicInteger(),errors=new AtomicInteger(),ready=new AtomicInteger();
            Trace(WebView w,int n){web=w;number=n;}
            void event(String type,String url){log("NAV fixture="+item.url+" instance="+number+" ms="+(SystemClock.elapsedRealtime()-began)+" "+type+" url="+safeUrl(url));}
        }
        void state()throws Exception{
            log("STATE fixture="+item.url+" productionReads="+reads+" fixtureRequests="+requests+" instances="+traces.size());
            if(dynamic!=null)log("STATE DynamicReader ended="+field(dynamic,"ended")+" ownsWeb="+field(dynamic,"ownsWeb")+" delivering="+field(dynamic,"delivering")+" attempts="+field(dynamic,"attempts"));
            for(Trace t:traces){
                boolean attached=t.web.getParent()!=null;
                log("STATE instance="+t.number+" attached="+attached+" start="+t.starts+" commit="+t.commits+" finish="+t.finishes+" intercept="+t.intercepts+" mainIntercept="+t.mainIntercepts+" matched="+t.matched+" other="+t.other+" errors="+t.errors+" fixtureReady="+t.ready+(attached?" currentUrl="+safeUrl(t.web.getUrl())+" progress="+t.web.getProgress()+" js="+t.web.getSettings().getJavaScriptEnabled():" detached; no destroyed-WebView query"));
            }
        }
        String html(){
            JSONArray values=new JSONArray();for(String s:snapshots)values.put(s);
            return "<!doctype html><html><head><meta charset='utf-8'><title>Synthetic reuse fixture</title></head><body><p>合成来源：无登录、无外网、无存储修改</p><script>(function(){var v="+values.toString().replace("</","<\\/")+",n=0,late=false,delayed="+org.json.JSONObject.quote(delayedPage).replace("</","<\\/")+";window.qrReleaseLate=function(){late=true;return !!delayed;};Object.defineProperty(document.documentElement,'outerHTML',{configurable:true,get:function(){n++;console.log('QR_REUSE_READ:'+n);return late&&delayed?delayed:v[Math.min(n-1,v.length-1)];}});"+(initialPath.isEmpty()?"":"history.replaceState(null,'','"+initialPath+"');")+"console.log('QR_REUSE_READY');})();</script></body></html>";
        }
        void install(WebView web){
            WebViewClient delegate=web.getWebViewClient();byte[] bytes=html().getBytes(StandardCharsets.UTF_8);
            Trace found=null;for(Trace trace:traces)if(trace.web==web)found=trace;
            final Trace trace=found==null?new Trace(web,traces.size()+1):found;if(found==null)traces.add(trace);
            trace.event("INSTALL delegate="+delegate.getClass().getName(),web.getUrl());
            web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.message().startsWith("QR_REUSE_READ:")){reads=Integer.parseInt(m.message().substring(14));log("SYNTHETIC "+item.url+" instance="+trace.number+" production DOM read="+reads);}else if(m.message().equals("QR_REUSE_READY")){trace.ready.incrementAndGet();trace.event("FIXTURE_READY",item.url);}return true;}});
            web.setWebViewClient(new WebViewClient(){
                private WebResourceResponse intercept(String url,boolean main){
                    boolean match=url.equals(item.url)||url.equals(item.url.replaceAll("(/question/\\d+).*","$1"));
                    trace.intercepts.incrementAndGet();if(main)trace.mainIntercepts.incrementAndGet();if(match)trace.matched.incrementAndGet();else trace.other.incrementAndGet();trace.event("INTERCEPT main="+main+" matched="+match,url);
                    // Exact fixture and its normalized question are locally served; all other resources are empty.
                    if(match){requests++;return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(bytes));}
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return intercept(r.getUrl().toString(),r.isForMainFrame());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,String u){return intercept(u,false);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,String u){return delegate.shouldOverrideUrlLoading(w,u);}
                @Override public void onPageStarted(WebView w,String u,Bitmap icon){trace.starts.incrementAndGet();trace.event("START",u);delegate.onPageStarted(w,u,icon);}
                @Override public void onPageCommitVisible(WebView w,String u){trace.commits.incrementAndGet();trace.event("COMMIT",u);delegate.onPageCommitVisible(w,u);}
                @Override public void onPageFinished(WebView w,String u){trace.finishes.incrementAndGet();trace.event("FINISH",u);delegate.onPageFinished(w,u);}
                @Override public void onLoadResource(WebView w,String u){delegate.onLoadResource(w,u);}
                @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){trace.errors.incrementAndGet();trace.event("ERROR code="+e.getErrorCode()+" main="+r.isForMainFrame(),r.getUrl().toString());delegate.onReceivedError(w,r,e);}
                @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){trace.errors.incrementAndGet();trace.event("HTTP_ERROR status="+e.getStatusCode()+" main="+r.isForMainFrame(),r.getUrl().toString());delegate.onReceivedHttpError(w,r,e);}
                @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){trace.errors.incrementAndGet();trace.event("SSL_ERROR",e.getUrl());delegate.onReceivedSslError(w,h,e);}
                @Override public void doUpdateVisitedHistory(WebView w,String u,boolean reload){delegate.doUpdateVisitedHistory(w,u,reload);}
                @Override public boolean onRenderProcessGone(WebView w,RenderProcessGoneDetail d){trace.errors.incrementAndGet();trace.event("RENDER_GONE crashed="+d.didCrash(),null);return delegate.onRenderProcessGone(w,d);}
            });
        }
        void open()throws Exception{
            call("open",new Class<?>[]{Item.class,boolean.class},item,false);
            dynamic=(DynamicReader)field(activity,"dynamic");
            if(dynamic==null)throw new AssertionError("Unique synthetic URL must take uncached DynamicReader path");
            initial=(WebView)field(dynamic,"web");install(initial);
        }
        void first()throws Exception{
            ui(this::open);waitFor("first parsed answer",10000,()->reading()!=null&&AnswerStream.answers(reading())==1);
        }
        void more()throws Exception{
            ui(()->{
                // Wrapping the new production owner does not navigate or evaluate source HTML.
                AnswerStream before=stream();if(before!=null)install(source(before));
                call("loadMore",new Class<?>[0]);
                // Legacy/fallback opens a new WebView in this UI turn: intercept before any navigation event.
                AnswerStream after=stream();if(after!=null&&after!=before)install(source(after));
            });
        }
    }
    private void picture(String name)throws Exception{
        Thread.sleep(300);Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private void summary(String name,String details)throws Exception{
        ui(()->{TextView t=new TextView(activity);t.setTextSize(19);t.setTextColor(0xffe3eae5);t.setBackgroundColor(0xff141a18);t.setPadding(26,60,26,20);t.setText("合成生命周期诊断\n不是真实平台/正文视觉验收\n"+name+"\n"+details);activity.setContentView(t);});picture(name);
    }
    private void journey(String name,Checked action){
        log("\nCASE "+name);
        fixtures.clear();
        try{ui(this::stopHome);action.run();completed++;}
        catch(Throwable error){failures++;log("ERROR "+name+" "+error);for(Throwable cause=error.getCause();cause!=null;cause=cause.getCause())log("ERROR cause class="+cause.getClass().getName());try{picture(name+"-error");}catch(Exception ignored){}}
        finally{try{ui(()->{for(Fixture fixture:fixtures)fixture.state();AnswerStream a=stream();log("STATE Main loadingMore="+field(activity,"loadingMore")+" readingAnswers="+(reading()==null?-1:AnswerStream.answers(reading()))+" hasStream="+(a!=null));if(a!=null)log("STATE AnswerStream closed="+field(a,"closed")+" busy="+field(a,"busy")+" started="+field(a,"started")+" attempts="+field(a,"attempts"));});}catch(Exception error){log("STATE unavailable class="+error.getClass().getName());}try{ui(this::stopHome);}catch(Exception error){failures++;log("ERROR cleanup "+error);}}
    }
    private void ownReaderSafe()throws Exception{
        WebView own=(WebView)field(activity,"readerWeb");
        check(own!=null&&!own.getSettings().getJavaScriptEnabled(),"Own reader JavaScript is off after production callback");
        check(own!=null&&!own.getSettings().getAllowFileAccess()&&!own.getSettings().getAllowContentAccess(),"Own reader file/content access stays disabled");
        check(own!=null&&own.getUrl()!=null&&own.getUrl().startsWith(ReaderHtml.ACTION),"Visible reader uses its own generated document URL");
    }
    private void reuse()throws Exception{
        String old=answer("reuse-old","合成已读回答：必须保留。"),next=answer("reuse-new","合成新增回答：必须通过真实解析与 Main 回调追加。");
        Fixture f=new Fixture(item(1),page(old),page(old),page(old+next),page(old+next));f.first();
        ui(()->{
            AnswerStream a=stream();check(a!=null,"Main adopts eligible source after first batch");
            check(a!=null&&source(a)==f.initial,"First batch and retained continuation own exactly the same WebView");
            check(!(Boolean)field(f.dynamic,"ownsWeb"),"DynamicReader relinquished source ownership during success window");
            ViewGroup host=activity.findViewById(android.R.id.content);
            check(f.initial.getParent()==host&&host.indexOfChild(f.initial)==0&&host.indexOfChild((View)field(activity,"root"))>0,"Retained source reattached behind opaque native root");
            check(f.initial.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS&&!f.initial.isFocusable()&&!f.initial.isClickable(),"Hidden source cannot receive accessibility or focus interaction");
            check(f.initial.getSettings().getBlockNetworkImage(),"Transferred extraction source retains blocked network-image setting");
        });
        f.more();waitFor("Main appends parsed answer",12000,()->reading()!=null&&AnswerStream.answers(reading())==2&&!(Boolean)field(activity,"loadingMore"));
        AnswerStream[] retained={null};
        ui(()->{retained[0]=stream();check(source(retained[0])==f.initial,"Continuation did not replace the source WebView");check(f.requests==1,"Exactly one locally intercepted source navigation across first batch and continuation; requests="+f.requests);check(f.initial.copyBackForwardList().getSize()==1,"Source navigation history contains one document");check(f.reads>=4,"Both initial and appended snapshots settled through production DOM reads; reads="+f.reads);check(text(reading()).contains("合成已读回答")&&text(reading()).contains("合成新增回答"),"Actual Main.reading retains old answer and appends new content");ownReaderSafe();});
        picture("01-main-reuse-two-answers");
        ui(()->{stopHome();check(stream()==null&&(Boolean)field(retained[0],"closed")&&f.initial.getParent()==null,"Return home closes continuation and removes hidden source");retained[0].close();f.dynamic.close();check(f.initial.getParent()==null,"Old and new owner close calls are idempotent after handoff");});
    }
    /** Fixed own-document inspection; a tap is a real pointer, never JavaScript click(). */
    private org.json.JSONObject ownTarget(String selector,boolean tap)throws Exception{
        CountDownLatch done=new CountDownLatch(1);Throwable[] error={null};org.json.JSONObject[] value={null};WebView[] target={null};
        java.util.concurrent.atomic.AtomicBoolean ownsJs=new java.util.concurrent.atomic.AtomicBoolean(),active=new java.util.concurrent.atomic.AtomicBoolean(true);
        ui(()->{
            WebView web=(WebView)field(activity,"readerWeb");target[0]=web;
            if(web==null||web.getUrl()==null||!web.getUrl().startsWith(ReaderHtml.ACTION+"?render="))throw new AssertionError("No current own reader");
            if(web.getSettings().getJavaScriptEnabled()){value[0]=new org.json.JSONObject().put("retry",true).put("reason","production-js-window");done.countDown();return;}
            ownsJs.set(true);web.getSettings().setJavaScriptEnabled(true);
            String expression="(function(){var e=document.querySelector("+org.json.JSONObject.quote(selector)+"),r=e?e.getBoundingClientRect():null,p=e?e.querySelectorAll('p:not(.preview)'):[],text='',m=document.querySelector('meta[name=quiet-reader-source]'),h=document.querySelector('h1');for(var i=0;i<p.length;i++)text+=p[i].textContent;return {href:location.href,ready:document.readyState,source:m?m.content:'',title:h?h.textContent:'',end:!!document.querySelector('.end'),y:scrollY,found:!!e,top:r?r.top:0,bottom:r?r.bottom:0,left:r?r.left:0,right:r?r.right:0,paragraphs:p.length,text:text,label:e?e.textContent:''};})()";
            web.evaluateJavascript("JSON.stringify("+expression+")",raw->{try{
                if(ownsJs.compareAndSet(true,false))web.getSettings().setJavaScriptEnabled(false);
                if(!active.get())return;
                Object decoded=new org.json.JSONTokener(raw).nextValue();if(!(decoded instanceof String))throw new AssertionError("Own DOM returned no JSON string");
                org.json.JSONObject box=new org.json.JSONObject((String)decoded);value[0]=box;
                Item item=(Item)field(activity,"current");Document document=reading();
                if(web!=field(activity,"readerWeb")||!box.optString("href").equals(web.getUrl())){box.put("retry",true).put("reason","revision-changed").put("nativeUrl",String.valueOf(web.getUrl()));return;}
                if(item==null||document==null||!item.url.equals(box.optString("source"))||!document.title.equals(box.optString("title"))||!box.optBoolean("end")||!"complete".equals(box.optString("ready"))){box.put("retry",true).put("reason","identity-or-dom-not-ready");return;}
                if(Math.abs(web.getScrollY()-box.optDouble("y")*web.getScale())>5){box.put("retry",true).put("reason","scroll-moved").put("nativeY",web.getScrollY()).put("scale",web.getScale());return;}
                if(!tap||!box.optBoolean("found"))return;
                android.graphics.Rect bounds=new android.graphics.Rect();int[] at=new int[2];web.getLocationOnScreen(at);
                float scale=web.getScale(),x=at[0]+(float)((box.optDouble("left")+box.optDouble("right"))/2)*scale,y=at[1]+(float)((box.optDouble("top")+box.optDouble("bottom"))/2)*scale;
                if(!web.isShown()||!web.hasWindowFocus()||!web.getGlobalVisibleRect(bounds))throw new AssertionError("Own reader has no focused visible bounds");
                box.put("tapX",x).put("tapY",y).put("visibleBounds",bounds.toShortString());
                if(x<=bounds.left+5||x>=bounds.right-5||y<=bounds.top+5||y>=bounds.bottom-5){web.scrollTo(0,Math.max(0,(int)((box.optDouble("top")+box.optDouble("y"))*scale)-web.getHeight()/3));box.put("retry",true).put("reason","target-outside-visible-bounds");return;}
                long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                try{boolean sentDown=getUiAutomation().injectInputEvent(down,false),sentUp=getUiAutomation().injectInputEvent(up,false);box.put("tapped",sentDown&&sentUp);}finally{down.recycle();up.recycle();}
            }catch(Throwable failure){error[0]=failure;}finally{done.countDown();}});
        });
        try{if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("Own target inspection timed out");if(error[0]!=null)throw new Exception(error[0]);return value[0];}
        finally{active.set(false);ui(()->{if(target[0]!=null&&ownsJs.compareAndSet(true,false))target[0].getSettings().setJavaScriptEnabled(false);});}
    }
    private boolean touchOwn(String selector)throws Exception{
        for(int i=0;i<8;i++){org.json.JSONObject box=ownTarget(selector,true);log("OWN_POINTER selector="+selector+" attempt="+i+" observation="+box);if(box.optBoolean("tapped"))return true;Thread.sleep(180);}return false;
    }
    /** Positive source/revision/title/end-marker readiness must precede any absence assertion. */
    private org.json.JSONObject readyOwn(String selector)throws Exception{
        org.json.JSONObject box=null;long until=SystemClock.elapsedRealtime()+5000;
        do{box=ownTarget(selector,false);if(!box.optBoolean("retry")&&box.optBoolean("found"))return box;Thread.sleep(150);}while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Current own document never became identity-bound and ready: "+box);
    }
    private void swipeOwn(boolean towardBottom)throws Exception{
        android.graphics.Rect bounds=new android.graphics.Rect();ui(()->{WebView web=(WebView)field(activity,"readerWeb");if(web==null||!web.hasWindowFocus()||!web.getGlobalVisibleRect(bounds))throw new AssertionError("Cannot swipe invisible own reader");});
        float x=bounds.exactCenterX(),start=bounds.top+bounds.height()*(towardBottom?.8f:.25f),end=bounds.top+bounds.height()*(towardBottom?.25f:.8f);long downTime=SystemClock.uptimeMillis();
        for(int i=0;i<=9;i++){long now=SystemClock.uptimeMillis();MotionEvent event=MotionEvent.obtain(downTime,now,i==0?MotionEvent.ACTION_DOWN:i==9?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,start+(end-start)*i/9f,0);event.setSource(InputDevice.SOURCE_TOUCHSCREEN);try{sendPointerSync(event);}finally{event.recycle();}if(i<9)Thread.sleep(35);}Thread.sleep(150);
    }
    private void delayedThirdAnswer()throws Exception{
        StringBuilder first=new StringBuilder(),second=new StringBuilder();for(int i=0;i<20;i++){first.append("合成第一条已读回答。");second.append("合成第二条已读回答。");}
        String one=first.toString(),two=second.toString(),three="合成第三条迟到回答，真实续读后必须可以展开阅读。第三答末段验证。";
        String initial=page(answer("late-one",one)+answer("late-two",two));
        Fixture f=new Fixture(item(11),initial);f.delayedPage=page(answer("late-one",one)+answer("late-two",two)+answer("late-three",three));
        ui(f::open);waitFor("initial two real parsed answers",10000,()->reading()!=null&&AnswerStream.answers(reading())==2);
        ui(()->{check(AnswerStream.answers(reading())==2,"Delayed fixture starts with exactly two answers");check(stream()!=null&&source(stream())==f.initial,"Main retains the first source while its third answer is unavailable");f.install(source(stream()));});
        waitFor("collapsed own-reader layout ready",5000,()->{WebView w=(WebView)field(activity,"readerWeb");return w!=null&&w.getContentHeight()>0&&w.getHeight()>0;});
        boolean[] expandForScroll={false};
        ui(()->{WebView w=(WebView)field(activity,"readerWeb");float density=activity.getResources().getDisplayMetrics().density;int threshold=(int)(100*density+.5f);float max=w.getContentHeight()*density-w.getHeight();expandForScroll[0]=max<=threshold;
            log("GEOMETRY 09 before input scrollY="+w.getScrollY()+" contentHeightCss="+w.getContentHeight()+" webHeightPx="+w.getHeight()+" density="+density+" scale="+w.getScale()+" productionMaxScroll="+max+" threshold100dp="+threshold+" requiresRealExpansion="+expandForScroll[0]);});
        boolean expanded=!expandForScroll[0]||touchOwn("#part-0 > a.action");
        check(expanded,"Auto-scroll journey has scrollable content; if collapsed viewport is too short, open the first real answer using pointer input");
        if(!expanded)throw new AssertionError("Cannot establish a real scrollable reading journey");
        if(expandForScroll[0])waitFor("expanded answer exceeds automatic scroll threshold",5000,()->{WebView w=(WebView)field(activity,"readerWeb");float density=activity.getResources().getDisplayMetrics().density;return w.getContentHeight()*density-w.getHeight()>100*density;});
        waitFor("initial automatic-scroll suppression expires",5000,()->System.currentTimeMillis()>(Long)field(activity,"suppressAutoUntil"));
        boolean[] began={false};for(int i=0;i<5&&!began[0];i++){swipeOwn(true);final int swipe=i;ui(()->{began[0]=(Boolean)field(activity,"loadingMore");WebView w=(WebView)field(activity,"readerWeb");log("GEOMETRY 09 swipe="+swipe+" scrollY="+w.getScrollY()+" contentHeightCss="+w.getContentHeight()+" webHeightPx="+w.getHeight()+" loadingMore="+began[0]);});}
        check(began[0],"Actual downward touch scrolling starts production automatic continuation");if(!began[0])throw new AssertionError("Automatic continuation never started");
        waitFor("bounded no-new continuation returns to Main",24000,()->!(Boolean)field(activity,"loadingMore")&&(Boolean)field(activity,"morePaused"));
        ui(()->{check(AnswerStream.answers(reading())==2&&text(reading()).contains(one)&&text(reading()).contains(two),"No-new callback preserves both already readable answers");check((Boolean)field(activity,"morePaused"),"No-new callback pauses automatic reattempts");String status=((TextView)field(activity,"readerStatus")).getText().toString();check(status.contains("本次没有新回答")&&status.contains("重试"),"User sees no-new explanation and explicit retry guidance");check(stream()!=null&&source(stream())==f.initial&&!(Boolean)field(stream(),"closed")&&!(Boolean)field(stream(),"busy"),"No-new outcome retains an idle live continuation source");ownReaderSafe();});
        ui(()->check(reading().notice.contains("本次没有新增回答")&&reading().notice.contains("加载下一批回答")&&reading().notice.contains("重试")&&!reading().notice.contains("继续下滑"),"No-new body notice must direct explicit retry and must not promise paused automatic scrolling"));
        picture("09a-two-answers-no-new-retry-guidance");
        int readsBefore=f.reads;CountDownLatch released=new CountDownLatch(1);String[] ack={null};
        ui(()->f.initial.evaluateJavascript("window.qrReleaseLate()",raw->{ack[0]=raw;released.countDown();}));
        check(released.await(3,TimeUnit.SECONDS)&&"true".equals(ack[0]),"Explicit synthetic third-answer gate released only after no-new result; no production callback replacement");
        swipeOwn(false);swipeOwn(true);Thread.sleep(1400);
        ui(()->{check(f.reads==readsBefore&&AnswerStream.answers(reading())==2,"Pure scrolling after pause does not pretend the late third answer was fetched");check(!(Boolean)field(activity,"loadingMore")&&(Boolean)field(activity,"morePaused"),"Paused automatic behavior remains idle until explicit retry");});
        check(touchOwn("a[href='https://quiet-reader.invalid/more']"),"Real pointer activates the visible load-next-answers link after no-new pause");
        waitFor("late third answer applied through production callback",12000,()->reading()!=null&&AnswerStream.answers(reading())==3&&!(Boolean)field(activity,"loadingMore"));
        ui(()->{java.util.Set<String> ids=new java.util.HashSet<>();for(Section section:reading().sections)if(section.answer)ids.add(section.id);check(ids.size()==3&&ids.contains("late-one")&&ids.contains("late-two")&&ids.contains("late-three"),"Recovered Main document contains three distinct answer identities");check(text(reading()).contains(one)&&text(reading()).contains(two)&&text(reading()).contains(three),"Explicit retry appends third answer without replacing either old answer");check(source(stream())==f.initial&&f.requests==1&&f.initial.copyBackForwardList().getSize()==1,"Retry uses retained source with exactly one main navigation/history entry");});
        check(touchOwn("#part-2 > a.action"),"Third answer expansion is activated by a real pointer");
        org.json.JSONObject body=null;for(int i=0;i<15;i++){Thread.sleep(150);body=ownTarget("#part-2",false);if(!body.optBoolean("retry")&&body.optInt("paragraphs")>0)break;}
        check(body!=null&&body.optInt("paragraphs")>0&&body.optString("text").contains("第三答末段验证"),"Third answer's non-preview text is actually rendered after expansion, not merely counted");
        ui(this::ownReaderSafe);picture("09b-third-answer-recovered-and-expanded");
        log("LIMIT Third answer is explicitly gated synthetic source HTML; actual parser, Main callback, automatic scrolling and physical retry are exercised. Not proof that anonymous Zhihu provides another answer or that login can be bypassed.");
    }
    private void videoShellThenAnswer()throws Exception{
        String video="<div class='QuestionRichText'><video src='https://example.invalid/synthetic-question.mp4'></video></div>";
        String answerText="合成视频问题下迟到的第一条回答。必须经过真实续读回调与触摸展开才算可读。";
        String shell=page(video),withAnswer=page(video+answer("video-late-answer",answerText));
        Fixture f=new Fixture(item(12),shell,shell,withAnswer,withAnswer);
        Document initial=SourceParser.article(Source.ZHIHU,shell,f.item.url);
        check(initial.unsupportedVideo&&!initial.hasContent()&&initial.canPresent(),"Synthetic video shell is presentable but has no invented answer/text block");
        ui(f::open);waitFor("video description or parsed answer initially presented",10000,()->reading()!=null);
        final boolean[] capable={false};
        ui(()->{
            check(reading().notice.contains("视频"),"Initial own presentation explains the unsupported source video");
            WebView own=(WebView)field(activity,"readerWeb");AnswerStream a=stream();
            capable[0]=own!=null&&a!=null&&!(Boolean)field(a,"closed");
            check(capable[0],"Presenting a video-only Zhihu question must retain own reader plus a usable continuation source");
            check(a!=null&&source(a)==f.initial&&f.initial.getParent()!=null,"Video-shell first presentation must not destroy or replace the retained same-question source");
        });
        picture("10a-video-shell-initial");
        if(!capable[0])throw new AssertionError("Video shell lost own-reader continuation; no private loadMore call may bypass the missing user entry");
        ui(()->f.install(source(stream())));
        waitFor("video own-reader document ready",5000,()->{WebView own=(WebView)field(activity,"readerWeb");return own!=null&&own.getUrl()!=null&&own.getUrl().startsWith(ReaderHtml.ACTION+"?render=");});
        org.json.JSONObject more=ownTarget("a[href='https://quiet-reader.invalid/more']",false);
        check(more.optBoolean("found")&&more.optString("label").contains("加载下一批回答"),"Video shell exposes the actual own-reader load-next-answers entry");
        check(touchOwn("a[href='https://quiet-reader.invalid/more']"),"Real pointer activates continuation from the video shell");
        waitFor("first late video-question answer applied",12000,()->reading()!=null&&AnswerStream.answers(reading())==1&&!(Boolean)field(activity,"loadingMore"));
        ui(()->{
            check(text(reading()).contains(answerText)&&AnswerStream.answers(reading())==1,"Actual Main callback presents the single late answer without replacing it with video metadata");
            check(reading().unsupportedVideo&&reading().notice.contains("视频"),"Continuation preserves the unsupported-video flag and explanation alongside the real answer");
            check(f.reads>=4,"Source sequence reaches and stabilizes actual answer snapshots; reads="+f.reads);
            check(source(stream())==f.initial&&f.requests==1&&f.initial.copyBackForwardList().getSize()==1,"Video continuation keeps one same-question source navigation");
        });
        check(touchOwn("section.part[data-section-label^='回答'] > a.action"),"Late answer opens using its actual visible fold control");
        org.json.JSONObject body=null;for(int i=0;i<15;i++){Thread.sleep(150);body=ownTarget("section.part[data-section-label^='回答']",false);if(!body.optBoolean("retry")&&body.optInt("paragraphs")>0)break;}
        check(body!=null&&body.optInt("paragraphs")>0&&body.optString("text").contains(answerText),"Late answer text is visibly rendered outside the preview, not just stored in a Document");
        ui(this::ownReaderSafe);picture("10b-video-late-answer-expanded");
        ui(()->{AnswerStream a=stream();stopHome();check(stream()==null&&(Boolean)field(a,"closed")&&f.initial.getParent()==null,"Leaving the recovered video question releases its retained source");});
        log("LIMIT V,V,V+A,V+A is synthetic. Initial video explanation may be presented before the answer; no fixed initial-read count is required. No platform/video playback/authentication claim.");
    }
    private void videoReplacementKeepsNewAnswer()throws Exception{
        String old="合成仍然有效的已读文字回答。",removed="合成迟到类型的视频摘要应删除。",added="合成新文字答案C，不能因为删一加一总数不变就误报没有新回答。";
        String initial=page(answer("keep-a",old)+answer("late-video-b",removed));
        String classified=answer("late-video-b",removed).replace("class='AnswerItem'","class='AnswerItem VideoAnswer'");
        String next=page(answer("keep-a",old)+classified+answer("new-c",added));
        Fixture f=new Fixture(item(13),initial,initial,next,next);
        ui(f::open);waitFor("two initial prose answers",10000,()->reading()!=null&&AnswerStream.answers(reading())==2);
        ui(()->{check(text(reading()).contains(old)&&text(reading()).contains(removed),"Replacement precondition: initial A and unclassified B prose are genuinely parsed");check(stream()!=null&&source(stream())==f.initial,"Replacement precondition retains the same source page");f.install(source(stream()));});
        check(touchOwn("#part-0 > a.action"),"Real pointer expands stable answer A before the filtering batch");Thread.sleep(350);picture("11a-before-video-reclassification");
        long started=SystemClock.elapsedRealtime();check(touchOwn("a[href='https://quiet-reader.invalid/more']"),"Real pointer requests the replacement batch");
        waitFor("replacement batch reaches actual Main callback",20000,()->reading()!=null&&reading().filteredSectionIds.contains("late-video-b")&&!(Boolean)field(activity,"loadingMore"));
        ui(()->{
            int attempts=(Integer)field(stream(),"attempts");log("REPLACEMENT elapsedMs="+(SystemClock.elapsedRealtime()-started)+" productionAttempts="+attempts+" reads="+f.reads+" morePaused="+field(activity,"morePaused"));
            check(attempts<=3,"One genuinely new answer must settle within three production samples despite equal net count; attempts="+attempts);
            check(AnswerStream.answers(reading())==2,"Deletion plus addition leaves exactly two readable answers");
            java.util.Set<String> ids=new java.util.HashSet<>();for(Section s:reading().sections)if(s.answer)ids.add(s.id);
            check(ids.contains("keep-a")&&ids.contains("new-c")&&!ids.contains("late-video-b"),"Production merge retains A, adds new C, and removes classified B identity");
            check(reading().filteredSectionIds.contains("late-video-b")&&!text(reading()).contains(removed),"Classified video answer body stays absent from the accumulated document");
            check(!reading().notice.contains("本次没有新增回答"),"Reader notice must not deny the actually added answer C");
            String status=((TextView)field(activity,"readerStatus")).getText().toString();check(status.contains("已追加")&&!status.contains("本次没有新回答"),"Visible native status correctly reports newly appended content");
            check(!(Boolean)field(activity,"morePaused"),"Net-zero replacement with new readable identity must not pause automatic continuation");
            check(source(stream())==f.initial&&f.requests==1,"Replacement batch uses retained source without another main navigation");
        });picture("11b-replacement-result-status");
        ui(()->{WebView web=(WebView)field(activity,"readerWeb");log("REPLACEMENT position nativeY="+web.getScrollY()+" contentHeight="+web.getContentHeight()+" webHeight="+web.getHeight()+" scale="+web.getScale());check(web.getScrollY()>=0,"Short-document anchor restoration never leaves a negative native scroll position");});
        org.json.JSONObject kept=readyOwn("#part-0");check(kept.optInt("paragraphs")>0&&kept.optString("text").contains(old),"Stable A remains expanded with non-preview text after B is removed");
        check(touchOwn("#part-1 > a.action"),"Real pointer expands new answer C after video deletion");
        org.json.JSONObject body=null;for(int i=0;i<15;i++){Thread.sleep(150);body=ownTarget("#part-1",false);if(!body.optBoolean("retry")&&body.optInt("paragraphs")>0)break;}
        check(body!=null&&body.optInt("paragraphs")>0&&body.optString("text").contains(added),"New C is visibly rendered as non-preview text, not only counted");
        org.json.JSONObject whole=readyOwn("body");check(!whole.optString("label").contains(removed),"Old video summary is absent from actual own-reader DOM");
        ui(this::ownReaderSafe);picture("11c-new-answer-expanded");
    }
    private View findNative(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())||(view instanceof TextView&&label.contentEquals(((TextView)view).getText())))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=findNative(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private boolean touchNative(String label)throws Exception{android.graphics.Rect box=new android.graphics.Rect();ui(()->{View view=findNative(activity.getWindow().getDecorView(),label);if(view==null||!view.getGlobalVisibleRect(box)||box.height()<view.getHeight()-2)throw new AssertionError("Native target unavailable: "+label);});long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,box.centerX(),box.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,box.centerX(),box.centerY(),0);try{return getUiAutomation().injectInputEvent(down,true)&&getUiAutomation().injectInputEvent(up,true);}finally{down.recycle();up.recycle();}}
    private void filteredLastAnswerCacheReturn()throws Exception{
        String removed="合成唯一配文：迟到视频分类后不得从旧缓存复活。";
        String first=page(answer("last-video",removed)),last=page(answer("last-video",removed).replace("class='AnswerItem'","class='AnswerItem VideoAnswer'"));
        Fixture f=new Fixture(item(14),first,first,last,last);
        android.content.SharedPreferences boards=getTargetContext().getSharedPreferences("boards",0);String key=Source.ZHIHU.name();java.util.Map<String,?> original=boards.getAll();
        try{
            ui(()->{Field selected=MainActivity.class.getDeclaredField("selected");selected.setAccessible(true);selected.set(activity,Source.ZHIHU);((Repository)field(activity,"repo")).cache(Source.ZHIHU,java.util.Collections.singletonList(f.item));});
            f.first();ui(()->{check(((Repository)field(activity,"repo")).cachedArticle(f.item)!=null,"Cache precondition: first parsed summary is actually cached");f.install(source(stream()));});
            check(touchOwn("a[href='https://quiet-reader.invalid/more']"),"Real pointer requests late classification of the only answer");
            waitFor("sole video answer removed",20000,()->reading()!=null&&reading().filteredSectionIds.contains("last-video")&&!(Boolean)field(activity,"loadingMore"));
            ui(()->{check(!reading().hasContent()&&AnswerStream.answers(reading())==0,"Live reader removes the sole classified video answer");Document cached=((Repository)field(activity,"repo")).cachedArticle(f.item);check(cached==null||!text(cached).contains(removed),"Article cache must not retain stale video prose after the live document became empty");});picture("12a-only-answer-filtered");
            check(touchNative("返回"),"Real native back returns from empty filtered question");waitFor("return to board",5000,()->field(activity,"current")==null);
            check(touchNative(f.item.title+"，"+f.item.detail+"，进入阅读"),"Real board card reopens the same classified question");
            // Observe any cache-miss fallback. This is not a same-turn constructor intercept;
            // the tested bug and intended filtered-document cache both use cache hits here.
            ui(()->{DynamicReader dynamic=(DynamicReader)field(activity,"dynamic");if(reading()==null&&dynamic!=null){WebView source=(WebView)field(dynamic,"web");if(source!=f.initial){Fixture reread=new Fixture(f.item,last,last);reread.install(source);}}});
            waitFor("reopened question presentation",10000,()->reading()!=null);
            ui(()->check(!text(reading()).contains(removed)&&AnswerStream.answers(reading())==0,"Returning and reopening cannot resurrect excluded video-summary text"));
            org.json.JSONObject body=readyOwn("body");check(!body.optString("label").contains(removed),"Actual reopened own-reader DOM contains no stale classified video summary");picture("12b-reopened-after-filter");
        }finally{android.content.SharedPreferences.Editor edit=boards.edit();for(String name:new String[]{key,key+"_time",key+"_endpoint"}){Object value=original.get(name);if(value instanceof String)edit.putString(name,(String)value);else if(value instanceof Long)edit.putLong(name,(Long)value);else edit.remove(name);}edit.commit();}
        log("LIMIT Cases 11/12 are locally intercepted synthetic source classification changes; actual production parsers/callbacks and pointer entry are used. No authenticated or live-platform claim.");
    }
    private void ownership(boolean take)throws Exception{
        Fixture f=new Fixture(item(take?2:3),page(answer("life-old","合成生命周期回答")));
        CountDownLatch done=new CountDownLatch(1);WebView[] first={null},second={null};int[] callbacks={0};String[] error={""};
        try{
            ui(()->{
                f.dynamic=new DynamicReader(activity,f.item,new Repository.Result<Document>(){
                    @Override public void success(Document d){callbacks[0]++;if(take){first[0]=f.dynamic.takeSource();second[0]=f.dynamic.takeSource();f.dynamic.close();}done.countDown();}
                    @Override public void failure(String reason){error[0]=reason;done.countDown();}
                });f.initial=(WebView)field(f.dynamic,"web");f.install(f.initial);
                check(f.dynamic.takeSource()==null,"Source cannot be taken before successful callback window");
            });
            check(done.await(10,TimeUnit.SECONDS),"Standalone DynamicReader callback within bound");
            ui(()->{
                check(callbacks[0]==1&&error[0].isEmpty(),"Standalone callback is production success exactly once");
                check(f.dynamic.takeSource()==null,"Source cannot be taken after callback returns");
                check(!(Boolean)field(f.dynamic,"ownsWeb")&&!(Boolean)field(f.dynamic,"delivering"),"Callback finally resets delivering and relinquishes ownership");
                if(take){check(first[0]==f.initial&&second[0]==null,"Successful callback can transfer exactly once");check(first[0].getParent()!=null,"Old owner close does not release transferred source");}
                else check(f.initial.getParent()==null,"Unclaimed source is automatically released after success");
            });
            if(take){
                CountDownLatch alive=new CountDownLatch(1);String[] title={null};AnswerStream[] owner={null};
                ui(()->{owner[0]=new AnswerStream(activity,f.item,first[0]);first[0].evaluateJavascript("document.title",v->{title[0]=v;alive.countDown();});});
                check(alive.await(3,TimeUnit.SECONDS)&&title[0]!=null&&title[0].contains("Synthetic reuse fixture"),"Transferred WebView remains executable after old-owner finally/close");
                ui(()->{ViewGroup host=activity.findViewById(android.R.id.content);check(first[0].getParent()==host&&host.indexOfChild(first[0])==0,"Continuation safely reattaches an already-parented source");owner[0].close();owner[0].close();check(first[0].getParent()==null,"New owner closes source idempotently");});
                first[0]=null;
            }
            summary(take?"02-single-take-window":"03-unclaimed-auto-release","生产回调次数 "+callbacks[0]+"；生产读取 "+f.reads+" 次；本地导航 "+f.requests+" 次");
        }finally{ui(()->{try{f.state();}finally{if(f.dynamic!=null)f.dynamic.close();if(first[0]!=null)SourceSurface.release(first[0]);}});}
    }
    private void permalink()throws Exception{
        Item root=item(4),link=new Item(Source.ZHIHU,root.title,root.url+"/answer/12345","");
        String old=answer("link-old","合成 permalink 已读回答"),next=answer("link-new","合成问题页第二回答");
        Fixture first=new Fixture(link,page(old));first.first();
        ui(()->{check(stream()==null,"Answer permalink does not reuse single-answer page");check(first.initial.getParent()==null,"Permalink initial source released after extraction");});
        Fixture continuation=new Fixture(root,page(old+next));
        ui(()->{call("loadMore",new Class<?>[0]);AnswerStream a=stream();if(a==null)throw new AssertionError("Fallback stream missing");continuation.install(source(a));check(source(a)!=first.initial,"Permalink creates a separate question source");});
        waitFor("permalink question fallback",10000,()->reading()!=null&&AnswerStream.answers(reading())==2&&!(Boolean)field(activity,"loadingMore"));
        ui(()->{check(continuation.requests==1,"Fallback navigates exactly once to normalized question");check(root.url.equals(source(stream()).getUrl()),"Fallback actual URL is the question root");check(text(reading()).contains("合成 permalink 已读回答")&&text(reading()).contains("合成问题页第二回答"),"Fallback production merge preserves permalink answer and appends second");ownReaderSafe();});picture("04-permalink-question-fallback");
    }
    private void changedQuestion()throws Exception{
        String old=answer("guard-old","合成原题已读回答"),wrong=answer("guard-wrong","合成错误题内容，不可追加");
        Fixture f=new Fixture(item(5),page(old),page(old),page(old+wrong));f.first();
        ui(()->{
            check(AnswerStream.canReuse(f.item,f.item.url+"?sort=default#answer"),"Eligibility ignores query/fragment for same question root");
            check(!AnswerStream.canReuse(f.item,f.item.url+"/answer/2"),"Loaded single-answer permalink is ineligible for reuse");
            check(!AnswerStream.canReuse(f.item,item(99).url)&&!AnswerStream.canReuse(f.item,"https://www.zhihu.com/signin")&&!AnswerStream.canReuse(f.item,"https://example.com/question/"+id+5),"Different question, login route and foreign host are ineligible");
        });
        CountDownLatch moved=new CountDownLatch(1);
        ui(()->f.initial.evaluateJavascript("history.replaceState(null,'','/question/"+id+99+"');location.href",v->moved.countDown()));
        check(moved.await(3,TimeUnit.SECONDS),"Synthetic same-origin source navigated to a different question via history API");
        f.more();waitFor("wrong-question completion",5000,()->reading()!=null&&"unavailable".equals(reading().moreStatus)&&!(Boolean)field(activity,"loadingMore"));
        ui(()->{check(AnswerStream.answers(reading())==1&&text(reading()).contains("合成原题已读回答")&&!text(reading()).contains("合成错误题"),"Wrong-question source is rejected without merging or losing already-read answer");check(reading().notice.contains("离开当前问题"),"Wrong-question notice explains why continuation stopped");String status=((TextView)field(activity,"readerStatus")).getText().toString();check(status.contains("离开当前问题")&&status.contains("重新读取")&&!status.contains("按钮重试"),"Native status agrees with unavailable source: re-read the current question, not retry the changed source");check(f.reads==2,"Changed URL rejected before another production HTML parse");ownReaderSafe();});picture("05-cross-question-rejected");
    }
    private void cancel()throws Exception{
        Fixture abandoned=new Fixture(item(6),page(""),page(answer("cancel-old","合成取消页，不可回写")));
        ui(abandoned::open);waitFor("first empty source inspection",5000,()->abandoned.reads>=1);
        Fixture replacement=new Fixture(item(7),page(answer("cancel-new","合成切换后的当前问题")));
        ui(replacement::open);waitFor("replacement answer",10000,()->reading()!=null&&text(reading()).contains("合成切换后的当前问题"));Thread.sleep(1500);
        ui(()->{check((Boolean)field(abandoned.dynamic,"ended")&&!(Boolean)field(abandoned.dynamic,"ownsWeb")&&abandoned.initial.getParent()==null,"Switch during initial extraction cancels and releases abandoned source");check(((Item)field(activity,"current")).url.equals(replacement.item.url)&&!text(reading()).contains("合成取消页"),"Late abandoned work cannot replace current question");check(stream()!=null&&source(stream())==replacement.initial,"Only current question owns retained continuation");ownReaderSafe();});picture("06-cancel-switch-preserves-current");
    }
    private void initialWrongQuestion()throws Exception{
        Fixture f=new Fixture(item(8),page(answer("initial-wrong","合成初始跳题正文，不可显示为请求的问题")));
        f.initialPath="/question/"+id+98;
        ui(f::open);waitFor("initial wrong-question source stops",10000,()->(Boolean)field(f.dynamic,"ended"));
        ui(()->{
            check(f.requests==1,"Initial redirected synthetic source was locally intercepted exactly once");
            check(reading()==null&&field(activity,"readerWeb")==null,"Initial wrong-question body is not rendered under the requested question");
            check(stream()==null,"Initial wrong-question source is not retained for continuation");
            check(f.initial.getParent()==null&&!(Boolean)field(f.dynamic,"ownsWeb"),"Initial wrong-question source is released on failure");
            check(((Item)field(activity,"current")).url.equals(f.item.url),"Rejected source cannot silently change the requested item");
        });picture("07-initial-cross-question-rejected");
    }
    private void immediateCancel()throws Exception{
        Fixture dynamicFixture=new Fixture(item(9),page(answer("immediate-dynamic","合成立即取消来源，不应读取")));
        Fixture streamFixture=new Fixture(item(10),page(answer("immediate-stream","合成立即取消续读，不应读取")));
        AtomicInteger dynamicCallbacks=new AtomicInteger(),streamCallbacks=new AtomicInteger();
        AnswerStream[] fresh={null};boolean[] dynamicUrlEmpty={false},streamUrlEmpty={false};
        CountDownLatch nextTurn=new CountDownLatch(1);
        try{
            ui(()->{
                dynamicFixture.dynamic=new DynamicReader(activity,dynamicFixture.item,new Repository.Result<Document>(){
                    @Override public void success(Document d){dynamicCallbacks.incrementAndGet();}
                    @Override public void failure(String reason){dynamicCallbacks.incrementAndGet();}
                });
                dynamicFixture.initial=(WebView)field(dynamicFixture.dynamic,"web");dynamicFixture.install(dynamicFixture.initial);
                String dynamicUrl=dynamicFixture.initial.getUrl();dynamicUrlEmpty[0]=dynamicUrl==null||dynamicUrl.equals("about:blank");
                dynamicFixture.dynamic.close();
                fresh[0]=new AnswerStream(activity,streamFixture.item);
                streamFixture.initial=source(fresh[0]);streamFixture.install(streamFixture.initial);
                Document previous=SourceParser.article(Source.ZHIHU,page(answer("immediate-old","合成已读回答，取消不应回调")),streamFixture.item.url);
                fresh[0].more(previous,new Repository.Result<Document>(){
                    @Override public void success(Document d){streamCallbacks.incrementAndGet();}
                    @Override public void failure(String reason){streamCallbacks.incrementAndGet();}
                });
                String streamUrl=streamFixture.initial.getUrl();streamUrlEmpty[0]=streamUrl==null||streamUrl.equals("about:blank");
                fresh[0].close();
                new Handler(Looper.getMainLooper()).post(nextTurn::countDown);
            });
            check(nextTurn.await(3,TimeUnit.SECONDS),"Immediate cancellation observed after next main-loop turn");
            // Allow asynchronous navigation notifications to surface; no timeout or original assertion relaxed.
            Thread.sleep(1200);
            ui(()->{
                Fixture.Trace d=dynamicFixture.traces.get(0),s=streamFixture.traces.get(0);
                check(dynamicUrlEmpty[0]&&dynamicFixture.requests==0&&d.intercepts.get()==0&&d.starts.get()==0&&d.commits.get()==0&&d.finishes.get()==0,"DynamicReader closed in construction turn never starts a source navigation");
                check(dynamicCallbacks.get()==0,"Immediately cancelled DynamicReader delivers no success/failure callback");
                check(dynamicFixture.reads==0&&d.ready.get()==0,"Immediately cancelled DynamicReader executes no synthetic source or parsing");
                check((Boolean)field(dynamicFixture.dynamic,"ended")&&!(Boolean)field(dynamicFixture.dynamic,"ownsWeb")&&dynamicFixture.initial.getParent()==null,"Immediately cancelled DynamicReader is ended and its source released");
                check(!((Handler)field(dynamicFixture.dynamic,"handler")).hasMessages(0),"DynamicReader close removes pending navigation and timeout callbacks");
                check(streamUrlEmpty[0]&&streamFixture.requests==0&&s.intercepts.get()==0&&s.starts.get()==0&&s.commits.get()==0&&s.finishes.get()==0,"Fresh AnswerStream closed in more() turn never starts a source navigation");
                check(streamCallbacks.get()==0,"Immediately cancelled AnswerStream delivers no success/failure callback");
                check(streamFixture.reads==0&&s.ready.get()==0,"Immediately cancelled AnswerStream executes no synthetic source or parsing");
                check((Boolean)field(fresh[0],"closed")&&!(Boolean)field(fresh[0],"busy")&&streamFixture.initial.getParent()==null,"Immediately cancelled AnswerStream is closed and its source released");
                check(!((Handler)field(fresh[0],"main")).hasMessages(0),"AnswerStream close removes pending navigation and timeout callbacks");
            });
            summary("08-immediate-cancel-no-navigation","同 UI turn 启动后取消\nDynamic 导航="+dynamicFixture.requests+" 回调="+dynamicCallbacks+"\nStream 导航="+streamFixture.requests+" 回调="+streamCallbacks+"\n已跨过下一轮主线程事件循环并额外观察 1.2 秒。");
        }finally{ui(()->{if(dynamicFixture.dynamic!=null)dynamicFixture.dynamic.close();if(fresh[0]!=null)fresh[0].close();});}
    }
    @Override public void onStart(){
        directory=new File(getTargetContext().getExternalFilesDir(null),"session-reuse-qa/run-"+System.currentTimeMillis());directory.mkdirs();
        try{
            log("SYNTHETIC ONLY. Actual Main.open/loadMore and production parsing/callbacks. Local WebViewClient interception; ES5 outerHTML sequence advances only on production reads. Case 09 explicitly releases its third-answer fixture only after the no-new result. No cookie/storage edits. Unique URLs avoid cached source content. Screenshots 01/04/05/06/07/09 show synthetic content or errors in actual reader; 02/03/08 are diagnostic summaries, not platform visual acceptance.");
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            journey("01-main-reuse",this::reuse);
            journey("02-single-transfer",()->ownership(true));
            journey("03-unclaimed-release",()->ownership(false));
            journey("04-permalink-fallback",this::permalink);
            journey("05-cross-question",this::changedQuestion);
            journey("06-cancel-switch",this::cancel);
            journey("07-initial-cross-question",this::initialWrongQuestion);
            journey("08-immediate-cancel",this::immediateCancel);
            journey("09-delayed-third-retry",this::delayedThirdAnswer);
            journey("10-video-shell-late-answer",this::videoShellThenAnswer);
            journey("11-video-replacement-new-answer",this::videoReplacementKeepsNewAnswer);
            journey("12-filtered-last-answer-cache-return",this::filteredLastAnswerCacheReturn);
        }catch(Throwable error){failures++;log("ERROR SETUP "+error);}
        finally{
            log("COMPLETED "+completed+" session reuse trials; checks="+checks+" failures="+failures);
            try(FileOutputStream out=new FileOutputStream(new File(directory,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception error){failures++;log("ERROR report write "+error);}
            Bundle result=new Bundle();result.putString("stream",report+"\nOutput: "+directory);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
