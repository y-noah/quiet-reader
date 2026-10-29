package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** A source window adds only child replies, never a new floor or end marker. */
final class TiebaNestedProgressProbe {
    private static final String URL="https://tieba.baidu.com/p/990077701";
    private final Instrumentation test;
    private Activity owner;private WebView web;private TiebaReplies replies;
    private int checks,failures;private final StringBuilder report=new StringBuilder("Controlled child-only source progress; no real account claim\n");
    TiebaNestedProgressProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean valid,String name){checks++;if(!valid)failures++;report.append(valid?"PASS ":"FAIL ").append(name).append('\n');}
    private void script(String js)throws Exception{CountDownLatch done=new CountDownLatch(1);ui(()->web.evaluateJavascript(js,ignored->done.countDown()));if(!done.await(4,TimeUnit.SECONDS))throw new IllegalStateException("fixture script timeout");}
    private Document sample()throws Exception{
        CountDownLatch done=new CountDownLatch(1);Document[] result={null};
        ui(()->web.evaluateJavascript(SourceSnapshot.script(Source.TIEBA),raw->{try{String json=(String)new org.json.JSONTokener(raw).nextValue();result[0]=SourceParser.article(Source.TIEBA,new org.json.JSONObject(json).getString("html"),URL);new ArticleSession(owner,URL).stamp(result[0]);}catch(Exception error){report.append(error).append('\n');}done.countDown();}));
        if(!done.await(4,TimeUnit.SECONDS)||result[0]==null)throw new IllegalStateException("snapshot unavailable");return result[0];
    }
    private Document more(Document previous)throws Exception{
        CountDownLatch done=new CountDownLatch(1);Document[] result={null};String[] failure={null};long start=SystemClock.elapsedRealtime();
        ui(()->replies.more(previous,new Repository.Result<>(){public void success(Document doc){result[0]=doc;done.countDown();}public void failure(String why){failure[0]=why;done.countDown();}}));
        check(done.await(23,TimeUnit.SECONDS),"child-only batch terminates within source deadline");
        check(result[0]!=null,"child-only source change is returned instead of discarded: "+failure[0]);
        report.append("BATCH ms=").append(SystemClock.elapsedRealtime()-start).append('\n');return result[0];
    }
    private void add(String id,String image)throws Exception{
        String row="<div class='pb-lzl-item'"+(id.isEmpty()?"":" data-id='"+id+"'")+"><a class='head-name'>楼内作者</a><div class='comment-content'><div class='pb-rich-text'><img class='emoji-img' src='"+image+"' alt='表情'></div></div></div>";
        script("document.querySelector('.pb-comment-item').insertAdjacentHTML('beforeend',"+org.json.JSONObject.quote(row)+")");
    }
    void run(){String database="test-ui-tieba-child-"+UUID.randomUUID()+".db";
        try{
            owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            String html="<html><meta charset=utf-8><style>.pc-pb-box{height:200px;overflow:auto}.pb-comment-item{min-height:400px}</style><div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>主帖正文</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=1001><a class=user-name>楼层作者</a><div class=comment-content><div class=pb-rich-text>固定楼层正文</div></div></div></div><div class=loading>加载中</div></div></div></html>";
            CountDownLatch loaded=new CountDownLatch(1);
            ui(()->{web=new WebView(owner);SourceSession.configure(owner,Source.TIEBA,web);SourceSurface.attach(owner,web);web.getSettings().setJavaScriptEnabled(true);web.setWebViewClient(new WebViewClient(){public void onPageFinished(WebView v,String url){loaded.countDown();}});web.loadDataWithBaseURL(URL,html,"text/html","UTF-8",URL);});
            check(loaded.await(5,TimeUnit.SECONDS),"controlled window loaded");
            Document before=sample();check(before.virtualDiscussion&&before.sections.size()==2&&before.moreStatus.isEmpty(),"one floor without end marker is the starting body");
            ui(()->replies=new TiebaReplies(owner,new Item(Source.TIEBA,"候选",URL,""),web));
            String emoji="https://tb3.bdstatic.com/emoji/image_emoticon1.png";
            add("2001",emoji);Document first=more(before);if(first==null)return;
            check(first.sections.size()==3&&first.sections.get(2).nestedReply&&first.sections.get(2).parentSectionId.equals(first.sections.get(1).id),"new emoji child retains its actual floor");
            check(first.sections.get(1).sourcePostId.equals("1001")&&!first.moreStatus.equals("end"),"no invented floor or source completion");
            add("2002",emoji);Document second=more(first);if(second==null)return;
            check(second.sections.size()==4&&!second.sections.get(2).id.equals(second.sections.get(3).id),"identical emoji with different source child IDs both survive");
            String other="https://tb3.bdstatic.com/emoji/image_emoticon2.png";add("",other);
            script("(()=>{const image=document.querySelector('.pb-lzl-item:last-child img');image.setAttribute('data-original',image.getAttribute('src'));image.setAttribute('src','data:image/gif;base64,AA')})()");
            Document third=more(second);if(third==null)return;
            check(third.sections.size()==5&&third.sections.get(4).nestedReply&&third.sections.get(4).blocks.get(0).inlineImages.get(0).url.equals(other),"new child without source ID keeps usable lazy emoji and counts as progress");
            ui(()->new ReadingHistory(owner).save(URL,third,Set.of(),0));
            Document restored=ReadingHistory.restore(new ReadingHistory(owner).get(URL));
            check(DocumentFingerprint.of(third).equals(DocumentFingerprint.of(restored))&&restored.sections.get(4).parentSectionId.equals(restored.sections.get(1).id),"child-only continuation survives history with parent relationship");
            script("(()=>{const image=document.querySelector('.pb-lzl-item:last-child img');image.setAttribute('src',image.getAttribute('data-original'));image.removeAttribute('data-original')})()");
            CountDownLatch stopped=new CountDownLatch(1);Document[] unexpected={null};String[] why={null};
            ui(()->replies.more(restored,new Repository.Result<>(){public void success(Document doc){unexpected[0]=doc;stopped.countDown();}public void failure(String value){why[0]=value;stopped.countDown();}}));
            check(stopped.await(23,TimeUnit.SECONDS)&&unexpected[0]==null&&why[0]!=null&&why[0].contains("本次未取得新的回复"),"unchanged floor and children stop for no new reply, not stale session or false progress: "+why[0]);
            check(restored.sections.size()==5,"stationary source preserves all previously read sections");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(replies!=null)replies.close();else if(web!=null)SourceSurface.release(web);if(owner!=null)owner.finish();});test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(URL).commit();test.getTargetContext().deleteDatabase(database);report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
