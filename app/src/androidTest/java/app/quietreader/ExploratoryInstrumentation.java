package app.quietreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.WebView;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static app.quietreader.Models.*;

/** Independent skeptical-reader journeys. All reading text is explicitly synthetic.
 * Debug-only reflection injects documents, but links are activated with real touch events.
 * Never describes these fixtures as proof of remote-platform extraction or account login.
 */
public final class ExploratoryInstrumentation extends Instrumentation {
    private MainActivity activity;
    private String mode="core";
    private final String runName="run-"+System.currentTimeMillis();
    private int checks, failures;
    private final StringBuilder report=new StringBuilder();
    private final List<String> screenshots=new ArrayList<>();
    private final String fixtureUrl="https://bbs.hupu.com/999999999.html";
    interface Checked { void run() throws Exception; }
    @Override public void onCreate(Bundle arguments){super.onCreate(arguments);if(arguments!=null)mode=arguments.getString("mode","core");start();}
    @Override public void callActivityOnResume(Activity page){super.callActivityOnResume(page);if(page instanceof MainActivity)activity=(MainActivity)page;}
    private void ui(Checked action)throws Exception{
        Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});
        waitForIdleSync();if(error[0]!=null)throw new Exception(error[0]);
    }
    private Object field(String name)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(activity);}
    private void set(String name,Object value)throws Exception{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(activity,value);}
    private void invoke(String name,Class<?>[] types,Object... values)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,values);}
    private WebView web()throws Exception{return (WebView)field("readerWeb");}
    private void check(boolean pass,String description){checks++;if(!pass)failures++;report.append(pass?"PASS ":"FAIL ").append(description).append('\n');}
    private void journey(String name,Checked action){
        report.append("\nJOURNEY ").append(name).append('\n');
        try{action.run();}catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');try{shot(name+"-error");}catch(Exception ignored){}}
        Bundle progress=new Bundle();progress.putString("stream",report.toString());sendStatus(0,progress);
    }
    private void shot(String name)throws Exception{
        waitForIdleSync();Thread.sleep(500);
        Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new Exception("Screenshot unavailable");
        File directory=outputDirectory();directory.mkdirs();
        try(FileOutputStream out=new FileOutputStream(new File(directory,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
        screenshots.add(name+".png");
    }
    private File outputDirectory(){return new File(getTargetContext().getExternalFilesDir(null),"exploratory-qa/"+runName);}
    private JSONObject dom(String expression)throws Exception{
        CountDownLatch complete=new CountDownLatch(1);String[] response={null};
        ui(()->{WebView w=web();w.getSettings().setJavaScriptEnabled(true);w.evaluateJavascript("JSON.stringify("+expression+")",value->{w.getSettings().setJavaScriptEnabled(false);response[0]=value;complete.countDown();});});
        if(!complete.await(5,TimeUnit.SECONDS))throw new Exception("DOM inspection timeout");
        Object decoded=new JSONTokener(response[0]).nextValue();
        if(!(decoded instanceof String))throw new AssertionError("Own-reader DOM inspection returned no JSON string; check test-script compatibility and page readiness");
        return new JSONObject((String)decoded);
    }
    private JSONObject box(String selector)throws Exception{
        return dom("(()=>{let e=document.querySelector("+JSONObject.quote(selector)+");if(!e)return {found:false};let r=e.getBoundingClientRect();return {found:true,x:r.x,y:r.y,width:r.width,height:r.height,documentY:r.y+scrollY,viewport:innerHeight,text:e.textContent}})()");
    }
    private void scrollCss(double y)throws Exception{ui(()->web().scrollTo(0,(int)Math.round(y*web().getScale())));Thread.sleep(300);}
    private void tap(String selector)throws Exception{
        JSONObject rectangle=box(selector);if(!rectangle.getBoolean("found"))throw new Exception("Missing touch target: "+selector);
        scrollCss(rectangle.getDouble("documentY")-rectangle.getDouble("viewport")/3);
        rectangle=box(selector);float[] point=new float[2];final JSONObject target=rectangle;
        ui(()->{WebView w=web();int[] location=new int[2];w.getLocationOnScreen(location);float scale=w.getScale();point[0]=location[0]+(float)(target.getDouble("x")+target.getDouble("width")/2)*scale;point[1]=location[1]+(float)(target.getDouble("y")+target.getDouble("height")/2)*scale;});
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);MotionEvent up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,point[0],point[1],0);
        try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}Thread.sleep(800);waitForIdleSync();
    }
    private TextView textView(View root,String label,boolean contains){
        if(root instanceof TextView){String text=((TextView)root).getText().toString(),description=String.valueOf(root.getContentDescription());if(contains?(text.contains(label)||description.contains(label)):(text.equals(label)||description.equals(label)))return (TextView)root;}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=textView(((ViewGroup)root).getChildAt(i),label,contains);if(found!=null)return found;}return null;
    }
    private View tabs(View v){return control(v,"平台选择",false);}
    private View control(View v,String label,boolean contains){String d=String.valueOf(v.getContentDescription()),t=v instanceof TextView?((TextView)v).getText().toString():"";if(contains?(d.contains(label)||t.contains(label)):(d.equals(label)||t.equals(label)))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=control(((ViewGroup)v).getChildAt(i),label,contains);if(found!=null)return found;}return null;}
    private void pressNative(String text,boolean contains)throws Exception{Rect bounds=new Rect();ui(()->{View v=control(activity.getWindow().getDecorView(),text,contains);if(v==null||!v.getGlobalVisibleRect(bounds)||bounds.width()<v.getWidth()-2||bounds.height()<v.getHeight()-2)throw new Exception("Native control not fully visible: "+text);});long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}Thread.sleep(300);}
    private void menu(String label)throws Exception{pressNative("更多选项",false);choose(label);}
    private boolean accessibleLabel(AccessibilityNodeInfo n,String label){if(n==null)return false;if(label.contentEquals(n.getText()==null?"":n.getText())||label.contentEquals(n.getContentDescription()==null?"":n.getContentDescription()))return true;for(int i=0;i<n.getChildCount();i++)if(accessibleLabel(n.getChild(i),label))return true;return false;}
    private void menuContents(boolean reading)throws Exception{pressNative("更多选项",false);AccessibilityNodeInfo tree=getUiAutomation().getRootInActiveWindow();for(String label:reading?new String[]{"重新读取","来源 / 登录","阅读字号","外观"}:new String[]{"刷新榜单","来源 / 登录","外观","全部平台"})check(accessibleLabel(tree,label),"Overflow action reachable: "+label);check(!accessibleLabel(tree,"收藏"),"Overflow has no favorites entry");shot((reading?"09c-reader-overflow-":"09b-home-overflow-")+screenshots.size());sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);Thread.sleep(200);check(!accessibleLabel(getUiAutomation().getRootInActiveWindow(),reading?"重新读取":"刷新榜单"),"Back dismisses overflow without navigating");}
    private boolean clickAccessible(AccessibilityNodeInfo node,String label){
        if(node==null)return false;CharSequence text=node.getText();
        if(text!=null&&text.toString().equals(label)){AccessibilityNodeInfo clickable=node;for(int i=0;i<4&&clickable!=null;i++,clickable=clickable.getParent())if(clickable.isClickable())return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);}
        for(int i=0;i<node.getChildCount();i++)if(clickAccessible(node.getChild(i),label))return true;return false;
    }
    private void choose(String label)throws Exception{for(int n=0;n<15;n++){if(android.os.Build.VERSION.SDK_INT>=35)getUiAutomation().clearCache();boolean clicked=clickAccessible(getUiAutomation().getRootInActiveWindow(),label);if(!clicked)for(android.view.accessibility.AccessibilityWindowInfo window:getUiAutomation().getWindows()){AccessibilityNodeInfo root=window.getRoot();if(root!=null&&getTargetContext().getPackageName().contentEquals(root.getPackageName())&&clickAccessible(root,label)){clicked=true;break;}}if(clicked){Thread.sleep(900);return;}Thread.sleep(200);}throw new Exception("Dialog choice missing: "+label);}
    private Document fixture(){
        Document d=new Document();d.title="独立挑刺测试：读到一半，还能不能接着读？";d.url=fixtureUrl;d.notice="合成测试数据，不是真实平台内容。覆盖短回答、长回答与后续回答。";
        for(int section=0;section<4;section++){
            Section s=new Section("synthetic-"+section,"测试作者 "+(section+1),true);int paragraphs=section==1?45:3;
            for(int p=0;p<paragraphs;p++)s.blocks.add(new Block("text","回答"+(section+1)+"·段落"+String.format(java.util.Locale.ROOT,"%02d",p+1)+"。这是明确标注的合成阅读测试内容。读者应当可以在任意位置展开、收起、调整字号与切换主题，而不需要重新寻找刚才读到的段落。"));
            d.sections.add(s);d.blocks.addAll(s.blocks);
        }return d;
    }
    private void freshReader()throws Exception{
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);set("font",19);activity.getPreferences(0).edit().putInt("font",19).commit();LoginActivity.pendingDocument=fixture();activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.HUPU.name()));});Thread.sleep(1000);
    }
    private boolean isExpanded(String id)throws Exception{boolean[] value={false};ui(()->value[0]=((Set<?>)field("expanded")).contains(id));return value[0];}
    private void expandLong()throws Exception{
        tap("#part-1 > a.action");check(isExpanded("synthetic-1"),"Long answer expands by actual link tap");
        JSONObject document=dom("({parts:document.querySelectorAll('section.part').length,paragraphs:document.querySelectorAll('#part-1 > p:not(.preview)').length,url:location.href})");
        check(document.getInt("parts")==4&&document.getInt("paragraphs")==45,"Expanded answer retains all four cards and 45 paragraphs, actual="+document);
        if(document.getInt("paragraphs")!=45)throw new Exception("Expanded content is missing; subsequent paragraph-position checks cannot run");
    }
    private String firstVisibleParagraph()throws Exception{return dom("(()=>{let p=[...document.querySelectorAll('#part-1 p')].find(e=>e.getBoundingClientRect().bottom>4);return {text:p?p.textContent.slice(0,15):'',y:p?p.getBoundingClientRect().top:0}})()").getString("text");}
    private void scrollToMiddle()throws Exception{JSONObject p=box("#part-1 > p:nth-of-type(20)");scrollCss(p.getDouble("documentY")-45);}
    private void selectedTab()throws Exception{
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);});Thread.sleep(300);
        ui(()->{View decor=activity.getWindow().getDecorView();int count=0;for(Source source:Source.values())if(source.visible()){TextView tab=textView(decor,source.label,false);Rect rect=new Rect();check(tab!=null&&tab.getText().length()==1&&tab.getGlobalVisibleRect(rect)&&rect.width()==tab.getWidth(),"Single-letter tab fully visible without scrolling: "+source.label);count++;}check(count==6&&control(decor,Source.HACKERNEWS.label,false)==null,"Exactly six domestic platforms, no foreign tab");});
        pressNative(Source.TIEBA.label,false);Thread.sleep(600);final boolean[] visible={false};final String[] detail={""};
        ui(()->{TextView selected=textView(activity.getWindow().getDecorView(),Source.TIEBA.label,false);Rect r=new Rect();visible[0]=selected!=null&&selected.isSelected()&&selected.getGlobalVisibleRect(r)&&r.width()>=selected.getWidth()*0.8;detail[0]=r.toShortString();});
        shot("01-selected-rightmost-tab");check(visible[0],"Selected rightmost platform remains visible after choosing it, bounds="+detail[0]);
    }
    private void loginReturn()throws Exception{
        String url="https://tieba.baidu.com/p/99999881726",cookie="qr_return_fixture=1; Path=/p/99999881726; Secure; SameSite=Lax";
        Item item=new Item(Source.TIEBA,"合成登录返回测试",url,"");
        Document before=SourceParser.article(Source.TIEBA,"<h1>合成测试</h1><div class='d_post_content'>合成旧主帖</div><div class='login-guard-mask'>登录后查看全部评论内容</div>",url);
        CountDownLatch cookieSaved=new CountDownLatch(1);
        try{
            ui(()->{
                set("current",item);set("reading",before);((Repository)field("repo")).cacheArticle(before);
                android.webkit.CookieManager.getInstance().setAcceptCookie(true);
                android.webkit.CookieManager.getInstance().setCookie(url,cookie,value->cookieSaved.countDown());
            });
            if(!cookieSaved.await(3,TimeUnit.SECONDS))throw new Exception("Synthetic session setup timed out");
            ui(()->{
                set("wasStopped",true);
                activity.onActivityResult(100,Activity.RESULT_CANCELED,null);
                check(field("reading")==null,"Back result discards the anonymous reading snapshot");
                check(((Repository)field("repo")).cachedArticle(item)==null,"Back result invalidates pre-login article cache");
                check(!(Boolean)field("wasStopped"),"Return owns refresh; resume cannot issue a duplicate");
                DynamicReader reader=(DynamicReader)field("dynamic");
                check(reader!=null,"Back result starts a fresh Tieba dynamic read without explicit import");
                if(reader==null)throw new Exception("No fresh source view");
                Field wf=DynamicReader.class.getDeclaredField("web");wf.setAccessible(true);WebView source=(WebView)wf.get(reader);
                android.webkit.WebViewClient original=source.getWebViewClient();
                String html="<!doctype html><meta charset='utf-8'><h1>合成会话验证</h1><div class='d_post_content'>合成主帖：这不是真实平台登录。</div><div class='login-guard-mask'>登录后查看全部评论内容</div><script>setTimeout(function(){if(document.cookie.indexOf('qr_return_fixture=1')>=0){document.querySelector('.login-guard-mask').remove();var reply=document.createElement('div');reply.className='d_post_content';reply.textContent='合成回复：新来源视图复用了现有会话';document.body.appendChild(reply);}},2600);</script>";
                source.setWebViewClient(new android.webkit.WebViewClient(){
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView w,android.webkit.WebResourceRequest r){return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream((r.isForMainFrame()?html:"").getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageStarted(WebView w,String u,Bitmap b){original.onPageStarted(w,u,b);}
                    @Override public void onPageCommitVisible(WebView w,String u){original.onPageCommitVisible(w,u);}
                    @Override public void onPageFinished(WebView w,String u){original.onPageFinished(w,u);}
                });
            });
            boolean[] ready={false};
            for(int i=0;i<30&&!ready[0];i++){Thread.sleep(250);ui(()->ready[0]=field("reading")!=null);}
            ui(()->{
                Document after=(Document)field("reading");
                check(after!=null&&after.blocks.stream().anyMatch(b->b.value.contains("新来源视图复用了现有会话")),"Fresh source actually sees the synthetic session and extracts its reply");
                check(after!=null&&!after.notice.contains("请登录"),"Old login warning is replaced by the new extraction");
            });
            shot("login-return-synthetic-reply");
            ui(()->{
                Document imported=new Document();imported.title="合成手动读取";imported.url=url;imported.blocks.add(new Block("text","手动读取仍保留已展开的内容。"));
                LoginActivity.pendingDocument=imported;
                activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.TIEBA.name()));
                check(field("reading")==imported&&field("dynamic")==null,"Explicit source import still displays imported content without redundant reload");
                check(LoginActivity.pendingDocument==null,"Imported result is consumed once");
            });
        }finally{
            CountDownLatch removed=new CountDownLatch(1);
            ui(()->android.webkit.CookieManager.getInstance().setCookie(url,"qr_return_fixture=; Max-Age=0; Path=/p/99999881726; Secure; SameSite=Lax",v->removed.countDown()));
            if(!removed.await(3,TimeUnit.SECONDS))throw new Exception("Synthetic cookie cleanup timed out");
        }
    }
    private void tiebaSessionAndBack()throws Exception{
        SharedPreferences prefs=getTargetContext().getSharedPreferences("source-session",0);Map<String,?> saved=new HashMap<>(prefs.getAll());
        LoginActivity sourcePage=null;
        try{
            ui(()->{
                Item item=new Item(Source.TIEBA,"合成模式测试","https://tieba.baidu.com/p/99999881726","");
                for(boolean desktop:new boolean[]{false,true}){
                    SourceSession.remember(activity,Source.TIEBA,desktop);
                    DynamicReader reader=new DynamicReader(activity,item,new Repository.Result<Document>(){public void success(Document d){}public void failure(String s){}});
                    Field f=DynamicReader.class.getDeclaredField("web");f.setAccessible(true);WebView view=(WebView)f.get(reader);
                    check(view.getSettings().getUserAgentString().equals(desktop?Repository.DESKTOP_UA:android.webkit.WebSettings.getDefaultUserAgent(activity)),"Tieba read matches remembered login display mode: desktop="+desktop);
                    reader.close(); // Cancel constructor's posted load; no fixture URL reaches network.
                }
                SourceSession.remember(activity,Source.TIEBA,false);
                Document gated=SourceParser.article(Source.TIEBA,"<div class='d_post_content'>合成主帖</div><div class='login-guard-mask'>登录后查看全部评论内容</div>",item.url);
                ((Repository)field("repo")).cacheArticle(gated);
                check(((Repository)field("repo")).cachedArticle(item)==null,"Gated result cannot enter 10-minute article cache");
                set("current",item);set("reading",gated);
            });
            ActivityMonitor monitor=addMonitor(LoginActivity.class.getName(),null,false);
            try{ui(()->invoke("login",new Class<?>[]{Item.class},(Item)field("current")));sourcePage=(LoginActivity)waitForMonitorWithTimeout(monitor,5000);}finally{removeMonitor(monitor);}
            if(sourcePage==null)throw new Exception("No explicit source page");
            Thread.sleep(500);waitForIdleSync(); // ActivityMonitor can observe creation before resume.
            LoginActivity page=sourcePage;CountDownLatch loaded=new CountDownLatch(1);WebView[] source={null};
            ui(()->{
                Field f=LoginActivity.class.getDeclaredField("web");f.setAccessible(true);WebView view=(WebView)f.get(page);source[0]=view;
                check(view.getSettings().getUserAgentString().equals(android.webkit.WebSettings.getDefaultUserAgent(page)),"Login page itself reuses saved mobile mode");
                String fixture="<!doctype html><meta charset='utf-8'><style>.resolved-mask{display:none}</style><h1>合成已解锁原帖</h1><div class='d_post_content'>合成主帖正文。</div><div class='d_post_content'>只存在于这个来源页面的合成回复。</div><div class='resolved-mask'><div class='login-guard-mask'>登录后查看全部评论内容</div></div>";
                view.stopLoading();view.setWebViewClient(new android.webkit.WebViewClient(){
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){return new android.webkit.WebResourceResponse("text/html","UTF-8",new java.io.ByteArrayInputStream((r.isForMainFrame()?fixture:"").getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageFinished(WebView v,String u){if(u.equals("https://tieba.baidu.com/p/99999881726"))loaded.countDown();}
                });
                view.loadUrl("https://tieba.baidu.com/p/99999881726");
            });
            if(!loaded.await(5,TimeUnit.SECONDS))throw new Exception("Synthetic source not ready");
            // Exercise real snapshot with a CSS-hidden ancestor, not just inline-style fixtures.
            JSONObject snapshot=null;
            for(int attempt=0;attempt<16;attempt++){
                CountDownLatch inspected=new CountDownLatch(1);String[] result={null};
                ui(()->source[0].evaluateJavascript(SourceSnapshot.script(Source.TIEBA),v->{result[0]=v;inspected.countDown();}));
                if(!inspected.await(3,TimeUnit.SECONDS))throw new Exception("Snapshot timeout");
                Object raw=result[0]==null?null:new JSONTokener(result[0]).nextValue();
                if(raw instanceof String){JSONObject candidate=new JSONObject((String)raw);if(candidate.optString("html").contains("只存在于这个来源页面")){snapshot=candidate;break;}}
                Thread.sleep(200);
            }
            if(snapshot==null)throw new Exception("Exact synthetic document did not become ready");
            check("https://tieba.baidu.com/p/99999881726".equals(snapshot.optString("url")),"Source snapshot has exact fixture post identity, not a data-document origin");
            ui(()->check("https://tieba.baidu.com/p/99999881726".equals(source[0].getUrl()),"Native source URL and rendered snapshot agree"));
            Document fixtureDocument=SourceParser.article(Source.TIEBA,snapshot.getString("html"),snapshot.getString("url"));
            check(fixtureDocument.hasContent()&&!fixtureDocument.loginRequired,"Rendered source snapshot is readable without a visible gate");
            ui(()->{Field f=LoginActivity.class.getDeclaredField("initialUrl");f.setAccessible(true);check(UrlPolicy.sameForumPost(Source.TIEBA,(String)f.get(page),source[0].getUrl()),"Auto-return target matches the original post");check(!page.isFinishing(),"Source activity still active before Back");});
            check(!snapshot.getString("html").contains("登录后查看全部评论内容"),"Computed-style hidden gate is excluded from snapshot");
            ui(()->{View back=control(page.getWindow().getDecorView(),"返回",false);check(back!=null&&back.performClick(),"Explicit source Back control activated");});
            boolean[] done={false};for(int n=0;n<24&&!done[0];n++){Thread.sleep(200);ui(()->{Document d=(Document)field("reading");done[0]=d!=null&&d.title.equals("合成已解锁原帖");});}
            ui(()->{Document doc=(Document)field("reading");check(done[0]&&doc.blocks.stream().anyMatch(b->b.value.contains("只存在于这个来源页面")),"Back imports the actual already-loaded reply instead of reconstructing the source page");check(done[0]&&!doc.loginRequired&&field("dynamic")==null,"No false login warning and no redundant source reload after import");});
            shot("tieba-back-preserved-source-reply");
        }finally{if(sourcePage!=null&&!sourcePage.isFinishing()){LoginActivity page=sourcePage;ui(page::finish);}restorePreferences(prefs,saved);}
    }
    private void platformBadges()throws Exception{
        for(boolean dark:new boolean[]{false,true}){
            ui(()->{getTargetContext().getSharedPreferences("appearance",0).edit().putInt("mode",dark?2:1).commit();activity.recreate();});Thread.sleep(750);
            ui(()->{set("selected",Source.ZHIHU);invoke("home",new Class<?>[]{boolean.class},false);
                List<Item> rows=new ArrayList<>();for(int i=1;i<=6;i++)rows.add(new Item(Source.ZHIHU,"合成预览 "+i+" · 热榜排版与平台配色",Source.ZHIHU.login,"预览"));
                invoke("showBoard",new Class<?>[]{List.class,String.class},rows,"外观预览 · 非实时热搜");
                check("News".equals(activity.getString(app.quietreader.R.string.app_name)),"App display name is News");
            });
            // Newly rebuilt native views have no measured bounds until the layout pass.
            Thread.sleep(350);
            ui(()->{
                for(Source source:Source.values())if(source.visible()){
                    TextView tab=textView(activity.getWindow().getDecorView(),source.label,false);Rect r=new Rect();
                    check(tab instanceof PlatformMarkView&&tab.getText().length()==1&&tab.getGlobalVisibleRect(r)&&r.width()==tab.getWidth()&&tab.getBackground()==null,"Rounded lettermark without a background tile fully visible: "+source.label+" dark="+dark);
                }
            });shot("platform-badges-"+(dark?"dark":"light"));
            for(Source source:Source.values())if(source.visible()){
                pressNative(source.label,false);ui(()->check(textView(activity.getWindow().getDecorView(),source.label,false).isSelected(),"Real platform tap updates selection: "+source.label+" dark="+dark));
            }
        }
    }
    private void loadingAndLinks()throws Exception{
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);invoke("status",new Class<?>[]{String.class,String.class},"正在整理内容…","合成加载状态，不访问网络");});Thread.sleep(200);
        ui(()->{View spinner=control(activity.getWindow().getDecorView(),"加载中",false);check(spinner instanceof android.widget.ProgressBar&&((android.widget.ProgressBar)spinner).isIndeterminate()&&spinner.isShown(),"Loading state shows native indeterminate spinner");});shot("00-loading-spinner");
        String shop="https://item.jd.com/100291533956.html",related="https://www.smzdm.com/p/99999882/";
        Document doc=new Document();doc.title="合成链接验证";doc.url=fixtureUrl;doc.blocks.add(new Block("text","商品 "+shop+"，相关 "+related+"。"));
        Document target=new Document();target.title="合成关联内容";target.url=related;target.blocks.add(new Block("text","已通过正文链接进入；返回应保留上一页。"));
        ui(()->{((Repository)field("repo")).cacheArticle(target);LoginActivity.pendingDocument=doc;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.HUPU.name()));});Thread.sleep(700);
        check(dom("({count:document.querySelectorAll('a.content-link').length})").getInt("count")==2,"Both bare URLs render as links");shot("00-reader-links");
        final Intent[] dispatched={null};ActivityMonitor monitor=new ActivityMonitor(){@Override public ActivityResult onStartActivity(Intent intent){if(Intent.ACTION_CHOOSER.equals(intent.getAction())){dispatched[0]=intent;return new ActivityResult(Activity.RESULT_CANCELED,null);}return null;}};
        addMonitor(monitor);try{tap("a.content-link");Thread.sleep(300);check(dispatched[0]!=null,"Real link tap dispatches system chooser, intercepted to avoid external app/network");if(dispatched[0]!=null){Intent link=dispatched[0].getParcelableExtra(Intent.EXTRA_INTENT);check(link!=null&&shop.equals(link.getDataString())&&Intent.ACTION_VIEW.equals(link.getAction()),"Chooser carries exact shopping URL");}}finally{removeMonitor(monitor);}
        tap("a.content-link:nth-of-type(2)");Thread.sleep(700);ui(()->check(((Item)field("current")).url.equals(related),"Supported-platform link opens in own reader"));shot("00-linked-article");
        ui(()->invoke("goBack",new Class<?>[]{}));Thread.sleep(600);ui(()->check(((Item)field("current")).url.equals(fixtureUrl),"Back from linked article returns to original reader"));
    }
    private void collapseBottom()throws Exception{
        freshReader();shot("02-all-collapsed");expandLong();tap("#part-1 > a.action:last-child");
        check(!isExpanded("synthetic-1"),"Bottom collapse control collapses intended answer");JSONObject header=box("#part-1 > a.action");shot("03-bottom-collapse-position");
        check(header.getDouble("y")>=-5&&header.getDouble("y")<header.getDouble("viewport")*0.6,"Collapsing at long-answer bottom returns to that answer header; y="+header.getDouble("y")+", viewport="+header.getDouble("viewport"));
    }
    private int boardCards(View view,boolean fullyVisible){int count=0;CharSequence description=view.getContentDescription();if(description!=null&&description.toString().endsWith("，进入阅读")){Rect r=new Rect();if(!fullyVisible||(view.getGlobalVisibleRect(r)&&r.height()>=view.getHeight()-2))count++;}if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)count+=boardCards(((ViewGroup)view).getChildAt(i),fullyVisible);return count;}
    private boolean imeVisible()throws Exception{boolean[] shown={false};ui(()->{View root=activity.getWindow().getDecorView();if(android.os.Build.VERSION.SDK_INT>=30){android.view.WindowInsets insets=root.getRootWindowInsets();shown[0]=insets!=null&&insets.isVisible(android.view.WindowInsets.Type.ime());}else{Rect visible=new Rect();root.getWindowVisibleDisplayFrame(visible);shown[0]=root.getHeight()-visible.bottom>root.getHeight()*.2;}});return shown[0];}
    private void searchKeyboardReading()throws Exception{
        Document article=fixture();String title="合成键盘旅程：搜索后直接阅读";article.title=title;Item item=new Item(Source.HUPU,title,fixtureUrl,"合成测试，不访问远程");
        ui(()->{set("selected",Source.HUPU);invoke("home",new Class<?>[]{boolean.class},false);((Repository)field("repo")).cacheArticle(article);invoke("showBoard",new Class<?>[]{List.class,String.class},Collections.singletonList(item),"合成键盘测试");});Thread.sleep(400);pressNative("搜索当前榜单",false);
        boolean shown=false;for(int i=0;i<15;i++){Thread.sleep(150);if(imeVisible()){shown=true;break;}}check(shown,"Real pointer focus opens the soft keyboard before filtering");shot("09d-search-keyboard");if(!shown)throw new Exception("Cannot test reader keyboard dismissal without visible IME");
        queryBoard("键盘旅程");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==1,"Typing with keyboard visible leaves the intended card"));pressNative(title+"，"+item.detail+"，进入阅读",false);
        boolean hidden=false;for(int i=0;i<15;i++){Thread.sleep(150);if(!imeVisible()){hidden=true;break;}}shot("09e-reader-after-search-keyboard");check(hidden,"Opening a filtered article automatically dismisses IME, preserving reader space");ui(()->check(field("reading")!=null&&((Document)field("reading")).title.equals(title),"Real card pointer opens its matching cached article"));if(!hidden)sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
    }
    private void queryBoard(String query)throws Exception{View[] input={null};ui(()->input[0]=control(activity.getWindow().getDecorView(),"搜索当前榜单",false));Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,query);ui(()->{if(input[0]==null||!input[0].performAccessibilityAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new Exception("Search text entry failed");});Thread.sleep(250);}
    private List<Item> returnBoardFixture()throws Exception{
        List<Item> items=new ArrayList<>();for(int i=1;i<=30;i++)items.add(new Item(Source.HUPU,"合成连续阅读第"+i+"项"+(i%10==4?" · 目标专题":""),"https://bbs.hupu.com/999990"+String.format(java.util.Locale.ROOT,"%02d",i)+".html","合成缓存，不请求远程"));
        ui(()->{Repository repo=(Repository)field("repo");repo.cache(Source.HUPU,items);for(Item item:items){Document doc=fixture();doc.title=item.title;doc.url=item.url;repo.cacheArticle(doc);}set("selected",Source.HUPU);invoke("home",new Class<?>[]{boolean.class},false);});Thread.sleep(500);return items;
    }
    private String cardDescription(Item item){return item.title+"，"+item.detail+"，进入阅读";}
    private android.widget.ScrollView boardScroll(View view){if(view instanceof android.widget.ScrollView)return (android.widget.ScrollView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){android.widget.ScrollView found=boardScroll(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void swipeBoardUp()throws Exception{
        Rect bounds=new Rect();ui(()->{android.widget.ScrollView scroll=boardScroll(activity.getWindow().getDecorView());if(scroll==null||!scroll.getGlobalVisibleRect(bounds))throw new Exception("Board scroll viewport missing");});
        long down=SystemClock.uptimeMillis();for(int step=0;step<=14;step++){float y=bounds.top+bounds.height()*(.8f-.55f*step/14);MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),step==0?MotionEvent.ACTION_DOWN:step==14?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,bounds.centerX(),y,0);try{sendPointerSync(event);}finally{event.recycle();}Thread.sleep(25);}Thread.sleep(500);
    }
    private void boardMiddleReturn()throws Exception{
        List<Item> items=returnBoardFixture();Item item=items.get(17);String description=cardDescription(item);Rect before=new Rect();boolean[] visible={false};
        for(int attempt=0;attempt<6;attempt++){ui(()->{View card=control(activity.getWindow().getDecorView(),description,false);visible[0]=card!=null&&card.getGlobalVisibleRect(before)&&before.height()>=card.getHeight()-2;});if(visible[0])break;swipeBoardUp();}
        check(visible[0],"Real vertical swipes reveal middle-ranked synthetic article 18");if(!visible[0])throw new Exception("Could not reach middle article for return test");ui(()->check(boardScroll(activity.getWindow().getDecorView()).getScrollY()>100,"Precondition: board really scrolled away from its top"));shot("60-board-middle-before-open");
        pressNative(description,false);Thread.sleep(500);ui(()->check(field("reading")!=null&&((Document)field("reading")).url.equals(item.url),"Actual middle-card pointer opens matching cached article"));shot("61-board-middle-reader");pressNative("返回",false);Thread.sleep(500);shot("62-board-middle-returned");
        Rect after=new Rect();boolean[] retained={false};ui(()->{View card=control(activity.getWindow().getDecorView(),description,false);retained[0]=card!=null&&card.getGlobalVisibleRect(after)&&after.height()>=card.getHeight()-2;});check(retained[0]&&Math.abs(before.top-after.top)<=24*activity.getResources().getDisplayMetrics().density,"Reader back retains middle article at same board offset; before="+before.toShortString()+", after="+after.toShortString()+", visible="+retained[0]);
    }
    private void boardSearchReturn()throws Exception{
        List<Item> items=returnBoardFixture();Item item=items.get(13);pressNative("搜索当前榜单",false);queryBoard("目标专题");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==3,"Synthetic keyword yields exactly three entries before opening"));shot("63-board-search-before-open");
        pressNative(cardDescription(item),false);Thread.sleep(500);ui(()->check(field("reading")!=null&&((Document)field("reading")).url.equals(item.url),"Actual filtered-card pointer opens matching cached article"));pressNative("返回",false);Thread.sleep(500);shot("64-board-search-returned");
        ui(()->{TextView input=textView(activity.getWindow().getDecorView(),"搜索当前榜单",false);check(input!=null&&input.getText().toString().equals("目标专题"),"Reader back preserves the user's search query");check(boardCards(activity.getWindow().getDecorView(),false)==3,"Reader back preserves three filtered results instead of restoring all thirty");});
    }
    private String boardQuery()throws Exception{String[] query={null};ui(()->{TextView input=textView(activity.getWindow().getDecorView(),"搜索当前榜单",false);query[0]=input==null?null:input.getText().toString();});return query[0];}
    private void selectPlatform(Source source)throws Exception{revealFooter(source.label);pressNative(source.label,false);Thread.sleep(350);}
    private void changeTheme(String label)throws Exception{ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);try{menu("外观");choose(label);Activity next=waitForMonitorWithTimeout(monitor,7000);if(!(next instanceof MainActivity))throw new Exception("Expected theme recreation");activity=(MainActivity)next;Thread.sleep(600);}finally{removeMonitor(monitor);}}
    private int actualBackground(View view){android.graphics.drawable.Drawable drawable=view.getBackground();return drawable instanceof android.graphics.drawable.ColorDrawable?((android.graphics.drawable.ColorDrawable)drawable).getColor():0;}
    private void checkWebAppearance(WebView view,boolean sourcePage,boolean dark)throws Exception{
        // WebView compositor background is not guaranteed to be a ColorDrawable.
        // Own HTML gets a computed-style assertion below; source pixels need image review.
        check(Theme.dark(view.getContext())==dark,"Constructed "+(sourcePage?"source":"own-reader")+" WebView context shares global appearance preference");
        if(android.os.Build.VERSION.SDK_INT>=33)check(view.getSettings().isAlgorithmicDarkeningAllowed()==(sourcePage&&dark),"Algorithmic darkening only applies to dark third-party source pages, never own HTML");
        else if(android.os.Build.VERSION.SDK_INT>=29)check(view.getSettings().getForceDark()==(sourcePage&&dark?android.webkit.WebSettings.FORCE_DARK_ON:android.webkit.WebSettings.FORCE_DARK_OFF),"Legacy force-dark only applies to dark third-party source pages");
        else report.append("LIMIT API <29 has no platform WebView darkening setting; native/background contract only.\n");
    }
    private String appearanceUrl(Source source){
        switch(source){case HUPU:return "https://bbs.hupu.com/99999881.html";case SMZDM:return "https://www.smzdm.com/p/99999882/";case ZHIHU:return "https://www.zhihu.com/question/99999883";case TIEBA:return "https://tieba.baidu.com/p/99999884";case WALLSTREET:return "https://wallstreetcn.com/articles/99999885";case HACKERNEWS:return "https://news.ycombinator.com/item?id=99999886";default:return "https://weibo.com/99999887/Abcdef";}
    }
    private void appearanceSource(boolean dark)throws Exception{
        selectPlatform(Source.SMZDM);
        ActivityMonitor monitor=addMonitor(LoginActivity.class.getName(),null,false);Activity sourceActivity=null;
        try{
            menu("来源 / 登录");sourceActivity=waitForMonitorWithTimeout(monitor,7000);
            if(!(sourceActivity instanceof LoginActivity))throw new AssertionError("Real source activity missing");
            final Activity page=sourceActivity;Field field=LoginActivity.class.getDeclaredField("web");field.setAccessible(true);WebView[] sourceWeb={null};
            CountDownLatch loaded=new CountDownLatch(1);String url="https://www.smzdm.com/p/99999882/?synthetic-appearance=1";
            ui(()->{
                WebView view=(WebView)field.get(page);sourceWeb[0]=view;view.stopLoading();
                ViewGroup host=page.findViewById(android.R.id.content);
                check(Theme.dark(page)==dark&&host.getChildCount()>0&&actualBackground(host.getChildAt(0))==(dark?0xff1f2025:0xfffafafa),"Constructed LoginActivity native background and theme match the global preference");
                checkWebAppearance(view,true,dark);
                // Explicit local white HTML tests the third-party rendering configuration;
                // never call it a downloaded SMZDM page or a successful account login.
                byte[] html=("<!doctype html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><style>body{background:#fff;color:#111;font:22px sans-serif;padding:24px}button{background:#eee;color:#111;padding:16px}</style></head><body><h1>合成白页外观测试</h1><p>这不是远程值得买网页，不验证登录。</p><button>合成按钮：检查可读对比度</button></body></html>").getBytes(StandardCharsets.UTF_8);
                view.setWebViewClient(new android.webkit.WebViewClient(){
                    @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView w,android.webkit.WebResourceRequest r){return new android.webkit.WebResourceResponse(r.isForMainFrame()&&r.getUrl().toString().equals(url)?"text/html":"text/plain","UTF-8",new java.io.ByteArrayInputStream(r.isForMainFrame()&&r.getUrl().toString().equals(url)?html:new byte[0]));}
                    @Override public void onPageFinished(WebView w,String u){if(url.equals(u))loaded.countDown();}
                });view.loadUrl(url);
            });
            check(loaded.await(7,TimeUnit.SECONDS),"Locally intercepted synthetic source page finishes within bound");
            CountDownLatch inspected=new CountDownLatch(1);boolean[] synthetic={false};ui(()->sourceWeb[0].evaluateJavascript("document.body.textContent.indexOf('合成白页外观测试')>=0",value->{synthetic[0]="true".equals(value);inspected.countDown();}));
            check(inspected.await(3,TimeUnit.SECONDS)&&synthetic[0],"Source screenshot is bound to the labelled local white-page fixture, not remote site content");
            shot("appearance-"+(dark?"dark":"light")+"-source-synthetic-white");
        }finally{removeMonitor(monitor);if(sourceActivity!=null){Activity closing=sourceActivity;ui(closing::finish);}Thread.sleep(350);}
        report.append("LIMIT LoginActivity initially constructs its normal source URL, then the test stops it and intercepts all fixture resources. No account input, cookies, storage or remote HTML exported. White-page screenshot is synthetic; API setting alone does not certify every site's night-mode quality.\n");
    }
    private void appearanceAcrossPlatforms()throws Exception{
        Source[] sources={Source.HUPU,Source.SMZDM,Source.ZHIHU,Source.WALLSTREET,Source.WEIBO,Source.TIEBA};
        Map<Source,Item> items=new java.util.EnumMap<>(Source.class);
        ui(()->{
            Repository repository=(Repository)field("repo");repository.cancelPending();
            for(Source source:sources){
                Item item=new Item(source,"合成外观 · "+source.label,appearanceUrl(source),"仅验证本地配色，不是真实热榜");items.put(source,item);repository.cache(source,Collections.singletonList(item));
                Document document=new Document();document.url=item.url;document.title=item.title;document.notice="合成测试：全平台统一外观。不是实际来源正文。";document.blocks.add(new Block("text","合成阅读正文：浅色应为浅底深字，深色应为炭灰底浅字。不得因平台切换改变外观。"));repository.cacheArticle(document);
            }
            set("selected",Source.HUPU);invoke("home",new Class<?>[]{boolean.class},false);
        });Thread.sleep(350);
        for(boolean dark:new boolean[]{true,false}){
            changeTheme(dark?"深色":"浅色");String tone=dark?"dark":"light";
            int background=dark?0xff1f2025:0xfffafafa,ink=dark?0xffcdcfd5:0xff30323a;
            for(Source source:sources){
                Item item=items.get(source);
                // Normal source-page return refreshes that board. Keep each isolated color
                // journey synthetic without suppressing the production return lifecycle.
                ui(()->{Repository repository=(Repository)field("repo");repository.cancelPending();Object dynamic=field("dynamicBoard");if(dynamic!=null)((DynamicBoard)dynamic).close();repository.cache(source,Collections.singletonList(item));});
                selectPlatform(source);queryBoard("");
                ui(()->{check(field("selected")==source&&field("current")==null,"Real platform pointer selects "+source.name()+" board in "+tone);check(actualBackground((View)field("root"))==background,"Native "+source.name()+" board background uses global "+tone);TextView title=textView(activity.getWindow().getDecorView(),item.title,false);check(title!=null&&title.getCurrentTextColor()==ink,"Native "+source.name()+" board title uses readable global foreground");});
                // Returning from the real LoginActivity deliberately invalidates article caches.
                // Reinstall only this labelled synthetic document before the next real card tap.
                ui(()->{Document document=new Document();document.url=item.url;document.title=item.title;document.notice="合成测试：全平台统一外观。不是实际来源正文。";document.blocks.add(new Block("text","合成阅读正文：浅色应为浅底深字，深色应为炭灰底浅字。不得因平台切换改变外观。"));((Repository)field("repo")).cacheArticle(document);});
                shot("appearance-"+tone+"-"+source.name()+"-board");pressNative(cardDescription(item),false);Thread.sleep(650);
                ui(()->{check(field("reading")!=null&&((Document)field("reading")).url.equals(item.url),"Actual article card opens the matching cached synthetic "+source.name()+" document");checkWebAppearance(web(),false,dark);});
                JSONObject style=dom("({background:getComputedStyle(document.body).backgroundColor,color:getComputedStyle(document.body).color,source:document.querySelector('meta[name=quiet-reader-source]').content,text:document.body.textContent})");
                check(style.getString("background").equals(dark?"rgb(31, 32, 37)":"rgb(250, 250, 250)")&&style.getString("color").equals(dark?"rgb(205, 207, 213)":"rgb(48, 50, 58)"),"Actual "+source.name()+" own HTML has correct "+tone+" background/foreground");
                check(style.getString("source").equals(item.url)&&style.getString("text").contains("合成阅读正文"),"Appearance DOM belongs to intended "+source.name()+" content, not stale previous platform");
                ui(()->check(!web().getSettings().getJavaScriptEnabled(),"Appearance observation leaves own-reader JS disabled"));
                shot("appearance-"+tone+"-"+source.name()+"-reader");pressNative("返回",false);Thread.sleep(200);
            }
            appearanceSource(dark);
        }
        report.append("LIMIT Appearance covers six synthetic cached boards/readers in both themes and labelled intercepted source HTML; not real SMZDM or other source-site CSS/login acceptance.\n");
    }
    private void acceptSyntheticSource(Source source,Document document)throws Exception{
        // Exercise the same result entry as LoginActivity, without claiming an account login.
        // Do not invoke render or replace any production parse/render callback.
        ui(()->{LoginActivity.pendingDocument=document;invoke("onActivityResult",new Class<?>[]{int.class,int.class,Intent.class},100,Activity.RESULT_OK,new Intent().putExtra("source",source.name()));});Thread.sleep(600);
    }
    private void filteredVideoReturn()throws Exception{
        Item video=new Item(Source.HUPU,"合成视频主帖","https://bbs.hupu.com/99999771.html","合成待分类条目"),text=new Item(Source.HUPU,"讨论视频技术的文字文章","https://bbs.hupu.com/99999772.html","合成文字条目，标题不是过滤依据");
        Document parsed=SourceParser.article(Source.HUPU,"<h1>合成视频主帖</h1><div class='post-content_main-post-info'><div class='bbs-thread-comp main-thread'><div class='thread-content-detail'><p></p></div><div><video src='https://bbs.hupu.com/synthetic.mp4'></video></div></div></div><div class='reply-list-wrapper'><div class='reply-list-item'><div class='thread-content-detail'>合成回复不得冒充正文</div></div></div>",video.url);
        check(parsed.filteredVideo&&!parsed.hasContent(),"Real SourceParser classifies synthetic video main post and excludes its reply body");
        ui(()->{Repository repository=(Repository)field("repo");repository.cancelPending();repository.cache(Source.HUPU,java.util.Arrays.asList(video,text));set("selected",Source.HUPU);invoke("home",new Class<?>[]{boolean.class},false);});queryBoard("");
        ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==2,"Before source classification both unknown-video and text-title candidates exist"));
        acceptSyntheticSource(Source.HUPU,parsed);
        ui(()->{check(field("current")==null&&field("reading")==null,"Classified video automatically returns to the board");check(control(activity.getWindow().getDecorView(),"已过滤视频主题",false)==null,"No video-filter interstitial forces an extra return tap");check(control(activity.getWindow().getDecorView(),"合成回复不得冒充正文",true)==null,"Filtered main-post replies are absent from rendered native UI");check(web()==null,"Filtered whole topic does not masquerade as a readable own WebView");});shot("video-filter-main-excluded");
        Thread.sleep(400);
        ui(()->check(control(activity.getWindow().getDecorView(),cardDescription(video),false)==null&&control(activity.getWindow().getDecorView(),cardDescription(text),false)!=null,"Actual return hides classified video URL while preserving title-containing-video text topic"));shot("video-filter-returned-board");
        Repository fresh=new Repository(getTargetContext());try{check(fresh.hiddenVideo(video)&&fresh.cached(Source.HUPU).size()==1&&fresh.cached(Source.HUPU).get(0).url.equals(text.url),"A newly constructed Repository reapplies persisted classification to existing cached board");}finally{fresh.close();}
        ui(()->{invoke("showBoard",new Class<?>[]{List.class,String.class},java.util.Arrays.asList(video,text),"合成刷新回调");check(control(activity.getWindow().getDecorView(),cardDescription(video),false)==null,"Synthetic refresh callback cannot resurrect a classified video URL");((Repository)field("repo")).cache(Source.HUPU,Collections.singletonList(video));invoke("home",new Class<?>[]{boolean.class},false);});Thread.sleep(250);
        ui(()->{check(boardCards(activity.getWindow().getDecorView(),false)==0&&control(activity.getWindow().getDecorView(),"暂时没有可显示的图文条目",true)!=null,"An all-filtered cached board has an explicit text/image empty state");View search=control(activity.getWindow().getDecorView(),"搜索当前榜单",false);check(search!=null&&!search.isEnabled(),"All-filtered empty board does not accept misleading search input");});shot("video-filter-empty-board");
    }
    private void weiboTopicCacheProtection()throws Exception{
        SharedPreferences prefs=getTargetContext().getSharedPreferences("video-filter",0);Map<String,?> before=new HashMap<>(prefs.getAll());Repository repository=new Repository(getTargetContext());
        Item mobile=new Item(Source.WEIBO,"合成移动话题","https://m.weibo.cn/search?containerid=synthetic-topic-cache","合成"),desktop=new Item(Source.WEIBO,"合成桌面话题","https://s.weibo.com/weibo?q=synthetic-topic-cache","合成"),post=new Item(Source.WEIBO,"合成独立视频帖","https://m.weibo.cn/status/99999775001","合成");
        try{
            prefs.edit().putLong(VideoPolicy.key(mobile),System.currentTimeMillis()).putLong(VideoPolicy.key(desktop),System.currentTimeMillis()).putLong(VideoPolicy.key(post),System.currentTimeMillis()).commit();
            check(!repository.hiddenVideo(mobile)&&!repository.hiddenVideo(desktop),"Old persisted video marks cannot hide either mobile or desktop mixed Weibo topic");
            check(repository.hiddenVideo(post),"Independent video post remains hidden despite topic exception");
            List<Item> visible=repository.visibleItems(java.util.Arrays.asList(mobile,desktop,post));check(visible.size()==2&&visible.contains(mobile)&&visible.contains(desktop),"Repository filtering preserves both topic entries and excludes only the independent video post");
            prefs.edit().remove(VideoPolicy.key(mobile)).remove(VideoPolicy.key(desktop)).commit();repository.rememberVideo(mobile);repository.rememberVideo(desktop);
            check(!prefs.contains(VideoPolicy.key(mobile))&&!prefs.contains(VideoPolicy.key(desktop)),"rememberVideo writes no new persisted video classification for topic URLs");
            Repository fresh=new Repository(getTargetContext());try{check(!fresh.hiddenVideo(mobile)&&!fresh.hiddenVideo(desktop)&&fresh.hiddenVideo(post),"A fresh Repository keeps topic exception and independent post classification");}finally{fresh.close();}
        }finally{repository.close();restorePreferences(prefs,before);check(prefs.getAll().equals(before),"Topic cache test restores the original video preferences exactly");}
        report.append("LIMIT Weibo topic cache case is a synthetic direct Repository test, not a real platform image or navigation result.\n");
    }
    private void filteredZhihuContinuation()throws Exception{
        String url="https://www.zhihu.com/question/99999773";
        String video="<div class='AnswerItem VideoAnswer' data-zop='{\"itemId\":\"synthetic-video-answer\"}'><div class='RichContent-inner'><div class='RichText'><p>合成独立视频答案配文不得显示</p><video></video></div></div></div>";
        Document only=SourceParser.article(Source.ZHIHU,"<h1>合成知乎过滤边界</h1>"+video,url);
        check(!only.filteredVideo&&only.filteredVideos==1&&!only.hasContent(),"All-video answers do not classify the whole Zhihu question as excluded");
        acceptSyntheticSource(Source.ZHIHU,only);ui(()->check(web()!=null,"Real source-result path retains own question reader when every initial answer was video"));
        JSONObject empty=dom("({source:document.querySelector('meta[name=quiet-reader-source]').content,sections:document.querySelectorAll('section').length,more:!!document.querySelector('a[href=\"https://quiet-reader.invalid/more\"]'),text:document.body.textContent})");
        check(empty.getString("source").equals(url)&&empty.getInt("sections")==0&&empty.getBoolean("more"),"Empty filtered question keeps correct provenance and actual continuation link without fake answer cards");
        ui(()->check(!web().getSettings().getJavaScriptEnabled(),"Filtered question observation leaves own-reader Javascript off"));shot("video-filter-zhihu-empty-answers");
        String prose="<div class='QuestionRichText'><video></video></div><div class='AnswerItem' data-zop='{\"itemId\":\"synthetic-text-answer\"}'><div class='RichContent-inner'><div class='RichText'><p>合成正常文字答案必须保留。</p></div></div></div>";
        Document mixed=SourceParser.article(Source.ZHIHU,"<h1>合成知乎过滤边界</h1>"+prose+video,url);
        check(!mixed.filteredVideo&&mixed.hasContent()&&mixed.filteredVideos==1,"Question video attachment plus a prose answer survives while dedicated video answer is filtered");
        acceptSyntheticSource(Source.ZHIHU,mixed);JSONObject body=dom("({text:document.body.textContent,more:!!document.querySelector('a[href=\"https://quiet-reader.invalid/more\"]')})");
        check(body.getString("text").contains("合成正常文字答案必须保留")&&!body.getString("text").contains("合成独立视频答案配文不得显示")&&body.getBoolean("more"),"Rendered question preserves actual prose, excludes video-answer copy, and retains more entry");
        ui(()->check(!web().getSettings().getJavaScriptEnabled(),"Mixed-answer observation leaves own-reader Javascript off"));shot("video-filter-zhihu-prose-preserved");
        report.append("LIMIT Video filter tests use real SourceParser and Main.onActivityResult with explicit synthetic pendingDocument; no simulated authentication, no private render invocation, and no remote continuation request. More-link presence is not a claim that later live answers were fetched.\n");
    }
    private void boardPlatformAndTheme()throws Exception{
        returnBoardFixture();queryBoard("目标专题");selectPlatform(Source.SMZDM);check("".equals(boardQuery()),"Another platform does not inherit Hupu search");queryBoard("无网络");selectPlatform(Source.HUPU);check("目标专题".equals(boardQuery()),"Switching back restores Hupu's own query");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==3,"Switching back restores Hupu's three filtered cards"));shot("65-platform-search-restored");
        selectPlatform(Source.SMZDM);check("无网络".equals(boardQuery()),"SMZDM retains its different platform-local query");selectPlatform(Source.HUPU);
        boolean wasDark=Theme.dark(activity);changeTheme(wasDark?"浅色":"深色");check("目标专题".equals(boardQuery()),"Appearance recreation preserves current platform query");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==3,"Appearance recreation preserves filtered results"));shot("66-theme-search-restored");changeTheme(wasDark?"深色":"浅色");
    }
    private void boardRefreshAnchor()throws Exception{
        List<Item> items=returnBoardFixture();swipeBoardUp();swipeBoardUp();Item[] anchor={null};Rect before=new Rect();ui(()->{for(Item item:items){View card=control(activity.getWindow().getDecorView(),cardDescription(item),false);Rect r=new Rect();if(card!=null&&card.getGlobalVisibleRect(r)&&r.height()>=card.getHeight()-2){anchor[0]=item;before.set(r);break;}}});check(anchor[0]!=null&&!anchor[0].url.equals(items.get(0).url),"Refresh scenario starts at an actual middle-board visible article");if(anchor[0]==null)throw new Exception("No visible refresh anchor");shot("67-board-before-synthetic-refresh");
        List<Item> refreshed=new ArrayList<>();refreshed.add(new Item(Source.HUPU,"合成刷新新第一名","https://bbs.hupu.com/99999101.html","受控刷新回调，不是实网"));refreshed.add(new Item(Source.HUPU,"合成刷新新第二名","https://bbs.hupu.com/99999102.html","受控刷新回调，不是实网"));refreshed.addAll(items);
        ui(()->invoke("showBoard",new Class<?>[]{List.class,String.class},refreshed,"合成刷新：前插2项"));Thread.sleep(600);shot("68-board-after-synthetic-refresh");Rect after=new Rect();boolean[] visible={false};ui(()->{View card=control(activity.getWindow().getDecorView(),cardDescription(anchor[0]),false);visible[0]=card!=null&&card.getGlobalVisibleRect(after)&&after.height()>=card.getHeight()-2;});check(visible[0]&&Math.abs(before.top-after.top)<=24*activity.getResources().getDisplayMetrics().density,"Refresh insertion retains same URL article and viewport offset, not old rank/pixels; before="+before.toShortString()+", after="+after.toShortString()+", url="+anchor[0].url);
        report.append("LIMIT Refresh uses explicit synthetic data delivered to real showBoard; it does not certify upstream freshness or ranking.\n");
    }
    private void compactBoard()throws Exception{
        String saved=activity.getPreferences(0).getString("saved","[]");List<Item> items=new ArrayList<>();String conditions="合成优惠：到手价 399 元 · 需领 50 元券 · 满 2 件可用 · 指定会员及尺码 · 商城包邮 · 仅限今晚 22 点前";
        items.add(new Item(Source.SMZDM,"合成测试：长商品名称和优惠限制必须自然换行，不能把领券门槛藏起来",Source.SMZDM.login,conditions));
        for(int i=1;i<12;i++)items.add(new Item(Source.SMZDM,"合成紧凑榜单条目 "+i,Source.SMZDM.login+"?qa="+i,i==3?"SPECIAL 条件搜索":"381万热度"));
        ui(()->{set("selected",Source.SMZDM);invoke("home",new Class<?>[]{boolean.class},false);invoke("showBoard",new Class<?>[]{List.class,String.class},items,"合成测试 · 非实网榜单");});Thread.sleep(400);
        ui(()->{View decor=activity.getWindow().getDecorView();check(boardCards(decor,false)==12,"Compact board retains all twelve synthetic entries");check(boardCards(decor,true)>=5,"Standard screen exposes at least five complete compact entries including a long deal");check(control(decor,"收藏",true)==null,"Home contains no favorites entry");TextView detail=textView(decor,conditions,false);boolean complete=detail!=null&&detail.getLayout()!=null&&detail.getLineCount()>=2;for(int n=0;complete&&n<detail.getLineCount();n++)complete=detail.getLayout().getEllipsisCount(n)==0;check(complete,"Long price/coupon/member/time conditions wrap without ellipsis");View bar=tabs(decor);Rect bounds=new Rect(),screen=new Rect();decor.getGlobalVisibleRect(screen);check(bar!=null&&bar.getGlobalVisibleRect(bounds)&&bounds.top>screen.centerY(),"Platform navigation is below the reading list, not above it");});shot("08-compact-long-deal");
        queryBoard("长商品名称");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==1,"Search filters title to one matching entry"));queryBoard("special");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==1&&textView(activity.getWindow().getDecorView(),"合成紧凑榜单条目 3",false)!=null,"Search matches detail case-insensitively"));
        queryBoard("肯定不存在的合成关键词");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==0&&textView(activity.getWindow().getDecorView(),"当前榜单没有匹配结果",false)!=null,"No-match search explains the empty result"));shot("09-search-empty");
        queryBoard("");ui(()->check(boardCards(activity.getWindow().getDecorView(),false)==12,"Clearing search restores all entries in the current board"));menuContents(false);check(saved.equals(activity.getPreferences(0).getString("saved","[]")),"Removing favorites entry does not erase legacy saved preferences");
        freshReader();menuContents(true);ui(()->check(control(activity.getWindow().getDecorView(),"收藏",true)==null&&tabs(activity.getWindow().getDecorView())==null,"Reader has neither favorites entry nor a bottom action toolbar"));
    }
    private void fontAnchor()throws Exception{
        freshReader();expandLong();scrollToMiddle();String before=firstVisibleParagraph();shot("04-middle-before-font");
        menu("阅读字号");choose("特大 · 25");Thread.sleep(500);String after=firstVisibleParagraph();shot("05-middle-after-font");
        check(before.equals(after),"Font change keeps the same reading paragraph, before="+before+", after="+after);
        check(isExpanded("synthetic-1"),"Font change preserves expanded answer");JSONObject width=dom("({width:innerWidth,scrollWidth:document.documentElement.scrollWidth})");check(width.getInt("width")==width.getInt("scrollWidth"),"Large font causes no horizontal overflow");
        // Measure actual rendered text rectangles, not CSS max-height or line-clamp declarations.
        // A clipped glyph line intersects the preview's lower edge; fully hidden later lines do not count.
        scrollCss(box("#part-0 .preview").getDouble("documentY")-100);Thread.sleep(200);
        JSONObject preview=dom("(function(){var p=document.querySelector('#part-0 .preview');if(!p)return {found:false,fullLines:0,partialLines:0};var b=p.getBoundingClientRect(),walker=document.createTreeWalker(p,NodeFilter.SHOW_TEXT,null,false),node,lines=[];while((node=walker.nextNode())){for(var i=0;i<node.length;i++){if(!node.data.charAt(i).trim())continue;var range=document.createRange();range.setStart(node,i);range.setEnd(node,i+1);var r=range.getBoundingClientRect();if(r.width<=0||r.height<=0)continue;var seen=false;for(var j=0;j<lines.length;j++)if(Math.abs(lines[j].top-r.top)<1){seen=true;break;}if(!seen)lines.push({top:r.top,bottom:r.bottom});}}var full=0,partial=0;for(var k=0;k<lines.length;k++){var line=lines[k];if(line.top>=b.bottom-1||line.bottom<=b.top+1)continue;if(line.top>=b.top-1&&line.bottom<=b.bottom+1)full++;else partial++;}return {found:true,fullLines:full,partialLines:partial,previewTop:b.top,previewBottom:b.bottom,textLines:lines};})()");
        shot("05b-large-font-collapsed-preview");
        check(preview.getBoolean("found")&&preview.getInt("fullLines")==3&&preview.getInt("partialLines")==0,"At font 25 collapsed preview has three complete visible glyph lines and no half-cut line: "+preview);
    }
    private void darkAndRestore()throws Exception{
        freshReader();expandLong();scrollToMiddle();String before=firstVisibleParagraph();
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);menu("外观");choose("深色");Activity next=waitForMonitorWithTimeout(monitor,7000);removeMonitor(monitor);if(!(next instanceof MainActivity))throw new Exception("Theme recreation missing");activity=(MainActivity)next;Thread.sleep(900);
        JSONObject style=dom("({background:getComputedStyle(document.body).backgroundColor,text:getComputedStyle(document.body).color})");shot("06-dark-middle");
        check("rgb(31, 32, 37)".equals(style.getString("background")),"Dark mode reader uses the charcoal palette (not inverted photographs)");
        check(isExpanded("synthetic-1"),"Theme recreation preserves expanded answer");String after=firstVisibleParagraph();check(before.equals(after),"Theme recreation preserves paragraph position, before="+before+", after="+after);
        ui(()->check(!web().getSettings().getJavaScriptEnabled(),"Own reader JavaScript remains disabled after inspection and navigation"));
    }
    private void cacheReturn()throws Exception{
        freshReader();expandLong();scrollToMiddle();String before=firstVisibleParagraph();Item item=new Item(Source.HUPU,fixture().title,fixtureUrl,"合成测试");
        ui(()->invoke("goBack",new Class<?>[]{}));long start=SystemClock.elapsedRealtime();ui(()->invoke("open",new Class<?>[]{Item.class,boolean.class},item,true));Thread.sleep(700);
        boolean[] content={false};ui(()->content[0]=field("reading")!=null);check(content[0],"Reopen same recently read article uses cached content in "+(SystemClock.elapsedRealtime()-start)+" ms");
        shot("07-reopened-cache");check(isExpanded("synthetic-1"),"Reopen recently read article retains expanded answer");check(before.equals(firstVisibleParagraph()),"Reopen recently read article resumes paragraph");
    }
    private void renderSafety()throws Exception{
        freshReader();JSONObject state=dom("({sections:document.querySelectorAll('section.part').length,expanded:document.querySelectorAll('section.part p:not(.preview)').length,preview:document.querySelectorAll('.preview').length})");
        check(state.getInt("sections")==4&&state.getInt("preview")==4&&state.getInt("expanded")==0,"All four independent answer cards start as previews, not one giant flattened article");
        ui(()->{check(!web().getSettings().getAllowFileAccess(),"Reader cannot access local files");check(!web().getSettings().getAllowContentAccess(),"Reader cannot access local content providers");});
    }
    private void appendGrowthAnchor()throws Exception{
        Document first=fixture();first.url="https://www.zhihu.com/question/19550225";first.title="合成续读：上一条补全时不要丢失当前段落";
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);set("font",19);activity.getPreferences(0).edit().putInt("font",19).commit();LoginActivity.pendingDocument=first;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.ZHIHU.name()));});Thread.sleep(900);
        tap("#part-0 > a.action");expandLong();scrollToMiddle();String before=firstVisibleParagraph();shot("40-stream-before-earlier-answer-growth");
        ui(()->{
            check(!((Repository)field("repo")).offline(),"Actual loadMore callback setup has an active network (no authentication used)");
            invoke("loadMore",new Class<?>[]{});AnswerStream stream=(AnswerStream)field("answers");if(stream==null)throw new Exception("Actual MainActivity.loadMore did not create AnswerStream");
            Field callbackField=AnswerStream.class.getDeclaredField("callback");callbackField.setAccessible(true);Repository.Result<Document> callback=(Repository.Result<Document>)callbackField.get(stream);stream.close();
            Document next=fixture();next.url=first.url;Section grown=new Section("synthetic-0","测试作者 1",true);grown.blocks.addAll(next.sections.get(0).blocks);for(int p=0;p<36;p++)grown.blocks.add(new Block("text","上一条回答补全部分"+(p+1)+"：合成内容，验证新增内容只能追加阅读价值，不能把正在看的后一条回答顶走。这里没有真实知乎账号或远程回答。"));next.sections.set(0,grown);
            Section added=new Section("synthetic-4","测试作者 5",true);added.blocks.add(new Block("text","合成新增第五条回答。"));next.sections.add(added);
            Document merged=AnswerStream.copy((Document)field("reading"));AnswerStream.merge(merged,next);AnswerStream.merge(merged,next);callback.success(merged);
        });Thread.sleep(1300);ui(()->check(!web().getSettings().getJavaScriptEnabled(),"Production append callback disables temporary reader scripting before test DOM inspection"));String after=firstVisibleParagraph();JSONObject paragraph=box("#part-1 > p:nth-of-type(20)");shot("41-stream-after-earlier-answer-growth");
        check(before.equals(after)&&paragraph.getDouble("y")>-200&&paragraph.getDouble("y")<paragraph.getDouble("viewport"),"Actual loadMore success keeps current paragraph after a preceding expanded answer grows; before="+before+", after="+after+", original paragraph y="+paragraph.getDouble("y"));
        check(isExpanded("synthetic-0")&&isExpanded("synthetic-1"),"Actual stream callback retains both expanded answers");
        JSONObject state=dom("({sections:document.querySelectorAll('section.part').length,firstParagraphs:document.querySelectorAll('#part-0 > p:not(.preview)').length})");check(state.getInt("sections")==5&&state.getInt("firstParagraphs")==39,"Actual stream callback applies longer old answer and deduplicates repeated merged answers: "+state);
    }
    private void imageHeavyFold()throws Exception{
        Document d=new Document();d.title="合成多图正文：短文字不代表短内容";d.url=fixtureUrl;d.notice="十二张本机生成测试图片，不是网络文章或下载资源。";Section part=new Section("synthetic-gallery","正文",false);part.blocks.add(new Block("text","合成图文说明，仅此一段短文字。"));
        Bitmap bitmap=Bitmap.createBitmap(200,260,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(0xff397d70);android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(0xffefbe62);canvas.drawRect(20,20,180,240,paint);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        for(int n=0;n<12;n++)part.blocks.add(new Block("image","https://images.example.invalid/synthetic-gallery-"+n+".png"));d.sections.add(part);d.blocks.addAll(part.blocks);
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);for(Block b:part.blocks)if(b.type.equals("image"))((android.util.LruCache<String,byte[]>)field("images")).put(b.value,bytes.toByteArray());LoginActivity.pendingDocument=d;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.HUPU.name()));});Thread.sleep(900);shot("42-image-heavy-single-section-initial");
        JSONObject state=dom("({actions:document.querySelectorAll('#part-0 > a.action').length,images:document.querySelectorAll('#part-0 img').length,height:document.documentElement.scrollHeight,viewport:innerHeight})");check(state.getInt("actions")>0&&state.getInt("images")==0,"Single image-heavy article starts as a collapsible preview instead of twelve full images; actual="+state);
        if(state.getInt("actions")>0){tap("#part-0 > a.action");check(dom("({images:document.querySelectorAll('#part-0 img').length})").getInt("images")==12,"Expanding gallery preserves all twelve images");tap("#part-0 > a.action:last-child");check(!isExpanded("synthetic-gallery"),"Gallery can be collapsed from the end");shot("43-image-gallery-collapsed");}
    }
    private void streamRevisionRace()throws Exception{
        Document d=fixture();d.url="https://www.zhihu.com/question/19550225";d.title="合成续读竞态：布局变化后仍可重试";
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);LoginActivity.pendingDocument=d;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.ZHIHU.name()));});Thread.sleep(700);
        ui(()->{
            invoke("loadMore",new Class<?>[]{});AnswerStream stream=(AnswerStream)field("answers");if(stream==null)throw new Exception("Actual loadMore did not create stream");Field f=AnswerStream.class.getDeclaredField("callback");f.setAccessible(true);Repository.Result<Document> callback=(Repository.Result<Document>)f.get(stream);stream.close();callback.success(AnswerStream.copy(d));
            // Force the same revision change as a fold action before evaluateJavascript returns.
            ((Set<String>)field("expanded")).add("synthetic-1");invoke("reloadReader",new Class<?>[]{Document.class},d);
        });Thread.sleep(1200);
        ui(()->{check(!web().getSettings().getJavaScriptEnabled(),"Layout-revision race also disables temporary scripting before test inspection");check(!((Boolean)field("loadingMore")),"Layout-revision race releases loadingMore instead of permanently blocking retry");check(textView(activity.getWindow().getDecorView(),"阅读布局已改变",true)!=null,"Layout-revision race provides explicit preserved-content and retry feedback");});
        check(isExpanded("synthetic-1")&&dom("({parts:document.querySelectorAll('section.part').length,paragraphs:document.querySelectorAll('#part-1 > p:not(.preview)').length})").getInt("paragraphs")==45,"Revision race preserves the user's newer expansion and existing article");shot("44-stream-revision-race-recoverable");
    }
    private String shell(String command)throws Exception{
        try(android.os.ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand(command);java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[2048];int read;while((read=input.read(buffer))!=-1)output.write(buffer,0,read);return output.toString("UTF-8").trim();
        }
    }
    private void emulatorOnly()throws Exception{if(!"1".equals(shell("getprop ro.kernel.qemu")))throw new Exception("Display/network stress tests are restricted to the project emulator");}
    private void configuration(Checked change)throws Exception{
        ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);
        try{change.run();Thread.sleep(1800);Activity next=monitor.getLastActivity();if(next instanceof MainActivity)activity=(MainActivity)next;waitForIdleSync();}finally{removeMonitor(monitor);}
    }
    private void visibleControl(Activity owner,String label)throws Exception{
        ui(()->{View control=control(owner.getWindow().getDecorView(),label,false);Rect visible=new Rect();boolean onScreen=control!=null&&control.getGlobalVisibleRect(visible);check(onScreen&&visible.width()>=control.getWidth()-2&&visible.height()>=control.getHeight()-2,"Control fully visible: "+label+", bounds="+visible.toShortString()+", actualSize="+(control==null?"missing":control.getWidth()+"x"+control.getHeight())+", parent="+(control==null?"missing":control.getParent().getClass().getSimpleName()));});
    }
    private void revealFooter(String label)throws Exception{
        for(int attempt=0;attempt<6;attempt++){
            Rect viewport=new Rect();boolean[] ready={false},left={true};
            ui(()->{TextView control=textView(activity.getWindow().getDecorView(),label,false);if(control==null)throw new Exception("Missing footer action "+label);Rect visible=new Rect();ready[0]=control.getGlobalVisibleRect(visible)&&visible.width()>=control.getWidth()-2;if(ready[0])return;android.view.ViewParent parent=control.getParent();while(parent!=null&&!(parent instanceof HorizontalScrollView))parent=parent.getParent();if(!(parent instanceof HorizontalScrollView))return;((View)parent).getGlobalVisibleRect(viewport);int[] position=new int[2];control.getLocationOnScreen(position);left[0]=position[0]+control.getWidth()>viewport.right;});
            if(ready[0]||viewport.isEmpty())break;
            float from=viewport.left+viewport.width()*(left[0]?0.85f:0.15f),to=viewport.left+viewport.width()*(left[0]?0.15f:0.85f),y=viewport.exactCenterY();long start=SystemClock.uptimeMillis();
            for(int step=0;step<=10;step++){int action=step==0?MotionEvent.ACTION_DOWN:step==10?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,from+(to-from)*step/10f,y,0);try{sendPointerSync(event);}finally{event.recycle();}Thread.sleep(20);}Thread.sleep(350);
        }
        visibleControl(activity,label);
    }
    private void smallLargeText()throws Exception{
        emulatorOnly();String oldFont=shell("settings get system font_scale"),oldSize=shell("wm size");
        String sizeRestore="wm size reset";for(String line:oldSize.split("\\r?\\n"))if(line.startsWith("Override size: ")){String value=line.substring(15).trim();if(value.matches("\\d+x\\d+"))sizeRestore="wm size "+value;}
        final String restore=sizeRestore;final String fontRestore=oldFont.matches("[0-9.]+")?oldFont:"1.0";
        try{
            ui(()->invoke("home",new Class<?>[]{boolean.class},false));configuration(()->{shell("wm size 840x1680");shell("settings put system font_scale 2.0");});
            shot("10-small-200-home");for(Source source:new Source[]{Source.ZHIHU,Source.WALLSTREET,Source.SMZDM,Source.WEIBO,Source.HUPU,Source.TIEBA})revealFooter(source.label);menuContents(false);shot("10b-small-200-bottom-platforms");
            ui(()->{TextView title=textView(activity.getWindow().getDecorView(),"合成测试榜单：无网络依赖",false);Rect visible=new Rect();boolean shown=title!=null&&title.getGlobalVisibleRect(visible);check(shown&&visible.height()>=title.getLineHeight(),"Large-system-font home must leave at least one readable line of the first article title, not only controls; title bounds="+visible.toShortString());});
            freshReader();shot("11-small-200-reader");for(String label:new String[]{"返回","更多选项"})visibleControl(activity,label);menuContents(true);
            JSONObject size=dom("({width:innerWidth,scrollWidth:document.documentElement.scrollWidth})");check(size.getInt("width")==size.getInt("scrollWidth"),"Small screen plus 200% system font has no horizontal reader overflow");
            LoginActivity login=(LoginActivity)startActivitySync(new Intent(getTargetContext(),LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("source",Source.WEIBO.name()).putExtra("url",Source.WEIBO.login));Thread.sleep(1000);
            shot("12-small-200-login-toolbar");for(String label:new String[]{"返回","读取到 News","桌面版"})visibleControl(login,label);
            String[] fullStatus={""};ui(()->{Field statusField=LoginActivity.class.getDeclaredField("status");statusField.setAccessible(true);TextView status=(TextView)statusField.get(login);fullStatus[0]=status.getText().toString();check(status.getMaxLines()==2&&status.getLineCount()<=2,"Small-screen source status occupies at most two lines at 200% system font");status.performClick();});Thread.sleep(350);
            android.view.accessibility.AccessibilityNodeInfo detailRoot=getUiAutomation().getRootInActiveWindow();boolean detailShown=false;if(detailRoot!=null){for(android.view.accessibility.AccessibilityNodeInfo node:detailRoot.findAccessibilityNodeInfosByText(fullStatus[0]))if(fullStatus[0].contentEquals(node.getText()==null?"":node.getText()))detailShown=true;check(!detailRoot.findAccessibilityNodeInfosByText("来源状态").isEmpty()&&detailShown,"Tapping truncated source status exposes the complete original text in a detail dialog");}else check(false,"Source status detail dialog is accessible");
            shot("12b-small-200-source-status-details");choose("关闭");ui(login::finish);
        }finally{configuration(()->{shell(restore);shell("settings put system font_scale "+fontRestore);});}
    }
    private int webViews(View view){int count=view instanceof WebView?1:0;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)count+=webViews(((ViewGroup)view).getChildAt(n));return count;}
    private void loadingAndSwitch()throws Exception{
        ui(()->{invoke("home",new Class<?>[]{boolean.class},false);invoke("open",new Class<?>[]{Item.class,boolean.class},new Item(Source.ZHIHU,"生命周期测试：公开问题加载中返回","https://www.zhihu.com/question/19550225",""),true);});
        ui(()->check(field("reading")==null&&field("dynamic")!=null,"Precondition: source is still loading when user decides to leave"));shot("20-loading-before-back");pressNative("返回",false);revealFooter(Source.WALLSTREET.label);pressNative(Source.WALLSTREET.label,false);Thread.sleep(300);
        ui(()->{check(field("dynamic")==null,"Source dynamic reader is disposed after returning");check(webViews(activity.getWindow().getDecorView())==0,"No hidden source WebView survives on the board");});
        // Keep observing longer than the source reader's deadline to expose stale callbacks.
        for(int second=0;second<24;second++){Thread.sleep(1000);boolean[] stable={false};ui(()->stable[0]=field("current")==null&&field("selected")==Source.WALLSTREET);if(!stable[0]){check(false,"Previous source callback replaced the chosen board after "+second+" seconds");break;}}
        shot("21-after-late-callback-window");ui(()->check(field("current")==null&&field("reading")==null&&field("selected")==Source.WALLSTREET,"Switching platform during load keeps the chosen board after late-callback window"));
    }
    private void forceDark()throws Exception{
        if(Theme.dark(activity))return;ActivityMonitor monitor=addMonitor(MainActivity.class.getName(),null,false);menu("外观");choose("深色");Activity next=waitForMonitorWithTimeout(monitor,7000);removeMonitor(monitor);if(!(next instanceof MainActivity))throw new Exception("Dark-theme recreation missing");activity=(MainActivity)next;Thread.sleep(500);
    }
    private void darkImage()throws Exception{
        freshReader();forceDark();Document d=fixture();String image="https://images.example.invalid/quiet-reader-synthetic.png";
        Bitmap bitmap=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888);android.graphics.Canvas canvas=new android.graphics.Canvas(bitmap);canvas.drawColor(0xff397d70);android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(0xffefbe62);canvas.drawRect(64,0,128,64,paint);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();d.sections.get(0).blocks.add(new Block("image",image));d.blocks.add(new Block("image",image));
        ui(()->{((android.util.LruCache<String,byte[]>)field("images")).put(image,bytes.toByteArray());LoginActivity.pendingDocument=d;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.HUPU.name()));});Thread.sleep(700);tap("#part-0 > a.action");tap("#part-0 img");
        shot("30-dark-image-dialog");choose("关闭");check(isExpanded("synthetic-0"),"Closing image zoom returns to the expanded reading answer");shot("31-dark-reader-after-image");
        report.append("REVIEW Screenshot 30-dark-image-dialog.png requires visual inspection for white WebView background; colored blocks are a synthetic image, not downloaded content.\n");
    }
    private Object previewField(ImagePreview preview,String name)throws Exception{Field f=ImagePreview.class.getDeclaredField(name);f.setAccessible(true);return f.get(preview);}
    private android.app.AlertDialog previewDialog(ImagePreview preview)throws Exception{return (android.app.AlertDialog)previewField(preview,"dialog");}
    private WebView previewWeb(ImagePreview preview)throws Exception{return (WebView)previewField(preview,"current");}
    private String previewStatus(ImagePreview preview)throws Exception{return ((TextView)previewField(preview,"status")).getText().toString();}
    private static final class PreviewTrace {
        final java.util.concurrent.atomic.AtomicInteger start=new java.util.concurrent.atomic.AtomicInteger(),commit=new java.util.concurrent.atomic.AtomicInteger(),finish=new java.util.concurrent.atomic.AtomicInteger(),intercept=new java.util.concurrent.atomic.AtomicInteger(),errors=new java.util.concurrent.atomic.AtomicInteger(),overrides=new java.util.concurrent.atomic.AtomicInteger();
        volatile String lastPage="";
        @Override public String toString(){return "start="+start+",commit="+commit+",finish="+finish+",intercept="+intercept+",errors="+errors+",overrides="+overrides+",lastPage="+lastPage;}
    }
    private final Map<ImagePreview,PreviewTrace> previewTraces=new java.util.IdentityHashMap<>();
    private void tracePreview(ImagePreview preview)throws Exception{
        WebView w=previewWeb(preview);android.webkit.WebViewClient original=w.getWebViewClient();PreviewTrace t=new PreviewTrace();previewTraces.put(preview,t);
        w.setWebViewClient(new android.webkit.WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,android.webkit.WebResourceRequest r){t.overrides.incrementAndGet();return original.shouldOverrideUrlLoading(v,r);}
            @Override public void onPageStarted(WebView v,String url,Bitmap icon){t.start.incrementAndGet();t.lastPage=url;original.onPageStarted(v,url,icon);}
            @Override public void onPageCommitVisible(WebView v,String url){t.commit.incrementAndGet();original.onPageCommitVisible(v,url);}
            @Override public void onPageFinished(WebView v,String url){t.finish.incrementAndGet();t.lastPage=url;original.onPageFinished(v,url);}
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){t.intercept.incrementAndGet();return original.shouldInterceptRequest(v,r);}
            @Override public void onReceivedError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){t.errors.incrementAndGet();original.onReceivedError(v,r,e);}
            @Override public void onReceivedHttpError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse response){original.onReceivedHttpError(v,r,response);}
        });
    }
    private void awaitPreview(ImagePreview preview,String contains)throws Exception{
        String[] text={""};for(int n=0;n<30;n++){ui(()->text[0]=previewStatus(preview));if(text[0].contains(contains)){check(true,"Native image status exposes "+contains);return;}Thread.sleep(200);}
        ui(()->{WebView w=previewWeb(preview);report.append("PREVIEW DIAGNOSTIC status=").append(previewStatus(preview)).append(" closed=").append(previewField(preview,"closed")).append(" showing=").append(previewDialog(preview).isShowing()).append(" progress=").append(((View)previewField(preview,"progress")).getVisibility()).append(" currentURL=").append(w==null?"<null>":w.getUrl()).append(" contentHeight=").append(w==null?-1:w.getContentHeight()).append(" loadProgress=").append(w==null?-1:w.getProgress()).append(" attached=").append(w!=null&&w.isAttachedToWindow()).append(" traces=").append(previewTraces.get(preview)).append('\n');});
        shot("image-preview-timeout-"+checks);check(false,"Native image status must expose "+contains+", actual="+text[0]);throw new Exception("Image preview status did not settle");
    }
    private byte[] imageFixtureBytes(){
        Bitmap b=Bitmap.createBitmap(128,64,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(b);c.drawColor(0xff397d70);android.graphics.Paint p=new android.graphics.Paint();p.setColor(0xffefbe62);c.drawRect(64,0,128,64,p);
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();
    }
    private JSONObject inspectPreview(WebView picture)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] raw={null};java.util.concurrent.atomic.AtomicBoolean own=new java.util.concurrent.atomic.AtomicBoolean();
        ui(()->{check(!picture.getSettings().getJavaScriptEnabled(),"Settled preview has scripting disabled before independent inspection");own.set(true);picture.getSettings().setJavaScriptEnabled(true);picture.evaluateJavascript("JSON.stringify((function(){var i=document.images[0];return {count:document.images.length,complete:!!i&&i.complete,width:i?i.naturalWidth:0,height:i?i.naturalHeight:0,background:getComputedStyle(document.body).backgroundColor,color:getComputedStyle(document.body).color,imageColor:i?getComputedStyle(i).color:'',filter:i?getComputedStyle(i).filter:'',url:location.href};})())",value->{if(own.compareAndSet(true,false))picture.getSettings().setJavaScriptEnabled(false);raw[0]=value;done.countDown();});});
        try{if(!done.await(5,TimeUnit.SECONDS))throw new Exception("Preview image inspection timeout");Object decoded=new JSONTokener(raw[0]).nextValue();if(!(decoded instanceof String))throw new Exception("Preview did not return a JSON string");return new JSONObject((String)decoded);}
        finally{ui(()->{if(own.compareAndSet(true,false))picture.getSettings().setJavaScriptEnabled(false);});}
    }
    private void touchDialogButton(ImagePreview preview,int which,boolean twice)throws Exception{
        Rect target=new Rect();ui(()->{android.widget.Button b=previewDialog(preview).getButton(which);Rect visible=new Rect();if(b==null||!b.isEnabled()||!b.hasWindowFocus()||!b.getGlobalVisibleRect(visible)||visible.width()==0||visible.height()==0)throw new Exception("Preview button is unavailable before real pointer tap");int[] screen=new int[2];b.getLocationOnScreen(screen);target.set(screen[0],screen[1],screen[0]+b.getWidth(),screen[1]+b.getHeight());report.append("Preview physical button=").append(b.getText()).append(" localVisible=").append(visible.toShortString()).append(" screenBounds=").append(target.toShortString()).append(" doubleTap=").append(twice).append('\n');});
        for(int n=0;n<(twice?2:1);n++){long now=SystemClock.uptimeMillis();MotionEvent d=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,target.centerX(),target.centerY(),0),u=MotionEvent.obtain(now,now+45,MotionEvent.ACTION_UP,target.centerX(),target.centerY(),0);try{sendPointerSync(d);sendPointerSync(u);}finally{d.recycle();u.recycle();}Thread.sleep(50);}
        waitForIdleSync();
    }
    private void imageCaptionIntegration()throws Exception{
        freshReader();forceDark();String url="https://images.example.invalid/feedback-"+runName+".png";byte[] png=imageFixtureBytes();Document d=fixture();Block photo=new Block("image",url);d.sections.get(0).blocks.add(photo);d.blocks.add(photo);
        ImagePreview[] preview={null};WebView[] reader={null};String[] readerUrl={null};int[] scroll={0};
        try{
            ui(()->{((android.util.LruCache<String,byte[]>)field("images")).put(url,"explicit synthetic invalid image bytes".getBytes(StandardCharsets.UTF_8));LoginActivity.pendingDocument=d;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.HUPU.name()));});Thread.sleep(650);tap("#part-0 > a.action");
            JSONObject caption=dom("(function(){var f=document.querySelector('#part-0 figure'),a=f?f.querySelector('figcaption a'):null,i=f?f.querySelector('img'):null;return {label:a?a.textContent:'',href:a?a.href:'',text:f?f.querySelector('figcaption').textContent:'',loaded:!!i&&i.complete&&i.naturalWidth>0};})()");
            check(caption.getString("label").equals("查看大图")&&caption.getString("href").equals(url)&&caption.getString("text").contains("重试"),"Caption is an actual exact-image link with a retry explanation even before image decode");
            check(!caption.getBoolean("loaded"),"Controlled invalid bytes do not count as a decoded inline image");shot("50-image-unavailable-clickable-caption");
            tap("#part-0 figure figcaption a");ui(()->{preview[0]=(ImagePreview)field("imagePreview");tracePreview(preview[0]);reader[0]=web();readerUrl[0]=web().getUrl();scroll[0]=web().getScrollY();});
            check(preview[0]!=null,"Real caption pointer tap opens production Main image preview");awaitPreview(preview[0],"失败");JSONObject failedStyle=inspectPreview(previewWeb(preview[0]));check(failedStyle.getString("background").equals("rgb(31, 32, 37)")&&failedStyle.getString("color").equals("rgb(205, 207, 213)")&&failedStyle.getString("imageColor").equals("rgb(205, 207, 213)"),"Actual dark failure document and image alt inherit readable light text, not black-on-dark");shot("51-image-explicit-failure");
            ui(()->check(previewDialog(preview[0]).getButton(android.app.AlertDialog.BUTTON_NEGATIVE).isEnabled()&&((View)previewField(preview[0],"progress")).getVisibility()==View.GONE,"Failure stops progress and leaves native retry available"));
            touchDialogButton(preview[0],android.app.AlertDialog.BUTTON_POSITIVE,false);ui(()->check((Boolean)previewField(preview[0],"closed")&&previewWeb(preview[0])==null,"Closing failed Main preview releases its owned WebView"));
            ui(()->((android.util.LruCache<String,byte[]>)field("images")).put(url,png));tap("#part-0 figure figcaption a");ImagePreview old=preview[0];ui(()->preview[0]=(ImagePreview)field("imagePreview"));
            check(preview[0]!=old,"Opening the same caption again uses a new preview owner");awaitPreview(preview[0],"已显示");WebView[] picture={null};ui(()->picture[0]=previewWeb(preview[0]));JSONObject decoded=inspectPreview(picture[0]);
            check(decoded.getInt("count")==1&&decoded.getBoolean("complete")&&decoded.getInt("width")==128&&decoded.getInt("height")==64,"Production Main preview actually decodes the in-memory image, not merely a success label");
            check(decoded.getString("background").equals("rgb(31, 32, 37)")&&decoded.getString("filter").equals("none"),"Dark preview retains dark background and uninverted image colors");shot("52-image-caption-reopened-success");
            touchDialogButton(preview[0],android.app.AlertDialog.BUTTON_POSITIVE,false);ui(()->check(web()==reader[0]&&readerUrl[0].equals(web().getUrl())&&Math.abs(scroll[0]-web().getScrollY())<5&&!web().getSettings().getJavaScriptEnabled(),"Opening and closing Main image preview does not reload, move or enable scripts in the underlying reader"));
            check(isExpanded("synthetic-0"),"Image failure and successful reopening preserve the expanded original answer");shot("53-image-returned-reader-position");
        }finally{ui(()->{if(preview[0]!=null)preview[0].dismiss();((android.util.LruCache<String,byte[]>)field("images")).remove(url);});}
    }
    private void imageRetryLifecycle()throws Exception{
        freshReader();forceDark();expandLong();scrollToMiddle();final byte[] png=imageFixtureBytes();final String url="https://images.example.invalid/retry-"+runName+".png";
        java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger(),invalidations=new java.util.concurrent.atomic.AtomicInteger();
        CountDownLatch recovering=new CountDownLatch(1),releaseRecovery=new CountDownLatch(1),lateStarted=new CountDownLatch(1),releaseLate=new CountDownLatch(1),lateFinished=new CountDownLatch(1);
        ImagePreview[] preview={null};WebView[] reader={null},failedWeb={null},recoveryWeb={null},lateWeb={null};String[] originalUrl={null};int[] originalY={0};
        try{
            ui(()->{reader[0]=web();originalUrl[0]=web().getUrl();originalY[0]=web().getScrollY();preview[0]=new ImagePreview(activity,url,true,()->{
                int n=loads.incrementAndGet();if(n==1)throw new java.io.IOException("synthetic first attempt failure");
                if(n==2){recovering.countDown();if(!releaseRecovery.await(10,TimeUnit.SECONDS))throw new java.io.IOException("synthetic recovery gate timeout");return new android.webkit.WebResourceResponse("image/png",null,new java.io.ByteArrayInputStream(png));}
                lateStarted.countDown();try{releaseLate.await(10,TimeUnit.SECONDS);throw new java.io.IOException("synthetic delayed error after close");}finally{lateFinished.countDown();}
            },()->invalidations.incrementAndGet());preview[0].show();failedWeb[0]=previewWeb(preview[0]);tracePreview(preview[0]);});
            awaitPreview(preview[0],"失败");shot("54-controlled-image-first-failure");
            ui(()->{String failureText=previewStatus(preview[0]);failedWeb[0].getWebViewClient().onPageFinished(failedWeb[0],"https://quiet-reader.invalid/image-preview");check(previewStatus(preview[0]).equals(failureText)&&!failedWeb[0].getSettings().getJavaScriptEnabled(),"Repeated page-finish cannot erase terminal image failure or reopen scripting");});
            touchDialogButton(preview[0],android.app.AlertDialog.BUTTON_NEGATIVE,true);check(recovering.await(4,TimeUnit.SECONDS),"Native retry starts a real controlled loader request");
            ui(()->{recoveryWeb[0]=previewWeb(preview[0]);check(recoveryWeb[0]!=failedWeb[0]&&failedWeb[0].getParent()==null,"Retry removes old picture and owns a new WebView");check(previewStatus(preview[0]).contains("正在加载")&&((View)previewField(preview[0],"progress")).getVisibility()==View.VISIBLE,"Pending retry exposes native loading text and progress");check(!previewDialog(preview[0]).getButton(android.app.AlertDialog.BUTTON_NEGATIVE).isEnabled(),"Pending retry disables duplicate retry activation");});
            check(loads.get()==2&&invalidations.get()==1,"Rapid second physical retry tap does not duplicate pending load or invalidate twice");shot("55-controlled-image-retrying");
            releaseRecovery.countDown();awaitPreview(preview[0],"已显示");JSONObject image=inspectPreview(recoveryWeb[0]);check(image.getBoolean("complete")&&image.getInt("width")==128&&image.getInt("height")==64,"Retry recovery is backed by decoded controlled image dimensions");
            ui(()->check(!recoveryWeb[0].getSettings().getJavaScriptEnabled()&&((View)previewField(preview[0],"progress")).getVisibility()==View.GONE,"Recovered image settles with scripts off and progress hidden"));shot("56-controlled-image-recovered");
            ui(()->{String readyText=previewStatus(preview[0]);recoveryWeb[0].getWebViewClient().onPageFinished(recoveryWeb[0],"https://quiet-reader.invalid/image-preview");check(previewWeb(preview[0])==recoveryWeb[0]&&previewStatus(preview[0]).equals(readyText)&&!recoveryWeb[0].getSettings().getJavaScriptEnabled(),"Repeated page-finish cannot replace current picture or reopen scripting after terminal image success");});
            touchDialogButton(preview[0],android.app.AlertDialog.BUTTON_NEGATIVE,false);check(lateStarted.await(4,TimeUnit.SECONDS),"Third controlled request is pending before dismissal");android.webkit.WebViewClient[] lateClient={null};String[] pendingStatus={null};
            ui(()->{lateWeb[0]=previewWeb(preview[0]);lateClient[0]=lateWeb[0].getWebViewClient();pendingStatus[0]=previewStatus(preview[0]);check(lateWeb[0]!=recoveryWeb[0]&&recoveryWeb[0].getParent()==null,"Further retry again replaces and detaches the previous picture");});
            touchDialogButton(preview[0],android.app.AlertDialog.BUTTON_POSITIVE,false);ui(()->check((Boolean)previewField(preview[0],"closed")&&previewWeb(preview[0])==null&&lateWeb[0].getParent()==null,"Closing a pending preview releases its current image WebView"));
            releaseLate.countDown();check(lateFinished.await(4,TimeUnit.SECONDS),"Controlled delayed loader really completes after close");Thread.sleep(350);
            // Supplement the real delayed loader failure with an explicitly synthetic stale finish callback.
            ui(()->{lateClient[0].onPageFinished(lateWeb[0],"https://quiet-reader.invalid/image-preview");check(previewStatus(preview[0]).equals(pendingStatus[0])&&!previewDialog(preview[0]).isShowing()&&previewWeb(preview[0])==null,"Late failure and stale page-finish cannot overwrite a closed preview or resurrect its window");check(web()==reader[0]&&originalUrl[0].equals(web().getUrl())&&Math.abs(originalY[0]-web().getScrollY())<5&&!web().getSettings().getJavaScriptEnabled(),"Retry and close callbacks leave underlying document instance, revision, position and script safety unchanged");});
            check(isExpanded("synthetic-1"),"Closing pending image retry retains the long answer expansion");shot("57-controlled-image-closed-late-callback");
            report.append("LIMIT Image retry Loader is controlled in-memory failure/PNG/delayed error. Production ImagePreview performs loading, decode inspection and callbacks; supplemental old page-finish is explicitly synthetic. Not real-platform image/network certification.\n");
        }finally{report.append("Controlled image loader calls=").append(loads.get()).append(" invalidations=").append(invalidations.get()).append('\n');releaseRecovery.countDown();releaseLate.countDown();ui(()->{if(preview[0]!=null)preview[0].dismiss();});}
    }
    private JSONObject emojiState()throws Exception{
        return dom("(function(){var e=document.querySelector('#part-0 p img.emoji'),p=e?e.parentElement:null,f=document.querySelector('#part-0 figure img'),r=e?e.getBoundingClientRect():null,a=f?f.closest('a'):null;return {emoji:document.querySelectorAll('#part-0 img.emoji').length,paragraph:p?p.textContent:'',alt:e?e.alt:'',linked:!!(e&&e.closest('a,figure')),display:e?getComputedStyle(e).display:'',width:r?r.width:0,height:r?r.height:0,font:p?parseFloat(getComputedStyle(p).fontSize):0,loaded:!!e&&e.complete&&e.naturalWidth>0,figures:document.querySelectorAll('#part-0 figure').length,captions:document.querySelectorAll('#part-0 figcaption').length,photoLoaded:!!f&&f.complete&&f.naturalWidth>0,photoLink:a?a.href:'',background:getComputedStyle(document.body).backgroundColor,overflow:document.documentElement.scrollWidth>innerWidth}})()");
    }
    private void inlineEmoji()throws Exception{
        freshReader();forceDark();
        String emoji="https://tb3.bdstatic.com/emoji/image_emoticon95@2x.png",photo="https://images.example.invalid/synthetic-normal-photo.png";
        Document d=SourceParser.article(Source.TIEBA,"<h1>合成贴吧表情与照片回归</h1><div class='d_post_content' id='synthetic-emoji'><p>合成表情前<img src='"+emoji+"' alt='笑'>表情后</p><img src='"+photo+"' alt='普通照片'></div><div class='d_post_content' id='synthetic-next'><p>合成第二楼层，必须保持折叠。</p></div>","https://tieba.baidu.com/p/999999997");
        d.notice="合成测试：两张图片均由本机生成，不访问外网，不是真实帖子。";
        check(d.sections.size()==2&&d.sections.get(0).blocks.size()==2&&d.sections.get(0).blocks.get(0).inlineImages.size()==1,"Parser keeps emoji in one text block while retaining a separate normal photo");
        for(int n=0;n<8;n++){Block tail=new Block("text","合成定位段落"+(n+1)+"。保留足够正文以检验字号调整后的阅读位置，避免短页面滚到底的自然约束掩盖定位行为。");d.sections.get(0).blocks.add(tail);d.blocks.add(tail);}
        Bitmap em=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);android.graphics.Canvas ec=new android.graphics.Canvas(em);ec.drawColor(0xffffbf40);java.io.ByteArrayOutputStream eb=new java.io.ByteArrayOutputStream();em.compress(Bitmap.CompressFormat.PNG,100,eb);em.recycle();
        // Deliberately small ordinary photo: dimensions alone must not classify it as an emoji.
        Bitmap im=Bitmap.createBitmap(48,24,Bitmap.Config.ARGB_8888);android.graphics.Canvas pc=new android.graphics.Canvas(im);pc.drawColor(0xff397d70);android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(0xffefbe62);pc.drawRect(24,0,48,24,paint);java.io.ByteArrayOutputStream pb=new java.io.ByteArrayOutputStream();im.compress(Bitmap.CompressFormat.PNG,100,pb);im.recycle();
        ui(()->{((android.util.LruCache<String,byte[]>)field("images")).put(emoji,eb.toByteArray());((android.util.LruCache<String,byte[]>)field("images")).put(photo,pb.toByteArray());LoginActivity.pendingDocument=d;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.TIEBA.name()));});Thread.sleep(700);
        JSONObject preview=dom("({text:document.querySelector('#part-0 .preview').textContent,images:document.querySelectorAll('#part-0 img').length})");check(preview.getString("text").contains("合成表情前[笑]表情后")&&preview.getInt("images")==0,"Collapsed preview retains accessible emoji placeholder and surrounding text without loading full images");
        tap("#part-0 > a.action");JSONObject small=emojiState();for(int n=0;n<10&&(!small.getBoolean("loaded")||!small.getBoolean("photoLoaded"));n++){Thread.sleep(200);small=emojiState();}
        check(small.getInt("emoji")==1&&small.getString("paragraph").equals("合成表情前表情后")&&small.getString("alt").equals("笑"),"Actual parsed emoji stays within its original paragraph and preserves both adjacent text runs: "+small);
        check(!small.getBoolean("linked")&&small.getString("display").equals("inline-block")&&small.getInt("figures")==1&&small.getInt("captions")==1,"Emoji has no photo-link/caption while normal photo retains its figure and enlargement caption");
        check(small.getBoolean("loaded")&&small.getBoolean("photoLoaded")&&small.getString("photoLink").equals(photo),"Both cached in-memory images render and normal photo retains its exact zoom target");
        check(Math.abs(small.getDouble("width")/small.getDouble("font")-1.35)<0.12&&Math.abs(small.getDouble("width")-small.getDouble("height"))<1,"Emoji is text-sized and square at normal font, not a separate oversized image");
        scrollCss(box("#part-0 > p").getDouble("documentY")-30);JSONObject beforeFont=box("#part-0 > p");shot("45-inline-emoji-dark-normal");
        menu("阅读字号");choose("特大 · 25");Thread.sleep(500);JSONObject large=emojiState();
        JSONObject afterFont=box("#part-0 > p");check(beforeFont.getString("text").equals(afterFont.getString("text"))&&Math.abs(beforeFont.getDouble("y")-afterFont.getDouble("y"))<50&&afterFont.getDouble("y")>-50&&afterFont.getDouble("y")<100,"Emoji font-change reload retains the same visible paragraph near its prior screen offset; before="+beforeFont.getDouble("y")+", after="+afterFont.getDouble("y"));
        check(large.getBoolean("loaded")&&large.getBoolean("photoLoaded")&&large.getDouble("width")>=small.getDouble("width")*1.2,"Increasing reading font also increases inline emoji without losing either image; normal="+small.getDouble("width")+", large="+large.getDouble("width"));
        check(Math.abs(large.getDouble("width")/large.getDouble("font")-1.35)<0.12&&!large.getBoolean("overflow")&&large.getString("background").equals("rgb(31, 32, 37)"),"Dark large-font emoji retains text-relative scale and has no horizontal overflow");
        scrollCss(box("#part-0 > p").getDouble("documentY")-30);shot("46-inline-emoji-dark-large");tap("#part-0 figure img");shot("47-inline-normal-photo-zoom");choose("关闭");
        check(isExpanded("synthetic-emoji")&&!isExpanded("synthetic-next")&&emojiState().getInt("emoji")==1,"Closing ordinary photo zoom preserves inline emoji, expanded current floor, and collapsed next floor");
        ui(()->check(!web().getSettings().getJavaScriptEnabled(),"Emoji reader returns to scripting-disabled state after image zoom"));
    }
    private void darkOfflineError()throws Exception{
        emulatorOnly();freshReader();forceDark();String wifi=shell("settings get global wifi_on"),mobile=shell("settings get global mobile_data");
        try{
            shell("svc wifi disable");shell("svc data disable");boolean offline=false;for(int n=0;n<12;n++){if(shell("dumpsys connectivity").contains("Active default network: none")){offline=true;break;}Thread.sleep(500);}check(offline,"Offline error scenario has no active default network");if(!offline)throw new Exception("Could not establish offline precondition");
            ui(()->invoke("home",new Class<?>[]{boolean.class},false));long start=SystemClock.elapsedRealtime();ui(()->{((Repository)field("repo")).invalidateArticles();invoke("open",new Class<?>[]{Item.class,boolean.class},new Item(Source.HUPU,"明确标注的离线错误测试","https://bbs.hupu.com/999999998.html",""),true);});
            boolean recovered=false;for(int n=0;n<42;n++){Thread.sleep(1000);boolean[] visible={false};ui(()->visible[0]=textView(activity.getWindow().getDecorView(),"重试",false)!=null||textView(activity.getWindow().getDecorView(),"登录 / 加载后读取",false)!=null);if(visible[0]){recovered=true;break;}}
            long elapsed=SystemClock.elapsedRealtime()-start;shot("32-dark-offline-error");check(recovered,"Offline failed article exposes actionable source/retry affordance within 42 seconds");check(recovered&&elapsed<=8000,"Known offline state should not spend a full dynamic-source timeout before error feedback; elapsed="+elapsed+" ms (8-second ceiling)");pressNative("返回",false);shot("33-dark-cached-board-offline");menuContents(false);
        }finally{if("1".equals(wifi)||"3".equals(wifi))shell("svc wifi enable");if("1".equals(mobile))shell("svc data enable");}
    }
    private void smzdmCacheMigration()throws Exception{
        SharedPreferences preferences=getTargetContext().getSharedPreferences("boards",0);
        String[] keys={"SMZDM","SMZDM_time","SMZDM_endpoint"};
        Map<String,?> before=new HashMap<>(preferences.getAll());Repository repository=new Repository(getTargetContext());
        final String endpoint="https://faxian.smzdm.com/h2s0t0f0c0p1/";
        final long fresh=System.currentTimeMillis();
        Item old=new Item(Source.SMZDM,"合成测试：旧公开集合必须拒绝","https://www.smzdm.com/p/999999991/","热搜榜商品公开集合 · 不等同 App 实时排序");
        Item current=new Item(Source.SMZDM,"合成测试：三小时缓存","https://www.smzdm.com/p/999999992/","9.9元 · 合成商城 · 07:00 · 12 评论");
        try{
            check(endpoint.equals(Source.SMZDM.endpoint),"Cache migration tests the official three-hour endpoint");
            check(preferences.edit().putString(keys[0],Models.toJson(Collections.singletonList(old)).toString()).putLong(keys[1],fresh).remove(keys[2]).commit(),"Synthetic fresh pre-migration cache committed without endpoint marker");
            check(repository.cached(Source.SMZDM).isEmpty(),"Production Repository rejects fresh legacy SMZDM entries without endpoint marker");
            check(repository.cachedAt(Source.SMZDM)==0,"Rejected unversioned cache cannot advertise a fresh timestamp");
            check(preferences.edit().putString(keys[2],"https://m.smzdm.com/tag/t5vepdx/").commit(),"Synthetic old-tag endpoint marker committed");
            check(repository.cached(Source.SMZDM).isEmpty()&&repository.cachedAt(Source.SMZDM)==0,"Production Repository rejects explicit old-tag cache and timestamp");
            check(preferences.edit().putString(keys[2],"https://faxian.smzdm.com/h3s0t0f0c0p1/").commit(),"Synthetic wrong-window endpoint marker committed");
            check(repository.cached(Source.SMZDM).isEmpty()&&repository.cachedAt(Source.SMZDM)==0,"Different time-window endpoint cannot masquerade as three-hour cache");
            repository.cache(Source.SMZDM,Collections.singletonList(current));List<Item> restored=repository.cached(Source.SMZDM);
            check(endpoint.equals(preferences.getString(keys[2],"")),"Production cache writer persists exact endpoint marker");
            check(restored.size()==1&&restored.get(0).url.equals(current.url)&&restored.get(0).detail.equals(current.detail),"Production Repository accepts new three-hour item and retains offer metadata");
            check(repository.cachedAt(Source.SMZDM)>=fresh,"Valid three-hour cache retains saved timestamp");
        }finally{
            repository.close();
            // Restore only owned keys, never unrelated boards, favorites or cookies.
            SharedPreferences.Editor editor=preferences.edit();
            for(String key:keys){Object value=before.get(key);if(!before.containsKey(key))editor.remove(key);else if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,new java.util.HashSet<>((Set<String>)value));else throw new IllegalStateException("Unexpected original cache key type: "+key);}
            check(editor.commit(),"Owned SMZDM cache keys restored after migration checks");Map<String,?> after=preferences.getAll();
            for(String key:keys)check(before.containsKey(key)==after.containsKey(key)&&java.util.Objects.equals(before.get(key),after.get(key)),"Original cache key restored exactly: "+key);
        }
        report.append("LIMIT Synthetic cache entries through real Repository methods; no network, cookie access, Main UI or App ranking certification.\n");
    }
    private void restorePreferences(SharedPreferences preferences,Map<String,?> snapshot){
        SharedPreferences.Editor editor=preferences.edit().clear();for(Map.Entry<String,?> entry:snapshot.entrySet()){Object value=entry.getValue();String key=entry.getKey();if(value instanceof String)editor.putString(key,(String)value);else if(value instanceof Long)editor.putLong(key,(Long)value);else if(value instanceof Integer)editor.putInt(key,(Integer)value);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else if(value instanceof Float)editor.putFloat(key,(Float)value);else if(value instanceof Set)editor.putStringSet(key,new java.util.HashSet<>((Set<String>)value));}editor.commit();
    }
    // Delayed bytes, not delayed DOM mutation: Chromium must discover intrinsic size itself.
    private static final class SlowImage extends java.io.InputStream {
        final CountDownLatch requested=new CountDownLatch(1),release=new CountDownLatch(1);
        final java.io.ByteArrayInputStream bytes; boolean ready;
        SlowImage(byte[] data){bytes=new java.io.ByteArrayInputStream(data);}
        private void awaitBytes()throws java.io.IOException{if(ready)return;requested.countDown();try{if(!release.await(90,TimeUnit.SECONDS))throw new java.io.IOException("Synthetic image gate timed out");ready=true;}catch(InterruptedException e){Thread.currentThread().interrupt();throw new java.io.IOException(e);}}
        @Override public int read()throws java.io.IOException{awaitBytes();return bytes.read();}
        @Override public int read(byte[] b,int off,int len)throws java.io.IOException{awaitBytes();return bytes.read(b,off,len);}
        @Override public void close(){release.countDown();}
    }
    private JSONObject layoutState(String anchor)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] raw={null};WebView[] owned={null};
        java.util.concurrent.atomic.AtomicBoolean active=new java.util.concurrent.atomic.AtomicBoolean(true),ownsJs=new java.util.concurrent.atomic.AtomicBoolean(false);
        String expression="(function(){var i=document.querySelector('figure img'),p="+(anchor==null?"null":"document.getElementById("+JSONObject.quote(anchor)+")")+";if(!p){var a=document.querySelectorAll('p[data-reading-anchor]');for(var n=0;n<a.length;n++){var r=a[n].getBoundingClientRect();if(r.top>=0&&r.top<innerHeight){p=a[n];break;}}}var b=p?p.getBoundingClientRect():null;return {ready:!!i&&!!p,id:p?p.id:'',top:b?b.top:null,scroll:scrollY,viewport:innerHeight,height:document.documentElement.scrollHeight,natural:i?i.naturalWidth:0,naturalHeight:i?i.naturalHeight:0,imageHeight:i?i.getBoundingClientRect().height:0,complete:!!i&&i.complete,belowTop:document.querySelectorAll('p[data-reading-anchor]')[1].getBoundingClientRect().top,url:location.href};})()";
        try{
            ui(()->{owned[0]=web();if(owned[0].getSettings().getJavaScriptEnabled())throw new Exception("Unexpected production JS window");owned[0].getSettings().setJavaScriptEnabled(true);ownsJs.set(true);owned[0].evaluateJavascript("JSON.stringify("+expression+")",value->{if(!active.get())return;raw[0]=value;if(ownsJs.compareAndSet(true,false))owned[0].getSettings().setJavaScriptEnabled(false);done.countDown();});});
            if(!done.await(5,TimeUnit.SECONDS))throw new Exception("Bounded image-layout read timed out");
            Object value=new JSONTokener(raw[0]).nextValue();if(!(value instanceof String))throw new Exception("Image-layout document not ready");JSONObject result=new JSONObject((String)value);
            ui(()->{result.put("nativeScroll",owned[0].getScrollY());result.put("nativeHeight",owned[0].getHeight());result.put("scale",owned[0].getScale());result.put("openPosition",field("openPosition"));result.put("pendingAnchor",field("pendingAnchor"));});return result;
        }finally{active.set(false);if(owned[0]!=null&&ownsJs.compareAndSet(true,false))ui(()->owned[0].getSettings().setJavaScriptEnabled(false));}
    }
    private void swipeReaderUp()throws Exception{
        Rect r=new Rect();ui(()->{if(!web().getGlobalVisibleRect(r))throw new Exception("Reader viewport missing");});long down=SystemClock.uptimeMillis();
        for(int n=0;n<=18;n++){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),n==0?MotionEvent.ACTION_DOWN:n==18?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,r.centerX(),r.top+r.height()*(.8f-.55f*n/18),0);try{sendPointerSync(e);}finally{e.recycle();}Thread.sleep(22);}Thread.sleep(450);
    }
    private void delayedImageLayout(String name,int swipes,boolean shortArticle)throws Exception{
        Bitmap bitmap=Bitmap.createBitmap(320,640,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(bitmap);c.drawColor(0xff397d70);android.graphics.Paint paint=new android.graphics.Paint();paint.setColor(0xffefbe62);paint.setTextSize(30);c.drawText("SYNTHETIC",55,100,paint);c.drawRect(30,160,290,590,paint);java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);bitmap.recycle();
        SlowImage slow=new SlowImage(bytes.toByteArray());String image="https://images.example.invalid/delayed-"+runName+"-"+name+".png";
        Document d=new Document();d.url=fixtureUrl;d.title="合成慢图 · "+name;d.notice="受控图片延迟；非真实平台内容。";Section part=new Section("delayed-"+name,"正文",true);part.blocks.add(new Block("text","图片上方的合成段落：先读文字，再观察图片出现是否改变阅读位置。"));part.blocks.add(new Block("image",image));
        for(int n=0;n<(shortArticle?3:38);n++)part.blocks.add(new Block("text","第 "+(n+1)+" 段合成阅读锚点。"+(shortArticle?"短文章保持原文顺序。":"这段文字只用于观察慢图加载之后的阅读定位，不代表任何真实来源。读到这里时保持手指静止，图片应当不会把当前段落移出屏幕。")));
        d.sections.add(part);d.blocks.addAll(part.blocks);
        try{
            ui(()->{invoke("home",new Class<?>[]{boolean.class},false);set("font",19);set("openPosition",-1);set("pendingAnchor",-1);LoginActivity.pendingDocument=d;activity.onActivityResult(100,Activity.RESULT_OK,new Intent().putExtra("source",Source.HUPU.name()));WebView w=web();android.webkit.WebViewClient original=w.getWebViewClient();w.setWebViewClient(new android.webkit.WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,android.webkit.WebResourceRequest r){return original.shouldOverrideUrlLoading(v,r);}
                @Override public boolean shouldOverrideUrlLoading(WebView v,String u){return original.shouldOverrideUrlLoading(v,u);}
                @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView v,android.webkit.WebResourceRequest r){if(r.getUrl().toString().equals(image))return new android.webkit.WebResourceResponse("image/png",null,slow);return original.shouldInterceptRequest(v,r);}
                @Override public void onPageStarted(WebView v,String u,Bitmap b){original.onPageStarted(v,u,b);}
                @Override public void onPageFinished(WebView v,String u){original.onPageFinished(v,u);}
                @Override public void onReceivedError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){original.onReceivedError(v,r,e);}
                @Override public void onReceivedHttpError(WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse e){original.onReceivedHttpError(v,r,e);}
            });});Thread.sleep(600);tap("#part-0 > a.action");
            check(slow.requested.await(5,TimeUnit.SECONDS),name+": actual image stream requested but held before first byte");
            for(int n=0;n<swipes;n++){int[] before={0},after={0};ui(()->before[0]=web().getScrollY());swipeReaderUp();ui(()->after[0]=web().getScrollY());if(after[0]==before[0])break;}
            Thread.sleep(1200);JSONObject before=layoutState(null);check(before.getBoolean("ready")&&before.getInt("natural")==0,name+": real paragraph exists while delayed image has no intrinsic size");
            check(before.getInt("openPosition")==-1&&before.getInt("pendingAnchor")==-1,name+": first reading has no pending cache/anchor restore");
            if(swipes>0)check(before.getInt("nativeScroll")>0,name+": pointer swipes genuinely left top");
            shot("80-"+name+"-before");Thread.sleep(1000);long released=SystemClock.elapsedRealtime();slow.release.countDown();JSONObject after=null;
            for(int n=0;n<30;n++){Thread.sleep(200);after=layoutState(before.getString("id"));if(after.getInt("natural")>0&&after.getBoolean("complete"))break;}
            Thread.sleep(700);after=layoutState(before.getString("id"));shot("81-"+name+"-after");
            report.append("LAYOUT ").append(name).append(" releaseToObservationMs=").append(SystemClock.elapsedRealtime()-released).append(" before=").append(before).append(" after=").append(after).append('\n');
            check(after.getInt("natural")==320&&after.getInt("naturalHeight")==640&&after.getDouble("imageHeight")>before.getDouble("imageHeight")+100,name+": actual delayed PNG decoded and intrinsically expanded");
            check(before.getString("url").equals(after.getString("url"))&&before.getString("id").equals(after.getString("id")),name+": same revision and fixed paragraph, no observer repositioning");
            if(swipes>0)check(Math.abs(before.getDouble("top")-after.getDouble("top"))<=3,name+": scrolled paragraph stays within 3 CSS px; delta="+(after.getDouble("top")-before.getDouble("top")));
            else report.append("OBSERVATION no manual scroll after expansion (fragment may already scroll; NOT a verified scroll-zero case): paragraph-below-image delta=").append(after.getDouble("belowTop")-before.getDouble("belowTop")).append(" CSS px; recorded separately from manual-scroll anchor assertions.\n");
            ui(()->check(!web().getSettings().getJavaScriptEnabled(),name+": temporary observation scripting is disabled"));
        }finally{slow.release.countDown();}
    }
    private void preloaderContract()throws Exception{
        class Fake implements ArticlePreloader.Loader {int calls,cancels;Repository.Result<Document> pending;public void load(Item i,Repository.Result<Document> cb){calls++;pending=cb;}public void cancel(){cancels++;}}
        Fake fake=new Fake();ArticlePreloader[] loader={null};int[] delivered={0},removed={0};
        String fixture="https://bbs.hupu.com/"+System.currentTimeMillis();
        Item a=new Item(Source.HUPU,"预读测试A",fixture+"01.html",""),b=new Item(Source.HUPU,"预读测试B",fixture+"02.html",""),c=new Item(Source.HUPU,"预读测试C",fixture+"03.html",""),d=new Item(Source.HUPU,"不应预读D",fixture+"04.html","");
        try{
            ui(()->{((ArticlePreloader)field("preloader")).pause();((Repository)field("repo")).invalidateArticles();loader[0]=new ArticlePreloader(activity,()->removed[0]++,fake);loader[0].offer(java.util.Arrays.asList(a,b,c,d));});Thread.sleep(450);
            check(fake.calls==1,"Predictive queue starts one request only");
            ui(()->check(loader[0].promote(a,new Repository.Result<Document>(){public void success(Document value){delivered[0]++;}public void failure(String why){}}),"Tap promotes the existing in-flight request"));
            ui(()->{Document doc=new Document();doc.url=a.url;doc.blocks.add(new Block("text","合成预读正文"));fake.pending.success(doc);});Thread.sleep(450);
            check(fake.calls==1&&delivered[0]==1,"Promotion does not restart request or run remaining speculation");
            ui(()->loader[0].offer(java.util.Arrays.asList(a,b,c,d)));Thread.sleep(450);check(fake.calls==2,"Fresh viewport skips cached item and starts the next prediction");
            Repository.Result<Document> stale=fake.pending;
            ui(()->{loader[0].pause();Document doc=new Document();doc.url=b.url;doc.blocks.add(new Block("text","过期回调"));stale.success(doc);check(((Repository)field("repo")).cachedArticle(b)==null,"Cancelled callbacks cannot populate the cache");});
            ui(()->loader[0].offer(java.util.Arrays.asList(b,c,d,a)));Thread.sleep(450);
            for(Item item:java.util.Arrays.asList(b,c,d)){ui(()->{Document doc=new Document();doc.url=item.url;if(item==b)doc.filteredVideo=true;else doc.blocks.add(new Block("text","合成预读正文"));fake.pending.success(doc);});Thread.sleep(100);}
            check(fake.calls==5,"A viewport runs at most three predictions serially");check(removed[0]==1,"Preloaded video classification notifies board removal before any tap");
            ui(()->{Repository r=(Repository)field("repo");check(r.hiddenVideo(b)&&r.cachedArticle(b)==null,"Video has a persistent exclusion, never a readable body cache");});
        }finally{ui(()->{if(loader[0]!=null)loader[0].close();});}
    }
    private void readingControls()throws Exception{
        freshReader();expandLong();tap("#part-1 nav.section-nav a[href$='index=2']");
        check(isExpanded("synthetic-2"),"Next-answer link expands its destination without a refetch");
        check(box("#part-2").getDouble("y")<80,"Next-answer navigation lands at the answer header");
        menu("回答 / 楼层目录");sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
        ActivityMonitor monitor=addMonitor(SettingsActivity.class.getName(),null,false);menu("阅读设置");Activity settings=waitForMonitorWithTimeout(monitor,3000);removeMonitor(monitor);
        check(settings instanceof SettingsActivity,"Settings is reachable from the reading overflow");
        if(settings!=null){
            Activity[] page={settings};ui(()->{View row=control(page[0].getWindow().getDecorView(),"阅读配色",true);Rect bounds=new Rect();check(row!=null&&row.getGlobalVisibleRect(bounds),"Palette setting and current value are visible");row.performClick();});
            ActivityMonitor changed=addMonitor(SettingsActivity.class.getName(),null,false);choose("暖灰 / 米白");Activity next=waitForMonitorWithTimeout(changed,3000);removeMonitor(changed);if(next!=null)page[0]=next;
            check(getTargetContext().getSharedPreferences("appearance",0).getInt("palette",0)==1,"Palette selection is saved by its real settings control");
            ui(()->control(page[0].getWindow().getDecorView(),"正文行距",true).performClick());choose("宽松");
            check(getTargetContext().getSharedPreferences("appearance",0).getInt("spacing",1)==2,"Line-spacing selection is saved by its real settings control");shot("predictive-settings");
            MainActivity before=activity;ui(()->page[0].finish());long until=SystemClock.elapsedRealtime()+3000;while((activity==before||activity.isDestroyed())&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);check(activity!=before&&!activity.isDestroyed(),"Returning from settings rebuilds the reader with saved preferences");Thread.sleep(400);
            JSONObject style=dom("({bg:getComputedStyle(document.body).backgroundColor,line:getComputedStyle(document.querySelector('p')).lineHeight,font:getComputedStyle(document.querySelector('p')).fontSize})");
            check(style.getString("bg").equals(Theme.dark(activity)?"rgb(37, 34, 31)":"rgb(247, 240, 227)"),"Selected palette reaches the actual reader, not just the settings preview");
            check(Double.parseDouble(style.getString("line").replace("px",""))/Double.parseDouble(style.getString("font").replace("px",""))>2,"Selected wider line spacing reaches the actual reader");
        }
    }
    private void livePrefetch()throws Exception{
        // Separate measured live journey; no synthetic content is used for these timings.
        if(activity==null||activity.isDestroyed())throw new Exception("No resumed MainActivity for live measurement");
        Repository[] repository={null};List<Item> items=new ArrayList<>();String[] error={""};CountDownLatch board=new CountDownLatch(1);
        ui(()->{repository[0]=new Repository(activity);repository[0].board(Source.HUPU,new Repository.Result<List<Item>>(){public void success(List<Item> value){items.addAll(value);board.countDown();}public void failure(String why){error[0]=why;board.countDown();}});});
        try{
            if(!board.await(35,TimeUnit.SECONDS)||items.isEmpty())throw new Exception("Live Hupu board unavailable: "+error[0]);
            ui(()->{set("selected",Source.HUPU);repository[0].invalidateArticles();invoke("home",new Class<?>[]{boolean.class},false);});
            long started=SystemClock.elapsedRealtime();Item[] ready={null};
            while(SystemClock.elapsedRealtime()-started<40000&&ready[0]==null){ui(()->{for(Item item:items)if(repository[0].cachedArticle(item)!=null){ready[0]=item;break;}});Thread.sleep(80);}
            check(ready[0]!=null,"Live viewport preloads a not-yet-opened article");if(ready[0]==null)return;
            report.append("MEASURE live prefetch ready after ").append(SystemClock.elapsedRealtime()-started).append(" ms; ").append(ready[0].url).append('\n');
            long tap=SystemClock.elapsedRealtime();ui(()->invoke("open",new Class<?>[]{Item.class,boolean.class},ready[0],true));
            boolean shown=false;while(SystemClock.elapsedRealtime()-tap<5000&&!shown){try{JSONObject state=dom("({text:document.querySelector('main')?.innerText||'',loaded:document.readyState})");shown=state.getString("text").length()>50;}catch(Exception ignored){}if(!shown)Thread.sleep(40);}
            long elapsed=SystemClock.elapsedRealtime()-tap;report.append("MEASURE live preloaded tap-to-body ").append(elapsed).append(" ms\n");check(shown&&elapsed<1000,"Preloaded live article text appears in less than one second (not just a loading shell)");shot("predictive-live-first-body");
            ui(()->{android.util.LruCache<?,?> cache=(android.util.LruCache<?,?>)field("images");check(cache.maxSize()==2*1024*1024,"Image byte cache is capped at 2 MiB");Field f=Repository.class.getDeclaredField("articles");f.setAccessible(true);android.util.LruCache<?,?> articles=(android.util.LruCache<?,?>)f.get(null);check(articles.maxSize()==2*1024*1024&&articles.size()<=articles.maxSize(),"Conservatively accounted text cache is capped at 2 MiB");});
        }finally{ui(()->repository[0].close());}
    }
    @Override public void onStart(){
        SharedPreferences boards=getTargetContext().getSharedPreferences("boards",0);Map<String,?> originalBoards=new HashMap<>(boards.getAll());
        boolean appearanceMode=mode.equals("appearance")||mode.equals("all");
        boolean videoMode=mode.equals("video-filter")||mode.equals("predictive")||mode.equals("all"),isolatedMode=!mode.equals("smzdm-cache");
        SharedPreferences appearance=getTargetContext().getSharedPreferences("appearance",0),mainPreferences=getTargetContext().getSharedPreferences(MainActivity.class.getSimpleName(),0);
        Map<String,?> originalAppearance=new HashMap<>(appearance.getAll()),originalMain=new HashMap<>(mainPreferences.getAll());
        SharedPreferences videoPreferences=getTargetContext().getSharedPreferences("video-filter",0);Map<String,?> originalVideos=new HashMap<>(videoPreferences.getAll());Document originalPending=LoginActivity.pendingDocument;
        try{
            android.accessibilityservice.AccessibilityServiceInfo accessibility=getUiAutomation().getServiceInfo();accessibility.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(accessibility);
            if(mode.equals("smzdm-cache")){journey("smzdm-cache-migration",this::smzdmCacheMigration);}else{
            appearance.edit().putInt("palette",0).putInt("accent",0).putInt("face",0).putInt("spacing",1).commit();
            Repository cache=new Repository(getTargetContext());for(Source source:Source.values())cache.cache(source,Collections.singletonList(new Item(source,"合成测试榜单：无网络依赖",source.login,"不是实网热搜")));cache.close();
            if(mode.equals("tieba-auth"))mainPreferences.edit().putString("selected",Source.WEIBO.name()).commit();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
            if(mode.equals("tieba-auth")){ui(()->check(field("selected")==Source.ZHIHU,"New launch defaults to first tab Zhihu despite legacy Weibo preference"));journey("login-back-refresh",this::loginReturn);journey("tieba-source-session-back",this::tiebaSessionAndBack);}
            if(mode.equals("login-return")){journey("login-back-refresh",this::loginReturn);journey("platform-badges",this::platformBadges);}
            if(mode.equals("home-cleanup")){journey("board-middle-return",this::boardMiddleReturn);journey("platform-badges",this::platformBadges);}
            if(mode.equals("predictive")){journey("bounded-preloader-promotion",this::preloaderContract);journey("reading-settings-jump",this::readingControls);journey("live-hupu-prefetch",this::livePrefetch);}
            if(mode.equals("image-layout")){journey("short-top",()->delayedImageLayout("short-top",0,true));journey("long-above",()->delayedImageLayout("long-above",0,false));journey("long-below",()->delayedImageLayout("long-below",1,false));journey("long-middle",()->delayedImageLayout("long-middle",6,false));journey("long-bottom",()->delayedImageLayout("long-bottom",40,false));}
            if(mode.equals("core")||mode.equals("all")){journey("platform-switch",this::selectedTab);journey("loading-and-links",this::loadingAndLinks);journey("compact-search-menus",this::compactBoard);journey("search-keyboard-reading",this::searchKeyboardReading);journey("collapse-bottom",this::collapseBottom);journey("font-reading-anchor",this::fontAnchor);journey("dark-reading-state",this::darkAndRestore);journey("cache-return",this::cacheReturn);journey("reader-safety",this::renderSafety);}
            if(mode.equals("board-return")||mode.equals("all")){journey("board-middle-return",this::boardMiddleReturn);journey("board-search-return",this::boardSearchReturn);journey("board-platform-theme",this::boardPlatformAndTheme);journey("board-refresh-anchor",this::boardRefreshAnchor);}
            if(mode.equals("stress")||mode.equals("all"))journey("small-system-200",this::smallLargeText);
            if(mode.equals("lifecycle")||mode.equals("all"))journey("return-during-load",this::loadingAndSwitch);
            if(mode.equals("dark")||mode.equals("all")){journey("dark-image",this::darkImage);journey("dark-offline-error",this::darkOfflineError);}
            if(mode.equals("images")||mode.equals("all")){journey("image-caption-integration",this::imageCaptionIntegration);journey("image-retry-lifecycle",this::imageRetryLifecycle);}
            if(mode.equals("audit")||mode.equals("all")){journey("stream-old-answer-growth",this::appendGrowthAnchor);journey("image-heavy-single-section",this::imageHeavyFold);journey("stream-revision-race",this::streamRevisionRace);journey("inline-emoji-photo",this::inlineEmoji);}
            if(appearanceMode)journey("seven-platform-appearance",this::appearanceAcrossPlatforms);
            if(videoMode){journey("video-filter-return-cache",this::filteredVideoReturn);journey("video-filter-question-answers",this::filteredZhihuContinuation);journey("weibo-topic-cache-protection",this::weiboTopicCacheProtection);}
            }
        }catch(Throwable error){failures++;report.append("SETUP ERROR ").append(error).append('\n');}finally{
            if(isolatedMode&&activity!=null)try{ui(()->{((Repository)field("repo")).cancelPending();Object dynamic=field("dynamicBoard");if(dynamic!=null)((DynamicBoard)dynamic).close();activity.finish();});}catch(Exception cleanup){failures++;report.append("ERROR isolated-mode cleanup ").append(cleanup).append('\n');}
            if(!mode.equals("smzdm-cache"))restorePreferences(boards,originalBoards);
            if(isolatedMode){restorePreferences(appearance,originalAppearance);restorePreferences(mainPreferences,originalMain);check(appearance.getAll().equals(originalAppearance),"Global appearance preferences restored exactly");check(mainPreferences.getAll().equals(originalMain),"Original main preferences including source/font/favorites restored exactly");check(boards.getAll().equals(originalBoards),"Original board cache restored exactly after isolated fixtures");}
            if(videoMode){restorePreferences(videoPreferences,originalVideos);LoginActivity.pendingDocument=originalPending;check(videoPreferences.getAll().equals(originalVideos)&&LoginActivity.pendingDocument==originalPending,"Original video classifications and pending source document restored exactly");}
        }
        report.append("\nSUMMARY ").append(checks).append(" checks; ").append(failures).append(" failures. Output: ").append(outputDirectory()).append(". Screenshots: ").append(screenshots).append(mode.equals("predictive")?"\nLimits: bounded-queue/controls/video tests are synthetic; only the explicitly labelled Hupu timing is live, on an emulator, not the user's phone. No authenticated login or other-platform speed claim.\n":"\nLimits: synthetic own-reader interaction journeys only; no real-platform extraction, network speed, authenticated login, or SMZDM App hot-search claim. Original board cache restored; real cookies and bookmarks not cleared.\n");
        try{File dir=outputDirectory();dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}
        Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
