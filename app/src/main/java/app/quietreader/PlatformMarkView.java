package app.quietreader;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;
import static app.quietreader.Models.Source;

/** Consistent native CJK glyphs with equal touch areas and a quiet selection marker. */
final class PlatformMarkView extends TextView {
    private final Paint indicator=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;

    PlatformMarkView(Context context,Source source,String label,int color) {
        super(context);
        density=getResources().getDisplayMetrics().density;
        setText(label);setTextColor(color);
        // Keep the single-character navigation within its 48 dp touch row at large system text sizes.
        setTextSize(TypedValue.COMPLEX_UNIT_DIP,20*Math.min(1.3f,getResources().getConfiguration().fontScale));
        setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        setGravity(Gravity.CENTER);setIncludeFontPadding(false);
        setSingleLine(true);setLetterSpacing(0);
        setPadding(0,0,0,Math.round(4*density));
        setContentDescription(source.label);
        indicator.setStrokeCap(Paint.Cap.ROUND);
        indicator.setStrokeWidth(2*density);
    }

    @Override protected void onDraw(Canvas canvas) {
        // TextView clips/translates its canvas to the text layout. Keep that state
        // local so the selection marker uses this view's full bounds.
        int checkpoint=canvas.save();
        try {super.onDraw(canvas);} finally {canvas.restoreToCount(checkpoint);}
        if(isSelected()) {
            indicator.setColor(getCurrentTextColor());
            // A parent draws scrolled TextViews in content coordinates; pin the marker to the viewport.
            canvas.drawLine(getScrollX()+getWidth()/2f-4*density,getScrollY()+getHeight()-6*density,
                    getScrollX()+getWidth()/2f+4*density,getScrollY()+getHeight()-6*density,indicator);
        }
    }
}

