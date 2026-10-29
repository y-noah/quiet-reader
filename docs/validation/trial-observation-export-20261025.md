# 本机评价观察：只读导出与独立资格拒绝

日期：2026-10-25。补齐实际评价库到开发侧观察报告的链路。尚未补齐独立留出试验，不改Android生产功能，不添加App常驻任务。

## 实现与用途

scripts/models/export_trial_observations.py仅用Python标准库和既有指标函数，mode=ro+query_only读取SQLite，在一个读事务中取得会话与标签，包括已提交WAL而排除未提交写入。使用contextlib.closing显式关闭连接，不写回原库。限定schema1、最多30轮/1800标签、单payload最多2Mi字符；验证三种排名候选一致/无重复、盲化显示与原池一致、选择属于展示项、时间及模型/画像/策略来源完整。

输出observation_schema=1、kind=recommendation_trial_observations、holdout_qualified=false、label_origin=unverified_app_choices，保留原冻结时间/版本/排名/标签与候选观测时间，计算本轮观测计数和逻辑SHA256。不导出标题、URL、正文、Cookie或兴趣库；缺失及unsure保留未知，不输出个人样本达标或增益目标结论。CLI拒绝覆盖原库及-wal/-shm文件。

运行：

    python scripts/models/export_trial_observations.py trial-snapshot.db --output observations.json

这是开发工具，当前没有在普通App接入SAF导出入口，也没有把观察数据改造成独立留出。Android实际取样探针仅允许API36/ranchu/隔离flowprobe，不能对用户普通包静默导出。

## 独立评估器保护

evaluate_recommendations.py新增明确资格检查：根级或会话holdout_qualified=false、非布尔true声明、observation kind均拒绝；仅换schema或label_origin不能消除明确的观察来源。已有旧schema1评估输入不要求新增声明，仍执行事件/时间/同池校验。工具无法防止恶意删除/伪造所有来源证据，也不能认证标签填写者，实际协议与独立人工事件核对仍必须保留。

## 验证和实际数据

- 新导出器10/0：同池计数/版本、不含敏感内容、原库字节不变、未知选择、重复/篡改候选、非法标签/时间、拒绝holdout升格、重复JSON key/错误schema、单列表/空库、WAL已提交/未提交、孤立标签/预算、CLI原库和sidecar保护。
- 首轮10项有9个清理错误：Python sqlite3连接上下文不自动close，Windows文件句柄未释放。改用closing后10/0，保留baseline摘要与最终原始测试日志；不是通过忽略清理错误取得通过。
- 既有严格评估器原8/0，增加明确观察资格拒绝后9/0。日志trial-observation-evaluator-final-20261025.txt；导出测试trial-observation-export-final-20261025.txt。
- ReleaseTrialExportProbe通过标准Android SQLite读取已验证non-debuggable隔离App的实际评价库，以同一事务复制sessions/labels到独立诊断副本，未引用生产类/改keep。原库只读、复制后原标签数量保持；两项通过，实际4轮/24标签。标签来自此前合成6候选UI流程，不是用户真实兴趣。
- 初副本0600导致adb pull拒绝，未修改App私有原库。仅API36隔离夹具的新外部诊断副本设可读后，复制/取回成功，最终trial-observation-snapshot-readable-20261025.txt为2/0。这个副本权限不作为普通App导出方案，后者应由用户选择文档位置。
- 61440字节的SQLite副本SHA256 e8ef6fffab4d080ea2ffcc9c5dd7507cb56e50555e8e4ca15ba34aa0c6554cf1，文件trial-observations-actual-20261025.db。正式导出实际格式成功，4会话/24选择，JSON trial-observations-actual-20261025.json的逻辑SHA256 925119b6b2312ab2a43917aae01f23cbd6a321b855ba20e51f69b130b5be819c。
- 该真实App格式观察JSON送严格评估CLI明确拒绝，退出2；最终原因provenance non-holdout observations cannot be evaluated as independent，日志trial-observations-strict-final-rejection-20261025.txt。未生成独立收益报告。

## 构建与边界

本轮仅开发侧工具及instrumentation。生产App SHA256仍13f947aeac8f421f47c5818510b4c598f018471ae1f9f73f4de787752c0c70f2，未变化；不是新增生产R8行为。最后恢复普通debug/store诊断构建与原签名安装，未清用户数据、提交、推送、发布或改品牌。

下一步仍需在排名前冻结学习画像/截止时间及完整学习事件集合，独立后续候选与人工事件归并、真实用户标签采集及受控输出到严格评估器。现有观察报告不能代替该协议。Find X7 Ultra内存/耗电、实际账号全文与最终正式发行等原门槛继续开放。
