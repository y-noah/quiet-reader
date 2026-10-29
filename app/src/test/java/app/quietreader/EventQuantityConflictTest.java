package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.json.JSONArray;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.discovery.EventClaimEvidence.Decision.*;

public class EventQuantityConflictTest {
    private static final long NOW=1_798_100_000_000L;
    private HotCandidate candidate(Models.Source source,String title){return new HotCandidate(source,"https://example.org/"+source+HotCandidate.hash(title),title,"","",false,1,null,NOW);}
    private void conflict(String a,String b){HotCandidate left=candidate(Models.Source.ZHIHU,a),right=candidate(Models.Source.WEIBO,b);assertEquals(CONFLICT,EventClaimEvidence.evaluate(left,right));assertEquals(CONFLICT,EventClaimEvidence.evaluate(right,left));Map<String,float[]> vectors=new HashMap<>();float[] unit=new float[512];unit[0]=1;for(HotCandidate c:List.of(left,right))vectors.put(SemanticOrder.key(c.modelText()),unit);assertTrue("Even perfect cosine cannot queue conflicting numeric roles",EventVerificationPlan.create(List.of(left,right),vectors,"model-v1").isEmpty());}
    @Test public void sameNumberSequenceCannotExchangeDeathAndInjuryRoles(){
        conflict("海城列车相撞，10人死亡、20人受伤","海城列车相撞，10人受伤、20人死亡");
        conflict("海城列车相撞，死亡10人、受伤20人","海城列车相撞，受伤10人、死亡20人");
    }
    @Test public void terseCasualtyCountsKeepTheirOutcome(){
        conflict("海城列车相撞，10死20伤","海城列车相撞，10伤20死");
        conflict("海城列车相撞，10人遇难、20人获救","海城列车相撞，10人获救、20人遇难");
    }
    @Test public void explicitCountScaleCannotDisappear(){
        conflict("海城列车相撞，10人死亡、20人受伤","海城列车相撞，10万人死亡、20人受伤");
        conflict("海城列车相撞，死亡10万人、受伤20人","海城列车相撞，死亡10人、受伤20人");
    }
    @Test public void consistentRolesStillOnlyAdmitVerification(){
        HotCandidate a=candidate(Models.Source.ZHIHU,"海城列车相撞，10人死亡、20人受伤"),b=candidate(Models.Source.WEIBO,"海城列车相撞，10名遇难、20名受伤");
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,b));assertEquals(2,CandidateOrder.arrange(List.of(a,b),new JSONArray(),false).size());
    }
    @Test public void adjoiningOutcomeClausesNeverBorrowEachOthersCount(){
        EventClaimEvidence.Decision[] expected={CANDIDATE,ALREADY_GROUPED};int index=0;for(String[] titles:new String[][]{{"海城列车相撞，10人死亡20人受伤","海城列车相撞，死亡10人受伤20人"},{"海城列车相撞，死亡10人、受伤20人","海城列车相撞，死亡10人受伤20人"}}){
            HotCandidate left=candidate(Models.Source.ZHIHU,titles[0]),right=candidate(Models.Source.WEIBO,titles[1]);assertEquals(expected[index++],EventClaimEvidence.evaluate(left,right));
        }
        conflict("海城列车相撞，10人死亡20人受伤","海城列车相撞，10人受伤20人死亡");
    }
    @Test public void mildAndSevereInjuryCountsAreDifferentRoles(){
        conflict("海城列车相撞，10人轻伤20人重伤","海城列车相撞，10人重伤20人轻伤");
    }
    @Test public void unknownOrAdditionalNumbersStillUseExistingStrictGate(){
        conflict("海城列车相撞，10人死亡","海城列车相撞，10人死亡、20人受伤");
        conflict("海城列车相撞，时速100公里","海城列车相撞，时速120公里");
    }
}
