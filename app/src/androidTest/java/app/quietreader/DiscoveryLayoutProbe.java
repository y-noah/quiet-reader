package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.util.*;

/** Uses the emulator's actual display/font configuration; synthetic content, no network. */
final class DiscoveryLayoutProbe {
    private final Instrumentation test;private final boolean dark;
    private Activity activity;private View dialogRoot;private int checks,failures;private final StringBuilder report=new StringBuilder();
    DiscoveryLayoutProbe(Instrumentation test,boolean dark){this.test=test;this.dark=dark;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View view,String label){if(view instanceof TextView&&(label.contentEquals(((TextView)view).getText())||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=find(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private View navigation(View view,String label){if(view instanceof TextView&&view.isClickable()&&(label.contentEquals(((TextView)view).getText())||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=navigation(((ViewGroup)view).getChildAt(i),label);if(found!=null)return found;}return null;}
    private ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){ListView found=list(((ViewGroup)view).getChildAt(i));if(found!=null)return found;}return null;}
    private void texts(View view,List<TextView> out){if(view.getVisibility()!=View.VISIBLE)return;if(view instanceof TextView)out.add((TextView)view);if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)texts(((ViewGroup)view).getChildAt(n),out);}
    private void inspect(String page)throws Exception{
        test.waitForIdleSync();Thread.sleep(200);
        test.runOnMainSync(()->{
            List<TextView> values=new ArrayList<>();texts(dialogRoot==null?activity.getWindow().getDecorView():dialogRoot,values);boolean vertical=true,horizontal=true;int measured=0;
            for(TextView value:values){android.text.Layout layout=value.getLayout();if(layout==null||layout.getLineCount()==0||value.getText().length()==0)continue;measured++;
                int height=value.getHeight()-value.getCompoundPaddingTop()-value.getCompoundPaddingBottom(),width=value.getWidth()-value.getCompoundPaddingLeft()-value.getCompoundPaddingRight();
                if(layout.getLineBottom(Math.min(value.getMaxLines(),layout.getLineCount())-1)>height+2){vertical=false;report.append("CLIPPED HEIGHT ").append(value.getText()).append('\n');}
                for(int n=0;n<layout.getLineCount();n++)if(layout.getLineMax(n)>width+2){horizontal=false;report.append("CLIPPED WIDTH available=").append(width).append(" actual=").append(layout.getLineMax(n)).append(" ").append(value.getText()).append('\n');break;}
            }
            check(measured>5&&vertical,page+" text fits allocated height");check(horizontal,page+" text fits allocated width");
            if("sources".equals(page)){ListView list=list(activity.getWindow().getDecorView());check(list.getFirstVisiblePosition()==0&&list.getChildAt(0)!=null&&list.getChildAt(0).getTop()>=0,"opening sources resets the prior feed scroll offset");}
            if(dialogRoot==null){
                View root=activity.getWindow().getDecorView();float density=activity.getResources().getDisplayMetrics().density;
                for(String label:new String[]{page.startsWith("feed")?"设置":"返回","●"}){View target=navigation(root,label);android.graphics.Rect rect=new android.graphics.Rect();check(target!=null&&target.getGlobalVisibleRect(rect)&&rect.height()>=48*density-1&&rect.width()>=48*density-1,page+" toolbar target visible and at least 48dp: "+label);}
                check(find(root,"稍后读")==null&&find(root,"发现值得读的")==null&&find(root,"选择兴趣")==null,"home has no saved navigation, oversized heading or interest banner");
                check(find(root,"科技")==null&&find(root,"社会")==null&&find(root,"文化")==null,"home has no category navigation");
                boolean clean=true;for(TextView value:values){String text=value.getText().toString();if(text.matches("\\d{2}")||text.equals("按来源内名次排列"))clean=false;}check(clean,"cards have no rank numbers or inline ranking explanation");
                if("feed".equals(page)){int[] listAt={0,0},topAt={0,0};list(root).getLocationOnScreen(listAt);navigation(root,"设置").getLocationOnScreen(topAt);check(listAt[1]-topAt[1]<=Math.round(58*density),"first candidate starts directly below one compact toolbar");}
            }
        });
        android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();if(screenshot==null)throw new IOException("Screenshot absent");
        android.content.res.Configuration config=activity.getResources().getConfiguration();String name="layout-"+config.screenWidthDp+"-"+Math.round(config.fontScale*100)+(dark?"-dark-":"-light-")+page+".png";
        try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),name))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{screenshot.recycle();}
        report.append("SCREENSHOT ").append(name).append('\n');
    }
    private void compactCards()throws Exception{
        test.runOnMainSync(()->{
            ListView view=list(activity.getWindow().getDecorView());android.widget.ListAdapter adapter=view.getAdapter();
            LinearLayout wrapper=(LinearLayout)adapter.getView(1,null,view);
            measureCard(wrapper,view.getWidth(),"long preview card");
            wrapper=(LinearLayout)adapter.getView(0,wrapper,view);
            measureCard(wrapper,view.getWidth(),"short card recycled from long preview");
            LinearLayout body=(LinearLayout)((LinearLayout)wrapper.getChildAt(0)).getChildAt(0);
            check(body.getChildAt(1).getVisibility()==View.GONE,"recycled empty preview has no reserved view");
            List<TextView> labels=new ArrayList<>();texts(activity.getWindow().getDecorView(),labels);check(labels.stream().noneMatch(v->"New".contentEquals(v.getText())||"New".contentEquals(v.getContentDescription()==null?"":v.getContentDescription())),"home has no in-app brand or replacement label");
        });
    }
    private void measureCard(LinearLayout wrapper,int width,String name){
        wrapper.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));wrapper.layout(0,0,width,wrapper.getMeasuredHeight());
        LinearLayout card=(LinearLayout)wrapper.getChildAt(0),body=(LinearLayout)card.getChildAt(0);View title=body.getChildAt(0),menu=card.getChildAt(1);
        check(body.getTop()+title.getTop()==card.getPaddingTop(),name+" title begins at card padding without a blank top area");
        check(card.getHeight()<=Math.max(body.getHeight(),menu.getHeight())+card.getPaddingTop()+card.getPaddingBottom()+1,name+" height follows actual content and menu touch target");
    }
    private void accessibilityClick(String label)throws Exception{
        test.runOnMainSync(()->{
            View target=navigation(activity.getWindow().getDecorView(),label);
            android.view.accessibility.AccessibilityNodeInfo node=target.createAccessibilityNodeInfo();
            check("android.widget.Button".contentEquals(node.getClassName()),"button accessibility role: "+label);
            check(node.isFocusable()&&node.isClickable(),"button is focusable and clickable: "+label);
            check(target.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null),"standard accessibility click accepted: "+label);
        });
        test.waitForIdleSync();
    }
    private android.view.accessibility.AccessibilityNodeInfo named(android.view.accessibility.AccessibilityNodeInfo node,String label){
        if(node==null)return null;if(label.contentEquals(node.getText()==null?"":node.getText()))return node;
        for(int n=0;n<node.getChildCount();n++){android.view.accessibility.AccessibilityNodeInfo found=named(node.getChild(n),label);if(found!=null)return found;}return null;
    }
    private boolean scrollMenu(android.view.accessibility.AccessibilityNodeInfo node){
        if(node==null)return false;if(node.isScrollable()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;
        for(int n=0;n<node.getChildCount();n++)if(scrollMenu(node.getChild(n)))return true;return false;
    }
    private void settingsItem(String label)throws Exception{
        accessibilityClick("设置");selectSettingsItem(label);
    }
    private void selectSettingsItem(String label)throws Exception{
        long until=SystemClock.elapsedRealtime()+5000;boolean clicked=false;
        while(SystemClock.elapsedRealtime()<until){android.view.accessibility.AccessibilityNodeInfo root=test.getUiAutomation().getRootInActiveWindow(),node=named(root,label);if(node!=null&&node.isVisibleToUser()){while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){clicked=true;break;}}scrollMenu(root);Thread.sleep(150);}
        check(clicked,"settings item is reachable using an accessibility action: "+label);test.waitForIdleSync();
    }
    private void sourceSettings()throws Exception{settingsItem("内容来源");}
    private void interestLayout()throws Exception{
        settingsItem("阅读偏好");selectSettingsItem("从热门话题选择兴趣");test.waitForIdleSync();
        java.lang.reflect.Field field=DiscoveryActivity.class.getDeclaredField("setupDialog");field.setAccessible(true);AlertDialog dialog=(AlertDialog)field.get(activity);
        check(dialog!=null&&dialog.isShowing(),"interest chooser opens through its actual settings entry");if(dialog==null)return;
        try{
            dialogRoot=dialog.getWindow().getDecorView();inspect("interest-top");
            test.runOnMainSync(()->{
                float density=activity.getResources().getDisplayMetrics().density;
                for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){
                    Button button=dialog.getButton(id);android.graphics.Rect rect=new android.graphics.Rect();check(button.getGlobalVisibleRect(rect)&&rect.width()>=48*density-1&&rect.height()>=48*density-1,"interest action fully visible and at least 48dp: "+button.getText());
                }
            });
            java.lang.reflect.Field scrollField=DiscoveryActivity.class.getDeclaredField("setupScroll");scrollField.setAccessible(true);ScrollView scroll=(ScrollView)scrollField.get(activity);
            test.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));test.waitForIdleSync();Thread.sleep(200);inspect("interest-bottom");
        }finally{test.runOnMainSync(dialog::cancel);dialogRoot=null;}
        accessibilityClick("返回");accessibilityClick("返回");
    }
    void run(){String database="test-ui-"+UUID.randomUUID()+".db";SharedPreferences prefs=test.getTargetContext().getSharedPreferences("appearance",0);boolean had=prefs.contains("mode");int old=prefs.getInt("mode",0);
        try{
            prefs.edit().putInt("mode",dark?2:1).commit();
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.ensureCandidatePolicy();long now=System.currentTimeMillis();List<HotCandidate> rows=new ArrayList<>();for(int n=0;n<60;n++)rows.add(new HotCandidate(Models.Source.ZHIHU,"https://www.zhihu.com/question/9898"+n,"长标题排版检查：怎样在更大的系统字号下清楚阅读完整的中文热门话题？ 第"+n+"条","这是一段用于验证大字号换行与摘要省略的合成内容，不代表真实平台新闻。","",false,n+1,null,now));
                rows.set(0,new HotCandidate(Models.Source.ZHIHU,rows.get(0).originalUrl,"武契奇承认败选","","",false,1,null,now));
                long revision=store.beginRefresh(Models.Source.ZHIHU);store.upsertSnapshot(Models.Source.ZHIHU,revision,rows,now);long snapshot=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"cold","all|SCIENCE",CandidateOrder.arrange(rows,new org.json.JSONArray(),false));store.activateSnapshot(snapshot);
            }
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            // Startup/migration runs on the storage executor. Inspect the actual
            // loaded frame, not a fixed delay that can capture an empty toolbar.
            long readyUntil=android.os.SystemClock.elapsedRealtime()+10000;boolean[] loaded={false};
            do{test.runOnMainSync(()->{try{java.lang.reflect.Field field=DiscoveryActivity.class.getDeclaredField("feed");field.setAccessible(true);loaded[0]=((List<?>)field.get(activity)).size()==60;}catch(Exception error){throw new IllegalStateException(error);}});if(loaded[0])break;Thread.sleep(40);}while(android.os.SystemClock.elapsedRealtime()<readyUntil);
            if(!loaded[0])throw new IllegalStateException("Layout candidate migration did not finish");
            // Acceptance queues active-snapshot persistence separately.
            boolean migrated=false;do{try(FeedStore store=new FeedStore(test.getTargetContext(),database)){org.json.JSONObject info=store.snapshotInfo(store.activeSnapshot());migrated=info!=null&&"all".equals(info.optString("filter"));}if(migrated)break;Thread.sleep(40);}while(android.os.SystemClock.elapsedRealtime()<readyUntil);
            android.content.res.Configuration config=activity.getResources().getConfiguration();report.append("CONFIG widthDp=").append(config.screenWidthDp).append(" fontScale=").append(config.fontScale).append(" dark=").append(dark).append('\n');
            inspect("feed");compactCards();
            android.graphics.Bitmap icon=android.graphics.Bitmap.createBitmap(432,432,android.graphics.Bitmap.Config.ARGB_8888);
            try{android.graphics.drawable.Drawable drawable=activity.getApplicationInfo().loadIcon(activity.getPackageManager());drawable.setBounds(0,0,432,432);drawable.draw(new android.graphics.Canvas(icon));try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"new-cow-icon-preview.png"))){icon.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}
            finally{icon.recycle();}
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){org.json.JSONObject info=store.snapshotInfo(store.activeSnapshot());check(info!=null&&"all".equals(info.optString("filter")),"upgrade strips the persisted category filter from the active snapshot");}
            test.runOnMainSync(()->{try{java.lang.reflect.Field field=DiscoveryActivity.class.getDeclaredField("feed");field.setAccessible(true);check(((List<?>)field.get(activity)).size()==60,"legacy category does not silently hide the cached candidate pool");}catch(Exception error){throw new IllegalStateException(error);}});
            int[] growthAnchor={-1,0};
            test.runOnMainSync(()->{ListView view=list(activity.getWindow().getDecorView());check(view.getCount()==40&&find(view,"查看更多话题")==null,"initial feed has forty rows and no manual more button");view.getAdapter().registerDataSetObserver(new android.database.DataSetObserver(){@Override public void onChanged(){if(view.getAdapter().getCount()==60&&growthAnchor[0]<0&&view.getChildAt(0)!=null){growthAnchor[0]=view.getFirstVisiblePosition();growthAnchor[1]=view.getChildAt(0).getTop();}}});view.setSelectionFromTop(37,0);});
            long growthUntil=android.os.SystemClock.elapsedRealtime()+5000;boolean[] grown={false};
            do{test.runOnMainSync(()->{ListView view=list(activity.getWindow().getDecorView());grown[0]=view.getCount()==60;});if(grown[0])break;Thread.sleep(40);}while(android.os.SystemClock.elapsedRealtime()<growthUntil);
            check(grown[0],"approaching the first window end automatically exposes the remaining topics");
            test.waitForIdleSync();
            test.runOnMainSync(()->{ListView view=list(activity.getWindow().getDecorView());report.append("GROWTH before=").append(growthAnchor[0]).append('/').append(growthAnchor[1]).append(" after=").append(view.getFirstVisiblePosition()).append('/').append(view.getChildAt(0).getTop()).append('\n');check(growthAnchor[0]>0&&view.getFirstVisiblePosition()==growthAnchor[0]&&view.getChildAt(0).getTop()==growthAnchor[1],"automatic feed growth retains the exact current row and pixel offset");view.setSelectionFromTop(59,0);});
            test.waitForIdleSync();test.runOnMainSync(()->{ListView view=list(activity.getWindow().getDecorView());check(view.getCount()==60&&find(view,"查看更多话题")==null,"exhausted feed has no repeated growth or manual footer");view.setSelectionFromTop(0,0);});test.waitForIdleSync();
            accessibilityClick("设置");inspect("settings");
            test.runOnMainSync(()->{ListView menu=list(activity.getWindow().getDecorView());check(menu.getCount()==5&&menu.getChildCount()==5,"all five grouped settings entries fit the actual viewport");List<TextView> labels=new ArrayList<>();texts(activity.getWindow().getDecorView(),labels);check(labels.stream().noneMatch(v->"设置".contentEquals(v.getText())),"settings does not repeat a visible settings title");});
            accessibilityClick("返回");interestLayout();
            test.runOnMainSync(()->{ListView list=list(activity.getWindow().getDecorView());check(list!=null&&list.getChildCount()>0,"actual cached candidate cards are rendered");list.scrollListBy(Math.round(150*activity.getResources().getDisplayMetrics().density));});
            test.waitForIdleSync();Thread.sleep(200);
            for(int attempt=0;attempt<8;attempt++){
                boolean[] reachable={false};test.runOnMainSync(()->{
                    List<TextView> labels=new ArrayList<>();texts(activity.getWindow().getDecorView(),labels);float density=activity.getResources().getDisplayMetrics().density;
                    for(TextView value:labels)if(value.getContentDescription()!=null&&value.getContentDescription().toString().startsWith("话题选项：")){android.graphics.Rect rect=new android.graphics.Rect();if(value.getGlobalVisibleRect(rect)&&rect.width()>=48*density-1&&rect.height()>=48*density-1)reachable[0]=true;}
                    if(!reachable[0])list(activity.getWindow().getDecorView()).scrollListBy(Math.round(48*density));
                });
                if(reachable[0])break;test.waitForIdleSync();Thread.sleep(100);
            }
            test.runOnMainSync(()->{List<TextView> values=new ArrayList<>();texts(activity.getWindow().getDecorView(),values);boolean accessible=false;float density=activity.getResources().getDisplayMetrics().density;
                for(TextView value:values)if(value.getContentDescription()!=null&&value.getContentDescription().toString().startsWith("话题选项：")){android.graphics.Rect rect=new android.graphics.Rect();if(value.getGlobalVisibleRect(rect)&&rect.width()>=48*density-1&&rect.height()>=48*density-1)accessible=true;}
                check(accessible,"long-card menu is fully reachable after scrolling");});
            inspect("feed-scrolled");
            test.runOnMainSync(()->check(navigation(activity.getWindow().getDecorView(),"来源")==null,"home has no independent source-management navigation"));
            sourceSettings();inspect("sources");
            test.runOnMainSync(()->check(find(activity.getWindow().getDecorView(),"内容来源")!=null,"accessibility click opens sources"));
            accessibilityClick("返回");accessibilityClick("返回");
            test.runOnMainSync(()->check(list(activity.getWindow().getDecorView())!=null&&navigation(activity.getWindow().getDecorView(),"设置")!=null,"settings source back returns directly to discovery"));
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{test.runOnMainSync(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();SharedPreferences.Editor edit=prefs.edit();if(had)edit.putInt("mode",old);else edit.remove("mode");edit.commit();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}




