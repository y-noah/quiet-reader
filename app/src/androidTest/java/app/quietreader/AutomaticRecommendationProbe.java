package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Real controller, SQLite and bundled models; synthetic candidates, no source fetch. */
final class AutomaticRecommendationProbe {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder();
    private int checks,failures;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private final boolean cold;
    AutomaticRecommendationProbe(Instrumentation test){this(test,false);}
    AutomaticRecommendationProbe(Instrumentation test,boolean cold){this.test=test;this.cold=cold;}
    private static Object field(Object value,String name){try{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void shell(String command)throws Exception{
        try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);java.io.InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            byte[] buffer=new byte[1024];while(in.read(buffer)!=-1){}
        }
    }
    private void await(BooleanSupplier condition,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>=end)throw new IllegalStateException("Automatic flow timed out: "+controller.recommendationStatus());Thread.sleep(100);}}
    void run(){
        String database="test-ui-auto-"+UUID.randomUUID()+".db";
        ExecutorService worker=null;
        try{
            if(cold){
                if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Cold probe requires isolated application");
                boolean absent=!new java.io.File(test.getTargetContext().getFilesDir(),"local-models").exists();
                check(absent,"cold flow begins without any installed model directory");
                if(!absent)throw new IllegalStateException("Cold install prerequisite not met; no data is deleted automatically");
            }
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();
            String[] titles={"中国空间站完成新的科学实验","空间站开展生命科学实验的新进展","明星恋情娱乐八卦"};
            for(int n=0;n<titles.length;n++)candidates.add(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/"+(99008000+n),titles[n],"","",false,n+1,null,now));
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){long revision=seed.beginRefresh(Models.Source.ZHIHU);seed.upsertSnapshot(Models.Source.ZHIHU,revision,candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("test_database",database).putExtra("test_skip_fetch",true));
            controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");
            await(()->(boolean)field(controller,"ready")&&((List<?>)field(activity,"feed")).size()==3,8000);
            long[] original={0};ui(()->original[0]=(long)field(controller,"snapshot"));
            check(onUi(()->field(controller,"recommendation")==null),"empty profile leaves candidates readable without starting inference");
            Map<String,Boolean> choices=new LinkedHashMap<>();choices.put(candidates.get(0).id,true);choices.put(candidates.get(2).id,false);
            ui(()->controller.finishInterestSetup(candidates,choices));
            shell("input keyevent 3");
            await(()->!(boolean)field(controller,"foreground"),8000);
            check(onUi(()->!activity.hasWindowFocus()&&!activity.isFinishing()),"system HOME stops the retained discovery Activity");
            worker.submit(()->{}).get(5,TimeUnit.SECONDS);Thread.sleep(1000);
            check(onUi(()->field(controller,"recommendation")==null&&(long)field(controller,"completedProfile")==-1),"backgrounded first-interest completion cannot launch or publish ranking");
            long started=SystemClock.elapsedRealtime();
            shell("am start -n "+test.getTargetContext().getPackageName()+"/app.quietreader.DiscoveryActivity -f 0x10020000");
            await(()->(boolean)field(controller,"foreground")&&activity.hasWindowFocus(),8000);
            check(onUi(()->field(activity,"controller")==controller),"returning to the task resumes the original Activity and controller");
            await(()->(long)field(controller,"completedProfile")>=0,60000);
            check(onUi(()->(long)field(controller,"pending")>0),"foreground return automatically finishes local ranking without a manual retry");
            check(onUi(()->(long)field(controller,"snapshot")==original[0]),"automatic ranking preserves the currently displayed snapshot");
            FeedStore store=(FeedStore)field(controller,"store");
            long[] counts=worker.submit(()->{
                long[] result=new long[2];int i=0;
                for(String table:Arrays.asList("embeddings","reranks"))try(android.database.Cursor c=store.getWritableDatabase().rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();result[i++]=c.getLong(0);}return result;
            }).get(5,TimeUnit.SECONDS);
            check(counts[0]>0&&counts[1]>0,"automatic flow executes and caches both embedding and reranker models");
            if(cold)check(new app.quietreader.ai.ModelFiles(test.getTargetContext()).installed(),"automatic recommendation installs bundled models without a manual repair action");
            ui(()->controller.applyPending(true));await(()->(boolean)field(activity,"personalized"),8000);
            check(onUi(()->((List<?>)field(activity,"feed")).size()==3),"applying personalized results retains the candidate list");
            report.append("AUTO_FLOW elapsedMs=").append(SystemClock.elapsedRealtime()-started).append(" embeddings=").append(counts[0]).append(" reranks=").append(counts[1]).append('\n');
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{
            if(activity!=null)ui(activity::finish);test.waitForIdleSync();
            if(worker!=null)try{worker.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle out=new Bundle();out.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
}
