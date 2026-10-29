package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import android.webkit.WebView;
import app.quietreader.discovery.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static app.quietreader.Models.*;

/** Real reader lifecycle and elapsed time; controlled native interaction callbacks. */
final class ReadInterestUiProbe {
    private final Instrumentation test;
    private final boolean staleVersion;
    private final boolean offlineSnapshot;
    private boolean quickExit;
    private String quickExitExclusion="";
    ReadInterestUiProbe quickExit(){quickExit=true;return this;}
    ReadInterestUiProbe quickExitExclusion(String value){quickExit=true;quickExitExclusion=value;return this;}
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    ReadInterestUiProbe(Instrumentation test){this(test,false);}
    ReadInterestUiProbe(Instrumentation test,boolean staleVersion){this(test,staleVersion,false);}
    ReadInterestUiProbe(Instrumentation test,boolean staleVersion,boolean offlineSnapshot){this.test=test;this.staleVersion=staleVersion;this.offlineSnapshot=offlineSnapshot;}
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private static Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new IllegalStateException(e);}}
    void run(){
        String database="test-ui-"+UUID.randomUUID()+".db";MainActivity[] activity={null};
        HotCandidate candidate=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/"+System.nanoTime(),"阅读弱参考合成内容","用于验证前台阅读行为","",false,1,null,System.currentTimeMillis());
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());if(offlineSnapshot)check(repo.offline(),"real Android connectivity reports no active network before opening cached body");Document doc=new Document();doc.url=candidate.originalUrl;doc.title=candidate.title;for(int n=0;n<(quickExitExclusion.equals("short")?1:50);n++)doc.blocks.add(new Block("text","这是用于验证实际阅读页面生命周期的合成段落，不代表用户真实兴趣。第"+n+"段。"));ArticleVersion.capture(candidate.readerItem(),doc);if(offlineSnapshot){doc.metadataHash=HotCandidate.metadataHash("缓存的旧标题","旧摘要");doc.cachedAt=System.currentTimeMillis()-11*60_000;}repo.cacheArticle(doc);repo.close();});
            if(offlineSnapshot){
                ArticleDiskCache disk=new ArticleDiskCache(test.getTargetContext());String[] session={null};test.runOnMainSync(()->session[0]=ArticleDiskCache.session(candidate.originalUrl));
                long deadline=SystemClock.elapsedRealtime()+5000;ArticleDiskCache.Hit hit;
                do{hit=disk.read(candidate.originalUrl,disk.namespace(),session[0],true,System.currentTimeMillis());if(hit!=null)break;Thread.sleep(25);}while(SystemClock.elapsedRealtime()<deadline);
                check(hit!=null,"old body is persisted before memory eviction and reader launch");
                check(disk.read(candidate.originalUrl,disk.namespace(),session[0],false,System.currentTimeMillis())==null,"same old disk body is too old for online freshness");
                test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());repo.releaseArticleMemory();check(repo.cachedArticle(candidate.readerItem())==null,"reader starts without an in-memory body and must restore from disk");repo.close();});
            }
            activity[0]=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).putExtra("discovery_item",candidate.readerItem().json().toString()).putExtra("discovery_candidate",candidate.json().toString()).putExtra("test_database",database).putExtra("direct_reader",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            AtomicBoolean ready=new AtomicBoolean();long end=SystemClock.elapsedRealtime()+8_000;
            while(!ready.get()&&SystemClock.elapsedRealtime()<end){test.runOnMainSync(()->{ReadingEngagement engagement=(ReadingEngagement)field(activity[0],"engagement");ready.set((boolean)field(engagement,"ready")&&(boolean)field(engagement,"visible"));});if(!ready.get())Thread.sleep(25);}
            check(ready.get(),"body completion and actual window focus enable reading evidence");
            if(offlineSnapshot)test.runOnMainSync(()->{Document shown=(Document)field(activity[0],"reading");check(shown!=null&&shown.hasContent()&&!ArticleVersion.matches(candidate.readerItem(),shown),"offline real reader renders old body without relabelling it as current candidate");});
            check(store.activeFeedback(System.currentTimeMillis()).length()==0,"opening and rendering alone create no reading interest");
            if(quickExit){
                test.runOnMainSync(()->{
                    Document shown=(Document)field(activity[0],"reading");
                    if(quickExitExclusion.equals("stale"))shown.metadataHash="0".repeat(64);
                    if(quickExitExclusion.equals("login"))shown.loginRequired=true;
                    if(quickExitExclusion.equals("loading"))((ReadingEngagement)field(activity[0],"engagement")).ready(false,SystemClock.elapsedRealtime());
                    if(quickExitExclusion.equals("background")){test.callActivityOnPause(activity[0]);test.callActivityOnResume(activity[0]);}
                });
                Thread.sleep(1200);test.runOnMainSync(activity[0]::onBackPressed);
                long until=SystemClock.elapsedRealtime()+5000;
                while(store.activeFeedback(System.currentTimeMillis()).length()==0&&SystemClock.elapsedRealtime()<until)Thread.sleep(25);
                org.json.JSONArray events=store.activeFeedback(System.currentTimeMillis());
                if(!quickExitExclusion.isEmpty()){
                    check(events.length()==0,"early return excluded for "+quickExitExclusion+"; no content or platform preference event");
                }else{
                check(events.length()==1&&"QUICK_EXIT".equals(events.optJSONObject(0).optString("action")),"explicit early back from a displayed long body records one weak negative, never a positive read");
                check(new PlatformPreference(events).empty(),"one early exit does not reduce the whole platform");
                check(!new SemanticOrder.Profile(events).samples.get(0).positive,"actual reader quick exit reaches the negative semantic sample");
                }
            }else{
            if(staleVersion)test.runOnMainSync(()->{Document shown=(Document)field(activity[0],"reading");shown.metadataHash="0".repeat(64);});
            // Allow automatic scroll restoration suppression to finish, then drive the real hooks.
            AtomicBoolean scrollAllowed=new AtomicBoolean();end=SystemClock.elapsedRealtime()+5000;
            while(!scrollAllowed.get()&&SystemClock.elapsedRealtime()<end){test.runOnMainSync(()->scrollAllowed.set(System.currentTimeMillis()>(long)field(activity[0],"suppressAutoUntil")));if(!scrollAllowed.get())Thread.sleep(25);}
            if(!scrollAllowed.get())throw new IllegalStateException("Reader scroll restoration guard did not finish");
            test.runOnMainSync(()->{activity[0].onUserInteraction();((WebView)field(activity[0],"readerWeb")).scrollTo(0,400);});
            Thread.sleep(20_100);
            test.runOnMainSync(activity[0]::finish);
            end=SystemClock.elapsedRealtime()+5_000;int count=0;
            while(SystemClock.elapsedRealtime()<end){count=store.activeFeedback(System.currentTimeMillis()).length();if(count>0)break;Thread.sleep(25);}
            if(staleVersion||offlineSnapshot)check(count==0,"elapsed foreground reading and scroll cannot learn from a mismatched body version");
            else{
                check(count==1&&"READ".equals(store.activeFeedback(System.currentTimeMillis()).optJSONObject(0).optString("action")),"foreground reading and scroll emit one weak event through onPause and bounded writer");
                check(new SemanticOrder.Profile(store.activeFeedback(System.currentTimeMillis())).samples.get(0).read,"reader-created event reaches the semantic profile");
                check(candidate.source.name().equals(store.activeFeedback(System.currentTimeMillis()).getJSONObject(0).getString("source")),"real reader records the actually opened platform alongside content interest");
                check(new PlatformPreference(store.activeFeedback(System.currentTimeMillis())).empty(),"one real read alone cannot establish platform-wide preference");
            }
            }
            store.readLearningEnabled(false,System.currentTimeMillis());check(store.activeFeedback(System.currentTimeMillis()).length()==0,"disabling withdraws the event produced by the real reader");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{test.runOnMainSync(()->{if(activity[0]!=null)activity[0].finish();});test.waitForIdleSync();test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
