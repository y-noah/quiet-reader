package app.quietreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import android.webkit.*;
import java.io.ByteArrayInputStream;
import org.json.JSONArray;
import java.util.*;
import static app.quietreader.Models.*;

public final class MainActivity extends Activity {
    private int INK=0xff30323a, MUTED=0xff696c76, GREEN=0xff247bc1, BG=0xfffafafa, CARD=0xfff1f2f4;
    private boolean dark,loadingMore=false,morePaused=false,primaryPending=false,wasStopped=false;
    private ArticlePreloader preloader;
    private ReaderPrefetch readerPrefetch;
    private final ReadingEngagement engagement=new ReadingEngagement();
    private app.quietreader.discovery.HotCandidate discoveryCandidate;
    private Item previousTopicPost;
    private boolean readingResumed;
    private ReadingHistory readingHistory;
    private boolean forceSourceRead;
    private boolean videoFallbackRequested;
    private VideoPreview videoPreview;
    private volatile ReaderVideoRequests inlineVideoRequests;
    private java.util.function.BiFunction<String,VideoAttachment,ReaderVideoRequests> videoRequestFactory=ReaderVideoRequests::new;
    private android.app.Dialog inlineFullscreen;
    private WebChromeClient.CustomViewCallback inlineFullscreenCallback;
    private Runnable inlineVideoMonitor,inlineFullscreenBack;
    private Set<String> returningAnswers;
    private final android.os.Handler preloadHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private AnswerStream answers;
    private DiscussionStream discussions;
    private TiebaReplies tiebaReplies;
    private boolean discussionInterrupted;
    private final Map<WebView,Integer> readerScripts=new IdentityHashMap<>();
    private final Set<String> expanded=new HashSet<>();
    private int pendingAnchor=-1,openPosition=-1;
    private long suppressAutoUntil=0;
    private WebView continuationGestureWeb;
    private int continuationGestureY,continuationGestureRevision;
    private float continuationTouchY;
    private Runnable deferredContinuation;
    private volatile String readerHtml="",readerPageUrl="";
    private int readerRevision=0;
    private String streamAnchor="";
    private int streamAnchorOffset=0;
    private ReadingViewport sampledViewport,pendingViewport;
    private String sampledViewportPage="";
    private int sampledViewportY,pendingViewportFallback;
    private boolean viewportRestoreRunning;
    private final Runnable sampleViewport=this::captureViewport;
    private static final class ReadingMark {Set<String> expanded;int position;org.json.JSONObject viewport;}
    private org.json.JSONObject historicalViewport;
    private static final android.util.LruCache<String,ReadingMark> marks=new android.util.LruCache<>(40);
    private static final ReaderImages images=ReaderImages.shared;
    private Repository repo;
    private PendingArticle pendingArticle;
    private ReaderImageOwner imageOwner;
    private LinearLayout root, content;
    private ScrollView scroll;
    private int generation=0, font=19;
    private Item current;
    private Document reading;
    private final Deque<Item> history=new ArrayDeque<>();
    private WebView readerWeb;
    private TextView readerStatus;
    private ImagePreview imagePreview;
    private android.app.Dialog readerActions;
    private DynamicReader dynamic;
    private static final class ReadingState {
        Item current; Document document; List<Item> history; int position; Set<String> expanded;ReadingViewport viewport;org.json.JSONObject historicalViewport;PendingArticle pending;DynamicReader pendingDynamic;ArticlePreloader promoted;ReaderImageOwner images;boolean primaryPending;int openingPosition=-1;
    }
    @Override public void onCreate(Bundle state) {
        Theme.apply(this);super.onCreate(state);images.attach(this); repo=new Repository(this);readingHistory=new ReadingHistory(this);dark=Theme.dark(this);
        try{String candidate=getIntent().getStringExtra("discovery_candidate");if(candidate!=null)discoveryCandidate=app.quietreader.discovery.HotCandidate.fromJson(new org.json.JSONObject(candidate));}catch(Exception ignored){}
        BackNavigation.install(this,this::systemBack);
        if(dark){INK=0xffcdcfd5;MUTED=0xff9b9da7;GREEN=0xff77b8eb;BG=0xff121316;CARD=0xff1b1d22;}
        BG=Theme.background(this);INK=Theme.ink(this);GREEN=Theme.accent(this);
        preloader=new ArticlePreloader(this,()->{},null,3,()->{},true);
        readerPrefetch=new ReaderPrefetch(this,ReaderPrefetchPlan.decode(getIntent().getStringExtra("reader_prefetch_plan")),()->preloader,
            ()->readingResumed&&!loadingMore&&reading!=null&&reading.hasContent()&&!reading.loginRequired&&!reading.sourceUnavailable&&inlineVideoRequests==null&&videoPreview==null&&!continuationDue());
        font=getPreferences(0).getInt("font",19);
        if(state==null){
            try{String payload=getIntent().getStringExtra("discovery_item");if(payload!=null){Item item=Item.from(new org.json.JSONObject(payload));if(UrlPolicy.belongs(item.source,item.url))current=item;}}catch(Exception ignored){}
        }
        if(state!=null) {
            try{String fallback=state.getString("previousTopicPost");if(fallback!=null)previousTopicPost=Item.from(new org.json.JSONObject(fallback));}catch(Exception ignored){}
            try {
                history.addAll(Models.fromJson(new JSONArray(state.getString("history","[]"))));
                String item=state.getString("current");
                if(item!=null)current=Item.from(new org.json.JSONObject(item));
                if(current!=null&&!current.source.readable())current=null;
                history.removeIf(i->!i.source.readable());
            } catch(Exception ignored) {}
        }
        String handoff=getIntent().getStringExtra("article_handoff");getIntent().removeExtra("article_handoff");
        ArticlePreloader adopted=ArticleHandoff.claim(handoff,current,this,()->{});
        if(adopted!=null){preloader.close();preloader=adopted;}
        ReadingState retained=(ReadingState)getLastNonConfigurationInstance();
        if(current!=null)readerPrefetch.opening(current);
        if(retained!=null)historicalViewport=retained.historicalViewport;
        if(retained!=null&&retained.pending!=null&&!retained.pending.matches(retained.current)){retained.pending.close();retained.pending=null;}
        if(retained!=null&&retained.pendingDynamic!=null&&!retained.pendingDynamic.matches(retained.current)){retained.pendingDynamic.close();retained.pendingDynamic=null;}
        if(retained!=null&&retained.promoted!=null&&!retained.promoted.canRetainForeground(retained.current)){retained.promoted.close();retained.promoted=null;}
        if(retained!=null&&retained.images!=null){if(retained.document!=null&&retainedReadingCurrent(retained)&&retained.images.matches(retained.current))imageOwner=retained.images;else retained.images.close();retained.images=null;}
        if(retained!=null&&retained.current!=null&&retained.current.source.readable()&&retained.document!=null&&retainedReadingCurrent(retained)) {
            if(retained.expanded!=null)expanded.addAll(retained.expanded);
            current=retained.current;history.clear();history.addAll(retained.history);
            history.removeIf(item->!item.source.readable());
            pendingViewport=retained.viewport;pendingViewportFallback=retained.position;
            frame();scroller();readerFooter();render(retained.document);
            if(pendingViewport==null)restorePosition(retained.position);
        } else if(retained!=null&&retained.pending!=null&&retained.current!=null&&retained.current.source.readable()){
            current=retained.current;history.clear();history.addAll(retained.history);history.removeIf(item->!item.source.readable());
            expanded.addAll(retained.expanded);primaryPending=retained.primaryPending;openPosition=retained.openingPosition;videoFallbackRequested=true;
            frame();scroller();status("正在整理内容…","正在继续读取正文");readerFooter();
            pendingArticle=retained.pending;bindPendingArticle(pendingArticle,current,generation);
        } else if(retained!=null&&retained.pendingDynamic!=null&&retained.current!=null&&retained.current.source.readable()){
            current=retained.current;history.clear();history.addAll(retained.history);history.removeIf(item->!item.source.readable());
            expanded.addAll(retained.expanded);primaryPending=retained.primaryPending;openPosition=retained.openingPosition;videoFallbackRequested=true;
            frame();scroller();status("正在加载动态内容…","正在继续读取正文");readerFooter();
            dynamic=retained.pendingDynamic;dynamic.attach(this,dynamicResult(current,generation));
        } else if(retained!=null&&retained.promoted!=null&&retained.current!=null&&retained.current.source.readable()){
            current=retained.current;history.clear();history.addAll(retained.history);history.removeIf(item->!item.source.readable());
            expanded.addAll(retained.expanded);primaryPending=retained.primaryPending;openPosition=retained.openingPosition;
            frame();scroller();status("正在整理内容…","正在继续读取预读内容");readerFooter();
            preloader.close();preloader=retained.promoted;preloader.resumeForeground(this,current,promotedResult(current,generation),()->{});
        } else if(current!=null) open(current,false);
        else finish();
    }
    private void restorePosition(int position) {
        suppressAutoUntil=System.currentTimeMillis()+1000;
        if(readerWeb!=null){WebView target=readerWeb;target.postDelayed(()->{if(readerWeb==target)restoreWebPosition(target,position);},400);}
        else if(scroll!=null)scroll.post(()->scroll.scrollTo(0,position));
    }
    private boolean retainedReadingCurrent(ReadingState state){
        Document doc=state.document;
        return ArticleSession.contentCurrent(doc)&&doc.cachedSession!=null&&doc.cachedSession.equals(ArticleDiskCache.session(doc.url))
            &&new ArticleDiskCache(this).namespace().equals(doc.cachedNamespace)&&usableBody(state.current,doc);
    }
    private void restoreWebPosition(WebView web,int position){
        web.scrollTo(0,ReadingPosition.clamp(position,web.getContentHeight(),web.getScale(),web.getHeight()));
    }
    @Override public Object onRetainNonConfigurationInstance() {
        ReadingState state=new ReadingState();state.current=current;state.document=reading;
        state.historicalViewport=historicalViewport;
        state.expanded=new HashSet<>(expanded);state.history=new ArrayList<>(history);state.position=readerWeb!=null?readerWeb.getScrollY():openPosition>=0?openPosition:scroll==null?0:scroll.getScrollY();state.primaryPending=primaryPending;state.openingPosition=openPosition;
        if(pendingArticle!=null){pendingArticle.detach();state.pending=pendingArticle;pendingArticle=null;}
        if(reading==null&&dynamic!=null){dynamic.detach();state.pendingDynamic=dynamic;dynamic=null;}
        if(reading==null&&preloader!=null&&preloader.canRetainForeground(current)){preloader.detachForeground();state.promoted=preloader;preloader=null;}
        if(reading!=null&&imageOwner!=null){state.images=imageOwner;imageOwner=null;}
        if(state.position>dp(2)&&sampledViewport!=null&&sampledViewportPage.equals(readerPageUrl)&&Math.abs(state.position-sampledViewportY)<=dp(2))state.viewport=sampledViewport;
        return state;
    }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value,int size,int color) {
        TextView v=new ActionTextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(false); return v;
    }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void space(LinearLayout parent,int height) { parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private TextView button(String label,Runnable action) {
        TextView t=text(label,14,GREEN); t.setGravity(Gravity.CENTER); t.setPadding(dp(10),dp(10),dp(10),dp(10)); t.setMinHeight(dp(48)); t.setBackground(shape(CARD,6)); t.setOnClickListener(v->action.run()); t.setContentDescription(label); t.setFocusable(true); return t;
    }
    private ImageButton icon(int resource,String description,Runnable action) {
        ImageButton v=new ImageButton(this);v.setImageResource(resource);v.setColorFilter(MUTED);
        android.util.TypedValue ripple=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,ripple,true);
        v.setBackgroundResource(ripple.resourceId);v.setPadding(dp(12),dp(12),dp(12),dp(12));
        v.setContentDescription(description);v.setOnClickListener(w->action.run());return v;
    }
    private void options() {
        if(current==null||readerActions!=null&&readerActions.isShowing())return;
        ReaderActions menu=new ReaderActions(this)
            .add("重新读取",()->{repo.invalidateArticles();forceSourceRead=true;open(current,false);})
            .add("来源 / 登录",()->login(current))
            .add("阅读字号",this::fontMenu)
            .add("外观",this::theme)
            .add("阅读设置",this::settings);
        if(reading!=null&&ReaderHtml.sections(reading).size()>1)menu.add("回答 / 楼层目录",this::sectionMenu);
        if(reading!=null&&reading.topic!=null){menu.add("同话题帖子",this::topicMenu);menu.add("话题来源页",()->login(current,true));}
        readerActions=menu.show();
    }
    private void topicMenu(){
        if(reading==null||reading.topic==null)return;
        ReaderActions choices=new ReaderActions(this);String selected=reading.sourceUrl();
        for(Item post:reading.topic.choices){Item request=TiebaTopic.select(current,reading.topic,post);choices.add(post.title,()->{if(!post.url.equals(selected)||!post.url.equals(current.selectedTopicPost))open(request,false);});}
        readerActions=choices.show();
    }
    private void dismissReaderActions(){if(readerActions!=null){readerActions.dismiss();readerActions=null;}}
    private void fontMenu(){new AlertDialog.Builder(this).setTitle("阅读字号").setItems(new String[]{"舒适 · 17","标准 · 19","大字 · 22","特大 · 25"},(d,n)->changeFont(new int[]{17,19,22,25}[n])).show();}
    private void frame() {
        dismissReaderActions();closeVideo();
        if(imageOwner!=null&&!imageOwner.matches(current)){imageOwner.close();imageOwner=null;}
        cancelPendingArticle();
        repo.cancelPending();streamAnchor="";
        if(readerWeb!=null) { SourceSurface.release(readerWeb);readerWeb=null; }
        if(dynamic!=null) { dynamic.close(); dynamic=null; }
        readerStatus=null;root=column(); root.setBackgroundColor(BG); root.setFitsSystemWindows(true);root.setClipToPadding(false);
        root.setPadding(dp(18),0,dp(18),0); setContentView(root);
        if(preloader!=null)preloader.reattach();
        root.setOnApplyWindowInsetsListener((v,insets)-> { root.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(0,dp(6),0,dp(6));
        if(current!=null){TextView back=button("返回",this::goBack);back.setSingleLine(true);back.setPadding(dp(10),0,dp(10),0);back.setBackgroundColor(Color.TRANSPARENT);bar.addView(back,new LinearLayout.LayoutParams(dp(Math.max(56,28*getResources().getConfiguration().fontScale+20)),dp(48)));}
        bar.addView(new android.widget.Space(this),new LinearLayout.LayoutParams(0,1,1));
        ImageButton more=icon(R.drawable.ic_more_horiz,"更多选项",()->{});more.setOnClickListener(v->options());
        bar.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(bar);
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void scroller() {
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content=column(); content.setPadding(0,0,0,dp(26)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void status(String title,String message) {
        content.removeAllViews(); space(content,30);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);
        if(title.startsWith("正在")){
            ProgressBar spinner=new ProgressBar(this);spinner.setIndeterminate(true);
            spinner.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GREEN));spinner.setContentDescription("加载中");
            LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(dp(24),dp(24));size.setMarginEnd(dp(12));heading.addView(spinner,size);
        }
        TextView h=text(title,18,INK); h.setTypeface(null,Typeface.BOLD); heading.addView(h,new LinearLayout.LayoutParams(0,-2,1));content.addView(heading);space(content,12);
        TextView p=text(message,15,MUTED); p.setLineSpacing(dp(5),1); content.addView(p); space(content,22);
        if(!title.startsWith("正在")&&reading==null&&previousTopicPost!=null){Item fallback=previousTopicPost;content.addView(button("返回原帖",()->{previousTopicPost=null;open(fallback,false);}));}
    }
    private void open(Item item,boolean push) {
        boolean changedTopicPost=current!=null&&current.url.equals(item.url)&&!item.selectedTopicPost.isEmpty()&&(reading!=null?!item.selectedTopicPost.equals(reading.sourceUrl()):!item.selectedTopicPost.equals(current.selectedTopicPost));
        if(current==null||!current.url.equals(item.url)||previousTopicPost!=null&&!previousTopicPost.url.equals(item.url))previousTopicPost=null;
        else if(changedTopicPost&&reading!=null&&reading.topic!=null){Document previous=reading;for(Item choice:previous.topic.choices)if(choice.url.equals(previous.sourceUrl())){previousTopicPost=TiebaTopic.select(current,previous.topic,choice);break;}}
        if(!item.source.readable()){toast("此平台已移除");return;}
        if(!item.source.unifiedReading()&&!item.url.equals(getIntent().getStringExtra("imported_source_url"))){login(item);return;}
        if(current==null||!current.url.equals(item.url))returningAnswers=null;
        if(imageOwner!=null){imageOwner.close();imageOwner=null;}
        recordReadingInterest();engagement.reset(android.os.SystemClock.elapsedRealtime());rememberReading();openPosition=-1;pendingAnchor=-1;
        sampledViewport=null;pendingViewport=null;historicalViewport=null;sampledViewportPage="";viewportRestoreRunning=false;preloadHandler.removeCallbacks(sampleViewport);
        closeAnswers();expanded.clear();
        if(!UrlPolicy.belongs(item.source,item.url)) { toast("来源地址不受支持"); return; }

        if(push&&current!=null) history.push(current);
        readerPrefetch.opening(item);current=item; reading=null;videoFallbackRequested=false; generation++; final int request=generation;
        ReadingMark mark=changedTopicPost?null:marks.get(item.url);if(mark!=null){expanded.addAll(mark.expanded);openPosition=mark.position;}
        Document resumed=null;org.json.JSONObject stored=readingHistory.get(item.url);
        if(stored!=null&&!item.selectedTopicPost.isEmpty()&&!item.selectedTopicPost.equals(stored.optString("bodySource"))){stored=null;mark=null;expanded.clear();openPosition=-1;}
        if(stored!=null){
            try{
                if(mark==null){mark=new ReadingMark();mark.expanded=new HashSet<>();JSONArray values=stored.getJSONArray("expanded");for(int n=0;n<values.length();n++)mark.expanded.add(values.getString(n));mark.position=stored.optInt("position");mark.viewport=stored.optJSONObject("viewport");expanded.addAll(mark.expanded);openPosition=mark.position;}
                if(!forceSourceRead&&stored.has("document")){Document snapshot=ReadingHistory.restore(stored);if(UrlPolicy.sameThread(item.source,item.url,snapshot.url)||item.url.equals(snapshot.url))resumed=snapshot;}
            }catch(Exception ignored){}
        }
        historicalViewport=mark==null?null:mark.viewport;
        primaryPending=mark==null;
        boolean bypassDisk=forceSourceRead;Document cached=forceSourceRead?null:ArticleVersion.preferredCached(resumed,repo.cachedArticle(item),doc->usableBody(item,doc));forceSourceRead=false;if(cached!=null||repo.hiddenVideo(item))preloader.pause();
        boolean promoted=cached==null&&!repo.hiddenVideo(item)&&preloader.promote(item,promotedResult(item,request));
        frame(); scroller(); status("正在整理内容…","只提取内容，不加载原站的推荐和操作界面");
        readerFooter();
        if(cached!=null){if(cached==resumed)render(cached);else renderCached(cached);return;}
        if(promoted)return;
        if(bypassDisk)loadArticleAfterCache(item,request);
        else repo.restoreArticle(item,storedDoc->{if(request!=generation)return;if(storedDoc!=null&&usableBody(item,storedDoc))renderCached(storedDoc);else loadArticleAfterCache(item,request);});
    }
    private boolean usableBody(Item item,Document doc){return ArticleVersion.selected(item,doc)&&ArticleSession.contentCurrent(doc)&&(discoveryCandidate==null||repo.offline()||ArticleVersion.matches(item,doc));}
    private Repository.Result<Document> promotedResult(Item item,int request){return new Repository.Result<Document>(){
        public void success(Document doc){if(request==generation)renderSource(item,doc,!doc.filteredVideo&&(doc.hasContent()||doc.unsupportedVideo||doc.filteredVideos>0)?preloader.takeSource():null,false);}
        public void failure(String why){if(request==generation){status("暂时没读到正文",why);content.addView(button("重试",()->open(item,false)));content.addView(button("来源 / 登录",()->login(item)));}}
    };}
    private void loadArticleAfterCache(Item item,int request){
        if(repo.offline()){status("当前没有网络","此篇正文尚未缓存在本机。连接网络后可重试。");content.addView(button("重试",()->open(item,false)));return;}
        videoFallbackRequested=true;
        if(SourceSession.usesWeb(this,item.source)){dynamic(item,request);return;}
        cancelPendingArticle();PendingArticle pending=new PendingArticle(this,item);pendingArticle=pending;bindPendingArticle(pending,item,request);pending.start();
    }
    private void bindPendingArticle(PendingArticle pending,Item item,int request){
        pending.attach(new Repository.Result<Document>(){
            public void success(Document doc){if(pendingArticle!=pending||request!=generation)return;pendingArticle=null;if(doc.title.trim().isEmpty())doc.title=item.title;if(SourceSession.nativeDocumentReady(item.source,doc))renderCached(doc);else dynamic(item,request);}
            public void failure(String reason){if(pendingArticle!=pending||request!=generation)return;pendingArticle=null;dynamic(item,request);}
        });
    }
    private void cancelPendingArticle(){if(pendingArticle!=null){pendingArticle.close();pendingArticle=null;}}
    private void dynamic(Item item,int request) {
        status("正在加载动态内容…","使用本机登录状态读取，不会自动打开其他 App");
        dynamic=new DynamicReader(this,item,dynamicResult(item,request),true);
    }
    private Repository.Result<Document> dynamicResult(Item item,int request){
        return new Repository.Result<Document>() {
            public void success(Document doc) {
                if(request!=generation)return;
                WebView source=!doc.filteredVideo&&(doc.hasContent()||doc.unsupportedVideo||doc.filteredVideos>0)&&dynamic!=null?dynamic.takeSource():null;
                renderSource(item,doc,source);
            }
            public void failure(String reason) { if(request!=generation)return;if(dynamic!=null){dynamic.close();dynamic=null;} status("需要来源页面协助",reason); content.addView(button("重试",()->open(item,false)));content.addView(button("登录 / 加载后读取",()->login(item))); }
        };
    }
    private void renderSource(Item item,Document doc,WebView source){renderSource(item,doc,source,true);}
    private void renderSource(Item item,Document doc,WebView source,boolean updateCache){
        try {
            // Promoted prefetches are already saved; fresh dynamic acquisitions still need saving.
            // Rendering replaces the native root; attach the continuation afterward.
            render(doc,updateCache);
            if(source!=null){if(TiebaReplies.supports(item.source,doc))tiebaReplies=new TiebaReplies(this,item,source);else answers=new AnswerStream(this,item,source);source=null;}
        }finally{if(source!=null)SourceSurface.release(source);}
    }
    private void readerFooter() {
        // Reading uses the compact top bar; no persistent action tray obscures content.
    }
    private void render(Document doc) {
        render(doc,true);
    }
    /** Repository has already checked this memory/disk hit and installed it in memory. */
    private void renderCached(Document doc){render(doc,false);}
    private void render(Document doc,boolean updateCache) {
        if(historicalViewport!=null){ReadingViewport restored=SavedReadingViewport.restore(doc,historicalViewport);historicalViewport=null;if(restored!=null&&openPosition>dp(2)){pendingViewport=restored;pendingViewportFallback=openPosition;openPosition=-1;}}
        if(doc.filteredVideo){
            repo.rememberVideo(current);
            if(!videoFallbackRequested&&!repo.offline()){loadArticleAfterCache(current,generation);return;}
            Document fallback=AnswerStream.copy(doc);fallback.filteredVideo=false;fallback.videoTopic=true;fallback.unsupportedVideo=true;
            fallback.notice="这是视频主题，默认推荐会过滤。当前未取得可整理的说明或封面，可在来源页查看。";render(fallback);return;
        }
        if(doc.videoTopic)repo.rememberVideo(current);
        if(primaryPending){ReaderHtml.expandPrimary(doc,expanded);primaryPending=false;}
        morePaused=!doc.moreStatus.isEmpty()&&!doc.moreStatus.equals("loaded");
        if(returningAnswers!=null&&doc.hasContent()){
            Set<String> previous=returningAnswers;returningAnswers=null;
            long added=AnswerStream.answerIds(doc).stream().filter(id->!previous.contains(id)).count();
            toast(current.source==Source.ZHIHU?(added>0?"已重新读取，新增 "+added+" 条回答":"已重新读取，暂无新增回答"):"已重新读取来源内容");
        }
        if(updateCache)repo.cacheArticle(doc);
        repo.reconcileVideo(current,doc);
        ReadingLanguageRecorder.record(this,getIntent().getStringExtra("test_database"),discoveryCandidate,doc);
        // A question's video description is not an answer. Keep the answer reader and
        // continuation entry even when its first snapshot contains no textual blocks.
        if(doc.hasContent()||doc.unsupportedVideo||(doc.filteredVideos>0&&AnswerStream.sameQuestion(current,doc.url))) {
            if(imageOwner==null||!imageOwner.matches(current,doc)){if(imageOwner!=null)imageOwner.close();imageOwner=new ReaderImageOwner(this,current,images,doc);}
            final ReaderImageOwner imageRequests=imageOwner;
            frame(); reading=doc;
            readerStatus=text("",12,MUTED);readerStatus.setPadding(0,0,0,dp(8));readerStatus.setVisibility(View.GONE);readerStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(readerStatus);
            readerWeb=ReaderStartup.take(this);readerWeb.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY); Theme.configureWeb(readerWeb,false); readerWeb.getSettings().setJavaScriptEnabled(false);
            // Blink snapshots this policy when the document is created. Media sources
            // are still attached only by our explicit native play action; source HTML
            // cannot supply scripts, autoplay attributes, or media URLs to this page.
            readerWeb.getSettings().setMediaPlaybackRequiresUserGesture(false);
            // Older WebView compositors can leave black tiles over text after zoom/repaint.
            if(android.os.Build.VERSION.SDK_INT<=27)readerWeb.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            readerWeb.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            readerWeb.getSettings().setAllowFileAccess(false); readerWeb.getSettings().setAllowContentAccess(false); readerWeb.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            readerWeb.setWebChromeClient(new WebChromeClient(){
                @Override public void onShowCustomView(View view,CustomViewCallback callback){
                    if(inlineVideoRequests==null||inlineFullscreen!=null){callback.onCustomViewHidden();return;}
                    inlineFullscreenCallback=callback;
                    inlineFullscreen=new android.app.Dialog(MainActivity.this,android.R.style.Theme_Black_NoTitleBar_Fullscreen){@Override public void onBackPressed(){hideInlineFullscreen();}};
                    inlineFullscreen.setContentView(view);inlineFullscreen.setOnDismissListener(ignored->hideInlineFullscreen());inlineFullscreen.show();inlineFullscreen.getWindow().setLayout(-1,-1);
                    if(android.os.Build.VERSION.SDK_INT>=33)inlineFullscreenBack=FullscreenBack.register(inlineFullscreen,MainActivity.this::hideInlineFullscreen);
                }
                @Override public void onHideCustomView(){hideInlineFullscreen();}
            });
            readerWeb.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest req) {
                    String dest=req.getUrl().toString();
                    if(dest.startsWith(ReaderHtml.ACTION+"toggle?index=")){
                        try {int n=Integer.parseInt(android.net.Uri.parse(dest).getQueryParameter("index"));List<Section> parts=ReaderHtml.sections(reading);if(n>=0&&n<parts.size()){String id=parts.get(n).id;if(!expanded.add(id))expanded.remove(id);pendingAnchor=n;reloadReader(reading);}}catch(Exception ignored){}
                    } else if(dest.startsWith(ReaderHtml.ACTION+"jump?index=")){
                        try{jumpTo(Integer.parseInt(android.net.Uri.parse(dest).getQueryParameter("index")));}catch(Exception ignored){}
                    } else if(dest.startsWith(ReaderHtml.ACTION+"post?index=")){
                        try {int n=Integer.parseInt(android.net.Uri.parse(dest).getQueryParameter("index"));List<Section> parts=ReaderHtml.sections(reading);
                            if(n>=0&&n<parts.size()&&WeiboPost.canContinue(current.source,reading.url,parts.get(n)))
                                open(new Item(Source.WEIBO,"微博原帖",parts.get(n).continuationUrl,"来源全文入口"),true);
                        }catch(Exception ignored){}
                    } else if(dest.equals(ReaderHtml.ACTION+"more"))loadMore();
                    else if(dest.equals(ReaderHtml.ACTION+"play"))playVideo();
                    else if(dest.startsWith(ReaderHtml.ACTION+"play?index=")){try{android.net.Uri uri=android.net.Uri.parse(dest);int index=Integer.parseInt(uri.getQueryParameter("index"));String anchor=uri.getQueryParameter("anchor");if(anchor==null)playVideo(index);else playInlineVideo(index,anchor);}catch(RuntimeException ignored){}}
                    else if(dest.equals(ReaderHtml.ACTION+"login"))login(current);
                    else if(dest.equals(readerPageUrl))return false;
                    else if(dest.equals(doc.url))login(current);
                    else if(dest.equals(doc.nextUrl)&&UrlPolicy.sameThread(current.source,doc.url,dest))open(new Item(current.source,doc.title,dest,"下一页"),true);
                    else if(reading!=null&&reading.blocks.stream().anyMatch(b->b.type.equals("image")&&b.value.equals(dest)))zoom(dest,reading.sourceUrl());
                    else if(req.isForMainFrame()&&req.hasGesture()&&ReaderLinks.allowed(dest))openContentLink(dest);
                    return true;
                }
                @Override public void onPageCommitVisible(WebView web,String url){
                    if(web!=readerWeb||!url.equals(readerPageUrl))return;int revision=readerRevision;
                    restoreViewport(web,revision);scheduleViewportSample();
                    web.postVisualStateCallback(revision,new WebView.VisualStateCallback(){@Override public void onComplete(long id){
                        if(web!=readerWeb||revision!=readerRevision||!url.equals(readerPageUrl))return;
                        // Text can already be read while an image keeps the page loading.
                        // Image-only documents still wait for normal page completion.
                        if(reading!=null&&ReaderHtml.sections(reading).stream().flatMap(part->part.blocks.stream()).anyMatch(block->block.type.equals("text")&&!block.value.trim().isEmpty()))readerReady(web,url);
                    }});
                }
                @Override public void onPageFinished(WebView web,String url){if(web!=readerWeb||!url.equals(readerPageUrl))return;settleReaderImages(web,null);readerReady(web,url);scheduleViewportSample();int autoRevision=readerRevision;web.postDelayed(()->{if(autoRevision==readerRevision)maybeLoadMore(web,false);},1600);if(pendingViewport!=null){restoreViewport(web,readerRevision);return;}if(!streamAnchor.isEmpty()){int offset=streamAnchorOffset;String anchor=streamAnchor;streamAnchor="";int revision=readerRevision;web.postVisualStateCallback(revision,new WebView.VisualStateCallback(){@Override public void onComplete(long id){restoreReadingAnchor(web,anchor,offset,revision);}});}else if(openPosition>=0){int pos=openPosition;openPosition=-1;restorePosition(pos);}}
                @Override public void onReceivedError(WebView web,WebResourceRequest request,WebResourceError error){if(!request.isForMainFrame())settleReaderImages(web,request.getUrl().toString());}
                @Override public void onReceivedHttpError(WebView web,WebResourceRequest request,WebResourceResponse response){if(!request.isForMainFrame())settleReaderImages(web,request.getUrl().toString());}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req) {
                    String url=req.getUrl().toString();
                    if(req.isForMainFrame()&&url.split("#",2)[0].equals(readerPageUrl.split("#",2)[0]))return new WebResourceResponse("text/html","UTF-8",200,"OK",java.util.Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(readerHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    ReaderVideoRequests media=inlineVideoRequests;
                    if(!req.isForMainFrame()&&media!=null&&media.owns(url))try{return media.response(url,req.getRequestHeaders());}catch(Exception error){return new WebResourceResponse("text/plain","utf-8",404,"Unavailable",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
                    Document active=reading;if(active==null||!active.containsImage(url))return new WebResourceResponse("text/plain","utf-8",new ByteArrayInputStream(new byte[0]));
                    try { return imageResponse(imageRequests,url,doc.sourceUrl()); }
                    catch(Exception e) { return new WebResourceResponse("text/plain","utf-8",404,"Unavailable",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0])); }
                }
            });
            root.addView(readerWeb,new LinearLayout.LayoutParams(-1,0,1)); readerFooter();
            readerWeb.setOnLongClickListener(v->{WebView.HitTestResult hit=readerWeb.getHitTestResult();String url=hit==null?null:hit.getExtra();if(url==null||!ReaderLinks.allowed(url)||reading.containsImage(url)||url.startsWith(ReaderHtml.ACTION))return false;
                new AlertDialog.Builder(this).setTitle("链接地址").setMessage(url).setNegativeButton("取消",null).setNeutralButton("复制链接",(d,n)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("链接",url));toast("已复制链接");}).setPositiveButton("打开",(d,n)->openContentLink(url)).show();return true;});
            reloadReader(doc);
            readerWeb.setOnScrollChangeListener((v,x,y,ox,oy)->{
                if(y!=oy)scheduleViewportSample();
                if(y!=oy)readerPrefetch.settled();
                if(y!=oy&&System.currentTimeMillis()>suppressAutoUntil)engagement.scroll(android.os.SystemClock.elapsedRealtime());
                if(y>oy)maybeLoadMore(readerWeb,true);});
            return;
        }
        reading=doc; content.removeAllViews(); scroll.scrollTo(0,0);
        TextView title=text(doc.title,26,INK); title.setTypeface(null,Typeface.BOLD); title.setLineSpacing(dp(5),1); content.addView(title); space(content,14);
        if(!doc.byline.isEmpty()){content.addView(text(doc.byline,12,MUTED));space(content,12);}
        if(!doc.notice.trim().isEmpty()) { TextView warning=text(doc.notice,13,dark?0xffe2c995:0xff856234); warning.setPadding(dp(14),dp(12),dp(14),dp(12)); warning.setBackground(shape(dark?0xff342e22:0xfff5eddf,12)); content.addView(warning); space(content,16); }
        if(!doc.hasContent()&&!doc.related.isEmpty()) {
            content.addView(text("选择一篇内容继续阅读",15,MUTED)); space(content,16);
            for(Item item:repo.visibleItems(doc.related)) relatedLink(item);
        }
        if(doc.sourceUnavailable)content.addView(button("重试",()->open(current,false)));
        if(doc.filteredVideos>0&&!doc.hasContent()&&doc.related.isEmpty())content.addView(button("返回图文列表",this::goBack));
        if(doc.sourceUnavailable||doc.filteredVideos==0||doc.hasContent()||!doc.related.isEmpty())content.addView(button("来源页 / 登录后重新读取",()->login(current)));
    }
    private void readerReady(WebView web,String url){
        if(web!=readerWeb||!url.equals(readerPageUrl))return;
        engagement.ready(reading!=null&&reading.hasContent()&&!reading.loginRequired&&!reading.sourceUnavailable&&!reading.unsupportedVideo,android.os.SystemClock.elapsedRealtime());
        readerPrefetch.ready();
    }
    private static WebResourceResponse imageResponse(ReaderImageOwner owner,String url,String referer)throws Exception {
        byte[] data=owner.load(url,referer);
        return imageResponse(data);
    }
    private static WebResourceResponse imageResponse(byte[] data)throws Exception {
        String mime=ImageType.detect(data);
        if(mime==null)throw new java.io.IOException("Not a supported image");
        return new WebResourceResponse(mime,null,new ByteArrayInputStream(data));
    }
    private void openContentLink(String url){
        String target=ReaderLinks.readerTarget(url);
        Source source=target.isEmpty()?null:ReaderLinks.readerSource(target);if(source!=null){
            open(new Item(source,"链接内容",target,"正文链接"),true);return;
        }
        try {startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),"打开链接"));}
        catch(android.content.ActivityNotFoundException e){toast("没有可打开此链接的浏览器");}
    }
    private void closeVideo(){
        if(inlineVideoMonitor!=null){preloadHandler.removeCallbacks(inlineVideoMonitor);inlineVideoMonitor=null;}
        hideInlineFullscreen();
        if(videoPreview!=null){videoPreview.dismiss();videoPreview=null;}
        ReaderVideoRequests requests=inlineVideoRequests;inlineVideoRequests=null;if(requests!=null)requests.close();
        if(requests!=null&&readerWeb!=null){readerScript(readerWeb,"(()=>{for(const v of document.querySelectorAll('.inline-media video')){v.pause();v.removeAttribute('src');v.controls=false;v.style.display='none';v.load();const f=v.closest('.inline-media'),a=f.querySelector('.media-play'),p=f.querySelector('.media-cover');f.querySelector('.media-stage').style.cssText='';if(p)p.style.display='block';a.style.cssText='';a.textContent='▶ 播放视频'}})()",ignored->{});}
    }
    private void hideInlineFullscreen(){
        Runnable unregister=inlineFullscreenBack;inlineFullscreenBack=null;if(unregister!=null)unregister.run();
        android.app.Dialog dialog=inlineFullscreen;inlineFullscreen=null;
        WebChromeClient.CustomViewCallback callback=inlineFullscreenCallback;inlineFullscreenCallback=null;
        if(dialog!=null)dialog.dismiss();if(callback!=null)callback.onCustomViewHidden();
    }
    @androidx.annotation.RequiresApi(33)
    private static final class FullscreenBack {
        static Runnable register(android.app.Dialog dialog,Runnable action){android.window.OnBackInvokedCallback callback=action::run;dialog.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_OVERLAY,callback);return ()->dialog.getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(callback);}
    }
    private void playInlineVideo(int index,String anchor){
        if(readerWeb==null||reading==null||current==null||!readingResumed||!anchor.matches("reading-[A-Za-z0-9_-]+-[0-9]+"))return;
        List<VideoAttachment> videos=ReaderMedia.attachments(reading);if(index<0||index>=videos.size())return;
        VideoAttachment video=videos.get(index);if(!ReaderMedia.allowed(reading.url,video.url))return;
        boolean present=false;for(Section section:ReaderHtml.sections(reading))for(int n=0;n<section.blocks.size();n++){
            Block b=section.blocks.get(n);if(b.type.equals("video")&&b.value.equals(ReaderMedia.key(video))&&ReaderHtml.anchorId(section.id,n).equals(anchor))present=true;
        }
        if(!present)return;
        readerPrefetch.defer();closeVideo();inlineVideoRequests=videoRequestFactory.apply(reading.url,video);
        String route=inlineVideoRequests.route();readerWeb.getSettings().setMediaPlaybackRequiresUserGesture(false);
        readerScript(readerWeb,"(()=>{const f=document.getElementById("+org.json.JSONObject.quote(anchor)+");const v=f&&f.querySelector('video');if(!v)return false;f.querySelector('.media-stage').style.cssText='';const p=f.querySelector('.media-cover');if(p)p.style.display='none';v.style.display='block';v.controls=true;f.querySelector('.media-play').style.display='none';f.querySelector('.media-status').textContent='';f.dataset.playResult='pending';v.src="+org.json.JSONObject.quote(route)+";v.load();v.play().then(()=>f.dataset.playResult='playing',error=>f.dataset.playResult=error.name);return true})()",ignored->{});
        ReaderVideoRequests owner=inlineVideoRequests;WebView web=readerWeb;long began=android.os.SystemClock.elapsedRealtime();boolean[] prepared={false};
        inlineVideoMonitor=new Runnable(){public void run(){
            if(inlineVideoRequests!=owner||readerWeb!=web||!readingResumed)return;
            readerScript(web,"(()=>{const f=document.getElementById("+org.json.JSONObject.quote(anchor)+");const v=f&&f.querySelector('video');return !v?'missing':v.error||(f.dataset.playResult&&!['pending','playing'].includes(f.dataset.playResult))?'error':v.ended?'ended':v.readyState>=2?'ready':'waiting'})()",result->{
                if(inlineVideoRequests!=owner||readerWeb!=web)return;
                if("\"ready\"".equals(result))prepared[0]=true;
                if("\"error\"".equals(result)||(!prepared[0]&&android.os.SystemClock.elapsedRealtime()-began>=20000)){
                    closeVideo();readerScript(web,"(()=>{const f=document.getElementById("+org.json.JSONObject.quote(anchor)+");if(!f)return;f.querySelector('video').style.display='none';f.querySelector('.media-stage').style.cssText='aspect-ratio:auto;background:transparent';const p=f.querySelector('.media-cover');if(p)p.style.display='none';const a=f.querySelector('.media-play');a.style.cssText='position:static;background:transparent;color:var(--green);justify-content:flex-start;font-size:13px';a.textContent='重试视频';f.querySelector('.media-status').textContent='视频暂时无法播放，可重试或在来源页查看。'})()",ignored->{});return;
                }
                if("\"ended\"".equals(result)||"\"missing\"".equals(result)){closeVideo();return;}
                preloadHandler.postDelayed(this,1000);
            });
        }};preloadHandler.postDelayed(inlineVideoMonitor,1000);
    }
    private void playVideo(){
        playVideo(0);
    }
    private void playVideo(int index){
        if(reading==null||current==null)return;
        List<VideoAttachment> attachments=ReaderMedia.attachments(reading);if(index<0||index>=attachments.size())return;
        VideoAttachment selected=attachments.get(index);if(!ReaderMedia.allowed(reading.url,selected.url))return;
        readerPrefetch.defer();closeVideo();rememberReading();Document media=AnswerStream.copy(reading);media.videoUrl=selected.url;media.videoPoster=selected.poster;Item item=current;
        videoPreview=new VideoPreview(this,media,dark,()->login(item));videoPreview.show();
    }
    private void zoom(String url,String referer) {
        if(imagePreview!=null)imagePreview.dismiss();
        // Keep list/reader thumbnails light; request the observed larger rendition only on tap.
        String previewUrl=current==null?url:WeiboImage.preview(current.source,url);
        List<String> gallery=new ArrayList<>();
        if(reading!=null)for(Block block:reading.blocks)if(block.type.equals("image")&&UrlPolicy.https(block.value)){
            String large=current==null?block.value:WeiboImage.preview(current.source,block.value);if(!gallery.contains(large))gallery.add(large);
        }
        if(!gallery.contains(previewUrl))gallery.add(previewUrl);
        ReaderImageOwner galleryOwner=new ReaderImageOwner(this,current,images,reading);
        imagePreview=new ImagePreview(this,gallery,gallery.indexOf(previewUrl),dark,u->imageResponse(galleryOwner,u,referer),u->images.remove(u),galleryOwner::close,()->{galleryOwner.resume();ReaderImages.Scope page=galleryOwner.snapshot();return u->imageResponse(page.load(u,referer));});imagePreview.show();
    }
    private void relatedLink(Item item) {
        TextView link=button(item.title,()->open(item,true));
        link.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        link.setTextColor(INK);link.setLineSpacing(dp(3),1);
        LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(-1,-2);size.bottomMargin=dp(8);
        content.addView(link,size);
    }
    private void login(Item item) {login(item,false);}
    private void login(Item item,boolean topicPage) {
        if(item==null||!item.source.readable())return;
        // A pre-login read must not finish late and restore an anonymous snapshot.
        generation++;preloader.pause();repo.cancelPending();closeAnswers();
        if(imageOwner!=null)imageOwner.close();
        if(dynamic!=null){dynamic.close();dynamic=null;}
        LoginActivity.pendingDocument=null;
        String target=!topicPage&&reading!=null&&reading.topic!=null&&reading.url.equals(item.url)?reading.sourceUrl():item.url;
        Intent i=new Intent(this,LoginActivity.class); i.putExtra("source",item.source.name()); i.putExtra("url",target);i.putExtra("reader_item",item.json().toString());i.putExtra("browse",!item.source.unifiedReading());i.putExtra("reader_import",current!=null); startActivityForResult(i,100);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==101){readerPrefetch.invalidate();getIntent().removeExtra("reader_prefetch_plan");recreate();return;}
        Document imported=null;Source importedSource=null;
        if(request==100&&result==RESULT_OK&&data!=null&&LoginActivity.pendingDocument!=null){
            try{Source source=Source.valueOf(data.getStringExtra("source"));Document doc=LoginActivity.pendingDocument;if(source.readable()&&UrlPolicy.belongs(source,doc.url)){imported=doc;importedSource=source;}}catch(Exception ignored){}
        }
        if(request==100){
            wasStopped=false; // The result owns this refresh; onResume must not start a duplicate.
            returningAnswers=current==null?null:reading==null?new HashSet<>():AnswerStream.answerIds(reading);
            forceSourceRead=current!=null;
            CookieManager.getInstance().flush();preloader.pause();if(!repo.importSourceRead(imported))imported=null;closeAnswers();
            // Re-resolve the original topic with the new account; a separately
            // extracted post must not replace the original recommendation identity.
            if(current!=null&&TiebaTopic.isTopic(current.url))imported=null;
            LoginActivity.pendingDocument=null;
        }
        if(request==100&&imported!=null) {
            Document doc=imported;
            getIntent().putExtra("imported_source_url",doc.url);
            Source source=importedSource;
            rememberReading();expanded.clear();openPosition=-1;pendingAnchor=-1;
            engagement.reset(android.os.SystemClock.elapsedRealtime());
            if(current==null||current.source!=source||!ArticleVersion.matches(current,doc))current=new Item(source,doc.title,doc.url,"");
            readerPrefetch.opening(current);
            forceSourceRead=false;
            generation++;primaryPending=true; frame(); scroller(); readerFooter(); render(doc);
        } else if(request==100){
            LoginActivity.pendingDocument=null;
            // Back/close returns RESULT_CANCELED even after a successful website login.
            // Always re-read with the shared WebView session, without assuming login succeeded.
            if(current!=null)open(current,false);
            else finish();
        }
    }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }
    private void goBack() { recordQuickExit();if(!history.isEmpty())open(history.pop(),false);else{rememberReading();finish();} }
    private void systemBack(){goBack();}
    @Override public void onBackPressed(){systemBack();}
    @Override protected void onSaveInstanceState(Bundle out) {
        if(previousTopicPost!=null)out.putString("previousTopicPost",previousTopicPost.json().toString());
        if(current!=null)out.putString("current",current.json().toString());
        out.putString("history",Models.toJson(new ArrayList<>(history)).toString());
        super.onSaveInstanceState(out);
    }
    private void theme(){new AlertDialog.Builder(this).setTitle("外观").setSingleChoiceItems(new String[]{"跟随系统","浅色","深色"},Theme.mode(this),(d,n)->{getSharedPreferences("appearance",0).edit().putInt("mode",n).apply();d.dismiss();recreate();}).show();}
    private void settings(){startActivityForResult(new Intent(this,SettingsActivity.class),101);}
    private void sectionMenu(){
        List<Section> parts=ReaderHtml.sections(reading);String[] labels=new String[parts.size()];
        for(int n=0;n<parts.size();n++){Section part=parts.get(n);String preview="";for(Block block:part.blocks)if(block.type.equals("text")){preview=block.value.replace('\n',' ');break;}labels[n]=(n+1)+" · "+part.label+"  "+preview.substring(0,Math.min(32,preview.length()));}
        new AlertDialog.Builder(this).setTitle("跳转到回答 / 楼层").setItems(labels,(d,n)->jumpTo(n)).show();
    }
    private void jumpTo(int index){if(reading==null)return;List<Section> parts=ReaderHtml.sections(reading);if(index<0||index>=parts.size())return;expanded.add(parts.get(index).id);streamAnchor="";openPosition=-1;pendingAnchor=index;reloadReader(reading);}
    private void changeFont(int next){
        int old=font;font=next;getPreferences(0).edit().putInt("font",font).apply();
        if(readerWeb!=null){
            suppressAutoUntil=System.currentTimeMillis()+1500;
            if(reading!=null&&reading.blocks.stream().anyMatch(b->!b.inlineImages.isEmpty())){
                atReadingPosition(()->reloadReader(reading),()->{});
            }else{sampledViewport=null;sampledViewportPage="";readerWeb.getSettings().setTextZoom(Math.round(readerWeb.getSettings().getTextZoom()*next/(float)old));scheduleViewportSample();}
        }
    }
    private void reloadReader(Document doc){
        cancelDeferredContinuation();
        readerPrefetch.rendering();
        closeVideo();
        suppressAutoUntil=System.currentTimeMillis()+1500;
        sampledViewport=null;sampledViewportPage="";viewportRestoreRunning=false;preloadHandler.removeCallbacks(sampleViewport);
        if(readerWeb!=null){readerWeb.getSettings().setTextZoom(Math.round(font*100f/19));readerHtml=ReaderHtml.render(doc,current.source,19,dark,expanded,loadingMore,font,Theme.style(this));readerPageUrl=ReaderHtml.ACTION+"?render="+System.nanoTime()+"-"+(++readerRevision)+(!streamAnchor.isEmpty()?"#"+streamAnchor:pendingAnchor>=0?"#part-"+pendingAnchor:"");pendingAnchor=-1;readerWeb.loadUrl(readerPageUrl);}
    }
    private void settleReaderImages(WebView web,String failedUrl){
        if(web!=readerWeb||reading==null||wasStopped||isFinishing()||isDestroyed()||failedUrl!=null&&!reading.containsImage(failedUrl))return;
        String page=readerPageUrl;int revision=readerRevision;
        readerScript(web,ReaderImageState.settle(page,failedUrl),value->{if(web==readerWeb&&revision==readerRevision&&page.equals(readerPageUrl)&&!wasStopped&&!"0".equals(value)&&!"null".equals(value))scheduleViewportSample();});
    }
    /** Fixed native expressions on our escaped CSP-protected document; no remote scripts or bridge. */
    private void readerScript(WebView web,String script,java.util.function.Consumer<String> callback){
        readerScripts.put(web,readerScripts.getOrDefault(web,0)+1);web.getSettings().setJavaScriptEnabled(true);
        java.util.concurrent.atomic.AtomicBoolean completed=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> finish=value->{
            if(!completed.compareAndSet(false,true))return;
            int remaining=readerScripts.getOrDefault(web,1)-1;
            if(remaining<=0){readerScripts.remove(web);if(web==readerWeb)web.getSettings().setJavaScriptEnabled(inlineVideoRequests!=null);}else readerScripts.put(web,remaining);
            callback.accept(value);
        };
        web.evaluateJavascript(script,finish::accept);preloadHandler.postDelayed(()->finish.accept("null"),500);
    }
    private void scheduleViewportSample(){preloadHandler.removeCallbacks(sampleViewport);if(!wasStopped)preloadHandler.postDelayed(sampleViewport,150);}
    private void reloadInterruptedImages(){
        WebView web=readerWeb;if(web==null||reading==null)return;
        int request=generation,revision=readerRevision,position=web.getScrollY();String page=readerPageUrl;
        readerScript(web,"(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.height>0&&r.bottom>0&&r.top<innerHeight)return {id:a.id,fraction:Math.min(1,Math.max(0,-r.top/r.height)),gap:Math.max(0,r.top)}}return null})()",value->{
            if(web!=readerWeb||request!=generation||revision!=readerRevision||!page.equals(readerPageUrl)||wasStopped)return;
            pendingViewport=position>dp(2)?ReadingViewport.parse(value):null;pendingViewportFallback=position;streamAnchor="";openPosition=pendingViewport==null?position:-1;
            reloadReader(reading);
        });
    }
    private void captureViewport(){
        WebView web=readerWeb;if(web==null||pendingViewport!=null||wasStopped||!readerPageUrl.equals(web.getUrl())||!readerPageUrl.startsWith(ReaderHtml.ACTION+"?render="))return;
        int revision=readerRevision;String page=readerPageUrl;
        // One debounced geometry read after scrolling stops, never text capture or polling.
        readerScript(web,"(()=>{for(const a of document.querySelectorAll('[data-reading-anchor]')){const r=a.getBoundingClientRect();if(r.height>0&&r.bottom>0&&r.top<innerHeight)return {id:a.id,fraction:Math.min(1,Math.max(0,-r.top/r.height)),gap:Math.max(0,r.top),scroll:scrollY}}return null})()",value->{
            if(web!=readerWeb||revision!=readerRevision||!page.equals(readerPageUrl)||wasStopped)return;
            ReadingViewport anchor=ReadingViewport.parse(value);if(anchor==null)return;
            try{double sampledY=new org.json.JSONObject(value).getDouble("scroll")*web.getScale();if(Math.abs(sampledY-web.getScrollY())>dp(2))return;}catch(Exception invalid){return;}
            sampledViewport=anchor;sampledViewportPage=page;sampledViewportY=web.getScrollY();
        });
    }
    private void restoreViewport(WebView web,int revision){
        ReadingViewport anchor=pendingViewport;if(anchor==null||viewportRestoreRunning||web!=readerWeb||revision!=readerRevision)return;
        viewportRestoreRunning=true;int fallback=pendingViewportFallback;
        suppressAutoUntil=System.currentTimeMillis()+1500;
        web.postVisualStateCallback(revision,new WebView.VisualStateCallback(){@Override public void onComplete(long id){
            if(web!=readerWeb||revision!=readerRevision)return;
            readerScript(web,"(()=>{const a=document.getElementById("+org.json.JSONObject.quote(anchor.id)+");if(!a)return null;let r=a.getBoundingClientRect();return {top:r.top,height:r.height}})()",value->{
                if(web!=readerWeb||revision!=readerRevision||pendingViewport!=anchor)return;
                int target=fallback;try{org.json.JSONObject rect=new org.json.JSONObject(value);target=anchor.destination(web.getScrollY(),rect.getDouble("top"),rect.getDouble("height"),web.getScale(),fallback);}catch(Exception invalid){}
                pendingViewport=null;viewportRestoreRunning=false;restoreWebPosition(web,target);scheduleViewportSample();
            });
        }});
    }
    private void showContinuationLoading(){
        WebView web=readerWeb;if(web==null||!readerPageUrl.equals(web.getUrl()))return;
        String label=current!=null&&current.source==Source.ZHIHU?"正在加载下一批回答…":"正在加载后续回复…";
        // Update this one action in place. Reloading a spinner document first can
        // race a fast source response and discard the original paragraph anchor.
        readerScript(web,"(()=>{const a=document.getElementById('reader-continuation');if(a){a.textContent="+org.json.JSONObject.quote(label)+";a.setAttribute('role','status');a.dataset.continuation='loading';a.removeAttribute('aria-hidden');a.querySelectorAll('a').forEach(link=>link.removeAttribute('href'))}})()",ignored->{});
    }
    private void restoreReadingAnchor(WebView web,String anchor,int offset,int revision){
        if(web!=readerWeb||revision!=readerRevision||!anchor.matches("reading-[A-Za-z0-9_-]+"))return;
        // A fragment near the bottom can be clamped before reaching the top.
        // Measure its actual location, rather than assuming it is already at y=0.
        java.util.concurrent.atomic.AtomicBoolean applied=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> finish=value->{
            if(!applied.compareAndSet(false,true)||web!=readerWeb||revision!=readerRevision)return;
            try{Object top=new org.json.JSONTokener(value).nextValue();if(top instanceof Number)restoreWebPosition(web,web.getScrollY()+Math.round(((Number)top).floatValue()*web.getScale())+offset);}catch(Exception ignored){}
        };
        readerScript(web,"(()=>{const a=document.getElementById("+org.json.JSONObject.quote(anchor)+");return a?a.getBoundingClientRect().top:null})()",finish);
    }
    private void appendAtReadingPosition(Document doc,int request,Set<String> previousAnswerIds){
        appendContentAtReadingPosition(doc,request,!AnswerStream.hasNewAnswers(doc,previousAnswerIds)||!doc.moreStatus.equals("loaded"));
    }
    private void appendContentAtReadingPosition(Document doc,int request,boolean paused){
        if(request!=generation)return;
        Runnable commit=()->{
            loadingMore=false;morePaused=paused;reading=doc;repo.cacheArticle(doc);
            ReadingLanguageRecorder.record(this,getIntent().getStringExtra("test_database"),discoveryCandidate,doc);
            reloadReader(doc);
        };
        atReadingPosition(commit,()->{int pos=readerWeb==null?0:readerWeb.getScrollY();streamAnchor="";commit.run();restorePosition(pos);});
    }
    private void atReadingPosition(Runnable apply,Runnable stale){
        WebView web=readerWeb;if(web==null)return;
        int request=generation,revision=readerRevision,pos=web.getScrollY();String page=readerPageUrl;
        // At the page top, preserve the title rather than the first body paragraph.
        if(pos<=dp(2)){
            streamAnchor="";pendingViewport=null;viewportRestoreRunning=false;
            openPosition=0;apply.run();return;
        }
        java.util.concurrent.atomic.AtomicBoolean applied=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> finish=value->{
            if(!applied.compareAndSet(false,true)||web!=readerWeb)return;
            if(request!=generation)return;
            if(revision!=readerRevision){stale.run();return;}
            streamAnchor="";
            try{org.json.JSONObject anchor=new org.json.JSONObject(value);String id=anchor.getString("id");if(id.matches("reading-[A-Za-z0-9_-]+")){streamAnchor=id;streamAnchorOffset=Math.round((float)-anchor.getDouble("top")*web.getScale());}}catch(Exception ignored){}
            boolean anchored=!streamAnchor.isEmpty();apply.run();if(!anchored)restorePosition(pos);
        };
        if(!page.equals(web.getUrl())||!page.startsWith(ReaderHtml.ACTION+"?render=")){finish.accept("");return;}
        // evaluateJavascript needs this switch; CSP default-src 'none' still forbids document scripts.
        // The fixed expression reads positions only, and scripting is switched off before any reload.
        readerScript(web,"(function(){var a=document.querySelectorAll('[data-reading-anchor]');for(var i=0;i<a.length;i++){var r=a[i].getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)return {id:a[i].id,top:r.top};}return null;})()",finish);
    }
    private void rememberReading(){if(current!=null&&reading!=null&&readerWeb!=null&&reading.hasContent()){ReadingMark mark=new ReadingMark();mark.expanded=new HashSet<>(expanded);mark.position=readerWeb.getScrollY();if(mark.position>dp(2)&&sampledViewport!=null&&sampledViewportPage.equals(readerPageUrl)&&Math.abs(mark.position-sampledViewportY)<=dp(2))mark.viewport=SavedReadingViewport.capture(reading,sampledViewport);marks.put(current.url,mark);readingHistory.save(current.url,reading,expanded,mark.position,mark.viewport);}}
    private void closeAnswers(){if(answers!=null){answers.close();answers=null;}if(discussions!=null){discussions.close();discussions=null;}if(tiebaReplies!=null){tiebaReplies.close();tiebaReplies=null;}discussionInterrupted=false;loadingMore=false;morePaused=false;}
    private boolean continuationDue(){return readerWeb!=null&&current!=null&&reading!=null&&!morePaused&&ReaderContinuation.automatic(current.source,reading)&&readerWeb.getHeight()>0&&readerWeb.getContentHeight()*readerWeb.getScale()-readerWeb.getScrollY()-readerWeb.getHeight()<readerWeb.getHeight()*.75f;}
    private void cancelDeferredContinuation(){if(deferredContinuation!=null){preloadHandler.removeCallbacks(deferredContinuation);deferredContinuation=null;}}
    /** Preserve one real forward gesture swallowed by the bounded restoration guard. */
    private void finishContinuationGesture(android.view.MotionEvent event){
        WebView web=continuationGestureWeb;continuationGestureWeb=null;
        long delay=suppressAutoUntil-System.currentTimeMillis();
        if(web==null||web!=readerWeb||continuationGestureRevision!=readerRevision||continuationTouchY-event.getRawY()<=android.view.ViewConfiguration.get(this).getScaledTouchSlop()||web.getScrollY()<=continuationGestureY||delay<0||delay>1500)return;
        int revision=readerRevision,request=generation;
        cancelDeferredContinuation();
        deferredContinuation=()->{deferredContinuation=null;if(web==readerWeb&&revision==readerRevision&&request==generation&&readingResumed&&hasWindowFocus())maybeLoadMore(web,true);};
        preloadHandler.postDelayed(deferredContinuation,delay+30);
    }
    private void maybeLoadMore(WebView web,boolean scrolled){
        if(web==null||web!=readerWeb||current==null||reading==null||!readingVisible()||loadingMore||morePaused||pendingViewport!=null||viewportRestoreRunning||System.currentTimeMillis()<=suppressAutoUntil||web.getHeight()<=0||!ReaderContinuation.automatic(current.source,reading))return;
        float height=web.getContentHeight()*web.getScale();
        if(scrolled?height-web.getScrollY()-web.getHeight()<web.getHeight()*.75f:height<=web.getHeight()+dp(32)&&web.getScrollY()<=dp(2))loadMore();
    }
    private boolean readingVisible(){return readingResumed&&hasWindowFocus()&&!wasStopped&&!isFinishing()&&!isDestroyed();}
    private void loadMore(){
        if(!readingVisible()||loadingMore||reading==null||current==null)return;
        if(TiebaReplies.supports(current.source,reading)){
            if(Set.of("limit","end","login").contains(reading.moreStatus))return;
            if(repo.offline()){continuationFailure("当前没有网络，已读回复保留，联网后可重试。");return;}
            readerPrefetch.defer();loadingMore=true;int request=generation;Document before=reading;showContinuationLoading();
            if(tiebaReplies==null)tiebaReplies=new TiebaReplies(this,current,null);
            tiebaReplies.more(before,new Repository.Result<>(){
                public void success(Document doc){if(request!=generation)return;appendContentAtReadingPosition(doc,request,!doc.moreStatus.equals("loaded"));}
                public void failure(String why){if(request!=generation)return;continuationFailure(why);}
            });return;
        }
        if(DiscussionPages.usesStream(current.source,reading)){
            if(!DiscussionPages.next(current.source,reading)||reading.moreStatus.equals("limit"))return;
            if(repo.offline()){continuationFailure("当前没有网络，已读回复保留，联网后可重试。");return;}
            readerPrefetch.defer();loadingMore=true;int request=generation;Document before=reading;
            showContinuationLoading();
            if(discussions==null)discussions=new DiscussionStream(this,current);
            discussions.more(before,new Repository.Result<>(){
                public void success(Document doc){if(request!=generation)return;appendContentAtReadingPosition(doc,request,!doc.moreStatus.equals("loaded"));}
                public void failure(String why){if(request!=generation)return;continuationFailure("回复加载失败，已读内容保留。"+why);}
            });return;
        }
        if(current.source!=Source.ZHIHU||reading.moreStatus.equals("limit"))return;
        if(repo.offline()){continuationFailure("当前没有网络，联网后可在这里重试。");return;}
        readerPrefetch.defer();loadingMore=true;int request=generation;Document before=reading;Set<String> previousAnswerIds=AnswerStream.answerIds(before);
        showContinuationLoading();
        if(answers==null)answers=new AnswerStream(this,current);
        answers.more(before,new Repository.Result<Document>(){
            public void success(Document doc){if(request!=generation)return;appendAtReadingPosition(doc,request,previousAnswerIds);}
            public void failure(String why){if(request!=generation)return;continuationFailure("续读失败，已读回答保留。"+why);}
        });
    }
    private void continuationFailure(String reason){
        if(reading==null)return;loadingMore=false;morePaused=true;reading.moreStatus="error";reading.notice=reason;
        atReadingPosition(()->reloadReader(reading),()->{});
    }
    private void recordReadingInterest(){if(discoveryCandidate!=null&&current!=null&&reading!=null&&ArticleSession.contentCurrent(reading)&&current.url.equals(discoveryCandidate.originalUrl)&&ArticleVersion.matches(discoveryCandidate.readerItem(),reading)&&engagement.take(android.os.SystemClock.elapsedRealtime()))ReadingInterestRecorder.record(this,getIntent().getStringExtra("test_database"),discoveryCandidate,reading);}
    private void recordQuickExit(){
        if(discoveryCandidate==null||current==null||reading==null||!ArticleSession.contentCurrent(reading)||!current.url.equals(discoveryCandidate.originalUrl)||!ArticleVersion.matches(discoveryCandidate.readerItem(),reading)||reading.loginRequired||reading.sourceUnavailable||reading.unsupportedVideo)return;
        long text=ReaderHtml.sections(reading).stream().filter(s->!ReaderHtml.discussion(s)||expanded.contains(s.id)).flatMap(s->s.blocks.stream()).filter(b->b.type.equals("text")).mapToLong(b->b.value.codePointCount(0,b.value.length())).sum();
        if(engagement.takeQuickExit(android.os.SystemClock.elapsedRealtime(),text>=300))ReadingInterestRecorder.quickExit(this,getIntent().getStringExtra("test_database"),discoveryCandidate,reading);
    }
    private void interruptViewportRestore(){if(pendingViewport!=null){pendingViewport=null;viewportRestoreRunning=false;scheduleViewportSample();}}
    // A back key release can arrive after opening the previous article. Only a new
    // navigation gesture should cancel that article's pending position restoration.
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){int action=event.getActionMasked();if(action==android.view.MotionEvent.ACTION_DOWN){cancelDeferredContinuation();interruptViewportRestore();continuationGestureWeb=readerWeb;continuationTouchY=event.getRawY();continuationGestureY=readerWeb==null?0:readerWeb.getScrollY();continuationGestureRevision=readerRevision;if(readerPrefetch!=null)readerPrefetch.defer();}boolean handled=super.dispatchTouchEvent(event);if(action==android.view.MotionEvent.ACTION_UP)finishContinuationGesture(event);else if(action==android.view.MotionEvent.ACTION_CANCEL)continuationGestureWeb=null;if(readerPrefetch!=null){if(action==android.view.MotionEvent.ACTION_UP)readerPrefetch.action();else if(action==android.view.MotionEvent.ACTION_CANCEL)readerPrefetch.settled();}return handled;}
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){if(event.getAction()==android.view.KeyEvent.ACTION_DOWN&&event.getKeyCode()!=android.view.KeyEvent.KEYCODE_BACK)interruptViewportRestore();return super.dispatchKeyEvent(event);}
    @Override public boolean dispatchGenericMotionEvent(android.view.MotionEvent event){if(event.getActionMasked()==android.view.MotionEvent.ACTION_SCROLL){interruptViewportRestore();if(readerPrefetch!=null){readerPrefetch.defer();readerPrefetch.action();}}return super.dispatchGenericMotionEvent(event);}
    @Override public void onUserInteraction(){super.onUserInteraction();engagement.touch(android.os.SystemClock.elapsedRealtime());}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);engagement.visible(readingResumed&&focused,android.os.SystemClock.elapsedRealtime());if(readerPrefetch!=null){if(focused)readerPrefetch.settled();else readerPrefetch.defer();}}
    @Override protected void onPause(){cancelDeferredContinuation();continuationGestureWeb=null;if(readerPrefetch!=null)readerPrefetch.pause();closeVideo();readingResumed=false;engagement.visible(false,android.os.SystemClock.elapsedRealtime());recordReadingInterest();rememberReading();super.onPause();}
    @Override protected void onStop(){
        dismissReaderActions();wasStopped=true;
        if(answers!=null){discussionInterrupted=answers.busy();answers.close();answers=null;if(discussionInterrupted)loadingMore=false;}
        if(tiebaReplies!=null){discussionInterrupted=tiebaReplies.busy();tiebaReplies.close();tiebaReplies=null;if(discussionInterrupted)loadingMore=false;}
        if(!isChangingConfigurations()&&imageOwner!=null)imageOwner.close();
        repo.cancelPending();
        if(pendingArticle!=null){if(isChangingConfigurations())pendingArticle.detach();else cancelPendingArticle();}
        if(reading==null&&dynamic!=null){if(isChangingConfigurations())dynamic.detach();else{dynamic.close();dynamic=null;}}
        // The reader snapshot/position survives; a hidden large-image WebView need not.
        if(imagePreview!=null){imagePreview.dismiss();imagePreview=null;}
        preloadHandler.removeCallbacks(sampleViewport);if(preloader!=null){if(isChangingConfigurations()&&reading==null&&preloader.canRetainForeground(current))preloader.detachForeground();else preloader.pause();}if(discussions!=null){discussionInterrupted=discussions.busy();discussions.close();discussions=null;if(discussionInterrupted)loadingMore=false;}super.onStop();
    }
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);app.quietreader.ai.RecommendationPolicy.memoryPressure(level);if(level>=TRIM_MEMORY_RUNNING_LOW){if(readerPrefetch!=null)readerPrefetch.trim();images.evictAll();if(repo!=null)repo.releaseArticleMemory();if(preloader!=null)preloader.trim();}}
    @Override protected void onResume(){super.onResume();readingResumed=true;if(readerPrefetch!=null)readerPrefetch.resume();engagement.visible(hasWindowFocus(),android.os.SystemClock.elapsedRealtime());boolean resume=wasStopped;wasStopped=false;if(discussionInterrupted){discussionInterrupted=false;continuationFailure("加载已暂停，已读回复保留，可重试继续。");}if(current!=null&&resume&&reading==null&&dynamic==null)open(current,false);else if(resume&&reading!=null&&imageOwner!=null&&imageOwner.paused()){if(!imageOwner.matches(current)){open(current,false);return;}boolean interrupted=imageOwner.takeInterrupted();imageOwner.resume();if(interrupted&&readerWeb!=null)reloadInterruptedImages();}}
    @Override protected void onDestroy() { generation++;if(readerPrefetch!=null)readerPrefetch.close();if(imageOwner!=null){imageOwner.close();imageOwner=null;}cancelPendingArticle();preloadHandler.removeCallbacksAndMessages(null);if(preloader!=null)preloader.close();closeAnswers(); if(imagePreview!=null)imagePreview.dismiss();if(dynamic!=null)dynamic.close();if(readerWeb!=null){SourceSurface.release(readerWeb);readerWeb=null;}repo.close(); super.onDestroy(); }
}
