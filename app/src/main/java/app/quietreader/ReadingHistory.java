package app.quietreader;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Bounded local reading snapshots; no account data or remote HTML is stored. */
final class ReadingHistory {
    private final SharedPreferences prefs;
    ReadingHistory(Context context){prefs=context.getSharedPreferences("reading-history",0);}
    boolean seen(String url){return prefs.contains(url);}
    JSONObject get(String url){try{return new JSONObject(prefs.getString(url,""));}catch(Exception ignored){return null;}}
    void save(String url,Document doc,Set<String> expanded,int position){
        try{
            JSONObject state=new JSONObject().put("at",System.currentTimeMillis()).put("position",Math.max(0,position)).put("expanded",new JSONArray(expanded));
            JSONObject snapshot=encode(doc);
            if(snapshot.toString().length()<300000)state.put("document",snapshot);
            SharedPreferences.Editor edit=prefs.edit().putString(url,state.toString());
            List<String> keys=new ArrayList<>(prefs.getAll().keySet());keys.remove(url);
            keys.sort(Comparator.comparingLong(k->{JSONObject s=get(k);return s==null?0:s.optLong("at");}));
            int total=state.toString().length();
            for(int i=keys.size()-1;i>=0;i--){String key=keys.get(i);String value=prefs.getString(key,"");total+=value.length();if(keys.size()-i>=30||total>1500000)edit.remove(key);}
            edit.apply();
        }catch(Exception ignored){}
    }
    static JSONObject encode(Document d)throws JSONException{
        JSONObject o=new JSONObject().put("url",d.url).put("title",d.title).put("byline",d.byline).put("notice",d.notice).put("next",d.nextUrl).put("more",d.moreStatus)
            .put("unsupportedVideo",d.unsupportedVideo).put("filteredVideos",d.filteredVideos).put("filteredIds",new JSONArray(d.filteredSectionIds));
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
            parts.put(new JSONObject().put("id",s.id).put("label",s.label).put("answer",s.answer).put("continuation",s.continuationUrl).put("blocks",blocks));
        }
        return o.put("sections",parts);
    }
    static Document decode(JSONObject o)throws JSONException{
        Document d=new Document();d.url=o.getString("url");d.title=o.optString("title");d.byline=o.optString("byline");d.notice=o.optString("notice");d.nextUrl=o.optString("next");d.moreStatus=o.optString("more");
        d.unsupportedVideo=o.optBoolean("unsupportedVideo");d.filteredVideos=o.optInt("filteredVideos");
        JSONArray ids=o.optJSONArray("filteredIds");if(ids!=null)for(int i=0;i<ids.length();i++)d.filteredSectionIds.add(ids.getString(i));
        JSONArray parts=o.getJSONArray("sections");
        for(int n=0;n<parts.length();n++){
            JSONObject p=parts.getJSONObject(n);Section s=new Section(p.getString("id"),p.getString("label"),p.getBoolean("answer"));s.continuationUrl=p.optString("continuation");
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
