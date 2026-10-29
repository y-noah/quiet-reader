# 内容少看、平台偏好与撤销的跨进程验证

日期：2026-10-25。Android16/API36，现有项目模拟器emulator-5560。合成文字候选、隔离SQLite库、真实DiscoveryActivity和DiscoveryController。未访问真实平台或变更个人偏好。

## 场景与实现

新增FeedbackRestartProbe，FeedStoreInstrumentation接受feedback-restart-write/read/final三个mode及32位十六进制probeId。数据库仅允许test-ui-feedback-restart-{id}.db；写入阶段拒绝已有库/标记，后续拒绝缺失库/标记，防止静默重新种数据掩盖持久化故障。正常包使用既有原签名覆盖安装诊断构建。

写入阶段：三个实际缓存文字卡片中V2EX A排第一，另有IT之家B及同平台独立讨论C。通过生产反馈入口对A记录LESS，点击实际“查看更新”的标准无障碍ACTION_CLICK接受新快照；再通过平台反馈入口对V2EX记录MORE，同样显式接受。确认A仍排在其他两条之后、全量候选仍保留，少看不等于屏蔽整个平台。

外部am force-stop结束App进程；第二次instrumentation验证PID不同，直接重开同一数据库与首页。内容LESS/平台MORE均存在；缓存列表仍将A放在最后。重新提交两个来源的实际SQLite快照（时间变化、同内容）并通过原排序入口重建，保护仍有效。撤销LESS后等待生产undo立即应用新快照，A回到平台偏好下的原位置，平台MORE保留。

再次外部force-stop、第三次instrumentation：确认又是不同PID，撤销结果仍保留。最后仅清理本诊断命名的合成库和PID标记。

模型调度通过生产interacting(true)滚动策略暂停；autoRefresh=false防止网络/预读。每个状态都直接检查没有活动LocalRecommendation、测试库embeddings及reranks总行数为0。因此验证的是模型不工作时的已知明确少看保护与持久化，不是两模型排序准确率或真实滚动动作验收。

## 原始结果

位于artifacts/local-ai/：

| 报告 | 检查/失败 | 作用 |
| --- | --- | --- |
| feedback-restart-write-20261025-api36.txt | 10/0 | 记录LESS与平台MORE、保护、缓存为空 |
| feedback-restart-read-20261025-api36.txt | 15/0 | 实际进程重启、来源快照更新、撤销且保留平台偏好 |
| feedback-restart-final-20261025-api36.txt | 6/0 | 再次进程重启仍保持撤销 |

完整成功链31/0，实际PID27949→28021→28087；过程没有清数据/卸载或重新种库。离线构建成功，既有425项JVM测试仍为通过（本轮仅修改androidTest，Gradle单元测试为UP-TO-DATE）。

首轮write 10/0，read为Process crashed，分别保存在feedback-restart-first-{write,read}-20261025-api36.txt。崩溃来自诊断错误：把生产undo的立即应用路径误当成待显式接收，读到瞬时pending后试图点击不可见更新按钮，并在runOnMainSync回调抛异常。修正诊断等待snapshot切换/pending=0，并把更新按钮缺失异常移到测试线程；未修改生产反馈、撤销或排序逻辑。

首轮崩溃后旧合成库没有重种；修正驱动后独立运行final，6/0，PID27523→28220，证明该库已提交的撤销也保留，随后清理旧诊断库。日志feedback-restart-first-crash-recovery-20261025-api36.txt。此额外检查不混入完整成功链31项。

## 边界

实际热门内容推荐质量、非精确文本负反馈的模型泛化、标题/摘要实质改版后的偏好迁移、按时间模式、所有UI反馈菜单、真实省电/高温状态未由本轮覆盖。当前检验的是明确同内容样本在标准候选顺序中的保护，不承诺所有类似话题永不出现；LESS仍为降序，BLOCK/ENOUGH另有独立范围。用户个人长期效果、Find X7 Ultra内存/耗电和真实知乎登录正文仍待验收。未提交、推送、发版或修改品牌。
