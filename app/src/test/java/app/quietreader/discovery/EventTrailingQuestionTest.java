package app.quietreader.discovery;

import app.quietreader.Models;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventTrailingQuestionTest {
    private static final String TAIL="，有哪些亮点值得关注？你看好其市场前景吗？";
    private static final String ASSERTION="星河发布首款触控屏 NoteBook Pro";
    private HotCandidate item(String id,String title){return new HotCandidate(Models.Source.ZHIHU,"https://example.org/"+id,title,"","",false,1,null,1793183700000L);}
    @Test public void removeOnlyCompleteGenericQuestionsAtTheEnd(){
        assertEquals(ASSERTION,EventQuestionFrame.title(ASSERTION+TAIL));
        assertEquals(ASSERTION,EventQuestionFrame.title("如何看待 "+ASSERTION+TAIL));
        for(String value:List.of(ASSERTION+"，有哪些亮点值得关注",ASSERTION+"，你看好其市场前景吗？但尚未开售",ASSERTION+"，市场前景是否因缺陷受影响？","有哪些亮点值得关注？",ASSERTION+"，有人问有哪些亮点值得关注？"))assertEquals(value,EventQuestionFrame.title(value));
        assertEquals(ASSERTION+"，起售价12399元",EventQuestionFrame.title(ASSERTION+"，起售价12399元"+TAIL));
    }
    @Test public void qualifiedProductClaimsCannotBorrowACompletedAssertion(){
        HotCandidate current=item("current",ASSERTION);
        for(String suffix:List.of("",TAIL))for(String qualifier:List.of("将","据称","未","拟","是否","去年","曾")){
            String other=qualifier.equals("据称")?qualifier+ASSERTION:ASSERTION.replace("发布",qualifier+"发布");
            assertNotEquals(other+suffix,EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(current,item("other",other+suffix)));
        }
    }
    @Test public void unknownProductClausesDoNotFallThroughToGenericAdmission(){
        for(String clause:List.of("，尚在测试中","，发布时间仍待确认","，只是概念展示"))
            assertNotEquals(clause,EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(item("a",ASSERTION),item("b",ASSERTION+clause)));
        assertEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(item("a",ASSERTION),item("b",ASSERTION+TAIL)));
    }
    @Test public void modelOnlyInputHasItsOwnCacheIdentityAndKeepsSourceTitle(){
        HotCandidate a=item("a",ASSERTION),b=item("b",ASSERTION+TAIL);float[] v=new float[512];v[0]=1;
        EventVerificationPlan.Task task=EventVerificationPlan.create(List.of(a,b),Map.of(SemanticOrder.key(a.modelText()),v,SemanticOrder.key(b.modelText()),v),"test-model").get(0);
        assertEquals(ASSERTION,task.leftInput);assertEquals(ASSERTION,task.rightInput);assertEquals(ASSERTION+TAIL,b.title);
        List<String> raw=new ArrayList<>(List.of(a.title,b.title));Collections.sort(raw);
        assertNotEquals(HotCandidate.hash("event-title-bidirectional-v1\ntest-model\n"+HotCandidate.hash(raw.get(0))+"\n"+HotCandidate.hash(raw.get(1))),task.cacheKey);
    }
}
