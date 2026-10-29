package app.quietreader;

import java.util.*;

/** Event-only prediction. Never owns a timer, renews action credit or changes ranking. */
final class PrefetchWindow {
    static final int LIMIT=40,NORMAL_LEAD=32,RAPID_LEAD=36;
    private boolean observing,anchored;
    private int first,top,direction=1,opposite;
    private long movedAt=-1,flingAt=-1;
    void gesture(boolean fling,long now){observing=true;opposite=0;if(fling)flingAt=now;else flingAt=-1;}
    void idle(){observing=false;opposite=0;}
    void stop(){idle();anchored=false;flingAt=-1;}
    void reset(){stop();direction=1;movedAt=-1;}
    void viewport(int nextFirst,int nextTop,long now){
        nextFirst=Math.max(0,nextFirst);
        if(!observing||!anchored){first=nextFirst;top=nextTop;anchored=true;return;}
        long rows=(long)nextFirst-first,pixels=(long)top-nextTop;
        if(rows==0&&Math.abs(pixels)<4)return;
        int next=rows!=0?Long.signum(rows):Long.signum(pixels);
        if(next==direction)opposite=0;
        else if(Math.abs(rows)>=2||++opposite>=2){direction=next;opposite=0;}
        movedAt=now;first=nextFirst;top=nextTop;
    }
    int direction(long now){return movedAt<0||now<movedAt||now-movedAt>=30_000?1:direction;}
    boolean rapid(long now){return flingAt>=0&&now>=flingAt&&now-flingAt<3_000;}
    List<Integer> positions(int total,int first,int visible,long now){return select(total,first,visible,direction(now),rapid(now),false);}
    static List<Integer> neighbors(int total,int at,int direction,boolean rapid){
        List<Integer> selected=select(total,at,1,direction,rapid,true);if(!selected.isEmpty())selected.remove(0);return selected;
    }
    private static List<Integer> select(int total,int anchor,int visible,int direction,boolean rapid,boolean reading){
        List<Integer> result=new ArrayList<>(LIMIT+1);if(total<=0)return result;
        int first=Math.min(total-1,Math.max(0,anchor)),limit=LIMIT+(reading?1:0);
        int count=Math.min(limit,Math.max(1,visible)),high=first+Math.min(total-first,count),low=first;
        for(int n=first;n<high;n++)result.add(n);
        int lead=rapid?RAPID_LEAD:NORMAL_LEAD,reverse=LIMIT-lead;
        int forward=(direction>=0?lead:reverse)+(reading?1:0),backward=direction>=0?reverse:lead;
        if(direction>=0){high=forward(result,total,first,high,forward,limit);low=backward(result,low,backward,limit);}
        else{low=backward(result,low,backward,limit);high=forward(result,total,first,high,forward,limit);}
        // At either list edge, reuse unused slots on the side that still has content.
        while(result.size()<limit&&(low>0||high<total)){
            if(direction>=0&&high<total||low==0)result.add(high++);
            else result.add(--low);
        }
        return result;
    }
    private static int forward(List<Integer> out,int total,int first,int high,int quota,int limit){int end=first+Math.min(total-first,quota);while(high<end&&out.size()<limit)out.add(high++);return high;}
    private static int backward(List<Integer> out,int low,int quota,int limit){int end=Math.max(0,low-quota);while(low>end&&out.size()<limit)out.add(--low);return low;}
}
