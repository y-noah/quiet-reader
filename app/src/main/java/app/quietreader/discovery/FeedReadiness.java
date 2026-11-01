package app.quietreader.discovery;

import java.util.*;
import java.util.function.Function;

/** Proofs of persisted, verified bodies; a scheduled download is never a proof. */
public final class FeedReadiness {
    public static final long FRESH_MS=10*60_000L,RETRY_MS=5*60_000L;
    private static final class Proof {
        final String scope;final long at;final boolean ready;
        Proof(String scope,long at,boolean ready){this.scope=scope;this.at=at;this.ready=ready;}
    }
    private final LinkedHashMap<String,Proof> proofs=new LinkedHashMap<>();
    public static String key(HotCandidate candidate){return candidate.id+"@"+candidate.textHash;}
    private void put(String key,Proof proof){proofs.remove(key);proofs.put(key,proof);while(proofs.size()>512)proofs.remove(proofs.keySet().iterator().next());}
    public void persisted(String key,String scope,long acquiredAt){put(key,new Proof(scope,acquiredAt,true));}
    public void failed(String key,String scope,long at){put(key,new Proof(scope,at,false));}
    private boolean matches(Proof proof,String scope,long now,long ttl){return proof!=null&&proof.scope.equals(scope)&&now>=proof.at&&now-proof.at<=ttl;}
    public boolean ready(HotCandidate candidate,String scope,long now){return ready(candidate,scope,now,FRESH_MS);}
    public boolean ready(HotCandidate candidate,String scope,long now,long maxAge){Proof proof=proofs.get(key(candidate));return matches(proof,scope,now,maxAge)&&proof.ready;}
    public boolean hasFailure(HotCandidate candidate,long now){Proof proof=proofs.get(key(candidate));return proof!=null&&!proof.ready&&now>=proof.at&&now-proof.at<=RETRY_MS;}
    public boolean failed(HotCandidate candidate,String scope,long now){Proof proof=proofs.get(key(candidate));return matches(proof,scope,now,RETRY_MS)&&!proof.ready;}
    /** Preserve ranking. Only a verified acquisition failure may be skipped. */
    public List<FeedStore.Row> targets(List<FeedStore.Row> ranked,Function<HotCandidate,String> scope,long now){
        List<FeedStore.Row> result=new ArrayList<>();
        for(FeedStore.Row row:ranked){if(failed(row.candidate,scope.apply(row.candidate),now))continue;result.add(row);if(result.size()==FeedBatch.SIZE)break;}
        return result;
    }
    public boolean complete(List<FeedStore.Row> target,Function<HotCandidate,String> scope,long now){
        return complete(target,scope,now,FRESH_MS);
    }
    public boolean complete(List<FeedStore.Row> target,Function<HotCandidate,String> scope,long now,long maxAge){
        if(target.isEmpty())return false;
        // Explicitly opted-in video cards open on demand; do not require or prefetch a video body.
        for(FeedStore.Row row:target)if(!HotSourceCatalog.optionalVideo(row.candidate)&&!ready(row.candidate,scope.apply(row.candidate),now,maxAge))return false;
        return true;
    }
    public void forget(HotCandidate candidate,String scope){Proof proof=proofs.get(key(candidate));if(proof!=null&&proof.scope.equals(scope))proofs.remove(key(candidate));}
    public void clear(){proofs.clear();}
}
