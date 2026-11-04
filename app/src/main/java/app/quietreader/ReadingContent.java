package app.quietreader;

import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.nodes.Comment;

/** Remove source navigation modules without stripping links cited in the article. */
final class ReadingContent {
    private ReadingContent(){}
    static void prune(Element root){
        root.select(".recommend,.recommend-list,.recommendation,.recommendations,.related-recommend,.related-news,.related-articles,.related-article-box,.related-article-content-box,.recommend-box,.recommend-content,.recommend-container,.recommend-wrap,.recommend-wrapper,#recommend,#recommendation,[data-module=recommend],[data-module=recommendation],.advertisement,.feed-ad-container").remove();
        // Some sources leave a heading and a link list directly inside the body.
        // Require an explicit module heading and link-only siblings; ordinary prose,
        // citations and text that merely mentions recommendations must survive.
        for(Element heading:new java.util.ArrayList<>(root.select("h2,h3,h4"))){
            if(!heading.text().trim().matches("相关推荐|推荐阅读|相关阅读|延伸阅读|猜你喜欢|更多推荐"))continue;
            Element next=nextContentSibling(heading);boolean removed=false;
            while(next!=null&&linkOnly(next)){
                Element after=nextContentSibling(next);next.remove();removed=true;next=after;
            }
            if(removed)heading.remove();
        }
    }
    private static Element nextContentSibling(Element element){
        for(Node node=element.nextSibling();node!=null;node=node.nextSibling()){
            if(node instanceof Element)return (Element)node;
            if(node instanceof Comment)continue;
            if(node instanceof TextNode&&((TextNode)node).isBlank())continue;
            return null;
        }
        return null;
    }
    private static boolean linkOnly(Element element){
        if(element.select("a[href]").isEmpty())return false;
        // A linked image or page navigation is still article content, not a recommendation list.
        if(!element.select("img,picture,figure,figcaption,blockquote,video,audio,pre,code,table,nav,[role=navigation],.pagination,.pager").isEmpty())return false;
        if(element.is("a[href]"))return true;
        Element copy=element.clone();copy.select("a[href]").remove();
        return copy.text().trim().isEmpty();
    }
    static void sspai(Element root){
        root.select(".comment__footer__wrapper").remove();
        for(Element body:root.select(".article__main__content,.ss__article__body")){
            for(Element heading:new java.util.ArrayList<>(body.select("h2,h3"))){
                if(!sspaiTail(heading.text()))continue;
                Element next=heading.nextElementSibling();while(next!=null){Element after=next.nextElementSibling();next.remove();next=after;}heading.remove();
            }
            for(Element paragraph:new java.util.ArrayList<>(body.select("p")))if(sspaiPromotion(paragraph.text()))paragraph.remove();
        }
    }
    private static boolean sspaiTail(String text){return text.trim().matches("少数派的近期动态|你可能错过的好文章");}
    private static boolean sspaiPromotion(String text){return text.trim().matches(">\\s*下载少数派客户端.*|>\\s*实用、好用的正版软件.*少数派.*");}
    /** Remove known source footer text from snapshots made by older app versions. */
    static void cleanStored(Models.Document doc){
        if(!UrlPolicy.belongs(Models.Source.SSPAI,doc.url))return;
        for(Models.Section section:doc.sections){
            boolean tail=false;java.util.Iterator<Models.Block> blocks=section.blocks.iterator();
            while(blocks.hasNext()){Models.Block block=blocks.next();if(sspaiTail(block.value))tail=true;if(tail||sspaiPromotion(block.value))blocks.remove();}
        }
        doc.sections.removeIf(section->section.blocks.isEmpty());doc.blocks.clear();for(Models.Section section:doc.sections)doc.blocks.addAll(section.blocks);
    }
}
