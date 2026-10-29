package app.quietreader;

import android.app.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** Synthetic-only production snapshot export and failure boundaries on real Android SQLite. */
final class TrialObservationSnapshotProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    TrialObservationSnapshotProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private long count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){c.moveToFirst();return c.getLong(0);}}
    void run(){String suffix=UUID.randomUUID().toString(),name="test-trial-snapshot-"+suffix+".db",feed="test-snapshot-"+suffix+".db";File output=new File(test.getTargetContext().getExternalFilesDir(null),"synthetic-observation-sanitized-"+suffix+".db"),failed=new File(test.getTargetContext().getCacheDir(),"snapshot-failed-"+suffix+".db");
        try(RecommendationTrialStore trials=new RecommendationTrialStore(test.getTargetContext(),name);FeedStore store=new FeedStore(test.getTargetContext(),feed)){
            long now=System.currentTimeMillis();HotCandidate old=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/920001","私有合成标题不能导出","私有摘要不能导出","",false,1,null,now),fresh=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/920002","新的私有标题不能导出","新的私有摘要不能导出","",false,2,null,now+3);
            store.upsertSnapshot(Models.Source.V2EX,store.beginRefresh(Models.Source.V2EX),List.of(old),now);store.feedback("synthetic-positive","MORE","sample",CandidateOrder.versionTarget(old),now,null,old.modelText(),old.source);
            JSONObject baseline=store.evaluationBaseline(now+1,HotCandidate.hash("model"),1);trials.protocol(baseline);
            store.upsertSnapshot(Models.Source.V2EX,store.beginRefresh(Models.Source.V2EX),List.of(old,fresh),now+3);List<FeedStore.Row> rows=store.evaluationCandidates(baseline,now+4,HotCandidate.hash("model"),1);
            JSONObject learning=store.comparisonLearning(rows,now+5,store.version("profile"),store.version("sources"));RecommendationComparison comparison=new RecommendationComparison(rows,rows,rows,HotCandidate.hash("model"),new SemanticOrder.Profile(store.activeFeedback(now+5)).signature,1,store.version("profile"),store.version("sources"),0).withLearning(learning,now+6).withProtocol(baseline);
            JSONObject paired=RecommendationTrialStore.freezeComparison(comparison,now+7,new Random(1));paired.put("unexpected_private","private-extra-field");paired.getJSONObject("learning_provenance").put("unexpected_private","private-learning-field");paired.getJSONObject("prospective_protocol").put("unexpected_private","private-protocol-field");
            trials.protocol(paired.getJSONObject("prospective_protocol"));String prospective=trials.create(paired);trials.label(prospective,0,"unsure",now+8);
            String partial=trials.create(RecommendationTrialStore.freeze(List.of(new FeedStore.Row(old.id,old,"private-reason")),1,1,1,"V2EX|TECHNOLOGY","base",now+9));
            String before=trials.read(prospective).toString(),protocol=trials.protocol().toString();
            TrialObservationSnapshot.create(test.getTargetContext().getDatabasePath(name),output);
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(output.getPath(),null,SQLiteDatabase.OPEN_READONLY)){
                check(db.getVersion()==1&&count(db,"sessions")==2&&count(db,"labels")==1,"production snapshot preserves complete and unfinished sessions with original labels");
                try(Cursor c=db.rawQuery("SELECT payload FROM sessions WHERE id=?",new String[]{prospective})){c.moveToFirst();JSONObject value=new JSONObject(c.getString(0));String raw=value.toString();
                    check(!raw.contains("不能导出")&&!raw.contains("https://")&&!raw.contains("private-")&&!raw.contains("snippet")&&!raw.contains("\"title\":")&&!raw.contains("\"url\":"),"metadata allowlist removes titles URLs snippets and unexpected nested private fields");
                    check(value.getJSONObject("prospective_protocol").getString("id").equals(baseline.getString("id"))&&value.getJSONObject("learning_provenance").getJSONArray("candidate_events").getJSONObject(0).getJSONArray("members").getJSONObject(0).getLong("first_seen_ms")==now+3&&!value.getBoolean("holdout_qualified"),"metadata copy retains prospective cutoff and first-observation provenance without qualification");
                    check(value.getJSONArray("items").getJSONObject(0).getJSONObject("candidate").length()==1&&value.getJSONArray("pool").getJSONObject(0).getJSONObject("candidate").getLong("observedAt")==now+3,"only candidate observation time survives in items and common pool");
                }
                try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name NOT IN ('android_metadata','sessions','labels')",null)){c.moveToFirst();check(c.getInt(0)==0,"export contains no account learning feedback or live preparation tables");}
            }
            check(trials.read(prospective).toString().equals(before)&&trials.protocol().toString().equals(protocol)&&trials.read(partial)!=null&&store.activeFeedback(now+10).length()==1,"read-only export preserves original trial preparation labels and learning feedback");
            boolean exists=false;try{TrialObservationSnapshot.create(test.getTargetContext().getDatabasePath(name),output);}catch(IOException expected){exists=true;}check(exists&&output.isFile(),"existing destination cannot be overwritten by snapshot construction");
            trials.getWritableDatabase().execSQL("INSERT INTO labels VALUES('unknown-session','unknown-event','want',?)",new Object[]{now+10});boolean orphan=false;try{TrialObservationSnapshot.create(test.getTargetContext().getDatabasePath(name),failed);}catch(IOException expected){orphan=true;}check(orphan&&!failed.exists(),"orphan-label failure deletes incomplete snapshot and preserves source");trials.getWritableDatabase().delete("labels","session=?",new String[]{"unknown-session"});
            trials.getWritableDatabase().execSQL("UPDATE labels SET choice='invalid' WHERE session=?",new Object[]{prospective});boolean bad=false;try{TrialObservationSnapshot.create(test.getTargetContext().getDatabasePath(name),failed);}catch(IOException expected){bad=true;}check(bad&&!failed.exists(),"invalid label rejects export and removes partial database");trials.getWritableDatabase().execSQL("UPDATE labels SET choice='unsure' WHERE session=?",new Object[]{prospective});
            check(output.setReadable(true,false),"explicit synthetic-only export copy available to developer compatibility check");report.append("synthetic_snapshot=").append(output.getName()).append('\n');
            trials.clear();boolean empty=false;try{TrialObservationSnapshot.create(test.getTargetContext().getDatabasePath(name),failed);}catch(IOException expected){empty=true;}check(empty&&!failed.exists(),"empty evaluation records fail without leaving a stale export");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.getTargetContext().deleteDatabase(name);test.getTargetContext().deleteDatabase(feed);SQLiteDatabase.deleteDatabase(failed);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
