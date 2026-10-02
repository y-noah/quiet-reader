# 微博真实来源恢复审查（0.3.7）

2026-10-02，专用 `quiet-reader-test` / `emulator-5556`，保留数据；没有输入账号、导出 Cookie/storage/HTML/账号文本或 URL query。精确正式 APK SHA256：`7A614FBC7C00B20B2D867DA658F6A9BD8541DDDCB366FA12F67B796274DF8CC1`。

审查采用 Product Design audit 的截图先行、流程分步和证据边界规则；对象是已有 Android App，通过原生 instrumentation 获取真实屏幕，不用网页替身。技能预检因本机无 python3 未运行，手动确认无 saved context。测试仅修改 `ReleaseInstrumentation.java` 与 `scripts/test-release.ps1`，增加仅 WEIBO sources 可用的 `-WeiboProbe`；所有原正文门槛保留。观察器委托原 WebViewClient，未修改导航或登录政策。

## 结论

**确认一个 App 恢复路径缺陷，而非已证明的“必须登录”或正文 selector 失配。** 来源尝试跳到 `http://m.weibo.cn/search`，被现有 HTTPS-only 规则拒绝，随后 `onPageFinished` 把明确的 blocked 状态覆盖成登录后读取提示。实际没有已提交 URL/正文，切换手机版/桌面版调用 reload 仍为空白。HTTPS-only 规则本身不应放宽为允许 HTTP。

第三轮精确事件（没有 query）：

```text
847ms navigation scheme=http address=m.weibo.cn/search port=-1
userInfoPresent=false javaUriValid=true mainFrame=true blocked=true
863ms finish m.weibo.cn/search
nativeBeforeFinish=已阻止外部跳转（m.weibo.cn）；请使用平台账号直接登录
nativeAfterFinish=来源页 · m.weibo.cn｜完成登录或展开后点「读取到静读」
```

来源确为 `app.quietreader.LoginActivity`，布局仅一只 WebView，attached/shown/parent=true，visibility=0。两种模式分别观察 20 秒，均 `getUrl=null`、`getOriginalUrl=null`、progress=100、DOM complete、bodyChars=0、正文 selector=0、密码输入框=0；没有看到登录表单或验证码。因此不能将“selector=0”单独解释为解析器缺陷，也不能把空白归为真实平台登录墙。

## 三步真实用户旅程

1. **微博热榜 → 首项：榜单正常，正文失败。** 首项“总书记谈家国同心”，第一轮榜单 7.239 秒、正文 22.427 秒后出现泛化协助页。用户知道下一步入口，但“请登录后重试”没有真实登录墙证据。
2. **真实点“登录 / 加载后读取”：恢复阻断。** 来源完全空白，无加载进度或可用表单；顶部却要求完成登录或展开。错误信息已被 finish 覆盖，用户不知道发生了什么。
3. **真实点读取、切桌面版、再读取：未恢复。** 提示变成“仅允许读取当前平台内容”，但页面无地址、无正文；切换模式没有发出有效重新导航。按钮可点、深色一致，不能抵消功能死路。

以下最终轮三张代表图均已实际打开。空白截图是失败状态证据，不被接受为来源正文加载成功：

![步骤1 正文受阻](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-124120-975/sources-1790916080966/source-0-blocked.png)

![步骤2 来源空白但提示已可读取](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-124120-975/sources-1790916080966/source-0-recovery-mobile.png)

![步骤3 桌面版读取仍无恢复](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-124120-975/sources-1790916080966/source-0-after-read-desktop.png)

## 运行证据与限制

三次命令均为 `scripts/test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboProbe`，按终态顺序执行，各 exit 1 是保留真实正文失败，不是 runner 编译崩溃。每轮六图，共 18 张均逐张检查。

| 轮次 | session | 目录 | 差别 |
| --- | --- | --- | --- |
| 1 | 29212 | `artifacts/release-emulator-5556-20261002-123513-276/sources-1790915713267` | 先证实空白；挂载较晚，初始事件缺失 |
| 2 | 35501 | `artifacts/release-emulator-5556-20261002-123845-068/sources-1790915925058` | onCreate 同 UI turn 观察，证实 blocked 被 finish 覆盖 |
| 3 | 28840 | `artifacts/release-emulator-5556-20261002-124120-975/sources-1790916080966` | 增加安全导航属性，明确同源 HTTP 降级 |

未观察到 SSL/HTTP 错误不等于不存在所有网络问题。初始 IO 可先于 observer 注册，但本次关键导航/finish 回调都已记录。未认证、未证明登录后可读取、未验证全部微博热搜，也未据此宣称 DynamicReader 的全部 22 秒均由相同原因构成。

建议：在白名单和无 userInfo/非默认端口约束下，将普通同平台 HTTP 跳转升级 HTTPS，而不是放行 HTTP；设置升级次数/重复地址上限；blocked/error 必须跨 finish 保留，直到新的明确加载开始；无已提交 URL 时模式切换应重新打开受校验初始地址。下一步用受控页面分别验证安全升级、重复降级循环停止、错误提示不被 finish 擦除，再复验真实来源。

资源已在第三轮终态后交还根代理；设备仍留 0.3.7 正式包，无活动 session。

## 0.3.8 精确正式包实网复验

正式 APK SHA256：`57AC5F2280C7C643148412BEA8398A2426204A6EA53665042707A14AB42309CE`。以下均保留数据/匿名，不输入账号，不清会话。

| 轮次 | session / 终态 | 证据目录 | 结论 |
| --- | --- | --- | --- |
| 首次修后 | 73049 / exit 1 | `artifacts/release-emulator-5556-20261002-124811-617/sources-1790916491610` | 自动正文仍失败；来源从 HTTP 跳转升级到 HTTPS，经过访客页后移动搜索页在 8.807 秒观察点已有 2355 字符、12 个正文选择器；真实点读取返回折叠卡 |
| 扩展验证的测试错误 | 40414 / exit 1 | `artifacts/release-emulator-5556-20261002-125130-066/sources-1790916690058` | 已 pointer 展开，但测试错误引用了 R8 后不存在的 Models.Source；是 runner 错误，不是产品崩溃。旧证据保留 |
| 修正测试后 | 32770 / exit 1 | `artifacts/release-emulator-5556-20261002-125539-057/sources-1790916939049` | 自动正文等待 21.358 秒仍受阻；实际来源在 4.799 秒观察点 2356 字符/12 选择器；读取后真实 pointer 展开 part-0，1 段非 preview 有意义文本，源身份 m.weibo.cn/search |

三轮分别 4、4、5 图，共 13 图均独立逐张查看。第二轮错误发生在输出对象前，没有写入新 recovery JSON；修后日志仅记录 section/数量/安全 host/path，不输出查询串。旧 `blocked=true` 名称是原 WebViewClient 返回 handled=true，升级同样为 true；新版改称 handled，不再仅凭此字段判被阻断。

当前用户路径：

1. 热榜打开该话题仍出现“需要来源页面协助”，并泛化建议登录，不能算自动读取通过。
2. 实际点击协助后，来源页面不再空白，确实显示移动微博。没有做账号认证。
3. 点“读取到静读”后，真实摘要可在深色自有 reader 中展开/看见收起入口。但段尾为“新 …全文”，证明仍是来源截断摘要，不能称微博完整正文。

![修后真实来源页面](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-125539-057/sources-1790916939049/source-0-recovery-mobile.png)

![真实展开但仍是源端截断摘要](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-125539-057/sources-1790916939049/source-0-recovered-expanded.png)

额外可见缺口：来源首卡有明确播放三角与 1:01 时长，同首卡配文仍进入阅读器，说明移动微博视频卡过滤尚未达到用户要求；不是根据“视频”关键词推断。该样本正文未提取图片，不能称图片/全文完整。访客会话建立后第二、三次自动路径仍失败，排除“只要首访建立会话就全部恢复”的简单结论，但尚未单独证实 UA 因果。

## 独立受控导航红测

真实 production LoginActivity/DynamicReader 与 WebView 导航，包装客户端只提供本地明确合成 HTML 并转发事件；不直接调用 shouldOverride/onPageStarted/onPageFinished，不替换生产解析或成功回调。Login 初始合法 GET 可能先于测试 wrapper，受控起点明确为 stopLoading + 安装拦截 + 加载不同的合成路径。无账户操作、无 Cookie/HTML 导出。

- 首轮 session 75588：`artifacts/navigation-recovery-emulator-5556-20261002-125747/run-1790917068669`，8 旅程 / 56 检查 / 18 失败。8 项是测试将同源 favicon 子资源错误记为非法目的导航；HTTP204 并未造成 nullURL，相关两项是 fixture 前置失败。原证据完整保留，不称 18 项生产缺陷。
- 诊断复跑 session 32388：`artifacts/navigation-recovery-emulator-5556-20261002-130129/run-1790917290113`，8 / 56 / 8 失败。测试明确按主框架判断非法目的导航，favicon 仍本地空响应。第7例按 root 同意改为实际 about:blank→真实点击显示模式→重开初始 HTTPS，覆盖同一无效来源 fallback 分支，不冒称 null 专证。
- 两轮各 8 张、合计 16 张截图均已逐张打开。Dynamic 截图是隐藏加载器后的首页，成功证据为其实际解析/唯一回调，不把首页图当合成正文视觉证据。

诊断包 SHA：`AD4333CBD2A8A8A2C7793DAB82F93707D4243C9F6A77AEB1FA1D09642B3C2E9F`，不是上述 R8 正式包 SHA。

已证实因果：

1. `sinaweibo://` 被真实回调拦截后，同一合成来源迟到的 START 事件看到 native 文案为“已阻止外部跳转”，随后生产 onPageStarted 将它改为“正在加载”；finish 再变“完成登录…”。
2. 重复 HTTP 降级与第5个不同升级地址均正确停止（分别只有2和5个 HTTPS 主资源），但同样的迟到 START 擦掉“来源反复跳转”的终态提示。
3. HTTP503 真正触发 `onReceivedHttpError(main=true,503)`，之后同一来源 START 擦除明确 HTTP503 文案，finish 泛化。不是测试手动调用错误事件。

Login/Dynamic 安全升级实际正文、外部主框架不发出、升级次数停止、无效来源模式恢复已通过；提示丢失仍使整轮失败。root 获得终态后接手生产修正。测试进一步保留所有原条件并新增3项合法后续导航清旧 issue 的正向检查，门槛8/59/0，尚待修后执行。

### 迟到 START 修复后绿测

session 26279 已实际 exit 0：`artifacts/navigation-recovery-emulator-5556-20261002-130734/run-1790917655743`，8 旅程 / 59 检查 / 0 失败。诊断包 SHA `2688873FB3AC1CAB94F26524D2333D8F3575E749C7AE7393192FDC84076A6B02`。8 张新图均独立逐张查看。

真实回调日志显示 HTTP503 的 START 前后、finish 后均保留 HTTP503；自定义 scheme/循环上限终态也不再变为完成登录提示。合法后续 HTTPS 来源导航实际到达 marker、允许导航并清除旧 issue，避免错误永久粘住。实际 about:blank→点击桌面版→重开初始 HTTPS 正向恢复通过。该结果不冒称真实 nullURL 专项、不含账号登录、不含各平台所有重定向形态认证。

![循环停止提示不会再被迟到事件覆盖](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/navigation-recovery-emulator-5556-20261002-130734/run-1790917655743/05-repeat-stop.png)

![受控真实 HTTP503 回调后的明确状态](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/navigation-recovery-emulator-5556-20261002-130734/run-1790917655743/08-http-error.png)

## 最终 0.3.8 正式包验收（迟到 START 修复后）

本节唯一正式 APK SHA256：`DC3D1F4C622E6BFE239C05602F699E5815B215E13AE9205E2676B949F2D2F7BB`。前文 `57AC…` 为中间候选，未混作最终包证据。

### 准确 R8 包常规 UI

session 43786，`scripts/test-release.ps1 -KeepData`，实际 exit 0，37 项检查通过。证据目录 `artifacts/release-emulator-5556-20261002-130913`。5 张新图均逐张查看：真实虎扑榜单、折叠正文、25 字号、首页更多菜单、全文标题搜索返回保留。深色/平台栏/主帖与回复标签、三行预览及分割线未发现新阻断；菜单无收藏入口。折叠截图不证明文章全部图片已解码或已读完全部内容。

### 最终包真实微博来源恢复

session 74880，`scripts/test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboProbe`，实际 exit 1。原自动正文失败被完整保留，不是 runner 崩溃，未改为整轮 PASS。证据目录 `artifacts/release-emulator-5556-20261002-131027-113/sources-1790917827103`，5 张新图均逐张查看。

1. 热榜用时 6.890 秒；首话题自动正文 21.403 秒后仍到来源协助页。泛化“请登录后重试”不能作为实际登录墙证据。
2. 真实点击协助后，808ms 观察到同平台普通 HTTP 导航被处理；2556ms 观察到 HTTPS `m.weibo.cn/search` 提交。4.491 秒观察点已有 2090 字符、13 个正文选择器，无账号输入、无认证结论。
3. 真实点击“读取到静读”返回自有阅读器，再经合法来源身份/当前 revision/可见坐标核对执行真实 pointer，展开 `part-0`。实际 1 条非 preview 有意义文本、0 张正文图，收起入口可见，深色保持。

最终截图仍明确显示两项未完成需求：展开内容结尾为“新 …全文”，是来源摘要，不是完整微博；来源同一卡有明确播放图标与 1:01 时长，其配文仍进入阅读器，移动微博视频卡过滤缺口未解决。不能以可展开摘要替代完整阅读验收，也不能据此声称登录功能成功。

![最终包来源实际可见，仍有视频卡及全文入口](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-131027-113/sources-1790917827103/source-0-recovery-mobile.png)

![最终包真实展开摘要，全文截断仍在](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-131027-113/sources-1790917827103/source-0-recovered-expanded.png)

本次最终验收共 18 张新图（受控 8、正式 normal 5、真实微博 5）全部独立查看。结论仅为导航恢复与错误状态修复通过受控检查、准确正式包 UI 无新增阻断；微博自动读取、源端全文与视频过滤继续未通过。全部命令已终态，Gradle/ADB 已交还根代理，无活动测试 session；设备保留最终正式包和原有数据。

## 移动微博视频 owner 结构取证（0.3.8 固定正式包）

session 2438 已终态 exit 1，仍保留自动正文受阻；本轮只增加测试结构观察，不改生产。目录 `artifacts/release-emulator-5556-20261002-132150-649/sources-1790918510640`，tested-release-apk.txt 确认为上述 `DC3D…` 0.3.8 正式包，不使用同期源码中的 0.3.9 版本号冒认。API35 项目 AVD 保留数据，5 张新图均实际逐张查看。普通 shell ADB 首次因用户目录权限未启动，随后项目脚本以项目 ANDROID_USER_HOME 与受批准权限正常执行；这是环境前置，不是产品失败。

1. **真实榜单→首话题：仍受阻。** 榜单6.959秒，未把来源协助页称登录墙。
2. **真实点来源：公开移动页面可见。** 4.683秒观察点2350字符/12正文选择器。截图首卡明确播放按钮与1:01；当前源、应用外框均为深色。
3. **读取→展开：仍把视频配文呈现。** 第一卡展开后保留来源“新 …全文”截断，不能称全文或视频过滤通过。

仅导出首6个公开卡的 tag/class 树（每卡最多150节点、9层）、媒体父链/数量及全文链接 host/path 形状。没有导出 HTML、节点正文、ID、账号字段、Cookie、Storage、query、媒体地址。完整结构在 report.txt 的 `WEIBO_PUBLIC_CARD_STRUCTURE`。

已观察到的真实归属：

```text
div.card-wrap
  div.card-main
    article.weibo-main
      div.weibo-og
        div.weibo-text
        div（无class）
          div.weibo-media.f-media
            div.weibo-media-wraps
              div.card-video.type-video[.vertical]
                div.mwb-video.mwbv-play.mwbv-info
                  button.mwbv-play-button
```

首6个 owner 各1个 `.weibo-text`；第0/1/3/4/5卡有上述明确媒体标志，但全部 `video` 标签计数为0。只检查 `<video>` 会漏掉尚未播放的卡片。第2卡无这些媒体节点，有11个 img（包含头像等，不能直接叫11张正文图），其媒体容器为 `.weibo-media-wraps.weibo-media.media-b`，可作后续保留非视频图文的真实对照。全文链接观察到 `https://m.weibo.cn/status/:id`，无query，未点击或验证该详情页。

引用/转发边界：本次6卡未观察到 `.weibo-rp` 或嵌套第二个 `.weibo-text`，不能编造真实转发样本。初稿 `quoteCount=1` 宽泛 class 包含 forward 查询实际命中底部 `i.m-font.m-font-forward` 分享操作图标，不是引用内容；已逐卡核对 tree 并明确更正解释，不得将该字段用于生产过滤判定。新测试后续应按真实 owner 比较视频配文消失与至少一条非视频配文保留，正文只在内存比较，日志只写布尔/计数。

![本轮真实移动来源视频卡](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-132150-649/sources-1790918510640/source-0-recovery-mobile.png)

![同卡配文仍被整理，证实漏过滤](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-132150-649/sources-1790918510640/source-0-recovered-expanded.png)

### 既有深色证据覆盖检查（不是本轮重跑）

- 0.3.4 appearance 155/0、30图覆盖API35七平台合成榜单/自有正文深浅切换，以及受控来源白页算法变暗；这是全局主题逻辑证据，不是七平台真实网页CSS或新0.3.9验收。
- 后续实网0.3.6已分别保留值得买深色榜/文/大图、贴吧真实二楼、虎扑折叠、见闻正文/大图、HN评论；本轮0.3.8微博实际来源和自有摘要也为深色。不同版本/样本不得合称本轮七源全通过。
- 缺口：新最终包七源深浅回归尚未执行；API26–32算法变暗分支尚无此appearance整组实测；第三方真实登录表单、验证弹窗及各平台多样远程CSS未逐一验证。不会以截图宣称全可访问性/所有设备统一色值。

本轮取证终态后已交还Gradle/ADB；无活动session，生产修复与后续验证由根代理安排。

## 0.3.9 精确正式包：真实视频配文排除，非视频文字保留

session2588终态**exit1**，保留自动正文受阻，未把整轮改称通过。命令`test-release.ps1 -KeepData -Mode sources -Source WEIBO -WeiboProbe`；目录`artifacts/release-emulator-5556-20261002-133057-324/sources-1790919057314`。tested-release-apk.txt核实SHA256 `8A2C1E3E36924E1B936A0727E755071BD69C282DFF3ACD416B02D35D71F95F34`，0.3.9/code20正式R8包，156118字节。本轮5张新图逐张查看，未输入账号、未清数据。

1. **榜单→自动读取仍失败。** 榜单7.037秒；首话题自动读取21.424秒后来源协助。图中“请登录后重试”是应用泛化文案，不能认定真实平台登录墙或登录就能修复。
2. **真实来源恢复。** 实际点协助，974ms观察到同平台HTTP被处理，2722ms HTTPS移动搜索提交；4.696秒观察点2193字符/14正文选择器。来源截图仍清楚显示首卡视频播放按钮与1:01，来源正文及应用外框均为深色。
3. **视频配文排除且文字保留。** 读取前，仅在内存保存可识别owner的归一化文字前缀，按明确播放器容器分类，排除引用范围和跨类同前缀歧义；日志不导出正文见证、Cookie、query或HTML。读取回自有页面、验证严格来源身份后：`publicSourceVideos=6, videoPrefixesPresent=0, publicSourceTextCards=4, textPrefixesRetained=4, ambiguousSkipped=0`。随后真实pointer展开part-0，确认1条非preview有意义文字，0张正文图。自有页面明确显示已过滤5条视频；该去重过滤ID数与6个源卡配文见证不是同一统计口径，不声称逐项数量完全一致。

![本轮真实来源仍有明确视频卡](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-133057-324/sources-1790919057314/source-0-recovery-mobile.png)

![回到自有阅读器，视频过滤说明及保留文字卡](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-133057-324/sources-1790919057314/source-0-after-read-mobile.png)

![实际展开非视频文字，仍有来源全文截断](C:/Users/10197/Documents/Codex/2026-08-23/qi/quiet-reader/artifacts/release-emulator-5556-20261002-133057-324/sources-1790919057314/source-0-recovered-expanded.png)

视觉结论：已观察的视频配文不再占据阅读卡，保留文字可真实展开/收起，深色从榜单、协助页、来源到自有阅读器保持；没有发现本轮新增视觉阻断。仍存在自动读取慢且失败、展开文字末尾“…全文”的来源摘要限制；本轮没有验证微博全文、真实账号、更多滚动加载或媒体兄弟节点里的照片完整提取。非视频卡前几段同题相似，不据此前缀相似认定重复帖子或擅自去重。

本轮统一外观受控155/0与缓存/视频受控28/0另记`EXPLORATORY-QA.md`，30+5图均已查看；加本轮正式微博5图，共40张新版截图逐张独立核对。API35专用AVD保留精确正式包与原数据，session2588终态后已交还Gradle/ADB；无新测试、无活动session。本轮关掉的是已取证移动视频owner及话题缓存边界，不把它扩大为所有微博视频、完整阅读或全平台访问完成。
