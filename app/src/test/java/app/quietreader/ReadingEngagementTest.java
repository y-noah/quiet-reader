package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingEngagementTest {
    @Test public void quickExitRequiresDisplayedSubstantialBodyAndExplicitUninterruptedReturn(){
        ReadingEngagement value=new ReadingEngagement();value.reset(0);value.visible(true,0);
        assertFalse(value.takeQuickExit(4000,true));value.ready(true,5000);
        assertFalse(value.takeQuickExit(5500,true));assertFalse(value.takeQuickExit(6500,false));
        assertTrue(value.takeQuickExit(7000,true));assertFalse(value.takeQuickExit(7100,true));assertFalse(value.take(30_000));
        value.reset(40_000);value.ready(true,40_000);value.visible(false,41_000);value.visible(true,42_000);
        assertFalse(value.takeQuickExit(43_000,true));
        value.reset(50_000);value.ready(true,50_000);assertFalse(value.takeQuickExit(56_000,true));
    }
    @Test public void loadingBackgroundAndUnattendedTimeDoNotQualify(){
        ReadingEngagement value=new ReadingEngagement();value.reset(0);value.visible(true,0);value.touch(0);value.scroll(1);assertFalse(value.take(60_000));
        value.ready(true,60_000);value.touch(60_001);value.scroll(60_010);value.visible(false,61_000);assertFalse(value.take(120_000));
        value.visible(true,120_000);assertFalse(value.take(180_000));
    }
    @Test public void qualifiedForegroundReadIsEmittedOnlyOnce(){
        ReadingEngagement value=new ReadingEngagement();value.reset(0);value.visible(true,0);value.ready(true,0);value.touch(1);value.scroll(5);
        assertFalse(value.take(19_999));assertTrue(value.take(20_002));assertFalse(value.take(29_000));
    }
    @Test public void automaticScrollOrClickOnlyCannotQualify(){
        ReadingEngagement value=new ReadingEngagement();value.reset(0);value.visible(true,0);value.ready(true,0);value.scroll(1);value.touch(2);assertFalse(value.take(25_000));
        value.reset(30_000);value.ready(true,30_000);value.touch(30_001);value.scroll(32_000);assertFalse(value.take(55_000));
    }
    @Test public void switchingContentResetsEvidence(){
        ReadingEngagement value=new ReadingEngagement();value.reset(0);value.visible(true,0);value.ready(true,0);value.touch(1);value.scroll(5);value.reset(25_000);value.ready(true,25_000);assertFalse(value.take(60_000));
    }
}
