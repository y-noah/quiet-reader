package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import android.widget.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real reader toolbar and matching action sheet, with no live account or source requests. */
final class ReaderActionsProbe {
    private final Instrumentation test;private final boolean dark;private MainActivity reader;private Repository repo;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReaderActionsProbe(Instrumentation test,boolean dark){this.test=test;this.dark=dark;}
    private Object field(String name){try{Field value=MainActivity.class.getDeclaredField(name);value.setAccessible(true);return value.get(reader);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable action){test.runOnMainSync(action);test.waitForIdleSync();}
    private void check(boolean good,String name){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(name).append('\n');}
    private View find(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())||view instanceof TextView&&label.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=find(((ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}return null;}
    void run(){
        SharedPreferences prefs=test.getTargetContext().getSharedPreferences("appearance",0);boolean had=prefs.contains("mode");int previous=prefs.getInt("mode",0);SettingsActivity settings=null;
        try{
            prefs.edit().putInt("mode",dark?2:1).commit();Item item=new Item(Source.V2EX,"统一菜单合成正文","https://www.v2ex.com/t/"+System.currentTimeMillis(),"");Document body=new Document();body.url=item.url;body.title=item.title;body.byline="实际作者";body.blocks.add(new Block("text","这是已准备好的阅读正文。"));ArticleVersion.capture(item,body);
            ui(()->{repo=new Repository(test.getTargetContext());repo.cacheArticle(body);});reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long until=SystemClock.elapsedRealtime()+7000;while(field("readerWeb")==null&&SystemClock.elapsedRealtime()<until)Thread.sleep(40);
            ui(()->{check(field("readerWeb") instanceof WebView,"cached article uses the unified reader");check(find(reader.getWindow().getDecorView(),"V2EX")==null&&find(reader.getWindow().getDecorView(),"New")==null,"reader toolbar contains no source name or brand");View more=find(reader.getWindow().getDecorView(),"更多选项");check(more!=null&&more.performClick(),"actual toolbar opens reader controls");});
            Dialog sheet=(Dialog)field("readerActions");test.waitForIdleSync();Thread.sleep(200);
            ui(()->{
                check(sheet!=null&&sheet.isShowing(),"reader action sheet is visible");check((sheet.getWindow().getAttributes().gravity&Gravity.VERTICAL_GRAVITY_MASK)==Gravity.BOTTOM,"controls open as a bottom sheet");
                View root=sheet.getWindow().getDecorView();float density=reader.getResources().getDisplayMetrics().density;
                for(String label:new String[]{"重新读取","来源 / 登录","阅读字号","外观","阅读设置"}){View row=find(root,label);android.graphics.Rect rect=new android.graphics.Rect();check(row!=null&&row.getGlobalVisibleRect(rect)&&rect.height()>=48*density-1,"visible minimum touch target: "+label);if(row!=null)check(((TextView)row).getCurrentTextColor()==Theme.ink(reader),"page text palette: "+label);}
                View row=find(root,"重新读取");check(row!=null&&((View)row.getParent()).getBackground() instanceof GradientDrawable&&((GradientDrawable)((View)row.getParent()).getBackground()).getColor().getDefaultColor()==Theme.background(reader),"sheet surface matches the reader palette");
            });
            Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot==null)throw new IOException("Missing menu screenshot");try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"minimal-reader-menu-"+(dark?"dark":"light")+".png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}
            Instrumentation.ActivityMonitor monitor=test.addMonitor(SettingsActivity.class.getName(),null,false);
            try{ui(()->{View row=find(sheet.getWindow().getDecorView(),"阅读设置");check(row!=null&&row.performClick(),"reading settings action is clickable");});settings=(SettingsActivity)test.waitForMonitorWithTimeout(monitor,5000);check(settings!=null&&!sheet.isShowing(),"action closes the sheet and opens existing reading settings");}finally{test.removeMonitor(monitor);}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{SettingsActivity end=settings;ui(()->{if(end!=null)end.finish();if(reader!=null)reader.finish();if(repo!=null)repo.close();});SharedPreferences.Editor edit=prefs.edit();if(had)edit.putInt("mode",previous);else edit.remove("mode");edit.commit();}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append('\n').toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
