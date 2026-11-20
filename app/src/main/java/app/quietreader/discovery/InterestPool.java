package app.quietreader.discovery;

import java.util.*;
import org.json.*;

/** Topic exclusions are hard boundaries; source dislikes are soft selection weights. */
public final class InterestPool {
    private InterestPool(){}
    public static List<FeedStore.Row> eligible(List<FeedStore.Row> ranked){
        List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:ranked)if(row.interestBand!=2)result.add(row);
        return result;
    }
    /** Manual preference edits reset membership; reading/exposure do not. */
    public static long revision(JSONArray feedback){
        List<String> values=new ArrayList<>();
        for(int n=0;n<feedback.length();n++){
            JSONObject event=feedback.optJSONObject(n);if(event==null)continue;
            String action=event.optString("action");
            if(Arrays.asList("MORE","LESS","FOLLOW","BLOCK","ENOUGH").contains(action))values.add(action+"|"+event.optString("scope")+"|"+event.optString("target")+"|"+event.optString("sample"));
        }
        return Long.parseLong(HotCandidate.hash("membership-v3-soft-source-heat\n"+String.join("\n",values)).substring(0,15),16);
    }
}
