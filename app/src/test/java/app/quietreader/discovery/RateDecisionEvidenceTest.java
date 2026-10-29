package app.quietreader.discovery;

import app.quietreader.Models;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.discovery.EventClaimEvidence.Decision.*;

public class RateDecisionEvidenceTest {
    private final long now=1_793_240_000_000L;
    private HotCandidate item(Models.Source source,String title){return new HotCandidate(source,"https://example.org/"+source+"/"+HotCandidate.hash(title),title,"","",false,1,null,now);}
    private EventClaimEvidence.Decision decision(String a,String b){return EventClaimEvidence.evaluate(item(Models.Source.CLS,a),item(Models.Source.WEIBO,b));}
    @Test public void explicitDecisionCanHaveOneSidedDissentDetails(){
        for(String bank:List.of("美联储","欧洲央行","英国央行")){
            String a=bank+"如期维持基准利率不变 两名票委主张加息25个基点",b=bank+"宣布维持利率不变";
            assertEquals(CANDIDATE,decision(a,b));assertEquals(CANDIDATE,decision(b,a));
        }
    }
    @Test public void sharedNumericAndDirectionalDetailsCannotBeErased(){
        String a="美联储维持利率不变，两名票委主张加息25个基点";
        for(String tail:List.of("两名票委主张降息25个基点","两名票委主张加息50个基点","三名票委主张加息25个基点"))assertEquals(CONFLICT,decision(a,"美联储维持利率不变，"+tail));
        assertEquals(CONFLICT,decision("美联储维持利率不变，目标区间为3.75%至4%","美联储维持利率不变，目标区间为4%至4.25%"));
        assertEquals(CONFLICT,decision("美联储维持基准利率不变","美联储维持政策利率不变"));
    }
    @Test public void unknownReportsPlansAndOtherBanksNeverBorrowTheMainDecision(){
        String a="美联储宣布维持利率不变";
        for(String b:List.of("美联储预计维持利率不变","美联储是否维持利率不变","美联储去年维持利率不变","美联储下次会议将维持利率不变","美联储维持利率不变，明年将加息两次","美联储维持利率不变，市场人士预计未来降息","美联储维持利率不变，目标区间为4%至3.75%","美联储维持利率不变，零名票委主张加息25个基点"))assertNotEquals(b,CANDIDATE,decision(a,b));
        assertEquals(CONFLICT,decision(a,"欧洲央行宣布维持利率不变"));
        assertNotEquals(CANDIDATE,decision(a,"美联储宣布加息25个基点"));
    }
    @Test public void completeParsedClaimsHaveVersionedMainDecisionModelInput(){
        HotCandidate a=item(Models.Source.CLS,"美联储如期维持基准利率不变 两名票委主张加息25个基点"),b=item(Models.Source.WEIBO,"美联储宣布维持利率不变");
        Map<String,float[]> vectors=new HashMap<>();float[] vector=new float[512];vector[0]=1;vectors.put(SemanticOrder.key(a.modelText()),vector);vectors.put(SemanticOrder.key(b.modelText()),vector);
        List<EventVerificationPlan.Task> tasks=EventVerificationPlan.create(List.of(a,b),vectors,"test-model");assertEquals(1,tasks.size());
        EventVerificationPlan.Task task=tasks.get(0);assertEquals("美联储维持利率不变",task.leftInput);assertEquals(task.leftInput,task.rightInput);
        assertTrue("Original display facts remain",a.title.contains("25个基点"));
        assertEquals("Admission and input are not a merge",2,CandidateOrder.arrange(List.of(a,b),new org.json.JSONArray(),false).size());
    }
    @Test public void completeNumericAttributesAcceptFormattingButRejectAmbiguousClauses(){
        assertEquals(CANDIDATE,decision("美联储维持利率不变，目标区间为3.750%至4.00%，两名票委主张加息25.0个基点","美联储宣布维持利率不变，2名票委主张加息25个基点，目标区间为3.75%至4%"));
        assertEquals(CANDIDATE,decision("美联储维持利率不变，目标区间为3.75%至4%","美联储宣布维持利率不变"));
        for(String title:List.of("美联储维持利率不变，","美联储维持利率不变，目标区间为4%至4%","美联储维持利率不变，两名票委主张加息0个基点","美联储维持利率不变，两名票委主张加息25个基点，两名票委主张加息25个基点","美联储维持利率不变，两名票委可能主张加息25个基点"))assertNull(title,RateDecisionEvidence.parse(title));
    }
    @Test public void typedDecisionCanRetrieveAcrossDivergingExcerptsButNeverMissingVectors(){
        HotCandidate a=item(Models.Source.CLS,"美联储如期维持基准利率不变 两名票委主张加息25个基点"),b=item(Models.Source.WEIBO,"美联储宣布维持利率不变");
        Map<String,float[]> vectors=new HashMap<>();float[] x=new float[512],y=new float[512];x[0]=1;y[1]=1;vectors.put(SemanticOrder.key(a.modelText()),x);vectors.put(SemanticOrder.key(b.modelText()),y);
        assertEquals(1,EventVerificationPlan.create(List.of(a,b),vectors,"test-model").size());
        vectors.remove(SemanticOrder.key(b.modelText()));assertTrue(EventVerificationPlan.create(List.of(a,b),vectors,"test-model").isEmpty());
    }
}
