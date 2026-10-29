package app.quietreader;

import android.app.Activity;
import android.os.*;
import android.webkit.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** Scroll the actual source's virtual reply list; retain only identity-matched prose. */
final class TiebaReplies {
    private final Activity owner;
    private final Item item;
    private final WebView web;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService parser=Executors.newSingleThreadExecutor();
    private boolean started,prepared,closed,busy;
    private int generation,attempts,waitingAttempts;
    private long epoch;
    private Document base,accumulated;
    private String last="";
    private Set<String> previouslyRead=Set.of();
    private Repository.Result<Document> callback;
    TiebaReplies(Activity owner,Item item,WebView loaded){
        this.owner=owner;this.item=item;started=loaded!=null;web=loaded==null?new WebView(owner):loaded;
        if(loaded==null)SourceSession.configure(owner,Source.TIEBA,web);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setMediaPlaybackRequiresUserGesture(true);settings.setBlockNetworkImage(true);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){boolean blocked=!UrlPolicy.belongs(Source.TIEBA,r.getUrl().toString());if(r.isForMainFrame()&&busy&&!same(r.getUrl().toString())){fail("来源已跳转，已读回复保留");return true;}return blocked;}
            @Override public void onPageFinished(WebView v,String url){if(busy&&attempts==0)begin();}
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,android.net.http.SslError e){h.cancel();fail("来源证书异常，已读回复保留");}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())fail("来源连接失败，已读回复保留");}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){String path=r.getUrl().getPath();return !r.isForMainFrame()&&path!=null&&path.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4s|webm|m3u8|mp3|m4a|aac|ts)$")?new WebResourceResponse("text/plain","UTF-8",new java.io.ByteArrayInputStream(new byte[0])):null;}
        });web.setDownloadListener((u,a,c,m,n)->{});SourceSurface.attach(owner,web);
    }
    boolean busy(){return busy;}
    static boolean supports(Source source,Document doc){return source==Source.TIEBA&&doc.tiebaReplyRevision==TiebaReplyRelations.REVISION&&doc.virtualDiscussion&&DiscussionPages.samePage(Source.TIEBA,doc.sourceUrl(),doc.sourceUrl());}
    private boolean same(String url){return base!=null&&DiscussionPages.samePage(Source.TIEBA,base.sourceUrl(),url);}
    private boolean current(){return base!=null&&item.url.equals(base.url)&&ArticleSession.contentCurrent(base)&&epoch==Repository.articleCacheEpoch()&&new ArticleDiskCache(owner).namespace().equals(base.cachedNamespace)&&ArticleDiskCache.session(base.url).equals(base.cachedSession);}
    void more(Document previous,Repository.Result<Document> result){
        if(closed||busy)return;
        if(!supports(item.source,previous)){result.failure("来源未提供可继续读取的评论列表");return;}
        base=AnswerStream.copy(previous);accumulated=AnswerStream.copy(previous);previouslyRead=progressIds(base);epoch=Repository.articleCacheEpoch();callback=result;busy=true;attempts=0;waitingAttempts=0;last="";generation++;
        if(!current()){fail("登录或缓存状态已变化，请重新读取正文");return;}
        main.postDelayed(()->fail("来源暂未提供新的回复，已读内容保留，可重试"),20_000);
        if(started){if(prepared)inspect();else begin();}else{started=true;web.loadUrl(base.sourceUrl());}
    }
    private void begin(){prepared=true;web.evaluateJavascript("(()=>{const box=document.querySelector('.pc-pb-box');if(box)box.scrollTop=0})()",ignored->main.postDelayed(this::inspect,100));}
    private void inspect(){
        if(closed||!busy)return;if(!current()){fail("登录或缓存状态已变化，已读内容保留");return;}if(!same(web.getUrl())){fail("来源帖子或页码已变化，已读内容保留");return;}
        int run=generation;attempts++;
        web.evaluateJavascript("(()=>{const raw="+SourceSnapshot.script(Source.TIEBA)+";const snapshot=JSON.parse(raw),box=document.querySelector('.pc-pb-box'),list=box&&box.querySelector('.pc-pb-reply-list .pb-comment-list');const rows=list?Array.from(list.querySelectorAll('.pb-comment-item[data-id]')).map(e=>e.getBoundingClientRect()).filter(r=>r.height>0):[];if(box&&list&&rows.length){const b=box.getBoundingClientRect(),l=list.getBoundingClientRect();snapshot.replyWindowReady=Math.min(...rows.map(r=>r.top))<=Math.max(b.top,l.top)+8&&Math.max(...rows.map(r=>r.bottom))>=Math.min(b.bottom,l.bottom)-8;}snapshot.replyEnd=!!snapshot.replyWindowReady&&box.scrollTop+box.clientHeight>=box.scrollHeight-8&&Array.from(box.querySelectorAll('.pc-pb-reply-list .loading')).some(e=>/已加载全部评论/.test(e.textContent));return JSON.stringify(snapshot)})()",raw->{
            if(closed||!busy||run!=generation)return;
            parser.execute(()->{
                Document parsed=null;boolean wrong=false,ready=false;
                try{Object decoded=new JSONTokener(raw).nextValue();if(decoded instanceof String){JSONObject snapshot=new JSONObject((String)decoded);wrong=!same(snapshot.optString("url"));ready=snapshot.optBoolean("replyWindowReady");if(!wrong&&!snapshot.isNull("html")){parsed=SourceParser.article(Source.TIEBA,snapshot.getString("html"),base.sourceUrl());TiebaReplyTimes.attach(parsed,snapshot.optJSONObject("replyTimes"),System.currentTimeMillis());if(parsed.virtualDiscussion&&parsed.moreStatus.isEmpty()&&snapshot.optBoolean("replyEnd"))parsed.moreStatus="end";}}}catch(Exception ignored){}
                Document incoming=parsed;boolean wrongSource=wrong,windowReady=ready;
                main.post(()->{
                    if(closed||!busy||run!=generation)return;
                    if(!current()||wrongSource||!same(web.getUrl())){fail("来源帖子或登录状态已变化，已读内容保留");return;}
                    boolean knownWindow=false;
                    if(incoming!=null&&incoming.virtualDiscussion){
                        try{accumulated=merge(accumulated,incoming);}catch(IllegalArgumentException invalid){fail(invalid.getMessage());return;}
                        String fingerprint=DocumentFingerprint.of(incoming);boolean settled=fingerprint.equals(last);last=fingerprint;
                        Set<String> visible=progressIds(incoming);
                        knownWindow=windowReady&&!settled&&!visible.isEmpty()&&previouslyRead.containsAll(visible);
                        if(Set.of("end","login","error","limit").contains(accumulated.moreStatus)||windowReady&&settled&&!previouslyRead.containsAll(progressIds(accumulated))){finish();return;}
                    }
                    if(!knownWindow)waitingAttempts++;
                    if(attempts>=120||waitingAttempts>=12){if(!previouslyRead.containsAll(progressIds(accumulated)))finish();else fail("本次未取得新的回复，已读内容保留，可重试");return;}
                    // Move through overlapping windows so virtualization cannot skip
                    // unseen replies by jumping straight to the final scroll height.
                    if(windowReady)web.evaluateJavascript("(()=>{const box=document.querySelector('.pc-pb-box'),list=box&&box.querySelector('.pc-pb-reply-list');if(!box||!list)return false;box.scrollTop+=Math.max(120,box.clientHeight*.65);return true})()",null);
                    // Already cached, changing windows need only a render frame;
                    // new or stationary content retains the normal source-load wait.
                    main.postDelayed(this::inspect,knownWindow?100:1000);
                });
            });
        });
    }
    private void finish(){if(closed||!busy)return;if(!current()){fail("登录状态已变化，已读内容保留");return;}busy=false;generation++;main.removeCallbacksAndMessages(null);Document result=accumulated;if(result.moreStatus.isEmpty())result.moreStatus="loaded";Repository.Result<Document> done=callback;callback=null;done.success(result);}
    private void fail(String why){if(closed||!busy)return;busy=false;generation++;main.removeCallbacksAndMessages(null);Repository.Result<Document> done=callback;callback=null;done.failure(why);}
    void close(){if(closed)return;closed=true;busy=false;generation++;callback=null;main.removeCallbacksAndMessages(null);parser.shutdownNow();SourceSurface.release(web);}
    private static Set<String> replyIds(Document doc){Set<String> ids=new HashSet<>();for(Section section:doc.sections)if(ReaderHtml.discussion(section)&&section.sourcePostId.matches("[1-9][0-9]{0,19}"))ids.add(section.sourcePostId);return ids;}
    /** A newly loaded child is progress even when its source floor was already read. */
    private static Set<String> progressIds(Document doc){
        Set<String> parents=new HashSet<>(),ids=new HashSet<>();
        for(Section section:doc.sections)if(ReaderHtml.discussion(section)&&!section.nestedReply&&section.sourcePostId.matches("[1-9][0-9]{0,19}")){parents.add(section.id);ids.add("floor:"+section.sourcePostId);}
        for(Section section:doc.sections)if(ReaderHtml.discussion(section)&&section.nestedReply&&!section.id.isEmpty()&&!section.parentSectionId.isEmpty()&&parents.contains(section.parentSectionId))ids.add("child:"+section.parentSectionId+":"+section.id);
        for(Section section:doc.sections)if(ReaderHtml.discussion(section)&&TiebaReplyMedia.explicit(section)&&(!section.nestedReply||parents.contains(section.parentSectionId)))
            for(int n=0;n<section.blocks.size();n++)for(InlineImage image:section.blocks.get(n).inlineImages)ids.add("emoji:"+section.id+":"+n+":"+image.start+":"+image.end+":"+image.url);
        return ids;
    }
    static Document merge(Document before,Document incoming){
        if(before.tiebaReplyRevision!=TiebaReplyRelations.REVISION||incoming.tiebaReplyRevision!=TiebaReplyRelations.REVISION)throw new IllegalArgumentException("评论格式已更新，请重新读取正文，已读内容保留");
        if(!before.virtualDiscussion||!incoming.virtualDiscussion||!DiscussionPages.samePage(Source.TIEBA,before.sourceUrl(),incoming.url))throw new IllegalArgumentException("评论快照与当前帖子不匹配");
        Document result=AnswerStream.copy(before);Set<String> ids=replyIds(result);
        result.filteredSectionIds.addAll(incoming.filteredSectionIds);result.filteredVideos=Math.max(Math.max(before.filteredVideos,incoming.filteredVideos),result.filteredSectionIds.size());
        Set<String> filtered=result.filteredSectionIds;
        result.sections.removeIf(s->ReaderHtml.discussion(s)&&filtered.contains(s.id));
        Set<String> sectionIds=new HashSet<>();Map<String,Section> existing=new HashMap<>();for(Section section:result.sections){sectionIds.add(section.id);existing.put(section.id,section);}
        for(Section section:incoming.sections){
            if(!ReaderHtml.discussion(section)||result.filteredSectionIds.contains(section.id))continue;
            if(sectionIds.contains(section.id)){
                Section old=existing.get(section.id),enriched=TiebaReplyMedia.enrich(old,section);
                if(enriched!=old){result.sections.set(result.sections.indexOf(old),enriched);existing.put(section.id,enriched);}
                continue;
            }
            if(!section.nestedReply){
                if(!section.sourcePostId.matches("[1-9][0-9]{0,19}")||!ids.add(section.sourcePostId))continue;
                result.sections.add(section);
            }else{
                int position=-1;
                if(!section.parentSectionId.isEmpty())for(int n=0;n<result.sections.size();n++)if(result.sections.get(n).id.equals(section.parentSectionId)){
                    position=n+1;while(position<result.sections.size()&&result.sections.get(position).nestedReply&&result.sections.get(position).parentSectionId.equals(section.parentSectionId))position++;break;
                }
                if(position<0)result.sections.add(section);else result.sections.add(position,section);
            }
            sectionIds.add(section.id);
            existing.put(section.id,section);
        }
        result.blocks.clear();for(Section section:result.sections)result.blocks.addAll(section.blocks);
        if(result.sections.stream().filter(ReaderHtml::discussion).count()+result.filteredSectionIds.size()>DiscussionPages.MAX_REPLIES||DocumentSize.estimate(result)>ArticleDiskCache.MAX_ENTRY||!DocumentSize.serializationFits(result,ArticleDiskCache.MAX_ENTRY)){result=AnswerStream.copy(before);result.moreStatus="limit";result.notice="已保留较多回复，可在来源页继续阅读。";return result;}
        TiebaReplyTimes.mergeTimes(result,incoming);
        result.moreStatus=incoming.moreStatus;result.notice=incoming.moreStatus.equals("login")?"后续评论需要来源登录，已读内容保留。":incoming.moreStatus.equals("error")?"来源评论加载失败，已读内容保留，可重试。":"";
        return result;
    }
}
