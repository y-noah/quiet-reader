package app.quietreader;

import android.content.Context;
import android.webkit.WebSettings;
import static app.quietreader.Models.*;

/** Platform defaults for the five supported source views. */
final class SourceSession {
    static boolean desktop(Context context,Source source){
        return source!=Source.WEIBO;
    }
    static String userAgent(Context context,Source source){return desktop(context,source)?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(context);}
}
