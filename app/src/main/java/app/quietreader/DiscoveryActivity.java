package app.quietreader;

import android.app.*;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import static app.quietreader.Models.*;

/** Single reading feed with a separate unified article reader. */
public final class DiscoveryActivity extends Activity implements DiscoveryController.Listener {
    private DiscoveryController controller;
    private RecommendationTrialUi trialEvaluation;
    private ListView list;
    private Card measuringCard;
    private FeedNavigationLayout navigation;
    private final FeedExposureTracker exposure=new FeedExposureTracker();
    private final Runnable reportExposure=this::sampleExposure;
    private final ViewTreeObserver.OnGlobalLayoutListener exposureLayout=this::sampleExposure;
    private Set<String> feedIdentities=Collections.emptySet();
    private boolean exposureForeground;
    private final Rows adapter=new Rows();
    private List<FeedStore.Row> feed=Collections.emptyList();
    private Map<Source,JSONObject> sources=new EnumMap<>(Source.class);
    private String page="feed",sourcesReturnPage="feed";
    private int feedPosition,feedOffset;
    private int ink,muted,bg,paper,line,accent,soft;
    private TextView brand,updates,settingsButton;
    private RefreshIndicatorDrawable refreshIndicator;
    private boolean refreshing;
    private ViewTreeObserver.OnPreDrawListener refreshDraw;
    private long refreshDrawEpoch;
    private AlertDialog setupDialog,recommendationDialog;
    private List<HotCandidate> setupCandidates;
    private Map<String,Boolean> setupChoices;
    private ScrollView setupScroll;
    private Bundle pendingSetup;
    private boolean loading,newest;
    private boolean updateAvailable;
    private RecommendationState recommendationState=RecommendationState.INITIAL;
    private Source loginReturn;
    private HotCandidate sourceReadCandidate;
    private String restoreAnchor;
    private int restorePosition,restoreOffset;
    private HotCandidate startupReadCandidate;
    private boolean startupReadWaiting;
    private boolean openingReader;
    private long startupReadEpoch;
    private boolean nativeFeedReady,startupForeground,readerDrawQueued;
    private ViewTreeObserver.OnPreDrawListener readerDraw;
    private final Runnable releaseReaderStartup=()->{readerDrawQueued=false;if(startupForeground&&!isFinishing()&&!isDestroyed())ReaderStartup.releaseDeferred();};
    @Override public void onCreate(Bundle state){
        Theme.apply(this);super.onCreate(state);BackNavigation.install(this,this::back);
        java.lang.ref.WeakReference<DiscoveryActivity> startupOwner=new java.lang.ref.WeakReference<>(this);
        ReaderStartup.defer(getApplicationContext(),()->{DiscoveryActivity owner=startupOwner.get();if(owner!=null&&!owner.isDestroyed()&&owner.controller!=null)owner.controller.readerReady();});
        boolean dark=Theme.dark(this);ink=Theme.ink(this);bg=Theme.background(this);accent=Theme.accent(this);
        muted=dark?0xffa5a9b3:0xff717680;paper=dark?0xff1b1d22:0xffffffff;line=dark?0xff2b2d33:0xffe9eaed;soft=dark?0xff1b2630:0xffedf4fa;
        if(state!=null){page=state.getString("page","feed");restoreAnchor=state.getString("feedAnchor");restorePosition=state.getInt("feedPosition");restoreOffset=state.getInt("feedOffset");try{String source=state.getString("loginReturn");if(source!=null)loginReturn=Source.valueOf(source);}catch(Exception ignored){}}
        if(state!=null)try{String candidate=state.getString("sourceReadCandidate");if(candidate!=null)sourceReadCandidate=HotCandidate.fromJson(new JSONObject(candidate));}catch(Exception ignored){}
        if(state!=null)sourcesReturnPage="settings".equals(state.getString("sourcesReturnPage"))?"settings":"feed";
        if(!"sources".equals(page)&&!settingsPage())page="feed";
        layout();
        if(state!=null)pendingSetup=state.getBundle("interestSetup");
        boolean testSkip=((getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0||BuildConfig.RANKING_MEASUREMENT_ENABLED&&("app.quietreader.flowprobe".equals(getPackageName())||"app.quietreader.discoveryprobe".equals(getPackageName())))&&getIntent().getBooleanExtra("test_skip_fetch",false);
        // Saved UI state must not disable foreground prefetch for the new owner.
        // The controller checks source freshness before issuing any refresh.
        controller=new DiscoveryController(this,this,!testSkip,null,state==null?null:state.getString("sourceFilter"));
        trialEvaluation=new RecommendationTrialUi(this,controller);trialEvaluation.restoreExport(state);
        if(state!=null&&state.getString("trialSession")!=null)trialEvaluation.resume(state.getString("trialSession"));
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void readerAfterNativeDraw(){
        if(!ReaderStartup.deferred()||!nativeFeedReady||!startupForeground||readerDrawQueued||list==null||isFinishing()||isDestroyed())return;
        readerDrawQueued=true;
        readerDraw=()->{if(readerDraw!=null){list.getViewTreeObserver().removeOnPreDrawListener(readerDraw);readerDraw=null;}list.postOnAnimation(releaseReaderStartup);return true;};
        list.getViewTreeObserver().addOnPreDrawListener(readerDraw);list.invalidate();
    }
    private TextView text(String value,int size,int color){TextView view=new ActionTextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setIncludeFontPadding(false);return view;}
    private LinearLayout column(){LinearLayout view=new LinearLayout(this);view.setOrientation(LinearLayout.VERTICAL);return view;}
    private GradientDrawable surface(int color){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),line);return shape;}
    private TextView button(String title,Runnable action){TextView view=text(title,14,accent);view.setGravity(Gravity.CENTER);view.setMinHeight(dp(48));view.setMinWidth(dp(48));view.setPadding(dp(8),dp(8),dp(8),dp(8));view.setContentDescription(title);view.setFocusable(true);view.setOnClickListener(v->action.run());return view;}
    private void layout(){
        LinearLayout root=column();root.setBackgroundColor(bg);root.setFitsSystemWindows(true);root.setPadding(dp(18),0,dp(18),0);
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(18)+insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),dp(18)+insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});setContentView(root);
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackgroundColor(bg);
        LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setBaselineAligned(false);
        brand=text("",20,ink);brand.setTypeface(null,Typeface.BOLD);brand.setSingleLine(true);brand.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(brand,new LinearLayout.LayoutParams(-1,-2));top.addView(heading,new LinearLayout.LayoutParams(0,dp(48),1));
        updates=button("",this::updateAndReturnToTop);refreshIndicator=new RefreshIndicatorDrawable(accent,getResources().getDisplayMetrics().density);refreshIndicator.setBounds(0,0,dp(20),dp(20));updates.setCompoundDrawables(refreshIndicator,null,null,null);top.addView(updates,new LinearLayout.LayoutParams(dp(48),dp(48)));
        settingsButton=button("",()->{if(!"feed".equals(page))back();else preferences();});settingsButton.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,dp(24));top.addView(settingsButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
        list=new ListView(this);list.setVerticalScrollBarEnabled(false);list.setDivider(null);list.setCacheColorHint(bg);list.setSelector(android.R.color.transparent);list.setClipToPadding(false);list.setPadding(0,dp(48),0,dp(12));list.setAdapter(adapter);
        navigation=new FeedNavigationLayout(this,list,top,()->controller!=null&&"feed".equals(page),this::refreshFeed);
        list.setOnScrollListener(new AbsListView.OnScrollListener(){public void onScrollStateChanged(AbsListView view,int state){navigation.scrollState(state);if(controller!=null)controller.scrollState(state);}public void onScroll(AbsListView view,int first,int count,int total){View child=list.getChildAt(0);navigation.scrolled(first,child==null?0:child.getTop());if("feed".equals(page)&&!feed.isEmpty()){feedPosition=first;feedOffset=child==null?0:child.getTop();if(controller!=null)controller.scrollViewport(first,count,feedOffset);}sampleExposure();}});
        list.getViewTreeObserver().addOnGlobalLayoutListener(exposureLayout);
        root.addView(navigation,new LinearLayout.LayoutParams(-1,0,1));
        pageHeading();
    }
    private void refreshFeed(){remember();controller.refreshRecommendations();if(list!=null)list.post(reportExposure);}
    private void pauseExposure(){if(list!=null)list.removeCallbacks(reportExposure);exposure.pause();}
    private void sampleExposure(){
        updateRefreshAnimation();
        if(list==null||controller==null||!exposureForeground||!hasWindowFocus()||!"feed".equals(page)||feed.isEmpty()||isFinishing()||isDestroyed()){pauseExposure();return;}
        list.removeCallbacks(reportExposure);android.graphics.Rect viewport=new android.graphics.Rect();
        Map<String,Double> fractions=new LinkedHashMap<>();
        if(list.getGlobalVisibleRect(viewport)){
            viewport.top=Math.max(viewport.top,navigation.toolbarBottom());
            for(int n=0;n<list.getChildCount();n++){View row=list.getChildAt(n);Object tag=row.getTag();if(!(tag instanceof String)||!feedIdentities.contains(tag)||row.getWidth()<=0||row.getHeight()<=0)continue;
                android.graphics.Rect visible=new android.graphics.Rect();if(row.getGlobalVisibleRect(visible)&&visible.intersect(viewport))fractions.put((String)tag,(double)visible.width()*visible.height()/((double)row.getWidth()*row.getHeight()));
            }
        }
        long now=android.os.SystemClock.elapsedRealtime();exposure.viewport(fractions,now);Set<String> seen=exposure.matured(now);if(!seen.isEmpty())controller.exposed(seen);
        long due=exposure.dueAt();if(due>=0)list.postDelayed(reportExposure,Math.max(1,due-now));
    }
    private void updateAndReturnToTop(){
        if(!"feed".equals(page))show("feed");
        restoreAnchor=null;feedPosition=0;feedOffset=0;
        list.setSelectionFromTop(0,0);
        refreshFeed();
    }
    private void selectPosition(int position,int offset){list.setSelectionFromTop(position,offset-list.getPaddingTop());}
    private void remember(){if(controller!=null)controller.remember(feedPosition,feedOffset);}
    private void show(String next){
        pauseExposure();
        remember();boolean changed=!page.equals(next);int returnPosition=feedPosition,returnOffset=feedOffset;
        if(changed)cancelStartupRead();
        page=next;controller.feedPage("feed".equals(page));pageHeading();
        // Different pages have unrelated row identities. Reset ListView's sync
        // state so it cannot carry a partial card offset into the source list.
        if(changed)list.setAdapter(adapter);else adapter.notifyDataSetChanged();
        if("feed".equals(page))selectPosition(returnPosition,returnOffset);else list.setSelectionFromTop(0,0);
        if("sources".equals(page))controller.readSources();
    }
    private void pageHeading(){
        boolean plain="feed".equals(page)||"settings".equals(page);brand.setVisibility(plain?View.GONE:View.VISIBLE);
        brand.setText("sources".equals(page)?"内容来源":"preferences".equals(page)?"阅读偏好":"storage".equals(page)?"缓存与存储":"advanced".equals(page)?"高级与诊断":"");
        updateStatusDescription();
        boolean feedPage="feed".equals(page);navigation.feedPage(feedPage);
        settingsButton.setText(feedPage?"":"‹");
        android.graphics.drawable.Drawable icon=feedPage?getDrawable(R.drawable.ic_settings_outline).mutate():null;
        if(icon!=null){icon.setTint(accent);icon.setBounds(0,0,dp(20),dp(20));}
        // A centered drawable replaces the platform-dependent emoji gear.
        settingsButton.setCompoundDrawables(icon,null,null,null);
        settingsButton.setContentDescription(feedPage?"设置":"返回");
        updateToolbar();
    }
    private boolean refreshBusy(){return refreshing||loading&&feed.isEmpty();}
    private void updateToolbar(){updates.setContentDescription(refreshBusy()?"正在刷新":updateAvailable?"查看更新":"刷新");updates.setEnabled(true);updates.setAlpha(1f);refreshIndicator.busy(refreshBusy());updateRefreshAnimation();updateStatusDescription();}
    private void updateRefreshAnimation(){if(refreshIndicator!=null)refreshIndicator.animate(exposureForeground&&hasWindowFocus()&&"feed".equals(page)&&navigation!=null&&navigation.toolbarVisible());}
    @Override public void refreshWork(boolean running){refreshing=running;if(!running)clearRefreshDraw();updateToolbar();}
    private void updateStatusDescription(){
        if(android.os.Build.VERSION.SDK_INT>=30)updates.setStateDescription((refreshBusy()?"正在刷新。":"")+recommendationState.accessibilityStatus());
        settingsButton.setContentDescription("feed".equals(page)?"设置":"返回");
    }
    private void recommendationDetails(){
        if(recommendationDialog!=null&&recommendationDialog.isShowing())return;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("本机推荐").setMessage(controller.recommendationStatus()).setPositiveButton("继续整理",(d,which)->{show("feed");controller.requestRecommendation();}).setNeutralButton("校验并修复模型",(d,which)->controller.repairModels()).setNegativeButton("关闭",null).create();
        recommendationDialog=dialog;
        dialog.setOnDismissListener(d->{if(recommendationDialog==dialog)recommendationDialog=null;});
        dialog.show();
    }
    private String time(long value){return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(value));}
    private void filterMenu(){
        String[] labels=new String[HotSourceCatalog.HOT.size()+1];labels[0]="全部来源";int current=0;
        for(int n=0;n<HotSourceCatalog.HOT.size();n++){Source source=HotSourceCatalog.HOT.get(n);labels[n+1]=source.label;if(source.name().equals(controller.sourceFilter()))current=n+1;}
        new AlertDialog.Builder(this).setTitle("筛选来源").setSingleChoiceItems(labels,current,(dialog,n)->{remember();controller.filterSource(n==0?"all":HotSourceCatalog.HOT.get(n-1).name());dialog.dismiss();show("feed");}).setNegativeButton("取消",null).show();
    }
    private void sortMenu(){new AlertDialog.Builder(this).setTitle("阅读顺序").setSingleChoiceItems(new String[]{"候选顺序","最新 · 无日期的内容排在后面"},newest?1:0,(d,n)->{remember();controller.sort(n==1);d.dismiss();show("feed");}).show();}
    private void prepareReader(HotCandidate candidate,boolean retry){
        startupReadCandidate=candidate;if(startupReadWaiting)return;startupReadWaiting=true;
        String requestedPage=page;long requestedEpoch=startupReadEpoch;
        Toast.makeText(this,"正在准备阅读…",Toast.LENGTH_SHORT).show();
        java.lang.ref.WeakReference<DiscoveryActivity> startupOwner=new java.lang.ref.WeakReference<>(this);
        Runnable ready=()->{DiscoveryActivity owner=startupOwner.get();if(owner==null||owner.startupReadEpoch!=requestedEpoch)return;owner.startupReadWaiting=false;HotCandidate selected=owner.startupReadCandidate;owner.startupReadCandidate=null;if(selected!=null&&!owner.isFinishing()&&!owner.isDestroyed()&&owner.hasWindowFocus()&&requestedPage.equals(owner.page))owner.open(selected);};
        if(retry)ReaderStartup.retry(getApplicationContext(),ready);else ReaderStartup.prepare(getApplicationContext(),ready);
    }
    private void open(HotCandidate candidate){
        if(openingReader)return;
        if(ReaderStartup.failed()){new AlertDialog.Builder(this).setMessage("阅读器暂时未准备好，可以重试准备。当前列表会保留。").setNegativeButton("取消",null).setPositiveButton("重试准备",(dialog,which)->prepareReader(candidate,true)).show();return;}
        if(ReaderStartup.pending()){prepareReader(candidate,false);return;}
        openingReader=true;remember();String prefetchPlan=controller.readerPrefetchPlan(candidate);String handoff=!candidate.source.unifiedReading()?null:controller.handoff(candidate.readerItem());controller.stop();
        if(!candidate.source.unifiedReading()){
            sourceReadCandidate=candidate;LoginActivity.pendingDocument=null;
            startActivityForResult(new Intent(this,LoginActivity.class).putExtra("source",candidate.source.name()).putExtra("url",candidate.originalUrl).putExtra("reader_item",candidate.readerItem().json().toString()).putExtra("browse",true).putExtra("reader_import",true).putExtra("return_to_discovery",true),202);
        }
        else try{startActivity(new Intent(this,MainActivity.class).putExtra("discovery_item",candidate.readerItem().json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("reader_prefetch_plan",prefetchPlan).putExtra("test_database",getIntent().getStringExtra("test_database")).putExtra("article_handoff",handoff).putExtra("direct_reader",true));}
        catch(RuntimeException failure){ArticleHandoff.cancel(handoff);throw failure;}
    }
    private void sourceMenu(Source source){
        JSONObject state=sources.get(source);String message=state==null?"":state.optString("error");
        long retry=state==null?0:state.optLong("retryAt");if(retry>System.currentTimeMillis())message+=(message.isEmpty()?"":"\n")+"可重试时间："+time(retry);
        new AlertDialog.Builder(this).setTitle(source.label+(message.isEmpty()?"":" · 来源状态")).setItems(new String[]{"登录 / 打开来源页","刷新候选","通过网页读取",message.isEmpty()?"查看最近更新":"查看失败详情","多看这个平台","少看这个平台"},(d,n)->{
            remember();
            if(n==0){controller.stop();startActivityForResult(new Intent(this,LoginActivity.class).putExtra("source",source.name()).putExtra("url",source.login).putExtra("board",true),201);loginReturn=source;}
            else if(n==1)controller.refresh(Collections.singletonList(source));
            else if(n==2)controller.retryBrowser(source);
            else if(n==4||n==5)controller.platformFeedback(source,n==4);
            else{JSONObject current=sources.get(source);new AlertDialog.Builder(this).setTitle(source.label).setMessage(sourceStatus(current)+(current==null||current.optString("error").isEmpty()?"":"\n"+current.optString("error"))).setPositiveButton("关闭",null).show();}
        }).show();
    }
    private String sourceStatus(JSONObject state){
        if(state==null)return "正在读取本机状态";
        String value=state.optString("status"),label;
        switch(value){case "READY":label="已更新";break;case "REFRESHING":label="正在刷新";break;case "OFFLINE":label="离线，保留缓存";break;case "LOGIN_REQUIRED":label="需登录或访问受限";break;case "RATE_LIMITED":label="平台限制了刷新频率";break;case "STALE":label="本机缓存";break;case "EMPTY":label="尚未获取";break;default:label="暂未取得新内容";}
        long last=state.optLong("lastSuccessAt");return (!state.optBoolean("enabled")?"已关闭 · ":"")+label+(last>0?"\n最近成功："+time(last):"")+(state.optLong("retryAt")>System.currentTimeMillis()?"\n可重试："+time(state.optLong("retryAt")):"");
    }
    private void cardMenu(FeedStore.Row row){
        String[] actions=new String[]{"多看这类","少看这类","这个话题看够了","排序说明","关注相关内容","屏蔽这条内容"};
        int platformIndex=row.entries.size()>1?8:6;
        actions=Arrays.copyOf(actions,platformIndex+1);if(row.entries.size()>1){actions[6]="查看各来源";actions[7]="分开显示这些来源";}actions[platformIndex]="调整"+row.candidate.source.label+"的平台偏好";
        new AlertDialog.Builder(this).setTitle(row.candidate.title).setItems(actions,(d,n)->{
            remember();
            if(n<3)controller.feedback(row,new String[]{"MORE","LESS","ENOUGH"}[n]);else if(n==4)followEditor(row.candidate.title);else if(n==5)controller.feedback(row,"BLOCK");else if(n==platformIndex)new AlertDialog.Builder(this).setTitle(row.candidate.source.label+"的平台偏好").setItems(new String[]{"多看这个平台","少看这个平台"},(dialog,choice)->controller.platformFeedback(row.candidate.source,choice==0)).show();else if(n==6)groupSources(row);else if(n==7)controller.separate(row);else new AlertDialog.Builder(this).setTitle("当前阅读顺序").setMessage(row.reason+"。同一条内容不会因重复获取而加分；这不是全网热度排名。").setPositiveButton("知道了",null).show();
        }).show();
    }
    private void groupSources(FeedStore.Row row){String[] labels=new String[row.entries.size()];for(int n=0;n<labels.length;n++)labels[n]=row.entries.get(n).source.label+" · "+row.entries.get(n).title;new AlertDialog.Builder(this).setTitle("选择阅读来源").setItems(labels,(dialog,n)->open(row.entries.get(n))).setNegativeButton("关闭",null).show();}
    private boolean settingsPage(){return Arrays.asList("settings","preferences","storage","advanced").contains(page);}
    private String[] settingsEntries(){switch(page){case "preferences":return new String[]{"从热门话题选择兴趣","添加关注内容","反馈记录 / 撤销","阅读行为弱参考","重置偏好"};case "storage":return new String[]{"本机数据占用","清理阅读缓存","清理推荐缓存"};case "advanced":return new String[]{"本机推荐状态","本轮推荐评价","阅读顺序","筛选来源"};default:return new String[]{"阅读外观","阅读偏好","内容来源","缓存与存储","高级与诊断"};}}
    private void preferences(){show("settings");}
    private void settingAction(int index){
        if("preferences".equals(page)){preferenceAction(index);return;}
        if("storage".equals(page)){storageAction(index);return;}
        if("advanced".equals(page)){advancedAction(index);return;}
        switch(index){
            case 0:startActivityForResult(new Intent(this,SettingsActivity.class),301);break;
            case 1:preferenceOptions();break;
            case 2:sourcesReturnPage="settings";show("sources");break;
            case 3:storageOptions();break;
            case 4:advancedOptions();break;
        }
    }
    private void preferenceOptions(){show("preferences");}
    private void preferenceAction(int index){
        switch(index){
            case 0:interestSetup();break;
            case 1:followEditor("");break;
            case 2:controller.feedbackHistory(this::feedbackHistory);break;
            case 3:readLearningOptions();break;
            case 4:new AlertDialog.Builder(this).setTitle("重置阅读偏好？").setMessage("反馈记录将清除，平台登录和阅读历史保留。").setNegativeButton("取消",null).setPositiveButton("重置",(d,which)->controller.resetProfile()).show();break;
        }
    }
    private void readLearningOptions(){controller.readLearningState(enabled->{if(isFinishing()||isDestroyed())return;new AlertDialog.Builder(this).setTitle("阅读行为弱参考 · "+(enabled?"已开启":"已关闭")).setMessage("点开本身不记为喜欢。正文显示后，前台阅读至少20秒并主动滚动才产生正向弱参考；较长正文显示0.8至5秒后主动返回，记一次更轻的负向参考。短文、加载等待、登录失败和切后台不计入快速退出；连续3篇才微调平台偏好。弱参考7天后失效。可在反馈记录中撤销；关闭会撤回已有阅读弱参考。不会覆盖明确的多看、少看和关注。").setPositiveButton(enabled?"关闭并撤回":"开启",(dialog,which)->controller.readLearning(!enabled)).setNegativeButton("返回",(dialog,which)->preferenceOptions()).show();});}
    private void storageOptions(){show("storage");}
    private void storageAction(int index){
        if(index==0)controller.storageStatus(message->{if(!isFinishing()&&!isDestroyed())new AlertDialog.Builder(this).setTitle("本机数据占用").setMessage(message).setPositiveButton("返回",(d,which)->storageOptions()).show();});
        else if(index==1)new AlertDialog.Builder(this).setTitle("清理阅读缓存？").setMessage("清除已预读和已阅读的正文及图片副本，再次打开可能需要加载。阅读进度、推荐偏好和平台登录保留。").setNegativeButton("取消",null).setPositiveButton("清理",(d,which)->controller.clearArticleCache()).show();
        else new AlertDialog.Builder(this).setTitle("清理推荐缓存？").setMessage("将清理候选列表和推荐计算结果，需要刷新后重新整理。阅读偏好、平台登录和离线模型保留。").setNegativeButton("取消",null).setPositiveButton("清理",(d,which)->controller.clearRecommendationCache()).show();
    }
    private void advancedOptions(){show("advanced");}
    private void advancedAction(int index){
        if(index==0)recommendationDetails();
        else if(index==1){if(trialEvaluation==null)trialEvaluation=new RecommendationTrialUi(this,controller);trialEvaluation.menu();}
        else if(index==2)sortMenu();
        else filterMenu();
    }
    private void interestSetup(){
        List<HotCandidate> candidates=controller.interestCandidates();if(candidates.isEmpty()){error("暂时没有可选话题，请先刷新来源");return;}
        showInterestSetup(candidates,new LinkedHashMap<>(),0);
    }
    private void showInterestSetup(List<HotCandidate> candidates,Map<String,Boolean> choices,int scrollPosition){
        if(isFinishing()||isDestroyed()||setupDialog!=null&&setupDialog.isShowing())return;
        controller.interacting(true);setupCandidates=candidates;setupChoices=choices;
        boolean stacked=getResources().getConfiguration().screenWidthDp<360||getResources().getConfiguration().fontScale>=1.3f;
        LinearLayout body=column();body.setPadding(dp(16),0,dp(16),0);
        TextView hint=text("来自当前热门；未选择的保持跳过，不会当成不喜欢。",13,muted);hint.setPadding(0,dp(8),0,dp(12));body.addView(hint);
        for(HotCandidate candidate:candidates){
            TextView title=text(candidate.title+" · "+candidate.source.label,16,ink);title.setPadding(0,dp(12),0,dp(6));body.addView(title);
            RadioGroup options=new RadioGroup(this);options.setOrientation(stacked?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL);String[] labels={"想看","不想看","跳过"};int[] ids=new int[3];
            for(int n=0;n<3;n++){RadioButton option=new RadioButton(this);option.setId(View.generateViewId());ids[n]=option.getId();option.setText(labels[n]);option.setTextSize(13);option.setTextColor(ink);option.setMinHeight(dp(48));option.setContentDescription(labels[n]+"："+candidate.title);options.addView(option,stacked?new RadioGroup.LayoutParams(-1,-2):new RadioGroup.LayoutParams(0,-2,1));}
            Boolean selected=choices.get(candidate.id);options.check(ids[selected==null?2:selected?0:1]);options.setOnCheckedChangeListener((group,id)->{if(id==ids[2])choices.remove(candidate.id);else choices.put(candidate.id,id==ids[0]);});body.addView(options);
        }
        ScrollView scroll=new ScrollView(this);scroll.addView(body);LinearLayout frame=column();frame.addView(scroll,new LinearLayout.LayoutParams(-1,Math.max(dp(160),(int)(getResources().getDisplayMetrics().heightPixels*(stacked?0.38f:0.55f)))));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("选几条兴趣 · "+candidates.size()+" 个话题").setView(frame).setPositiveButton("保存选择",(d,n)->controller.finishInterestSetup(candidates,choices)).setNeutralButton("先浏览",(d,n)->controller.finishInterestSetup(Collections.emptyList(),Collections.emptyMap())).setNegativeButton("取消",null).create();
        setupDialog=dialog;setupScroll=scroll;
        dialog.setOnDismissListener(d->{if(setupDialog==dialog){setupDialog=null;setupCandidates=null;setupChoices=null;setupScroll=null;}controller.interacting(false);});dialog.show();scroll.post(()->scroll.scrollTo(0,scrollPosition));
    }
    private void followEditor(String initial){
        controller.suspendRecommendation("编辑关注内容时暂停整理");
        EditText input=new EditText(this);input.setHint("例如：空间站的科学实验");input.setText(initial);input.setMinLines(2);input.setMaxLines(5);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});input.setContentDescription("想关注的内容");
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("关注什么内容？").setMessage("用一句话描述。在已获取的热门内容中优先寻找相关内容，不会自动订阅或通知。").setView(input).setNegativeButton("取消",null).setPositiveButton("选择期限",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{
            String text=HotCandidate.clean(input.getText().toString(),240);if(text.codePointCount(0,text.length())<2){input.setError("请写下至少两个字");return;}dialog.dismiss();
            new AlertDialog.Builder(this).setTitle("关注多久？").setItems(new String[]{"1 天","7 天","30 天","长期关注"},(choice,n)->controller.follow(text,new int[]{1,7,30,0}[n])).setNegativeButton("取消",null).show();
        }));dialog.show();
    }
    private static final String[] FEEDBACK_FILTERS={"全部反馈","内容偏好","平台偏好","阅读弱参考"};
    private void feedbackHistory(JSONArray history){feedbackHistory(history,0);}
    private static int feedbackKind(JSONObject row){
        if("source".equals(row.optString("scope")))return 2;
        return Arrays.asList("READ","QUICK_EXIT","SAVED").contains(row.optString("action"))?3:1;
    }
    private String feedbackLabel(JSONObject row){
        String action=row.optString("action");
        String label="QUICK_EXIT".equals(action)?"快速退出 · 弱参考":"READ".equals(action)?"近期阅读 · 弱参考":"SAVED".equals(action)?"历史收藏 · 弱参考":"MORE".equals(action)?"多看":"LESS".equals(action)?"少看":"ENOUGH".equals(action)?"看够了":"BLOCK".equals(action)?"已屏蔽":"SEPARATE".equals(action)?"分开显示":"FOLLOW".equals(action)?(row.isNull("expires")?"长期关注":"关注至 "+time(row.optLong("expires"))):action;
        return (feedbackKind(row)==2?"平台偏好 · ":"")+label;
    }
    private void feedbackHistory(JSONArray history,int filter){
        if(isFinishing()||isDestroyed())return;
        List<JSONObject> selected=new ArrayList<>();
        // Store order is chronological. Present newest first without changing the
        // event order used to rebuild content and platform preferences.
        for(int n=history.length()-1;n>=0;n--){JSONObject row=history.optJSONObject(n);if(row!=null&&(filter==0||feedbackKind(row)==filter))selected.add(row);}
        String[] labels=new String[selected.size()];
        for(int n=0;n<labels.length;n++){JSONObject row=selected.get(n);String text=app.quietreader.discovery.HotCandidate.clean(row.optString("sample"),81);boolean clipped=text.codePointCount(0,text.length())>80;labels[n]=feedbackLabel(row)+" · "+app.quietreader.discovery.HotCandidate.clean(text,80)+(clipped?"…":"");}
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle("反馈记录 · "+FEEDBACK_FILTERS[filter]+"（"+selected.size()+"）")
            .setNegativeButton("关闭",null).setNeutralButton("筛选",(d,w)->new AlertDialog.Builder(this).setTitle("筛选反馈记录").setSingleChoiceItems(FEEDBACK_FILTERS,filter,(picker,n)->{picker.dismiss();feedbackHistory(history,n);}).setNegativeButton("返回记录",(picker,n)->feedbackHistory(history,filter)).show());
        if(selected.isEmpty())dialog.setMessage(history.length()==0?"在话题卡片菜单中选择多看、少看或看够了，可在这里查看并撤销。":"暂无这类反馈，可切换筛选查看其他记录。");
        else dialog.setItems(labels,(d,n)->feedbackDetails(history,filter,selected.get(n)));
        dialog.show();
    }
    private void feedbackDetails(JSONArray history,int filter,JSONObject row){
        if(isFinishing()||isDestroyed())return;
        String message=feedbackLabel(row)+"\n\n"+row.optString("sample")+"\n\n记录于："+time(row.optLong("at"));
        if(!row.isNull("expires")&&row.optLong("expires")>0)message+="\n失效于："+time(row.optLong("expires"));
        if("SAVED".equals(row.optString("action")))message+="\n\n撤销这条推荐参考，历史收藏数据仍保留。";
        else message+="\n\n只撤销这条反馈，其他反馈保留。";
        new AlertDialog.Builder(this).setTitle("反馈详情").setMessage(message).setNegativeButton("返回",(d,n)->feedbackHistory(history,filter))
            .setPositiveButton("撤销反馈",(d,n)->{remember();controller.undo(row.optString("id"));}).show();
    }
    @Override public void newPresentation(){pauseExposure();exposure.newPresentation();}
    private void clearRefreshDraw(){refreshDrawEpoch++;if(refreshDraw!=null&&list!=null){if(list.getViewTreeObserver().isAlive())list.getViewTreeObserver().removeOnPreDrawListener(refreshDraw);refreshDraw=null;}}
    @Override public void beforeFeedDraw(Runnable complete){
        clearRefreshDraw();
        if(list==null||!"feed".equals(page)||!exposureForeground){complete.run();return;}
        long epoch=refreshDrawEpoch;
        // Pre-draw runs after the replacement rows have been laid out. Settle
        // the indicator before this same frame is drawn, not in a later UI job.
        // Removing a pre-draw listener here is supported; OnDraw cannot do so.
        refreshDraw=()->{if(epoch!=refreshDrawEpoch)return true;clearRefreshDraw();complete.run();return true;};
        list.getViewTreeObserver().addOnPreDrawListener(refreshDraw);list.invalidate();
    }
    @Override public void feed(List<FeedStore.Row> rows,int position,int offset,boolean newest){
        pauseExposure();feedIdentities=new HashSet<>();for(FeedStore.Row row:rows)feedIdentities.add(row.eventId);
        nativeFeedReady=true;readerAfterNativeDraw();
        if(restoreAnchor!=null){position=Math.min(restorePosition,Math.max(0,rows.size()-1));for(int n=0;n<rows.size();n++)if(EventGroups.matches(rows.get(n),restoreAnchor)){position=n;break;}offset=restoreOffset;restoreAnchor=null;}
        feed=rows;this.newest=newest;feedPosition=position;feedOffset=offset;
        updateToolbar();
        if("feed".equals(page)){adapter.notifyDataSetChanged();if(position==0&&offset==0)list.setSelectionFromTop(0,0);else selectPosition(position,offset);}
        if(pendingSetup!=null){Bundle restored=pendingSetup;pendingSetup=null;list.post(()->{
            try{JSONArray data=new JSONArray(restored.getString("candidates","[]"));if(data.length()==0||data.length()>20)return;List<HotCandidate> candidates=new ArrayList<>();Map<String,Boolean> choices=new LinkedHashMap<>();JSONObject selected=new JSONObject(restored.getString("choices","{}"));for(int n=0;n<data.length();n++){HotCandidate candidate=HotCandidate.fromJson(data.getJSONObject(n));candidates.add(candidate);if(selected.has(candidate.id))choices.put(candidate.id,selected.getBoolean(candidate.id));}showInterestSetup(candidates,choices,restored.getInt("scroll"));}catch(JSONException ignored){}
        });}
    }
    @Override public void sources(Map<Source,JSONObject> states){sources=states;if("sources".equals(page))adapter.notifyDataSetChanged();}
    @Override public void recommendationState(RecommendationState value){recommendationState=value;updateStatusDescription();if(recommendationDialog!=null&&recommendationDialog.isShowing())recommendationDialog.setMessage(value.detail);}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(app.quietreader.ai.RecommendationPolicy.memoryPressure(level)&&controller!=null)controller.memoryPressure();}
    @Override public void state(boolean loading,boolean updateAvailable){boolean changed=this.loading!=loading;this.loading=loading;this.updateAvailable=updateAvailable;updateToolbar();updateStatusDescription();if(changed&&feed.isEmpty()&&"feed".equals(page))adapter.notifyDataSetChanged();}
    @Override public void error(String message){nativeFeedReady=true;readerAfterNativeDraw();Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}
    private void back(){if("sources".equals(page))show(sourcesReturnPage);else if(settingsPage()&&!"settings".equals(page))show("settings");else if(!"feed".equals(page))show("feed");else finish();}
    @Override public void onBackPressed(){back();}
    @Override protected void onResume(){super.onResume();exposureForeground=true;startupForeground=true;openingReader=false;if(!ReaderStartup.pending()){java.lang.ref.WeakReference<DiscoveryActivity> ownerRef=new java.lang.ref.WeakReference<>(this);ReaderStartup.defer(getApplicationContext(),()->{DiscoveryActivity owner=ownerRef.get();if(owner!=null&&!owner.isDestroyed()&&owner.controller!=null)owner.controller.readerReady();});}readerAfterNativeDraw();if(trialEvaluation!=null)trialEvaluation.foreground();if(controller!=null){controller.feedPage("feed".equals(page));if(loginReturn!=null){Repository cache=new Repository(this);cache.invalidateArticles();cache.close();}controller.start();if(loginReturn!=null){Source source=loginReturn;loginReturn=null;controller.refresh(Collections.singletonList(source));}}if(list!=null)list.post(reportExposure);}
    @Override protected void onPostResume(){super.onPostResume();updateRefreshAnimation();}
    @Override protected void onPause(){exposureForeground=false;updateRefreshAnimation();pauseExposure();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);updateRefreshAnimation();if(focused)sampleExposure();else pauseExposure();}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request==TrialExportUi.REQUEST){if(trialEvaluation!=null)trialEvaluation.exportResult(result,data);return;}if(request==301){recreate();return;}
        if(request!=202)return;
        HotCandidate candidate=sourceReadCandidate;sourceReadCandidate=null;
        Document doc=LoginActivity.pendingDocument;LoginActivity.pendingDocument=null;
        if(result!=RESULT_OK||data==null||candidate==null||!candidate.source.name().equals(data.getStringExtra("source"))||doc==null||!UrlPolicy.belongs(candidate.source,doc.url))doc=null;
        Repository cache=new Repository(this);boolean imported;
        try{imported=cache.importBrowserSnapshot(doc);}finally{cache.close();}
        if(!imported){if(result==RESULT_OK)error("来源状态已变化，请重新打开内容后读取");return;}
        boolean same=ArticleVersion.matches(candidate.readerItem(),doc);
        Item item=same?candidate.readerItem():new Item(candidate.source,doc.title,doc.url,"");
        Intent reader=new Intent(this,MainActivity.class).putExtra("discovery_item",item.json().toString()).putExtra("test_database",getIntent().getStringExtra("test_database")).putExtra("direct_reader",true).putExtra("imported_source_url",item.url);
        if(same)reader.putExtra("discovery_candidate",candidate.json().toString());
        controller.stop();startActivity(reader);
    }
    private void cancelStartupRead(){startupReadEpoch++;startupReadCandidate=null;startupReadWaiting=false;}
    @Override protected void onStop(){clearRefreshDraw();startupForeground=false;readerDrawQueued=false;if(list!=null){list.removeCallbacks(releaseReaderStartup);if(readerDraw!=null){list.getViewTreeObserver().removeOnPreDrawListener(readerDraw);readerDraw=null;}}cancelStartupRead();if(trialEvaluation!=null)trialEvaluation.pause();remember();if(controller!=null)controller.stop();super.onStop();}
    @Override protected void onSaveInstanceState(Bundle out){remember();out.putString("page",page);if(controller!=null)out.putString("sourceFilter",controller.sourceFilter());out.putString("sourcesReturnPage",sourcesReturnPage);out.putInt("feedPosition",feedPosition);out.putInt("feedOffset",feedOffset);out.putString("feedAnchor",feedPosition<feed.size()?feed.get(feedPosition).eventId:"");if(loginReturn!=null)out.putString("loginReturn",loginReturn.name());if(sourceReadCandidate!=null)out.putString("sourceReadCandidate",sourceReadCandidate.json().toString());if(setupDialog!=null&&setupDialog.isShowing()&&setupCandidates!=null){Bundle setup=new Bundle();JSONArray candidates=new JSONArray();for(HotCandidate candidate:setupCandidates)candidates.put(candidate.json());setup.putString("candidates",candidates.toString());setup.putString("choices",new JSONObject(setupChoices).toString());setup.putInt("scroll",setupScroll.getScrollY());out.putBundle("interestSetup",setup);}if(trialEvaluation!=null){trialEvaluation.saveExport(out);if(trialEvaluation.activeSession()!=null)out.putString("trialSession",trialEvaluation.activeSession());}super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){clearRefreshDraw();pauseExposure();if(list!=null&&list.getViewTreeObserver().isAlive())list.getViewTreeObserver().removeOnGlobalLayoutListener(exposureLayout);if(trialEvaluation!=null)trialEvaluation.close();if(setupDialog!=null)setupDialog.dismiss();if(recommendationDialog!=null)recommendationDialog.dismiss();if(controller!=null)controller.close();super.onDestroy();}
    @Override public int feedCapacity(List<FeedStore.Row> candidates){
        if(candidates.isEmpty())return 0;
        int width=list.getWidth()-list.getPaddingLeft()-list.getPaddingRight();
        int height=list.getHeight()-list.getPaddingTop()-list.getPaddingBottom();
        if(width<=0||height<=0){
            android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
            WindowInsets insets=list.getRootWindowInsets();
            int horizontal=insets==null?0:insets.getSystemWindowInsetLeft()+insets.getSystemWindowInsetRight();
            int vertical=insets==null?dp(48):insets.getSystemWindowInsetTop()+insets.getSystemWindowInsetBottom();
            width=Math.max(1,metrics.widthPixels-dp(36)-horizontal);
            height=Math.max(1,metrics.heightPixels-vertical-list.getPaddingTop()-list.getPaddingBottom());
        }
        if(measuringCard==null)measuringCard=new Card();
        final int rowWidth=width;
        return FeedBatch.countForHeight(candidates.size(),height,index->{
            measuringCard.content(candidates.get(index),index);
            measuringCard.measure(View.MeasureSpec.makeMeasureSpec(rowWidth,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            return measuringCard.getMeasuredHeight()+dp(10);
        });
    }
    private final class Card extends LinearLayout {
        final TextView title,summary,meta,menu;final LinearLayout body;
        Card(){super(DiscoveryActivity.this);setOrientation(HORIZONTAL);setBaselineAligned(false);setGravity(Gravity.TOP);setPadding(dp(12),dp(10),dp(4),dp(6));
            body=column();addView(body,new LayoutParams(0,-2,1));
            title=text("",16,ink);title.setLineSpacing(dp(3),1);title.setTypeface(null,Typeface.BOLD);body.addView(title);
            summary=text("",12,muted);summary.setMaxLines(2);summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setPadding(0,dp(6),0,0);body.addView(summary);
            meta=text("",11,muted);meta.setPadding(0,dp(4),0,0);body.addView(meta);menu=button("⋯",()->{});LayoutParams menuLayout=new LayoutParams(dp(48),dp(48));menuLayout.gravity=Gravity.BOTTOM;addView(menu,menuLayout);
            setMinimumHeight(dp(64));
        }
        void bind(FeedStore.Row row,int index){
            content(row,index);
            HotCandidate candidate=row.candidate;
            setBackground(surface(index==0?soft:paper));
            setOnClickListener(v->open(candidate));setOnLongClickListener(v->{cardMenu(row);return true;});menu.setContentDescription("话题选项："+candidate.title);menu.setOnClickListener(v->cardMenu(row));
        }
        void content(FeedStore.Row row,int index){
            HotCandidate candidate=row.candidate;title.setText(candidate.title);title.setTextSize(index==0?17:16);
            String preview=HotCandidate.clean(candidate.snippet,1600);
            String normalized=preview.replaceAll("[\\p{P}\\p{Z}\\s]","").toLowerCase(Locale.ROOT);
            String heading=candidate.title.replaceAll("[\\p{P}\\p{Z}\\s]","").toLowerCase(Locale.ROOT);
            if(normalized.isEmpty()||heading.contains(normalized))preview="";
            else if(preview.codePointCount(0,preview.length())>36)preview=HotCandidate.clean(preview,36)+"…";
            summary.setText(preview);summary.setVisibility(preview.isEmpty()?View.GONE:View.VISIBLE);
            meta.setText(candidate.source.label+(row.entries.size()>1?" · "+row.entries.size()+" 个来源入口":"")+" · "+(candidate.publishedAt==null?"获取于 "+time(candidate.observedAt):"来源时间 "+time(candidate.publishedAt)));
        }
    }
    private final class SourceCard extends LinearLayout {
        final TextView title,status;final Switch enabled;
        SourceCard(){super(DiscoveryActivity.this);setPadding(dp(12),dp(12),dp(8),dp(12));setGravity(Gravity.CENTER_VERTICAL);setBackground(surface(paper));LinearLayout details=column();title=text("",18,ink);status=text("",12,muted);status.setPadding(0,dp(8),0,0);details.addView(title);details.addView(status);addView(details,new LayoutParams(0,-2,1));enabled=new Switch(DiscoveryActivity.this);enabled.setMinHeight(dp(48));enabled.setMinWidth(dp(48));addView(enabled);}
        void bind(Source source){JSONObject state=sources.get(source);title.setText(source.label+" · "+source.category);status.setText(sourceStatus(state));enabled.setOnCheckedChangeListener(null);enabled.setChecked(state!=null&&state.optBoolean("enabled"));enabled.setEnabled(state!=null);enabled.setContentDescription("启用"+source.label);enabled.setOnCheckedChangeListener((v,on)->{remember();controller.enable(source,on);});setOnClickListener(v->sourceMenu(source));}
    }
    private final class Rows extends BaseAdapter {
        @Override public int getViewTypeCount(){return 4;}
        @Override public int getItemViewType(int position){if(settingsPage())return 3;if("sources".equals(page))return 1;int count=feed.size();return count==0?2:0;}
        @Override public int getCount(){if(settingsPage())return settingsEntries().length;if("sources".equals(page))return HotSourceCatalog.HOT.size();int count=feed.size();return count==0?1:count;}
        @Override public Object getItem(int position){return position;}
        @Override public long getItemId(int position){return position;}
        @Override public View getView(int position,View recycled,android.view.ViewGroup parent){
            int type=getItemViewType(position);LinearLayout wrapper=recycled instanceof LinearLayout?(LinearLayout)recycled:column();wrapper.setPadding(0,dp(5),0,dp(5));
            if(type==3){wrapper.removeAllViews();TextView value=button(settingsEntries()[position],()->settingAction(position));value.setTextSize(16);value.setTextColor(ink);value.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);value.setPadding(dp(14),dp(12),dp(14),dp(12));value.setMinHeight(dp(56));value.setBackground(surface(paper));wrapper.addView(value,new LinearLayout.LayoutParams(-1,-2));return wrapper;}
            if(type==2){wrapper.removeAllViews();int count=feed.size();TextView value=text(loading?"正在获取热门话题…":(!"all".equals(controller.sourceFilter())?"这个来源暂无话题":"尚无话题"),14,muted);value.setPadding(dp(8),dp(12),dp(8),dp(12));value.setTextColor(muted);wrapper.addView(value);return wrapper;}
            if(type==1){SourceCard card=wrapper.getChildCount()>0&&wrapper.getChildAt(0) instanceof SourceCard?(SourceCard)wrapper.getChildAt(0):new SourceCard();wrapper.removeAllViews();wrapper.addView(card);card.bind(HotSourceCatalog.HOT.get(position));return wrapper;}
            Card card=wrapper.getChildCount()>0&&wrapper.getChildAt(0) instanceof Card?(Card)wrapper.getChildAt(0):new Card();wrapper.removeAllViews();wrapper.addView(card);
            card.bind(feed.get(position),position);wrapper.setTag(feed.get(position).eventId);return wrapper;
        }
    }
}
