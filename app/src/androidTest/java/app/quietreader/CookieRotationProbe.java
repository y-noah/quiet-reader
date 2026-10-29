package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static app.quietreader.Models.*;

/** Synthetic host-only cookies and intercepted HTML; never account login. */
final class CookieRotationProbe {
    private final Instrumentation test;private int checks,failures;
    private final StringBuilder report=new StringBuilder("SYNTHETIC COOKIE ROTATION; real DynamicReader/WebView/parser\n");
    CookieRotationProbe(Instrumentation test){this.test=test;}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void finish(DynamicReader reader,Document doc){try{Method m=DynamicReader.class.getDeclaredMethod("finish",Document.class);m.setAccessible(true);m.invoke(reader,doc);}catch(Exception e){throw new IllegalStateException(e);}}
    private void forbidden(WebView web,String url){
        WebResourceRequest request=new WebResourceRequest(){
            public android.net.Uri getUrl(){return android.net.Uri.parse(url);}
            public boolean isForMainFrame(){return true;}
            public boolean isRedirect(){return false;}
            public boolean hasGesture(){return false;}
            public String getMethod(){return "GET";}
            public java.util.Map<String,String> getRequestHeaders(){return java.util.Collections.emptyMap();}
        };
        web.getWebViewClient().onReceivedHttpError(web,request,new WebResourceResponse("text/html","UTF-8",403,"Forbidden",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0])));
    }
    void run(){
        Activity home=null;String database="test-ui-"+System.nanoTime()+".db";
        try{
            home=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            for(String scenario:new String[]{"fresh","rotates-again","cancel","http-fresh","http-rotates-again","http-cancel","http-unchanged"}){
                Activity owner=home;String url="https://qr-session-"+System.nanoTime()+".zhihu.com/question/123";
                DynamicReader[] original={null},replacement={null};WebView[] transferred={null};Document[] accepted={null};
                CountDownLatch done=new CountDownLatch(1);AtomicInteger callbacks=new AtomicInteger(),requests=new AtomicInteger();String[] error={""};
                try{
                    test.runOnMainSync(()->{
                        CookieManager cookies=CookieManager.getInstance();cookies.setCookie(url,"qr_fixture=before; Path=/; Secure");
                        original[0]=new DynamicReader(owner,new Item(Source.ZHIHU,"合成问题",url,""),new Repository.Result<>(){
                            public void success(Document doc){accepted[0]=doc;transferred[0]=original[0].takeSource();callbacks.incrementAndGet();done.countDown();}
                            public void failure(String why){error[0]=why;callbacks.incrementAndGet();done.countDown();}
                        });
                        if(!scenario.equals("http-unchanged"))cookies.setCookie(url,"qr_fixture=after; Path=/; Secure");
                        if(scenario.startsWith("http-"))forbidden((WebView)field(original[0],"web"),url);
                        else{Document stale=new Document();stale.url=url;stale.blocks.add(new Block("text","stale body must never be retagged"));finish(original[0],stale);}
                        replacement[0]=(DynamicReader)field(original[0],"replacement");
                        if(scenario.equals("http-unchanged")){check(replacement[0]==null&&callbacks.get()==1&&accepted[0]==null&&error[0].contains("限制了访问"),"unchanged-session HTTP restriction is reported without retry");return;}
                        check(replacement[0]!=null,"cookie change creates one fresh reader: "+scenario);
                        if(replacement[0]!=null)check(field(original[0],"deadlineAt").equals(field(replacement[0],"deadlineAt")),"session retry shares the original absolute deadline: "+scenario);
                        if(replacement[0]==null)return;
                        WebView web=(WebView)field(replacement[0],"web");WebViewClient delegate=web.getWebViewClient();
                        web.setWebViewClient(new WebViewClient(){
                            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                                if(request.isForMainFrame())requests.incrementAndGet();
                                String html="<html><head><meta charset='utf-8'></head><body><h1>合成问题</h1><div class=QuestionRichText>重新读取的新正文</div></body></html>";
                                return new WebResourceResponse("text/html","UTF-8",new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
                            }
                            @Override public void onPageCommitVisible(WebView view,String u){if(scenario.endsWith("rotates-again"))cookies.setCookie(url,"qr_fixture=third; Path=/; Secure");delegate.onPageCommitVisible(view,u);}
                            @Override public void onPageFinished(WebView view,String u){delegate.onPageFinished(view,u);}
                        });
                        if(scenario.endsWith("cancel"))original[0].close();
                    });
                    if(scenario.equals("http-unchanged"))continue;
                    if(replacement[0]==null)continue;
                    if(scenario.endsWith("cancel")){
                        Thread.sleep(500);check(callbacks.get()==0&&requests.get()==0,"closing original cancels replacement before navigation");
                    }else{
                        check(done.await(12,TimeUnit.SECONDS),"replacement settles: "+scenario);
                        check(callbacks.get()==1,"one terminal callback: "+scenario);
                        if(scenario.endsWith("fresh")){
                            check(accepted[0]!=null&&accepted[0].blocks.stream().anyMatch(b->b.value.equals("重新读取的新正文"))&&accepted[0].blocks.stream().noneMatch(b->b.value.contains("stale body")),"fresh DOM replaces old-session body");
                            check(accepted[0]!=null&&accepted[0].cachedSession.equals(ArticleDiskCache.session(url)),"fresh acquisition carries its actual session");
                            check(transferred[0]!=null,"continuation handoff delegates to replacement");
                        }else{
                            check(accepted[0]==null&&error[0].contains("状态已变化"),"second rotation terminates without loop or stale success");
                            check(field(replacement[0],"replacement")==null,"retry count bounded to one fresh reader");
                        }
                    }
                }finally{test.runOnMainSync(()->{if(original[0]!=null)original[0].close();if(transferred[0]!=null)SourceSurface.release(transferred[0]);CookieManager.getInstance().setCookie(url,"qr_fixture=; Max-Age=0; Path=/; Secure");});}
            }
        }catch(Throwable e){failures++;report.append("ERROR ").append(e).append('\n');}
        finally{if(home!=null){Activity end=home;test.runOnMainSync(end::finish);test.waitForIdleSync();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
