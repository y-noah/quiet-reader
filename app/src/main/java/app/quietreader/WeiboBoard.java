package app.quietreader;

import java.util.List;
import static app.quietreader.Models.*;

/** Official Weibo feeds only; never treat the previous snapshot as a successful refresh. */
final class WeiboBoard {
    static final String PRIMARY="https://weibo.com/ajax/statuses/hot_band";
    static final String LEGACY="https://weibo.com/ajax/side/hotSearch";
    interface Fetch { String get(String url) throws Exception; }
    static List<Item> load(Fetch fetch) throws Exception {
        Exception first;
        try { return SourceParser.list(Source.WEIBO,fetch.get(PRIMARY)); }
        catch(Exception e) { first=e; }
        if(Thread.currentThread().isInterrupted())throw first;
        try { return SourceParser.list(Source.WEIBO,fetch.get(LEGACY)); }
        catch(Exception e) { if(e!=first)e.addSuppressed(first);throw e; }
    }
    static boolean endpoint(String url){return PRIMARY.equals(url)||LEGACY.equals(url);}
    static boolean compatibleCache(String url){return endpoint(url);}
}
