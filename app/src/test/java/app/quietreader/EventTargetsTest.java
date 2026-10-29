package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class EventTargetsTest {
    private HotCandidate candidate(Models.Source source,int n,String suffix){return new HotCandidate(source,"https://example.org/item/"+n,"空间站完成新的科学实验验证"+suffix,"乘组完成了一项新的科学实验，将观测结果传回地面研究团队进行分析。"+suffix,"",false,n,1000L,1);}
    private JSONArray event(String action,String target)throws Exception{return new JSONArray().put(new JSONObject().put("action",action).put("scope","event").put("target",target));}
    @Test public void capturedBlockCoversKnownUrlsAfterMetadataChangesAndNewUnchangedDuplicates()throws Exception{
        HotCandidate a=candidate(Models.Source.ZHIHU,1,""),b=candidate(Models.Source.WEIBO,2,"");FeedStore.Row group=CandidateOrder.arrange(List.of(a,b),new JSONArray(),false).get(0);
        String target=EventTargets.capture(group,false);assertTrue(EventTargets.structuredTarget(target));assertEquals(3,EventTargets.expand(target).size());
        assertTrue(CandidateOrder.arrange(List.of(candidate(Models.Source.ZHIHU,1,"有更新"),candidate(Models.Source.WEIBO,2,"有更新"),candidate(Models.Source.V2EX,3,"")),event("BLOCK",target),false).isEmpty());
        HotCandidate other=candidate(Models.Source.ZHIHU,4,"独立研究");assertEquals(other.id,CandidateOrder.arrange(List.of(a,b,other),event("BLOCK",target),false).get(0).candidate.id);
    }
    @Test public void enoughIsVersionedAndSingleUndoRestoresWholeCapturedScope()throws Exception{
        HotCandidate a=candidate(Models.Source.ZHIHU,1,""),b=candidate(Models.Source.WEIBO,2,"");FeedStore.Row group=CandidateOrder.arrange(List.of(a,b),new JSONArray(),false).get(0);String target=EventTargets.capture(group,true);
        assertTrue(CandidateOrder.arrange(List.of(a,b),event("ENOUGH",target),false).isEmpty());
        List<FeedStore.Row> changed=CandidateOrder.arrange(List.of(candidate(Models.Source.ZHIHU,1,"实质更新"),b),event("ENOUGH",target),false);assertEquals(1,changed.size());assertEquals(a.id,changed.get(0).candidate.id);
        assertEquals(2,CandidateOrder.arrange(List.of(a,b),new JSONArray(),false).get(0).entries.size());
    }
    @Test public void singletonEvidenceGroupAlsoRetainsMemberIdentity()throws Exception{
        HotCandidate a=candidate(Models.Source.ZHIHU,1,"");FeedStore.Row group=CandidateOrder.arrange(List.of(a),new JSONArray(),false).get(0);
        assertTrue(CandidateOrder.arrange(List.of(candidate(Models.Source.ZHIHU,1,"更新")),event("BLOCK",EventTargets.capture(group,false)),false).isEmpty());
    }
    @Test public void legacyAndMalformedStructuredTargetsDoNotInventAdditionalScope()throws Exception{
        HotCandidate a=candidate(Models.Source.ZHIHU,1,"");assertEquals(Set.of(a.id),EventTargets.expand(a.id));assertEquals(Set.of(CandidateOrder.versionTarget(a)),EventTargets.expand(CandidateOrder.versionTarget(a)));
        for(String bad:List.of("{broken","{\"format\":\"future\",\"targets\":[\""+a.id+"\"]}","{\"format\":\"event-targets-v1\",\"targets\":[\""+a.id+"\",\"ZHIHU\"]}")){assertFalse(EventTargets.structuredTarget(bad));assertFalse(EventTargets.expand(bad).contains(a.id));assertEquals(1,CandidateOrder.arrange(List.of(a),event("BLOCK",bad),false).size());}
    }
}
