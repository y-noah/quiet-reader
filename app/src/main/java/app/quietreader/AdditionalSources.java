package app.quietreader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** The observed IFANR article list; recommendations and unrelated links are excluded. */
final class AdditionalSources {
    static boolean supports(Source source){return source==Source.IFANR;}
    static List<Item> list(Source source,String raw)throws Exception{
        if(!supports(source))throw new IllegalArgumentException("不支持此榜单来源");
        List<Item> out=new ArrayList<>();
        for(Element a:Jsoup.parse(raw,source.endpoint).select(".article-info h3 a[href],a.js-title-transform[href]")){
            String url=UrlPolicy.normalize(source.endpoint,a.attr("href"));
            if(ReaderLinks.readerSource(url)!=source)continue;
            String title=Jsoup.parse(a.hasAttr("title")?a.attr("title"):a.text()).text().trim();
            if(title.isEmpty()||out.stream().anyMatch(i->i.url.equals(url)))continue;
            Element row=a.closest("li,article,.article-info");
            out.add(new Item(source,title,url,source.category,VideoPolicy.row(row==null?a:row)));
            if(out.size()>=30)break;
        }
        if(out.isEmpty())throw new IllegalStateException("平台暂未返回公开列表");
        return VideoPolicy.filter(out);
    }
}
