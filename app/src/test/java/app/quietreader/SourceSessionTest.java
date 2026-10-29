package app.quietreader;

import org.junit.Test;
import static app.quietreader.Models.*;
import static org.junit.Assert.*;

public class SourceSessionTest {
    @Test public void hupuLoginShellRequiresBrowserEvenWhenItIsAPresentableStatus(){
        Document shell=new Document();shell.loginRequired=true;
        assertTrue(shell.canPresent());assertFalse(SourceSession.nativeDocumentReady(Source.HUPU,shell));
        shell.blocks.add(new Block("text","受限的预览"));
        assertFalse(SourceSession.nativeDocumentReady(Source.HUPU,shell));
        shell.loginRequired=false;assertTrue(SourceSession.nativeDocumentReady(Source.HUPU,shell));
    }
    @Test public void emptyHupuStillFallsBackButActualUnavailableStatusIsPreserved(){
        Document document=new Document();assertFalse(SourceSession.nativeDocumentReady(Source.HUPU,document));
        document.sourceUnavailable=true;assertTrue(SourceSession.nativeDocumentReady(Source.HUPU,document));
    }
    @Test public void existingOtherSourcePermissionStatusesKeepTheirEstablishedRoute(){
        Document shell=new Document();shell.loginRequired=true;
        for(Source source:Source.values())if(source!=Source.HUPU)assertTrue(source.name(),SourceSession.nativeDocumentReady(source,shell));
    }
}
