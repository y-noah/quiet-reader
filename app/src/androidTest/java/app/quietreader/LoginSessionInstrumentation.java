package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Local synthetic login -> fresh source -> continuation. No real account inspection. */
public final class LoginSessionInstrumentation extends Instrumentation {
    private static final String URL="https://www.zhihu.com/question/9876501011";
    private static final String KEY="qr_session_regression_1011";
    private MainActivity activity;
    private LoginActivity login;
    private boolean opening;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    interface Task {void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean yes,String label){checks++;if(!yes)failures++;report.append(yes?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Task task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private static Object field(Object value,String name)throws Exception{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    private static View find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),text);if(v!=null)return v;}return null;}
    private static String document(boolean seed){
        String script=seed?"localStorage.setItem('"+KEY+"','ready');document.cookie='"+KEY+"=ready; Path=/question/9876501011; Secure; SameSite=Lax';":"";
        return "<html><head><meta charset='utf-8'></head><body><h1>合成会话测试</h1><script>"+script+
                "var ok=localStorage.getItem('"+KEY+"')==='ready'&&document.cookie.split('; ').indexOf('"+KEY+"=ready')>=0;"+
                "document.write('<div class=AnswerItem id=1011><div class=RichContent-inner><div class=RichText><p>'+(ok?'QR_SESSION_VISIBLE':'QR_SESSION_MISSING')+'</p></div></div></div>');</script></body></html>";
    }
    private void intercept(WebView web,boolean seed){
        WebViewClient delegate=web.getWebViewClient();
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){
                String body=URL.equals(r.getUrl().toString())?document(seed):"";
                return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
            }
            @Override public void onPageStarted(WebView w,String u,android.graphics.Bitmap icon){delegate.onPageStarted(w,u,icon);}
            @Override public void onPageCommitVisible(WebView w,String u){delegate.onPageCommitVisible(w,u);}
            @Override public void onPageFinished(WebView w,String u){delegate.onPageFinished(w,u);}
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
        });
    }
    @Override public void callActivityOnCreate(Activity a,Bundle b){
        super.callActivityOnCreate(a,b);
        if(opening&&a instanceof LoginActivity)try{login=(LoginActivity)a;WebView web=(WebView)field(a,"web");web.stopLoading();intercept(web,true);web.loadUrl(URL);}catch(Exception e){throw new RuntimeException(e);}
    }
    private void quiet()throws Exception{
        ((Repository)field(activity,"repo")).cancelPending();
        ((ArticlePreloader)field(activity,"preloader")).pause();((Handler)field(activity,"preloadHandler")).removeCallbacksAndMessages(null);
    }
    private String js(WebView web,String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] value={""};
        ui(()->web.evaluateJavascript(script,s->{value[0]=s;done.countDown();}));
        if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Script timeout");return value[0];
    }
    @Override public void onStart(){
        SharedPreferences prefs=getTargetContext().getSharedPreferences("source-session",0);
        Map<String,?> old=new HashMap<>(prefs.getAll());DynamicReader[] reader={null};WebView[] source={null};
        try{
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ui(()->{quiet();SourceSession.setDesktop(activity,Source.ZHIHU,true);});
            opening=true;startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source","ZHIHU").putExtra("url",URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));opening=false;
            Thread.sleep(800);
            ui(()->{View mode=find(login.getWindow().getDecorView(),"手机版");check(mode!=null,"Real login screen has display-mode control");if(mode!=null)mode.performClick();});
            Thread.sleep(800);
            WebView loginWeb=(WebView)field(login,"web");
            check(js(loginWeb,"document.body.innerText.indexOf('QR_SESSION_VISIBLE')>=0").equals("true"),"Synthetic login page established only the test cookie/storage marker");
            String[] ua={""};ui(()->{ua[0]=loginWeb.getSettings().getUserAgentString();check(!SourceSession.desktop(activity,Source.ZHIHU),"Login mode change persists for later readers");find(login.getWindow().getDecorView(),"返回阅读").performClick();});
            Thread.sleep(500);ui(this::quiet);
            CountDownLatch done=new CountDownLatch(1);Document[] result={null};
            ui(()->{
                check(SourceSession.usesWeb(activity,Source.ZHIHU),"Returned source uses the WebView session");
                reader[0]=new DynamicReader(activity,new Item(Source.ZHIHU,"合成会话",URL,""),new Repository.Result<Document>(){
                    public void success(Document d){result[0]=d;source[0]=reader[0].takeSource();done.countDown();}
                    public void failure(String why){done.countDown();}
                });
                WebView fresh=(WebView)field(reader[0],"web");intercept(fresh,false);
                check(ua[0].equals(fresh.getSettings().getUserAgentString()),"Fresh reader retains the login page user agent");
            });
            check(done.await(12,TimeUnit.SECONDS),"Fresh reader completed within timeout");
            check(result[0]!=null&&result[0].blocks.stream().anyMatch(b->b.value.contains("QR_SESSION_VISIBLE")),"Fresh reader actually sees the synthetic shared session");
            ui(()->{
                check(source[0]!=null,"Eligible source is available for continuation");
                if(source[0]!=null){
                    // Keep a non-default UA to detect accidental rewriting of an adopted page.
                    source[0].getSettings().setUserAgentString("QR-retained-session-UA");
                    AnswerStream stream=new AnswerStream(activity,new Item(Source.ZHIHU,"合成会话",URL,""),source[0]);
                    check("QR-retained-session-UA".equals(source[0].getSettings().getUserAgentString()),"Continuation does not rewrite an adopted page user agent");
                    stream.close();source[0]=null;
                }
                for(Source platform:Source.displayOrder()){
                    SourceSession.setDesktop(activity,platform,false);
                    DynamicReader probe=new DynamicReader(activity,new Item(platform,"设置检查",platform.login,""),new Repository.Result<Document>(){public void success(Document d){}public void failure(String s){}});
                    WebView web=(WebView)field(probe,"web");
                    check(web.getSettings().getUserAgentString().equals(SourceSession.userAgent(activity,platform)),"Shared source mode "+platform.name());
                    check(CookieManager.getInstance().acceptThirdPartyCookies(web)==(platform==Source.WEIBO),"Source cookie policy "+platform.name());
                    probe.close();
                }
            });
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{
            try{
                // Delete only our synthetic marker; never enumerate or clear account cookies/storage.
                WebView[] cleanup={null};CountDownLatch loaded=new CountDownLatch(1);
                ui(()->{if(reader[0]!=null)reader[0].close();if(source[0]!=null)SourceSurface.release(source[0]);if(login!=null&&!login.isFinishing())login.finish();
                    cleanup[0]=new WebView(activity);SourceSession.configure(activity,Source.ZHIHU,cleanup[0]);
                    cleanup[0].setWebViewClient(new WebViewClient(){
                        @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<html></html>".getBytes(StandardCharsets.UTF_8)));}
                        @Override public void onPageFinished(WebView w,String u){loaded.countDown();}
                    });cleanup[0].loadUrl(URL);
                });
                if(loaded.await(5,TimeUnit.SECONDS))js(cleanup[0],"localStorage.removeItem('"+KEY+"');document.cookie='"+KEY+"=; Max-Age=0; Path=/question/9876501011; Secure';");
                ui(()->{SourceSurface.release(cleanup[0]);quiet();activity.finish();});
                SharedPreferences.Editor restore=prefs.edit();for(Source s:Source.displayOrder())for(String suffix:new String[]{"-desktop","-web"}){String key=s.name()+suffix;if(old.containsKey(key))restore.putBoolean(key,(Boolean)old.get(key));else restore.remove(key);}restore.commit();
            }catch(Exception e){failures++;report.append("ERROR cleanup ").append(e).append('\n');}
        }
        report.append("CHECKS ").append(checks).append(" FAILURES ").append(failures).append('\n');
        Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
