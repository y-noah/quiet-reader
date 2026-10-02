package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.webkit.*;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Read-only timing observer. Never persists HTML, cookies, or extracted private text. */
public final class LatencyInstrumentation extends Instrumentation {
    private Activity activity;
    private File directory;
    private final StringBuilder report=new StringBuilder();
    private final Handler main=new Handler(Looper.getMainLooper());
    private int successes,failures;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private synchronized void log(String value){report.append(value).append('\n');}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static String hash(String value)throws Exception{
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();
    }
    private static JSONObject fingerprint(Document d)throws Exception{
        StringBuilder body=new StringBuilder(),text=new StringBuilder(),urls=new StringBuilder(),paths=new StringBuilder();int chars=0,images=0,answers=0,answerChars=0;
        for(Block b:d.blocks){body.append(b.type).append('\0').append(b.value).append('\n');if(b.type.equals("text"))text.append("text\0").append(b.value.length()).append(':').append(b.value).append('\n');if(b.type.equals("image")){urls.append(b.value).append('\n');paths.append(imagePath(b.value)).append('\n');images++;}else chars+=b.value.length();for(InlineImage i:b.inlineImages){urls.append(i.url).append('\n');paths.append(imagePath(i.url)).append('\n');images++;}}
        for(Section s:d.sections)if(s.answer){boolean readable=false;for(Block b:s.blocks){if(b.type.equals("text")&&!b.value.trim().isEmpty()){answerChars+=b.value.length();readable=true;}if(b.type.equals("image")||!b.inlineImages.isEmpty())readable=true;}if(readable)answers++;}
        return new JSONObject().put("blocks",d.blocks.size()).put("sections",d.sections.size()).put("characters",chars).put("images",images).put("related",d.related.size()).put("actualAnswerSections",answers).put("answerTextChars",answerChars).put("semanticSha256",DocumentFingerprint.of(d)).put("contentSha256",hash(body.toString())).put("textOnlySha256",hash(text.toString())).put("imageUrlsSha256",hash(urls.toString())).put("imageHostPathSha256",hash(paths.toString()));
    }
    private static String imagePath(String url){android.net.Uri uri=android.net.Uri.parse(url);return String.valueOf(uri.getHost()).toLowerCase(java.util.Locale.ROOT)+String.valueOf(uri.getEncodedPath());}
    private final class Trial {
        final Source source;final String url;final int attempt;final CountDownLatch done=new CountDownLatch(1);
        final ExecutorService parser=Executors.newSingleThreadExecutor();
        volatile boolean stopped;long start;DynamicReader reader;WebView web;int samples,completedSamples;volatile long firstReady=-1,firstContent=-1,firstAnswer=-1,callbackAt=-1,pollDue=-1;String result="";
        long firstCommit=-1,firstFinished=-1;volatile int evalInFlight,parseInFlight;
        final java.util.List<JSONObject> observations=new java.util.ArrayList<>();
        String observedProductionState="";long lastProductionObserved=-1;Document lastBest;String bestHash="";
        Trial(Source s,String u,int a){source=s;url=u;attempt=a;}
        long elapsed(){return SystemClock.elapsedRealtime()-start;}
        void event(String name){log(source+" #"+attempt+" "+name+" ms="+elapsed());}
        boolean fixedSource(String candidate){
            if(!UrlPolicy.belongs(source,candidate))return false;
            android.net.Uri expected=android.net.Uri.parse(url),actual=android.net.Uri.parse(candidate);
            return java.util.Objects.equals(expected.getHost(),actual.getHost())&&java.util.Objects.equals(expected.getPath(),actual.getPath());
        }
        void observeProduction(String where){
            if(reader==null)return;long at=elapsed(),costStart=SystemClock.elapsedRealtime();
            try{
                Document best=(Document)field(reader,"best");if(best!=lastBest){lastBest=best;bestHash=best==null?"":DocumentFingerprint.of(best);}
                String state="attempts="+field(reader,"attempts")+" lastSnapshot="+field(reader,"lastSnapshot")+" bestSemanticSha256="+bestHash+" ended="+field(reader,"ended");
                if(!state.equals(observedProductionState)){log(source+" #"+attempt+" PROD_OBSERVED atMs="+at+" sincePreviousObservationMs="+(lastProductionObserved<0?-1:at-lastProductionObserved)+" where="+where+" "+state+" observerCostMs="+(SystemClock.elapsedRealtime()-costStart)+" (change occurred after previous observation, not exact inspect/parse timing)");observedProductionState=state;}
                lastProductionObserved=at;
            }catch(Exception e){event("productionObservationError="+e.getClass().getSimpleName());}
        }
        void schedulePoll(){if(!stopped){pollDue=elapsed()+400;main.postDelayed(poll,400);}}
        synchronized void observationGap(){if(!stopped)observations.add(new JSONObject());}
        synchronized void summarizeFinal(Document d,String finalHash){
            long firstSame=-1,firstPair=-1;boolean previousSame=false;
            for(JSONObject o:observations){boolean same=fixedSource(d.url)&&fixedSource(o.optString("sourceUrl"))&&o.optString("semanticSha256").equals(finalHash);if(same&&firstSame<0)firstSame=o.optLong("receivedMs");if(same&&previousSame&&firstPair<0)firstPair=o.optLong("receivedMs");previousSame=same;}
            log(source+" #"+attempt+" OBSERVER_FINAL_MATCH sameFixedUrl="+fixedSource(d.url)+" firstSameAsFinalMs="+firstSame+" firstConsecutiveFinalPairSecondMs="+firstPair+" callbackMinusFirstSameMs="+(firstSame<0?-1:callbackAt-firstSame)+" callbackMinusObservedPairMs="+(firstPair<0?-1:callbackAt-firstPair)+" completedSamples="+completedSamples+" receivedSamples="+samples+" evalInFlightAtStop="+evalInFlight+" parseInFlightAtStop="+parseInFlight+" (observer samples only; not production stability or completeness proof)");
        }
        boolean ended(){try{return stopped||reader==null||(Boolean)field(reader,"ended");}catch(Exception e){return true;}}
        void begin(){
            start=SystemClock.elapsedRealtime();event("construct.start");
            reader=new DynamicReader(activity,new Item(source,"Fixed public latency probe",url,""),new Repository.Result<Document>(){
                @Override public void success(Document d){callbackAt=elapsed();observeProduction("successCallback");stopped=true;successes++;main.removeCallbacks(poll);parser.shutdownNow();try{JSONObject f=fingerprint(d);result=f.toString();log(source+" #"+attempt+" SUCCESS ms="+callbackAt+" final="+result);summarizeFinal(d,f.getString("semanticSha256"));}catch(Exception e){log("fingerprint error "+e.getClass().getSimpleName());}done.countDown();}
                @Override public void failure(String reason){callbackAt=elapsed();observeProduction("failureCallback");stopped=true;failures++;main.removeCallbacks(poll);parser.shutdownNow();result="FAILURE";log(source+" #"+attempt+" FAILURE ms="+callbackAt+" reason="+reason);done.countDown();}
            });
            try{
                web=(WebView)field(reader,"web");WebViewClient original=web.getWebViewClient();
                log(source+" #"+attempt+" blockNetworkImage="+web.getSettings().getBlockNetworkImage());
                web.setWebViewClient(new WebViewClient(){
                    @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return original.shouldOverrideUrlLoading(w,r);}
                    @Override public boolean shouldOverrideUrlLoading(WebView w,String u){return original.shouldOverrideUrlLoading(w,u);}
                    @Override public void onPageStarted(WebView w,String u,Bitmap icon){event("pageStarted fixedSource="+fixedSource(u));original.onPageStarted(w,u,icon);observeProduction("pageStarted");}
                    @Override public void onPageCommitVisible(WebView w,String u){if(fixedSource(u)&&firstCommit<0)firstCommit=elapsed();event("pageCommitVisible fixedSource="+fixedSource(u));original.onPageCommitVisible(w,u);observeProduction("pageCommitVisible");}
                    @Override public void onPageFinished(WebView w,String u){if(fixedSource(u)&&firstFinished<0)firstFinished=elapsed();event("pageFinished fixedSource="+fixedSource(u));original.onPageFinished(w,u);observeProduction("pageFinished");}
                    @Override public void onLoadResource(WebView w,String u){original.onLoadResource(w,u);}
                    @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return original.shouldInterceptRequest(w,r);}
                    @Override public WebResourceResponse shouldInterceptRequest(WebView w,String u){return original.shouldInterceptRequest(w,u);}
                    @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){event("error main="+r.isForMainFrame());original.onReceivedError(w,r,e);}
                    @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){original.onReceivedHttpError(w,r,e);}
                    @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){event("sslError");original.onReceivedSslError(w,h,e);}
                    @Override public void onReceivedHttpAuthRequest(WebView w,HttpAuthHandler h,String host,String realm){original.onReceivedHttpAuthRequest(w,h,host,realm);}
                    @Override public void onReceivedClientCertRequest(WebView w,ClientCertRequest r){original.onReceivedClientCertRequest(w,r);}
                    @Override public void doUpdateVisitedHistory(WebView w,String u,boolean reload){original.doUpdateVisitedHistory(w,u,reload);}
                    @Override public void onScaleChanged(WebView w,float old,float current){original.onScaleChanged(w,old,current);}
                    @Override public boolean onRenderProcessGone(WebView w,RenderProcessGoneDetail d){return original.onRenderProcessGone(w,d);}
                });
                event("construct.end.proxyInstalled");observeProduction("constructed");pollDue=elapsed();main.post(poll);
            }catch(Exception e){log("observer setup "+e);stop();done.countDown();}
        }
        final Runnable poll=()->sample();
        void sample(){
            if(ended())return;long requested=elapsed();observeProduction("observerRequest");evalInFlight=1;
            log(source+" #"+attempt+" OBSERVER_REQUEST ms="+requested+" dueMs="+pollDue+" mainScheduleLateMs="+(pollDue<0?-1:Math.max(0,requested-pollDue)));
            web.evaluateJavascript("(function(){var h=document.documentElement?document.documentElement.outerHTML:'';return JSON.stringify({href:location.href,ready:document.readyState,length:h.length,html:h.length*3<4194304?h:null});})()",raw->{
                evalInFlight=0;if(ended())return;long received=elapsed();observeProduction("observerReceived");
                try{
                    Object decoded=new JSONTokener(raw).nextValue();if(!(decoded instanceof String)){event("nonStringSnapshotSkipped");observationGap();schedulePoll();return;}
                    JSONObject observation=new JSONObject((String)decoded);String ready=observation.optString("ready");
                    if(!fixedSource(observation.optString("href"))){event("nonFixedSourceSnapshotSkipped");observationGap();schedulePoll();return;}
                    if(firstReady<0&&(ready.equals("interactive")||ready.equals("complete"))){firstReady=received;log(source+" #"+attempt+" firstNonLoadingReady ms="+received+" state="+ready+" (poll upper bound; not exact DOMContentLoaded)");}
                    if(observation.isNull("html")){event("HTML_OVER_4M_SKIPPED");observationGap();schedulePoll();return;}
                    final String html=observation.getString("html"),sampleUrl=observation.getString("href");final int n=++samples;final long enqueued=elapsed();parseInFlight=1;
                    parser.execute(()->{
                        long parseStart=elapsed();
                        try{
                            Document d=SourceParser.article(source,html,url);long parsedAt=elapsed();
                            JSONObject f=fingerprint(d);long hashedAt=elapsed();
                            synchronized(Trial.this){
                                if(stopped)return;
                                completedSamples++;
                                observations.add(new JSONObject().put("receivedMs",received).put("sourceUrl",sampleUrl).put("semanticSha256",f.getString("semanticSha256")));
                                if(d.hasContent()&&firstContent<0){firstContent=received;log(source+" #"+attempt+" FIRST_CONTENT snapshotMs="+received+" parseFinishedMs="+parsedAt+" fingerprint="+f);}
                                if(f.getInt("actualAnswerSections")>0&&firstAnswer<0){firstAnswer=received;log(source+" #"+attempt+" FIRST_ACTUAL_ANSWER snapshotMs="+received+" parseFinishedMs="+parsedAt+" answerSections="+f.getInt("actualAnswerSections")+" answerTextChars="+f.getInt("answerTextChars"));}
                                log(source+" #"+attempt+" sample="+n+" requestMs="+requested+" receivedMs="+received+" evalMs="+(received-requested)+" parserEnqueuedMs="+enqueued+" parseStartMs="+parseStart+" parserQueueMs="+(parseStart-enqueued)+" parseFinishedMs="+parsedAt+" parseMs="+(parsedAt-parseStart)+" observerHashMs="+(hashedAt-parsedAt)+" ready="+ready+" htmlChars="+html.length()+" fingerprint="+f);
                            }
                        }catch(Exception e){if(!stopped){observationGap();log(source+" observerParseError="+e.getClass().getSimpleName());}}
                        finally{parseInFlight=0;schedulePoll();}
                    });
                }catch(Exception e){parseInFlight=0;if(!stopped){observationGap();log(source+" observerDecodeError="+e.getClass().getSimpleName());schedulePoll();}}
            });
        }
        void stop(){stopped=true;main.removeCallbacks(poll);parser.shutdownNow();if(reader!=null)reader.close();}
    }
    private void runTrial(Source s,String url,int attempt)throws Exception{
        Trial t=new Trial(s,url,attempt);runOnMainSync(t::begin);
        if(!t.done.await(28,TimeUnit.SECONDS)){runOnMainSync(t::stop);failures++;log(s+" #"+attempt+" OBSERVER_TIMEOUT");}
        log(s+" #"+attempt+" SUMMARY commitMs="+t.firstCommit+" finishedMs="+t.firstFinished+" readyMs="+t.firstReady+" contentSnapshotMs="+t.firstContent+" actualAnswerSnapshotMs="+t.firstAnswer+" callbackMs="+t.callbackAt+" gapMs="+(t.firstContent<0?-1:t.callbackAt-t.firstContent)+" answerGapMs="+(t.firstAnswer<0?-1:t.callbackAt-t.firstAnswer)+" receivedSamples="+t.samples+" completedSamples="+t.completedSamples);
        runOnMainSync(()->{TextView label=new TextView(activity);label.setTextSize(18);label.setTextColor(0xffe3eae5);label.setBackgroundColor(0xff141a18);label.setPadding(28,44,28,28);label.setText("只读时间诊断，不是正文视觉验收\n"+s+" / "+attempt+"\n"+url+"\nready: "+t.firstReady+" ms\nfirst content: "+t.firstContent+" ms\ncallback: "+t.callbackAt+" ms\n"+t.result);activity.setContentView(label);});
        Thread.sleep(300);Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap!=null)try(FileOutputStream out=new FileOutputStream(new File(directory,s+"-"+attempt+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        Thread.sleep(600);
    }
    @Override public void onStart(){
        directory=new File(getTargetContext().getExternalFilesDir(null),"latency-qa/run-"+System.currentTimeMillis());directory.mkdirs();
        try{
            android.content.pm.PackageInfo installed=getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0);
            log("Installed production version="+installed.versionName+" code="+installed.versionCode+"; DynamicReader direct fixed URLs; same-session sequential twice per source; no cookie/storage clear; NOT cold-cache/release timing.");
            log("Observer installs forwarding client in constructor main-thread turn; extra 400ms serialized DOM polling, SourceParser and hashing add overhead and may contend with production. OuterHTML UTF-16 length*3 <4MiB conservative UTF-8 cap; stores counts/hashes only. Fixed source host/path required; no HTML/Cookie/account data persisted. Source JS already enabled by production; no DOM mutations. Stops at callback/destroy. Production attempts/lastSnapshot/best are read-only interval observations, not exact inspect events. firstSameAsFinal uses same fixed URL plus production DocumentFingerprint semantic SHA; observer stability never replaces production decision, nor proves full-source completeness. This direct-reader path excludes HTTP fallback, Main rendering and AnswerStream reuse latency.");
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(5000);
            for(int n=1;n<=2;n++)runTrial(Source.TIEBA,"https://tieba.baidu.com/p/11061609054",n);
            for(int n=1;n<=2;n++)runTrial(Source.ZHIHU,"https://www.zhihu.com/question/2088948992545484946",n);
            log("COMPLETED 4 latency trials; successes="+successes+" failures="+failures);
        }catch(Throwable e){log("OBSERVER ERROR "+e);}
        finally{
            try(FileOutputStream out=new FileOutputStream(new File(directory,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}
            Bundle result=new Bundle();result.putString("stream",report+"\nOutput: "+directory);finish(Activity.RESULT_OK,result);
        }
    }
}
