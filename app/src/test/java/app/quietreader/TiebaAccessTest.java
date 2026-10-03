package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class TiebaAccessTest {
    private Document parse(String extra){return SourceParser.article(Source.TIEBA,"<h1>合成主帖</h1><div class='d_post_content'>主帖正文</div>"+extra,"https://tieba.baidu.com/p/123");}
    private String gate="<div class='login-guard-mask'>登录后查看全部评论内容</div>";
    @Test public void actualVisibleGateStillReportsRestriction(){Document d=parse(gate);assertTrue(d.loginRequired);assertTrue(d.notice.contains("尚未取得受限回复"));}
    @Test public void quotedPhraseIsNotAClaimThatUserIsLoggedOut(){Document d=parse("<div class='d_post_content'>我不喜欢登录后查看全部评论内容这个提示。</div>");assertFalse(d.loginRequired);assertFalse(d.notice.contains("请登录"));assertTrue(d.blocks.stream().anyMatch(b->b.value.contains("我不喜欢登录后查看")));}
    @Test public void inlineHiddenGateDoesNotTriggerWarning(){for(String attr:new String[]{"hidden","style='display: none'","style='color:red;display:none !important;color:blue'","style='visibility:hidden'"})assertFalse(parse("<div "+attr+">"+gate+"</div>").loginRequired);}
    @Test public void visibleGateAlongsideHiddenGateRemainsRestricted(){assertTrue(parse("<div hidden>"+gate+"</div>"+gate).loginRequired);}
    @Test public void mobileAuthIsAllowedOnlyInExplicitTiebaLogin(){for(String host:new String[]{"passport.baidu.com","wappass.baidu.com"}){String url="https://"+host+"/passport/login";assertTrue(UrlPolicy.loginAllowed(Source.TIEBA,url));assertFalse(UrlPolicy.belongs(Source.TIEBA,url));assertFalse(UrlPolicy.loginAllowed(Source.ZHIHU,url));assertFalse(UrlPolicy.loginAllowed(Source.TIEBA,"https://"+host+".evil.test/"));}assertFalse(UrlPolicy.loginAllowed(Source.TIEBA,"https://www.baidu.com/"));assertFalse(UrlPolicy.loginAllowed(Source.TIEBA,"http://wappass.baidu.com/"));}
}
