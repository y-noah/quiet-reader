package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Captured public hotNews through real parser, SQLite and category controls. */
final class ThepaperCategoryProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private DiscoveryController controller;
    private ExecutorService worker;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ThepaperCategoryProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{java.lang.reflect.Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Category deadline: "+stage);Thread.sleep(25);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(activity,"feed");}
    private View find(View root,String label){if(root instanceof TextView&&root.isClickable()&&label.contentEquals(((TextView)root).getText()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View found=find(((ViewGroup)root).getChildAt(i),label);if(found!=null)return found;}return null;}
    private void click(ContentCategory category)throws Exception{
        View[] target={null};ui(()->{target[0]=find(activity.getWindow().getDecorView(),category.label);if(target[0]!=null)target[0].requestRectangleOnScreen(new android.graphics.Rect(0,0,target[0].getWidth(),target[0].getHeight()),true);});test.waitForIdleSync();
        boolean[] clicked={false};ui(()->{View view=target[0];android.graphics.Rect bounds=new android.graphics.Rect();clicked[0]=view!=null&&view.getGlobalVisibleRect(bounds)&&bounds.width()>=view.getWidth()-1&&view.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);});
        if(!clicked[0])throw new IllegalStateException("Visible category action unavailable");
        await(category.name(),()->controller.categoryFilter()==category&&rows().size()==expected(category));
        check(onUi(()->target[0].isSelected()),"actual category control selected: "+category.name());
        check(onUi(()->rows().stream().allMatch(row->category==ContentCategory.ALL||ContentCategory.of(row.candidate)==category)),"displayed cards match source-owned category: "+category.name());
    }
    private int expected(ContentCategory category){switch(category){case ALL:return 17;case SOCIETY:return 12;case TECH:case CULTURE:return 1;case OTHER:return 3;default:return 0;}}
    private void open(String database)throws Exception{
        activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        controller=(DiscoveryController)field(activity,"controller");worker=(ExecutorService)field(controller,"storage");await("ready",()->(boolean)field(controller,"ready"));
    }
    private void finishActivity()throws Exception{ui(activity::finish);test.waitForIdleSync();worker.awaitTermination(5,TimeUnit.SECONDS);}
    void run(){String database="test-ui-paper-category-"+UUID.randomUUID()+".db";
        try{
            String raw;try(java.io.InputStream in=test.getContext().getAssets().open("fixtures/thepaper-hot-nodes-20261025.json")){raw=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
            List<Models.Item> items=SourceParser.list(Models.Source.THEPAPER,raw);long now=System.currentTimeMillis();List<HotCandidate> candidates=new ArrayList<>();int rank=0;
            for(Models.Item item:items)candidates.add(new HotCandidate(item.source,item.url,item.title,item.snippet,item.detail,item.video,++rank,item.publishedAt,now,ContentLanguage.Body.UNKNOWN,item.sourceTag));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){store.ensureCandidatePolicy();store.upsertSnapshot(Models.Source.THEPAPER,store.beginRefresh(Models.Source.THEPAPER),candidates,now);}
            open(database);await("all text cards",()->rows().size()==17);List<String> base=new ArrayList<>();ui(()->rows().forEach(row->base.add(row.eventId)));
            check(onUi(()->rows().stream().noneMatch(row->row.candidate.video)),"three source-marked video entries stay excluded from discovery");
            for(ContentCategory category:List.of(ContentCategory.TECH,ContentCategory.SOCIETY,ContentCategory.OTHER,ContentCategory.ALL,ContentCategory.CULTURE)){
                click(category);List<String> visible=new ArrayList<>();ui(()->rows().forEach(row->visible.add(row.eventId)));List<String> retained=new ArrayList<>(base);retained.retainAll(visible);check(retained.equals(visible),"filter preserves frozen relative order: "+category.name());
            }
            finishActivity();open(database);await("restored culture",()->rows().size()==1&&controller.categoryFilter()==ContentCategory.CULTURE);
            check(onUi(()->ContentCategory.of(rows().get(0).candidate)==ContentCategory.CULTURE),"reopened activity restores category and source tag from SQLite");
            FeedStore store=(FeedStore)field(controller,"store");check(worker.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==0).get(),"category browsing adds no content or platform preference");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{if(activity!=null)try{finishActivity();}catch(Exception ignored){}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
