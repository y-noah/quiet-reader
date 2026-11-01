package app.quietreader;

/** Non-accumulating lease. Only user actions or the authorized foreground refresh cycle renew it. */
final class ActionPrefetchBudget {
    static final long WINDOW_MS=20_000;
    private long deadline;
    void action(long now){deadline=Math.max(deadline,now+WINDOW_MS);}
    /** A finite current screen plus four future screens, at most one four-and-a-half-minute slice, leaving time for local ranking. */
    void reserve(long now,int count){deadline=now+Math.min(270_000L,Math.max(WINDOW_MS,Math.min(75,Math.max(0,count))*5_000L));}
    long remaining(long now){return Math.max(0,deadline-now);}
    boolean active(long now){return remaining(now)>0;}
    void clear(){deadline=0;}
}
