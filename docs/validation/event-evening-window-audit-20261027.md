# 晚间公开窗口去重审计 — 2026-10-27

## 来源和先冻结标签

证据artifacts/local-ai/text-source-evening-audit-20261027/。隔离API36模拟器使用生产HotSourceCollector有界采集文字来源：246候选，8/10 READY；V2EX报告访问/登录限制、BBC中文连接超时。本轮不能证明这两个来源可用，也不能仅凭超时断言适配器有错。相对此前256候选窗口有97个新标题，不是97个新独立事件。

在看到本轮模型分数前，按公开标题内容选定12个跨来源配对：same5/different4/uncertain3。5对同事件只有三个事件族：球员道歉（3对，含1个相同标题对照）、联盟道歉、球迷论坛抵制声明；相关事件不能当五个独立收益。区别行为者和动作，不把同一人物的道歉、品牌解约、另一个运动员取关混成一件事。问题句、单个事实与多事实标题、泛称“发声”的具体动作不确定时标uncertain。

labels-before-model.json与测试资产observed-event-evening-window-20261027.json完全相同。冻结SHA256 bf224f2fe63f44d6374abe2f7a8fd887f414027bf3067d9d6deb2bd03e8ac995；来源SHA256 c1f1951ea34bf444118f55c63a332bafb2061c7178326963d24e413901d23872。freeze-before-model.json明确开发者标题标签、非随机选样、不是用户兴趣标签；不得据此推断总体准确率。

## 实际双模型结果

复用EventFreshQualityProbe，只新增测试入口和冻结资产，无生产修改。实际Android LocalModelEngine计算向量、现有有界计划、双向reranker和EventGroupAssembly：same合并3/5，different合并0/4，uncertain合并0/3，plannedTasks7。quality.txt两个范围检查通过、唯一INSTRUMENTATION_CODE:-1；两个范围检查不等于全部去重需求通过，也没有把12个配对虚报成12个自动验收项。

漏项保留原标签与完整候选元数据：

- “姆巴佩道歉”/“姆巴佩就辱华言论道歉”：已进入复核，双向最低5.9660015，低于现有7.0，不合并。
- “西甲官方向中国球迷致歉”/“西甲官方：代表联盟所有俱乐部向中国球迷致以诚挚的歉意”：INSUFFICIENT，未进入计划；独立测得双向最低7.7627563，但模型高分本身不能越过证据门槛。

freeze-after-model.json逐项验证12对标签和完整左右元数据未变化，remaining-misses.json保留两漏项。未降低门槛、未把漏项改标不同事件、未因为本样本增加无依据的直接合并。后续可研究有明确行为者/受众/动作的致歉句式证据，但仍需拒绝行为者/受众/原因冲突、疑问/否认/计划及多事件混杂，并保留模型复核；尚未实施。

主机默认Python缺少onnxruntime、现有recommendation-python也未找到该模块，未为此安装新依赖或调用云模型，最终由已有Android实际模型完成。首次主机标签比对未显式UTF8，gbk读取失败；改用UTF8后完整比对成功，不隐去失败。build-driver.log构建成功，test-package.json存驱动文件摘要；不声称拉取安装包核对过其SHA。

## 清理和开放项

仅删除两个明确公开导出路径，停止隔离包并关闭AVD，cleanup.json复核命名模拟器进程0；测试临时数据库由原探针finally清理。没有手机操作、提交/推送或发布。普通候选仍3d1434ae，手机仍此前c050a471。

本轮说明保守漏合并仍存在，不能宣称去重完成。V2EX/BBC当前网络可用性、真实账号正文评论、贴吧逐条关系、ARM整体峰值与拔线能耗、长期完整预读和本人推荐收益仍开放。
