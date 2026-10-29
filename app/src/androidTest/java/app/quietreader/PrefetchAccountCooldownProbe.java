package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.CookieManager;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real CookieManager plus ordinary preloader; controlled failures, no source requests or login. */
final class PrefetchAccountCooldownProbe {
    private final Instrumentation test;private DiscoveryActivity home;private ArticlePreloader preloader;private ExecutorService storage;
    private final List<Repository.Result<Document>> calls=new ArrayList<>();
    private int checks,failures;private String url,path;private final StringBuilder report=new StringBuilder();
    PrefetchAccountCooldownProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name)throws Exception{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private void ui(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(BooleanSupplier condition)throws Exception{long until=SystemClock.elapsedRealtime()+3500;do{if(onUi(condition))return true;Thread.sleep(25);}while(SystemClock.elapsedRealtime()<until);return onUi(condition);}
    private void cookie(String name,String value)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] accepted={false};ui(()->CookieManager.getInstance().setCookie(url,name+"="+value+"; Path="+path+"; Secure; SameSite=Lax",ok->{accepted[0]=ok;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS)||!accepted[0])throw new IllegalStateException("Synthetic scoped cookie rejected");}
    private void offer(Item item){ui(()->preloader.offer(Collections.singletonList(item)));}
    private void failLast(){ui(()->calls.get(calls.size()-1).failure("受控预读限制，不代表真实平台权限"));}
    private void suppress(Item item,int expected,String label)throws Exception{offer(item);Thread.sleep(750);check(onUi(()->calls.size()==expected&&!preloader.busy()),label);}
    void run(){String database="test-ui-prefetch-account-"+UUID.randomUUID()+".db";SharedPreferences prefs=test.getTargetContext().getSharedPreferences("article-cache",0);boolean had=prefs.contains("namespace");String namespace=prefs.getString("namespace","initial");
        try{
            check(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"isolated emulator only; synthetic cookies and controlled transport");
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            storage=(ExecutorService)field(field(home,"controller"),"storage");
            path="/question/"+System.currentTimeMillis();url="https://www.zhihu.com"+path;
            Item item=new Item(Source.ZHIHU,"账号边界预读冷却测试",url,"");
            cookie("z_c0","synthetic-account-a");cookie("_xsrf","synthetic-context-a");
            ui(()->preloader=new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item value,Repository.Result<Document> result){calls.add(result);}public void cancel(){}},1,()->{},true));
            offer(item);if(!await(()->calls.size()==1))throw new IllegalStateException("First controlled acquisition absent");check(true,"first body attempt reaches controlled transport");failLast();
            suppress(item,1,"unchanged failed account remains cooled down");
            cookie("_xsrf","synthetic-context-b");suppress(item,1,"rotating request context does not restart a failed account attempt");
            cookie("z_c0","synthetic-account-b");offer(item);boolean renewed=await(()->calls.size()==2);check(renewed,"actual account fingerprint change immediately permits a new attempt");
            if(renewed)failLast();
            int before=onUi(()->calls.size()==2)?2:1;
            ui(()->new ArticleDiskCache(home).invalidate());offer(item);boolean invalidated=await(()->calls.size()==before+1);check(invalidated,"new cache namespace immediately permits a new attempt");
            if(invalidated){failLast();suppress(item,before+1,"a second failure in the new namespace is cooled down again");}
            ui(()->preloader.pause());int stopped=calls.size();Thread.sleep(750);check(onUi(()->calls.size()==stopped&&!preloader.busy()),"pause leaves no active retry or automatic work");
            Item unrelated=new Item(Source.ZHIHU,"另一个账号冷却测试",url+"/answer/1","");offer(unrelated);check(await(()->calls.size()==stopped+1),"another article is not blocked by this article cooldown");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{
            ui(()->{if(preloader!=null)preloader.close();if(home!=null)home.finish();});test.waitForIdleSync();
            if(storage!=null)try{if(!storage.awaitTermination(8,TimeUnit.SECONDS)){failures++;report.append("ERROR storage cleanup timeout\n");}}catch(InterruptedException error){Thread.currentThread().interrupt();failures++;report.append("ERROR storage cleanup interrupted\n");}
            if(url!=null)for(String name:List.of("z_c0","_xsrf")){CountDownLatch done=new CountDownLatch(1);ui(()->CookieManager.getInstance().setCookie(url,name+"=; Path="+path+"; Max-Age=0; Secure",ignored->done.countDown()));try{if(!done.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Scoped cookie cleanup timeout");}catch(Exception error){failures++;report.append("ERROR cookie cleanup\n");}}
            SharedPreferences.Editor restore=prefs.edit();if(had)restore.putString("namespace",namespace);else restore.remove("namespace");restore.commit();
            check(prefs.getString("namespace","initial").equals(namespace),"cache namespace restored after test");test.getTargetContext().deleteDatabase(database);
        }
        report.append("scope=synthetic account/cache scope and controlled transport; not real login permissions or energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
