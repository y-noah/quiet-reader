package app.quietreader;

import java.net.URI;
import java.util.*;
import org.jsoup.nodes.Element;
import org.json.*;
import static app.quietreader.Models.*;

/** Only the topic's own ordered thread list may supply a default reading target. */
final class TiebaTopic {
    static final int MAX_POSTS=40;
    private TiebaTopic(){}
    static final class Resolved {
        final String topicUrl,postUrl,postSession;
        final long at;
        final List<Item> choices;
        Resolved(String topicUrl,String postUrl,String postSession,long at,List<Item> choices){
            if(!isTopic(topicUrl)||!postUrl.equals(postUrl(topicUrl,postUrl))||!postSession.matches("[0-9a-f]{64}")||at<=0||choices.isEmpty()||choices.size()>MAX_POSTS)throw new IllegalArgumentException("Invalid topic resolution");
            Set<String> ids=new HashSet<>();boolean found=false;
            for(Item choice:choices){
                if(choice.source!=Source.TIEBA||!choice.selectedTopicPost.isEmpty()||!choice.url.equals(postUrl(topicUrl,choice.url))||choice.title.isBlank()||!ids.add(choice.url))throw new IllegalArgumentException("Invalid topic choice");
                if(choice.url.equals(postUrl))found=true;
            }
            if(!found)throw new IllegalArgumentException("Post is outside topic choices");
            this.topicUrl=topicUrl;this.postUrl=postUrl;this.postSession=postSession;this.at=at;this.choices=List.copyOf(choices);
        }
        JSONObject json()throws JSONException{return new JSONObject().put("topic",topicUrl).put("post",postUrl).put("session",postSession).put("at",at).put("choices",Models.toJson(choices));}
        static Resolved read(JSONObject value)throws JSONException{
            try{return new Resolved(value.getString("topic"),value.getString("post"),value.getString("session"),value.getLong("at"),Models.fromJson(value.getJSONArray("choices")));}
            catch(IllegalArgumentException invalid){throw new JSONException("Invalid topic resolution");}
        }
    }
    static Document attach(String topicUrl,List<Item> choices,Document post,String session,long at){
        Resolved resolved=new Resolved(topicUrl,post.url,session,at,choices);
        post.topic=resolved;post.url=topicUrl;return post;
    }
    static Item select(Item root,Resolved resolved,Item post){
        if(root.source!=Source.TIEBA||!root.url.equals(resolved.topicUrl)||resolved.choices.stream().noneMatch(choice->choice.url.equals(post.url)))throw new IllegalArgumentException("Post is outside topic choices");
        return new Item(root.source,root.title,root.url,root.detail,root.video,root.snippet,root.publishedAt,root.sourceTag,post.url);
    }
    /** Explicit choices are checked against each freshly acquired official topic list. */
    static Item target(Item request,List<Item> choices){
        return choices.stream().filter(choice->request.selectedTopicPost.isEmpty()?!choice.video:choice.url.equals(request.selectedTopicPost)).findFirst().orElse(null);
    }
    static boolean isTopic(String url){return id(url)>0;}
    static long id(String url){
        if(!UrlPolicy.belongs(Source.TIEBA,url))return 0;
        try{
            URI uri=URI.create(url);
            if(!"/hottopic/browse/hottopic".equals(uri.getRawPath()))return 0;
            String id=null;
            if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){
                String[] pair=part.split("=",2);
                if(!pair[0].equals("topic_id"))continue;
                if(id!=null||pair.length!=2||!pair[1].matches("[1-9][0-9]{0,18}"))return 0;
                id=pair[1];
            }
            return id==null?0:Long.parseLong(id);
        }catch(IllegalArgumentException invalid){return 0;}
    }
    static List<Item> posts(Element page,String url){
        if(!isTopic(url))return Collections.emptyList();
        Element topic=page.selectFirst(".topic-wrapper");
        if(topic==null)return Collections.emptyList();
        Element selected=topic.selectFirst(".recom-sort-selected");
        String order=selected==null?"":selected.text().trim();
        List<Item> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for(Element row:topic.select(".thread-list>li")){
            if(result.size()==MAX_POSTS)break;
            if(row.closest("aside,nav,.recommend,.advertisement,.ad,[data-tuiguang]")!=null||row.hasClass("ad")||row.hasClass("advertisement"))continue;
            Element anchor=row.selectFirst("a.title[href],a.track-thread-title[href]");
            if(anchor==null||anchor.closest(".thread-list>li")!=row)continue;
            String title=anchor.text().trim(),target=postUrl(url,anchor.attr("href"));
            if(title.isEmpty()||target.isEmpty()||!seen.add(target))continue;
            Element summary=row.selectFirst(".center .content,.content");
            String text=summary==null?"":summary.text().trim();
            result.add(new Item(Source.TIEBA,title,target,order,VideoPolicy.row(row),text,null));
        }
        return Collections.unmodifiableList(result);
    }
    static String postUrl(String base,String href){
        String url=UrlPolicy.normalize(base,href);
        if(!UrlPolicy.belongs(Source.TIEBA,url))return "";
        try{
            URI uri=URI.create(url);String path=uri.getPath();
            // Do not turn an encoded path, continuation page or arbitrary /p/ substring
            // into the main post. Tracking parameters and fragments are discarded.
            if(!path.equals(uri.getRawPath())||!path.matches("/p/[1-9][0-9]{0,19}/?"))return "";
            if(uri.getRawQuery()!=null)for(String part:uri.getRawQuery().split("&")){
                String[] pair=part.split("=",2);
                if(pair[0].equals("pn")&&(pair.length!=2||!pair[1].equals("1")))return "";
            }
            return "https://tieba.baidu.com"+path.replaceFirst("/$","");
        }catch(IllegalArgumentException invalid){return "";}
    }
}
