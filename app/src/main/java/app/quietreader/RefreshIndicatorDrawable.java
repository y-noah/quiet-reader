package app.quietreader;

import android.animation.ValueAnimator;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

/** Existing refresh dot becomes a small ring only while refresh work is active. */
final class RefreshIndicatorDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int color,size;
    private final float stroke;
    private ValueAnimator animator;
    private boolean busy;
    private float angle;
    private int alpha=255;
    RefreshIndicatorDrawable(int color,float density){this.color=color;size=Math.round(20*density);stroke=2.2f*density;}
    void busy(boolean value){if(busy==value)return;busy=value;if(!value)animate(false);invalidateSelf();}
    void animate(boolean visible){
        boolean run=busy&&visible&&ValueAnimator.areAnimatorsEnabled();
        if(run&&animator==null){
            animator=ValueAnimator.ofFloat(0,360);animator.setDuration(900);animator.setRepeatCount(ValueAnimator.INFINITE);animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(value->{angle=(float)value.getAnimatedValue();invalidateSelf();});animator.start();
        }else if(!run&&animator!=null){animator.cancel();animator.removeAllUpdateListeners();animator=null;angle=0;invalidateSelf();}
    }
    boolean animating(){return animator!=null&&animator.isRunning();}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();float cx=bounds.exactCenterX(),cy=bounds.exactCenterY();paint.setColor(color);paint.setAlpha(alpha);
        if(!busy){paint.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,Math.min(bounds.width(),bounds.height())*.2f,paint);return;}
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(stroke);paint.setStrokeCap(Paint.Cap.ROUND);
        float radius=(Math.min(bounds.width(),bounds.height())-stroke)/2;
        paint.setAlpha(Math.round(alpha*.2f));canvas.drawCircle(cx,cy,radius,paint);paint.setAlpha(alpha);
        canvas.drawArc(cx-radius,cy-radius,cx+radius,cy+radius,angle-90,110,false,paint);
    }
    @Override public int getIntrinsicWidth(){return size;}
    @Override public int getIntrinsicHeight(){return size;}
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
