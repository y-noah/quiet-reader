package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;
import static app.quietreader.ArticleProgressEvidence.*;

/** Real pending snapshot/controller/UI with explicitly seeded exposure/body facts.
 * Not a live news or semantic-quality oracle. No private APIs used in shipping UI. */
final class ArticleFollowupProbe {
    private final Instrumentation test;private DiscoveryActivity home;private DiscoveryController controller;private ExecutorService serial;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    ArticleFollowupProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(Object owner,String name){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void set(Object owner,String name,Object value){try{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){failure[0]=e;}});if(failure[0]!=null)throw new IllegalStateException(failure[0]);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(stage);Thread.sleep(20);}}
    private void publish(long id){set(controller,"pending",id);try{Method state=DiscoveryController.class.getDeclaredMethod("state");state.setAccessible(true);state.invoke(controller);}catch(Exception e){throw new IllegalStateException(e);}}
    private Set<Long> hashes(int from,int to){Set<Long> result=new HashSet<>();for(long n=from;n<to;n++)result.add(n);return result;}
    @SuppressWarnings("unchecked") void run(){String database="test-ui-followup-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();List<HotCandidate> items=new ArrayList<>();for(int n=0;n<30;n++)items.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99700000+n),"后续报道受控中文话题"+n,"","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==Source.THEPAPER);seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),items,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(home,"controller");
            await("actual initial home",()->(boolean)field(controller,"ready")&&((List<?>)field(home,"feed")).size()>2);
            ui(()->{controller.interacting(true);set(home,"exposureForeground",false);}); // Exposure timing is separately tested; these are seeded facts.
            serial=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");List<FeedStore.Row> original=new ArrayList<>((List<FeedStore.Row>)field(home,"feed"));long visible=(long)field(controller,"snapshot");
            FeedStore.Row first=original.get(0);HotCandidate previous=first.candidate;
            HotCandidate followup=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_99799999","同一事件的明确后续报道","","",false,1,null,now);
            Map<String,ArticleProgressBinding.Scope> scopes=new HashMap<>();ui(()->{scopes.put(previous.id,ArticleProgressBinding.scope(home,previous));scopes.put(followup.id,ArticleProgressBinding.scope(home,followup));});
            List<FeedStore.Row> pendingRows=new ArrayList<>();FeedStore.Row group=new FeedStore.Row(first.eventId,followup,"",List.of(previous,followup),first.interestBand);pendingRows.add(group);pendingRows.addAll(original.subList(1,original.size()));
            long pending=serial.submit(()->{
                long time=System.currentTimeMillis();List<HotCandidate> supply=new ArrayList<>(items);supply.add(followup);store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),supply,time);
                store.recordExposed(visible,List.of(first.eventId),time-5000);
                ArticleProgressHistory history=new ArticleProgressHistory(store.getWritableDatabase());history.observe(new Snapshot(previous.id,scopes.get(previous.id).key,time-6000,time-8000,hashes(0,300),Set.of()),time);history.presented(previous.id,scopes.get(previous.id).key,time-5000,time);
                history.observe(new Snapshot(followup.id,scopes.get(followup.id).key,time-1000,time-2000,hashes(0,428),hashes(300,428)),time);
                return store.commitRanking(store.version("sources"),store.profile(time).getLong("version"),time,"personal","all",pendingRows);
            }).get(8,TimeUnit.SECONDS);
            ArticleProgressPreparation prepared=serial.submit(()->ArticleProgressPreparation.read(store,pending,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            check(prepared.followups.size()==1&&prepared.candidates.keySet().equals(scopes.keySet()),"requested pending batch includes the new representative and its displayed baseline in bounded scope preparation");
            check(serial.submit(()->ArticleProgressPreparation.read(store,visible,System.currentTimeMillis()).followups.isEmpty()).get(5,TimeUnit.SECONDS),"old visible snapshot cannot supply a follow-up it does not contain");
            ArticleFollowupEvidence.Proof proof=prepared.followups.get(0);long cutoff=serial.submit(()->{FeedRefreshOrder.Recent recent=store.recentExposure(List.of(group),System.currentTimeMillis()).get(group.eventId);return Math.max(recent.shownAt,recent.readAt);}).get(5,TimeUnit.SECONDS);
            check(serial.submit(()->proof.current(store.getWritableDatabase(),group,scopes,cutoff)>0).get(5,TimeUnit.SECONDS),"same accepted event and both current scopes admit an explicit persisted follow-up");
            Map<String,ArticleProgressBinding.Scope> missing=new HashMap<>(scopes);missing.remove(previous.id);
            check(serial.submit(()->proof.current(store.getWritableDatabase(),group,missing,cutoff)==0).get(5,TimeUnit.SECONDS),"a missing baseline account scope cannot be transferred from the new article");
            FeedStore.Row unrelated=new FeedStore.Row("another-event",followup,"",group.entries,first.interestBand),oldRepresentative=new FeedStore.Row(first.eventId,previous,"",group.entries,first.interestBand);
            check(!proof.belongs(unrelated)&&!proof.belongs(oldRepresentative),"proof cannot cross accepted event boundaries or promote a card that still opens the old article");
            ui(()->{publish(pending);((View)field(home,"updates")).performClick();});await("accepted pending follow-up",()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=visible);
            long accepted=(long)field(controller,"snapshot");check(onUi(()->((List<FeedStore.Row>)field(home,"feed")).get(0).candidate.id.equals(followup.id))&&serial.submit(store::activeSnapshot).get(5,TimeUnit.SECONDS)==accepted,"real dot/controller promotes the newly pending follow-up and opens its representative rather than the cooled old article");
            check(serial.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==0).get(5,TimeUnit.SECONDS),"follow-up admission creates no interest feedback");
            android.content.SharedPreferences prefs=home.getSharedPreferences("article-cache",0);String namespace=prefs.getString("namespace","initial");
            try{prefs.edit().putString("namespace","followup-account-"+UUID.randomUUID()).commit();check(serial.submit(()->proof.current(store.getWritableDatabase(),group,scopes,cutoff)==0).get(5,TimeUnit.SECONDS),"account/cache namespace change invalidates both sides before publication");}finally{prefs.edit().putString("namespace",namespace).commit();}
            check(serial.submit(()->proof.current(store.getWritableDatabase(),group,scopes,proof.at)==0).get(5,TimeUnit.SECONDS),"whole-event later exposure or reading consumes an older follow-up proof");
            serial.submit(()->{long time=System.currentTimeMillis();new ArticleProgressHistory(store.getWritableDatabase()).observe(new Snapshot(followup.id,scopes.get(followup.id).key,time,time-1,hashes(0,300),Set.of()),time);return null;}).get(5,TimeUnit.SECONDS);
            check(serial.submit(()->proof.current(store.getWritableDatabase(),group,scopes,cutoff)==0).get(5,TimeUnit.SECONDS),"body withdrawn after preparation invalidates its previously prepared proof");
            check(serial.submit(()->ArticleProgressPreparation.read(store,accepted,System.currentTimeMillis()).followups.isEmpty()).get(5,TimeUnit.SECONDS),"repost without substantial marked additions does not schedule account capture");
            serial.submit(()->{store.recordExposed(accepted,List.of(group.eventId),System.currentTimeMillis());return null;}).get(5,TimeUnit.SECONDS);ui(()->{publish(accepted);((View)field(home,"updates")).performClick();});await("consumed follow-up cooled",()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=accepted);
            check(onUi(()->((List<FeedStore.Row>)field(home,"feed")).get(0).candidate.id.equals(original.get(1).candidate.id)),"after actual event exposure the next refresh prefers another unseen same-quality item");
            serial.submit(()->{bounds(store);return null;}).get(10,TimeUnit.SECONDS);
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(home!=null)home.finish();});test.waitForIdleSync();if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void bounds(FeedStore store){
        long now=System.currentTimeMillis();ArticleProgressHistory history=new ArticleProgressHistory(store.getWritableDatabase());String scope=HotCandidate.hash("bounded-followup");List<FeedStore.Row> rows=new ArrayList<>();Set<String> eligible=new HashSet<>();
        for(int n=0;n<20;n++){
            HotCandidate before=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99600000+n*2),"边界基线"+n,"","",false,n+1,null,now),after=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99600001+n*2),"边界后续"+n,"","",false,n+1,null,now);
            history.observe(new Snapshot(before.id,scope,now-6000,now-8000,hashes(0,300),Set.of()),now);history.presented(before.id,scope,now-5000,now);history.observe(new Snapshot(after.id,scope,now-1000,now-2000,hashes(0,428),hashes(300,428)),now);
            rows.add(new FeedStore.Row("bounded-"+n,after,"",List.of(before,after),0));eligible.add(before.id);eligible.add(after.id);
        }
        ArticleFollowupEvidence bounded=new ArticleFollowupEvidence(store.getWritableDatabase(),now);check(bounded.find(rows,eligible).size()==16&&bounded.comparedPairs()==16,"one explicit action compares at most sixteen eligible follow-up pairs");
        for(FeedStore.Row row:rows){HotCandidate before=row.entries.get(0),after=row.candidate;history.observe(new Snapshot(before.id,scope,now-3000,now-4000,hashes(0,11000),Set.of()),now);history.presented(before.id,scope,now-2500,now);history.observe(new Snapshot(after.id,scope,now,now-1,hashes(0,11500),hashes(11000,11500)),now);}
        ArticleFollowupEvidence bytes=new ArticleFollowupEvidence(store.getWritableDatabase(),now);List<ArticleFollowupEvidence.Proof> admitted=bytes.find(rows,eligible);check(bytes.decodedBytes()>ArticleFollowupEvidence.MAX_DECODE_BYTES/2&&bytes.decodedBytes()<=ArticleFollowupEvidence.MAX_DECODE_BYTES&&admitted.size()<16,"large persisted bodies exhaust the bounded budget before sixteen pairs and obey the one-MiB decode ceiling");
    }
}
