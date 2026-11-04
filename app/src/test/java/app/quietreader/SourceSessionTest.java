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
    @Test public void malformedNativeHupuStateGetsOneBrowserAcquisitionBeforeShowingUnavailable(){
        Document document=new Document();assertFalse(SourceSession.nativeDocumentReady(Source.HUPU,document));
        document.sourceUnavailable=true;assertFalse(SourceSession.nativeDocumentReady(Source.HUPU,document));
    }
    @Test public void existingOtherSourcePermissionStatusesKeepTheirEstablishedRoute(){
        Document shell=new Document();shell.loginRequired=true;
        for(Source source:Source.values())if(source!=Source.HUPU)assertTrue(source.name(),SourceSession.nativeDocumentReady(source,shell));
    }
    @Test public void unavailableTextShellsNeedBrowserRatherThanBeingMistakenForBodies(){
        for(Source source:new Source[]{Source.BAIDU,Source.CLS,Source.SSPAI,Source.IFANR,Source.ITHOME,Source.THEPAPER,Source.BBC,Source.NYT,Source.V2EX}){
            Document shell=new Document();shell.sourceUnavailable=true;
            assertTrue(shell.canPresent());assertFalse(source.name(),SourceSession.nativeDocumentReady(source,shell));
            shell.blocks.add(new Block("text","未验证完整性的预览"));assertFalse(source.name(),SourceSession.nativeDocumentReady(source,shell));
            shell.sourceUnavailable=false;assertTrue(source.name(),SourceSession.nativeDocumentReady(source,shell));
        }
    }
    @Test public void thepaperMissingStateIsARealParserShellThatMustPermitFallback(){
        Document shell=SourceParser.article(Source.THEPAPER,"<html><body><div id=app></div></body></html>","https://www.thepaper.cn/newsDetail_forward_123456789");
        assertTrue(shell.sourceUnavailable);assertFalse(shell.hasContent());assertFalse(SourceSession.nativeDocumentReady(Source.THEPAPER,shell));
    }
    @Test public void explicitVideoAndPermissionStatesRemainTerminal(){
        for(Source source:Source.values())if(source!=Source.HUPU){
            Document video=new Document();video.sourceUnavailable=true;video.filteredVideo=true;assertTrue(source.name(),SourceSession.nativeDocumentReady(source,video));
            video.filteredVideo=false;video.unsupportedVideo=true;assertTrue(source.name(),SourceSession.nativeDocumentReady(source,video));
            video.unsupportedVideo=false;video.videoTopic=true;assertTrue(source.name(),SourceSession.nativeDocumentReady(source,video));
            video.videoTopic=false;video.filteredVideos=1;assertTrue(source.name(),SourceSession.nativeDocumentReady(source,video));
        }
        for(Source source:new Source[]{Source.DOUYIN,Source.XHS,Source.BILIBILI}){Document restricted=new Document();restricted.sourceUnavailable=true;assertTrue(source.name(),SourceSession.nativeDocumentReady(source,restricted));}
    }
    @Test public void pastLoginDoesNotForceStaticSourcesToRunABrowserForEveryArticle(){
        for(Source source:new Source[]{Source.BAIDU,Source.CLS,Source.SSPAI,Source.IFANR,Source.ITHOME,Source.THEPAPER,Source.BBC,Source.NYT,Source.HUPU,Source.V2EX}){
            assertFalse(source.name(),SourceSession.requiresWeb(source,true));
            Document body=new Document();body.blocks.add(new Block("text","正文"));assertTrue(SourceSession.nativeDocumentReady(source,body));
            assertFalse(SourceSession.nativeDocumentReady(source,new Document()));
        }
        assertTrue(SourceSession.requiresWeb(Source.ZHIHU,false));assertTrue(SourceSession.requiresWeb(Source.ZHIHU,true));
        for(Source source:new Source[]{Source.WEIBO,Source.TIEBA,Source.XHS,Source.DOUYIN})assertTrue(SourceSession.requiresWeb(source,true));
    }
}
