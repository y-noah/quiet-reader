# 事件模型完整输入保护（2026-10-26）

已实现，而非待办：LocalModelEngine 保留原有 pair token 组装与256上限，额外报告 originalTokens / usedTokens / complete。RankingService 将输入覆盖状态随实际方向分数回传。事件装配需要两个方向都明确完整；不能由字符数或高分推测。

FeedStore 在同一事务中写入原始方向分数与版本化覆盖标记，仍受既有5000行LRU限制，无数据库迁移。旧无标记、非法标记或标记被逐出均视为事件缓存未命中，安全重算。已知截断结果保留缓存且不允许合并，再次整理不会无限重算。装配及前瞻策略版本已更新。

## 实际证据

- build18-coverage.txt 构建成功；严格解析91份最终JVM XML：544测试，0失败/错误/跳过。
- input-coverage-first.txt：23检查，0失败。5个批准pair token golden及5个INT8数值golden不变。真实引擎处理合成不同结尾标题时，两个方向均8.103666分，但262原始token仅使用256；覆盖为false，装配保持分开。此为合成风险反例，不能冒充真实新闻。
- 同专项验证真实SQLite旧无标记缓存失效、已知截断缓存、标记写入故障事务回滚、非法0.5标记、缺失/逐出标记及完整方向持久化。
- recommendation-coverage-first.txt：19检查，0失败。实际普通LocalRecommendation/RankingClient完成冷运行、零新推理缓存、同池对比、单方向取消续算；旧99分无覆盖的合成长期标题两方向安全重算并保留两行，第二轮初筛/精排均0。
- holdout-coverage.txt：刷新真实模型输出，6/0；回归集24候选、2对规划，输入覆盖来自实际引擎，不手工补true。这组已用于调整策略，不再是独立质量留出集。
- assembly-coverage.txt：读取新版实际输出装配，19/0；24→23、SQLite快照/来源/拆分撤销/屏蔽/看够/版本/比较池边界保持。

日志均位于 artifacts/local-ai/event-verification-plan-20261026/。仅 emulator-5560 独立 app.quietreader.discoveryprobe，运行后停止。未操作手机、普通包、提交、推送、版本号或发版。

## 尚未证明

完整输入只是必要条件，7.0联合策略仍非通用事件等价概率。已知同事件低分漏合并、实际首页Controller接纳、真实个人推荐收益、账号全文与ARM整App峰值/发热/耗电门槛不能据本专项关闭。
