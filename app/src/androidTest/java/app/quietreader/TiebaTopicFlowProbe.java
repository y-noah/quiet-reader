package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.webkit.*;
import android.webkit.CookieManager;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static app.quietreader.Models.*;

/** Controlled HTTP and WebView responses through production acquisition/cache paths. */
final class TiebaTopicFlowProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder("Controlled fixtures; no real account or public-source claim\n");
    private static final String POST="https://tieba.baidu.com/p/990088801",OTHER="https://tieba.baidu.com/p/990088802";
    private final List<String> requests=new CopyOnWriteArrayList<>();
    private final CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
    private Activity owner;private Repository repo;private ArticlePreloader preloader;private DynamicReader dynamic;
    private volatile boolean redirectPost;
    TiebaTopicFlowProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    private static String root(int id){return "https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=99008880"+id;}
    private static Item item(int id){return new Item(Source.TIEBA,"原热搜身份"+id,root(id),"热榜",false,"候选原摘要",null);}
    private static String topic(){return "<html><head><meta charset=utf-8></head><body><div class=topic-wrapper><span class=recom-sort-selected>最热</span><ul class=thread-list><li><a class=title href='"+POST+"'>实际帖子标题</a><div class=content>话题摘要不能当正文</div></li><li><a class=title href='"+OTHER+"'>同话题另一帖</a></li></ul></div></body></html>";}
    private static String post(boolean next){return "<html><head><meta charset=utf-8></head><body><h1>实际帖子标题</h1><div id=j_p_postlist><div class=l_post><a class=d_name>原作者</a><div class=d_post_content id=post_content_"+(next?"3":"1")+">"+(next?"后续回复正文":"真实主帖文字".repeat(70))+"</div></div>"+(next?"":"<div class=l_post><a class=d_name>回复者</a><div class=d_post_content id=post_content_2>首屏回复</div></div>")+"</div>"+(next?"":"<a rel=next href='?pn=2'>下一页</a>")+"</body></html>";}
    private void transport(){URL.setURLStreamHandlerFactory(protocol->!protocol.equals("https")?null:new URLStreamHandler(){
        protected URLConnection openConnection(URL url)throws IOException{
            String target=url.toString();if(!target.startsWith("https://tieba.baidu.com/"))throw new IOException("Unexpected controlled transport host");
            return new HttpURLConnection(url){
                public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){}
                public int getResponseCode(){requests.add(target);if(target.equals(root(2))){blocked.countDown();try{release.await(12,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}return target.equals(root(3))||redirectPost&&target.equals(POST)?302:200;}
                public String getHeaderField(String key){return "Location".equals(key)?target.equals(root(3))?root(9):OTHER:null;}
                public InputStream getInputStream(){return new ByteArrayInputStream((TiebaTopic.isTopic(target)?topic():post(target.contains("pn=2"))).getBytes(StandardCharsets.UTF_8));}
            };
        }
    });}
    private Document nativeRead(Item item,boolean success)throws Exception{
        CountDownLatch done=new CountDownLatch(1);Document[] result={null};String[] error={null};
        ui(()->repo.articleForReading(item,new Repository.Result<>(){public void success(Document doc){result[0]=doc;done.countDown();}public void failure(String why){error[0]=why;done.countDown();}}));
        check(done.await(8,TimeUnit.SECONDS),"native read terminates: "+item.url);
        check(success?result[0]!=null:error[0]!=null&&result[0]==null,success?"native read returns resolved body":"wrong-identity redirect fails without body");return result[0];
    }
    private void await(String label,java.util.function.BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+10_000;while(true){boolean[] good={false};ui(()->good[0]=condition.getAsBoolean());if(good[0])return;if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException(label);Thread.sleep(40);}}
    void run(){String database="test-ui-tieba-topic-"+UUID.randomUUID()+".db";Map<String,?> prefs=test.getTargetContext().getSharedPreferences("source-session",0).getAll();
        try{
            transport();owner=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            ui(()->{((DiscoveryController)field(owner,"controller")).interacting(true);test.getTargetContext().getSharedPreferences("source-session",0).edit().putBoolean("TIEBA-web",false).apply();repo=new Repository(owner);repo.invalidateArticles();});
            Document doc=nativeRead(item(1),true);if(doc==null)throw new IllegalStateException("Missing native body");
            ui(()->{check(doc.url.equals(root(1))&&doc.sourceUrl().equals(POST),"topic recommendation and actual post remain independent");check(doc.title.equals("实际帖子标题")&&doc.sections.size()==2&&doc.related.isEmpty(),"default body contains actual post and replies, no topic preview list");check(ArticleVersion.matches(item(1),doc)&&doc.topic.choices.size()==2,"resolved body retains original metadata and alternate choices");check(DiscussionPages.next(Source.TIEBA,doc),"resolved topic exposes actual post continuation");check(requests.equals(List.of(root(1),POST)),"native acquisition fetches topic then exactly the selected post");new ReadingHistory(owner).save(root(1),doc,Collections.emptySet(),17);check(new ReadingHistory(owner).get(root(1)).has("document"),"history stores resolved body under original topic");});
            Field writer=ArticleDiskCache.class.getDeclaredField("writer");writer.setAccessible(true);((ExecutorService)writer.get(null)).submit(()->{}).get(5,TimeUnit.SECONDS);
            CountDownLatch disk=new CountDownLatch(1);Document[] restored={null};ui(()->{repo.releaseArticleMemory();repo.restoreArticle(item(1),value->{restored[0]=value;disk.countDown();});});
            check(disk.await(5,TimeUnit.SECONDS)&&restored[0]!=null,"actual disk cache restores resolved topic");ui(()->check(restored[0]!=null&&restored[0].sourceUrl().equals(POST)&&ArticleVersion.matches(item(1),restored[0]),"disk hit retains actual-post session and recommendation metadata"));
            CountDownLatch continued=new CountDownLatch(1);Document[] merged={null};DiscussionStream[] stream={null};ui(()->{stream[0]=new DiscussionStream(owner,item(1));stream[0].more(doc,new Repository.Result<>(){public void success(Document value){merged[0]=value;continued.countDown();}public void failure(String why){report.append("CONTINUATION ").append(why).append('\n');continued.countDown();}});});
            check(continued.await(6,TimeUnit.SECONDS)&&merged[0]!=null,"production continuation reads actual post page two");ui(()->{check(merged[0]!=null&&merged[0].url.equals(root(1))&&merged[0].sourceUrl().equals(POST)&&merged[0].sections.size()==3&&merged[0].discussionPage==2,"continuation preserves topic identity and main post");stream[0].close();String rootSession=ArticleDiskCache.session(root(1));CookieManager.getInstance().setCookie(POST,"qr_topic_account=changed; Path=/p/; Secure");check(rootSession.equals(ArticleDiskCache.session(root(1)))&&!ArticleSession.contentCurrent(doc),"post-only cookie change is detected even when topic cookies are unchanged");check(repo.cachedArticle(item(1))==null,"post session change rejects memory cache");check(!new ReadingHistory(owner).get(root(1)).has("document"),"post session change strips history body");check(!repo.importBrowserSnapshot(doc),"post session change rejects source snapshot import");CookieManager.getInstance().setCookie(POST,"qr_topic_account=; Max-Age=0; Path=/p/; Secure");});
            CountDownLatch promoted=new CountDownLatch(1);Document[] promotedBody={null};ui(()->{preloader=new ArticlePreloader(owner,()->{});preloader.offer(List.of(item(2)));});check(blocked.await(6,TimeUnit.SECONDS),"ordinary preloader starts the same topic acquisition");ui(()->check(preloader.promote(item(2),new Repository.Result<>(){public void success(Document value){promotedBody[0]=value;promoted.countDown();}public void failure(String why){report.append("PROMOTION ").append(why).append('\n');promoted.countDown();}}),"foreground request promotes the in-flight topic prefetch"));release.countDown();check(promoted.await(7,TimeUnit.SECONDS)&&promotedBody[0]!=null,"promoted prefetch delivers resolved body");ui(()->{check(promotedBody[0]!=null&&promotedBody[0].url.equals(root(2))&&promotedBody[0].sourceUrl().equals(POST),"promotion keeps dual identity");check(Collections.frequency(requests,root(2))==1,"promotion does not restart the topic request");preloader.close();});
            nativeRead(item(3),false);redirectPost=true;nativeRead(item(5),false);redirectPost=false;
            dynamicRead(item(4),false);dynamicRead(item(6),true);
            ui(()->{DynamicReader speculation=new DynamicReader(owner,item(8),new Repository.Result<>(){public void success(Document d){}public void failure(String why){}});try{long remaining=(long)field(speculation,"deadlineAt")-SystemClock.elapsedRealtime();check(remaining>20_000&&remaining<=22_000&&(int)field(speculation,"attemptLimit")==14,"topic speculation retains original 22-second/14-attempt budget");}finally{speculation.close();}});
            selectedNative();detachedNative();
            feedback(database,doc);images(doc);
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{release.countDown();ui(()->{if(dynamic!=null)dynamic.close();if(preloader!=null)preloader.close();if(repo!=null)repo.close();if(owner!=null)owner.finish();CookieManager.getInstance().setCookie(POST,"qr_topic_account=; Max-Age=0; Path=/p/; Secure");android.content.SharedPreferences.Editor edit=test.getTargetContext().getSharedPreferences("source-session",0).edit().clear();for(Map.Entry<String,?> e:prefs.entrySet())if(e.getValue() instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)e.getValue());edit.apply();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void detachedNative()throws Exception{
        PendingArticle[] pending={null};CountDownLatch delivered=new CountDownLatch(1);String[] failure={null};Document[] body={null};
        try{
            ui(()->{pending[0]=new PendingArticle(owner,item(7));pending[0].start();});await("detached native topic never completed",()->(boolean)field(pending[0],"completed"));
            ui(()->{check(pending[0].matches(item(7))&&field(pending[0],"result")!=null,"detached native result retains valid resolved body");CookieManager.getInstance().setCookie(POST,"qr_topic_account=changed; Path=/p/; Secure");check(!pending[0].matches(item(7)),"detached native ownership rejects changed actual-post account");pending[0].attach(new Repository.Result<>(){public void success(Document doc){body[0]=doc;delivered.countDown();}public void failure(String why){failure[0]=why;delivered.countDown();}});});
            check(delivered.await(2,TimeUnit.SECONDS)&&body[0]==null&&failure[0]!=null,"reattaching cannot deliver buffered body from old post session");
        }finally{ui(()->{if(pending[0]!=null)pending[0].close();CookieManager.getInstance().setCookie(POST,"qr_topic_account=; Max-Age=0; Path=/p/; Secure");});}
    }
    private void selectedNative()throws Exception{
        Document defaultPost=nativeRead(item(10),true);if(defaultPost==null)return;
        Item selected=TiebaTopic.select(item(10),defaultPost.topic,defaultPost.topic.choices.get(1));
        ui(()->check(repo.cachedArticle(selected)==null,"explicit alternate cannot consume default-post memory cache"));
        Document alternate=nativeRead(selected,true);if(alternate==null)return;
        ui(()->{check(alternate.url.equals(selected.url)&&alternate.sourceUrl().equals(OTHER),"native alternate reads official chosen post under original topic identity");check(ArticleVersion.matches(item(10),alternate)&&ArticleVersion.matches(selected,alternate),"alternate preserves original candidate version for qualified feedback");PendingArticle pending=new PendingArticle(owner,selected);try{check(!pending.matches(item(10)),"in-flight ownership distinguishes default and selected requests");}finally{pending.close();}});
        dynamicRead(selected,false);
        Item absent=new Item(Source.TIEBA,item(11).title,item(11).url,"",false,item(11).snippet,null,"","https://tieba.baidu.com/p/990088899");nativeRead(absent,false);
    }
    private void feedback(String database,Document doc)throws Exception{
        app.quietreader.discovery.HotCandidate candidate=new app.quietreader.discovery.HotCandidate(Source.TIEBA,doc.url,item(1).title,item(1).snippet,"",false,1,null,System.currentTimeMillis());
        Field f=ReadingInterestRecorder.class.getDeclaredField("writer");f.setAccessible(true);ExecutorService writer=(ExecutorService)f.get(null);
        CountDownLatch blocked=new CountDownLatch(1),release=new CountDownLatch(1);
        writer.submit(()->{blocked.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});check(blocked.await(2,TimeUnit.SECONDS),"reading feedback worker held before controlled account change");
        try{ui(()->{ReadingInterestRecorder.record(owner,database,candidate,doc);CookieManager.getInstance().setCookie(POST,"qr_topic_account=changed; Path=/p/; Secure");});}finally{release.countDown();}
        writer.submit(()->{}).get(4,TimeUnit.SECONDS);
        try(app.quietreader.discovery.FeedStore store=new app.quietreader.discovery.FeedStore(owner,database)){check(store.activeFeedback(System.currentTimeMillis()).length()==0,"queued reading cannot learn from old actual-post account");}
        ui(()->{CookieManager.getInstance().setCookie(POST,"qr_topic_account=; Max-Age=0; Path=/p/; Secure");ReadingInterestRecorder.record(owner,database,candidate,doc);ReadingInterestRecorder.quickExit(owner,database,candidate,doc);});writer.submit(()->{}).get(4,TimeUnit.SECONDS);
        try(app.quietreader.discovery.FeedStore store=new app.quietreader.discovery.FeedStore(owner,database)){
            org.json.JSONArray events=store.activeFeedback(System.currentTimeMillis());check(events.length()==2,"qualified reading and quick exit can both retain original-topic identity");
            check(events.toString().contains(candidate.id)&&!events.toString().contains("实际帖子标题"),"weak feedback attaches to original topic rather than borrowed post title");
        }
    }
    private void images(Document original)throws Exception{
        Document doc=AnswerStream.copy(original);String image="https://9.9.9.9/qr-topic-"+System.nanoTime()+".png";Block block=new Block("image",image);doc.sections.get(0).blocks.add(block);doc.blocks.add(block);
        AtomicInteger fetched=new AtomicInteger();String[] referer={null};CountDownLatch done=new CountDownLatch(1);
        ReaderImages images=new ReaderImages(url->CookieManager.getInstance().getCookie(url),(ReaderImages.Fetcher)(url,ref,cookies)->{fetched.incrementAndGet();referer[0]=ref;done.countDown();return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/l9sAAAAASUVORK5CYII=");},2*1024*1024);
        ImagePreloader[] loader={null};ReaderImageOwner[] bodyOwner={null};
        try{
            ui(()->{bodyOwner[0]=new ReaderImageOwner(owner,item(1),images,doc);check(bodyOwner[0].matches(item(1),doc),"body image owner binds actual-post provenance");loader[0]=new ImagePreloader(owner,()->true,images);loader[0].offer(List.of(item(1)));loader[0].prepared(item(1),doc);loader[0].start();});
            check(done.await(4,TimeUnit.SECONDS)&&POST.equals(referer[0]),"image prefetch uses actual post Referer");await("image prefetch did not finish",()->!loader[0].busy());
            ui(()->{loader[0].close();loader[0]=new ImagePreloader(owner,()->true,images);loader[0].offer(List.of(item(1)));loader[0].prepared(item(1),doc);CookieManager.getInstance().setCookie(POST,"qr_topic_account=changed; Path=/p/; Secure");loader[0].start();});
            await("stale image prefetch did not stop",()->!loader[0].busy());check(fetched.get()==1,"actual-post account change prevents prepared image request");
            ui(()->check(!bodyOwner[0].matches(item(1)),"body image owner invalidates when only actual-post account changes"));
            boolean rejected=false;try{bodyOwner[0].load(image,POST);}catch(IOException expected){rejected=true;}check(rejected&&fetched.get()==1,"stale body image scope cannot fetch or restore new-account images");
        }finally{ui(()->{if(bodyOwner[0]!=null)bodyOwner[0].close();if(loader[0]!=null)loader[0].close();CookieManager.getInstance().setCookie(POST,"qr_topic_account=; Max-Age=0; Path=/p/; Secure");});}
    }
    private void dynamicRead(Item item,boolean cancel)throws Exception{
        String expectedPost=item.selectedTopicPost.isEmpty()?POST:item.selectedTopicPost;
        CountDownLatch done=new CountDownLatch(1),postRequested=new CountDownLatch(1),postRelease=new CountDownLatch(cancel?1:0);AtomicInteger pages=new AtomicInteger();Document[] result={null};String[] failure={null};
        ui(()->{dynamic=new DynamicReader(owner,item,new Repository.Result<>(){public void success(Document value){result[0]=value;done.countDown();}public void failure(String why){failure[0]=why;done.countDown();}},true);long remaining=(long)field(dynamic,"deadlineAt")-SystemClock.elapsedRealtime();check(remaining>38_000&&remaining<=40_000,"explicit two-document topic acquisition has bounded 40-second deadline");WebView web=(WebView)field(dynamic,"web");WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String url=request.getUrl().toString();if(request.isForMainFrame()){pages.incrementAndGet();if(url.equals(expectedPost)){postRequested.countDown();try{postRelease.await(6,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}}return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream((TiebaTopic.isTopic(url)?topic():post(false)).getBytes(StandardCharsets.UTF_8)));}
            public void onPageCommitVisible(WebView view,String url){delegate.onPageCommitVisible(view,url);}
            public void onPageFinished(WebView view,String url){delegate.onPageFinished(view,url);}
            public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return delegate.shouldOverrideUrlLoading(view,request);}
        });});
        try{
            check(postRequested.await(8,TimeUnit.SECONDS),"dynamic reader automatically navigates from topic to selected post");
            if(cancel){ui(()->dynamic.close());postRelease.countDown();Thread.sleep(600);check(done.getCount()==1&&result[0]==null,"cancelled topic resolution cannot deliver late post content");}
            else{check(done.await(10,TimeUnit.SECONDS)&&result[0]!=null,"dynamic resolution completes: "+failure[0]);if(result[0]!=null)ui(()->{check(result[0].url.equals(item.url)&&result[0].sourceUrl().equals(expectedPost)&&result[0].title.equals("实际帖子标题"),"dynamic path retains actual title and original topic identity");check(ArticleVersion.matches(item,result[0])&&ArticleSession.contentCurrent(result[0]),"dynamic path stamps matched metadata and actual post session");check(pages.get()==2,"dynamic path loads exactly one topic and one post");});}
        }finally{postRelease.countDown();ui(()->dynamic.close());}
    }
}
