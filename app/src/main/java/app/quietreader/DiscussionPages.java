package app.quietreader;

import java.net.URI;
import java.util.*;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Known forum pagination. A next page contributes replies, never a new main post. */
final class DiscussionPages {
    static final int MAX_REPLIES=600;
    static boolean supports(Source source,String url){return source==Source.HUPU?!HupuDiscussion.threadId(url).isEmpty():(source==Source.V2EX||source==Source.TIEBA)&&UrlPolicy.sameForumPost(source,url,url);}
    static boolean usesStream(Source source,Document doc){return supports(source,doc.sourceUrl())&&(source!=Source.HUPU||doc.sections.stream().anyMatch(section->section.id.matches("hupu-(main|reply)-[1-9][0-9]*"))||doc.filteredSectionIds.stream().anyMatch(id->id.matches("hupu-reply-[1-9][0-9]*")));}
    static int page(Source source,String url){
        if(!supports(source,url))return -1;
        if(source==Source.HUPU)return HupuDiscussion.page(url);
        try{
            String query=URI.create(url).getRawQuery(),key=source==Source.V2EX?"p":"pn";int number=1;boolean seen=false;
            if(query!=null)for(String field:query.split("&")){String[] pair=field.split("=",2);if(!pair[0].equals(key))continue;if(seen||pair.length!=2||!pair[1].matches("[1-9][0-9]{0,5}"))return -1;seen=true;number=Integer.parseInt(pair[1]);}
            return number;
        }catch(Exception invalid){return -1;}
    }
    static int current(Source source,Document doc){return Math.max(page(source,doc.sourceUrl()),doc.discussionPage);}
    static boolean samePage(Source source,String requested,String actual){return UrlPolicy.sameForumPost(source,requested,actual)&&page(source,requested)>0&&page(source,requested)==page(source,actual);}
    static boolean next(Source source,Document doc){return supports(source,doc.sourceUrl())&&UrlPolicy.sameThread(source,doc.sourceUrl(),doc.nextUrl)&&page(source,doc.nextUrl)>current(source,doc);}
    static void pagination(Source source,Element scope,Document doc){
        if(!supports(source,doc.url))return;
        doc.discussionPage=page(source,doc.url);doc.nextUrl="";int nearest=Integer.MAX_VALUE;
        for(Element anchor:scope.select("a[href]")){
            String text=anchor.text().trim();boolean control=anchor.attr("rel").equals("next")||text.matches("下一页[>›»]?|[›»❯]")||source==Source.V2EX&&(anchor.hasClass("page_normal")||anchor.hasClass("page_next"))||source==Source.HUPU&&anchor.closest(".hupu-rc-pagination")!=null;
            if(!control||anchor.closest("aside,.recommend,.advertisement")!=null)continue;
            String url=UrlPolicy.normalize(doc.url,anchor.attr("href"));int page=page(source,url);
            if(UrlPolicy.sameThread(source,doc.url,url)&&page>doc.discussionPage&&page<nearest){doc.nextUrl=url;nearest=page;}
        }
    }
    static Document merge(Source source,Document before,Document incoming,String requested){
        if(!next(source,before)||!before.nextUrl.equals(requested)||!requested.equals(incoming.url)||!UrlPolicy.sameForumPost(source,before.sourceUrl(),incoming.url))throw new IllegalArgumentException("后续页与当前讨论不匹配");
        Document result=AnswerStream.copy(before);
        if(incoming.loginRequired){result.moreStatus="login";result.notice="后续回复需要来源登录或权限；已读内容保留。";return result;}
        if(source==Source.HUPU&&(!usesStream(source,before)||!usesStream(source,incoming)))throw new IllegalArgumentException("来源未提供稳定的虎扑回复身份，已读内容保留，可重新读取或在来源页继续");
        if(incoming.sourceUnavailable||!incoming.hasContent()&&incoming.filteredSectionIds.isEmpty())throw new IllegalArgumentException("来源尚未提供可整理的后续回复，已读内容保留");
        if(result.sections.isEmpty())result.sections.addAll(ReaderHtml.sections(before));
        int newlyFiltered=0;
        for(String id:incoming.filteredSectionIds)if(result.filteredSectionIds.add(id))newlyFiltered++;
        result.filteredVideos=Math.max(Math.max(before.filteredVideos,incoming.filteredVideos),result.filteredSectionIds.size());
        Set<String> excluded=result.filteredSectionIds;
        result.sections.removeIf(section->ReaderHtml.discussion(section)&&excluded.contains(section.id));
        Set<String> ids=new HashSet<>();for(Section section:result.sections)ids.add(section.id);
        int added=0;
        for(Section section:incoming.sections)if(ReaderHtml.discussion(section)&&!result.filteredSectionIds.contains(section.id)&&!legacyTiebaReply(source,section,ids)&&ids.add(section.id)){result.sections.add(section);added++;}
        if(added==0&&newlyFiltered==0)throw new IllegalArgumentException("本次没有取得新的回复；来源可能未切换页码，可重试或打开来源确认");
        result.blocks.clear();for(Section section:result.sections)result.blocks.addAll(section.blocks);
        if(result.sections.stream().filter(ReaderHtml::discussion).count()+result.filteredSectionIds.size()>MAX_REPLIES||DocumentSize.estimate(result)>ArticleDiskCache.MAX_ENTRY||!DocumentSize.serializationFits(result,ArticleDiskCache.MAX_ENTRY)){
            result=AnswerStream.copy(before);result.moreStatus="limit";result.notice="当前已保留较多回复，为控制内存暂缓继续追加；可在来源页继续阅读。";return result;
        }
        result.discussionPage=page(source,requested);
        result.nextUrl=next(source,incoming)?incoming.nextUrl:"";
        result.moreStatus=result.nextUrl.isEmpty()?"end":"loaded";
        result.notice="本次新增 "+added+" 条回复。"+(result.nextUrl.isEmpty()?"来源当前页面未提供下一页，不代表已取得全部历史回复。":"已读内容保留，可继续加载后续页。");
        if(result.filteredVideos>0)result.notice+=" "+VideoPolicy.filteredNotice(result.filteredVideos);
        return result;
    }
    private static boolean legacyTiebaReply(Source source,Section section,Set<String> ids){
        if(source!=Source.TIEBA||!section.sourcePostId.matches("[1-9][0-9]{0,19}"))return false;
        String canonical="tieba-post-"+app.quietreader.discovery.HotCandidate.hash("post_content_"+section.sourcePostId).substring(0,24);
        String legacy="tieba-post-"+app.quietreader.discovery.HotCandidate.hash(section.sourcePostId).substring(0,24);
        return section.id.equals(canonical)&&ids.contains(legacy);
    }
}
