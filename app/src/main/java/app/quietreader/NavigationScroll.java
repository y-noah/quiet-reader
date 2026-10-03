package app.quietreader;

/** Direction hysteresis in dp; tiny scroll jitter must not flash the platform bar. */
final class NavigationScroll {
    private float distance;
    private boolean hidden;
    void reset(){distance=0;hidden=false;}
    boolean update(float position,float delta){
        if(position<=2){reset();return false;}
        if(delta==0)return hidden;
        if(Math.signum(delta)!=Math.signum(distance))distance=0;
        distance+=delta;
        if(!hidden&&distance>=16){hidden=true;distance=0;}
        else if(hidden&&distance<=-8){hidden=false;distance=0;}
        return hidden;
    }
}
