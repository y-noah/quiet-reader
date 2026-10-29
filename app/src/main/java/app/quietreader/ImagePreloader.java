package app.quietreader;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.*;
import app.quietreader.ai.RecommendationPolicy;
import app.quietreader.discovery.ContentLanguage;
import java.util.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Foreground-only, body-first image speculation. No bitmaps, WebViews or retained payloads. */
final class ImagePreloader implements AutoCloseable {
    static final int IMAGE_LIMIT=2*1024*1024,SLICE_BYTES=32*1024*1024,IMAGES_PER_BODY=24;
    static final long SLICE_MS=20_000;
    private final Context context;
    private final ReaderImages images;
    private final BooleanSupplier eligible;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final RequestQueue requests=new RequestQueue();
    private final LinkedHashMap<String,Item> window=new LinkedHashMap<>();
    private final Map<String,Prepared> prepared=new HashMap<>();
    private final LinkedHashMap<String,Long> attempted=new LinkedHashMap<>();
    private final Deque<Job> queue=new ArrayDeque<>();
    private RecommendationPolicy.Watch watch;
    private volatile long revision,deadline;
    private boolean closed,running;
    private Runnable onIdle=()->{};
    void observeIdle(Runnable observer){onIdle=observer;}
    private String activeKey;
    private int spent;
    private final Runnable timeout=this::pause;
    private static final class Prepared {
        final String session,namespace,referer,refererSession;final List<String> urls;
        Prepared(Document doc){session=doc.cachedSession;namespace=doc.cachedNamespace;referer=doc.sourceUrl();refererSession=doc.topic==null?session:doc.topic.postSession;urls=select(doc);}
    }
    private static final class Job {
        final Item item;final Prepared body;final String url,key;
        Job(Item item,Prepared body,String url){this.item=item;this.body=body;this.url=url;key=item.url+'\0'+url+'\0'+body.session+'\0'+body.namespace+'\0'+body.referer+'\0'+body.refererSession;}
    }
    ImagePreloader(Context context,BooleanSupplier eligible){this(context,eligible,ReaderImages.shared);images.attach(context);}
    ImagePreloader(Context context,BooleanSupplier eligible,ReaderImages images){this.context=context.getApplicationContext();this.eligible=eligible;this.images=images;}
    private static String identity(Item item){return item.source.name()+'|'+item.url+'@'+ArticleVersion.hash(item);}
    void offer(List<Item> items){
        pause();window.clear();prepared.clear();
        for(Item item:items){if(window.size()>=20)break;window.putIfAbsent(identity(item),item);}
    }
    void prepared(Item item,Document doc){
        if(closed||!window.containsKey(identity(item))||!ArticleVersion.matches(item,doc)||!ArticleSession.contentCurrent(doc)||!item.url.equals(doc.url)||doc.cachedSession==null||doc.cachedNamespace==null)return;
        if(item.video||doc.filteredVideo||doc.videoTopic||doc.loginRequired||doc.sourceUnavailable||BodyVideoEvidence.classify(item.source,doc)||BodyLanguageEvidence.classify(doc)==ContentLanguage.Body.NON_CHINESE)return;
        prepared.put(identity(item),new Prepared(doc));
    }
    static List<String> select(Document doc){
        Set<String> expanded=new HashSet<>();ReaderHtml.expandPrimary(doc,expanded);
        LinkedHashSet<String> urls=new LinkedHashSet<>();Set<String> media=new HashSet<>();
        media.add(doc.videoPoster);media.add(doc.videoUrl);
        for(VideoAttachment video:doc.videos){media.add(video.poster);media.add(video.url);}
        List<Section> sections=ReaderHtml.sections(doc);
        for(int index=0;index<sections.size();index++){
            Section part=sections.get(index);
            if(part.label.startsWith("回复")||part.label.startsWith("评论"))continue;
            if(part.answer&&!expanded.contains(part.id))continue;
            if(index>0&&!expanded.contains(part.id))continue;
            for(Block block:part.blocks){
                // Inline images are mainly emoji and avatars; only normal body figures qualify.
                if(!block.type.equals("image")||!UrlPolicy.https(block.value)||media.contains(block.value))continue;
                urls.add(block.value);if(urls.size()==IMAGES_PER_BODY)return new ArrayList<>(urls);
            }
        }
        return new ArrayList<>(urls);
    }
    void start(){
        if(closed||running||!eligible.getAsBoolean()||!available())return;
        queue.clear();long now=SystemClock.elapsedRealtime();
        // Cover every article's first figure before continuing deeper into bodies.
        for(int depth=0;depth<IMAGES_PER_BODY;depth++)for(Map.Entry<String,Item> entry:window.entrySet()){
            Prepared body=prepared.get(entry.getKey());if(body==null||depth>=body.urls.size())continue;
            Job job=new Job(entry.getValue(),body,body.urls.get(depth));Long at=attempted.get(job.key);
            if(at==null||now-at>=5*60_000)queue.add(job);
        }
        if(queue.isEmpty())return;
        running=true;spent=0;deadline=now+SLICE_MS;long epoch=++revision;
        main.postDelayed(timeout,SLICE_MS);
        watch=new RecommendationPolicy.Watch(context,reason->pause());watch.start();
        if(running&&epoch==revision)next(epoch);
    }
    private boolean available(){ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);return (cm==null||cm.getActiveNetwork()!=null)&&RecommendationPolicy.restriction(context).isEmpty();}
    private boolean current(Job job,long epoch){return revision==epoch&&SystemClock.elapsedRealtime()<deadline&&job.body.session.equals(ArticleDiskCache.session(job.item.url))&&job.body.refererSession.equals(ArticleDiskCache.session(job.body.referer))&&job.body.namespace.equals(context.getSharedPreferences("article-cache",0).getString("namespace","initial"))&&available();}
    private void next(long epoch){
        if(!running||epoch!=revision)return;
        if(!eligible.getAsBoolean()||!available()||spent>=SLICE_BYTES||queue.isEmpty()){pause();return;}
        Job job=queue.removeFirst();int limit=Math.min(IMAGE_LIMIT,SLICE_BYTES-spent);
        activeKey=job.key;
        attempted.put(job.key,SystemClock.elapsedRealtime());while(attempted.size()>256)attempted.remove(attempted.keySet().iterator().next());
        requests.submit(ticket->{
            int used=limit;
            try{used=images.load(job.url,job.body.referer,ticket,()->current(job,epoch),limit,false).length;}catch(Exception ignored){}
            int cost=used;main.post(()->{if(epoch!=revision||!running)return;activeKey=null;spent+=cost;next(epoch);});
        });
    }
    void pause(){boolean wasRunning=running;revision++;running=false;if(activeKey!=null){attempted.remove(activeKey);activeKey=null;}queue.clear();main.removeCallbacks(timeout);requests.cancelPending();if(watch!=null){watch.close();watch=null;}if(wasRunning)onIdle.run();}
    boolean busy(){return running;}
    @Override public void close(){if(closed)return;pause();closed=true;window.clear();prepared.clear();requests.close();}
}
