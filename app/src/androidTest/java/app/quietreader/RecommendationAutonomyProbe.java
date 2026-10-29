package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.ai.LocalRecommendation;
import app.quietreader.discovery.*;
import org.json.*;
import java.lang.reflect.Field;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Ordinary foreground scheduling with real models and synthetic cached prose.
 * No explicit recommendation request, source refresh, scores or timers injected. */
final class RecommendationAutonomyProbe {
    private final boolean interruptInBackground,controlledYields;
    private final Instrumentation test;private DiscoveryActivity home;private DiscoveryController controller;private ExecutorService storage;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final Map<File,byte[]> previousFiles=new LinkedHashMap<>();private final Map<File,Long> inodes=new LinkedHashMap<>();
    private final Set<Object> runs=Collections.newSetFromMap(new IdentityHashMap<>());
    RecommendationAutonomyProbe(Instrumentation test){this(test,false);}
    RecommendationAutonomyProbe(Instrumentation test,boolean background){this(test,background,false);}
    RecommendationAutonomyProbe(Instrumentation test,boolean background,boolean controlledYields){this.test=test;this.interruptInBackground=background;this.controlledYields=controlledYields;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private long now(){return SystemClock.elapsedRealtime();}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private static final class State {long source,profile,pending;boolean foreground;long cacheLeaseRemaining;Object run;RecommendationState status;}
    private State observe(){State s=new State();ui(()->{s.source=(long)field(controller,"completedSource");s.profile=(long)field(controller,"completedProfile");s.pending=(long)field(controller,"pending");s.foreground=(boolean)field(controller,"foreground");s.run=field(controller,"recommendation");s.cacheLeaseRemaining=((ActionPrefetchBudget)field(controller,"prefetchBudget")).remaining(now());s.status=(RecommendationState)field(controller,"recommendationState");});
        if(s.run!=null&&runs.add(s.run))report.append("AUTO_START count=").append(runs.size()).append(" elapsed=").append(now()).append(" cacheLeaseRemaining=").append(s.cacheLeaseRemaining).append('\n');return s;}
    private boolean workerAlive(){List<ActivityManager.RunningAppProcessInfo> p=test.getTargetContext().getSystemService(ActivityManager.class).getRunningAppProcesses();return p!=null&&p.stream().anyMatch(x->x.processName.equals("app.quietreader.discoveryprobe:ranking"));}
    private static String order(FeedStore s,long id){StringBuilder b=new StringBuilder();for(FeedStore.Row r:s.readSnapshot(id,200,0))b.append(r.eventId).append('\n');return b.toString();}
    private void seedBodies(List<HotCandidate> candidates)throws Exception{
        ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());String ns=disk.namespace();long at=System.currentTimeMillis();
        for(HotCandidate c:candidates){String[] session={null};ui(()->session[0]=ArticleDiskCache.session(c.originalUrl));File file=new File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(ns+"\n"+session[0]+"\n"+c.originalUrl)+".json");
            previousFiles.put(file,file.exists()?java.nio.file.Files.readAllBytes(file.toPath()):null);
            Document d=new Document();d.url=c.originalUrl;d.title=c.title;d.blocks.add(new Block("text","这是一段为自动调度测试预置的合成中文正文，用于验证主动预读和本地推荐的协作，不代表真实新闻或个人兴趣判断。".repeat(14)));ArticleVersion.capture(c.readerItem(),d);
            if(TiebaTopic.isTopic(c.originalUrl)){
                String post="https://tieba.baidu.com/p/"+(99009000000L+candidates.indexOf(c));String[] postSession={null};ui(()->postSession[0]=ArticleDiskCache.session(post));
                d.topic=new TiebaTopic.Resolved(c.originalUrl,post,postSession[0],at,List.of(new Item(Source.TIEBA,"合成缓存对应帖（仅测试）",post,"")));
            }
            disk.write(d,ns,session[0],at);
            ArticleDiskCache.Hit hit=disk.read(c.originalUrl,ns,session[0],false,System.currentTimeMillis());
            if(hit==null||!ArticleVersion.matches(c.readerItem(),hit.document))throw new IllegalStateException("Seed body does not satisfy current cache identity: "+c.source);
            if(!file.isFile())throw new IllegalStateException("Synthetic cache write failed");inodes.put(file,android.system.Os.stat(file.getPath()).st_ino);
        }
        check(inodes.size()==100,"all100 seeded synthetic bodies pass actual disk/session/metadata/topic validation before launch");
    }
    private long count(FeedStore store,String table){try(android.database.Cursor c=store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
    private void backgroundAndReturn(String database)throws Exception{
        long left=now();test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME);
        long limit=now()+5000;while(observe().foreground&&now()<limit)Thread.sleep(25);
        check(!observe().foreground,"actual HOME stops the foreground controller during real model work");
        limit=now()+5000;while(workerAlive()&&now()<limit)Thread.sleep(25);
        check(!workerAlive(),"actual HOME retires the in-flight model process");
        int starts=runs.size();long quiet=now();boolean quietModels=true;
        while(now()-quiet<20000){State s=observe();if(s.foreground||s.run!=null||workerAlive())quietModels=false;Thread.sleep(100);}
        check(quietModels&&runs.size()==starts,"background stays free of model work beyond the normal15-second scheduling interval");
        report.append("AUTO_BACKGROUND elapsedMs=").append(now()-left).append(" observedQuietMs=").append(now()-quiet).append(" startsBefore=").append(starts).append(" startsAfter=").append(runs.size()).append('\n');
        ui(()->test.getTargetContext().startActivity(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).putExtra("test_database",database)));
        limit=now()+10000;while(!observe().foreground&&now()<limit)Thread.sleep(25);
        check(observe().foreground&&!home.isFinishing(),"returning to the retained home resumes ordinary foreground scheduling");
    }
    void run(){String database="test-ui-autonomy-"+UUID.randomUUID()+".db";
        try{
            if(!"app.quietreader.discoveryprobe".equals(test.getTargetContext().getPackageName()))throw new IllegalStateException("Isolated target required");
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("energy-replay-100-v2.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            List<HotCandidate> all=new ArrayList<>();Map<Source,List<HotCandidate>> pools=new EnumMap<>(Source.class);
            JSONArray input=fixture.getJSONArray("candidates");for(int n=0;n<input.length();n++){HotCandidate c=HotCandidate.fromJson(input.getJSONObject(n));all.add(c);pools.computeIfAbsent(c.source,k->new ArrayList<>()).add(c);}
            long at=System.currentTimeMillis();
            try(FeedStore s=new FeedStore(test.getTargetContext(),database)){
                s.getWritableDatabase().execSQL("UPDATE sources SET enabled=0");
                for(Map.Entry<Source,List<HotCandidate>> p:pools.entrySet()){s.getWritableDatabase().execSQL("UPDATE sources SET enabled=1 WHERE id=?",new Object[]{p.getKey().name()});s.upsertSnapshot(p.getKey(),s.beginRefresh(p.getKey()),p.getValue(),at);}
                JSONArray profile=fixture.getJSONArray("developerProfile");for(int n=0;n<profile.length();n++){JSONObject f=profile.getJSONObject(n);s.feedback(f.getString("id"),f.getString("action"),"sample",f.getString("id"),at,null,f.getString("sample"),null);}
            }
            seedBodies(all);
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database));
            controller=(DiscoveryController)field(home,"controller");storage=(ExecutorService)field(controller,"storage");
            long deadline=now()+25000;boolean ready=false;while(now()<deadline){boolean[] v={false};ui(()->v[0]=(boolean)field(controller,"ready")&&!ReaderStartup.pending()&&!ReaderStartup.failed());if(v[0]){ready=true;break;}Thread.sleep(30);}if(!ready)throw new IllegalStateException("Home initialization timeout");
            check((boolean)field(controller,"autoRefresh"),"ordinary foreground scheduling and active prefetch are enabled");
            try(FeedStore s=new FeedStore(test.getTargetContext(),database)){
                long active=s.activeSnapshot();String initial=order(s,active);check(s.readSnapshot(active,200,0).size()==100,"ordinary home starts with100 distinct public-title events");
                long start=now();deadline=start+120000;State latest=null;boolean complete=false,backgroundDone=false;long progressBaseline=0;Object progressRun=null;List<LocalRecommendation> yieldedRuns=new ArrayList<>();boolean resumedAfterCacheExpiry=false;
                while(now()<deadline){latest=observe();long source=s.version("sources"),profile=s.profile(System.currentTimeMillis()).getLong("version");
                    if(controlledYields&&latest.run!=null){
                        if(yieldedRuns.size()==2&&runs.size()>=3&&latest.cacheLeaseRemaining==0)resumedAfterCacheExpiry=true;
                        long persisted=count(s,"embeddings");
                        if(progressRun!=latest.run){progressRun=latest.run;progressBaseline=persisted;}
                        if(yieldedRuns.size()<2&&persisted>progressBaseline&&!yieldedRuns.contains(latest.run)){
                            LocalRecommendation target=(LocalRecommendation)latest.run;boolean[] applied={false};
                            ui(()->{if(field(controller,"recommendation")!=target)return;try{Field f=LocalRecommendation.class.getDeclaredField("deadline");f.setAccessible(true);f.setLong(target,now()-1);applied[0]=true;}catch(Exception e){throw new IllegalStateException(e);}});
                            if(applied[0]){yieldedRuns.add(target);report.append("CONTROLLED_BUDGET_YIELD savedVectors=").append(persisted).append(" window=").append(yieldedRuns.size()).append("; test-only expiry after actual persisted inference, not natural timing evidence\n");}
                        }
                    }
                    if(interruptInBackground&&!backgroundDone&&latest.run!=null&&count(s,"embeddings")>0){
                        check(true,"an actual embedding has been persisted before the HOME interruption");backgroundAndReturn(database);backgroundDone=true;continue;
                    }
                    if(latest.source==source&&latest.profile==profile&&latest.status.phase==RecommendationState.Phase.READY&&latest.run==null){complete=true;break;}Thread.sleep(100);}
                if(controlledYields){
                    check(yieldedRuns.size()==2&&yieldedRuns.stream().allMatch(LocalRecommendation::canContinueAutomatically),"two controlled normal-budget expiries with real newly persisted vectors request continuation");
                    check(resumedAfterCacheExpiry&&runs.size()>=3,"a third ordinary model window starts after cache lease expiry without manual recommendation or renewed caching");
                }
                if(interruptInBackground)check(backgroundDone,"the real model interruption and return path was exercised");
                check(complete,"no manual recommendation action is needed to complete the current source/profile recommendation");
                report.append("AUTO_RESULT elapsedMs=").append(now()-start).append(" starts=").append(runs.size()).append(" source=").append(s.version("sources")).append(" profile=").append(s.version("profile")).append(" phase=").append(latest==null?"none":latest.status.phase).append(" detail=").append(latest==null?"":latest.status.detail.replace('\n',' ')).append('\n');
                if(!complete)throw new IllegalStateException("Automatic foreground work did not fully complete in120 seconds");
                check(s.activeSnapshot()==active&&initial.equals(order(s,active)),"automatic preparation never inserts or reorders the active feed");
                long prepared;try(android.database.Cursor c=s.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM candidates WHERE json_extract(payload,'$.bodyLanguageAt')>0",null)){c.moveToFirst();prepared=c.getLong(0);}check(prepared>0,"ordinary prefetch publishes actual versioned synthetic body evidence without clicks");
                long retire=now()+5000;while(workerAlive()&&now()<retire)Thread.sleep(50);check(!workerAlive(),"completed foreground work releases the actual model process");
                int starts=runs.size();long quiet=now();boolean quietModels=true;while(now()-quiet<45000){State v=observe();if(v.run!=null||workerAlive())quietModels=false;Thread.sleep(100);}
                check(quietModels&&runs.size()==starts,"45 seconds of unchanged foreground browsing starts no new model work");
                check(s.activeSnapshot()==active&&initial.equals(order(s,active)),"quiet foreground period retains the original full feed snapshot");
                int unchanged=0;for(Map.Entry<File,Long> e:inodes.entrySet())if(e.getKey().isFile()&&android.system.Os.stat(e.getKey().getPath()).st_ino==e.getValue())unchanged++;
                check(unchanged==100,"automatic prefetch/model cooperation does not rewrite100 valid body files");report.append("AUTO_QUIET elapsedMs=").append(now()-quiet).append(" startsBefore=").append(starts).append(" startsAfter=").append(runs.size()).append(" preparedBodies=").append(prepared).append(" unchangedBodies=").append(unchanged).append('\n');
            }
        }catch(Throwable e){check(false,android.util.Log.getStackTraceString(e));}
        finally{
            ui(()->{if(home!=null)home.finish();});test.waitForIdleSync();
            try{if(storage!=null)storage.awaitTermination(10,TimeUnit.SECONDS);for(Class<?> c:List.of(ArticleDiskCache.class,ReadingLanguageRecorder.class)){Field f=c.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(10,TimeUnit.SECONDS);}for(Map.Entry<File,byte[]> e:previousFiles.entrySet()){if(e.getValue()==null)e.getKey().delete();else java.nio.file.Files.write(e.getKey().toPath(),e.getValue());}}catch(Exception e){check(false,"test cleanup: "+e.getClass().getSimpleName());}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle b=new Bundle();b.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);
    }
}
