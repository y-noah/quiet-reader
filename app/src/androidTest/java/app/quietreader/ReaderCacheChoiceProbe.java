package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.WebView;
import app.quietreader.discovery.HotCandidate;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Synthetic bodies, real history/repository/reader. No source acquisition is needed. */
final class ReaderCacheChoiceProbe {
    private final Instrumentation test;private MainActivity reader;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReaderCacheChoiceProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable task){test.runOnMainSync(task);}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}catch(Exception error){throw new IllegalStateException(error);}}
    private void check(boolean okay,String label){checks++;if(!okay)failures++;report.append(okay?"PASS ":"FAIL ").append(label).append('\n');}
    private Document body(Item item,long at,String text){Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.cachedAt=at;Section section=new Section("cache-choice-main","正文",false);section.blocks.add(new Block("text",text));doc.sections.add(section);doc.blocks.addAll(section.blocks);ArticleVersion.capture(item,doc);return doc;}
    void run(){Context context=test.getTargetContext();String database="test-ui-cache-choice-"+UUID.randomUUID()+".db";List<String> urls=new ArrayList<>();Repository[] repo={null};
        try{
            for(int mode=0;mode<4;mode++){
                final int scenario=mode;long now=System.currentTimeMillis();String url="https://www.thepaper.cn/newsDetail_forward_"+now+mode;urls.add(url);
                HotCandidate candidate=new HotCandidate(Source.THEPAPER,url,"缓存正文选择测试","","",false,1,null,now);Item item=candidate.readerItem();
                Document history=body(item,now-2000,"阅读历史正文"),memory=body(item,mode==2?now-2000:mode==3?now-3000:now-1000,"提前准备的新正文");
                if(mode==1)history.metadataHash="old";
                ui(()->{repo[0]=new Repository(context);repo[0].cacheArticle(history);new ReadingHistory(context).save(url,history,Set.of("saved-expanded"),0);repo[0].cacheArticle(memory);});
                reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                long end=SystemClock.elapsedRealtime()+8000;boolean[] ready={false};while(SystemClock.elapsedRealtime()<end){ui(()->ready[0]=field("reading")!=null&&field("readerWeb")!=null);if(ready[0])break;Thread.sleep(40);}
                check(ready[0],"scenario "+mode+" actual reader becomes ready");
                ui(()->{Document chosen=(Document)field("reading");check(scenario<2?chosen==memory:chosen!=null&&chosen.blocks.get(0).value.equals("阅读历史正文"),"scenario "+scenario+" chooses expected body before disk/network fallback");check(field("dynamic")==null&&field("pendingArticle")==null,"scenario "+scenario+" has no source reader acquisition");check(((Set<?>)field("expanded")).contains("saved-expanded"),"scenario "+scenario+" retains saved expanded state");});
                String expected=mode<2?"提前准备的新正文":"阅读历史正文";boolean displayed=false;long domEnd=SystemClock.elapsedRealtime()+8000;
                do{CountDownLatch loaded=new CountDownLatch(1);String[] text={""};ui(()->{WebView web=(WebView)field("readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.body&&document.body.innerText",value->{text[0]=value;web.getSettings().setJavaScriptEnabled(false);loaded.countDown();});});
                    if(loaded.await(3,TimeUnit.SECONDS)&&text[0].contains(expected)){displayed=true;break;}Thread.sleep(40);
                }while(SystemClock.elapsedRealtime()<domEnd);
                check(displayed,"scenario "+mode+" actual DOM displays chosen body");
                ui(()->{reader.finish();repo[0].close();});test.waitForIdleSync();reader=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(repo[0]!=null){repo[0].releaseArticleMemory();repo[0].close();}});test.waitForIdleSync();
            try{Field writer=ReadingLanguageRecorder.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(3,TimeUnit.SECONDS);}catch(Exception error){failures++;report.append("ERROR cleanup language writer: ").append(error).append('\n');}
            android.content.SharedPreferences.Editor edit=context.getSharedPreferences("reading-history",0).edit();for(String url:urls)edit.remove(url);edit.commit();context.deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
