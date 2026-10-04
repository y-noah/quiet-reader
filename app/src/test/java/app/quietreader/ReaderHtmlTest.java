package app.quietreader;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
public class ReaderHtmlTest {
    @Test public void imageCaptionIsAnIndependentRetryEntryWithoutPageScripts(){
        Document d=new Document();d.title="Image";d.url="https://www.cls.cn/detail/1";
        String image="https://example.com/photo.png?a=1&b=2";d.blocks.add(new Block("image",image));
        for(boolean dark:new boolean[]{false,true}){
            String html=ReaderHtml.render(d,Source.CLS,19,dark,java.util.Collections.emptySet(),false);
            assertTrue(html.contains("<figcaption><a href='https://example.com/photo.png?a=1&amp;b=2'>查看大图</a>"));
            assertTrue(html.contains("图片可能稍后显示，点开可重试"));
            assertFalse(html.contains("轻点图片放大"));assertFalse(html.contains("<script"));assertFalse(html.contains("onload="));
            assertTrue(html.contains("default-src 'none'"));
        }
    }
    @Test public void shortImageCollectionsFoldAcrossPlatforms(){
        Document d=new Document();d.title="Pictures";d.url="https://bbs.hupu.com/1.html";
        d.blocks.add(new Block("text","Short caption"));for(int i=0;i<12;i++)d.blocks.add(new Block("image","https://example.com/"+i+".png"));
        for(Source source:Source.values()){String html=ReaderHtml.render(d,source,19);assertTrue(html.contains("展开阅读"));assertTrue(html.contains("包含 12 张图片"));assertFalse(html.contains("<img "));}
    }
    @Test public void readingAnchorsSurviveGrowthOfEarlierSection(){
        Document d=new Document();d.url="https://www.zhihu.com/question/1";
        Section first=new Section("first","One",true),second=new Section("second","Two",true);first.blocks.add(new Block("text","a"));second.blocks.add(new Block("text","b"));d.sections.add(first);d.sections.add(second);
        java.util.Set<String> expanded=new java.util.HashSet<>(java.util.Arrays.asList("first","second"));
        String anchor="id='"+ReaderHtml.anchorId("second",0)+"'";assertTrue(ReaderHtml.render(d,Source.ZHIHU,19,false,expanded,false).contains(anchor));
        first.blocks.add(new Block("text","new"));assertTrue(ReaderHtml.render(d,Source.ZHIHU,19,false,expanded,false).contains(anchor));
    }
    @Test public void remoteMarkupIsEscapedNotExecuted() {
        Document d=new Document();d.title="<script>evil()</script>";d.url="https://www.ifanr.com/1";
        d.blocks.add(new Block("text","<img src=x onerror=evil()>"));d.blocks.add(new Block("image","javascript:evil()"));
        String html=ReaderHtml.render(d,Source.IFANR,19);
        assertFalse(html.contains("<script>"));assertFalse(html.contains("src='javascript:"));assertTrue(html.contains("&lt;img"));assertTrue(html.contains("default-src 'none'"));
    }
    @Test public void preservesPartialContentWarning() {
        Document d=new Document();d.notice="部分内容";d.title="Test";d.url="https://www.zhihu.com/question/1";
        assertTrue(ReaderHtml.render(d,Source.ZHIHU,100).contains("部分内容"));
        assertFalse(ReaderHtml.render(d,Source.ZHIHU,100).contains("font-size:100px"));
    }
}
