# 真实断网旧正文阅读与兴趣隔离

## 范围

API36模拟器，正常debug store诊断包，随机test-ui数据库及独立URL；正文为明确的合成中文段落，不使用真实账号或冒充个人兴趣。生产代码未修改。

ReadInterestUiProbe新增read-interest-offline-snapshot模式：实际关闭模拟器Wi-Fi与移动数据，检查Android无active network。正文版本戳来自旧元数据，候选保持新元数据；正文时间为11分钟前，超过在线10分钟新鲜期。等待实际磁盘落盘，验证同一文件在线读取拒绝、离线读取允许；清空Repository内存缓存并检查无内存命中，再启动真实MainActivity。正文加载完成与窗口聚焦后，经实际用户交互/滚动钩子和20秒前台停留，退出阅读页。

验证旧正文可展示且metadataHash没有被重新标成新候选；onPause之后观察独立数据库5秒，没有READ事件，因此也没有可供平台弱倾向使用的阅读事件。测试结束在finally按原状态恢复模拟器Wi-Fi及移动数据。网络原状态与恢复后均为1/1。

## 报告

所有报告位于artifacts/local-ai：

| 报告 | 结果 | 边界 |
|---|---|---|
| read-interest-offline-snapshot-api36.txt | 6/0 | 初版实际断网与旧版本内存缓存，保留报告，不作为磁盘恢复证据 |
| read-interest-offline-positive-control-api36.txt | 7/0 | 同版本正常正文完成真实20秒阅读，产生且仅产生一个READ，携带实际来源，进入语义画像；单次不足以建立平台倾向，关闭学习后撤回 |
| read-interest-offline-disk-snapshot-api36.txt | 9/0 | 最终加强为超过在线新鲜期的磁盘缓存，内存清空后真实离线阅读，不产生新候选READ |
| build-offline-disk-read-interest.txt | 构建成功 | store诊断包及本地模型资源，无发布 |

JVM332，失败/错误/跳过均0。新增用例只增加诊断路径，没有放宽生产版本校验或禁止全部阅读学习。正向对照发生在最后增加磁盘夹具之前；夹具只在offlineSnapshot模式运行，正常阅读分支未变化。

## 不覆盖

不以该用例证明真实来源完整正文、登录切换、跨进程重启恢复、离线图片字节、所有缓存年龄、所有主题/字号或推荐质量已完成。这里证明的是过在线新鲜期且元数据版本不符的正文，在真实断网磁盘恢复时仍可读，不会通过阅读弱反馈训练当前候选。没有证明离线显式收藏/多看等另一路径的全部边界。Find X7 Ultra功耗/峰值与精确release升级仍待验证。
