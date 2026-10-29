package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Actual controller, SQLite, and card UI; controlled source timing, no live data. */
final class DiscoverySupplyProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private final List<ExecutorService> workers=new ArrayList<>();
    private final List<Fetch> calls=new ArrayList<>();
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private static final class Fetch {
        final Source source;final Repository.Result<List<Item>> result;boolean closed;
        Fetch(Source source,Repository.Result<List<Item>> result){this.source=source;this.result=result;}
    }
    DiscoverySupplyProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Supply deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean text(View view,String value){if(view instanceof TextView&&value.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)if(text(((ViewGroup)view).getChildAt(n),value))return true;return false;}
    private int count(){return ((List<?>)field(activity,"feed")).size();}
    private boolean loading(){return (boolean)field(controller,"loading");}
    private long pending(){return (long)field(controller,"pending");}
    private Fetch fetch(Source source){return calls.stream().filter(f->f.source==source&&!f.closed).findFirst().get();}
    private void success(Source source,String title){fetch(source).result.success(Collections.singletonList(new Item(source,title,source.boardPage(),"123")));}
    private void settled()throws Exception{((ExecutorService)field(controller,"storage")).submit(()->{}).get(5,TimeUnit.SECONDS);test.waitForIdleSync();Thread.sleep(900);test.waitForIdleSync();}
    void run(){String database="test-ui-supply-"+UUID.randomUUID()+".db";
        try{
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));
            DiscoveryController original=(DiscoveryController)field(activity,"controller");ExecutorService old=(ExecutorService)field(original,"storage");workers.add(old);ui(original::close);old.awaitTermination(8,TimeUnit.SECONDS);
            ui(()->{
                controller=new DiscoveryController(activity,activity,false,new HotSourceCollector.Transport(){
                    public HotSourceCollector.Request start(Source source,boolean browser,Repository.Result<List<Item>> result){Fetch call=new Fetch(source,result);calls.add(call);return ()->call.closed=true;}
                    public boolean offline(){return false;}
                });
                try{Field f=DiscoveryActivity.class.getDeclaredField("controller");f.setAccessible(true);f.set(activity,controller);}catch(Exception e){throw new IllegalStateException(e);}
                workers.add((ExecutorService)field(controller,"storage"));controller.start();
            });
            await("ready",()->(boolean)field(controller,"ready"));
            ui(()->controller.refresh(Arrays.asList(Source.ZHIHU,Source.WEIBO,Source.HUPU)));
            await("three requests",()->calls.size()==3);test.waitForIdleSync();
            check(onUi(()->text(activity.getWindow().getDecorView(),"正在获取热门话题…")),"empty discovery describes acquisition while sources are loading");
            ui(()->success(Source.ZHIHU,"城市树荫如何影响夏季体感温度"));settled();
            check(onUi(()->count()==1&&loading()&&calls.stream().filter(f->!f.closed).count()==2),"first text batch is readable before two slow sources finish");
            Object frozen=field(activity,"feed");
            ui(()->success(Source.WEIBO,"今年公共图书馆延长开放时间"));settled();
            check(onUi(()->pending()>0&&loading()),"later batch offers an update while a third source remains pending");
            check(onUi(()->field(activity,"feed")==frozen&&count()==1),"later supply never replaces the visible frozen cards");
            ui(()->controller.applyPending(false));await("explicit update",()->count()==2);
            check(onUi(this::loading),"viewing partial results does not claim refresh is finished");
            ui(()->fetch(Source.HUPU).result.failure("受控来源暂不可用"));await("finished",()->!loading());settled();
            check(onUi(()->count()==2),"one failed source preserves the two readable text topics");
            check(onUi(()->pending()==0),"last-source failure creates no redundant update for already accepted supply");
            ui(()->controller.applyPending(false));settled();
            ui(()->controller.refresh(Arrays.asList(Source.ZHIHU,Source.WEIBO)));
            await("second round",()->calls.size()==5);
            Fetch late=fetch(Source.WEIBO);
            Fetch committed=fetch(Source.ZHIHU);
            ui(()->success(Source.ZHIHU,"城市树荫更新后的文字话题"));
            await("committed before delayed publication",()->committed.closed);
            ui(controller::stop);settled();
            check(onUi(()->!loading()&&pending()==0&&count()==2),"leaving discovery cancels delayed partial publication");
            ui(()->late.result.success(Collections.singletonList(new Item(Source.WEIBO,"离开后不能写入的迟到话题",Source.WEIBO.boardPage(),"1"))));settled();
            check(onUi(()->pending()==0&&count()==2&&late.closed),"late transport callback cannot publish after stop");
            ui(controller::start);await("resume recovers committed supply",()->pending()>0);
            check(onUi(()->count()==2),"return preserves visible cards until committed supply is requested");
            ui(()->controller.applyPending(false));await("resume applies update",()->hasTitle("城市树荫更新后的文字话题"));
            check(onUi(()->!hasTitle("离开后不能写入的迟到话题")),"resume recovers only committed supply and rejects cancelled source data");
            ui(()->controller.refresh(Collections.singletonList(Source.ZHIHU)));await("replacement first request",()->calls.size()==6);
            Fetch obsolete=fetch(Source.ZHIHU);
            ui(()->success(Source.ZHIHU,"应被新一轮取代的文字话题"));await("replacement first commit",()->obsolete.closed);
            ui(()->controller.refresh(Collections.singletonList(Source.ZHIHU)));await("replacement second request",()->calls.size()==7);
            settled();
            check(onUi(()->pending()==0&&hasTitle("城市树荫更新后的文字话题")),"replacement refresh cancels previous delayed publication without changing visible cards");
            ui(()->{obsolete.result.success(Collections.singletonList(new Item(Source.ZHIHU,"旧请求迟到不能覆盖",Source.ZHIHU.boardPage(),"1")));success(Source.ZHIHU,"新一轮实际取得的文字话题");});
            await("replacement completes",()->!loading()&&pending()>0);
            ui(()->controller.applyPending(false));await("replacement apply",()->hasTitle("新一轮实际取得的文字话题"));
            check(onUi(()->count()==2&&!hasTitle("旧请求迟到不能覆盖")),"new refresh publishes its actual data without duplicate or late replacement");
            ui(()->controller.refresh(Collections.singletonList(Source.WEIBO)));await("failure-only round",()->calls.size()==8);
            ui(()->fetch(Source.WEIBO).result.failure("本轮没有新内容"));await("failure-only complete",()->!loading());settled();
            check(onUi(()->pending()==0&&count()==2),"failure-only refresh keeps readable cards without claiming a content update");
            ui(()->controller.refresh(Collections.singletonList(Source.ZHIHU)));await("saved before replacement",()->calls.size()==9);
            Fetch saved=fetch(Source.ZHIHU);ui(()->success(Source.ZHIHU,"上一轮已保存仍应可读的文字话题"));await("saved committed",()->saved.closed);
            // The previous Weibo failure is correctly under retry backoff. Use
            // the just-successful source rather than bypassing that policy.
            ui(()->controller.refresh(Collections.singletonList(Source.ZHIHU)));await("failed replacement",()->calls.size()==10);
            ui(()->fetch(Source.ZHIHU).result.failure("替代请求失败"));await("saved update offered",()->!loading()&&pending()>0);
            check(onUi(()->hasTitle("新一轮实际取得的文字话题")),"failed replacement preserves frozen cards while offering earlier committed data");
            ui(()->controller.applyPending(false));await("saved update applied",()->hasTitle("上一轮已保存仍应可读的文字话题"));
            check(onUi(()->count()==2),"a replacement failure cannot strand an earlier successfully committed batch");
        }catch(Exception e){check(false,e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            ui(()->{if(activity!=null)activity.finish();});
            for(ExecutorService worker:workers)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
            Bundle result=new Bundle();result.putString("report",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
    @SuppressWarnings("unchecked") private boolean hasTitle(String title){return ((List<app.quietreader.discovery.FeedStore.Row>)field(activity,"feed")).stream().anyMatch(row->row.candidate.title.equals(title));}
}
