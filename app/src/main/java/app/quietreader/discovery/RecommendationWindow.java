package app.quietreader.discovery;

import java.util.*;

/** A finite interest selection. Rebuilding a feed never fills it with rejected old rows. */
public final class RecommendationWindow {
    public static final int POOL_SIZE=300, SIZE=75;
    private RecommendationWindow(){}
    public static String key(HotCandidate candidate){return candidate.id+"@"+candidate.textHash;}
    public static boolean startsNewWindow(List<FeedStore.Row> ranked,Map<String,Boolean> decisions,Set<String> delivered){
        boolean fresh=false;
        for(FeedStore.Row row:ranked)for(HotCandidate member:row.entries){
            if(delivered.contains(member.id))continue;
            Boolean decision=decisions.get(key(member));if(Boolean.TRUE.equals(decision))return false;if(decision==null)fresh=true;
        }
        return fresh;
    }
    public static List<FeedStore.Row> select(List<FeedStore.Row> ranked,Map<String,Boolean> decisions,Set<String> delivered){
        return select(ranked,decisions,delivered,Collections.emptySet(),false);
    }
    /** A first completed model pass may refine provisional, unshown choices.
     * Already delivered provisional choices consume this window's 75 slots. */
    public static List<FeedStore.Row> select(List<FeedStore.Row> ranked,Map<String,Boolean> decisions,Set<String> delivered,Set<String> provisional,boolean refine){
        Set<String> deliveredProvisional=new HashSet<>();
        if(refine)for(String key:provisional){int at=key.indexOf('@');if(at>0&&delivered.contains(key.substring(0,at))&&Boolean.TRUE.equals(decisions.get(key)))deliveredProvisional.add(key.substring(0,at));}
        if(refine)for(FeedStore.Row row:ranked)for(HotCandidate member:row.entries){String key=key(member);if(provisional.contains(key)){if(!delivered.contains(member.id))decisions.remove(key);}}
        List<FeedStore.Row> result=new ArrayList<>();Set<String> chosen=new HashSet<>();int pending=0;
        for(FeedStore.Row row:ranked){
            boolean selected=false,known=false,shown=false;
            for(HotCandidate member:row.entries){String key=key(member);known|=decisions.containsKey(key);selected|=Boolean.TRUE.equals(decisions.get(key));shown|=delivered.contains(member.id);}
            if(row.interestBand==2||(!selected&&known)||shown&&!selected)continue;
            if(!shown&&pending>=SIZE-deliveredProvisional.size())continue;
            result.add(row);if(!shown)pending++;
            for(HotCandidate member:row.entries)chosen.add(key(member));
        }
        // A better new candidate may displace an unshown selection. Remember that
        // rejection too: consuming the newcomer must not backfill the old tail.
        for(FeedStore.Row row:ranked)for(HotCandidate member:row.entries){String key=key(member);if(!delivered.contains(member.id))decisions.put(key,chosen.contains(key));else decisions.putIfAbsent(key,chosen.contains(key));}
        return result;
    }
    public static List<FeedStore.Row> undelivered(List<FeedStore.Row> rows,Set<String> delivered){
        List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:rows){boolean shown=false;for(HotCandidate member:row.entries)if(delivered.contains(member.id)){shown=true;break;}if(!shown)result.add(row);}
        return result;
    }
}
