package app.quietreader.ai;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class MemorySampleReuseTest {
    private static Set<Integer> pids(Integer... values){return new HashSet<>(Arrays.asList(values));}
    @Test public void newExitedOrReplacedProcessInvalidatesWithinFiveSeconds(){
        assertFalse(ProcessMemoryBudget.canReuse(101,100,pids(1),pids(1,2),true));
        assertFalse(ProcessMemoryBudget.canReuse(101,100,pids(1,2),pids(1),true));
        assertFalse(ProcessMemoryBudget.canReuse(101,100,pids(1,2),pids(1,3),true));
        assertTrue(ProcessMemoryBudget.canReuse(101,100,pids(1,2),pids(2,1),true));
    }
    @Test public void missingProcessListAndExpiredOrBackwardClockCannotReuse(){
        assertFalse(ProcessMemoryBudget.canReuse(101,100,pids(1),pids(1),false));
        assertFalse(ProcessMemoryBudget.canReuse(101,-1,pids(1),pids(1),true));
        assertFalse(ProcessMemoryBudget.canReuse(99,100,pids(1),pids(1),true));
        assertFalse(ProcessMemoryBudget.canReuse(5100,100,pids(1),pids(1),true));
        assertFalse(ProcessMemoryBudget.canReuse(101,100,pids(),pids(),true));
        assertTrue(ProcessMemoryBudget.canReuse(5099,100,pids(1),pids(1),true));
    }
}
