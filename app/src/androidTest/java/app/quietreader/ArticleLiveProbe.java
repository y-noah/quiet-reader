package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import static app.quietreader.Models.*;

/** One public hot topic per reading source; V2EX may use board fallback. No login actions. */
final class ArticleLiveProbe {
    private final Instrumentation test;
    private Activity activity;
    private Repository repo;
    private DynamicReader dynamic;
    private DynamicBoard dynamicBoard;
    private final String onlySource;
    private final boolean showReader;
    private final boolean allowVideo;
    private final String requestedTarget;
    private boolean forceBrowser;
    ArticleLiveProbe browserOnly(){forceBrowser=true;return this;}
    private boolean requireReplyTimes;
    ArticleLiveProbe withReplyTimes(){requireReplyTimes=true;return this;}
    private boolean forceNative;
    ArticleLiveProbe nativeFirst(){forceNative=true;return this;}
    private final android.os.Handler cookieSampler=new android.os.Handler(android.os.Looper.getMainLooper());
    ArticleLiveProbe(Instrumentation test,String onlySource){this(test,onlySource,false);}
    ArticleLiveProbe(Instrumentation test,String onlySource,boolean showReader){this(test,onlySource,showReader,false);}
    ArticleLiveProbe(Instrumentation test,String onlySource,boolean showReader,boolean allowVideo){this(test,onlySource,showReader,allowVideo,"");}
    ArticleLiveProbe(Instrumentation test,String onlySource,boolean showReader,boolean allowVideo,String target){this.test=test;this.onlySource=onlySource;this.showReader=showReader;this.allowVideo=allowVideo;requestedTarget=target==null?"":target;}
    void run(){
        String database="test-ui-"+UUID.randomUUID()+".db";
        StringBuilder report=new StringBuilder("LIVE ARTICLE RETRIEVAL; one topic per source, no login/captcha actions\n");int ready=0,errors=0;
        try{
            if(requireReplyTimes&&(!"TIEBA".equals(onlySource)||requestedTarget.isEmpty()))throw new IllegalArgumentException("Reply-time probe requires one explicit public Tieba target");
            if(allowVideo&&!"BILIBILI".equals(onlySource))throw new IllegalArgumentException("Video fallback probe currently supports BILIBILI only");
            if(!requestedTarget.isEmpty()&&!("TIEBA".equals(onlySource)&&(TiebaTopic.isTopic(requestedTarget)||DiscussionPages.page(Source.TIEBA,requestedTarget)>0)||"HUPU".equals(onlySource)&&DiscussionPages.page(Source.HUPU,requestedTarget)>0||"WEIBO".equals(onlySource)&&VideoPolicy.weiboTopic(requestedTarget)||"V2EX".equals(onlySource)&&DiscussionPages.page(Source.V2EX,requestedTarget)>0||"XHS".equals(onlySource)&&!XhsArticle.id(requestedTarget).isEmpty()||"DOUYIN".equals(onlySource)&&DouyinArticle.sameContent(requestedTarget,requestedTarget)||"ITHOME".equals(onlySource)&&IthomeSource.articleUrl(requestedTarget)||"THEPAPER".equals(onlySource)&&!ThepaperSource.articleId(requestedTarget).isEmpty()||"BBC".equals(onlySource)&&!BbcChineseSource.articleId(requestedTarget).isEmpty()))throw new IllegalArgumentException("Unsupported public content target");
            if(!requestedTarget.isEmpty())report.append("Explicit current public content; source board is not queried or certified.\n");
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            test.runOnMainSync(()->repo=new Repository(activity));
            report.append("  offlineAtRequest=").append(repo.offline()).append('\n');
            for(Source source:onlySource==null?Source.displayOrder():new Source[]{Source.valueOf(onlySource)}){
                if(onlySource!=null&&!source.name().equals(onlySource))continue;
                report.append("  boardTransport=").append(!requestedTarget.isEmpty()?"explicit-target":allowVideo||source==Source.XHS||source==Source.DOUYIN||source==Source.BILIBILI?"browser":"native").append('\n');
                List<Item> items=new ArrayList<>();CountDownLatch board=new CountDownLatch(1);String[] error={""};
                Repository.Result<List<Item>> listResult=new Repository.Result<List<Item>>(){public void success(List<Item> value){items.addAll(value);board.countDown();}public void failure(String why){error[0]=why;board.countDown();}};
                boolean[] boardFallback={false};
                test.runOnMainSync(()->{if(!requestedTarget.isEmpty()){items.add(new Item(source,source==Source.XHS?"公开笔记":"公开话题",requestedTarget,""));board.countDown();}else if(allowVideo||source==Source.XHS||source==Source.DOUYIN||source==Source.BILIBILI)dynamicBoard=new DynamicBoard(activity,source,listResult);else repo.board(source,new Repository.Result<List<Item>>(){
                    public void success(List<Item> value){listResult.success(value);}
                    public void failure(String why){if(source==Source.V2EX){boardFallback[0]=true;dynamicBoard=new DynamicBoard(activity,source,listResult);}else listResult.failure(why);}
                });});
                if(!board.await(35,TimeUnit.SECONDS)||items.isEmpty()){report.append(source.label).append(" board unavailable: ").append(error[0]).append('\n');test.runOnMainSync(()->{repo.cancelPending();if(dynamicBoard!=null){dynamicBoard.close();dynamicBoard=null;}});continue;}
                report.append("  boardBrowserFallback=").append(boardFallback[0]).append('\n');
                test.runOnMainSync(()->{if(dynamicBoard!=null){dynamicBoard.close();dynamicBoard=null;}});
                Item item=items.stream().filter(i->allowVideo||!i.video&&!VideoPolicy.url(i.source,i.url)).findFirst().orElse(null);
                if(item==null){report.append(source.label).append(" no nonvideo topic\n");continue;}
                CountDownLatch done=new CountDownLatch(1);Document[] body={null};boolean[] browser={false},restoredDisk={false};long start=SystemClock.elapsedRealtime();
                String beforeCookie=android.webkit.CookieManager.getInstance().getCookie(item.url);
                Set<String> rotatedNames=new TreeSet<>();String[] previousCookie={beforeCookie};List<String> cookieChanges=new ArrayList<>();
                String[] publicAuthorNodes={""};
                Runnable[] sampleCookie={null};sampleCookie[0]=()->{if(done.getCount()==0)return;String currentCookie=android.webkit.CookieManager.getInstance().getCookie(item.url);Set<String> changed=changedCookieNames(previousCookie[0],currentCookie);rotatedNames.addAll(changed);if(source==Source.HUPU&&!Objects.equals(previousCookie[0],currentCookie))cookieChanges.add((SystemClock.elapsedRealtime()-start)+"ms names="+changed+" orderOnly="+changed.isEmpty()+" reader="+(dynamic==null?"none":readerState(dynamic)));previousCookie[0]=currentCookie;if(source==Source.WEIBO&&dynamic!=null)sampleWeiboAuthors(dynamic,publicAuthorNodes);cookieSampler.postDelayed(sampleCookie[0],1000);};
                String[] tiebaDom={""};boolean[] cacheAccepted={false};Repository.Result<Document> finish=new Repository.Result<Document>(){public void success(Document doc){body[0]=doc;cacheAccepted[0]=repo.cacheArticle(doc,Repository.articleCacheEpoch());done.countDown();}public void failure(String why){error[0]=why;done.countDown();}};
                Runnable fallback=()->{browser[0]=true;dynamic=new DynamicReader(activity,item,finish,true);if(source==Source.TIEBA)sampleTiebaDom(dynamic,tiebaDom);};
                test.runOnMainSync(()->{cookieSampler.post(sampleCookie[0]);if(repo.offline()){restoredDisk[0]=true;repo.restoreArticle(item,doc->{if(doc!=null)finish.success(doc);else finish.failure("离线正文缓存不存在");});}else if(forceBrowser||!forceNative&&SourceSession.usesWeb(activity,source))fallback.run();else repo.article(item,new Repository.Result<Document>(){
                    public void success(Document doc){if(SourceSession.nativeDocumentReady(source,doc))finish.success(doc);else fallback.run();}
                    public void failure(String why){fallback.run();}
                });});
                boolean completed=done.await(50,TimeUnit.SECONDS);long elapsed=SystemClock.elapsedRealtime()-start;
                boolean[] hit={false};String[] readerState={restoredDisk[0]?"disk-restore":"native"};test.runOnMainSync(()->{cookieSampler.removeCallbacksAndMessages(null);hit[0]=repo.cachedArticle(item)!=null;repo.cancelPending();if(dynamic!=null){readerState[0]=readerState(dynamic);dynamic.close();dynamic=null;}});
                Document doc=body[0];boolean available=completed&&doc!=null&&doc.hasContent()&&!doc.loginRequired&&!doc.sourceUnavailable;
                if(available)ready++;
                report.append(source.label).append(" title=").append(item.title).append(" browser=").append(browser[0]).append(" ms=").append(elapsed).append(" content=").append(available).append(" cacheAccepted=").append(cacheAccepted[0]).append(" cacheHit=").append(hit[0]);
                if(doc!=null)report.append(" sections=").append(doc.sections.size()).append(" blocks=").append(doc.blocks.size()).append(" loginRequired=").append(doc.loginRequired).append(" unavailable=").append(doc.sourceUnavailable).append(" filteredVideo=").append(doc.filteredVideo).append(" filteredVideos=").append(doc.filteredVideos).append(" unsupportedVideo=").append(doc.unsupportedVideo).append(" poster=").append(!doc.videoPoster.isEmpty()).append(" related=").append(doc.related.size());
                if(doc!=null&&source==Source.V2EX)report.append(" sourceReplyIds=").append(doc.sections.stream().filter(s->s.id.matches("v2ex-r_[0-9]+")).count()).append(" authoredReplies=").append(doc.sections.stream().filter(s->s.label.startsWith("回复 · ")).count()).append(" mainAuthor=").append(!doc.byline.isEmpty()).append(" page=").append(doc.discussionPage).append(" moreStatus=").append(doc.moreStatus).append(" hasNext=").append(DiscussionPages.next(source,doc));
                if(doc!=null&&source==Source.HUPU)report.append(" sourceReplyIds=").append(doc.sections.stream().filter(s->s.id.matches("hupu-reply-[0-9]+")).count()).append(" authoredReplies=").append(doc.sections.stream().filter(s->s.label.startsWith("回复 · ")).count()).append(" mainAuthor=").append(!doc.byline.isEmpty()).append(" page=").append(doc.discussionPage).append(" moreStatus=").append(doc.moreStatus).append(" hasNext=").append(DiscussionPages.next(source,doc));
                if(doc!=null&&source==Source.TIEBA)report.append(" resolvedTopic=").append(doc.topic!=null).append(" actualPost=").append(doc.sourceUrl()).append(" actualTitle=").append(doc.title).append(" officialFirst=").append(doc.topic==null?"":doc.topic.choices.stream().filter(choice->!choice.video).map(choice->choice.url).findFirst().orElse("")).append(" metadataMatches=").append(ArticleVersion.matches(item,doc)).append(" page=").append(doc.discussionPage).append(" moreStatus=").append(doc.moreStatus).append(" hasNext=").append(DiscussionPages.next(source,doc));
                if(!completed)report.append(" timeout");if(!error[0].isEmpty())report.append(" error=").append(error[0]);report.append('\n');
                report.append("  acquisitionState=").append(readerState[0]).append('\n');
                if(source==Source.TIEBA)report.append("  publicDomStructure=").append(tiebaDom[0]).append('\n');
                if(source==Source.TIEBA&&doc!=null){
                    long children=doc.sections.stream().filter(s->s.nestedReply).count(),timed=doc.sections.stream().filter(s->s.nestedReply&&TiebaReplyTimes.VERSION.equals(s.sourceReplyEvidence)&&s.sourceReplyAt>0).count();
                    report.append("  replyTimes children=").append(children).append(" sourceTimedChildren=").append(timed).append(" timedTopLevel=").append(doc.sections.stream().filter(s->!s.nestedReply&&s.sourceReplyAt>0).count()).append('\n');
                    if(requireReplyTimes){
                        if(!available||timed==0)throw new IllegalStateException("Live source has no verified child creation times; metadata retrieval remains unproven");
                        java.util.Set<String> parentIds=new java.util.HashSet<>();long floorImages=0;
                        for(Section part:doc.sections)if(!part.nestedReply&&!part.sourcePostId.isEmpty()){parentIds.add(part.id);floorImages+=part.blocks.stream().filter(b->b.type.equals("image")).count();}
                        long linked=doc.sections.stream().filter(s->s.nestedReply&&parentIds.contains(s.parentSectionId)).count();
                        report.append("  sourceParentFloors=").append(parentIds.size()).append(" childParentMatches=").append(linked).append(" floorImageBlocks=").append(floorImages).append('\n');
                        if(linked!=children)throw new IllegalStateException("Live nested reply lost its rendered source parent floor");
                        Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));int matched=0;
                        for(Section section:doc.sections)if(section.sourceReplyAt>0){
                            Section saved=restored.sections.stream().filter(s->s.id.equals(section.id)).findFirst().orElseThrow();
                            if(!section.nestedReply||!TiebaReplyTimes.valid(doc.sourceUrl(),section,section.sourceReplyAt,System.currentTimeMillis())||saved.sourceReplyAt!=section.sourceReplyAt||!saved.parentSectionId.equals(section.parentSectionId)||!saved.sourceReplyEvidence.equals(section.sourceReplyEvidence))throw new IllegalStateException("Live source timestamp or history identity mismatch");matched++;
                        }
                        report.append("  replyTimesHistoryExact=").append(matched).append("; production WebView snapshot/parser/history, anonymous public source only\n");
                        if(doc.moreStatus.equals("login")){boolean rejected=ArticleProgressEvidence.freeze(Source.TIEBA,doc,app.quietreader.discovery.HotCandidate.hash("live-anonymous-probe"),System.currentTimeMillis())==null;report.append("  restrictedProgressRejected=").append(rejected).append('\n');if(!rejected)throw new IllegalStateException("Restricted live source created progress evidence");}
                    }
                }
                if(requireReplyTimes&&doc==null)throw new IllegalStateException("Live source did not return a document");
                report.append("  rotatedCookieNamesDuringRead=").append(rotatedNames).append("; values never exported\n");
                if(source==Source.HUPU)for(String change:cookieChanges)report.append("  cookieTimeline ").append(change).append("; values never exported\n");
                if(doc!=null)report.append("  labels=").append(doc.sections.stream().map(s->s.label).limit(5).collect(java.util.stream.Collectors.toList())).append(" notice=").append(doc.notice).append('\n');
                if(source==Source.WEIBO)report.append("  publicAuthorNodes=").append(publicAuthorNodes[0]).append('\n');
                report.append("  changedCookieNames=").append(changedCookieNames(beforeCookie,android.webkit.CookieManager.getInstance().getCookie(item.url))).append("; values never exported\n");
                if(source==Source.TIEBA){report.append("  changedIdentityCookieNames=").append(changedCookieNames(ArticleCookies.identity(item.url,beforeCookie),ArticleCookies.identity(item.url,android.webkit.CookieManager.getInstance().getCookie(item.url)))).append("; values never exported\n");String jump=cookieMap(android.webkit.CookieManager.getInstance().getCookie(item.url)).get("USER_JUMP");report.append("  userJumpShape=").append(jumpShape(jump)).append("; value never exported\n");}
                if(forceBrowser&&available){
                    String afterRead=android.webkit.CookieManager.getInstance().getCookie(item.url);
                    Thread.sleep(5000);boolean[] delayedHit={false};
                    test.runOnMainSync(()->delayedHit[0]=repo.cachedArticle(item)!=null);
                    report.append("  browserCacheAfter5s=").append(delayedHit[0]).append(" metadataMatches=").append(ArticleVersion.matches(item,doc))
                        .append(" lateChangedCookieNames=").append(changedCookieNames(afterRead,android.webkit.CookieManager.getInstance().getCookie(item.url))).append("; values never exported\n");
                }
                if(showReader&&available)present(item,report);
                if((source==Source.V2EX||source==Source.HUPU)&&available)followDiscussion(item,doc,report);
                if(source==Source.TIEBA&&doc!=null&&!requireReplyTimes)followTieba(doc,report);
            }
        }catch(Throwable error){errors++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{cookieSampler.removeCallbacksAndMessages(null);if(dynamicBoard!=null)dynamicBoard.close();if(dynamic!=null)dynamic.close();if(repo!=null)repo.close();if(activity!=null)activity.finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("readable=").append(ready).append(onlySource==null?"/5 probeErrors=":"/1 probeErrors=").append(errors).append("; not a full-content or authenticated reading certification\n");
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(errors==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static String jumpShape(String value){
        if(value==null)return "absent";if(value.matches("[01]"))return "boolean";if(value.matches("[0-9]{10,13}"))return "timestamp";
        try{String decoded=java.net.URLDecoder.decode(value,"UTF-8").replaceAll("^\"|\"$","");if(UrlPolicy.belongs(Source.TIEBA,decoded))return "tieba-navigation-url";if(decoded.startsWith("/"))return "navigation-path";if(decoded.startsWith("{"))return "json";if(decoded.matches("[0-9]+"))return "integer";}catch(Exception ignored){}return "opaque";
    }
    private static void sampleTiebaDom(DynamicReader reader,String[] output){
        try{
            java.lang.reflect.Field field=DynamicReader.class.getDeclaredField("web");field.setAccessible(true);android.webkit.WebView web=(android.webkit.WebView)field.get(reader);android.webkit.WebViewClient delegate=web.getWebViewClient();
            web.setWebViewClient(new android.webkit.WebViewClient(){
                public android.webkit.WebResourceResponse shouldInterceptRequest(android.webkit.WebView v,android.webkit.WebResourceRequest r){return delegate.shouldInterceptRequest(v,r);}
                public boolean shouldOverrideUrlLoading(android.webkit.WebView v,android.webkit.WebResourceRequest r){return delegate.shouldOverrideUrlLoading(v,r);}
                public void onPageCommitVisible(android.webkit.WebView v,String u){delegate.onPageCommitVisible(v,u);}
                public void onPageFinished(android.webkit.WebView v,String u){delegate.onPageFinished(v,u);for(int delay:new int[]{500,2500,5000})v.postDelayed(()->{if(!u.equals(v.getUrl()))return;v.evaluateJavascript("(()=>({floorIds:Array.from(document.querySelectorAll('.pb-comment-item')).slice(0,16).map(e=>({id:e.getAttribute('data-id'),children:e.querySelectorAll('.pb-lzl-item').length,regions:Array.from(e.querySelectorAll('.comment-content,.pb-rich-text')).filter(c=>!c.closest('.pb-lzl-item')).map(c=>({tag:c.tagName,cls:c.className,textLength:c.textContent.length,images:c.querySelectorAll('img').length,ownedImages:Array.from(c.querySelectorAll('img')).filter(i=>!i.closest('.pb-lzl-item')).slice(0,8).map(i=>({cls:i.className,attributes:Object.fromEntries(Array.from(i.attributes).filter(a=>['src','data-src','data-original','data-tb-lazyload','width','height'].includes(a.name)).map(a=>[a.name,a.value]))})),video:c.querySelectorAll('video').length,children:Array.from(c.children).map(n=>({tag:n.tagName,cls:n.className}))}))})),sourceComponent:Array.from(document.querySelectorAll('.pb-lzl-item')).slice(0,16).map(e=>{const c=e.__vue__||e.__vueParentComponent;return c?{name:c.$options?c.$options.name:c.type&&c.type.name,ownRoot:c.$el===e||!!c.subTree&&c.subTree.el===e,props:Object.entries(c.$props||c.props||{}).slice(0,12).map(([key,v])=>({key:key,type:typeof v,parentId:key==='pid'?v:null,fields:v&&typeof v==='object'?Object.keys(v).slice(0,35):[],ids:v&&typeof v==='object'?Object.fromEntries(Object.entries(v).filter(([k,x])=>/^(id|pid|post_id|comment_id|spid|thread_id|tid|time)$/.test(k)&&['string','number'].includes(typeof x))):{}}))}:null;}),children:Array.from(document.querySelectorAll('.pb-lzl-item,.lzl_single_post')).slice(0,16).map(e=>({id:e.id,dataId:e.getAttribute('data-id'),attributes:Array.from(e.attributes).map(a=>a.name)})),loginMask:Array.from(document.querySelectorAll('.pc-pb-reply-list .login-guard-mask,.SignContainer-content')).map(e=>({cls:e.className,visible:!!e.getClientRects().length})),hidden:document.hidden,metadata:window.__qrReplyTimes?{outcome:window.__qrReplyTimes.outcome,age:Date.now()-window.__qrReplyTimes.started,rows:window.__qrReplyTimes.rows?window.__qrReplyTimes.rows.length:0}:null,url:location.href,title:document.title,counts:['.pc-pb-box','#j_p_postlist','.d_post_content','.image-text','.pb-rich-text','.error-page','.error-msg'].map(s=>[s,document.querySelectorAll(s).length]),tree:Array.from(document.querySelectorAll('#app>*,#app>*>*,.pc-pb-box>*')).slice(0,20).map(e=>({tag:e.tagName,cls:e.className})),gates:Array.from(document.querySelectorAll('.error-msg,.error-page,.error-tip')).slice(0,2).map(e=>e.innerText.slice(0,100))}))()",value->output[0]=value);},delay);}
                public void onReceivedSslError(android.webkit.WebView v,android.webkit.SslErrorHandler h,android.net.http.SslError e){delegate.onReceivedSslError(v,h,e);}
                public void onReceivedError(android.webkit.WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceError e){delegate.onReceivedError(v,r,e);}
                public void onReceivedHttpError(android.webkit.WebView v,android.webkit.WebResourceRequest r,android.webkit.WebResourceResponse e){delegate.onReceivedHttpError(v,r,e);}
            });
        }catch(Exception ignored){}
    }
    private static void sampleWeiboAuthors(DynamicReader dynamic,String[] result){
        try{
            java.lang.reflect.Field replacement=DynamicReader.class.getDeclaredField("replacement"),field=DynamicReader.class.getDeclaredField("web");replacement.setAccessible(true);field.setAccessible(true);
            DynamicReader next=(DynamicReader)replacement.get(dynamic);if(next!=null){sampleWeiboAuthors(next,result);return;}
            android.webkit.WebView web=(android.webkit.WebView)field.get(dynamic);
            // Public post headers only. Never read account controls, cookies or storage.
            web.evaluateJavascript("(()=>{const text=document.querySelector('.weibo-text');if(!text)return null;const card=text.closest('.card-wrap,.card');if(!card)return null;return Array.from(card.querySelectorAll('header,h3,.weibo-info,.m-text-cut,.name,.username')).filter(e=>!e.closest('.weibo-text,.weibo-rp,.weibo-media')).slice(0,8).map(e=>({tag:e.tagName,cls:e.className,text:e.innerText.slice(0,120),children:Array.from(e.children).slice(0,4).map(c=>({tag:c.tagName,cls:c.className,text:c.innerText.slice(0,80)}))}))})()",value->{if(!"null".equals(value))result[0]=value;});
        }catch(Exception ignored){}
    }
    private void followDiscussion(Item item,Document before,StringBuilder report)throws Exception{
        if(!DiscussionPages.next(item.source,before)){report.append("  discussionContinuation=not-offered; multi-page reading not certified\n");return;}
        DiscussionStream[] stream={null};CountDownLatch done=new CountDownLatch(1);Document[] merged={null};String[] error={""};long start=SystemClock.elapsedRealtime();
        try{
            test.runOnMainSync(()->{stream[0]=new DiscussionStream(activity,item);stream[0].more(before,new Repository.Result<Document>(){
                public void success(Document value){merged[0]=value;done.countDown();}
                public void failure(String why){error[0]=why;done.countDown();}
            });});
            boolean completed=done.await(55,TimeUnit.SECONDS);Document after=merged[0];
            report.append("  discussionContinuation completed=").append(completed).append(" ms=").append(SystemClock.elapsedRealtime()-start).append(" requestedPage=").append(DiscussionPages.page(item.source,before.nextUrl));
            if(after!=null){
                long oldReplies=before.sections.stream().filter(ReaderHtml::discussion).count(),newReplies=after.sections.stream().filter(ReaderHtml::discussion).count();
                boolean original=before.url.equals(after.url)&&before.metadataHash.equals(after.metadataHash)&&before.sections.get(0)==after.sections.get(0);
                report.append(" page=").append(after.discussionPage).append(" repliesBefore=").append(oldReplies).append(" repliesAfter=").append(newReplies).append(" mainAndIdentityPreserved=").append(original).append(" status=").append(after.moreStatus);
                if(!original||newReplies<=oldReplies||after.discussionPage<=before.discussionPage)throw new IllegalStateException("Live continuation did not append new replies while preserving main post");
            }
            if(!error[0].isEmpty())report.append(" error=").append(error[0]);report.append('\n');
        }finally{test.runOnMainSync(()->{if(stream[0]!=null)stream[0].close();});}
    }
    private void followTieba(Document topic,StringBuilder report)throws Exception{
        String url=topic.blocks.stream().flatMap(b->b.inlineLinks.stream()).map(l->l.url).filter(u->ReaderLinks.readerSource(u)==Source.TIEBA).findFirst().orElse("");
        if(url.isEmpty()){report.append("  no topic post link to follow\n");return;}
        Item item=new Item(Source.TIEBA,"话题内帖子",url,"");CountDownLatch done=new CountDownLatch(1);Document[] body={null};String[] error={""};boolean[] browser={false};long start=SystemClock.elapsedRealtime();
        Repository.Result<Document> result=new Repository.Result<>(){public void success(Document doc){body[0]=doc;repo.cacheArticle(doc);done.countDown();}public void failure(String why){error[0]=why;done.countDown();}};
        Runnable fallback=()->{browser[0]=true;dynamic=new DynamicReader(activity,item,result);};
        test.runOnMainSync(()->repo.article(item,new Repository.Result<>(){public void success(Document doc){if(doc.canPresent())result.success(doc);else fallback.run();}public void failure(String why){fallback.run();}}));
        boolean completed=done.await(50,TimeUnit.SECONDS);boolean[] hit={false};test.runOnMainSync(()->{hit[0]=repo.cachedArticle(item)!=null;repo.cancelPending();if(dynamic!=null){dynamic.close();dynamic=null;}});
        Document doc=body[0];report.append("  followedPost=").append(url).append(" completed=").append(completed).append(" browser=").append(browser[0]).append(" ms=").append(SystemClock.elapsedRealtime()-start).append(" content=").append(doc!=null&&doc.hasContent()).append(" cacheHit=").append(hit[0]);
        if(doc!=null)report.append(" sections=").append(doc.sections.size()).append(" blocks=").append(doc.blocks.size()).append(" labels=").append(doc.sections.stream().map(s->s.label).limit(4).collect(java.util.stream.Collectors.toList()));
        if(!error[0].isEmpty())report.append(" error=").append(error[0]);report.append('\n');
        if(showReader&&doc!=null&&doc.hasContent())present(item,report);
    }
    private void present(Item item,StringBuilder report)throws Exception{
        MainActivity reader;
        if(allowVideo||!requestedTarget.isEmpty()){
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false),login=test.addMonitor(LoginActivity.class.getName(),null,false);
            try{
                test.runOnMainSync(()->{try{
                    app.quietreader.discovery.HotCandidate candidate=new app.quietreader.discovery.HotCandidate(item.source,item.url,item.title,"","",item.video,1,null,System.currentTimeMillis());
                    java.lang.reflect.Method open=DiscoveryActivity.class.getDeclaredMethod("open",app.quietreader.discovery.HotCandidate.class);open.setAccessible(true);open.invoke(activity,candidate);
                }catch(Exception e){throw new IllegalStateException(e);}});
                Activity opened=test.waitForMonitorWithTimeout(monitor,5000);
                if(!(opened instanceof MainActivity)||login.getHits()!=0)throw new IllegalStateException("Video entry bypassed common reader");
                reader=(MainActivity)opened;report.append("  productionEntry=common-reader rawPageOpens=0\n");
            }finally{test.removeMonitor(monitor);test.removeMonitor(login);}
        }else reader=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            java.lang.reflect.Field webField=MainActivity.class.getDeclaredField("readerWeb"),dynamicField=MainActivity.class.getDeclaredField("dynamic");webField.setAccessible(true);dynamicField.setAccessible(true);
            long until=SystemClock.elapsedRealtime()+7000;boolean[] ready={false};
            while(SystemClock.elapsedRealtime()<until){test.runOnMainSync(()->{try{ready[0]=webField.get(reader)!=null;}catch(Exception e){throw new IllegalStateException(e);}});if(ready[0])break;Thread.sleep(100);}
            Thread.sleep(700);boolean[] sourceReload={false};test.runOnMainSync(()->{try{sourceReload[0]=dynamicField.get(reader)!=null;}catch(Exception e){throw new IllegalStateException(e);}});
            report.append("  readerUI=").append(ready[0]).append(" sourceReload=").append(sourceReload[0]).append('\n');
            if(!ready[0]||sourceReload[0])throw new IllegalStateException("Live body did not reach common cached reader");
            if((allowVideo||!requestedTarget.isEmpty())&&item.source!=Source.V2EX&&item.source!=Source.HUPU&&item.source!=Source.WEIBO){
                android.webkit.WebView web=(android.webkit.WebView)webField.get(reader);boolean coverLoaded=false;long coverUntil=SystemClock.elapsedRealtime()+12000;
                java.lang.reflect.Field documentField=MainActivity.class.getDeclaredField("reading");documentField.setAccessible(true);Document displayed=(Document)documentField.get(reader);
                String imageSelector=displayed.unsupportedVideo?".media-poster,.inline-media .media-cover":".part img:not(.emoji)";
                report.append("  imageSelector=").append(imageSelector).append('\n');
                while(SystemClock.elapsedRealtime()<coverUntil){
                    CountDownLatch sampled=new CountDownLatch(1);boolean[] good={false};
                    test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("!!document.querySelector('"+imageSelector+"')&&document.querySelector('"+imageSelector+"').naturalWidth>0",value->{good[0]="true".equals(value);web.getSettings().setJavaScriptEnabled(false);sampled.countDown();});});
                    if(!sampled.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Cover DOM sampling timeout");
                    if(good[0]){coverLoaded=true;break;}Thread.sleep(250);
                }
                report.append(allowVideo?"  actualCoverLoaded=":"  actualBodyImageLoaded=").append(coverLoaded).append('\n');
                if(!coverLoaded)throw new IllegalStateException("Actual video cover did not load");
                CountDownLatch measured=new CountDownLatch(1);String[] metrics={""};
                test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify({overflow:document.documentElement.scrollWidth>innerWidth+1,players:document.querySelectorAll('video,iframe').length,imageWidth:document.querySelector('"+imageSelector+"').naturalWidth})",value->{metrics[0]=value;web.getSettings().setJavaScriptEnabled(false);measured.countDown();});});
                if(!measured.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Cover geometry sampling timeout");report.append("  actualReaderMetrics=").append(metrics[0]).append('\n');
                // A history restore may still be posted by onPageFinished after decode.
                Thread.sleep(700);
                CountDownLatch positioned=new CountDownLatch(1),painted=new CountDownLatch(1);
                test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.querySelector('"+imageSelector+"').scrollIntoView({block:'center'})",value->{web.getSettings().setJavaScriptEnabled(false);positioned.countDown();});});
                if(!positioned.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Image positioning timeout");test.waitForIdleSync();
                test.runOnMainSync(()->web.postVisualStateCallback(1,new android.webkit.WebView.VisualStateCallback(){public void onComplete(long id){painted.countDown();}}));
                if(!painted.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Image visual frame timeout");Thread.sleep(250);report.append("  actualImagePositionedVisibleFrame=true\n");
                CountDownLatch geometry=new CountDownLatch(1);
                test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("(()=>{const i=document.querySelector('"+imageSelector+"'),r=i.getBoundingClientRect();return JSON.stringify({y:scrollY,height:innerHeight,top:r.top,bottom:r.bottom,width:r.width,visibility:getComputedStyle(i).visibility,opacity:getComputedStyle(i).opacity})})()",value->{report.append("  positionedImageGeometry=").append(value).append(" nativeScroll=").append(web.getScrollY()).append(" focus=").append(web.hasWindowFocus()).append('\n');web.getSettings().setJavaScriptEnabled(false);geometry.countDown();});});
                if(!geometry.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Positioned image geometry timeout");
            }
            if(item.source==Source.WEIBO){
                java.lang.reflect.Field field=MainActivity.class.getDeclaredField("reading");field.setAccessible(true);Document displayed=(Document)field.get(reader);
                Section authored=displayed.sections.stream().filter(s->s.label.startsWith("微博 · ")).findFirst().orElse(null);
                report.append("  displayedAuthoredPosts=").append(displayed.sections.stream().filter(s->s.label.startsWith("微博 · ")).count()).append("; reading history may retain older body\n");
                if(authored!=null){
                    android.webkit.WebView web=(android.webkit.WebView)webField.get(reader);String[] valid={""};long untilDOM=SystemClock.elapsedRealtime()+7000;
                    do{CountDownLatch measured=new CountDownLatch(1);test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.documentElement.scrollWidth<=innerWidth+1&&Array.from(document.querySelectorAll('[data-section-label]')).some(e=>e.getAttribute('data-section-label')==="+org.json.JSONObject.quote(authored.label)+")",value->{valid[0]=value;web.getSettings().setJavaScriptEnabled(false);measured.countDown();});});if(!measured.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Weibo author DOM sampling timeout");if("true".equals(valid[0]))break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<untilDOM);
                    if(!"true".equals(valid[0]))throw new IllegalStateException("Weibo author did not reach reader DOM or horizontal overflow");
                    report.append("  actualAuthorDOM=true horizontalOverflow=false\n");
                }else if(!requestedTarget.isEmpty())throw new IllegalStateException("Explicit public topic has no author in actual displayed body");
            }
            if(item.source==Source.V2EX||item.source==Source.HUPU){
                android.webkit.WebView web=(android.webkit.WebView)webField.get(reader);String[] valid={""};long untilDOM=SystemClock.elapsedRealtime()+7000;
                do{
                    CountDownLatch measured=new CountDownLatch(1);
                    test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.readyState!=='loading'&&document.documentElement.scrollWidth<=innerWidth+1&&document.querySelectorAll('.discussion').length>0",value->{valid[0]=value;web.getSettings().setJavaScriptEnabled(false);measured.countDown();});});
                    if(!measured.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Discussion DOM sampling timeout");
                    if("true".equals(valid[0]))break;Thread.sleep(100);
                }while(SystemClock.elapsedRealtime()<untilDOM);
                if(!"true".equals(valid[0])){
                    CountDownLatch measured=new CountDownLatch(1);
                    test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("JSON.stringify({ready:document.readyState,width:innerWidth,scroll:document.documentElement.scrollWidth,replies:document.querySelectorAll('.discussion').length})",value->{report.append("  failedDiscussionDOM=").append(value).append('\n');web.getSettings().setJavaScriptEnabled(false);measured.countDown();});});
                    measured.await(3,TimeUnit.SECONDS);throw new IllegalStateException("Live forum reader lacks replies or has horizontal overflow");
                }
                report.append("  actualDiscussionDOM=true horizontalOverflow=false\n");
                continueLiveDiscussion(reader,web,item.source,report);
                if(item.source==Source.HUPU)continueLiveDiscussion(reader,web,item.source,report);
            }
            android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap!=null){
                java.io.File folder=new java.io.File(test.getTargetContext().getExternalFilesDir(null),"structured-live-reader");folder.mkdirs();java.io.File file=new java.io.File(folder,item.source.name()+"-"+System.currentTimeMillis()+".png");
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}
                report.append("  screenshot=").append(file).append('\n');
            }
        }finally{test.runOnMainSync(reader::finish);test.waitForIdleSync();}
    }
    private void continueLiveDiscussion(MainActivity reader,android.webkit.WebView web,Source source,StringBuilder report)throws Exception{
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("reading");field.setAccessible(true);Document before=(Document)field.get(reader);
        if(!DiscussionPages.next(source,before)){
            long replies=before.sections.stream().filter(ReaderHtml::discussion).count();CountDownLatch measured=new CountDownLatch(1);String[] valid={""};
            test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.querySelectorAll('.discussion').length==="+replies+"&&!document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]')",value->{valid[0]=value;web.getSettings().setJavaScriptEnabled(false);measured.countDown();});});
            if(!measured.await(3,TimeUnit.SECONDS)||!"true".equals(valid[0]))throw new IllegalStateException("Restored discussion DOM differs from actual cached replies");
            report.append("  actualContinuationTap=not-offered displayedPage=").append(before.discussionPage).append(" displayedReplies=").append(replies).append("; current/restored reply DOM verified, no new tap claimed\n");return;
        }
        CountDownLatch positioned=new CountDownLatch(1);
        test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]').scrollIntoView({block:'center'})",value->positioned.countDown());});
        if(!positioned.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Live continuation positioning timeout");
        Thread.sleep(150);CountDownLatch measured=new CountDownLatch(1);String[] rectangle={""};
        test.runOnMainSync(()->web.evaluateJavascript("(()=>{const r=document.querySelector('a[href=\""+ReaderHtml.ACTION+"more\"]').getBoundingClientRect();for(const a of document.querySelectorAll('[data-reading-anchor]')){const v=a.getBoundingClientRect();if(v.bottom>0&&v.top<innerHeight)return {x:r.left+r.width/2,y:r.top+r.height/2,id:a.id,top:v.top}}return {x:r.left+r.width/2,y:r.top+r.height/2}})()",value->{rectangle[0]=value;web.getSettings().setJavaScriptEnabled(false);measured.countDown();}));
        if(!measured.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Live continuation geometry timeout");
        org.json.JSONObject point=new org.json.JSONObject(rectangle[0]);float[] xy={0,0};test.runOnMainSync(()->{int[] location=new int[2];web.getLocationOnScreen(location);xy[0]=location[0]+(float)point.optDouble("x")*web.getScale();xy[1]=location[1]+(float)point.optDouble("y")*web.getScale();});
        long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,xy[0],xy[1],0),up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,xy[0],xy[1],0);
        try{test.sendPointerSync(down);test.sendPointerSync(up);}finally{down.recycle();up.recycle();}
        Document[] shown={before};long end=SystemClock.elapsedRealtime()+55000;
        while(SystemClock.elapsedRealtime()<end){
            test.runOnMainSync(()->{try{shown[0]=(Document)field.get(reader);}catch(Exception e){throw new IllegalStateException(e);}});
            if(shown[0].discussionPage>before.discussionPage||List.of("error","login","limit").contains(shown[0].moreStatus))break;Thread.sleep(100);
        }
        long oldReplies=before.sections.stream().filter(ReaderHtml::discussion).count(),newReplies=shown[0].sections.stream().filter(ReaderHtml::discussion).count();
        report.append("  actualContinuationTap page=").append(shown[0].discussionPage).append(" repliesBefore=").append(oldReplies).append(" repliesAfter=").append(newReplies).append(" status=").append(shown[0].moreStatus).append('\n');
        if(shown[0].discussionPage<=before.discussionPage||newReplies<=oldReplies||!shown[0].url.equals(before.url))throw new IllegalStateException("Actual live continuation tap did not append same-thread replies");
        boolean rendered=false;long renderEnd=SystemClock.elapsedRealtime()+7000;
        while(SystemClock.elapsedRealtime()<renderEnd){
            CountDownLatch sampled=new CountDownLatch(1);String[] good={""};boolean ended="end".equals(shown[0].moreStatus);
            test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("document.querySelectorAll('.discussion').length==="+newReplies+"&&document.documentElement.scrollWidth<=innerWidth+1&&document.querySelectorAll('a[href=\""+ReaderHtml.ACTION+"more\"]').length==="+(ended?0:1),value->{good[0]=value;web.getSettings().setJavaScriptEnabled(false);sampled.countDown();});});
            if(!sampled.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Live appended DOM sampling timeout");
            if("true".equals(good[0])){rendered=true;break;}Thread.sleep(100);
        }
        report.append("  actualAppendedDOM=").append(rendered).append(" continuationExpected=").append(DiscussionPages.next(source,shown[0])).append('\n');
        if(!rendered)throw new IllegalStateException("Live appended replies did not reach reader DOM");
        String anchor=point.getString("id");double expectedTop=point.getDouble("top"),actualTop=Double.NaN;long anchorEnd=SystemClock.elapsedRealtime()+8000;
        while(SystemClock.elapsedRealtime()<anchorEnd){
            CountDownLatch sampled=new CountDownLatch(1);String[] top={""};
            test.runOnMainSync(()->{web.getSettings().setJavaScriptEnabled(true);web.evaluateJavascript("(()=>{const a=document.getElementById("+org.json.JSONObject.quote(anchor)+");return a?a.getBoundingClientRect().top:null})()",value->{top[0]=value;web.getSettings().setJavaScriptEnabled(false);sampled.countDown();});});
            if(!sampled.await(3,TimeUnit.SECONDS))throw new IllegalStateException("Live reading anchor sampling timeout");
            try{actualTop=Double.parseDouble(top[0]);}catch(Exception ignored){}
            if(Math.abs(actualTop-expectedTop)<3)break;Thread.sleep(100);
        }
        report.append("  actualReadingAnchorCSS=").append(expectedTop).append(" -> ").append(actualTop).append('\n');
        if(Math.abs(actualTop-expectedTop)>=3||Double.isNaN(actualTop))throw new IllegalStateException("Live append moved the visible reading paragraph");
    }
    private static Set<String> changedCookieNames(String before,String after){
        Map<String,String> a=cookieMap(before),b=cookieMap(after);Set<String> names=new TreeSet<>(a.keySet());names.addAll(b.keySet());names.removeIf(n->Objects.equals(a.get(n),b.get(n)));return names;
    }
    private static String readerState(DynamicReader reader){
        try{
            Class<?> type=DynamicReader.class;java.lang.reflect.Field attempts=type.getDeclaredField("attempts"),ended=type.getDeclaredField("ended"),replacement=type.getDeclaredField("replacement");
            attempts.setAccessible(true);ended.setAccessible(true);replacement.setAccessible(true);
            DynamicReader next=(DynamicReader)replacement.get(reader);
            DynamicReader active=next==null?reader:next;java.lang.reflect.Field selected=type.getDeclaredField("topicPostUrl"),bestField=type.getDeclaredField("best");selected.setAccessible(true);bestField.setAccessible(true);Document best=(Document)bestField.get(active);
            return "attempts="+attempts.getInt(reader)+",ended="+ended.getBoolean(reader)+(next==null?",retry=none":",retry={attempts="+attempts.getInt(next)+",ended="+ended.getBoolean(next)+"}")+",selectedPost="+selected.get(active)+",bestSections="+(best==null?0:best.sections.size())+",bestContent="+(best!=null&&best.hasContent());
        }catch(Exception error){return "diagnostic-unavailable";}
    }
    private static Map<String,String> cookieMap(String raw){Map<String,String> out=new HashMap<>();if(raw!=null)for(String entry:raw.split(";")){int at=entry.indexOf('=');if(at>0)out.put(entry.substring(0,at).trim(),entry.substring(at+1));}return out;}
}
