package app.quietreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.CornerPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.widget.TextView;
import static app.quietreader.Models.Source;

/** Six compact, rounded monoline lettermarks, independent of the device's CJK font. */
final class PlatformMarkView extends TextView {
    private final Paint ink=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mark=new Path();
    private final float density;

    PlatformMarkView(Context context,Source source,String label,int color) {
        super(context);
        density=getResources().getDisplayMetrics().density;
        // Retain real text and the full platform name for accessibility and selection semantics.
        setText(label);setTextColor(color);setTextSize(18);setSingleLine(true);
        setContentDescription(source.label);setIncludeFontPadding(false);
        ink.setStyle(Paint.Style.STROKE);ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);ink.setPathEffect(new CornerPathEffect(.8f));
        switch(source) {
            case AGGREGATE: // 总
                stroke(10,3,13,7);stroke(23,3,20,7);stroke(8,10,25,10,25,19,8,19,8,10);
                stroke(5,23,3,28);stroke(11,23,11,28,23,28,25,25);stroke(17,22,19,25);stroke(28,22,30,27);break;
            case ZHIHU:
                stroke(7,4,4,10);stroke(6,7,15,7);stroke(3,15,16,15);
                stroke(10,7,10,15,8,22,3,28);stroke(10,20,15,26);
                stroke(20,8,29,8,29,25,20,25,20,8);break;
            case WALLSTREET:
                stroke(7,20,7,5,25,5,25,20);stroke(16,11,16,18,13,24,5,28);
                stroke(19,20,19,26,21,28,27,28,29,26,29,23);break;
            case IFANR: // 爱：圆头单线，与其他入口一致，不增加背景块。
                stroke(7,6,25,3);stroke(8,9,10,12);stroke(15,7,17,11);stroke(25,7,22,12);
                stroke(4,17,4,13,29,13,29,17);stroke(7,18,27,18);
                stroke(14,15,12,21,7,27,3,29);stroke(14,22,25,22,20,27,14,30);
                stroke(14,23,20,27,29,30);break;
            case GEEKPARK: // 极
                stroke(3,10,13,10);stroke(8,3,8,29);stroke(8,12,3,22);stroke(9,15,13,20);
                stroke(16,5,26,5,22,14,29,14,26,22,20,28);
                stroke(18,6,18,17,16,24,13,29);stroke(19,14,23,22,30,28);break;
            case CLS: // 财
                stroke(4,21,4,5,14,5,14,21);stroke(9,10,9,21,7,25,2,29);
                stroke(10,24,15,28);stroke(18,11,30,11);stroke(26,3,26,28,22,28);
                stroke(25,13,21,19,17,23);break;
            case SMZDM:
                stroke(8,4,5,11,2,15);stroke(6,12,6,28);
                stroke(12,7,29,7);stroke(20,3,19,12);
                stroke(13,27,13,12,27,12,27,27);stroke(14,17,26,17);
                stroke(14,22,26,22);stroke(10,28,30,28);break;
            case WEIBO:
                stroke(7,4,2,9);stroke(8,11,2,17);stroke(5,15,5,28);
                stroke(11,5,11,11,19,11,19,5);stroke(15,3,15,10);
                stroke(10,15,20,15);stroke(12,20,12,24,9,28);
                stroke(12,20,17,20,17,27,21,24);
                stroke(25,4,23,12,21,16);stroke(24,10,30,10);
                stroke(28,11,27,19,24,25,21,28);stroke(23,16,26,24,30,28);break;
            case HUPU:
                stroke(17,3,17,9);stroke(18,5,25,5);
                stroke(8,17,8,10,29,10,27,15);stroke(8,17,7,23,3,28);
                stroke(11,16,23,13);stroke(16,12,16,19,18,20,24,20,26,18);
                stroke(14,24,14,26,11,29);stroke(14,24,21,24,21,28,23,29,28,29,29,26);break;
            default: // 贴
                stroke(4,21,4,5,14,5,14,21);stroke(9,10,9,21,7,25,2,29);
                stroke(10,24,15,28);stroke(22,3,22,15);stroke(23,8,30,8);
                stroke(19,16,29,16,29,27,19,27,19,16);break;
        }
    }

    private void stroke(float... points) {
        mark.moveTo(points[0],points[1]);
        for(int i=2;i<points.length;i+=2)mark.lineTo(points[i],points[i+1]);
    }

    @Override protected void onDraw(Canvas canvas) {
        // Draw the rounded strokes themselves, never a tile behind a square system glyph.
        float size=Math.min(24*density,Math.min(getWidth()-8*density,getHeight()-16*density));
        canvas.save();canvas.translate((getWidth()-size)/2,(getHeight()-size)/2-2*density);
        canvas.scale(size/32,size/32);
        ink.setColor(getCurrentTextColor());ink.setAlpha(isPressed()?170:255);
        ink.setStrokeWidth(isSelected()?2.7f:2.4f);canvas.drawPath(mark,ink);canvas.restore();
        if(isSelected()) {
            ink.setStrokeWidth(2*density);
            canvas.drawLine(getWidth()/2f-5*density,getHeight()-4*density,
                    getWidth()/2f+5*density,getHeight()-4*density,ink);
        }
    }
}
