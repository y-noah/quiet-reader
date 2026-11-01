package app.quietreader.discovery;

import java.util.*;

/** Delivery history is not reading feedback: unseen first, then oldest delivered. */
public final class FeedPresentationOrder {
    private FeedPresentationOrder(){}
    public static List<FeedStore.Row> arrange(List<FeedStore.Row> ranked,Map<String,Long> delivered,long now){
        return arrange(ranked,delivered,now,null);
    }
    public static List<FeedStore.Row> arrange(List<FeedStore.Row> ranked,Map<String,Long> delivered,long now,PlatformPreference preference){
        return arrange(ranked,delivered,now,preference,0);
    }
    public static List<FeedStore.Row> arrange(List<FeedStore.Row> ranked,Map<String,Long> delivered,long now,PlatformPreference preference,long turn){
        List<FeedStore.Row> result=new ArrayList<>(ranked);
        result.sort(Comparator.comparingLong(row->lastDelivery(row,delivered,now)));
        return FeedSourceBalance.arrangeDelivered(result,preference,turn);
    }
    private static long lastDelivery(FeedStore.Row row,Map<String,Long> delivered,long now){
        long at=0;for(HotCandidate member:row.entries){long value=delivered.getOrDefault(member.id,0L);if(value>0&&value<=now)at=Math.max(at,value);}
        return at;
    }
}
