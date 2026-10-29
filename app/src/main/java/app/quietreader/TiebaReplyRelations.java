package app.quietreader;

import java.net.URI;
import java.util.*;
import java.util.regex.*;
import org.jsoup.nodes.*;
import static app.quietreader.Models.*;

/** Extract only a leading, source-linked reply target, never infer one from order. */
final class TiebaReplyRelations {
    static final int REVISION=4;
    private TiebaReplyRelations(){}
    static boolean canReuse(Models.Document document,boolean offline){
        return offline||!UrlPolicy.belongs(Source.TIEBA,document.sourceUrl())||document.tiebaReplyRevision==REVISION;
    }
    static String extractTarget(Element body,String base){
        Element link=body.selectFirst("a[href]");if(link==null)return "";
        String name=link.text().trim(),url=UrlPolicy.normalize(base,link.attr("href"));
        if(name.isEmpty()||name.length()>64||!UrlPolicy.belongs(Source.TIEBA,url))return "";
        try{if(!URI.create(url).getPath().matches("/home/main/?"))return "";}catch(Exception invalid){return "";}
        List<TextNode> nodes=new ArrayList<>();collect(body,nodes);
        StringBuilder text=new StringBuilder();int beforeLink=0;boolean found=false;
        for(TextNode node:nodes){
            if(!found&&inside(node,link))found=true;
            if(!found)beforeLink+=node.getWholeText().length();
            text.append(node.getWholeText());
        }
        if(!found||!text.substring(0,beforeLink).matches("\\s*回复\\s*"))return "";
        Matcher prefix=Pattern.compile("^\\s*回复\\s*"+Pattern.quote(name)+"\\s*[:：]\\s*").matcher(text);
        if(!prefix.find())return "";
        int remaining=prefix.end();
        for(TextNode node:nodes){if(remaining==0)break;String value=node.getWholeText();int count=Math.min(remaining,value.length());node.text(value.substring(count));remaining-=count;}
        // The now-empty profile anchor and wrappers cannot become large paragraphs.
        link.remove();return name;
    }
    private static void collect(Node node,List<TextNode> into){
        if(node instanceof TextNode){into.add((TextNode)node);return;}
        for(Node child:node.childNodes())collect(child,into);
    }
    private static boolean inside(Node node,Element ancestor){for(Node p=node.parent();p!=null;p=p.parent())if(p==ancestor)return true;return false;}
}
