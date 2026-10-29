package app.quietreader.discovery;

import app.quietreader.Models;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventQuestionFrameTest {
    private HotCandidate item(String id,String title){return new HotCandidate(Models.Source.ZHIHU,"https://example.org/"+id,title,"","",false,1,null,1793183700000L);}
    private EventVerificationPlan.Task task(HotCandidate a,HotCandidate b){
        float[] v=new float[512];v[0]=1;
        return EventVerificationPlan.create(List.of(a,b),Map.of(SemanticOrder.key(a.modelText()),v,SemanticOrder.key(b.modelText()),v),"test-model").get(0);
    }
    @Test public void onlyLeadingFramingAndOneFinalQuestionMarkAreRemoved(){
        for(String prefix:List.of("如何看待","怎么看待","如何评价","如何理解")){
            String assertion="星河俱乐部是否就球员言论道歉，前年曾经回应，金额 12 万，球迷怎么看待？";
            assertEquals(assertion,EventQuestionFrame.title(prefix+" "+assertion+"？"));
            assertEquals("星河俱乐部道歉，球迷是否接受",EventQuestionFrame.title(prefix+"星河俱乐部道歉，球迷是否接受？"));
        }
        for(String unchanged:List.of("星河俱乐部道歉，如何看待？","是否要求星河俱乐部道歉？","如何看待？","如何看待","“如何看待星河俱乐部道歉？”"))assertEquals(unchanged,EventQuestionFrame.title(unchanged));
    }
    @Test public void transformedScoresCannotReuseOldRawOrUnwrappedPairKeys(){
        HotCandidate a=item("a","星河俱乐部就球员言论道歉"),b=item("b","如何看待星河俱乐部就球员言论道歉，球迷是否接受？");
        EventVerificationPlan.Task task=task(a,b);
        assertEquals("如何看待星河俱乐部就球员言论道歉，球迷是否接受？",b.title);
        String actual=task.left.id.equals(b.id)?task.leftInput:task.rightInput;
        assertEquals("星河俱乐部就球员言论道歉，球迷是否接受",actual);
        List<String> raw=new ArrayList<>(List.of(a.title,b.title));Collections.sort(raw);
        String old=HotCandidate.hash("event-title-bidirectional-v1\ntest-model\n"+HotCandidate.hash(raw.get(0))+"\n"+HotCandidate.hash(raw.get(1)));
        assertNotEquals(old,task.cacheKey);assertEquals(task.cacheKey,task(b,a).cacheKey);
        HotCandidate unwrapped=item("b",actual);
        assertNotEquals(task.cacheKey,task(a,unwrapped).cacheKey);
    }
    @Test public void originalQualifiersStillDetermineAdmission(){
        HotCandidate current=item("a","星河俱乐部就球员言论道歉");
        for(String qualifier:List.of("是否","未","计划","去年","前年","曾经")){
            HotCandidate other=item("b","如何看待星河俱乐部"+qualifier+"就球员言论道歉？");
            assertTrue(EventQuestionFrame.title(other.title).contains(qualifier));
            assertNotEquals(EventClaimEvidence.Decision.CANDIDATE,EventClaimEvidence.evaluate(current,other));
        }
    }
}
