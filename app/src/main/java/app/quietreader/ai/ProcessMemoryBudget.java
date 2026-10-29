package app.quietreader.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.*;
import java.util.*;

/** Public same-UID PSS is a lower bound: isolated WebView renderers may be invisible. */
public final class ProcessMemoryBudget {
    public static final long SOFT_LIMIT_KIB=850L*1024;
    private static Snapshot previous;
    private static Set<Integer> previousPids=Collections.emptySet();
    private static long sampledAt=-1;
    private ProcessMemoryBudget(){}
    public static final class Snapshot {
        public final long pssKiB;
        public final int measuredProcesses,requestedProcesses;
        public final boolean includesMain,processListAvailable;
        Snapshot(long pss,int measured,int requested,boolean main,boolean enumerated){pssKiB=pss;measuredProcesses=measured;requestedProcesses=requested;includesMain=main;processListAvailable=enumerated;}
        public boolean overBudget(){return exceeds(pssKiB);}
        public boolean shouldPause(){return ProcessMemoryBudget.shouldPause(pssKiB,measuredProcesses,requestedProcesses,includesMain,processListAvailable);}
    }
    public static boolean exceeds(long pssKiB){return pssKiB>=SOFT_LIMIT_KIB;}
    /** Incomplete same-UID samples cannot authorize another inference batch. */
    public static boolean shouldPause(long pss,int measured,int requested,boolean main,boolean enumerated){
        return !enumerated||!main||requested<=0||measured!=requested||pss<=0||exceeds(pss);
    }
    static boolean canReuse(long now,long at,Set<Integer> before,Set<Integer> current,boolean enumerated){
        return enumerated&&at>=0&&now>=at&&now-at<5000&&!current.isEmpty()&&before.equals(current);
    }
    /** Called only on a worker at inference boundaries; never a background timer. */
    public static synchronized Snapshot sample(Context context){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("PSS sampling must run on worker");
        long now=SystemClock.elapsedRealtime();
        Set<Integer> pids=new LinkedHashSet<>();pids.add(android.os.Process.myPid());
        long total=0;int measured=0;boolean main=false,enumerated=false;
        ActivityManager manager=null;
        try{
            manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> running=manager==null?null:manager.getRunningAppProcesses();
            enumerated=running!=null&&!running.isEmpty();
            if(running!=null)for(ActivityManager.RunningAppProcessInfo process:running)
                if(process.uid==android.os.Process.myUid()&&(process.processName.equals(context.getPackageName())||process.processName.startsWith(context.getPackageName()+":")))pids.add(process.pid);
        }catch(RuntimeException ignored){/* A missing process list cannot reuse a previously verified sample. */}
        if(previous!=null&&canReuse(now,sampledAt,previousPids,pids,enumerated))return previous;
        try{
            int[] ids=new int[pids.size()];int index=0;for(int pid:pids)ids[index++]=pid;
            Debug.MemoryInfo[] values=manager==null?null:manager.getProcessMemoryInfo(ids);
            if(values!=null)for(int n=0;n<Math.min(ids.length,values.length);n++){
                long value=values[n]==null?0:values[n].getTotalPss();
                if(value>0){total+=value;measured++;if(ids[n]==android.os.Process.myPid())main=true;}
            }
        }catch(RuntimeException ignored){/* Unknown processes are never treated as a verified total. */}
        previous=new Snapshot(total,measured,pids.size(),main,enumerated);previousPids=new HashSet<>(pids);sampledAt=now;return previous;
    }
}
