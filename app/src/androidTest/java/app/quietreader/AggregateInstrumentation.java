package app.quietreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Independent, black-box user journey. Does not access account state or alter preferences. */
public final class AggregateInstrumentation extends Instrumentation {
    private Activity activity;
    private File output;
    private int checks;
    private final StringBuilder report=new StringBuilder();
    private boolean ifanrOnly;
    private final String[] platforms={"总榜","知乎","微博","虎扑","财联社","爱范儿"};
    @Override public void onCreate(Bundle args){super.onCreate(args);ifanrOnly=args!=null&&"ifanr".equals(args.getString("mode"));start();}
    @Override public void callActivityOnResume(Activity activity){super.callActivityOnResume(activity);this.activity=activity;}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try {
            output=new File(getTargetContext().getExternalFilesDir(null),"aggregate-review/run-"+System.currentTimeMillis());
            if(!output.mkdirs())throw new AssertionError("Cannot create fresh evidence directory");
            Intent launch=new Intent().setClassName(getTargetContext().getPackageName(),"app.quietreader.MainActivity");
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            activity=startActivitySync(launch);waitForIdleSync();SystemClock.sleep(1800);shot("00-initial");
            if(ifanrOnly){ifanrJourney();report.append("PASS ").append(checks).append(" ifanr/theme assertions\n");write();result.putString("stream",report+"Output: "+output.getAbsolutePath()+"\n");finish(Activity.RESULT_OK,result);return;}
            navigation();
            tap("总榜");shot("01-aggregate-opening");
            long began=SystemClock.elapsedRealtime(),firstAt=-1;int previous=-1;
            while(SystemClock.elapsedRealtime()-began<25000){
                int size=cards().size();
                if(size>0&&firstAt<0)firstAt=SystemClock.elapsedRealtime()-began;
                if(size!=previous){report.append("At ").append(SystemClock.elapsedRealtime()-began).append(" ms: ").append(size).append(" rows\n");previous=size;}
                if(size>0&&!nativeText().contains("个获取中"))break;
                SystemClock.sleep(400);
            }
            report.append("First visible rows after ").append(firstAt).append(" ms (may use existing device cache)\n");
            check(cards().size()<=100,"Aggregate is at most 100 rows");
            check(nativeText().contains("Top100"),"Aggregate has an explicit Top100 heading");
            check(nativeText().contains("综合推荐")&&nativeText().contains("非真实热度换算"),"Recommendation order is not presented as comparable real heat");
            final List<View> rows=cards();
            runOnMainSync(()->{for(View row:rows){
                ViewGroup card=(ViewGroup)row;check(card.getChildCount()==2,"No trailing platform column on aggregate card");
                ViewGroup body=(ViewGroup)card.getChildAt(1);
                check(body.getChildCount()==3,"Metadata, spacer, and title share one column");
                TextView meta=(TextView)body.getChildAt(0),title=(TextView)body.getChildAt(2);
                check(meta.getTop()<title.getTop()&&meta.getLeft()==title.getLeft(),"Platform appears above and aligned with title");
                check(meta.getTextSize()<title.getTextSize(),"Platform metadata remains small text");
            }});
            shot("02-aggregate-settled");
            String settled=nativeText();
            tap("来源状态 · 排序说明");SystemClock.sleep(250);shot("03-source-status");
            android.view.accessibility.AccessibilityNodeInfo tree=getUiAutomation().getRootInActiveWindow();
            String stateText=accessibilityText(tree);report.append("Source status dialog:\n").append(stateText).append('\n');
            check(stateText.contains("12个平台"),"Source status dialog opens");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);waitForIdleSync();
            check(nativeText().contains("Top100"),"Closing source status returns to aggregate");
            for(String platform:platforms){tap(platform);waitForIdleSync();check(selected(platform),"Selected tab: "+platform);}
            tap("总榜");SystemClock.sleep(500);
            check(nativeText().contains("Top100"),"Returning from another source restores aggregate");
            if(cards().isEmpty())report.append("OBSERVATION: No live rows available; article journey not claimed.\n");
            else readAndReturn();
            report.append("Home at review start:\n").append(settled).append('\n');
            shot("06-final-home");
            report.append("PASS ").append(checks).append(" assertions; see observations for live-source boundaries.\n");
            write();result.putString("stream",report+"Output: "+output.getAbsolutePath()+"\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            report.append("FAIL: ").append(error.toString()).append('\n');
            try{shot("failure");write();}catch(Exception ignored){}
            result.putString("stream",report+"Output: "+(output==null?"":output.getAbsolutePath())+"\n");finish(Activity.RESULT_CANCELED,result);
        }
    }
    private void navigation(){
        runOnMainSync(()->{
            float density=getTargetContext().getResources().getDisplayMetrics().density;
            int previous=-1;
            for(String platform:platforms){
                View tab=find(activity.getWindow().getDecorView(),platform);
                check(tab!=null,"Navigation has "+platform);
                Rect rect=new Rect();boolean visible=tab.getGlobalVisibleRect(rect);
                report.append(platform).append(" rect=").append(rect).append(" size=").append(tab.getWidth()).append('x').append(tab.getHeight()).append(" density=").append(density).append('\n');
                check(visible&&rect.width()==tab.getWidth(),"Fully visible navigation: "+platform);
                check(rect.height()>=47*density&&rect.width()>=47*density,"At least 48dp-class navigation target: "+platform);
                check(rect.left>previous,"Navigation order: "+platform);previous=rect.left;
                check(tab.getBackground()==null,"Rounded glyph has no background tile: "+platform);
            }
        });
    }
    private void ifanrJourney()throws Exception{
        android.content.SharedPreferences prefs=getTargetContext().getSharedPreferences("appearance",0);
        boolean existed=prefs.contains("mode");int old=prefs.getInt("mode",0);
        try{for(int mode:new int[]{2,1}){
            String prefix=mode==2?"10-dark":"20-light";
            prefs.edit().putInt("mode",mode).commit();runOnMainSync(()->activity.recreate());SystemClock.sleep(1500);
            tap("总榜");SystemClock.sleep(1000);shot(prefix+"-aggregate");
            final List<View> rows=cards();runOnMainSync(()->{Integer left=null;for(View row:rows){ViewGroup c=(ViewGroup)row;ViewGroup body=(ViewGroup)c.getChildAt(1);int[] xy=new int[2];body.getLocationOnScreen(xy);if(left==null)left=xy[0];check(left==xy[0],"All aggregate ranks including 100 use the same text column");}});
            if(!rows.isEmpty()){runOnMainSync(()->{ScrollView s=findScroll(activity.getWindow().getDecorView());s.fullScroll(View.FOCUS_DOWN);});SystemClock.sleep(500);shot(prefix+"-aggregate-end");}
            tap("爱范儿");long until=SystemClock.elapsedRealtime()+22000;
            while(cards().isEmpty()&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(300);
            check(selected("爱范儿"),"Ifanr tab is selected");check(!cards().isEmpty(),"Ifanr has real source articles");SystemClock.sleep(800);shot(prefix+"-ifanr-list");
            View first=cards().get(0);Rect rect=new Rect();runOnMainSync(()->first.getGlobalVisibleRect(rect));check(rect.height()>0,"Ifanr first card visible");click(rect.centerX(),rect.centerY());
            boolean[] ready={false};until=SystemClock.elapsedRealtime()+35000;
            while(SystemClock.elapsedRealtime()<until){runOnMainSync(()->ready[0]=findReader(activity.getWindow().getDecorView())!=null);if(ready[0])break;SystemClock.sleep(300);}
            check(ready[0],"Ifanr real article opens own reader");SystemClock.sleep(1800);shot(prefix+"-ifanr-reader");
            runOnMainSync(()->{WebView web=findReader(activity.getWindow().getDecorView());web.scrollTo(0,web.getHeight());});SystemClock.sleep(500);shot(prefix+"-ifanr-scroll");
            tap("返回");SystemClock.sleep(400);check(selected("爱范儿"),"Read back retains Ifanr source");shot(prefix+"-ifanr-back");
        }}finally{android.content.SharedPreferences.Editor edit=prefs.edit();if(existed)edit.putInt("mode",old);else edit.remove("mode");edit.commit();runOnMainSync(()->activity.recreate());}
    }
    private void readAndReturn()throws Exception {
        ScrollView[] board={null};runOnMainSync(()->board[0]=findScroll(activity.getWindow().getDecorView()));
        // A real swipe tests a non-top return position, without injecting a saved position.
        Rect area=new Rect();runOnMainSync(()->board[0].getGlobalVisibleRect(area));
        swipe(area.centerX(),area.bottom-100,area.centerX(),area.top+150);SystemClock.sleep(1600);
        List<View> available=cards();View target=null;
        for(View card:available){Rect rect=new Rect();boolean[] visible={false};runOnMainSync(()->visible[0]=card.getGlobalVisibleRect(rect));
            String label=String.valueOf(card.getContentDescription());
            if(visible[0]&&rect.height()>70&&area.contains(rect.centerX(),rect.centerY())&&(label.contains("IT之家")||label.contains("掘金")||label.contains("少数派")||label.contains("财联社"))){target=card;break;}}
        if(target==null)for(View card:available){Rect rect=new Rect();boolean[] visible={false};runOnMainSync(()->visible[0]=card.getGlobalVisibleRect(rect));if(visible[0]&&rect.height()>70&&area.contains(rect.centerX(),rect.centerY())){target=card;break;}}
        if(target==null)throw new AssertionError("No visible real article card after scrolling");
        final View chosen=target;String[] label={""};int[] before={0};Rect hit=new Rect();
        runOnMainSync(()->{label[0]=String.valueOf(chosen.getContentDescription());before[0]=board[0].getScrollY();chosen.getGlobalVisibleRect(hit);});
        report.append("Opened card: ").append(label[0]).append('\n');
        shot("04-before-reader");click(hit.centerX(),hit.centerY());SystemClock.sleep(300);
        // A cross-platform group legitimately shows a source chooser before opening.
        String dialog=accessibilityText(getUiAutomation().getRootInActiveWindow());
        if(dialog.contains("选择阅读来源")){
            android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
            android.view.accessibility.AccessibilityNodeInfo option=findAccessible(root," · ");
            if(option!=null){Rect rect=new Rect();option.getBoundsInScreen(rect);click(rect.centerX(),rect.centerY());}
        }
        long deadline=SystemClock.elapsedRealtime()+35000;String readerUrl="";
        while(SystemClock.elapsedRealtime()<deadline){
            String[] url={""};runOnMainSync(()->{WebView reader=findReader(activity.getWindow().getDecorView());if(reader!=null)url[0]=reader.getUrl();});
            readerUrl=url[0];if(readerUrl!=null&&readerUrl.startsWith("https://quiet-reader.invalid/?render="))break;
            SystemClock.sleep(400);
        }
        SystemClock.sleep(500);shot("05-reader");
        report.append("Reader rendered: ").append(readerUrl!=null&&readerUrl.startsWith("https://quiet-reader.invalid/?render=")).append('\n');
        check(readerUrl!=null&&readerUrl.startsWith("https://quiet-reader.invalid/?render="),"Real aggregate article reached the app reader");
        tap("返回");SystemClock.sleep(650);
        check(selected("总榜"),"Reader back returns to aggregate, not the article's native source tab");
        int[] after={0};runOnMainSync(()->after[0]=findScroll(activity.getWindow().getDecorView()).getScrollY());
        report.append("Board return scroll px: ").append(before[0]).append(" -> ").append(after[0]).append('\n');
        check(Math.abs(before[0]-after[0])<50,"Aggregate return keeps reading position");
    }
    private View find(View node,String label){
        if(node==null||!node.isShown())return null;
        String text=node instanceof TextView?((TextView)node).getText().toString():"";
        if(label.equals(text)||label.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))return node;
        if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){View match=find(((ViewGroup)node).getChildAt(n),label);if(match!=null)return match;}
        return null;
    }
    private boolean selected(String label){boolean[] selected={false};runOnMainSync(()->{View view=find(activity.getWindow().getDecorView(),label);selected[0]=view!=null&&view.isSelected();});return selected[0];}
    private List<View> cards(){List<View> out=new ArrayList<>();runOnMainSync(()->collectCards(activity.getWindow().getDecorView(),out));return out;}
    private void collectCards(View node,List<View> out){if(node.getContentDescription()!=null&&node.getContentDescription().toString().endsWith("进入阅读"))out.add(node);if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++)collectCards(((ViewGroup)node).getChildAt(n),out);}
    private ScrollView findScroll(View node){if(node instanceof ScrollView)return (ScrollView)node;if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){ScrollView found=findScroll(((ViewGroup)node).getChildAt(n));if(found!=null)return found;}return null;}
    private WebView findReader(View node){if(node instanceof WebView){String url=((WebView)node).getUrl();if(url!=null&&url.startsWith("https://quiet-reader.invalid/?render="))return (WebView)node;}if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++){WebView found=findReader(((ViewGroup)node).getChildAt(n));if(found!=null)return found;}return null;}
    private String nativeText(){StringBuilder text=new StringBuilder();runOnMainSync(()->collectText(activity.getWindow().getDecorView(),text));return text.toString();}
    private void collectText(View node,StringBuilder text){if(node instanceof TextView)text.append(((TextView)node).getText()).append('\n');if(node instanceof ViewGroup)for(int n=0;n<((ViewGroup)node).getChildCount();n++)collectText(((ViewGroup)node).getChildAt(n),text);}
    private String accessibilityText(android.view.accessibility.AccessibilityNodeInfo node){if(node==null)return "";StringBuilder text=new StringBuilder();if(node.getText()!=null)text.append(node.getText()).append('\n');for(int n=0;n<node.getChildCount();n++)text.append(accessibilityText(node.getChild(n)));return text.toString();}
    private android.view.accessibility.AccessibilityNodeInfo findAccessible(android.view.accessibility.AccessibilityNodeInfo node,String text){if(node==null)return null;if(node.getText()!=null&&node.getText().toString().contains(text))return node;for(int n=0;n<node.getChildCount();n++){android.view.accessibility.AccessibilityNodeInfo found=findAccessible(node.getChild(n),text);if(found!=null)return found;}return null;}
    private void tap(String label)throws Exception{Rect rect=new Rect();runOnMainSync(()->{View target=find(activity.getWindow().getDecorView(),label);check(target!=null,"Tap target present: "+label);check(target.getGlobalVisibleRect(rect),"Tap target visible: "+label);});click(rect.centerX(),rect.centerY());waitForIdleSync();SystemClock.sleep(120);}
    private void click(float x,float y){long at=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0));sendPointerSync(MotionEvent.obtain(at,at+80,MotionEvent.ACTION_UP,x,y,0));}
    private void swipe(float x,float y,float endX,float endY){long at=SystemClock.uptimeMillis();sendPointerSync(MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,x,y,0));for(int n=1;n<=12;n++){SystemClock.sleep(20);sendPointerSync(MotionEvent.obtain(at,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,x+(endX-x)*n/12,y+(endY-y)*n/12,0));}sendPointerSync(MotionEvent.obtain(at,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,endX,endY,0));}
    private void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    private void shot(String name)throws Exception{Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new AssertionError("Screenshot unavailable");try(FileOutputStream stream=new FileOutputStream(new File(output,name+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,stream);}bitmap.recycle();}
    private void write()throws Exception{try(FileOutputStream stream=new FileOutputStream(new File(output,"review.txt"))){stream.write(report.toString().getBytes(StandardCharsets.UTF_8));}}
    @Override public void runOnMainSync(Runnable runnable){
        Throwable[] error={null};super.runOnMainSync(()->{try{runnable.run();}catch(Throwable problem){error[0]=problem;}});
        if(error[0]!=null)throw new AssertionError(error[0]);
    }
}
