package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Public anonymous topic, production dynamic acquisition and transferred-source continuation. */
final class TiebaLiveRepliesProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("LIVE anonymous Tieba, no login actions or account export\n");
    private Activity owner;private DynamicReader dynamic;private TiebaReplies replies;
    TiebaLiveRepliesProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Set<String> ids(Document doc){Set<String> ids=new HashSet<>();for(Section section:doc.sections)if(ReaderHtml.discussion(section)&&section.sourcePostId.matches("[1-9][0-9]{0,19}"))ids.add(section.sourcePostId);return ids;}
    void run(){String database="test-ui-tieba-live-replies-"+UUID.randomUUID()+".db";
        try{
            String url="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=28368027";
            Item item=new Item(Source.TIEBA,"公开贴吧话题",url,"");
            owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            CountDownLatch acquired=new CountDownLatch(1);Document[] body={null};String[] error={""};long start=SystemClock.elapsedRealtime();
            ui(()->dynamic=new DynamicReader(owner,item,new Repository.Result<>(){
                public void success(Document doc){body[0]=doc;WebView source=dynamic.takeSource();check(source!=null,"successful virtual post transfers its actual loaded source");if(source!=null)replies=new TiebaReplies(owner,item,source);acquired.countDown();}
                public void failure(String why){error[0]=why;acquired.countDown();}
            },true));
            check(acquired.await(43,TimeUnit.SECONDS),"public acquisition terminates within explicit deadline");
            Document before=body[0];check(before!=null&&before.topic!=null&&before.virtualDiscussion&&!ids(before).isEmpty(),"public topic resolves to virtual discussion with stable reply identities");
            if(before==null||replies==null)throw new IllegalStateException("No public continuation owner: "+error[0]);
            report.append("ACQUIRED actual=").append(before.sourceUrl()).append(" title=").append(before.title).append(" replies=").append(ids(before).size()).append(" ms=").append(SystemClock.elapsedRealtime()-start).append('\n');
            ui(()->dynamic.close());Set<String> first=ids(before);boolean grew=false;
            for(int batch=1;batch<=3&&!Set.of("end","login","error","limit").contains(before.moreStatus);batch++){
                Document base=before;CountDownLatch done=new CountDownLatch(1);Document[] result={null};String[] why={""};long at=SystemClock.elapsedRealtime();
                ui(()->replies.more(base,new Repository.Result<>(){public void success(Document doc){result[0]=doc;done.countDown();}public void failure(String reason){why[0]=reason;done.countDown();}}));
                check(done.await(23,TimeUnit.SECONDS),"public continuation batch "+batch+" terminates");
                Document next=result[0];report.append("BATCH ").append(batch).append(" returned=").append(next!=null).append(" ms=").append(SystemClock.elapsedRealtime()-at).append(" error=").append(why[0]);if(next!=null)report.append(" replies=").append(ids(next).size()).append(" status=").append(next.moreStatus);report.append('\n');
                check(next!=null,"public continuation preserves a readable result");if(next==null)break;
                check(next.url.equals(base.url)&&next.sourceUrl().equals(base.sourceUrl())&&next.topic==base.topic&&next.sections.get(0)==base.sections.get(0),"public continuation preserves topic, actual post and main content");
                check(ids(next).containsAll(ids(base)),"public continuation retains previously read reply identities");grew|=ids(next).size()>ids(base).size();before=next;
            }
            if(before.moreStatus.equals("login")){check(!ReaderContinuation.automatic(Source.TIEBA,before),"actual guest login gate stops automatic continuation");check(before.notice.contains("需要来源登录")&&!before.notice.contains("仅展示当前页"),"actual source restriction explains login rather than showing a generic reading hint");report.append("LIMITATION public source requires login; authenticated reply growth remains unverified\n");}
            else check(grew&&ids(before).size()>first.size(),"actual public source adds stable replies beyond initial acquisition");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(replies!=null)replies.close();if(dynamic!=null)dynamic.close();if(owner!=null)owner.finish();});test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
