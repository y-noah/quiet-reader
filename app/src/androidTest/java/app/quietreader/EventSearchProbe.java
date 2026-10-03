package app.quietreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.webkit.*;
import android.widget.FrameLayout;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;

/** Bounded feasibility observation only. No cookie export, captcha bypass or production feature. */
final class EventSearchProbe {
    static void run(Instrumentation test,Activity host,String filter)throws Exception {
        String[] sources={"HUPU","WALLSTREET","TIEBA","WEIBO","ZHIHU","SMZDM"};
        String q="%E8%A5%BF%E8%B4%9D";
        String[] urls={"https://bbs.hupu.com/search?q="+q+"&topicId=&sortby=general&page=1","https://wallstreetcn.com/search?q="+q,
            "https://tieba.baidu.com/f/search/res?ie=utf-8&qw="+q+"&sm=2&rn=10","https://s.weibo.com/weibo?q="+q,
            "https://www.zhihu.com/search?type=content&q="+q,"https://search.smzdm.com/?c=home&s="+q+"&v=b"};
        File dir=new File(test.getTargetContext().getExternalFilesDir(null),"event-search-probe-"+System.currentTimeMillis());dir.mkdirs();
        JSONArray reports=new JSONArray();
        for(int n=0;n<sources.length;n++){
            if(filter.equals("remaining")&&!(sources[n].equals("TIEBA")||sources[n].equals("ZHIHU")||sources[n].equals("SMZDM")))continue;
            final String source=sources[n];final String url=urls[n];final WebView[] holder={null};final String[] issue={""};long begin=android.os.SystemClock.elapsedRealtime();
            try {
                test.runOnMainSync(()->{
                    WebView web=new WebView(host);holder[0]=web;WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setBlockNetworkImage(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36");
                    if(source.equals("WEIBO"))CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
                    web.setWebViewClient(new WebViewClient(){
                        @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){String h=request.getUrl().getHost();boolean blocked=!"https".equals(request.getUrl().getScheme())||h==null||!h.matches("(?:[a-z0-9-]+\\.)*(?:hupu\\.com|wallstreetcn\\.com|baidu\\.com|weibo\\.com|weibo\\.cn|sina\\.com\\.cn|sina\\.cn|sinaimg\\.cn|zhihu\\.com|smzdm\\.com)");if(blocked)issue[0]="navigation outside source: "+h;return blocked;}
                        @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())issue[0]="main network error "+e.getErrorCode();}
                        @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse e){if(r.isForMainFrame())issue[0]="main HTTP "+e.getStatusCode();}
                        @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail){issue[0]="renderer stopped; incomplete observation";return true;}
                    });host.addContentView(web,new FrameLayout.LayoutParams(-1,-1));web.loadUrl(url);
                });
                JSONObject state=null;
                for(int attempt=0;attempt<9;attempt++){
                    Thread.sleep(2000);if(issue[0].startsWith("renderer stopped"))break;CountDownLatch done=new CountDownLatch(1);String[] value={null};
                    test.runOnMainSync(()->holder[0].evaluateJavascript("JSON.stringify((function(){var text=(document.body?document.body.innerText:'');var out=[],seen={};Array.from(document.querySelectorAll('a[href]')).forEach(function(a){try{var u=new URL(a.href),t=a.textContent.trim();if(t.indexOf('西贝')<0||t.length<4||seen[u.href])return;var ok=(u.hostname==='bbs.hupu.com'&&/^\\/\\d+\\.html$/.test(u.pathname))||(u.hostname==='wallstreetcn.com'&&/^\\/articles\\/\\d+$/.test(u.pathname))||(u.hostname==='tieba.baidu.com'&&/^\\/p\\/\\d+$/.test(u.pathname))||(/(^|\\.)zhihu.com$/.test(u.hostname)&&/^\\/(question|answer)\\//.test(u.pathname))||(/(^|\\.)smzdm.com$/.test(u.hostname)&&/^\\/p\\/\\d+/.test(u.pathname));if(ok){seen[u.href]=1;out.push({title:t.slice(0,100),url:u.origin+u.pathname});}}catch(e){}});return {host:location.hostname,path:location.pathname,title:document.title,candidates:out.slice(0,6),candidateCount:out.length,weiboCards:document.querySelectorAll('.card-wrap[mid],.card-feed').length,tiebaCards:document.querySelectorAll('.s_post').length,zhihuCards:document.querySelectorAll('.SearchResult-Card').length,loginPrompt:/登录后|登录查看更多|扫码登录|登录即可/.test(text),challenge:/安全验证|验证码|访问异常|验证您的|完成验证/.test(text),empty:/暂无搜索|没有找到|无相关结果/.test(text),bodyLength:text.length};})())",v->{value[0]=v;done.countDown();}));
                    if(!done.await(3,TimeUnit.SECONDS))break;
                    try {Object raw=new JSONTokener(value[0]).nextValue();if(raw instanceof String)state=new JSONObject((String)raw);}catch(Exception ignored){}
                    if(state!=null&&(state.optInt("candidateCount")>0||state.optBoolean("challenge")||state.optInt("weiboCards")>0))break;
                }
                if(state==null)state=new JSONObject().put("observation","no usable DOM within time budget");
                state.put("source",source).put("elapsedMs",android.os.SystemClock.elapsedRealtime()-begin).put("issue",issue[0]);reports.put(state);
                Bundle progress=new Bundle();progress.putString("stream",state.toString()+"\n");test.sendStatus(0,progress);
                try(FileOutputStream out=new FileOutputStream(new File(dir,"report.json"))){out.write(reports.toString(2).getBytes(StandardCharsets.UTF_8));}
            } finally {test.runOnMainSync(()->{if(holder[0]!=null){holder[0].stopLoading();((android.view.ViewGroup)holder[0].getParent()).removeView(holder[0]);holder[0].destroy();}});}
        }
        Bundle result=new Bundle();result.putString("stream","DONE event search feasibility observations; not a feature acceptance test. Output: "+dir);test.finish(Activity.RESULT_OK,result);
    }
}
