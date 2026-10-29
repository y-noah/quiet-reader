package app.quietreader.discovery;

import java.util.*;
import java.util.function.Function;

/** Explicit presentation policy after eligibility and interest bands, never a model score. */
public final class FeedRefreshOrder {
    // Initial product parameters; real-use quality and battery acceptance remain separate.
    public static final long SHOWN_COOLDOWN_MS=2*60*60_000L;
    public static final long READ_COOLDOWN_MS=24*60*60_000L;
    private FeedRefreshOrder(){}
    /** Unclassified rows are barriers; a reason string must never be used as confidence. */
    public static List<FeedStore.Row> forRows(List<FeedStore.Row> ranked,Map<String,Recent> history,long now){
        if(now<0)throw new IllegalArgumentException("Invalid current time");
        // A refresh is a presentation of the same relevance order, not a new
        // relevance baseline. Restore it before applying current cooldowns.
        List<FeedStore.Row> base=new ArrayList<>(ranked);Set<Integer> positions=new HashSet<>();
        boolean complete=true;for(FeedStore.Row row:base)if(row.refreshBasePosition<0||row.refreshBasePosition>=base.size()||!positions.add(row.refreshBasePosition)){complete=false;break;}
        if(complete)base.sort(Comparator.comparingInt(row->row.refreshBasePosition));
        ranked=base;
        List<FeedStore.Row> result=new ArrayList<>();
        for(int start=0;start<ranked.size();){int band=ranked.get(start).interestBand,end=start+1;while(end<ranked.size()&&ranked.get(end).interestBand==band)end++;
            List<FeedStore.Row> segment=ranked.subList(start,end);if(band<0)result.addAll(segment);else result.addAll(arrange(Collections.singletonList(segment),row->history.get(row.eventId),now));start=end;
        }
        return Collections.unmodifiableList(result);
    }
    public static final class Recent {
        public final long shownAt,readAt,verifiedProgressAt;
        /** Progress must be independently verified; title/rank/cache changes are insufficient. */
        public Recent(long shownAt,long readAt,long verifiedProgressAt){this.shownAt=shownAt;this.readAt=readAt;this.verifiedProgressAt=verifiedProgressAt;}
    }
    /** Preserve quality boundaries and existing relevance order inside each exposure tier. */
    public static <T> List<T> arrange(List<? extends List<T>> qualityBands,Function<T,Recent> history,long now){
        if(now<0)throw new IllegalArgumentException("Invalid current time");
        List<T> result=new ArrayList<>();
        for(List<T> band:qualityBands){
            List<T> unseen=new ArrayList<>(),shown=new ArrayList<>(),read=new ArrayList<>();
            for(T item:band){Recent recent=history.apply(item);int tier=tier(recent,now);(tier==0?unseen:tier==1?shown:read).add(item);}
            result.addAll(unseen);result.addAll(shown);result.addAll(read);
        }
        return Collections.unmodifiableList(result);
    }
    private static int tier(Recent recent,long now){
        if(recent==null)return 0;
        long shown=valid(recent.shownAt,now)?recent.shownAt:0,read=valid(recent.readAt,now)?recent.readAt:0;
        if(valid(recent.verifiedProgressAt,now)&&recent.verifiedProgressAt>Math.max(shown,read))return 0;
        if(read>0&&now-read<READ_COOLDOWN_MS)return 2;
        return shown>0&&now-shown<SHOWN_COOLDOWN_MS?1:0;
    }
    private static boolean valid(long at,long now){return at>0&&at<=now;}
}
