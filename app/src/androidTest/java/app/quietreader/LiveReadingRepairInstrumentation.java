package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Real network observations through production repositories; blocked access is not a pass. */
public final class LiveReadingRepairInstrumentation extends Instrumentation {
    private final StringBuilder report=new StringBuilder();
    private MainActivity activity;private Repository repo;
    private static final class Await<T> implements Repository.Result<T>{
        final CountDownLatch latch=new CountDownLatch(1);T value;String error="";
        public void success(T v){value=v;latch.countDown();}public void failure(String e){error=e;latch.countDown();}
        T get()throws Exception{if(!latch.await(30,TimeUnit.SECONDS))throw new Exception("bounded callback timed out");return value;}
    }
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_OK;
        try{
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            runOnMainSync(()->repo=new Repository(activity));
            Await<List<Item>> board=new Await<>();runOnMainSync(()->repo.board(Source.DOUBAN,board));List<Item> topics=board.get();
            report.append("LIVE Douban board: ").append(topics==null?board.error:topics.size()+" supported topics").append('\n');
            if(topics!=null)for(Item item:topics){Await<Document> read=new Await<>();long start=SystemClock.elapsedRealtime();runOnMainSync(()->repo.article(item,read));Document doc=read.get();
                report.append("topic ").append(AdditionalSources.doubanTopicId(item.url)).append(" ms=").append(SystemClock.elapsedRealtime()-start).append(" state=").append(doc==null?read.error:doc.loginRequired?"LOGIN_REQUIRED":doc.sourceUnavailable?"UNAVAILABLE":"RETURNED").append(" entries=").append(doc==null?0:doc.related.size()).append('\n');
            }
            Await<List<Item>> zhihu=new Await<>();runOnMainSync(()->repo.board(Source.ZHIHU,zhihu));List<Item> questions=zhihu.get();
            if(questions!=null&&!questions.isEmpty()){
                Item item=questions.get(0);Await<Document> first=new Await<>();DynamicReader[] reader={null};AnswerStream[] stream={null};
                runOnMainSync(()->reader[0]=new DynamicReader(activity,item,new Repository.Result<Document>(){
                    public void success(Document doc){WebView source=reader[0].takeSource();if(source!=null)stream[0]=new AnswerStream(activity,item,source);first.success(doc);}
                    public void failure(String reason){first.failure(reason);}
                }));
                Document initial=first.get();report.append("LIVE Zhihu ").append(item.url).append(" initial=").append(initial==null?first.error:AnswerStream.answers(initial)+" answers").append('\n');
                if(initial!=null){Await<Document> more=new Await<>();runOnMainSync(()->{if(stream[0]==null)stream[0]=new AnswerStream(activity,item);stream[0].more(initial,more);});Document next=more.get();report.append("LIVE Zhihu continuation: ").append(next==null?more.error:next.moreStatus+" before="+AnswerStream.answers(initial)+" after="+AnswerStream.answers(next)+" notice="+next.notice).append('\n');}
                runOnMainSync(()->{reader[0].close();if(stream[0]!=null)stream[0].close();});
            }else report.append("LIVE Zhihu board unavailable: ").append(zhihu.error).append('\n');
        }catch(Throwable failure){status=Activity.RESULT_CANCELED;report.append("ERROR ").append(failure).append('\n');}
        finally{runOnMainSync(()->{if(repo!=null)repo.close();if(activity!=null)activity.finish();});try{File dir=new File(getTargetContext().getExternalFilesDir(null),"live-reading-repair");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"report.txt"))){out.write(report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}catch(Exception failure){status=Activity.RESULT_CANCELED;}result.putString("stream",report+"\nObservations only: login restrictions are not successful reads.\n");finish(status,result);}
    }
}
