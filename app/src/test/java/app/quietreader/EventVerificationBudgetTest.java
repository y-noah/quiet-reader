package app.quietreader;

import app.quietreader.discovery.EventVerificationBudget;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventVerificationBudgetTest {
    @Test public void interestsRetainPersonalRerankerReserveWhileColdProfileUsesOtherwiseIdleTime(){
        long start=100_000,overall=start+15_000;
        assertEquals(start+3000,EventVerificationBudget.deadline(start,overall,true));
        assertEquals(start+8000,EventVerificationBudget.deadline(start,overall,false));
        assertEquals(overall-7000,EventVerificationBudget.deadline(start+6000,overall,true));
        assertEquals(overall-1000,EventVerificationBudget.deadline(start+9000,overall,false));
    }
    @Test public void lateOrExpiredWindowCannotGainTimeAndElapsedTimeCannotOverflow(){
        for(boolean interests:new boolean[]{false,true})for(long now:new long[]{0,1000,8000,14000,15000,18000,Long.MAX_VALUE-1}){
            long deadline=EventVerificationBudget.deadline(now,15_000,interests);
            assertTrue("Never extends the original total window",deadline<=15_000);
            if(now>=15_000)assertTrue("Expired work is not revived",deadline<now);
        }
        assertEquals(Long.MAX_VALUE-1000,EventVerificationBudget.deadline(Long.MAX_VALUE-10,Long.MAX_VALUE,false));
        assertTrue(EventVerificationBudget.deadline(1,0,false)<1);
    }
}
