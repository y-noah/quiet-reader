# 主动预读页面重建修复与真实来源链路

## 已复现生产缺陷

DiscoveryActivity此前以state==null&&!testSkip创建DiscoveryController。正常保存状态重建后autoRefresh为false，而该字段同时控制viewport调度及canPrefetch，造成新Activity生命周期内主动预读一直关闭。此问题并非网络慢或来源限制。

生产修复只把该参数改为!testSkip，保存状态不撤销自动缓存许可；既有maybeStart仍检查每个来源15分钟新鲜期、开关和退避，没有新增计时刷新。debug明确test_skip_fetch的隔离继续保留。

## 受控传输、真实生命周期

PrefetchLifecycleProbe使用30条明确合成文字候选、随机数据库、真实Activity/Controller/SQLite/Repository/调度器，替换的仅是ArticlePreloader.Loader。源快照刚成功且仅该源启用；不向真实平台发送合成文章请求，也不冒充真实来源供给或用户偏好。

从正常前台启动触发viewport预读，在阅读页打开之前验证版本匹配的共享正文缓存。把窗口移动至第21项，旧请求取消，新请求跟随窗口，旧请求迟到不能写缓存。随后实际Activity.recreate、实际HOME以及返回原任务，验证重建与恢复自动预读、后台取消及迟到拒绝。源requested仍为初始1，证明这些操作没有重复刷新仍新鲜的榜单。

| artifacts/local-ai报告 | 结果 |
|---|---|
| prefetch-lifecycle-baseline-api36.txt | 7/2：重建后autoRefresh关闭且不会恢复预读；其他5项通过 |
| prefetch-lifecycle-fixed-api36.txt | 同7项修复后7/0 |
| prefetch-lifecycle-complete-api36.txt | 扩展实际HOME返回与新鲜源不刷新，10/0 |
| prefetch-lifecycle-repeat-api36.txt | 相同生产实现再次10/0 |

这些窗口操作通过实际controller.viewport触发，HOME与recreate是真实系统生命周期。不是手势速度/所有屏幕布局或厂商进程回收的证明。

## 无替换加载器的真实来源验证

LivePrefetchProbe先通过Android Repository请求当前澎湃热榜，逐篇检查共享缓存，选择没有匹配正文的实际非视频文章；不删除已有用户缓存。只把选定真实候选放入随机诊断库，来源成功时间设为当前且其他源禁用。正常Discovery启动、真实viewport自动预读，没有注入loader或直接请求正文。正文缓存准备完成前不点击；准备后通过实际Discovery.open进入MainActivity，检查版本、正文、统一WebView及无来源DynamicReader重载；预取本身无READ或平台反馈。

| 报告 | 结果与测量范围 |
|---|---|
| live-prefetch-thepaper-api36.txt | 5/0；实际热榜第1篇34324278，自动取得匹配正文3164ms；点击到reading/readerWeb已建立487ms，尚未要求页面加载完成 |
| live-prefetch-thepaper-completed-api36.txt | 5/0；改用另一未缓存的真实第2篇34301129，自动取得匹配正文3693ms；点击到实际ReadingEngagement.ready（页面加载完成）2524ms |

两次开始前均确认无匹配正文；第一次粗就绪时间不能与第二次页面完成时间直接比较，也不等于首个文字绘制时间。预取消除了点击后重新获取来源正文，但统一WebView创建、正文排版和图片加载仍有成本，不能宣称所有内容秒开。只有一条真实候选的诊断列表，不能由此声称真实20项窗口全部提前完成；宽窗口/移动/取消由上面的受控用例验证。

## 回归与限制

discovery-ui-prefetch-lifecycle-api36.txt为77/1，唯一失败是旧测试把来源数量写死为9；已有IT之家和澎湃后实际为11。只更新该断言预期为11，保留旧报告，discovery-ui-prefetch-catalog-verified-api36.txt为77/0，涵盖原发现卡片、筛选、重建锚点、预读/交接、热限制及缓存失效。build-prefetch-ui-catalog.txt成功，JVM337失败/错误/跳过0；diff检查通过。

本轮不证明真实登录平台、所有来源预测命中、长期后台杀进程恢复、个人排序效果、FindX7Ultra全进程峰值/耗电或精确release升级已完成。未提交、推送、发布或改品牌，整体目标仍未完成。
