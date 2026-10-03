package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Synthetic network, real WebView XHR -> production parser -> merge -> callback. */
public final class AnswerPagingInstrumentation extends Instrumentation {
    private static final String URL="https://www.zhihu.com/question/9000000000000000001";
    private static final String API="https://www.zhihu.com/api/v4/questions/9000000000000000001/answers";
    private Activity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    interface Checked {void run()throws Exception;}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private void check(boolean ok,String label){checks++;if(!ok)failures++;report.append(ok?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private void ui(Checked action)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{action.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private static String html(){return "<!doctype html><html><head><meta charset='utf-8'></head><body><div class='AnswerItem' id='1'><div class='RichContent-inner'><div class='RichText'><p>已读回答</p></div></div></div></body></html>";}
    private static Document previous(){return SourceParser.article(Source.ZHIHU,html(),URL);}
    private static String json(int id,String next,boolean end){return "{\"data\":[{\"id\":"+id+",\"content\":\"<p>API回答"+id+"</p>\"}],\"paging\":{\"is_end\":"+end+",\"next\":"+org.json.JSONObject.quote(next)+"}}";}

    private final class Trial {
        final String mode;
        AnswerStream stream;
        volatile int requests,callbacks,foreignRequests;
        final List<String> urls=Collections.synchronizedList(new ArrayList<>());
        final CountDownLatch entered=new CountDownLatch(1);
        final BlockingQueue<Document> results=new LinkedBlockingQueue<>();
        long started;
        Trial(String mode){this.mode=mode;}
        void begin()throws Exception {
            stream=new AnswerStream(activity,new Item(Source.ZHIHU,"合成分页测试",URL,""));
            WebView web=(WebView)field(stream,"web");WebViewClient delegate=web.getWebViewClient();
            web.setWebViewClient(new WebViewClient(){
                WebResourceResponse respond(String url){
                    if(url.equals(URL))return response(200,"text/html",html());
                    if(url.startsWith(API+"?")){
                        int n=++requests;urls.add(url);entered.countDown();
                        if(mode.equals("close")||mode.equals("timeout"))try{Thread.sleep(mode.equals("close")?1500:6000);}catch(InterruptedException ignored){}
                        if(mode.equals("403"))return response(403,"application/json","{\"error\":\"synthetic gate\"}");
                        if(mode.equals("foreign"))return response(200,"application/json",json(1,"https://evil.test/answers?offset=10",false));
                        if(mode.equals("cap")||mode.equals("timeout")||mode.equals("close"))return response(200,"application/json",json(1,API+"?offset="+(n*10),false));
                        if(url.contains("offset=20"))return response(200,"application/json",json(3,"",true));
                        if(url.contains("offset=10"))return response(200,"application/json",json(2,API+"?offset=20",false));
                        return response(200,"application/json",json(1,API+"?offset=10",false));
                    }
                    if(url.contains("evil.test"))foreignRequests++;
                    return response(200,"text/plain","");
                }
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest r){return respond(r.getUrl().toString());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView w,String u){return respond(u);}
                @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return delegate.shouldOverrideUrlLoading(w,r);}
                @Override public void onPageFinished(WebView w,String u){delegate.onPageFinished(w,u);}
                @Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){delegate.onReceivedSslError(w,h,e);}
            });
            more(previous());
        }
        void more(Document old){started=SystemClock.elapsedRealtime();stream.more(old,new Repository.Result<Document>(){
            public void success(Document d){callbacks++;results.add(d);}
            public void failure(String reason){callbacks++;report.append("Unexpected failure ").append(reason).append('\n');}
        });}
        Document await()throws Exception {
            Document d=results.poll(23,TimeUnit.SECONDS);
            check(d!=null,mode+" callback within 23 s scheduler allowance");
            check(SystemClock.elapsedRealtime()-started<23000,mode+" respects 20 s production bound with tolerance");
            check(requests>0,mode+" exercised real same-origin XHR interception");return d;
        }
        void close(){if(stream!=null)stream.close();}
    }
    private static WebResourceResponse response(int code,String mime,String body){return new WebResourceResponse(mime,"UTF-8",code,code==200?"OK":"Forbidden",Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));}
    private void run(String mode,CheckedTrial body){Trial trial=new Trial(mode);try{ui(trial::begin);body.run(trial);}catch(Throwable t){failures++;report.append("ERROR ").append(mode).append(' ').append(t).append('\n');}finally{try{ui(trial::close);}catch(Exception e){failures++;}}}
    interface CheckedTrial {void run(Trial trial)throws Exception;}
    @Override public void onStart(){
        try{
            report.append("Synthetic source only; real production AnswerStream and native WebView XHR. No real account/session read.\n");
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ui(()->{((Repository)field(activity,"repo")).cancelPending();((AggregateLoader)field(activity,"aggregateLoader")).cancel();Object d=field(activity,"dynamicBoard");if(d!=null)((DynamicBoard)d).close();});
            run("pages",t->{
                Document first=t.await();if(first==null)return;
                check(AnswerStream.answerIds(first).equals(new HashSet<>(Arrays.asList("1","2"))),"Duplicate first page advances to new answer while preserving old answer");
                check(t.requests==2&&first.moreStatus.equals("loaded"),"First click fetches duplicate page then one new page");
                ui(()->t.more(first));Document last=t.await();if(last==null)return;
                check(AnswerStream.answerIds(last).equals(new HashSet<>(Arrays.asList("1","2","3"))),"Second click uses retained cursor and preserves every earlier answer");
                check(t.requests==3&&t.callbacks==2,"Exactly one production callback per click");
                check(last.moreStatus.equals("end")&&last.notice.contains("末页"),"Last page reports source end with appended answer");
            });
            run("403",t->{Document d=t.await();if(d!=null){check(d.moreStatus.equals("login"),"HTTP 403 reports login restriction");check(AnswerStream.answerIds(d).contains("1"),"HTTP 403 retains existing answer");check(t.requests==1&&t.callbacks==1,"HTTP 403 stops without retry storm");}});
            run("foreign",t->{Document d=t.await();if(d!=null){check(t.requests==1&&t.foreignRequests==0,"External cursor is never requested");check(AnswerStream.answers(d)==1&&d.notice.contains("不等于已读完"),"Invalid cursor preserves old content without claiming completion");}});
            run("cap",t->{Document d=t.await();if(d!=null)check(t.requests==3&&t.callbacks==1&&AnswerStream.answers(d)==1,"Duplicate cursor chain is limited to 3 API pages per click");});
            run("timeout",t->{Document d=t.await();if(d!=null){check(t.requests<=3&&t.callbacks==1,"Slow API chain remains bounded and delivers once");check(AnswerStream.answers(d)==1,"Slow API timeout preserves previous answer");}});
            run("close",t->{check(t.entered.await(8,TimeUnit.SECONDS),"Close test reaches pending real XHR");ui(t::close);check(t.results.poll(3,TimeUnit.SECONDS)==null&&t.callbacks==0,"Late XHR after close cannot callback or replace content");});
        }catch(Throwable t){failures++;report.append("ERROR SETUP ").append(t).append('\n');}
        report.append("CHECKS ").append(checks).append(" FAILURES ").append(failures).append('\n');
        try{File dir=new File(getTargetContext().getExternalFilesDir(null),"answer-paging-qa");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,"report.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}}catch(Exception e){failures++;}
        Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
