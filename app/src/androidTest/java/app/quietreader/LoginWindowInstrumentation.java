package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Wide synthetic login dialog with a hostile viewport; real controls and touch gestures. */
public final class LoginWindowInstrumentation extends Instrumentation {
    private static final String URL="https://www.zhihu.com/question/9876501029";
    private LoginActivity activity;
    private WebView web;
    private File output;
    private int checks,failures,requests;
    private String variant;
    private final StringBuilder report=new StringBuilder();
    interface Task{void run()throws Exception;}
    @Override public void onCreate(Bundle b){super.onCreate(b);variant=b.getString("variant","full");start();}
    private void check(boolean yes,String label){checks++;if(!yes)failures++;report.append(yes?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Task task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static View find(View root,String label){if(root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    private String page(){
        return "<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no'>"+
                "<style>html,body{height:100%}body{margin:0;min-width:1280px;background:#e7edf5;color:#192535;font:24px sans-serif}header{padding:30px}"+
                "#dialog{position:fixed;width:640px;left:50%;top:50%;transform:translate(-50%,-50%);background:white;padding:32px;box-sizing:border-box;border:2px solid #3568b5;border-radius:12px}"+
                "input{box-sizing:border-box;width:100%;font-size:26px;padding:14px}button{padding:14px;font-size:24px}small{display:block;margin:20px 0}</style></head>"+
                "<body><header>合成桌面页面 · 固定宽度 1280px</header><section id='dialog'><h1>合成登录窗口</h1><p>仅验证布局，不是真实账号登录。</p>"+
                "<input id='draft' placeholder='合成输入内容'><small>左右边框与此按钮应完整可见</small><button id='submit' type='button' onclick='window.submitted=true'>合成登录按钮</button></section>"+
                "<button id='corner' style='position:fixed;right:20px;bottom:20px' onclick='window.cornerClicks=(window.cornerClicks||0)+1'>右下角按钮</button></body></html>";
    }
    @Override public void callActivityOnCreate(Activity a,Bundle b){
        super.callActivityOnCreate(a,b);
        if(a instanceof LoginActivity)try{
            activity=(LoginActivity)a;web=(WebView)field(a,"web");web.stopLoading();WebViewClient delegate=web.getWebViewClient();
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){
                    boolean match=r.getUrl().toString().startsWith(URL);if(match)requests++;
                    return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream((match?page():"").getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageStarted(WebView w,String u,Bitmap icon){delegate.onPageStarted(w,u,icon);}
                @Override public void onPageFinished(WebView w,String u){delegate.onPageFinished(w,u);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
            });web.loadUrl(URL);
        }catch(Exception e){throw new RuntimeException(e);}
    }
    private String js(String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] value={""};
        ui(()->web.evaluateJavascript(script,s->{value[0]=s;done.countDown();}));
        if(!done.await(3,TimeUnit.SECONDS))throw new IOException("Script timed out");return value[0];
    }
    private JSONObject observe()throws Exception{
        String raw=js("JSON.stringify((function(){var d=document.getElementById('dialog'),r=d.getBoundingClientRect(),v=window.visualViewport;return {left:r.left,top:r.top,right:r.right,bottom:r.bottom,x:v?v.offsetLeft:0,y:v?v.offsetTop:0,width:v?v.width:innerWidth,height:v?v.height:innerHeight,viewport:document.querySelector('meta[name=viewport]').content,draft:document.getElementById('draft').value};})())");
        return new JSONObject((String)new JSONTokener(raw).nextValue());
    }
    private float scale()throws Exception{float[] value={0};ui(()->value[0]=web.getScale());return value[0];}
    private void tap(String label)throws Exception{ui(()->{View button=find(activity.getWindow().getDecorView(),label);if(button==null)throw new AssertionError("Missing control "+label);button.performClick();});Thread.sleep(650);}
    private void shot(String name)throws Exception{
        Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new IOException("No screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){image.compress(Bitmap.CompressFormat.PNG,100,out);}image.recycle();
    }
    private void drag(boolean horizontal,boolean reverse)throws Exception{
        int[] location=new int[2],size=new int[2];
        ui(()->{View window=(View)field(activity,"sourceWindow");window.getLocationOnScreen(location);size[0]=window.getWidth();size[1]=window.getHeight();});
        long down=SystemClock.uptimeMillis();
        for(int n=0;n<=16;n++){
            float fraction=(reverse?.2f:.8f)+(reverse?.6f:-.6f)*n/16;
            float x=location[0]+size[0]*(horizontal?fraction:.5f),y=location[1]+size[1]*(horizontal?.45f+.04f*n/16:fraction);
            MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==16?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,y,0);
            sendPointerSync(event);event.recycle();Thread.sleep(25);
        }
        Thread.sleep(450);
    }
    private int[] windowPosition()throws Exception{
        int[] xy=new int[2];ui(()->{Object window=field(activity,"sourceWindow");xy[0]=((View)field(window,"horizontal")).getScrollX();xy[1]=((View)field(window,"vertical")).getScrollY();});return xy;
    }
    private void physicalClick(String id)throws Exception{
        JSONObject rect=new JSONObject((String)new JSONTokener(js("JSON.stringify((function(){var r=document.getElementById("+JSONObject.quote(id)+").getBoundingClientRect();return {x:(r.left+r.right)/2,y:(r.top+r.bottom)/2};})())")).nextValue());
        int[] location=new int[2];float factor=scale();ui(()->web.getLocationOnScreen(location));
        float x=location[0]+(float)rect.getDouble("x")*factor,y=location[1]+(float)rect.getDouble("y")*factor;
        long down=SystemClock.uptimeMillis();MotionEvent press=MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,x,y,0);sendPointerSync(press);press.recycle();
        MotionEvent release=MotionEvent.obtain(down,SystemClock.uptimeMillis()+60,MotionEvent.ACTION_UP,x,y,0);sendPointerSync(release);release.recycle();Thread.sleep(350);
    }
    private void slidingWindow()throws Exception{
        ui(()->{View window=(View)field(activity,"sourceWindow");report.append("SIZES window=").append(window.getWidth()).append('x').append(window.getHeight()).append(" web=").append(web.getWidth()).append('x').append(web.getHeight()).append('\n');check(web.getWidth()>window.getWidth()+50&&web.getHeight()>window.getHeight(),"Desktop surface extends past the viewport on both axes");});
        physicalClick("draft");sendStringSync("WINDOW_DRAFT");Thread.sleep(350);
        check(observe().getString("draft").equals("WINDOW_DRAFT"),"Real tap and key input work in the sliding form");
        ui(()->((android.view.inputmethod.InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(web.getWindowToken(),0));
        js("document.getElementById('draft').blur()");Thread.sleep(500);tap("窗口居中");
        int before=requests;float natural=scale();int[] first=windowPosition();drag(true,false);int[] right=windowPosition();
        check(right[0]>first[0]+50,"Real leftward drag exposes the right side");
        drag(true,true);int[] left=windowPosition();check(left[0]<right[0]-50,"Real rightward drag exposes the left side");
        drag(false,false);int[] bottom=windowPosition();check(bottom[1]>left[1]+50,"Real upward drag exposes the bottom");
        drag(false,true);check(windowPosition()[1]<bottom[1]-50,"Real downward drag exposes the top");
        check(Math.abs(scale()-natural)<.02,"Two-axis dragging does not change page zoom");
        check(js("window.cornerClicks||0").equals("0"),"Dragging does not accidentally click a form control");
        ui(()->{Object window=field(activity,"sourceWindow");((HorizontalScrollView)field(window,"horizontal")).fullScroll(View.FOCUS_RIGHT);((ScrollView)field(window,"vertical")).fullScroll(View.FOCUS_DOWN);});Thread.sleep(500);
        physicalClick("corner");check(js("window.cornerClicks").equals("1"),"Real tap reaches previously off-screen bottom-right control");
        shot("00-window-bottom-right");
        tap("窗口居中");int[] centered=windowPosition();check(Math.abs(centered[0]-first[0])<=2&&Math.abs(centered[1]-first[1])<=2,"Center button restores the center of the desktop window");
        check(observe().getString("draft").equals("WINDOW_DRAFT"),"Window dragging preserves typed form contents");
        check(requests==before,"Window dragging never reloads the source");
        shot("00-window-centered");
        js("var tail=document.createElement('div');tail.id='long-page';tail.style.height='3000px';document.body.appendChild(tail);");
        ui(()->((ScrollView)field(field(activity,"sourceWindow"),"vertical")).fullScroll(View.FOCUS_DOWN));Thread.sleep(400);
        drag(false,false);ui(()->check(web.getScrollY()>50,"At the window bottom, a new upward stroke continues scrolling the webpage"));
        js("document.getElementById('long-page').remove()");ui(()->web.scrollTo(0,0));tap("窗口居中");
    }
    private void repeatWindow()throws Exception{
        float natural=scale();
        for(int n=1;n<=3;n++){
            int[] before=windowPosition();drag(true,false);
            check(windowPosition()[0]>before[0]+30,"Round "+n+" still pans horizontally");
            tap("窗口居中");
            check(Math.abs(windowPosition()[0]-before[0])<=2,"Round "+n+" center is repeatable");
            boolean stable=true;
            for(int i=0;i<6;i++){Thread.sleep(150);stable&=Math.abs(scale()-natural)<.02;}
            check(stable,"Round "+n+" has no scale drift or flashing");
            check(observe().getString("draft").equals("WINDOW_DRAFT"),"Round "+n+" retains actual typed input");
        }
        drag(true,false);int[] saved=windowPosition();
        ui(()->{web.getWebViewClient().onPageFinished(web,URL);web.getWebViewClient().onPageFinished(web,URL);});Thread.sleep(400);
        check(Math.abs(windowPosition()[0]-saved[0])<=2,"Duplicate page callbacks do not reset the pan");
        ui(()->activity.recreate());Thread.sleep(1400);
        check(Math.abs(windowPosition()[0]-saved[0])<=2&&Math.abs(windowPosition()[1]-saved[1])<=2,"Activity recreation restores pan position");
        ui(()->web.loadUrl(URL+"?next=1"));Thread.sleep(1200);
        ui(()->check(web.getUrl().endsWith("?next=1"),"Fresh source navigation completes inside window"));
        ui(()->check(web.getWidth()>((View)field(activity,"sourceWindow")).getWidth()+50,"Navigation retains sliding surface"));
        ui(()->web.goBack());Thread.sleep(1200);
        ui(()->check(URL.equals(web.getUrl()),"Source back navigation returns to the earlier page"));
        tap("手机版");check(!SourceSession.desktop(activity,Models.Source.ZHIHU),"Mobile mode remains selectable");
        tap("桌面版");Thread.sleep(600);
        check(SourceSession.desktop(activity,Models.Source.ZHIHU),"Desktop mode remains selectable");
        ui(()->activity.finish());Thread.sleep(400);
        startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source","ZHIHU").putExtra("url",URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1400);
        ui(()->check(web.getWidth()>((View)field(activity,"sourceWindow")).getWidth()+50,"Closing and reopening creates the full window"));
        check(Math.abs(scale()-natural)<.05,"Reopened source retains natural scale");
    }
    @Override public void onStart(){
        SharedPreferences prefs=getTargetContext().getSharedPreferences("source-session",0);boolean had=prefs.contains("ZHIHU-desktop"),mode=prefs.getBoolean("ZHIHU-desktop",true);
        boolean hadWeb=prefs.contains("ZHIHU-web"),usedWeb=prefs.getBoolean("ZHIHU-web",false);
        try{
            output=new File(getTargetContext().getExternalFilesDir(null),"login-window-qa/run-"+System.currentTimeMillis());output.mkdirs();
            prefs.edit().putBoolean("ZHIHU-desktop",true).commit();
            startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source","ZHIHU").putExtra("url",URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(1600);
            if(variant.equals("landscape")){ui(()->activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1400);}
            slidingWindow();
            check(requests>0,"Positive fixture page loaded");
            check(observe().getString("viewport").contains("user-scalable=no"),"Original viewport is preserved without DOM rewrites");
            ui(()->{
                WebSettings settings=web.getSettings();
                check(!settings.supportZoom()&&!settings.getBuiltInZoomControls(),"Fixed window does not introduce competing zoom controls");
                check(settings.getUseWideViewPort()&&!settings.getLoadWithOverviewMode(),"Wide layout is stable without auto overview");
                check(settings.getUserAgentString().equals(Repository.DESKTOP_UA),"Desktop login user agent is retained");
                check(find(activity.getWindow().getDecorView(),"适应屏幕")==null&&find(activity.getWindow().getDecorView(),"切换缩放浏览")==null,"Obsolete layout controls are removed");
            });
            repeatWindow();
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');try{shot("error");}catch(Exception ignored){}}
        finally{
            try{if(activity!=null)ui(activity::finish);SharedPreferences.Editor restore=prefs.edit();if(had)restore.putBoolean("ZHIHU-desktop",mode);else restore.remove("ZHIHU-desktop");if(hadWeb)restore.putBoolean("ZHIHU-web",usedWeb);else restore.remove("ZHIHU-web");restore.commit();}catch(Exception e){failures++;}
            report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
            try(FileOutputStream out=new FileOutputStream(new File(output,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){failures++;}
            Bundle result=new Bundle();result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
