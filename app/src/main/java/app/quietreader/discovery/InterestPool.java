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
        Map<Source,Integer> baseline=new EnumMap<>(Source.class),limits=new EnumMap<>(Source.class);
        int count=0;
        List<FeedStore.Row> usable=new ArrayList<>();for(FeedStore.Row row:ranked)if(row.interestBand!=2)usable.add(row);
        // The normal pool uses the same source-balanced selection as FeedStore,
        // rather than the first 75 rows of an unbounded, source-heavy ranking.
        for(FeedStore.Row row:FeedSourceBalance.arrange(usable,preference)){
            baseline.merge(row.candidate.source,1,Integer::sum);
            if(++count==RecommendationWindow.SIZE)break;
        }
        for(Source source:Source.values())if(preference.score(source)<0)limits.put(source,(int)Math.floor(baseline.getOrDefault(source,0)*preference.exposureWeight(source)));
        return limits;
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
