package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic predicate-scope controls; not observed headlines or holdout. */
public final class ApologyQualificationTest {
    private static final long NOW=1793183700000L;
    private HotCandidate item(String id,String title){return new HotCandidate(Models.Source.WEIBO,"https://example.org/"+id,title,"","",false,1,null,NOW);}
    private List<EventVerificationPlan.Task> plan(HotCandidate a,HotCandidate b){
        float[] vector=new float[512];vector[0]=1;Map<String,float[]> vectors=new HashMap<>();
        vectors.put(SemanticOrder.key(a.modelText()),vector);vectors.put(SemanticOrder.key(b.modelText()),vector);
        return EventVerificationPlan.create(List.of(a,b),vectors,"test-model");
    }
    @Test public void separatedPredicateQualificationCannotBecomeCurrentCompletedApology()throws Exception{
        HotCandidate current=item("a","星河俱乐部就球员言论道歉");
        var constructor=EventVerificationPlan.Task.class.getDeclaredConstructor(HotCandidate.class,HotCandidate.class,double.class,String.class);constructor.setAccessible(true);
        for(String qualifier:List.of("是否","能否","计划","拟","预计","准备","即将","将要","未","没有","不会","去年","前年")){
            HotCandidate other=item("b","如何看待星河俱乐部"+qualifier+"就球员言论道歉？");
            assertNotEquals(qualifier,EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(current,other));
            assertTrue(qualifier,plan(current,other).isEmpty());
            EventVerificationPlan.Task stale=constructor.newInstance(current,other,1d,"test-model");
            var rows=List.of(new FeedStore.Row(current.id,current,"a"),new FeedStore.Row(other.id,other,"b"));
            assertEquals(qualifier,2,EventGroupAssembly.apply(rows,new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(stale,99f,99f,true)),"test-model").size());
        }
    }
    @Test public void completedReasonAndUnrelatedLaterQuestionStayAdmissible(){
        HotCandidate a=item("a","星河俱乐部就球员言论道歉");
        for(String title:List.of("如何看待星河俱乐部就球员言论道歉？","星河俱乐部就球员言论道歉，球迷是否接受？"))
            assertTrue(title,Set.of(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.Decision.ALREADY_GROUPED).contains(EventClaimEvidence.evaluate(a,item("b",title))));
    }
    @Test public void differentRelativeYearsAndPlannedReasonsStayDistinct(){
        HotCandidate a=item("a","星河俱乐部去年就球员言论道歉"),b=item("b","星河俱乐部前年就球员言论道歉");
        assertNotEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(a,b));
        for(String connector:List.of("就","为","因","针对")){
            a=item("a","星河俱乐部"+connector+"球员言论道歉");b=item("b","星河俱乐部计划"+connector+"球员言论道歉");
            assertNotEquals(connector,EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(a,b));
        }
    }
    @Test public void unspecifiedFormerApologyCannotBorrowCurrentEventIdentity()throws Exception{
        HotCandidate current=item("a","星河俱乐部就球员言论道歉");
        var constructor=EventVerificationPlan.Task.class.getDeclaredConstructor(HotCandidate.class,HotCandidate.class,double.class,String.class);constructor.setAccessible(true);
        for(String former:List.of("曾","曾经","曾经已")){
            HotCandidate past=item("b","如何看待星河俱乐部"+former+"就球员言论道歉？");
            assertNotEquals(former,EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(current,past));
            assertTrue(former,plan(current,past).isEmpty());
            var stale=constructor.newInstance(current,past,1d,"test-model");
            var rows=List.of(new FeedStore.Row(current.id,current,"a"),new FeedStore.Row(past.id,past,"b"));
            assertEquals(former,2,EventGroupAssembly.apply(rows,new JSONArray(),List.of(new EventGroupAssembly.ScoredPair(stale,99f,99f,true)),"test-model").size());
        }
        HotCandidate already=item("b","星河俱乐部已经就球员言论道歉");
        assertEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(current,already));
        assertEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(item("c","星河俱乐部曾就球员言论道歉"),item("d","星河俱乐部曾经就球员言论道歉")));
    }
}
