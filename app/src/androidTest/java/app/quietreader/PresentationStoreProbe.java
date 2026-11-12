package app.quietreader;

import android.app.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Isolated real SQLite migration, delivery bounds and stale-context checks. */
final class PresentationStoreProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    PresentationStoreProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private long count(SQLiteDatabase db,String table){try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM "+table,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private List<FeedStore.Row> rows(int start,int count,long now){List<FeedStore.Row> result=new ArrayList<>();for(int n=start;n<start+count;n++){HotCandidate c=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+n,"迁移验证中文话题"+n,"","",false,n+1,null,now);result.add(new FeedStore.Row(c.id,c,""));}return result;}
    void run(){String database="test-ui-presentation-store-"+UUID.randomUUID()+".db";
        try{
            long now=System.currentTimeMillis(),id,profile;List<FeedStore.Row> pool=rows(0,60,now);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.ensureCandidatePolicy();for(Source source:Source.values())store.enable(source,source==Source.THEPAPER);List<HotCandidate> candidates=new ArrayList<>();for(FeedStore.Row row:pool)candidates.add(row.candidate);
                store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),candidates,now);profile=store.profile(now).getLong("version");id=store.commitRanking(store.version("sources"),profile,now,"cold","all",pool);store.activateSnapshot(id);
                store.recordPresentation(pool.subList(0,15),now);check(store.profile(now).getLong("version")==profile&&store.profile(now).getJSONArray("feedback").length()==0,"whole-batch delivery creates no interest feedback or profile change");
                check(store.recentExposure(pool,now).isEmpty(),"delivered-but-unseen cards are not falsely counted as viewport exposure");
                long next=store.presentationSnapshot(id,"all",Collections.emptyList(),now+1);check(store.readSnapshot(next,15,0).get(0).eventId.equals(pool.get(15).eventId),"unclassified pool rotates on persisted delivery without model confidence");store.activateSnapshot(next);store.recordPresentation(store.readSnapshot(next,15,0),now+1);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                long next=store.presentationSnapshot(store.activeSnapshot(),"all",Collections.emptyList(),now+2);check(store.readSnapshot(next,15,0).get(0).eventId.equals(pool.get(30).eventId),"process-level store reopen remembers two whole delivered batches");
                long source=store.version("sources");store.enable(Source.THEPAPER,false);check(store.version("sources")!=source&&store.presentationSnapshot(next,"all",Collections.emptyList(),now+3)<0,"disabled-source context invalidates the saved next-batch snapshot");store.enable(Source.THEPAPER,true);
                store.recordPresentation(rows(1000,2600,now),now);check(count(store.getWritableDatabase(),"presentations")==2500,"delivery history stays bounded at 2500 identities");store.recordPresentation(List.of(pool.get(0)),now+8L*86400000);check(count(store.getWritableDatabase(),"presentations")==1,"old delivery history expires rather than growing indefinitely");
                SQLiteDatabase raw=store.getWritableDatabase();raw.execSQL("DROP TABLE presentations");raw.execSQL("DROP TABLE recommendation_window");raw.setVersion(9);
            }
            try(FeedStore upgraded=new FeedStore(test.getTargetContext(),database)){
                check(upgraded.getWritableDatabase().getVersion()==12&&count(upgraded.getWritableDatabase(),"presentations")==0&&count(upgraded.getWritableDatabase(),"recommendation_window")==0,"v9 to v12 adds delivery storage without destructive recreation");
                check(upgraded.candidates(200,0).size()==60&&upgraded.profile(now).getLong("version")==profile,"migration preserves source candidates and profile version");check(upgraded.activeSnapshot()>0&&upgraded.readSnapshot(upgraded.activeSnapshot(),15,0).size()==15,"migration preserves the previous visible snapshot");
                // The earlier disabled-source case deliberately invalidated the
                // old snapshot; seed a current v11 ranking before testing resume.
                upgraded.activateSnapshot(upgraded.commitRanking(upgraded.version("sources"),upgraded.profile(now).getLong("version"),now,"cold","all",pool));
                upgraded.recordPresentation(pool.subList(0,15),now);
                SQLiteDatabase raw=upgraded.getWritableDatabase();raw.execSQL("DROP TABLE recommendation_window");raw.setVersion(11);
            }
            try(FeedStore upgraded=new FeedStore(test.getTargetContext(),database)){
                check(upgraded.getWritableDatabase().getVersion()==12&&count(upgraded.getWritableDatabase(),"recommendation_window")==0,"v11 installed-version upgrade creates the recommendation window");
                check(count(upgraded.getWritableDatabase(),"presentations")==15&&upgraded.candidates(200,0).size()==60&&upgraded.profile(now).getLong("version")==profile,"v11 upgrade preserves delivery history, candidates and preference version");
                long next=upgraded.presentationSnapshot(upgraded.activeSnapshot(),"all",Collections.emptyList(),now+4);
                check(next>0&&upgraded.readSnapshot(next,15,0).get(0).eventId.equals(pool.get(15).eventId),"v11 upgrade resumes after previously delivered content rather than replaying it");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
