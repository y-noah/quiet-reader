package app.quietreader;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import org.jsoup.nodes.Element;
import static app.quietreader.Models.*;

/** Source reply creation time, not proof that a discussion has valuable new information. */
final class DiscussionReplyEvidence {
    static final String VERSION="v2ex-reply-created-v1";
    static final String HUPU_VERSION="hupu-reply-created-v1";
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss xxx",Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);
    private DiscussionReplyEvidence(){}
    static boolean valid(String url,Section section,long at,long now){
        return UrlPolicy.belongs(Source.V2EX,url)&&DiscussionPages.supports(Source.V2EX,url)
                &&section.id.matches("v2ex-r_[1-9][0-9]{0,19}")&&!section.answer&&!section.nestedReply
                &&at>=946684800000L&&at<=now;
    }
    static boolean valid(String version,String url,Section section,long at,long now){
        if(VERSION.equals(version))return valid(url,section,at,now);
        if(TiebaReplyTimes.VERSION.equals(version))return TiebaReplyTimes.valid(url,section,at,now);
        return HUPU_VERSION.equals(version)&&!HupuDiscussion.threadId(url).isEmpty()
                &&section.id.matches("hupu-reply-[1-9][0-9]{0,31}")&&!section.answer&&!section.nestedReply
                &&at>=946684800000L&&at<=now;
    }
    /** Called only after HupuDiscussion has validated thread, page and audience.
     * Highlight/ordinary copies must agree; display-relative strings are ignored. */
    static void captureHupu(Document doc,org.json.JSONArray lights,org.json.JSONArray rows,long now){
        Map<String,Long> stamps=new HashMap<>();
        for(org.json.JSONArray entries:new org.json.JSONArray[]{lights,rows}){
            if(entries==null)continue;
            if(entries.length()>2048)return;
            for(int n=0;n<entries.length();n++){
                org.json.JSONObject row=entries.optJSONObject(n);if(row==null)continue;
                String id=row.optString("pid");if(!id.matches("[1-9][0-9]{0,31}"))continue;
                Object raw=row.opt("createdAt");long at=raw instanceof Long||raw instanceof Integer?((Number)raw).longValue():0;
                if(at<946684800000L||at>now)at=0;
                Long previous=stamps.putIfAbsent(id,at);if(previous!=null&&previous.longValue()!=at)stamps.put(id,0L);
            }
        }
        for(Section section:doc.sections){
            if(!section.id.startsWith("hupu-reply-"))continue;
            Long at=stamps.get(section.id.substring(11));
            if(at!=null&&valid(HUPU_VERSION,doc.url,section,at,now)){section.sourceReplyAt=at;section.sourceReplyEvidence=HUPU_VERSION;}
        }
    }
    static void capture(Source source,org.jsoup.nodes.Document page,Document doc,long now){
        if(source!=Source.V2EX||!DiscussionPages.supports(source,doc.url))return;
        Map<String,Element> cells=new HashMap<>();int scanned=0;
        for(Element cell:page.select("[id]")){
            if(!cell.id().matches("r_[1-9][0-9]{0,19}"))continue;
            if(++scanned>2048)return;
            if(cells.containsKey(cell.id()))cells.put(cell.id(),null);else cells.put(cell.id(),cell);
        }
        for(Section section:doc.sections){
            if(!section.id.startsWith("v2ex-r_"))continue;
            Element cell=cells.get(section.id.substring(5));
            if(cell!=null&&cell.hasClass("cell"))captureCell(cell,section,doc.url,now);
        }
    }
    private static void captureCell(Element cell,Section section,String url,long now){
        if(cell.select(".reply_content").size()!=1)return;
        long found=0;int count=0;
        for(Element stamp:cell.select(".ago[title]")){
            // Body quotations and nested cards must never timestamp the containing reply.
            if(stamp.closest(".cell")!=cell||stamp.closest(".reply_content")!=null)continue;
            if(++count>1)return;
            String raw=stamp.attr("title");
            if(!raw.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2} [+-][0-9]{2}:[0-9]{2}"))return;
            try{found=OffsetDateTime.parse(raw,TIME).toInstant().toEpochMilli();}catch(java.time.DateTimeException invalid){return;}
        }
        if(count==1&&valid(url,section,found,now)){section.sourceReplyAt=found;section.sourceReplyEvidence=VERSION;}
    }
}
