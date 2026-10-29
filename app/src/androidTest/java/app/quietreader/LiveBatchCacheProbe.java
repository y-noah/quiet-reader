package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.provider.Settings;
import android.webkit.WebView;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import org.json.*;
import static app.quietreader.Models.*;

/** Public live boards and bodies with the ordinary finite-batch Controller.
 * No fake body, loader, source transport, renewed lease or model suspension. */
final class LiveBatchCacheProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;private DiscoveryController controller;private Repository repo;
    private MainActivity reader;private int wifi=-1,mobile=-1;private boolean radiosChanged;
    private final Map<String,String> previousHistory=new LinkedHashMap<>();
    private ExecutorService storage;private int checks,failures;
    private final StringBuilder report=new StringBuilder("LIVE FINITE BATCH CACHE; public network, ordinary Controller and models\n");
    private final Map<String,Long> firstPrepared=new LinkedHashMap<>();
    private final List<HotCandidate> pool=new ArrayList<>();
    LiveBatchCacheProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Class<?> type=owner instanceof Class?(Class<?>)owner:owner.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private boolean onUi(BooleanSupplier task){boolean[] value={false};ui(()->value[0]=task.getAsBoolean());return value[0];}
    private void check(boolean good,String label){checks++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');if(!good)failures++;}
    private void await(String label,BooleanSupplier task,long ms)throws Exception{long until=SystemClock.elapsedRealtime()+ms;while(!onUi(task)){if(SystemClock.elapsedRealtime()>until)throw new IOException(label);Thread.sleep(80);}}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(controller,"rows");}
    private List<Item> batch(){List<Item> result=new ArrayList<>();ui(()->{for(FeedStore.Row row:rows())result.add(row.candidate.readerItem());});return result;}
    private File file(Item item){String[] identity={""};ui(()->{ArticleDiskCache cache=new ArticleDiskCache(test.getTargetContext());identity[0]=HotCandidate.hash(cache.namespace()+"\n"+ArticleDiskCache.session(item.url)+"\n"+item.url);});return new File(test.getTargetContext().getCacheDir(),"article-bodies-v1/"+identity[0]+".json");}
    private boolean prepared(Item item)throws Exception{
        Document[] body={null};ui(()->body[0]=repo.cachedArticle(item));File file=file(item);
        if(body[0]==null||!body[0].hasContent()||body[0].loginRequired||body[0].sourceUnavailable||body[0].filteredVideo||!ArticleVersion.matches(item,body[0])||!file.isFile())return false;
        JSONObject record=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
        return record.optLong("at")==body[0].cachedAt&&DocumentFingerprint.of(body[0]).equals(DocumentFingerprint.of(ReadingHistory.decode(record.getJSONObject("document"))));
    }
    private Map<String,Long> runBatch(String name,List<Item> items)throws Exception{
        Map<String,Long> acquired=new LinkedHashMap<>();long started=SystemClock.elapsedRealtime(),nextStatus=started;
        check(items.size()>3&&items.size()<=20,name+" finite actual batch count="+items.size());
        long deadline=started+125000;
        while(SystemClock.elapsedRealtime()<deadline){
            for(Item item:items)if(!acquired.containsKey(item.url)&&prepared(item)){long ms=SystemClock.elapsedRealtime()-started;acquired.put(item.url,ms);firstPrepared.putIfAbsent(item.url,ms);}
            long now=SystemClock.elapsedRealtime();
            if(now>=nextStatus){String status=name+" prepared="+acquired.size()+"/"+items.size()+" elapsedMs="+(now-started)+"\n";report.append(status);Bundle update=new Bundle();update.putString("stream",status);test.sendStatus(1,update);nextStatus=now+15000;}
            if(acquired.size()==items.size())break;
            if(onUi(()->!((ActionPrefetchBudget)field(controller,"prefetchBudget")).active(SystemClock.elapsedRealtime())&&!((ArticlePreloader)field(controller,"preloader")).busy()))break;
            Thread.sleep(250);
        }
        for(Item item:items)report.append("BODY batch=").append(name).append(" source=").append(item.source).append(" id=").append(HotCandidate.hash(item.url)).append(" preparedMs=").append(acquired.getOrDefault(item.url,-1L)).append('\n');
        check(acquired.size()==items.size(),name+" every displayed body has matching RAM and persisted content before any click: "+acquired.size()+"/"+items.size());
        return acquired;
    }
    private Map<String,String> inventory(){Map<String,String> out=new TreeMap<>();File[] files=new File(test.getTargetContext().getCacheDir(),"article-bodies-v1").listFiles();if(files!=null)for(File file:files)if(file.isFile())out.put(file.getName(),file.length()+":"+file.lastModified());return out;}
    private void drain()throws Exception{((ExecutorService)field(ArticleDiskCache.class,"writer")).submit(()->{}).get(10,TimeUnit.SECONDS);}
    private String shell(String command)throws Exception{try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand(command);InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){return new String(input.readAllBytes(),StandardCharsets.UTF_8);}}
    private void offlineReaders(List<Item> batch,String database)throws Exception{
        Context context=test.getTargetContext();wifi=Settings.Global.getInt(context.getContentResolver(),"wifi_on",-1);mobile=Settings.Global.getInt(context.getContentResolver(),"mobile_data",-1);
        if((wifi!=0&&wifi!=1)||(mobile!=0&&mobile!=1))throw new IllegalStateException("Unknown emulator radio state");
        ui(home::finish);test.waitForIdleSync();storage.awaitTermination(10,TimeUnit.SECONDS);
        radiosChanged=true;shell("svc wifi disable");shell("svc data disable");await("emulator radios actually offline",repo::offline,10000);
        Set<Source> sampled=new HashSet<>();
        for(Item item:batch){if(!sampled.add(item.source))continue;File source=file(item);JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(source.toPath()),StandardCharsets.UTF_8));long acquired=saved.getLong("at");Document expected=ReadingHistory.decode(saved.getJSONObject("document"));String text="";for(Block block:expected.blocks)if(block.type.equals("text")&&block.value.length()>=30){text=block.value.substring(0,25);break;}if(text.isEmpty())throw new IllegalStateException("Public body text sample missing");
            android.content.SharedPreferences history=context.getSharedPreferences("reading-history",0);previousHistory.put(item.url,history.getString(item.url,null));history.edit().remove(item.url).commit();ui(repo::releaseArticleMemory);
            long clicked=SystemClock.elapsedRealtime();reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await("offline reader ready "+item.source,()->field(reader,"reading")!=null&&field(reader,"readerWeb")!=null,10000);
            check(onUi(()->{Document actual=(Document)field(reader,"reading");return ArticleVersion.matches(item,actual)&&actual.cachedAt==acquired&&DocumentFingerprint.of(actual).equals(DocumentFingerprint.of(expected))&&field(reader,"dynamic")==null&&field(reader,"pendingArticle")==null;}),"offline RAM-cold reader restores exact prepared disk body without source acquisition: "+item.source);
            String expectedText=text;boolean visible=false;long until=SystemClock.elapsedRealtime()+10000;
            while(SystemClock.elapsedRealtime()<until){CountDownLatch evaluated=new CountDownLatch(1);String[] result={""};ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.body&&document.body.innerText",value->{result[0]=value;web.getSettings().setJavaScriptEnabled(false);evaluated.countDown();});});if(evaluated.await(3,TimeUnit.SECONDS)&&result[0].contains(expectedText)){visible=true;break;}Thread.sleep(75);}
            check(visible,"actual offline reader DOM contains public article prose: "+item.source);report.append("OFFLINE source=").append(item.source).append(" textVisibleMs=").append(SystemClock.elapsedRealtime()-clicked).append("; memory evicted, history temporarily withheld and restored in cleanup\n");
            ui(reader::finish);test.waitForIdleSync();reader=null;
        }
        check(sampled.size()==3,"offline actual reader sampled all three public sources");
    }
    void run(){String database="test-ui-live-batch-"+UUID.randomUUID()+".db";
        try{
            Context context=test.getTargetContext();if(!context.getPackageName().equals("app.quietreader.discoveryprobe")||!Build.HARDWARE.equals("ranchu")||(context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new IllegalStateException("Owned isolated emulator required");
            List<Source> sources=Arrays.asList(Source.ITHOME,Source.THEPAPER,Source.BBC);ui(()->repo=new Repository(context));long observed=System.currentTimeMillis();
            for(Source source:sources){List<Item> board=new ArrayList<>();String[] error={""};CountDownLatch done=new CountDownLatch(1);ui(()->repo.board(source,new Repository.Result<List<Item>>(){public void success(List<Item> items){board.addAll(items);done.countDown();}public void failure(String why){error[0]=why;done.countDown();}}));
                if(!done.await(35,TimeUnit.SECONDS))throw new IOException("Board timeout "+source);check(board.size()>=8,"public board supply "+source+" count="+board.size()+" error="+error[0]);
                for(int n=0;n<Math.min(30,board.size());n++){Item item=board.get(n);if(item.video||!UrlPolicy.belongs(source,item.url))continue;pool.add(new HotCandidate(source,item.url,item.title,item.snippet,"",false,n+1,item.publishedAt,observed));}
            }
            try(FeedStore seed=new FeedStore(context,database)){for(Source source:Source.values())seed.enable(source,sources.contains(source));for(Source source:sources){List<HotCandidate> subset=new ArrayList<>();for(HotCandidate item:pool)if(item.source==source)subset.add(item);seed.upsertSnapshot(source,seed.beginRefresh(source),subset,observed);}seed.finishInterestSetup(List.of(),Map.of(),observed);}
            home=(DiscoveryActivity)test.startActivitySync(new Intent(context,DiscoveryActivity.class).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(home,"controller");storage=(ExecutorService)field(controller,"storage");
            await("ordinary finite home ready",()->(boolean)field(controller,"ready")&&(boolean)field(controller,"foreground")&&!rows().isEmpty(),15000);
            check(onUi(()->(boolean)field(controller,"autoRefresh")&&field(field(controller,"preloader"),"loader")==null),"ordinary caching enabled without replacing loader or suspending models");
            List<Item> before=batch();int cold=0;for(Item item:before)if(!file(item).exists())cold++;report.append("INITIAL pool=").append(pool.size()).append(" displayed=").append(before.size()).append(" coldDiskAtFirstObservation=").append(cold).append("; no cache clearing\n");
            Map<String,Long> initial=runBatch("initial",before);long first=(long)field(controller,"snapshot");
            Thread.sleep(2000);
            ui(controller::refreshRecommendations);await("explicit batch acceptance",()->(long)field(controller,"snapshot")!=first,15000);await("list draw finishes spinner",()->!(boolean)field(controller,"explicitRefreshRunning"),5000);
            check(true,"real explicit refresh finishes its indicator before waiting for speculative preparation");
            List<Item> after=batch();check(!before.stream().map(i->i.url).toList().equals(after.stream().map(i->i.url).toList()),"explicit refresh presents a different ordered batch");
            Map<String,Long> refreshed=runBatch("refreshed",after);drain();Set<String> allowed=new HashSet<>();for(Item item:after)allowed.add(item.url);boolean removed=true;int old=0;for(Item item:before)if(!allowed.contains(item.url)){old++;removed&=!file(item).exists();}
            check(removed,"old non-shared body files evicted after actual replacement; oldUnique="+old);
            check(after.stream().map(i->i.source).distinct().count()>=2,"actual displayed live batch includes multiple public sources");
            shell("input keyevent 3");await("actual Android background",()->!(boolean)field(controller,"foreground"),5000);drain();Map<String,String> stopped=inventory();for(int n=0;n<10;n++){ui(()->controller.viewport(0));Thread.sleep(500);}
            check(stopped.equals(inventory())&&onUi(()->!((ArticlePreloader)field(controller,"preloader")).busy()),"background passive callbacks cannot launch body work or create files");
            long feedback=storage.submit(()->((FeedStore)field(controller,"store")).activeFeedback(System.currentTimeMillis()).length()).get(5,TimeUnit.SECONDS);check(feedback==0,"live full-batch caching and exposure create no interest feedback");
            offlineReaders(after,database);
            report.append("RESULT initial=").append(initial.size()).append('/').append(before.size()).append(" refreshed=").append(refreshed.size()).append('/').append(after.size()).append("\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(home!=null&&!home.isFinishing())home.finish();if(repo!=null)repo.close();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(10,TimeUnit.SECONDS);}catch(InterruptedException error){Thread.currentThread().interrupt();}
            if(radiosChanged)try{shell("svc wifi "+(wifi==1?"enable":"disable"));shell("svc data "+(mobile==1?"enable":"disable"));check(Settings.Global.getInt(test.getTargetContext().getContentResolver(),"wifi_on",-1)==wifi&&Settings.Global.getInt(test.getTargetContext().getContentResolver(),"mobile_data",-1)==mobile,"original emulator radio settings restored");}catch(Exception error){failures++;report.append("ERROR radio restoration ").append(error).append('\n');}
            android.content.SharedPreferences.Editor history=test.getTargetContext().getSharedPreferences("reading-history",0).edit();for(Map.Entry<String,String> entry:previousHistory.entrySet())if(entry.getValue()==null)history.remove(entry.getKey());else history.putString(entry.getKey(),entry.getValue());history.commit();test.getTargetContext().deleteDatabase(database);}
        report.append("scope=public live native bodies on x86 emulator, finite batch and ordinary models; not real accounts, ARM peaks, personal quality or unplugged energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
