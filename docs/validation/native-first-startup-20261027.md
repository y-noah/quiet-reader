# 缓存原生列表先绘制 / 2026-10-27

## 产品行为

上轮仅将 Repository 旧来源迁移移出主线程，WebView 可选预热仍可能在缓存标题绘制前执行。本轮 DiscoveryActivity 在 onCreate 先注册并延后 ReaderStartup，保留 pending 状态和既有生命周期跟踪；控制器因此仍暂缓自动来源抓取、模型整理和正文预读，但缓存数据库读取照常进行。

收到首次原生 feed 结果（允许空列表）或存储错误后，在列表 OnPreDraw 的下一次动画回调释放可选预热。这样已缓存内容无需等待可选 WebView 启动。只在确有 deferred 任务、页面处于前台且已拿到原生结果时安装一次回调；onStop 移除监听及动画回调，不为后续每次 feed 更新重复调度预热。

显式阅读准备走原 prepare/retry，立即释放 deferred 任务；用户无需等待首帧门槛。重复请求仍合并到既有待开阅读请求，准备成功后按原页面、窗口焦点、epoch 条件导航。预热失败重试、最多一个无网络空白 WebView、后台释放、低内存处理及登录 Cookie 规则保留。

首帧前销毁的 Activity 可能从未 started，因此不能只靠 onStop 清理。ReaderStartup 的 onActivityDestroyed 在没有前台 owner 时安排既有一次性 backgroundRelease；取消尚未派发的任务、递增 epoch、完成等待回调，不将取消记为提供者失败。下一次进入可重新开始，不启动后台循环或网络任务。静态 deferred Runnable 仅持 application context，界面等待回调保留弱引用。

这只是启动顺序优化。WebView 后续必须在主线程执行的部分仍可能停顿，不承诺首绘后已无卡顿，也没有把工作永远跳过来制造快结果。

## 同一测试的旧包负对照

FeedStartupProbe 新增“缓存标题 preDraw 时尚未派发可选 provider worker”的状态检查；不是用某个 ms 阈值推断先后。原 cd8a8c02 隔离包配相同测试，before.txt 为预期 9 checks / 1 failure、code 0，唯一失败为该顺序检查，其他缓存保留及可选准备检查通过。

新隔离 App SHA256 `9ac5e1df00b39f0fb2169037f7a09e7fc86664ec0b7f5b93a233d3b2538fc792`，已拉取实际安装 APK 核对一致。第一组最终驱动 test.apk SHA256 `4575aa0dda25a46c03b3d5c718815081e3b7e9ece36dc083293289a895463389`；随后只增加独立生命周期观察的 test-lifecycle.apk SHA256 `dc3613fcc612613ad24212f77e9f70794b8e6656f56a2422d4be0d80df11fa50`。两组运行生产 App 相同。

## 首帧边界和原回归

证据目录 `artifacts/local-ai/native-first-startup-20261027/`。660 JVM 检查，0 failures/errors/skipped；最终 Debug App/测试包、Debug/Release Lint 成功。选定隔离回归共 93/0，每个 instrumentation code -1：

| 入口 | 检查数 | 范围 |
| --- | ---: | --- |
| reader-native-cancel | 6 | 完整 onCreate 后、首帧前直接 finish，不派发可选 worker，取消 pending、不报 provider 失败、删除隔离数据库 |
| reader-native-explicit | 6 | 同一时点显式 prepare 立即派发，真实空白阅读器准备仅完成一次，退出释放 |
| feed-startup-no-stacks | 9 | 384 条持久化合成候选，精确缓存标题先 preDraw，快照/条数保留、不生成偏好、随后可选准备成功、重进及数据库清理 |
| reader-startup | 9 | 空数据首次进入、真实空白 WebView、前后台释放和重建、所有权 |
| reader-startup-failure | 8 | 实际卡片、受控超时、真实重试操作、恰好一次打开阅读器、独立生命周期观察和最终归零 |
| reader-startup-cancel | 7 | 重试取消与陈旧 epoch 回调不补开，最终计数归零 |
| article-disk | 37 | 实际正文缓存及合成 Cookie 会话隔离等原路径 |
| repository-migration | 11 | 被故意阻塞的配置迁移仍不挡 UI，支持缓存及独立数据不误删，有限线程退出 |

本轮新发现并修复的生产失败保留在 cancel-post-created.txt：6 checks / 2 failures，首帧前销毁后 deferred pending 没有退出；补充销毁处理后 reader-native-cancel.txt 为 6/0。原失败的合并条件包含 deferredStart 尚未清除，不能误称已证明它派发了 provider worker。

两处测试问题单独保留：cancel-first.txt 在 onActivityCreated 读取尚未初始化的 controller，产生 null receiver；改为 onActivityPostCreated 后才操作，随即得到上述真实销毁失败。retry-instant-count-failure.txt 在两个 Activity 交接期间立即要求计数=1；补充独立 started/stopped 观察后，RETRY_TRANSITION 为 tracked=2 / observedStarted=2，随后明确记录首页 stop 后=1、阅读器 stop 后=0。改为有界等待实际页面完成交接并要求两个计数一致、最后均归零，未为该测试改动生产计数规则。

## 首绘观测与统计边界

新包无栈采样单轮首进程：启动→preCreate64ms、preCreate→created 回调38ms、preCreate→postCreated111ms、resumed177ms、外框 preDraw246ms、缓存标题 preDraw896ms、created 回调后心跳最大延迟105ms。提供者已预热重进为标题683ms、心跳31ms。

这些是主机 API36 Debug 隔离 App、固定合成持久化快照、关闭来源抓取下的观察。旧多轮已有明显系统波动，不能用旧1514ms和新896ms直接宣称稳定提速或套用到 Find X7 Ultra。可靠证据是可选预热派发晚于缓存标题 preDraw；不是物理屏幕已呈现、冷磁盘缓存、ARM耗电或整 App 无停顿证明。

测量口径进一步修正：onActivityCreated 可由 super.onCreate 提前派发，不等于子类 onCreate 已全部完成。旧日志字段 onCreateMs 实为 preCreated→created 回调差值。本轮新增 preCreateToPostCreatedMs，旧差值明确命名为 preCreateToCreatedCallbackMs；心跳仍从 created 回调开始，以覆盖 onCreate 后续工作。旧日志保持原样并在原诊断文档注明，不能将两种口径混比。

## 普通候选

`C:/Users/10197/Documents/Codex/New-next-trial/New-20261027-b78691eb.apk`，319449810 bytes，SHA256 `b78691ebfbd102b872664d35d1994853ac5f2c5a4c1e3dfa3e61c5bf6d9f10d4`。普通原签名 app.quietreader /47 /0.4.0-upgrade-probe /target36；DEBUG=false、RANKING_MEASUREMENT_ENABLED=false，证书 SHA256 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37` 验签通过。模型/清单及64位ELF16KiB LOAD检查通过，ZIP全成员可识别明文凭据扫描无命中。

证据 `artifacts/local-ai/ordinary-native-first-20261027/`。专用 quiet-reader-upgrade-api36/emulator-5562 保留 e7725e19/schema7 数据，当前版本核对12/0、-r覆盖后12/0、实际双模型/界面19/0、重复核对12/0，共55/0，各code-1；无卸载、清数据或重种旧状态。原收藏JSON、字体外观、来源会话模式、阅读位置及展开、合成持久Cookie保留，实际embedding/reranker及JNI执行，服务释放。实际安装APK拉取哈希匹配候选，安全目录指针已更新。

普通55项不直接执行新增的首帧顺序状态断言；该断言由上述同生产源码的 Debug 隔离包验证，不能混称所有分支均经普通R8状态测试。手机未替换，无推送/上传/发版，测试模拟器关闭、userdata保留。

## 仍开放

真实虎扑/知乎权限与贴吧来源层级、本人连续推荐收益、独立新事件去重质量、ARM整App RAM≤1GB、拔线耗电温升、真实长期物理磁盘/完整预读、完整TalkBack及全页面视觉。后续 WebView 主线程工作、真正呈现帧与交互延迟仍需真机验证，整体目标未完成。
