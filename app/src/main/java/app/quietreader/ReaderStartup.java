package app.quietreader;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.content.ComponentCallbacks2;
import android.content.res.Configuration;
import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.RenderProcessGoneDetail;
import androidx.webkit.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Prepares at most one blank reader. The unclaimed view owns only application context. */
final class ReaderStartup {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final List<Runnable> waiting=new ArrayList<>();
    private static boolean started,pending,failed,engineReady,claimed,callbacksRegistered;
    private static WebView blank;
    private static MutableContextWrapper blankContext;
    private static ExecutorService startupWorker;
    private static Runnable deferredStart;
    private static int foregroundActivities;
    private static long startupEpoch;
    private static final Runnable backgroundRelease=()->{if(foregroundActivities==0)releaseUnclaimed();};
    private static final Runnable warmTimeout=()->{if(pending){discardBlank();finish(true,startupWorker);}};
    static boolean pending(){return pending;}
    static boolean failed(){return failed;}
    static boolean deferred(){return deferredStart!=null;}
    /** Only an explicit user retry clears a failed optional startup. */
    static void retry(Context context,Runnable ready){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Reader retry requires main-thread ownership");
        if(failed&&!pending){failed=false;if(!engineReady)started=false;}
        prepare(context,ready);
    }
    static void prepare(Context context,Runnable ready){
        prepare(context,ready,false);
    }
    /** Hold optional provider work until the owner's cached native result has drawn. */
    static void defer(Context context,Runnable ready){
        prepare(context,ready,true);
    }
    static void releaseDeferred(){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Reader startup requires main-thread ownership");
        Runnable launch=deferredStart;deferredStart=null;if(launch!=null)launch.run();
    }
    private static void prepare(Context context,Runnable ready,boolean defer){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Reader startup requires main-thread ownership");
        if(started&&!pending){
            if(!failed&&engineReady&&!claimed&&blank==null&&foregroundActivities>0){pending=true;long epoch=++startupEpoch;waiting.add(ready);Context application=context.getApplicationContext();Runnable launch=()->warmBlank(application,startupWorker,epoch);if(defer)deferredStart=launch;else main.post(launch);}
            else main.post(ready);
            return;
        }
        waiting.add(ready);if(started){if(!defer)releaseDeferred();return;}started=true;pending=true;long epoch=++startupEpoch;
        Context application=context.getApplicationContext();
        if(!callbacksRegistered){
            callbacksRegistered=true;
            application.registerComponentCallbacks(new ComponentCallbacks2(){
                @Override public void onConfigurationChanged(Configuration configuration){releaseUnclaimed();}
                @Override public void onLowMemory(){releaseUnclaimed();}
                @Override public void onTrimMemory(int level){if(level>=TRIM_MEMORY_RUNNING_LOW)releaseUnclaimed();}
            });
            ((Application)application).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
                @Override public void onActivityStarted(Activity activity){foregroundActivities++;main.removeCallbacks(backgroundRelease);}
                @Override public void onActivityStopped(Activity activity){foregroundActivities=Math.max(0,foregroundActivities-1);main.postDelayed(backgroundRelease,1000);}
                @Override public void onActivityCreated(Activity activity,Bundle state){}
                @Override public void onActivityResumed(Activity activity){}
                @Override public void onActivityPaused(Activity activity){}
                @Override public void onActivitySaveInstanceState(Activity activity,Bundle state){}
                @Override public void onActivityDestroyed(Activity activity){if(foregroundActivities==0)main.postDelayed(backgroundRelease,1000);}
            });
        }
        if(defer)deferredStart=()->startProvider(application,epoch);else startProvider(application,epoch);
    }
    private static void startProvider(Context application,long epoch){
        if(epoch!=startupEpoch||!pending)return;
        ThreadPoolExecutor worker=new ThreadPoolExecutor(0,1,5,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),r->{Thread thread=new Thread(()->{android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);r.run();},"reader-startup");thread.setDaemon(true);return thread;});
        startupWorker=worker;
        java.util.concurrent.Executor guarded=task->worker.execute(()->{try{task.run();}catch(RuntimeException|LinkageError error){main.post(()->finish(true,worker,epoch));}});
        try{
            WebViewCompat.startUpWebView(application,new WebViewStartUpConfig.Builder(guarded).build(),new WebViewOutcomeReceiver<WebViewStartUpResult,WebViewStartupException>(){
                @Override public void onResult(WebViewStartUpResult result){main.post(()->{if(epoch!=startupEpoch||!pending)return;engineReady=true;warmBlank(application,worker,epoch);});}
                @Override public void onError(WebViewStartupException error){main.post(()->finish(true,worker,epoch));}
            });
        }catch(RuntimeException|LinkageError error){finish(true,worker,epoch);}
    }
    private static void warmBlank(Context application,ExecutorService worker,long epoch){
        if(epoch!=startupEpoch||!pending||blank!=null)return;
        if(foregroundActivities==0||claimed){finish(false,worker,epoch);return;}
        try{
            blankContext=new MutableContextWrapper(application);
            WebView view=new WebView(blankContext);blank=view;
            WebSettings settings=view.getSettings();settings.setJavaScriptEnabled(false);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
            settings.setBlockNetworkLoads(true);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            view.setWebViewClient(new WebViewClient(){
                @Override public void onPageFinished(WebView web,String url){if(web==blank&&pending)finish(false,worker,epoch);}
                @Override public boolean onRenderProcessGone(WebView web,RenderProcessGoneDetail detail){if(web==blank){discardBlank();if(pending)finish(true,worker,epoch);}return true;}
            });
            main.postDelayed(warmTimeout,15000);
            view.loadDataWithBaseURL(null,"<!doctype html><meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'\"><body></body>","text/html","utf-8",null);
        }catch(RuntimeException|LinkageError error){discardBlank();finish(true,worker,epoch);}
    }
    static WebView take(Activity owner){
        if(Looper.myLooper()!=Looper.getMainLooper())throw new IllegalStateException("Reader view requires main-thread ownership");
        claimed=true;
        if(blank==null||pending)return new WebView(owner);
        WebView view=blank;MutableContextWrapper context=blankContext;blank=null;blankContext=null;
        context.setBaseContext(owner);view.setWebViewClient(new WebViewClient());view.clearHistory();view.getSettings().setBlockNetworkLoads(false);return view;
    }
    private static void discardBlank(){
        WebView view=blank;blank=null;blankContext=null;
        if(view!=null){view.stopLoading();view.destroy();}
    }
    private static void releaseUnclaimed(){
        boolean deferred=deferredStart!=null;
        if(deferred){deferredStart=null;if(!engineReady)started=false;++startupEpoch;}
        boolean warming=blank!=null&&pending;discardBlank();
        // Losing the optional warm view is not a provider failure. A later read can construct normally.
        if(warming||deferred)finish(false,startupWorker);
    }
    private static void finish(boolean error,ExecutorService worker,long epoch){
        if(epoch!=startupEpoch){if(worker!=null&&worker!=startupWorker)worker.shutdown();return;}
        finish(error,worker);
    }
    private static void finish(boolean error,ExecutorService worker){
        if(worker!=null)worker.shutdown();if(!pending)return;deferredStart=null;main.removeCallbacks(warmTimeout);pending=false;failed=error;
        List<Runnable> listeners=new ArrayList<>(waiting);waiting.clear();for(Runnable listener:listeners)listener.run();
    }
}
