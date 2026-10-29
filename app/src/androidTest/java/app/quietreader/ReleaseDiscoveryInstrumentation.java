package app.quietreader;

import android.app.*;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

/** Minified app checks without production-class reflection; fixture phases require the isolated target. */
public final class ReleaseDiscoveryInstrumentation extends Instrumentation {
    private Activity activity;
    private int checks,failures;
    private final StringBuilder report=new StringBuilder();
    private final BlockingQueue<Message> replies=new LinkedBlockingQueue<>();
    private final CountDownLatch connected=new CountDownLatch(1);
    private Messenger service,inbox;
    private DetachedProbeRecorder detached;
    private Bundle runArguments;
    private boolean bound,cold;private String phase="";
    private boolean foregroundModels,boundaryModelInput;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){service=new Messenger(binder);connected.countDown();}public void onServiceDisconnected(ComponentName name){}};
    @Override public void onCreate(Bundle args){
        super.onCreate(args);runArguments=args==null?new Bundle():new Bundle(args);cold=args!=null&&"true".equals(args.getString("cold"));phase=args==null?"":args.getString("phase","");
        if(args!=null&&args.containsKey("detachedRun"))try{detached=new DetachedProbeRecorder(getTargetContext(),args,phase);}catch(Exception error){Bundle failed=new Bundle();failed.putString("stream","ERROR detached run initialization: "+error.getClass().getSimpleName()+"\nchecks=0 failures=1\n");super.finish(Activity.RESULT_CANCELED,failed);return;}
        start();
    }
    @Override public boolean onException(Object object,Throwable error){
        if(detached==null)return super.onException(object,error);
        Bundle failed=new Bundle();failed.putString("stream","UNHANDLED_TEST_EXCEPTION "+android.util.Log.getStackTraceString(error)+"\nchecks=0 failures=1\n");
        finish(Activity.RESULT_CANCELED,failed);return true;
    }
    @Override public void finish(int code,Bundle result){if(detached!=null&&!detached.finish(code,result))return;super.finish(code,result);}

    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    private View find(View view,String label){if(view instanceof TextView&&(label.contentEquals(((TextView)view).getText())||label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription())))return view;if(view instanceof android.view.ViewGroup)for(int n=0;n<((android.view.ViewGroup)view).getChildCount();n++){View result=find(((android.view.ViewGroup)view).getChildAt(n),label);if(result!=null)return result;}return null;}
    private void click(String label){runOnMainSync(()->{if(label.equals("设置")){for(int n=0;n<3;n++){View back=find(activity.getWindow().getDecorView(),"返回");if(back==null)break;back.performClick();}}View view=find(activity.getWindow().getDecorView(),label);if(view==null)throw new IllegalStateException("Missing view: "+label);view.performClick();});}
    private void menu(String label)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+5000;
        while(SystemClock.elapsedRealtime()<deadline){android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(android.view.accessibility.AccessibilityNodeInfo node:nodes(root,label)){if(!label.contentEquals(node.getText()==null?"":node.getText()))continue;if(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){waitForIdleSync();return;}if(node.isVisibleToUser()&&node.isEnabled()){android.graphics.Rect rect=new android.graphics.Rect();node.getBoundsInScreen(rect);long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,rect.centerX(),rect.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,rect.centerX(),rect.centerY(),0);try{sendPointerSync(down);sendPointerSync(up);}finally{down.recycle();up.recycle();}waitForIdleSync();return;}while(node!=null&&!node.isClickable())node=node.getParent();if(node!=null&&node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK))return;}Thread.sleep(50);}
        dump(getUiAutomation().getRootInActiveWindow(),0);throw new IllegalStateException("Missing menu: "+label);
    }
    private List<android.view.accessibility.AccessibilityNodeInfo> nodes(android.view.accessibility.AccessibilityNodeInfo node,String label){List<android.view.accessibility.AccessibilityNodeInfo> result=new ArrayList<>();if(node==null)return result;if(label.contentEquals(node.getText()==null?"":node.getText()))result.add(node);for(int i=0;i<node.getChildCount();i++)result.addAll(nodes(node.getChild(i),label));return result;}
    private void dump(android.view.accessibility.AccessibilityNodeInfo node,int depth){if(node==null||depth>12)return;report.append("NODE ").append(node.getClassName()).append(" text=").append(node.getText()).append(" clickable=").append(node.isClickable()).append("\n");for(int i=0;i<node.getChildCount();i++)dump(node.getChild(i),depth+1);}
    private void send(int what,Bundle data)throws Exception{Message message=Message.obtain(null,what);message.setData(data);message.replyTo=inbox;service.send(message);}
    private void model(String kind)throws Exception{
        String[] texts={"手机电池续航和散热测试","天文学家发现新的系外行星"};String query="手机性能与电池续航";
        if(boundaryModelInput){char[] text=new char[4096],other=new char[4096],question=new char[512];Arrays.fill(text,'闻');Arrays.fill(other,'讯');Arrays.fill(question,'科');texts=new String[]{new String(text),new String(other)};query=new String(question);}
        replies.clear();Bundle request=new Bundle();request.putString("kind",kind);request.putStringArray("texts",texts);request.putStringArray("ids",new String[]{"one","two"});request.putString("query",query);request.putLong("budgetMs",3000);request.putLong("generation",42);request.putLong("batch",7);send(1,request);
        int items=0;boolean done=false;long deadline=SystemClock.elapsedRealtime()+30_000;
        while(SystemClock.elapsedRealtime()<deadline&&!done){Message message=replies.poll(1,TimeUnit.SECONDS);if(message==null)continue;Bundle value=message.getData();
            if(message.what==12)throw new IllegalStateException("Release inference: "+value.getString("code"));
            if(message.what==10){items++;check(value.getLong("generation")==42&&value.getLong("batch")==7,"minified "+kind+" preserves request identity");
                if(foregroundModels)runOnMainSync(()->check(activity!=null&&!activity.isFinishing()&&activity.hasWindowFocus(),"native feed retains foreground focus at actual "+kind+" output"));
                if(kind.equals("embedding")){float[] vector=value.getFloatArray("vector");double norm=0;boolean finite=vector!=null&&vector.length==512;if(vector!=null)for(float x:vector){finite&=Float.isFinite(x);norm+=(double)x*x;}check(finite&&Math.abs(norm-1)<0.001,"minified embedding returns normalized 512-dimensional vector");}
                else{check(Float.isFinite(value.getFloat("logit",Float.NaN)),"minified reranker returns finite logit through SentencePiece JNI");if(boundaryModelInput)check(value.getInt("inputTokens")>256&&value.getInt("usedTokens")==256&&!value.getBoolean("inputComplete",true),"maximum service input uses exactly256 rerank tokens and explicitly reports truncation");}
            }else if(message.what==11){done=true;check(value.getInt("completed")==items,"minified "+kind+" completes delivered batch");}
        }
        check(done&&items>0,"actual "+kind+" inference succeeds in minified service");
    }
    @Override public void onStart(){
        if(phase.equals("refresh-animation-retained")){new ReleaseRefreshAnimationProbe(this).run(runArguments);return;}
        if(phase.equals("public-tieba-times")){new ReleasePublicTiebaTimesProbe(this).run();return;}
        if(phase.equals("public-tieba-times-wide")){new ReleasePublicTiebaTimesProbe(this,true).run();return;}
        if(phase.equals("reader-early-continuation")){new ReleaseTiebaIdentityProbe(this,false,true).run();return;}
        if(phase.equals("reader-image-font")){new ReleaseTiebaIdentityProbe(this,false,true,true).run();return;}
        if(phase.equals("retained-recommendation")){new ReleaseRetainedRecommendationProbe(this).run(runArguments);return;}
        if(phase.equals("phone-navigation")){new ReleasePhoneNavigationProbe(this).run(runArguments);return;}
        if(phase.equals("reader-cache-choice")){new ReleaseReaderCacheChoiceProbe(this).run();return;}
        if(phase.equals("bbc-chart")){new ReleaseBbcChartProbe(this).run();return;}
        if(detached!=null)try{if(!detached.start(this))return;}catch(Exception error){Bundle failed=new Bundle();failed.putString("stream","ERROR detached sampler initialization: "+error.getClass().getSimpleName()+"\nchecks=0 failures=1\n");finish(Activity.RESULT_CANCELED,failed);return;}
        if(phase.equals("hupu-rendered-media")){new ReleaseHupuMediaProbe(this).run();return;}
        if(phase.equals("tieba-emoji-identity")){new ReleaseTiebaIdentityProbe(this).run();return;}
        if(phase.equals("tieba-child-progress")){new ReleaseTiebaIdentityProbe(this,true).run();return;}
        if(phase.equals("tieba-media-progress")){new ReleaseTiebaIdentityProbe(this).mediaOnly().run();return;}
        if(phase.equals("reading-layout")){new ReleaseReadingFlowProbe(this).runLayout(runArguments);return;}
        if(phase.equals("memory-recovery")){new ReleaseMemoryRecoveryProbe(this).run();return;}
        if(phase.equals("image-failure")){new ReleaseImageFailureProbe(this).run();return;}
        if(phase.equals("short-headlines")){new ReleaseReadingFlowProbe(this).runShortHeadlines();return;}
        if(phase.equals("appointment-event")){new ReleaseReadingFlowProbe(this).runAppointmentEvent();return;}
        if(phase.equals("broadcast-grammar")){new ReleaseReadingFlowProbe(this).runStoppedEvents(true);return;}
        if(phase.equals("passing-events")){new ReleaseReadingFlowProbe(this).runPassingEvents();return;}
        if(phase.equals("statement-events")){new ReleaseReadingFlowProbe(this).runStatementEvents();return;}
        if(phase.equals("wealth-record-events")){new ReleaseReadingFlowProbe(this).runWealthEvents();return;}
        if(phase.equals("release-specification-events")){new ReleaseReadingFlowProbe(this).runReleaseSpecificationEvents();return;}
        if(phase.equals("product-attribute-events")){new ReleaseReadingFlowProbe(this).runProductAttributeEvents();return;}
        if(phase.equals("rate-decision-events")){new ReleaseReadingFlowProbe(this).runRateDecisionEvents();return;}
        if(phase.equals("product-prompt-events")){new ReleaseReadingFlowProbe(this).runProductPromptEvents();return;}
        if(phase.equals("apology-events")){new ReleaseReadingFlowProbe(this).runApologyEvents();return;}
        if(phase.equals("qualified-apology-events")){new ReleaseReadingFlowProbe(this).runQualifiedApologyEvents();return;}
        if(phase.equals("question-frame-events")){new ReleaseReadingFlowProbe(this).runQuestionFrameEvents();return;}
        if(phase.equals("confidence-event-groups")){new ReleaseReadingFlowProbe(this).runConfidenceEventGroups();return;}
        if(phase.equals("affiliation-events")){new ReleaseReadingFlowProbe(this).runDirectRelationEvents(false);return;}
        if(phase.equals("airspace-events")){new ReleaseReadingFlowProbe(this).runDirectRelationEvents(true);return;}
        if(phase.equals("judgment-events")){new ReleaseReadingFlowProbe(this).runJudgmentEvents();return;}
        if(phase.equals("stopped-events")){new ReleaseReadingFlowProbe(this).runStoppedEvents();return;}
        if(phase.equals("appearance-layout")){new ReleaseAppearanceLayoutProbe(this).run(runArguments);return;}
        if(phase.equals("settings-insets")){new SettingsInsetsProbe(this).run();return;}
        if(phase.equals("live-reader")){new ReleaseLiveReaderProbe(this).run(runArguments);return;}
        if(phase.equals("phone-hupu-history")){new ReleasePhoneSourceProbe(this).runHupuHistory(runArguments);return;}
        if(phase.equals("phone-hupu")){new ReleasePhoneSourceProbe(this).run(runArguments);return;}
        if(phase.equals("phone-zhihu")){new ReleasePhoneSourceProbe(this).runZhihu(runArguments);return;}
        if(phase.equals("phone-tieba")){new ReleasePhoneSourceProbe(this).runTieba(runArguments);return;}
        if(phase.equals("live-model-reading")){new ReleaseReadingFlowProbe(this).runLiveInterruption(runArguments);return;}
        if(phase.equals("energy-setup")||phase.equals("energy-replay")||phase.equals("energy-background")){new ReleaseReadingFlowProbe(this).runReplaySetup(runArguments);return;}
        if(phase.equals("control-idle")){
            if(detached==null){Bundle failed=new Bundle();failed.putString("stream","Detached control required\nchecks=0 failures=1\n");finish(Activity.RESULT_CANCELED,failed);return;}
            long end=SystemClock.elapsedRealtime()+120000;
            while(!detached.isTerminal()&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(100);
            if(!detached.isTerminal()){Bundle failed=new Bundle();failed.putString("stream","Cancellation was not exercised\nchecks=0 failures=1\n");finish(Activity.RESULT_CANCELED,failed);}return;
        }
        if(phase.equals("normal-upgrade-seed")||phase.equals("normal-upgrade-original-verify")||phase.equals("normal-upgrade-verify")||phase.equals("normal-upgrade-before")||phase.equals("normal-upgrade-renew-expired-cookie")){new ReleaseNormalUpgradeProbe(this,phase).run();return;}
        if(phase.equals("trial-export")){new ReleaseTrialExportProbe(this).run();return;}
        if(phase.equals("reading-export")){new ReleaseReadingFlowProbe(this).runExport();return;}
        if(phase.equals("recommendation-refresh")){new ReleaseReadingFlowProbe(this).runRefresh();return;}
        if(phase.equals("recommendation-refresh-news")){new ReleaseReadingFlowProbe(this).runRefresh(true);return;}
        if(phase.equals("current-batch-cache")){new ReleaseReadingFlowProbe(this).runCurrentBatchCache();return;}
        if(phase.equals("hupu-discussion-progress-seed")){new ReleaseReadingFlowProbe(this).runDiscussionProgress(false,true);return;}
        if(phase.equals("hupu-discussion-progress-verify")){new ReleaseReadingFlowProbe(this).runDiscussionProgress(true,true);return;}
        if(phase.equals("tieba-discussion-progress-seed")){new ReleaseReadingFlowProbe(this).runTiebaDiscussionProgress(false);return;}
        if(phase.equals("tieba-discussion-progress-verify")){new ReleaseReadingFlowProbe(this).runTiebaDiscussionProgress(true);return;}
        if(phase.equals("discussion-progress-seed")){new ReleaseReadingFlowProbe(this).runDiscussionProgress(false);return;}
        if(phase.equals("discussion-progress-verify")){new ReleaseReadingFlowProbe(this).runDiscussionProgress(true);return;}
        if(phase.equals("article-followup")){new ReleaseReadingFlowProbe(this).runFollowup();return;}
        if(phase.equals("queued-article-identity")){new ReleaseQueuedArticleIdentityProbe(this).run();return;}
        if(phase.equals("reading-core")){new ReleaseReadingFlowProbe(this).runCore();return;}
        if(phase.equals("reading-flow")){new ReleaseReadingFlowProbe(this).run();return;}
        if(phase.equals("upgrade-seed")||phase.equals("upgrade-verify")){new ReleaseUpgradeProbe(this,phase.equals("upgrade-seed")).run();return;}
        if(!Arrays.asList("","models","models-ui","foreground-models").contains(phase)){
            Bundle invalid=new Bundle();invalid.putString("stream","ERROR unknown release probe phase: "+phase+"\nchecks=0 failures=1\n");
            finish(Activity.RESULT_CANCELED,invalid);return;
        }
        try{
            boolean auditModelNetwork="true".equals(runArguments.getString("auditModelNetwork","false"));
            if(auditModelNetwork&&!getTargetContext().getPackageName().equals("app.quietreader.discoveryprobe"))throw new IllegalStateException("Network audit requires isolated target");
            long[] networkBefore=null;
            foregroundModels=phase.equals("foreground-models");
            String modelInput=runArguments.getString("modelInput","short");
            if(!Arrays.asList("short","boundary").contains(modelInput)||!foregroundModels&&!modelInput.equals("short"))throw new IllegalArgumentException("Boundary inputs require isolated foreground workload");
            boundaryModelInput=modelInput.equals("boundary");
            report.append("MODEL_INPUT mode=").append(modelInput).append("; boundary uses service maximum query512/text4096 chars, not realistic corpus quality\n");
            if(foregroundModels&&!Arrays.asList("app.quietreader.flowprobe","app.quietreader.discoveryprobe").contains(getTargetContext().getPackageName()))throw new IllegalStateException("Foreground workload requires isolated target; ordinary user data is not a fixture");
            check((getTargetContext().getApplicationInfo().flags&ApplicationInfo.FLAG_DEBUGGABLE)==0,"installed target is non-debuggable release");
            if(cold){boolean absent=!new java.io.File(getTargetContext().getFilesDir(),"local-models").exists();check(absent,"cold install starts without model copies");if(!absent)throw new IllegalStateException("Cold fixture is not fresh");}
            activity=startActivitySync(new Intent().setClassName(getTargetContext().getPackageName(),"app.quietreader.DiscoveryActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
            runOnMainSync(()->{View root=activity.getWindow().getDecorView();check("New".contentEquals(getTargetContext().getApplicationInfo().loadLabel(getTargetContext().getPackageManager())),"release launcher uses approved New name");check(find(root,"New")==null&&find(root,"News")==null,"release home omits in-app branding");for(String label:new String[]{"设置"})check(find(root,label)!=null,"release native entry exists: "+label);});
            click("设置");menu("内容来源");click("返回");click("设置");menu("高级与诊断");menu("本机推荐状态");menu("校验并修复模型");
            long repairStart=SystemClock.elapsedRealtime(),repairDeadline=repairStart+60_000;boolean ready=false;
            while(SystemClock.elapsedRealtime()<repairDeadline&&!ready){
                click("设置");menu("高级与诊断");menu("本机推荐状态");
                android.view.accessibility.AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
                ready=root!=null&&!root.findAccessibilityNodeInfosByText("离线模型校验完成").isEmpty();
                boolean failed=root!=null&&!root.findAccessibilityNodeInfosByText("模型修复未完成").isEmpty();
                menu("关闭");if(failed)throw new IllegalStateException("Bundled model repair failed");if(!ready)Thread.sleep(250);
            }
            check(ready,"release model repair UI confirms bundled model integrity");if(!ready)throw new IllegalStateException("Model preparation timeout");
            report.append("modelPreparationMs=").append(SystemClock.elapsedRealtime()-repairStart).append(" cold=").append(cold).append('\n');
            if(foregroundModels){
                long focusDeadline=SystemClock.elapsedRealtime()+5000;boolean[] focused={false};
                do{runOnMainSync(()->focused[0]=activity.hasWindowFocus());if(!focused[0])Thread.sleep(50);}while(!focused[0]&&SystemClock.elapsedRealtime()<focusDeadline);
                runOnMainSync(()->{activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);check(activity.hasWindowFocus(),"native feed stays in foreground before direct service workload");});
                report.append("WORKLOAD foreground native feed plus direct real service requests; not production controller ranking or personal recommendation benefit; no cache deletion\n");
            }else runOnMainSync(activity::finish);
            waitForIdleSync();
            if(auditModelNetwork){
                if(foregroundModels)throw new IllegalArgumentException("Quiescent model network audit requires finished feed");
                Thread.sleep(2000);long rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()),tx=android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
                Thread.sleep(2000);networkBefore=new long[]{android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()),android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid())};
                check(rx>=0&&tx>=0&&networkBefore[0]>=0&&networkBefore[1]>=0,"Android exposes supported app-UID network byte counters");
                check(rx==networkBefore[0]&&tx==networkBefore[1],"app UID is network-quiet for two seconds before model binding");
                report.append("MODEL_NETWORK_BASELINE uid=").append(android.os.Process.myUid()).append(" rx=").append(networkBefore[0]).append(" tx=").append(networkBefore[1]).append(" scope=all same-UID interfaces counted by Android; no socket-attempt or isolated-renderer proof\n");
            }
            runOnMainSync(()->{inbox=new Messenger(new Handler(Looper.getMainLooper(),message->{Message copy=Message.obtain();copy.what=message.what;copy.setData(new Bundle(message.getData()));replies.add(copy);return true;}));bound=getTargetContext().bindService(new Intent().setClassName(getTargetContext().getPackageName(),"app.quietreader.ai.RankingService"),connection,Context.BIND_AUTO_CREATE);});
            if(!bound||!connected.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Release service bind failed");send(3,new Bundle());Message hello=replies.poll(5,TimeUnit.SECONDS);check(hello!=null&&hello.what==3&&hello.getData().getInt("pid")!=android.os.Process.myPid(),"release model worker runs in separate process");
            int modelRounds=1,modelHoldMs=0;
            if(foregroundModels){
                modelRounds=Integer.parseInt(runArguments.getString("modelRounds","1"));
                modelHoldMs=Integer.parseInt(runArguments.getString("modelHoldMs","0"));
                if(modelRounds<1||modelRounds>4||modelHoldMs<0||modelHoldMs>2000)throw new IllegalArgumentException("Bounded foreground sampling requires 1..4 rounds and 0..2000ms hold");
                report.append("MODEL_SAMPLING rounds=").append(modelRounds).append(" holdMs=").append(modelHoldMs).append("; holds expose resident allocations, not active inference duration\n");
            }
            for(int round=0;round<modelRounds;round++){
                model("embedding");if(modelHoldMs>0)Thread.sleep(modelHoldMs);
                model("reranker");if(modelHoldMs>0)Thread.sleep(modelHoldMs);
            }
            send(2,new Bundle());runOnMainSync(()->{getTargetContext().unbindService(connection);bound=false;});
            long deadline=SystemClock.elapsedRealtime()+5000;boolean alive=true;
            while(alive&&SystemClock.elapsedRealtime()<deadline){alive=false;java.util.List<ActivityManager.RunningAppProcessInfo> processes=((ActivityManager)getTargetContext().getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();if(processes!=null)for(ActivityManager.RunningAppProcessInfo process:processes)if(process.processName.equals(getTargetContext().getPackageName()+":ranking"))alive=true;if(alive)Thread.sleep(50);}
            check(!alive,"minified worker exits after release and unbind");
            if(auditModelNetwork){
                Thread.sleep(2000);long rx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()),tx=android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
                report.append("MODEL_NETWORK_RESULT rxDelta=").append(rx-networkBefore[0]).append(" txDelta=").append(tx-networkBefore[1]).append("; actual model binding, embedding, reranking, release and two-second accounting grace\n");
                check(rx==networkBefore[0]&&tx==networkBefore[1],"actual local dual-model run transfers zero additional app-UID network bytes in the measured interval");
                java.net.HttpURLConnection control=(java.net.HttpURLConnection)new java.net.URL("https://www.example.com/").openConnection();
                control.setConnectTimeout(5000);control.setReadTimeout(5000);control.setUseCaches(false);control.setInstanceFollowRedirects(false);int controlBytes=0;
                try{if(control.getResponseCode()!=200)throw new java.io.IOException("Counter calibration HTTP status");try(java.io.InputStream in=control.getInputStream()){byte[] buffer=new byte[4096];int size;while((size=in.read(buffer))!=-1){controlBytes+=size;if(controlBytes>32768)throw new java.io.IOException("Counter calibration size");}}}finally{control.disconnect();}
                Thread.sleep(2000);long controlRx=android.net.TrafficStats.getUidRxBytes(android.os.Process.myUid()),controlTx=android.net.TrafficStats.getUidTxBytes(android.os.Process.myUid());
                report.append("MODEL_NETWORK_POSITIVE_CONTROL bytes=").append(controlBytes).append(" rxDelta=").append(controlRx-rx).append(" txDelta=").append(controlTx-tx).append("; explicit www.example.com request after model interval, no cookies or content/feedback sent\n");
                check(controlBytes>0&&controlRx>rx&&controlTx>tx,"same-UID explicit HTTPS positive control advances both network counters");
            }
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{runOnMainSync(()->{if(bound){getTargetContext().unbindService(connection);bound=false;}if(activity!=null)activity.finish();});}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}





