package app.quietreader;

import android.app.Activity;
import android.os.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Prepare candidate topics off-screen, before publishing their titles. */
final class TopicPreparation implements AutoCloseable {
    private final Activity activity;
    private final ArticlePreloader loader;
    private final RequestQueue media=new RequestQueue();
    private final java.util.concurrent.ThreadPoolExecutor mediaWorker=new java.util.concurrent.ThreadPoolExecutor(1,1,5,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(100),r->{Thread t=new Thread(r,"topic-media-warm");t.setDaemon(true);return t;});
    private final Set<String> preparingMedia=new HashSet<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String,Item> items=new LinkedHashMap<>();
    private final Map<String,Document> ready=new HashMap<>();
    private final Map<String,Integer> mediaCounts=new HashMap<>();
    int mediaCount(Item item){return mediaCounts.getOrDefault(key(item),0);}
    private final Set<String> verifying=new HashSet<>();
    private final Runnable changed;
    private final java.util.function.BiConsumer<Item,Document> onReady;
    private final Runnable start=this::start;
    private volatile boolean foreground,closed;
    private boolean interacting;
    private volatile long epoch;
    private int limit=TopicBundle.PAGE;
    void window(int count){int next=Math.max(TopicBundle.PAGE,count);if(next==limit)return;limit=next;loader.topicWindow(limit);if(foreground&&!closed){main.removeCallbacks(start);main.post(start);}}
    TopicPreparation(Activity activity,Runnable changed,java.util.function.BiConsumer<Item,Document> onReady){
        this.activity=activity;this.changed=changed;this.onReady=onReady;
        loader=new ArticlePreloader(activity,()->{},null,100,()->{},true).topicWindow(TopicBundle.PAGE);
        loader.observePrepared(this::prepared);
        loader.observeFailure(item->{main.removeCallbacks(start);if(foreground&&!closed)main.postDelayed(start,61_000);});
    }
    static boolean supports(Item item){return item.source==Source.TIEBA&&TiebaTopic.isTopic(item.url);}
    private static String key(Item item){return item.url+"|"+ArticleVersion.hash(item);}
    private boolean cachedVersion(Item item,Document doc){return ArticleVersion.matches(item,doc)||ReadingNetwork.offline(activity)&&ArticleVersion.readableCached(item,doc,System.currentTimeMillis(),true);}
    void plan(List<Item> candidates){
        ArticleSessionSnapshot.run(()->planSnapshot(candidates));
    }
    private void planSnapshot(List<Item> candidates){
        if(closed)return;
        LinkedHashMap<String,Item> next=new LinkedHashMap<>();Set<String> retain=new HashSet<>();
        for(Item item:candidates)if(supports(item)){retain.add(key(item));if(ready(item)==null&&next.size()<100)next.putIfAbsent(item.url,item);}
        boolean same=next.size()==items.size();
        if(same)for(Map.Entry<String,Item> entry:next.entrySet())if(!ArticleVersion.sameRequest(entry.getValue(),items.get(entry.getKey()))){same=false;break;}
        ready.keySet().retainAll(retain);mediaCounts.keySet().retainAll(retain);
        if(same)return;
        items.clear();items.putAll(next);
        main.removeCallbacks(start);main.post(start);
    }
    Document ready(Item item){
        return ArticleSessionSnapshot.within(()->readySnapshot(item));
    }
    private Document readySnapshot(Item item){
        Document doc=ready.get(key(item));
        if(doc==null||mediaCount(item)<Math.min(limit,TopicBundle.total(doc))||!TopicBundle.prepared(doc,limit)||!cachedVersion(item,doc)||!ArticleSession.contentCurrent(doc))return null;
        long age=System.currentTimeMillis()-doc.cachedAt;
        return age>=0&&age<=ArticleDiskCache.MAX_AGE&&new ArticleSession(activity,item).owns(doc)?doc:null;
    }
    private void prepared(Item item,Document doc){
        if(closed||!foreground||!items.containsKey(item.url)||ready(items.get(item.url))!=null)return;
        // The media barrier below owns image warming. Do not run a second image
        // queue or redraw the feed for every incomplete child document.
        if(!TopicBundle.prepared(doc,limit)||!verifying.add(item.url))return;
        long request=epoch;Item root=items.get(item.url);
        if(root==null||!cachedVersion(root,doc)){verifying.remove(item.url);return;}
        new ArticleDiskCache(activity).verify(doc,ReadingNetwork.offline(activity),hit->main.post(()->{
            android.util.Log.d("NewTopicCache","disk verified="+(hit!=null)+" initial="+(hit!=null&&TopicBundle.prepared(hit.document,limit))+" metadata="+(hit!=null&&ArticleVersion.matches(root,hit.document)));
            verifying.remove(item.url);if(closed||!foreground||request!=epoch)return;
            if(hit!=null&&TopicBundle.prepared(hit.document,limit)&&cachedVersion(root,hit.document)&&ArticleSession.contentCurrent(hit.document)){
                Document whole=hit.document;for(Item choice:whole.topic.choices)if(!choice.video){whole=TopicBundle.focus(whole,choice.url);break;}
                prepareMedia(root,whole,request);return;
            }
        }));
    }
    private void prepareMedia(Item item,Document doc,long request){
        if(!preparingMedia.add(item.url))return;

        int mediaLimit=limit;RequestQueue.Ticket ticket=media.register();
        mediaWorker.execute(()->{
            boolean complete=true;long bytes=0;
            try{
                Set<String> seen=new HashSet<>();
                int n=0;for(Item choice:doc.topic.choices){if(choice.video)continue;if(n++>=mediaLimit)break;Document body=TopicBundle.find(doc,choice.url);if(body==null)continue;
                    for(Section section:ReaderHtml.sections(body))for(Block block:section.blocks){
                        List<String> urls=new ArrayList<>();if(block.type.equals("image"))urls.add(block.value);for(InlineImage image:block.inlineImages)urls.add(image.url);
                        for(String url:urls)if(UrlPolicy.https(url)&&seen.add(body.sourceUrl()+"\n"+url)){
                            ticket.check();if(bytes>48L*1024*1024)throw new java.io.IOException("Topic image cache budget exceeded");
                            bytes+=ReaderImages.shared.load(url,body.sourceUrl(),ticket,()->foreground&&!closed&&request==epoch,8*1024*1024,true,item.url).length;
                        }
                    }
                }
            }catch(Exception failure){android.util.Log.d("NewTopicCache","media failed="+failure.getClass().getSimpleName());complete=false;}finally{media.release(ticket);}
            boolean success=complete;main.post(()->{if(closed||!foreground||request!=epoch)return;
                preparingMedia.remove(item.url);
                if(success&&ArticleSession.contentCurrent(doc)&&cachedVersion(item,doc)){new ArticleDiskCache(activity).pinTopic(doc);ready.put(key(item),doc);mediaCounts.put(key(item),mediaLimit);onReady.accept(item,doc);}
                changed.run();
                if(!closed&&foreground&&(!success||mediaLimit<limit)){main.removeCallbacks(start);main.postDelayed(start,success?0:3000);}
            });
        });
    }
    private void start(){
        if(closed||!foreground||interacting||items.isEmpty())return;
        // CookieManager access also initializes WebView. Respect the native
        // first-draw/provider barrier before even looking up cached articles.
        if(ReaderStartup.pending()){main.removeCallbacks(start);main.postDelayed(start,100);return;}
        loader.offer(new ArrayList<>(items.values()),100,false);
    }
    void resume(){foreground=true;main.post(start);}
    void interacting(boolean value){
        interacting=value;main.removeCallbacks(start);
        if(value)loader.finishWindow();
        else if(foreground&&!closed)main.postDelayed(start,500);
    }
    void pause(){foreground=false;epoch++;main.removeCallbacks(start);loader.pause();media.cancelPending();mediaWorker.getQueue().clear();preparingMedia.clear();verifying.clear();}
    @Override public void close(){pause();closed=true;loader.close();media.close();mediaWorker.shutdownNow();ready.clear();items.clear();}
}
