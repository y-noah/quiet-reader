package app.quietreader;

import android.app.*;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Actual SQLite migration, transaction and cleanup. Synthetic identities, no phone/accounts. */
final class ExposureHistoryProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    ExposureHistoryProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private long scalar(SQLiteDatabase db,String sql){try(Cursor c=db.rawQuery(sql,null)){return c.moveToFirst()?c.getLong(0):0;}}
    private HotCandidate candidate(int id,long now){return new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(980000000+id),"曝光存储受控话题"+id,"独立记录实际看见的内容，不代表兴趣反馈。","",false,id+1,null,now);}
    void run(){Context context=test.getTargetContext();String name="test-exposure-history-"+UUID.randomUUID()+".db";long now=System.currentTimeMillis(),snapshot=0;List<FeedStore.Row> rows=new ArrayList<>();HotCandidate a=candidate(1,now),b=candidate(2,now),c=candidate(3,now);rows.add(new FeedStore.Row("shown-event",a,"",List.of(a,b)));rows.add(new FeedStore.Row("unseen-event",c,""));
        try{
            try(FeedStore store=new FeedStore(context,name)){
                store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),List.of(a,b,c),now);
                store.feedback("preserve-feedback","MORE","sample",a.id,now,null,a.modelText());store.putRerank("preserve-model",.75f,now);
                snapshot=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"cold","all",rows);store.activateSnapshot(snapshot);
                SQLiteDatabase db=store.getWritableDatabase();db.execSQL("DROP TABLE feed_exposure");db.execSQL("DROP TABLE article_progress");db.setVersion(7);
            }
            try(FeedStore store=new FeedStore(context,name)){
                SQLiteDatabase db=store.getWritableDatabase();long profile=store.version("profile"),sources=store.version("sources");
                check(db.getVersion()==9&&scalar(db,"SELECT COUNT(*) FROM feed_exposure")==0,"synthetic schema7 upgrades to9 with empty exposure history");
                check(store.activeSnapshot()==snapshot&&store.readSnapshot(snapshot,20,0).size()==2&&store.activeFeedback(now).length()==1&&store.rerank("preserve-model",now)==.75f,"upgrade preserves active list, feedback and model cache");
                check(store.recordExposed(snapshot,List.of("unknown-event"),now)==0&&store.recordExposed(snapshot+100,List.of("shown-event"),now)==0,"unknown or nonexistent snapshot rows cannot manufacture exposure");
                check(store.recordExposed(snapshot,List.of("shown-event","shown-event"),now)==1,"actual captured event is recorded once per batch");
                Map<String,FeedRefreshOrder.Recent> history=store.recentExposure(rows,now);check(history.size()==1&&history.get("shown-event").shownAt==now&&history.get("shown-event").readAt==0,"only displayed event is seen; no click or read is invented");
                FeedStore.Row switched=new FeedStore.Row("different-group",b,"");check(store.recentExposure(List.of(switched),now).get("different-group").shownAt==now,"exact accepted member retains exposure when the representative/group changes");
                HotCandidate unrelated=candidate(4,now);check(store.recentExposure(List.of(new FeedStore.Row("unrelated",unrelated,"")),now).isEmpty(),"another source identity does not inherit guessed headline exposure");
                store.recordConsumed(b,now+10);history=store.recentExposure(rows,now+10);check(history.get("shown-event").readAt==now+10&&history.get("shown-event").shownAt==now,"qualified read is a separate stronger presentation fact");
                store.recordExposed(snapshot,List.of("shown-event"),now-100);check(store.recentExposure(rows,now+10).get("shown-event").shownAt==now,"late older callback cannot rewind or delete newer history");
                check(store.version("profile")==profile&&store.version("sources")==sources&&store.activeFeedback(now+10).length()==1&&store.rerank("preserve-model",now+10)==.75f,"display/read history never changes preference/source versions or invalidates model cache");
                long before=scalar(db,"SELECT COUNT(*) FROM feed_exposure");
                db.execSQL("CREATE TRIGGER reject_exposure BEFORE INSERT ON feed_exposure WHEN NEW.key='event:unseen-event' BEGIN SELECT RAISE(ABORT,'controlled storage failure'); END");boolean failed=false;
                try{store.recordExposed(snapshot,List.of("shown-event","unseen-event"),now+100);}catch(RuntimeException expected){failed=true;}finally{db.execSQL("DROP TRIGGER reject_exposure");}
                history=store.recentExposure(rows,now+100);check(failed&&scalar(db,"SELECT COUNT(*) FROM feed_exposure")==before&&history.get("shown-event").shownAt==now&&!history.containsKey("unseen-event"),"mid-batch storage failure rolls back earlier updates and inserts atomically");
                List<String> excessive=new ArrayList<>();for(int n=0;n<65;n++)excessive.add("event-"+n);boolean rejected=false;try{store.recordExposed(snapshot,excessive,now);}catch(IllegalArgumentException expected){rejected=true;}check(rejected&&scalar(db,"SELECT COUNT(*) FROM feed_exposure")==before,"oversized exposure batch fails before mutation");
                rejected=false;try{store.recordConsumed(a,Long.MAX_VALUE);}catch(IllegalArgumentException expected){rejected=true;}check(rejected&&scalar(db,"SELECT COUNT(*) FROM feed_exposure")==before,"invalid far-future timestamp cannot evict valid current history");
                db.execSQL("UPDATE feed_exposure SET read_at=?,touched_at=? WHERE key=?",new Object[]{now+10000,now+10000,"member:"+b.id});history=store.recentExposure(rows,now+100);check(history.get("shown-event").readAt==0&&history.get("shown-event").shownAt==now,"clock rollback removes future read time while retaining valid prior exposure");
                store.clearDerivedCache();check(store.recentExposure(rows,now+100).size()==1&&store.activeFeedback(now+100).length()==1,"clearing reusable caches preserves actual reading/display facts and preference feedback");
                check(store.recentExposure(rows,now+8L*24*60*60_000).isEmpty()&&scalar(db,"SELECT COUNT(*) FROM feed_exposure")==0,"seven-day retention is pruned on access without a background timer");
                db.beginTransaction();try{for(int n=0;n<10020;n++)db.execSQL("INSERT INTO feed_exposure(key,shown_at,touched_at) VALUES (?,?,?)",new Object[]{"synthetic-"+n,now,now});db.setTransactionSuccessful();}finally{db.endTransaction();}
                store.recordConsumed(a,now+1);check(scalar(db,"SELECT COUNT(*) FROM feed_exposure")==10000&&store.recentExposure(rows,now+1).get("shown-event").readAt==now+1,"write/access cleanup enforces 10000-key bound and preserves newest real member");
            }
            try(FeedStore reopened=new FeedStore(context,name)){check(reopened.getWritableDatabase().getVersion()==9&&reopened.recentExposure(rows,now+1).get("shown-event").readAt==now+1,"read/exposure facts persist across close and reopen");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{context.deleteDatabase(name);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
