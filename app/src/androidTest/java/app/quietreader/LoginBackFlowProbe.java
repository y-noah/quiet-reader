package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import android.view.*;
import android.widget.TextView;
import app.quietreader.discovery.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static app.quietreader.Models.*;

/** Delivered RESULT_CANCELED in the real reader; controlled pages/accounts only.
 * Does not simulate a real source login or the OS Back gesture. */
final class LoginBackFlowProbe {
    private final Instrumentation test;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    LoginBackFlowProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private void check(boolean b,String s){checks++;if(!b)failures++;report.append(b?"PASS ":"FAIL ").append(s).append('\n');}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new IllegalStateException(e);}}
    private void await(java.util.function.BooleanSupplier condition)throws Exception{
        long end=SystemClock.elapsedRealtime()+12000;
        while(SystemClock.elapsedRealtime()<end){boolean[] ready={false};ui(()->ready[0]=condition.getAsBoolean());if(ready[0])return;Thread.sleep(50);}
        throw new IllegalStateException("Controlled cancelled-login return timed out");
    }
    private void cookie(String url,String value)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        ui(()->CookieManager.getInstance().setCookie(url,"qr_cancel_account="+value+"; Path="+android.net.Uri.parse(url).getPath()+"; Secure; SameSite=Lax"+(value.isEmpty()?"; Max-Age=0":""),ok->done.countDown()));
        if(!done.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Cookie callback missing");
    }
    private static String text(View view){StringBuilder out=new StringBuilder();if(view instanceof TextView)out.append(((TextView)view).getText());if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)out.append(text(group.getChildAt(n)));}return out.toString();}
    private void cancelResult(MainActivity owner,Item item,AtomicInteger requests,boolean denied){
        try{
            MainActivity.class.getDeclaredMethod("onActivityResult",int.class,int.class,Intent.class).invoke(owner,100,Activity.RESULT_CANCELED,null);
            DynamicReader dynamic=(DynamicReader)field(owner,"dynamic");
            check(dynamic!=null,"cancelled result starts current-session source acquisition");
            if(dynamic==null)throw new IllegalStateException("No source acquisition after result");
            WebView web=(WebView)field(dynamic,"web");WebViewClient production=web.getWebViewClient();
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    String html="";
                    if(request.isForMainFrame()&&request.getUrl().toString().equals(item.url)){
                        requests.incrementAndGet();
                        if(denied)return new WebResourceResponse("text/html","UTF-8",403,"Forbidden",Collections.emptyMap(),new ByteArrayInputStream("<html><body>Controlled source restriction</body></html>".getBytes(StandardCharsets.UTF_8)));
                        html="<html><head><meta charset=utf-8></head><body><h1>返回后合成问题</h1><div class=QuestionRichText>返回后重新读取的合成补充</div><div class=AnswerItem data-zop='{&quot;itemId&quot;:123}'><div class=AuthorInfo-name>合成作者</div><div class=RichContent-inner><div class=RichText><p>返回后重新读取的合成回答。此页不是实际账号权限证据。</p></div></div></div></body></html>";
                    }
                    return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                }
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){production.onReceivedHttpError(view,request,response);}
                @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){production.onReceivedError(view,request,error);}
                @Override public void onPageCommitVisible(WebView view,String url){production.onPageCommitVisible(view,url);}
                @Override public void onPageFinished(WebView view,String url){production.onPageFinished(view,url);}
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return production.shouldOverrideUrlLoading(view,request);}
            });
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    void run(){
        String database="test-login-back-"+UUID.randomUUID()+".db";
        MainActivity reader=null;Repository repo=null;String activeUrl=null;
        boolean accepting=CookieManager.getInstance().acceptCookie();
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Isolated package required");
            ui(()->CookieManager.getInstance().setAcceptCookie(true));
            for(String scenario:List.of("same","changed","denied")){
                boolean changed=!scenario.equals("same"),denied=scenario.equals("denied");
                report.append("SCENARIO ").append(scenario).append('\n');
                String url="https://www.zhihu.com/question/"+System.currentTimeMillis();activeUrl=url;
                HotCandidate candidate=new HotCandidate(Source.ZHIHU,url,"返回后合成问题","原始摘要","",false,1,null,System.currentTimeMillis());Item item=candidate.readerItem();
                cookie(url,"before");
                try(FeedStore store=new FeedStore(test.getTargetContext(),database)){store.upsertSnapshot(Source.ZHIHU,store.beginRefresh(Source.ZHIHU),List.of(candidate),System.currentTimeMillis());}
                Document old=new Document();old.url=url;old.title=item.title;old.blocks.add(new Block("text","返回之前的合成旧缓存"));ArticleVersion.capture(item,old);
                Repository[] cache={null};ui(()->{cache[0]=new Repository(test.getTargetContext());cache[0].cacheArticle(old);});repo=cache[0];
                reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("direct_reader",true).putExtra("discovery_item",item.json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database));
                MainActivity owner=reader;await(()->field(owner,"reading")==old);
                String oldSession=old.cachedSession;
                if(changed)cookie(url,"after");
                ui(()->{check(changed!=oldSession.equals(ArticleDiskCache.session(url)),"test actually controls whether account scope changes");LoginActivity.pendingDocument=null;});
                AtomicInteger requests=new AtomicInteger();ui(()->cancelResult(owner,item,requests,denied));
                if(denied){
                    await(()->field(owner,"dynamic")==null&&text(owner.getWindow().getDecorView()).contains("需要来源页面协助"));
                    ui(()->{String shown=text(owner.getWindow().getDecorView());check(field(owner,"reading")==null&&((Repository)field(owner,"repo")).cachedArticle(item)==null,"actual source denial cannot resurrect an old-account cached body");check(!shown.contains("登录失败")&&!shown.contains("正在登录")&&shown.contains("登录 / 加载后读取"),"source restriction yields a usable recovery action without inventing login status");check(LoginActivity.pendingDocument==null&&requests.get()==1,"denial is bounded and does not manufacture an imported result");owner.finish();});test.waitForIdleSync();reader=null;repo.close();repo=null;cookie(url,"");activeUrl=null;continue;
                }
                await(()->field(owner,"reading")!=null&&field(owner,"reading")!=old);
                ui(()->{
                    Document fresh=(Document)field(owner,"reading");Item current=(Item)field(owner,"current");Repository shared=(Repository)field(owner,"repo");
                    check(requests.get()==1,"cancelled result reads source once instead of restoring old body");
                    check(current.url.equals(url)&&current.snippet.equals(item.snippet),"back preserves original topic and recommendation metadata");
                    check(fresh.sections.stream().anyMatch(s->s.blocks.stream().anyMatch(b->b.value.contains("合成回答"))),"fresh answer reaches actual reader after cancelled login result");
                    check(!fresh.loginRequired&&field(owner,"readerWeb")!=null,"successful extraction renders readable content");
                    check(fresh.cachedSession.equals(ArticleDiskCache.session(url))&&shared.cachedArticle(item)==fresh,"fresh body is cached only under current account scope");
                    check(LoginActivity.pendingDocument==null,"back never needs a manually imported pending document");
                });
                Document accepted=(Document)field(owner,"reading");Thread.sleep(500);
                ui(()->check(field(owner,"reading")==accepted&&requests.get()==1,"late callbacks do not duplicate source acquisition"));
                cookie(url,changed?"third":"after");
                ui(()->check(((Repository)field(owner,"repo")).cachedArticle(item)==null,"a later account change still rejects the newly read cached body"));
                ui(owner::finish);test.waitForIdleSync();reader=null;repo.close();repo=null;cookie(url,"");activeUrl=null;
            }
        }catch(Throwable e){checks++;failures++;report.append("FAIL ").append(android.util.Log.getStackTraceString(e));}
        finally{
            MainActivity last=reader;Repository remaining=repo;
            ui(()->{if(last!=null)last.finish();if(remaining!=null)remaining.close();});
            if(activeUrl!=null)try{cookie(activeUrl,"");}catch(Exception ignored){}
            ui(()->CookieManager.getInstance().setAcceptCookie(accepting));
            test.getTargetContext().deleteDatabase(database);
        }
        Bundle result=new Bundle();result.putString("stream",report.append("checks=").append(checks).append(" failures=").append(failures).append("\nscope=delivered cancelled Activity result with controlled DOM and real CookieManager; not real source account permissions or OS Back gesture\n").toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
