package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Black-box UI assertions against the installed, minified release; no internal reflection. */
public final class ReleaseInstrumentation extends Instrumentation {
    private Activity home; private File output; private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    interface Work {void run() throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void ui(Work work)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{work.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);}
    private void check(boolean pass,String name){checks++;if(!pass)failures++;report.append(pass?"PASS ":"FAIL ").append(name).append('\n');}
    private View find(View view,String name){if(name.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof TextView&&name.contentEquals(((TextView)view).getText()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),name);if(found!=null)return found;}return null;}
    private boolean contains(View view,String text){if(view instanceof TextView&&((TextView)view).getText().toString().contains(text))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(contains(((ViewGroup)view).getChildAt(i),text))return true;return false;}
    private void shot(String name)throws Exception{Thread.sleep(350);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new IOException("Screenshot missing");try(FileOutputStream out=new FileOutputStream(new File(output,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        output=new File(getTargetContext().getExternalFilesDir(null),"release-qa/run-"+System.currentTimeMillis());output.mkdirs();
        android.content.pm.PackageInfo info=getTargetContext().getPackageManager().getPackageInfo("app.quietreader",0);
        check("0.3.27".equals(info.versionName)&&info.versionCode==38,"exact 0.3.27 release installed");
        home=startActivitySync(new Intent().setClassName("app.quietreader","app.quietreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(900);
        ui(()->{View root=home.getWindow().getDecorView();check(find(root,"News")!=null,"News brand");check(contains(root,"综合推荐 Top50"),"Top50 visible");check(find(root,"总榜").isSelected(),"default total ranking");});
        shot("01-release-home");
        for(String name:new String[]{"总榜","知乎","微博","虎扑","财联社","爱范儿"}){
            ui(()->{View tab=find(home.getWindow().getDecorView(),name);check(tab instanceof TextView,"native text tab "+name);check(tab!=null&&tab.performClick(),"tab click "+name);});Thread.sleep(200);
            ui(()->{TextView tab=(TextView)find(home.getWindow().getDecorView(),name);check(tab.isSelected(),"tab selected "+name);check(!tab.getTypeface().isBold(),"consistent regular weight "+name);check(tab.getHeight()>=48*home.getResources().getDisplayMetrics().density-1,"48dp touch height "+name);});
        }
        ui(()->find(home.getWindow().getDecorView(),"总榜").performClick());Thread.sleep(400);shot("02-release-total");
    }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}finally{try{if(home!=null)ui(home::finish);report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');try(FileOutputStream out=new FileOutputStream(new File(output,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception error){failures++;}result.putString("stream",report+"\nOutput: "+output);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}}
}
