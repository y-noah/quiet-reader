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

/** Actual dot/controller/storage, seeded facts; no live network or phone. */
final class RecommendationRefreshProbe {
    private final Instrumentation test;private DiscoveryActivity home;private DiscoveryController controller;private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final boolean discussion;
    RecommendationRefreshProbe(Instrumentation test){this(test,false);}
    RecommendationRefreshProbe(Instrumentation test,boolean discussion){this.test=test;this.discussion=discussion;}
    private String prose(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier value){boolean[] result={false};ui(()->result[0]=value.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(stage);Thread.sleep(20);}}
    private void publish(long id){try{Field f=DiscoveryController.class.getDeclaredField("pending");f.setAccessible(true);f.setLong(controller,id);Method m=DiscoveryController.class.getDeclaredMethod("state");m.setAccessible(true);m.invoke(controller);}catch(Exception error){throw new IllegalStateException(error);}}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void barrier(ExecutorService serial,CountDownLatch release)throws Exception{CountDownLatch entered=new CountDownLatch(1);serial.submit(()->{entered.countDown();try{if(!release.await(12,TimeUnit.SECONDS))throw new IllegalStateException("test barrier expired");}catch(InterruptedException error){Thread.currentThread().interrupt();}});if(!entered.await(3,TimeUnit.SECONDS))throw new IllegalStateException("barrier not entered");}
    @SuppressWarnings("unchecked") void run(){String database="test-ui-refresh-"+UUID.randomUUID()+".db";ExecutorService serial=null;CountDownLatch first=new CountDownLatch(1),second=new CountDownLatch(1),account=new CountDownLatch(1);
        android.content.SharedPreferences preferences=test.getTargetContext().getSharedPreferences("article-cache",0);String originalNamespace=preferences.getString("namespace","initial");
        try{
            Source selected=discussion?Source.V2EX:Source.THEPAPER;
            long now=System.currentTimeMillis();List<HotCandidate> items=new ArrayList<>();for(int n=0;n<70;n++)items.add(new HotCandidate(selected,(discussion?"https://www.v2ex.com/t/":"https://www.thepaper.cn/newsDetail_forward_")+(99910000+n),"刷新候选受控中文话题"+n,"","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())seed.enable(source,source==selected);seed.upsertSnapshot(selected,seed.beginRefresh(selected),items,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database).putExtra("test_skip_fetch",true));controller=(DiscoveryController)field(home,"controller");
            await("initial feed",()->(boolean)field(controller,"ready")&&((List<?>)field(home,"feed")).size()>2);ui(()->controller.interacting(true));
            serial=(ExecutorService)field(controller,"storage");FeedStore store=(FeedStore)field(controller,"store");long original=(long)field(controller,"snapshot");List<FeedStore.Row> originalRows=new ArrayList<>((List<FeedStore.Row>)field(home,"feed"));int seenCount=Math.min(10,originalRows.size()-1);List<String> seen=new ArrayList<>();for(int n=0;n<seenCount;n++)seen.add(originalRows.get(n).eventId);
            long profile=serial.submit(()->{store.recordExposed(original,seen,System.currentTimeMillis());store.recordConsumed(originalRows.get(1).candidate,System.currentTimeMillis());return store.version("profile");}).get(5,TimeUnit.SECONDS);
            barrier(serial,first);long beforeEpoch=(long)field(controller,"explicitRefreshEpoch");Object oldView=field(home,"feed");
            ui(()->{publish(original);long wall=System.nanoTime(),cpu=Debug.threadCpuTimeNanos();((View)field(home,"updates")).performClick();report.append("REFRESH_CLICK rows=").append(originalRows.size()).append(" synchronousWallMs=").append((System.nanoTime()-wall)/1000000.0).append(" synchronousCpuMs=").append((Debug.threadCpuTimeNanos()-cpu)/1000000.0).append('\n');((View)field(home,"updates")).performClick();});
            check(onUi(()->(long)field(controller,"explicitRefreshEpoch")==beforeEpoch+1&&field(home,"feed")==oldView),"rapid real dot clicks coalesce into one request while storage is blocked");
            first.countDown();await("explicit refreshed view",()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=original);
            long refreshed=(long)field(controller,"snapshot");List<FeedStore.Row> view=new ArrayList<>((List<FeedStore.Row>)field(home,"feed"));
            check(view.get(0).eventId.equals(originalRows.get(seenCount).eventId),"real update click brings first eligible unseen candidate to the front");
            check(view.stream().noneMatch(row->row.eventId.equals(originalRows.get(1).eventId))&&serial.submit(()->store.readSnapshot(refreshed,100,0).get(69).eventId.equals(originalRows.get(1).eventId)).get(5,TimeUnit.SECONDS),"qualified read moves to the end of the full pool and stays outside the next 20-row batch");
            check(serial.submit(()->store.version("profile")==profile&&store.activeSnapshot()==refreshed).get(5,TimeUnit.SECONDS),"new view is active in SQLite without creating preference feedback");
            ArticleProgressBinding.Scope[] progressScope={null};ui(()->progressScope[0]=ArticleProgressBinding.scope(home,originalRows.get(0).candidate));
            if(discussion)Thread.sleep(1100); // Source timestamps have second precision; new reply must follow real exposure.
            serial.submit(()->{long time=System.currentTimeMillis(),shown=store.recentExposure(List.of(originalRows.get(0)),time).get(originalRows.get(0).eventId).shownAt;
                Set<Long> oldBody=new HashSet<>(),newBody=new HashSet<>(),marked=new HashSet<>();for(long n=0;n<300;n++)oldBody.add(n);newBody.addAll(oldBody);for(long n=300;n<428;n++){newBody.add(n);marked.add(n);}
                ArticleProgressHistory evidence=new ArticleProgressHistory(store.getWritableDatabase());String identity=originalRows.get(0).candidate.id,scope=progressScope[0].key;
                if(discussion){
                    String url=originalRows.get(0).candidate.originalUrl,main="<div class=topic_content>"+prose(1)+"</div>";
                    Document prior=SourceParser.article(Source.V2EX,main,url);prior.cachedAt=shown-100;
                    String at=java.time.format.DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx").format(java.time.Instant.ofEpochMilli(time/1000*1000).atOffset(java.time.ZoneOffset.ofHours(8)));
                    Document next=SourceParser.article(Source.V2EX,main+"<div class=cell id=r_90001><span class=ago title='"+at+"'>刚刚</span><div class=reply_content>【最新进展】"+prose(2)+"</div></div>",url);next.cachedAt=time;
                    evidence.observe(ArticleProgressEvidence.capture(Source.V2EX,prior,scope,time),time);evidence.presented(identity,scope,shown,time);evidence.observe(ArticleProgressEvidence.capture(Source.V2EX,next,scope,time),time);
                }else{
                    evidence.observe(new ArticleProgressEvidence.Snapshot(identity,scope,shown-100,shown-1000,oldBody,Set.of()),time);evidence.presented(identity,scope,shown,time);
                    evidence.observe(new ArticleProgressEvidence.Snapshot(identity,scope,time-1,time-2,newBody,marked),time);
                }return null;
            }).get(5,TimeUnit.SECONDS);
            ui(()->{publish(refreshed);((View)field(home,"updates")).performClick();});await("real scoped progress refresh",()->!(boolean)field(controller,"explicitRefreshRunning")&&(long)field(controller,"snapshot")!=refreshed);
            long protectedSnapshot=(long)field(controller,"snapshot");check(onUi(()->((List<FeedStore.Row>)field(home,"feed")).get(0).eventId.equals(originalRows.get(0).eventId)),discussion?"actual controller promotes a parsed marked new V2EX reply through scoped worker prefilter":"worker prefilter plus current account scope lets actual controller promote a persisted same-article revision");
            barrier(serial,account);Object accountView=field(home,"feed");ui(()->{publish(original);((View)field(home,"updates")).performClick();});
            preferences.edit().putString("namespace","refresh-account-"+UUID.randomUUID()).commit();account.countDown();await("changed account cancels refresh publication",()->!(boolean)field(controller,"explicitRefreshRunning"));
            check(onUi(()->field(home,"feed")==accountView&&(long)field(controller,"snapshot")==protectedSnapshot)&&serial.submit(store::activeSnapshot).get(5,TimeUnit.SECONDS)==protectedSnapshot,"account/cache namespace change during explicit refresh rejects late view activation");
            preferences.edit().putString("namespace",originalNamespace).commit();
            barrier(serial,second);Object acceptedView=field(home,"feed");ui(()->{publish(original);((View)field(home,"updates")).performClick();controller.stop();});second.countDown();serial.submit(()->{}).get(8,TimeUnit.SECONDS);test.waitForIdleSync();
            check(onUi(()->field(home,"feed")==acceptedView&&(long)field(controller,"snapshot")==protectedSnapshot&&!(boolean)field(controller,"explicitRefreshRunning")),"backgrounding cancels late refresh publication and clears the in-flight request");
            check(serial.submit(store::activeSnapshot).get(5,TimeUnit.SECONDS)==protectedSnapshot,"late background result cannot activate its prepared snapshot");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{first.countDown();second.countDown();account.countDown();preferences.edit().putString("namespace",originalNamespace).commit();ui(()->{if(home!=null)home.finish();});test.waitForIdleSync();if(serial!=null)try{serial.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
