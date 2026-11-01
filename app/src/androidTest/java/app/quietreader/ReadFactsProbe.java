package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.util.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static app.quietreader.Models.*;

/** Real SQLite, prepared refreshes and actual reader-to-home lifecycle. */
final class ReadFactsProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReadFactsProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private String texts(View view){String out=view instanceof TextView?((TextView)view).getText().toString():"";if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)out+="\n"+texts(((ViewGroup)view).getChildAt(n));return out;}
    private List<FeedStore.Row> all(FeedStore store,long id)throws Exception{List<FeedStore.Row> result=new ArrayList<>();int count=store.snapshotInfo(id).getInt("count");for(int n=0;n<count;n+=200)result.addAll(store.readSnapshot(id,200,n));return result;}
    void restart(String phase){
        String database="test-ui-read-facts-restart.db";ArticleReadHistory history=new ArticleReadHistory(test.getTargetContext(),database);
        HotCandidate candidate=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/99887766","跨进程已读事实","","",false,1,null,System.currentTimeMillis());
        if("write".equals(phase)){
            history.mark(candidate,System.currentTimeMillis());check(history.seen(candidate),"read fact is visible immediately");
            // This fixture has no Activity whose onStop drains Android QueuedWork.
            // Keep its process alive for the same asynchronous disk write instead
            // of Instrumentation.finish killing it at the very next instruction.
            java.io.File file=new java.io.File(test.getTargetContext().getApplicationInfo().dataDir,"shared_prefs/article-reads-"+HotCandidate.hash(database)+".xml");
            long until=SystemClock.elapsedRealtime()+2000;boolean persisted=false;
            do{try{persisted=file.isFile()&&new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains(ArticleReadIdentity.key(candidate));if(persisted)break;Thread.sleep(20);}catch(Exception ignored){}}while(SystemClock.elapsedRealtime()<until);
            check(persisted,"Android async write reaches disk within two seconds");
        }
        else{check(history.seen(candidate),"read marker survives instrumentation process exit and force-stop");test.getTargetContext().deleteSharedPreferences("article-reads-"+HotCandidate.hash(database));}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void run(){String database="test-ui-read-facts-"+UUID.randomUUID()+".db";DiscoveryActivity home=null;MainActivity reader=null;
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();List<FeedStore.Row> rows=new ArrayList<>();
            for(int n=0;n<45;n++){HotCandidate c=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/"+(90000000+n)+"#reply10","已读回归中文话题"+n,"真实控件与合成正文验证","",false,n+1,null,now);candidates.add(c);rows.add(new FeedStore.Row(c.id,c,""));}
            store.ensureCandidatePolicy();for(Source source:Source.values())store.enable(source,source==Source.V2EX);
            store.upsertSnapshot(Source.V2EX,store.beginRefresh(Source.V2EX),candidates,now);store.finishInterestSetup(List.of(),Map.of(),now);long profile=store.profile(now).getLong("version");
            long original=store.commitRanking(store.version("sources"),profile,now,"cold","all",rows);store.activateSnapshot(original);
            ArticleReadHistory history=new ArticleReadHistory(test.getTargetContext(),database);HotCandidate read=candidates.get(0);history.mark(read,now);
            check(store.activeFeedback(now).length()==0&&store.profile(now).getLong("version")==profile,"read fact creates no preference feedback or profile mutation");
            HotCandidate changed=new HotCandidate(Source.V2EX,read.originalUrl.replace("reply10","reply90"),"原标题后来变化","新摘要","999",false,2,null,now+1);
            check(history.seen(changed),"reply-count/title/heat changes cannot bypass read identity");
            long next=store.presentationSnapshot(original,"all",rows.subList(0,15),now+2);check(all(store,next).size()==44&&all(store,next).stream().noneMatch(r->history.seen(r.candidate)),"cached reserve refresh excludes the original even when preferred");
            long slow=store.refreshSnapshot(original,"all",now+3);check(all(store,slow).size()==44,"ordinary refresh excludes the same original");
            long rebuilt=store.commitRanking(store.version("sources"),profile,now+4,"personal","all",rows);check(all(store,rebuilt).size()==44,"model/new snapshot commit cannot reintroduce a read original");
            long promoted=store.promoteReserve(rebuilt,"all",rows.subList(0,15),now+5);check(all(store,promoted).size()==44,"reserve promotion cannot append read leftovers");
            HotCandidate other=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/90000000",read.title,"","",false,1,null,now);
            List<FeedStore.Row> group=history.unread(List.of(new FeedStore.Row("same-event",read,"",List.of(read,other))));
            check(group.size()==1&&group.get(0).candidate.id.equals(other.id)&&group.get(0).entries.size()==1&&!history.seen(other),"another platform original remains unread and becomes the group entry");
            check(new ArticleReadHistory(test.getTargetContext(),database).seen(read),"read fact survives history owner recreation");
            check(store.unread(all(store,original)).size()==44,"saved active snapshot is filtered on restore");
            HotCandidate legacy=candidates.get(1);store.getWritableDatabase().execSQL("INSERT OR REPLACE INTO feed_exposure VALUES (?,?,?,?)",new Object[]{"member:"+legacy.id,0,now,now});history.migrated(false);store.unread(rows);
            check(history.seen(legacy),"previous versions' persisted read exposure migrates without preference learning");
            // Real home -> reader -> Back, using a cached body and no source requests.
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryActivity shownHome=home;test.runOnMainSync(()->shownHome.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));long deadline=SystemClock.elapsedRealtime()+15000;AtomicBoolean ready=new AtomicBoolean();
            do{test.runOnMainSync(()->ready.set(shownHome.hasWindowFocus()&&!ReaderStartup.pending()&&!((List<?>)field(shownHome,"feed")).isEmpty()));if(ready.get())break;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<deadline);
            check(ready.get(),"actual home renders a restored unread list");if(!ready.get())throw new IllegalStateException("Home lacks focus or reader startup is pending");
            List<FeedStore.Row>[] visible=new List[]{null};int[] first={0};test.runOnMainSync(()->{visible[0]=new ArrayList<>((List<FeedStore.Row>)field(shownHome,"feed"));first[0]=((ListView)field(shownHome,"list")).getFirstVisiblePosition();});HotCandidate opened=visible[0].get(first[0]).candidate;
            test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());Document doc=new Document();doc.url=opened.originalUrl;doc.title=opened.title;for(int n=0;n<25;n++)doc.blocks.add(new Block("text","这是已读功能回归的完整合成正文，验证正文实际显示之后的记录，不表示用户兴趣。段落"+n));ArticleVersion.capture(opened.readerItem(),doc);repo.cacheArticle(doc);repo.close();});
            check(!history.seen(opened),"prefetch/cache creation alone does not mark read");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            test.runOnMainSync(()->{ListView list=(ListView)field(shownHome,"list");View wrapper=list.getChildAt(0);((ViewGroup)wrapper).getChildAt(0).performClick();});
            reader=(MainActivity)test.waitForMonitorWithTimeout(monitor,8000);test.removeMonitor(monitor);check(reader!=null,"actual card opens the reader");if(reader==null)throw new IllegalStateException("Reader did not open");
            deadline=SystemClock.elapsedRealtime()+10000;while(!history.seen(opened)&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(40);
            check(history.seen(opened),"visible successful body marks read before twenty-second interest threshold");
            check(store.activeFeedback(System.currentTimeMillis()).length()==0,"short successful reading does not count as liking");
            MainActivity shownReader=reader;test.runOnMainSync(shownReader::onBackPressed);test.waitForIdleSync();
            String[] labels={""};test.runOnMainSync(()->labels[0]=texts(((ListView)field(shownHome,"list")).getChildAt(0)));
            check(labels[0].contains("已读"),"Back immediately rebinds the visible card with read marker");
            test.runOnMainSync(()->check(((List<FeedStore.Row>)field(shownHome,"feed")).get(0).candidate.id.equals(opened.id),"Back preserves current card order instead of jumping the list"));
            DiscoveryController controller=(DiscoveryController)field(home,"controller");long[] oldSnapshot={0};test.runOnMainSync(()->{oldSnapshot[0]=(long)field(controller,"snapshot");controller.refreshRecommendations();});
            deadline=SystemClock.elapsedRealtime()+15000;AtomicBoolean refreshed=new AtomicBoolean();
            do{test.runOnMainSync(()->refreshed.set((long)field(controller,"snapshot")!=oldSnapshot[0]&&!(boolean)field(controller,"explicitRefreshRunning")));if(refreshed.get())break;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<deadline);
            check(refreshed.get(),"actual home refresh completes with the new batch");
            test.runOnMainSync(()->check(((List<FeedStore.Row>)field(shownHome,"feed")).stream().noneMatch(r->history.seen(r.candidate)),"actual refreshed cards contain no read original"));
            // Isolate repeated store refresh from optional asynchronous interest work.
            test.runOnMainSync(controller::stop);Thread.sleep(150);
            List<FeedStore.Row> currentRows=CandidateOrder.arrange(store.candidates(5000,0),store.activeFeedback(System.currentTimeMillis()),false);long current=store.commitRanking(store.version("sources"),store.profile(System.currentTimeMillis()).getLong("version"),System.currentTimeMillis(),"cold","all",currentRows);store.activateSnapshot(current);
            for(int round=0;round<12;round++){long id=round%2==0?store.presentationSnapshot(store.activeSnapshot(),"all",rows,System.currentTimeMillis()):store.refreshSnapshot(store.activeSnapshot(),"all",System.currentTimeMillis());if(id<0)throw new IllegalStateException("stale test snapshot");store.activateSnapshot(id);check(all(store,id).stream().noneMatch(r->history.seen(r.candidate)),"refresh "+round+" never returns a read original");}
            for(HotCandidate c:candidates)history.mark(c,System.currentTimeMillis());long empty=store.presentationSnapshot(store.activeSnapshot(),"all",rows,System.currentTimeMillis());check(all(store,empty).isEmpty(),"exhausted unread supply does not refill with read originals");
        }catch(Throwable error){failures++;report.append(android.util.Log.getStackTraceString(error));}
        finally{DiscoveryActivity h=home;MainActivity r=reader;test.runOnMainSync(()->{if(r!=null)r.finish();if(h!=null)h.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);test.getTargetContext().deleteSharedPreferences("article-reads-"+HotCandidate.hash(database));}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
