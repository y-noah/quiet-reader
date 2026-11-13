package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.ListView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import static app.quietreader.Models.*;

/** Explicit feedback through the actual controller, backed by the actual SQLite pool. */
final class InterestMembershipProbe {
    private final Instrumentation test;private Activity home;private DiscoveryController controller;private FeedStore store;private ExecutorService serial;
    private final String database="test-ui-interest-membership.db";private int checks,failures;private final StringBuilder report=new StringBuilder();
    InterestMembershipProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+15000;boolean[] ready={false};do{ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<until);throw new IllegalStateException("UI update timeout");}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return new ArrayList<>((List<FeedStore.Row>)field(controller,"allRows"));}
    private long count(List<FeedStore.Row> rows,Source source){return rows.stream().filter(r->r.candidate.source==source).count();}
    private void refresh()throws Exception{long before=(long)field(controller,"snapshot");ui(()->controller.refreshRecommendations());await(()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=before);}
    void run(){
        Context context=test.getTargetContext();context.deleteDatabase(database);
        try{
            if(!context.getPackageName().equals("app.quietreader.topicprobe")||!Build.MODEL.startsWith("sdk_gphone"))throw new IllegalStateException("Isolated emulator required");
            List<HotCandidate> candidates=new ArrayList<>();long now=System.currentTimeMillis();
            for(int n=0;n<300;n++){
                Source source=n%3==0?Source.V2EX:n%3==1?Source.THEPAPER:Source.BBC;
                String host=source==Source.V2EX?"https://www.v2ex.com/t/":source==Source.THEPAPER?"https://www.thepaper.cn/newsDetail_forward_":"https://www.bbc.com/zhongwen/articles/";
                candidates.add(new HotCandidate(source,host+(999970000+n),"兴趣池中文候选 "+n,"","",false,n+1,null,now));
            }
            try(FeedStore seed=new FeedStore(context,database)){
                seed.ensureCandidatePolicy();for(Source source:Source.values())seed.enable(source,List.of(Source.V2EX,Source.THEPAPER,Source.BBC).contains(source));
                for(Source source:List.of(Source.V2EX,Source.THEPAPER,Source.BBC)){List<HotCandidate> one=new ArrayList<>();for(HotCandidate c:candidates)if(c.source==source)one.add(c);seed.upsertSnapshot(source,seed.beginRefresh(source),one,now);}
                seed.finishInterestSetup(List.of(),Map.of(),now);
            }
            home=test.startActivitySync(new Intent(context,DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            controller=(DiscoveryController)field(home,"controller");serial=(ExecutorService)field(controller,"storage");store=(FeedStore)field(controller,"store");
            await(()->(boolean)field(controller,"ready")&&!rows().isEmpty());ui(()->controller.interacting(true));
            List<FeedStore.Row> before=rows();long sourceCount=count(before,Source.V2EX),profile=(long)field(controller,"visibleProfileVersion");
            check(before.size()==75&&sourceCount>=4,"baseline actual interest pool holds "+before.size()+" rows, V2EX="+sourceCount);
            ui(()->controller.platformFeedback(Source.V2EX,false));await(()->(long)field(controller,"visibleProfileVersion")>profile);
            List<FeedStore.Row> reduced=rows();long limit=(long)Math.floor(sourceCount*0.25);
            check(reduced.size()==75&&count(reduced,Source.V2EX)==limit,"actual platform LESS reduces total membership from "+sourceCount+" to "+limit+", not just the first page");
            List<FeedStore.Row> canonical=serial.submit(()->{
                List<FeedStore.Row> all=CandidateOrder.forModels(store.candidates(300,0),store.activeFeedback(System.currentTimeMillis()));all.sort(Comparator.comparing(r->r.eventId));return all;
            }).get(10,TimeUnit.SECONDS);
            check(reduced.stream().filter(r->r.candidate.source==Source.V2EX).map(r->r.candidate.id).toList().equals(canonical.stream().filter(r->r.candidate.source==Source.V2EX).limit(limit).map(r->r.candidate.id).toList()),"platform LESS retains the platform's highest-ranked candidates in their original order");
            ListView list=(ListView)field(home,"list");check(list.getCount()==15,"actual homepage contains fifteen cards and no persistent exhaustion footer");
            FeedStore.Row disliked=reduced.stream().filter(r->r.candidate.source!=Source.V2EX).findFirst().get();long previous=(long)field(controller,"visibleProfileVersion");
            ui(()->controller.feedback(disliked,"LESS"));await(()->(long)field(controller,"visibleProfileVersion")>previous);
            check(rows().stream().noneMatch(r->r.entries.stream().anyMatch(c->c.id.equals(disliked.candidate.id))),"actual content LESS removes the matched article from the whole pool immediately");
            ArticleReadHistory history=new ArticleReadHistory(context,database);HotCandidate read=rows().get(0).candidate;history.mark(read,System.currentTimeMillis());
            ui(()->{controller.stop();controller.start();controller.interacting(true);});
            check(rows().stream().noneMatch(r->r.entries.stream().anyMatch(c->c.id.equals(read.id))),"read return immediately removes the article from current and future rows");
            for(int round=0;round<7;round++){refresh();check(count(rows(),Source.V2EX)<=limit&&rows().stream().noneMatch(r->r.entries.stream().anyMatch(c->c.id.equals(read.id)||c.id.equals(disliked.candidate.id))),"whole-pool source budget, read and content exclusions survive refresh/cycle "+round);}
            // Old read facts and large reading histories must never become new recommendations.
            HotCandidate ancient=candidates.get(299);history.mark(ancient,System.currentTimeMillis()-181L*86400000);
            check(history.seen(ancient),"read facts remain effective past the previous 180-day expiry");
            SharedPreferences prefs=context.getSharedPreferences("article-reads-"+HotCandidate.hash(database),0);SharedPreferences.Editor edit=prefs.edit();for(int n=0;n<5005;n++)edit.putLong("test-history-"+n,now);edit.commit();history.mark(candidates.get(298),now);
            check(history.seen(ancient)&&prefs.getLong("test-history-0",0)>0,"recording another read never evicts older facts beyond 5000 entries");
            FeedStore.Row grouped=new FeedStore.Row("read-group",read,"",List.of(read,candidates.get(297)));
            check(history.unread(List.of(grouped)).isEmpty(),"an already-read grouped event cannot return under another platform representative");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(home!=null)home.finish();});if(serial!=null)try{serial.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException ignored){}context.deleteDatabase(database);context.getSharedPreferences("article-reads-"+HotCandidate.hash(database),0).edit().clear().commit();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
