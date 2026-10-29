# 混淆包中的实际推荐与阅读交互（2026-10-25）

## 本轮解决的证据缺口

此前ReleaseDiscoveryInstrumentation只覆盖基础入口、模型校验和模型IPC，不能证明真实控制器生成推荐、等待确认后接受，或进入统一阅读器。本轮增加ReleaseReadingFlowProbe，用Android标准SQLite/SharedPreferences/文件/API和实际界面构造与操作夹具；不引用生产类、反射R8字段、添加keep规则或使用生产诊断旁路。

来源候选和正文是明确标注的合成中文内容。实际执行的部分包括DiscoveryController、FeedStore、LocalRecommendation、两个离线模型、卡片菜单、保存页、MainActivity、磁盘正文解码、ReaderHtml展开和返回。不是线上来源取得、真实账号、个人推荐质量或完全离线的验收。

## 独立构建与数据边界

新增build-release-probe.ps1的-FlowInstall及仅改变applicationId的isolatedReleaseFlowProbe构建属性，实际包名app.quietreader.flowprobe。与普通包、discoveryprobe、持久升级夹具installprobe隔离；脚本拒绝同时选择FlowInstall和IsolatedInstall。没有修改生产推荐/阅读代码、品牌或版本号，没有提交、推送或发布。

探针拒绝其他包名。每次重置仅这个专用包里的合成推荐表，保留其模型文件；六个候选均使用新一轮合成URL，避免先前夹具的内存正文/历史快照先于新文件命中。没有卸载或pm clear。既有升级夹具及其Cookie没有重置。

- App：319324959字节，SHA256 2239a191ac42c17245556ac7d56d956737efc58d650d3619d8750aa8910c05c8。
- 最终测试APK：2553068字节，SHA256 a7c3ec1bd67715c1d009ba6b714247b557612e662e4ff8575bb448bc5538c32e。
- release-reading-flow-installed-match-20261025.json：设备实际base.apk的sha256sum与上述App相同。
- App在各次修正测试夹具/选择器后未改变；变化只在测试APK。全部产物在artifacts/local-ai/，原独立升级包留档不覆盖。

## 夹具与真实操作

先通过真实偏好界面校验/准备安装包内的模型，再关闭初始页面。原始供给为六个V2EX格式的合成候选，包含娱乐、手机电池/散热、徒步、路由器等不同主题；原始首条是娱乐。种入一条手机内容MORE。来源保持启用并设置未来被动重试时间，使受测阶段不依赖真实榜单变化；没有传test_skip_fetch或test_database。

六份缓存正文均有正确metadataHash、当前namespace和会话指纹，包含24段text块，标注合成作者/测试用途。直接写文件仅准备输入，不把它算为生产网络取得、预取落盘或图片缓存的证据。实际读取由正式控制器/阅读器完成。

生产界面请求继续整理，等待当前source/profile版本的personal快照，同时读取实际SQLite向量和rerank缓存；检查界面仍显示旧首条/旧模式，直到真实点击查看更新。选择实际第一条卡片菜单保存，切稍后读，点击卡片并由标准ActivityMonitor取得真正MainActivity。用WebView可访问节点和非空屏幕边界确认正文，实际点击展开并确认第3段超出130字折叠预览；滚动900px，返回保存列表，再切回发现。最后对同一卡片通过正式菜单少看，再生成并确认新的个性化顺序。

弱学习实际开启；预读与不足20秒的短读没有READ记录。此处不覆盖满足20秒后的正例，之前debug正例证据仍独立保留。

## 结果和视觉检查

release-reading-flow-screenshot-20261025-api36.txt：19项，0失败。

1. 非debuggable包，真实模型准备界面成功。
2. 初始中立快照可见，模型完成后原卡片/模式冻结。
3. 实际向量和reranker缓存写入，显式确认才接受personal快照，实际顺序改变。
4. 真实卡片保存、保存页显示和点击进入统一正文成功。
5. 实际展开呈现预览以外的段落，完整缓存正文可以滚动。
6. 返回保存列表，再回发现仍保留已接受的快照和适合我模式。
7. 弱学习开启时短读/预取不产生READ。
8. 实际少看反馈保留V2EX来源，新结果仍需确认，接受后原首条被调后。

release-reading-flow-expanded-20261025.png已实际查看：来源栏、收起动作和多段正文可见；文本换行正常，无横向溢出或播放器。截图在展开动作的保位位置，正文标题位于上方，不称为文章首屏截图。单张合成正文截图不能代表全部来源/字号/颜色/无障碍验收。

release-reading-flow-alignment-20261025.json：六个64位库全部LOAD段16KB对齐，0失败。release-reading-flow-credentials-20261025.json：全部ZIP成员已知明文密钥格式0命中；这两项不代表ARM实机或任意秘密缺失证明。

## 保留的失败过程

- first：9项后1错误，夹具错误使用p块，实际缓存类型为text。
- text-fixture、history-reset：各9项后1错误。只改磁盘/清对应历史不足以隔离初次模型准备页面可能已读入的旧内存正文；保留失败，不归为生产读取回归。
- generation：9项后1错误。改用新URL后，界面转储已经显示正确正文折叠预览，但WebView通过findAccessibilityNodeInfosByText检索未返回该节点；该遍历API问题不能解释为正文没显示。测试改为直接遍历标准可访问树，并仍要求visible和非空屏幕边界。
- accessibility：10项后1错误，实际展开和第3段检查通过，测试使用instrumentation context的外部文件目录为空，截图失败。
- screenshot：改用target context的外部目录、补上非空bounds检查后19/0，截图已取回检查。

这些修正均在测试夹具/测试驱动；没有为通过检查放宽生产逻辑。每次失败报告与build-release-flow-*.txt均保留。

## 恢复及剩余门槛

build-store-after-release-flow-20261025.txt、install-store-after-release-flow-20261025.txt记录正常debug/store构建与原签名-r安装恢复成功，未清数据。当前JVM XML有效结果419项，失败/错误/跳过均0；构建可复用结果，不声称全部重新执行。tracked diff检查和新增探针空白/冲突标记检查通过。

本轮补上了混淆包中真实控制器推荐到阅读的一条完整路径，不关闭所有来源、评论续页、图片、登录、账号切换、冷安装、普通包正式升级/回滚、完整TalkBack、个人独立留出推荐质量，以及Find X7 Ultra整体RAM/耗电/发热门槛。整体目标仍未完成。
