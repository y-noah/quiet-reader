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
        // V2EX and Hupu render their thread/replies in the HTML. Send the shared account
        // cookies through Repository first; a past login visit must not force
        // scripts/analytics to run on every read. Hupu's public thread repeatedly
        // rotates request cookies in WebView; the same shared-cookie native read
        // obtains its complete current page without changing the session.
        // Failed/restricted extraction still falls back to the shared WebView.
        if(source==Source.V2EX||source==Source.HUPU)return false;
        return source==Source.ZHIHU||context.getSharedPreferences("source-session",0).getBoolean(source.name()+"-web",false);
    }
    static boolean nativeDocumentReady(Source source,Document document){
        // Hupu may hydrate the authenticated response after the server-rendered
        // shell. Try the existing shared WebView before presenting its login gate.
        return document.canPresent()&&(source!=Source.HUPU||!document.loginRequired);
    }
    static String userAgent(Context context,Source source){return desktop(context,source)?Repository.DESKTOP_UA:WebSettings.getDefaultUserAgent(context);}
    static void configure(Context context,Source source,WebView web){
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setUserAgentString(userAgent(context,source));
        // Desktop Tieba omits viewport metadata. Use Android's desktop CSS viewport
        // so its virtual list is not initialized with a narrow mobile reply window.
        // The native WebView remains the size of the existing foreground surface.
        if(source==Source.TIEBA&&desktop(context,source)){
            settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,source==Source.WEIBO);
    }
}
