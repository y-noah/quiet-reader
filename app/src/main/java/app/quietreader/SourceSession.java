package app.quietreader;

import android.content.Context;
import android.webkit.WebSettings;
import static app.quietreader.Models.*;

/** Keep the Tieba extraction view in the same display mode as its explicit login view. */
final class SourceSession {
    static boolean desktop(Context context,Source source){
        return source==Source.TIEBA?context.getSharedPreferences("source-session",0).getBoolean("tieba-desktop",true):source!=Source.WEIBO;
    }
    static void remember(Context context,Source source,boolean desktop){
        if(source==Source.TIEBA)context.getSharedPreferences("source-session",0).edit().putBoolean("tieba-desktop",desktop).apply();
    }
    static String userAgent(Context context,Source source){return desktop(context,source)?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(context);}
}
