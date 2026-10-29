# 过期存储错误与刷新请求归属（2026-10-29）

## 问题与实现

`DiscoveryController.work` 的普通异常处理原先只判断 Controller 是否关闭。旧队列任务晚返回异常时，会停止后来一次显式刷新、修改新推荐状态并显示错误。受控真实 Controller/SQLite 队列原实现 10 项中 6 项失败，见 `artifacts/local-ai/work-failure-scope-20261029/before.txt`；不是从静态代码推断用户已经遇到同一根因。

现在入队时捕获 feedGeneration 和 explicitRefreshEpoch，错误回主线程后只处理仍属于当前代次和当前刷新请求的异常。四处推荐拥有的队列工作另核对 recommendationEpoch/前台状态；对比评价还核对 trialFailure。普通存储任务不一律绑定推荐代次，避免一次普通滚动吞掉真正属于当前请求的存储错误。成功回调沿用各自已有归属检查，不改变来源、模型预算或定时策略。

## Debug 证据

实际隔离 Activity、独立 SQLite 库、真实存储执行队列和受控失败时序；不请求外部来源、不运行模型。最终专项连续两轮各 12/0，原始 instrumentation code 均 -1。验证旧失败不能取消新刷新的发布/等待绘制状态、旧 generation/显式请求/已取消推荐异常不污染新状态，以及当前普通错误仍能终止动画并保留可读列表。

首次修复后 12 项中 1 项失败保留：测试错误地要求刷新后 snapshot ID 必须变化，而未曝光且顺序未变时生产代码合法复用快照。只修正测试为观察真实 feed 发布计数、busy 状态和持有的 afterFeedDraw 回调，没有修改生产快照规则来迎合测试。

`RefreshPresentationProbe` 的存储失败注入由点击前移到三次点击之后，令其确实属于当前请求；原测试把旧错误取消新刷新当作预期。调整后动画 13/0，取消排队推荐输入 9/0，待接收结果竞争 15/0，下拉/待更新 6/0，刷新曝光边界 5/0，有限首页批次 10/0，共 58 项。含专项两轮共 82 项，全部 raw -1。所有原始输出保存在同名 artifacts 目录。

生产修复后完整 JVM 899 测试/127 套、双 lint 和 Debug 构建通过；随后仅调整上述快照断言并重建驱动成功。Debug 实际 App/driver SHA256 与本地受测 APK 一致，见 `debug-installed-hashes.json`。未把 adb 退出码当作测试通过证据。

## R8、普通升级与候选

最终 R8 模拟器构建成功，实际首页换批/动画 10/0、真实双模型利率事件与来源守恒/热度不叠加/手动拆分 25/0、待接收列表正文预缓存与真实阅读复用 10/0，共 45 项，raw -1。它们证明正常流程未退化，不是混淆版强制错误注入证明。

专用升级 AVD 先核对实际旧包 SHA256 为 7c9a2db9，保留 userdata 的基线 12/0；覆盖安装原签名候选后 12/0，实际双模型 19/0，再验 12/0，共 55 项，raw -1。本轮没有重种或续期 Cookie，合成 Cookie/阅读位置/字体/外观/来源模式及旧数据保留。不能称为真实受限账号验证。R8 和普通 App/driver 四个实际安装 hash 均匹配，见各 installed-hashes.json。

最终普通候选 `New-20261029-d9cbac97.apk`，319498962 字节，SHA256 `d9cbac977835926fb5c9afe1ae1a7cb4ecbf4129506e776d0c6665b2955b0d39`；app.quietreader / 47 / 0.4.0-upgrade-probe，targetSdk36。五模型文件与源 manifest hash、六个64位ELF的16KiB LOAD、ZIP对齐及原证书 `ab59106cfee4c6d46f0206d37b0c8ee5d976972abe62716b2eb7275a719e8a37` 核对通过。81 ZIP成员已知明文凭据格式扫描0发现，不代替完整外联审计。首次审计调用误用了共享Python目录，签名调用缺JAVA_HOME，均未通过；改用工程已有Python及已验证JDK后成功，不把工具调用失败算成审计结果。

仅更新 staged-candidate，安全副本 hash 一致；current-candidate 文件 hash 未变，手机仍805b4ff9，phoneInstalled/published/phoneValidationComplete均false。两AVD卸载临时驱动、停止App并退出，保留userdata；最终ADB无设备、指定AVD的CIM进程0。本轮探针无新增需要导出的截图文件。

## 开放边界

本轮共 182 项实际 instrumentation 检查，不把重复开发样本当独立准确率。本专项不能证明真实账号内容/全分页、Find X7 Ultra 一划手感/动画/ARM 峰值/耗电、长期缓存、个人推荐收益或独立去重质量；上一窗口5/11同事件漏项仍保留。手机未访问，未发布，整体目标仍未完成。
