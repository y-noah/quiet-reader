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
        } else if(source==Source.TOUTIAO) {
            JSONArray a=new JSONObject(raw).getJSONArray("data");
            for(int i=0;i<a.length();i++) { JSONObject t=a.getJSONObject(i); add(out,source,t.optString("Title"),"https://www.toutiao.com/trending/"+t.optString("ClusterIdStr")+"/",t.optString("HotValue"),VideoPolicy.metadata(t)); }
        } else if(source==Source.WALLSTREET) {
            JSONArray a=new JSONObject(raw).getJSONObject("data").getJSONArray("day_items");
            for(int i=0;i<a.length();i++){JSONObject t=a.getJSONObject(i);add(out,source,t.optString("title"),t.optString("uri"),t.optString("pageviews")+" 阅读",VideoPolicy.metadata(t));}
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
        } else if(source==Source.GEEKPARK) {
            JSONArray a=new JSONObject(raw).getJSONArray("posts");
            for(int i=0;i<a.length();i++){
                JSONObject t=a.getJSONObject(i);String id=t.optString("id");
                if(!id.matches("[1-9]\\d*"))continue;
                add(out,source,t.optString("title"),"https://www.geekpark.net/news/"+id,"七日热门",
                        VideoPolicy.metadata(t)||t.optString("post_type").matches("video|pure_video"));
            }
        } else if(source==Source.HACKERNEWS) {
            for(Element row:Jsoup.parse(raw,source.endpoint).select("tr.athing")) {
                Element a=row.selectFirst(".titleline > a");String id=row.id();
                if(a!=null&&id.matches("\\d+"))add(out,source,a.text(),"https://news.ycombinator.com/item?id="+id,"热门讨论 · 英文",VideoPolicy.row(row));
            }
        } else if(source==Source.SMZDM) {
            org.jsoup.nodes.Document page=Jsoup.parse(raw,source.endpoint);
            // Fail closed on captcha, a default 12/24h page or an unrelated product collection.
            if(!page.title().contains("3小时内最热优惠排行"))throw new IllegalStateException("未取得三小时好价榜");
            for(Element row:page.select("#feed-main-list > li[data-tab=3h最热]")){
                if(!row.attr("data-type").equals("普通")||row.is(".ad,.advertisement"))continue;
                Element a=row.selectFirst(".feed-ver-title a[href]");
                if(a==null)continue;
                String url=a.absUrl("href");
                if(!url.matches("https://(?:www\\.)?smzdm\\.com/p/\\d+/"))continue;
                List<String> detail=new ArrayList<>();
                for(String selector:new String[]{".z-highlight",".tag-bottom-right",".feed-ver-date"}){
                    Element value=row.selectFirst(selector);if(value!=null&&!value.text().isEmpty())detail.add(value.text());
                }
                Element comments=row.selectFirst("a.z-group-data[href$=#comments]");
                if(comments!=null&&comments.text().matches("\\d+"))detail.add(comments.text()+" 评论");
                add(out,source,a.text(),url,String.join(" · ",detail),VideoPolicy.row(row));
                if(out.size()>=30)break;
            }
        } else if(source==Source.TIEBA) {
            JSONArray a=new JSONObject(raw).getJSONObject("data").getJSONObject("bang_topic").getJSONArray("topic_list");
            for(int i=0;i<a.length();i++) { JSONObject t=a.getJSONObject(i); add(out,source,t.optString("topic_name"),t.optString("topic_url"),t.optString("discuss_num"),VideoPolicy.metadata(t)); }
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
        Element h=d.selectFirst(source==Source.TIEBA?".pb-title,h1":"h1");
        result.title=h==null?d.title():h.text();
        if(source==Source.CLS){Element title=d.selectFirst(".title-box");if(title!=null)result.title=title.text();}
        if(source==Source.GUOKR){Element title=d.selectFirst("[class*=ArticleTitle-]");if(title!=null)result.title=title.text();}
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
            case TOUTIAO: selector="article"; break;
            case ZHIHU: selector=".RichContent-inner .RichText, .Post-RichTextContainer, .QuestionRichText, .AnswerItem.VideoAnswer, .AnswerItem[data-content-type=video]"; break;
            case HUPU: selector=".thread-content-detail, .thread-content, .quote-content"; break;
            case TIEBA: selector=".d_post_content, .post-content, .postContent, [class*=post-content-text], .pb-content-wrap, .comment-content, .pb-rich-text"; break;
            case WEIBO: selector=".card-wrap .txt, .weibo-text, [class*=detail_wbtext]"; break;
            case WALLSTREET: selector="article,.article-content"; break;
            case CLS: selector="section.content-box > .content"; break;
            case GEEKPARK: selector="#article-body .article-content"; break;
            case GUOKR: selector="[class*=ArticleContent-]"; break;
            case DOUBAN: selector=".note .note, #link-report .note, .topic-content, .review-content, .status-saying, .rich-content"; break;
            case ITHOME: selector="#paragraph.post_content"; break;
            case IFANR: selector="article.c-article-content"; break;
            case JUEJIN: selector="#article-root .article-viewer"; break;
            case SSPAI: selector=".article-body .wangEditor-txt"; break;
            case HACKERNEWS: selector=".toptext,.commtext"; break;
            case SMZDM: selector=".txt-detail,.article-content,.article_section,.item-preferential"; break;
            default: selector="article";
        }
        Set<String> seen=new HashSet<>();
        java.util.Map<Integer,String> discussionAuthors=new java.util.HashMap<>();
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
            if(source==Source.ITHOME)clean.select(".tougao-user").remove();
            if(!continuation.isEmpty())for(Element a:clean.select("a[href]"))
                if(WeiboPost.fulltextLabel(a)&&WeiboPost.same(continuation,UrlPolicy.normalize(url,a.attr("href"))))a.remove();
            if(source==Source.TIEBA){
                // Nested replies have their own media ownership; do not erase the parent floor.
                for(Element reply:clean.select(".pb-lzl-item")){
                    Element body=reply.selectFirst(".comment-content");
                    if(body!=null&&VideoPolicy.videoSection(source,body)){
                        result.filteredSectionIds.add(sectionId(source,body));
                        reply.remove();
                    }
                }
                result.filteredVideos=result.filteredSectionIds.size();
                cleanTiebaChrome(clean);
            }
            clean.select("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container").remove();
            if(VideoPolicy.player(clean)||clean.text().contains("视频加载中"))hasVideo=true;
            clean.select(VideoPolicy.PLAYERS).remove();
            clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.RichContent-actions,.pc-pb-comments-desc,.feed-ad-container").remove();
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
                Element container=answer?root.closest(".AnswerItem"):source==Source.HACKERNEWS&&root.closest(".comtr")!=null?root.closest(".comtr"):root;
                String id=continuation.isEmpty()?sectionId(source,root,photoUrls):"weibo-"+WeiboPost.id(continuation);
                Element name=container.selectFirst(".AuthorInfo-name,.author-name,.hnuser");
                String label=answer?"回答":source==Source.ZHIHU?(root.is(".QuestionRichText")?"问题补充":"回答"):source==Source.HUPU||source==Source.TIEBA?"楼层":source==Source.WEIBO?"微博":source==Source.HACKERNEWS?"讨论":"正文";
                if(source==Source.HUPU)label=root.closest(".reply-list-wrapper")!=null?"回复":root.closest("[class*='post-content_main-post-info']")!=null?"主帖":"楼层";
                if(source==Source.TIEBA){
                    Element post=root.closest(".l_post[data-field]");
                    try{if(post!=null&&new JSONObject(post.attr("data-field")).getJSONObject("content").optInt("post_no")==1)label="主帖";}catch(Exception ignored){}
                    if(root.closest(".pb-content-wrap")!=null&&root.closest(".comment-content,.pb-lzl-item")==null)label="主帖";
                }
                if(source==Source.HACKERNEWS&&name!=null){Element indent=container.selectFirst(".ind[indent]");int depth=0;try{depth=Integer.parseInt(indent==null?"0":indent.attr("indent"));}catch(Exception ignored){}label=depth>0&&discussionAuthors.containsKey(depth-1)?"回复 "+discussionAuthors.get(depth-1):"评论";discussionAuthors.put(depth,name.text());}
                Section section=new Section(id,label+(name==null?"":" · "+name.text()),answer||label.equals("回答"));
                section.continuationUrl=continuation;
                section.blocks.addAll(result.blocks.subList(start,result.blocks.size()));result.sections.add(section);
            }
        }
        // SMZDM's actual product hero is outside txt-detail. Do not treat an og:image,
        // recommendation or a login-wall picture as readable article content.
        if(source==Source.SMZDM&&result.hasContent()&&!result.sections.isEmpty()) {
            java.util.regex.Matcher product=java.util.regex.Pattern.compile("^https://(?:www\\.)?smzdm\\.com/p/(\\d+)/?(?:[?#].*)?$").matcher(url);
            if(product.matches())for(Element info:d.select("#feed-main > .J_info[articleid]")) {
                if(!info.attr("articleid").matches("\\d+_"+product.group(1))||info.closest(VideoPolicy.EXCLUDED)!=null)continue;
                // A matching product container can also host recommendations. Select only
                // its owned image, not the first matching image inside a nested sidebar.
                Element image=VideoPolicy.owned(info).selectFirst(".img-box > img.main-img");if(image==null)continue;
                String raw=image.attr("src");if(raw.isEmpty()||raw.startsWith("data:"))raw=image.attr("data-src");
                String src=UrlPolicy.normalize(url,raw);
                String imageHost=UrlPolicy.host(src);
                if(!UrlPolicy.https(src)||!(UrlPolicy.domain(imageHost,"zdmimg.com")||imageHost.equals("qny.smzdm.com"))||result.containsImage(src))continue;
                Block hero=new Block("image",src);result.blocks.add(0,hero);result.sections.get(0).blocks.add(0,hero);break;
            }
        }
        // Topic pages link to multiple original posts. Only platform-specific content URLs qualify.
        String pattern;
        switch(source) {
            case TOUTIAO: pattern="/article/\\d+"; break;
            case TIEBA: pattern="/p/\\d+"; break;
            case HUPU: pattern="/\\d+\\.html"; break;
            case ZHIHU: pattern="/question/\\d+(/answer/\\d+)?"; break;
            default: pattern="/(detail/\\d+|\\d+/[A-Za-z0-9]+)";
        }
        // Only known topic containers can produce a selection list. A blocked article's
        // recommendation links must never masquerade as successfully loaded content.
        boolean topic=(source==Source.TOUTIAO&&url.contains("/trending/"))
                ||(source==Source.TIEBA&&url.contains("/hottopic/"))
                ||(source==Source.WEIBO&&VideoPolicy.weiboTopic(url));
        for(Element a:topic?d.select("a[href]"):new org.jsoup.select.Elements()) {
            if(a.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement")!=null)continue;
            String dest=UrlPolicy.normalize(url,a.attr("href"));
            if(dest.equals(url)||!dest.matches(".*"+pattern+".*")||a.text().trim().length()<6) continue;
            Element owner=a.closest(".card-wrap,.card,.topic-item,.threadlist_li,.l_post");
            if(VideoPolicy.url(source,dest)||VideoPolicy.row(owner)){result.filteredVideos++;continue;}
            add(result.related,source,a.text(),dest,"");
            if(result.related.size()>=30) break;
        }
        String body=d.body().text();
        if(source==Source.TIEBA){
            // A quoted phrase in a reply or a hidden login dialog is not authentication state.
            for(Element gate:d.select(".login-guard-mask")){
                boolean hidden=false;
                for(Element node=gate;node!=null;node=node.parent()){
                    String style=node.attr("style").replaceAll("\\s+","").toLowerCase(java.util.Locale.ROOT);
                    if(node.hasAttr("hidden")||style.matches(".*(?:display:none|visibility:hidden|visibility:collapse)(?:!important)?(?:;.*)?$")){hidden=true;break;}
                }
                if(!hidden&&(gate.text().contains("登录后查看")||gate.text().contains("登录后继续"))){result.loginRequired=true;break;}
            }
            if(result.loginRequired)result.notice="贴吧网页仍显示回复登录限制，尚未取得受限回复。可打开「来源 / 登录」确认原帖回复是否已显示，再读取回来。";
        }else if(body.contains("登录后查看")||body.contains("登录后继续")||body.contains("展开阅读全文")||body.contains("打开App查看全文"))
            result.notice="当前页面可能只返回部分内容。请登录后重新读取；未加载部分不会被当作全文。";
        if(source==Source.ZHIHU && !result.blocks.isEmpty()) result.notice="回答独立折叠。下滑到底或点「加载下一批回答」继续；登录限制或未加载部分不代表完整回答列表。";
        if(source==Source.WEIBO && !result.blocks.isEmpty()) {
            result.notice=VideoPolicy.weiboTopic(url)?"仅展示当前页面已加载的微博；不是话题下全部内容。":"仅展示来源页已加载的微博正文。";
            if(result.sections.stream().anyMatch(s->!s.continuationUrl.isEmpty()))result.notice+=" 部分内容是摘要，展开后可继续读取原帖。";
        }
        if((source==Source.HUPU||source==Source.TIEBA)&&!result.blocks.isEmpty())
            result.notice=(result.notice.isEmpty()?"":result.notice+" ")+"仅展示当前页已加载的帖子和回复；未加载部分不会显示，不代表全部楼层。";
        result.unsupportedVideo=hasVideo;
        if(hasVideo)result.notice=(result.notice.isEmpty()?"":result.notice+" ")+"原文包含视频；News 仅整理文字和图片，视频可在来源页观看。";
        if(source==Source.HACKERNEWS) {
            Element title=d.selectFirst(".titleline");if(title!=null)result.title=title.text();
            result.notice="这里整理 HN 当前页的讨论，不是外部新闻全文。外部新闻网站暂未适配。";
        }
        if(result.filteredVideos>0)result.notice=(result.notice.isEmpty()?"":result.notice+" ")+VideoPolicy.filteredNotice(result.filteredVideos);
        if(!result.hasContent()&&result.related.isEmpty()&&!hasVideo&&result.filteredVideos==0) result.notice="尚未取得可阅读正文，可能需要登录、页面尚未加载或此类型暂不支持。";
        if(source==Source.DOUBAN&&!result.hasContent()&&(body.contains("你没有权限访问这个页面")||body.contains("请登录后重试"))){result.loginRequired=true;result.notice="豆瓣要求登录或当前账号没有访问权限。请在来源页确认后重新读取。";}
        return result;
    }
    private static String sectionId(Source source,Element root){
        return sectionId(source,root,java.util.Collections.emptyList());
    }
    private static String sectionId(Source source,Element root,List<String> originalPhotos){
        Element container=VideoPolicy.owner(source,root);
        if(source==Source.HACKERNEWS&&root.closest(".comtr")!=null)container=root.closest(".comtr");
        String id="";try{id=new JSONObject(container.attr("data-zop")).optString("itemId","");}catch(Exception ignored){}
        if(id.isEmpty())id=container.id();
        if(id.isEmpty()){
            Element clean=root.clone();clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.RichContent-actions,.pc-pb-comments-desc,.feed-ad-container").remove();
            if(source==Source.TIEBA)cleanTiebaChrome(clean);
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
        // IT之家 uses an HTTP transparent PNG in src, not a data URI. The original may
        // live on a merchant CDN, so validate the resolved URL rather than its image host.
        if(UrlPolicy.belongs(Source.ITHOME,base)){
            for(String attribute:new String[]{"data-original","data-src"}){
                String original=UrlPolicy.normalize(base,image.attr(attribute));
                if(!original.isEmpty()&&!isImagePlaceholder(original))return original;
            }
        }
        String src=image.attr("src");
        if(src.isEmpty()||src.startsWith("data:"))src=image.attr("data-src");
        if(src.isEmpty())src=image.attr("data-original");
        String resolved=UrlPolicy.normalize(base,src);
        return isImagePlaceholder(resolved)?"":resolved;
    }
    static boolean isImagePlaceholder(String url){
        try{java.net.URI uri=java.net.URI.create(url);return "img.ithome.com".equalsIgnoreCase(uri.getHost())&&"/images/v2/t.png".equals(uri.getPath());}
        catch(Exception ignored){return false;}
    }
    private static void cleanTiebaChrome(Element clean){
        // Observed UI containers only. The same words inside actual prose remain untouched.
        for(Element header:clean.select(".pb-lzl-item > .head-line.user-info")){
            Element author=header.selectFirst(".head-name");
            String name=author==null?"":author.text().trim();
            if(name.isEmpty())header.remove();
            else header.replaceWith(new Element("p").text(name+"："));
        }
        clean.select(".lzl-wrapper > .show-more-lzl").remove();
        clean.select(".login-guard-mask").remove(); // Report access state via notice, never as post prose.
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
            // Confirmed Tieba emoji CDN path. Do not classify photos by small dimensions or alt text.
            if(isInlineEmoji(src)) {
                String alt=e.attr("alt").trim();if(alt.isEmpty()||alt.length()>80)alt="表情";
                int start=buffer.length();buffer.append('[').append(alt).append(']');
                inline.add(new InlineImage(start,buffer.length(),src,alt));return;
            }
            flush(result,buffer,inline,links);
            if(!src.isEmpty()) result.blocks.add(new Block("image",src));
            return;
        }
        boolean boundary=e.isBlock()||e.tagName().equals("br");
        if(boundary) flush(result,buffer,inline,links);
        for(Node child:e.childNodes()) visit(child,result,buffer,inline,links,base);
        if(boundary) flush(result,buffer,inline,links);
    }
    private static boolean isInlineEmoji(String src) {
        try {
            java.net.URI uri=java.net.URI.create(src);
            return "https".equals(uri.getScheme())&&"tb3.bdstatic.com".equals(uri.getHost())
                    &&uri.getPath().matches("/emoji/image_emoticon[0-9]+(?:@2x)?\\.png");
        } catch(Exception ignored){return false;}
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
