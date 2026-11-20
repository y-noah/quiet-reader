package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Persistent soft-source feedback and changing heat in real SQLite. */
final class SourceQuotaTransitionProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    SourceQuotaTransitionProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String text){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(text).append('\n');}
    private long count(List<FeedStore.Row> rows){return rows.stream().filter(r->r.candidate.source==Source.V2EX).count();}
    private List<FeedStore.Row> rows(long now){List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<300;n++){
        Source source=List.of(Source.V2EX,Source.BBC,Source.THEPAPER).get(n%3);HotCandidate c=new HotCandidate(source,"https://example.com/transition/"+n,"候选话题 "+n,"","",false,n+10,null,now);rows.add(new FeedStore.Row(c.id,c,"",List.of(c),1));
    }return rows;}
    void run(){String database="test-ui-source-transitions.db";long now=System.currentTimeMillis();
        try{
            if(!test.getTargetContext().getPackageName().matches("app\\.quietreader\\.(topicprobe|discoveryprobe)")||!android.os.Build.HARDWARE.equals("ranchu"))throw new IllegalStateException("Isolated emulator required");
            List<FeedStore.Row> rows=rows(now);test.getTargetContext().deleteDatabase(database);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.finishInterestSetup(List.of(),Map.of(),now);long neutral=count(store.selectRecommendations(rows,now));
                store.feedback("less","LESS","source","V2EX",now+1,null,"",Source.V2EX);
                long reduced=count(store.selectRecommendations(rows,now+2));check(reduced>0&&reduced<neutral,"LESS lowers membership without suppressing the source");
                for(int n=0;n<3;n++){store.feedback("again-"+n,"LESS","source","V2EX",now+3+n,null,"",Source.V2EX);check(count(store.selectRecommendations(rows,now+7+n))==reduced,"repeated LESS does not compound into zero "+n);}
                store.getWritableDatabase().execSQL("INSERT OR REPLACE INTO meta VALUES ('interest_source_quota_V2EX',1)");
                check(count(store.selectRecommendations(rows,now+11))==reduced,"legacy zero-quota metadata cannot veto selection");
                store.feedback("more","MORE","source","V2EX",now+12,null,"",Source.V2EX);check(count(store.selectRecommendations(rows,now+13))>neutral,"MORE still increases the share");
                store.undo("more",now+14);check(count(store.selectRecommendations(rows,now+15))==reduced,"undo restores LESS without a frozen cap");
                HotCandidate old=rows.get(297).candidate;
                check(store.selectRecommendations(rows,now+16).stream().noneMatch(r->r.candidate.id.equals(old.id)),"cold tail was previously rejected");
                HotCandidate hot=new HotCandidate(old.source,old.originalUrl,old.title,old.snippet,old.rawHeat,false,1,old.publishedAt,now+17);
                rows.set(297,new FeedStore.Row(hot.id,hot,"",List.of(hot),1));
                check(store.selectRecommendations(rows,now+18).stream().anyMatch(r->r.candidate.id.equals(hot.id)),"newly hot unseen topic escapes an earlier rejection");
                rows.set(297,rows.get(297).withInterestBand(2));check(store.selectRecommendations(rows,now+19).stream().noneMatch(r->r.candidate.id.equals(hot.id)),"topic dislike stays excluded despite heat");
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){check(count(store.selectRecommendations(rows,now+20))>0,"source remains available after reopen");}
            test.getTargetContext().deleteDatabase(database);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.finishInterestSetup(List.of(),Map.of(),now);store.feedback("early","LESS","source","V2EX",now+1,null,"",Source.V2EX);
                store.selectRecommendations(rows.stream().filter(r->r.candidate.source!=Source.V2EX).toList(),now+2);
                check(count(store.selectRecommendations(rows,now+3))>0,"temporary absence cannot freeze a future share to zero");
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(e));}
        finally{test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
