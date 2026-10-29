# 刷新首帧同步修复（2026-10-29）

## 修复与证据

用户要求继续修已知问题后，补测此前没有直接覆盖的“新卡片第一帧仍是忙碌圆点”。原生产代码在 OnDraw 后 post 另一个 UI 任务结束刷新，意味着首次画出新列表时状态仍忙碌；新增首帧观察在旧代码得到11检查、1失败、raw 0，baseline-frame.txt 原样保留。此结果仅证明首帧时序差异，不证明手机上秒级延迟的全部原因。

DiscoveryActivity 改用一次性 OnPreDraw：替换卡片完成布局后、同帧绘制前结束显式刷新。回调接口相应命名为 beforeFeedDraw；保留 epoch 校验、停止/销毁时移除监听及后台采集不延长忙碌的规则。没有加入定时停止、周期轮询或新的网络/模型工作。

测试实际观察首次绘制时的圆点 busy 状态，并核对 ListView 第一张实际卡片 View 中的新标题，不能只靠数据字段更新判定。受控 pre-draw gate 可阻止整帧显示，此时准备好的布局可以结束忙碌状态；没有屏幕帧会展示中间状态。数据仍在队列中时必须保持忙碌。相应旧测试“未画出之前字段必须仍 busy”的实现假设已调整为上述用户可见行为要求，未改变生产排序、供给或缓存以迎合测试。

## 实际检查

证据位于 `artifacts/local-ai/refresh-frame-sync-20261029/`。

| 检查 | 结果 |
| --- | --- |
| 旧生产代码首帧基线 | 11检查、1失败、raw 0 |
| 修复后实际首帧/新标题/有限批次/重建 | 两轮各13/0、raw -1 |
| 动画角度、生命周期、连续点击、失败恢复 | 13/0、raw -1 |
| 实际顶部下拉与取消 | 6/0、raw -1 |
| 存储失败作用域 | 12/0、raw -1 |
| 刷新快照与回滚 | 14/0、raw -1 |
| 普通旧候选覆盖前状态 | 12/0、raw -1 |
| 原签名新候选覆盖后状态 | 12/0、raw -1 |
| 普通非调试 R8 包即时忙碌与最终停止 | 11/0、raw -1 |

成功运行共106检查；不把失败基线计为通过。首次覆盖前检查误用了 normal-upgrade-before，它专供 schema6 旧版本，而保留的 bad82 数据已经是 schema9，故12检查1失败；新包当时尚未安装，记录 upgrade-before.txt 保留。纠正为 normal-upgrade-verify 后，原数据12/0，再覆盖新包12/0；没有重种或重写数据库/登录 Cookie 来绕过失败。

runtime-summary.json 独立解析全部11个日志，每份恰好一个检查汇总和一个 raw 终端。JVM 共903测试/127套件，失败、错误、跳过均0；Debug/Release lint 成功，详见 jvm-summary.json、fixed-final-build.txt 和 lint.txt。

## 候选交付

新候选 `New-0.4.0-20261029-ca219a90.apk`，319498946字节，SHA256 `ca219a90e8bec16cf08fa3899f80b9658bc4c99670bc66c61f2f3bd31353643a`。普通包 app.quietreader / 48 / 0.4.0 / targetSdk36，原证书和16KiB ZIP对齐通过。

与 bad82 普通候选比较，仅 classes.dex 改变，无增删ZIP成员；29个模型、原生库和 manifest/资源成员逐字节不变。见 package-comparison.json、signature.txt。覆盖前后实际安装 base.apk hash 均核对。

仅更新安全目录 staged-candidate.json；phoneInstalled、published、phoneValidationComplete 均为false。手机/current仍805b4ff9，未访问手机、推送或发布。候选和源码指纹见 candidate.json / source-sha256.json。

两台专用模拟器的临时驱动均已卸载，Debug 动画截图已删除，App 数据保留；停止后两个指定 serial 均不可达，见 cleanup.json / stopped.json。最终源码指纹与已测构建一致。

## 限制

新卡片首帧直接观察来自受控 Debug 隔离包；普通 R8 的补充检查仍只证明动画即时开始和最终结束，不能冒称它也完成了新标题首帧对照。知乎/虎扑真实账号权限、Find X7 Ultra 动画感受、整App内存和耗电仍未关闭。去重策略没有改动，不将本次时序修复描述为已解决所有已知问题。
