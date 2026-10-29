package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Controlled source WebView, actual R8 extraction/continuation/history; no production reflection. */
final class ReleaseTiebaIdentityProbe {
    private final Instrumentation test;
    private boolean childOnly,earlyGesture,imageFont,mediaOnly;
    private Activity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("R8 TIEBA EMOJI IDENTITY; synthetic source windows, not real account/content evidence\n");
    private final String url="https://tieba.baidu.com/p/"+System.currentTimeMillis();
    ReleaseTiebaIdentityProbe(Instrumentation test){this.test=test;}
    ReleaseTiebaIdentityProbe(Instrumentation test,boolean childOnly){this.test=test;this.childOnly=childOnly;}
    ReleaseTiebaIdentityProbe(Instrumentation test,boolean childOnly,boolean earlyGesture){this(test,childOnly);this.earlyGesture=earlyGesture;}
    ReleaseTiebaIdentityProbe(Instrumentation test,boolean childOnly,boolean earlyGesture,boolean imageFont){this(test,childOnly,earlyGesture);this.imageFont=imageFont;}
    ReleaseTiebaIdentityProbe mediaOnly(){mediaOnly=true;return this;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private interface Ready{boolean get()throws Exception;}
    private void await(String label,Ready ready)throws Exception{long until=SystemClock.elapsedRealtime()+22000;while(!ready.get()){if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException("Timeout: "+label);Thread.sleep(80);}}
    private WebView web(View node,boolean own){if(node instanceof WebView){String current=((WebView)node).getUrl();if(current!=null&&(own?current.startsWith("https://quiet-reader.invalid/"):current.startsWith(url)))return (WebView)node;}if(node instanceof ViewGroup)for(int i=0;i<((ViewGroup)node).getChildCount();i++){WebView found=web(((ViewGroup)node).getChildAt(i),own);if(found!=null)return found;}return null;}
    private WebView find(boolean own){WebView[] found={null};ui(()->found[0]=web(activity.getWindow().getDecorView(),own));return found[0];}
    private String js(WebView page,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] raw={null};ui(()->{boolean enabled=page.getSettings().getJavaScriptEnabled();page.getSettings().setJavaScriptEnabled(true);page.evaluateJavascript(script,result->{raw[0]=result;page.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("JS timeout");return raw[0];}
    private String rows(int round){String a="https://tb3.bdstatic.com/emoji/image_emoticon1.png",b="https://tb3.bdstatic.com/emoji/image_emoticon2.png";String image=round==0?"src='data:image/gif;base64,AA' data-original='"+a+"'":round==1?"src='data:image/gif;base64,AA' data-src='"+b+"'":"src='"+a+"'";String floor="<div class=pb-comment-item data-id=11><a class=user-name>父楼作者</a><div class=comment-content><div class=pb-rich-text>父楼正文</div></div><div class=pb-lzl-item><a class=head-name>同一楼内作者</a><div class=comment-content><div class=pb-rich-text><img class=emoji-img "+image+" alt='表情'></div></div></div></div>";for(int n=12;n<12+round;n++)floor+="<div class=pb-comment-item data-id="+n+"><div class=comment-content><div class=pb-rich-text>续载楼层"+n+"</div></div></div>";return floor;}
    private String sourceRows(int round){
        if(mediaOnly){String images="";for(int n=1;n<=2;n++)images+="<img class=emoji-img alt='表情' src='"+(n<=round?"https://tb3.bdstatic.com/emoji/image_emoticon"+n+".png":"data:image/gif;base64,AA")+"'>";
            return "<div class=pb-comment-item data-id=11><a class=user-name>父楼作者</a><div class=comment-content><div class=pb-rich-text>父楼正文</div></div><div class=pb-lzl-item data-id=101><a class=head-name>楼内作者</a><div class=comment-content><div class=pb-rich-text>楼内正文"+images+"</div></div></div></div>";
        }
        String html=rows(round);return earlyGesture&&!imageFont?html.replaceAll("<img[^>]*>","受控楼内文字"+(round%2)):html;
    }
    private String fixture(){StringBuilder body=new StringBuilder();for(int n=0;n<(imageFont?12:earlyGesture?16:55);n++)body.append("<p>受控长正文第").append(n).append("段，用于保留真实滚动触发，不代表真实新闻内容。</p>");return "<html><meta charset=utf-8><style>.pc-pb-box{height:400px;overflow:auto}.pb-comment-item{min-height:80px}</style><h1>受控贴吧表情身份</h1><div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>"+body+"</div></div><div class=pc-pb-reply-list><div class=pb-comment-list>"+sourceRows(0)+"</div></div></div></html>";}
    private void menu(String text)throws Exception{long end=SystemClock.elapsedRealtime()+5000;do{android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(!text.contentEquals(node.getText()==null?"":node.getText())||!node.isVisibleToUser())continue;while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}}Thread.sleep(30);}while(SystemClock.elapsedRealtime()<end);throw new IllegalStateException("Missing menu: "+text);}
    private View more(View root){if("更多选项".contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=more(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
    private void earlyForward(WebView page)throws Exception{
        String[] before={null};ui(()->before[0]=page.getUrl());
        ui(()->{View button=more(activity.getWindow().getDecorView());if(button==null)throw new IllegalStateException("Reader menu absent");button.performClick();});test.waitForIdleSync();menu("阅读字号");long fontAction=SystemClock.elapsedRealtime();menu("舒适 · 17");
        int[] bounds=new int[4];ui(()->{int[] p=new int[2];page.getLocationOnScreen(p);bounds[0]=p[0];bounds[1]=p[1];bounds[2]=page.getWidth();bounds[3]=page.getHeight();});float x=bounds[0]+bounds[2]*.5f,from=bounds[1]+bounds[3]*.9f,to=bounds[1]+bounds[3]*.12f;long start=SystemClock.uptimeMillis();
        for(int n=0;n<=12;n++){MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,from+(to-from)*n/12f,0);try{test.sendPointerSync(event);}finally{event.recycle();}if(n<12)Thread.sleep(15);}
        long elapsed=SystemClock.elapsedRealtime()-fontAction;float[] state=new float[3];ui(()->{state[0]=page.getScrollY();state[1]=page.getContentHeight()*page.getScale()-page.getScrollY()-page.getHeight();state[2]=page.getHeight();});report.append("EARLY_FONT_GESTURE elapsedMs=").append(elapsed).append(" y=").append(state[0]).append(" distance=").append(state[1]).append(" height=").append(state[2]).append('\n');check(elapsed<1200&&state[0]>0&&state[1]<state[2]*.75f,"real forward touch reaches continuation range within the actual font restoration guard; no private field/deadline mutation");
        if(imageFont){String[] after={null};ui(()->after[0]=page.getUrl());check(!Objects.equals(before[0],after[0]),"font action actually reloads the image-bearing document rather than textZoom only");await("inline PNG decoded after font rerender",()->"true".equals(js(page,"Array.from(document.images).some(i=>i.complete&&i.naturalWidth===24)")));check(true,"actual inline PNG is decoded after the image-dependent font rerender");}
    }
    private void nativeLabels(View view){if(view instanceof android.widget.TextView)report.append("NATIVE_LABEL ").append(((android.widget.TextView)view).getText()).append("\n");if(view instanceof android.view.ViewGroup)for(int i=0;i<((android.view.ViewGroup)view).getChildCount();i++)nativeLabels(((android.view.ViewGroup)view).getChildAt(i));}
    private int nested(WebView reader)throws Exception{String raw=js(reader,"document.querySelectorAll('.nested-reply').length");try{return Integer.parseInt(raw);}catch(Exception waiting){return -1;}}
    void run(){Context context=test.getTargetContext();android.content.SharedPreferences prefs=context.getSharedPreferences("source-session",0);boolean had=prefs.contains("TIEBA-web"),old=prefs.getBoolean("TIEBA-web",false);android.content.SharedPreferences fontPrefs=context.getSharedPreferences("MainActivity",0);boolean hadFont=fontPrefs.contains("font");int oldFont=fontPrefs.getInt("font",19);try{
        check(context.getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator target only");
        check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"target is non-debuggable R8 release");
        ui(()->prefs.edit().putBoolean("TIEBA-web",true).commit());
        if(earlyGesture)fontPrefs.edit().putInt("font",19).commit();
        JSONObject item=new JSONObject().put("source","TIEBA").put("title","受控贴吧表情身份").put("url",url).put("detail","").put("video",false);
        Application app=(Application)context.getApplicationContext();
        Application.ActivityLifecycleCallbacks hook=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity owner,Bundle state){if(!owner.getClass().getName().equals("app.quietreader.MainActivity"))return;ViewGroup host=owner.findViewById(android.R.id.content);host.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){boolean installed;
                public void onChildViewRemoved(View parent,View child){}
                public void onChildViewAdded(View parent,View child){if(installed||!(child instanceof WebView))return;installed=true;WebView source=(WebView)child;WebViewClient delegate=source.getWebViewClient();source.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream((request.isForMainFrame()?fixture():"").getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                    @Override public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return delegate.shouldOverrideUrlLoading(view,request);}
                });}
            });}
            public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        ui(()->app.registerActivityLifecycleCallbacks(hook));
        try{activity=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("source fixture DOM",()->{WebView source=find(false);return source!=null&&"true".equals(js(source,"!!document.querySelector('.pc-pb-box')"));});
        }finally{ui(()->app.unregisterActivityLifecycleCallbacks(hook));}
        await("initial extracted reader",()->find(true)!=null&&nested(find(true))==1);check(true,earlyGesture&&!imageFont?"actual R8 parser renders controlled text child; font-only guard excludes image rerender timing":"actual R8 source parser renders the first lazy emoji child");
        if(imageFont){
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(24,24,android.graphics.Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff307050);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();byte[] png=out.toByteArray();
            WebView own=find(true);ui(()->{WebViewClient delegate=own.getWebViewClient();own.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(!request.isForMainFrame()&&request.getUrl().toString().startsWith("https://tb3.bdstatic.com/emoji/"))return new WebResourceResponse("image/png",null,new ByteArrayInputStream(png));return delegate.shouldInterceptRequest(view,request);}
                @Override public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                @Override public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return delegate.shouldOverrideUrlLoading(view,request);}
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){delegate.onReceivedError(view,request,error);}
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){delegate.onReceivedHttpError(view,request,response);}
            });});report.append("IMAGE_TRANSPORT fixed PNG substituted only for fixture emoji URLs; no remote image/cache permission claim\n");
        }
        for(int round=1;round<=2;round++){
            WebView retained=find(false);check(retained!=null,"virtual source survives transfer for continuation");
            String nextRows=childOnly?rows(0).replace("image_emoticon1.png","image_emoticon"+(round+1)+".png"):sourceRows(round);
            if(mediaOnly){js(find(true),"scrollTo(0,0);true");Thread.sleep(100);}
            js(retained,"document.querySelector('.pb-comment-list').innerHTML="+JSONObject.quote(nextRows)+";true");
            WebView page=find(true);if(earlyGesture&&round==1)earlyForward(page);else{Thread.sleep(3400);js(page,"scrollTo(0,document.documentElement.scrollHeight);true");}
            final int expectedFloor=11+round;
            final int expectedChildren=round+1;
            final int expectedImages=round;
            await("automatic continuation round "+round,()->{WebView current=find(true);return current!=null&&(mediaOnly?String.valueOf(expectedImages).equals(js(current,"document.querySelectorAll('.nested-reply img').length")):childOnly?nested(current)==expectedChildren:"true".equals(js(current,"document.body.innerText.includes('续载楼层"+expectedFloor+"')")));});
            check(nested(find(true))==(mediaOnly?1:childOnly?expectedChildren:2),mediaOnly?"same explicit child gains media without any new child or floor":childOnly?"child-only source change reaches the unified reader without a new floor":round==1?"different lazy emoji survives same-parent merge":"loading the first emoji into src never duplicates it");
        }
        check("true".equals(js(find(true),"document.documentElement.scrollWidth<=innerWidth+1")),"rendered nested replies do not overflow horizontally");
        ui(activity::finish);test.waitForIdleSync();activity=null;
        JSONObject saved=null;for(Object value:context.getSharedPreferences("reading-history",0).getAll().values())try{JSONObject state=new JSONObject(String.valueOf(value));if(url.equals(state.optString("bodySource"))){saved=state;break;}}catch(JSONException ignored){}
        check(saved!=null&&saved.has("document"),"ordinary pause persists the actual continued body");JSONObject doc=saved.getJSONObject("document");check(doc.optInt("tiebaReplyRevision")==4,"persisted body uses rendered source identity revision4");
        JSONArray sections=doc.getJSONArray("sections");String parent="";Set<String> ids=new HashSet<>();int children=0;for(int n=0;n<sections.length();n++){JSONObject section=sections.getJSONObject(n);if(section.optString("sourcePostId").equals("11"))parent=section.getString("id");}
        for(int n=0;n<sections.length();n++){JSONObject section=sections.getJSONObject(n);if(section.optBoolean("nestedReply")){children++;check(!parent.isEmpty()&&parent.equals(section.optString("parentSectionId")),"emoji child retains the explicit parent floor");ids.add(section.getString("id"));}}
        int expected=mediaOnly?1:childOnly?3:2;check(children==expected&&ids.size()==expected,"history keeps exactly "+expected+" distinct emoji child identities");
        if(mediaOnly){int images=0,floors=0;for(int n=0;n<sections.length();n++){JSONObject section=sections.getJSONObject(n);if(!section.optString("sourcePostId").isEmpty())floors++;if(section.optBoolean("nestedReply")){check("101".equals(section.optString("sourceChildId")),"history retains validated raw child identity");JSONArray blocks=section.getJSONArray("blocks");for(int b=0;b<blocks.length();b++)images+=blocks.getJSONObject(b).getJSONArray("images").length();}}check(images==2&&floors==1,"both media-only batches survive ordinary pause with unchanged source identities");}
        if(childOnly){int floors=0;for(int n=0;n<sections.length();n++)if(!sections.getJSONObject(n).optString("sourcePostId").isEmpty())floors++;check(floors==1,"both automatic batches completed with the same single source floor");}
    }catch(Throwable error){failures++;try{if(activity!=null){WebView src=find(false),own=find(true);ui(()->nativeLabels(activity.getWindow().getDecorView()));report.append("DIAGNOSTIC source=").append(src!=null).append(" reader=").append(own!=null).append("\n");if(src!=null)report.append("SOURCE_SHAPE ").append(js(src,"JSON.stringify({modern:!!document.querySelector('.pc-pb-box'),roots:document.querySelectorAll('.pb-rich-text').length,rows:document.querySelectorAll('.pb-comment-item').length})")).append("\n");if(own!=null)report.append("READER_SHAPE ").append(js(own,"JSON.stringify({nested:document.querySelectorAll('.nested-reply').length,parts:document.querySelectorAll('.part').length})")).append("\n");}}catch(Exception diagnostic){report.append("DIAGNOSTIC_ERROR ").append(diagnostic.getClass().getSimpleName()).append("\n");}report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
    finally{if(activity!=null){ui(activity::finish);test.waitForIdleSync();}ui(()->{android.content.SharedPreferences.Editor edit=prefs.edit();if(had)edit.putBoolean("TIEBA-web",old);else edit.remove("TIEBA-web");edit.commit();if(earlyGesture){android.content.SharedPreferences.Editor fontEdit=fontPrefs.edit();if(hadFont)fontEdit.putInt("font",oldFont);else fontEdit.remove("font");fontEdit.commit();}});}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
