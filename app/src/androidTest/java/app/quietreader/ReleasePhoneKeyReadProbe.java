package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** At most four real article opens; deliberately not a bulk phone campaign. */
final class ReleasePhoneKeyReadProbe {
    private final Instrumentation test;
    private Activity home,reader;
    private ListView list;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReleasePhoneKeyReadProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier action){boolean[] result={false};ui(()->result[0]=action.getAsBoolean());return result[0];}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(BooleanSupplier condition,String label)throws Exception{long until=SystemClock.elapsedRealtime()+15000;do{if(onUi(condition))return;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<until);throw new IllegalStateException(label);}
    private <T extends View>T find(View root,Class<T> kind){if(kind.isInstance(root))return kind.cast(root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){T found=find(((ViewGroup)root).getChildAt(n),kind);if(found!=null)return found;}return null;}
    private WebView reading(View root){if(root instanceof WebView){String url=((WebView)root).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/?render="))return (WebView)root;}if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){WebView found=reading(((ViewGroup)root).getChildAt(n));if(found!=null)return found;}return null;}
    private void texts(View view,List<String> out){if(view instanceof TextView)out.add(((TextView)view).getText().toString());if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)texts(((ViewGroup)view).getChildAt(n),out);}
    private long feedback(){try(SQLiteDatabase db=SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor cursor=db.rawQuery("SELECT COUNT(*) FROM feedback",null)){return cursor.moveToFirst()?cursor.getLong(0):0;}}
    private JSONObject snapshot(WebView web)throws Exception{CountDownLatch done=new CountDownLatch(1);String[] value={"null"};ui(()->{boolean was=web.getSettings().getJavaScriptEnabled();web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify({parts:document.querySelectorAll('main .part').length,chars:(document.querySelector('main')?.textContent||'').length,images:document.querySelectorAll('main img').length,overflow:document.documentElement.scrollWidth>innerWidth+2})",result->{web.getSettings().setJavaScriptEnabled(was);value[0]=result;done.countDown();});});if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("reader DOM timed out");Object result=new JSONTokener(value[0]).nextValue();return result instanceof String?new JSONObject((String)result):new JSONObject();}
    void diagnoseFailedHupu(){
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("only authorized phone");
            String url="";try(SQLiteDatabase db=SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor cursor=db.rawQuery("SELECT payload FROM candidates WHERE source='HUPU'",null)){
                while(cursor.moveToNext()){JSONObject row=new JSONObject(cursor.getString(0));if(row.optString("title").equals("现在的女生为了出片穿的真大胆")){url=row.getString("url");report.append("PUBLIC_ITEM ").append(url).append('\n');break;}}
            }
            if(!url.matches("https://bbs\\.hupu\\.com/[1-9][0-9]*\\.html"))throw new IllegalStateException("failed public thread not found");
            home=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.LoginActivity").putExtra("source","HUPU").putExtra("url",url).putExtra("board",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline=SystemClock.elapsedRealtime()+30_000;JSONObject latest=new JSONObject();
            do{WebView[] web={null};ui(()->web[0]=find(home.getWindow().getDecorView(),WebView.class));if(web[0]!=null){CountDownLatch done=new CountDownLatch(1);String[] value={"null"};ui(()->web[0].evaluateJavascript("JSON.stringify((()=>{try{const p=JSON.parse(document.getElementById('__NEXT_DATA__')?.textContent||'{}').props?.pageProps,d=p?.detail,t=d?.thread,r=d?.replies;return {path:location.pathname,hasState:!!p,threadId:t?.tid,threadUrl:t?.url,status:t?.status,range:d?.visibleRange,threadRange:t?.visibleRange,page:r?.current,replyCount:r?.list?.length,code:p?.detailErrorInfo?.code,contentChars:t?.content?.length||0,renderedChars:document.querySelector('[class*=post-content_main-post-info] .thread-content-detail')?.innerText?.length||0,renderedImages:document.querySelectorAll('[class*=post-content_main-post-info] .thread-content-detail img').length,hasVideo:t?.hasVideo,signedIn:/^[1-9][0-9]*$/.test(String(d?.user?.puid||''))}}catch(e){return {error:'state unavailable'}}})())",result->{value[0]=result;done.countDown();}));if(done.await(3,TimeUnit.SECONDS)){Object raw=new JSONTokener(value[0]).nextValue();if(raw instanceof String)latest=new JSONObject((String)raw);}if(latest.optBoolean("hasState"))break;}Thread.sleep(200);}while(SystemClock.elapsedRealtime()<deadline);
            report.append("PUBLIC_STRUCTURE ").append(latest).append('\n');
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(home!=null)home.finish();});}
        report.append("failures=").append(failures).append("; public identity/structure and signed-in boolean only; no cookies or account identifiers\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void verifyFailedHupu(){
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("only authorized phone");
            long before=feedback();JSONObject item=new JSONObject().put("source","HUPU").put("url","https://bbs.hupu.com/643478433.html").put("title","现在的女生为了出片穿的真大胆").put("detail","").put("video",false);
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY);Cursor cursor=db.rawQuery("SELECT payload FROM candidates WHERE source='HUPU'",null)){while(cursor.moveToNext()){JSONObject candidate=new JSONObject(cursor.getString(0));if(candidate.optString("url").equals(item.getString("url"))){item=candidate.put("detail",candidate.optString("heat"));break;}}}
            for(int n=0;n<2;n++){long start=SystemClock.elapsedRealtime();reader=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));JSONObject body=new JSONObject();long until=start+35_000;
                do{WebView[] own={null};ui(()->own[0]=reading(reader.getWindow().getDecorView()));if(own[0]!=null){body=snapshot(own[0]);if(body.optInt("parts")>0&&body.optInt("chars")>80)break;}Thread.sleep(120);}while(SystemClock.elapsedRealtime()<until);
                long elapsed=SystemClock.elapsedRealtime()-start;report.append("FAILED_THREAD_RECHECK warm=").append(n==1).append(" ms=").append(elapsed).append(" parts=").append(body.optInt("parts")).append(" chars=").append(body.optInt("chars")).append('\n');
                check(body.optInt("parts")>0&&body.optInt("chars")>80,"empty-main public thread now renders actual discussion");if(n==1)check(elapsed<3500,"reopen uses prepared body within 3.5 seconds");ui(reader::finish);test.waitForIdleSync();reader=null;
            }
            check(before==feedback(),"focused checks did not create preference feedback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; two focused opens of previously failed public thread\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void open(int position,boolean warm)throws Exception{
        ui(()->list.setSelectionFromTop(position,0));await(()->list.getFirstVisiblePosition()<=position&&list.getFirstVisiblePosition()+list.getChildCount()>position,"requested real row not visible");
        Instrumentation.ActivityMonitor monitor=test.addMonitor("app.quietreader.MainActivity",null,false);long start=SystemClock.elapsedRealtime();
        try{ui(()->{ViewGroup wrapper=(ViewGroup)list.getChildAt(position-list.getFirstVisiblePosition());if(!wrapper.getChildAt(0).performClick())throw new IllegalStateException("real card did not click");});reader=test.waitForMonitorWithTimeout(monitor,8000);}finally{test.removeMonitor(monitor);}
        if(reader==null)throw new IllegalStateException("actual reader Activity absent");JSONObject body=new JSONObject();String terminal="";
        try{long until=SystemClock.elapsedRealtime()+55_000;do{
            WebView[] own={null};List<String> nativeText=new ArrayList<>();ui(()->{own[0]=reading(reader.getWindow().getDecorView());texts(reader.getWindow().getDecorView(),nativeText);});
            if(own[0]!=null){body=snapshot(own[0]);if(body.optInt("parts")>0&&(body.optInt("chars")>80||body.optInt("images")>0))break;}
            for(String text:nativeText)if(Set.of("暂时没读到正文","需要来源页面协助","当前没有网络").contains(text))terminal=text;
            if(!terminal.isEmpty())break;Thread.sleep(120);
        }while(SystemClock.elapsedRealtime()<until);
        long elapsed=SystemClock.elapsedRealtime()-start;report.append("READ position=").append(position).append(" warm=").append(warm).append(" ms=").append(elapsed).append(" parts=").append(body.optInt("parts")).append(" chars=").append(body.optInt("chars")).append(" terminal=").append(terminal).append('\n');
        check(body.optInt("parts")>0&&(body.optInt("chars")>80||body.optInt("images")>0),"actual home card produces own-reader content");check(!body.optBoolean("overflow"),"own reader fits horizontal viewport");
        if(warm)check(elapsed<3500,"immediate reopen reuses body within 3.5 seconds");
        }finally{ui(reader::finish);test.waitForIdleSync();reader=null;}
        await(home::hasWindowFocus,"home did not resume after finishing reader");
    }
    void run(){
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("only authorized original phone app");
            long before=feedback();home=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->{list=find(home.getWindow().getDecorView(),ListView.class);return home.hasWindowFocus()&&list!=null&&list.getCount()==15&&list.getChildCount()>0;},"home did not show fifteen real topics");
            List<Integer> choices=new ArrayList<>();Set<String> sources=new HashSet<>();ui(()->{
                for(int position=0;position<15&&choices.size()<3;position++){List<String> labels=new ArrayList<>();texts(list.getAdapter().getView(position,null,list),labels);String source="";for(String label:labels)if(label.contains("获取于")||label.contains("来源时间"))source=label.split(" · ")[0];if(!source.isEmpty()&&sources.add(source)){choices.add(position);report.append("SELECT position=").append(position).append(" source=").append(source).append(" title=").append(labels.isEmpty()?"":labels.get(0)).append('\n');}}
                for(int fallback:new int[]{14,7,0})if(choices.size()<3&&!choices.contains(fallback))choices.add(fallback);
            });
            for(int position:choices)open(position,false);open(choices.get(choices.size()-1),true);
            check(before==feedback(),"key checks did not create preference feedback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; four real opens maximum, no claim of all-source availability\n");
        try(FileOutputStream output=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"phone-key-reads.txt"))){output.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
