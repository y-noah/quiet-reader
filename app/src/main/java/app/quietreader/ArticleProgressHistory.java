package app.quietreader;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import org.json.*;
import static app.quietreader.ArticleProgressEvidence.*;

/** Worker-only derivative evidence in the feed database; no preference/model writes. */
public final class ArticleProgressHistory {
    static final long MAX_BYTES=8L*1024*1024,TTL_MS=7L*24*60*60_000;
    static final int MAX_ROWS=256,MAX_PAYLOAD=512000;
    private final SQLiteDatabase db;private final long budget;private final int rows;
    ArticleProgressHistory(SQLiteDatabase db){this(db,MAX_BYTES,MAX_ROWS);}
    ArticleProgressHistory(SQLiteDatabase db,long budget,int rows){this.db=db;this.budget=Math.max(1024,Math.min(MAX_BYTES,budget));this.rows=Math.max(1,Math.min(MAX_ROWS,rows));}
    public static void create(SQLiteDatabase db){
        db.execSQL("CREATE TABLE article_progress (key TEXT PRIMARY KEY, format TEXT NOT NULL, identity TEXT NOT NULL, scope TEXT NOT NULL, latest TEXT NOT NULL DEFAULT '', baseline TEXT NOT NULL DEFAULT '', latest_at INTEGER NOT NULL DEFAULT 0, presented_at INTEGER NOT NULL DEFAULT 0, verified_at INTEGER NOT NULL DEFAULT 0, touched_at INTEGER NOT NULL, bytes INTEGER NOT NULL CHECK(bytes>=0))");
        db.execSQL("CREATE INDEX article_progress_time ON article_progress(touched_at DESC,key)");
    }
    private void worker(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Progress storage must run off UI thread");}
    private static void time(long now){if(now<=0||now>System.currentTimeMillis()+60_000)throw new IllegalArgumentException("Invalid progress clock");}
    private static String key(String identity,String scope){if(identity==null||scope==null||!identity.matches("[0-9a-f]{64}")||!scope.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid progress identity");return identity+":"+scope;}
    private static void snapshot(Snapshot value,long now){if(value==null||value.acquiredAt>now||(value.discussion==null?value.modifiedAt<946684800000L:value.modifiedAt<0)||value.modifiedAt>value.acquiredAt||value.body.size()<100||value.body.size()>MAX_SHINGLES||value.marked.size()>MAX_SHINGLES||!value.body.containsAll(value.marked))throw new IllegalArgumentException("Invalid body evidence");key(value.identity,value.scope);}
    private static final class State {String latest="",baseline="";long latestAt,presentedAt,verifiedAt,touchedAt;}
    private State load(String key,long now){State state=new State();try(Cursor c=db.rawQuery("SELECT format,latest,baseline,latest_at,presented_at,verified_at,touched_at FROM article_progress WHERE key=?",new String[]{key})){
        if(!c.moveToFirst()||!VERSION.equals(c.getString(0))||c.getLong(3)>now||c.getLong(4)>now||c.getLong(6)>now)return state;
        state.latest=c.getString(1);state.baseline=c.getString(2);state.latestAt=c.getLong(3);state.presentedAt=c.getLong(4);state.verifiedAt=c.getLong(5);state.touchedAt=c.getLong(6);return state;
    }}
    static Snapshot decode(String raw,long now){
        if(raw.isEmpty()||!ArticleUpdateEvidence.boundedJson(raw,MAX_PAYLOAD,6))return null;
        try{JSONTokener parser=new JSONTokener(raw);Object value=parser.nextValue();return value instanceof JSONObject&&parser.nextClean()==0?Snapshot.read((JSONObject)value,now):null;}catch(JSONException malformed){return null;}
    }
    private void save(String identity,String scope,State state){
        ContentValues value=new ContentValues();value.put("key",key(identity,scope));value.put("format",VERSION);value.put("identity",identity);value.put("scope",scope);value.put("latest",state.latest);value.put("baseline",state.baseline);value.put("latest_at",state.latestAt);value.put("presented_at",state.presentedAt);value.put("verified_at",state.verifiedAt);value.put("touched_at",state.touchedAt);value.put("bytes",state.latest.length()+state.baseline.length());db.insertOrThrow("article_progress",null,value);
    }
    private void replace(String identity,String scope,State state){String key=key(identity,scope);db.delete("article_progress","key=?",new String[]{key});save(identity,scope,state);}
    /** Called on a foreground action/write, never by a timer. Payloads are ASCII hashes. */
    void maintain(long now){worker();time(now);db.beginTransaction();try{prune(now);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    private void prune(long now){
        db.delete("article_progress","touched_at<? OR touched_at>? OR latest_at>? OR presented_at>?",new String[]{""+Math.max(0,now-TTL_MS),""+now,""+now,""+now});
        long bytes=0;int count=0;java.util.List<String> expired=new java.util.ArrayList<>();
        try(Cursor c=db.rawQuery("SELECT key,bytes FROM article_progress ORDER BY touched_at DESC,key",null)){while(c.moveToNext()){count++;long size=c.getLong(1);if(count>rows||size<0||size>budget-bytes)expired.add(c.getString(0));else bytes+=size;}}
        for(String key:expired)db.delete("article_progress","key=?",new String[]{key});
    }
    /** Acquisition alone never establishes a seen/read baseline. */
    boolean observe(Snapshot current,long now){worker();time(now);snapshot(current,now);String key=key(current.identity,current.scope);db.beginTransaction();try{
        State state=load(key,now);if(current.acquiredAt<=state.latestAt){db.setTransactionSuccessful();return false;}
        state.latest=current.json().toString();state.latestAt=current.acquiredAt;
        if(state.baseline.isEmpty()&&state.presentedAt>0&&current.acquiredAt<=state.presentedAt)state.baseline=state.latest;
        state.verifiedAt=verifiedProgress(decode(state.baseline,now),current,state.presentedAt,now);state.touchedAt=Math.max(current.acquiredAt,state.presentedAt);
        replace(current.identity,current.scope,state);prune(now);db.setTransactionSuccessful();return true;
    }catch(JSONException malformed){throw new IllegalStateException(malformed);}finally{db.endTransaction();}}
    /** Only an actual accepted-snapshot exposure may call this method. */
    boolean presented(String identity,String scope,long at,long now){worker();time(now);if(at<=0||at>now)throw new IllegalArgumentException("Invalid presentation time");String key=key(identity,scope);db.beginTransaction();try{
        State state=load(key,now);if(at<=state.presentedAt){db.setTransactionSuccessful();return false;}
        state.presentedAt=at;if(state.latestAt>0&&state.latestAt<=at)state.baseline=state.latest;
        state.verifiedAt=state.latestAt<=at?0:verifiedProgress(decode(state.baseline,now),decode(state.latest,now),at,now);state.touchedAt=Math.max(at,state.latestAt);
        replace(identity,scope,state);prune(now);db.setTransactionSuccessful();return true;
    }finally{db.endTransaction();}}
    /** Use the frozen body actually read, even if a newer prefetch already exists. */
    boolean consumed(Snapshot actual,long at,long now){worker();time(now);snapshot(actual,now);if(at<actual.acquiredAt||at>now)throw new IllegalArgumentException("Invalid consumption time");String key=key(actual.identity,actual.scope);db.beginTransaction();try{
        State state=load(key,now);if(at<state.presentedAt){db.setTransactionSuccessful();return false;}
        state.baseline=actual.json().toString();state.presentedAt=at;
        if(actual.acquiredAt>=state.latestAt){state.latest=state.baseline;state.latestAt=actual.acquiredAt;}
        state.verifiedAt=state.latestAt<=at?0:verifiedProgress(actual,decode(state.latest,now),at,now);state.touchedAt=Math.max(at,state.latestAt);
        replace(actual.identity,actual.scope,state);prune(now);db.setTransactionSuccessful();return true;
    }catch(JSONException malformed){throw new IllegalStateException(malformed);}finally{db.endTransaction();}}
    /** No JSON/hash work on the refresh lookup; writes computed the proof atomically. */
    java.util.Set<String> potential(java.util.Collection<String> identities,long now){
        worker();time(now);java.util.Set<String> unique=new java.util.LinkedHashSet<>(identities);
        if(unique.size()>128)throw new IllegalArgumentException("Too many progress candidates");
        if(unique.isEmpty())return java.util.Collections.emptySet();
        java.util.List<String> args=new java.util.ArrayList<>(java.util.List.of(VERSION,""+now,""+Math.max(0,now-TTL_MS),""+now));
        for(String identity:unique){if(identity==null||!identity.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid progress candidate");args.add(identity);}
        String placeholders=String.join(",",java.util.Collections.nCopies(unique.size(),"?"));java.util.Set<String> found=new java.util.HashSet<>();
        try(Cursor c=db.rawQuery("SELECT DISTINCT identity FROM article_progress WHERE format=? AND presented_at>0 AND verified_at>presented_at AND verified_at<=latest_at AND latest_at<=? AND touched_at BETWEEN ? AND ? AND identity IN ("+placeholders+")",args.toArray(new String[0]))){while(c.moveToNext())found.add(c.getString(0));}
        return java.util.Collections.unmodifiableSet(found);
    }
    /** Exact scope and whole-event exposure still have to pass after the coarse prefilter. */
    long progress(String identity,String scope,long lastPresentation,long now){worker();time(now);String key=key(identity,scope);try(Cursor c=db.rawQuery("SELECT format,presented_at,verified_at,latest_at,touched_at FROM article_progress WHERE key=?",new String[]{key})){
        if(!c.moveToFirst()||!VERSION.equals(c.getString(0))||lastPresentation<=0)return 0;long shown=c.getLong(1),proof=c.getLong(2),latest=c.getLong(3),touched=c.getLong(4);
        return shown>0&&proof>Math.max(shown,lastPresentation)&&proof<=latest&&latest<=now&&touched<=now&&touched>=Math.max(0,now-TTL_MS)?proof:0;
    }}
}
