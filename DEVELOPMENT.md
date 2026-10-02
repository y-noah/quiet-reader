# 静读 / Quiet Reader

## 0.3.7 (2026-10-02)

DynamicReader's current-WebView and sampled-location guards now apply known Tieba/Hupu post identities, not just platform domain membership. UrlPolicy shares the supported numeric post/pagination patterns between sameForumPost and sameThread; the latter still excludes an identical URL. Both endpoints must belong to the platform, and Tieba passport hosts cannot supply forum content. Independent `test-identity.ps1` uses real production callbacks with local synthetic same-origin History navigation, including positive same-post and pagination cases; it does not prove HTTP302 handling. The new gate is part of test-local. Evidence: `IDENTITY-QA.md`, `VERIFICATION-0.3.7.md`. The current live-source and latency observations on unchanged 0.3.6 remain separately labelled in `LIVE-QA-0.3.6-remaining.md` and `LATENCY-0.3.6.md`.

## 0.3.6 (2026-10-02)

Live 0.3.5 screenshot review exposed Tieba's hidden nested-reply badges in the own-reader body. A separate fixed-public-page diagnostic observed bounded, redacted DOM structure without exporting cookies/storage or source HTML. Tieba cleanup converts observed direct nested-reply author headers to plain attribution, removes UI-only more-replies controls, and shares cleanup with fallback section identity. Do not filter body words. VideoPolicy excludes nested-reply ownership when classifying a floor; SourceParser separately drops confirmed video nested replies and records exclusions, preserving the parent and other text replies. Independent seven-case red/green tests and exact-release observations belong in `VERIFICATION-0.3.6.md`. `test-release.ps1 -KeepData -Mode sources -Source TIEBA -FixedTieba` requires the observed live topic and exact post identity; a changed/missing live sample is a reported limit, not a replacement fixture.

## 0.3.1 (2026-10-02)

Independent real-pointer journeys reproduced list position and search-query loss after returning from a cached article. Main now records per-source board marks before leaving a board, including query, item URL anchor and offset; post-layout restoration is revision/navigation guarded. Query edits return to the new results' top; refreshes preserve an existing anchor where possible. Activity saved state carries marks through theme/configuration recreation, without adding permanent search-history preferences. `test-experience.ps1 -Mode board-return` owns the new red/green evidence; see `VERIFICATION-0.3.1.md` for actual coverage, not inferred completion.

## 0.3.0 (2026-10-02)

MainActivity uses a compact ranked list, local current-board search and bottom platform navigation. The top overflow owns refresh, source/login, appearance and reader type size; legacy saved preferences are retained but no user-facing favorites entry is restored. Short heat metrics are width-measured before side placement; coupon/merchant details and larger-font metadata wrap below the title. ReaderHtml keeps the document identity in an escaped head meta rather than a permanently visible login action, without changing source allowlists, CSP or continuation behavior. Charcoal/neutral colors also cover image previews and native source chrome. An official Google Material Icons more_horiz path is converted to VectorDrawable with its license bundled. Current UI evidence and intentional deviations from the reference belong in `design-qa.md` and `VERIFICATION-0.3.0.md`; historical counts do not certify this release.

## 0.2.8 (2026-10-02)

SMZDM product hero extraction is separately scoped to the observed main information block and a matching article ID. It requires existing article content, accepts only the official image domain, avoids duplicate image URLs, and populates both document and section blocks so the existing image allowlist/preview path works. Sharing metadata, recommendation rails and login walls cannot supply a substitute article. See `VERIFICATION-0.2.8.md` for red/green parser and exact-release image evidence. The local full regression entrypoint also includes the previously separate SMZDM cache-migration gate.

## 0.2.7 (2026-10-02)

SMZDM now uses the observed official `faxian.smzdm.com/h2s0t0f0c0p1/` three-hour deal feed, not the generic tag collection. Require the three-hour document title and row data-tab, exclude non-ordinary rows, read only main-feed deal headings, and preserve original order and complete offer conditions. No fallback to another period. An endpoint provenance key invalidates only the replaced SMZDM cache; other boards/bookmarks/cookies are untouched. Public web ranking is not certified as identical to the App. Release evidence and remaining limits: `VERIFICATION-0.2.7.md`.

## 0.2 development notes (2026-10-01)

0.2.6 addresses the independently observed blank-image/early-zoom-caption gap. ReaderHtml exposes a real caption link even when the image has not appeared. ImagePreview owns a native loading/error/retry UI and a fresh restricted WebView per attempt; its exact local HTTPS document is intercepted, and only the allowlisted image request uses Main's existing bounded image loader. A fixed read-only inspection requires complete + nonzero natural dimensions before claiming success, and disables scripting on completion/timeout/release. Retry evicts only that image cache entry, not the article; instance/closed/settled guards reject stale finishes. Activity destruction dismisses the preview. API35 all 108/0 and API26 images 36/0 passed; both systems also passed the original real Wallstreet image/preview/return journey using the exact signed release. Version-specific evidence, earlier failures and remaining limits belong in `VERIFICATION-0.2.6.md`.

2026-10-02 harness follow-up: the API35 host emulator repeatedly exited, with a Windows qemu APPCRASH recorded for the first occurrence. `start-emulator.ps1` now uses documented software-renderer/Vulkan-off compatibility options by default and writes a separate launch directory per run; this is an isolation experiment, not proof of crash causality. The exact 0.2.5 app APK is unchanged. Source release checks bind reads to the selected section and use bounded pointer re-location across own-reader revisions; source mode assigns a known run ID before instrumentation, saves incremental evidence before screenshots, and attempts bounded recovery even without a final Output line. See `VERIFICATION-0.2.5.md` and `EXPLORATORY-QA.md` for actual outcomes, including retained failures.

0.2.5: a successful DynamicReader callback can transfer its eligible same-question WebView once to AnswerStream. Parsing termination and WebView ownership are separate; unclaimed/cancelled pages still release, and Main reattaches an accepted source only after replacing the native root. Question identity is checked before extraction, on snapshot location, and again after parsing, not merely by platform domain. The retained source keeps image blocking and is scoped to the current Activity/reading session; do not claim no background source requests while it is retained. Initial navigation posts after ownership initialization and is cancelled by close. `test-session-reuse.ps1` exercises actual Main callbacks with synthetic intercepted pages (8 journeys / 74 checks); release and current evidence are tracked in `VERIFICATION-0.2.5.md`.

0.2.4 changes dynamic stabilization from block counts to a length-prefixed semantic document fingerprint, computed beside background parsing. Zhihu continuation returns even a single new answer once consecutive extracted snapshots agree, instead of holding it until five arrive. The independent `test-dynamic-reading.ps1` runs locally intercepted synthetic sources through actual production parsing/callbacks; old conditions reproduce 6 failures and the correction passes 28 checks. These deterministic source sequences do not certify real-platform completeness or authenticated paging. Current evidence and release status: `VERIFICATION-0.2.4.md`. `test-local.ps1` includes this gate before the broader user journeys.

0.2.3 disables ordinary network-image loading only in the hidden DynamicReader surface. The own reader/zoom and explicit LoginActivity remain unchanged. Fixed-page A-B-A diagnostics compare content fingerprints, not just counts; they show content preservation for the sampled pages, a Tieba improvement signal, but no consistent Zhihu speedup. `test-latency.ps1` records counts/hashes without source HTML or cookies and is not a cold-cache benchmark. See `VERIFICATION-0.2.3.md` for exact release evidence and unresolved speed/authentication limits.

0.2.2 follow-up: text blocks may carry immutable `InlineImage` UTF-16 ranges over accessible placeholders. SourceParser currently recognizes only the observed Tieba emoji CDN filename pattern. ReaderHtml escapes each text segment and image attribute; photos retain separate figures and zoom. MainActivity's image allowlist and Repository's memory accounting include these inline URLs. Do not infer emoji status from small dimensions or arbitrary alt text. Evidence and remaining limits: `VERIFICATION-0.2.2.md`.

0.2.1 follow-up fixes are tracked in `VERIFICATION-0.2.1.md`. Stream append now captures a stable paragraph ID and viewport offset from the escaped own document, then uses an internal fragment/native offset to restore it. A fixed native read-only evaluation temporarily enables scripting under the existing restrictive CSP; it is disabled before reload or on timeout. No page scripts or bridge are introduced. RequestQueue cancels obsolete screen I/O off the main thread. Multi-image single sections also fold, with image counts in the preview.

Current source replaces visible Toutiao with WallstreetCN, adds Hacker News discussion reading and an explicitly non-equivalent SMZDM public product collection. Legacy Toutiao links remain parseable. Theme supports system/light/dark; reader sections fold independently on every source. Zhihu `AnswerStream` retains a source WebView, loads real subsequent content and merges stable answer IDs without removing old answers. Anonymous continuation currently meets a real login wall; authenticated pagination is not certified.

0.2 signed release: 135,135 bytes, SHA-256 `9A1731CF8A2561D466578A7D3B81CB7D4E32B1A952D8DDD679C7721B2938C711`. The same release passed API 35 UI (23), offline/restart (10), real pagination (5), reader recreation (8), and API 26 UI (27) checks. Isolated exploratory debug journeys passed 45 checks, with a later 14-check small-screen rerun including two new status-detail checks. API 26–27 own readers use software layers after a reproducible old-WebView black-tile defect; final screenshots must be inspected, not replaced with green assertion counts.

Reader HTML is served by an exact private HTTPS URL interception, with no-store responses, scripts disabled, escaped content and CSP; never load remote source HTML into it. A previous loadDataWithBaseURL + fragment navigation caused blank reader pages and was replaced. Text zoom keeps reading paragraphs; per-URL bounded memory marks/cache keep expansions and scroll. Native network state checks provide immediate offline status. Permissions now include INTERNET and ACCESS_NETWORK_STATE.

Regression entrypoint: `scripts/test-local.ps1`. Independent synthetic-user journeys: `scripts/test-experience.ps1`; use the exclusive project AVD, never a user phone. It records fresh screenshots and debug APK hash; synthetic checks do not prove authentication or live extraction. Exact release checks remain a separate gate. Latest evidence belongs in `VERIFICATION-0.2.md` and `EXPLORATORY-QA.md`.

Everything below is historical 0.1 evidence and architecture context, not a claim that the new release has passed those old counts.

## Product contract
Personal Android app: five domestic trending sources (Weibo, Zhihu, Toutiao, Tieba, Hupu), topic selection and clean full-content reading. No posting, likes, analytics, cloud accounts, forced app launches or recommendation feed inside reader. Platform login is optional and device-local. Never manufacture content or silently label excerpts as full text. Unavailable sources must show actionable status and retain dated cache.

## Architecture
Java 17 + Android platform UI + jsoup only. No server required; no React/Flutter engine or bundled Chromium. Native network adapters obtain public lists; Android WebView handles normal authorized sessions and dynamic source documents. Remote WebViews never receive a JavaScript-to-native bridge. Extracted documents are rendered in our own native UI, not remote page layouts. Reject non-HTTPS, local-network, non-source and app-scheme navigation. Source adapters and extractors are separately testable.

## Verification gates
- Build installable APK with isolated toolchain.
- Parser / URL policy / reader sanitization and failure-state tests.
- Live five-source tests; distinguish auth-required and unavailable from success.
- Android runtime navigation, rotation/back behavior, cache/offline and font controls.
- Visual QA for long title, multi-paragraph, image-heavy and multi-answer documents.
- Login isolation; no secrets in logs, backups, artifacts or cloud services.
- Deliver APK + source + accurate validation limitations; do not claim all authenticated platforms tested without evidence.

## Current state
Independent Android project builds in project-local `.tools`; existing TopList checkout is untouched. No credentials from conversation are used. Signed personal release is at `artifacts/静读.apk` (126,567 bytes). See `VERIFICATION.md` for the final requirement-by-requirement scope and provider limitations. The previous Debug emulator package/data were removed for release-signature testing; only test-owned emulator data was reset, not host files or physical devices.

Verified 2026-10-01:
- JVM: 14 tests, zero failures (parsers, URL policy, escaped reader, image signatures, recommendation exclusion, video placeholders, modern Tieba markup and same-thread pagination).
- Android: 27 assertions passed, including activity recreation retaining the reading document, script/file-access restrictions, bookmark persistence, no horizontal overflow and all 16 real Hupu images decoding.
- Exact minified release APK: 21 UI assertions passed on both API 26 and 35, including live Hupu board → article, font dialog, favorites, back navigation, source-view toggle and source Activity recreation. Latest tested hash: `31D87A2A365B447EB82B830CB4383A4BD03C860419EEFC5E8938F13EFCEC112E`. Additional release checks: 10 offline/restart assertions (explicitly no active default network), 8 reader recreation/scroll assertions, and 5 real same-thread pagination assertions. No app-private reflection is used by the release runner.
- Real Hupu reader telemetry: viewport and scroll width both 372 CSS px; all 16 extracted images decoded, including the 690-pixel-wide long image. Compact-toolbar screenshot is in `artifacts/hupu-current.png`.
- Lint: zero errors, six warnings (intentional JavaScript in isolated source views, Chinese-only hardcoded strings).
- Reader now validates raster signatures instead of returning `image/*`; no remote source HTML is inserted in the reader.
- Known article pages never fall back to recommendation links as substitute content. Forum pages are explicitly marked as current-page excerpts, not complete threads.

Anonymous Android observations (not authenticated-platform certification):
- Zhihu: latest retest 30 board entries → 51 content blocks on the then-current first question. Stable own-reader screenshot inspected at `artifacts/zhihu-current.png`; long title and text wrap without original source UI.
- Toutiao: 50 board entries → 2 topic links → a video article's textual description. The misleading video-loading placeholder is now stripped; video-only/description content is labelled, not represented as full video playback.
- Tieba: 30 board entries → 10 topic links → 72 content blocks after adapting modern markup and providing the dynamic WebView a real viewport behind the opaque native screen (`SourceSurface`). Previous detached view captured only 4 main-post blocks. Real own-reader screenshot `artifacts/tieba-current.png` confirms text and images without source ad/download UI. Explicit partial-content notice remains: anonymous pages do not contain all replies.
- Hupu: 35 board entries → 139 blocks in the first tested article.
- Weibo: after fixing the observed summary URL and relative link base to `s.weibo.com`, the Android board probe returned 51 rows (50 ranked plus pinned). First topic's anonymous content was unavailable, so the source/login route is still needed; authenticated extraction remains unverified.

Verified since the early scaffold: live same-thread pagination, retained reader scroll after recreation, source-view mode after recreation, offline dated cache, process-restart preferences and synthetic WebView cookie persistence, and minimum API 26 runtime. All five release boards were observed; four anonymous first articles rendered in the own reader, while Weibo's first topic correctly required source assistance. Provider/account restrictions remain explicitly documented, not certified by synthetic cookie tests. For cold Weibo sessions, the source-board screen now offers a return-to-board action and automatically retries using the resulting local session. Same-thread next-page links are supported for Hupu/Tieba and author meta is displayed when present. Bookmarks can be removed by long press, and returning from a saved article returns to saved links.

Dependency review: upgraded to jsoup 1.23.2 and NIO desugaring 2.1.5, per https://jsoup.org/download and https://github.com/google/desugar_jdk_libs/blob/master/VERSION_JDK11_NIO.txt. JVM tests, R8 release build, release lint (zero errors, six known warnings), and API 26/35 runtime tests passed after migration. MIT and GPLv2+Classpath license texts are preserved in the APK.

Release workflow: `scripts/release.ps1` creates a project-local 3072-bit RSA personal signing key, stores its password with Windows DPAPI, builds with R8, verifies the APK v2 signature and copies the installable result. Private signing data stays under ignored `.tools/signing`, never in delivered source. `scripts/test-release.ps1` checks the emulator is virtual, compiles the black-box runner using `-PreleaseUi`, signs the test APK with the same key, resets only emulator packages and tests the exact release APK. It detects assertion failures as script failures, not just adb's exit status. Build scripts now install the command-line tools at the SDK's canonical `cmdline-tools/latest` path; bootstrap preserves an existing JDK instead of comparing an old cached zip against a new release.

Developer test runner: `adb -s emulator-5556 shell am instrument -w app.quietreader.test/app.quietreader.SmokeInstrumentation`. Pass `-e probe true` before the runner to collect anonymous five-source board → topic → article observations. Probe success means observations completed, not that all sources worked. Set `ANDROID_USER_HOME` to the project's `.tools/android-user` before calling adb.
# 0.3.2 媒体语义回归补充

虎扑出现“主帖视频在文本容器旁、第一条回复仅emoji”的真实样本时，必须分别核验主帖媒体提示和回复，不使用非空段落数冒充主帖正文。SourceParser仅检查归属明确的主帖容器，过滤推荐播放器；Document.unsupportedVideo允许呈现明确限制，但不令hasContent伪真。静态、动态、主动来源读取使用canPresent，视频状态纳入DocumentFingerprint。

ReleaseInstrumentation对已展开段落检查Unicode文字/数字，排除emoji-only、keycap emoji与内嵌emoji图片；虎扑只绑定主帖，缺失时单独记录MEDIA_LIMITED/REPLIES_ONLY/身份未确认。主帖含文字但也含未支持视频仍为部分可读，不标整轮通过。Source runner的7项语义自检覆盖同一实际判定；单元合成fixture与实网证据保持分离。

值得买原始截图用于确认三小时入口，不写死商品或名次。公开网页标题、3h字段、有效商品、价格条件和更新行为才是可重复断言；同源性/个性化/缓存未控制时，不由两张不同时间截图推断错榜。

## 0.3.3 视频问题与答案续读

canPresent不等于hasContent。知乎根问题可能先只有视频说明、后出现回答；该情形仍需自有ReaderHtml的显式续读入口，并在现有同题白名单内takeSource移交，不能呈现说明后销毁来源。空sections/blocks不生成虚构section。AnswerStream复制与合并保留unsupportedVideo及视频限制提示。65 JVM测试、独立真实Main受控旅程10/114、准确R8包常规UI36分别通过，证据与不同哈希边界见VERIFICATION-0.3.3.md。短页最大滚动距离不足100dp时通过显式more续读；自动续读测试需先记录几何再以真实展开/滑动满足前置，不能私调loadMore冒充滚动行为。

## 0.3.4 外观与视频过滤

Theme.configureWeb集中设置WebView底色。sourcePage只在全局dark时允许平台算法变暗，自有HTML显式关闭以避免二次反色；LoginActivity使用同一窗口/原生底色。不能用第三方CSS的computed background仍为white断言算法未变暗，必须看实际截图；原站主动禁用算法或老Android不作强行CSS反色。

VideoPolicy仅依据正向媒体证据，元数据不递归下钻问题附件、标题不做关键字屏蔽。Document.filteredVideo表示整个视频主题；filteredVideos/filteredSectionIds表示同页已过滤的独立内容。unsupportedVideo仍只是剩余文字内存在附带媒体的说明，绝不可当作整帖屏蔽依据。mainMedia在复制的主帖DOM里排除嵌套回复/推荐/引用；同一主帖owner的videoSection必须用相同归属规则。

Repository的video-filter偏好与登录Cookie隔离，最多256个主题身份、7天有效；只合并已知同帖分页身份，微博不同q查询和HN不同id必须独立。cacheArticle不保存filteredVideo；Main渲染前拦截、返回榜单/相关列表/读缓存重新过滤。未知类型不预抓全文；首次打开确认后隐藏，不宣称从未曝光任何未知视频标题。AnswerStream的已过滤ID集合跨快照合并，避免先出现文字摘要、后判为视频的回答复活。

正式包normal测试允许至多5条候选，但仅明确FILTERED_VIDEO_TOPIC允许验证隐藏后继续选下一条；登录、超时、正文不可读均不能当作过滤通过。每次normal与sources使用独立runId截图目录，防止旧截图污染新证据。

## 0.3.5 过滤后续读身份与负缓存

续读进展以请求前 answer ID 集合为基准，AnswerStream 完成条件、正文通知和 Main 自动续读暂停状态必须共用 hasNewAnswers，不能用 sections.size 或答案总数差值：迟到的视频分类会删旧回答，新增可能被净数量抵消。相同 ID 的正文增补仍按已有回答处理。

cacheArticle 对明确 filteredVideos/filteredSectionIds 的空结果也覆盖旧缓存，保存排除 ID；单纯网络错误/无正文不作为负缓存成功。整帖 filteredVideo 则移除旧缓存。测试返回重进须正向确认当前自己的文档已提交、来源身份正确，再检查不含旧视频文本，禁止空 DOM 的负断言假通过。

锚点恢复不能直接 scrollBy 负 offset：短文档无法把锚点顶到视口首行，WebView 原生 scrollY 可能变负但 DOM scrollY 已夹至 0。统一 restoreWebPosition 使用 ReadingPosition.clamp，将目标约束于当前 contentHeight × scale 与视口范围；保留 revision/当前 WebView 身份门控。新增短页复测必须连同长文增补保位/字号/主题/缓存返回一起回归，不移除坐标一致性检查来掩盖异常。

## 0.3.8 来源导航恢复

已实证微博 s.weibo.com 搜索入口在手机来源视图跳到普通 HTTP m.weibo.cn/search，HTTPS-only 拒绝后 onPageFinished 覆盖了阻断提示。不得直接允许 HTTP 或任意 intent。UrlPolicy.upgradePlatformNavigation 仅接受无 userInfo、默认 HTTP 端口、升级后仍属于当前平台的地址，并保留 raw path/query/fragment；LoginActivity 与 DynamicReader 明确取消原导航并加载 HTTPS。升级地址重复或超过四次即停止，手动模式重载清空这一链的记录。

LoginActivity 在明确接受新的主导航、安全升级、手动重载/返回时清理 pageIssue；不能在 onPageStarted 清理：已实测它会迟于同页阻断或 HTTP503 回调到达。start/finish 均不得覆盖 HTTP/连接/证书/跳转错误。没有当前 URL 时，模式重载必须用初始合法来源地址，不调用无效果的 reload。读取成功、来源页加载、平台账号登录三者分别验证；匿名访客页面可读取不表示真实账户登录已测试。

精确 R8 正式包的 ReleaseInstrumentation 不能直接引用被混淆的 app 类/枚举或私有字段。来源观察器在 onCreate 返回同 UI turn 代理原 WebViewClient；其导航返回 true 表示 handled，可能是 HTTPS 升级，不一定是阻断。记录只留 scheme/host/path/端口与计数；不输出查询参数或会话数据。

## 0.3.9 移动微博视频归属

已实测未播放的移动微博视频仅有 `.card-video.type-video > .mwb-video` 封面及按钮，没有 `<video>`。视频识别需涵盖这种正向媒体证据；不要模糊匹配所有含 video 的 class 或标题。每个 `.card-wrap` 的媒体和 `.weibo-text` 归为同一帖子。

VideoPolicy.weiboTopic 依据 HTTPS 主机及路径识别桌面搜索 `/weibo` 和移动搜索 `/search`，不允许查询字符串中的相似链接改变身份。此类页面每条卡片独立过滤，不因首卡视频或 og:type 将整页屏蔽。Repository 不写入话题视频记忆，并忽略旧话题视频记忆，不删除原偏好；独立视频帖的持久化屏蔽仍有效。

实网负断言必须同时确认有效阅读页身份和至少一条非视频文字的正向留存，不能靠空阅读页“没有视频配文”算通过。有限的公开文字前缀仅可在诊断内存比较，报告只留计数及布尔值；无混合卡片对照时明确未验证。合成归属/配色检查、实网匿名读取、账号认证分别报告。

## 0.3.10 自动读取与微博原帖

ReadNavigation 对接受的新主导航维护 pending URL 和 revision。HTTP 升级之后，旧文档 onPageFinished 不能触发新页的解析；异步提取回调也必须属于当前 revision。正文解析基址使用实际已验证的来源 URL，再保留请求 URL 作为阅读文档身份，避免移动页面的相对 /status 链接错误地归到桌面域名。

WeiboPost 只识别 m.weibo.cn 的 /status 或 /detail 单段明确 ID；相同 ID 才视为同帖，不猜测数字 ID 与另一种编码的对应。全文入口只从已知话题页的真实“全文”链接取得；忽略引用、推荐和多个不同目标的歧义。Section.continuationUrl 参与文档稳定性指纹。全文操作沿用 MainActivity 的 open/history 流程，只有展开的对应段落才显示入口。

图片提取仅限移动微博同一 .weibo-og 的 .weibo-media-wraps，不把头像、引用或视频封面当正文图；真实图片下载、分辨率和返回位置必须另作交互验收，不能由 URL 个数推断阅读完成。

## 0.3.11 图片身份与按需原图

移动微博文字之外的原帖照片必须在去重之前收集，规范化后的同一组地址同时参与去重键、无来源 ID 时的段落身份和渲染。不能仅用配文判断两条图片帖相同，也不能让两个不同图片区共享折叠 ID。引用、推荐、视频封面继续排除。

实际原站点击证明同 JPEG 的 orj360 缩略图有 mw2000 查看器版本。WeiboImage 只对微博已知 wx1–wx4.sinaimg.cn 的无查询/片段、普通 JPEG 路径应用该规则；其余地址不重写。映射在 Main.zoom 执行，正文仍用缩略图；大图缓存与重试失效使用大图 URL。不要全站替换图片路径或为此携带平台 Cookie。

真实图片验收必须分开记录：初展、图片视口解码、预览解码尺寸与同图身份、关闭后未移动位置。只记录图片地址、只看状态文案或在验证之前主动 scrollIntoView 均不能证明看图体验及保位完整通过。

## 值得买商品图归属补验

匹配 `J_info[articleid]` 与当前商品 ID 只是主图提取的第一道边界。其内部也可能包含推荐/引用节点，必须先以 `VideoPolicy.owned` 排除非本商品区域，再取 `.img-box > img.main-img`；不能把推荐图片区的第一个同类节点当作主图。两项合成测试先在旧实现上失败，修后全量 150 项通过。此安全边界测试并不证明特定线上商品已经提取到主图。

实网验收应区分原站无图、静读没有图片元素、已有图片尚未解码三种情况。`present=false` 不等于图片下载失败；先通过同一商品原页和精确身份确认图片归属，再决定补提取或标记无图样本。不要拿榜单缩略图、分享元数据、推荐商品或另一个样本替代缺失的商品主图。

0.3.12 通过固定真实商品 `/p/183314225/` 验证：同 ID 的上述主图来自 `qny.smzdm.com`，自然尺寸 250×250。允许既有 zdmimg.com 图片及精确 qny.smzdm.com，不猜测其他 CDN 子域和高清路径。假后缀、userinfo 及其他未知子域继续拒绝；图片白名单只在商品身份和自有区域检查之后使用。

