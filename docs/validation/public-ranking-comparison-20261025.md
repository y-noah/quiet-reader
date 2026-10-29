# 同轮三顺序的正式接口

日期：2026-10-25。承接 three-order-capture-20261025.md，消除技术导出对模型内部字段反射的依赖；尚未接通用户三模式盲化采集界面。

## 实现与边界

LocalRecommendation 增加显式 requestComparison()，只能在 start 前请求；未请求时不进行额外中性顺序和初筛顺序评分，不输出对比记录。start 本身防止重复启动。

计算完成时在原串行存储线程复用同轮向量和精排结果，产生 RecommendationComparison。三种完整顺序共用画像和平台偏好：基础顺序保留明确负反馈等生产保障；初筛使用实际完整向量、空精排分数；双模型使用实际交付结果。没有额外模型调用。只保留不可修改的行列表副本和版本信息，不向调用方暴露向量或logits映射。

仅 fullyComputed、具备语义结果、向量数量完整且非空候选时发出 comparison 回调，随后调用既有 complete。暂停/取消不产生可比较记录；此接口不是持久化、独立事件留出或相关性标签。

RecommendationComparison 检查三顺序相同的唯一非空事件集合、模型revision及画像signature；记录generation、来源版本、画像版本和有限精排分数数量。精排分数为0并不必然是失败（没有可精排组时可以正常完成），不凭分数数量伪造收益。

## 实测

构建：build-probe.ps1 -Offline -Target store，原签名覆盖安装至API36/emulator-5560。单元测试432/0，其中新增3项检查不可修改副本、事件集合缺失/重复/不一致拒绝、缺失版本信息拒绝。

TextRankingReplayProbe 已改为正式 comparison 回调，不再反射读取vectors/logits/revision。既有公开固定池285条、有效282条，本次取相同100事件；画像仍为开发者声明的技术兴趣/娱乐负偏好，绝非用户画像。

- 首轮接口检查7/0。
- 扩展边界检查9/0：冷轮101/101向量、34新精排、10087ms；暖轮0新精排、396ms，实际输出顺序相同。
- 同轮接口保留画像signature、generation/source/profile版本及实际交付的精确双模型顺序。
- 未请求对比的正常计算不发comparison，且完整顺序不变。
- 强制内存保护且存在缺失向量时，计算不完整、不发comparison、精排为0。
- 开发者画像未写入用户反馈。

日志：artifacts/local-ai/public-three-order-20261025-064426.txt、public-three-order-boundaries-20261025-064517.txt。

产物：artifacts/local-ai/public-three-orders-20261025.json，148583字节，SHA256 2333dbf5cacb0648731e4aaa03dd7e7239810601c0356d8238d656a4c5a0480a。主机复核三顺序各100条、各100唯一事件、相对候选集合差异0；labels为空、holdout_qualified=false。

## 未通过的整体门槛

正式接口尚未连接Controller和评价库的三模式协议。后续仍需同池前排并集去重盲化、独立事件/时间切分、用户真实标签及离线评估导出；不能把单列表评价或本次开发者技术回放算作个人效果证据。未验证Find X7 Ultra内存/耗电、真实账号阅读或最终发布包。未提交、推送、发版或修改品牌。
