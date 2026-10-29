# 合并卡片屏蔽与看够的作用域

## 生产缺陷

EventGroups以标题、足够长摘要及来源发布时间所在日建立保守合并编号；文本更新会改变该编号。此前DiscoveryController对BLOCK只记录row.eventId，对ENOUGH只记录eventId@textHash。合并卡片屏蔽后，已知来源同一个URL更新元数据即可换合并编号，绕过永久屏蔽；单来源候选原有基于candidate.id的永久屏蔽没有此问题。

## 修复

新增EventTargets.capture，在单条反馈的target里保留event-targets-v1结构：合并编号和当时已知的各来源条目身份。BLOCK捕获身份，ENOUGH捕获各身份及内容hash；普通未归组单条保留原target格式。CandidateOrder与EventGroups展开同一作用域，来源切换、元数据更新或分组变化不能让已捕获BLOCK成员逃逸；ENOUGH只抑制捕获版本，新版本允许再出现。

仍是一条SQLite反馈、一次profile变更、一次历史撤销，sample仍是可读内容，不把内部结构显示给用户或输入语义模型。无需重建表或删除旧数据；旧字符串target继续按原规则解释。旧记录没有捕获过的成员身份不能凭空补回，不声称可恢复丢失信息。

多来源版本目标可能超过旧通用512字符上限。只对scope=event、action=BLOCK/ENOUGH且结构及每项身份均有效的目标允许较长内容；上限1000000字符、5001项（对应最多5000候选及组编号）。其他scope/action或畸形结构继续受原上限约束。错误结构不扩展成其他条目或来源的屏蔽。

保守边界：只覆盖已观察身份和同一精确合并编号，不把共同关键词、相似标题或未知未来报道推断为已屏蔽事件。独立角度和其他条目保留；热度不叠加，硬过滤仍发生在模型输入之前。

## 证据

API36正常debug诊断包，随机数据库、明确合成五来源同文及独立角度；无真实平台请求、登录或用户偏好变更。

| artifacts/local-ai报告 | 结果 | 范围 |
|---|---|---|
| grouped-feedback-baseline-api36.txt | 9/2 | 实际Controller BLOCK后，已知URL更新逃逸屏蔽，重开SQLite仍逃逸；原版本化ENOUGH与撤销通过 |
| grouped-feedback-fixed-api36.txt | 10/0 | 捕获身份/版本后通过，四来源ENOUGH的完整target实际超过512并成功持久化 |
| grouped-feedback-verified-api36.txt | 11/0 | 加入长target例外只适用于合法结构/作用域/动作的拒绝验证，没有产生额外反馈 |
| feed-store-grouped-feedback-api36.txt | 158/0 | 完整存储/采集/旧升级及来源扩展回归 |
| discovery-ui-grouped-feedback-api36.txt | 77/0 | 原卡片、来源、分组、冻结列表、重建、预读/交接、缓存失效及资源限制交互回归 |

GroupedFeedbackProbe通过真实DiscoveryController.feedback、实际prepare/apply、SQLite刷新/重开及undo验证作用域；没有用手工删除反馈模拟撤销。仅直接调用controller动作，不把该探针称为实际点击所有原生菜单的验证。独立的发现页回归保留原有菜单覆盖。

新增4项EventTargetsTest覆盖已知URL更新、新的完全同文来源、独立角度、版本化ENOUGH、单成员但拥有合并编号的情况、旧target与畸形结构。最终build-grouped-feedback-final.txt成功，JVM345失败/错误/跳过0，diff检查通过；本轮无剩余活跃测试/构建。

## 限制

这证明反馈作用域一致性，不证明自然语言事件聚类质量、真实个人推荐效果、所有来源登录全文/讨论、FindX7Ultra资源功耗或精确release升级已完成。旧反馈只能保留已有信息。未提交、推送、发版或改品牌，整体目标未完成。
