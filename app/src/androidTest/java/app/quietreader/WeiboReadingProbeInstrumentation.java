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
import java.util.List;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Production callbacks, forwarding client; never saves HTML/cookies/source text. */
public final class WeiboReadingProbeInstrumentation extends Instrumentation {
 Activity activity; File directory; int trialNumber; final StringBuilder report=new StringBuilder(); final Handler main=new Handler(Looper.getMainLooper());
 @Override public void onCreate(Bundle b){super.onCreate(b);start();}
 synchronized void log(String s){report.append(s).append('\n');}
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static String safe(String s){try{java.net.URI u=new java.net.URI(s);String p=u.getPath();return u.getScheme()+"://"+u.getHost()+(p==null?"":p.replaceAll("/(status|detail)/[^/]+","/$1/:id"));}catch(Exception e){return "<invalid>";}}
 final class Trial {
  final boolean mobile;final Item item;final CountDownLatch done=new CountDownLatch(1);DynamicReader reader;WebView web;boolean stopped;long began;String result="no callback";
  Trial(Item i,boolean m){item=i;mobile=m;}
  void event(String s){log((mobile?"MOBILE":"DESKTOP")+" ms="+(SystemClock.elapsedRealtime()-began)+" "+s);}
  void begin(){
   began=SystemClock.elapsedRealtime();reader=new DynamicReader(activity,item,new Repository.Result<Document>(){
    public void success(Document d){int chars=0;for(Block b:d.blocks)if(b.type.equals("text"))chars+=b.value.length();result="success blocks="+d.blocks.size()+" sections="+d.sections.size()+" textChars="+chars+" filteredVideos="+d.filteredVideos;end();}
    public void failure(String reason){result="failure reason="+reason;end();}
    void end(){stopped=true;main.removeCallbacks(poll);event("CALLBACK "+result);done.countDown();}
   });
   try{web=(WebView)field(reader,"web");web.getSettings().setUserAgentString(mobile?WebSettings.getDefaultUserAgent(activity):Repository.DESKTOP_UA);
    event("CONFIG mobileUA="+mobile+" blockImage="+web.getSettings().getBlockNetworkImage()+" thirdParty="+CookieManager.getInstance().acceptThirdPartyCookies(web)+" initial="+safe(item.url));WebViewClient c=web.getWebViewClient();
    web.setWebViewClient(new WebViewClient(){
     public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){boolean h=c.shouldOverrideUrlLoading(w,r);event("navigate main="+r.isForMainFrame()+" handled="+h+" "+safe(r.getUrl().toString()));return h;}
     public boolean shouldOverrideUrlLoading(WebView w,String u){return c.shouldOverrideUrlLoading(w,u);}
     public void onPageStarted(WebView w,String u,Bitmap i){event("start "+safe(u));c.onPageStarted(w,u,i);}
     public void onPageCommitVisible(WebView w,String u){event("commit "+safe(u));c.onPageCommitVisible(w,u);}
     public void onPageFinished(WebView w,String u){event("finish "+safe(u));c.onPageFinished(w,u);}
     public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return c.shouldInterceptRequest(w,r);}
     public WebResourceResponse shouldInterceptRequest(WebView w,String u){return c.shouldInterceptRequest(w,u);}
     public void onLoadResource(WebView w,String u){c.onLoadResource(w,u);}
     public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){event("error main="+r.isForMainFrame()+" code="+e.getErrorCode()+" "+safe(r.getUrl().toString()));c.onReceivedError(w,r,e);}
     public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){event("HTTP main="+r.isForMainFrame()+" code="+e.getStatusCode()+" "+safe(r.getUrl().toString()));c.onReceivedHttpError(w,r,e);}
     public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){event("SSL code="+e.getPrimaryError());c.onReceivedSslError(w,h,e);}
     public void onReceivedHttpAuthRequest(WebView w,HttpAuthHandler h,String host,String realm){c.onReceivedHttpAuthRequest(w,h,host,realm);}
     public void onReceivedClientCertRequest(WebView w,ClientCertRequest r){c.onReceivedClientCertRequest(w,r);}
     public void doUpdateVisitedHistory(WebView w,String u,boolean r){c.doUpdateVisitedHistory(w,u,r);}
     public void onScaleChanged(WebView w,float a,float b){c.onScaleChanged(w,a,b);}
     public boolean onRenderProcessGone(WebView w,RenderProcessGoneDetail d){return c.onRenderProcessGone(w,d);}
     public void onSafeBrowsingHit(WebView w,WebResourceRequest r,int threat,SafeBrowsingResponse response){c.onSafeBrowsingHit(w,r,threat,response);}
    });main.postDelayed(poll,700);
   }catch(Exception e){event("SETUP_ERROR "+e.getClass().getSimpleName());stopped=true;reader.close();done.countDown();}
  }
  final Runnable poll=()->sample();
  void sample(){if(stopped)return;web.evaluateJavascript("JSON.stringify((function(){var t=document.body?document.body.innerText:'';return {ready:document.readyState,bodyChars:t.length,selectors:document.querySelectorAll('.card-wrap .txt,.weibo-text,[class*=detail_wbtext]').length,loginWords:/扫码登录|短信登录|密码登录|登录微博/.test(t),captchaWords:/验证码|安全验证|访问频次/.test(t)};})())",raw->{if(stopped)return;try{Object v=new JSONTokener(raw).nextValue();if(v instanceof String)event("DOM "+new JSONObject((String)v)+" location="+safe(web.getUrl())+" attempts="+field(reader,"attempts"));}catch(Exception e){event("DOM_ERROR "+e.getClass().getSimpleName());}main.postDelayed(poll,700);});}
  void stop(){stopped=true;main.removeCallbacks(poll);if(reader!=null)reader.close();}
 }
 void trial(Item i,boolean m)throws Exception{trialNumber++;Trial t=new Trial(i,m);runOnMainSync(t::begin);if(!t.done.await(28,TimeUnit.SECONDS)){runOnMainSync(t::stop);throw new AssertionError("Callback timeout");}
  runOnMainSync(()->{TextView v=new TextView(activity);v.setTextColor(0xffcdcfd5);v.setBackgroundColor(0xff1f2025);v.setTextSize(18);v.setPadding(30,100,30,30);v.setText("诊断摘要，不是正文视觉验收\n"+(m?"系统 mobile UA":"显式 desktop UA")+"\n"+t.result+"\n同一热榜条目；无账号输入/无清理Cookie");activity.setContentView(v);});Thread.sleep(300);Bitmap b=getUiAutomation().takeScreenshot();if(b!=null)try(FileOutputStream out=new FileOutputStream(new File(directory,String.format(java.util.Locale.ROOT,"%02d-%s-summary.png",trialNumber,m?"mobile":"desktop")))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
 }
 @Override public void onStart(){directory=new File(getTargetContext().getExternalFilesDir(null),"weibo-reading-qa/run-"+System.currentTimeMillis());directory.mkdirs();Repository r=new Repository(getTargetContext());try{
  log("Genuine production DynamicReader callbacks; same-session explicit DESKTOP then MOBILE then DESKTOP, only UA changed between trials. Sequential cache/session confound remains; extra 700ms read-only DOM count overhead. Real cached board Item, not newly fetched board. No HTML/text/query/cookies persisted.");activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1200);List<Item> items=r.cached(Source.WEIBO);if(items.isEmpty())throw new AssertionError("No actual cached Weibo Item");Item i=items.get(0);log("ITEM sameObject=true source="+i.source+" safeURL="+safe(i.url));trial(i,false);trial(i,true);trial(i,false);log("COMPLETED 3 UA observations; callback success separately reported, not a pass requirement.");
 }catch(Throwable e){log("OBSERVER_ERROR "+e.getClass().getSimpleName()+": "+e.getMessage());}finally{r.close();try(FileOutputStream out=new FileOutputStream(new File(directory,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}Bundle b=new Bundle();b.putString("stream",report+"\nOutput: "+directory);finish(Activity.RESULT_OK,b);}}
}
