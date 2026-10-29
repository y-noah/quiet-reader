package app.quietreader.discovery;

/** Transient work status, separate from the ranking of the displayed snapshot. */
public final class RecommendationState {
    public enum Phase { IDLE,RUNNING,PAUSED,REPAIRING,READY,PARTIAL }
    public static final RecommendationState INITIAL=new RecommendationState(Phase.IDLE,"选择想多看或少看的话题后，在本机整理顺序");
    public final Phase phase;public final String detail;
    public RecommendationState(Phase phase,String detail){this.phase=java.util.Objects.requireNonNull(phase);this.detail=java.util.Objects.requireNonNull(detail);}
    /** Screen-reader state, separate from the update button's refresh/accept action. */
    public String accessibilityStatus(){
        switch(phase){
            case RUNNING:return "正在本机整理";
            case PARTIAL:return "本轮推荐部分完成";
            case READY:return "本轮推荐已完成";
            case PAUSED:return "本轮推荐已暂停";
            case REPAIRING:return "正在检查本机模型";
            default:return "尚未开始本轮整理";
        }
    }
    public String hint(boolean loading,boolean newest,boolean personal,boolean pending,boolean hasRows){
        if(loading)return hasRows?"正在更新热门话题，已有内容可继续阅读":"正在获取热门话题…";
        if(phase==Phase.REPAIRING)return "正在检查本机模型，当前列表仍可阅读";
        if(phase==Phase.RUNNING)return "正在按兴趣整理，当前列表保持不变";
        if(phase==Phase.PAUSED)return "本次整理已暂停，点此查看原因";
        if(phase==Phase.PARTIAL)return pending?"部分结果已准备，可查看更新":"本次整理已暂停，点此查看原因";
        if(phase==Phase.READY&&pending)return "新顺序已准备，点“查看更新”接收";
        if(pending)return "有内容或顺序更新，可查看更新";
        if(newest)return "按来源提供的发布时间排列";
        return personal?"当前顺序已参考你的阅读偏好":"从各平台热门中挑选，可逐步调教兴趣";
    }
}
