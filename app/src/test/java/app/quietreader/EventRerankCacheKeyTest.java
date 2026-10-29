package app.quietreader;

import app.quietreader.discovery.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventRerankCacheKeyTest {
    private HotCandidate candidate(Models.Source source,String url,String title,long at){return new HotCandidate(source,url,title,"","",false,1,null,at);}
    private EventVerificationPlan.Task task(HotCandidate a,HotCandidate b,String revision){float[] v=new float[512];v[0]=1;Map<String,float[]> vectors=new HashMap<>();vectors.put(SemanticOrder.key(a.modelText()),v);vectors.put(SemanticOrder.key(b.modelText()),v);return EventVerificationPlan.create(Arrays.asList(a,b),vectors,revision).get(0);}
    @Test public void directionsModelAndExactTextAreIndependentOfEndpointIdsAndFetchTimes(){
        HotCandidate a=candidate(Models.Source.ZHIHU,"https://example.org/a","王沪宁陪同朝鲜最高领导人金正恩访问丹东",1000),b=candidate(Models.Source.WEIBO,"https://example.org/b","王沪宁陪同金正恩访问丹东",1000);EventVerificationPlan.Task first=task(a,b,"v1");
        String forward=EventRerankCacheKey.of(first,false),backward=EventRerankCacheKey.of(first,true);assertNotEquals(forward,backward);
        HotCandidate c=candidate(Models.Source.TIEBA,"https://example.org/c",a.title,1001),d=candidate(Models.Source.BAIDU,"https://example.org/d",b.title,1001);EventVerificationPlan.Task copy=task(c,d,"v1");
        assertEquals(Set.of(forward,backward),Set.of(EventRerankCacheKey.of(copy,false),EventRerankCacheKey.of(copy,true)));
        assertNotEquals(forward,EventRerankCacheKey.of(task(a,b,"v2"),false));
        HotCandidate changed=candidate(b.source,b.originalUrl,"王沪宁陪同金正恩到访丹东",1000);assertNotEquals(forward,EventRerankCacheKey.of(task(a,changed,"v1"),false));
    }
}
