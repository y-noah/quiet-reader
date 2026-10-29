package app.quietreader;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class PrefetchWindowTest {
    @Test public void visibleCardsStayFirstAndNormalLookaheadHasFortyUniqueTargets(){
        PrefetchWindow window=new PrefetchWindow();List<Integer> rows=window.positions(100,40,4,100);
        assertEquals(Arrays.asList(40,41,42,43),rows.subList(0,4));assertEquals(Integer.valueOf(71),rows.get(31));assertEquals(Integer.valueOf(39),rows.get(32));assertEquals(Integer.valueOf(32),rows.get(39));assertEquals(40,new HashSet<>(rows).size());
    }
    @Test public void realReversalNeedsEvidenceAndLayoutChangesCannotReverseIt(){
        PrefetchWindow window=new PrefetchWindow();window.viewport(40,0,100);window.gesture(false,100);
        window.viewport(40,3,110);assertEquals(1,window.direction(110));window.viewport(40,10,120);assertEquals(1,window.direction(120));window.viewport(39,-50,130);assertEquals(-1,window.direction(130));
        window.idle();window.viewport(70,0,140);assertEquals(-1,window.direction(140));List<Integer> rows=window.positions(100,70,4,140);assertEquals(Integer.valueOf(69),rows.get(4));assertTrue(rows.contains(38));assertFalse(rows.contains(78));
    }
    @Test public void flingShiftsSlotsWithoutIncreasingParallelWorkOrWindowSize(){
        PrefetchWindow window=new PrefetchWindow();window.viewport(40,0,100);window.gesture(true,100);window.viewport(44,0,110);window.idle();
        List<Integer> rows=window.positions(120,44,3,200);assertEquals(40,rows.size());assertEquals(Integer.valueOf(79),rows.get(35));assertEquals(Integer.valueOf(43),rows.get(36));assertEquals(Integer.valueOf(40),rows.get(39));
        assertFalse(window.rapid(3100));assertEquals(Integer.valueOf(75),window.positions(120,44,3,3100).get(31));
    }
    @Test public void rapidUpwardReadingPlanPrioritizesOlderNeighborsAndNeverIncludesAnchor(){
        List<Integer> rows=PrefetchWindow.neighbors(120,60,-1,true);assertEquals(40,rows.size());assertEquals(Integer.valueOf(59),rows.get(0));assertEquals(Integer.valueOf(24),rows.get(35));assertEquals(Arrays.asList(61,62,63,64),rows.subList(36,40));assertFalse(rows.contains(60));
        assertEquals(40,new HashSet<>(rows).size());
    }
    @Test public void listEdgesReuseUnusedSlotsWithoutDuplicatesOrOutOfRange(){
        assertEquals(40,new PrefetchWindow().positions(100,0,3,0).size());
        for(int size=0;size<85;size++)for(int first:new int[]{-1,0,4,50,100})for(int visible:new int[]{0,1,4,60}){
            List<Integer> rows=new PrefetchWindow().positions(size,first,visible,0);assertEquals(Math.min(size,40),rows.size());assertEquals(rows.size(),new HashSet<>(rows).size());for(int n:rows)assertTrue(n>=0&&n<size);
            for(int direction:new int[]{-1,1})for(boolean rapid:new boolean[]{false,true}){List<Integer> neighbors=PrefetchWindow.neighbors(size,first,direction,rapid);assertEquals(Math.max(0,Math.min(size-1,40)),neighbors.size());assertEquals(neighbors.size(),new HashSet<>(neighbors).size());for(int n:neighbors)assertTrue(n>=0&&n<size);}
        }
    }
    @Test public void staleMotionAndBackgroundDoNotInventNewFlingEvidence(){
        PrefetchWindow window=new PrefetchWindow();window.viewport(50,0,100);window.gesture(true,100);window.viewport(45,0,110);assertEquals(-1,window.direction(110));window.stop();assertFalse(window.rapid(111));window.viewport(80,0,200);assertEquals(-1,window.direction(200));assertEquals(1,window.direction(30_110));window.reset();assertEquals(1,window.direction(210));
    }
}
