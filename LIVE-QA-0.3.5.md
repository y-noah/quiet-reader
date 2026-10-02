# 0.3.5 独立实网逐源检查（2026-10-02）

对象：现有正式 APK，SHA256 `17BED42ECFFCD85F14ECBA9E2F7201697F546B05E757CA17E6808A741E9C3D81`。仅项目 AVD `quiet-reader-test` / `emulator-5556`，已核验 boot_completed=1；KeepData，不清数据、不输入账号、不使用真机。AGENTS 文件搜索无结果。

按 SMZDM → ZHIHU → TIEBA → HUPU → WEIBO → WALLSTREET → HACKERNEWS 顺序运行单源正式包测试；每源终态后先审其全部新截图。脚本观测 PASS 不等于匿名正文可读，视频过滤与登录受限分别记录。截图沿用现有大阅读字号，不冒充默认字号；计时包含本轮实际缓存/网络条件，不等于冷启动基准。使用 audit 技能的实际截图优先流程。

## 1. 什么值得买：本样本可读

- session 90854，exit 0。证据：`artifacts/release-emulator-5556-20261002-113954-816/sources-1790912394802`。
- 公开三小时榜 11 项，榜单 1.874 秒；首篇 `https://www.smzdm.com/p/183298019/`，充电器 87 元需用券，正文 2.698 秒。两段实际说明可读，未仅凭自有阅读页外壳判通过。
- 五张截图逐张核对：`source-6-board`、`expanded`、`first-image`、`image-preview`、`image-preview-returned`。榜单长标题与优惠条件换行清楚；榜单、正文和预览保持深色。
- 展开即时图未解码，但 609ms 观测点出现真实充电器图，250×250；实际点击“查看大图”打开，关闭后原文章/图片身份保持。放大图明显低清，属于所取源图分辨率限制，不能称高清。
- 本轮不证明 App 榜序逐项一致，不证明全部商品正文/图片或正文返回榜单保位。

## 2. 知乎：实际第二回答可读，加载仍约十秒

- session 55570，exit 0。证据：`artifacts/release-emulator-5556-20261002-114211-841/sources-1790912531833`。
- 榜单 1.740 秒；问题 `2088971415525356116`，首篇及首卡展开 10.104 秒。本次首卡只是问题补充，因此继续真实触摸第二条实际回答“咖啡不加盐”，确认 8 段非 preview 正文。
- 三图 `source-1-board`、`expanded`、`second-answer` 均已逐张审阅：深色一致、折叠摘要三完整行、第二回答与收起入口清晰，未见横向裁切。大阅读字号使首屏内容较少，是保留的设置。
- 初始两条实际回答不代表全部回答；未证明第三条、登录后分页或第一回答提示的 12 张图均能解码。问题补充本身以省略号结束，不能据此声称补充全文完整。

## 3. 贴吧：首楼文字可读，但发现楼层摘要混入站点徽章

- session 28228，exit 0。证据：`artifacts/release-emulator-5556-20261002-114351-892/sources-1790912631884`。
- 榜单 1.670 秒；`https://tieba.baidu.com/p/11061609054` 首楼展开 16.239 秒，明显偏慢。3 个已提取楼层，不是完整帖子认证。
- 两张新图 `source-2-board.png`、`source-2-expanded.png` 逐张审阅。首楼两段真实文字清楚、表情已经可见；普通照片在展开图尚未显示，DOM 即时 naturalWidth=0/complete=false，属于未验证解码，不直接归因加载失败。
- **确定的阅读瑕疵：**第二楼折叠摘要出现“贴吧成长等级 本吧头衔 零至无限 … 反抗之子 …”，站点徽章混入内容。截图即用户可见证据，不把脚本 READABLE 泛化为清洁正文。建议限定楼层内容结构并排除元信息节点，保留实际原文。
- 已将缺陷与资源交给根代理，暂停剩余来源，等待修复/继续采证安排。当前无活动 Gradle/ADB session。

## 后续来源

待依序实测，不沿用旧截图作为本轮通过证据。

## 贴吧污染定向诊断与独立红测

- 新增测试专用 `TiebaDomProbeInstrumentation`，仅在 `-PtiebaDomProbeUi` 下选择；真实启动生产 LoginActivity，固定 `https://tieba.baidu.com/p/11061609054`，未输入账号、未读 Cookie/storage、未导出整页 HTML。构建 session54743 exit0；诊断 session44928 exit0。
- 证据 `artifacts/tieba-dom-probe-20261002-114917/run-1790912958648`。8.985 秒快照 exactUrl=true、ready=interactive，取得目标结构。每个匹配仅父链 tag/class；最多2个父容器、各12k字符结构，所有非目标正文仅留下长度占位。
- 实际污染节点 `.tooltip__popper → .tooltip → .name-info → .name-info-link → .head-info → .head-line.user-info`。外层 `.comment-content` 包含正文 `.pb-rich-text`、图片 `.image-card-wrapper`、操作 `.pc-pb-comments-desc` 和 `.lzl-wrapper`；楼中楼 `.pb-lzl-item` 又含作者头部及内层 `.comment-content`。不能直接删楼中楼容器。
- `01-public-source.png` 已独立看图：仍是贴吧 Logo/加载进度状态，**不是来源正文视觉通过**。DOM结构证据与视觉完成度分开陈述，不为此额外重跑来源页。
- 新 `TiebaMetadataTest` 五项完全合成的最小结构测试：徽章/头像不入正文；普通正文讨论同名词保持；楼中楼作者、文字和两张普通图保持；展开更多控件不入正文；仅徽章变化不改变 section ID。初轮3项1失败，最终 session10802 exit1，5项3失败，失败项分别为徽章污染、结构控件污染、ID随徽章变化。原文/作者/图片保留两项通过。
- 红测 XML 独立保存至 `artifacts/tieba-dom-probe-20261002-114917/red-jvm-3` 和 `red-jvm-5`，避免后续绿测覆盖证据。生产未修改，测试文件稳定，Gradle/ADB已交根代理修复。
- 后续获授权补两项独立合成归属测试。session23742 exit1，最终7项4失败：在原3个失败之外，父楼纯文字+楼中楼视频+另一文字回复被错误清空（hasContent=false）；确认主楼视频仍整主题过滤这一对照通过。XML另存 `red-jvm-7`。这是合成生产parser调用证据，不是真实帖恰有楼中楼视频的认证。
- 已离线增加 ReleaseInstrumentation 参数 `fixedTieba=1`：仅从当前榜单选择原题，强验URL11061609054；绑定第2卡实际触摸展开、正文“杰克第18话”身份和非preview段落、图片保留，再于complete的同源自有阅读DOM检查徽章文本消失。旧普通来源模式不变，不用JS点击/私有render替代。当前测试修改尚未编译/运行，待根修复后统一验证。
