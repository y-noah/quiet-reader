package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual production collection and cards in an isolated database, no labels or fixture feed. */
final class LiveDiscoverySupplyProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private DiscoveryController controller;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    LiveDiscoverySupplyProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] value={false};ui(()->value[0]=condition.getAsBoolean());return value[0];}
    private void await(String stage,long timeout,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Live discovery deadline: "+stage);Thread.sleep(30);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(activity,"feed");}
    private ListView list(){return (ListView)field(activity,"list");}
    private View find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=find(((ViewGroup)view).getChildAt(n),text);if(found!=null)return found;}return null;}
    private void more()throws Exception{
        int[] before={0};ui(()->{before[0]=list().getAdapter().getCount();list().setSelectionFromTop(before[0]-3,0);});
        await("automatic near-bottom expansion",5000,()->list().getAdapter().getCount()>before[0]);
        check(onUi(()->find(list(),"查看更多话题")==null),"near-bottom layout expands without a manual more button");
    }
    private String anchor(){return rows().get(list().getFirstVisiblePosition()).eventId;}
    private void retained(String expected,int offset,String stage)throws Exception{
        await(stage,8000,()->!rows().isEmpty()&&list().getChildCount()>0&&expected.equals(anchor()));
        check(onUi(()->expected.equals(anchor())&&Math.abs(list().getChildAt(0).getTop()-offset)<=1),stage+" retains actual event identity and pixel offset");
    }
    void run(){String database="test-ui-live-supply-"+UUID.randomUUID()+".db";ExecutorService worker=null;
        try{
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_database",database));
            controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");
            await("automatic collection begins",10000,()->(boolean)field(controller,"autoStarted"));
            await("automatic collection finishes",100000,()->!(boolean)field(controller,"loading"));
            ui(()->controller.interacting(true));
            FeedStore store=(FeedStore)field(controller,"store");long committed=worker.submit(()->store.version("sources")).get(5,TimeUnit.SECONDS);
            long[] requested={-1};
            await("complete source version displayed",8000,()->{
                long pending=(long)field(controller,"pending");
                if(pending>0&&pending!=requested[0]){requested[0]=pending;controller.applyPending(false);}
                return (long)field(controller,"visibleSourceVersion")>=committed;
            });
            check(onUi(()->(long)field(controller,"visibleSourceVersion")>=committed),"applied snapshot includes the completed collection's source version");
            await("one hundred actual cards",8000,()->rows().size()>=100);
            List<FeedStore.Row> actual=new ArrayList<>(rows());
            report.append("actualCards=").append(actual.size()).append('\n');
            check(actual.size()>=100,"actual default-source collection supplies at least 100 grouped text cards");
            check(actual.stream().allMatch(row->!row.candidate.video&&HotSourceCatalog.TEXT_HOT.contains(row.candidate.source)&&ContentLanguage.eligible(row.candidate.title,row.candidate.snippet,row.candidate.bodyLanguage)),"every displayed primary entry meets default text/language eligibility");
            Set<String> identities=new HashSet<>();for(FeedStore.Row row:actual)identities.add(row.eventId);
            check(identities.size()==actual.size(),"production grouping produces no repeated card identity");
            check(onUi(()->list().getAdapter().getCount()<actual.size()),"real candidate pool uses incremental native card rendering");
            more();more();
            ui(()->{controller.interacting(true);list().setSelectionFromTop(99,0);});
            // Near the actual end ListView clamps the viewport to the last row;
            // the requested card must be visible, not necessarily top-aligned.
            await("hundredth card",5000,()->list().getFirstVisiblePosition()<=99&&list().getFirstVisiblePosition()+list().getChildCount()>99);
            check(onUi(()->{ViewGroup wrapper=(ViewGroup)list().getChildAt(99-list().getFirstVisiblePosition());TextView title=(TextView)field(wrapper.getChildAt(0),"title");return actual.get(99).candidate.title.contentEquals(title.getText());}),"the hundredth rendered card uses the exact collected source title");
            check(onUi(()->list().getChildCount()<20),"actual long feed still recycles visible native cards");
            ui(()->controller.interacting(true));
            Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot==null)throw new IllegalStateException("Screenshot unavailable");
            try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"live-discovery-text-page100.png"))){screenshot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
            String[] savedAnchor={null};int[] savedOffset={0};
            ui(()->{savedAnchor[0]=anchor();savedOffset[0]=list().getChildAt(0).getTop();((View)field(activity,"settingsButton")).performClick();});
            await("settings opens",5000,()->"settings".equals(field(activity,"page")));
            ui(()->((View)field(activity,"settingsButton")).performClick());
            retained(savedAnchor[0],savedOffset[0],"return from settings");
            Instrumentation.ActivityMonitor monitor=test.addMonitor(DiscoveryActivity.class.getName(),null,false);
            try{
                ui(activity::recreate);
                Activity next=test.waitForMonitorWithTimeout(monitor,8000);
                if(!(next instanceof DiscoveryActivity))throw new IllegalStateException("Recreated discovery absent");
                activity=(DiscoveryActivity)next;
                controller=(DiscoveryController)field(activity,"controller");
                worker=(ExecutorService)field(controller,"storage");
                retained(savedAnchor[0],savedOffset[0],"configuration recreation");
                ui(()->controller.interacting(true));
                check(onUi(()->list().getAdapter().getCount()>=100&&list().getChildCount()<20),"recreated long feed keeps expanded range and recycled views");
            }finally{test.removeMonitor(monitor);}
            Map<Models.Source,Integer> distribution=new EnumMap<>(Models.Source.class);for(FeedStore.Row row:actual)distribution.put(row.candidate.source,distribution.getOrDefault(row.candidate.source,0)+1);
            report.append("actualCards=").append(actual.size()).append(" primarySources=").append(distribution).append("; no personal relevance labels\n");
        }catch(Throwable e){check(false,e.getClass().getSimpleName()+": "+e.getMessage());}
        finally{
            ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();
            if(worker!=null)try{worker.awaitTermination(8,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            test.getTargetContext().deleteDatabase(database);
            Bundle result=new Bundle();result.putString("report",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
        }
    }
}
