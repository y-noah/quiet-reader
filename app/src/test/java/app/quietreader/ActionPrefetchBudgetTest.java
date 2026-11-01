package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActionPrefetchBudgetTest {
    @Test public void ordinaryGesturesCannotShortenTheCurrentReserveLease(){
        ActionPrefetchBudget budget=new ActionPrefetchBudget();budget.reserve(1000,75);
        budget.action(2000);assertEquals(269_000,budget.remaining(2000));
        budget.reserve(400_000,1);assertEquals(20_000,budget.remaining(400_000));
        budget.clear();assertFalse(budget.active(400_001));
    }
    @Test public void reserveLeaseIsFiniteAndClearsOnBackground(){
        ActionPrefetchBudget budget=new ActionPrefetchBudget();budget.reserve(100,75);
        assertEquals(270_000,budget.remaining(100));assertFalse(budget.active(270_100));
        budget.reserve(500_000,10000);assertEquals(270_000,budget.remaining(500_000));
        budget.clear();assertEquals(0,budget.remaining(500_001));
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
