package app.quietreader;
import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebSettings;
final class Theme {
    static int mode(Context c){return c.getSharedPreferences("appearance",0).getInt("mode",0);}
    static boolean dark(Context c){int m=mode(c);return m==2||(m==0&&(c.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
    static int background(Context c){return dark(c)?0xff1f2025:0xfffafafa;}
    static void apply(Activity a){boolean d=dark(a);a.setTheme(d?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background(a)));a.getWindow().setStatusBarColor(background(a));a.getWindow().setNavigationBarColor(background(a));a.getWindow().getDecorView().setSystemUiVisibility(d?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);}
    /** Own HTML has an explicit palette; only third-party pages need algorithmic darkening. */
    @SuppressWarnings("deprecation")
    static void configureWeb(WebView web,boolean sourcePage){
        web.setBackgroundColor(background(web.getContext()));
        boolean adapt=sourcePage&&dark(web.getContext());
        if(android.os.Build.VERSION.SDK_INT>=33)web.getSettings().setAlgorithmicDarkeningAllowed(adapt);
        else if(android.os.Build.VERSION.SDK_INT>=29)web.getSettings().setForceDark(adapt?WebSettings.FORCE_DARK_ON:WebSettings.FORCE_DARK_OFF);
    }
}
