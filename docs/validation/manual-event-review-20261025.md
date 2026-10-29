# 人工事件核对到严格指标（2026-10-25）

本次新增开发侧离线核对工具 scripts/models/review_trial_evaluation.py。它接收前瞻观察导出及单独的完整人工核对表，验证绑定关系后调用既有严格指标函数。不是自动给观察数据改holdout标志，不产生可被误用为已认证用户数据的中间输入。原观察信封继续被 evaluate_recommendations.py 拒绝。

## 核对与计算边界

1. 从指定protocol ID生成空核对表；不自动填写事件映射、评价来源或人工声明。只列候选事件/所有归组成员/准备时全部已知旧身份及可恢复学习身份，不输出标题、正文、URL、Cookie或原始偏好样本。
2. 核对者必须在本地对照原冻结候选内容及完整、不变的学习历史，给所有身份填写同一套canonical event key。同一事件换URL/标题/平台必须同key；一个冻结分组的所有成员必须同key。旧学习目标无法恢复身份时，人工在additional_prior_events补充事件与截止前的已知时间。空补充表只是人工声明，不能自动证明历史完整。
3. 必须声明完整历史核对、跨平台事件核对、实际选择来源、未使用截止后的学习记录。reviewer_id使用有界匿名标识，label_origin只能明确为user/developer/synthetic。空表、少映射、重复/未知身份、错误scope、不完整声明均拒绝。
4. 核对表绑定观察logical SHA、指定协议、完整模型/画像/策略/来源/安装/截止基线，另外绑定本地学习核对证据文件的实际字节SHA。CLI必须提供该文件并实际只读计算hash；文件内容不会出现在报告中。空/超64MiB证据文件拒绝，输入JSON限32MiB。文件hash证明字节绑定，不证明文件包含完整历史或核对者认真核对，真实性仍依赖人工。
5. 每个选中会话重新验证三顺序同池、模型/画像签名及策略、时间、成员、有效标签范围。不信任导出的观测计数，也不把未标/不确定当负例。拒绝同池多个条目映射同事件、一个分组映射不同事件、候选与旧事件重叠，以及不同前瞻会话重复人工事件。不能静默删除难例、重排或把重复事件拆key凑样本；发现污染应重新采样。
6. 严格评估器新增可选prior_events排除集。它与training_events分开：准备时已有但未必训练过的事件可以保守排除，不能伪造labeled_at_ms。内部指标投影把全部已知旧身份及人工补充作为prior_events，known_at_ms采用准备截止这一“已在快照中可知”的时间；不会据此声称没有训练历史或重建了原训练标签。
7. 输出kind=reviewer_declared_trial_report、qualification=human_declared_not_authenticated，带观察/核对/协议/本地证据摘要及条件指标。个人样本门槛只按声明的实际来源与既有5会话/100不同双模型前排条件计算；synthetic/developer永远不能补齐个人门槛。即使人工声明user且门槛满足，也不证明身份真实性、稳定收益或资源达标。没有标题/正文/账号字段或原始学习文本。

CLI不覆盖现有报告、任何输入及其SQLite sidecar。生成模板与计算命令分开；输出使用独占创建，防止核对轨迹被覆盖。无模型、网络或App学习反馈写入。

## 开发侧操作

先用export_trial_observations.py只读导出评价库快照；选择一个已有前瞻protocol ID。运行：

    python scripts/models/review_trial_evaluation.py observations.json --template PROTOCOL_ID --output review-template.json

在本地原始候选和完整历史上逐项核对，另存完成的review.json及本地核对证据文件。不得把模板空值批量自动补成“人工已核对”来制造用户效果；合成回归必须明确synthetic。完成后：

    python scripts/models/review_trial_evaluation.py observations.json --review review.json --learning-evidence local-learning-audit.json --output new-reviewed-report.json

此为开发侧命令行链路，尚无面向普通用户的App内人工事件审查/SAF导出界面。没有实际账号数据或真实用户核对结果时，不把本文件当成A07收益通过证据。

## 测试与实际Android格式

- 人工核对12项/0失败：只读SQLite导出→模板→完整核对→严格指标，遗漏/伪声明/证据不符/旧事件/跨会话重复/归组冲突/元数据变化拒绝，不确定/未标保留，五组合成100前排也不能达个人门槛，CLI拒绝覆盖和错误证据。日志 artifacts/local-ai/manual-event-review-final-tests-20261025.txt。
- 严格评估10项/0失败，包含新增prior_events泄漏与时间边界；观察导出14项/0失败。日志 manual-event-review-regression-20261025.txt。
- Android API36显式prospective-review-export诊断模式18项/0失败，只创建临时合成FeedStore/评价库；导出单轮合成前瞻会话和单条合成学习证据，不读取普通数据库或账号。只给新副本设置读取权限。日志 manual-event-review-android-final-20261025.txt。
- 初次实际链路发现探针将profile_signature写成占位字符串，核对工具按规则exit2拒绝；修正探针为真实SemanticOrder.Profile.signature，未放松验证。拒绝证据 manual-event-review-placeholder-rejection-20261025.txt，旧失败副本保留。
- 修正后实际Android SQLite→观察JSON→空模板→明确synthetic核对→报告成功；空模板仍exit2，原观察交给严格评估器仍exit2，源SQLite SHA前后相同。摘要 manual-event-review-actual-pipeline-20261025.json。

最终合成副本后缀aaafe4c6-5e5c-4aaa-9435-9ba9e8371e41：SQLite24576字节，SHA256=4f1d5ede84ba082ea122e4334a4115a4ca34c7a3a955e82991a38ebd447594c4；证据708字节，SHA256=17fd4b65c031cbfcecae1f6cf253f75ddea7db2eb163bcf8dab8abd89b07e97b。观察logical SHA=2281041e60f1f53371dbd360569dea977645eb7e83a02cd91ba01889cb18746f。实际报告label_origin=synthetic、personal_sample_floor_met=false，不能当个人推荐收益报告。

## 仍未完成

真实本人标签、真实完整历史审查和跨平台人工事件判定仍不存在；工具不能认证人工声明，不能恢复未知的被淘汰内容，也不能证明模型排序更合用户兴趣。Find X7 Ultra全进程RAM/耗电/发热、真实权限来源、最终普通包升级/R8与完整辅助功能等原审计缺口仍开放。本轮Android生产源码未修改，只增诊断导出与测试；无提交、推送、发布、默认版本或品牌变更。
