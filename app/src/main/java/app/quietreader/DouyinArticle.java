package app.quietreader;

import org.json.*;
import org.jsoup.nodes.Element;
import java.util.*;
import java.util.regex.*;
import static app.quietreader.Models.*;

/** Reads content already supplied by a Douyin page; does not request playback or account APIs. */
final class DouyinArticle {
    private static final Pattern PUSH=Pattern.compile("(?:self|window)\\.__pace_f\\.push\\s*\\(");
    private static final Pattern RECORD=Pattern.compile("(?:^|\\n)[0-9a-fA-F]+:([\\[{])");
    private static final Pattern HOT_PATH=Pattern.compile("/hot/([0-9]+)(?:/[^/]+)?/?");
    private static final Pattern VIDEO_PATH=Pattern.compile("/video/([0-9]+)/?");
    private DouyinArticle(){}
    // Current board links include an encoded display title after the numeric hot id.
    // The title is an alias, while the owner record must still match that exact id.
    private static String contentPath(String url){
        if(!UrlPolicy.belongs(Source.DOUYIN,url))return "";
        try{
            String path=java.net.URI.create(url).getRawPath();if(path==null)return "";
            Matcher hot=HOT_PATH.matcher(path);if(hot.matches())return "/hot/"+hot.group(1);
            Matcher video=VIDEO_PATH.matcher(path);return video.matches()?"/video/"+video.group(1):"";
        }catch(IllegalArgumentException error){return "";}
    }
    static boolean sameContent(String requested,String loaded){
        String first=contentPath(requested),second=contentPath(loaded);
        if(first.isEmpty()||second.isEmpty())return false;
        if(first.equals(second))return true;
        if(!first.startsWith("/hot/")||!second.startsWith("/video/"))return false;
        String query=java.net.URI.create(loaded).getRawQuery();if(query==null)return false;
        String id=first.split("/")[2];int matches=0;
        for(String entry:query.split("&"))if(entry.startsWith("sentenceId=")){if(!entry.equals("sentenceId="+id))return false;matches++;}
        return matches==1;
    }
    static Document article(Element page,String url){
        Document out=new Document();out.url=url;
        if(contentPath(url).isEmpty())return out;
        StringBuilder stream=new StringBuilder();int frames=0;
        for(Element script:page.select("script:not([src])")){
            String raw=script.data();if(raw.length()>3*1024*1024)continue;
            Matcher match=PUSH.matcher(raw);
            while(match.find()&&frames++<512)try{
                Object value=new JSONTokener(raw.substring(match.end())).nextValue();
                if(!(value instanceof JSONArray))continue;JSONArray frame=(JSONArray)value;
                if(frame.optInt(0,-1)!=1||!(frame.opt(1) instanceof String))continue;
                String text=frame.getString(1);if(stream.length()+text.length()>3*1024*1024)return out;
                stream.append(text);
            }catch(JSONException ignored){}
        }
        // Flight records can span multiple push calls. Joining first is required;
        // parsing each script separately silently loses the current content object.
        Matcher record=RECORD.matcher(stream);int[] remaining={20000};JSONObject pending=null;
        while(record.find())try{
            Object value=new JSONTokener(stream.substring(record.start(1))).nextValue();
            JSONObject owner=find(value,url,0,remaining);if(owner!=null){JSONObject aweme=owner.optJSONObject("aweme");if(aweme!=null&&aweme.optJSONObject("detail")!=null){read(owner,out);return out;}pending=owner;}
        }catch(JSONException ignored){}
        if(pending!=null){read(pending,out);return out;}
        out.notice="来源尚未返回当前内容，可能仍在加载、需要登录或话题已失效。可打开来源页确认后重试。";
        return out;
    }
    private static JSONObject find(Object value,String url,int depth,int[] remaining){
        if(depth>12||--remaining[0]<0)return null;
        if(value instanceof JSONObject){
            JSONObject object=(JSONObject)value;
            if(object.has("aweme")&&owns(object,url))return object;
            for(Iterator<String> keys=object.keys();keys.hasNext();){JSONObject found=find(object.opt(keys.next()),url,depth+1,remaining);if(found!=null)return found;}
        }else if(value instanceof JSONArray){JSONArray list=(JSONArray)value;for(int i=0;i<list.length();i++){JSONObject found=find(list.opt(i),url,depth+1,remaining);if(found!=null)return found;}}
        return null;
    }
    private static boolean owns(JSONObject owner,String url){
        String path=contentPath(url),id=path.split("/")[2];
        JSONObject aweme=owner.optJSONObject("aweme"),detail=aweme==null?null:aweme.optJSONObject("detail");
        if(path.startsWith("/hot/"))return id.equals(owner.optString("hotId"))&&(detail==null||!present(owner.opt("awemeId"))||owner.optString("awemeId").equals(detail.optString("awemeId")));
        return detail!=null&&id.equals(detail.optString("awemeId"));
    }
    private static boolean present(Object value){return value!=null&&value!=JSONObject.NULL&&!Boolean.FALSE.equals(value)&&!"".equals(value)&&!"$undefined".equals(value)&&!(value instanceof Number&&((Number)value).doubleValue()==0);}
    private static void read(JSONObject owner,Document out){
        JSONObject aweme=owner.optJSONObject("aweme"),detail=aweme==null?null:aweme.optJSONObject("detail");
        if(aweme!=null&&aweme.optInt("statusCode",-1)!=0){out.sourceUnavailable=true;out.notice="来源未提供此内容，请在来源页确认可见状态。";return;}
        if(aweme==null||detail==null){out.notice="来源尚未返回当前话题内容；未加载的内容不会当作已读完。";return;}
        if(present(detail.opt("pcNeedLogin"))){out.loginRequired=true;out.notice="来源要求登录后查看，请在来源页完成登录后重试。";return;}
        JSONObject status=detail.optJSONObject("status");
        if(status==null||status.optInt("privateStatus",-1)!=0||present(status.opt("isDelete"))||present(status.opt("isProhibited"))||present(status.opt("isReviewing"))||present(status.opt("partSee"))||present(detail.opt("isPrivate"))||present(detail.opt("isFriendLimit"))){out.sourceUnavailable=true;out.notice="来源未确认此内容公开可见，请在来源页确认权限。";return;}
        if(present(detail.opt("chargeInfo"))||present(detail.opt("entertainmentPreviewConfig"))||present(detail.opt("entertainmentVideoPaidWay"))){out.sourceUnavailable=true;out.notice="内容存在付费或其他权限限制，请在来源页确认。";return;}
        String id=detail.optString("awemeId");if(!id.matches("[0-9]+"))return;
        String description=detail.optString("desc").trim();
        out.title=detail.optString("itemTitle").trim();if(out.title.isEmpty()){out.title=description.split("\\n",2)[0];if(out.title.codePointCount(0,out.title.length())>80)out.title=out.title.substring(0,out.title.offsetByCodePoints(0,80))+"…";}
        JSONObject author=detail.optJSONObject("authorInfo");if(author!=null)out.byline=author.optString("nickname");
        JSONObject video=detail.optJSONObject("video");out.unsupportedVideo=video!=null;out.videoTopic=out.unsupportedVideo;
        if(video!=null){String cover=UrlPolicy.normalize(out.url,video.optString("cover"));if(UrlPolicy.domain(UrlPolicy.host(cover),"douyinpic.com"))out.videoPoster=cover;}
        if(!description.isEmpty()){
            Section body=new Section("douyin-body-"+id,video==null?"正文":"视频简介",false);body.blocks.add(new Block("text",description));out.sections.add(body);out.blocks.addAll(body.blocks);
        }
        JSONObject comments=owner.optJSONObject("comment");JSONArray rows=comments==null||comments.optInt("statusCode",-1)!=0?null:comments.optJSONArray("comments");
        Set<String> seen=new HashSet<>();int count=0;
        if(rows!=null)for(int i=0;i<rows.length()&&count<100;i++){
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;String cid=row.optString("cid"),text=row.optString("text").trim();
            if(cid.isEmpty()||text.isEmpty()||!seen.add(cid))continue;
            JSONObject user=row.optJSONObject("user");String name=user==null?"":user.optString("nickname");
            Section section=new Section("douyin-comment-"+cid,"评论"+(name.isEmpty()?"":" · "+name),false);section.blocks.add(new Block("text",text));out.sections.add(section);out.blocks.addAll(section.blocks);count++;
        }
        out.notice=(video==null?"已整理来源提供的文字。":"已整理视频封面和说明，视频未转写为正文，播放需在来源页按其权限进行。")+(rows==null?"评论尚未获取。":"仅展示当前页已加载的评论，未加载内容不代表已读完；继续加载尚未接入。");
    }
}
