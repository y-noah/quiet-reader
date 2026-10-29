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
    private static final java.util.regex.Pattern HYBRID_IMAGE_OPEN=java.util.regex.Pattern.compile("!\\[\\]\\(\\s*$"),HYBRID_IMAGE_CLOSE=java.util.regex.Pattern.compile("^\\s*\\)");
    private SourceParser() {}
    public static List<Item> list(Source source,String raw) throws Exception {
        if(!source.readable())throw new IllegalArgumentException("不支持此榜单来源");
        if(source==Source.ITHOME)return IthomeSource.list(raw);
        if(source==Source.THEPAPER)return ThepaperSource.list(raw);
        if(source==Source.BBC)return BbcChineseSource.list(raw);
        if(AdditionalSources.supports(source))return AdditionalSources.list(source,raw);
        List<Item> out=new ArrayList<>();
        if(source==Source.WEIBO&&raw.trim().startsWith("{")) {
            JSONObject response=new JSONObject(raw);
            if(response.has("ok")&&response.optInt("ok")!=1)throw new IllegalStateException("微博暂未返回热搜");
            JSONObject data=response.getJSONObject("data");
            JSONArray a=data.optJSONArray("band_list");
            if(a==null)a=data.getJSONArray("realtime");
            for(int i=0;i<a.length();i++) { JSONObject t=a.getJSONObject(i); if(t.optInt("is_ad",0)!=0)continue;String title=t.optString("word").trim();if(title.isEmpty())title=t.optString("note",t.optString("word_scheme")).trim();add(out,source,title,"https://s.weibo.com/weibo?q="+java.net.URLEncoder.encode(title,"UTF-8"),t.optString("num"),VideoPolicy.metadata(t)); }
        } else if(source==Source.ZHIHU) {
            JSONArray a=new JSONObject(raw).getJSONArray("data");
            for(int i=0;i<a.length();i++) {
                JSONObject t=a.getJSONObject(i).getJSONObject("target");
                JSONObject excerpt=t.optJSONObject("excerpt_area");Object text=excerpt==null?null:excerpt.opt("text");
                // This field is source-provided plain question context, not
                // article HTML or a complete authenticated answer.
                String snippet=text instanceof String?app.quietreader.discovery.HotCandidate.clean((String)text,600):"";
                add(out,source,t.getJSONObject("title_area").optString("text"),t.getJSONObject("link").optString("url"),t.optJSONObject("metrics_area")==null?"":t.getJSONObject("metrics_area").optString("text"),VideoPolicy.metadata(t),snippet);
            }
        } else if(source==Source.CLS) {
            JSONObject response=new JSONObject(raw);
            if(response.optInt("errno",-1)!=0)throw new IllegalStateException("财联社暂未返回资讯榜");
            JSONArray a=response.getJSONArray("data");
            for(int i=0;i<a.length();i++){
                JSONObject t=a.getJSONObject(i);String id=t.optString("id");
                if(!id.matches("[1-9]\\d*"))continue;
                Object brief=t.opt("brief");
                String snippet=brief instanceof String?app.quietreader.discovery.HotCandidate.clean((String)brief,600):"";
                add(out,source,t.optString("title"),"https://api3.cls.cn/share/article/"+id+"?os=android&sv=835",
                        t.optLong("readNum")>0?t.optLong("readNum")+" 阅读":"",VideoPolicy.metadata(t)||t.optString("article_schema").toLowerCase(java.util.Locale.ROOT).contains("video"),snippet,clsPublishedAt(t.opt("ctime")));
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
        add(out,source,title,url,detail,video,"");
    }
    /** Official ctime is Unix seconds, verified against the article byline.
     * Accept integral JSON numbers only; milliseconds and out-of-calendar values
     * stay unknown instead of making an invalid item lead the latest view. */
    private static Long clsPublishedAt(Object value) {
        if(!(value instanceof Integer)&&!(value instanceof Long))return null;
        long seconds=((Number)value).longValue();
        return seconds>0&&seconds<=253402300799L?seconds*1000L:null;
    }
    private static void add(List<Item> out,Source source,String title,String url,String detail,boolean video,String snippet) {
        add(out,source,title,url,detail,video,snippet,null);
    }
    private static void add(List<Item> out,Source source,String title,String url,String detail,boolean video,String snippet,Long publishedAt) {
        url=UrlPolicy.normalize(source.endpoint,org.jsoup.parser.Parser.unescapeEntities(url,false));
        if(title==null||title.trim().isEmpty()||!UrlPolicy.belongs(source,url)) return;
        final String dest=url;
        if(out.stream().noneMatch(i->i.url.equals(dest))) out.add(new Item(source,title.trim(),url,detail,video,snippet,publishedAt));
    }
    public static Document article(Source source,String html,String url) {
        return article(source,html,url,false);
    }
    static Document readingArticle(Source source,String html,String url){return article(source,html,url,true);}
    private static Document article(Source source,String html,String url,boolean explicitReading) {
        org.jsoup.nodes.Document d=Jsoup.parse(html,url);
        Document result=parseArticle(source,d,url,explicitReading);
        if(!result.loginRequired&&!result.sourceUnavailable&&!result.filteredVideo&&!result.videoTopic&&result.notice.isEmpty()){
            long generic=ArticleUpdateEvidence.modified(source,d,url,System.currentTimeMillis());
            if(result.sourceModifiedAt>0&&generic>0&&result.sourceModifiedAt!=generic)result.sourceModifiedAt=0;
            else if(result.sourceModifiedAt<=0)result.sourceModifiedAt=generic;
            if(result.sourceModifiedAt>0)result.sourceUpdateEvidence=ArticleUpdateEvidence.VERSION;
        }
        return result;
    }
    private static Document parseArticle(Source source,org.jsoup.nodes.Document d,String url,boolean explicitReading){
        if(source==Source.BBC)return BbcChineseSource.article(d,url);
        if(source==Source.HUPU){Document structured=HupuDiscussion.article(d,url);if(structured!=null)return structured;}
        if(source==Source.THEPAPER){d=ThepaperSource.articlePage(d,url);if(d==null){Document unavailable=new Document();unavailable.url=url;unavailable.sourceUnavailable=true;unavailable.notice="尚未取得澎湃文章正文，可在来源页检查访问状态";return unavailable;}}
        if(source==Source.DOUYIN)return DouyinArticle.article(d,url);
        if(source==Source.XHS)return XhsArticle.article(d,url);
        if(StructuredSources.supports(source))return StructuredSources.article(source,d,url);
        if(source==Source.CLS)d.select(".related-article-box,.related-article-content-box").remove();
        Document result=new Document(); result.url=url;
        Element h=d.selectFirst("h1");
        result.title=h==null?d.title():h.text();
        if(source==Source.CLS){Element title=d.selectFirst(".title-box");if(title!=null)result.title=title.text();}
        boolean mainVideo=VideoPolicy.mainPost(source,d,url);
        result.videoTopic=mainVideo;
        if(mainVideo&&!explicitReading){
            result.filteredVideo=true;result.filteredVideos=1;
            result.notice="已过滤视频主题，不展示视频帖及其回复。";return result;
        }
        Element author=d.selectFirst("meta[name=author]");
        if(author!=null)result.byline=author.attr("content").trim();
        if(source==Source.V2EX){Element member=d.selectFirst(".header small.gray a[href^=/member/]");if(member!=null)result.byline=member.text().trim();}
        if(source==Source.CLS){Element info=d.selectFirst(".information-box");if(info!=null)result.byline=info.text();}
        if(source==Source.ITHOME){
            if(!IthomeSource.articleUrl(url)){result.sourceUnavailable=true;result.notice="此页面不是已支持的IT之家文章";return result;}
            Element writer=d.selectFirst("#author_baidu");if(writer!=null)result.byline=writer.text();
            for(Element image:d.select("#paragraph.post_content img[data-original]")){String original=UrlPolicy.normalize(url,image.attr("data-original"));if(!original.isEmpty())image.attr("src",original);}
        }
        for(Element a:d.select("a[rel=next],a[href]")) {
            if(!a.attr("rel").equals("next")&&!a.text().trim().matches("下一页[>›»]?"))continue;
            String next=UrlPolicy.normalize(url,a.attr("href"));
            if(UrlPolicy.sameThread(source,url,next)){result.nextUrl=next;break;}
        }
        if(source==Source.V2EX||source==Source.HUPU)DiscussionPages.pagination(source,d,result);
        // Only extract actual article regions, never the whole body or recommendation rail.
        String selector;
        switch(source) {
            case ZHIHU: selector=".RichContent-inner .RichText, .Post-RichTextContainer, .QuestionRichText, .AnswerItem.VideoAnswer, .AnswerItem[data-content-type=video]"; break;
            case HUPU: selector=".thread-content-detail, .thread-content, .quote-content"; break;
            case WEIBO: selector=".card-wrap .txt, .weibo-text, [class*=detail_wbtext]"; break;
            case CLS: selector="section.content-box > .content"; break;
            case V2EX: selector=".topic_content,.reply_content"; break;
            case SSPAI: selector=".article__main__content,.ss__article__body"; break;
            case IFANR: selector="article.c-article-content"; break;
            case ITHOME: selector="#paragraph.post_content"; break;
            case THEPAPER: selector="#quiet-paper-body"; break;
            default: selector="article";
        }
        Set<String> seen=new HashSet<>();
        boolean hasVideo=mainVideo;
        // Hupu's main video is a sibling of the text region, not inside it.
        // Inspect only observed main-post containers, excluding recommendation players.
        if(source==Source.HUPU)for(Element main:d.select("[class*='post-content_main-post-info']")) {
            if(main.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container")!=null)continue;
            Element owned=main.clone();
            owned.select("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container,.reply-list-wrapper").remove();
            if(!owned.select("video").isEmpty())hasVideo=true;
            if(explicitReading&&mainVideo)ReaderMedia.capture(result,owned);
        }
        for(Element root:d.select(selector)) {
            boolean nested=false;
            for(Element p:root.parents()) if(p.is(selector)) { nested=true; break; }
            if(nested) continue;
            if(root.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.feed-ad-container")!=null)continue;
            boolean primaryVideo=explicitReading&&mainVideo&&(source!=Source.HUPU||root.closest(".reply-list-wrapper")==null);
            if(VideoPolicy.videoSection(source,root)&&!primaryVideo){
                result.filteredSectionIds.add(sectionId(source,root));
                result.filteredVideos=result.filteredSectionIds.size();continue;
            }
            ReaderMedia.capture(result,root);
            String continuation=source==Source.WEIBO?WeiboPost.continuation(url,root):"";
            Element clean=root.clone();
            if(!continuation.isEmpty())for(Element a:clean.select("a[href]"))
                if(WeiboPost.fulltextLabel(a)&&WeiboPost.same(continuation,UrlPolicy.normalize(url,a.attr("href"))))a.remove();
            clean.select("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container").remove();
            if(VideoPolicy.player(clean)||clean.text().contains("视频加载中"))hasVideo=true;
            ReaderMedia.position(result,clean);
            clean.select(VideoPolicy.PLAYERS).remove();
            clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.RichContent-actions,.feed-ad-container").remove();
            for(Element leaf:clean.getAllElements())if(leaf.children().isEmpty()&&leaf.text().matches("视频加载中[.…。]*"))leaf.remove();
            if(source==Source.V2EX)stripRenderedImageSyntax(clean,url);
            List<Element> photos=source==Source.WEIBO?mobileOriginalPhotos(root,url):java.util.Collections.emptyList();
            List<String> photoUrls=new ArrayList<>();for(Element img:photos)photoUrls.add(imageSource(img,url));
            Element weiboAuthor=source==Source.WEIBO?WeiboPost.author(root):null;
            String key=(source==Source.V2EX?sectionId(source,root)+"|":"")+(weiboAuthor==null?"":weiboAuthor.text()+"|")+clean.text()+clean.select("img").eachAttr("src")+clean.select("quiet-reader-media").eachAttr("data-key")+continuation+photoUrls;
            if(!seen.add(key)||key.trim().isEmpty()) continue;
            if(!result.blocks.isEmpty()) result.blocks.add(new Block("separator",""));
            int start=result.blocks.size();
            StringBuilder buffer=new StringBuilder();
            List<InlineImage> inline=new ArrayList<>();
            List<InlineLink> links=new ArrayList<>();
            visit(clean,result,buffer,inline,links,url); flush(result,buffer,inline,links);
            for(Element img:photos){visit(img,result,buffer,inline,links,url);flush(result,buffer,inline,links);}
            if(result.blocks.subList(start,result.blocks.size()).stream().noneMatch(b->!b.type.equals("video")))result.blocks.subList(start,result.blocks.size()).clear();
            if(result.blocks.size()>start) {
                boolean answer=source==Source.ZHIHU&&root.closest(".AnswerItem")!=null;
                Element container=answer?root.closest(".AnswerItem"):root;
                String id=continuation.isEmpty()?sectionId(source,root,photoUrls):"weibo-"+WeiboPost.id(continuation);
                Element name=container.selectFirst(".AuthorInfo-name,.author-name");
                if(source==Source.WEIBO)name=weiboAuthor;
                if(source==Source.V2EX&&root.is(".reply_content")){Element reply=root.closest(".cell");if(reply!=null)name=reply.selectFirst("strong a[href*=/member/]");}
                String label=answer?"回答":source==Source.ZHIHU?(root.is(".QuestionRichText")?"问题补充":"回答"):source==Source.V2EX?(root.is(".reply_content")?"回复":"正文"):source==Source.HUPU?"楼层":source==Source.WEIBO?"微博":"正文";
                if(source==Source.HUPU)label=root.closest(".reply-list-wrapper")!=null?"回复":root.closest("[class*='post-content_main-post-info']")!=null?"主帖":"楼层";
                Section section=new Section(id,label+(name==null?"":" · "+name.text()),answer||label.equals("回答"));
                section.continuationUrl=continuation;
                section.blocks.addAll(result.blocks.subList(start,result.blocks.size()));
                int sameSupplement=-1;if(source==Source.ZHIHU&&!section.answer&&root.is(".QuestionRichText")&&id.startsWith("zhihu-question-"))for(int i=0;i<result.sections.size();i++)if(result.sections.get(i).id.equals(id)){sameSupplement=i;break;}
                if(sameSupplement<0)result.sections.add(section);else if(section.blocks.stream().mapToInt(b->b.value.length()).sum()>result.sections.get(sameSupplement).blocks.stream().mapToInt(b->b.value.length()).sum())result.sections.set(sameSupplement,section);
            }
        }
        if(source==Source.ZHIHU){result.blocks.clear();for(Section section:result.sections)result.blocks.addAll(section.blocks);}
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
        if(source==Source.ZHIHU && !result.blocks.isEmpty()) result.notice=(result.notice.isEmpty()?"":result.notice+" ")+"首条回答默认展开，后续可按需展开。下滑继续加载；当前内容不代表完整回答列表。";
        if(source==Source.WEIBO && !result.blocks.isEmpty()) {
            result.notice=VideoPolicy.weiboTopic(url)?"仅展示当前页面已加载的微博；不是话题下全部内容。":"仅展示来源页已加载的微博正文。";
            if(result.sections.stream().anyMatch(s->!s.continuationUrl.isEmpty()))result.notice+=" 部分内容是摘要，展开后可继续读取原帖。";
        }
        if(source==Source.HUPU&&!result.blocks.isEmpty())
            result.notice=(result.notice.isEmpty()?"":result.notice+" ")+"仅展示当前页已加载的帖子和回复；未加载部分不会显示，不代表全部楼层。";
        result.unsupportedVideo=hasVideo;
        if(hasVideo)result.notice=(result.notice.isEmpty()?"":result.notice+" ")+(ReaderMedia.allowed(result.url,result.videoUrl)?"原文包含视频，主动点击后才加载；播放受限时可在来源页查看。":"原文包含视频；当前整理文字和图片，视频可在来源页观看。");
        if(result.filteredVideos>0)result.notice=(result.notice.isEmpty()?"":result.notice+" ")+VideoPolicy.filteredNotice(result.filteredVideos);
        if(!result.hasContent()&&result.related.isEmpty()&&!hasVideo&&result.filteredVideos==0) result.notice="尚未取得可阅读正文，可能需要登录、页面尚未加载或此类型暂不支持。";
        DiscussionReplyEvidence.capture(source,d,result,System.currentTimeMillis());
        return result;
    }
    static boolean appendRegion(Document result,Element root,String id,String label,boolean answer,String base){
        Element clean=root.clone();
        boolean media=!clean.select(VideoPolicy.PLAYERS).isEmpty();
        ReaderMedia.position(result,clean);
        clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.comment-content,.reply-list,.tts-broadcast,"+VideoPolicy.PLAYERS).remove();
        int start=result.blocks.size();
        StringBuilder text=new StringBuilder();List<InlineImage> images=new ArrayList<>();List<InlineLink> links=new ArrayList<>();
        visit(clean,result,text,images,links,base);flush(result,text,images,links);
        result.unsupportedVideo|=media;
        if(result.blocks.subList(start,result.blocks.size()).stream().noneMatch(b->!b.type.equals("video")))result.blocks.subList(start,result.blocks.size()).clear();
        if(result.blocks.size()==start)return false;
        Section section=new Section(id,label,answer);section.blocks.addAll(result.blocks.subList(start,result.blocks.size()));result.sections.add(section);return true;
    }
    private static String sectionId(Source source,Element root){
        return sectionId(source,root,java.util.Collections.emptyList());
    }
    private static String sectionId(Source source,Element root,List<String> originalPhotos){
        if(source==Source.ZHIHU&&root.is(".QuestionRichText")){String question=ZhihuAnswers.questionId(root.baseUri());if(!question.isEmpty())return "zhihu-question-"+question;}
        if(source==Source.V2EX){
            if(root.is(".topic_content")){int index=root.ownerDocument().select(".topic_content").indexOf(root);return index<=0?"v2ex-main":"v2ex-supplement-"+index;}
            Element reply=root.closest(".cell");
            if(reply!=null){if(reply.id().matches("r_[0-9]+"))return "v2ex-"+reply.id();Element author=reply.selectFirst("strong a[href*=/member/]"),floor=reply.selectFirst(".no");String position=floor==null?root.baseUri()+"|"+root.ownerDocument().select(".reply_content").indexOf(root):floor.text();return "v2ex-reply-"+app.quietreader.discovery.HotCandidate.hash((author==null?"":author.text())+"|"+position+"|"+root.text()).substring(0,24);}
        }
        Element container=VideoPolicy.owner(source,root);
        String id="";try{id=new JSONObject(container.attr("data-zop")).optString("itemId","");}catch(Exception ignored){}
        if(id.isEmpty())id=container.id();
        if(id.isEmpty()){
            Element clean=root.clone();clean.select("script,style,iframe,video,audio,form,button,input,textarea,nav,aside,.recommend,.advertisement,.ad,.RichContent-actions,.feed-ad-container").remove();
            String key=clean.text()+clean.select("img").eachAttr("src");
            if(source==Source.WEIBO){Element author=WeiboPost.author(root);if(author!=null)key=author.text()+"|"+key;}
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
    static String imageSource(Element image,String base){
        // A nonempty data/blob placeholder is not a usable address. Continue
        // through the observed lazy attributes instead of discarding the image.
        for(String attribute:new String[]{"src","data-src","data-original"}){
            String src=UrlPolicy.normalize(base,image.attr(attribute).trim());if(!src.isEmpty())return src;
        }
        return "";
    }
    /** V2EX auto-embeds pasted image URLs even inside literal ![](URL).
     * Remove only the empty wrapper immediately surrounding that same image,
     * on our extraction clone. Never parse Markdown or change source markup. */
    private static void stripRenderedImageSyntax(Element root,String base){
        for(Element image:root.select("img")){
            if(image.closest("pre,code,kbd,samp")!=null)continue;
            String src=imageSource(image,base);if(src.isEmpty())continue;
            Element host=image,parent=image.parent();
            if(parent!=null&&parent.normalName().equals("a")){
                if(parent.childrenSize()!=1||parent.child(0)!=image||!parent.text().isEmpty()||!src.equals(UrlPolicy.normalize(base,parent.attr("href"))))continue;
                host=parent;
            }
            if(!(host.previousSibling() instanceof TextNode)||!(host.nextSibling() instanceof TextNode))continue;
            TextNode before=(TextNode)host.previousSibling(),after=(TextNode)host.nextSibling();
            String leading=before.getWholeText(),trailing=after.getWholeText();
            java.util.regex.Matcher open=HYBRID_IMAGE_OPEN.matcher(leading),close=HYBRID_IMAGE_CLOSE.matcher(trailing);
            if(open.find()&&close.find()){
                before.text(leading.substring(0,open.start()));
                after.text(trailing.substring(close.end()));
            }
        }
    }
    private static void visit(Node node,Document result,StringBuilder buffer,List<InlineImage> inline,List<InlineLink> links,String base) {
        if(node instanceof TextNode) { buffer.append(((TextNode)node).text()); return; }
        if(!(node instanceof Element)) return;
        Element e=(Element)node;
        if(e.tagName().equals("quiet-reader-media")){
            String key=e.attr("data-key");
            if(ReaderMedia.index(result,key)>=0){flush(result,buffer,inline,links);result.blocks.add(new Block("video",key));}
            return;
        }
        if(e.tagName().equals("a")&&!e.text().isEmpty()&&e.select("img,div,p,br").isEmpty()){
            String url=UrlPolicy.normalize(base,e.attr("href"));
            if(ReaderLinks.allowed(url)){
                int start=buffer.length();buffer.append(e.text());links.add(new InlineLink(start,buffer.length(),url));return;
            }
        }
        if(e.tagName().equals("img")) {
            String src=imageSource(e,base);
            if(e.hasClass("emoji-img")||e.hasClass("BDE_Smiley")||e.closest(".pb-emot")!=null){
                String alt=e.attr("alt").trim();if(alt.isEmpty())alt=e.attr("title").trim();
                // There is no image to attach to this span. Keep a specific
                // source label when available; never leak an object glyph.
                if(src.isEmpty()){buffer.append(alt);return;}
                int start=buffer.length();buffer.append(alt.isEmpty()?"\uFFFC":alt);
                inline.add(new InlineImage(start,buffer.length(),src,alt.isEmpty()?"表情":alt));
                return;
            }
            flush(result,buffer,inline,links);
            if(!src.isEmpty()){
                Block image=new Block("image",src);
                try{image.imageWidth=Integer.parseInt(e.attr("width"));image.imageHeight=Integer.parseInt(e.attr("height"));}catch(NumberFormatException ignored){}
                if(image.imageWidth<=0||image.imageHeight<=0){try{image.imageWidth=Integer.parseInt(e.attr("data-rawwidth"));image.imageHeight=Integer.parseInt(e.attr("data-rawheight"));}catch(NumberFormatException ignored){}}
                result.blocks.add(image);
            }
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
