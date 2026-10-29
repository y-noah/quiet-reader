# 发现页分批供给与刷新收尾 / 2026-10-25

## 范围与问题

按文字流平台的热门文章、问答、帖子和讨论推进发现主流程。此前 HotSourceCollector 各源独立保存，但 DiscoveryController 只在整轮 onFinished 后构建列表；首源已经保存时，其他慢源仍会阻挡首批卡片出现。新增受控来源测试在修改前得到 5 项检查、5 失败（4 个断言及等待显式更新超时），记录 discovery-supply-baseline-api36.txt；不是在线来源测速。

## 实现

- HotSourceCollector 仅在有效非空快照实际提交成功后通知 onSnapshotCommitted；开始请求、失败、重复或已取消回调不触发有效供给通知。
- DiscoveryController 以 250ms 合并短时间内的来源结果，整轮尚未结束即可构建首批候选。空列表先显示有效结果；已有列表继续冻结，通过“查看更新”接收后续批次。不在来源请求未结束时启动本地模型。
- 最后一个来源结束时，检查最新快照的来源版本、画像版本、筛选和排序模式。匹配时复用已有有效快照，避免末尾失败再构建相同更新；不匹配则重建。替换刷新失败后，前一轮已经保存但尚未展示的有效来源数据仍可进入更新。
- 新刷新、离页和关闭移除延迟动作；刷新/离页增加列表 generation，拒绝已经排入存储队列的旧发布/读取结果。返回后按现有来源/画像版本检查恢复已提交内容。
- 空发现页刷新中显示“正在获取热门话题…”，结束后恢复原有空列表/筛选提示。只重绑空列表的加载状态，不借此重排已有卡片。
- 新增包内构造参数用于受控 Transport 注入；生产构造函数仍使用实际 Repository/DynamicBoard，不增加发行版 Intent 开关。

本次不增加来源、不改默认平台、不改变跨来源热度、模型和平台偏好算法。正文/图片预读仍使用既有调度，不能把首批卡片提前出现写成正文已经缓存或所有平台请求加速。

## 验证证据

以下报告位于 artifacts/local-ai/：

| 验证 | 结果 | 报告 |
| --- | --- | --- |
| 最终构建、JVM | 403，失败/错误 0 | build-discovery-supply-final-verified.txt；app/build/test-results/testDebugUnitTest |
| 分批供给/生命周期/失败收尾 | 16/0 | discovery-supply-final-verified-api36.txt |
| 分批供给时序重复验证 | 16/0 | discovery-supply-repeat-verified-api36.txt |
| SQLite 与来源采集器完整回归 | 158/0 | feed-store-supply-final-verified-api36.txt |
| 卡片/返回/筛选/反馈 | 77/0 | discovery-ui-supply-completion-final-api36.txt |
| 自动推荐与双模型执行 | 8/0 | automatic-recommendation-supply-completion-final-api36.txt |
| 主动预读生命周期 | 10/0 | prefetch-lifecycle-supply-completion-final-api36.txt |
| 平台偏好 | 15/0 | platform-preference-supply-completion-final-api36.txt |

分批供给使用真实 DiscoveryActivity、Controller、SQLite 与主线程调度，受控三源 transport 延迟第二/第三个请求。验证未等整轮完成就读首批、后续不替换可见卡片、显式接收更新时仍刷新中、末源失败不虚报更新、已提交后离页取消发布、迟到拒绝、返回恢复、新轮替换、全失败保留，以及替换失败找回已提交数据。

中间 discovery-supply-completion-final-api36.txt 15/1：测试最后一场景再次请求刚失败的微博，正常退避使 transport 未启动，测试等待超时。改用刚成功的知乎源发起替代失败，不修改生产退避或放宽断言。保留该失败报告；最终 16/0。

git diff --check 及本次修改 Java 的行尾空白/冲突标记检查通过。没有提交、推送或发版。

## 验收边界

这是 API36 模拟器的控制流与交互证据，不是 Find X7 Ultra 真机性能、真实来源完整可读率或个人推荐效果证明。来源端权限、实时供给与长期新鲜度仍需实际验证；个人推荐仍缺独立人工留出和多次真实使用反馈；ARM 整体 RAM ≤1GB、耗电/发热和精确升级仍待验收。整体目标未完成。
