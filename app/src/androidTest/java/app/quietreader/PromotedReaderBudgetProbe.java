package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Real promotion of a staged loader; synthetic topic/post HTML, no source permission claim. */
final class PromotedReaderBudgetProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC TIEBA PROMOTION: actual preloader promotion, WebView/parser, delayed body\n");
    PromotedReaderBudgetProbe(Instrumentation test){this.test=test;}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private static Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    void run(){
        Activity home=null;ArticlePreloader[] preloader={null};DynamicReader[] dynamic={null};String database="test-ui-promoted-budget-"+System.nanoTime()+".db";
        try{
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Activity owner=home;
            ui(()->((DiscoveryController)field(owner,"controller")).interacting(true));
            String url="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=99007770",post="https://tieba.baidu.com/p/990077701";Item item=new Item(Source.TIEBA,"合成话题",url,"");
            CountDownLatch staged=new CountDownLatch(1),postStarted=new CountDownLatch(1),done=new CountDownLatch(1);AtomicInteger requests=new AtomicInteger(),callbacks=new AtomicInteger();Document[] body={null};String[] error={""};long[] originalDeadline={0};
            @SuppressWarnings("unchecked") Repository.Result<Document>[] pending=new Repository.Result[1];
            ui(()->{preloader[0]=new ArticlePreloader(owner,()->{},new ArticlePreloader.Loader(){public void load(Item chosen,Repository.Result<Document> cb){pending[0]=cb;staged.countDown();}public void cancel(){}});preloader[0].offer(List.of(item));});
            check(staged.await(5,TimeUnit.SECONDS),"test loader stages actual speculative preloader state");
            ui(()->{try{
                dynamic[0]=new DynamicReader(owner,item,pending[0]);Field f=ArticlePreloader.class.getDeclaredField("dynamic");f.setAccessible(true);f.set(preloader[0],dynamic[0]);WebView web=(WebView)field(dynamic[0],"web");WebViewClient delegate=web.getWebViewClient();
                originalDeadline[0]=(long)field(dynamic[0],"deadlineAt");check((int)field(dynamic[0],"attemptLimit")==14,"unopened speculative reader keeps its short budget");
                web.setWebViewClient(new WebViewClient(){
                    @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){String target=request.getUrl().toString(),html="";if(request.isForMainFrame()){requests.incrementAndGet();if(target.equals(url))html="<html><meta charset=utf-8><body><div class=topic-wrapper><span class=recom-sort-selected>最热</span><ul class=thread-list><li><a class=title href='"+post+"'>实际文字帖</a></li></ul></div></body></html>";else if(target.equals(post)){postStarted.countDown();html="<html><meta charset=utf-8><body><h1>实际文字帖</h1><script>setTimeout(()=>{document.body.insertAdjacentHTML('beforeend',\"<div id=j_p_postlist><div class=l_post><div class=d_post_content id=post_content_1>延迟取得的完整文字正文末尾</div></div></div>\")},26000)</script></body></html>";}}return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                    @Override public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                    @Override public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                    @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return delegate.shouldOverrideUrlLoading(view,request);}
                });
            }catch(Exception e){throw new IllegalStateException(e);}});
            check(postStarted.await(8,TimeUnit.SECONDS),"same speculative WebView resolves topic to its post");Thread.sleep(2000);
            ui(()->{
                WebView before=(WebView)field(dynamic[0],"web");
                Repository.Result<Document> receiver=new Repository.Result<>(){public void success(Document doc){body[0]=doc;callbacks.incrementAndGet();done.countDown();}public void failure(String reason){error[0]=reason;callbacks.incrementAndGet();done.countDown();}};
                check(preloader[0].promote(item,receiver),"explicit open promotes the in-flight preloader");
                check((WebView)field(dynamic[0],"web")==before,"promotion reuses the existing WebView");
                check((long)field(dynamic[0],"deadlineAt")==originalDeadline[0]+18000&&(int)field(dynamic[0],"attemptLimit")==28,"promotion uses the foreground budget from original acquisition start");
                long deadline=(long)field(dynamic[0],"deadlineAt");check(preloader[0].promote(item,receiver)&&(long)field(dynamic[0],"deadlineAt")==deadline,"rebinding the foreground callback cannot extend the deadline again");
            });
            check(done.await(37,TimeUnit.SECONDS),"promoted acquisition settles within its original bounded foreground budget");
            report.append("failureReason=").append(error[0]).append('\n');
            check(body[0]!=null&&body[0].hasContent()&&body[0].sections.stream().flatMap(s->s.blocks.stream()).anyMatch(b->b.value.contains("完整文字正文末尾")),"post body arriving after 26 seconds is delivered to the foreground");
            check(body[0]!=null&&body[0].url.equals(url)&&body[0].sourceUrl().equals(post)&&ArticleVersion.matches(item,body[0]),"promoted body keeps topic/post and metadata identities");
            check(requests.get()==2,"topic and post each load once with no promotion restart");
            check(callbacks.get()==1,"promotion delivers one terminal callback");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{Activity end=home;ui(()->{if(preloader[0]!=null)preloader[0].close();if(dynamic[0]!=null)dynamic[0].close();if(end!=null)end.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
