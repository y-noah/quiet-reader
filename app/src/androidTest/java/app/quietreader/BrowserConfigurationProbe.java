package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import static app.quietreader.Models.*;

/** One bounded public-source observation; never exports HTML, account storage or Cookie values. */
final class BrowserConfigurationProbe {
    private final Instrumentation test;
    private final String profile;
    private final String target;
    BrowserConfigurationProbe(Instrumentation test,String profile){this(test,profile,"");}
    BrowserConfigurationProbe(Instrumentation test,String profile,String target){this.test=test;this.profile=profile;this.target=target==null?"":target;}
    void run(){
        String database="test-ui-browser-config-"+UUID.randomUUID()+".db";
        StringBuilder report=new StringBuilder("LIVE DOUYIN BROWSER CONFIGURATION; no login/captcha actions or HTML export\n");
        Activity activity=null;WebView[] web={null};int errors=0;
        AtomicInteger status=new AtomicInteger(),networkError=new AtomicInteger(),commits=new AtomicInteger(),finishes=new AtomicInteger(),blockedMedia=new AtomicInteger();
        boolean blockImages="images".equals(profile)||"production".equals(profile),wide="wide".equals(profile)||"production".equals(profile);
        try{
            if(!Arrays.asList("normal","images","wide","production").contains(profile))throw new IllegalArgumentException("Unknown browser profile");
            if(!target.isEmpty()&&!DouyinArticle.sameContent(target,target))throw new IllegalArgumentException("Unsupported content target");
            report.append("profile=").append(profile).append(" blockImages=").append(blockImages).append(" wide=").append(wide).append(" allProfilesBlockMedia=true\n");
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Activity owner=activity;
            test.runOnMainSync(()->{
                web[0]=new WebView(owner);SourceSession.configure(owner,Source.DOUYIN,web[0]);WebSettings settings=web[0].getSettings();
                settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setBlockNetworkImage(blockImages);settings.setMediaPlaybackRequiresUserGesture(true);
                web[0].setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !UrlPolicy.loginAllowed(Source.DOUYIN,request.getUrl().toString());}
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String path=request.getUrl().getPath();if(!request.isForMainFrame()&&path!=null&&path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4s|webm|m3u8|mp3|m4a|aac|ts)$")){blockedMedia.incrementAndGet();return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}return null;}
                    @Override public void onPageCommitVisible(WebView view,String url){commits.incrementAndGet();}
                    @Override public void onPageFinished(WebView view,String url){finishes.incrementAndGet();}
                    @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(request.isForMainFrame())status.set(response.getStatusCode());}
                    @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())networkError.set(error.getErrorCode());}
                    @Override public void onReceivedSslError(WebView view,SslErrorHandler handler,android.net.http.SslError error){handler.cancel();networkError.set(-11);}
                });
                SourceSurface.attach(owner,web[0]);
                if(wide){android.view.ViewGroup.LayoutParams size=web[0].getLayoutParams();size.width=Math.round(1280*owner.getResources().getDisplayMetrics().density);size.height=Math.round(960*owner.getResources().getDisplayMetrics().density);web[0].setLayoutParams(size);settings.setUseWideViewPort(false);settings.setLoadWithOverviewMode(false);}
                web[0].loadUrl(target.isEmpty()?Source.DOUYIN.boardPage():target);
            });
            List<Item> rows=Collections.emptyList();JSONObject last=new JSONObject();Document body=null;boolean sameContent=false;String html="";long start=SystemClock.elapsedRealtime(),end=start+30_000;
            while(SystemClock.elapsedRealtime()<end){
                Thread.sleep(2500);CountDownLatch sampled=new CountDownLatch(1);AtomicReference<String> raw=new AtomicReference<>("");
                long sampleStart=SystemClock.elapsedRealtime();
                test.runOnMainSync(()->web[0].evaluateJavascript("JSON.stringify({html:document.documentElement.outerHTML.slice(0,4000000),loadedUrl:location.href,url:location.origin+location.pathname,title:document.title,width:innerWidth,ready:document.readyState})",value->{raw.set(value);sampled.countDown();}));
                if(!sampled.await(2,TimeUnit.SECONDS))continue;
                Object value=new JSONTokener(raw.get()).nextValue();if(value instanceof String){last=new JSONObject((String)value);html=last.optString("html");}
                if(target.isEmpty())try{rows=SourceParser.list(Source.DOUYIN,html);}catch(Exception ignored){}
                else{sameContent=DouyinArticle.sameContent(target,last.optString("loadedUrl"));if(sameContent)body=SourceParser.readingArticle(Source.DOUYIN,html,last.optString("loadedUrl"));}
                report.append("sampleAtMs=").append(SystemClock.elapsedRealtime()-start).append(" snapshotAndParseMs=").append(SystemClock.elapsedRealtime()-sampleStart).append(" htmlChars=").append(html.length()).append(" rows=").append(rows.size()).append(" bodyPresent=").append(body!=null&&body.canPresent()).append('\n');
                if(!rows.isEmpty()||body!=null&&body.canPresent()||status.get()>=400||networkError.get()!=0)break;
            }
            org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(html);
            report.append("elapsedMs=").append(SystemClock.elapsedRealtime()-start).append(" rows=").append(rows.size()).append(" htmlChars=").append(html.length()).append(" hotAnchors=").append(page.select("a[href*=/hot/]").size()).append(" ownedTitleAnchors=").append(page.select("a[href*=/hot/]:has(h3)").size()).append('\n');
            last.remove("html");last.remove("loadedUrl");report.append("page=").append(last).append('\n');
            if(!target.isEmpty()){
                report.append("sameContent=").append(sameContent).append(" flightScripts=").append(page.select("script:not([src])").stream().filter(s->s.data().contains("__pace_f")).count()).append('\n');
                if(body!=null)report.append("body hasContent=").append(body.hasContent()).append(" canPresent=").append(body.canPresent()).append(" loginRequired=").append(body.loginRequired).append(" sourceUnavailable=").append(body.sourceUnavailable).append(" videoFallback=").append(body.unsupportedVideo).append(" sections=").append(body.sections.size()).append(" notice=").append(body.notice).append('\n');
            }
            report.append("mainHttpError=").append(status).append(" mainNetworkError=").append(networkError).append(" commits=").append(commits).append(" finishes=").append(finishes).append(" blockedMedia=").append(blockedMedia).append('\n');
            for(int i=0;i<Math.min(3,rows.size());i++)report.append("topic=").append(rows.get(i).title).append(" url=").append(rows.get(i).url).append('\n');
            report.append("ready=").append(target.isEmpty()?!rows.isEmpty():body!=null&&body.canPresent()).append("; source observation only, not body/authentication certification\n");
        }catch(Throwable error){errors++;report.append("ERROR ").append(error.getClass().getSimpleName()).append('\n');}
        finally{Activity end=activity;test.runOnMainSync(()->{if(web[0]!=null)SourceSurface.release(web[0]);if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("probeErrors=").append(errors).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(errors==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
