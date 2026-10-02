package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReadingPositionTest {
    @Test public void negativeAnchorOffsetCannotProduceNegativeNativeScroll(){assertEquals(0,ReadingPosition.clamp(-780,600,2.625f,1800));}
    @Test public void shortenedPageClampsOldPositionToItsNewEnd(){assertEquals(100,ReadingPosition.clamp(1500,800,2f,1500));}
    @Test public void pageShorterThanViewportStaysAtTop(){assertEquals(0,ReadingPosition.clamp(500,400,2.625f,1800));}
    @Test public void validReadingPositionIsUnchanged(){assertEquals(615,ReadingPosition.clamp(615,2000,2.625f,1800));}
}
