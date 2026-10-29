# 刷新入口一致性与实际展示周期

本轮仅本地修复和验证；没有推送、发布、访问手机或更改 current-candidate。手机仍是 c315361c。整体推荐效果、真机资源和真实账号权限验收继续开放。

## 复现与修复

1. 实际首页已有 pending 内容、来源仍 loading 时，圆点可以接收，下拉被 `!loading` 拦截。真实指针手势原回归 2 项 1 失败。下拉准入改为有 pending 或当前不在加载，仍共用 refreshFeed；无 pending 的加载中下拉不启动重复采集，取消手势不接收内容。
2. 原 refreshFeed 在请求时清空本轮曝光集合。存储队列阻塞、两次点击合并、随后取消的真实首页回归中，同一旧列表 shownAt 从 1793164473265 变为 1793164475091，5 项 1 失败。曝光周期重置移至 Controller 验证成功、即将发布显式刷新结果的位置，通过 Listener 通知 Activity；自动更新、排队、合并或取消不触发重置。实际成功接受同一批次后，仍须重新满足可见停留门槛才能产生新曝光。

不改变推荐质量分层、热度计算、冷却期限或兴趣学习；没有新增模型调用、服务、定时轮询或缓存额度。

## 验证

证据目录：`artifacts/local-ai/refresh-presentation-20261028/`。

- Debug 两轮实际下拉 6/0、实际曝光边界 5/0。取消后 shownAt 保持原值；成功发布后才推进，并且曝光计时器最终停止。
- 相关回归两轮：recommendation-refresh 8/0、exposure-viewport 7/0、update-dot-top 13/0、feed-navigation 18/0、explicit-feed-cache-action 6/0、pending-acceptance-race 15/0。每轮合计 78 项。最终 `*-final.txt` 使用 raw Instrumentation 输出，逐项校验唯一成功终态 -1；首轮普通输出保留，不伪造原日志终态。
- 完整 JVM 764 项 / 112 套，失败、错误、跳过均为 0；Debug/Release Lint 成功。
- R8 真实自然曝光和圆点换批：新闻路径 7/0、社区路径 7/0。精确 loading/pending 受控手势与存储阻塞取消专项在 Debug 执行，未扩大为混淆专项覆盖。
- 普通包保留 userdata：原包验证 12/0，升级后 12/0，实际双模型 19/0，再验 12/0；共 55 项。模拟 Cookie、设置和历史保留不等同于平台真实账号权限验证。

## 测试过程记录

- 首次沙箱构建因共享 Gradle 缓存依赖 AccessDenied 失败；使用既有共享工具权限后成功，日志均保留。
- 升级前第一次误用 `mode` 而非 `phase` 参数，实际执行默认模型检查 19/0；保留为 `upgrade-wrong-argument-models.txt`，不计作升级前验证。
- 随后误选仅适用于 schema6 的 `normal-upgrade-before` 阶段，已安装 schema9 导致 12 项 1 失败；保留 `upgrade-legacy-phase-mismatch.txt`。未改数据库或降低断言，改用当前版本的 `normal-upgrade-verify` 后 12/0。

## 本地候选与剩余边界

普通候选 `normal/app.apk`：319466194 字节，SHA256 `1ab99036587d0905e16c453f4fda32e262766a9a8864191e37717d0d99479452`，与模拟器实际安装 base.apk 一致。R8/驱动散列见 package-hashes.json，终态汇总见 terminal-summary.json。保留升级模拟器数据，测试后关闭模拟器。

跨 URL 后续报道、讨论实质进展、个人实际阅读收益、候选供给，以及整 App ARM 峰值内存与拔线耗电仍需继续验证；本轮不宣称整个重构已经完成。
