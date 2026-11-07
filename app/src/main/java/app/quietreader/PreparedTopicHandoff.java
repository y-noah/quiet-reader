package app.quietreader;

import android.app.Activity;
import android.os.SystemClock;
import java.util.UUID;
import static app.quietreader.Models.*;

/** Single-use prepared content, avoiding JSON/disk acquisition on the tap path. */
final class PreparedTopicHandoff {
    private static Item item;private static Document document;private static String token;private static long at;
    static String offer(Item selected,Document doc){item=selected;document=doc;at=SystemClock.elapsedRealtime();String offered=token=UUID.randomUUID().toString();new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(()->{if(offered.equals(token)){item=null;document=null;token=null;}},5000);return offered;}
    static Document take(String key,Item selected,Activity owner){
        if(key==null||!key.equals(token))return null;
        Document result=document;boolean valid=SystemClock.elapsedRealtime()-at<5000&&ArticleVersion.sameRequest(item,selected)&&TopicBundle.initialReady(result)&&new ArticleSession(owner,selected).owns(result)&&ArticleSession.contentCurrent(result);
        item=null;document=null;token=null;return valid?result:null;
    }
}
