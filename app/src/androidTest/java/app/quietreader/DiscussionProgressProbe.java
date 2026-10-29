package app.quietreader;

import android.app.*;
import android.os.Bundle;
import android.webkit.CookieManager;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;
import static app.quietreader.ArticleProgressEvidence.*;

/** Actual source parser, scoped binding, SQLite and explicit refresh; synthetic discussion. */
final class DiscussionProgressProbe {
    private final Source source;private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    DiscussionProgressProbe(Instrumentation test){this(test,Source.V2EX);}
    DiscussionProgressProbe(Instrumentation test,Source source){this.test=test;this.source=source;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private String prose(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    private String reply(long at){return "<div class=cell id=r_90001><span class=ago title='"+java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx").format(java.time.Instant.ofEpochMilli(at).atOffset(java.time.ZoneOffset.ofHours(8)))+"'>刚刚</span><div class=reply_content>【最新进展】"+prose(2)+"</div></div>";}
    private Document document(HotCandidate candidate,long replyAt){
        if(source==Source.V2EX)return SourceParser.article(source,"<div class=topic_content>"+prose(1)+"</div>"+(replyAt>0?reply(replyAt):""),candidate.originalUrl);
        if(source==Source.TIEBA){
            Document doc=SourceParser.article(source,"<div class=pc-pb-box><div class=image-text><div class=pb-content-wrap>"+prose(1)+"</div></div><div class=pc-pb-reply-list><div class=pb-comment-list><div class=pb-comment-item data-id=154074790043><div class=comment-content><div class=pb-rich-text>"+prose(3)+"</div></div>"+(replyAt>0?"<div class=pb-lzl-item data-id=154076383836><div class=comment-content><div class=pb-rich-text>【最新进展】"+prose(2)+"</div></div></div>":"")+"</div></div></div></div>",candidate.originalUrl);
            try{if(replyAt>0)TiebaReplyTimes.attach(doc,new org.json.JSONObject().put("url",candidate.originalUrl).put("rows",new org.json.JSONArray().put(new org.json.JSONArray().put("154074790043").put("154076383836").put(replyAt/1000))),System.currentTimeMillis());}catch(org.json.JSONException error){throw new IllegalStateException(error);}return doc;
        }
        try{org.json.JSONObject thread=new org.json.JSONObject().put("tid",HupuDiscussion.threadId(candidate.originalUrl)).put("url",java.net.URI.create(candidate.originalUrl).getPath()).put("content","<p>"+prose(1)+"</p>").put("status",0).put("visibleRange","ALL_SEE");org.json.JSONArray rows=new org.json.JSONArray();if(replyAt>0)rows.put(new org.json.JSONObject().put("pid","90001").put("createdAt",replyAt).put("content","<p>【最新进展】"+prose(2)+"</p>"));org.json.JSONObject detail=new org.json.JSONObject().put("thread",thread).put("visibleRange","ALL_SEE").put("lights",new org.json.JSONArray()).put("replies",new org.json.JSONObject().put("current",1).put("list",rows));return SourceParser.article(source,"<script id=__NEXT_DATA__>"+new org.json.JSONObject().put("props",new org.json.JSONObject().put("pageProps",new org.json.JSONObject().put("detail",detail)))+"</script>",candidate.originalUrl);}catch(org.json.JSONException e){throw new IllegalStateException(e);}
    }
    private ArticleProgressBinding binding(HotCandidate candidate,long acquired,long replyAt){
        Document doc=document(candidate,replyAt);ArticleProgressBinding[] value={null};
        ui(()->{ArticleSession session=new ArticleSession(test.getTargetContext(),candidate.readerItem());if(!session.stamp(doc))throw new IllegalStateException("Synthetic acquisition stamp failed");doc.cachedAt=acquired;value[0]=ArticleProgressBinding.capture(test.getTargetContext(),candidate,doc);});
        if(value[0]==null)throw new IllegalStateException("Synthetic discussion did not capture");return value[0];
    }
    private void cookie(String url,String name,String value)throws Exception{cookie(url,name,value,"/");}
    private void cookie(String url,String name,String value,String path)throws Exception{CountDownLatch done=new CountDownLatch(1);ui(()->CookieManager.getInstance().setCookie(url,name+"="+value+"; Path="+path+"; Secure",ignored->done.countDown()));if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Cookie fixture timed out");}
    private void selectedPostAccount(FeedStore store,HotCandidate post,long replyAt,String cookieName)throws Exception{
        String topicUrl="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id="+System.currentTimeMillis();
        HotCandidate topic=new HotCandidate(Source.TIEBA,topicUrl,"合成热搜话题","","",false,1,null,System.currentTimeMillis());
        Document doc=document(post,replyAt);ArticleProgressBinding[] frozen={null};
        ui(()->{TiebaTopic.attach(topicUrl,List.of(post.readerItem()),doc,ArticleDiskCache.session(post.originalUrl),System.currentTimeMillis());if(!new ArticleSession(test.getTargetContext(),topic.readerItem()).stamp(doc))throw new IllegalStateException("Topic stamp failed");frozen[0]=ArticleProgressBinding.capture(test.getTargetContext(),topic,doc);});
        check(frozen[0]!=null,"resolved topic captures actual selected post and candidate identities");
        if(frozen[0]==null)return;
        try{
            cookie(post.originalUrl,cookieName,"post-account","/p/");
            check(frozen[0].scope.current(),"post-path cookie change leaves topic-path scope unchanged in controlled fixture");
            frozen[0].observe(store,()->true);frozen[0].consumed(store,System.currentTimeMillis());
            try(android.database.Cursor rows=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM article_progress WHERE identity=?",new String[]{topic.id})){rows.moveToFirst();check(rows.getInt(0)==0,"selected post account guard rejects delayed observation and consumption despite unchanged topic scope");}
            ArticleProgressBinding[] next={null};ui(()->next[0]=ArticleProgressBinding.capture(test.getTargetContext(),topic,doc));check(next[0]==null,"old resolved body cannot recapture after selected-post account changes");
        }finally{cookie(post.originalUrl,cookieName,"; Max-Age=0","/p/");}
    }
    void run(){String database="test-discussion-progress-"+UUID.randomUUID()+".db",cookieName="qr_discussion_TOKEN_"+UUID.randomUUID().toString().replace("-","");String url=source==Source.V2EX?"https://www.v2ex.com/t/"+System.currentTimeMillis():source==Source.TIEBA?"https://tieba.baidu.com/p/"+System.currentTimeMillis():"https://bbs.hupu.com/"+System.currentTimeMillis()+".html";long now=System.currentTimeMillis()/1000*1000,old=now-10000,seen=now-8000,updated=now-2000;String scope=null,identity=null;
        try{
            cookie(url,cookieName,"first");
            HotCandidate candidate=new HotCandidate(source,url,"讨论进展受控中文主题","","",false,1,null,now),other=new HotCandidate(source,url.replace(".html","")+"1"+(source==Source.HUPU?".html":""),"另一个未见过的同质量主题","","",false,2,null,now);
            ArticleProgressBinding before=binding(candidate,old,0),after=binding(candidate,now,updated);scope=before.scope.key;identity=candidate.id;
            check(before.input.finish(now).modifiedAt==0&&after.input.finish(now).modifiedAt==updated,"actual Android parser and UI-safe bindings keep main-only baseline separate from a later created marked reply");
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.upsertSnapshot(source,store.beginRefresh(source),List.of(candidate,other),now);
                long id=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"personal","all",List.of(new FeedStore.Row("discussion",candidate,"",List.of(candidate),0),new FeedStore.Row("unseen",other,"",List.of(other),0)));store.activateSnapshot(id);
                ArticleProgressHistory history=new ArticleProgressHistory(store.getWritableDatabase());long profile=store.version("profile"),source=store.version("sources");
                before.observe(store,()->true);after.observe(store,()->true);
                check(history.progress(candidate.id,scope,seen,now)==0,"prefetch alone does not fabricate an exposure baseline or promote a thread");
                store.getWritableDatabase().delete("article_progress",null,null);before.observe(store,()->true);
                List<HotCandidate> accepted=store.recordExposedMembers(id,List.of("discussion"),seen);for(HotCandidate member:accepted)history.presented(member.id,scope,seen,now);
                FeedStore.ProgressLookup lookup=(row,recent,at)->history.progress(row.candidate.id,before.scope.key,Math.max(recent.shownAt,recent.readAt),at);
                long cooled=store.refreshSnapshot(id,"all",now,lookup);check(store.readSnapshot(cooled,10,0).get(0).eventId.equals("unseen"),"real accepted exposure cools the unchanged discussion behind an unseen same-band item");
                after.observe(store,()->true);check(history.progress(candidate.id,scope,seen,now)==updated,"scoped production binding persists substantial new-reply proof on storage worker");
                check(ArticleProgressPreparation.read(store,cooled,now).candidates.containsKey(candidate.id),"actual requested-snapshot prefilter schedules source account scope only after a persisted proof exists");
                long returned=store.refreshSnapshot(cooled,"all",now,lookup);check(store.readSnapshot(returned,10,0).get(0).eventId.equals("discussion"),"SQLite explicit refresh uses the new-reply proof to restore same-band relevance");
                check(history.progress(candidate.id,HotCandidate.hash("other-account"),seen,now)==0&&history.progress(candidate.id,scope,now-1000,now)==0,"different account scope and later whole-event reading both block the proof");
                before.consumed(store,seen);check(history.progress(candidate.id,scope,seen,now)==updated,"qualified reading uses the frozen body actually displayed despite a newer prefetched reply");
                before.consumed(store,now);check(history.progress(candidate.id,scope,now,now)==0,"a reply older than the actual latest read cannot be repeatedly promoted");
                store.recordExposed(returned,List.of("discussion"),now);history.presented(candidate.id,scope,now,now);long consumed=store.refreshSnapshot(returned,"all",now,lookup);
                check(store.readSnapshot(consumed,10,0).get(0).eventId.equals("unseen"),"actual later presentation consumes proof and returns unseen-first behavior");
                store.getWritableDatabase().delete("article_progress",null,null);before.observe(store,()->true);history.presented(candidate.id,scope,seen,now);after.observe(store,()->false);
                check(history.progress(candidate.id,scope,seen,now)==0,"cancelled acquisition cannot persist new discussion evidence");
                after.observe(store,()->true);
                check(store.version("profile")==profile&&store.version("sources")==source&&store.activeFeedback(now).length()==0,"discussion evidence never changes interest, platform preferences or source versions");
                Snapshot roundtrip=ArticleProgressHistory.decode(after.input.finish(now).json().toString(),now);check(roundtrip!=null&&roundtrip.discussion!=null,"actual Android hash-only JSON remains within existing bounded depth/byte decoder");
                if(this.source==Source.TIEBA)selectedPostAccount(store,candidate,updated,cookieName+"_post_TOKEN");
            }
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(new ArticleProgressHistory(reopened.getWritableDatabase()).progress(identity,scope,seen,now)==updated,"SQLite reopen preserves the exact scoped discussion proof");cookie(url,cookieName,"second");check(!before.scope.current()&&!after.scope.current(),"actual CookieManager account rotation invalidates both prior discussion captures");ArticleProgressBinding next=binding(candidate,now,updated);check(!next.scope.key.equals(scope)&&new ArticleProgressHistory(reopened.getWritableDatabase()).progress(identity,next.scope.key,seen,now)==0,"new account scope cannot inherit prior-account discussion evidence");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{test.getTargetContext().deleteDatabase(database);try{cookie(url,cookieName,"; Max-Age=0");}catch(Exception cleanup){failures++;report.append("ERROR synthetic cookie cleanup\n");}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
