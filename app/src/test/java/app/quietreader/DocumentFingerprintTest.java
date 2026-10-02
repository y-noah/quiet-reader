package app.quietreader;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class DocumentFingerprintTest {
    private Document document() {
        Document d=new Document();d.title="合成问题";d.url="https://www.zhihu.com/question/123";
        Section s=new Section("answer-1","回答 · 甲",true);
        s.blocks.add(new Block("text","正文摘要"));d.sections.add(s);d.blocks.addAll(s.blocks);
        return d;
    }
    @Test public void independentlyParsedIdenticalContentMatches() {
        assertEquals(DocumentFingerprint.of(document()),DocumentFingerprint.of(document()));
    }
    @Test public void sameBlockCountDoesNotHideGrowingTextOrReplacement() {
        Document d=document();String before=DocumentFingerprint.of(d);
        d.blocks.set(0,new Block("text","正文摘要随后加载完整内容"));
        assertNotEquals(before,DocumentFingerprint.of(d));
        d.blocks.set(0,new Block("text","另一摘要"));
        assertNotEquals(before,DocumentFingerprint.of(d));
    }
    @Test public void sameFlatTextStillDetectsDifferentAnswerIdentityAndSectionBody() {
        Document d=document();String before=DocumentFingerprint.of(d);
        Section replacement=new Section("answer-2","回答 · 甲",true);replacement.blocks.addAll(d.blocks);
        d.sections.set(0,replacement);assertNotEquals(before,DocumentFingerprint.of(d));
        d=document();d.sections.get(0).blocks.set(0,new Block("text","回答变长但平铺内容未变化"));
        assertNotEquals(before,DocumentFingerprint.of(d));
    }
    @Test public void detectsNormalAndInlineImageChanges() {
        Document d=document();d.blocks.add(new Block("image","https://example.invalid/1.png"));
        String before=DocumentFingerprint.of(d);d.blocks.set(1,new Block("image","https://example.invalid/2.png"));
        assertNotEquals(before,DocumentFingerprint.of(d));
        d.blocks.set(1,new Block("text","[图]",Collections.singletonList(new InlineImage(0,3,"https://example.invalid/1.png","图"))));
        before=DocumentFingerprint.of(d);
        d.blocks.set(1,new Block("text","[图]",Collections.singletonList(new InlineImage(0,3,"https://example.invalid/2.png","图"))));
        assertNotEquals(before,DocumentFingerprint.of(d));
        before=DocumentFingerprint.of(d);
        d.blocks.set(1,new Block("text","[图]",Collections.singletonList(new InlineImage(1,3,"https://example.invalid/2.png","图"))));
        assertNotEquals(before,DocumentFingerprint.of(d));
    }
    @Test public void relatedDestinationAndPaginationNoticeMatter() {
        Document d=document();d.related.add(new Item(Source.ZHIHU,"下一篇","https://www.zhihu.com/question/124",""));
        String before=DocumentFingerprint.of(d);
        d.related.set(0,new Item(Source.ZHIHU,"下一篇","https://www.zhihu.com/question/125",""));
        assertNotEquals(before,DocumentFingerprint.of(d));
        before=DocumentFingerprint.of(d);d.notice="需要登录";assertNotEquals(before,DocumentFingerprint.of(d));
        before=DocumentFingerprint.of(d);d.nextUrl="https://www.zhihu.com/question/123?page=2";
        assertNotEquals(before,DocumentFingerprint.of(d));
    }
    @Test public void lengthsAndTypesPreventFieldBoundaryAmbiguity() {
        Document a=document(),b=document();a.title="ab";a.byline="c";b.title="a";b.byline="bc";
        assertNotEquals(DocumentFingerprint.of(a),DocumentFingerprint.of(b));
        b=document();String before=DocumentFingerprint.of(b);b.blocks.set(0,new Block("heading","正文摘要"));
        assertNotEquals(before,DocumentFingerprint.of(b));
    }
    @Test public void mediaCapabilityChangeBreaksStability() {
        Document d=document();String before=DocumentFingerprint.of(d);
        d.unsupportedVideo=true;
        assertNotEquals(before,DocumentFingerprint.of(d));
    }
}
