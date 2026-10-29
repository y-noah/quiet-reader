package app.quietreader;

import org.junit.Test;
import static org.junit.Assert.*;
import static app.quietreader.Models.*;
import app.quietreader.discovery.*;

public class BodyVideoEvidenceTest {
    @Test public void embeddedVideosAndVideoAnswersDoNotClassifyTheWholeTopic(){
        Document d=new Document();d.url="https://www.zhihu.com/question/123";d.unsupportedVideo=true;d.filteredVideos=2;
        assertFalse(BodyVideoEvidence.classify(Source.ZHIHU,d));d.videoTopic=true;assertTrue(BodyVideoEvidence.classify(Source.ZHIHU,d));
        d.videoTopic=false;d.filteredVideo=true;assertTrue(BodyVideoEvidence.classify(Source.ZHIHU,d));
    }
    @Test public void gatesAndWeiboTopicCollectionsNeverBecomeVideoEvidence(){
        Document d=new Document();d.videoTopic=true;d.url="https://bbs.hupu.com/123.html";d.loginRequired=true;assertFalse(BodyVideoEvidence.classify(Source.HUPU,d));
        d.loginRequired=false;d.sourceUnavailable=true;assertFalse(BodyVideoEvidence.classify(Source.HUPU,d));
        d.sourceUnavailable=false;d.url="https://s.weibo.com/weibo?q=%23topic%23";assertFalse(BodyVideoEvidence.classify(Source.WEIBO,d));
    }
    @Test public void videoEvidenceRoundTripsWithoutChangingModelTextIdentity()throws Exception{
        HotCandidate before=new HotCandidate(Source.HUPU,"https://bbs.hupu.com/123.html","当前中文话题","","",false,1,null,100);
        HotCandidate video=new HotCandidate(before.source,before.originalUrl,before.title,before.snippet,before.rawHeat,true,before.sourceRank,null,100,ContentLanguage.Body.UNKNOWN,"",0,200);
        HotCandidate restored=HotCandidate.fromJson(video.json());assertEquals(200,restored.videoEvidenceAt);assertTrue(restored.video);assertEquals(before.textHash,restored.textHash);assertEquals(before.id,restored.id);
        org.json.JSONObject old=video.json();old.remove("videoEvidenceAt");assertEquals(0,HotCandidate.fromJson(old).videoEvidenceAt);
        assertTrue(CandidateOrder.arrange(java.util.List.of(restored),new org.json.JSONArray(),false).isEmpty());
    }
    private static Document prose(String label){
        Document d=new Document();d.url="https://bbs.hupu.com/123.html";Section main=new Section("main",label,false);main.blocks.add(new Block("text","完整的来源主帖内容".repeat(30)));d.sections.add(main);d.blocks.addAll(main.blocks);return d;
    }
    @Test public void onlySubstantialPrimaryProseCanCorrectInferredType(){
        for(String label:new String[]{"正文","正文 · 作者","主帖","主帖 · 作者","问题补充"})assertTrue(BodyVideoEvidence.textTopic(Source.HUPU,prose(label)));
        for(String label:new String[]{"回复 · 作者","评论","回答","搜索结果 · 来源摘要","相关帖子 · 摘要","视频简介"})assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,prose(label)));
        Document answer=prose("正文");Section disguised=new Section("answer","正文",true);disguised.blocks.addAll(answer.sections.get(0).blocks);answer.sections.clear();answer.sections.add(disguised);assertFalse(BodyVideoEvidence.textTopic(Source.ZHIHU,answer));
        Document shortBody=prose("主帖");shortBody.sections.get(0).blocks.clear();shortBody.sections.get(0).blocks.add(new Block("text","只有一句摘要"));assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,shortBody));
        Document legacy=new Document();legacy.blocks.add(new Block("text","主帖身份不明确".repeat(100)));assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,legacy));
    }
    @Test public void restrictedVideoCollectionsAndLaterPagesCannotCorrectType(){
        Document d=prose("主帖");d.loginRequired=true;assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,d));
        d.loginRequired=false;d.sourceUnavailable=true;assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,d));
        d.sourceUnavailable=false;d.videoTopic=true;assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,d));
        d.videoTopic=false;d.filteredVideo=true;assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,d));
        d.filteredVideo=false;d.url="https://bbs.hupu.com/123-2.html";assertFalse(BodyVideoEvidence.textTopic(Source.HUPU,d));
        d.url="https://s.weibo.com/weibo?q=%23topic%23";assertFalse(BodyVideoEvidence.textTopic(Source.WEIBO,d));
        d.url="https://bbs.hupu.com/123.html";d.unsupportedVideo=true;assertTrue(BodyVideoEvidence.textTopic(Source.HUPU,d)); // An attachment is not the topic's type.
    }
    @Test public void negativeTypeWatermarkRoundTripsAndKeepsCandidateEligible()throws Exception{
        HotCandidate text=new HotCandidate(Source.HUPU,"https://bbs.hupu.com/123.html","中文图文主帖","","",false,1,null,100,ContentLanguage.Body.UNKNOWN,"",0,300);
        HotCandidate restored=HotCandidate.fromJson(text.json());assertFalse(restored.video);assertEquals(300,restored.videoEvidenceAt);assertEquals(text.textHash,restored.textHash);
        assertEquals(1,CandidateOrder.arrange(java.util.List.of(restored),new org.json.JSONArray(),false).size());
    }
}
