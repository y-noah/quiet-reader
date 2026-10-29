package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventVerificationPlanTest {
    private static final long NOW=1_798_100_000_000L;
    private HotCandidate candidate(String suffix,String title,long time){return new HotCandidate(Models.Source.ZHIHU,"https://example.org/"+suffix,title,"","",false,1,null,time);}
    private float[] vector(float first,float second){float[] result=new float[512];result[0]=first;result[1]=second;return result;}
    private Map<String,float[]> vectors(List<HotCandidate> entries){Map<String,float[]> result=new HashMap<>();for(HotCandidate c:entries)result.put(SemanticOrder.key(c.modelText()),vector(1,0));return result;}
    @Test public void admissionUsesCachedVectorsAndNeverMergesFeed(){
        HotCandidate a=candidate("a","王沪宁陪同朝鲜最高领导人金正恩访问丹东",NOW),b=candidate("b","王沪宁陪同金正恩访问丹东",NOW);
        List<HotCandidate> input=Arrays.asList(a,b);List<EventVerificationPlan.Task> tasks=EventVerificationPlan.create(input,vectors(input),"model-v1");
        assertEquals(1,tasks.size());assertEquals(1,tasks.get(0).similarity,0.00001);
        assertEquals(2,CandidateOrder.arrange(input,new org.json.JSONArray(),false).size());
        assertTrue(EventVerificationPlan.create(input,Collections.emptyMap(),"model-v1").isEmpty());
        try{tasks.clear();fail();}catch(UnsupportedOperationException expected){}
    }
    @Test public void symmetricCacheIdentityInvalidatesMetadataTimeAndModelChanges(){
        HotCandidate a=candidate("a","王沪宁陪同朝鲜最高领导人金正恩访问丹东",NOW),b=candidate("b","王沪宁陪同金正恩访问丹东",NOW);
        List<HotCandidate> input=Arrays.asList(a,b);Map<String,float[]> cached=vectors(input);
        EventVerificationPlan.Task original=EventVerificationPlan.create(input,cached,"v1").get(0);String key=original.cacheKey;
        assertEquals(key,EventVerificationPlan.create(Arrays.asList(b,a),cached,"v1").get(0).cacheKey);
        assertNotEquals(key,EventVerificationPlan.create(input,cached,"v2").get(0).cacheKey);
        HotCandidate later=candidate("b",b.title,NOW+1);
        EventVerificationPlan.Task refreshed=EventVerificationPlan.create(Arrays.asList(a,later),cached,"v1").get(0);
        assertEquals("Refreshing metadata must not force new raw inference",key,refreshed.cacheKey);
        assertNotEquals(original.evidenceKey,refreshed.evidenceKey);
        HotCandidate enriched=new HotCandidate(Models.Source.ZHIHU,b.originalUrl,b.title,"新增来源原文说明","",false,1,NOW-1000,NOW);
        List<HotCandidate> enrichment=Arrays.asList(a,enriched);EventVerificationPlan.Task context=EventVerificationPlan.create(enrichment,vectors(enrichment),"v1").get(0);
        assertEquals(key,context.cacheKey);assertNotEquals(original.evidenceKey,context.evidenceKey);
        HotCandidate stale=candidate("b",b.title,NOW+86_400_001L);List<HotCandidate> staleInput=Arrays.asList(a,stale);
        assertTrue("Cached logits cannot bypass current time evidence",EventVerificationPlan.create(staleInput,vectors(staleInput),"v1").isEmpty());
        HotCandidate changed=candidate("b","王沪宁陪同金正恩到访丹东",NOW);List<HotCandidate> updated=Arrays.asList(a,changed);
        assertNotEquals(key,EventVerificationPlan.create(updated,vectors(updated),"v1").get(0).cacheKey);
    }
    @Test public void invalidVectorsAndConflictingClaimsNeverReachModelQueue(){
        HotCandidate a=candidate("a","该城市发现新的珍贵考古遗址",NOW),b=candidate("b","该城市尚未发现新的珍贵考古遗址",NOW);
        List<HotCandidate> input=Arrays.asList(a,b);assertTrue(EventVerificationPlan.create(input,vectors(input),"v1").isEmpty());
        b=candidate("c","该城市近日发现珍贵考古遗址",NOW);input=Arrays.asList(a,b);
        for(float[] invalid:new float[][]{new float[2],new float[512],vector(Float.NaN,0),vector(Float.POSITIVE_INFINITY,0)}){
            Map<String,float[]> cached=vectors(input);cached.put(SemanticOrder.key(b.modelText()),invalid);
            assertTrue(EventVerificationPlan.create(input,cached,"v1").isEmpty());
        }
        a=candidate("low-overlap","该城市发现一批古代青铜器考古遗址",NOW);input=Arrays.asList(a,b);
        Map<String,float[]> cached=vectors(input);cached.put(SemanticOrder.key(b.modelText()),vector(0,1));
        assertTrue(EventVerificationPlan.create(input,cached,"v1").isEmpty());
    }
    @Test public void retrievalThresholdIsStableAcrossFiniteVectorScales(){
        HotCandidate a=candidate("scale-a","海洋科学团队发现深海新型生命样本",NOW),b=candidate("scale-b","海洋科学团队出土远古沉船珍贵青铜文物",NOW);
        List<HotCandidate> input=Arrays.asList(a,b);assertEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(a,b));
        for(float scale:new float[]{1e-20f,1f,1e20f})for(float similarity:new float[]{.74f,.76f}){
            Map<String,float[]> cached=new HashMap<>();cached.put(SemanticOrder.key(a.modelText()),vector(scale,0));cached.put(SemanticOrder.key(b.modelText()),vector(similarity*scale,(float)Math.sqrt(1-similarity*similarity)*scale));
            List<EventVerificationPlan.Task> tasks=EventVerificationPlan.create(input,cached,"v1");assertEquals(similarity>.75f?1:0,tasks.size());
            if(!tasks.isEmpty())assertEquals(similarity,tasks.get(0).similarity,.00001);
        }
    }
    @Test public void queueIsBoundedAndDeterministicBeyondFirstFewStories(){
        List<HotCandidate> input=new ArrayList<>();
        for(int n=0;n<40;n++)input.add(candidate("item"+n,"王沪宁陪同金正恩访问丹东"+new String(Character.toChars(0x4e00+n)),NOW));
        List<EventVerificationPlan.Task> first=EventVerificationPlan.create(input,vectors(input),"v1");
        assertEquals(EventVerificationPlan.MAX_PAIRS,first.size());Set<String> ids=new HashSet<>();for(EventVerificationPlan.Task task:first){assertTrue(ids.add(task.evidenceKey));assertEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(task.left,task.right));}
        Collections.reverse(input);List<EventVerificationPlan.Task> second=EventVerificationPlan.create(input,vectors(input),"v1");
        for(int n=0;n<first.size();n++)assertEquals(first.get(n).evidenceKey,second.get(n).evidenceKey);
        List<HotCandidate> late=new ArrayList<>();for(int n=0;n<EventVerificationPlan.MAX_CANDIDATES;n++)late.add(candidate("empty"+n,"没有可用缓存的条目"+n,NOW));late.addAll(input);
        assertTrue(EventVerificationPlan.create(late,vectors(input),"v1").isEmpty());
    }
    @Test public void cancellationReturnsNoPartialPlanAndKeepsInterrupt(){
        HotCandidate a=candidate("a","王沪宁陪同朝鲜最高领导人金正恩访问丹东",NOW),b=candidate("b","王沪宁陪同金正恩访问丹东",NOW);List<HotCandidate> input=Arrays.asList(a,b);
        Thread.currentThread().interrupt();try{assertTrue(EventVerificationPlan.create(input,vectors(input),"v1").isEmpty());assertTrue(Thread.currentThread().isInterrupted());}finally{Thread.interrupted();}
    }
    @Test public void maximumWindowRetainsOnlyBoundedWork(){
        List<HotCandidate> input=new ArrayList<>();for(int n=0;n<EventVerificationPlan.MAX_CANDIDATES;n++)input.add(candidate("stress"+n,"王沪宁陪同金正恩访问丹东"+new String(Character.toChars(0x20000+n)),NOW));
        Map<String,float[]> cached=vectors(input);long start=System.nanoTime();
        List<EventVerificationPlan.Task> tasks=EventVerificationPlan.create(input,cached,"v1");
        assertEquals(EventVerificationPlan.MAX_PAIRS,tasks.size());
        System.out.println("EVENT_PLAN_DESKTOP_WORST_WINDOW candidates="+input.size()+" tasks="+tasks.size()+" elapsed_ms="+(System.nanoTime()-start)/1_000_000L+"; synthetic equal vectors, not ARM/battery acceptance");
    }
    @Test public void observedContainedHeadlineSurvivesDivergentSourceExcerptVectors()throws Exception{
        org.json.JSONObject fixture;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/observed-event-pairs-20261026.json")){fixture=new org.json.JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        org.json.JSONObject pair=fixture.getJSONArray("pairs").getJSONObject(2);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));
        assertEquals("又输欧美,BLG爆冷不敌MKOI",a.title);assertEquals("BLG爆冷不敌MKOI",b.title);
        List<HotCandidate> input=Arrays.asList(a,b);Map<String,float[]> cached=vectors(input);cached.put(SemanticOrder.key(b.modelText()),vector(0,1));
        List<EventVerificationPlan.Task> tasks=EventVerificationPlan.create(input,cached,"v1");assertEquals(1,tasks.size());assertEquals(0,tasks.get(0).similarity,0.00001);
        assertEquals("Still retrieval only",2,CandidateOrder.arrange(input,new org.json.JSONArray(),false).size());
        HotCandidate conflict=candidate("wrong","网传BLG爆冷不敌MKOI",a.observedAt);List<HotCandidate> different=Arrays.asList(a,conflict);
        assertTrue("Containment never bypasses claim guards",EventVerificationPlan.create(different,vectors(different),"v1").isEmpty());
    }
    @Test public void callerCancellationStopsSharedWorkerWithoutInterruptingItsDatabaseThread(){
        List<HotCandidate> input=new ArrayList<>();for(int n=0;n<40;n++)input.add(candidate("cancel"+n,"王沪宁陪同金正恩访问丹东"+new String(Character.toChars(0x20000+n)),NOW));
        java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
        assertTrue(EventVerificationPlan.create(input,vectors(input),"v1",()->calls.incrementAndGet()>70).isEmpty());
        assertFalse("Shared storage thread stays usable",Thread.currentThread().isInterrupted());
    }
    @Test public void observedShortAndInsertedDetailHeadlinesCanReachVerifierWithDivergentExcerpts()throws Exception{
        org.json.JSONObject fixture;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/observed-event-fresh-pairs-20261026.json")){fixture=new org.json.JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        for(int index:new int[]{1,2}){
            org.json.JSONObject pair=fixture.getJSONArray("pairs").getJSONObject(index);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));
            List<HotCandidate> input=Arrays.asList(a,b);Map<String,float[]> cached=vectors(input);cached.put(SemanticOrder.key(b.modelText()),vector(0,1));
            assertEquals("Explicitly synthetic orthogonal vectors isolate lexical retrieval",1,EventVerificationPlan.create(input,cached,"v1").size());
            assertEquals(2,CandidateOrder.arrange(input,new org.json.JSONArray(),false).size());
        }
        HotCandidate a=candidate("accept","某候选人承认败选",NOW),b=candidate("refuse","某候选人拒绝承认败选",NOW);List<HotCandidate> refusal=Arrays.asList(a,b);
        assertTrue("Overlap never bypasses refusal",EventVerificationPlan.create(refusal,vectors(refusal),"v1").isEmpty());
        b=candidate("stale",a.title+"并开展反对党政治活动",NOW+86_400_001L);List<HotCandidate> stale=Arrays.asList(a,b);
        assertTrue("Overlap never bypasses time evidence",EventVerificationPlan.create(stale,vectors(stale),"v1").isEmpty());
    }
}
