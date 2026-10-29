package app.quietreader;

import app.quietreader.discovery.RecommendationState;
import static app.quietreader.discovery.RecommendationState.Phase.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecommendationStateTest {
    private String hint(RecommendationState.Phase phase,boolean personal,boolean pending){return new RecommendationState(phase,"诊断详情 · 已缓存向量 12/20").hint(false,false,personal,pending,true);}
    @Test public void workProgressNeverClaimsThatVisibleColdCardsWereReordered(){
        assertTrue(hint(RUNNING,false,false).contains("保持不变"));
        assertTrue(hint(READY,false,true).contains("查看更新"));
        assertFalse(hint(READY,false,false).contains("已参考"));
        assertTrue(hint(READY,true,false).contains("已参考"));
        assertTrue(RecommendationState.INITIAL.hint(false,false,false,true,true).contains("查看更新"));
    }
    @Test public void pauseAndPartialResultsKeepTheExistingSnapshotReadable(){
        assertTrue(hint(PAUSED,true,false).contains("暂停"));
        assertTrue(hint(PARTIAL,false,true).contains("部分结果"));
        assertFalse(hint(PARTIAL,false,false).contains("已参考"));
        assertTrue(hint(REPAIRING,false,false).contains("仍可阅读"));
    }
    @Test public void sourceRefreshAndActualChronologicalModeRemainDistinct(){
        RecommendationState state=new RecommendationState(PAUSED,"温度升高");
        assertTrue(state.hint(true,false,false,false,true).contains("已有内容"));
        assertFalse(state.hint(true,false,false,false,false).contains("已有内容"));
        assertTrue(RecommendationState.INITIAL.hint(false,true,true,false,true).contains("发布时间"));
        assertFalse(hint(PARTIAL,false,true).contains("12/20"));
    }
}
