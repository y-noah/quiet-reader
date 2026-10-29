package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.os.*;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Two real foreground collections, then controlled failures against the same live-derived cache. */
final class LiveTextRefreshProbe {
    private final Instrumentation test;
    private final ExecutorService storage=Executors.newSingleThreadExecutor();
    private final AtomicReference<CountDownLatch> completion=new AtomicReference<>();
    private final AtomicInteger storageErrors=new AtomicInteger();
    private final StringBuilder report=new StringBuilder("LIVE TEXT REFRESH MATRIX: no login/captcha actions; same titles do not prove staleness or freshness.\n");
    private int checks,failures;
    private Activity activity;private FeedStore store;private HotSourceCollector collector;
    LiveTextRefreshProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private <T>T db(Callable<T> work)throws Exception{return storage.submit(work).get(8,TimeUnit.SECONDS);}
    private void ui(Runnable work){test.runOnMainSync(work);}
    private HotSourceCollector.Listener listener(){return new HotSourceCollector.Listener(){public void onSourceChanged(Source source){}public void onFinished(){CountDownLatch done=completion.get();if(done!=null)done.countDown();}public void onStorageFailure(){storageErrors.incrementAndGet();}};}
    private JSONObject snapshot(Source source)throws Exception{return db(()->{
        JSONObject value=store.sourceState(source);List<HotCandidate> all=store.candidates(5000,0),rows=new ArrayList<>();for(HotCandidate candidate:all)if(candidate.source==source)rows.add(candidate);
        rows.sort(Comparator.comparing(c->c.id));StringBuilder text=new StringBuilder();Set<String> ids=new HashSet<>();boolean chinese=true,owned=true;int videos=0,snippets=0;
        for(HotCandidate c:rows){text.append(c.id).append(':').append(c.textHash).append(':').append(c.sourceRank).append('\n');ids.add(c.id);chinese&=HotSourceCatalog.hasChineseTitle(c.title);owned&=UrlPolicy.belongs(source,c.originalUrl);if(c.video)videos++;if(!c.snippet.isEmpty())snippets++;}
        JSONObject first=new JSONObject();try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT id,first_seen FROM candidates WHERE source=? AND active=1 ORDER BY id",new String[]{source.name()})){while(cursor.moveToNext())first.put(cursor.getString(0),cursor.getLong(1));}
        return value.put("source",source.name()).put("rows",rows.size()).put("content_sha256",HotCandidate.hash(text.toString())).put("first_seen",first).put("unique",ids.size()==rows.size()).put("chinese",chinese).put("owned",owned).put("videos",videos).put("snippets",snippets);
    });}
    private boolean refresh(List<Source> sources,long seconds)throws Exception{CountDownLatch done=new CountDownLatch(1);completion.set(done);ui(()->collector.refresh(sources));return done.await(seconds,TimeUnit.SECONDS);}
    private boolean preserved(JSONObject before,JSONObject after)throws JSONException{return before.getInt("rows")==after.getInt("rows")&&before.getString("content_sha256").equals(after.getString("content_sha256"))&&before.getLong("lastSuccessAt")==after.getLong("lastSuccessAt");}
    void run(){String suffix=UUID.randomUUID().toString(),database="test-live-refresh-"+suffix+".db",uiDatabase="test-ui-refresh-"+suffix+".db";JSONArray rounds=new JSONArray();Map<Source,JSONObject> previous=new EnumMap<>(Source.class);int firstReady=0,secondReady=0,secondAttempts=0;
        try{
            store=new FeedStore(test.getTargetContext(),database);db(()->{for(Source source:HotSourceCatalog.TEXT_HOT)store.enable(source,true);return null;});
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",uiDatabase).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            ui(()->collector=new HotSourceCollector(activity,store,storage,listener()));
            for(int round=1;round<=2;round++){
                long began=System.currentTimeMillis();boolean finished=refresh(HotSourceCatalog.TEXT_HOT,110);check(finished,"real text collection round "+round+" finishes within bounded foreground window");if(!finished)throw new IllegalStateException("Live source timeout");
                JSONArray states=new JSONArray();
                for(Source source:HotSourceCatalog.TEXT_HOT){
                    JSONObject state=snapshot(source),before=previous.get(source);String status=state.getString("status");boolean ready="READY".equals(status);
                    if(ready){if(round==1)firstReady++;else secondReady++;}
                    check(!"REFRESHING".equals(status)&&state.getBoolean("unique")&&state.getBoolean("chinese")&&state.getBoolean("owned"),source.name()+" round "+round+" settles with unique Chinese owned-source candidates");
                    if(ready)check(state.getLong("lastSuccessAt")>=began&&state.getInt("rows")>0,source.name()+" round "+round+" persists a real successful acquisition time");
                    if(before!=null){
                        boolean attempted=state.getLong("requested")>before.getLong("requested");if(attempted)secondAttempts++;
                        if(!ready)check(preserved(before,state),source.name()+" failed or rate-limited second round preserves previous live contents and successful time");
                        JSONObject oldFirst=before.getJSONObject("first_seen"),newFirst=state.getJSONObject("first_seen");boolean retained=true;int shared=0;
                        for(Iterator<String> ids=oldFirst.keys();ids.hasNext();){String id=ids.next();if(newFirst.has(id)){shared++;retained&=oldFirst.getLong(id)==newFirst.getLong(id);}}
                        check(retained,source.name()+" refresh preserves first-observation identity for "+shared+" resident candidates");
                        state.put("new_attempt",attempted).put("same_content",before.getString("content_sha256").equals(state.getString("content_sha256")));
                    }
                    report.append("ROUND ").append(round).append(' ').append(source.name()).append(" status=").append(status).append(" requested=").append(state.getLong("requested")).append(" committed=").append(state.getLong("committed")).append(" rows=").append(state.getInt("rows")).append(" snippets=").append(state.getInt("snippets")).append(" videos=").append(state.getInt("videos")).append(" successAt=").append(state.getLong("lastSuccessAt")).append(" retryAt=").append(state.getLong("retryAt")).append(round==2?" attempted="+state.getBoolean("new_attempt")+" sameContent="+state.getBoolean("same_content"):"").append('\n');
                    if(!state.optString("error").isEmpty())report.append("  status_reason=").append(state.getString("error")).append('\n');
                    states.put(state);previous.put(source,state);
                }
                rounds.put(new JSONObject().put("round",round).put("began_at",began).put("completed_at",System.currentTimeMillis()).put("sources",states));
            }
            ui(collector::close);
            AtomicInteger failedRequests=new AtomicInteger();ui(()->collector=new HotSourceCollector(store,storage,listener(),new HotSourceCollector.Transport(){public boolean offline(){return true;}public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){failedRequests.incrementAndGet();result.failure("受控离线回退验证");return ()->{};}}));
            boolean finished=refresh(HotSourceCatalog.TEXT_HOT,15);check(finished,"controlled offline transport finishes against the same live-derived database");JSONArray fallback=new JSONArray();
            for(Source source:HotSourceCatalog.TEXT_HOT){JSONObject before=previous.get(source),after=snapshot(source);boolean attempted=after.getLong("requested")>before.getLong("requested");check(preserved(before,after)&&!"REFRESHING".equals(after.getString("status"))&&(!attempted||"OFFLINE".equals(after.getString("status"))),source.name()+" controlled failure retains real rows and freshness metadata without fake success");fallback.put(new JSONObject().put("source",source.name()).put("attempted",attempted).put("status",after.getString("status")).put("rows",after.getInt("rows")));}
            check(storageErrors.get()==0,"two live rounds and controlled fallback report no storage errors");
            JSONObject evidence=new JSONObject().put("schema",1).put("live_rounds",rounds).put("controlled_fallback",fallback).put("controlled_requests",failedRequests.get()).put("login_certified",false).put("personal_quality_certified",false).put("notice","Short-interval acquisition evidence only; identical payloads do not prove stale or fresh upstream content. Offline stage uses an injected failing transport, not a real radio outage.");
            try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),"live-text-refresh-matrix.json"))){output.write(evidence.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{
            if(collector!=null)ui(collector::close);
            try{if(store!=null)db(()->{store.close();return null;});}catch(Exception error){failures++;}
            storage.shutdown();if(activity!=null){ui(activity::finish);test.waitForIdleSync();}test.getTargetContext().deleteDatabase(database);test.getTargetContext().deleteDatabase(uiDatabase);test.getTargetContext().deleteDatabase("test-trial-"+uiDatabase);
        }
        int sourceCount=HotSourceCatalog.TEXT_HOT.size();
        report.append("liveReady=").append(firstReady).append('/').append(sourceCount).append(" then ").append(secondReady).append('/').append(sourceCount).append(" secondNewAttempts=").append(secondAttempts).append('/').append(sourceCount).append("; availability is not inferred from check count\n");report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
