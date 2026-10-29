package app.quietreader;

import app.quietreader.discovery.*;
import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static app.quietreader.Models.Source;

public class AppointmentEventEvidenceTest {
    private static final long NOW=1_798_100_000_000L;
    private static final String ANNOUNCED="2028星河年度大会总导演公布，林岚连续五年扛起重任",DIRECT="林岚出任2028星河年度大会总导演，连续第5年执导大会";
    private HotCandidate candidate(Source source,String title,Long published,long observed,String snippet){return new HotCandidate(source,"https://example.org/"+source+"/"+HotCandidate.hash(title),title,snippet,"",false,1,published,observed);}
    private HotCandidate a(){return candidate(Source.ITHOME,ANNOUNCED,null,NOW,"");}
    private HotCandidate b(){return candidate(Source.THEPAPER,DIRECT,NOW-60_000,NOW,"");}
    private List<FeedStore.Row> rows(HotCandidate... entries){return CandidateOrder.arrange(Arrays.asList(entries),new JSONArray(),false);}
    @Test public void observedDirectorPairUsesCompleteSlotsAndRetainsBothSourceEntrances()throws Exception{
        JSONObject fixture;try(java.io.InputStream in=getClass().getResourceAsStream("/fixtures/observed-event-fresh-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));}
        boolean found=false;JSONArray pairs=fixture.getJSONArray("pairs");
        for(int n=0;n<pairs.length();n++){JSONObject pair=pairs.getJSONObject(n);if(!pair.getJSONObject("left").getString("title").contains("总导演"))continue;
            assertEquals("same",pair.getString("label"));HotCandidate left=HotCandidate.fromJson(pair.getJSONObject("left")),right=HotCandidate.fromJson(pair.getJSONObject("right"));List<FeedStore.Row> grouped=rows(left,right);assertEquals(1,grouped.size());assertEquals(2,grouped.get(0).entries.size());assertEquals(new HashSet<>(Arrays.asList(left.id,right.id)),new HashSet<>(Arrays.asList(grouped.get(0).entries.get(0).id,grouped.get(0).entries.get(1).id)));found=true;
        }assertTrue(found);
    }
    @Test public void changingAnyExplicitSlotOrCountCannotMerge(){
        assertEquals(1,rows(a(),b()).size());
        for(String title:new String[]{DIRECT.replace("林岚","陈宁"),DIRECT.replace("2028","2029"),DIRECT.replace("星河","海城"),DIRECT.replace("总导演","主教练"),DIRECT.replace("第5年","第6年"),DIRECT.replace("第5年","第5届")})assertEquals(title,2,rows(a(),candidate(Source.THEPAPER,title,NOW-60_000,NOW,"")).size());
    }
    @Test public void unknownTimePublicationAndSubstantiveExcerptsNeedExistingVerifier(){
        assertEquals(2,rows(a(),candidate(Source.THEPAPER,DIRECT,null,NOW,"")).size());
        for(Long published:new Long[]{NOW-37*3600_000L,NOW+600_001L,0L})assertEquals(2,rows(a(),candidate(Source.THEPAPER,DIRECT,published,NOW,"")).size());
        assertEquals(2,rows(a(),candidate(Source.THEPAPER,DIRECT,NOW-60_000,0,"")).size());
        assertEquals(2,rows(a(),candidate(Source.THEPAPER,DIRECT,NOW-60_000,NOW,"这是一段独立来源说明，可能包含新的事实，不能仅靠标题快捷合并。")).size());
    }
    @Test public void deniedUncertainAndAnonymousAppointmentsStaySeparate(){
        for(String suffix:new String[]{"，网传消息尚未确认","，本人否认消息","，回应任命争议","，随后辞职","，是否属实"})assertEquals(suffix,2,rows(a(),candidate(Source.THEPAPER,DIRECT+suffix,NOW-60_000,NOW,"")).size());
        for(String actor:new String[]{"本人","某人","网友"})assertEquals(2,rows(candidate(Source.ITHOME,ANNOUNCED.replace("林岚",actor),null,NOW,""),candidate(Source.THEPAPER,DIRECT.replace("林岚",actor),NOW-60_000,NOW,"")).size());
        assertEquals(2,rows(candidate(Source.ITHOME,ANNOUNCED.replace("星河年度大会","某地年度大会"),null,NOW,""),candidate(Source.THEPAPER,DIRECT.replace("星河年度大会","某地年度大会"),NOW-60_000,NOW,"")).size());
    }
    @Test public void conflictsInCoObservedFamilyAndIndependentSamePlatformPostsAreNotBridged(){
        HotCandidate conflicting=candidate(Source.WEIBO,DIRECT.replace("第5年","第6年"),NOW-60_000,NOW,"");assertEquals(3,rows(a(),b(),conflicting).size());
        assertEquals(2,rows(a(),candidate(Source.ITHOME,DIRECT,NOW-60_000,NOW,"")).size());
        HotCandidate thread=new HotCandidate(Source.V2EX,"https://www.v2ex.com/t/1234567",ANNOUNCED,"","",false,1,null,NOW);assertEquals(2,rows(thread,b()).size());
    }
    @Test public void manualSeparateHideAndUndoRemainAuthoritative()throws Exception{
        FeedStore.Row merged=rows(a(),b()).get(0);
        for(String action:Arrays.asList("SEPARATE","BLOCK","ENOUGH")){
            JSONArray feedback=new JSONArray().put(new JSONObject().put("scope","event").put("action",action).put("target",EventTargets.capture(merged,action.equals("ENOUGH"))));
            assertEquals(action.equals("SEPARATE")?2:0,CandidateOrder.arrange(Arrays.asList(a(),b()),feedback,false).size());
        }assertEquals(1,rows(a(),b()).size());
    }
    @Test public void exactSlotEvidenceDoesNotAddRerankerWorkAndMissingEvidenceStillCan(){
        Map<String,float[]> cached=new HashMap<>();float[] vector=new float[512];vector[0]=1;
        for(HotCandidate c:Arrays.asList(a(),b()))cached.put(SemanticOrder.key(c.modelText()),vector);
        assertTrue(EventVerificationPlan.create(Arrays.asList(a(),b()),cached,"fixed").isEmpty());
        HotCandidate unpublished=candidate(Source.THEPAPER,DIRECT,null,NOW,"");assertEquals(1,EventVerificationPlan.create(Arrays.asList(a(),unpublished),cached,"fixed").size());
    }
}
