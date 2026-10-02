package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Fixed-public-page structural observer. No cookie/storage access or source HTML dump. */
public final class TiebaDomProbeInstrumentation extends Instrumentation {
    private static final String URL="https://tieba.baidu.com/p/11061609054";
    private Activity activity;
    private File directory;
    private final StringBuilder report=new StringBuilder();
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){WebView found=web(g.getChildAt(i));if(found!=null)return found;}}return null;}
    private JSONObject sample()throws Exception {
        CountDownLatch done=new CountDownLatch(1);String[] result={null};Throwable[] failure={null};
        String js="(function(){var expected='"+URL+"';var actual=location.origin+location.pathname;if(actual!==expected)return JSON.stringify({exactUrl:false,loginPage:/login|passport/i.test(location.href)});"
            +"var matches=[],roots=[],walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,null,false),n;"
            +"function meta(e){return {tag:e.tagName,cls:String(e.className||'').slice(0,250)};}"
            +"while((n=walker.nextNode())&&matches.length<12){if(!/贴吧成长等级|本吧头衔/.test(n.nodeValue)||/^(SCRIPT|STYLE|NOSCRIPT)$/.test(n.parentElement.tagName))continue;var e=n.parentElement,chain=[];for(var p=e,j=0;p&&j<8;p=p.parentElement,j++)chain.push(meta(p));"
            +"matches.push({text:n.nodeValue.trim().slice(0,140),chain:chain});var root=e.closest('.comment-content,.pb-rich-text,.pb-content-wrap,.d_post_content');if(root&&roots.indexOf(root)<0)roots.push(root);}"
            +"function structure(root){var clone=root.cloneNode(true);Array.prototype.forEach.call(clone.querySelectorAll('script,style,noscript,form,input,textarea,select,iframe,video,audio'),function(e){e.remove();});"
            +"function fmt(e,depth){if(depth>9)return '[depth-limit]';if(e.nodeType===3){var t=e.nodeValue.trim();return /贴吧成长等级|本吧头衔/.test(t)?t.slice(0,140):(t?'[text '+t.length+' chars]':'');}if(e.nodeType!==1)return '';var c=String(e.className||'').slice(0,200);var s='<'+e.tagName+(c?' class='+JSON.stringify(c):'')+'>';for(var i=0;i<e.childNodes.length&&s.length<11000;i++)s+=fmt(e.childNodes[i],depth+1);return s+'</'+e.tagName+'>';};return fmt(clone,0).slice(0,12000);}"
            +"return JSON.stringify({exactUrl:true,ready:document.readyState,matches:matches,rootStructures:roots.slice(0,2).map(structure)});})()";
        runOnMainSync(()->{try{WebView w=web(activity.getWindow().getDecorView());if(w==null)throw new AssertionError("No source WebView");w.evaluateJavascript(js,value->{result[0]=value;done.countDown();});}catch(Throwable t){failure[0]=t;done.countDown();}});
        if(!done.await(5,TimeUnit.SECONDS))throw new AssertionError("DOM observer timed out");if(failure[0]!=null)throw new AssertionError(failure[0]);
        Object decoded=new JSONTokener(result[0]).nextValue();if(!(decoded instanceof String))throw new AssertionError("DOM result not string");return new JSONObject((String)decoded);
    }
    private void screenshot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(directory,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Override public void onStart(){int code=Activity.RESULT_CANCELED;try{
        directory=new File(getTargetContext().getExternalFilesDir(null),"tieba-dom-probe/run-"+System.currentTimeMillis());directory.mkdirs();
        report.append("Fixed public structural probe; no auth/cookies/storage/HTML export. URL=").append(URL).append('\n');
        activity=startActivitySync(new Intent(getTargetContext(),LoginActivity.class).putExtra("source","TIEBA").putExtra("url",URL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long start=SystemClock.elapsedRealtime();JSONObject state=null;boolean found=false;
        while(SystemClock.elapsedRealtime()-start<30000){state=sample();report.append("sample ms=").append(SystemClock.elapsedRealtime()-start).append(" exactUrl=").append(state.optBoolean("exactUrl")).append(" ready=").append(state.optString("ready")).append(" matches=").append(state.optJSONArray("matches")==null?0:state.getJSONArray("matches").length()).append('\n');if(state.optBoolean("loginPage"))break;if(state.optBoolean("exactUrl")&&state.getJSONArray("matches").length()>0){found=true;break;}SystemClock.sleep(750);}
        report.append("FINAL ").append(state==null?"null":state.toString(2)).append('\n');screenshot("01-public-source.png");
        report.append(found?"FOUND_PUBLIC_TARGET_STRUCTURE\n":"TARGET_NOT_FOUND_OR_SOURCE_RESTRICTED\n");code=Activity.RESULT_OK;
    }catch(Throwable t){report.append("ERROR ").append(t).append('\n');try{screenshot("error.png");}catch(Exception ignored){} }
    finally{try{if(directory!=null){try(FileOutputStream out=new FileOutputStream(new File(directory,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}}catch(Exception ignored){}if(activity!=null)runOnMainSync(()->activity.finish());Bundle b=new Bundle();b.putString("stream",report+"\nOutput: "+(directory==null?"none":directory.getAbsolutePath())+"\n");finish(code,b);}}
}
