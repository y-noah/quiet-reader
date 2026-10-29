# 分批供给期间主动准备正文 / 2026-10-25

## 问题与实现

上一轮已让首批热门候选在其他来源尚未结束时可读，但 DiscoveryController.canPrefetch 的 loading 条件仍阻止全部正文/图片预读。用户看得到卡片，点击前的空闲时间却无法用来准备正文。

新增 DiscoveryPrefetchSupplyProbe：真实 DiscoveryActivity、Controller、SQLite、ArticlePreloader 与 Repository 共享正文缓存，榜单及正文使用受控 transport/loader。基线 discovery-prefetch-supply-baseline-api36.txt 为1项、1失败：首批卡片已出现，但另外两个普通榜单请求仍等待时，没有正文预读，等待超时。

实现保持普通榜单优先，并利用其空闲名额：

- HotSourceCollector.allowsPrefetch 只在当前来源 job 少于3且没有活动/排队的浏览器 job 时允许一个串行预读管线。三个来源名额均占用时不另开预读。
- DiscoveryController 不再将整轮 loading 作为预读的一刀切条件；继续检查前台、当前发现页、用户交互、电量/温度/系统内存、磁盘预算、模型修复和推理互斥。推荐模型依然等待来源采集完成。
- 来源浏览器 transport 启动前，新的 onBrowserStarting 通知同步暂停正文和图片预读。通知后再次检查 job 是否仍有效，避免监听器取消任务后继续启动浏览器。
- 来源开始/结束通知重新安排当前窗口的去抖预读，浏览器结束后可以恢复之前中断的正文；实际失败仍遵循原退避。离页/新轮/策略取消仍拒绝迟到写入。

未增加线程池、预读窗口、图片预算、缓存容量、轮询或后台工作。这里约束的是调度管线和名额，取消时底层连接释放可能异步；不能推导为任意瞬间物理socket数保证，更不能推导为真机1GB峰值/耗电已通过。

## 验证

报告位于 artifacts/local-ai/，最终构建 build-discovery-prefetch-slot-final.txt，JVM403项、失败/错误0。

| API36模拟器验证 | 检查/失败 | 报告 |
| --- | --- | --- |
| 无现有候选的冷启动供给与预读 | 7/0 | discovery-prefetch-supply-prefetch-slot-final-api36.txt |
| 有过期榜单卡片的刷新与预读 | 8/0 | discovery-prefetch-supply-warm-prefetch-slot-final-api36.txt |
| 冷启动时序复跑 | 7/0 | discovery-prefetch-supply-prefetch-slot-repeat-api36.txt |
| 已有卡片时序复跑 | 8/0 | discovery-prefetch-supply-warm-prefetch-slot-repeat-api36.txt |
| SQLite/来源采集器完整回归 | 158/0 | feed-store-prefetch-slot-final-api36.txt |
| 分批供给/冻结/替代失败 | 16/0 | discovery-supply-prefetch-slot-final-api36.txt |
| 预读窗口/转屏/Home生命周期 | 10/0 | prefetch-lifecycle-prefetch-slot-final-api36.txt |
| 图片预读省电/热状态/后台策略 | 12/0 | image-prefetch-policy-prefetch-slot-final-api36.txt |
| 自动推荐及两个模型执行 | 8/0 | automatic-recommendation-prefetch-slot-final-api36.txt |

冷/暖场景均确认：两个普通榜单仍在进行时准备正文、点击前获得元数据匹配的共享正文、V2EX原生失败转浏览器时暂停预读、暂停后的迟到正文不得缓存、浏览器占用期间viewport动作不能重启预读、浏览器结束但微博普通请求仍等待时恢复同一被中断正文、离页取消和迟到拒绝。暖场景另验证：已有三张可读卡片、三个榜单名额同时占用时，预读不会另开请求。冷启动空列表路径单独保留，不能以暖场景替代首次供给。

受控loader未发真实正文HTTP或创建真实来源浏览器，不能把该probe当成全部来源可读性、浏览器实际PSS或网络时延证据。图片策略另有回归，但本probe没有在浏览器启动瞬间运行实际图片下载，不宣称该特定重叠场景已实测。

git diff --check 及本次Java文件的行尾空白/冲突标记检查通过。

## 剩余验收

真实平台热门供给、真实登录全文及长期新鲜度、独立人工兴趣留出、Find X7 Ultra整体RAM/耗电/发热、精确APK升级仍待完成。源端受限和未缓存内容依然可能等待，不能承诺所有卡片秒开。本次未提交、推送、发版或改变品牌。
