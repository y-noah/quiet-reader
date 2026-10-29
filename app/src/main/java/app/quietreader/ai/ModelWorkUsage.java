package app.quietreader.ai;

import java.util.*;

/** Only work actually reported by the model process; never a battery or whole-app estimate. */
public final class ModelWorkUsage {
    private final Set<String> expected=new HashSet<>(),received=new HashSet<>();
    private String kind="";
    private boolean batchDone;
    private long embeddings,reranks,cpuMs,elapsedMs,loadCpuMs,loadMs,missing;
    public void begin(String kind,Collection<String> ids){
        if(!kind.equals("embedding")&&!kind.equals("reranker"))throw new IllegalArgumentException("Unknown model stage");
        this.kind=kind;expected.clear();expected.addAll(ids);received.clear();batchDone=false;
    }
    public void item(String id,long elapsed,long cpu){
        if(!expected.contains(id)||!received.add(id))return;
        if(kind.equals("embedding"))embeddings=add(embeddings,1);else reranks=add(reranks,1);
        if(elapsed<0||cpu<0){missing=add(missing,1);return;}
        elapsedMs=add(elapsedMs,elapsed);cpuMs=add(cpuMs,cpu);
    }
    public void done(long elapsed,long cpu){
        if(kind.isEmpty()||batchDone)return;batchDone=true;
        if(elapsed<0||cpu<0){missing=add(missing,1);return;}
        loadMs=add(loadMs,elapsed);loadCpuMs=add(loadCpuMs,cpu);
    }
    private static long add(long a,long b){return a>Long.MAX_VALUE-b?Long.MAX_VALUE:a+b;}
    public String description(){
        return "已回报模型工作量：初筛 "+embeddings+" 条，精排 "+reranks+" 条；推理 CPU "+cpuMs+" ms / 用时 "+elapsedMs+" ms；加载 CPU "+loadCpuMs+" ms / 用时 "+loadMs+" ms"
                +(missing>0?"；有 "+missing+" 项计时未回报":"")
                +"。仅模型进程已回报部分，缓存命中不计为新推理；不含未完成任务、内容获取和界面，不代表整机耗电。";
    }
}
