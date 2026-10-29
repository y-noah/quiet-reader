package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Synthetic source HTML, real preloader/repository/session/cache; no live-source claim. */
final class LegacyVideoRevalidationProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC HTML; legacy marker -> bounded discovery revalidation, no source requests\n");
    LegacyVideoRevalidationProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private boolean await(java.util.function.BooleanSupplier good)throws Exception{long end=SystemClock.elapsedRealtime()+4000;while(SystemClock.elapsedRealtime()<end){boolean[] result={false};ui(()->result[0]=good.getAsBoolean());if(result[0])return true;Thread.sleep(50);}return false;}
    void run(){
        DiscoveryActivity activity=null;Repository[] repo={null};ArticlePreloader[] preloader={null};String database="test-ui-legacy-video-"+UUID.randomUUID()+".db";
        Item item=new Item(Source.HUPU,"合成兼容主题","https://bbs.hupu.com/"+System.currentTimeMillis()+".html","");
        AtomicInteger loads=new AtomicInteger();Repository.Result<Document>[] pending=new Repository.Result[1];
        try{
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));DiscoveryActivity owner=activity;
            ArticlePreloader.Loader loader=new ArticlePreloader.Loader(){public void load(Item target,Repository.Result<Document> callback){loads.incrementAndGet();pending[0]=callback;}public void cancel(){pending[0]=null;}};
            CountDownLatch idle=new CountDownLatch(1);
            Document oldText=new Document();oldText.url=item.url;oldText.blocks.add(new Block("text","早于视频标记的旧图文缓存"));
            ui(()->{repo[0]=new Repository(owner);repo[0].rememberVideo(item);repo[0].cacheArticle(oldText);oldText.cachedAt=test.getTargetContext().getSharedPreferences("video-filter",0).getLong(VideoPolicy.key(item),0)-1;repo[0].cacheArticle(oldText);preloader[0]=new ArticlePreloader(owner,()->{},loader,3,idle::countDown);preloader[0].offer(List.of(item));});
            check(idle.await(3,TimeUnit.SECONDS)&&loads.get()==0,"source-board preloader preserves its existing hidden-video exclusion");
            ui(()->{preloader[0].close();preloader[0]=new ArticlePreloader(owner,()->{},loader,20,()->{},true);preloader[0].offer(List.of(item));});
            check(await(()->loads.get()==1),"discovery rechecks legacy marker instead of reusing older text memory cache");
            ui(()->check(!preloader[0].reserve(item),"hidden acquisition is not handed to a reader that cannot promote it"));
            Document text=SourceParser.article(Source.HUPU,"<h1>更新为图文的主题</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail><p>来源目前实际提供的中文正文。</p></div></div>",item.url);
            ui(()->{Repository.Result<Document> cancelled=pending[0];preloader[0].pause();cancelled.success(text);check(repo[0].hiddenVideo(item)&&repo[0].cachedArticle(item)==oldText,"stopping a hidden acquisition ignores its late success without changing marker or cache");preloader[0].offer(List.of(item));});
            check(await(()->loads.get()==2),"an explicitly cancelled acquisition may be offered again");
            ui(()->pending[0].success(text));
            check(await(()->!preloader[0].busy()),"fresh text acquisition completes without an extra request");
            ui(()->{check(text.hasContent()&&!repo[0].hiddenVideo(item),"later validated text retires the old URL video marker");check(repo[0].cachedArticle(item)==text,"revalidated body remains available in the common cache");repo[0].rememberVideo(item);});
            long marked=test.getTargetContext().getSharedPreferences("video-filter",0).getLong(VideoPolicy.key(item),0);
            Document replies=SourceParser.article(Source.HUPU,"<div class=reply-list-wrapper><div class=thread-content-detail>只有回复内容，不能改变主帖类型。</div></div>",item.url);
            ui(()->{repo[0].cacheArticle(replies);repo[0].reconcileVideo(item,replies);check(repo[0].hiddenVideo(item),"reply-only snapshot cannot reclassify the Hupu main post");Document later=AnswerStream.copy(text);later.url=item.url.replace(".html","-2.html");new ArticleSession(owner,later.url).stamp(later);repo[0].reconcileVideo(new Item(item.source,item.title,later.url,""),later);check(repo[0].hiddenVideo(item),"later thread page cannot erase the canonical main-post marker");});
            ui(()->{text.cachedAt=marked-1;repo[0].reconcileVideo(item,text);check(repo[0].hiddenVideo(item),"older cached text cannot erase a newer video observation");text.cachedAt=System.currentTimeMillis();String namespace=text.cachedNamespace;text.cachedNamespace="obsolete";repo[0].reconcileVideo(item,text);check(repo[0].hiddenVideo(item),"invalidated namespace cannot erase a marker");text.cachedNamespace=namespace;String session=text.cachedSession;text.cachedSession="obsolete";repo[0].reconcileVideo(item,text);check(repo[0].hiddenVideo(item),"old cookie session cannot erase a marker");text.cachedSession=session;text.loginRequired=true;repo[0].reconcileVideo(item,text);check(repo[0].hiddenVideo(item),"login-gated prose cannot erase a marker");text.loginRequired=false;});
            ui(()->{preloader[0].close();repo[0].rememberVideo(item);preloader[0]=new ArticlePreloader(owner,()->{},loader,20,()->{},true);preloader[0].offer(List.of(item));});
            check(await(()->loads.get()==3),"legacy marker can be rechecked through the bounded loader");
            ui(()->pending[0].failure("controlled unavailable page"));check(await(()->!preloader[0].busy()),"failed revalidation terminates without clearing the marker");
            ui(()->{check(repo[0].hiddenVideo(item),"failed source retains the earlier video marker");preloader[0].offer(List.of(item));});
            check(await(()->!preloader[0].busy())&&loads.get()==3,"reoffering after failure respects the five-minute attempt throttle");
            ui(()->{preloader[0].offer(List.of(new Item(item.source,item.title,item.url,"",true)));});
            check(await(()->!preloader[0].busy())&&loads.get()==3,"source-confirmed video candidates are never revalidated speculatively");
            ui(()->{preloader[0].close();preloader[0]=new ArticlePreloader(owner,()->{},loader,20,()->{},true);preloader[0].offer(List.of(item));});
            check(await(()->loads.get()==4),"a new revalidation can observe a source-confirmed video result");
            ui(()->{Document video=new Document();video.url=item.url;video.filteredVideo=true;pending[0].success(video);});
            check(await(()->!preloader[0].busy()),"video-only completion settles without caching a prose body");
            ui(()->preloader[0].offer(List.of(item)));
            check(await(()->!preloader[0].busy())&&loads.get()==4,"video-only success retains the same five-minute network cooldown");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{DiscoveryActivity end=activity;ui(()->{if(preloader[0]!=null)preloader[0].close();if(repo[0]!=null){repo[0].releaseArticleMemory();repo[0].close();}if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().getSharedPreferences("video-filter",0).edit().remove(VideoPolicy.key(item)).commit();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
