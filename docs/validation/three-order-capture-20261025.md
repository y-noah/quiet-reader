# 真实本地排序的三顺序技术记录

日期：2026-10-25。仅扩展androidTest诊断，不修改Android生产算法、用户画像或界面。

## 数据与方法

扩展TextRankingReplayProbe（mode=text-ranking-replay），读取此前live-text-sources在隔离库采集的公开text-hot-replay.json。现场文件capturedAt=1792867966176，原始285候选，当前资格/分组合计282事件，原有回放固定前100。不是全池效果试验。

使用原有明确标注的开发测试画像：软件开发、开源工具、手机电脑实际使用为正例，明星恋情绯闻/粉丝争吵为负例。这是诊断输入，不代表用户偏好；数据库真实feedback表仍0行。

每轮实际LocalRecommendation完成后，从已关闭、无继续写入的该对象读取实际revision、最终vectors/logits和资源计时；在同一串行存储worker上用生产SemanticOrder还原仅初筛的结果，传空精排scores并同样应用一次平台先验。不再运行一次模型，不采用另外画像/另外版本的向量。双模型结果直接来自该轮真实complete回调。中性结果同样使用该候选集和画像的无向量保护/平台先验路径。

读取内部状态的反射仅存在诊断代码；没有生产测试钩子、保留规则或用户入口。初筛顺序为同一轮最终向量的生产策略回放，不是独立B模式的CPU/能耗测量。

## 导出内容

完成、向量齐备、有限精排值存在、三份顺序的完整事件集合和数量一致时，原子替换App外部私有目录text-ranking-capture.json。记录：

- 100个事件身份、实际主入口/备选来源及公开标题/摘要/观测时间。
- neutral/embedding/dual三个完整身份顺序。
- 实际模型manifest revision、画像signature、SemanticOrder/PlatformPreference/metadata/tokenizer缓存版本。
- 原公开corpus重新序列化JSON的UTF-8 SHA256（字段名source_corpus_json_sha256，不能当原文件逐字节hash）。
- 本轮定义开发画像的实际时间、原候选采集时间、导出时间与两轮缓存数、新精排数、模型报告的CPU/加载信息。

顶层capture_schema=1、label_origin=developer、holdout_qualified=false、labels为空；保留未合格原因。保存的候选先于本轮开发画像定义，事件身份也没有人工核对，不能倒填时间让它变成独立个人留出。该技术记录不是evaluate_recommendations的schema_version=1输入，实测直接传给评估器会被拒绝，不能只改标签来源就宣称个人效果达标。

## 本轮实际证据

artifacts/local-ai/text-ranking-three-orders-20261025-api36.txt：6/0。

- 第一轮实际101/101向量，34个新精排结果，11307ms，完整语义结果。
- 第二轮101/101缓存向量，0个新精排结果，实际有限精排scores仍34，327ms；两轮最终顺序一致。
- 相同100事件、初筛与双模型32个位置不同。这仅说明精排参与并改变位置，不是P@20改善或人类价值判断。
- 导出文件artifacts/local-ai/text-ranking-capture-20261025.json，148583字节，SHA256 b839384e36c365c958bbb2d5ed38978a341f971e16c05dc532a74e3c0850f37c。
- 本机取回后再次断言三模式各100个唯一身份且集合相同、两轮版本一致/完整、无标签及开发来源、不合格标记；摘要artifacts/local-ai/text-ranking-capture-verified-20261025.json，quality_claim=null。

公开候选文件来源与实际模型版本保留，未读取普通用户数据库/反馈、Cookie或登录凭据。耗时为模拟器实际回放，不能推断Find X7 Ultra性能或电池增量；reported_model_work仍只包括模型已报告部分。

## 后续门槛

本轮补齐了技术排序记录，尚未提供真实用户冻结画像/新候选的独立留出采集入口、人工跨平台事件核对、盲化用户标签或漏选抽检。需要正确采集新数据，再进入离线评估；现有开发画像和无标签记录不能充当个人样本。真实账号阅读、真机资源、完整TalkBack、最终R8/普通升级交付和品牌确认等原门槛仍保留。未提交、推送、发版、改名或更换logo。
