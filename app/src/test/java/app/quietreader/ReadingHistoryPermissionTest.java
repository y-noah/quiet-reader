package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;

public class ReadingHistoryPermissionTest {
    @Test public void snapshotDoesNotTurnRestrictedPreviewIntoCompleteBody()throws Exception{
        for(int mode=0;mode<3;mode++){
            Document doc=new Document();doc.url="https://www.zhihu.com/question/123";doc.title="登录受限的预览";doc.blocks.add(new Block("text","只读到摘要，后面的内容仍然需要登录。"));
            doc.loginRequired=mode==0;doc.sourceUnavailable=mode==1;doc.filteredVideo=mode==2;
            Document restored=ReadingHistory.decode(ReadingHistory.encode(doc));
            assertEquals(doc.loginRequired,restored.loginRequired);assertEquals(doc.sourceUnavailable,restored.sourceUnavailable);assertEquals(doc.filteredVideo,restored.filteredVideo);
        }
    }
}
