package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Cached synthetic prose through the real preloader/controller/SQLite snapshot path. */
final class BodyLanguageFlowProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private ExecutorService storage;
    private final StringBuilder report=new StringBuilder();private int checks,failures;
    BodyLanguageFlowProbe(Instrumentation test){this.test=test;}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+8000;while(true){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;if(SystemClock.elapsedRealtime()>until)throw new IllegalStateException("Language flow timeout");Thread.sleep(50);}}
    void run(){String database="test-ui-language-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();
            for(int n=0;n<3;n++)candidates.add(new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/"+(99007600+n),"中文合成话题"+n,"","",false,n+1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){long r=store.beginRefresh(Source.ZHIHU);store.upsertSnapshot(Source.ZHIHU,r,candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            DiscoveryController controller=(DiscoveryController)field(activity,"controller");storage=(ExecutorService)field(controller,"storage");
            await(()->(boolean)field(controller,"ready")&&((List<?>)field(activity,"feed")).size()==3);
            HotCandidate target=candidates.get(0);Document prose=new Document();prose.url=target.originalUrl;prose.title=target.title;
            prose.blocks.add(new Block("text","This report describes scientific observations and discusses their implications for future research. ".repeat(10)));
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(target.readerItem(),prose);repo.cacheArticle(prose);repo.close();((ArticlePreloader)field(controller,"preloader")).offer(Collections.singletonList(target.readerItem()));});
            FeedStore store=(FeedStore)field(controller,"store");long until=SystemClock.elapsedRealtime()+8000;boolean recorded=false;
            while(!recorded&&SystemClock.elapsedRealtime()<until){recorded=storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(target.id)&&c.bodyLanguage==ContentLanguage.Body.NON_CHINESE)).get(3,TimeUnit.SECONDS);if(!recorded)Thread.sleep(50);}
            check(recorded,"real preloader cache hit persists foreign body evidence through controller");
            await(()->(long)field(controller,"pending")>0);
            check(((List<?>)field(activity,"feed")).size()==3,"body evidence keeps the currently displayed feed frozen");
            ui(()->controller.applyPending(true));await(()->((List<?>)field(activity,"feed")).size()==2);
            check(((List<?>)field(activity,"feed")).size()==2,"applying rebuilt feed removes the foreign body candidate");
            check(storage.submit(()->store.candidates(100,0).size()==3).get(3,TimeUnit.SECONDS),"language filtering retains source facts instead of deleting the candidate");
            HotCandidate direct=candidates.get(1);Document directBody=new Document();directBody.url=direct.originalUrl;directBody.title=direct.title;
            directBody.blocks.add(new Block("text","This independent article explains how researchers measure and interpret experimental results. ".repeat(10)));
            ui(()->{Repository repo=new Repository(activity);ArticleVersion.capture(direct.readerItem(),directBody);repo.cacheArticle(directBody);repo.close();});
            Instrumentation.ActivityMonitor readerMonitor=test.addMonitor(MainActivity.class.getName(),null,false);
            ui(()->{try{java.lang.reflect.Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(activity,direct);}catch(Exception e){throw new IllegalStateException(e);}});
            Activity reader=test.waitForMonitorWithTimeout(readerMonitor,5000);test.removeMonitor(readerMonitor);
            check(reader!=null,"direct card opens the real reader without a speculative request");
            if(reader==null)throw new IllegalStateException("Reader absent");
            until=SystemClock.elapsedRealtime()+8000;boolean readRecorded=false;
            while(!readRecorded&&SystemClock.elapsedRealtime()<until){readRecorded=storage.submit(()->store.candidates(100,0).stream().anyMatch(c->c.id.equals(direct.id)&&c.bodyLanguage==ContentLanguage.Body.NON_CHINESE)).get(3,TimeUnit.SECONDS);if(!readRecorded)Thread.sleep(50);}
            check(readRecorded,"ordinary reader persists body language evidence without prefetch");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);await(reader::isFinishing);
            await(()->(long)field(controller,"pending")>0);
            ui(()->controller.applyPending(true));await(()->((List<?>)field(activity,"feed")).size()==1);
            check(((List<?>)field(activity,"feed")).size()==1,"returning from reader rebuilds recommendations with new body evidence");
            ui(()->{Repository repo=new Repository(activity);repo.releaseArticleMemory();repo.close();});
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{if(activity!=null)ui(activity::finish);test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
