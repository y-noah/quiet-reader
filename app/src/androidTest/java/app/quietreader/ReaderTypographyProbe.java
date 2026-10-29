package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Actual reader under configured display/font scale; adversarial text, not live source quality. */
final class ReaderTypographyProbe {
    private android.app.UiAutomation automation;
    private final Instrumentation test;private final boolean dark,normalText,screenReader;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC reader typography; actual WebView and display configuration\n");
    ReaderTypographyProbe(Instrumentation test,boolean dark,boolean normalText,boolean screenReader){this.test=test;this.dark=dark;this.normalText=normalText;this.screenReader=screenReader;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(Object owner,String key)throws Exception{Field field=owner.getClass().getDeclaredField(key);field.setAccessible(true);return field.get(owner);}
    private String js(WebView web,String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={""};test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Reader DOM sampling timeout");return value[0];}
    void run(){run(false);}
    void run(boolean controlOnly){
        android.content.SharedPreferences prefs=test.getTargetContext().getSharedPreferences("appearance",0);boolean had=prefs.contains("mode");int old=prefs.getInt("mode",0);MainActivity reader=null;automation=test.getUiAutomation(screenReader?android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES:0);int oldServiceFlags=automation.getServiceInfo().flags;
        try{
            if(screenReader){android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;info.flags&=~android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;automation.setServiceInfo(info);Thread.sleep(500);}
            report.append("AUTOMATION screenReader=").append(screenReader).append(" flags=").append(automation.getServiceInfo().flags).append('\n');
            android.view.accessibility.AccessibilityManager manager=(android.view.accessibility.AccessibilityManager)test.getTargetContext().getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
            java.util.List<android.accessibilityservice.AccessibilityServiceInfo> services;boolean actualService;long serviceDeadline=SystemClock.elapsedRealtime()+5000;
            do{services=manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK);actualService=false;for(android.accessibilityservice.AccessibilityServiceInfo service:services)if(service.getId().endsWith("ReaderProbeAccessibilityService"))actualService=true;if(!screenReader||actualService||SystemClock.elapsedRealtime()>=serviceDeadline)break;Thread.sleep(100);}while(true);
            for(android.accessibilityservice.AccessibilityServiceInfo service:services)report.append("VISIBLE_SERVICE ").append(service.getId()).append('\n');
            report.append("VISIBLE_SERVICE_COUNT ").append(services.size()).append('\n');
            if(screenReader){check(actualService,"WebView can observe the actual emulator test accessibility service");if(!actualService)throw new IllegalStateException("Required bound test accessibility service is not visible");}
            prefs.edit().putInt("mode",dark?2:1).commit();String url="https://bbs.hupu.com/"+System.currentTimeMillis()+".html";
            Item item=new Item(Source.HUPU,"统一文字阅读排版检查",url,"");Document body=new Document();body.url=url;body.title="中文长标题与LongUnbrokenTitle"+"A".repeat(160);body.byline="VeryLongAuthor"+"B".repeat(180);body.notice="中文阅读提示"+"C".repeat(160);body.discussionPage=1;body.nextUrl=url.replace(".html","-2.html");
            Section main=new Section("hupu-main-123",normalText?"主帖 · 作者":"主帖 · "+body.byline,false);main.blocks.add(new Block("text","中文段落与长链接：https://example.com/"+"path".repeat(70)));body.sections.add(main);
            Section comment=new Section("hupu-reply-1",normalText?"回复 · 读者":"回复 · VeryLongReplyAuthor"+"D".repeat(180),false);comment.blocks.add(new Block("text","这是一段短回复，作者与正文应完整换行。"));body.sections.add(comment);
            Section folded=new Section("hupu-reply-2",normalText?"回答 · 另一位读者":"回答 · LongFoldActionAuthor"+"E".repeat(180),true);folded.blocks.add(new Block("text","这是一段需要折叠的长回复，用来验证展开按钮中的作者与操作文字仍能完整换行。".repeat(50)));body.sections.add(folded);
            if(normalText){body.title="普通中文阅读标题";body.byline="作者";body.notice="阅读提示";}
            report.append("FIXTURE normalText=").append(normalText).append('\n');
            for(Section part:body.sections)body.blocks.addAll(part.blocks);ArticleVersion.capture(item,body);
            test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(body);repo.close();});
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity owner=reader;
            long until=SystemClock.elapsedRealtime()+8000;WebView web=null;
            while(SystemClock.elapsedRealtime()<until){web=(WebView)field(owner,"readerWeb");if(web!=null&&"true".equals(js(web,"document.readyState==='complete'&&!!document.querySelector('.reader-content')")))break;Thread.sleep(50);}
            if(web==null)throw new IllegalStateException("Reader not displayed");WebView target=web;
            test.runOnMainSync(()->{try{Field font=MainActivity.class.getDeclaredField("font");font.setAccessible(true);font.setInt(owner,28);Method reload=MainActivity.class.getDeclaredMethod("reloadReader",Document.class);reload.setAccessible(true);reload.invoke(owner,body);}catch(Exception error){throw new IllegalStateException(error);}});
            String expectedPage=(String)field(owner,"readerPageUrl");boolean ready=false;
            until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){if("true".equals(js(target,"location.href==="+org.json.JSONObject.quote(expectedPage)+"&&document.readyState==='complete'&&!!document.querySelector('.discussion')&&parseFloat(getComputedStyle(document.querySelector('p')).fontSize)>25"))){ready=true;break;}Thread.sleep(50);}
            if(!ready)throw new IllegalStateException("Current maximum-font reader page did not commit");
            if(!controlOnly){
            android.content.res.Configuration config=owner.getResources().getConfiguration();report.append("CONFIG widthDp=").append(config.screenWidthDp).append(" fontScale=").append(config.fontScale).append(" readerFont=28 dark=").append(dark).append('\n');
            org.json.JSONObject metrics=new org.json.JSONObject(js(target,"(()=>{const all=Array.from(document.querySelectorAll('h1,.meta,.action,.notice,p'));return {width:innerWidth,scroll:document.documentElement.scrollWidth,font:parseFloat(getComputedStyle(document.querySelector('p')).fontSize),bg:getComputedStyle(document.body).backgroundColor,overflow:all.filter(e=>e.getBoundingClientRect().right>innerWidth+1||e.scrollWidth>e.clientWidth+1).map(e=>({tag:e.tagName,cls:e.className,width:e.clientWidth,scroll:e.scrollWidth})),actions:Array.from(document.querySelectorAll('.action,.section-nav a,.image-open,.media-fallback a,.notice summary')).map(e=>({height:e.getBoundingClientRect().height,width:e.getBoundingClientRect().width}))}})()"));
            report.append("METRICS ").append(metrics).append('\n');check(metrics.getDouble("font")>=27,"maximum app reading font is applied");check(metrics.getDouble("scroll")<=metrics.getDouble("width")+1,"whole reader has no horizontal overflow");check(metrics.getJSONArray("overflow").length()==0,"long title, authors, links and actions wrap within their boxes");check(metrics.getString("bg").equals(dark?"rgb(31, 32, 37)":"rgb(250, 250, 250)"),"reader uses explicit selected theme");
            org.json.JSONArray actions=metrics.getJSONArray("actions");boolean sized=actions.length()>0;for(int n=0;n<actions.length();n++){org.json.JSONObject action=actions.getJSONObject(n);sized&=action.getDouble("height")>=48&&action.getDouble("width")>=48;}check(sized,"independent reader actions have at least 48 by 48 CSS px targets");
            String name="layout-reader-"+config.screenWidthDp+"-"+Math.round(config.fontScale*100)+(dark?"-dark":"-light");screenshot(name+".png");
            js(target,"document.querySelector('.section-label').scrollIntoView({block:'start'})");Thread.sleep(200);screenshot(name+"-author.png");
            js(target,"document.querySelector('.action').scrollIntoView({block:'center'})");Thread.sleep(200);screenshot(name+"-action.png");
            report.append("BEFORE_ACTION ").append(js(target,"(()=>{const a=document.querySelector('.action');return {top:a.getBoundingClientRect().top,height:a.getBoundingClientRect().height,view:innerHeight,scroll:scrollY,href:a.href,expanded:a.getAttribute('aria-expanded')}})()")).append('\n');
            Document actual=(Document)field(owner,"reading");
            report.append("READING_MODEL sections=").append(actual==null?-1:actual.sections.size()).append(" expanded=").append(field(owner,"expanded")).append('\n');
            if(actual!=null)for(Section part:actual.sections)report.append("SECTION id=").append(part.id).append(" answer=").append(part.answer).append('\n');
            check(clickToggle("展开阅读"),"folded content exposes a clickable accessibility node");
            test.waitForIdleSync();Thread.sleep(300);
            boolean didExpand=waitToggle(target,true);check(didExpand,"accessibility click expands content and updates aria-expanded");
            if(!didExpand){pointerToggle(target);boolean pointerExpanded=waitToggle(target,true);report.append("POINTER_COMPARISON expanded=").append(pointerExpanded).append(" native=").append(field(owner,"expanded")).append('\n');didExpand=pointerExpanded;}
            report.append("AFTER_CLICK expanded=").append(field(owner,"expanded")).append('\n');
            js(target,"document.querySelector('.action').scrollIntoView({block:'center'})");Thread.sleep(200);
            check(clickToggle("收起"),"expanded content retains its accessibility click action");
            test.waitForIdleSync();Thread.sleep(300);
            check(didExpand&&waitToggle(target,false),"accessibility click folds previously expanded content and restores aria-expanded");
            }
            minimalLinkControl(target);
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags=oldServiceFlags;automation.setServiceInfo(info);MainActivity owner=reader;test.runOnMainSync(()->{if(owner!=null)owner.finish();});test.waitForIdleSync();android.content.SharedPreferences.Editor edit=prefs.edit();if(had)edit.putInt("mode",old);else edit.remove("mode");edit.commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private boolean waitToggle(WebView web,boolean expanded)throws Exception{
        long until=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<until){if("true".equals(js(web,"(()=>{const a=document.querySelector('.action[href*=\"toggle?\"]');return !!a&&a.getAttribute('aria-expanded')==='"+expanded+"'&&document.querySelectorAll('.preview').length==="+(expanded?0:1)+"})()")))return true;Thread.sleep(50);}report.append("TOGGLE_TIMEOUT ").append(js(web,"({url:location.href,actions:Array.from(document.querySelectorAll('.action')).map(a=>({text:a.textContent,expanded:a.getAttribute('aria-expanded')}))})")).append('\n');return false;
    }
    private android.view.accessibility.AccessibilityNodeInfo accessibilityRoot(){
        if(android.os.Build.VERSION.SDK_INT>=33)automation.clearCache();
        return automation.getRootInActiveWindow();
    }
    private boolean clickToggle(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<until){if(clickToggle(accessibilityRoot(),label))return true;Thread.sleep(100);}return false;
    }
    private boolean clickToggle(android.view.accessibility.AccessibilityNodeInfo node,String label){
        if(node==null)return false;
        for(int n=0;n<node.getChildCount();n++)if(clickToggle(node.getChild(n),label))return true;
        CharSequence text=node.getText(),description=node.getContentDescription();
        if(node.isVisibleToUser()&&node.isEnabled()&&node.isClickable()&&((text!=null&&text.toString().contains(label))||(description!=null&&description.toString().contains(label)))){android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);report.append("NODE_STATE ").append(node).append('\n');report.append("ACCESSIBILITY_ACTION ").append(node.getClassName()).append(" text=").append(text).append(" description=").append(description).append(" bounds=").append(bounds).append(" actions=").append(node.getActionList()).append('\n');boolean clicked=node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK);report.append("ACCESSIBILITY_CLICK accepted=").append(clicked).append('\n');return clicked;}
        return false;
    }
    private void pointerToggle(WebView web)throws Exception{
        org.json.JSONObject point=new org.json.JSONObject(js(web,"(()=>{const a=document.querySelector('.action');const r=a.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2,dpr:devicePixelRatio}})()"));
        int[] location=new int[2];test.runOnMainSync(()->web.getLocationOnScreen(location));float x=(float)(location[0]+point.getDouble("x")*point.getDouble("dpr")),y=(float)(location[1]+point.getDouble("y")*point.getDouble("dpr"));
        long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,x,y,0),up=android.view.MotionEvent.obtain(now,now+100,android.view.MotionEvent.ACTION_UP,x,y,0);
        try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}test.waitForIdleSync();Thread.sleep(300);
    }
    private void minimalLinkControl(WebView original)throws Exception{
        android.content.pm.PackageInfo provider=WebView.getCurrentWebViewPackage();
        report.append("WEBVIEW_PROVIDER ").append(provider==null?"unknown":provider.packageName+" "+provider.versionName).append(" serviceFlags=").append(automation.getServiceInfo().flags).append('\n');
        java.util.concurrent.atomic.AtomicInteger navigations=new java.util.concurrent.atomic.AtomicInteger();CountDownLatch loaded=new CountDownLatch(1);
        WebView[] control={null};android.view.ViewGroup parent=(android.view.ViewGroup)original.getParent();int index=parent.indexOfChild(original);android.view.ViewGroup.LayoutParams layout=original.getLayoutParams();
        try{
            test.runOnMainSync(()->{WebView web=new WebView(original.getContext());control[0]=web;web.getSettings().setJavaScriptEnabled(false);parent.removeView(original);parent.addView(web,index,layout);web.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){report.append("MINIMAL_NAVIGATION ").append(request.getUrl()).append('\n');navigations.incrementAndGet();return true;}
                @Override public void onPageFinished(WebView view,String url){loaded.countDown();}
            });web.loadDataWithBaseURL("https://example.org/","<!doctype html><html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><a style='display:block;padding:24px;font-size:22px' href='https://example.org/accessibility-action'>最小链接检查</a><input type='checkbox' aria-label='最小选框检查' style='width:48px;height:48px'></body></html>","text/html","UTF-8",null);});
            if(!loaded.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Minimal link did not load");test.waitForIdleSync();Thread.sleep(400);
            boolean accepted=clickToggle("最小链接检查");Thread.sleep(1000);test.waitForIdleSync();
            report.append("MINIMAL_FRESH_LINK accepted=").append(accepted).append(" navigations=").append(navigations.get()).append('\n');
            check(accepted&&navigations.get()==1,"fresh plain WebView link accessibility activates navigation without reader markup or DOM sampling");
            Boolean before=checkedState(accessibilityRoot(),"最小选框检查");boolean checkboxClick=clickToggle("最小选框检查");Thread.sleep(1000);Boolean after=checkedState(accessibilityRoot(),"最小选框检查");
            report.append("MINIMAL_CHECKBOX before=").append(before).append(" accepted=").append(checkboxClick).append(" after=").append(after).append('\n');
            check(Boolean.FALSE.equals(before)&&checkboxClick&&Boolean.TRUE.equals(after),"fresh plain WebView checkbox accessibility changes native checkbox state");
        }finally{test.runOnMainSync(()->{if(control[0]!=null){parent.removeView(control[0]);control[0].destroy();}if(original.getParent()==null)parent.addView(original,index,layout);});}
    }
    private Boolean checkedState(android.view.accessibility.AccessibilityNodeInfo node,String label){
        if(node==null)return null;CharSequence text=node.getText(),description=node.getContentDescription();
        if(node.isCheckable()&&((text!=null&&text.toString().contains(label))||(description!=null&&description.toString().contains(label))))return node.isChecked();
        for(int n=0;n<node.getChildCount();n++){Boolean result=checkedState(node.getChild(n),label);if(result!=null)return result;}return null;
    }
    private void screenshot(String name)throws IOException{android.graphics.Bitmap screenshot=automation.takeScreenshot();if(screenshot==null)throw new IOException("Screenshot missing");try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}report.append("SCREENSHOT ").append(name).append('\n');}
}
