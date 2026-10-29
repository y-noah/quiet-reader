package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static app.quietreader.Models.*;

/** Actual reader auto continuation and source ownership, controlled virtualized source. */
final class TiebaVirtualUiProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("Controlled virtual source; actual reader scroll/lifecycle, no live account claim\n");
    private MainActivity reader;private DynamicReader dynamic;
    private final boolean dark;
    TiebaVirtualUiProbe(Instrumentation test){this(test,false);}
    TiebaVirtualUiProbe(Instrumentation test,boolean dark){this.test=test;this.dark=dark;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private void await(java.util.function.BooleanSupplier ready,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<end){boolean[] result={false};ui(()->result[0]=ready.getAsBoolean());if(result[0])return;Thread.sleep(50);}throw new IllegalStateException("Virtual reader timeout");}
    private int count(Document doc){return (int)doc.sections.stream().filter(ReaderHtml::discussion).count();}
    private Document reading(){return (Document)field(reader,"reading");}
    private String js(String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,value->{result[0]=value;done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Reader script timeout");return result[0];}
    private void dom()throws Exception{long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){String[] page={""};ui(()->page[0]=(String)field(reader,"readerPageUrl"));if(js("location.href==="+org.json.JSONObject.quote(page[0])+"&&document.readyState==='complete'").equals("true"))return;Thread.sleep(50);}throw new IllegalStateException("Reader DOM not committed");}
    private void screenshot(boolean tall)throws Exception{
        CountDownLatch painted=new CountDownLatch(1);ui(()->((WebView)field(reader,"readerWeb")).postVisualStateCallback(1,new WebView.VisualStateCallback(){public void onComplete(long id){painted.countDown();}}));if(!painted.await(4,TimeUnit.SECONDS))throw new IllegalStateException("No visible frame");
        android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("No screenshot");
        String name="tieba-virtual-ui-"+(dark?"dark":"light")+"-"+reader.getResources().getConfiguration().screenWidthDp+"-"+(tall?"long":"short")+".png";
        try(FileOutputStream output=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{bitmap.recycle();}report.append("SCREENSHOT ").append(name).append('\n');
    }
    private String fixture(boolean tall){
        int rowHeight=tall?100:200,window=tall?7:3;
        String main=tall?"<p>主帖段落，用于验证滚动后原有阅读位置保持不变。</p>".repeat(30):"短主帖";
        return "<html><meta charset=utf-8><style>.pc-pb-box{height:400px;overflow:auto}.image-text{height:120px;overflow:hidden}.pb-comment-list{height:"+(40*rowHeight)+"px;position:relative}.pb-comment-item{position:absolute;height:"+rowHeight+"px}</style><h1>统一阅读实际帖子</h1><div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>"+main+"</div></div><div class=pc-pb-reply-list><div class=pb-comment-list></div><div class=loading>—— 已加载全部评论 ——</div></div></div><script>const box=document.querySelector('.pc-pb-box'),list=document.querySelector('.pb-comment-list');function draw(){const start=Math.max(0,Math.floor((box.scrollTop-120)/"+rowHeight+")-1),size=box.scrollTop>0?Math.max(4,"+window+"):"+window+";list.innerHTML='';for(let i=start;i<Math.min(40,start+size);i++){const row=document.createElement('div');row.className='pb-comment-item';row.setAttribute('data-id',String(2000+i));row.style.top=(i*"+rowHeight+")+'px';row.innerHTML='<div class=comment-content><div class=pb-rich-text>回复'+i+'</div></div>';list.appendChild(row)}}box.addEventListener('scroll',draw);draw();</script></html>";
    }
    private void phase(boolean tall,String database)throws Exception{
        String url="https://tieba.baidu.com/p/"+(tall?"990055511":"990055512");Item item=new Item(Source.TIEBA,"统一候选",url,"");
        Document initial=SourceParser.article(Source.TIEBA,"<h1>初始已缓存正文</h1><div id=j_p_postlist><div class=d_post_content>初始正文</div></div>",url);ArticleVersion.capture(item,initial);
        ui(()->{Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(initial);repo.close();});
        reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database));
        report.append("CONFIG widthDp=").append(reader.getResources().getConfiguration().screenWidthDp).append(" fontScale=").append(reader.getResources().getConfiguration().fontScale).append(" dark=").append(Theme.dark(reader)).append('\n');check(Theme.dark(reader)==dark,"reader uses requested theme");
        await(()->field(reader,"reading")!=null,5000);AtomicInteger sourceRequests=new AtomicInteger();CountDownLatch transferred=new CountDownLatch(1);Document[] acquired={null};String[] error={""};
        ui(()->{dynamic=new DynamicReader(reader,item,new Repository.Result<>(){
            public void success(Document doc){acquired[0]=doc;WebView source=dynamic.takeSource();check(source!=null,"dynamic virtual source transfers to actual reader");if(source!=null)try{Method render=MainActivity.class.getDeclaredMethod("renderSource",Item.class,Document.class,WebView.class);render.setAccessible(true);render.invoke(reader,item,doc,source);}catch(Exception e){throw new IllegalStateException(e);}transferred.countDown();}
            public void failure(String why){error[0]=why;transferred.countDown();}
        },true);WebView source=(WebView)field(dynamic,"web");WebViewClient delegate=source.getWebViewClient();source.setWebViewClient(new WebViewClient(){
            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(request.isForMainFrame())sourceRequests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream((request.isForMainFrame()?fixture(tall):"").getBytes(StandardCharsets.UTF_8)));}
            public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}
            public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return delegate.shouldOverrideUrlLoading(view,request);}
        });});
        check(transferred.await(9,TimeUnit.SECONDS)&&acquired[0]!=null,"controlled dynamic source reaches common reader: "+error[0]);if(acquired[0]==null)throw new IllegalStateException("Missing transferred source");
        ui(()->dynamic.close());dom();int initialCount=tall?7:3;check(count(acquired[0])==initialCount,"actual acquisition starts with the partial source window");
        String anchor="";double top=0;
        if(tall){
            await(()->System.currentTimeMillis()>(long)field(reader,"suppressAutoUntil"),4000);
            org.json.JSONObject position=new org.json.JSONObject(js("(()=>{const target=Math.max(0,document.documentElement.scrollHeight-innerHeight),delta=target-scrollY;for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.bottom>delta&&r.top<innerHeight+delta)return {id:a.id,top:r.top-delta,target}}return null})()"));anchor=position.getString("id");top=position.getDouble("top");
            ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.scrollTo(0,Math.round((float)position.optDouble("target")*web.getScale()));check(web.getScrollY()>0,"long reader reaches bottom with a real scroll change");});
        }else check(js("document.documentElement.scrollHeight<=innerHeight+32").equals("true"),"short initial reader fits on screen without a scroll gesture");
        await(()->reading()!=null&&count(reading())>initialCount&&!(boolean)field(reader,"loadingMore"),22000);dom();
        check(true,tall?"bottom scroll automatically appends virtual replies":"fitting short reader automatically starts continuation");
        if(tall){String id=anchor;double expected=top;long end=SystemClock.elapsedRealtime()+6000;double actual=Double.NaN;while(SystemClock.elapsedRealtime()<end){try{actual=Double.parseDouble(js("document.getElementById("+org.json.JSONObject.quote(id)+").getBoundingClientRect().top"));}catch(Exception ignored){}if(Math.abs(actual-expected)<=8)break;Thread.sleep(60);}report.append("ANCHOR expected=").append(expected).append(" actual=").append(actual).append('\n');check(Math.abs(actual-expected)<=8,"append preserves visible reading anchor within eight CSS pixels");}
        check(js("document.documentElement.scrollWidth<=innerWidth+1&&!document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]')").equals("true"),"common reader has no horizontal overflow or more button");
        screenshot(tall);
        for(int batch=0;batch<3&&!reading().moreStatus.equals("end");batch++){
            int before=count(reading());await(()->System.currentTimeMillis()>(long)field(reader,"suppressAutoUntil"),4000);
            ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.scrollTo(0,Math.round(web.getContentHeight()*web.getScale()));});
            await(()->count(reading())>before&&!(boolean)field(reader,"loadingMore")||reading().moreStatus.equals("end"),22000);dom();
        }
        ui(()->{Document doc=reading();check(count(doc)==40&&doc.moreStatus.equals("end"),"repeated actual bottom scrolling reaches all forty replies and stops");check(doc.sections.stream().filter(ReaderHtml::discussion).map(s->s.sourcePostId).distinct().count()==40,"reader continuation never duplicates virtual reply identities");check(sourceRequests.get()==1,"reader reuses transferred source without reloading from the beginning");reader.finish();});test.waitForIdleSync();reader=null;dynamic=null;
    }
    void run(){String database="test-ui-tieba-virtual-ui-"+UUID.randomUUID()+".db";
        android.content.SharedPreferences appearance=test.getTargetContext().getSharedPreferences("appearance",0);boolean had=appearance.contains("mode");int old=appearance.getInt("mode",0);
        try{ui(()->appearance.edit().putInt("mode",dark?2:1).apply());phase(true,database);phase(false,database);}catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(dynamic!=null)dynamic.close();if(reader!=null)reader.finish();android.content.SharedPreferences.Editor edit=appearance.edit();if(had)edit.putInt("mode",old);else edit.remove("mode");edit.apply();});test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
