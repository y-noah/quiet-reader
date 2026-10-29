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
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Framework-only checks of real R8 native acquisition, asynchronous persistence and cache reopening. */
final class ReleaseQueuedArticleIdentityProbe {
    private final Instrumentation test;private Activity activity;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReleaseQueuedArticleIdentityProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)throw new IllegalStateException(label);}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private WebView find(View view){if(view instanceof WebView){String url=((WebView)view).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/"))return (WebView)view;}if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){WebView found=find(((ViewGroup)view).getChildAt(n));if(found!=null)return found;}return null;}
    private String js(String expression)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] answer={null};ui(()->{WebView web=find(activity.getWindow().getDecorView());if(web==null){done.countDown();return;}boolean old=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(expression,value->{answer[0]=value;web.getSettings().setJavaScriptEnabled(old);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IOException("DOM callback timeout");return answer[0];}
    private void awaitBody()throws Exception{long end=SystemClock.elapsedRealtime()+20000;do{if("true".equals(js("document.body&&document.body.innerText.includes('受控主帖正文')&&document.querySelectorAll('.nested-reply').length===2")))return;Thread.sleep(50);}while(SystemClock.elapsedRealtime()<end);throw new IOException("Unified reader fixture not drawn");}
    private void launch(String url)throws Exception{JSONObject item=new JSONObject().put("source","TIEBA").put("url",url).put("title","队列缓存身份验证").put("detail","").put("snippet","").put("video",false);activity=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));}
    private void close(){if(activity==null)return;Activity closing=activity;ui(closing::finish);long end=SystemClock.elapsedRealtime()+5000;boolean[] done={false};do{ui(()->done[0]=closing.isDestroyed());if(done[0])break;SystemClock.sleep(30);}while(SystemClock.elapsedRealtime()<end);if(!done[0])throw new IllegalStateException("Reader finish timeout");test.waitForIdleSync();activity=null;}
    private String hash(String value)throws Exception{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    private JSONObject identified(JSONObject document)throws Exception{JSONArray parts=document.getJSONArray("sections");for(int n=0;n<parts.length();n++){JSONObject part=parts.getJSONObject(n);if(part.optBoolean("nestedReply")&&part.optString("sourceChildId").equals("8822"))return part;}return null;}
    void run(){String url="https://tieba.baidu.com/p/9900771133?qr-queued="+System.currentTimeMillis();Context context=test.getTargetContext();SharedPreferences sessions=context.getSharedPreferences("source-session",0),history=context.getSharedPreferences("reading-history",0);boolean hadWeb=sessions.contains("TIEBA-web"),oldWeb=sessions.getBoolean("TIEBA-web",false);File bodyFile=null;
        try{
            check(context.getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated API36 emulator only");check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual R8 target is non-debuggable");
            sessions.edit().putBoolean("TIEBA-web",false).commit();
            byte[] html=("<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>受控主帖正文</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id='7711'><a class=user-name>楼层作者</a><div class=comment-content><div class=pb-rich-text>受控楼层正文</div></div><div class=pb-lzl-item data-id='8822'><a class=head-name>楼内作者</a><div class=comment-content><div class=pb-rich-text>明确身份的楼内回复</div></div></div><div class=pb-lzl-item><a class=head-name>另一位作者</a><div class=comment-content><div class=pb-rich-text>无来源编号的楼内回复</div></div></div></div></div></div></div>").replace("受控主帖正文","受控主帖正文".repeat(80)).getBytes(StandardCharsets.UTF_8);
            AtomicInteger requests=new AtomicInteger();URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL target)throws IOException{if(!target.toString().equals(url))throw new IOException("Non-fixture transport unavailable");return new HttpURLConnection(target){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){requests.incrementAndGet();return 200;}public InputStream getInputStream(){return new ByteArrayInputStream(html);}};}});
            launch(url);awaitBody();check(requests.get()==1,"real R8 native acquisition parses the fixture once and draws nested replies");close();
            JSONObject marker=new JSONObject(history.getString(url,"{}"));JSONObject original=identified(marker.getJSONObject("document"));check(original!=null&&!original.optString("parentSectionId").isEmpty(),"actual reader history retains the parser child ID and parent control");
            String namespace=marker.getString("namespace"),session=marker.getString("session");bodyFile=new File(context.getCacheDir(),"article-bodies-v1/"+hash(namespace+"\n"+session+"\n"+url)+".json");
            long end=SystemClock.elapsedRealtime()+8000;while(!bodyFile.isFile()&&SystemClock.elapsedRealtime()<end)Thread.sleep(30);check(bodyFile.isFile(),"actual acquisition produces its asynchronous body-cache file");
            byte[] initialBytes=Files.readAllBytes(bodyFile.toPath());JSONObject saved=new JSONObject(new String(initialBytes,StandardCharsets.UTF_8)),savedChild=identified(saved.getJSONObject("document"));
            check(savedChild!=null&&savedChild.optString("parentSectionId").equals(original.getString("parentSectionId")),"R8 asynchronous cache preserves exact child identity and DOM parent");
            check(saved.getJSONObject("document").toString().equals(marker.getJSONObject("document").toString()),"R8 queued cache matches all fields in the independently saved reader body");
            JSONArray parts=saved.getJSONObject("document").getJSONArray("sections");int unknown=0;for(int n=0;n<parts.length();n++)if(parts.getJSONObject(n).optBoolean("nestedReply")&&parts.getJSONObject(n).optString("sourceChildId").isEmpty())unknown++;check(unknown==1,"R8 cache does not invent an identity for the ID-less reply");
            long inode=android.system.Os.stat(bodyFile.getPath()).st_ino,modified=bodyFile.lastModified();launch(url);awaitBody();check(requests.get()==1,"reopening the prepared article does not reacquire the source");close();
            long afterInode=android.system.Os.stat(bodyFile.getPath()).st_ino;boolean sameBytes=java.util.Arrays.equals(initialBytes,Files.readAllBytes(bodyFile.toPath()));report.append("CACHE inodeBefore=").append(inode).append(" inodeAfter=").append(afterInode).append(" mtimeBefore=").append(modified).append(" mtimeAfter=").append(bodyFile.lastModified()).append(" sameBytes=").append(sameBytes).append('\n');
            check(afterInode==inode&&sameBytes,"cache reopening preserves the existing identical acquisition file; LRU touches are allowed");
            JSONObject after=new JSONObject(history.getString(url,"{}"));check(identified(after.getJSONObject("document"))!=null&&after.getLong("bodyAt")==marker.getLong("bodyAt"),"reopening retains reply identity without renewing body freshness");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{try{close();history.edit().remove(url).commit();SharedPreferences.Editor edit=sessions.edit();if(hadWeb)edit.putBoolean("TIEBA-web",oldWeb);else edit.remove("TIEBA-web");edit.commit();if(bodyFile!=null&&bodyFile.exists()&&!bodyFile.delete())throw new IOException("Body fixture cleanup failed");check(!history.contains(url)&&(bodyFile==null||!bodyFile.exists()),"synthetic history/body fixture removed and prior source mode restored");}catch(Exception error){failures++;report.append("CLEANUP ").append(error.getClass().getSimpleName()).append('\n');}}
        report.append("scope=controlled transport through actual minified parser, queue and reader; not live login/energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
