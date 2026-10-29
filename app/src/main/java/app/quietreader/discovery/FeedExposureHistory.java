package app.quietreader.discovery;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;

/** Worker-owned, bounded presentation facts; never writes preference/model tables. */
final class FeedExposureHistory {
    static final long TTL_MS=7L*24*60*60_000;
    static final int MAX_KEYS=10000,MAX_VISIBLE=64;
    private FeedExposureHistory(){}
    private static boolean validTime(long at){return at>0&&at<=System.currentTimeMillis()+60_000;}
    static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE feed_exposure (key TEXT PRIMARY KEY, shown_at INTEGER NOT NULL DEFAULT 0, read_at INTEGER NOT NULL DEFAULT 0, touched_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX feed_exposure_time ON feed_exposure(touched_at DESC,key)");
    }
    private static void prune(SQLiteDatabase db,long now,boolean reading){
        if(reading){db.execSQL("UPDATE feed_exposure SET shown_at=0 WHERE shown_at>?",new Object[]{now});db.execSQL("UPDATE feed_exposure SET read_at=0 WHERE read_at>?",new Object[]{now});db.execSQL("UPDATE feed_exposure SET touched_at=MAX(shown_at,read_at) WHERE touched_at>?",new Object[]{now});}
        db.delete("feed_exposure","touched_at<? OR touched_at=0",new String[]{""+Math.max(0,now-TTL_MS)});
        db.execSQL("DELETE FROM feed_exposure WHERE key IN (SELECT key FROM feed_exposure ORDER BY touched_at DESC,key LIMIT -1 OFFSET "+MAX_KEYS+")");
    }
    private static void write(SQLiteDatabase db,String key,long at,boolean read){
        db.execSQL("INSERT OR IGNORE INTO feed_exposure(key,touched_at) VALUES (?,?)",new Object[]{key,at});
        db.execSQL("UPDATE feed_exposure SET "+(read?"read_at":"shown_at")+"=MAX("+(read?"read_at":"shown_at")+",?),touched_at=MAX(touched_at,?) WHERE key=?",new Object[]{at,at,key});
    }
    /** Resolve only identities from the actual immutable displayed snapshot. */
    static int exposed(SQLiteDatabase db,long snapshot,Collection<String> events,long at){
        return exposed(db,snapshot,events,at,null);
    }
    static int exposed(SQLiteDatabase db,long snapshot,Collection<String> events,long at,Map<String,HotCandidate> members){
        if(events.size()>MAX_VISIBLE||!validTime(at))throw new IllegalArgumentException("Invalid exposure batch");
        LinkedHashSet<String> unique=new LinkedHashSet<>(events);for(String id:unique)if(id==null||id.isEmpty()||id.length()>256)throw new IllegalArgumentException("Invalid event identity");
        if(unique.isEmpty()||snapshot<=0)return 0;
        int accepted=0;db.beginTransaction();try{
            for(String event:unique)try(Cursor row=db.rawQuery("SELECT payload,reason FROM snapshot_rows WHERE snapshot=? AND event_id=?",new String[]{""+snapshot,event})){
                if(!row.moveToFirst())continue;
                FeedStore.Row captured=FeedStore.Row.read(event,row.getString(0),row.getString(1));
                write(db,"event:"+event,at,false);for(HotCandidate member:captured.entries){write(db,"member:"+member.id,at,false);if(members!=null)members.put(member.id,member);}accepted++;
            }
            // A delayed older callback must not delete newer committed history.
            prune(db,at,false);db.setTransactionSuccessful();return accepted;
        }finally{db.endTransaction();}
    }
    static void consumed(SQLiteDatabase db,HotCandidate candidate,long at){
        if(!validTime(at))throw new IllegalArgumentException("Invalid read time");
        db.beginTransaction();try{write(db,"member:"+candidate.id,at,true);prune(db,at,false);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    /** An event inherits only an accepted event key or one of its exact source members. */
    static Map<String,FeedRefreshOrder.Recent> recent(SQLiteDatabase db,Collection<FeedStore.Row> rows,long now){
        if(now<0)throw new IllegalArgumentException("Invalid current time");
        Map<String,long[]> facts=new HashMap<>();Map<String,FeedRefreshOrder.Recent> result=new HashMap<>();
        db.beginTransaction();try{
            prune(db,now,true);
            try(Cursor cursor=db.rawQuery("SELECT key,shown_at,read_at FROM feed_exposure",null)){while(cursor.moveToNext())facts.put(cursor.getString(0),new long[]{cursor.getLong(1),cursor.getLong(2)});}
            for(FeedStore.Row row:rows){long shown=0,read=0;List<String> keys=new ArrayList<>();keys.add("event:"+row.eventId);for(HotCandidate member:row.entries)keys.add("member:"+member.id);
                for(String key:keys){long[] value=facts.get(key);if(value!=null){if(value[0]<=now)shown=Math.max(shown,value[0]);if(value[1]<=now)read=Math.max(read,value[1]);}}
                if(shown>0||read>0)result.put(row.eventId,new FeedRefreshOrder.Recent(shown,read,0));
            }
            db.setTransactionSuccessful();return Collections.unmodifiableMap(result);
        }finally{db.endTransaction();}
    }
}
