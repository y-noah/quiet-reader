package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingViewportTest {
    @Test public void preservesParagraphFractionAfterNarrowToWideReflow(){
        ReadingViewport viewport=ReadingViewport.parse("{\"id\":\"reading-cGFyYWdyYXBo-9\",\"fraction\":0.35,\"gap\":0}");assertNotNull(viewport);
        // Before reflow paragraph starts at 4000 and is 1000 high (position 4350).
        // After reflow it starts at 2400 and is 500 high: preserve progress at 2575.
        assertEquals(2575,viewport.destination(0,2400,500,1,4350));
        assertEquals(5150,viewport.destination(400,2200,500,2,8700));
    }
    @Test public void leadingGapAndUnavailableGeometryHaveSafeFallbacks(){
        ReadingViewport viewport=ReadingViewport.parse("{\"id\":\"reading-YQ-0\",\"fraction\":0,\"gap\":30}");assertNotNull(viewport);
        assertEquals(470,viewport.destination(100,400,100,1,99));assertEquals(0,viewport.destination(0,5,10,1,99));
        assertEquals(99,viewport.destination(100,400,0,1,99));assertEquals(99,viewport.destination(100,Double.NaN,100,1,99));assertEquals(99,viewport.destination(100,400,100,Float.POSITIVE_INFINITY,99));
    }
    @Test public void malformedOrUnboundedAnchorsAreRejected(){
        for(String value:new String[]{"null","{}","{\"id\":\"other-page\",\"fraction\":0}","{\"id\":\"reading-YQ-0\",\"fraction\":-1}","{\"id\":\"reading-YQ-0\",\"fraction\":1.1}","{\"id\":\"reading-YQ-0\",\"fraction\":0,\"gap\":10001}","{\"id\":\"reading-"+"a".repeat(513)+"\",\"fraction\":0}"})assertNull(value,ReadingViewport.parse(value));
    }
}
