package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Ordinary minified APK, retained state, framework APIs only. */
final class ReleaseRefreshAnimationProbe {
    private final Instrumentation test;
    private Activity home;
    private TextView refresh;
    private int checks, failures;
    private final StringBuilder report=new StringBuilder("Retained ordinary R8 refresh; no fixture writes or production reflection\n");
    ReleaseRefreshAnimationProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){Throwable[] failure={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable error){failure[0]=error;}});if(failure[0]!=null)throw new AssertionError("UI probe failure",failure[0]);}
    private void check(boolean value,String label){checks++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');if(!value)throw new IllegalStateException(label);}
    private TextView find(View root){
        if(root instanceof TextView){String description=String.valueOf(root.getContentDescription());if(description.equals("刷新")||description.equals("正在刷新")||description.equals("查看更新"))return (TextView)root;}
        if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView found=find(((ViewGroup)root).getChildAt(i));if(found!=null)return found;}
        return null;
    }
    private boolean ring(){
        Drawable d=refresh.getCompoundDrawables()[0];if(d==null)return false;
        Rect bounds=d.getBounds();Bitmap b=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.translate(-bounds.left,-bounds.top);d.draw(canvas);
        boolean centerClear=Color.alpha(b.getPixel(b.getWidth()/2,b.getHeight()/2))==0;int pixels=0;
        for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)if(Color.alpha(b.getPixel(x,y))>0)pixels++;
        b.recycle();return centerClear&&pixels>0;
    }
    private boolean solidDot(){
        Drawable d=refresh.getCompoundDrawables()[0];if(d==null)return false;
        Rect bounds=d.getBounds();Bitmap b=Bitmap.createBitmap(bounds.width(),bounds.height(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(b);canvas.translate(-bounds.left,-bounds.top);d.draw(canvas);
        boolean filled=Color.alpha(b.getPixel(b.getWidth()/2,b.getHeight()/2))>0;b.recycle();return filled;
    }
    private boolean idle(){boolean[] value={false};ui(()->{refresh=find(home.getWindow().getDecorView());value[0]=refresh!=null&&!"正在刷新".contentEquals(refresh.getContentDescription())&&!String.valueOf(refresh.getStateDescription()).contains("正在刷新")&&solidDot();});return value[0];}
    void run(Bundle args){try{
        Context c=test.getTargetContext();
        boolean phone="true".equals(args.getString("phone"));
        check(c.getPackageName().equals("app.quietreader")&&Build.VERSION.SDK_INT==36&&(phone?"PHY110".equals(Build.MODEL):Build.HARDWARE.equals("ranchu")),"confined to ordinary API36 emulator or explicitly authorized Find X7 Ultra");
        check((c.getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"actual target is non-debuggable");
        if(!phone)check(c.getSharedPreferences("normal-package-upgrade-probe",0).contains("nonce"),"retained synthetic upgrade fixture exists; never seed user data");
        home=test.startActivitySync(new Intent().setClassName(c.getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        ui(()->home.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON));
        test.waitForIdleSync();Thread.sleep(1000);
        long end=SystemClock.elapsedRealtime()+60000;
        while(SystemClock.elapsedRealtime()<end&&!idle())Thread.sleep(100);
        check(idle(),"retained feed reaches idle dot before explicit refresh");
        ui(()->{
            check(home.hasWindowFocus(),"native feed has foreground focus");
            float density=c.getResources().getDisplayMetrics().density;
            check(refresh.getWidth()>=Math.round(48*density)&&refresh.getHeight()>=Math.round(48*density),"48dp refresh touch target retained");
            check(refresh.performClick(),"ordinary production click is handled");
            check(ring(),"local refresh immediately renders hollow busy ring before queued work returns");
            check(String.valueOf(refresh.getStateDescription()).contains("正在刷新"),"busy state is exposed to accessibility");
            refresh.performClick();refresh.performClick();
            check(ring()&&refresh.isEnabled(),"repeat clicks preserve busy ring and return-to-top target");
        });
        end=SystemClock.elapsedRealtime()+60000;while(SystemClock.elapsedRealtime()<end&&!idle())Thread.sleep(100);
        ui(()->report.append("END description=").append(refresh==null?"missing":refresh.getContentDescription()).append(" state=").append(refresh==null?"missing":refresh.getStateDescription()).append(" ring=").append(refresh!=null&&ring()).append('\n'));
        check(idle(),"production refresh terminates and restores solid idle dot");
    }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
    finally{if(home!=null)ui(home::finish);}
    report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
