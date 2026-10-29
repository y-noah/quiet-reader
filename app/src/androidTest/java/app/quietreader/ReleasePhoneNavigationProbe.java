package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Real retained phone feed; framework views and actual input, no fixture or feedback. */
final class ReleasePhoneNavigationProbe {
    private final Instrumentation test;private Activity home;private ListView list;private View bar;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    ReleasePhoneNavigationProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private View find(View root,boolean wantList){if(wantList&&root instanceof ListView||!wantList&&"设置".contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),wantList);if(v!=null)return v;}return null;}
    private void check(boolean ok,String label){checks++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');if(!ok)throw new IllegalStateException(label);}
    private int position(){int[] p={0};ui(()->p[0]=list.getFirstVisiblePosition()*100000-(list.getChildCount()==0?0:list.getChildAt(0).getTop()));return p[0];}
    private float translation(){float[] value={0};ui(()->value[0]=bar.getTranslationY());return value[0];}
    private void swipe(boolean forward)throws Exception{int[] bounds=new int[4];ui(()->{int[] p=new int[2];list.getLocationOnScreen(p);bounds[0]=p[0];bounds[1]=p[1];bounds[2]=list.getWidth();bounds[3]=list.getHeight();});float x=bounds[0]+bounds[2]*.4f,from=bounds[1]+bounds[3]*(forward?.8f:.3f),to=bounds[1]+bounds[3]*(forward?.3f:.8f);long time=SystemClock.uptimeMillis();for(int n=0;n<=16;n++){MotionEvent e=MotionEvent.obtain(time,time+n*30,n==0?MotionEvent.ACTION_DOWN:n==16?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE,x,from+(to-from)*n/16f,0);try{test.sendPointerSync(e);}finally{e.recycle();}if(n<16)Thread.sleep(30);}Thread.sleep(1000);}
    void run(Bundle args){try{
        Context c=test.getTargetContext();check("true".equals(args.getString("phone"))&&"app.quietreader".equals(c.getPackageName())&&"PHY110".equals(Build.MODEL)&&Build.VERSION.SDK_INT==36,"navigation is confined to authorized ordinary Android16 Find X7 Ultra");
        home=test.startActivitySync(new Intent().setClassName(c.getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        long end=SystemClock.elapsedRealtime()+15000;boolean[] ready={false};do{ui(()->{list=(ListView)find(home.getWindow().getDecorView(),true);View settings=find(home.getWindow().getDecorView(),false);if(settings!=null)bar=(View)settings.getParent();ready[0]=home.hasWindowFocus()&&list!=null&&list.getChildCount()>3&&bar!=null;});if(!ready[0])Thread.sleep(100);}while(!ready[0]&&SystemClock.elapsedRealtime()<end);check(ready[0],"real retained feed has focus and enough visible cards");
        int before=position();swipe(true);int after=position();report.append("FORWARD before=").append(before).append(" after=").append(after).append(" toolbarY=").append(translation()).append('\n');check(after>before,"actual forward touch advances the real list");check(translation()<0,"forward reading hides toolbar after settling");Thread.sleep(1800);check(translation()<0,"idle does not reveal the hidden toolbar");
        before=position();swipe(false);after=position();report.append("REVERSE before=").append(before).append(" after=").append(after).append(" toolbarY=").append(translation()).append('\n');check(after<before&&translation()==0,"actual reverse touch reveals toolbar");
    }catch(Throwable e){failures++;report.append("ERROR ").append(e.getClass().getSimpleName()).append(':').append(e.getMessage()).append('\n');}finally{ui(()->{if(home!=null)home.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
