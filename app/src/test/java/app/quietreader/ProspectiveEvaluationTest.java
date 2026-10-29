package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProspectiveEvaluationTest {
    private HotCandidate candidate(int id,String title){return new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/"+id,title,"摘要","",false,id,null,100);}
    private FeedStore.Row row(HotCandidate c){return new FeedStore.Row(c.id,c,"");}
    private String hash(String s){return HotCandidate.hash(s);}
    private JSONObject baseline()throws Exception{
        return new JSONObject().put("protocol_schema",1).put("holdout_qualified",false).put("id","protocol-one").put("policy",ProspectiveEvaluation.POLICY).put("ranking_policies",ProspectiveEvaluation.policies())
            .put("cutoff_ms",150).put("app_epoch_ms",1).put("profile_version",2).put("model_revision",hash("model")).put("source_config_sha256",hash("sources"))
            .put("ledger_sha256",hash("ledger")).put("active_feedback_sha256",hash("active"))
            .put("known_identities",new JSONArray()).put("known_text_hashes",new JSONArray()).put("used_identities",new JSONArray()).put("used_text_hashes",new JSONArray());
    }
    private JSONObject learning(HotCandidate c)throws Exception{
        return new JSONObject().put("schema",1).put("holdout_qualified",false).put("learning_cutoff_ms",300).put("profile_version",2).put("source_version",3).put("ledger_rows",1).put("latest_feedback_at_ms",100)
            .put("ledger_sha256",hash("ledger")).put("active_feedback_sha256",hash("active")).put("known_learning_identities",new JSONArray())
            .put("candidate_events",new JSONArray().put(new JSONObject().put("event_key",c.id).put("members",new JSONArray().put(new JSONObject().put("id",c.id).put("text_hash",c.textHash).put("first_seen_ms",200)))));
    }
    @Test public void excludesOldReappearingAndCopiedCandidatesAndMissingTimes()throws Exception{
        HotCandidate old=candidate(1,"原来的中文讨论"),copy=candidate(2,"原来的中文讨论"),fresh=candidate(3,"一个新出现的中文讨论"),missing=candidate(4,"没有时间的中文讨论"),early=candidate(5,"刚好截止的中文讨论");
        JSONObject baseline=baseline().put("known_identities",new JSONArray().put(old.id)).put("known_text_hashes",new JSONArray().put(old.textHash));
        List<FeedStore.Row> selected=ProspectiveEvaluation.select(List.of(row(old),row(copy),row(fresh),row(missing),row(early)),baseline,Map.of(old.id,200L,copy.id,200L,fresh.id,200L,early.id,150L));
        assertEquals(1,selected.size());assertEquals(fresh.id,selected.get(0).eventId);
    }
    @Test public void mixedGroupCannotSmuggleAnOldMemberAndUsedEventsAreExcluded()throws Exception{
        HotCandidate old=candidate(1,"旧讨论"),fresh=candidate(2,"新讨论");JSONObject baseline=baseline();
        FeedStore.Row mixed=new FeedStore.Row("group-"+hash("group"),fresh,"",List.of(fresh,old));
        assertTrue(ProspectiveEvaluation.select(List.of(mixed),baseline,Map.of(old.id,100L,fresh.id,200L)).isEmpty());
        baseline.put("used_identities",new JSONArray().put(fresh.id));assertTrue(ProspectiveEvaluation.select(List.of(row(fresh)),baseline,Map.of(fresh.id,200L)).isEmpty());
    }
    @Test public void requiresStableLearningModelInstallationSourcesAndPolicies()throws Exception{
        JSONObject baseline=baseline(),learning=learning(candidate(2,"新讨论"));
        ProspectiveEvaluation.requireCurrent(baseline,learning,hash("model"),1,hash("sources"));
        assertThrows(IllegalArgumentException.class,()->ProspectiveEvaluation.requireCurrent(baseline,learning,hash("new model"),1,hash("sources")));
        assertThrows(IllegalArgumentException.class,()->ProspectiveEvaluation.requireCurrent(baseline,learning,hash("model"),2,hash("sources")));
        assertThrows(IllegalArgumentException.class,()->ProspectiveEvaluation.requireCurrent(baseline,learning,hash("model"),1,hash("different sources")));
        assertThrows(IllegalArgumentException.class,()->ProspectiveEvaluation.requireSettings(baseline,3,hash("model"),1,hash("sources")));
        learning.put("ledger_sha256",hash("changed"));assertThrows(IllegalArgumentException.class,()->ProspectiveEvaluation.requireCurrent(baseline,learning,hash("model"),1,hash("sources")));
        baseline.put("ranking_policies","old policy");ProspectiveEvaluation.validate(baseline);assertThrows(IllegalArgumentException.class,()->ProspectiveEvaluation.requireSettings(baseline,2,hash("model"),1,hash("sources")));
    }
    @Test public void prospectiveSessionCarriesBaselineWithoutClaimingIndependence()throws Exception{
        HotCandidate c=candidate(2,"新出现的中文讨论");List<FeedStore.Row> rows=List.of(row(c));
        JSONObject baseline=baseline();RecommendationComparison comparison=new RecommendationComparison(rows,rows,rows,hash("model"),"profile",1,2,3,0).withLearning(learning(c),400).withProtocol(baseline);
        baseline.put("id","mutated");assertEquals("protocol-one",comparison.protocol().getString("id"));
        JSONObject frozen=RecommendationTrialStore.freezeComparison(comparison,400,new Random(1));RecommendationTrialStore.validate(frozen);assertFalse(frozen.getBoolean("holdout_qualified"));
        frozen.getJSONObject("prospective_protocol").getJSONArray("known_identities").put(c.id);
        assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.validate(frozen));
    }
    @Test public void oldSafeWrapperIdentityCannotReenterUnderItsNewPrimaryGroup()throws Exception{
        HotCandidate c=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/991011","如何看待县委书记回应被扔下水？","","",false,1,null,100);
        String legacy="group-"+HotCandidate.hash("hot-title-v1\n如何看待县委书记回应被扔下水\n0");
        JSONObject baseline=baseline().put("used_identities",new JSONArray().put(legacy));List<FeedStore.Row> rows=CandidateOrder.forModels(List.of(c),new JSONArray());
        assertNotEquals(legacy,rows.get(0).eventId);assertTrue(ProspectiveEvaluation.select(rows,baseline,Map.of(c.id,200L)).isEmpty());
    }
}
