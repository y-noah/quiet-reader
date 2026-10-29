package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Controlled metadata changes; real repository/preloader/session/SQLite, no source transport. */
final class ArticleVersionFlowProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC ACQUISITIONS; real version isolation/cache/preloader/SQLite\n");
    ArticleVersionFlowProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){boolean[] ok={false};ui(()->ok[0]=condition.getAsBoolean());if(ok[0])return true;Thread.sleep(50);}return false;}
    private void drain()throws Exception{Field field=ReadingLanguageRecorder.class.getDeclaredField("writer");field.setAccessible(true);((ExecutorService)field.get(null)).submit(()->{}).get(3,TimeUnit.SECONDS);}
    void run(){
        String database="test-ui-body-version-"+UUID.randomUUID()+".db";DiscoveryActivity activity=null;Repository[] repo={null};ArticlePreloader[] preloader={null};
        long now=System.currentTimeMillis();String url="https://bbs.hupu.com/"+now+".html";
        HotCandidate before=new HotCandidate(Source.HUPU,url,"旧中文标题","旧摘要","",false,1,null,now),after=new HotCandidate(Source.HUPU,url,"新中文标题","新摘要","",false,1,null,now);
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(after),now);
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DiscoveryActivity owner=activity;
            Document old=new Document();old.url=url;old.blocks.add(new Block("text","Foreign article prose should not classify the new metadata. ".repeat(30)));ArticleVersion.capture(before.readerItem(),old);
            ui(()->{repo[0]=new Repository(owner);repo[0].cacheArticle(old);ReadingLanguageRecorder.record(owner,database,after,old);});drain();
            check(store.candidates(10,0).get(0).bodyLanguage==ContentLanguage.Body.UNKNOWN,"old-version foreign prose cannot classify new candidate language");
            ui(()->{old.videoTopic=true;ReadingLanguageRecorder.record(owner,database,after,old);});drain();
            check(!store.candidates(10,0).get(0).video,"old-version video body cannot classify the new candidate");old.videoTopic=false;
            AtomicInteger loads=new AtomicInteger(),prepared=new AtomicInteger();Repository.Result<Document>[] pending=new Repository.Result[1];
            ArticlePreloader.Loader loader=new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> cb){loads.incrementAndGet();pending[0]=cb;}public void cancel(){pending[0]=null;}};
            ui(()->{preloader[0]=new ArticlePreloader(owner,()->{},loader,20,()->{},true);preloader[0].observePrepared((item,doc)->prepared.incrementAndGet());preloader[0].offer(List.of(after.readerItem()));});
            check(await(()->loads.get()==1)&&prepared.get()==0,"discovery preloader bypasses differently versioned memory body");
            HotCandidate newest=new HotCandidate(Source.HUPU,url,after.title,"再次更新的摘要","",false,1,null,now);
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(newest),now);
            Document stale=new Document();stale.url=url;ArticleVersion.capture(after.readerItem(),stale);
            ui(()->{check(!preloader[0].reserve(newest.readerItem()),"same URL with new metadata cannot reserve an old acquisition");Repository.Result<Document> late=pending[0];check(!preloader[0].promote(newest.readerItem(),new Repository.Result<Document>(){public void success(Document d){prepared.incrementAndGet();}public void failure(String why){prepared.incrementAndGet();}}),"promotion rejects a different metadata version");late.success(stale);check(prepared.get()==0&&repo[0].cachedArticle(before.readerItem())==old,"cancelled old acquisition cannot publish or replace cache");preloader[0].offer(List.of(newest.readerItem()));});
            check(await(()->loads.get()==2),"latest candidate starts one replacement acquisition");
            Document fresh=SourceParser.article(Source.HUPU,"<h1>新正文</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail>"+"这是与最新话题版本对应的中文正文。".repeat(40)+"</div></div>",url);
            ui(()->{check(new ArticleSession(owner,newest.readerItem()).stamp(fresh),"source session captures requested metadata before publishing");pending[0].success(fresh);});
            check(await(()->prepared.get()==1),"only current acquisition reaches prepared observer");
            ui(()->{check(repo[0].cachedArticle(newest.readerItem())==fresh&&ArticleVersion.matches(newest.readerItem(),fresh),"fresh cache retains its acquired candidate version");ReadingLanguageRecorder.record(owner,database,newest,fresh);});drain();
            check(store.candidates(10,0).get(0).bodyLanguage==ContentLanguage.Body.CHINESE,"current-version body may contribute language evidence");
            Item fourth=new Item(Source.HUPU,"第四版中文标题",url,""),fifth=new Item(Source.HUPU,"第五版中文标题",url,"");
            ui(()->preloader[0].offer(List.of(fourth)));check(await(()->loads.get()==3),"another metadata version starts fresh acquisition");
            Repository.Result<Document> superseded=pending[0];ui(()->preloader[0].offer(List.of(fifth)));
            check(await(()->loads.get()==4),"offering newer metadata cancels and replaces the same-URL acquisition");
            ui(()->{superseded.success(fresh);check(prepared.get()==1,"late result from superseded metadata is not published");preloader[0].pause();});
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{DiscoveryActivity end=activity;ui(()->{if(preloader[0]!=null)preloader[0].close();if(repo[0]!=null){repo[0].releaseArticleMemory();repo[0].close();}if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
