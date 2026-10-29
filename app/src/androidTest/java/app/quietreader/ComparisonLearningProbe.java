package app.quietreader;

import android.app.Instrumentation;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;

/** Actual SQLite capture, never touches the ordinary feed or any account. */
final class ComparisonLearningProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ComparisonLearningProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){String name="test-learning-"+UUID.randomUUID()+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
            long now=System.currentTimeMillis();HotCandidate candidate=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/987654","中文文字流讨论","验证真实数据库的首次观察与完整记录指纹","",false,1,null,now);
            long request=store.beginRefresh(Models.Source.V2EX);store.upsertSnapshot(Models.Source.V2EX,request,List.of(candidate),now);
            store.feedback("positive","MORE","sample",CandidateOrder.versionTarget(candidate),now,null,candidate.modelText(),candidate.source);
            JSONObject profile=store.profile(now+1);List<FeedStore.Row> rows=CandidateOrder.forModels(store.candidates(100,0),profile.getJSONArray("feedback"));
            JSONObject captured=store.comparisonLearning(rows,now+2,profile.getLong("version"),store.version("sources"));
            check(captured.getInt("ledger_rows")==1,"complete initial ledger counted");
            check(captured.getJSONArray("known_learning_identities").getString(0).equals(candidate.id),"versioned learning identity retained");
            check(captured.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).getLong("first_seen_ms")==now,"original database first observation captured");
            check(ComparisonLearning.candidateIdentities(captured.getJSONArray("candidate_events").getJSONObject(0)).equals(Set.of(rows.get(0).eventId,candidate.id)),"captured static group alias and original member identity are both retained");
            check(!captured.getBoolean("holdout_qualified"),"pre-ranking observation never self-qualifies holdout");
            request=store.beginRefresh(Models.Source.V2EX);store.upsertSnapshot(Models.Source.V2EX,request,List.of(candidate),now+3);
            JSONObject refreshed=store.comparisonLearning(rows,now+4,store.version("profile"),store.version("sources"));
            check(refreshed.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).getLong("first_seen_ms")==now,"refresh cannot turn old event into future observation");
            check(refreshed.getString("ledger_sha256").equals(captured.getString("ledger_sha256")),"source refresh leaves ledger fingerprint stable");
            store.undo("positive",now+5);JSONObject undone=store.comparisonLearning(rows,now+6,store.version("profile"),store.version("sources"));
            check(undone.getInt("ledger_rows")==2,"undo row included in full ledger");
            check(!undone.getString("ledger_sha256").equals(captured.getString("ledger_sha256")),"undo changes full learning fingerprint");
            check(undone.getJSONArray("known_learning_identities").getString(0).equals(candidate.id),"undo does not erase known prior exposure");
            boolean stale=false;try{store.comparisonLearning(rows,now+7,profile.getLong("version"),store.version("sources"));}catch(IllegalStateException expected){stale=true;}
            check(stale,"rejects capture using stale preference version");
            check(store.activeFeedback(now+7).length()==0,"capture adds no learning feedback");
            JSONObject frozen=RecommendationTrialStore.freezeComparison(new RecommendationComparison(rows,rows,rows,"model","profile",1,store.version("profile"),store.version("sources"),0).withLearning(undone,now+8),now+9,new Random(1));
            RecommendationTrialStore.validate(frozen);check(frozen.has("learning_provenance"),"captured provenance reaches paired session payload");
            store.feedback("expired","LESS","sample",CandidateOrder.versionTarget(candidate),now+10,now+11,candidate.modelText(),candidate.source);
            JSONObject expiredProfile=store.profile(now+12);JSONObject expired=store.comparisonLearning(rows,now+12,expiredProfile.getLong("version"),store.version("sources"));
            check(expiredProfile.getJSONArray("feedback").length()==0&&expired.getInt("ledger_rows")==3,"expired feedback remains in historical ledger but not active profile");
            check(!expired.getString("ledger_sha256").equals(undone.getString("ledger_sha256")),"expired learning history affects ledger fingerprint");
            store.getWritableDatabase().delete("candidates","id=?",new String[]{candidate.id});
            request=store.beginRefresh(Models.Source.V2EX);store.upsertSnapshot(Models.Source.V2EX,request,List.of(candidate),now+13);
            JSONObject afterEviction=store.comparisonLearning(rows,now+14,store.version("profile"),store.version("sources"));
            check(afterEviction.getJSONArray("known_learning_identities").getString(0).equals(candidate.id),"candidate eviction does not erase prior learning identity");
            check(afterEviction.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).getLong("first_seen_ms")==now+13,"first observation is explicitly current database residency, not global event birth");
            store.feedback("platform","MORE","source",Models.Source.V2EX.name(),now+15,null,"",Models.Source.V2EX);
            JSONObject platform=store.comparisonLearning(rows,now+16,store.version("profile"),store.version("sources"));
            check(platform.getInt("ledger_rows")==4&&!platform.getString("ledger_sha256").equals(afterEviction.getString("ledger_sha256")),"platform preferences contribute to full learning fingerprint");
            check(platform.getJSONArray("known_learning_identities").length()==1,"platform preference is not misreported as a learned event");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{test.getTargetContext().deleteDatabase(name);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        Bundle result=new Bundle();result.putInt("checks",checks);result.putInt("failures",failures);result.putString("report",report.toString());test.finish(failures==0?-1:0,result);
    }
}
