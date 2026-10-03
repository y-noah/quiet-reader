package app.quietreader;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}
    public enum Source {
        WEIBO("微博", "热搜", "https://weibo.com/ajax/side/hotSearch", "https://passport.weibo.com/sso/signin?entry=wapsso&source=wapssowb&url=https%3A%2F%2Fm.weibo.cn%2F"),
        ZHIHU("知乎", "热榜", "https://www.zhihu.com/api/v3/feed/topstory/hot-list-web?limit=30&desktop=true", "https://www.zhihu.com/signin"),
        TOUTIAO("头条", "热榜", "https://www.toutiao.com/hot-event/hot-board/?origin=toutiao_pc", "https://www.toutiao.com/"),
        TIEBA("贴吧", "热议", "https://tieba.baidu.com/hottopic/browse/topicList", "https://tieba.baidu.com/"),
        HUPU("虎扑", "热帖", "https://bbs.hupu.com/topic-daily-hot", "https://bbs.hupu.com/"),
        WALLSTREET("华尔街见闻", "日间最热", "https://api-one-wscn.awtmt.com/apiv1/content/articles/hot?period=all", "https://wallstreetcn.com/"),
        HACKERNEWS("Hacker News", "热门讨论", "https://news.ycombinator.com/", "https://news.ycombinator.com/login"),
        SMZDM("什么值得买", "3小时最热·公开榜", "https://faxian.smzdm.com/h2s0t0f0c0p1/", "https://faxian.smzdm.com/h2s0t0f0c0p1/"),
        // Public web request checksum: MD5(SHA1("app=cailianpress&os=android&sv=835")); no account secret.
        CLS("财联社", "资讯热榜", "https://api3.cls.cn/v1/hot_list?app=cailianpress&os=android&sv=835&sign=e89e141e1391c13c7d2b99d8c142848c", "https://api3.cls.cn/quote/toplist?app=cailianpress&os=android&sv=835&tab=1"),
        GEEKPARK("极客公园", "七日热门", "https://mainssl.geekpark.net/api/v1/posts/hot_in_week?per=7", "https://www.geekpark.net/tags/AI"),
        GUOKR("果壳", "首页精选·非热榜", "https://www.guokr.com/", "https://www.guokr.com/"),
        DOUBAN("豆瓣", "热门话题", "https://m.douban.com/rexxar/api/v2/gallery/web_hot_topics", "https://www.douban.com/gallery/"),
        ITHOME("IT之家", "日榜", "https://www.ithome.com/block/rank.html", "https://www.ithome.com/"),
        IFANR("爱范儿", "最新文章·非热榜", "https://www.ifanr.com/", "https://www.ifanr.com/"),
        JUEJIN("掘金", "热榜", "https://api.juejin.cn/content_api/v1/content/article_rank?category_id=1&type=hot", "https://juejin.cn/hot/articles"),
        SSPAI("少数派", "热门选文", "https://sspai.com/api/v1/article/tag/page/get?limit=30&offset=0&tag=%E7%83%AD%E9%97%A8%E6%96%87%E7%AB%A0", "https://sspai.com/"),
        AGGREGATE("综合", "Top100", "", "");
        public boolean visible() { return this==ZHIHU||this==IFANR||this==CLS||this==SMZDM||this==HUPU||this==WEIBO; }
        public boolean readable(){return this!=AGGREGATE&&this!=TOUTIAO&&this!=HACKERNEWS&&this!=TIEBA;}
        public boolean navigable(){return this==AGGREGATE||visible();}
        public static Source[] displayOrder(){return new Source[]{ZHIHU,IFANR,CLS,SMZDM,HUPU,WEIBO};}
        public static Source[] navigationOrder(){return new Source[]{AGGREGATE,ZHIHU,IFANR,CLS,SMZDM,HUPU,WEIBO};}
        public static Source[] aggregateSources(){return new Source[]{ZHIHU,SMZDM,WEIBO,HUPU,GUOKR,GEEKPARK,DOUBAN,ITHOME,IFANR,JUEJIN,WALLSTREET,CLS,SSPAI};}
        public String referer(){return this==DOUBAN?login:endpoint;}
        public final String label, category, endpoint, login;
        Source(String label, String category, String endpoint, String login) {
            this.label=label; this.category=category; this.endpoint=endpoint; this.login=login;
        }
    }
    public static final class Item {
        public final Source source;
        public final String title, url, detail;
        public final boolean video;
        public Item(Source source, String title, String url, String detail) {
            this(source,title,url,detail,false);
        }
        public Item(Source source,String title,String url,String detail,boolean video){
            this.source=source;this.title=title;this.url=url;this.detail=detail;this.video=video;
        }
        public JSONObject json() {
            JSONObject o=new JSONObject();
            try { o.put("source", source.name()); o.put("title", title); o.put("url", url); o.put("detail", detail);o.put("video",video); } catch(Exception ignored) {}
            return o;
        }
        public static Item from(JSONObject o) {
            return new Item(Source.valueOf(o.optString("source")), o.optString("title"), o.optString("url"), o.optString("detail"),o.optBoolean("video"));
        }
    }
    public static final class Block {
        public final String type, value;
        public final List<InlineImage> inlineImages;
        public final List<InlineLink> inlineLinks;
        public Block(String type, String value) { this(type,value,java.util.Collections.emptyList()); }
        public Block(String type,String value,List<InlineImage> images) {
            this(type,value,images,java.util.Collections.emptyList());
        }
        public Block(String type,String value,List<InlineImage> images,List<InlineLink> links) {
            this.type=type;this.value=value;
            this.inlineImages=java.util.Collections.unmodifiableList(new ArrayList<>(images));
            this.inlineLinks=java.util.Collections.unmodifiableList(new ArrayList<>(links));
        }
    }
    public static final class InlineLink {
        public final int start,end;public final String url;
        public InlineLink(int start,int end,String url){this.start=start;this.end=end;this.url=url;}
    }
    /** UTF-16 ranges replace accessible placeholders in plain paragraph text. Never raw HTML. */
    public static final class InlineImage {
        public final int start,end;
        public final String url,alt;
        public InlineImage(int start,int end,String url,String alt) {
            this.start=start;this.end=end;this.url=url;this.alt=alt;
        }
    }
    public static final class Document {
        public String title="", byline="", url="", notice="", nextUrl="",moreStatus="";
        public boolean unsupportedVideo;
        public boolean filteredVideo;
        public boolean loginRequired;
        public int filteredVideos;
        public final java.util.Set<String> filteredSectionIds=new java.util.HashSet<>();
        public final List<Block> blocks=new ArrayList<>();
        public final List<Item> related=new ArrayList<>();
        public final List<Section> sections=new ArrayList<>();
        public boolean hasContent() { return !blocks.isEmpty(); }
        public boolean canPresent() { return hasContent()||!related.isEmpty()||unsupportedVideo||filteredVideo||filteredVideos>0; }
        public boolean containsImage(String url) {
            for(Block b:blocks) {
                if(b.type.equals("image")&&b.value.equals(url))return true;
                for(InlineImage i:b.inlineImages)if(i.url.equals(url))return true;
            }
            return false;
        }
    }
    public static final class Section {
        public final String id,label;
        public final boolean answer;
        public String continuationUrl="";
        public final List<Block> blocks=new ArrayList<>();
        public Section(String id,String label,boolean answer) { this.id=id;this.label=label;this.answer=answer; }
    }
    public static JSONArray toJson(List<Item> items) {
        JSONArray a=new JSONArray(); for(Item item:items) a.put(item.json()); return a;
    }
    public static List<Item> fromJson(JSONArray a) {
        List<Item> items=new ArrayList<>();
        for(int i=0;i<a.length();i++) { try { items.add(Item.from(a.getJSONObject(i))); } catch(Exception ignored) {} }
        return items;
    }
}
