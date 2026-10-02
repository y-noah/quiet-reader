package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Controlled navigation through real clients, never direct callback invocation. */
public final class NavigationRecoveryInstrumentation extends Instrumentation {
    private static final String BASE="https://m.weibo.cn/qr-synthetic-navigation/";
    private static final String INITIAL="https://m.weibo.cn/search";
    private static final String MARKER="SYNTHETIC_NAVIGATION_BODY_真实受控正文，不是平台内容";
    private File directory;private Activity main;private Trial opening;
    private final StringBuilder report=new StringBuilder();private int checks,failures,completed;
    interface Action{void run()throws Exception;}
    interface Condition{boolean yes()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private synchronized void log(String s){report.append(s).append('\n');}
    private void check(boolean p,String s){checks++;if(!p)failures++;log((p?"PASS ":"FAIL ")+s);}
    private static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private void ui(Action a)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{a.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);}
    private boolean await(Condition c,int ms)throws Exception{long end=SystemClock.elapsedRealtime()+ms;do{if(c.yes())return true;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<end);return c.yes();}
    @Override public void callActivityOnCreate(Activity a,Bundle b){super.callActivityOnCreate(a,b);if(a instanceof LoginActivity&&opening!=null){try{opening.login=a;opening.web=(WebView)field(a,"web");opening.web.stopLoading();opening.install();opening.web.loadUrl(opening.url);}catch(Exception e){throw new RuntimeException(e);}}}
    private static String body(Document d){StringBuilder b=new StringBuilder();if(d!=null)for(Block v:d.blocks)if(v.type.equals("text"))b.append(v.value);return b.toString();}
    private final class Trial {
        final String name,mode,url;Activity login;WebView web;DynamicReader reader;
        volatile int mainRequests,httpRequests,foreignRequests,navigations,finishes,commits,callbacks,fixtureReady,initialRequests,httpErrors;
        volatile boolean retryArmed;
        volatile String lastFinished="",failure="",lastNav="";volatile boolean lastHandled;
        volatile Document result;final CountDownLatch done=new CountDownLatch(1);
        Trial(String n,String m){name=n;mode=m;url=BASE+m+"/start";}
        String status()throws Exception{String[] s={""};ui(()->s[0]=login==null?failure:((TextView)field(login,"status")).getText().toString());return s[0];}
        String address()throws Exception{String[] s={null};ui(()->s[0]=web.getUrl());return s[0];}
        String html(String u){
            String target="";
            if(mode.equals("upgrade")||mode.equals("dynamic")){if(u.equals(url))target=BASE.replace("https:","http:")+mode+"/end";}
            else if(mode.equals("external")&&!u.endsWith("/recovered"))target="https://example.org/qr-synthetic-blocked";
            else if(mode.equals("scheme"))target="sinaweibo://qr-synthetic-blocked";
            else if(mode.equals("repeat"))target=BASE.replace("https:","http:")+"repeat/loop";
            else if(mode.equals("budget")){int index=u.equals(url)?0:Integer.parseInt(u.substring(u.lastIndexOf('/')+1));target=BASE.replace("https:","http:")+"budget/"+(index+1);}
            String text=target.isEmpty()?MARKER:"SYNTHETIC_NAVIGATION_INTERMEDIATE";
            return "<!doctype html><html><head><meta charset='utf-8'><title>合成导航回归</title><script>console.log('QR_NAV_FIXTURE_READY');"+(target.isEmpty()?"":"location.href="+JSONObject.quote(target)+";")+"</script></head><body style='background:#1f2025;color:#cdcfd5;font-size:22px'><h1>合成导航测试 · 非真实平台</h1><div class='weibo-text'>"+text+"</div></body></html>";
        }
        synchronized WebResourceResponse response(String u,boolean frame){
            if(u.startsWith("http:"))httpRequests++;
            if(u.equals(INITIAL)){
                initialRequests++;
                if(mode.equals("invalid-retry")&&retryArmed){if(frame)mainRequests++;return new WebResourceResponse("text/html","UTF-8",200,"OK",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(html(u).getBytes(StandardCharsets.UTF_8)));}
                return new WebResourceResponse("text/html","UTF-8",204,"No Content",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(new byte[0]));
            }
            if(!u.startsWith(BASE)){if(frame)foreignRequests++;try{java.net.URI parsed=new java.net.URI(u);log(name+" otherResource mainFrame="+frame+" scheme="+parsed.getScheme()+" host="+parsed.getHost()+" path="+parsed.getPath());}catch(Exception ignored){}return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
            if(frame)mainRequests++;
            int code=mode.equals("http-error")?503:200;
            String reason=code==503?"Service Unavailable":code==204?"No Content":"OK";
            return new WebResourceResponse("text/html","UTF-8",code,reason,Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream((code==204?"":html(u)).getBytes(StandardCharsets.UTF_8)));
        }
        void install(){WebViewClient original=web.getWebViewClient();web.setWebChromeClient(new WebChromeClient(){@Override public boolean onConsoleMessage(ConsoleMessage m){if(m.message().equals("QR_NAV_FIXTURE_READY"))fixtureReady++;return true;}});
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return response(r.getUrl().toString(),r.isForMainFrame());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,String u){return response(u,true);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){navigations++;lastNav=r.getUrl().toString();boolean v=original.shouldOverrideUrlLoading(w,r);lastHandled=v;log(name+" actual navigation="+lastNav+" handled="+v);return v;}
                @Override public boolean shouldOverrideUrlLoading(WebView w,String u){return original.shouldOverrideUrlLoading(w,u);}
                @Override public void onPageStarted(WebView w,String u,Bitmap i){try{log(name+" START url="+u+" before="+(login==null?failure:((TextView)field(login,"status")).getText()));}catch(Exception ignored){}original.onPageStarted(w,u,i);try{log(name+" START after="+(login==null?failure:((TextView)field(login,"status")).getText()));}catch(Exception ignored){}}
                @Override public void onPageCommitVisible(WebView w,String u){if(u.startsWith(BASE))commits++;original.onPageCommitVisible(w,u);}
                @Override public void onPageFinished(WebView w,String u){original.onPageFinished(w,u);if(u.startsWith(BASE)){finishes++;lastFinished=u;try{log(name+" actual finish status="+(login==null?failure:((TextView)field(login,"status")).getText()));}catch(Exception ignored){}}}
                @Override public void onReceivedError(WebView w,WebResourceRequest r,WebResourceError e){original.onReceivedError(w,r,e);}
                @Override public void onReceivedHttpError(WebView w,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame())httpErrors++;log(name+" HTTP_ERROR main="+r.isForMainFrame()+" code="+e.getStatusCode());original.onReceivedHttpError(w,r,e);}
                @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){original.onReceivedSslError(w,h,e);}
                @Override public void onLoadResource(WebView w,String u){original.onLoadResource(w,u);}
                @Override public void doUpdateVisitedHistory(WebView w,String u,boolean r){original.doUpdateVisitedHistory(w,u,r);}
                @Override public boolean onRenderProcessGone(WebView w,RenderProcessGoneDetail d){return original.onRenderProcessGone(w,d);}
            });
        }
        boolean marker()throws Exception{CountDownLatch latch=new CountDownLatch(1);boolean[] yes={false};ui(()->{if(web.getUrl()==null||(!web.getUrl().startsWith(BASE)&&!(retryArmed&&web.getUrl().equals(INITIAL)))){latch.countDown();return;}web.evaluateJavascript("document.body&&document.body.textContent.indexOf("+JSONObject.quote(MARKER)+")>=0",s->{yes[0]="true".equals(s);latch.countDown();});});return latch.await(2,TimeUnit.SECONDS)&&yes[0];}
        void begin()throws Exception{
            if(mode.equals("dynamic")){ui(()->{reader=new DynamicReader(main,new Item(Source.WEIBO,"合成升级",url,""),new Repository.Result<Document>(){public void success(Document d){result=d;callbacks++;done.countDown();}public void failure(String s){failure=s;callbacks++;done.countDown();}});web=(WebView)field(reader,"web");install();});}
            else{opening=this;try{startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source","WEIBO").putExtra("url",INITIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}finally{opening=null;}}
        }
        void run(){log("CASE "+name+" controlled="+url);try{begin();
            if(mode.equals("dynamic")){
                check(done.await(24,TimeUnit.SECONDS),name+" bounded actual production callback");check(result!=null&&failure.isEmpty(),name+" actual DynamicReader succeeds");check(body(result).contains(MARKER),name+" actual parsed final body marker");check(callbacks==1,name+" exactly one production callback");check(navigations==1&&lastHandled&&lastNav.startsWith("http:")&&mainRequests==2,name+" real downgrade upgraded with two HTTPS documents");
            }else if(mode.equals("upgrade")){
                check(await(this::marker,8000),name+" final marker actually in DOM");check((BASE+mode+"/end").equals(address()),name+" final committed URL is expected HTTPS");check(navigations==1&&lastHandled,name+" actual navigation handled by production");check(await(()->finishes>0,3000)&&!status().contains("阻止"),name+" finished without false blocked issue");
            }else if(mode.equals("external")||mode.equals("scheme")){
                check(await(()->status().contains("已阻止外部跳转"),8000),name+" native blocked issue displayed");check(navigations==1&&lastHandled&&fixtureReady>0,name+" fixture actually attempted unsafe navigation");check(await(()->finishes>0,3000),name+" real fixture finish observed");Thread.sleep(400);check(status().contains("已阻止外部跳转")&&!status().contains("完成登录"),name+" blocked issue survives actual finish");
                if(mode.equals("external")){
                    String next=BASE+"external/recovered";
                    // Source script causes a real browser navigation, not synthetic callbacks.
                    ui(()->web.evaluateJavascript("location.href="+JSONObject.quote(next),null));
                    check(await(this::marker,8000),name+" later legal source navigation reaches real final DOM marker");
                    check(next.equals(address())&&navigations==2&&!lastHandled,name+" production permits later HTTPS source navigation");
                    check(await(()->lastFinished.equals(next),3000)&&!status().contains("阻止")&&status().contains("完成登录"),name+" legitimate later completed page clears prior issue");
                }
            }else if(mode.equals("repeat")||mode.equals("budget")){
                check(await(()->status().contains("来源反复跳转"),8000),name+" loop stopped with explicit issue");int count=mainRequests;check(count==(mode.equals("repeat")?2:5),name+" bounded expected HTTPS requests");Thread.sleep(600);check(mainRequests==count&&navigations==(mode.equals("repeat")?2:5),name+" no continuing navigation loop");check(finishes>0&&status().contains("来源反复跳转"),name+" stop issue retained after finish");
            }else if(mode.equals("invalid-retry")){
                boolean loaded=await(this::marker,8000);ui(()->web.loadUrl("about:blank"));check(loaded&&await(()->"about:blank".equals(address()),5000),name+" positive fixture then actual navigation leaves no valid platform source URL (not null-specific)");retryArmed=true;tapMode(login);check(await(this::marker,8000),name+" actual display-mode pointer recovers marker");check(mainRequests==2,name+" initial safe URL requested again exactly once");check(INITIAL.equals(address()),name+" recovered initial HTTPS address");
            }else if(mode.equals("http-error")){
                check(await(()->status().contains("HTTP 503"),8000),name+" actual main HTTP503 error explained");check(await(()->finishes>0,3000)&&fixtureReady>0,name+" local 503 response really loaded/finished");Thread.sleep(400);check(status().contains("HTTP 503")&&!status().contains("完成登录"),name+" HTTP issue not overwritten by finish");
            }
            check(mainRequests>0,name+" positive local main resource interception");check(httpRequests==0,name+" no HTTP resource permitted");check(foreignRequests==0,name+" no foreign destination resource requested");
            log(name+" controlledRequests="+mainRequests+" initialRequests="+initialRequests+" navigation="+navigations+" ready="+fixtureReady+" finish="+finishes+" commits="+commits+" httpErrors="+httpErrors+" status="+status());
            screenshot(name);completed++;
        }catch(Throwable e){failures++;log("ERROR "+name+" "+e);try{screenshot(name+"-error");}catch(Exception ignored){}}finally{try{ui(()->{if(reader!=null)reader.close();if(login!=null)login.finish();});save();}catch(Exception e){failures++;log("ERROR cleanup "+e);}}}
    }
    private void tapMode(Activity a)throws Exception{int[] p=new int[2];boolean[] valid={false};ui(()->{View v=findButton(a.getWindow().getDecorView(),"桌面版");if(v!=null&&v.isShown()&&a.getWindow().getDecorView().hasWindowFocus()){v.getLocationOnScreen(p);p[0]+=v.getWidth()/2;p[1]+=v.getHeight()/2;valid[0]=true;}});if(!valid[0])throw new AssertionError("Real mode button unavailable");long t=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,p[0],p[1],0));sendPointerSync(MotionEvent.obtain(t,t+50,MotionEvent.ACTION_UP,p[0],p[1],0));}
    private View findButton(View v,String s){if(v instanceof Button&&s.contentEquals(((Button)v).getText()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=findButton(((ViewGroup)v).getChildAt(i),s);if(f!=null)return f;}return null;}
    private void screenshot(String n)throws Exception{Thread.sleep(150);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(directory,n+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private void save()throws Exception{try(FileOutputStream out=new FileOutputStream(new File(directory,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}
    @Override public void onStart(){directory=new File(getTargetContext().getExternalFilesDir(null),"navigation-recovery-qa/run-"+System.currentTimeMillis());directory.mkdirs();try{
        log("SYNTHETIC: actual WebView navigation and original client callbacks. Login onCreate initial GET may start before wrapper; controlled start is stopLoading+install wrapper+load fixture. No HTML/account/cookie/storage export. Case7 uses actual about:blank to test invalid-source fallback; does NOT independently prove nullURL. Earlier HTTP204 fixture did not create nullURL; retained as fixture failure. Dynamic screenshot alone is not a visible source-body claim.");
        main=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));ui(()->{((Repository)field(main,"repo")).cancelPending();Object b=field(main,"dynamicBoard");if(b!=null)((DynamicBoard)b).close();});
        new Trial("01-login-upgrade","upgrade").run();new Trial("02-dynamic-upgrade","dynamic").run();new Trial("03-external-block","external").run();new Trial("04-scheme-block","scheme").run();new Trial("05-repeat-stop","repeat").run();new Trial("06-budget-stop","budget").run();new Trial("07-invalid-retry","invalid-retry").run();new Trial("08-http-error","http-error").run();
    }catch(Throwable e){failures++;log("ERROR SETUP "+e);}finally{log("COMPLETED "+completed+" navigation trials; checks="+checks+" failures="+failures);try{save();}catch(Exception e){failures++;}Bundle b=new Bundle();b.putString("stream",report+"\nOutput: "+directory);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);}}
}
