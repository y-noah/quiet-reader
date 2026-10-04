package app.quietreader;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import java.util.*;
import static app.quietreader.Models.*;

/** One speculative request, bounded look-ahead, and promotion instead of duplicate work. */
final class ArticlePreloader implements AutoCloseable {
    interface Loader {void load(Item item,Repository.Result<Document> cb);void cancel();}
    private final Loader loader;
    private final Activity activity;
    private final Repository repo;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable onVideo;
    private final Map<String,Long> attempted=new LinkedHashMap<String,Long>(){
        @Override protected boolean removeEldestEntry(Map.Entry<String,Long> entry){return size()>64;}
    };
    private final Deque<Item> queue=new ArrayDeque<>();
    private Item active;
    private DynamicReader dynamic;
    private Repository.Result<Document> foreground;
    private int revision;
    private boolean closed;

    ArticlePreloader(Activity activity,Runnable onVideo){this(activity,onVideo,null);}
    ArticlePreloader(Activity activity,Runnable onVideo,Loader loader){this.activity=activity;this.onVideo=onVideo;this.loader=loader;repo=new Repository(activity);}

    void offer(List<Item> candidates){
        if(closed||foreground!=null)return;
        queue.clear();
        Set<String> seen=new HashSet<>();
        // At most three predictions per viewport; do not walk the entire board while idle.
        for(Item item:candidates){if(seen.add(item.url))queue.add(item);if(queue.size()==3)break;}
        if(active!=null&&queue.stream().noneMatch(i->i.url.equals(active.url)))cancelActive();
        main.removeCallbacks(pump);main.postDelayed(pump,350);
    }
    private final Runnable pump=this::next;
    private void next(){
        if(closed||active!=null||foreground!=null||repo.offline())return;
        while(!queue.isEmpty()){
            Item item=queue.removeFirst();Long at=attempted.get(item.url);
            if(!item.source.readable()||!UrlPolicy.belongs(item.source,item.url)||item.url.equals(item.source.login)||item.url.equals(item.source.endpoint)||repo.hiddenVideo(item)||repo.cachedArticle(item)!=null||(at!=null&&System.currentTimeMillis()-at<5*60*1000))continue;
            active=item;attempted.put(item.url,System.currentTimeMillis());int request=++revision;
            if(loader!=null)loader.load(item,new Repository.Result<Document>(){
                public void success(Document doc){if(valid(request))complete(item,doc);}
                public void failure(String why){if(valid(request)){Repository.Result<Document> cb=foreground;foreground=null;active=null;if(cb!=null)cb.failure(why);else main.post(pump);}}
            });
            else if(item.source==Source.ZHIHU)loadDynamic(item,request);
            else repo.article(item,new Repository.Result<Document>(){
                public void success(Document doc){if(valid(request)){if(doc.canPresent())complete(item,doc);else loadDynamic(item,request);}}
                public void failure(String why){if(valid(request))loadDynamic(item,request);}
            });
            return;
        }
    }
    /** Must be called before replacing the board. A true result now owns the callback. */
    boolean promote(Item item,Repository.Result<Document> callback){
        queue.clear();main.removeCallbacks(pump);
        if(active!=null&&active.url.equals(item.url)){foreground=callback;return true;}
        cancelActive();return false;
    }
    private boolean valid(int request){return !closed&&active!=null&&request==revision;}
    private void loadDynamic(Item item,int request){
        if(!valid(request))return;
        dynamic=new DynamicReader(activity,item,new Repository.Result<Document>(){
            public void success(Document doc){if(valid(request))complete(item,doc);}
            public void failure(String why){if(!valid(request))return;Repository.Result<Document> cb=foreground;foreground=null;active=null;dynamic=null;if(cb!=null)cb.failure(why);else main.post(pump);}
        });
    }
    private void complete(Item item,Document doc){
        if(doc.title.trim().isEmpty())doc.title=item.title;
        if(doc.filteredVideo)repo.rememberVideo(item);else repo.cacheArticle(doc);
        Repository.Result<Document> cb=foreground;foreground=null;active=null;dynamic=null;
        if(cb!=null)cb.success(doc);
        else {if(doc.filteredVideo)onVideo.run();main.post(pump);}
    }
    private void cancelActive(){
        revision++;repo.cancelPending();if(loader!=null)loader.cancel();if(dynamic!=null){dynamic.close();dynamic=null;}
        if(active!=null)attempted.remove(active.url);active=null;foreground=null;
    }
    void pause(){queue.clear();main.removeCallbacks(pump);cancelActive();}
    void trim(){if(foreground==null)pause();}
    void reattach(){if(dynamic!=null)dynamic.reattach(activity);}
    @Override public void close(){pause();closed=true;repo.close();}
}
