package app.quietreader;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.util.UUID;
import static app.quietreader.Models.*;

/** Main-thread, single-use bridge between discovery and its reader Activity. */
final class ArticleHandoff {
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static ArticleHandoff pending;
    private final String token=UUID.randomUUID().toString();
    private final Item item;
    private final ArticlePreloader loader;
    private final Runnable expire=this::discard;
    private ArticleHandoff(Item item,ArticlePreloader loader){this.item=item;this.loader=loader;}
    static String offer(Item item,ArticlePreloader loader){
        if(!loader.reserve(item))return null;
        if(pending!=null)pending.discard();
        pending=new ArticleHandoff(item,loader);main.postDelayed(pending.expire,5_000);
        return pending.token;
    }
    static ArticlePreloader claim(String token,Item item,Activity owner,Runnable onVideo){
        ArticleHandoff value=pending;
        if(value==null||token==null||!value.token.equals(token))return null;
        if(!ArticleVersion.sameRequest(value.item,item)){value.discard();return null;}
        pending=null;main.removeCallbacks(value.expire);value.loader.adopt(owner,onVideo);return value.loader;
    }
    static void cancel(String token){if(pending!=null&&pending.token.equals(token))pending.discard();}
    private void discard(){if(pending!=this)return;pending=null;main.removeCallbacks(expire);loader.close();}
}
