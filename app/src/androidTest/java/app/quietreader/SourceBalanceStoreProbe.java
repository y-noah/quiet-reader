package app.quietreader;
import android.app.*;
import android.os.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;
/** Isolated SQLite source migration and opt-in eligibility; never writes user data. */
final class SourceBalanceStoreProbe {
    private final Instrumentation test;private int checks,failures;private final StringBuilder report=new StringBuilder();
    SourceBalanceStoreProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){String name="test-source-balance-"+UUID.randomUUID()+".db";
        try{
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                store.enable(Source.BILIBILI,true);store.enable(Source.DOUYIN,false);store.enable(Source.WEIBO,false);
                store.getWritableDatabase().delete("sources","id=?",new String[]{"NYT"});store.getWritableDatabase().setVersion(10);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){
                check(store.getWritableDatabase().getVersion()==12&&store.sourceState(Source.NYT).getBoolean("enabled"),"schema10 upgrades with new Chinese source");
                check(!store.sourceState(Source.WEIBO).getBoolean("enabled")&&!store.sourceState(Source.DOUYIN).getBoolean("enabled")&&store.sourceState(Source.BILIBILI).getBoolean("enabled"),"upgrade preserves all previous source choices");
                long now=System.currentTimeMillis();HotCandidate video=new HotCandidate(Source.BILIBILI,"https://www.bilibili.com/video/BV1234567890","中文视频主题","","",true,1,null,now);
                store.ensureCandidatePolicy();long revision=store.beginRefresh(Source.BILIBILI);store.upsertSnapshot(Source.BILIBILI,revision,List.of(video),now);
                check(store.currentCandidate(video)&&CandidateOrder.arrange(store.candidates(5000,0),new JSONArray(),false).size()==1,"enabled video source passes candidate and snapshot eligibility");
                store.enable(Source.BILIBILI,false);check(!store.currentCandidate(video)&&store.candidates(5000,0).isEmpty(),"disabling video source removes it immediately");
                FeedStore.Row first=new FeedStore.Row(video.id,video,"");long initial=store.balanceTurn();
                store.recordPresentation(List.of(first),now,true);check(store.balanceTurn()==initial+1,"actual balanced presentation advances allocation turn");
                store.recordPresentation(List.of(first),now+1,true);check(store.balanceTurn()==initial+1,"body refresh or repeated publication cannot spend another allocation turn");
                HotCandidate another=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/123","另一条中文主题","","",false,1,null,now);
                store.recordPresentation(List.of(new FeedStore.Row(another.id,another,"")),now+2,false);check(store.balanceTurn()==initial+1,"single-source and newest views do not skew mixed allocation");
                store.enable(Source.NYT,false);
            }
            try(FeedStore store=new FeedStore(test.getTargetContext(),name)){check(!store.sourceState(Source.NYT).getBoolean("enabled"),"new source opt-out persists on reopen");check(store.balanceTurn()==1,"allocation turn persists after process/store reopen");}
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{test.getTargetContext().deleteDatabase(name);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle out=new Bundle();out.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
}
