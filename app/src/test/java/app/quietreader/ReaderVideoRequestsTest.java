package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReaderVideoRequestsTest {
    @Test public void onlySingleBoundedSyntaxByteRangesAreForwarded(){
        for(String valid:new String[]{null,"bytes=0-","bytes=0-1023","bytes=100-200","bytes=-128"})assertTrue(valid,ReaderVideoRequests.validRange(valid));
        for(String invalid:new String[]{"","bytes=-","bytes=0-1,2-3","bytes=0-1\r\nX-Header: bad","items=0-1","bytes=+1-2","bytes=9999999999999999999-"})assertFalse(invalid,ReaderVideoRequests.validRange(invalid));
    }
    @Test public void untrustedMediaCannotCreatePlaybackOwner(){
        try{new ReaderVideoRequests("https://sspai.com/post/123",new Models.VideoAttachment("https://untrusted.example/a.mp4",""));fail();}catch(IllegalArgumentException expected){}
    }
}
