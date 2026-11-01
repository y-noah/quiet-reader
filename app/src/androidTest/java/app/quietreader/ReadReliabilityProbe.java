package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Isolated emulator only: real preloader/cache/lifecycle, controlled acquisition outcomes. */
final class ReadReliabilityProbe {
    private final Instrumentation test;
    private DiscoveryActivity home;
    private ArticlePreloader preloader;
    private Repository cache;
    private final List<Fetch> calls=new ArrayList<>();
    private final List<String> prepared=new ArrayList<>(),failed=new ArrayList<>();
    private int rounds,failures,cancelled;
    private long identity=System.currentTimeMillis();
    private final StringBuilder report=new StringBuilder();
    private record Fetch(Item item,Repository.Result<Document> callback){}
    ReadReliabilityProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new IllegalStateException(error[0]);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private void await(BooleanSupplier condition,String message)throws Exception{long until=SystemClock.elapsedRealtime()+5000;do{if(onUi(condition))return;Thread.sleep(20);}while(SystemClock.elapsedRealtime()<until);require(onUi(condition),message);}
    private Item item(String name){return new Item(Source.THEPAPER,name,"https://www.thepaper.cn/newsDetail_forward_"+(++identity),"");}
    private void offer(Item... items){ui(()->preloader.offer(Arrays.asList(items),items.length,true));}
    private void reset(){ui(()->{if(preloader!=null)preloader.close();calls.clear();prepared.clear();failed.clear();cancelled=0;preloader=new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> cb){calls.add(new Fetch(item,cb));}public void cancel(){cancelled++;}},4,()->{});preloader.observePrepared((item,doc)->prepared.add(item.url));preloader.observeFailure(item->failed.add(item.url));});}
    private Document body(Item item){Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","这是隔离测试的完整正文，用于校验缓存归属与请求取消，不代表用户的真实阅读兴趣。".repeat(8)));require(new ArticleSession(home,item).stamp(doc),"fixture session stamp");return doc;}
    private void complete(int index){ui(()->{Fetch call=calls.get(index);call.callback.success(body(call.item));});}
    private void offlineFailure(){ui(()->{try{Method m=ArticlePreloader.class.getDeclaredMethod("failed",int.class,String.class,boolean.class);m.setAccessible(true);m.invoke(preloader,(int)field(preloader,"revision"),"当前离线且正文尚未缓存",false);}catch(Exception e){throw new IllegalStateException(e);}});}
    private void scenario(int kind)throws Exception{
        reset();Item first=item("受控文章甲"),second=item("受控文章乙");
        offer(first,second);await(()->calls.size()==1,"first request absent");
        switch(kind){
            case 0 -> {
                offlineFailure();await(()->calls.size()==2,"offline item blocked unrelated queued item");complete(1);await(()->!preloader.busy(),"queue did not settle");
                offer(first);await(()->calls.size()==3,"reconnected action remained blocked by five-minute failure cooldown");complete(2);
                require(onUi(()->cache.cachedArticle(first)!=null&&failed.isEmpty()),"offline recovery cached body or source failure count incorrect");
            }
            case 1 -> {
                ui(()->calls.get(0).callback.failure("controlled temporary server error",503,0));await(()->calls.size()==2,"temporary failure blocked next item");
                require(onUi(()->calls.get(1).item.url.equals(second.url)),"retry took priority over unaffected item");complete(1);
                await(()->calls.size()==3,"transient failure never retried");complete(2);await(()->!preloader.busy(),"successful retry did not settle");
                require(onUi(()->cache.cachedArticle(first)!=null&&prepared.size()==2&&failed.isEmpty()),"successful retry published terminal failure or lost body");
            }
            case 2 -> {
                ui(()->require(preloader.reserve(first),"reserve failed"));ui(()->calls.get(0).callback.failure("controlled terminal failure"));
                require(onUi(()->failed.isEmpty()),"reserved acquisition called former home failure observer");
            }
            case 3 -> {
                ui(preloader::pause);ui(()->calls.get(0).callback.success(body(first)));require(onUi(()->cache.cachedArticle(first)==null),"late cancelled result entered cache");
                offer(first);await(()->calls.size()==2,"cancelled article could not restart");ui(()->calls.get(0).callback.failure("stale failure"));complete(1);
                require(onUi(()->cache.cachedArticle(first)!=null&&prepared.size()==1&&failed.isEmpty()),"stale callback affected current generation");
            }
            case 4 -> {
                ui(()->calls.get(0).callback.failure("controlled rate limit",429,System.currentTimeMillis()+60_000));await(()->calls.size()==2,"rate limited article blocked unrelated article");complete(1);
                offer(first);Thread.sleep(180);require(onUi(()->calls.size()==2&&!preloader.busy()),"rate limited request retried before Retry-After");
            }
            case 5 -> {
                ui(()->calls.get(0).callback.failure("controlled temporary server error",503,0));await(()->calls.size()==2,"second item not started");complete(1);
                await(()->calls.size()==3,"bounded retry absent");ui(()->calls.get(2).callback.failure("controlled temporary server error",503,0));
                await(()->!preloader.busy(),"repeated failure left automatic work queued");offer(first);Thread.sleep(180);
                require(onUi(()->calls.size()==3&&failed.size()==1),"retry bound or terminal failure count incorrect");
            }
            case 6 -> {
                ui(()->calls.get(0).callback.failure("controlled temporary server error",503,0));await(()->calls.size()==2,"second item not started");complete(1);ui(preloader::pause);
                Thread.sleep(1750);require(onUi(()->calls.size()==2&&!preloader.busy()),"pause restarted scheduled retry");
            }
            case 7 -> {
                int[] delivered={0};ui(()->require(preloader.promote(first,new Repository.Result<Document>(){public void success(Document doc){delivered[0]++;}public void failure(String why){throw new AssertionError(why);}}),"promotion failed"));
                complete(0);ui(()->calls.get(0).callback.success(body(first)));require(onUi(()->delivered[0]==1&&cache.cachedArticle(first)!=null&&calls.size()==1),"promotion duplicated acquisition or terminal delivery");
            }
            case 8 -> {
                ui(preloader::finishWindow);ui(()->calls.get(0).callback.failure("controlled temporary server error",503,0));
                Thread.sleep(1750);require(onUi(()->calls.size()==1&&!preloader.busy()),"stopped action window started an automatic retry");
            }
            case 9 -> {
                ui(()->new ArticleDiskCache(home).invalidate());ui(()->calls.get(0).callback.failure("failure from obsolete cache scope"));await(()->calls.size()==2,"new scope queue blocked");complete(1);
                require(onUi(()->failed.isEmpty()),"old-scope failure notified current-scope observer");offer(first);await(()->calls.size()==3,"new scope could not retry");complete(2);
                require(onUi(()->cache.cachedArticle(first)!=null),"new scope recovery missing");
            }
            default -> throw new IllegalArgumentException();
        }
    }
    void run(boolean baseline,int count){run(baseline,count,0);}
    void run(boolean baseline,int count,int firstScenario){String database="test-reliability-"+UUID.randomUUID()+".db";ExecutorService storage=null;File ledger=new File(test.getTargetContext().getFilesDir(),baseline?"read-reliability-baseline.jsonl":"read-reliability-rounds.jsonl");
        try{
            require(test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe")&&Build.HARDWARE.equals("ranchu"),"bulk rounds forbidden on user phone");
            home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true).putExtra("test_database",database));storage=(ExecutorService)field(field(home,"controller"),"storage");ui(()->cache=new Repository(home));
            try(FileOutputStream output=new FileOutputStream(ledger)){
                for(int i=0;i<count;i++){int kind=baseline?i%3:(firstScenario+i)%10;long started=SystemClock.elapsedRealtime();String error="";
                    try{scenario(kind);}catch(Throwable e){error=e.toString();failures++;}finally{ui(()->{if(preloader!=null)preloader.close();});}
                    rounds++;JSONObject row=new JSONObject().put("round",i+1).put("scenario",kind).put("baseline",baseline).put("elapsedMs",SystemClock.elapsedRealtime()-started).put("requests",calls.size()).put("cancelCalls",cancelled).put("pass",error.isEmpty()).put("error",error);
                    output.write((row+"\n").getBytes(StandardCharsets.UTF_8));output.flush();if(!error.isEmpty())report.append("FAIL ").append(row).append('\n');
                    if(i%10==0){Bundle progress=new Bundle();progress.putString("stream","read reliability rounds="+rounds+" failures="+failures+"\n");test.sendStatus(0,progress);}
                }
            }
        }catch(Throwable e){failures++;report.append("INFRA ").append(e).append('\n');}
        finally{ui(()->{if(preloader!=null)preloader.close();if(cache!=null)cache.close();if(home!=null)home.finish();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("rounds=").append(rounds).append(" failures=").append(failures).append(" ledger=").append(ledger.getName()).append("\nOnly controlled emulator acquisition/lifecycle scenarios, not live platform success.\n");Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
