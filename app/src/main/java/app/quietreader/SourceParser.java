package app.quietreader;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static app.quietreader.Models.*;

/** Pure parsers: no Android, network, credentials or script execution. */
public final class SourceParser {
    private SourceParser() {}
    public static List<Item> list(Source source,String raw) throws Exception {
        if(!source.readable())throw new IllegalArgumentException("不支持此榜单来源");
        if(AdditionalSources.supports(source))return AdditionalSources.list(source,raw);
        List<Item> out=new ArrayList<>();
        if(source==Source.WEIBO&&raw.trim().startsWith("{")) {
            JSONArray a=new JSONObject(raw).getJSONObject("data").getJSONArray("realtime");
            for(int i=0;i<a.length();i++) { JSONObject t=a.getJSONObject(i); if(t.optInt("is_ad",0)!=0)continue;String title=t.optString("word",t.optString("word_scheme"));add(out,source,title,"https://s.weibo.com/weibo?q="+java.net.URLEncoder.encode(title,"UTF-8"),t.optString("num"),VideoPolicy.metadata(t)); }
        } else if(source==Source.ZHIHU) {
            JSONArray a=new JSONObject(raw).getJSONArray("data");
            for(int i=0;i<a.length();i++) {
                JSONObject t=a.getJSONObject(i).getJSONObject("target");
                add(out,source,t.getJSONObject("title_area").optString("text"),t.getJSONObject("link").optString("url"),t.optJSONObject("metrics_area")==null?"":t.getJSONObject("metrics_area").optString("text"),VideoPolicy.metadata(t));
            }
        } else if(source==Source.CLS) {
            JSONObject response=new JSONObject(raw);
            if(response.optInt("errno",-1)!=0)throw new IllegalStateException("财联社暂未返回资讯榜");
            JSONArray a=response.getJSONArray("data");
            for(int i=0;i<a.length();i++){
                JSONObject t=a.getJSONObject(i);String id=t.optString("id");
                if(!id.matches("[1-9]\\d*"))continue;
                add(out,source,t.optString("title"),"https://api3.cls.cn/share/article/"+id+"?os=android&sv=835",
                        t.optLong("readNum")>0?t.optLong("readNum")+" 阅读":"",VideoPolicy.metadata(t)||t.optString("article_schema").toLowerCase(java.util.Locale.ROOT).contains("video"));
            }
        } else {
            org.jsoup.nodes.Document d=Jsoup.parse(raw,source==Source.WEIBO?"https://s.weibo.com/top/summary":source.endpoint);
            String selector=source==Source.WEIBO?"#pl_top_realtimehot td.td-02 a":"a.p-title";
            for(Element a:d.select(selector)) {Element row=a.closest("tr,li,.topic-item,.post-item");add(out,source,a.text(),a.absUrl("href"),"",VideoPolicy.row(row==null?a:row));}
        }
        if(out.isEmpty()) throw new IllegalStateException("未读到榜单，可能需要登录或平台已调整页面");
        return VideoPolicy.filter(out);
    }
    private static void add(List<Item> out,Source source,String title,String url,String detail) {
        add(out,source,title,url,detail,false);
    }
    private static void add(List<Item> out,Source source,String title,String url,String detail,boolean video) {
        url=UrlPolicy.normalize(source.endpoint,org.jsoup.parser.Parser.unescapeEntities(url,false));
        if(title==null||title.trim().isEmpty()||!UrlPolicy.belongs(source,url)) return;
        final String dest=url;
        if(out.stream().noneMatch(i->i.url.equals(dest))) out.add(new Item(source,title.trim(),url,detail,video));
    }
    public static Document article(Source source,String html,String url) {
        org.jsoup.nodes.Document d=Jsoup.parse(html,url);
        if(source==Source.CLS)d.select(".related-article-box,.related-article-content-box").remove();
        Document result=new Document(); result.url=url;
        Element h=d.selectFirst("h1");
        result.title=h==null?d.title():h.text();
        if(source==Source.CLS){Element title=d.selectFirst(".title-box");if(title!=null)result.title=title.text();}
        if(VideoPolicy.mainPost(source,d,url)){
            result.filteredVideo=true;result.filteredVideos=1;
            result.notice="已过滤视频主题，不展示视频帖及其回复。";return result;
        }
        Element author=d.selectFirst("meta[name=author]");
        if(author!=null)result.byline=author.attr("content").trim();
        if(source==Source.CLS){Element info=d.selectFirst(".information-box");if(info!=null)result.byline=info.text();}
        for(Element a:d.select("a[rel=next],a[href]")) {
            if(!a.attr("rel").equals("next")&&!a.text().trim().matches("下一页[>›»]?"))continue;
            String next=UrlPolicy.normalize(url,a.attr("href"));
            if(UrlPolicy.sameThread(source,url,next)){result.nextUrl=next;break;}
        }
        // Only extract actual article regions, never the whole body or recommendation rail.
        String selector;
        switch(source) {
            case ZHIHU: selector=".RichContent-inner .RichText, .Post-RichTextContainer, .QuestionRichText, .AnswerItem.VideoAnswer, .AnswerItem[data-content-type=video]"; break;
            case HUPU: selector=".thread-content-detail, .thread-content, .quote-content"; break;
            case WEIBO: selector=".card-wrap .txt, .weibo-text, [class*=detail_wbtext]"; break;
            case CLS: selector="section.content-box > .content"; break;
            case IFANR: selector="article.c-article-content"; break;
            default: selector="article";
        }
        Set<String> seen=new HashSet<>();
        boolean hasVideo=false;
        // Hupu's main video is a sibling of the text region, not inside it.
        // Inspect only observed main-post containers, excluding recommendation players.
        if(source==Source.HUPU)for(Element main:d.select("[class*='post-content_main-post-info']")) {
            if(main.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container")!=null)continue;
            Element owned=main.clone();
            owned.select("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container,.reply-list-wrapper").remove();
            if(!owned.select("video").isEmpty())hasVideo=true;
        }
        for(Element root:d.select(selector)) {
            boolean nested=false;
            for(Element p:root.parents()) if(p.is(selector)) { nested=true; break; }
            if(nested) continue;
            if(root.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.feed-ad-container")!=null)continue;
            if(VideoPolicy.videoSection(source,root)){
                result.filteredSectionIds.add(sectionId(source,root));
                result.filteredVideos=result.filteredSectionIds.size();continue;
            }
            String continuation=source==Source.WEIBO?WeiboPost.continuation(url,root):"";
            Element clean=root.clone();
            if(!continuation.isEmpty())for(Element a:clean.select("a[href]"))
                if(WeiboPost.fulltextLabel(a)&&WeiboPost.same(continuation,UrlPolicy.normalize(url,a.attr("href"))))a.remove();
            clean.select("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container").remove();
            if(VideoPolicy.player(clean)||clean.text().contains("视频加载中"))hasVideo=true;
            clean.select(VideoPolicy.PLAYERS).remove();
            clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.RichContent-actions,.feed-ad-container").remove();
            for(Element leaf:clean.getAllElements())if(leaf.children().isEmpty()&&leaf.text().matches("视频加载中[.…。]*"))leaf.remove();
            List<Element> photos=source==Source.WEIBO?mobileOriginalPhotos(root,url):java.util.Collections.emptyList();
            List<String> photoUrls=new ArrayList<>();for(Element img:photos)photoUrls.add(imageSource(img,url));
            String key=clean.text()+clean.select("img").eachAttr("src")+continuation+photoUrls;
            if(!seen.add(key)||key.trim().isEmpty()) continue;
            if(!result.blocks.isEmpty()) result.blocks.add(new Block("separator",""));
            int start=result.blocks.size();
            StringBuilder buffer=new StringBuilder();
            List<InlineImage> inline=new ArrayList<>();
            List<InlineLink> links=new ArrayList<>();
            visit(clean,result,buffer,inline,links,url); flush(result,buffer,inline,links);
            for(Element img:photos){visit(img,result,buffer,inline,links,url);flush(result,buffer,inline,links);}
            if(result.blocks.size()>start) {
                boolean answer=source==Source.ZHIHU&&root.closest(".AnswerItem")!=null;
                Element container=answer?root.closest(".AnswerItem"):root;
                String id=continuation.isEmpty()?sectionId(source,root,photoUrls):"weibo-"+WeiboPost.id(continuation);
                Element name=container.selectFirst(".AuthorInfo-name,.author-name");
                String label=answer?"回答":source==Source.ZHIHU?(root.is(".QuestionRichText")?"问题补充":"回答"):source==Source.HUPU?"楼层":source==Source.WEIBO?"微博":"正文";
                if(source==Source.HUPU)label=root.closest(".reply-list-wrapper")!=null?"回复":root.closest("[class*='post-content_main-post-info']")!=null?"主帖":"楼层";
                Section section=new Section(id,label+(name==null?"":" · "+name.text()),answer||label.equals("回答"));
                section.continuationUrl=continuation;
                section.blocks.addAll(result.blocks.subList(start,result.blocks.size()));result.sections.add(section);
            }
        }
        // Weibo topic pages link to multiple original posts.
        String pattern="/(detail/\\d+|\\d+/[A-Za-z0-9]+)";
        // Only known topic containers can produce a selection list. A blocked article's
        // recommendation links must never masquerade as successfully loaded content.
        boolean topic=source==Source.WEIBO&&VideoPolicy.weiboTopic(url);
        for(Element a:topic?d.select("a[href]"):new org.jsoup.select.Elements()) {
            if(a.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement")!=null)continue;
            String dest=UrlPolicy.normalize(url,a.attr("href"));
            if(dest.equals(url)||!dest.matches(".*"+pattern+".*")||a.text().trim().length()<6) continue;
            Element owner=a.closest(".card-wrap,.card,.topic-item");
            if(VideoPolicy.url(source,dest)||VideoPolicy.row(owner)){result.filteredVideos++;continue;}
            add(result.related,source,a.text(),dest,"");
            if(result.related.size()>=30) break;
        }
        String body=d.body().text();
        if(body.contains("登录后查看")||body.contains("登录后继续")||body.contains("展开阅读全文")||body.contains("打开App查看全文"))
            result.notice="当前页面可能只返回部分内容。请登录后重新读取；未加载部分不会被当作全文。";
        if(source==Source.ZHIHU && !result.blocks.isEmpty()) result.notice="回答独立折叠。下滑到底或点「加载下一批回答」继续；登录限制或未加载部分不代表完整回答列表。";
        if(source==Source.WEIBO && !result.blocks.isEmpty()) {
            result.notice=VideoPolicy.weiboTopic(url)?"仅展示当前页面已加载的微博；不是话题下全部内容。":"仅展示来源页已加载的微博正文。";
            if(result.sections.stream().anyMatch(s->!s.continuationUrl.isEmpty()))result.notice+=" 部分内容是摘要，展开后可继续读取原帖。";
        }
        if(source==Source.HUPU&&!result.blocks.isEmpty())
            result.notice=(result.notice.isEmpty()?"":result.notice+" ")+"仅展示当前页已加载的帖子和回复；未加载部分不会显示，不代表全部楼层。";
        result.unsupportedVideo=hasVideo;
        if(hasVideo)result.notice=(result.notice.isEmpty()?"":result.notice+" ")+"原文包含视频；News 仅整理文字和图片，视频可在来源页观看。";
        if(result.filteredVideos>0)result.notice=(result.notice.isEmpty()?"":result.notice+" ")+VideoPolicy.filteredNotice(result.filteredVideos);
        if(!result.hasContent()&&result.related.isEmpty()&&!hasVideo&&result.filteredVideos==0) result.notice="尚未取得可阅读正文，可能需要登录、页面尚未加载或此类型暂不支持。";
        return result;
    }
    private static String sectionId(Source source,Element root){
        return sectionId(source,root,java.util.Collections.emptyList());
    }
    private static String sectionId(Source source,Element root,List<String> originalPhotos){
        Element container=VideoPolicy.owner(source,root);
        String id="";try{id=new JSONObject(container.attr("data-zop")).optString("itemId","");}catch(Exception ignored){}
        if(id.isEmpty())id=container.id();
        if(id.isEmpty()){
            Element clean=root.clone();clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.RichContent-actions,.feed-ad-container").remove();
            String key=clean.text()+clean.select("img").eachAttr("src");
            if(!originalPhotos.isEmpty())key+=originalPhotos;
            id="part-"+Integer.toHexString(key.hashCode());
        }
        return id;
    }
    private static List<Element> mobileOriginalPhotos(Element root,String base){
        List<Element> photos=new ArrayList<>();Element original=root.parent();
        if(!root.is(".weibo-text")||original==null||!original.is(".weibo-og"))return photos;
        // The same owned sibling photos must participate in both deduplication and rendering.
        Element media=VideoPolicy.owned(original);media.select(VideoPolicy.PLAYERS).remove();
        Set<String> seen=new HashSet<>();
        for(Element img:media.select(".weibo-media-wraps img")){
            if(img.closest(".weibo-og")!=media||img.closest(".weibo-text")!=null)continue;
            String url=imageSource(img,base);
            if(!url.isEmpty()&&seen.add(url))photos.add(img);
            if(photos.size()>=30)break;
        }
        return photos;
    }
    private static String imageSource(Element image,String base){
        String src=image.attr("src");
        if(src.isEmpty()||src.startsWith("data:"))src=image.attr("data-src");
        if(src.isEmpty())src=image.attr("data-original");
        return UrlPolicy.normalize(base,src);
    }
    private static void visit(Node node,Document result,StringBuilder buffer,List<InlineImage> inline,List<InlineLink> links,String base) {
        if(node instanceof TextNode) { buffer.append(((TextNode)node).text()); return; }
        if(!(node instanceof Element)) return;
        Element e=(Element)node;
        if(e.tagName().equals("a")&&!e.text().isEmpty()&&e.select("img,div,p,br").isEmpty()){
            String url=UrlPolicy.normalize(base,e.attr("href"));
            if(ReaderLinks.allowed(url)){
                int start=buffer.length();buffer.append(e.text());links.add(new InlineLink(start,buffer.length(),url));return;
            }
        }
        if(e.tagName().equals("img")) {
            String src=imageSource(e,base);
            flush(result,buffer,inline,links);
            if(!src.isEmpty()) result.blocks.add(new Block("image",src));
            return;
        }
        boolean boundary=e.isBlock()||e.tagName().equals("br");
        if(boundary) flush(result,buffer,inline,links);
        for(Node child:e.childNodes()) visit(child,result,buffer,inline,links,base);
        if(boundary) flush(result,buffer,inline,links);
    }
    private static void flush(Document result,StringBuilder buffer,List<InlineImage> inline,List<InlineLink> links) {
        int leading=0;while(leading<buffer.length()&&buffer.charAt(leading)<=' ')leading++;
        String text=buffer.toString().trim();buffer.setLength(0);
        if(!text.isEmpty()) {
            List<InlineImage> adjusted=new ArrayList<>();
            for(InlineImage i:inline)adjusted.add(new InlineImage(i.start-leading,i.end-leading,i.url,i.alt));
            List<InlineLink> adjustedLinks=new ArrayList<>();
            for(InlineLink link:links)adjustedLinks.add(new InlineLink(link.start-leading,link.end-leading,link.url));
            result.blocks.add(new Block("text",text,adjusted,adjustedLinks));
        }
        inline.clear();
        links.clear();
    }
}
