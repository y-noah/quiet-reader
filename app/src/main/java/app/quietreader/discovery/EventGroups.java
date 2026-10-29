package app.quietreader.discovery;
import java.util.*;
import org.json.*;

/** Explicit thread identity, exact evidence, then substantial identical hot headlines. */
public final class EventGroups {
    public static final String VERSION="explicit-thread-metadata-v7-appointment";
    private EventGroups(){}
    public static boolean matches(FeedStore.Row row,String anchor){
        if(row.eventId.equals(anchor))return true;
        for(HotCandidate entry:row.entries)if(entry.id.equals(anchor)||aliases(entry).contains(anchor))return true;
        return false;
    }
    static String key(HotCandidate c){
        return key(c,false);
    }
    private static boolean exact(HotCandidate c){return c.title.codePointCount(0,c.title.length())>=10&&c.snippet.codePointCount(0,c.snippet.length())>=24&&!c.title.equals(c.snippet)&&c.publishedAt!=null;}
    private static String key(HotCandidate c,boolean legacy){
        // A discussion ID is stronger than copied text or a changing excerpt.
        // Preserve the old evidence key only as a compatibility alias; it must
        // never decide current buckets for two explicitly distinct threads.
        if(!legacy&&c.source==app.quietreader.Models.Source.V2EX){
            String thread=v2exThread(c);return thread==null?c.id:thread;
        }
        if(!exact(c)){
            String thread=v2exThread(c);if(thread!=null)return thread;
            // Known discussion URLs remain authoritative even with identical headings.
            if(c.source==app.quietreader.Models.Source.V2EX)return c.id;
            return headlineKey(c,legacy);
        }
        return "group-"+HotCandidate.hash("exact-evidence-v1\n"+c.title+"\n"+c.snippet+"\n"+Math.floorDiv(c.publishedAt,86_400_000L));
    }
    private static String headlineKey(HotCandidate c,boolean legacy){
        String title=legacy?HeadlineIdentity.literal(c.title):HeadlineIdentity.core(c.title);
        if(title.codePointCount(0,title.length())>=10&&c.observedAt>0){long day=Math.floorDiv((c.publishedAt==null?c.observedAt:c.publishedAt)+8L*3600_000,86_400_000L);return "group-"+HotCandidate.hash((!legacy&&HeadlineIdentity.hasNumbers(c.title)?"hot-title-numeric-v2\n":"hot-title-v1\n")+title+"\n"+day);}
        return c.id;
    }
    /** Resolve only co-observed, unambiguous families. Never persist a guessed
     * headline alias on every complete record: a later conflicting excerpt must
     * not inherit another event's feedback. Captured members retain both sides. */
    private static Map<String,String> bridgeKeys(List<FeedStore.Row> rows){
        Map<String,String> resolved=new HashMap<>();Map<String,List<HotCandidate>> families=new HashMap<>();
        for(FeedStore.Row row:rows){HotCandidate c=row.candidate;resolved.put(c.id,key(c));if(c.source==app.quietreader.Models.Source.V2EX)continue;String headline=headlineKey(c,false);if(!headline.equals(c.id))families.computeIfAbsent(headline,ignored->new ArrayList<>()).add(c);}
        for(List<HotCandidate> family:families.values()){
            Map<String,HotCandidate> complete=new HashMap<>();for(HotCandidate c:family)if(exact(c))complete.put(key(c),c);
            if(complete.size()!=1)continue;Map.Entry<String,HotCandidate> evidence=complete.entrySet().iterator().next();
            for(HotCandidate c:family)if(!exact(c)&&(c.snippet.isEmpty()||HeadlineIdentity.core(c.snippet).equals(HeadlineIdentity.core(c.title))||c.snippet.equals(evidence.getValue().snippet)))resolved.put(c.id,evidence.getKey());
        }
        // A unique, complete cross-source copy may still identify the same
        // event. Never use that copy to union two explicit discussion IDs, or
        // transitively join conflicting complete excerpts through one thread.
        Map<String,Set<String>> byThread=new HashMap<>(),byEvidence=new HashMap<>();
        Set<String> otherEvidence=new HashSet<>();
        for(FeedStore.Row row:rows){HotCandidate c=row.candidate;if(!exact(c))continue;
            String evidence=key(c,true);
            if(c.source!=app.quietreader.Models.Source.V2EX){otherEvidence.add(evidence);continue;}
            String thread=v2exThread(c);if(thread==null)continue;
            byThread.computeIfAbsent(thread,ignored->new HashSet<>()).add(evidence);
            byEvidence.computeIfAbsent(evidence,ignored->new HashSet<>()).add(thread);
        }
        for(FeedStore.Row row:rows){HotCandidate c=row.candidate;if(c.source!=app.quietreader.Models.Source.V2EX)continue;
            String thread=v2exThread(c);Set<String> evidence=byThread.get(thread);
            if(evidence==null||evidence.size()!=1)continue;String copy=evidence.iterator().next();
            if(otherEvidence.contains(copy)&&byEvidence.get(copy).size()==1)resolved.put(c.id,copy);
        }
        resolved.putAll(ShortHeadlineEvidence.resolve(rows));
        resolved.putAll(SportsEventEvidence.resolve(rows));
        resolved.putAll(AppointmentEventEvidence.resolve(rows));
        return resolved;
    }
    static Set<String> aliases(HotCandidate c){Set<String> result=new LinkedHashSet<>();result.add(key(c));
        // The old complete-evidence key used literal metadata, so decimal facts
        // are safe here even when legacy punctuation-stripped headlines are not.
        if(c.source==app.quietreader.Models.Source.V2EX&&exact(c)||HeadlineIdentity.legacySafe(c.title)&&(exact(c)||!HeadlineIdentity.hasNumbers(c.title)))result.add(key(c,true));return result;}
    static Set<String> versionAliases(HotCandidate c){
        Set<String> result=new LinkedHashSet<>();for(String alias:aliases(c))result.add(alias+"@"+c.textHash);
        if(c.source!=app.quietreader.Models.Source.V2EX&&key(c).startsWith("group-")&&(c.snippet.codePointCount(0,c.snippet.length())<24||c.title.equals(c.snippet)||c.publishedAt==null))
            result.add(key(c)+"@"+HotCandidate.metadataHash(HeadlineIdentity.core(c.title),c.snippet));
        return result;
    }
    static Set<String> captureAliases(HotCandidate c,boolean versioned){
        // Compatibility lookup can recognize an old text-only group, but new
        // single-thread feedback must not propagate that alias to another ID.
        if(c.source==app.quietreader.Models.Source.V2EX)return Collections.singleton(key(c)+(versioned?"@"+c.textHash:""));
        return versioned?versionAliases(c):aliases(c);
    }
    private static String v2exThread(HotCandidate c){
        if(c.source!=app.quietreader.Models.Source.V2EX)return null;
        java.net.URI uri=java.net.URI.create(c.canonicalUrl);String path=uri.getRawPath(),fragment=uri.getRawFragment();
        // The observed hot list appends a changing reply-count anchor, not a
        // different article. Retain original URLs/IDs and all unknown query/hash
        // routes; do not change persisted feedback or frozen evaluation records.
        if(!Arrays.asList("www.v2ex.com","v2ex.com").contains(uri.getHost())||uri.getPort()!=-1||uri.getRawQuery()!=null||path==null||!path.matches("/t/[1-9][0-9]*/?"))return null;
        if(fragment!=null&&!fragment.matches("reply[0-9]+"))return null;
        return "group-"+HotCandidate.hash("v2ex-thread-v1\n"+path.replaceFirst("/$",""));
    }
    public static List<FeedStore.Row> group(List<FeedStore.Row> rows,JSONArray feedback){
        Set<String> separate=new HashSet<>(),hidden=new HashSet<>();
        for(int n=0;n<feedback.length();n++){JSONObject f=feedback.optJSONObject(n);if(f==null)continue;if("SEPARATE".equals(f.optString("action"))&&"event".equals(f.optString("scope")))separate.addAll(EventTargets.expand(f.optString("target")));if("event".equals(f.optString("scope"))&&Arrays.asList("BLOCK","ENOUGH").contains(f.optString("action")))hidden.addAll(EventTargets.expand(f.optString("target")));}
        // Resolve before hiding members. Otherwise a captured complete member's
        // ID would disappear first and its headline-only duplicates would leak.
        Map<String,String> resolved=bridgeKeys(rows);
        Set<String> separatedKeys=new HashSet<>(separate);
        for(FeedStore.Row row:rows){HotCandidate c=row.candidate;if(separate.contains(c.id)||!Collections.disjoint(separate,aliases(c)))separatedKeys.add(resolved.get(c.id));}
        Map<String,List<FeedStore.Row>> buckets=new LinkedHashMap<>();
        for(FeedStore.Row row:rows){HotCandidate c=row.candidate;String key=resolved.get(c.id);if(hidden.contains(c.id)||hidden.contains(CandidateOrder.versionTarget(c))||hidden.contains(key)||!Collections.disjoint(hidden,aliases(c))||!Collections.disjoint(hidden,versionAliases(c)))continue;buckets.computeIfAbsent(separatedKeys.contains(key)?c.id:key,ignored->new ArrayList<>()).add(row);}
        List<FeedStore.Row> result=new ArrayList<>();
        for(Map.Entry<String,List<FeedStore.Row>> bucket:buckets.entrySet()){
            List<FeedStore.Row> members=bucket.getValue();FeedStore.Row first=members.get(0);String key=bucket.getKey();
            if(hidden.contains(key)||hidden.contains(key+"@"+first.candidate.textHash))continue;
            List<HotCandidate> entries=new ArrayList<>();for(FeedStore.Row member:members)entries.add(member.candidate);
            result.add(new FeedStore.Row(key,first.candidate,first.reason,entries));
        }
        return result;
    }
}
