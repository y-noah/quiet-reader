package app.quietreader.discovery;

/** Share the existing total foreground deadline; do not reserve a personal
 * reranker phase when there are no content-interest samples to rerank against. */
public final class EventVerificationBudget {
    public static final String VERSION="event-dispatch-budget-v2-cold-profile";
    private EventVerificationBudget(){}
    public static long deadline(long now,long overallDeadline,boolean hasContentSamples){
        if(now<0||overallDeadline<0)throw new IllegalArgumentException("Nonnegative elapsed time required");
        long window=hasContentSamples?3000:8000,reserve=hasContentSamples?7000:1000;
        long bounded=now>Long.MAX_VALUE-window?Long.MAX_VALUE:now+window;
        return Math.min(overallDeadline-reserve,bounded);
    }
}
