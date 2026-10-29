package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.database.Cursor;
import android.widget.TextView;
import app.quietreader.ai.LocalRecommendation;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real model completion followed by an isolated SQLite fault or concurrent feedback write. */
final class RecommendationCommitProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private DiscoveryController controller;
    private int checks,failures;private final StringBuilder report=new StringBuilder("SYNTHETIC preferences/candidates; real models, controller and SQLite commit\n");
    RecommendationCommitProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,long timeout,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Commit deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private long count(FeedStore store,String table){try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){return cursor.moveToFirst()?cursor.getLong(0):0;}}
    void run(){
        for(String scenario:new String[]{"storage-failure","profile-race"}){
            String database="test-ui-commit-"+UUID.randomUUID()+".db";ExecutorService worker=null;
            try{
                long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();
                String[] titles={"中国空间站生命科学实验","中国空间站生命科学实验取得新进展","家庭光伏储能系统使用体验","明星恋情娱乐八卦"};
                for(int n=0;n<titles.length;n++)candidates.add(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+(99009100+n),titles[n],"","",false,n+1,null,now));
                try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){seed.upsertSnapshot(Models.Source.ZHIHU,seed.beginRefresh(Models.Source.ZHIHU),candidates,now);seed.feedback("seed","MORE","sample",candidates.get(0).id,now,null,candidates.get(0).modelText());}
                activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
                controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");
                await("initial model work",15000,()->field(controller,"recommendation")!=null);
                ExecutorService serial=worker;FeedStore store=(FeedStore)field(controller,"store");
                Object visible=field(activity,"feed");long snapshot=(long)field(controller,"snapshot");long initialSnapshots=serial.submit(()->count(store,"snapshots")).get(5,TimeUnit.SECONDS);
                CountDownLatch injected=new CountDownLatch(1);Future<?>[] mutation={null};boolean[] computed={false};
                ui(()->{
                    LocalRecommendation running=(LocalRecommendation)field(controller,"recommendation");LocalRecommendation.Listener delegate=(LocalRecommendation.Listener)field(running,"listener");
                    LocalRecommendation.Listener boundary=new LocalRecommendation.Listener(){
                        public void status(String value){delegate.status(value);}
                        public void complete(List<FeedStore.Row> rows,boolean semantic,int encoded,int total,int refined,String status){
                            computed[0]=running.fullyComputed()&&semantic;
                            mutation[0]=serial.submit(()->{
                                if(scenario.equals("storage-failure"))store.getWritableDatabase().execSQL("CREATE TRIGGER qr_commit_fault BEFORE INSERT ON snapshots BEGIN SELECT RAISE(ABORT,'synthetic snapshot write failure'); END");
                                else store.feedback("concurrent-more","MORE","sample",candidates.get(2).id,System.currentTimeMillis(),null,candidates.get(2).modelText(),Models.Source.ZHIHU);
                            });
                            delegate.complete(rows,semantic,encoded,total,refined,status);injected.countDown();
                        }
                    };
                    try{Field callback=LocalRecommendation.class.getDeclaredField("listener");callback.setAccessible(true);callback.set(running,boundary);}catch(Exception error){throw new IllegalStateException(error);}
                });
                check(injected.await(60,TimeUnit.SECONDS),scenario+": real model result reaches the commit boundary");
                if(mutation[0]==null)throw new IllegalStateException("Commit mutation was not installed");mutation[0].get(5,TimeUnit.SECONDS);
                await("commit rejected",8000,()->((RecommendationState)field(controller,"recommendationState")).phase==RecommendationState.Phase.PAUSED);
                check(computed[0],scenario+": injection follows a fully computed semantic result");
                String expected=scenario.equals("storage-failure")?"本机内容暂时无法更新":"来源或偏好已更新";
                check(onUi(()->controller.recommendationStatus().contains(expected)&&field(controller,"recommendation")==null),scenario+": failure clears running work and exposes its actual reason");
                check(onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot&&(long)field(controller,"pending")==0&&!(boolean)field(activity,"personalized")&&"候选顺序".contentEquals(((TextView)field(activity,"sort")).getText())),scenario+": rejected result cannot replace or relabel visible cards or become pending");
                check(serial.submit(()->count(store,"snapshots")==initialSnapshots&&count(store,"embeddings")>0&&store.candidates(100,0).size()==candidates.size()&&store.activeFeedback(System.currentTimeMillis()).length()==(scenario.equals("storage-failure")?1:2)).get(5,TimeUnit.SECONDS),scenario+": transaction leaves no orphan snapshot and preserves candidates, feedback and model caches");
                check(onUi(()->((TextView)field(activity,"subtitle")).getText().toString().contains("暂停")),scenario+": the actual homepage reports paused work");
                if(scenario.equals("storage-failure"))serial.submit(()->store.getWritableDatabase().execSQL("DROP TRIGGER qr_commit_fault")).get(5,TimeUnit.SECONDS);
                ui(controller::requestRecommendation);
                await("recovery pending",60000,()->(long)field(controller,"pending")>0&&(long)field(controller,"completedProfile")>=0);
                long currentProfile=serial.submit(()->store.profile(System.currentTimeMillis()).getLong("version")).get(5,TimeUnit.SECONDS);
                check(onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot&&(long)field(controller,"completedProfile")==currentProfile),scenario+": retry uses the current profile and keeps the old list frozen until acceptance");
                if(scenario.equals("profile-race")){
                    long stalePending=(long)field(controller,"pending");
                    serial.submit(()->store.feedback("acceptance-race","LESS","sample",candidates.get(3).id,System.currentTimeMillis(),null,candidates.get(3).modelText(),Models.Source.ZHIHU)).get(5,TimeUnit.SECONDS);
                    ui(()->((TextView)field(activity,"updates")).performClick());
                    await("stale pending acceptance handled",8000,()->(long)field(controller,"snapshot")!=snapshot||(long)field(controller,"pending")!=stalePending);
                    boolean kept=onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot&&!(boolean)field(activity,"personalized"));
                    check(kept,"profile-race: accepting an old pending result after newer feedback cannot publish the obsolete preference version");
                    if(!kept)continue;
                    long latestProfile=serial.submit(()->store.profile(System.currentTimeMillis()).getLong("version")).get(5,TimeUnit.SECONDS);
                    await("newer profile result pending",60000,()->(long)field(controller,"pending")>0&&(long)field(controller,"completedProfile")==latestProfile);
                    check(onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot),"profile-race: obsolete pending acceptance prepares a fresh result without jumping the visible list");
                    long sourcePending=(long)field(controller,"pending");
                    List<HotCandidate> refreshed=new ArrayList<>(candidates);refreshed.add(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/99009199","家庭储能新技术的实际应用","","",false,5,null,System.currentTimeMillis()));
                    serial.submit(()->store.upsertSnapshot(Models.Source.ZHIHU,store.beginRefresh(Models.Source.ZHIHU),refreshed,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
                    ui(()->((TextView)field(activity,"updates")).performClick());
                    await("stale source acceptance handled",8000,()->(long)field(controller,"snapshot")!=snapshot||(long)field(controller,"pending")!=sourcePending);
                    boolean sourceKept=onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot&&!(boolean)field(activity,"personalized"));
                    check(sourceKept,"source-race: source refresh after preparation also rejects obsolete pending cards");if(!sourceKept)continue;
                    long latestSource=serial.submit(()->store.version("sources")).get(5,TimeUnit.SECONDS);
                    await("newer source result pending",60000,()->(long)field(controller,"pending")>0&&(long)field(controller,"completedSource")==latestSource&&(long)field(controller,"completedProfile")==latestProfile);
                    check(onUi(()->field(activity,"feed")==visible&&(long)field(controller,"snapshot")==snapshot),"source-race: current-source model result remains pending until explicit acceptance");
                }
                ui(()->((TextView)field(activity,"updates")).performClick());await("recovered result accepted",8000,()->(boolean)field(activity,"personalized")&&(long)field(controller,"pending")==0&&(long)field(controller,"snapshot")!=snapshot);
                check(onUi(()->"适合我".contentEquals(((TextView)field(activity,"sort")).getText())),scenario+": explicit update accepts the recovered personal snapshot");
                if(scenario.equals("profile-race"))check(onUi(()->((List<FeedStore.Row>)field(activity,"feed")).stream().flatMap(row->row.entries.stream()).anyMatch(entry->entry.originalUrl.endsWith("/99009199"))),"source-race: accepted fresh cards include the newly collected article");
                if(scenario.equals("profile-race")){
                    ui(()->controller.sort(true));await("chronological snapshot accepted",8000,()->"最新".contentEquals(((TextView)field(activity,"sort")).getText())&&(long)field(controller,"pending")==0);
                    Object chronological=field(activity,"feed");long chronologicalSnapshot=(long)field(controller,"snapshot");
                    ui(()->controller.feedback(((List<FeedStore.Row>)field(activity,"feed")).get(0),"MORE"));await("chronological pending prepared",8000,()->(long)field(controller,"pending")>0);
                    long oldPending=(long)field(controller,"pending");
                    serial.submit(()->store.feedback("chronological-race","LESS","sample",candidates.get(3).id,System.currentTimeMillis(),null,candidates.get(3).modelText(),Models.Source.ZHIHU)).get(5,TimeUnit.SECONDS);
                    ui(()->((TextView)field(activity,"updates")).performClick());await("chronological pending rebuilt",8000,()->(long)field(controller,"snapshot")!=chronologicalSnapshot||((long)field(controller,"pending")>0&&(long)field(controller,"pending")!=oldPending));
                    check(onUi(()->field(activity,"feed")==chronological&&(long)field(controller,"snapshot")==chronologicalSnapshot&&field(controller,"recommendation")==null),"chronological: stale pending result is rebuilt without running models or moving current cards");
                    ui(()->((TextView)field(activity,"updates")).performClick());await("chronological update accepted",8000,()->(long)field(controller,"pending")==0&&(long)field(controller,"snapshot")!=chronologicalSnapshot);
                    check(onUi(()->"最新".contentEquals(((TextView)field(activity,"sort")).getText())&&((TextView)field(activity,"subtitle")).getText().toString().contains("按来源提供")&&((RecommendationState)field(controller,"recommendationState")).phase!=RecommendationState.Phase.PAUSED),"chronological: accepted current result retains its real sort label without a stale paused message");
                }
            }catch(Throwable error){check(false,scenario+": "+error.getClass().getSimpleName()+": "+error.getMessage());}
            finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}activity=null;controller=null;test.getTargetContext().deleteDatabase(database);}
        }
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append('\n').toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
