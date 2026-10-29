package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Captured public BBC payload through native R8 parsing/reader. Controlled HTTPS, not live coverage. */
final class ReleaseBbcChartProbe {
    private final Instrumentation test;private Activity activity;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReleaseBbcChartProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)throw new IllegalStateException(label);}
    private String asset(String name)throws Exception{try(InputStream in=test.getContext().getAssets().open("bbc-flourish-20261028/"+name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private WebView find(View root){if(root instanceof WebView){String url=((WebView)root).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/"))return (WebView)root;}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){WebView found=find(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
    private WebView reader(){WebView[] result={null};ui(()->result[0]=find(activity.getWindow().getDecorView()));return result[0];}
    private boolean savedLink(JSONObject doc,String url)throws Exception{JSONArray parts=doc.getJSONArray("sections");for(int n=0;n<parts.length();n++){JSONArray blocks=parts.getJSONObject(n).getJSONArray("blocks");for(int b=0;b<blocks.length();b++){JSONArray links=blocks.getJSONObject(b).optJSONArray("links");if(links!=null)for(int l=0;l<links.length();l++)if(url.equals(links.getJSONObject(l).optString("url")))return true;}}return false;}
    private String js(WebView web,String source)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};ui(()->{boolean enabled=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(source,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});if(!done.await(5,TimeUnit.SECONDS))throw new IOException("DOM timeout");return result[0];}
    void run(){String target="";try{
        check(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator only");check((test.getTargetContext().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual non-debuggable R8 target");
        JSONObject expected=new JSONObject(asset("expected.json"));byte[] html=asset("article.html").getBytes(StandardCharsets.UTF_8);target=expected.getString("url")+"?qr-r8-chart="+System.currentTimeMillis();String requestUrl=target;AtomicInteger requests=new AtomicInteger(),chartRequests=new AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{if(url.getHost().endsWith("flourish.studio")||url.getHost().equals("flo.uri.sh"))chartRequests.incrementAndGet();if(!url.toString().equals(requestUrl))throw new IOException("Non-article transport deliberately unavailable");return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){requests.incrementAndGet();return 200;}public InputStream getInputStream(){return new ByteArrayInputStream(html);}};}});
        JSONObject item=new JSONObject().put("source","BBC").put("url",target).put("title",expected.getString("title")).put("detail","").put("video",false);
        activity=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long end=SystemClock.elapsedRealtime()+20000;boolean ready=false;while(SystemClock.elapsedRealtime()<end){WebView web=reader();if(web!=null&&"true".equals(js(web,"Array.from(document.querySelectorAll('a')).some(a=>a.href==="+JSONObject.quote(expected.getString("chart"))+")"))){ready=true;break;}Thread.sleep(100);}check(ready,"real minified native reader presents the captured chart entry");
        check(requests.get()==1,"captured article acquired once through the ordinary native transport");
        JSONArray paragraphs=expected.getJSONArray("paragraphs");check(paragraphs.length()>20&&"true".equals(js(reader(),paragraphs+".every(s=>document.body.innerText.includes(s))")),"every captured source prose paragraph remains visible around the chart");
        check("true".equals(js(reader(),"document.querySelectorAll('iframe').length===0&&!document.documentElement.outerHTML.includes('window.addEventListener')")),"reader imports no opaque chart iframe or resize script");check(chartRequests.get()==0,"reading article makes no proactive chart-provider request");
        ui(activity::finish);test.waitForIdleSync();activity=null;JSONObject saved=new JSONObject(test.getTargetContext().getSharedPreferences("reading-history",0).getString(target,"{}"));check(saved.has("document")&&savedLink(saved.getJSONObject("document"),expected.getString("chart")),"ordinary reader exit persists the explicit chart alongside its body");
    }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
    finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(!target.isEmpty())test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(target).commit();}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
