package app.quietreader;

import app.quietreader.discovery.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.discovery.EventClaimEvidence.Decision.*;

/** Literal actor/object evidence admits verification, never establishes equivalence. */
public class DirectRelationEvidenceTest {
    private static final long NOW=1_793_236_926_474L;
    private EventClaimEvidence.Decision decision(String a,String b){
        return EventClaimEvidence.evaluate(new HotCandidate(Models.Source.THEPAPER,"https://example.org/a",a,"","",false,1,NOW-12*3600_000,NOW),
                new HotCandidate(Models.Source.BAIDU,"https://example.org/b",b,"","",false,1,null,NOW+1000));
    }
    @Test public void namedAffiliationCanBeVerifiedAcrossPublicationDayBoundary(){
        assertEquals(CANDIDATE,decision("工程师林清加盟江城大学","工程师林清加盟江城大学"));
        assertNotEquals(CANDIDATE,decision("工程师林清加盟江城大学","工程师周明加盟江城大学"));
        assertNotEquals(CANDIDATE,decision("工程师林清加盟江城大学","工程师林清加盟海川大学"));
    }
    @Test public void airspaceClosureRetainsLiteralActorAndAirlineRegion(){
        assertEquals(CANDIDATE,decision("甲国对乙国地区航司关闭领空","甲国对乙国航司关闭领空"));
        assertNotEquals(CANDIDATE,decision("甲国对乙国航司关闭领空","丙国对乙国航司关闭领空"));
        assertNotEquals(CANDIDATE,decision("甲国对乙国航司关闭领空","甲国对丙国航司关闭领空"));
    }
    @Test public void qualifiersAndExtraClaimsCannotBorrowTheAssertedRelation(){
        for(String prefix:new String[]{"未","没有","计划","拟","疑似","网传","是否","如果","去年","曾经"}){
            assertNotEquals(prefix,CANDIDATE,decision("工程师林清加盟江城大学",prefix+"工程师林清加盟江城大学"));
            assertNotEquals(prefix,CANDIDATE,decision("甲国对乙国航司关闭领空",prefix+"甲国对乙国航司关闭领空"));
        }
        for(String suffix:new String[]{"？","，后又退出","的传闻","，另有三人受伤"}){
            assertNotEquals(suffix,CANDIDATE,decision("工程师林清加盟江城大学","工程师林清加盟江城大学"+suffix));
            assertNotEquals(suffix,CANDIDATE,decision("甲国对乙国航司关闭领空","甲国对乙国航司关闭领空"+suffix));
        }
    }
    @Test public void vagueSubjectsAndDifferentActionsRemainInsufficient(){
        for(String title:new String[]{"某工程师加盟江城大学","该工程师加盟江城大学","一名工程师加盟江城大学","工程师林清加盟某大学","工程师林清可能加盟江城大学"})
            assertNotEquals(title,CANDIDATE,decision("工程师林清加盟江城大学",title));
        assertNotEquals(CANDIDATE,decision("甲国对乙国航司关闭领空","甲国对乙国航司开放领空"));
        assertNotEquals(CANDIDATE,decision("工程师林清加盟江城大学","工程师林清离开江城大学"));
    }
    @Test public void repeatingAQualifierOrGenericActorDoesNotMakeItAnAssertedRelation(){
        for(String qualifier:new String[]{"将","或将","有望","准备","考虑","打算","计划","拟","可能","欲","曾","去年","据称","网传","否认","是否"}){
            String joining="工程师林清"+qualifier+"加盟江城大学";
            String airspace="甲国"+qualifier+"对乙国航司关闭领空";
            assertNotEquals(joining,CANDIDATE,decision(joining,joining));
            assertNotEquals(airspace,CANDIDATE,decision(airspace,airspace));
        }
        for(String vague:new String[]{"工程师加盟江城大学","教授加盟江城大学","政府对乙国航司关闭领空","甲国对国家航司关闭领空"})
            assertNotEquals(vague,CANDIDATE,decision(vague,vague));
    }
}
