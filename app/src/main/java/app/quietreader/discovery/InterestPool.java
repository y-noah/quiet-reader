package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;
import org.json.*;

/** Membership budgets, independent of ranking and per-page delivery. */
public final class InterestPool {
    private InterestPool(){}
    public static List<FeedStore.Row> eligible(List<FeedStore.Row> ranked,PlatformPreference preference){
        return eligible(ranked,limits(ranked,preference));
    }
    public static Map<Source,Integer> limits(List<FeedStore.Row> ranked,PlatformPreference preference){
        Map<Source,Integer> baseline=baseline(ranked,preference),limits=new EnumMap<>(Source.class);
        for(Source source:Source.values())if(preference.score(source)<0)limits.put(source,(int)Math.floor(baseline.getOrDefault(source,0)*preference.exposureWeight(source)));
        return limits;
    }
    public static Map<Source,Integer> baseline(List<FeedStore.Row> ranked,PlatformPreference preference){
        Map<Source,Integer> baseline=new EnumMap<>(Source.class);
        int count=0;
        List<FeedStore.Row> usable=new ArrayList<>();for(FeedStore.Row row:ranked)if(row.interestBand!=2)usable.add(row);
        // The normal pool uses the same source-balanced selection as FeedStore,
        // rather than the first 75 rows of an unbounded, source-heavy ranking.
        for(FeedStore.Row row:FeedSourceBalance.arrange(usable,preference)){
            baseline.merge(row.candidate.source,1,Integer::sum);
            if(++count==RecommendationWindow.SIZE)break;
        }
        return baseline;
    }
    public static List<FeedStore.Row> eligible(List<FeedStore.Row> ranked,Map<Source,Integer> limits){
        Map<Source,Integer> used=new EnumMap<>(Source.class);
        List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:ranked){
            if(row.interestBand==2)continue;
            Source source=row.candidate.source;
            if(limits.containsKey(source)){
                int limit=limits.get(source);
                if(used.getOrDefault(source,0)>=limit)continue;
            }
            used.merge(source,1,Integer::sum);result.add(row);
        }
        return result;
    }
    /** Manual feedback may restore a prior allowance; passive reads may not. */
    public static long sourceRevision(JSONArray feedback,Source source){
        List<String> values=new ArrayList<>();
        for(int n=0;n<feedback.length();n++){
            JSONObject event=feedback.optJSONObject(n);if(event==null)continue;
            String action=event.optString("action"),scope=event.optString("scope");
            boolean sameSource=scope.equals("source")?event.optString("target").equals(source.name()):scope.equals("sample")&&event.optString("source").equals(source.name());
            if(sameSource&&Arrays.asList("MORE","LESS").contains(action))values.add(action+"|"+scope+"|"+event.optString("target")+"|"+event.optString("id"));
        }
        return Long.parseLong(HotCandidate.hash("source-membership-v1\n"+String.join("\n",values)).substring(0,15),16);
    }
    /** Reading/exposure never reopen rejected tails; explicit preference edits do. */
    public static long revision(JSONArray feedback){
        List<String> values=new ArrayList<>();
        for(int n=0;n<feedback.length();n++){
            JSONObject event=feedback.optJSONObject(n);if(event==null)continue;
            String action=event.optString("action");
            if(Arrays.asList("MORE","LESS","FOLLOW","BLOCK","ENOUGH").contains(action))values.add(action+"|"+event.optString("scope")+"|"+event.optString("target")+"|"+event.optString("sample"));
        }
        return Long.parseLong(HotCandidate.hash("membership-v2\n"+String.join("\n",values)).substring(0,15),16);
    }
}
