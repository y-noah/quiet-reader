package app.quietreader;

import java.util.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Presentation hierarchy only. A thread retains its own identity, cache and reply loader. */
final class TopicReaderHtml {
    private TopicReaderHtml(){}
    static int visible(Set<String> expanded){int count=TopicBundle.PAGE;for(String key:expanded)if(key.startsWith("topic-window:"))try{count=Math.max(count,Integer.parseInt(key.substring(13)));}catch(NumberFormatException ignored){}return count;}
    static void visible(Set<String> expanded,int count){expanded.removeIf(key->key.startsWith("topic-window:"));expanded.add("topic-window:"+count);}
    static String commentsKey(Document doc){return "topic-comments:"+doc.sourceUrl();}
    static String collapsedKey(Document doc){return "topic-collapsed:"+doc.sourceUrl();}
    static String openKey(String post){return "topic-open:"+post;}
    static boolean postOpen(Document root,Document body,Set<String> expanded){return body!=null&&(body.sourceUrl().equals(root.sourceUrl())?!expanded.contains(collapsedKey(body)):expanded.contains(openKey(body.sourceUrl())));}
    static boolean bodyVisible(Document doc,Set<String> expanded){return doc!=null&&(doc.topic==null||!expanded.contains(collapsedKey(doc))||doc.topicBodies.values().stream().anyMatch(body->expanded.contains(openKey(body.sourceUrl()))));}
    static boolean commentsVisible(Document doc,Set<String> expanded){return doc!=null&&(doc.topic==null||!expanded.contains(collapsedKey(doc))&&expanded.contains(commentsKey(doc)));}
    static String decorate(Item item,Document doc,String html,Set<String> expanded){
        if(item.source==Source.TIEBA&&doc.topic!=null&&item.url.equals(doc.url)&&item.url.equals(doc.topic.topicUrl))return tieba(item,doc,html,expanded);
        if(item.source==Source.WEIBO&&VideoPolicy.weiboTopic(item.url)){
            org.jsoup.nodes.Document page=Jsoup.parse(html);Element main=page.selectFirst("main");
            if(main==null)return html;Element title=main.selectFirst("h1");if(title!=null)title.text(item.title);
            List<Section> parts=ReaderHtml.sections(doc);
            for(int n=0;n<parts.size();n++){
                Section part=parts.get(n);if(!part.label.equals("微博")&&!part.label.startsWith("微博 · "))continue;
                Element card=page.getElementById("part-"+n);if(card==null)continue;
                card.addClass("topic-answer");if(!part.continuationUrl.isEmpty())card.attr("data-post-url",part.continuationUrl);
            }
            return page.outerHtml();
        }
        return html;
    }
    private static String tieba(Item item,Document doc,String html,Set<String> expanded){
        org.jsoup.nodes.Document page=Jsoup.parse(html);Element main=page.selectFirst("main");if(main==null)return html;
        Element title=main.selectFirst("h1");if(title==null)return html;title.text(item.title);
        for(Element element:new ArrayList<>(main.children()))if(element!=title&&!element.normalName().equals("style"))element.remove();
        int ready=0,total=0;for(Item post:doc.topic.choices)if(!post.video){total++;if(TopicBundle.find(doc,post.url)!=null)ready++;}

        Element answers=new Element("div").addClass("topic-answers");
        int shown=0;for(int n=0;n<doc.topic.choices.size();n++){
            Item post=doc.topic.choices.get(n);if(post.video)continue;if(shown>=visible(expanded))break;
            Document part=TopicBundle.find(doc,post.url);if(part==null)continue;shown++;boolean open=postOpen(doc,part,expanded);
            Element card=answers.appendElement("article").addClass("topic-answer").attr("data-post-url",post.url).attr("id","reading-topic-"+n);
            String actualTitle=part!=null&&!part.title.isBlank()?part.title:post.title;
            card.appendElement("h2").addClass("topic-answer-title").text(actualTitle);
            if(part!=null&&open){
                Set<String> all=new HashSet<>(expanded);for(Section section:ReaderHtml.sections(part))all.add(section.id);
                Element rendered=Jsoup.parse(ReaderHtml.render(part,Source.TIEBA,19,false,all,false)).selectFirst("main");
                Element body=new Element("div").addClass("topic-post-body");for(Element node:new ArrayList<>(rendered.children()))if(!node.normalName().equals("h1")&&!node.normalName().equals("style"))body.appendChild(node);
                Element comments=new Element("div").addClass("topic-comments").attr("data-post-url",post.url);int count=0;
                Element content=body.selectFirst(".reader-content");if(content!=null)for(Element node:new ArrayList<>(content.children())){
                    if(node.hasClass("discussion-heading"))node.remove();else if(node.hasClass("discussion")){comments.appendChild(node);count++;}else if(node.id().equals("reader-continuation"))comments.appendChild(node);
                }
                for(Element control:body.select("a[href*='toggle?index='],.section-collapse"))control.remove();for(Element control:comments.select("a[href*='toggle?index='],.section-collapse"))control.remove();
                boolean primary=part.sourceUrl().equals(doc.sourceUrl());if(!primary){for(Element node:body.select("[id]"))node.attr("id","reading-topic-"+n+"-"+node.id());for(Element node:comments.select("[id]"))node.attr("id","reading-topic-"+n+"-"+node.id());}
                for(Element link:comments.select("a[href]"))if(link.attr("href").equals(ReaderHtml.ACTION+"more"))link.attr("href",ReaderHtml.ACTION+"topic-more?index="+n);
                card.appendChild(body);
                boolean showComments=expanded.contains(commentsKey(part));
                card.appendElement("a").addClass("action topic-comments-toggle").attr("href",ReaderHtml.ACTION+"topic-comments?index="+n).attr("aria-expanded",Boolean.toString(showComments)).text((showComments?"收起":"展开")+"评论"+(count>0?" · 已加载 "+count+" 条":""));
                if(showComments){
                    if(count==0)comments.appendElement("p").addClass("meta").text("当前尚未取得评论，可尝试继续加载。");
                    card.appendChild(comments);
                }
            }else if(!post.snippet.isBlank())card.appendElement("p").addClass("preview").text(post.snippet);
            Element toggle=card.appendElement("a").addClass("action topic-post-toggle").attr("href",ReaderHtml.ACTION+"topic?index="+n).attr("aria-expanded",Boolean.toString(open)).attr("aria-controls","reading-topic-"+n);
            if(open)toggle.addClass("section-collapse").attr("aria-label","收起当前内容").attr("title","收起当前内容").html("<svg viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='1.8' stroke-linecap='round' stroke-linejoin='round' aria-hidden='true'><path d='m6 14 6-6 6 6M6 19l6-6 6 6'/></svg>");
            else toggle.text("展开阅读 ↓");
        }
        if(shown<total)answers.appendElement("a").addClass("action topic-batch-more").attr("id","topic-batch-more").attr("href",ReaderHtml.ACTION+"topic-next").text("加载更多");
        main.appendChild(answers);
        main.appendElement("style").text(".topic-answer{border-bottom:1px solid var(--line);padding:14px 0;margin-bottom:12px}.topic-answer-title{font-size:17px;line-height:1.5;margin:0 0 10px;overflow-wrap:anywhere}.topic-comments{margin:8px 0 12px;padding-left:12px;border-left:2px solid var(--line)}.topic-comments .discussion{margin-bottom:10px}.topic-answer>.preview{font-size:15px}.topic-comments-toggle,.topic-post-toggle{font-size:13px}");
        return page.outerHtml();
    }
}
