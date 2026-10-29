# 筛选保留符合条件的默认阅读来源

日期：2026-10-25。属于平台偏好P03与分类/来源视图交互的修复，不修改模型或偏好权重。

## 已复现的问题

PlatformPreference在合并事件中选择偏好平台为Row.candidate，保留原entries顺序。SourceFilter.apply对任何非all筛选都重新遍历entries并选择第一个符合项，忽略已选candidate。因此同一合并话题中澎湃排在entries第一、V2EX因偏好成为主入口且二者均为科技时，点科技会无故切回澎湃。

新增SourceFilterTest.categoryKeepsTheAlreadyPreferredEligibleEntry，使用真实PlatformPreference与SourceFilter组合；修复前429项中1项失败，日志artifacts/local-ai/filter-preferred-source-baseline-20261025.txt。另一个新增测试保护分类/来源必须同时匹配同一入口、不能被偏好绕过，修复前该边界已通过。

## 修复方式

每一行优先查找与原candidate身份和文本版本相同、且同时满足来源与分类的原组成员。SQLite读回时candidate与entries是分别反序列化的对象，不能用引用相等来判断；用已有id/textHash确认，实际选择的仍是entries中的合法成员。

原入口不符合筛选条件时，再按原entries顺序选择符合项；都不符合则不显示。不把偏好重新应用一遍，不计算新的平台分数，不改行顺序、原始组成员、推荐理由或底层快照。all仍直接保留完整冻结顺序。明确指定来源/分类始终优先于保留旧入口。

## 验证证据

- JVM429 tests / 0 failures / 0 errors / 0 skipped；完整离线debug/store构建成功，正常诊断包保留原签名覆盖安装API36现有项目模拟器。
- 新增grouped-filter-preference诊断，合成相同标题/实质摘要/发布时间的双来源科技话题，生产EventGroups真实合并；另一条独立科技候选保留。隔离SQLite仅种一条显式V2EX平台MORE，不是用户个人偏好。
- 实际DiscoveryActivity从真实序列化快照选择V2EX，即使entries第一为澎湃；标准无障碍ACTION_CLICK点击科技分类后仍为V2EX，事件顺序及备选入口保留。
- 通过生产controller.filterSource切到THEPAPER（未伪称点击来源菜单），主入口切成符合条件的澎湃；恢复all仍在TECH分类且重选原偏好V2EX。结束/重开Activity后TECH和V2EX入口从SQLite恢复；反馈条数仍1，没有重复学习。
- 上述7/0，日志artifacts/local-ai/grouped-filter-preference-20261025-api36.txt。
- 针对公共筛选函数变更，回归已有固定公开澎湃样本的五类实际按钮、各分类数量/相对顺序、视频排除和重开恢复，18/0；日志artifacts/local-ai/filter-preferred-category-regression-20261025-api36.txt。

## 边界

合并样本为合成，不证明真实平台事件归并覆盖率或跨站角度判断正确。入口检查未打开真实账号文章，不替代知乎全文/登录验收；未覆盖所有source选择菜单、并发来源变更或完整TalkBack。个人长期推荐质量、真机RAM/功耗、精确正式R8与升级交付等原门槛继续未完成。本轮未提交、推送、发版、改名或更换logo。
