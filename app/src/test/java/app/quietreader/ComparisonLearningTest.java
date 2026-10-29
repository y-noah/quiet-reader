package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ComparisonLearningTest {
    private final HotCandidate candidate=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/123","中文讨论","内容摘要","",false,1,null,100);
    private List<FeedStore.Row> rows(){return List.of(new FeedStore.Row(candidate.id,candidate,""));}
    private JSONObject capture()throws Exception{
        JSONObject member=new JSONObject().put("id",candidate.id).put("text_hash",candidate.textHash).put("first_seen_ms",100);
        return new JSONObject().put("schema",1).put("holdout_qualified",false).put("learning_cutoff_ms",150).put("profile_version",2).put("source_version",3)
            .put("ledger_rows",1).put("latest_feedback_at_ms",120).put("ledger_sha256",HotCandidate.hash("ledger")).put("active_feedback_sha256",HotCandidate.hash("active"))
            .put("known_learning_identities",new JSONArray().put(candidate.id))
            .put("candidate_events",new JSONArray().put(new JSONObject().put("event_key",candidate.id).put("members",new JSONArray().put(member))));
    }
    @Test public void identitiesIncludeVersionedReadSavedAndGroupedButNotTopics(){
        String id=candidate.id,other=HotCandidate.hash("other");
        assertEquals(Set.of(id),ComparisonLearning.eventIdentities("sample",id+"@"+candidate.textHash));
        assertEquals(Set.of(id),ComparisonLearning.eventIdentities("sample","read:"+id+":"+candidate.textHash));
        assertEquals(Set.of(id),ComparisonLearning.eventIdentities("sample","saved:"+id));
        assertEquals(Set.of(id,other),ComparisonLearning.eventIdentities("event","{\"format\":\"event-targets-v1\",\"targets\":[\""+id+"\",\""+other+"\"]}"));
        assertTrue(ComparisonLearning.eventIdentities("entity",id).isEmpty());
        assertTrue(ComparisonLearning.eventIdentities("source",id).isEmpty());
        assertTrue(ComparisonLearning.eventIdentities("sample","一个关注的主题").isEmpty());
    }
    @Test public void preRankingCaptureIsCopiedAndSurvivesTrialFreeze()throws Exception{
        JSONObject value=capture();RecommendationComparison base=new RecommendationComparison(rows(),rows(),rows(),"model","profile",1,2,3,4);
        RecommendationComparison frozen=base.withLearning(value,200);value.put("ledger_rows",999);
        assertEquals(1,frozen.learning().getInt("ledger_rows"));JSONObject exposed=frozen.learning();exposed.put("ledger_rows",998);
        assertEquals(1,frozen.learning().getInt("ledger_rows"));assertNull(base.learning());
        JSONObject trial=RecommendationTrialStore.freezeComparison(frozen,200,new Random(1));RecommendationTrialStore.validate(trial);
        assertFalse(trial.getBoolean("holdout_qualified"));assertEquals(100,trial.getJSONObject("learning_provenance").getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).getLong("first_seen_ms"));
    }
    @Test public void refusesTimeTravelVersionsAndFalseQualification()throws Exception{
        JSONObject future=capture().put("learning_cutoff_ms",201);assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validate(future,2,3,200));
        JSONObject latest=capture().put("latest_feedback_at_ms",151);assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validate(latest,2,3,200));
        JSONObject qualified=capture().put("holdout_qualified",true);assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validate(qualified,2,3,200));
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validate(capture(),9,3,200));
        JSONObject first=capture();first.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).put("first_seen_ms",160);
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validate(first,2,3,200));
    }
    @Test public void refusesPoolOrContentReplacementAtAttachmentAndPersistence()throws Exception{
        JSONObject changed=capture();changed.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).put("text_hash",HotCandidate.hash("changed"));
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validatePool(changed,rows()));
        RecommendationComparison comparison=new RecommendationComparison(rows(),rows(),rows(),"model","profile",1,2,3,4).withLearning(capture(),200);
        JSONObject trial=RecommendationTrialStore.freezeComparison(comparison,200,new Random(1));
        trial.getJSONObject("learning_provenance").getJSONArray("candidate_events").getJSONObject(0).put("event_key","wrong");
        assertThrows(IllegalArgumentException.class,()->RecommendationTrialStore.validate(trial));
    }
    @Test public void staticAliasesAreCheckedAgainstCapturedMembersAndLegacyScopeIsUnchanged()throws Exception{
        JSONObject old=capture(),event=old.getJSONArray("candidate_events").getJSONObject(0);
        assertEquals(Set.of(candidate.id),ComparisonLearning.candidateIdentities(event));
        String thread=CandidateOrder.forModels(List.of(candidate),new JSONArray()).get(0).eventId;
        event.put("identity_aliases",new JSONArray().put(thread));ComparisonLearning.validate(old,2,3,200);ComparisonLearning.validatePool(old,rows());
        assertEquals(Set.of(candidate.id,thread),ComparisonLearning.candidateIdentities(event));
        event.put("identity_aliases",new JSONArray().put("group-"+HotCandidate.hash("unrelated")));
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validatePool(old,rows()));
    }
    @Test public void malformedAndRepeatedGroupAliasesCannotEnterProvenance()throws Exception{
        for(JSONArray aliases:List.of(new JSONArray().put("not-an-identity"),new JSONArray().put(candidate.id).put(candidate.id))){JSONObject value=capture();value.getJSONArray("candidate_events").getJSONObject(0).put("identity_aliases",aliases);assertThrows(IllegalArgumentException.class,()->ComparisonLearning.validate(value,2,3,200));}
    }
    @Test public void regroupConservesPreRankingLedgerAndExactMembers()throws Exception{
        HotCandidate second=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/321","另一个真实中文标题","摘要","",false,1,null,100);
        JSONObject before=capture();JSONObject member=new JSONObject().put("id",second.id).put("text_hash",second.textHash).put("first_seen_ms",110);before.getJSONArray("candidate_events").put(new JSONObject().put("event_key",second.id).put("members",new JSONArray().put(member)));
        FeedStore.Row grouped=new FeedStore.Row(candidate.id,candidate,"",List.of(candidate,second));List<FeedStore.Row> rows=List.of(grouped);JSONObject after=ComparisonLearning.regroup(before,rows);
        assertEquals(2,before.getJSONArray("candidate_events").length());assertEquals(1,after.getJSONArray("candidate_events").length());assertEquals(EventGroupAssembly.VERSION,after.getString("grouping_policy"));
        for(String key:List.of("learning_cutoff_ms","ledger_sha256","active_feedback_sha256","latest_feedback_at_ms","profile_version","source_version"))assertEquals(before.get(key),after.get(key));
        ComparisonLearning.validatePool(after,rows);assertEquals(110,after.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(1).getLong("first_seen_ms"));
        RecommendationComparison comparison=new RecommendationComparison(rows,rows,rows,"model","profile",1,2,3,4).withLearning(before,200);assertEquals(1,comparison.learning().getJSONArray("candidate_events").length());
        assertEquals(comparison.learning().toString(),comparison.withLearning(comparison.learning(),300).learning().toString());
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.regroup(before,this.rows()));
        HotCandidate changed=new HotCandidate(second.source,second.originalUrl,second.title,"变更内容","",false,1,null,100);
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.regroup(before,List.of(new FeedStore.Row(candidate.id,candidate,"",List.of(candidate,changed)))));
        JSONObject alias=new JSONObject(before.toString());alias.getJSONArray("candidate_events").getJSONObject(1).put("identity_aliases",new JSONArray().put("group-"+HotCandidate.hash("unrelated")));
        assertThrows(IllegalArgumentException.class,()->ComparisonLearning.regroup(alias,rows));
    }
}
