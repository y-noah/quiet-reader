package app.quietreader;

import android.app.Instrumentation;
import android.os.Bundle;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;

/** Real storage and model-comparison payload; all labels/candidates are synthetic. */
final class ProspectiveEvaluationProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    private final boolean export;
    private String snapshotFile,evidenceFile;
    ProspectiveEvaluationProbe(Instrumentation test){this(test,false);}
    ProspectiveEvaluationProbe(Instrumentation test,boolean export){this.test=test;this.export=export;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private HotCandidate candidate(int id,long at){return new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/"+id,"合成文字流热门讨论第"+id+"项","验证先准备后采集的受控样本，不代表真实新闻或用户兴趣。","",false,id,null,at);}
    void run(){String suffix=UUID.randomUUID().toString(),feed="test-prospective-"+suffix+".db",trial="test-trial-prospective-"+suffix+".db",legacy="test-trial-prospective-old-"+suffix+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),feed)){
            long now=System.currentTimeMillis();String model=HotCandidate.hash("model");HotCandidate old=candidate(1,now),fresh=candidate(2,now+3);
            long request=store.beginRefresh(Models.Source.V2EX);store.upsertSnapshot(Models.Source.V2EX,request,List.of(old),now);
            store.feedback("learned","MORE","sample",CandidateOrder.versionTarget(old),now,null,old.modelText(),old.source);
            JSONObject baseline=store.evaluationBaseline(now+1,model,1);check(!baseline.getBoolean("holdout_qualified"),"baseline is explicitly non-independent");
            try(RecommendationTrialStore trials=new RecommendationTrialStore(test.getTargetContext(),trial)){trials.protocol(baseline);}
            try(RecommendationTrialStore trials=new RecommendationTrialStore(test.getTargetContext(),trial)){check(trials.protocol().toString().equals(baseline.toString()),"baseline survives SQLite close and reopen");}
            try(RecommendationTrialStore trials=new RecommendationTrialStore(test.getTargetContext(),trial)){
                trials.protocol(new JSONObject(baseline.toString()).put("ranking_policies","previous-install-policy"));
                check(trials.protocol().getString("ranking_policies").equals("previous-install-policy"),"stale policy remains readable for menu replacement or clear");trials.protocol(baseline);
            }
            check(store.evaluationCandidates(baseline,now+2,model,1).isEmpty(),"existing pool cannot become a prospective trial");
            request=store.beginRefresh(Models.Source.V2EX);store.upsertSnapshot(Models.Source.V2EX,request,List.of(old,fresh),now+3);
            List<FeedStore.Row> rows=store.evaluationCandidates(baseline,now+4,model,1);check(rows.size()==1&&rows.get(0).candidate.id.equals(fresh.id),"later refresh admits only genuinely later database candidates");
            Set<String> consumed=new TreeSet<>();for(FeedStore.Row row:rows){consumed.add(row.eventId);for(HotCandidate member:row.entries)consumed.add(member.id);}
            JSONObject oldGrouping=new JSONObject(baseline.toString()).put("ranking_policies",ProspectiveEvaluation.policies().replace("/"+EventGroups.VERSION,""));
            boolean regrouped=false;try{store.evaluationCandidates(oldGrouping,now+4,model,1);}catch(IllegalArgumentException expected){regrouped=expected.getMessage().contains("规则已更新");}
            check(regrouped,"grouping-policy upgrade rejects old preparation without rewriting its stored record");
            JSONObject learning=store.comparisonLearning(rows,now+5,store.version("profile"),store.version("sources"));
            String profileSignature=new SemanticOrder.Profile(store.activeFeedback(now+5)).signature;
            RecommendationComparison comparison=new RecommendationComparison(rows,rows,rows,model,profileSignature,1,store.version("profile"),store.version("sources"),0).withLearning(learning,now+6).withProtocol(baseline);
            JSONObject frozen=RecommendationTrialStore.freezeComparison(comparison,now+7,new Random(1));String id;
            try(RecommendationTrialStore trials=new RecommendationTrialStore(test.getTargetContext(),trial)){
                id=trials.create(frozen);JSONObject stored=trials.read(id);RecommendationTrialStore.validate(stored);
                check(stored.getJSONObject("prospective_protocol").getString("id").equals(baseline.getString("id")),"session persists original pre-collection protocol");
                check(ProspectiveEvaluation.strings(trials.protocol().getJSONArray("used_identities")).equals(consumed),"pool consumption retains exactly event and member identities before any choice");
                check(store.evaluationCandidates(trials.protocol(),now+8,model,1).isEmpty(),"a second session cannot reuse consumed pool");
                boolean stale=false;try{trials.create(frozen);}catch(IllegalArgumentException expected){stale=true;}check(stale,"stale session cannot consume the same protocol state twice");
                trials.label(id,0,"want",now+8);check(store.activeFeedback(now+8).length()==1,"evaluation label does not train preferences");
                if(export)exportSynthetic(trials,store,baseline,now+8);
                for(int n=0;n<31;n++)trials.create(RecommendationTrialStore.freeze(rows,1,1,1,"all","base",now+10+n));
                check(trials.read(id)==null&&ProspectiveEvaluation.strings(trials.protocol().getJSONArray("used_identities")).equals(consumed),"thirty-session eviction does not forget consumed event or member identities");
                JSONObject snapshot=trials.protocol();store.feedback("platform","MORE","source",Models.Source.V2EX.name(),now+50,null,"",Models.Source.V2EX);
                boolean changed=false;try{store.evaluationCandidates(snapshot,now+51,model,1);}catch(IllegalArgumentException expected){changed=true;}check(changed,"changed platform preference invalidates the prepared baseline");
                store.enable(Models.Source.V2EX,false);boolean emptyChanged=false;try{store.evaluationCandidates(snapshot,now+52,model,1);}catch(IllegalArgumentException expected){emptyChanged=expected.getMessage().contains("来源设置");}check(emptyChanged,"changed source configuration is rejected even when no candidates remain");
                trials.clear();check(trials.protocol()==null&&trials.unfinished()==null,"explicit clear retires both trials and preparation state");
            }
            // Upgrade only the separate evaluation DB, preserving old session and label.
            try(SQLiteDatabase db=SQLiteDatabase.openOrCreateDatabase(test.getTargetContext().getDatabasePath(legacy),null)){
                db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER NOT NULL,payload TEXT NOT NULL)");db.execSQL("CREATE TABLE labels(session TEXT NOT NULL,event TEXT NOT NULL,choice TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(session,event))");
                JSONObject single=RecommendationTrialStore.freeze(rows,1,1,1,"all","base",now);ContentValues value=new ContentValues();value.put("id","old");value.put("at",now);value.put("payload",single.toString());db.insertOrThrow("sessions",null,value);db.execSQL("INSERT INTO labels VALUES(?,?,?,?)",new Object[]{"old",fresh.id,"unsure",now+1});db.setVersion(1);
            }
            try(RecommendationTrialStore upgraded=new RecommendationTrialStore(test.getTargetContext(),legacy)){check(upgraded.getReadableDatabase().getVersion()==2&&upgraded.protocol()==null,"v1 trial database upgrades with empty preparation state");check(upgraded.read("old").getJSONObject("labels").getJSONObject(fresh.id).getString("choice").equals("unsure"),"v1 upgrade preserves old labels and candidate payload");}
            metadataBridge(now,model);
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{for(String name:List.of(feed,trial,legacy))test.getTargetContext().deleteDatabase(name);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        Bundle result=new Bundle();result.putInt("checks",checks);result.putInt("failures",failures);result.putString("report",report.toString());if(snapshotFile!=null)result.putString("synthetic_snapshot_file",snapshotFile);if(evidenceFile!=null)result.putString("synthetic_evidence_file",evidenceFile);test.finish(failures==0?-1:0,result);
    }
    private void metadataBridge(long now,String model)throws Exception{
        String feed="test-prospective-bridge-"+UUID.randomUUID()+".db",trial="test-trial-bridge-"+UUID.randomUUID()+".db";
        try(FeedStore store=new FeedStore(test.getTargetContext(),feed)){
            HotCandidate seed=candidate(991001,now);store.upsertSnapshot(Models.Source.V2EX,store.beginRefresh(Models.Source.V2EX),List.of(seed),now);
            store.feedback("bridge-learned","MORE","sample",CandidateOrder.versionTarget(seed),now,null,seed.modelText(),seed.source);
            JSONObject baseline=store.evaluationBaseline(now+1,model,1);
            String title="研究团队公布新型电池测试结果",summary="研究团队公布新型电池的完整测试数据，说明了测试环境、循环次数和实际测量结果，后续将继续开展实验。";
            HotCandidate complete=new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/991002",title,summary,"",false,1,now+3,now+3);
            HotCandidate brief=new HotCandidate(Models.Source.WEIBO,"https://weibo.com/123/Abc991002","如何看待"+title+"？","","",false,1,null,now+3);
            store.upsertSnapshot(complete.source,store.beginRefresh(complete.source),List.of(complete),now+3);store.upsertSnapshot(brief.source,store.beginRefresh(brief.source),List.of(brief),now+3);
            List<FeedStore.Row> rows=store.evaluationCandidates(baseline,now+4,model,1);check(rows.size()==1&&rows.get(0).entries.size()==2,"prospective collection admits a genuinely new bridged event with both source members");
            String weakKey=CandidateOrder.forModels(List.of(brief),new JSONArray()).get(0).eventId,strongKey=CandidateOrder.forModels(List.of(complete),new JSONArray()).get(0).eventId;
            JSONObject learning=store.comparisonLearning(rows,now+5,store.version("profile"),store.version("sources"));
            Set<String> aliases=ProspectiveEvaluation.strings(learning.getJSONArray("candidate_events").getJSONObject(0).getJSONArray("identity_aliases"));
            check(aliases.contains(weakKey)&&aliases.contains(strongKey),"pre-ranking provenance captures both static identities behind a bridged card");
            RecommendationComparison comparison=new RecommendationComparison(rows,rows,rows,model,new SemanticOrder.Profile(store.activeFeedback(now+5)).signature,1,store.version("profile"),store.version("sources"),0).withLearning(learning,now+6).withProtocol(baseline);
            JSONObject frozen=RecommendationTrialStore.freezeComparison(comparison,now+7,new Random(2));
            try(RecommendationTrialStore trials=new RecommendationTrialStore(test.getTargetContext(),trial)){trials.protocol(baseline);trials.create(frozen);Set<String> used=ProspectiveEvaluation.strings(trials.protocol().getJSONArray("used_identities"));check(used.containsAll(Set.of(weakKey,strongKey,complete.id,brief.id)),"consuming a bridged evaluation durably consumes both metadata identities and original members");}
            JSONObject consumed;try(RecommendationTrialStore reopened=new RecommendationTrialStore(test.getTargetContext(),trial)){consumed=reopened.protocol();check(ProspectiveEvaluation.strings(consumed.getJSONArray("used_identities")).containsAll(Set.of(weakKey,strongKey)),"SQLite reopen retains consumed weak and complete group identities");}
            HotCandidate replacementA=new HotCandidate(complete.source,"https://www.zhihu.com/question/991003","这是取代旧记录的无关独立问题甲","","",false,1,null,now+8),replacementB=new HotCandidate(brief.source,"https://weibo.com/123/Abc991003","这是取代旧记录的无关独立问题乙","","",false,1,null,now+8);
            HotCandidate unseenBrief=new HotCandidate(Models.Source.ITHOME,"https://www.ithome.com/1/991/002.htm","热议："+title,"","",false,1,null,now+8);
            store.upsertSnapshot(replacementA.source,store.beginRefresh(replacementA.source),List.of(replacementA),now+8);store.upsertSnapshot(replacementB.source,store.beginRefresh(replacementB.source),List.of(replacementB),now+8);store.upsertSnapshot(unseenBrief.source,store.beginRefresh(unseenBrief.source),List.of(unseenBrief),now+8);
            List<FeedStore.Row> later=store.evaluationCandidates(consumed,now+9,model,1);check(later.stream().flatMap(row->row.entries.stream()).noneMatch(c->c.id.equals(unseenBrief.id))&&later.stream().anyMatch(row->row.candidate.id.equals(replacementA.id)),"new headline URL cannot reenter evaluation after all original bridge members leave active snapshots");
        }finally{test.getTargetContext().deleteDatabase(feed);test.getTargetContext().deleteDatabase(trial);}
    }
    /** Explicit diagnostic mode only: both inputs were created by this probe. */
    private void exportSynthetic(RecommendationTrialStore trials,FeedStore learning,JSONObject baseline,long now)throws Exception{
        if((test.getTargetContext().getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0)throw new IllegalStateException("Synthetic export requires diagnostic build");
        java.io.File folder=test.getTargetContext().getExternalFilesDir(null);String suffix=UUID.randomUUID().toString();
        java.io.File snapshot=new java.io.File(folder,"synthetic-prospective-review-"+suffix+".db"),evidence=new java.io.File(folder,"synthetic-learning-evidence-"+suffix+".json");
        SQLiteDatabase source=trials.getReadableDatabase();source.beginTransaction();try(SQLiteDatabase destination=SQLiteDatabase.openOrCreateDatabase(snapshot,null)){
            destination.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER,payload TEXT)");destination.execSQL("CREATE TABLE labels(session TEXT,event TEXT,choice TEXT,at INTEGER)");
            try(android.database.Cursor cursor=source.rawQuery("SELECT id,at,payload FROM sessions",null)){while(cursor.moveToNext())destination.execSQL("INSERT INTO sessions VALUES(?,?,?)",new Object[]{cursor.getString(0),cursor.getLong(1),cursor.getString(2)});}
            try(android.database.Cursor cursor=source.rawQuery("SELECT session,event,choice,at FROM labels",null)){while(cursor.moveToNext())destination.execSQL("INSERT INTO labels VALUES(?,?,?,?)",new Object[]{cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getLong(3)});}
            destination.setVersion(2);source.setTransactionSuccessful();
        }finally{source.endTransaction();}
        JSONObject audit=new JSONObject().put("kind","synthetic-learning-evidence").put("real_user_choices",false).put("protocol_id",baseline.getString("id"))
            .put("ledger_sha256",baseline.getString("ledger_sha256")).put("profile",learning.profile(now)).put("notice","Only probe-created feedback and candidates; no account, Cookie or real user learning data.");
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(evidence)){output.write(audit.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        check(snapshot.setReadable(true,false)&&evidence.setReadable(true,false),"new synthetic-only export copies are readable without changing original DB permissions");
        check(snapshot.length()>0&&evidence.length()>0,"actual Android synthetic session and learning evidence exported");snapshotFile=snapshot.getName();evidenceFile=evidence.getName();
    }
}
