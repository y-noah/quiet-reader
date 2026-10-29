package app.quietreader;

import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.discovery.EventClaimEvidence.Decision.*;

public class EventClaimEvidenceTest {
    @Test public void institutionalStatementRequiresSameActorStanceAndCrossSupportedExtraClause(){
        long at=1_793_183_700_000L;
        HotCandidate a=new HotCandidate(Models.Source.THEPAPER,"https://www.thepaper.cn/newsDetail_forward_34349984","央视体育青少中心：不认可皇马的表态，在道歉没有到位之前一切免谈","","",false,10,at-1000,at);
        HotCandidate b=new HotCandidate(Models.Source.CLS,"https://api3.cls.cn/share/article/2517612","总台体育青少中心发言人：不认可皇马的表态","在皇马的道歉没有到位之前，一切免谈。","",false,10,at-1000,at);
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,b));
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(b,a));
        HotCandidate unsupported=new HotCandidate(b.source,b.originalUrl,b.title,"","",false,10,b.publishedAt,at);
        assertNotEquals(CANDIDATE,EventClaimEvidence.evaluate(a,unsupported));
    }
    private final long now=1_798_100_000_000L;
    private HotCandidate candidate(Models.Source source,String title,long observed,Long published){return new HotCandidate(source,"https://example.org/"+source+"/"+HotCandidate.hash(title),title,"","",false,1,published,observed);}
    private EventClaimEvidence.Decision decision(String a,String b){return EventClaimEvidence.evaluate(candidate(Models.Source.ZHIHU,a,now,null),candidate(Models.Source.WEIBO,b,now,null));}
    @Test public void obituaryCourtesyTitleOnlyAdmitsExactNamedAssertion(){
        assertEquals(CANDIDATE,decision("陈明逝世","陈明同志逝世"));
        assertEquals(CANDIDATE,decision("陈明同志逝世！","陈明逝世"));
        assertEquals(CONFLICT,decision("陈明逝世","陈敏同志逝世"));
        for(String title:new String[]{"陈明疑似逝世","陈明未逝世","陈明是否逝世","陈明同志逝世，享年90岁","陈明同志逝世？","教授陈明逝世","某某同志逝世","男子同志逝世","老人同志逝世","父亲同志逝世","陈明同志逝世传闻被辟谣"})
            assertNotEquals(title,CANDIDATE,decision("陈明逝世",title));
        for(String generic:new String[]{"男子","女子","老人","网友","父亲","母亲","教授","医生"})
            assertNotEquals(generic,CANDIDATE,decision(generic+"逝世",generic+"同志逝世"));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(candidate(Models.Source.CLS,"陈明逝世",now,null),candidate(Models.Source.WEIBO,"陈明同志逝世",now+2*86400000L,null)));
        List<HotCandidate> pair=Arrays.asList(candidate(Models.Source.CLS,"陈明逝世",now,null),candidate(Models.Source.WEIBO,"陈明同志逝世",now,null));
        assertEquals("Admission alone does not merge",2,CandidateOrder.arrange(pair,new JSONArray(),false).size());
    }
    @Test public void typedReleaseSpecificationsAllowOmissionWithoutInventingFacts(){
        String full="4399 元起：星河发布 Nova pad 12 平板，9.5 寸 90Hz OLED 屏、X30 Pro 芯片、支持 IP68 防尘防水";
        String shorter="星河 Nova pad 12 发布，首搭 OLED 屏幕，起售价 4399 元，还有哪些亮点值得关注？";
        assertEquals(CANDIDATE,decision(full,shorter));assertEquals(CANDIDATE,decision(shorter,full));
        assertEquals(CANDIDATE,decision(full,"星河发布 Nova pad 12，起售价4399元"));
        assertEquals(CANDIDATE,decision("星河发布 Nova pad 12，起售价4399元，X30 Pro芯片","4399元起：星河 Nova pad 12 发布，X30 Pro芯片"));
        assertEquals("Admission does not create an event group",2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,full,now,null),candidate(Models.Source.WEIBO,shorter,now,null)),new JSONArray(),false).size());
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(candidate(Models.Source.CLS,full,now,null),candidate(Models.Source.WEIBO,shorter,now-86_400_001L,null)));
    }
    @Test public void typedReleaseKeepsExplicitActorModelPriceAndEverySharedSpecification(){
        String full="4399元起：星河发布 Nova pad 12 平板，9.5寸90Hz OLED屏、X30 Pro芯片、支持IP68防尘防水";
        for(String other:new String[]{full.replace("星河","海岚"),full.replace("pad 12","pad 13"),full.replace("4399","4499"),full.replace("9.5寸","8.5寸"),full.replace("90Hz","120Hz"),full.replace("OLED","LCD"),full.replace("X30 Pro","X30 Max"),full.replace("IP68","IP67"),full.replace("平板","手机")}){
            assertEquals(other,CONFLICT,decision(full,other));assertEquals(CONFLICT,decision(other,full));
        }
    }
    @Test public void unrecognizedReleaseClausesCannotExplainAdditionalNumbers(){
        String shorter="星河 Nova pad 12 发布，起售价4399元";
        for(String clause:new String[]{"返券200元","销量100万","比上一代便宜500元","售价3999元","支持IP68防尘防水但不保修","X30 Pro芯片但没有防水","2026年上市","90Hz不是120Hz","预计起售价4399元","起售价4399美元"})
            assertNotEquals(clause,CANDIDATE,decision(shorter,"4399元起：星河发布 Nova pad 12，"+clause));
        assertNotEquals(CANDIDATE,decision(shorter,"星河计划发布 Nova pad 12，起售价4399元，9寸90Hz OLED屏"));
        assertNotEquals(CANDIDATE,decision(shorter,"星河是否发布 Nova pad 12，起售价4399元，9寸90Hz OLED屏"));
        assertNotEquals(CANDIDATE,decision(shorter,"4399元起：星河发布 Nova pad 12，起售价4499元，9寸90Hz OLED屏"));
        assertNotEquals(CANDIDATE,decision("星河发布Nova9","星河发布Nova9，返券500元"));
        assertNotEquals(CANDIDATE,decision("星河发布Nova9","4399元起：星河发布Nova9，起售价4499元"));
    }
    @Test public void exactWealthRecordAllowsOneOmittedAmountButKeepsAllAssertedFacts(){
        String full="张明6700亿刷新中国首富财富纪录",shorter="张明刷新中国首富财富纪录";
        assertEquals(CANDIDATE,decision(full,shorter));assertEquals(CANDIDATE,decision(shorter,full));
        assertEquals(CANDIDATE,decision("李华120亿美元刷新亚洲首富财富纪录","李华刷新亚洲首富财富纪录"));
        for(String other:new String[]{"李明6700亿刷新中国首富财富纪录","张明6700亿刷新世界首富财富纪录","张明6800亿刷新中国首富财富纪录","张明6700万刷新中国首富财富纪录","张明6700亿美元刷新中国首富财富纪录"})
            assertNotEquals(CANDIDATE,decision(full,other));
        for(String other:new String[]{"张明可能刷新中国首富财富纪录","张明没有刷新中国首富财富纪录","张明计划刷新中国首富财富纪录","张明刷新中国首富财富纪录？","某人6700亿刷新中国首富财富纪录","张明6700亿再次刷新中国首富财富纪录","张明6700亿刷新2026年中国首富财富纪录"})
            assertNotEquals(CANDIDATE,decision(shorter,other));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(candidate(Models.Source.CLS,full,now,null),candidate(Models.Source.WEIBO,shorter,now-86_400_001L,null)));
        assertEquals(2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,full,now,null),candidate(Models.Source.WEIBO,shorter,now,null)),new JSONArray(),false).size());
    }
    @Test public void completeApologyParaphraseRequiresLiteralActorAndRecipient(){
        String plain="海岚联赛官方向中国球迷致歉",full="海岚联赛官方：代表联盟所有俱乐部向中国球迷致以诚挚的歉意";
        assertEquals(CANDIDATE,decision(plain,full));assertEquals(CANDIDATE,decision(full,plain));
        assertEquals(CANDIDATE,decision("海岚向球迷致歉","海岚向球迷致以歉意"));
        assertEquals(CONFLICT,decision(plain,full.replace("海岚联赛","星河联赛")));
        assertEquals(CONFLICT,decision(plain,full.replace("中国球迷","英国球迷")));
        assertEquals(CONFLICT,decision(full,full.replace("联盟所有俱乐部","个人").replace("诚挚的","")));
        assertEquals("Frame admission alone never merges cards",2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,plain,now,null),candidate(Models.Source.WEIBO,full,now,null)),new JSONArray(),false).size());
    }
    @Test public void apologyExpansionCannotInferMissingSlotsOrDiscardUncertaintyAndExtraEvents(){
        String plain="海岚联赛官方向中国球迷致歉";
        for(String title:new String[]{"官方向中国球迷致以诚挚的歉意","海岚联赛官方致以诚挚的歉意","海岚联赛官方计划向中国球迷致以诚挚的歉意","海岚联赛官方是否向中国球迷致以诚挚的歉意","海岚联赛官方未向中国球迷致以诚挚的歉意","海岚联赛官方向中国球迷致以诚挚的歉意？","海岚联赛官方因票价向中国球迷致以诚挚的歉意","海岚联赛官方向中国球迷致以诚挚的歉意，并终止合作","海岚联赛官方：代表某些人向中国球迷致以诚挚的歉意"})
            assertNotEquals(title,CANDIDATE,decision(plain,title));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(candidate(Models.Source.CLS,plain,now,null),candidate(Models.Source.WEIBO,"海岚联赛官方向中国球迷致以歉意",now+2*86_400_000L,null)));
    }
    @Test public void explicitCorruptionJudgmentRequiresSamePersonAmountOutcomeAndCompatibleStage(){
        String full="受贿2.75亿余元，某省原党委委员李文远一审被判死缓";
        assertEquals(CANDIDATE,decision(full,"受贿2.75亿余元，某省原党委委员李文远被判死缓"));
        assertEquals(CANDIDATE,decision(full,"受贿2.75亿余元 李文远一审被判死缓"));
        assertEquals(CANDIDATE,decision("受贿800万元，张明一审被判无期徒刑","受贿800万元，张明被判无期徒刑"));
        assertEquals(CONFLICT,decision(full,full.replace("李文远","陈文远")));
        assertEquals(CONFLICT,decision(full,full.replace("2.75亿","2.75万")));
        assertEquals(CONFLICT,decision(full,full.replace("一审","二审")));
        assertEquals(CONFLICT,decision(full,full.replace("死缓","死刑")));
        assertEquals(CONFLICT,decision(full,full.replace("某省原党委委员","某企业原董事长")));
        assertEquals("Judgment admission never merges without verification",2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,full,now,null),candidate(Models.Source.THEPAPER,"受贿2.75亿余元 李文远一审被判死缓",now,null)),new JSONArray(),false).size());
    }
    @Test public void uncertainOrIncompleteJudgmentCannotInferThePersonOrSentence(){
        String full="受贿2.75亿余元，某省原党委委员李文远一审被判死缓";
        for(String title:new String[]{"受贿2.75亿余元，李某一审被判死缓","受贿2.75亿余元，一名男子被判死缓","受贿2.75亿余元，李文远拟被判死缓","受贿2.75亿余元，李文远是否被判死缓","受贿2.75亿余元，李文远未被判死缓","受贿2.75亿余元，李文远被判死缓？","受贿2.75亿余元，李文远被判死缓，案件详情公布","李文远被判死缓","受贿2.75亿余元，被判死缓"})
            assertNotEquals(title,CANDIDATE,decision(full,title));
    }
    @Test public void concreteSharedClaimIsOnlyAdmittedForVerification(){
        HotCandidate a=candidate(Models.Source.ZHIHU,"王沪宁陪同朝鲜最高领导人金正恩访问丹东",now,null),b=candidate(Models.Source.WEIBO,"王沪宁陪同金正恩访问丹东",now,null);
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,b));
        assertEquals("Admission is not a merge proof",2,CandidateOrder.arrange(Arrays.asList(a,b),new JSONArray(),false).size());
    }
    @Test public void stoppedActionsRequireExactExplicitSubjectAndObjectBeforeVerification(){
        assertEquals(CANDIDATE,decision("星河台全面叫停涉张明赛事和广告播出","星河台叫停涉张明赛事和广告播出"));
        assertEquals(CANDIDATE,decision("星河饮料集团：坚决抵制损害公众利益行为，终止与张明全部合作","星河饮料集团：终止与张明合作"));
        assertEquals(CONFLICT,decision("星河饮料集团：终止与张明合作","海城乳业：终止与张明合作"));
        assertEquals(CONFLICT,decision("星河饮料集团：终止与张明合作","星河饮料集团：终止与李宁合作"));
        assertEquals(CONFLICT,decision("星河台叫停涉张明赛事和广告播出","星河台叫停涉张明广告播出"));
        assertEquals(CONFLICT,decision("星河乳业：终止与张明合作","星河：终止与张明合作"));
    }
    @Test public void collectiveUncertainAndAnalysisStoppedHeadlinesCannotPassOnSharedTarget(){
        for(String title:new String[]{"星河、海城：终止与张明合作","多家企业终止与张明合作","星河饮料集团是否终止与张明合作","星河饮料集团计划终止与张明合作","星河饮料集团：回应终止与张明合作","星河饮料集团：终止与张明合作，或需赔偿"})
            assertNotEquals(title,CANDIDATE,decision("星河饮料集团：终止与张明合作",title));
        assertNotEquals(CANDIDATE,decision("星河台叫停涉张明赛事和广告播出","星河台删除张明相关报道"));
        assertEquals("Exact stopped claim only permits model verification",2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,"星河台全面叫停涉张明赛事和广告播出",now,null),candidate(Models.Source.WEIBO,"星河台叫停涉张明赛事和广告播出",now,null)),new JSONArray(),false).size());
    }
    @Test public void completeInvolvedBroadcastFramePreservesActorObjectAndScope(){
        assertEquals(CANDIDATE,decision("星河台叫停涉张明赛事和广告播出","星河台已经全面叫停涉及张明的赛事和广告播出"));
        assertEquals(CONFLICT,decision("星河台叫停涉张明赛事和广告播出","蓝海台叫停涉及张明的赛事和广告播出"));
        assertEquals(CONFLICT,decision("星河台叫停涉张明赛事和广告播出","星河台叫停涉及李明的赛事和广告播出"));
        assertEquals(CONFLICT,decision("星河台叫停涉张明赛事播出","星河台叫停涉及张明的赛事和广告播出"));
        assertEquals(CONFLICT,decision("星河台叫停涉张明赛事和广告播出","星河台叫停涉及张明赛事和广告播出"));
        assertNotEquals(CANDIDATE,decision("星河台叫停涉张明赛事和广告播出","星河台计划叫停涉及张明的赛事和广告播出"));
        assertEquals(CONFLICT,decision("星河台叫停涉张明3场赛事和广告播出","星河台叫停涉及张明4场的赛事和广告播出"));
        assertEquals(2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,"星河台叫停涉张明赛事和广告播出",now,null),candidate(Models.Source.WEIBO,"星河台已经全面叫停涉及张明的赛事和广告播出",now,null)),new JSONArray(),false).size());
    }
    @Test public void stoppedActionsRetainNumbersTimeAndProtectedFacts(){
        assertEquals(CONFLICT,decision("星河台叫停3场赛事播出","星河台叫停4场赛事播出"));
        assertNotEquals(CANDIDATE,decision("星河饮料集团终止与张明合作","星河饮料集团没有终止与张明合作"));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(candidate(Models.Source.CLS,"星河台全面叫停涉张明赛事和广告播出",now,null),candidate(Models.Source.WEIBO,"星河台叫停涉张明赛事和广告播出",0,null)));
    }
    @Test public void responsesFatalitiesAndDifferentProjectsCannotShareAVerificationTask(){
        assertEquals(CONFLICT,decision("毛不易出席好友婚礼致辞引争议","新郎新娘回应毛不易婚礼致辞引争议"));
        assertEquals(CONFLICT,decision("病重男子转院途中被司机丢弃","病重男子转院途中被司机丢弃致死"));
        assertEquals(CONFLICT,decision("乒乓球亚锦赛男单四强出炉","乒乓球亚锦赛女单四强出炉"));
        assertEquals(CONFLICT,decision("王沪宁陪同金正恩访问丹东","金正恩访问丹东"));
    }
    @Test public void numericFactsKeepOrderSignsUnitsAndDecimalPunctuation(){
        for(String[] pair:new String[][]{{"利率1.5%发布","利率15%发布"},{"指标-1发布","指标1发布"},{"指数上涨1%发布","指数上涨1‰发布"},{"比分0比1发布","比分1比0发布"}})
            assertEquals(CONFLICT,decision("该公司"+pair[0],"该公司"+pair[1]));
        assertEquals(CANDIDATE,decision("该公司发布利率１．５％新政策","消息称该公司发布利率1.5%新政策"));
    }
    @Test public void explicitSmallChineseCountsMatchDigitsButNeverDropConflictingCounts(){
        for(String[] count:new String[][]{{"五","5"},{"两","2"},{"十","10"},{"二十一","21"},{"九十九","99"}})
            assertEquals(CANDIDATE,decision("新华集团发布连续"+count[0]+"年经营报告","新华集团发布连续第"+count[1]+"年经营报告"));
        assertEquals(CONFLICT,decision("新华集团发布连续五年经营报告","新华集团发布连续第6年经营报告"));
        assertEquals(CONFLICT,decision("新华集团发布连续五年经营报告","新华集团发布连续第5届经营报告"));
        assertEquals(CONFLICT,decision("新华集团发布第五次经营报告","新华集团发布连续第5年经营报告"));
        for(String ambiguous:new String[]{"三四","数十","近十","两三","五百"})
            assertEquals(CONFLICT,decision("新华集团发布连续"+ambiguous+"年经营报告","新华集团发布连续第10年经营报告"));
    }
    @Test public void explicitAppointmentAnnouncementsRequireMatchingRoleCountAndPredicateState(){
        assertEquals(CANDIDATE,decision("2027 羊年央视春晚总导演公布，于蕾连续五年扛起重任","于蕾出任2027羊年央视春晚总导演，连续第5年执导春晚"));
        assertEquals(CANDIDATE,decision("新华集团董事长人选公布，张明出任董事长","张明获任新华集团董事长"));
        assertEquals(CONFLICT,decision("张明出任新华集团董事长","张明出任新华集团总经理"));
        assertEquals(CONFLICT,decision("张明出任新华集团董事长","张明计划出任新华集团董事长"));
        assertEquals(CONFLICT,decision("张明出任新华集团董事长","张明未获任新华集团董事长"));
        assertEquals(CONFLICT,decision("张明出任新华集团董事长","张明是否出任新华集团董事长"));
    }
    @Test public void shortSharedPersonAndGenericActionAreInsufficient(){
        assertEquals(INSUFFICIENT,decision("毛不易这情商","毛不易 情商"));
        assertEquals(INSUFFICIENT,decision("近日正式发布新款蓝牙耳机","据报道发布新款智能家电"));
        assertEquals(INSUFFICIENT,decision("该公司发布新型蓝牙耳机","该公司发布家用智能空调"));
        assertEquals(CONFLICT,decision("某战队战胜另一支战队","某战队不敌另一支战队"));
    }
    @Test public void unconfirmedAndNegatedClaimsCannotBecomeEquivalentThroughHighSimilarity(){
        assertEquals(CONFLICT,decision("该城市发现新的珍贵考古遗址","该城市尚未发现新的珍贵考古遗址"));
        assertEquals(CONFLICT,decision("县委书记回应被扔下水事件","县委书记回应被扔下水传闻系谣言"));
        assertEquals(CONFLICT,decision("某公司发布新款折叠屏手机","网传某公司发布新款折叠屏手机"));
    }
    @Test public void fetchWindowIsNotInventedPublicationEvidence(){
        String left="王沪宁陪同朝鲜最高领导人金正恩访问丹东",right="王沪宁陪同金正恩访问丹东";
        HotCandidate a=candidate(Models.Source.ZHIHU,left,now,null);
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(a,candidate(Models.Source.WEIBO,right,0,null)));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(a,candidate(Models.Source.WEIBO,right,now+86_400_001L,null)));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(a,candidate(Models.Source.WEIBO,right,now,now+600_001L)));
        long midnight=Math.floorDiv(now+8*3600_000L,86_400_000L)*86_400_000L-8*3600_000L;
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(candidate(Models.Source.ZHIHU,left,midnight-1,null),candidate(Models.Source.WEIBO,right,midnight+1,null)));
        assertEquals(INSUFFICIENT,EventClaimEvidence.evaluate(candidate(Models.Source.ZHIHU,left,now,midnight-1),candidate(Models.Source.WEIBO,right,now,midnight+1)));
    }
    @Test public void explicitIndependentDiscussionsRemainConflicting(){
        HotCandidate a=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/111","某公司发布新型手机产品发布会","","",false,1,null,now);
        HotCandidate b=new HotCandidate(Models.Source.V2EX,"https://www.v2ex.com/t/112","某公司正式发布新型手机产品发布会","","",false,1,null,now);
        assertEquals(CONFLICT,EventClaimEvidence.evaluate(a,b));
    }
    @Test public void electionResultsAndApologiesReachVerificationWithoutErasingRefusalOrOutcome(){
        assertEquals(CANDIDATE,decision("武契奇承认败选","武契奇承认前进党在议会选举中败选"));
        assertEquals(CANDIDATE,decision("Kimi致歉，公布补偿方案","Kimi致歉并宣布补偿方案"));
        assertEquals(CANDIDATE,decision("巴西总统卢拉赢得连任","卢拉赢得连任，当选巴西新一任总统"));
        for(String refusal:new String[]{"拒绝承认","拒不承认","不承认"})assertEquals(CONFLICT,decision("某候选人承认败选","某候选人"+refusal+"败选"));
        assertEquals(CONFLICT,decision("某候选人赢得连任","某候选人承认败选"));
        assertEquals(CONFLICT,decision("Kimi致歉并补偿会员","Kimi否认需要致歉并补偿会员"));
        assertEquals(CONFLICT,decision("Kimi致歉补偿25%","Kimi致歉补偿15%"));
        assertEquals(INSUFFICIENT,decision("某候选人赢得连任","某候选人第二轮投票开始"));
        assertEquals(INSUFFICIENT,decision("Kimi致歉公布补偿方案","Kimi产品使用体验"));
        assertEquals("New admission never merges without actual verification",2,CandidateOrder.arrange(Arrays.asList(candidate(Models.Source.CLS,"Kimi致歉，公布补偿方案",now,null),candidate(Models.Source.WEIBO,"Kimi致歉并宣布补偿方案",now,null)),new JSONArray(),false).size());
    }
    @Test public void directPredicateNegationCannotBeMistakenForTheCompletedAction(){
        for(String[] pair:new String[][]{
            {"该候选人成功连任地方议会主席","该候选人未能连任地方议会主席"},
            {"该候选人当选地方议会主席","该候选人未当选地方议会主席"},
            {"该候选人当选地方议会主席","该候选人没能当选地方议会主席"},
            {"该公司发布折叠屏手机新品","该公司未发布折叠屏手机新品"},
            {"该公司发布折叠屏手机新品","该公司不能发布折叠屏手机新品"},
            {"该候选人成功连任地方议会主席","该候选人未能成功连任地方议会主席"},
            {"该城市发现珍贵考古遗址","该城市未发现珍贵考古遗址"},
            {"该研究所发现新的古代遗址","该研究所未 发现新的古代遗址"}}){
            assertEquals(Arrays.toString(pair),CONFLICT,decision(pair[0],pair[1]));
            assertEquals(Arrays.toString(pair),CONFLICT,decision(pair[1],pair[0]));
        }
    }
    @Test public void pendingAndOpenPredicateQuestionsAreNotCompletedEvents(){
        for(String prefix:new String[]{"能否","是否","会不会","能不能","计划","拟","预计","准备","即将","将要"})
            assertEquals(prefix,CONFLICT,decision("该公司发布新款折叠屏手机","该公司"+prefix+"发布新款折叠屏手机"));
        assertEquals(CONFLICT,decision("该候选人当选地方议会主席","该候选人能否当选地方议会主席"));
        assertEquals(CONFLICT,decision("该公司发布新款折叠屏手机","该公司是否已经发布新款折叠屏手机"));
        assertEquals(CONFLICT,decision("该公司发布新款折叠屏手机","该公司能否成功发布新款折叠屏手机"));
        assertEquals("Opinion about an explicitly completed fact is not an open factual question",CANDIDATE,
            decision("该公司发布新款折叠屏手机","该公司发布新款折叠屏手机，是否合理？"));
    }
    @Test public void predicateScopeDoesNotConfuseDifferentNegatedActions(){
        assertEquals(CONFLICT,decision("该公司未发布新款手机并开售平板电脑","该公司发布新款手机但未开售平板电脑"));
        assertEquals(CANDIDATE,decision("该公司未能发布新款折叠屏手机","该公司没能发布新款折叠屏手机"));
        assertEquals(CANDIDATE,decision("该公司计划发布新款折叠屏手机","该公司拟发布新款折叠屏手机"));
        assertEquals(CONFLICT,decision("该公司计划发布新款折叠屏手机","该公司是否发布新款折叠屏手机"));
        assertEquals(CANDIDATE,decision("未央科技发布新款折叠屏手机","未央科技正式发布新款折叠屏手机"));
        assertEquals(CANDIDATE,decision("不凡科技发布新款折叠屏手机","不凡科技正式发布新款折叠屏手机"));
    }
    @Test public void sharedExplicitReleaseVersionCanHaveAdditionalParametersButNeverConflictingSpecifiedFacts()throws Exception{
        JSONObject fixture;try(InputStream in=getClass().getResourceAsStream("/fixtures/observed-event-holdout-pairs-20261026.json")){fixture=new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}
        JSONObject pair=fixture.getJSONArray("pairs").getJSONObject(0);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));
        assertEquals(CANDIDATE,EventClaimEvidence.evaluate(a,b));assertEquals(CANDIDATE,EventClaimEvidence.evaluate(b,a));
        assertEquals(CONFLICT,decision("月之暗面发布Kimi K3.1 Pro新模型","月之暗面发布Kimi K3.2 Pro最强3万亿模型"));
        assertEquals(CONFLICT,decision("月之暗面发布K3.1利率1.5%方案","月之暗面发布K3.1利率15%与3万亿方案"));
        assertEquals(CONFLICT,decision("某战队S16以0比1不敌对手","某战队S16以1比0战胜对手"));
        assertEquals(CONFLICT,decision("某公司发布K3.1与K4.2新产品","某公司发布K4.2与K3.1及6款新产品"));
    }
    @Test public void observedPairsRejectConflictingAndUncertainClaimsWithoutRejectingEverything()throws Exception{
        int candidates=0,already=0,unknown=0,different=0,same=0;
        for(String resource:new String[]{"/fixtures/observed-event-pairs-20261026.json","/fixtures/observed-event-stress-pairs-20261026.json"}){
            JSONObject fixture;try(InputStream input=getClass().getResourceAsStream(resource)){assertNotNull(input);fixture=new JSONObject(new String(input.readAllBytes(),StandardCharsets.UTF_8));}
            JSONArray pairs=fixture.getJSONArray("pairs");for(int n=0;n<pairs.length();n++){
                JSONObject pair=pairs.getJSONObject(n);HotCandidate a=HotCandidate.fromJson(pair.getJSONObject("left")),b=HotCandidate.fromJson(pair.getJSONObject("right"));
                EventClaimEvidence.Decision result=EventClaimEvidence.evaluate(a,b);String label=pair.getString("label");
                if(label.equals("same")){same++;if(result==CANDIDATE)candidates++;if(result==ALREADY_GROUPED)already++;}
                else {if(label.equals("uncertain"))unknown++;else different++;assertTrue(label+": "+a.title+" / "+b.title,result==CONFLICT||result==INSUFFICIENT);}
            }
        }
        assertEquals(7,same);assertEquals(11,different);assertEquals(5,unknown);assertTrue("Actual paraphrases reach model admission",candidates>=2);assertEquals(2,already);
        System.out.println("OBSERVED_CLAIM_ADMISSION same_candidates="+candidates+" already_grouped="+already+" same_total="+same+" different_rejected="+different+" uncertain_rejected="+unknown+"; admission only, not merge acceptance");
    }
}

