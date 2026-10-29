package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public final class ReaderContinuationTest {
    private Document question(){Document d=new Document();d.url="https://www.zhihu.com/question/123";return d;}
    @Test public void questionsCanFetchFirstOrNextBatchWithoutAnActionLink(){
        Document d=question();assertTrue(ReaderContinuation.automatic(Source.ZHIHU,d));
        d.moreStatus="loaded";assertTrue(ReaderContinuation.automatic(Source.ZHIHU,d));
        String html=ReaderHtml.render(d,Source.ZHIHU,19);
        assertTrue(html.contains("data-continuation='ready'"));assertFalse(html.contains("href='"+ReaderHtml.ACTION+"more'"));
    }
    @Test public void blockedAndExhaustedStatesNeverAutomaticallyRetry(){
        for(String state:new String[]{"error","login","limit","end","unknown"}){Document d=question();d.moreStatus=state;assertFalse(state,ReaderContinuation.automatic(Source.ZHIHU,d));}
        Document d=question();d.loginRequired=true;assertFalse(ReaderContinuation.automatic(Source.ZHIHU,d));d.loginRequired=false;d.sourceUnavailable=true;assertFalse(ReaderContinuation.automatic(Source.ZHIHU,d));
    }
    @Test public void forumContinuationCannotCrossThreadsOrRepeatTheCurrentPage(){
        for(Source source:new Source[]{Source.V2EX,Source.TIEBA}){
            Document d=new Document();d.url=source==Source.V2EX?"https://www.v2ex.com/t/123":"https://tieba.baidu.com/p/123";
            d.nextUrl=d.url+(source==Source.V2EX?"?p=2":"?pn=2");assertTrue(source.name(),ReaderContinuation.automatic(source,d));
            d.nextUrl=d.url;assertFalse(ReaderContinuation.automatic(source,d));
            d.nextUrl=d.url.replace("123","456")+(source==Source.V2EX?"?p=2":"?pn=2");assertFalse(ReaderContinuation.automatic(source,d));
        }
    }
    @Test public void offsiteQuestionsAndOrdinaryArticlesHaveNoAutomaticAcquisition(){
        Document d=question();d.url="https://evilzhihu.com/question/123";assertFalse(ReaderContinuation.automatic(Source.ZHIHU,d));
        d.url="https://www.zhihu.com/signin";assertFalse(ReaderContinuation.automatic(Source.ZHIHU,d));
        d.url="https://www.bbc.com/zhongwen/articles/test";assertFalse(ReaderContinuation.automatic(Source.BBC,d));
    }
    @Test public void theReadingSurfaceContainsAuthorAndBodyWithoutSourceExplanations(){
        Document d=question();d.title="正文标题";d.byline="实际作者";d.blocks.add(new Block("text","正文段落"));
        org.jsoup.nodes.Document page=org.jsoup.Jsoup.parse(ReaderHtml.render(d,Source.ZHIHU,19));
        assertTrue(page.body().text().contains("实际作者"));assertTrue(page.body().text().contains("正文段落"));
        assertFalse(page.body().text().contains("知乎"));assertFalse(page.body().text().contains("原作者内容"));assertFalse(page.body().text().contains("当前已提取内容结束"));
    }
}
