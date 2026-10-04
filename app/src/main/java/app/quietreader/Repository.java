package app.quietreader;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;
import org.json.JSONArray;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import static app.quietreader.Models.*;

public final class Repository {
    public static final String UA="Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    public static final String DESKTOP_UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    public interface Result<T> { void success(T result); void failure(String reason); }
    private final RequestQueue requests=new RequestQueue();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Context context;
    private static final android.util.LruCache<String,Document> articles=new android.util.LruCache<String,Document>(2*1024*1024){
        @Override protected int sizeOf(String key,Document d){return DocumentSize.estimate(d);}
    };
    private static final android.util.LruCache<String,Long> articleTimes=new android.util.LruCache<>(60);
    public Document cachedArticle(Item i){if(!i.source.readable())return null;Long at=articleTimes.get(i.url);return at!=null&&(offline()||System.currentTimeMillis()-at<10*60*1000)?articles.get(i.url):null;}
    public boolean offline(){android.net.ConnectivityManager cm=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);return cm!=null&&cm.getActiveNetwork()==null;}
    public void cacheArticle(Document d){
        if(d.filteredVideo||d.loginRequired||d.sourceUnavailable){articles.remove(d.url);articleTimes.remove(d.url);return;}
        // A confirmed empty result must replace earlier unclassified video prose too.
        // Preserve the excluded IDs so reopening and later continuation cannot revive it.
        if(d.hasContent()||!d.related.isEmpty()||d.filteredVideos>0||!d.filteredSectionIds.isEmpty()){articles.put(d.url,d);articleTimes.put(d.url,System.currentTimeMillis());}
    }
    public boolean hiddenVideo(Item item){
        return item.video||VideoPolicy.url(item.source,item.url)||(!(item.source==Source.WEIBO&&VideoPolicy.weiboTopic(item.url))&&System.currentTimeMillis()-context.getSharedPreferences("video-filter",0).getLong(VideoPolicy.key(item),0)<7L*24*60*60*1000);
    }
    public List<Item> visibleItems(List<Item> items){List<Item> result=new java.util.ArrayList<>();for(Item item:items)if(item.source.readable()&&!hiddenVideo(item))result.add(item);return result;}
    public void rememberVideo(Item item){
        // A dynamic topic may gain text posts later; never persist a card's type as its topic.
        if(item.source==Source.WEIBO&&VideoPolicy.weiboTopic(item.url))return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("video-filter",0);
        android.content.SharedPreferences.Editor edit=prefs.edit().putLong(VideoPolicy.key(item),System.currentTimeMillis());
        java.util.List<java.util.Map.Entry<String,?>> entries=new java.util.ArrayList<>(prefs.getAll().entrySet());
        entries.sort(java.util.Comparator.comparingLong(e->e.getValue() instanceof Long?(Long)e.getValue():0));
        for(int n=0;n<entries.size()-255;n++)edit.remove(entries.get(n).getKey());edit.apply();
        articles.remove(item.url);articleTimes.remove(item.url);
        // Remove the row from the saved board too, without making its freshness look newer.
        android.content.SharedPreferences boards=context.getSharedPreferences("boards",0);
        try{List<Item> rows=Models.fromJson(new JSONArray(boards.getString(item.source.name(),"[]")));rows.removeIf(row->VideoPolicy.key(row).equals(VideoPolicy.key(item)));boards.edit().putString(item.source.name(),Models.toJson(rows).toString()).apply();}catch(Exception ignored){}
    }
    public void invalidateArticles(){articles.evictAll();articleTimes.evictAll();}
    public Repository(Context context) { this.context=context.getApplicationContext(); clearRetiredSources(); }
    private void clearRetiredSources() {
        android.content.SharedPreferences boards=context.getSharedPreferences("boards",0);
        android.content.SharedPreferences videos=context.getSharedPreferences("video-filter",0);
        android.content.SharedPreferences.Editor boardEdit=boards.edit(),videoEdit=videos.edit();
        for(Source source:Source.values())if(!source.readable()){
            String name=source.name();
            for(String key:boards.getAll().keySet())if(key.equals(name)||key.startsWith(name+"_"))boardEdit.remove(key);
            for(String key:videos.getAll().keySet())if(key.startsWith(name+"|"))videoEdit.remove(key);
        }
        boardEdit.apply();videoEdit.apply();
        context.getSharedPreferences("source-session",0).edit().remove("tieba-desktop").apply();
        android.content.SharedPreferences prefs=context.getSharedPreferences("MainActivity",0);
        try {
            List<Item> saved=Models.fromJson(new JSONArray(prefs.getString("saved","[]")));
            if(saved.removeIf(item->!item.source.readable()))prefs.edit().putString("saved",Models.toJson(saved).toString()).apply();
        }catch(Exception ignored){}
    }
    private boolean validBoardCache(Source s) {
        return s.readable();
    }
    public long cachedAt(Source s) { return validBoardCache(s)?context.getSharedPreferences("boards",0).getLong(s.name()+"_time",0):0; }
    public void cache(Source s,List<Item> items) {if(!s.readable())return;android.content.SharedPreferences.Editor edit=context.getSharedPreferences("boards",0).edit().putString(s.name(),Models.toJson(visibleItems(items)).toString()).putString(s.name()+"_endpoint",s.endpoint).putLong(s.name()+"_time",System.currentTimeMillis());edit.apply();}
    public List<Item> cached(Source s) {
        if(!validBoardCache(s))return java.util.Collections.emptyList();
        try { return visibleItems(Models.fromJson(new JSONArray(context.getSharedPreferences("boards",0).getString(s.name(),"[]")))); }
        catch(Exception e) { return java.util.Collections.emptyList(); }
    }
    public void board(Source s,Result<List<Item>> cb) {
        if(!s.readable()){cb.failure("此平台已移除");return;}
        String cookie=CookieManager.getInstance().getCookie(s.endpoint);
        requests.submit(ticket->{
            try {
                List<Item> items=SourceParser.list(s,fetch(s.endpoint,cookie,s.endpoint,ticket));
                ticket.check();
                cache(s,items);
                main.post(()->{if(!ticket.cancelled())cb.success(items);});
            } catch(Exception e) { main.post(()->{if(!ticket.cancelled())cb.failure(explain(e));}); }
        });
    }
    public void article(Item item,Result<Document> cb) {
        if(!item.source.readable()){cb.failure("此平台已移除");return;}
        String cookie=CookieManager.getInstance().getCookie(item.url);
        String doubanCookie=item.source==Source.DOUBAN?CookieManager.getInstance().getCookie("https://m.douban.com/"):null;
        requests.submit(ticket->{
            try {
                Document doc;
                String topicId=item.source==Source.DOUBAN?AdditionalSources.doubanTopicId(item.url):"";
                if(!topicId.isEmpty()){
                    String api="https://m.douban.com/rexxar/api/v2/gallery/topic/"+topicId+"/items?from_web=1&sort=hot&start=0&count=20&status_full_text=1&guest_only=0";
                    try {doc=AdditionalSources.doubanTopic(item,fetch(api,doubanCookie,item.url,ticket));}
                    catch(Exception failure){ticket.check();doc=new Document();doc.url=item.url;doc.title=item.title;doc.sourceUnavailable=true;doc.loginRequired=failure instanceof HttpFailure&&(((HttpFailure)failure).status==401||((HttpFailure)failure).status==403);doc.notice="豆瓣话题暂未加载成功。"+explain(failure)+"；可重试或打开来源页确认。";}
                }else if(item.source==Source.WALLSTREET&&item.url.matches("https://wallstreetcn.com/articles/\\d+")){
                    String id=item.url.substring(item.url.lastIndexOf('/')+1);
                    org.json.JSONObject data=new org.json.JSONObject(fetch("https://api-one-wscn.awtmt.com/apiv1/content/articles/"+id+"?extract=0",null,item.url,ticket)).getJSONObject("data");
                    doc=SourceParser.article(item.source,"<h1>"+ReaderHtml.escape(data.optString("title"))+"</h1><article>"+data.optString("content")+"</article>",item.url);
                    doc.byline=data.optString("source_name");
                    if(data.optBoolean("is_need_pay")||data.optBoolean("is_trial"))doc.notice="此文有付费或试读限制，仅展示接口实际返回的内容。请在来源页登录查看权限。";
                } else doc=SourceParser.article(item.source,fetch(item.url,cookie,item.url,ticket),item.url);
                ticket.check();cacheArticle(doc);Document result=doc;main.post(()->{if(!ticket.cancelled())cb.success(result);});
            }
            catch(Exception e) { main.post(()->{if(!ticket.cancelled())cb.failure(explain(e));}); }
        });
    }
    public static String explain(Exception e) {
        if(e instanceof HttpFailure&&(((HttpFailure)e).status==401||((HttpFailure)e).status==403))return "来源要求登录或限制了当前访问，请在来源页登录后重新读取";
        if(e instanceof java.net.SocketTimeoutException) return "连接超时；可重试，或在来源页登录后读取";
        if(e instanceof java.net.UnknownHostException) return "网络暂不可用，已保留缓存";
        if(e instanceof SecurityException) return "来源链接不符合安全规则";
        return "平台暂未返回可用内容；可能需要登录或更新适配";
    }
    public static String fetch(String url,String cookie,String referer) throws Exception {
        return new String(bytes(url,cookie,referer,4*1024*1024),StandardCharsets.UTF_8);
    }
    static final class HttpFailure extends java.io.IOException {
        final int status;
        HttpFailure(int status){super("HTTP "+status);this.status=status;}
    }
    static String fetch(String url,String cookie,String referer,RequestQueue.Ticket ticket)throws Exception{return new String(bytes(url,cookie,referer,4*1024*1024,ticket),StandardCharsets.UTF_8);}
    public static byte[] bytes(String url,String cookie,String referer,int limit) throws Exception {
        return bytes(url,cookie,referer,limit,null);
    }
    private static byte[] bytes(String url,String cookie,String referer,int limit,RequestQueue.Ticket ticket) throws Exception {
        String current=url;
        for(int redirect=0;redirect<5;redirect++) {
            if(ticket!=null)ticket.check();
            UrlPolicy.requirePublicHost(current);
            HttpURLConnection c=(HttpURLConnection)new URL(current).openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(5000); c.setReadTimeout(7000);
            c.setRequestProperty("User-Agent",DESKTOP_UA);
            c.setRequestProperty("Accept","text/html,application/json,image/*;q=0.8,*/*;q=0.5");
            if(referer!=null) c.setRequestProperty("Referer",referer);
            // Never forward a session cookie to a different host on a redirect.
            if(cookie!=null&&UrlPolicy.host(current).equals(UrlPolicy.host(url))) c.setRequestProperty("Cookie",cookie);
            try {
                if(ticket!=null){ticket.bind(c);ticket.check();}
                int code=c.getResponseCode();
                if(ticket!=null)ticket.check();
                if(code>=300&&code<400) { current=UrlPolicy.normalize(current,c.getHeaderField("Location")); continue; }
                if(code!=200) throw new HttpFailure(code);
                if(c.getContentLengthLong()>limit) throw new java.io.IOException("Response too large");
                try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    byte[] block=new byte[8192]; int size;
                    while((size=in.read(block))!=-1) { if(ticket!=null)ticket.check();if(out.size()+size>limit) throw new java.io.IOException("Response too large"); out.write(block,0,size); }
                    return out.toByteArray();
                }
            } finally { if(ticket!=null)ticket.unbind(c);c.disconnect(); }
        }
        throw new java.io.IOException("Too many redirects");
    }
    public void cancelPending(){requests.cancelPending();}
    public void close() { requests.close(); }
}
