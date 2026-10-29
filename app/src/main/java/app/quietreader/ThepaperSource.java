package app.quietreader;

import org.json.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import java.util.*;
import static app.quietreader.Models.*;

/** Public sidebar hotNews only; no editorial picks, latest feed or advertising. */
final class ThepaperSource {
    private ThepaperSource(){}
    static String articleId(String url){
        if(!UrlPolicy.belongs(Source.THEPAPER,url)||!UrlPolicy.host(url).equals("www.thepaper.cn"))return "";
        try{String path=java.net.URI.create(url).getPath();return path.matches("/newsDetail_forward_[1-9][0-9]*")?path.substring("/newsDetail_forward_".length()):"";}catch(Exception ignored){return "";}
    }
    static List<Item> list(String raw)throws JSONException{
        JSONObject response=new JSONObject(raw);
        if(response.optInt("resultCode")!=1)throw new IllegalStateException("澎湃热榜暂不可用");
        JSONArray hot=response.getJSONObject("data").getJSONArray("hotNews");List<Item> rows=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(int n=0;n<hot.length()&&n<100;n++){
            JSONObject entry=hot.getJSONObject(n);String id=entry.optString("contId"),title=app.quietreader.discovery.HotCandidate.clean(entry.optString("name"),240);int type=entry.optInt("contType",-1);
            if(!id.matches("[1-9][0-9]*")||title.isEmpty()||(type!=0&&type!=9)||!entry.optString("link").trim().isEmpty()||!seen.add(id))continue;
            boolean video=type==9||entry.optString("trackArticleType").equals("视频");long published=entry.optLong("pubTimeLong");
            rows.add(new Item(Source.THEPAPER,title,"https://www.thepaper.cn/newsDetail_forward_"+id,"热榜 #"+(n+1),video,app.quietreader.discovery.HotCandidate.clean(entry.optString("summary"),800),published>0?published:null,entry.optJSONObject("nodeInfo")==null?"":entry.getJSONObject("nodeInfo").optString("name")));
        }
        if(rows.isEmpty())throw new IllegalStateException("澎湃未返回可用热门文章");return rows;
    }
    /** Rebuild from identity-matched article data, never sidebar text or summary. */
    static org.jsoup.nodes.Document articlePage(org.jsoup.nodes.Document page,String url){
        try{
            String id=articleId(url);Element state=page.selectFirst("script#__NEXT_DATA__");if(id.isEmpty()||state==null)return null;
            JSONObject props=new JSONObject(state.data()).getJSONObject("props").getJSONObject("pageProps");
            JSONObject detail=props.getJSONObject("detailData").getJSONObject("contentDetail");
            if(!id.equals(props.optString("contId"))||!id.equals(detail.optString("contId"))||detail.optInt("contType",-1)!=0||detail.optBoolean("paywalled")||!detail.optString("link").trim().isEmpty())return null;
            String content=detail.optString("content");if(content.trim().isEmpty())return null;
            org.jsoup.nodes.Document clean=Jsoup.parse("",url);clean.head().appendElement("meta").attr("name","author").attr("content",detail.optString("author"));
            clean.body().appendElement("h1").text(detail.optString("name"));clean.body().appendElement("article").attr("id","quiet-paper-body").html(content);return clean;
        }catch(Exception ignored){return null;}
    }
}
