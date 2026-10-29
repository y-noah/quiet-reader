package app.quietreader;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;
import org.json.*;

/** Test APK only. Fixed isolated target; files disappear when that target is uninstalled. */
final class DetachedProbeRecorder {
    static final String PACKAGE="app.quietreader.flowprobe";
    private static final int MAX_SHELL_BYTES=512*1024;
    private static final Pattern PID=Pattern.compile("\\*\\* MEMINFO in pid (\\d+) ");
    private final Context context;
    private final String id,phase;
    private final JSONObject measurement;
    private final int watcherPid,timeoutSeconds,intervalSeconds;
    private final long started=SystemClock.elapsedRealtime(),wallStarted=System.currentTimeMillis();
    private final AtomicFile state;
    private final File samplesFile;
    private final File stallsFile;
    private final android.os.Handler uiPulse=new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile long lastUiPulse=SystemClock.elapsedRealtime();
    private int stallSnapshots;
    private final Runnable heartbeat=new Runnable(){public void run(){lastUiPulse=SystemClock.elapsedRealtime();if(!terminal.get())uiPulse.postDelayed(this,250);}};
    private final AtomicBoolean terminal=new AtomicBoolean();
    private final AtomicInteger samples=new AtomicInteger();
    private final Object pipeLock=new Object(),rawLock=new Object();
    private volatile UiAutomation automation;
    private ParcelFileDescriptor activePipe;
    private BufferedWriter raw;
    private ScheduledExecutorService executor;
    private BroadcastReceiver cancelReceiver;
    private NotificationManager notifications;
    private static final int NOTIFICATION_ID=7319;
    private final boolean cancelNotification;

    static boolean validId(String id){return id!=null&&id.matches("[a-z0-9][a-z0-9-]{0,39}");}
    DetachedProbeRecorder(Context context,Bundle args,String phase)throws Exception{
        if(!PACKAGE.equals(context.getPackageName()))throw new IllegalArgumentException("Detached runs require the fixed isolated package");
        id=args.getString("detachedRun","");
        if(!validId(id))throw new IllegalArgumentException("Invalid detached run identifier");
        this.phase=phase.isEmpty()?"models":phase;
        if(!Arrays.asList("models","reading-core","control-idle","energy-setup","energy-replay","energy-background").contains(this.phase))throw new IllegalArgumentException("Detached phase is not enabled");
        watcherPid=Integer.parseInt(args.getString("watcherPid","0"));
        timeoutSeconds=Integer.parseInt(args.getString("runTimeoutSeconds","300"));
        intervalSeconds=Integer.parseInt(args.getString("sampleIntervalSeconds","5"));
        if(watcherPid<=0||timeoutSeconds<1||timeoutSeconds>3600||intervalSeconds<2||intervalSeconds>30)throw new IllegalArgumentException("Invalid detached limits");
        if(this.phase.startsWith("energy-")){
            String stages=args.getString("stages",""),cache=args.getString("cache",""),fixture=args.getString("fixtureSha256","");
            int duration=Integer.parseInt(args.getString("replaySeconds","1800"));
            if(!Arrays.asList("NONE","EMBEDDING","DUAL").contains(stages)||!Arrays.asList("cold","warm").contains(cache)||!fixture.matches("[a-f0-9]{64}")||duration<60||duration>3000)throw new IllegalArgumentException("Invalid measurement identity");
            if((this.phase.equals("energy-replay")||this.phase.equals("energy-background"))&&timeoutSeconds<duration+240)throw new IllegalArgumentException("Watchdog must include preparation margin");
            String prefetch=args.getString("activePrefetch","false");if(!prefetch.equals("true")&&!prefetch.equals("false"))throw new IllegalArgumentException("Invalid prefetch identity");
            String network=args.getString("networkProfile","uncontrolled");
            if(!Arrays.asList("uncontrolled","offline-cache").contains(network)||this.phase.equals("energy-replay")&&duration>300&&!network.equals("offline-cache"))throw new IllegalArgumentException("Long replay requires offline-cache profile");
            measurement=new JSONObject().put("networkProfile",network).put("stages",stages).put("cache",cache).put("fixtureSha256",fixture).put("replaySeconds",duration).put("activePrefetch",prefetch.equals("true"));
        }else measurement=null;
        this.context=context;
        cancelNotification="true".equals(args.getString("cancelNotification","false"));
        File directory=context.getExternalFilesDir(null);
        if(directory==null)throw new IOException("App-specific result directory unavailable");
        File statusFile=new File(directory,"device-run-"+id+"-state.json");
        samplesFile=new File(directory,"device-run-"+id+"-samples.ndjson");
        stallsFile=new File(directory,"device-run-"+id+"-stalls.ndjson");
        // Reserve once, including AtomicFile recovery files and partial sample evidence.
        if(samplesFile.exists()||new File(statusFile.getPath()+".bak").exists()||new File(statusFile.getPath()+".new").exists()
            ||!statusFile.createNewFile())throw new IOException("Run identifier already has evidence");
        if(!samplesFile.createNewFile()||!stallsFile.createNewFile())throw new IOException("Run identifier already has samples/stalls");
        state=new AtomicFile(statusFile);
        try{
            raw=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(samplesFile,false),StandardCharsets.UTF_8));
            writeState(base("RUNNING").put("samples",0));
        }catch(Exception error){if(raw!=null)try{raw.close();}catch(IOException ignored){}throw error;}
    }
    boolean isTerminal(){return terminal.get();}
    private JSONObject base(String status)throws JSONException{
        return new JSONObject().put("schema",1).put("runId",id).put("phase",phase).put("package",PACKAGE).put("measurement",measurement==null?JSONObject.NULL:measurement)
            .put("state",status).put("watcherPid",watcherPid).put("appPid",android.os.Process.myPid())
            .put("startedAtMillis",wallStarted).put("elapsedMs",SystemClock.elapsedRealtime()-started)
            .put("timeoutSeconds",timeoutSeconds).put("sampleIntervalSeconds",intervalSeconds)
            .put("samplesFile",samplesFile.getName()).put("stallsFile",stallsFile.getName()).put("wholeAppPeakVerified",false).put("energyComparisonVerified",false);
    }
    private void writeState(JSONObject value)throws IOException{
        FileOutputStream output=null;
        try{output=state.startWrite();output.write((value.toString()+"\n").getBytes(StandardCharsets.UTF_8));state.finishWrite(output);}
        catch(IOException error){if(output!=null)state.failWrite(output);throw error;}
    }
    boolean start(Instrumentation test)throws Exception{
        if(measurement!=null){
            byte[] bytes;try(InputStream in=test.getContext().getAssets().open("energy-replay-100.json")){bytes=in.readAllBytes();}
            StringBuilder digest=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(bytes))digest.append(String.format(Locale.ROOT,"%02x",b&255));
            if(!digest.toString().equals(measurement.getString("fixtureSha256")))throw new IOException("Installed replay fixture differs from requested identity");
        }
        if(cancelNotification)installCancelNotification();
        executor=Executors.newScheduledThreadPool(2);
        executor.schedule(()->abort("TIMED_OUT"),Math.max(0,timeoutSeconds*1000L-(SystemClock.elapsedRealtime()-started)),TimeUnit.MILLISECONDS);
        automation=test.getUiAutomation();
        uiPulse.post(heartbeat);
        executor.scheduleWithFixedDelay(this::observeUiStall,500,500,TimeUnit.MILLISECONDS);
        sample();
        if(!terminal.get())try{executor.scheduleWithFixedDelay(this::sample,intervalSeconds,intervalSeconds,TimeUnit.SECONDS);}
        catch(RejectedExecutionException error){if(!terminal.get())throw error;}
        return !terminal.get();
    }
    private void observeUiStall(){
        long lag=SystemClock.elapsedRealtime()-lastUiPulse;
        if(terminal.get()||lag<1500||stallSnapshots>=16)return;
        try{
            org.json.JSONArray threads=new org.json.JSONArray();
            for(Map.Entry<Thread,StackTraceElement[]> entry:Thread.getAllStackTraces().entrySet()){
                if(threads.length()>=48)break;org.json.JSONArray stack=new org.json.JSONArray();int n=0;
                for(StackTraceElement frame:entry.getValue()){if(n++>=64)break;stack.put(frame.toString());}
                threads.put(new JSONObject().put("name",entry.getKey().getName()).put("state",entry.getKey().getState().name()).put("stack",stack));
            }
            JSONObject row=new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-started).put("uiHeartbeatLagMs",lag).put("threads",threads);
            try(FileOutputStream out=new FileOutputStream(stallsFile,true)){out.write((row.toString()+"\n").getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
            stallSnapshots++;
        }catch(Exception ignored){}
    }
    private void installCancelNotification(){
        notifications=context.getSystemService(NotificationManager.class);
        if(notifications==null||!notifications.areNotificationsEnabled())throw new IllegalStateException("Test stop notification is unavailable");
        String channel="isolated-device-test",action=PACKAGE+".STOP_TEST."+id;
        notifications.createNotificationChannel(new NotificationChannel(channel,"临时测试控制",NotificationManager.IMPORTANCE_LOW));
        if(notifications.getNotificationChannel(channel).getImportance()==NotificationManager.IMPORTANCE_NONE)throw new IllegalStateException("Test stop channel is disabled");
        cancelReceiver=new BroadcastReceiver(){@Override public void onReceive(Context ignored,Intent intent){
            if(action.equals(intent.getAction()))new Thread(()->abort("CANCELLED"),"probe-user-cancel").start();
        }};
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(cancelReceiver,new IntentFilter(action),Context.RECEIVER_NOT_EXPORTED);
        else context.registerReceiver(cancelReceiver,new IntentFilter(action));
        PendingIntent stop=PendingIntent.getBroadcast(context,0,new Intent(action).setPackage(PACKAGE),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notice=new Notification.Builder(context,channel).setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentTitle("临时测试正在运行").setContentText("可拔线；点这里停止测试。编号："+id)
            .setOngoing(true).setContentIntent(stop).addAction(new Notification.Action.Builder(null,"停止测试",stop).build()).build();
        notifications.notify(NOTIFICATION_ID,notice);
    }
    private void removeCancelNotification(){
        if(notifications!=null)notifications.cancel(NOTIFICATION_ID);
        if(cancelReceiver!=null){try{context.unregisterReceiver(cancelReceiver);}catch(IllegalArgumentException ignored){}cancelReceiver=null;}
    }
    private JSONObject shell(String command){
        long began=SystemClock.elapsedRealtime();ByteArrayOutputStream data=new ByteArrayOutputStream();ParcelFileDescriptor pipe=null;boolean truncated=false;
        JSONObject result=new JSONObject();
        try{
            if(terminal.get())throw new InterruptedIOException("Recorder is closing");
            if(Build.VERSION.SDK_INT<31)throw new IllegalStateException("Detached shell sampling requires API31");
            ParcelFileDescriptor[] channels=automation.executeShellCommandRw("sh");
            pipe=channels[0];
            try(OutputStream input=new ParcelFileDescriptor.AutoCloseOutputStream(channels[1])){
                input.write((command+" 2>&1; printf '\\nQR_DEVICE_PROBE_EXIT=%s\\n' \"$?\"\n").getBytes(StandardCharsets.UTF_8));
            }
            synchronized(pipeLock){if(terminal.get())throw new InterruptedIOException("Recorder is closing");activePipe=pipe;}
            try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(pipe)){
                byte[] buffer=new byte[8192];int count;
                while((count=input.read(buffer))!=-1){int remaining=MAX_SHELL_BYTES-data.size();if(count>remaining){if(remaining>0)data.write(buffer,0,remaining);truncated=true;break;}data.write(buffer,0,count);}
            }
            String output=data.toString("UTF-8");Matcher exit=Pattern.compile("\\nQR_DEVICE_PROBE_EXIT=(-?\\d+)\\s*$").matcher(output);
            if(!truncated&&exit.find()){result.put("exit",Integer.parseInt(exit.group(1)));output=output.substring(0,exit.start());}else result.put("exit",JSONObject.NULL);
            result.put("stdout",output).put("truncated",truncated);
        }catch(Exception error){try{result.put("exit",JSONObject.NULL).put("stdout",data.toString("UTF-8")).put("error",error.getClass().getSimpleName());}catch(Exception ignored){}}
        finally{
            synchronized(pipeLock){if(activePipe==pipe)activePipe=null;}
            if(pipe!=null)try{pipe.close();}catch(IOException ignored){}
            try{result.put("queryMs",SystemClock.elapsedRealtime()-began);}catch(JSONException ignored){}
        }
        return result;
    }
    private void sample(){
        if(terminal.get())return;
        try{
            JSONObject row=new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-started);
            row.put("processesBefore",shell("dumpsys activity processes "+PACKAGE));
            JSONObject memory=shell("dumpsys meminfo --package "+PACKAGE);row.put("meminfo",memory);
            row.put("processesAfter",shell("dumpsys activity processes "+PACKAGE));
            TreeSet<Integer> pids=new TreeSet<>();Matcher pid=PID.matcher(memory.optString("stdout"));while(pid.find())pids.add(Integer.parseInt(pid.group(1)));
            if(!pids.isEmpty()){
                StringBuilder stat=new StringBuilder("cat"),status=new StringBuilder("cat");for(int value:pids){stat.append(" /proc/").append(value).append("/stat");status.append(" /proc/").append(value).append("/status");}
                row.put("procStat",shell(stat.toString())).put("procStatus",shell(status.toString()));
            }
            if(samples.get()%3==0)row.put("battery",shell("dumpsys battery")).put("thermal",shell("dumpsys thermalservice")).put("dataDisk",shell("df -k /data"));
            if(samples.get()==0)row.put("clockTicks",shell("getconf CLK_TCK"));
            row.put("finishedElapsedMs",SystemClock.elapsedRealtime()-started);
            synchronized(rawLock){if(terminal.get())return;raw.write(row.toString());raw.newLine();raw.flush();samples.incrementAndGet();}
        }catch(Exception error){
            // Missing data is evidence, never a fake zero or successful resource result.
            try{synchronized(rawLock){if(!terminal.get()){raw.write(new JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-started).put("error",error.getClass().getSimpleName()).toString());raw.newLine();raw.flush();}}}catch(Exception ignored){}
        }
    }
    private void stopSampler(){
        uiPulse.removeCallbacks(heartbeat);
        synchronized(pipeLock){if(activePipe!=null)try{activePipe.close();}catch(IOException ignored){}activePipe=null;}
        if(executor!=null)executor.shutdownNow();
        removeCancelNotification();
        synchronized(rawLock){if(raw!=null)try{raw.close();}catch(IOException ignored){}raw=null;}
    }
    boolean finish(int code,Bundle results){
        if(!terminal.compareAndSet(false,true))return false;
        try{stopSampler();}catch(Exception error){
            if(results!=null)results.putString("stream",results.getString("stream","")+"\nSAMPLER_CLEANUP_FAILED "+error.getClass().getSimpleName()+"\nchecks=0 failures=1\n");
        }
        try{
            String stream=results==null?"":results.getString("stream","");
            Matcher match=Pattern.compile("^checks=(\\d+) failures=(\\d+)\\s*$",Pattern.MULTILINE).matcher(stream);
            int checks=-1,failures=-1,count=0;while(match.find()){checks=Integer.parseInt(match.group(1));failures=Integer.parseInt(match.group(2));count++;}
            boolean passed=code==Activity.RESULT_OK&&count==1&&checks>0&&failures==0;
            writeState(base("COMPLETE").put("resultCode",code).put("checks",checks).put("failures",failures).put("passed",passed).put("samples",samples.get()).put("stream",stream));
        }catch(Exception error){
            if(results!=null)results.putString("stream",results.getString("stream","")+"\nDETACHED_RESULT_WRITE_FAILED\nchecks=0 failures=1\n");
        }
        return true;
    }
    private void abort(String status){
        if(!terminal.compareAndSet(false,true))return;
        Thread fallback=new Thread(()->{SystemClock.sleep(2000);android.os.Process.killProcess(android.os.Process.myPid());},"probe-stop-fallback");fallback.setDaemon(true);fallback.start();
        stopSampler();
        try{writeState(base(status).put("passed",false).put("samples",samples.get()).put("stopMethod","isolated-package-force-stop-requested; self-kill fallback"));}catch(Exception ignored){}
        try{if(automation!=null){ParcelFileDescriptor pipe=automation.executeShellCommand("am force-stop "+PACKAGE);pipe.close();}}catch(Exception ignored){}
    }
}
