package app.quietreader.discovery;

import app.quietreader.Models.Source;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FeedReadinessTest {
    private List<FeedStore.Row> pool(int count){
        List<FeedStore.Row> rows=new ArrayList<>();for(int n=0;n<count;n++){
            HotCandidate c=new HotCandidate(Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_"+n,"新闻"+n,"","",false,n+1,null,1000);
            rows.add(new FeedStore.Row(c.id,c,"",List.of(c),0));
        }return rows;
    }
    @Test public void lowerRankReadyBodiesCannotBypassAnUnattemptedHigherRank(){
        FeedReadiness ledger=new FeedReadiness();List<FeedStore.Row> rows=pool(30);
        for(int n=1;n<30;n++)ledger.persisted(FeedReadiness.key(rows.get(n).candidate),"account",1000);
        List<FeedStore.Row> target=ledger.targets(rows,c->"account",2000);
        assertEquals(15,target.size());assertSame(rows.get(0),target.get(0));assertFalse(ledger.complete(target,c->"account",2000));
        ledger.persisted(FeedReadiness.key(rows.get(0).candidate),"account",1000);
        assertTrue(ledger.complete(target,c->"account",2000));
    }
    @Test public void actualFailureAllowsNextRankButExpiresWithoutPermanentPlatformPenalty(){
        FeedReadiness ledger=new FeedReadiness();List<FeedStore.Row> rows=pool(20);
        ledger.failed(FeedReadiness.key(rows.get(0).candidate),"account",1000);
        assertSame(rows.get(1),ledger.targets(rows,c->"account",2000).get(0));
        assertSame(rows.get(0),ledger.targets(rows,c->"other-account",2000).get(0));
        assertSame(rows.get(0),ledger.targets(rows,c->"account",1001+FeedReadiness.RETRY_MS).get(0));
    }
    @Test public void metadataAccountNamespaceAndOriginalFreshnessRemainRequired(){
        FeedReadiness ledger=new FeedReadiness();HotCandidate candidate=pool(1).get(0).candidate;
        ledger.persisted(FeedReadiness.key(candidate),"namespace/account",1000);
        assertTrue(ledger.ready(candidate,"namespace/account",2000));
        assertFalse(ledger.ready(candidate,"new-namespace/account",2000));assertFalse(ledger.ready(candidate,"namespace/new-account",2000));
        assertFalse(ledger.ready(candidate,"namespace/account",999));assertFalse(ledger.ready(candidate,"namespace/account",1001+FeedReadiness.FRESH_MS));
        HotCandidate changed=new HotCandidate(candidate.source,candidate.originalUrl,"changed",candidate.snippet,"",false,1,null,1000);
        assertFalse(ledger.ready(changed,"namespace/account",2000));
        ledger.forget(candidate,"wrong");assertTrue(ledger.ready(candidate,"namespace/account",2000));
        ledger.forget(candidate,"namespace/account");assertFalse(ledger.ready(candidate,"namespace/account",2000));
    }
    @Test public void offlineBodyProofUsesBoundedOriginalAgeWithoutRenewingOnlineFreshness(){
        FeedReadiness ledger=new FeedReadiness();HotCandidate candidate=pool(1).get(0).candidate;long at=1000,now=at+20*60_000;
        ledger.persisted(FeedReadiness.key(candidate),"account",at);
        assertFalse(ledger.ready(candidate,"account",now));
        assertTrue(ledger.ready(candidate,"account",now,7L*24*60*60_000));
        assertFalse(ledger.ready(candidate,"account",at+7L*24*60*60_000+1,7L*24*60*60_000));
        assertFalse(ledger.ready(candidate,"new-account",now,7L*24*60*60_000));
    }
    @Test public void exhaustionAndAnEmptyPoolNeverInventAReadyScreen(){
        FeedReadiness ledger=new FeedReadiness();assertFalse(ledger.complete(List.of(),c->"account",1000));
        List<FeedStore.Row> rows=pool(2);ledger.persisted(FeedReadiness.key(rows.get(0).candidate),"account",1000);
        ledger.failed(FeedReadiness.key(rows.get(1).candidate),"account",1000);
        List<FeedStore.Row> target=ledger.targets(rows,c->"account",1001);
        assertEquals(1,target.size());assertTrue(ledger.complete(target,c->"account",1001));
    }
}
