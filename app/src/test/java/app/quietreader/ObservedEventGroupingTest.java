package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

/** Fixed observed metadata, developer claim labels, no personal-interest labels.
 * Measures the current algorithm rather than declaring missing semantic recall solved. */
public class ObservedEventGroupingTest {
    @Test public void observedPairsRetainDistinctFactsAndExposeUnmergedClaims() throws Exception {
        verify("/fixtures/observed-event-pairs-20261026.json",5,5,3,2);
    }
    @Test public void secondObservedSnapshotRetainsNewOutcomesAndDifferentStatisticalFacts() throws Exception {
        verify("/fixtures/observed-event-stress-pairs-20261026.json",2,6,2,0);
    }
    private void verify(String resource,int expectedSame,int expectedDifferent,int expectedUncertain,int minimumMerged) throws Exception {
        JSONObject fixture;
        try(InputStream stream=getClass().getResourceAsStream(resource)){
            assertNotNull("Observed source fixture exists",stream);
            fixture=new JSONObject(new String(stream.readAllBytes(),StandardCharsets.UTF_8));
        }
        JSONArray pairs=fixture.getJSONArray("pairs");
        int same=0,mergedSame=0,different=0,wrongMerges=0,uncertain=0;
        for(int n=0;n<pairs.length();n++){
            JSONObject pair=pairs.getJSONObject(n);
            HotCandidate left=HotCandidate.fromJson(pair.getJSONObject("left"));
            HotCandidate right=HotCandidate.fromJson(pair.getJSONObject("right"));
            assertNotEquals(left.id,right.id);
            List<FeedStore.Row> rows=CandidateOrder.arrange(Arrays.asList(left,right),new JSONArray(),false);
            assertFalse("Both source facts remain eligible",rows.isEmpty());
            int members=0;for(FeedStore.Row row:rows)members+=row.entries.size();
            assertEquals("Keep both source entrances",2,members);
            boolean merged=rows.size()==1;
            switch(pair.getString("label")){
                case "same":same++;if(merged)mergedSame++;break;
                case "different":different++;if(merged)wrongMerges++;
                    assertFalse("Distinct event claims: "+left.title+" / "+right.title,merged);break;
                case "uncertain":uncertain++;break;
                default:fail("Unknown fixture label");
            }
        }
        assertEquals(expectedSame,same);assertEquals(expectedDifferent,different);assertEquals(expectedUncertain,uncertain);
        // Exact duplicates must still work; non-identical misses are reported,
        // not silently relabeled as separate events to manufacture a pass.
        assertTrue("Known exact duplicates remain grouped",mergedSame>=minimumMerged);
        System.out.println("OBSERVED_EVENT_BASELINE fixture="+resource+" same_merged="+mergedSame+"/"+same
            +" different_wrongly_merged="+wrongMerges+"/"+different
            +" uncertain_excluded="+uncertain+"; not whole-corpus quality acceptance");
    }
}
