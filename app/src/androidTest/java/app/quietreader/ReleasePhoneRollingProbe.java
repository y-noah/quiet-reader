package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Ordinary signed phone app, framework UI and read-only SQL/cache evidence. No fixtures or preference writes. */
final class ReleasePhoneRollingProbe {
    private final Instrumentation test;
    private Activity home,reader;private ListView list;private View toolbar;
    private Context context;private int checks,failures;private long started;
    private final StringBuilder report=new StringBuilder();private final JSONObject evidence=new JSONObject();
    private File output;
    ReleasePhoneRollingProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier action){boolean[] r={false};ui(()->r[0]=action.getAsBoolean());return r[0];}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');save();}
    private void log(String value){report.append(value).append('\n');save();}
    private void save(){if(output==null)return;try{evidence.put("elapsedMs",SystemClock.elapsedRealtime()-started).put("checks",checks).put("failures",failures).put("report",report.toString());Files.write(output.toPath(),evidence.toString(2).getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
    private void await(String label,BooleanSupplier condition,long ms)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(label);Thread.sleep(50);}}
    private SQLiteDatabase db(){return SQLiteDatabase.openDatabase(context.getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);}
    private long scalar(String sql){try(SQLiteDatabase d=db();Cursor c=d.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private long active(){return scalar("SELECT value FROM meta WHERE key='active_snapshot'");}
    private List<JSONObject> rows(int limit)throws Exception{List<JSONObject> result=new ArrayList<>();long id=active();try(SQLiteDatabase d=db();Cursor c=d.rawQuery("SELECT payload FROM snapshot_rows WHERE snapshot=? ORDER BY position LIMIT ?",new String[]{""+id,""+limit})){while(c.moveToNext())result.add(new JSONObject(c.getString(0)));}return result;}
    private static String hash(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())||root instanceof TextView&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View hit=find(((ViewGroup)root).getChildAt(i),label);if(hit!=null)return hit;}return null;}
    private <T extends View>T view(View root,Class<T> type){if(type.isInstance(root))return type.cast(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){T hit=view(((ViewGroup)root).getChildAt(i),type);if(hit!=null)return hit;}return null;}
    private WebView web(){WebView[] w={null};ui(()->{w[0]=view(reader.getWindow().getDecorView(),WebView.class);if(w[0]!=null&&(w[0].getUrl()==null||!w[0].getUrl().startsWith("https://quiet-reader.invalid/?render=")))w[0]=null;});return w[0];}
    private void launch(){home=test.startActivitySync(new Intent().setClassName(context.getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));ui(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));}
    private void homeReady()throws Exception{await("phone home ready",()->{list=view(home.getWindow().getDecorView(),ListView.class);View settings=find(home.getWindow().getDecorView(),"设置");if(settings!=null)toolbar=(View)settings.getParent();return home.hasWindowFocus()&&list!=null&&list.getChildCount()>0&&list.getCount()>1&&toolbar!=null;},185000);}
    private JSONObject cached(JSONObject row)throws Exception{
        String url=row.getString("url");String[] cookie={null};ui(()->cookie[0]=CookieManager.getInstance().getCookie(url));
        String namespace=context.getSharedPreferences("article-cache",0).getString("namespace","initial");File file=new File(context.getCacheDir(),"article-bodies-v1/"+hash(namespace+"\n"+fixtureSession(url,cookie[0])+"\n"+url)+".json");
        if(!file.isFile()||file.length()>2*1024*1024)return null;
        JSONObject entry=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));long age=System.currentTimeMillis()-entry.optLong("at");JSONObject doc=entry.getJSONObject("document");
        return age>=0&&age<=600000&&url.equals(doc.optString("url"))&&hash("hot-title-v2\n"+row.getString("title")+"\n"+row.optString("snippet")).equals(doc.optString("metadataHash"))?doc.put("_probeAt",entry.optLong("at")):null;
    }
    private Map<String,JSONObject> cachedRows(List<JSONObject> rows)throws Exception{Map<String,JSONObject> result=new LinkedHashMap<>();for(JSONObject row:rows){JSONObject doc=cached(row);if(doc!=null)result.put(row.getString("url"),doc);}return result;}
    private Map<String,Integer> missing(List<JSONObject> rows,Map<String,JSONObject> hits)throws Exception{Map<String,Integer> counts=new LinkedHashMap<>();for(JSONObject row:rows)if(!hits.containsKey(row.getString("url")))counts.merge(row.getString("source"),1,Integer::sum);return counts;}
    private void swipe(View surface,boolean forward)throws Exception{int[] b=new int[4];ui(()->{int[] xy=new int[2];surface.getLocationOnScreen(xy);b[0]=xy[0];b[1]=xy[1];b[2]=surface.getWidth();b[3]=surface.getHeight();});float x=b[0]+b[2]*.45f,from=b[1]+b[3]*(forward?.8f:.25f),to=b[1]+b[3]*(forward?.3f:.8f);long t=SystemClock.uptimeMillis();for(int i=0;i<=15;i++){android.view.MotionEvent e=android.view.MotionEvent.obtain(t,t+i*25,i==0?0:i==15?1:2,x,from+(to-from)*i/15f,0);test.sendPointerSync(e);e.recycle();if(i<15)Thread.sleep(25);}Thread.sleep(700);}
    private View dot(){View root=home.getWindow().getDecorView();View value=find(root,"刷新");if(value==null)value=find(root,"查看更新");if(value==null)value=find(root,"正在刷新");return value;}
    private Map<String,String> files(){Map<String,String> result=new TreeMap<>();for(String name:List.of("article-bodies-v1","reader-images-v1")){File root=new File(context.getCacheDir(),name);File[] all=root.listFiles();if(all!=null)for(File f:all)if(f.isFile())result.put(name+"/"+f.getName(),f.length()+":"+f.lastModified());}return result;}
    private JSONObject dom(WebView page)throws Exception{CountDownLatch latch=new CountDownLatch(1);String[] value={"{}"};ui(()->{boolean enabled=page.getSettings().getJavaScriptEnabled();page.getSettings().setJavaScriptEnabled(true);page.evaluateJavascript("JSON.stringify({len:document.body?.innerText?.length||0,parts:document.querySelectorAll('main .part').length,overflow:document.documentElement.scrollWidth>innerWidth+2})",r->{page.getSettings().setJavaScriptEnabled(enabled);value[0]=r;latch.countDown();});});if(!latch.await(4,TimeUnit.SECONDS))throw new IllegalStateException("reader DOM deadline");Object v=new JSONTokener(value[0]).nextValue();return v instanceof String?new JSONObject((String)v):new JSONObject();}
    private void readerCheck(List<JSONObject> visible)throws Exception{
        int selected=-1;for(int i=0;i<visible.size();i++){JSONObject doc=cached(visible.get(i));if(doc!=null&&doc.toString().length()>2500){selected=i;break;}}
        if(selected<0){log("READER no long cached article in this screen; cannot certify cache-open latency");return;}
        final int at=selected;final JSONObject item=visible.get(at);test.waitForIdleSync();ui(()->list.setSelectionFromTop(at,list.getPaddingTop()));Thread.sleep(250);
        Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);long click=SystemClock.elapsedRealtime();
        try{ui(()->{View wrapper=list.getChildAt(at-list.getFirstVisiblePosition());if(wrapper instanceof ViewGroup&&((ViewGroup)wrapper).getChildCount()>0)((ViewGroup)wrapper).getChildAt(0).performClick();});reader=test.waitForMonitorWithTimeout(monitor,15000);}finally{test.removeMonitor(monitor);}
        if(reader==null)throw new IllegalStateException("cached reader did not open");long until=SystemClock.elapsedRealtime()+15000;WebView page=null;JSONObject state=new JSONObject();
        while(SystemClock.elapsedRealtime()<until){page=web();if(page!=null){state=dom(page);if(state.optInt("parts")>0&&state.optInt("len")>100)break;}Thread.sleep(100);}
        long latency=SystemClock.elapsedRealtime()-click;log("READER source="+item.getString("source")+" readyMs="+latency+" parts="+state.optInt("parts")+" chars="+state.optInt("len"));check(state.optInt("parts")>0&&state.optInt("len")>100,"real cached article renders text in common reader");check(!state.optBoolean("overflow"),"real reader has no horizontal overflow");
        check(onUi(()->find(reader.getWindow().getDecorView(),"返回")==null),"phone reader has no redundant back button");
        WebView target=page;View[] bar={null};int[] height={0};ui(()->{View more=find(reader.getWindow().getDecorView(),"更多选项");bar[0]=(View)more.getParent();height[0]=target.getHeight();});
        swipe(target,true);boolean moved=onUi(()->target.getScrollY()>0);check(moved&&onUi(()->bar[0].getTranslationY()<0),"real downward reader gesture hides toolbar");Thread.sleep(500);check(onUi(()->bar[0].getTranslationY()<0&&target.getHeight()==height[0]),"reader idle stays hidden without resizing text viewport");swipe(target,false);check(onUi(()->bar[0].getTranslationY()==0),"real upward reader gesture restores toolbar");
        while(SystemClock.elapsedRealtime()-click<8500)Thread.sleep(100);
        test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("return to same home",()->home.hasWindowFocus(),7000);reader=null;
    }
    void run(Bundle args){started=SystemClock.elapsedRealtime();context=test.getTargetContext();output=new File(context.getExternalFilesDir(null),"phone-rolling-cache.json");
        try{
            check("true".equals(args.getString("phone"))&&"app.quietreader".equals(context.getPackageName())&&"PHY110".equals(Build.MODEL)&&Build.VERSION.SDK_INT==36,"confined to authorized Android16 Find X7 Ultra");check((context.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"installed phone app is original signed non-debuggable release");
            long feedback=scalar("SELECT COUNT(*) FROM feedback");launch();homeReady();check(onUi(()->list.getCount()==15),"real retained homepage contains fifteen rows");
            ui(()->list.setSelectionFromTop(0,list.getPaddingTop()));Thread.sleep(250);List<JSONObject> initial=rows(75);View[] title={null};ui(()->{try{title[0]=find(home.getWindow().getDecorView(),initial.get(0).getString("title"));}catch(Exception ignored){}});log("HOME rows="+onUi(()->list.getCount()==15)+" titleSp="+(title[0] instanceof TextView?((TextView)title[0]).getTextSize()/context.getResources().getDisplayMetrics().scaledDensity:-1));check(title[0] instanceof TextView&&Math.abs(((TextView)title[0]).getTextSize()-android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,15,context.getResources().getDisplayMetrics()))<.1,"actual phone home title is fifteen sp");
            if("true".equals(args.getString("diagnose"))){diagnoseMisses(rows(15));check(feedback==scalar("SELECT COUNT(*) FROM feedback"),"miss diagnosis created no preference feedback");}else if(!"true".equals(args.getString("layoutOnly"))){
            boolean paced="true".equals(args.getString("paced"));
            long warmUntil=SystemClock.elapsedRealtime()+175000;int count=-1;
            while(SystemClock.elapsedRealtime()<warmUntil){List<JSONObject> pool=rows(75);Map<String,JSONObject> hit=cachedRows(pool);count=hit.size();int current=cachedRows(rows(15)).size();log("WARM total="+pool.size()+" diskReady="+count+" currentReady="+current+" missing="+missing(pool,hit));if(count==pool.size()&&pool.size()>=75)break;Thread.sleep(15000);}
            List<JSONObject> visible=rows(15);Set<String> shown=new HashSet<>();for(JSONObject row:visible)shown.add(row.getString("url"));
            readerCheck(visible);ui(()->list.setSelectionFromTop(0,list.getPaddingTop()));Thread.sleep(600);swipe(list,true);check(onUi(()->toolbar.getTranslationY()<0),"phone home downward gesture hides toolbar");swipe(list,false);check(onUi(()->toolbar.getTranslationY()==0),"phone home upward gesture restores toolbar");
            for(int round=0;round<4;round++){
                long beforeWall=System.currentTimeMillis();long previous=active(),t=SystemClock.elapsedRealtime();ui(()->{list.setSelectionFromTop(0,list.getPaddingTop());View refresh=dot();refresh.performClick();refresh.performClick();refresh.performClick();});
                long end=SystemClock.elapsedRealtime()+20000;while((active()==previous||onUi(()->find(home.getWindow().getDecorView(),"正在刷新")!=null))&&SystemClock.elapsedRealtime()<end)Thread.sleep(30);
                visible=rows(15);int hit=0,repeat=0;Map<String,Integer> late=new LinkedHashMap<>();for(JSONObject row:visible){String url=row.getString("url");JSONObject prior=cached(row);if(prior!=null&&prior.optLong("_probeAt")<=beforeWall)hit++;else late.merge(row.getString("source"),1,Integer::sum);if(!shown.add(url))repeat++;}
                log("REFRESH round="+(round+1)+" ms="+(SystemClock.elapsedRealtime()-t)+" rows="+visible.size()+" cachedBefore="+hit+" lateSources="+late+" repeated="+repeat+" pendingSpinner="+onUi(()->find(home.getWindow().getDecorView(),"正在刷新")!=null));
                check(active()!=previous&&onUi(()->list.getCount()==15),"real refresh publishes fifteen rows");check(!onUi(()->find(home.getWindow().getDecorView(),"正在刷新")!=null),"refresh spinner ends with the accepted list");check(repeat==0,"successive phone rounds do not repeat earlier screen members");check(cachedRows(visible).size()==15,"all fifteen published bodies are already persisted and version/session matched");check(hit==15,"all fifteen bodies were prepared before the refresh tap");
                // A failed deadline is already recorded. Settle that same operation before
                // measuring another round; never label stale pre-publication rows as current.
                if(active()==previous||onUi(()->find(home.getWindow().getDecorView(),"正在刷新")!=null)){
                    long settle=SystemClock.elapsedRealtime()+185000;
                    while(onUi(()->find(home.getWindow().getDecorView(),"正在刷新")!=null)&&SystemClock.elapsedRealtime()<settle)Thread.sleep(100);
                    visible=rows(15);log("SETTLED round="+(round+1)+" totalMs="+(SystemClock.elapsedRealtime()-t)+" changed="+(active()!=previous));
                }
                if(paced){Thread.sleep(15000);swipe(list,true);swipe(list,false);Thread.sleep(15000);Map<String,JSONObject> supplied=cachedRows(visible);log("DWELL round="+(round+1)+" currentReady="+supplied.size()+" missing="+missing(visible,supplied)+" "+cacheSize());}else Thread.sleep(2000);
            }
            readerCheck(visible);
            if(!paced&&!"true".equals(args.getString("skipPeriodic"))){
            long stable=active(),requests=scalar("SELECT SUM(requested) FROM sources");boolean periodic=false;long due=started+365000;
            while(SystemClock.elapsedRealtime()<due){long now=scalar("SELECT SUM(requested) FROM sources");if(now>requests){periodic=true;break;}log("PERIODIC waiting requested="+now);Thread.sleep(10000);}
            check(periodic,"actual foreground five-minute source check starts without a tap");check(active()==stable,"automatic source update preserves the visible presentation");
            }
            log("FINAL "+cacheSize());
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_HOME);await("home background",()->!home.hasWindowFocus(),5000);Thread.sleep(5000);Map<String,String> stopped=files();long stoppedRequests=scalar("SELECT SUM(requested) FROM sources");Thread.sleep(10000);
            check(stopped.equals(files())&&stoppedRequests==scalar("SELECT SUM(requested) FROM sources"),"background stops new source requests and body/image file writes after settling");check(feedback==scalar("SELECT COUNT(*) FROM feedback"),"test clicks and brief reader navigation created no preference feedback");
            }else summaryCheck();
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));save();}
        finally{ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');save();Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void diagnoseMisses(List<JSONObject> visible)throws Exception{
        int sampled=0;
        for(int n=0;n<visible.size()&&sampled<2;n++){
            JSONObject item=visible.get(n);if(cached(item)!=null)continue;sampled++;final int at=n;
            ui(()->list.setSelectionFromTop(at,list.getPaddingTop()));Thread.sleep(250);
            Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);long click=SystemClock.elapsedRealtime();
            try{ui(()->{View wrapper=list.getChildAt(at-list.getFirstVisiblePosition());if(wrapper instanceof ViewGroup&&((ViewGroup)wrapper).getChildCount()>0)((ViewGroup)wrapper).getChildAt(0).performClick();});reader=test.waitForMonitorWithTimeout(monitor,15000);}finally{test.removeMonitor(monitor);}
            if(reader==null)throw new IllegalStateException("uncached reader did not open");JSONObject state=new JSONObject();long deadline=SystemClock.elapsedRealtime()+30000;
            while(SystemClock.elapsedRealtime()<deadline){WebView page=web();if(page!=null){state=dom(page);if(state.optInt("parts")>0&&state.optInt("len")>100)break;}Thread.sleep(200);}
            boolean[] gate={false},retry={false};ui(()->scanStatus(reader.getWindow().getDecorView(),gate,retry));
            log("MISS source="+item.getString("source")+" elapsedMs="+(SystemClock.elapsedRealtime()-click)+" rendered="+(state.optInt("parts")>0&&state.optInt("len")>100)+" parts="+state.optInt("parts")+" chars="+state.optInt("len")+" nativeLoginText="+gate[0]+" nativeRetryText="+retry[0]+" cachedAfter="+(cached(item)!=null));
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await("miss returns home",()->home.hasWindowFocus(),7000);reader=null;Thread.sleep(1000);
        }
        log("MISS sampled="+sampled);check(sampled>0,"at least one actual visible cache miss sampled");
    }
    private void scanStatus(View root,boolean[] login,boolean[] retry){if(root instanceof TextView){String t=((TextView)root).getText().toString();login[0]|=t.contains("登录");retry[0]|=t.contains("重试")||t.contains("重新读取");}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)scanStatus(((ViewGroup)root).getChildAt(n),login,retry);}
    private String cacheSize(){long bodies=0,images=0;int bodyCount=0,imageCount=0;for(String name:List.of("article-bodies-v1","reader-images-v1")){File[] all=new File(context.getCacheDir(),name).listFiles();if(all!=null)for(File file:all)if(file.isFile()){if(name.startsWith("article")){bodies+=file.length();bodyCount++;}else{images+=file.length();imageCount++;}}}return "bodyFiles="+bodyCount+" bodyBytes="+bodies+" imageFiles="+imageCount+" imageBytes="+images;}
    private void summaryCheck()throws Exception{int[] count={0},bad={0};ui(()->scanSummaries(home.getWindow().getDecorView(),count,bad));check(count[0]>0&&bad[0]==0,"home summaries are at most one line with ellipsis");check(cachedRows(rows(15)).size()==15,"all current bodies remain persisted and session/version matched after reopening");}
    private void scanSummaries(View root,int[] count,int[] bad){if(root instanceof TextView){TextView t=(TextView)root;if(t.getEllipsize()==android.text.TextUtils.TruncateAt.END){count[0]++;if(t.getMaxLines()!=1||t.getLineCount()>1)bad[0]++;}}if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)scanSummaries(((ViewGroup)root).getChildAt(i),count,bad);}
    private String fixtureSession(String url,String raw)throws Exception{
        if(raw==null)raw="";
        java.net.URI uri=java.net.URI.create(url);String host=uri.getHost(),path=uri.getPath();
        if("https".equals(uri.getScheme())&&"www.zhihu.com".equals(host)&&uri.getPort()==-1&&path.matches("/question/[1-9][0-9]*(?:/answer/[1-9][0-9]*)?/?")){
            // Read-only cache-key comparison; raw cookies never leave memory.
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
            fields.sort(Comparator.comparing(ReleasePhoneRollingProbe::fixtureCookieName));
            return hash("zhihu-content-v2;"+String.join("; ",fields));
        }
        boolean tieba="tieba.baidu.com".equals(host)&&(path.matches("/p/\\d+/?")||path.startsWith("/hottopic/"));
        if("www.baidu.com".equals(host)&&"/s".equals(path)||tieba){
            List<String> account=new ArrayList<>();
            for(String field:raw.split(";")){int at=field.indexOf('=');if(at<=0)continue;String name=field.substring(0,at).trim().toUpperCase(Locale.ROOT);if(Set.of("BAIDUID","BAIDUID_BFESS","BAIDU_WISE_UID","HMACCOUNT").contains(name))continue;if(name.matches("BDUSS.*|[PS]TOKEN.*|PASSID.*|.*(?:SESSION|AUTH|TOKEN|LOGIN|USER|ACCOUNT).*|.*UID|SID|SSON"))account.add(field.trim());}
            Collections.sort(account);return hash((tieba?"tieba-account-v2;":"baidu-account-v1;")+String.join("; ",account));
        }
        return hash(raw);
    }
    private static String fixtureCookieName(String value){int at=value.indexOf('=');return at>0?value.substring(0,at).trim():value;}
}
