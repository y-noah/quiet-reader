package app.quietreader;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

/** Give virtualized source documents a real viewport, behind the opaque native UI. */
final class SourceSurface {
    private SourceSurface() {}
    static void attach(Activity activity,WebView web) {
        ViewGroup host=activity.findViewById(android.R.id.content);
        web.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        web.setFocusable(false);web.setFocusableInTouchMode(false);web.setClickable(false);
        web.getSettings().setMediaPlaybackRequiresUserGesture(true);
        if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);
        host.addView(web,0,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
    }
    static void release(WebView web) {
        web.stopLoading();
        if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);
        web.destroy();
    }
}
