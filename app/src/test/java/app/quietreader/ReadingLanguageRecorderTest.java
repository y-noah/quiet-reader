package app.quietreader;

import app.quietreader.discovery.ContentLanguage;
import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class ReadingLanguageRecorderTest {
    @Test public void queuedEvidenceKeepsAcquisitionIdentityAndProseWhenReaderChanges(){
        Document body=new Document();body.url="https://www.v2ex.com/t/123";body.metadataHash="metadata";
        body.cachedAt=123;body.cachedSession="session-a";body.cachedNamespace="namespace-a";
        Section primary=new Section("main","正文",false);
        Block prose=new Block("text","这是关于手机续航与散热的中文使用体验。".repeat(30));
        primary.blocks.add(prose);body.sections.add(primary);body.blocks.add(prose);
        Document queued=ReadingLanguageRecorder.snapshot(body);
        body.blocks.clear();primary.blocks.clear();body.sections.clear();body.cachedSession="session-b";body.cachedNamespace="namespace-b";body.cachedAt=999;body.metadataHash="new";body.videoTopic=true;
        assertEquals(ContentLanguage.Body.CHINESE,BodyLanguageEvidence.classify(queued));
        assertTrue(BodyVideoEvidence.textTopic(Source.V2EX,queued));assertFalse(BodyVideoEvidence.classify(Source.V2EX,queued));
        assertEquals("session-a",queued.cachedSession);assertEquals("namespace-a",queued.cachedNamespace);assertEquals(123,queued.cachedAt);assertEquals("metadata",queued.metadataHash);
    }
    @Test public void queuedEvidenceDoesNotTurnRestrictedOrVideoBodiesIntoTextEvidence(){
        Document body=new Document();body.url="https://www.v2ex.com/t/123";body.loginRequired=true;body.videoTopic=true;
        Document queued=ReadingLanguageRecorder.snapshot(body);body.loginRequired=false;body.videoTopic=false;
        assertEquals(ContentLanguage.Body.UNKNOWN,BodyLanguageEvidence.classify(queued));
        assertFalse(BodyVideoEvidence.textTopic(Source.V2EX,queued));
        assertFalse(BodyVideoEvidence.classify(Source.V2EX,queued));
    }
}
