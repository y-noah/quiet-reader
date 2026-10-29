package app.quietreader;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import app.quietreader.discovery.*;
import org.json.*;
import java.util.*;

/** Optional local observations. Never reads/writes the learning feedback database. */
final class RecommendationTrialStore extends SQLiteOpenHelper {
    RecommendationTrialStore(Context context){super(context,"recommendation-trials.db",null,2);}
    RecommendationTrialStore(Context context,String diagnostic){super(context,diagnosticName(context,diagnostic),null,2);}
    private static String diagnosticName(Context context,String name){if((context.getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)==0||name==null||!name.matches("test-trial-[a-z0-9-]+\\.db"))throw new IllegalArgumentException("Invalid trial diagnostic database");return name;}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,at INTEGER NOT NULL,payload TEXT NOT NULL)");db.execSQL("CREATE TABLE labels(session TEXT NOT NULL,event TEXT NOT NULL,choice TEXT NOT NULL,at INTEGER NOT NULL,PRIMARY KEY(session,event))");createProtocol(db);}
    private static void createProtocol(SQLiteDatabase db){db.execSQL("CREATE TABLE evaluation_protocol(id INTEGER PRIMARY KEY CHECK(id=1),payload TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){if(oldVersion==1&&newVersion==2)createProtocol(db);else throw new IllegalStateException("Unsupported trial schema");}
    // A stale policy must remain readable so the menu can replace or clear it.
    JSONObject protocol()throws JSONException{try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM evaluation_protocol WHERE id=1",null)){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}}
    void protocol(JSONObject value)throws JSONException{ProspectiveEvaluation.validate(value);ContentValues row=new ContentValues();row.put("id",1);row.put("payload",value.toString());getWritableDatabase().insertWithOnConflict("evaluation_protocol",null,row,SQLiteDatabase.CONFLICT_REPLACE);}
    static JSONObject freeze(List<FeedStore.Row> rows,long snapshot,long sourceVersion,long profileVersion,String filter,String mode,long at)throws JSONException{
        JSONArray items=new JSONArray();Set<String> seen=new HashSet<>();
        for(FeedStore.Row row:rows){if(seen.add(row.eventId))items.put(new JSONObject().put("event_key",row.eventId).put("candidate",row.candidate.json()));if(items.length()==20)break;}
        return new JSONObject().put("trial_schema",1).put("kind","single_snapshot").put("holdout_qualified",false).put("snapshot",snapshot).put("source_version",sourceVersion).put("profile_version",profileVersion).put("filter",filter).put("mode",mode).put("frozen_at_ms",at).put("items",items);
    }
    static JSONObject freezeComparison(RecommendationComparison comparison,long at,Random random)throws JSONException{
        if(comparison.dual.size()>100)throw new IllegalArgumentException("Comparison pool exceeds trial budget");
        Map<String,FeedStore.Row> canonical=new LinkedHashMap<>();JSONArray pool=new JSONArray();
        for(FeedStore.Row row:comparison.dual){canonical.put(row.eventId,row);pool.put(new JSONObject().put("event_key",row.eventId).put("candidate",row.candidate.json()));}
        JSONObject rankings=new JSONObject();Set<String> front=new LinkedHashSet<>();String[] modes={"neutral","embedding","dual"};
        List<List<FeedStore.Row>> orders=Arrays.asList(comparison.neutral,comparison.embedding,comparison.dual);
        for(int n=0;n<modes.length;n++){JSONArray ids=new JSONArray();List<FeedStore.Row> order=orders.get(n);for(int i=0;i<order.size();i++){ids.put(order.get(i).eventId);if(i<20)front.add(order.get(i).eventId);}rankings.put(modes[n],ids);}
        List<String> shuffled=new ArrayList<>(front);Collections.shuffle(shuffled,random);JSONArray items=new JSONArray();
        for(String id:shuffled)items.put(new JSONObject().put("event_key",id).put("candidate",canonical.get(id).candidate.json()));
        return new JSONObject().put("trial_schema",1).put("kind","paired_snapshot").put("holdout_qualified",false)
            .put("frozen_at_ms",at).put("source_version",comparison.sourceVersion).put("profile_version",comparison.profileVersion)
            .put("generation",comparison.generation).put("model_revision",comparison.modelRevision).put("profile_signature",comparison.profileSignature)
            .put("semantic_policy",SemanticOrder.VERSION).put("platform_policy",PlatformPreference.VERSION)
            .put("learning_provenance",comparison.learning()).put("prospective_protocol",comparison.protocol()).put("finite_rerank_scores",comparison.finiteRerankScores).put("pool",pool).put("rankings",rankings).put("items",items);
    }
    static void validate(JSONObject frozen)throws JSONException{
        String kind=frozen.getString("kind");boolean paired="paired_snapshot".equals(kind);
        if(frozen.getInt("trial_schema")!=1||!paired&&!"single_snapshot".equals(kind)||frozen.getBoolean("holdout_qualified"))throw new IllegalArgumentException("Invalid trial provenance");
        JSONArray items=frozen.getJSONArray("items");if(items.length()==0||items.length()>(paired?60:20))throw new IllegalArgumentException("No trial candidates");
        Map<String,HotCandidate> selected=new HashMap<>();for(int n=0;n<items.length();n++){JSONObject item=items.getJSONObject(n);String id=item.getString("event_key");if(id.isEmpty()||selected.put(id,HotCandidate.fromJson(item.getJSONObject("candidate")))!=null)throw new IllegalArgumentException("Duplicate trial event");}
        if(!paired)return;
        if(frozen.getString("model_revision").isEmpty()||frozen.getString("profile_signature").isEmpty())throw new IllegalArgumentException("Missing comparison provenance");
        if(frozen.has("learning_provenance"))ComparisonLearning.validate(frozen.getJSONObject("learning_provenance"),frozen.getLong("profile_version"),frozen.getLong("source_version"),frozen.getLong("frozen_at_ms"));
        JSONArray pool=frozen.getJSONArray("pool");if(pool.length()==0||pool.length()>100)throw new IllegalArgumentException("Invalid comparison pool");
        Set<String> expected=new HashSet<>();for(int n=0;n<pool.length();n++){JSONObject row=pool.getJSONObject(n);String id=row.getString("event_key");if(id.isEmpty()||!expected.add(id))throw new IllegalArgumentException("Duplicate pool event");HotCandidate candidate=HotCandidate.fromJson(row.getJSONObject("candidate")),chosen=selected.get(id);if(chosen!=null&&(!chosen.id.equals(candidate.id)||!chosen.textHash.equals(candidate.textHash)))throw new IllegalArgumentException("Unequal blinded content");}
        if(frozen.has("learning_provenance")){
            Map<String,JSONObject> recorded=new HashMap<>();JSONArray events=frozen.getJSONObject("learning_provenance").getJSONArray("candidate_events");
            for(int n=0;n<events.length();n++){JSONObject event=events.getJSONObject(n);recorded.put(event.getString("event_key"),event);}
            if(!recorded.keySet().equals(expected))throw new IllegalArgumentException("Learning capture pool differs");
            for(int n=0;n<pool.length();n++){JSONObject row=pool.getJSONObject(n);HotCandidate candidate=HotCandidate.fromJson(row.getJSONObject("candidate"));JSONArray members=recorded.get(row.getString("event_key")).getJSONArray("members");boolean found=false;
                for(int i=0;i<members.length();i++){JSONObject member=members.getJSONObject(i);if(candidate.id.equals(member.getString("id"))&&candidate.textHash.equals(member.getString("text_hash")))found=true;}
                if(!found)throw new IllegalArgumentException("Learning capture content differs");
            }
        }
        if(frozen.has("prospective_protocol")){
            JSONObject baseline=frozen.getJSONObject("prospective_protocol");ProspectiveEvaluation.validate(baseline);
            if(!frozen.has("learning_provenance")||!baseline.getString("model_revision").equals(frozen.getString("model_revision")))throw new IllegalArgumentException("Prospective learning capture missing or model differs");
            JSONObject learning=frozen.getJSONObject("learning_provenance");
            ProspectiveEvaluation.requireCurrent(baseline,learning,frozen.getString("model_revision"),baseline.getLong("app_epoch_ms"),baseline.getString("source_config_sha256"));
            Set<String> known=ProspectiveEvaluation.strings(baseline.getJSONArray("known_identities")),hashes=ProspectiveEvaluation.strings(baseline.getJSONArray("known_text_hashes"));known.addAll(ProspectiveEvaluation.strings(baseline.getJSONArray("used_identities")));hashes.addAll(ProspectiveEvaluation.strings(baseline.getJSONArray("used_text_hashes")));
            JSONArray events=learning.getJSONArray("candidate_events");for(int n=0;n<events.length();n++){JSONObject event=events.getJSONObject(n);if(!Collections.disjoint(known,ComparisonLearning.candidateIdentities(event)))throw new IllegalArgumentException("Previously exposed prospective event");JSONArray members=event.getJSONArray("members");
                for(int i=0;i<members.length();i++){JSONObject member=members.getJSONObject(i);if(member.getLong("first_seen_ms")<=baseline.getLong("cutoff_ms")||known.contains(member.getString("id"))||hashes.contains(member.getString("text_hash")))throw new IllegalArgumentException("Prospective pool contains old content");}
            }
        }
        JSONObject rankings=frozen.getJSONObject("rankings");if(rankings.length()!=3)throw new IllegalArgumentException("Three comparison orders required");Set<String> front=new HashSet<>();
        for(String mode:Arrays.asList("neutral","embedding","dual")){JSONArray order=rankings.getJSONArray(mode);Set<String> found=new HashSet<>();for(int n=0;n<order.length();n++){String id=order.getString(n);if(!found.add(id))throw new IllegalArgumentException("Duplicate ranked event");if(n<20)front.add(id);}if(!found.equals(expected))throw new IllegalArgumentException("Unequal comparison pools");}
        if(!selected.keySet().equals(front))throw new IllegalArgumentException("Incomplete comparison fronts");
    }
    static String comparisonSummary(JSONObject value)throws JSONException{
        if(!"paired_snapshot".equals(value.optString("kind")))return "";
        JSONObject labels=value.getJSONObject("labels");StringBuilder result=new StringBuilder("\n\n本轮前排选择（同一批候选）：");String[] modes={"neutral","embedding","dual"},names={"基础顺序","兴趣初筛","双模型顺序"};
        for(int mode=0;mode<modes.length;mode++){JSONArray order=value.getJSONObject("rankings").getJSONArray(modes[mode]);int total=Math.min(20,order.length()),want=0,skip=0;
            for(int n=0;n<total;n++){JSONObject label=labels.optJSONObject(order.getString(n));String choice=label==null?"":label.optString("choice");if("want".equals(choice))want++;else if("skip".equals(choice))skip++;}
            result.append('\n').append(names[mode]).append("：想看 ").append(want).append('/').append(total).append("，不想看 ").append(skip).append("，不确定或未评价 ").append(total-want-skip);
        }
        return result.append("\n仅反映本轮选择，不确定的内容不算不喜欢。").toString();
    }
    String create(JSONObject frozen)throws JSONException{
        validate(frozen);
        String id=UUID.randomUUID().toString();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            if(frozen.has("prospective_protocol")){
                JSONObject expected=frozen.getJSONObject("prospective_protocol"),current=protocol();
                if(current==null||!current.toString().equals(expected.toString()))throw new IllegalArgumentException("Evaluation baseline changed");
                Set<String> used=ProspectiveEvaluation.strings(current.getJSONArray("used_identities")),hashes=ProspectiveEvaluation.strings(current.getJSONArray("used_text_hashes"));
                JSONArray events=frozen.getJSONObject("learning_provenance").getJSONArray("candidate_events");for(int n=0;n<events.length();n++){JSONObject event=events.getJSONObject(n);used.addAll(ComparisonLearning.candidateIdentities(event));JSONArray members=event.getJSONArray("members");for(int i=0;i<members.length();i++){JSONObject member=members.getJSONObject(i);hashes.add(member.getString("text_hash"));}}
                current.put("used_identities",new JSONArray(used)).put("used_text_hashes",new JSONArray(hashes));protocol(current);
            }
            ContentValues values=new ContentValues();values.put("id",id);values.put("at",frozen.getLong("frozen_at_ms"));values.put("payload",frozen.toString());db.insertOrThrow("sessions",null,values);
            db.execSQL("DELETE FROM sessions WHERE id NOT IN (SELECT id FROM sessions ORDER BY at DESC,rowid DESC LIMIT 30)");db.execSQL("DELETE FROM labels WHERE session NOT IN (SELECT id FROM sessions)");db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    JSONObject read(String id)throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM sessions WHERE id=?",new String[]{id})){if(!c.moveToFirst())return null;JSONObject result=new JSONObject(c.getString(0));JSONObject labels=new JSONObject();try(Cursor rows=getReadableDatabase().rawQuery("SELECT event,choice,at FROM labels WHERE session=?",new String[]{id})){while(rows.moveToNext())labels.put(rows.getString(0),new JSONObject().put("choice",rows.getString(1)).put("at_ms",rows.getLong(2)));}return result.put("id",id).put("labels",labels);}
    }
    String latestCompleted()throws JSONException{
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM sessions ORDER BY at DESC,rowid DESC",null)){
            while(c.moveToNext()){String id=c.getString(0);JSONObject value=read(id);if(next(value)==value.getJSONArray("items").length())return id;}
        }return null;
    }
    static String completedSummary(JSONObject value)throws JSONException{
        if(next(value)!=value.getJSONArray("items").length())throw new IllegalArgumentException("Trial is unfinished");
        int want=0,skip=0,unsure=0;JSONArray items=value.getJSONArray("items");JSONObject labels=value.getJSONObject("labels");
        for(int n=0;n<items.length();n++){String choice=labels.getJSONObject(items.getJSONObject(n).getString("event_key")).getString("choice");if(choice.equals("want"))want++;else if(choice.equals("skip"))skip++;else unsure++;}
        return "本轮已记录 "+items.length()+" 项：想看 "+want+"，不想看 "+skip+"，不确定 "+unsure+"。这些选择不会改变阅读偏好。"+comparisonSummary(value);
    }
    static int next(JSONObject session)throws JSONException{JSONArray items=session.getJSONArray("items");JSONObject labels=session.getJSONObject("labels");for(int n=0;n<items.length();n++)if(!labels.has(items.getJSONObject(n).getString("event_key")))return n;return items.length();}
    String unfinished()throws JSONException{try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM sessions ORDER BY at DESC,rowid DESC",null)){while(c.moveToNext()){String id=c.getString(0);JSONObject session=read(id);if(next(session)<session.getJSONArray("items").length())return id;}}return null;}
    void label(String id,int index,String choice,long at)throws JSONException{
        if(!Arrays.asList("want","skip","unsure").contains(choice))throw new IllegalArgumentException("Invalid trial choice");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{
            JSONObject session=read(id);if(session==null||index<0||index>=session.getJSONArray("items").length()||at<session.getLong("frozen_at_ms"))throw new IllegalArgumentException("Trial no longer available");
            ContentValues values=new ContentValues();values.put("session",id);values.put("event",session.getJSONArray("items").getJSONObject(index).getString("event_key"));values.put("choice",choice);values.put("at",at);db.insertWithOnConflict("labels",null,values,SQLiteDatabase.CONFLICT_REPLACE);db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    String summary(){SQLiteDatabase db=getReadableDatabase();long sessions=0,labels=0;try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM sessions",null)){c.moveToFirst();sessions=c.getLong(0);}try(Cursor c=db.rawQuery("SELECT COUNT(*) FROM labels",null)){c.moveToFirst();labels=c.getLong(0);}return "本机已记录 "+sessions+" 轮、"+labels+" 项选择。保留最近30轮。";}
    void clear(){SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{db.delete("labels",null,null);db.delete("sessions",null,null);db.delete("evaluation_protocol",null,null);db.setTransactionSuccessful();}finally{db.endTransaction();}}
}
