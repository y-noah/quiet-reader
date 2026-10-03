package app.quietreader;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** Additional aggregate sources: only observed public lists, never arbitrary page links. */
final class AdditionalSources {
    static boolean supports(Source s){return s==Source.GUOKR||s==Source.DOUBAN||s==Source.ITHOME||s==Source.IFANR||s==Source.JUEJIN||s==Source.SSPAI;}
    static List<Item> list(Source source,String raw)throws Exception{
        List<Item> out=new ArrayList<>();
        if(source==Source.DOUBAN){
            JSONArray items=new JSONObject(raw).getJSONArray("items");
            for(int n=0;n<items.length();n++){JSONObject item=items.getJSONObject(n);String id=item.optString("id");
                if(item.optBoolean("is_ad")||!item.optBoolean("is_public",true)||!id.matches("[1-9]\\d*"))continue;
                add(out,source,item.optString("title",item.optString("name")),"https://www.douban.com/gallery/topic/"+id+"/",item.optString("card_subtitle"),VideoPolicy.metadata(item));}
        }else if(source==Source.JUEJIN){
            JSONObject response=new JSONObject(raw);if(response.optInt("err_no",-1)!=0)throw new IllegalStateException("掘金未返回热榜");
            JSONArray items=response.getJSONArray("data");
            for(int n=0;n<items.length();n++){JSONObject item=items.getJSONObject(n).getJSONObject("content");String id=item.optString("content_id");
                if(!id.matches("[1-9]\\d*"))continue;
                add(out,source,item.optString("title"),"https://juejin.cn/post/"+id,source.category,VideoPolicy.metadata(item));}
        }else if(source==Source.SSPAI){
            JSONObject response=new JSONObject(raw);if(response.optInt("error",-1)!=0)throw new IllegalStateException("少数派未返回选文");
            JSONArray items=response.getJSONArray("data");
            for(int n=0;n<items.length();n++){JSONObject item=items.getJSONObject(n);String id=item.optString("id");
                if(!id.matches("[1-9]\\d*")||!item.optString("advertisement_url").isEmpty())continue;
                add(out,source,item.optString("title"),"https://sspai.com/post/"+id,source.category,VideoPolicy.metadata(item));}
        }else{
            org.jsoup.nodes.Document doc=Jsoup.parse(raw,source.endpoint);
            String selector=source==Source.ITHOME?"#d-1 li a[href]":source==Source.IFANR?".article-info h3 a[href],a.js-title-transform[href]":"a[href*=/article/]";
            for(Element a:doc.select(selector)){
                String url=UrlPolicy.normalize(source.endpoint,a.attr("href"));
                if(ReaderLinks.readerSource(url)!=source)continue;
                String title=a.hasAttr("title")?a.attr("title"):a.text();
                if(source==Source.GUOKR){Element heading=a.selectFirst("[class*=Title],h2,h3");if(heading!=null)title=heading.text();}
                Element row=a.closest("li,article,.article-info");
                add(out,source,title,url,source.category,VideoPolicy.row(row==null?a:row));
                if(out.size()>=30)break;
            }
        }
        if(out.isEmpty())throw new IllegalStateException("平台暂未返回公开列表");
        return VideoPolicy.filter(out);
    }
    private static void add(List<Item> out,Source source,String title,String url,String detail,boolean video){
        if(title.trim().isEmpty()||!UrlPolicy.belongs(source,url)||out.stream().anyMatch(i->i.url.equals(url)))return;
        out.add(new Item(source,Jsoup.parse(title).text(),url,detail,video));
    }
    static String doubanTopicId(String url){
        if(!UrlPolicy.belongs(Source.DOUBAN,url))return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("^/gallery/topic/(\\d+)/?$").matcher(java.net.URI.create(url).getPath());
        return m.matches()?m.group(1):"";
    }
    static Document doubanTopic(Item item,String raw)throws Exception{
        Document doc=new Document();doc.title=item.title;doc.url=item.url;
        JSONArray items=new JSONObject(raw).getJSONArray("items");Set<String> seen=new HashSet<>();
        for(int n=0;n<items.length();n++){
            JSONObject row=items.getJSONObject(n),target=row.optJSONObject("target");
            if(target==null||row.optBoolean("is_ad"))continue;
            if(VideoPolicy.metadata(target)||target.optJSONObject("video_info")!=null){doc.filteredVideos++;continue;}
            String title=target.optString("title");if(title.isEmpty())title=target.optString("abstract",row.optString("abstract"));
            title=Jsoup.parse(title).text();String url=UrlPolicy.normalize(item.url,target.optString("url"));
            if(title.isEmpty()||ReaderLinks.readerSource(url)!=Source.DOUBAN||!seen.add(url))continue;
            JSONObject author=target.optJSONObject("author");
            doc.related.add(new Item(Source.DOUBAN,title.length()>160?title.substring(0,160)+"…":title,url,author==null?"":author.optString("name")));
        }
        doc.notice="此处是话题当前返回的首批热门图文，不是全部讨论；点击条目阅读。部分原帖可能需要在来源页登录。";
        return doc;
    }
}
