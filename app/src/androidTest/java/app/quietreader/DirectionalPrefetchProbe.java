package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.MotionEvent;
import android.widget.ListView;
import app.quietreader.discovery.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real list touch/fling and layout callbacks; controlled serial bodies, no live source claim. */
final class DirectionalPrefetchProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private ArticlePreloader preloader;
    private ListView list;
    private ExecutorService storage;
    private int checks,failures,cancels;
    private final List<Fetch> calls=new ArrayList<>();
    private final StringBuilder report=new StringBuilder();
    private static final class Fetch {final Item item;final Repository.Result<Document> callback;Fetch(Item item,Repository.Result<Document> callback){this.item=item;this.callback=callback;}}
    DirectionalPrefetchProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void set(Object owner,String name,Object value){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Deadline: "+stage+" calls="+calls.size()+" first="+list.getFirstVisiblePosition()+" viewport="+field(controller,"viewport")+" interacting="+field(controller,"interacting"));Thread.sleep(30);}}
    private void swipeDown()throws Exception{
        float[] point=new float[4];ui(()->{int[] at=new int[2];list.getLocationOnScreen(at);point[0]=at[0]+list.getWidth()*.5f;point[1]=at[1]+list.getHeight()*.25f;point[2]=at[1]+list.getHeight()*.8f;});
        long began=SystemClock.uptimeMillis();send(began,MotionEvent.ACTION_DOWN,point[0],point[1]);
        for(int n=1;n<=12;n++){Thread.sleep(25);send(began,MotionEvent.ACTION_MOVE,point[0],point[1]+(point[2]-point[1])*n/12);}
        send(began,MotionEvent.ACTION_UP,point[0],point[2]);
    }
    private void send(long began,int action,float x,float y){MotionEvent event=MotionEvent.obtain(began,SystemClock.uptimeMillis(),action,x,y,0);try{test.sendPointerSync(event);}finally{event.recycle();}}
    private void finish(Fetch fetch){Document doc=new Document();doc.url=fetch.item.url;doc.title=fetch.item.title;doc.blocks.add(new Block("text","这是受控的方向预缓存正文，只验证调度、身份和复用，不代表平台真实内容。".repeat(10)));new ArticleSession(activity,fetch.item).stamp(doc);fetch.callback.success(doc);}
    void run(){String database="test-ui-direction-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();for(int n=0;n<100;n++)candidates.add(new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),"中文文字方向预读样本第"+n+"项","","",false,n+1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(Source source:Source.values())store.enable(source,source==Source.THEPAPER);store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),candidates,now);}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database));
            controller=(DiscoveryController)field(activity,"controller");list=(ListView)field(activity,"list");storage=(ExecutorService)field(controller,"storage");
            ui(()->{((ArticlePreloader)field(controller,"preloader")).close();preloader=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> callback){calls.add(new Fetch(item,callback));}public void cancel(){cancels++;}},40,()->{},true);set(controller,"preloader",preloader);});
            await("initial prefetch",()->!calls.isEmpty()&&activity.hasWindowFocus());
            check(onUi(()->((List<?>)field(controller,"rows")).size()==100),"source supplies one hundred distinct frozen candidates before prediction");
            int beforeLayout=calls.size();long[] initialDeadline={0};ui(()->{initialDeadline[0]=(long)field(field(controller,"prefetchBudget"),"deadline");list.setSelectionFromTop(25,-list.getPaddingTop());});
            await("passive selection position",()->list.getFirstVisiblePosition()==25);await("passive viewport request",()->calls.size()>beforeLayout&&calls.get(calls.size()-1).item.url.equals(candidates.get(list.getFirstVisiblePosition()).originalUrl));
            check(onUi(()->((PrefetchWindow)field(controller,"cacheWindow")).direction(SystemClock.elapsedRealtime())==1&&(long)field(field(controller,"prefetchBudget"),"deadline")==initialDeadline[0]),"programmatic layout moves the window without inventing a direction change or renewing action credit");
            int beforeGesture=calls.size(),beforeCancels=cancels;swipeDown();
            await("actual upward list scroll settles",()->!(boolean)field(controller,"interacting")&&list.getFirstVisiblePosition()<25&&list.getFirstVisiblePosition()>0);
            await("directional request",()->calls.size()>beforeGesture&&calls.get(calls.size()-1).item.url.equals(candidates.get(list.getFirstVisiblePosition()).originalUrl));
            int[] anchor={0},visible={0};ui(()->{anchor[0]=list.getFirstVisiblePosition();visible[0]=(int)field(controller,"visibleCount");report.append("GESTURE first=").append(anchor[0]).append(" visible=").append(visible[0]).append(" rapid=").append(((PrefetchWindow)field(controller,"cacheWindow")).rapid(SystemClock.elapsedRealtime())).append('\n');});
            check(onUi(()->((PrefetchWindow)field(controller,"cacheWindow")).direction(SystemClock.elapsedRealtime())==-1&&cancels>beforeCancels),"real finger gesture reverses prediction upward and cancels speculative work during touch");
            check(onUi(()->((Deque<?>)field(preloader,"queue")).size()+1<=40),"gesture prediction retains a maximum forty-body window and one active acquisition");
            for(int n=0;n<visible[0];n++){
                Fetch fetch=calls.get(calls.size()-1);int count=calls.size();check(fetch.item.url.equals(candidates.get(anchor[0]+n).originalUrl),"visible card has priority before offscreen neighbors "+n);ui(()->finish(fetch));await("next serial prepared body",()->calls.size()>count);
            }
            check(calls.get(calls.size()-1).item.url.equals(candidates.get(anchor[0]-1).originalUrl),"after visible cards the next real acquisition is the preceding event, not a later one");
            ReaderPrefetchPlan[] plan={null};ui(()->plan[0]=ReaderPrefetchPlan.decode(controller.readerPrefetchPlan(candidates.get(anchor[0]))));
            check(plan[0]!=null&&plan[0].candidates.size()==40&&plan[0].candidates.get(0).originalUrl.equals(candidates.get(anchor[0]-1).originalUrl),"opening an article carries the same upward frozen-neighbor priority into its reader plan");
            long[] deadline={0};ui(()->{deadline[0]=(long)field(field(controller,"prefetchBudget"),"deadline");list.setSelectionFromTop(30,-list.getPaddingTop());});await("second passive layout",()->list.getFirstVisiblePosition()==30);
            check(onUi(()->((PrefetchWindow)field(controller,"cacheWindow")).direction(SystemClock.elapsedRealtime())==-1&&(long)field(field(controller,"prefetchBudget"),"deadline")==deadline[0]),"later passive layout cannot undo the user's direction or extend the lease");
            check(onUi(()->((List<FeedStore.Row>)field(controller,"rows")).get(55).candidate.id.equals(candidates.get(55).id)),"prediction changes preparation order without changing the visible recommendation order");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(storage!=null)try{storage.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
