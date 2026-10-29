package app.quietreader;

import android.app.*;
import android.os.Bundle;
import app.quietreader.discovery.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Real SQLite transactions, using an isolated temporary database, no source requests. */
public final class FeedStoreInstrumentation extends Instrumentation {
    private int checks,failures;
    private boolean liveSources;
    private boolean liveBrowser;
    private boolean discoveryUi;
    private boolean semantic;
    private boolean modelRepair;
    private String restartPhase,restartId,liveSource,liveTarget;
    private final StringBuilder report=new StringBuilder();
    private void check(boolean good,String label){checks++;if(!good)failures++;report.append(good?"PASS ":"FAIL ").append(label).append('\n');}
    @Override public void onCreate(Bundle args){super.onCreate(args);if(args!=null){restartPhase=args.getString("mode");restartId=args.getString("probeId");liveSource=args.getString("source");liveTarget=args.getString("target","");}modelRepair=args!=null&&"model-repair".equals(args.getString("mode"));semantic=args!=null&&"semantic".equals(args.getString("mode"));liveSources=args!=null&&"live-sources".equals(args.getString("mode"));liveBrowser=args!=null&&"live-browser".equals(args.getString("mode"));discoveryUi=args!=null&&"discovery-ui".equals(args.getString("mode"));start();}
    private static HotCandidate fixture(Models.Source source,int id,long now){return new HotCandidate(source,"https://example.org/topic/"+id,"合成中文话题"+id,"验证事务与缓存的合成内容","热度不参与跨平台相加",false,id+1,null,now);}
    @Override public void onStart(){String database="test-discovery-"+UUID.randomUUID()+".db";
        if("zhihu-slow-expansion".equals(restartPhase)){new ZhihuExpansionProbe(this).run();return;}
        if("dynamic-deadline".equals(restartPhase)||"dynamic-deadline-baseline".equals(restartPhase)){new DynamicDeadlineProbe(this).run("dynamic-deadline-baseline".equals(restartPhase));return;}
        if("login-extraction-worker".equals(restartPhase)){new LoginExtractionWorkerProbe(this).run();return;}
        if("promoted-reader-budget".equals(restartPhase)){new PromotedReaderBudgetProbe(this).run();return;}
        if("repository-transport".equals(restartPhase)){new RepositoryTransportProbe(this).run();return;}
        if("cancelled-ranking-input".equals(restartPhase)){new CancelledRankingInputProbe(this).run();return;}
        if("refresh-animation".equals(restartPhase)){new RefreshPresentationProbe(this,false,true).run();return;}
        if("home-fixed-batch".equals(restartPhase)){new RefreshPresentationProbe(this,false,false,true).run();return;}
        if("work-failure-scope".equals(restartPhase)){new WorkFailureScopeProbe(this).run();return;}
        if("article-followup".equals(restartPhase)){new ArticleFollowupProbe(this).run();return;}
        if("refresh-pull-pending".equals(restartPhase)||"refresh-exposure-boundary".equals(restartPhase)){new RefreshPresentationProbe(this,"refresh-pull-pending".equals(restartPhase)).run();return;}
        if("article-progress-scope".equals(restartPhase)){new ArticleProgressScopeProbe(this).run();return;}
        if("reader-image-font".equals(restartPhase)||"reader-image-font-background".equals(restartPhase)){new ReaderImageFontContinuationProbe(this,"reader-image-font-background".equals(restartPhase)).run();return;}
        if("article-progress-history".equals(restartPhase)){new ArticleProgressHistoryProbe(this).run();return;}
        if("article-progress".equals(restartPhase)){new ArticleProgressProbe(this).run();return;}
        if("tieba-child-times".equals(restartPhase)){new TiebaReplyTimesProbe(this).run();return;}
        if("discussion-reply-evidence".equals(restartPhase)){new DiscussionReplyEvidenceProbe(this).run();return;}
        if("hupu-discussion-progress".equals(restartPhase)){new DiscussionProgressProbe(this,Models.Source.HUPU).run();return;}
        if("tieba-discussion-progress".equals(restartPhase)){new DiscussionProgressProbe(this,Models.Source.TIEBA).run();return;}
        if("discussion-progress".equals(restartPhase)){new DiscussionProgressProbe(this).run();return;}
        if("recommendation-refresh-discussion".equals(restartPhase)){new RecommendationRefreshProbe(this,true).run();return;}
        if("recommendation-refresh".equals(restartPhase)){new RecommendationRefreshProbe(this).run();return;}
        if("refresh-snapshot".equals(restartPhase)){new RefreshSnapshotProbe(this).run();return;}
        if("exposure-viewport".equals(restartPhase)){new ExposureViewportProbe(this).run();return;}
        if("exposure-history".equals(restartPhase)){new ExposureHistoryProbe(this).run();return;}
        if("update-dot-top".equals(restartPhase)){new UpdateDotTopProbe(this).run();return;}
        if("reader-early-continuation".equals(restartPhase)||"reader-early-passive".equals(restartPhase)||"reader-early-background".equals(restartPhase)){new ReaderEarlyContinuationProbe(this,"reader-early-passive".equals(restartPhase)?"passive":"reader-early-background".equals(restartPhase)?"background":"forward").run();return;}
        if("explicit-feed-cache-action".equals(restartPhase)){new ExplicitFeedCacheActionProbe(this).run();return;}
        if("default-cache-pressure".equals(restartPhase)){new DefaultCachePressureProbe(this).run();return;}
        if("queued-article-identity".equals(restartPhase)){new QueuedArticleIdentityProbe(this).run();return;}
        if("live-batch-cache".equals(restartPhase)){new LiveBatchCacheProbe(this).run();return;}
        if("live-prefetch-natural-expiry".equals(restartPhase)){new LivePrefetchSessionProbe(this,true,true,true).run();return;}
        if("reader-cache-choice".equals(restartPhase)){new ReaderCacheChoiceProbe(this).run();return;}
        if("live-bbc-chart".equals(restartPhase)){new LivePrefetchProbe(this,"BBC","https://www.bbc.com/zhongwen/articles/c680zjdvk7kgo/simp").run();return;}
        if("bbc-cache-diagnosis".equals(restartPhase)){new BbcCacheDiagnosisProbe(this).run();return;}
        if("live-prefetch-multisource".equals(restartPhase)){new LivePrefetchSessionProbe(this,true,true).run();return;}
        if("image-cache-limit".equals(restartPhase)){new ImageCacheLimitProbe(this).run();return;}
        if("settings-insets".equals(restartPhase)){new SettingsInsetsProbe(this).run();return;}
        if("reader-source-accessibility".equals(restartPhase)){new ReaderSourceAccessibilityProbe(this).run();return;}
        if("feed-startup".equals(restartPhase)){new FeedStartupProbe(this).run();return;}
        if("feed-startup-no-stacks".equals(restartPhase)){new FeedStartupProbe(this,false).run();return;}
        if("repository-migration".equals(restartPhase)){new RepositoryMigrationProbe(this).run();return;}
        if("reader-native-cancel".equals(restartPhase)||"reader-native-explicit".equals(restartPhase)){new ReaderNativeFrameProbe(this,"reader-native-cancel".equals(restartPhase)).run();return;}
        if("feed-navigation".equals(restartPhase)){new FeedNavigationProbe(this).run();return;}
        if("legacy-navigation-removal".equals(restartPhase)){new LegacyNavigationRemovalProbe(this).run();return;}
        if("minimal-reader-dark".equals(restartPhase)||"minimal-reader-light".equals(restartPhase)){new ReaderActionsProbe(this,"minimal-reader-dark".equals(restartPhase)).run();return;}
        if("event-verification-model".equals(restartPhase)){new EventVerificationModelProbe(this).run();return;}
        if("event-verification-holdout".equals(restartPhase)){new EventVerificationModelProbe(this,true).run();return;}
        if("event-group-assembly".equals(restartPhase)){new EventGroupAssemblyProbe(this).run();return;}
        if("event-recommendation".equals(restartPhase)){new EventRecommendationProbe(this).run();return;}
        if("event-input-coverage".equals(restartPhase)){new EventInputCoverageProbe(this).run();return;}
        if("event-controller".equals(restartPhase)){new EventControllerProbe(this).run();return;}
        if("event-new-window-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-new-window-20261028.json","event-new-window-quality",8,6,6).run();return;}
        if("event-full-window-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-full-window-20261028.json","event-full-window-quality",8,6,6).run();return;}
        if("event-independent-window-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-independent-window-20261029.json","event-independent-window-quality",11,7,6).run();return;}
        if("event-admission-safety-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/model-admission-safety-20261029.json","event-admission-safety-quality",0,0,11,true).run();return;}
        if("event-admission-controls-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/model-admission-controls-20261029.json","event-admission-controls-quality",2,5,1,true).run();return;}
        if("event-noon-window-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-noon-window-20261029.json","event-noon-window-quality",3,4,6).run();return;}
        if("event-question-controls-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/question-frame-controls-20261029.json","event-question-controls-quality",1,1,20,true).run();return;}
        if("event-statement-boundary-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/statement-boundary-controls-20261028.json","event-statement-boundary-quality",1,1,6).run();return;}
        if("event-fresh-quality".equals(restartPhase)){new EventFreshQualityProbe(this).run();return;}
        if("event-residual-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-residual-20261027.json","event-residual-quality",8,7,5).run();return;}
        if("event-later-window-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-later-window-20261027.json","event-later-window-quality",6,6,4).run();return;}
        if("event-evening-window-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-evening-window-20261027.json","event-evening-window-quality",5,4,3).run();return;}
        if("event-independent-quality".equals(restartPhase)){new EventFreshQualityProbe(this,false,"fixtures/observed-event-independent-20261027.json","event-independent-quality",6,5,3).run();return;}
        if("event-input-ablation".equals(restartPhase)){new EventInputAblationProbe(this).run();return;}
        if("event-trailing-ablation".equals(restartPhase)){new EventInputAblationProbe(this,true).run();return;}
        if("event-rate-focus-ablation".equals(restartPhase)){new EventInputAblationProbe(this,"fixtures/rate-focus-ablation-20261029.json","event-rate-focus-ablation.json").run();return;}
        if("event-polarity-quality".equals(restartPhase)){new EventFreshQualityProbe(this,true).run();return;}
        if("sports-event-flow".equals(restartPhase)){new SportsEventFlowProbe(this).run();return;}
        if("recommendation-continuation".equals(restartPhase)){new RecommendationAutonomyProbe(this,false,true).run();return;}
        if("recommendation-autonomy-background".equals(restartPhase)){new RecommendationAutonomyProbe(this,true).run();return;}
        if("recommendation-autonomy".equals(restartPhase)){new RecommendationAutonomyProbe(this).run();return;}
        if("recommendation-completion".equals(restartPhase)){new RecommendationCompletionProbe(this).run();return;}
        if("event-window-replay".equals(restartPhase)){new EventWindowReplayProbe(this).run();return;}
        if("model-reading-live".equals(restartPhase)||"model-reading-live-rerank".equals(restartPhase)){new ModelReadingInterruptionProbe(this,restartPhase.endsWith("rerank"),liveSource==null?Models.Source.THEPAPER:Models.Source.valueOf(liveSource)).run();return;}
        if("model-reading-interruption".equals(restartPhase)){new ModelReadingInterruptionProbe(this).run();return;}
        if("model-reading-rerank".equals(restartPhase)){new ModelReadingInterruptionProbe(this,true).run();return;}
        if("cached-reader-latency-cold".equals(restartPhase)||"cached-reader-latency-warm".equals(restartPhase)){new CachedReaderLatencyProbe(this,"cached-reader-latency-warm".equals(restartPhase)).run();return;}
        if("comparison-learning".equals(restartPhase)){new ComparisonLearningProbe(this).run();return;}
        if("trial-export-saf".equals(restartPhase)){new TrialExportSafProbe(this).run();return;}
        if("trial-observation-snapshot".equals(restartPhase)){new TrialObservationSnapshotProbe(this).run();return;}
        if("prospective-evaluation".equals(restartPhase)){new ProspectiveEvaluationProbe(this).run();return;}
        if("prospective-review-export".equals(restartPhase)){new ProspectiveEvaluationProbe(this,true).run();return;}
        if("recommendation-trial".equals(restartPhase)){new RecommendationTrialProbe(this).run();return;}
        if("grouped-filter-preference".equals(restartPhase)){new GroupedFilterPreferenceProbe(this).run();return;}
        if("thepaper-category-ui".equals(restartPhase)){new ThepaperCategoryProbe(this).run();return;}
        if("feedback-restart-write".equals(restartPhase)||"feedback-restart-read".equals(restartPhase)||"feedback-restart-final".equals(restartPhase)){new FeedbackRestartProbe(this).run(restartPhase,restartId);return;}
        if("memory-process-set".equals(restartPhase)){new app.quietreader.ai.MemoryProcessSetProbe(this).run();return;}
        if("discovery-supply".equals(restartPhase)){new DiscoverySupplyProbe(this).run();return;}
        if("discovery-prefetch-supply".equals(restartPhase)){new DiscoveryPrefetchSupplyProbe(this).run();return;}
        if("discovery-prefetch-supply-warm".equals(restartPhase)){new DiscoveryPrefetchSupplyProbe(this,true).run();return;}
        if("live-discovery-supply".equals(restartPhase)){new LiveDiscoverySupplyProbe(this).run();return;}
        if("rerank-reuse".equals(restartPhase)){new app.quietreader.ai.RerankReuseProbe(this).run();return;}
        if("model-verification".equals(restartPhase)){new app.quietreader.ai.ModelVerificationProbe(this).run();return;}
        if("pending-acceptance-race".equals(restartPhase)){new PendingAcceptanceRaceProbe(this).run();return;}
        if("recommendation-status".equals(restartPhase)){new RecommendationStatusProbe(this).run();return;}
        if("live-prefetch-session".equals(restartPhase)){new LivePrefetchSessionProbe(this).run();return;}
        if("live-prefetch-session-warm".equals(restartPhase)){new LivePrefetchSessionProbe(this,true).run();return;}
        if("prefetch-disk-reuse".equals(restartPhase)){new PrefetchDiskReuseProbe(this).run();return;}
        if("prefetch-account-cooldown".equals(restartPhase)){new PrefetchAccountCooldownProbe(this).run();return;}
        if("reader-startup-cancel".equals(restartPhase)){new ReaderStartupFailureProbe(this,true).run();return;}
        if("reader-startup-failure".equals(restartPhase)){new ReaderStartupFailureProbe(this).run();return;}
        if("reader-startup".equals(restartPhase)){new ReaderStartupProbe(this).run();return;}
        if("recommendation-commit".equals(restartPhase)){new RecommendationCommitProbe(this).run();return;}
        if("image-prefetch".equals(restartPhase)){new ImagePrefetchProbe(this).run();return;}
        if("image-prefetch-policy".equals(restartPhase)){new ImagePrefetchProbe(this,true).run();return;}
        if("live-image-prefetch".equals(restartPhase)){new LiveImagePrefetchProbe(this,liveSource).run();return;}
        if("reader-rotation".equals(restartPhase)){new ReaderRotationProbe(this).run();return;}
        if("reader-rotation-loading".equals(restartPhase)){new ReaderRotationProbe(this,true).run();return;}
        if("pending-article-rotation".equals(restartPhase)){new PendingArticleRotationProbe(this).run();return;}
        if("promoted-native-rotation".equals(restartPhase)){new PendingArticleRotationProbe(this,true).run();return;}
        if("dynamic-rotation".equals(restartPhase)){new DynamicRotationProbe(this).run();return;}
        if("image-preview-layout".equals(restartPhase)){new ImagePreviewLayoutProbe(this).run();return;}
        if("reader-image-failure".equals(restartPhase)){new ReaderImageFailureProbe(this).run();return;}
        if("reader-image-flow".equals(restartPhase)){new ReaderImageFlowProbe(this).run();return;}
        if("reader-image-ownership".equals(restartPhase)){new ReaderImageOwnershipProbe(this).run();return;}
        if("shared-image-sockets".equals(restartPhase)){new SharedImageSocketProbe(this).run();return;}
        if("history-position-prepare".equals(restartPhase)||"history-position-restore".equals(restartPhase)){new ReaderHistoryProcessProbe(this,restartPhase.endsWith("prepare"),restartId,liveTarget).run();return;}
        if("reader-image-session".equals(restartPhase)){new ReaderImageSessionProbe(this).run();return;}
        if("image-offline-prepare".equals(restartPhase)||"image-offline-restore".equals(restartPhase)){new ImageOfflineProbe(this,restartPhase.endsWith("prepare")?"prepare":"restore",restartId).run();return;}
        if("image-cache-clear".equals(restartPhase)){new ImageCacheClearProbe(this).run();return;}
        if("live-browser-config".equals(restartPhase)){new BrowserConfigurationProbe(this,restartId,liveTarget).run();return;}
        if("board-commit".equals(restartPhase)){new BoardCommitProbe(this).run();return;}
        if("dynamic-reader-budget".equals(restartPhase)){new DynamicReaderBudgetProbe(this).run();return;}
        if("board-commit-hydration".equals(restartPhase)){new BoardCommitProbe(this,true).run();return;}
        if("answer-api-budget".equals(restartPhase)){new AnswerApiBudgetProbe(this).run();return;}
        if("discussion-flow".equals(restartPhase)){new DiscussionFlowProbe(this).run();return;}
        if("reader-link-navigation".equals(restartPhase)){new ReaderLinkNavigationProbe(this).run();return;}
        if("webview-accessibility-control".equals(restartPhase)){new ReaderTypographyProbe(this,false,true,"screenreader".equals(liveTarget)).run(true);return;}
        if("reader-layout-dark".equals(restartPhase)||"reader-layout-light".equals(restartPhase)){new ReaderTypographyProbe(this,"reader-layout-dark".equals(restartPhase),("normal".equals(liveTarget)||"screenreader-normal".equals(liveTarget)),liveTarget!=null&&liveTarget.startsWith("screenreader-")).run();return;}
        if("v2ex-fallback".equals(restartPhase)){new V2exFallbackProbe(this).run();return;}
        if("platform-preference".equals(restartPhase)){new PlatformPreferenceProbe(this).run();return;}
        if("platform-preference-dark".equals(restartPhase)){new PlatformPreferenceProbe(this,true).run();return;}
        if("reader-prefetch".equals(restartPhase)){new ReaderPrefetchProbe(this).run();return;}
        if("directional-prefetch".equals(restartPhase)){new DirectionalPrefetchProbe(this).run();return;}
        if("socket-cancellation".equals(restartPhase)){new SocketCancellationProbe(this).run();return;}
        if("webview-socket-cancellation".equals(restartPhase)){new WebViewSocketCancellationProbe(this).run();return;}
        if("answer-stream-background".equals(restartPhase)){new AnswerStreamBackgroundProbe(this).run();return;}
        if("prefetch-lifecycle".equals(restartPhase)){new PrefetchLifecycleProbe(this).run();return;}
        if("live-prefetch".equals(restartPhase)){new LivePrefetchProbe(this,liveSource).run();return;}
        if("slow-image-reading".equals(restartPhase)){new SlowImageReadingProbe(this).run();return;}
        if("slow-image-only".equals(restartPhase)){new SlowImageReadingProbe(this,true).run();return;}
        if("feedback-fallback".equals(restartPhase)){new app.quietreader.ai.FeedbackFallbackProbe(this).run();return;}
        if("grouped-feedback".equals(restartPhase)){new GroupedFeedbackProbe(this).run();return;}
        if("tieba-emoji-light".equals(restartPhase)||"tieba-emoji-dark".equals(restartPhase)||"tieba-emoji-live".equals(restartPhase)){new TiebaEmojiProbe(this,"tieba-emoji-dark".equals(restartPhase),"tieba-emoji-live".equals(restartPhase)).run();return;}
        if("login-back-flow".equals(restartPhase)){new LoginBackFlowProbe(this).run();return;}
        if("login-read-flow".equals(restartPhase)){new LoginReadFlowProbe(this).run();return;}
        if("hupu-native-routing".equals(restartPhase)){new HupuNativeRoutingProbe(this).run();return;}
        if("tieba-topic-flow".equals(restartPhase)){new TiebaTopicFlowProbe(this).run();return;}
        if("tieba-virtual-replies".equals(restartPhase)){new TiebaRepliesProbe(this).run();return;}
        if("tieba-child-progress".equals(restartPhase)){new TiebaNestedProgressProbe(this).run();return;}
        if("tieba-rehydrate".equals(restartPhase)){new TiebaRepliesProbe(this,true).run();return;}
        if("tieba-rehydrate-slow".equals(restartPhase)){new TiebaRepliesProbe(this,true,true).run();return;}
        if("tieba-selection-ui".equals(restartPhase)){new TiebaSelectionUiProbe(this).run();return;}
        if("tieba-process-prepare".equals(restartPhase)||"tieba-process-cleanup".equals(restartPhase)){new TiebaSelectionUiProbe(this,restartPhase.endsWith("prepare")?"prepare":"cleanup",restartId).run();return;}
        if("tieba-live-replies".equals(restartPhase)){new TiebaLiveRepliesProbe(this).run();return;}
        if("tieba-virtual-ui".equals(restartPhase)||"tieba-virtual-ui-dark".equals(restartPhase)){new TiebaVirtualUiProbe(this,"tieba-virtual-ui-dark".equals(restartPhase)).run();return;}
        if("source-browse-import".equals(restartPhase)){new SourceBrowseImportProbe(this).run();return;}
        if("video-evidence-flow".equals(restartPhase)){new VideoEvidenceFlowProbe(this).run();return;}
        if("legacy-video-revalidation".equals(restartPhase)){new LegacyVideoRevalidationProbe(this).run();return;}
        if("article-version-flow".equals(restartPhase)){new ArticleVersionFlowProbe(this).run();return;}
        if("video-evidence-store".equals(restartPhase)){try{VideoEvidenceStoreProbe.run(this,this::check);}catch(Exception error){failures++;report.append("ERROR ").append(error).append('\n');}report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
        if("storage-churn".equals(restartPhase)||"storage-churn-physical".equals(restartPhase)){try{StorageChurnProbe.run(this,this::check,"storage-churn-physical".equals(restartPhase));}catch(Exception error){failures++;report.append("ERROR ").append(error).append('\n');}report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
        if("article-disk".equals(restartPhase)){try{ArticleDiskCacheProbe.run(this,this::check);}catch(Exception error){failures++;report.append("ERROR ").append(error).append('\n');}report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
        if("cookie-rotation".equals(restartPhase)){new CookieRotationProbe(this).run();return;}
        if("structured-reader-flow".equals(restartPhase)){new StructuredReaderFlowProbe(this).run();return;}
        if("douyin-reader-contract".equals(restartPhase)){new DouyinReaderProbe(this).run();return;}
        if("douyin-activity-cookie".equals(restartPhase)){new DouyinReaderProbe(this,true).run();return;}
        if("xhs-reader-contract".equals(restartPhase)){new XhsReaderProbe(this).run();return;}
        if("explicit-video-reading".equals(restartPhase)){new ExplicitVideoFlowProbe(this).run();return;}
        if("multiple-video-reading".equals(restartPhase)){new ExplicitVideoFlowProbe(this,true).run();return;}
        if("automatic-recommendation".equals(restartPhase)){new AutomaticRecommendationProbe(this).run();return;}
        if("automatic-cold".equals(restartPhase)){new AutomaticRecommendationProbe(this,true).run();return;}
        if("body-language-flow".equals(restartPhase)){new BodyLanguageFlowProbe(this).run();return;}
        if("model-recovery".equals(restartPhase)){new ModelRecoveryProbe(this).run();return;}
        if("model-rollback".equals(restartPhase)){new app.quietreader.ai.ModelRollbackProbe(this).run();return;}
        if("recommendation-recovery".equals(restartPhase)){new app.quietreader.ai.RecommendationRecoveryProbe(this).run();return;}
        if("live-text-refresh".equals(restartPhase)){new LiveTextRefreshProbe(this).run();return;}
        if("live-text-sources".equals(restartPhase)){new HotSourceLiveProbe(this,true,null,true).run();return;}
        if("recommendation-stages".equals(restartPhase)){new app.quietreader.ai.RecommendationStagesProbe(this).run();return;}
        if("text-ranking-replay".equals(restartPhase)){new TextRankingReplayProbe(this).run();return;}
        if("text-feedback-replay".equals(restartPhase)){new TextRankingReplayProbe(this,true).run();return;}
        if("ranking-handoff".equals(restartPhase)){new RankingHandoffProbe(this).run();return;}
        if("ranking-pending-bind".equals(restartPhase)){new RankingPendingBindProbe(this).run();return;}
        if("batch-cache-retention".equals(restartPhase)){new BatchCacheRetentionProbe(this).run();return;}
        if("batch-full-prefetch".equals(restartPhase)){new BatchFullPrefetchProbe(this).run();return;}
        if("live-production-sources".equals(restartPhase)){new HotSourceLiveProbe(this,true).run();return;}
        if("live-production-source".equals(restartPhase)){new HotSourceLiveProbe(this,true,Models.Source.valueOf(liveSource)).run();return;}
        if("read-interest".equals(restartPhase)){new ReadInterestUiProbe(this).run();return;}
        if("quick-exit-interest".equals(restartPhase)){new ReadInterestUiProbe(this).quickExit().run();return;}
        if("inline-video".equals(restartPhase)){new InlineVideoProbe(this).run();return;}
        if("inline-video-no-capture".equals(restartPhase)){new InlineVideoProbe(this,false).run();return;}
        if(restartPhase!=null&&restartPhase.startsWith("quick-exit-exclude-")){
            String exclusion=restartPhase.substring("quick-exit-exclude-".length());
            if(!java.util.Set.of("short","stale","login","loading","background").contains(exclusion))throw new IllegalArgumentException("Unknown quick-exit exclusion");
            new ReadInterestUiProbe(this).quickExitExclusion(exclusion).run();return;
        }
        if("read-interest-store".equals(restartPhase)){try{ReadInterestProbe.run(getTargetContext(),this::check);}catch(Exception error){failures++;report.append("ERROR ").append(error).append('\n');}report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);return;}
        if("read-interest-stale-version".equals(restartPhase)){new ReadInterestUiProbe(this,true).run();return;}
        if("read-interest-offline-snapshot".equals(restartPhase)){new ReadInterestUiProbe(this,false,true).run();return;}
        if("dynamic-handoff".equals(restartPhase)){new DynamicHandoffProbe(this).run();return;}
        if("promoted-rotation".equals(restartPhase)){new DynamicHandoffProbe(this,true).run();return;}
        if("promoted-background".equals(restartPhase)){new DynamicHandoffProbe(this,true,true).run();return;}
        if("layout-dark".equals(restartPhase)||"layout-light".equals(restartPhase)){new DiscoveryLayoutProbe(this,"layout-dark".equals(restartPhase)).run();return;}
        if("live-articles".equals(restartPhase)){new ArticleLiveProbe(this,liveSource).run();return;}
        if("live-articles-browser".equals(restartPhase)){new ArticleLiveProbe(this,liveSource).browserOnly().run();return;}
        if("live-articles-native".equals(restartPhase)){new ArticleLiveProbe(this,liveSource).nativeFirst().run();return;}
        if("live-articles-ui".equals(restartPhase)){new ArticleLiveProbe(this,liveSource,true).run();return;}
        if("live-video-ui".equals(restartPhase)){new ArticleLiveProbe(this,liveSource,true,true).run();return;}
        if("live-note-target".equals(restartPhase)){new ArticleLiveProbe(this,liveSource,false,false,liveTarget).run();return;}
        if("tieba-live-times".equals(restartPhase)){new ArticleLiveProbe(this,"TIEBA",false,false,liveTarget).browserOnly().withReplyTimes().run();return;}
        if("live-note-target-ui".equals(restartPhase)){new ArticleLiveProbe(this,liveSource,true,false,liveTarget).run();return;}
        if("cache-restart-write".equals(restartPhase)||"cache-restart-read".equals(restartPhase)){ArticleRestartProbe.run(this,restartPhase,restartId);return;}
        if(semantic){new RecommendationProbe(this).run();return;}
        if(modelRepair){new ModelRepairProbe(this).run();return;}
        if(liveSources){new HotSourceLiveProbe(this).run();return;}
        if(liveBrowser){new HotSourceBrowserProbe(this,liveSource,liveTarget).run();return;}
        if(discoveryUi){new DiscoveryUiProbe(this).run();return;}
        Map<String,?> sessionBefore=getTargetContext().getSharedPreferences("source-session",0).getAll();
        long snapshot=-1;HotCandidate first=fixture(Models.Source.ZHIHU,1,100);
        try{
            try(FeedStore store=new FeedStore(getTargetContext(),database)){
                long old=store.beginRefresh(Models.Source.ZHIHU),fresh=store.beginRefresh(Models.Source.ZHIHU);
                check(!store.upsertSnapshot(Models.Source.ZHIHU,old,Arrays.asList(first),100),"stale source request cannot commit");
                check(store.upsertSnapshot(Models.Source.ZHIHU,fresh,Arrays.asList(first,first),100),"current source request commits");
                check(store.candidates(100,0).size()==1,"repeated source identity is idempotent");
                long sources=store.version("sources");
                check(!store.upsertSnapshot(Models.Source.ZHIHU,fresh,Arrays.asList(first),200)&&store.version("sources")==sources,"repeated successful revision cannot fake freshness");
                long failed=store.beginRefresh(Models.Source.ZHIHU);
                check(store.failRefresh(Models.Source.ZHIHU,failed,"OFFLINE","NETWORK"),"failure updates its own request");
                check(store.sourceState(Models.Source.ZHIHU).getLong("lastSuccessAt")==100&&store.candidates(100,0).size()==1,"failure preserves successful timestamp and cached candidates");
                check(!store.failRefresh(Models.Source.ZHIHU,old,"UNAVAILABLE","STALE"),"stale failure cannot overwrite current source state");
                app.quietreader.discovery.LegacySavedFixture.save(store,"event-one",first,110);
                float[] vector=new float[512];vector[0]=1;store.putVector(first.textHash,"model-a","tokenizer-a",vector,120);
                String key=FeedStore.vectorKey(first.textHash,"model-a","tokenizer-a");
                long profile=store.version("profile");
                check(store.feedback("more-1","MORE","sample",first.id,130,null,first.modelText()),"explicit feedback persists");
                check(!store.feedback("more-1","MORE","sample",first.id,130,null,first.modelText()),"duplicate feedback does not double count");
                check(store.version("profile")==profile+1&&store.vector(key,140)!=null,"feedback changes profile but retains document vectors");
                check(store.commitRanking(sources,profile,140,"personal","all",Arrays.asList(new FeedStore.Row("event-one",first,"合成理由")))==-1,"stale profile cannot publish ranking");
                profile=store.version("profile");
                snapshot=store.commitRanking(sources,profile,150,"personal","all",Arrays.asList(new FeedStore.Row("event-one",first,"合成理由")));
                check(snapshot>0&&store.readSnapshot(snapshot,50,0).size()==1,"frozen feed snapshot commits and reads");
                boolean rolledBack=false;
                try{store.commitRanking(sources,profile,160,"personal","all",Arrays.asList(new FeedStore.Row("same",first,""),new FeedStore.Row("same",first,"")));}catch(android.database.sqlite.SQLiteConstraintException expected){rolledBack=true;}
                check(rolledBack&&store.latestSnapshot()==snapshot,"interrupted invalid snapshot rolls back all rows and header");
                check(store.undo("more-1",170)&&!store.undo("more-1",171),"undo is replayable and idempotent");
                check(store.activeFeedback(180).length()==0,"undone positive sample is absent from rebuilt profile");
                store.feedback("temporary","FOLLOW","sample",first.id,180,200L,first.title);
                long temporaryProfile=store.profile(190).getLong("version");
                check(store.commitRanking(sources,temporaryProfile,200,"personal","all",Collections.emptyList())==-1,"temporary interest expiry invalidates running inference at commit");
                check(store.profile(200).getLong("version")==temporaryProfile+1&&store.profile(210).getLong("version")==temporaryProfile+1,"expiry changes effective profile exactly once without timer");
                check(store.activeFeedback(190).length()==0&&store.activeFeedback(200).length()==0,"expired interest stays expired after device clock rollback");
                store.enable(Models.Source.ZHIHU,false);
                check(store.candidates(100,0).isEmpty()&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1,"source disabled without deleting saved metadata");
                check(store.commitRanking(sources,store.version("profile"),210,"personal","all",Collections.emptyList())==-1,"changed source settings invalidate queued ranking");
                store.enable(Models.Source.ZHIHU,true);
                store.resetProfile();
                check(store.activeFeedback(220).length()==0&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.vector(key,220)!=null,"reset profile preserves saved entries and cached embeddings");
                store.activateSnapshot(snapshot);
                for(int n=0;n<5;n++)store.commitRanking(store.version("sources"),store.version("profile"),230+n,"cold","all",Arrays.asList(new FeedStore.Row("new-event-"+n,first,"")));
                check(store.activeSnapshot()==snapshot&&store.readSnapshot(snapshot,50,0).size()==1,"pending feed updates cannot evict the snapshot being read");
                try(android.database.Cursor count=store.getWritableDatabase().rawQuery("SELECT COUNT(*) FROM snapshots",null)){count.moveToFirst();check(count.getInt(0)<=3,"pinned feed and pending updates remain within three snapshots");}
                // Construct the original v1 layout, then exercise the real helper upgrade.
                store.getWritableDatabase().execSQL("ALTER TABLE sources DROP COLUMN retry_at");
                store.getWritableDatabase().execSQL("ALTER TABLE sources DROP COLUMN failures");
                store.getWritableDatabase().execSQL("DELETE FROM meta WHERE key='profile_time'");
                store.getWritableDatabase().execSQL("DROP TABLE reranks");
                store.getWritableDatabase().execSQL("ALTER TABLE feedback DROP COLUMN source");
                store.getWritableDatabase().execSQL("DROP TABLE feed_exposure");store.getWritableDatabase().execSQL("DROP TABLE article_progress");store.getWritableDatabase().setVersion(1);
            }
            try(FeedStore store=new FeedStore(getTargetContext(),database)){
                check(store.getWritableDatabase().getVersion()==9&&store.sourceState(Models.Source.ZHIHU).getLong("retryAt")==0,"v1 database upgrades retry, expiry, rerank cache and source provenance without destructive reset");
                check(store.candidates(100,0).size()==1&&store.readSnapshot(snapshot,50,0).get(0).candidate.id.equals(first.id),"process-style reopen restores source and frozen feed");
                store.clearDerivedCache();
                check(store.candidates(100,0).isEmpty()&&store.latestSnapshot()==0&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1,"clear derived cache preserves explicit saved metadata");
                check(store.sourceState(Models.Source.ZHIHU).getLong("lastSuccessAt")==0,"cache clear cannot advertise absent content as fresh");
                long cancelled=store.beginRefresh(Models.Source.ZHIHU);
                store.cancelRefresh(Models.Source.ZHIHU,cancelled);
                check(!store.upsertSnapshot(Models.Source.ZHIHU,cancelled,Arrays.asList(first),300),"cancel invalidates late commit at the database boundary");
                long disabled=store.beginRefresh(Models.Source.ZHIHU);
                store.enable(Models.Source.ZHIHU,false);
                check(!store.upsertSnapshot(Models.Source.ZHIHU,disabled,Arrays.asList(first),300)&&store.beginRefresh(Models.Source.ZHIHU)<0,"disabled source refuses pending commits and new requests");
                store.enable(Models.Source.ZHIHU,true);
                long limited=store.beginRefresh(Models.Source.ZHIHU,1000);
                check(store.failRefresh(Models.Source.ZHIHU,limited,"RATE_LIMITED","HTTP429",200_000,1000),"rate limit recorded");
                check(store.beginRefresh(Models.Source.ZHIHU,199_999)<0&&store.sourceState(Models.Source.ZHIHU).getLong("retryAt")==200_000,"server retry deadline persists without new requests");
                check(!store.upsertSnapshot(Models.Source.ZHIHU,limited,Arrays.asList(first),300)&&!store.failRefresh(Models.Source.ZHIHU,limited,"OFFLINE","late"),"terminal failure cannot be overwritten by duplicate callbacks");
                long resumed=store.beginRefresh(Models.Source.ZHIHU,200_000);
                check(store.upsertSnapshot(Models.Source.ZHIHU,resumed,Arrays.asList(first),200_001)&&store.sourceState(Models.Source.ZHIHU).getLong("retryAt")==0,"successful user retry resets backoff");
                float[] unit=new float[512];unit[0]=1;store.putVector(first.textHash,"model-a","tokenizer-a",unit,400);
                String corruptKey=FeedStore.vectorKey(first.textHash,"model-a","tokenizer-a");
                byte[] invalid=new byte[2048];java.nio.ByteBuffer.wrap(invalid).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN);
                store.getWritableDatabase().execSQL("UPDATE embeddings SET vector=? WHERE key=?",new Object[]{invalid,corruptKey});
                check(store.vector(corruptKey,410)==null,"corrupt nonfinite embedding rejected and evicted");
                store.putVector(first.textHash,"model-a","tokenizer-a",unit,420);
                store.getWritableDatabase().execSQL("UPDATE embeddings SET vector='invalid-type' WHERE key=?",new Object[]{corruptKey});
                check(store.vector(corruptKey,430)==null,"embedding with wrong SQLite type becomes a cache miss");
                long beforePolicy=store.version("sources");store.ensureCandidatePolicy();long afterPolicy=store.version("sources");store.ensureCandidatePolicy();
                check(afterPolicy==beforePolicy+1&&store.version("sources")==afterPolicy,"new candidate language policy invalidates old snapshots exactly once");
            }
            cacheUpgradeProbe(database+"-v2",first);
            cacheSpaceProbe(database+"-space",first);
            try(FeedStore language=new FeedStore(getTargetContext(),database+"-language")){
                long request=language.beginRefresh(Models.Source.ZHIHU);language.upsertSnapshot(Models.Source.ZHIHU,request,Collections.singletonList(first),100);
                long version=language.version("sources");
                check(!language.recordBodyLanguage(first.id,"obsolete",ContentLanguage.Body.NON_CHINESE)&&language.version("sources")==version,"stale body language evidence cannot change a newer metadata version");
                check(language.recordBodyLanguage(first.id,first.textHash,ContentLanguage.Body.NON_CHINESE)&&CandidateOrder.arrange(language.candidates(100,0),new org.json.JSONArray(),false).isEmpty(),"persisted non-Chinese body evidence excludes the next recommendation");
                long changed=language.version("sources");
                check(!language.recordBodyLanguage(first.id,first.textHash,ContentLanguage.Body.NON_CHINESE)&&language.version("sources")==changed,"repeated body language evidence does not repeatedly invalidate ranking");
                HotCandidate evidence=language.candidates(100,0).get(0);long bodyAt=evidence.bodyLanguageAt;
                HotCandidate refreshed=new HotCandidate(first.source,first.originalUrl,first.title,first.snippet,"new heat",false,7,null,bodyAt+1000);
                long next=language.beginRefresh(first.source);language.upsertSnapshot(first.source,next,Collections.singletonList(refreshed),bodyAt+1000);
                HotCandidate retained=language.candidates(100,0).get(0);
                check(retained.bodyLanguage==ContentLanguage.Body.NON_CHINESE&&retained.bodyLanguageAt==bodyAt&&retained.sourceRank==7&&retained.rawHeat.equals("new heat"),"refresh preserves fresh exact-version body evidence while updating source rank and heat");
                next=language.beginRefresh(first.source);language.upsertSnapshot(first.source,next,Collections.singletonList(refreshed),bodyAt+2000);
                check(language.candidates(100,0).get(0).bodyLanguageAt==bodyAt,"repeated source refresh cannot renew the original body evidence timestamp");
                next=language.beginRefresh(first.source);language.upsertSnapshot(first.source,next,Collections.singletonList(refreshed),bodyAt+FeedStore.BODY_LANGUAGE_TTL+1);
                check(language.candidates(100,0).get(0).bodyLanguage==ContentLanguage.Body.UNKNOWN,"expired body evidence returns to unknown on source refresh");
                language.recordBodyLanguage(first.id,first.textHash,ContentLanguage.Body.NON_CHINESE);
                HotCandidate updated=new HotCandidate(first.source,first.originalUrl,first.title,"已有新的中文内容摘要","",false,1,null,System.currentTimeMillis());
                next=language.beginRefresh(first.source);language.upsertSnapshot(first.source,next,Collections.singletonList(updated),System.currentTimeMillis());
                check(language.candidates(100,0).get(0).bodyLanguage==ContentLanguage.Body.UNKNOWN&&!language.recordBodyLanguage(first.id,first.textHash,ContentLanguage.Body.NON_CHINESE),"changed content metadata discards old body evidence and rejects late results");
                check(!language.recordBodyLanguage(updated.id,updated.textHash,ContentLanguage.Body.NON_CHINESE,System.currentTimeMillis()-FeedStore.BODY_LANGUAGE_TTL-1000)&&!language.recordBodyLanguage(updated.id,updated.textHash,ContentLanguage.Body.NON_CHINESE,System.currentTimeMillis()+60000),"stale cached bodies and future timestamps cannot renew language evidence");
            }finally{getTargetContext().deleteDatabase(database+"-language");}
            try(FeedStore store=new FeedStore(getTargetContext(),database)){
                HotCandidate other=fixture(Models.Source.WEIBO,2,100);
                long grouped=store.commitRanking(store.version("sources"),store.version("profile"),System.currentTimeMillis(),"cold","all",Arrays.asList(new FeedStore.Row("group-test",first,"匹配标题摘要日期",Arrays.asList(first,other))));
                List<HotCandidate> entries=store.readSnapshot(grouped,10,0).get(0).entries;
                check(entries.size()==2&&entries.get(1).originalUrl.equals(other.originalUrl)&&entries.get(1).source==Models.Source.WEIBO,"frozen grouped snapshot preserves every original source entry");
                long anchored=store.commitRanking(store.version("sources"),store.version("profile"),System.currentTimeMillis(),"cold","all",Arrays.asList(new FeedStore.Row("prefix",other,""),new FeedStore.Row("group-test",first,"",Arrays.asList(first,other))));
                store.anchor(anchored,first.id,-12);
                check(store.snapshotInfo(anchored).getInt("position")==1&&store.snapshotInfo(anchored).getInt("offset")==-12,"old source-content anchor resolves to its grouped snapshot row");
                store.feedback("separate-test","SEPARATE","event","group-test",System.currentTimeMillis(),null,first.title);
                check(store.undo("separate-test",System.currentTimeMillis()),"manual group separation can be undone in the event log");
            }
            new HotSourceCollectorProbe(this,this::check).run();
            SourceExpansionProbe.run(this,this::check);
            ArticleDiskCacheProbe.run(this,this::check);
            ReadInterestProbe.run(getTargetContext(),this::check);
            InterestSetupProbe.run(getTargetContext(),this::check);
            check(sessionBefore.equals(getTargetContext().getSharedPreferences("source-session",0).getAll()),"recommendation operations preserve source session settings");
        }catch(Throwable ex){failures++;report.append("ERROR ").append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append('\n');}
        finally{getTargetContext().deleteDatabase(database);}
        report.append("checks=").append(checks).append(" failures=").append(failures).append('\n');
        try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"discovery-store-probe.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ex){failures++;}
        Bundle result=new Bundle();result.putString("stream",report.toString());result.putInt("checks",checks);result.putInt("failures",failures);finish(failures==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void cacheSpaceProbe(String database,HotCandidate candidate)throws Exception{
        File file=getTargetContext().getDatabasePath(database);
        try{
            try(FeedStore store=new FeedStore(getTargetContext(),database)){
                app.quietreader.discovery.LegacySavedFixture.save(store,candidate.id,candidate,1);store.feedback("preserved-interest","MORE","sample",candidate.id,2,null,candidate.modelText());
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();
                try{
                    // Stress derivative storage only; user-owned tables contain real valid metadata.
                    for(int n=0;n<128;n++)db.execSQL("INSERT INTO embeddings VALUES (?,?,?,?,?,?,?)",new Object[]{"pressure-"+n,"hash","model","tokenizer","normalizer",new byte[65536],n});
                    db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                long occupied=databaseBytes(file);check(occupied>8L*1024*1024,"cache fixture occupies measured database and WAL bytes");
                // SQLite may retain free pages, but repeated replacement should reuse them.
                long initialPages;
                try(android.database.Cursor pages=db.rawQuery("PRAGMA page_count",null)){pages.moveToFirst();initialPages=pages.getLong(0);}
                long largest=occupied;boolean reused=true;
                for(int round=0;round<4;round++){
                    db.beginTransaction();
                    try{
                        db.delete("embeddings",null,null);
                        for(int n=0;n<128;n++)db.execSQL("INSERT INTO embeddings VALUES (?,?,?,?,?,?,?)",new Object[]{"round-"+round+"-"+n,"hash","model","tokenizer","normalizer",new byte[65536],n});
                        db.setTransactionSuccessful();
                    }finally{db.endTransaction();}
                    largest=Math.max(largest,databaseBytes(file));
                    try(android.database.Cursor pages=db.rawQuery("PRAGMA page_count",null)){pages.moveToFirst();reused&=pages.getLong(0)<=initialPages+32;}
                }
                check(reused,"repeated cache replacement reuses SQLite pages without automatic VACUUM");
                check(largest<=occupied*2+1024*1024,"four cache replacement rounds keep database and WAL growth bounded");
                report.append("CACHE_CHURN initialBytes=").append(occupied).append(" peakBytes=").append(largest).append(" initialPages=").append(initialPages).append('\n');
                store.clearDerivedCache();check(store.reclaimCacheSpace(),"explicit cache cleanup checkpoints and compacts outside transaction");
                long released=databaseBytes(file);check(released<occupied/4,"cache cleanup releases physical storage instead of leaving free SQLite pages");
                check(app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.activeFeedback(10).length()==1,"physical compaction retains saved metadata and explicit interests");
                db.beginTransaction();boolean rejected=false;try{store.reclaimCacheSpace();}catch(IllegalStateException expected){rejected=true;}finally{db.endTransaction();}
                check(rejected,"compaction cannot run inside a source or ranking transaction");
                store.putRerank("after-cleanup",0.25f,11);check(Float.valueOf(0.25f).equals(store.rerank("after-cleanup",12)),"cache remains writable after physical compaction");
            }
            try(FeedStore reopened=new FeedStore(getTargetContext(),database)){check(app.quietreader.discovery.LegacySavedFixture.saved(reopened).size()==1&&reopened.activeFeedback(20).length()==1,"saved metadata and preferences survive reopen after compaction");}
        }finally{getTargetContext().deleteDatabase(database);}
    }
    private static long databaseBytes(File file){return file.length()+new File(file.getPath()+"-wal").length()+new File(file.getPath()+"-shm").length();}
    private void cacheUpgradeProbe(String database,HotCandidate candidate)throws Exception{
        try{
            try(FeedStore store=new FeedStore(getTargetContext(),database)){
                app.quietreader.discovery.LegacySavedFixture.save(store,candidate.id,candidate,1);store.feedback("keep","MORE","sample",candidate.id,2,null,candidate.modelText());
                float[] unit=new float[512];unit[0]=1;store.putVector("keep","model","tokenizer",unit,3);
                store.getWritableDatabase().execSQL("DROP TABLE reranks");store.getWritableDatabase().execSQL("ALTER TABLE feedback DROP COLUMN source");store.getWritableDatabase().execSQL("DROP TABLE feed_exposure");store.getWritableDatabase().execSQL("DROP TABLE article_progress");store.getWritableDatabase().setVersion(2);
            }
            try(FeedStore store=new FeedStore(getTargetContext(),database)){
                check(store.getWritableDatabase().getVersion()==9&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.activeFeedback(10).length()==1&&store.vector(FeedStore.vectorKey("keep","model","tokenizer"),10)!=null,"v2 upgrade creates rerank cache/provenance and preserves saved feedback and vectors");
                store.putRerank("valid",-1.25f,10);check(Float.valueOf(-1.25f).equals(store.rerank("valid",20)),"finite negative rerank score round trips");
                boolean rejected=false;try{store.putRerank("nan",Float.NaN,20);}catch(IllegalArgumentException expected){rejected=true;}check(rejected&&store.rerank("nan",21)==null,"nonfinite rerank write is rejected");
                store.getWritableDatabase().execSQL("UPDATE reranks SET logit='broken' WHERE key='valid'");check(store.rerank("valid",22)==null,"corrupt text score is not silently interpreted as zero");
                store.putRerank("overflow",1,23);store.getWritableDatabase().execSQL("UPDATE reranks SET logit=1e300 WHERE key='overflow'");check(store.rerank("overflow",24)==null,"score outside float range is evicted");
                android.database.sqlite.SQLiteDatabase db=store.getWritableDatabase();db.beginTransaction();try{
                    for(int n=0;n<5000;n++)db.execSQL("INSERT INTO reranks VALUES (?,?,?)",new Object[]{"entry-"+n,(float)n,n});db.setTransactionSuccessful();
                }finally{db.endTransaction();}
                store.rerank("entry-0",6000);store.putRerank("new",1,6001);
                try(android.database.Cursor c=db.rawQuery("SELECT COUNT(*) FROM reranks",null)){c.moveToFirst();check(c.getInt(0)==5000&&store.rerank("entry-0",6002)!=null&&store.rerank("entry-1",6002)==null,"rerank cache evicts least recently used and stays within 5000 entries");}
                long sourceVersion=store.version("sources"),profileVersion=store.version("profile");store.clearDerivedCache();
                check(store.rerank("new",6003)==null&&store.vector(FeedStore.vectorKey("keep","model","tokenizer"),6003)==null&&app.quietreader.discovery.LegacySavedFixture.saved(store).size()==1&&store.activeFeedback(6003).length()==1,"clearing derived caches removes scores and vectors but preserves user feedback and saved items");
                check(store.commitRanking(sourceVersion,profileVersion,6003,"personal","all",Collections.emptyList())<0,"cache clear invalidates in-flight ranking publication");
            }
        }finally{getTargetContext().deleteDatabase(database);}
    }
}




