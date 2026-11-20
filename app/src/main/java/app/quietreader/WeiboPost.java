package app.quietreader;

import java.net.URI;
import java.util.regex.*;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Only observed mobile post routes may continue a truncated search-card body. */
final class WeiboPost {
    private WeiboPost(){}
    static final String VISITOR_NOTICE="微博要求完成访客验证或登录。请打开来源页确认后重新读取。";
    static final class VisitorRequired extends java.io.IOException {}
    static String searchPeer(String url){
        if(!VideoPolicy.weiboTopic(url))return "";
        return "s.weibo.com".equals(UrlPolicy.host(url))?"https://m.weibo.cn/search":"https://s.weibo.com/weibo";
    }
    static boolean sameSearchCookiePath(String first,String second){
        return VideoPolicy.weiboTopic(first)&&VideoPolicy.weiboTopic(second)
                &&UrlPolicy.host(first).equals(UrlPolicy.host(second))
                &&URI.create(first).getRawPath().equals(URI.create(second).getRawPath());
    }
    static boolean gateRedirect(String original,String destination){
        return VideoPolicy.weiboTopic(original)&&(visitor(destination)||signIn(destination));
    }
    static Document gateDocument(String url){
        Document result=new Document();result.url=url;result.loginRequired=true;result.notice=VISITOR_NOTICE;return result;
    }
    static boolean signIn(String url){
        if(!UrlPolicy.belongs(Source.WEIBO,url))return false;
        String host=UrlPolicy.host(url),path=URI.create(url).getPath();
        return "passport.weibo.com".equals(host)&&"/sso/v2/login".equals(path)
                ||"passport.weibo.cn".equals(host)&&"/signin/login".equals(path);
    }
    static boolean visitor(String url){
        if(!UrlPolicy.belongs(Source.WEIBO,url))return false;
        String host=UrlPolicy.host(url),path=URI.create(url).getPath();
        return "/visitor/visitor".equals(path)&&("passport.weibo.com".equals(host)||"visitor.passport.weibo.cn".equals(host));
    }
    static boolean accessGate(String url,org.jsoup.nodes.Document page){
        return UrlPolicy.belongs(Source.WEIBO,url)&&(signIn(url)||"Sina Visitor System".equals(page.title())
                &&page.selectFirst("head script[src^=/js/visitor/mini_original.js]")!=null);
    }
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
