package app.quietreader;

import android.content.Context;
import android.view.MotionEvent;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** A real, wide browser surface behind a small two-axis window. No page zoom or DOM rewriting. */
final class SourceWindow extends FrameLayout {
    private final ScrollView vertical;
    private final HorizontalScrollView horizontal;
    private final WebView web;
    private final FrameLayout surface;

    SourceWindow(Context context,WebView web){
        super(context);this.web=web;
        vertical=new ScrollView(context){
            private float downX,downY;
            @Override public boolean onInterceptTouchEvent(MotionEvent event){
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downY=event.getY();}
                // At the window's edge, let a new stroke continue scrolling the webpage.
                if(event.getActionMasked()==MotionEvent.ACTION_MOVE){
                    float delta=event.getY()-downY;
                    if(event.getPointerCount()>1||Math.abs(event.getX()-downX)>Math.abs(delta)||(delta<0&&!canScrollVertically(1))||(delta>0&&!canScrollVertically(-1)))return false;
                }
                return super.onInterceptTouchEvent(event);
            }
        };vertical.setFillViewport(true);
        horizontal=new HorizontalScrollView(context){
            @Override public boolean onInterceptTouchEvent(MotionEvent event){
                if(event.getPointerCount()>1)return false;
                return super.onInterceptTouchEvent(event);
            }
        };horizontal.setFillViewport(true);
        vertical.setContentDescription("来源页面上下滑动窗口");
        horizontal.setContentDescription("来源页面左右滑动窗口");
        surface=new FrameLayout(context);
        surface.addView(web,new FrameLayout.LayoutParams(1,1));
        horizontal.addView(surface,new FrameLayout.LayoutParams(1,1));
        vertical.addView(horizontal,new ScrollView.LayoutParams(-1,-2));
        addView(vertical,new FrameLayout.LayoutParams(-1,-1));
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        resize(MeasureSpec.getSize(widthSpec),MeasureSpec.getSize(heightSpec));
        super.onMeasure(widthSpec,heightSpec);
    }
    private void resize(int viewportWidth,int viewportHeight){
        if(viewportWidth==0||viewportHeight==0)return;
        int width=Math.max(viewportWidth,dp(1280));
        int height=Math.max(viewportHeight,dp(960));
        if(web.getLayoutParams().width==width&&web.getLayoutParams().height==height)return;
        web.setLayoutParams(new FrameLayout.LayoutParams(width,height));
        surface.setMinimumWidth(width);surface.setMinimumHeight(height);
        surface.setLayoutParams(new FrameLayout.LayoutParams(width,height));
        horizontal.setLayoutParams(new ScrollView.LayoutParams(-1,height));
    }
    int panX(){return horizontal.getScrollX();}
    int panY(){return vertical.getScrollY();}
    void restorePosition(int x,int y){horizontal.scrollTo(x,0);vertical.scrollTo(0,y);}
    void center(){
        horizontal.scrollTo(Math.max(0,(web.getWidth()-getWidth())/2),0);
        vertical.scrollTo(0,Math.max(0,(web.getHeight()-getHeight())/2));
    }
}
