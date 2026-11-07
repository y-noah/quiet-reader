package app.quietreader;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.webkit.WebView;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.jsoup.Jsoup;
import static app.quietreader.Models.*;

/** Local synthetic sources, real Activity/WebView/cache paths, project emulator only. */
public final class TopicThreadInstrumentation extends Instrumentation {
    private static final String ROOT="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=999991070",A="https://tieba.baidu.com/p/999991071",B="https://tieba.baidu.com/p/999991072";
    private final Item item=new Item(Source.TIEBA,"热议话题标题 · 合成测试",ROOT,"");
    private final List<Item> choices=List.of(new Item(Source.TIEBA,"第一篇真实帖子",A,"最热"),new Item(Source.TIEBA,"第二篇真实帖子",B,"最热"));
    private final StringBuilder report=new StringBuilder();private MainActivity reader;private Repository repo;private int checks,failures;private boolean dark;
    private Bundle arguments;
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;dark="true".equals(args.getString("dark"));start();}
    private void check(boolean result,String name){checks++;if(!result)failures++;report.append(result?"PASS ":"FAIL ").append(name).append('\n');}
    private void ui(Runnable action){runOnMainSync(action);waitForIdleSync();}
    private Object field(String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(reader);}catch(Exception error){throw new IllegalStateException(error);}}
    private org.jsoup.nodes.Document html(){return Jsoup.parse(String.valueOf(field("readerHtml")));}
    private Document body(String post,String title,String comment){
        Document doc=new Document();doc.tiebaReplyRevision=TiebaReplyRelations.REVISION;doc.url=post;doc.title=title;doc.byline="真实帖子作者";
        Section main=new Section("body-"+post.substring(post.lastIndexOf('/')+1),"正文",false);main.blocks.add(new Block("text","本篇正文内容。".repeat(60)));
        Section reply=new Section("reply-"+post.substring(post.lastIndexOf('/')+1),"回复 · 评论作者",false);reply.blocks.add(new Block("text",comment));
        doc.sections.add(main);doc.sections.add(reply);doc.blocks.addAll(main.blocks);doc.blocks.addAll(reply.blocks);
        TiebaTopic.attach(ROOT,choices,doc,ArticleDiskCache.session(post),System.currentTimeMillis());ArticleVersion.capture(item,doc);doc.cachedAt=System.currentTimeMillis();doc.cachedSession=ArticleDiskCache.session(ROOT);doc.cachedNamespace=new ArticleDiskCache(getTargetContext()).namespace();return doc;
    }
    private void waitFor(java.util.function.BooleanSupplier condition,String name)throws Exception{
        long until=SystemClock.elapsedRealtime()+10000;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<until)Thread.sleep(50);
        if(!condition.getAsBoolean())throw new AssertionError(name);
    }
    private void click(String selector)throws Exception{
        long until=SystemClock.elapsedRealtime()+10000;
        do{
            WebView web=(WebView)field("readerWeb");CountDownLatch latch=new CountDownLatch(1);String[] response={""};
            String expected=String.valueOf(field("readerPageUrl")).split("#",2)[0];
            ui(()->web.evaluateJavascript("(()=>{if(document.readyState!=='complete'||location.href.split('#')[0]!=="+org.json.JSONObject.quote(expected)+")return false;const a=document.querySelector("+org.json.JSONObject.quote(selector)+");if(!a)return false;a.click();return true})()",result->{response[0]=result;latch.countDown();}));
            if(latch.await(5,TimeUnit.SECONDS)&&"true".equals(response[0]))return;
            Thread.sleep(100);
        }while(SystemClock.elapsedRealtime()<until);
        throw new AssertionError("Missing rendered action: "+selector);
    }
    private void shot(String name)throws Exception{
        if(name.equals("topic-comments")){
            WebView web=(WebView)field("readerWeb");CountDownLatch latch=new CountDownLatch(1);
            ui(()->web.evaluateJavascript("(()=>{const node=document.querySelector('.topic-comments');if(!node)return false;node.scrollIntoView();return true})()",value->latch.countDown()));
            if(!latch.await(5,TimeUnit.SECONDS))throw new IOException("Comment screenshot scroll timed out");
        }
        waitForIdleSync();Thread.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();if(bitmap==null)throw new IOException("Missing screenshot");
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),name+"-"+(dark?"dark":"light")+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
    }
    private void launch(Item target)throws Exception{
        reader=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).putExtra("discovery_item",target.json().toString()).putExtra("direct_reader",true).putExtra("test_database","test-ui-topic-107.db").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitFor(()->field("readerWeb")!=null&&!String.valueOf(field("readerHtml")).isEmpty(),"own reader did not appear");
    }
    private void windowPrefetch(Activity owner)throws Exception{
        String url="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=999992000";
        Item root=new Item(Source.TIEBA,"窗口预取测试",url,"");List<Item> posts=new ArrayList<>();for(int n=0;n<9;n++)posts.add(new Item(Source.TIEBA,"窗口正文 "+n,"https://tieba.baidu.com/p/"+(999992001+n),""));
        List<String> fetched=new ArrayList<>();CountDownLatch first=new CountDownLatch(1),second=new CountDownLatch(1),third=new CountDownLatch(1);ArticlePreloader[] queue={null};
        ui(()->{queue[0]=new ArticlePreloader(owner,()->{},new ArticlePreloader.Loader(){
            public void load(Item request,Repository.Result<Document> callback){String selected=request.selectedTopicPost.isEmpty()?posts.get(0).url:request.selectedTopicPost;fetched.add(selected);Document doc=body(A,"窗口正文","");doc.url=url;doc.topic=new TiebaTopic.Resolved(url,selected,ArticleDiskCache.session(selected),System.currentTimeMillis(),posts);doc.cachedSession=ArticleDiskCache.session(url);ArticleVersion.capture(root,doc);callback.success(doc);}
            public void cancel(){}
        },9,()->{}).topicWindow(3);queue[0].observePrepared((item,doc)->{if(TopicBundle.prepared(doc,3))first.countDown();if(TopicBundle.prepared(doc,6))second.countDown();if(TopicBundle.prepared(doc,9))third.countDown();});queue[0].offer(List.of(root),9,true);});
        check(first.await(10,TimeUnit.SECONDS),"initial body window completes before reading");
        check(fetched.size()==3&&new HashSet<>(fetched).equals(new HashSet<>(posts.subList(0,3).stream().map(post->post.url).toList())),"first window fetches exactly three bodies, never all nine");
        ui(()->{queue[0].topicWindow(6);queue[0].offer(List.of(root),9,true);});check(second.await(10,TimeUnit.SECONDS),"reading ahead prepares the next three bodies");
        check(fetched.size()==6,"first bodies are reused when preparing the next window");
        ui(()->{queue[0].topicWindow(9);queue[0].offer(List.of(root),9,true);});check(third.await(10,TimeUnit.SECONDS),"advancing again prepares the final body window");
        check(fetched.size()==9,"each body is acquired once across all three windows");ui(()->queue[0].close());
    }
    private String viewText(View view){StringBuilder text=new StringBuilder();if(view instanceof android.widget.TextView)text.append(((android.widget.TextView)view).getText());if(view instanceof android.view.ViewGroup)for(int n=0;n<((android.view.ViewGroup)view).getChildCount();n++)text.append(viewText(((android.view.ViewGroup)view).getChildAt(n)));return text.toString();}
    private View find(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof android.view.ViewGroup)for(int n=0;n<((android.view.ViewGroup)view).getChildCount();n++){View found=find(((android.view.ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}return null;}
    @Override public void onStart(){
        if("true".equals(arguments.getString("real_front"))){try{TopicFrontProbe.run(this,arguments);}catch(Exception error){Bundle result=new Bundle();result.putString("stream","ERROR "+error);finish(0,result);}return;}
        SharedPreferences appearance=getTargetContext().getSharedPreferences("appearance",0);boolean had=appearance.contains("mode");int previous=appearance.getInt("mode",0);
        try{
            appearance.edit().putInt("mode",dark?2:1).commit();TopicPostCacheProbe.run(this,this::check);ArticleDiskCacheProbe.run(this,this::check);
            ArticlePreloader[] wholeQueue={null};CountDownLatch wholeDone=new CountDownLatch(1);List<Boolean> stages=new ArrayList<>();
            ui(()->{Repository fresh=new Repository(getTargetContext());fresh.invalidateArticles();fresh.releaseArticleMemory();fresh.close();});
            getTargetContext().getSharedPreferences("reading-history",0).edit().remove(ROOT).commit();
            Activity warm=startActivitySync(new Intent(getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database","test-ui-topic-warm.db").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            java.util.concurrent.atomic.AtomicInteger scrollRequests=new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicReference<Repository.Result<Document>> scrollReply=new java.util.concurrent.atomic.AtomicReference<>();
            ArticlePreloader[] scrollingQueue={null};
            ui(()->{scrollingQueue[0]=new ArticlePreloader(warm,()->{},new ArticlePreloader.Loader(){public void load(Item request,Repository.Result<Document> callback){scrollRequests.incrementAndGet();scrollReply.set(callback);}public void cancel(){}},3,()->{}).wholeTopics();scrollingQueue[0].offer(List.of(item),3,true);});
            waitFor(()->scrollReply.get()!=null,"scroll prefetch fixture did not start");
            ui(()->{scrollingQueue[0].finishWindow();scrollReply.get().success(body(A,"滑动前已启动","当前请求允许完成"));});
            waitForIdleSync();
            ui(()->{check(scrollRequests.get()==1&&!scrollingQueue[0].busy(),"an in-flight topic completing during scroll does not enqueue more child loads");scrollingQueue[0].close();Repository fresh=new Repository(warm);fresh.invalidateArticles();fresh.releaseArticleMemory();fresh.close();});
            ui(()->{wholeQueue[0]=new ArticlePreloader(warm,()->{},new ArticlePreloader.Loader(){public void load(Item requested,Repository.Result<Document> callback){callback.success(body(requested.selectedTopicPost.isEmpty()?A:requested.selectedTopicPost,"预取正文","独立评论"));}public void cancel(){}},40,()->{}).wholeTopics();wholeQueue[0].observePrepared((request,doc)->{stages.add(TopicBundle.complete(doc));if(TopicBundle.complete(doc))wholeDone.countDown();});wholeQueue[0].offer(List.of(item),40,true);});
            check(wholeDone.await(10,TimeUnit.SECONDS),"list prefetch completes every body before the reader is opened");
            check(stages.size()>=2&&!stages.get(0)&&stages.get(stages.size()-1),"first-body acquisition never counts as whole-topic readiness");
            ui(()->wholeQueue[0].close());
            DiscoveryActivity discovery=(DiscoveryActivity)warm;
            app.quietreader.discovery.HotCandidate candidate=new app.quietreader.discovery.HotCandidate(Source.TIEBA,ROOT,item.title,"","",false,1,null,System.currentTimeMillis());
            app.quietreader.discovery.FeedStore.Row row=new app.quietreader.discovery.FeedStore.Row("publication-topic",candidate,"",List.of(candidate));
            java.lang.reflect.Field publishedField=DiscoveryActivity.class.getDeclaredField("feed");publishedField.setAccessible(true);
            java.lang.reflect.Field preparationField=DiscoveryActivity.class.getDeclaredField("topicPreparation");preparationField.setAccessible(true);
            TopicPreparation preparation=(TopicPreparation)preparationField.get(discovery);
            ui(()->check(!viewText(discovery.getWindow().getDecorView()).contains("尚无话题"),"bootstrap does not flash a false empty state"));
            ui(()->{discovery.state(true,false);discovery.feed(List.of(),0,0,false);});
            ui(()->check(!viewText(discovery.getWindow().getDecorView()).contains("尚无话题"),"an unsettled empty restore stays a transition rather than an empty result"));
            windowPrefetch(warm);
            ui(()->{
                discovery.feed(List.of(row),0,0,false);
                try{check(((List<?>)publishedField.get(discovery)).isEmpty(),"unfinished topic title is never published as a clickable loading card");}catch(Exception error){throw new IllegalStateException(error);}
                Repository local=new Repository(discovery);Document all=body(A,"预取正文","独立评论");TopicBundle.merge(all,body(B,"预取正文","独立评论"));local.cacheArticle(all);local.close();
            });
            waitFor(()->preparation.ready(item)!=null,"off-screen whole-topic preparation did not finish");
            ui(()->{
                try{check(((List<?>)publishedField.get(discovery)).size()==1,"whole-topic completion publishes its title without binding or opening a card first");}catch(Exception error){throw new IllegalStateException(error);}
                app.quietreader.discovery.HotCandidate incoming=new app.quietreader.discovery.HotCandidate(Source.TIEBA,ROOT,item.title+" 新版","","",false,1,null,System.currentTimeMillis());
                discovery.feed(List.of(new app.quietreader.discovery.FeedStore.Row("publication-topic-next",incoming,"",List.of(incoming))),0,0,false);
                try{List<?> shown=(List<?>)publishedField.get(discovery);check(shown.size()==1&&shown.get(0)==row,"refresh keeps the prior fully readable title while a replacement is incomplete");}catch(Exception error){throw new IllegalStateException(error);}
                warm.finish();
            });
            ui(()->{repo=new Repository(getTargetContext());repo.invalidateArticles();getTargetContext().getSharedPreferences("reading-history",0).edit().remove(ROOT).commit();repo.cacheArticle(body(B,choices.get(1).title,"只属于乙帖的评论"));repo.cacheArticle(body(A,choices.get(0).title,"只属于甲帖的评论"));});launch(item);
            ArticlePreloader[] topicQueue={null};CountDownLatch preparedPosts=new CountDownLatch(2);Set<String> preparedUrls=new HashSet<>();
            ui(()->{topicQueue[0]=new ArticlePreloader(reader,()->{});topicQueue[0].observePrepared((request,doc)->{if(preparedUrls.add(request.selectedTopicPost))preparedPosts.countDown();});Document root=body(A,choices.get(0).title,"只属于甲帖的评论");topicQueue[0].offer(List.of(TiebaTopic.select(item,root.topic,choices.get(0)),TiebaTopic.select(item,root.topic,choices.get(1))),40,true);});
            check(preparedPosts.await(5,TimeUnit.SECONDS),"prefetch queue prepares both posts sharing one topic URL");ui(()->topicQueue[0].close());
            check(html().selectFirst("h1").text().equals(item.title),"root title remains the hot topic");
            check(html().select("article.topic-answer").size()==2,"two source posts are separate answer cards");
            check(html().selectFirst(".topic-answer-title").text().equals(choices.get(0).title),"actual first-post title is below the topic");
            check(!html().text().contains("只属于甲帖的评论"),"comments stay folded initially");
            ui(()->{View share=find(reader.getWindow().getDecorView(),"分享原文链接");float density=reader.getResources().getDisplayMetrics().density;check(share!=null&&Math.abs(share.getLayoutParams().width/density-48)<1,"share touch target remains 48dp");check(share!=null&&Math.abs((share.getLayoutParams().width-share.getPaddingLeft()-share.getPaddingRight())/density-14)<1,"share content box is 14dp");});
            ui(()->{android.widget.ImageView share=(android.widget.ImageView)find(reader.getWindow().getDecorView(),"分享原文链接");android.graphics.RectF drawn=new android.graphics.RectF(0,0,share.getDrawable().getIntrinsicWidth(),share.getDrawable().getIntrinsicHeight());share.getImageMatrix().mapRect(drawn);float density=reader.getResources().getDisplayMetrics().density;check(share.getScaleType()==android.widget.ImageView.ScaleType.FIT_CENTER&&Math.abs(drawn.width()/density-14)<1,"actual drawable matrix scales share glyph to 14dp");});
            Thread.sleep(1800);check(field("tiebaReplies")==null,"folded comments do not start a reply loader");shot("topic-answer");
            click("#reading-topic-0 .topic-comments-toggle");waitFor(()->html().selectFirst(".topic-comments")!=null,"comments did not expand");
            check(html().selectFirst(".topic-comments").text().contains("只属于甲帖的评论"),"first-post comments appear within that post");
            check(html().selectFirst(".topic-comments").closest("article").attr("data-post-url").equals(A),"comments retain actual source post identity");
            shot("topic-comments");
            for(int turn=0;turn<4;turn++){
                boolean second=turn%2==0;String url=second?B:A,title=choices.get(second?1:0).title,comment=second?"只属于乙帖的评论":"只属于甲帖的评论";
                String card="#reading-topic-"+(second?1:0);String pageBefore=String.valueOf(field("readerPageUrl"));Object webBefore=field("readerWeb");
                if(html().selectFirst(card+" .topic-post-body")==null)click(card+" .topic-post-toggle");
                waitFor(()->html().selectFirst(card+" .topic-post-body")!=null,"cached answer did not expand");
                check(html().selectFirst("h1").text().equals(item.title),"switch keeps root title "+turn);
                check(html().selectFirst(card+" .topic-post-body").closest("article").attr("data-post-url").equals(url),"switch displays requested post "+turn);
                check(pageBefore.equals(String.valueOf(field("readerPageUrl")))&&webBefore==field("readerWeb"),"answer expansion is local without page reload "+turn);
                if(html().selectFirst(card+" .topic-comments")==null)click(card+" .topic-comments-toggle");waitFor(()->html().selectFirst(card+" .topic-comments")!=null,"new comments missing");
                check(html().selectFirst(card+" .topic-comments").text().contains(comment),"switch uses this post's comments "+turn);
                check(!html().selectFirst(card+" .topic-comments").text().contains(second?"只属于甲帖的评论":"只属于乙帖的评论"),"switch excludes other post's comments "+turn);
            }
            String beforeCollapsePage=String.valueOf(field("readerPageUrl"));Object beforeCollapseWeb=field("readerWeb");
            check(html().selectFirst("#reading-topic-0 .topic-post-toggle.section-collapse svg")!=null,"topic uses the answer-style floating collapse arrow");
            click("#reading-topic-0 .topic-post-toggle");waitFor(()->html().selectFirst("#reading-topic-0 .topic-post-body")==null,"post collapse did not apply");
            check(beforeCollapsePage.equals(String.valueOf(field("readerPageUrl")))&&beforeCollapseWeb==field("readerWeb"),"collapsing a post patches its card without reloading the WebView");
            check(html().selectFirst("#reading-topic-0 .topic-comments")==null&&html().selectFirst("#reading-topic-1 .topic-comments")!=null,"collapse hides only this answer and preserves the other answer");
            click("#reading-topic-0 .topic-post-toggle");waitFor(()->html().selectFirst("#reading-topic-0 .topic-post-body")!=null,"post reopen did not apply");check(html().select("article.topic-answer").size()==2,"reopen keeps all post cards");
            List<Item> batchPosts=new ArrayList<>();for(int n=0;n<9;n++)batchPosts.add(new Item(Source.TIEBA,"预取批次正文 "+n,"https://tieba.baidu.com/p/"+(999991080+n),""));
            Document batch=null;for(Item post:batchPosts){Document part=body(A,post.title,"属于 "+post.title+" 的评论");part.topic=new TiebaTopic.Resolved(ROOT,post.url,ArticleDiskCache.session(post.url),System.currentTimeMillis(),batchPosts);if(batch==null)batch=part;else TopicBundle.merge(batch,part);}
            Document cachedBatch=batch;
            ui(()->reader.finish());
            ui(()->{repo.cacheArticle(cachedBatch);getTargetContext().getSharedPreferences("reading-history",0).edit().remove(ROOT).commit();try{Field f=MainActivity.class.getDeclaredField("marks");f.setAccessible(true);((android.util.LruCache<?,?>)f.get(null)).evictAll();}catch(Exception error){throw new IllegalStateException(error);}});
            launch(item);waitFor(()->html().select("article.topic-answer").size()==3,"initial answer batch was not rendered");
            check(html().select("article.topic-answer").size()==3,"first view renders three answers rather than waiting for the entire directory");
            check(!html().text().contains("正文尚在准备")&&html().selectFirst(".topic-batch-more")!=null,"initial batch has real bodies and a single continuation entry");
            waitFor(()->((Integer)field("topicMediaReady"))>=6,"next answer batch was not prepared ahead");
            check(html().select("article.topic-answer").size()==3,"prefetch does not force extra answers into the current view");
            Object batchWeb=field("readerWeb");String firstBefore=html().getElementById("reading-topic-0").outerHtml();String batchPage=String.valueOf(field("readerPageUrl"));
            click("#topic-batch-more");waitFor(()->html().select("article.topic-answer").size()==6,"cached next batch did not append");
            check(batchWeb==field("readerWeb")&&batchPage.equals(String.valueOf(field("readerPageUrl"))),"loading more appends locally without reloading the reader");
            check(firstBefore.equals(html().getElementById("reading-topic-0").outerHtml()),"appending preserves the current answer and its expansion state");
            waitFor(()->((Integer)field("topicMediaReady"))>=9,"the following batch was not prefetched after expansion");
            ui(()->((WebView)field("readerWeb")).evaluateJavascript("scrollTo(0,document.body.scrollHeight)",null));
            waitFor(()->html().select("article.topic-answer").size()==9,"approaching the end did not append the prepared batch");
            check(html().selectFirst(".topic-batch-more")==null,"the final batch removes the continuation entry");
            check(firstBefore.equals(html().getElementById("reading-topic-0").outerHtml()),"automatic continuation preserves earlier answer DOM");shot("topic-batches");
            Item direct=new Item(Source.TIEBA,"普通帖子","https://tieba.baidu.com/p/999991079","");Document plain=new Document();plain.url=direct.url;plain.title="普通帖子真实标题";plain.blocks.add(new Block("text","普通帖正文"));ArticleVersion.capture(direct,plain);
            ui(()->{reader.finish();repo.cacheArticle(plain);});launch(direct);check(html().select(".topic-answer").isEmpty(),"a direct post remains a normal reader");check(html().selectFirst("h1").text().equals(plain.title),"direct-post title is not relabeled as a topic");
            Item weibo=new Item(Source.WEIBO,"微博热议主题","https://s.weibo.com/weibo?q=%23test%23","");
            Document posts=SourceParser.article(Source.WEIBO,"<h1>来源页标题</h1><div class=card-wrap><div class=info><a class=name>作者甲</a></div><p class=txt>第一条微博自己的内容</p></div><div class=card-wrap><div class=info><a class=name>作者乙</a></div><p class=txt>第二条微博自己的内容</p></div>",weibo.url);
            ArticleVersion.capture(weibo,posts);ui(()->{reader.finish();repo.cacheArticle(posts);});launch(weibo);
            check(html().selectFirst("h1").text().equals(weibo.title),"Weibo retains topic title");
            check(html().select(".topic-answer").size()==2,"Weibo displays two separate post cards");
            check(html().select(".topic-answer").get(0).text().contains("第一条微博")&&!html().select(".topic-answer").get(0).text().contains("第二条微博"),"Weibo first card excludes other post");
            check(html().select(".topic-answer").get(1).text().contains("第二条微博"),"Weibo second card retains its own content");
            check(html().select(".topic-comments").isEmpty(),"Weibo does not invent unavailable comments");shot("weibo-topic");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage()).append('\n');}
        finally{ui(()->{if(reader!=null)reader.finish();if(repo!=null)repo.close();});SharedPreferences.Editor edit=appearance.edit();if(had)edit.putInt("mode",previous);else edit.remove("mode");edit.commit();}
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append('\n').toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
