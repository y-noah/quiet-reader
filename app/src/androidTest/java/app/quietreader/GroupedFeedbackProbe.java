package app.quietreader;

import android.app.*;
import android.content.Intent;
import android.os.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static app.quietreader.Models.*;

/** Real controller/SQLite group feedback, updates and one-action undo. Synthetic text only. */
final class GroupedFeedbackProbe {
    private final Instrumentation test;private DiscoveryActivity activity;private DiscoveryController controller;private FeedStore store;private ExecutorService worker;
    private int checks,failures;private final StringBuilder report=new StringBuilder();
    GroupedFeedbackProbe(Instrumentation test){this.test=test;}
    private Object field(Object owner,String name){try{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}catch(Exception e){throw new IllegalStateException(e);}}
    private void ui(Runnable r){test.runOnMainSync(r);}
    private boolean onUi(BooleanSupplier condition){boolean[] result={false};ui(()->result[0]=condition.getAsBoolean());return result[0];}
    private void await(String stage,BooleanSupplier condition)throws Exception{long end=SystemClock.elapsedRealtime()+8000;while(!onUi(condition)){if(SystemClock.elapsedRealtime()>end)throw new IllegalStateException("Group feedback deadline: "+stage);Thread.sleep(30);}}
    private void check(boolean value,String label){checks++;if(!value)failures++;report.append(value?"PASS ":"FAIL ").append(label).append('\n');}
    @SuppressWarnings("unchecked") private List<FeedStore.Row> rows(){return (List<FeedStore.Row>)field(activity,"feed");}
    private boolean contains(String id){return rows().stream().flatMap(row->row.entries.stream()).anyMatch(c->c.id.equals(id));}
    private void publish(List<HotCandidate> candidates)throws Exception{worker.submit(()->{for(Source source:List.of(Source.ZHIHU,Source.WEIBO,Source.V2EX,Source.ITHOME,Source.THEPAPER)){List<HotCandidate> part=new ArrayList<>();for(HotCandidate c:candidates)if(c.source==source)part.add(c);store.upsertSnapshot(source,store.beginRefresh(source),part,System.currentTimeMillis());}}).get(5,TimeUnit.SECONDS);long[] previous={0};ui(()->{previous[0]=(long)field(controller,"snapshot");controller.sort(false);});await("published snapshot",()->(long)field(controller,"snapshot")!=previous[0]&&(long)field(controller,"pending")==0);}
    private void feedback(FeedStore.Row row,String action)throws Exception{long[] previous={0};ui(()->{previous[0]=(long)field(controller,"snapshot");controller.feedback(row,action);});await(action,()->(long)field(controller,"snapshot")!=previous[0]&&(long)field(controller,"pending")==0);}
    private void undo(String id)throws Exception{long[] previous={0};ui(()->{previous[0]=(long)field(controller,"snapshot");controller.undo(id);});await("undo",()->(long)field(controller,"snapshot")!=previous[0]&&(long)field(controller,"pending")==0);}
    private HotCandidate changed(HotCandidate c){return new HotCandidate(c.source,c.originalUrl,c.title+"，已有新的实质进展",c.snippet+"后续实验数据已更新。","",false,c.sourceRank,c.publishedAt,System.currentTimeMillis());}
    void run(){String database="test-ui-group-feedback-"+UUID.randomUUID()+".db";List<HotCandidate> candidates=new ArrayList<>();long now=System.currentTimeMillis();
        try{
            Source[] sources={Source.ZHIHU,Source.WEIBO,Source.V2EX,Source.ITHOME,Source.THEPAPER};String[] urls={"https://www.zhihu.com/question/990041","https://weibo.com/123/Abc990041","https://www.v2ex.com/t/990041","https://www.ithome.com/1/999/041.htm","https://www.thepaper.cn/newsDetail_forward_990041"};
            for(int n=0;n<sources.length;n++)candidates.add(new HotCandidate(sources[n],urls[n],"空间站完成一项新的科学实验验证","空间站乘组在实验舱完成了新的科学实验，记录了实验数据并传回地面研究团队。","",false,n+1,now,now));
            HotCandidate other=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990042","无关的中文话题","","",false,10,null,now),angle=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990043","空间站的另一项独立研究讨论","这是另一项研究，不能因同平台或包含相同名词被屏蔽。","",false,11,now,now);candidates.add(other);
            try(FeedStore seed=new FeedStore(test.getTargetContext(),database)){for(Source source:sources){List<HotCandidate> part=new ArrayList<>();for(HotCandidate c:candidates)if(c.source==source)part.add(c);seed.upsertSnapshot(source,seed.beginRefresh(source),part,now);}}
            activity=(DiscoveryActivity)test.startActivitySync(new Intent(test.getTargetContext(),DiscoveryActivity.class).putExtra("test_skip_fetch",true).putExtra("test_database",database).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));controller=(DiscoveryController)field(activity,"controller");store=(FeedStore)field(controller,"store");worker=(ExecutorService)field(controller,"storage");await("initial group",()->rows().size()==2);
            FeedStore.Row group=rows().stream().filter(row->row.entries.size()==5).findFirst().orElseThrow();feedback(group,"BLOCK");check(onUi(()->rows().size()==1&&contains(other.id)),"blocking one five-source card hides its known group without removing unrelated content");
            JSONArray blocked=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();check(blocked.length()==1,"group block is one persisted reversible feedback event");
            HotCandidate updated=changed(candidates.get(0));candidates.set(0,updated);candidates.add(angle);publish(candidates);
            check(onUi(()->!contains(updated.id)&&contains(angle.id)&&contains(other.id)),"permanent group block survives known member metadata changes without blocking a new angle");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false).stream().flatMap(row->row.entries.stream()).noneMatch(c->c.id.equals(updated.id)),"reopened database retains known-member block scope");}
            undo(blocked.getJSONObject(0).getString("id"));check(onUi(()->candidates.stream().allMatch(c->contains(c.id))),"one undo restores every known group member including its new version");
            FeedStore.Row remaining=rows().stream().filter(row->row.entries.size()==4).findFirst().orElseThrow();feedback(remaining,"ENOUGH");check(onUi(()->contains(updated.id)&&remaining.entries.stream().noneMatch(c->contains(c.id))),"enough hides only captured versions and leaves already updated content available");
            JSONArray enough=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();check(enough.length()==1,"group enough also remains one reversible feedback event");
            Set<String> expectedTargets=new HashSet<>();expectedTargets.add(remaining.eventId+"@"+remaining.candidate.textHash);
            for(HotCandidate member:remaining.entries){expectedTargets.add(CandidateOrder.versionTarget(member));if(member.source==Source.V2EX)expectedTargets.add(CandidateOrder.arrange(Collections.singletonList(member),new JSONArray(),false).get(0).eventId+"@"+member.textHash);}
            check(enough.getJSONObject(0).getString("target").length()>512&&EventTargets.expand(enough.getJSONObject(0).getString("target")).equals(expectedTargets),"complete four-source target retains every captured version plus stable discussion alias beyond the old length cap");
            String structured=enough.getJSONObject(0).getString("target");check(worker.submit(()->{String[][] invalid={{"MORE","sample",structured},{"BLOCK","source",structured},{"SEPARATE","source",structured},{"SEPARATE","event","{"+"x".repeat(600)},{"BLOCK","event","{"+"x".repeat(600)}};for(int n=0;n<invalid.length;n++){try{store.feedback("invalid-scope-"+n,invalid[n][0],invalid[n][1],invalid[n][2],System.currentTimeMillis(),null,"不应保存");return false;}catch(IllegalArgumentException expected){}}return store.activeFeedback(System.currentTimeMillis()).length()==1;}).get(),"expanded feedback target length remains restricted to validated event actions and payloads");
            HotCandidate next=changed(candidates.get(1));candidates.set(1,next);publish(candidates);check(onUi(()->contains(next.id)&&remaining.entries.stream().filter(c->!c.id.equals(next.id)).noneMatch(c->contains(c.id))),"new member version returns after enough while unchanged source versions stay hidden");
            undo(enough.getJSONObject(0).getString("id"));check(onUi(()->candidates.stream().allMatch(c->contains(c.id))),"one enough undo restores all captured source versions");
            HotCandidate replyA=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990044#reply72","回复数变化仍是同一个中文讨论","完整摘要及发布时间不能覆盖已明确的讨论帖子身份，两个版本均保留原始入口。","",false,1,now,now),replyB=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990044#reply73","同一个讨论修改后的完整中文标题",replyA.snippet+"更新说明。","",false,2,now,now);
            candidates.set(2,replyA);candidates.add(replyB);publish(candidates);
            FeedStore.Row thread=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(replyA.id))).findFirst().orElseThrow();
            check(thread.entries.size()==2&&thread.entries.stream().anyMatch(c->c.id.equals(replyB.id)),"actual SQLite snapshot groups observed reply-count URL aliases into one thread card");
            feedback(thread,"BLOCK");JSONArray threadBlock=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
            HotCandidate replyC=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990044#reply74","该讨论标题有更新但屏蔽仍有效","","",false,1,null,now+1);candidates.set(2,replyC);publish(candidates);
            check(onUi(()->!contains(replyC.id)&&!contains(replyB.id)&&contains(other.id)),"controller thread block survives both reply count and title changes while unrelated topics remain");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false).stream().flatMap(row->row.entries.stream()).noneMatch(c->c.id.equals(replyC.id)||c.id.equals(replyB.id)),"reopened SQLite retains the stable thread block without rewriting candidate IDs");}
            undo(threadBlock.getJSONObject(0).getString("id"));check(onUi(()->contains(replyC.id)&&contains(replyB.id)),"single persisted thread-block undo restores its current reply-count variants");
            String headline="县委书记回应被扔下水";
            HotCandidate hotA=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990050","如何看待"+headline+"？","","source-a",false,1,null,now);
            HotCandidate hotB=new HotCandidate(Source.WEIBO,"https://weibo.com/123/Abc990050","#"+headline+"#","","source-b",false,2,null,now);
            candidates.add(hotA);candidates.add(hotB);publish(candidates);
            FeedStore.Row hot=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(hotA.id))).findFirst().orElseThrow();
            check(hot.entries.size()==2&&hot.entries.stream().anyMatch(c->c.id.equals(hotB.id))&&hot.candidate.rawHeat.equals("source-a"),"controller combines headline wrappers, retains source entries and does not add heat");
            long[] beforeSplit={0};FeedStore.Row toSeparate=hot;
            ui(()->{beforeSplit[0]=(long)field(controller,"snapshot");controller.separate(toSeparate);});await("wrapper separation",()->(long)field(controller,"snapshot")!=beforeSplit[0]&&(long)field(controller,"pending")==0);
            check(onUi(()->rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(hotA.id)||c.id.equals(hotB.id))).count()==2),"real separation action displays both wrapper entries independently");
            JSONArray splitEvents=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false).stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(hotA.id)||c.id.equals(hotB.id))).count()==2,"SQLite reopen preserves wrapper separation");}
            undo(splitEvents.getJSONObject(0).getString("id"));
            hot=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(hotA.id))).findFirst().orElseThrow();feedback(hot,"ENOUGH");
            JSONArray hotEnough=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
            HotCandidate hotC=new HotCandidate(Source.ITHOME,"https://www.ithome.com/1/999/050.htm","热议："+headline,"","source-c",false,1,null,now);
            HotCandidate progress=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_990051",headline+"的调查结果公布","","",false,1,null,now);
            candidates.add(hotC);candidates.add(progress);publish(candidates);
            check(onUi(()->!contains(hotA.id)&&!contains(hotB.id)&&!contains(hotC.id)&&contains(progress.id)),"enough suppresses unseen cosmetic wrapper versions but preserves substantive progress");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){List<FeedStore.Row> restored=CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false);check(restored.stream().flatMap(row->row.entries.stream()).noneMatch(c->c.id.equals(hotC.id))&&restored.stream().flatMap(row->row.entries.stream()).anyMatch(c->c.id.equals(progress.id)),"SQLite reopen preserves cosmetic-version enough without hiding progress");}
            undo(hotEnough.getJSONObject(0).getString("id"));
            hot=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(hotA.id))).findFirst().orElseThrow();
            check(hot.entries.size()==3,"one enough undo restores all three observed headline wrappers");feedback(hot,"BLOCK");
            JSONArray hotBlock=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
            HotCandidate hotD=new HotCandidate(Source.WEIBO,"https://weibo.com/123/Abc990052","如何评价「"+headline+"」一事？","","source-d",false,3,null,now);candidates.add(hotD);publish(candidates);
            check(onUi(()->!contains(hotA.id)&&!contains(hotB.id)&&!contains(hotC.id)&&!contains(hotD.id)&&contains(progress.id)),"group block covers a newly observed wrapper while keeping distinct follow-up content");
            undo(hotBlock.getJSONObject(0).getString("id"));
            check(onUi(()->rows().stream().anyMatch(row->row.entries.size()==4&&row.entries.stream().anyMatch(c->c.id.equals(hotD.id)))),"one block undo restores the four-source-entry headline family");
            HotCandidate decimal=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990060","今年本地区经济同比增长1.5%","","decimal",false,1,null,now);
            HotCandidate integer=new HotCandidate(Source.WEIBO,"https://weibo.com/123/Abc990060","今年本地区经济同比增长15%","","integer",false,1,null,now);
            HotCandidate fullwidth=new HotCandidate(Source.ITHOME,"https://www.ithome.com/1/999/060.htm","如何看待今年本地区经济同比增长１．５％？","","fullwidth",false,1,null,now);
            candidates.add(decimal);candidates.add(integer);candidates.add(fullwidth);publish(candidates);
            FeedStore.Row decimalGroup=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(decimal.id))).findFirst().orElseThrow();
            check(decimalGroup.entries.size()==2&&decimalGroup.entries.stream().anyMatch(c->c.id.equals(fullwidth.id))&&decimalGroup.entries.stream().noneMatch(c->c.id.equals(integer.id)),"controller preserves decimal facts while grouping fullwidth presentation variants");
            feedback(decimalGroup,"BLOCK");JSONArray numericBlock=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
            HotCandidate anotherDecimal=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_990060","热议：今年本地区经济同比增长1.5%","","new-decimal",false,1,null,now);candidates.add(anotherDecimal);publish(candidates);
            check(onUi(()->!contains(decimal.id)&&!contains(fullwidth.id)&&!contains(anotherDecimal.id)&&contains(integer.id)),"numeric family block covers unseen wrappers without suppressing the distinct integer fact");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){List<FeedStore.Row> restored=CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false);check(restored.stream().flatMap(row->row.entries.stream()).noneMatch(c->c.id.equals(decimal.id)||c.id.equals(fullwidth.id)||c.id.equals(anotherDecimal.id))&&restored.stream().flatMap(row->row.entries.stream()).anyMatch(c->c.id.equals(integer.id)),"SQLite reopen preserves the decimal block without reviving lossy numeric aliases");}
            undo(numericBlock.getJSONObject(0).getString("id"));check(onUi(()->rows().stream().anyMatch(row->row.entries.size()==3&&row.entries.stream().anyMatch(c->c.id.equals(decimal.id)))&&contains(integer.id)),"one numeric block undo restores its three variants and keeps the separate integer card");
            String bridgeTitle="研究团队公布新型电池测试结果",bridgeSummary="研究团队公布新型电池的完整测试数据，说明了测试环境、循环次数和实际测量结果，后续将继续开展实验。";
            HotCandidate completeA=new HotCandidate(Source.ZHIHU,"https://www.zhihu.com/question/990070",bridgeTitle,bridgeSummary,"complete-a",false,1,now,now);
            HotCandidate completeB=new HotCandidate(Source.ITHOME,"https://www.ithome.com/1/999/070.htm",bridgeTitle,bridgeSummary,"complete-b",false,2,now,now);
            HotCandidate briefA=new HotCandidate(Source.WEIBO,"https://weibo.com/123/Abc990070","如何看待"+bridgeTitle+"？","","brief-a",false,3,null,now);
            HotCandidate briefB=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_990070","热议："+bridgeTitle,"","brief-b",false,4,null,now);
            candidates.addAll(List.of(completeA,completeB,briefA,briefB));publish(candidates);
            FeedStore.Row bridge=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(completeA.id))).findFirst().orElseThrow();
            check(bridge.entries.size()==4&&bridge.entries.stream().anyMatch(c->c.id.equals(briefA.id)),"controller bridges one complete evidence family with headline-only source entries");
            long[] beforeBridgeSplit={0};FeedStore.Row bridgeToSplit=bridge;ui(()->{beforeBridgeSplit[0]=(long)field(controller,"snapshot");controller.separate(bridgeToSplit);});await("metadata bridge separation",()->(long)field(controller,"snapshot")!=beforeBridgeSplit[0]&&(long)field(controller,"pending")==0);
            JSONArray bridgeSplit=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();Set<String> bridgeIds=Set.of(completeA.id,completeB.id,briefA.id,briefB.id);
            check(onUi(()->rows().stream().filter(row->row.entries.stream().anyMatch(c->bridgeIds.contains(c.id))).count()==4)&&bridgeSplit.length()==1&&EventTargets.structuredTarget(bridgeSplit.getJSONObject(0).getString("target")),"one persisted separation captures both metadata families rather than only the displayed group key");
            candidates.removeIf(c->c.id.equals(completeA.id)||c.id.equals(completeB.id));publish(candidates);
            check(onUi(()->rows().stream().filter(row->row.entries.stream().anyMatch(c->bridgeIds.contains(c.id))).count()==2),"headline members stay separated after both complete entries disappear from active snapshots");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false).stream().filter(row->row.entries.stream().anyMatch(c->bridgeIds.contains(c.id))).count()==2,"SQLite reopen retains separation after complete-member disappearance");}
            undo(bridgeSplit.getJSONObject(0).getString("id"));check(onUi(()->rows().stream().anyMatch(row->row.entries.size()==2&&row.entries.stream().anyMatch(c->c.id.equals(briefA.id)))),"one bridge-separation undo restores the remaining headline family");
            candidates.addAll(List.of(completeA,completeB));publish(candidates);bridge=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(completeA.id))).findFirst().orElseThrow();feedback(bridge,"ENOUGH");
            JSONArray bridgeEnough=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();candidates.removeIf(c->c.id.equals(completeA.id)||c.id.equals(completeB.id));
            HotCandidate briefC=new HotCandidate(Source.ITHOME,"https://www.ithome.com/1/999/071.htm",bridgeTitle,"","brief-c",false,2,null,now);candidates.add(briefC);publish(candidates);
            check(onUi(()->!contains(briefA.id)&&!contains(briefB.id)&&!contains(briefC.id)),"captured enough versions survive complete-member disappearance and a new cosmetic headline entry");
            undo(bridgeEnough.getJSONObject(0).getString("id"));candidates.addAll(List.of(completeA,completeB));publish(candidates);
            bridge=rows().stream().filter(row->row.entries.stream().anyMatch(c->c.id.equals(completeA.id))).findFirst().orElseThrow();feedback(bridge,"BLOCK");JSONArray bridgeBlock=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();
            check(onUi(()->!contains(completeA.id)&&!contains(completeB.id)&&!contains(briefA.id)&&!contains(briefB.id)&&!contains(briefC.id)),"blocking a bridged family cannot leak headline-only members after complete IDs are filtered");
            candidates.removeIf(c->c.id.equals(completeA.id)||c.id.equals(completeB.id));publish(candidates);
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check(CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false).stream().flatMap(row->row.entries.stream()).noneMatch(c->bridgeIds.contains(c.id)||c.id.equals(briefC.id)),"reopened SQLite block keeps observed headline identity after complete members disappear");}
            undo(bridgeBlock.getJSONObject(0).getString("id"));check(onUi(()->rows().stream().anyMatch(row->row.entries.size()==3&&row.entries.stream().anyMatch(c->c.id.equals(briefC.id)))),"one bridge block undo restores all remaining headline variants");
            HotCandidate conflict=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_990072",bridgeTitle,bridgeSummary+"另一组实验否认了这些测试结果，尚未确认。","conflict",false,1,now,now);
            candidates.addAll(List.of(completeA,completeB,conflict));publish(candidates);Set<String> allBridgeIds=new HashSet<>(bridgeIds);allBridgeIds.add(briefC.id);allBridgeIds.add(conflict.id);
            check(onUi(()->rows().stream().filter(row->row.entries.stream().anyMatch(c->allBridgeIds.contains(c.id))).count()==3),"conflicting complete excerpts stay apart and headline-only entries do not connect their groups");
            HotCandidate independentA=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990080#reply1","相同完整文字仍然是两个独立讨论帖子","完整摘要和原始发布时间一致，但帖子编号不同，新单帖反馈不可继承另一帖的旧文字分组标识。","",false,1,now,now);
            HotCandidate independentB=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/990081#reply1",independentA.title,independentA.snippet,"",false,2,now,now);
            candidates.removeIf(c->c.source==Source.V2EX);candidates.addAll(List.of(independentA,independentB));publish(candidates);
            check(onUi(()->rows().stream().filter(row->row.entries.size()==1&&(row.candidate.id.equals(independentA.id)||row.candidate.id.equals(independentB.id))).count()==2),"controller keeps identical complete text on two explicit discussion IDs separate");
            FeedStore.Row single=rows().stream().filter(row->row.candidate.id.equals(independentA.id)).findFirst().orElseThrow();feedback(single,"BLOCK");
            check(onUi(()->!contains(independentA.id)&&contains(independentB.id)&&contains(other.id)),"new single-thread block does not leak through legacy complete-text aliases");
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){List<FeedStore.Row> visible=CandidateOrder.arrange(reopened.candidates(100,0),reopened.activeFeedback(System.currentTimeMillis()),false);check(visible.stream().flatMap(row->row.entries.stream()).anyMatch(c->c.id.equals(independentB.id))&&visible.stream().flatMap(row->row.entries.stream()).noneMatch(c->c.id.equals(independentA.id)),"SQLite reopen preserves explicit-thread feedback isolation");}
            JSONArray isolatedFeedback=worker.submit(()->store.activeFeedback(System.currentTimeMillis())).get();String isolatedId=null;
            for(int n=0;n<isolatedFeedback.length();n++){JSONObject event=isolatedFeedback.getJSONObject(n);if("BLOCK".equals(event.optString("action"))&&EventTargets.expand(event.optString("target")).contains(independentA.id))isolatedId=event.getString("id");}
            if(isolatedId==null)throw new IllegalStateException("New single-thread block missing");undo(isolatedId);
            check(onUi(()->rows().stream().filter(row->row.entries.size()==1&&(row.candidate.id.equals(independentA.id)||row.candidate.id.equals(independentB.id))).count()==2),"single undo restores both independent discussions without merging them");
        }catch(Throwable error){failures++;report.append("ERROR ").append(error.getClass().getSimpleName()).append(':').append(error.getMessage()).append('\n');}
        finally{ui(()->{if(activity!=null)activity.finish();});test.waitForIdleSync();if(worker!=null)try{worker.awaitTermination(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}test.getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
