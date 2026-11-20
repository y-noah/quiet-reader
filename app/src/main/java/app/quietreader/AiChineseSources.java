package app.quietreader;

import java.net.URI;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Public Chinese AI feeds: Qbit's observed hot rail and Leiphone's AI-only news list. */
final class AiChineseSources {
    private AiChineseSources(){}
    static String canonical(Source source,String url){
        if(!UrlPolicy.belongs(source,url))return "";
        try{String path=new URI(url).getPath();
            if(source==Source.QBITAI&&path.matches("/20[0-9]{2}/(?:0[1-9]|1[0-2])/[1-9][0-9]*\\.html"))return "https://www.qbitai.com"+path;
            if(source==Source.LEIPHONE_AI&&path.matches("/category/(?:ai|academic|yanxishe)/[A-Za-z0-9]{8,40}\\.html"))return "https://www.leiphone.com"+path;
        }catch(Exception ignored){}return "";
    }
    static List<Item> list(Source source,String html){
        org.jsoup.nodes.Document page=Jsoup.parse(html,source.endpoint);List<Item> items=new ArrayList<>();Set<String> seen=new HashSet<>();
        String selector=source==Source.QBITAI?".content_right .yaowen .picture_text a[href]":".list li .word h3 a.headTit[href]";
        for(Element link:page.select(selector)){
            Element heading=link.selectFirst("h4");
            String url=canonical(source,UrlPolicy.normalize(source.endpoint,link.attr("href"))),title=(heading==null?link:heading).text().trim();
            if(url.isEmpty()||title.isEmpty()||!seen.add(url)||link.closest(".advertisement,.ad,[data-ad]")!=null)continue;
            String snippet="";if(source==Source.LEIPHONE_AI){Element card=link.closest(".word");Element description=card==null?null:card.selectFirst(".des");if(description!=null)snippet=description.text();}
            items.add(new Item(source,title,url,source==Source.QBITAI?"热门文章":"AI最新文章 · 非热榜",false,snippet,null));
            if(items.size()==40)break;
        }
        if(items.isEmpty())throw new IllegalStateException(source.label+"暂未返回可读取的AI文章");return items;
    }
    static void prepare(Source source,Element page,String url){
        String selector=source==Source.QBITAI?".article":".lphArticle-detail .lph-article-comView";
        for(Element body:page.select(selector)){
            body.select(".xiangguan,.person_box,.share_pc,.realted-article,.recmdArticle,.pageActive").remove();
            if(source==Source.QBITAI)body.select("h1,.article_info,.zhaiyao").remove();
            if(source==Source.LEIPHONE_AI)body.select("a[href*='vip.yanxishe.com/?from=leiphonearticle']").remove();
            for(Element image:body.select("img[data-src],img[data-original]"))for(String attr:List.of("data-src","data-original")){
                String actual=UrlPolicy.normalize(url,image.attr(attr));if(!actual.isEmpty()){image.attr("src",actual);break;}
            }
        }
    }
}
