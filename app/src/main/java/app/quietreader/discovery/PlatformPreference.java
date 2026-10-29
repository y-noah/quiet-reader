package app.quietreader.discovery;

import app.quietreader.Models.Source;
import org.json.*;
import java.util.*;

/** Separate local source prior. Never uses heat, fetch counts or unread impressions.
 * Explicit source choices dominate; three distinct engaged articles are required
 * for a weak prior. Bounded rank shifts preserve content-negative categories. */
public final class PlatformPreference {
    public static final String VERSION="platform-prior-v2";
    private final Map<Source,Double> values=new EnumMap<>(Source.class);
    public final String signature;
    public PlatformPreference(JSONArray feedback){
        Map<Source,Double> explicit=new EnumMap<>(Source.class);
        Map<Source,Map<String,Double>> observations=new EnumMap<>(Source.class);
        for(int n=0;n<feedback.length();n++){
            JSONObject event=feedback.optJSONObject(n);if(event==null)continue;
            String action=event.optString("action"),scope=event.optString("scope"),target=event.optString("target");
            if(scope.equals("source")){
                Source source=parse(target);if(source!=null&&(action.equals("MORE")||action.equals("LESS")))explicit.put(source,action.equals("MORE")?1.0:-1.0);
                continue;
            }
            if(!scope.equals("sample"))continue;
            Source source=parse(event.optString("source"));if(source==null)continue;
            double strength=action.equals("QUICK_EXIT")?-0.5:action.equals("READ")?1:action.equals("SAVED")?1.5:action.equals("MORE")?2:action.equals("LESS")?-2:0;
            if(strength==0)continue;
            // Distinct articles, not repeated reads, metadata revisions or action count.
            String identity=target.replaceFirst("^(read:|saved:|quick:)","").split("[@:]",2)[0];
            if(!identity.matches("[a-f0-9]{64}"))continue;
            Map<String,Double> articles=observations.computeIfAbsent(source,ignored->new HashMap<>());
            Double prior=articles.get(identity);if(prior==null||Math.abs(strength)>=Math.abs(prior))articles.put(identity,strength);
        }
        List<String> keys=new ArrayList<>();
        for(Source source:Source.values()){
            Map<String,Double> articles=observations.getOrDefault(source,Collections.emptyMap());double score=0;
            if(articles.size()>=3){double sum=0;for(double value:articles.values())sum+=value;score=Math.max(-0.35,Math.min(0.35,sum/(articles.size()+8)));}
            score=explicit.getOrDefault(source,score);if(score!=0)values.put(source,score);
            keys.add(source.name()+"="+score);
        }
        signature=HotCandidate.hash(VERSION+"\n"+String.join("\n",keys));
    }
    private static Source parse(String name){try{return Source.valueOf(name);}catch(Exception ignored){return null;}}
    public boolean empty(){return values.isEmpty();}
    public double score(Source source){return values.getOrDefault(source,0.0);}
    public List<FeedStore.Row> apply(List<FeedStore.Row> rows){return apply(rows,Collections.emptyMap());}
    public List<FeedStore.Row> apply(List<FeedStore.Row> rows,Map<String,Integer> categories){
        List<FeedStore.Row> result=new ArrayList<>();Map<String,Integer> order=new HashMap<>();int index=0;
        for(FeedStore.Row row:rows){
            HotCandidate selected=row.candidate;
            for(HotCandidate entry:row.entries)if(score(entry.source)>score(selected.source))selected=entry;
            String reason=score(selected.source)==0?row.reason:row.reason+" · 参考平台偏好";
            result.add(new FeedStore.Row(row.eventId,selected,reason,row.entries,row.interestBand,row.refreshBasePosition));order.put(row.eventId,index++);
        }
        // Source prior shifts a rank key by at most four; weak evidence by <1.5.
        result.sort(Comparator.comparingInt((FeedStore.Row row)->categories.getOrDefault(row.eventId,0))
            .thenComparingDouble(row->order.get(row.eventId)-4*score(row.candidate.source))
            .thenComparingInt(row->order.get(row.eventId)));
        return result;
    }
}
