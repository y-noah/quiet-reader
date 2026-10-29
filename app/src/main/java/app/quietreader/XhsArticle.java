package app.quietreader;

import org.json.*;
import org.jsoup.nodes.Element;
import java.util.*;
import java.util.regex.*;
import static app.quietreader.Models.*;

/** Owned note metadata only; never evaluates the source's JavaScript. */
final class XhsArticle {
    private static final Pattern STATE=Pattern.compile("(?:window\\.)?__INITIAL_STATE__\\s*=\\s*");
    private XhsArticle(){}
    static String id(String url){
        if(!UrlPolicy.belongs(Source.XHS,url))return "";
        String path=java.net.URI.create(url).getPath();
        if(!path.matches("/(?:explore|discovery/item)/[0-9a-fA-F]{24}/?"))return "";
        path=path.replaceFirst("/$","");return path.substring(path.lastIndexOf('/')+1).toLowerCase(Locale.ROOT);
    }
    static boolean sameNote(String requested,String loaded){String first=id(requested);return !first.isEmpty()&&first.equalsIgnoreCase(id(loaded));}
    static JSONObject state(Element page){
        for(Element script:page.select("script:not([src])")){
            String raw=script.data();if(raw.length()>3*1024*1024)continue;Matcher matcher=STATE.matcher(raw);
            if(matcher.find())try{Object value=new JSONTokener(raw.substring(matcher.end())).nextValue();if(value instanceof JSONObject)return (JSONObject)value;}catch(JSONException ignored){}
        }
        return null;
    }
    static Set<String> videoNoteIds(Element page){
        Set<String> ids=new HashSet<>();JSONObject state=state(page),feed=state==null?null:state.optJSONObject("feed");JSONArray rows=feed==null?null:feed.optJSONArray("feeds");
        if(rows!=null)for(int i=0;i<Math.min(rows.length(),300);i++){JSONObject row=rows.optJSONObject(i),card=row==null?null:row.optJSONObject("noteCard");if(card!=null&&"video".equals(card.optString("type")))ids.add(row.optString("id"));}
        return ids;
    }
    static Document article(Element page,String url){
        Document out=new Document();out.url=url;String id=id(url);if(id.isEmpty())return out;
        JSONObject state=state(page),group=state==null?null:state.optJSONObject("note"),map=group==null?null:group.optJSONObject("noteDetailMap");
        JSONObject container=map==null?null:map.optJSONObject(id),note=container==null?null:container.optJSONObject("note");
        String current=group==null?"":group.optString("currentNoteId");
        if(note==null||!id.equalsIgnoreCase(note.optString("noteId"))||(current.matches("[0-9a-fA-F]{24}")&&!id.equalsIgnoreCase(current))){out.notice="来源尚未提供当前笔记正文，可能需要登录、访问链接已失效或页面仍在加载。请打开来源页确认后重试。";return out;}
        if(note.optBoolean("isPrivate")||note.optBoolean("isDeleted")||note.optBoolean("isBlocked")){out.sourceUnavailable=true;out.notice="来源标记此笔记受限或不可用，请在来源页确认。";return out;}
        if(note.optBoolean("needLogin")){out.loginRequired=true;out.notice="来源要求登录后查看此笔记，请登录后重新读取。";return out;}
        String type=note.optString("type");if(!type.equals("normal")&&!type.equals("video")){out.notice="尚未支持这种笔记类型，请在来源页查看。";return out;}
        String description=note.optString("desc").replaceAll("#([^#\\n]{1,100})\\[话题\\]#","#$1#").trim();
        out.title=note.optString("title").trim();if(out.title.isEmpty()){out.title=description.split("\\n",2)[0];if(out.title.codePointCount(0,out.title.length())>80)out.title=out.title.substring(0,out.title.offsetByCodePoints(0,80))+"…";}
        JSONObject user=note.optJSONObject("user");if(user!=null)out.byline=user.optString("nickname");
        out.unsupportedVideo=type.equals("video");out.videoTopic=out.unsupportedVideo;Section body=new Section("xhs-body-"+id,out.unsupportedVideo?"视频简介":"正文",false);
        if(!description.isEmpty())body.blocks.add(new Block("text",description));
        JSONArray images=note.optJSONArray("imageList");Set<String> seen=new HashSet<>();
        if(images!=null)for(int i=0;i<Math.min(images.length(),80);i++){
            JSONObject image=images.optJSONObject(i);if(image==null)continue;String source=imageUrl(image,url);if(source.isEmpty()||!seen.add(source))continue;
            if(out.unsupportedVideo){out.videoPoster=source;break;}
            Block block=new Block("image",source);int width=image.optInt("width"),height=image.optInt("height");
            if(width>0&&width<=20000&&height>0&&height<=100000){block.imageWidth=width;block.imageHeight=height;}body.blocks.add(block);
        }
        if(!body.blocks.isEmpty()){out.sections.add(body);out.blocks.addAll(body.blocks);}
        JSONObject comments=container.optJSONObject("comments");JSONArray list=comments==null?null:comments.optJSONArray("list");seen.clear();int count=0;
        if(list!=null)for(int i=0;i<list.length()&&count<100;i++){
            JSONObject row=list.optJSONObject(i);if(row==null)continue;String cid=row.optString("id"),text=row.optString("content").trim();
            if(cid.isEmpty()||text.isEmpty()||!seen.add(cid))continue;JSONObject author=row.optJSONObject("userInfo");String name=author==null?"":author.optString("nickname");
            Section reply=new Section("xhs-comment-"+cid,"评论"+(name.isEmpty()?"":" · "+name),false);reply.blocks.add(new Block("text",text));out.sections.add(reply);out.blocks.addAll(reply.blocks);count++;
        }
        out.notice="仅整理来源当前提供的"+(out.unsupportedVideo?"视频封面和说明，视频未转写为正文，需在来源页按其权限播放。":"图文内容。")+(count==0?"评论尚未获取。":"仅展示已加载的顶层评论；更多评论与回复尚未接入。");
        return out;
    }
    private static String imageUrl(JSONObject image,String base){
        for(String key:new String[]{"urlDefault","url","urlPre"}){String value=UrlPolicy.normalize(base,image.optString(key));if(UrlPolicy.domain(UrlPolicy.host(value),"xhscdn.com"))return value;}
        JSONArray variants=image.optJSONArray("infoList");if(variants!=null)for(int i=0;i<Math.min(variants.length(),20);i++){JSONObject variant=variants.optJSONObject(i);if(variant!=null&&variant.optString("imageScene").equals("WB_DFT")){String value=UrlPolicy.normalize(base,variant.optString("url"));if(UrlPolicy.domain(UrlPolicy.host(value),"xhscdn.com"))return value;}}
        return "";
    }
}
