package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
public class ImageTypeTest {
    @Test public void identifiesRasterAndRejectsActiveDocuments() {
        assertEquals("image/webp",ImageType.detect("RIFF1234WEBPdata".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("image/gif",ImageType.detect("GIF89a123456789".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ImageType.detect("<html>blocked page</html>".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ImageType.detect("<svg onload='x'/>".getBytes(StandardCharsets.US_ASCII)));
        assertNull(ImageType.detect(new byte[0]));
    }
}
