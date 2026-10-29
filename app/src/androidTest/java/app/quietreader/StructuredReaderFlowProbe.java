package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import app.quietreader.discovery.*;
import static app.quietreader.Models.*;

/** Synthetic source HTML exercises the actual cache, discovery and reader Activities. */
final class StructuredReaderFlowProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC HTML; source adapters -> cache -> discovery card -> own reader\n");
    StructuredReaderFlowProbe(Instrumentation test){this.test=test;}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+7000;while(true){boolean[] good={false};test.runOnMainSync(()->good[0]=condition.getAsBoolean());if(good[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Reader flow timeout");Thread.sleep(50);}}
    void run(){
        String database="test-ui-structured-"+System.nanoTime()+".db";DiscoveryActivity activity=null;Activity reader=null;ArticlePreloader[] loader={null};Repository[] repo={null};
        try{
            long now=System.currentTimeMillis();List<HotCandidate> candidates=List.of(new HotCandidate(Source.BAIDU,"https://www.baidu.com/s?wd=qr_fixture_"+now,"百度合成图文话题","","",false,1,null,now),new HotCandidate(Source.TIEBA,"https://tieba.baidu.com/p/"+now,"贴吧合成图文帖子","","",false,1,null,now));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){for(HotCandidate c:candidates){long r=store.beginRefresh(c.source);store.upsertSnapshot(c.source,r,List.of(c),now);}}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            DiscoveryActivity home=activity;await(()->(boolean)field(field(home,"controller"),"ready"));test.runOnMainSync(()->((DiscoveryController)field(home,"controller")).interacting(true));
            for(HotCandidate candidate:candidates){
                String html=candidate.source==Source.BAIDU?"<div id=content_left><div><h3><a href='https://www.cls.cn/detail/123'>合成新闻入口</a></h3><div class=c-abstract>合成摘要</div></div></div>":"<h1>合成帖子</h1><div id=j_p_postlist><div class=d_post_content>合成主帖正文</div><div class=d_post_content>合成回复</div></div>";
                Document doc=SourceParser.article(candidate.source,html,candidate.originalUrl);doc.title=candidate.title;ArticleVersion.capture(candidate.readerItem(),doc);
                CountDownLatch prefetched=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger();
                test.runOnMainSync(()->{
                    loader[0]=new ArticlePreloader(home,()->{},new ArticlePreloader.Loader(){public void load(Item item,Repository.Result<Document> cb){loads.incrementAndGet();cb.success(doc);}public void cancel(){}},20,()->{});
                    loader[0].observePrepared((item,body)->prefetched.countDown());loader[0].offer(List.of(candidate.readerItem()));
                });
                check(prefetched.await(5,TimeUnit.SECONDS)&&loads.get()==1,candidate.source+" enters proactive content loader");
                test.runOnMainSync(()->{loader[0].close();loader[0]=null;repo[0]=new Repository(home);check(repo[0].cachedArticle(candidate.readerItem())!=null,candidate.source+" prepared content is reusable from common cache");});
                Instrumentation.ActivityMonitor mainMonitor=test.addMonitor(MainActivity.class.getName(),null,false),loginMonitor=test.addMonitor(LoginActivity.class.getName(),null,false);
                try{
                    test.runOnMainSync(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(home,candidate);}catch(Exception e){throw new IllegalStateException(e);}});
                    reader=test.waitForMonitorWithTimeout(mainMonitor,5000);check(reader instanceof MainActivity,candidate.source+" discovery card opens own reading Activity");
                    if(reader==null)throw new IllegalStateException("Reader absent");Activity active=reader;
                    await(()->field(active,"readerWeb")!=null);
                    test.runOnMainSync(()->{check(((Document)field(active,"reading")).url.equals(candidate.originalUrl),candidate.source+" keeps original content/cache identity");check(((String)field(active,"readerHtml")).contains("data-reader-layout='"+(candidate.source==Source.TIEBA?"discussion":"article")+"'"),candidate.source+" uses normalized content layout");check(field(active,"dynamic")==null&&loginMonitor.getHits()==0,candidate.source+" cache hit needs neither source reload nor raw page Activity");});
                }finally{test.removeMonitor(mainMonitor);test.removeMonitor(loginMonitor);if(reader!=null){Activity end=reader;test.runOnMainSync(end::finish);test.waitForIdleSync();reader=null;}}
                test.runOnMainSync(()->{repo[0].close();repo[0]=null;((DiscoveryController)field(home,"controller")).interacting(true);});
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{Activity endHome=activity,endReader=reader;test.runOnMainSync(()->{if(loader[0]!=null)loader[0].close();if(repo[0]!=null)repo[0].close();if(endReader!=null)endReader.finish();if(endHome!=null)endHome.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
