package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import android.webkit.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Connected assistive-technology tree, with a loaded source behind the real unified reader. */
final class ReaderSourceAccessibilityProbe {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder();
    private UiAutomation automation;
    private MainActivity reader;
    private WebView source;
    private Repository repo;
    private int checks,failures;
    ReaderSourceAccessibilityProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);test.waitForIdleSync();}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private AccessibilityNodeInfo find(AccessibilityNodeInfo root,String label){
        if(root==null)return null;
        if(root.isVisibleToUser()&&(label.contentEquals(root.getText()==null?"":root.getText())||label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())))return root;
        for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo found=find(root.getChild(n),label);if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo node(String label){if(Build.VERSION.SDK_INT>=33)automation.clearCache();return find(automation.getRootInActiveWindow(),label);}
    private AccessibilityNodeInfo await(String label)throws Exception{
        long end=SystemClock.elapsedRealtime()+7000;AccessibilityNodeInfo found;
        do{found=node(label);if(found!=null)return found;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<end);
        return null;
    }
    void run(){
        int oldFlags=0;boolean restoreFlags=false;
        String title="正在阅读的统一排版标题",body="当前正文应当可以被辅助阅读正常访问。",hidden="后台来源采集内容不可进入阅读焦点";
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target only");
            automation=test.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
            android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();oldFlags=info.flags;restoreFlags=true;
            info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            info.flags&=~android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;automation.setServiceInfo(info);
            AccessibilityManager manager=(AccessibilityManager)test.getTargetContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            long until=SystemClock.elapsedRealtime()+5000;boolean bound=false;
            do{bound=false;for(android.accessibilityservice.AccessibilityServiceInfo service:manager.getEnabledAccessibilityServiceList(-1))if(service.getId().endsWith("ReaderProbeAccessibilityService"))bound=true;if(bound&&manager.isTouchExplorationEnabled())break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<until);
            check(bound&&manager.isTouchExplorationEnabled(),"actual test service and touch exploration active before reader creation");
            if(!bound||!manager.isTouchExplorationEnabled())throw new IllegalStateException("Bound reader service required");
            Item item=new Item(Source.V2EX,title,"https://www.v2ex.com/t/"+System.currentTimeMillis(),"");
            Document doc=new Document();doc.title=title;doc.url=item.url;doc.blocks.add(new Block("text",body));ArticleVersion.capture(item,doc);
            repo=new Repository(test.getTargetContext());repo.cacheArticle(doc);
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            check(await(title)!=null&&await(body)!=null,"real unified reader title and body appear in the connected accessibility tree");
            CountDownLatch loaded=new CountDownLatch(1);
            ui(()->{source=new WebView(reader);source.getSettings().setJavaScriptEnabled(true);source.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView view,String url){loaded.countDown();}});SourceSurface.attach(reader,source);source.loadDataWithBaseURL("https://reader-source-fixture.invalid/","<html lang='zh-CN'><meta name='viewport' content='width=device-width,initial-scale=1'><h1>"+hidden+"</h1><a href='https://reader-source-fixture.invalid/action'>后台专用链接</a></html>","text/html","UTF-8",null);});
            check(loaded.await(7,TimeUnit.SECONDS),"background source document finishes loading");
            CountDownLatch sampled=new CountDownLatch(1);String[] geometry={""};
            ui(()->source.evaluateJavascript("document.body.innerText.includes('"+hidden+"')&&innerWidth>0&&innerHeight>0",value->{geometry[0]=value;sampled.countDown();}));
            check(sampled.await(3,TimeUnit.SECONDS)&&"true".equals(geometry[0]),"hidden source has real viewport and loaded extraction content");
            check(await(body)!=null&&node(hidden)==null&&node("后台专用链接")==null,"source content and links stay out of the active reader tree");
            // Positive control: the same loaded source is discoverable when its exclusion is explicitly removed.
            ui(()->source.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO));
            check(await(hidden)!=null,"positive control exposes the same loaded source when exclusion is removed");
            ui(()->SourceSurface.attach(reader,source));
            check(await(body)!=null&&node(hidden)==null,"production reattachment restores exclusion while keeping the visible body accessible");
            AccessibilityNodeInfo text=node(body);check(text!=null&&text.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),"visible reader body accepts accessibility focus");
            AccessibilityNodeInfo more=await("更多选项");check(more!=null&&more.performAction(AccessibilityNodeInfo.ACTION_CLICK),"connected reader toolbar opens its real menu");
            check(await("来源 / 登录")!=null&&node(hidden)==null,"reader menu is reachable without exposing the background source");
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
            check(await(body)!=null,"dismissing menu returns to the accessible reader body");
            ui(()->{SourceSurface.release(source);source=null;});
            check(await(body)!=null&&node(hidden)==null,"source release preserves the foreground reading tree");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{
            ui(()->{if(source!=null){SourceSurface.release(source);source=null;}if(reader!=null)reader.finish();if(repo!=null)repo.close();});
            if(restoreFlags){android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags=oldFlags;automation.setServiceInfo(info);}
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
