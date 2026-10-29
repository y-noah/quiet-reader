package app.quietreader.discovery;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Passive deadlines only: the collector never schedules a wakeup or auto retry. */
public final class RefreshBackoff {
    private RefreshBackoff(){}
    public static long retryAfter(String value,long now){
        if(value==null)return 0;
        String text=value.trim();
        try{
            if(text.matches("[0-9]+")){
                long seconds=Long.parseLong(text);
                return seconds>(Long.MAX_VALUE-now)/1000?Long.MAX_VALUE:now+seconds*1000;
            }
            return Math.max(now,ZonedDateTime.parse(text,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli());
        }catch(RuntimeException ex){return 0;}
    }
    public static long until(int failures,int httpStatus,long serverDeadline,long now){
        long base=httpStatus==429?60_000:15_000;
        long delay=Math.min(30*60_000L,base*(1L<<Math.min(7,Math.max(0,failures-1))));
        return Math.max(serverDeadline,now>Long.MAX_VALUE-delay?Long.MAX_VALUE:now+delay);
    }
}
