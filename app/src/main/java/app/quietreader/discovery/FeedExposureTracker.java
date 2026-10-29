package app.quietreader.discovery;

import java.util.*;

/** Continuous, foreground visibility only. Acquisition and ranking are never exposure. */
public final class FeedExposureTracker {
    public static final long DWELL_MS=1200;
    public static final double MIN_VISIBLE=.5;
    private static final int MAX_VISIBLE=64;
    public static final int MAX_REPORTED=5000;
    private final Map<String,Long> pending=new LinkedHashMap<>();
    private final Set<String> reported=new HashSet<>();
    private long last=-1;

    /** Monotonic elapsed time; a caller accounts for toolbar/viewport occlusion. */
    public void viewport(Map<String,Double> visible,long elapsed){
        if(!advance(elapsed))return;
        if(reported.size()>=MAX_REPORTED){pending.clear();return;}
        Set<String> eligible=new LinkedHashSet<>();
        for(Map.Entry<String,Double> entry:visible.entrySet()){
            String id=entry.getKey();Double fraction=entry.getValue();
            if(id==null||id.isEmpty()||id.length()>256||fraction==null||!Double.isFinite(fraction)||fraction<MIN_VISIBLE||fraction>1||reported.contains(id))continue;
            eligible.add(id);if(eligible.size()==MAX_VISIBLE)break;
        }
        pending.keySet().retainAll(eligible);
        for(String id:eligible)pending.putIfAbsent(id,elapsed);
    }
    /** One deadline, or -1 when no callback is needed. Never a periodic wakeup. */
    public long dueAt(){long due=Long.MAX_VALUE;for(long since:pending.values())due=Math.min(due,since>Long.MAX_VALUE-DWELL_MS?Long.MAX_VALUE:since+DWELL_MS);return pending.isEmpty()?-1:due;}
    public Set<String> matured(long elapsed){
        if(!advance(elapsed))return Collections.emptySet();
        Set<String> result=new LinkedHashSet<>();
        for(Iterator<Map.Entry<String,Long>> it=pending.entrySet().iterator();it.hasNext();){Map.Entry<String,Long> entry=it.next();if(elapsed-entry.getValue()>=DWELL_MS){if(reported.size()<MAX_REPORTED){reported.add(entry.getKey());result.add(entry.getKey());}it.remove();}}
        return Collections.unmodifiableSet(result);
    }
    /** Pause breaks continuity; returning does not retrospectively count hidden time. */
    public void pause(){pending.clear();last=-1;}
    /** Only an explicit new presentation may record a second exposure. */
    public void newPresentation(){pause();reported.clear();}
    private boolean advance(long elapsed){if(elapsed<0||last>elapsed){pause();if(elapsed>=0)last=elapsed;return false;}last=elapsed;return true;}
}
