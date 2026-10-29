package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Synthetic supply/cache, real minified controllers/models/UI. Android APIs only. */
final class ReleaseReadingFlowProbe {
    private final Instrumentation test;
    private Activity home,reader;
    private boolean holdReplayScreen,recordFirstReaderStacks;
    private final Map<Activity,Boolean> replayScreenFlags=new WeakHashMap<>();
    /** Test-only lifecycle observation; decor draw is not a presented body frame. */
    private static final class LaunchTimeline implements Application.ActivityLifecycleCallbacks {
        final java.util.concurrent.atomic.AtomicLongArray times=new java.util.concurrent.atomic.AtomicLongArray(10);
        private View decor;private android.view.ViewTreeObserver.OnDrawListener draw;
        private boolean reader(Activity owner){return owner.getClass().getName().equals("app.quietreader.MainActivity");}
        private void tick(int index){times.compareAndSet(index,0,SystemClock.elapsedRealtime());}
        @Override public void onActivityPreCreated(Activity owner,Bundle state){if(reader(owner))tick(0);}
        @Override public void onActivityCreated(Activity owner,Bundle state){if(!reader(owner))return;tick(1);decor=owner.getWindow().getDecorView();draw=()->{tick(4);decor.post(this::removeDraw);};decor.getViewTreeObserver().addOnDrawListener(draw);}
        // Created is dispatched inside super.onCreate; only PostCreated bounds the full override.
        @Override public void onActivityPostCreated(Activity owner,Bundle state){if(reader(owner))tick(7);}
        @Override public void onActivityStarted(Activity owner){if(reader(owner))tick(2);}
        @Override public void onActivityPostStarted(Activity owner){if(reader(owner))tick(8);}
        @Override public void onActivityPostResumed(Activity owner){if(reader(owner))tick(9);}
        @Override public void onActivityResumed(Activity owner){if(reader(owner))tick(3);}
        @Override public void onActivityPaused(Activity owner){}
        @Override public void onActivityStopped(Activity owner){}
        @Override public void onActivitySaveInstanceState(Activity owner,Bundle state){}
        @Override public void onActivityDestroyed(Activity owner){}
        void removeDraw(){if(decor!=null&&draw!=null&&decor.getViewTreeObserver().isAlive())decor.getViewTreeObserver().removeOnDrawListener(draw);draw=null;}
        JSONObject report(long start,long monitor,long idle)throws JSONException{
            JSONObject result=new JSONObject();String[] names={"preCreateMs","createdMs","startedMs","resumedMs","firstDecorDrawMs","dispatchStartedMs","dispatchEndedMs","postCreatedMs","postStartedMs","postResumedMs"};
            for(int n=0;n<names.length;n++){long value=times.get(n);result.put(names[n],value==0?-1:value-start);}
            return result.put("monitorReturnedMs",monitor==0?-1:monitor-start).put("idleWaitEndedMs",idle==0?-1:idle-start).put("scope","test lifecycle/decor observation; Created is inside super; PostCreated bounds full onCreate; not presented body frame");
        }
    }
    /** Explicit diagnostic only: stack sampling changes scheduling and is never a frame benchmark. */
    private static final class FirstReaderStacks implements AutoCloseable {
        private final List<JSONObject> samples=Collections.synchronizedList(new ArrayList<>());
        private final Thread worker;
        private volatile boolean stopped;
        FirstReaderStacks(long start){
            Thread mainThread=Looper.getMainLooper().getThread();
            worker=new Thread(()->{
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                while(!stopped&&SystemClock.elapsedRealtime()-start<15000&&samples.size()<75){
                    long before=SystemClock.elapsedRealtime();StackTraceElement[] trace=mainThread.getStackTrace();
                    try{JSONArray frames=new JSONArray();for(int n=0;n<Math.min(trace.length,48);n++)frames.put(trace[n].toString());
                        samples.add(new JSONObject().put("elapsedMs",before-start).put("captureMs",SystemClock.elapsedRealtime()-before).put("frames",frames));
                    }catch(JSONException error){throw new IllegalStateException(error);}
                    try{Thread.sleep(200);}catch(InterruptedException end){return;}
                }
            },"first-reader-stack-diagnostic");worker.setDaemon(true);worker.start();
        }
        @Override public void close(){stopped=true;worker.interrupt();try{worker.join(2000);}catch(InterruptedException end){Thread.currentThread().interrupt();}}
        void append(StringBuilder report){synchronized(samples){for(JSONObject sample:samples)report.append("FIRST_READER_STACK ").append(sample).append('\n');}
            report.append("FIRST_READER_STACK_SCOPE test-only main-thread snapshots; sampling changes scheduling; not frame or energy evidence; workerStopped=").append(!worker.isAlive()).append('\n');}
    }
    private void holdReplayScreen(Activity owner){
        if(!holdReplayScreen||owner==null)return;
        ui(()->{Window window=owner.getWindow();if(!replayScreenFlags.containsKey(owner))replayScreenFlags.put(owner,(window.getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});
    }
    private void restoreReplayScreens(){
        ui(()->{for(Map.Entry<Activity,Boolean> entry:replayScreenFlags.entrySet()){
            Window window=entry.getKey().getWindow();if(!entry.getValue())window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            if(((window.getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)!=entry.getValue())throw new IllegalStateException("Replay screen flag not restored");
        }replayScreenFlags.clear();});holdReplayScreen=false;
    }
    private JSONArray seedFeedback;
    private String uncachedLiveUrl="";
    private UiAutomation automation(){return test.getUiAutomation();}
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("MINIFIED FLOW: synthetic Chinese supply and cached bodies; not live-source or personal-quality evidence\n");
    private final List<JSONObject> candidates=new ArrayList<>();
    private final List<String> ids=new ArrayList<>(),hashes=new ArrayList<>();
    private static final String DATABASE="personalized-hot-topics-v1.db";
    ReleaseReadingFlowProbe(Instrumentation test){this.test=test;}
    private Context context(){return test.getTargetContext();}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    // Match FeedStore's WAL mode: observation must not repeatedly try switching
    // the live application's journal to TRUNCATE while prefetch writes evidence.
    private SQLiteDatabase db(){return SQLiteDatabase.openDatabase(context().getDatabasePath(DATABASE).getPath(),null,SQLiteDatabase.OPEN_READWRITE|SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING);}
    private long scalar(String sql){try(SQLiteDatabase db=db();Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private String string(String sql){try(SQLiteDatabase db=db();Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getString(0):"";}}
    private static String hash(String text)throws Exception{StringBuilder result=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))result.append(String.format(Locale.ROOT,"%02x",b&255));return result.toString();}
    private View find(View root,String label){if(root instanceof TextView){String description=String.valueOf(root.getContentDescription());if(label.contentEquals(((TextView)root).getText())||label.equals(description)||label.equals("●")&&Set.of("刷新","查看更新","正在刷新").contains(description))return root;}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=find(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
    private <T extends View>T view(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){T found=view(((ViewGroup)root).getChildAt(n),type);if(found!=null)return found;}return null;}
    private boolean shown(String label){boolean[] result={false};ui(()->{View found=find(home.getWindow().getDecorView(),label);result[0]=found!=null&&found.isShown();});return result[0];}
    private void click(String label){ui(()->{if(label.equals("设置")){for(int n=0;n<3;n++){View back=find(home.getWindow().getDecorView(),"返回");if(back==null)break;back.performClick();}}View target=find(home.getWindow().getDecorView(),label);if(target==null||!target.isShown())throw new IllegalStateException("Missing home control: "+label);target.performClick();});test.waitForIdleSync();}
    private void menu(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<until){AccessibilityNodeInfo root=automation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:matching(root,label)){
            if(!node.isVisibleToUser()||!label.contentEquals(node.getText()==null?"":node.getText()))continue;
            if(node.isClickable()&&node.performAction(AccessibilityNodeInfo.ACTION_CLICK)){test.waitForIdleSync();return;}
            Rect rect=new Rect();node.getBoundsInScreen(rect);if(rect.isEmpty())continue;
            long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,rect.centerX(),rect.centerY(),0);
            try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}test.waitForIdleSync();return;
        }Thread.sleep(50);}throw new IllegalStateException("Missing visible menu: "+label);
    }
    private List<AccessibilityNodeInfo> matching(AccessibilityNodeInfo root,String label){List<AccessibilityNodeInfo> result=new ArrayList<>();if(root==null)return result;if(root.getText()!=null&&root.getText().toString().contains(label)||root.getContentDescription()!=null&&root.getContentDescription().toString().contains(label))result.add(root);for(int n=0;n<root.getChildCount();n++)result.addAll(matching(root.getChild(n),label));return result;}
    private boolean accessible(String label){for(AccessibilityNodeInfo node:matching(automation().getRootInActiveWindow(),label)){Rect bounds=new Rect();node.getBoundsInScreen(bounds);if(node.isVisibleToUser()&&!bounds.isEmpty())return true;}return false;}
    private void waitText(String label,long ms)throws Exception{long until=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<until){if(accessible(label))return;Thread.sleep(100);}throw new IllegalStateException("Visible text timeout: "+label);}
    private void waitHome(long ms)throws Exception{long until=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<until){if(shown("设置"))return;Thread.sleep(100);}throw new IllegalStateException("Visible home settings control timeout");}
    private void launch(){home=test.startActivitySync(new Intent().setClassName(context().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();}
    private void prepareModels()throws Exception{
        modelReceipt("before-model-preparation");
        launch();click("设置");menu("高级与诊断");menu("本机推荐状态");menu("校验并修复模型");waitHomeFocus();
        long until=SystemClock.elapsedRealtime()+90000;boolean ready=false;
        while(SystemClock.elapsedRealtime()<until){click("设置");menu("高级与诊断");menu("本机推荐状态");ready=accessible("离线模型校验完成");boolean failed=accessible("模型修复未完成");menu("关闭");waitHomeFocus();if(ready||failed)break;Thread.sleep(300);}
        check(ready,"bundled models prepared through real release repair UI");if(!ready)throw new IllegalStateException("Model repair did not finish");ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
    }
    private void waitHomeFocus()throws Exception{long end=SystemClock.elapsedRealtime()+6000;do{boolean[] focused={false};ui(()->focused[0]=home!=null&&!home.isFinishing()&&home.hasWindowFocus());if(focused[0])return;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<end);throw new IllegalStateException("Home did not regain focus after model dialog dismissal");}
    private void modelReceipt(String phase)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream input=context().getAssets().open("local-ai-manifest.json")){byte[] buffer=new byte[4096];int count;while((count=input.read(buffer))!=-1)bytes.write(buffer,0,count);}
        File receipt=new File(context().getFilesDir(),"local-models/"+hash(bytes.toString("UTF-8"))+"/.verification-v1.json");
        report.append("MODEL_RECEIPT ").append(new JSONObject().put("phase",phase).put("exists",receipt.isFile()).put("bytes",receipt.length())).append('\n');
    }
    private void seed()throws Exception{
        String[] titles={"合成话题：明星红毯造型讨论","合成话题：手机电池续航与散热测试","合成话题：手机电池老化和充电策略","合成话题：安卓手机功耗与后台管理","合成话题：周末徒步路线与装备","合成话题：家用路由器组网经验"};
        long now=System.currentTimeMillis();
        for(int n=0;n<titles.length;n++){
            // A new URL generation avoids the previous run's real in-memory or
            // reading-history cache winning over freshly written fixture files.
            String url="https://www.v2ex.com/t/"+now+n,snippet=n==0?"合成摘要：娱乐明星活动。":n<4?"合成摘要：分享手机续航、散热和电池使用经验。":"合成摘要：社区交流使用经验。";
            JSONObject value=new JSONObject().put("source","V2EX").put("url",url).put("title",titles[n]).put("snippet",snippet).put("heat","").put("video",false).put("rank",n+1).put("publishedAt",JSONObject.NULL).put("observedAt",now);
            value.put("bodyLanguage","CHINESE").put("bodyLanguageAt",now).put("videoEvidenceAt",now);
            candidates.add(value);ids.add(hash("V2EX\n"+url));hashes.add(hash("hot-title-v2\n"+titles[n]+"\n"+snippet));
        }
        persistSeed(now,true,false);
    }
    private void persistSeed(long now,boolean clearModels,boolean disableLearning)throws Exception{
        try(SQLiteDatabase db=db()){
            db.beginTransaction();try{
                // This package contains only this probe's synthetic data. Never use installprobe.
                for(String table:new String[]{"snapshot_rows","snapshots","feedback","saved","candidates"})db.delete(table,null,null);
                if(clearModels){db.delete("embeddings",null,null);db.delete("reranks",null,null);}
                db.execSQL("UPDATE sources SET enabled=0,status='EMPTY',requested=requested+1");
                db.execSQL("UPDATE sources SET enabled=1,status='STALE',success_at=?,retry_at=? WHERE id='V2EX'",new Object[]{now,now+86400000L});
                for(int n=0;n<candidates.size();n++){ContentValues c=new ContentValues();c.put("id",ids.get(n));c.put("source",candidates.get(n).getString("source"));c.put("payload",candidates.get(n).toString());c.put("text_hash",hashes.get(n));c.put("first_seen",now);c.put("last_seen",now);c.put("rank",n+1);c.put("active",1);db.insertOrThrow("candidates",null,c);db.execSQL("UPDATE sources SET enabled=1,status='READY',success_at=?,retry_at=? WHERE id=?",new Object[]{now,now+86400000L,candidates.get(n).getString("source")});}
                if(seedFeedback==null){
                db.execSQL("INSERT INTO feedback(id,action,scope,target,at,sample,source) VALUES ('flow-positive','MORE','sample',?,?,?,?)",new Object[]{ids.get(1)+"@"+hashes.get(1),now,candidates.get(1).getString("title")+"\n"+candidates.get(1).getString("snippet"),candidates.get(1).getString("source")});
                }else for(int f=0;f<seedFeedback.length();f++){
                    JSONObject value=seedFeedback.getJSONObject(f);
                    db.execSQL("INSERT INTO feedback(id,action,scope,target,at,sample) VALUES (?,?,?,?,?,?)",new Object[]{value.getString("id"),value.getString("action"),"sample",value.getString("id"),now,value.getString("sample")});
                }
                db.execSQL("INSERT OR REPLACE INTO meta VALUES ('sources',20),('profile',20),('profile_time',?),('candidate_policy',2),('interest_setup_done',1),('read_learning_disabled',0),('active_snapshot',0)",new Object[]{now});
                if(disableLearning)db.execSQL("UPDATE meta SET value=1 WHERE key='read_learning_disabled'");
                db.setTransactionSuccessful();
            }finally{db.endTransaction();}
        }
        String namespace=context().getSharedPreferences("article-cache",0).getString("namespace","initial");
        for(int n=0;n<candidates.size();n++){
            String url=candidates.get(n).getString("url");if(url.equals(uncachedLiveUrl))continue;String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie(url));
            context().getSharedPreferences("reading-history",0).edit().remove(url).commit();
            String session=fixtureSession(url,cookie[0]);JSONArray blocks=new JSONArray();
            for(int p=0;p<24;p++)blocks.put(new JSONObject().put("type","text").put("value",p==0?"合成阅读正文起点：这段内容来自独立测试缓存，并非真实平台文章。":"合成正文第"+p+"段：用于检查统一阅读排版、滚动以及返回卡片列表的行为。内容不代表真实新闻或个人推荐效果。").put("images",new JSONArray()).put("links",new JSONArray()));
            JSONObject section=new JSONObject().put("id","flow-body").put("label","合成作者 · 阅读测试").put("answer",false).put("blocks",blocks);
            JSONObject doc=new JSONObject().put("url",url).put("title",candidates.get(n).getString("title")).put("byline","合成作者").put("metadataHash",hashes.get(n)).put("tiebaReplyRevision",TiebaReplyRelations.REVISION).put("hupuBodyRevision",HupuDiscussion.BODY_REVISION).put("sections",new JSONArray().put(section));
            if("TIEBA".equals(candidates.get(n).getString("source"))&&"/hottopic/browse/hottopic".equals(java.net.URI.create(url).getPath())){
                String post="https://tieba.baidu.com/p/"+(99009000000L+n);String[] postCookie={null};ui(()->postCookie[0]=CookieManager.getInstance().getCookie(post));
                JSONObject choice=new JSONObject().put("source","TIEBA").put("title","合成缓存对应帖（仅测试）").put("url",post).put("detail","").put("video",false);
                doc.put("topicResolution",new JSONObject().put("topic",url).put("post",post).put("session",fixtureSession(post,postCookie[0])).put("at",now).put("choices",new JSONArray().put(choice)));
            }
            File root=new File(context().getCacheDir(),"article-bodies-v1");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Fixture cache directory unavailable");
            try(FileOutputStream out=new FileOutputStream(new File(root,hash(namespace+"\n"+session+"\n"+url)+".json"))){out.write(new JSONObject().put("at",now).put("document",doc).put("related",new JSONArray()).toString().getBytes(StandardCharsets.UTF_8));}
        }
    }
    // Serialization reference for seeding only; does not validate cookie/session policy.
    private String fixtureSession(String url,String raw)throws Exception{
        if(raw==null)raw="";
        java.net.URI uri=java.net.URI.create(url);String host=uri.getHost(),path=uri.getPath();
        if("https".equals(uri.getScheme())&&"www.zhihu.com".equals(host)&&uri.getPort()==-1&&path.matches("/question/[1-9][0-9]*(?:/answer/[1-9][0-9]*)?/?")){
            // Seeding serialization reference only. Account validation is tested separately.
            boolean authenticated=false;
            for(String field:raw.split(";")){String value=field.trim();if(fixtureCookieName(value).equals("z_c0")&&value.indexOf('=')>=0&&!value.substring(value.indexOf('=')+1).trim().isEmpty())authenticated=true;}
            List<String> fields=new ArrayList<>();
            for(String field:raw.split(";")){
                String value=field.trim(),name=fixtureCookieName(value);
                if(name.equals("_xsrf")||name.equals("_zap")||name.equals("_gid")||name.equals("_gat")||name.equals("_ga")||name.startsWith("_ga_")||name.startsWith("_gat_"))continue;
                if(name.matches("Hm_(?:lpvt|lvt)_[a-fA-F0-9]{32}"))continue;
                if(authenticated&&(name.equals("BEC")||name.equals("JOID")||name.equals("osd")||name.equals("SESSIONID")))continue;
                if(!value.isEmpty())fields.add(value);
            }
            fields.sort(Comparator.comparing(ReleaseReadingFlowProbe::fixtureCookieName));
            return hash("zhihu-content-v2;"+String.join("; ",fields));
        }
        boolean tieba="tieba.baidu.com".equals(host)&&(path.matches("/p/\\d+/?")||path.startsWith("/hottopic/"));
        if("www.baidu.com".equals(host)&&"/s".equals(path)||tieba){
            List<String> account=new ArrayList<>();
            for(String field:raw.split(";")){int at=field.indexOf('=');if(at<=0)continue;String name=field.substring(0,at).trim().toUpperCase(Locale.ROOT);if(tieba&&Set.of("BAIDUID","BAIDUID_BFESS","BAIDU_WISE_UID","HMACCOUNT").contains(name))continue;if(name.matches("BDUSS.*|[PS]TOKEN.*|PASSID.*|.*(?:SESSION|AUTH|TOKEN|LOGIN|USER|ACCOUNT).*|.*UID|SID|SSON"))account.add(field.trim());}
            Collections.sort(account);return hash((tieba?"tieba-account-v2;":"baidu-account-v1;")+String.join("; ",account));
        }
        return hash(raw);
    }
    private static String fixtureCookieName(String value){int at=value.indexOf('=');return at>0?value.substring(0,at).trim():value;}
    private void requestRanking()throws Exception{
        click("设置");menu("高级与诊断");menu("本机推荐状态");
        report.append("CORE_REQUEST_DETAIL atMs=").append(SystemClock.elapsedRealtime()).append('\n');
        dump(automation().getRootInActiveWindow(),0);
        menu("继续整理");
        check(shown("设置"),"continue organizing returns to the feed where actual model work is allowed");
    }
    private long waitPersonal(long after)throws Exception{
        long until=SystemClock.elapsedRealtime()+60000,acknowledged=after;int windows=1;
        while(SystemClock.elapsedRealtime()<until){
            String current="profile_version=(SELECT value FROM meta WHERE key='profile') AND source_version=(SELECT value FROM meta WHERE key='sources') AND id>"+after;
            long id=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots WHERE mode='personal' AND "+current);
            if(id>0&&scalar("SELECT COUNT(*) FROM reranks")>0){report.append("CORE_EXPLICIT_WINDOWS ").append(windows).append('\n');return id;}
            // A foreground window may return partial vectors at its existing
            // deadline. Continue through the actual UI only after that window
            // has committed, keeping the original overall deadline and cap.
            long committed=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots WHERE "+current);
            if(committed>acknowledged&&windows<4){acknowledged=committed;windows++;requestRanking();}
            Thread.sleep(100);
        }
        report.append("CORE_TIMEOUT vectors=").append(scalar("SELECT COUNT(*) FROM embeddings")).append(" pairs=").append(scalar("SELECT COUNT(*) FROM reranks")).append(" windows=").append(windows).append('\n');
        throw new IllegalStateException("Personal snapshot/rerank cache timeout");
    }
    private long active(){return scalar("SELECT value FROM meta WHERE key='active_snapshot'");}
    private void trialFlow()throws Exception{
        long before=active(),feedback=scalar("SELECT COUNT(*) FROM feedback"),started=System.currentTimeMillis();
        click("设置");menu("高级与诊断");menu("本轮推荐评价");menu("选择操作");menu("对比推荐效果");waitText("本轮评价 · 1/6",30000);
        check(!accessible("基础顺序")&&!accessible("双模型顺序"),"minified paired evaluation hides ranking-mode attribution before labels");
        String[] choices={"想看","不想看","不确定"};for(int n=0;n<6;n++){waitText("本轮评价 · "+(n+1)+"/6",8000);menu(choices[n%3]);}
        waitText("本轮评价已保存",8000);check(accessible("基础顺序：想看 2/6")&&accessible("双模型顺序：想看 2/6")&&accessible("不确定或未评价 2"),"minified completion summary preserves unknown labels and per-order counts");
        File file=context().getDatabasePath("recommendation-trials.db");
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(file.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT id,at,payload FROM sessions ORDER BY at DESC,rowid DESC LIMIT 1",null)){
            if(!c.moveToFirst())throw new IllegalStateException("Minified trial session missing");JSONObject payload=new JSONObject(c.getString(2));String id=c.getString(0);
            check(c.getLong(1)>=started&&"paired_snapshot".equals(payload.getString("kind"))&&!payload.getBoolean("holdout_qualified")&&payload.getJSONArray("items").length()==6&&payload.getJSONObject("rankings").length()==3,"non-debuggable production trial database retains paired orders and honest provenance");
            JSONObject learning=payload.getJSONObject("learning_provenance");
            check(db.getVersion()==2&&learning.getInt("schema")==1&&!learning.getBoolean("holdout_qualified")&&learning.getJSONArray("candidate_events").length()==6&&learning.getLong("learning_cutoff_ms")<=payload.getLong("frozen_at_ms")&&learning.getInt("ledger_rows")==feedback,"minified paired trial captures the real pre-ranking learning ledger in schema two");
            try(Cursor labels=db.rawQuery("SELECT COUNT(*),COUNT(DISTINCT event),COUNT(DISTINCT choice) FROM labels WHERE session=?",new String[]{id})){labels.moveToFirst();check(labels.getInt(0)==6&&labels.getInt(1)==6&&labels.getInt(2)==3,"minified native choices persist once per event with three distinct meanings");}
        }
        menu("完成");click("设置");menu("高级与诊断");menu("本轮推荐评价");menu("选择操作");menu("查看最近完成评价");waitText("最近完成的评价",8000);
        check(accessible("6 项：想看 2，不想看 2，不确定 2")&&accessible("双模型顺序：想看 2/6"),"minified completed history reopens the saved counts without recomputing labels");
        Thread.sleep(500);screenshot("release-trial-history.png");menu("关闭");check(active()==before&&scalar("SELECT COUNT(*) FROM feedback")==feedback,"minified evaluation and history leave active cards and learning feedback unchanged");
    }
    private void trialAction(String label)throws Exception{click("设置");menu("高级与诊断");menu("本轮推荐评价");menu("选择操作");menu(label);}
    private JSONObject protocol()throws Exception{
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context().getDatabasePath("recommendation-trials.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT payload FROM evaluation_protocol WHERE id=1",null)){
            if(!c.moveToFirst())throw new IllegalStateException("Prepared protocol missing");return new JSONObject(c.getString(0));
        }
    }
    private long trialCount(){try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context().getDatabasePath("recommendation-trials.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT COUNT(*) FROM sessions",null)){c.moveToFirst();return c.getLong(0);}}
    private Set<String> trialIdentities(){
        Set<String> result=new TreeSet<>();
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context().getDatabasePath("recommendation-trials.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT id FROM sessions",null)){
            while(c.moveToNext())result.add(c.getString(0));
        }return result;
    }
    private void prospectiveFlow()throws Exception{
        long before=active(),feedback=scalar("SELECT COUNT(*) FROM feedback");Set<String> sessions=trialIdentities();
        report.append("PROSPECTIVE_INITIAL_RETAINED_SESSIONS ").append(sessions.size()).append('\n');
        trialAction("准备新话题评价");menu("开始准备");waitText("已准备新话题评价",10000);menu("知道了");
        JSONObject baseline=protocol();long cutoff=baseline.getLong("cutoff_ms");
        check(!baseline.getBoolean("holdout_qualified")&&baseline.getJSONArray("known_identities").length()>=6&&baseline.getJSONArray("used_identities").length()==0,"minified preparation freezes existing candidate identities before future collection");
        trialAction("评价准备后的新话题");waitText("准备之后还没有足够的新话题",10000);
        check(trialIdentities().equals(sessions),"minified prospective UI refuses to relabel the old candidate pool");menu("关闭");
        long at=Math.max(System.currentTimeMillis(),cutoff+1);String title="合成新话题：安卓文字阅读应用的离线字体体验",snippet="合成摘要：准备评价之后才采集到的社区文字讨论。",url="https://www.v2ex.com/t/"+at+"99";
        String id=hash("V2EX\n"+url),textHash=hash("hot-title-v2\n"+title+"\n"+snippet);
        JSONObject value=new JSONObject().put("source","V2EX").put("url",url).put("title",title).put("snippet",snippet).put("heat","").put("video",false).put("rank",7).put("publishedAt",JSONObject.NULL).put("observedAt",at).put("bodyLanguage","CHINESE").put("bodyLanguageAt",at).put("videoEvidenceAt",at);
        try(SQLiteDatabase db=db()){
            db.beginTransaction();try{ContentValues row=new ContentValues();row.put("id",id);row.put("source","V2EX");row.put("payload",value.toString());row.put("text_hash",textHash);row.put("first_seen",at);row.put("last_seen",at);row.put("rank",7);row.put("active",1);db.insertOrThrow("candidates",null,row);db.execSQL("UPDATE meta SET value=value+1 WHERE key='sources'");db.setTransactionSuccessful();}finally{db.endTransaction();}
        }
        trialAction("评价准备后的新话题");waitText("本轮评价 · 1/1",30000);waitText(title,5000);
        Set<String> createdSessions=trialIdentities(),addedSessions=new HashSet<>(createdSessions);addedSessions.removeAll(sessions);
        if(addedSessions.size()!=1)throw new IllegalStateException("Expected exactly one new prospective session identity");
        JSONObject consumed=protocol();
        check(consumed.getString("id").equals(baseline.getString("id"))&&consumed.getJSONArray("used_identities").toString().contains(id)&&consumed.getJSONArray("used_text_hashes").toString().contains(textHash),"minified production consumes the future pool durably before the first label");
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(context().getDatabasePath("recommendation-trials.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor c=db.rawQuery("SELECT payload FROM sessions ORDER BY at DESC,rowid DESC LIMIT 1",null)){
            if(!c.moveToFirst())throw new IllegalStateException("Prospective session missing");JSONObject payload=new JSONObject(c.getString(0)),learning=payload.getJSONObject("learning_provenance");JSONArray members=learning.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members");
            check(payload.getJSONObject("prospective_protocol").getString("id").equals(baseline.getString("id"))&&payload.getJSONArray("pool").length()==1&&members.length()==1&&members.getJSONObject(0).getString("id").equals(id)&&members.getJSONObject(0).getLong("first_seen_ms")>cutoff&&!payload.getBoolean("holdout_qualified"),"minified prospective session retains the original cutoff and genuinely later member provenance");
        }
        menu("不确定");waitText("本轮评价已保存",8000);check(accessible("不确定 1")&&accessible("不确定或未评价 1"),"minified future evaluation preserves uncertainty rather than inventing a dislike");Thread.sleep(500);screenshot("release-prospective-complete.png");menu("完成");
        trialAction("评价准备后的新话题");waitText("准备之后还没有足够的新话题",10000);check(trialIdentities().equals(createdSessions),"minified future pool cannot be reused by another session");menu("关闭");
        check(active()==before&&scalar("SELECT COUNT(*) FROM feedback")==feedback,"minified prospective evaluation leaves visible ranking and interest training untouched");
        // Mutate this isolated fixture's profile version to exercise the production UI's stale-baseline refusal.
        try(SQLiteDatabase db=db()){db.execSQL("UPDATE meta SET value=value+1 WHERE key='profile'");}
        trialAction("评价准备后的新话题");waitText("偏好记录已变化或过期",10000);check(accessible("请重新准备新话题评价")&&trialIdentities().equals(createdSessions),"minified stale preference baseline requires explicit preparation instead of silently mixing histories");menu("关闭");
    }
    private void exportFlow()throws Exception{
        long before=active(),feedback=scalar("SELECT COUNT(*) FROM feedback");
        trialAction("导出评价记录");waitText("选择保存位置",5000);menu("取消");
        trialAction("导出评价记录");menu("选择保存位置");
        String filename="synthetic-minified-observations-"+UUID.randomUUID()+".db";AccessibilityNodeInfo edit=null;long until=SystemClock.elapsedRealtime()+12000;
        while(SystemClock.elapsedRealtime()<until&&edit==null){edit=editable(automation().getRootInActiveWindow());if(edit==null)Thread.sleep(80);}
        if(edit==null)throw new IllegalStateException("System export filename missing");Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,filename);
        check(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args),"minified native export reaches the real system save destination picker");Thread.sleep(300);screenshot("release-export-system-picker.png");menu(accessible("Save")?"Save":"SAVE");returnToFeed();waitHome(10000);
        File output=new File(context().getExternalFilesDir(null),filename);boolean copied=false;until=SystemClock.elapsedRealtime()+10000;
        while(SystemClock.elapsedRealtime()<until&&!copied){
            try(ParcelFileDescriptor descriptor=automation().executeShellCommand("cat /sdcard/Download/"+filename);InputStream input=new FileInputStream(descriptor.getFileDescriptor());OutputStream bytes=new FileOutputStream(output)){byte[] buffer=new byte[32768];int length;while((length=input.read(buffer))!=-1)bytes.write(buffer,0,length);}
            if(output.length()>=16384)copied=true;else Thread.sleep(100);
        }
        boolean sanitized=true;int sessions=0,labels=0;
        try(SQLiteDatabase db=SQLiteDatabase.openDatabase(output.getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor rows=db.rawQuery("SELECT payload FROM sessions",null)){
            while(rows.moveToNext()){sessions++;JSONObject value=new JSONObject(rows.getString(0));sanitized&=!value.getBoolean("holdout_qualified");JSONArray items=value.getJSONArray("items");for(int n=0;n<items.length();n++)sanitized&=items.getJSONObject(n).getJSONObject("candidate").length()==1;}
            try(Cursor rows2=db.rawQuery("SELECT COUNT(*) FROM labels",null)){rows2.moveToFirst();labels=rows2.getInt(0);}
        }
        check(sessions>=2&&labels>=7&&sanitized,"minified production SAF output preserves real fixture observations with metadata-only candidates");
        until=SystemClock.elapsedRealtime()+5000;File folder=new File(context().getCacheDir(),"trial-exports");while(SystemClock.elapsedRealtime()<until&&folder.listFiles()!=null&&folder.listFiles().length>0)Thread.sleep(50);
        check((folder.listFiles()==null||folder.listFiles().length==0)&&active()==before&&scalar("SELECT COUNT(*) FROM feedback")==feedback,"minified successful export cleans staging and leaves learning and active cards unchanged");
        check(output.setReadable(true,false),"synthetic-only minified SAF copy can be checked by the desktop evaluator");report.append("synthetic_snapshot=").append(filename).append('\n');
    }
    private AccessibilityNodeInfo editable(AccessibilityNodeInfo root){if(root==null)return null;if(root.isEditable()&&root.isVisibleToUser())return root;for(int n=0;n<root.getChildCount();n++){AccessibilityNodeInfo result=editable(root.getChild(n));if(result!=null)return result;}return null;}
    private boolean personalSnapshot(){return "personal".equals(string("SELECT mode FROM snapshots WHERE id="+active()));}
    private void flow()throws Exception{
        launchReplay(false);waitText(candidates.get(0).getString("title"),10000);long initial=active();
        check(initial>0&&!personalSnapshot(),"initial real card list has a neutral snapshot");
        String initialOrder=string("SELECT group_concat(event_id,',') FROM (SELECT event_id FROM snapshot_rows WHERE snapshot="+initial+" ORDER BY position)");
        requestRanking();long pending=waitPersonal(initial);
        long computedSource=scalar("SELECT source_version FROM snapshots WHERE id="+pending),computedProfile=scalar("SELECT profile_version FROM snapshots WHERE id="+pending);
        waitText("查看更新",5000);
        click("设置");menu("高级与诊断");menu("本机推荐状态");waitText("已回报模型工作量",5000);
        String usage="";for(AccessibilityNodeInfo node:matching(automation().getRootInActiveWindow(),"已回报模型工作量"))if(node.getText()!=null)usage=node.getText().toString();
        report.append("RESOURCE_DETAIL_OBSERVED ").append(usage).append('\n');
        // Automatic ranking may finish vectors before the explicit request. Cache
        // hits correctly report zero new embeddings; actual rerank work still counts.
        boolean freshWork=usage.matches("(?s).*(?:初筛|精排) [1-9][0-9]* 条.*");
        check(freshWork&&usage.matches("(?s).*初筛 [0-9]+ 条，精排 [0-9]+ 条.*推理 CPU [1-9][0-9]* ms.*加载 CPU [0-9]+ ms.*")&&!usage.contains("计时未回报")&&usage.contains("不代表整机耗电"),"real recommendation details expose reported process CPU and loading work with scope limits");
        menu("关闭");returnToFeed();waitHome(5000);
        check(active()==initial&&shown(candidates.get(0).getString("title"))&&!personalSnapshot(),"real model completion leaves displayed neutral cards and label frozen");
        check(scalar("SELECT COUNT(*) FROM embeddings")>=candidates.size()&&scalar("SELECT COUNT(*) FROM reranks")>=2,"production recommendation writes actual vector and reranker caches");
        click("查看更新");long until=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<until&&(active()==initial||!personalSnapshot()))Thread.sleep(50);
        long accepted=active();
        String acceptedMode=string("SELECT mode FROM snapshots WHERE id="+accepted);
        long acceptedSource=scalar("SELECT source_version FROM snapshots WHERE id="+accepted),acceptedProfile=scalar("SELECT profile_version FROM snapshots WHERE id="+accepted);
        report.append("ACCEPT_OBSERVED initiallyPrepared=").append(pending).append(" active=").append(accepted).append(" mode=").append(acceptedMode).append(" expectedSourceAtLeast=").append(computedSource).append(" actualSource=").append(acceptedSource).append(" expectedProfile=").append(computedProfile).append(" actualProfile=").append(acceptedProfile).append('\n');
        check(accepted>initial&&acceptedMode.equals("personal")&&acceptedSource>=computedSource&&acceptedProfile==computedProfile&&personalSnapshot(),"explicit update accepts a current personal snapshot and its label");
        // Automatic calculation can replace the pending result while the workload
        // details are open. Freeze the result actually accepted by this click.
        pending=accepted;
        String ordered=string("SELECT group_concat(event_id,',') FROM (SELECT event_id FROM snapshot_rows WHERE snapshot="+pending+" ORDER BY position)");
        check(!initialOrder.equals(ordered),"actual local inference changes the synthetic candidate order");
        String title=string("SELECT json_extract(payload,'$.title') FROM snapshot_rows WHERE snapshot="+pending+" ORDER BY position LIMIT 1");
        ui(()->{View target=find(home.getWindow().getDecorView(),title);if(target==null)throw new IllegalStateException("First card absent");View card=target;while(card!=null&&!card.isLongClickable())card=(View)card.getParent();if(card==null)throw new IllegalStateException("Card menu absent");card.performLongClick();});
        check(!accessible("稍后读"),"production card menu has no removed read-later action");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        check(shown(title)&&active()==pending,"dismissing the card menu preserves the accepted recommendation");
        // This isolated core fixture disables prefetch; online prefetch has its
        // separate replay. Reset only the evidence timestamp, not language/version.
        JSONObject evidenceEntry=new JSONObject(string("SELECT payload FROM snapshot_rows WHERE snapshot="+pending+" ORDER BY position LIMIT 1"));
        String evidenceId=hash(evidenceEntry.getString("source")+"\n"+evidenceEntry.getString("url"));
        try(SQLiteDatabase db=db()){db.execSQL("UPDATE candidates SET payload=json_set(payload,'$.bodyLanguageAt',0) WHERE id=?",new Object[]{evidenceId});}
        Thread.sleep(500);
        if(scalar("SELECT COALESCE(json_extract(payload,'$.bodyLanguageAt'),0) FROM candidates WHERE id='"+evidenceId+"'")!=0)throw new IllegalStateException("Reader evidence reset failed");
        String cachedUrl=string("SELECT json_extract(payload,'$.url') FROM candidates WHERE id='"+evidenceId+"'");
        File cachedBody=null;
        File[] cachedBodies=new File(context().getCacheDir(),"article-bodies-v1").listFiles();
        if(cachedBodies!=null)for(File candidate:cachedBodies){
            if(!candidate.getName().endsWith(".json"))continue;
            JSONObject payload=new JSONObject(new String(java.nio.file.Files.readAllBytes(candidate.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            if(cachedUrl.equals(payload.getJSONObject("document").optString("url"))){
                if(cachedBody!=null)throw new IllegalStateException("Ambiguous selected body cache");
                cachedBody=candidate;
            }
        }
        if(cachedBody==null)throw new IllegalStateException("Selected body cache absent");
        long cachedInode=android.system.Os.stat(cachedBody.getPath()).st_ino;
        Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);
        ui(()->{View target=find(home.getWindow().getDecorView(),title);while(target!=null&&!target.isClickable())target=(View)target.getParent();if(target==null)throw new IllegalStateException("Recommendation card absent");target.performClick();});
        reader=test.waitForMonitorWithTimeout(monitor,10000);test.removeMonitor(monitor);if(reader==null)throw new IllegalStateException("Reader launch timeout");
        long readingStarted=SystemClock.elapsedRealtime();waitText("合成阅读正文起点",15000);
        menu("1 · 展开阅读 ↓");waitText("合成正文第3段",5000);
        check(accessible("合成正文第3段"),"actual reader expand action reveals content beyond the folded preview");
        screenshot("release-reading-flow-expanded.png");
        WebView[] web={null};ui(()->web[0]=view(reader.getWindow().getDecorView(),WebView.class));
        check(web[0]!=null,"recommendation card opens the production unified reader WebView");
        check(accessible("合成阅读正文起点"),"production reader decodes full cached body beyond candidate summary");
        long evidenceUntil=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<evidenceUntil&&scalar("SELECT COALESCE(json_extract(payload,'$.bodyLanguageAt'),0) FROM candidates WHERE id='"+evidenceId+"'")==0)Thread.sleep(50);
        check(scalar("SELECT COALESCE(json_extract(payload,'$.bodyLanguageAt'),0) FROM candidates WHERE id='"+evidenceId+"'")>0,"recommendation reader publishes session-bound body language evidence after its timestamp was cleared with prefetch stopped");
        check(cachedBody.isFile()&&android.system.Os.stat(cachedBody.getPath()).st_ino==cachedInode,"opening a validated cached body preserves its file instead of rewriting it");
        if(web[0]==null)throw new IllegalStateException("Reader WebView absent");
        ui(()->web[0].scrollTo(0,900));Thread.sleep(800);int[] scroll={0};ui(()->scroll[0]=web[0].getScrollY());check(scroll[0]>0,"rendered unified body supports vertical reading");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();waitHome(6000);
        check(!home.isFinishing()&&shown(title),"back from reader returns to the retained recommendation list");
        waitText(title,5000);check(active()==pending&&personalSnapshot(),"returning to discovery retains the accepted snapshot and correct mode");
        check(SystemClock.elapsedRealtime()-readingStarted<20000&&scalar("SELECT value FROM meta WHERE key='read_learning_disabled'")==0&&scalar("SELECT COUNT(*) FROM feedback WHERE action='READ'")==0,"enabled weak learning ignores prefetch and reading shorter than twenty seconds");
        ui(()->{View target=find(home.getWindow().getDecorView(),title);while(target!=null&&!target.isLongClickable())target=(View)target.getParent();if(target==null)throw new IllegalStateException("Discovery card menu absent");target.performLongClick();});menu("少看这类");
        until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until&&scalar("SELECT COUNT(*) FROM feedback WHERE action='LESS'")==0)Thread.sleep(50);
        check(scalar("SELECT COUNT(*) FROM feedback WHERE action='LESS' AND source='V2EX'")==1,"actual card dislike records the content feedback and real platform");
        requestRanking();long changed=waitPersonal(pending);waitText("查看更新",5000);
        check(active()==pending&&shown(title),"new feedback result remains pending while the accepted cards stay visible");
        long changedSource=scalar("SELECT source_version FROM snapshots WHERE id="+changed),changedProfile=scalar("SELECT profile_version FROM snapshots WHERE id="+changed);
        click("查看更新");until=SystemClock.elapsedRealtime()+5000;
        // A second valid computation can replace pending before this click. Read
        // the accepted identity, then require its real visible dislike-aware order.
        while(SystemClock.elapsedRealtime()<until&&(active()==pending||!personalSnapshot()))Thread.sleep(50);
        long dislikeAccepted=active();
        String dislikeMode=string("SELECT mode FROM snapshots WHERE id="+dislikeAccepted);
        long dislikeSource=scalar("SELECT source_version FROM snapshots WHERE id="+dislikeAccepted),dislikeProfile=scalar("SELECT profile_version FROM snapshots WHERE id="+dislikeAccepted);
        String first=string("SELECT json_extract(payload,'$.title') FROM snapshot_rows WHERE snapshot="+dislikeAccepted+" ORDER BY position LIMIT 1");
        while(SystemClock.elapsedRealtime()<until&&!shown(first))Thread.sleep(50);
        report.append("DISLIKE_ACCEPT_OBSERVED initiallyPrepared=").append(changed).append(" activeSnapshot=").append(dislikeAccepted)
            .append(" mode=").append(dislikeMode).append(" expectedSourceAtLeast=").append(changedSource).append(" actualSource=").append(dislikeSource)
            .append(" expectedProfile=").append(changedProfile).append(" actualProfile=").append(dislikeProfile)
            .append(" previousTitle=").append(title).append(" newFirst=").append(first)
            .append(" shownFirst=").append(shown(first)).append(" personalLabel=").append(personalSnapshot()).append('\n');
        check(dislikeAccepted>pending&&active()==dislikeAccepted&&dislikeMode.equals("personal")&&dislikeSource>=changedSource&&dislikeProfile==changedProfile&&!first.isEmpty()&&!title.equals(first)&&shown(first)&&personalSnapshot(),"explicit acceptance applies the new dislike-aware ordering through production UI");
    }
    private void returnToFeed(){
        ui(()->{for(int n=0;n<4;n++){View back=find(home.getWindow().getDecorView(),"返回");if(back==null)break;back.performClick();}});
        test.waitForIdleSync();
        if(!shown("设置"))throw new IllegalStateException("Replay failed to return from settings to the feed");
    }
    private String replayRecommendationState(){
        String[] state={""};ui(()->{View dot=find(home.getWindow().getDecorView(),"●");if(dot==null||Build.VERSION.SDK_INT<30)throw new IllegalStateException("API30+ recommendation state observation required");CharSequence value=dot.getStateDescription();state[0]=value==null?"":value.toString();});return state[0];
    }
    private boolean isolatedReplayTarget(){return Arrays.asList("app.quietreader.flowprobe","app.quietreader.discoveryprobe").contains(context().getPackageName());}
    private String replayFixture="energy-replay-100-v2.json";
    void runShortHeadlines(){
        try{
            if(!"app.quietreader.discoveryprobe".equals(context().getPackageName()))throw new IllegalStateException("Isolated discovery target required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"short-headline test uses actual minified nondebuggable app");
            holdReplayScreen=true;launchReplay(false);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("observed-short-headlines-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            seedFeedback=new JSONArray();JSONArray pool=fixture.getJSONArray("candidates");
            for(int n=0;n<pool.length();n++){JSONObject value=pool.getJSONObject(n);candidates.add(value);ids.add(hash(value.getString("source")+"\n"+value.getString("url")));hashes.add(hash("hot-title-v2\n"+value.getString("title")+"\n"+value.optString("snippet")));}
            check(candidates.size()==6,"six exact public records, not invented headline variants");persistSeed(System.currentTimeMillis(),true,true);launchReplay(false);
            long until=SystemClock.elapsedRealtime()+10000;while(active()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long frozen=active();
            check(frozen>0&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==3,"ordinary cold feed displays three events from six short-title records");
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen+" AND json_array_length(payload,'$.groupEntries')=2")==3,"every persisted card preserves both original source entries");
            Set<String> members=new HashSet<>();try(SQLiteDatabase db=db();Cursor cursor=db.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=?",new String[]{Long.toString(frozen)})){while(cursor.moveToNext()){JSONObject row=new JSONObject(cursor.getString(0));JSONArray entries=row.getJSONArray("groupEntries");boolean representative=false;for(int n=0;n<entries.length();n++){JSONObject entry=entries.getJSONObject(n);check(members.add(hash(entry.getString("source")+"\n"+entry.getString("url"))),"each public source identity occurs only once");if(row.getString("source").equals(entry.getString("source"))&&row.getString("url").equals(entry.getString("url"))){representative=true;check(row.getString("heat").equals(entry.getString("heat")),"representative retains raw source heat without addition");}}check(representative,"card representative is one of its original source entries");}}
            check(members.equals(new HashSet<>(ids)),"exact six-member source and URL set is conserved");
            String title="香港将上线网约车";String unaffected=string("SELECT group_concat(event_id,'|') FROM (SELECT event_id FROM snapshot_rows WHERE snapshot="+frozen+" AND json_extract(payload,'$.title')!='"+title+"' ORDER BY position)");waitText(title,10000);screenshot("release-short-headlines.png");
            ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isLongClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(card==null||!card.performLongClick())throw new IllegalStateException("No real card menu");});menu("查看各来源");waitText("选择阅读来源",5000);
            check(!matching(automation().getRootInActiveWindow(),"微博 · "+title).isEmpty()&&!matching(automation().getRootInActiveWindow(),"百度 · "+title).isEmpty(),"actual source chooser exposes both original platform entries");menu("关闭");
            ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isLongClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(card==null||!card.performLongClick())throw new IllegalStateException("No real split menu");});menu("分开显示这些来源");
            until=SystemClock.elapsedRealtime()+10000;while(scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")==1,"real split menu persists one explicit separation feedback");
            until=SystemClock.elapsedRealtime()+10000;while(active()==frozen&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(active()!=frozen&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())==4,"explicit split immediately displays separate cards without a second confirmation");
            check(unaffected.equals(string("SELECT group_concat(event_id,'|') FROM (SELECT event_id FROM snapshot_rows WHERE snapshot="+active()+" AND json_extract(payload,'$.title')!='"+title+"' ORDER BY position)")),"explicit split preserves the relative order of unaffected paired cards");
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==3,"original frozen snapshot remains immutable after explicit split");
            screenshot("release-short-headlines-split.png");
            check(scalar("SELECT COUNT(*) FROM feedback WHERE action IN ('READ','QUICK_EXIT','MORE','LESS')")==0,"card menus and split do not invent content or platform interest");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{restoreReplayScreens();ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private AccessibilityNodeInfo accessibleButton(String name)throws Exception{
        AccessibilityNodeInfo target=null;long until=SystemClock.elapsedRealtime()+5000;
        while(target==null&&SystemClock.elapsedRealtime()<until){
            for(AccessibilityNodeInfo node:matching(automation().getRootInActiveWindow(),name)){
                if(!node.isVisibleToUser()||!node.isClickable())continue;
                CharSequence description=node.getContentDescription(),text=node.getText();
                if(name.contentEquals(description==null?"":description)||name.contentEquals(text==null?"":text)){target=node;break;}
            }
            if(target==null)Thread.sleep(50);
        }
        if(target==null)throw new IllegalStateException("Accessible button missing: "+name);
        Rect bounds=new Rect();target.getBoundsInScreen(bounds);float density=context().getResources().getDisplayMetrics().density;
        check("android.widget.Button".contentEquals(target.getClassName())&&target.isFocusable(),"connected button role and keyboard focus: "+name);
        check(bounds.width()>=48*density-1&&bounds.height()>=48*density-1,"connected action has visible 48dp target: "+name);
        check(target.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS),"connected accessibility focus accepted: "+name);
        target.performAction(AccessibilityNodeInfo.ACTION_CLEAR_ACCESSIBILITY_FOCUS);
        return target;
    }
    /** Exact public appointment pair through minified cold feed and real menus; no production reflection. */
    void runAppointmentEvent(){
        android.accessibilityservice.AccessibilityServiceInfo priorAccessibility=null;
        try{
            if(!"app.quietreader.discoveryprobe".equals(context().getPackageName()))throw new IllegalStateException("Isolated discovery target required");
            priorAccessibility=automation().getServiceInfo();
            android.accessibilityservice.AccessibilityServiceInfo exploration=automation().getServiceInfo();
            exploration.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            automation().setServiceInfo(exploration);
            android.view.accessibility.AccessibilityManager accessibility=(android.view.accessibility.AccessibilityManager)context().getSystemService(Context.ACCESSIBILITY_SERVICE);
            long focusDeadline=SystemClock.elapsedRealtime()+5000;while(!accessibility.isTouchExplorationEnabled()&&SystemClock.elapsedRealtime()<focusDeadline)Thread.sleep(50);
            check(accessibility.isTouchExplorationEnabled(),"touch exploration enabled for actual accessibility focus checks");
            if(!accessibility.isTouchExplorationEnabled())throw new IllegalStateException("Touch exploration unavailable");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"appointment test uses minified nondebuggable target");
            holdReplayScreen=true;launchReplay(false);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-fresh-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            seedFeedback=new JSONArray();JSONArray pairs=fixture.getJSONArray("pairs");
            for(int n=0;n<pairs.length();n++){JSONObject pair=pairs.getJSONObject(n);if(!pair.getJSONObject("left").getString("title").contains("总导演"))continue;
                if(!"same".equals(pair.getString("label")))throw new IllegalStateException("Fixture label changed");
                for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);candidates.add(value);ids.add(hash(value.getString("source")+"\n"+value.getString("url")));hashes.add(hash("hot-title-v2\n"+value.getString("title")+"\n"+value.optString("snippet")));}
            }
            check(candidates.size()==2,"exact two recorded director records retained");persistSeed(System.currentTimeMillis(),true,true);launchReplay(false);
            long until=SystemClock.elapsedRealtime()+10000;while(active()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long frozen=active();
            check(frozen>0&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==1,"minified cold feed groups exact appointment into one event");
            JSONObject row=new JSONObject(string("SELECT payload FROM snapshot_rows WHERE snapshot="+frozen+" LIMIT 1"));JSONArray entries=row.getJSONArray("groupEntries");
            Set<String> members=new HashSet<>();boolean representative=false;
            for(int n=0;n<entries.length();n++){JSONObject entry=entries.getJSONObject(n);members.add(hash(entry.getString("source")+"\n"+entry.getString("url")));if(row.getString("source").equals(entry.getString("source"))&&row.getString("url").equals(entry.getString("url"))){representative=true;check(row.getString("heat").equals(entry.getString("heat")),"appointment representative keeps source heat without addition");}}
            check(entries.length()==2&&members.equals(new HashSet<>(ids))&&representative,"appointment retains both exact source identities and valid representative");
            String title=row.getString("title");waitText(title,10000);
            accessibleButton("刷新");
            check(accessibleButton("设置").performAction(AccessibilityNodeInfo.ACTION_CLICK),"connected settings action opens settings");test.waitForIdleSync();waitText("阅读外观",5000);
            check(accessibleButton("返回").performAction(AccessibilityNodeInfo.ACTION_CLICK),"connected back action restores feed");test.waitForIdleSync();waitText(title,5000);
            check(accessibleButton("话题选项："+title).performAction(AccessibilityNodeInfo.ACTION_CLICK),"connected card options action opens source menu");test.waitForIdleSync();menu("查看各来源");waitText("选择阅读来源",5000);
            for(JSONObject candidate:candidates)check(!matching(automation().getRootInActiveWindow(),candidate.getString("title")).isEmpty(),"appointment source chooser exposes original title: "+candidate.getString("source"));menu("关闭");
            ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isLongClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(card==null||!card.performLongClick())throw new IllegalStateException("Missing split menu");});menu("分开显示这些来源");
            until=SystemClock.elapsedRealtime()+10000;while(active()==frozen&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(active()!=frozen&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())==2,"appointment manual split immediately produces two cards");
            check(scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")==1,"appointment split persists one explicit separation");
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==1,"appointment original snapshot remains frozen");
            check(scalar("SELECT COUNT(*) FROM feedback WHERE action IN ('READ','QUICK_EXIT','MORE','LESS')")==0,"appointment menus do not invent interest feedback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{restoreReplayScreens();ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();if(priorAccessibility!=null)automation().setServiceInfo(priorAccessibility);}
        report.append("scope=exact recorded appointment regression through R8 UI; not independent quality, account or ARM validation\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    /** Actual R8 Controller, models, acceptance and split for fixed stopped-action records. */
    /** Synthetic qualified assertions plus retained public brand controls; no seeded scores. */
    void runQualifiedApologyEvents(){runQualifiedApologyEvents(false);}
    void runQuestionFrameEvents(){runQualifiedApologyEvents(true);}
    private void runQualifiedApologyEvents(boolean questionFrame){
        try{
            check("app.quietreader.discoveryprobe".equals(context().getPackageName())&&Build.HARDWARE.equals("ranchu"),"qualified apology replay is confined to the isolated emulator");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"qualified apology uses actual nondebuggable R8 target");
            holdReplayScreen=true;launchReplay(false);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            JSONObject window,independent;
            try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-new-window-20261028.json")){window=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-independent-20261027.json")){independent=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();
            for(int index:new int[]{2,6})for(String side:List.of("left","right")){JSONObject c=independent.getJSONArray("pairs").getJSONObject(index).getJSONObject(side);unique.put(hash(c.getString("source")+"\n"+c.getString("url")),c);}
            JSONObject current=window.getJSONArray("pairs").getJSONObject(2).getJSONObject("right");
            check("皇马官方就姆巴佩言论致歉".equals(current.getString("title"))&&unique.size()==3,"retained current title and unrelated brand controls have exact identities");
            unique.put(hash(current.getString("source")+"\n"+current.getString("url")),current);
            for(String qualifier:questionFrame?List.of("去年","是否","曾"):List.of("去年","是否")){
                String title="皇马"+qualifier+"就姆巴佩言论道歉";
                JSONObject c=new JSONObject(current.toString()).put("title",questionFrame?"如何看待"+title+"？":title).put("url","https://example.org/qualified-apology/"+(qualifier.equals("去年")?"past":qualifier.equals("曾")?"former":"question"));
                unique.put(hash(c.getString("source")+"\n"+c.getString("url")),c);
            }
            Set<String> completeApology=new HashSet<>();completeApology.add(hash(current.getString("source")+"\n"+current.getString("url")));
            if(questionFrame){JSONObject longer=window.getJSONArray("pairs").getJSONObject(2).getJSONObject("left");String id=hash(longer.getString("source")+"\n"+longer.getString("url"));unique.put(id,longer);completeApology.add(id);}
            int inputCount=questionFrame?8:6,expectedRows=questionFrame?6:5,expectedApologyCards=questionFrame?4:3;
            seedFeedback=new JSONArray();for(Map.Entry<String,JSONObject> entry:unique.entrySet()){JSONObject c=entry.getValue();candidates.add(c);ids.add(entry.getKey());hashes.add(hash("hot-title-v2\n"+c.getString("title")+"\n"+c.optString("snippet")));}
            check(candidates.size()==inputCount,"exact input count includes brand controls and separate qualified assertions");persistSeed(System.currentTimeMillis(),true,true);launchReplay(false);
            long until=SystemClock.elapsedRealtime()+10000;while(active()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long frozen=active();
            check(frozen>0&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==inputCount,"all original cards are visible before actual models");
            requestRanking();until=SystemClock.elapsedRealtime()+60000;long prepared=0;
            while(SystemClock.elapsedRealtime()<until){
                prepared=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots WHERE id>"+frozen+" AND source_version=(SELECT value FROM meta WHERE key='sources') AND profile_version=(SELECT value FROM meta WHERE key='profile')");
                if(prepared>0&&"本轮推荐已完成".equals(replayRecommendationState())&&scalar("SELECT COUNT(*) FROM reranks")>=2)break;
                Thread.sleep(100);
            }
            long rows=prepared==0?0:scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+prepared);
            report.append("QUALIFIED_MODEL state=").append(replayRecommendationState()).append(" vectors=").append(scalar("SELECT COUNT(*) FROM embeddings")).append(" scores=").append(scalar("SELECT COUNT(*) FROM reranks")).append(" preparedRows=").append(rows).append('\n');
            check("本轮推荐已完成".equals(replayRecommendationState())&&scalar("SELECT COUNT(*) FROM embeddings")>=inputCount&&scalar("SELECT COUNT(*) FROM reranks")>=2,"actual models finish all inputs and verify the unrelated brand pair");
            check(rows==expectedRows,"completed model snapshot groups only equivalent assertions and keeps qualifiers separate");
            check(active()==frozen,"completed verification keeps visible list frozen before acceptance");waitText("查看更新",5000);click("查看更新");
            until=SystemClock.elapsedRealtime()+10000;while(active()==frozen&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long accepted=active();
            check(accepted!=frozen&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+accepted)==expectedRows,"real update action accepts expected events");
            Set<String> members=new HashSet<>();int apologyCards=0,brandGroups=0,apologyGroups=0;String splitApologyTitle="";
            try(SQLiteDatabase db=db();Cursor cursor=db.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=?",new String[]{Long.toString(accepted)})){
                while(cursor.moveToNext()){JSONObject row=new JSONObject(cursor.getString(0));JSONArray entries=row.optJSONArray("groupEntries");if(entries==null)entries=new JSONArray().put(row);
                    if(row.getString("title").contains("皇马")){apologyCards++;
                        if(questionFrame&&entries.length()==2){Set<String> actual=new HashSet<>();for(int n=0;n<entries.length();n++){JSONObject entry=entries.getJSONObject(n);actual.add(hash(entry.getString("source")+"\n"+entry.getString("url")));if(entry.getString("title").equals(row.getString("title")))check(entry.getString("heat").equals(row.getString("heat")),"question-frame group does not sum heat");}check(actual.equals(completeApology),"only the observed long/short current apology pair is grouped");apologyGroups++;splitApologyTitle=row.getString("title");}
                        else check(entries.length()==1,"each qualified apology retains its own card");
                    }
                    if(entries.length()==2&&row.getString("title").contains("东鹏"))brandGroups++;
                    for(int n=0;n<entries.length();n++){JSONObject entry=entries.getJSONObject(n);check(members.add(hash(entry.getString("source")+"\n"+entry.getString("url"))),"each exact source identity appears once");}
                }
            }
            check(apologyCards==expectedApologyCards&&brandGroups==1&&apologyGroups==(questionFrame?1:0)&&members.equals(new HashSet<>(ids)),"acceptance conserves every source identity and all distinct assertions");
            check(scalar("SELECT COUNT(*) FROM feedback")==0,"verification and acceptance invent no user preferences");
            if(questionFrame){final String title=splitApologyTitle;if(title.isEmpty())throw new IllegalStateException("Expected current apology group is absent; cannot test its split");waitText(title,5000);ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isLongClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(card==null||!card.performLongClick())throw new IllegalStateException("No question-frame split menu");});menu("分开显示这些来源");until=SystemClock.elapsedRealtime()+10000;while(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())!=expectedRows+1&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())==expectedRows+1&&scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")==1,"real split restores long and short apology separately");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{restoreReplayScreens();ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("scope=synthetic qualified apology assertions and retained development brand controls; actual R8 controller/models/acceptance, not independent quality or ARM resources\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    /** Retained public five-source cluster; actual directional scores, never injected. */
    void runConfidenceEventGroups(){
        try{
            check("app.quietreader.discoveryprobe".equals(context().getPackageName())&&Build.HARDWARE.equals("ranchu"),"confidence replay is confined to the isolated emulator");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"confidence replay uses actual nondebuggable R8 target");
            holdReplayScreen=true;launchReplay(false);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-full-window-20261028.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            String plain="皇马就姆巴佩言论道歉",official="皇马官方就姆巴佩言论致歉",detail="皇马就姆巴佩错误言论道歉",question="如何看待皇马官方致歉，称「就姆巴佩近期言论对中国人民造成的不适表达歉意」？";
            Set<String> titles=Set.of(plain,official,detail,question),firstGroup=new HashSet<>(),secondGroup=new HashSet<>();seedFeedback=new JSONArray();
            JSONArray pool=fixture.getJSONArray("candidatePool");
            for(int n=0;n<pool.length();n++){JSONObject c=pool.getJSONObject(n);if(!titles.contains(c.getString("title")))continue;String id=hash(c.getString("source")+"\n"+c.getString("url"));candidates.add(c);ids.add(id);hashes.add(hash("hot-title-v2\n"+c.getString("title")+"\n"+c.optString("snippet")));(c.getString("title").equals(plain)||c.getString("title").equals(detail)?firstGroup:secondGroup).add(id);}
            check(candidates.size()==5&&firstGroup.size()==3&&secondGroup.size()==2,"five exact retained source identities cover four title variants");
            persistSeed(System.currentTimeMillis(),true,true);launchReplay(false);long until=SystemClock.elapsedRealtime()+10000;
            while(active()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long frozen=active();
            check(frozen>0&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==4,"cold source identity grouping retains four title variants");
            requestRanking();until=SystemClock.elapsedRealtime()+60000;long prepared=0;
            while(SystemClock.elapsedRealtime()<until){prepared=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots WHERE id>"+frozen+" AND source_version=(SELECT value FROM meta WHERE key='sources') AND profile_version=(SELECT value FROM meta WHERE key='profile')");if(prepared>0&&"本轮推荐已完成".equals(replayRecommendationState()))break;Thread.sleep(100);}
            long count=prepared==0?0:scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+prepared);report.append("CONFIDENCE_MODEL rows=").append(count).append(" vectors=").append(scalar("SELECT COUNT(*) FROM embeddings")).append(" scores=").append(scalar("SELECT COUNT(*) FROM reranks")).append(" state=").append(replayRecommendationState()).append('\n');
            check(count==2&&"本轮推荐已完成".equals(replayRecommendationState())&&scalar("SELECT COUNT(*) FROM embeddings")>=4&&scalar("SELECT COUNT(*) FROM reranks")>=2,"actual model evidence prepares two complete groups instead of three order-dependent fragments");
            if(count!=2)throw new IllegalStateException("Expected strongest complete groups absent");check(active()==frozen,"model completion leaves current cards frozen until explicit acceptance");
            waitText("查看更新",5000);click("查看更新");until=SystemClock.elapsedRealtime()+10000;while(active()==frozen&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long accepted=active();
            check(accepted!=frozen&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+accepted)==2,"real update action accepts the two prepared groups");
            Set<String> all=new HashSet<>();String splitTitle="";int verified=0;
            try(SQLiteDatabase db=db();Cursor cursor=db.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=?",new String[]{Long.toString(accepted)})){
                while(cursor.moveToNext()){JSONObject row=new JSONObject(cursor.getString(0));JSONArray entries=row.getJSONArray("groupEntries");Set<String> group=new HashSet<>();boolean representative=false;
                    for(int n=0;n<entries.length();n++){JSONObject c=entries.getJSONObject(n);String id=hash(c.getString("source")+"\n"+c.getString("url"));check(all.add(id),"accepted source identity occurs exactly once");group.add(id);if(row.getString("source").equals(c.getString("source"))&&row.getString("url").equals(c.getString("url"))){representative=true;check(row.getString("heat").equals(c.getString("heat")),"group heat remains its representative source heat");}}
                    check(representative&&(group.equals(firstGroup)||group.equals(secondGroup)),"all members belong to one expected complete-evidence group");verified++;if(group.equals(secondGroup))splitTitle=row.getString("title");
                }
            }
            check(verified==2&&all.equals(new HashSet<>(ids)),"all five source identities survive the two-group presentation");check(scalar("SELECT COUNT(*) FROM feedback")==0,"grouping and acceptance do not invent preferences");
            final String title=splitTitle;if(title.isEmpty())throw new IllegalStateException("Expected long/short group absent for split");waitText(title,5000);
            ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isLongClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(card==null||!card.performLongClick())throw new IllegalStateException("No confidence split menu");});menu("分开显示这些来源");until=SystemClock.elapsedRealtime()+10000;while(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())!=3&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())==3&&scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")==1,"manual split restores only the selected two-member group");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{restoreReplayScreens();ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("scope=retained development cluster, actual R8 models/controller/UI; not independent quality, full-pool budget or ARM resources\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private enum EventFixture { STOPPED, GRAMMAR, JUDGMENT, APOLOGY, WEALTH, RELEASE, PRODUCT_ATTRIBUTES, PRODUCT_PROMPT, RATE_DECISION, PASSING, STATEMENT, AFFILIATION, AIRSPACE }
    void runStoppedEvents(){runEventFixture(EventFixture.STOPPED);}
    void runStoppedEvents(boolean grammar){runEventFixture(grammar?EventFixture.GRAMMAR:EventFixture.STOPPED);}
    void runJudgmentEvents(){runEventFixture(EventFixture.JUDGMENT);}
    void runApologyEvents(){runEventFixture(EventFixture.APOLOGY);}
    void runPassingEvents(){runEventFixture(EventFixture.PASSING);}
    void runStatementEvents(){runEventFixture(EventFixture.STATEMENT);}
    void runWealthEvents(){runEventFixture(EventFixture.WEALTH);}
    void runReleaseSpecificationEvents(){runEventFixture(EventFixture.RELEASE);}
    void runProductAttributeEvents(){runEventFixture(EventFixture.PRODUCT_ATTRIBUTES);}
    void runProductPromptEvents(){runEventFixture(EventFixture.PRODUCT_PROMPT);}
    void runRateDecisionEvents(){runEventFixture(EventFixture.RATE_DECISION);}
    void runDirectRelationEvents(boolean airspace){runEventFixture(airspace?EventFixture.AIRSPACE:EventFixture.AFFILIATION);}
    private void runEventFixture(EventFixture fixtureKind){
        boolean grammar=fixtureKind==EventFixture.GRAMMAR,judgment=fixtureKind==EventFixture.JUDGMENT,apology=fixtureKind==EventFixture.APOLOGY,wealth=fixtureKind==EventFixture.WEALTH,release=fixtureKind==EventFixture.RELEASE,passing=fixtureKind==EventFixture.PASSING,statement=fixtureKind==EventFixture.STATEMENT;
        boolean affiliation=fixtureKind==EventFixture.AFFILIATION,airspace=fixtureKind==EventFixture.AIRSPACE,productAttributes=fixtureKind==EventFixture.PRODUCT_ATTRIBUTES,productPrompt=fixtureKind==EventFixture.PRODUCT_PROMPT,rateDecision=fixtureKind==EventFixture.RATE_DECISION;
        try{
            if(!"app.quietreader.discoveryprobe".equals(context().getPackageName()))throw new IllegalStateException("Isolated discovery target required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"stopped events use minified nondebuggable target");
            holdReplayScreen=true;launchReplay(false);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-independent-20261027.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            LinkedHashMap<String,JSONObject> unique=new LinkedHashMap<>();
            for(int index:new int[]{1,2,6})for(String side:List.of("left","right")){JSONObject value=fixture.getJSONArray("pairs").getJSONObject(index).getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}
            if(affiliation||airspace||productAttributes||productPrompt||rateDecision){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject window;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-independent-window-20261029.json")){window=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                JSONObject pair=window.getJSONArray("pairs").getJSONObject(rateDecision?4:productPrompt?3:productAttributes?2:airspace?6:9);
                check("same".equals(pair.getString("label"))&&pair.getJSONObject("left").getString("title").contains(rateDecision?"维持":productAttributes||productPrompt?"MacBook Pro":airspace?"关闭领空":"加盟南京大学"),"selected event uses its exact frozen public pair without injected scores");
                for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}
                check(unique.size()==5,"direct relation retains the unrelated brand pair and singleton");
            }
            if(statement){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject window;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-new-window-20261028.json")){window=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                JSONObject pair=window.getJSONArray("pairs").getJSONObject(3);
                check("same".equals(pair.getString("label"))&&"央视体育青少中心：不认可皇马的表态，在道歉没有到位之前一切免谈".equals(pair.getJSONObject("left").getString("title"))&&"总台体育青少中心发言人：不认可皇马的表态".equals(pair.getJSONObject("right").getString("title")),"statement branch retains exact frozen public pair and its own source metadata without seeded scores");
                for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}
                check(unique.size()==5,"statement branch retains the unrelated brand pair and separate brand");
            }
            if(passing){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject window;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-new-window-20261028.json")){window=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                JSONObject pair=window.getJSONArray("pairs").getJSONObject(0);
                check("same".equals(pair.getString("label"))&&"刘延东逝世".equals(pair.getJSONObject("left").getString("title"))&&"刘延东同志逝世".equals(pair.getJSONObject("right").getString("title")),"passing branch retains exact frozen public courtesy-title pair without seeded scores");
                for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}
                check(unique.size()==5,"passing branch retains the unrelated brand pair and separate brand");
            }
            if(grammar){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject residual;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-residual-20261027.json")){residual=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                boolean found=false;JSONArray pairs=residual.getJSONArray("pairs");
                for(int n=0;n<pairs.length();n++){JSONObject pair=pairs.getJSONObject(n);if(pair.getJSONObject("left").getString("title").contains("叫停")&&pair.getJSONObject("right").getString("title").contains("涉及")){
                    for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}found=true;break;
                }}
                check(found&&unique.size()==5,"grammar branch uses frozen public involved-broadcast titles without synthetic scores");
            }
            if(judgment){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject later;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-later-window-20261027.json")){later=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                JSONObject court=later.getJSONArray("pairs").getJSONObject(1);
                for(String side:List.of("left","right")){JSONObject value=court.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}
                check(unique.size()==5,"judgment branch uses frozen public court titles, including an omitted role, without seeded scores");
            }
            if(apology){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject evening;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-evening-window-20261027.json")){evening=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                JSONObject pair=evening.getJSONArray("pairs").getJSONObject(3);
                check("西甲官方向中国球迷致歉".equals(pair.getJSONObject("left").getString("title"))&&"西甲官方：代表联盟所有俱乐部向中国球迷致以诚挚的歉意".equals(pair.getJSONObject("right").getString("title")),"apology branch uses exact frozen public titles without seeded scores");
                for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}
                check(unique.size()==5,"apology branch retains the independent brand pair and singleton");
            }
            if(wealth){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject residual;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-residual-20261027.json")){residual=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                boolean found=false;JSONArray pairs=residual.getJSONArray("pairs");
                for(int n=0;n<pairs.length();n++){JSONObject pair=pairs.getJSONObject(n);if(pair.getJSONObject("left").getString("title").equals("张一鸣6700亿刷新中国首富财富纪录")&&pair.getJSONObject("right").getString("title").equals("张一鸣刷新中国首富财富纪录")){
                    if(!pair.getString("label").equals("same"))throw new IllegalStateException("Wealth fixture label changed");
                    for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}found=true;break;
                }}
                check(found&&unique.size()==5,"wealth branch uses exact frozen public amount-enriched pair without seeded scores");
            }
            if(release){
                unique.entrySet().removeIf(entry->entry.getValue().optString("title").contains("叫停"));
                JSONObject residual;try(InputStream in=test.getContext().getAssets().open("fixtures/observed-event-residual-20261027.json")){residual=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
                boolean found=false;JSONArray pairs=residual.getJSONArray("pairs");
                for(int n=0;n<pairs.length();n++){JSONObject pair=pairs.getJSONObject(n);if(pair.getJSONObject("left").getString("title").equals("5499 元起：苹果发布 iPad mini 8 平板，8.5 寸 60Hz OLED 屏、A20 Pro 芯片、支持 IP67 防尘防水")&&pair.getJSONObject("right").getString("title").equals("苹果 iPad mini 8 发布，首搭 OLED 屏幕，起售价 5499 元，还有哪些亮点值得关注？")){
                    if(!pair.getString("label").equals("same"))throw new IllegalStateException("Release fixture label changed");
                    for(String side:List.of("left","right")){JSONObject value=pair.getJSONObject(side);unique.put(hash(value.getString("source")+"\n"+value.getString("url")),value);}found=true;break;
                }}
                check(found&&unique.size()==5,"release branch uses exact frozen public parameter-enriched pair without seeded scores");
            }
            seedFeedback=new JSONArray();for(Map.Entry<String,JSONObject> entry:unique.entrySet()){JSONObject value=entry.getValue();candidates.add(value);ids.add(entry.getKey());hashes.add(hash("hot-title-v2\n"+value.getString("title")+"\n"+value.optString("snippet")));}
            check(candidates.size()==5,"five exact records contain two same-event pairs and a separate brand");persistSeed(System.currentTimeMillis(),true,true);launchReplay(false);
            long until=SystemClock.elapsedRealtime()+10000;while(active()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long frozen=active();
            check(frozen>0&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==5,"cold list keeps all five cards before actual model verification");
            requestRanking();until=SystemClock.elapsedRealtime()+60000;long prepared=0;
            while(SystemClock.elapsedRealtime()<until){prepared=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots WHERE id>"+frozen+" AND source_version=(SELECT value FROM meta WHERE key='sources') AND profile_version=(SELECT value FROM meta WHERE key='profile') AND (SELECT COUNT(*) FROM snapshot_rows WHERE snapshot=snapshots.id)=3");if(prepared>0&&scalar("SELECT COUNT(*) FROM reranks")>0)break;Thread.sleep(100);}
            long vectorCount=scalar("SELECT COUNT(*) FROM embeddings"),scoreCount=scalar("SELECT COUNT(*) FROM reranks");
            report.append("EVENT_MODEL vectors=").append(vectorCount).append(" scores=").append(scoreCount).append(" prepared=").append(prepared).append('\n');
            // The affiliation pair deliberately has identical model text; its
            // two source identities share one vector, without seeded outputs.
            // The two complete rate inputs are identical after guarded focus;
            // both directions legitimately share one score cache entry.
            check(prepared>0&&vectorCount>=(affiliation?4:5)&&scoreCount>=(rateDecision?3:4),"actual R8 models prepare two verified groups with persisted vector and directional score caches");
            if(prepared<=0)throw new IllegalStateException("Stopped-action verified snapshot deadline");
            check(active()==frozen,"model completion leaves five visible cards frozen until acceptance");waitText("查看更新",5000);click("查看更新");
            until=SystemClock.elapsedRealtime()+10000;while(active()==frozen&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long accepted=active();
            check(accepted!=frozen&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+accepted)==3,"real update click accepts three stopped-action events");
            Set<String> members=new HashSet<>();String splitTitle="";int groups=0,solo=0;
            try(SQLiteDatabase db=db();Cursor cursor=db.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=?",new String[]{Long.toString(accepted)})){
                while(cursor.moveToNext()){JSONObject row=new JSONObject(cursor.getString(0));JSONArray entries=row.optJSONArray("groupEntries");if(entries==null)entries=new JSONArray().put(row);if(entries.length()==2){groups++;if(row.getString("title").contains(rateDecision?"维持":productAttributes||productPrompt?"MacBook Pro":affiliation?"加盟南京大学":airspace?"关闭领空":statement?"体育青少中心":passing?"刘延东":release?"iPad mini 8":wealth?"财富纪录":apology?"西甲官方":judgment?"受贿":"叫停"))splitTitle=row.getString("title");}else solo++;
                    Set<String> sourceActors=new HashSet<>();boolean representative=false;
                    for(int n=0;n<entries.length();n++){JSONObject entry=entries.getJSONObject(n);check(members.add(hash(entry.getString("source")+"\n"+entry.getString("url"))),"stopped-action member appears once");sourceActors.add(entry.getString("title").contains("蒙牛")?"brand-b":entry.getString("title").contains("东鹏")?"brand-a":"primary-event");if(row.getString("source").equals(entry.getString("source"))&&row.getString("url").equals(entry.getString("url"))){representative=true;check(row.getString("heat").equals(entry.getString("heat")),"stopped-action representative heat is not summed");}}
                    check(representative&&sourceActors.size()==1,"each event has one subject and a real source representative");
                }
            }
            check(groups==2&&solo==1&&members.equals(new HashSet<>(ids)),"two groups and separate brand conserve exact five source identities");
            if(splitTitle.isEmpty())throw new IllegalStateException("Primary verified group missing");final String title=splitTitle;waitText(title,5000);
            ui(()->{View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isLongClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(card==null||!card.performLongClick())throw new IllegalStateException("No stopped-action split menu");});menu("分开显示这些来源");
            until=SystemClock.elapsedRealtime()+10000;while((active()==accepted||scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())!=4||scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")!=1)&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            report.append("STOPPED_SPLIT active=").append(active()).append(" accepted=").append(accepted).append(" rows=").append(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())).append(" feedback=").append(scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")).append('\n');
            check(active()!=accepted&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active())==4&&scalar("SELECT COUNT(*) FROM feedback WHERE action='SEPARATE'")==1,"real split restores two primary-event cards without merging the other brand");
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active()+" AND json_array_length(payload,'$.groupEntries')=2 AND json_extract(payload,'$.title') LIKE '%东鹏%'")==1&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active()+" AND json_array_length(payload,'$.groupEntries')>1 AND json_extract(payload,'$.title') NOT LIKE '%东鹏%'")==0,"unaffected accepted brand group survives while primary-event pair stays split");
            long frozenExists=scalar("SELECT COUNT(*) FROM snapshots WHERE id="+frozen),acceptedExists=scalar("SELECT COUNT(*) FROM snapshots WHERE id="+accepted);
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen)==(frozenExists==0?0:5)&&scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+accepted)==(acceptedExists==0?0:3),"retained earlier snapshots keep their row counts; bounded eviction may remove old snapshots");
            check(scalar("SELECT COUNT(*) FROM feedback WHERE action IN ('READ','QUICK_EXIT','MORE','LESS')")==0,"stopped-action acceptance and menus do not invent preferences");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');dump(automation().getRootInActiveWindow(),0);try{screenshot("release-event-"+fixtureKind.name().toLowerCase(Locale.ROOT)+"-failure.png");}catch(Exception ignored){}}
        finally{restoreReplayScreens();ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("scope=fixed ").append(fixtureKind.name()).append(" event regression with actual R8 models and UI; not independent quality or ARM resources\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    /** Independent cache-key serialization for this already-clean public corpus; never seeds scores. */
    private String livePersonalRerankPredicate()throws Exception{
        String manifest;try(InputStream in=context().getAssets().open("local-ai-manifest.json")){manifest=new String(in.readAllBytes(),StandardCharsets.UTF_8);}
        String revision=hash(manifest);Set<String> keys=new LinkedHashSet<>();
        for(int n=0;n<seedFeedback.length();n++){
            String query=liveModelInput(seedFeedback.getJSONObject(n).getString("sample"),240),queryKey=hash("semantic-input-v1\n"+liveModelInput(query,1000));
            for(JSONObject candidate:candidates){String title=candidate.getString("title"),snippet=candidate.optString("snippet"),text=title+(snippet.isEmpty()||snippet.equals(title)?"":"\n"+snippet);String textKey=hash("semantic-input-v1\n"+liveModelInput(text,1000));keys.add(hash("rerank-pair-v1\n"+revision+"\nexamples-rrf-conflict-v8\n"+queryKey+"\n"+textKey));}
        }
        List<String> quoted=new ArrayList<>();for(String key:keys)quoted.add("'"+key+"'");
        return "key IN ("+String.join(",",quoted)+") AND logit=logit AND abs(logit)<1e30";
    }
    private static String liveModelInput(String text,int limit){String input=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFC).replaceAll("[\\s\\p{Z}]+"," ").trim();return input.codePointCount(0,input.length())>limit?input.substring(0,input.offsetByCodePoints(0,limit)):input;}
    /** Real ordinary controller -> real card -> uncached native article, using public APIs only. */
    void runLiveInterruption(Bundle args){
        LaunchTimeline timeline=new LaunchTimeline();Application application=(Application)context().getApplicationContext();boolean observing=false;
        try{
            if(!"app.quietreader.discoveryprobe".equals(context().getPackageName()))throw new IllegalStateException("Only isolated discoveryprobe allowed");
            String stage=args.getString("stage","embedding");if(!Arrays.asList("embedding","reranker").contains(stage))throw new IllegalArgumentException("Unknown model boundary");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"live interruption target is non-debuggable R8");
            context().getSharedPreferences("isolated-measurement",0).edit().putString("stages","DUAL").commit();
            holdReplayScreen=true;launchReplay(false);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            ReleaseLiveReaderProbe bodyProbe=new ReleaseLiveReaderProbe(test);JSONArray samples;
            String sampleAsset=args.getString("sampleAsset","release-live-bbc-20261026.json");
            if(!Arrays.asList("release-live-bbc-20261026.json","release-live-bbc-more-20261026.json").contains(sampleAsset))throw new IllegalArgumentException("Unknown public capture set");
            try(InputStream in=test.getContext().getAssets().open(sampleAsset)){samples=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getJSONArray("records");}
            JSONObject selected=null;
            for(int n=0;n<samples.length();n++){JSONObject sample=samples.getJSONObject(n);String url=sample.getString("url");if(!bodyProbe.cacheFile(url).exists()&&!context().getSharedPreferences("reading-history",0).contains(url)){selected=sample;break;}}
            if(selected==null)throw new IllegalStateException("No genuinely uncached public sample remains; do not delete cache to fabricate cold acquisition");
            uncachedLiveUrl=selected.getString("url");String title=selected.getString("title");JSONArray paragraphs=selected.getJSONArray("expectedParagraphs");
            JSONObject fixture;try(InputStream in=test.getContext().getAssets().open("energy-replay-100-v2.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
            seedFeedback=fixture.getJSONArray("developerProfile");JSONArray pool=fixture.getJSONArray("candidates");long now=System.currentTimeMillis();
            JSONObject target=new JSONObject().put("source","BBC").put("url",uncachedLiveUrl).put("title",title).put("snippet","").put("heat","").put("video",false).put("rank",1).put("publishedAt",JSONObject.NULL).put("observedAt",now);
            pool.put(0,target);
            for(int n=0;n<pool.length();n++){JSONObject value=pool.getJSONObject(n);candidates.add(value);ids.add(hash(value.getString("source")+"\n"+value.getString("url")));hashes.add(hash("hot-title-v2\n"+value.getString("title")+"\n"+value.optString("snippet")));}
            persistSeed(now,true,false);
            check(scalar("SELECT COUNT(*) FROM embeddings")==0&&scalar("SELECT COUNT(*) FROM reranks")==0,"live transition begins without any seeded model output");
            check(!bodyProbe.cacheFile(uncachedLiveUrl).exists()&&!context().getSharedPreferences("reading-history",0).contains(uncachedLiveUrl),"live target remains absent from disk and reading history after fixture setup");
            report.append("LIVE_OVERRIDE url=").append(uncachedLiveUrl).append(" paragraphs=").append(paragraphs.length()).append("; 99 synthetic cached bodies plus one real uncached article; developer interests only; no speculative prefetch; explicit production menu starts ranking\n");
            launchReplay(false);waitHome(10000);long until=SystemClock.elapsedRealtime()+10000;
            while(active()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            long frozen=active();String escaped=uncachedLiveUrl.replace("'","''");
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+frozen+" AND json_extract(payload,'$.url')='"+escaped+"'")==1,"ordinary frozen feed contains the exact real target once");
            int position=(int)scalar("SELECT position FROM snapshot_rows WHERE snapshot="+frozen+" AND json_extract(payload,'$.url')='"+escaped+"'");
            replayViewport(position,title);long feedbackBefore=scalar("SELECT COUNT(*) FROM feedback");requestRanking();
            String scorePredicate=stage.equals("reranker")?livePersonalRerankPredicate():"";
            String outputSql=stage.equals("reranker")?"SELECT COUNT(*) FROM reranks WHERE "+scorePredicate:"SELECT COUNT(*) FROM embeddings WHERE length(vector)=2048";
            boolean running=false;until=SystemClock.elapsedRealtime()+45000;
            while(SystemClock.elapsedRealtime()<until){if(scalar(outputSql)>0&&replayRecommendationState().equals("正在本机整理")&&!backgroundRankingPids().isEmpty()){running=true;break;}Thread.sleep(50);}
            check(running,"actual "+stage+" output is committed while normal controller reports running and ranking process is alive");
            if(!running)throw new IllegalStateException("No proven live model boundary");
            check(!bodyProbe.cacheFile(uncachedLiveUrl).exists(),"article is still uncached immediately before the card action");
            report.append("LIVE_MODEL_BOUNDARY stage=").append(stage).append(" relevantOutputs=").append(scalar(outputSql)).append(" vectors=").append(scalar("SELECT COUNT(*) FROM embeddings")).append(" rankingPids=").append(backgroundRankingPids());
            if(stage.equals("reranker"))report.append(" personalLogit=").append(string("SELECT printf('%.6f',logit) FROM reranks WHERE "+scorePredicate+" LIMIT 1"));report.append('\n');
            ui(()->application.registerActivityLifecycleCallbacks(timeline));observing=true;
            Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);long clicked=SystemClock.elapsedRealtime(),clickWall=System.currentTimeMillis();
            try{
                ui(()->{
                    View dot=find(home.getWindow().getDecorView(),"●");if(dot==null||!"正在本机整理".contentEquals(dot.getStateDescription()))throw new IllegalStateException("Model no longer running at the actual card boundary");
                    View card=find(home.getWindow().getDecorView(),title);if(card==null||!card.isShown())throw new IllegalStateException("Live card not visible after returning from settings");
                    while(!card.isClickable()&&card.getParent() instanceof View)card=(View)card.getParent();if(!card.isClickable())throw new IllegalStateException("Live card not clickable");
                    timeline.times.set(5,SystemClock.elapsedRealtime());card.performClick();timeline.times.set(6,SystemClock.elapsedRealtime());
                });
                reader=test.waitForMonitorWithTimeout(monitor,10000);
            }finally{test.removeMonitor(monitor);}
            check(reader!=null,"actual feed card launches the common reader");if(reader==null)throw new IllegalStateException("No reader Activity");holdReplayScreen(reader);
            JSONObject actual=new JSONObject(reader.getIntent().getStringExtra("discovery_candidate"));check(uncachedLiveUrl.equals(actual.getString("url")),"reader opens the selected source event rather than another card");
            until=SystemClock.elapsedRealtime()+5000;while(!backgroundRankingPids().isEmpty()&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            long retired=SystemClock.elapsedRealtime()-clicked;check(backgroundRankingPids().isEmpty(),"normal card navigation retires the model worker when opening the reader");
            WebView[] page={null};JSONObject observed=null;until=SystemClock.elapsedRealtime()+40000;
            while(SystemClock.elapsedRealtime()<until){ui(()->page[0]=view(reader.getWindow().getDecorView(),WebView.class));if(page[0]!=null){observed=bodyProbe.dom(page[0]);if(observed.optInt("parts")>0&&bodyProbe.matched(observed.optString("text"),paragraphs)==paragraphs.length())break;}Thread.sleep(100);}
            check(observed!=null&&observed.optInt("parts")>0&&bodyProbe.matched(observed.optString("text"),paragraphs)==paragraphs.length(),"real uncached article reaches unified DOM with every independent paragraph and tail");
            report.append("LIVE_TRANSITION modelRetirementObservedMs=").append(retired).append(" bodyObservedMs=").append(SystemClock.elapsedRealtime()-clicked).append(" timeline=").append(timeline.report(clicked,0,0)).append("; card performClick/host observation, not touch or presented-frame timing\n");
            File body=bodyProbe.cacheFile(uncachedLiveUrl);until=SystemClock.elapsedRealtime()+5000;while(!body.isFile()&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(body.isFile(),"real network acquisition persists through the ordinary body cache");
            JSONObject saved=new JSONObject(new String(java.nio.file.Files.readAllBytes(body.toPath()),StandardCharsets.UTF_8)),document=saved.getJSONObject("document");
            check(saved.getLong("at")>=clickWall&&uncachedLiveUrl.equals(document.getString("url"))&&document.getJSONArray("sections").getJSONObject(0).getString("id").startsWith("bbc-article-"),"newly acquired body has current time, exact URL and native BBC identity");
            check(backgroundRankingPids().isEmpty(),"model remains retired while actual article is visible");
            check(scalar("SELECT COUNT(*) FROM feedback")==feedbackBefore&&scalar("SELECT COUNT(*) FROM feedback WHERE action='READ'")==0,"opening and loading do not add implicit content or platform preferences");
            check(active()==frozen,"reading does not silently accept a pending reordered feed");
            long frameWait=bodyProbe.captureDrawnBody(page[0],"release-live-model-reading.png");
            check(true,"actual screenshot contains nonblank article pixels inside the WebView, excluding native bars");report.append("BODY_SCREENSHOT_AFTER_DOM waitMs=").append(frameWait).append("; screenshot sampling is test overhead, not presented-frame latency\n");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');try{screenshot("release-live-model-reading-failure.png");}catch(Exception ignored){}}
        finally{
            if(observing)ui(()->{timeline.removeDraw();application.unregisterActivityLifecycleCallbacks(timeline);});
            restoreReplayScreens();ui(()->{if(reader!=null&&!reader.isFinishing())reader.finish();if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();
            context().getSharedPreferences("isolated-measurement",0).edit().remove("stages").commit();
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void runReplaySetup(Bundle args){
        try{
            if(!isolatedReplayTarget())throw new IllegalStateException("Isolated replay target required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"replay target is an actual non-debuggable minified release");
            replayFixture=args.getString("fixture","energy-replay-100-v2.json");
            if(!Arrays.asList("energy-replay-100.json","energy-replay-100-v2.json").contains(replayFixture))throw new IllegalArgumentException("Unknown fixed replay corpus");
            holdReplayScreen=true;recordFirstReaderStacks="true".equals(args.getString("firstReaderStacks","false"));
            String network=args.getString("networkProfile","uncontrolled");
            replayNetworkAbsent(network);
            String stages=args.getString("stages","DUAL"),cache=args.getString("cache","cold");
            if(!Arrays.asList("NONE","EMBEDDING","DUAL").contains(stages)||!Arrays.asList("cold","warm").contains(cache))throw new IllegalArgumentException("Invalid replay mode");
            if(!context().getSharedPreferences("isolated-measurement",0).edit().putString("stages",stages).commit())throw new IOException("Stage setup failed");
            launchReplay();ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            JSONObject fixture;try(InputStream input=test.getContext().getAssets().open(replayFixture)){fixture=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
            seedFeedback=fixture.getJSONArray("developerProfile");
            JSONArray pool=fixture.getJSONArray("candidates");if(pool.length()!=100)throw new IllegalStateException("Replay requires exactly100 candidates");
            for(int n=0;n<pool.length();n++){JSONObject value=pool.getJSONObject(n);candidates.add(value);ids.add(hash(value.getString("source")+"\n"+value.getString("url")));hashes.add(hash("hot-title-v2\n"+value.getString("title")+"\n"+value.optString("snippet")));}
            Set<String> uniqueInputs=new HashSet<>();
            for(JSONObject candidate:candidates){String title=candidate.getString("title"),snippet=candidate.optString("snippet");uniqueInputs.add((title+(snippet.isEmpty()||snippet.equals(title)?"":"\n"+snippet)).replaceAll("[\\s\\p{Z}]+"," ").trim());}
            for(int n=0;n<seedFeedback.length();n++)uniqueInputs.add(seedFeedback.getJSONObject(n).getString("sample").replaceAll("[\\s\\p{Z}]+"," ").trim());
            int expectedVectors=uniqueInputs.size();
            report.append("REPLAY_UNIQUE_MODEL_INPUTS ").append(expectedVectors).append('\n');
            persistSeed(System.currentTimeMillis(),"cold".equals(cache),true);
            boolean verifyDiskReuse="true".equals(args.getString("verifyPrefetchDiskReuse","false"));
            Map<File,Long> originalBodies=new LinkedHashMap<>();
            if(verifyDiskReuse){
                if(!"true".equals(args.getString("activePrefetch","false")))throw new IllegalArgumentException("Disk reuse verification requires ordinary prefetch");
                String namespace=context().getSharedPreferences("article-cache",0).getString("namespace","initial");
                for(JSONObject candidate:candidates){String url=candidate.getString("url");String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie(url));
                    File body=new File(context().getCacheDir(),"article-bodies-v1/"+hash(namespace+"\n"+fixtureSession(url,cookie[0])+"\n"+url)+".json");originalBodies.put(body,android.system.Os.stat(body.getPath()).st_ino);
                }
                check(originalBodies.size()==100&&scalar("SELECT COUNT(*) FROM candidates WHERE json_extract(payload,'$.bodyLanguageAt')>0")==0,"disk-reuse fixture starts with100 bodies and no prepared body evidence");
            }
            report.append("REPLAY_SETUP stages=").append(stages).append(" cache=").append(cache).append(" corpus=").append(replayFixture).append("; public titles/synthetic cached bodies, no energy claim\n");
            boolean autonomous="true".equals(args.getString("autonomous","false"));
            if(autonomous&&!"true".equals(args.getString("activePrefetch","false")))throw new IllegalArgumentException("Autonomous proof requires ordinary active prefetch");
            long autonomousBefore=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots"),autonomousStarted=SystemClock.elapsedRealtime();
            launchReplay("true".equals(args.getString("activePrefetch","false")));waitHome(10000);
            long until=SystemClock.elapsedRealtime()+10000;
            while(SystemClock.elapsedRealtime()<until&&active()==0)Thread.sleep(100);
            check(scalar("SELECT COUNT(*) FROM candidates")==100,"replay seeds exactly100 fixed public candidates");
            long initialRows=scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+active());
            report.append("REPLAY_INITIAL_EVENTS ").append(initialRows).append("\n");
            check(initialRows==100,"production grouping retains exactly100 replay events");
            long frozenSnapshot=active();
            String frozenOrder=string("SELECT group_concat(event_id,'|') FROM (SELECT event_id FROM snapshot_rows WHERE snapshot="+frozenSnapshot+" ORDER BY position)");
            if("true".equals(args.getString("activePrefetch","false"))){
                boolean offlineProfile=network.equals("offline-cache");
                until=SystemClock.elapsedRealtime()+(offlineProfile?1000:25000);
                while(SystemClock.elapsedRealtime()<until&&(offlineProfile||scalar("SELECT COUNT(*) FROM candidates WHERE json_extract(payload,'$.bodyLanguageAt')>0")==0))Thread.sleep(100);
                long prepared=scalar("SELECT COUNT(*) FROM candidates WHERE json_extract(payload,'$.bodyLanguageAt')>0");
                report.append("PREFETCH_BEFORE_READER preparedEvidence=").append(prepared).append(" policy=").append(offlineProfile?"offline-paused":"online-enabled").append('\n');
                check(offlineProfile?prepared==0:prepared>0,offlineProfile?"ordinary foreground prefetch stays paused without a network":"ordinary foreground prefetch restores versioned cached bodies before any reader click");
                if(offlineProfile?prepared!=0:prepared==0)throw new IllegalStateException("Ordinary prefetch does not match network policy");
            }
            long before=autonomous?autonomousBefore:scalar("SELECT COALESCE(MAX(id),0) FROM snapshots");
            // Force a new measured controller generation even if automatic ranking
            // already completed this fixture before the explicit menu action.
            if(!autonomous){
                try(SQLiteDatabase db=db()){db.execSQL("UPDATE meta SET value=value+1 WHERE key='profile'");}
                requestRanking();
            }
            until=SystemClock.elapsedRealtime()+180000;
            boolean complete=false;int windows=autonomous?0:1;long acknowledged=before;String lastProgress="";
            while(SystemClock.elapsedRealtime()<until){
                long latest=scalar("SELECT COALESCE(MAX(id),0) FROM snapshots");
                long vectors=scalar("SELECT COUNT(*) FROM embeddings"),pairs=scalar("SELECT COUNT(*) FROM reranks");
                String phase=replayRecommendationState();
                String progress=phase+"|snapshot="+latest+"|vectors="+vectors+"|pairs="+pairs+"|source="+scalar("SELECT value FROM meta WHERE key='sources'")+"|prepared="+scalar("SELECT COUNT(*) FROM candidates WHERE json_extract(payload,'$.bodyLanguageAt')>0");
                if(!progress.equals(lastProgress)){report.append("AUTONOMY_PROGRESS ms=").append(SystemClock.elapsedRealtime()-autonomousStarted).append(' ').append(progress).append('\n');lastProgress=progress;}
                boolean current=scalar("SELECT COUNT(*) FROM snapshots WHERE id="+latest+" AND source_version=(SELECT value FROM meta WHERE key='sources') AND profile_version=(SELECT value FROM meta WHERE key='profile')")>0;
                current=current&&(!stages.equals("DUAL")||"personal".equals(string("SELECT mode FROM snapshots WHERE id="+latest)));
                complete=latest>before&&current&&phase.equals("本轮推荐已完成")&&(stages.equals("NONE")||vectors>=expectedVectors&&(stages.equals("EMBEDDING")||pairs>0));
                if(complete){report.append("REPLAY_COMPLETE_STATE snapshot=").append(latest).append(" state=").append(phase).append(" source=").append(scalar("SELECT value FROM meta WHERE key='sources'")).append(" profile=").append(scalar("SELECT value FROM meta WHERE key='profile'")).append('\n');break;}
                // A neutral/prefetch snapshot is not a finished model slice. Never
                // enter settings and cancel an in-flight computation to poll progress.
                if(!autonomous&&latest>acknowledged&&windows<6&&(phase.equals("本轮推荐部分完成")||phase.equals("本轮推荐已暂停"))){acknowledged=latest;windows++;requestRanking();}
                Thread.sleep(100);
            }
            report.append("REPLAY_EXPLICIT_WINDOWS ").append(windows).append('\n');
            if(autonomous){
                report.append("AUTONOMOUS_COMPLETION elapsedMs=").append(SystemClock.elapsedRealtime()-autonomousStarted).append("; zero manual recommendation requests; public titles/developer profile/synthetic cached prose\n");
                check(complete&&windows==0,"ordinary minified foreground completes current recommendation without any manual continuation");
                check(active()==frozenSnapshot&&frozenOrder.equals(string("SELECT group_concat(event_id,'|') FROM (SELECT event_id FROM snapshot_rows WHERE snapshot="+active()+" ORDER BY position)")),"autonomous pending result preserves the entire visible event order until explicit acceptance");
                long quietUntil=SystemClock.elapsedRealtime()+5000;
                while(SystemClock.elapsedRealtime()<quietUntil&&!backgroundRankingPids().isEmpty())Thread.sleep(100);
                check(backgroundRankingPids().isEmpty(),"autonomously completed model worker retires");
                long quietVectors=scalar("SELECT COUNT(*) FROM embeddings"),quietPairs=scalar("SELECT COUNT(*) FROM reranks");
                boolean quiet=true;quietUntil=SystemClock.elapsedRealtime()+15000;int samples=0;
                while(SystemClock.elapsedRealtime()<quietUntil){samples++;quiet&=backgroundRankingPids().isEmpty();Thread.sleep(100);}
                check(quiet&&scalar("SELECT COUNT(*) FROM embeddings")==quietVectors&&scalar("SELECT COUNT(*) FROM reranks")==quietPairs,"completed unchanged foreground has no observed model worker or cache growth for15 seconds");
                report.append("AUTONOMOUS_QUIET samples=").append(samples).append(" intervalMs=100; not continuous CPU or energy evidence\n");
            }
            click("设置");menu("高级与诊断");menu("本机推荐状态");dump(automation().getRootInActiveWindow(),0);menu("关闭");
            check(complete,"exact-current-version recommendation reaches actual completed state through ordinary release controller");
            if(!complete)throw new IllegalStateException("Recommendation completion not proved; cache existence is insufficient");
            long vectors=scalar("SELECT COUNT(*) FROM embeddings"),pairs=scalar("SELECT COUNT(*) FROM reranks");
            report.append("REPLAY_CACHE vectors=").append(vectors).append(" pairs=").append(pairs).append('\n');
            if("cold".equals(cache)&&stages.equals("NONE"))check(vectors==0&&pairs==0,"release NONE creates no model caches");
            if("cold".equals(cache)&&stages.equals("EMBEDDING"))check(vectors>=expectedVectors&&pairs==0,"release EMBEDDING creates vectors without rerank pairs");
            if(stages.equals("DUAL"))check(vectors>=expectedVectors&&pairs>0,"release DUAL executes both actual stages on fixed100 corpus");
            returnToFeed();
            if("energy-replay".equals(args.getString("phase")))replayReading(args,stages,cache);
            else if("energy-background".equals(args.getString("phase")))replayBackground(args,stages,cache);
            if(verifyDiskReuse){
                int unchanged=0;for(Map.Entry<File,Long> entry:originalBodies.entrySet())if(entry.getKey().isFile()&&android.system.Os.stat(entry.getKey().getPath()).st_ino==entry.getValue())unchanged++;
                report.append("PREFETCH_DISK_REUSE original=").append(originalBodies.size()).append(" unchanged=").append(unchanged).append('\n');
                check(unchanged==100,"ordinary prefetch and any reader opens in this phase preserve all100 existing body files without rewriting");
                if(autonomous&&"energy-replay-100-v2.json".equals(replayFixture)){
                    long prepared=scalar("SELECT COUNT(*) FROM candidates WHERE json_extract(payload,'$.bodyLanguageAt')>0");
                    report.append("PREFETCH_FULL_WINDOW preparedEvidence=").append(prepared).append(" expectedMinimum=32\n");
                    check(prepared>=32,"valid cached text corpus prepares the entire32-item forward window without a reader click");
                }
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{
            restoreReplayScreens();
            ui(()->{if(reader!=null&&!reader.isFinishing())reader.finish();if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();
            context().getSharedPreferences("isolated-measurement",0).edit().remove("stages").commit();
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private List<Integer> backgroundRankingPids(){
        android.app.ActivityManager manager=context().getSystemService(android.app.ActivityManager.class);
        List<android.app.ActivityManager.RunningAppProcessInfo> running=manager==null?null:manager.getRunningAppProcesses();
        if(running==null)throw new IllegalStateException("Background process list unavailable");
        boolean mainPresent=false;List<Integer> ranking=new ArrayList<>();
        for(android.app.ActivityManager.RunningAppProcessInfo process:running){
            if(process.pid==android.os.Process.myPid()&&process.processName.equals(context().getPackageName()))mainPresent=true;
            if(process.processName.equals(context().getPackageName()+":ranking"))ranking.add(process.pid);
        }
        if(!mainPresent)throw new IllegalStateException("Background main process missing from observation");
        Collections.sort(ranking);return ranking;
    }
    /** Actual system HOME/onStop, no force-stop or service rebinding to obtain a quiet result. */
    private void replayBackground(Bundle args,String stages,String cache)throws Exception{
        int seconds=Integer.parseInt(args.getString("replaySeconds","1800"));
        if(seconds<60||seconds>3000)throw new IllegalArgumentException("Invalid background duration");
        String run=args.getString("detachedRun",args.getString("replayRun","")),network=args.getString("networkProfile","uncontrolled");
        if(!run.matches("[a-z0-9][a-z0-9-]{0,39}"))throw new IllegalArgumentException("Invalid background identity");
        java.util.concurrent.atomic.AtomicBoolean stopped=new java.util.concurrent.atomic.AtomicBoolean(),returned=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicLong stoppedAt=new java.util.concurrent.atomic.AtomicLong(-1);
        List<String> lifecycleEvents=Collections.synchronizedList(new ArrayList<>());
        Application.ActivityLifecycleCallbacks lifecycle=new Application.ActivityLifecycleCallbacks(){
            public void onActivityStopped(Activity a){lifecycleEvents.add("stopped:"+a.getClass().getName()+":target="+(a==home));if(a==home){stoppedAt.compareAndSet(-1,SystemClock.elapsedRealtime());stopped.set(true);}}
            public void onActivityResumed(Activity a){lifecycleEvents.add("resumed:"+a.getClass().getName()+":target="+(a==home));if(a==home&&stopped.get())returned.set(true);}
            public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}
            public void onActivityPaused(Activity a){lifecycleEvents.add("paused:"+a.getClass().getName()+":target="+(a==home));}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        };
        Application application=home.getApplication();
        File file=new File(context().getExternalFilesDir(null),"device-run-"+run+"-background.ndjson");
        if(!file.createNewFile())throw new IOException("Background evidence already exists");
        byte[] fixture;try(InputStream input=test.getContext().getAssets().open(replayFixture)){fixture=input.readAllBytes();}
        restoreReplayScreens();
        List<Integer> before=backgroundRankingPids();long retainedSnapshot=active(),leftAt=SystemClock.elapsedRealtime();
        ui(()->application.registerActivityLifecycleCallbacks(lifecycle));
        try(FileOutputStream output=new FileOutputStream(file);BufferedWriter log=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8))){
            if(Build.VERSION.SDK_INT>=29)ui(()->home.registerActivityLifecycleCallbacks(lifecycle));
            boolean[] startFocus={false};ui(()->startFocus[0]=home.hasWindowFocus());
            AccessibilityNodeInfo startRoot=automation().getRootInActiveWindow();
            report.append("BACKGROUND_START focus=").append(startFocus[0]).append(" rootIsTarget=").append(startRoot!=null&&context().getPackageName().contentEquals(startRoot.getPackageName())).append(" finishing=").append(home.isFinishing()).append(" destroyed=").append(home.isDestroyed()).append('\n');
            leftAt=SystemClock.elapsedRealtime();
            try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(automation().executeShellCommand("input keyevent KEYCODE_HOME"))){
                ByteArrayOutputStream commandOutput=new ByteArrayOutputStream();byte[] buffer=new byte[512];int size;
                while((size=input.read(buffer))!=-1){if(commandOutput.size()+size>4096)throw new IOException("HOME command output exceeds limit");commandOutput.write(buffer,0,size);}
                report.append("BACKGROUND_HOME_METHOD shell-input output=").append(commandOutput.toString("UTF-8").trim()).append('\n');
            }
            long stopDeadline=leftAt+5000;while(!stopped.get()&&SystemClock.elapsedRealtime()<stopDeadline)Thread.sleep(50);
            if(!stopped.get()){
                boolean[] focused={false};ui(()->focused[0]=home.hasWindowFocus());
                report.append("BACKGROUND_HOME_NOT_STOPPED focus=").append(focused[0]).append(" lifecycle=").append(lifecycleEvents).append('\n');
                throw new IllegalStateException("HOME did not deliver Activity.onStop");
            }
            check(true,"real HOME transition stops the foreground discovery activity without force-stop");
            long graceEnd=leftAt+10000,graceRemaining;while((graceRemaining=graceEnd-SystemClock.elapsedRealtime())>0)Thread.sleep(Math.min(100,graceRemaining));
            long quietStarted=SystemClock.elapsedRealtime();
            log.write(new JSONObject().put("kind","identity").put("runId",run).put("stages",stages).put("cache",cache)
                .put("fixtureSha256",hash(new String(fixture,StandardCharsets.UTF_8))).put("networkProfile",network)
                .put("activePrefetchEnabled","true".equals(args.getString("activePrefetch","false")))
                .put("quietSeconds",seconds).put("pollIntervalMs",2000).put("graceMs",10000).put("observedStopMs",stoppedAt.get()-leftAt)
                .put("homeMethod","shell-input").put("activityStopped",stopped.get()).put("rankingPidsBeforeHome",new JSONArray(before))
                .put("scope","sampled model-process absence; not screen-off, wakeup trace or energy proof").toString());log.newLine();log.flush();output.getFD().sync();
            boolean allQuiet=true;int samples=seconds*1000/2000+1;
            for(int index=0;index<samples;index++){
                long scheduled=index*2000L,remaining=quietStarted+scheduled-SystemClock.elapsedRealtime();if(remaining>0)Thread.sleep(remaining);
                List<Integer> ranking=backgroundRankingPids();boolean offline=replayNetworkAbsent(network);
                boolean quiet=ranking.isEmpty()&&!returned.get();allQuiet&=quiet;
                log.write(new JSONObject().put("kind","sample").put("index",index).put("scheduledMs",scheduled)
                    .put("elapsedMs",SystemClock.elapsedRealtime()-quietStarted).put("mainPresent",true)
                    .put("rankingPids",new JSONArray(ranking)).put("returnedForeground",returned.get()).put("networkAbsent",offline).put("quiet",quiet).toString());
                log.newLine();log.flush();output.getFD().sync();
            }
            check(allQuiet,"model process remains absent at every two-second checkpoint after the ten-second background grace");
            log.write(new JSONObject().put("kind","complete").put("samples",samples).put("elapsedMs",SystemClock.elapsedRealtime()-quietStarted)
                .put("quiet",allQuiet).toString());log.newLine();log.flush();output.getFD().sync();
            report.append("BACKGROUND_OPERATIONS ").append(file.getName()).append(" durationSeconds=").append(seconds).append(" samples=").append(samples).append(" quiet=").append(allQuiet).append('\n');
            ui(()->context().startActivity(new Intent().setClassName(context().getPackageName(),"app.quietreader.DiscoveryActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            long resumeUntil=SystemClock.elapsedRealtime()+8000;
            while(!returned.get()&&SystemClock.elapsedRealtime()<resumeUntil)Thread.sleep(50);
            check(returned.get()&&active()==retainedSnapshot&&shown("设置"),"returning after the background interval retains the displayed snapshot and visible discovery page");
        }finally{ui(()->{application.unregisterActivityLifecycleCallbacks(lifecycle);if(Build.VERSION.SDK_INT>=29)home.unregisterActivityLifecycleCallbacks(lifecycle);});}
    }
    /** Observation only: the host/user controls radios; this probe never changes them. */
    private boolean replayNetworkAbsent(String profile){
        if(!Arrays.asList("uncontrolled","offline-cache").contains(profile))throw new IllegalArgumentException("Unknown network profile");
        android.net.ConnectivityManager manager=(android.net.ConnectivityManager)context().getSystemService(Context.CONNECTIVITY_SERVICE);
        if(manager==null)throw new IllegalStateException("Connectivity service unavailable");
        boolean absent=manager.getActiveNetwork()==null;
        if(profile.equals("offline-cache")&&!absent)throw new IllegalStateException("Offline-cache replay has an active network");
        return absent;
    }
    /** Programmatic viewport selection plus real card actions, not human gesture/frame evidence. */
    private void replayReading(Bundle args,String stages,String cache)throws Exception{
        String run=args.getString("detachedRun",args.getString("replayRun","replay-"+System.currentTimeMillis()));
        if(!run.matches("[a-z0-9][a-z0-9-]{0,39}"))throw new IllegalArgumentException("Invalid replay identity");
        int seconds=Integer.parseInt(args.getString("replaySeconds","1800"));
        if(seconds<60||seconds>3000)throw new IllegalArgumentException("Replay duration must be60..3000 seconds");
        String network=args.getString("networkProfile","uncontrolled");
        if(seconds>300&&!network.equals("offline-cache"))throw new IllegalArgumentException("Long online fixture replay is not controlled");
        boolean networkAbsentAtStart=replayNetworkAbsent(network);
        byte[] fixture;try(InputStream in=test.getContext().getAssets().open(replayFixture)){fixture=in.readAllBytes();}
        String fixtureHash=hash(new String(fixture,StandardCharsets.UTF_8));
        long snapshot=active();List<String> titles=new ArrayList<>(),events=new ArrayList<>();
        try(SQLiteDatabase database=db();Cursor rows=database.rawQuery("SELECT event_id,json_extract(payload,'$.title') FROM snapshot_rows WHERE snapshot=? ORDER BY position",new String[]{Long.toString(snapshot)})){
            while(rows.moveToNext()){events.add(rows.getString(0));titles.add(rows.getString(1));}
        }
        if(titles.size()!=100||new HashSet<>(events).size()!=100)throw new IllegalStateException("Replay needs100 independent displayed events");
        File file=new File(context().getExternalFilesDir(null),"device-run-"+run+"-operations.ndjson");
        if(!file.createNewFile())throw new IOException("Replay evidence already exists");
        long started=SystemClock.elapsedRealtime();int opened=0;Set<String> observed=new HashSet<>();
        try(FileOutputStream output=new FileOutputStream(file);BufferedWriter log=new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8))){
            log.write(new JSONObject().put("networkProfile",network).put("networkAbsentAtStart",networkAbsentAtStart).put("kind","identity").put("runId",run).put("stages",stages).put("cache",cache).put("fixtureSha256",fixtureHash).put("snapshot",snapshot).put("durationSeconds",seconds).put("viewportMethod","ListView.setSelectionFromTop; not gesture/frame evidence").put("activePrefetchEnabled","true".equals(args.getString("activePrefetch","false"))).put("bodies","synthetic cached text").toString());log.newLine();log.flush();output.getFD().sync();
            for(int index=0;index<100;index++){
                long scheduled=seconds*1000L*index/100;
                long remaining=started+scheduled-SystemClock.elapsedRealtime();if(remaining>0)Thread.sleep(remaining);
                long operationStart=SystemClock.elapsedRealtime();boolean passed=false,networkAbsentBefore=false,networkAbsentAfter=false;String error="";
                try{
                    networkAbsentBefore=replayNetworkAbsent(network);
                    replayViewport(index,titles.get(index));observed.add(events.get(index));
                    if(index==0||index%20==19){replayArticle(titles.get(index),index);opened++;}
                    if(active()!=snapshot)throw new IllegalStateException("Displayed snapshot changed without acceptance");
                    networkAbsentAfter=replayNetworkAbsent(network);
                    passed=true;
                }catch(Exception failure){error=failure.getClass().getSimpleName()+": "+failure.getMessage();throw failure;}
                finally{
                    log.write(new JSONObject().put("networkAbsentBefore",networkAbsentBefore).put("networkAbsentAfter",networkAbsentAfter).put("kind","operation").put("index",index).put("event",events.get(index)).put("title",titles.get(index)).put("scheduledMs",scheduled).put("startedMs",operationStart-started).put("durationMs",SystemClock.elapsedRealtime()-operationStart).put("passed",passed).put("error",error).toString());log.newLine();log.flush();output.getFD().sync();
                }
            }
            long remaining=started+seconds*1000L-SystemClock.elapsedRealtime();if(remaining>0)Thread.sleep(remaining);
            check(observed.size()==100,"actual ListView viewport shows every one of100 distinct fixed events");
            check(opened==6,"six real card opens render expand scroll and return to retained anchors");
            log.write(new JSONObject().put("networkAbsentAtEnd",replayNetworkAbsent(network)).put("kind","complete").put("viewed",observed.size()).put("opened",opened).put("elapsedMs",SystemClock.elapsedRealtime()-started).toString());log.newLine();log.flush();output.getFD().sync();
        }
        report.append("REPLAY_OPERATIONS ").append(file.getName()).append(" viewed=").append(observed.size()).append(" opened=").append(opened).append(" elapsedMs=").append(SystemClock.elapsedRealtime()-started).append(" fixtureSha256=").append(fixtureHash).append('\n');
    }
    private void replayViewport(int index,String title)throws Exception{
        ListView[] list={null};ui(()->list[0]=view(home.getWindow().getDecorView(),ListView.class));
        if(list[0]==null)throw new IllegalStateException("Discovery ListView missing");
        int[] count={0};ui(()->count[0]=list[0].getCount());
        while(index>=count[0]-1&&count[0]<100){
            ui(()->list[0].setSelectionFromTop(list[0].getCount()-1,0));test.waitForIdleSync();
            long until=SystemClock.elapsedRealtime()+5000;
            while(!shown("查看更多话题")&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            click("查看更多话题");ui(()->count[0]=list[0].getCount());
        }
        ui(()->list[0].setSelectionFromTop(index,0));test.waitForIdleSync();
        long until=SystemClock.elapsedRealtime()+5000;boolean[] visible={false};
        while(SystemClock.elapsedRealtime()<until){
            ui(()->{View card=find(home.getWindow().getDecorView(),title);Rect bounds=new Rect(),viewport=new Rect();visible[0]=card!=null&&card.getGlobalVisibleRect(bounds)&&list[0].getGlobalVisibleRect(viewport)&&bounds.intersect(viewport)&&bounds.height()>0;});
            if(visible[0])return;Thread.sleep(50);
        }
        report.append("REPLAY_VIEWPORT_FAILURE index=").append(index).append(" title=").append(title).append(" activeSnapshot=").append(active()).append('\n');
        ui(()->{report.append("REPLAY_VIEWPORT_RANGE first=").append(list[0].getFirstVisiblePosition()).append(" last=").append(list[0].getLastVisiblePosition()).append(" count=").append(list[0].getCount()).append('\n');});
        dump(automation().getRootInActiveWindow(),0);screenshot("replay-viewport-failure.png");
        throw new IllegalStateException("Replay title not visible at index"+index);
    }
    private void replayArticle(String title,int index)throws Exception{
        JSONObject expected=new JSONObject(string("SELECT payload FROM snapshot_rows WHERE snapshot="+active()+" AND position="+index));
        ListView[] list={null};int[] anchor={0,0};
        ui(()->{list[0]=view(home.getWindow().getDecorView(),ListView.class);anchor[0]=list[0].getFirstVisiblePosition();anchor[1]=list[0].getChildAt(0).getTop();});
        long clickStarted=SystemClock.elapsedRealtime(),activityObserved=0,monitorObserved=0;
        FirstReaderStacks stacks=index==0&&recordFirstReaderStacks?new FirstReaderStacks(clickStarted):null;
        LaunchTimeline timeline=new LaunchTimeline();Application application=(Application)context().getApplicationContext();ui(()->application.registerActivityLifecycleCallbacks(timeline));
        try{
        Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);
        try{
            ui(()->{timeline.times.set(5,SystemClock.elapsedRealtime());View card=find(home.getWindow().getDecorView(),title);while(card!=null&&!card.isClickable())card=(View)card.getParent();if(card==null)throw new IllegalStateException("Replay card not clickable");for(int tap=0;tap<3;tap++)card.performClick();timeline.times.set(6,SystemClock.elapsedRealtime());});
            reader=test.waitForMonitorWithTimeout(monitor,10000);
            monitorObserved=SystemClock.elapsedRealtime();
            test.waitForIdleSync();
            if(monitor.getHits()!=1)throw new IllegalStateException("Repeated card taps opened multiple readers");
        }finally{test.removeMonitor(monitor);}
        activityObserved=SystemClock.elapsedRealtime();
        if(reader==null)throw new IllegalStateException("Replay unified reader launch timeout");
        holdReplayScreen(reader);
        JSONObject actualCandidate=new JSONObject(reader.getIntent().getStringExtra("discovery_candidate"));
        if(!expected.getString("source").equals(actualCandidate.getString("source"))||!expected.getString("url").equals(actualCandidate.getString("url")))throw new IllegalStateException("Reader opened a different source event");
        report.append("REPLAY_OPEN_IDENTITY index=").append(index).append(" source=").append(actualCandidate.getString("source")).append(" urlHash=").append(hash(actualCandidate.getString("url"))).append(" monitorHits=1\n");
        WebView[] web={null};long bodyDeadline=SystemClock.elapsedRealtime()+15000;
        JSONObject body=null;
        while(SystemClock.elapsedRealtime()<bodyDeadline){
            ui(()->web[0]=view(reader.getWindow().getDecorView(),WebView.class));
            if(web[0]!=null){body=replayDom(web[0]);if(body.optBoolean("sentinel")&&body.optBoolean("control"))break;}
            Thread.sleep(100);
        }
        if(web[0]==null||body==null||!body.optBoolean("sentinel")||!body.optBoolean("control")){dump(automation().getRootInActiveWindow(),0);screenshot("replay-body-failure.png");throw new IllegalStateException("Replay cached DOM/control missing");}
        long bodyObserved=SystemClock.elapsedRealtime();
        if(body.optBoolean("expanded"))throw new IllegalStateException("Replay must begin folded");
        // DOM can arrive before WebView's first scale/layout settles. Require stable
        // geometry before converting CSS coordinates into a real touch location.
        boolean stable=false;long layoutDeadline=SystemClock.elapsedRealtime()+3000;
        while(SystemClock.elapsedRealtime()<layoutDeadline){Thread.sleep(150);JSONObject next=replayDom(web[0]);if(next.optBoolean("control")&&Math.abs(next.getDouble("x")-body.getDouble("x"))<0.5&&Math.abs(next.getDouble("y")-body.getDouble("y"))<0.5){body=next;stable=true;break;}body=next;}
        if(!stable)throw new IllegalStateException("Replay control geometry did not settle");
        report.append("REPLAY_LATENCY index=").append(index).append(" activityObservedMs=").append(activityObserved-clickStarted).append(" bodyObservedMs=").append(bodyObserved-clickStarted).append(" stableControlMs=").append(SystemClock.elapsedRealtime()-clickStarted).append(" scope=host-instrumentation-observation-not-frame-presentation\n");
        report.append("REPLAY_TIMELINE index=").append(index).append(' ').append(timeline.report(clickStarted,monitorObserved,activityObserved)).append('\n');
        int[] location=new int[2];float[] scale={1};ui(()->{web[0].getLocationOnScreen(location);scale[0]=web[0].getScale();});
        float x=location[0]+(float)body.getDouble("x")*scale[0],y=location[1]+(float)body.getDouble("y")*scale[0];
        report.append("REPLAY_TOUCH x=").append(x).append(" y=").append(y).append(" scale=").append(scale[0]).append(" location=").append(Arrays.toString(location)).append(" dom=").append(body).append('\n');
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,x,y,0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try{test.sendPointerSync(down);Thread.sleep(60);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
        long expandedDeadline=SystemClock.elapsedRealtime()+5000;boolean expanded=false;
        while(SystemClock.elapsedRealtime()<expandedDeadline){JSONObject actual=replayDom(web[0]);if(actual.optBoolean("expanded")&&actual.optBoolean("beyondPreview")){expanded=true;break;}Thread.sleep(100);}
        if(!expanded){report.append("REPLAY_TOUCH_AFTER ").append(replayDom(web[0])).append('\n');screenshot("replay-touch-failure.png");throw new IllegalStateException("Real touch did not expand cached body");}
        ui(()->web[0].scrollTo(0,900));Thread.sleep(350);int[] scroll={0};ui(()->scroll[0]=web[0].getScrollY());
        if(scroll[0]<=0)throw new IllegalStateException("Replay body cannot scroll");
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);test.waitForIdleSync();
        long until=SystemClock.elapsedRealtime()+5000;boolean[] restored={false};
        while(SystemClock.elapsedRealtime()<until){
            ui(()->{View first=list[0].getChildAt(0);restored[0]=!home.isFinishing()&&list[0].getFirstVisiblePosition()==anchor[0]&&first!=null&&Math.abs(first.getTop()-anchor[1])<=2&&find(home.getWindow().getDecorView(),title)!=null;});
            if(restored[0])return;Thread.sleep(50);
        }throw new IllegalStateException("Replay return lost list anchor");
        }finally{
            if(stacks!=null){stacks.close();stacks.append(report);}
            report.append("REPLAY_TIMELINE_FINAL index=").append(index).append(' ').append(timeline.report(clickStarted,monitorObserved,activityObserved).put("observationStoppedMs",SystemClock.elapsedRealtime()-clickStarted)).append('\n');
            ui(()->{timeline.removeDraw();application.unregisterActivityLifecycleCallbacks(timeline);});
        }
    }
    // Observe only the App-owned synthetic reader. No source-page scripts or production setting changes.
    private JSONObject replayDom(WebView web)throws Exception{
        java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] raw={null},requestedPage={null};boolean[] javascript={false};
        ui(()->{requestedPage[0]=web.getUrl();javascript[0]=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("(()=>{const t=document.body?document.body.innerText:'';const a=document.querySelector('a[aria-expanded]');const r=a?a.getBoundingClientRect():null;return {sentinel:t.includes('合成阅读正文起点'),control:!!r&&r.width>0&&r.height>0,expanded:!!a&&a.getAttribute('aria-expanded')==='true',beyondPreview:t.includes('合成正文第3段'),x:r?r.x+r.width/2:0,y:r?r.y+r.height/2:0};})()",value->{raw[0]=value;web.getSettings().setJavaScriptEnabled(javascript[0]);done.countDown();});});
        if(!done.await(3,java.util.concurrent.TimeUnit.SECONDS)){
            String[] currentPage={null};ui(()->{currentPage[0]=web.getUrl();web.getSettings().setJavaScriptEnabled(javascript[0]);});
            boolean changed=!Objects.equals(requestedPage[0],currentPage[0]);
            boolean ownRender=false;try{android.net.Uri uri=android.net.Uri.parse(currentPage[0]);ownRender="https".equals(uri.getScheme())&&"quiet-reader.invalid".equals(uri.getHost())&&"/".equals(uri.getPath())&&uri.getQueryParameter("render")!=null;}catch(Exception ignored){}
            report.append("REPLAY_DOM_CALLBACK_TIMEOUT pageChanged=").append(changed).append(" currentOwnRender=").append(ownRender).append('\n');
            // A missing callback is no observation. Retry only the App-owned
            // render page within the existing bounded caller deadlines; a pass
            // still requires actual sentinel/control/expanded text evidence.
            // Production also scopes its JavaScript settings asynchronously.
            if(ownRender)return new JSONObject();
            throw new IllegalStateException("Replay DOM observation timeout outside reader render");
        }
        return new JSONObject(raw[0]==null?"{}":raw[0]);
    }
    private void launchReplay(){launchReplay(false);}
    private void launchReplay(boolean activePrefetch){home=test.startActivitySync(new Intent().setClassName(context().getPackageName(),"app.quietreader.DiscoveryActivity").putExtra("test_skip_fetch",!activePrefetch).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));holdReplayScreen(home);test.waitForIdleSync();}
    private void layoutAudit(View root,int[] counts){
        if(root instanceof TextView&&root.isShown()){
            TextView text=(TextView)root;android.text.Layout layout=text.getLayout();Rect visible=new Rect();
            if(text.getText().length()>0&&layout!=null&&text.getGlobalVisibleRect(visible)&&visible.height()>=text.getHeight()-1&&text.getEllipsize()==null){
                counts[0]++;Rect ink=new Rect();String content=layout.getText().toString();
                for(int n=0;n<layout.getLineCount();n++){int start=layout.getLineStart(n),end=layout.getLineEnd(n);while(end>start&&Character.isWhitespace(content.charAt(end-1)))end--;if(start==end)continue;text.getPaint().getTextBounds(content,start,end,ink);int baseline=text.getBaseline()+layout.getLineBaseline(n)-layout.getLineBaseline(0),top=baseline+ink.top,bottom=baseline+ink.bottom;
                    if(top<text.getCompoundPaddingTop()-1||bottom>text.getHeight()-text.getCompoundPaddingBottom()+1){counts[1]++;report.append("CLIPPED_INK ").append(text.getText()).append(" top=").append(top).append(" bottom=").append(bottom).append(" padding=").append(text.getCompoundPaddingTop()).append('/').append(text.getCompoundPaddingBottom()).append(" height=").append(text.getHeight()).append('\n');break;}}
                for(int n=0;n<layout.getLineCount();n++)if(layout.getLineWidth(n)>text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+1){counts[1]++;report.append("WIDE_TEXT ").append(text.getText()).append('\n');break;}
            }
        }
        if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)layoutAudit(((ViewGroup)root).getChildAt(n),counts);
    }
    private void layoutCapture(String theme,String page)throws Exception{
        Thread.sleep(350);int[] counts={0,0};Activity owner=reader!=null?reader:home;ui(()->layoutAudit(owner.getWindow().getDecorView(),counts));
        check(counts[0]>0&&counts[1]==0,"fully visible native text fits its measured control: "+page+" count="+counts[0]);screenshot("release-layout-"+theme+"-"+page+".png");
    }
    private void layoutMenu(String label)throws Exception{
        for(int attempt=0;attempt<12;attempt++){
            if(accessible(label)){menu(label);return;}
            AccessibilityNodeInfo root=automation().getRootInActiveWindow(),scroll=null;
            java.util.ArrayDeque<AccessibilityNodeInfo> queue=new java.util.ArrayDeque<>();if(root!=null)queue.add(root);
            while(!queue.isEmpty()){AccessibilityNodeInfo node=queue.removeFirst();if(node.isScrollable()&&node.isVisibleToUser()){scroll=node;break;}for(int n=0;n<node.getChildCount();n++){AccessibilityNodeInfo child=node.getChild(n);if(child!=null)queue.addLast(child);}}
            if(scroll==null||!scroll.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))break;test.waitForIdleSync();Thread.sleep(100);
        }
        throw new IllegalStateException("Unreachable layout action: "+label);
    }
    private WebView layoutReader(View root){if(root instanceof WebView){String url=((WebView)root).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/"))return (WebView)root;}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){WebView found=layoutReader(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
    private JSONObject layoutDom(WebView web)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+6000;
        do{java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] raw={null};boolean[] enabled={false};
            ui(()->{enabled[0]=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify({width:document.documentElement.clientWidth,scrollWidth:document.documentElement.scrollWidth,body:!!document.body&&document.body.innerText.includes('合成阅读正文起点'),expanded:!!document.body&&document.body.innerText.includes('合成正文第3段')})",value->{raw[0]=value;web.getSettings().setJavaScriptEnabled(enabled[0]);done.countDown();});});
            if(!done.await(2,java.util.concurrent.TimeUnit.SECONDS)){ui(()->web.getSettings().setJavaScriptEnabled(enabled[0]));throw new IllegalStateException("Layout DOM callback timeout");}
            if(raw[0]!=null&&!raw[0].equals("null")){JSONObject result=new JSONObject(new JSONArray("["+raw[0]+"]").getString(0));if(result.optBoolean("body")&&result.optInt("width")>0)return result;}Thread.sleep(100);
        }while(SystemClock.elapsedRealtime()<deadline);throw new IllegalStateException("Layout DOM navigation timeout");
    }
    void runLayout(Bundle args){
        android.content.SharedPreferences appearance=context().getSharedPreferences("appearance",0);boolean hadMode=appearance.contains("mode");int previousMode=appearance.getInt("mode",0);String theme=args.getString("theme","dark");
        android.content.SharedPreferences readerPrefs=context().getSharedPreferences("MainActivity",0);boolean hadFont=readerPrefs.contains("font");int previousFont=readerPrefs.getInt("font",19);
        try{
            if(!isolatedReplayTarget()||!Arrays.asList("dark","light").contains(theme))throw new IllegalStateException("Isolated layout target/theme required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"layout target is actual minified nondebuggable app");
            appearance.edit().putInt("mode",theme.equals("dark")?2:1).commit();readerPrefs.edit().putInt("font",28).commit();holdReplayScreen=true;launchReplay(false);waitHome(10000);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            seedFeedback=new JSONArray();seed();launchReplay(false);waitText(candidates.get(0).getString("title"),10000);
            report.append("LAYOUT theme=").append(theme).append(" widthDp=").append(home.getResources().getConfiguration().screenWidthDp).append(" fontScale=").append(home.getResources().getConfiguration().fontScale).append(" readerFont=").append(readerPrefs.getInt("font",19)).append('\n');
            layoutCapture(theme,"home");click("设置");layoutCapture(theme,"settings");
            layoutMenu("阅读偏好");layoutCapture(theme,"preferences");click("返回");layoutMenu("缓存与存储");layoutCapture(theme,"storage");click("返回");layoutMenu("高级与诊断");layoutCapture(theme,"advanced");click("返回");
            layoutMenu("内容来源");layoutCapture(theme,"sources-top");
            ui(()->{ListView list=view(home.getWindow().getDecorView(),ListView.class);if(list==null)throw new IllegalStateException("Source list absent");list.setSelection(list.getCount()-1);});test.waitForIdleSync();layoutCapture(theme,"sources-bottom");returnToFeed();
            Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);
            try{ui(()->{View target=find(home.getWindow().getDecorView(),candidates.get(0).optString("title"));while(target!=null&&!target.isClickable())target=(View)target.getParent();if(target==null)throw new IllegalStateException("Layout card absent");target.performClick();});reader=test.waitForMonitorWithTimeout(monitor,12000);}finally{test.removeMonitor(monitor);}
            if(reader==null)throw new IllegalStateException("Layout reader absent");holdReplayScreen(reader);waitText("合成阅读正文起点",15000);
            WebView[] web={null};ui(()->web[0]=layoutReader(reader.getWindow().getDecorView()));if(web[0]==null)throw new IllegalStateException("Own rendered reader absent");
            JSONObject folded=layoutDom(web[0]);check(folded.getBoolean("body")&&folded.getInt("width")>0&&folded.getInt("scrollWidth")<=folded.getInt("width")+1,"folded cached text body has no document-wide horizontal overflow");report.append("FOLDED_DOM ").append(folded).append('\n');layoutCapture(theme,"reader-folded");
            layoutMenu("1 · 展开阅读 ↓");JSONObject expanded;long expandedDeadline=SystemClock.elapsedRealtime()+8000;do{expanded=layoutDom(web[0]);if(expanded.optBoolean("expanded"))break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<expandedDeadline);check(expanded.getBoolean("expanded")&&expanded.getInt("scrollWidth")<=expanded.getInt("width")+1,"actual expand retains full body without document-wide horizontal overflow");report.append("EXPANDED_DOM ").append(expanded).append('\n');ui(()->web[0].scrollTo(0,0));layoutCapture(theme,"reader-expanded");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');dump(automation().getRootInActiveWindow(),0);try{screenshot("release-layout-"+theme+"-failure.png");}catch(Exception ignored){}}
        finally{restoreReplayScreens();ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();});test.waitForIdleSync();android.content.SharedPreferences.Editor restore=appearance.edit();if(hadMode)restore.putInt("mode",previousMode);else restore.remove("mode");restore.commit();check(appearance.contains("mode")==hadMode&&appearance.getInt("mode",0)==previousMode,"layout probe restores original appearance mode");android.content.SharedPreferences.Editor fontRestore=readerPrefs.edit();if(hadFont)fontRestore.putInt("font",previousFont);else fontRestore.remove("font");fontRestore.commit();check(readerPrefs.contains("font")==hadFont&&readerPrefs.getInt("font",19)==previousFont,"layout probe restores original reading font");}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    /** Seeded body hashes with real app-created account scopes and natural exposure.
     * Uses only Android/SQLite APIs; no reference to minified production classes. */
    void runFollowup(){
        try{
            if(!isolatedReplayTarget())throw new IllegalStateException("Dedicated flow package required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"follow-up target is genuinely minified and non-debuggable");
            launch();waitHome(10000);long retained=active();report.append("FOLLOWUP_RETAINED snapshot=").append(retained).append(" rows=").append(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+retained)).append(" neverExposed=").append(scalar("SELECT COUNT(*) FROM snapshot_rows s WHERE s.snapshot="+retained+" AND NOT EXISTS (SELECT 1 FROM feed_exposure e WHERE e.key='event:'||s.event_id AND e.shown_at>0)")).append('\n');ui(home::finish);test.waitForIdleSync();Thread.sleep(500);home=null;
            seedFeedback=new JSONArray();seed();long now=System.currentTimeMillis();
            for(int n=6;n<30;n++){String title="离线后续报道候选第"+n+"项";candidates.add(new JSONObject(candidates.get(0).toString()).put("title",title).put("rank",n+1));ids.add("");hashes.add(hash("hot-title-v2\n"+title+"\n"+candidates.get(n).getString("snippet")));}
            for(int n=0;n<candidates.size();n++){String url="https://www.thepaper.cn/newsDetail_forward_"+(now+n+1000),title=candidates.get(n).getString("title")+" · 轮次"+now;candidates.get(n).put("source","THEPAPER").put("url",url).put("title",title);ids.set(n,hash("THEPAPER\n"+url));hashes.set(n,hash("hot-title-v2\n"+title+"\n"+candidates.get(n).getString("snippet")));}
            // Let the production grouper accept the pair, so automatic pending
            // rankings preserve it instead of replacing a test-injected grouping.
            candidates.get(29).put("title",candidates.get(0).getString("title")).put("snippet",candidates.get(0).getString("snippet"));hashes.set(29,hashes.get(0));
            persistSeed(now,true,true);launch();waitHome(10000);waitText(candidates.get(0).getString("title"),10000);long initial=active();
            ui(home::finish);test.waitForIdleSync();Thread.sleep(500);home=null;
            JSONObject merged=new JSONObject(string("SELECT payload FROM snapshot_rows WHERE snapshot="+initial+" ORDER BY position LIMIT 1"));JSONArray members=merged.getJSONArray("groupEntries");
            check(members.length()==2,"production grouping retains both same-headline article URLs in the actual representative event");
            JSONObject after=merged,before=members.getJSONObject(0).getString("url").equals(after.getString("url"))?members.getJSONObject(1):members.getJSONObject(0);
            String beforeId=hash(before.getString("source")+"\n"+before.getString("url")),afterId=hash(after.getString("source")+"\n"+after.getString("url"));
            String event=string("SELECT event_id FROM snapshot_rows WHERE snapshot="+initial+" ORDER BY position LIMIT 1");
            try(SQLiteDatabase db=db()){db.execSQL("DELETE FROM article_progress WHERE identity IN (?,?)",new Object[]{beforeId,afterId});db.execSQL("DELETE FROM feed_exposure WHERE key IN (?,?)",new Object[]{"member:"+beforeId,"member:"+afterId});}
            launch();waitHome(10000);waitText(after.getString("title"),10000);long until=SystemClock.elapsedRealtime()+10000;
            while(SystemClock.elapsedRealtime()<until&&scalar("SELECT COUNT(*) FROM article_progress WHERE identity IN ('"+beforeId+"','"+afterId+"') AND presented_at>0")<2)Thread.sleep(50);
            check(scalar("SELECT COUNT(*) FROM article_progress WHERE identity IN ('"+beforeId+"','"+afterId+"') AND presented_at>0")==2,"actual accepted group dwell captures both article account scopes without seeding cookies or scope keys");
            String beforeScope=string("SELECT scope FROM article_progress WHERE identity='"+beforeId+"' ORDER BY presented_at DESC LIMIT 1"),afterScope=string("SELECT scope FROM article_progress WHERE identity='"+afterId+"' ORDER BY presented_at DESC LIMIT 1");
            long shown=Math.max(scalar("SELECT MAX(presented_at) FROM article_progress WHERE identity='"+beforeId+"'"),scalar("SELECT MAX(presented_at) FROM article_progress WHERE identity='"+afterId+"'"));Thread.sleep(30);long fresh=System.currentTimeMillis();
            String old=followupBody(beforeId,beforeScope,shown-1000,shown-2000,false).toString(),updated=followupBody(afterId,afterScope,fresh-1,fresh-2,true).toString();
            try(SQLiteDatabase db=db()){
                db.execSQL("UPDATE article_progress SET baseline=?,latest=?,latest_at=?,verified_at=0,touched_at=?,bytes=? WHERE identity=? AND scope=?",new Object[]{old,old,shown-1000,fresh,old.length()*2,beforeId,beforeScope});
                db.execSQL("UPDATE article_progress SET latest=?,latest_at=?,verified_at=0,touched_at=?,bytes=? WHERE identity=? AND scope=?",new Object[]{updated,fresh-1,fresh,updated.length(),afterId,afterScope});
            }
            long original=active(),profile=scalar("SELECT value FROM meta WHERE key='profile'");
            report.append("FOLLOWUP_READY original=").append(original).append(" latest=").append(scalar("SELECT MAX(id) FROM snapshots")).append(" group=").append(event).append(" shown=").append(shown).append(" modified=").append(fresh-2).append(" members=").append(string("SELECT json_array_length(payload,'$.groupEntries') FROM snapshot_rows WHERE snapshot="+original+" ORDER BY position LIMIT 1")).append('\n');
            check(scalar("SELECT COUNT(*) FROM article_progress WHERE identity IN ('"+beforeId+"','"+afterId+"') AND verified_at>0")==0,"fixture has no same-article revision shortcut; cross-URL body comparison is required");
            click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==original&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long refreshed=active();
            report.append("FOLLOWUP_ACCEPTED snapshot=").append(refreshed).append(" first=").append(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+refreshed+" ORDER BY position LIMIT 1")).append(" member=").append(string("SELECT json_extract(payload,'$.url') FROM snapshot_rows WHERE snapshot="+refreshed+" ORDER BY position LIMIT 1")).append('\n');
            check(refreshed!=original&&event.equals(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+refreshed+" ORDER BY position LIMIT 1")),"real R8 refresh retains the evidenced follow-up ahead of unseen peers in the same quality band");
            check(after.getString("url").equals(string("SELECT json_extract(payload,'$.url') FROM snapshot_rows WHERE snapshot="+refreshed+" ORDER BY position LIMIT 1"))&&shown(after.getString("title")),"promoted visible card uses the new report URL and title");
            until=SystemClock.elapsedRealtime()+10000;while(scalar("SELECT MAX(shown_at) FROM feed_exposure WHERE key='member:"+afterId+"'")<=fresh&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(scalar("SELECT MAX(shown_at) FROM feed_exposure WHERE key='member:"+afterId+"'")>fresh,"actual refreshed dwell consumes the follow-up through normal exposure persistence");
            long unseen=scalar("SELECT COUNT(*) FROM snapshot_rows s WHERE s.snapshot="+refreshed+" AND NOT EXISTS (SELECT 1 FROM feed_exposure e WHERE e.key='event:'||s.event_id AND e.shown_at>0)");report.append("FOLLOWUP_UNSEEN_AFTER_DWELL ").append(unseen).append('\n');check(unseen>0,"this run has genuinely unseen alternatives after consuming the follow-up, independent of retained prior fixtures");
            click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==refreshed&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
            check(!event.equals(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+active()+" ORDER BY position LIMIT 1")),"a consumed follow-up cannot repeatedly bypass cooldown on the next real refresh");
            check(scalar("SELECT value FROM meta WHERE key='profile'")==profile&&scalar("SELECT COUNT(*) FROM feedback")==0,"minified follow-up exposure and acceptance do not manufacture preference feedback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private JSONObject followupBody(String identity,String scope,long acquired,long modified,boolean update)throws Exception{
        JSONArray body=new JSONArray(),marked=new JSONArray();for(int n=0;n<(update?428:300);n++){body.put(Integer.toHexString(n));if(update&&n>=300)marked.put(Integer.toHexString(n));}
        return new JSONObject().put("version","marked-body-revision-v1").put("identity",identity).put("scope",scope).put("acquiredAt",acquired).put("modifiedAt",modified).put("body",body).put("marked",marked);
    }
    private String discussionProse(int seed){Random random=new Random(seed);StringBuilder text=new StringBuilder();for(int n=0;n<300;n++)text.append((char)(0x4e00+random.nextInt(3000)));return text.toString();}
    /** Serialized synthetic body only: production decoder/fingerprinter must derive its own proof. */
    private File discussionBody(JSONObject candidate,String metadata,long acquired,long replyAt)throws Exception{
        String url=candidate.getString("url"),namespace=context().getSharedPreferences("article-cache",0).getString("namespace","initial");String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie(url));
        boolean hupu="HUPU".equals(candidate.getString("source")),tieba="TIEBA".equals(candidate.getString("source"));String mainId=tieba?"tieba-post-"+hash("synthetic-main").substring(0,24):hupu?"hupu-main-"+java.net.URI.create(url).getPath().replace("/","").replace(".html",""):"v2ex-main";
        JSONObject main=new JSONObject().put("id",mainId).put("label","正文").put("answer",false).put("blocks",new JSONArray().put(new JSONObject().put("type","text").put("value",discussionProse(1)).put("images",new JSONArray()).put("links",new JSONArray())));JSONArray sections=new JSONArray().put(main);
        if(replyAt>0)sections.put(new JSONObject().put("id",hupu?"hupu-reply-90001":"v2ex-r_90001").put("label","回复").put("answer",false).put("sourceReplyEvidence",new JSONObject().put("version",hupu?"hupu-reply-created-v1":"v2ex-reply-created-v1").put("createdAt",replyAt)).put("blocks",new JSONArray().put(new JSONObject().put("type","text").put("value","【最新进展】"+discussionProse(2)).put("images",new JSONArray()).put("links",new JSONArray()))));
        if(tieba){
            if(replyAt>0)sections.remove(1);
            String parent="tieba-post-"+hash("post_content_154074790043").substring(0,24);
            sections.put(new JSONObject().put("id",parent).put("sourcePostId","154074790043").put("label","回复").put("answer",false).put("blocks",new JSONArray().put(new JSONObject().put("type","text").put("value",discussionProse(3)).put("images",new JSONArray()).put("links",new JSONArray()))));
            if(replyAt>0)sections.put(new JSONObject().put("id","tieba-post-"+hash("lzl:154076383836").substring(0,24)).put("label","回复").put("answer",false).put("nestedReply",true).put("parentSectionId",parent).put("sourceReplyEvidence",new JSONObject().put("version","tieba-child-created-v1").put("createdAt",replyAt)).put("blocks",new JSONArray().put(new JSONObject().put("type","text").put("value","【最新进展】"+discussionProse(2)).put("images",new JSONArray()).put("links",new JSONArray()))));
        }
        JSONObject document=new JSONObject().put("url",url).put("title",candidate.getString("title")).put("metadataHash",metadata).put("discussionPage",1).put("sections",sections);
        if(hupu)document.put("hupuBodyRevision",HupuDiscussion.BODY_REVISION);
        if(tieba)document.put("tiebaReplyRevision",4).put("virtualDiscussion",true);
        File root=new File(context().getCacheDir(),"article-bodies-v1");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Discussion cache directory unavailable");
        File file=new File(root,hash(namespace+"\n"+fixtureSession(url,cookie[0])+"\n"+url)+".json");try(FileOutputStream out=new FileOutputStream(file)){out.write(new JSONObject().put("at",acquired).put("document",document).put("related",new JSONArray()).toString().getBytes(StandardCharsets.UTF_8));}return file;
    }
    /** Two independent process phases preserve real scopes/exposures while replacing a synthetic disk body.
     * The host force-stops ONLY this isolated package between phases to discard static memory cache. */
    void runDiscussionProgress(boolean verify){runDiscussionProgress(verify,false);}
    void runDiscussionProgress(boolean verify,boolean hupu){runDiscussionProgress(verify,hupu,false);}
    void runTiebaDiscussionProgress(boolean verify){runDiscussionProgress(verify,false,true);}
    private void runDiscussionProgress(boolean verify,boolean hupu,boolean tieba){
        try{
            if(!isolatedReplayTarget())throw new IllegalStateException("Dedicated isolated flow package required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"discussion target is non-debuggable/minified; Android APIs and serialized body fixtures only");
            String evidenceVersion=tieba?"marked-tieba-child-v1":"marked-new-reply-v1";
            android.content.SharedPreferences state=context().getSharedPreferences(tieba?"release-tieba-discussion-progress-probe":hupu?"release-hupu-discussion-progress-probe":"release-discussion-progress-probe",0);
            if(!verify){
                launch();waitHome(10000);ui(home::finish);test.waitForIdleSync();Thread.sleep(500);home=null;
                seedFeedback=new JSONArray();seed();long now=System.currentTimeMillis();
                for(int n=6;n<30;n++){String title="离线讨论刷新候选第"+n+"项",url="https://www.v2ex.com/t/"+(now+n+1000);candidates.add(new JSONObject(candidates.get(0).toString()).put("url",url).put("title",title).put("rank",n+1));ids.add(hash("V2EX\n"+url));hashes.add("");}
                for(int n=0;n<candidates.size();n++){JSONObject candidate=candidates.get(n);candidate.put("title",candidate.getString("title")+" · 轮次"+now);hashes.set(n,hash("hot-title-v2\n"+candidate.getString("title")+"\n"+candidate.getString("snippet")));}
                if(hupu)for(int n=0;n<candidates.size();n++){String url="https://bbs.hupu.com/"+(now+n+1000)+".html";candidates.get(n).put("source","HUPU").put("url",url);ids.set(n,hash("HUPU\n"+url));}
                if(tieba)for(int n=0;n<candidates.size();n++){String url="https://tieba.baidu.com/p/"+(now+n+1000);candidates.get(n).put("source","TIEBA").put("url",url);ids.set(n,hash("TIEBA\n"+url));}
                persistSeed(now,true,true);discussionBody(candidates.get(0),hashes.get(0),now,0);
                launch();waitHome(10000);waitText(candidates.get(0).getString("title"),10000);String identity=ids.get(0);long until=SystemClock.elapsedRealtime()+15000;
                while(SystemClock.elapsedRealtime()<until&&scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND presented_at>0 AND baseline<>'' AND json_extract(baseline,'$.discussion.version')='"+evidenceVersion+"'")==0)Thread.sleep(50);
                check(scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND presented_at>0 AND baseline<>'' AND json_extract(baseline,'$.modifiedAt')=0")==1,"actual R8 preloader and natural visible-card dwell establish a scoped main-only hash baseline without invented source time");
                long original=active(),shown=scalar("SELECT MAX(presented_at) FROM article_progress WHERE identity='"+identity+"'");String event=string("SELECT event_id FROM snapshot_rows WHERE snapshot="+original+" AND json_extract(payload,'$.url')='"+candidates.get(0).getString("url")+"'");
                click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==original&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long cooled=active();
                check(cooled!=original&&!event.equals(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+cooled+" ORDER BY position LIMIT 1")),"actual update dot cools the naturally exposed thread behind unseen same-band content");
                check(scalar("SELECT position FROM snapshot_rows WHERE snapshot="+cooled+" AND event_id='"+event+"'")>=8,"target thread is outside the initial viewport before the independent update phase");
                check(scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND verified_at>0")==0,"seed phase has no injected progress proof");
                state.edit().putString("candidate",candidates.get(0).toString()).putString("identity",identity).putString("metadata",hashes.get(0)).putString("event",event).putLong("shown",shown).putLong("cooled",cooled).putLong("profile",scalar("SELECT value FROM meta WHERE key='profile'")).commit();
            }else{
                String identity=state.getString("identity",""),event=state.getString("event","");if(!identity.matches("[0-9a-f]{64}")||event.isEmpty())throw new IllegalStateException("Completed seed phase required");
                JSONObject candidate=new JSONObject(state.getString("candidate",""));long shown=state.getLong("shown",0),cooled=state.getLong("cooled",0),replyAt=System.currentTimeMillis()/1000*1000;
                if(replyAt<=shown){Thread.sleep(1100);replyAt=System.currentTimeMillis()/1000*1000;}if(replyAt<=shown)throw new IllegalStateException("New reply must follow real exposure");
                long acquired=System.currentTimeMillis();File body=discussionBody(candidate,state.getString("metadata",""),acquired,replyAt);long inode=android.system.Os.stat(body.getPath()).st_ino;
                check(active()==cooled&&scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND verified_at>0")==0,"independent phase starts from the cooled snapshot and no derivative proof");
                launch();waitHome(10000);long until=SystemClock.elapsedRealtime()+15000;
                while(SystemClock.elapsedRealtime()<until&&scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND verified_at>"+shown)==0)Thread.sleep(50);
                check(scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND verified_at="+replyAt+" AND latest_at="+acquired+" AND json_extract(latest,'$.version')='"+evidenceVersion+"'")==1,"real R8 disk decoder, preloader observer and fingerprint worker derive new-reply proof without seeded hashes or scalar proof");
                check(active()==cooled&&scalar("SELECT MAX(presented_at) FROM article_progress WHERE identity='"+identity+"'")==shown,"preparing an offscreen updated body changes neither active list nor actual presentation baseline");
                click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==cooled&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long accepted=active();waitText(candidate.getString("title"),10000);
                check(accepted!=cooled&&event.equals(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+accepted+" ORDER BY position LIMIT 1")),"real R8 dot and scoped prefilter promote the updated discussion at its original interest quality");
                until=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<until&&scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND presented_at>="+replyAt+" AND verified_at=0")==0)Thread.sleep(50);
                check(scalar("SELECT COUNT(*) FROM article_progress WHERE identity='"+identity+"' AND presented_at>="+replyAt+" AND verified_at=0")==1,"actual renewed visible-card dwell consumes the proof rather than repeatedly promoting it");
                check(scalar("SELECT COUNT(*) FROM snapshot_rows s WHERE snapshot="+accepted+" AND NOT EXISTS (SELECT 1 FROM feed_exposure e WHERE e.key='event:'||s.event_id AND e.shown_at>0)")>0,"an unseen alternative remains before the next explicit update");
                click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==accepted&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
                check(active()!=accepted&&!event.equals(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+active()+" ORDER BY position LIMIT 1")),"next explicit update returns to unseen-first after consuming discussion progress");
                check(scalar("SELECT value FROM meta WHERE key='profile'")==state.getLong("profile",-1)&&scalar("SELECT COUNT(*) FROM feedback")==0,"R8 discussion updates never invent interest feedback or platform preference changes");
                check(body.isFile()&&android.system.Os.stat(body.getPath()).st_ino==inode,"updated disk body is reused without being rewritten by preload or explicit refresh");state.edit().clear().commit();
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private Set<String> batchUrls(long snapshot,int count)throws Exception{
        Set<String> urls=new LinkedHashSet<>();for(int n=0;n<count;n++)urls.add(new JSONObject(string("SELECT payload FROM snapshot_rows WHERE snapshot="+snapshot+" AND position="+n)).getString("url"));return urls;
    }
    private void waitBatchPrepared(Set<String> urls)throws Exception{
        List<String> identities=new ArrayList<>();for(String url:urls)identities.add(hash("THEPAPER\n"+url));
        String sql="SELECT COUNT(DISTINCT identity) FROM article_progress WHERE latest_at>0 AND identity IN ('"+String.join("','",identities)+"')";
        long until=SystemClock.elapsedRealtime()+15000;while(SystemClock.elapsedRealtime()<until&&scalar(sql)<urls.size())Thread.sleep(30);
        check(scalar(sql)==urls.size(),"every displayed R8 article has actual prepared-body evidence before any article click");
    }
    void runCurrentBatchCache(){
        try{
            if(!isolatedReplayTarget())throw new IllegalStateException("Dedicated flow package required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"whole-batch cache target is actual R8 without production reflection");
            launch();waitHome(10000);ui(home::finish);test.waitForIdleSync();Thread.sleep(500);home=null;
            seedFeedback=new JSONArray();seed();long now=System.currentTimeMillis();
            for(int n=6;n<18;n++){String title="有限批次正文缓存验证第"+n+"项";JSONObject value=new JSONObject(candidates.get(0).toString()).put("title",title).put("rank",n+1);candidates.add(value);ids.add("");hashes.add(hash("hot-title-v2\n"+title+"\n"+value.getString("snippet")));}
            for(int n=0;n<candidates.size();n++){String url="https://www.thepaper.cn/newsDetail_forward_"+(now+n+1000);candidates.get(n).put("source","THEPAPER").put("url",url);ids.set(n,hash("THEPAPER\n"+url));}
            persistSeed(now,true,true);try(SQLiteDatabase fixtureDb=db()){fixtureDb.delete("feed_exposure",null,null);}
            File root=new File(context().getCacheDir(),"article-bodies-v1");String namespace=context().getSharedPreferences("article-cache",0).getString("namespace","initial");Map<String,File> files=new LinkedHashMap<>();Map<String,byte[]> payloads=new LinkedHashMap<>();
            for(JSONObject candidate:candidates){String url=candidate.getString("url");String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie(url));File file=new File(root,hash(namespace+"\n"+fixtureSession(url,cookie[0])+"\n"+url)+".json");JSONObject fixture=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));fixture.getJSONObject("document").put("sourceUpdateEvidence",new JSONObject().put("version","source-modified-v1").put("modifiedAt",now-1000));JSONObject section=fixture.getJSONObject("document").getJSONArray("sections").getJSONObject(0);section.put("label","正文");JSONArray prose=new JSONArray();java.util.Random random=new java.util.Random(20261029);for(int p=0;p<24;p++){StringBuilder text=new StringBuilder("合成缓存验证文字：");for(int c=0;c<80;c++)text.append((char)(0x4e00+random.nextInt(1800)));prose.put(new JSONObject().put("type","text").put("value",text.toString()).put("images",new JSONArray()).put("links",new JSONArray()));}section.put("blocks",prose);byte[] bytes=fixture.toString().getBytes(StandardCharsets.UTF_8);java.nio.file.Files.write(file.toPath(),bytes);files.put(url,file);payloads.put(url,bytes);}
            launch();waitHome(10000);waitText(candidates.get(0).getString("title"),10000);int[] count={0};ui(()->count[0]=view(home.getWindow().getDecorView(),ListView.class).getCount());long first=active();Set<String> before=batchUrls(first,count[0]);
            check(count[0]>3&&count[0]<18,"whole finite batch is smaller than the full source pool and larger than three topics");waitBatchPrepared(before);
            long until=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<until&&Arrays.stream(root.listFiles()).filter(f->f.getName().endsWith(".json")).count()!=before.size())Thread.sleep(30);
            check(Arrays.stream(root.listFiles()).filter(f->f.getName().endsWith(".json")).count()==before.size(),"R8 initialization removes off-batch body records instead of retaining the full source pool");
            until=SystemClock.elapsedRealtime()+25000;while(!shown("查看更新")&&SystemClock.elapsedRealtime()<until)Thread.sleep(100);
            check(shown("查看更新"),"actual local computation stages an update so the test does not request live source collection");
            // Reintroduce controlled disk acquisitions for the upcoming batch,
            // without seeding progress, feedback, session scope or a fake callback.
            for(Map.Entry<String,byte[]> entry:payloads.entrySet())if(!before.contains(entry.getKey()))try(FileOutputStream out=new FileOutputStream(files.get(entry.getKey()))){out.write(entry.getValue());}
            Map<String,Long> inodes=new HashMap<>();for(String url:before)inodes.put(url,android.system.Os.stat(files.get(url).getPath()).st_ino);
            long feedback=scalar("SELECT COUNT(*) FROM feedback");click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==first&&SystemClock.elapsedRealtime()<until)Thread.sleep(30);
            long next=active();ui(()->count[0]=view(home.getWindow().getDecorView(),ListView.class).getCount());Set<String> after=batchUrls(next,count[0]);check(next!=first&&!before.equals(after),"real update dot accepts a different finite content batch");waitBatchPrepared(after);
            boolean overlap=false,kept=true,removed=true;for(String url:before){if(after.contains(url)){overlap=true;kept&=files.get(url).isFile()&&android.system.Os.stat(files.get(url).getPath()).st_ino==inodes.get(url);}else removed&=!files.get(url).exists();}
            check(overlap&&kept,"shared body files retain their inode across actual refresh without redundant rewriting");check(removed,"old non-shared body records disappear after successful R8 replacement");
            check(scalar("SELECT COUNT(*) FROM feedback")==feedback,"whole-batch preparation and eviction create no interest feedback");
            until=SystemClock.elapsedRealtime()+5000;while(shown("正在刷新")&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);check(!shown("正在刷新"),"refresh indicator completes independently of remaining model/cache work");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("scope=framework-only R8 finite batch, controlled disk acquisitions and real preloader/body evidence; not live network/login or phone energy\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void runRefresh(){runRefresh(false);}
    void runRefresh(boolean news){
        try{
            if(!isolatedReplayTarget())throw new IllegalStateException("Dedicated flow package required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"refresh target is non-debuggable/minified; no production-class reflection");
            launch();waitHome(10000);ui(home::finish);test.waitForIdleSync();Thread.sleep(700);home=null;
            seedFeedback=new JSONArray();seed();long now=System.currentTimeMillis();
            for(int n=6;n<30;n++){String title="离线刷新合成候选第"+n+"项",url="https://www.v2ex.com/t/"+(now+n+1000);JSONObject value=new JSONObject(candidates.get(0).toString()).put("url",url).put("title",title).put("rank",n+1);candidates.add(value);ids.add(hash("V2EX\n"+url));hashes.add(hash("hot-title-v2\n"+title+"\n"+value.getString("snippet")));}
            if(news)for(int n=0;n<candidates.size();n++){String url="https://www.thepaper.cn/newsDetail_forward_"+(now+n+1000);candidates.get(n).put("source","THEPAPER").put("url",url);ids.set(n,hash("THEPAPER\n"+url));}
            persistSeed(now,true,true);launch();waitHome(10000);waitText(candidates.get(0).getString("title"),10000);
            int[] homeCount={0};ui(()->homeCount[0]=view(home.getWindow().getDecorView(),ListView.class).getCount());
            check(homeCount[0]>0&&homeCount[0]<20,"actual R8 home adapts its finite presentation from a 30-candidate pool");
            String currentExposure="SELECT COUNT(*) FROM feed_exposure WHERE key='member:"+ids.get(0)+"' AND shown_at>0";
            long until=SystemClock.elapsedRealtime()+10000,original=0;while(SystemClock.elapsedRealtime()<until){original=active();if(original>0&&scalar(currentExposure)>0)break;Thread.sleep(100);}
            check(original>0&&scalar(currentExposure)>0,"actual minified home dwell writes this run's visible-card exposure without test-injected seen flags");
            check(scalar("SELECT COUNT(*) FROM feed_exposure WHERE key='member:"+ids.get(25)+"'")==0,"offscreen candidate is not exposed by binding or prefetch");
            String first=string("SELECT event_id FROM snapshot_rows WHERE snapshot="+original+" ORDER BY position LIMIT 1");
            String expected=string("SELECT s.event_id FROM snapshot_rows s WHERE s.snapshot="+original+" AND NOT EXISTS (SELECT 1 FROM feed_exposure e WHERE e.key='event:'||s.event_id AND e.shown_at>0) ORDER BY s.position LIMIT 1");
            report.append("refreshFixture original=").append(original).append(" first=").append(first).append(" expected=").append(expected).append(" currentMemberExposed=").append(scalar(currentExposure)).append('\n');
            long profile=scalar("SELECT value FROM meta WHERE key='profile'");
            check(expected!=null&&!expected.equals(first),"fixture has a measured visible first card and an eligible unseen candidate");
            click(shown("查看更新")?"查看更新":"刷新");until=SystemClock.elapsedRealtime()+10000;while(active()==original&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);long refreshed=active();
            check(refreshed!=original&&expected.equals(string("SELECT event_id FROM snapshot_rows WHERE snapshot="+refreshed+" ORDER BY position LIMIT 1")),"real minified update dot presents first unseen same-band candidate");
            check(scalar("SELECT COUNT(*) FROM snapshot_rows WHERE snapshot="+refreshed+" AND json_extract(payload,'$.interestBand')=1")==30,"classified band metadata survives R8 and real snapshot persistence");
            check(scalar("SELECT value FROM meta WHERE key='profile'")==profile&&scalar("SELECT COUNT(*) FROM feedback")==0,"minified exposure and refresh do not invent interest feedback");
            until=SystemClock.elapsedRealtime()+10000;while(shown("正在刷新")&&SystemClock.elapsedRealtime()<until)Thread.sleep(20);test.waitForIdleSync();
            check(!shown("正在刷新"),"actual refreshed R8 home completes its busy indication after presentation");
            int[] beforeBottom={0};ui(()->{ListView list=view(home.getWindow().getDecorView(),ListView.class);beforeBottom[0]=list.getCount();list.setSelectionFromTop(beforeBottom[0]-1,0);});test.waitForIdleSync();Thread.sleep(300);
            ui(()->homeCount[0]=view(home.getWindow().getDecorView(),ListView.class).getCount());check(homeCount[0]==beforeBottom[0]&&homeCount[0]>0,"scrolling to the actual R8 batch bottom does not append cards");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void run(){run(false,true);}
    void runExport(){run(true,true);}
    void runCore(){run(false,false);}
    private void run(boolean exportOnly,boolean includeExport){
        try{
            if(!isolatedReplayTarget())throw new IllegalStateException("Dedicated flow package required");
            check((context().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"flow target is genuinely non-debuggable and minified");
            if(exportOnly){launch();waitHome(10000);exportFlow();}
            else{prepareModels();seed();flow();trialFlow();prospectiveFlow();if(includeExport)exportFlow();else report.append("SAF export excluded from this core-only run; no public test files created\n");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');dump(automation().getRootInActiveWindow(),0);try{screenshot("release-reading-flow-failure.png");}catch(Exception ignored){}}
        finally{restoreReplayScreens();ui(()->{if(reader!=null&&!reader.isFinishing())reader.finish();if(home!=null&&!home.isFinishing())home.finish();});test.waitForIdleSync();try{modelReceipt("after-workload");}catch(Exception error){failures++;report.append("ERROR model receipt observation: ").append(error.getClass().getSimpleName()).append('\n');}}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void dump(AccessibilityNodeInfo node,int depth){if(node==null||depth>9)return;if(node.getText()!=null){Rect rect=new Rect();node.getBoundsInScreen(rect);report.append("UI ").append(node.getClassName()).append(" visible=").append(node.isVisibleToUser()).append(" bounds=").append(rect).append(' ').append(node.getText()).append('\n');}for(int n=0;n<node.getChildCount();n++)dump(node.getChild(n),depth+1);}
    private void screenshot(String name)throws Exception{android.graphics.Bitmap bitmap=automation().takeScreenshot();if(bitmap==null)throw new IOException("Screenshot unavailable");File directory=context().getExternalFilesDir(null);if(directory==null)throw new IOException("Screenshot directory unavailable");try(FileOutputStream out=new FileOutputStream(new File(directory,name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}
}

