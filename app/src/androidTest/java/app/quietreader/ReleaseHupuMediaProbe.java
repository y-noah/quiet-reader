package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;

/** Synthetic native HTTPS -> actual minified reader -> history, framework APIs only. */
final class ReleaseHupuMediaProbe {
    private final Instrumentation test;
    private Activity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("R8 HUPU MEDIA; controlled HTML, not real account/permission evidence\n");
    private volatile String target="",html="";
    private final AtomicInteger requests=new AtomicInteger();
    private byte[] png;
    ReleaseHupuMediaProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean value,String label){checks++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');if(!value)throw new IllegalStateException(label);}
    private WebView web(View node){if(node instanceof WebView){String url=((WebView)node).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/"))return (WebView)node;}if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){WebView found=web(((ViewGroup)node).getChildAt(n));if(found!=null)return found;}return null;}
    private WebView reader(){WebView[] found={null};ui(()->found[0]=web(activity.getWindow().getDecorView()));return found[0];}
    private String js(WebView page,String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};ui(()->{boolean prior=page.getSettings().getJavaScriptEnabled();page.getSettings().setJavaScriptEnabled(true);page.evaluateJavascript(script,value->{result[0]=value;page.getSettings().setJavaScriptEnabled(prior);done.countDown();});});if(!done.await(5,TimeUnit.SECONDS))throw new IOException("DOM callback timeout");return result[0];}
    private interface Ready{boolean get()throws Exception;}
    private void await(String label,Ready ready)throws Exception{long end=SystemClock.elapsedRealtime()+15000;while(!ready.get()){if(SystemClock.elapsedRealtime()>end)throw new IOException("Timeout: "+label);Thread.sleep(80);}}
    private String image(String name){return "https://i.hupucdn.com/qr-r8-"+name+".png";}
    private String img(String name){return "<img src='"+image(name)+"'>";}
    private JSONObject saved(Context context){for(Object value:context.getSharedPreferences("reading-history",0).getAll().values())try{JSONObject state=new JSONObject(String.valueOf(value));if(target.equals(state.optString("bodySource")))return state;}catch(JSONException ignored){}return null;}
    private JSONObject reply(int id,String content)throws Exception{return new JSONObject().put("pid",String.valueOf(id)).put("content",content).put("author",new JSONObject().put("puname","受控作者"+id));}
    private String fixture(String id,boolean login)throws Exception{
        String hidden="<span hidden>隐藏辅助内容"+img("hidden-helper")+"</span>";
        String main=img("main")+hidden,visible=img("reply")+hidden,extra="<p>同文附图</p>"+img("secret");
        JSONArray rows=new JSONArray().put(reply(10,visible)).put(reply(11,extra)).put(reply(12,extra)).put(reply(13,extra));
        JSONObject thread=new JSONObject().put("tid",id).put("url","/"+id+".html").put("title","受控图片主帖").put("content",main).put("status",3).put("visibleRange",login?"LOGIN_SEE":"ALL_SEE").put("hasVideo",false);
        JSONArray staleHighlights=new JSONArray().put(reply(10,"<p>未渲染的旧热门回复</p>")).put(reply(10,"<p> </p>"));
        JSONObject detail=new JSONObject().put("thread",thread).put("visibleRange",login?"LOGIN_SEE":"ALL_SEE").put("lights",staleHighlights).put("replies",new JSONObject().put("current",1).put("total",1).put("list",rows));
        if(login)detail.put("user",new JSONObject().put("puid","789"));
        JSONObject state=new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("detail",detail).put("detailErrorInfo",new JSONObject().put("code",200))));
        return "<html><meta charset=utf-8><h1>受控图片主帖</h1><script id='__NEXT_DATA__' type='application/json'>"+state+"</script><div class=post-content_main-post-info__fixture><div class=thread-content-detail>"+main+"</div></div><div class=reply-list-wrapper><div class=thread-content-detail>"+visible+"</div><div class=thread-content-detail><p>同文附图</p></div><div class=thread-content-detail><p>同文附图</p>"+img("different")+"</div><div class=thread-content-detail><p>同文附图</p><span hidden>"+img("secret")+"</span></div></div></html>";
    }
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
        boolean article=url.toString().equals(target),image=url.getHost().equals("i.hupucdn.com")&&url.getPath().startsWith("/qr-r8-");
        if(!article&&!image)throw new IOException("Unexpected controlled URL");
        byte[] body=article?html.getBytes(StandardCharsets.UTF_8):png;
        return new HttpURLConnection(url){public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}public int getResponseCode(){if(article)requests.incrementAndGet();return 200;}public String getHeaderField(String name){return "Content-Type".equalsIgnoreCase(name)?article?"text/html; charset=utf-8":"image/png":null;}public InputStream getInputStream(){return new ByteArrayInputStream(body);}};
    }});}
    void run(){Context context=test.getTargetContext();try{
        check(context.getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator only");
        check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"non-debuggable R8 target");
        Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);bitmap.eraseColor(0xff3c9b72);try(ByteArrayOutputStream bytes=new ByteArrayOutputStream()){bitmap.compress(Bitmap.CompressFormat.PNG,100,bytes);png=bytes.toByteArray();}finally{bitmap.recycle();}transport();
        for(boolean login:new boolean[]{false,true}){
            String id=String.valueOf(System.currentTimeMillis());target="https://bbs.hupu.com/"+id+".html";html=fixture(id,login);requests.set(0);
            JSONObject item=new JSONObject().put("source","HUPU").put("title","受控图片主帖").put("url",target).put("detail","").put("video",false);
            activity=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("two actual reader sections",()->reader()!=null&&"2".equals(js(reader(),"document.querySelectorAll('.part').length")));
            String mode=login?"LOGIN_SEE":"public status3";
            check(requests.get()==1,mode+": actual native source read once");
            String media=js(reader(),"JSON.stringify(Array.from(document.querySelectorAll('figure img')).map(i=>i.src))");
            report.append("READER_MEDIA ").append(media).append('\n');
            check("true".equals(js(reader(),"(()=>{let a=Array.from(document.querySelectorAll('figure img')).map(i=>i.src),b=JSON.parse("+JSONObject.quote(new JSONArray().put(image("main")).put(image("reply")).toString())+");return a.length===b.length&&a.every((x,n)=>x===b[n])})()")),mode+": pure-image main and reply retained; JSON-only/different/hidden attachment replies excluded");
            js(reader(),"document.querySelectorAll('figure img').forEach(i=>i.loading='eager');true");
            await("actual image decode",()->"true".equals(js(reader(),"Array.from(document.querySelectorAll('figure img')).every(i=>i.naturalWidth===16)")));
            check(true,mode+": both accepted images decode through production image loader");
            Activity closing=activity;ui(closing::finish);test.waitForIdleSync();JSONObject immediate=saved(context);
            report.append("EXIT_STATE ").append(mode).append(" destroyed=").append(closing.isDestroyed()).append(" historyPresent=").append(immediate!=null).append(" documentPresent=").append(immediate!=null&&immediate.has("document")).append('\n');
            await("actual reader destruction",closing::isDestroyed);check(true,mode+": prior reader fully exits before history assertion or next source");activity=null;
            await("persisted reader body",()->{JSONObject value=saved(context);return value!=null&&value.has("document");});
            JSONObject saved=saved(context);
            check(saved!=null&&saved.has("document"),mode+": ordinary reader exit persists extracted body");
            JSONObject doc=saved.getJSONObject("document");JSONArray sections=doc.getJSONArray("sections");
            check(doc.optInt("hupuBodyRevision")==HupuDiscussion.BODY_REVISION&&sections.length()==2&&sections.getJSONObject(0).optString("id").equals("hupu-main-"+id)&&sections.getJSONObject(1).optString("id").equals("hupu-reply-10"),mode+": history keeps current revision and exact accepted source IDs");
            check(!doc.toString().contains("未渲染的旧热门回复"),mode+": rejected highlight never reserves or enters the accepted regular reply");
            check(!doc.toString().contains(image("secret"))&&!doc.toString().contains(image("different")),mode+": uncorroborated attachments never enter persisted reader body");
            check(!doc.toString().contains("隐藏辅助内容")&&!doc.toString().contains(image("hidden-helper")),mode+": hidden source markup neither blocks the visible body nor enters reader history");
        }
    }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
    finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
