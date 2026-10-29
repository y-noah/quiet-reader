package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.*;
import android.widget.*;
import app.quietreader.discovery.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real native card UI with isolated SQLite and cached synthetic articles; no network. */
final class DiscoveryUiProbe {
    private final Instrumentation instrumentation;
    private DiscoveryActivity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final List<ExecutorService> workers=new ArrayList<>();
    DiscoveryUiProbe(Instrumentation instrumentation){this.instrumentation=instrumentation;}
    private static Object field(Object object,String name){try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private void ui(Runnable task){instrumentation.runOnMainSync(task);}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    private void await(BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!condition.getAsBoolean()&&SystemClock.elapsedRealtime()<end){instrumentation.waitForIdleSync();Thread.sleep(20);}if(!condition.getAsBoolean())throw new IllegalStateException("UI probe timed out");}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(activity,"feed");}
    private DiscoveryController controller(){return (DiscoveryController)field(activity,"controller");}
    private ListView list(){return (ListView)field(activity,"list");}
    private View find(View root,String text){if(root instanceof TextView&&(text.contentEquals(((TextView)root).getText())||text.contentEquals(root.getContentDescription()==null?"":root.getContentDescription())))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View result=find(((ViewGroup)root).getChildAt(n),text);if(result!=null)return result;}return null;}
    private void click(String label){ui(()->{if(label.equals("设置")){for(int n=0;n<3&&!"feed".equals(field(activity,"page"));n++)activity.onBackPressed();}View view=find(activity.getWindow().getDecorView(),label);if(view==null)throw new IllegalStateException("Button absent: "+label);view.performClick();});}
    private void track(){workers.add((ExecutorService)field(controller(),"storage"));}
    private android.view.accessibility.AccessibilityNodeInfo editable(android.view.accessibility.AccessibilityNodeInfo node){
        if(node==null)return null;if(node.isEditable())return node;for(int n=0;n<node.getChildCount();n++){android.view.accessibility.AccessibilityNodeInfo found=editable(node.getChild(n));if(found!=null)return found;}return null;
    }
    private void dialogClick(String text)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+3000;
        do{
        instrumentation.waitForIdleSync();android.view.accessibility.AccessibilityNodeInfo root=instrumentation.getUiAutomation().getRootInActiveWindow();
        if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:dialogNodes(root,text)){
            if(!text.contentEquals(node.getText()==null?"":node.getText()))continue;
            if(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){instrumentation.waitForIdleSync();return;}
            if(node.isVisibleToUser()){
                android.graphics.Rect bounds=new android.graphics.Rect(),window=new android.graphics.Rect();node.getBoundsInScreen(bounds);root.getBoundsInScreen(window);
                if(!bounds.intersect(window)||bounds.isEmpty())continue;
                long now=SystemClock.uptimeMillis();android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0),up=android.view.MotionEvent.obtain(now,now+60,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
                try{instrumentation.sendPointerSync(down);instrumentation.sendPointerSync(up);}finally{down.recycle();up.recycle();}
                instrumentation.waitForIdleSync();return;
            }
            while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){instrumentation.waitForIdleSync();return;}
        }scrollDialogForward(root);Thread.sleep(50);
        }while(SystemClock.elapsedRealtime()<deadline);
        dumpDialog(instrumentation.getUiAutomation().getRootInActiveWindow(),0);
        throw new IllegalStateException("Dialog control absent: "+text);
    }
    private List<android.view.accessibility.AccessibilityNodeInfo> dialogNodes(android.view.accessibility.AccessibilityNodeInfo root,String text){List<android.view.accessibility.AccessibilityNodeInfo> nodes=new ArrayList<>();if(root==null)return nodes;if(text.contentEquals(root.getText()==null?"":root.getText()))nodes.add(root);for(int n=0;n<root.getChildCount();n++)nodes.addAll(dialogNodes(root.getChild(n),text));return nodes;}
    private boolean scrollDialogForward(android.view.accessibility.AccessibilityNodeInfo node){if(node==null)return false;if(node.isScrollable()&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD))return true;for(int n=0;n<node.getChildCount();n++)if(scrollDialogForward(node.getChild(n)))return true;return false;}
    private void dumpDialog(android.view.accessibility.AccessibilityNodeInfo node,int depth){if(node==null||depth>12)return;android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);report.append("DIALOG ").append(depth).append(' ').append(node.getClassName()).append(" text=").append(node.getText()).append(" desc=").append(node.getContentDescription()).append(" visible=").append(node.isVisibleToUser()).append(" clickable=").append(node.isClickable()).append(" scrollable=").append(node.isScrollable()).append(" bounds=").append(bounds).append('\n');for(int i=0;i<node.getChildCount();i++)dumpDialog(node.getChild(i),depth+1);}
    void run(){
        String database="test-ui-"+UUID.randomUUID()+".db";
        try{
            try(FeedStore store=new FeedStore(instrumentation.getTargetContext(),database)){
                store.ensureCandidatePolicy();
                List<HotCandidate> candidates=new ArrayList<>();long now=System.currentTimeMillis();
                for(int n=0;n<100;n++)candidates.add(new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990000"+n,"测试样本 "+(n+1)+" · 为什么城市树荫会影响夏季的体感温度？",n%3==0?"这是用于检查卡片排版、阅读返回和缓存位置的合成摘要。":"","",false,n+1,n==99?now-1000:null,now));
                long revision=store.beginRefresh(Source.ZHIHU);store.upsertSnapshot(Source.ZHIHU,revision,candidates,now);
                long id=store.commitRanking(store.version("sources"),store.version("profile"),now,"cold","all",CandidateOrder.arrange(candidates,new org.json.JSONArray(),false));store.activateSnapshot(id);store.anchor(id,candidates.get(60).id,-8);
            }
            Intent intent=new Intent(instrumentation.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity=(DiscoveryActivity)instrumentation.startActivitySync(intent);track();await(()->rows().size()==100);instrumentation.waitForIdleSync();Thread.sleep(150);
            ui(()->{
                check(list().getFirstVisiblePosition()==60,"cold start restores frozen snapshot anchor beyond first page");
                check(list().getChildCount()<20,"native list recycles views instead of inflating 100 cards");
                check(find(activity.getWindow().getDecorView(),"设置")!=null&&find(activity.getWindow().getDecorView(),"稍后读")==null,"compact card layout exposes settings without removed navigation");
            });
            FeedStore.Row selected=rows().get(60);Document document=new Document();document.url=selected.candidate.originalUrl;document.title=selected.candidate.title;document.blocks.add(new Block("text","合成正文，仅验证原有阅读器桥接和系统返回。"));
            ui(()->{Repository repository=new Repository(activity);repository.cacheArticle(document);repository.close();});
            Instrumentation.ActivityMonitor readerMonitor=instrumentation.addMonitor(MainActivity.class.getName(),null,false);
            ui(()->((ViewGroup)list().getChildAt(0)).getChildAt(0).performClick());Activity reader=instrumentation.waitForMonitorWithTimeout(readerMonitor,5000);instrumentation.removeMonitor(readerMonitor);
            check(reader!=null,"card opens existing native source reader");
            if(reader!=null){check(((Item)field(reader,"current")).url.equals(selected.candidate.originalUrl),"reader receives exact source URL");check(reader.getIntent().getBooleanExtra("direct_reader",false),"direct reader returns to discovery rather than old board");instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(reader::isFinishing);}
            instrumentation.waitForIdleSync();Thread.sleep(150);ui(()->check(list().getFirstVisiblePosition()==60,"system BACK preserves card scroll anchor"));
            ui(()->check(find(activity.getWindow().getDecorView(),"稍后读")==null,"removed read-later entry does not return after reading"));
            List<String> expectedRemaining=new ArrayList<>();for(FeedStore.Row row:rows())if(!row.eventId.equals(selected.eventId))expectedRemaining.add(row.eventId);
            ui(()->controller().feedback(selected,"BLOCK"));await(()->rows().size()==99);List<String> remaining=new ArrayList<>();for(FeedStore.Row row:rows())remaining.add(row.eventId);
            check(remaining.equals(expectedRemaining),"explicit content block removes only its identity and preserves remaining order");
            CountDownLatch unblocked=new CountDownLatch(1);ui(()->controller().feedbackHistory(history->{for(int n=0;n<history.length();n++)if("BLOCK".equals(history.optJSONObject(n).optString("action")))controller().undo(history.optJSONObject(n).optString("id"));unblocked.countDown();}));
            check(unblocked.await(5,TimeUnit.SECONDS),"content block is available for undo");await(()->rows().size()==100);
            ui(()->controller().feedback(selected,"ENOUGH"));await(()->rows().size()==99);check(rows().stream().noneMatch(r->r.eventId.equals(selected.eventId)),"enough feedback removes only the selected content version");
            CountDownLatch undone=new CountDownLatch(1);ui(()->controller().feedbackHistory(history->{for(int n=0;n<history.length();n++)if("ENOUGH".equals(history.optJSONObject(n).optString("action")))controller().undo(history.optJSONObject(n).optString("id"));undone.countDown();}));check(undone.await(5,TimeUnit.SECONDS),"feedback history is available for undo");await(()->rows().size()==100);
            List<String> beforeFilter=new ArrayList<>();for(FeedStore.Row row:rows())beforeFilter.add(row.eventId);
            ui(()->check(find(activity.getWindow().getDecorView(),"科技")==null&&find(activity.getWindow().getDecorView(),"其他")==null,"home has no category controls"));
            click("设置");dialogClick("高级与诊断");dialogClick("筛选来源");dialogClick("微博");await(()->rows().isEmpty()&&controller().sourceFilter().equals("WEIBO"));
            check(rows().isEmpty(),"source filter shows an empty subset without substituting other platforms");
            click("设置");dialogClick("高级与诊断");dialogClick("筛选来源");dialogClick("全部来源");await(()->rows().size()==100);
            List<String> afterFilter=new ArrayList<>();for(FeedStore.Row row:rows())afterFilter.add(row.eventId);
            check(beforeFilter.equals(afterFilter),"clearing source filter preserves frozen order");
            CountDownLatch filterBlocked=new CountDownLatch(1),releaseFilter=new CountDownLatch(1);
            ((ExecutorService)field(controller(),"storage")).submit(()->{filterBlocked.countDown();try{releaseFilter.await(10,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
            check(filterBlocked.await(3,TimeUnit.SECONDS),"source restoration fixture holds the pending filter commit on the real storage queue");
            DiscoveryActivity replacement;
            try{
                click("设置");dialogClick("高级与诊断");dialogClick("筛选来源");dialogClick("知乎");await(()->controller().sourceFilter().equals("ZHIHU"));
                ui(()->{list().setSelectionFromTop(30,-5);});instrumentation.waitForIdleSync();Thread.sleep(100);
                Instrumentation.ActivityMonitor recreated=instrumentation.addMonitor(DiscoveryActivity.class.getName(),null,false);ui(activity::recreate);
                try{replacement=(DiscoveryActivity)instrumentation.waitForMonitorWithTimeout(recreated,5000);}finally{instrumentation.removeMonitor(recreated);}
                check(replacement!=null,"configuration-style recreation creates a new home");
            }finally{releaseFilter.countDown();}
            if(replacement!=null){activity=replacement;track();await(()->rows().size()==100);instrumentation.waitForIdleSync();Thread.sleep(150);ui(()->check(list().getFirstVisiblePosition()==30,"recreation restores committed card anchor"));}
            check(controller().sourceFilter().equals("ZHIHU"),"source filter survives activity recreation with reading anchor");
            check(controller().categoryFilter()==ContentCategory.ALL,"recreation does not revive a removed category filter");
            ui(()->{controller().sort(true);controller().sort(false);controller().sort(true);});await(()->(boolean)field(activity,"newest")&&rows().get(0).candidate.sourceRank==100);Thread.sleep(150);ui(()->check((boolean)field(activity,"newest")&&rows().get(0).candidate.sourceRank==100,"rapid sort changes keep the latest order and known dates ahead of unknown dates"));
            ui(()->controller().sort(false));await(()->!(boolean)field(activity,"newest")&&rows().get(0).candidate.sourceRank==1);
            String oldTitle=rows().get(0).candidate.title;List<HotCandidate> changed=new ArrayList<>();for(FeedStore.Row row:rows()){HotCandidate c=row.candidate;changed.add(new HotCandidate(c.source,c.originalUrl,c.sourceRank==1?"测试样本 · 新一轮内容应等待用户查看更新":c.title,c.snippet,c.rawHeat,c.video,c.sourceRank,c.publishedAt,System.currentTimeMillis()));}
            FeedStore store=(FeedStore)field(controller(),"store");ExecutorService worker=(ExecutorService)field(controller(),"storage");worker.submit(()->{long revision=store.beginRefresh(Source.ZHIHU);store.upsertSnapshot(Source.ZHIHU,revision,changed,System.currentTimeMillis());}).get(5,TimeUnit.SECONDS);
            ui(()->{try{Method prepare=DiscoveryController.class.getDeclaredMethod("prepare",boolean.class);prepare.setAccessible(true);prepare.invoke(controller(),false);}catch(Exception ex){throw new IllegalStateException(ex);}});
            await(()->Boolean.TRUE.equals(field(activity,"updateAvailable")));check(rows().get(0).candidate.title.equals(oldTitle),"new candidates remain pending while the visible snapshot stays unchanged");click("查看更新");await(()->rows().get(0).candidate.title.contains("新一轮内容"));check(!rows().get(0).candidate.title.equals(oldTitle),"explicit update action applies the new frozen snapshot");
            click("设置");dialogClick("内容来源");ui(()->check(list().getAdapter().getCount()==11,"source management lists eleven eligible hot sources including IT之家 and 澎湃"));click("返回");click("返回");ui(()->list().setSelectionFromTop(0,0));instrumentation.waitForIdleSync();Thread.sleep(200);
            android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();File output=new File(instrumentation.getTargetContext().getExternalFilesDir(null),"discovery-cards.png");try(FileOutputStream stream=new FileOutputStream(output)){check(screenshot!=null&&screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream),"native card screenshot captured for visual review");}if(screenshot!=null)screenshot.recycle();
            check(!(boolean)field(controller(),"loading"),"cached home is idle after controlled interactions");
            // Seed a legacy saved item directly: the removed UI must not be restored,
            // but cache cleanup must continue to preserve existing user data.
            worker.submit(()->app.quietreader.discovery.LegacySavedFixture.saveWithInterest(store,selected.eventId,selected.candidate,System.currentTimeMillis())).get(5,TimeUnit.SECONDS);
            // Hold the actual storage queue between preparation and its main-thread callback.
            worker.submit(()->store.feedback("race-sample","MORE","sample",selected.candidate.id,System.currentTimeMillis(),null,selected.candidate.modelText())).get(5,TimeUnit.SECONDS);
            CountDownLatch releasePreparation=new CountDownLatch(1),preparationBlocked=new CountDownLatch(1);
            worker.submit(()->{preparationBlocked.countDown();try{releasePreparation.await(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}});
            check(preparationBlocked.await(5,TimeUnit.SECONDS),"recommendation preparation race uses a blocked real storage queue");
            try{ui(()->{try{Method begin=DiscoveryController.class.getDeclaredMethod("beginRecommendation");begin.setAccessible(true);begin.invoke(controller());controller().suspendRecommendation("测试暂停");}catch(Exception ex){throw new IllegalStateException(ex);}});}finally{releasePreparation.countDown();}
            worker.submit(()->{}).get(5,TimeUnit.SECONDS);instrumentation.waitForIdleSync();
            ui(()->check(field(controller(),"recommendation")==null&&controller().recommendationStatus().equals("测试暂停"),"suspended queued preparation cannot revive model work"));
            ui(()->controller().clearRecommendationCache());await(()->rows().isEmpty());
            check(worker.submit(()->app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.activeFeedback(System.currentTimeMillis()).length()==2&&store.candidates(100,0).isEmpty()).get(5,TimeUnit.SECONDS),"cache clear empties visible recommendations and retains saved items and preferences");
            ui(()->{try{Method editor=DiscoveryActivity.class.getDeclaredMethod("followEditor",String.class);editor.setAccessible(true);editor.invoke(activity,"");}catch(Exception ex){throw new IllegalStateException(ex);}});
            instrumentation.waitForIdleSync();android.view.accessibility.AccessibilityNodeInfo input=editable(instrumentation.getUiAutomation().getRootInActiveWindow());
            Bundle text=new Bundle();text.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"空间站的科学实验");
            check(input!=null&&input.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,text),"follow editor accepts an explicit interest description");
            dialogClick("选择期限");dialogClick("7 天");
            final org.json.JSONObject[] followed={null};await(()->{try{return worker.submit(()->{org.json.JSONArray feedback=store.activeFeedback(System.currentTimeMillis());for(int n=0;n<feedback.length();n++)if("FOLLOW".equals(feedback.optJSONObject(n).optString("action"))){followed[0]=feedback.optJSONObject(n);return true;}return false;}).get(2,TimeUnit.SECONDS);}catch(Exception ex){throw new IllegalStateException(ex);}});
            check(followed[0].optString("sample").equals("空间站的科学实验")&&followed[0].optLong("expires")-followed[0].optLong("at")==7*86_400_000L,"duration selection persists exactly seven days without modifying interest text");
            ui(()->controller().undo(followed[0].optString("id")));worker.submit(()->{}).get(5,TimeUnit.SECONDS);instrumentation.waitForIdleSync();
            check(worker.submit(()->store.activeFeedback(System.currentTimeMillis()).length()==2&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1).get(5,TimeUnit.SECONDS),"ending temporary follow retains unrelated preferences and saved items");
            String groupTitle="空间站完成一项新的科学实验验证",groupSummary="空间站乘组在实验舱完成了新的科学实验，记录实验数据并传回地面研究团队。";long groupTime=System.currentTimeMillis();
            HotCandidate zh=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/99008888",groupTitle,groupSummary,"",false,1,groupTime,groupTime);
            HotCandidate wb=new HotCandidate(Source.WEIBO,"https://weibo.com/99008888",groupTitle,groupSummary,"",false,1,groupTime,groupTime);
            worker.submit(()->{store.resetProfile();long z=store.beginRefresh(Source.ZHIHU);store.upsertSnapshot(Source.ZHIHU,z,Arrays.asList(zh),groupTime);long w=store.beginRefresh(Source.WEIBO);store.upsertSnapshot(Source.WEIBO,w,Arrays.asList(wb),groupTime);}).get(5,TimeUnit.SECONDS);
            ui(()->controller().filterSource("all"));
            ui(()->controller().sort(false));await(()->rows().size()==1&&rows().get(0).entries.size()==2);
            FeedStore.Row group=rows().get(0);Document groupedDocument=new Document();groupedDocument.url=zh.originalUrl;groupedDocument.title=zh.title;groupedDocument.blocks.add(new Block("text","测试多来源阅读返回，仅使用本机缓存。"));
            ui(()->{Repository repository=new Repository(activity);repository.cacheArticle(groupedDocument);repository.close();try{Method menu=DiscoveryActivity.class.getDeclaredMethod("cardMenu",FeedStore.Row.class);menu.setAccessible(true);menu.invoke(activity,group);}catch(Exception ex){throw new IllegalStateException(ex);}});
            dialogClick("查看各来源");Instrumentation.ActivityMonitor groupedReader=instrumentation.addMonitor(MainActivity.class.getName(),null,false);dialogClick(Source.ZHIHU.label+" · "+groupTitle);
            Activity opened=instrumentation.waitForMonitorWithTimeout(groupedReader,5000);instrumentation.removeMonitor(groupedReader);
            check(opened!=null&&((Item)field(opened,"current")).url.equals(zh.originalUrl),"group source picker opens the selected original URL");
            if(opened!=null){instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);await(opened::isFinishing);}instrumentation.waitForIdleSync();
            ui(()->{try{Method menu=DiscoveryActivity.class.getDeclaredMethod("cardMenu",FeedStore.Row.class);menu.setAccessible(true);menu.invoke(activity,rows().get(0));}catch(Exception ex){throw new IllegalStateException(ex);}});dialogClick("分开显示这些来源");await(()->rows().size()==2);
            check(rows().stream().allMatch(r->r.entries.size()==1),"group separation menu restores individual source cards");
            CountDownLatch restoredGroup=new CountDownLatch(1);ui(()->controller().feedbackHistory(history->{for(int n=0;n<history.length();n++)if("SEPARATE".equals(history.optJSONObject(n).optString("action")))controller().undo(history.optJSONObject(n).optString("id"));restoredGroup.countDown();}));
            check(restoredGroup.await(5,TimeUnit.SECONDS),"group separation remains visible in undo history");await(()->rows().size()==1&&rows().get(0).entries.size()==2);
            CountDownLatch prefetched=new CountDownLatch(1);java.util.concurrent.atomic.AtomicInteger fetched=new java.util.concurrent.atomic.AtomicInteger();List<Item> ahead=new ArrayList<>();String suffix=""+System.nanoTime();
            for(int n=0;n<30;n++)ahead.add(new Item(Source.ZHIHU,"预取测试"+n,"https://www.zhihu.com/question/"+suffix+n,""));
            ArticlePreloader[] proactive={null};ui(()->{proactive[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void cancel(){}public void load(Item item,Repository.Result<Document> callback){fetched.incrementAndGet();Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","未点击前已经准备好的正文内容。"));callback.success(doc);}},20,prefetched::countDown);proactive[0].offer(ahead);});
            try{check(prefetched.await(5,TimeUnit.SECONDS)&&fetched.get()==20,"proactive viewport prepares twenty documents serially before any click");
                ui(()->{Repository readerCache=new Repository(activity);check(readerCache.cachedArticle(ahead.get(19))!=null&&readerCache.cachedArticle(ahead.get(20))==null,"new reader shares prefetched body cache within bounded window");readerCache.close();});
            }finally{ui(()->proactive[0].close());}
            CountDownLatch slowStarted=new CountDownLatch(1),slowDone=new CountDownLatch(1);java.util.concurrent.atomic.AtomicReference<Repository.Result<Document>> slowCallback=new java.util.concurrent.atomic.AtomicReference<>();Item slow=ahead.get(25);
            ui(()->{proactive[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void cancel(){}public void load(Item item,Repository.Result<Document> callback){slowCallback.set(callback);slowStarted.countDown();}},20,slowDone::countDown);proactive[0].offer(Arrays.asList(slow,ahead.get(26)));});
            try{check(slowStarted.await(5,TimeUnit.SECONDS),"slow speculative document begins without a click");
                ui(()->{proactive[0].finishWindow();Document doc=new Document();doc.url=slow.url;doc.title=slow.title;doc.blocks.add(new Block("text","慢请求完成后仍然可以复用。"));slowCallback.get().success(doc);});
                check(slowDone.await(5,TimeUnit.SECONDS),"ending a prefetch window lets its active document finish");
                ui(()->{Repository cache=new Repository(activity);check(cache.cachedArticle(slow)!=null&&cache.cachedArticle(ahead.get(26))==null,"completed slow document is cached while queued work stays stopped");cache.close();});
            }finally{ui(()->proactive[0].close());}
            prefetchThermal(ahead.get(29),20);
            prefetchThermal(ahead.get(29),3);
            prefetchHandoff();
            prefetchStorageBudget();
            interestSetupFlow();
            ui(()->{
                Repository cache=new Repository(activity);long oldEpoch=Repository.articleCacheEpoch();cache.invalidateArticles();
                Document stale=new Document();stale.url=slow.url;stale.title=slow.title;stale.blocks.add(new Block("text","旧会话结果不得重新填充缓存。"));
                check(!cache.cacheArticle(stale,oldEpoch)&&cache.cachedArticle(slow)==null,"invalidated session rejects late cache writes from earlier requests");
                check(cache.cacheArticle(stale,Repository.articleCacheEpoch())&&cache.cachedArticle(slow)!=null,"current session can refill the cache after invalidation");cache.close();
                controller().interacting(true);controller().stop();controller().start();check(!(boolean)field(controller(),"interacting"),"returning to foreground releases the previous reader interaction gate");check(((Handler)field(controller(),"main")).hasCallbacks((Runnable)field(controller(),"recommend")),"foreground return schedules local ranking independently of automatic source refresh");
            });
        }catch(Throwable ex){failures++;report.append("ERROR ").append(ex.getClass().getSimpleName()).append(": ").append(ex.getMessage()).append('\n');}
        finally{
            if(activity!=null)ui(activity::finish);instrumentation.waitForIdleSync();for(ExecutorService worker:workers)try{if(!worker.awaitTermination(5,TimeUnit.SECONDS))failures++;}catch(InterruptedException ex){Thread.currentThread().interrupt();failures++;}
            instrumentation.getTargetContext().deleteDatabase(database);
        }
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());instrumentation.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void prefetchHandoff()throws Exception{
        Item item=new Item(Source.ZHIHU,"交接测试","https://www.zhihu.com/question/"+System.nanoTime(),"");
        CountDownLatch started=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Repository.Result<Document>> callback=new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger loads=new java.util.concurrent.atomic.AtomicInteger(),cancels=new java.util.concurrent.atomic.AtomicInteger();
        ArticlePreloader[] loader={null};
        ui(()->{controller().interacting(true);loader[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void cancel(){cancels.incrementAndGet();}public void load(Item i,Repository.Result<Document> cb){loads.incrementAndGet();callback.set(cb);started.countDown();}},20,()->{});
            try{Field slot=DiscoveryController.class.getDeclaredField("preloader");slot.setAccessible(true);((ArticlePreloader)slot.get(controller())).close();slot.set(controller(),loader[0]);}catch(Exception ex){throw new IllegalStateException(ex);}loader[0].offer(Collections.singletonList(item));});
        check(started.await(5,TimeUnit.SECONDS),"handoff request starts in discovery");
        Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(MainActivity.class.getName(),null,false);Activity reader=null;
        try{
            ui(()->{try{Method open=DiscoveryActivity.class.getDeclaredMethod("open",HotCandidate.class);open.setAccessible(true);open.invoke(activity,new HotCandidate(item.source,item.url,item.title,"","",false,1,null,System.currentTimeMillis()));}catch(Exception ex){throw new IllegalStateException(ex);}});
            reader=instrumentation.waitForMonitorWithTimeout(monitor,5000);check(reader!=null,"discovery launches the reader with an active handoff");
            if(reader==null)return;Activity target=reader;
            shell("cmd thermalservice override-status 2");instrumentation.waitForIdleSync();
            ui(()->check(cancels.get()==0&&field(loader[0],"policyWatch")==null,"thermal restriction does not cancel a promoted foreground reading request"));
            ui(()->{check(field(target,"preloader")==loader[0]&&cancels.get()==0&&loads.get()==1,"reader owns the same uncancelled request after discovery stops");
                Document doc=new Document();doc.url=item.url;doc.title=item.title;doc.blocks.add(new Block("text","交接后的正文无需重复请求。"));callback.get().success(doc);});
            await(()->field(target,"reading")!=null);
            check(((Document)field(target,"reading")).url.equals(item.url)&&loads.get()==1,"in-flight result renders in the reader without another fetch");
        }finally{shell("cmd thermalservice reset");instrumentation.removeMonitor(monitor);if(reader!=null){Activity target=reader;ui(target::finish);}instrumentation.waitForIdleSync();}
        CountDownLatch readyStarted=new CountDownLatch(1);Item ready=new Item(Source.ZHIHU,"交接间隙完成",item.url+"8","");String[] readyToken={null};
        ui(()->{loader[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void cancel(){}public void load(Item i,Repository.Result<Document> cb){callback.set(cb);readyStarted.countDown();}},20,()->{});loader[0].offer(Collections.singletonList(ready));});
        try{check(readyStarted.await(5,TimeUnit.SECONDS),"completion race starts before reader creation");ui(()->{
            readyToken[0]=ArticleHandoff.offer(ready,loader[0]);Document doc=new Document();doc.url=ready.url;doc.title=ready.title;doc.blocks.add(new Block("text","在两个页面切换之间完成。"));callback.get().success(doc);
            ArticlePreloader received=ArticleHandoff.claim(readyToken[0],ready,activity,()->{});Repository cache=new Repository(activity);
            check(received==loader[0]&&cache.cachedArticle(ready)!=null,"completion before claim remains available through shared cache");cache.close();
            check(ArticleHandoff.claim(readyToken[0],ready,activity,()->{})==null,"successfully claimed handoff cannot be consumed twice");
        });}finally{ui(()->{ArticleHandoff.cancel(readyToken[0]);loader[0].close();});}
        // No Activity launch: unclaimed requests must not keep an owner alive.
        CountDownLatch abandonedStarted=new CountDownLatch(1),abandonedClosed=new CountDownLatch(1);String[] token={null};
        Item abandoned=new Item(Source.ZHIHU,"未接收测试",item.url+"9","");
        ui(()->{loader[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void cancel(){abandonedClosed.countDown();}public void load(Item i,Repository.Result<Document> cb){abandonedStarted.countDown();}},20,()->{});loader[0].offer(Collections.singletonList(abandoned));});
        try{check(abandonedStarted.await(5,TimeUnit.SECONDS),"abandoned handoff starts");ui(()->{token[0]=ArticleHandoff.offer(abandoned,loader[0]);check(token[0]!=null,"active request receives a one-use handoff token");check(ArticleHandoff.claim("wrong-token",abandoned,activity,()->{})==null,"unrelated token cannot claim a pending request");});
            check(abandonedClosed.await(7,TimeUnit.SECONDS),"unclaimed handoff expires and cancels its request");
            ui(()->check(ArticleHandoff.claim(token[0],abandoned,activity,()->{})==null,"expired handoff cannot be reclaimed"));
        }finally{ui(()->{ArticleHandoff.cancel(token[0]);loader[0].close();});}
    }
    private void interestSetupFlow()throws Exception{
        try(FeedStore store=new FeedStore(instrumentation.getTargetContext(),activity.getIntent().getStringExtra("test_database"))){
            int before=store.activeFeedback(System.currentTimeMillis()).length();List<String> frozen=new ArrayList<>();for(FeedStore.Row row:rows())frozen.add(row.eventId);
            click("设置");dialogClick("阅读偏好");dialogClick("从热门话题选择兴趣");dialogClick("想看");
            Instrumentation.ActivityMonitor monitor=instrumentation.addMonitor(DiscoveryActivity.class.getName(),null,false);ui(activity::recreate);
            DiscoveryActivity replacement=(DiscoveryActivity)instrumentation.waitForMonitorWithTimeout(monitor,5000);instrumentation.removeMonitor(monitor);
            check(replacement!=null,"setup dialog supports configuration-style recreation");
            if(replacement==null)throw new IllegalStateException("Setup recreation absent");activity=replacement;track();await(()->field(activity,"setupChoices")!=null);
            check(((Map<?,?>)field(activity,"setupChoices")).size()==1,"unsaved setup selection survives Activity recreation");dialogClick("保存选择");
            await(()->store.activeFeedback(System.currentTimeMillis()).length()==before+1);
            check(store.activeFeedback(System.currentTimeMillis()).length()==before+1,"setup dialog commits the selected actual candidate only");
            List<String> after=new ArrayList<>();for(FeedStore.Row row:rows())after.add(row.eventId);check(after.equals(frozen),"setup selection does not jump the visible frozen feed");
            long version=store.version("profile");click("设置");dialogClick("阅读偏好");dialogClick("从热门话题选择兴趣");dialogClick("先浏览");
            await(()->!store.needsInterestSetup(System.currentTimeMillis()));check(store.version("profile")==version,"setup skip does not generate negative or positive feedback");
        }
    }
    private void prefetchStorageBudget()throws Exception{
        DataStorageBudget.Snapshot measured=DataStorageBudget.measure(instrumentation.getTargetContext().getDataDir());
        check(measured.bytes==measured.bodyBytes+measured.databaseBytes+measured.webBytes+measured.otherBytes,"storage components exactly reconcile with measured data total");
        report.append("STORAGE_COMPONENTS body=").append(measured.bodyBytes).append(" database=").append(measured.databaseBytes).append(" web=").append(measured.webBytes).append(" other=").append(measured.otherBytes).append("\n");
        report.append("DATA STORAGE bytes=").append(measured.bytes).append(" complete=").append(measured.complete).append(" free=").append(measured.free).append('\n');
        check(measured.bytes>0&&measured.free>0,"worker measures real app data without reading cookie contents");
        ui(()->{
            DiscoveryController target=controller();Map<String,Object> prior=new HashMap<>();
            try{
                for(String name:Arrays.asList("autoRefresh","foreground","feedPage","interacting","loading","storageSpace"))prior.put(name,field(target,name));
                for(String name:Arrays.asList("autoRefresh","foreground","feedPage","interacting","loading")){Field f=DiscoveryController.class.getDeclaredField(name);f.setAccessible(true);f.set(target,!name.equals("interacting")&&!name.equals("loading"));}
                ArticlePreloader loader=(ArticlePreloader)field(target,"preloader");loader.pause();
                Field usage=DiscoveryController.class.getDeclaredField("storageSpace");usage.setAccessible(true);
                Method start=DiscoveryController.class.getDeclaredMethod("startPrefetch");start.setAccessible(true);
                usage.set(target,new DataStorageBudget.FreeSpace(DataStorageBudget.MIN_FREE-1,true));start.invoke(target);
                check(!loader.busy(),"disk pressure prevents speculative request queue admission");
                usage.set(target,new DataStorageBudget.FreeSpace(DataStorageBudget.MIN_FREE,false));start.invoke(target);
                check(!loader.busy(),"unverified available space cannot authorize prefetch");
                usage.set(target,new DataStorageBudget.FreeSpace(DataStorageBudget.MIN_FREE,true));start.invoke(target);
                check(loader.busy(),"verified sufficient free space queues prefetch independently of inventory totals");
            }catch(Exception error){throw new IllegalStateException(error);}
            finally{try{Method pause=DiscoveryController.class.getDeclaredMethod("pausePreload");pause.setAccessible(true);pause.invoke(target);for(Map.Entry<String,Object> entry:prior.entrySet()){Field f=DiscoveryController.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(target,entry.getValue());}}catch(Exception error){throw new IllegalStateException(error);}}
        });
    }
    private String shell(String command)throws Exception{
        try(android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand(command);java.io.InputStream input=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[1024];int n;while((n=input.read(bytes))!=-1)output.write(bytes,0,n);return output.toString("UTF-8").trim();
        }
    }
    private void prefetchThermal(Item item,int window)throws Exception{
        if(!"1".equals(shell("getprop ro.kernel.qemu")))throw new IllegalStateException("Emulator-only thermal probe");
        ArticlePreloader[] loader={null};CountDownLatch started=new CountDownLatch(1),cancelled=new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Repository.Result<Document>> pending=new java.util.concurrent.atomic.AtomicReference<>();
        try{
            shell("cmd thermalservice override-status 0");
            ui(()->{loader[0]=new ArticlePreloader(activity,()->{},new ArticlePreloader.Loader(){public void cancel(){cancelled.countDown();}public void load(Item i,Repository.Result<Document> cb){pending.set(cb);started.countDown();}},window,()->{});loader[0].offer(Collections.singletonList(item));});
            check(started.await(5,TimeUnit.SECONDS),"thermal probe begins an active speculative read window="+window);
            shell("cmd thermalservice override-status 2");
            check(cancelled.await(5,TimeUnit.SECONDS),"thermal callback immediately cancels active prefetch window="+window);
            ui(()->{
                check(!loader[0].busy()&&field(loader[0],"policyWatch")==null,"resource pause drains queue and unregisters prefetch watcher");
                Document late=new Document();late.url=item.url;late.title=item.title;late.blocks.add(new Block("text","被取消的迟到正文"));
                if(pending.get()!=null)pending.get().success(late);
                Repository cache=new Repository(activity);check(cache.cachedArticle(item)==null,"cancelled prefetch cannot populate cache with a late callback");cache.close();
            });
            shell("cmd thermalservice override-status 0");instrumentation.waitForIdleSync();
            ui(()->check(!loader[0].busy(),"temperature recovery alone does not restart speculative work"));
        }finally{ui(()->{if(loader[0]!=null)loader[0].close();});shell("cmd thermalservice reset");}
    }
}




