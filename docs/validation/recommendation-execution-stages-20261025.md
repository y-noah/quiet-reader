# 独立计算阶段验证（2026-10-25）

目的：耗电对照必须真正分别运行无模型/只初筛/双模型，不能从一次双模型输出的三种排序冒充三种能耗工作负载。本轮只补计算执行路径，不声明30分钟阅读脚本或能耗验收完成。

## 实现

LocalRecommendation增加package-private Stages及测量构造器。所有既有构造器仍默认DUAL，生产UI无选择入口，用户批准的默认双模型方案未更换。

- NONE：直接计算现有基础/平台及精确少看保护顺序，不调用ModelFiles.prepare、不创建RankingClient、不写向量或精排缓存。
- EMBEDDING：使用同一真实向量加载/批次/缓存/取消路径，向量齐后完成，不进入rerank计划或缓存查询。暖缓存仍做正常模型资源准备检查，但不提交两种推理。
- DUAL：既有生产路径不变；测量测试特意使用旧构造器验证默认仍实际执行两阶段。
- requestComparison仅允许DUAL，避免误把未执行精排的计算作为三顺序对比。

## 现场验证

scripts/models/build-probe.ps1 -Offline -Target store构建成功，JVM466/0。API36模拟器通过签名诊断包运行FeedStoreInstrumentation mode=recommendation-stages。RecommendationStagesProbe使用每阶段独立随机SQLite库，三条合成候选/两条开发者反馈，真实本地模型/Messenger/缓存；结束关闭并删除本轮数据库。仅此样本，不是个人推荐收益。

- artifacts/local-ai/recommendation-stages-20261025.txt：12/2。NONE、EMBEDDING及暖缓存通过，首轮DUAL在一个计算窗口未完整，原记录保留。此轮没有保存状态文案，不能精确归因；不扩大生产15秒窗口绕过结果。
- recommendation-stages-continuation-20261025.txt：12/0。新增状态文案和至多四个显式窗口的测试续算支持，但此轮DUAL首窗口已完成，因此不能称已验证跨窗口续算。NONE prepare0/submissions0/向量0/精排0、无模型缓存；EMBEDDING prepare1/submissions1/向量3/精排0；暖EMBEDDING prepare1/submissions0；默认DUAL prepare1/submissions2/向量3/精排2，已保存真实精排logits。
- 三阶段均保留完整候选集合，精确少看保护仍有效。阶段测试12项全部通过，原有JVM466项全部通过。

## 后续边界

这是debug诊断构造器级执行验证，不是当前精确R8包、30分钟/100候选/相同页面回放/登录页/媒体脚本，也没有真实手机非充电能量和热条件结果。R8长测还需可审计的隔离测量入口；不能把旧06C6包当成新增源码已压缩验证。下一步把阶段选择接入固定100候选阅读回放，固定冷暖缓存、内容和交互时序，接通设备端采样及通知取消，再做重复长测。A08保持开放；未连接手机，未提交/推送/发布。
