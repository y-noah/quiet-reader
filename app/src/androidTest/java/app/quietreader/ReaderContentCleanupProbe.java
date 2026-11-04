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

/** Actual reader DOM, including a legacy cached search directory. No external requests. */
final class ReaderContentCleanupProbe {
    private final Instrumentation test;private MainActivity reader;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReaderContentCleanupProbe(Instrumentation test){this.test=test;}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}catch(Exception error){throw new IllegalStateException(error);}}
    private void ui(Runnable work){test.runOnMainSync(work);}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    void run(){Context context=test.getTargetContext();String database="test-content-cleanup-"+UUID.randomUUID()+".db";List<String> urls=new ArrayList<>();
        try{
            for(int round=0;round<2;round++)for(int mode=0;mode<6;mode++){
                Source source=mode>=4?Source.SSPAI:mode==3?Source.IFANR:Source.BAIDU;String url=mode>=4?"https://sspai.com/post/"+System.nanoTime():mode==3?"https://www.ifanr.com/"+System.nanoTime():"https://www.baidu.com/s?wd=cleanup"+System.nanoTime();urls.add(url);
                HotCandidate candidate=new HotCandidate(source,url,"阅读正文清理验证","","",false,1,null,System.currentTimeMillis());Item item=candidate.readerItem();Document body;
                if(mode==3)body=SourceParser.article(source,"<article class=c-article-content><p>保留的真实正文<a href='https://example.org/citation'>引用原文</a></p><div class=recommend-container><a href='https://example.org/forbidden'>不应出现的推荐</a></div></article>",url);
                else if(mode==4)body=SourceParser.article(source,"<div class=article-body><div class=post__body__extend__item><h2>真实文章标题</h2><div class=post__body__extend__item__content><p>保留的真实正文<a href='https://example.org/citation'>引用原文</a></p></div></div><div class=article__main__content><h2>少数派的近期动态</h2><ul><li><a href='https://example.org/forbidden'>不应出现的推荐</a></li></ul></div></div>",url);
                else if(mode==5){body=new Document();body.url=url;Section old=new Section("search-old","正文",false);old.blocks.add(new Block("text","保留的真实正文"));old.blocks.add(new Block("text","你可能错过的好文章"));old.blocks.add(new Block("text","不应出现的推荐"));body.sections.add(old);body.blocks.addAll(old.blocks);}
                else if(mode==1){body=new Document();body.url=url;Section intro=new Section("search-intro","介绍",false);intro.blocks.add(new Block("text","保留的真实正文"));body.sections.add(intro);Section links=new Section("search-old","搜索结果",false);String title="不应出现的推荐";links.blocks.add(new Block("text",title,List.of(),List.of(new InlineLink(0,title.length(),"https://example.org/forbidden"))));body.sections.add(links);body.blocks.addAll(intro.blocks);body.blocks.addAll(links.blocks);}
                else body=SourceParser.article(source,"<div id=content_left>"+(mode==0?"<div class=rel-baikan-content><p>保留的真实正文</p></div>":"")+"<div><h3><a href='https://example.org/forbidden'>不应出现的推荐</a></h3><div class=c-abstract>保留的真实正文</div></div></div>",url);
                body.title=item.title;ArticleVersion.capture(item,body);Document prepared=body;
                ui(()->{Repository repo=new Repository(context);repo.cacheArticle(prepared);if(prepared.sections.stream().anyMatch(s->s.id.equals("search-old"))){new ReadingHistory(context).save(url,prepared,Set.of(),0);repo.releaseArticleMemory();}repo.close();});
                reader=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
                boolean[] ready={false};long until=SystemClock.elapsedRealtime()+8000;while(SystemClock.elapsedRealtime()<until){ui(()->ready[0]=field("reading")!=null&&field("readerWeb")!=null);if(ready[0])break;Thread.sleep(30);}check(ready[0],"round "+round+" mode "+mode+" actual reader renders prepared content");if(!ready[0])throw new IllegalStateException("Body did not render");
                String[] dom={""};until=SystemClock.elapsedRealtime()+5000;do{CountDownLatch done=new CountDownLatch(1);ui(()->{WebView web=(WebView)field("readerWeb");web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.body&&document.body.innerHTML",value->{dom[0]=value;web.getSettings().setJavaScriptEnabled(false);done.countDown();});});done.await(2,TimeUnit.SECONDS);if(dom[0].contains("保留的真实正文"))break;Thread.sleep(30);}while(SystemClock.elapsedRealtime()<until);
                check(dom[0].contains("保留的真实正文"),"reader DOM keeps actual content");check(!dom[0].contains("不应出现的推荐")&&!dom[0].contains("example.org/forbidden"),"reader DOM has no recommended article or navigation link");
                if(mode==3||mode==4)check(dom[0].contains("example.org/citation"),"ordinary article citation remains usable");
                ui(()->check(field("dynamic")==null&&field("pendingArticle")==null,"prepared content never launches another source acquisition"));ui(reader::finish);test.waitForIdleSync();reader=null;
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error));}
        finally{ui(()->{if(reader!=null)reader.finish();Repository repo=new Repository(context);repo.releaseArticleMemory();repo.close();});android.content.SharedPreferences.Editor edit=context.getSharedPreferences("reading-history",0).edit();for(String url:urls)edit.remove(url);edit.commit();context.deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
