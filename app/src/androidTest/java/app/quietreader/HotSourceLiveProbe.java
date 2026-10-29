package app.quietreader;

import android.app.*;
import android.content.Context;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Opt-in live retrieval diagnostic. Availability is reported, never asserted from mocks. */
final class HotSourceLiveProbe {
    private final Instrumentation instrumentation;
    private HotSourceCollector collector;
    private final boolean production;
    private final List<Source> sources;
    HotSourceLiveProbe(Instrumentation instrumentation){this(instrumentation,false);}
    HotSourceLiveProbe(Instrumentation instrumentation,boolean production){this(instrumentation,production,null);}
    HotSourceLiveProbe(Instrumentation instrumentation,boolean production,Source source){this(instrumentation,production,source,false);}
    HotSourceLiveProbe(Instrumentation instrumentation,boolean production,Source source,boolean textOnly){this.instrumentation=instrumentation;this.production=production;this.sources=textOnly?HotSourceCatalog.TEXT_HOT:source==null?HotSourceCatalog.HOT:Collections.singletonList(source);if(source!=null&&!HotSourceCatalog.eligible(source))throw new IllegalArgumentException("Not a hot source");}
    void run(){
        Context context=instrumentation.getTargetContext();String database="test-live-sources-"+UUID.randomUUID()+".db";
        ExecutorService storage=Executors.newSingleThreadExecutor();FeedStore store=new FeedStore(context,database);
        CountDownLatch finished=new CountDownLatch(1);AtomicInteger errors=new AtomicInteger();
        StringBuilder report=new StringBuilder(production?"LIVE PRODUCTION HOT SOURCE RETRIEVAL\nBilibili/Douyin use serial normal WebViews; V2EX may fall back once. No login/captcha actions; full reading not certified.\n":"LIVE NATIVE HOT SOURCE RETRIEVAL\nNo browser fallback; login/full reading not certified by this probe.\n");
        Activity[] activity={null};String uiDatabase="test-ui-"+UUID.randomUUID()+".db";
        int ready=0;boolean completed=false;
        try{
            // An explicit diagnostic of an optional board is an opt-in in this
            // isolated probe database, never a change to the user's settings.
            storage.submit(()->{for(Source source:sources)store.enable(source,true);}).get(5,TimeUnit.SECONDS);
            if(production)activity[0]=instrumentation.startActivitySync(new android.content.Intent(context,DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",uiDatabase).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK));
            instrumentation.runOnMainSync(()->{
                HotSourceCollector.Listener listener=new HotSourceCollector.Listener(){
                    public void onSourceChanged(Source source){}
                    public void onFinished(){finished.countDown();}
                    public void onStorageFailure(){errors.incrementAndGet();}
                };
                collector=production?new HotSourceCollector(activity[0],store,storage,listener):new HotSourceCollector(store,storage,listener,new HotSourceCollector.Transport(){
                    public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){
                        if(browser)throw new IllegalStateException("Native probe only");
                        Repository repository=new Repository(context);repository.board(source,result);return repository::close;
                    }
                    public boolean offline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);return cm!=null&&cm.getActiveNetwork()==null;}
                });collector.refresh(sources);
            });
            completed=finished.await(90,TimeUnit.SECONDS);
            for(Source source:sources){
                org.json.JSONObject state=storage.submit(()->store.sourceState(source)).get(5,TimeUnit.SECONDS);
                List<HotCandidate> candidates=storage.submit(()->{List<HotCandidate> rows=store.candidates(5000,0);rows.removeIf(c->c.source!=source);return rows;}).get(5,TimeUnit.SECONDS);
                if(state.getString("status").equals("READY"))ready++;
                report.append(source.label).append(" status=").append(state.getString("status")).append(" rows=").append(candidates.size()).append(" lastSuccessAt=").append(state.getLong("lastSuccessAt")).append(" retryAt=").append(state.getLong("retryAt")).append('\n');
                if(!state.optString("error").isEmpty())report.append("  ").append(state.getString("error")).append('\n');
                Map<ContentCategory,Integer> categories=new EnumMap<>(ContentCategory.class);int tagged=0;
                for(HotCandidate candidate:candidates){if(!candidate.sourceTag.isEmpty())tagged++;ContentCategory category=ContentCategory.of(candidate);categories.put(category,categories.getOrDefault(category,0)+1);}
                report.append("  explicitTags=").append(tagged).append(" categories=").append(categories).append('\n');
                report.append("  sourceSnippets=").append(candidates.stream().filter(candidate->!candidate.snippet.isEmpty()).count()).append('/').append(candidates.size()).append('\n');
                report.append("  sourcePublicationDates=").append(candidates.stream().filter(candidate->candidate.publishedAt!=null).count()).append('/').append(candidates.size()).append('\n');
                for(int n=0;n<Math.min(3,candidates.size());n++)report.append("  ").append(candidates.get(n).title).append(" publishedAt=").append(candidates.get(n).publishedAt).append('\n');
            }
            if(completed&&production&&sources.equals(HotSourceCatalog.TEXT_HOT)&&errors.get()==0){
                List<HotCandidate> collected=storage.submit(()->store.candidates(5000,0)).get(5,TimeUnit.SECONDS);
                org.json.JSONArray pool=new org.json.JSONArray();for(HotCandidate candidate:collected)pool.put(candidate.json());
                List<FeedStore.Row> neutral=CandidateOrder.arrange(collected,new org.json.JSONArray(),false);
                int groupedEntries=0,merged=0;for(FeedStore.Row row:neutral){groupedEntries+=row.entries.size();if(row.entries.size()>1)merged++;}
                report.append("neutralTextCards=").append(neutral.size()).append(" eligibleEntries=").append(groupedEntries).append(" mergedCards=").append(merged).append("; algorithmic grouping, not human event labels\n");
                org.json.JSONObject corpus=new org.json.JSONObject().put("schema",1).put("capturedAt",System.currentTimeMillis()).put("purpose","public hot candidates; no user preference labels").put("candidates",pool);
                try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),"text-hot-replay.json"))){output.write(corpus.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                report.append("publicReplayCandidates=").append(pool.length()).append("; saved text-hot-replay.json, no account/session export\n");
            }
        }catch(Exception ex){errors.incrementAndGet();report.append("ERROR ").append(ex.getClass().getSimpleName()).append('\n');}
        finally{
            if(collector!=null)instrumentation.runOnMainSync(collector::close);
            try{storage.submit(store::close).get(5,TimeUnit.SECONDS);}catch(Exception ex){errors.incrementAndGet();}
            storage.shutdown();context.deleteDatabase(database);
            if(activity[0]!=null){instrumentation.runOnMainSync(activity[0]::finish);instrumentation.waitForIdleSync();context.deleteDatabase(uiDatabase);}
        }
        report.append("completed=").append(completed).append(" ready=").append(ready).append('/').append(sources.size()).append(" storageErrors=").append(errors.get()).append('\n');
        Bundle result=new Bundle();result.putString("stream",report.toString());result.putInt("ready",ready);result.putBoolean("completed",completed);
        instrumentation.finish(completed&&errors.get()==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
