package app.quietreader;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class SmzdmBoardTest {
    private String row(String id,String type,String tab,String title,String price) {
        return "<li data-type='"+type+"' data-tab='"+tab+"'><h5 class='feed-ver-title'><a href='https://www.smzdm.com/p/"+id+"/'>"+title+"</a></h5><div class='z-highlight'>"+price+"</div><a class='tag-bottom-right'>京东</a><div class='feed-ver-date'>07:01</div><a class='z-group-data' href='https://www.smzdm.com/p/"+id+"/#comments'><i></i>3</a></li>";
    }
    private String page(String rows) {return "<title>精选_3小时内最热优惠排行_什么值得买</title><ul id='feed-main-list'>"+rows+"</ul>";}
    @Test public void preservesOfficialOrderPriceConditionsAndMerchant() throws Exception {
        List<Item> items=SourceParser.list(Source.SMZDM,page(row("20","普通","3h最热","可口可乐 &amp; 饮料","6.9元（需用券）")+row("10","普通","3h最热","另一件商品","31.9元（需买2件）")));
        assertEquals(2,items.size());assertTrue(items.get(0).url.endsWith("/20/"));
        assertEquals("可口可乐 & 饮料",items.get(0).title);
        assertEquals("6.9元（需用券） · 京东 · 07:01 · 3 评论",items.get(0).detail);
        assertTrue(items.get(1).detail.contains("需买2件"));
    }
    @Test public void ignoresOtherBoardsAdsAndDuplicates() throws Exception {
        String valid=row("1","普通","3h最热","真正的商品","0元");
        List<Item> items=SourceParser.list(Source.SMZDM,page(valid+valid+row("2","广告","3h最热","广告商品","1元")+row("3","普通","12h最热","十二小时","2元")+row("4","普通","3h最热","站外链接","1元").replace("www.smzdm.com/p/4/","evil.test/p/4/"))+row("6","普通","3h最热","榜外推荐","99元"));
        assertEquals(1,items.size());assertEquals("真正的商品",items.get(0).title);
    }
    @Test public void rejectsWrongPeriodCaptchaAndOldCollection() throws Exception {
        for(String raw:new String[]{page(row("1","普通","3h最热","商品","1元")).replace("3小时内最热","24小时内最热"),"<script>captcha.show()</script>","<h1>热搜商品</h1><a href='https://www.smzdm.com/p/1/'>商品名称</a>"}){
            try{SourceParser.list(Source.SMZDM,raw);fail("Must not relabel other content as 3h deals");}catch(IllegalStateException expected){}
        }
    }
}
