package app.quietreader;

import app.quietreader.discovery.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class RecommendationComparisonTest {
    private FeedStore.Row row(String id){return new FeedStore.Row(id,new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/123","文字讨论样本","摘要","",false,1,null,100),"");}
    private RecommendationComparison capture(List<FeedStore.Row> a,List<FeedStore.Row> b,List<FeedStore.Row> c){return new RecommendationComparison(a,b,c,"model-v1","profile-v1",3,4,5,2);}
    @Test public void frozenOrdersRetainProvenanceAndRejectMutation(){
        FeedStore.Row a=row("a"),b=row("b");List<FeedStore.Row> input=new ArrayList<>(Arrays.asList(a,b));RecommendationComparison value=capture(input,Arrays.asList(b,a),input);input.clear();
        assertEquals(2,value.neutral.size());assertEquals("b",value.embedding.get(0).eventId);assertEquals(4,value.profileVersion);assertEquals(5,value.sourceVersion);assertEquals(3,value.generation);assertEquals(2,value.finiteRerankScores);
        assertThrows(UnsupportedOperationException.class,()->value.dual.clear());
    }
    @Test public void rejectsMissingDuplicatedOrDifferentEvents(){
        FeedStore.Row a=row("a"),b=row("b");assertThrows(IllegalArgumentException.class,()->capture(List.of(a,b),List.of(a),List.of(a,b)));
        assertThrows(IllegalArgumentException.class,()->capture(List.of(a,a),List.of(a,a),List.of(a,a)));
        assertThrows(IllegalArgumentException.class,()->capture(List.of(a),List.of(b),List.of(a)));
        assertThrows(IllegalArgumentException.class,()->capture(List.of(),List.of(),List.of()));
    }
    @Test public void rejectsMissingModelProvenance(){
        List<FeedStore.Row> rows=List.of(row("a"));assertThrows(IllegalArgumentException.class,()->new RecommendationComparison(rows,rows,rows,"","profile",1,1,1,0));
        assertThrows(IllegalArgumentException.class,()->new RecommendationComparison(rows,rows,rows,"revision","",1,1,1,0));
    }
}
