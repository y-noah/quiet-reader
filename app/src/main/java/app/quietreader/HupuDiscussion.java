package app.quietreader;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.net.URI;
import java.util.*;
import static app.quietreader.Models.*;

/** Identity-matched source thread state. Account identifiers are never retained. */
final class HupuDiscussion {
    static final int BODY_REVISION=4;
    static final String PAGE_NOTICE="仅展示来源当前页已提供的主帖与回复；热门回复与普通回复按来源顺序整理，相同回复只保留一次，不代表全部讨论。";
    static boolean canReuse(Document doc,boolean offline){return offline||!UrlPolicy.belongs(Source.HUPU,doc.sourceUrl())||doc.hupuBodyRevision==BODY_REVISION;}
    private HupuDiscussion(){}
    static String threadId(String url){
        if(!UrlPolicy.belongs(Source.HUPU,url))return "";
        try{java.util.regex.Matcher m=java.util.regex.Pattern.compile("^/([1-9][0-9]*)(?:-[1-9][0-9]{0,5})?\\.html$").matcher(new URI(url).getPath());return m.matches()?m.group(1):"";}catch(Exception ignored){return "";}
    }
    static int page(String url){
        if(threadId(url).isEmpty())return -1;
        String path=URI.create(url).getPath();int hyphen=path.indexOf('-');return hyphen<0?1:Integer.parseInt(path.substring(hyphen+1,path.length()-5));
    }
    static Document article(org.jsoup.nodes.Document page,String url){
        Element state=page.selectFirst("script#__NEXT_DATA__");if(state==null)return null;
        Document out=new Document();out.url=url;
        try{
            String id=threadId(url);if(id.isEmpty()||state.data().length()>4*1024*1024)throw new IllegalArgumentException();
            JSONObject props=new JSONObject(state.data()).getJSONObject("props").getJSONObject("pageProps"),detail=props.getJSONObject("detail"),thread=detail.getJSONObject("thread"),replies=detail.getJSONObject("replies");
            if(!id.equals(thread.optString("tid"))||!UrlPolicy.sameForumPost(Source.HUPU,url,UrlPolicy.normalize(url,thread.optString("url")))||replies.optInt("current",-1)!=page(url))throw new IllegalArgumentException();
            boolean restricted=!"ALL_SEE".equals(detail.optString("visibleRange"))||!"ALL_SEE".equals(thread.optString("visibleRange"));
            int status=thread.optInt("status",-1);
            // Observed public hot threads also use status 3. Do not equate it
            // with denial, but require successful, actually rendered content.
            boolean corroborate=restricted||status==3;
            Set<String> renderedMain=corroborate?renderedBodies(page,false,url):null,renderedReplies=corroborate?renderedBodies(page,true,url):null;
            if(restricted&&!authorizedRendered(props,detail,thread,replies,url,renderedMain,renderedReplies)){out.loginRequired=true;out.notice="此讨论需要来源登录或权限，请在来源页确认后重新读取。";return out;}
            if(status!=0&&(status!=3||!successful(props)||!renderedPage(thread,replies,url,renderedMain,renderedReplies)))throw new IllegalArgumentException();
            // Keep the established main-video policy and explicit playback fallback.
            if(flag(thread,"hasVideo")){
                // Preserve this identity-validated positive type even when the player
                // has not hydrated. The existing explicit-reader path owns playback.
                page.head().prependElement("meta").attr("property","og:type").attr("content","video");return null;
            }
            if(VideoPolicy.mainPost(Source.HUPU,page,url))return null;
            Element title=page.selectFirst("h1");out.title=title==null?thread.optString("title"):title.text();out.byline=author(thread);
            if(page(url)==1){
                Element body=visibleContent(Jsoup.parseBodyFragment(thread.optString("content"),url).body());
                if(!SourceParser.appendRegion(out,body,"hupu-main-"+id,label("主帖",out.byline),false,url))throw new IllegalArgumentException();
            }
            Set<String> seen=new HashSet<>();JSONArray lights=detail.optJSONArray("lights");
            if(page(url)==1&&lights!=null)appendReplies(out,lights,seen,url,renderedReplies);
            appendReplies(out,replies.getJSONArray("list"),seen,url,renderedReplies);
            DiscussionReplyEvidence.captureHupu(out,page(url)==1?lights:null,replies.getJSONArray("list"),System.currentTimeMillis());
            DiscussionPages.pagination(Source.HUPU,page,out);
            out.notice=PAGE_NOTICE;
            if(out.filteredVideos>0)out.notice+=" "+VideoPolicy.filteredNotice(out.filteredVideos);
            if(!out.hasContent()&&out.filteredSectionIds.isEmpty())throw new IllegalArgumentException();
            return out;
        }catch(Exception invalid){out=new Document();out.url=url;out.sourceUnavailable=true;out.notice="尚未取得身份及页码匹配的虎扑正文，可在来源页确认后重试。";return out;}
    }
    /** LOGIN_SEE is an audience rule, not evidence of a failed login. Require the
     * same successful response to identify a signed-in viewer AND render the body.
     * Unknown/self-only audiences remain closed; embedded JSON alone is insufficient. */
    private static boolean authorizedRendered(JSONObject props,JSONObject detail,JSONObject thread,JSONObject replies,String url,Set<String> main,Set<String> renderedReplies){
        for(String range:new String[]{detail.optString("visibleRange"),thread.optString("visibleRange")})
            if(!"ALL_SEE".equals(range)&&!"LOGIN_SEE".equals(range))return false;
        JSONObject user=detail.optJSONObject("user");
        if(user==null||!user.optString("puid").matches("[1-9][0-9]{0,31}"))return false;
        if(!successful(props))return false;
        return renderedPage(thread,replies,url,main,renderedReplies);
    }
    private static boolean successful(JSONObject props){JSONObject info=props.optJSONObject("detailErrorInfo");return info!=null&&info.optInt("code",-1)==200;}
    private static boolean renderedPage(JSONObject thread,JSONObject replies,String url,Set<String> main,Set<String> renderedReplies){
        if(page(url)==1)return rendered(main,thread.optString("content"),url);
        JSONArray rows=replies.optJSONArray("list");
        if(rows!=null)for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row!=null&&rendered(renderedReplies,row.optString("content"),url))return true;}
        return false;
    }
    private static boolean rendered(Set<String> bodies,String content,String base){
        String signature=bodySignature(visibleContent(Jsoup.parseBodyFragment(content).body()),base);
        return !signature.isEmpty()&&bodies.contains(signature);
    }
    /** Compare both prose and ordered usable media. Empty-text image replies are real
     * content; matching prose alone cannot authorize an unrendered JSON attachment. */
    private static String bodySignature(Element body,String base){
        String text=body.text();List<String> images=new ArrayList<>();
        for(Element image:body.select("img")){String url=SourceParser.imageSource(image,base);if(!url.isEmpty())images.add(url);}
        return text.isEmpty()&&images.isEmpty()?"":new JSONArray().put(text).put(new JSONArray(images)).toString();
    }
    private static boolean hidden(Element node){
        return node.hasAttr("hidden")||node.attr("aria-hidden").equalsIgnoreCase("true")||node.attr("style").replaceAll("\\s+","").toLowerCase(Locale.ROOT).matches(".*(?:display:none|visibility:hidden|content-visibility:hidden).*");
    }
    /** Use the same visible-content rules for state, rendered corroboration and
     * output. A hidden subtree cannot cause denial or leak into a readable body. */
    private static Element visibleContent(Element body){
        Element visible=body;
        visible.select("script,style,iframe,aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container").remove();
        for(Element descendant:new ArrayList<>(visible.getAllElements()))if(descendant!=visible&&hidden(descendant))descendant.remove();
        return visible;
    }
    private static Set<String> renderedBodies(org.jsoup.nodes.Document page,boolean reply,String base){
        Set<String> bodies=new HashSet<>();
        String selector=reply?".reply-list-wrapper .thread-content-detail":"[class*='post-content_main-post-info'] .thread-content-detail";
        for(Element body:page.select(selector)){
            if(body.closest("aside,nav,.recommend,.recommend-list,.related-recommend,.advertisement,.ad,.feed-ad-container")!=null||hidden(body)||body.parents().stream().anyMatch(HupuDiscussion::hidden))continue;
            String signature=bodySignature(visibleContent(body.clone()),base);if(!signature.isEmpty())bodies.add(signature);
        }
        return bodies;
    }
    private static void appendReplies(Document out,JSONArray rows,Set<String> seen,String url,Set<String> renderedReplies)throws JSONException{
        for(int n=0;n<rows.length()&&n<DiscussionPages.MAX_REPLIES&&seen.size()<DiscussionPages.MAX_REPLIES;n++){
            JSONObject row=rows.getJSONObject(n);String id=row.optString("pid");
            if(!id.matches("[1-9][0-9]{0,31}")||flag(row,"isHidden")||flag(row,"isAudit")||flag(row,"isDelete")||flag(row,"isSelfDelete")||row.optInt("hidePost")!=0||seen.contains(id))continue;
            if(renderedReplies!=null&&!rendered(renderedReplies,row.optString("content"),url))continue;
            Element body=Jsoup.parseBodyFragment(row.optString("content"),url).body();
            if(VideoPolicy.videoSection(Source.HUPU,body)){seen.add(id);out.filteredSectionIds.add("hupu-reply-"+id);out.filteredVideos=out.filteredSectionIds.size();continue;}
            // An unrendered or empty highlighted copy must not suppress a later
            // readable copy of the same source reply in the regular list.
            if(SourceParser.appendRegion(out,visibleContent(body),"hupu-reply-"+id,label("回复",author(row)),false,url))seen.add(id);
        }
    }
    private static boolean flag(JSONObject row,String key){return row.optBoolean(key)||row.optInt(key,0)!=0;}
    private static String author(JSONObject item){JSONObject author=item.optJSONObject("author");return author==null?"":author.optString("puname").trim();}
    private static String label(String role,String author){return author.isEmpty()?role:role+" · "+author;}
}
