package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Synthetic cache/fallback cases and opt-in real official Weibo refresh. No cookie logging. */
public final class WeiboBoardInstrumentation extends Instrumentation {
    private MainActivity activity;
    private boolean live;
    private int checks,failures;
    private long oldTime;
    private final StringBuilder report=new StringBuilder();
    private final Map<String,Map<String,?>> backups=new LinkedHashMap<>();
    interface Task{void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);live="true".equals(args.getString("live"));start();}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Task action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private void invoke(String name,Class<?>[] types,Object...args)throws Exception{Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(activity,args);}
    private SharedPreferences prefs(String name){return getTargetContext().getSharedPreferences(name,0);}
    private void quiet()throws Exception{((Repository)field(activity,"repo")).cancelPending();((ArticlePreloader)field(activity,"preloader")).pause();((Handler)field(activity,"preloadHandler")).removeCallbacksAndMessages(null);}
    @Override public void callActivityOnCreate(Activity a,Bundle state){if(a instanceof MainActivity){state=new Bundle();state.putString("source","WEIBO");}super.callActivityOnCreate(a,state);}
    private int buttons(View v){int n=v instanceof TextView&&"打开来源页后重试".contentEquals(((TextView)v).getText())?1:0;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)n+=buttons(((ViewGroup)v).getChildAt(i));return n;}
    private void seed()throws Exception{
        for(String name:new String[]{"boards","video-filter","MainActivity","source-session"})backups.put(name,new HashMap<>(prefs(name).getAll()));
        oldTime=System.currentTimeMillis()-5*60*1000;
        List<Item> old=Collections.singletonList(new Item(Source.WEIBO,"QR_SYNTHETIC_OLD_BOARD","https://s.weibo.com/weibo?q=QR_SYNTHETIC_OLD_BOARD","旧缓存"));
        prefs("boards").edit().putString("WEIBO",Models.toJson(old).toString()).putString("WEIBO_endpoint",WeiboBoard.LEGACY).putLong("WEIBO_time",oldTime).commit();
    }
    private void cacheAndFailure()throws Exception{
        ui(()->{
            quiet();Repository r=(Repository)field(activity,"repo");
            check(r.cachedAt(Source.WEIBO)==oldTime,"legacy endpoint keeps original cache timestamp");
            check(r.cached(Source.WEIBO).size()==1,"legacy cache remains available after upgrade");
            check(field(activity,"selected")==Source.WEIBO,"Weibo channel restored");
            String initial=(String)field(activity,"boardLabel");
            report.append("INITIAL ").append(initial).append('\n');
            check(initial.contains("缓存")&&!initial.startsWith("刷新中"),"fresh snapshot identified as cache, including offline startup");
            invoke("boardFailure",new Class<?>[]{List.class,String.class},r.cached(Source.WEIBO),"合成访问限制");
            check(((String)field(activity,"boardLabel")).startsWith("刷新失败"),"failed refresh is explicit");
            check(r.cachedAt(Source.WEIBO)==oldTime,"failure never advances saved cache time");
            check(((List<Item>)field(activity,"boardItems")).get(0).title.equals("QR_SYNTHETIC_OLD_BOARD"),"failure retains old readable content");
            check(buttons(activity.getWindow().getDecorView())==1,"exactly one source retry action");
            invoke("drawBoard",new Class<?>[0]);
            check(buttons(activity.getWindow().getDecorView())==1,"retry survives later board rendering");
            prefs("boards").edit().putString("WEIBO_endpoint","https://evil.test/").commit();
            check(r.cachedAt(Source.WEIBO)==0&&r.cached(Source.WEIBO).isEmpty(),"unrelated endpoint cache rejected");
            prefs("boards").edit().putString("WEIBO_endpoint",WeiboBoard.LEGACY).commit();
        });
    }
    private String html(String title){return "<html><body><div id='pl_top_realtimehot'><table><tr><td class='td-02'><a href='/weibo?q="+title+"'>"+title+"</a></td></tr></table></div></body></html>";}
    private void webFallback(String title)throws Exception{
        DynamicBoard[] board={null};CountDownLatch done=new CountDownLatch(1);List<Item>[] rows=new List[]{null};
        ui(()->{
            board[0]=new DynamicBoard(activity,Source.WEIBO,new Repository.Result<List<Item>>(){public void success(List<Item> value){rows[0]=value;done.countDown();}public void failure(String why){done.countDown();}});
            WebView web=(WebView)field(board[0],"web");WebViewClient delegate=web.getWebViewClient();
            check(web.getSettings().getCacheMode()==WebSettings.LOAD_NO_CACHE,"fallback bypasses HTTP cache");
            check(web.getSettings().getDomStorageEnabled()&&CookieManager.getInstance().acceptThirdPartyCookies(web),"fallback retains session configuration");
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html(title).getBytes(StandardCharsets.UTF_8)));}
                @Override public void onPageFinished(WebView w,String url){delegate.onPageFinished(w,url);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
            });
        });
        try{check(done.await(10,TimeUnit.SECONDS),"fallback completes");check(rows[0]!=null&&rows[0].size()==1&&title.equals(rows[0].get(0).title),"fallback reads current document "+title);}
        finally{ui(()->board[0].close());}
    }
    private void cancelledFallback()throws Exception{
        int[] callbacks={0};
        ui(()->{
            DynamicBoard board=new DynamicBoard(activity,Source.WEIBO,new Repository.Result<List<Item>>(){public void success(List<Item> value){callbacks[0]++;}public void failure(String why){callbacks[0]++;}});
            board.close();Method fail=DynamicBoard.class.getDeclaredMethod("fail");fail.setAccessible(true);fail.invoke(board);
            check(callbacks[0]==0,"closed fallback cannot publish or fail into another screen");
        });
    }
    private void realRefresh()throws Exception{
        for(int round=1;round<=2;round++){
            final int n=round;long[] before={0};
            ui(()->{
                before[0]=((Repository)field(activity,"repo")).cachedAt(Source.WEIBO);
                invoke("home",new Class<?>[]{boolean.class},true);
                check(((String)field(activity,"boardLabel")).startsWith("刷新中"),"manual refresh immediately shows progress "+n);
                check(buttons(activity.getWindow().getDecorView())==0,"new attempt clears old failure action "+n);
            });
            long deadline=SystemClock.elapsedRealtime()+55000;String[] label={""};
            do{Thread.sleep(200);ui(()->label[0]=(String)field(activity,"boardLabel"));}while(label[0].startsWith("刷新中")&&SystemClock.elapsedRealtime()<deadline);
            ui(()->{
                quiet();Repository r=(Repository)field(activity,"repo");List<Item> items=(List<Item>)field(activity,"boardItems");
                check(label[0].startsWith("更新于"),"official live request completed "+n);
                check(r.cachedAt(Source.WEIBO)>before[0],"live success advances saved timestamp "+n);
                check(items.size()>=30&&items.stream().noneMatch(i->i.title.equals("QR_SYNTHETIC_OLD_BOARD")),"old fixture replaced by real board "+n);
                check(WeiboBoard.PRIMARY.equals(prefs("boards").getString("WEIBO_endpoint","")),"successful refresh migrates cache identity "+n);
                report.append("LIVE round=").append(n).append(" rows=").append(items.size()).append(" state=").append(label[0]).append('\n');
            });
        }
    }
    private void restore(){for(Map.Entry<String,Map<String,?>> entry:backups.entrySet()){SharedPreferences.Editor e=prefs(entry.getKey()).edit().clear();for(Map.Entry<String,?> pair:entry.getValue().entrySet()){Object v=pair.getValue();String k=pair.getKey();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Set)e.putStringSet(k,(Set<String>)v);}e.commit();}}
    @Override public void onStart(){Bundle result=new Bundle();try{
        seed();activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        Thread.sleep(300);cacheAndFailure();webFallback("QR_WEB_FIRST");webFallback("QR_WEB_SECOND");cancelledFallback();if(live)realRefresh();
    }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
    finally{try{if(activity!=null)ui(()->{quiet();activity.finish();});restore();}catch(Exception e){failures++;report.append("CLEANUP ERROR ").append(e).append('\n');}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    }
}
