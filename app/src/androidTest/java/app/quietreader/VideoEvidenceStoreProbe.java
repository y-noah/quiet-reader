package app.quietreader;

import android.app.Instrumentation;
import app.quietreader.discovery.*;
import java.util.*;
import java.util.function.BiConsumer;
import static app.quietreader.Models.*;

final class VideoEvidenceStoreProbe {
    static void run(Instrumentation test,BiConsumer<Boolean,String> check)throws Exception{
        String database="test-ui-video-store-"+UUID.randomUUID()+".db";long now=System.currentTimeMillis(),bodyAt=now-1000;
        HotCandidate candidate=new HotCandidate(Source.HUPU,"https://bbs.hupu.com/99007699.html","合成图文类型未知","","",false,1,null,now);
        try(FeedStore store=new FeedStore(test.getTargetContext(),database)){
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),now);long version=store.version("sources");
            check.accept(!store.recordVideo(candidate.id,"obsolete-hash",bodyAt)&&store.version("sources")==version,"old candidate version cannot classify replacement metadata");
            check.accept(!store.recordVideo(candidate.id,candidate.textHash,now+3600000)&&!store.recordVideo(candidate.id,candidate.textHash,now-FeedStore.VIDEO_EVIDENCE_TTL-1),"future and expired video evidence are rejected");
            check.accept(store.recordVideo(candidate.id,candidate.textHash,bodyAt)&&store.version("sources")==version+1,"whole-topic video evidence invalidates stale ranking input");
            HotCandidate recorded=store.candidates(10,0).get(0);
            check.accept(recorded.video&&recorded.videoEvidenceAt==bodyAt&&recorded.id.equals(candidate.id)&&recorded.textHash.equals(candidate.textHash),"video evidence preserves source and model text identity");
            check.accept(!store.recordVideo(candidate.id,candidate.textHash,bodyAt)&&!store.recordVideo(candidate.id,candidate.textHash,bodyAt-1),"duplicate and older video observations cannot churn source version");
            check.accept(CandidateOrder.arrange(store.candidates(10,0),new org.json.JSONArray(),false).isEmpty(),"candidate ordering excludes inferred videos");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),now+100);
            check.accept(store.candidates(10,0).get(0).video&&store.candidates(10,0).get(0).videoEvidenceAt==bodyAt,"ordinary source refresh preserves current video evidence without renewing it");
            store.recordBodyLanguage(candidate.id,candidate.textHash,ContentLanguage.Body.CHINESE,bodyAt);
            check.accept(store.candidates(10,0).get(0).videoEvidenceAt==bodyAt,"body language updates do not discard video evidence");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),bodyAt+FeedStore.VIDEO_EVIDENCE_TTL+1);
            check.accept(!store.candidates(10,0).get(0).video,"expired inferred type clears on fresh source metadata");
            store.recordVideo(candidate.id,candidate.textHash,bodyAt);
            HotCandidate replacement=new HotCandidate(candidate.source,candidate.originalUrl,"更新后的中文内容","","",false,1,null,now);
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(replacement),now);
            check.accept(!store.candidates(10,0).get(0).video&&!store.recordVideo(candidate.id,candidate.textHash,bodyAt),"changed content metadata rejects late video classification");
            HotCandidate sourceVideo=new HotCandidate(candidate.source,candidate.originalUrl,candidate.title,"","",true,1,null,now);
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(sourceVideo),now);
            check.accept(!store.recordVideo(sourceVideo.id,sourceVideo.textHash,bodyAt)&&store.candidates(10,0).get(0).videoEvidenceAt==0,"source-provided video type stays separate from inferred evidence time");
            check.accept(store.profile(now).getJSONArray("feedback").length()==0,"type evidence does not create preference or blacklist feedback");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),now);
            store.recordVideo(candidate.id,candidate.textHash,bodyAt);
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check.accept(reopened.candidates(10,0).get(0).videoEvidenceAt==bodyAt,"reopening SQLite preserves inferred video classification");}
            HotCandidate other=new HotCandidate(Source.HUPU,"https://bbs.hupu.com/99007700.html","另一个中文话题","","",false,1,null,now);
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(other),now);
            check.accept(!store.recordVideo(candidate.id,candidate.textHash,bodyAt+100),"late observation cannot update an inactive candidate");
            Document filtered=SourceParser.article(Source.HUPU,"<h1>合成视频主题</h1><div class=post-content_main-post-info__fixture><div class=thread-content-detail>视频说明</div><video src='https://v.hupucdn.com/fixture.mp4'></video></div>",candidate.originalUrl);
            boolean[] stamped={false};test.runOnMainSync(()->{Repository repo=new Repository(test.getTargetContext());try{repo.cacheArticle(filtered);stamped[0]=filtered.filteredVideo&&filtered.cachedAt>0&&filtered.cachedSession!=null&&filtered.cachedNamespace!=null&&repo.cachedArticle(candidate.readerItem())==null;}finally{repo.close();}});
            check.accept(stamped[0],"filtered source result keeps validated evidence stamp without caching its body");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),now);
            store.recordVideo(candidate.id,candidate.textHash,bodyAt);
            long beforeCorrection=store.version("sources"),textAt=bodyAt+100;
            check.accept(!store.recordTextTopic(candidate.id,"obsolete-hash",textAt)&&!store.recordTextTopic(candidate.id,candidate.textHash,bodyAt-1)&&!store.recordTextTopic(candidate.id,candidate.textHash,bodyAt),"wrong metadata, older and tied text cannot undo video evidence");
            check.accept(!store.recordTextTopic(candidate.id,candidate.textHash,now+3600000)&&!store.recordTextTopic(candidate.id,candidate.textHash,now-FeedStore.VIDEO_EVIDENCE_TTL-1),"future and expired text corrections are rejected");
            check.accept(store.recordTextTopic(candidate.id,candidate.textHash,textAt)&&store.version("sources")==beforeCorrection+1,"newer same-metadata primary text clears inferred video and invalidates ranking");
            HotCandidate corrected=store.candidates(10,0).get(0);
            check.accept(!corrected.video&&corrected.videoEvidenceAt==textAt&&corrected.textHash.equals(candidate.textHash)&&CandidateOrder.arrange(List.of(corrected),new org.json.JSONArray(),false).size()==1,"corrected topic re-enters eligible candidates with negative watermark");
            long correctedVersion=store.version("sources");
            check.accept(!store.recordVideo(candidate.id,candidate.textHash,bodyAt)&&!store.recordVideo(candidate.id,candidate.textHash,textAt)&&!store.recordTextTopic(candidate.id,candidate.textHash,textAt)&&store.version("sources")==correctedVersion,"late/tied positive and duplicate negative observations cannot revive video or churn source version");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),now+100);
            check.accept(!store.candidates(10,0).get(0).video&&store.candidates(10,0).get(0).videoEvidenceAt==textAt,"normal metadata refresh preserves negative watermark without renewing it");
            store.recordBodyLanguage(candidate.id,candidate.textHash,ContentLanguage.Body.CHINESE,textAt);
            try(FeedStore reopened=new FeedStore(test.getTargetContext(),database)){check.accept(!reopened.candidates(10,0).get(0).video&&reopened.candidates(10,0).get(0).videoEvidenceAt==textAt,"language writes and SQLite reopen preserve corrected type watermark");}
            check.accept(store.recordVideo(candidate.id,candidate.textHash,textAt+1)&&store.candidates(10,0).get(0).video,"strictly newer video acquisition may reclassify corrected topic");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(sourceVideo),now);
            check.accept(!store.recordTextTopic(sourceVideo.id,sourceVideo.textHash,textAt+2)&&store.candidates(10,0).get(0).video&&store.candidates(10,0).get(0).videoEvidenceAt==0,"text cannot override source-provided video type");
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),now);
            store.recordTextTopic(candidate.id,candidate.textHash,textAt);
            store.upsertSnapshot(Source.HUPU,store.beginRefresh(Source.HUPU),List.of(candidate),textAt+FeedStore.VIDEO_EVIDENCE_TTL+1);
            check.accept(store.candidates(10,0).get(0).videoEvidenceAt==0,"negative watermark also expires rather than becoming a permanent type lock");
            check.accept(store.profile(now).getJSONArray("feedback").length()==0,"type correction creates no interest feedback or blacklist");
        }finally{test.getTargetContext().deleteDatabase(database);}
    }
}
