package app.quietreader;

import android.app.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import org.json.*;

/** Read-only aggregate evidence. Never reads cookies, account identifiers or feedback text. */
final class ReleaseFeedSourceAudit {
    private final Instrumentation test;
    ReleaseFeedSourceAudit(Instrumentation test){this.test=test;}
    void run(){JSONObject result=new JSONObject();int failures=0;
        try{
            if(!test.getTargetContext().getPackageName().equals("app.quietreader")||!Build.MODEL.equals("PHY110"))throw new IllegalStateException("authorized ordinary phone only");
            try(SQLiteDatabase db=SQLiteDatabase.openDatabase(test.getTargetContext().getDatabasePath("personalized-hot-topics-v1.db").getPath(),null,SQLiteDatabase.OPEN_READONLY)){
                long active=0;try(Cursor c=db.rawQuery("SELECT value FROM meta WHERE key='active_snapshot'",null)){if(c.moveToFirst())active=c.getLong(0);}result.put("snapshot",active);
                try(Cursor c=db.rawQuery("SELECT mode,filter,created_at FROM snapshots WHERE id=?",new String[]{""+active})){if(c.moveToFirst())result.put("mode",c.getString(0)).put("filter",c.getString(1)).put("createdAt",c.getLong(2));}
                JSONArray sources=new JSONArray();try(Cursor c=db.rawQuery("SELECT s.id,s.enabled,s.status,s.success_at,s.failures,s.retry_at,COUNT(c.id),SUM(CASE WHEN p.at IS NULL THEN 1 ELSE 0 END) FROM sources s LEFT JOIN candidates c ON c.source=s.id AND c.active=1 LEFT JOIN presentations p ON p.candidate_id=c.id GROUP BY s.id ORDER BY s.id",null)){
                    while(c.moveToNext())sources.put(new JSONObject().put("source",c.getString(0)).put("enabled",c.getInt(1)).put("status",c.getString(2)).put("successAt",c.getLong(3)).put("failures",c.getInt(4)).put("retryAt",c.getLong(5)).put("activeCandidates",c.getInt(6)).put("neverPresented",c.getInt(6)==0?0:c.getInt(7)));}result.put("sources",sources);
                JSONObject top=new JSONObject(),pool=new JSONObject();JSONArray first=new JSONArray();try(Cursor c=db.rawQuery("SELECT position,payload FROM snapshot_rows WHERE snapshot=? ORDER BY position",new String[]{""+active})){while(c.moveToNext()){JSONObject row=new JSONObject(c.getString(1));String source=row.optString("source");pool.put(source,pool.optInt(source)+1);if(c.getInt(0)<15){top.put(source,top.optInt(source)+1);first.put(new JSONObject().put("position",c.getInt(0)).put("source",source).put("rank",row.optInt("rank")).put("interestBand",row.optInt("interestBand",-1)));}}}result.put("first15",top).put("pool",pool).put("rows",first);
                JSONArray feedback=new JSONArray();try(Cursor c=db.rawQuery("SELECT action,scope,source,COUNT(*) FROM feedback WHERE undo_of IS NULL GROUP BY action,scope,source",null)){while(c.moveToNext())feedback.put(new JSONObject().put("action",c.getString(0)).put("scope",c.getString(1)).put("source",c.getString(2)).put("count",c.getInt(3)));}result.put("feedbackCounts",feedback);
            }
        }catch(Throwable error){failures++;try{result.put("error",error.toString());}catch(JSONException ignored){}}
        Bundle out=new Bundle();out.putString("stream",result.toString()+"\nfailures="+failures+"\n");test.finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);
    }
}
