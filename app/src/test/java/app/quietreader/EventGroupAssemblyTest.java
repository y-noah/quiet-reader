package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventGroupAssemblyTest {
    private static final long NOW=1_798_100_000_000L;
    private HotCandidate candidate(String id,String title){return new HotCandidate(Models.Source.ZHIHU,"https://example.org/"+id,title,"","",false,1,null,NOW);}
    private EventVerificationPlan.Task task(HotCandidate a,HotCandidate b){Map<String,float[]> cached=new HashMap<>();float[] unit=new float[512];unit[0]=1;cached.put(SemanticOrder.key(a.modelText()),unit);cached.put(SemanticOrder.key(b.modelText()),unit);return EventVerificationPlan.create(Arrays.asList(a,b),cached,"model-v1").get(0);}
    private EventGroupAssembly.ScoredPair score(HotCandidate a,HotCandidate b,float forward,float backward){return new EventGroupAssembly.ScoredPair(task(a,b),forward,backward,true);}
    private List<FeedStore.Row> rows(HotCandidate... input){return CandidateOrder.forModels(Arrays.asList(input),new JSONArray());}
    private JSONArray feedback(String action,String target)throws Exception{return new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",target));}
    private HotCandidate a(){return candidate("a","王沪宁陪同朝鲜最高领导人金正恩访问丹东");}
    private HotCandidate b(){return candidate("b","王沪宁陪同金正恩访问丹东");}
    @Test public void verifiedPairPreservesLeadingCardOriginalEntriesAndMemberAnchors(){
        HotCandidate a=a(),b=b();List<FeedStore.Row> before=rows(a,b);List<FeedStore.Row> after=EventGroupAssembly.apply(before,new JSONArray(),Arrays.asList(score(a,b,9,8)),"model-v1");
        assertEquals(2,before.size());assertEquals(1,after.size());FeedStore.Row row=after.get(0);assertEquals(before.get(0).eventId,row.eventId);assertSame(before.get(0).candidate,row.candidate);assertEquals(before.get(0).reason,row.reason);assertEquals(2,row.entries.size());
        assertTrue(EventGroups.matches(row,a.id));assertTrue(EventGroups.matches(row,b.id));assertEquals(1,SourceFilter.apply(after,b.source.name()).size());
        assertEquals(2,before.size());assertEquals(1,before.get(0).entries.size());
    }
    @Test public void lowIncompleteNonfiniteWrongModelAndChangedEvidenceStaySeparate(){
        HotCandidate a=a(),b=b();List<FeedStore.Row> input=rows(a,b);
        for(float[] values:new float[][]{{9,6.9f},{Float.NaN,9},{9,Float.POSITIVE_INFINITY}})assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),Arrays.asList(score(a,b,values[0],values[1])),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),Collections.emptyList(),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),Arrays.asList(score(a,b,9,9)),"model-v2").size());
        HotCandidate changed=new HotCandidate(b.source,b.originalUrl,b.title,"新增摘要","",false,1,null,NOW);assertEquals(2,EventGroupAssembly.apply(rows(a,changed),new JSONArray(),Arrays.asList(score(a,b,9,9)),"model-v1").size());
        HotCandidate later=new HotCandidate(b.source,b.originalUrl,b.title,"","",false,1,null,NOW+1);assertEquals(2,EventGroupAssembly.apply(rows(a,later),new JSONArray(),Arrays.asList(score(a,b,9,9)),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),Arrays.asList(score(a,b,9,9),score(a,b,1,1)),"model-v1").size());
    }
    @Test public void manualSplitAndCapturedEnoughOrBlockSurviveAssemblyAndUndo()throws Exception{
        HotCandidate a=a(),b=b();List<EventGroupAssembly.ScoredPair> scores=Arrays.asList(score(a,b,9,9));List<FeedStore.Row> merged=EventGroupAssembly.apply(rows(a,b),new JSONArray(),scores,"model-v1");FeedStore.Row row=merged.get(0);
        assertEquals(2,EventGroupAssembly.apply(merged,feedback("SEPARATE",EventTargets.capture(row,false)),scores,"model-v1").size());
        assertEquals(1,EventGroupAssembly.apply(rows(a,b),new JSONArray(),scores,"model-v1").size());
        assertTrue(EventGroupAssembly.apply(rows(a,b),feedback("BLOCK",EventTargets.capture(row,false)),scores,"model-v1").isEmpty());
        assertTrue(EventGroupAssembly.apply(rows(a,b),feedback("ENOUGH",EventTargets.capture(row,true)),scores,"model-v1").isEmpty());
        HotCandidate changed=new HotCandidate(b.source,b.originalUrl,b.title,"新增摘要","",false,1,null,NOW);assertEquals(1,EventGroupAssembly.apply(rows(a,changed),feedback("ENOUGH",EventTargets.capture(row,true)),scores,"model-v1").size());
    }
    @Test public void incompleteTriangleNeverMergesThroughMiddleArticle(){
        HotCandidate a=a(),b=b(),c=candidate("c","王沪宁陪同金正恩到访丹东");List<FeedStore.Row> input=Arrays.asList(new FeedStore.Row(a.id,a,"a"),new FeedStore.Row(b.id,b,"b"),new FeedStore.Row(c.id,c,"c"));
        List<EventGroupAssembly.ScoredPair> partial=Arrays.asList(score(a,b,9,9),score(b,c,9,9));List<FeedStore.Row> result=EventGroupAssembly.apply(input,new JSONArray(),partial,"model-v1");assertEquals(2,result.size());assertEquals(2,result.get(0).entries.size());
        List<EventGroupAssembly.ScoredPair> complete=new ArrayList<>(partial);complete.add(score(a,c,9,9));assertEquals(1,EventGroupAssembly.apply(input,new JSONArray(),complete,"model-v1").size());
    }
    @Test public void strongerCompleteGroupsAreNotBlockedByAnEarlierWeakerJoin()throws Exception{
        HotCandidate a=a(),b=b(),c=candidate("c","王沪宁陪同金正恩到访丹东"),d=candidate("d","王沪宁陪同金正恩正式访问丹东");
        List<FeedStore.Row> input=List.of(new FeedStore.Row(a.id,a,"a"),new FeedStore.Row(b.id,b,"b"),new FeedStore.Row(c.id,c,"c"),new FeedStore.Row(d.id,d,"d"));
        List<EventGroupAssembly.ScoredPair> evidence=new ArrayList<>(List.of(score(a,b,7.1f,7.1f),score(a,d,7.9f,7.9f),score(b,c,7.3f,7.3f),score(b,d,6.8f,6.8f)));
        for(int round=0;round<2;round++){
            List<FeedStore.Row> result=EventGroupAssembly.apply(input,new JSONArray(),evidence,"model-v1");
            assertEquals("A weaker A-B edge must not block the two stronger complete groups A-D and B-C",2,result.size());
            assertEquals(a.id,result.get(0).candidate.id);assertEquals(b.id,result.get(1).candidate.id);
            assertEquals(List.of(a.id,d.id),result.get(0).entries.stream().map(entry->entry.id).toList());
            assertEquals(List.of(b.id,c.id),result.get(1).entries.stream().map(entry->entry.id).toList());
            assertEquals(4,result.stream().mapToInt(row->row.entries.size()).sum());
            Collections.reverse(evidence);
        }
        assertEquals(3,EventGroupAssembly.apply(input,feedback("SEPARATE",d.id),evidence,"model-v1").size());
    }
    @Test public void exactSourceCopiesStillRequireEveryCrossMemberVerification(){
        HotCandidate a=a(),copy=new HotCandidate(Models.Source.THEPAPER,"https://example.org/copy",a.title,"","",false,1,null,NOW),b=b();
        List<FeedStore.Row> input=rows(a,copy,b);assertEquals(2,input.size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(score(a,b,9,9)),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(score(a,b,9,9),score(copy,b,9,6.9f)),"model-v1").size());
        List<FeedStore.Row> complete=EventGroupAssembly.apply(input,new JSONArray(),List.of(score(a,b,9,9),score(copy,b,9,9)),"model-v1");
        assertEquals(1,complete.size());assertEquals(3,complete.get(0).entries.size());
    }
    @Test public void confidenceIsRecomputedAfterEachGroupGrows(){
        HotCandidate a=a(),b=b(),c=candidate("c","王沪宁陪同金正恩到访丹东"),d=candidate("d","王沪宁陪同金正恩正式访问丹东");
        List<FeedStore.Row> input=List.of(new FeedStore.Row(a.id,a,"a"),new FeedStore.Row(b.id,b,"b"),new FeedStore.Row(c.id,c,"c"),new FeedStore.Row(d.id,d,"d"));
        List<EventGroupAssembly.ScoredPair> evidence=List.of(score(a,b,9,9),score(a,c,8.8f,8.8f),score(b,c,7.2f,7.2f),score(c,d,8,8));
        List<FeedStore.Row> result=EventGroupAssembly.apply(input,new JSONArray(),evidence,"model-v1");
        assertEquals(2,result.size());
        assertEquals("After A-B joins, its strength to C is 7.2, below C-D's 8.0",List.of(a.id,b.id),result.get(0).entries.stream().map(entry->entry.id).toList());
        assertEquals(List.of(c.id,d.id),result.get(1).entries.stream().map(entry->entry.id).toList());
    }
    @Test public void distinctV2exThreadsCannotBeJoinedThroughAnArticle()throws Exception{
        HotCandidate a=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/111",a().title,"","",false,1,null,NOW),b=b(),c=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/112","王沪宁陪同金正恩到访丹东","","",false,1,null,NOW);
        List<FeedStore.Row> input=Arrays.asList(new FeedStore.Row(a.id,a,"a"),new FeedStore.Row(b.id,b,"b"),new FeedStore.Row(c.id,c,"c"));
        List<EventGroupAssembly.ScoredPair> scores=Arrays.asList(score(a,b,9,9),score(b,c,9,9));List<FeedStore.Row> merged=EventGroupAssembly.apply(input,new JSONArray(),scores,"model-v1");assertEquals(2,merged.size());
        for(FeedStore.Row row:merged)assertTrue(row.entries.stream().filter(entry->entry.source==Models.Source.V2EX).count()<=1);
        FeedStore.Row first=merged.get(0);List<FeedStore.Row> hidden=EventGroupAssembly.apply(input,feedback("BLOCK",EventTargets.capture(first,false)),scores,"model-v1");assertEquals(1,hidden.size());assertEquals(c.id,hidden.get(0).candidate.id);
    }
    @Test public void selectedReaderMemberIsKeptWhenEntryListHasAnotherFirstMember(){
        HotCandidate a=a(),b=b();FeedStore.Row selected=new FeedStore.Row(a.id,b,"原有理由",Arrays.asList(a,b));
        List<FeedStore.Row> result=EventGroupAssembly.apply(Arrays.asList(selected),new JSONArray(),Arrays.asList(score(a,b,9,9)),"model-v1");
        assertEquals(1,result.size());assertEquals(b.id,result.get(0).candidate.id);assertEquals("原有理由",result.get(0).reason);assertTrue(EventGroups.matches(result.get(0),a.id));
    }
    @Test public void highScoresCannotMergeWithoutCompleteInputsOrWithConflictingCoverage(){
        HotCandidate a=a(),b=b();EventVerificationPlan.Task task=task(a,b);List<FeedStore.Row> input=rows(a,b);
        EventGroupAssembly.ScoredPair unknown=new EventGroupAssembly.ScoredPair(task,99,99);
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(unknown),"model-v1").size());
        EventGroupAssembly.ScoredPair complete=new EventGroupAssembly.ScoredPair(task,99,99,true);
        assertEquals(1,EventGroupAssembly.apply(input,new JSONArray(),List.of(complete),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(complete,unknown),"model-v1").size());
    }
    @Test public void completeQuotedHeadlineIsNotAnAssertedEventEvenWithHighRelevance()throws Exception{
        // These synthetic quotation prefixes exceeded 7.0 in the frozen desktop
        // INT8 challenge. This regression uses deliberately high injected scores
        // to exercise the current admission/assembly guard, not model accuracy.
        String headline="傲慢的皇马还要装多久鸵鸟";
        java.lang.reflect.Constructor<EventVerificationPlan.Task> constructor=EventVerificationPlan.Task.class.getDeclaredConstructor(HotCandidate.class,HotCandidate.class,double.class,String.class);
        constructor.setAccessible(true);
        for(String prefix:new String[]{"有人杜撰了这句话","网上流传的虚构标题","请别再照搬这个标题","上个月的旧文章标题","被冒用的文章标题","原作者撤回了这个标题","这句话断章取义"}){
            HotCandidate a=candidate("quote",prefix+"，"+headline),b=candidate("original",headline);
            Map<String,float[]> vectors=new HashMap<>();float[] vector=new float[512];vector[0]=1;
            vectors.put(SemanticOrder.key(a.modelText()),vector);vectors.put(SemanticOrder.key(b.modelText()),vector);
            assertTrue(prefix,EventVerificationPlan.create(List.of(a,b),vectors,"model-v1").isEmpty());
            EventVerificationPlan.Task delivered=constructor.newInstance(a,b,1.0,"model-v1");
            List<FeedStore.Row> result=EventGroupAssembly.apply(rows(a,b),new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(delivered,99,99,true)),"model-v1");
            assertEquals(prefix,2,result.size());
            Set<String> ids=new HashSet<>();for(FeedStore.Row row:result)for(HotCandidate member:row.entries)ids.add(member.id);
            assertEquals(Set.of(a.id,b.id),ids);
        }
    }
    @Test public void copiedStatementExcerptAndHighRelevanceCannotSupplySpeakerOrCurrentAssertion()throws Exception{
        // Offline metadata-context experiment raised these synthetic controls
        // above7. The following99/99 values are deliberately injected, not
        // actual model logits. Even delivered old-policy evidence must not
        // bypass current admission or manufacture an event identity.
        String original="央视体育青少中心：不认可皇马的表态，在道歉没有到位之前一切免谈";
        String copied="伤害中国人民感情的言行自然会遭到中国社会各界的共同抵制，希望皇马不要一厢情愿做白日梦。在皇马的道歉没有到位之前，一切免谈。";
        java.lang.reflect.Constructor<EventVerificationPlan.Task> constructor=EventVerificationPlan.Task.class.getDeclaredConstructor(HotCandidate.class,HotCandidate.class,double.class,String.class);
        constructor.setAccessible(true);
        for(String title:new String[]{"总台财经节目中心发言人：不认可皇马的表态","总台体育青少中心发言人：尚未就皇马表态作出回应","总台体育青少中心发言人：网传不认可皇马的表态不实","总台体育青少中心发言人：是否认可皇马的表态","总台体育青少中心发言人：去年不认可皇马的表态","网友假冒总台体育青少中心：不认可皇马的表态","总台体育青少中心回应：不认可皇马的表态系断章取义"}){
            HotCandidate a=candidate("statement",original),b=new HotCandidate(Models.Source.ZHIHU,"https://example.org/statement-context",title,copied,"",false,1,null,NOW);
            Map<String,float[]> vectors=new HashMap<>();float[] vector=new float[512];vector[0]=1;
            vectors.put(SemanticOrder.key(a.modelText()),vector);vectors.put(SemanticOrder.key(b.modelText()),vector);
            assertTrue(title,EventVerificationPlan.create(List.of(a,b),vectors,"model-v1").isEmpty());
            EventVerificationPlan.Task delivered=constructor.newInstance(a,b,1.0,"model-v1");
            List<FeedStore.Row> result=EventGroupAssembly.apply(rows(a,b),new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(delivered,99,99,true)),"model-v1");
            assertEquals(title,2,result.size());Set<String> ids=new HashSet<>();for(FeedStore.Row row:result)for(HotCandidate member:row.entries)ids.add(member.id);
            assertEquals(Set.of(a.id,b.id),ids);
        }
    }
    @Test public void deliveredOldPolicyScoresCannotBypassCurrentPredicateProtection()throws Exception{
        // Model scores may predate a policy update. Construct the delivered task
        // directly because the current planner must no longer admit these pairs.
        java.lang.reflect.Constructor<EventVerificationPlan.Task> constructor=EventVerificationPlan.Task.class.getDeclaredConstructor(HotCandidate.class,HotCandidate.class,double.class,String.class);
        constructor.setAccessible(true);
        for(String title:new String[]{"该公司未发布新款折叠屏手机","该公司计划发布新款折叠屏手机","该公司是否发布新款折叠屏手机"}){
            HotCandidate a=candidate("complete","该公司发布新款折叠屏手机"),b=candidate("qualified",title);
            Map<String,float[]> vectors=new HashMap<>();float[] vector=new float[512];vector[0]=1;
            vectors.put(SemanticOrder.key(a.modelText()),vector);vectors.put(SemanticOrder.key(b.modelText()),vector);
            assertTrue("No new inference on contradictory claims",EventVerificationPlan.create(List.of(a,b),vectors,"model-v1").isEmpty());
            EventVerificationPlan.Task delivered=constructor.newInstance(a,b,1.0,"model-v1");
            assertEquals("Synthetic high logits never override current facts",2,EventGroupAssembly.apply(rows(a,b),new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(delivered,99,99,true)),"model-v1").size());
        }
    }
}
