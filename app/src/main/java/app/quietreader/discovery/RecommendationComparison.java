package app.quietreader.discovery;

import java.util.*;

/** Same-run technical orders. Contains no inferred relevance labels or holdout claim. */
public final class RecommendationComparison {
    public final List<FeedStore.Row> neutral,embedding,dual;
    public final String modelRevision,profileSignature;
    public final long generation,profileVersion,sourceVersion;
    public final int finiteRerankScores;
    private String learningProvenance;
    private String prospectiveProtocol;
    public RecommendationComparison withProtocol(org.json.JSONObject baseline){
        try{
            ProspectiveEvaluation.validate(baseline);
            if(learningProvenance==null||!modelRevision.equals(baseline.getString("model_revision")))throw new IllegalArgumentException("模型版本与准备时不同，请重新准备新话题评价。");
            RecommendationComparison copy=withLearning(learning(),Long.MAX_VALUE);copy.prospectiveProtocol=ProspectiveEvaluation.sessionProtocol(baseline).toString();return copy;
        }catch(org.json.JSONException error){throw new IllegalArgumentException(error);}
    }
    public org.json.JSONObject protocol(){try{return prospectiveProtocol==null?null:new org.json.JSONObject(prospectiveProtocol);}catch(org.json.JSONException error){throw new IllegalStateException(error);}}
    /** Copies the pre-ranking capture; callers cannot mutate saved provenance. */
    public RecommendationComparison withLearning(org.json.JSONObject value,long rankedAt){
        try{ComparisonLearning.validate(value,profileVersion,sourceVersion,rankedAt);org.json.JSONObject grouped=ComparisonLearning.regroup(value,neutral);
            RecommendationComparison copy=new RecommendationComparison(neutral,embedding,dual,modelRevision,profileSignature,generation,profileVersion,sourceVersion,finiteRerankScores);
            copy.learningProvenance=grouped.toString();return copy;
        }catch(org.json.JSONException error){throw new IllegalArgumentException(error);}
    }
    public org.json.JSONObject learning(){try{return learningProvenance==null?null:new org.json.JSONObject(learningProvenance);}catch(org.json.JSONException error){throw new IllegalStateException(error);}}
    public RecommendationComparison(List<FeedStore.Row> neutral,List<FeedStore.Row> embedding,List<FeedStore.Row> dual,
            String revision,String signature,long generation,long profileVersion,long sourceVersion,int scores){
        Set<String> pool=identities(neutral);
        if(pool.isEmpty()||!pool.equals(identities(embedding))||!pool.equals(identities(dual))
                ||revision==null||revision.isEmpty()||signature==null||signature.isEmpty()||scores<0)
            throw new IllegalArgumentException("Incomplete comparison provenance or event pool");
        this.neutral=Collections.unmodifiableList(new ArrayList<>(neutral));
        this.embedding=Collections.unmodifiableList(new ArrayList<>(embedding));
        this.dual=Collections.unmodifiableList(new ArrayList<>(dual));
        modelRevision=revision;profileSignature=signature;this.generation=generation;this.profileVersion=profileVersion;this.sourceVersion=sourceVersion;finiteRerankScores=scores;
    }
    private static Set<String> identities(List<FeedStore.Row> rows){
        Set<String> ids=new HashSet<>();for(FeedStore.Row row:rows)if(row==null||row.eventId==null||row.eventId.isEmpty()||!ids.add(row.eventId))throw new IllegalArgumentException("Invalid comparison event identity");return ids;
    }
}
