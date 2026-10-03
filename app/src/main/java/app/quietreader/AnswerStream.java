package app.quietreader;

import android.app.Activity;
import android.os.*;
import android.webkit.*;
import org.json.JSONTokener;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** A retained source page loads real subsequent answers by scrolling, without exporting cookies. */
final class AnswerStream {
    private final WebView web;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService parser=Executors.newSingleThreadExecutor();
    private final Item item;
    private boolean closed=false,busy=false,started=false;
    private int attempts;
    private Set<String> previousAnswerIds=Collections.emptySet();
    private String lastSnapshot="";
    private String stopReason="";
    private String stopStatus="";
    private Document accumulated;
    private Repository.Result<Document> callback;
    AnswerStream(Activity activity,Item item){this(activity,item,null);}
    AnswerStream(Activity activity,Item item,WebView loadedSource){
        this.item=item;web=loadedSource==null?new WebView(activity):loadedSource;started=loadedSource!=null;
        WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setUserAgentString(Repository.DESKTOP_UA);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,false);
        web.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !UrlPolicy.belongs(Source.ZHIHU,r.getUrl().toString());}@Override public void onPageFinished(WebView w,String u){if(busy&&attempts==0)inspect();}@Override public void onReceivedSslError(WebView w,SslErrorHandler h,android.net.http.SslError e){h.cancel();finish();}});
        web.setDownloadListener((u,a,c,m,n)->{});SourceSurface.attach(activity,web);
    }
    static boolean canReuse(Item item,String loadedUrl) {
        if(!sameQuestion(item,loadedUrl))return false;
        try {
            String requested=java.net.URI.create(item.url).getPath();
            String loaded=java.net.URI.create(loadedUrl).getPath();
            // A single-answer permalink needs a fresh question page to expose the other answers.
            return requested!=null&&requested.matches("/question/\\d+/?")&&loaded!=null
                    &&requested.replaceAll("/$","").equals(loaded.replaceAll("/$",""));
        } catch(Exception invalid) { return false; }
    }
    static boolean sameQuestion(Item item,String loadedUrl) {
        if(item.source!=Source.ZHIHU||!UrlPolicy.belongs(Source.ZHIHU,item.url)||!UrlPolicy.belongs(Source.ZHIHU,loadedUrl))return false;
        try {
            java.util.regex.Pattern pattern=java.util.regex.Pattern.compile("/question/(\\d+)(?:/answer/\\d+)?/?");
            java.util.regex.Matcher requested=pattern.matcher(java.net.URI.create(item.url).getPath());
            java.util.regex.Matcher loaded=pattern.matcher(java.net.URI.create(loadedUrl).getPath());
            return requested.matches()&&loaded.matches()&&requested.group(1).equals(loaded.group(1));
        } catch(Exception invalid) { return false; }
    }
    void more(Document previous,Repository.Result<Document> cb){
        if(closed||busy)return;busy=true;callback=cb;attempts=0;stopReason="";stopStatus="";lastSnapshot="";accumulated=copy(previous);previousAnswerIds=answerIds(previous);
        main.postDelayed(this::finish,20000);
        if(!started){started=true;String url=item.url.replaceAll("(/question/\\d+).*","$1");main.post(()->{if(!closed&&busy)web.loadUrl(url);});}else inspect();
    }
    private void inspect(){
        if(closed||!busy)return;attempts++;
        if(!sameQuestion(item,web.getUrl())){leftQuestion();return;}
        web.evaluateJavascript("JSON.stringify({url:location.href,html:document.documentElement.outerHTML,login:[...document.querySelectorAll('.Modal-wrapper .SignFlow,.Modal .SignFlow,.SignContainer-content')].some(e=>e.getClientRects().length>0)})",raw->{
            if(closed||!busy)return;
            parser.execute(()->{
                Document parsed=null;boolean auth=false,wrongPage=false;
                try{Object value=new JSONTokener(raw).nextValue();if(value instanceof String&&((String)value).length()<4*1024*1024){org.json.JSONObject payload=new org.json.JSONObject((String)value);wrongPage=!sameQuestion(item,payload.optString("url"));auth=payload.optBoolean("login");if(!wrongPage)parsed=SourceParser.article(Source.ZHIHU,payload.optString("html"),item.url);}}catch(Exception ignored){}
                Document result=parsed;String snapshot=result==null?"":DocumentFingerprint.of(result);boolean needsLogin=auth,wrongSource=wrongPage;main.post(()->{
                    if(closed||!busy)return;
                    if(wrongSource||!sameQuestion(item,web.getUrl())){leftQuestion();return;}
                    if(result!=null)merge(accumulated,result);
                    if(needsLogin){stopStatus="login";stopReason="知乎要求登录才能继续。请在来源页登录后重新读取，已读回答已保留。";finish();return;}
                    // Make even one new answer available once its extracted content settles.
                    // Two equal counts alone cannot distinguish a preview from a growing answer.
                    boolean settled=result!=null&&result.hasContent()&&snapshot.equals(lastSnapshot);
                    lastSnapshot=snapshot;
                    if((hasNewAnswers(accumulated,previousAnswerIds)&&settled)||attempts>=12){finish();return;}
                    web.evaluateJavascript("(()=>{const more=document.querySelector('.Question-mainColumn .ViewAll');if(more&&/查看剩余|查看全部/.test(more.innerText)){more.click();return 'more'}const b=[...document.querySelectorAll('button.ContentItem-more')].find(e=>/阅读全文|展开/.test(e.innerText));if(b)b.click();window.scrollTo(0,document.documentElement.scrollHeight);return 'scroll'})()",null);
                    main.postDelayed(this::inspect,1100);
                });
            });
        });
    }
    private void leftQuestion(){stopStatus="unavailable";stopReason="来源页面已离开当前问题，已读回答保留。请重新读取后继续。";finish();}
    private void finish(){
        if(closed||!busy)return;busy=false;main.removeCallbacksAndMessages(null);
        accumulated.moreStatus=stopReason.isEmpty()?"loaded":stopStatus;int count=answers(accumulated);
        accumulated.notice=!stopReason.isEmpty()?stopReason:!hasNewAnswers(accumulated,previousAnswerIds)?"本次没有新增回答。已保留 "+count+" 条回答，请点「加载下一批回答」重试；未取得新内容不等于已读完全部回答。":"已加载 "+count+" 条回答。可继续下滑或点「加载下一批回答」；未取得新内容不等于已读完全部回答。";
        if(accumulated.unsupportedVideo)accumulated.notice+=" 原文包含视频；News 仅整理文字和图片，视频可在来源页观看。";
        if(accumulated.filteredVideos>0)accumulated.notice+=" "+VideoPolicy.filteredNotice(accumulated.filteredVideos);
        callback.success(accumulated);
    }
    static int answers(Document d){int n=0;for(Section s:d.sections)if(s.answer)n++;return n;}
    static Set<String> answerIds(Document d){Set<String> ids=new HashSet<>();for(Section s:d.sections)if(s.answer&&!d.filteredSectionIds.contains(s.id))ids.add(s.id);return ids;}
    /** Removal of a newly classified video can offset additions; net size is not progress. */
    static boolean hasNewAnswers(Document d,Set<String> previous){for(String id:answerIds(d))if(!previous.contains(id))return true;return false;}
    static Document copy(Document d){Document n=new Document();n.title=d.title;n.byline=d.byline;n.url=d.url;n.notice=d.notice;n.nextUrl=d.nextUrl;n.moreStatus=d.moreStatus;n.unsupportedVideo=d.unsupportedVideo;n.filteredVideo=d.filteredVideo;n.filteredVideos=d.filteredVideos;n.filteredSectionIds.addAll(d.filteredSectionIds);n.blocks.addAll(d.blocks);n.sections.addAll(d.sections);n.related.addAll(d.related);return n;}
    static void merge(Document into,Document from){
        into.unsupportedVideo|=from.unsupportedVideo;
        into.filteredSectionIds.addAll(from.filteredSectionIds);
        into.filteredVideos=Math.max(Math.max(into.filteredVideos,from.filteredVideos),into.filteredSectionIds.size());
        into.sections.removeIf(s->into.filteredSectionIds.contains(s.id));
        for(Section s:from.sections){if(into.filteredSectionIds.contains(s.id))continue;int existing=-1;for(int i=0;i<into.sections.size();i++)if(into.sections.get(i).id.equals(s.id)){existing=i;break;}if(existing<0)into.sections.add(s);else if(length(s)>length(into.sections.get(existing)))into.sections.set(existing,s);}
        into.blocks.clear();for(Section s:into.sections)into.blocks.addAll(s.blocks);
    }
    private static int length(Section s){int n=0;for(Block b:s.blocks)n+=b.value.length();return n;}
    void close(){if(closed)return;closed=true;busy=false;main.removeCallbacksAndMessages(null);parser.shutdownNow();SourceSurface.release(web);}
}
