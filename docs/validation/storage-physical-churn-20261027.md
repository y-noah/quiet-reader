# 实际分配超过2GiB后的缓存轮换 — 2026-10-27

本轮仅扩展StorageChurnProbe和测试路由，普通候选仍3d1434ae，手机仍此前c050a471。没有生产修改、手机操作、推送或发布。先前storage-churn-20261027.md是稀疏逻辑边界；本轮补Android模拟器文件系统的实际分配证据，仍不关闭完整长期R13。

## 工作负载和保护

storage-churn-physical仅允许app.quietreader.discoveryprobe且HARDWARE=ranchu。随机隔离缓存目录中写入2147487744字节，每次复用1MiB确定性字节缓冲，实际顺序写满并fsync；不调用setLength代替写入。写前要求目标大小之外至少512MiB可用空间；每次写前检查中断/120秒截止，fsync自身不具有硬超时保证。保持原稀疏模式独立可用。

同一隔离目录中运行40次生产ArticleDiskCache写入/重开及30次ImageDiskCache写入，缩小LRU预算以覆盖淘汰；检查过期/partial清理、低空间拒绝、清派生缓存保留真实临时SQLite反馈及profile、合成网页/偏好标记和大文件。finally递归清理严格约束在已解析的隔离目录，删除独立测试数据库/偏好。

## 实际结果

证据artifacts/local-ai/storage-physical-churn-20261027/。构建成功；results.txt13项/0失败，唯一INSTRUMENTATION_CODE:-1。st_blocks*512=2147495936字节，覆盖完整逻辑文件2147487744字节，明确超过2GiB实际Android文件系统分配。verification-summary.json由原始日志严格解析终态和大小，没有将70次写入另算70个检查。

缓存继续准入、新正文/图片可读、旧条目淘汰、反馈/版本及合成保护标记保持、最终隔离目录/数据库不存在均通过。/data可用空间测试前5471060KiB，清理后5473628KiB；这是分区端点读数，可能含系统其他活动，不称每个字节的可用空间恢复量。大文件没有保留在AVD中。

此物理分配为Android模拟器guest文件系统证据，不承诺宿主qcow2镜像立即缩小，不代表真实Find X7 Ultra闪存/热状态/耐久性或长达数日的自然积累。仅测试生产派生缓存存取接口，没有执行完整DiscoveryController/ReaderPrefetch在线获取，也没有用合成Cookies标记冒充真实登录。仍需长期完整预读、真实账号、ARM全App峰值、拔线耗电和本人推荐质量。

App及驱动Debug文件摘要见debug-packages.json；没有拉取安装包进行哈希匹配的额外主张。结束停止指定隔离包、emu kill并复核cleanup.json命名模拟器进程0。最终进程查询首次自动权限审核超时，允许的一次重试成功；不是操作被判定不安全，也没有据此询问手机权限。无需重建普通候选。

## 同轮阅读反馈核查

检查ReadingEngagement、ReadingInterestRecorder、FeedStore和PlatformPreference及现有ReadInterestUiProbe/ReadInterestProbe后，没有新增生产变更：单纯点击不记正反馈；实际显示/触控滚动/停留为正证据；短读需足量正文、显式返回且无后台中断；平台弱反馈需不同文章积累，明确选择优先。现有排除/撤销覆盖不当作本轮重新运行结果，也不据源码核查宣称真实个人效果通过。
