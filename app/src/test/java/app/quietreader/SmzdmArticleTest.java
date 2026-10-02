package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SmzdmArticleTest {
    private static final String URL="https://www.smzdm.com/p/123/";
    private static final String IMAGE="https://y.zdmimg.com/202609/29/product.jpg_d250.jpg";
    private String header(String id,String image){return "<div id='feed-main'><div class='info J_info' articleid='3_"+id+"'><a class='img-box' href='https://go.smzdm.com/shop'><img class='main-img' src='"+image+"' onclick='evil()'></a></div></div>";}
    private String body(){return "<h1>测试商品</h1><article class='txt-detail'><p>全额预售，售价399元/件。</p></article>";}
    @Test public void includesMatchingProductHeroInReaderAndImageAllowlist(){
        Document d=SourceParser.article(Source.SMZDM,header("123",IMAGE)+body(),URL);
        assertEquals(2,d.blocks.size());assertEquals("image",d.blocks.get(0).type);assertEquals(IMAGE,d.blocks.get(0).value);
        assertEquals(2,d.sections.get(0).blocks.size());assertTrue(d.containsImage(IMAGE));
        String html=ReaderHtml.render(d,Source.SMZDM,19);
        assertTrue(html.contains(IMAGE));assertTrue(html.contains("全额预售"));assertFalse(html.contains("onclick"));assertFalse(html.contains("go.smzdm.com"));
    }
    @Test public void doesNotMakeBlockedPageReadableOrUseOtherProductOrUnsafeImage(){
        assertFalse(SourceParser.article(Source.SMZDM,header("123",IMAGE)+"<h1>请登录</h1>",URL).hasContent());
        assertEquals(1,SourceParser.article(Source.SMZDM,header("456",IMAGE)+body(),URL).blocks.size());
        assertEquals(1,SourceParser.article(Source.SMZDM,header("123","javascript:alert(1)")+body(),URL).blocks.size());
        assertEquals(1,SourceParser.article(Source.SMZDM,header("123","https://evil.test/image.jpg")+body(),URL).blocks.size());
        assertEquals(1,SourceParser.article(Source.SMZDM,"<aside><img class='main-img' src='"+IMAGE+"'></aside>"+body(),URL).blocks.size());
        assertEquals(1,SourceParser.article(Source.SMZDM,"<aside>"+header("123",IMAGE)+"</aside>"+body(),URL).blocks.size());
    }
    @Test public void bodyImageIsNotDuplicated(){
        Document d=SourceParser.article(Source.SMZDM,header("123",IMAGE)+"<article class='txt-detail'><p>商品说明</p><img src='"+IMAGE+"'></article>",URL);
        assertEquals(2,d.blocks.size());assertEquals(1,d.blocks.stream().filter(b->b.type.equals("image")).count());
    }
    @Test public void acceptsLazyHeroButDoesNotUseSocialSharingMetadata(){
        String lazy=header("123",IMAGE).replace("src='"+IMAGE+"'","src='data:image/gif;base64,AA' data-src='"+IMAGE+"'");
        assertTrue(SourceParser.article(Source.SMZDM,lazy+body(),URL).containsImage(IMAGE));
        assertFalse(SourceParser.article(Source.SMZDM,"<meta property='og:image' content='"+IMAGE+"'>"+body(),URL).containsImage(IMAGE));
    }
    @Test public void nestedRecommendationImageCannotMasqueradeAsProductHero(){
        String recommendation="<aside class='recommend'><a class='img-box'><img class='main-img' src='"+IMAGE+"'></a></aside>";
        String html="<div id='feed-main'><div class='J_info' articleid='3_123'>"+recommendation+"</div></div>"+body();
        Document d=SourceParser.article(Source.SMZDM,html,URL);
        assertTrue(d.hasContent());assertFalse(d.containsImage(IMAGE));
    }
    @Test public void recommendationBeforeRealHeroDoesNotHideTheOwnedImage(){
        String unrelated="https://y.zdmimg.com/202609/29/recommended.jpg";
        String recommendation="<div class='recommend'><a class='img-box'><img class='main-img' src='"+unrelated+"'></a></div>";
        String html=header("123",IMAGE).replace("articleid='3_123'>","articleid='3_123'>"+recommendation)+body();
        Document d=SourceParser.article(Source.SMZDM,html,URL);
        assertFalse(d.containsImage(unrelated));assertTrue(d.containsImage(IMAGE));
        assertEquals(1,d.blocks.stream().filter(b->b.type.equals("image")).count());
    }
    @Test public void acceptsObservedQnyProductCdnWithoutBroadeningToEverySmzdmHost(){
        String observed="https://qny.smzdm.com/202104/20/product.jpg";
        assertTrue(SourceParser.article(Source.SMZDM,header("123",observed)+body(),URL).containsImage(observed));
        for(String unsafe:new String[]{"https://qny.smzdm.com.evil.test/product.jpg","https://unknown.smzdm.com/product.jpg","https://qny.smzdm.com@evil.test/product.jpg"})
            assertFalse(SourceParser.article(Source.SMZDM,header("123",unsafe)+body(),URL).containsImage(unsafe));
    }
}
