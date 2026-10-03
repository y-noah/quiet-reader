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
import java.text.SimpleDateFormat;
import java.util.*;
import static app.quietreader.Models.*;

public final class MainActivity extends Activity {
    private int INK=0xff30323a, MUTED=0xff696c76, GREEN=0xff247bc1, BG=0xfffafafa, CARD=0xfff1f2f4, BUTTON=0xffeceef2;
    private boolean dark,loadingMore=false,morePaused=false,primaryPending=false,wasStopped=false;
    private ArticlePreloader preloader;
    private TextView jumpButton;
    private final android.os.Handler preloadHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable predict=this::predictArticles;
    private boolean scrollBackwards;
    private AnswerStream answers;
    private final Set<String> expanded=new HashSet<>();
    private int pendingAnchor=-1,openPosition=-1;
    private long suppressAutoUntil=0;
    private volatile String readerHtml="",readerPageUrl="";
    private int readerRevision=0;
    private String streamAnchor="";
    private int streamAnchorOffset=0;
    private static final class ReadingMark {Set<String> expanded;int position;}
    private static final android.util.LruCache<String,ReadingMark> marks=new android.util.LruCache<>(40);
    private static final android.util.LruCache<String,byte[]> images=new android.util.LruCache<String,byte[]>(2*1024*1024){@Override protected int sizeOf(String k,byte[] v){return v.length;}};
    private Repository repo;
    private AggregateLoader aggregateLoader;
    private final Map<String,AggregateRanker.Entry> aggregateEntries=new HashMap<>();
    private String aggregateDetail="";
    private LinearLayout root, content;
    private LinearLayout platformNavigation;
    private final NavigationScroll navigationScroll=new NavigationScroll();
    private boolean restoringBoard;
    private ScrollView scroll;
    private Source selected=Source.AGGREGATE;
    private int generation=0, font=19;
    private Item current;
    private Document reading;
    private final Deque<Item> history=new ArrayDeque<>();
    private List<Item> saved=new ArrayList<>();
    private boolean savedPage=false;
    private WebView readerWeb;
    private TextView readerStatus;
    private ImagePreview imagePreview;
    private List<Item> boardItems=Collections.emptyList();
    private String boardLabel="";
    private static final class BoardMark {String anchor="";int position,offset;}
    private final Map<Source,BoardMark> boardMarks=new EnumMap<>(Source.class);
    private Source boardSource;
    private int boardRevision;
    private DynamicReader dynamic;
    private DynamicBoard dynamicBoard;
    private static final class ReadingState {
        Item current; Document document; List<Item> history; int position; Set<String> expanded;
    }
    @Override public void onCreate(Bundle state) {
        Theme.apply(this);super.onCreate(state); repo=new Repository(this);aggregateLoader=new AggregateLoader(repo);dark=Theme.dark(this);
        if(dark){INK=0xffcdcfd5;MUTED=0xff9b9da7;GREEN=0xff77b8eb;BG=0xff1f2025;CARD=0xff282a31;BUTTON=0xff2d3038;}
        BG=Theme.background(this);INK=Theme.ink(this);GREEN=Theme.accent(this);
        preloader=new ArticlePreloader(this,()->{if(current==null&&!savedPage){rememberBoard();boardItems=repo.visibleItems(boardItems);drawBoard(boardMarks.get(selected));}});
        font=getPreferences(0).getInt("font",19);
        // A new launch opens the first tab; rotation/settings recreation restores current state below.
        try { saved=Models.fromJson(new JSONArray(getPreferences(0).getString("saved","[]"))); } catch(Exception ignored) {}
        if(state!=null) {
            try { selected=Source.valueOf(state.getString("source",Source.AGGREGATE.name())); } catch(Exception ignored) {}
            Bundle boards=state.getBundle("boardMarks");
            if(boards!=null)for(Source source:Source.values()){
                Bundle value=boards.getBundle(source.name());if(value==null)continue;
                BoardMark mark=new BoardMark();mark.anchor=value.getString("anchor","");
                mark.position=value.getInt("position");mark.offset=value.getInt("offset");boardMarks.put(source,mark);
            }
        }
        if(!selected.navigable())selected=Source.AGGREGATE;
        if(state!=null) {
            savedPage=false; // Legacy saved links stay on disk, but no longer have an app entry point.
            try {
                history.addAll(Models.fromJson(new JSONArray(state.getString("history","[]"))));
                String item=state.getString("current");
                if(item!=null)current=Item.from(new org.json.JSONObject(item));
                if(current!=null&&!current.source.readable())current=null;
                history.removeIf(i->!i.source.readable());
            } catch(Exception ignored) {}
        }
        ReadingState retained=(ReadingState)getLastNonConfigurationInstance();
        if(retained!=null&&retained.current!=null&&retained.current.source.readable()&&retained.document!=null) {
            if(retained.expanded!=null)expanded.addAll(retained.expanded);
            current=retained.current;history.clear();history.addAll(retained.history);
            frame(current.source.label+" / 阅读","news");scroller();readerFooter();render(retained.document);
            restorePosition(retained.position);
        } else if(current!=null) open(current,false);
        else if(savedPage)bookmarks();
        else home(false);
    }
    private void restorePosition(int position) {
        suppressAutoUntil=System.currentTimeMillis()+1000;
        if(readerWeb!=null){WebView target=readerWeb;target.postDelayed(()->{if(readerWeb==target)restoreWebPosition(target,position);},400);}
        else if(scroll!=null)scroll.post(()->scroll.scrollTo(0,position));
    }
    private void restoreWebPosition(WebView web,int position){
        web.scrollTo(0,ReadingPosition.clamp(position,web.getContentHeight(),web.getScale(),web.getHeight()));
    }
    @Override public Object onRetainNonConfigurationInstance() {
        ReadingState state=new ReadingState();state.current=current;state.document=reading;
        state.expanded=new HashSet<>(expanded);state.history=new ArrayList<>(history);state.position=readerWeb!=null?readerWeb.getScrollY():scroll==null?0:scroll.getScrollY();return state;
    }
    private int dp(float n) { return (int)(n*getResources().getDisplayMetrics().density+0.5f); }
    private TextView text(String value,int size,int color) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); v.setIncludeFontPadding(false); return v;
    }
    private GradientDrawable shape(int color,int radius) { GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void space(LinearLayout parent,int height) { parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height))); }
    private TextView button(String label,Runnable action) {
        TextView t=text(label,14,GREEN); t.setGravity(Gravity.CENTER); t.setPadding(dp(10),dp(10),dp(10),dp(10)); t.setMinHeight(dp(48)); t.setBackground(shape(CARD,6)); t.setOnClickListener(v->action.run()); t.setContentDescription(label); return t;
    }
    private ImageButton icon(int resource,String description,Runnable action) {
        ImageButton v=new ImageButton(this);v.setImageResource(resource);v.setColorFilter(MUTED);
        android.util.TypedValue ripple=new android.util.TypedValue();getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,ripple,true);
        v.setBackgroundResource(ripple.resourceId);v.setPadding(dp(12),dp(12),dp(12),dp(12));
        v.setContentDescription(description);v.setOnClickListener(w->action.run());return v;
    }
    private void options(View anchor) {
        PopupMenu menu=new PopupMenu(this,anchor);
        if(current==null)menu.getMenu().add("刷新榜单").setOnMenuItemClickListener(i->{home(true);return true;});
        else menu.getMenu().add("重新读取").setOnMenuItemClickListener(i->{repo.invalidateArticles();open(current,false);return true;});
        menu.getMenu().add("来源 / 登录").setOnMenuItemClickListener(i->{if(current==null&&selected==Source.AGGREGATE)aggregateLogin();else login(current==null?new Item(selected,selected.label,selected.login,""):current);return true;});
        if(current!=null)menu.getMenu().add("阅读字号").setOnMenuItemClickListener(i->{fontMenu();return true;});
        menu.getMenu().add("外观").setOnMenuItemClickListener(i->{theme();return true;});
        menu.getMenu().add("阅读设置").setOnMenuItemClickListener(i->{settings();return true;});
        if(reading!=null&&ReaderHtml.sections(reading).size()>1)menu.getMenu().add("回答 / 楼层目录").setOnMenuItemClickListener(i->{sectionMenu();return true;});
        if(current==null)menu.getMenu().add("全部平台").setOnMenuItemClickListener(i->{
            Source[] sources=Source.navigationOrder();
            new AlertDialog.Builder(this).setTitle("切换平台").setItems(Arrays.stream(sources).map(s->s.label).toArray(String[]::new),(d,n)->{selected=sources[n];home(false);}).show();return true;
        });
        menu.show();
    }
    private void fontMenu(){new AlertDialog.Builder(this).setTitle("阅读字号").setItems(new String[]{"舒适 · 17","标准 · 19","大字 · 22","特大 · 25"},(d,n)->changeFont(new int[]{17,19,22,25}[n])).show();}
    private void frame(String eyebrow,String title) {
        if(platformNavigation!=null)platformNavigation.animate().cancel();
        platformNavigation=null;navigationScroll.reset();restoringBoard=false;
        repo.cancelPending();aggregateLoader.cancel();streamAnchor="";
        if(readerWeb!=null) { readerWeb.stopLoading(); readerWeb.destroy(); readerWeb=null; }
        if(dynamic!=null) { dynamic.close(); dynamic=null; }
        if(dynamicBoard!=null) {dynamicBoard.close();dynamicBoard=null;}
        readerStatus=null;jumpButton=null;root=column(); root.setBackgroundColor(BG); root.setFitsSystemWindows(true);root.setClipToPadding(false);
        root.setPadding(dp(18),0,dp(18),0); setContentView(root);
        if(preloader!=null)preloader.reattach();
        root.setOnApplyWindowInsetsListener((v,insets)-> { root.setPadding(dp(18),insets.getSystemWindowInsetTop(),dp(18),insets.getSystemWindowInsetBottom()); return insets; });
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(0,dp(6),0,dp(6));
        if(current!=null){TextView back=button("返回",this::goBack);back.setSingleLine(true);back.setBackgroundColor(Color.TRANSPARENT);bar.addView(back,new LinearLayout.LayoutParams(dp(Math.max(56,28*getResources().getConfiguration().fontScale+20)),dp(48)));}
        TextView brand=text(current==null?getString(R.string.app_name):current.source.label, current==null?19:15,INK);brand.setTypeface(null,Typeface.BOLD);
        brand.setGravity(Gravity.CENTER_VERTICAL);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton more=icon(R.drawable.ic_more_horiz,"更多选项",()->{});more.setOnClickListener(v->options(v));
        bar.addView(more,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(bar);
        root.setFocusableInTouchMode(true);root.requestFocus();
    }
    private void scroller() {
        scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false); scroll.setVerticalScrollBarEnabled(false);
        content=column(); content.setPadding(0,0,0,dp(26)); scroll.addView(content); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    }
    private void home(boolean refresh) {
        preloader.pause();preloadHandler.removeCallbacks(predict);scrollBackwards=false;
        rememberBoard();
        rememberReading();
        closeAnswers();expanded.clear();
        generation++; current=null; reading=null; history.clear(); savedPage=false;
        getPreferences(0).edit().putString("selected",selected.name()).apply();
        boardItems=Collections.emptyList();boardLabel="";boardSource=selected;
        frame("news","只看你关心的");
        scroller();
        LinearLayout row=new LinearLayout(this);row.setBaselineAligned(false);row.setContentDescription("平台选择");
        for(Source s:Source.navigationOrder()) {
            String shortName=s==Source.AGGREGATE?"总":s==Source.SMZDM?"值":s.label.substring(0,1);
            int brand=platformColor(s);
            TextView t=new PlatformMarkView(this,s,shortName,dark?blend(brand,Color.WHITE,.38f):brand);
            t.setSelected(s==selected);
            t.setOnClickListener(v->{selected=s;home(false);});
            row.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));
        }
        // Overlay instead of resizing the viewport: hiding the bar must not move the article rows.
        root.removeView(scroll);
        FrameLayout board=new FrameLayout(this);
        LinearLayout.LayoutParams boardSize=new LinearLayout.LayoutParams(-1,0,1);
        boardSize.leftMargin=-dp(18);boardSize.rightMargin=-dp(18);root.addView(board,boardSize);
        FrameLayout.LayoutParams listSize=new FrameLayout.LayoutParams(-1,-1);
        listSize.leftMargin=dp(18);listSize.rightMargin=dp(18);board.addView(scroll,listSize);
        content.setPadding(0,0,0,dp(56)); // Last row can scroll clear of the 48 dp touch targets.
        row.setBackgroundColor(BG);platformNavigation=row;
        board.addView(row,new FrameLayout.LayoutParams(-1,dp(48),Gravity.BOTTOM));
        if(selected==Source.AGGREGATE){
            aggregateEntries.clear();final int request=generation;
            aggregateLoader.load(refresh,(entries,summary,detail)->{
                if(request!=generation||current!=null||selected!=Source.AGGREGATE)return;
                aggregateEntries.clear();aggregateDetail=detail;List<Item> items=new ArrayList<>();
                for(AggregateRanker.Entry entry:entries){Item item=entry.primary;aggregateEntries.put(item.url,entry);items.add(new Item(item.source,item.title,item.url,entry.detail,item.video));}
                showBoard(items,summary);
            });return;
        }
        List<Item> cache=repo.cached(selected); long age=System.currentTimeMillis()-repo.cachedAt(selected);
        boolean cachedBoard=repo.cachedAt(selected)>0;
        if(cachedBoard) showBoard(cache,"缓存 · "+time(repo.cachedAt(selected)));
        else status("正在连接"+selected.label+"…","首次获取可能需要几秒钟");
        if(repo.offline()){if(!cachedBoard)status("当前没有网络","连接网络后，从右上角菜单刷新。已有本机缓存会保留。");else showBoard(cache,(refresh?"刷新失败 · ":"离线 · ")+"缓存 "+time(repo.cachedAt(selected)));return;}
        if(!refresh&&cachedBoard&&age<15*60*1000) return;
        final int request=generation;
        repo.board(selected,new Repository.Result<List<Item>>() {
            public void success(List<Item> items) { if(request==generation) showBoard(items,"更新于 "+time(System.currentTimeMillis())); }
            public void failure(String reason) {
                if(request!=generation)return;
                if(cache.isEmpty())status("正在尝试网页来源…","使用系统浏览器内核获取榜单");
                dynamicBoard=new DynamicBoard(MainActivity.this,selected,new Repository.Result<List<Item>>(){
                    public void success(List<Item> items){if(request!=generation)return;repo.cache(selected,items);showBoard(items,"更新于 "+time(System.currentTimeMillis()));}
                    public void failure(String why){if(request!=generation)return;if(cache.isEmpty()){status("暂时没读到榜单",why);content.addView(button("打开来源页后重试",()->login(new Item(selected,selected.label,selected==Source.WEIBO?"https://s.weibo.com/top/summary":selected.login,""))));}else showBoard(cache,"刷新失败 · 缓存 "+time(repo.cachedAt(selected)));}
                });
            }
        });
    }
    private static int platformColor(Source source){
        switch(source){
            case ZHIHU:return 0xff256dcc;
            case IFANR:return 0xff16816f;
            case CLS:return 0xff147f9e;
            case SMZDM:return 0xffc34e64;
            case WEIBO:return 0xffab650d;
            case HUPU:return 0xff8455ba;
            default:return 0xff147f9e;
        }
    }
    private static int blend(int base,int tint,float amount){
        return Color.rgb(Math.round(Color.red(base)*(1-amount)+Color.red(tint)*amount),Math.round(Color.green(base)*(1-amount)+Color.green(tint)*amount),Math.round(Color.blue(base)*(1-amount)+Color.blue(tint)*amount));
    }
    private String time(long millis) { return new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(millis)); }
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
    }
    private void showBoard(List<Item> items,String label) {
        if(!boardItems.isEmpty())rememberBoard();
        boardItems=repo.visibleItems(items);boardLabel=label;drawBoard(boardMarks.get(selected));
    }
    private void rememberBoard() {
        if(current!=null||savedPage||boardItems.isEmpty()||boardSource==null||scroll==null)return;
        BoardMark mark=new BoardMark();mark.position=scroll.getScrollY();
        if(mark.position>0)for(int i=0;i<content.getChildCount();i++){
            View card=content.getChildAt(i);
            if(card.getTag() instanceof String&&card.getBottom()>mark.position){mark.anchor=(String)card.getTag();mark.offset=card.getTop()-mark.position;break;}
        }
        boardMarks.put(boardSource,mark);
    }
    private void drawBoard(){drawBoard(null);}
    private void drawBoard(BoardMark mark) {
        if(content==null||current!=null)return;
        restoringBoard=true;
        int revision=++boardRevision,request=generation;
        content.removeAllViews();space(content,14);
        String name=selected==Source.AGGREGATE?"综合推荐 Top100":selected.label+" "+selected.category;
        android.text.SpannableString headingText=new android.text.SpannableString(name+(boardLabel.isEmpty()?"":"  （"+boardLabel+"）"));
        if(headingText.length()>name.length()){
            headingText.setSpan(new android.text.style.RelativeSizeSpan(.65f),name.length(),headingText.length(),0);
            headingText.setSpan(new android.text.style.ForegroundColorSpan(MUTED),name.length(),headingText.length(),0);
            headingText.setSpan(new android.text.style.StyleSpan(Typeface.NORMAL),name.length(),headingText.length(),0);
        }
        TextView heading=text("",17,INK);heading.setTypeface(null,Typeface.BOLD);heading.setText(headingText);heading.setLineSpacing(dp(4),1);content.addView(heading);space(content,16);
        if(selected==Source.AGGREGATE){TextView info=button("来源状态 · 排序说明",this::aggregateInfo);info.setBackgroundColor(Color.TRANSPARENT);info.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);info.setPadding(0,0,0,0);content.addView(info);}
        if(selected==Source.AGGREGATE){content.addView(text("综合热点优先 · 垂直内容补充 · 非真实热度换算",12,MUTED));space(content,10);}
        int rank=0;for(Item item:boardItems)card(item,++rank);
        if(boardItems.isEmpty()){TextView empty=text(selected==Source.AGGREGATE?(boardLabel.contains("获取中")?"正在汇集各平台，内容会陆续显示…":"暂未取得可用图文榜单，可查看来源状态或稍后刷新。") :"暂时没有可显示的图文条目，已开启视频过滤。可稍后刷新。",15,MUTED);empty.setPadding(0,dp(20),0,dp(20));content.addView(empty);}
        // Restore after layout, using an item identity so fresh rankings do not shift the reader's place.
        // A newer filter/render or navigation invalidates this one-shot callback.
        ScrollView target=scroll;LinearLayout entries=content;
        target.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            @Override public boolean onPreDraw(){
                if(target.getViewTreeObserver().isAlive())target.getViewTreeObserver().removeOnPreDrawListener(this);
                if(scroll!=target||current!=null||generation!=request||boardRevision!=revision)return true;
                int position=mark==null?0:mark.position;
                if(mark!=null&&!mark.anchor.isEmpty())for(int i=0;i<entries.getChildCount();i++){
                    View card=entries.getChildAt(i);if(mark.anchor.equals(card.getTag())){position=card.getTop()-mark.offset;break;}
                }
                target.scrollTo(0,Math.max(0,position));restoringBoard=false;schedulePrediction();return true;
            }
        });
        target.setOnScrollChangeListener((v,x,y,ox,oy)->{
            scrollBackwards=y<oy;schedulePrediction();
            if(!restoringBoard&&platformNavigation!=null&&scroll==target&&current==null){
                float density=getResources().getDisplayMetrics().density;
                boolean hidden=navigationScroll.update(y/density,(y-oy)/density);
                if(hidden!=(platformNavigation.getTag()==Boolean.TRUE)){
                    platformNavigation.setTag(hidden);
                    platformNavigation.setImportantForAccessibility(hidden?View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS:View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    platformNavigation.animate().translationY(hidden?dp(48):0).setDuration(160).start();
                }
            }
        });
    }
    private void schedulePrediction(){preloadHandler.removeCallbacks(predict);preloadHandler.postDelayed(predict,200);}
    private void predictArticles(){
        if(current!=null||savedPage||wasStopped||scroll==null||content==null||repo.offline())return;
        List<Item> candidates=new ArrayList<>();int top=scroll.getScrollY(),bottom=top+scroll.getHeight();
        // The visible rows plus one viewport in the current direction, never the whole feed.
        for(int i=0;i<content.getChildCount();i++){
            View row=content.getChildAt(i);Object tag=row.getTag();if(!(tag instanceof String))continue;
            if(row.getBottom()<=Math.max(0,top-(scrollBackwards?scroll.getHeight():0))||row.getTop()>=bottom+(scrollBackwards?0:scroll.getHeight()))continue;
            for(Item item:boardItems)if(item.url.equals(tag)){candidates.add(item);break;}
        }
        if(scrollBackwards)Collections.reverse(candidates);preloader.offer(candidates);
    }
    private void card(Item item,int rank) {
        LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.TOP);card.setPadding(0,dp(5),0,dp(5));card.setMinimumHeight(dp(48));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=dp(2);content.addView(card,cp);
        if(rank>0){int color=rank==1?(dark?0xffed827b:0xffb4423c):rank==2?(dark?0xffdfaa72:0xff995c24):rank==3?(dark?0xffcfba7b:0xff826b26):MUTED;TextView number=text(rank+".",13,color);number.setPadding(0,dp(2),0,0);card.addView(number,new LinearLayout.LayoutParams(dp(((selected==Source.AGGREGATE&&current==null)||rank>99?27:20)*getResources().getConfiguration().fontScale),-2));}
        LinearLayout body=column();card.addView(body,new LinearLayout.LayoutParams(0,-2,1));
        boolean aggregateCard=rank>0&&current==null&&selected==Source.AGGREGATE;
        if(aggregateCard){TextView source=text(item.detail.isEmpty()?item.source.label:item.detail,12,MUTED);source.setLineSpacing(dp(2),1);body.addView(source);space(body,4);}
        TextView title=text(item.title,14,INK);title.setLineSpacing(0,1);
        if(rank==0&&current!=null&&current.source==Source.DOUBAN){title.setMaxLines(3);title.setEllipsize(android.text.TextUtils.TruncateAt.END);}
        body.addView(title);
        String metric=item.source==Source.ZHIHU?item.detail.replaceFirst("\\s*热度$",""):item.detail;
        TextView measured=text(metric,12,MUTED);
        boolean sideMetric=rank>0&&item.detail.length()<=14&&!item.detail.contains("·")&&!item.detail.contains("元")&&getResources().getConfiguration().fontScale<=1.3f&&measured.getPaint().measureText(metric)<=dp(46);
        if(!aggregateCard&&!item.detail.trim().isEmpty()){
            TextView meta=text(sideMetric?metric:item.detail,12,MUTED);
            if(sideMetric){meta.setGravity(Gravity.RIGHT);meta.setPadding(dp(8),dp(3),0,0);card.addView(meta,new LinearLayout.LayoutParams(dp(54),-2));}
            else{space(body,5);meta.setLineSpacing(dp(2),1);body.addView(meta);}
        }
        card.setTag(item.url);card.setOnClickListener(v->{if(current==null&&selected==Source.AGGREGATE)openAggregate(item);else open(item,true);}); card.setContentDescription(item.title+"，"+item.detail+"，进入阅读");
        if(savedPage&&current==null)card.setOnLongClickListener(v->{
            new AlertDialog.Builder(this).setTitle("移除收藏？").setMessage(item.title).setNegativeButton("取消",null).setPositiveButton("移除",(dialog,which)->{saved.removeIf(entry->entry.url.equals(item.url));persistSaved();bookmarks();}).show();return true;
        });
    }
    private void aggregateInfo(){new AlertDialog.Builder(this).setTitle(Source.aggregateSources().length+"个平台 · 来源与排序").setMessage(aggregateDetail).setPositiveButton("知道了",null).show();}
    private void aggregateLogin(){Source[] sources=Source.aggregateSources();new AlertDialog.Builder(this).setTitle("选择来源 / 登录").setItems(Arrays.stream(sources).map(s->s.label).toArray(String[]::new),(d,n)->{Source s=sources[n];login(new Item(s,s.label,s.login,""));}).show();}
    private void openAggregate(Item item){
        AggregateRanker.Entry entry=aggregateEntries.get(item.url);
        if(entry==null||entry.alternatives.size()<2){open(entry==null?item:entry.primary,true);return;}
        List<Item> alternatives=new ArrayList<>(entry.alternatives);
        new AlertDialog.Builder(this).setTitle("选择阅读来源").setItems(alternatives.stream().map(i->i.source.label+" · "+i.title).toArray(String[]::new),(d,n)->open(alternatives.get(n),true)).show();
    }
    private void open(Item item,boolean push) {
        rememberReading();openPosition=-1;pendingAnchor=-1;
        closeAnswers();expanded.clear();
        if(!UrlPolicy.belongs(item.source,item.url)) { toast("来源地址不受支持"); return; }
        rememberBoard();
        if(push&&current!=null) history.push(current);
        current=item; reading=null; generation++; final int request=generation;
        ReadingMark mark=marks.get(item.url);if(mark!=null){expanded.addAll(mark.expanded);openPosition=mark.position;}
        primaryPending=mark==null;preloadHandler.removeCallbacks(predict);
        Document cached=repo.cachedArticle(item);if(cached!=null||repo.hiddenVideo(item))preloader.pause();
        boolean promoted=cached==null&&!repo.hiddenVideo(item)&&preloader.promote(item,new Repository.Result<Document>(){
            public void success(Document doc){if(request==generation)render(doc);}
            public void failure(String why){if(request==generation){status("暂时没读到正文",why);content.addView(button("重试",()->open(item,false)));content.addView(button("来源 / 登录",()->login(item)));}}
        });
        frame(item.source.label+" / 阅读","news"); scroller(); status("正在整理内容…","只提取内容，不加载原站的推荐和操作界面");
        readerFooter();
        if(repo.hiddenVideo(item)){Document hidden=new Document();hidden.url=item.url;hidden.title=item.title;hidden.filteredVideo=true;render(hidden);return;}
        if(cached!=null){render(cached);return;}
        if(promoted)return;
        if(repo.offline()){status("当前没有网络","此篇正文尚未缓存在本机。连接网络后可重试。");content.addView(button("重试",()->open(item,false)));return;}
        if(item.source==Source.ZHIHU||(item.source==Source.TIEBA&&item.url.contains("/p/"))){dynamic(item,request);return;}
        repo.article(item,new Repository.Result<Document>() {
            public void success(Document doc) { if(request!=generation)return; if(doc.title.trim().isEmpty()) doc.title=item.title; if(doc.canPresent())render(doc);else dynamic(item,request); }
            public void failure(String reason) { if(request==generation)dynamic(item,request); }
        });
    }
    private void dynamic(Item item,int request) {
        status("正在加载动态内容…","使用本机登录状态读取，不会自动打开其他 App");
        dynamic=new DynamicReader(this,item,new Repository.Result<Document>() {
            public void success(Document doc) {
                if(request!=generation)return;
                WebView source=!doc.filteredVideo&&(doc.hasContent()||doc.unsupportedVideo||doc.filteredVideos>0)&&dynamic!=null?dynamic.takeSource():null;
                try {
                    // frame() replaces the native root, so reattach the transferred page afterward.
                    render(doc);
                    if(source!=null) { answers=new AnswerStream(MainActivity.this,item,source);source=null; }
                } finally { if(source!=null)SourceSurface.release(source); }
            }
            public void failure(String reason) { if(request!=generation)return; status("需要来源页面协助",reason); content.addView(button("登录 / 加载后读取",()->login(item))); }
        });
    }
    private void readerFooter() {
        // Reading uses the compact top bar; no persistent action tray obscures content.
    }
    private void render(Document doc) {
        if(doc.filteredVideo){
            repo.rememberVideo(current);reading=null;goBack();toast("已从列表移除视频主题");return;
        }
        if(primaryPending){ReaderHtml.expandPrimary(doc,expanded);primaryPending=false;}
        repo.cacheArticle(doc);
        // A question's video description is not an answer. Keep the answer reader and
        // continuation entry even when its first snapshot contains no textual blocks.
        if(doc.hasContent()||((doc.unsupportedVideo||doc.filteredVideos>0)&&AnswerStream.sameQuestion(current,doc.url))) {
            frame(current.source.label+" / 阅读","news"); reading=doc;
            readerStatus=text("",12,MUTED);readerStatus.setPadding(0,0,0,dp(8));readerStatus.setVisibility(View.GONE);readerStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(readerStatus);
            readerWeb=new WebView(this);readerWeb.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY); Theme.configureWeb(readerWeb,false); readerWeb.getSettings().setJavaScriptEnabled(false);
            // Older WebView compositors can leave black tiles over text after zoom/repaint.
            if(android.os.Build.VERSION.SDK_INT<=27)readerWeb.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            readerWeb.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            readerWeb.getSettings().setAllowFileAccess(false); readerWeb.getSettings().setAllowContentAccess(false); readerWeb.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
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
                    else if(dest.equals(ReaderHtml.ACTION+"login"))login(current);
                    else if(dest.equals(readerPageUrl))return false;
                    else if(dest.equals(doc.url))login(current);
                    else if(dest.equals(doc.nextUrl)&&UrlPolicy.sameThread(current.source,doc.url,dest))open(new Item(current.source,doc.title,dest,"下一页"),true);
                    else if(reading!=null&&reading.blocks.stream().anyMatch(b->b.type.equals("image")&&b.value.equals(dest)))zoom(dest,reading.url);
                    else if(req.isForMainFrame()&&req.hasGesture()&&ReaderLinks.allowed(dest))openContentLink(dest);
                    return true;
                }
                @Override public void onPageFinished(WebView web,String url){if(web!=readerWeb||!url.equals(readerPageUrl))return;if(!streamAnchor.isEmpty()){int offset=streamAnchorOffset;streamAnchor="";int revision=readerRevision;web.postVisualStateCallback(revision,new WebView.VisualStateCallback(){@Override public void onComplete(long id){if(web==readerWeb&&revision==readerRevision)restoreWebPosition(web,web.getScrollY()+offset);}});}else if(openPosition>=0){int pos=openPosition;openPosition=-1;restorePosition(pos);}}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req) {
                    String url=req.getUrl().toString();
                    if(req.isForMainFrame()&&url.split("#",2)[0].equals(readerPageUrl.split("#",2)[0]))return new WebResourceResponse("text/html","UTF-8",200,"OK",java.util.Collections.singletonMap("Cache-Control","no-store"),new ByteArrayInputStream(readerHtml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    Document active=reading;if(active==null||!active.containsImage(url))return new WebResourceResponse("text/plain","utf-8",new ByteArrayInputStream(new byte[0]));
                    try { return imageResponse(url,doc.url); }
                    catch(Exception e) { return new WebResourceResponse("text/plain","utf-8",404,"Unavailable",java.util.Collections.emptyMap(),new ByteArrayInputStream(new byte[0])); }
                }
            });
            FrameLayout readerArea=new FrameLayout(this);readerArea.addView(readerWeb,new FrameLayout.LayoutParams(-1,-1));
            jumpButton=button("下一条 ↓",this::jumpNext);jumpButton.setTextSize(12);jumpButton.setVisibility(View.GONE);
            FrameLayout.LayoutParams jumpSize=new FrameLayout.LayoutParams(-2,dp(44),Gravity.BOTTOM|Gravity.END);jumpSize.bottomMargin=dp(12);readerArea.addView(jumpButton,jumpSize);
            root.addView(readerArea,new LinearLayout.LayoutParams(-1,0,1)); readerFooter();
            readerWeb.setOnLongClickListener(v->{WebView.HitTestResult hit=readerWeb.getHitTestResult();String url=hit==null?null:hit.getExtra();if(url==null||!ReaderLinks.allowed(url)||reading.containsImage(url)||url.startsWith(ReaderHtml.ACTION))return false;
                new AlertDialog.Builder(this).setTitle("链接地址").setMessage(url).setNegativeButton("取消",null).setNeutralButton("复制链接",(d,n)->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(android.content.ClipData.newPlainText("链接",url));toast("已复制链接");}).setPositiveButton("打开",(d,n)->openContentLink(url)).show();return true;});
            reloadReader(doc);
            readerWeb.setOnScrollChangeListener((v,x,y,ox,oy)->{
                if(jumpButton!=null)jumpButton.setVisibility(y>dp(160)&&y<oy&&reading!=null&&ReaderHtml.sections(reading).size()>1?View.VISIBLE:View.GONE);
                if(System.currentTimeMillis()>suppressAutoUntil&&current!=null&&current.source==Source.ZHIHU&&!loadingMore&&!morePaused&&y>oy&&y>dp(100)&&readerWeb!=null&&readerWeb.getContentHeight()*getResources().getDisplayMetrics().density-y-readerWeb.getHeight()<dp(80))loadMore();});
            return;
        }
        reading=doc; content.removeAllViews(); scroll.scrollTo(0,0);
        TextView title=text(doc.title,26,INK); title.setTypeface(null,Typeface.BOLD); title.setLineSpacing(dp(5),1); content.addView(title); space(content,14);
        content.addView(text(current.source.label+" · "+(doc.unsupportedVideo?"视频内容":doc.hasContent()?"已提取页面内容":"话题内容"),12,MUTED)); space(content,22);
        if(!doc.notice.trim().isEmpty()) { TextView warning=text(doc.notice,13,dark?0xffe2c995:0xff856234); warning.setPadding(dp(14),dp(12),dp(14),dp(12)); warning.setBackground(shape(dark?0xff342e22:0xfff5eddf,12)); content.addView(warning); space(content,16); }
        if(!doc.hasContent()&&!doc.related.isEmpty()) {
            content.addView(text("选择一篇内容继续阅读",15,MUTED)); space(content,16);
            for(Item item:repo.visibleItems(doc.related)) card(item,0);
        }
        if(doc.sourceUnavailable)content.addView(button("重试",()->open(current,false)));
        if(doc.filteredVideos>0&&!doc.hasContent()&&doc.related.isEmpty())content.addView(button("返回图文列表",this::goBack));
        if(doc.sourceUnavailable||doc.filteredVideos==0||doc.hasContent()||!doc.related.isEmpty())content.addView(button("来源页 / 登录后重新读取",()->login(current)));
    }
    private WebResourceResponse imageResponse(String url,String referer)throws Exception {
        if(SourceParser.isImagePlaceholder(url))throw new java.io.IOException("Placeholder is not article content");
        byte[] data=images.get(url);if(data==null){data=Repository.bytes(url,null,referer,8*1024*1024);if(ImageType.detect(data)!=null)images.put(url,data);}
        String mime=ImageType.detect(data);
        if(mime==null)throw new java.io.IOException("Not a supported image");
        return new WebResourceResponse(mime,null,new ByteArrayInputStream(data));
    }
    private void openContentLink(String url){
        Source source=ReaderLinks.readerSource(url);if(source!=null){
            open(new Item(source,"链接内容",url,"正文链接"),true);return;
        }
        try {startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),"打开链接"));}
        catch(android.content.ActivityNotFoundException e){toast("没有可打开此链接的浏览器");}
    }
    private void zoom(String url,String referer) {
        if(imagePreview!=null)imagePreview.dismiss();
        // Keep list/reader thumbnails light; request the observed larger rendition only on tap.
        String previewUrl=current==null?url:WeiboImage.preview(current.source,url);
        imagePreview=new ImagePreview(this,previewUrl,dark,()->imageResponse(previewUrl,referer),()->images.remove(previewUrl));imagePreview.show();
    }
    private void login(Item item) {
        if(item==null)return;
        // A pre-login read must not finish late and restore an anonymous snapshot.
        generation++;preloadHandler.removeCallbacks(predict);preloader.pause();repo.cancelPending();aggregateLoader.cancel();closeAnswers();
        if(dynamic!=null){dynamic.close();dynamic=null;}
        if(dynamicBoard!=null){dynamicBoard.close();dynamicBoard=null;}
        LoginActivity.pendingDocument=null;
        Intent i=new Intent(this,LoginActivity.class); i.putExtra("source",item.source.name()); i.putExtra("url",item.url);i.putExtra("board",current==null&&!savedPage); startActivityForResult(i,100);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==101){recreate();return;}
        if(request==100){
            wasStopped=false; // The result owns this refresh; onResume must not start a duplicate.
            CookieManager.getInstance().flush();preloader.pause();repo.invalidateArticles();closeAnswers();
        }
        if(request==100&&result==RESULT_OK&&data!=null&&LoginActivity.pendingDocument!=null) {
            Document doc=LoginActivity.pendingDocument; LoginActivity.pendingDocument=null;
            rememberBoard();
            Source source=Source.valueOf(data.getStringExtra("source")); current=new Item(source,doc.title,doc.url,"");
            generation++;primaryPending=true; frame(source.label+" / 阅读","news"); scroller(); readerFooter(); render(doc);
        } else if(request==100){
            LoginActivity.pendingDocument=null;
            // Back/close returns RESULT_CANCELED even after a successful website login.
            // Always re-read with the shared WebView session, without assuming login succeeded.
            if(current!=null)open(current,false);
            else if(!savedPage)home(true);
        }
    }
    private void save() {
        if(current==null)return;
        for(Item item:saved) if(item.url.equals(current.url)) { toast("已在收藏中"); return; }
        saved.add(0,current);persistSaved(); toast("已收藏链接");
    }
    private void persistSaved() {getPreferences(0).edit().putString("saved",Models.toJson(saved).toString()).apply();}
    private void bookmarks() { rememberReading();closeAnswers();generation++; current=null; reading=null;history.clear(); savedPage=true; frame("SAVED / 本机收藏","稍后慢慢读"); scroller(); if(saved.isEmpty())status("还没有收藏","阅读页面可保存链接，收藏只留在本机。");else {content.addView(text("长按条目可移除收藏",12,MUTED));space(content,12);for(Item item:saved)card(item,0);}root.addView(button("返回热榜",()->home(false))); }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }
    private void goBack() { if(!history.isEmpty())open(history.pop(),false);else if(savedPage&&current!=null)bookmarks();else home(false); }
    @Override public void onBackPressed() { if(current!=null||savedPage)goBack();else super.onBackPressed(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        rememberBoard();Bundle boards=new Bundle();
        for(Map.Entry<Source,BoardMark> entry:boardMarks.entrySet()){
            BoardMark mark=entry.getValue();Bundle value=new Bundle();value.putString("anchor",mark.anchor);
            value.putInt("position",mark.position);value.putInt("offset",mark.offset);boards.putBundle(entry.getKey().name(),value);
        }
        out.putBundle("boardMarks",boards);
        out.putString("source",selected.name());out.putBoolean("savedPage",savedPage);
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
    private void jumpNext(){
        WebView web=readerWeb;if(web==null)return;int request=generation,revision=readerRevision;
        if(!readerPageUrl.equals(web.getUrl()))return;
        java.util.concurrent.atomic.AtomicBoolean done=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> finish=value->{if(!done.compareAndSet(false,true)||web!=readerWeb)return;web.getSettings().setJavaScriptEnabled(false);if(request!=generation||revision!=readerRevision)return;try{int index=Integer.parseInt(value);if(index<0)return;if(index+1<ReaderHtml.sections(reading).size())jumpTo(index+1);else if(current.source==Source.ZHIHU)loadMore();else toast("已是最后一条");}catch(Exception ignored){}};
        web.getSettings().setJavaScriptEnabled(true);
        web.evaluateJavascript("(function(){var a=document.querySelectorAll('section.part');for(var i=0;i<a.length;i++)if(a[i].getBoundingClientRect().bottom>1)return i;return -1;})()",finish::accept);
        web.postDelayed(()->finish.accept("-1"),500);
    }
    private void changeFont(int next){
        int old=font;font=next;getPreferences(0).edit().putInt("font",font).apply();
        if(readerWeb!=null){
            suppressAutoUntil=System.currentTimeMillis()+1500;
            if(reading!=null&&reading.blocks.stream().anyMatch(b->!b.inlineImages.isEmpty())){
                atReadingPosition(()->reloadReader(reading),()->{});
            }else readerWeb.getSettings().setTextZoom(Math.round(readerWeb.getSettings().getTextZoom()*next/(float)old));
        }
    }
    private void reloadReader(Document doc){
        suppressAutoUntil=System.currentTimeMillis()+1500;
        if(readerWeb!=null){readerWeb.getSettings().setTextZoom(Math.round(font*100f/19));readerHtml=ReaderHtml.render(doc,current.source,19,dark,expanded,loadingMore,font,Theme.style(this));readerPageUrl=ReaderHtml.ACTION+"?render="+System.nanoTime()+"-"+(++readerRevision)+(!streamAnchor.isEmpty()?"#"+streamAnchor:pendingAnchor>=0?"#part-"+pendingAnchor:"");pendingAnchor=-1;readerWeb.loadUrl(readerPageUrl);}
    }
    /** Read only our escaped, CSP-protected document. No remote scripts or native bridge are enabled. */
    private void appendAtReadingPosition(Document doc,int request,Set<String> previousAnswerIds){
        if(request!=generation)return;
        Runnable commit=()->{
            loadingMore=false;morePaused=!AnswerStream.hasNewAnswers(doc,previousAnswerIds)||!doc.moreStatus.equals("loaded");reading=doc;repo.cacheArticle(doc);
            if(readerStatus!=null)readerStatus.setText(doc.moreStatus.equals("login")?"知乎要求登录。请用右上角菜单「来源 / 登录」后重新读取。":!doc.moreStatus.equals("loaded")?doc.notice:morePaused?"本次没有新回答，不能据此确认已读完。可点底部按钮重试。":"已追加回答 · 共 "+AnswerStream.answers(doc)+" 条");
            reloadReader(doc);
            toast(doc.moreStatus.equals("login")?"知乎要求登录，未能加载下一批；已读回答保留":morePaused?doc.notice:"已追加 "+(AnswerStream.answerIds(doc).stream().filter(id->!previousAnswerIds.contains(id)).count())+" 条回答");
        };
        atReadingPosition(commit,()->{int pos=readerWeb==null?0:readerWeb.getScrollY();streamAnchor="";commit.run();restorePosition(pos);});
    }
    private void atReadingPosition(Runnable apply,Runnable stale){
        WebView web=readerWeb;if(web==null)return;
        int request=generation,revision=readerRevision,pos=web.getScrollY();String page=readerPageUrl;
        java.util.concurrent.atomic.AtomicBoolean applied=new java.util.concurrent.atomic.AtomicBoolean();
        java.util.function.Consumer<String> finish=value->{
            if(!applied.compareAndSet(false,true)||web!=readerWeb)return;
            web.getSettings().setJavaScriptEnabled(false);
            if(request!=generation)return;
            if(revision!=readerRevision){stale.run();return;}
            streamAnchor="";
            try{org.json.JSONObject anchor=new org.json.JSONObject(value);String id=anchor.getString("id");if(id.matches("reading-[A-Za-z0-9_-]+")){streamAnchor=id;streamAnchorOffset=Math.round((float)-anchor.getDouble("top")*web.getScale());}}catch(Exception ignored){}
            boolean anchored=!streamAnchor.isEmpty();apply.run();if(!anchored)restorePosition(pos);
        };
        if(!page.equals(web.getUrl())||!page.startsWith(ReaderHtml.ACTION+"?render=")){finish.accept("");return;}
        // evaluateJavascript needs this switch; CSP default-src 'none' still forbids document scripts.
        // The fixed expression reads positions only, and scripting is switched off before any reload.
        web.getSettings().setJavaScriptEnabled(true);
        web.evaluateJavascript("(function(){var a=document.querySelectorAll('[data-reading-anchor]');for(var i=0;i<a.length;i++){var r=a[i].getBoundingClientRect();if(r.bottom>0&&r.top<innerHeight)return {id:a[i].id,top:r.top};}return null;})()",finish::accept);
        web.postDelayed(()->finish.accept(""),500);
    }
    private void rememberReading(){if(current!=null&&reading!=null&&readerWeb!=null){ReadingMark mark=new ReadingMark();mark.expanded=new HashSet<>(expanded);mark.position=readerWeb.getScrollY();marks.put(current.url,mark);}}
    private void closeAnswers(){if(answers!=null){answers.close();answers=null;}loadingMore=false;morePaused=false;}
    private void loadMore(){
        if(loadingMore||reading==null||current==null||current.source!=Source.ZHIHU)return;
        if(repo.offline()){morePaused=true;if(readerStatus!=null){readerStatus.setVisibility(View.VISIBLE);readerStatus.setText("当前没有网络，已读回答保留。联网后点「加载下一批回答」重试。");}return;}
        loadingMore=true;int request=generation;Document before=reading;Set<String> previousAnswerIds=AnswerStream.answerIds(before);
        if(readerStatus!=null){readerStatus.setVisibility(View.VISIBLE);readerStatus.setText("正在加载下一批回答…你可以继续阅读已加载内容");}
        toast("正在加载下一批回答，已读内容会保留");
        if(answers==null)answers=new AnswerStream(this,current);
        answers.more(before,new Repository.Result<Document>(){
            public void success(Document doc){if(request!=generation)return;appendAtReadingPosition(doc,request,previousAnswerIds);}
            public void failure(String why){if(request!=generation)return;loadingMore=false;morePaused=true;if(readerStatus!=null){readerStatus.setVisibility(View.VISIBLE);readerStatus.setText("续读失败，已读回答保留。"+why+" 可点底部按钮重试。");}toast(why);}
        });
    }
    @Override protected void onStop(){wasStopped=true;preloadHandler.removeCallbacks(predict);if(preloader!=null)preloader.pause();if(aggregateLoader!=null)aggregateLoader.cancel();super.onStop();}
    @Override public void onTrimMemory(int level){super.onTrimMemory(level);if(level>=TRIM_MEMORY_RUNNING_LOW){images.evictAll();if(repo!=null)repo.invalidateArticles();if(preloader!=null)preloader.trim();}}
    @Override protected void onResume(){super.onResume();boolean resume=wasStopped;wasStopped=false;if(current==null){if(resume&&selected==Source.AGGREGATE)home(false);else schedulePrediction();}else if(resume&&reading==null&&dynamic==null)open(current,false);}
    @Override protected void onDestroy() { generation++;preloadHandler.removeCallbacksAndMessages(null);if(preloader!=null)preloader.close();closeAnswers(); if(imagePreview!=null)imagePreview.dismiss();if(dynamic!=null)dynamic.close();if(dynamicBoard!=null)dynamicBoard.close();if(readerWeb!=null)readerWeb.destroy();aggregateLoader.close();repo.close(); super.onDestroy(); }
}
