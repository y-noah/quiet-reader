# 预读磁盘命中避免重复写入（2026-10-25）

## 实际问题与实现

ArticlePreloader在普通磁盘restoreArticle成功后仍进入通用complete，重新调用Repository.cacheArticle。恢复路径已经验证会话/namespace并将正文放入共享内存；第二次cacheArticle会再次序列化、排队写临时文件、fsync和原子替换。内存命中分支与此前MainActivity.renderCached已跳过这项工作，预读磁盘分支仍有遗漏。

ArticlePreloader.complete新增内部acquired标志。仅实际restoreArticle命中传false，继续执行版本/视频证据核对、onPrepared、前台回调与下一项调度，保留原文件及采集时间。新获取、动态页面、续读等既有调用默认true，仍写缓存。恢复过程中被阅读页重建暂存的结果保留bufferedAcquired标志，resumeForeground后沿用；取消时清除缓冲与标志。没有改变Cookie指纹、namespace、ArticleVersion、有效期、缓存预算、正文/图片预取窗口、线程优先级或模型。

## 定向复现和回归

PrefetchDiskReuseProbe使用模拟器隔离debug包和随机测试库。以实际ArticleDiskCache落盘3篇唯一URL正文，清空内存后交给无Loader替代的普通ArticlePreloader恢复；使用writer任务屏障等待排队写入结束，比较真实文件inode而不是会因LRU读取更新时间变化的mtime。新正文对照仅使用明确受控Loader，不称网络抓取。

基线baseline.txt为13/3，三篇inode均发生改变（82761→82765、82763→82761、82764→82763），其余正文版本、原采集时间、新获取落盘通过。三个断言直接复现重复原子替换，未把失败改为跳过。

修复后fixed.txt为17/0：3篇真实磁盘恢复共享且版本匹配、inode不变、采集时间不变、重复viewport内存命中、在途磁盘恢复晋升为显式阅读、脱离旧owner后缓冲、恢复owner单次交付且inode不变、新获取仍产生可重新读取的磁盘正文。重建交接用同一主线程轮次运行正常pump，保证异步磁盘回调在detach之后到达，不写生产私有状态。

|当前debug回归|检查/失败|范围|
|---|---:|---|
|prefetch-disk-reuse|17/0|磁盘恢复/重建/新获取落盘|
|article-disk|37/0|会话、namespace、有效期、损坏/预算/清理|
|article-version-flow|14/0|新旧候选版本隔离、取消迟到结果|
|prefetch-lifecycle|16/0|滚动窗口、取消重试、Activity重建、HOME恢复|
|JVM|469/0/0/0|现有普通单元测试|

debug包身份、逐项原始输出与严格结果保存在artifacts/local-ai/prefetch-disk-reuse-20261025；不把旧包通过归给新包。

## 混淆安装包检查

新App F2B0B5698B542C38B5B5789A472F73CCEB4EA41A5981DC19C40F591A568B73BB，319416490字节；驱动102026555FE9B9AF59ADB50F1682CBF82EC4FD6F74C1DAFD546FA6DEBBF4C421。App/驱动/mapping/身份清单归档release/。新版与之前89C64F43…不同，不能沿用旧完整回归作为新版通过。

测试驱动新增显式verifyPrefetchDiskReuse=true，仅测试侧在写入100个固定合成正文后保存各文件inode，确认初始正文证据为0，再走普通在线预读和100条/6篇阅读，结束核对全部100个文件身份不变。该检查不改变生产缓存。prefetch-disk-20261025-release-a及同包阅读核心现已终态，结果见下文。

减少重复写入是行为修复，不从少量模拟器样本声称真机耗电/发热或首次打开速度改善。首次WebView导航与绘制延迟、真实个人兴趣收益、登录全文、Find X7 Ultra整App峰值与受控耗电、空间提示及最终升级仍开放。本轮未操作手机、提交、推送、更改默认版本或发版。

## 新混淆包完整回放终态

prefetch-disk-20261025-release-a已终态9/0，实际安装base.apk散列与F2B0B569…一致。严格主机readingReplay.passed=true：100个不同固定事件、6次真实卡片打开/展开/滚动/锚点返回，62434ms完整结束，连续三点单实例及每篇来源/URL身份通过。DUAL101向量/6输入对，点击前preparedEvidence=1。PREFETCH_DISK_REUSE original=100/unchanged=100，初始正文证据为0，普通预读确有恢复正文；全流程后100个原正文文件inode均保持。不是100篇真实下载证明。

首篇完整onCreate3165ms、正文DOM10806ms仍慢，不能将减少重复写盘说成首篇渲染已解决；这次没有资源或堆栈采样，仍非真机呈现时刻。同包核心结果见下节。

## 同包核心终态及收尾

reading-core同一session91860终态37/0，严格instrumentation_summary.passed=true。真实本地两模型、初筛/精排缓存、保持当前列表/显式接纳排序、正文读取/展开/滚动/缓存文件保持、后台正文语言证据恢复、短阅读不记偏好、内容反馈的实际平台归属、准备后新候选评价及旧画像拒绝均通过。实际接纳反馈排序时pending从984变为985，驱动以真实接纳快照/版本和可见顺序验证，通过既有竞态边界，没有改成固定编号或删除断言。合成画像排序变动不证明本人推荐收益。

所有本轮模拟器测试均终态，最后pidof主进程无结果、已返回系统桌面；未操作手机、未更改手机安装、默认版本或公开发布。新包尚不满足全部A00–A12，不能仅以本轮节省重复写入和回归通过宣称开发全部完成。
