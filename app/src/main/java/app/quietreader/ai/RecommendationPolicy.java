package app.quietreader.ai;

import android.app.ActivityManager;
import android.content.*;
import android.os.*;
import java.util.function.Consumer;

/** Public system signals only. No polling service, hidden temperature API or wakelock. */
public final class RecommendationPolicy {
    private RecommendationPolicy(){}
    public static final long MEMORY_RECOVERY_MS=30_000;
    private static volatile long memoryResumeAt;
    /** Process-wide admission guard; no timer or automatic restart on recovery. */
    public static boolean memoryPressure(int level){
        if(level<android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW||level==android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)return false;
        memoryResumeAt=SystemClock.elapsedRealtime()+MEMORY_RECOVERY_MS;return true;
    }
    /** Scoped to a live foreground pipeline. Recovery never starts work itself. */
    public static final class Watch implements AutoCloseable {
        private final Context context;
        private final Consumer<String> restricted;
        private final PowerManager power;
        private boolean closed,registered,thermalRegistered;
        private PowerManager.OnThermalStatusChangedListener thermal;
        private final BroadcastReceiver receiver=new BroadcastReceiver(){
            @Override public void onReceive(Context c,Intent intent){check();}
        };
        public Watch(Context context,Consumer<String> restricted){
            this.context=context.getApplicationContext();this.restricted=restricted;
            power=(PowerManager)this.context.getSystemService(Context.POWER_SERVICE);
        }
        public void start(){
            if(closed||registered)return;
            IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_BATTERY_CHANGED);filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);else context.registerReceiver(receiver,filter);
            registered=true;
            if(Build.VERSION.SDK_INT>=29&&power!=null){thermal=status->check();power.addThermalStatusListener(context.getMainExecutor(),thermal);thermalRegistered=true;}
            check();
        }
        private void check(){if(closed)return;String reason=restriction(context);if(!reason.isEmpty())restricted.accept(reason);}
        @Override public void close(){if(closed)return;closed=true;if(registered){context.unregisterReceiver(receiver);registered=false;}if(thermalRegistered&&Build.VERSION.SDK_INT>=29){power.removeThermalStatusListener(thermal);thermalRegistered=false;}}
    }
    public static String restriction(Context context){
        if(SystemClock.elapsedRealtime()<memoryResumeAt)return "内存恢复中，已暂停整理";
        PowerManager power=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if(power!=null){if(!power.isInteractive())return "屏幕关闭，已暂停整理";if(power.isPowerSaveMode())return "省电模式下暂缓整理";if(Build.VERSION.SDK_INT>=29&&power.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_MODERATE)return "手机温度升高，已暂停整理";}
        Intent battery=context.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery!=null){int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);if(level>=0&&scale>0&&100L*level/scale<20)return "电量较低，已暂停整理";}
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();if(manager!=null){manager.getMemoryInfo(memory);if(memory.lowMemory)return "内存紧张，已暂停整理";}
        return "";
    }
}
