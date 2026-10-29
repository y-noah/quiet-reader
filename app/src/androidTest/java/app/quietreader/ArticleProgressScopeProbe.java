package app.quietreader;

import android.app.*;
import android.os.*;
import android.webkit.CookieManager;
import app.quietreader.discovery.HotCandidate;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real CookieManager scope checks; timings are emulator diagnostics, not ARM frame/energy proof. */
final class ArticleProgressScopeProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ArticleProgressScopeProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void cookie(String url,String value)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] success={false};ui(()->CookieManager.getInstance().setCookie(url,value,result->{success[0]=result;done.countDown();}));if(!done.await(5,TimeUnit.SECONDS)||!success[0])throw new IllegalStateException("Cookie fixture not accepted");}
    void run(){String origin="https://scope-"+UUID.randomUUID()+".quiet-reader.invalid",url=origin+"/article";android.content.Context context=test.getTargetContext();android.content.SharedPreferences preferences=context.getSharedPreferences("article-cache",0);String prior=preferences.getString("namespace","initial");List<HotCandidate> candidates=new ArrayList<>();
        try{
            String payload="x".repeat(1800);cookie(url,"qr_scope="+payload+"; Path=/; Secure");for(int n=0;n<128;n++)candidates.add(new HotCandidate(Source.ITHOME,origin+"/article/"+n,"作用域性能受控中文话题"+n,"","",false,n+1,null,System.currentTimeMillis()));
            ArticleProgressBinding.Scope[] captured=new ArticleProgressBinding.Scope[128];long[] wall=new long[7],cpu=new long[7],validation=new long[7];
            for(int round=0;round<7;round++){int index=round;ui(()->{long start=System.nanoTime(),beforeCpu=Debug.threadCpuTimeNanos();for(int n=0;n<candidates.size();n++)captured[n]=ArticleProgressBinding.scope(context,candidates.get(n));cpu[index]=Debug.threadCpuTimeNanos()-beforeCpu;wall[index]=System.nanoTime()-start;long validateStart=System.nanoTime();for(ArticleProgressBinding.Scope scope:captured)if(scope==null||!scope.current())throw new IllegalStateException("Unstable unchanged scope");validation[index]=System.nanoTime()-validateStart;});}
            for(int n=2;n<7;n++)report.append("SCOPE_TIMING measuredRound=").append(n-1).append(" captures=128 captureWallMs=").append(wall[n]/1000000.0).append(" captureCpuMs=").append(cpu[n]/1000000.0).append(" validateWallMs=").append(validation[n]/1000000.0).append('\n');
            check(Arrays.stream(captured).allMatch(Objects::nonNull),"128 real scoped captures and revalidations survive unchanged CookieManager state");
            Document doc=new Document();doc.url=candidates.get(0).originalUrl;ArticleSession[] session={null};ui(()->{session[0]=new ArticleSession(context,doc.url);check(session[0].stamp(doc)&&captured[0].owns(doc),"actual stamped document belongs to its captured progress scope");});
            cookie(url,"qr_scope=changed-account; Path=/; Secure");ui(()->check(!session[0].current()&&!captured[0].current()&&!captured[0].owns(doc),"real account-cookie change rejects old token, progress scope and document ownership"));
            ArticleProgressBinding.Scope[] next={null};ui(()->next[0]=ArticleProgressBinding.scope(context,candidates.get(0)));check(next[0]!=null&&!next[0].key.equals(captured[0].key),"new cookie state gets a different persisted scope key");
            preferences.edit().putString("namespace","scope-probe-"+UUID.randomUUID()).commit();ui(()->check(!next[0].current(),"cache namespace replacement invalidates an otherwise unchanged account scope"));
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{preferences.edit().putString("namespace",prior).commit();try{cookie(url,"qr_scope=; Max-Age=0; Path=/; Secure");}catch(Exception error){failures++;report.append("ERROR cookie fixture cleanup\n");}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
