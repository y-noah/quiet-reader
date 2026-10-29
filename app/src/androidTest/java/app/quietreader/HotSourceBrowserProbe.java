package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONTokener;
import static app.quietreader.Models.*;

/** Opt-in normal WebView retrieval. Never automates a captcha or login action. */
final class HotSourceBrowserProbe {
    private final Instrumentation instrumentation;
    private Activity activity;private WebView web;private final Source[] sources;private final String target;
    HotSourceBrowserProbe(Instrumentation instrumentation,String source){this(instrumentation,source,"");}
    HotSourceBrowserProbe(Instrumentation instrumentation,String source,String target){this.instrumentation=instrumentation;this.sources=source==null?new Source[]{Source.TIEBA,Source.BILIBILI,Source.DOUYIN}:new Source[]{Source.valueOf(source)};this.target=target==null?"":target;}
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    void run(){
        StringBuilder report=new StringBuilder("LIVE NORMAL BROWSER HOT SOURCE RETRIEVAL\nNo login/captcha actions; each source bounded to 30 seconds.\n");int ready=0,errors=0;
        try{
            activity=instrumentation.startActivitySync(new Intent(instrumentation.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            instrumentation.runOnMainSync(()->{try{
                ((Repository)field("repo")).cancelPending();((ArticlePreloader)field("preloader")).pause();((Handler)field("preloadHandler")).removeCallbacksAndMessages(null);
                DynamicBoard old=(DynamicBoard)field("dynamicBoard");if(old!=null)old.close();
            }catch(Exception ex){throw new IllegalStateException(ex);}});
            for(Source source:sources){
                if(!target.isEmpty()&&(sources.length!=1||source!=Source.DOUYIN||!DouyinArticle.sameContent(target,target)))throw new IllegalArgumentException("Unsupported observation target");
                instrumentation.runOnMainSync(()->{
                    web=new WebView(activity);SourceSession.configure(activity,source,web);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);web.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
                    web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !UrlPolicy.loginAllowed(source,r.getUrl().toString());}});
                    SourceSurface.attach(activity,web);web.loadUrl(!target.isEmpty()?target:source==Source.TIEBA?source.endpoint:source.boardPage());
                });
                List<Item> rows=Collections.emptyList();String html="";long end=SystemClock.elapsedRealtime()+30_000;
                while(SystemClock.elapsedRealtime()<end){
                    Thread.sleep(3000);AtomicReference<String> raw=new AtomicReference<>("");CountDownLatch read=new CountDownLatch(1);
                    instrumentation.runOnMainSync(()->web.evaluateJavascript(source==Source.TIEBA?"document.body.innerText.slice(0,4000000)":"document.documentElement.outerHTML.slice(0,4000000)",value->{raw.set(value);read.countDown();}));
                    if(!read.await(2,TimeUnit.SECONDS))continue;
                    Object decoded=new JSONTokener(raw.get()).nextValue();if(decoded instanceof String)html=(String)decoded;
                    if(!target.isEmpty()){if(html.contains("\"aweme\"")||html.contains("\\\"aweme\\\""))break;}
                    else try{rows=SourceParser.list(source,html);if(!rows.isEmpty())break;}catch(Exception ignored){}
                }
                if(!rows.isEmpty())ready++;
                report.append(source.label).append(" rows=").append(rows.size()).append(" htmlChars=").append(html.length()).append('\n');
                for(int n=0;n<Math.min(3,rows.size());n++)report.append("  ").append(rows.get(n).title).append('\n');
                File output=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"live-"+source.name().toLowerCase(Locale.ROOT)+(target.isEmpty()?"":"-content")+".html");
                try(FileOutputStream out=new FileOutputStream(output)){out.write(html.getBytes(StandardCharsets.UTF_8));}
                instrumentation.runOnMainSync(()->{SourceSurface.release(web);web=null;});
            }
        }catch(Exception ex){errors++;report.append("ERROR ").append(ex.getClass().getSimpleName()).append('\n');}
        finally{instrumentation.runOnMainSync(()->{if(web!=null)SourceSurface.release(web);if(activity!=null)activity.finish();});}
        report.append("ready=").append(ready).append("/").append(sources.length).append(" probeErrors=").append(errors).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());instrumentation.finish(errors==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}

