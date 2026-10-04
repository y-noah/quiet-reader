package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class NavigationAndImagesTest {
    @Test public void scrollDirectionHasHysteresisAndTopAlwaysShows(){
        NavigationScroll bar=new NavigationScroll();
        assertFalse(bar.update(10,10));assertTrue(bar.update(20,10));
        assertTrue(bar.update(18,-2));assertTrue(bar.update(19,1));
        assertFalse(bar.update(10,-9));assertFalse(bar.update(11,1));
        assertTrue(bar.update(30,19));assertFalse(bar.update(0,-30));
        assertTrue(bar.update(100,100));bar.reset();assertFalse(bar.update(100,0));
    }
}
