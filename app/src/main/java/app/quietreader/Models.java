package app.quietreader;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class Models {
    private Models() {}
    public enum Source {
        WEIBO("微博", "热搜", WeiboBoard.PRIMARY, "https://passport.weibo.com/sso/signin?entry=wapsso&source=wapssowb&url=https%3A%2F%2Fm.weibo.cn%2F"),
        ZHIHU("知乎", "热榜", "https://www.zhihu.com/api/v3/feed/topstory/hot-list-web?limit=30&desktop=true", "https://www.zhihu.com/signin"),
        HUPU("虎扑", "热帖", "https://bbs.hupu.com/topic-daily-hot", "https://bbs.hupu.com/"),
        // Public web request checksum: MD5(SHA1("app=cailianpress&os=android&sv=835")); no account secret.
        CLS("财联社", "资讯热榜", "https://api3.cls.cn/v1/hot_list?app=cailianpress&os=android&sv=835&sign=e89e141e1391c13c7d2b99d8c142848c", "https://api3.cls.cn/quote/toplist?app=cailianpress&os=android&sv=835&tab=1"),
        IFANR("爱范儿", "最新文章·非热榜", "https://www.ifanr.com/", "https://www.ifanr.com/"),
        V2EX("V2EX", "最热", "https://www.v2ex.com/?tab=hot", "https://www.v2ex.com/signin"),
        ITHOME("IT之家", "日榜", "https://www.ithome.com/", "https://www.ithome.com/"),
        THEPAPER("澎湃新闻", "热榜", "https://cache.thepaper.cn/contentapi/wwwIndex/rightSidebar", "https://www.thepaper.cn/"),
        BBC("BBC中文", "热门阅读", "https://www.bbc.com/zhongwen/popular/read/simp", "https://www.bbc.com/zhongwen/simp"),
        BAIDU("百度", "热搜", "https://top.baidu.com/board?tab=realtime", "https://www.baidu.com/"),
        TIEBA("贴吧", "热议", TiebaHotBoard.ENDPOINT, "https://tieba.baidu.com/"),
        SSPAI("少数派", "首页文章·非全站热榜", "https://sspai.com/", "https://sspai.com/"),
        DOUYIN("抖音", "热榜", "https://www.douyin.com/hot", "https://www.douyin.com/"),
        BILIBILI("B站", "综合排行榜", "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all", "https://www.bilibili.com/"),
        XHS("小红书", "发现页点赞·非全站热榜", "https://www.xiaohongshu.com/explore", "https://www.xiaohongshu.com/explore");
        public boolean visible() { return this==ZHIHU||this==V2EX||this==CLS||this==HUPU||this==WEIBO; }
        public boolean readable(){return true;}
        public boolean webReading(){return this==BAIDU||this==TIEBA||this==DOUYIN||this==BILIBILI||this==XHS;}
        public boolean unifiedReading(){return !webReading()||StructuredSources.supports(this);}
        public String boardPage(){return this==THEPAPER?"https://www.thepaper.cn/":this==TIEBA?"https://tieba.baidu.com/":this==V2EX?"https://www.v2ex.com/?tab=hot":this==BILIBILI?"https://www.bilibili.com/v/popular/rank/all":this==WEIBO?"https://s.weibo.com/top/summary":endpoint;}
        public static Source[] displayOrder(){return new Source[]{ZHIHU,WEIBO,HUPU,CLS,V2EX};}
        public final String label, category, endpoint, login;
        Source(String label, String category, String endpoint, String login) {
            this.label=label; this.category=category; this.endpoint=endpoint; this.login=login;
        }
    }
    public static final class Item {
        public final Source source;
        public final String title, url, detail;
        public final String snippet;
        public final String sourceTag;
        final String selectedTopicPost;
        public final Long publishedAt;
        public final boolean video;
        public Item(Source source, String title, String url, String detail) {
            this(source,title,url,detail,false);
        }
        public Item(Source source,String title,String url,String detail,boolean video){
            this(source,title,url,detail,video,"",null);
        }
        public Item(Source source,String title,String url,String detail,boolean video,String snippet,Long publishedAt){
            this(source,title,url,detail,video,snippet,publishedAt,"");
        }
        public Item(Source source,String title,String url,String detail,boolean video,String snippet,Long publishedAt,String sourceTag){
            this(source,title,url,detail,video,snippet,publishedAt,sourceTag,"");
        }
        Item(Source source,String title,String url,String detail,boolean video,String snippet,Long publishedAt,String sourceTag,String selectedTopicPost){
            this.source=source;this.title=title;this.url=url;this.detail=detail;this.video=video;this.snippet=snippet==null?"":snippet;this.publishedAt=publishedAt;
            this.sourceTag=sourceTag==null?"":sourceTag;
            if(!selectedTopicPost.isEmpty()&&(source!=Source.TIEBA||!TiebaTopic.isTopic(url)||!selectedTopicPost.equals(TiebaTopic.postUrl(url,selectedTopicPost))))throw new IllegalArgumentException("Invalid topic post request");
            this.selectedTopicPost=selectedTopicPost;
        }
        public JSONObject json() {
            JSONObject o=new JSONObject();
            try { o.put("source", source.name()); o.put("title", title); o.put("url", url); o.put("detail", detail);o.put("video",video);if(!sourceTag.isEmpty())o.put("sourceTag",sourceTag);if(!snippet.isEmpty())o.put("snippet",snippet);if(publishedAt!=null)o.put("publishedAt",publishedAt); } catch(Exception ignored) {}
            if(!selectedTopicPost.isEmpty())try{o.put("selectedTopicPost",selectedTopicPost);}catch(Exception ignored){}
            return o;
        }
        public static Item from(JSONObject o) {
            return new Item(Source.valueOf(o.optString("source")), o.optString("title"), o.optString("url"), o.optString("detail"),o.optBoolean("video"),o.optString("snippet"),o.has("publishedAt")&&!o.isNull("publishedAt")?o.optLong("publishedAt"):null,o.optString("sourceTag"),o.optString("selectedTopicPost"));
        }
    }
    public static final class Block {
        public final String type, value;
        public final List<InlineImage> inlineImages;
        public final List<InlineLink> inlineLinks;
        public int imageWidth,imageHeight;
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
    public static final class VideoAttachment {
        public final String url,poster;
        public VideoAttachment(String url,String poster){this.url=url;this.poster=poster;}
    }
    public static final class Document {
        // url remains the requested/cache identity; a resolved topic also records
        // the exact post whose body is displayed, without changing recommendation identity.
        TiebaTopic.Resolved topic;
        public String sourceUrl(){return topic==null?url:topic.postUrl;}
        public final List<VideoAttachment> videos=new ArrayList<>();
        long cachedAt;
        String cachedSession, cachedNamespace;
        String metadataHash="";
        long sourceModifiedAt;
        String sourceUpdateEvidence="";
        int discussionPage;
        boolean virtualDiscussion;
        // Newly acquired documents use the current Tieba structure. Legacy JSON
        // must explicitly decode to revision 0 rather than being relabeled.
        int tiebaReplyRevision=TiebaReplyRelations.REVISION;
        int hupuBodyRevision=HupuDiscussion.BODY_REVISION;
        public String title="", byline="", url="", notice="", nextUrl="",moreStatus="",videoPoster="",videoUrl="";
        public boolean unsupportedVideo;
        public boolean videoTopic;
        public boolean filteredVideo;
        public boolean loginRequired;
        public boolean sourceUnavailable;
        public int filteredVideos;
        public final java.util.Set<String> filteredSectionIds=new java.util.HashSet<>();
        public final List<Block> blocks=new ArrayList<>();
        public final List<Item> related=new ArrayList<>();
        public final List<Section> sections=new ArrayList<>();
        public boolean hasContent() { return blocks.stream().anyMatch(b->!b.type.equals("video")&&!b.type.equals("separator")); }
        public boolean canPresent() { return hasContent()||!related.isEmpty()||unsupportedVideo||filteredVideo||filteredVideos>0||loginRequired||sourceUnavailable; }
        public boolean containsImage(String url) {
            for(VideoAttachment video:videos)if(!video.poster.isEmpty()&&video.poster.equals(url))return true;
            if(!videoPoster.isEmpty()&&videoPoster.equals(url))return true;
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
        public String sourcePostId="";
        String sourceChildId="";
        long sourceReplyAt;
        String sourceReplyEvidence="";
        public boolean nestedReply;
        public String parentSectionId="",replyToAuthor="";
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
