package app.quietreader.discovery;

import java.util.*;

/** One home presentation. The full ranked pool remains available for refresh. */
public final class FeedBatch {
    public static final int SIZE=15;
    private FeedBatch(){}
    public static List<FeedStore.Row> first(List<FeedStore.Row> ranked){
        return first(ranked,SIZE);
    }
    public static List<FeedStore.Row> first(List<FeedStore.Row> ranked,int capacity){
        return Collections.unmodifiableList(new ArrayList<>(ranked.subList(0,Math.min(Math.min(SIZE,Math.max(0,capacity)),ranked.size()))));
    }
}
