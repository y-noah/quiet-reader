package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.*;
import static app.quietreader.Models.*;

/** Real native acquisition then one controlled extraction WebView; no live accounts. */
final class NativeFallbackRoleProbe {
    private static final String WEIBO_SEARCH="https://s.weibo.com/weibo?q=qr-test-cookie";
    private static final String WEIBO_MOBILE="https://m.weibo.cn/search?containerid=qr-test-cookie";
    private static final String WEIBO_VISITOR="https://visitor.passport.weibo.cn/visitor/visitor";
    private final Instrumentation test;private Activity home;private PendingArticle pending;private DynamicReader dynamic;
    private int checks,failures;private final StringBuilder report=new StringBuilder("NATIVE FALLBACK ROLE; controlled transport, not live source certification\n");
    NativeFallbackRoleProbe(Instrumentation test){this.test=test;}
    private void ui(Runnable action){test.runOnMainSync(action);}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception error){throw new IllegalStateException(error);}}
    private String hydrated(String id)throws Exception{
        JSONObject detail=new JSONObject().put("contId",id).put("contType",0).put("name","受控动态正文").put("content","<p>浏览器取得的完整中文正文，应当替换原生壳页。</p>");
        JSONObject state=new JSONObject().put("props",new JSONObject().put("pageProps",new JSONObject().put("contId",id).put("detailData",new JSONObject().put("contentDetail",detail))));
        return "<html><meta charset=utf-8><body><script id=__NEXT_DATA__ type=application/json>"+state+"</script></body></html>";
    }
    private void setCookie(String url,String value)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        ui(()->android.webkit.CookieManager.getInstance().setCookie(url,value,accepted->{if(accepted)done.countDown();}));
        if(!done.await(5,TimeUnit.SECONDS))throw new IOException("Synthetic cookie was not accepted");
    }
    private Document fetch(Repository repository,Item item)throws Exception{
        CountDownLatch done=new CountDownLatch(1);
        Document[] received={null};
        ui(()->repository.articleForReading(item,new Repository.Result<Document>(){
            public void success(Document doc){received[0]=doc;done.countDown();}
            public void failure(String why){done.countDown();}
        }));
        if(!done.await(10,TimeUnit.SECONDS))throw new IOException("Synthetic read did not settle");
        return received[0];
    }
    private void weiboRedirect(Map<String,String> requestCookies,Set<String> attempted)throws Exception{
        setCookie(WEIBO_SEARCH,"SUB=desktop-fixture; Path=/weibo; Secure");
        setCookie(WEIBO_MOBILE,"SUB=mobile-fixture; Path=/search; Secure");
        Item item=new Item(Source.WEIBO,"受控微博热搜",WEIBO_SEARCH,"");
        Repository repository=new Repository(home);
        try{
            Document content=fetch(repository,item);
            check(content!=null&&content.hasContent()&&!content.loginRequired,"source-scoped mobile cookie unlocks the redirected body");
            check("SUB=desktop-fixture".equals(requestCookies.get(WEIBO_SEARCH))&&"SUB=mobile-fixture".equals(requestCookies.get(WEIBO_MOBILE)),"desktop and mobile requests send only their own cookies");
            check(content!=null&&WEIBO_SEARCH.equals(content.url)&&content.sections.stream().anyMatch(section->"https://m.weibo.cn/status/QrBeta123".equals(section.continuationUrl)),"relative post links use the final mobile URL while the body keeps the original cache identity");
            check(repository.cachedArticle(item)!=null,"body is reusable while both sessions match");
            setCookie(WEIBO_MOBILE,"SUB=mobile-changed; Path=/search; Secure");
            check(repository.cachedArticle(item)==null,"changing only the mobile session invalidates the desktop URL body");
            Document gate=fetch(repository,item);
            check(gate!=null&&gate.loginRequired&&!gate.hasContent(),"official visitor redirect returns the gate when the mobile session no longer matches");
            check(!attempted.contains(WEIBO_VISITOR),"native requests stop at the visitor redirect without sending session cookies");
            if(gate!=null)weiboGateRenewal(item,gate);
        }finally{ui(repository::close);}
    }
    private void weiboGateRenewal(Item item,Document gate)throws Exception{
        AtomicInteger terminal=new AtomicInteger();
        ui(()->{
            dynamic=new DynamicReader(home,item,new Repository.Result<Document>(){
                public void success(Document document){terminal.incrementAndGet();}
                public void failure(String why){terminal.incrementAndGet();}
            },true);
            WebView web=(WebView)field(dynamic,"web");
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
            });
            long before=(long)field(dynamic,"deadlineAt");
            dynamic.confirmWeiboGate(gate);
            check((long)field(dynamic,"deadlineAt")==before,"confirmed gate has a short check without shortening the original deadline");
        });
        setCookie(WEIBO_MOBILE,"SUB=mobile-renewed; Path=/search; Secure");
        ui(()->{
            ((Runnable)field(dynamic,"checkWeiboGate")).run();
            DynamicReader replacement=(DynamicReader)field(dynamic,"replacement");
            check(replacement!=null&&terminal.get()==0,"mobile session renewal retries before reporting a stale visitor gate");
            check(replacement!=null&&(long)field(replacement,"deadlineAt")==((long)field(dynamic,"deadlineAt")),"mobile session retry keeps the original acquisition deadline");
            dynamic.close();dynamic=null;
        });
    }
    void run(){String database="test-ui-native-fallback-"+UUID.randomUUID();AtomicInteger nativeRequests=new AtomicInteger();Set<String> allowed=ConcurrentHashMap.newKeySet();
        Map<String,String> requestCookies=new ConcurrentHashMap<>();Set<String> attempted=ConcurrentHashMap.newKeySet();
        allowed.addAll(List.of(WEIBO_SEARCH,WEIBO_MOBILE));
        try{
            URL.setURLStreamHandlerFactory(protocol->!"https".equals(protocol)?null:new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{String target=url.toString();attempted.add(target);if(!allowed.contains(target))throw new IOException("Unexpected fixture URL");return new HttpURLConnection(url){
                public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
                public int getResponseCode(){
                    nativeRequests.incrementAndGet();
                    if(target.equals(WEIBO_SEARCH)||target.equals(WEIBO_MOBILE))requestCookies.put(target,Objects.toString(getRequestProperty("Cookie"),""));
                    return target.equals(WEIBO_SEARCH)||target.equals(WEIBO_MOBILE)&&!"SUB=mobile-fixture".equals(requestCookies.get(target))?302:200;
                }
                public String getHeaderField(String name){return "Location".equalsIgnoreCase(name)?target.equals(WEIBO_SEARCH)?WEIBO_MOBILE:target.equals(WEIBO_MOBILE)?WEIBO_VISITOR:null:null;}
                public InputStream getInputStream(){
                    String html=target.equals(WEIBO_MOBILE)?"<div class=card-wrap><p class=txt>已读到受控微博话题的完整文字内容<a href='/status/QrBeta123'>全文</a></p></div>":"<html><body><div id=app></div></body></html>";
                    return new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8));
                }
            };}});
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(boolean readable:new boolean[]{true,false}){
                String id=Long.toString(System.currentTimeMillis()),url="https://www.thepaper.cn/newsDetail_forward_"+id;allowed.add(url);Item item=new Item(Source.THEPAPER,"受控原生壳页",url,"");
                String html=readable?hydrated(id):"<html><body><div id=app></div></body></html>";CountDownLatch done=new CountDownLatch(1);AtomicInteger nativeCallbacks=new AtomicInteger(),browserStarts=new AtomicInteger(),browserRequests=new AtomicInteger(),terminal=new AtomicInteger();Document[] result={null};String[] failure={""};int before=nativeRequests.get();
                ui(()->{pending=new PendingArticle(home,item);pending.attach(new Repository.Result<Document>(){
                    public void success(Document doc){nativeCallbacks.incrementAndGet();check(doc.sourceUnavailable&&!doc.hasContent(),"real native parser returns an unavailable shell");
                        if(SourceSession.nativeDocumentReady(item.source,doc)){result[0]=doc;terminal.incrementAndGet();done.countDown();return;}
                        browserStarts.incrementAndGet();dynamic=new DynamicReader(home,item,new Repository.Result<Document>(){public void success(Document body){result[0]=body;terminal.incrementAndGet();done.countDown();}public void failure(String why){failure[0]=why;terminal.incrementAndGet();done.countDown();}},true);
                        WebView web=(WebView)field(dynamic,"web");WebViewClient delegate=web.getWebViewClient();web.setWebViewClient(new WebViewClient(){
                            public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){if(request.isForMainFrame())browserRequests.incrementAndGet();return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));}
                            public void onPageCommitVisible(WebView view,String current){delegate.onPageCommitVisible(view,current);}
                            public void onPageFinished(WebView view,String current){delegate.onPageFinished(view,current);}
                        });
                    }
                    public void failure(String why){failure[0]=why;terminal.incrementAndGet();done.countDown();}
                });pending.start();});
                check(done.await(15,TimeUnit.SECONDS),"native shell fallback settles: readable="+readable);
                check(nativeRequests.get()==before+1&&nativeCallbacks.get()==1,"one native acquisition returns once");
                check(browserStarts.get()==1&&browserRequests.get()==1,"exactly one browser fallback navigation");
                check(terminal.get()==1,"exactly one terminal result");
                check(readable?result[0]!=null&&result[0].hasContent()&&!result[0].sourceUnavailable&&result[0].blocks.stream().anyMatch(b->b.value.contains("浏览器取得的完整中文正文")):result[0]!=null&&result[0].sourceUnavailable&&!result[0].hasContent(),readable?"hydrated body replaces native shell":"second unavailable shell remains unavailable without invented prose");
                Thread.sleep(700);check(nativeRequests.get()==before+1&&browserStarts.get()==1&&terminal.get()==1,"terminal browser result never loops back into native acquisition");
                ui(()->{if(pending!=null)pending.close();if(dynamic!=null)dynamic.close();pending=null;dynamic=null;});
                if(!failure[0].isEmpty())report.append("sourceFailure=").append(failure[0]).append('\n');
            }
            weiboRedirect(requestCookies,attempted);
        }catch(Throwable error){failures++;report.append("ERROR ").append(android.util.Log.getStackTraceString(error)).append('\n');}
        finally{ui(()->{if(pending!=null)pending.close();if(dynamic!=null)dynamic.close();if(home!=null)home.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("scope=PendingArticle, parser, native readiness decision and DynamicReader; not MainActivity navigation or real permissions\nchecks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
