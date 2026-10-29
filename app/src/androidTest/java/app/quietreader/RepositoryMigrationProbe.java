package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** A deliberately blocked real preference migration must never block UI construction. */
final class RepositoryMigrationProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    RepositoryMigrationProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static int workers(){int n=0;for(Thread t:Thread.getAllStackTraces().keySet())if(t.isAlive()&&"source-cache-migration".equals(t.getName()))n++;return n;}
    void run(){
        Context base=test.getTargetContext();String prefix="test-migration-"+UUID.randomUUID()+"-";
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger uiReads=new AtomicInteger(),migrationReads=new AtomicInteger();Repository[] repos=new Repository[2];
        String[] names={"boards","video-filter","source-session","article-cache","reading-history","settings"};
        Context isolated=new ContextWrapper(base){
            @Override public Context getApplicationContext(){return this;}
            @Override public SharedPreferences getSharedPreferences(String name,int mode){
                if(Looper.myLooper()==Looper.getMainLooper()){uiReads.incrementAndGet();throw new IllegalStateException("UI must not load migration preferences");}
                migrationReads.incrementAndGet();entered.countDown();
                try{if(!release.await(8,TimeUnit.SECONDS))throw new IllegalStateException("Migration gate timed out");}
                catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
                return base.getSharedPreferences(prefix+name,mode);
            }
        };
        try{
            if(!"app.quietreader.discoveryprobe".equals(base.getPackageName()))throw new IllegalStateException("Isolated target only");
            // Only this probe's reserved fixtures, including an interrupted negative control.
            java.io.File[] old=new java.io.File(base.getApplicationInfo().dataDir,"shared_prefs").listFiles();
            if(old!=null)for(java.io.File file:old)if(file.getName().matches("test-migration-[a-f0-9-]+-(boards|video-filter|source-session|article-cache|reading-history|settings)\\.xml")){
                if(!base.deleteSharedPreferences(file.getName().substring(0,file.getName().length()-4)))throw new IllegalStateException("Could not remove interrupted probe fixture");
            }
            SharedPreferences boards=base.getSharedPreferences(prefix+"boards",0),videos=base.getSharedPreferences(prefix+"video-filter",0),session=base.getSharedPreferences(prefix+"source-session",0);
            SharedPreferences.Editor b=boards.edit().putString("AGGREGATE","retired").putLong("AGGREGATE_time",123).putString("DOUBAN_endpoint","retired"),v=videos.edit().putLong("DOUBAN|retired",123);
            for(Models.Source source:Models.Source.values())if(source.readable()){
                b.putString(source.name(),"supported "+source.name()).putLong(source.name()+"_time",456).putString(source.name()+"_endpoint",source.endpoint);
                v.putLong(source.name()+"|supported",456);
            }
            if(!b.commit()||!v.commit()||!session.edit().putString("tieba-desktop","legacy").putString("zhihu","session preserved").commit())throw new IllegalStateException("Could not seed migration fixture");
            Map<String,Map<String,?>> protectedData=new HashMap<>();for(String name:new String[]{"article-cache","reading-history","settings"}){
                SharedPreferences prefs=base.getSharedPreferences(prefix+name,0);if(!prefs.edit().putString("sentinel","preserve "+name).commit())throw new IllegalStateException("Could not seed protected fixture");protectedData.put(name,new HashMap<>(prefs.getAll()));
            }
            RuntimeException[] creationFailure={null};
            test.runOnMainSync(()->{try{repos[0]=new Repository(isolated);}catch(RuntimeException error){creationFailure[0]=error;}});
            if(creationFailure[0]!=null)throw new IllegalStateException("UI repository construction attempted migration preference IO",creationFailure[0]);
            check(entered.await(3,TimeUnit.SECONDS),"finite migration enters blocked preference load on its worker");
            check(uiReads.get()==0,"repository UI constructor performs no migration preference reads");
            CountDownLatch responsive=new CountDownLatch(1);new Handler(Looper.getMainLooper()).post(responsive::countDown);
            check(responsive.await(1,TimeUnit.SECONDS)&&release.getCount()==1,"main queue remains responsive while migration preference load is blocked");
            test.runOnMainSync(()->{repos[1]=new Repository(isolated);repos[0].close();repos[1].close();});
            check(workers()==1&&migrationReads.get()==1,"repeated repositories share one finite process migration, even when repositories close");
            release.countDown();long until=SystemClock.elapsedRealtime()+5000;while(workers()!=0&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);
            check(workers()==0,"migration worker exits without an idle pool or retry loop");
            check(!boards.contains("AGGREGATE")&&!boards.contains("AGGREGATE_time")&&!boards.contains("DOUBAN_endpoint")&&!videos.contains("DOUBAN|retired"),"unsupported board fields and video markers are removed");
            boolean supported=true;for(Models.Source source:Models.Source.values())if(source.readable())supported&=("supported "+source.name()).equals(boards.getString(source.name(),""))&&boards.getLong(source.name()+"_time",0)==456&&source.endpoint.equals(boards.getString(source.name()+"_endpoint",""))&&videos.getLong(source.name()+"|supported",0)==456;
            check(supported,"all currently supported platform cache fields and markers are preserved");
            check(!session.contains("tieba-desktop")&&"session preserved".equals(session.getString("zhihu","")),"only obsolete desktop session field is removed");
            boolean preserved=true;for(Map.Entry<String,Map<String,?>> e:protectedData.entrySet())preserved&=e.getValue().equals(base.getSharedPreferences(prefix+e.getKey(),0).getAll());
            check(preserved,"independent article namespace, history and settings preference fixtures remain unchanged");
            check(uiReads.get()==0&&migrationReads.get()==3,"migration reads exactly its three preference scopes off the main thread");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{
            release.countDown();test.runOnMainSync(()->{for(Repository repo:repos)if(repo!=null)repo.close();});
            long until=SystemClock.elapsedRealtime()+5000;while(workers()!=0&&SystemClock.elapsedRealtime()<until)try{Thread.sleep(20);}catch(InterruptedException error){Thread.currentThread().interrupt();break;}
            boolean clean=workers()==0;for(String name:names)clean&=base.deleteSharedPreferences(prefix+name);check(clean,"migration terminates and isolated preference fixtures are removed");
        }
        report.append("scope=blocked migration IO and real isolated SharedPreferences, not real accounts or full startup latency\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
