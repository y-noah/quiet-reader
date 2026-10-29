package app.quietreader;

import app.quietreader.ai.ModelWorkUsage;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class ModelWorkUsageTest {
    @Test public void accumulatesNativeProcessCpuAcrossStagesWithoutMixingCacheCounts(){
        ModelWorkUsage usage=new ModelWorkUsage();
        usage.begin("embedding",Arrays.asList("a","b"));usage.item("a",10,28);usage.item("b",12,31);usage.done(100,70);
        usage.begin("reranker",Collections.singleton("a"));usage.item("a",20,48);usage.done(140,90);
        String text=usage.description();assertTrue(text.contains("初筛 2 条，精排 1 条"));assertTrue(text.contains("推理 CPU 107 ms / 用时 42 ms"));assertTrue(text.contains("加载 CPU 160 ms / 用时 240 ms"));assertTrue(text.contains("不代表整机耗电"));
    }
    @Test public void rejectsUnexpectedOrDuplicateItemsAndDuplicateBatchDone(){
        ModelWorkUsage usage=new ModelWorkUsage();usage.begin("embedding",Collections.singleton("a"));usage.item("unknown",100,100);usage.item("a",2,3);usage.item("a",100,100);usage.done(4,5);usage.done(100,100);
        assertTrue(usage.description().contains("初筛 1 条，精排 0 条"));assertTrue(usage.description().contains("推理 CPU 3 ms / 用时 2 ms"));assertTrue(usage.description().contains("加载 CPU 5 ms / 用时 4 ms"));
    }
    @Test public void missingTimingRemainsUnknownAndZeroReportedWorkIsNotWholeAppIdle(){
        ModelWorkUsage usage=new ModelWorkUsage();usage.begin("embedding",Collections.singleton("a"));usage.item("a",-1,2);usage.done(1,-1);
        assertTrue(usage.description().contains("有 2 项计时未回报"));assertTrue(usage.description().contains("不含未完成任务、内容获取和界面"));assertFalse(usage.description().contains("CPU -"));
        assertTrue(new ModelWorkUsage().description().contains("缓存命中不计为新推理"));
    }
    @Test public void countersSaturateInsteadOfWrappingToNegative(){
        ModelWorkUsage usage=new ModelWorkUsage();usage.begin("embedding",Arrays.asList("a","b"));usage.item("a",Long.MAX_VALUE,Long.MAX_VALUE);usage.item("b",10,10);usage.done(Long.MAX_VALUE,Long.MAX_VALUE);usage.begin("reranker",Collections.singleton("c"));usage.done(10,10);
        assertTrue(usage.description().contains("推理 CPU "+Long.MAX_VALUE+" ms / 用时 "+Long.MAX_VALUE+" ms"));assertTrue(usage.description().contains("加载 CPU "+Long.MAX_VALUE+" ms / 用时 "+Long.MAX_VALUE+" ms"));
    }
}
