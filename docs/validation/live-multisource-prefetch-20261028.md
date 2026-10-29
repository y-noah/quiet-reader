# 三源真实网络连续缓存（2026-10-28）

## 实际范围

扩展测试专用LivePrefetchSessionProbe，新增live-prefetch-multisource，保留原单源冷/暖模式。IT之家、澎湃、BBC中文真实公开榜单和正文，普通DiscoveryController、ArticlePreloader、Repository及解析器，不注入传输/正文加载器、不使用合成正文、不清磁盘制造冷态。随机隔离test-ui数据库仅启用这三个来源，初始快照来自刚刚实取的各源榜单。通过产品滚动状态/视口API轮换来源位置，30个十秒动作窗口，包含向前/向后及非前三条位置；不是触摸输入、阅读点击或视觉掉帧测试。每个动作显式暂停推荐模型，限定为正文缓存路径验证，不能作模型并行全App资源证明。

每十个窗口做一次三源真实刷新，最后再做一次，核对提交源版本及应用新快照。记录的READY来自各源实际存储状态；不靠总退出码代替逐源可用性。允许原有磁盘正文，warmAllowed=true；initiallyCold只记录开始时缺失RAM和磁盘文件数量，不宣称所有准备成功均为新下载。

## 原始证据

目录artifacts/local-ai/live-multisource-prefetch-20261028/。

- build.log终态成功。仅测试源码变更，未改变生产代码；构建命令包含JVM任务但允许缓存，本轮不声称重新执行全量JVM/Lint/R8。
- session.txt：315503ms，41个初始真实候选，29个起初无RAM/磁盘文件，最终40份匹配正文；19项/0失败/INSTRUMENTATION_CODE -1。准备匹配数从10→22→39→40，包含IT之家12、澎湃19、BBC9。剩余一个BBC候选未在本轮取得匹配正文；本驱动未记录其单项失败/过滤原因，不据此断言其可读或已适配，更不能说41篇全部成功。
- 三轮真实刷新共9条来源状态均READY，三次均提交更高来源版本。最终保留一个完全相同元数据版本的已准备候选，清空正文RAM后通过普通预读恢复，内容指纹、获取时间和实际文件inode保持相同，没有把复用当新获取。
- 实际Android HOME后排空之前的写入，再执行十个被动viewport回调、观察5秒：预读器及动作额度保持停止，正文文件清单/长度/mtime/inode和完成写任务数不增长；个人兴趣事件为0。五秒观察不等于后台30分钟无唤醒认证。
- single-source-regression.txt：同一扩展驱动的原ITHOME暖缓存模式，69322ms、12候选/12准备，开始冷目标0；12项/0失败/code -1。十窗口、实际刷新READY、同实例RAM淘汰后磁盘回访及HOME停止再次通过。
- packages.json与installed-hash.txt匹配：debug生产App SHA256 3b20708428e592133af431877ae6651dfb86f56d07fcdbe05dfcd229ea62c038，测试APK fb05b1d760f57c36e1f06a5056ea947bfdb990565024194a128eccf283aa6a7c。与前轮图片额度修复的debug生产APK字节一致；当前测试源码快照归档为LivePrefetchSessionProbe.java。
- 两个随机test-ui-live-session数据库均清理；databases-after.txt无本轮前缀，屏幕超时设置保持原值，session-cleanup.json。未删除原有缓存和不相关旧测试数据库，也未修改手机。

## 状态与剩余门槛

本地普通候选仍8aa074ca，手机仍f0d4dd5a；没有普通候选重建、手机操作、系统认证重试、推送或发版。专用模拟器正常退出，终态进程核对见cleanup.json，保留AVD用户数据。

本轮补充三来源、约五分钟有动作会话的证据；未覆盖数小时/天级积累、达到预算的真实在线淘汰、App被杀/进程恢复后的全链路、真实受限账号、全部候选正文、ARM连续全App≤1GB、拔线能耗/温升、本人推荐收益或独立语义去重。不能用31项通过数累计关闭上述整体门槛。
