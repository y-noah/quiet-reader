# 首次缓存阅读的生命周期诊断（2026-10-25）

本轮只增加测试APK的观测；生产App仍为89C64F439843478C46E86A3DE6BF7769C429737AAE18D4001D0490F16506B875，319416490字节。未操作手机、提交、推送、改默认版本或发版。证据位于artifacts/local-ai/reader-first-open-20261025。

## 先前运行终态与失败

reader-timeline-20261025-a由原始session37159收集终态，命令退出0但checks=5/failures=1，不算功能通过。100条操作记录的第99条发生Replay DOM observation timeout，缺少complete；严格主机readingReplay.passed=false。首篇Activity/正文/稳定控件观测7394/9305/9480ms。前五篇已有展开、滚动、返回记录，不替代六篇全部通过。原始watcher、操作日志、summary与事后logcat保留；事后日志缓冲不保证涵盖失败时刻，因此末篇超时根因仍未确定。

首版Created生命周期点发生在super.onCreate内部，不能将createdMs-preCreateMs作为完整onCreate耗时。之前口头所述43ms仅为进入基类回调的时间，已纠正。测试现增加onActivityPostCreated/PostStarted/PostResumed，并在失败finally也记录时间轴；缺失值为-1，不填0。

## 前后台预热所有权

ReaderStartupProbe使用随机隔离数据库与真实DiscoveryActivity启动/结束，仅读取内部状态，不写私有标志。reader-startup-20261025-a为9/0：前台准备单个空白视图、未领取视图禁网络/JS、退后台宽限后释放、释放不误记provider失败、新首页恢复预热、首次领取同一对象并解除网络阻断、领取后不另建投机视图。实际返回状态foreground=1/engineReady=true/claimed=false/blank=true/pending=false。

该结果否定本轮“重新创建首页会丢失预热”的待验证猜测：DiscoveryActivity.onResume已有重新prepare调用。本轮不为这个分支引入生产补丁。debug App身份另存reader-startup-20261025-a-identity.json；当时测试驱动后来被混淆回放构建覆盖，未虚构其散列。测试源码保留，未把debug结果归给R8 App。

## 完整生命周期回放

reader-timeline-20261025-b：驱动647F7BDD6B4D5044AEE018A3C9984A98448587371E096C3E482E4DD623372DB6（driver-b归档），终态7/0；严格主机身份/顺序/完整结束通过100个不同事件、6篇展开/滚动/返回，回放62364ms。DUAL真实101向量/7输入对，普通联网预读点击前preparedEvidence=1。

|索引|完整onCreate观测ms|首次decor绘制观测ms|正文DOM观测ms|
|---|---:|---:|---:|
|0|3880|6728|11193|
|19|100|528|4239|
|39|39|247|1166|
|59|11|309|1267|
|79|104|365|1504|
|99|11|249|1301|

完整onCreate为PostCreated-PreCreated；绘制/正文时间从点击开始计。首篇preCreate1183、postCreated5063、postResumed5065、idleWaitEnded9341，说明产品启动阶段及后续队列等待都占时，不能只归因于instrumentation等待。decor绘制不是正文实际显示帧，DOM回调也不代表屏幕呈现；模拟器调度、缓存与宿主负载未控制，不能作为手机速度结论。a末篇超时本轮未重现，不将其写成已修复。

## 后续定位

显式firstReaderStacks=true仅在首篇启用限定15秒、最多75次、200ms间隔的主线程堆栈诊断，后台优先级线程、内存暂存、不进行meminfo或落盘同步采样。退出时停止并报告线程状态。此诊断会改变调度，不与无堆栈轮次作提速或能耗比较。普通运行无此线程/权限/数据采集。reader-stacks-20261025-c已启动，未终态前不计通过或推断瓶颈。

本轮真实账号全文、个人兴趣收益、Find X7 Ultra整App峰值与非充电受控能耗/发热、空间提示来源及最终升级验收仍开放。

## 首篇堆栈诊断终态

reader-stacks-20261025-c终态7/0，严格主机身份/时间轴/完整结束100/6通过，回放62469ms。相同生产App89C64F43…，驱动D5F5C7F1D85EF9D84E1E614302A99B70E7F499556D6CE936E4AD85C77A92E8B5与生产mapping归档driver-c/。DUAL实际101向量/6输入对；点击前preparedEvidence=1。首篇完整onCreate2672ms、Activity/正文/稳定控件7919/8531/8773ms；堆栈采样已改变调度，不与b轮做提速比较。

47个主线程快照，诊断线程在finally确认workerStopped=true。990ms观察Cookie读取；1195ms为设置WebViewClient，1459ms为领取空白视图时clearHistory，1694ms为设置本地视图属性。1915/2130/2345/2566/2773/3026/3237/3444ms八个快照均在Native NavigationController加载调用，经本包mapping/source对应MainActivity.reloadReader的readerWeb.loadUrl(readerPageUrl)，处于完整onCreate内。3657ms后出现消息队列空闲，之后还有WebView附着、HardwareRenderer绘制快照。快照不是连续trace，不能将首末差值等同于精确CPU耗时或排除同时发生的其他工作。

下一步应针对实际WebView导航/绘制路径做真机同包观察，不能因已有正文缓存就宣称秒开；不能通过去掉Cookie/namespace/正文版本校验换取速度。旧HTTPS同路径预热实验已经失败撤回，不重复把该方案当未做过的新修复。本轮没有将WebView迁至工作线程或增加后台浏览器池。模拟器故障与手机真实体验应分别验证；未操作手机。a末篇DOM超时仍按原始失败保存，两轮后续没有复现不等于已定位修复。

所有本轮运行都已终态、证据收集后force-stop隔离flow包。App代码/默认版本/手机安装均未改变。
