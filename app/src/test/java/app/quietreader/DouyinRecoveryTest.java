package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
public class DouyinRecoveryTest {
 @Test public void canonicalTopicReturnsToServerRenderedEntry(){assertEquals("https://www.douyin.com/hot/2689461",SourceMobileLayout.recoveryUrl(Source.DOUYIN,"https://www.douyin.com/video/123?modeFrom=hotDetail&sentenceId=2689461&position=1"));}
 @Test public void ordinaryVideosAndOtherOriginsAreUnchanged(){for(String url:new String[]{"https://www.douyin.com/video/123","https://www.douyin.com/video/123?sentenceId=x","https://evil.test/video/123?sentenceId=1","https://www.douyin.com.evil.test/video/123?sentenceId=1","https://user@www.douyin.com/video/123?sentenceId=1","http://www.douyin.com/video/123?sentenceId=1","https://www.douyin.com/hot/123?sentenceId=1"})assertEquals(url,SourceMobileLayout.recoveryUrl(Source.DOUYIN,url));}
 @Test public void otherSourcesKeepTheirNavigation(){String url="https://www.douyin.com/video/123?sentenceId=1";assertEquals(url,SourceMobileLayout.recoveryUrl(Source.BILIBILI,url));assertNull(SourceMobileLayout.recoveryUrl(Source.DOUYIN,null));}
}
