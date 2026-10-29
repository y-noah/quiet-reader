package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Actual view dispatch, layout and ListView scroll; no source requests or phone data. */
final class FeedNavigationProbe {
    private final Instrumentation test;
    private DiscoveryActivity activity;
    private FeedNavigationLayout frame;
    private ListView list;
    private TextView toolbar;
    private boolean enabled=true;
    private int refreshes,checks,failures;
    private volatile int actualScrollState;
    private float density;
    private final StringBuilder report=new StringBuilder();
    FeedNavigationProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void event(long start,int action,float x,float y){ui(()->{MotionEvent event=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,x*density,y*density,0);try{frame.dispatchTouchEvent(event);}finally{event.recycle();}});}
    private void gesture(float x,float y,float dx,float dy,boolean cancel)throws Exception{
        long start=SystemClock.uptimeMillis();event(start,MotionEvent.ACTION_DOWN,x,y);
        for(int n=1;n<=12;n++){event(start,MotionEvent.ACTION_MOVE,x+dx*n/12,y+dy*n/12);Thread.sleep(12);}
        event(start,cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x+dx,y+dy);test.waitForIdleSync();
    }
    private void top()throws Exception{ui(()->{list.setSelectionFromTop(0,list.getPaddingTop());});test.waitForIdleSync();Thread.sleep(100);}
    void run(){
        try{
            Intent intent=new Intent(test.getTargetContext(),DiscoveryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("test_skip_fetch",true);
            activity=(DiscoveryActivity)test.startActivitySync(intent);
            ui(()->{
                density=activity.getResources().getDisplayMetrics().density;
                list=new ListView(activity);list.setPadding(0,Math.round(48*density),0,0);
                String[] rows=new String[100];for(int i=0;i<rows.length;i++)rows[i]="手势验证正文 "+i;
                list.setAdapter(new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,rows));
                toolbar=new TextView(activity);toolbar.setText("导航");toolbar.setBackgroundColor(0xffeeeeee);
                frame=new FeedNavigationLayout(activity,list,toolbar,()->enabled,()->refreshes++);
                list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView v,int state){actualScrollState=state;frame.scrollState(state);}public void onScroll(AbsListView v,int first,int count,int total){View child=list.getChildAt(0);frame.scrolled(first,child==null?0:child.getTop());}});
                activity.setContentView(frame);
            });test.waitForIdleSync();top();
            int[] height={0},offset={0};ui(()->{height[0]=list.getHeight();offset[0]=list.getChildAt(0).getTop();});
            gesture(80,100,0,60,false);ui(()->check(refreshes==0,"short downward drag does not refresh"));
            top();gesture(80,100,0,220,false);ui(()->{check(refreshes==1,"top pull refreshes exactly once on release");check(list.getTranslationY()==0,"released pull restores list translation");check(list.getHeight()==height[0],"pull does not resize list");});
            top();gesture(80,100,0,220,true);ui(()->check(refreshes==1,"cancelled pull does not refresh"));
            top();gesture(60,100,200,35,false);ui(()->check(refreshes==1,"horizontal drag does not refresh"));
            top();enabled=false;gesture(80,100,0,220,false);ui(()->check(refreshes==1,"loading gate suppresses refresh"));enabled=true;
            top();ui(()->frame.feedPage(false));gesture(80,100,0,220,false);ui(()->check(refreshes==1,"settings page never enables pull refresh"));ui(()->frame.feedPage(true));
            top();long start=SystemClock.uptimeMillis();event(start,MotionEvent.ACTION_DOWN,80,350);
            for(int n=1;n<=10;n++){event(start,MotionEvent.ACTION_MOVE,80,350-20*n);Thread.sleep(20);}
            ui(()->{check(toolbar.getTranslationY()<0,"toolbar hides during downward browsing");check(list.getHeight()==height[0],"toolbar visibility does not resize list");});
            event(start,MotionEvent.ACTION_UP,80,150);test.waitForIdleSync();
            long idleDeadline=SystemClock.elapsedRealtime()+6000;
            while(actualScrollState!=AbsListView.OnScrollListener.SCROLL_STATE_IDLE&&SystemClock.elapsedRealtime()<idleDeadline)Thread.sleep(40);
            check(actualScrollState==AbsListView.OnScrollListener.SCROLL_STATE_IDLE,"real ListView reaches idle after finger release and fling");
            Thread.sleep(250);
            ui(()->{
                check(toolbar.getTranslationY()<0,"stopping downward browsing keeps toolbar hidden");
                check(toolbar.getImportantForAccessibility()==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,"hidden toolbar stays out of accessibility focus after stopping");
                frame.feedPage(true);
                check(toolbar.getTranslationY()<0,"same-feed update does not reveal the toolbar");
            });
            gesture(80,150,0,90,false);
            ui(()->check(toolbar.getTranslationY()==0,"toolbar returns while browsing upward"));
            ui(()->check(toolbar.getTranslationY()==0,"stopping after upward browsing keeps toolbar visible"));
            ui(()->list.setSelectionFromTop(10,0));test.waitForIdleSync();gesture(80,100,0,220,false);
            ui(()->check(refreshes==1,"drag from middle of feed cannot refresh"));
            top();ui(()->check(list.getChildAt(0).getTop()==offset[0],"return to top retains original content offset"));
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
