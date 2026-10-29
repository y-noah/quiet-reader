package app.quietreader;

/** Monotonic foreground reading evidence. No timers, content capture, or browser history. */
final class ReadingEngagement {
    private long at,total,foregroundTotal,lastTouch=-1;
    private boolean visible,ready,scrolled,emitted,interrupted;
    void reset(long now){at=now;total=0;foregroundTotal=0;lastTouch=-1;ready=false;scrolled=false;emitted=false;interrupted=false;}
    private void accrue(long now){
        if(visible&&ready&&lastTouch>=0)total+=Math.max(0,Math.min(now,lastTouch+30_000)-at);
        if(visible&&ready)foregroundTotal+=Math.max(0,now-at);
        at=now;
    }
    void visible(boolean value,long now){accrue(now);if(ready&&visible&&!value)interrupted=true;visible=value;}
    void ready(boolean value,long now){accrue(now);ready=value;}
    void touch(long now){accrue(now);if(visible&&ready)lastTouch=now;}
    void scroll(long now){accrue(now);if(visible&&ready&&lastTouch>=0&&now-lastTouch<=1500)scrolled=true;}
    boolean take(long now){accrue(now);if(emitted||!scrolled||total<20_000)return false;emitted=true;return true;}
    boolean takeQuickExit(long now,boolean substantial){accrue(now);if(emitted||!substantial||!ready||!visible||interrupted||foregroundTotal<800||foregroundTotal>5000)return false;emitted=true;return true;}
}
