package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.discovery.EventClaimEvidence.Decision.*;

/** Synthetic guard cases plus the existing developer tuning pair, not holdout. */
public class InstitutionStatementPipelineTest {
    private static final long NOW=1_793_183_700_000L;
    private static final String LONG="央视体育青少中心：不认可皇马的表态，在道歉没有到位之前一切免谈";
    private static final String SHORT="总台体育青少中心发言人：不认可皇马的表态";
    private static final String EXCERPT="在皇马的道歉没有到位之前，一切免谈。";
    private HotCandidate item(String id,String title,String excerpt){return new HotCandidate(Models.Source.CLS,"https://example.org/"+id,title,excerpt,"",false,1,NOW-1000,NOW);}
    private List<EventVerificationPlan.Task> plan(HotCandidate a,HotCandidate b){
        Map<String,float[]> vectors=new HashMap<>();float[] v=new float[512];v[0]=1;
        vectors.put(SemanticOrder.key(a.modelText()),v);vectors.put(SemanticOrder.key(b.modelText()),v);
        return EventVerificationPlan.create(List.of(a,b),vectors,"model-v1");
    }
    private List<FeedStore.Row> rows(HotCandidate a,HotCandidate b){return List.of(new FeedStore.Row(a.id,a,"a"),new FeedStore.Row(b.id,b,"b"));}
    @Test public void canonicalInputsKeepAllClausesAndSeparateOldRawScoreKeys(){
        HotCandidate a=item("a",LONG,""),b=item("b",SHORT,EXCERPT);
        EventVerificationPlan.Task task=plan(a,b).get(0);
        String ai=task.left.id.equals(a.id)?task.leftInput:task.rightInput;
        String bi=task.left.id.equals(b.id)?task.leftInput:task.rightInput;
        assertEquals("总台体育青少中心："+LONG.substring(LONG.indexOf('：')+1),ai);
        assertEquals("总台体育青少中心：不认可皇马的表态",bi);
        List<String> raw=new ArrayList<>(List.of(a.title,b.title));Collections.sort(raw);
        String old=HotCandidate.hash("event-title-bidirectional-v1\nmodel-v1\n"+HotCandidate.hash(raw.get(0))+"\n"+HotCandidate.hash(raw.get(1)));
        assertNotEquals(old,task.cacheKey);
        assertNotEquals(EventRerankCacheKey.of(task,false),EventRerankCacheKey.of(task,true));
        assertEquals(task.cacheKey,plan(b,a).get(0).cacheKey);
        assertEquals(LONG,a.title);assertEquals(SHORT,b.title);
    }
    @Test public void rawNonstatementScoreIdentityIsRetained(){
        HotCandidate a=item("a","王沪宁陪同金正恩访问丹东",""),b=item("b","王沪宁陪同朝鲜最高领导人金正恩访问丹东","");
        EventVerificationPlan.Task task=plan(a,b).get(0);List<String> raw=new ArrayList<>(List.of(a.title,b.title));Collections.sort(raw);
        assertEquals(HotCandidate.hash("event-title-bidirectional-v1\nmodel-v1\n"+HotCandidate.hash(raw.get(0))+"\n"+HotCandidate.hash(raw.get(1))),task.cacheKey);
        assertEquals(task.left.title,task.leftInput);assertEquals(task.right.title,task.rightInput);
    }
    @Test public void exactInstitutionAndSpokespersonAreNotAStorySpecificRule(){
        HotCandidate a=item("a","星河研究院：支持青禾公司的决定",""),b=item("b","星河研究院发言人：支持青禾公司的决定","");
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,b));assertEquals(1,plan(a,b).size());
        for(String title:new String[]{"星辰研究院：支持青禾公司的决定","星河研究院：反对青禾公司的决定","星河研究院：支持青禾集团的决定","星河研究院：支持青禾公司的声明","星河研究院：去年支持青禾公司的决定","星河研究院：支持青禾公司的决定，后续进展值得关注"})
            assertNotEquals(title,CANDIDATE,EventClaimEvidence.evaluate(a,item("c",title,"")));
    }
    @Test public void borrowedExcerptCannotSupplySpeakerStanceOrCurrentAssertion(){
        HotCandidate a=item("a",LONG,"");
        for(String title:new String[]{"总台财经节目中心发言人：不认可皇马的表态","总台体育青少中心发言人：认可皇马的表态","总台体育青少中心发言人：尚未就皇马表态作出回应","总台体育青少中心发言人：网传不认可皇马的表态不实","总台体育青少中心发言人：是否认可皇马的表态","总台体育青少中心发言人：去年不认可皇马的表态","网友假冒总台体育青少中心：不认可皇马的表态","总台体育青少中心回应：不认可皇马的表态系断章取义"}){
            HotCandidate b=item("b",title,EXCERPT);assertNotEquals(title,CANDIDATE,EventClaimEvidence.evaluate(a,b));assertTrue(title,plan(a,b).isEmpty());
        }
    }
    @Test public void conditionNeedsLiteralCrossSupportAndNoDenialOrTemporalWrapper(){
        HotCandidate a=item("a",LONG,"");
        for(String excerpt:new String[]{"","皇马的道歉还没到位。","在其他俱乐部的道歉没有到位之前，一切免谈。","去年："+EXCERPT,"网传"+EXCERPT+"不实","否认曾说过："+EXCERPT,"声明没有提及："+EXCERPT})
            assertNotEquals(excerpt,CANDIDATE,EventClaimEvidence.evaluate(a,item("b",SHORT,excerpt)));
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,item("b",SHORT,EXCERPT)));
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,item("b",SHORT,EXCERPT+"其他安排另行公布。")));
        HotCandidate explicit=item("c",SHORT+"，在皇马的道歉没有到位之前一切免谈","");
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,explicit));
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(explicit,a));
    }
    @Test public void embeddedOrContinuedConditionalTextIsNotACompleteSupportingAssertion()throws Exception{
        HotCandidate a=item("a",LONG,"");
        var constructor=EventVerificationPlan.Task.class.getDeclaredConstructor(HotCandidate.class,HotCandidate.class,double.class,String.class);
        constructor.setAccessible(true);
        for(String excerpt:new String[]{
            "在皇马的道歉没有到位之前，一切免谈是假消息。",
            "有人杜撰了这句话，在皇马的道歉没有到位之前，一切免谈。",
            "原标题写着：在皇马的道歉没有到位之前，一切免谈。",
            "在皇马的道歉没有到位之前，一切免谈，后来又接受解释。",
            "在皇马的道歉没有到位之前，一切免谈直到另行通知。",
            "关于这句「在皇马的道歉没有到位之前，一切免谈」的争议。"
        }){
            HotCandidate b=item("b",SHORT,excerpt);
            assertNotEquals(excerpt,CANDIDATE,EventClaimEvidence.evaluate(a,b));
            assertTrue(excerpt,plan(a,b).isEmpty());
            var task=constructor.newInstance(a,b,1.0,"model-v1");
            assertEquals(excerpt,2,EventGroupAssembly.apply(rows(a,b),new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(task,99,99,true)),"model-v1").size());
        }
    }
    @Test public void admissionStillNeedsCompleteBidirectionalModelEvidenceAndMetadataIdentity(){
        HotCandidate a=item("a",LONG,""),b=item("b",SHORT,EXCERPT);EventVerificationPlan.Task task=plan(a,b).get(0);
        List<FeedStore.Row> input=rows(a,b);
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(task,9,6.9f,true)),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(input,new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(task,9,9,false)),"model-v1").size());
        // Injected values exercise assembly; not Android or desktop inference.
        var score=new EventGroupAssembly.ScoredPair(task,9,9,true);
        assertEquals(1,EventGroupAssembly.apply(input,new JSONArray(),List.of(score),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(rows(a,item("b",SHORT,"")),new JSONArray(),List.of(score),"model-v1").size());
        assertEquals(2,EventGroupAssembly.apply(rows(a,item("b",SHORT,EXCERPT+"新增内容")),new JSONArray(),List.of(score),"model-v1").size());
    }
}
