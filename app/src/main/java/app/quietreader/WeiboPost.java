package app.quietreader;

import java.net.URI;
import java.util.regex.*;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Only observed mobile post routes may continue a truncated search-card body. */
final class WeiboPost {
    private WeiboPost(){}
    static String id(String url){
        if(!UrlPolicy.https(url)||!"m.weibo.cn".equals(UrlPolicy.host(url)))return "";
        try{
            Matcher m=Pattern.compile("^/(?:status|detail)/([A-Za-z0-9]{1,32})/?$").matcher(new URI(url).getRawPath());
            return m.matches()?m.group(1):"";
        }catch(Exception ignored){return "";}
    }
    static boolean same(String a,String b){String id=id(a);return !id.isEmpty()&&id.equals(id(b));}
    /** Only the enclosing post's observed author header; never mentions or repost prose. */
    static Element author(Element root){
        if(root.closest(VideoPolicy.EXCLUDED+",.weibo-rp")!=null)return null;
        Element owner=root.closest(".card-wrap,.card");if(owner==null)return null;
        for(Element candidate:owner.select("header h3.m-text-cut,.info a.name")){
            if(candidate.closest(".card-wrap,.card")!=owner||candidate.closest(VideoPolicy.EXCLUDED+",.weibo-rp,.weibo-text,.txt")!=null)continue;
            if(!candidate.text().trim().isEmpty())return candidate;
        }
        return null;
    }
    static boolean fulltextLabel(Element a){return a.text().trim().matches("(?:…|\\.\\.\\.)?(?:全文|展开全文)");}
    static String continuation(String page,Element root){
        if(!VideoPolicy.weiboTopic(page))return "";
        String found="";
        for(Element a:root.select("a[href]")){
            if(a.closest(VideoPolicy.EXCLUDED)!=null||!fulltextLabel(a))continue;
            String dest=UrlPolicy.normalize(page,a.attr("href"));
            if(id(dest).isEmpty())continue;
            if(!found.isEmpty()&&!same(found,dest))return "";
            found=dest;
        }
        return found;
    }
    static boolean canContinue(Source source,String current,Section part){
        return source==Source.WEIBO&&VideoPolicy.weiboTopic(current)&&!id(part.continuationUrl).isEmpty();
    }
}
