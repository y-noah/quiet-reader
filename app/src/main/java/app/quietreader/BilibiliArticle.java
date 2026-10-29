package app.quietreader;

import org.json.*;
import org.jsoup.nodes.Element;
import java.util.regex.*;
import static app.quietreader.Models.*;

/** Metadata already supplied by the requested video page; never requests media streams. */
final class BilibiliArticle {
    private static final Pattern STATE=Pattern.compile("(?:window\\.)?__INITIAL_STATE__\\s*=\\s*");
    private BilibiliArticle(){}
    static boolean sameVideo(String requested,String loaded){
        if(!UrlPolicy.belongs(Source.BILIBILI,requested)||!UrlPolicy.belongs(Source.BILIBILI,loaded))return false;
        String first=java.net.URI.create(requested).getPath(),second=java.net.URI.create(loaded).getPath();
        return first.matches("/video/BV[0-9A-Za-z]+/?")&&second.matches("/video/BV[0-9A-Za-z]+/?")&&first.split("/")[2].equals(second.split("/")[2]);
    }
    static void read(Element page,Document out){
        String path=java.net.URI.create(out.url).getPath();
        if(!path.matches("/video/BV[0-9A-Za-z]+/?"))return;
        String requested=path.split("/")[2];
        for(Element script:page.select("script:not([src])")){
            String raw=script.data();if(raw.length()>3*1024*1024)continue;
            Matcher match=STATE.matcher(raw);if(!match.find())continue;
            try{
                Object parsed=new JSONTokener(raw.substring(match.end())).nextValue();
                if(!(parsed instanceof JSONObject))continue;
                JSONObject state=(JSONObject)parsed,video=state.optJSONObject("videoData");
                if(video==null||!requested.equals(video.optString("bvid")))continue;
                // A redirected or unavailable page cannot lend another video's content.
                JSONObject error=state.optJSONObject("error");
                if((error!=null&&error.optInt("code",0)!=0)||video.optInt("state",0)<0){out.sourceUnavailable=true;out.notice="该视频当前不可用，请到来源页确认。";return;}
                String title=video.optString("title").trim();if(title.isEmpty())continue;
                out.title=title;JSONObject owner=video.optJSONObject("owner");if(owner!=null)out.byline=owner.optString("name");
                String cover=UrlPolicy.normalize(out.url,video.optString("pic"));
                if(UrlPolicy.domain(UrlPolicy.host(cover),"hdslb.com"))out.videoPoster=cover;
                String description=video.optString("desc").trim();
                if(!description.isEmpty()){
                    Section section=new Section("bilibili-description-"+requested,"视频简介",false);
                    // Source strings are plain text. ReaderLinks escapes markup during rendering.
                    section.blocks.add(new Block("text",description));out.sections.add(section);out.blocks.addAll(section.blocks);
                }
                out.unsupportedVideo=true;out.videoTopic=true;
                out.notice="已整理视频封面、作者和视频简介，未将视频转写为正文；评论尚未获取。视频需在来源页按其权限播放。";
                return;
            }catch(JSONException ignored){}
        }
    }
}
