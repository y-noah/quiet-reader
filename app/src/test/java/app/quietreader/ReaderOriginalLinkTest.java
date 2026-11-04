package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import java.util.List;

public class ReaderOriginalLinkTest {
    @Test public void regularPostSharesSourceInsteadOfInternalReaderPage(){
        Item item=new Item(Source.ZHIHU,"问题","https://www.zhihu.com/question/123","");
        assertEquals(item.url,MainActivity.originalLink(item,null));
        Document other=new Document();other.url="https://quiet-reader.invalid/";
        assertEquals(item.url,MainActivity.originalLink(item,other));
    }
    @Test public void tiebaTopicSharesTheDisplayedPostInsteadOfTheTopicDirectory(){
        String topic="https://tieba.baidu.com/hottopic/browse/hottopic?topic_id=28368032";
        Item root=new Item(Source.TIEBA,"话题",topic,""),post=new Item(Source.TIEBA,"原帖","https://tieba.baidu.com/p/11134197308","");
        Document document=new Document();document.url=post.url;
        TiebaTopic.attach(topic,List.of(post),document,"a".repeat(64),1);
        assertEquals(post.url,MainActivity.originalLink(root,document));
        assertEquals(post.url,MainActivity.originalLink(TiebaTopic.select(root,document.topic,post),null));
    }
    @Test public void unavailableOrForeignLinksAreNeverShared(){
        assertEquals("",MainActivity.originalLink(null,null));
        assertEquals("",MainActivity.originalLink(new Item(Source.ZHIHU,"无效","https://example.org/private",""),null));
    }
}
