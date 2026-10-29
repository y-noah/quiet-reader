package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Real Chromium snapshot/fetch/stream/abort with controlled transport, no public requests. */
final class TiebaReplyTimesProbe {
    private final Instrumentation test;private Activity owner;private WebView web;
    private final AtomicInteger requests=new AtomicInteger();private volatile String response;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final String url="https://tieba.baidu.com/p/11139792603?qr_times_probe="+UUID.randomUUID();
    TiebaReplyTimesProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable work){Throwable[] error={null};test.runOnMainSync(()->{try{work.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private void check(boolean value,String text){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(text).append('\n');}
    private String js(String script)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={null};ui(()->web.evaluateJavascript(script,value->{result[0]=value;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("JavaScript callback missing");return result[0];}
    private JSONObject snapshot()throws Exception{return new JSONObject((String)new JSONTokener(js(SourceSnapshot.script(Source.TIEBA))).nextValue());}
    private void load(String html)throws Exception{
        CountDownLatch loaded=new CountDownLatch(1);
        ui(()->{web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String current){loaded.countDown();}
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                String path=request.getUrl().getPath();boolean endpoint="/p/totalComment".equals(path);
                if(endpoint)requests.incrementAndGet();
                return new WebResourceResponse(endpoint?"application/json":"text/plain","UTF-8",200,"OK",Map.of("Cache-Control","no-store"),new java.io.ByteArrayInputStream((endpoint?response:"").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            }
        });web.loadDataWithBaseURL(url,html,"text/html","UTF-8",null);});
        if(!loaded.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Controlled source page missing");test.waitForIdleSync();
    }
    private void stalled()throws Exception{js("window.__qrReplyTimes=null;window.__calls=0;window.__aborts=0;window.fetch=(u,o)=>{window.__calls++;return new Promise((yes,no)=>o.signal.addEventListener('abort',()=>{window.__aborts++;no(new DOMException('cancelled','AbortError'));},{once:true}));};");}
    void run(){String database="test-tieba-times-"+UUID.randomUUID()+".db";
        try{
            JSONObject fixture;try(java.io.InputStream in=test.getContext().getAssets().open("fixtures/tieba-child-times-20261028.json")){fixture=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
            JSONArray rows=fixture.getJSONArray("replies");JSONObject groups=new JSONObject();StringBuilder html=new StringBuilder("<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>受控主帖</div></div><div class=pc-pb-reply-list><div class=pb-comment-list>");String last="";
            for(int n=0;n<rows.length();n++){
                JSONObject row=rows.getJSONObject(n);String parent=row.getString("post_id");
                if(!parent.equals(last)){if(!last.isEmpty())html.append("</div>");last=parent;html.append("<div class=pb-comment-item data-id='").append(parent).append("'><div class=comment-content><div class=pb-rich-text>受控父楼</div></div>");groups.put(parent,new JSONObject().put("comment_info",new JSONArray()));}
                groups.getJSONObject(parent).getJSONArray("comment_info").put(row);html.append("<div class=pb-lzl-item data-id='").append(row.getString("comment_id")).append("'><div class=comment-content><div class=pb-rich-text>受控子回复</div></div></div>");
            }
            html.append("</div></div></div></div>");String body=html.toString();response=new JSONObject().put("errno",0).put("data",new JSONObject().put("comment_list",groups)).toString();
            owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ui(()->{web=new WebView(owner);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);owner.setContentView(web);});load(body);
            check("false".equals(js("document.hidden")),"controlled source WebView is actually foreground-visible");
            JSONObject first=snapshot();check(!first.isNull("html")&&first.isNull("replyTimes"),"starting optional metadata fetch returns readable HTML immediately");
            JSONObject ready=null;long until=SystemClock.elapsedRealtime()+3000;
            while(SystemClock.elapsedRealtime()<until){JSONObject sample=snapshot();ready=sample.optJSONObject("replyTimes");if(ready!=null)break;Thread.sleep(50);}
            check(ready!=null&&ready.getJSONArray("rows").length()==3&&requests.get()==1,"actual Chromium same-origin fetch strips source response to three identity/time rows with one request");
            if(ready==null)throw new IllegalStateException("No metadata from controlled source response");
            Document doc=SourceParser.article(Source.TIEBA,body,url);TiebaReplyTimes.attach(doc,ready,System.currentTimeMillis());Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));
            check(restored.sections.stream().filter(s->s.nestedReply&&s.sourceReplyAt>0).count()==3,"actual parser and Android history preserve all three exact child times");
            check(restored.sections.stream().filter(s->!s.nestedReply).noneMatch(s->s.sourceReplyAt>0),"main and parent floors never borrow child timestamps");
            for(int n=0;n<5;n++)snapshot();check(requests.get()==1,"repeated source snapshots reuse the bounded five-minute result");
            stalled();snapshot();Thread.sleep(1400);check("1".equals(js("window.__calls"))&&"1".equals(js("window.__aborts")),"one-second timeout aborts an unfinished source request");
            snapshot();check("1".equals(js("window.__calls"))&&snapshot().isNull("replyTimes"),"failed request is not retried by subsequent snapshots");
            stalled();snapshot();js("window.dispatchEvent(new Event('pagehide'))");Thread.sleep(50);check("1".equals(js("window.__aborts")),"leaving source page aborts request before its timeout");
            js("window.__qrReplyTimes=null;window.__cancels=0;window.fetch=()=>Promise.resolve(new Response(new ReadableStream({start(c){c.enqueue(new Uint8Array(524289));},cancel(){window.__cancels++;}}))); ");snapshot();Thread.sleep(200);
            check("1".equals(js("window.__cancels"))&&snapshot().isNull("replyTimes"),"oversize stream is cancelled without retaining metadata or blocking prose");
            stalled();js("var gate=document.createElement('div');gate.className='SignContainer-content';gate.textContent='controlled gate';document.body.appendChild(gate)");JSONObject gated=snapshot();check(gated.optBoolean("login")&&"0".equals(js("window.__calls")),"visible source login gate prevents optional metadata request");
            load("<div>没有楼中楼的可读正文</div>");stalled();snapshot();check("0".equals(js("window.__calls")),"no stable child identities means no metadata request");
            load(body);int previousRequests=requests.get();
            String components="(()=>{const rows="+rows+";for(const e of document.querySelectorAll('.pb-lzl-item')){const r=rows.find(r=>r.comment_id===e.getAttribute('data-id')||r.comment_id===e.getAttribute('data-qr-child-id')||r.comment_id===e.__qrTestId);if(!r)continue;e.__qrTestId=r.comment_id;e.removeAttribute('data-id');e.__vue__={$el:e,$props:{pid:Number(r.post_id),commentData:{id:Number(r.comment_id),time:r.now_time}}};}})()";
            js(components);js("const guard=document.createElement('div');guard.className='login-guard-mask';guard.textContent='controlled continuation gate';document.querySelector('.pc-pb-reply-list').appendChild(guard)");
            JSONObject rendered=snapshot();JSONObject direct=rendered.optJSONObject("replyTimes");
            check(direct!=null&&TiebaRenderedReplies.KIND.equals(direct.optString("kind"))&&direct.getJSONArray("rows").length()==3&&requests.get()==previousRequests,"exact Vue2 rendered roots supply three child identities and times without a new request despite a continuation gate");
            Document directDoc=SourceParser.article(Source.TIEBA,rendered.getString("html"),url);TiebaReplyTimes.attach(directDoc,direct,System.currentTimeMillis());
            check(directDoc.moreStatus.equals("login")&&directDoc.sections.stream().filter(s->s.nestedReply&&s.sourceReplyAt>0).count()==3&&directDoc.sections.stream().filter(s->!s.nestedReply).noneMatch(s->s.sourceReplyAt>0),"rendered visible prose metadata preserves the login boundary and never stamps a parent");
            directDoc.cachedAt=System.currentTimeMillis();check(ArticleProgressEvidence.freeze(Source.TIEBA,directDoc,app.quietreader.discovery.HotCandidate.hash("controlled"),System.currentTimeMillis())==null,"continuation restriction still prevents recommendation-progress evidence");
            js("document.querySelector('.pb-lzl-item').__vue__.$el=document.createElement('div')");rendered=snapshot();
            check(rendered.getJSONObject("replyTimes").getJSONArray("rows").length()==2&&"false".equals(js("document.querySelector('.pb-lzl-item').hasAttribute('data-qr-child-id')")),"recycled or unrelated component roots clear old annotations and cannot borrow a prior identity");
            js(components);js("const e=document.querySelector('.pb-lzl-item');e.__vue__.$el=e;e.__vue__.$props.pid=999");rendered=snapshot();
            check(rendered.getJSONObject("replyTimes").getJSONArray("rows").length()==2,"wrong source parent ID excludes the mismatched rendered child");
            js(components);js("const es=document.querySelectorAll('.pb-lzl-item');es[0].__vue__.$el=es[0];es[0].__vue__.$props.pid=154074790043;es[1].__vue__.$props.commentData.id=es[0].__vue__.$props.commentData.id");rendered=snapshot();
            check(rendered.isNull("replyTimes")&&"0".equals(js("document.querySelectorAll('[data-qr-child-id]').length"))&&requests.get()==previousRequests,"duplicate rendered child IDs reject the complete annotation batch without starting a gated fallback request");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(web!=null)SourceSurface.release(web);if(owner!=null)owner.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
