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
/** Test-only origin intercepts establish synthetic sessions, never account credentials. */
public final class ExpandedBrowsingInstrumentation extends Instrumentation {
 private MainActivity activity;private LoginActivity page;private boolean intercept,seed,live;private String url;private int checks,failures;
 private final StringBuilder report=new StringBuilder();private static final String KEY="qr_expanded_session_1032";
 interface Task{void run()throws Exception;}
 @Override public void onCreate(Bundle args){super.onCreate(args);live=args!=null&&"true".equals(args.getString("live"));start();}
 private void check(boolean yes,String label){checks++;if(!yes)failures++;report.append(yes?"PASS ":"FAIL ").append(label).append('\n');}
 private void ui(Task task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
 private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static View find(View root,String text){if(root instanceof TextView&&text.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),text);if(v!=null)return v;}return null;}
 private void quiet()throws Exception{((Repository)field(activity,"repo")).cancelPending();((ArticlePreloader)field(activity,"preloader")).pause();((Handler)field(activity,"preloadHandler")).removeCallbacksAndMessages(null);}
 private String js(WebView w,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] v={""};ui(()->w.evaluateJavascript(script,s->{v[0]=s;done.countDown();}));if(!done.await(4,TimeUnit.SECONDS))throw new IOException("Script timeout");return v[0];}
 @Override public void callActivityOnCreate(Activity a,Bundle state){super.callActivityOnCreate(a,state);if(intercept&&a instanceof LoginActivity)try{
  page=(LoginActivity)a;WebView w=(WebView)field(a,"web");w.stopLoading();WebViewClient delegate=w.getWebViewClient();final boolean establish=seed;
  w.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest r){
    String script=establish?"localStorage.setItem('"+KEY+"','ok');document.cookie='"+KEY+"=ok; Path=/__qr_test_1032; Secure; SameSite=Lax';":"";
    String html="<html><meta name=viewport content='width=device-width,initial-scale=1'><body><h1>合成来源页面</h1><script>"+script+"document.body.dataset.session=(localStorage.getItem('"+KEY+"')==='ok'&&document.cookie.includes('"+KEY+"=ok'))?'ready':'missing';</script><a href='https://example.org/article'>外部原文</a><a href='intent://blocked'>App链接</a></body></html>";
    return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
   }
   @Override public void onPageFinished(WebView view,String u){delegate.onPageFinished(view,u);}
   @Override public void onPageStarted(WebView view,String u,android.graphics.Bitmap b){delegate.onPageStarted(view,u,b);}
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(view,r);}
  });w.loadUrl(url);
 }catch(Exception e){throw new RuntimeException(e);}}
 private LoginActivity open(Source s,boolean browse,boolean establish)throws Exception{
  intercept=true;seed=establish;url="https://"+UrlPolicy.host(s.login)+"/__qr_test_1032";
  LoginActivity result=(LoginActivity)startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source",s.name()).putExtra("url",url).putExtra("browse",browse).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));intercept=false;
  WebView w=(WebView)field(result,"web");long end=System.currentTimeMillis()+6000;
  while(System.currentTimeMillis()<end){if(js(w,"document.body&&document.body.dataset.session").equals("\"ready\""))break;Thread.sleep(100);}
  return result;
 }
 private void sessions()throws Exception{
  for(Source s:Source.values())if(s.readable()){
   LoginActivity login=open(s,false,true);WebView first=(WebView)field(login,"web");
   check(js(first,"document.body.dataset.session").equals("\"ready\""),s+" synthetic login established");
   ui(()->{check(field(login,"sourceWindow")!=null,s+" login retains sliding window");login.finish();});
   LoginActivity browse=open(s,true,false);WebView web=(WebView)field(browse,"web");
   check(js(web,"document.body.dataset.session").equals("\"ready\""),s+" new browser reuses cookie and localStorage");
   ui(()->{check(field(browse,"sourceWindow")==null&&web.getSettings().supportZoom(),s+" source reading has independent zoomable viewport");check(find(browse.getWindow().getDecorView(),"读取当前页")==null,s+" no duplicate text-extraction action in browser");});
   if(s==Source.BILIBILI){ui(()->{web.getWebChromeClient().onShowCustomView(new android.view.View(browse),()->{});check(field(browse,"fullscreen")!=null,"video fullscreen accepted");browse.onBackPressed();check(field(browse,"fullscreen")==null&&!browse.isFinishing(),"Back exits fullscreen before closing browser");});}
   if(s==Source.BAIDU){
    js(web,"document.querySelector('a[href^=intent]').click()");Thread.sleep(200);check(js(web,"location.protocol").equals("\"https:\""),"App scheme cannot leave HTTPS browser");
    js(web,"document.querySelector('a[href^=https]').click()");Thread.sleep(300);check(js(web,"location.hostname").equals("\"example.org\""),"Search result opens external HTTPS original");check(js(web,"document.body.dataset.session").equals("\"missing\""),"Source session is not exposed to external origin");ui(browse::onBackPressed);Thread.sleep(300);
   }
   js(web,"localStorage.removeItem('"+KEY+"');document.cookie='"+KEY+"=; Max-Age=0; Path=/__qr_test_1032; Secure';");
   ui(browse::finish);
  }
 }
 private void liveBoards()throws Exception{
  for(Source s:new Source[]{Source.TIEBA,Source.DOUYIN}){
   CountDownLatch done=new CountDownLatch(1);List<Item>[] items=new List[]{null};String[] error={""};DynamicBoard[] board={null};
   ui(()->{board[0]=new DynamicBoard(activity,s,new Repository.Result<List<Item>>(){public void success(List<Item> rows){items[0]=rows;done.countDown();}public void failure(String why){error[0]=why;done.countDown();}});});
   if(!done.await(6,TimeUnit.SECONDS)){WebView w=(WebView)field(board[0],"web");report.append(s+" diagnostic "+js(w,"JSON.stringify({title:document.title,width:innerWidth,links:document.querySelectorAll('a[href*=hottopic]').length,text:document.body.innerText.slice(0,900)})")+"\n");}
   boolean completed=done.await(35,TimeUnit.SECONDS);ui(()->board[0].close());
   check(completed&&items[0]!=null&&!items[0].isEmpty(),s+" live WebView board count="+(items[0]==null?0:items[0].size())+(error[0].isEmpty()?"":" unavailable"));
  }
 }
 @Override public void onStart(){SharedPreferences prefs=getTargetContext().getSharedPreferences("source-session",0);Map<String,?> old=new HashMap<>(prefs.getAll());
  try{activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));ui(this::quiet);if(live)liveBoards();else sessions();}
  catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
  finally{try{ui(()->{if(page!=null&&!page.isFinishing())page.finish();if(activity!=null){quiet();activity.finish();}});SharedPreferences.Editor edit=prefs.edit();for(Source s:Source.values())for(String suffix:new String[]{"-desktop","-web"}){String k=s.name()+suffix;if(old.containsKey(k))edit.putBoolean(k,(Boolean)old.get(k));else edit.remove(k);}edit.commit();}catch(Exception e){failures++;report.append("ERROR cleanup ").append(e).append('\n');}}
  report.append("CHECKS ").append(checks).append(" FAILURES ").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
