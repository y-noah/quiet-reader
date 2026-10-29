# 曝光历史存储与首页接线

仅本地API36隔离Debug验证；没有访问手机、更新普通候选或发布。推荐式更新尚未完整可用。

## 实现

FeedStore schema7→8新增feed_exposure表与时间索引，原表不重建。单批最多64个事件，只从指定的不可变snapshot_rows解析其已接受事件与成员，未知快照/条目不记；不按相似标题推断其他事件。精确成员保留代表平台/分组更换前的曝光事实。

独立保存shown_at/read_at，不写MORE/LESS，不修改profile/sources版本，不清向量/精排缓存。recordRead在既有文档/会话和有效阅读门控之后增加已读事实；原有阅读弱参考仍按开关独立产生READ反馈，不能将本表的版本不变误称为整个阅读行为不会产生偏好。QUICK_EXIT不记有效读完。重置偏好/清派生缓存保留已见事实；系统清应用数据一并删除数据库。

时间、批量写入及清理是事务；旧回调不回退新时间，远未来输入拒绝，回退后的未来列归零并保留仍有效的其他列。按7天保留、最多10000个事件/成员键清理；索引匹配淘汰顺序；没有周期清理任务。曝光写入失败保守视为未见，不重试、不打断采集/模型工作。

DiscoveryActivity从真实绑定行的eventId tag和有效可见矩形取面积，扣除顶部导航遮挡，使用已有50%/1200ms状态机。只有实际存在于当前feed身份集合内的绑定行参与，不把适配器行数或预缓存当作曝光。滑动/布局/焦点变化采样，仅一个到期回调；成熟后没有定时截止。设置页、Activity pause、失焦、销毁取消待计时；同展示周期不重复计入。明确更新才开启新周期，加载中单纯回顶部不重置周期。

## 证据

artifacts/local-ai/exposure-history-20261028/：

- final-exposure-history.txt：18/0/code-1，合成schema7→8迁移保留列表/反馈/模型缓存、成员身份继承、未知快照拒绝、偏好版本独立、事务中途失败回滚、超批量拒绝、未来/倒退时间、7天清理/10000键上限、关闭重开。
- final-exposure-viewport.txt、repeat-exposure-viewport.txt：各7/0/code-1，真实绑定卡片稳定驻留入库、未上屏条目不计、快速切换视口不计、设置/实际Activity pause中断、成熟后dueAt=-1、不产生兴趣反馈。滚动使用ListView定位来控制视口，非真实手势/帧率/耗电证据。
- final-store-regression.txt：190/0/code-1，包含原有v1/v2迁移、来源扩展、阅读弱参考与偏好保留。相应合成旧版夹具在降schema前也删除新增表，避免把带新表的假旧库当成真实旧schema。
- final-update-dot-top.txt：13/0/code-1；final-explicit-feed-cache-action.txt：6/0/code-1，导航和有限缓存额度保持有效。
- build-final.txt、jvm-summary.json：720 tests /109 suites，0失败/错误/跳过；lint-final.txt：Debug/Release Lint成功。
- 最终隔离Debug app SHA256 5e2a31ef9f9031a8d6cba20a5eaf5bfb458eed5fb4d63f6c738265e15c715887。

viewport-first.txt保留7项1失败：快速视口切换的组合断言未过，未分别输出两半条件。旧测试只等待目标行存在，没有确认列表从40扩展后目标行充分可见；仅加强测试定位/充分可见前置条件并分开记录后，viewport-diagnostic.txt及最终两轮通过。未修改产品阈值，不将第一轮唯一原因认定为目标行部分可见。

## 尚待完成

R8下的新表/实际可见区域验证、保留普通schema7包的真实升级、极端布局/字体/遮挡和拔线能耗尚待验证。手机仍c315361c/schema7。本轮不是普通包升级证据。

曝光事实已采集，但FeedRefreshOrder仍未接入显式更新的快照提交。下一步需要从模型结果传递明确兴趣质量分层，保留不同层边界；用曝光表调整同层顺序并验证异步更新/旧快照竞态，随后完成重要进展证据判定。不能从解释性文案猜质量层，也不能把标题改动当作进展。
