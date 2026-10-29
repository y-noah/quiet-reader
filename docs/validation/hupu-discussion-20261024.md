# 虎扑文字讨论续读 / 2026-10-24

## 缺口与实现

上一轮微博作者修复为进展。本轮真实虎扑热榜公开帖子 `643184294` 提供50页回复，但DiscussionPages只支持V2EX和贴吧，旧虎扑DOM没有保留稳定回复ID/作者，原分页是打开独立下一页。

新增HupuDiscussion，仅解析公开页 `__NEXT_DATA__.props.pageProps.detail` 中身份匹配的thread、lights和replies；要求thread.tid、thread.url与当前帖子一致、replies.current等于请求页码，公开visibleRange为ALL_SEE且status0。权限限制返回明确登录/权限状态；身份/页码不匹配或状态结构错误不能用推荐栏回退为正文。没有state的旧DOM仍走旧解析。

首屏保留主帖与作者；热评优先、再来源普通回复，用pid去重，不以内容相同推定同一回复。后续页只输出回复，DiscussionStream按已知身份追加到原主帖。隐藏/审核/删除回复跳过，视频回复过滤，主视频保持既有默认过滤及显式兜底；身份匹配state明确hasVideo时，即使播放器尚未hydrate也不误判图文。统一解析沿用原内容清理、图片和链接处理，不提取账户state或latest/hot推荐。

虎扑页码为`/tid.html`和`/tid-N.html`，只接受合法正数/有限页码；分页从真实hupu-rc-pagination等已知控制中选同帖子最近后续链接，不自行构造未出现的下一页。复用600回复/2MiB预算、单有效请求、会话namespace与epoch保护及原阅读锚点。DynamicReader补同帖子且同请求页校验，避免后续页被重定向回第一页仍当作成功；Repository已有同页保护继续复用。

旧无稳定身份虎扑快照/视频DOM保持独立下一页入口，不强行与新版source-ID回复混合；若新版续读返回旧DOM则保留原内容、明确失败。无缓存/历史数据重置、数据库升级、后台评论预取或新增模型。

## 证据

报告在`artifacts/local-ai/`，公开原始页在`artifacts/public-sources/`：

- `hupu-hot-discussion-20261024.html`：当次真实热榜提供`/643184294.html`及2/50页入口。两页公开HTML取得thread.tid、replies.current、作者、pid和分页。裁剪到`app/src/test/resources/hupu-discussion-page1.html`、`page2.html`时只保留正文相关字段/公开作者昵称和分页，去掉账户、个人标识字段及推荐。原内容不作虚构替换；控制台部分Unicode显示失真不代表源文件损坏。
- `build-hupu-discussion-baseline.txt`：新增初始7项中6失败，复现身份/作者/分页/权限缺失。第一次生产修复仅旧“继续阅读下一页”断言失败；最终通过区分旧无身份文档与新流式文档保留旧兼容，没有删除原断言。
- 最终HupuDiscussionTest10项，含真实裁剪两页：首屏主帖+64条去重回复，第二页20条，其中1条已出现，合并主帖+83回复；同帖错误页/错误tid/受限/隐藏数字与布尔标志/视频及旧快照混合均覆盖。`build-hupu-discussion-multipage.txt`最终构建成功，JVM360失败/错误/跳过0。
- `discussion-flow-hupu-api36.txt`、`discussion-flow-hupu-final-api36.txt`、`discussion-flow-hupu-verified-api36.txt`均50/0：V2EX/贴吧/虎扑实际触摸、重复点击单请求、锚点、去重、失败重试、迟到回调、后台取消/返回重试、末页、磁盘恢复；知乎共享快速追加及三来源网页请求页保护。受控网页，不冒充认证来源证明。
- `live-hupu-discussion-touch-api36.txt`与`live-hupu-discussion-repeat-api36.txt`：实际公开热帖，64条具source ID和作者回复、主作者；通过生产卡片入口进入共用阅读器，无原网页跳转或正文重载；触摸第二页64→83，DOM无横向溢出，位置误差约0.12 CSS px。后者仍由首屏开始，不声称已经恢复第三页。
- `live-hupu-discussion-three-pages-api36.txt`：同一真实阅读会话连续点击两次，64→83→100，页码2→3，DOM每次均匹配，始终单一续读入口；可见段落top分别9.97023868560791→9.851190567016602、3.9642858505249023→3.9642858505249023。单独正常DiscussionStream也取得第二页并保持主帖与原元数据。它是独立流检查，不是第三次UI点击。
- `hupu-three-pages-api36.png`已目视检查作者/回复卡片、实际图片与窄屏无横向溢出。位置测试等待最终就位，不能证明全程无瞬间跳动。
- `feed-store-hupu-discussion-api36.txt`158/0；`discovery-ui-hupu-discussion-api36.txt`77/0；最终diff检查通过。最终仅补第二次实际点击的诊断代码后再次构建/360JVM与真实三页通过，生产代码与上述广回归相同。

## 未完成范围

真实证据限一个公开热帖三页，不证明50页全部、其他权限类型、账号切换、所有字体主题或长期稳定。未知state格式明确失败，不把摘要或原网页当作新版正文适配。主视频兜底继续保留旧DOM路径，不声称新版state视频播放器完成。

个人反馈标注/独立留出、认证知乎完整内容及其他来源、Find X7 Ultra真机RAM/耗电、精确签名包升级等仍未完成。无提交、推送、发版、改名或换logo；整体目标继续。
