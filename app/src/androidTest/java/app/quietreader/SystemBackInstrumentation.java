package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.KeyEvent;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;

/** Real system BACK dispatch on target36, with controlled source pages. */
public final class SystemBackInstrumentation extends Instrumentation {
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private Activity activity;
    private WebView source;
    private volatile String loaded="";
    interface Action {void run()throws Exception;}
    private void ui(Action action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable ex){error[0]=ex;}});if(error[0]!=null)throw new Exception(error[0]);}
    private static Object field(Object object,String name)throws Exception{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void callActivityOnCreate(Activity activity,Bundle state){
        super.callActivityOnCreate(activity,state);
        try{
            if(activity instanceof MainActivity){((Repository)field(activity,"repo")).cancelPending();((ArticlePreloader)field(activity,"preloader")).pause();}
            if(activity instanceof LoginActivity){
                source=(WebView)field(activity,"web");source.stopLoading();
                source.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream("<html><body>合成返回导航页</body></html>".getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageFinished(WebView view,String url){loaded=url;}
                });source.clearHistory();source.loadUrl("https://www.zhihu.com/qr-system-back/one");
            }
        }catch(Exception ex){throw new RuntimeException(ex);}
    }
    private boolean page(String suffix)throws InterruptedException{long end=SystemClock.elapsedRealtime()+7000;while(!loaded.endsWith(suffix)&&SystemClock.elapsedRealtime()<end)Thread.sleep(50);return loaded.endsWith(suffix);}
    private void back()throws Exception{sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);waitForIdleSync();Thread.sleep(500);}
    @Override public void onStart(){
        try{
            check(getTargetContext().getApplicationInfo().targetSdkVersion==36,"APK targets Android16/API36");
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(400);
            ui(()->{Field current=MainActivity.class.getDeclaredField("current");current.setAccessible(true);current.set(activity,new Models.Item(Models.Source.ZHIHU,"合成阅读状态","https://www.zhihu.com/question/9876501060",""));});
            back();ui(()->check(!activity.isFinishing()&&field(activity,"current")==null,"system BACK returns reading state to source list"));
            back();ui(()->check(activity.isFinishing()||activity.isDestroyed(),"system BACK exits top-level reader"));
            activity=startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source","ZHIHU").putExtra("url","https://www.zhihu.com/qr-system-back/one").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(page("/one"),"controlled login/source first page loaded");
            ui(()->source.loadUrl("https://www.zhihu.com/qr-system-back/two"));check(page("/two"),"controlled source second page loaded");
            back();check(page("/one"),"system BACK navigates source history instead of closing window");
            ui(()->{source.clearHistory();});back();ui(()->check(activity.isFinishing()||activity.isDestroyed(),"system BACK closes source window at history root"));
        }catch(Throwable ex){failures++;report.append("ERROR ").append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append('\n');}
        finally{if(activity!=null)runOnMainSync(()->activity.finish());}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"system-back-probe.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ex){failures++;}
        Bundle result=new Bundle();result.putString("stream",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
