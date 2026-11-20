package app.quietreader.discovery;

/** Comparable position within an actual source hot list, never a sum of raw counts. */
public final class SourceHeat {
    private SourceHeat(){}
    public static double strength(HotCandidate item){
        switch(item.source){
            case ZHIHU:case WEIBO:case HUPU:case CLS:case V2EX:case SOLIDOT:case ITHOME:
            case THEPAPER:case BBC:case NYT:case BAIDU:case TIEBA:case DOUYIN:case BILIBILI:case QBITAI:
                return Math.log(2)/Math.log(item.sourceRank+1.0);
            default:return 0; // Newest-first feeds are not popularity measurements.
        }
    }
    public static boolean strong(HotCandidate item){return item.sourceRank<=3&&strength(item)>0;}
}
