package app.quietreader.discovery;

import java.util.*;

/** Metadata for four future screens. Reserving never records a view or a preference. */
public final class FeedReserve {
    public static final int ROUNDS=4, CAPACITY=FeedBatch.SIZE*(ROUNDS+1);
    public static final long UPDATE_MS=5*60_000L;
    private final LinkedHashSet<String> presented=new LinkedHashSet<>();
    private List<FeedStore.Row> future=Collections.emptyList();
    private String filter="all";
    private PlatformPreference preference;
    private long nextTurn;
    public void nextTurn(long value){nextTurn=value;}
    public void preference(PlatformPreference value){preference=value;}
    public void accepted(List<FeedStore.Row> visible){
        for(FeedStore.Row row:visible)for(HotCandidate member:row.entries)presented.add(member.id);
        while(presented.size()>2500)presented.remove(presented.iterator().next());
    }
    public void stage(List<FeedStore.Row> ordered,String selected){
        if(!filter.equals(SourceFilter.normalize(selected)))clear();filter=SourceFilter.normalize(selected);
        List<FeedStore.Row> next=new ArrayList<>();Set<String> used=new HashSet<>(presented);
        List<FeedStore.Row> eligible=SourceFilter.apply(ordered,selected);
        Map<String,FeedStore.Row> available=new HashMap<>();for(FeedStore.Row row:eligible)available.put(row.eventId,row);
        List<FeedStore.Row> staged=new ArrayList<>();
        // Keep already prepared body identities stable between collection cycles.
        // Body evidence/ranking callbacks must not continually throw away useful work.
        for(FeedStore.Row old:future){FeedStore.Row now=available.get(old.eventId);if(now!=null&&sameBody(old,now))staged.add(now);}
        staged.addAll(eligible);
        for(FeedStore.Row row:staged){
            boolean seen=false;for(HotCandidate member:row.entries)if(used.contains(member.id)){seen=true;break;}
            if(seen)continue;
            next.add(row);for(HotCandidate member:row.entries)used.add(member.id);

        }
        next=FeedSourceBalance.spread(next);
        future=Collections.unmodifiableList(new ArrayList<>(next.subList(0,Math.min(next.size(),FeedBatch.SIZE*ROUNDS))));
    }
    public List<FeedStore.Row> future(){return future;}
    public List<String> next(String selected){
        if(!filter.equals(SourceFilter.normalize(selected)))return Collections.emptyList();
        List<String> ids=new ArrayList<>();for(FeedStore.Row row:future){ids.add(row.eventId);if(ids.size()==FeedBatch.SIZE)break;}return ids;
    }
    public List<FeedStore.Row> plan(String selected){
        return filter.equals(SourceFilter.normalize(selected))?new ArrayList<>(future):Collections.emptyList();
    }
    /** Revalidate the actual body identities against the fresh eligible ranking.
     * Learning a body's language changes the source version, not that body's identity. */
    public static List<FeedStore.Row> promotePrepared(List<FeedStore.Row> current,List<FeedStore.Row> planned){
        Map<String,FeedStore.Row> available=new HashMap<>();for(FeedStore.Row row:current)available.put(row.eventId,row);
        List<String> preferred=new ArrayList<>();
        for(FeedStore.Row old:planned){FeedStore.Row now=available.get(old.eventId);
            if(now!=null&&sameBody(old,now)){preferred.add(now.eventId);}}
        return promote(current,preferred);
    }
    private static boolean sameBody(FeedStore.Row old,FeedStore.Row now){
        if(!old.candidate.id.equals(now.candidate.id)||!old.candidate.textHash.equals(now.candidate.textHash)||!old.candidate.originalUrl.equals(now.candidate.originalUrl)||old.entries.size()!=now.entries.size())return false;
        Map<String,String> members=new HashMap<>();for(HotCandidate member:old.entries)members.put(member.id,member.textHash);
        for(HotCandidate member:now.entries)if(!member.textHash.equals(members.get(member.id)))return false;
        return true;
    }
    /** Commit only identities still present in the validated, current ranking. */
    public static List<FeedStore.Row> promote(List<FeedStore.Row> current,List<String> preferred){
        if(preferred.isEmpty())return current;
        Map<String,FeedStore.Row> remaining=new LinkedHashMap<>();for(FeedStore.Row row:current)remaining.put(row.eventId,row);
        List<FeedStore.Row> result=new ArrayList<>();
        for(String id:preferred){FeedStore.Row row=remaining.remove(id);if(row==null)return current;result.add(row);}
        result.addAll(remaining.values());return result;
    }
    public void clear(){future=Collections.emptyList();}
}
