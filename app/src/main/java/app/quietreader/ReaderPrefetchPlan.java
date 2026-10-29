package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Small frozen neighborhood, never a new ranking or an unbounded Intent payload. */
final class ReaderPrefetchPlan {
    static final int FORWARD=32,BACKWARD=8,MAX_BYTES=128*1024;
    final HotCandidate anchor;
    final List<HotCandidate> candidates;
    private ReaderPrefetchPlan(HotCandidate anchor,List<HotCandidate> candidates){this.anchor=anchor;this.candidates=Collections.unmodifiableList(new ArrayList<>(candidates));}
    static ReaderPrefetchPlan from(List<FeedStore.Row> rows,HotCandidate selected){
        return from(rows,selected,1,false);
    }
    static ReaderPrefetchPlan from(List<FeedStore.Row> rows,HotCandidate selected,int direction,boolean rapid){
        int at=-1;
        for(int n=0;n<rows.size()&&at<0;n++)for(HotCandidate entry:rows.get(n).entries)if(entry.id.equals(selected.id)&&ArticleVersion.sameRequest(entry.readerItem(),selected.readerItem())){at=n;break;}
        List<HotCandidate> neighbors=new ArrayList<>();Set<String> seen=new HashSet<>();seen.add(selected.id);
        if(at>=0)for(int n:PrefetchWindow.neighbors(rows.size(),at,direction,rapid))add(neighbors,seen,rows.get(n).candidate);
        return new ReaderPrefetchPlan(selected,neighbors);
    }
    private static void add(List<HotCandidate> out,Set<String> seen,HotCandidate candidate){
        if(seen.add(candidate.id)&&candidate.source.unifiedReading()&&!candidate.video&&UrlPolicy.belongs(candidate.source,candidate.originalUrl))out.add(candidate);
    }
    boolean matches(Item item){return ArticleVersion.sameRequest(anchor.readerItem(),item);}
    String encode(){try{
        JSONArray entries=new JSONArray();for(HotCandidate candidate:candidates)entries.put(candidate.json());
        String encoded=new JSONObject().put("anchor",anchor.json()).put("candidates",entries).toString();
        return encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=MAX_BYTES?encoded:null;
    }catch(JSONException error){return null;}}
    static ReaderPrefetchPlan decode(String encoded){
        if(encoded==null||encoded.length()>MAX_BYTES||encoded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)return null;
        try{
            JSONObject value=new JSONObject(encoded);HotCandidate anchor=HotCandidate.fromJson(value.getJSONObject("anchor"));JSONArray entries=value.getJSONArray("candidates");
            if(entries.length()>FORWARD+BACKWARD||!UrlPolicy.belongs(anchor.source,anchor.originalUrl))return null;
            List<HotCandidate> candidates=new ArrayList<>();Set<String> seen=new HashSet<>();seen.add(anchor.id);
            for(int n=0;n<entries.length();n++)add(candidates,seen,HotCandidate.fromJson(entries.getJSONObject(n)));
            return new ReaderPrefetchPlan(anchor,candidates);
        }catch(JSONException|IllegalArgumentException error){return null;}
    }
}
