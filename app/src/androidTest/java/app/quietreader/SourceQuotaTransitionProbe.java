package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.Models.*;

/** Stateful source-budget transitions through production SQLite selection. */
final class SourceQuotaTransitionProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    SourceQuotaTransitionProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String text){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(text).append('\n');}
    private long count(List<FeedStore.Row> rows){return rows.stream().filter(r->r.candidate.source==Source.V2EX).count();}
    void run(){String database="test-ui-source-transitions.db";long now=System.currentTimeMillis();
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.topicprobe")||!android.os.Build.MODEL.startsWith("sdk_gphone"))throw new IllegalStateException("Isolated emulator required");
            List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<300;n++){
                Source source=List.of(Source.V2EX,Source.BBC,Source.THEPAPER).get(n%3);HotCandidate c=new HotCandidate(source,"https://example.com/transition/"+n,"数量变更候选 "+n,"","",false,n+1,null,now);rows.add(new FeedStore.Row(c.id,c,"",List.of(c),1));
            }
            test.getTargetContext().deleteDatabase(database);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.finishInterestSetup(List.of(),Map.of(),now);
                for(int n=0;n<3;n++){HotCandidate c=rows.get(n*3).candidate;store.feedback("weak-"+n,"QUICK_EXIT","sample",c.id,now+n,null,c.modelText(),Source.V2EX);}
                long weak=count(store.selectRecommendations(rows,now+10));check(weak>6,"weak reading evidence allows more than explicit LESS");
                store.feedback("explicit-less","LESS","source","V2EX",now+11,null,"",Source.V2EX);
                check(count(store.selectRecommendations(rows,now+12))==6,"explicit LESS after weak negative evidence shrinks the saved quota to six");
                store.undo("explicit-less",now+13);
                check(count(store.selectRecommendations(rows,now+14))==weak,"undo explicit LESS restores the previous weak allowance rather than keeping a hidden hard cap");
                store.feedback("explicit-less-again","LESS","source","V2EX",now+15,null,"",Source.V2EX);
                check(count(store.selectRecommendations(rows,now+16))==6,"LESS after undo reinstates the quarter budget");
                for(int n=0;n<3;n++){store.feedback("repeat-"+n,"LESS","source","V2EX",now+20+n,null,"",Source.V2EX);check(count(store.selectRecommendations(rows,now+25+n))==6,"repeated LESS does not multiply the reduction "+n);}
                store.feedback("restore-more","MORE","source","V2EX",now+40,null,"",Source.V2EX);check(count(store.selectRecommendations(rows,now+41))>6,"MORE releases the prior negative quota");
                store.feedback("less-again","LESS","source","V2EX",now+42,null,"",Source.V2EX);check(count(store.selectRecommendations(rows,now+43))==6,"LESS after MORE starts a normal quarter budget");
            }
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(count(reopened.selectRecommendations(rows,now+44))==6,"negative quota survives SQLite close and reopen");}
            test.getTargetContext().deleteDatabase(database);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.finishInterestSetup(List.of(),Map.of(),now);store.feedback("early-less","LESS","source","V2EX",now+1,null,"",Source.V2EX);
                store.selectRecommendations(rows.stream().filter(r->r.candidate.source!=Source.V2EX).toList(),now+2);
                check(count(store.selectRecommendations(rows,now+3))==6,"temporarily absent source does not freeze its future budget to zero");
            }
            test.getTargetContext().deleteDatabase(database);
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.finishInterestSetup(List.of(),Map.of(),now);store.feedback("legacy-less","LESS","source","V2EX",now+1,null,"",Source.V2EX);
                store.getWritableDatabase().execSQL("INSERT OR REPLACE INTO meta VALUES ('interest_source_quota_V2EX',1)");
                check(count(store.selectRecommendations(rows,now+2))==6,"upgrade recovers the old absent-source zero without a saved basis");
                store.getWritableDatabase().delete("meta","key=?",new String[]{"interest_source_basis_V2EX"});
                check(count(store.selectRecommendations(rows.stream().filter(r->r.candidate.source==Source.V2EX).toList(),now+3))<=6,"upgrade never enlarges an existing positive saved cap after other sources disappear");
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(e));}
        finally{test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
