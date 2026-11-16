package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import app.quietreader.discovery.*;
import app.quietreader.ai.RecommendationPolicy;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import static app.quietreader.Models.*;

/** Public live bodies and normal prefetch. No seeded body or extra cache preparation wait. */
final class LiveCacheOpenRoundsProbe {
    private final Instrumentation test;private final long dwellMs,homeWaitMs;
    private DiscoveryActivity home;private DiscoveryController controller;private Repository repo;
    private volatile MainActivity reader;private ExecutorService storage;
    private final StringBuilder report=new StringBuilder();private int failures;
    private final JSONArray observations=new JSONArray(),rounds=new JSONArray(),boards=new JSONArray();
    LiveCacheOpenRoundsProbe(Instrumentation test){this(test,0);}
    LiveCacheOpenRoundsProbe(Instrumentation test,long dwellMs){this(test,dwellMs,0);}
    LiveCacheOpenRoundsProbe(Instrumentation test,long dwellMs,long homeWaitMs){this.test=test;this.dwellMs=dwellMs;this.homeWaitMs=homeWaitMs;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier action){boolean[] value={false};ui(()->value[0]=action.getAsBoolean());return value[0];}
    private void await(String label,BooleanSupplier condition,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IOException(label);Thread.sleep(30);}}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> batch(){List<FeedStore.Row> out=new ArrayList<>();ui(()->out.addAll((List<FeedStore.Row>)field(controller,"rows")));return out;}
    private File bodyFile(Item item){String[] key={""};ui(()->{ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());key[0]=HotCandidate.hash(disk.namespace()+"\n"+ArticleDiskCache.session(item.url)+"\n"+item.url);});return new File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+key[0]+".json");}
    private void emit(String text){report.append(text).append('\n');Bundle status=new Bundle();status.putString("stream",text+"\n");test.sendStatus(1,status);}
    private static String nativeText(android.view.View view){
        StringBuilder text=new StringBuilder();if(view instanceof android.widget.TextView)text.append(((android.widget.TextView)view).getText()).append('\n');
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++)text.append(nativeText(group.getChildAt(i)));}return text.toString();
    }
    private String warmStatus(){
        List<FeedStore.Row> rows=batch();String[] status={""};ui(()->{
            int cached=0;for(FeedStore.Row row:rows)if(repo.cachedArticle(row.candidate.readerItem())!=null)cached++;
            status[0]=" cached="+cached+" readerPending="+ReaderStartup.pending()+" preloaderBusy="+((ArticlePreloader)field(controller,"preloader")).busy()+" budgetMs="+((ActionPrefetchBudget)field(controller,"prefetchBudget")).remaining(SystemClock.elapsedRealtime())+" collecting="+field(controller,"loading")+" modelBusy="+(field(controller,"recommendation")!=null)+" foreground="+field(controller,"foreground")+" restriction="+RecommendationPolicy.restriction(home);
        });return status[0];
    }
    private JSONObject open(int round,int index,FeedStore.Row row)throws Exception{
        Item item=row.candidate.readerItem();boolean[] memory={false};ui(()->memory[0]=repo.cachedArticle(item)!=null);
        boolean disk=bodyFile(item).isFile();reader=null;long[] clicked={0},wall={0};
        ui(()->{clicked[0]=SystemClock.elapsedRealtime();wall[0]=System.currentTimeMillis();try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(home,row.candidate);}catch(Exception e){throw new IllegalStateException(e);}});
        long domMs=-1,visibleMs=-1;boolean sourceWork=false;String terminal="timeout",cache="unknown";Document[] acquired={null};
        long deadline=clicked[0]+25000;
        while(SystemClock.elapsedRealtime()<deadline){
            if(reader==null){Thread.sleep(20);continue;}
            WebView[] web={null};String[] marker={""},image={""},page={""};boolean[] fetching={false};
            ui(()->{acquired[0]=(Document)field(reader,"reading");web[0]=(WebView)field(reader,"readerWeb");page[0]=(String)field(reader,"readerPageUrl");fetching[0]=field(reader,"dynamic")!=null||field(reader,"pendingArticle")!=null;
                Document doc=acquired[0];if(doc!=null&&doc.hasContent())for(Section part:ReaderHtml.sections(doc)){
                    for(Block block:part.blocks){if("image".equals(block.type)&&image[0].isEmpty())image[0]=block.value;if("text".equals(block.type)&&!block.value.trim().isEmpty()){marker[0]=block.value.substring(0,Math.min(20,block.value.length()));break;}}
                    // A later folded answer is not evidence for the primary body.
                    // In particular, a picture-only question must be measured by its picture.
                    if(!marker[0].isEmpty()||!image[0].isEmpty())break;
                }});
            sourceWork|=fetching[0];Document doc=acquired[0];
            if(doc!=null&&(doc.loginRequired||doc.sourceUnavailable||doc.filteredVideo)){terminal=doc.loginRequired?"login_required":doc.sourceUnavailable?"source_unavailable":"filtered_video";break;}
            if(web[0]!=null&&(!marker[0].isEmpty()||!image[0].isEmpty())){
                CountDownLatch evaluated=new CountDownLatch(1);boolean[] ready={false};
                String evidence=!marker[0].isEmpty()?"document.body.innerText.includes("+JSONObject.quote(marker[0])+")":"Array.from(document.querySelectorAll('main img')).some(i=>i.getAttribute('src')==="+JSONObject.quote(image[0])+"&&i.complete&&i.naturalWidth>0&&i.getBoundingClientRect().bottom>0&&i.getBoundingClientRect().top<innerHeight)";
                ui(()->{boolean js=web[0].getSettings().getJavaScriptEnabled();web[0].getSettings().setJavaScriptEnabled(true);web[0].evaluateJavascript("location.href==="+JSONObject.quote(page[0])+"&&document.readyState==='complete'&&("+evidence+")",value->{ready[0]="true".equals(value);web[0].getSettings().setJavaScriptEnabled(js);evaluated.countDown();});});
                if(evaluated.await(2,TimeUnit.SECONDS)&&ready[0]){
                    domMs=SystemClock.elapsedRealtime()-clicked[0];CountDownLatch visual=new CountDownLatch(1);long[] visualAt={0};
                    ui(()->{web[0].postVisualStateCallback(index,new WebView.VisualStateCallback(){public void onComplete(long request){visualAt[0]=SystemClock.elapsedRealtime();visual.countDown();}});web[0].invalidate();});
                    if(visual.await(3,TimeUnit.SECONDS)){visibleMs=visualAt[0]-clicked[0];terminal="readable";}else terminal="visual_timeout";
                    break;
                }
            }
            Thread.sleep(25);
        }
        if(acquired[0]!=null&&acquired[0].cachedAt>0&&acquired[0].cachedAt<=wall[0])cache=memory[0]?"memory":disk?"disk_or_history":"prepared_handoff_or_history";
        else if(sourceWork)cache="foreground_acquisition";
        JSONObject result=new JSONObject().put("round",round).put("index",index).put("source",item.source.name()).put("url",item.url).put("title",item.title).put("memoryHitBeforeClick",memory[0]).put("diskFileBeforeClick",disk).put("sourceWorkObserved",sourceWork).put("bodyOrigin",cache).put("domMs",domMs).put("readableMs",visibleMs).put("instant",visibleMs>=0&&visibleMs<=1000).put("terminal",terminal);
        if(!"readable".equals(terminal)&&reader!=null){
            CountDownLatch diagnosed=new CountDownLatch(1);String[] evidence={"no reader WebView"};
            ui(()->{WebView web=(WebView)field(reader,"readerWeb");Document doc=(Document)field(reader,"reading");
                evidence[0]="document="+(doc!=null)+" content="+(doc!=null&&doc.hasContent())+" dynamic="+(field(reader,"dynamic")!=null)+" pending="+(field(reader,"pendingArticle")!=null)+" native="+nativeText(reader.getWindow().getDecorView());
                if(web==null){diagnosed.countDown();return;}boolean js=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);
                web.evaluateJavascript("JSON.stringify({text:document.body.innerText.slice(0,1000),images:Array.from(document.querySelectorAll('main img')).map(i=>({src:i.getAttribute('src'),complete:i.complete,width:i.naturalWidth,top:i.getBoundingClientRect().top,bottom:i.getBoundingClientRect().bottom}))})",value->{evidence[0]+=" dom="+value;web.getSettings().setJavaScriptEnabled(js);diagnosed.countDown();});});
            diagnosed.await(3,TimeUnit.SECONDS);result.put("diagnostic",evidence[0]);
        }
        observations.put(result);emit("OPEN "+result);if(dwellMs>0&&"readable".equals(terminal))Thread.sleep(dwellMs);
        if(reader!=null){MainActivity closing=reader;ui(closing::finish);await("reader closes",closing::isDestroyed,5000);reader=null;}
        await("home resumes",()->(boolean)field(controller,"foreground"),5000);
        return result;
    }
    void run(){String database="test-ui-live-open-rounds-"+UUID.randomUUID()+".db";Application app=(Application)test.getTargetContext().getApplicationContext();
        Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){public void onActivityCreated(Activity a,Bundle b){}public void onActivityPostCreated(Activity a,Bundle b){if(a instanceof MainActivity)reader=(MainActivity)a;}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}};
        app.registerActivityLifecycleCallbacks(observer);
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Isolated emulator only");
            ui(()->repo=new Repository(test.getTargetContext()));List<HotCandidate> pool=new ArrayList<>();long observed=System.currentTimeMillis();
            for(Source source:List.of(Source.ZHIHU,Source.WEIBO,Source.HUPU,Source.CLS,Source.V2EX,Source.ITHOME,Source.THEPAPER,Source.BBC,Source.BAIDU,Source.SOLIDOT,Source.INFOQ)){
                List<Item> items=new ArrayList<>();CountDownLatch done=new CountDownLatch(1);String[] error={""};
                ui(()->repo.board(source,new Repository.Result<List<Item>>(){public void success(List<Item> value){items.addAll(value);done.countDown();}public void failure(String message){error[0]=message;done.countDown();}}));
                if(!done.await(35,TimeUnit.SECONDS))error[0]="timeout";
                JSONObject board=new JSONObject().put("source",source.name()).put("items",items.size()).put("error",error[0]);boards.put(board);emit("BOARD "+board);
                for(int n=0;n<Math.min(30,items.size());n++){Item item=items.get(n);if(!item.video&&UrlPolicy.belongs(source,item.url))pool.add(new HotCandidate(source,item.url,item.title,item.snippet,item.detail,false,n+1,item.publishedAt,observed));}
            }
            if(pool.size()<FeedBatch.SIZE)throw new IOException("Insufficient live supply: "+pool.size());
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values()){List<HotCandidate> subset=new ArrayList<>();for(HotCandidate item:pool)if(item.source==source)subset.add(item);seed.enable(source,!subset.isEmpty());if(!subset.isEmpty())seed.upsertSnapshot(source,seed.beginRefresh(source),subset,observed);}seed.finishInterestSetup(List.of(),Map.of(),observed);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(home,"controller");storage=(ExecutorService)field(controller,"storage");
            emit("CONTEXT autoRefresh="+field(controller,"autoRefresh")+" restriction="+RecommendationPolicy.restriction(home));
            await("initial feed",()->(boolean)field(controller,"ready")&&!((List<?>)field(controller,"rows")).isEmpty(),15000);
            for(int round=1;round<=3;round++){
                long before=(long)field(controller,"snapshot");
                if(round>1){ui(controller::refreshRecommendations);await("refresh ends",()->!(boolean)field(controller,"explicitRefreshRunning"),15000);}
                long warmStarted=SystemClock.elapsedRealtime(),warmSnapshot=(long)field(controller,"snapshot");
                if(homeWaitMs>0){emit("HOME_WAIT round="+round+" durationMs="+homeWaitMs+" snapshot="+warmSnapshot);long nextStatus=warmStarted+15000;while(SystemClock.elapsedRealtime()-warmStarted<homeWaitMs){Thread.sleep(1000);if(SystemClock.elapsedRealtime()>=nextStatus){emit("HOME_WAIT_PROGRESS round="+round+" elapsedMs="+(SystemClock.elapsedRealtime()-warmStarted)+warmStatus());nextStatus+=15000;}}}
                long actualHomeWait=SystemClock.elapsedRealtime()-warmStarted;
                List<FeedStore.Row> rows=batch();Collections.shuffle(rows,new Random(20261103+round));int slow=0,failed=0,uncached=0;List<Long> durations=new ArrayList<>();
                for(int index=0;index<rows.size();index++){JSONObject result=open(round,index+1,rows.get(index));if(!result.getBoolean("instant"))slow++;if(!result.getString("terminal").equals("readable"))failed++;if(result.getString("bodyOrigin").equals("foreground_acquisition"))uncached++;if(result.getLong("readableMs")>=0)durations.add(result.getLong("readableMs"));}
                Collections.sort(durations);JSONObject result=new JSONObject().put("round",round).put("readingDwellMs",dwellMs).put("homeWaitMs",homeWaitMs).put("actualHomeWaitMs",actualHomeWait).put("snapshotAtHomeWaitStart",warmSnapshot).put("opened",rows.size()).put("notInstant",slow).put("failed",failed).put("foregroundAcquisitions",uncached).put("snapshotBeforeAction",before).put("snapshotAfterReads",field(controller,"snapshot")).put("medianMs",durations.isEmpty()?-1:durations.get(durations.size()/2)).put("maxMs",durations.isEmpty()?-1:durations.get(durations.size()-1));rounds.put(result);emit("ROUND "+result);
            }
        }catch(Throwable error){failures++;emit("ERROR "+android.util.Log.getStackTraceString(error));}
        finally{
            ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();if(repo!=null)repo.close();});app.unregisterActivityLifecycleCallbacks(observer);
            if(storage!=null)try{storage.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);
            try{JSONObject result=new JSONObject().put("readingDwellMs",dwellMs).put("homeWaitMs",homeWaitMs).put("thresholdMs",1000).put("scope","Public live bodies, ordinary controller; x86 debug emulator visual callback observation, not physical phone frames; no injected body; prescribed home wait and reading dwell recorded explicitly").put("boards",boards).put("rounds",rounds).put("opens",observations).put("infrastructureFailures",failures);try(FileOutputStream output=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"live-cache-open-rounds-"+dwellMs+(homeWaitMs>0?"-home-"+homeWaitMs:"")+".json"))){output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));}}catch(Exception error){failures++;report.append(error);}
        }
        report.append("checks=").append(observations.length()).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
