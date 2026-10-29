package app.quietreader.discovery;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.os.Looper;
import app.quietreader.Models.Source;
import app.quietreader.ArticleProgressHistory;
import org.json.*;
import java.nio.*;
import java.util.*;

/** Worker-only recommendation storage, separate from cookies and existing reader data.
 * Source commits and feed snapshots are transactions with stale-result checks.
 */
public final class FeedStore extends SQLiteOpenHelper {
    public static final String DATABASE="personalized-hot-topics-v1.db";
    public static final long BODY_LANGUAGE_TTL=10*60_000L;
    public static final long VIDEO_EVIDENCE_TTL=7L*24*60*60*1000;
    public FeedStore(Context context){this(context,DATABASE);}
    public FeedStore(Context context,String databaseName){super(context,databaseName,null,9);setWriteAheadLoggingEnabled(true);}
    @Override public void onConfigure(SQLiteDatabase db){db.setForeignKeyConstraintsEnabled(true);}
    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        db.execSQL("INSERT INTO meta VALUES ('sources',0),('profile',0),('profile_time',0)");
        db.execSQL("CREATE TABLE sources (id TEXT PRIMARY KEY, requested INTEGER NOT NULL DEFAULT 0, committed INTEGER NOT NULL DEFAULT 0, success_at INTEGER NOT NULL DEFAULT 0, status TEXT NOT NULL DEFAULT 'EMPTY', error TEXT NOT NULL DEFAULT '', enabled INTEGER NOT NULL DEFAULT 1, retry_at INTEGER NOT NULL DEFAULT 0, failures INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE candidates (id TEXT PRIMARY KEY, source TEXT NOT NULL REFERENCES sources(id), payload TEXT NOT NULL, text_hash TEXT NOT NULL, first_seen INTEGER NOT NULL, last_seen INTEGER NOT NULL, rank INTEGER NOT NULL, active INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX candidates_source ON candidates(source,active,rank)");
        db.execSQL("CREATE TABLE feedback (id TEXT PRIMARY KEY, action TEXT NOT NULL, scope TEXT NOT NULL, target TEXT NOT NULL, at INTEGER NOT NULL, expires INTEGER, undo_of TEXT REFERENCES feedback(id), sample TEXT NOT NULL, source TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE UNIQUE INDEX feedback_undo ON feedback(undo_of) WHERE undo_of IS NOT NULL");
        db.execSQL("CREATE TABLE embeddings (key TEXT PRIMARY KEY, text_hash TEXT NOT NULL, model TEXT NOT NULL, tokenizer TEXT NOT NULL, normalizer TEXT NOT NULL, vector BLOB NOT NULL, used_at INTEGER NOT NULL)");
        createReranks(db);
        db.execSQL("CREATE TABLE snapshots (id INTEGER PRIMARY KEY AUTOINCREMENT, source_version INTEGER NOT NULL, profile_version INTEGER NOT NULL, created_at INTEGER NOT NULL, mode TEXT NOT NULL, filter TEXT NOT NULL, anchor TEXT NOT NULL DEFAULT '', offset INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE snapshot_rows (snapshot INTEGER NOT NULL REFERENCES snapshots(id) ON DELETE CASCADE, position INTEGER NOT NULL, event_id TEXT NOT NULL, payload TEXT NOT NULL, reason TEXT NOT NULL, PRIMARY KEY(snapshot,position), UNIQUE(snapshot,event_id))");
        db.execSQL("CREATE TABLE saved (id TEXT PRIMARY KEY, event_id TEXT NOT NULL, payload TEXT NOT NULL, saved_at INTEGER NOT NULL)");
        FeedExposureHistory.create(db);
        ArticleProgressHistory.create(db);
        for(Source source:Source.values()){
            ContentValues v=new ContentValues();v.put("id",source.name());v.put("enabled",HotSourceCatalog.defaultEnabled(source)?1:0);db.insertOrThrow("sources",null,v);
        }
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){
            db.execSQL("ALTER TABLE sources ADD COLUMN retry_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE sources ADD COLUMN failures INTEGER NOT NULL DEFAULT 0");
            db.execSQL("INSERT OR IGNORE INTO meta VALUES ('profile_time',0)");
        }
        if(oldVersion<3)createReranks(db);
        if(oldVersion<4)db.execSQL("ALTER TABLE feedback ADD COLUMN source TEXT NOT NULL DEFAULT ''");
        if(oldVersion<5){ContentValues source=new ContentValues();source.put("id",Source.ITHOME.name());source.put("enabled",1);db.insertWithOnConflict("sources",null,source,SQLiteDatabase.CONFLICT_IGNORE);}
        if(oldVersion<6){ContentValues source=new ContentValues();source.put("id",Source.THEPAPER.name());source.put("enabled",1);db.insertWithOnConflict("sources",null,source,SQLiteDatabase.CONFLICT_IGNORE);}
        if(oldVersion<7){ContentValues source=new ContentValues();source.put("id",Source.BBC.name());source.put("enabled",1);db.insertWithOnConflict("sources",null,source,SQLiteDatabase.CONFLICT_IGNORE);}
        if(oldVersion<8)FeedExposureHistory.create(db);
        if(oldVersion<9)ArticleProgressHistory.create(db);
        if(newVersion>9)throw new IllegalStateException("Explicit data migration required");
    }
    private static void createReranks(SQLiteDatabase db){db.execSQL("CREATE TABLE IF NOT EXISTS reranks (key TEXT PRIMARY KEY, logit REAL NOT NULL, used_at INTEGER NOT NULL)");}
    public Float rerank(String key,long at){SQLiteDatabase db=db();try(Cursor c=db.rawQuery("SELECT logit FROM reranks WHERE key=?",new String[]{key})){
        if(!c.moveToFirst())return null;
        int type=c.getType(0);float value=type==Cursor.FIELD_TYPE_FLOAT||type==Cursor.FIELD_TYPE_INTEGER?c.getFloat(0):Float.NaN;
        if(!Float.isFinite(value)){db.delete("reranks","key=?",new String[]{key});return null;}
        ContentValues values=new ContentValues();values.put("used_at",at);db.update("reranks",values,"key=?",new String[]{key});return value;
    }}
    private static void writeRerank(SQLiteDatabase db,String key,float logit,long at){if(!Float.isFinite(logit))throw new IllegalArgumentException("Invalid rerank score");ContentValues values=new ContentValues();values.put("key",key);values.put("logit",logit);values.put("used_at",at);if(db.insertWithOnConflict("reranks",null,values,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new SQLiteException("Rerank cache commit failed");}
    private static void trimReranks(SQLiteDatabase db){db.execSQL("DELETE FROM reranks WHERE key IN (SELECT key FROM reranks ORDER BY used_at DESC,key LIMIT -1 OFFSET 5000)");}
    public void putRerank(String key,float logit,long at){SQLiteDatabase db=db();db.beginTransaction();try{writeRerank(db,key,logit,at);trimReranks(db);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public static final class EventRerank {
        public final float logit;public final boolean complete;
        private EventRerank(float logit,boolean complete){this.logit=logit;this.complete=complete;}
    }
    /** A score without the exact current coverage marker is a cache miss. */
    public EventRerank eventRerank(String key,long at){SQLiteDatabase db=db();db.beginTransaction();try{Float score=rerank(key,at),coverage=rerank(EventRerankCacheKey.coverage(key),at);db.setTransactionSuccessful();return score==null||coverage==null||coverage!=0f&&coverage!=1f?null:new EventRerank(score,coverage==1f);}finally{db.endTransaction();}}
    public void putEventRerank(String key,float logit,boolean complete,long at){SQLiteDatabase db=db();db.beginTransaction();try{writeRerank(db,key,logit,at);writeRerank(db,EventRerankCacheKey.coverage(key),complete?1f:0f,at);trimReranks(db);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    private static void worker(){if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("FeedStore must run off the UI thread");}
    private SQLiteDatabase db(){worker();return getWritableDatabase();}
    private static long scalar(SQLiteDatabase db,String sql,String...args){try(Cursor c=db.rawQuery(sql,args)){return c.moveToFirst()?c.getLong(0):0;}}
    private static void bump(SQLiteDatabase db,String key){db.execSQL("UPDATE meta SET value=value+1 WHERE key=?",new Object[]{key});}
    public long version(String key){if(!key.equals("sources")&&!key.equals("profile"))throw new IllegalArgumentException("Unknown version");return scalar(db(),"SELECT value FROM meta WHERE key=?",key);}
    /** Rebuild pre-policy snapshots once; keep feedback and reusable vectors. */
    public void ensureCandidatePolicy(){SQLiteDatabase db=db();db.beginTransaction();try{
        if(scalar(db,"SELECT value FROM meta WHERE key='candidate_policy'")!=2){db.execSQL("INSERT OR REPLACE INTO meta VALUES ('candidate_policy',2)");bump(db,"sources");}
        db.setTransactionSuccessful();
    }finally{db.endTransaction();}}
    public long beginRefresh(Source source){return beginRefresh(source,System.currentTimeMillis());}
    /** A negative revision means disabled or still within a passive retry deadline. */
    public long beginRefresh(Source source,long now){
        SQLiteDatabase db=db();db.beginTransaction();try{
            if(scalar(db,"SELECT COUNT(*) FROM sources WHERE id=? AND enabled=1 AND retry_at<=?",source.name(),""+now)==0)return -1;
            db.execSQL("UPDATE sources SET requested=requested+1,status='REFRESHING',error='' WHERE id=?",new Object[]{source.name()});
            long revision=scalar(db,"SELECT requested FROM sources WHERE id=?",source.name());db.setTransactionSuccessful();return revision;
        }finally{db.endTransaction();}
    }
    public boolean upsertSnapshot(Source source,long revision,List<HotCandidate> candidates,long at){
        if(candidates.isEmpty()||candidates.size()>500)throw new IllegalArgumentException("Source snapshot size is invalid");
        LinkedHashMap<String,HotCandidate> unique=new LinkedHashMap<>();
        for(HotCandidate candidate:candidates){if(candidate.source!=source)throw new IllegalArgumentException("Mixed source snapshot");unique.putIfAbsent(candidate.id,candidate);}
        SQLiteDatabase db=db();db.beginTransaction();try{
            if(!refreshing(db,source,revision))return false;
            db.execSQL("UPDATE candidates SET active=0 WHERE source=?",new Object[]{source.name()});
            for(HotCandidate c:unique.values()){
                long first=scalar(db,"SELECT first_seen FROM candidates WHERE id=?",c.id);
                HotCandidate incoming=c;
                try(Cursor old=db.rawQuery("SELECT payload FROM candidates WHERE id=? AND text_hash=?",new String[]{c.id,c.textHash})){
                    if(old.moveToFirst()){HotCandidate prior=parse(old.getString(0));long age=at-prior.bodyLanguageAt,videoAge=at-prior.videoEvidenceAt;
                        boolean language=c.bodyLanguage==ContentLanguage.Body.UNKNOWN&&prior.bodyLanguage!=ContentLanguage.Body.UNKNOWN&&prior.bodyLanguageAt>0&&age>=0&&age<=BODY_LANGUAGE_TTL;
                        boolean video=!c.video&&prior.videoEvidenceAt>0&&videoAge>=0&&videoAge<=VIDEO_EVIDENCE_TTL;
                        if(language||video)incoming=new HotCandidate(c.source,c.originalUrl,c.title,c.snippet,c.rawHeat,video?prior.video:c.video,c.sourceRank,c.publishedAt,c.observedAt,language?prior.bodyLanguage:c.bodyLanguage,c.sourceTag,language?prior.bodyLanguageAt:c.bodyLanguageAt,video?prior.videoEvidenceAt:c.videoEvidenceAt);
                    }
                }
                ContentValues v=new ContentValues();v.put("id",c.id);v.put("source",source.name());v.put("payload",incoming.json().toString());v.put("text_hash",c.textHash);v.put("first_seen",first==0?at:first);v.put("last_seen",at);v.put("rank",c.sourceRank);v.put("active",1);
                if(db.insertWithOnConflict("candidates",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new SQLiteException("Candidate commit failed");
            }
            ContentValues state=new ContentValues();state.put("committed",revision);state.put("success_at",at);state.put("status","READY");state.put("error","");state.put("failures",0);state.put("retry_at",0);db.update("sources",state,"id=?",new String[]{source.name()});
            // Saved/feedback/snapshots own their metadata; eviction cannot destroy them.
            db.execSQL("DELETE FROM candidates WHERE id IN (SELECT id FROM candidates ORDER BY active DESC,last_seen DESC,id LIMIT -1 OFFSET 5000)");
            bump(db,"sources");db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    public boolean failRefresh(Source source,long revision,String status,String code){
        return failRefresh(source,revision,status,code,0,System.currentTimeMillis());
    }
    private static boolean refreshing(SQLiteDatabase db,Source source,long revision){
        return scalar(db,"SELECT COUNT(*) FROM sources WHERE id=? AND requested=? AND committed<? AND enabled=1 AND status='REFRESHING'",source.name(),""+revision,""+revision)==1;
    }
    /** Worker-side guard before a second acquisition phase reuses a refresh revision. */
    public boolean refreshCurrent(Source source,long revision){return refreshing(db(),source,revision);}
    public boolean failRefresh(Source source,long revision,String status,String code,long serverRetryAt,long now){
        if(!Arrays.asList("OFFLINE","LOGIN_REQUIRED","RATE_LIMITED","UNAVAILABLE").contains(status))throw new IllegalArgumentException("Invalid failure status");
        SQLiteDatabase db=db();db.beginTransaction();try{
            if(!refreshing(db,source,revision))return false;
            int count=(int)Math.min(100,scalar(db,"SELECT failures FROM sources WHERE id=?",source.name())+1);
            ContentValues values=new ContentValues();values.put("status",status);values.put("error",HotCandidate.clean(code,120));values.put("failures",count);
            // Offline does not penalize the next user request after connectivity returns.
            values.put("retry_at",status.equals("OFFLINE")?0:RefreshBackoff.until(count,status.equals("RATE_LIMITED")?429:0,serverRetryAt,now));
            db.update("sources",values,"id=?",new String[]{source.name()});db.setTransactionSuccessful();return true;
        }finally{db.endTransaction();}
    }
    public void cancelRefresh(Source source,long revision){
        db().execSQL("UPDATE sources SET requested=requested+1,status=CASE WHEN success_at>0 THEN 'STALE' ELSE 'EMPTY' END,error='' WHERE id=? AND requested=? AND committed<? AND status='REFRESHING'",new Object[]{source.name(),revision,revision});
    }
    public JSONObject sourceState(Source source){try(Cursor c=db().rawQuery("SELECT requested,committed,success_at,status,error,enabled,retry_at,failures FROM sources WHERE id=?",new String[]{source.name()})){
        if(!c.moveToFirst())throw new IllegalStateException("Source absent");return new JSONObject().put("requested",c.getLong(0)).put("committed",c.getLong(1)).put("lastSuccessAt",c.getLong(2)).put("status",c.getString(3)).put("error",c.getString(4)).put("enabled",c.getInt(5)!=0).put("retryAt",c.getLong(6)).put("failures",c.getLong(7));
    }catch(JSONException e){throw new IllegalStateException(e);}}
    public void enable(Source source,boolean enabled){SQLiteDatabase db=db();db.beginTransaction();try{ContentValues v=new ContentValues();v.put("enabled",enabled?1:0);if(db.update("sources",v,"id=? AND enabled<>?",new String[]{source.name(),enabled?"1":"0"})>0){
        db.execSQL("UPDATE sources SET requested=requested+1,status=CASE WHEN status='REFRESHING' THEN CASE WHEN success_at>0 THEN 'STALE' ELSE 'EMPTY' END ELSE status END WHERE id=?",new Object[]{source.name()});bump(db,"sources");
    }db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public List<HotCandidate> candidates(int limit,int offset){
        if(limit<1||limit>5000||offset<0)throw new IllegalArgumentException("Invalid page");List<HotCandidate> rows=new ArrayList<>();
        try(Cursor c=db().rawQuery("SELECT c.payload FROM candidates c JOIN sources s ON s.id=c.source WHERE c.active=1 AND s.enabled=1 ORDER BY c.rank,c.last_seen DESC,c.id LIMIT ? OFFSET ?",new String[]{""+limit,""+offset})){
            while(c.moveToNext())rows.add(parse(c.getString(0)));
        }return rows;
    }
    private static HotCandidate parse(String payload){try{return HotCandidate.fromJson(new JSONObject(payload));}catch(JSONException e){throw new IllegalStateException("Stored candidate is invalid",e);}}
    /** Evidence belongs to the exact candidate metadata version that was prefetched. */
    public boolean recordBodyLanguage(String id,String textHash,ContentLanguage.Body language){
        return recordBodyLanguage(id,textHash,language,System.currentTimeMillis());
    }
    public boolean recordBodyLanguage(String id,String textHash,ContentLanguage.Body language,long bodyAt){
        long age=System.currentTimeMillis()-bodyAt;
        if(language==ContentLanguage.Body.UNKNOWN||bodyAt<=0||age<0||age>BODY_LANGUAGE_TTL)return false;
        SQLiteDatabase db=db();db.beginTransaction();try{
            HotCandidate existing;
            try(Cursor c=db.rawQuery("SELECT payload FROM candidates WHERE id=? AND text_hash=? AND active=1",new String[]{id,textHash})){
                if(!c.moveToFirst())return false;existing=parse(c.getString(0));
            }
            if(existing.bodyLanguageAt>bodyAt||existing.bodyLanguage==language&&existing.bodyLanguageAt==bodyAt)return false;
            boolean changed=existing.bodyLanguage!=language;
            HotCandidate updated=new HotCandidate(existing.source,existing.originalUrl,existing.title,existing.snippet,existing.rawHeat,existing.video,existing.sourceRank,existing.publishedAt,existing.observedAt,language,existing.sourceTag,bodyAt,existing.videoEvidenceAt);
            ContentValues value=new ContentValues();value.put("payload",updated.json().toString());
            db.update("candidates",value,"id=? AND text_hash=?",new String[]{id,textHash});if(changed)bump(db,"sources");db.setTransactionSuccessful();return changed;
        }finally{db.endTransaction();}
    }
    /** Positive whole-topic video evidence; never a preference or a blacklist event. */
    public boolean recordVideo(String id,String textHash,long bodyAt){
        return recordTopicType(id,textHash,true,bodyAt);
    }
    /** A verified later primary text body can correct inferred video type, never
     * override source-provided video metadata. Its watermark rejects late positives. */
    public boolean recordTextTopic(String id,String textHash,long bodyAt){
        return recordTopicType(id,textHash,false,bodyAt);
    }
    private boolean recordTopicType(String id,String textHash,boolean video,long bodyAt){
        long age=System.currentTimeMillis()-bodyAt;
        if(bodyAt<=0||age<0||age>VIDEO_EVIDENCE_TTL)return false;
        SQLiteDatabase db=db();db.beginTransaction();try{
            HotCandidate existing;
            try(Cursor c=db.rawQuery("SELECT payload FROM candidates WHERE id=? AND text_hash=? AND active=1",new String[]{id,textHash})){
                if(!c.moveToFirst())return false;existing=parse(c.getString(0));
            }
            if((existing.video&&existing.videoEvidenceAt==0)||existing.videoEvidenceAt>=bodyAt)return false;
            boolean changed=existing.video!=video;
            HotCandidate updated=new HotCandidate(existing.source,existing.originalUrl,existing.title,existing.snippet,existing.rawHeat,video,existing.sourceRank,existing.publishedAt,existing.observedAt,existing.bodyLanguage,existing.sourceTag,existing.bodyLanguageAt,bodyAt);
            ContentValues value=new ContentValues();value.put("payload",updated.json().toString());
            db.update("candidates",value,"id=? AND text_hash=?",new String[]{id,textHash});if(changed)bump(db,"sources");db.setTransactionSuccessful();return changed;
        }finally{db.endTransaction();}
    }
    public boolean feedback(String id,String action,String scope,String target,long at,Long expires,String sample){
        return feedback(id,action,scope,target,at,expires,sample,null);
    }
    public boolean feedback(String id,String action,String scope,String target,long at,Long expires,String sample,Source source){
        boolean memberTarget="event".equals(scope)&&Arrays.asList("BLOCK","ENOUGH","SEPARATE").contains(action)&&EventTargets.structuredTarget(target);
        if(!Arrays.asList("MORE","LESS","ENOUGH","BLOCK","FOLLOW","SEPARATE","SAVED","READ","QUICK_EXIT").contains(action)||!Arrays.asList("sample","event","source","entity").contains(scope)||id.isEmpty()||target.isEmpty()||target.length()>512&&!memberTarget||expires!=null&&expires<=at)throw new IllegalArgumentException("Invalid feedback");
        SQLiteDatabase db=db();db.beginTransaction();try{
            ContentValues v=new ContentValues();v.put("id",id);v.put("action",action);v.put("scope",scope);v.put("target",target);v.put("at",at);if(expires!=null)v.put("expires",expires);v.put("sample",HotCandidate.clean(sample,1000));v.put("source",source==null?"":source.name());
            long inserted=db.insertWithOnConflict("feedback",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(inserted!=-1)bump(db,"profile");db.setTransactionSuccessful();return inserted!=-1;
        }finally{db.endTransaction();}
    }
    public boolean undo(String eventId,long at){
        SQLiteDatabase db=db();db.beginTransaction();try(Cursor c=db.rawQuery("SELECT scope,target FROM feedback WHERE id=? AND undo_of IS NULL",new String[]{eventId})){
            if(!c.moveToFirst())return false;
            ContentValues v=new ContentValues();v.put("id",UUID.randomUUID().toString());v.put("action","UNDO");v.put("scope",c.getString(0));v.put("target",c.getString(1));v.put("at",at);v.put("undo_of",eventId);v.put("sample","");
            long inserted=db.insertWithOnConflict("feedback",null,v,SQLiteDatabase.CONFLICT_IGNORE);if(inserted!=-1)bump(db,"profile");db.setTransactionSuccessful();return inserted!=-1;
        }finally{db.endTransaction();}
    }
    /** Advance expiry without timers. The clock watermark prevents expired interests
     * reappearing after a device clock rollback. Read/commit callers use one epoch. */
    private static long expireFeedback(SQLiteDatabase db,long now){
        long previous=scalar(db,"SELECT value FROM meta WHERE key='profile_time'");
        long effective=Math.max(previous,now);
        if(effective>previous){
            if(scalar(db,"SELECT COUNT(*) FROM feedback f WHERE f.undo_of IS NULL AND f.expires>? AND f.expires<=? AND NOT EXISTS(SELECT 1 FROM feedback u WHERE u.undo_of=f.id)",""+previous,""+effective)>0)bump(db,"profile");
            db.execSQL("INSERT OR REPLACE INTO meta VALUES ('profile_time',?)",new Object[]{effective});
        }
        return effective;
    }
    public JSONObject profile(long now){
        SQLiteDatabase db=db();db.beginTransaction();try{
            long effective=expireFeedback(db,now);JSONObject result=new JSONObject().put("version",scalar(db,"SELECT value FROM meta WHERE key='profile'"));
            result.put("feedback",readFeedback(db,effective));db.setTransactionSuccessful();return result;
        }catch(JSONException e){throw new IllegalStateException(e);}finally{db.endTransaction();}
    }
    public JSONArray activeFeedback(long now){return profile(now).optJSONArray("feedback");}
    public String evaluationSourceConfig(){JSONArray sources=new JSONArray();try(Cursor c=db().rawQuery("SELECT id,enabled FROM sources ORDER BY id",null)){while(c.moveToNext())sources.put(new JSONArray().put(c.getString(0)).put(c.getInt(1)));}return HotCandidate.hash(sources.toString());}
    public JSONObject evaluationBaseline(long now,String model,long appEpoch){
        SQLiteDatabase db=db();db.beginTransaction();try{
            JSONObject profile=profile(now);List<Row> rows=CandidateOrder.forModels(candidates(5000,0),profile.getJSONArray("feedback"));
            if(rows.isEmpty()||new SemanticOrder.Profile(profile.getJSONArray("feedback")).empty())throw new IllegalArgumentException("请先获取一些热门内容并设置阅读偏好。");
            JSONObject learning=comparisonLearning(rows.subList(0,Math.min(100,rows.size())),now,profile.getLong("version"),version("sources"));
            Set<String> known=new TreeSet<>(),hashes=new TreeSet<>();
            known.addAll(ProspectiveEvaluation.strings(learning.getJSONArray("known_learning_identities")));
            try(Cursor c=db.rawQuery("SELECT payload FROM candidates ORDER BY id",null)){while(c.moveToNext()){HotCandidate candidate=parse(c.getString(0));known.add(candidate.id);known.addAll(EventGroups.aliases(candidate));hashes.add(candidate.textHash);}}
            JSONObject result=new JSONObject().put("protocol_schema",1).put("holdout_qualified",false).put("id",UUID.randomUUID().toString()).put("policy",ProspectiveEvaluation.POLICY)
                .put("ranking_policies",ProspectiveEvaluation.policies()).put("cutoff_ms",learning.getLong("learning_cutoff_ms")).put("app_epoch_ms",appEpoch).put("model_revision",model)
                .put("source_config_sha256",evaluationSourceConfig()).put("profile_version",profile.getLong("version")).put("ledger_sha256",learning.getString("ledger_sha256"))
                .put("active_feedback_sha256",learning.getString("active_feedback_sha256")).put("known_identities",new JSONArray(known)).put("known_text_hashes",new JSONArray(hashes))
                .put("used_identities",new JSONArray()).put("used_text_hashes",new JSONArray());
            ProspectiveEvaluation.validate(result);db.setTransactionSuccessful();return result;
        }catch(JSONException error){throw new IllegalStateException(error);}finally{db.endTransaction();}
    }
    public List<Row> evaluationCandidates(JSONObject baseline,long now,String model,long appEpoch){
        SQLiteDatabase db=db();db.beginTransaction();try{
            JSONObject profile=profile(now);ProspectiveEvaluation.requireSettings(baseline,profile.getLong("version"),model,appEpoch,evaluationSourceConfig());List<Row> available=CandidateOrder.forModels(candidates(5000,0),profile.getJSONArray("feedback"));
            if(available.isEmpty())return Collections.emptyList();
            JSONObject learning=comparisonLearning(available.subList(0,Math.min(100,available.size())),now,profile.getLong("version"),version("sources"));
            ProspectiveEvaluation.requireCurrent(baseline,learning,model,appEpoch,evaluationSourceConfig());Map<String,Long> first=new HashMap<>();
            try(Cursor c=db.rawQuery("SELECT id,first_seen FROM candidates",null)){while(c.moveToNext())first.put(c.getString(0),c.getLong(1));}
            List<Row> result=ProspectiveEvaluation.select(available,baseline,first);db.setTransactionSuccessful();return result;
        }catch(JSONException error){throw new IllegalStateException(error);}finally{db.endTransaction();}
    }
    /** One transaction before model work. Includes undone/expired ledger entries so
     * evicting a candidate cannot make its known learning identity disappear. */
    public JSONObject comparisonLearning(List<Row> rows,long now,long expectedProfile,long expectedSource){
        SQLiteDatabase db=db();db.beginTransaction();try{
            long cutoff=expireFeedback(db,now),profile=scalar(db,"SELECT value FROM meta WHERE key='profile'"),source=scalar(db,"SELECT value FROM meta WHERE key='sources'");
            if(profile!=expectedProfile||source!=expectedSource)throw new IllegalStateException("Comparison inputs changed");
            java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");Set<String> learned=new TreeSet<>();int count=0;long latest=0;
            try(Cursor c=db.rawQuery("SELECT id,action,scope,target,at,expires,undo_of,sample,source FROM feedback ORDER BY at,id",null)){
                while(c.moveToNext()){
                    if(++count>100000)throw new IllegalStateException("Comparison learning ledger exceeds budget");
                    JSONArray record=new JSONArray();for(int i=0;i<9;i++)record.put(c.isNull(i)?JSONObject.NULL:i==4||i==5?c.getLong(i):c.getString(i));
                    digest.update((record.toString()+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    latest=Math.max(latest,c.getLong(4));learned.addAll(ComparisonLearning.eventIdentities(c.getString(2),c.getString(3)));
                    if(learned.size()>10000)throw new IllegalStateException("Comparison learning identities exceed budget");
                }
            }
            StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
            JSONArray events=new JSONArray();for(Row row:rows){JSONArray members=new JSONArray();for(HotCandidate member:row.entries){
                try(Cursor c=db.rawQuery("SELECT first_seen,text_hash FROM candidates WHERE id=?",new String[]{member.id})){
                    if(!c.moveToFirst()||!member.textHash.equals(c.getString(1)))throw new IllegalStateException("Comparison candidate changed");
                    members.put(new JSONObject().put("id",member.id).put("text_hash",member.textHash).put("first_seen_ms",c.getLong(0)));
                }
            }Set<String> aliases=new TreeSet<>();for(HotCandidate member:row.entries)aliases.addAll(EventGroups.aliases(member));events.put(new JSONObject().put("event_key",row.eventId).put("members",members).put("identity_aliases",new JSONArray(aliases)));}
            JSONObject result=new JSONObject().put("schema",1).put("holdout_qualified",false).put("learning_cutoff_ms",cutoff).put("profile_version",profile).put("source_version",source)
                .put("ledger_rows",count).put("latest_feedback_at_ms",latest).put("ledger_sha256",hash.toString()).put("active_feedback_sha256",HotCandidate.hash(readFeedback(db,cutoff).toString()))
                .put("known_learning_identities",new JSONArray(learned)).put("candidate_events",events);
            ComparisonLearning.validate(result,profile,source,now);ComparisonLearning.validatePool(result,rows);db.setTransactionSuccessful();return result;
        }catch(JSONException|java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}finally{db.endTransaction();}
    }
    private static JSONArray readFeedback(SQLiteDatabase db,long now){JSONArray rows=new JSONArray();try(Cursor c=db.rawQuery("SELECT f.id,f.action,f.scope,f.target,f.at,f.expires,f.sample,f.source FROM feedback f WHERE f.undo_of IS NULL AND (f.expires IS NULL OR f.expires>?) AND NOT EXISTS(SELECT 1 FROM feedback u WHERE u.undo_of=f.id) ORDER BY f.at,f.id",new String[]{""+now})){
        while(c.moveToNext())rows.put(new JSONObject().put("id",c.getString(0)).put("action",c.getString(1)).put("scope",c.getString(2)).put("target",c.getString(3)).put("at",c.getLong(4)).put("expires",c.isNull(5)?JSONObject.NULL:c.getLong(5)).put("sample",c.getString(6)).put("source",c.getString(7)));
    }catch(JSONException e){throw new IllegalStateException(e);}return rows;}
    public void resetProfile(){SQLiteDatabase db=db();db.beginTransaction();try{db.delete("feedback","undo_of IS NOT NULL",null);db.delete("feedback",null,null);db.execSQL("INSERT OR REPLACE INTO meta VALUES ('interest_setup_done',0)");db.execSQL("INSERT OR REPLACE INTO meta VALUES ('read_learning_after',?)",new Object[]{System.currentTimeMillis()});bump(db,"profile");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public boolean readLearningEnabled(){return scalar(db(),"SELECT COUNT(*) FROM meta WHERE key='read_learning_disabled' AND value=1")==0;}
    public boolean needsInterestSetup(long now){return scalar(db(),"SELECT value FROM meta WHERE key='interest_setup_done'")==0&&new SemanticOrder.Profile(activeFeedback(now)).empty();}
    public void finishInterestSetup(List<HotCandidate> candidates,Map<String,Boolean> choices,long now){
        if(candidates.size()>20)throw new IllegalArgumentException("Too many setup candidates");
        Map<String,HotCandidate> allowed=new HashMap<>();for(HotCandidate candidate:candidates)allowed.put(candidate.id,candidate);
        if(!allowed.keySet().containsAll(choices.keySet())||choices.containsValue(null))throw new IllegalArgumentException("Unknown setup choice");
        SQLiteDatabase db=db();db.beginTransaction();try{
            for(Map.Entry<String,Boolean> choice:choices.entrySet()){HotCandidate candidate=allowed.get(choice.getKey());feedback(UUID.randomUUID().toString(),choice.getValue()?"MORE":"LESS","sample",CandidateOrder.versionTarget(candidate),now,null,candidate.modelText(),candidate.source);}
            db.execSQL("INSERT OR REPLACE INTO meta VALUES ('interest_setup_done',1)");db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void readLearningEnabled(boolean enabled,long at){
        SQLiteDatabase db=db();db.beginTransaction();try{
            db.execSQL("INSERT OR REPLACE INTO meta VALUES ('read_learning_disabled',?)",new Object[]{enabled?0:1});
            db.execSQL("INSERT OR REPLACE INTO meta VALUES ('read_learning_after',?)",new Object[]{at});
            if(!enabled){List<String> ids=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT f.id FROM feedback f WHERE f.action IN ('READ','QUICK_EXIT') AND NOT EXISTS(SELECT 1 FROM feedback u WHERE u.undo_of=f.id)",null)){while(c.moveToNext())ids.add(c.getString(0));}for(String id:ids)undo(id,at);}
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public boolean recordRead(HotCandidate candidate,long at){
        recordConsumed(candidate,at);
        return recordReading(candidate,at,false);
    }
    /** Display history is separate from interest learning and does not change its version. */
    public int recordExposed(long snapshot,Collection<String> events,long at){return FeedExposureHistory.exposed(db(),snapshot,events,at);}
    public void recordConsumed(HotCandidate candidate,long at){FeedExposureHistory.consumed(db(),candidate,at);}
    public Map<String,FeedRefreshOrder.Recent> recentExposure(Collection<Row> rows,long now){return FeedExposureHistory.recent(db(),rows,now);}
    public List<HotCandidate> recordExposedMembers(long snapshot,Collection<String> events,long at){Map<String,HotCandidate> members=new LinkedHashMap<>();FeedExposureHistory.exposed(db(),snapshot,events,at,members);return new ArrayList<>(members.values());}
    public boolean currentCandidate(HotCandidate expected){try(Cursor c=db().rawQuery("SELECT c.payload FROM candidates c JOIN sources s ON s.id=c.source WHERE c.id=? AND c.text_hash=? AND c.active=1 AND s.enabled=1",new String[]{expected.id,expected.textHash})){
        if(!c.moveToFirst())return false;HotCandidate actual=parse(c.getString(0));return !actual.video&&ContentLanguage.eligible(actual.title,actual.snippet,actual.bodyLanguage);
    }}
    public boolean recordQuickExit(HotCandidate candidate,long at){return recordReading(candidate,at,true);}
    private boolean recordReading(HotCandidate candidate,long at,boolean quickExit){
        SQLiteDatabase db=db();db.beginTransaction();try{
            if(!readLearningEnabled()||at<=scalar(db,"SELECT value FROM meta WHERE key='read_learning_after'"))return false;
            String action=quickExit?"QUICK_EXIT":"READ",target=(quickExit?"quick:":"read:")+candidate.id+":"+candidate.textHash;
            // An undone/expired read is not silently revived by reopening the same version.
            if(scalar(db,"SELECT COUNT(*) FROM feedback WHERE action=? AND target=?",action,target)>0)return false;
            boolean added=feedback(UUID.randomUUID().toString(),action,"sample",target,at,at+7L*86_400_000,candidate.modelText(),candidate.source);
            db.setTransactionSuccessful();return added;
        }finally{db.endTransaction();}
    }
    public static String vectorKey(String textHash,String model,String tokenizer){return HotCandidate.hash(model+"\n"+tokenizer+"\n"+HotCandidate.NORMALIZATION+"\n"+textHash);}
    public void putVector(String textHash,String model,String tokenizer,float[] vector,long at){
        if(vector.length!=512)throw new IllegalArgumentException("Wrong embedding dimensions");double norm=0;for(float x:vector){if(!Float.isFinite(x))throw new IllegalArgumentException("Invalid embedding");norm+=(double)x*x;}if(Math.abs(norm-1)>0.001)throw new IllegalArgumentException("Embedding is not normalized");
        ByteBuffer bytes=ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN);for(float x:vector)bytes.putFloat(x);
        ContentValues v=new ContentValues();v.put("key",vectorKey(textHash,model,tokenizer));v.put("text_hash",textHash);v.put("model",model);v.put("tokenizer",tokenizer);v.put("normalizer",HotCandidate.NORMALIZATION);v.put("vector",bytes.array());v.put("used_at",at);
        SQLiteDatabase db=db();db.beginTransaction();try{if(db.insertWithOnConflict("embeddings",null,v,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new SQLiteException("Embedding cache commit failed");db.execSQL("DELETE FROM embeddings WHERE key IN (SELECT key FROM embeddings ORDER BY used_at DESC,key LIMIT -1 OFFSET 5000)");db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public float[] vector(String key,long at){SQLiteDatabase db=db();try(Cursor c=db.rawQuery("SELECT vector FROM embeddings WHERE key=?",new String[]{key})){
        if(!c.moveToFirst())return null;byte[] bytes=c.getType(0)==Cursor.FIELD_TYPE_BLOB?c.getBlob(0):null;if(bytes==null||bytes.length!=2048){db.delete("embeddings","key=?",new String[]{key});return null;}ByteBuffer buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);float[] out=new float[512];double norm=0;for(int i=0;i<out.length;i++){out[i]=buffer.getFloat();norm+=(double)out[i]*out[i];}
        if(!Double.isFinite(norm)||Math.abs(norm-1)>0.001){db.delete("embeddings","key=?",new String[]{key});return null;}
        ContentValues values=new ContentValues();values.put("used_at",at);db.update("embeddings",values,"key=?",new String[]{key});return out;
    }}
    public static final class Row {
        public static final String BAND_VERSION="semantic-category-v1";
        public final String eventId,reason;public final HotCandidate candidate;
        /** -1 is unclassified legacy/base order; 0/1/2 are semantic confidence bands. */
        public final int interestBand;
        public final int refreshBasePosition;
        public final List<HotCandidate> entries;
        public Row(String eventId,HotCandidate candidate,String reason){this(eventId,candidate,reason,Collections.singletonList(candidate));}
        public Row(String eventId,HotCandidate candidate,String reason,List<HotCandidate> entries){this(eventId,candidate,reason,entries,-1);}
        public Row(String eventId,HotCandidate candidate,String reason,List<HotCandidate> entries,int interestBand){this(eventId,candidate,reason,entries,interestBand,-1);}
        public Row(String eventId,HotCandidate candidate,String reason,List<HotCandidate> entries,int interestBand,int refreshBasePosition){if(interestBand< -1||interestBand>2||refreshBasePosition< -1||refreshBasePosition>=5000)throw new IllegalArgumentException("Invalid feed ordering metadata");this.eventId=eventId;this.candidate=candidate;this.reason=reason;this.entries=Collections.unmodifiableList(new ArrayList<>(entries));this.interestBand=interestBand;this.refreshBasePosition=refreshBasePosition;}
        public Row withRefreshBasePosition(int position){return new Row(eventId,candidate,reason,entries,interestBand,position);}
        public Row withInterestBand(int value){return new Row(eventId,candidate,reason,entries,value);}
        JSONObject payload(){JSONObject value=candidate.json();JSONArray sources=new JSONArray();for(HotCandidate entry:entries)sources.put(entry.json());try{value.put("groupEntries",sources);if(interestBand>=0)value.put("interestBandVersion",BAND_VERSION).put("interestBand",interestBand);if(refreshBasePosition>=0)value.put("refreshOrderVersion",1).put("refreshBasePosition",refreshBasePosition);return value;}catch(JSONException ex){throw new IllegalStateException(ex);}}
        static Row read(String event,String payload,String reason){try{JSONObject value=new JSONObject(payload);HotCandidate main=HotCandidate.fromJson(value);JSONArray sources=value.optJSONArray("groupEntries");List<HotCandidate> entries=new ArrayList<>();if(sources!=null)for(int n=0;n<sources.length();n++)entries.add(HotCandidate.fromJson(sources.getJSONObject(n)));if(entries.isEmpty())entries.add(main);Object band=value.opt("interestBand");int typed=-1;if(BAND_VERSION.equals(value.optString("interestBandVersion"))&&band instanceof Number){double number=((Number)band).doubleValue();if(number>=0&&number<=2&&number==Math.rint(number))typed=(int)number;}Object position=value.opt("refreshBasePosition");int base=-1;if(value.optInt("refreshOrderVersion")==1&&position instanceof Number){double number=((Number)position).doubleValue();if(number>=0&&number<5000&&number==Math.rint(number))base=(int)number;}return new Row(event,main,reason,entries,typed,base);}catch(JSONException ex){throw new IllegalStateException(ex);}}
    }
    /** -1 means inputs changed while inference was running. No stale feed is published. */
    public long commitRanking(long sourceVersion,long profileVersion,long at,String mode,String filter,List<Row> rows){
        if(rows.size()>5000)throw new IllegalArgumentException("Feed too large");SQLiteDatabase db=db();db.beginTransaction();try{
            expireFeedback(db,at);
            if(scalar(db,"SELECT value FROM meta WHERE key='sources'")!=sourceVersion||scalar(db,"SELECT value FROM meta WHERE key='profile'")!=profileVersion){db.setTransactionSuccessful();return -1;}
            ContentValues v=new ContentValues();v.put("source_version",sourceVersion);v.put("profile_version",profileVersion);v.put("created_at",at);v.put("mode",mode);v.put("filter",filter);long id=db.insertOrThrow("snapshots",null,v);
            int position=0;for(Row row:rows){ContentValues item=new ContentValues();item.put("snapshot",id);item.put("position",position++);item.put("event_id",row.eventId);item.put("payload",row.payload().toString());item.put("reason",HotCandidate.clean(row.reason,120));db.insertOrThrow("snapshot_rows",null,item);}
            long active=scalar(db,"SELECT value FROM meta WHERE key='active_snapshot'");
            if(active>0)db.execSQL("DELETE FROM snapshots WHERE id<>? AND id NOT IN (SELECT id FROM snapshots ORDER BY id DESC LIMIT 2)",new Object[]{active});
            else db.execSQL("DELETE FROM snapshots WHERE id NOT IN (SELECT id FROM snapshots ORDER BY id DESC LIMIT 3)");db.setTransactionSuccessful();return id;
        }finally{db.endTransaction();}
    }
    public List<Row> readSnapshot(long snapshot,int limit,int offset){if(limit<1||limit>200||offset<0)throw new IllegalArgumentException("Invalid feed page");List<Row> rows=new ArrayList<>();try(Cursor c=db().rawQuery("SELECT event_id,payload,reason FROM snapshot_rows WHERE snapshot=? ORDER BY position LIMIT ? OFFSET ?",new String[]{""+snapshot,""+limit,""+offset})){while(c.moveToNext())rows.add(Row.read(c.getString(0),c.getString(1),c.getString(2)));}return rows;}
    /** Explicit user presentation only. No preference/model changes and no activation.
     * -1 requests a fresh ranking when any saved context/member is no longer valid. */
    public long refreshSnapshot(long id,String filter,long now){
        return refreshSnapshot(id,filter,now,null);
    }
    public interface ProgressLookup {long progress(Row row,FeedRefreshOrder.Recent recent,long now);}
    public long refreshSnapshot(long id,String filter,long now,ProgressLookup progress){
        SQLiteDatabase db=db();db.beginTransaction();try{
            JSONObject info=snapshotInfo(id),profile=profile(now);
            if(info==null||!SourceFilter.normalize(filter).equals(info.optString("filter"))||info.optLong("sourceVersion")!=version("sources")||info.optLong("profileVersion")!=profile.optLong("version")){db.setTransactionSuccessful();return -1;}
            List<Row> original=new ArrayList<>();int count=info.optInt("count");for(int start=0;start<count;start+=200)original.addAll(readSnapshot(id,200,start));
            Map<String,HotCandidate> allowed=new HashMap<>();for(Row row:CandidateOrder.forModels(candidates(5000,0),profile.getJSONArray("feedback")))for(HotCandidate member:row.entries)allowed.put(member.id,member);
            for(Row row:original)for(HotCandidate member:row.entries){HotCandidate current=allowed.get(member.id);if(current==null||!current.json().toString().equals(member.json().toString())){db.setTransactionSuccessful();return -1;}}
            if("newest".equals(info.optString("mode"))){db.setTransactionSuccessful();return id;}
            Set<Integer> positions=new HashSet<>();boolean complete=true;for(Row row:original)if(row.refreshBasePosition<0||row.refreshBasePosition>=original.size()||!positions.add(row.refreshBasePosition)){complete=false;break;}
            List<Row> baseline=new ArrayList<>();for(int n=0;n<original.size();n++)baseline.add(complete?original.get(n):original.get(n).withRefreshBasePosition(n));
            Map<String,FeedRefreshOrder.Recent> recent=new HashMap<>(recentExposure(baseline,now));
            if(progress!=null){int lookups=0;for(Row row:baseline){FeedRefreshOrder.Recent seen=recent.get(row.eventId);if(seen==null||++lookups>128)continue;
                try{long proof=progress.progress(row,seen,now);if(proof>Math.max(seen.shownAt,seen.readAt)&&proof<=now)recent.put(row.eventId,new FeedRefreshOrder.Recent(seen.shownAt,seen.readAt,proof));}catch(RuntimeException unavailable){/* Optional derivative evidence cannot interrupt refresh. */}
            }}
            List<Row> ordered=FeedRefreshOrder.forRows(baseline,recent,now);
            boolean changed=false;for(int n=0;n<original.size();n++)if(!original.get(n).eventId.equals(ordered.get(n).eventId)){changed=true;break;}
            long result=changed?commitRanking(info.optLong("sourceVersion"),profile.getLong("version"),now,info.optString("mode"),filter,ordered):id;
            db.setTransactionSuccessful();return result;
        }catch(JSONException failure){throw new IllegalStateException(failure);}finally{db.endTransaction();}
    }
    public long latestSnapshot(){return scalar(db(),"SELECT MAX(id) FROM snapshots");}
    public long activeSnapshot(){long id=scalar(db(),"SELECT value FROM meta WHERE key='active_snapshot'");return scalar(db(),"SELECT COUNT(*) FROM snapshots WHERE id=?",""+id)>0?id:latestSnapshot();}
    public void activateSnapshot(long id){SQLiteDatabase db=db();if(scalar(db,"SELECT COUNT(*) FROM snapshots WHERE id=?",""+id)==0)throw new IllegalArgumentException("Snapshot absent");db.execSQL("INSERT OR REPLACE INTO meta VALUES ('active_snapshot',?)",new Object[]{id});}
    public JSONObject snapshotInfo(long id){try(Cursor c=db().rawQuery("SELECT mode,filter,anchor,offset,created_at,source_version,profile_version FROM snapshots WHERE id=?",new String[]{""+id})){
        if(!c.moveToFirst())return null;
        return new JSONObject().put("id",id).put("mode",c.getString(0)).put("filter",c.getString(1)).put("anchor",c.getString(2)).put("offset",c.getInt(3)).put("createdAt",c.getLong(4)).put("sourceVersion",c.getLong(5)).put("profileVersion",c.getLong(6)).put("count",scalar(db(),"SELECT COUNT(*) FROM snapshot_rows WHERE snapshot=?",""+id)).put("position",anchorPosition(id,c.getString(2)));
    }catch(JSONException ex){throw new IllegalStateException(ex);}}
    /** Called once by the foreground owner, before creating any new collection jobs. */
    public void recoverInterruptedRefreshes(){db().execSQL("UPDATE sources SET requested=requested+1,status=CASE WHEN success_at>0 THEN 'STALE' ELSE 'EMPTY' END,error='' WHERE status='REFRESHING'");}
    private int anchorPosition(long snapshot,String anchor){
        if(anchor==null||anchor.isEmpty())return 0;
        try(Cursor exact=db().rawQuery("SELECT position FROM snapshot_rows WHERE snapshot=? AND event_id=?",new String[]{""+snapshot,anchor})){if(exact.moveToFirst())return exact.getInt(0);}
        try(Cursor rows=db().rawQuery("SELECT position,event_id,payload,reason FROM snapshot_rows WHERE snapshot=? ORDER BY position",new String[]{""+snapshot})){
            while(rows.moveToNext())if(EventGroups.matches(Row.read(rows.getString(1),rows.getString(2),rows.getString(3)),anchor))return rows.getInt(0);
        }return 0;
    }
    public void anchor(long snapshot,String eventId,int offset){ContentValues v=new ContentValues();v.put("anchor",eventId);v.put("offset",offset);db().update("snapshots",v,"id=?",new String[]{""+snapshot});}
    public void clearDerivedCache(){SQLiteDatabase db=db();db.beginTransaction();try{db.delete("snapshots",null,null);db.delete("embeddings",null,null);db.delete("reranks",null,null);db.delete("candidates",null,null);db.delete("article_progress",null,null);db.execSQL("UPDATE sources SET requested=requested+1,status='EMPTY',error='',success_at=0");bump(db,"sources");db.setTransactionSuccessful();}finally{db.endTransaction();}}
    /** Explicit user cleanup only. VACUUM is intentionally never part of scrolling or inference. */
    public boolean reclaimCacheSpace(){
        SQLiteDatabase db=db();if(db.inTransaction())throw new IllegalStateException("Compaction requires a committed transaction");
        try{
            if(!checkpoint(db))return false;
            db.execSQL("VACUUM");
            return checkpoint(db);
        }catch(SQLiteException unavailable){return false;}
    }
    private static boolean checkpoint(SQLiteDatabase db){
        try(Cursor result=db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)",null)){return result.moveToFirst()&&result.getInt(0)==0;}
    }
}
