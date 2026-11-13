package app.quietreader.discovery;

import app.quietreader.Models.Source;
import java.util.*;
import org.json.*;

/** Membership budgets, independent of ranking and per-page delivery. */
public final class InterestPool {
    private InterestPool(){}
    public static List<FeedStore.Row> eligible(List<FeedStore.Row> ranked,PlatformPreference preference){
        Map<Source,Integer> baseline=new EnumMap<>(Source.class),used=new EnumMap<>(Source.class);
        int count=0;
        for(FeedStore.Row row:ranked)if(row.interestBand!=2){
            baseline.merge(row.candidate.source,1,Integer::sum);
            if(++count==RecommendationWindow.SIZE)break;
        }
        List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:ranked){
            if(row.interestBand==2)continue;
            Source source=row.candidate.source;
            if(preference.score(source)<0){
                int limit=(int)Math.floor(baseline.getOrDefault(source,0)*preference.exposureWeight(source));
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
        return Long.parseLong(HotCandidate.hash("membership-v1\n"+String.join("\n",values)).substring(0,15),16);
    }
}
