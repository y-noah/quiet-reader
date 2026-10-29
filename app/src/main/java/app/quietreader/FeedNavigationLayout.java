package app.quietreader;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ListView;
import java.util.function.BooleanSupplier;

/** Feed-only gestures; moving the toolbar never relays out the reading list. */
final class FeedNavigationLayout extends FrameLayout {
    private final ListView list;
    private final View toolbar;
    private final BooleanSupplier canRefresh;
    private final Runnable refresh;
    private final int slop;
    private final float threshold, maximum;
    private boolean feed=true, scrolling, positioned, eligible, pulling;
    private int first, top;
    private float downX,downY,pull,direction;

    FeedNavigationLayout(Context context,ListView list,View toolbar,BooleanSupplier canRefresh,Runnable refresh){
        super(context);this.list=list;this.toolbar=toolbar;this.canRefresh=canRefresh;this.refresh=refresh;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        float density=getResources().getDisplayMetrics().density;threshold=64*density;maximum=88*density;
        addView(list,new LayoutParams(-1,-1));addView(toolbar,new LayoutParams(-1,Math.round(48*density)));
    }
    void feedPage(boolean value){if(feed==value)return;feed=value;positioned=false;direction=0;resetPull();showToolbar(true);}
    int toolbarBottom(){android.graphics.Rect visible=new android.graphics.Rect();return toolbar.getGlobalVisibleRect(visible)?visible.bottom:Integer.MIN_VALUE;}
    boolean toolbarVisible(){return toolbar.getTranslationY()>=0;}
    void scrollState(int state){
        scrolling=state!=android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
        if(!scrolling)direction=0;
    }
    void scrolled(int position,int offset){
        if(positioned&&feed&&scrolling&&!pulling){
            float delta=position==first?top-offset:position>first?slop*2:-slop*2;
            if(delta*direction<0)direction=0;
            direction+=delta;
            if(Math.abs(direction)>slop){showToolbar(direction<0);direction=0;}
        }
        first=position;top=offset;positioned=true;
    }
    private void showToolbar(boolean show){
        float target=show?0:-toolbar.getLayoutParams().height;
        if(toolbar.getTranslationY()==target)return;
        toolbar.animate().cancel();toolbar.setTranslationY(target);
        toolbar.setImportantForAccessibility(show?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                resetPull();downX=event.getX();downY=event.getY();
                eligible=feed&&canRefresh.getAsBoolean()&&!list.canScrollVertically(-1)&&downY>=toolbar.getHeight();break;
            case MotionEvent.ACTION_POINTER_DOWN:eligible=false;resetPull();break;
            case MotionEvent.ACTION_MOVE:
                if(eligible){float x=event.getX()-downX,y=event.getY()-downY;
                    if(Math.abs(x)>slop&&Math.abs(x)>Math.abs(y)||y< -slop)eligible=false;
                    else if(y>slop&&y>Math.abs(x)&&!list.canScrollVertically(-1)){
                        pulling=true;list.animate().cancel();showToolbar(true);getParent().requestDisallowInterceptTouchEvent(true);return true;
                    }
                }break;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:eligible=false;break;
            default:break;
        }
        return super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(!pulling)return super.onTouchEvent(event);
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_MOVE:pull=Math.min(maximum,Math.max(0,(event.getY()-downY-slop)*0.5f));list.setTranslationY(pull);break;
            case MotionEvent.ACTION_UP:
                boolean request=pull>=threshold&&feed&&canRefresh.getAsBoolean();resetPull();if(request)refresh.run();break;
            case MotionEvent.ACTION_CANCEL:case MotionEvent.ACTION_POINTER_DOWN:resetPull();break;
            default:break;
        }
        return true;
    }
    private void resetPull(){pulling=false;eligible=false;pull=0;list.animate().cancel();list.setTranslationY(0);}
    @Override protected void onDetachedFromWindow(){resetPull();toolbar.animate().cancel();super.onDetachedFromWindow();}
}
