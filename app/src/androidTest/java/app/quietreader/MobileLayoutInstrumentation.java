package app.quietreader;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;
public final class MobileLayoutInstrumentation extends Instrumentation {
    private LoginActivity page;private File output;private boolean live,raw,autoplay;private String only="";private boolean diagnose;private volatile String douyinCase="";private int checks,failures;
    private final java.util.List<String> networkIssues=new java.util.concurrent.CopyOnWriteArrayList<>();
    private final StringBuilder report=new StringBuilder();
    interface Task{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);diagnose=args!=null&&"true".equals(args.getString("diagnose"));live=args!=null&&"true".equals(args.getString("live"));raw=args!=null&&"true".equals(args.getString("raw"));autoplay=args!=null&&"true".equals(args.getString("autoplay"));if(args!=null)only=args.getString("source","");start();}
    private void ui(Task task)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);}
    private void check(boolean ok,String label){android.util.Log.i("QR_MOBILE",(ok?"PASS ":"FAIL ")+label);checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private WebView web()throws Exception{Field f=LoginActivity.class.getDeclaredField("web");f.setAccessible(true);return (WebView)f.get(page);}
    private String js(String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};WebView w=web();ui(()->w.evaluateJavascript(script,s->{result[0]=s;done.countDown();}));if(!done.await(15,TimeUnit.SECONDS))throw new IOException("JS timeout");return result[0];}
    private String url(Source s){return switch(s){
        case BAIDU->"https://www.baidu.com/s?wd=%E9%83%91%E9%92%A6%E6%96%87%E4%B8%AD%E7%BD%91";
        case TIEBA->"https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=28366928&topic_name=%E4%B8%96%E7%BA%AA%E5%92%8C%E8%A7%A3";
        case BILIBILI->"https://www.bilibili.com/video/BV1VeHQ6tEaS/";
        default->"https://www.douyin.com/hot/2689461";
    };}
    private String selector(Source s){return switch(s){case BAIDU->"#content_left";case TIEBA->".topic-wrapper,.main-content,.pb-title-wrap,.pc-pb-reply-top";case BILIBILI->"#mirror-vdcon,#playerWrap";default->".leftContainer,.video-detail-container";};}
    private String fixture(Source s){
        String content=switch(s){
            case BAIDU->"<div id=wrapper><div id=container><div id=content_left><div><h3>百度搜索结果：长标题应当自动换行</h3><p>可读的搜索摘要与原文入口</p><a id=action href='#detail'>打开原文</a></div></div><div id=content_right>桌面侧栏</div></div></div>";
            case TIEBA->"<div class=topic-wrapper><div class=topic-header-info><h2 class=topic-title>贴吧话题长标题</h2></div><div class=topic-body><div class=main><ul class=thread-list><li><div class=center><a class=title id=action href='#detail'>相关帖子与讨论内容</a><div class=content>点击帖子继续阅读</div></div></li></ul></div></div></div>";
            case BILIBILI->"<div id=app><div id=mirror-vdcon><div class=left-container><div id=viewbox_report><h1>哔哩哔哩长视频标题需要正确换行</h1></div><div id=playerWrap><div id=bilibili-player><video></video></div></div><div id=arc_toolbar_report><button id=action>查看评论</button></div></div><div class=right-container>推荐侧栏</div></div></div>";
            default->"<div id=root><div class=playerControlHeight><div class=leftContainer><div class=video-detail-container><div><div class=xgplayer><video></video></div></div></div><div><h1>抖音视频完整描述需要换行显示</h1><button id=action>查看评论</button></div></div><div class=detailPage>桌面推荐栏</div></div></div>";
        };
        if(s==Source.DOUYIN&&!douyinCase.isEmpty()){
            content=content.replace("<video></video>","");
            String media=douyinCase.equals("untrusted")?"https://example.com/video.mp4":"https://v11-weba.douyinvod.com/fixture.mp4";
            String setup="const detail={awemeId:'123',desc:'Fixture title',authorInfo:{nickname:'Author'},video:{playAddr:[{src:"+org.json.JSONObject.quote(media)+"}]},status:{privateStatus:0},isPrivate:false,isFriendLimit:false};";
            if(douyinCase.equals("private"))setup+="detail.status.privateStatus=1;";
            if(douyinCase.equals("login"))setup+="detail.pcNeedLogin=true;";
            if(douyinCase.equals("paid"))setup+="detail.chargeInfo={price:10};";
            setup+="window.__pace_f=[[1,'7:'+JSON.stringify(['$','fixture',null,{aweme:{statusCode:0,detail},comment:{statusCode:0,comments:[{cid:'1',user:{nickname:'Reader'},text:'literal markup & text'}],cursor:20,hasMore:false}}])]];";
            content+="<script>"+setup+"</script>";
        }
        return "<html><head><meta name=viewport content='width=1280'><style>body{min-width:1100px}#wrapper,.topic-wrapper,#mirror-vdcon,.playerControlHeight{width:1100px;margin-left:100px}#content_left,.left-container,.leftContainer{width:800px}#playerWrap{width:800px;height:450px}button{height:44px}</style></head><body>"+content+"<div id=detail style='height:1800px'>后续内容</div><script>document.querySelector('#action').onclick=()=>document.body.dataset.clicked='yes';</script></body></html>";
    }
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b!=null){try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}}
    private void waitLayout()throws Exception{long end=System.currentTimeMillis()+(live?45000:6000);while(System.currentTimeMillis()<end){if(!js("document.documentElement.getAttribute('data-qr-mobile')").equals("null"))return;Thread.sleep(300);}}
    private void bounds(Source s,String name)throws Exception{
        check(js("document.documentElement.getAttribute('data-qr-mobile')").equals(org.json.JSONObject.quote(s.name())),name+" adapter activated");
        check(js("innerWidth<=600").equals("true"),name+" uses phone-sized CSS viewport");
        String selector=selector(s);
        check(js("(()=>{let nodes=Array.from(document.querySelectorAll('"+selector+"')).filter(e=>e.getBoundingClientRect().width>0);return nodes.length>0&&nodes.every(e=>{let r=e.getBoundingClientRect();return r.left>=-1&&r.right<=innerWidth+1;})})()").equals("true"),name+" main content and player fit viewport");
        report.append(name+" metrics ").append(js("JSON.stringify({width:innerWidth,scroll:document.documentElement.scrollWidth,scale:visualViewport.scale,title:document.title,text:document.body.innerText.slice(0,180),over:Array.from(document.querySelectorAll('#content_left>div,.topic-wrapper>div,.pc-pb-box,.pc-pb-box>.container,.image-text,.pb-title-wrap,.pb-content-wrap,.pc-pb-reply-list,.video-detail-container,.leftContainer>div,#mirror-vdcon>div')).filter(e=>{let r=e.getBoundingClientRect();return r.width>0&&r.right>innerWidth+2&&getComputedStyle(e).position!=='absolute'&&getComputedStyle(e).position!=='fixed'}).slice(0,18).map(e=>({tag:e.tagName,id:e.id,c:e.className,w:Math.round(e.getBoundingClientRect().width)}))})")).append('\n');
    }
    @Override public void callActivityOnCreate(Activity a,Bundle state){
        super.callActivityOnCreate(a,state);
        if(live&&a instanceof LoginActivity)try{
            page=(LoginActivity)a;WebView.setWebContentsDebuggingEnabled(true);WebView w=web();if(autoplay)w.getSettings().setMediaPlaybackRequiresUserGesture(false);WebViewClient delegate=w.getWebViewClient();
            w.setWebViewClient(new WebViewClient(){
                @Override public void onPageStarted(WebView v,String u,Bitmap b){delegate.onPageStarted(v,u,b);}
                @Override public void onPageCommitVisible(WebView v,String u){delegate.onPageCommitVisible(v,u);}
                @Override public void onPageFinished(WebView v,String u){delegate.onPageFinished(v,u);}
                @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(v,r);}
                @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(networkIssues.size()<25)networkIssues.add(response.getStatusCode()+" "+r.getUrl().getHost()+r.getUrl().getPath());delegate.onReceivedHttpError(v,r,response);}
                @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error){if(networkIssues.size()<25)networkIssues.add(error.getErrorCode()+" "+r.getUrl().getHost()+r.getUrl().getPath());delegate.onReceivedError(v,r,error);}
            });
        }catch(Exception e){throw new RuntimeException(e);}
        if(!live&&a instanceof LoginActivity)try{
            page=(LoginActivity)a;Source source=Source.valueOf(a.getIntent().getStringExtra("source"));WebView w=web();w.stopLoading();WebViewClient delegate=w.getWebViewClient();
            w.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(fixture(source).getBytes(StandardCharsets.UTF_8)));}
                @Override public void onPageStarted(WebView view,String u,Bitmap icon){delegate.onPageStarted(view,u,icon);}
                @Override public void onPageCommitVisible(WebView view,String u){delegate.onPageCommitVisible(view,u);}
                @Override public void onPageFinished(WebView view,String u){delegate.onPageFinished(view,u);}
            });w.loadUrl(url(source));
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private void tap(String selector)throws Exception{
        js("document.querySelector("+org.json.JSONObject.quote(selector)+").scrollIntoView({block:'center'})");Thread.sleep(250);
        String raw=js("JSON.stringify((()=>{let r=document.querySelector("+org.json.JSONObject.quote(selector)+").getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})())");
        org.json.JSONObject point=new org.json.JSONObject((String)new org.json.JSONTokener(raw).nextValue());
        int[] location=new int[2];float[] scale={1};ui(()->{web().getLocationOnScreen(location);scale[0]=web().getScale();});
        float x=location[0]+(float)point.getDouble("x")*scale[0],y=location[1]+(float)point.getDouble("y")*scale[0];long now=SystemClock.uptimeMillis();
        sendPointerSync(android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0));sendPointerSync(android.view.MotionEvent.obtain(now,now+80,android.view.MotionEvent.ACTION_UP,x,y,0));
    }
    private void fallbackFixtures()throws Exception{
        for(String scenario:new String[]{"public","private","login","paid","untrusted"}){
            douyinCase=scenario;ui(()->web().loadUrl(url(Source.DOUYIN)));waitLayout();Thread.sleep(2400);
            boolean expected=scenario.equals("public");
            check(js("!!document.querySelector('#qr-douyin-reader')").equals(String.valueOf(expected)),"DOUYIN fallback eligibility "+scenario);
            if(expected){
                check(js("document.querySelectorAll('#qr-douyin-reader').length===1&&!document.querySelector('#qr-douyin-player video')").equals("true"),"DOUYIN creates one reader without eager media loading");
                check(js("document.querySelectorAll('#qr-douyin-reader .qr-douyin-comment').length===1").equals("true"),"DOUYIN source comments rendered");
                js("history.replaceState({},'', '/video/123');document.querySelector('#root').replaceChildren()");Thread.sleep(300);
                check(js("!!document.querySelector('#qr-douyin-reader h1')").equals("true"),"DOUYIN reader survives source SPA replacement");
                for(int n=0;n<3;n++)js(SourceMobileLayout.script(Source.DOUYIN,true));
                check(js("document.querySelectorAll('#qr-douyin-reader').length===1").equals("true"),"DOUYIN callbacks do not duplicate controls");
                js(SourceMobileLayout.script(Source.DOUYIN,false));
                check(js("!document.querySelector('#qr-douyin-reader')&&!document.documentElement.hasAttribute('data-qr-douyin-fallback')").equals("true"),"DOUYIN original layout restores and releases fallback");
                js(SourceMobileLayout.script(Source.DOUYIN,true));Thread.sleep(1200);
                check(js("!!document.querySelector('#qr-douyin-reader')").equals("true"),"DOUYIN phone reader restores after source content has disappeared");
            }
        }
        douyinCase="";
    }
    private void run(Source s)throws Exception{
        page=(LoginActivity)startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source",s.name()).putExtra("url",url(s)).putExtra("browse",true).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        if(diagnose){android.util.Log.i("QR_DIAG","Holding before any test gesture");Thread.sleep(600000);}
        if(raw){ui(()->{Field f=LoginActivity.class.getDeclaredField("mobileLayout");f.setAccessible(true);f.setBoolean(page,false);web().getSettings().setLoadWithOverviewMode(true);web().reload();});Thread.sleep(18000);}else waitLayout();Thread.sleep(live?3500:400);bounds(s,s.name());shot(s.name());
        if(live){
            if((s==Source.DOUYIN||s==Source.BILIBILI)&&js("!!document.querySelector('.video-detail-container,#playerWrap')").equals("true")){
                String rect=js("JSON.stringify((()=>{let e=document.querySelector('#qr-douyin-player button')||document.querySelector('.video-detail-container,#playerWrap'),r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2}})())");
                org.json.JSONObject point=new org.json.JSONObject((String)new org.json.JSONTokener(rect).nextValue());
                int[] location=new int[2];float[] scale={1};ui(()->{web().getLocationOnScreen(location);scale[0]=web().getScale();});
                float x=location[0]+(float)point.getDouble("x")*scale[0],y=location[1]+(float)point.getDouble("y")*scale[0];long now=SystemClock.uptimeMillis();
                sendPointerSync(android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0));
                sendPointerSync(android.view.MotionEvent.obtain(now,now+80,android.view.MotionEvent.ACTION_UP,x,y,0));Thread.sleep(5000);
                if(s==Source.DOUYIN){long until=System.currentTimeMillis()+40000;while(System.currentTimeMillis()<until){if(js("!!document.querySelector('.video-detail-container video')").equals("true"))break;Thread.sleep(750);}}
                Thread.sleep(1500);shot(s+"-tapped");
            }
            if(s==Source.DOUYIN||s==Source.BILIBILI){
                String media=s==Source.DOUYIN?"#qr-douyin-player video,.video-detail-container video":"#playerWrap video";
                long until=System.currentTimeMillis()+20000;
                while(System.currentTimeMillis()<until&&!js("(()=>{let v=document.querySelector('"+media+"');return !!v&&v.readyState>=2&&v.videoWidth>0&&!v.paused})()").equals("true"))Thread.sleep(500);
                check(js("(()=>{let v=document.querySelector('"+media+"');return !!v&&v.readyState>=2&&v.videoWidth>0&&!v.paused})()").equals("true"),s+" visible player decodes real video");
                js("window.qrPlaybackTime=document.querySelector('"+media+"')?.currentTime||0");Thread.sleep(2200);
                check(js("(document.querySelector('"+media+"')?.currentTime||0)>window.qrPlaybackTime+0.5").equals("true"),s+" playback time advances after real touch");
            }
            if(s==Source.DOUYIN){check(js("!!document.querySelector('#qr-douyin-reader h1')&&document.querySelectorAll('#qr-douyin-reader .qr-douyin-comment').length>0").equals("true"),"DOUYIN playback retains topic and comments across canonical route");bounds(s,"DOUYIN-after-navigation");shot("DOUYIN-loaded");report.append("DOUYIN late DOM ").append(js("JSON.stringify({path:location.pathname,text:document.body.innerText.slice(0,350),video:Array.from(document.querySelectorAll('video')).map(e=>[e,e.parentElement,e.parentElement.parentElement,e.parentElement.parentElement.parentElement].map(x=>({c:x.className,tag:x.tagName}))),h1:Array.from(document.querySelectorAll('h1')).map(e=>[e,e.parentElement,e.parentElement.parentElement].map(x=>({c:x.className,tag:x.tagName})))})")).append((char)10);}
            if(s==Source.DOUYIN)report.append("DOUYIN player structure ").append(js("JSON.stringify(Array.from(document.querySelectorAll('.video-detail-container,.video-detail-container *')).slice(0,40).map(e=>({tag:e.tagName,c:e.className,text:e.children.length===0?e.textContent.slice(0,80):''})))")).append((char)10);
            report.append(s+" video ").append(js("JSON.stringify(Array.from(document.querySelectorAll('video')).map(v=>({ready:v.readyState,paused:v.paused,time:v.currentTime,w:v.videoWidth,h:v.videoHeight,error:v.error&&v.error.code})))")).append((char)10);
            if(s==Source.DOUYIN&&js("!!document.querySelector('#qr-douyin-reader')").equals("true")){
                js("window.qrCommentCount=document.querySelectorAll('#qr-douyin-reader .qr-douyin-comment').length;document.querySelector('#qr-douyin-reader>button')?.click()");
                long commentUntil=System.currentTimeMillis()+22000;
                while(System.currentTimeMillis()<commentUntil&&!js("document.querySelectorAll('#qr-douyin-reader .qr-douyin-comment').length>window.qrCommentCount").equals("true"))Thread.sleep(500);
                check(js("document.querySelectorAll('#qr-douyin-reader .qr-douyin-comment').length>window.qrCommentCount").equals("true"),"DOUYIN comments paginate beyond initial page");
                js("document.querySelector('#qr-douyin-reader .qr-douyin-comment button')?.click()");
                long replyUntil=System.currentTimeMillis()+20000;
                while(System.currentTimeMillis()<replyUntil&&!js("!!document.querySelector('.qr-douyin-comment .qr-douyin-comment')").equals("true"))Thread.sleep(500);
                check(js("!!document.querySelector('.qr-douyin-comment .qr-douyin-comment')").equals("true"),"DOUYIN comment replies load");
                js("document.querySelector('#qr-douyin-reader h2').scrollIntoView()");Thread.sleep(400);shot("DOUYIN-comments");
                js("(()=>{let b=document.createElement('button');b.id='qr-test-fullscreen';b.style='position:fixed;bottom:20px;right:20px;z-index:2147483647';b.textContent='Fullscreen QA';b.onclick=()=>{window.qrFullscreenClicked=true;document.querySelector('#qr-douyin-player video').requestFullscreen().catch(e=>window.qrFullscreenError=e.message)};document.querySelector('#qr-douyin-reader').prepend(b)})()");
                shot("DOUYIN-fullscreen-button");tap("#qr-test-fullscreen");Thread.sleep(1000);report.append("Fullscreen diagnostics ").append(js("JSON.stringify({clicked:window.qrFullscreenClicked,error:window.qrFullscreenError,element:document.fullscreenElement&&document.fullscreenElement.tagName,enabled:document.fullscreenEnabled})")).append((char)10);
                Field fullscreen=LoginActivity.class.getDeclaredField("fullscreen");fullscreen.setAccessible(true);
                check(fullscreen.get(page)!=null,"DOUYIN actual media fullscreen opens");
                if(fullscreen.get(page)!=null)ui(page::onBackPressed);Thread.sleep(400);
                check(fullscreen.get(page)==null&&js("!!document.querySelector('#qr-douyin-reader')").equals("true"),"DOUYIN back exits fullscreen into reader");
                js("document.getElementById('qr-test-fullscreen')?.remove();window.qrBeforeReload=true");
                ui(()->page.onActivityResult(200,Activity.RESULT_OK,null));
                long recoveryUntil=System.currentTimeMillis()+45000;
                while(System.currentTimeMillis()<recoveryUntil&&!js("window.qrBeforeReload!==true&&!!document.querySelector('#qr-douyin-reader')").equals("true"))Thread.sleep(500);
                check(js("window.qrBeforeReload!==true&&!!document.querySelector('#qr-douyin-reader')").equals("true"),"DOUYIN login return reloads topic and recovers mobile reader");
                check(js("!document.querySelector('#qr-douyin-player video')").equals("true"),"DOUYIN return does not autoplay or retain old media");

            }
            js("(()=>{let e=document.querySelector('.pc-pb-box,.route-scroll-container');if(e)e.scrollTop=650;else scrollTo(0,650)})()");Thread.sleep(3000);shot(s+"-scrolled");
        }
        if(!live){
            check(js("document.querySelector('#action').click();document.body.dataset.clicked").equals(org.json.JSONObject.quote("yes")),s+" original event handler retained");
            ui(()->{android.view.ViewGroup.LayoutParams lp=web().getLayoutParams();lp.width=Math.round(320*page.getResources().getDisplayMetrics().density);web().setLayoutParams(lp);});Thread.sleep(400);
            bounds(s,s+" narrow320");
            check(js("innerWidth<=322").equals("true"),s+" narrow screen has no desktop overflow");
            ui(()->{android.view.ViewGroup.LayoutParams lp=web().getLayoutParams();lp.width=-1;web().setLayoutParams(lp);});Thread.sleep(300);
            String before=js("visualViewport.scale");
            js("window.qrSavedBody=document.body.innerHTML;document.body.innerHTML='<p>页面正在切换</p>'");Thread.sleep(250);
            check(js("document.documentElement.hasAttribute('data-qr-mobile')").equals("true"),s+" SPA replacement does not restore desktop viewport");
            js("document.body.innerHTML=window.qrSavedBody;delete window.qrSavedBody");Thread.sleep(250);
            for(int n=0;n<5;n++)js(SourceMobileLayout.script(s,true));
            Thread.sleep(500);check(before.equals(js("visualViewport.scale")),s+" repeated callbacks do not change scale");
            js(SourceMobileLayout.script(s,false));check(js("document.documentElement.hasAttribute('data-qr-mobile')").equals("false"),s+" original layout can be restored");
            check(js("document.querySelector('meta[name=viewport]').content").equals(org.json.JSONObject.quote("width=1280")),s+" original viewport restored");
            js(SourceMobileLayout.script(s,true));bounds(s,s+" restored");
            js("scrollTo(0,500)");check(!js("scrollY").equals("0"),s+" vertical scrolling works");
        }else if(s==Source.TIEBA){
            ui(()->web().loadUrl("https://tieba.baidu.com/p/10977988189"));waitLayout();Thread.sleep(3500);bounds(s,"TIEBA-post");shot("TIEBA-post");
        }
        if(!live&&s==Source.DOUYIN)fallbackFixtures();
        ui(page::finish);page=null;
    }
    @Override public void onStart(){try{
        output=new File(getTargetContext().getExternalFilesDir(null),"mobile-layout-qa/"+(live?"live-":"fixture-")+System.currentTimeMillis());output.mkdirs();
        for(Source s:new Source[]{Source.BAIDU,Source.TIEBA,Source.BILIBILI,Source.DOUYIN})if(only.isEmpty()||s.name().equals(only))run(s);
    }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
    finally{try{if(page!=null)ui(page::finish);}catch(Exception ignored){}
        report.append("Network diagnostics ").append(networkIssues).append((char)10);
        report.append("CHECKS ").append(checks).append(" FAILURES ").append(failures).append('\n');
        try{java.nio.file.Files.write(new File(output,"report.txt").toPath(),report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
        Bundle result=new Bundle();result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }}
}
