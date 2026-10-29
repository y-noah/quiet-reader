package app.quietreader.discovery;

import org.json.*;
import java.util.*;

/** Neutral, deterministic fallback while interest evidence/model results are absent.
 * Source heat and number of reposts never enter the comparator.
 */
public final class CandidateOrder {
    private CandidateOrder(){}
    public static String versionTarget(HotCandidate candidate){return candidate.id+"@"+candidate.textHash;}
    /** Exclusion changes should not reshuffle the remaining visible cards.
     * Newly fetched items follow the retained cards in their existing base order. */
    public static List<FeedStore.Row> preserveVisibleOrder(List<FeedStore.Row> allowed,List<FeedStore.Row> visible){
        Map<String,Integer> positions=new HashMap<>();for(int n=0;n<visible.size();n++){FeedStore.Row row=visible.get(n);positions.putIfAbsent(row.eventId,n);for(HotCandidate entry:row.entries){positions.putIfAbsent(entry.id,n);for(String alias:EventGroups.aliases(entry))positions.putIfAbsent(alias,n);}}
        List<FeedStore.Row> result=new ArrayList<>(allowed);result.sort(Comparator.comparingInt(r->{int position=positions.getOrDefault(r.eventId,Integer.MAX_VALUE);for(HotCandidate entry:r.entries)position=Math.min(position,positions.getOrDefault(entry.id,positions.getOrDefault(EventGroups.key(entry),Integer.MAX_VALUE)));return position;}));return result;
    }
    /** Retain previously accepted groups only while every original member and
     * its complete metadata remain allowed and no explicit split targets them.
     * This is continuity of the accepted snapshot, never new merge evidence. */
    public static List<FeedStore.Row> preserveVisibleGroups(List<FeedStore.Row> allowed,List<FeedStore.Row> visible,JSONArray feedback){
        Set<String> separate=new HashSet<>();for(int n=0;n<feedback.length();n++){JSONObject f=feedback.optJSONObject(n);if(f!=null&&"SEPARATE".equals(f.optString("action"))&&"event".equals(f.optString("scope")))separate.addAll(EventTargets.expand(f.optString("target")));}
        Map<String,HotCandidate> current=new HashMap<>();Map<String,FeedStore.Row> owner=new HashMap<>();
        for(FeedStore.Row row:allowed)for(HotCandidate member:row.entries){current.put(member.id,member);owner.put(member.id,row);}
        Map<String,FeedStore.Row> retained=new HashMap<>();Set<String> reserved=new HashSet<>();
        for(FeedStore.Row row:visible){
            if(row.entries.size()<2||separate.contains(row.eventId))continue;
            Set<String> ids=new HashSet<>();boolean valid=true;
            for(HotCandidate member:row.entries){ids.add(member.id);HotCandidate now=current.get(member.id);
                if(now==null||!member.json().toString().equals(now.json().toString())||separate.contains(member.id)||!Collections.disjoint(separate,EventGroups.aliases(member))||reserved.contains(member.id)){valid=false;break;}}
            if(!valid)continue;
            for(String id:ids)for(HotCandidate member:owner.get(id).entries)if(!ids.contains(member.id)){valid=false;break;}
            if(!valid)continue;reserved.addAll(ids);for(String id:ids)retained.put(id,row);
        }
        List<FeedStore.Row> result=new ArrayList<>();Set<String> emitted=new HashSet<>();
        for(FeedStore.Row row:allowed){FeedStore.Row keep=retained.get(row.candidate.id);if(keep==null)result.add(row);else if(emitted.add(keep.eventId))result.add(keep);}
        return preserveVisibleOrder(result,visible);
    }
    public static List<FeedStore.Row> arrange(List<HotCandidate> candidates,JSONArray feedback,boolean newest){
        return arrange(candidates,feedback,newest,true);
    }
    public static List<FeedStore.Row> forModels(List<HotCandidate> candidates,JSONArray feedback){return arrange(candidates,feedback,false,false);}
    private static List<FeedStore.Row> arrange(List<HotCandidate> candidates,JSONArray feedback,boolean newest,boolean platforms){
        Set<String> sources=new HashSet<>();
        for(int n=0;n<feedback.length();n++){
            JSONObject f=feedback.optJSONObject(n);if(f==null)continue;
            if("BLOCK".equals(f.optString("action"))&&"source".equals(f.optString("scope")))sources.add(f.optString("target"));
        }
        Map<String,HotCandidate> identities=new HashMap<>();
        for(HotCandidate c:candidates){
            if(c.video)continue;
            if(!ContentLanguage.eligible(c.title,c.snippet,c.bodyLanguage))continue;
            if(sources.contains(c.source.name()))continue;
            HotCandidate previous=identities.get(c.id);if(previous==null||c.observedAt>previous.observedAt)identities.put(c.id,c);
        }
        List<HotCandidate> sorted=new ArrayList<>(identities.values());
        Comparator<HotCandidate> order=Comparator.comparingInt((HotCandidate c)->c.sourceRank).thenComparing(c->c.id);
        if(newest)order=Comparator.comparing((HotCandidate c)->c.publishedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(order);
        sorted.sort(order);List<FeedStore.Row> result=new ArrayList<>();
        for(HotCandidate c:sorted)result.add(new FeedStore.Row(c.id,c,newest?(c.publishedAt==null?"来源未提供发布时间":"按来源提供的时间排列"):"按来源内名次排列"));
        List<FeedStore.Row> grouped=EventGroups.group(result,feedback);
        if(!platforms||newest)return grouped;
        // The readable base snapshot must retain known exact dislikes even when
        // power/thermal policy prevents the model pipeline from starting at all.
        SemanticOrder.Profile profile=new SemanticOrder.Profile(feedback);
        return SemanticOrder.fuse(SemanticOrder.rank(grouped,profile,Collections.emptyMap()),Collections.emptyMap(),profile.platforms);
    }
}
