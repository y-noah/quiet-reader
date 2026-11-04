package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Real installed phone UI only; no synthetic candidates, preferences, or reflection. */
final class ReleasePhoneSearchProbe {
    private final Instrumentation test;private Activity home;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReleasePhoneSearchProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String message){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(message).append('\n');}
    private <T extends View>T type(View view,Class<T> kind){if(view.getVisibility()!=View.VISIBLE)return null;if(kind.isInstance(view))return kind.cast(view);if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){T result=type(((ViewGroup)view).getChildAt(n),kind);if(result!=null)return result;}return null;}
    private View find(View view,String value){if(view.getVisibility()!=View.VISIBLE)return null;if(view instanceof TextView&&(value.contentEquals(((TextView)view).getText())||value.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View result=find(((ViewGroup)view).getChildAt(n),value);if(result!=null)return result;}return null;}
    private View root(){return home.getWindow().getDecorView();}
    private ListView list(){return type(root(),ListView.class);}
    private void texts(View view,List<String> out){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView&&!(((TextView)view).getText().length()==0))out.add(((TextView)view).getText().toString());if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)texts(((ViewGroup)view).getChildAt(n),out);}
    private void await(BooleanSupplier condition,String stage,long timeout)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;boolean[] ready={false};do{test.runOnMainSync(()->ready[0]=home.hasWindowFocus()&&condition.getAsBoolean());if(ready[0])return;Thread.sleep(60);}while(SystemClock.elapsedRealtime()<until);throw new IllegalStateException(stage);}
    private void query(String text)throws Exception{test.runOnMainSync(()->type(root(),EditText.class).setText(text));await(()->find(root(),"正在查找…")==null,"query did not settle",15000);}
    void diagnoseWeibo(){Activity reader=null;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("Authorized phone only");
            JSONObject selected=null;
            try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor rows=db.rawQuery("SELECT payload FROM candidates WHERE source='WEIBO'",null)){
                while(rows.moveToNext()){JSONObject item=new JSONObject(rows.getString(0));if("迪丽热巴 哈曼卡顿".equals(item.optString("title"))){selected=item.put("detail",item.optString("heat"));break;}}
            }
            if(selected==null)throw new IllegalStateException("Original failed topic no longer stored");
            home=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            reader=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",selected.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity current=reader;long started=SystemClock.elapsedRealtime(),end=started+35000;boolean terminal=false;
            do{
                List<String> labels=new ArrayList<>();test.runOnMainSync(()->texts(current.getWindow().getDecorView(),labels));
                if(labels.contains("需要来源页面协助")||labels.contains("暂时没读到正文")){report.append("FAILED_READER ").append(new JSONArray(labels)).append('\n');terminal=true;break;}
                android.webkit.WebView[] web={null};test.runOnMainSync(()->web[0]=type(current.getWindow().getDecorView(),android.webkit.WebView.class));
                if(web[0]!=null){java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);boolean[] ready={false};test.runOnMainSync(()->web[0].evaluateJavascript("document.querySelectorAll('main .part').length>0",value->{ready[0]="true".equals(value);done.countDown();}));done.await(2,java.util.concurrent.TimeUnit.SECONDS);if(ready[0]){report.append("RETRY readable=true\n");terminal=true;break;}}
                Thread.sleep(120);
            }while(SystemClock.elapsedRealtime()<end);
            report.append("DIAGNOSE elapsedMs=").append(SystemClock.elapsedRealtime()-started).append(" terminal=").append(terminal).append('\n');
        }catch(Throwable error){report.append("ERROR ").append(error).append('\n');}
        finally{Activity last=reader;test.runOnMainSync(()->{if(last!=null)last.finish();if(home!=null)home.finish();});}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(Activity.RESULT_OK,result);
    }
    private void webs(View root,List<android.webkit.WebView> out){if(root instanceof android.webkit.WebView)out.add((android.webkit.WebView)root);if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++)webs(((ViewGroup)root).getChildAt(n),out);}
    void diagnoseSources(){Activity reader=null;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("Authorized phone only");
            List<JSONObject> items=new ArrayList<>();
            try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor rows=db.rawQuery("SELECT payload FROM candidates WHERE source IN ('WEIBO','V2EX')",null)){
                while(rows.moveToNext()){JSONObject item=new JSONObject(rows.getString(0));if(Set.of("医学生请回答","迪丽热巴 哈曼卡顿").contains(item.optString("title")))items.add(item.put("detail",item.optString("heat")));}
            }
            report.append("DIAGNOSTIC candidates=").append(items.size()).append('\n');
            for(JSONObject item:items){
                reader=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",item.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Activity current=reader;long started=SystemClock.elapsedRealtime(),until=started+45000;String previous="";
                do{
                    List<String> labels=new ArrayList<>();List<android.webkit.WebView> pages=new ArrayList<>();test.runOnMainSync(()->{texts(current.getWindow().getDecorView(),labels);webs(current.getWindow().getDecorView(),pages);});
                    for(android.webkit.WebView web:pages){java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);String[] state={"callback-missing"};test.runOnMainSync(()->web.evaluateJavascript("JSON.stringify({host:location.hostname,path:location.pathname,ready:document.readyState,title:document.title,chars:document.body?document.body.innerText.length:0,v2ex:document.querySelectorAll('.topic_content,.reply_content').length,weibo:document.querySelectorAll('.card-wrap,.card-feed').length,own:document.querySelectorAll('main .part').length})",value->{state[0]=value;done.countDown();}));done.await(2,java.util.concurrent.TimeUnit.SECONDS);String snapshot=item.optString("source")+" "+state[0];if(!snapshot.equals(previous)){report.append("STATE ms=").append(SystemClock.elapsedRealtime()-started).append(' ').append(snapshot).append('\n');previous=snapshot;}}
                    if(labels.contains("需要来源页面协助")||labels.contains("暂时没读到正文")){report.append("TERMINAL ").append(new JSONArray(labels)).append('\n');break;}
                    if(previous.contains("\\\"own\\\":")&&!previous.contains("\\\"own\\\":0"))break;
                    Thread.sleep(700);
                }while(SystemClock.elapsedRealtime()<until);
                test.runOnMainSync(current::finish);test.waitForIdleSync();reader=null;
            }
        }catch(Throwable error){report.append("ERROR ").append(error).append('\n');}
        finally{Activity last=reader;test.runOnMainSync(()->{if(last!=null)last.finish();});}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(Activity.RESULT_OK,result);
    }
    void sampleWeibo(){sampleWeibo(false);}
    void sampleWeibo(boolean middle){Activity reader=null;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("Authorized phone only");
            List<JSONObject> items=new ArrayList<>();
            try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY);android.database.Cursor rows=db.rawQuery("SELECT payload FROM candidates WHERE source='WEIBO' AND active=1"+(middle?" AND rank BETWEEN 20 AND 22":"")+" ORDER BY rank DESC LIMIT 3",null)){
                while(rows.moveToNext()){JSONObject item=new JSONObject(rows.getString(0));items.add(item.put("detail",item.optString("heat")));}
            }
            check(items.size()==3,"three actual Weibo pool topics available; middle="+middle);if(items.size()!=3)throw new IllegalStateException("Insufficient Weibo sample");
            home=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            items.add(items.get(items.size()-1));
            for(int n=0;n<items.size();n++){
                JSONObject selected=items.get(n);long started=SystemClock.elapsedRealtime();
                reader=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.MainActivity").putExtra("direct_reader",true).putExtra("discovery_item",selected.toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Activity current=reader;boolean readable=false;String terminal="";long end=started+35000;
                do{
                    List<String> labels=new ArrayList<>();test.runOnMainSync(()->texts(current.getWindow().getDecorView(),labels));
                    if(labels.contains("需要来源页面协助")||labels.contains("暂时没读到正文")){terminal=new JSONArray(labels).toString();break;}
                    android.webkit.WebView[] web={null};test.runOnMainSync(()->web[0]=type(current.getWindow().getDecorView(),android.webkit.WebView.class));
                    if(web[0]!=null){java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);boolean[] ready={false};test.runOnMainSync(()->web[0].evaluateJavascript("document.querySelectorAll('main .part').length>0 && (document.querySelector('main').innerText.length>80 || document.querySelectorAll('main .part img').length>0)",value->{ready[0]="true".equals(value);done.countDown();}));done.await(2,java.util.concurrent.TimeUnit.SECONDS);if(ready[0]){readable=true;break;}}
                    Thread.sleep(100);
                }while(SystemClock.elapsedRealtime()<end);
                long elapsed=SystemClock.elapsedRealtime()-started;report.append("WEIBO sample=").append(n+1).append(" rank=").append(selected.optInt("rank")).append(" repeat=").append(n==3).append(" ms=").append(elapsed).append(" readable=").append(readable).append(" terminal=").append(terminal).append('\n');
                check(readable,"actual Weibo topic produces readable content: sample="+(n+1));if(n==3)check(elapsed<3500,"Weibo immediate cached reopen under 3.5s");
                test.runOnMainSync(current::finish);test.waitForIdleSync();reader=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{Activity last=reader;test.runOnMainSync(()->{if(last!=null)last.finish();if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; three real pool topics plus one reopen, no forced cache deletion\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void run(){
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("Authorized phone app only");
            home=test.startActivitySync(new Intent().setClassName(test.getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
            await(()->list()!=null&&list().getCount()==20&&list().getChildCount()>0,"home did not load twenty real topics",20000);
            String[] title={""};List<String> homeBefore=new ArrayList<>();
            test.runOnMainSync(()->{
                check(list().getCount()==20,"installed home shows twenty topics");
                View entry=find(root(),"搜索热搜池");check(entry instanceof TextView&&"搜索热搜".contentEquals(((TextView)entry).getText())&&entry.getWidth()>entry.getHeight()*2,"installed home uses a labeled wide search field");
                for(int n=0;n<20;n++){List<String> values=new ArrayList<>();texts(list().getAdapter().getView(n,null,list()),values);if(!values.isEmpty())homeBefore.add(values.get(0));}
                title[0]=homeBefore.get(0);list().setSelectionFromTop(19,0);
            });test.waitForIdleSync();
            test.runOnMainSync(()->{check(list().getCount()==20,"scrolling to home bottom does not append");find(root(),"搜索热搜池").performClick();});
            await(()->type(root(),EditText.class)!=null&&find(root(),"正在查找…")==null,"search entry did not settle",15000);
            test.runOnMainSync(()->{
                List<String> labels=new ArrayList<>();texts(root(),labels);int size=-1;
                java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("输入关键词，在整个热搜池的 (\\d+) 条内容中查找");
                for(String label:labels){java.util.regex.Matcher match=pattern.matcher(label);if(match.matches())size=Integer.parseInt(match.group(1));}
                report.append("POOL searchableRecords=").append(size).append('\n');check(size>=20&&size<=5000,"search shows the actual full-pool count");
            });
            String search=title[0].substring(0,title[0].offsetByCodePoints(0,Math.min(40,title[0].codePointCount(0,title[0].length()))));
            query(search);test.runOnMainSync(()->check(find(root(),title[0])!=null,"real title search returns its reading card"));
            String absent="无匹配验收"+UUID.randomUUID().toString().replace("-","");query(absent);
            test.runOnMainSync(()->check(find(root(),"热搜池里没有相关热搜")!=null,"absent query explicitly says no related hot topic"));
            test.runOnMainSync(()->{EditText input=type(root(),EditText.class);input.setText(search);input.setText(absent);input.setText(search);});
            await(()->find(root(),title[0])!=null,"rapid final query not published",15000);Thread.sleep(350);
            test.runOnMainSync(()->check(find(root(),title[0])!=null,"rapid queries retain the latest result"));
            query("");test.runOnMainSync(()->find(root(),"返回").performClick());
            await(()->find(root(),"设置")!=null,"home did not return",10000);
            test.runOnMainSync(()->{
                List<String> homeAfter=new ArrayList<>();for(int n=0;n<list().getCount();n++){List<String> labels=new ArrayList<>();texts(list().getAdapter().getView(n,null,list()),labels);if(!labels.isEmpty())homeAfter.add(labels.get(0));}
                check(homeBefore.equals(homeAfter),"search leaves the home batch unchanged");
            });
            for(int round=1;round<=2;round++){
                long started=SystemClock.elapsedRealtime();
                test.runOnMainSync(()->{View refresh=find(root(),"刷新");if(refresh==null)refresh=find(root(),"查看更新");if(refresh==null)throw new IllegalStateException("refresh not idle");refresh.performClick();});
                await(()->find(root(),"正在刷新")==null&&list().getCount()==20,"refresh did not settle",20000);
                report.append("REFRESH round=").append(round).append(" settledMs=").append(SystemClock.elapsedRealtime()-started).append('\n');
                test.runOnMainSync(()->check(list().getCount()==20&&list().getFirstVisiblePosition()==0,"refresh settles indicator on a twenty-row screen at the top"));
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{test.runOnMainSync(()->{if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append("; real phone search plus two refreshes, no exhaustive all-source claim\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
