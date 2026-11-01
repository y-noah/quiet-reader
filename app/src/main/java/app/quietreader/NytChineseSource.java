package app.quietreader;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** Official Chinese daily most-viewed list and public Chinese article prose. */
final class NytChineseSource {
    private NytChineseSource(){}
    static boolean articleUrl(String url){
        if(!UrlPolicy.belongs(Source.NYT,url))return false;
        try{return java.net.URI.create(url).getPath().matches("/[a-z-]+/[0-9]{8}/[a-z0-9-]+/");}catch(Exception ignored){return false;}
    }
    static List<Item> list(String raw)throws JSONException{
        JSONObject root=new JSONObject(raw);if(root.optInt("code",-1)!=0)throw new IllegalStateException("纽约时报中文热门榜暂不可用");
        JSONArray daily=root.getJSONObject("list").getJSONArray("daily");List<Item> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int n=0;n<daily.length()&&n<100;n++){
            JSONObject item=daily.optJSONObject(n);if(item==null||!"article".equals(item.optString("type")))continue;
            String url=item.optString("url"),title=app.quietreader.discovery.HotCandidate.clean(item.optString("headline"),240);
            if(!articleUrl(url)||title.isEmpty()||!seen.add(url))continue;
            result.add(new Item(Source.NYT,title,url,"每日热门 #"+(n+1),false,app.quietreader.discovery.HotCandidate.clean(item.optString("summary"),600),null));
        }
        if(result.isEmpty())throw new IllegalStateException("纽约时报中文热门榜没有可用文章");return result;
    }
    static Document article(org.jsoup.nodes.Document page,String url){
        Document out=new Document();out.url=url;
        Element canonical=page.selectFirst("link[rel=canonical]"),header=page.selectFirst("article.article-content .article-header h1"),prose=page.selectFirst("article.article-content section.article-body");
        boolean identity=false;
        try{identity=canonical!=null&&articleUrl(url)&&articleUrl(canonical.attr("href"))&&java.net.URI.create(url).getPath().equals(java.net.URI.create(canonical.attr("href")).getPath());}catch(Exception ignored){}
        if(!identity||header==null||prose==null||prose.select(".article-body-item .article-paragraph").isEmpty()){
            out.sourceUnavailable=true;out.notice="尚未取得纽约时报中文文章正文，可在来源页检查访问状态";return out;
        }
        out.title=header.text();Element author=page.selectFirst("meta[name=byline]");if(author!=null)out.byline=author.attr("content");
        Element body=Jsoup.parse("",url).body().appendElement("article");
        Element hero=page.selectFirst("article.article-content > figure.article-span-photo");if(hero!=null)body.appendChild(hero.clone());
        for(Element paragraph:prose.select(".article-body-item .article-paragraph")){
            Element copy=paragraph.clone();copy.select("script,style,iframe,.big_ad,.article-body-aside").remove();
            if(copy.select("figure,img,video").isEmpty())copy.tagName("p");body.appendChild(copy);
        }
        SourceParser.appendRegion(out,body,"nyt-article","正文",false,url);
        if(out.blocks.isEmpty()){out.sourceUnavailable=true;out.notice="纽约时报中文正文暂不可用";}
        return out;
    }
}
