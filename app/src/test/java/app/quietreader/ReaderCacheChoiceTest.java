package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReaderCacheChoiceTest {
    private final Item item=new Item(Source.THEPAPER,"当前标题","https://www.thepaper.cn/newsDetail_forward_123","");
    private Document body(long at,String text){Document doc=new Document();doc.url=item.url;doc.cachedAt=at;doc.blocks.add(new Block("text",text));ArticleVersion.capture(item,doc);return doc;}
    private Document choose(Document history,Document memory){return ArticleVersion.preferredCached(history,memory,doc->ArticleVersion.matches(item,doc));}
    @Test public void newerPreparedBodyReplacesOlderHistory(){Document history=body(100,"旧正文"),memory=body(200,"新正文及评论");assertSame(memory,choose(history,memory));}
    @Test public void invalidHistoryDoesNotMaskValidPreparedBody(){Document history=body(100,"旧版"),memory=body(200,"当前版");history.metadataHash="old";assertSame(memory,choose(history,memory));}
    @Test public void equalAcquisitionKeepsExpandedHistory(){Document history=body(100,"正文及手动展开的后续内容"),memory=body(100,"正文");assertSame(history,choose(history,memory));}
    @Test public void olderOrInvalidMemoryDoesNotReplaceHistory(){Document history=body(200,"当前正文"),memory=body(100,"旧正文");assertSame(history,choose(history,memory));memory.cachedAt=300;memory.metadataHash="old";assertSame(history,choose(history,memory));}
    @Test public void missingAndRejectedBodiesFallThrough(){Document valid=body(100,"正文"),bad=body(200,"旧版");bad.metadataHash="old";assertSame(valid,choose(null,valid));assertSame(valid,choose(valid,null));assertNull(choose(bad,null));assertNull(choose(null,bad));assertNull(choose(null,null));}
}
