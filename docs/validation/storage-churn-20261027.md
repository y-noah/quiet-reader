# 持续缓存轮换与2GiB逻辑边界验证（2026-10-27）

本轮仅新增测试驱动StorageChurnProbe及storage-churn路由，不修改生产缓存策略，普通候选仍cc5a55d0。需求R13允许总ROM超过2GB并持续淘汰无用缓存；正文96MiB和图片128MiB为各自派生缓存的合理有界LRU，并非已取消的总150MB/300MB限额，也不以全App累计2GB阻断预读。

## 范围与限制

隔离测试目录中建立超过2GiB的稀疏文件，仅证明64位逻辑占用统计与空间准入没有2GB硬顶，不代表真实写满2GB物理空间或长期用户积累。记录stat块数，明确区分逻辑大小和实际分配。

在同一目录执行40次真实正文写入/重开，以及30次真实图片写入，使用缩小的派生缓存预算加速淘汰；检查旧条目淘汰、新条目可读、字节和数量边界、过期与遗留partial清理、低空间图片拒绝且已有条目保留。使用真实SQLite独立临时数据库保留兴趣反馈和profile版本，另以合成文件标记保护网页与偏好路径，最终清除全部测试文件和数据库。

合成网页文件不等同于WebView真实登录Cookie；本轮也不直接跑DiscoveryController/ReaderPrefetch网络预取，更不证明长期物理磁盘增长、整App ARM内存、耗电或用户账号。已有文章磁盘/实际Cookie回归与SQLite物理回收证据另列，不将单次规模测试当作全部R13完成。

## 结果

API36隔离Debug实际storage-churn 13/0，补跑article-disk 37/0，本轮选定50项通过，两组INSTRUMENTATION_CODE:-1。正文实际Cookie变化隔离、旧namespace晚写拒绝、清正文保留阅读标记均通过，仍非真实平台账号证明。构建终态成功，初次受限构建因既有Gradle缓存AccessDenied失败，日志保留；使用本机权限重跑成功，未改生产代码。App/测试APK归档散列见verification-summary.json；临时目录和数据库清理断言通过，关闭AVD后指定进程0。证据 artifacts/local-ai/storage-churn-20261027/。

