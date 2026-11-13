package app.quietreader;

import android.content.*;
import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;
import static app.quietreader.Models.*;

/** Durable read facts without automatic expiry. Never changes preferences, cookies, or body caches. */
public final class ArticleReadHistory {

    private final SharedPreferences prefs;
    public ArticleReadHistory(Context context,String database){
        prefs=context.getApplicationContext().getSharedPreferences("article-reads-"+HotCandidate.hash(database),0);
    }
    public static String database(Context context,String diagnostic){
        return (context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0&&diagnostic!=null&&diagnostic.matches("test-ui-[a-z0-9-]+\\.db")?diagnostic:FeedStore.DATABASE;
    }
    public boolean seen(HotCandidate candidate){return seen(candidate.source,candidate.originalUrl);}
    public boolean seen(Source source,String url){long at=prefs.getLong(ArticleReadIdentity.key(source,url),0);return at>0;}
    public void mark(HotCandidate candidate,long at){mark(candidate.source,candidate.originalUrl,at);}
    public void mark(Source source,String url,long at){markKey(ArticleReadIdentity.key(source,url),at);}
    private void markKey(String key,long at){
        if(at<=0||at>System.currentTimeMillis()+60000)return;
        synchronized(ArticleReadHistory.class){
            if(prefs.getLong(key,0)>=at)return;
            SharedPreferences.Editor edit=prefs.edit().putLong(key,at);
            // apply updates the shared in-process view immediately, before Back
            // can bind cards or a worker can commit an already prepared batch.
            edit.apply();
        }
    }
    static boolean readable(Document doc){return doc!=null&&doc.hasContent()&&!doc.loginRequired&&!doc.sourceUnavailable&&!doc.filteredVideo&&!doc.unsupportedVideo;}
    /** Read facts follow the original actually displayed, not ranking metadata.
     * Offline older titles are still the same article; interest learning retains
     * its separate, stricter version check. */
    static boolean displayed(Item item,Document doc){return item!=null&&readable(doc)&&item.url.equals(doc.url)&&ArticleVersion.selected(item,doc)&&(!TiebaTopic.isTopic(item.url)||doc.topic!=null);}
    public List<FeedStore.Row> unread(List<FeedStore.Row> rows){
        List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:rows){
            boolean seen=false;for(HotCandidate entry:row.entries)if(seen(entry)){seen=true;break;}
            if(!seen)result.add(row);
        }
        return result;
    }
    /** Older viewport snapshots are only evidence if they retain a successful body. */
    public void migrateBodies(Context context){
        if(prefs.getBoolean("legacy-bodies",false))return;
        for(Map.Entry<String,?> entry:context.getSharedPreferences("reading-history",0).getAll().entrySet())try{
            JSONObject state=new JSONObject(String.valueOf(entry.getValue()));
            if(!state.has("document"))continue;Document doc=ReadingHistory.restore(state);
            if(!readable(doc))continue;
            for(Source source:Source.values())if(UrlPolicy.belongs(source,entry.getKey())){markKey(ArticleReadIdentity.key(source,entry.getKey()),state.optLong("at"));break;}
        }catch(Exception ignored){}
        prefs.edit().putBoolean("legacy-bodies",true).apply();
    }
    public boolean migrated(){return prefs.getBoolean("legacy-feed",false);}
    public void migrated(boolean value){prefs.edit().putBoolean("legacy-feed",value).apply();}
}
