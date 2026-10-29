package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import app.quietreader.ai.*;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.json.JSONArray;
import static app.quietreader.Models.*;

/** Actual controller/model ITEM -> normal home open, with synthetic cached prose.
 * The callback boundary deliberately fixes the user action timing, not a touch benchmark. */
final class ModelReadingInterruptionProbe {
    private final Instrumentation test;private final boolean rerank;private final Source liveSource;private DiscoveryActivity home;private MainActivity reader;
    private DiscoveryController controller;private ExecutorService storage;private Repository cache;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    private volatile long clicked,firstItem,resumed,closedAt,diedAt,clickWall;private volatile int pid,late;
    private IBinder observedBinder;private final IBinder.DeathRecipient death=()->diedAt=SystemClock.elapsedRealtime();
    private final CountDownLatch opened=new CountDownLatch(1);
    private final List<java.io.File> files=new ArrayList<>();private final List<String> urls=new ArrayList<>();
    ModelReadingInterruptionProbe(Instrumentation test){this(test,false);}
    ModelReadingInterruptionProbe(Instrumentation test,boolean rerank){this(test,rerank,null);}
    ModelReadingInterruptionProbe(Instrumentation test,boolean rerank,Source liveSource){this.test=test;this.rerank=rerank;this.liveSource=liveSource;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private long now(){return SystemClock.elapsedRealtime();}
    private void observeWorker(RankingClient client){if(observedBinder!=null)return;Messenger service=(Messenger)field(client,"service");if(service!=null){observedBinder=service.getBinder();try{observedBinder.linkToDeath(death,0);}catch(RemoteException e){throw new IllegalStateException(e);}}}
    private void await(String label,long timeout,java.util.function.BooleanSupplier condition)throws Exception{long end=now()+timeout;while(!condition.getAsBoolean()){if(now()>end)throw new IllegalStateException("Timed out: "+label);Thread.sleep(30);}}
    private boolean workerAlive(){List<ActivityManager.RunningAppProcessInfo> list=((ActivityManager)test.getTargetContext().getSystemService(Activity.ACTIVITY_SERVICE)).getRunningAppProcesses();return list!=null&&list.stream().anyMatch(p->p.processName.equals(test.getTargetContext().getPackageName()+":ranking"));}
    private String js(String code)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->{WebView web=(WebView)field(reader,"readerWeb");if(web==null){done.countDown();return;}boolean old=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(code,value->{result[0]=value;web.getSettings().setJavaScriptEnabled(old);done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("DOM callback timeout");return result[0];}
    private HotCandidate liveCandidate()throws Exception{
        List<Item> items=new ArrayList<>();CountDownLatch listed=new CountDownLatch(1);String[] error={""};
        ui(()->{cache=new Repository(test.getTargetContext());cache.board(liveSource,new Repository.Result<List<Item>>(){public void success(List<Item> rows){items.addAll(rows);listed.countDown();}public void failure(String why){error[0]=why;listed.countDown();}});});
        if(!listed.await(30,TimeUnit.SECONDS)||items.isEmpty())throw new IllegalStateException("Actual board unavailable: "+error[0]);
        for(int n=0;n<items.size();n++){
            Item item=items.get(n);if(item.video)continue;CountDownLatch inspected=new CountDownLatch(1);Document[] existing={null};
            ui(()->cache.restoreArticle(item,doc->{existing[0]=doc;inspected.countDown();}));if(!inspected.await(5,TimeUnit.SECONDS))throw new IllegalStateException("Cache inspection timed out");
            if(existing[0]==null||!ArticleVersion.matches(item,existing[0])){
                report.append("LIVE_TARGET url=").append(item.url).append(" sourceRank=").append(n+1).append("; no fresh matching disk/memory body before model starts\n");
                return new HotCandidate(item.source,item.url,item.title,item.snippet,item.detail,false,n+1,item.publishedAt,System.currentTimeMillis());
            }
        }
        throw new IllegalStateException("All current live bodies are already cached; cannot claim a fresh network acquisition");
    }
    void run(){String database="test-ui-model-reading-"+UUID.randomUUID()+".db";Application app=(Application)test.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){}
            public void onActivityStarted(Activity a){}
            public void onActivityResumed(Activity a){if(a instanceof MainActivity){reader=(MainActivity)a;resumed=now();}}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){}
        };app.registerActivityLifecycleCallbacks(lifecycle);
        try{
            report.append(liveSource==null?"SYNTHETIC 100 candidates/body; ":"PUBLIC 100-title corpus with one current uncached live-board article; no synthetic prose; ").append("real bundled models and controller; stage=").append(rerank?"reranker":"embedding").append("; fixed ITEM-boundary open, not touch/phone/frame timing\n");
            List<HotCandidate> candidates=new ArrayList<>();long observed=System.currentTimeMillis();
            if(liveSource==null){for(int n=0;n<100;n++)candidates.add(new HotCandidate(Source.ITHOME,"https://www.ithome.com/0/model-reading-"+observed+"-"+n+".htm","本地模型计算中的缓存阅读"+n,"软件开发与数据备份的实际经验，家庭存储方案和设备维护建议。","",false,n+1,null,observed));}
            else {
                try(java.io.InputStream in=test.getContext().getAssets().open("energy-replay-100-v2.json")){JSONArray input=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("candidates");for(int n=0;n<input.length();n++)candidates.add(HotCandidate.fromJson(input.getJSONObject(n)));}
                candidates.set(40,liveCandidate());
            }
            HotCandidate selected=candidates.get(40);Item item=selected.readerItem();if(liveSource==null)urls.add(item.url);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                Map<Source,List<HotCandidate>> pools=new EnumMap<>(Source.class);for(HotCandidate candidate:candidates)pools.computeIfAbsent(candidate.source,k->new ArrayList<>()).add(candidate);
                for(Source source:Source.values())store.enable(source,pools.containsKey(source));
                for(Map.Entry<Source,List<HotCandidate>> pool:pools.entrySet())store.upsertSnapshot(pool.getKey(),store.beginRefresh(pool.getKey()),pool.getValue(),observed);
                store.feedback("model-reading-interest","MORE","sample",candidates.get(0).id,observed,null,candidates.get(0).modelText(),candidates.get(0).source);
            }
            // Installation/verification is outside the measured interval, as with an already usable App.
            ModelFiles.prepare(test.getTargetContext());
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database).putExtra("test_skip_fetch",true));
            controller=(DiscoveryController)field(home,"controller");storage=(ExecutorService)field(controller,"storage");
            await("home and blank reader",20000,()->{boolean[] ready={false};ui(()->ready[0]=(boolean)field(controller,"ready")&&!ReaderStartup.pending()&&!ReaderStartup.failed());return ready[0];});
            ui(()->{if(liveSource==null){cache=new Repository(home);Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","模型运行时已缓存的目标正文。".repeat(180)));ArticleVersion.capture(item,doc);cache.cacheArticle(doc);String key=new ArticleDiskCache(home).namespace()+"\n"+ArticleDiskCache.session(item.url)+"\n"+item.url;files.add(new java.io.File(home.getCacheDir(),"article-bodies-v1/"+HotCandidate.hash(key)+".json"));}else check(cache.cachedArticle(item)==null||!ArticleVersion.matches(item,cache.cachedArticle(item)),"live target remains uncached before requesting the actual model");controller.requestRecommendation();});
            await("real ranking client",60000,()->{boolean[] ready={false};ui(()->{Object recommendation=field(controller,"recommendation");ready[0]=recommendation!=null&&field(recommendation,"client")!=null;});return ready[0];});
            ui(()->{
                Object recommendation=field(controller,"recommendation");RankingClient client=(RankingClient)field(recommendation,"client");RankingClient.Listener delegate=(RankingClient.Listener)field(client,"listener");observeWorker(client);
                RankingClient.Listener observer=new RankingClient.Listener(){
                    public void onResult(int kind,Bundle result){
                        if(clicked>0)late++;
                        if(kind==RankingService.HELLO){pid=result.getInt("pid");observeWorker(client);}
                        delegate.onResult(kind,result);
                        if(kind==RankingService.ITEM&&clicked==0&&(rerank?result.containsKey("logit"):result.containsKey("vector"))){
                            firstItem=now();float[] vector=result.getFloatArray("vector");check(rerank?Float.isFinite(result.getFloat("logit",Float.NaN)):vector!=null&&vector.length==512,"actual bundled "+(rerank?"reranker produces a finite score":"embedding produces a 512-dimensional result")+" before reading");
                            clickWall=System.currentTimeMillis();clicked=now();try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(home,selected);}catch(Exception e){throw new IllegalStateException(e);}opened.countDown();
                        }
                    }
                    public void onClosed(){closedAt=now();delegate.onClosed();}
                };
                try{Field listener=RankingClient.class.getDeclaredField("listener");listener.setAccessible(true);listener.set(client,observer);}catch(Exception e){throw new IllegalStateException(e);}
            });
            check(opened.await(45,TimeUnit.SECONDS),"real model result reaches the fixed reading action boundary");if(clicked==0)throw new IllegalStateException("No actual model ITEM");
            await("reader resumed",20000,()->reader!=null&&resumed>0);
            long dom=0,end=now()+(liveSource==null?15000:45000);while(now()<end){
                Document[] body={null};ui(()->body[0]=(Document)field(reader,"reading"));String marker="模型运行时已缓存的目标正文";
                if(liveSource!=null){Document doc=body[0];if(doc==null||!doc.hasContent()||doc.loginRequired||doc.sourceUnavailable||!ArticleVersion.matches(item,doc)){Thread.sleep(25);continue;}marker=doc.blocks.stream().filter(b->b.type.equals("text")&&b.value.length()>30).map(b->b.value.substring(0,Math.min(40,b.value.length()))).findFirst().orElse("");if(marker.isEmpty())throw new IllegalStateException("No actual article text marker");}
                String page=(String)field(reader,"readerPageUrl");if("true".equals(js("location.href==="+JSONObject.quote(page)+"&&document.readyState==='complete'&&document.body.innerText.includes("+JSONObject.quote(marker)+")"))){dom=now();break;}Thread.sleep(25);
            }
            check(dom>0,liveSource==null?"cached exact body displays while leaving the real model window":"newly acquired actual public body displays after leaving the model window");
            check(field(reader,"dynamic")==null&&field(reader,"pendingArticle")==null,liveSource==null?"cached read starts no source fetch or source WebView":"actual native body reaches the reader without source WebView fallback or an unfinished request");
            if(liveSource!=null){Document doc=(Document)field(reader,"reading");check(doc!=null&&doc.cachedAt>=clickWall&&ArticleVersion.matches(item,doc),"live body is acquired after the model-boundary click, with current metadata");report.append("LIVE_BODY source=").append(item.source).append(" sections=").append(doc==null?0:doc.sections.size()).append(" blocks=").append(doc==null?0:doc.blocks.size()).append("\n");}
            if(liveSource!=null){JSONArray feedback=storage.submit(()->((FeedStore)field(controller,"store")).activeFeedback(System.currentTimeMillis())).get(5,TimeUnit.SECONDS);check(feedback.length()==1&&"MORE".equals(feedback.getJSONObject(0).optString("action")),"opening and loading actual prose adds no implicit reading or platform preference to the explicit developer sample");}
            boolean[] stopped={false};ui(()->stopped[0]=field(controller,"recommendation")==null&&!(boolean)field(controller,"foreground"));check(stopped[0],"home open stops the actual controller recommendation before reading");
            await("ranking worker retirement",5000,()->!workerAlive());long retired=now();Thread.sleep(250);
            check(closedAt>=clicked&&closedAt-clicked<1000,"bound client closes during the same reading transition");check(late==0,"cancelled client delivers no later model results to the controller");
            check(diedAt>=clicked&&diedAt-clicked<5000,"actual ranking binder death is observed within five seconds of opening");
            check(!workerAlive(),"released ranking process stays absent during the reader");
            report.append("MODEL_READING ").append(new JSONObject().put("workerPid",pid).put("firstItemToClickMs",clicked-firstItem).put("clientCloseMs",closedAt-clicked).put("binderDeathMs",diedAt>0?diedAt-clicked:-1).put("resumedMs",resumed-clicked).put("domObservedMs",dom>0?dom-clicked:-1).put("liveSource",liveSource==null?"":liveSource.name()).put("retirementObservedMs",retired-clicked).put("lateResults",late)).append('\n');
        }catch(Throwable error){check(false,android.util.Log.getStackTraceString(error));}
        finally{
            ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();if(cache!=null){cache.releaseArticleMemory();cache.close();}if(observedBinder!=null&&observedBinder.isBinderAlive())try{observedBinder.unlinkToDeath(death,0);}catch(java.util.NoSuchElementException ignored){}});test.waitForIdleSync();app.unregisterActivityLifecycleCallbacks(lifecycle);
            try{if(storage!=null)storage.awaitTermination(8,TimeUnit.SECONDS);for(Class<?> type:List.of(ArticleDiskCache.class,ReadingLanguageRecorder.class)){Field writer=type.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(10,TimeUnit.SECONDS);}}catch(Exception ignored){}
            for(java.io.File file:files)file.delete();for(String url:urls)test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(url).commit();test.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
