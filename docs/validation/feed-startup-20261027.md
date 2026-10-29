# 缓存首页首绘诊断 / 2026-10-27

## 范围与结论

新增 androidTest `FeedStartupProbe`，由 `FeedStoreInstrumentation` 的 `mode=feed-startup` 进入。没有修改生产启动顺序，也没有更新普通候选包。API36 主机模拟器、Debug 隔离应用、384 条已接受且持久化的合成 IT之家候选，来源抓取关闭。测量从发起 Activity 到真实视图树中精确首条标题的 OnPreDraw；这不是屏幕实际呈现帧、系统冷磁盘缓存、ARM 性能、模型完成或真实来源联网性能。

两轮修正后的完整运行均 8 checks / 0 failures，且 instrumentation code -1。首进程首页标题首绘 1513 / 1143 ms；提供者已预热的再次进入为 715 / 624 ms。不能由两次重复得出稳定延迟或可靠提升。

此前 action-prefetch-performance-20261027 中的 10427 / 4320 ms 是整段自动排序及准备完成时间，不能当作首页等待时间。本诊断将两种口径分开。

## 测量

| 轮次 / 阶段 | Activity preCreate | onCreate 区间 | resumed | 外框 preDraw | 缓存标题 preDraw | 创建后心跳最大延迟 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| diagnostic / 进程内首次 | 71 | 33 | 364 | 429 | 1513 | 288 |
| diagnostic / 提供者预热重进 | 25 | 12 | 45 | 80 | 715 | 91 |
| repeat / 进程内首次 | 66 | 26 | 303 | 369 | 1143 | 242 |
| repeat / 提供者预热重进 | 64 | 25 | 94 | 115 | 624 | 18 |

单位均 ms。onCreate 由生命周期 preCreated/created 回调差值观测，其他时间相对启动请求；心跳在 created 之后开始，不能覆盖整个 onCreate。first-home-in-process 不等于系统冷启动。

## 观测与后续定位

最多保存 8 个不同主线程栈，每 25 ms 检查一次，仅在首标题尚未首绘且主线程心跳超过 100 ms 未执行时采样。记录存在 SharedPreferencesImpl.awaitLoadedLocked → Repository.clearRetiredSources → ArticlePreloader 构造、WebView 浏览器启动及其 Binder 查询、硬件渲染初始化、文字测量等栈。采样说明这些工作出现在启动区间，不能证明各自耗时占比，更不能将一次栈记录当成根因。

下一步应分离配置迁移成本与 WebView 必须在主线程完成的工作，若调整启动排序，需同时覆盖缓存非空/空列表、重进、快速阅读、切后台取消及初始化失败重试，不能通过推迟整套读取准备来掩盖成本。当前不据此改动生产，也不宣称启动优化已完成。

测试检查已接受快照仍为原快照、384 条记录保留、没有凭空生成阅读偏好、可选阅读器准备成功；每次首绘后暂停可选整理工作，并在 Activity 退出及控制器存储线程结束后删除隔离数据库。条数检查不等价于逐条正文内容一致性验证。

## 原始失败与证据

第一版曾记录首标题 2166 ms、外框 678 ms、心跳延迟 611 ms，随后因测试调用 readSnapshot(limit=500) 违反生产最大 200 条限制而失败。已改为两页各 200 条，只修改测试；保留 results.txt / first-page-size-failure.txt。这个失败不能算通过或正式性能基线。

证据目录：`artifacts/local-ai/feed-startup-20261027/`。

- app.apk：89459729 bytes，SHA256 `e9190d335d6c007063531a8426e876127fd3860ae009850867e8052564c2d53d`。
- test-diagnostic.apk：3886673 bytes，SHA256 `1c20f306d7cbb7e0f70b157c423c2590f0e4a314b55e2805812d0bd5595c3238`。
- diagnostic.txt：SHA256 `56dab8db95540ec58b434079716e43aed6931dbd5d1f2da50ed6048aa80c5cda`。
- repeat.txt：SHA256 `a90e4a4916c5a9ca0fac6910d72e0bf2206fea048b371d741ccce978ad9405c0`。

测试已结束并关闭指定模拟器，无手机操作、推送或发版。普通候选仍为 New-20261027-7dbfd1d4.apk。真实登录、贴吧层级、ARM 整 App 内存、非充电耗电温升及个人推荐收益门槛仍开放。

## 无停顿栈采样的补充对照

增加仅测试入口 `mode=feed-startup-no-stacks`，完全不创建定期采样 executor，仍保留同样的生命周期、preDraw 和心跳观测。已有 `feed-startup` 默认保留采样，并在每条 STARTUP 记录明确 stackSampling 值。没有生产开关或普通包行为变化。

指定 AVD 重新启动后连续运行无采样、无采样复测、有采样三轮，每轮重新 force-stop 目标应用。三轮各 8/0 且 code -1，共 24/0；不是随机化基准，不能证明采样零开销。

| 轮次 / 阶段 | Activity preCreate | onCreate 区间 | resumed | 外框 preDraw | 缓存标题 preDraw | 创建后心跳最大延迟 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| no-stacks / 进程内首次 | 140 | 52 | 842 | 1117 | 4858 | 887 |
| no-stacks / 提供者预热重进 | 152 | 40 | 211 | 299 | 2196 | 93 |
| control-no-stacks-repeat / 进程内首次 | 84 | 41 | 497 | 641 | 1787 | 428 |
| control-no-stacks-repeat / 提供者预热重进 | 52 | 10 | 72 | 103 | 598 | 31 |
| control-with-stacks / 进程内首次 | 71 | 30 | 285 | 548 | 1259 | 258 |
| control-with-stacks / 提供者预热重进 | 97 | 12 | 117 | 142 | 796 | 70 |

第一轮在 AVD boot_completed 后开始，仍可能有系统启动余波。关闭栈采样并未消除较大延迟；不能把这个表当作抽样开关的因果对照，也不能将 1.1 秒描述为稳定首屏指标。单次快结果不足以支持生产优化结论，需要控制系统运行条件和分阶段耗时。

新测试包 `test-no-stacks.apk` SHA256 `a7e54ec4e47419173c26193b7c9bf009a2fee7b2e266450b4fdd5d8f31e47b5e`，构建记录 build-no-stacks.txt。实际运行目标通过 pm path 拉取 installed-control.apk，SHA256 与原 app.apk 的 e9190d33 完全一致。三份新日志为 no-stacks.txt / control-no-stacks-repeat.txt / control-with-stacks.txt；本次与前两轮合计 40 个执行检查通过，质量和真机性能门槛不变。已关闭指定模拟器。

后续已单独修复 Repository 构造的同步迁移等待，证据及更新普通候选 e7725e19 见 repository-migration-20261027.md。本页数值保留为改动前诊断，不能与后续单次结果直接作可靠性能对比。

后续生命周期观察确认 onActivityCreated 可由 super.onCreate 提前派发，因此本页旧日志 onCreateMs 只是 preCreated→created 回调差值，不是完整子类 onCreate 耗时。新测试另记 postCreated，并明确区分这两个区间，见 native-first-startup-20261027.md。首绘及心跳原始数据不变。本页历史候选已由该文 b78691eb 替代。
