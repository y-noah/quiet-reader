package app.quietreader;

import app.quietreader.discovery.RefreshBackoff;
import org.junit.Test;
import static org.junit.Assert.*;

public class RefreshBackoffTest {
    @Test public void respectsSecondsAndServerDates(){
        assertEquals(121_000,RefreshBackoff.retryAfter("120",1000));
        assertEquals(1445412480000L,RefreshBackoff.retryAfter("Wed, 21 Oct 2015 07:28:00 GMT",1000));
        assertEquals(1000,RefreshBackoff.retryAfter("0",1000));
    }
    @Test public void invalidHeaderDoesNotBreakFetching(){
        for(String value:new String[]{null,"","later","-1","1.5","999999999999999999999999999"})assertEquals(0,RefreshBackoff.retryAfter(value,1000));
        assertEquals(Long.MAX_VALUE,RefreshBackoff.retryAfter("9223372036854775807",1000));
    }
    @Test public void boundedLocalBackoffNeverShortensServerWait(){
        assertEquals(61_000,RefreshBackoff.until(1,429,0,1000));
        assertEquals(1_801_000,RefreshBackoff.until(100,429,0,1000));
        assertEquals(99_000_000,RefreshBackoff.until(1,429,99_000_000,1000));
        assertEquals(16_000,RefreshBackoff.until(1,503,0,1000));
    }
}
