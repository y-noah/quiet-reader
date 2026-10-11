package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Black-box UI assertions against the installed, minified release; no internal reflection. */
public final class ReleaseInstrumentation extends Instrumentation {
    private Activity home; private File output; private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    interface Work {void run() throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void ui(Work work)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{work.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);}
    private void check(boolean pass,String name){checks++;if(!pass)failures++;report.append(pass?"PASS ":"FAIL ").append(name).append('\n');}
    private View find(View view,String name){if(name.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof TextView&&name.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),name);if(found!=null)return found;}return null;}
    private boolean contains(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    private android.webkit.WebView web(View v){if(v instanceof android.webkit.WebView)return (android.webkit.WebView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){android.webkit.WebView found=web(((ViewGroup)v).getChildAt(n));if(found!=null)return found;}return null;}
    private void shot(String name)throws Exception{Thread.sleep(350);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Screenshot missing");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private String js(String expression)throws Exception{java.util.concurrent.CountDownLatch latch=new java.util.concurrent.CountDownLatch(1);String[] value={""};ui(()->web(home.getWindow().getDecorView()).evaluateJavascript(expression,r->{value[0]=r;latch.countDown();}));if(!latch.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IOException("JS timeout");return value[0];}
    @Override public void onStart(){Bundle result=new Bundle();try{
        output=new File(getTargetContext().getExternalFilesDir(null),"release-qa/run-"+System.currentTimeMillis());output.mkdirs();
        android.content.pm.PackageInfo info=getTargetContext().getPackageManager().getPackageInfo("app.quietreader",0);
        check("0.3.34".equals(info.versionName)&&info.versionCode==45,"exact 0.3.34 release installed");
        home=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(900);
        ui(()->{View root=home.getWindow().getDecorView();check(find(root,"News")!=null,"News brand");check(find(root,"总榜")==null,"removed board absent");check(find(root,"知乎").isSelected(),"default Zhihu board");});
        shot("01-release-home");
        for(String name:new String[]{"知乎","微博","虎扑","财联社","V2EX"}){
            ui(()->{View tab=find(home.getWindow().getDecorView(),name);check(tab instanceof TextView,"native text tab "+name);check(tab!=null&&tab.performClick(),"tab click "+name);});Thread.sleep(200);
            ui(()->{TextView tab=(TextView)find(home.getWindow().getDecorView(),name);check(tab.isSelected(),"tab selected "+name);check(!tab.getTypeface().isBold(),"consistent regular weight "+name);check(tab.getHeight()>=48*home.getResources().getDisplayMetrics().density-1,"48dp touch height "+name);});
        }
        ui(()->find(home.getWindow().getDecorView(),"知乎").performClick());Thread.sleep(400);shot("02-release-zhihu");
        ui(home::finish);
        home=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.LoginActivity").putExtra("source","ZHIHU").putExtra("url","https://www.zhihu.com/question/9876501029").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->{
            View root=home.getWindow().getDecorView();
            check(find(root,"窗口居中")!=null&&find(root,"返回阅读")!=null,"release source window controls visible");
            check(find(root,"适应屏幕")==null&&find(root,"缩小")==null,"release obsolete zoom controls absent");
            HorizontalScrollView h=(HorizontalScrollView)find(root,"来源页面左右滑动窗口");
            ScrollView v=(ScrollView)find(root,"来源页面上下滑动窗口");
            check(h!=null&&v!=null,"release two-axis native window exists");
            android.webkit.WebView w=(android.webkit.WebView)((ViewGroup)h.getChildAt(0)).getChildAt(0);
            w.stopLoading();w.loadDataWithBaseURL("https://www.zhihu.com/","<html><body style='margin:0;width:1280px;height:960px'>Release sliding window</body></html>","text/html","UTF-8",null);
        });Thread.sleep(1000);
        ui(()->{
            View root=home.getWindow().getDecorView();
            HorizontalScrollView h=(HorizontalScrollView)find(root,"来源页面左右滑动窗口");
            ScrollView v=(ScrollView)find(root,"来源页面上下滑动窗口");
            check(h.getChildAt(0).getWidth()>h.getWidth()&&v.getChildAt(0).getHeight()>v.getHeight(),"release canvas overflows on both axes");
            h.scrollTo(0,0);v.scrollTo(0,0);find(root,"窗口居中").performClick();
            check(h.getScrollX()>0&&v.getScrollY()>0,"release center button moves both axes");
        });shot("03-release-source-window");
        ui(home::finish);
        home=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.LoginActivity").putExtra("source","BILIBILI").putExtra("url","https://www.bilibili.com/").putExtra("browse",true).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->{View root=home.getWindow().getDecorView();check(find(root,"登录滑窗")!=null&&find(root,"返回热榜")!=null,"release source browser has login and return controls");check(find(root,"窗口居中")==null&&find(root,"读取当前页")==null,"release browser has no duplicate extraction/window controls");android.webkit.WebView w=web(root);check(w!=null&&w.getSettings().supportZoom(),"release browser can zoom independently");w.stopLoading();});

        ui(()->{
            android.webkit.WebView w=web(home.getWindow().getDecorView());android.webkit.WebViewClient original=w.getWebViewClient();
            w.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest r){
                    String html="<html><head><meta name=viewport content='width=1280'><style>body{min-width:1100px}#mirror-vdcon{width:1100px}.left-container{width:800px}</style></head><body><div id=app><div id=mirror-vdcon><div class=left-container><div id=viewbox_report><h1>Release mobile video title</h1></div><div id=playerWrap><video></video></div></div></div></div></body></html>";
                    return new android.webkit.WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageFinished(android.webkit.WebView view,String url){original.onPageFinished(view,url);}
            });w.loadUrl("https://www.bilibili.com/video/BV123abc/");
        });Thread.sleep(1300);
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] layout={""};
        ui(()->web(home.getWindow().getDecorView()).evaluateJavascript("document.documentElement.dataset.qrMobile==='BILIBILI'&&innerWidth<500&&document.querySelector('#playerWrap').getBoundingClientRect().right<=innerWidth+1",value->{layout[0]=value;done.countDown();}));
        check(done.await(5,java.util.concurrent.TimeUnit.SECONDS)&&"true".equals(layout[0]),"minified release actually reflows source content");
        shot("04-release-mobile-video");
        ui(home::finish);
        home=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.LoginActivity").putExtra("source","DOUYIN").putExtra("url","https://www.douyin.com/hot/12345").putExtra("browse",true).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        ui(()->{
            android.webkit.WebView w=web(home.getWindow().getDecorView());w.stopLoading();android.webkit.WebViewClient original=w.getWebViewClient();
            w.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView view,android.webkit.WebResourceRequest r){
                    String html="""
                        <html><head><meta name=viewport content='width=1280'></head><body><div id=root><div class=leftContainer><div class=video-detail-container></div></div></div><script>
                        window.__pace_f=[[1,'7:'+JSON.stringify([null,null,null,{aweme:{statusCode:0,detail:{awemeId:'123',desc:'Release public video',video:{playAddr:[{src:'https://v11-weba.douyinvod.com/fixture.mp4'}]},status:{privateStatus:0}}},comment:{statusCode:0,comments:[{cid:'1',text:'Release comment',user:{nickname:'Reader'}}],cursor:20,hasMore:false}}])]];
                        </script></body></html>
                        """;
                    return new android.webkit.WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onPageFinished(android.webkit.WebView view,String url){original.onPageFinished(view,url);}
            });w.loadUrl("https://www.douyin.com/hot/12345");
        });Thread.sleep(3000);
        check(js("!!document.querySelector('#qr-douyin-reader h1')&&document.querySelectorAll('.qr-douyin-comment').length===1").equals("true"),"minified release renders independent Douyin video and comments");
        js("history.replaceState({},'', '/video/123');document.getElementById('root').replaceChildren()");Thread.sleep(350);
        check(js("document.querySelectorAll('#qr-douyin-reader').length===1").equals("true"),"minified release survives source canonical route rebuild");
        ui(()->find(home.getWindow().getDecorView(),"原网页").performClick());
        check(js("!document.querySelector('#qr-douyin-reader')").equals("true"),"minified release original-page toggle cleans reader");
        ui(()->find(home.getWindow().getDecorView(),"手机排版").performClick());Thread.sleep(400);
        check(js("!!document.querySelector('#qr-douyin-reader')").equals("true"),"minified release mobile toggle restores reader");
        shot("05-release-douyin-reader");

    }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}finally{try{if(home!=null)ui(home::finish);report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');try(FileOutputStream out=new FileOutputStream(new File(output,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception error){failures++;}result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}}
}
