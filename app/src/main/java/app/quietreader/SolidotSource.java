package app.quietreader;

import java.net.URI;
import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** The site's explicit popular-article rail; excludes pinned promotions and comments. */
final class SolidotSource {
    private SolidotSource(){}
    static boolean articleUrl(String url){
        if(!UrlPolicy.belongs(Source.SOLIDOT,url))return false;
        try{URI uri=new URI(url);return "/story".equals(uri.getPath())&&uri.getRawQuery()!=null&&uri.getRawQuery().matches("sid=[1-9][0-9]*")&&uri.getFragment()==null;}catch(Exception ignored){return false;}
    }
    static List<Item> list(String raw){
        List<Item> out=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Element block:Jsoup.parse(raw,Source.SOLIDOT.endpoint).select(".block_r")){
            Element heading=block.selectFirst(".rig_tittle h2");
            if(heading==null||!heading.text().equals("热门文章"))continue;
            for(Element link:block.select(".contentbox a[href]")){
                String url=UrlPolicy.normalize(Source.SOLIDOT.endpoint,link.attr("href")),title=link.text().trim();
                if(articleUrl(url)&&!title.isEmpty()&&seen.add(url))out.add(new Item(Source.SOLIDOT,title,url,"热门文章 #"+(out.size()+1)));
                if(out.size()==30)break;
            }
        }
        if(out.isEmpty())throw new IllegalStateException("奇客暂未返回热门文章");
        return out;
    }
}
