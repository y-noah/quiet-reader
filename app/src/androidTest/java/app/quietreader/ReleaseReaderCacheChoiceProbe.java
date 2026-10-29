package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Real minified acquisition and RAM, with deliberately altered old history. Framework APIs only. */
final class ReleaseReaderCacheChoiceProbe {
    private final Instrumentation test;private Activity activity;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReleaseReaderCacheChoiceProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)throw new IllegalStateException(label);}
    private String asset(String name)throws Exception{try(InputStream in=test.getContext().getAssets().open("bbc-flourish-20261028/"+name)){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}}
    private WebView find(View root){if(root instanceof WebView){String url=((WebView)root).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/"))return (WebView)root;}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){WebView found=find(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
    private String js(String source)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={null};ui(()->{WebView web=find(activity.getWindow().getDecorView());if(web==null){done.countDown();return;}boolean enabled=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(source,result->{value[0]=result;web.getSettings().setJavaScriptEnabled(enabled);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("DOM timeout");return value[0];}
    private void await(String expression)throws Exception{long end=SystemClock.elapsedRealtime()+20000;do{if("true".equals(js(expression)))return;Thread.sleep(60);}while(SystemClock.elapsedRealtime()<end);throw new IOException("Reader did not display expected content");}
    private void launch(JSONObject item)throws Exception{JSONObject candidate=new JSONObject(item.toString()).put("rank",1).put("observedAt",System.currentTimeMillis());activity=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).putExtra("discovery_candidate",candidate.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private void close(){Activity closing=activity;if(closing==null)return;ui(closing::finish);long end=SystemClock.elapsedRealtime()+5000;boolean[] destroyed={false};do{ui(()->destroyed[0]=closing.isDestroyed());if(destroyed[0])break;SystemClock.sleep(30);}while(SystemClock.elapsedRealtime()<end);if(!destroyed[0])throw new IllegalStateException("Reader finish lifecycle did not complete");test.waitForIdleSync();activity=null;}
    private void alterFirstText(JSONObject document,String marker)throws Exception{JSONArray sections=document.getJSONArray("sections");for(int n=0;n<sections.length();n++){JSONArray blocks=sections.getJSONObject(n).getJSONArray("blocks");for(int b=0;b<blocks.length();b++){JSONObject block=blocks.getJSONObject(b);if("text".equals(block.optString("type"))){block.put("value",block.getString("value")+marker);return;}}}throw new IOException("Captured body has no text block");}
    void run(){List<String> urls=new ArrayList<>();try{
        check(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator only");check((test.getTargetContext().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual non-debuggable R8 target");
        JSONObject expected=new JSONObject(asset("expected.json"));byte[] html=asset("article.html").getBytes(StandardCharsets.UTF_8);Set<String> allowed=ConcurrentHashMap.newKeySet();AtomicInteger requests=new AtomicInteger();
        URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{if(!allowed.contains(url.toString()))throw new IOException("Non-article transport deliberately unavailable");return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){requests.incrementAndGet();return 200;}public InputStream getInputStream(){return new ByteArrayInputStream(html);}};}});
        SharedPreferences histories=test.getTargetContext().getSharedPreferences("reading-history",0);
        for(int mode=0;mode<4;mode++){
            String url=expected.getString("url")+"?qr-r8-cache="+System.currentTimeMillis()+"-"+mode,marker="历史专用验证文字"+mode;urls.add(url);allowed.add(url);
            JSONObject item=new JSONObject().put("source","BBC").put("url",url).put("title",expected.getString("title")).put("snippet","").put("detail","").put("video",false);
            int before=requests.get();launch(item);await("document.body&&document.body.innerText.includes("+JSONObject.quote(expected.getJSONArray("paragraphs").getString(0))+")");close();
            JSONObject state=new JSONObject(histories.getString(url,"{}"));report.append("scenario=").append(mode).append(" articleRequests=").append(requests.get()-before).append(" persistedBody=").append(state.has("document")).append('\n');check(state.has("document")&&requests.get()==before+1,"scenario "+mode+" captures actual parsed body once and persists history");
            long freshAt=state.getLong("bodyAt");JSONArray expanded=new JSONArray(state.getJSONArray("expanded").toString());check(expanded.length()>0,"scenario "+mode+" actual initial reading has expanded primary content");alterFirstText(state.getJSONObject("document"),marker);
            if(mode<2)state.put("bodyAt",freshAt-1000);if(mode==1)state.getJSONObject("document").put("metadataHash","f".repeat(64));if(mode==3)state.put("bodyAt",freshAt+1);
            check(System.currentTimeMillis()>=state.getLong("bodyAt"),"scenario "+mode+" history acquisition is not in the future");histories.edit().putString(url,state.toString()).commit();
            launch(item);await("document.body&&document.body.innerText.includes("+JSONObject.quote(expected.getJSONArray("paragraphs").getString(0))+")");
            check((mode<2?"false":"true").equals(js("document.body.innerText.includes("+JSONObject.quote(marker)+")")),"scenario "+mode+" visible body chooses newer valid acquisition and history on equal time");
            check(requests.get()==before+1,"scenario "+mode+" reopening performs no second native article acquisition");close();
            JSONObject after=new JSONObject(histories.getString(url,"{}"));Set<String> retained=new HashSet<>();JSONArray afterExpanded=after.getJSONArray("expanded");for(int n=0;n<afterExpanded.length();n++)retained.add(afterExpanded.getString(n));boolean preserved=true;for(int n=0;n<expanded.length();n++)preserved&=retained.contains(expanded.getString(n));check(preserved,"scenario "+mode+" retains actual initial expanded reading state");
        }
    }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
    finally{close();SharedPreferences.Editor edit=test.getTargetContext().getSharedPreferences("reading-history",0).edit();for(String url:urls)edit.remove(url);edit.commit();}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
