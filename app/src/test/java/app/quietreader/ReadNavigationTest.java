package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadNavigationTest {
    private static final String OLD="https://s.weibo.com/weibo?q=test",NEXT="https://m.weibo.cn/search?containerid=test";
    @Test public void upgradeRejectsOldFinishWithNullOrOldCommittedAddress(){
        ReadNavigation gate=new ReadNavigation();gate.begin(NEXT);
        assertFalse(gate.ready(OLD,null));assertFalse(gate.ready(OLD,OLD));assertTrue(gate.pending());
        assertTrue(gate.ready(NEXT,NEXT));assertFalse(gate.pending());
    }
    @Test public void lateOldFinishCannotClaimTheNewCommittedDocument(){
        ReadNavigation gate=new ReadNavigation();gate.begin(NEXT);assertTrue(gate.ready(NEXT,NEXT));
        assertFalse(gate.ready(OLD,NEXT));
    }
    @Test public void acceptedRedirectUpdatesExpectedDestination(){
        ReadNavigation gate=new ReadNavigation();gate.begin(NEXT);
        String visitor="https://visitor.passport.weibo.cn/visitor/visitor";gate.begin(visitor);
        assertFalse(gate.ready(NEXT,NEXT));assertTrue(gate.ready(visitor,visitor));
        gate.begin(NEXT);assertTrue(gate.ready(NEXT,NEXT));
    }
    @Test public void snapshotFromEarlierNavigationNeverBecomesCurrentAgain(){
        ReadNavigation gate=new ReadNavigation();long old=gate.revision();assertTrue(gate.current(old));
        gate.begin(NEXT);assertFalse(gate.current(old));assertFalse(gate.current(gate.revision()));
        gate.ready(NEXT,NEXT);assertFalse(gate.current(old));assertTrue(gate.current(gate.revision()));
    }
    @Test public void initialNormalPageRequiresMatchingNonemptyCurrentAddress(){
        ReadNavigation gate=new ReadNavigation();assertFalse(gate.ready(null,null));assertFalse(gate.ready("",""));
        assertFalse(gate.ready(OLD,NEXT));assertTrue(gate.ready(OLD,OLD));
    }
}
