package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real preloader with an indefinitely blocked browser source and cheap native bodies. */
final class WarmupPriorityProbe {
    private final Instrumentation test;
    WarmupPriorityProbe(Instrumentation test){this.test=test;}
    void run(){Activity owner=null;ArticlePreloader[] loader={null};StringBuilder report=new StringBuilder();int failures=0;
        try{
            owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database","test-ui-warm-priority-"+System.nanoTime()+".db").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long id=System.currentTimeMillis();Item browser=new Item(Source.ZHIHU,"慢速正文","https://www.zhihu.com/question/"+id,""),paper=new Item(Source.THEPAPER,"快速正文甲","https://www.thepaper.cn/newsDetail_forward_"+id,""),solidot=new Item(Source.SOLIDOT,"快速正文乙","https://www.solidot.org/story?sid="+id,""),future=new Item(Source.HUPU,"后续批次","https://bbs.hupu.com/"+id+".html","");
            List<Item> visible=List.of(browser,paper,solidot),started=new ArrayList<>(),prepared=new ArrayList<>();CountDownLatch blocked=new CountDownLatch(1);
            Activity activity=owner;
            test.runOnMainSync(()->{
                loader[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){
                    public void load(Item item,Repository.Result<Document> callback){started.add(item);if(item==browser){blocked.countDown();return;}Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","真实预读队列的受控正文，浏览器来源保持等待时其它正文仍应先完成。"));ArticleVersion.capture(item,doc);callback.success(doc);}
                    public void cancel(){}
                },4,()->{},true);
                loader[0].observePrepared((item,doc)->prepared.add(item));loader[0].offer(ArticleWarmupOrder.arrange(visible,List.of(future),source->source==Source.ZHIHU),4,true);
            });
            if(!blocked.await(10,TimeUnit.SECONDS))throw new IllegalStateException("blocked source not reached");
            boolean[] valid={false};test.runOnMainSync(()->valid[0]=started.equals(List.of(paper,solidot,browser))&&prepared.equals(List.of(paper,solidot))&&visible.get(0)==browser);
            if(!valid[0])throw new IllegalStateException("native bodies did not finish before the blocked browser, or future screen overtook the current screen");
            report.append("PASS both current native bodies prepared before the blocked browser\nPASS future screen did not overtake a current article\nPASS visible reading order remains unchanged\n");
        }catch(Throwable error){failures++;report.append(android.util.Log.getStackTraceString(error));}
        finally{Activity activity=owner;test.runOnMainSync(()->{if(loader[0]!=null)loader[0].close();if(activity!=null)activity.finish();});}
        Bundle result=new Bundle();result.putString("stream",report+"checks=3 failures="+failures+"\n");test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
