package app.quietreader;

/** Monotonic, non-accumulating lease. Automatic callbacks may inspect, never renew. */
final class ActionPrefetchBudget {
    static final long WINDOW_MS=20_000;
    static final long BATCH_MAX_MS=120_000;
    private long deadline;
    void action(long now){deadline=Math.max(deadline,now+WINDOW_MS);}
    /** One accepted finite batch, never renewed by individual cache completions. */
    void batch(long now,int count){deadline=now+Math.min(BATCH_MAX_MS,Math.max(WINDOW_MS,Math.min(20,Math.max(0,count))*10_000L));}
    long remaining(long now){return Math.max(0,deadline-now);}
    boolean active(long now){return remaining(now)>0;}
    void clear(){deadline=0;}
}
