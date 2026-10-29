package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Real SQLite ordering/rollback with deliberately seeded display facts. */
final class RefreshSnapshotProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    RefreshSnapshotProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private List<String> ids(List<FeedStore.Row> rows){List<String> ids=new ArrayList<>();for(FeedStore.Row row:rows)ids.add(row.eventId);return ids;}
    void run(){String name="test-refresh-snapshot-"+UUID.randomUUID()+".db";long now=System.currentTimeMillis();
        try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
            List<HotCandidate> items=new ArrayList<>();List<FeedStore.Row> rows=new ArrayList<>();int[] bands={0,0,1,1,1,2,-1};
            for(int n=0;n<7;n++){HotCandidate item=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+(99900000+n),"独立中文排序受控话题"+n,"","",false,n+1,null,now);items.add(item);rows.add(new FeedStore.Row("e"+n,item,"",List.of(item),bands[n]));}
            store.upsertSnapshot(Source.THEPAPER,store.beginRefresh(Source.THEPAPER),items,now);
            long source=store.version("sources"),profile=store.profile(now).getLong("version");
            long original=store.commitRanking(source,profile,now,"personal","all",rows);store.activateSnapshot(original);
            store.recordExposed(original,List.of("e0","e2","e3"),now);store.recordConsumed(items.get(2),now);
            check(store.readSnapshot(original,20,0).get(5).interestBand==2,"typed quality band survives actual SQLite payload roundtrip");
            long refreshed=store.refreshSnapshot(original,"all",now+1);List<FeedStore.Row> changed=store.readSnapshot(refreshed,20,0);
            check(refreshed>original&&ids(changed).equals(List.of("e1","e0","e4","e3","e2","e5","e6")),"unseen precedes seen then read only within each classified interest band");
            check(store.activeSnapshot()==original,"preparing a refreshed snapshot never activates it or replaces reading");
            check(store.version("sources")==source&&store.version("profile")==profile&&store.activeFeedback(now+1).length()==0,"presentation cooldown does not learn preferences or invalidate source/model context");
            check(store.refreshSnapshot(refreshed,"all",now+2)==refreshed,"repeated refresh without changed facts is stable and creates no duplicate snapshot");
            check(store.refreshSnapshot(refreshed,"V2EX",now+2)==-1,"a different source-filter context cannot reuse old classification");
            long progressed=store.refreshSnapshot(refreshed,"all",now+3,(row,recent,at)->"e0".equals(row.eventId)||"e2".equals(row.eventId)?now+2:0);
            check(ids(store.readSnapshot(progressed,20,0)).equals(List.of("e0","e1","e2","e4","e3","e5","e6")),"verified later progress restores relevance only within the same interest band");
            long unproved=store.refreshSnapshot(progressed,"all",now+4,(row,recent,at)->now);
            check(ids(store.readSnapshot(unproved,20,0)).equals(ids(changed)),"proof predating or equal to last exposure cannot bypass cooldown");
            long unavailable=store.refreshSnapshot(unproved,"all",now+5,(row,recent,at)->{throw new IllegalStateException("controlled unavailable evidence");});
            check(unavailable==unproved,"optional evidence failure preserves ordinary refresh behavior");
            // Subsequent rollback expectations refer to the most recent equivalent presentation.
            refreshed=unproved;
            store.getWritableDatabase().execSQL("CREATE TRIGGER reject_refresh BEFORE INSERT ON snapshots BEGIN SELECT RAISE(ABORT,'controlled refresh failure'); END");boolean failed=false;
            try{store.refreshSnapshot(refreshed,"all",now+FeedRefreshOrder.READ_COOLDOWN_MS+1);}catch(RuntimeException expected){failed=true;}finally{store.getWritableDatabase().execSQL("DROP TRIGGER reject_refresh");}
            check(failed&&store.latestSnapshot()==refreshed&&store.activeSnapshot()==original,"failed refreshed-snapshot commit rolls back and preserves readable identities");
            long expired=store.refreshSnapshot(refreshed,"all",now+FeedRefreshOrder.READ_COOLDOWN_MS+1);
            check(ids(store.readSnapshot(expired,20,0)).equals(ids(rows)),"cooldown expiry restores saved relevance order instead of freezing prior novelty order");
            long newest=store.commitRanking(source,profile,now,"newest","all",rows);
            check(store.refreshSnapshot(newest,"all",now+1)==newest,"chronological mode retains its timestamp order");
            store.feedback("new-feedback","LESS","sample",items.get(0).id,now+2,null,items.get(0).modelText());
            check(store.refreshSnapshot(expired,"all",now+3)==-1,"profile changes reject the saved interest classification");
            long current=store.commitRanking(source,store.profile(now+3).getLong("version"),now+3,"personal","all",rows);store.enable(Source.THEPAPER,false);
            check(store.refreshSnapshot(current,"all",now+4)==-1,"disabled sources reject the old candidate pool before presentation");
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{test.getTargetContext().deleteDatabase(name);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
