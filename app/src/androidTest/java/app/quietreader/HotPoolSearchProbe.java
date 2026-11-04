package app.quietreader;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.*;

/** Synthetic full-pool search on the isolated emulator app; no user phone or source calls. */
final class HotPoolSearchProbe {
    private final Instrumentation test;private Activity activity;private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    HotPoolSearchProbe(Instrumentation test){this.test=test;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View view,String label){
        if(view.getVisibility()!=View.VISIBLE)return null;
        if(view instanceof TextView&&(label.contentEquals(((TextView)view).getText())||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())))return view;
        if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View hit=find(((ViewGroup)view).getChildAt(n),label);if(hit!=null)return hit;}return null;
    }
    private ListView list(View view){if(view instanceof ListView)return (ListView)view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){ListView found=list(((ViewGroup)view).getChildAt(n));if(found!=null)return found;}return null;}
    private View root(){return activity.getWindow().getDecorView();}
    private EditText input(){return (EditText)find(root(),"搜索整个热搜池");}
    private void await(BooleanSupplier condition)throws Exception{
        long end=SystemClock.elapsedRealtime()+8000;boolean[] good={false};do{test.runOnMainSync(()->good[0]=condition.getAsBoolean());if(good[0])return;Thread.sleep(40);}while(SystemClock.elapsedRealtime()<end);throw new IllegalStateException("UI condition timed out");
    }
    private void query(String value)throws Exception{
        test.runOnMainSync(()->input().setText(value));
        await(()->find(root(),"正在查找…")==null);test.waitForIdleSync();
    }
    private HotCandidate fixture(Models.Source source,int id,String title){return new HotCandidate(source,"https://"+(source==Models.Source.ZHIHU?"www.zhihu.com/question/":source==Models.Source.BBC?"www.bbc.com/zhongwen/articles/":"weibo.com/")+id,title,"","",false,id+1,null,System.currentTimeMillis());}
    void run(){String database="test-ui-"+UUID.randomUUID()+".db";
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Isolated emulator target required");
            List<HotCandidate> zhihu=new ArrayList<>();for(int n=0;n<40;n++)zhihu.add(fixture(Models.Source.ZHIHU,n,"中文测试新闻第"+n+"条"));
            HotCandidate deep=fixture(Models.Source.BBC,101,"寒武纪发布最新重要公告");
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                store.ensureCandidatePolicy();long now=System.currentTimeMillis();
                for(Models.Source source:HotSourceCatalog.HOT)store.enable(source,source==Models.Source.ZHIHU||source==Models.Source.BBC||source==Models.Source.WEIBO);
                store.upsertSnapshot(Models.Source.ZHIHU,store.beginRefresh(Models.Source.ZHIHU),zhihu,now);
                store.upsertSnapshot(Models.Source.BBC,store.beginRefresh(Models.Source.BBC),List.of(deep),now);
                store.upsertSnapshot(Models.Source.WEIBO,store.beginRefresh(Models.Source.WEIBO),List.of(fixture(Models.Source.WEIBO,201,"不可出现的微博独家消息")),now);store.enable(Models.Source.WEIBO,false);
                long snapshot=store.commitRanking(store.version("sources"),store.profile(now).getLong("version"),now,"cold","ZHIHU",CandidateOrder.forModels(store.candidates(5000,0),new JSONArray()));store.activateSnapshot(snapshot);
            }
            new ArticleReadHistory(test.getTargetContext(),database).mark(deep,System.currentTimeMillis());
            activity=test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            await(()->list(root())!=null&&list(root()).getCount()==20);
            test.runOnMainSync(()->{
                check(list(root()).getCount()==20,"home presents twenty rows");
                list(root()).setSelectionFromTop(19,0);
            });test.waitForIdleSync();
            test.runOnMainSync(()->{check(list(root()).getCount()==20,"home bottom does not append more rows");View target=find(root(),"搜索热搜池");check(target!=null,"compact search entry exists");check(target instanceof TextView&&"搜索热搜".contentEquals(((TextView)target).getText())&&target.getWidth()>2*target.getHeight(),"home search is a labeled wide field in the existing toolbar");target.performClick();});
            await(()->find(root(),"输入关键词，在整个热搜池的 41 条内容中查找")!=null);
            check(true,"search counts all enabled active candidates, not twenty home rows");
            query("寒武记发布");
            test.runOnMainSync(()->{check(list(root()).getCount()==1&&find(root(),deep.title)!=null,"typo search finds a read article outside the current source filter");check(find(root(),"已读 · BBC中文 · 获取于 "+new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(deep.observedAt)))!=null,"search retains the read marker");});
            android.graphics.Bitmap shot=test.getUiAutomation().takeScreenshot();if(shot!=null){try(FileOutputStream out=new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null),"hot-pool-search.png"))){shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}finally{shot.recycle();}}
            query("完全无关的巴黎体育比赛");test.runOnMainSync(()->check(find(root(),"热搜池里没有相关热搜")!=null,"no match displays an explicit empty state"));
            query("微博独家");test.runOnMainSync(()->check(find(root(),"热搜池里没有相关热搜")!=null,"disabled sources are not searched"));
            test.runOnMainSync(()->{input().setText("寒武纪");input().setText("巴黎奥运会");input().setText("寒武记发布");});
            await(()->find(root(),deep.title)!=null);Thread.sleep(350);
            test.runOnMainSync(()->check(find(root(),deep.title)!=null&&list(root()).getCount()==1,"rapid typing cannot publish an older query result"));
            query("中文测试新闻");test.runOnMainSync(()->check(list(root()).getCount()==40,"search results are not truncated to the home batch"));
            query("");test.runOnMainSync(()->{check(find(root(),"输入关键词，在整个热搜池的 41 条内容中查找")!=null,"clearing query restores the actual pool count");find(root(),"返回").performClick();});
            await(()->find(root(),"设置")!=null);
            test.runOnMainSync(()->check(list(root()).getCount()==20,"leaving search restores the twenty-row home"));
            try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
                check(store.profile(System.currentTimeMillis()).getJSONArray("feedback").length()==0,"search does not write interest feedback");
                check(store.candidates(5000,0).size()==41,"search does not fetch or inject candidates");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error).append('\n');}
        finally{
            test.runOnMainSync(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();
            test.getTargetContext().deleteDatabase(database);
            test.getTargetContext().getSharedPreferences("article-reads-"+HotCandidate.hash(database),0).edit().clear().commit();
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
