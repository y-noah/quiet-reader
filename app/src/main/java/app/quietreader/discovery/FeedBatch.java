package app.quietreader.discovery;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** One home presentation. The full ranked pool remains available for refresh. */
public final class FeedBatch {
    public static final int SIZE=20;
    private FeedBatch(){}
    public static List<FeedStore.Row> first(List<FeedStore.Row> ranked){
        return first(ranked,SIZE);
    }
    public static List<FeedStore.Row> first(List<FeedStore.Row> ranked,int capacity){
        return Collections.unmodifiableList(new ArrayList<>(ranked.subList(0,Math.min(Math.min(SIZE,Math.max(0,capacity)),ranked.size()))));
    }
    /** Initial viewport plus about one deliberate swipe. Measured only at publication. */
    public static int countForHeight(int available,int viewportHeight,IntUnaryOperator heightAt){
        if(available<=0)return 0;
        if(viewportHeight<=0)throw new IllegalArgumentException("Positive viewport required");
        long budget=(long)viewportHeight*8/5,used=0;int count=0;
        for(int n=0;n<Math.min(SIZE,available);n++){
            int height=heightAt.applyAsInt(n);
            if(height<=0)throw new IllegalArgumentException("Positive row height required");
            if(count>0&&used+height>budget)break;
            used+=height;count++;
            if(used>=budget)break;
        }
        return count;
    }
}
