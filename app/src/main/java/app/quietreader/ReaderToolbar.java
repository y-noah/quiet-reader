package app.quietreader;

import android.view.View;
import android.view.ViewConfiguration;

/** Overlay-only movement: never changes the reader viewport or restores itself on idle. */
final class ReaderToolbar {
    private final View bar;
    private final int slop;
    private float direction;
    ReaderToolbar(View bar){this.bar=bar;slop=ViewConfiguration.get(bar.getContext()).getScaledTouchSlop();}
    void scroll(int y,int oldY){
        int delta=y-oldY;if(delta==0)return;
        if(delta*direction<0)direction=0;direction+=delta;
        if(Math.abs(direction)>slop){show(direction<0);direction=0;}
    }
    void show(boolean visible){
        bar.animate().cancel();bar.setTranslationY(visible?0:-bar.getHeight());
        bar.setImportantForAccessibility(visible?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
}
