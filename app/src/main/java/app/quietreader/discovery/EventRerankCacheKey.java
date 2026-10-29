package app.quietreader.discovery;

/** Directional raw logits stay reusable across metadata refreshes and policy changes. */
public final class EventRerankCacheKey {
    public static final String COVERAGE_VERSION="balanced-pair256-coverage-v1";
    private EventRerankCacheKey(){}
    public static String of(EventVerificationPlan.Task task,boolean reverse){
        String query=reverse?task.rightInput:task.leftInput,text=reverse?task.leftInput:task.rightInput;
        return HotCandidate.hash("event-direction-v1\n"+task.cacheKey+"\n"+HotCandidate.hash(query)+"\n"+HotCandidate.hash(text));
    }
    public static String coverage(String scoreKey){return HotCandidate.hash(COVERAGE_VERSION+"\n"+scoreKey);}
}
