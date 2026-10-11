package app.quietreader;

import android.content.Context;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.CookieManager;
import static app.quietreader.Models.*;

/** Platform defaults for the supported source views. */
final class SourceSession {
    static boolean desktop(Context context,Source source){
        return context.getSharedPreferences("source-session",0).getBoolean(source.name()+"-desktop",source!=Source.WEIBO);
    }
    static void setDesktop(Context context,Source source,boolean desktop){
        context.getSharedPreferences("source-session",0).edit().putBoolean(source.name()+"-desktop",desktop).apply();
    }
    static void preferWeb(Context context,Source source){
        context.getSharedPreferences("source-session",0).edit().putBoolean(source.name()+"-web",true).apply();
    }
    static boolean usesWeb(Context context,Source source){
        return source==Source.ZHIHU||context.getSharedPreferences("source-session",0).getBoolean(source.name()+"-web",false);
    }
    static String userAgent(Context context,Source source){return desktop(context,source)?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(context);}
    static void configure(Context context,Source source,WebView web){
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setUserAgentString(userAgent(context,source));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,source==Source.WEIBO);
    }
}
