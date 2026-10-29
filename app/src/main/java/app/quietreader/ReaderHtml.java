package app.quietreader;

import static app.quietreader.Models.*;
import java.util.*;

/** Escaped own layout, without scripts or a native bridge. */
public final class ReaderHtml {
    public static final String ACTION="https://quiet-reader.invalid/";
    private ReaderHtml() {}
    public static String escape(String v){return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");}
    public static String render(Document d,Source s,int font){return render(d,s,font,false,Collections.emptySet(),false);}
    public static String render(Document d,Source source,int font,boolean dark,Set<String> expanded,boolean loading){
        return render(d,source,font,dark,expanded,loading,font);
    }
    public static String render(Document d,Source source,int font,boolean dark,Set<String> expanded,boolean loading,int inlineFont){
        return render(d,source,font,dark,expanded,loading,inlineFont,ReaderStyle.defaults());
    }
    static String render(Document d,Source source,int font,boolean dark,Set<String> expanded,boolean loading,int inlineFont,ReaderStyle style){
        StringBuilder h=new StringBuilder("<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='no-referrer'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src https:; media-src https://quiet-reader.invalid; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><style>");
        h.insert(h.indexOf("<style>"),"<meta name='quiet-reader-source' content='"+escape(d.url)+"'>");
        // Reserve room in CSS; WebView's INSIDE_INSET shrinks the visual viewport
        // without consistently shrinking layout width, producing a horizontal scrollbar.
        h.append(dark?":root{color-scheme:dark;--bg:#121316;--card:#1b1d22;--ink:#cdcfd5;--muted:#9b9da7;--green:#77b8eb;--line:#2b2d33;--warn:#24231f}":":root{color-scheme:light;--bg:#fafafa;--card:#f1f2f4;--ink:#30323a;--muted:#696c76;--green:#247bc1;--line:#e0e1e5;--warn:#f5f1e9}");
        h.append("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:system-ui,sans-serif}main{max-width:680px;margin:auto;padding:12px 2px 40px}h1{font-size:27px;line-height:1.5;margin:0 0 16px;overflow-wrap:anywhere}.meta,.end{font-size:12px;color:var(--muted);line-height:1.7;margin-bottom:22px}p{font-size:").append(Math.max(16,Math.min(28,font))).append("px;line-height:1.9;margin:0 0 22px;overflow-wrap:anywhere;white-space:pre-wrap}figure{margin:20px 0}img{display:block;max-width:100%;height:auto;margin:auto;border-radius:10px;min-height:30px}figcaption{font-size:11px;color:var(--muted);text-align:center;margin-top:8px}.notice{padding:12px;border-radius:12px;background:var(--warn);font-size:13px;line-height:1.7;margin-bottom:22px}hr{border:0;border-top:1px solid var(--line);margin:25px 0}a{color:var(--green);text-decoration:none}.action{display:block;padding:14px;border-radius:12px;background:var(--card);border:1px solid var(--line);font-size:14px;line-height:1.6;margin:14px 0}.part{border-bottom:1px solid var(--line);padding-bottom:12px;margin-bottom:18px}.preview{font-size:16px;line-height:1.8;color:var(--muted);margin:12px 0}.end{text-align:center;margin-top:30px}</style></head><body><main>");
        h.append("<style>h1{font-size:22px;font-weight:600;line-height:1.5;margin-bottom:12px}main{padding:12px 0 28px}p{line-height:1.8;margin-bottom:18px}.meta{margin-bottom:18px}.action{padding:10px 0;background:transparent;border:0;border-radius:0;min-height:48px;margin:8px 0}.notice{border-radius:4px;padding:10px;font-size:12px}.notice summary{min-height:48px;padding:12px 0;cursor:pointer}.part{padding-bottom:8px;margin-bottom:14px}img{border-radius:4px}.preview{line-height:1.7}.end{margin-top:22px}</style>");
        h.append("<style>h1{font-size:19px;line-height:1.4;margin-bottom:6px}.meta{font-size:11px;margin-bottom:8px}.notice{padding:7px 9px;margin-bottom:10px;font-size:11px;line-height:1.5}.section-nav{display:flex;gap:18px;font-size:13px;margin:0 0 8px}.section-nav a{display:inline-flex;align-items:center;min-height:48px;min-width:48px;max-width:100%;padding:8px 0}.content-link{overflow-wrap:anywhere;text-decoration:underline;text-underline-offset:3px}").append(style.css(dark)).append("</style>");
        List<Section> sections=sections(d);
        Set<String> positioned=new HashSet<>();for(Section part:sections)for(Block block:part.blocks)if(block.type.equals("video"))positioned.add(block.value);
        h.append("<style>.inline-media{margin:12px 0}.inline-media .media-stage{position:relative;aspect-ratio:16/9;max-height:60vh;background:var(--card);border-radius:4px;overflow:hidden}.inline-media video{display:none;width:100%;height:100%;aspect-ratio:16/9;background:#080909;border-radius:4px}.inline-media .media-cover{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;margin:0;min-height:0}.inline-media .media-actions{display:flex;gap:16px;flex-wrap:wrap;font-size:13px}.inline-media a{display:inline-flex;align-items:center;min-height:48px;min-width:48px}.inline-media .media-play{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:12px;color:#fff;background:#0003;border-radius:4px;font-size:16px}.inline-media .media-status{font-size:12px;color:var(--muted)}</style>");
        h.append("<h1>").append(escape(d.title)).append("</h1>");
        if(!d.byline.isEmpty())h.append("<div class='meta author'>").append(escape(d.byline)).append("</div>");
        if(!d.hasContent()&&!d.notice.isEmpty()&&d.moreStatus.isEmpty())h.append("<div class='notice' role='status'>").append(escape(d.notice)).append("</div>");
        h.append("<style>.preview{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}img.emoji{display:inline-block;width:1.35em;height:1.35em;min-height:0;max-width:none;object-fit:contain;vertical-align:-.25em;margin:0 .08em;border-radius:0}</style>");
        // Android textZoom changes glyphs but not image em boxes. Size inline images explicitly.
        double emojiSize=Math.max(16,Math.min(28,inlineFont))*1.35;
        h.append("<style>img.emoji{width:").append(emojiSize).append("px;height:").append(emojiSize).append("px}.part p.emoji-line{line-height:1.55;margin-bottom:4px}</style>");
        h.append("<style>main{padding-right:12px}figcaption,.meta,.action,.notice,.end,.discussion-heading{overflow-wrap:anywhere}.section-nav{flex-wrap:wrap}.part figure>a{display:block;background:var(--card);border-radius:4px;outline:1px solid var(--line)}.image-open{display:inline-flex;align-items:center;justify-content:center;min-width:48px;min-height:48px;padding:8px 12px;font-size:13px}figure.image-unavailable{margin:8px 0}figure.image-unavailable>a{display:none}figure.image-unavailable figcaption{margin-top:0;text-align:left}</style>");
        boolean hasDiscussion=sections.stream().anyMatch(ReaderHtml::discussion);
        h.append("<style>.part.discussion.nested-reply{margin-left:14px;border-left:2px solid var(--line);border-radius:6px}.discussion .section-label{font-size:11px;line-height:1.5;margin-bottom:6px}</style>");
        h.append("<style>.discussion-heading{font-size:17px;margin:28px 0 16px}.part.discussion{background:var(--card);padding:12px;border:1px solid var(--line);border-radius:12px}.part.discussion>:last-child{margin-bottom:0}.media-fallback{aspect-ratio:16/9;display:flex;flex-direction:column;justify-content:center;align-items:center;text-align:center;gap:10px;background:var(--card);border:1px solid var(--line);border-radius:12px;margin:18px 0;padding:20px}.media-fallback strong{font-size:17px}.media-fallback p{font-size:13px;color:var(--muted);margin:0}.media-fallback a{padding:10px;min-height:48px}</style>");
        h.append("<div class='reader-content' data-reader-layout='").append(d.videoTopic||(d.unsupportedVideo&&positioned.isEmpty())?"video":hasDiscussion?"discussion":"article").append("'>");
        if(d.unsupportedVideo&&ReaderMedia.attachments(d).stream().anyMatch(v->!positioned.contains(ReaderMedia.key(v)))){
            h.append("<style>.media-fallback[data-cover]{aspect-ratio:auto;padding:0;overflow:hidden}.media-fallback .media-poster{width:100%;aspect-ratio:16/9;object-fit:contain;margin:0;border-radius:0;min-height:0}.media-caption{padding:14px}.media-caption p{margin:8px 0 0}</style>");
            List<VideoAttachment> videos=ReaderMedia.attachments(d);
            boolean multiple=videos.size()>1;
            if(multiple)h.append("<style>.media-fallback.media-compact{aspect-ratio:auto;padding:10px;display:grid;grid-template-columns:88px minmax(0,1fr);gap:12px;text-align:left;margin:10px 0}.media-compact:not([data-cover]){grid-template-columns:1fr}.media-compact .media-poster{width:88px;height:50px;object-fit:contain;border-radius:6px}.media-compact .media-caption{padding:0;min-width:0}.media-compact strong{font-size:14px}.media-compact a{display:block;padding:8px 0;min-height:48px}.media-compact .media-unavailable{font-size:12px;color:var(--muted);margin-top:6px}</style><p class='meta'>视频附件 · 不会自动加载或播放</p>");
            for(int mediaIndex=0;mediaIndex<videos.size();mediaIndex++){
            VideoAttachment video=videos.get(mediaIndex);boolean poster=UrlPolicy.https(video.poster);
            if(positioned.contains(ReaderMedia.key(video)))continue;
            h.append("<figure class='media-fallback").append(multiple?" media-compact":"").append("'").append(poster?" data-cover":"").append(" aria-label='视频内容'>");
            if(poster)h.append("<img class='media-poster' src='").append(escape(video.poster)).append("' alt='视频封面'>");
            h.append("<div class='media-caption'><strong>").append(multiple?"视频 "+(mediaIndex+1):"原文包含视频").append("</strong>");
            if(!multiple)h.append("<p>当前保留已获取的图文，视频不会自动加载或播放。</p>");
            if(ReaderMedia.allowed(d.url,video.url))h.append("<a class='media-play' href='").append(ACTION).append(videos.size()>1?"play?index="+mediaIndex:"play").append("'>播放视频 →</a>");
            else if(multiple)h.append("<div class='media-unavailable'>需在来源页观看</div>");
            if(!multiple)h.append("<a href='").append(ACTION).append("login'>在来源页查看视频 →</a>");
            h.append("</div></figure>");
            }
            if(multiple)h.append("<a class='action' href='").append(ACTION).append("login'>在来源页查看视频 →</a>");
        }
        if(d.videos.size()>=ReaderMedia.MAX_ATTACHMENTS)h.append("<p class='meta'>本页最多整理前16个视频附件，其余请在来源页查看。</p>");
        boolean discussionHeading=false;
        int index=0;
        for(Section part:sections){
            StringBuilder text=new StringBuilder();int imageCount=0;for(Block b:part.blocks){if(b.type.equals("text"))text.append(b.value).append(' ');if(b.type.equals("image"))imageCount++;}
            boolean entry=collectionEntry(part);
            // Adding a comment must not suddenly collapse an otherwise readable body.
            // Short replies are already complete cards; only substantial content needs a toggle.
            boolean independentPost=part.label.equals("微博")||part.label.startsWith("微博 · ");
            boolean fold=!entry&&(part.answer||(independentPost&&sections.size()>1)||text.length()>900||imageCount>1);
            boolean open=!fold||expanded.contains(part.id);
            boolean comment=discussion(part);
            if(comment&&!discussionHeading){h.append("<h2 class='discussion-heading'>讨论</h2>");discussionHeading=true;}
            h.append("<section class='").append(comment?part.nestedReply?"part discussion nested-reply":"part discussion":"part").append("' id='part-").append(index).append("' data-section-label='").append(escape(part.label)).append("'>");
            String label=sectionLabel(part.label,source);
            if(!part.answer&&(label.equals("回复")||label.equals("评论")))label="";
            if(comment&&!part.answer){
                if(label.startsWith("回复 · ")||label.startsWith("评论 · "))label=label.substring(5);
                if(part.nestedReply)label=label.isEmpty()?"楼内回复":label+" · 楼内回复";
                if(!part.replyToAuthor.isEmpty())label+=(label.isEmpty()?"":" · ")+"回复 "+part.replyToAuthor;
            }
            if(fold){
                if(!label.isEmpty())h.append("<div class='meta section-label'>").append(escape(label)).append("</div>");
                h.append("<a class='action' href='").append(ACTION).append("toggle?index=").append(index).append("' aria-expanded='").append(open).append("'>").append(index+1).append(open?" · 收起 ↑":" · 展开阅读 ↓").append("</a>");
            }
            else if(!label.isEmpty())h.append("<div class='meta section-label'>").append(escape(label)).append("</div>");
            if(open){
                int blockIndex=0;for(Block b:part.blocks)block(h,b,anchorId(part.id,blockIndex++),d);
                if(WeiboPost.canContinue(source,d.url,part))h.append("<a class='action' href='").append(ACTION).append("post?index=").append(index).append("'>继续读取原帖 →</a>");
                if(!entry)navigation(h,index,sections);}
            else {h.append("<p class='preview'>").append(escape(text.substring(0,Math.min(130,text.length())))).append(text.length()>130?"…":"").append("</p>");if(imageCount>0)h.append("<div class='meta'>包含 ").append(imageCount).append(" 张图片 · 展开后查看</div>");}
            h.append("</section>");index++;
        }
        if(ReaderContinuation.question(source,d)||DiscussionPages.usesStream(source,d))continuation(h,d,source,loading);
        h.append("</div>");
        if(!DiscussionPages.usesStream(source,d)&&UrlPolicy.sameThread(source,d.url,d.nextUrl))h.append("<a class='action' href='").append(escape(d.nextUrl)).append("'>继续阅读下一页 →</a>");
        return h.append("</main></body></html>").toString();
    }
    private static String sectionLabel(String label,Source source){
        if(label.equals(source.label)||Set.of("正文","内容","话题介绍","视频简介","搜索结果 · 来源摘要","相关帖子 · 摘要").contains(label))return "";
        return label.startsWith(source.label+" · ")?label.substring(source.label.length()+3):label;
    }
    private static void continuation(StringBuilder h,Document d,Source source,boolean loading){
        String state=d.moreStatus;
        h.append("<div id='reader-continuation' class='continuation-status"+(state.equals("error")||state.equals("login")||state.equals("limit")?" notice":"")+"' data-continuation='").append(loading?"loading":ReaderContinuation.automatic(source,d)?"ready":state.isEmpty()?"end":escape(state)).append("'");
        if(loading)h.append(" role='status'>正在加载…");
        else if(state.equals("error")||state.equals("login")||state.equals("limit")){
            h.append(" role='status'>").append(escape(d.notice));
            h.append("<a class='action' href='").append(ACTION).append(state.equals("error")?"more'>重试":"login'>在来源页继续阅读").append("</a>");
        }else h.append(" aria-hidden='true'>");
        h.append("</div>");
    }
    static boolean discussion(Section part){return part.answer||part.label.startsWith("回复")||part.label.startsWith("评论");}
    static boolean collectionEntry(Section part){return part.label.equals("搜索结果 · 来源摘要")||part.label.equals("相关帖子 · 摘要");}
    static List<Section> sections(Document d){
        if(!d.sections.isEmpty())return d.sections;
        if(d.blocks.isEmpty())return Collections.emptyList();
        List<Section> parts=new ArrayList<>();Section part=new Section("legacy-0","正文",false);parts.add(part);
        for(Block b:d.blocks){if(b.type.equals("separator")){part=new Section("legacy-"+parts.size(),"内容",false);parts.add(part);}else part.blocks.add(b);}return parts;
    }
    static void expandPrimary(Document doc,Set<String> expanded){
        List<Section> parts=sections(doc);if(parts.isEmpty())return;Section first=parts.get(0);
        if(!first.answer&&(first.label.startsWith("主帖")||first.label.startsWith("话题介绍")||first.label.equals("正文")||first.label.startsWith("正文 · ")||first.label.equals("问题补充")||first.label.equals("视频简介")))expanded.add(first.id);
        if(doc.url.matches("https://[^/]*zhihu.com/question/.*"))
            for(Section part:parts)if(part.answer){expanded.add(part.id);break;}
    }
    private static void navigation(StringBuilder h,int index,List<Section> parts){
        if(index+1>=parts.size()||!parts.get(index+1).answer)return;
        // Answers benefit from opening the next destination; ordinary short comments
        // follow naturally in the scroll and should not each acquire a next button.
        h.append("<nav class='section-nav' aria-label='内容跳转'>");
        Section next=parts.get(index+1);
        h.append("<a href='").append(ACTION).append("jump?index=").append(index+1).append("'>").append(next.answer?"下一条回答 ↓":next.label.startsWith("回复")?"下一条回复 ↓":"下一段内容 ↓").append("</a>");
        h.append("</nav>");
    }
    static String anchorId(String section,int block){return "reading-"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(section.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+block;}
    // Keep source paragraphs/anchors intact; only remove prose-sized gaps from image-only emoji lines.
    private static boolean emojiLine(Block block){
        if(block.inlineImages.isEmpty()||!block.inlineLinks.isEmpty())return false;
        int at=0;
        for(InlineImage image:block.inlineImages){
            if(image.start<at||image.end<=image.start||image.end>block.value.length()||!UrlPolicy.https(image.url))return false;
            for(int n=at;n<image.start;n++)if(!Character.isWhitespace(block.value.charAt(n)))return false;
            at=image.end;
        }
        for(int n=at;n<block.value.length();n++)if(!Character.isWhitespace(block.value.charAt(n)))return false;
        return true;
    }
    private static void block(StringBuilder h,Block b,String anchor,Document doc){
        if(b.type.equals("text")){
            h.append(emojiLine(b)?"<p class='emoji-line' data-reading-anchor id='":"<p data-reading-anchor id='").append(anchor).append("'>");
            h.append(ReaderLinks.render(b)).append("</p>");
        }
        else if(b.type.equals("image")&&UrlPolicy.https(b.value)){
            h.append("<figure data-reading-anchor id='").append(anchor).append("'><a href='").append(escape(b.value)).append("'><img loading='lazy' ");
            if(b.imageWidth>0&&b.imageHeight>0&&b.imageWidth<=20000&&b.imageHeight<=100000)
                h.append("width='").append(b.imageWidth).append("' height='").append(b.imageHeight).append("' style='width:100%;aspect-ratio:").append(b.imageWidth).append("/").append(b.imageHeight).append(";' ");
            else h.append("style='width:100%;height:240px;object-fit:contain;background:var(--card)' ");
            h.append("src='").append(escape(b.value)).append("' alt='正文图片'></a><figcaption><a class='image-open' href='").append(escape(b.value)).append("' aria-label='查看正文图片大图，加载失败可在大图中重试'>查看大图</a></figcaption></figure>");
        }
        else if(b.type.equals("video")){
            int index=ReaderMedia.index(doc,b.value);if(index<0)return;
            VideoAttachment video=ReaderMedia.attachments(doc).get(index);
            h.append("<figure class='inline-media' data-reading-anchor id='").append(anchor).append("' data-media-key='").append(b.value).append("'>");
            if(ReaderMedia.allowed(doc.url,video.url)){
                h.append("<div class='media-stage'><video playsinline preload='none' aria-label='正文视频'");
                if(ReaderMedia.allowedPoster(doc.url,video.poster))h.append(" poster='").append(escape(video.poster)).append("'");
                h.append("></video>");
                if(ReaderMedia.allowedPoster(doc.url,video.poster))h.append("<img class='media-cover' loading='lazy' src='").append(escape(video.poster)).append("' alt='视频封面'>");
                h.append("<a class='media-play' aria-label='播放正文视频' href='").append(ACTION).append("play?index=").append(index).append("&amp;anchor=").append(anchor).append("'>▶ 播放视频</a></div><div class='media-actions'>");
            }else {
                if(ReaderMedia.allowedPoster(doc.url,video.poster))h.append("<div class='media-stage'><img class='media-cover' loading='lazy' src='").append(escape(video.poster)).append("' alt='视频封面'></div>");
                h.append("<div class='media-actions'>");
            }
            h.append("<a href='").append(ACTION).append("login'>来源页视频 ↗</a></div><div class='media-status' role='status'></div></figure>");
        }
        else if(b.type.equals("separator"))h.append("<hr>");
    }
}
