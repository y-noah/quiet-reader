package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import static app.quietreader.Models.*;

/** Real list measurement and recycled cards, with no external source requests. */
final class HomeCardSpacingProbe {
    private final Instrumentation test;private DiscoveryActivity home;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    HomeCardSpacingProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable work){test.runOnMainSync(work);}
    void run(){String database="test-card-spacing-"+UUID.randomUUID()+".db";int previous=Theme.mode(test.getTargetContext());
        try{
            for(int theme:new int[]{1,2}){
                test.getTargetContext().getSharedPreferences("appearance",0).edit().putInt("mode",theme).commit();
                long now=System.currentTimeMillis();List<FeedStore.Row> fixtures=new ArrayList<>();
                String[] titles={"嫁金钗","中国公务团组人员被瑞典警方拘押","中国石化：坚决拥护党中央决定","这是一个需要自然换行的长标题，用来核对卡片会随正文高度变化而不是继续保留固定留白"};
                List<HotCandidate> candidates=new ArrayList<>();
                for(int n=0;n<titles.length;n++){HotCandidate candidate=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(now+n),titles[n],"","",false,n+1,null,now);candidates.add(candidate);fixtures.add(new FeedStore.Row(candidate.id,candidate,"",List.of(candidate)));}
                try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){seed.upsertSnapshot(Source.THEPAPER,seed.beginRefresh(Source.THEPAPER),candidates,now);seed.finishInterestSetup(List.of(),Map.of(),now);}
                home=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_database",database).putExtra("test_skip_fetch",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                test.waitForIdleSync();Thread.sleep(500);ui(()->{((DiscoveryController)field(home,"controller")).stop();home.feed(fixtures,0,0,false);});test.waitForIdleSync();
                ListView list=(ListView)field(home,"list");
                for(int round=0;round<3;round++){final int iteration=round;
                    for(int at=0;at<fixtures.size();at++){
                        final int position=at;ui(()->list.setSelection(position));test.waitForIdleSync();Thread.sleep(70);
                        ui(()->{
                            ViewGroup wrapper=(ViewGroup)list.getChildAt(position-list.getFirstVisiblePosition());ViewGroup card=(ViewGroup)wrapper.getChildAt(0);
                            TextView meta=(TextView)field(card,"meta"),summary=(TextView)field(card,"summary"),title=(TextView)field(card,"title");View body=(View)field(card,"body");
                            int contentBottom=body.getTop()+meta.getBottom(),gap=card.getHeight()-card.getPaddingBottom()-contentBottom;
                            check(Math.abs(gap)<=2,"theme "+theme+" round "+iteration+" item "+position+" has no reserved space below source line (px="+gap+")");
                            check(summary.getVisibility()==View.GONE,"no-summary card leaves no preview row");
                            check(title.getBottom()<=meta.getTop(),"title and source never overlap");
                            check(card.getHeight()>=48*home.getResources().getDisplayMetrics().density,"whole card retains comfortable tap height");
                        });
                    }
                    // Rebind a previously empty preview, then remove it again on the same adapter.
                    HotCandidate candidate=new HotCandidate(Source.THEPAPER,fixtures.get(0).candidate.originalUrl,"简介测试","一段与标题不同的简短内容摘要，用于验证一行预览和复用。","",false,1,null,now);
                    List<FeedStore.Row> withPreview=new ArrayList<>(fixtures);withPreview.set(0,new FeedStore.Row(candidate.id,candidate,"",List.of(candidate)));
                    ui(()->home.feed(withPreview,0,0,false));test.waitForIdleSync();
                    ui(()->{ViewGroup card=(ViewGroup)((ViewGroup)list.getChildAt(0)).getChildAt(0);TextView summary=(TextView)field(card,"summary");check(summary.getVisibility()==View.VISIBLE&&summary.getLineCount()==1,"recycled preview appears on exactly one line");});
                    ui(()->home.feed(fixtures,0,0,false));test.waitForIdleSync();
                }
                int[] taps={0};ui(()->{ViewGroup card=(ViewGroup)((ViewGroup)list.getChildAt(0)).getChildAt(0);View menu=(View)field(card,"menu");menu.setOnClickListener(v->taps[0]++);float x=menu.getLeft()+menu.getWidth()/2f,y=menu.getTop()-8*home.getResources().getDisplayMetrics().density;long at=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(at,at+30,MotionEvent.ACTION_UP,x,y,0);try{card.dispatchTouchEvent(down);card.dispatchTouchEvent(up);}finally{down.recycle();up.recycle();}});test.waitForIdleSync();ui(()->check(taps[0]==1,"compact menu retains an expanded tap area above its visible glyph"));
                ui(()->{ViewGroup card=(ViewGroup)((ViewGroup)list.getChildAt(0)).getChildAt(0);TextView menu=(TextView)field(card,"menu");android.graphics.drawable.Drawable icon=menu.getCompoundDrawables()[0];check(menu.getText().length()==0&&icon!=null&&icon.getBounds().height()<=menu.getHeight()&&icon.getBounds().width()<=menu.getWidth(),"overflow icon fits its compact visual box independently of system text scale");});
                android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot!=null){try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(test.getTargetContext().getExternalFilesDir(null),"card-spacing-"+theme+".png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();}
                ui(home::finish);test.waitForIdleSync();home=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(home!=null)home.finish();});test.getTargetContext().getSharedPreferences("appearance",0).edit().putInt("mode",previous).commit();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
