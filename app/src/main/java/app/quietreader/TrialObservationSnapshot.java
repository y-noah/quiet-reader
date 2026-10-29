package app.quietreader;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.*;
import java.util.*;
import org.json.*;

/** Consistent metadata-only copy. The learning database and account stores are never opened. */
final class TrialObservationSnapshot {
    private static final int MAX_PAYLOAD=2*1024*1024;
    private TrialObservationSnapshot(){}
    static void create(File source,File target)throws Exception{
        if(!source.isFile()||target.exists())throw new IOException("Snapshot source unavailable or target already exists");
        boolean complete=false;
        try(SQLiteDatabase input=SQLiteDatabase.openDatabase(source.getPath(),null,SQLiteDatabase.OPEN_READONLY);
            SQLiteDatabase output=SQLiteDatabase.openOrCreateDatabase(target,null)){
            input.beginTransaction();output.beginTransaction();
            try{
                if(input.getVersion()!=1&&input.getVersion()!=2)throw new IOException("Unsupported evaluation schema");
                output.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER NOT NULL,payload TEXT NOT NULL)");
                output.execSQL("CREATE TABLE labels(session TEXT NOT NULL,event TEXT NOT NULL,choice TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(session,event))");
                output.setVersion(1);int count=0,labels=0;
                try(Cursor rows=input.rawQuery("SELECT id,at,length(payload) FROM sessions ORDER BY at,rowid",null)){
                    while(rows.moveToNext()){
                        if(++count>30||rows.getLong(2)<=0||rows.getLong(2)>MAX_PAYLOAD)throw new IOException("Evaluation export budget exceeded");
                        String id=rows.getString(0);if(!id.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}"))throw new IOException("Invalid session identity");
                        JSONObject original;
                        try(Cursor payload=input.rawQuery("SELECT payload FROM sessions WHERE id=?",new String[]{id})){if(!payload.moveToFirst())throw new IOException("Session missing");original=new JSONObject(payload.getString(0));}
                        RecommendationTrialStore.validate(original);
                        if(rows.getLong(1)!=original.getLong("frozen_at_ms"))throw new IOException("Session time differs");
                        JSONObject clean=sanitize(original);ContentValues row=new ContentValues();row.put("id",id);row.put("at",rows.getLong(1));row.put("payload",clean.toString());output.insertOrThrow("sessions",null,row);
                        Set<String> events=new HashSet<>();JSONArray items=original.getJSONArray("items");for(int n=0;n<items.length();n++)events.add(items.getJSONObject(n).getString("event_key"));
                        try(Cursor choices=input.rawQuery("SELECT event,choice,at FROM labels WHERE session=? ORDER BY event",new String[]{id})){
                            while(choices.moveToNext()){
                                String event=choices.getString(0),choice=choices.getString(1);long at=choices.getLong(2);
                                if(++labels>1800||!events.contains(event)||!Arrays.asList("want","skip","unsure").contains(choice)||at<rows.getLong(1))throw new IOException("Invalid evaluation label");
                                output.execSQL("INSERT INTO labels VALUES(?,?,?,?)",new Object[]{id,event,choice,at});
                            }
                        }
                    }
                }
                if(count==0)throw new IOException("No evaluation sessions");
                try(Cursor orphan=input.rawQuery("SELECT COUNT(*) FROM labels WHERE session NOT IN (SELECT id FROM sessions)",null)){orphan.moveToFirst();if(orphan.getInt(0)!=0)throw new IOException("Orphan evaluation label");}
                output.setTransactionSuccessful();
            }finally{output.endTransaction();input.endTransaction();}
            complete=true;
        }finally{if(!complete)SQLiteDatabase.deleteDatabase(target);}
    }
    private static JSONObject fields(JSONObject input,String... keys)throws JSONException{
        JSONObject output=new JSONObject();for(String key:keys)output.put(key,input.get(key));return output;
    }
    private static JSONObject candidateEvents(JSONObject input,String key)throws JSONException,IOException{
        JSONArray original=input.getJSONArray(key),events=new JSONArray();
        for(int n=0;n<original.length();n++){
            JSONObject event=original.getJSONObject(n);String identity=event.getString("event_key");requireHash(identity,true);
            long at=event.getJSONObject("candidate").getLong("observedAt");if(at<0)throw new IOException("Invalid observation time");
            events.put(new JSONObject().put("event_key",identity).put("candidate",new JSONObject().put("observedAt",at)));
        }
        return new JSONObject().put(key,events);
    }
    private static void requireHash(String value,boolean group)throws IOException{if(!value.matches(group?"(?:group-)?[a-f0-9]{64}":"[a-f0-9]{64}"))throw new IOException("Invalid metadata identity");}
    private static JSONObject learning(JSONObject value)throws JSONException,IOException{
        JSONObject output=fields(value,"schema","holdout_qualified","learning_cutoff_ms","profile_version","source_version","ledger_rows","latest_feedback_at_ms","ledger_sha256","active_feedback_sha256","known_learning_identities");
        JSONArray events=new JSONArray(),original=value.getJSONArray("candidate_events");
        for(int n=0;n<original.length();n++){
            JSONObject event=original.getJSONObject(n);requireHash(event.getString("event_key"),true);JSONArray members=new JSONArray(),input=event.getJSONArray("members");
            for(int i=0;i<input.length();i++)members.put(fields(input.getJSONObject(i),"id","text_hash","first_seen_ms"));
            events.put(new JSONObject().put("event_key",event.getString("event_key")).put("members",members));
        }
        return output.put("candidate_events",events);
    }
    static JSONObject sanitize(JSONObject input)throws JSONException,IOException{
        JSONObject output=fields(input,"trial_schema","kind","holdout_qualified","frozen_at_ms","source_version","profile_version");
        output.put("items",candidateEvents(input,"items").getJSONArray("items"));
        if("paired_snapshot".equals(input.getString("kind"))){
            for(String key:Arrays.asList("model_revision","profile_signature")){requireHash(input.getString(key),false);output.put(key,input.getString(key));}
            for(String key:Arrays.asList("semantic_policy","platform_policy")){String value=input.getString(key);if(!value.matches("[A-Za-z0-9._/-]{1,256}"))throw new IOException("Invalid ranking policy");output.put(key,value);}
            for(String key:Arrays.asList("generation","finite_rerank_scores"))output.put(key,input.getLong(key));
            output.put("pool",candidateEvents(input,"pool").getJSONArray("pool"));JSONObject orders=new JSONObject();
            for(String mode:Arrays.asList("neutral","embedding","dual")){JSONArray order=input.getJSONObject("rankings").getJSONArray(mode);for(int n=0;n<order.length();n++)requireHash(order.getString(n),true);orders.put(mode,order);}output.put("rankings",orders);
            if(input.has("learning_provenance"))output.put("learning_provenance",learning(input.getJSONObject("learning_provenance")));
            if(input.has("prospective_protocol")){
                JSONObject value=input.getJSONObject("prospective_protocol"),protocol=fields(value,"protocol_schema","holdout_qualified","id","policy","ranking_policies","cutoff_ms","app_epoch_ms","profile_version","model_revision","source_config_sha256","ledger_sha256","active_feedback_sha256","known_identities","known_text_hashes","used_identities","used_text_hashes");
                if(!protocol.getString("id").matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")||!protocol.getString("policy").matches("[A-Za-z0-9._/-]{1,256}")||!protocol.getString("ranking_policies").matches("[A-Za-z0-9._/-]{1,512}"))throw new IOException("Invalid prospective metadata");
                output.put("prospective_protocol",protocol);
            }
        }else{
            output.put("snapshot",input.getLong("snapshot"));String filter=input.getString("filter"),mode=input.getString("mode");
            if(!filter.matches("[A-Za-z0-9_|:-]{1,128}")||!mode.matches("[A-Za-z0-9_-]{1,40}"))throw new IOException("Invalid snapshot metadata");
            output.put("filter",filter).put("mode",mode);
        }
        return output;
    }
}
