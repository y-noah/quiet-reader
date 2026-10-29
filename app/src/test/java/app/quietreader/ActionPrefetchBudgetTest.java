package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActionPrefetchBudgetTest {
    @Test public void fullBatchHasOneFiniteLeaseAndOrdinaryGesturesCannotShortenIt(){
        ActionPrefetchBudget budget=new ActionPrefetchBudget();budget.batch(1000,8);
        assertEquals(80_000,budget.remaining(1000));budget.action(2000);
        assertEquals(79_000,budget.remaining(2000));assertFalse(budget.active(81_000));
        budget.batch(90_000,20);assertEquals(120_000,budget.remaining(90_000));
        budget.batch(91_000,1);assertEquals(20_000,budget.remaining(91_000));
        budget.clear();assertFalse(budget.active(91_001));
    }
    @Test public void passiveChecksCannotStartOrExtendWork(){
        ActionPrefetchBudget budget=new ActionPrefetchBudget();
        assertFalse(budget.active(100));budget.action(100);
        for(long now=100;now<20_100;now+=100)assertTrue(budget.active(now));
        assertFalse(budget.active(20_100));assertEquals(0,budget.remaining(30_000));
    }
    @Test public void burstsRenewRatherThanAccumulateCredit(){
        ActionPrefetchBudget budget=new ActionPrefetchBudget();
        for(long now=100;now<=1000;now+=100)budget.action(now);
        assertEquals(20_000,budget.remaining(1000));
        assertFalse(budget.active(21_000));
    }
    @Test public void backgroundClearsCreditAndNewActionCanResume(){
        ActionPrefetchBudget budget=new ActionPrefetchBudget();budget.action(100);
        budget.clear();assertFalse(budget.active(101));
        budget.action(500);assertTrue(budget.active(501));
    }
}
