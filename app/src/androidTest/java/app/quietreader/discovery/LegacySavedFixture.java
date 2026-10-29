package app.quietreader.discovery;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.*;
import org.json.*;

/** Seeds historical data for upgrade/preservation checks. Never part of the app APK. */
public final class LegacySavedFixture {
    private LegacySavedFixture(){}
    public static void save(FeedStore store,String eventId,HotCandidate candidate,long at){
        ContentValues value=new ContentValues();
        value.put("id",candidate.id);value.put("event_id",eventId);
        value.put("payload",candidate.json().toString());value.put("saved_at",at);
        store.getWritableDatabase().insertWithOnConflict("saved",null,value,SQLiteDatabase.CONFLICT_REPLACE);
    }
    public static void saveWithInterest(FeedStore store,String eventId,HotCandidate candidate,long at){
        save(store,eventId,candidate,at);
        store.feedback(UUID.randomUUID().toString(),"SAVED","sample","saved:"+candidate.id,at,at+14L*86_400_000,candidate.modelText(),candidate.source);
    }
    public static List<HotCandidate> saved(FeedStore store){
        List<HotCandidate> rows=new ArrayList<>();
        try(Cursor cursor=store.getReadableDatabase().rawQuery("SELECT payload FROM saved ORDER BY saved_at DESC,id",null)){
            while(cursor.moveToNext())rows.add(HotCandidate.fromJson(new JSONObject(cursor.getString(0))));
        }catch(JSONException error){throw new IllegalStateException(error);}
        return rows;
    }
}