package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** A real scrollable WebView replacing virtual rows; all source content is controlled. */
final class TiebaRepliesProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("Controlled virtual-list fixture, no real account claim\n");
    private static final String URL="https://tieba.baidu.com/p/990077700";
    private Activity owner;private WebView web;private TiebaReplies replies;
    private boolean rehydrate,slow;
    TiebaRepliesProbe(Instrumentation test){this.test=test;}
    TiebaRepliesProbe(Instrumentation test,boolean rehydrate){this.test=test;this.rehydrate=rehydrate;}
    TiebaRepliesProbe(Instrumentation test,boolean rehydrate,boolean slow){this.test=test;this.rehydrate=rehydrate;this.slow=slow;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private String fixture(){return "<html><meta charset=utf-8><style>.pc-pb-box{height:400px;overflow:auto}.pb-comment-list{height:3000px;position:relative}.pb-comment-item{position:absolute;height:100px}</style><h1>实际帖子</h1><div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖正文</div></div><div class=pc-pb-reply-list><div class=pb-comment-list></div><div class=loading>—— 已加载全部评论 ——</div></div></div><script>const box=document.querySelector('.pc-pb-box'),list=document.querySelector('.pb-comment-list');let ticks=0;function draw(){ticks++;let start=Math.max(0,Math.floor(box.scrollTop/100)-1);list.innerHTML='';for(let i=start;i<Math.min(30,start+7);i++){let row=document.createElement('div');row.className='pb-comment-item';row.setAttribute('data-id',String(1000+i));row.style.top=(i*100)+'px';row.innerHTML='<div class=comment-content><div class=pb-rich-text>回复'+i+'</div></div>';list.appendChild(row)}}box.addEventListener('scroll',draw);draw();</script></html>";}
    private int count(Document doc){return (int)doc.sections.stream().filter(ReaderHtml::discussion).count();}
    private Document sample()throws Exception{
        CountDownLatch done=new CountDownLatch(1);Document[] result={null};
        ui(()->web.evaluateJavascript(SourceSnapshot.script(Source.TIEBA),raw->{try{String json=(String)new org.json.JSONTokener(raw).nextValue();result[0]=SourceParser.article(Source.TIEBA,new org.json.JSONObject(json).getString("html"),URL);new ArticleSession(owner,URL).stamp(result[0]);}catch(Exception error){report.append(error).append('\n');}done.countDown();}));
        if(!done.await(3,TimeUnit.SECONDS)||result[0]==null)throw new IllegalStateException("No fixture snapshot");return result[0];
    }
    private Document more(Document before)throws Exception{
        CountDownLatch done=new CountDownLatch(1);Document[] result={null};String[] failure={null};
        ui(()->replies.more(before,new Repository.Result<>(){public void success(Document doc){result[0]=doc;done.countDown();}public void failure(String why){failure[0]=why;done.countDown();}}));
        check(done.await(23,TimeUnit.SECONDS),"virtual batch terminates within bounded deadline");
        check(result[0]!=null,"virtual batch returns merged replies: "+failure[0]);return result[0];
    }
    void run(){String database="test-ui-tieba-virtual-"+UUID.randomUUID()+".db";
        try{
            owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            CountDownLatch loaded=new CountDownLatch(1);
            String source=rehydrate?fixture().replace("height:3000px","height:"+(slow?2000:8000)+"px").replace("Math.min(30,","Math.min("+(slow?20:80)+","):fixture();
            if(slow)source=source.replace("box.addEventListener('scroll',draw)","let pending;box.addEventListener('scroll',()=>{clearTimeout(pending);pending=setTimeout(draw,1200)})");
            String sourceHtml=source;
            ui(()->{web=new WebView(owner);SourceSession.configure(owner,Source.TIEBA,web);SourceSurface.attach(owner,web);web.getSettings().setJavaScriptEnabled(true);web.setWebViewClient(new WebViewClient(){public void onPageFinished(WebView v,String url){loaded.countDown();}});web.loadDataWithBaseURL(URL,sourceHtml,"text/html","UTF-8",URL);});
            check(loaded.await(5,TimeUnit.SECONDS),"controlled source WebView loaded");Thread.sleep(250);
            Document before=sample();check(before.virtualDiscussion&&count(before)==7&&before.moreStatus.isEmpty(),"initial window is partial despite source download-end marker");
            if(rehydrate){
                int knownCount=slow?10:70,totalCount=slow?20:80;
                StringBuilder known=new StringBuilder("<div class=pc-pb-box><div class=pc-pb-reply-list><div class=pb-comment-list>");
                for(int i=0;i<knownCount;i++)known.append("<div class=pb-comment-item data-id=").append(1000+i).append("><div class=comment-content><div class=pb-rich-text>回复").append(i).append("</div></div></div>");
                known.append("</div></div></div>");
                Document cached=TiebaReplies.merge(before,SourceParser.article(Source.TIEBA,known.toString(),URL));
                ArticleDiskCache disk=new ArticleDiskCache(owner);String namespace=disk.namespace(),session=ArticleDiskCache.session(URL);long at=System.currentTimeMillis();
                disk.enqueue(cached,namespace,session,at);
                java.lang.reflect.Field writer=ArticleDiskCache.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(4,TimeUnit.SECONDS);
                Repository repository=new Repository(owner);CountDownLatch restored=new CountDownLatch(1);Document[] restoredBody={null};
                ui(()->repository.restoreArticle(new Item(Source.TIEBA,"候选",URL,""),doc->{restoredBody[0]=doc;restored.countDown();}));
                check(restored.await(4,TimeUnit.SECONDS)&&restoredBody[0]!=null&&count(restoredBody[0])==knownCount,"previously read replies restore through repository session validation: "+knownCount);
                repository.close();
                Document restoredDocument=restoredBody[0];
                ui(()->replies=new TiebaReplies(owner,new Item(Source.TIEBA,"候选",URL,""),web));
                long start=SystemClock.elapsedRealtime();Document continued=more(restoredDocument);
                if(slow)for(int batch=0;continued!=null&&!continued.moreStatus.equals("end")&&batch<5;batch++)continued=more(continued);
                if(continued!=null){check(count(continued)>knownCount,"fresh source crosses old windows and reaches unseen replies in one bounded batch");check(continued.sections.get(0)==restoredDocument.sections.get(0),"rehydration preserves cached main post");check(continued.sections.stream().filter(ReaderHtml::discussion).map(s->s.sourcePostId).distinct().count()==count(continued),"rehydration retains unique reply identities");
                    Set<String> ids=new HashSet<>();for(Section section:continued.sections)if(ReaderHtml.discussion(section))ids.add(section.sourcePostId);
                    boolean complete=ids.size()==totalCount;for(int i=0;i<totalCount;i++)complete&=ids.contains(String.valueOf(1000+i));
                    check(complete&&continued.moreStatus.equals("end"),"all source identities survive recovery without skipping unseen rows: "+totalCount);
                    if(!slow){
                    CountDownLatch frozen=new CountDownLatch(1);
                    ui(()->web.evaluateJavascript("(()=>{box.removeEventListener('scroll',draw);box.scrollTop=0;draw();document.querySelector('.loading').textContent='加载中'})()",ignored->frozen.countDown()));
                    check(frozen.await(3,TimeUnit.SECONDS),"source fixture enters a stationary loading state");
                    CountDownLatch stopped=new CountDownLatch(1);String[] why={null};Document[] unexpected={null};long stalledAt=SystemClock.elapsedRealtime();
                    ui(()->replies.more(restoredDocument,new Repository.Result<>(){public void success(Document doc){unexpected[0]=doc;stopped.countDown();}public void failure(String reason){why[0]=reason;stopped.countDown();}}));
                    check(stopped.await(22,TimeUnit.SECONDS)&&unexpected[0]==null&&why[0]!=null&&why[0].contains("已读内容保留"),"stationary old window stops with retry rather than false completion");
                    check(count(restoredDocument)==70&&SystemClock.elapsedRealtime()-stalledAt<21_000,"stalled recovery preserves original cached body within twenty-second deadline");
                    }
                }
                report.append("REHYDRATE ms=").append(SystemClock.elapsedRealtime()-start).append('\n');
            }else{
            ArticleDiskCache disk=new ArticleDiskCache(owner);String namespace=disk.namespace(),session=ArticleDiskCache.session(URL);long at=System.currentTimeMillis();
            disk.enqueue(before,namespace,session,at);
            java.lang.reflect.Field writer=ArticleDiskCache.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(4,TimeUnit.SECONDS);
            ArticleDiskCache.Hit hit=disk.read(URL,namespace,session,false,at+1);
            check(hit!=null&&hit.document.virtualDiscussion&&hit.document.sections.stream().filter(ReaderHtml::discussion).allMatch(s->s.sourcePostId.matches("[1-9][0-9]{0,19}")),"async disk snapshot preserves virtual flag and stable reply identities");
            ui(()->replies=new TiebaReplies(owner,new Item(Source.TIEBA,"候选",URL,""),web));
            Document merged=more(before);
            if(merged!=null){check(count(merged)>7,"scrolling virtual source adds replies beyond first window");check(merged.sections.get(0)==before.sections.get(0),"main post survives replaced source rows");
                for(int batch=0;batch<4&&!merged.moreStatus.equals("end");batch++){merged=more(merged);if(merged==null)break;}
                if(merged!=null){check(count(merged)==30,"overlapping scroll windows retain all thirty stable reply IDs");check(merged.moreStatus.equals("end"),"end is reported only after traversing source to bottom");check(merged.sections.stream().filter(ReaderHtml::discussion).map(s->s.sourcePostId).distinct().count()==30,"virtual replacements do not duplicate replies");}
            }
            Document old=before;ui(()->CookieManager.getInstance().setCookie(URL,"qr_virtual_account=changed; Path=/p/; Secure"));
            CountDownLatch rejected=new CountDownLatch(1);boolean[] stale={false};ui(()->replies.more(old,new Repository.Result<>(){public void success(Document doc){rejected.countDown();}public void failure(String why){stale[0]=true;rejected.countDown();}}));
            check(rejected.await(2,TimeUnit.SECONDS)&&stale[0],"changed account rejects old buffered document before scrolling");
            Document fresh=sample();java.util.concurrent.atomic.AtomicInteger delivered=new java.util.concurrent.atomic.AtomicInteger();
            ui(()->{Repository.Result<Document> callback=new Repository.Result<>(){public void success(Document doc){delivered.incrementAndGet();}public void failure(String why){delivered.incrementAndGet();}};replies.more(fresh,callback);replies.more(fresh,callback);check(replies.busy(),"repeated continuation remains one in-flight batch");replies.close();});
            Thread.sleep(1200);check(delivered.get()==0,"closed source owner suppresses late snapshots and callbacks");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(replies!=null)replies.close();else if(web!=null)SourceSurface.release(web);CookieManager.getInstance().setCookie(URL,"qr_virtual_account=; Max-Age=0; Path=/p/; Secure");if(owner!=null)owner.finish();});test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
