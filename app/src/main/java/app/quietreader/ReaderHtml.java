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
        StringBuilder h=new StringBuilder("<!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'><meta name='referrer' content='no-referrer'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; img-src https:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'\"><style>");
        h.insert(h.indexOf("<style>"),"<meta name='quiet-reader-source' content='"+escape(d.url)+"'>");
        h.append(dark?":root{color-scheme:dark;--bg:#1f2025;--card:#282a31;--ink:#cdcfd5;--muted:#9b9da7;--green:#77b8eb;--line:#34363e;--warn:#2c2b28}":":root{color-scheme:light;--bg:#fafafa;--card:#f1f2f4;--ink:#30323a;--muted:#696c76;--green:#247bc1;--line:#e0e1e5;--warn:#f5f1e9}");
        h.append("*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink);font-family:system-ui,sans-serif}main{max-width:680px;margin:auto;padding:12px 2px 40px}h1{font-size:27px;line-height:1.5;margin:0 0 16px;overflow-wrap:anywhere}.meta,.end{font-size:12px;color:var(--muted);line-height:1.7;margin-bottom:22px}p{font-size:").append(Math.max(16,Math.min(28,font))).append("px;line-height:1.9;margin:0 0 22px;overflow-wrap:anywhere;white-space:pre-wrap}figure{margin:20px 0}img{display:block;max-width:100%;height:auto;margin:auto;border-radius:10px;min-height:30px}figcaption{font-size:11px;color:var(--muted);text-align:center;margin-top:8px}.notice{padding:12px;border-radius:12px;background:var(--warn);font-size:13px;line-height:1.7;margin-bottom:22px}hr{border:0;border-top:1px solid var(--line);margin:25px 0}a{color:var(--green);text-decoration:none}.action{display:block;padding:14px;border-radius:12px;background:var(--card);border:1px solid var(--line);font-size:14px;line-height:1.6;margin:14px 0}.part{border-bottom:1px solid var(--line);padding-bottom:12px;margin-bottom:18px}.preview{font-size:16px;line-height:1.8;color:var(--muted);margin:12px 0}.end{text-align:center;margin-top:30px}</style></head><body><main>");
        h.append("<style>h1{font-size:22px;font-weight:600;line-height:1.5;margin-bottom:12px}main{padding:12px 0 28px}p{line-height:1.8;margin-bottom:18px}.meta{margin-bottom:18px}.action{padding:10px 0;background:transparent;border:0;border-radius:0;min-height:44px;margin:8px 0}.notice{border-radius:4px;padding:10px;font-size:12px}.part{padding-bottom:8px;margin-bottom:14px}img{border-radius:4px}.preview{line-height:1.7}.end{margin-top:22px}</style>");
        h.append("<h1>").append(escape(d.title)).append("</h1><div class='meta'>").append(escape(source.label)).append(" · 内容来自原作者</div>");
        if(!d.byline.isEmpty())h.append("<div class='meta'>作者：").append(escape(d.byline)).append("</div>");
        if(!d.notice.isEmpty())h.append("<div class='notice'>").append(escape(d.notice)).append("</div>");
        h.append("<style>.preview{display:-webkit-box;-webkit-line-clamp:3;-webkit-box-orient:vertical;overflow:hidden}img.emoji{display:inline-block;width:1.35em;height:1.35em;min-height:0;max-width:none;object-fit:contain;vertical-align:-.25em;margin:0 .08em;border-radius:0}</style>");
        // Android textZoom changes glyphs but not image em boxes. Size inline images explicitly.
        double emojiSize=Math.max(16,Math.min(28,inlineFont))*1.35;
        h.append("<style>img.emoji{width:").append(emojiSize).append("px;height:").append(emojiSize).append("px}</style>");
        List<Section> sections=sections(d);
        int index=0;
        for(Section part:sections){
            StringBuilder text=new StringBuilder();int imageCount=0;for(Block b:part.blocks){if(b.type.equals("text"))text.append(b.value).append(' ');if(b.type.equals("image"))imageCount++;}
            boolean fold=part.answer||sections.size()>1||text.length()>900||imageCount>1;
            boolean open=!fold||expanded.contains(part.id);
            h.append("<section class='part' id='part-").append(index).append("' data-section-label='").append(escape(part.label)).append("'>");
            if(fold)h.append("<a class='action' href='").append(ACTION).append("toggle?index=").append(index).append("'>").append(escape(part.label)).append(" · ").append(index+1).append(open?" · 收起 ↑":" · 展开阅读 ↓").append("</a>");
            else if(source==Source.HUPU&&(part.label.startsWith("主帖")||part.label.startsWith("回复")))h.append("<div class='meta section-label'>").append(escape(part.label)).append("</div>");
            if(open){int blockIndex=0;for(Block b:part.blocks)block(h,b,anchorId(part.id,blockIndex++));
                if(WeiboPost.canContinue(source,d.url,part))h.append("<a class='action' href='").append(ACTION).append("post?index=").append(index).append("'>继续读取原帖 →</a>");
                if(fold)h.append("<a class='action' href='").append(ACTION).append("toggle?index=").append(index).append("'>收起这段内容 ↑</a>");}
            else {h.append("<p class='preview'>").append(escape(text.substring(0,Math.min(130,text.length())))).append(text.length()>130?"…":"").append("</p>");if(imageCount>0)h.append("<div class='meta'>包含 ").append(imageCount).append(" 张图片 · 展开后查看</div>");}
            h.append("</section>");index++;
        }
        if(source==Source.ZHIHU&&d.url.matches("https://[^/]*zhihu.com/question/\\d+.*"))h.append("<a class='action' href='").append(ACTION).append("more'>").append(loading?"正在加载下一批回答…":"加载下一批回答 ↓").append("</a>");
        h.append("<div class='end'>— 当前已提取内容结束 —</div>");
        if(UrlPolicy.sameThread(source,d.url,d.nextUrl))h.append("<a class='action' href='").append(escape(d.nextUrl)).append("'>继续阅读下一页 →</a>");
        return h.append("</main></body></html>").toString();
    }
    static List<Section> sections(Document d){
        if(!d.sections.isEmpty())return d.sections;
        if(d.blocks.isEmpty())return Collections.emptyList();
        List<Section> parts=new ArrayList<>();Section part=new Section("legacy-0","正文",false);parts.add(part);
        for(Block b:d.blocks){if(b.type.equals("separator")){part=new Section("legacy-"+parts.size(),"内容",false);parts.add(part);}else part.blocks.add(b);}return parts;
    }
    static String anchorId(String section,int block){return "reading-"+java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(section.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"-"+block;}
    private static void block(StringBuilder h,Block b,String anchor){
        if(b.type.equals("text")){
            h.append("<p data-reading-anchor id='").append(anchor).append("'>");
            h.append(ReaderLinks.render(b)).append("</p>");
        }
        else if(b.type.equals("image")&&UrlPolicy.https(b.value))h.append("<figure data-reading-anchor id='").append(anchor).append("'><a href='").append(escape(b.value)).append("'><img loading='lazy' src='").append(escape(b.value)).append("' alt='正文图片暂未显示'></a><figcaption><a href='").append(escape(b.value)).append("'>查看大图</a> · 图片可能稍后显示，点开可重试</figcaption></figure>");
        else if(b.type.equals("separator"))h.append("<hr>");
    }
}
