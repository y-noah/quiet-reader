package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;

/** Captures evidence for the requested snapshot, including newly pending members.
 * Invoked on the storage worker; only possible evidence returns to UI for scopes. */
final class ArticleProgressPreparation {
    final Map<String,HotCandidate> candidates=new LinkedHashMap<>();
    List<ArticleFollowupEvidence.Proof> followups=Collections.emptyList();
    static ArticleProgressPreparation read(FeedStore store,long snapshot,long now)throws Exception{
        ArticleProgressPreparation result=new ArticleProgressPreparation();
        Map<String,HotCandidate> eligible=new LinkedHashMap<>();List<FeedStore.Row> rows=new ArrayList<>();
        for(int start=0;start<5000&&eligible.size()<128;start+=200){
            List<FeedStore.Row> page=store.readSnapshot(snapshot,200,start);rows.addAll(page);
            for(FeedStore.Row row:page)for(HotCandidate member:row.entries)if(eligible.size()<128&&!member.video&&ArticleProgressBinding.supported(member.source))eligible.putIfAbsent(member.id,member);
            if(page.size()<200)break;
        }
        ArticleProgressHistory history=new ArticleProgressHistory(store.getWritableDatabase());
        Set<String> needed=new HashSet<>(history.potential(eligible.keySet(),now));
        result.followups=new ArticleFollowupEvidence(store.getWritableDatabase(),now).find(rows,eligible.keySet());
        for(ArticleFollowupEvidence.Proof proof:result.followups){needed.add(proof.beforeIdentity);needed.add(proof.afterIdentity);}
        for(String identity:needed)result.candidates.put(identity,eligible.get(identity));
        return result;
    }
}
