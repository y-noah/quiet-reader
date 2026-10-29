package app.quietreader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** Observed public homepage day ranking only; never homepage latest or software ads. */
final class IthomeSource {
    private IthomeSource(){}
    static List<Item> list(String html){
        org.jsoup.nodes.Document page=Jsoup.parse(html,Source.ITHOME.endpoint);List<Item> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
        Element rank=page.selectFirst("div#rank");
        if(rank==null||rank.select("ul.bar > li").stream().noneMatch(tab->tab.attr("data-id").equals("1")&&tab.text().equals("日榜")))throw new IllegalStateException("IT之家日榜结构暂不可用");
        Element day=rank.selectFirst("ul.order[id=d-1]");if(day==null)throw new IllegalStateException("IT之家未返回日榜");
        for(Element link:day.select("li > a[href]")){
            String url=UrlPolicy.normalize(Source.ITHOME.endpoint,link.attr("href")),title=app.quietreader.discovery.HotCandidate.clean(link.text(),240);
            if(!articleUrl(url)||title.isEmpty()||!seen.add(url))continue;
            rows.add(new Item(Source.ITHOME,title,url,"日榜 #"+(rows.size()+1),false,"",null,""));if(rows.size()==100)break;
        }
        if(rows.isEmpty())throw new IllegalStateException("IT之家未返回有效日榜条目");return rows;
    }
    static boolean articleUrl(String url){
        if(!UrlPolicy.belongs(Source.ITHOME,url))return false;
        try{return (UrlPolicy.host(url).equals("www.ithome.com")||UrlPolicy.host(url).equals("ithome.com"))&&java.net.URI.create(url).getPath().matches("/\\d+/\\d{3}/\\d{3}\\.htm");}catch(Exception ignored){return false;}
    }
}
