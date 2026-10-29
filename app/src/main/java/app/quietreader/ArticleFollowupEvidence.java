package app.quietreader;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import app.quietreader.discovery.*;
import java.util.*;
import static app.quietreader.ArticleProgressEvidence.*;

/** Worker-only bounded comparisons of stored bodies; never fetches or runs a model.
 * The representative must itself contain the update, so a promoted card opens it. */
final class ArticleFollowupEvidence {
    static final int MAX_PAIRS=16,MAX_DECODE_BYTES=1024*1024;
    static final class Proof {
        final String event,beforeIdentity,beforeScope,afterIdentity,afterScope,beforeHash,afterHash;
        final long at,beforePresented,afterPresented;
        Proof(String event,Body before,Body after,long at){this.event=event;beforeIdentity=before.snapshot.identity;beforeScope=before.snapshot.scope;afterIdentity=after.snapshot.identity;afterScope=after.snapshot.scope;beforeHash=before.hash;afterHash=after.hash;beforePresented=before.presented;afterPresented=after.presented;this.at=at;}
        boolean belongs(FeedStore.Row row){return event.equals(row.eventId)&&afterIdentity.equals(row.candidate.id)&&row.entries.stream().anyMatch(member->member.id.equals(beforeIdentity))&&row.entries.stream().anyMatch(member->member.id.equals(afterIdentity));}
        long current(SQLiteDatabase db,FeedStore.Row row,Map<String,ArticleProgressBinding.Scope> scopes,long cutoff){
            if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Follow-up validation must run off UI");
            if(at<=cutoff||!belongs(row))return 0;
            ArticleProgressBinding.Scope before=scopes.get(beforeIdentity),after=scopes.get(afterIdentity);
            return before!=null&&after!=null&&beforeScope.equals(before.key)&&afterScope.equals(after.key)&&before.current()&&after.current()&&unchanged(db,beforeIdentity,beforeScope,"baseline",beforeHash,beforePresented)&&unchanged(db,afterIdentity,afterScope,"latest",afterHash,afterPresented)?at:0;
        }
        private boolean unchanged(SQLiteDatabase db,String identity,String scope,String column,String hash,long presented){try(Cursor c=db.rawQuery("SELECT "+column+",presented_at FROM article_progress WHERE key=? AND format=?",new String[]{identity+":"+scope,VERSION})){if(!c.moveToFirst()||c.getLong(1)!=presented)return false;String raw=c.getString(0);return raw.length()<=ArticleProgressHistory.MAX_PAYLOAD&&hash.equals(HotCandidate.hash(raw));}}
    }
    private static final class Body {final Snapshot snapshot;final long presented;final String hash;Body(Snapshot snapshot,long presented,String raw){this.snapshot=snapshot;this.presented=presented;hash=HotCandidate.hash(raw);}}
    private final SQLiteDatabase db;private final long now;private int bytes,pairs;
    private final Map<String,Body> baselines=new HashMap<>(),latest=new HashMap<>();
    ArticleFollowupEvidence(SQLiteDatabase db,long now){this.db=db;this.now=now;}
    private Body body(String identity,boolean baseline){
        Map<String,Body> cache=baseline?baselines:latest;if(cache.containsKey(identity))return cache.get(identity);Body result=null;
        // Only the newest retained account version is considered. A stale account
        // must not become a fallback when its current scope later fails validation.
        String payload=baseline?"baseline":"latest",order=baseline?"presented_at":"latest_at";
        try(Cursor c=db.rawQuery("SELECT scope,"+payload+",presented_at,latest_at FROM article_progress WHERE identity=? AND format=? AND touched_at BETWEEN ? AND ? AND latest_at<=? AND presented_at<=? AND "+payload+"<>'' ORDER BY "+order+" DESC,key LIMIT 1",new String[]{identity,VERSION,""+Math.max(0,now-ArticleProgressHistory.TTL_MS),""+now,""+now,""+now})){
            if(c.moveToFirst()){
                String raw=c.getString(1);if(raw.length()<=MAX_DECODE_BYTES-bytes){bytes+=raw.length();Snapshot value=ArticleProgressHistory.decode(raw,now);
                    long presented=c.getLong(2);if(value!=null&&value.identity.equals(identity)&&value.scope.equals(c.getString(0))&&(baseline?presented>0&&value.acquiredAt<=presented:value.acquiredAt==c.getLong(3)))result=new Body(value,presented,raw);
                }
            }
        }
        cache.put(identity,result);return result;
    }
    List<Proof> find(List<FeedStore.Row> rows,Set<String> eligible){
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("Follow-up comparison must run off UI");
        List<Proof> result=new ArrayList<>();
        for(FeedStore.Row row:rows){
            if(row.entries.size()<2||!eligible.contains(row.candidate.id))continue;
            Body after=body(row.candidate.id,false);if(after==null)continue;
            for(HotCandidate member:row.entries){
                if(member.id.equals(row.candidate.id)||!eligible.contains(member.id))continue;
                if(pairs>=MAX_PAIRS||bytes>=MAX_DECODE_BYTES)return Collections.unmodifiableList(result);
                pairs++;Body before=body(member.id,true);if(before==null)continue;
                long proof=verifiedFollowup(before.snapshot,after.snapshot,Math.max(before.presented,after.presented),now);
                if(proof>0){result.add(new Proof(row.eventId,before,after,proof));break;}
            }
        }
        return Collections.unmodifiableList(result);
    }
    int decodedBytes(){return bytes;}
    int comparedPairs(){return pairs;}
}
