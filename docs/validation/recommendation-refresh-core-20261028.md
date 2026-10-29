# 推荐式更新第一批核心逻辑

用户在讨论可行性后明确同意实施。需求蓝本为 recommendation-refresh-v1.md，原 personalized-hot-topics-v1.md 继续适用。没有将热榜候选来源扩张为无界搜索。

## 已实现

- FeedExposureTracker：连续可见面积阈值、1200ms驻留、独立卡片计时、部分遮挡/离开/后台中断、同展示周期去重、显式新展示周期复位、单截止时刻、时间回退失效、可见/已报集合有界。调用者必须传入有效可见面积；状态机本身没有真实Activity可见能力。
- FeedRefreshOrder：调用者先提供已做资格与兴趣分层的列表，再在每层稳定安排未冷却、已曝光、已读。未知记录不降权；过期后恢复模型顺序；不越级提升低质量新内容；全池已见不洗牌凑新内容；未来/无效时间不无限隐藏条目。明确 verifiedProgressAt 比实际见/读时间更新时可放行，但此参数的真实进展识别尚未实现，不能从换标题/缓存时刻填入。
- 无新模型、网络、后台线程、定时器或自动学习调用。

## 证据

artifacts/local-ai/reader-actions-r8-20261028/refresh-order-full-jvm.txt：完整720 tests、109 suites，0失败/错误/跳过；其中FeedExposureTrackerTest 10、FeedRefreshOrderTest 9。此前full-jvm-lint.txt在曝光模块阶段通过Debug/Release Lint；新增换批模块之后只跑完整JVM，不冒称已重新Lint。

## 未完成

尚未接入Activity的实际可见面积、生命周期定时器、持久化、已有有效阅读事件、事件成员身份迁移、质量分层传递、显式更新任务/快照提交及重要进展识别。因此该功能尚不能在App中试用，当前普通APK/手机仍c315361c，未发布。

下一步先做存储与迁移、再接真实可见区域和更新动作；必须验证曝光变化不更新profile版本、不清模型缓存，不将未点击变成LESS反馈。新老快照异步并发和当前列表稳定性继续是门槛。
