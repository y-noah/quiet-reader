# 本地推荐运行故障恢复 / 2026-10-24

## 生产变化

LocalRecommendation以前在RankingService.ERROR时直接结束。现在仅针对模型不可用/执行失败，在原前台计算窗口中检查一次APK认可的兼容前版。ModelFiles对前版ready、文件长度及全部SHA256做完整校验，只允许首选版失败后转前版，不允许同版本自退或前版再转回首选版。取消不能触发校验/回退；成功选择后首选版暂缓10分钟，手动修复成功仍清除暂缓。

前版选择前不丢弃现有结果。完成校验后先检查原时限与系统限制；只有还允许整理才在串行SQLite队列清空本轮派生状态并按新revision重读缓存。新窗口不重置15秒截止时间，限制回调、内存检查、关闭、进程退出仍有效。旧向量/精排缓存不迁入新revision，ITEM入库显式捕获取得revision，返回消息仍检查revision及完整请求身份。worker切版本先关闭旧engine；不在回退检查时释放后立即重绑进程。

这只补齐错误恢复机制。当前APK兼容清单仍为空：没有另一套正式模型可宣称已通过升级兼容认证。当前用户首次安装没有前版时，故障仍显示可解释提示与现有可读内容。

## 本轮证据

设备为项目API36模拟器emulator-5560；应用是隔离ID `app.quietreader.discoveryprobe`，不是手机真机。使用带模型的debug store runner，不发布。以下文件在 `artifacts/local-ai/`：

| 报告 | 结果 | 实际覆盖 |
|---|---|---|
| model-rollback-execution-api36.txt | 15/0 | 原安装失败选择、暂缓、allowlist拒绝、运行失败前版选择、禁止自退/循环、无前版、同长度损坏拒绝、取消 |
| recommendation-recovery-api36.txt | 9/0 | 首轮恢复链路，后增加精排阶段覆盖 |
| recommendation-recovery-final-api36.txt | 11/0 | embedding和reranker请求失败后的真实双模型重算、版本缓存隔离、一次发布/一次回退、无前版保留部分结果、校验中取消、原时限过期 |
| semantic-execution-recovery-api36.txt | 13/0 | 正常双模型、缓存复用、受控内存暂停、热信号中断/释放、取消；在最终仅新增精排测试前运行，之后生产代码未变化 |
| model-recovery-execution-fallback-api36.txt | 3/0 | 实际修改本机tokenizer同长度字节，真实worker自动修复后执行embedding，字节恢复 |
| feed-store-execution-recovery-api36.txt | 148/0 | 完整本地存储回归，来源会话保留 |

最终构建 `build-execution-recovery-final.txt` 成功；JVM311，failures/errors/skipped均0。diff --check通过。本轮上述测试均通过；旧文档中的历史失败记录不受影响。

## 诊断的明确边界

RecommendationRecoveryProbe的ModelAccess仅在androidTest替换。受控首选清单使用额外JSON空白产生不被APK认可的revision，由真实RankingService在allowlist检查时拒绝，再注入当前真实APK模型作为替代。恢复后实际执行embedding/reranker，使用临时SQLite；缓存向量隔离包含一个明确合成的旧版向量。精排故障场景则把真实模型所得向量放入受控失败revision的缓存，确保第一个失败请求来自reranker阶段。截止时间负例通过反射提前截止；取消负例在校验依赖处等待并确认中断。

因此11/0证明真实Messenger/SQLite/推理的恢复编排，但不证明真实native推理中途崩溃、另一套正式模型的结果、个人推荐质量或真实跨APK升级。ModelRollbackProbe同样通过受控manifest直接调用生产选择器，不是两个正式APK的升级测试。正式资产未为测试修改，未认可诊断revision。原模型字节修复3/0是另外的真实native故障/修复证据，不把两种证据混称一个端到端正式升级试验。

仍待：真实跨APK兼容前版/数据保留升级、规则回滚、真实用户兴趣留出评估、FindX7Ultra全进程1GB/温度/耗电，以及全来源统一阅读的剩余适配验收。整体开发目标未完成。
