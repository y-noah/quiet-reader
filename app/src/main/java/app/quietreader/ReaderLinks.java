package app.quietreader;

import java.util.*;
import java.util.regex.*;
import static app.quietreader.Models.*;

/** Only escaped labels and ordinary web links enter the script-free reader. */
public final class ReaderLinks {
    private ReaderLinks() {}
    private static final Pattern URL=Pattern.compile("https?://[^\\s<>\"'\\p{IsHan}，。；！？、（）【】]+",Pattern.CASE_INSENSITIVE);
    public static boolean allowed(String url){return UrlPolicy.https(url)&&!UrlPolicy.host(url).equals("quiet-reader.invalid");}
    static Source readerSource(String url){
        if(!allowed(url))return null;
        String path=java.net.URI.create(url).getPath();
        for(Source source:Source.values())if(source.readable()&&UrlPolicy.belongs(source,url)){
            String pattern;
            switch(source){
                case SMZDM: pattern="/p/\\d+/?";break;
                case ZHIHU: pattern="/(?:question/\\d+(?:/answer/\\d+)?|answer/\\d+|p/\\d+)/?";break;
                case TIEBA: pattern="/p/\\d+/?";break;
                case HUPU: pattern="/\\d+(?:-\\d+)?\\.html";break;
                case WALLSTREET: pattern="/(?:articles|livenews)/\\d+/?";break;
                case CLS: pattern="/(?:detail|share/article)/\\d+/?";break;
                case GEEKPARK: pattern="/news/\\d+/?";break;
                case GUOKR: pattern="/article/\\d+/?";break;
                case DOUBAN: pattern="/(?:gallery/topic|topic|note|review|people/[^/]+/status)/\\d+/?";break;
                case ITHOME: pattern="/(?:\\d+/\\d+/\\d+|html/\\d+)\\.htm";break;
                case IFANR: pattern="/(?:app/)?\\d+/?";break;
                case JUEJIN: pattern="/post/\\d+/?";break;
                case SSPAI: pattern="/post/\\d+/?";break;
                case WEIBO: pattern="/(?:detail/[A-Za-z0-9]+|status/[A-Za-z0-9]+|\\d+/[A-Za-z0-9]+)/?";break;
                default: continue;
            }
            if(path.matches(pattern))return source;
        }
        return null; // Shopping redirects / login / unsupported pages belong in the chosen browser.
    }
    static String shortLabel(String url){
        String host=UrlPolicy.host(url);
        if(UrlPolicy.domain(host,"jd.com"))return "京东商品 ↗";
        if(UrlPolicy.domain(host,"smzdm.com"))return "值得买链接 ↗";
        if(UrlPolicy.domain(host,"taobao.com")||UrlPolicy.domain(host,"tmall.com"))return "淘宝 / 天猫 ↗";
        if(UrlPolicy.domain(host,"zhihu.com"))return "知乎链接 ↗";
        return host+" ↗";
    }
    private static String anchor(String url,String label){String text=label.matches("(?is)^https?://.*")?shortLabel(url):label;return "<a class='content-link' title='"+ReaderHtml.escape(url)+"' href='"+ReaderHtml.escape(url)+"'>"+ReaderHtml.escape(text)+"</a>";}
    static String plain(String text){
        Matcher m=URL.matcher(text);StringBuilder out=new StringBuilder();int at=0;
        while(m.find()){
            int end=m.end();while(end>m.start()&&".,;!?:)]}".indexOf(text.charAt(end-1))>=0)end--;
            String label=text.substring(m.start(),end),url=UrlPolicy.normalize("https://quiet-reader.invalid/",label);
            out.append(ReaderHtml.escape(text.substring(at,m.start())));
            out.append(allowed(url)?anchor(url,label):ReaderHtml.escape(label));at=end;
        }
        return out.append(ReaderHtml.escape(text.substring(at))).toString();
    }
    static String render(Block b){
        List<InlineLink> spans=new ArrayList<>(b.inlineLinks);
        for(InlineImage image:b.inlineImages)spans.add(new InlineLink(image.start,image.end,""));
        spans.sort(Comparator.comparingInt(s->s.start));StringBuilder out=new StringBuilder();int at=0;
        for(InlineLink span:spans){
            if(span.start<at||span.end<=span.start||span.end>b.value.length())continue;
            if(!span.url.isEmpty()&&!allowed(span.url))continue;
            InlineImage image=null;if(span.url.isEmpty())for(InlineImage i:b.inlineImages)if(i.start==span.start&&i.end==span.end&&UrlPolicy.https(i.url)){image=i;break;}
            if(span.url.isEmpty()&&image==null)continue;
            out.append(plain(b.value.substring(at,span.start)));
            if(image!=null)out.append("<img class='emoji' src='").append(ReaderHtml.escape(image.url)).append("' alt='").append(ReaderHtml.escape(image.alt)).append("'>");
            else out.append(anchor(span.url,b.value.substring(span.start,span.end)));
            at=span.end;
        }
        return out.append(plain(b.value.substring(at))).toString();
    }
}
