package app.quietreader.discovery;

import java.util.*;
import org.json.*;

/** Pre-ranking provenance only. Identity matches still need human event adjudication. */
public final class ComparisonLearning {
    private ComparisonLearning(){}
    /** Old frozen records retain their original member scope. New captures also
     * retain static group aliases when the displayed event bridges two families. */
    public static Set<String> candidateIdentities(JSONObject event)throws JSONException{
        Set<String> result=new TreeSet<>();result.add(event.getString("event_key"));JSONArray members=event.getJSONArray("members");for(int n=0;n<members.length();n++)result.add(members.getJSONObject(n).getString("id"));
        if(event.has("identity_aliases"))result.addAll(ProspectiveEvaluation.strings(event.getJSONArray("identity_aliases")));
        return result;
    }
    public static Set<String> eventIdentities(String scope,String target){
        Set<String> result=new TreeSet<>();
        if("source".equals(scope)||"entity".equals(scope))return result;
        for(String value:EventTargets.expand(target)){
            if(value.startsWith("saved:"))value=value.substring(6);
            else if(value.startsWith("read:"))value=value.substring(5).replace(':','@');
            if(value.matches("(?:group-)?[a-f0-9]{64}(?:@[a-f0-9]{64})?"))result.add(value.split("@",2)[0]);
        }
        return result;
    }
    public static void validate(JSONObject value,long profile,long source,long rankedAt)throws JSONException{
        if(value.getInt("schema")!=1||value.getLong("profile_version")!=profile||value.getLong("source_version")!=source
                ||value.getLong("learning_cutoff_ms")<0||value.getLong("learning_cutoff_ms")>rankedAt
                ||value.getLong("latest_feedback_at_ms")>value.getLong("learning_cutoff_ms")
                ||!value.getString("ledger_sha256").matches("[a-f0-9]{64}")
                ||!value.getString("active_feedback_sha256").matches("[a-f0-9]{64}")
                ||value.getInt("ledger_rows")<0||value.getInt("ledger_rows")>100000||value.getBoolean("holdout_qualified"))
            throw new IllegalArgumentException("Invalid pre-ranking learning provenance");
        Set<String> identities=new HashSet<>();JSONArray training=value.getJSONArray("known_learning_identities");
        if(training.length()>10000)throw new IllegalArgumentException("Learning identities exceed budget");
        for(int n=0;n<training.length();n++){String id=training.getString(n);if(!id.matches("(?:group-)?[a-f0-9]{64}")||!identities.add(id))throw new IllegalArgumentException("Invalid learning identity");}
        Set<String> events=new HashSet<>();JSONArray candidates=value.getJSONArray("candidate_events");
        if(candidates.length()==0||candidates.length()>100)throw new IllegalArgumentException("Invalid provenance pool");
        for(int n=0;n<candidates.length();n++){
            JSONObject event=candidates.getJSONObject(n);if(!events.add(event.getString("event_key")))throw new IllegalArgumentException("Duplicate provenance event");
            if(event.has("identity_aliases")){JSONArray aliases=event.getJSONArray("identity_aliases");if(aliases.length()>10000)throw new IllegalArgumentException("Provenance aliases exceed budget");ProspectiveEvaluation.strings(aliases);}
            JSONArray members=event.getJSONArray("members");Set<String> seen=new HashSet<>();if(members.length()==0||members.length()>5000)throw new IllegalArgumentException("Invalid provenance members");
            for(int i=0;i<members.length();i++){JSONObject member=members.getJSONObject(i);long first=member.getLong("first_seen_ms");
                if(!member.getString("id").matches("[a-f0-9]{64}")||!member.getString("text_hash").matches("[a-f0-9]{64}")||!seen.add(member.getString("id"))||first<=0||first>value.getLong("learning_cutoff_ms"))throw new IllegalArgumentException("Invalid first-observation provenance");}
        }
    }
    public static void validatePool(JSONObject value,List<FeedStore.Row> rows)throws JSONException{
        Map<String,FeedStore.Row> expected=new HashMap<>();for(FeedStore.Row row:rows)expected.put(row.eventId,row);
        JSONArray events=value.getJSONArray("candidate_events");if(events.length()!=expected.size())throw new IllegalArgumentException("Provenance pool differs");
        for(int n=0;n<events.length();n++){
            JSONObject event=events.getJSONObject(n);FeedStore.Row row=expected.remove(event.getString("event_key"));if(row==null)throw new IllegalArgumentException("Unknown provenance event");
            Map<String,String> members=new HashMap<>();for(HotCandidate c:row.entries)members.put(c.id,c.textHash);
            JSONArray recorded=event.getJSONArray("members");if(recorded.length()!=members.size())throw new IllegalArgumentException("Provenance members differ");
            for(int i=0;i<recorded.length();i++){JSONObject member=recorded.getJSONObject(i);if(!member.getString("text_hash").equals(members.remove(member.getString("id"))))throw new IllegalArgumentException("Provenance content differs");}
            if(event.has("identity_aliases")){Set<String> aliases=new TreeSet<>();for(HotCandidate member:row.entries)aliases.addAll(EventGroups.aliases(member));if(!aliases.equals(ProspectiveEvaluation.strings(event.getJSONArray("identity_aliases"))))throw new IllegalArgumentException("Provenance group aliases differ");}
        }
    }
    /** Regroup a pre-ranking capture without reading a newer learning ledger.
     * The exact member IDs, text hashes and first observations must be conserved. */
    public static JSONObject regroup(JSONObject value,List<FeedStore.Row> rows)throws JSONException{
        JSONObject copy=new JSONObject(value.toString());
        validate(copy,copy.getLong("profile_version"),copy.getLong("source_version"),copy.getLong("learning_cutoff_ms"));
        try{validatePool(copy,rows);return copy;}catch(IllegalArgumentException changedPool){/* Validate conservation below. */}
        Map<String,HotCandidate> current=new HashMap<>();
        for(FeedStore.Row row:rows)for(HotCandidate member:row.entries)if(current.put(member.id,member)!=null)throw new IllegalArgumentException("Repeated regroup member");
        Map<String,JSONObject> captured=new HashMap<>();JSONArray original=copy.getJSONArray("candidate_events");
        for(int n=0;n<original.length();n++){
            JSONObject event=original.getJSONObject(n);Set<String> keys=new HashSet<>(),aliases=new TreeSet<>();JSONArray members=event.getJSONArray("members");
            for(int i=0;i<members.length();i++){
                JSONObject member=members.getJSONObject(i);HotCandidate actual=current.get(member.getString("id"));
                if(actual==null||!actual.textHash.equals(member.getString("text_hash"))||captured.put(actual.id,member)!=null)throw new IllegalArgumentException("Regroup cannot replace, omit or repeat captured content");
                keys.add(actual.id);aliases.addAll(EventGroups.aliases(actual));
            }
            keys.addAll(aliases);if(!keys.contains(event.getString("event_key")))throw new IllegalArgumentException("Unknown captured regroup identity");
            if(event.has("identity_aliases")&&!aliases.equals(ProspectiveEvaluation.strings(event.getJSONArray("identity_aliases"))))throw new IllegalArgumentException("Captured regroup aliases differ");
        }
        if(captured.size()!=current.size())throw new IllegalArgumentException("Regroup cannot add candidates");
        JSONArray events=new JSONArray();
        for(FeedStore.Row row:rows){JSONArray members=new JSONArray();Set<String> aliases=new TreeSet<>();
            for(HotCandidate member:row.entries){JSONObject before=captured.remove(member.id);if(before==null)throw new IllegalArgumentException("Unknown regroup content");members.put(new JSONObject(before.toString()));aliases.addAll(EventGroups.aliases(member));}
            events.put(new JSONObject().put("event_key",row.eventId).put("members",members).put("identity_aliases",new JSONArray(aliases)));
        }
        copy.put("parent_event_pool_sha256",HotCandidate.hash(original.toString())).put("grouping_policy",EventGroupAssembly.VERSION).put("candidate_events",events);
        validatePool(copy,rows);return copy;
    }
}
