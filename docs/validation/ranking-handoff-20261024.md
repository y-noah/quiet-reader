# 连续推荐进程交接 / 2026-10-24

## 直接复现

前一轮真实文字反馈回放的第二批曾143ms暂停，但未记录status，不能唯一归因。本轮新增RankingHandoffProbe，直接使用真实RankingClient、真实embedding和独立:ranking进程：第一个客户端DONE后，在同一主线程回调中close并立即创建下一客户端，没有测试sleep、失败重试或关闭内存保护。

ranking-handoff-baseline-api36.txt：4检查/2失败。两次HELLO均为PID19081，第二客户端在完成推理前关闭；第一客户端已完成，主进程存活。证明旧进程释放/退出与新绑定之间有可重复的真实竞态，不依赖把上一份缺失status的报告倒推成证据。

代码对应：RankingService在释放/解绑时retire，100ms后杀掉模型进程；新客户端可能在旧进程真正退出前绑定同一进程，之后被旧退出动作中断。不能靠延长推理预算、无限重绑或测试等待掩盖此问题。

## 修复与边界

RankingClient关闭已连接客户端时记录实际待退出的远程Binder。下一客户端首次绑定前，若该Binder仍存活，通过linkToDeath等待真实退出；收到死亡事件才启动首次绑定。没有固定等待时长、轮询、推理重发或失败自动重连。

等待可取消，关闭时移除DeathRecipient、待发请求及Handler消息。已有10秒连接超时从首次submit开始覆盖退出等待，不因死亡事件重新计时；LocalRecommendation原15秒前台预算与资源监测继续有效。已取消实例即使收到迟到onServiceConnected也不发HELLO/RUN。

本轮针对“已有连接完成后释放→立即启动后继”及等待时取消；未把尚未拿到Binder的首次绑定取消、主进程死亡或所有系统厂商行为宣称全部通过。

## 验证

正常debug应用、API36模拟器，报告位于artifacts/local-ai。

| 报告 | 检查/失败 | 范围 |
|---|---|---|
| ranking-handoff-baseline-api36.txt | 4/2 | 原生产实现可重复失败，两次绑定同进程，后继推理中断 |
| ranking-handoff-fixed-api36.txt | 5/0 | 等待旧Binder实际死亡后，两个客户端均完成真实推理且PID不同 |
| ranking-handoff-cancel-api36.txt | 7/0 | 加真实等待登记/尚未绑定断言；等待中取消后无HELLO/结果；正常后继完成 |
| ranking-handoff-repeat-api36.txt | 7/0 | 最终包再次回归，取消与真实后继推理均通过 |
| text-feedback-handoff-fixed-api36.txt | 12/0 | 相同真实快照：LESS、undo、替代画像及另100身份全部完成；第二批102/102向量、40精排、4675ms |
| semantic-handoff-fixed-api36.txt | 13/0 | 双模型/暖缓存、取消、内存暂停与真实温度通知回归 |
| recommendation-recovery-handoff-fixed-api36.txt | 11/0 | 模型执行失败有限恢复、取消、原预算、结果/cache身份回归 |

build-ranking-handoff-cancel.txt成功；最终JVM327，失败/错误/跳过均0，diff检查通过。当前无本轮活跃构建/测试；未提交、推送、发版或改品牌。

本轮证明修复了受控可重复的交接缺陷，同一真实文字回放也通过，不能证明所有历史暂停都只有此原因，更不能取代真实个人推荐质量、完整峰值/功耗、真机与精确包升级验收。整体目标未完成。
