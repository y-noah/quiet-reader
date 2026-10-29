package app.quietreader;

import app.quietreader.ai.ProcessMemoryBudget;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProcessMemoryBudgetTest {
    @Test public void pressureUsesKiBAndIncludesTheBoundary(){
        assertFalse(ProcessMemoryBudget.exceeds(0));
        assertFalse(ProcessMemoryBudget.exceeds(850L*1024-1));
        assertTrue(ProcessMemoryBudget.exceeds(850L*1024));
        assertTrue(ProcessMemoryBudget.exceeds(1024L*1024));
    }
    @Test public void missingOrPartialMemoryCannotAuthorizeMoreWork(){
        assertTrue(ProcessMemoryBudget.shouldPause(0,0,1,false,false));
        assertTrue(ProcessMemoryBudget.shouldPause(40_000,1,2,true,true));
        assertTrue(ProcessMemoryBudget.shouldPause(40_000,1,1,false,true));
        assertTrue(ProcessMemoryBudget.shouldPause(40_000,1,1,true,false));
        assertTrue(ProcessMemoryBudget.shouldPause(0,1,1,true,true));
    }
    @Test public void completeKnownProcessesStillRespectSoftLimit(){
        assertFalse(ProcessMemoryBudget.shouldPause(400_000,2,2,true,true));
        assertTrue(ProcessMemoryBudget.shouldPause(850L*1024,2,2,true,true));
    }
}
