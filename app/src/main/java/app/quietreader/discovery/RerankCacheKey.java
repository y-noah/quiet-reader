package app.quietreader.discovery;

/** A raw model logit depends on this input pair, never on platform preferences
 * or unrelated profile examples. Current grouping and safeguards run afresh. */
public final class RerankCacheKey {
    private RerankCacheKey(){}
    public static String of(String modelRevision,String query,String candidateText){
        if(modelRevision==null||modelRevision.isEmpty())throw new IllegalArgumentException("Model revision required");
        return HotCandidate.hash("rerank-pair-v1\n"+modelRevision+"\n"+SemanticOrder.VERSION+"\n"+SemanticOrder.key(query)+"\n"+SemanticOrder.key(candidateText));
    }
}
