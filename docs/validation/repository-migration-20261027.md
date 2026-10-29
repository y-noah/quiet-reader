# 旧来源配置迁移移出主线程 / 2026-10-27

## 产品改动及边界

首屏诊断栈曾出现 SharedPreferencesImpl.awaitLoadedLocked → Repository.clearRetiredSources → ArticlePreloader 构造。原 Repository 每次构造都同步读取 boards / video-filter / source-session，再清理弃用来源数据；发现页会在主线程创建该对象，因此配置尚未载入时可能阻塞界面。

现将同一清理逻辑移到 `source-cache-migration` 后台优先级线程，每个进程用 AtomicBoolean 只启动一次，执行结束即退出，不建立常驻线程池、定时器、网络请求或自动重试。只持有 application context；关闭 Repository 的请求队列不会中断共享的有限迁移。若启动线程失败可由下一次 Repository 构造再次尝试；执行失败只记录固定通用日志，留待下次进程启动，不使原生首页构造失败。

安全性来自原有读取边界：不支持的来源已不能读取缓存或成为可见候选，迁移无需在界面启动前完成。清理的仍是非 readable 来源命名空间、对应 video 标记以及废弃 tieba-desktop 字段；没有扩大删除范围，也没有重写支持来源的正文、偏好、历史、Cookie 或来源会话设置。后台清理与有效来源写入只涉及互不重叠的键。

这个改动消除一处已证明的主线程配置等待，**不是整段首屏或 WebView 初始化已优化完成**。没有调整 ReaderStartup、模型排序、主动缓存或用户偏好规则。

## 同测试旧包负对照与新包验证

新增 androidTest `RepositoryMigrationProbe`，`mode=repository-migration`，仅 app.quietreader.discoveryprobe 可运行。ContextWrapper 将每个偏好作用域映射到随机、保留前缀的真实 SharedPreferences；在工作线程读取第一份迁移配置时用 latch 故意阻塞，任何 UI 线程迁移配置读取都立即报错。不是靠固定耗时阈值猜测卡顿。

原 e9190d33 隔离包配同一最终驱动，报告 `UI repository construction attempted migration preference IO`，栈明确位于原 clearRetiredSources/Repository 构造；`before-caught.txt` 为预期负对照 1 check / 1 failure、code 0。这里的通过项仅是最终临时数据清理，不把负对照算通过。

最初负对照驱动未在 runOnMainSync 内捕获异常，导致测试进程崩溃，日志 before.txt / before-crash-log.txt 保留。修正仅测试异常捕获，并在隔离包中精确清理本 probe 保留前缀、限定六个作用域的中断临时数据；没有删除应用实际数据。旧包保持不变后重跑，得到上述可读的预期失败。

新隔离 App SHA256 `cd8a8c02ccceea810906963feb95941d44549568977b8cb8eedf55d57f88b461`，最终驱动 SHA256 `fbab782938ac18fe4d176aad4107da3d3f05a6cfb6c5ab37df854ae37159a6f3`。实际安装 APK 拉取散列与归档 App 一致。两次独立进程运行各 11/0，均 code -1，覆盖：

- UI 构造不读迁移配置；配置读取被阻塞时主线程队列仍可响应。
- 多个 Repository 共享一个任务；即使关闭这些 Repository，也不新建重复迁移或遗留空闲线程。
- 废弃榜单字段、视频标记和旧 desktop 字段被移除。
- 遍历所有当前支持平台，核对缓存值、时间、endpoint 和视频标记均保留。
- 保留另外的会话哨兵，以及独立正文命名空间、历史、设置偏好夹具。
- 只在工作线程读取既定三份配置，任务结束、测试偏好文件清理完成。

这些是受控真实配置文件和哨兵，不是已证明真实虎扑/知乎账号登录。

## 回归及首屏观测

660 JVM 检查，0 failures/errors/skipped；Debug App/驱动及 Debug/Release Lint 构建成功。新 App 上缓存首页 8/0、阅读器前后台释放/重建 9/0、正文磁盘缓存 37/0，均 code -1。本轮隔离正向合计 76/0（22+8+9+37）。

本轮无栈采样缓存首标题 preDraw 首进程 1514ms、提供者预热重进 658ms；创建后主线程心跳最大延迟 488/53ms。此前多轮波动较大，本轮也有 WebView 等启动工作，不做可靠提速、无停顿或真机延迟的结论。详见 feed-startup-20261027.md 的测量范围和原始失败。

证据目录 `artifacts/local-ai/repository-migration-20261027/` 包含构建终态、负对照、after/repeat、三个回归、实际安装包和驱动。

## 原签名普通候选与保数据升级

新候选 `C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-e7725e19.apk`，319449810 bytes，SHA256 `e7725e19479c85a768d0b98147ee3e0bb91453a20951c710d8a9650c14cdd039`。普通 app.quietreader / versionCode47 / 0.4.0-upgrade-probe / target36，DEBUG=false、RANKING_MEASUREMENT_ENABLED=false。原签名证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37` 验证通过。

普通 R8 构建终态成功；模型及清单逐项检查、64位 ELF 16KiB LOAD 检查通过；所有 ZIP 成员可识别明文凭据扫描无命中。首次单独验签命令未设置 JAVA_HOME，保存 signature-missing-runtime.txt；配置既有 JDK 后验签通过，并非签名本身失败。

专用 quiet-reader-upgrade-api36/emulator-5562 保留既有 7dbfd1d4/schema7 数据，升级前 12/0、-r 覆盖后 12/0、实际双模型及界面 19/0、再验证 12/0，共 55/0，各 code -1。没有卸载、清数据或重种迁移夹具。支持来源会话设置、合成持久 Cookie、旧收藏原文、字体外观、阅读位置和展开关系保持，双模型实际运行且服务退出。真实账号权限、ARM 资源不能由该模拟器证明。

第一次误用了预期 schema6 的旧 before 阶段，产生 schema7 != 6 的单项测试失败，另存 before-schema6-assumption.txt；改用与当前 schema7 匹配的 normal-upgrade-verify 后重跑升级前基线，无生产或数据修补。这不构成新版本 46→47 迁移证明。

实际安装 APK 拉取哈希匹配新候选，安全目录 current-candidate.json 已更新。证据目录 `artifacts/local-ai/ordinary-repository-migration-20261027/` 包含模型扫描、签名、mapping、BuildConfig、安装比对及所有测试日志。

手机仍未更新，无上传/推送/正式发布。两个指定测试模拟器已关闭，保留 userdata。真实账号、贴吧来源关系、整 App ARM RAM≤1GB、拔线能耗/温升、推荐收益和独立事件质量仍开放，整体目标未完成。
