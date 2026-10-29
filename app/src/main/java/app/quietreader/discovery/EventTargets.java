package app.quietreader.discovery;

import java.util.*;
import org.json.*;

/** One reversible event keeps observed member identities; enough keeps versions. */
public final class EventTargets {
    private static final String FORMAT="event-targets-v1";
    private static final int MAX_LENGTH=1_000_000,MAX_TARGETS=5001;
    private EventTargets(){}
    public static String capture(FeedStore.Row row,boolean versioned){
        if(row.entries.size()==1&&row.eventId.equals(row.candidate.id))return versioned?CandidateOrder.versionTarget(row.candidate):row.eventId;
        Set<String> targets=new LinkedHashSet<>();targets.add(row.eventId+(versioned?"@"+row.candidate.textHash:""));
        for(HotCandidate member:row.entries)targets.add(versioned?CandidateOrder.versionTarget(member):member.id);
        if(targets.size()>MAX_TARGETS)throw new IllegalArgumentException("Too many event members");
        // Original member identities keep priority within the existing bound.
        // Additional aliases preserve old wrapper groups and cosmetic versions.
        for(HotCandidate member:row.entries)for(String alias:EventGroups.captureAliases(member,versioned))if(targets.size()<MAX_TARGETS)targets.add(alias);
        try{return new JSONObject().put("format",FORMAT).put("targets",new JSONArray(targets)).toString();}catch(JSONException error){throw new IllegalStateException(error);}
    }
    private static Set<String> structured(String target){
        if(target==null||target.length()>MAX_LENGTH||!target.startsWith("{"))return null;
        try{
            JSONObject object=new JSONObject(target);if(!FORMAT.equals(object.optString("format")))return null;
            JSONArray values=object.getJSONArray("targets");if(values.length()==0||values.length()>MAX_TARGETS)return null;Set<String> result=new LinkedHashSet<>();
            for(int n=0;n<values.length();n++){Object value=values.get(n);if(!(value instanceof String)||!((String)value).matches("(?:group-)?[a-f0-9]{64}(?:@[a-f0-9]{64})?"))return null;result.add((String)value);}return result;
        }catch(JSONException error){return null;}
    }
    public static boolean structuredTarget(String target){return structured(target)!=null;}
    public static Set<String> expand(String target){Set<String> result=structured(target);return result==null?Collections.singleton(target):result;}
}
