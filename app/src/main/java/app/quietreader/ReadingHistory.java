package app.quietreader;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Bounded local reading snapshots; no account data or remote HTML is stored. */
final class ReadingHistory {
    private final SharedPreferences prefs;
    private final Context context;
    ReadingHistory(Context context){this.context=context.getApplicationContext();prefs=this.context.getSharedPreferences("reading-history",0);}
    boolean seen(String url){return prefs.contains(url);}
    boolean clearBodies(){
        SharedPreferences.Editor edit=prefs.edit();
        for(Map.Entry<String,?> entry:prefs.getAll().entrySet())try{
            JSONObject state=new JSONObject(String.valueOf(entry.getValue()));
            state.remove("document");state.remove("bodyAt");state.remove("session");state.remove("namespace");
            edit.putString(entry.getKey(),state.toString());
        }catch(JSONException ignored){}
        return edit.commit();
    }
    JSONObject get(String url){try{JSONObject state=new JSONObject(prefs.getString(url,""));
        android.net.ConnectivityManager network=(android.net.ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);boolean offline=network!=null&&network.getActiveNetwork()==null;
        long age=System.currentTimeMillis()-state.optLong("bodyAt");
        if(!ArticleDiskCache.session(url).equals(state.optString("session"))||!new ArticleDiskCache(context).namespace().equals(state.optString("namespace"))||age<0||age>(offline?ArticleDiskCache.MAX_AGE:10*60_000))state.remove("document");
        if(state.has("document")){Document body=decode(state.getJSONObject("document"));if(!ArticleSession.contentCurrent(body)||!TiebaReplyRelations.canReuse(body,offline)||!HupuDiscussion.canReuse(body,offline))state.remove("document");}
        return state;
    }catch(Exception ignored){return null;}}
    void save(String url,Document doc,Set<String> expanded,int position){
        save(url,doc,expanded,position,null);
    }
    void save(String url,Document doc,Set<String> expanded,int position,JSONObject viewport){
        try{
            JSONObject state=new JSONObject().put("at",System.currentTimeMillis()).put("position",Math.max(0,position)).put("expanded",new JSONArray(expanded)).put("bodyAt",doc.cachedAt).put("session",doc.cachedSession).put("namespace",doc.cachedNamespace);
            if(viewport!=null&&viewport.toString().length()<=4096)state.put("viewport",viewport);
            state.put("bodySource",doc.sourceUrl());
            // Oversized or uncacheable bodies retain their reading marker without
            // materializing a second JSON tree/string on the UI thread.
            if(doc.cachedAt>0&&doc.cachedSession!=null&&DocumentSize.estimate(doc)<=600_000&&DocumentSize.serializationFits(doc,300_000)){
                JSONObject snapshot=encode(doc);
                if(snapshot.toString().length()<300000)state.put("document",snapshot);
            }
            SharedPreferences.Editor edit=prefs.edit().putString(url,state.toString());
            List<String> keys=new ArrayList<>(prefs.getAll().keySet());keys.remove(url);
            keys.sort(Comparator.comparingLong(k->{try{return new JSONObject(prefs.getString(k,"{}")).optLong("at");}catch(JSONException ignored){return 0L;}}));
            int total=state.toString().length();
            for(int i=keys.size()-1;i>=0;i--){String key=keys.get(i);String value=prefs.getString(key,"");total+=value.length();if(keys.size()-i>=30||total>1500000)edit.remove(key);}
            edit.apply();
        }catch(Exception ignored){}
    }
    static Document restore(JSONObject state)throws JSONException{Document d=decode(state.getJSONObject("document"));d.cachedAt=state.optLong("bodyAt");d.cachedSession=state.optString("session");d.cachedNamespace=state.optString("namespace");return d;}
    static JSONObject encode(Document d)throws JSONException{
        JSONObject o=new JSONObject().put("url",d.url).put("title",d.title).put("byline",d.byline).put("notice",d.notice).put("next",d.nextUrl).put("more",d.moreStatus)
            .put("unsupportedVideo",d.unsupportedVideo).put("videoTopic",d.videoTopic).put("videoPoster",d.videoPoster).put("videoUrl",d.videoUrl).put("filteredVideos",d.filteredVideos).put("filteredIds",new JSONArray(d.filteredSectionIds));
        o.put("metadataHash",d.metadataHash);
        if(ArticleUpdateEvidence.VERSION.equals(d.sourceUpdateEvidence)&&d.sourceModifiedAt>0)o.put("sourceUpdateEvidence",new JSONObject().put("version",ArticleUpdateEvidence.VERSION).put("modifiedAt",d.sourceModifiedAt));
        if(d.topic!=null)o.put("topicResolution",d.topic.json());
        o.put("discussionPage",d.discussionPage);
        o.put("virtualDiscussion",d.virtualDiscussion);
        if(UrlPolicy.belongs(Source.TIEBA,d.sourceUrl()))o.put("tiebaReplyRevision",d.tiebaReplyRevision);
        if(UrlPolicy.belongs(Source.HUPU,d.sourceUrl()))o.put("hupuBodyRevision",d.hupuBodyRevision);
        JSONArray mediaItems=new JSONArray();
        for(VideoAttachment video:d.videos)if(mediaItems.length()<ReaderMedia.MAX_ATTACHMENTS)mediaItems.put(new JSONObject().put("url",video.url).put("poster",video.poster));
        o.put("videos",mediaItems);
        JSONArray parts=new JSONArray();
        for(Section s:ReaderHtml.sections(d)){
            JSONArray blocks=new JSONArray();
            for(Block b:s.blocks){
                JSONObject block=new JSONObject().put("type",b.type).put("value",b.value).put("width",b.imageWidth).put("height",b.imageHeight);
                JSONArray images=new JSONArray(),links=new JSONArray();
                for(InlineImage i:b.inlineImages)images.put(new JSONObject().put("start",i.start).put("end",i.end).put("url",i.url).put("alt",i.alt));
                for(InlineLink l:b.inlineLinks)links.put(new JSONObject().put("start",l.start).put("end",l.end).put("url",l.url));
                blocks.put(block.put("images",images).put("links",links));
            }
            JSONObject part=new JSONObject().put("id",s.id).put("label",s.label).put("answer",s.answer).put("continuation",s.continuationUrl).put("blocks",blocks);
            if(!s.sourcePostId.isEmpty())part.put("sourcePostId",s.sourcePostId);
            if(!s.sourceChildId.isEmpty())part.put("sourceChildId",s.sourceChildId);
            if(DiscussionReplyEvidence.valid(s.sourceReplyEvidence,d.sourceUrl(),s,s.sourceReplyAt,System.currentTimeMillis()))part.put("sourceReplyEvidence",new JSONObject().put("version",s.sourceReplyEvidence).put("createdAt",s.sourceReplyAt));
            if(s.nestedReply)part.put("nestedReply",true);
            if(!s.parentSectionId.isEmpty())part.put("parentSectionId",s.parentSectionId);
            if(!s.replyToAuthor.isEmpty())part.put("replyToAuthor",s.replyToAuthor);
            parts.put(part);
        }
        return o.put("sections",parts);
    }
    static Document decode(JSONObject o)throws JSONException{
        Document d=new Document();d.url=o.getString("url");d.title=o.optString("title");d.byline=o.optString("byline");d.notice=o.optString("notice");d.nextUrl=o.optString("next");d.moreStatus=o.optString("more");
        if(o.has("topicResolution")){d.topic=TiebaTopic.Resolved.read(o.getJSONObject("topicResolution"));if(!d.url.equals(d.topic.topicUrl))throw new JSONException("Topic identity differs");}
        d.unsupportedVideo=o.optBoolean("unsupportedVideo");d.videoTopic=o.optBoolean("videoTopic");d.filteredVideos=o.optInt("filteredVideos");
        String metadata=o.optString("metadataHash");if(metadata.matches("[0-9a-f]{64}"))d.metadataHash=metadata;
        JSONObject update=o.optJSONObject("sourceUpdateEvidence");if(update!=null&&ArticleUpdateEvidence.VERSION.equals(update.optString("version"))){Object time=update.opt("modifiedAt");if(time instanceof Long||time instanceof Integer){long at=((Number)time).longValue();if(at>=946684800000L&&at<=System.currentTimeMillis()){d.sourceModifiedAt=at;d.sourceUpdateEvidence=ArticleUpdateEvidence.VERSION;}}}
        int discussionPage=o.optInt("discussionPage");if(discussionPage>=0&&discussionPage<=999999)d.discussionPage=discussionPage;
        d.virtualDiscussion=o.optBoolean("virtualDiscussion")&&DiscussionPages.supports(Source.TIEBA,d.sourceUrl());
        d.tiebaReplyRevision=o.optInt("tiebaReplyRevision",0);
        d.hupuBodyRevision=o.optInt("hupuBodyRevision",0);
        String poster=o.optString("videoPoster");if(UrlPolicy.https(poster))d.videoPoster=poster;
        String media=o.optString("videoUrl");if(ReaderMedia.allowed(d.url,media))d.videoUrl=media;
        JSONArray mediaItems=o.optJSONArray("videos");
        if(mediaItems!=null)for(int n=0;n<Math.min(mediaItems.length(),ReaderMedia.MAX_ATTACHMENTS);n++){
            JSONObject video=mediaItems.optJSONObject(n);if(video==null)continue;
            String address=video.optString("url"),cover=video.optString("poster");
            ReaderMedia.add(d,ReaderMedia.allowed(d.url,address)?address:"",ReaderMedia.allowedPoster(d.url,cover)?cover:"");
        }
        JSONArray ids=o.optJSONArray("filteredIds");if(ids!=null)for(int i=0;i<ids.length();i++)d.filteredSectionIds.add(ids.getString(i));
        JSONArray parts=o.getJSONArray("sections");
        for(int n=0;n<parts.length();n++){
            JSONObject p=parts.getJSONObject(n);Section s=new Section(p.getString("id"),p.getString("label"),p.getBoolean("answer"));s.continuationUrl=p.optString("continuation");String postId=p.optString("sourcePostId");if(postId.matches("[1-9][0-9]{0,19}"))s.sourcePostId=postId;
            s.nestedReply=p.optBoolean("nestedReply");s.parentSectionId=p.optString("parentSectionId");s.replyToAuthor=p.optString("replyToAuthor");
            String childId=p.optString("sourceChildId");
            if(UrlPolicy.belongs(Source.TIEBA,d.sourceUrl())&&s.nestedReply&&!s.parentSectionId.isEmpty()&&childId.matches("[1-9][0-9]{0,19}")&&s.id.equals("tieba-post-"+app.quietreader.discovery.HotCandidate.hash("lzl:"+childId).substring(0,24)))s.sourceChildId=childId;
            JSONObject replyTime=p.optJSONObject("sourceReplyEvidence");
            if(replyTime!=null){Object value=replyTime.opt("createdAt");if(value instanceof Long||value instanceof Integer){long at=((Number)value).longValue();if(DiscussionReplyEvidence.valid(replyTime.optString("version"),d.sourceUrl(),s,at,System.currentTimeMillis())){s.sourceReplyAt=at;s.sourceReplyEvidence=replyTime.optString("version");}}}
            JSONArray blocks=p.getJSONArray("blocks");
            for(int j=0;j<blocks.length();j++){
                JSONObject b=blocks.getJSONObject(j);List<InlineImage> images=new ArrayList<>();List<InlineLink> links=new ArrayList<>();
                JSONArray im=b.getJSONArray("images"),li=b.getJSONArray("links");
                for(int k=0;k<im.length();k++){JSONObject i=im.getJSONObject(k);images.add(new InlineImage(i.getInt("start"),i.getInt("end"),i.getString("url"),i.getString("alt")));}
                for(int k=0;k<li.length();k++){JSONObject l=li.getJSONObject(k);links.add(new InlineLink(l.getInt("start"),l.getInt("end"),l.getString("url")));}
                Block block=new Block(b.getString("type"),b.getString("value"),images,links);block.imageWidth=b.optInt("width");block.imageHeight=b.optInt("height");s.blocks.add(block);d.blocks.add(block);
            }
            d.sections.add(s);
        }
        return d;
    }
}
