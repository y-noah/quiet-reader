# 统一阅读基础与来源原页检查（2026-10-24）

依据：requirements/unified-reader-v1.md。全来源统一阅读尚未完成。

## 已实现与证据

- ReaderHtml 共用图文/讨论/视频兜底布局；回答、明确回复/评论展示独立讨论区。V2EX 新提取结果区分主帖正文与回复。
- unsupportedVideo 无正文时也进入统一阅读器，避免落入空白状态；保留已有正文。媒体卡片明确“在来源页查看”，无内嵌播放器，不据此认定视频适配完成。
- 单元测试 242 项，失败/错误/跳过均为 0；ReaderHtml 新增跨来源讨论分区、视频兜底保留文字且不嵌入来源脚本/播放器验证。
- `artifacts/local-ai/unified-reader-foundation-api36.txt`：真实 MainActivity.render/ReaderHtml 路径，合成 article/discussion/video 文档，深浅色、320dp 与父容器可用宽度、150% 字号，40/0。检查 WebView 本身没有被父容器裁到屏幕外。测试恢复 appearance.mode；不证明平台提取/鉴权/播放器或完整无障碍通过。
- 最终截图在 `artifacts/local-ai/unified-reader-foundation-final/`；复核讨论浅色与视频深色截图。此前仅修改私有 dark 字段、强设 412dp 的截图/28项结果不足以作为完整主题/屏幕边界证据，已由上述结果取代。

## 原网页辅助入口检查

MobileLayoutInstrumentation 新增 current=true，从实时榜单选择首条，B站/抖音走正常 DynamicBoard；layoutOnly=true 不混入播放测试。错误来源参数明确失败，不允许空跑通过。build-probe 支持 mobile/reader runner。

| 来源 | 本次观察 | 证据范围 |
|---|---|---|
| 百度 | 当前话题适配启用、412px视口、主区域无横向溢出 | 初始3/0只是几何；截图发现缩略图与文字重叠，不当成完整通过 |
| 贴吧 | 当前话题3/0，截图话题说明、相关帖可见 | 非帖子正文/登录/分页验收 |
| B站 | 当前热门视频3/0，标题、画面、简介可见 | layoutOnly，不证明持续播放、评论或登录 |
| 抖音 | 当前热门列表未读到，0项检查/1失败 | 真实失败保留；不能用旧链接/夹具替代当前列表证据 |

原始结果 `mobile-current-{baidu,tieba,bilibili,douyin}-20261024.txt`。

百度新版 `.cos-row > .cos-col` 增加手机单列布局及父行宽度限制。当前页面再次运行4/0，含图文列不相交检查：`mobile-current-baidu-fixed-20261024.txt`；新增双列夹具最初暴露700px宽度未重置（17项/4失败），补齐宽度后 `mobile-baidu-fixture-20261024.txt`17/0，覆盖320dp、事件保留、切换原网页及缩放稳定。来源摘要/署名等其他复杂模板仍可有排版问题，不把此项称为百度所有页面适配完毕。

用户已明确要求所有内容统一阅读，以上 CSS 仅维护辅助原网页体验，不能作为 U01–U05 完成证据。此处原页检查早于下述百度、贴吧迁移；当前仍未迁移默认阅读入口的是 B站、抖音、小红书。统一播放与全来源迁移未完成。

## 百度、贴吧结构化迁移与交接回归

新增 StructuredSources，只提取来源中明确属于当前内容的区域。百度 `/s` 读取话题介绍和搜索摘要、具体链接，不冒充新闻全文；贴吧话题读取介绍、相关帖子入口/摘要，不冒充评论。贴吧帖子读取旧版 d_post_content 与实际观察到的新版 image-text/pb-content-wrap、comment-content/pb-rich-text，保留正文和各回复作者。pb-comment-item 是单条作者归属边界，不能从整个 pb-comment-list 读取首个作者套用于所有回复。表情按可读文字/占位处理，不把每个表情作为大图；稳定 ID 同时考虑作者、楼层和相同内容出现次数。支持现有同帖下一页链接；新版虚拟列表评论续页尚未实现。

两源的发现点击和 App 内阅读链接改走 MainActivity/ReaderHtml，ArticlePreloader 复用主动预取和共享正文缓存。原页与登录仍为显式辅助入口。集合里的具体入口直接可点，摘要直接展示，不增加逐条展开/下一条按钮；话题/正文默认展开。集合元信息明确“话题与来源摘要”，内容范围说明默认可见，避免折叠提示让摘要看似全文。CandidateOrder 在分组前排除明确标记 video 的候选；预读也跳过明确视频条目，未知媒体仍需正文识别。

真实来源会在加载中旋转百度请求/风控/实验 Cookie；全量 Cookie 直接作为缓存身份会导致每次成功提取均被会话检查丢弃。ArticleCookies 对精确的百度搜索及贴吧话题/帖子 URL 提取账号/会话字段作为缓存指纹，其他 URL 保留全量指纹；完整 Cookie 仍照常发送给来源。显式登录返回继续使缓存命名空间失效。BDUSS/BDUSS_BFESS/STOKEN 及通用 session/auth 等变更均有受控测试，**真实登录账号切换和未知账号字段尚未验收**。

DynamicReader 对缓存代次未变而 Cookie 身份变化只允许一次全新采集：先释放旧 WebView/DOM，再新建提取器；不把旧 DOM 改签为新会话。二次变化报错退出；取消、Activity 交接及知乎已加载续读 WebView 委托均有回归。清缓存造成的代次变化不会被自动重试绕过。

验证证据：

- 最新构建 252 项 JVM 测试，失败/错误/跳过均为 0。覆盖摘要与登录页边界、两版贴吧正文、不同作者/重复文本、表情、同帖续页、会话指纹、跨来源明确视频候选过滤及范围提示可见性。
- `structured-reader-flow-api36.txt`：12/0。真实 SourceParser→ArticlePreloader→共享缓存→DiscoveryActivity.open→MainActivity/ReaderHtml；两源各 6 项，确认缓存命中不重开动态来源或 LoginActivity。输入为合成页面，不代替真实站点验收。
- `cookie-rotation-fresh-reader-api36.txt`：13/0。模拟不同 Cookie 身份，真实 DynamicReader/WebView 验证一次新采集、只接受新 DOM、续读转交、二次变化有界失败、关闭取消；没有登录真实账号。
- `dynamic-handoff-unified-reader-api36.txt`：10/0。真实预读 WebView 在原 Activity 销毁后转入阅读器、上下文重绑、生产提取与知乎续读复用同一 WebView、不重新导航、销毁释放；输入为合成页面。
- `structured-baidu-live-ui-20261024.txt`：真实当前话题“数十万辆新能源‘孤儿车’谁负责修”，9 段/11 块，8335ms；动态获取后 cacheHit=true，实际共用 readerUI=true/sourceReload=false。这是摘要集合，不是新闻全文。
- `structured-tieba-live-ui-20261024.txt`：真实当前话题“丢人!LPL四队瑞士轮首日全败”，11 段/18 块，614ms；再跟随真实帖子 `/p/11131778967`，11 段/25 块，17974ms，正文及不同回复作者可见。两级均 cacheHit=true、readerUI=true/sourceReload=false。不是完整评论分页/账号验收。
- `structured-live-reader/` 三张实际 MainActivity 截图已核对：话题摘要不混入讨论，帖子正文作者正确；截图时正文图片尚未显示，独立大图/重试入口存在。图片实际加载成功和原比例展示不能据此认定通过。
- 范围提示修改后重新安装正常 debug：`structured-reader-flow-api36.txt` 再次12/0；`structured-baidu-live-ui-scope-20261024.txt` 真实百度9段11块/8213ms，cacheHit=true、readerUI=true/sourceReload=false。`structured-live-reader-final/BAIDU-1792819500287.png` 已核对，“话题与来源摘要”和展开的“内容范围”直接可见；图片仍为占位，本次截图不证明加载完成。最终 `git diff --check` 无空白错误（有现有 LF/CRLF 提示）。

上述耗时为模拟器采集观察，不是 Find X7 Ultra 首屏性能。证据里的 Cookie 仅记录发生变化的字段名，不输出值。

## 尚需完成

逐源结构化内容适配与真实样本、统一默认入口、评论分页、按需统一播放器/鉴权降级、预读接入、用户兴趣质量与 Find X7 Ultra 整体 RAM/耗电测试。没有发布/推送/品牌变更。

## B站默认入口迁移与共用视频封面（后续轮）

此前状态的“B站默认原网页”由本节取代。BilibiliArticle 从当前 `/video/BV…` 页的 `__INITIAL_STATE__.videoData` 读取已提供的标题、作者、视频简介与封面，按请求 BV 身份校验，拒绝另一个视频/不可用状态/损坏脚本。只解析 JSON，不执行来源脚本；不提取整页、推荐列表、播放器配置、账号状态或媒体流地址。封面限制为 hdslb.com HTTPS 图片。公开可见元数据不代表已经取得视频播放权限，受限播放继续由来源处理。

StructuredSources 支持 B站，发现点击、平台列表及已识别正文视频链接走 MainActivity/ReaderHtml；默认推荐仍在 CandidateOrder 中排除明确视频候选，ArticlePreloader 继续跳过已标记视频，不因为迁移而主动预取视频媒体。动态采集限制停留在同一 BV，增加 m4s 文件拦截。没有统一内嵌播放器，也没有 B站评论采集或分页，视频卡片入口明确“在来源页查看视频”。

Document 新增可选 videoPoster，纳入共用授权图片管线、历史/磁盘缓存序列化、缓存异步快照、复制/续读合并、内容指纹与大小估算；旧缓存无字段仍可读。共用渲染器以固定 16:9 封面区和独立说明/主动来源入口展示，无封面仍保留原兜底。简介保持纯文本并使用共用链接/转义处理。

证据：

- `fixtures/bilibili-video-data-20261024.json` 是当前真实公开视频页元数据的裁剪样本，只留必要视频字段，owner 只留公开名字；不包含账号数据、Cookie、播放器数据。5 项新增 JVM 测试覆盖真实标题/作者、跨视频身份、不可信封面、空简介、不可用/结构变化、转义、旧缓存兼容与封面指纹。最终 257 项 JVM 测试失败/错误/跳过均为 0。
- `bilibili-unified-live-ui-20261024.txt`：原生榜单请求失败，readable=0/1；probeErrors=0 只表示工具没异常，不算来源通过。补齐验证工具以使用既有生产 DynamicBoard 的榜单网页路径，没有删掉这次失败证据。
- `bilibili-unified-browser-live-ui-20261024.txt`：当前真实榜首“折1万次，会坏吗？iPhone Duo深度评测”，原生正文采集2815ms、1段1块视频简介；生产 DiscoveryActivity.open 实际进入共用 MainActivity，原页 Activity 打开数0，cacheHit=true、sourceReload=false。700ms截图的封面仍空白，未当成加载成功。
- `bilibili-unified-cover-live-ui-20261024.txt`：再次当前页采集2920ms，等到 actualCoverLoaded=true 才截图。WebView 真实自然图宽1280、overflow=false、players=0。`bilibili-unified-cover-reader/BILIBILI-1792819977976.png` 已复核：真实封面、作者、视频简介和统一卡片正常。它验证的是公开元数据/图片，不证明账号播放权限、评论或内嵌播放。
- `unified-reader-with-poster-api36.txt`：56/0。生产 MainActivity/ReaderHtml 的图文、讨论、无封面视频、有封面视频，深浅色、320dp/父容器可用宽度、150%字号，不横向溢出或被父容器裁出屏幕。封面使用合成图片注入既有图片内存缓存后经过真实授权图片管线解码；不是远程可用性证据。最终截图目录 `unified-reader-with-poster/unified-1792820010124/`，深色视频封面已复核。
- `dynamic-handoff-video-migration-api36.txt`：10/0，新增导航身份检查后的预读/知乎续读交接回归；其余来源统一缓存链路另记 `structured-reader-flow-video-migration-api36.txt`。最终 diff --check 无空白错误。

以上均在 API36 模拟器，未证明 Find X7 Ultra 1GB 总峰值/能耗。正常 debug 最终回到 store runner；release 仍旧，不提交/推送/发布/改名称图标。目标保持未完成。

## 抖音结构化提取基础与真实来源失败（后续轮）

本轮仍未迁移抖音默认入口。新增 DouyinArticle，由 SourceParser 调用，读取页面已提供的 self/window.__pace_f.push 数据，只解析 JSON，不执行来源脚本或请求播放/账号接口。实际观测到 Flight 同一记录被拆在多个 push 里，解析前合并字符串，再按记录读取；最多3MiB、512帧、深度12和20000节点。不能把每个 script 当独立完整 JSON。当前话题需匹配 owner.hotId，当前视频需匹配 detail.awemeId，有显式 owner.awemeId 时还须与 detail 一致；空记录不会压住后续同身份完整记录。

公开状态未确认、私密、好友可见、删除、审核、受限可见、登录、付费/预览等已知字段均不返回描述/评论正文。评论只处理当前 payload 的已加载顶层记录，按 cid 去重并保留每条作者；同文本不同 cid 不合并，不冒充完整评论。视频封面只接收 HTTPS douyinpic.com 图片，复用共用 videoPoster；没有新播放器、媒体流缓存、评论续页或图文笔记图片适配。标题优先 itemTitle，否则说明首行最多80码点，完整说明保留在正文区。动态页面若缺标题使用请求条目标题，避免共用卡片空标题。

DynamicReader 增加抖音同内容导航边界：同 hot/video 身份可接受；hot 转 video 需要唯一且相同的 sentenceId，不能把另一个视频改签为请求话题。来源会话仍沿用已有完整 Cookie 指纹及一次全新采集规则，没有放宽抖音账号身份校验。

本轮真实外部状态：

- `douyin-unified-board-observation-20261024.txt`：正常 WebView 热榜框架806260字符、0条，ready=0/1。不是解析工具异常，但不能算来源通过。
- 普通 HTTP 热榜页是脚本框架，公开 `/aweme/v1/web/hot/search/list/` 请求返回0字节；本轮没有把第三方数据或旧榜单当当前结果。
- `douyin-unified-topic-observation-20261024.txt`：只对既有历史 `/hot/2689461` 做页面观察，786168字符；从 Flight 中观察到 hotId=2689461、aweme=null、comment=null，没有内容。不是实时热门或成功阅读验收，也不能仅凭此断言已过期或一定登录限制。
- `fixtures/douyin-empty-topic-20261024.json` 仅保留该真实空状态的必要字段。正向测试为显式合成契约；不将其称为真实成功样本。
- 尝试整份导出诊断应用 boards.xml 时自动审批拒绝，理由为整文件可能包含跨平台/用户相关缓存、超出必要范围；没有执行此导出，也没有绕过拒绝。后续使用公开网页观测与合成契约，未使用账号数据。

验证：新增8项 JVM 用例覆盖跨分片、话题/视频身份、权限不泄露描述、真实空状态、空记录后的内容、评论去重/作者、转义、规范跳转和不可信封面。总计265项 JVM 测试失败/错误/跳过均为0。第一次构建因 Android JSONObject 不支持 keySet 失败，改用 keys 迭代后重建成功。

`douyin-reader-contract-api36.txt` 初次9项有1失败：错误地把 favicon 也归为媒体请求，不删除该记录。分开记录媒体扩展/播放接口与其他请求后，`douyin-reader-contract-media-api36.txt`9/0，非媒体附属路径仅 /favicon.ico；真实 DynamicReader/WebView→SourceSnapshot→SourceParser→当前会话 stamp→共享缓存→生产 MainActivity.render 的受控链路通过，共用 HTML 保留视频说明和各评论，无来源 script/video/iframe。测试直接调用共用渲染，不是生产默认入口迁移证明。`dynamic-handoff-douyin-contract-api36.txt`10/0，其他动态预读/知乎续读交接回归。

仍需真实可读取的当前内容样本与登录环境后才切默认路由；完成正向真实样本、图文图片、更多评论/回复、共享默认入口和 U04 播放生命周期后才能关闭抖音迁移。此项未完成不等于整个项目没有可继续工作。没有发布/推送或更换品牌。

最终回填：权限错误且 detail=null 的状态分类与动态空标题回退后，重新构建、安装正常 debug/store runner；`douyin-reader-contract-final-api36.txt`9/0（附属请求仅/favicon.ico）。265 JVM最终结果0失败/错误/跳过，diff --check通过。无活跃构建/测试进程；release未更新。

## 小红书公开样本与统一阅读组件验证（后续轮）

普通桌面 HTTP 本轮取得公开发现页 27 个条目，并顺着来源原始链接取得图文和视频笔记。保留必要公开标题、说明、作者昵称与自有图片字段为 `fixtures/xhs-image-note-data-20261024.json` 和 `fixtures/xhs-note-data-20261024.json`；省略访问签名、账号状态、用户 ID 和媒体流。来源原链接所需查询参数在实际请求中原样保留，不发明签名。真实样本初始评论均为空。

新增 XhsArticle，仅解析有界 3MiB 的 INITIAL_STATE，不执行 JavaScript。当前 URL 的笔记 ID、noteDetailMap 的键、note.noteId 及显式 currentNoteId 需一致；允许同笔记规范路径与查询变化，不接受其他笔记、登录页或发现页。已知受限字段明确提示，缺结构/未知类型不抽整页推荐广告。图文只用自有 xhscdn 图片且保留尺寸、去重，视频以共用封面和说明兜底；不提取视频流、不自动播放。顶层评论按 ID 去重并保留各自作者，真实评论、分页及楼中楼未验收。AdditionalSources 根据来源明确的 video 元数据标记视频，供既有推荐过滤；未改变小红书默认关闭或非热榜标签。

真实模拟器结果保留为失败：

- `xhs-native-reading-before-migration-20261024.txt` 原生列表无条目，readable=0/1。
- `xhs-browser-list-reading-before-migration-20261024.txt` 既有 DynamicBoard 网页列表路径仍无条目，readable=0/1。
- 首次显式链接命令因远程 shell 对 URL 查询符的解析失败，修正参数引号后才执行有效测试；不是来源或测试 runner 成功证据。
- `xhs-public-image-body-live-20261024.txt` 有效显式当前公开图文测试在6791ms后被来源跳转拦住，content=false/cacheHit=false/readable=0/1；仅记录变化 Cookie 的名称，不导出值。未放宽内容身份校验，也未把宿主 HTTP 成功当作 Android 登录/正文成功。

新增8项 JVM 测试覆盖真实图文、视频封面、同笔记身份、权限/空结构、评论作者及 ID 去重、图片所有权、undefined 字面量和转义、来源视频分类；总计273项，失败/错误/跳过均0。

`xhs-reader-contract-api36.txt` 21/0：公开裁剪元数据通过受控 WebView、真实 SourceSnapshot、Android JSONTokener、解析、当前会话 stamp、共享缓存、生产 MainActivity.render；评论和图片字节为合成数据。两类图片解码、无横向溢出，无播放请求/来源脚本/播放器；这不是实时来源、远程图片或生产默认入口验收。首次截图早于可见帧，视频空白，未用于视觉通过；补充等待渲染后再拍 `xhs-reader-contract-painted-api36.txt` 及对应截图。小红书仍保留旧默认入口，等待真实正向访问后才迁移。

补拍最终21/0，图文的等比合成图片、讨论分区和视频封面/说明/来源入口已目视核对。截图暂存本地 `quiet-reader-qa-xhs/discussion-painted.png`、`video-painted.png`；蓝色图片是测试合成位图，不是远程图片获取成功。首次空白截图和报告保留。图文正文默认展开、评论预览独立，视频说明没有被封面兜底隐藏。

整体目标未完成，无提交/推送/发布/改品牌。Find X7 Ultra 整体1GB峰值、能耗、真实个性化质量、完整账号阅读及全部来源迁移仍未验收；ROM持续淘汰而非2GB硬限。

## 共用阅读器交互清理（后续轮）

ReaderHtml 不再仅因 sections.size>1 把短正文/普通短评论折叠。短内容直接显示完整文字和作者，无多余收起/下一段按钮；长评论、多图、回答保留一个折叠入口，下一条跳转只保留后续回答的有效入口。多个独立微博原帖仍各自按需展开，避免破坏摘要后续读取的归属或把不同原帖图片全部加载。默认正文展开和知乎首答逻辑未取消。

新增两项 JVM 交互语义检查覆盖所有来源共用布局、短内容完整显示、长评论展开/收起及后续回答入口；最终275项，失败/错误/跳过均0。首次构建3项失败：新用例错误期待折叠内容也有跳转，另外两项现有微博用例发现独立原帖被全部展开；修正真实微博行为并使新用例在已展开状态检查跳转后通过，未削弱微博断言。

`unified-reader-simple-controls-api36.txt`68/0：图文、讨论、无封面/有封面视频，深浅色、320dp/父容器宽度、150%字号；短正文/评论无重复控件，长评论通过真实MainActivity URL操作展开完整正文并收回，单一按钮，无来源播放器/脚本。截图目录 `browsing-qa/unified-1792822502588`，深浅色讨论截图已目视复核。

`reader-simple-controls-prepare-api36.txt`12/0；首次 `reader-simple-controls-restore-api36.txt`0项后报readerWeb为null。定位为过时测试用 invalidateArticles 模拟重启，而该方法会轮换缓存命名空间并使持久化正文失效。改用 releaseArticleMemory，明确检查命名空间不变、磁盘快照有效、内存正文为空，未放宽生产缓存/登录失效条件。最终 `reader-simple-controls-prepare-final-api36.txt`12/0、`reader-simple-controls-restore-final-api36.txt`14/0：实际不同测试进程恢复全部正文、已展开目标、滚动位置误差<=5px，不重载来源；真实横滑切图、重试、缩放和长图上下滚动通过。保留首次失败报告。

正常debug最终reader runner，release未更新；无发布/推送/提交/改品牌。本轮解决共用交互细节，不等同抖音/小红书默认迁移、真实登录与真机资源等整体验收完成。

## 共用按需播放器与资源生命周期（后续轮）

新增 ReaderMedia：仅从已选正文区的 video/src 或 source/src 读取首个已适配 MP4/WebM 直链，排除推荐、广告、引用区域，不读取播放API、脚本或DRM。媒体需HTTPS、标准端口、无用户名，且属于正文平台对应的明确域名集合；支持的普通图文适配器为财联社、少数派、爱范儿、虎扑、知乎，其他来源继续共用封面/说明与来源入口。原有专用视频主题过滤没有取消。`Document.videoUrl` 接入共用缓存、磁盘/历史序列化、复制/续读合并、指纹及空间估算；反序列化重新验证，旧快照默认空。只有元数据进入缓存，不缓存视频文件。

ReaderHtml仍无script/video/iframe及媒体预取，只有有可接受地址时显示App的播放动作。MainActivity.playVideo才创建VideoPreview，后台域名检查之后才交给原生VideoView/MediaController；仅带来源Referer，不把账户Cookie交给平台解码器。需要鉴权、地址失效、格式不支持时显示来源降级。Modal中有播放/暂停/进度控制、全屏、关闭、重试、来源入口；真实来源播放/权限及全部控制细节仍未证明。

同步dismiss在返回前撤销播放器、计时器和工作线程；frame切换内容及onPause触发关闭。Android33+使用Dialog自己的OnBackInvokedDispatcher，返回先退出全屏，再关闭；注册随关闭释放。全屏改变同一个播放器窗口，不重建阅读WebView。20秒未准备好自动释放，不自动重试；失败收起空视频框并禁用无意义全屏入口。

证据与限制：

- 新增6项 JVM 来源归属、排除区域、播放延迟、快照兼容/重新验证、复制合并/指纹/估算及未知地址降级测试；最终281项，失败/错误/跳过0。
- 公开MDN CC0 `flower.mp4`，1128375字节，仅在androidTest资产，来源/许可见该资产旁README；不是生产包内容，不是某平台真实视频获取证据。
- `video-reader-contract-api36.txt`初次13项4失败：dismiss监听异步导致立即资源断言失败，Android16返回没有经过旧KeyListener导致全屏被直接关闭，后续Back又使阅读页退出；同时发现AlertDialog全屏白色空区。保留原报告/截图，修复同步关闭、预测返回与完整自定义Dialog布局。
- `video-reader-back-lifecycle-api36.txt`13/0：真实Android原生解码、播放位置推进、单一播放器、全屏返回/关闭、原阅读实例与位置、失败重试入口、来源动作、Home生命周期释放。使用本地资产替换测试加载器；生产动作打开/同UI turn关闭也实际执行，但不证明线上地址播放。
- `video-reader-timeout-cancel-api36.txt`17/0：另增实际20秒等待、无自动重试、主动重试后新解码器播放、预检查中取消且迟到加载不生效。
- `video-reader-final-api36.txt`18/0：最终另检查失败时视频框隐藏、全屏禁用。最终截图目录 `video-reader-contract/1792823546131`；此前修正版 `1792823368360` 已核对真实运动画面、全屏黑边保持比例、返回阅读页上方弹窗、完整深色失败界面。失败状态后续收起空框，需以最终截图为准。

本轮不认定 U04 完成：还需真实平台合法直链/权限、嵌入附件与多视频归属、纯视频过滤与显式兜底边界、完整控制/窄屏大字和真机资源耗电验收。B站/抖音/小红书不提取播放流；后两者默认迁移仍未完成。未发布/推送/提交或改品牌，整体目标保持未完成。

最终回归 `unified-reader-after-media-api36.txt`68/0，共用图文/讨论/视频深浅色、窄屏与150%字号及长评论操作通过。最终紧凑深色失败截图已核对，空播放器框已隐藏；本轮本地截图在 `quiet-reader-qa-video/unavailable-final.png`。APK目录核对：flower.mp4不在正常debug App包中，只在instrumentation包中。281 JVM最终0失败/错误/跳过，diff --check通过。最终正常debug reader runner，无活跃构建/测试进程。

## 2026-10-24 / 默认视频过滤与主动阅读兜底

区分预读与主动阅读：SourceParser.article仍过滤纯视频主题；readingArticle保留当前主题已提供的说明、普通回复和来源所属媒体元数据。Repository.articleForReading、DynamicReader显式模式及LoginActivity“读取”快照接入后者；会话变化后的DynamicReader替换保留解析模式。知乎问题的视频回答、微博话题中的视频原帖继续过滤。虎扑主视频可从正文同级且属于主帖的区域取得，排除回复与推荐。

Document.videoTopic跨历史快照、缓存复制、续读合并及指纹保留；视频主题说明不作为中文文章正文已证实的依据。MainActivity不再因已知视频标记立即退回列表，用户主动打开可复用缓存或重新读取，生成统一封面/说明/讨论；预读过滤结果最多转入一次主动获取，无循环。已知视频仍不进入默认候选/来源列表。尚不能声称所有运行时识别的视频都已反馈到发现候选数据库。无媒体文件预取或自动播放。

ReaderMedia封面必须属于适配来源；选中可播放视频时封面只能来自该视频，不继承前一视频的封面。新增6项单元后总计287项，失败/错误/跳过均0。构建成功，当前正常debug为store runner。

API36受控合成链路：explicit-video-reading-final-api36.txt16/0，后补DOM/封面实际解码与绘制检查18/0，再补视图可见尺寸19/0（explicit-video-reading-layout-api36.txt）。前两张截图正文仍空白，不能作为视觉通过证据；DOM与合成器回调并不保证截图已包含新帧。最终等待呈现后截图已核对：统一标题、封面卡、播放/来源动作、主帖说明、讨论卡及内容结束提示完整。最终截图位于工作区外安全目录quiet-reader-qa-video/explicit-fallback-layout.png；封面字节为合成蓝色图块，平台页面为合成HTML，不是线上媒体成功证明。

回归报告：cookie-rotation-explicit-video-api36.txt13/0；dynamic-handoff-explicit-video-api36.txt10/0；structured-reader-explicit-video-api36.txt12/0。分别验证会话变化与有限重试、已有WebView/知乎续读交接、百度/贴吧预读缓存与生产入口。LoginActivity解析调用已修复并构建通过，本轮没有实际账号登录提取验收。未提交/推送/发布/修改品牌。

整体目标仍未完成：抖音/小红书默认入口迁移及真实读取、账号全文与评论续页、更多媒体归属和真实播放权限、推荐质量、FindX7Ultra整App1GB峰值及耗电等仍需继续验证。

## 2026-10-24 / 多视频附件独立身份与紧凑排版

上一轮主动视频主题19项验证与会话/入口回归为实际进展。本轮处理正文只保留首个可播放视频的问题：Document新增不可变VideoAttachment条目的有序列表；ReaderMedia在已确认的正文范围收集独立地址/封面，保留首次出现顺序、去重、排除推荐引用，最多16项。每个封面只属于自己的video；不可播放的条目不能借用下一条地址。旧videoUrl/videoPoster字段保留以兼容旧快照与单附件调用，读取列表时以列表为准。

多附件分别生成索引播放动作，MainActivity检查索引和来源地址后复制当前Document供播放器使用，不修改共享阅读/缓存对象；切换前同步关闭旧播放器。无效索引不会关闭已有播放器。正文HTML仍不包含视频文件URL、来源script/video/iframe，不自动加载媒体。单附件原布局保持；多附件改为小封面紧凑卡片，解释和来源入口合并一次，避免重复大封面推远正文。达到16项时提示其余去来源查看。此项不是所有来源、所有附件位置与评论归属都已解决的声明。

列表接入ArticleDiskCache排队快照、ReadingHistory编码/逐项重新验证/旧快照兼容、AnswerStream复制与旧新格式合并去重、DocumentFingerprint及内存/序列化估算。封面进入Document.containsImage的授权集合。跨来源未知地址恢复后无播放/封面；JSON数组最多恢复16项，原生解码器仍不接收Cookie。

验证证据：
- 新增MultipleReaderMediaTest七项，覆盖独立封面/动作、不支持项、去重/排除区域、快照校验与过量数组、旧新格式复制/合并、后续附件影响指纹与估算、限额来源提示；最终294 JVM，失败/错误/跳过均0。修正旧快照用例同时去掉新videos字段，确保是真正旧格式。
- multiple-video-reading-api36.txt25/0；首版两张大封面使正文位于屏幕之外，截图核对后改紧凑布局；multiple-video-compact-api36.txt25/0。受控HTML经真实DynamicReader/会话/共享缓存/生产入口；实际DOM卡片封面与动作匹配，真实JS点击第二附件、无效索引、切到第一附件；确认旧播放器关闭/解码view移除、正文和缓存身份未被修改。测试未证明来源视频文件可播放，异步预检查可能在关闭前开始。
- article-disk-multiple-video-api36.txt36/0：包含真实文件写入/重开后两附件与授权封面保持、会话指纹隔离、缓存淘汰、损坏恢复、旧会话拒绝及清理等回归。实际队列复制已接入，但本轮新增断言直接覆盖write/read，没有单独证明并发enqueue后列表突变的快照行为。
- unified-reader-multiple-video-api36.txt82/0：图文、讨论、无封面视频、有封面视频及双附件，深浅色、320dp/可用宽度、150%字号；双附件正文初始位置可见、唯一来源入口、无来源播放器/脚本、无横向溢出。最终浅色带合成双封面和深色150%无封面截图已复核，位于quiet-reader-qa-video/multiple-compact.png、multiple-dark-font150.png。
- video-reader-after-multiple-api36.txt18/0：本地CC0真实native解码、单播放器、全屏返回/关闭、保持阅读实例与位置、紧凑失败、异步预检查取消/迟到忽略、实际20秒超时、无自动重试、主动重试、实际Home后台释放。此为本地测试传输，不是平台真实权限/线上播放验收。

最终正常debug reader runner，构建成功；无活跃构建/测试进程。未提交/推送/发布/改名logo，原release未更新。全来源默认迁移、真实登录全文/评论续页、实际来源媒体权限与更多归属、推荐质量及FindX7Ultra整体峰值/耗电等仍待完成；不认定整体目标或U04已完成。

## 2026-10-24 / 正文整篇视频类型反馈到发现列表

上一轮多视频独立身份/紧凑卡片与294 JVM、25+36+82+18 Android检查为实际进展。本轮重新检查小红书真实读取门槛：匿名公开发现请求HTTP200、36292字符、feed.feeds为空；两个既有公开签名笔记重试也HTTP200、36292字符并重定向，均无noteDetailMap，undertakeNote为空。这只能说明本次匿名页面没有提供可用笔记，不能断言必定登录限制、来源永久失效或全部用户均如此。未输出签名地址、未使用账号Cookie。原始公开页面保存于artifacts/local-ai/xhs-public-*-current.html；小红书默认入口迁移仍未完成。

检查发现正文已判定videoTopic/filteredVideo后，Repository视频标记与发现SQLite候选之间没有完整回写链路。新增BodyVideoEvidence，仅接受整篇videoTopic或filteredVideo，拒绝登录/来源不可用状态，拒绝微博话题集合；unsupportedVideo附件、filteredVideos视频回答数量不能把整篇文章判为视频。正文语言和媒体类型证据独立，视频说明不会被用来证明中文文章正文。

HotCandidate新增可选videoEvidenceAt，旧JSON默认0；来源明确video类型与正文推断的时间分开，不改变id/textHash或模型文本。FeedStore.recordVideo仅更新当前active候选且id/textHash匹配、时间非未来且七天内；相同/更旧观察幂等；类型变化提高sources版本使在途排序失效，不更新来源success_at、不写入兴趣/屏蔽反馈。刷新同版本候选继承未过期的推断但不续期；正文元数据变化或刷新时证据超过七天则不继承。未刷新、离线的旧候选仍保留原事实，不虚构新类型。数据库schema保持3，扩展JSON兼容。

DiscoveryController预读完成回调与MainActivity既有ReadingLanguageRecorder都可提交视频证据；检查当前Cookie指纹、正文URL及缓存namespace。预读在合并事件的各来源入口里找精确候选。读取HTML成功但默认过滤的视频结果，Repository在不缓存正文的情况下保留本次已验证的时间/会话/namespace，供类型回写使用；登录变化/缓存epoch原有拒绝条件保留。发现列表仍遵循冻结与待更新快照规则，更新被接受后排除视频；主动打开历史或显式视频入口仍使用统一兜底。

证据与范围：
- 新增3项BodyVideoEvidenceTest，覆盖附件/回答边界、微博集合/权限状态、证据JSON兼容与模型身份不变；另在真实SourceParser图文夹视频用例中断言不是整篇视频。最终297 JVM失败/错误/跳过均0，构建成功。
- video-evidence-store-api36.txt12/0，最终video-evidence-store-final-api36.txt15/0：真实SQLite的版本、时间、幂等、排序排除、刷新继承且不续期、语言更新不丢视频证据、到期/内容变化、来源类型时间分离、无兴趣反馈、另一个数据库连接重开、失榜晚回调，以及生产Repository过滤结果有stamp而无缓存正文。重开是SQLite连接测试，不宣称进程重启/跨APK升级。
- video-evidence-flow-api36.txt7/0，最终video-evidence-flow-final-api36.txt9/0：合成已缓存视频元数据通过真实预读回调→controller→SQLite→冻结/待更新快照，应用后剔除；真实发现卡→MainActivity→类型回写→返回重建；旧namespace与Cookie拒绝。使用受控videoTopic样本，不是线上平台视频判断质量验收，不含真实HTTP提取→该回写链路的完整端到端证明。
- body-language-after-video-evidence-api36.txt7/0；explicit-video-after-type-evidence-api36.txt19/0；feed-store-after-video-evidence-api36.txt147/0。回归语言过滤、主动视频缓存兜底、来源事务/排序/缓存/迁移/偏好，来源会话设置保持。

旧版video-filter偏好里只有URL与时间、没有内容hash的标记没有自动导入新候选证据，避免把旧内容类型套给新标题/摘要；这些旧标记仍可能使对应预读被跳过，此兼容场景尚未解决。当前新获取的视频可通过上述链路写入，未将其称为所有历史标记均已迁移。全来源默认迁移、真实账号全文与评论续页、真实平台媒体权限、推荐质量、FindX7Ultra1GB总体峰值/耗电及升级等仍待完成。

正常debug最终store runner，无活跃构建/测试。未提交/推送/发布或修改名称/logo，原release未更新，整体目标保持未完成。

## 2026-10-24 / 旧网址视频标记的重新核验

上一轮视频类型回写297 JVM与SQLite15/预读阅读9/完整存储147等为实际进展。本轮解决旧video-filter只有URL/时间、没有候选内容hash时会令发现预读一直跳过的问题。ArticlePreloader新增仅发现页启用的revalidateHidden模式，初建与交接后重建均开启；来源榜单预读保留原有过滤。来源明确video以及明确视频URL仍不预读。

发现页遇到旧标记，不自动把标记导入当前候选，也不从旧内存或磁盘正文推断当前类型，直接走既有来源获取路径重新核验。一次一个页面、窗口限制、网络/耗电限制、失败五分钟节流保持。最初实现允许复用已缓存视频元数据，最终收紧为所有带旧标记的候选都重新读取，避免旧正文套用当前元数据。此次核验不预下载视频。

Repository.reconcileVideo只允许当前URL、有效会话及namespace、时间不早于标记且非未来、实际有内容且非登录/不可用/整篇视频的结果撤销旧标记；MainActivity主动读到的新图文也接入。虎扑标记按线程归一，因此还要求可识别的主帖段落，后续分页和只有回复的快照不能改写主帖类型。来源原缓存中曾删除的条目不伪造恢复，后续正常刷新可重新显示。新版SQLite里已有版本的视频证据仍按上一轮七天/元数据变化规则处理，本轮没有额外添加同版本的否定类型证据。

旧隐藏请求进行时不允许reserve交给无法promote它的阅读页；发现页stop按原路径取消请求。暂停后迟到success无效，不写缓存/标记；真正取消可再次offer，失败则继续节流。

证据：LegacyVideoRevalidationProbe使用合成HTML与受控Loader，真实预读器/Repository/会话/缓存；不是线上来源成功验收。初始15/0，补主帖/分页17/0，再补取消19/0；最终legacy-video-revalidation-source-refresh-api36.txt19/0，覆盖来源榜单保留过滤、旧内存正文不被复用、隐藏请求拒绝交接、取消/迟到、再offer、主帖新图文撤销、共享缓存、回复/分页拒绝、旧时间/namespace/Cookie/登录限制、失败保留标记与节流、已知视频不预读。自有临时URL和数据库清理，未清空用户偏好。

video-evidence-after-legacy-revalidation-api36.txt9/0验证普通缓存类型反馈和主动阅读返回；该测试仅清理自己的三个固定合成URL标记/历史，避免前轮测试状态影响。body-language-legacy-revalidation-api36.txt7/0；本轮较早explicit-video-legacy-revalidation-api36.txt19/0；最终feed-store-legacy-source-refresh-api36.txt147/0，dynamic-handoff-legacy-source-refresh-api36.txt见报告。最终JVM297，失败/错误/跳过均0，构建成功，diff --check通过。

这替代上一章节“旧URL标记会永久跳过预读，兼容未解决”的状态；无法取得有效来源正文时仍保留旧标记而不编造类型。通用正文缓存与候选元数据版本的取得时机/归属还需进一步核验，不把此项边界修复当作所有缓存类型判断已完成。小红书/抖音默认迁移、真实账号全文和评论续页、真实媒体权限/归属、推荐质量、FindX7Ultra总体1GB峰值/耗电及跨APK升级等仍未完成。

最终正常debug为store runner；未提交/推送/发布/改品牌，release未更新，整体目标保持未完成。


## 2026-10-24 / 正文取得版本与发现候选隔离

统一阅读仍按图文、图文带讨论、视频兜底三类推进，平台仅负责内容取得与权限。此次补齐共享正文缓存的版本边界，不能因此认定全来源入口迁移完成。

新增ArticleVersion，与HotCandidate共用标题/摘要规范化及SHA-256。Document.metadataHash仅在Repository实际获取、DynamicReader的Item绑定ArticleSession创建时捕获；普通cacheArticle不赋予或改写版本。字段贯穿磁盘排队快照、历史JSON、AnswerStream复制、指纹及资源估算。旧快照无字段或字段无效仍可阅读，但版本未知；合并后续回答保留主文版本。

ReadingLanguageRecorder、DiscoveryController.bodyPrepared及MainActivity弱阅读反馈要求精确URL与取得时的元数据版本一致，原Cookie/namespace校验继续生效。发现预读跳过不匹配的内存/磁盘正文；在线发现阅读同样重新获取，离线可展示旧/未知快照，但不能用于新候选的语言、视频类型或弱兴趣判断。此处离线分支尚无实际离线恢复端到端验收。

reserve/promote按来源、URL和元数据版本匹配；新版本offer替换同URL旧请求，迟到结果不发布。最后一轮新增检查发现失败节流仍只按URL记，导致新版本也等待五分钟（article-version-final-api36.txt，14项中2失败）；已改为来源+URL+元数据hash为attempt key。取消仍可重新发起，同版本失败仍五分钟节流。失败报告保留，未用最终通过覆盖问题记录。

验证：最终JVM301，失败/错误/跳过均0；git diff --check通过。article-version-throttle-api36.txt14/0覆盖真实预读器/会话/Repository/SQLite、旧正文不能判断新候选、缓存跳过、交接拒绝、新版本替换和迟到回调；legacy-video-version-throttle-api36.txt19/0确认旧标记核验与同版本五分钟节流保持。body-language-version-api36.txt7/0；video-evidence-version-api36.txt9/0；structured-reader-version-api36.txt12/0；dynamic-handoff-version-final-api36.txt10/0；explicit-video-version-api36.txt19/0；feed-store-version-api36.txt148/0（含实际磁盘写入重开后版本保持）。上述回归在最后attempt key调整前运行，最后调整由14+19项针对性检查覆盖；没有将全部报告声称为最终代码全量重跑。

实际MainActivity阅读生命周期、滚动与20.1秒阅读：read-interest-version-api36.txt5/0允许匹配版本反馈；read-interest-stale-version-api36.txt4/0拒绝不匹配版本。负例在初始呈现后受控修改Document版本模拟不匹配，并非实际离线恢复。所有上述合成内容/受控获取不能替代真实平台内容质量、账号全文或权限验收。

LoginActivity“读取当前页”快照尚无Item取得版本；可以显示，但不能作为匹配发现候选的证据或弱兴趣样本，后续需要绑定原始请求身份并验证实际提取与返回时会话变更。不能在普通缓存写入时补盖当前候选版本。同版本标题/摘要不变而正文由视频改成图文的否定类型纠正仍未实现。

抖音/小红书默认入口迁移、真实账号全文与评论续页、真实媒体权限/归属、推荐质量、FindX7Ultra整体1GB峰值及耗电、跨APK保数据升级仍待完成。正常debug为store runner，无活跃构建/测试；未提交、推送、发布或修改名称/logo，release未更新，整体目标保持未完成。


## 2026-10-24 / 来源登录页读取回到统一阅读器

接续上一轮正文取得版本工作，补齐LoginActivity的生产提取路径。MainActivity在打开来源登录页前传入原始Item；LoginActivity仅在实际读取URL与原请求完全一致时绑定该Item的标题/摘要版本，跳到其他文章的正文保持未知版本。每次JS提取前捕获ArticleSession与页面navigation版本；导航、Cookie或缓存namespace变化时拒绝迟到快照，提示重新读取，不把旧DOM盖成新会话内容。知乎自动展开的重试每次重新取得会话。

新增Repository.importSourceRead：先在锁内验证刚取得的正文时间、Cookie及旧namespace，再失效全部旧正文和在途取得；仅该已验证快照移入新namespace。保留取得时刻、会话和metadataHash，不通过普通cacheArticle给旧正文补盖版本。未知身份、未来、过期、Cookie已变或namespace已失效均拒绝导入；失败/取消仍清理旧缓存并沿原路径重新获取。MainActivity一次消费pendingDocument，匹配原Item时保留原摘要，换页时使用新内容入口；新正文的阅读计时重置。

登录入口改按unifiedReading而非旧webReading分流。已结构化的百度/贴吧/B站也能使用双向滑窗的“读取当前页”；抖音/小红书尚未完成默认迁移的纯网页路径仍保持原状。本次实际完整读取验证覆盖知乎与贴吧，不把同一条件分支当作百度/B站完整来源登录验收。

新增LoginReadFlowProbe，由真实MainActivity发起登录Activity、受控HTTPS来源WebView载入HTML、实际按钮触发SourceSnapshot提取、ActivityResult返回、共用渲染/缓存及SQLite证据写回。覆盖原页、跨页、提取期间Cookie变化后拒绝/再读、贴吧滑窗导入。初版login-read-flow-api36.txt23项后1超时，diagnostic版34项后1超时；原等待仅依靠onPageFinished，取消的初始载入也可能发出该回调。测试改为确认真实合成DOM与readyState，Cookie场景确认before生效并用400ms受控renderer任务固定callback时序；不是生产中增加等待。原两次超时的全部根因未独立证明，不删失败报告。随后dom-ready版52/0、final版59/0。

最终login-read-flow-learning-api36.txt61/0：包含实际磁盘写入后释放内存/restoreArticle恢复，保留hash和原取得时间；旧请求stamp失败、其他旧正文不能重新发布；五类非法快照拒绝。还以真实前台焦点、1.2秒恢复等待、实际scrollTo和20.1秒阅读，再经真实onPause及有界writer，确认导入本身不产生反馈，阅读后恰好产生一条READ。没有伪造系统时钟或直接写入反馈。页面、正文、讨论及Cookie均为合成数据；不能替代真实知乎登录全文、跨真实账号切换或线上贴吧结构成功验收。

最终生产代码回归：cookie-rotation-login-import-api36.txt13/0；dynamic-handoff-login-import-api36.txt10/0；structured-reader-flow-login-import-api36.txt12/0；article-version-flow-login-import-api36.txt14/0；feed-store-login-import-api36.txt148/0。前三轮构建及最终测试包构建成功；最终JVM301，失败/错误/跳过均0，diff --check通过。最后一次构建仅增加61项中的真实阅读反馈测试，生产代码未再变化。无活跃构建/测试。未提交、推送、发布、改名或改logo。

本节替代上一节“LoginActivity主动提取尚无取得身份”的待办状态；仅版本及返回缓存链路已补齐。全来源默认迁移、真实账号全文/讨论分页、更多媒体归属与权限、实际离线恢复负例、同元数据正文类型否定纠正、推荐效果、FindX7Ultra1GB整体峰值/耗电及跨APK升级仍待完成，整体目标保持未完成。


## 2026-10-24 / 贴吧与V2EX讨论续页及快速追加位置

新增DiscussionPages负责已知论坛分页和纯内容合并，DiscussionStream负责一次一个按需请求、Cookie/namespace/缓存epoch校验及关闭；MainActivity和ReaderHtml复用统一讨论卡片与继续加载动作。此次仅支持贴吧/p/数字和V2EX/t/数字的来源明确分页，不构造来源未提供的虚拟列表接口。评论不参加主动预取；获取仅由阅读操作触发。默认保留原生读取及动态页回退，WebView使用本机来源会话。

分页只接受同帖、页码向前的p/pn链接，重复/非法页码拒绝，多个页码链接选择最近后续页。修复UrlPolicy.normalize对?p=2查询相对地址丢失帖子路径的问题；新增论坛原生请求逐次重定向的同帖/同页校验，避免用原网址给另一篇正文盖身份。重定向语义有单元测试及代码检查，本轮没有受控HTTP重定向服务器的实际传输测试，不能写成该网络分支已端到端验收。

V2EX优先使用真实r_回复ID，分别保留主帖作者、回复作者；同文不同作者不再因纯文本相同而折叠。主帖补充使用独立身份。没有来源ID/楼层时保留页内位置以免丢弃不同回复，不保证此不确定结构的跨页重复识别。贴吧沿用post_content/data-field身份，缺少楼层的后备键加入当前页。合并只追加新回复，重复主帖和已存在回复不再添加；保持原URL、正文取得hash、会话和取得时间。没有新回复/错帖/倒退页报错并保留游标，不默默跳过。来源未给下一页只说明当前未提供后续页，不称为全部历史回复。

Document.discussionPage保存已加载到的来源页，贯穿JSON、磁盘队列快照、复制、指纹及序列化预算；旧快照根据原URL推断起始页，非法新字段忽略。累计600条讨论或正文缓存条目2MiB边界时停止整页追加，保留已读内容和来源入口，不部分丢弃正文/回复。继续加载只有一个入口；加载中不可重复触发，失败可重试、需要权限和达到本次阅读预算分别说明。切后台关闭当前请求，返回时保留正文与重试游标，迟到回调无效。

多轮验证纠正了两种错误判断：早期脚本点击和旧DOM就绪判断引起重试超时，最终改成当前文档就绪后真实触摸；原先按scrollY恒定断言位置也不准确，已改为同一段落在屏幕中的CSS top。失败报告保留：discussion-flow-api36.txt7项后1失败；ready版21项2失败；diagnostic版7项后1失败；touch、scroll-diagnostic及anchor版2项后1超时。不能把这些超时都宣称已独立证明同一根因。

visible-anchor版32项2失败则提供了实际可见偏移证据：V2EX段落CSS top约-19.92变0.27，贴吧约-20.18变-0.11。加载过程原来先重载一个spinner文档，快速响应又追加新文档，两个异步位置捕获/恢复可能使原锚点失效。已改为仅在当前动作原地更新加载状态，结果到达后一次追加；受控DOM脚本按WebView计数协调启用/关闭，仍只执行App自产、CSP保护页面中的固定逻辑，没有来源JS桥。片段滚动在页尾可能被截断，因此恢复时读取实际锚点位置再补偿，不能假设片段已贴到顶部。

最终证据：
- 新增DiscussionPagesTest十项，覆盖来源身份/作者、同文回复、主帖补充、最近前向游标及查询链接、错页/无新增、只追加讨论、登录限制、600条预算、JSON/旧快照/指纹以及单一按钮状态。最终311 JVM，失败/错误/跳过均0；构建和diff --check通过。
- discussion-flow-inline-loading-api36.txt32/0；补知乎快速追加后discussion-flow-discussion-final-api36.txt34/0；最终仅补V2EX主帖作者后discussion-flow-discussion-source-final-api36.txt34/0。真实触摸、实际MainActivity/WebView DOM、重复请求保护、失败与迟到回调、SettingsActivity前后台、恢复重试、真实磁盘释放/恢复均覆盖。V2EX同段CSS top -19.9226→-19.9226；贴吧-20.1845→-20.3036；不是以scrollY不变冒充可见位置稳定。默认生产DynamicReader还实际读取受控来源页并合并；原生HTTP获取未在该合成链路中证明。
- 知乎快速追加用实际MainActivity共享showContinuationLoading/appendAtReadingPosition及WebView，确认段落位置与原正文身份保持、不为此创建额外来源浏览器；追加回答是合成结构，不是完整登录知乎HTTP/API续读验收。
- login-read-flow-discussion-source-final-api36.txt61/0（含真实20.1秒阅读学习）；feed-store-discussion-final-api36.txt148/0。
- dynamic-handoff-discussion-final-api36.txt10/0、structured-reader-flow-discussion-final-api36.txt12/0、article-version-flow-discussion-final-api36.txt14/0、cookie-rotation-discussion-final-api36.txt13/0。在最后补主帖作者前运行，之后未修改这些会话/共享恢复代码，不宣称全部回归在最后补丁后重跑。
- reader-prepare-discussion-final-api36.txt12/0、reader-restore-discussion-final-api36.txt14/0、reader-unified-discussion-final-api36.txt82/0：折叠/下一回答、历史位置/图片交互、统一图文讨论与视频兜底、深浅色/320dp/150%字号。论坛新续页按钮的真实触摸是默认宽度/主题，不能将通用82项扩大成论坛续页全部主题字号已测试。

真实来源范围：本机CLI匿名公开V2EX hot API和一个当前公开主题读取成功，原始HTML见v2ex-discussion-page1-current.html（topic1251402、接口replies60、118830字节），确认真实r_回复容器和主帖header作者结构，本页无分页控件。Android最终live-v2ex-discussion-final-api36.txt榜单返回访问限制，readable0/1；probeErrors0不是来源通过。未取得真实多页正例，不以离线样本/受控WebView代替手机VPN、实际来源权限或登录读取成功。

正常debug最终reader runner，无活跃构建/测试；未提交、推送、发布或改品牌。U03仍未完成：贴吧新版虚拟列表/楼中楼及其他平台评论分页、真实账号全文/切换、更多媒体归属/权限、全来源默认迁移、推荐质量、实际离线恢复负例、同元数据类型否定纠正、FindX7Ultra整体1GB峰值/耗电及跨APK升级等仍有验收缺口，整体目标保持未完成。
