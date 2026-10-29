package app.quietreader;

import app.quietreader.discovery.*;
import org.json.JSONArray;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
import static app.quietreader.discovery.EventClaimEvidence.Decision.*;

/** Complete source attributes, not a license to erase numbers from headlines. */
public class ProductReleaseAttributesTest {
    private static final long NOW=1_793_236_926_474L;
    private static final String FULL="星河首款触控屏 NoteBook Pro 正式发布：双层串联 OLED、低至 1.28 千克重量，12,399 元起";
    private static final String SHORT="星河发布首款触屏NoteBook Pro";
    private HotCandidate item(int id,String title){return new HotCandidate(id==1?Models.Source.ITHOME:Models.Source.BAIDU,"https://example.org/"+id,title,"","",false,id,null,NOW);}
    private EventClaimEvidence.Decision decision(String left,String right){return EventClaimEvidence.evaluate(item(1,left),item(2,right));}
    @Test public void namedLetterModelAcceptsOnlyExplicitlyParsedOmittedAttributes(){
        assertEquals(CANDIDATE,decision(FULL,SHORT));assertEquals(CANDIDATE,decision(SHORT,FULL));
        assertEquals(CANDIDATE,decision(FULL,"12399元起：星河发布首款触控屏 NoteBook Pro，搭载OLED屏幕"));
    }
    @Test public void sharedPricesWeightsAndScreenArchitectureMustAgree(){
        for(String conflicting:new String[]{FULL.replace("12,399","12,499"),FULL.replace("1.28","1.29"),FULL.replace("双层串联","单层串联"),FULL.replace("OLED","LCD")})
            assertEquals(conflicting,CONFLICT,decision(FULL,conflicting));
    }
    @Test public void currencySeparatorsAreNotGeneralNumberOrUnitRewriting(){
        assertEquals(CANDIDATE,decision(FULL,FULL.replace("12,399","12399")));
        for(String malformed:new String[]{"12,39","1,23,399","012,399","12.399","12399美元","1.2399万元"}){
            String other=FULL.replace("12,399 元",malformed.contains("元")||malformed.contains("美元")?malformed:malformed+" 元");
            assertNotEquals(other,CANDIDATE,decision(FULL,other));
        }
    }
    @Test public void fullBrandModelAndUnknownClausesRemainAuthoritative(){
        for(String other:new String[]{FULL.replace("星河","北辰"),FULL.replace("NoteBook Pro","NoteBook Max"),FULL.replace("NoteBook Pro","NoteBook Pro 2"),
                FULL+"，返券500元",FULL+"，销量超过100万台",FULL.replace("低至 1.28 千克重量","重量1280克"),FULL.replace("OLED","QLED"),FULL+"，起售价12499元"})
            assertNotEquals(other,CANDIDATE,decision(SHORT,other));
    }
    @Test public void qualifiersAndFactualQuestionsCannotBecomeCompletedReleases(){
        for(String word:new String[]{"计划","将","是否","据称","疑似","未"})
            assertNotEquals(word,CANDIDATE,decision(FULL,SHORT.replace("发布",word+"发布")));
        assertNotEquals(CANDIDATE,decision(FULL,SHORT+"是否属实？"));
    }
    @Test public void admittedSpecificationsStillNeedActualModelVerification(){
        HotCandidate full=item(1,FULL),shorter=item(2,SHORT);
        assertEquals(2,CandidateOrder.arrange(List.of(full,shorter),new JSONArray(),false).size());
        assertEquals(2,EventGroupAssembly.apply(CandidateOrder.arrange(List.of(full,shorter),new JSONArray(),false),new JSONArray(),List.of(),"actual-model-required").size());
    }
}
