package app.quietreader;

import org.json.JSONObject;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** Positive media evidence only. Titles, thumbnails and mentions of video are not evidence. */
final class VideoPolicy {
    static final String EXCLUDED="aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container,.quote-content,blockquote,.WB_feed_expand";
    // Mobile Weibo renders its unplayed video as a poster/button without a <video> tag.
    static final String PLAYERS="video,.VideoCard,.VideoAnswer,.ZVideo,.video-player,.video_player,.WB_video,.card-video.type-video,.mwb-video,.tb-video,.j_video,[data-video-src],[data-video-id]";
    private VideoPolicy(){}
    static boolean metadata(JSONObject item){
        if(item==null)return false;
        for(String key:new String[]{"type","content_type","object_type","media_type","feed_type"})
            if(item.optString(key).toLowerCase(Locale.ROOT).matches("video|zvideo|video_answer|videoanswer|short_video|shortvideo"))return true;
        if("question".equals(item.optString("type")))return false;
        for(String key:new String[]{"is_video","isVideo"})if(item.optBoolean(key)||"1".equals(item.optString(key)))return true;
        return false; // Do not recursively treat a question's attachment as its content type.
    }
    static boolean url(Source source,String url){
        if(!UrlPolicy.belongs(source,url))return false;
        try {
            String path=java.net.URI.create(url).getPath();if(path==null)return false;
            switch(source){
                case ZHIHU:return path.matches("/zvideo/[^/]+/?");
                case WEIBO:return path.startsWith("/tv/show/")||path.startsWith("/tv/v/")||path.startsWith("/s/video/");
                case TIEBA:return path.startsWith("/video/");
                case TOUTIAO:return path.matches("/(video|short-video)/.*");
                case WALLSTREET:return path.startsWith("/videos/");
                default:return false;
            }
        }catch(Exception ignored){return false;}
    }
    static List<Item> filter(List<Item> items){
        List<Item> out=new ArrayList<>();for(Item item:items)if(!item.video&&!url(item.source,item.url))out.add(item);return out;
    }
    /** Both observed search surfaces contain independent posts, not one main post. */
    static boolean weiboTopic(String url){
        if(!UrlPolicy.belongs(Source.WEIBO,url))return false;
        try{
            java.net.URI uri=java.net.URI.create(url);String host=uri.getHost(),path=uri.getPath();
            return ("s.weibo.com".equalsIgnoreCase(host)&&("/weibo".equals(path)||"/weibo/".equals(path)))
                    ||("m.weibo.cn".equalsIgnoreCase(host)&&("/search".equals(path)||"/search/".equals(path)));
        }catch(Exception ignored){return false;}
    }
    static Element owned(Element element){Element clean=element.clone();clean.select(EXCLUDED).remove();return clean;}
    static boolean player(Element element){return element!=null&&!owned(element).select(PLAYERS).isEmpty();}
    static boolean typed(Element element){
        if(element==null)return false;
        for(String attr:new String[]{"data-type","data-content-type","data-media-type"})
            if(element.attr(attr).toLowerCase(Locale.ROOT).matches("video|zvideo|video_answer|short_video"))return true;
        try{if(metadata(new JSONObject(element.attr("data-zop"))))return true;}catch(Exception ignored){}
        return element.is(".VideoAnswer,.ZVideo,.video-post,.video-card");
    }
    static boolean row(Element row){return row!=null&&(typed(row)||player(row));}
    private static boolean mainMedia(Element main){
        Element clean=owned(main);clean.select(".reply-list-wrapper,.comment-content,.comment-list,.reply-list").remove();return row(clean);
    }
    static Element owner(Source source,Element root){
        String selector=source==Source.WEIBO?".card-wrap,.card,.WB_feed_type,[class*=detail_wbtext]":
                source==Source.ZHIHU?".AnswerItem":source==Source.TIEBA?".l_post,.comment-content,.pb-content-wrap":
                source==Source.HUPU?".reply-list-item,[class*='post-content_main-post-info']":"article";
        Element owner=root.closest(selector);return owner==null?root:owner;
    }
    static boolean videoSection(Source source,Element root){
        Element owner=owner(source,root);
        if(source==Source.HUPU&&owner.is("[class*='post-content_main-post-info']"))return mainMedia(owner);
        if(source==Source.TIEBA&&owner.is(".pb-content-wrap"))return mainMedia(owner);
        if(source==Source.TIEBA){
            // A floor owns its content, not a video posted in a nested reply.
            Element direct=owned(owner);direct.select(".lzl-wrapper,.pb-lzl-item").remove();
            return row(direct);
        }
        if(typed(owner))return true;
        if(source==Source.ZHIHU&&root.closest(".AnswerItem")!=null&&!owned(owner).select(".VideoAnswer,.ZVideo").isEmpty())return true;
        if(!player(owner))return false;
        if(source==Source.WEIBO||source==Source.TIEBA||source==Source.HUPU)return true;
        if(source==Source.ZHIHU&&root.closest(".AnswerItem")!=null){
            // Keep prose answers with an embedded clip; remove dedicated/empty video answers.
            Element prose=owned(root);prose.select(PLAYERS+",script,style").remove();
            return prose.text().replaceAll("视频加载中[.…。]*","").trim().isEmpty();
        }
        return false;
    }
    static boolean mainPost(Source source,Element page,String url){
        if(VideoPolicy.url(source,url))return true;
        // A question can have a video attachment and still carry useful text answers.
        if(source==Source.ZHIHU&&url.matches("https://[^/]*zhihu\\.com/question/\\d+.*"))return false;
        if(source==Source.WEIBO&&weiboTopic(url))return false;
        Element type=page.selectFirst("head meta[property=og:type]");
        if(type!=null&&type.attr("content").startsWith("video"))return true;
        if(source==Source.HUPU){
            for(Element main:page.select("[class*='post-content_main-post-info']"))
                if(main.closest(EXCLUDED)==null&&mainMedia(main))return true;
        }else if(source==Source.TIEBA){
            for(Element main:page.select(".pb-content-wrap"))
                if(main.closest(EXCLUDED+",.comment-content,.reply-list-wrapper,.l_post") == null&&mainMedia(main))return true;
            for(Element post:page.select(".l_post[data-field]")){
                if(post.closest(EXCLUDED)!=null)continue;
                try{JSONObject content=new JSONObject(post.attr("data-field")).optJSONObject("content");
                    if(content!=null&&content.optInt("post_no")==1&&mainMedia(post))return true;
                }catch(Exception ignored){}
            }
        }else if(source==Source.WEIBO){
            for(Element main:page.select(".weibo-text,[class*=detail_wbtext]"))if(main.closest(EXCLUDED)==null&&videoSection(source,main))return true;
        }
        return false;
    }
    static String filteredNotice(int count){return "已过滤 "+count+" 条视频内容，仅显示图文。";}
    static String key(Item item){
        try{
            java.net.URI uri=java.net.URI.create(item.url);String path=uri.getPath();
            if(item.source==Source.HUPU&&path.matches("/\\d+(?:-\\d+)?\\.html"))path=path.replaceFirst("-\\d+(?=\\.html$)","");
            else if(!(item.source==Source.TIEBA&&path.matches("/p/\\d+")))return item.source.name()+"|"+item.url;
            return item.source.name()+"|"+uri.getHost()+path;
        }catch(Exception ignored){return item.source.name()+"|"+item.url;}
    }
}
