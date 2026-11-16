package app.quietreader;

import java.util.*;
import java.net.URI;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import static app.quietreader.Models.*;

/** Official Chinese engineering RSS, explicitly a recent-article feed, not a hot rank. */
final class InfoqSource {
    private InfoqSource(){}
    static String canonical(String url){
        if(!UrlPolicy.belongs(Source.INFOQ,url))return "";
        try{URI uri=new URI(url);String path=uri.getPath();return path!=null&&path.matches("/article/[A-Za-z0-9_-]+/?")?"https://www.infoq.cn"+path.replaceFirst("/$",""):"";}catch(Exception ignored){return "";}
    }
    static List<Item> list(String raw){
        List<Item> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Element row:Jsoup.parse(raw,"https://www.infoq.cn/",Parser.xmlParser()).select("rss > channel > item")){
            Element title=row.selectFirst("title"),link=row.selectFirst("link"),time=row.selectFirst("pubDate");
            if(title==null||link==null)continue;String url=canonical(link.text()),label=title.text().trim();
            if(url.isEmpty()||label.isEmpty()||!seen.add(url))continue;
            Long at=null;try{if(time!=null)at=java.time.ZonedDateTime.parse(time.text(),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli();}catch(Exception ignored){}
            result.add(new Item(Source.INFOQ,label,url,"最新技术文章 · 非热榜",false,"",at));
            if(result.size()==40)break;
        }
        if(result.isEmpty())throw new IllegalStateException("InfoQ暂未返回技术文章");return result;
    }
}
