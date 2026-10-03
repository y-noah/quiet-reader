package app.quietreader;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import java.util.*;
import static app.quietreader.Models.*;

/** Three concurrent requests, one overall deadline; all state/cache publication is on main. */
final class AggregateLoader implements AutoCloseable {
    interface Listener {void changed(List<AggregateRanker.Entry> entries,String summary,String detail);}
    private final RequestQueue requests=new RequestQueue();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Repository repo;
    private final Map<Source,AggregateRanker.Feed> feeds=new EnumMap<>(Source.class);
    private final Map<Source,String> states=new EnumMap<>(Source.class);
    private final Set<Source> pending=EnumSet.noneOf(Source.class);
    private int generation; private Runnable deadline; private Listener listener;
    AggregateLoader(Repository repo){this.repo=repo;}
    void load(boolean refresh,Listener listener){
        cancel();this.listener=listener;feeds.clear();states.clear();pending.clear();
        int run=generation;long now=System.currentTimeMillis();
        Map<Source,String> cookies=new EnumMap<>(Source.class);
        for(Source source:Source.aggregateSources()){
            long at=repo.cachedAt(source);List<Item> cached=repo.cached(source);
            boolean usable=at>0&&now-at<=AggregateRanker.MAX_AGE&&!cached.isEmpty();
            if(usable)feeds.put(source,new AggregateRanker.Feed(source,cached,at));
            if(repo.offline())states.put(source,usable?"离线 · 缓存":"离线 · 无可用缓存");
            else if(!refresh&&usable&&now-at<15*60*1000)states.put(source,"缓存 · 15分钟内");
            else {pending.add(source);states.put(source,usable?"更新中 · 先用缓存":"连接中");cookies.put(source,CookieManager.getInstance().getCookie(source.endpoint));}
        }
        publish();
        if(pending.isEmpty())return;
        deadline=()->{if(run!=generation)return;for(Source s:pending)states.put(s,feeds.containsKey(s)?"本轮超时 · 保留缓存":"本轮超时 · 暂未参与");pending.clear();requests.cancelPending();publish();};
        main.postDelayed(deadline,22000);
        for(Source source:new ArrayList<>(pending))requests.submitConcurrent(ticket->{
            try{
                List<Item> items=SourceParser.list(source,Repository.fetch(source.endpoint,cookies.get(source),source.referer(),ticket));ticket.check();
                main.post(()->{if(run!=generation||!pending.remove(source))return;
                    List<Item> visible=repo.visibleItems(items);repo.cache(source,visible);
                    if(visible.isEmpty()){feeds.remove(source);states.put(source,"已返回 · 无图文条目");}
                    else{feeds.put(source,new AggregateRanker.Feed(source,visible,System.currentTimeMillis()));states.put(source,"已更新 · "+visible.size()+"条");}
                    finish();});
            }catch(Exception failure){main.post(()->{if(run!=generation||!pending.remove(source))return;states.put(source,feeds.containsKey(source)?"获取失败 · 保留缓存":"获取失败 · 暂未参与");finish();});}
        });
    }
    private void finish(){if(pending.isEmpty()&&deadline!=null)main.removeCallbacks(deadline);publish();}
    private void publish(){
        long now=System.currentTimeMillis();List<AggregateRanker.Entry> entries=AggregateRanker.rank(new ArrayList<>(feeds.values()),now);
        boolean hasCached=feeds.keySet().stream().anyMatch(s->states.getOrDefault(s,"").contains("缓存"));
        String summary=feeds.size()+"/"+Source.aggregateSources().length+" 来源"+(hasCached?" · 含缓存":"")+(pending.isEmpty()?"":" · "+pending.size()+"个获取中");
        StringBuilder detail=new StringBuilder("这是按个人阅读偏好编排的综合 Top100，不是精确的全网热度排行榜。综合热点优先，垂直内容补充。权重是推荐优先级，不是平台用户量或真实热度的测量值。\n\n");
        for(Source source:Source.aggregateSources()){
            detail.append(source.label).append(" · ").append(source.category).append("\n").append(states.get(source));
            AggregateRanker.Feed feed=feeds.get(source);if(feed!=null)detail.append(" · 快照 ").append(new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(feed.fetchedAt)));
            detail.append("\n推荐权重 ").append(AggregateRanker.weight(source)).append(" · 最多主导 ").append(AggregateRanker.sourceLimit(source)).append("条\n\n");
        }
        detail.append("排序：1100 / (10 + 榜内名次) × 推荐权重 × 缓存新鲜度。同完整标题跨平台出现，取最高分加其他各平台分值的40%。最多100条；不足时显示实际数量，不放宽单源上限凑数。知乎/微博优先，豆瓣/虎扑及财经随后，科技、精选作补充；垂直平台高位仍可能超过综合平台的低位或旧缓存，并非全部固定排在末尾。果壳首页精选和爱范儿最新文章不是热榜。\n\n仅合并忽略普通标点、空格、大小写后相同的完整标题，保留小数与数值符号区别；相近措辞可能仍重复，不作语义事件聚类。\n\n缓存年龄按15分钟一档，每12小时分值减半，超过24小时不参与。快照时间是获取时间，不是文章发表时间；不把请求早晚几秒作为同档内容的排序依据。来源失败会标出缓存或未参与，不编造。刷新在右上角菜单。");
        if(listener!=null)listener.changed(entries,summary,detail.toString());
    }
    void cancel(){generation++;if(deadline!=null)main.removeCallbacks(deadline);requests.cancelPending();listener=null;pending.clear();}
    @Override public void close(){cancel();requests.close();}
}
