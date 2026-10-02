package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

/** Synthetic reduced fixture of 2026-10-02 WebView-observed public Tieba DOM. */
public class TiebaMetadataTest {
    private static String rich(String value){return "<div class='pb-rich-text'><div class='pb-content-item'><span class='pb-text-wrapper text'><span>"+value+"</span></span></div></div>";}
    private static String head(){return "<div class='head-line user-info'><div class='head-info'><a class='name-info-link'><div class='name-info'><a class='head-name'>合成回复作者</a><div class='tooltip'><div class='tooltip__popper'>贴吧成长等级</div><div class='icon_tshows tooltip__reference'><img src='https://tb3.bdstatic.com/synthetic-badge.png'></div></div><div class='tooltip'><div class='tooltip__popper'>本吧头衔</div><div class='icon_level tooltip__reference'><span class='level-icon'>徽章等级测试9</span><span class='tag level-yellow'>徽章称号测试</span></div></div></div></a></div></div>";}
    private static Document parse(String html){return SourceParser.article(Source.TIEBA,"<html><head><title>合成元信息隔离</title></head><body><div class='comment-content has-extra-margin'>"+html+"</div></body></html>","https://tieba.baidu.com/p/11061609054");}
    private static String content(Document d){StringBuilder s=new StringBuilder(d.byline);for(Section p:d.sections){s.append(p.label);for(Block b:p.blocks)if("text".equals(b.type))s.append(b.value);}return s.toString();}
    private static Document nested(){return parse(rich("合成主楼正常正文")+"<div class='image-card-wrapper'><img src='https://tiebapic.baidu.com/synthetic-main.jpg'></div><div class='pc-pb-comments-desc'>操作区时间测试 回复 赞</div><div class='lzl-wrapper'><div class='pb-lzl-item'>"+head()+"<div class='comment-content no-extra-margin'>"+rich("合成楼中楼真实回复")+"<div class='image-card-wrapper'><img src='https://tiebapic.baidu.com/synthetic-reply.jpg'></div><div class='pc-pb-comments-desc'>回复操作测试</div></div></div></div>");}
    @Test public void observedNestedBadgeUiDoesNotPolluteReading(){
        Document d=nested();String text=content(d);
        assertFalse("Hidden tooltip text must not become body text",text.contains("贴吧成长等级"));
        assertFalse(text.contains("本吧头衔"));assertFalse(text.contains("徽章等级测试9"));assertFalse(text.contains("徽章称号测试"));
        assertFalse(text.contains("操作区时间测试"));assertFalse(text.contains("回复操作测试"));
        assertFalse("A user badge is not article photography",d.blocks.stream().anyMatch(b->b.value.contains("synthetic-badge.png")));
    }
    @Test public void ordinaryBodyDiscussingSameWordsIsNeverTextFiltered(){
        String original="合成正文：我讨论贴吧成长等级和本吧头衔，也引用徽章称号测试，这些文字必须保留。";
        assertTrue(content(parse(rich(original))).contains(original));
    }
    @Test public void structuralMoreRepliesControlIsNotBodyButSameWordsInProseRemain(){
        Document d=parse(rich("合成正文讨论：查看全部回复按钮为何不好用")+"<div class='lzl-wrapper'><div class='show-more-lzl'>查看剩余99条回复</div></div>");
        assertFalse(content(d).contains("查看剩余99条回复"));
        assertTrue(content(d).contains("合成正文讨论：查看全部回复按钮为何不好用"));
    }
    @Test public void badgeMutationDoesNotChangeStableReadingSectionId(){
        String before=rich("合成主楼正常正文")+"<div class='lzl-wrapper'><div class='pb-lzl-item'>"+head()+"<div class='comment-content no-extra-margin'>"+rich("合成楼中楼真实回复")+"</div></div></div>";
        Document a=parse(before),b=parse(before.replace("徽章等级测试9","徽章等级测试10").replace("徽章称号测试","新徽章称号测试"));
        assertFalse(a.sections.isEmpty());assertEquals(a.sections.get(0).id,b.sections.get(0).id);
    }
    @Test public void nestedReplyAuthorBodyAndArticlePicturesSurviveCleanup(){
        Document d=nested();String text=content(d);
        assertTrue(text.contains("合成主楼正常正文"));assertTrue(text.contains("合成楼中楼真实回复"));
        assertTrue("Keep real reply attribution somewhere in the extracted reading",text.contains("合成回复作者"));
        assertTrue(d.blocks.stream().anyMatch(b->b.value.equals("https://tiebapic.baidu.com/synthetic-main.jpg")));
        assertTrue(d.blocks.stream().anyMatch(b->b.value.equals("https://tiebapic.baidu.com/synthetic-reply.jpg")));
    }
    @Test public void nestedVideoReplyDoesNotEraseParentOrOtherTextReplies(){
        String videoReply="<div class='pb-lzl-item'><div class='comment-content no-extra-margin'>"+rich("视频楼中楼配文应过滤")+"<video src='https://example.invalid/synthetic.mp4'></video><img src='https://tiebapic.baidu.com/synthetic-video-poster.jpg'></div></div>";
        String textReply="<div class='pb-lzl-item'><div class='comment-content no-extra-margin'>"+rich("另一个文字回复讨论视频这个词但没有播放器")+"</div></div>";
        Document d=parse(rich("父楼纯文字应继续可读")+"<div class='lzl-wrapper'>"+videoReply+textReply+"</div>");
        assertFalse(d.filteredVideo);assertTrue(d.hasContent());
        assertTrue(content(d).contains("父楼纯文字应继续可读"));
        assertTrue(content(d).contains("另一个文字回复讨论视频这个词但没有播放器"));
        assertFalse(content(d).contains("视频楼中楼配文应过滤"));
        assertFalse(d.blocks.stream().anyMatch(b->b.value.contains("synthetic-video-poster")));
        assertTrue("Filtered nested media should remain explicitly accounted for",d.filteredVideos>0);
    }
    @Test public void confirmedMainVideoStillFiltersWholeTopicIncludingReplies(){
        String html="<html><head><title>合成视频主楼</title></head><body><div class='pb-content-wrap'>"+rich("视频主楼配文")+"<video src='https://example.invalid/main.mp4'></video><div class='comment-content'>"+rich("不可拿回复替代视频主楼")+"</div></div></body></html>";
        Document d=SourceParser.article(Source.TIEBA,html,"https://tieba.baidu.com/p/11061609054");
        assertTrue(d.filteredVideo);assertFalse(d.hasContent());assertTrue(d.sections.isEmpty());
    }
}
