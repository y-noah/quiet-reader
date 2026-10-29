package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.TextView;
import java.lang.reflect.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Production reader/menu lifecycle; controlled topic/post transport and engagement clock. */
final class TiebaSelectionUiProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("Controlled source/menu flow, not real account or recommendation-benefit evidence\n");
    private static final String ROOT="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=990066600",FIRST="https://tieba.baidu.com/p/990066601",SECOND="https://tieba.baidu.com/p/990066602";
    private final List<String> requests=new CopyOnWriteArrayList<>();
    private volatile boolean missingFirst;
    private MainActivity reader;
    private String processPhase="",processId="";
    TiebaSelectionUiProbe(Instrumentation test){this.test=test;}
    TiebaSelectionUiProbe(Instrumentation test,String phase,String id){this.test=test;processPhase=phase;processId=id;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private void call(Object target,String name){try{Method m=target.getClass().getDeclaredMethod(name);m.setAccessible(true);m.invoke(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private View find(View view,String label){if(label.equals(view.getContentDescription())||view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void click(String label){ui(()->{Dialog dialog=(Dialog)field(reader,"readerActions");View root=dialog!=null&&dialog.isShowing()?dialog.getWindow().getDecorView():reader.getWindow().getDecorView();View target=find(root,label);if(target==null||!target.performClick())throw new IllegalStateException("Missing active action: "+label);});}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+9_000;while(SystemClock.elapsedRealtime()<end){boolean[] okay={false};ui(()->okay[0]=condition.getAsBoolean());if(okay[0])return;Thread.sleep(40);}throw new IllegalStateException("Reader state timeout");}
    private String post(boolean second){return "<h1>"+(second?"第二帖实际标题":"第一帖实际标题")+"</h1><div id=j_p_postlist><div class=l_post><a class=d_name>实际作者</a><div class=d_post_content id=post_content_"+(second?"21":"11")+">"+"<p>这是有足够长度用于阅读的真实结构合成段落。</p>".repeat(45)+"</div></div><div class=l_post><div class=d_post_content id=post_content_"+(second?"22":"12")+">"+(second?"仅第二帖回复":"仅第一帖回复")+"</div></div></div>";}
    private String topic(){return "<div class=topic-wrapper><ul class=thread-list>"+(missingFirst?"":"<li><a class=title href='"+FIRST+"'>第一帖实际标题</a></li>")+"<li><a class=title href='"+SECOND+"'>第二帖实际标题</a></li></ul></div>";}
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{String target=url.toString();if(!List.of(ROOT,FIRST,SECOND).contains(target))throw new IOException("Unexpected controlled URL");return new HttpURLConnection(url){public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}public int getResponseCode(){requests.add(target);return 200;}public InputStream getInputStream(){return new ByteArrayInputStream((target.equals(ROOT)?topic():post(target.equals(SECOND))).getBytes(StandardCharsets.UTF_8));}};}});}
    private void drainFeedback()throws Exception{Field f=ReadingInterestRecorder.class.getDeclaredField("writer");f.setAccessible(true);((ExecutorService)f.get(null)).submit(()->{}).get(4,TimeUnit.SECONDS);}
    private String js(String script)throws Exception{
        CountDownLatch done=new CountDownLatch(1);String[] result={""};ui(()->{WebView web=(WebView)field(reader,"readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript(script,value->{result[0]=value;done.countDown();});});
        if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("No selected reader DOM");return result[0];
    }
    private org.json.JSONObject position()throws Exception{return new org.json.JSONObject(js("(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.height>0&&r.bottom>0&&r.top<=1)return {id:a.id,fraction:-r.top/r.height,y:scrollY}}return {id:'',fraction:0,y:scrollY}})()"));}
    private android.content.SharedPreferences processPrefs(){return test.getTargetContext().getSharedPreferences("tieba-process-"+processId,0);}
    private void prepareProcess(boolean had,boolean old)throws Exception{
        long end=SystemClock.elapsedRealtime()+6000;
        while(SystemClock.elapsedRealtime()<end&&!js("document.readyState==='complete'&&document.querySelectorAll('[data-reading-anchor]').length>10").equals("true"))Thread.sleep(50);
        js("(()=>{const a=document.querySelectorAll('[data-reading-anchor]')[8],r=a.getBoundingClientRect();scrollTo(0,scrollY+r.top+r.height*.35)})()");Thread.sleep(650);
        org.json.JSONObject before=position();check(!before.optString("id").isEmpty()&&before.optInt("y")>0,"selected post reaches noninitial reading position before process stop");
        processPrefs().edit().putInt("pid",android.os.Process.myPid()).putInt("task",reader.getTaskId()).putString("position",before.toString()).putBoolean("hadWeb",had).putBoolean("oldWeb",old).commit();
        try(ParcelFileDescriptor fd=test.getUiAutomation().executeShellCommand("input keyevent 3");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){while(in.read()!=-1){}}
        await(()->(boolean)field(reader,"wasStopped"));
        org.json.JSONObject saved=new org.json.JSONObject(test.getTargetContext().getSharedPreferences("reading-history",0).getString(ROOT,"{}"));
        check(saved.optString("bodySource").equals(SECOND)&&saved.has("viewport"),"actual background saves selected body's identity and semantic viewport");
        check(((Item)field(reader,"current")).selectedTopicPost.equals(SECOND),"background task still owns explicit second-post request");
        report.append("PREPARED task=").append(reader.getTaskId()).append(" pid=").append(android.os.Process.myPid()).append(" nativeY=").append(((WebView)field(reader,"readerWeb")).getScrollY()).append(" position=").append(before).append('\n');
    }
    private void cleanupProcess(){
        android.content.SharedPreferences saved=processPrefs();
        android.content.SharedPreferences.Editor edit=test.getTargetContext().getSharedPreferences("source-session",0).edit();
        if(saved.getBoolean("hadWeb",false))edit.putBoolean("TIEBA-web",saved.getBoolean("oldWeb",false));else edit.remove("TIEBA-web");edit.commit();
        test.getTargetContext().deleteDatabase("test-ui-tieba-process-"+processId+".db");test.getTargetContext().getSharedPreferences("reading-history",0).edit().remove(ROOT).commit();saved.edit().clear().commit();
        check(true,"process-trial preferences and database cleaned");finishReport();
    }
    private void finishReport(){report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();
        if(processPhase.equals("prepare")&&failures==0){report.append("READY_FOR_PROCESS_KILL\n");result.putString("stream",report.toString());test.sendStatus(0,result);new Handler(Looper.getMainLooper()).postDelayed(()->{Bundle timeout=new Bundle();timeout.putString("stream","ERROR host did not terminate prepared process within 45 seconds\n");test.finish(Activity.RESULT_CANCELED,timeout);},45_000);}
        else{result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
    void run(){String database="test-ui-tieba-selection-"+UUID.randomUUID()+".db";android.content.SharedPreferences prefs=test.getTargetContext().getSharedPreferences("source-session",0);boolean had=prefs.contains("TIEBA-web"),old=prefs.getBoolean("TIEBA-web",false);
        if(processPhase.equals("cleanup")){cleanupProcess();return;}
        if(processPhase.equals("prepare"))database="test-ui-tieba-process-"+processId+".db";
        try{
            transport();app.quietreader.discovery.HotCandidate candidate=new app.quietreader.discovery.HotCandidate(Source.TIEBA,ROOT,"原热搜候选标题","原热搜摘要","",false,1,null,System.currentTimeMillis());Item root=candidate.readerItem();
            Document first=SourceParser.article(Source.TIEBA,post(false),FIRST);List<Item> choices=TiebaTopic.posts(org.jsoup.Jsoup.parse(topic()),ROOT);TiebaTopic.attach(ROOT,choices,first,ArticleDiskCache.session(FIRST),System.currentTimeMillis());ArticleVersion.capture(root,first);
            ui(()->{prefs.edit().putBoolean("TIEBA-web",false).apply();Repository repo=new Repository(test.getTargetContext());repo.cacheArticle(first);repo.close();});
            reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",root.json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database));
            await(()->field(reader,"reading")!=null&&field(reader,"readerWeb")!=null);ui(()->((WebView)field(reader,"readerWeb")).scrollTo(0,400));
            click("更多选项");click("同话题帖子");click("第一帖实际标题");
            await(()->field(reader,"reading")!=null&&((Item)field(reader,"current")).selectedTopicPost.equals(FIRST));
            check(requests.isEmpty(),"explicitly choosing currently displayed default post binds selection without source HTTP");
            click("更多选项");click("同话题帖子");click("第二帖实际标题");
            await(()->field(reader,"reading")!=null&&((Document)field(reader,"reading")).sourceUrl().equals(SECOND));
            ui(()->{Document doc=(Document)field(reader,"reading");Item current=(Item)field(reader,"current");check(current.url.equals(ROOT)&&current.selectedTopicPost.equals(SECOND),"menu selection retains root recommendation request");check(doc.title.equals("第二帖实际标题")&&ArticleVersion.matches(root,doc),"reader shows actual selected title with original metadata");check(doc.blocks.stream().noneMatch(b->b.value.contains("仅第一帖回复"))&&doc.blocks.stream().anyMatch(b->b.value.contains("仅第二帖回复")),"old replies are replaced rather than appended across posts");check(((ArrayDeque<?>)field(reader,"history")).isEmpty(),"switching posts does not add duplicate root to back stack");check(((WebView)field(reader,"readerWeb")).getScrollY()==0,"new post starts without old reading position");});
            check(requests.equals(List.of(ROOT,SECOND)),"selection revalidates official topic then fetches only selected post");drainFeedback();
            try(app.quietreader.discovery.FeedStore store=new app.quietreader.discovery.FeedStore(test.getTargetContext(),database)){check(store.activeFeedback(System.currentTimeMillis()).length()==0,"opening and selecting do not write interest feedback");}
            if(processPhase.equals("prepare")){prepareProcess(had,old);}else{
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);ui(reader::recreate);Activity replacement=monitor.waitForActivityWithTimeout(7000);test.removeMonitor(monitor);if(!(replacement instanceof MainActivity))throw new IllegalStateException("No recreated reader");reader=(MainActivity)replacement;
            await(()->field(reader,"reading")!=null);ui(()->{Document doc=(Document)field(reader,"reading");check(doc.sourceUrl().equals(SECOND)&&((Item)field(reader,"current")).selectedTopicPost.equals(SECOND),"rotation keeps selected actual post and root request");Bundle saved=new Bundle();reader.onSaveInstanceState(saved);try{check(Item.from(new org.json.JSONObject(saved.getString("current"))).selectedTopicPost.equals(SECOND),"process-restoration payload retains explicit post choice");}catch(Exception e){throw new IllegalStateException(e);}});
            check(requests.size()==2,"rotation does not refetch or switch back to first post");
            // The gate itself uses a controlled monotonic clock; no real-reading claim.
            ui(()->{ReadingEngagement engagement=(ReadingEngagement)field(reader,"engagement");long now=SystemClock.elapsedRealtime();engagement.reset(now-21_000);engagement.visible(true,now-21_000);engagement.ready(true,now-21_000);engagement.touch(now-21_000);engagement.scroll(now-20_900);call(reader,"recordReadingInterest");});drainFeedback();
            try(app.quietreader.discovery.FeedStore store=new app.quietreader.discovery.FeedStore(test.getTargetContext(),database)){org.json.JSONArray events=store.activeFeedback(System.currentTimeMillis());check(events.length()==1&&events.toString().contains(candidate.id),"qualified selected-post reading records original candidate identity");check(!events.toString().contains("第二帖实际标题"),"post title is not substituted into recommendation feedback");}
            ui(()->{ReadingEngagement engagement=(ReadingEngagement)field(reader,"engagement");long now=SystemClock.elapsedRealtime();engagement.reset(now-2_000);engagement.visible(true,now-2_000);engagement.ready(true,now-2_000);call(reader,"recordQuickExit");});drainFeedback();
            try(app.quietreader.discovery.FeedStore store=new app.quietreader.discovery.FeedStore(test.getTargetContext(),database)){org.json.JSONArray events=store.activeFeedback(System.currentTimeMillis());check(events.length()==2&&events.toString().contains(candidate.id),"selected-post quick exit retains original candidate identity");}
            missingFirst=true;click("更多选项");click("同话题帖子");click("第一帖实际标题");await(()->field(reader,"dynamic")!=null);
            // Deliver a controlled dynamic-transport failure through the real owner callback.
            ui(()->{DynamicReader loading=(DynamicReader)field(reader,"dynamic");@SuppressWarnings("unchecked") Repository.Result<Document> callback=(Repository.Result<Document>)field(loading,"callback");callback.failure("受控来源加载失败");check(find(reader.getWindow().getDecorView(),"返回原帖")!=null,"failed selected read exposes return to previous readable post");});
            click("返回原帖");await(()->field(reader,"reading")!=null&&((Document)field(reader,"reading")).sourceUrl().equals(SECOND));
            check(Collections.frequency(requests,FIRST)==0,"removed official target never silently fetches another post");ui(()->check(((Document)field(reader,"reading")).title.equals("第二帖实际标题"),"failure return restores previous actual body"));
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{if(!processPhase.equals("prepare")||failures>0){ui(()->{if(reader!=null)reader.finish();android.content.SharedPreferences.Editor edit=prefs.edit();if(had)edit.putBoolean("TIEBA-web",old);else edit.remove("TIEBA-web");edit.apply();});test.getTargetContext().deleteDatabase(database);}}
        finishReport();
    }
}
